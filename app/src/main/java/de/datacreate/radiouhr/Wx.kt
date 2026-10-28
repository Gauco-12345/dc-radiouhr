package de.datacreate.radiouhr

import android.content.Context
import android.content.SharedPreferences
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

/** Einfache HTTP-Hilfen für Hintergrund-Threads. */
object Net {
    private fun open(url: String, method: String = "GET", body: String? = null, headers: Map<String, String> = emptyMap()): HttpURLConnection {
        var u = URL(url)
        var redirects = 0
        while (true) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 12000
            c.readTimeout = 20000
            c.requestMethod = method
            c.setRequestProperty("User-Agent", "DC-Radiouhr/3.0 (Android)")
            for ((k, v) in headers) c.setRequestProperty(k, v)
            if (body != null) {
                c.doOutput = true
                c.setRequestProperty("Content-Type", "application/x-www-form-urlencoded;charset=UTF-8")
                c.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
            }
            val code = c.responseCode
            if (code in 300..399 && redirects < 5 && body == null) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("Redirect ohne Ziel")
                u = URL(u, loc)
                redirects++
                continue
            }
            if (code !in 200..299) {
                val err = try { c.errorStream?.bufferedReader()?.use { it.readText() } } catch (_: Exception) { null }
                c.disconnect()
                throw IOException("HTTP $code ${err ?: ""}".trim())
            }
            return c
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap()): String {
        val c = open(url, headers = headers)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    fun post(url: String, form: Map<String, String>): String {
        val body = form.entries.joinToString("&") { "${it.key}=${URLEncoder.encode(it.value, "UTF-8")}" }
        val c = open(url, "POST", body)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}

/** Wetter-Hilfen (Open-Meteo-Codes, Formatierung). */
object Wx {
    fun prefs(ctx: Context): SharedPreferences = ctx.getSharedPreferences("radiouhr", Context.MODE_PRIVATE)

    fun icon(code: Int, day: Boolean = true): String = when (code) {
        0, 1 -> if (day) "☀" else "☾"
        2 -> if (day) "⛅" else "☾"
        3 -> "☁"
        45, 48 -> "≋"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "☁"
    }

    fun text(code: Int): String = when (code) {
        0 -> "klar"
        1 -> "überwiegend klar"
        2 -> "teilweise bewölkt"
        3 -> "bewölkt"
        45, 48 -> "Nebel"
        in 51..57 -> "Nieselregen"
        in 61..67 -> "Regen"
        in 71..77 -> "Schnee"
        in 80..82 -> "Regenschauer"
        85, 86 -> "Schneeschauer"
        in 95..99 -> "Gewitter"
        else -> "–"
    }

    fun dir(deg: Double): String {
        if (deg.isNaN()) return ""
        val names = arrayOf("N", "NO", "O", "SO", "S", "SW", "W", "NW")
        return names[(((deg % 360) + 22.5) / 45).toInt() % 8]
    }

    fun mm(v: Double): String =
        if (v.isNaN() || v < 0.05) "0 mm" else String.format(Locale.GERMANY, "%.1f mm", v)

    fun deg(v: Double): String = if (v.isNaN()) "–°" else "${Math.round(v)}°"

    fun deg1(v: Double): String = if (v.isNaN()) "–°" else String.format(Locale.GERMANY, "%.1f°", v)

    fun sunHours(seconds: Double): String =
        if (seconds.isNaN()) "–" else String.format(Locale.GERMANY, "%.0f h", seconds / 3600.0)

    /** Open-Meteo: alle Werte, die Startseite und Detailseite brauchen. */
    fun forecastUrl(lat: Double, lon: Double): String = String.format(
        Locale.US,
        "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
            "&current=temperature_2m,apparent_temperature,relative_humidity_2m,dew_point_2m,weather_code,is_day," +
            "wind_speed_10m,wind_gusts_10m,wind_direction_10m,surface_pressure,cloud_cover,precipitation,uv_index,visibility" +
            "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,is_day" +
            "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum,precipitation_probability_max," +
            "sunshine_duration,sunrise,sunset,uv_index_max,wind_speed_10m_max,wind_gusts_10m_max,wind_direction_10m_dominant" +
            "&timezone=auto&forecast_days=7&forecast_hours=24",
        lat, lon
    )
}

/** Netatmo-Wetterstation: OAuth-Token verwalten und Stationsdaten holen. */
object Netatmo {
    const val REDIRECT = "https://localhost/dcradiouhr"
    private const val TOKEN_URL = "https://api.netatmo.com/oauth2/token"

    fun connected(p: SharedPreferences) =
        !p.getString("ntClientId", "").isNullOrEmpty() &&
            !p.getString("ntClientSecret", "").isNullOrEmpty() &&
            !p.getString("ntRefresh", "").isNullOrEmpty()

    fun authorizeUrl(clientId: String, state: String): String =
        "https://api.netatmo.com/oauth2/authorize?client_id=${URLEncoder.encode(clientId, "UTF-8")}" +
            "&redirect_uri=${URLEncoder.encode(REDIRECT, "UTF-8")}&scope=read_station&state=$state"

    private fun store(p: SharedPreferences, json: String) {
        val j = JSONObject(json)
        val access = j.getString("access_token")
        val refresh = j.optString("refresh_token", p.getString("ntRefresh", "") ?: "")
        val expires = j.optLong("expires_in", 10800L)
        p.edit()
            .putString("ntAccess", access)
            .putString("ntRefresh", refresh)
            .putLong("ntExpires", System.currentTimeMillis() + (expires - 120) * 1000L)
            .apply()
    }

    fun exchangeCode(p: SharedPreferences, code: String) {
        val res = Net.post(
            TOKEN_URL, mapOf(
                "grant_type" to "authorization_code",
                "client_id" to (p.getString("ntClientId", "") ?: ""),
                "client_secret" to (p.getString("ntClientSecret", "") ?: ""),
                "code" to code,
                "redirect_uri" to REDIRECT,
                "scope" to "read_station"
            )
        )
        store(p, res)
    }

    @Synchronized
    fun accessToken(p: SharedPreferences): String {
        val access = p.getString("ntAccess", "") ?: ""
        if (access.isNotEmpty() && System.currentTimeMillis() < p.getLong("ntExpires", 0L)) return access
        val res = Net.post(
            TOKEN_URL, mapOf(
                "grant_type" to "refresh_token",
                "refresh_token" to (p.getString("ntRefresh", "") ?: ""),
                "client_id" to (p.getString("ntClientId", "") ?: ""),
                "client_secret" to (p.getString("ntClientSecret", "") ?: "")
            )
        )
        store(p, res)
        return p.getString("ntAccess", "") ?: ""
    }

    /** Holt getstationsdata, speichert das Ergebnis und gibt das erste Gerät zurück. */
    fun fetch(p: SharedPreferences): JSONObject? {
        val token = accessToken(p)
        val res = Net.get("https://api.netatmo.com/api/getstationsdata", mapOf("Authorization" to "Bearer $token"))
        val dev = JSONObject(res).optJSONObject("body")?.optJSONArray("devices")?.optJSONObject(0) ?: return null
        p.edit().putString("ntJson", dev.toString()).putLong("ntTime", System.currentTimeMillis()).apply()
        return dev
    }

    fun cached(p: SharedPreferences): JSONObject? = try {
        p.getString("ntJson", null)?.let { JSONObject(it) }
    } catch (_: Exception) { null }

    fun outdoor(dev: JSONObject): JSONObject? = module(dev, "NAModule1")
    fun rain(dev: JSONObject): JSONObject? = module(dev, "NAModule3")
    fun wind(dev: JSONObject): JSONObject? = module(dev, "NAModule2")

    fun module(dev: JSONObject, type: String): JSONObject? {
        val mods = dev.optJSONArray("modules") ?: return null
        for (i in 0 until mods.length()) {
            val m = mods.optJSONObject(i) ?: continue
            if (m.optString("type") == type) return m
        }
        return null
    }

    fun disconnect(p: SharedPreferences) {
        p.edit().remove("ntAccess").remove("ntRefresh").remove("ntExpires").remove("ntJson").remove("ntTime").apply()
    }
}
