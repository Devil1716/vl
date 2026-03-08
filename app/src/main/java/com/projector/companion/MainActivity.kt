package com.projector.companion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SCREEN_CAPTURE_REQUEST = 1001
    private var statusText: TextView? = null
    private var ipText: TextView? = null
    private var startButton: Button? = null
    private var stopButton: Button? = null
    private var statusDot: View? = null
    private var accessibilityStatus: TextView? = null
    private var accessibilityBtn: Button? = null
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusDot = findViewById(R.id.statusDot)
        accessibilityStatus = findViewById(R.id.accessibilityStatus)
        accessibilityBtn = findViewById(R.id.accessibilityBtn)

        updateIpAddress()

        ipText?.setOnClickListener {
            val logFile = java.io.File(applicationContext.getExternalFilesDir(null), "projector_error.log")
            val msg = if (logFile.exists()) logFile.readText() else "No error log found."
            android.app.AlertDialog.Builder(this)
                .setTitle("Crash Log")
                .setMessage(msg)
                .setPositiveButton("OK", null)
                .show()
        }

        startButton?.setOnClickListener {
            requestScreenCapture()
        }

        stopButton?.setOnClickListener {
            stopStreaming()
        }

        accessibilityBtn?.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        val unrestrictBtn: Button = findViewById(R.id.unrestrictBtn)
        unrestrictBtn.setOnClickListener {
            val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            intent.data = android.net.Uri.parse("package:$packageName")
            startActivity(intent)
        }

        // Check for updates
        UpdateChecker(this).checkInBackground()

        // Start discovery service
        startService(Intent(this, DiscoveryService::class.java))
    }

    override fun onResume() {
        super.onResume()
        updateAccessibilityStatus()
    }

    private fun isAccessibilityEnabled(): Boolean {
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        return enabledServices.contains(packageName + "/" + InputAccessibilityService::class.java.canonicalName)
    }

    private fun updateAccessibilityStatus() {
        val unrestrictBtn: Button = findViewById(R.id.unrestrictBtn)
        if (isAccessibilityEnabled()) {
            accessibilityStatus?.text = "✓ Phone control enabled"
            accessibilityStatus?.setTextColor(android.graphics.Color.parseColor("#4ade80"))
            accessibilityBtn?.visibility = View.GONE
            unrestrictBtn.visibility = View.GONE
        } else {
            accessibilityStatus?.text = "⚠ Enable Accessibility for full control"
            accessibilityStatus?.setTextColor(android.graphics.Color.parseColor("#f59e0b"))
            accessibilityBtn?.visibility = View.VISIBLE
            unrestrictBtn.visibility = View.VISIBLE
        }
    }

    private fun updateIpAddress() {
        try {
            val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
            val ip = wifiManager.connectionInfo.ipAddress
            val ipAddress = String.format(
                "%d.%d.%d.%d",
                ip and 0xff,
                ip shr 8 and 0xff,
                ip shr 16 and 0xff,
                ip shr 24 and 0xff
            )
            ipText?.text = "IP: $ipAddress"
        } catch (e: Exception) {
            ipText?.text = "IP: unavailable"
        }
    }

    private fun requestScreenCapture() {
        val projectionManager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(projectionManager.createScreenCaptureIntent(), SCREEN_CAPTURE_REQUEST)
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == SCREEN_CAPTURE_REQUEST && resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.resultCode = resultCode
            ScreenCaptureService.resultData = data
            startService(Intent(this, ScreenCaptureService::class.java))
            isStreaming = true
            updateUI()
        }
    }

    private fun stopStreaming() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        isStreaming = false
        updateUI()
    }

    private fun updateUI() {
        if (isStreaming) {
            statusText?.text = "Screen is shared"
            statusText?.setTextColor(android.graphics.Color.parseColor("#4ade80"))
            statusDot?.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#4ade80"))
            startButton?.visibility = View.GONE
            stopButton?.visibility = View.VISIBLE
        } else {
            statusText?.text = "Ready to Connect"
            statusText?.setTextColor(android.graphics.Color.parseColor("#FFFFFF"))
            statusDot?.backgroundTintList = android.content.res.ColorStateList.valueOf(android.graphics.Color.parseColor("#AAAAAA"))
            startButton?.visibility = View.VISIBLE
            stopButton?.visibility = View.GONE
        }
    }
}
