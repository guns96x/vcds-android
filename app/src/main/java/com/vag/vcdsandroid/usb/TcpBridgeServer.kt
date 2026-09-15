package com.vag.vcdsandroid.usb

import android.util.Log
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.ConcurrentHashMap

/**
 * Localhost TCP Bridge Server listening on 127.0.0.1:9999.
 * Provides interactive terminal access, debugging, and brute-forcing from PC
 * via 'adb forward tcp:9999 tcp:9999'.
 *
 * Implements Astra security & concurrency recommendations:
 * - Tracks and closes all accepted sockets on stop()
 * - Provides isBridgeActive flag to prevent serial collision with DiagEngine
 * - Framed command protocol
 */
class TcpBridgeServer(private val transport: UsbKwpTransport, private val port: Int = 9999) {
    private var serverSocket: ServerSocket? = null
    private var isRunning = false
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val activeSockets = ConcurrentHashMap.newKeySet<Socket>()

    val isBridgeActive: Boolean
        get() = activeSockets.isNotEmpty()

    fun start() {
        if (isRunning) return
        isRunning = true
        scope.launch {
            try {
                serverSocket = ServerSocket().apply {
                    reuseAddress = true
                    bind(InetSocketAddress("127.0.0.1", port))
                }
                Log.i("VCDS_TCP", "TCP Bridge Server listening on 127.0.0.1:$port")

                while (isRunning && isActive) {
                    try {
                        val client = serverSocket?.accept() ?: break
                        // Enforce single active client to avoid concurrent serial interleaving
                        for (prev in activeSockets) {
                            try { prev.close() } catch (_: Exception) {}
                        }
                        activeSockets.clear()
                        Log.i("VCDS_TCP", "Client connected: ${client.remoteSocketAddress}")
                        activeSockets.add(client)
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) Log.w("VCDS_TCP", "Accept error: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                Log.e("VCDS_TCP", "Server error: ${e.message}")
            }
        }
    }

    private fun handleClient(client: Socket) {
        scope.launch {
            val inputStream = client.getInputStream()
            val outputStream = client.getOutputStream()
            val rxBuffer = ByteArray(1024)

            // RX Coroutine: Read from USB -> Send to TCP client
            val rxJob = launch {
                val usbBuf = ByteArray(512)
                while (isActive && client.isConnected && !client.isClosed) {
                    try {
                        val n = transport.read(usbBuf, 60)
                        if (n > 0) {
                            outputStream.write(usbBuf, 0, n)
                            outputStream.flush()
                            val hexStr = usbBuf.take(n).joinToString(" ") { "%02X".format(it) }
                            Log.d("VCDS_TCP", "USB -> TCP ($n bytes): $hexStr")
                        }
                    } catch (e: Exception) {
                        if (!transport.isConnected() || client.isClosed) {
                            break
                        }
                    }
                    delay(5)
                }
            }

            // TX Loop: Read from TCP client -> Write to USB or handle control command
            try {
                while (isActive && client.isConnected && !client.isClosed) {
                    val n = inputStream.read(rxBuffer)
                    if (n <= 0) break

                    val text = String(rxBuffer, 0, n)
                    if (text.startsWith("@@CMD:")) {
                        handleCommand(text.trim(), outputStream)
                    } else {
                        val bytesToSend = rxBuffer.copyOf(n)
                        transport.write(bytesToSend)
                        val hexStr = bytesToSend.joinToString(" ") { "%02X".format(it) }
                        Log.d("VCDS_TCP", "TCP -> USB ($n bytes): $hexStr")
                    }
                }
            } catch (e: Exception) {
                Log.w("VCDS_TCP", "Client loop ended: ${e.message}")
            } finally {
                rxJob.cancel()
                activeSockets.remove(client)
                try { client.close() } catch (_: Exception) {}
                Log.i("VCDS_TCP", "Client disconnected")
            }
        }
    }

    private fun handleCommand(cmd: String, out: OutputStream) {
        Log.i("VCDS_TCP", "Control command: $cmd")
        val cleanCmd = cmd.removePrefix("@@CMD:").trim()
        val parts = cleanCmd.split(":")
        when (parts[0].uppercase()) {
            "BAUD" -> {
                val rate = parts.getOrNull(1)?.toIntOrNull() ?: 500000
                val ok = transport.setBaudRate(rate)
                out.write("OK:BAUD:$rate:$ok\n".toByteArray())
                out.flush()
            }
            "DTR" -> {
                val state = parts.getOrNull(1) == "1"
                transport.setDtr(state)
                out.write("OK:DTR:$state\n".toByteArray())
                out.flush()
            }
            "RTS" -> {
                val state = parts.getOrNull(1) == "1"
                transport.setRts(state)
                out.write("OK:RTS:$state\n".toByteArray())
                out.flush()
            }
            "PURGE" -> {
                transport.purge()
                out.write("OK:PURGED\n".toByteArray())
                out.flush()
            }
            "PING" -> {
                out.write("OK:PONG\n".toByteArray())
                out.flush()
            }
            "FASTINIT" -> {
                transport.sendFastInitPulse()
                out.write("OK:FASTINIT_DONE\n".toByteArray())
                out.flush()
            }
            else -> {
                out.write("ERR:UNKNOWN_CMD\n".toByteArray())
                out.flush()
            }
        }
    }

    fun stop() {
        isRunning = false
        try { serverSocket?.close() } catch (_: Exception) {}
        for (client in activeSockets) {
            try { client.close() } catch (_: Exception) {}
        }
        activeSockets.clear()
        scope.cancel()
    }
}
