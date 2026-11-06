package de.datacreate.radiouhr

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
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
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors

/** Ordentliche Einstellungsseite. */
class SettingsActivity : ScaledActivity() {

    private lateinit var prefs: SharedPreferences
    private lateinit var list: LinearLayout
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private val dlgTheme = android.R.style.Theme_Material_Dialog_Alert

    private val accent = col("#FF8A3D")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private var tab = 0
    private lateinit var tabBox: LinearLayout
    private lateinit var scroll: ScrollView
    private val TABS = listOf("Darstellung", "Wetter", "Netatmo", "Wecker", "Nacht", "Radio & Termine", "Sichern")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Wx.prefs(this)
        tab = savedInstanceState?.getInt("tab", 0) ?: intent.getIntExtra("tab", 0)
        setupFrame()
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        build()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putInt("tab", tab)
    }

    override fun onConfigurationChanged(newConfig: android.content.res.Configuration) {
        super.onConfigurationChanged(newConfig)
        setupFrame()
        build()
    }

    /** Hochformat: Reiter oben. Querformat: Reiter links, Inhalt rechts. */
    private fun setupFrame() {
        val land = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val root = LinearLayout(this).apply {
            orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setBackgroundColor(col("#121212"))
        }
        tabBox = LinearLayout(this).apply {
            orientation = if (land) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            setPadding(dp(10), dp(10), dp(10), dp(6))
        }
        if (land) {
            val sv = ScrollView(this)
            sv.setBackgroundColor(col("#181818"))
            sv.addView(tabBox)
            root.addView(sv, LinearLayout.LayoutParams(dp(210), ViewGroup.LayoutParams.MATCH_PARENT))
        } else {
            val hs = android.widget.HorizontalScrollView(this).apply { isHorizontalScrollBarEnabled = false }
            hs.setBackgroundColor(col("#181818"))
            hs.addView(tabBox)
            root.addView(hs, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        scroll = ScrollView(this)
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(40))
        }
        scroll.addView(list)
        root.addView(scroll, if (land) LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        else LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        setContentView(root)
    }

    private fun buildTabs() {
        val land = tabBox.orientation == LinearLayout.VERTICAL
        tabBox.removeAllViews()
        if (land) {
            tabBox.addView(TextView(this).apply {
                text = "‹  Einstellungen"
                textSize = 19f
                setTextColor(Color.WHITE)
                setPadding(dp(6), dp(6), 0, dp(14))
                setOnClickListener { finish() }
            })
        }
        TABS.forEachIndexed { i, name ->
            val sel = i == tab
            tabBox.addView(TextView(this).apply {
                text = name
                textSize = 15f
                setTextColor(if (sel) Color.BLACK else col("#DDDDDD"))
                if (sel) setTypeface(typeface, Typeface.BOLD)
                setPadding(dp(14), dp(10), dp(14), dp(10))
                background = GradientDrawable().apply {
                    cornerRadius = dp(20).toFloat()
                    setColor(if (sel) accent else col("#262626"))
                }
                setOnClickListener {
                    tab = i
                    build()
                    scroll.scrollTo(0, 0)
                }
            }, LinearLayout.LayoutParams(
                if (land) ViewGroup.LayoutParams.MATCH_PARENT else ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply { setMargins(dp(3), dp(3), dp(3), dp(3)) })
        }
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    override fun onDestroy() {
        io.shutdownNow()
        super.onDestroy()
    }

    // ------------------------------------------------------------ Bausteine

    private fun title(text: String) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(TextView(this).apply {
            this.text = "‹"
            textSize = 30f
            setTextColor(Color.WHITE)
            setPadding(0, 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        row.addView(TextView(this).apply {
            this.text = text
            textSize = 24f
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        })
        list.addView(row)
    }

    private fun section(text: String) {
        list.addView(TextView(this).apply {
            this.text = text.uppercase(Locale.GERMANY)
            textSize = 13f
            letterSpacing = 0.08f
            setTextColor(accent)
            setTypeface(typeface, Typeface.BOLD)
            setPadding(0, dp(26), 0, dp(6))
        })
    }

    private fun card(): LinearLayout {
        val c = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(col("#1E1E1E"))
            }
            setPadding(dp(14), dp(6), dp(14), dp(6))
        }
        list.addView(c, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        return c
    }

    private fun check(parent: LinearLayout, label: String, key: String, def: Boolean, hint: String? = null, onChange: ((Boolean) -> Unit)? = null) {
        val cb = CheckBox(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
            isChecked = prefs.getBoolean(key, def)
            setPadding(dp(8), dp(10), 0, dp(10))
            setOnCheckedChangeListener { _, v ->
                prefs.edit().putBoolean(key, v).apply()
                onChange?.invoke(v)
            }
        }
        parent.addView(cb)
        if (hint != null) parent.addView(small(hint))
    }

    private fun small(text: String) = TextView(this).apply {
        this.text = text
        textSize = 13f
        setTextColor(col("#9A9A9A"))
        setPadding(dp(4), 0, 0, dp(8))
    }

    private fun spinner(parent: LinearLayout, label: String, options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(4), dp(6), 0, dp(6))
        }
        row.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
        }, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        val sp = Spinner(this)
        sp.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, options)
        sp.setSelection(selected.coerceIn(0, options.size - 1))
        var last = selected.coerceIn(0, options.size - 1)
        sp.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position == last) return
                last = position
                onSelect(position)
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
        row.addView(sp)
        parent.addView(row)
    }

    private fun button(parent: LinearLayout, label: String, sub: String? = null, onClick: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(4), dp(12), 0, dp(12))
            isClickable = true
            setOnClickListener { onClick() }
        }
        box.addView(TextView(this).apply {
            text = label
            textSize = 16f
            setTextColor(Color.WHITE)
        })
        if (sub != null) box.addView(TextView(this).apply {
            text = sub
            textSize = 13f
            setTextColor(col("#9A9A9A"))
        })
        parent.addView(box)
    }

    // ------------------------------------------------------------ Aufbau

    private fun build() {
        buildTabs()
        list.removeAllViews()
        if (tabBox.orientation == LinearLayout.HORIZONTAL) title("Einstellungen")
        var c: LinearLayout

        // Darstellung
        if (tab == 0) {
        section("Darstellung")
        c = card()
        val skins = Skins.all
        spinner(c, "Design", skins.map { it.name }, skins.indexOfFirst { it.id == prefs.getString("skin", "standard") }) {
            prefs.edit().putString("skin", skins[it].id).apply()
            main.post { build() }
        }
        c.addView(small(Skins.byId(prefs.getString("skin", "standard")).desc))
        val clocks = listOf(
            "" to "wie Design",
            "TEXT" to "Ziffern",
            "NIXIE" to "Nixie-Röhren",
            "SEGMENT" to "7-Segment",
            "FLIP" to "Klappziffern",
            "ANALOG_BRAUN" to "Zeiger, Bauhaus",
            "STATION" to "Bahnhofsuhr",
            "ANALOG_WOOD" to "Zeiger, Holz",
            "CUCKOO" to "Kuckucksuhr"
        )
        val cur = prefs.getString("clockStyle", "") ?: ""
        spinner(c, "Uhrenart", clocks.map { it.second }, maxOf(0, clocks.indexOfFirst { it.first == cur })) {
            prefs.edit().putString("clockStyle", clocks[it].first).apply()
        }

        val zooms = listOf(80, 90, 100, 110, 125, 140, 160)
        spinner(c, "Größe", zooms.map { if (it == 100) "automatisch" else "$it %" }, maxOf(0, zooms.indexOf(prefs.getInt("uiZoom", 100)))) {
            prefs.edit().putInt("uiZoom", zooms[it]).apply()
            main.post { recreate() }
        }
        c.addView(small("„automatisch“ passt alles an die Bildschirmgröße an (Handy, 8-Zoll-Tablet …). Mit % feiner einstellen."))
        val sizes = listOf(60, 70, 80, 90, 100, 115, 130, 150, 175, 200)
        spinner(c, "Senderkacheln", sizes.map { "$it %" }, maxOf(0, sizes.indexOf(prefs.getInt("favScale", 100)))) {
            prefs.edit().putInt("favScale", sizes[it]).apply()
        }
        spinner(c, "Tasten (Timer, ＋, Zahnrad)", sizes.map { "$it %" }, maxOf(0, sizes.indexOf(prefs.getInt("btnScale", 100)))) {
            prefs.edit().putInt("btnScale", sizes[it]).apply()
        }

        }
        if (tab == 1) {
        // Wetter
        section("Wetter")
        c = card()
        val srcIds = listOf("meta", "om")
        spinner(c, "Quelle", listOf("Mehrere Wetterdienste", "Open-Meteo"), maxOf(0, srcIds.indexOf(prefs.getString("wxSource", "meta")))) {
            prefs.edit().putString("wxSource", srcIds[it]).apply()
            main.post { build() }
        }
        if (prefs.getString("wxSource", "meta") == "meta") {
            c.addView(small("Konsens (Median) wie bei DC Wetter-Meta – direkt abgefragt bei DWD ICON, ECMWF, NOAA GFS, Météo-France, UK Met Office, GEM Kanada und MET Norway. Sonnenstunden, Sonnenauf-/untergang und UV von Open-Meteo."))
            check(c, "Zusätzlich DWD MOSMIX (Wetterstation)", "wxMosmix", false)
            button(c, "Wetterdienste testen") { testMeta() }
        }
        c = card()
        check(c, "Wetter für aktuellen Standort", "useGps", true,
            if (prefs.getBoolean("useGps", true)) null else "Fester Ort: ${prefs.getString("placeName", "Todtnau")}") { build() }
        button(c, "Festen Ort wählen …", prefs.getString("placeName", "Todtnau")) { askPlace() }
        c = card()
        c.addView(small("Was neben der Temperatur angezeigt wird:"))
        check(c, "Ort", "wxPlace", true)
        check(c, "Gefühlte Temperatur", "wxFeels", false)
        check(c, "Wind", "wxWind", false)
        check(c, "Luftfeuchte", "wxHum", false)
        check(c, "Vorhersage", "wxForecast", true)
        val dayOpts = (1..7).toList()
        spinner(c, "Tage in der Vorhersage", dayOpts.map { if (it == 1) "nur heute" else "$it Tage" }, maxOf(0, dayOpts.indexOf(prefs.getInt("wxDays", 3)))) {
            prefs.edit().putInt("wxDays", dayOpts[it]).apply()
        }
        check(c, "Sonnenstunden in der Vorhersage", "wxSun", true)
        check(c, "Regenmenge in der Vorhersage", "wxRain", true)
        c.addView(small("Antippen von Wetter oder Temperatur öffnet die ausführliche Wetterseite."))

        }
        if (tab == 2) {
        // Netatmo
        section("Netatmo-Wetterstation")
        c = card()
        if (Netatmo.connected(prefs)) {
            val dev = Netatmo.cached(prefs)
            val name = dev?.optString("station_name") ?: dev?.optString("module_name") ?: "verbunden"
            val t = prefs.getLong("ntTime", 0L)
            c.addView(TextView(this).apply {
                text = "✓ Verbunden: $name" + if (t > 0) "\nletzte Daten: " + SimpleDateFormat("dd.MM. HH:mm", Locale.GERMANY).format(Date(t)) else ""
                textSize = 15f
                setTextColor(col("#8CE08C"))
                setPadding(dp(4), dp(10), 0, dp(6))
            })
            val devs = Netatmo.all(prefs)
            if (devs.size > 1) {
                val ids = devs.map { it.optString("_id") }
                val curId = prefs.getString("ntDevice", "") ?: ""
                spinner(c, "Station", devs.map { Netatmo.label(it) }, maxOf(0, ids.indexOf(curId))) {
                    Netatmo.select(prefs, ids[it])
                    main.post { build() }
                }
            }
            check(c, "Außentemperatur von Netatmo statt Internet-Wetter", "ntOutdoor", true)
            check(c, "Wettervorhersage für den Ort dieser Station", "ntPlace", false,
                Netatmo.place(prefs)?.let { "Ort der Station: ${it.third}" })
            check(c, "Innentemperatur auf der Startseite zeigen", "ntIndoor", false)
            button(c, "Jetzt aktualisieren") { testNetatmo() }
            button(c, "Verbindung trennen") {
                Netatmo.disconnect(prefs)
                build()
            }
        } else {
            c.addView(small("Verbinde deine Netatmo-Station, dann zeigt die Uhr die echten Werte von deinem Balkon/Garten (Temperatur, Feuchte, Min/Max, CO₂, Regenmesser …)."))
            button(c, "Netatmo verbinden …", "Assistent: App anlegen, anmelden, fertig") {
                startActivity(Intent(this, NetatmoAuthActivity::class.java))
            }
        }

        }
        if (tab == 5) {
        // Termine
        section("Termine")
        c = card()
        check(c, "Termine anzeigen", "showEvents", true)
        val ranges = listOf(1, 2, 3, 7)
        spinner(c, "Zeitraum", listOf("nur heute", "heute + morgen", "3 Tage", "7 Tage"), maxOf(0, ranges.indexOf(prefs.getInt("evDays", 1)))) {
            prefs.edit().putInt("evDays", ranges[it]).apply()
            main.post { build() }
        }
        val counts = (2..8).toList()
        spinner(c, "Höchstens", counts.map { "$it Termine" }, maxOf(0, counts.indexOf(prefs.getInt("evMax", 5)))) {
            prefs.edit().putInt("evMax", counts[it]).apply()
        }
        c = card()
        c.addView(small("Die Termine kommen aus dem Kalender dieses Geräts – also aus allen Konten, die unter Android-Einstellungen → Konten synchronisiert werden (Google, Samsung, Exchange …)."))
        val granted = android.os.Build.VERSION.SDK_INT < 23 ||
            checkSelfPermission(android.Manifest.permission.READ_CALENDAR) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (!granted) {
            c.addView(TextView(this).apply {
                text = "✗ Kein Zugriff auf den Kalender"
                textSize = 15f
                setTextColor(col("#FF8080"))
                setPadding(dp(4), dp(8), 0, dp(4))
            })
            button(c, "Zugriff erlauben …") {
                if (android.os.Build.VERSION.SDK_INT >= 23) requestPermissions(arrayOf(android.Manifest.permission.READ_CALENDAR), 7)
            }
            button(c, "App-Berechtigungen öffnen", "falls die Abfrage nicht mehr erscheint") {
                startActivity(Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, android.net.Uri.parse("package:$packageName")))
            }
        } else {
            val cals = try { Events.calendars(this) } catch (_: Exception) { emptyList() }
            val evs = try { Events.query(this, prefs) } catch (_: Exception) { emptyList() }
            c.addView(TextView(this).apply {
                text = "✓ Zugriff erlaubt · ${cals.size} Kalender · ${evs.size} Termine im Zeitraum"
                textSize = 15f
                setTextColor(col("#8CE08C"))
                setPadding(dp(4), dp(8), 0, dp(4))
            })
            if (cals.isEmpty()) {
                c.addView(small("Auf diesem Gerät ist kein Kalender synchronisiert. In den Android-Einstellungen → Konten → Google → Kalender-Synchronisierung einschalten (bei Samsung zusätzlich in der Kalender-App unter Kalender verwalten)."))
            } else {
                c.addView(small("Diese Kalender verwenden:"))
                val hidden = Events.hidden(prefs).toMutableSet()
                for (cal in cals) {
                    val cb = CheckBox(this).apply {
                        text = cal.name + (if (cal.account.isNotEmpty() && cal.account != cal.name) "  ·  ${cal.account}" else "") +
                            (if (!cal.sync) "  (nicht synchronisiert)" else "")
                        textSize = 14f
                        setTextColor(Color.WHITE)
                        isChecked = cal.id.toString() !in hidden
                        buttonTintList = android.content.res.ColorStateList.valueOf(cal.color or 0xFF000000.toInt())
                        setOnCheckedChangeListener { _, v ->
                            if (v) hidden.remove(cal.id.toString()) else hidden.add(cal.id.toString())
                            prefs.edit().putStringSet("calHidden", HashSet(hidden)).apply()
                        }
                    }
                    c.addView(cb)
                }
                if (evs.isNotEmpty()) {
                    c.addView(small("\nGefunden:\n" + evs.take(8).joinToString("\n") { "• " + it.label }))
                }
            }
        }

        }
        if (tab == 4) {
        // Nacht
        section("Nachtmodus")
        c = card()
        check(c, "Automatisch einschalten", "autoNight", true, "Nachts nur die Uhr, gedimmt. Mehrere Zeiträume möglich, z. B. auch Mittagsschlaf.")
        c = card()
        (c.layoutParams as LinearLayout.LayoutParams).bottomMargin = dp(10)
        val brightLabel = TextView(this).apply {
            textSize = 16f
            setTextColor(Color.WHITE)
            setPadding(dp(4), dp(8), 0, 0)
        }
        fun bl(v: Int) { brightLabel.text = "Helligkeit der Nachtuhr: $v %" }
        bl(prefs.getInt("nightBright", 5))
        c.addView(brightLabel)
        c.addView(android.widget.SeekBar(this).apply {
            max = 99
            progress = prefs.getInt("nightBright", 5) - 1
            setOnSeekBarChangeListener(object : android.widget.SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: android.widget.SeekBar?, v: Int, fromUser: Boolean) {
                    bl(v + 1)
                    if (fromUser) {
                        prefs.edit().putInt("nightBright", v + 1).apply()
                        previewBrightness(v + 1)
                    }
                }
                override fun onStartTrackingTouch(sb: android.widget.SeekBar?) {}
                override fun onStopTrackingTouch(sb: android.widget.SeekBar?) {
                    previewBrightness(-1)
                }
            })
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(44)))
        c.addView(small("Beim Schieben zeigt das Display die Helligkeit kurz zur Probe."))
        val ncol = listOf("#B8432F" to "Rot", "#C8782A" to "Bernstein", "#3FA34D" to "Grün", "#3D72C4" to "Blau", "#BBBBBB" to "Weiß")
        spinner(c, "Farbe", ncol.map { it.second }, maxOf(0, ncol.indexOfFirst { it.first == prefs.getString("nightColor", "#B8432F") })) {
            prefs.edit().putString("nightColor", ncol[it].first).apply()
        }
        c = card()
        val zones = NightZones.load(prefs)
        zones.forEachIndexed { i, z ->
            timeRow(c, "${hhmm(z.from)} – ${hhmm(z.to)}", Days.label(z.days),
                onFrom = { pickTime(z.from) { v -> z.from = v; NightZones.save(prefs, zones); build() } },
                onTo = { pickTime(z.to) { v -> z.to = v; NightZones.save(prefs, zones); build() } },
                onDays = { pickDays(z.days) { v -> z.days = v; NightZones.save(prefs, zones); build() } },
                onDelete = { zones.removeAt(i); NightZones.save(prefs, zones); build() })
        }
        button(c, "＋ Zeitraum hinzufügen") {
            zones.add(NightZones.Zone(13 * 60, 14 * 60 + 30))
            NightZones.save(prefs, zones)
            build()
        }

        }
        if (tab == 3) {
        // Wecker
        section("Radiowecker")
        c = card()
        val alarms = Alarms.load(prefs)
        val st = stationList()
        if (alarms.isEmpty()) c.addView(small("Noch kein Wecker. Der Wecker spielt den gewählten Sender, blendet langsam ein und hat eine Schlummertaste."))
        alarms.forEachIndexed { i, a ->
            alarmRow(c, a, st, onChange = { Alarms.save(this, prefs, alarms); build() }, onDelete = {
                alarms.removeAt(i)
                Alarms.save(this, prefs, alarms)
                build()
            })
        }
        button(c, "＋ Wecker hinzufügen") {
            pickTime(6 * 60 + 30) { v ->
                alarms.add(Alarms.Alarm(System.currentTimeMillis(), v, 0x1F, st.firstOrNull()?.second ?: "", true))
                Alarms.save(this, prefs, alarms)
                build()
            }
        }
        if (alarms.isNotEmpty()) {
            val vols = listOf(20, 30, 40, 50, 60, 70, 80, 100)
            spinner(c, "Lautstärke", vols.map { "$it %" }, maxOf(0, vols.indexOf(prefs.getInt("alarmVol", 50)))) {
                prefs.edit().putInt("alarmVol", vols[it]).apply()
            }
            val fades = listOf(0, 30, 60, 120, 300)
            spinner(c, "Einblenden", fades.map { if (it == 0) "sofort" else if (it < 60) "$it s" else "${it / 60} min" },
                maxOf(0, fades.indexOf(prefs.getInt("alarmFade", 60)))) {
                prefs.edit().putInt("alarmFade", fades[it]).apply()
            }
            val snoozes = listOf(5, 9, 10, 15, 20)
            spinner(c, "Schlummern", snoozes.map { "$it min" }, maxOf(0, snoozes.indexOf(prefs.getInt("snoozeMin", 9)))) {
                prefs.edit().putInt("snoozeMin", snoozes[it]).apply()
            }
            val ends = listOf(0, 30, 60, 90, 120)
            spinner(c, "Automatisch aus nach", ends.map { if (it == 0) "nie" else "$it min" }, maxOf(0, ends.indexOf(prefs.getInt("alarmEnd", 60)))) {
                prefs.edit().putInt("alarmEnd", ends[it]).apply()
            }
            c.addView(small("Klappt der Sender beim Wecken nicht, ertönt nach 30 Sekunden der normale Weckton."))
        }

        }
        if (tab == 5) {
        // Radio
        section("Radio")
        c = card()
        check(c, "Beim Start automatisch abspielen", "autoplay", false)
        c.addView(small("Sender hinzufügen: ＋ auf der Startseite. Entfernen: Sender lange drücken."))

        }
        if (tab == 6) {
        // Sichern
        section("Sichern & Übertragen")
        c = card()
        button(c, "Alle Daten exportieren …", "Sender, Wecker, Design, Wetter, Nachtzeiten, Netatmo-Zugang – als Datei, z. B. für ein zweites Gerät") {
            exportData()
        }
        button(c, "Daten importieren …", "ersetzt alle Einstellungen dieses Geräts") { importData() }
        c.addView(small("Die Datei enthält den Netatmo-Zugang – nicht öffentlich weitergeben."))

        }
        list.addView(small("\nDC Radiouhr · Datacreate"))
    }

    private fun stationList(): List<Pair<String, String>> = try {
        val a = JSONArray(prefs.getString("stations", "[]"))
        (0 until a.length()).map { a.getJSONObject(it).let { o -> o.optString("name") to o.optString("url") } }
    } catch (_: Exception) { emptyList() }

    private fun chip(text: String, big: Boolean = false, onClick: () -> Unit) = TextView(this).apply {
        this.text = text
        textSize = if (big) 22f else 14f
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(10), dp(6), dp(10), dp(6))
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(col("#2C2C2C"))
        }
        setOnClickListener { onClick() }
    }

    private fun timeRow(parent: LinearLayout, label: String, sub: String, onFrom: () -> Unit, onTo: () -> Unit, onDays: () -> Unit, onDelete: () -> Unit) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, dp(8), 0, dp(8))
        }
        val parts = label.split(" – ")
        row.addView(chip(parts[0], true, onFrom))
        row.addView(TextView(this).apply { text = "  bis  "; setTextColor(col("#9A9A9A")); textSize = 14f })
        row.addView(chip(parts.getOrElse(1) { "" }, true, onTo))
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        row.addView(chip(sub, false, onDays), LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip("✕", false, onDelete), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        parent.addView(row)
    }

    private fun alarmRow(parent: LinearLayout, a: Alarms.Alarm, stations: List<Pair<String, String>>, onChange: () -> Unit, onDelete: () -> Unit) {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(0, dp(8), 0, dp(10))
        }
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(CheckBox(this).apply {
            isChecked = a.on
            setOnCheckedChangeListener { _, v -> a.on = v; onChange() }
        })
        row.addView(chip(hhmm(a.min), true) { pickTime(a.min) { v -> a.min = v; a.on = true; onChange() } })
        row.addView(View(this), LinearLayout.LayoutParams(dp(10), 1))
        row.addView(chip(Days.label(a.days)) { pickDays(a.days, allowNone = true) { v -> a.days = v; onChange() } },
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(chip("✕", false, onDelete), LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = dp(8) })
        box.addView(row)
        if (stations.isNotEmpty()) {
            val idx = maxOf(0, stations.indexOfFirst { it.second == a.station })
            spinner(box, "Sender", stations.map { it.first }, idx) { a.station = stations[it].second; onChange() }
        }
        if (a.on) {
            val t = Alarms.nextTime(a)
            box.addView(small("klingelt " + SimpleDateFormat("EEEE, d. MMMM 'um' HH:mm", Locale.GERMANY).format(Date(t))))
        }
        parent.addView(box)
    }

    /** Kompakter Zeitwähler (Stunde : Minute) – passt auch quer und auf kleine Displays. */
    private fun pickTime(min: Int, onSet: (Int) -> Unit) {
        fun np(maxV: Int, value: Int) = android.widget.NumberPicker(this).apply {
            minValue = 0
            maxValue = maxV
            displayedValues = Array(maxV + 1) { String.format("%02d", it) }
            this.value = value
            wrapSelectorWheel = true
            descendantFocusability = android.widget.NumberPicker.FOCUS_BLOCK_DESCENDANTS
        }
        val h = np(23, min / 60)
        val m = np(59, min % 60)
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(8), dp(16), 0)
        }
        row.addView(h)
        row.addView(TextView(this).apply {
            text = " : "
            textSize = 28f
            setTextColor(Color.WHITE)
        })
        row.addView(m)
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Uhrzeit")
            .setView(row)
            .setPositiveButton("OK") { _, _ -> onSet(h.value * 60 + m.value) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun pickDays(mask: Int, allowNone: Boolean = false, onSet: (Int) -> Unit) {
        val names = arrayOf("Montag", "Dienstag", "Mittwoch", "Donnerstag", "Freitag", "Samstag", "Sonntag")
        val checked = BooleanArray(7) { mask and (1 shl it) != 0 }
        AlertDialog.Builder(this, dlgTheme)
            .setTitle(if (allowNone) "Wochentage (keiner = einmalig)" else "Wochentage")
            .setMultiChoiceItems(names, checked) { _, i, v -> checked[i] = v }
            .setPositiveButton("OK") { _, _ ->
                var m = 0
                for (i in 0..6) if (checked[i]) m = m or (1 shl i)
                if (m == 0 && !allowNone) m = Days.ALL
                onSet(m)
            }
            .setNeutralButton("Mo–Fr") { _, _ -> onSet(0x1F) }
            .setNegativeButton("Abbrechen", null)
            .show()
    }

    private fun previewBrightness(pct: Int) {
        val lp = window.attributes
        lp.screenBrightness = if (pct < 0) android.view.WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else (pct / 100f).coerceAtLeast(0.01f)
        window.attributes = lp
    }

    // ------------------------------------------------------------ Export / Import

    private val REQ_EXPORT = 41
    private val REQ_IMPORT = 42

    /** Zwischenspeicher, die nicht mitgenommen werden (werden neu geladen). */
    private val SKIP = setOf("wxJson", "wxTime", "metaJson", "metaTime", "lastFire", "snoozeAt", "snoozeStation", "gpsName")

    private fun exportData() {
        val name = "DC-Radiouhr-" + SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY).format(Date()) + ".json"
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "application/json"
            putExtra(Intent.EXTRA_TITLE, name)
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_EXPORT)
        } catch (_: Exception) {
            Toast.makeText(this, "Kein Dateimanager verfügbar", Toast.LENGTH_LONG).show()
        }
    }

    private fun importData() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "*/*"
        }
        try {
            @Suppress("DEPRECATION")
            startActivityForResult(i, REQ_IMPORT)
        } catch (_: Exception) {
            Toast.makeText(this, "Kein Dateimanager verfügbar", Toast.LENGTH_LONG).show()
        }
    }

    private fun buildExport(): String {
        val data = JSONObject()
        for ((key, v) in prefs.all) {
            if (key in SKIP || v == null) continue
            val t = when (v) {
                is String -> "s"
                is Int -> "i"
                is Long -> "l"
                is Float -> "f"
                is Boolean -> "b"
                else -> continue
            }
            data.put(key, JSONObject().put("t", t).put("v", if (v is Float) v.toDouble() else v))
        }
        return JSONObject()
            .put("app", "DC Radiouhr")
            .put("format", 1)
            .put("exported", SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.GERMANY).format(Date()))
            .put("prefs", data)
            .toString(2)
    }

    private fun applyImport(json: String): Int {
        val root = JSONObject(json)
        if (root.optString("app") != "DC Radiouhr") throw IllegalArgumentException("Keine DC-Radiouhr-Datei")
        val data = root.getJSONObject("prefs")
        val e = prefs.edit().clear()
        var n = 0
        val keys = data.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            val o = data.optJSONObject(key) ?: continue
            when (o.optString("t")) {
                "s" -> e.putString(key, o.optString("v"))
                "i" -> e.putInt(key, o.optInt("v"))
                "l" -> e.putLong(key, o.optLong("v"))
                "f" -> e.putFloat(key, o.optDouble("v").toFloat())
                "b" -> e.putBoolean(key, o.optBoolean("v"))
                else -> continue
            }
            n++
        }
        // Netatmo-Zugriffstoken neu holen lassen (Refresh-Token bleibt)
        e.remove("ntAccess").putLong("ntExpires", 0L)
        e.commit()
        return n
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        @Suppress("DEPRECATION")
        super.onActivityResult(requestCode, resultCode, data)
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            if (requestCode == REQ_EXPORT) {
                contentResolver.openOutputStream(uri, "wt")?.use { it.write(buildExport().toByteArray(Charsets.UTF_8)) }
                Toast.makeText(this, "Exportiert", Toast.LENGTH_LONG).show()
            } else if (requestCode == REQ_IMPORT) {
                val json = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) } ?: return
                val n = applyImport(json)
                Alarms.schedule(this)
                Toast.makeText(this, "$n Einstellungen importiert – App startet neu", Toast.LENGTH_LONG).show()
                main.postDelayed({
                    startActivity(Intent(this, MainActivity::class.java)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                    finish()
                }, 800)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "Fehler: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    // ------------------------------------------------------------ Aktionen

    private fun testMeta() {
        build()
        val lat = prefs.getFloat("wxLat", prefs.getFloat("lat", 47.8295f)).toDouble()
        val lon = prefs.getFloat("wxLon", prefs.getFloat("lon", 7.9447f)).toDouble()
        io.execute {
            val msg = try {
                val b = Meta.fetch(prefs, lat, lon)
                if (b == null) "Keine Daten" else "OK – ${b.sources.size} Wetterdienste: " + b.sources.keys.joinToString(", ") { Meta.NAMES[it] ?: it }
            } catch (e: Exception) {
                "Fehler: ${e.message}"
            }
            main.post { Toast.makeText(this, msg, Toast.LENGTH_LONG).show() }
        }
    }

    private fun testNetatmo() {
        Toast.makeText(this, "Hole Netatmo-Daten …", Toast.LENGTH_SHORT).show()
        io.execute {
            val msg = try {
                val dev = Netatmo.fetch(prefs)
                if (dev == null) "Keine Station gefunden" else {
                    val out = Netatmo.outdoor(dev)?.optJSONObject("dashboard_data")
                    "OK – außen ${out?.optDouble("Temperature")?.let { Wx.deg1(it) } ?: "–"}"
                }
            } catch (e: Exception) {
                "Fehler: ${e.message}"
            }
            main.post {
                Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                build()
            }
        }
    }

    private fun askPlace() {
        val et = EditText(this).apply {
            hint = "Ort, z. B. Todtnau"
            setSingleLine(true)
            setTextColor(Color.WHITE)
        }
        val box = LinearLayout(this).apply {
            setPadding(dp(20), dp(10), dp(20), 0)
            addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        AlertDialog.Builder(this, dlgTheme)
            .setTitle("Fester Ort für das Wetter")
            .setView(box)
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
                JSONObject(Net.get("https://geocoding-api.open-meteo.com/v1/search?name=$enc&count=8&language=de&format=json"))
                    .optJSONArray("results")
            } catch (_: Exception) { null }
            main.post {
                if (res == null || res.length() == 0) {
                    Toast.makeText(this, "Ort nicht gefunden", Toast.LENGTH_SHORT).show()
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
                        build()
                    }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            }
        }
    }
}
