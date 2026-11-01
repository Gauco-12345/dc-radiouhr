package de.datacreate.radiouhr

import android.app.Activity
import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.res.Configuration
import android.os.Build
import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar

/** Basis für alle Seiten: skaliert die Oberfläche passend zur Bildschirmgröße (Tablet). */
open class ScaledActivity : Activity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        try {
            val p = newBase.getSharedPreferences("radiouhr", Context.MODE_PRIVATE)
            val base = newBase.resources.configuration
            val sw = base.smallestScreenWidthDp.takeIf { it > 0 } ?: 360
            val auto = (sw / 400f).coerceIn(1f, 2.2f)
            val zoom = p.getInt("uiZoom", 100) / 100f
            val f = auto * zoom
            if (f in 0.95f..1.05f) return
            val cfg = Configuration()
            cfg.densityDpi = (newBase.resources.displayMetrics.densityDpi * f).toInt()
            applyOverrideConfiguration(cfg)
        } catch (_: Exception) {
        }
    }
}

/** Wochentage als Bitmaske: Bit 0 = Montag … Bit 6 = Sonntag. */
object Days {
    val SHORT = arrayOf("Mo", "Di", "Mi", "Do", "Fr", "Sa", "So")
    const val ALL = 0x7F

    fun bit(cal: Calendar): Int = when (cal.get(Calendar.DAY_OF_WEEK)) {
        Calendar.MONDAY -> 0
        Calendar.TUESDAY -> 1
        Calendar.WEDNESDAY -> 2
        Calendar.THURSDAY -> 3
        Calendar.FRIDAY -> 4
        Calendar.SATURDAY -> 5
        else -> 6
    }

    fun label(mask: Int): String = when (mask and ALL) {
        ALL -> "täglich"
        0x1F -> "Mo–Fr"
        0x60 -> "Sa, So"
        0 -> "einmalig"
        else -> (0..6).filter { mask and (1 shl it) != 0 }.joinToString(", ") { SHORT[it] }
    }
}

fun hhmm(min: Int) = String.format("%02d:%02d", (min / 60) % 24, min % 60)

/** Nachtmodus-Zeiträume: mehrere, minutengenau, je mit Wochentagen. */
object NightZones {
    class Zone(var from: Int, var to: Int, var days: Int = Days.ALL)

    fun load(p: SharedPreferences): MutableList<Zone> {
        val raw = p.getString("nightZones", null)
        if (raw == null) {
            // Übernahme der alten Einstellung (nur Stunden)
            return mutableListOf(Zone(p.getInt("nightFrom", 22) * 60, p.getInt("nightTo", 7) * 60))
        }
        return try {
            val a = JSONArray(raw)
            (0 until a.length()).map {
                val o = a.getJSONObject(it)
                Zone(o.optInt("from", 1320), o.optInt("to", 420), o.optInt("days", Days.ALL))
            }.toMutableList()
        } catch (_: Exception) {
            mutableListOf()
        }
    }

    fun save(p: SharedPreferences, zones: List<Zone>) {
        val a = JSONArray()
        for (z in zones) a.put(JSONObject().put("from", z.from).put("to", z.to).put("days", z.days))
        p.edit().putString("nightZones", a.toString()).apply()
    }

    fun isNight(p: SharedPreferences, cal: Calendar = Calendar.getInstance()): Boolean {
        val m = cal.get(Calendar.HOUR_OF_DAY) * 60 + cal.get(Calendar.MINUTE)
        val today = Days.bit(cal)
        val yesterday = (today + 6) % 7
        for (z in load(p)) {
            if (z.from == z.to) continue
            if (z.from < z.to) {
                if (m >= z.from && m < z.to && z.days and (1 shl today) != 0) return true
            } else {
                if (m >= z.from && z.days and (1 shl today) != 0) return true
                if (m < z.to && z.days and (1 shl yesterday) != 0) return true
            }
        }
        return false
    }
}

/** Radiowecker. */
object Alarms {
    const val ACTION = "de.datacreate.radiouhr.ALARM"

