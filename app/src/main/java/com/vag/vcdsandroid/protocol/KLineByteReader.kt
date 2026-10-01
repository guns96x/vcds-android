package com.vag.vcdsandroid.protocol

/**
 * Serves K-Line bytes one at a time on top of a USB serial port.
 *
 * The five-baud handshake must inspect single bytes (sync 0x55, KB1, KB2,
 * ~address), but a USB serial read cannot be one byte long: the FTDI driver in
 * usb-serial-for-android 3.x throws `IllegalArgumentException("Read buffer too
 * small")` for any buffer of 2 bytes or less, and a bulk read shorter than the
 * endpoint packet size can overflow and drop data. Reading into a 1-byte array
 * therefore never returned a byte and the slow init always timed out waiting
 * for sync.
 *
 * This reader always reads whole packets into [READ_CHUNK_BYTES] and queues
 * whatever arrives, so sync and both key bytes are kept even when the ECU sends
 * them inside the same USB packet. Kept Android-free so it is unit tested.
 *
 * @param readChunk reads into the given buffer with a timeout in ms and returns
 *   the byte count (0 on timeout). Exceptions propagate to the caller.
 * @param nanoTime monotonic clock, injectable for tests.
 */
class KLineByteReader(
    private val readChunk: (buffer: ByteArray, timeoutMs: Int) -> Int,
    private val nanoTime: () -> Long = System::nanoTime
) {
    companion object {
        /** A multiple of the 64-byte full-speed bulk packet size. */
        const val READ_CHUNK_BYTES = 256

        /** Upper bound for one blocking USB read so deadlines stay accurate. */
        const val MAX_READ_SLICE_MS = 5L
    }

    private val chunk = ByteArray(READ_CHUNK_BYTES)
    private val pending = ArrayDeque<Int>()

    /** Monotonic time at which the most recent USB chunk was received. */
    var lastChunkNs: Long = 0L
        private set

    /** Bytes received but not yet consumed. */
    val pendingCount: Int get() = pending.size

    /** Returns the next byte (0..255) or null if none arrives before [deadlineNs]. */
    fun readByteUntil(deadlineNs: Long): Int? {
        while (pending.isEmpty()) {
            val now = nanoTime()
            if (now >= deadlineNs) return null
            val sliceMs = ((deadlineNs - now) / 1_000_000L)
                .coerceAtLeast(1L)
                .coerceAtMost(MAX_READ_SLICE_MS)
                .toInt()
            val n = readChunk(chunk, sliceMs)
            if (n > 0) {
                lastChunkNs = nanoTime()
                for (i in 0 until minOf(n, chunk.size)) pending.addLast(chunk[i].toInt() and 0xFF)
            }
        }
        return pending.removeFirst()
    }

    /** Discards queued bytes; the caller purges the hardware buffers separately. */
    fun clear() {
        pending.clear()
    }
}
