package de.datacreate.radiouhr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.content.FileProvider
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** Update direkt aus der App: Version prüfen, APK laden, Installation starten. */
object Updater {
    private const val BASE = "https://github.com/Gauco-12345/dc-radiouhr/releases/download/aktuell/"

    fun installedCode(ctx: Context): Long = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode else @Suppress("DEPRECATION") pi.versionCode.toLong()
    } catch (_: Exception) { 0L }

    fun installedName(ctx: Context): String = try {
        ctx.packageManager.getPackageInfo(ctx.packageName, 0).versionName ?: "?"
    } catch (_: Exception) { "?" }

    /** (versionCode, versionName) der neuesten Version oder null. */
    fun latest(): Pair<Long, String>? {
        val t = Net.get(BASE + "version.txt").trim()
        val parts = t.split(Regex("\\s+"), 2)
        val code = parts.getOrNull(0)?.toLongOrNull() ?: return null
        return code to (parts.getOrNull(1) ?: code.toString())
    }

    /** Lädt die APK (mit Fortschritt 0..100) in den Zwischenspeicher. */
    fun download(ctx: Context, progress: (Int) -> Unit): File {
        val dir = File(ctx.cacheDir, "updates").apply { mkdirs() }
        val f = File(dir, "DC-Radiouhr.apk")
        var u = URL(BASE + "DC-Radiouhr.apk")
        var c: HttpURLConnection
        var hops = 0
        while (true) {
            c = u.openConnection() as HttpURLConnection
            c.instanceFollowRedirects = false
            c.connectTimeout = 15000
            c.readTimeout = 30000
            val code = c.responseCode
            if (code in 300..399 && hops < 6) {
                u = URL(u, c.getHeaderField("Location"))
                c.disconnect()
                hops++
                continue
            }
            if (code !in 200..299) throw IllegalStateException("HTTP $code")
            break
        }
        val total = c.contentLength.toLong()
        c.inputStream.use { input ->
            f.outputStream().use { out ->
                val buf = ByteArray(32 * 1024)
                var done = 0L
                var last = -1
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    done += n
                    if (total > 0) {
                        val pct = (done * 100 / total).toInt()
                        if (pct != last) { last = pct; progress(pct) }
                    }
                }
            }
        }
        c.disconnect()
        return f
    }

    fun install(a: Activity, f: File) {
        val uri = FileProvider.getUriForFile(a, "de.datacreate.radiouhr.files", f)
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        a.startActivity(i)
    }

    fun canInstall(a: Activity): Boolean =
        Build.VERSION.SDK_INT < 26 || a.packageManager.canRequestPackageInstalls()

    fun openInstallPermission(a: Activity) {
        if (Build.VERSION.SDK_INT >= 26) a.startActivity(
            Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, android.net.Uri.parse("package:" + a.packageName))
        )
    }
}
