package de.datacreate.radiouhr

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Ausführliche Wetterseite: Netatmo-Station, aktuelle Werte, 24 Stunden, 7 Tage. */
class WeatherActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val accent = col("#FF8A3D")
    private val sub = col("#9A9A9A")
    private val iconCol = col("#FFD27A")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Wx.prefs(this)
        scroll = ScrollView(this)
        scroll.setBackgroundColor(col("#101418"))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(40))
        }
        scroll.addView(list)
        setContentView(scroll)
        render()
        refresh()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    private fun refresh() {
        val lat = prefs.getFloat("wxLat", prefs.getFloat("lat", 47.8295f)).toDouble()
        val lon = prefs.getFloat("wxLon", prefs.getFloat("lon", 7.9447f)).toDouble()
        io.execute {
            try {
                val json = Net.get(Wx.forecastUrl(lat, lon))
                prefs.edit().putString("wxJson", json).putLong("wxTime", System.currentTimeMillis()).apply()
            } catch (_: Exception) { }
            if (Netatmo.connected(prefs)) try { Netatmo.fetch(prefs) } catch (_: Exception) { }
            main.post { render() }
        }
    }

    // ------------------------------------------------------------ Bausteine

    private fun t(s: String, size: Float, color: Int = Color.WHITE, bold: Boolean = false, light: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        if (light) typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
    }

    private fun section(title: String) {
        list.addView(t(title.uppercase(Locale.GERMANY), 13f, accent, bold = true).apply {
            letterSpacing = 0.08f
            setPadding(0, dp(24), 0, dp(8))
        })
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(col("#1B2128"))
            }
            setPadding(dp(14), dp(12), dp(14), dp(12))
        }
        list.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return c
    }

    /** Raster aus Kennzahlen (Wert groß, Bezeichnung klein). */
    private fun grid(parent: LinearLayout, items: List<Pair<String, String>>, cols: Int = 3) {
        var row: LinearLayout? = null
        items.forEachIndexed { i, (label, value) ->
            if (i % cols == 0) {
                row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
                parent.addView(row)
            }
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                setPadding(0, dp(6), dp(6), dp(6))
            }
            cell.addView(t(value, 19f))
            cell.addView(t(label, 12f, sub))
            row?.addView(cell, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        }
        val rest = (cols - items.size % cols) % cols
        repeat(rest) { row?.addView(View(this), LinearLayout.LayoutParams(0, 1, 1f)) }
    }

    private fun d(o: JSONObject?, key: String): Double = o?.optDouble(key, Double.NaN) ?: Double.NaN
    private fun fmt(v: Double, unit: String, dec: Int = 0): String =
        if (v.isNaN()) "–" else String.format(Locale.GERMANY, "%.${dec}f %s", v, unit).trim()
    private fun hm(epochSec: Long): String = if (epochSec <= 0) "" else SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(epochSec * 1000))
    private fun isoTime(s: String): String = if (s.length >= 16) s.substring(11, 16) else s

    private fun trend(s: String): String = when (s) {
        "up" -> " ↑"
        "down" -> " ↓"
        "stable" -> " →"
        else -> ""
    }

    // ------------------------------------------------------------ Aufbau

    private fun render() {
        val y = scroll.scrollY
        list.removeAllViews()
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(t("‹", 30f).apply {
            setPadding(0, 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        val place = prefs.getString("wxPlaceName", null) ?: prefs.getString("placeName", "Todtnau") ?: ""
        head.addView(t("Wetter · $place", 22f, light = true))
        list.addView(head)
        val wt = prefs.getLong("wxTime", 0L)
        if (wt > 0) list.addView(t("Stand " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(wt)), 12f, sub))

        netatmoSection()

        val wx = try { prefs.getString("wxJson", null)?.let { JSONObject(it) } } catch (_: Exception) { null }
        if (wx == null) {
            list.addView(t("\nWetterdaten werden geladen …", 15f, sub))
            return
        }
        currentSection(wx)
        todaySection(wx)
        hourlySection(wx)
        dailySection(wx)
        list.addView(t("\nQuelle: Open-Meteo.com" + if (Netatmo.connected(prefs)) " · Netatmo" else "", 11f, sub))
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun netatmoSection() {
        if (!Netatmo.connected(prefs)) return
        val dev = Netatmo.cached(prefs) ?: return
        section("Deine Netatmo-Station")
        val nt = prefs.getLong("ntTime", 0L)
        val out = Netatmo.outdoor(dev)
        val od = out?.optJSONObject("dashboard_data")
        if (od != null) {
            val c = card()
            c.addView(t("Außen", 13f, sub))
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.BOTTOM
            }
            row.addView(t(Wx.deg1(d(od, "Temperature")) + trend(od.optString("temp_trend")), 44f, light = true))
            row.addView(t("  ${fmt(d(od, "Humidity"), "%")}", 18f, sub))
            c.addView(row)
            grid(c, listOf(
                "Höchst heute" to Wx.deg1(d(od, "max_temp")) + "  " + hm(od.optLong("date_max_temp")),
                "Tiefst heute" to Wx.deg1(d(od, "min_temp")) + "  " + hm(od.optLong("date_min_temp")),
                "Gemessen" to hm(od.optLong("time_utc"))
            ))
            val batt = out.optInt("battery_percent", -1)
            if (batt >= 0) c.addView(t("Batterie Außenmodul: $batt %", 12f, sub))
        }
        val dd = dev.optJSONObject("dashboard_data")
        if (dd != null) {
            val c = card()
            (c.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
            c.addView(t("Innen · ${dev.optString("module_name", "Basisstation")}", 13f, sub))
            grid(c, listOf(
                "Temperatur" to Wx.deg1(d(dd, "Temperature")) + trend(dd.optString("temp_trend")),
                "Luftfeuchte" to fmt(d(dd, "Humidity"), "%"),
                "CO₂" to fmt(d(dd, "CO2"), "ppm"),
                "Lärm" to fmt(d(dd, "Noise"), "dB"),
                "Luftdruck" to fmt(d(dd, "Pressure"), "hPa", 1) + trend(dd.optString("pressure_trend")),
                "Min / Max" to "${Wx.deg1(d(dd, "min_temp"))} / ${Wx.deg1(d(dd, "max_temp"))}"
            ))
        }
        Netatmo.rain(dev)?.optJSONObject("dashboard_data")?.let { rd ->
            val c = card()
            (c.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
            c.addView(t("Regenmesser", 13f, sub))
            grid(c, listOf(
                "jetzt" to fmt(d(rd, "Rain"), "mm", 1),
                "letzte Stunde" to fmt(d(rd, "sum_rain_1"), "mm", 1),
                "heute" to fmt(d(rd, "sum_rain_24"), "mm", 1)
            ))
        }
        Netatmo.wind(dev)?.optJSONObject("dashboard_data")?.let { wd ->
            val c = card()
            (c.layoutParams as LinearLayout.LayoutParams).topMargin = dp(10)
            c.addView(t("Windmesser", 13f, sub))
            grid(c, listOf(
                "Wind" to "${fmt(d(wd, "WindStrength"), "km/h")} ${Wx.dir(d(wd, "WindAngle"))}",
                "Böen" to "${fmt(d(wd, "GustStrength"), "km/h")} ${Wx.dir(d(wd, "GustAngle"))}",
                "Max heute" to fmt(d(wd, "max_wind_str"), "km/h")
            ))
        }
        if (nt > 0) list.addView(t("Netatmo-Daten von " + SimpleDateFormat("HH:mm", Locale.GERMANY).format(Date(nt)), 11f, sub).apply {
            setPadding(0, dp(4), 0, 0)
        })
    }

    private fun currentSection(wx: JSONObject) {
        val cur = wx.optJSONObject("current") ?: return
        section("Jetzt")
        val c = card()
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        val code = cur.optInt("weather_code", -1)
        row.addView(t(Wx.icon(code, cur.optInt("is_day", 1) == 1), 40f, iconCol))
        row.addView(t("  " + Wx.deg(d(cur, "temperature_2m")), 44f, light = true))
        row.addView(t("   " + Wx.text(code), 17f, sub))
        c.addView(row)
        grid(c, listOf(
            "gefühlt" to Wx.deg(d(cur, "apparent_temperature")),
            "Luftfeuchte" to fmt(d(cur, "relative_humidity_2m"), "%"),
            "Taupunkt" to Wx.deg(d(cur, "dew_point_2m")),
            "Wind" to "${fmt(d(cur, "wind_speed_10m"), "km/h")} ${Wx.dir(d(cur, "wind_direction_10m"))}",
            "Böen" to fmt(d(cur, "wind_gusts_10m"), "km/h"),
            "Luftdruck" to fmt(d(cur, "surface_pressure"), "hPa"),
            "Bewölkung" to fmt(d(cur, "cloud_cover"), "%"),
            "UV-Index" to fmt(d(cur, "uv_index"), "", 1),
            "Sicht" to fmt(d(cur, "visibility") / 1000.0, "km")
        ))
    }

    private fun todaySection(wx: JSONObject) {
        val dy = wx.optJSONObject("daily") ?: return
        section("Heute")
        val c = card()
        fun a(k: String) = dy.optJSONArray(k)
        grid(c, listOf(
            "Höchst" to Wx.deg(a("temperature_2m_max")?.optDouble(0) ?: Double.NaN),
            "Tiefst" to Wx.deg(a("temperature_2m_min")?.optDouble(0) ?: Double.NaN),
            "Sonnenstunden" to Wx.sunHours(a("sunshine_duration")?.optDouble(0) ?: Double.NaN),
            "Regen" to Wx.mm(a("precipitation_sum")?.optDouble(0) ?: Double.NaN),
            "Regenrisiko" to fmt(a("precipitation_probability_max")?.optDouble(0) ?: Double.NaN, "%"),
            "UV max" to fmt(a("uv_index_max")?.optDouble(0) ?: Double.NaN, "", 1),
            "Sonnenaufgang" to isoTime(a("sunrise")?.optString(0) ?: ""),
            "Sonnenuntergang" to isoTime(a("sunset")?.optString(0) ?: ""),
            "Böen max" to fmt(a("wind_gusts_10m_max")?.optDouble(0) ?: Double.NaN, "km/h")
        ))
    }

    private fun hourlySection(wx: JSONObject) {
        val h = wx.optJSONObject("hourly") ?: return
        val times = h.optJSONArray("time") ?: return
        section("Nächste 24 Stunden")
        val hs = HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
        val row = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        hs.addView(row)
        for (i in 0 until minOf(24, times.length())) {
            val cell = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(dp(6), dp(8), dp(6), dp(8))
                background = GradientDrawable().apply {
                    cornerRadius = dp(10).toFloat()
                    setColor(col("#1B2128"))
                }
            }
            cell.addView(t(isoTime(times.optString(i)), 12f, sub))
            cell.addView(t(Wx.icon(h.optJSONArray("weather_code")?.optInt(i, -1) ?: -1,
                (h.optJSONArray("is_day")?.optInt(i, 1) ?: 1) == 1), 20f, iconCol))
            cell.addView(t(Wx.deg(h.optJSONArray("temperature_2m")?.optDouble(i) ?: Double.NaN), 16f))
            val pp = h.optJSONArray("precipitation_probability")?.optDouble(i) ?: Double.NaN
            cell.addView(t(if (pp.isNaN()) "" else "${Math.round(pp)} %", 11f, col("#8FB8D8")))
            row.addView(cell, LinearLayout.LayoutParams(dp(58), ViewGroup.LayoutParams.WRAP_CONTENT).apply { rightMargin = dp(6) })
        }
        list.addView(hs)
    }

    private fun dailySection(wx: JSONObject) {
        val dy = wx.optJSONObject("daily") ?: return
        val times = dy.optJSONArray("time") ?: return
        section("7 Tage")
        val c = card()
        val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        val wd = SimpleDateFormat("EEE d.M.", Locale.GERMANY)
        for (i in 0 until times.length()) {
            val label = if (i == 0) "Heute" else try { wd.format(parse.parse(times.getString(i))!!) } catch (_: Exception) { "" }
            val code = dy.optJSONArray("weather_code")?.optInt(i, -1) ?: -1
            val row = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_VERTICAL
                setPadding(0, dp(8), 0, dp(8))
            }
            row.addView(t(label, 14f), LinearLayout.LayoutParams(dp(76), ViewGroup.LayoutParams.WRAP_CONTENT))
            row.addView(t(Wx.icon(code), 20f, iconCol), LinearLayout.LayoutParams(dp(34), ViewGroup.LayoutParams.WRAP_CONTENT))
            val mid = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
            mid.addView(t(Wx.text(code), 13f))
            val rain = dy.optJSONArray("precipitation_sum")?.optDouble(i) ?: Double.NaN
            val prob = dy.optJSONArray("precipitation_probability_max")?.optDouble(i) ?: Double.NaN
            val sun = dy.optJSONArray("sunshine_duration")?.optDouble(i) ?: Double.NaN
            val wind = dy.optJSONArray("wind_speed_10m_max")?.optDouble(i) ?: Double.NaN
            mid.addView(t("☀ ${Wx.sunHours(sun)}  ☂ ${Wx.mm(rain)}${if (prob.isNaN()) "" else " (${Math.round(prob)} %)"}  ≈ ${fmt(wind, "km/h")}", 11f, sub))
            row.addView(mid, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
            val mx = dy.optJSONArray("temperature_2m_max")?.optDouble(i) ?: Double.NaN
            val mn = dy.optJSONArray("temperature_2m_min")?.optDouble(i) ?: Double.NaN
            row.addView(t("${Wx.deg(mx)} / ${Wx.deg(mn)}", 15f))
            c.addView(row)
        }
    }
}
