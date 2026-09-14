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
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.vag.vcdsandroid.R
import com.vag.vcdsandroid.databinding.ActivityMainBinding
import com.vag.vcdsandroid.logging.AsyncCsvLogger
import com.vag.vcdsandroid.protocol.DecodedPid
import com.vag.vcdsandroid.bluetooth.ElmResponse
import com.vag.vcdsandroid.protocol.DiagState
import com.vag.vcdsandroid.protocol.Elm327DiagnosticEngine
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

enum class AppConnectionMode {
    TURBO_FAST_OBD,   // Mode A: High-speed OBD-II RPM+MAP pairs (ELM327 Bluetooth)
    VAG_OEM_TP20,     // Mode B: VAG OEM Group 011/008/003 (TP 2.0 CAN)
    USB_HARDWARE,     // USB-KKL / Ross-Tech cable
    SIMULATOR_DEMO    // Virtual EDC16 Demo
}

data class SignalSample(
    val pid: String,
    val name: String,
    val value: Double?,
    val formatted: String,
    val unit: String,
    val status: String, // "OK", "TIMEOUT", "NO DATA", "INVALID", "STALE", "IDLE"
    val latencyMs: Long,
    val rxNanos: Long,
    val staleThresholdNs: Long
) {
    fun getEffectiveStatus(nowNs: Long): String {
        if (status == "IDLE" || status == "TIMEOUT" || status == "NO DATA" || status == "INVALID") return status
        if (rxNanos <= 0L || (nowNs - rxNanos) > staleThresholdNs) return "STALE"
        return status
    }

    fun getAgeString(nowNs: Long): String {
        if (rxNanos <= 0L) return "--- ago"
        val diffMs = (nowNs - rxNanos) / 1_000_000L
        return if (diffMs < 1000L) {
            "${diffMs}ms ago"
        } else {
            String.format(Locale.US, "%.1fs ago", diffMs / 1000.0)
        }
    }
}

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var transport: UsbKwpTransport
    private lateinit var engine: Kwp2000DiagnosticEngine
    private lateinit var elmEngine: Elm327DiagnosticEngine
    private lateinit var asyncLogger: AsyncCsvLogger

    private var connectionMode: AppConnectionMode = AppConnectionMode.TURBO_FAST_OBD
    private var pollingJob: Job? = null
    private var tickerJob: Job? = null
    private var isDiagnosticTestRunning: Boolean = false

    // Atmospheric pressure calibration
    private var calibratedBaroMbar: Double? = null
    private var calibratedBaroSource: String = "UNCALIBRATED"

    // Live Telemetry Signal Samples (Mode A Cockpit)
    private var sampleRpm = SignalSample("010C", "RPM", null, "---", "RPM", "IDLE", 0L, 0L, 800_000_000L)
    private var sampleMap = SignalSample("010B", "MAP", null, "---", "mbar", "IDLE", 0L, 0L, 800_000_000L)
    private var sampleMaf = SignalSample("0110", "MAF", null, "---", "g/s", "IDLE", 0L, 0L, 2_000_000_000L)
    private var sampleSpeed = SignalSample("010D", "SPEED", null, "---", "km/h", "IDLE", 0L, 0L, 2_000_000_000L)
    private var sampleLoad = SignalSample("0104", "LOAD", null, "---", "%", "IDLE", 0L, 0L, 2_000_000_000L)
    private var sampleBaro = SignalSample("0133", "BARO", null, "---", "mbar", "IDLE", 0L, 0L, 10_000_000_000L)
    private var sampleCoolant = SignalSample("0105", "COOLANT", null, "---", "°C", "IDLE", 0L, 0L, 10_000_000_000L)
    private var sampleIat = SignalSample("010F", "IAT", null, "---", "°C", "IDLE", 0L, 0L, 10_000_000_000L)
    private var sampleVoltage = SignalSample("0142", "VOLTAGE", null, "---", "V", "IDLE", 0L, 0L, 10_000_000_000L)

    // RAW Debug state
    private var debugLastTx: String = "---"
    private var debugLastRx: String = "---"
    private var debugLastParsed: String = "---"
    private var debugLastLatency: Long = 0L
    private var isRawDebugExpanded: Boolean = false

    // Bus statistics state
    private var busReqRate: Double = 0.0
    private var busRpmHz: Double = 0.0
    private var busMapHz: Double = 0.0
    private var busMafHz: Double = 0.0
    private var busAvgLatency: Long = 0L
    private var logStartUtcMs: Long = 0L

    // Cache latest live telemetry for multi-group logging (VAG OEM Mode B)
    private var lastRpm: Double = 0.0
    private var lastBoostReq: Double = 0.0
    private var lastBoostAct: Double = 0.0
    private var lastN75: Double = 0.0
    private var lastDriverWish: Double = 0.0
    private var lastTorqueLim: Double = 0.0
    private var lastSmokeLim: Double = 0.0
    private var lastMafAct: Double = 0.0

    private var currentDevice: UsbDevice? = null
    private var isPermissionRequested: Boolean = false
    private var selectedBluetoothDevice: BluetoothDevice? = null

    private val usbPermissionReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbKwpTransport.ACTION_USB_PERMISSION) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                isPermissionRequested = false
                if (granted && device != null) {
                    currentDevice = device
                    Toast.makeText(this@MainActivity, "USB Permission Granted: ${device.deviceName}", Toast.LENGTH_SHORT).show()
                    updateStatusUI()
                    performConnect()
                } else {
                    Toast.makeText(this@MainActivity, "USB Permission Denied", Toast.LENGTH_SHORT).show()
                    updateStatusUI()
                }
            }
        }
    }

    private val usbDetachedReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                } else {
                    @Suppress("DEPRECATION")
                    intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                }
                if (device != null && device.deviceId == currentDevice?.deviceId) {
                    currentDevice = null
                    isPermissionRequested = false
                    Toast.makeText(this@MainActivity, "USB Cable Detached", Toast.LENGTH_SHORT).show()
                    if (connectionMode == AppConnectionMode.USB_HARDWARE) {
                        performDisconnect()
                    }
                }
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

        requestNeededPermissions()

        // Default to Mode A: Turbo Fast OBD-II via Bluetooth ELM327
        connectionMode = AppConnectionMode.TURBO_FAST_OBD
        elmEngine.forceGenericObd = true

        updateModeToggleButton()
        setupListeners()
        applyModeUi()
        handleUsbIntent(intent)
        updateStatusUI()
        startTicker()

        val permFilter = IntentFilter(UsbKwpTransport.ACTION_USB_PERMISSION)
        val detachFilter = IntentFilter(UsbManager.ACTION_USB_DEVICE_DETACHED)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbPermissionReceiver, permFilter, RECEIVER_NOT_EXPORTED)
            registerReceiver(usbDetachedReceiver, detachFilter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbPermissionReceiver, permFilter)
            registerReceiver(usbDetachedReceiver, detachFilter)
        }
    }

    private fun requestNeededPermissions() {
        val perms = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                perms.add(Manifest.permission.ACCESS_FINE_LOCATION)
            }
        }
        if (perms.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, perms.toTypedArray(), 101)
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleUsbIntent(intent)
    }

    override fun onResume() {
        super.onResume()
        if (connectionMode == AppConnectionMode.USB_HARDWARE && engine.state == DiagState.DISCONNECTED) {
            checkAttachedDevice()
        }
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

    private fun handleUsbIntent(intent: Intent?) {
        if (intent == null) return
        if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
            val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
            } else {
                @Suppress("DEPRECATION")
                intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
            }
            if (device != null) {
                currentDevice = device
                connectionMode = AppConnectionMode.USB_HARDWARE
                updateModeToggleButton()
                applyModeUi()
                val info = UsbKwpTransport.identifyDevice(device)
                Toast.makeText(this, "USB Attached: ${info.displayName}", Toast.LENGTH_SHORT).show()

                if (transport.hasPermission(device)) {
                    performConnect()
                } else {
                    transport.requestPermission(device)
                }
            }
        }
    }

    private fun checkAttachedDevice() {
        val device = transport.findAvailableDevice()
        if (device != null) {
            currentDevice = device
            if (!transport.hasPermission(device) && !isPermissionRequested) {
                isPermissionRequested = true
                transport.requestPermission(device)
            }
        }
        updateStatusUI()
    }

    private fun updateModeToggleButton() {
        binding.btnModeToggle.text = when (connectionMode) {
            AppConnectionMode.TURBO_FAST_OBD -> "Mode A: Turbo Fast"
            AppConnectionMode.VAG_OEM_TP20 -> "Mode B: VAG OEM (TP 2.0)"
            AppConnectionMode.USB_HARDWARE -> "Mode: USB Cable"
            AppConnectionMode.SIMULATOR_DEMO -> "Mode: Demo (Sim)"
        }
    }

    private fun applyModeUi() {
        when (connectionMode) {
            AppConnectionMode.TURBO_FAST_OBD -> {
                binding.layoutTurboFast.visibility = View.VISIBLE
                binding.layoutOemGroups.visibility = View.GONE
            }
            AppConnectionMode.VAG_OEM_TP20, AppConnectionMode.USB_HARDWARE, AppConnectionMode.SIMULATOR_DEMO -> {
                binding.layoutTurboFast.visibility = View.GONE
                binding.layoutOemGroups.visibility = View.VISIBLE
                binding.tvTp20Badge.visibility = if (connectionMode == AppConnectionMode.VAG_OEM_TP20) View.VISIBLE else View.GONE
                binding.liveGraphView.visibility = View.GONE
            }
        }
        binding.btnToggleLog.text = if (asyncLogger.isLogging) "STOP LOG" else "START LOG"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(
            Color.parseColor(if (asyncLogger.isLogging) "#F85149" else "#3FB950")
        )
    }

    private fun setupListeners() {
        // Mode toggle: cycles through TURBO_FAST_OBD -> VAG_OEM_TP20 -> USB_HARDWARE -> SIMULATOR_DEMO
        binding.btnModeToggle.setOnClickListener {
            performDisconnect()
            connectionMode = when (connectionMode) {
                AppConnectionMode.TURBO_FAST_OBD -> AppConnectionMode.VAG_OEM_TP20
                AppConnectionMode.VAG_OEM_TP20 -> AppConnectionMode.USB_HARDWARE
                AppConnectionMode.USB_HARDWARE -> AppConnectionMode.SIMULATOR_DEMO
                AppConnectionMode.SIMULATOR_DEMO -> AppConnectionMode.TURBO_FAST_OBD
            }
            updateModeToggleButton()
            applyModeUi()
            if (connectionMode == AppConnectionMode.SIMULATOR_DEMO) {
                engine.setMode(TransportMode.SIMULATOR_DEMO)
            } else {
                engine.setMode(TransportMode.USB_HARDWARE)
            }
            updateStatusUI()
        }

        // Long click mode button: fast toggle between Mode A and Mode B
        binding.btnModeToggle.setOnLongClickListener {
            performDisconnect()
            connectionMode = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) {
                AppConnectionMode.VAG_OEM_TP20
            } else {
                AppConnectionMode.TURBO_FAST_OBD
            }
            updateModeToggleButton()
            applyModeUi()
            updateStatusUI()
            val label = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) "Mode A: Turbo Fast" else "Mode B: VAG OEM"
            Toast.makeText(this, "Switched to $label", Toast.LENGTH_SHORT).show()
            true
        }

        // Connect button
        binding.btnConnect.setOnClickListener {
            val isConn = isConnected()
            if (isConn) {
                performDisconnect()
            } else {
                checkAndConnect()
            }
        }

        // Pre-Flight self-check
        binding.btnCheckData.setOnClickListener {
            runPreFlightCheck()
        }

        // Collapsible RAW DEBUG toggle
        binding.layoutRawDebugHeader.setOnClickListener {
            toggleRawDebug()
        }

        // WOT Log toggle
        binding.btnToggleLog.setOnClickListener {
            if (!asyncLogger.isLogging) {
                startWotLog()
            } else {
                stopWotLog()
            }
        }

        // 10s RPM Stress Test (Mode B / OEM)
        binding.btnRpmTest.setOnClickListener {
            runRpmStressTest()
        }

        // 20s Timing Benchmark (Mode B / OEM)
        binding.btnBenchmark.setOnClickListener {
            runTimingBenchmark()
        }

        // BARO Calibration (Mode B / OEM)
        binding.btnCalibrateBaro.setOnClickListener {
            calibrateBaroExplicit()
        }

        // Scan DTC (Mode B / OEM)
        binding.btnScanDtc.setOnClickListener {
            scanDtc()
        }

        // Clear DTC (Mode B / OEM)
        binding.btnClearDtc.setOnClickListener {
            confirmClearDtc()
        }

        // Tap sub-status or long-click Connect to choose Bluetooth device
        binding.tvSubStatus.setOnClickListener {
            if ((connectionMode == AppConnectionMode.TURBO_FAST_OBD || connectionMode == AppConnectionMode.VAG_OEM_TP20) && !isConnected()) {
                showBluetoothDevicePicker()
            }
        }
        binding.btnConnect.setOnLongClickListener {
            if ((connectionMode == AppConnectionMode.TURBO_FAST_OBD || connectionMode == AppConnectionMode.VAG_OEM_TP20) && !isConnected()) {
                showBluetoothDevicePicker()
                true
            } else {
                false
            }
        }
    }

    private fun toggleRawDebug() {
        isRawDebugExpanded = !isRawDebugExpanded
        binding.layoutRawDebugContent.visibility = if (isRawDebugExpanded) View.VISIBLE else View.GONE
        binding.tvRawDebugHeaderTitle.text = if (isRawDebugExpanded) {
            "▼ RAW TELEMETRY DEBUG (EXPANDED)"
        } else {
            "▶ RAW TELEMETRY DEBUG (COLLAPSED)"
        }
    }

    private fun isConnected(): Boolean {
        return when (connectionMode) {
            AppConnectionMode.TURBO_FAST_OBD, AppConnectionMode.VAG_OEM_TP20 -> {
                elmEngine.state == DiagState.CONNECTED || elmEngine.state == DiagState.POLLING
            }
            AppConnectionMode.USB_HARDWARE, AppConnectionMode.SIMULATOR_DEMO -> {
                engine.state == DiagState.CONNECTED || engine.state == DiagState.POLLING
            }
        }
    }

    private fun checkAndConnect() {
        when (connectionMode) {
            AppConnectionMode.TURBO_FAST_OBD -> {
                elmEngine.forceGenericObd = true
                performConnectElm()
            }
            AppConnectionMode.VAG_OEM_TP20 -> {
                elmEngine.forceGenericObd = false
                performConnectElm()
            }
            AppConnectionMode.USB_HARDWARE -> {
                val device = currentDevice ?: transport.findAvailableDevice()
                if (device == null) {
                    Toast.makeText(this, "No USB cable detected. Switch to ELM327 (Mode A/B) or plug in cable.", Toast.LENGTH_LONG).show()
                    updateStatusUI()
                    return
                }
                currentDevice = device
                if (!transport.hasPermission(device)) {
                    isPermissionRequested = true
                    transport.requestPermission(device)
                    Toast.makeText(this, "Requesting USB permission...", Toast.LENGTH_SHORT).show()
                    updateStatusUI()
                    return
                }
                performConnect()
            }
            AppConnectionMode.SIMULATOR_DEMO -> {
                performConnect()
            }
        }
    }

    private fun showBluetoothDevicePicker() {
        val bonded = elmEngine.transport.getBondedDevices()
        if (bonded.isEmpty()) {
            Toast.makeText(this, "Не знайдено спарених Bluetooth пристроїв. Спаруйте адаптер у налаштуваннях Android.", Toast.LENGTH_LONG).show()
            return
        }

        val deviceNames = bonded.map { "${it.name ?: "Unknown"} [${it.address}]" }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Оберіть Bluetooth адаптер")
            .setItems(deviceNames) { _, which ->
                val chosen = bonded[which]
                selectedBluetoothDevice = chosen
                binding.tvSubStatus.text = "Вибрано: ${chosen.name} [${chosen.address}] (Натисніть Connect)"
                Toast.makeText(this, "Вибрано: ${chosen.name}", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Скасувати", null)
            .show()
    }

    private fun performConnectElm() {
        val modeTitle = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) "Turbo Fast (OBD-II)" else "VAG OEM (TP 2.0)"
        binding.tvStatus.text = "Connecting to $modeTitle..."
        binding.tvSubStatus.text = "Starting RFCOMM connection..."
        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
        binding.btnConnect.isEnabled = false

        binding.tvDtcList.text = "=== ELM327 $modeTitle LOG ==="
        elmEngine.onLogListener = { msg ->
            runOnUiThread {
                if (!isConnected()) {
                    binding.tvSubStatus.text = msg
                }
                val currentText = binding.tvDtcList.text.toString()
                if (currentText.length > 2500) {
                    binding.tvDtcList.text = currentText.takeLast(1200) + "\n" + msg
                } else {
                    binding.tvDtcList.text = currentText + "\n" + msg
                }
            }
        }

        lifecycleScope.launch {
            val success = elmEngine.connect(selectedBluetoothDevice)
            binding.btnConnect.isEnabled = true
            updateStatusUI()
            if (success) {
                Toast.makeText(this@MainActivity, "$modeTitle Connected!", Toast.LENGTH_SHORT).show()
                startPolling()
            } else {
                val err = elmEngine.lastError ?: "ELM327 connection failed"
                Toast.makeText(this@MainActivity, err, Toast.LENGTH_LONG).show()
            }
        }
    }

    private fun performConnect() {
        binding.tvStatus.text = "Connecting to EDC16..."
        binding.tvSubStatus.text = "Negotiating protocol..."
        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
        binding.btnConnect.isEnabled = false

        lifecycleScope.launch {
            val success = engine.connect(currentDevice)
            binding.btnConnect.isEnabled = true
            if (success) {
                updateStatusUI()
                startPolling()
            } else {
                updateStatusUI()
                val err = engine.lastError ?: "Connection failed"
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
            Log.w("MainActivity", "Error during disconnect: " + e.message)
        }
        updateStatusUI()
    }

    private fun startWotLog() {
        logStartUtcMs = System.currentTimeMillis()
        val (rawFile, pairFile) = asyncLogger.startLogging(lifecycleScope)
        binding.btnToggleLog.text = "STOP LOG"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F85149"))
        binding.tvLogMetrics.text = "Log: Active -> ${pairFile.name}"
        Toast.makeText(this, "Log Started: ${pairFile.name}", Toast.LENGTH_SHORT).show()
    }

    private fun stopWotLog() {
        val (rawFile, pairFile) = asyncLogger.stopLogging()
        binding.btnToggleLog.text = "START LOG"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3FB950"))
        if (pairFile != null) {
            val msg = "Saved: ${pairFile.name} & ${rawFile?.name}"
            binding.tvLogMetrics.text = msg
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun startTicker() {
        tickerJob?.cancel()
        tickerJob = lifecycleScope.launch(Dispatchers.Main) {
            while (isActive) {
                updateLiveCockpitUi()
                delay(100L)
            }
        }
    }

    private fun applyStatusPill(tv: TextView, status: String) {
        tv.text = status
        when (status) {
            "OK" -> {
                tv.setBackgroundResource(R.drawable.bg_status_pill_ok)
                tv.setTextColor(Color.parseColor("#3FB950"))
            }
            "STALE" -> {
                tv.setBackgroundResource(R.drawable.bg_status_pill_stale)
                tv.setTextColor(Color.parseColor("#F0883E"))
            }
            "TIMEOUT", "ERROR", "INVALID" -> {
                tv.setBackgroundResource(R.drawable.bg_status_pill_err)
                tv.setTextColor(Color.parseColor("#F85149"))
            }
            "NO DATA" -> {
                tv.setBackgroundResource(R.drawable.bg_status_pill_stale)
                tv.setTextColor(Color.parseColor("#F0883E"))
            }
            else -> { // IDLE
                tv.setBackgroundResource(R.drawable.bg_status_pill_idle)
                tv.setTextColor(Color.parseColor("#8B949E"))
            }
        }
    }

    private fun updateLiveCockpitUi() {
        val nowNs = SystemClock.elapsedRealtimeNanos()

        // 1. RPM
        binding.tvFastRpm.text = sampleRpm.formatted
        val rpmStat = sampleRpm.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastRpmStatus, rpmStat)
        binding.tvFastRpmAge.text = sampleRpm.getAgeString(nowNs)
        binding.tvFastRpmLatency.text = "${sampleRpm.latencyMs}ms"

        // 2. MAP
        binding.tvFastMap.text = sampleMap.formatted
        val mapStat = sampleMap.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastMapStatus, mapStat)
        binding.tvFastMapAge.text = sampleMap.getAgeString(nowNs)
        binding.tvFastMapLatency.text = "${sampleMap.latencyMs}ms"

        // 3. Boost Gauge (MAP - BARO)
        val curMapVal = sampleMap.value
        val curBaroVal = calibratedBaroMbar
        if (curMapVal != null && curBaroVal != null && curBaroVal > 0.0) {
            val boostMbar = curMapVal - curBaroVal
            val boostBar = boostMbar / 1000.0
            val sign = if (boostBar >= 0.0) "+" else ""
            binding.tvFastBoost.text = String.format(Locale.US, "%s%.2f", sign, boostBar)
            binding.tvFastBoostMbar.text = String.format(Locale.US, "bar (%s%.0f mbar)", sign, boostMbar)
            applyStatusPill(binding.tvFastBoostStatus, if (mapStat == "OK") "OK" else mapStat)
            binding.tvFastBoostSource.text = calibratedBaroSource
        } else {
            binding.tvFastBoost.text = "---"
            binding.tvFastBoostMbar.text = "bar (--- mbar)"
            applyStatusPill(binding.tvFastBoostStatus, "IDLE")
            binding.tvFastBoostSource.text = calibratedBaroSource
        }

        // 4. MAF
        binding.tvFastMaf.text = sampleMaf.formatted
        val mafStat = sampleMaf.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastMafStatus, mafStat)
        binding.tvFastMafAge.text = sampleMaf.getAgeString(nowNs)
        binding.tvFastMafLatency.text = "${sampleMaf.latencyMs}ms"

        // 5. Speed
        binding.tvFastSpeed.text = sampleSpeed.formatted
        val spdStat = sampleSpeed.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastSpeedStatus, spdStat)
        binding.tvFastSpeedAge.text = sampleSpeed.getAgeString(nowNs)
        binding.tvFastSpeedLatency.text = "${sampleSpeed.latencyMs}ms"

        // 6. Load
        binding.tvFastLoad.text = sampleLoad.formatted
        val lodStat = sampleLoad.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastLoadStatus, lodStat)
        binding.tvFastLoadAge.text = sampleLoad.getAgeString(nowNs)
        binding.tvFastLoadLatency.text = "${sampleLoad.latencyMs}ms"

        // 7. BARO
        binding.tvFastBaro.text = if (calibratedBaroMbar != null) String.format(Locale.US, "%.0f", calibratedBaroMbar) else "---"
        binding.tvFastBaroSource.text = calibratedBaroSource
        applyStatusPill(binding.tvFastBaroStatus, if (calibratedBaroMbar != null) "OK" else "IDLE")

        // 8. Coolant
        binding.tvFastCoolant.text = sampleCoolant.formatted
        val clnStat = sampleCoolant.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastCoolantStatus, clnStat)
        binding.tvFastCoolantAge.text = sampleCoolant.getAgeString(nowNs)

        // 9. IAT
        binding.tvFastIat.text = sampleIat.formatted
        val iatStat = sampleIat.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastIatStatus, iatStat)
        binding.tvFastIatAge.text = sampleIat.getAgeString(nowNs)

        // 10. Voltage
        binding.tvFastVoltage.text = sampleVoltage.formatted
        val vStat = sampleVoltage.getEffectiveStatus(nowNs)
        applyStatusPill(binding.tvFastVoltageStatus, vStat)
        binding.tvFastVoltageAge.text = sampleVoltage.getAgeString(nowNs)

        // Bus & Logger live statistics
        binding.tvElmBusState.text = when (elmEngine.state) {
            DiagState.POLLING -> "ELM: POLLING"
            DiagState.CONNECTED -> "ELM: CONNECTED"
            DiagState.ERROR -> "ELM: ERROR"
            else -> "ELM: IDLE"
        }
        if (asyncLogger.isLogging) {
            val durSec = (System.currentTimeMillis() - logStartUtcMs) / 1000L
            val mm = durSec / 60L
            val ss = durSec % 60L
            binding.tvLogState.text = "LOG: ON"
            binding.tvLogState.setTextColor(Color.parseColor("#3FB950"))
            binding.tvLogDuration.text = String.format(Locale.US, "%02d:%02d", mm, ss)
        } else {
            binding.tvLogState.text = "LOG: OFF"
            binding.tvLogState.setTextColor(Color.parseColor("#8B949E"))
            binding.tvLogDuration.text = "00:00"
        }
        binding.tvBusReqRate.text = String.format(Locale.US, "%.1f req/s", busReqRate)
        binding.tvBusRpmHz.text = String.format(Locale.US, "RPM: %.1fHz", busRpmHz)
        binding.tvBusMapHz.text = String.format(Locale.US, "MAP: %.1fHz", busMapHz)
        binding.tvBusMafHz.text = String.format(Locale.US, "MAF: %.1fHz", busMafHz)
        binding.tvBusAvgLatency.text = "Lat: ${busAvgLatency}ms"
        binding.tvLogRows.text = "Rows: ${asyncLogger.rowsWritten}"
        val q = asyncLogger.queueSize
        binding.tvLogQueue.text = "Queue: $q"
        binding.tvLogQueue.setTextColor(if (q > 30) Color.parseColor("#F85149") else Color.parseColor("#8B949E"))
        val drop = asyncLogger.droppedRecords
        binding.tvLogDropped.text = "Drop: $drop"
        binding.tvLogDropped.setTextColor(if (drop > 0) Color.parseColor("#F85149") else Color.parseColor("#8B949E"))
        binding.tvLogSize.text = String.format(Locale.US, "%.0f KB", asyncLogger.fileSizeBytes / 1024.0)

        // RAW DEBUG widget
        binding.tvDebugLastTx.text = "Last TX: $debugLastTx"
        binding.tvDebugLastRx.text = "Last RX: $debugLastRx"
        binding.tvDebugParsed.text = "Parsed: $debugLastParsed"
        binding.tvDebugLatency.text = "Latency: ${debugLastLatency}ms"
    }

    private fun updateRpmSample(dec: DecodedPid, resp: ElmResponse) {
        sampleRpm = SignalSample(
            pid = "010C",
            name = "RPM",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.0f", dec.value) else dec.formatted,
            unit = "RPM",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 800_000_000L
        )
        debugLastTx = "010C"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "RPM = ${dec.formatted}"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateMapSample(dec: DecodedPid, resp: ElmResponse) {
        sampleMap = SignalSample(
            pid = "010B",
            name = "MAP",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.0f", dec.value) else dec.formatted,
            unit = "mbar",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 800_000_000L
        )
        debugLastTx = "010B"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "MAP = ${dec.formatted} mbar"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateMafSample(dec: DecodedPid, resp: ElmResponse) {
        sampleMaf = SignalSample(
            pid = "0110",
            name = "MAF",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.1f", dec.value) else dec.formatted,
            unit = "g/s",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 2_000_000_000L
        )
        debugLastTx = "0110"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "MAF = ${dec.formatted} g/s"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateSpeedSample(dec: DecodedPid, resp: ElmResponse) {
        sampleSpeed = SignalSample(
            pid = "010D",
            name = "SPEED",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.0f", dec.value) else dec.formatted,
            unit = "km/h",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 2_000_000_000L
        )
        debugLastTx = "010D"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "Speed = ${dec.formatted} km/h"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateLoadSample(dec: DecodedPid, resp: ElmResponse) {
        sampleLoad = SignalSample(
            pid = "0104",
            name = "LOAD",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.1f", dec.value) else dec.formatted,
            unit = "%",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 2_000_000_000L
        )
        debugLastTx = "0104"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "Load = ${dec.formatted} %"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateCoolantSample(dec: DecodedPid, resp: ElmResponse) {
        sampleCoolant = SignalSample(
            pid = "0105",
            name = "COOLANT",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.0f", dec.value) else dec.formatted,
            unit = "°C",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 10_000_000_000L
        )
        debugLastTx = "0105"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "Coolant = ${dec.formatted} °C"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateIatSample(dec: DecodedPid, resp: ElmResponse) {
        sampleIat = SignalSample(
            pid = "010F",
            name = "IAT",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.0f", dec.value) else dec.formatted,
            unit = "°C",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 10_000_000_000L
        )
        debugLastTx = "010F"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "IAT = ${dec.formatted} °C"
        debugLastLatency = resp.elapsedMs
    }

    private fun updateVoltageSample(dec: DecodedPid, resp: ElmResponse) {
        sampleVoltage = SignalSample(
            pid = "0142",
            name = "VOLTAGE",
            value = dec.value,
            formatted = if (dec.status == PidStatus.VALID && dec.value != null) String.format(Locale.US, "%.1f", dec.value) else dec.formatted,
            unit = "V",
            status = if (dec.status == PidStatus.VALID) "OK" else dec.status.name,
            latencyMs = resp.elapsedMs,
            rxNanos = resp.rxNanos,
            staleThresholdNs = 10_000_000_000L
        )
        debugLastTx = "0142"
        debugLastRx = resp.raw.trim()
        debugLastParsed = "Voltage = ${dec.formatted} V"
        debugLastLatency = resp.elapsedMs
    }

    private fun runPreFlightCheck() {
        if (!isConnected()) {
            Toast.makeText(this, "Спочатку підключіться до адаптера (Connect)", Toast.LENGTH_SHORT).show()
            return
        }
        if (isDiagnosticTestRunning) return
        isDiagnosticTestRunning = true

        lifecycleScope.launch {
            binding.btnCheckData.isEnabled = false
            binding.btnCheckData.text = "CHECKING..."
            binding.layoutVerdictBanner.setBackgroundColor(Color.parseColor("#1E2330"))
            binding.tvPreFlightVerdict.text = "PROBING SENSORS (0/9)..."
            binding.tvPreFlightVerdict.setTextColor(Color.parseColor("#39C5CF"))

            val pidsToTest = listOf(
                Pair("010C", "RPM"),
                Pair("010B", "MAP"),
                Pair("0110", "MAF"),
                Pair("010D", "SPEED"),
                Pair("0104", "LOAD"),
                Pair("0105", "COOLANT"),
                Pair("010F", "IAT"),
                Pair("0142", "VOLTAGE"),
                Pair("0133", "BARO")
            )

            val results = mutableMapOf<String, Boolean>()

            withContext(Dispatchers.IO) {
                pidsToTest.forEachIndexed { idx, (pid, name) ->
                    withContext(Dispatchers.Main) {
                        binding.tvPreFlightVerdict.text = "PROBING $name ($pid) [${idx + 1}/9]..."
                    }
                    val resp = elmEngine.transport.sendCommand(pid, 300L)
                    val ok = when (pid) {
                        "010C" -> {
                            val d = PidDecoder.decodeRpm(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateRpmSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "010B" -> {
                            val d = PidDecoder.decodeMap(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateMapSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "0110" -> {
                            val d = PidDecoder.decodeMaf(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateMafSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "010D" -> {
                            val d = PidDecoder.decodeSpeed(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateSpeedSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "0104" -> {
                            val d = PidDecoder.decodeLoad(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateLoadSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "0105" -> {
                            val d = PidDecoder.decodeCoolant(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateCoolantSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "010F" -> {
                            val d = PidDecoder.decodeIat(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            updateIatSample(d, resp)
                            d.status == PidStatus.VALID
                        }
                        "0142" -> {
                            var d = PidDecoder.decodeVoltage(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            if (d.status != PidStatus.VALID) {
                                val atrvResp = elmEngine.transport.sendCommand("ATRV", 200L)
                                d = PidDecoder.decodeVoltage(atrvResp.raw, atrvResp.txNanos, atrvResp.rxNanos, atrvResp.elapsedMs, atrvResp.timedOut)
                                updateVoltageSample(d, atrvResp)
                            } else {
                                updateVoltageSample(d, resp)
                            }
                            d.status == PidStatus.VALID
                        }
                        "0133" -> {
                            val d = PidDecoder.decodeBaro(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)
                            if (d.status == PidStatus.VALID && d.value != null) {
                                calibratedBaroMbar = d.value
                                calibratedBaroSource = "PID_0133"
                            }
                            d.status == PidStatus.VALID
                        }
                        else -> false
                    }
                    results[pid] = ok
                    delay(25L)
                }
            }

            val rpmOk = results["010C"] == true
            val mapOk = results["010B"] == true
            val totalPassed = results.values.count { it }

            if (rpmOk && mapOk && totalPassed == 9) {
                binding.layoutVerdictBanner.setBackgroundColor(Color.parseColor("#1A3FB950"))
                binding.tvPreFlightVerdict.text = "READY TO LOG — ALL 9 SENSORS VERIFIED (100% PARITY)"
                binding.tvPreFlightVerdict.setTextColor(Color.parseColor("#3FB950"))
            } else if (rpmOk && mapOk) {
                binding.layoutVerdictBanner.setBackgroundColor(Color.parseColor("#1AF0883E"))
                binding.tvPreFlightVerdict.text = "READY WITH WARNINGS — TURBO OK ($totalPassed/9 SENSORS OK)"
                binding.tvPreFlightVerdict.setTextColor(Color.parseColor("#F0883E"))
            } else {
                binding.layoutVerdictBanner.setBackgroundColor(Color.parseColor("#1AF85149"))
                binding.tvPreFlightVerdict.text = "NOT READY — CORE TURBO SIGNALS FAILED (RPM/MAP TIMEOUT)"
                binding.tvPreFlightVerdict.setTextColor(Color.parseColor("#F85149"))
            }

            binding.btnCheckData.isEnabled = true
            binding.btnCheckData.text = "CHECK DATA"
            isDiagnosticTestRunning = false
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch(Dispatchers.IO) {
            if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) {
                // High-Speed ELM327 timing optimizations
                elmEngine.transport.sendCommand("ATAT1", 500L)
                elmEngine.transport.sendCommand("ATST0A", 500L)
                elmEngine.transport.sendCommand("ATH0", 500L)
                elmEngine.transport.sendCommand("ATS0", 500L)

                // Initial Baro calibration attempt if uncalibrated
                if (calibratedBaroMbar == null) {
                    calibrateBaroInternal()
                }

                var step = 0
                var auxStep = 0

                var windowStart = SystemClock.elapsedRealtime()
                var windowTotalReqs = 0
                var windowRpmCount = 0
                var windowMapCount = 0
                var windowMafCount = 0
                var windowLatencySum = 0L

                while (isActive && isConnected() && !isDiagnosticTestRunning) {
                    when (step) {
                        0, 1, 3, 5, 7 -> {
                            // Core Turbo Pair: RPM (010C) + MAP (010B)
                            val rpmResp = elmEngine.transport.sendCommand("010C", 200L)
                            windowTotalReqs++
                            windowRpmCount++
                            windowLatencySum += rpmResp.elapsedMs
                            val rpmDec = PidDecoder.decodeRpm(rpmResp.raw, rpmResp.txNanos, rpmResp.rxNanos, rpmResp.elapsedMs, rpmResp.timedOut)
                            updateRpmSample(rpmDec, rpmResp)
                            asyncLogger.logRawEvent("010C", rpmDec.value, "RPM", rpmResp.raw, rpmResp.elapsedMs, rpmDec.status.name, rpmResp.txNanos, rpmResp.rxNanos)

                            val mapResp = elmEngine.transport.sendCommand("010B", 200L)
                            windowTotalReqs++
                            windowMapCount++
                            windowLatencySum += mapResp.elapsedMs
                            val mapDec = PidDecoder.decodeMap(mapResp.raw, mapResp.txNanos, mapResp.rxNanos, mapResp.elapsedMs, mapResp.timedOut)
                            updateMapSample(mapDec, mapResp)
                            asyncLogger.logRawEvent("010B", mapDec.value, "mbar", mapResp.raw, mapResp.elapsedMs, mapDec.status.name, mapResp.txNanos, mapResp.rxNanos)

                            // Monotonic delta between MAP and RPM
                            val dtMs = if (rpmResp.rxNanos > 0L && mapResp.rxNanos > 0L) {
                                (mapResp.rxNanos - rpmResp.rxNanos) / 1_000_000L
                            } else {
                                mapResp.elapsedMs
                            }

                            val pairValid = rpmDec.status == PidStatus.VALID && mapDec.status == PidStatus.VALID && dtMs in 0..150L
                            val invalidReason = if (!pairValid) {
                                if (rpmDec.status != PidStatus.VALID) "RPM_${rpmDec.status}"
                                else if (mapDec.status != PidStatus.VALID) "MAP_${mapDec.status}"
                                else "SKEW_${dtMs}ms"
                            } else ""

                            // Opportunistic engine-off Baro calibration
                            if (calibratedBaroMbar == null && sampleRpm.value != null && (sampleRpm.value ?: 0.0) < 300.0 && sampleMap.value != null && (sampleMap.value ?: 0.0) in 850.0..1080.0) {
                                calibratedBaroMbar = sampleMap.value
                                calibratedBaroSource = "ENGINE_OFF_MAP"
                            }

                            // Log Turbo Pair to CSV
                            if (sampleRpm.value != null && sampleMap.value != null) {
                                asyncLogger.logTurboPair(
                                    rpm = sampleRpm.value ?: 0.0,
                                    mapMbarAbs = sampleMap.value ?: 0.0,
                                    baroMbar = calibratedBaroMbar,
                                    baroSource = calibratedBaroSource,
                                    dtMapRpmMs = dtMs,
                                    pairValid = pairValid,
                                    invalidReason = invalidReason,
                                    mafGs = sampleMaf.value,
                                    speedKmh = sampleSpeed.value,
                                    loadPct = sampleLoad.value,
                                    coolantC = sampleCoolant.value,
                                    iatC = sampleIat.value,
                                    voltageV = sampleVoltage.value,
                                    latencyMs = rpmResp.elapsedMs + mapResp.elapsedMs,
                                    monoNs = mapResp.rxNanos
                                )
                            }
                        }
                        2 -> {
                            // MAF (0110)
                            val mafResp = elmEngine.transport.sendCommand("0110", 200L)
                            windowTotalReqs++
                            windowMafCount++
                            windowLatencySum += mafResp.elapsedMs
                            val mafDec = PidDecoder.decodeMaf(mafResp.raw, mafResp.txNanos, mafResp.rxNanos, mafResp.elapsedMs, mafResp.timedOut)
                            updateMafSample(mafDec, mafResp)
                            asyncLogger.logRawEvent("0110", mafDec.value, "g/s", mafResp.raw, mafResp.elapsedMs, mafDec.status.name, mafResp.txNanos, mafResp.rxNanos)
                        }
                        4 -> {
                            // Speed (010D)
                            val spdResp = elmEngine.transport.sendCommand("010D", 200L)
                            windowTotalReqs++
                            windowLatencySum += spdResp.elapsedMs
                            val spdDec = PidDecoder.decodeSpeed(spdResp.raw, spdResp.txNanos, spdResp.rxNanos, spdResp.elapsedMs, spdResp.timedOut)
                            updateSpeedSample(spdDec, spdResp)
                            asyncLogger.logRawEvent("010D", spdDec.value, "km/h", spdResp.raw, spdResp.elapsedMs, spdDec.status.name, spdResp.txNanos, spdResp.rxNanos)
                        }
                        6 -> {
                            // Load (0104)
                            val lodResp = elmEngine.transport.sendCommand("0104", 200L)
                            windowTotalReqs++
                            windowLatencySum += lodResp.elapsedMs
                            val lodDec = PidDecoder.decodeLoad(lodResp.raw, lodResp.txNanos, lodResp.rxNanos, lodResp.elapsedMs, lodResp.timedOut)
                            updateLoadSample(lodDec, lodResp)
                            asyncLogger.logRawEvent("0104", lodDec.value, "%", lodResp.raw, lodResp.elapsedMs, lodDec.status.name, lodResp.txNanos, lodResp.rxNanos)
                        }
                        8 -> {
                            // Aux Sensors Cycle: Coolant -> IAT -> Voltage -> BARO
                            when (auxStep % 4) {
                                0 -> {
                                    val clnResp = elmEngine.transport.sendCommand("0105", 200L)
                                    windowTotalReqs++
                                    windowLatencySum += clnResp.elapsedMs
                                    val clnDec = PidDecoder.decodeCoolant(clnResp.raw, clnResp.txNanos, clnResp.rxNanos, clnResp.elapsedMs, clnResp.timedOut)
                                    updateCoolantSample(clnDec, clnResp)
                                    asyncLogger.logRawEvent("0105", clnDec.value, "°C", clnResp.raw, clnResp.elapsedMs, clnDec.status.name, clnResp.txNanos, clnResp.rxNanos)
                                }
                                1 -> {
                                    val iatResp = elmEngine.transport.sendCommand("010F", 200L)
                                    windowTotalReqs++
                                    windowLatencySum += iatResp.elapsedMs
                                    val iatDec = PidDecoder.decodeIat(iatResp.raw, iatResp.txNanos, iatResp.rxNanos, iatResp.elapsedMs, iatResp.timedOut)
                                    updateIatSample(iatDec, iatResp)
                                    asyncLogger.logRawEvent("010F", iatDec.value, "°C", iatResp.raw, iatResp.elapsedMs, iatDec.status.name, iatResp.txNanos, iatResp.rxNanos)
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
                                    updateVoltageSample(vDec, vResp)
                                    asyncLogger.logRawEvent("0142", vDec.value, "V", vResp.raw, vResp.elapsedMs, vDec.status.name, vResp.txNanos, vResp.rxNanos)
                                }
                                3 -> {
                                    if (calibratedBaroMbar == null) {
                                        val baroResp = elmEngine.transport.sendCommand("0133", 200L)
                                        windowTotalReqs++
                                        windowLatencySum += baroResp.elapsedMs
                                        val baroDec = PidDecoder.decodeBaro(baroResp.raw, baroResp.txNanos, baroResp.rxNanos, baroResp.elapsedMs, baroResp.timedOut)
                                        if (baroDec.status == PidStatus.VALID && baroDec.value != null) {
                                            calibratedBaroMbar = baroDec.value
                                            calibratedBaroSource = "PID_0133"
                                        }
                                        asyncLogger.logRawEvent("0133", baroDec.value, "mbar", baroResp.raw, baroResp.elapsedMs, baroDec.status.name, baroResp.txNanos, baroResp.rxNanos)
                                    }
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
            } else if (connectionMode == AppConnectionMode.VAG_OEM_TP20) {
                // Mode B: VAG OEM Group 011/008/003 (VW TP 2.0 CAN)
                var loopCount = 0
                var windowStartTime = SystemClock.elapsedRealtime()
                var windowGroupCount = 0
                var lastLatencyMs = 0L
                var lastUiUpdateMs = 0L

                while (isActive && isConnected()) {
                    loopCount++
                    val cycleStart = SystemClock.elapsedRealtime()

                    val g011 = elmEngine.readMeasuringGroup(11)
                    lastLatencyMs = SystemClock.elapsedRealtime() - cycleStart
                    windowGroupCount++

                    var curRpm = 0.0
                    var boostSpec: Double? = null
                    var boostAct: Double? = null
                    var n75: Double? = null

                    if (g011 != null && g011.values.size >= 4) {
                        curRpm = g011.values[0].rawValue
                        boostSpec = g011.values[1].rawValue
                        boostAct = g011.values[2].rawValue
                        n75 = g011.values[3].rawValue

                        if (asyncLogger.isLogging) {
                            asyncLogger.logTurboPair(
                                rpm = curRpm,
                                mapMbarAbs = boostAct ?: 0.0,
                                baroMbar = null,
                                baroSource = "TP20",
                                dtMapRpmMs = 0L,
                                pairValid = true,
                                mafGs = null,
                                latencyMs = lastLatencyMs
                            )
                        }
                    }

                    if (loopCount % 4 == 0) {
                        val g008 = elmEngine.readMeasuringGroup(8)
                        windowGroupCount++
                        if (g008 != null && g008.values.size >= 4) {
                            lastDriverWish = g008.values[1].rawValue
                            lastTorqueLim = g008.values[2].rawValue
                            lastSmokeLim = g008.values[3].rawValue
                        }
                    }

                    if (loopCount % 8 == 0) {
                        val g003 = elmEngine.readMeasuringGroup(3)
                        windowGroupCount++
                        if (g003 != null && g003.values.size >= 4) {
                            lastMafAct = g003.values[2].rawValue
                        }
                    }

                    val nowMono = SystemClock.elapsedRealtime()
                    val dt = (nowMono - windowStartTime) / 1000.0
                    if (dt >= 0.5) {
                        windowStartTime = nowMono
                        windowGroupCount = 0
                    }

                    if (nowMono - lastUiUpdateMs >= 40L) {
                        lastUiUpdateMs = nowMono
                        val snapRpm = curRpm
                        val snapSpec = boostSpec
                        val snapAct = boostAct
                        val snapN75 = n75
                        val snapWish = lastDriverWish
                        val snapTorq = lastTorqueLim
                        val snapSmoke = lastSmokeLim
                        val snapMaf = lastMafAct

                        withContext(Dispatchers.Main) {
                            binding.tvRpm.text = String.format(Locale.US, "%.0f RPM", snapRpm)
                            binding.tvBoostSpecified.text = if (snapSpec != null) String.format(Locale.US, "%.0f", snapSpec) else "---"
                            binding.tvBoostActual.text = if (snapAct != null) String.format(Locale.US, "%.0f", snapAct) else "---"
                            binding.tvN75.text = if (snapN75 != null) String.format(Locale.US, "%.1f", snapN75) else "---"

                            binding.tvDriverWish.text = String.format(Locale.US, "Wish: %.1f mg", snapWish)
                            binding.tvTorqueLimit.text = String.format(Locale.US, "Torq: %.1f mg", snapTorq)
                            binding.tvSmokeLimit.text = String.format(Locale.US, "Smoke: %.1f mg", snapSmoke)
                            binding.tvMafActual.text = String.format(Locale.US, "MAF: %.1f mg/s", snapMaf)
                        }
                    }
                }
            } else {
                // USB Hardware / Simulator Demo
                var cycleCount = 0
                while (isActive && isConnected()) {
                    val cycleStartTime = System.currentTimeMillis()
                    cycleCount++
                    try {
                        val group = engine.readMeasuringGroup(11)
                        if (group != null && group.values.size >= 4) {
                            val rpmVal = group.values[0]
                            val boostReqVal = group.values[1]
                            val boostActVal = group.values[2]
                            val n75Val = group.values[3]

                            lastRpm = rpmVal.rawValue
                            lastBoostReq = boostReqVal.rawValue
                            lastBoostAct = boostActVal.rawValue
                            lastN75 = n75Val.rawValue

                            withContext(Dispatchers.Main) {
                                binding.tvRpm.text = if (rpmVal.formattedValue == "N/A") "--- RPM" else "${rpmVal.formattedValue} RPM"
                                binding.tvBoostSpecified.text = boostReqVal.formattedValue
                                binding.tvBoostActual.text = boostActVal.formattedValue
                                binding.tvN75.text = n75Val.formattedValue
                                binding.tvSubStatus.text = "USB/Demo Stream: #${cycleCount}"
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("MainActivity", "USB/Demo polling error: ${e.message}")
                    }
                    val elapsed = System.currentTimeMillis() - cycleStartTime
                    delay((30L - elapsed).coerceAtLeast(10L))
                }
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private suspend fun calibrateBaroInternal(): Boolean {
        val resp33 = elmEngine.transport.sendCommand("0133", 400L)
        val dec33 = PidDecoder.decodeBaro(resp33.raw, resp33.txNanos, resp33.rxNanos, resp33.elapsedMs, resp33.timedOut)
        if (dec33.status == PidStatus.VALID && dec33.value != null) {
            calibratedBaroMbar = dec33.value
            calibratedBaroSource = "PID_0133"
            Log.i("BARO_CALIB", "Baro calibrated via PID 0133: $calibratedBaroMbar mbar")
            return true
        }

        val rpmResp = elmEngine.transport.sendCommand("010C", 200L)
        val rpmDec = PidDecoder.decodeRpm(rpmResp.raw, rpmResp.txNanos, rpmResp.rxNanos, rpmResp.elapsedMs, rpmResp.timedOut)
        val engineOff = rpmDec.status == PidStatus.VALID && (rpmDec.value ?: 0.0) < 300.0

        if (engineOff) {
            val samples = mutableListOf<Double>()
            for (i in 0 until 5) {
                val mapResp = elmEngine.transport.sendCommand("010B", 200L)
                val mapDec = PidDecoder.decodeMap(mapResp.raw, mapResp.txNanos, mapResp.rxNanos, mapResp.elapsedMs, mapResp.timedOut)
                if (mapDec.status == PidStatus.VALID && mapDec.value != null && mapDec.value in 850.0..1080.0) {
                    samples.add(mapDec.value)
                }
                delay(40L)
            }
            if (samples.size >= 3) {
                samples.sort()
                calibratedBaroMbar = samples[samples.size / 2]
                calibratedBaroSource = "ENGINE_OFF_MAP"
                Log.i("BARO_CALIB", "Baro calibrated via ENGINE_OFF_MAP: $calibratedBaroMbar mbar")
                return true
            }
        }

        calibratedBaroMbar = null
        calibratedBaroSource = "UNAVAILABLE"
        return false
    }

    private fun calibrateBaroExplicit() {
        if (!isConnected()) {
            Toast.makeText(this, "Connect to ECU first", Toast.LENGTH_SHORT).show()
            return
        }
        if (isDiagnosticTestRunning) return
        isDiagnosticTestRunning = true
        val wasPolling = pollingJob != null
        stopPolling()

        lifecycleScope.launch {
            try {
                binding.tvSubStatus.text = "Calibrating Barometric pressure..."
                val ok = calibrateBaroInternal()
                if (ok) {
                    val msg = "BARO Calibrated: ${String.format(Locale.US, "%.0f", calibratedBaroMbar)} mbar ($calibratedBaroSource)"
                    binding.tvSmokeLimit.text = "BARO: ${String.format(Locale.US, "%.0f", calibratedBaroMbar)} ($calibratedBaroSource)"
                    Toast.makeText(this@MainActivity, msg, Toast.LENGTH_LONG).show()
                } else {
                    binding.tvSmokeLimit.text = "BARO: UNAVAILABLE"
                    Toast.makeText(this@MainActivity, "BARO Unavailable (PID 0133 unsupported, engine running)", Toast.LENGTH_LONG).show()
                }
            } finally {
                isDiagnosticTestRunning = false
                if (wasPolling && isConnected()) {
                    startPolling()
                }
            }
        }
    }

    private fun runRpmStressTest() {
        if (!isConnected()) {
            Toast.makeText(this, "Connect to ECU first", Toast.LENGTH_SHORT).show()
            return
        }
        if (isDiagnosticTestRunning) return
        isDiagnosticTestRunning = true
        val wasPolling = pollingJob != null
        stopPolling()

        lifecycleScope.launch {
            try {
                binding.tvDtcList.text = "=== 10s RPM STRESS TEST (PID 010C ONLY) ===\nExecuting rapid acquisition..."
                val startTime = SystemClock.elapsedRealtime()
                var attempted = 0
                var valid = 0
                var timeouts = 0
                var maxRpm = 0.0
                val latencies = mutableListOf<Long>()

                while (SystemClock.elapsedRealtime() - startTime < 10000L && isActive && isConnected()) {
                    attempted++
                    val resp = elmEngine.transport.sendCommand("010C", 150L)
                    val dec = PidDecoder.decodeRpm(resp.raw, resp.txNanos, resp.rxNanos, resp.elapsedMs, resp.timedOut)

                    if (asyncLogger.isLogging) {
                        asyncLogger.logRawEvent("010C", dec.value, "RPM", resp.raw, resp.elapsedMs, dec.status.name, resp.txNanos, resp.rxNanos)
                    }

                    if (dec.status == PidStatus.VALID && dec.value != null) {
                        valid++
                        latencies.add(resp.elapsedMs)
                        if (dec.value > maxRpm) maxRpm = dec.value
                        binding.tvRpm.text = String.format(Locale.US, "%.0f RPM", dec.value)
                    } else {
                        timeouts++
                    }

                    val elapsedSec = (SystemClock.elapsedRealtime() - startTime) / 1000.0
                    binding.tvSubStatus.text = "RPM Stress Test: ${String.format(Locale.US, "%.1f", elapsedSec)}s / 10s (#$attempted)"
                }

                val durationSec = (SystemClock.elapsedRealtime() - startTime) / 1000.0
                val hz = if (durationSec > 0.0) valid / durationSec else 0.0
                val avgLat = if (latencies.isNotEmpty()) latencies.average() else 0.0
                val minLat = latencies.minOrNull() ?: 0L
                val maxLat = latencies.maxOrNull() ?: 0L

                val report = """
=== 10s RPM STRESS TEST RESULTS ===
Target PID: 010C (Engine RPM)
Time Elapsed: ${String.format(Locale.US, "%.2f", durationSec)} s
Total Queries Sent: $attempted
Valid Responses: $valid
Timeouts / Errors: $timeouts
Effective Acquisition Rate: ${String.format(Locale.US, "%.1f", hz)} Hz / req/s
Peak RPM Observed: ${String.format(Locale.US, "%.0f", maxRpm)} RPM
Latency (ms): min=$minLat, avg=${String.format(Locale.US, "%.1f", avgLat)}, max=$maxLat
====================================
                """.trimIndent()

                Log.i("RPM_STRESS_TEST", report)
                binding.tvDtcList.text = report
                Toast.makeText(this@MainActivity, "Stress Test Complete! Rate: ${String.format(Locale.US, "%.1f", hz)} Hz", Toast.LENGTH_LONG).show()
            } finally {
                isDiagnosticTestRunning = false
                if (wasPolling && isConnected()) {
                    startPolling()
                }
            }
        }
    }

    private fun runTimingBenchmark() {
        if (!isConnected()) {
            Toast.makeText(this, "Connect to ECU first", Toast.LENGTH_SHORT).show()
            return
        }
        if (isDiagnosticTestRunning) return
        isDiagnosticTestRunning = true
        val wasPolling = pollingJob != null
        stopPolling()

        lifecycleScope.launch {
            try {
                binding.tvDtcList.text = "=== TIMING BENCHMARK (Phase 1: Default ATAT0/ATST32) ===\nRunning 10 seconds..."
                elmEngine.transport.sendCommand("ATAT0", 500L)
                elmEngine.transport.sendCommand("ATST32", 500L)

                val p1Start = SystemClock.elapsedRealtime()
                var p1Queries = 0
                val p1Latencies = mutableListOf<Long>()
                while (SystemClock.elapsedRealtime() - p1Start < 10000L && isActive && isConnected()) {
                    p1Queries++
                    val resp = elmEngine.transport.sendCommand("010C", 300L)
                    if (!resp.timedOut) {
                        p1Latencies.add(resp.elapsedMs)
                    }
                    val elapsedSec = (SystemClock.elapsedRealtime() - p1Start) / 1000.0
                    binding.tvSubStatus.text = "Bench Phase 1: ${String.format(Locale.US, "%.1f", elapsedSec)}s / 10s (#$p1Queries)"
                }
                val p1Duration = (SystemClock.elapsedRealtime() - p1Start) / 1000.0
                val p1Hz = if (p1Duration > 0.0) p1Queries / p1Duration else 0.0
                val p1AvgLat = if (p1Latencies.isNotEmpty()) p1Latencies.average() else 0.0

                binding.tvDtcList.text = "=== TIMING BENCHMARK (Phase 2: Aggressive ATAT1/ATST0A) ===\nRunning 10 seconds..."
                elmEngine.transport.sendCommand("ATAT1", 500L)
                elmEngine.transport.sendCommand("ATST0A", 500L)

                val p2Start = SystemClock.elapsedRealtime()
                var p2Queries = 0
                val p2Latencies = mutableListOf<Long>()
                while (SystemClock.elapsedRealtime() - p2Start < 10000L && isActive && isConnected()) {
                    p2Queries++
                    val resp = elmEngine.transport.sendCommand("010C", 300L)
                    if (!resp.timedOut) {
                        p2Latencies.add(resp.elapsedMs)
                    }
                    val elapsedSec = (SystemClock.elapsedRealtime() - p2Start) / 1000.0
                    binding.tvSubStatus.text = "Bench Phase 2: ${String.format(Locale.US, "%.1f", elapsedSec)}s / 10s (#$p2Queries)"
                }
                val p2Duration = (SystemClock.elapsedRealtime() - p2Start) / 1000.0
                val p2Hz = if (p2Duration > 0.0) p2Queries / p2Duration else 0.0
                val p2AvgLat = if (p2Latencies.isNotEmpty()) p2Latencies.average() else 0.0

                val speedupPct = if (p1Hz > 0.0) ((p2Hz - p1Hz) / p1Hz) * 100.0 else 0.0

                val benchReport = """
=== TIMING BENCHMARK RESULTS ===
Phase 1 (Default: ATAT0 + ATST32):
  Queries: $p1Queries
  Rate: ${String.format(Locale.US, "%.1f", p1Hz)} req/s
  Avg Latency: ${String.format(Locale.US, "%.1f", p1AvgLat)} ms

Phase 2 (Optimized: ATAT1 + ATST0A):
  Queries: $p2Queries
  Rate: ${String.format(Locale.US, "%.1f", p2Hz)} req/s
  Avg Latency: ${String.format(Locale.US, "%.1f", p2AvgLat)} ms

Speedup: ${String.format(Locale.US, "%+.1f%%", speedupPct)}
================================
                """.trimIndent()

                Log.i("TIMING_BENCHMARK", benchReport)
                binding.tvDtcList.text = benchReport
                Toast.makeText(this@MainActivity, "Benchmark Complete! Speedup: ${String.format(Locale.US, "%+.1f%%", speedupPct)}", Toast.LENGTH_LONG).show()
            } finally {
                elmEngine.transport.sendCommand("ATAT1", 500L)
                elmEngine.transport.sendCommand("ATST0A", 500L)
                elmEngine.transport.sendCommand("ATH0", 500L)
                elmEngine.transport.sendCommand("ATS0", 500L)
                isDiagnosticTestRunning = false
                if (wasPolling && isConnected()) {
                    startPolling()
                }
            }
        }
    }

    private fun scanDtc() {
        if (!isConnected()) {
            Toast.makeText(this, "Connect to ECU first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvDtcList.text = "Scanning ECU diagnostic trouble codes..."
        lifecycleScope.launch {
            val wasPolling = pollingJob != null
            stopPolling()
            try {
                val list = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD || connectionMode == AppConnectionMode.VAG_OEM_TP20) {
                    elmEngine.readFaultCodes()
                } else {
                    engine.readFaultCodes()
                }

                if (list.isEmpty()) {
                    binding.tvDtcList.text = "No fault codes found. System OK!"
                } else {
                    val sb = StringBuilder()
                    sb.append("Found ${list.size} Fault Code(s):\n\n")
                    for (dtc in list) {
                        sb.append("● ${dtc.saeCode} (${dtc.vagCode}):\n")
                        sb.append("  EN: ${dtc.descriptionEn}\n")
                        sb.append("  UA: ${dtc.descriptionUk}\n\n")
                    }
                    binding.tvDtcList.text = sb.toString()
                }
            } finally {
                if (wasPolling && isConnected()) {
                    startPolling()
                }
            }
        }
    }

    private fun confirmClearDtc() {
        AlertDialog.Builder(this)
            .setTitle("Clear Fault Codes")
            .setMessage("Do you want to send command to clear all ECU fault codes?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    val wasPolling = pollingJob != null
                    stopPolling()
                    try {
                        val cleared = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD || connectionMode == AppConnectionMode.VAG_OEM_TP20) {
                            elmEngine.clearFaultCodes()
                        } else {
                            engine.clearFaultCodes()
                        }
                        if (cleared) {
                            Toast.makeText(this@MainActivity, "Fault codes cleared successfully", Toast.LENGTH_SHORT).show()
                            binding.tvDtcList.text = "Fault codes cleared."
                        } else {
                            Toast.makeText(this@MainActivity, "Failed to clear DTCs", Toast.LENGTH_SHORT).show()
                        }
                    } finally {
                        if (wasPolling && isConnected()) {
                            startPolling()
                        }
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatusUI() {
        when (connectionMode) {
            AppConnectionMode.SIMULATOR_DEMO -> {
                when (engine.state) {
                    DiagState.CONNECTED, DiagState.POLLING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_green)
                        binding.tvStatus.text = "Simulated EDC16 (Demo Mode)"
                        binding.tvSubStatus.text = "Virtual Golf 5 1.9 TDI BLS active"
                        binding.btnConnect.text = "Disconnect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DA3633"))
                        binding.btnConnect.setTextColor(Color.WHITE)
                    }
                    DiagState.CONNECTING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        binding.tvStatus.text = "Starting Simulation..."
                        binding.tvSubStatus.text = "Initializing virtual ECU telemetry"
                    }
                    else -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        binding.tvStatus.text = "Simulator Ready"
                        binding.tvSubStatus.text = "Tap Connect to start simulated telemetry"
                        binding.btnConnect.text = "Connect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
                        binding.btnConnect.setTextColor(Color.WHITE)
                    }
                }
            }
            AppConnectionMode.TURBO_FAST_OBD, AppConnectionMode.VAG_OEM_TP20 -> {
                when (elmEngine.state) {
                    DiagState.CONNECTED, DiagState.POLLING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_green)
                        val protoMode = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) {
                            "Turbo Fast (OBD-II)"
                        } else if (elmEngine.isTp20Active) {
                            "VW TP 2.0 (Group 011)"
                        } else {
                            "Generic OBD-II (Mode 01)"
                        }
                        binding.tvStatus.text = "Connected: $protoMode"
                        binding.tvSubStatus.text = "ECU Online | ${elmEngine.transport.connectedDeviceName ?: "V-LINK"}"
                        binding.btnConnect.text = "Disconnect"
                        binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#DA3633"))
                        binding.btnConnect.setTextColor(Color.WHITE)
                    }
                    DiagState.CONNECTING -> {
                        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                        binding.tvStatus.text = "Connecting..."
                        binding.tvSubStatus.text = if (connectionMode == AppConnectionMode.TURBO_FAST_OBD) {
                            "Detecting ELM327 Bluetooth (Turbo Fast)..."
                        } else {
                            "Detecting ELM327 Bluetooth & TP 2.0..."
                        }
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
        }
    }
}
