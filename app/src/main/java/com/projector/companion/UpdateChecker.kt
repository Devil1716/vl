package com.projector.companion

import android.app.AlertDialog
import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import androidx.core.content.FileProvider
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Utility class to check for updates on GitHub and install them.
 * Bypasses Android 14 scoped storage restrictions by using the internal cache.
 */
class UpdateChecker(private val context: Context) {

    companion object {
        private const val GITHUB_REPO = "Devil1716/vl"
        private const val RELEASES_URL = "https://api.github.com/repos/$GITHUB_REPO/releases/latest"
        private const val APK_FILE_NAME = "Projector-update.apk"
    }

    /**
     * Checks for a newer version in the background.
     */
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

                    // Compare remote tag with local version name
                    if (isNewer(latestTag, currentVersion ?: "0.0.0")) {
                        // Find the first APK asset in the release
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

    /**
     * SemVer comparison logic.
     */
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

    /**
     * Downloads the APK to the app's internal cache and triggers installation.
     */
    private fun downloadAndInstall(apkUrl: String, version: String) {
        val dialog = AlertDialog.Builder(context)
            .setTitle("Downloading Update...")
            .setMessage("Please wait while the update is downloading.")
            .setCancelable(false)
            .create()
        dialog.show()

        Thread {
            try {
                // Save to internal cache to avoid permission issues on Android 11+ (Scoped Storage)
                val apkFile = File(context.cacheDir, APK_FILE_NAME)
                if (apkFile.exists()) apkFile.delete()

                val url = URL(apkUrl)
                val conn = url.openConnection() as HttpURLConnection
                conn.connectTimeout = 10000
                conn.readTimeout = 10000
                conn.connect()

                if (conn.responseCode != HttpURLConnection.HTTP_OK) {
                    throw Exception("Server returned HTTP ${conn.responseCode}")
                }

                val input = conn.inputStream
                val output = apkFile.outputStream()
                val buffer = ByteArray(4096)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    output.write(buffer, 0, bytesRead)
                }
                output.close()
                input.close()
                conn.disconnect()

                Handler(Looper.getMainLooper()).post {
                    dialog.dismiss()
                    installApk(apkFile)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                Handler(Looper.getMainLooper()).post {
                    dialog.dismiss()
                    AlertDialog.Builder(context)
                        .setTitle("Download Failed")
                        .setMessage("Failed to download the update: ${e.message}\n\nPlease update manually via GitHub.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }.start()
    }

    /**
     * Uses FileProvider to securely share the APK URI with the system installer.
     */
    private fun installApk(apkFile: File) {
        try {
            val intent = Intent(Intent.ACTION_VIEW)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    apkFile
                )
                intent.setDataAndType(uri, "application/vnd.android.package-archive")
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } else {
                intent.setDataAndType(
                    Uri.fromFile(apkFile),
                    "application/vnd.android.package-archive"
                )
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
