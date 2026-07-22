package com.botcelular.mu

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File

/**
 * Chequea GitHub Releases (repo configurado en Config.GITHUB_REPO_*) y,
 * si hay una versión más nueva que la instalada, descarga el .apk
 * adjunto al release y dispara el instalador del sistema. No hay
 * distribución por Play Store, así que este es el mecanismo completo de
 * actualización de la app.
 */
object UpdateChecker {
    private const val TAG = "UpdateChecker"
    private val client = OkHttpClient()

    data class UpdateInfo(val versionName: String, val apkUrl: String)

    /** Devuelve info del último release si es más nuevo que [currentVersion], o null. */
    suspend fun checkForUpdate(currentVersion: String): UpdateInfo? = withContext(Dispatchers.IO) {
        try {
            val url = "https://api.github.com/repos/${Config.GITHUB_REPO_OWNER}/${Config.GITHUB_REPO_NAME}/releases/latest"
            val request = Request.Builder().url(url).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    Log.w(TAG, "GitHub API respondió ${response.code}")
                    return@withContext null
                }
                val body = response.body?.string() ?: return@withContext null
                val json = JSONObject(body)
                val tagName = json.getString("tag_name").removePrefix("v")
                if (!isNewer(tagName, currentVersion)) return@withContext null

                val assets = json.getJSONArray("assets")
                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk")) {
                        return@withContext UpdateInfo(tagName, asset.getString("browser_download_url"))
                    }
                }
                Log.w(TAG, "Release $tagName no tiene un .apk adjunto.")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error chequeando actualización", e)
            null
        }
    }

    /** Compara versiones tipo "1.2.3" — true si [remote] > [local]. */
    private fun isNewer(remote: String, local: String): Boolean {
        val r = remote.split(".").map { it.toIntOrNull() ?: 0 }
        val l = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(r.size, l.size)) {
            val rv = r.getOrElse(i) { 0 }
            val lv = l.getOrElse(i) { 0 }
            if (rv != lv) return rv > lv
        }
        return false
    }

    /** Descarga el APK vía DownloadManager y avisa por [onComplete] cuando termina. */
    fun downloadAndInstall(context: Context, info: UpdateInfo, onComplete: () -> Unit) {
        val downloadDir = File(context.getExternalFilesDir(null), "updates").apply { mkdirs() }
        val destFile = File(downloadDir, "botcelular-${info.versionName}.apk")

        val request = DownloadManager.Request(Uri.parse(info.apkUrl))
            .setTitle("Actualizando BotCelular a ${info.versionName}")
            .setDestinationUri(Uri.fromFile(destFile))
            .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)

        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        val downloadId = downloadManager.enqueue(request)

        val receiver = object : BroadcastReceiver() {
            override fun onReceive(ctx: Context, intent: Intent) {
                val id = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1)
                if (id != downloadId) return
                context.unregisterReceiver(this)
                installApk(context, destFile)
                onComplete()
            }
        }
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            context.registerReceiver(receiver, filter)
        }
    }

    private fun installApk(context: Context, apkFile: File) {
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
