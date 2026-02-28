package com.projector.companion

import android.app.Service
import android.content.Intent
import android.net.wifi.WifiManager
import android.os.Build
import android.os.IBinder
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress

class DiscoveryService : Service() {

    private var running = false
    private var thread: Thread? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        running = true
        thread = Thread {
            val socket = DatagramSocket()
            socket.broadcast = true
            val broadcastAddress = InetAddress.getByName("255.255.255.255")

            while (running) {
                try {
                    val deviceName = Build.MODEL
                    val localIp = getLocalIpAddress()
                    val message = "PROJECTOR_BEACON|$deviceName|$localIp"
                    val data = message.toByteArray()
                    val packet = DatagramPacket(data, data.size, broadcastAddress, 9999)
                    socket.send(packet)
                    Thread.sleep(2000)
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
            socket.close()
        }
        thread?.start()
        return START_STICKY
    }

    private fun getLocalIpAddress(): String {
        val wifiManager = applicationContext.getSystemService(WIFI_SERVICE) as WifiManager
        val ip = wifiManager.connectionInfo.ipAddress
        return String.format(
            "%d.%d.%d.%d",
            ip and 0xff,
            ip shr 8 and 0xff,
            ip shr 16 and 0xff,
            ip shr 24 and 0xff
        )
    }

    override fun onDestroy() {
        running = false
        thread?.interrupt()
        super.onDestroy()
    }
}
