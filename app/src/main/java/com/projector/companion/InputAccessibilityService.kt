package com.projector.companion

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.os.Build
import android.view.accessibility.AccessibilityEvent
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.InetSocketAddress
import java.net.ServerSocket

class InputAccessibilityService : AccessibilityService() {

    companion object {
        var instance: InputAccessibilityService? = null
        const val TAG = "InputService"
    }

    private var running = false
    private var serverThread: Thread? = null

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        running = true
        android.util.Log.d(TAG, "Accessibility Service connected")
        serverThread = Thread { startInputServer() }
        serverThread?.start()
    }

    private fun startInputServer() {
        try {
            val serverSocket = ServerSocket()
            serverSocket.reuseAddress = true
            serverSocket.bind(InetSocketAddress(8081))
            android.util.Log.d(TAG, "Input server listening on port 8081")
            while (running) {
                try {
                    val client = serverSocket.accept()
                    android.util.Log.d(TAG, "Input client connected: ${client.inetAddress}")
                    Thread {
                        val reader = BufferedReader(InputStreamReader(client.getInputStream()))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            try {
                                val json = JSONObject(line!!)
                                val type = json.getString("type")
                                when (type) {
                                    "tap" -> {
                                        val x = json.getDouble("x").toFloat()
                                        val y = json.getDouble("y").toFloat()
                                        performTap(x, y)
                                    }
                                    "swipe" -> {
                                        val x1 = json.getDouble("x1").toFloat()
                                        val y1 = json.getDouble("y1").toFloat()
                                        val x2 = json.getDouble("x2").toFloat()
                                        val y2 = json.getDouble("y2").toFloat()
                                        val duration = json.optLong("duration", 300)
                                        performSwipe(x1, y1, x2, y2, duration)
                                    }
                                    "back" -> {
                                        performGlobalAction(GLOBAL_ACTION_BACK)
                                    }
                                    "home" -> {
                                        performGlobalAction(GLOBAL_ACTION_HOME)
                                    }
                                    "recents" -> {
                                        performGlobalAction(GLOBAL_ACTION_RECENTS)
                                    }
                                    "notifications" -> {
                                        performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
                                    }
                                }
                            } catch (e: Exception) {
                                android.util.Log.e(TAG, "Error processing input: ${e.message}")
                            }
                        }
                    }.start()
                } catch (e: Exception) {
                    if (running) {
                        android.util.Log.e(TAG, "Client accept error: ${e.message}")
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Fatal error starting input server: ${e.message}")
        }
    }

    private fun performTap(x: Float, y: Float) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path()
        path.moveTo(x, y)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 100))
            .build()
        dispatchGesture(gesture, null, null)
    }

    private fun performSwipe(x1: Float, y1: Float, x2: Float, y2: Float, duration: Long) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.N) return
        val path = Path()
        path.moveTo(x1, y1)
        path.lineTo(x2, y2)
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, duration))
            .build()
        dispatchGesture(gesture, null, null)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        running = false
        instance = null
        super.onDestroy()
    }
}
