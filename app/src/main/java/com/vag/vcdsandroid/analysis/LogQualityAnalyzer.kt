package com.vag.vcdsandroid.analysis

import java.io.BufferedReader
import java.io.File
import java.io.FileReader
import java.io.StringReader
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class TurboPairRecord(
    val pairSeq: Long,
    val timestampUtcMs: Long,
    val monoNs: Long,
    val rpm: Double,
    val mapMbarAbs: Double,
    val baroMbar: Double?,
    val baroSource: String?,
    val boostMbar: Double?,
    val boostBar: Double?,
    val dtMapRpmMs: Long,
    val pairValid: Boolean,
    val invalidReason: String?,
    val mafGs: Double?,
    val mafAgeMs: Long?,
    val speedKmh: Double?,
    val speedAgeMs: Long?,
    val loadPct: Double?,
    val loadAgeMs: Long?,
    val coolantC: Double?,
    val coolantAgeMs: Long?,
    val iatC: Double?,
    val iatAgeMs: Long?,
    val voltageV: Double?,
    val voltageAgeMs: Long?,
    val latencyMs: Long
)

enum class PullVerdict {
    PASS_ACCEPTANCE_PULL,
    INCOMPLETE_PULL_WARNING,
    POOR_DATA_QUALITY_ERROR
}

enum class GearVerdict {
    VERIFIED_4TH_GEAR,
    DETECTED_3RD_GEAR,
    DETECTED_2ND_GEAR,
    OTHER_GEAR_DETECTED,
    NOT_VERIFIED_INSUFFICIENT_DATA
}

data class SpeedRpmRatioObservation(
    val pairSeq: Long,
    val rpm: Double,
    val speedKmh: Double,
    val speedAgeMs: Long,
    val kmhPer1000Rpm: Double
)

data class LogQualityReport(
    val totalPairs: Int,
    val validPairs: Int,
    val validPairPct: Double,
    val minRpm: Double?,
    val maxRpm: Double?,
    val peakMapMbar: Double?,
    val peakBoostBar: Double?,
    val baroSource: String?,
    val baroStable: Boolean,
    val minBaroMbar: Double?,
    val maxBaroMbar: Double?,
    val maxMafAgeMs: Long?,
    val maxSpeedAgeMs: Long?,
    val maxLoadAgeMs: Long?,
    val observedRatioKmhPer1000Rpm: Double?,
    val ratioObservations: List<SpeedRpmRatioObservation>,
    val gearVerdict: GearVerdict,
    val gearConfidenceNotes: String,
    val hasRpmCoverage1300To4000: Boolean,
    val pullStartRpm: Double?,
    val pullPeakRpm: Double?,
    val hasFreshSpoolLoad: Boolean,
    val overallVerdict: PullVerdict,
    val verdictSummary: String,
    val actionableChecklist: List<String>
) {
    fun formatHumanReadable(): String {
        val sb = StringBuilder()
        val verdictIcon = when (overallVerdict) {
            PullVerdict.PASS_ACCEPTANCE_PULL -> "🟢 [ІДЕАЛЬНИЙ ЗВІТ / ACCEPTANCE PASS]"
            PullVerdict.INCOMPLETE_PULL_WARNING -> "🟡 [ПОТРІБНЕ ДООПРАЦЮВАННЯ / WARNING]"
            PullVerdict.POOR_DATA_QUALITY_ERROR -> "🔴 [ПОМИЛКА ЯКОСТІ / ERROR]"
        }
        sb.appendLine(verdictIcon)
        sb.appendLine(verdictSummary)
        sb.appendLine()
        sb.appendLine("📊 МЕТРИКИ ЗАЇЗДУ:")
        sb.appendLine("• Валідні пари: $validPairs / $totalPairs (${String.format(Locale.US, "%.1f", validPairPct)}%)")
        sb.appendLine("• Діапазон RPM: ${minRpm?.roundToInt() ?: "-"} .. ${maxRpm?.roundToInt() ?: "-"} об/хв")
        sb.appendLine("• Піковий наддув: ${String.format(Locale.US, "%.3f", peakBoostBar ?: 0.0)} бар (${peakMapMbar?.roundToInt() ?: "-"} mbar MAP)")
        sb.appendLine("• Барометр: ${String.format(Locale.US, "%.1f", minBaroMbar ?: 0.0)}..${String.format(Locale.US, "%.1f", maxBaroMbar ?: 0.0)} mbar (${if (baroStable) "СТАБІЛЬНИЙ" else "НЕСТАБІЛЬНИЙ"}, $baroSource)")
        sb.appendLine("• Макс. вік aux: MAF=${maxMafAgeMs ?: "-"}ms, Speed=${maxSpeedAgeMs ?: "-"}ms, Load=${maxLoadAgeMs ?: "-"}ms")
        sb.appendLine()
        sb.appendLine("🚗 ПЕРЕДАЧА ТА СПІВВІДНОШЕННЯ:")
        if (observedRatioKmhPer1000Rpm != null) {
            sb.appendLine("• Розраховано: ${String.format(Locale.US, "%.1f", observedRatioKmhPer1000Rpm)} км/год / 1000 RPM (точок: ${ratioObservations.size})")
        }
        sb.appendLine("• Статус передачі: $gearConfidenceNotes")
        sb.appendLine()
        sb.appendLine("🎯 ЗОНИ НАДДУВУ:")
        val rpmCoverageStr = if (hasRpmCoverage1300To4000) "✅ 1300 → 4000 RPM покрито" else "⚠️ Лише ${pullStartRpm?.roundToInt() ?: "-"} .. ${pullPeakRpm?.roundToInt() ?: "-"} RPM (потрібно до 4000)"
        sb.appendLine("• Охоплення RPM: $rpmCoverageStr")
        val spoolStr = if (hasFreshSpoolLoad) "✅ Зафіксовано свіжий WOT у 1500–1900 RPM" else "⚠️ Немає свіжого WOT (Load >= 80%) у 1500–1900 RPM"
        sb.appendLine("• Ранній спул (1500-1900): $spoolStr")

        if (actionableChecklist.isNotEmpty()) {
            sb.appendLine()
            sb.appendLine("📋 РЕКОМЕНДАЦІЇ ДЛЯ НАСТУПНОГО ЗАЇЗДУ:")
            for (action in actionableChecklist) {
                sb.appendLine("• $action")
            }
        }
        return sb.toString().trimEnd()
    }
}

