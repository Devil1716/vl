package com.projector.companion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    private val SCREEN_CAPTURE_REQUEST = 1001
    private var statusText: TextView? = null
    private var ipText: TextView? = null
    private var startButton: Button? = null
    private var stopButton: Button? = null
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)

        updateIpAddress()

        startButton?.setOnClickListener {
            requestScreenCapture()
        }

        stopButton?.setOnClickListener {
            stopStreaming()
        }

        // Check for updates
        UpdateChecker(this).checkInBackground()

        // Start discovery service
        startService(Intent(this, DiscoveryService::class.java))
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
            ipText?.text = "Device IP: $ipAddress"
        } catch (e: Exception) {
            ipText?.text = "Device IP: unavailable"
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
            statusText?.text = "Status: Streaming"
            startButton?.isEnabled = false
            stopButton?.isEnabled = true
        } else {
            statusText?.text = "Status: Idle"
            startButton?.isEnabled = true
            stopButton?.isEnabled = false
        }
    }
}
