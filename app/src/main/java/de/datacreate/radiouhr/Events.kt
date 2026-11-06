package de.datacreate.radiouhr

import android.content.ContentUris
import android.content.Context
import android.content.SharedPreferences
import android.provider.CalendarContract
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Termine aus dem Android-Kalender des Geräts (Google, Samsung, Exchange … – alles, was dort synchronisiert ist). */
object Events {
    class Cal(val id: Long, val name: String, val account: String, val color: Int, val visible: Boolean, val sync: Boolean)
    class Ev(val sort: Long, val label: String)

    fun hidden(p: SharedPreferences): Set<String> = p.getStringSet("calHidden", emptySet()) ?: emptySet()

    fun calendars(ctx: Context): List<Cal> {
        val out = ArrayList<Cal>()
        val proj = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.VISIBLE,
            CalendarContract.Calendars.SYNC_EVENTS
        )
        ctx.contentResolver.query(CalendarContract.Calendars.CONTENT_URI, proj, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                out.add(Cal(c.getLong(0), c.getString(1) ?: "Kalender", c.getString(2) ?: "", c.getInt(3),
                    c.getInt(4) == 1, c.getInt(5) == 1))
            }
        }
        return out.sortedWith(compareBy({ it.account }, { it.name }))
    }

    /** Termine ab jetzt bis Ende des gewählten Zeitraums (1 = nur heute). */
    fun query(ctx: Context, p: SharedPreferences): List<Ev> {
        val dayMs = 24 * 60 * 60_000L
        val days = p.getInt("evDays", 1).coerceIn(1, 14)
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(y, m, d, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val endCal = Calendar.getInstance().apply { timeInMillis = start; add(Calendar.DAY_OF_MONTH, days) }
        val end = endCal.timeInMillis
        val now = System.currentTimeMillis()
        val hide = hidden(p)
        val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
        val dayFmt = SimpleDateFormat("EEE", Locale.GERMANY)

        fun dayIndex(localMs: Long): Int {
            val c = Calendar.getInstance().apply { timeInMillis = localMs }
            c.set(Calendar.HOUR_OF_DAY, 0); c.set(Calendar.MINUTE, 0); c.set(Calendar.SECOND, 0); c.set(Calendar.MILLISECOND, 0)
            return Math.round((c.timeInMillis - start) / dayMs.toDouble()).toInt()
        }
        fun prefix(idx: Int, ms: Long): String = when (idx) {
            0 -> ""
            1 -> "Morgen "
            else -> dayFmt.format(Date(ms)).trimEnd('.') + " "
        }

        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, start - dayMs)
        ContentUris.appendId(b, end + dayMs)
        val proj = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.CALENDAR_ID
        )
        val found = ArrayList<Ev>()
        ctx.contentResolver.query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext()) {
                if (c.getLong(4).toString() in hide) continue
                val title = c.getString(0)?.trim().takeUnless { it.isNullOrEmpty() } ?: "(ohne Titel)"
                val begin = c.getLong(1)
                val stop = c.getLong(2)
                if (c.getInt(3) == 1) {
                    // ganztägig: in UTC gespeichert → für jeden Tag des Zeitraums prüfen
                    for (i in 0 until days) {
                        val utc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                            clear()
                            set(y, m, d)
                            add(Calendar.DAY_OF_MONTH, i)
                        }.timeInMillis
                        if (utc >= begin && utc < stop) {
                            found.add(Ev(start + i * dayMs - 1, prefix(i, start + i * dayMs + 12 * 3600_000L) + "ganztägig · $title"))
                            break
                        }
                    }
                } else if (begin < end && stop > now) {
                    val idx = dayIndex(begin).coerceAtLeast(0)
                    val label = if (begin < now) "jetzt" else prefix(idx, begin) + timeFmt.format(Date(begin))
                    found.add(Ev(begin, "$label  $title"))
                }
            }
        }
        return found.sortedBy { it.sort }.distinctBy { it.label }
    }
}