    class Alarm(
        var id: Long, var min: Int, var days: Int, var station: String, var on: Boolean
    )

    fun load(p: SharedPreferences): MutableList<Alarm> = try {
        val a = JSONArray(p.getString("alarms", "[]"))
        (0 until a.length()).map {
            val o = a.getJSONObject(it)
            Alarm(o.optLong("id"), o.optInt("min", 420), o.optInt("days", 0x1F), o.optString("station"), o.optBoolean("on", true))
        }.toMutableList()
    } catch (_: Exception) {
        mutableListOf()
    }

    fun save(ctx: Context, p: SharedPreferences, list: List<Alarm>) {
        val a = JSONArray()
        for (x in list) a.put(JSONObject().put("id", x.id).put("min", x.min).put("days", x.days).put("station", x.station).put("on", x.on))
        p.edit().putString("alarms", a.toString()).apply()
        schedule(ctx)
    }

    /** Nächster Auslösezeitpunkt eines Weckers (ms) ab „from“. */
    fun nextTime(a: Alarm, from: Long = System.currentTimeMillis()): Long {
        val c = Calendar.getInstance()
        c.timeInMillis = from
        c.set(Calendar.SECOND, 0)
        c.set(Calendar.MILLISECOND, 0)
        c.set(Calendar.HOUR_OF_DAY, a.min / 60)
        c.set(Calendar.MINUTE, a.min % 60)
        if (c.timeInMillis <= from) c.add(Calendar.DAY_OF_MONTH, 1)
        if (a.days and Days.ALL == 0) return c.timeInMillis
        for (i in 0 until 8) {
            if (a.days and (1 shl Days.bit(c)) != 0) return c.timeInMillis
            c.add(Calendar.DAY_OF_MONTH, 1)
        }
        return c.timeInMillis
    }

    /** Nächster Wecker überhaupt (inkl. Schlummern): Zeit und Wecker-ID (-1 = Schlummern). */
    fun next(p: SharedPreferences): Pair<Long, Long>? {
        var best: Pair<Long, Long>? = null
        val snooze = p.getLong("snoozeAt", 0L)
        if (snooze > System.currentTimeMillis()) best = snooze to -1L
        for (a in load(p)) {
            if (!a.on) continue
            val t = nextTime(a)
            if (best == null || t < best.first) best = t to a.id
        }
        return best
    }

    private fun pending(ctx: Context): PendingIntent {
        val i = Intent(ctx, MainActivity::class.java).setAction(ACTION)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val flags = PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
        return PendingIntent.getActivity(ctx, 4711, i, flags)
    }

    fun schedule(ctx: Context) {
        val p = ctx.getSharedPreferences("radiouhr", Context.MODE_PRIVATE)
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val pi = pending(ctx)
        am.cancel(pi)
        val n = next(p) ?: return
        try {
            val show = PendingIntent.getActivity(
                ctx, 4712, Intent(ctx, MainActivity::class.java),
                PendingIntent.FLAG_UPDATE_CURRENT or (if (Build.VERSION.SDK_INT >= 23) PendingIntent.FLAG_IMMUTABLE else 0)
            )
            am.setAlarmClock(AlarmManager.AlarmClockInfo(n.first, show), pi)
        } catch (e: SecurityException) {
            am.set(AlarmManager.RTC_WAKEUP, n.first, pi)
        }
    }

    /** Welcher Wecker ist jetzt (diese Minute) fällig? null = keiner. */
    fun dueNow(p: SharedPreferences): Alarm? {
        val c = Calendar.getInstance()
        val m = c.get(Calendar.HOUR_OF_DAY) * 60 + c.get(Calendar.MINUTE)
        val bit = 1 shl Days.bit(c)
        return load(p).firstOrNull { it.on && it.min == m && (it.days and Days.ALL == 0 || it.days and bit != 0) }
    }
}

/** Nach Neustart den nächsten Wecker wieder planen. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Alarms.schedule(context)
    }
}
