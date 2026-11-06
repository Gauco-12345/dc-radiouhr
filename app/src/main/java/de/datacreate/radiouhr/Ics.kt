package de.datacreate.radiouhr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.util.Calendar
import java.util.TimeZone

/**
 * Kalender-Abos über iCal-Links (z. B. Googles „Privatadresse im iCal-Format“).
 * Funktioniert ohne Google-Konto auf dem Gerät – ideal für Fire-Tablets.
 */
object Ics {
    class Sub(val name: String, val url: String)
    class Occ(val begin: Long, val end: Long, val allDay: Boolean, val title: String, val dayKey: Int)

    fun subs(p: SharedPreferences): MutableList<Sub> = try {
        val a = JSONArray(p.getString("icsUrls", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> Sub(o.optString("name"), o.optString("url")) } }.toMutableList()
    } catch (_: Exception) { mutableListOf() }

    fun save(p: SharedPreferences, list: List<Sub>) {
        val a = JSONArray()
        for (s in list) a.put(JSONObject().put("name", s.name).put("url", s.url))
        p.edit().putString("icsUrls", a.toString()).apply()
    }

    fun normalize(url: String): String {
        val u = url.trim()
        return if (u.startsWith("webcal://", true)) "https://" + u.substring(9) else u
    }

    private fun cacheFile(ctx: Context, url: String) = File(ctx.cacheDir, "ics_" + Integer.toHexString(url.hashCode()))

    /** Text des Abos – aus dem Zwischenspeicher, alle 30 Minuten neu geladen. */
    fun text(ctx: Context, url: String, force: Boolean = false): String? {
        val f = cacheFile(ctx, url)
        if (!force && f.exists() && System.currentTimeMillis() - f.lastModified() < 30 * 60_000L) return f.readText()
        return try {
            val t = Net.get(normalize(url))
            if (!t.contains("BEGIN:VCALENDAR")) throw IllegalArgumentException("Kein iCal-Kalender")
            f.writeText(t)
            t
        } catch (e: Exception) {
            if (f.exists()) f.readText() else if (force) throw e else null
        }
    }

    // ------------------------------------------------------------ Parser

    private class Prop(val name: String, val params: Map<String, String>, val value: String)

