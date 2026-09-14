package com.vag.vcdsandroid.ui

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.os.SystemClock
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vag.vcdsandroid.R
import com.vag.vcdsandroid.databinding.ActivityMainBinding
import com.vag.vcdsandroid.logging.AsyncCsvLogger
import com.vag.vcdsandroid.protocol.DiagState
import com.vag.vcdsandroid.protocol.Elm327DiagnosticEngine
import com.vag.vcdsandroid.protocol.ElmDiagnosticState
import com.vag.vcdsandroid.protocol.Kwp2000DiagnosticEngine
import com.vag.vcdsandroid.protocol.PidDecoder
import com.vag.vcdsandroid.protocol.PidStatus
import com.vag.vcdsandroid.protocol.TransportMode
import com.vag.vcdsandroid.usb.UsbKwpTransport
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

enum class AppConnectionMode {
    TURBO_FAST_OBD,   // Mode A: High-speed OBD-II RPM+MAP pairs (ELM327 Bluetooth)
    VAG_OEM_TP20,     // Mode B: VAG OEM Group 011/008/003 (TP 2.0 CAN)
    USB_HARDWARE,     // USB FTDI K-Line
    SIMULATOR_DEMO    // Virtual Demo
}

data class DiagnosticSample(
    val pid: String,
    val name: String,
    val value: Double?,
    val formattedValue: String,
    val unit: String,
    val status: PidStatus,
    val latencyMs: Long,
    val timestampUtcMs: Long,
    val monoNanos: Long,
    val rawResponse: String
) {
    fun getEffectiveStatus(nowNanos: Long = SystemClock.elapsedRealtimeNanos()): PidStatus {
        if (status != PidStatus.VALID) return status
        val ageMs = (nowNanos - monoNanos) / 1_000_000
        val thresholdMs = when (pid) {
            "010C", "010B" -> 800L
            "0110", "010D", "0104" -> 2000L
            else -> 10000L
        }
        return if (ageMs > thresholdMs) PidStatus.STALE else PidStatus.VALID
    }

    fun getAgeMs(nowNanos: Long = SystemClock.elapsedRealtimeNanos()): Long {
        return ((nowNanos - monoNanos) / 1_000_000).coerceAtLeast(0L)
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var transport: UsbKwpTransport
    private lateinit var engine: Kwp2000DiagnosticEngine
    private lateinit var elmEngine: Elm327DiagnosticEngine
    private lateinit var asyncLogger: AsyncCsvLogger

    private var connectionMode = AppConnectionMode.TURBO_FAST_OBD
    private var isPermissionRequested = false
    private var currentDevice: UsbDevice? = null

    private var pollingJob: Job? = null
    private var tickerJob: Job? = null
    private var preFlightJob: Job? = null

    private var calibratedBaroMbar: Double? = null
    private var calibratedBaroSource: String = "UNSET"
    private var isRawDebugExpanded = false

    // Diagnostic samples storage (thread-safe, shared between UI and CSV)
    private val latestSamples = ConcurrentHashMap<String, DiagnosticSample>()

    // Bus performance tracking
    private var busReqRate: Double = 0.0
    private var busRpmHz: Double = 0.0
    private var busMapHz: Double = 0.0
    private var busMafHz: Double = 0.0
    private var busAvgLatency: Long = 0L

    private var windowStart = 0L
    private var windowTotalReqs = 0
    private var windowRpmCount = 0
    private var windowMapCount = 0
    private var windowMafCount = 0
    private var windowLatencySum = 0L

    private var logStartUtcMs: Long = 0L

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbKwpTransport.ACTION_USB_PERMISSION) {
                isPermissionRequested = false
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                if (granted) {
                    currentDevice = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    updateStatusUI()
                    performConnect()
                } else {
                    updateStatusUI()
                    Toast.makeText(this@MainActivity, "USB Permission Denied", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private val usbDetachedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                currentDevice = null
                performDisconnect()
                updateStatusUI()
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transport = UsbKwpTransport(this)
        engine = Kwp2000DiagnosticEngine(this, transport)
        elmEngine = Elm327DiagnosticEngine(this)
        asyncLogger = AsyncCsvLogger(this)

        val permFilter = IntentFilter(UsbKwpTransport.ACTION_USB_PERMISSION)
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, permFilter, Context.RECEIVER_NOT_EXPORTED)
            registerReceiver(usbDetachedReceiver, detachFilter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, permFilter)
            registerReceiver(usbDetachedReceiver, detachFilter)
        }

        setupListeners()
        switchConnectionMode(AppConnectionMode.TURBO_FAST_OBD)
        startUiTicker()
    }

    override fun onDestroy() {
        tickerJob?.cancel()
        performDisconnect()
        try {
            unregisterReceiver(usbPermissionReceiver)
        } catch (_: Exception) {}
        try {
            unregisterReceiver(usbDetachedReceiver)
        } catch (_: Exception) {}
        super.onDestroy()
    }

    private fun setupListeners() {
        // Mode toggle button
        binding.btnModeToggle.setOnClickListener {
            val modes = arrayOf(
                "Mode A: Turbo Fast (OBD-II High Speed)",
                "Mode B: VAG OEM (Group 011 / TP 2.0)",
                "Mode C: USB FTDI (KKL Cable)",
                "Mode D: Virtual Simulator (Demo)"
            )
            AlertDialog.Builder(this)
                .setTitle("Select Diagnostic Mode")
                .setItems(modes) { _, which ->
                    val newMode = when (which) {
                        0 -> AppConnectionMode.TURBO_FAST_OBD
                        1 -> AppConnectionMode.VAG_OEM_TP20
                        2 -> AppConnectionMode.USB_HARDWARE
                        else -> AppConnectionMode.SIMULATOR_DEMO
                    }
                    switchConnectionMode(newMode)
                }
                .show()
        }

        // Connect button
        binding.btnConnect.setOnClickListener {
            val isConnected = when (connectionMode) {
                AppConnectionMode.TURBO_FAST_OBD, AppConnectionMode.VAG_OEM_TP20 ->
                    elmEngine.state == DiagState.CONNECTED || elmEngine.state == DiagState.POLLING
                AppConnectionMode.USB_HARDWARE, AppConnectionMode.SIMULATOR_DEMO ->
                    engine.state == DiagState.CONNECTED || engine.state == DiagState.POLLING
            }
            if (isConnected) {
                performDisconnect()
            } else {
                performConnect()
            }
        }

        // Pre-Flight Check button
        binding.btnCheckData.setOnClickListener {
            runPreFlightCheck()
        }

        // Log toggle button
        binding.btnToggleLog.setOnClickListener {
            if (!asyncLogger.isLogging) {
                startWotLog()
            } else {
                stopWotLog()
            }
        }

        // Collapsible RAW DEBUG toggle
        binding.layoutRawDebugHeader.setOnClickListener {
            isRawDebugExpanded = !isRawDebugExpanded
            binding.layoutRawDebugContent.visibility = if (isRawDebugExpanded) View.VISIBLE else View.GONE
            binding.tvRawDebugHeaderTitle.text = if (isRawDebugExpanded) "▼ RAW DEBUG (tap to collapse)" else "▶ RAW DEBUG (tap to toggle)"
        }

        // DTC Buttons for Mode B
        binding.btnScanDtc.setOnClickListener {
            lifecycleScope.launch {
                binding.tvDtcList.text = "Scanning DTCs..."
                val dtcs = engine.readFaultCodes()
                if (dtcs.isEmpty()) {
                    binding.tvDtcList.text = "No fault codes stored in EDC16."
                } else {
                    binding.tvDtcList.text = dtcs.joinToString("\n") { "${it.saeCode} (${it.vagCode}): ${it.descriptionEn}" }
                }
            }
        }

        binding.btnClearDtc.setOnClickListener {
            lifecycleScope.launch {
                val ok = engine.clearFaultCodes()
                if (ok) {
                    binding.tvDtcList.text = "Fault codes cleared successfully."
                } else {
                    binding.tvDtcList.text = "Failed to clear DTCs."
                }
            }
        }
    }

    private fun switchConnectionMode(newMode: AppConnectionMode) {
        if (connectionMode != newMode) {
            performDisconnect()
            connectionMode = newMode
        }

        when (newMode) {
            AppConnectionMode.TURBO_FAST_OBD -> {
                binding.btnModeToggle.text = "Mode: A (Turbo Fast)"
                binding.layoutTurboFast.visibility = View.VISIBLE
                binding.layoutOemGroups.visibility = View.GONE
            }
            AppConnectionMode.VAG_OEM_TP20 -> {
                binding.btnModeToggle.text = "Mode: B (VAG OEM)"
                binding.layoutTurboFast.visibility = View.GONE
                binding.layoutOemGroups.visibility = View.VISIBLE
            }
            AppConnectionMode.USB_HARDWARE -> {
                binding.btnModeToggle.text = "Mode: USB K-Line"
                binding.layoutTurboFast.visibility = View.GONE
                binding.layoutOemGroups.visibility = View.VISIBLE
                engine.setMode(TransportMode.USB_HARDWARE)
            }
            AppConnectionMode.SIMULATOR_DEMO -> {
                binding.btnModeToggle.text = "Mode: Simulator"
                binding.layoutTurboFast.visibility = View.GONE
                binding.layoutOemGroups.visibility = View.VISIBLE
                engine.setMode(TransportMode.SIMULATOR_DEMO)
            }
        }
        updateStatusUI()
    }

    private fun performConnect() {
        if (connectionMode == AppConnectionMode.TURBO_FAST_OBD || connectionMode == AppConnectionMode.VAG_OEM_TP20) {
            connectElmBluetooth()
        } else if (connectionMode == AppConnectionMode.SIMULATOR_DEMO) {
            lifecycleScope.launch {
                engine.connect(null)
                updateStatusUI()
                startOemPolling()
            }
        } else {
            val dev = currentDevice ?: transport.findAvailableDevice()
            if (dev == null) {
                Toast.makeText(this, "No USB FTDI / KKL cable detected.", Toast.LENGTH_LONG).show()
                updateStatusUI()
                return
            }
            if (!transport.hasPermission(dev)) {
                isPermissionRequested = true
                transport.requestPermission(dev)
                updateStatusUI()
                return
            }
            lifecycleScope.launch {
                val ok = engine.connect(dev)
                updateStatusUI()
                if (ok) startOemPolling()
            }
        }
    }

    private fun connectElmBluetooth() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.BLUETOOTH_CONNECT, Manifest.permission.BLUETOOTH_SCAN), 101)
                return
            }
        }
        val paired = elmEngine.transport.getBondedDevices()
        if (paired.isEmpty()) {
            Toast.makeText(this, "No paired Bluetooth devices found! Pair ELM327 in Android Settings.", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames: Array<CharSequence> = paired.map { device ->
            try {
                ("${device.name ?: "Unknown"} (${device.address})") as CharSequence
            } catch (_: SecurityException) {
                "Unknown Device" as CharSequence
            }
        }.toTypedArray()

        AlertDialog.Builder(this)
            .setTitle("Select ELM327 / V-LINK Adapter")
            .setItems(deviceNames) { _, idx ->
                val chosenDevice = paired[idx]
                startElmConnection(chosenDevice)
            }
            .show()
    }

    private fun startElmConnection(device: BluetoothDevice) {
        binding.tvStatus.text = "Connecting..."
        binding.tvSubStatus.text = "Opening RFCOMM to ${device.address}..."
        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
        binding.btnConnect.isEnabled = false

        lifecycleScope.launch {
            val success = elmEngine.connect(device)
            binding.btnConnect.isEnabled = true
            updateStatusUI()
            if (success) {
                if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) {
                    startTurboFastPolling()
                } else {
                    startOemPolling()
                }
            } else {
                val err = elmEngine.lastError ?: "Failed to connect to ELM327"
                Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performDisconnect() {
        stopPolling()
        if (asyncLogger.isLogging) {
            stopWotLog()
        }
        try {
            when (connectionMode) {
                AppConnectionMode.TURBO_FAST_OBD, AppConnectionMode.VAG_OEM_TP20 -> elmEngine.disconnect()
                AppConnectionMode.USB_HARDWARE, AppConnectionMode.SIMULATOR_DEMO -> engine.disconnect()
            }
        } catch (e: Exception) {
            Log.w("MainActivity", "Error during disconnect: ${e.message}")
        }
        updateStatusUI()
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
        preFlightJob?.cancel()
        preFlightJob = null
    }

    private fun startWotLog() {
        logStartUtcMs = System.currentTimeMillis()
        val (rawFile, pairFile) = asyncLogger.startLogging(lifecycleScope)
        binding.btnToggleLog.text = "STOP LOG"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F85149"))
        Toast.makeText(this, "Log Started: ${pairFile.name}", Toast.LENGTH_SHORT).show()
    }

    private fun stopWotLog() {
        val (rawFile, pairFile) = asyncLogger.stopLogging()
        binding.btnToggleLog.text = "START 4TH GEAR WOT LOG"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3FB950"))
        if (pairFile != null) {
            Toast.makeText(this, "Log Saved: ${pairFile.name} (${pairFile.length() / 1024} KB)", Toast.LENGTH_LONG).show()
        }
    }

    // =========================================================================
    // UNIFIED DATA WIRING: ELM RAW -> parser -> DiagnosticSample -> UI & CSV
    // =========================================================================

    private fun onDiagnosticSampleReceived(sample: DiagnosticSample) {
        latestSamples[sample.pid] = sample

        // 1. Log to Raw Event CSV immediately with exact same fields
        asyncLogger.logRawEvent(
            pid = sample.pid,
            value = sample.value,
            unit = sample.unit,
            raw = sample.rawResponse,
            latencyMs = sample.latencyMs,
            status = sample.status.name,
            rxNanos = sample.monoNanos
        )

        // 2. Direct UI widget update
        updateWidgetForSample(sample)

        // 3. RAW DEBUG collapsible update
        updateRawDebug(sample)
    }

    private fun updateWidgetForSample(sample: DiagnosticSample) {
        val now = SystemClock.elapsedRealtimeNanos()
        val effStatus = sample.getEffectiveStatus(now)
        val ageMs = sample.getAgeMs(now)
        val color = getStatusColor(effStatus)

        when (sample.pid) {
            "010C" -> {
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    binding.tvHeroRpm.text = String.format(Locale.US, "%.0f", sample.value)
                } else {
                    binding.tvHeroRpm.text = "---"
                }
                binding.tvRpmStatusAge.text = "${effStatus.name} | ${sample.latencyMs}ms | ${ageMs}ms"
                binding.tvRpmStatusAge.setTextColor(color)
            }
            "010B" -> {
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    binding.tvHeroMap.text = String.format(Locale.US, "%.0f mbar", sample.value)
                } else {
                    binding.tvHeroMap.text = "--- mbar"
                }
                binding.tvMapStatusAge.text = "${effStatus.name} | ${sample.latencyMs}ms | ${ageMs}ms"
                binding.tvMapStatusAge.setTextColor(color)

                // Update Hero Boost
                val baroSample = latestSamples["0133"]
                val baroVal = baroSample?.value ?: calibratedBaroMbar ?: 1000.0
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    val boostMbar = sample.value - baroVal
                    val boostBar = boostMbar / 1000.0
                    binding.tvHeroBoost.text = String.format(Locale.US, "%.2f bar", boostBar)
                    val baroLabel = if (baroSample?.status == PidStatus.VALID) "0133" else "CALIB"
                    binding.tvBoostStatusAge.text = "OK | $baroLabel | Rel"
                    binding.tvBoostStatusAge.setTextColor(Color.parseColor("#3FB950"))
                } else {
                    binding.tvHeroBoost.text = "--- bar"
                    binding.tvBoostStatusAge.text = "IDLE | Rel Gauge"
                    binding.tvBoostStatusAge.setTextColor(Color.parseColor("#8B949E"))
                }
            }
            "0110" -> {
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    binding.tvMafVal.text = String.format(Locale.US, "%.1f g/s", sample.value)
                } else {
                    binding.tvMafVal.text = "--- g/s"
                }
                binding.tvMafStatusAge.text = "${effStatus.name} · ${sample.latencyMs}ms"
                binding.tvMafStatusAge.setTextColor(color)
            }
            "010D" -> {
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    binding.tvSpeedVal.text = String.format(Locale.US, "%.0f km/h", sample.value)
                } else {
                    binding.tvSpeedVal.text = "--- km/h"
                }
                binding.tvSpeedStatusAge.text = "${effStatus.name} · ${sample.latencyMs}ms"
                binding.tvSpeedStatusAge.setTextColor(color)
            }
            "0104" -> {
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    binding.tvLoadVal.text = String.format(Locale.US, "%.1f %%", sample.value)
                } else {
                    binding.tvLoadVal.text = "--- %"
                }
                binding.tvLoadStatusAge.text = "${effStatus.name} · ${sample.latencyMs}ms"
                binding.tvLoadStatusAge.setTextColor(color)
            }
            "0133" -> {
                if (sample.status == PidStatus.VALID && sample.value != null) {
                    binding.tvBaroVal.text = String.format(Locale.US, "%.0f mbar", sample.value)
                } else {
                    binding.tvBaroVal.text = "--- mbar"
                }
                binding.tvBaroStatusAge.text = "${effStatus.name} · ${sample.latencyMs}ms"
                binding.tvBaroStatusAge.setTextColor(color)
            }
        }
    }

    private fun updateRawDebug(sample: DiagnosticSample) {
        binding.tvDebugLastTx.text = "TX: ${sample.pid}"
        binding.tvDebugLastRx.text = "RX: ${sample.rawResponse.trim()}"
        binding.tvDebugParsed.text = "Parsed: ${sample.name} = ${sample.formattedValue} ${sample.unit} [${sample.status}]"
        binding.tvDebugLatency.text = "Latency: ${sample.latencyMs} ms"
    }

    private fun getStatusColor(status: PidStatus): Int {
        return when (status) {
            PidStatus.VALID -> Color.parseColor("#3FB950")      // Green
            PidStatus.STALE -> Color.parseColor("#D29922")      // Amber
            PidStatus.TIMEOUT, PidStatus.ERROR, PidStatus.INVALID_FORMAT -> Color.parseColor("#F85149") // Red
            PidStatus.IDLE, PidStatus.NO_DATA -> Color.parseColor("#8B949E") // Muted Gray
        }
    }

    private fun startUiTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch {
            while (isActive) {
                refreshAgesAndStatuses()
                delay(100)
            }
        }
    }

    private fun refreshAgesAndStatuses() {
        val now = SystemClock.elapsedRealtimeNanos()

        latestSamples["010C"]?.let { s ->
            val eff = s.getEffectiveStatus(now)
            val age = s.getAgeMs(now)
            binding.tvRpmStatusAge.text = "${eff.name} | ${s.latencyMs}ms | ${age}ms"
            binding.tvRpmStatusAge.setTextColor(getStatusColor(eff))
        }
        latestSamples["010B"]?.let { s ->
            val eff = s.getEffectiveStatus(now)
            val age = s.getAgeMs(now)
            binding.tvMapStatusAge.text = "${eff.name} | ${s.latencyMs}ms | ${age}ms"
            binding.tvMapStatusAge.setTextColor(getStatusColor(eff))
        }
        latestSamples["0110"]?.let { s ->
            val eff = s.getEffectiveStatus(now)
            val age = s.getAgeMs(now)
            binding.tvMafStatusAge.text = "${eff.name} · ${age}ms"
            binding.tvMafStatusAge.setTextColor(getStatusColor(eff))
        }
        latestSamples["010D"]?.let { s ->
            val eff = s.getEffectiveStatus(now)
            val age = s.getAgeMs(now)
            binding.tvSpeedStatusAge.text = "${eff.name} · ${age}ms"
            binding.tvSpeedStatusAge.setTextColor(getStatusColor(eff))
        }
        latestSamples["0104"]?.let { s ->
            val eff = s.getEffectiveStatus(now)
            val age = s.getAgeMs(now)
            binding.tvLoadStatusAge.text = "${eff.name} · ${age}ms"
            binding.tvLoadStatusAge.setTextColor(getStatusColor(eff))
        }
        latestSamples["0133"]?.let { s ->
            val eff = s.getEffectiveStatus(now)
            val age = s.getAgeMs(now)
            binding.tvBaroStatusAge.text = "${eff.name} · ${age}ms"
            binding.tvBaroStatusAge.setTextColor(getStatusColor(eff))
        }

        // Bus Stats compact line
        binding.tvBusStats.text = String.format(
            Locale.US,
            "%.1f req/s | RPM: %.1f Hz | MAP: %.1f Hz | Lat: %d ms",
            busReqRate, busRpmHz, busMapHz, busAvgLatency
        )

        // Recording / Queue Stats compact line
        if (asyncLogger.isLogging) {
            val elapsedSec = (System.currentTimeMillis() - logStartUtcMs) / 1000
            val min = elapsedSec / 60
            val sec = elapsedSec % 60
            val sizeKb = asyncLogger.fileSizeBytes / 1024
            binding.tvLogMetrics.text = String.format(
                Locale.US,
                "REC ACTIVE (%02d:%02d) | Rows: %d | Queue: %d | Dropped: %d | %d KB",
                min, sec, asyncLogger.rowsWritten, asyncLogger.queueSize, asyncLogger.droppedRecords, sizeKb
            )
            binding.tvLogMetrics.setTextColor(Color.parseColor("#F85149"))
        } else {
            val sizeKb = asyncLogger.fileSizeBytes / 1024
            binding.tvLogMetrics.text = String.format(
                Locale.US,
                "REC OFF | Rows: %d | Queue: %d | Dropped: %d | %d KB",
                asyncLogger.rowsWritten, asyncLogger.queueSize, asyncLogger.droppedRecords, sizeKb
            )
            binding.tvLogMetrics.setTextColor(Color.parseColor("#8B949E"))
        }
    }

    // =========================================================================
    // PRE-FLIGHT SELF CHECK (1.5 - 2s Probe across all 9 PIDs)
    // =========================================================================

    private fun runPreFlightCheck() {
        if (elmEngine.state != DiagState.CONNECTED && elmEngine.state != DiagState.POLLING) {
            Toast.makeText(this, "Connect ELM327 Bluetooth first!", Toast.LENGTH_SHORT).show()
            return
        }

        preFlightJob?.cancel()
        preFlightJob = lifecycleScope.launch {
            binding.btnCheckData.isEnabled = false
            binding.tvPreFlightStatus.text = "Pre-flight: Running test sequence..."
            binding.tvPreFlightStatus.setTextColor(Color.parseColor("#D29922"))

            val pidsToTest = listOf("010C", "010B", "0110", "010D", "0104", "0105", "010F", "0142", "0133")
            var validCount = 0

            for (pid in pidsToTest) {
                binding.tvPreFlightStatus.text = "Pre-flight: Testing $pid..."
                val resp = elmEngine.transport.sendCommand(pid, 300L)
                val dec = when (pid) {
                    "010C" -> PidDecoder.decodeRpm(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "010B" -> PidDecoder.decodeMap(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "0110" -> PidDecoder.decodeMaf(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "010D" -> PidDecoder.decodeSpeed(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "0104" -> PidDecoder.decodeLoad(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "0105" -> PidDecoder.decodeCoolant(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "010F" -> PidDecoder.decodeIat(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "0142" -> PidDecoder.decodeVoltage(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    "0133" -> PidDecoder.decodeBaro(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                    else -> null
                }
                if (dec != null) {
                    val sample = DiagnosticSample(
                        pid = dec.pid,
                        name = pidName(dec.pid),
                        value = dec.value,
                        formattedValue = dec.formatted,
                        unit = dec.unit,
                        status = dec.status,
                        latencyMs = dec.latencyMs,
                        timestampUtcMs = System.currentTimeMillis(),
                        monoNanos = if (dec.rxNanos > 0L) dec.rxNanos else SystemClock.elapsedRealtimeNanos(),
                        rawResponse = dec.rawString
                    )
                    onDiagnosticSampleReceived(sample)
                    if (dec.status == PidStatus.VALID) validCount++
                }
                delay(40)
            }

            val rpmOk = latestSamples["010C"]?.status == PidStatus.VALID
            val mapOk = latestSamples["010B"]?.status == PidStatus.VALID

            if (rpmOk && mapOk) {
                if (validCount == pidsToTest.size) {
                    binding.tvPreFlightStatus.text = "🟢 PRE-FLIGHT OK: All 9 sensors responding (Ready to log)"
                    binding.tvPreFlightStatus.setTextColor(Color.parseColor("#3FB950"))
                } else {
                    binding.tvPreFlightStatus.text = "🟡 PRE-FLIGHT WARNING: Core OK, $validCount/9 sensors responding"
                    binding.tvPreFlightStatus.setTextColor(Color.parseColor("#D29922"))
                }
            } else {
                binding.tvPreFlightStatus.text = "🔴 PRE-FLIGHT FAILED: Core engine data (RPM/MAP) not responding"
                binding.tvPreFlightStatus.setTextColor(Color.parseColor("#F85149"))
            }

            binding.btnCheckData.isEnabled = true
        }
    }

    private fun pidName(pid: String): String {
        return when (pid) {
            "010C" -> "RPM"
            "010B" -> "MAP"
            "0110" -> "MAF"
            "010D" -> "Speed"
            "0104" -> "Load"
            "0105" -> "Coolant"
            "010F" -> "IAT"
            "0142" -> "Voltage"
            "0133" -> "BARO"
            else -> pid
        }
    }

    // =========================================================================
    // TURBO FAST POLLING LOOP (Weighted Round-Robin with Exact Parity)
    // =========================================================================

    private fun startTurboFastPolling() {
        stopPolling()
        pollingJob = lifecycleScope.launch(Dispatchers.IO) {
            var step = 0
            var auxStep = 0
            windowStart = SystemClock.elapsedRealtime()

            while (isActive && (elmEngine.state == DiagState.CONNECTED || elmEngine.state == DiagState.POLLING)) {
                when (step) {
                    0, 1, 3, 5, 7 -> {
                        // High-Priority RPM (010C) then MAP (010B) pair
                        val rpmResp = elmEngine.transport.sendCommand("010C", 200L)
                        windowTotalReqs++
                        windowRpmCount++
                        windowLatencySum += rpmResp.elapsedMs
                        val rpmDec = PidDecoder.decodeRpm(rpmResp.raw, rpmResp.txNanos, rpmResp.rxNanos, rpmResp.elapsedMs, rpmResp.timedOut)
                        val rpmSample = DiagnosticSample("010C", "RPM", rpmDec.value, rpmDec.formatted, "RPM", rpmDec.status, rpmResp.elapsedMs, System.currentTimeMillis(), if (rpmResp.rxNanos > 0L) rpmResp.rxNanos else SystemClock.elapsedRealtimeNanos(), rpmResp.raw)
                        withContext(Dispatchers.Main) {
                            onDiagnosticSampleReceived(rpmSample)
                        }

                        val mapResp = elmEngine.transport.sendCommand("010B", 200L)
                        windowTotalReqs++
                        windowMapCount++
                        windowLatencySum += mapResp.elapsedMs
                        val mapDec = PidDecoder.decodeMap(mapResp.raw, mapResp.txNanos, mapResp.rxNanos, mapResp.elapsedMs, mapResp.timedOut)
                        val mapSample = DiagnosticSample("010B", "MAP", mapDec.value, mapDec.formatted, "mbar", mapDec.status, mapResp.elapsedMs, System.currentTimeMillis(), if (mapResp.rxNanos > 0L) mapResp.rxNanos else SystemClock.elapsedRealtimeNanos(), mapResp.raw)
                        withContext(Dispatchers.Main) {
                            onDiagnosticSampleReceived(mapSample)

                            // Synchronized Turbo Pair CSV writing
                            if (rpmSample.status == PidStatus.VALID && mapSample.status == PidStatus.VALID) {
                                val dtMs = (mapSample.monoNanos - rpmSample.monoNanos) / 1_000_000
                                val pairValid = dtMs in 0..150
                                val baroSample = latestSamples["0133"]
                                val baroVal = baroSample?.value ?: calibratedBaroMbar ?: 1000.0
                                if (asyncLogger.isLogging) {
                                    asyncLogger.logTurboPair(
                                        rpm = rpmSample.value ?: 0.0,
                                        mapMbarAbs = mapSample.value ?: 0.0,
                                        baroMbar = baroVal,
                                        baroSource = if (baroSample?.status == PidStatus.VALID) "0133" else "CALIB",
                                        dtMapRpmMs = dtMs,
                                        pairValid = pairValid,
                                        invalidReason = if (pairValid) "" else "dt_jitter_${dtMs}ms",
                                        mafGs = latestSamples["0110"]?.value,
                                        speedKmh = latestSamples["010D"]?.value,
                                        loadPct = latestSamples["0104"]?.value,
                                        coolantC = latestSamples["0105"]?.value,
                                        iatC = latestSamples["010F"]?.value,
                                        voltageV = latestSamples["0142"]?.value,
                                        latencyMs = rpmSample.latencyMs + mapSample.latencyMs,
                                        monoNs = mapSample.monoNanos
                                    )
                                }
                            }
                        }
                    }
                    2 -> {
                        // MAF (0110)
                        val mafResp = elmEngine.transport.sendCommand("0110", 200L)
                        windowTotalReqs++
                        windowMafCount++
                        windowLatencySum += mafResp.elapsedMs
                        val mafDec = PidDecoder.decodeMaf(mafResp.raw, mafResp.txNanos, mafResp.rxNanos, mafResp.elapsedMs, mafResp.timedOut)
                        val mafSample = DiagnosticSample("0110", "MAF", mafDec.value, mafDec.formatted, "g/s", mafDec.status, mafResp.elapsedMs, System.currentTimeMillis(), if (mafResp.rxNanos > 0L) mafResp.rxNanos else SystemClock.elapsedRealtimeNanos(), mafResp.raw)
                        withContext(Dispatchers.Main) {
                            onDiagnosticSampleReceived(mafSample)
                        }
                    }
                    4 -> {
                        // Speed (010D)
                        val spdResp = elmEngine.transport.sendCommand("010D", 200L)
                        windowTotalReqs++
                        windowLatencySum += spdResp.elapsedMs
                        val spdDec = PidDecoder.decodeSpeed(spdResp.raw, spdResp.txNanos, spdResp.rxNanos, spdResp.elapsedMs, spdResp.timedOut)
                        val spdSample = DiagnosticSample("010D", "Speed", spdDec.value, spdDec.formatted, "km/h", spdDec.status, spdResp.elapsedMs, System.currentTimeMillis(), if (spdResp.rxNanos > 0L) spdResp.rxNanos else SystemClock.elapsedRealtimeNanos(), spdResp.raw)
                        withContext(Dispatchers.Main) {
                            onDiagnosticSampleReceived(spdSample)
                        }
                    }
                    6 -> {
                        // Load (0104)
                        val lodResp = elmEngine.transport.sendCommand("0104", 200L)
                        windowTotalReqs++
                        windowLatencySum += lodResp.elapsedMs
                        val lodDec = PidDecoder.decodeLoad(lodResp.raw, lodResp.txNanos, lodResp.rxNanos, lodResp.elapsedMs, lodResp.timedOut)
                        val lodSample = DiagnosticSample("0104", "Load", lodDec.value, lodDec.formatted, "%", lodDec.status, lodResp.elapsedMs, System.currentTimeMillis(), if (lodResp.rxNanos > 0L) lodResp.rxNanos else SystemClock.elapsedRealtimeNanos(), lodResp.raw)
                        withContext(Dispatchers.Main) {
                            onDiagnosticSampleReceived(lodSample)
                        }
                    }
                    8 -> {
                        // Aux Sensors
                        when (auxStep % 4) {
                            0 -> {
                                val clnResp = elmEngine.transport.sendCommand("0105", 200L)
                                windowTotalReqs++
                                windowLatencySum += clnResp.elapsedMs
                                val clnDec = PidDecoder.decodeCoolant(clnResp.raw, clnResp.txNanos, clnResp.rxNanos, clnResp.elapsedMs, clnResp.timedOut)
                                val clnSample = DiagnosticSample("0105", "Coolant", clnDec.value, clnDec.formatted, "°C", clnDec.status, clnResp.elapsedMs, System.currentTimeMillis(), if (clnResp.rxNanos > 0L) clnResp.rxNanos else SystemClock.elapsedRealtimeNanos(), clnResp.raw)
                                withContext(Dispatchers.Main) { onDiagnosticSampleReceived(clnSample) }
                            }
                            1 -> {
                                val iatResp = elmEngine.transport.sendCommand("010F", 200L)
                                windowTotalReqs++
                                windowLatencySum += iatResp.elapsedMs
                                val iatDec = PidDecoder.decodeIat(iatResp.raw, iatResp.txNanos, iatResp.rxNanos, iatResp.elapsedMs, iatResp.timedOut)
                                val iatSample = DiagnosticSample("010F", "IAT", iatDec.value, iatDec.formatted, "°C", iatDec.status, iatResp.elapsedMs, System.currentTimeMillis(), if (iatResp.rxNanos > 0L) iatResp.rxNanos else SystemClock.elapsedRealtimeNanos(), iatResp.raw)
                                withContext(Dispatchers.Main) { onDiagnosticSampleReceived(iatSample) }
                            }
                            2 -> {
                                var vResp = elmEngine.transport.sendCommand("0142", 200L)
                                windowTotalReqs++
                                windowLatencySum += vResp.elapsedMs
                                var vDec = PidDecoder.decodeVoltage(vResp.raw, vResp.txNanos, vResp.rxNanos, vResp.elapsedMs, vResp.timedOut)
                                if (vDec.status != PidStatus.VALID) {
                                    vResp = elmEngine.transport.sendCommand("ATRV", 200L)
                                    vDec = PidDecoder.decodeVoltage(vResp.raw, vResp.txNanos, vResp.rxNanos, vResp.elapsedMs, vResp.timedOut)
                                }
                                val vSample = DiagnosticSample("0142", "Voltage", vDec.value, vDec.formatted, "V", vDec.status, vResp.elapsedMs, System.currentTimeMillis(), if (vResp.rxNanos > 0L) vResp.rxNanos else SystemClock.elapsedRealtimeNanos(), vResp.raw)
                                withContext(Dispatchers.Main) { onDiagnosticSampleReceived(vSample) }
                            }
                            3 -> {
                                val baroResp = elmEngine.transport.sendCommand("0133", 200L)
                                windowTotalReqs++
                                windowLatencySum += baroResp.elapsedMs
                                val baroDec = PidDecoder.decodeBaro(baroResp.raw, baroResp.txNanos, baroResp.rxNanos, baroResp.elapsedMs, baroResp.timedOut)
                                val baroSample = DiagnosticSample("0133", "BARO", baroDec.value, baroDec.formatted, "mbar", baroDec.status, baroResp.elapsedMs, System.currentTimeMillis(), if (baroResp.rxNanos > 0L) baroResp.rxNanos else SystemClock.elapsedRealtimeNanos(), baroResp.raw)
                                if (baroDec.status == PidStatus.VALID && baroDec.value != null) {
                                    calibratedBaroMbar = baroDec.value
                                    calibratedBaroSource = "PID_0133"
                                }
                                withContext(Dispatchers.Main) { onDiagnosticSampleReceived(baroSample) }
                            }
                        }
                        auxStep++
                    }
                }

                step = (step + 1) % 9

                val now = SystemClock.elapsedRealtime()
                val dt = (now - windowStart) / 1000.0
                if (dt >= 0.5) {
                    busReqRate = windowTotalReqs / dt
                    busRpmHz = windowRpmCount / dt
                    busMapHz = windowMapCount / dt
                    busMafHz = windowMafCount / dt
                    busAvgLatency = if (windowTotalReqs > 0) windowLatencySum / windowTotalReqs else 0L
                    windowStart = now
                    windowTotalReqs = 0
                    windowRpmCount = 0
                    windowMapCount = 0
                    windowMafCount = 0
                    windowLatencySum = 0L
                }
            }
        }
    }

    // =========================================================================
    // MODE B OEM POLLING (Group 011 / 008 / 003)
    // =========================================================================

        private fun startOemPolling() {
        stopPolling()
        pollingJob = lifecycleScope.launch {
            var cycleCount = 0
            while (isActive) {
                val isConnected = when (connectionMode) {
                    AppConnectionMode.VAG_OEM_TP20 -> elmEngine.state == DiagState.CONNECTED || elmEngine.state == DiagState.POLLING
                    AppConnectionMode.USB_HARDWARE, AppConnectionMode.SIMULATOR_DEMO -> engine.state == DiagState.CONNECTED || engine.state == DiagState.POLLING
                    else -> false
                }
                if (!isConnected) break

                val g011 = if (connectionMode == AppConnectionMode.VAG_OEM_TP20) {
                    elmEngine.readMeasuringGroup(11)
                } else {
                    engine.readMeasuringGroup(11)
                }

                if (g011 != null && g011.values.size >= 4) {
                    val rpmVal = g011.values[0].rawValue
                    val targetBoost = g011.values[1].rawValue
                    val actualBoost = g011.values[2].rawValue
                    val n75 = g011.values[3].rawValue

                    binding.tvRpm.text = String.format(Locale.US, "%.0f RPM", rpmVal)
                    binding.tvBoostSpecified.text = String.format(Locale.US, "%.0f", targetBoost)
                    binding.tvBoostActual.text = String.format(Locale.US, "%.0f", actualBoost)
                    binding.tvN75.text = String.format(Locale.US, "%.1f %%", n75)

                    binding.liveGraphView.addTelemetryPoint(
                        targetBoost.toFloat(),
                        actualBoost.toFloat(),
                        n75.toFloat()
                    )
                }

                if (cycleCount % 2 == 0) {
                    val g008 = if (connectionMode == AppConnectionMode.VAG_OEM_TP20) {
                        elmEngine.readMeasuringGroup(8)
                    } else {
                        engine.readMeasuringGroup(8)
                    }
                    if (g008 != null && g008.values.size >= 4) {
                        val lastDriverWish = g008.values[1].rawValue
                        val lastTorqueLim = g008.values[2].rawValue
                        val lastSmokeLim = g008.values[3].rawValue

                        binding.tvDriverWish.text = String.format(Locale.US, "Driver: %.1f mg", lastDriverWish)
                        binding.tvTorqueLimit.text = String.format(Locale.US, "Torque: %.1f mg", lastTorqueLim)
                        binding.tvSmokeLimit.text = String.format(Locale.US, "Smoke: %.1f mg", lastSmokeLim)
                    }
                } else {
                    val g003 = if (connectionMode == AppConnectionMode.VAG_OEM_TP20) {
                        elmEngine.readMeasuringGroup(3)
                    } else {
                        engine.readMeasuringGroup(3)
                    }
                    if (g003 != null && g003.values.size >= 4) {
                        val mafReq = g003.values[1].rawValue
                        val lastMafAct = g003.values[2].rawValue
                        val egr = g003.values[3].rawValue

                        binding.tvMafSpecified.text = String.format(Locale.US, "Target: %.0f mg/s", mafReq)
                        binding.tvMafActual.text = String.format(Locale.US, "Actual: %.0f mg/s", lastMafAct)
                        binding.tvEgrDuty.text = String.format(Locale.US, "EGR: %.1f %%", egr)
                    }
                }
                cycleCount++
                delay(30)
            }
        }
    }

private fun updateStatusUI() {
        when (connectionMode) {
            AppConnectionMode.TURBO_FAST_OBD, AppConnectionMode.VAG_OEM_TP20 -> {
                when (elmEngine.state) {
                    DiagState.CONNECTED, DiagState.POLLING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_green)
                        val protoMode = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) "Turbo Fast (OBD-II)" else "VW TP 2.0 (OEM)"
                        binding.tvStatus.text = "Connected: $protoMode"
                        binding.tvSubStatus.text = "ECU Online | ${elmEngine.transport.connectedDeviceName ?: "V-LINK"}"
                        binding.btnConnect.text = "Disconnect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DA3633"))
                        binding.btnConnect.setTextColor(Color.WHITE)
                    }
                    DiagState.CONNECTING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        binding.tvStatus.text = "Connecting..."
                        binding.tvSubStatus.text = "Negotiating ELM327 Bluetooth protocol..."
                    }
                    DiagState.ERROR -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_red)
                        binding.tvStatus.text = "Connection Error"
                        binding.tvSubStatus.text = elmEngine.lastError ?: "ELM327 timeout"
                        binding.btnConnect.text = "Retry"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                        binding.btnConnect.setTextColor(Color.WHITE)
                    }
                    DiagState.DISCONNECTED -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        val title = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) "Ready: Mode A (Turbo Fast)" else "Ready: Mode B (VAG OEM)"
                        binding.tvStatus.text = title
                        binding.tvSubStatus.text = "Ignition ON -> Tap Connect"
                        binding.btnConnect.text = "Connect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                        binding.btnConnect.setTextColor(Color.WHITE)
                    }
                }
            }
            AppConnectionMode.USB_HARDWARE -> {
                when (engine.state) {
                    DiagState.CONNECTED, DiagState.POLLING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_green)
                        val info = transport.getActiveAdapterInfo()
                        binding.tvStatus.text = "Connected (USB)"
                        binding.tvSubStatus.text = "ECU Online | ${info?.displayName ?: "USB Adapter"}"
                        binding.btnConnect.text = "Disconnect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#30363D"))
                    }
                    DiagState.CONNECTING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        binding.tvStatus.text = "Connecting..."
                        binding.tvSubStatus.text = "Negotiating USB protocol"
                    }
                    DiagState.ERROR -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_red)
                        binding.tvStatus.text = "Error Connecting"
                        binding.tvSubStatus.text = engine.lastError ?: "USB timeout"
                        binding.btnConnect.text = "Retry"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                    }
                    DiagState.DISCONNECTED -> {
                        val dev = currentDevice ?: transport.findAvailableDevice()
                        if (dev == null) {
                            binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_red)
                            binding.tvStatus.text = "No USB Adapter"
                            binding.tvSubStatus.text = "Plug in USB cable or switch to Mode A/B (BT)"
                            binding.btnConnect.text = "Connect"
                            binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                        } else {
                            val info = UsbKwpTransport.identifyDevice(dev)
                            binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                            binding.tvStatus.text = "Ready: ${info.displayName}"
                            binding.tvSubStatus.text = "Ignition ON -> Tap Connect"
                            binding.btnConnect.text = "Connect"
                            binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                        }
                    }
                }
            }
            AppConnectionMode.SIMULATOR_DEMO -> {
                when (engine.state) {
                    DiagState.CONNECTED, DiagState.POLLING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_green)
                        binding.tvStatus.text = "Simulated EDC16 (Demo Mode)"
                        binding.tvSubStatus.text = "Virtual Golf 5 1.9 TDI BLS active"
                        binding.btnConnect.text = "Disconnect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#30363D"))
                    }
                    else -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        binding.tvStatus.text = "Simulator Ready"
                        binding.tvSubStatus.text = "Tap Connect to start simulated telemetry"
                        binding.btnConnect.text = "Connect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                    }
                }
            }
        }
    }
}
