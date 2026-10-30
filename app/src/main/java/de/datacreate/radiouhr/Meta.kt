package de.datacreate.radiouhr

import android.content.SharedPreferences
import org.json.JSONObject
import java.util.Locale

/**
 * DC Wetter-Meta: holt das Paket aller Wetterdienste vom eigenen WordPress-Plugin
 * (/wp-json/dcwm/v1/forecast) und bildet – wie das Plugin im Browser – einen Konsens (Median).
 */
object Meta {
    const val DEFAULT_BASE = "https://www.news-vodaheim.de"

    val NAMES = linkedMapOf(
        "icon" to "DWD ICON", "ecmwf" to "ECMWF", "gfs" to "NOAA GFS", "mf" to "Météo-France",
        "ukmo" to "UK Met Office", "gem" to "GEM Kanada", "met" to "MET Norway", "bs" to "DWD MOSMIX"
    )

    private val PRIO = mapOf(
        "thunder" to 8, "snow" to 7, "rain" to 6, "showers" to 5, "fog" to 4, "cloudy" to 3, "partly" to 2, "clear" to 1
    )

    fun icon(cat: String?, night: Boolean = false): String = when (cat) {
        "thunder" -> "⚡"
        "snow" -> "❄"
        "rain", "showers" -> "☂"
        "fog" -> "≋"
        "cloudy" -> "☁"
        "partly" -> if (night) "☁" else "⛅"
        "clear" -> if (night) "☾" else "☀"
        else -> "·"
    }

    fun text(cat: String?): String = when (cat) {
        "thunder" -> "Gewitter"
        "snow" -> "Schnee"
        "rain" -> "Regen"
        "showers" -> "Schauer"
        "fog" -> "Nebel"
        "cloudy" -> "bedeckt"
        "partly" -> "wolkig"
        "clear" -> "sonnig"
        else -> "–"
    }

    class Pt(
        val t: Long, val temp: Double, val precip: Double, val span: Int, val pop: Double,
        val cat: String?, val cloud: Double, val wind: Double, val gust: Double, val rh: Double
    )

    class Hour(
        val t: Long, val n: Int, val temp: Double, val tl: Double, val th: Double, val precip: Double, val pop: Double,
        val wind: Double, val gust: Double, val cloud: Double, val rh: Double, val cat: String?, val by: Map<String, Pt>
    )

    class Day(
        val dayKey: Long, val n: Int, val tmax: Double, val tmin: Double, val psum: Double, val wind: Double,
        val gust: Double, val pop: Double, val spread: Double, val smin: Double, val smax: Double,
        val pmin: Double, val pmax: Double, val cat: String?, val per: Map<String, SrcDay>
    )

    class SrcDay(val cover: Int, val tmax: Double, val tmin: Double, val psum: Double, val wind: Double, val gust: Double, val pop: Double, val cat: String?)

    class Bundle(val offset: Long, val sources: Map<String, List<Pt>>, val status: Map<String, String>, val generated: Long, val stale: Boolean)

    // ------------------------------------------------------------ Laden

    fun base(p: SharedPreferences): String =
        (p.getString("metaBase", DEFAULT_BASE) ?: DEFAULT_BASE).trim().trimEnd('/').ifEmpty { DEFAULT_BASE }

    fun url(p: SharedPreferences, lat: Double, lon: Double): String =
        String.format(Locale.US, "%s/wp-json/dcwm/v1/forecast?lat=%.2f&lon=%.2f", base(p), lat, lon)

    /** Lädt das Paket und legt es im Zwischenspeicher ab. */
    fun fetch(p: SharedPreferences, lat: Double, lon: Double): Bundle? {
        val json = Net.get(url(p, lat, lon))
        val b = parse(json) ?: return null
        p.edit().putString("metaJson", json).putLong("metaTime", System.currentTimeMillis()).apply()
        return b
    }

    fun cached(p: SharedPreferences): Bundle? = try {
        p.getString("metaJson", null)?.let { parse(it) }
    } catch (_: Exception) { null }

    private fun num(a: org.json.JSONArray, i: Int): Double =
        if (a.isNull(i)) Double.NaN else a.optDouble(i, Double.NaN)

    private fun cloudCat(c: Double): String? =
        if (c.isNaN()) null else if (c < 25) "clear" else if (c < 75) "partly" else "cloudy"

    fun parse(json: String): Bundle? {
        val j = JSONObject(json)
        val srcs = j.optJSONObject("sources") ?: return null
        val map = LinkedHashMap<String, List<Pt>>()
        val status = LinkedHashMap<String, String>()
        val keys = srcs.keys()
        while (keys.hasNext()) {
            val id = keys.next()
            val s = srcs.optJSONObject(id) ?: continue
            status[id] = if (s.optString("status") == "ok") "ok" else (s.optString("msg").ifEmpty { "Fehler" })
            if (s.optString("status") != "ok") continue
            val data = s.optJSONArray("data") ?: continue
            val pts = ArrayList<Pt>(data.length())
            for (i in 0 until data.length()) {
                val a = data.optJSONArray(i) ?: continue
                val cloud = num(a, 6)
                val cat = if (a.isNull(5)) null else a.optString(5).ifEmpty { null }
                pts.add(Pt(a.optLong(0), num(a, 1), num(a, 2), a.optInt(3, 1), num(a, 4), cat ?: cloudCat(cloud),
                    cloud, num(a, 7), num(a, 8), num(a, 9)))
            }
            if (pts.isNotEmpty()) map[id] = pts
        }
        if (map.isEmpty()) return null
        return Bundle(j.optLong("offset", 3600L), map, status, j.optLong("generated", 0L), j.optBoolean("stale", false))
    }

