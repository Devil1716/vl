package com.projector.companion

import android.app.*
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.*
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.util.DisplayMetrics
import android.view.Surface
import android.view.WindowManager
import androidx.core.app.NotificationCompat
import java.io.File
import java.io.FileWriter
import java.io.OutputStream
import java.io.PrintWriter
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket
import java.text.SimpleDateFormat
import java.util.Date

class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "projector_channel"
        const val NOTIFICATION_ID = 1
        const val TAG = "ScreenCapture"
        var resultCode: Int = 0
        var resultData: Intent? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var serverSocket: ServerSocket? = null
    private var running = false
    @Volatile private var codecConfigData: ByteArray? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Projector Active")
            .setContentText("Sharing screen to PC...")
            .setSmallIcon(android.R.drawable.ic_menu_view)
            .build()

        startForeground(NOTIFICATION_ID, notification)

        running = true
        Thread { startStreaming() }.start()

        return START_NOT_STICKY
    }

    private fun logToFile(msg: String, e: Throwable? = null) {
        android.util.Log.e(TAG, msg, e)
        try {
            val logFile = File(applicationContext.getExternalFilesDir(null), "projector_error.log")
            val fw = FileWriter(logFile, true)
            val pw = PrintWriter(fw)
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Date())
            pw.println("[$time] $msg")
            e?.printStackTrace(pw)
            pw.close()
        } catch (ex: Exception) {
            // Ignored
        }
    }

    private fun startStreaming() {
        try {
            val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            mediaProjection = projectionManager.getMediaProjection(resultCode, resultData!!)

            val wm = getSystemService(WINDOW_SERVICE) as WindowManager
            val metrics = DisplayMetrics()
            @Suppress("DEPRECATION")
            wm.defaultDisplay.getRealMetrics(metrics)

            val width = 720
            val height = (metrics.heightPixels.toFloat() / metrics.widthPixels * width).toInt()
            val dpi = metrics.densityDpi

            android.util.Log.d(TAG, "Starting encoder: ${width}x${height}, dpi=$dpi")

            // H.264 Encoder
            val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
                setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
                setInteger(MediaFormat.KEY_FRAME_RATE, 30)
                setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 1)
                setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
            }

            mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
            mediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

            val inputSurface: Surface = mediaCodec!!.createInputSurface()
            mediaCodec!!.start()
            android.util.Log.d(TAG, "Encoder started")

            virtualDisplay = mediaProjection?.createVirtualDisplay(
                "ProjectorDisplay",
                width, height, dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                inputSurface, null, null
            )
            android.util.Log.d(TAG, "VirtualDisplay created")

            // Drain initial codec config (SPS/PPS) before accepting clients
            drainCodecConfig()

            // TCP Server for video with port reuse
            serverSocket = ServerSocket()
            serverSocket!!.reuseAddress = true
            serverSocket!!.bind(InetSocketAddress(8080))
            android.util.Log.d(TAG, "Server listening on port 8080")
            logToFile("Server successfully started on port 8080")

            while (running) {
                try {
                    val client: Socket = serverSocket!!.accept()
                    android.util.Log.d(TAG, "Client connected: ${client.inetAddress}")
                    Thread { streamToClient(client.getOutputStream()) }.start()
                } catch (e: Exception) {
                    if (running) {
                        e.printStackTrace()
                        logToFile("Client accept error", e)
                    }
                }
            }
        } catch (e: Exception) {
            logToFile("Fatal error in startStreaming", e)
        }
    }

    private fun drainCodecConfig() {
        val bufferInfo = MediaCodec.BufferInfo()
        // Wait briefly for the encoder to produce codec config (SPS/PPS)
        val deadline = System.currentTimeMillis() + 2000
        while (System.currentTimeMillis() < deadline) {
            val index = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 100000)
            if (index >= 0) {
                if (bufferInfo.flags and MediaCodec.BUFFER_FLAG_CODEC_CONFIG != 0) {
                    val buffer = mediaCodec!!.getOutputBuffer(index)!!
                    buffer.position(bufferInfo.offset)
                    buffer.limit(bufferInfo.offset + bufferInfo.size)
                    val config = ByteArray(bufferInfo.size)
                    buffer.get(config)
                    codecConfigData = config
                    android.util.Log.d(TAG, "Cached codec config: ${config.size} bytes")
                }
                mediaCodec!!.releaseOutputBuffer(index, false)
                if (codecConfigData != null) break
            }
        }
    }

    private fun sendFrame(output: OutputStream, data: ByteArray) {
        output.write(data)
        output.flush()
    }

    private fun streamToClient(output: OutputStream) {
        val bufferInfo = MediaCodec.BufferInfo()
        var frameCount = 0
        try {
            // Send cached codec config (SPS/PPS) first so the decoder can initialize
            codecConfigData?.let {
                android.util.Log.d(TAG, "Sending cached codec config to client: ${it.size} bytes")
                sendFrame(output, it)
            }

            // Request a keyframe so the client can start decoding immediately
            val params = android.os.Bundle()
            params.putInt(MediaCodec.PARAMETER_KEY_REQUEST_SYNC_FRAME, 0)
            mediaCodec!!.setParameters(params)

            while (running) {
                val index = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10000)
                if (index >= 0) {
                    if (bufferInfo.size > 0) {
                        val buffer = mediaCodec!!.getOutputBuffer(index)!!
                        buffer.position(bufferInfo.offset)
                        buffer.limit(bufferInfo.offset + bufferInfo.size)
                        val data = ByteArray(bufferInfo.size)
                        buffer.get(data)

                        sendFrame(output, data)

                        frameCount++
                        if (frameCount <= 3 || frameCount % 30 == 0) {
                            android.util.Log.d(TAG, "Sent frame #$frameCount, size=${data.size}, flags=${bufferInfo.flags}")
                        }
                    }
                    mediaCodec!!.releaseOutputBuffer(index, false)
                }
            }
        } catch (e: Exception) {
            android.util.Log.e(TAG, "Stream to client ended after $frameCount frames: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, "Projector Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    override fun onDestroy() {
        running = false
        virtualDisplay?.release()
        mediaCodec?.stop()
        mediaCodec?.release()
        mediaProjection?.stop()
        serverSocket?.close()
        super.onDestroy()
    }
}
