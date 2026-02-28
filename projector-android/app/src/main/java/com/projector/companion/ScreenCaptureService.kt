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
import java.io.OutputStream
import java.net.ServerSocket
import java.net.Socket

class ScreenCaptureService : Service() {

    companion object {
        const val CHANNEL_ID = "projector_channel"
        const val NOTIFICATION_ID = 1
        var resultCode: Int = 0
        var resultData: Intent? = null
    }

    private var mediaProjection: MediaProjection? = null
    private var virtualDisplay: VirtualDisplay? = null
    private var mediaCodec: MediaCodec? = null
    private var serverSocket: ServerSocket? = null
    private var running = false

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

    private fun startStreaming() {
        val projectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        mediaProjection = projectionManager.getMediaProjection(resultCode, resultData!!)

        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val metrics = DisplayMetrics()
        wm.defaultDisplay.getRealMetrics(metrics)

        val width = 720
        val height = (metrics.heightPixels.toFloat() / metrics.widthPixels * width).toInt()
        val dpi = metrics.densityDpi

        // H.264 Encoder
        val format = MediaFormat.createVideoFormat(MediaFormat.MIMETYPE_VIDEO_AVC, width, height).apply {
            setInteger(MediaFormat.KEY_BIT_RATE, 4_000_000)
            setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 2)
            setInteger(MediaFormat.KEY_COLOR_FORMAT, MediaCodecInfo.CodecCapabilities.COLOR_FormatSurface)
        }

        mediaCodec = MediaCodec.createEncoderByType(MediaFormat.MIMETYPE_VIDEO_AVC)
        mediaCodec!!.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)

        val inputSurface: Surface = mediaCodec!!.createInputSurface()
        mediaCodec!!.start()

        virtualDisplay = mediaProjection?.createVirtualDisplay(
            "ProjectorDisplay",
            width, height, dpi,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            inputSurface, null, null
        )

        // TCP Server for video
        serverSocket = ServerSocket(8080)
        while (running) {
            try {
                val client: Socket = serverSocket!!.accept()
                Thread { streamToClient(client.getOutputStream()) }.start()
            } catch (e: Exception) {
                if (running) e.printStackTrace()
            }
        }
    }

    private fun streamToClient(output: OutputStream) {
        val bufferInfo = MediaCodec.BufferInfo()
        try {
            while (running) {
                val index = mediaCodec!!.dequeueOutputBuffer(bufferInfo, 10000)
                if (index >= 0) {
                    val buffer = mediaCodec!!.getOutputBuffer(index) ?: continue
                    val data = ByteArray(bufferInfo.size)
                    buffer.get(data)

                    // Write frame size (4 bytes big-endian) then frame data
                    val sizeBytes = byteArrayOf(
                        (data.size shr 24 and 0xFF).toByte(),
                        (data.size shr 16 and 0xFF).toByte(),
                        (data.size shr 8 and 0xFF).toByte(),
                        (data.size and 0xFF).toByte()
                    )
                    output.write(sizeBytes)
                    output.write(data)
                    output.flush()

                    mediaCodec!!.releaseOutputBuffer(index, false)
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
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