    private fun unfold(text: String): List<String> {
        val out = ArrayList<String>()
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            if ((raw.startsWith(" ") || raw.startsWith("\t")) && out.isNotEmpty()) out[out.size - 1] = out.last() + raw.substring(1)
            else out.add(raw)
        }
        return out
    }

    private fun prop(line: String): Prop? {
        val colon = line.indexOf(':')
        if (colon <= 0) return null
        val head = line.substring(0, colon)
        val parts = head.split(';')
        val params = HashMap<String, String>()
        for (pp in parts.drop(1)) {
            val eq = pp.indexOf('=')
            if (eq > 0) params[pp.substring(0, eq).uppercase()] = pp.substring(eq + 1).trim('"')
        }
        return Prop(parts[0].uppercase(), params, line.substring(colon + 1))
    }

    private fun unescape(s: String) = s.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",").replace("\\;", ";").replace("\\\\", "\\")

    /** Datum/Zeit → (Millisekunden, ganztägig). Ganztägig wird als lokale Mitternacht geliefert. */
    private fun time(p: Prop): Pair<Long, Boolean>? {
        val v = p.value.trim()
        return try {
            if (p.params["VALUE"] == "DATE" || (v.length == 8 && !v.contains('T'))) {
                val c = Calendar.getInstance()
                c.clear()
                c.set(v.substring(0, 4).toInt(), v.substring(4, 6).toInt() - 1, v.substring(6, 8).toInt())
                c.timeInMillis to true
            } else {
                val tz = when {
                    v.endsWith("Z") -> TimeZone.getTimeZone("UTC")
                    p.params["TZID"] != null -> TimeZone.getTimeZone(p.params["TZID"])
                    else -> TimeZone.getDefault()
                }
                val c = Calendar.getInstance(tz)
                c.clear()
                c.set(v.substring(0, 4).toInt(), v.substring(4, 6).toInt() - 1, v.substring(6, 8).toInt(),
                    v.substring(9, 11).toInt(), v.substring(11, 13).toInt(), if (v.length >= 15) v.substring(13, 15).toInt() else 0)
                c.timeInMillis to false
            }
        } catch (_: Exception) { null }
    }

    private fun duration(s: String): Long {
        // P1D, PT1H30M, P1W …
        var ms = 0L
        val m = Regex("(\\d+)([WDHMS])").findAll(s.substringAfter('P'))
        var inTime = false
        val t = s.indexOf('T')
        for (x in m) {
            val n = x.groupValues[1].toLong()
            inTime = t >= 0 && x.range.first + 1 > t
            ms += when (x.groupValues[2]) {
                "W" -> n * 7 * 86400000L
                "D" -> n * 86400000L
                "H" -> n * 3600000L
                "M" -> if (inTime) n * 60000L else n * 30 * 86400000L
                else -> n * 1000L
            }
        }
        return ms
    }

    private val WD = mapOf("MO" to Calendar.MONDAY, "TU" to Calendar.TUESDAY, "WE" to Calendar.WEDNESDAY,
        "TH" to Calendar.THURSDAY, "FR" to Calendar.FRIDAY, "SA" to Calendar.SATURDAY, "SU" to Calendar.SUNDAY)

    /** Alle Termine des Abos, die in [from, to) liegen (auch Serientermine). */
    fun occurrences(text: String, from: Long, to: Long): List<Occ> {
        val out = ArrayList<Occ>()
        val overrides = HashSet<String>()   // UID|Startzeit verschobener Einzeltermine
        val events = ArrayList<Map<String, List<Prop>>>()
        var cur: HashMap<String, MutableList<Prop>>? = null
        var depth = 0
        for (line in unfold(text)) {
            when {
                line == "BEGIN:VEVENT" -> { cur = HashMap(); depth = 0 }
                line.startsWith("BEGIN:") && cur != null -> depth++
                line.startsWith("END:") && cur != null && line != "END:VEVENT" -> depth--
                line == "END:VEVENT" -> { cur?.let { events.add(it) }; cur = null }
                cur != null && depth == 0 -> prop(line)?.let { pr -> cur!!.getOrPut(pr.name) { ArrayList() }.add(pr) }
            }
        }
        for (e in events) {
            val rid = e["RECURRENCE-ID"]?.firstOrNull() ?: continue
            val uid = e["UID"]?.firstOrNull()?.value ?: continue
            time(rid)?.let { overrides.add("$uid|${it.first}") }
        }
        for (e in events) {
            if (e["STATUS"]?.firstOrNull()?.value?.uppercase() == "CANCELLED") continue
            val st = e["DTSTART"]?.firstOrNull()?.let { time(it) } ?: continue
            val title = unescape(e["SUMMARY"]?.firstOrNull()?.value ?: "(ohne Titel)")
            val start = st.first
            val allDay = st.second
            val endP = e["DTEND"]?.firstOrNull()?.let { time(it) }
            val len = when {
                endP != null -> (endP.first - start).coerceAtLeast(0L)
                e["DURATION"] != null -> duration(e["DURATION"]!!.first().value)
                allDay -> 86400000L
                else -> 0L
            }
            val uid = e["UID"]?.firstOrNull()?.value ?: ""
            val ex = HashSet<Long>()
            e["EXDATE"]?.forEach { x ->
                for (v in x.value.split(',')) time(Prop("EXDATE", x.params, v))?.let { ex.add(it.first) }
            }
            val rrule = e["RRULE"]?.firstOrNull()?.value
            val isOverride = e["RECURRENCE-ID"] != null
            fun add(b: Long) {
                if (b in ex) return
                if (!isOverride && rrule != null && overrides.contains("$uid|$b")) return
                val en = b + len
                if (b < to && (if (len > 0) en > from else b >= from)) out.add(Occ(b, en, allDay, title, 0))
            }
            if (rrule == null || isOverride) {
                add(start)
                continue
            }
            expand(rrule, start, e["DTSTART"]!!.first(), to, ::add)
        }
        return out
    }

    private fun expand(rule: String, start: Long, dtProp: Prop, to: Long, add: (Long) -> Unit) {
        val r = rule.split(';').mapNotNull { val i = it.indexOf('='); if (i > 0) it.substring(0, i).uppercase() to it.substring(i + 1) else null }.toMap()
        val freq = r["FREQ"] ?: return
        val interval = (r["INTERVAL"]?.toIntOrNull() ?: 1).coerceAtLeast(1)
        val count = r["COUNT"]?.toIntOrNull() ?: Int.MAX_VALUE
        val until = r["UNTIL"]?.let { time(Prop("UNTIL", emptyMap(), it))?.first } ?: Long.MAX_VALUE
        val tz = when {
            dtProp.value.endsWith("Z") -> TimeZone.getTimeZone("UTC")
            dtProp.params["TZID"] != null -> TimeZone.getTimeZone(dtProp.params["TZID"])
            else -> TimeZone.getDefault()
        }
        val byDay = r["BYDAY"]?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() } ?: emptyList()
        val byMonthDay = r["BYMONTHDAY"]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: emptyList()
        val base = Calendar.getInstance(tz).apply { timeInMillis = start }
        val h = base.get(Calendar.HOUR_OF_DAY)
        val mi = base.get(Calendar.MINUTE)
        var n = 0
        var guard = 0
        val c = Calendar.getInstance(tz).apply { timeInMillis = start }

        fun emit(t: Long): Boolean {
            if (t < start) return true
            if (t > until || n >= count || t >= to) return false
            n++
            add(t)
            return true
        }

        when (freq) {
            "DAILY" -> while (guard++ < 20000) {
                if (!emit(c.timeInMillis)) return
                c.add(Calendar.DAY_OF_MONTH, interval)
            }
            "WEEKLY" -> {
                val days = if (byDay.isEmpty()) listOf(base.get(Calendar.DAY_OF_WEEK)) else byDay.mapNotNull { WD[it.takeLast(2)] }
                val wk = Calendar.getInstance(tz).apply {
                    timeInMillis = start
                    firstDayOfWeek = Calendar.MONDAY
                    set(Calendar.DAY_OF_WEEK, Calendar.MONDAY)
                    if (timeInMillis > start) add(Calendar.WEEK_OF_YEAR, -1)
                }
                while (guard++ < 5000) {
                    val list = days.map { dw ->
                        Calendar.getInstance(tz).apply {
                            timeInMillis = wk.timeInMillis
                            val diff = ((dw - Calendar.MONDAY) + 7) % 7
                            add(Calendar.DAY_OF_MONTH, diff)
                            set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                        }.timeInMillis
                    }.sorted()
                    for (t in list) if (!emit(t)) return
                    wk.add(Calendar.WEEK_OF_YEAR, interval)
                }
            }
            "MONTHLY" -> {
                val m = Calendar.getInstance(tz).apply { timeInMillis = start; set(Calendar.DAY_OF_MONTH, 1) }
                while (guard++ < 2000) {
                    val cands = ArrayList<Long>()
                    if (byDay.isNotEmpty()) {
                        for (bd in byDay) {
                            val dw = WD[bd.takeLast(2)] ?: continue
                            val ord = bd.dropLast(2).toIntOrNull() ?: 0
                            val all = ArrayList<Long>()
                            val d = Calendar.getInstance(tz).apply { timeInMillis = m.timeInMillis }
                            val month = d.get(Calendar.MONTH)
                            while (d.get(Calendar.MONTH) == month) {
                                if (d.get(Calendar.DAY_OF_WEEK) == dw) all.add(Calendar.getInstance(tz).apply {
                                    timeInMillis = d.timeInMillis; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, mi)
                                    set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                                }.timeInMillis)
                                d.add(Calendar.DAY_OF_MONTH, 1)
                            }
                            when {
                                ord > 0 && ord <= all.size -> cands.add(all[ord - 1])
                                ord < 0 && -ord <= all.size -> cands.add(all[all.size + ord])
                                ord == 0 -> cands.addAll(all)
                            }
                        }
                    } else {
                        val days = if (byMonthDay.isEmpty()) listOf(base.get(Calendar.DAY_OF_MONTH)) else byMonthDay
                        val maxD = m.getActualMaximum(Calendar.DAY_OF_MONTH)
                        for (dd in days) {
                            val day = if (dd < 0) maxD + dd + 1 else dd
                            if (day in 1..maxD) cands.add(Calendar.getInstance(tz).apply {
                                timeInMillis = m.timeInMillis; set(Calendar.DAY_OF_MONTH, day); set(Calendar.HOUR_OF_DAY, h)
                                set(Calendar.MINUTE, mi); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
                            }.timeInMillis)
                        }
                    }
                    for (t in cands.sorted()) if (!emit(t)) return
                    m.add(Calendar.MONTH, interval)
                }
            }
            "YEARLY" -> while (guard++ < 300) {
                if (!emit(c.timeInMillis)) return
                c.add(Calendar.YEAR, interval)
            }
        }
    }
}
