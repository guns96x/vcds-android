package com.vag.vcdsandroid.ui

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.ColorStateList
import android.graphics.Color
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.vag.vcdsandroid.R
import com.vag.vcdsandroid.databinding.ActivityMainBinding
import com.vag.vcdsandroid.logging.CsvLogger
import com.vag.vcdsandroid.protocol.DiagState
import com.vag.vcdsandroid.protocol.Kwp2000DiagnosticEngine
import com.vag.vcdsandroid.protocol.TransportMode
import com.vag.vcdsandroid.usb.UsbKwpTransport
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var transport: UsbKwpTransport
    private lateinit var engine: Kwp2000DiagnosticEngine
    private lateinit var logger: CsvLogger

    private var pollingJob: Job? = null

    // Cache latest live telemetry for multi-group logging
    private var lastRpm: Double = 0.0
    private var lastBoostReq: Double = 0.0
    private var lastBoostAct: Double = 0.0
    private var lastN75: Double = 0.0
    private var lastDriverWish: Double = 0.0
    private var lastTorqueLim: Double = 0.0
    private var lastSmokeLim: Double = 0.0
    private var lastMafAct: Double = 0.0

    private val usbReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            when (intent?.action) {
                UsbKwpTransport.ACTION_USB_PERMISSION -> {
                    val device: UsbDevice? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
                    } else {
                        @Suppress("DEPRECATION")
                        intent.getParcelableExtra(UsbManager.EXTRA_DEVICE)
                    }
                    val granted = intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)
                    if (granted && device != null) {
                        Toast.makeText(this@MainActivity, "USB Permission Granted", Toast.LENGTH_SHORT).show()
                        performConnect()
                    } else {
                        Toast.makeText(this@MainActivity, "USB Permission Denied", Toast.LENGTH_SHORT).show()
                    }
                }
                UsbManager.ACTION_USB_DEVICE_ATTACHED -> {
                    Toast.makeText(this@MainActivity, "USB Cable Attached", Toast.LENGTH_SHORT).show()
                    updateStatusUI()
                }
                UsbManager.ACTION_USB_DEVICE_DETACHED -> {
                    Toast.makeText(this@MainActivity, "USB Cable Detached", Toast.LENGTH_SHORT).show()
                    performDisconnect()
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
        logger = CsvLogger(this)

        setupListeners()
        updateStatusUI()

        val filter = IntentFilter().apply {
            addAction(UsbKwpTransport.ACTION_USB_PERMISSION)
            addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
            addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(usbReceiver, filter, RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(usbReceiver, filter)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            unregisterReceiver(usbReceiver)
        } catch (_: Exception) {}
        performDisconnect()
    }

    private fun setupListeners() {
        // Mode toggle: USB vs Simulated Demo
        binding.btnModeToggle.setOnClickListener {
            val newMode = if (engine.mode == TransportMode.USB_HARDWARE) {
                TransportMode.SIMULATOR_DEMO
            } else {
                TransportMode.USB_HARDWARE
            }
            engine.setMode(newMode)
            binding.btnModeToggle.text = if (newMode == TransportMode.SIMULATOR_DEMO) "Mode: Demo (Sim)" else "Mode: USB"
            updateStatusUI()
        }

        // Connect button
        binding.btnConnect.setOnClickListener {
            if (engine.state == DiagState.CONNECTED || engine.state == DiagState.POLLING) {
                performDisconnect()
            } else {
                checkAndConnect()
            }
        }

        // WOT Log toggle
        binding.btnToggleLog.setOnClickListener {
            if (!logger.isLogging) {
                startWotLog()
            } else {
                stopWotLog()
            }
        }

        // Scan DTC
        binding.btnScanDtc.setOnClickListener {
            scanDtc()
        }

        // Clear DTC
        binding.btnClearDtc.setOnClickListener {
            confirmClearDtc()
        }
    }

    private fun checkAndConnect() {
        if (engine.mode == TransportMode.USB_HARDWARE) {
            val device = transport.findAvailableDevice()
            if (device == null) {
                Toast.makeText(this, "No USB FTDI / KKL cable detected. Plug in OTG adapter.", Toast.LENGTH_LONG).show()
                return
            }
            if (!transport.hasPermission(device)) {
                transport.requestPermission(device)
                return
            }
        }
        performConnect()
    }

    private fun performConnect() {
        binding.tvStatus.text = "Connecting via K-Line (10400 bps)..."
        binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)

        lifecycleScope.launch {
            val success = engine.connect()
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
        if (logger.isLogging) {
            stopWotLog()
        }
        engine.disconnect()
        updateStatusUI()
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = lifecycleScope.launch {
            var cycleCount = 0
            while (isActive && (engine.state == DiagState.CONNECTED || engine.state == DiagState.POLLING)) {
                // High-speed polling: Poll Group 011 every cycle, alternate 008 and 003
                val g011 = engine.readMeasuringGroup(11)
                if (g011 != null && g011.values.size >= 4) {
                    lastRpm = g011.values[0].rawValue
                    lastBoostReq = g011.values[1].rawValue
                    lastBoostAct = g011.values[2].rawValue
                    lastN75 = g011.values[3].rawValue

                    binding.tvRpm.text = String.format(Locale.US, "%.0f RPM", lastRpm)
                    binding.tvBoostSpecified.text = String.format(Locale.US, "%.0f", lastBoostReq)
                    binding.tvBoostActual.text = String.format(Locale.US, "%.0f", lastBoostAct)
                    binding.tvN75.text = String.format(Locale.US, "%.1f", lastN75)

                    binding.liveGraphView.addTelemetryPoint(
                        lastBoostReq.toFloat(),
                        lastBoostAct.toFloat(),
                        lastN75.toFloat()
                    )
                }

                if (cycleCount % 2 == 0) {
                    val g008 = engine.readMeasuringGroup(8)
                    if (g008 != null && g008.values.size >= 4) {
                        lastDriverWish = g008.values[1].rawValue
                        lastTorqueLim = g008.values[2].rawValue
                        lastSmokeLim = g008.values[3].rawValue

                        binding.tvDriverWish.text = String.format(Locale.US, "Driver: %.1f mg", lastDriverWish)
                        binding.tvTorqueLimit.text = String.format(Locale.US, "Torque: %.1f mg", lastTorqueLim)
                        binding.tvSmokeLimit.text = String.format(Locale.US, "Smoke: %.1f mg", lastSmokeLim)
                    }
                } else {
                    val g003 = engine.readMeasuringGroup(3)
                    if (g003 != null && g003.values.size >= 4) {
                        val mafReq = g003.values[1].rawValue
                        lastMafAct = g003.values[2].rawValue
                        val egr = g003.values[3].rawValue

                        binding.tvMafSpecified.text = String.format(Locale.US, "Target: %.0f mg/s", mafReq)
                        binding.tvMafActual.text = String.format(Locale.US, "Actual: %.0f mg/s", lastMafAct)
                        binding.tvEgrDuty.text = String.format(Locale.US, "EGR: %.1f %%", egr)
                    }
                }

                // If logging, write sample
                if (logger.isLogging) {
                    logger.logSample(
                        rpm = lastRpm,
                        boostSpecified = lastBoostReq,
                        boostActual = lastBoostAct,
                        n75Duty = lastN75,
                        driverWishIq = lastDriverWish,
                        torqueLimitIq = lastTorqueLim,
                        smokeLimitIq = lastSmokeLim,
                        mafActual = lastMafAct
                    )

                    binding.tvLogMetrics.text = String.format(
                        Locale.US,
                        "REC ● %d samples | %.1fs | Peak: %.0f mbar",
                        logger.getSampleCount(),
                        logger.getDurationSeconds(),
                        logger.peakBoostMbar
                    )
                }

                cycleCount++
                delay(if (engine.mode == TransportMode.SIMULATOR_DEMO) 80 else 30)
            }
        }
    }

    private fun stopPolling() {
        pollingJob?.cancel()
        pollingJob = null
    }

    private fun startWotLog() {
        val file = logger.startNewLog()
        binding.btnToggleLog.text = "STOP LOGGING (REC)"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#F85149"))
        binding.tvLogMetrics.text = "Recording to ${file.name}..."
        Toast.makeText(this, "Started WOT Log: ${file.name}", Toast.LENGTH_SHORT).show()
    }

    private fun stopWotLog() {
        val file: File? = logger.stopLog()
        binding.btnToggleLog.text = "START 4TH GEAR WOT LOG"
        binding.btnToggleLog.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#3FB950"))
        if (file != null) {
            val msg = "Saved: ${file.name} (${logger.getSampleCount()} samples, Peak Boost: ${logger.peakBoostMbar.toInt()} mbar)"
            binding.tvLogMetrics.text = msg
            Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        }
    }

    private fun scanDtc() {
        if (engine.state != DiagState.CONNECTED && engine.state != DiagState.POLLING) {
            Toast.makeText(this, "Connect to ECU first", Toast.LENGTH_SHORT).show()
            return
        }

        binding.tvDtcList.text = "Scanning ECU diagnostic trouble codes..."
        lifecycleScope.launch {
            val list = engine.readFaultCodes()
            if (list.isEmpty()) {
                binding.tvDtcList.text = "No fault codes found. System OK!"
            } else {
                val sb = StringBuilder()
                sb.append("Found ${list.size} Fault Code(s):\n\n")
                for (dtc in list) {
                    sb.append("● ${dtc.saeCode} (${dtc.vagCode}):\n")
                    sb.append("  EN: ${dtc.descriptionEn}\n")
                    sb.append("  UA: ${dtc.descriptionUk}\n")
                    sb.append("  Status: 0x${Integer.toHexString(dtc.statusByte).uppercase()} (MIL: ${if (dtc.isMilActive) "ON" else "OFF"})\n\n")
                }
                binding.tvDtcList.text = sb.toString()
            }
        }
    }

    private fun confirmClearDtc() {
        AlertDialog.Builder(this)
            .setTitle("Clear Fault Codes")
            .setMessage("Do you want to send KWP2000 0x14 command to clear all ECU fault codes?")
            .setPositiveButton("Clear") { _, _ ->
                lifecycleScope.launch {
                    val cleared = engine.clearFaultCodes()
                    if (cleared) {
                        Toast.makeText(this@MainActivity, "Fault codes cleared successfully", Toast.LENGTH_SHORT).show()
                        binding.tvDtcList.text = "Fault codes cleared."
                    } else {
                        Toast.makeText(this@MainActivity, "Failed to clear DTCs", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun updateStatusUI() {
        when (engine.state) {
            DiagState.CONNECTED, DiagState.POLLING -> {
                binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_green)
                binding.tvStatus.text = if (engine.mode == TransportMode.SIMULATOR_DEMO) {
                    "Simulated EDC16 (Demo Mode)"
                } else {
                    "Connected (K-Line 10400 bps)"
                }
                binding.btnConnect.text = "Disconnect"
                binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#30363D"))
            }
            DiagState.CONNECTING -> {
                binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_yellow)
                binding.tvStatus.text = "Connecting..."
            }
            DiagState.ERROR -> {
                binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_red)
                binding.tvStatus.text = "Error: ${engine.lastError ?: "Failed"}"
                binding.btnConnect.text = "Retry"
                binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
            }
            DiagState.DISCONNECTED -> {
                binding.statusIndicator.setBackgroundResource(R.drawable.ic_status_dot_red)
                binding.tvStatus.text = "Disconnected"
                binding.btnConnect.text = "Connect"
                binding.btnConnect.backgroundTintList = ColorStateList.valueOf(Color.parseColor("#388BFD"))
            }
        }
    }
}
