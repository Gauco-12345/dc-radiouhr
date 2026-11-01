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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Wx.prefs(this)
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(col("#121212"))
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(12), dp(18), dp(40))
        }
        scroll.addView(list)
        setContentView(scroll)
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
        list.removeAllViews()
        title("Einstellungen")

        // Darstellung
        section("Darstellung")
        var c = card()
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
        check(c, "Vorhersage (3 Tage)", "wxForecast", true)
        check(c, "Sonnenstunden in der Vorhersage", "wxSun", true)
        check(c, "Regenmenge in der Vorhersage", "wxRain", true)
        c.addView(small("Antippen von Wetter oder Temperatur öffnet die ausführliche Wetterseite."))

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

        // Termine
        section("Termine")
        c = card()
        check(c, "Heutige Termine anzeigen", "showEvents", true)
        val counts = (2..8).toList()
        spinner(c, "Höchstens", counts.map { "$it Termine" }, counts.indexOf(prefs.getInt("evMax", 5))) {
            prefs.edit().putInt("evMax", counts[it]).apply()
        }

        // Nacht
        section("Nachtmodus")
        c = card()
        check(c, "Automatisch einschalten", "autoNight", true, "Nachts nur die Uhr, gedimmt in Rot. Mehrere Zeiträume möglich, z. B. auch Mittagsschlaf.")
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

        // Radio
        section("Radio")
        c = card()
        check(c, "Beim Start automatisch abspielen", "autoplay", false)
        c.addView(small("Sender hinzufügen: ＋ auf der Startseite. Entfernen: Sender lange drücken."))

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

    private fun pickTime(min: Int, onSet: (Int) -> Unit) {
        android.app.TimePickerDialog(this, android.R.style.Theme_Material_Dialog_Alert, { _, h, m -> onSet(h * 60 + m) },
            min / 60, min % 60, true).show()
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
