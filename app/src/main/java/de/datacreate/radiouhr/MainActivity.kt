package de.datacreate.radiouhr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.EditText
import android.widget.HorizontalScrollView
import android.widget.ImageView
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
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.Random
import java.util.TimeZone
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** icon: "" = noch nicht gesucht, "-" = keins gefunden, sonst Bild-URL */
data class Station(val name: String, val url: String, val icon: String = "")

class MainActivity : Activity() {

    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newFixedThreadPool(2)
    private val rnd = Random()
    private val icons = ConcurrentHashMap<String, Bitmap>()

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
    private lateinit var tvEvents: TextView
    private lateinit var tvStation: TextView
    private lateinit var tvNowPlaying: TextView
    private lateinit var favScroll: HorizontalScrollView
    private lateinit var favRow: LinearLayout
    private lateinit var btnPlay: TextView
    private lateinit var btnTimer: TextView
    private lateinit var btnNight: TextView
    private lateinit var knob: KnobView

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
    private val accent = Color.parseColor("#FF8A3D")

    // ================================================================ Lebenszyklus

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
        tvEvents = findViewById(R.id.tvEvents)
        tvStation = findViewById(R.id.tvStation)
        tvNowPlaying = findViewById(R.id.tvNowPlaying)
        favScroll = findViewById(R.id.favScroll)
        favRow = findViewById(R.id.favRow)
        btnPlay = findViewById(R.id.btnPlay)
        btnTimer = findViewById(R.id.btnTimer)
        btnNight = findViewById(R.id.btnNight)

