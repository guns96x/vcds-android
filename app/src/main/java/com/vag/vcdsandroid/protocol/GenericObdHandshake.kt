package com.vag.vcdsandroid.protocol

import com.vag.vcdsandroid.bluetooth.ElmResponse
import java.util.Locale

sealed class HandshakeResult {
    data class Success(
        val stage: String,
        val rpmResponse: ElmResponse,
        val mapResponse: ElmResponse,
        val protocolName: String,
        val protocolNum: String
    ) : HandshakeResult()

    data class Failure(
        val reason: String,
        val trace: String
    ) : HandshakeResult()
}

class GenericObdHandshake(
    private val sendCmd: suspend (cmd: String, timeoutMs: Long) -> ElmResponse,
    private val log: (String) -> Unit
) {
    companion object {
        fun cleanHex(raw: String): String {
            val upper = raw.uppercase(Locale.ROOT)
            if (upper.contains("NO DATA") || upper.contains("ERROR") || upper.contains("UNABLE TO CONNECT") || upper.contains("STOPPED")) {
                return ""
            }
            return upper.replace(Regex("[^0-9A-F]"), "")
        }

        fun validPidReply(resp: ElmResponse, marker: String): Boolean {
            if (!resp.promptReceived || resp.timedOut) return false
            val clean = cleanHex(resp.raw)
            if (!clean.contains(marker)) return false
            return when (marker) {
                "410C" -> PidDecoder.decodeRpm(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut).status == PidStatus.VALID
                "410B" -> PidDecoder.decodeMap(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut).status == PidStatus.VALID
                else -> true
            }
        }
    }

    suspend fun execute(): HandshakeResult {
        log("5. Перевірка напруги та конфігурація OBD-II зв'язку...")
        val vBat = sendCmd("ATRV", 1000L)
        log("Напруга бортової мережі: ${vBat.raw.trim()}")

        // =====================================================================
        // Stage 1: KNOWN-GOOD AUTO PATH (ATD -> ATSP0)
        // Must require BOTH valid 010C and 010B
        // =====================================================================
        log("--- Stage 1: Спроба KNOWN-GOOD AUTO PATH (ATD -> ATSP0) ---")
        val stage1Init = listOf(
            "ATD" to 1500L,
            "ATE0" to 1000L,
            "ATL0" to 1000L,
            "ATS0" to 1000L,
            "ATH0" to 1000L,
            "ATCAF1" to 1000L,
            "ATCFC1" to 1000L,
            "ATR1" to 1000L,
            "ATAT1" to 1000L,
            "ATSP0" to 2000L
        )
        for ((cmd, timeout) in stage1Init) {
            val r = sendCmd(cmd, timeout)
            log("GENERIC INIT $cmd -> ${r.raw.trim()} (${r.elapsedMs}ms)")
        }

        val rpmResp1 = sendCmd("010C", 5000L)
        log("AUTO PROBE 010C -> ${rpmResp1.raw.trim()} (${rpmResp1.elapsedMs}ms)")
        val mapResp1 = sendCmd("010B", 5000L)
        log("AUTO PROBE 010B -> ${mapResp1.raw.trim()} (${mapResp1.elapsedMs}ms)")

        val rpmOk1 = validPidReply(rpmResp1, "410C")
        val mapOk1 = validPidReply(mapResp1, "410B")

        if (rpmOk1 && mapOk1) {
            val dpResp = sendCmd("ATDP", 1000L)
            val dpnResp = sendCmd("ATDPN", 1000L)
            log("==> OBD_READY via AUTO (RPM+MAP OK): ${dpResp.raw.trim()} (${dpnResp.raw.trim()})")
            return HandshakeResult.Success("AUTO", rpmResp1, mapResp1, dpResp.raw.trim(), dpnResp.raw.trim())
        }

        // Optional check for diagnostic evidence
        val pid0100 = sendCmd("0100", 7000L)
        log("AUTO PROBE 0100 -> ${pid0100.raw.trim()} (${pid0100.elapsedMs}ms)")

        // =====================================================================
        // Stage 2: FIXED CAN 11/500 WITH DEFAULT HEADER (ATD -> ATSP6)
        // Must require BOTH valid 010C and 010B
        // =====================================================================
        log("--- Stage 2: Спроба FIXED CAN 11/500 WITH DEFAULT HEADER (ATD -> ATSP6) ---")
        sendCmd("ATD", 1500L)
        val stage2Init = listOf(
            "ATE0" to 1000L,
            "ATL0" to 1000L,
            "ATS0" to 1000L,
            "ATH0" to 1000L,
            "ATCAF1" to 1000L,
            "ATCFC1" to 1000L,
            "ATR1" to 1000L,
            "ATAT1" to 1000L,
            "ATSP6" to 1500L
        )
        for ((cmd, timeout) in stage2Init) {
            val r = sendCmd(cmd, timeout)
            log("STAGE2 INIT $cmd -> ${r.raw.trim()} (${r.elapsedMs}ms)")
        }

        val rpmResp2 = sendCmd("010C", 5000L)
        log("SP6 PROBE 010C -> ${rpmResp2.raw.trim()} (${rpmResp2.elapsedMs}ms)")
        val mapResp2 = sendCmd("010B", 5000L)
        log("SP6 PROBE 010B -> ${mapResp2.raw.trim()} (${mapResp2.elapsedMs}ms)")

        val rpmOk2 = validPidReply(rpmResp2, "410C")
        val mapOk2 = validPidReply(mapResp2, "410B")

        if (rpmOk2 && mapOk2) {
            val dpResp = sendCmd("ATDP", 1000L)
            val dpnResp = sendCmd("ATDPN", 1000L)
            log("==> OBD_READY via SP6_DEFAULT_HEADER (RPM+MAP OK): ${dpResp.raw.trim()} (${dpnResp.raw.trim()})")
            return HandshakeResult.Success("SP6_DEFAULT_HEADER", rpmResp2, mapResp2, dpResp.raw.trim(), dpnResp.raw.trim())
        }

        // =====================================================================
        // Stage 3: PHYSICAL 7E0/7E8 EXPERIMENTAL FALLBACK
        // Must require BOTH valid 010C and 010B
        // =====================================================================
        log("--- Stage 3: Спроба PHYSICAL 7E0/7E8 FALLBACK (ATD -> ATSP6 -> ATSH7E0 -> ATCRA7E8) ---")
        sendCmd("ATD", 1500L)
        val stage3Init = listOf(
            "ATE0" to 1000L,
            "ATL0" to 1000L,
            "ATS0" to 1000L,
            "ATH0" to 1000L,
            "ATCAF1" to 1000L,
            "ATCFC1" to 1000L,
            "ATR1" to 1000L,
            "ATAT1" to 1000L,
            "ATSP6" to 1500L,
            "ATSH7E0" to 1000L,
            "ATCRA7E8" to 1000L
        )
        for ((cmd, timeout) in stage3Init) {
            val r = sendCmd(cmd, timeout)
            log("STAGE3 INIT $cmd -> ${r.raw.trim()} (${r.elapsedMs}ms)")
        }

        val rpmResp3 = sendCmd("010C", 5000L)
        log("PHYSICAL PROBE 010C -> ${rpmResp3.raw.trim()} (${rpmResp3.elapsedMs}ms)")
        val mapResp3 = sendCmd("010B", 5000L)
        log("PHYSICAL PROBE 010B -> ${mapResp3.raw.trim()} (${mapResp3.elapsedMs}ms)")

        val rpmOk3 = validPidReply(rpmResp3, "410C")
        val mapOk3 = validPidReply(mapResp3, "410B")

        if (rpmOk3 && mapOk3) {
            val dpResp = sendCmd("ATDP", 1000L)
            val dpnResp = sendCmd("ATDPN", 1000L)
            log("==> OBD_READY via PHYSICAL_7E0 (RPM+MAP OK): ${dpResp.raw.trim()} (${dpnResp.raw.trim()})")
            return HandshakeResult.Success("PHYSICAL_7E0", rpmResp3, mapResp3, dpResp.raw.trim(), dpnResp.raw.trim())
        }

        // Mandatory ATD cleanup so manual headers never leak
        sendCmd("ATD", 1500L)

        val err = "ЕБУ двигуна не відповідає на обов'язкову пару OBD-II Mode 01 (010C RPM + 010B MAP).\nПеревірте, щоб запалювання було увімкнене (або заведіть двигун) та адаптер був щільно вставлений у роз'єм OBD!"
        log("ERR: $err")
        return HandshakeResult.Failure(err, "")
    }
}