object LogQualityAnalyzer {

    // Golf 5 1.9 TDI (0A4 5-speed manual, tire ~1.94m) expected km/h per 1000 RPM
    const val RATIO_2ND_MIN = 13.5
    const val RATIO_2ND_MAX = 18.0
    const val RATIO_3RD_MIN = 22.5
    const val RATIO_3RD_MAX = 27.5
    const val RATIO_4TH_MIN = 32.5
    const val RATIO_4TH_MAX = 38.5
    const val RATIO_5TH_MIN = 43.0
    const val RATIO_5TH_MAX = 50.0

    fun analyze(file: File): LogQualityReport {
        val pairs = parseCsv(file)
        return analyzePairs(pairs)
    }

    fun analyze(lines: List<String>): LogQualityReport {
        val pairs = parseCsvLines(lines)
        return analyzePairs(pairs)
    }

    fun parseCsv(file: File): List<TurboPairRecord> {
        if (!file.exists()) return emptyList()
        val lines = mutableListOf<String>()
        BufferedReader(FileReader(file)).use { reader ->
            var line: String? = reader.readLine()
            while (line != null) {
                if (line.isNotBlank()) lines.add(line)
                line = reader.readLine()
            }
        }
        return parseCsvLines(lines)
    }

    fun parseCsvLines(lines: List<String>): List<TurboPairRecord> {
        if (lines.size <= 1) return emptyList()
        val header = lines[0].split(",").map { it.trim() }
        val colMap = header.withIndex().associate { it.value to it.index }

        val records = mutableListOf<TurboPairRecord>()
        for (i in 1 until lines.size) {
            val line = lines[i]
            val cols = line.split(",").map { it.trim() }
            if (cols.size < 5) continue

            fun getDouble(name: String): Double? {
                val idx = colMap[name] ?: return null
                if (idx >= cols.size) return null
                val v = cols[idx]
                return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v.toDoubleOrNull()
            }

            fun getLong(name: String): Long? {
                val idx = colMap[name] ?: return null
                if (idx >= cols.size) return null
                val v = cols[idx]
                return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v.toLongOrNull()
            }

            fun getString(name: String): String? {
                val idx = colMap[name] ?: return null
                if (idx >= cols.size) return null
                val v = cols[idx]
                return if (v.isEmpty() || v.equals("null", ignoreCase = true)) null else v
            }

            fun getBoolean(name: String): Boolean {
                val idx = colMap[name] ?: return false
                if (idx >= cols.size) return false
                return cols[idx].equals("true", ignoreCase = true)
            }

            val pairSeq = getLong("pair_seq") ?: i.toLong()
            val timestampUtc = getLong("timestamp_utc_ms") ?: 0L
            val monoNs = getLong("mono_ns") ?: 0L
            val rpm = getDouble("rpm") ?: 0.0
            val mapMbarAbs = getDouble("map_mbar_abs") ?: 0.0
            val baroMbar = getDouble("baro_mbar")
            val baroSource = getString("baro_source")
            val boostMbar = getDouble("boost_mbar")
            val boostBar = getDouble("boost_bar")
            val dtMapRpmMs = getLong("dt_map_rpm_ms") ?: 0L
            val pairValid = getBoolean("pair_valid")
            val invalidReason = getString("invalid_reason")
            val mafGs = getDouble("maf_g_s")
            val mafAgeMs = getLong("maf_age_ms")
            val speedKmh = getDouble("speed_kmh")
            val speedAgeMs = getLong("speed_age_ms")
            val loadPct = getDouble("load_pct")
            val loadAgeMs = getLong("load_age_ms")
            val coolantC = getDouble("coolant_c")
            val coolantAgeMs = getLong("coolant_age_ms")
            val iatC = getDouble("iat_c")
            val iatAgeMs = getLong("iat_age_ms")
            val voltageV = getDouble("voltage_v")
            val voltageAgeMs = getLong("voltage_age_ms")
            val latencyMs = getLong("latency_ms") ?: 0L

            records.add(
                TurboPairRecord(
                    pairSeq = pairSeq,
                    timestampUtcMs = timestampUtc,
                    monoNs = monoNs,
                    rpm = rpm,
                    mapMbarAbs = mapMbarAbs,
                    baroMbar = baroMbar,
                    baroSource = baroSource,
                    boostMbar = boostMbar,
                    boostBar = boostBar,
                    dtMapRpmMs = dtMapRpmMs,
                    pairValid = pairValid,
                    invalidReason = invalidReason,
                    mafGs = mafGs,
                    mafAgeMs = mafAgeMs,
                    speedKmh = speedKmh,
                    speedAgeMs = speedAgeMs,
                    loadPct = loadPct,
                    loadAgeMs = loadAgeMs,
                    coolantC = coolantC,
                    coolantAgeMs = coolantAgeMs,
                    iatC = iatC,
                    iatAgeMs = iatAgeMs,
                    voltageV = voltageV,
                    voltageAgeMs = voltageAgeMs,
                    latencyMs = latencyMs
                )
            )
        }
        return records
    }

