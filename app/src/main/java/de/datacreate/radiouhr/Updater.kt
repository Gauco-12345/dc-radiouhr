package de.datacreate.radiouhr

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build

/** Update-Prüfung: Version vergleichen, Download im Browser öffnen. */
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

    const val APK_URL = BASE + "DC-Radiouhr.apk"

    /** Öffnet den Download im Browser – die App installiert nichts selbst. */
    fun openDownload(a: Activity) {
        a.startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse(APK_URL)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }
}
