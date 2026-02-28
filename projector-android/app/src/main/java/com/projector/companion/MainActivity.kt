package com.projector.companion

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import android.view.Gravity
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity

class MainActivity : AppCompatActivity() {

    companion object {
        private const val REQUEST_MEDIA_PROJECTION = 1001
    }

    private var isSharing = false
    private lateinit var shareButton: Button
    private lateinit var statusText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(32), dp(80), dp(32), dp(32))
            setBackgroundColor(Color.parseColor("#121212"))
        }

        // App title
        val title = TextView(this).apply {
            text = "Projector"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 36f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(8))
        }
        layout.addView(title)

        // Subtitle
        val subtitle = TextView(this).apply {
            text = "Mirror your phone screen to any PC on the same Wi-Fi"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#AAAAAA"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(40))
        }
        layout.addView(subtitle)

        // Status indicator
        statusText = TextView(this).apply {
            text = "Status: Ready"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            setTextColor(Color.parseColor("#888888"))
            gravity = Gravity.CENTER
            setPadding(0, 0, 0, dp(32))
        }
        layout.addView(statusText)

        // Share button
        shareButton = Button(this).apply {
            text = "Start Sharing"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
            setTextColor(Color.WHITE)
            setTypeface(null, Typeface.BOLD)
            isAllCaps = false
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0078D4"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(56)
            )
            params.bottomMargin = dp(16)
            layoutParams = params
        }
        shareButton.setOnClickListener {
            if (!isSharing) {
                val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                startActivityForResult(projectionManager.createScreenCaptureIntent(), REQUEST_MEDIA_PROJECTION)
            } else {
                stopSharing()
            }
        }
        layout.addView(shareButton)

        // Accessibility button
        val accessibilityButton = Button(this).apply {
            text = "Enable Accessibility (for Touch)"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setTextColor(Color.parseColor("#CCCCCC"))
            isAllCaps = false
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#2D2D2D"))
                cornerRadius = dp(12).toFloat()
            }
            background = bg
            val params = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(48)
            )
            layoutParams = params
        }
        accessibilityButton.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            Toast.makeText(this, "Enable 'Projector' in the Accessibility list", Toast.LENGTH_LONG).show()
        }
        layout.addView(accessibilityButton)

        // Instructions at bottom
        val instructions = TextView(this).apply {
            text = "\n\nHow to use:\n1. Tap 'Enable Accessibility' above\n2. Tap 'Start Sharing'\n3. Open ADB-Projector.exe on your PC\n4. Your phone appears automatically!"
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(Color.parseColor("#666666"))
            setPadding(0, dp(24), 0, 0)
        }
        layout.addView(instructions)

        setContentView(layout)

        // Check for updates on launch
        UpdateChecker(this).checkInBackground()
    }

    private fun dp(value: Int): Int {
        return TypedValue.applyDimension(
            TypedValue.COMPLEX_UNIT_DIP,
            value.toFloat(),
            resources.displayMetrics
        ).toInt()
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_MEDIA_PROJECTION && resultCode == Activity.RESULT_OK && data != null) {
            ScreenCaptureService.resultCode = resultCode
            ScreenCaptureService.resultData = data

            val captureIntent = Intent(this, ScreenCaptureService::class.java)
            startForegroundService(captureIntent)

            val discoveryIntent = Intent(this, DiscoveryService::class.java)
            startService(discoveryIntent)

            isSharing = true
            shareButton.text = "Stop Sharing"
            shareButton.let {
                val bg = GradientDrawable().apply {
                    setColor(Color.parseColor("#D42020"))
                    cornerRadius = dp(12).toFloat()
                }
                it.background = bg
            }
            statusText.text = "Status: Sharing Active"
            statusText.setTextColor(Color.parseColor("#4CAF50"))

            Toast.makeText(this, "Sharing started! Open Projector on your PC.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopSharing() {
        stopService(Intent(this, ScreenCaptureService::class.java))
        stopService(Intent(this, DiscoveryService::class.java))
        isSharing = false
        shareButton.text = "Start Sharing"
        shareButton.let {
            val bg = GradientDrawable().apply {
                setColor(Color.parseColor("#0078D4"))
                cornerRadius = dp(12).toFloat()
            }
            it.background = bg
        }
        statusText.text = "Status: Ready"
        statusText.setTextColor(Color.parseColor("#888888"))
    }
}