    fun analyzePairs(pairs: List<TurboPairRecord>): LogQualityReport {
        val totalPairs = pairs.size
        if (totalPairs == 0) {
            return LogQualityReport(
                totalPairs = 0,
                validPairs = 0,
                validPairPct = 0.0,
                minRpm = null,
                maxRpm = null,
                peakMapMbar = null,
                peakBoostBar = null,
                baroSource = null,
                baroStable = false,
                minBaroMbar = null,
                maxBaroMbar = null,
                maxMafAgeMs = null,
                maxSpeedAgeMs = null,
                maxLoadAgeMs = null,
                observedRatioKmhPer1000Rpm = null,
                ratioObservations = emptyList(),
                gearVerdict = GearVerdict.NOT_VERIFIED_INSUFFICIENT_DATA,
                gearConfidenceNotes = "Лог порожній",
                hasRpmCoverage1300To4000 = false,
                pullStartRpm = null,
                pullPeakRpm = null,
                hasFreshSpoolLoad = false,
                overallVerdict = PullVerdict.POOR_DATA_QUALITY_ERROR,
                verdictSummary = "Файл логу не містить записів",
                actionableChecklist = listOf("Перевірте з'єднання з адаптером та повторіть заїзд")
            )
        }

        val validPairsList = pairs.filter { it.pairValid }
        val validPairsCount = validPairsList.size
        val validPairPct = (validPairsCount.toDouble() / totalPairs.toDouble()) * 100.0

        val minRpm = validPairsList.map { it.rpm }.filter { it > 0.0 }.minOrNull()
        val maxRpm = validPairsList.map { it.rpm }.maxOrNull()
        val peakMapMbar = validPairsList.map { it.mapMbarAbs }.maxOrNull()
        val peakBoostBar = validPairsList.mapNotNull { it.boostBar }.maxOrNull()

        // BARO continuity
        val baroValues = validPairsList.mapNotNull { it.baroMbar }
        val minBaroMbar = baroValues.minOrNull()
        val maxBaroMbar = baroValues.maxOrNull()
        val baroStable = if (minBaroMbar != null && maxBaroMbar != null) {
            (maxBaroMbar - minBaroMbar) <= 5.0
        } else false
        val baroSource = validPairsList.firstNotNullOfOrNull { it.baroSource } ?: "UNKNOWN"

        // Aux maximum ages
        val maxMafAgeMs = validPairsList.mapNotNull { it.mafAgeMs }.maxOrNull()
        val maxSpeedAgeMs = validPairsList.mapNotNull { it.speedAgeMs }.maxOrNull()
        val maxLoadAgeMs = validPairsList.mapNotNull { it.loadAgeMs }.maxOrNull()

        // Gear ratio analysis: speed_age <= 1000 ms, speed >= 15 km/h, rpm >= 1200
        val ratioObs = mutableListOf<SpeedRpmRatioObservation>()
        for (p in validPairsList) {
            val spd = p.speedKmh
            val spdAge = p.speedAgeMs
            if (spd != null && spdAge != null && spd >= 15.0 && p.rpm >= 1200.0 && spdAge <= 1000L) {
                val ratio = (spd * 1000.0) / p.rpm
                ratioObs.add(
                    SpeedRpmRatioObservation(
                        pairSeq = p.pairSeq,
                        rpm = p.rpm,
                        speedKmh = spd,
                        speedAgeMs = spdAge,
                        kmhPer1000Rpm = ratio
                    )
                )
            }
        }

        // Determine median ratio from high-load / acceleration section if possible
        val observedRatio: Double? = if (ratioObs.isNotEmpty()) {
            val sorted = ratioObs.map { it.kmhPer1000Rpm }.sorted()
            sorted[sorted.size / 2]
        } else null

        val (gearVerdict, gearNotes) = when {
            observedRatio == null || ratioObs.size < 2 -> {
                GearVerdict.NOT_VERIFIED_INSUFFICIENT_DATA to "Недостатньо свіжих точок швидкості (вік <= 1000 мс) для верифікації передачі"
            }
            observedRatio in RATIO_4TH_MIN..RATIO_4TH_MAX -> {
                GearVerdict.VERIFIED_4TH_GEAR to "4-а передача підтверджена (~${String.format(Locale.US, "%.1f", observedRatio)} км/год на 1000 RPM)"
            }
            observedRatio in RATIO_3RD_MIN..RATIO_3RD_MAX -> {
                GearVerdict.DETECTED_3RD_GEAR to "Виявлено 3-ю передачу (~${String.format(Locale.US, "%.1f", observedRatio)} км/год на 1000 RPM). Потрібна 4-а передача!"
            }
            observedRatio in RATIO_2ND_MIN..RATIO_2ND_MAX -> {
                GearVerdict.DETECTED_2ND_GEAR to "Виявлено 2-у передачу (~${String.format(Locale.US, "%.1f", observedRatio)} км/год на 1000 RPM). Потрібна 4-а передача!"
            }
            else -> {
                GearVerdict.OTHER_GEAR_DETECTED to "Виявлено невідому передачу (${String.format(Locale.US, "%.1f", observedRatio)} км/год / 1000 RPM). Потрібна 4-а передача!"
            }
        }

        // Pull detection: find the primary acceleration pull under load/boost
        var longestPullStartRpm: Double? = null
        var longestPullPeakRpm: Double? = null
        var currentStartRpm: Double? = null
        var currentPeakRpm: Double? = null
        var prevRpm = 0.0

        for (p in validPairsList) {
            val r = p.rpm
            if (r > prevRpm - 100.0) { // allow small jitter up to 100 rpm during continuous pull
                if (currentStartRpm == null) currentStartRpm = r
                if (currentPeakRpm == null || r > currentPeakRpm) currentPeakRpm = r
            } else {
                // Segment ended
                if (currentStartRpm != null && currentPeakRpm != null) {
                    val currentSpan = currentPeakRpm - currentStartRpm
                    val longestSpan = if (longestPullStartRpm != null && longestPullPeakRpm != null) {
                        longestPullPeakRpm - longestPullStartRpm
                    } else 0.0
                    if (currentSpan > longestSpan) {
                        longestPullStartRpm = currentStartRpm
                        longestPullPeakRpm = currentPeakRpm
                    }
                }
                currentStartRpm = r
                currentPeakRpm = r
            }
            prevRpm = r
        }
        if (currentStartRpm != null && currentPeakRpm != null) {
            val currentSpan = currentPeakRpm - currentStartRpm
            val longestSpan = if (longestPullStartRpm != null && longestPullPeakRpm != null) {
                longestPullPeakRpm - longestPullStartRpm
            } else 0.0
            if (currentSpan > longestSpan) {
                longestPullStartRpm = currentStartRpm
                longestPullPeakRpm = currentPeakRpm
            }
        }

        val pullStart = longestPullStartRpm ?: minRpm
        val pullPeak = longestPullPeakRpm ?: maxRpm
        val hasRpmCoverage = (pullStart != null && pullStart <= 1450.0 && pullPeak != null && pullPeak >= 3900.0)

        // Spool load check: in 1500..1900 RPM, is there fresh load >= 80% with age <= 1200 ms?
        var hasFreshSpoolLoad = false
        for (p in validPairsList) {
            val r = p.rpm
            val ld = p.loadPct
            val ldAge = p.loadAgeMs
            if (r in 1500.0..1900.0 && ld != null && ldAge != null) {
                if (ld >= 80.0 && ldAge <= 1200L && (p.boostBar ?: 0.0) >= 0.2) {
                    hasFreshSpoolLoad = true
                    break
                }
            }
        }

        // Actionable checklist & overall verdict
        val checklist = mutableListOf<String>()
        val overallVerdict: PullVerdict
        val verdictSummary: String

        if (validPairPct < 90.0 || totalPairs < 8) {
            overallVerdict = PullVerdict.POOR_DATA_QUALITY_ERROR
            verdictSummary = "Низька якість зв'язку з ЕБУ: лише ${String.format(Locale.US, "%.1f", validPairPct)}% валідних пар."
            checklist.add("Перевірте надійність підключення адаптера в роз'ємі OBD.")
            checklist.add("Переконайтеся, що напруга АКБ стабільна і немає радіозавад.")
        } else {
            if (gearVerdict != GearVerdict.VERIFIED_4TH_GEAR) {
                if (gearVerdict == GearVerdict.DETECTED_3RD_GEAR) {
                    checklist.add("УВАГА: Заїзд виконано на 3-й передачі! Для розрахунку та правильного навантаження турбіни увімкніть 4-у передачу.")
                } else {
                    checklist.add("Перевірте передачу: очікується 4-а передача (~35.4 км/год на 1000 RPM).")
                }
            }

            if (!hasRpmCoverage) {
                val peakRpmVal = pullPeak?.roundToInt() ?: 0
                if (peakRpmVal < 3900) {
                    checklist.add("Оберти досягли лише $peakRpmVal RPM: тримайте газ довше, поки тахометр не досягне ~4000 RPM.")
                }
                val startRpmVal = pullStart?.roundToInt() ?: 0
                if (startRpmVal > 1450) {
                    checklist.add("Початок запису на $startRpmVal RPM запізній: починайте плавний вихід на WOT з 1200-1300 RPM.")
                }
            }

            if (!hasFreshSpoolLoad) {
                checklist.add("Зона спулу 1500–1900 RPM без свіжого WOT (навантаження < 80%): натискайте педаль у підлогу завчасно (на ~1200 RPM у 4-й передачі).")
            }

            if (!baroStable) {
                checklist.add("Барометр змінювався більше ніж на 5 mbar під час заїзду.")
            }

            if (checklist.isEmpty()) {
                overallVerdict = PullVerdict.PASS_ACCEPTANCE_PULL
                verdictSummary = "Ідеальний заїзд! 4-а передача, повний діапазон 1300→4000 RPM та валідні дані наддуву зафіксовані."
            } else {
                overallVerdict = PullVerdict.INCOMPLETE_PULL_WARNING
                verdictSummary = "Лог записано успішно, але заїзд не задовольняє всім вимогам калібрування (передача, діапазон або ранній WOT)."
            }
        }

        return LogQualityReport(
            totalPairs = totalPairs,
            validPairs = validPairsCount,
            validPairPct = validPairPct,
            minRpm = minRpm,
            maxRpm = maxRpm,
            peakMapMbar = peakMapMbar,
            peakBoostBar = peakBoostBar,
            baroSource = baroSource,
            baroStable = baroStable,
            minBaroMbar = minBaroMbar,
            maxBaroMbar = maxBaroMbar,
            maxMafAgeMs = maxMafAgeMs,
            maxSpeedAgeMs = maxSpeedAgeMs,
            maxLoadAgeMs = maxLoadAgeMs,
            observedRatioKmhPer1000Rpm = observedRatio,
            ratioObservations = ratioObs,
            gearVerdict = gearVerdict,
            gearConfidenceNotes = gearNotes,
            hasRpmCoverage1300To4000 = hasRpmCoverage,
            pullStartRpm = pullStart,
            pullPeakRpm = pullPeak,
            hasFreshSpoolLoad = hasFreshSpoolLoad,
            overallVerdict = overallVerdict,
            verdictSummary = verdictSummary,
            actionableChecklist = checklist
        )
    }
}
