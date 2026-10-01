package com.vag.vcdsandroid.usb

import com.vag.vcdsandroid.adapters.HexB03Constants
import com.vag.vcdsandroid.adapters.HexB03Frame

/**
 * Evidence status of a hardware claim. Mirrors the labels used in AI_CONTEXT.md.
 * A claim never moves up this list without a car log or a binary proof.
 */
enum class Evidence { PROVEN, PROVEN_STATIC, OBSERVED, INFERRED, UNKNOWN }

/**
 * Steps of the USB OEM connection, in the only order they can be reached.
 * Shown to the user as a progress line; see [UsbConnectionTracker].
 */
enum class UsbConnectionState(val label: String) {
    IDLE("Idle"),
    USB_DETECTED("USB detected"),
    USB_PERMISSION("USB permission"),
    INTERFACE_OPENED("Interface opened"),
    INTERFACE_IDENTIFIED("Interface identified"),
    ADAPTER_MODE_DETECTED("Adapter mode detected"),
    ECU_INITIALIZATION("ECU initialization"),
    ECU_REPLY("ECU reply"),
    SESSION_ACTIVE("Session active")
}

/**
 * Why a connection attempt ended. One value per distinguishable cause, so the
 * screen and the log never say just "Connection failed".
 *
 * [evidence] states how well the cause itself is established, not how well the
 * hint is: a silent cable is only INFERRED to be unpowered.
 */
enum class UsbConnectResult(
    val title: String,
    val hint: String,
    val evidence: Evidence,
    val isFailure: Boolean = true
) {
    USB_DEVICE_NOT_FOUND(
        "USB device not found",
        "No FTDI/Ross-Tech device is attached to the phone. Check the OTG adapter.",
        Evidence.OBSERVED
    ),
    PERMISSION_DENIED(
        "USB permission denied",
        "Android did not grant USB access. Re-plug the cable and accept the permission dialog.",
        Evidence.OBSERVED
    ),
    INTERFACE_NOT_POWERED(
        "Interface not powered / silent",
        "The serial port opened but the cable returned no byte at all. The interface is believed " +
            "to be powered from OBD pin 16, so plug it into the phone first and then into the car.",
        Evidence.INFERRED
    ),
    SERIAL_OPEN_FAILED(
        "Serial open failed",
        "The USB serial port could not be opened or configured.",
        Evidence.OBSERVED
    ),
    ADAPTER_IDENTIFY_FAILED(
        "Adapter identify failed",
        "The cable answered, but not with the expected ROSSTECH identity.",
        Evidence.OBSERVED
    ),
    ADAPTER_ECHO_ONLY(
        "Adapter echo only",
        "The cable sent our own request back unchanged and no answer frame. It behaves like a " +
            "transparent (dumb) K-Line cable, not like an intelligent interface.",
        Evidence.OBSERVED
    ),
    NO_KLINE_ADAPTER_RESPONSE(
        "No K-Line / adapter response",
        "Nothing came back from the K-Line path, not even our own echo.",
        Evidence.OBSERVED
    ),
    INIT_TIMEOUT(
        "Init timeout",
        "The cable accepted the 5-baud init request but never answered within the init window.",
        Evidence.OBSERVED
    ),
    INVALID_CHECKSUM(
        "Invalid checksum",
        "A reply frame arrived but its checksum is wrong, so it was discarded.",
        Evidence.OBSERVED
    ),
    UNEXPECTED_ADAPTER_RESPONSE(
        "Unexpected adapter response",
        "The cable answered with bytes or a frame that this step does not expect.",
        Evidence.OBSERVED
    ),
    ECU_NOT_RESPONDING(
        "ECU not responding",
        "The link works but the engine ECU gave no valid answer. Check ignition ON.",
        Evidence.OBSERVED
    ),
    UNSUPPORTED_PROTOCOL(
        "Unsupported protocol",
        "The ECU answered with keywords this app does not speak (KW1281).",
        Evidence.OBSERVED
    ),
    ECU_INIT_ANSWERED(
        "ECU answered 5-baud init",
        "Sync and key bytes were received. KWP framing over the smart cable is UNKNOWN, so no " +
            "diagnostic session is claimed yet.",
        Evidence.OBSERVED,
        isFailure = false
    ),
    CONNECTION_ESTABLISHED(
        "Connection established",
        "A checksum-valid KWP reply from ECU 01 was received.",
        Evidence.OBSERVED,
        isFailure = false
    )
}

/**
 * Records how far one connection attempt got. Forward-only, so a late callback
 * cannot move the display backwards, and every transition is logged with the
 * time since the attempt began. Android-free: the log sink is injected.
 */
