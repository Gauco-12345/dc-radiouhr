package de.datacreate.radiouhr

import android.content.SharedPreferences
import org.json.JSONObject
import java.util.Locale

/**
 * DC Wetter-Meta: holt das Paket aller Wetterdienste vom eigenen WordPress-Plugin
 * (/wp-json/dcwm/v1/forecast) und bildet – wie das Plugin im Browser – einen Konsens (Median).
 */
object Meta {
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

    // ------------------------------------------------------------ Laden (direkt bei den Wetterdiensten)

    private val MODELS = linkedMapOf(
        "icon" to "icon_seamless", "ecmwf" to "ecmwf_ifs025", "gfs" to "gfs_seamless",
        "mf" to "meteofrance_seamless", "ukmo" to "ukmo_seamless", "gem" to "gem_seamless"
    )
    private const val UA = "DC-Radiouhr/3.2 (+https://github.com/Gauco-12345/dc-radiouhr)"

    private fun r(v: Double, dec: Int): Any = if (v.isNaN()) JSONObject.NULL else Math.round(v * Math.pow(10.0, dec.toDouble())) / Math.pow(10.0, dec.toDouble())

    private fun ptJson(t: Long, temp: Double, pr: Double, span: Int, pop: Double, cat: String?, cloud: Double, wind: Double, gust: Double, rh: Double) =
        org.json.JSONArray().put(t).put(r(temp, 1)).put(r(pr, 2)).put(span).put(r(pop, 0))
            .put(cat ?: JSONObject.NULL).put(r(cloud, 0)).put(r(wind, 1)).put(r(gust, 1)).put(r(rh, 0))

    private fun wmoCat(c: Int): String? = when {
        c < 0 -> null
        c <= 1 -> "clear"
        c == 2 -> "partly"
        c == 3 -> "cloudy"
        c == 45 || c == 48 -> "fog"
        c in 51..67 -> "rain"
        c in 71..77 || c == 85 || c == 86 -> "snow"
        c in 80..82 -> "showers"
        c >= 95 -> "thunder"
        else -> null
    }

    private fun metCat(sym: String): String? {
        val s = sym.replace(Regex("_(day|night|polartwilight)$"), "")
        return when {
            s.contains("thunder") -> "thunder"
            s.contains("snow") || s.contains("sleet") -> "snow"
            s.contains("showers") -> "showers"
            s.contains("rain") -> "rain"
            s == "fog" -> "fog"
            s == "cloudy" -> "cloudy"
            s == "partlycloudy" -> "partly"
            s == "clearsky" || s == "fair" -> "clear"
            else -> null
        }
    }

    private fun bsCat(icon: String, cond: String, cloud: Double): String? = when {
        cond == "thunderstorm" || icon == "thunderstorm" -> "thunder"
        cond in listOf("snow", "sleet", "hail") || icon in listOf("snow", "sleet", "hail") -> "snow"
        cond == "rain" || icon == "rain" -> "rain"
        cond == "fog" || icon == "fog" -> "fog"
        icon == "cloudy" -> "cloudy"
        icon.startsWith("partly") -> "partly"
        icon.startsWith("clear") -> "clear"
        else -> cloudCat(cloud)
    }

