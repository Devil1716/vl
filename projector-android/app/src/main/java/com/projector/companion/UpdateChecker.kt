package com.projector.companion

import android.app.DownloadManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AlertDialog
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class UpdateChecker(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "Devil1716/vl"
        private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
    }

    fun checkInBackground() {
        Thread {
            try {
                val url = URL(RELEASES_URL)
                val conn = url.openConnection() as HttpURLConnection
                conn.setRequestProperty("Accept", "application/vnd.github+json")
                conn.connectTimeout = 5000
                conn.readTimeout = 5000

                if (conn.responseCode == 200) {
                    val json = JSONObject(conn.inputStream.bufferedReader().readText())
                    val latestTag = json.getString("tag_name").removePrefix("v")
                    val currentVersion = context.packageManager
                        .getPackageInfo(context.packageName, 0).versionName

                    if (isNewer(latestTag, currentVersion ?: "0.0.0")) {
                        // Find APK download URL
                        val assets = json.getJSONArray("assets")
                        var apkUrl: String? = null
                        for (i in 0 until assets.length()) {
                            val asset = assets.getJSONObject(i)
                            if (asset.getString("name").endsWith(".apk")) {
                                apkUrl = asset.getString("browser_download_url")
                                break
                            }
                        }

                        if (apkUrl != null) {
                            val finalApkUrl = apkUrl
                            Handler(Looper.getMainLooper()).post {
                                showUpdateDialog(latestTag, finalApkUrl)
                            }
                        }
                    }
                }
                conn.disconnect()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }.start()
    }

    private fun isNewer(remote: String, local: String): Boolean {
        try {
            val remoteParts = remote.split(".").map { it.toIntOrNull() ?: 0 }
            val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
            for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
                val r = remoteParts.getOrElse(i) { 0 }
                val l = localParts.getOrElse(i) { 0 }
                if (r > l) return true
                if (r < l) return false
            }
        } catch (_: Exception) {}
        return false
    }

    private fun showUpdateDialog(version: String, apkUrl: String) {
        AlertDialog.Builder(context)
            .setTitle("Update Available")
            .setMessage("Version $version is available.\n\nWould you like to download and install it?")
            .setPositiveButton("Update") { _, _ ->
                downloadAndInstall(apkUrl, version)
            }
            .setNegativeButton("Later", null)
            .show()
    }

    private fun downloadAndInstall(apkUrl: String, version: String) {
        val request = DownloadManager.Request(Uri.parse(apkUrl))
            .setTitle("Projector Update v$version")
            .setDescription("Downloading update...")
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            .setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, "Projector-update.apk")
            .setMimeType("application/vnd.android.package-archive")

        val dm = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        dm.enqueue(request)

        AlertDialog.Builder(context)
            .setTitle("Downloading...")
            .setMessage("The update is downloading. Check your notifications.\n\nWhen done, tap the notification to install.")
            .setPositiveButton("OK", null)
            .show()
    }
}
