package de.datacreate.radiouhr

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
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
data class DayForecast(
    val label: String, val code: Int, val max: Double, val min: Double, val wind: Double, val rain: Double,
    val sun: Double = Double.NaN, val cat: String? = null
)

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
    private lateinit var btnPlay: ImageView
    private lateinit var btnTimer: View
    private lateinit var tvTimerBadge: TextView
    private lateinit var btnNight: View
    private lateinit var btnSettings: ImageView
    private lateinit var btnAdd: ImageView
    private lateinit var btnGear: ImageView
    private lateinit var weatherBox: View
    private lateinit var wxDivider: View
    private lateinit var weatherRow: View
    private lateinit var forecastRow: LinearLayout
    private lateinit var favGrid: LinearLayout
    private lateinit var timerIcon: ImageView
    private lateinit var clockView: ClockView
    private lateinit var dialView: DialView
    private lateinit var skin: Skin
    private var lastForecast: List<DayForecast> = emptyList()
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
        tvTimerBadge = findViewById(R.id.tvTimerBadge)
        btnNight = findViewById(R.id.btnNight)
        weatherRow = findViewById(R.id.weatherRow)
        forecastRow = findViewById(R.id.forecastRow)
        favGrid = findViewById(R.id.favGrid)
        timerIcon = findViewById(R.id.timerIcon)
        clockView = findViewById(R.id.clockView)
        dialView = findViewById(R.id.dialView)
        dialView.onPick = { i -> stationClick(i) }
        clockView.setOnClickListener { setNight(!night) }
        skin = loadSkin()


        setupPlayer()
        loadStations()

        btnPlay.setOnClickListener { if (wantPlaying) stopRadio() else playCurrent() }
        tvStation.setOnClickListener { if (wantPlaying) stopRadio() else playCurrent() }
        tvStation.setOnLongClickListener {
            showStations()
            true
        }
        knob = findViewById(R.id.knob)
        knob.onChange = { v ->
            val max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC)
            audio.setStreamVolume(AudioManager.STREAM_MUSIC, Math.round(v * max), 0)
        }
        syncKnob()
        btnTimer.setOnClickListener { showTimer() }
        btnNight.setOnClickListener { setNight(!night) }
        btnSettings = findViewById(R.id.btnSettings)
        btnSettings.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        btnGear = findViewById(R.id.btnGear)
        btnGear.setOnClickListener { startActivity(Intent(this, SettingsActivity::class.java)) }
        weatherBox = findViewById(R.id.weatherBox)
        wxDivider = findViewById(R.id.wxDivider)
        weatherBox.setOnClickListener { startActivity(Intent(this, WeatherActivity::class.java)) }
        tvTemp.setOnClickListener { startActivity(Intent(this, WeatherActivity::class.java)) }
        btnAdd = findViewById(R.id.btnAdd)
        btnAdd.setOnClickListener { showStations() }
        clockPanel.setOnClickListener { setNight(!night) }
        root.setOnClickListener { if (night) setNight(false) }

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
        skin = loadSkin()
        lastAutoNight = null
        applyLook()
        checkAutoNight(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))
        showWeather(prefs.getString("wxPlaceName", placeName()) ?: placeName())
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
        buildFavorites()
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
        val ts: Float
        val availH: Float
        if (cfg.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            root.orientation = LinearLayout.HORIZONTAL
            clockPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1.3f)
            radioPanel.layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply {
                leftMargin = dp(8).toInt()
            }
            val panelW = dm.widthPixels * 0.55f
            ts = skin.clockScale * (if (night) minOf(dm.widthPixels / 2.7f, dm.heightPixels * 0.7f)
            else minOf(panelW / 2.55f, dm.heightPixels * 0.42f))
            availH = dm.heightPixels * (if (night) 0.92f else 0.86f)
        } else {
            root.orientation = LinearLayout.VERTICAL
            clockPanel.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
            radioPanel.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = dp(8).toInt() }
            ts = skin.clockScale * dm.widthPixels / (if (night) 2.5f else 2.6f)
            availH = dm.heightPixels * (if (night) 0.85f else 0.55f)
        }
        tvTime.setTextSize(TypedValue.COMPLEX_UNIT_PX, ts)
        val ch = when (skin.clock) {
            ClockStyle.CUCKOO -> availH
            ClockStyle.ANALOG_BRAUN, ClockStyle.ANALOG_WOOD, ClockStyle.STATION -> minOf(ts * 2.1f, availH)
            else -> minOf(ts * 1.3f, availH)
        }
        clockView.layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ch.toInt())
    }

    private fun dp(v: Int): Float = v * resources.displayMetrics.density

    // ================================================================ Uhr & Nachtmodus

    private val tick = object : Runnable {
        override fun run() {
            val now = Date()
            tvTime.text = timeFmt.format(now)
            tvDate.text = dateFmt.format(now)

            val cal = Calendar.getInstance()
            clockView.setTime(cal)
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
                    tvTimerBadge.text = ""
                } else {
                    tvTimerBadge.text = "${(left / 60000) + 1}′"
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
        val should = nightAt(hour)
        if (lastAutoNight != should) {
            lastAutoNight = should
            setNight(should)
        }
    }

    private fun nightAt(h: Int): Boolean {
        val from = prefs.getInt("nightFrom", 22)
        val to = prefs.getInt("nightTo", 7)
        return if (from == to) false else if (from < to) h in from until to else (h >= from || h < to)
    }

    private fun isNightHour(): Boolean = nightAt(Calendar.getInstance().get(Calendar.HOUR_OF_DAY))

    private val backToNight = Runnable { if (!night && isNightHour()) setNight(true) }

    private fun setNight(on: Boolean) {
        night = on
        applyLook()
        val lp = window.attributes
        lp.screenBrightness = if (on) 0.01f else WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        window.attributes = lp
        // nachts kurz angetippt: nach 1 Minute wieder nur Uhr
        main.removeCallbacks(backToNight)
        if (!on && prefs.getBoolean("autoNight", true) && isNightHour()) main.postDelayed(backToNight, 60_000L)
    }

    private fun vis(show: Boolean) = if (show) View.VISIBLE else View.GONE

    private fun shape(fill: Int, stroke: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = dp(1) * skin.corner
        setColor(fill)
        setStroke(maxOf(1, dp(1).toInt()), stroke)
    }

    private fun btnBg(primary: Boolean = false, style: BtnStyle = skin.btn, latched: Boolean = false): StateListDrawable {
        val den = resources.displayMetrics.density
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_pressed), ButtonDrawable(style, skin, true, primary, den))
            addState(intArrayOf(), ButtonDrawable(style, skin, latched, primary, den))
        }
    }

    /** Design (Skin) und Tag/Nacht auf alle Elemente anwenden. */
    private fun applyLook() {
        val k = skin
        val on = night
        val den = resources.displayMetrics.density
        root.background = if (on) ColorDrawable(Color.BLACK) else BgDrawable(k.bg, k, den)
        clockPanel.background = if (on || k.clockPanel == PanelStyle.NONE) null else PanelDrawable(k.clockPanel, k, den)
        radioPanel.background = if (k.radioPanel == PanelStyle.NONE) null else PanelDrawable(k.radioPanel, k, den)
        val cp = if (!on && k.clockPanel != PanelStyle.NONE) dp(16).toInt() else 0
        clockPanel.setPadding(cp, cp, cp, cp)
        val rp = if (k.radioPanel != PanelStyle.NONE) dp(12).toInt() else 0
        radioPanel.setPadding(rp, rp, rp, rp)

        // Uhr
        val drawn = k.clock != ClockStyle.TEXT
        tvTime.visibility = vis(!drawn)
        clockView.visibility = vis(drawn)
        clockView.style = k.clock
        clockView.litColor = if (on) Color.parseColor("#B8432F") else k.fg
        clockView.alpha = if (on && (k.clock == ClockStyle.ANALOG_BRAUN || k.clock == ClockStyle.FLIP || k.clock == ClockStyle.CUCKOO || k.clock == ClockStyle.STATION)) 0.35f
        else if (on) 0.6f else 1f
        tvTime.typeface = Typeface.create(k.clockFamily, k.clockStyle)
        if (k.textGlow && !on) tvTime.setShadowLayer(dp(14), 0f, 0f, k.accent) else tvTime.setShadowLayer(0f, 0f, 0f, 0)

        val tf = Typeface.create(k.textFamily, Typeface.NORMAL)
        tvTemp.typeface = tf
        tvDate.typeface = tf
        tvWeatherInfo.typeface = tf
        tvEvents.typeface = tf
        tvTime.setTextColor(if (on) Color.parseColor("#B8432F") else k.fg)
        tvTemp.setTextColor(k.fg)
        tvDate.setTextColor(k.sub)
        tvWeatherInfo.setTextColor(k.sub)
        tvNowPlaying.setTextColor(k.sub)
        tvEvents.setTextColor(k.fg)
        tvWeatherIcon.setTextColor(k.icon)
        tvTimerBadge.setTextColor(if (k.btn == BtnStyle.IVORY || k.btn == BtnStyle.ROUND) col("#B5300B") else k.accent)
        if (k.textGlow) {
            tvTemp.setShadowLayer(dp(8), 0f, 0f, k.accent)
        } else {
            tvTemp.setShadowLayer(0f, 0f, 0f, 0)
        }

        // Sender-Anzeige
        dialView.visibility = vis(k.station == StationStyle.DIAL)
        tvStation.visibility = vis(k.station != StationStyle.DIAL)
        if (k.station == StationStyle.LCD) {
            tvStation.typeface = Typeface.create("monospace", Typeface.BOLD)
            tvStation.isAllCaps = true
            tvStation.setTextColor(k.accent)
            tvStation.setShadowLayer(dp(4), 0f, 0f, k.accent)
            tvStation.background = GradientDrawable().apply {
                cornerRadius = dp(6)
                setColor(k.lcdScreen)
                setStroke(dp(2).toInt(), col("#000000"))
            }
            val pd = dp(8).toInt()
            tvStation.setPadding(pd, pd / 2, pd, pd / 2)
        } else {
            tvStation.typeface = Typeface.create(k.textFamily, Typeface.BOLD)
            tvStation.isAllCaps = false
            tvStation.setTextColor(k.fg)
            tvStation.setShadowLayer(if (k.textGlow) dp(6) else 0f, 0f, 0f, if (k.textGlow) k.accent else 0)
            tvStation.background = null
            tvStation.setPadding(0, 0, 0, 0)
        }

        radioPanel.visibility = vis(!on)
        tvDate.visibility = vis(!on)
        val fc = k.forecast && prefs.getBoolean("wxForecast", true)
        weatherBox.visibility = vis(!on)
        weatherRow.visibility = vis(!on)
        tvWeatherInfo.visibility = vis(!on && k.details && tvWeatherInfo.text.isNotEmpty())
        forecastRow.visibility = vis(!on && fc)
        wxDivider.visibility = vis(!on && fc)
        wxDivider.setBackgroundColor(Color.argb(70, Color.red(k.sub), Color.green(k.sub), Color.blue(k.sub)))
        btnGear.visibility = vis(!on)
        btnGear.setImageDrawable(GearDrawable(if (k.btn == BtnStyle.ROUND) col("#2A2A2A") else k.iconTint))
        tvNowPlaying.visibility = vis(k.details)
        favScroll.visibility = View.GONE
        favGrid.visibility = vis(k.presets != Presets.NONE)
        btnTimer.visibility = vis(k.extraButtons)
        btnNight.visibility = vis(k.extraButtons)
        if (on || !k.events) tvEvents.visibility = View.GONE else loadEvents()

        // Tasten
        btnPlay.background = btnBg(primary = true)
        btnTimer.background = btnBg()
        btnNight.background = btnBg()
        btnSettings.background = btnBg()
        btnAdd.background = btnBg()
        btnAdd.setColorFilter(if (k.btn == BtnStyle.ROUND) col("#2A2A2A") else k.iconTint)
        val tint = if (k.btn == BtnStyle.ROUND) col("#2A2A2A") else k.iconTint
        btnPlay.setColorFilter(if (k.btn == BtnStyle.ROUND) Color.WHITE else tint)
        timerIcon.setColorFilter(tint)
        (btnNight as ImageView).setColorFilter(tint)
        btnSettings.setColorFilter(tint)
        val ip = if (k.btn == BtnStyle.IVORY) dp(3).toInt() else 0
        for (v in listOf<View>(btnPlay, btnNight, btnSettings, btnAdd, timerIcon)) {
            v.setPadding(dp(10).toInt(), dp(10).toInt() - ip, dp(10).toInt(), dp(10).toInt() + ip)
        }

        knob.style = k.knob
        knob.accent = k.accent
        knob.bodyColor = k.knobBody
        knob.trackColor = k.knobTrack
        knob.dotColor = k.fg

        buildFavorites()
        showForecast(lastForecast)
        applyOrientation(resources.configuration)
    }

    private fun showSkins() {
        val list = Skins.all
        val idx = list.indexOfFirst { it.id == skin.id }
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Design wählen")
            .setSingleChoiceItems(list.map { "${it.name}  –  ${it.desc}" }.toTypedArray(), idx) { d, w ->
                skin = list[w]
                prefs.edit().putString("skin", skin.id).apply()
                applyLook()
                d.dismiss()
            }
            .setNegativeButton("Schließen", null)
            .show()
    }

    // ================================================================ Wetter

    private val weatherLoop = object : Runnable {
        override fun run() {
            refreshWeather()
            main.postDelayed(this, 15 * 60 * 1000L)
        }
    }

    private fun loadSkin(): Skin {
        val base = Skins.byId(prefs.getString("skin", "standard"))
        val o = prefs.getString("clockStyle", "") ?: ""
        return if (o.isEmpty()) base else try { base.copy(clock = ClockStyle.valueOf(o)) } catch (_: Exception) { base }
    }

    private fun placeName() = prefs.getString("placeName", "Todtnau") ?: "Todtnau"

    private fun refreshWeather() {
        val ntPlace = if (prefs.getBoolean("ntPlace", false) && Netatmo.connected(prefs)) Netatmo.place(prefs) else null
        if (ntPlace != null) {
            fetchWeather(ntPlace.first, ntPlace.second, ntPlace.third)
        } else if (prefs.getBoolean("useGps", true) && hasLocationPerm()) {
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
        prefs.edit()
            .putFloat("wxLat", lat.toFloat())
            .putFloat("wxLon", lon.toFloat())
            .putString("wxPlaceName", place)
            .apply()
        io.execute {
            var ok = false
            try {
                val json = Net.get(Wx.forecastUrl(lat, lon))
                JSONObject(json)
                prefs.edit().putString("wxJson", json).putLong("wxTime", System.currentTimeMillis()).apply()
                ok = true
            } catch (_: Exception) { }
            if (prefs.getString("wxSource", "meta") == "meta") {
                try {
                    if (Meta.fetch(prefs, lat, lon) != null) ok = true
                } catch (_: Exception) { }
            }
            if (Netatmo.connected(prefs) && System.currentTimeMillis() - prefs.getLong("ntTime", 0L) > 4 * 60_000L) {
                try { Netatmo.fetch(prefs) } catch (_: Exception) { }
            }
            main.post {
                showWeather(place)
                if (!ok) {
                    main.removeCallbacks(weatherLoop)
                    main.postDelayed(weatherLoop, 60 * 1000L)
                }
            }
        }
    }

    /** Wetter aus dem Zwischenspeicher anzeigen (Open-Meteo + ggf. Netatmo). */
    private fun showWeather(place: String) {
        val j = try { prefs.getString("wxJson", null)?.let { JSONObject(it) } } catch (_: Exception) { null }
        val cur = j?.optJSONObject("current")
        val daily = j?.optJSONObject("daily")
        if (cur == null) {
            tvWeatherInfo.text = "$place\nWetter nicht abrufbar"
            return
        }
        var temp = cur.optDouble("temperature_2m", Double.NaN)
        var hum = cur.optDouble("relative_humidity_2m", Double.NaN)
        val code = cur.optInt("weather_code", -1)
        var icon = Wx.icon(code, cur.optInt("is_day", 1) == 1)
        val meta = if (prefs.getString("wxSource", "meta") == "meta") Meta.cached(prefs) else null
        val metaNow = meta?.let { Meta.now(it) }
        if (meta != null && metaNow != null && !metaNow.temp.isNaN()) {
            temp = metaNow.temp
            if (!metaNow.rh.isNaN()) hum = metaNow.rh
            val h = Meta.localHour(meta, metaNow.t)
            if (metaNow.cat != null) icon = Meta.icon(metaNow.cat, h < 7 || h >= 19)
        }
        var fromNetatmo = false
        var indoor = Double.NaN
        if (Netatmo.connected(prefs)) {
            val dev = Netatmo.cached(prefs)
            if (dev != null) {
                val od = Netatmo.outdoor(dev)?.optJSONObject("dashboard_data")
                if (prefs.getBoolean("ntOutdoor", true) && od != null && !od.optDouble("Temperature", Double.NaN).isNaN()) {
                    temp = od.optDouble("Temperature")
                    hum = od.optDouble("Humidity", hum)
                    fromNetatmo = true
                }
                indoor = dev.optJSONObject("dashboard_data")?.optDouble("Temperature", Double.NaN) ?: Double.NaN
            }
        }
        tvTemp.text = if (fromNetatmo) Wx.deg1(temp) else Wx.deg(temp)
        tvWeatherIcon.text = icon

        val parts = mutableListOf<String>()
        if (prefs.getBoolean("wxPlace", true)) parts.add(place)
        if (prefs.getBoolean("wxFeels", false)) parts.add("gefühlt ${Wx.deg(cur.optDouble("apparent_temperature", Double.NaN))}")
        if (prefs.getBoolean("wxWind", false)) parts.add("Wind ${Math.round(cur.optDouble("wind_speed_10m", 0.0))} km/h")
        if (prefs.getBoolean("wxHum", false) && !hum.isNaN()) parts.add("${Math.round(hum)} % Feuchte")
        if (prefs.getBoolean("ntIndoor", false) && !indoor.isNaN()) parts.add("innen ${Wx.deg1(indoor)}")
        tvWeatherInfo.text = parts.joinToString("\n")
        tvWeatherInfo.visibility = vis(!night && skin.details && parts.isNotEmpty())

        val days = mutableListOf<DayForecast>()
        val times = daily?.optJSONArray("time")
        if (daily != null && times != null) {
            val parse = SimpleDateFormat("yyyy-MM-dd", Locale.US)
            val wd = SimpleDateFormat("EEE", Locale.GERMANY)
            for (i in 0 until minOf(times.length(), 3)) {
                val label = when (i) {
                    0 -> "Heute"
                    1 -> "Morgen"
                    else -> (try { wd.format(parse.parse(times.getString(i))!!) } catch (_: Exception) { "" }).trimEnd('.')
                }
                days.add(
                    DayForecast(
                        label,
                        daily.optJSONArray("weather_code")?.optInt(i, -1) ?: -1,
                        daily.optJSONArray("temperature_2m_max")?.optDouble(i) ?: Double.NaN,
                        daily.optJSONArray("temperature_2m_min")?.optDouble(i) ?: Double.NaN,
                        daily.optJSONArray("wind_speed_10m_max")?.optDouble(i) ?: Double.NaN,
                        daily.optJSONArray("precipitation_sum")?.optDouble(i, 0.0) ?: 0.0,
                        daily.optJSONArray("sunshine_duration")?.optDouble(i, Double.NaN) ?: Double.NaN
                    )
                )
            }
        }
        if (meta != null) {
            val md = Meta.days(meta, 3)
            for (i in days.indices) {
                val m = md.getOrNull(i) ?: continue
                val o = days[i]
                days[i] = DayForecast(
                    o.label, o.code,
                    if (m.tmax.isNaN()) o.max else m.tmax,
                    if (m.tmin.isNaN()) o.min else m.tmin,
                    if (m.wind.isNaN()) o.wind else m.wind,
                    if (m.psum.isNaN()) o.rain else m.psum,
                    o.sun, m.cat
                )
            }
        }
        showForecast(days)
    }

    private fun mm(v: Double): String = Wx.mm(v)

    private fun showForecast(days: List<DayForecast>) {
        lastForecast = days
        forecastRow.removeAllViews()
        val k = skin
        val colW = dp(64).toInt()
        val showSun = prefs.getBoolean("wxSun", true)
        val showRain = prefs.getBoolean("wxRain", true)
        for (f in days) {
            val col = LinearLayout(this).apply {
                orientation = LinearLayout.VERTICAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            fun line(t: String, sp: Float, color: Int) = TextView(this).apply {
                text = t
                textSize = sp
                setTextColor(color)
                gravity = Gravity.CENTER
                setSingleLine(true)
            }
            col.addView(line(f.label, 11f, k.sub))
            col.addView(line(if (f.cat != null) Meta.icon(f.cat) else weatherIcon(f.code, true), 20f, k.icon))
            col.addView(line("${Wx.deg(f.max)} / ${Wx.deg(f.min)}", 13f, k.fg))
            if (showSun) col.addView(line("☀ " + Wx.sunHours(f.sun), 10f, k.sub))
            if (showRain) col.addView(line("☂ " + Wx.mm(f.rain), 10f, k.wind))
            forecastRow.addView(col, LinearLayout.LayoutParams(colW, ViewGroup.LayoutParams.WRAP_CONTENT))
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
        if (!skin.events || !prefs.getBoolean("showEvents", true) || !hasPerm(Manifest.permission.READ_CALENDAR)) {
            tvEvents.visibility = View.GONE
            return
        }
        io.execute {
            val lines = try { queryToday() } catch (_: Exception) { null }
            main.post {
                if (lines == null || night || !skin.events) {
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
        val max = prefs.getInt("evMax", 5)
        return if (sorted.size > max) sorted.take(max - 1) + "+ ${sorted.size - max + 1} weitere" else sorted
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
        showStationName()
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
        btnPlay.setImageResource(if (wantPlaying) R.drawable.ic_stop else R.drawable.ic_play)
    }

    private fun showStationName() {
        tvStation.text = if (stations.isEmpty()) "Keine Sender – tippe auf ＋"
        else (if (wantPlaying) "" else "▶  ") + stations[current.coerceIn(0, stations.size - 1)].name
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

    private fun stationClick(i: Int) {
        if (i == current && wantPlaying) stopRadio() else {
            current = i
            playCurrent()
        }
    }

    private fun logoView(s: Station, size: Int): View {
        val bmp = icons[s.url]
        val rad = dp(1) * minOf(skin.corner, 16f)
        val v: View = if (bmp != null) {
            ImageView(this).apply {
                setImageBitmap(bmp)
                if (skin.mono) colorFilter = ColorMatrixColorFilter(ColorMatrix().apply { setSaturation(0f) })
                scaleType = ImageView.ScaleType.FIT_CENTER
                val p = size / 10
                setPadding(p, p, p, p)
                background = GradientDrawable().apply {
                    cornerRadius = rad
                    setColor(Color.WHITE)
                }
            }
        } else {
            TextView(this).apply {
                text = shortLabel(s.name)
                gravity = Gravity.CENTER
                setTextColor(Color.WHITE)
                setTypeface(typeface, Typeface.BOLD)
                textSize = if (size > dp(56)) 16f else 13f
                background = GradientDrawable().apply {
                    cornerRadius = rad
                    setColor(if (skin.mono) col("#3A3A3A") else colorFor(s.name))
                }
            }
        }
        v.clipToOutline = true
        return v
    }

    /** Kachel mit Logo (Designs mit Senderleiste). */
    private fun favItem(i: Int, s: Station, size: Int, captionSp: Float, active: Int): View {
        val item = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = dp(4).toInt()
            setPadding(p, p, p, p)
        }
        val iconView = logoView(s, size)
        if (i == active) {
            val bg = iconView.background
            if (bg is GradientDrawable) bg.setStroke(dp(3).toInt(), if (wantPlaying) skin.accent else skin.sub)
        }
        item.addView(iconView, LinearLayout.LayoutParams(size, size))
        item.addView(TextView(this).apply {
            text = s.name
            textSize = captionSp
            setTextColor(if (i == active) skin.fg else skin.sub)
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        item.setOnClickListener { stationClick(i) }
        item.setOnLongClickListener {
            confirmRemove(i)
            true
        }
        return item
    }

    /** Elfenbein-Stationstaste (Retro): die gewählte Taste bleibt eingerastet. */
    private fun ivoryKey(label: String, latched: Boolean): TextView = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 14f
        maxLines = 2
        ellipsize = TextUtils.TruncateAt.END
        typeface = Typeface.create("serif", Typeface.BOLD)
        setTextColor(if (latched) col("#7A1010") else col("#3B2A1A"))
        background = btnBg(style = BtnStyle.IVORY, latched = latched)
        val p = dp(4).toInt()
        setPadding(p, p, p, p + dp(5).toInt())
    }

    /** Autoradio-Taste mit Speicherplatz-Nummer. */
    private fun keypadKey(i: Int, s: Station, active: Boolean): View {
        val key = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            background = btnBg(style = BtnStyle.KEYPAD, latched = active)
            val p = dp(6).toInt()
            setPadding(p, p, p, p + dp(4).toInt())
        }
        key.addView(TextView(this).apply {
            text = (i + 1).toString()
            textSize = 13f
            typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
            setTextColor(if (active) skin.accent else col("#9AA3AD"))
            gravity = Gravity.CENTER
        })
        val size = dp(58).toInt()
        key.addView(logoView(s, size), LinearLayout.LayoutParams(size, size))
        key.addView(TextView(this).apply {
            text = s.name
            textSize = 11f
            setTextColor(if (active) Color.WHITE else col("#C8CDD3"))
            gravity = Gravity.CENTER
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        key.setOnClickListener { stationClick(i) }
        key.setOnLongClickListener {
            confirmRemove(i)
            true
        }
        return key
    }

    private fun addTile(size: Int, captionSp: Float): View {
        val add = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            val p = dp(4).toInt()
            setPadding(p, p, p, p)
            setOnClickListener { showStations() }
        }
        add.addView(TextView(this).apply {
            text = "＋"
            gravity = Gravity.CENTER
            textSize = 24f
            setTextColor(skin.iconTint)
            background = btnBg()
        }, LinearLayout.LayoutParams(size, size))
        add.addView(TextView(this).apply {
            text = "Sender"
            textSize = captionSp
            gravity = Gravity.CENTER
            setTextColor(skin.sub)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return add
    }

    /** Breite des Radiobereichs (für Umbruch der Senderkacheln). */
    private fun panelWidthPx(): Int {
        val dm = resources.displayMetrics
        val land = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
        val w = if (land) (dm.widthPixels - dp(24)) / 2.3f - dp(8) else dm.widthPixels - dp(24)
        return (w - radioPanel.paddingLeft - radioPanel.paddingRight).toInt()
    }

    /** Senderkacheln mehrzeilig anordnen. */
    private fun fillGrid(views: List<View>, cols: Int, itemH: Int) {
        val cellW = panelWidthPx() / cols
        var row: LinearLayout? = null
        views.forEachIndexed { n, v ->
            if (n % cols == 0) {
                row = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    gravity = Gravity.CENTER_HORIZONTAL
                }
                favGrid.addView(row, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }
            row?.addView(v, LinearLayout.LayoutParams(cellW - dp(4).toInt(), itemH).apply {
                setMargins(dp(2).toInt(), dp(3).toInt(), dp(2).toInt(), dp(3).toInt())
            })
        }
    }

    private fun buildFavorites() {
        if (!::favRow.isInitialized || !::skin.isInitialized) return
        favRow.removeAllViews()
        favGrid.removeAllViews()
        val active = current.coerceIn(0, maxOf(0, stations.size - 1))
        dialView.setStations(stations.map { it.name }, active, wantPlaying)

        when (skin.presets) {
            Presets.NONE -> return
            Presets.ROW -> {
                val size = dp(72).toInt()
                val views = mutableListOf<View>()
                stations.forEachIndexed { i, s -> views.add(favItem(i, s, size, 13f, active)) }
                fillGrid(views, maxOf(1, panelWidthPx() / dp(92).toInt()), dp(122).toInt())
                return
            }
            Presets.KEYS -> {
                val views = mutableListOf<View>()
                stations.forEachIndexed { i, s ->
                    val key = ivoryKey(s.name, i == active && wantPlaying)
                    key.setOnClickListener { stationClick(i) }
                    key.setOnLongClickListener {
                        confirmRemove(i)
                        true
                    }
                    views.add(key)
                }
                fillGrid(views, maxOf(1, panelWidthPx() / dp(88).toInt()), dp(74).toInt())
                return
            }
            Presets.GRID -> {
                val views = mutableListOf<View>()
                stations.forEachIndexed { i, s -> views.add(keypadKey(i, s, i == active && wantPlaying)) }
                fillGrid(views, 4, dp(128).toInt())
                return
            }
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
                    tvTimerBadge.text = ""
                } else {
                    sleepAt = System.currentTimeMillis() + m * 60_000L
                    tvTimerBadge.text = "$m′"
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
            .setNeutralButton("Fester Ort") { _, _ -> askPlace() }
            .setNegativeButton("Design") { _, _ -> showSkins() }
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
