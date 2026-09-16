package com.vag.vcdsandroid.bluetooth

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.os.SystemClock
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

data class ElmResponse(
    val command: String,
    val raw: String,
    val promptReceived: Boolean,
    val timedOut: Boolean,
    val elapsedMs: Long,
    val txNanos: Long = 0L,
    val rxNanos: Long = 0L
)

class BluetoothElmTransport(private val context: Context) {

    companion object {
        private const val TAG = "ELM_BT"
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val RFCOMM_CONNECT_TIMEOUT_MS = 10000L
    }

    private val bluetoothAdapter: BluetoothAdapter? by lazy {
        BluetoothAdapter.getDefaultAdapter()
    }

    private var socket: BluetoothSocket? = null
    private var inStream: InputStream? = null
    private var outStream: OutputStream? = null

    private val commandMutex = Mutex()
    private var lastCommandTimedOut = AtomicBoolean(false)
    private val rxLeftover = StringBuilder()

    var connectedDeviceName: String? = null
        private set

    var connectedDeviceAddress: String? = null
        private set

    val isConnected: Boolean
        get() = socket?.isConnected == true

    @SuppressLint("MissingPermission")
    fun getBondedDevices(): List<BluetoothDevice> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()
        return try {
            adapter.bondedDevices?.toList() ?: emptyList()
        } catch (e: SecurityException) {
            Log.w("ELM_BT", "SecurityException reading bonded devices: ${e.message}")
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    fun findPairedElmDevice(preferredAddress: String? = null): BluetoothDevice? {
        val bonded = getBondedDevices()
        if (bonded.isEmpty()) return null

        if (!preferredAddress.isNullOrBlank()) {
            val pref = bonded.firstOrNull { it.address.equals(preferredAddress, ignoreCase = true) }
            if (pref != null) return pref
        }

        val obdKeywords = listOf(
            "V-LINK", "VLINK", "OBD", "ELM", "VGATE", "VIECAR", "KONNWEI",
            "CARLINK", "IOS-VLINK", "HHOBD", "ICAR", "SCANNER", "DIAG"
        )

        // Filter out audio, phone, computer, and wearable devices
        val filtered = bonded.filter { dev ->
            val devClass = dev.bluetoothClass
            if (devClass != null) {
                val major = devClass.majorDeviceClass
                if (major == android.bluetooth.BluetoothClass.Device.Major.AUDIO_VIDEO ||
                    major == android.bluetooth.BluetoothClass.Device.Major.PHONE ||
                    major == android.bluetooth.BluetoothClass.Device.Major.COMPUTER ||
                    major == android.bluetooth.BluetoothClass.Device.Major.WEARABLE) {
                    return@filter false
                }
            }
            true
        }

        val match = filtered.firstOrNull { dev ->
            val name = (dev.name ?: "").uppercase()
            val clean = name.replace("-", "").replace("_", "").replace(" ", "")
            obdKeywords.any { kw ->
                val cleanKw = kw.replace("-", "").replace("_", "").replace(" ", "")
                name.contains(kw) || clean.contains(cleanKw)
            }
        } ?: filtered.firstOrNull() ?: bonded.firstOrNull()

        return match
    }

    /**
     * Connects to the BluetoothDevice using a 3-stage RFCOMM strategy with strict timeout
     * and explicit socket.close() on timeout.
     */
    @SuppressLint("MissingPermission")
    suspend fun connect(
        targetDevice: BluetoothDevice? = null,
        logCallback: ((String) -> Unit)? = null
    ): Boolean = withContext(Dispatchers.IO) {
        disconnect()

        val dev = targetDevice ?: findPairedElmDevice() ?: run {
            val msg = "No paired ELM327 / OBD2 Bluetooth device found!"
            Log.e(TAG, msg)
            logCallback?.invoke("ERR: $msg")
            return@withContext false
        }

        val devName = dev.name ?: "Unknown"
        val devMac = dev.address
        val bondState = when (dev.bondState) {
            BluetoothDevice.BOND_BONDED -> "BOND_BONDED (12)"
            BluetoothDevice.BOND_BONDING -> "BOND_BONDING (11)"
            else -> "BOND_NONE (${dev.bondState})"
        }

        val headerMsg = "Initiating RFCOMM connection: Name='$devName', MAC='XX:XX:XX:XX:XX:XX', BondState=$bondState"
        Log.i(TAG, headerMsg)
        logCallback?.invoke(headerMsg)

        // Step 1: cancelDiscovery() is critical before socket.connect()
        try {
            if (bluetoothAdapter?.isDiscovering == true) {
                bluetoothAdapter?.cancelDiscovery()
                Log.i(TAG, "cancelDiscovery() called successfully")
                logCallback?.invoke("cancelDiscovery() executed")
            }
        } catch (e: SecurityException) {
            Log.w(TAG, "cancelDiscovery SecurityException: ${e.message}")
        } catch (e: Exception) {
            Log.w(TAG, "cancelDiscovery Exception: ${e.message}")
        }

        var activeSocket: BluetoothSocket? = null
        var connected = false

        // Attempt 1: Standard Secure RFCOMM using SPP UUID
        val attempt1Msg = "Attempt 1: Secure SPP RFCOMM (UUID: $SPP_UUID)..."
        Log.i(TAG, attempt1Msg)
        logCallback?.invoke(attempt1Msg)
        try {
            val s = dev.createRfcommSocketToServiceRecord(SPP_UUID)
            activeSocket = s
            connectWithTimeout(s, RFCOMM_CONNECT_TIMEOUT_MS)
            connected = s.isConnected
            if (connected) {
                val successMsg = "Attempt 1 SUCCESS: Connected via Secure SPP!"
                Log.i(TAG, successMsg)
                logCallback?.invoke(successMsg)
            }
        } catch (e: Exception) {
            val errMsg = "Attempt 1 FAILED [${e.javaClass.simpleName}]: ${e.message}"
            Log.w(TAG, errMsg)
            logCallback?.invoke(errMsg)
            try { activeSocket?.close() } catch (_: Exception) {}
            activeSocket = null
        }

        // Attempt 2: Insecure RFCOMM using SPP UUID
        if (!connected) {
            val attempt2Msg = "Attempt 2: Insecure SPP RFCOMM (UUID: $SPP_UUID)..."
            Log.i(TAG, attempt2Msg)
            logCallback?.invoke(attempt2Msg)
            try {
                val s = dev.createInsecureRfcommSocketToServiceRecord(SPP_UUID)
                activeSocket = s
                connectWithTimeout(s, RFCOMM_CONNECT_TIMEOUT_MS)
                connected = s.isConnected
                if (connected) {
                    val successMsg = "Attempt 2 SUCCESS: Connected via Insecure SPP!"
                    Log.i(TAG, successMsg)
                    logCallback?.invoke(successMsg)
                }
            } catch (e: Exception) {
                val errMsg = "Attempt 2 FAILED [${e.javaClass.simpleName}]: ${e.message}"
                Log.w(TAG, errMsg)
                logCallback?.invoke(errMsg)
                try { activeSocket?.close() } catch (_: Exception) {}
                activeSocket = null
            }
        }

        // Attempt 3: Reflection Channel 1 (Diagnostic fallback only)
        if (!connected) {
            val attempt3Msg = "Attempt 3 (Fallback): Reflection Channel 1 (non-SDK)..."
            Log.i(TAG, attempt3Msg)
            logCallback?.invoke(attempt3Msg)
            try {
                val m = dev.javaClass.getMethod("createRfcommSocket", Int::class.javaPrimitiveType)
                val s = m.invoke(dev, 1) as? BluetoothSocket
                if (s != null) {
                    activeSocket = s
                    connectWithTimeout(s, RFCOMM_CONNECT_TIMEOUT_MS)
                    connected = s.isConnected
                    if (connected) {
                        val successMsg = "Attempt 3 SUCCESS: Connected via Reflection Channel 1!"
                        Log.i(TAG, successMsg)
                        logCallback?.invoke(successMsg)
                    }
                }
            } catch (e: Exception) {
                val errMsg = "Attempt 3 FAILED [${e.javaClass.simpleName}]: ${e.message}"
                Log.e(TAG, errMsg)
                logCallback?.invoke(errMsg)
                try { activeSocket?.close() } catch (_: Exception) {}
                activeSocket = null
            }
        }

        if (!connected || activeSocket == null) {
            val finalErr = "All RFCOMM connection attempts failed for $devName [$devMac]"
            Log.e(TAG, finalErr)
            logCallback?.invoke("ERR: $finalErr")
            disconnect()
            return@withContext false
        }

        return@withContext try {
            socket = activeSocket
            inStream = activeSocket.inputStream
            outStream = activeSocket.outputStream
            connectedDeviceName = devName
            connectedDeviceAddress = devMac
            lastCommandTimedOut.set(false)

            val openMsg = "I/O Streams cached. RFCOMM socket connected to $devName [$devMac]"
            Log.i(TAG, openMsg)
            logCallback?.invoke(openMsg)
            true
        } catch (e: Exception) {
            val streamErr = "Failed to obtain I/O streams: ${e.message}"
            Log.e(TAG, streamErr)
            logCallback?.invoke("ERR: $streamErr")
            disconnect()
            false
        }
    }

    /**
     * Connects with timeout in a background thread; explicitly calls socket.close()
     * if the timeout occurs to unblock the native socket.connect() call.
     */
    private fun connectWithTimeout(sock: BluetoothSocket, timeoutMs: Long) {
        val completed = AtomicBoolean(false)
        var exception: Exception? = null

        val thread = Thread({
            try {
                sock.connect()
                completed.set(true)
            } catch (e: Exception) {
                exception = e
            }
        }, "BT-Connect-Thread")

        thread.start()

        try {
            thread.join(timeoutMs)
        } catch (e: InterruptedException) {
            try { sock.close() } catch (_: Exception) {}
            throw IOException("Socket connect interrupted", e)
        }

        if (!completed.get()) {
            // Unblock and abort native connect call by closing socket
            try { sock.close() } catch (_: Exception) {}
            if (exception != null) {
                throw exception!!
            }
            throw IOException("BluetoothSocket.connect() timed out after ${timeoutMs}ms")
        }

        if (exception != null) {
            throw exception!!
        }
    }

    /**
     * Serialized command execution using commandMutex.
     * Appends '\r', reads until '>', tracks timeout, returns structured ElmResponse.
     */
    suspend fun sendCommand(
        cmd: String,
        timeoutMs: Long = 2000L
    ): ElmResponse = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val out = outStream ?: return@withLock ElmResponse(cmd, "", promptReceived = false, timedOut = true, elapsedMs = 0)
            val input = inStream ?: return@withLock ElmResponse(cmd, "", promptReceived = false, timedOut = true, elapsedMs = 0)

            val startTime = System.currentTimeMillis()

            // If the previous command timed out, attempt buffer recovery
            if (lastCommandTimedOut.get()) {
                Log.w(TAG, "Previous command timed out. Performing input recovery to '>' prompt...")
                rxLeftover.setLength(0)
                val recovered = ElmPromptRecovery.recoverToPrompt(
                    input,
                    timeoutMs = 1200L,
                    timeProvider = { SystemClock.elapsedRealtime() }
                )
                if (recovered) {
                    lastCommandTimedOut.set(false)
                    rxLeftover.setLength(0)
                    Log.i(TAG, "ELM prompt boundary recovered successfully.")
                } else {
                    rxLeftover.setLength(0)
                    val now = SystemClock.elapsedRealtimeNanos()
                    Log.e(TAG, "TRANSPORT_DESYNC: Prompt boundary NOT recovered. Aborting next command [$cmd].")
                    return@withLock ElmResponse(
                        command = cmd,
                        raw = "TRANSPORT_DESYNC",
                        promptReceived = false,
                        timedOut = true,
                        elapsedMs = 1200L,
                        txNanos = 0L,
                        rxNanos = now
                    )
                }
            }

            var txNanos = 0L
            // Write command with Carriage Return (\r)
            try {
                val toSend = (cmd.trim() + "\r").toByteArray(Charsets.US_ASCII)
                txNanos = SystemClock.elapsedRealtimeNanos()
                out.write(toSend)
                out.flush()
            } catch (e: Exception) {
                val rxNanos = SystemClock.elapsedRealtimeNanos()
                Log.e(TAG, "Write error for [$cmd]: ${e.message}")
                return@withLock ElmResponse(cmd, "", promptReceived = false, timedOut = true, elapsedMs = System.currentTimeMillis() - startTime, txNanos = txNanos, rxNanos = rxNanos)
            }

            val sb = StringBuilder()
            var promptSeen = false

            // Drain leftover characters from previous reads first
            if (rxLeftover.isNotEmpty()) {
                val existing = rxLeftover.toString()
                rxLeftover.setLength(0)
                for (idx in existing.indices) {
                    val c = existing[idx]
                    if (c == '>') {
                        promptSeen = true
                        if (idx + 1 < existing.length) {
                            rxLeftover.append(existing.substring(idx + 1))
                        }
                        break
                    }
                    if (c == '\r') {
                        sb.append('\n')
                    } else if (c != '\u0000') {
                        sb.append(c)
                    }
                }
            }

            val buffer = ByteArray(512)

            while (!promptSeen && System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val available = input.available()
                    if (available > 0) {
                        val count = input.read(buffer, 0, minOf(available, buffer.size))
                        if (count <= 0) break
                        for (i in 0 until count) {
                            val c = buffer[i].toInt().toChar()
                            if (c == '>') {
                                promptSeen = true
                                if (i + 1 < count) {
                                    for (j in (i + 1) until count) {
                                        val rem = buffer[j].toInt().toChar()
                                        if (rem != '\u0000') {
                                            rxLeftover.append(rem)
                                        }
                                    }
                                }
                                break
                            }
                            if (c == '\r') {
                                sb.append('\n')
                            } else if (c != '\u0000') {
                                sb.append(c)
                            }
                        }
                        if (promptSeen) break
                    } else {
                        Thread.sleep(1)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Read error for [$cmd]: ${e.message}")
                    break
                }
            }

            val rxNanos = SystemClock.elapsedRealtimeNanos()
            val elapsed = if (txNanos > 0L) (rxNanos - txNanos) / 1_000_000L else (System.currentTimeMillis() - startTime)
            val raw = sb.toString().trim()

            if (!promptSeen) {
                lastCommandTimedOut.set(true)
                Log.w(TAG, "ELM CMD [$cmd] TIMED OUT after ${elapsed}ms! Response so far: [$raw]")
            } else {
                Log.d(TAG, "ELM CMD [$cmd] -> [$raw] (${elapsed}ms)")
            }

            return@withLock ElmResponse(
                command = cmd,
                raw = raw,
                promptReceived = promptSeen,
                timedOut = !promptSeen,
                elapsedMs = elapsed,
                txNanos = txNanos,
                rxNanos = rxNanos
            )
        }
    }

    /**
     * Executes multiple ELM commands in a single pipelined write without roundtrip latency.
     * Crucial for meeting microsecond/millisecond bus timing deadlines (e.g. VW TP 2.0 T_E = 100ms).
     */
    suspend fun sendPipelinedCommands(
        cmds: List<String>,
        timeoutMs: Long = 2500L
    ): ElmResponse = withContext(Dispatchers.IO) {
        commandMutex.withLock {
            val out = outStream ?: return@withLock ElmResponse(cmds.joinToString(";"), "", promptReceived = false, timedOut = true, elapsedMs = 0)
            val input = inStream ?: return@withLock ElmResponse(cmds.joinToString(";"), "", promptReceived = false, timedOut = true, elapsedMs = 0)

            val startTime = System.currentTimeMillis()
            if (lastCommandTimedOut.get()) {
                Log.w(TAG, "Previous command timed out. Performing input recovery to '>' prompt before pipelined write...")
                rxLeftover.setLength(0)
                val recovered = ElmPromptRecovery.recoverToPrompt(
                    input,
                    timeoutMs = 1200L,
                    timeProvider = { SystemClock.elapsedRealtime() }
                )
                if (recovered) {
                    lastCommandTimedOut.set(false)
                    rxLeftover.setLength(0)
                    Log.i(TAG, "ELM prompt boundary recovered successfully before pipelined write.")
                } else {
                    rxLeftover.setLength(0)
                    val now = SystemClock.elapsedRealtimeNanos()
                    Log.e(TAG, "TRANSPORT_DESYNC: Prompt boundary NOT recovered before pipelined write. Aborting.")
                    return@withLock ElmResponse(
                        command = cmds.joinToString(";"),
                        raw = "TRANSPORT_DESYNC",
                        promptReceived = false,
                        timedOut = true,
                        elapsedMs = 1200L,
                        txNanos = 0L,
                        rxNanos = now
                    )
                }
            }

            val combinedPayload = cmds.joinToString("\r", postfix = "\r") { it.trim() }
            var txNanos = 0L
            try {
                txNanos = SystemClock.elapsedRealtimeNanos()
                out.write(combinedPayload.toByteArray(Charsets.US_ASCII))
                out.flush()
            } catch (e: Exception) {
                val rxNanos = SystemClock.elapsedRealtimeNanos()
                Log.e(TAG, "Pipelined write error: ${e.message}")
                return@withLock ElmResponse(combinedPayload, "", promptReceived = false, timedOut = true, elapsedMs = System.currentTimeMillis() - startTime, txNanos = txNanos, rxNanos = rxNanos)
            }

            val expectedPrompts = cmds.size
            var promptsSeen = 0
            val sb = StringBuilder()
            val buffer = ByteArray(512)

            while (System.currentTimeMillis() - startTime < timeoutMs) {
                try {
                    val available = input.available()
                    if (available > 0) {
                        val count = input.read(buffer, 0, minOf(available, buffer.size))
                        if (count <= 0) break
                        for (i in 0 until count) {
                            val c = buffer[i].toInt().toChar()
                            if (c == '>') {
                                promptsSeen++
                                if (promptsSeen >= expectedPrompts) break
                            } else if (c == '\r') {
                                sb.append('\n')
                            } else if (c != '\u0000') {
                                sb.append(c)
                            }
                        }
                        if (promptsSeen >= expectedPrompts) break
                    } else {
                        Thread.sleep(1)
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "Pipelined read error: ${e.message}")
                    break
                }
            }

            val rxNanos = SystemClock.elapsedRealtimeNanos()
            val elapsed = if (txNanos > 0L) (rxNanos - txNanos) / 1_000_000L else (System.currentTimeMillis() - startTime)
            val raw = sb.toString().trim()
            val allPrompts = promptsSeen >= expectedPrompts

            if (!allPrompts) {
                lastCommandTimedOut.set(true)
                Log.w(TAG, "Pipelined [${cmds.joinToString(";")}] TIMED OUT (prompts $promptsSeen/$expectedPrompts) after ${elapsed}ms: [$raw]")
            } else {
                Log.d(TAG, "Pipelined [${cmds.joinToString(";")}] -> [$raw] (${elapsed}ms)")
            }

            return@withLock ElmResponse(
                command = combinedPayload,
                raw = raw,
                promptReceived = allPrompts,
                timedOut = !allPrompts,
                elapsedMs = elapsed,
                txNanos = txNanos,
                rxNanos = rxNanos
            )
        }
    }

    private fun recoverInputBuffer(input: InputStream, recoveryTimeoutMs: Long): Boolean {
        return ElmPromptRecovery.recoverToPrompt(
            input,
            timeoutMs = recoveryTimeoutMs,
            timeProvider = { SystemClock.elapsedRealtime() }
        )
    }

    fun disconnect() {
        try { inStream?.close() } catch (_: Exception) {}
        try { outStream?.close() } catch (_: Exception) {}
        try { socket?.close() } catch (_: Exception) {}
        inStream = null
        outStream = null
        socket = null
        connectedDeviceName = null
        connectedDeviceAddress = null
        lastCommandTimedOut.set(false)
        rxLeftover.setLength(0)
        Log.i(TAG, "Bluetooth RFCOMM disconnected and cleaned up")
    }
}
