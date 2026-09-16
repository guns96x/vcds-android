package com.vag.vcdsandroid.sensors

data class PhoneBaroReading(
    val valueMbar: Double?,
    val available: Boolean,
    val fresh: Boolean,
    val ageMs: Long,
    val monoNs: Long = 0L
)

class BarometerMedianFilter(
    private val windowSize: Int = 10,
    private val minPlausibleHpa: Double = 800.0,
    private val maxPlausibleHpa: Double = 1100.0,
    private val maxFreshAgeNs: Long = 5_000_000_000L, // 5000 ms
    private val maxGapBetweenSamplesNs: Long = 5_000_000_000L
) {
    private val samples = ArrayDeque<Double>(windowSize)
    private var lastSampleMonoNs: Long = 0L

    @Synchronized
    fun addSample(hpa: Double, monoNs: Long) {
        if (hpa !in minPlausibleHpa..maxPlausibleHpa) return

        // If time gap since last sample exceeds max gap (e.g. after background pause), clear stale window
        if (lastSampleMonoNs > 0L && (monoNs - lastSampleMonoNs) > maxGapBetweenSamplesNs) {
            samples.clear()
        }

        lastSampleMonoNs = monoNs
        if (samples.size >= windowSize) {
            samples.removeFirst()
        }
        samples.addLast(hpa)
    }

    @Synchronized
    fun getMedianReading(nowNs: Long): PhoneBaroReading {
        if (samples.isEmpty()) {
            return PhoneBaroReading(
                valueMbar = null,
                available = false,
                fresh = false,
                ageMs = 0L
            )
        }

        val ageNs = (nowNs - lastSampleMonoNs).coerceAtLeast(0L)
        val ageMs = ageNs / 1_000_000L
        val isFresh = ageNs <= maxFreshAgeNs

        // If stale after maxFreshAgeNs, do not return stale reading
        if (!isFresh) {
            return PhoneBaroReading(
                valueMbar = null,
                available = true,
                fresh = false,
                ageMs = ageMs,
                monoNs = lastSampleMonoNs
            )
        }

        val sorted = samples.sorted()
        val median = if (sorted.size % 2 == 1) {
            sorted[sorted.size / 2]
        } else {
            val mid = sorted.size / 2
            (sorted[mid - 1] + sorted[mid]) / 2.0
        }

        return PhoneBaroReading(
            valueMbar = median,
            available = true,
            fresh = true,
            ageMs = ageMs,
            monoNs = lastSampleMonoNs
        )
    }

    @Synchronized
    fun clear() {
        samples.clear()
        lastSampleMonoNs = 0L
    }
}