    /** ISO-Zeit (UTC, „Z“ oder „+00:00“) → Unix-Sekunden. */
    private fun iso(s: String): Long {
        val f = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US)
        f.timeZone = java.util.TimeZone.getTimeZone("UTC")
        var t = f.parse(s.take(19))!!.time / 1000L
        val rest = s.drop(19)
        val m = Regex("([+-])(\\d{2}):?(\\d{2})").find(rest)
        if (m != null) {
            val off = m.groupValues[2].toInt() * 3600 + m.groupValues[3].toInt() * 60
            t -= if (m.groupValues[1] == "+") off else -off
        }
        return t
    }

    private fun dn(o: JSONObject?, k: String): Double = if (o == null || o.isNull(k)) Double.NaN else o.optDouble(k, Double.NaN)

    /** Fragt alle Dienste ab, baut das Paket (gleiches Format wie das Plugin) und speichert es. */
    fun fetch(p: SharedPreferences, lat0: Double, lon0: Double): Bundle? {
        val lat = Math.round(lat0 * 100) / 100.0
        val lon = Math.round(lon0 * 100) / 100.0
        val sources = JSONObject()
        var offset = Long.MIN_VALUE
        var elevation = Double.NaN

        // 1) Open-Meteo: sechs Modelle in einer Abfrage
        try {
            val vars = "temperature_2m,precipitation,precipitation_probability,weather_code,cloud_cover,wind_speed_10m,wind_gusts_10m,relative_humidity_2m"
            val url = String.format(Locale.US,
                "https://api.open-meteo.com/v1/forecast?latitude=%.2f&longitude=%.2f&hourly=%s&models=%s&timezone=auto&timeformat=unixtime&forecast_days=8&wind_speed_unit=kmh",
                lat, lon, vars, MODELS.values.joinToString(","))
            val j = JSONObject(Net.get(url, mapOf("User-Agent" to UA)))
            offset = j.optLong("utc_offset_seconds", 0L)
            elevation = j.optDouble("elevation", Double.NaN)
            val h = j.getJSONObject("hourly")
            val time = h.getJSONArray("time")
            for ((id, model) in MODELS) {
                fun arr(k: String) = h.optJSONArray("${k}_$model") ?: (if (MODELS.size == 1) h.optJSONArray(k) else null)
                val T = arr("temperature_2m")
                if (T == null) {
                    sources.put(id, JSONObject().put("status", "err").put("msg", "keine Daten"))
                    continue
                }
                val P = arr("precipitation"); val PP = arr("precipitation_probability"); val W = arr("weather_code")
                val C = arr("cloud_cover"); val WS = arr("wind_speed_10m"); val WG = arr("wind_gusts_10m"); val RH = arr("relative_humidity_2m")
                fun v(a: org.json.JSONArray?, i: Int) = if (a == null || a.isNull(i)) Double.NaN else a.optDouble(i, Double.NaN)
                val data = org.json.JSONArray()
                for (i in 0 until time.length()) {
                    val temp = v(T, i)
                    if (temp.isNaN()) continue
                    val cloud = v(C, i)
                    val w = if (W == null || W.isNull(i)) -1 else W.optInt(i, -1)
                    data.put(ptJson(time.getLong(i), temp, v(P, i), 1, v(PP, i), wmoCat(w) ?: cloudCat(cloud), cloud, v(WS, i), v(WG, i), v(RH, i)))
                }
                sources.put(id, if (data.length() > 0) JSONObject().put("status", "ok").put("data", data)
                else JSONObject().put("status", "err").put("msg", "keine Daten"))
            }
        } catch (e: Exception) {
            for (id in MODELS.keys) sources.put(id, JSONObject().put("status", "err").put("msg", e.message ?: "Fehler"))
        }

        // 2) MET Norway
        try {
            val url = String.format(Locale.US, "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=%.4f&lon=%.4f", lat, lon)
            val j = JSONObject(Net.get(url, mapOf("User-Agent" to UA)))
            val ts = j.getJSONObject("properties").getJSONArray("timeseries")
            val data = org.json.JSONArray()
            for (i in 0 until ts.length()) {
                val e = ts.getJSONObject(i)
                val dd = e.getJSONObject("data")
                val d = dd.optJSONObject("instant")?.optJSONObject("details") ?: continue
                val temp = dn(d, "air_temperature")
                if (temp.isNaN()) continue
                val n1 = dd.optJSONObject("next_1_hours")
                val n6 = dd.optJSONObject("next_6_hours")
                val n = n1 ?: n6
                val cl = dn(d, "cloud_area_fraction")
                val sym = n?.optJSONObject("summary")?.optString("symbol_code").orEmpty()
                val ws = dn(d, "wind_speed")
                data.put(ptJson(iso(e.getString("time")), temp, dn(n?.optJSONObject("details"), "precipitation_amount"),
                    if (n1 != null) 1 else 6, Double.NaN, (if (sym.isNotEmpty()) metCat(sym) else null) ?: cloudCat(cl), cl,
                    if (ws.isNaN()) ws else ws * 3.6, Double.NaN, dn(d, "relative_humidity")))
            }
            sources.put("met", if (data.length() > 0) JSONObject().put("status", "ok").put("data", data)
            else JSONObject().put("status", "err").put("msg", "keine Daten"))
        } catch (e: Exception) {
            sources.put("met", JSONObject().put("status", "err").put("msg", e.message ?: "Fehler"))
        }

        // 3) optional: DWD MOSMIX über Bright Sky (Stationswerte, auf Ortshöhe umgerechnet)
        if (p.getBoolean("wxMosmix", false)) try {
            val day = java.text.SimpleDateFormat("yyyy-MM-dd", Locale.US).apply { timeZone = java.util.TimeZone.getTimeZone("UTC") }
            val now = System.currentTimeMillis()
            val url = String.format(Locale.US, "https://api.brightsky.dev/weather?lat=%.2f&lon=%.2f&date=%s&last_date=%s",
                lat, lon, day.format(java.util.Date(now - 86400000L)), day.format(java.util.Date(now + 8 * 86400000L)))
            val j = JSONObject(Net.get(url, mapOf("User-Agent" to UA)))
            val w = j.getJSONArray("weather")
            // häufigste Station
            val count = HashMap<Int, Int>()
            for (i in 0 until w.length()) {
                val sid = w.getJSONObject(i).optInt("source_id", -1)
                count[sid] = (count[sid] ?: 0) + 1
            }
            val top = count.maxByOrNull { it.value }?.key
            var height = Double.NaN
            var station = "?"
            val srcs = j.optJSONArray("sources")
            if (srcs != null) for (i in 0 until srcs.length()) {
                val so = srcs.getJSONObject(i)
                if (so.optInt("id") == top) {
                    height = dn(so, "height")
                    station = so.optString("station_name", "?")
                }
            }
            val delta = if (!height.isNaN() && !elevation.isNaN() && Math.abs(height - elevation) >= 100) (height - elevation) * 0.0065 else 0.0
            val data = org.json.JSONArray()
            for (i in 0 until w.length()) {
                val o = w.getJSONObject(i)
                val temp = dn(o, "temperature")
                if (temp.isNaN()) continue
                val cloud = dn(o, "cloud_cover")
                data.put(ptJson(iso(o.getString("timestamp")), temp + delta, dn(o, "precipitation"), 1, dn(o, "precipitation_probability"),
                    bsCat(o.optString("icon"), o.optString("condition"), cloud), cloud, dn(o, "wind_speed"), dn(o, "wind_gust_speed"), dn(o, "relative_humidity")))
            }
            sources.put("bs", if (data.length() > 0) JSONObject().put("status", "ok").put("data", data).put("msg", "Station $station")
            else JSONObject().put("status", "err").put("msg", "keine Daten"))
        } catch (e: Exception) {
            sources.put("bs", JSONObject().put("status", "err").put("msg", e.message ?: "Fehler"))
        }

        if (offset == Long.MIN_VALUE) offset = (java.util.TimeZone.getDefault().getOffset(System.currentTimeMillis()) / 1000L)
        val bundle = JSONObject().put("generated", System.currentTimeMillis() / 1000L).put("offset", offset).put("sources", sources)
        val json = bundle.toString()
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