class UsbConnectionTracker(
    private val sink: (String) -> Unit = {},
    private val nanoTime: () -> Long = System::nanoTime
) {
    private var startedNs = nanoTime()
    private val reached = ArrayList<Pair<UsbConnectionState, Long>>()

    var state: UsbConnectionState = UsbConnectionState.IDLE
        private set
    var result: UsbConnectResult? = null
        private set

    private fun elapsedMs(): Long = (nanoTime() - startedNs) / 1_000_000L

    @Synchronized
    fun reset() {
        startedNs = nanoTime()
        reached.clear()
        state = UsbConnectionState.IDLE
        result = null
        sink("STATE reset")
    }

    /** Moves forward to [to]. A transition to the same or an earlier state is ignored. */
    @Synchronized
    fun advance(to: UsbConnectionState, detail: String = "") {
        if (to.ordinal <= state.ordinal) {
            sink("STATE ignored ${to.name} (already ${state.name})")
            return
        }
        val now = elapsedMs()
        reached += to to now
        sink("STATE ${state.name} -> ${to.name} t=${now}ms${if (detail.isEmpty()) "" else " $detail"}")
        state = to
    }

    /** Ends the attempt with [outcome]; the state stays at the last step that was reached. */
    @Synchronized
    fun finish(outcome: UsbConnectResult, detail: String = "") {
        result = outcome
        sink(
            "RESULT ${outcome.name} (${if (outcome.isFailure) "FAIL" else "OK"}, " +
                "evidence=${outcome.evidence}) after ${state.name} t=${elapsedMs()}ms" +
                if (detail.isEmpty()) "" else " $detail"
        )
    }

    /** One line per step, with the failing step marked, for the result dialog. */
    @Synchronized
    fun progressText(): String {
        val reachedStates = reached.map { it.first }.toSet()
        val lines = UsbConnectionState.values().filter { it != UsbConnectionState.IDLE }.map { s ->
            val mark = if (s in reachedStates) "[x]" else "[ ]"
            "$mark ${s.label}"
        }
        val tail = result?.let { "\n=> ${it.title}: ${it.hint}" } ?: ""
        return lines.joinToString("\n") + tail
    }
}

/**
 * Turns "what did the cable send back" into one [UsbConnectResult].
 *
 * The evidence is the exact request, every raw byte received and every
 * checksum-valid cable frame. Pure and deterministic, so each branch is unit
 * tested; the decision order below is part of the contract.
 */
object B03RxClassifier {

    /** Where in the connection the cable stayed quiet. */
    enum class Stage { INTERFACE_PROBE, ECU_INIT }

    /**
     * Classifies an exchange that did NOT produce the expected reply frame.
     *
     * @param request the exact bytes written to the cable
     * @param raw every byte received, in order
     * @param frames checksum-valid cable frames decoded from [raw]
     * @param expectedOpcode opcode of the reply the caller was waiting for
     */
    fun classifyNoReply(
        stage: Stage,
        request: ByteArray,
        raw: ByteArray,
        frames: List<HexB03Frame>,
        expectedOpcode: Byte
    ): UsbConnectResult {
        // A valid cable frame with another opcode is an answer we do not understand.
        if (frames.any { it.opcode != expectedOpcode }) return UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE
        if (raw.isEmpty()) {
            return when (stage) {
                Stage.INTERFACE_PROBE -> UsbConnectResult.INTERFACE_NOT_POWERED
                Stage.ECU_INIT -> UsbConnectResult.INIT_TIMEOUT
            }
        }
        if (isEchoOnly(request, raw)) return UsbConnectResult.ADAPTER_ECHO_ONLY
        if (hasBadChecksumFrame(raw)) return UsbConnectResult.INVALID_CHECKSUM
        return UsbConnectResult.UNEXPECTED_ADAPTER_RESPONSE
    }

    /**
     * True when the received bytes are our own request coming back, whole or cut
     * short, optionally followed by more bytes that are not a cable frame.
     */
    fun isEchoOnly(request: ByteArray, raw: ByteArray): Boolean {
        if (request.isEmpty() || raw.isEmpty()) return false
        val n = minOf(request.size, raw.size)
        for (i in 0 until n) if (request[i] != raw[i]) return false
        return true
    }

    /** True when [raw] holds a complete-length cable frame (marker 0x4D) whose XOR is wrong. */
    fun hasBadChecksumFrame(raw: ByteArray): Boolean {
        for (i in raw.indices) {
            if (raw[i] != HexB03Constants.MARKER_CABLE) continue
            if (i + 1 >= raw.size) continue
            val len = raw[i + 1].toInt() and 0xFF
            if (len < HexB03Constants.MIN_FRAME_LEN || i + len > raw.size) continue
            var xor = 0
            for (k in i until i + len - 1) xor = xor xor (raw[k].toInt() and 0xFF)
            if (xor != (raw[i + len - 1].toInt() and 0xFF)) return true
        }
        return false
    }
}