        tvTime.typeface = Typeface.create("sans-serif-thin", Typeface.NORMAL)
        tvTemp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)

        setupPlayer()
        loadStations()

        btnPlay.setOnClickListener { if (wantPlaying) stopRadio() else playCurrent() }
        findViewById<View>(R.id.btnPrev).setOnClickListener { step(-1) }
        findViewById<View>(R.id.btnNext).setOnClickListener { step(1) }
        knob = findViewById(R.id.knob)
        knob.onChange = { v ->
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0)
        }
        syncKnob()
        btnTimer.setOnClickListener { showTimer() }
        btnNight.setOnClickListener { setNight(!night) }
        findViewById<View>(R.id.btnSettings).setOnClickListener { showSettings() }
        clockPanel.setOnClickListener { setNight(!night) }

        showStationName()
        loadIcons()
        applyOrientation(resources.configuration)
        setNight(false)
        askPermissionsIfNeeded()

        if (prefs.getBoolean("autoplay", false) && stations.isNotEmpty()) playCurrent()
    }

    override fun onResume() {
        super.onResume()
        hideSystemUi()
        main.removeCallbacks(tick)
        main.post(tick)
        main.removeCallbacks(weatherLoop)
        main.post(weatherLoop)
        loadEvents()
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

    // ================================================================ Berechtigungen

    private fun hasPerm(p: String): Boolean =
        Build.VERSION.SDK_INT < 23 || checkSelfPermission(p) == PackageManager.PERMISSION_GRANTED

    private fun hasLocationPerm() =
        hasPerm(Manifest.permission.ACCESS_COARSE_LOCATION) || hasPerm(Manifest.permission.ACCESS_FINE_LOCATION)

    private fun askPermissionsIfNeeded() {
        if (Build.VERSION.SDK_INT < 23) return
        val need = mutableListOf<String>()
        if (prefs.getBoolean("useGps", true) && !hasLocationPerm()) {
            need.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            need.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (prefs.getBoolean("showEvents", true) && !hasPerm(Manifest.permission.READ_CALENDAR)) {
            need.add(Manifest.permission.READ_CALENDAR)
        }
        if (need.isNotEmpty()) requestPermissions(need.toTypedArray(), 1)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        refreshWeather()
        loadEvents()
    }

    // ================================================================ Layout

    private fun applyOrientation(cfg: Configuration) {
        val dm = resources.displayMetrics
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            root.orientation = LinearLayout.HORIZONTAL
            clockPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.3f)
            radioPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
            val panelW = dm.widthPixels * 0.55f
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, minOf(panelW / 3.0f, dm.heightPixels * 0.33f))
        } else {
            root.orientation = LinearLayout.VERTICAL
            clockPanel.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            radioPanel.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            )
            tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, dm.widthPixels / 3.2f)
        }
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    // ================================================================ Uhr & Nachtmodus

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            tvTime.text = timeFmt.format(now)
            tvDate.text = dateFmt.format(now)

            val cal = Calendar.getInstance()
            val minute = cal.get(Calendar.MINUTE)
            if (minute != lastMinute) {
                val first = lastMinute == -1
                lastMinute = minute
                // leichtes Verschieben gegen Einbrennen
                clockPanel.translationX = (rnd.nextInt(13) - 6) * dp(1)
                clockPanel.translationY = (rnd.nextInt(13) - 6) * dp(1)
                checkAutoNight(cal.get(Calendar.HOUR_OF_DAY))
                if (!first) loadEvents()
            }

            syncKnob()

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

    /** Drehrad an Systemlautstärke angleichen (z. B. nach Lautstärketasten). */
    private fun syncKnob() {
        if (knob.dragging) return
        val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
        if (max > 0) knob.value = audio.getStreamVolume(AudioManager.STREAM_MUSIC).toFloat() / max
    }

    override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
        val r = super.dispatchKeyEvent(event)
        main.postDelayed({ syncKnob() }, 100)
        return r
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
        tvEvents.setTextColor(if (on) sub else Color.parseColor("#DDDDDD"))
        tvWeatherIcon.setTextColor(if (on) sub else Color.parseColor("#FFD27A"))
        radioPanel.alpha = if (on) 0.4f else 1f
        knob.accent = if (on) Color.parseColor("#B8432F") else accent
        btnNight.text = if (on) "Tag" else "Nacht"
        val lp = window.attributes
        lp.screenBrightness = if (on) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
    }

    // ================================================================ Wetter

    private val weatherLoop = object : Runnable {
        override fun run() {
            refreshWeather()
            main.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun placeName() = prefs.getString("placeName", "Todtnau") ?: "Todtnau"

    private fun refreshWeather() {
        if (prefs.getBoolean("useGps", true) && hasLocationPerm()) {
            findLocation { loc ->
                if (loc == null) {
                    fetchWeather(prefs.getFloat("lat", 47.8295f).toDouble(), prefs.getFloat("lon", 7.9447f).toDouble(), placeName())
                } else {
                    io.execute {
                        val name = reverseName(loc.latitude, loc.longitude)
                        if (name != null) prefs.edit().putString("gpsName", name).apply()
                        val label = name ?: prefs.getString("gpsName", null) ?: "Aktueller Standort"
                        main.post { fetchWeather(loc.latitude, loc.longitude, label) }
                    }
                }
            }
        } else {
            fetchWeather(prefs.getFloat("lat", 47.8295f).toDouble(), prefs.getFloat("lon", 7.9447f).toDouble(), placeName())
        }
    }

    @SuppressLint("MissingPermission")
    private fun findLocation(cb: (Location?) -> Unit) {
        val lm = getSystemService(LOCATION_SERVICE) as LocationManager
        var best: Location? = null
        try {
            for (p in lm.getProviders(true)) {
                val l = lm.getLastKnownLocation(p) ?: continue
                val b = best
                if (b == null || l.time > b.time) best = l
            }
        } catch (_: Exception) { }
        val known = best
        if (known != null && System.currentTimeMillis() - known.time < 30 * 60_000L) {
            cb(known)
            return
        }
        val provider = when {
            lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
            lm.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
            else -> null
        }
        if (provider == null) {
            cb(known)
            return
        }
        var done = false
        val listener = object : LocationListener {
            override fun onLocationChanged(location: Location) {
                if (!done) {
                    done = true
                    cb(location)
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) { }
            override fun onProviderEnabled(provider: String) { }
            override fun onProviderDisabled(provider: String) { }
        }
        try {
            @Suppress("DEPRECATION")
            lm.requestSingleUpdate(provider, listener, Looper.getMainLooper())
        } catch (_: Exception) {
            cb(known)
            return
        }
        main.postDelayed({
            if (!done) {
                done = true
                try { lm.removeUpdates(listener) } catch (_: Exception) { }
                cb(known)
            }
        }, 30_000L)
    }

    private fun reverseName(lat: Double, lon: Double): String? {
        // 1. Android-Geocoder
        try {
            if (Geocoder.isPresent()) {
                @Suppress("DEPRECATION")
                val a = Geocoder(this, Locale.GERMANY).getFromLocation(lat, lon, 1)
                val first = a?.firstOrNull()
                val n = first?.locality ?: first?.subAdminArea
                if (!n.isNullOrBlank()) return n
            }
        } catch (_: Exception) { }
        // 2. Fallback: OpenStreetMap Nominatim
        return try {
            val j = JSONObject(
                httpGet("https://nominatim.openstreetmap.org/reverse?format=json&zoom=10&accept-language=de&lat=$lat&lon=$lon")
            ).optJSONObject("address")
            j?.optString("village")?.ifEmpty { null }
                ?: j?.optString("town")?.ifEmpty { null }
                ?: j?.optString("city")?.ifEmpty { null }
                ?: j?.optString("municipality")?.ifEmpty { null }
        } catch (_: Exception) { null }
    }

    private fun fetchWeather(lat: Double, lon: Double, place: String) {
        val url = String.format(
            Locale.US,
            "https://api.open-meteo.com/v1/forecast?latitude=%.4f&longitude=%.4f" +
                "&current=temperature_2m,apparent_temperature,weather_code,wind_speed_10m,is_day" +
                "&daily=temperature_2m_max,temperature_2m_min&timezone=auto&forecast_days=1",
            lat, lon
        )
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
        0, 1, 2 -> if (day) "☀" else "☾"
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

    // ================================================================ Termine

    private fun loadEvents() {
        if (!prefs.getBoolean("showEvents", true) || !hasPerm(Manifest.permission.READ_CALENDAR)) {
            tvEvents.visibility = View.GONE
            return
        }
        io.execute {
            val lines = try { queryToday() } catch (_: Exception) { null }
            main.post {
                if (lines == null) {
                    tvEvents.visibility = View.GONE
                } else {
                    tvEvents.text = if (lines.isEmpty()) "Heute keine Termine mehr" else lines.joinToString("\n")
                    tvEvents.visibility = View.VISIBLE
                }
            }
        }
    }

    private fun queryToday(): List<String> {
        val day = 24 * 60 * 60_000L
        val cal = Calendar.getInstance()
        val y = cal.get(Calendar.YEAR)
        val m = cal.get(Calendar.MONTH)
        val d = cal.get(Calendar.DAY_OF_MONTH)
        cal.set(y, m, d, 0, 0, 0)
        cal.set(Calendar.MILLISECOND, 0)
        val start = cal.timeInMillis
        val end = start + day
        val now = System.currentTimeMillis()
        val todayUtc = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(y, m, d)
        }.timeInMillis

        val b = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(b, start - day)
        ContentUris.appendId(b, end + day)
        val proj = arrayOf(
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY
        )
        val found = mutableListOf<Pair<Long, String>>()
        contentResolver.query(b.build(), proj, null, null, CalendarContract.Instances.BEGIN + " ASC")?.use { c ->
            while (c.moveToNext()) {
                val title = c.getString(0)?.trim().takeUnless { it.isNullOrEmpty() } ?: "(ohne Titel)"
                val begin = c.getLong(1)
                val stop = c.getLong(2)
                val allDay = c.getInt(3) == 1
                if (allDay) {
                    if (todayUtc >= begin && todayUtc < stop) found.add(Long.MIN_VALUE to "ganztägig · $title")
                } else if (begin < end && stop > now) {
                    val label = if (begin < now) "jetzt" else timeFmt.format(Date(begin))
                    found.add(begin to "$label  $title")
                }
            }
        }
        val sorted = found.sortedBy { it.first }.map { it.second }.distinct()
        return if (sorted.size > 4) sorted.take(3) + "+ ${sorted.size - 3} weitere" else sorted
    }

    // ================================================================ Radio

    private val retry = Runnable { if (wantPlaying) playCurrent() }

    private fun setupPlayer() {
        val http = DefaultHttpDataSource.Factory()
            .setAllowCrossProtocolRedirects(true)
            .setUserAgent("DC-Radiouhr/1.1")
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
        buildFavorites()
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
        tvStation.text = if (stations.isEmpty()) "Keine Sender – tippe auf ＋"
        else stations[current.coerceIn(0, stations.size - 1)].name
        buildFavorites()
    }

    // ================================================================ Favoriten-Kacheln

    private fun shortLabel(name: String): String {
        val words = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return "?"
        if (words[0].length <= 5) return words[0].uppercase(Locale.GERMANY)
        return words.take(3).joinToString("") { it.take(1) }.uppercase(Locale.GERMANY)
    }

    private fun colorFor(name: String): Int {
        val palette = intArrayOf(
            0xFFE8572A.toInt(), 0xFF2A7FE8.toInt(), 0xFF2AA876.toInt(), 0xFF9B4DCA.toInt(),
            0xFFD4A017.toInt(), 0xFFCC3D6B.toInt(), 0xFF3AA6B9.toInt(), 0xFF6B7A8F.toInt()
        )
        return palette[(name.hashCode() and 0x7fffffff) % palette.size]
    }

    private fun tile(size: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(14)
        setColor(Color.WHITE)
    }

    private fun buildFavorites() {
        if (!::favRow.isInitialized) return
        favRow.removeAllViews()
        val size = dp(52).toInt()
        val itemW = dp(70).toInt()
        val active = current.coerceIn(0, maxOf(0, stations.size - 1))

        stations.forEachIndexed { i, s ->
            val item = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
                val p = dp(4).toInt()
                setPadding(p, p, p, p)
            }
            val bmp = icons[s.url]
            val iconView: View = if (bmp != null) {
                ImageView(this).apply {
                    setImageBitmap(bmp)
                    scaleType = ImageView.ScaleType.FIT_CENTER
                    val p = dp(5).toInt()
                    setPadding(p, p, p, p)
                    background = tile(size)
                }
            } else {
                TextView(this).apply {
                    text = shortLabel(s.name)
                    gravity = Gravity.CENTER
                    setTextColor(Color.WHITE)
                    setTypeface(typeface, Typeface.BOLD)
                    textSize = 14f
                    background = GradientDrawable().apply {
                        cornerRadius = dp(14)
                        setColor(colorFor(s.name))
                    }
                }
            }
            if (i == active) {
                val bg = iconView.background
                if (bg is GradientDrawable) bg.setStroke(dp(3).toInt(), if (wantPlaying) accent else Color.GRAY)
            }
            if (Build.VERSION.SDK_INT >= 21) iconView.clipToOutline = true
            item.addView(iconView, LinearLayout.LayoutParams(size, size))

            val caption = TextView(this).apply {
                text = s.name
                textSize = 11f
                setTextColor(if (i == active) Color.WHITE else Color.parseColor("#999999"))
                gravity = Gravity.CENTER
                setSingleLine(true)
                ellipsize = TextUtils.TruncateAt.END
            }
            item.addView(caption, LinearLayout.LayoutParams(itemW - dp(8).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
            item.setOnClickListener {
                if (i == current && wantPlaying) stopRadio() else {
                    current = i
                    playCurrent()
                }
            }
            item.setOnLongClickListener {
                confirmRemove(i)
                true
            }
            favRow.addView(item, LinearLayout.LayoutParams(itemW, ViewGroup.LayoutParams.WRAP_CONTENT))
        }

        // Kachel „+“ zum Hinzufügen / Verwalten
        val add = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = dp(4).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { showStations() }
        }
        val plus = TextView(this).apply {
            text = "＋"
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(Color.WHITE)
            background = GradientDrawable().apply {
                cornerRadius = dp(14)
                setColor(0x22FFFFFF)
                setStroke(dp(1).toInt(), 0x55FFFFFF)
            }
        }
        add.addView(plus, LinearLayout.LayoutParams(size, size))
        add.addView(TextView(this).apply {
            text = "Sender"
            textSize = 11f
            gravity = Gravity.CENTER
            setTextColor(Color.parseColor("#999999"))
        }, LinearLayout.LayoutParams(itemW - dp(8).toInt(), ViewGroup.LayoutParams.WRAP_CONTENT))
        favRow.addView(add, LinearLayout.LayoutParams(itemW, ViewGroup.LayoutParams.WRAP_CONTENT))

        // aktiven Sender sichtbar halten
        favScroll.post {
            val child = favRow.getChildAt(active) ?: return@post
            val target = child.left - (favScroll.width - child.width) / 2
            favScroll.smoothScrollTo(maxOf(0, target), 0)
        }
    }

    private fun loadIcons() {
        val list = stations.toList()
        io.execute {
            var changed = false
            for (s in list) {
                if (icons.containsKey(s.url)) continue
                var iconUrl = s.icon
                if (iconUrl.isEmpty()) {
                    iconUrl = lookupFavicon(s.name).ifEmpty { "-" }
                    val found = iconUrl
                    main.post {
                        val idx = stations.indexOfFirst { it.url == s.url }
                        if (idx >= 0) {
                            stations[idx] = stations[idx].copy(icon = found)
                            saveStations()
                        }
                    }
                }
                if (iconUrl == "-") continue
                val bmp = loadBitmap(iconUrl) ?: continue
                icons[s.url] = bmp
                changed = true
            }
            if (changed) main.post { buildFavorites() }
        }
    }

    private fun lookupFavicon(name: String): String {
        return try {
            val arr = radioBrowser(
                "/json/stations/search?name=${URLEncoder.encode(name, "UTF-8")}&limit=10&order=clickcount&reverse=true"
            ) ?: return ""
            var fallback = ""
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val fav = o.optString("favicon")
                if (fav.isEmpty()) continue
                if (o.optString("name").trim().equals(name.trim(), ignoreCase = true)) return fav
                if (fallback.isEmpty()) fallback = fav
            }
            fallback
        } catch (_: Exception) { "" }
    }

    private fun loadBitmap(url: String): Bitmap? {
        val f = File(cacheDir, "ic_" + Integer.toHexString(url.hashCode()))
        try {
            val bytes = if (f.exists()) f.readBytes() else {
                val b = httpGetBytes(url, 1_500_000)
                f.writeBytes(b)
                b
            }
            val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
            var sample = 1
            while (opts.outWidth / sample > 256 || opts.outHeight / sample > 256) sample *= 2
            val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, BitmapFactory.Options().apply { inSampleSize = sample })
            if (bmp == null) f.delete()
            return bmp
        } catch (_: Exception) {
            f.delete()
            return null
        }
    }

    // ================================================================ Senderliste

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
                    stations.add(Station(o.getString("name"), o.getString("url"), o.optString("icon", "")))
                }
            } catch (_: Exception) { }
        }
        current = prefs.getInt("current", 0)
    }

    private fun saveStations() {
        val arr = JSONArray()
        for (s in stations) arr.put(JSONObject().put("name", s.name).put("url", s.url).put("icon", s.icon))
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
            .setMessage("„${s.name}“ aus den Favoriten entfernen?")
            .setPositiveButton("Entfernen") { _, _ ->
                val wasCurrent = pos == current
                stations.removeAt(pos)
                icons.remove(s.url)
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
        loadIcons()
    }

    private fun input(hint: String, type: Int = InputType.TYPE_CLASS_TEXT): EditText =
        EditText(this).apply {
            this.hint = hint
            inputType = type
            setSingleLine(true)
            setTextColor(Color.WHITE)
            setHintTextColor(Color.GRAY)
        }

    private fun padded(vararg views: View): View = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        val p = dp(20).toInt()
        setPadding(p, p / 2, p, 0)
        for (v in views) addView(v)
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

    private fun radioBrowser(path: String): JSONArray? {
        val servers = listOf(
            "https://de1.api.radio-browser.info",
            "https://fi1.api.radio-browser.info",
            "https://de2.api.radio-browser.info"
        )
        for (srv in servers) {
            try {
                return JSONArray(httpGet(srv + path))
            } catch (_: Exception) { }
        }
        return null
    }

    private fun searchStations(q: String) {
        toast("Suche „$q“ …")
        val enc = URLEncoder.encode(q, "UTF-8")
        io.execute {
            val result = radioBrowser("/json/stations/search?name=$enc&limit=40&hidebroken=true&order=clickcount&reverse=true")
            main.post {
                if (result == null) {
                    toast("Sendersuche nicht erreichbar")
                    return@post
                }
                val found = mutableListOf<Station>()
                val labels = mutableListOf<String>()
                for (i in 0 until result.length()) {
                    val o = result.getJSONObject(i)
                    val url = o.optString("url_resolved").ifEmpty { o.optString("url") }
                    val name = o.optString("name").trim()
                    if (url.isEmpty() || name.isEmpty()) continue
                    found.add(Station(name, url, o.optString("favicon").ifEmpty { "-" }))
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
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Sender per URL hinzufügen")
            .setView(padded(name, url))
            .setPositiveButton("Hinzufügen") { _, _ ->
                val u = url.text.toString().trim()
                val n = name.text.toString().trim().ifEmpty { u }
                if (u.startsWith("http")) addStation(Station(n, u)) else toast("Ungültige URL")
            }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    // ================================================================ Timer & Optionen

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
            "Wetter für aktuellen Standort",
            "Heutige Termine anzeigen",
            "Nachtmodus automatisch (22–7 Uhr)",
            "Radio beim Start automatisch abspielen"
        )
        val checked = booleanArrayOf(
            prefs.getBoolean("useGps", true),
            prefs.getBoolean("showEvents", true),
            prefs.getBoolean("autoNight", true),
            prefs.getBoolean("autoplay", false)
        )
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Optionen")
            .setMultiChoiceItems(labels, checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("OK") { _, _ ->
                prefs.edit()
                    .putBoolean("useGps", checked[0])
                    .putBoolean("showEvents", checked[1])
                    .putBoolean("autoNight", checked[2])
                    .putBoolean("autoplay", checked[3])
                    .apply()
                lastAutoNight = null
                checkAutoNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
                askPermissionsIfNeeded()
                refreshWeather()
                loadEvents()
            }
            .setNeutralButton("Festen Ort wählen") { _, _ -> askPlace() }
            .show()
    }

    private fun askPlace() {
        val et = input("Ort, z. B. Todtnau")
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Fester Ort für das Wetter")
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
                            .putBoolean("useGps", false)
                            .apply()
                        refreshWeather()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
    }

    // ================================================================ Hilfen

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun open(url: String): HttpURLConnection {
        var u = URL(url)
        var redirects = 0
        while (true) {
            val c = u.openConnection() as HttpURLConnection
            c.connectTimeout = 10000
            c.readTimeout = 15000
            c.instanceFollowRedirects = true
            c.setRequestProperty("User-Agent", "DC-Radiouhr/1.1 (Android)")
            val code = c.responseCode
            if (code in 300..399 && redirects < 5) {
                val loc = c.getHeaderField("Location")
                c.disconnect()
                if (loc == null) throw IOException("Redirect ohne Ziel")
                u = URL(u, loc)
                redirects++
                continue
            }
            if (code !in 200..299) {
                c.disconnect()
                throw IOException("HTTP $code")
            }
            return c
        }
    }

    private fun httpGet(url: String): String {
        val c = open(url)
        try {
            return c.inputStream.bufferedReader().use { it.readText() }
        } finally {
            c.disconnect()
        }
    }

    private fun httpGetBytes(url: String, max: Int): ByteArray {
        val c = open(url)
        try {
            c.inputStream.use { input ->
                val out = ByteArrayOutputStream()
                val buf = ByteArray(8192)
                while (true) {
                    val n = input.read(buf)
                    if (n < 0) break
                    out.write(buf, 0, n)
                    if (out.size() > max) throw IOException("zu groß")
                }
                return out.toByteArray()
            }
        } finally {
            c.disconnect()
        }
    }
}
