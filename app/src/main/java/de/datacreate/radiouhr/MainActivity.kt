package de.datacreate.radiouhr

import android.app.Activity
import android.app.AlertDialog
import android.content.SharedPreferences
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Typeface
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.concurrent.Executors

data class Station(val name: String, val url: String)

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val rnd = Random()

    private lateinit var prefs: SharedPreferences
    private lateinit var player: ExoPlayer
    private lateinit var audio: AudioManager

    private lateinit var root: LinearLayout
    private lateinit var clockPanel: LinearLayout
    private lateinit var radioPanel: LinearLayout
    private lateinit var tvTime: TextView
    private lateinit var tvDate: TextView
    private lateinit var tvWeatherIcon: TextView
    private lateinit var tvTemp: TextView
    private lateinit var tvWeatherInfo: TextView
    private lateinit var tvStation: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var btnPlay: TextView
    private lateinit var btnTimer: TextView

    private val stations = mutableListOf<Station>()
    private var current = 0
    private var wantPlaying = false
    private var night = false
    private var lastAutoNight: Boolean? = null
    private var lastMinute = -1
    private var sleepAt = 0L

    private val timeFmt = SimpleDateFormat("HH:mm", Locale.GERMANY)
    private val dateFmt = SimpleDateFormat("EEEE, d. MMMM", Locale.GERMANY)
    private val dlgTheme = android.R.style.Theme_Material_Dialog_Alert

    // ---------------------------------------------------------------- Lebenszyklus

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        @Suppress("DEPRECATION")
        window.addFlags(
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON or
                WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED
        )
        volumeControlStream = AudioManager.STREAM_MUSIC

        prefs = getSharedPreferences("radiouhr", MODE_PRIVATE)
        audio = getSystemService(AUDIO_SERVICE) as AudioManager

        root = findViewById(R.id.root)
        clockPanel = findViewById(R.id.clockPanel)
        radioPanel = findViewById(R.id.radioPanel)
        tvTime = findViewById(R.id.tvTime)
        tvDate = findViewById(R.id.tvDate)
        tvWeatherIcon = findViewById(R.id.tvWeatherIcon)
        tvTemp = findViewById(R.id.tvTemp)
        tvWeatherInfo = findViewById(R.id.tvWeatherInfo)
        tvStation = findViewById(R.id.tvStation)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        btnPlay = findViewById(R.id.btnPlay)
        btnTimer = findViewById(R.id.btnTimer)

        tvTime.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        tvTemp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

        setupPlayer()
        loadStations()

        btnPlay.setOnClickListener { if (wantPlaying) stopRadio() else playCurrent() }
        findViewById<View>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { step(1) }
        findViewById<View>(R.id.btnVolDown).setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_LOWER, AudioManager.FLAG_SHOW_UI)
        }
        findViewById<View>(R.id.btnVolUp).setOnClickListener {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_RAISE, AudioManager.FLAG_SHOW_UI)
        }
        findViewById<View>(R.id.btnStations).setOnClickListener { showStations() }
        btnTimer.setOnClickListener { showTimer() }
        findViewById<View>(R.id.btnNight).setOnClickListener { setNight(!night) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        clockPanel.setOnClickListener { setNight(!night) }

        showStationName()
        applyOrientation(resources.configuration)
        setNight(false)

        if (prefs.getBoolean("autoplay", false) && stations.isNotEmpty()) playCurrent()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        main.removeCallbacks(tick)
        main.post(tick)
        main.removeCallbacks(weatherLoop)
        main.post(weatherLoop)
    }

    override fun onPause() {
        super.onPause()
        main.removeCallbacks(tick)
        main.removeCallbacks(weatherLoop)
        // Radio läuft bewusst weiter
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        player.release()
        io.shutdownNow()
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemUi()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyOrientation(newConfig)
    }

    @Suppress("DEPRECATION")
    private fun hideSystemUi() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    // ---------------------------------------------------------------- Layout

    private fun applyOrientation(cfg: Configuration) {
        val dm = resources.displayMetrics
        val landscape = cfg.orientation == Configuration.ORIENTATION_LANDSCAPE
        if (landscape) {
            root.orientation = LinearLayout.HORIZONTAL
            clockPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 3f)
            radioPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f)
            val panelW = dm.widthPixels * 0.6f
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(panelW / 3.0f, dm.heightPixels * 0.42f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            clockPanel.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 3f)
            radioPanel.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 2f)
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, dm.widthPixels / 3.1f)
        }
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    // ---------------------------------------------------------------- Uhr

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            tvTime.text = timeFmt.format(now)
            tvDate.text = dateFmt.format(now)

            val cal = Calendar.getInstance()
            val minute = cal.get(Calendar.MINUTE)
            if (minute != lastMinute) {
                lastMinute = minute
                // leichtes Verschieben gegen Einbrennen
                clockPanel.translationX = (rnd.nextInt(13) - 6) * dp(1)
                clockPanel.translationY = (rnd.nextInt(13) - 6) * dp(1)
                checkAutoNight(cal.get(Calendar.HOUR_OF_DAY))
            }

            if (sleepAt > 0) {
                val left = sleepAt - System.currentTimeMillis()
                if (left <= 0) {
                    sleepAt = 0
                    stopRadio()
                    btnTimer.text = "Timer"
                } else {
                    btnTimer.text = "Timer ${(left / 60000) + 1}′"
                }
            }
            main.postDelayed(this, 1000 - System.currentTimeMillis() % 1000)
        }
    }

    private fun checkAutoNight(hour: Int) {
        if (!prefs.getBoolean("autoNight", true)) {
            lastAutoNight = null
            return
        }
        val should = hour >= 22 || hour < 7
        if (lastAutoNight != should) {
            lastAutoNight = should
            setNight(should)
        }
    }

    private fun setNight(on: Boolean) {
        night = on
        val fg = if (on) Color.parseColor("#B8432F") else Color.WHITE
        val sub = if (on) Color.parseColor("#7A2C20") else Color.parseColor("#AAAAAA")
        tvTime.setTextColor(fg)
        tvTemp.setTextColor(fg)
        tvStation.setTextColor(fg)
        tvDate.setTextColor(sub)
        tvWeatherInfo.setTextColor(sub)
        tvNowPlaying.setTextColor(sub)
        tvWeatherIcon.setTextColor(if (on) sub else Color.parseColor("#FFD27A"))
        radioPanel.alpha = if (on) 0.45f else 1f
        val lp = window.attributes
        lp.screenBrightness = if (on) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    // ---------------------------------------------------------------- Wetter

    private val weatherLoop = object : Runnable {
        override fun run() {
            loadWeather()
            main.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun placeName() = prefs.getString("placeName", "Todtnau") ?: "Todtnau"

    private fun loadWeather() {
        val lat = prefs.getFloat("lat", 47.8295f)
        val lon = prefs.getFloat("lon", 7.9447f)
        val place = placeName()
        val url = "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon" +
            "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
            "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1"
        io.execute {
            try {
                val j = JSONObject(httpGet(url))
                val cur = j.getJSONObject("current")
                val temp = cur.getDouble("temperature_2m")
                val feels = cur.getDouble("apparent_temperature")
                val code = cur.getInt("weather_code")
                val wind = cur.getDouble("wind_speed_10m")
                val isDay = cur.optInt("is_day", 1) == 1
                val daily = j.getJSONObject("daily")
                val max = daily.getJSONArray("temperature_2m_max").getDouble(0)
                val min = daily.getJSONArray("temperature_2m_min").getDouble(0)
                val stand = timeFmt.format(Date())
                main.post {
                    tvTemp.text = "${Math.round(temp)}°"
                    tvWeatherIcon.text = weatherIcon(code, isDay)
                    tvWeatherInfo.text = "$place · ${weatherText(code)}\n" +
                        "gefühlt ${Math.round(feels)}° · ${Math.round(min)}° / ${Math.round(max)}° · " +
                        "Wind ${Math.round(wind)} km/h · Stand $stand"
                }
            } catch (e: Exception) {
                main.post {
                    tvWeatherInfo.text = "$place · Wetter gerade nicht abrufbar"
                    main.removeCallbacks(weatherLoop)
                    main.postDelayed(weatherLoop, 60 * 1000L)
                }
            }
        }
    }

    private fun weatherIcon(code: Int, day: Boolean): String = when (code) {
        0 -> if (day) "☀" else "☾"
        1, 2 -> if (day) "☀" else "☾"
        3 -> "☁"
        45, 48 -> "≋"
        in 51..67, in 80..82 -> "☂"
        in 71..77, 85, 86 -> "❄"
        in 95..99 -> "⚡"
        else -> "☁"
    }

    private fun weatherText(code: Int): String = when (code) {
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

    // ---------------------------------------------------------------- Radio

    private val retry = Runnable { if (wantPlaying) playCurrent() }

    private fun setupPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("DC-Radiouhr/1.0")
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .build()
        player.setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(C.USAGE_MEDIA)
                .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                .build(),
            true
        )
        player.setWakeMode(C.WAKE_MODE_NETWORK)
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                if (isPlaying && tvNowPlaying.text.startsWith("Verbinde")) tvNowPlaying.text = ""
                updatePlayButton()
            }

            override fun onPlaybackStateChanged(state: Int) {
                if (state == Player.STATE_BUFFERING && wantPlaying) tvNowPlaying.text = "Verbinde …"
                if (state == Player.STATE_ENDED && wantPlaying) main.postDelayed(retry, 3000)
            }

            override fun onPlayerError(error: PlaybackException) {
                if (wantPlaying) {
                    tvNowPlaying.text = "Keine Verbindung – neuer Versuch …"
                    main.removeCallbacks(retry)
                    main.postDelayed(retry, 5000)
                }
            }

            override fun onMediaMetadataChanged(mediaMetadata: MediaMetadata) {
                val t = mediaMetadata.title?.toString()?.trim()
                if (!t.isNullOrEmpty()) tvNowPlaying.text = t
            }
        })
    }

    private fun playCurrent() {
        if (stations.isEmpty()) {
            showStations()
            return
        }
        current = current.coerceIn(0, stations.size - 1)
        val s = stations[current]
        wantPlaying = true
        main.removeCallbacks(retry)
        player.setMediaItem(MediaItem.fromUri(s.url))
        player.prepare()
        player.play()
        tvNowPlaying.text = "Verbinde …"
        prefs.edit().putInt("current", current).apply()
        showStationName()
        updatePlayButton()
    }

    private fun stopRadio() {
        wantPlaying = false
        main.removeCallbacks(retry)
        player.stop()
        tvNowPlaying.text = ""
        updatePlayButton()
    }

    private fun step(dir: Int) {
        if (stations.isEmpty()) return
        current = (current + dir + stations.size) % stations.size
        if (wantPlaying) playCurrent() else {
            prefs.edit().putInt("current", current).apply()
            showStationName()
        }
    }

    private fun updatePlayButton() {
        btnPlay.text = if (wantPlaying) "■" else "▶"
    }

    private fun showStationName() {
        tvStation.text = if (stations.isEmpty()) "Keine Sender – tippe auf „Sender“"
        else stations[current.coerceIn(0, stations.size - 1)].name
    }

    // ---------------------------------------------------------------- Senderliste

    private fun loadStations() {
        stations.clear()
        val raw = prefs.getString("stations", null)
        if (raw == null) {
            stations.add(Station("SWR3", "https://liveradio.swr.de/sw282p3/swr3/play.mp3"))
            stations.add(Station("SWR1 Baden-Württemberg", "https://liveradio.swr.de/sw282p3/swr1bw/play.mp3"))
            stations.add(Station("Deutschlandfunk", "https://st01.sslstream.dlf.de/dlf/01/128/mp3/stream.mp3"))
            stations.add(Station("Deutschlandfunk Kultur", "https://st02.sslstream.dlf.de/dlf/02/128/mp3/stream.mp3"))
            saveStations()
        } else {
            try {
                val arr = JSONArray(raw)
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    stations.add(Station(o.getString("name"), o.getString("url")))
                }
            } catch (_: Exception) { }
        }
        current = prefs.getInt("current", 0)
    }

    private fun saveStations() {
        val arr = JSONArray()
        for (s in stations) arr.put(JSONObject().put("name", s.name).put("url", s.url))
        prefs.edit().putString("stations", arr.toString()).putInt("current", current).apply()
    }

    private fun showStations() {
        val names = stations.map { it.name }.toTypedArray()
        val checked = if (stations.isEmpty()) -1 else current.coerceIn(0, stations.size - 1)
        val d = AlertDialog.Builder(this, dlgTheme)
            .setTitle("Sender  (lange drücken = entfernen)")
            .setSingleChoiceItems(names, checked) { dlg, which ->
                current = which
                playCurrent()
                dlg.dismiss()
            }
            .setPositiveButton("Sender suchen") { _, _ -> askSearch() }
            .setNeutralButton("Eigene URL") { _, _ -> askCustomUrl() }
            .setNegativeButton("Schließen", null)
            .create()
        d.setOnShowListener {
            d.listView?.setOnItemLongClickListener { _, _, pos, _ ->
                d.dismiss()
                confirmRemove(pos)
                true
            }
        }
        d.show()
    }

    private fun confirmRemove(pos: Int) {
        if (pos !in stations.indices) return
        val s = stations[pos]
        AlertDialog.Builder(this, dlgTheme)
            .setMessage("„${s.name}“ entfernen?")
            .setPositiveButton("Entfernen") { _, _ ->
                val wasCurrent = pos == current
                stations.removeAt(pos)
                if (pos < current) current--
                if (current >= stations.size) current = 0
                if (wasCurrent && wantPlaying) stopRadio()
                saveStations()
                showStationName()
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun addStation(s: Station) {
        val idx = stations.indexOfFirst { it.url == s.url }
        current = if (idx >= 0) idx else {
            stations.add(s)
            stations.size - 1
        }
        saveStations()
        playCurrent()
    }

    private fun input(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = type
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

    private fun padded(v: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(20).toInt()
        setPadding(p, p / 2, p, 0)
        addView(v)
    }

    private fun askSearch() {
        val et = input("z. B. SWR4, Antenne, Jazz …")
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Radiosender suchen")
            .setView(padded(et))
            .setPositiveButton("Suchen") { _, _ ->
                val q = et.text.toString().trim()
                if (q.isNotEmpty()) searchStations(q)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun searchStations(q: String) {
        toast("Suche „$q“ …")
        val enc = URLEncoder.encode(q, "UTF-8")
        val servers = listOf(
            "https://de1.api.radio-browser.info",
            "https://fi1.api.radio-browser.info",
            "https://de2.api.radio-browser.info"
        )
        io.execute {
            var result: JSONArray? = null
            for (srv in servers) {
                try {
                    result = JSONArray(
                        httpGet("$srv/json/stations/search?name=$enc&limit=40&hidebroken=true&order=clickcount&reverse=true")
                    )
                    break
                } catch (_: Exception) { }
            }
            main.post {
                val arr = result
                if (arr == null) {
                    toast("Sendersuche nicht erreichbar")
                    return@post
                }
                val found = mutableListOf<Station>()
                val labels = mutableListOf<String>()
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                    val name = o.optString("name").trim()
                    if (url.isEmpty() || name.isEmpty()) continue
                    found.add(Station(name, url))
                    val br = o.optInt("bitrate", 0)
                    val info = listOf(o.optString("countrycode"), o.optString("codec"), if (br > 0) "$br kbit/s" else "")
                        .filter { it.isNotEmpty() }.joinToString(" · ")
                    labels.add(if (info.isEmpty()) name else "$name\n$info")
                }
                if (found.isEmpty()) {
                    toast("Nichts gefunden")
                    return@post
                }
                AlertDialog.Builder(this, dlgTheme)
                    .setTitle("Treffer – antippen zum Hinzufügen")
                    .setItems(labels.toTypedArray()) { _, which -> addStation(found[which]) }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
    }

    private fun askCustomUrl() {
        val name = input("Name")
        val url = input("Stream-URL (http…)", InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_URI)
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(20).toInt()
            setPadding(p, p / 2, p, 0)
            addView(name)
            addView(url)
        }
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Sender per URL hinzufügen")
            .setView(box)
            .setPositiveButton("Hinzufügen") { _, _ ->
                val u = url.text.toString().trim()
                val n = name.text.toString().trim().ifEmpty { u }
                if (u.startsWith("http")) addStation(Station(n, u)) else toast("Ungültige URL")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // ---------------------------------------------------------------- Timer & Optionen

    private fun showTimer() {
        val mins = intArrayOf(0, 15, 30, 45, 60, 90, 120)
        val labels = mins.map { if (it == 0) "Aus" else "$it Minuten" }.toTypedArray()
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Einschlaf-Timer")
            .setItems(labels) { _, which ->
                val m = mins[which]
                if (m == 0) {
                    sleepAt = 0
                    btnTimer.text = "Timer"
                } else {
                    sleepAt = System.currentTimeMillis() + m * 60_000L
                    btnTimer.text = "Timer $m′"
                    if (!wantPlaying) playCurrent()
                }
            }
            .show()
    }

    private fun showSettings() {
        val labels = arrayOf(
            "Radio beim Start automatisch abspielen",
            "Nachtmodus automatisch (22–7 Uhr)"
        )
        val checked = booleanArrayOf(
            prefs.getBoolean("autoplay", false),
            prefs.getBoolean("autoNight", true)
        )
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Optionen · Wetter-Ort: ${placeName()}")
            .setMultiChoiceItems(labels, checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("OK") { _, _ ->
                prefs.edit()
                    .putBoolean("autoplay", checked[0])
                    .putBoolean("autoNight", checked[1])
                    .apply()
                lastAutoNight = null
                checkAutoNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
            }
            .setNeutralButton("Ort ändern") { _, _ -> askPlace() }
            .show()
    }

    private fun askPlace() {
        val et = input("Ort, z. B. Todtnau")
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Ort für das Wetter")
            .setView(padded(et))
            .setPositiveButton("Suchen") { _, _ ->
                val q = et.text.toString().trim()
                if (q.isNotEmpty()) searchPlace(q)
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun searchPlace(q: String) {
        val enc = URLEncoder.encode(q, "UTF-8")
        io.execute {
            val res: JSONArray? = try {
                JSONObject(httpGet("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=8&language=de&format=json"))
                    .optJSONArray("results")
            } catch (_: Exception) { null }
            main.post {
                if (res == null || res.length() == 0) {
                    toast("Ort nicht gefunden")
                    return@post
                }
                val labels = Array(res.length()) { i ->
                    val o = res.getJSONObject(i)
                    listOf(o.optString("name"), o.optString("admin1"), o.optString("country"))
                        .filter { it.isNotEmpty() }.joinToString(", ")
                }
                AlertDialog.Builder(this, dlgTheme)
                    .setTitle("Welcher Ort?")
                    .setItems(labels) { _, which ->
                        val o = res.getJSONObject(which)
                        prefs.edit()
                            .putString("placeName", o.optString("name"))
                            .putFloat("lat", o.getDouble("latitude").toFloat())
                            .putFloat("lon", o.getDouble("longitude").toFloat())
                            .apply()
                        loadWeather()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
    }

    // ---------------------------------------------------------------- Hilfen

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun httpGet(url: String): String {
        val c = URL(url).openConnection() as HttpURLConnection
        c.connectTimeout = 10000
        c.readTimeout = 15000
        c.setRequestProperty("User-Agent", "DC-Radiouhr/1.0")
        try {
            if (c.responseCode !in 200..299) throw IOException("HTTP ${c.responseCode}")
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }
}