    // ------------------------------------------------------------ Statistik

    private fun fin(v: Double) = !v.isNaN() && !v.isInfinite()

    fun med(list: List<Double>): Double {
        val a = list.filter { fin(it) }.sorted()
        if (a.isEmpty()) return Double.NaN
        val m = a.size / 2
        return if (a.size % 2 == 1) a[m] else (a[m - 1] + a[m]) / 2
    }

    private fun mn(list: List<Double>) = list.filter { fin(it) }.minOrNull() ?: Double.NaN
    private fun mx(list: List<Double>) = list.filter { fin(it) }.maxOrNull() ?: Double.NaN

    fun modeCat(cats: List<String?>): String? {
        val n = HashMap<String, Int>()
        for (c in cats) if (c != null && PRIO.containsKey(c)) n[c] = (n[c] ?: 0) + 1
        var best: String? = null
        for ((c, k) in n) {
            val b = best
            if (b == null || k > n[b]!! || (k == n[b]!! && PRIO[c]!! > PRIO[b]!!)) best = c
        }
        return best
    }

    /** Spanne ohne die extremsten Werte (ab 5 Quellen). */
    private fun trim(list: List<Double>): Pair<Double, Double> {
        var a = list.filter { fin(it) }.sorted()
        if (a.size >= 5) a = a.subList(1, a.size - 1)
        return if (a.isEmpty()) Double.NaN to Double.NaN else a.first() to a.last()
    }

    fun confidence(spread: Double, n: Int): String? =
        if (n < 2 || spread.isNaN()) null else if (spread <= 2) "hoch" else if (spread <= 4.5) "mittel" else "gering"

    /** Lokaler Tag / lokale Stunde (Zeitzone vom Wetterpaket). */
    private fun fdiv(a: Long, b: Long): Long {
        val q = a / b
        return if ((a % b != 0L) && ((a < 0) != (b < 0))) q - 1 else q
    }

    fun dayKey(b: Bundle, t: Long): Long = fdiv(t + b.offset, 86400L)
    fun localHour(b: Bundle, t: Long): Int = ((t + b.offset - fdiv(t + b.offset, 86400L) * 86400L) / 3600L).toInt()

    fun hours(b: Bundle): List<Hour> {
        val map = java.util.TreeMap<Long, HashMap<String, Pt>>()
        for ((id, pts) in b.sources) for (p in pts) {
            val k = (p.t / 3600L) * 3600L
            map.getOrPut(k) { HashMap() }[id] = p
        }
        return map.map { (t, by) ->
            val ps = by.values.toList()
            val p1 = ps.filter { it.span == 1 }
            val temps = ps.map { it.temp }
            val tr = trim(temps)
            Hour(t, ps.size, med(temps), tr.first, tr.second, med(p1.map { it.precip }), med(ps.map { it.pop }),
                med(ps.map { it.wind }), med(ps.map { it.gust }), med(ps.map { it.cloud }), med(ps.map { it.rh }),
                modeCat(ps.map { it.cat }), by)
        }
    }

    /** Aktuelle Stunde (Konsens). */
    fun now(b: Bundle): Hour? {
        val now = System.currentTimeMillis() / 1000L
        val hs = hours(b)
        return hs.lastOrNull { it.t <= now && it.n >= maxOf(1, b.sources.size / 2) } ?: hs.firstOrNull()
    }

    private fun sourceDay(b: Bundle, points: List<Pt>): SrcDay {
        val cover = points.sumOf { it.span }
        val pr = points.map { it.precip }.filter { fin(it) }
        val psum = if (pr.isEmpty()) Double.NaN else pr.sum()
        fun cnt(c: String) = points.filter { it.cat == c }.sumOf { it.span }
        val cat = when {
            cnt("thunder") > 0 -> "thunder"
            fin(psum) && psum >= 1 -> if (cnt("snow") > cnt("rain") + cnt("showers")) "snow" else if (cnt("showers") > cnt("rain")) "showers" else "rain"
            cnt("fog") >= 8 -> "fog"
            else -> {
                val dp = points.filter { val h = localHour(b, it.t); h in 8..18 }
                cloudCat(med((if (dp.isNotEmpty()) dp else points).map { it.cloud })) ?: modeCat(points.map { it.cat })
            }
        }
        return SrcDay(cover, mx(points.map { it.temp }), mn(points.map { it.temp }), psum,
            mx(points.map { it.wind }), mx(points.map { it.gust }), mx(points.map { it.pop }), cat)
    }

    fun days(b: Bundle, count: Int): List<Day> {
        val today = dayKey(b, System.currentTimeMillis() / 1000L)
        val out = ArrayList<Day>()
        for (i in 0 until count) {
            val dk = today + i
            val per = LinkedHashMap<String, SrcDay>()
            for ((id, pts) in b.sources) {
                val dayPts = pts.filter { dayKey(b, it.t) == dk }
                if (dayPts.isEmpty()) continue
                val sd = sourceDay(b, dayPts)
                if (i > 0 && sd.cover < 18) continue
                per[id] = sd
            }
            if (per.isEmpty()) continue
            val v = per.values.toList()
            val maxs = v.map { it.tmax }
            val tr = trim(maxs)
            out.add(Day(dk, v.size, med(maxs), med(v.map { it.tmin }), med(v.map { it.psum }), med(v.map { it.wind }),
                med(v.map { it.gust }), med(v.map { it.pop }), if (v.size > 1) tr.second - tr.first else Double.NaN,
                mn(maxs), mx(maxs), mn(v.map { it.psum }), mx(v.map { it.psum }), modeCat(v.map { it.cat }), per))
        }
        return out
    }
}
