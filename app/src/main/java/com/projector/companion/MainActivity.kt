package com.projector.companion

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.wifi.WifiManager
import android.os.Bundle
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
    private var isStreaming = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        statusText = findViewById(R.id.statusText)
        ipText = findViewById(R.id.ipText)
        startButton = findViewById(R.id.startButton)
        stopButton = findViewById(R.id.stopButton)
        statusDot = findViewById(R.id.statusDot)

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
