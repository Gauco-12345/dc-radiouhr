package de.datacreate.radiouhr

import android.annotation.SuppressLint
import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import java.util.Locale
import java.util.UUID
import java.util.concurrent.Executors

/** Assistent zum Verbinden der Netatmo-Wetterstation (OAuth2). */
class NetatmoAuthActivity : Activity() {

    private lateinit var prefs: SharedPreferences
    private val main = Handler(Looper.getMainLooper())
    private val io = Executors.newSingleThreadExecutor()
    private var state = ""
    private var web: WebView? = null
    private lateinit var etId: EditText
    private lateinit var etSecret: EditText
    private lateinit var etRefresh: EditText

    private val accent = col("#FF8A3D")
    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Wx.prefs(this)
        showSteps()
    }

    override fun onDestroy() {
        io.shutdownNow()
        web?.destroy()
        super.onDestroy()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (web != null) {
            web?.destroy()
            web = null
            showSteps()
        } else {
            @Suppress("DEPRECATION")
            super.onBackPressed()
        }
    }

    // ------------------------------------------------------------ Bausteine

    private fun text(s: String, size: Float = 15f, color: Int = Color.WHITE, bold: Boolean = false) = TextView(this).apply {
        text = s
        textSize = size
        setTextColor(color)
        if (bold) setTypeface(typeface, Typeface.BOLD)
        setPadding(0, dp(4), 0, dp(4))
        setLineSpacing(0f, 1.15f)
    }

    private fun step(parent: LinearLayout, n: Int, title: String): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                cornerRadius = dp(14).toFloat()
                setColor(col("#1E1E1E"))
            }
            setPadding(dp(16), dp(12), dp(16), dp(14))
        }
        card.addView(text("Schritt $n · $title", 17f, accent, true))
        parent.addView(card, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            topMargin = dp(14)
        })
        return card
    }

    private fun btn(label: String, primary: Boolean = false, onClick: () -> Unit) = Button(this).apply {
        text = label
        isAllCaps = false
        textSize = 15f
        setTextColor(if (primary) Color.BLACK else Color.WHITE)
        background = GradientDrawable().apply {
            cornerRadius = dp(10).toFloat()
            setColor(if (primary) accent else col("#333333"))
        }
        setOnClickListener { onClick() }
    }

    private fun field(hint: String, value: String): EditText = EditText(this).apply {
        this.hint = hint
        setText(value)
        setSingleLine(true)
        inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        setTextColor(Color.WHITE)
        setHintTextColor(col("#777777"))
        typeface = Typeface.MONOSPACE
        textSize = 14f
    }

    private fun pasteRow(parent: LinearLayout, et: EditText) {
        val row = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        row.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        row.addView(btn("Einfügen") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            val t = cm.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()?.trim()
            if (!t.isNullOrEmpty()) et.setText(t)
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(42)).apply { leftMargin = dp(8) })
        parent.addView(row)
    }

    private fun open(url: String) {
        try {
            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (_: Exception) {
            Toast.makeText(this, "Kein Browser gefunden", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCredentials(): Boolean {
        val id = etId.text.toString().trim()
        val secret = etSecret.text.toString().trim()
        if (id.isEmpty() || secret.isEmpty()) {
            Toast.makeText(this, "Bitte Client ID und Client Secret eintragen (Schritt 2)", Toast.LENGTH_LONG).show()
            return false
        }
        prefs.edit().putString("ntClientId", id).putString("ntClientSecret", secret).apply()
        return true
    }

    // ------------------------------------------------------------ Schritte

    private fun showSteps() {
        val scroll = ScrollView(this)
        scroll.setBackgroundColor(col("#121212"))
        val l = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(18), dp(14), dp(18), dp(40))
        }
        scroll.addView(l)
        val head = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
        }
        head.addView(text("‹", 30f).apply {
            setPadding(0, 0, dp(14), 0)
            setOnClickListener { finish() }
        })
        head.addView(text("Netatmo verbinden", 24f))
        l.addView(head)
        l.addView(text("Netatmo gibt Daten nur an eigene „Apps“ heraus. Die legst du einmalig kostenlos an – dauert 2 Minuten.", 14f, col("#AAAAAA")))

        // 1
        var c = step(l, 1, "App bei Netatmo anlegen")
        c.addView(text("Öffne das Netatmo-Entwicklerportal, melde dich mit deinem normalen Netatmo-Konto an und lege eine App an:\n" +
            "• App name: DC Radiouhr\n• Description: Radiowecker\n• Data protection officer / E-Mail: deine Adresse\n" +
            "• Redirect URI: genau diese Adresse eintragen:", 14f))
        c.addView(text(Netatmo.REDIRECT, 15f, col("#8CE08C"), true).apply { typeface = Typeface.MONOSPACE })
        val r1 = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        r1.addView(btn("Adresse kopieren") {
            val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            cm.setPrimaryClip(ClipData.newPlainText("Redirect URI", Netatmo.REDIRECT))
            Toast.makeText(this, "Kopiert", Toast.LENGTH_SHORT).show()
        }, LinearLayout.LayoutParams(0, dp(46), 1f).apply { rightMargin = dp(6) })
        r1.addView(btn("Portal öffnen", true) { open("https://dev.netatmo.com/apps/createanapp") },
            LinearLayout.LayoutParams(0, dp(46), 1f).apply { leftMargin = dp(6) })
        c.addView(r1)

        // 2
        c = step(l, 2, "Client ID und Secret eintragen")
        c.addView(text("Nach dem Speichern zeigt Netatmo „client ID“ und „client secret“. Beide kopieren und hier einfügen:", 14f))
        etId = field("client ID", prefs.getString("ntClientId", "") ?: "")
        etSecret = field("client secret", prefs.getString("ntClientSecret", "") ?: "")
        pasteRow(c, etId)
        pasteRow(c, etSecret)

        // 3
        c = step(l, 3, "Anmelden und freigeben")
        c.addView(text("Melde dich bei Netatmo an und bestätige den Zugriff „read_station“. Danach ist alles fertig.", 14f))
        c.addView(btn("Mit Netatmo anmelden", true) {
            if (saveCredentials()) startLogin()
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(50)).apply { topMargin = dp(6) })

        // Alternative
        c = step(l, 4, "Alternative: Token-Generator")
        c.addView(text("Falls die Anmeldung nicht klappt: In deiner App im Portal unten bei „Token generator“ den Scope read_station wählen, „Generate Token“ drücken und den Refresh Token hier einfügen.", 14f))
        etRefresh = field("refresh token", "")
        pasteRow(c, etRefresh)
        c.addView(btn("Token speichern und testen") {
            val rt = etRefresh.text.toString().trim()
            if (saveCredentials() && rt.isNotEmpty()) {
                prefs.edit().putString("ntRefresh", rt).remove("ntAccess").putLong("ntExpires", 0L).apply()
                testAndFinish()
            }
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(48)).apply { topMargin = dp(6) })

        setContentView(scroll)
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun startLogin() {
        state = UUID.randomUUID().toString().replace("-", "").take(16)
        val wv = WebView(this)
        web = wv
        wv.settings.javaScriptEnabled = true
        wv.settings.domStorageEnabled = true
        CookieManager.getInstance().setAcceptCookie(true)
        wv.webViewClient = object : WebViewClient() {
            @Deprecated("Deprecated in Java")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean = handle(url)

            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                if (handle(url)) view.stopLoading()
            }
        }
        setContentView(wv)
        wv.loadUrl(Netatmo.authorizeUrl(prefs.getString("ntClientId", "") ?: "", state))
    }

    private var handled = false

    private fun handle(url: String): Boolean {
        if (!url.lowercase(Locale.ROOT).startsWith(Netatmo.REDIRECT)) return false
        if (handled) return true
        handled = true
        val uri = Uri.parse(url)
        val code = uri.getQueryParameter("code")
        val err = uri.getQueryParameter("error")
        main.post {
            web?.destroy()
            web = null
            if (code.isNullOrEmpty()) {
                handled = false
                showSteps()
                Toast.makeText(this, "Anmeldung abgebrochen${if (err != null) ": $err" else ""}", Toast.LENGTH_LONG).show()
            } else {
                showSteps()
                Toast.makeText(this, "Angemeldet – hole Token …", Toast.LENGTH_SHORT).show()
                io.execute {
                    try {
                        Netatmo.exchangeCode(prefs, code)
                        main.post { testAndFinish() }
                    } catch (e: Exception) {
                        main.post {
                            handled = false
                            Toast.makeText(this, "Token-Fehler: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
        return true
    }

    private fun testAndFinish() {
        io.execute {
            val msg = try {
                val dev = Netatmo.fetch(prefs)
                if (dev == null) null else {
                    val name = dev.optString("station_name", dev.optString("module_name", "Station"))
                    val out = Netatmo.outdoor(dev)?.optJSONObject("dashboard_data")?.optDouble("Temperature", Double.NaN) ?: Double.NaN
                    "Verbunden mit „$name“ – außen ${Wx.deg1(out)}"
                }
            } catch (e: Exception) {
                "ERR" + (e.message ?: "unbekannter Fehler")
            }
            main.post {
                when {
                    msg == null -> Toast.makeText(this, "Verbunden, aber keine Wetterstation im Konto gefunden.", Toast.LENGTH_LONG).show()
                    msg.startsWith("ERR") -> {
                        Netatmo.disconnect(prefs)
                        Toast.makeText(this, "Fehler: ${msg.removePrefix("ERR")}", Toast.LENGTH_LONG).show()
                    }
                    else -> {
                        prefs.edit().putBoolean("ntOutdoor", true).apply()
                        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
                        finish()
                    }
                }
            }
        }
    }
}
