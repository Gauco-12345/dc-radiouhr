package de.datacreate.radiouhr

import android.app.Activity
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Schnittstelle zur Hauptseite (Player, Hintergrund-Threads, Bilder). */
interface PodcastHost {
    fun skin(): Skin
    fun loadNew(done: (Int) -> Unit)
    val activity: Activity
    val prefs: SharedPreferences
    fun io(r: () -> Unit)
    fun ui(r: () -> Unit)
    fun bitmap(url: String): Bitmap?
    fun play(pod: Podcast.Pod, ep: Podcast.Ep)
    fun toggle()
    fun seekBy(ms: Long)
    fun seekTo(ms: Long)
    fun position(): Long
    fun duration(): Long
    fun isPlaying(): Boolean
    fun current(): Pair<Podcast.Pod, Podcast.Ep>?
    fun speed(): Float
    fun setSpeed(f: Float)
    fun accent(): Int
    /** Bild aus der Mediathek (content://), höchstens max Pixel breit/hoch. */
    fun localBitmap(uri: android.net.Uri, max: Int): Bitmap?
    fun playVideo(uri: android.net.Uri, title: String)
    /** Warteschlange für „nächste Datei automatisch“ (Mediathek). */
    fun setQueue(q: List<Podcast.Ep>)
    fun openSettings(tab: Int)
}

/** Vollbild-Bereich für Podcasts: Abos, Suche, Folgen, Player. */
class PodcastPanel(private val h: PodcastHost, private val media: Boolean = false) {
    private val a = h.activity
    private val d = a.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()

    private var root: LinearLayout? = null
    private lateinit var title: TextView
    private lateinit var list: LinearLayout
    private lateinit var scroll: ScrollView
    private lateinit var bar: LinearLayout
    private lateinit var barTitle: TextView
    private lateinit var barSub: TextView
    private lateinit var barArt: ImageView
    private lateinit var seek: SeekBar
    private lateinit var tPos: TextView
    private lateinit var tLeft: TextView
    private lateinit var bPlay: TextView
    private lateinit var bSpeed: TextView
    private lateinit var barText: LinearLayout
    private lateinit var ctrRow: LinearLayout
    private lateinit var timesRow: LinearLayout
    private var land = false
    private var dragging = false
    private var screen = "home"
    private var curPod: Podcast.Pod? = null
    private var lastQuery = ""
    private val dateFmt = SimpleDateFormat("d. MMM yyyy", Locale.GERMANY)

    val visible get() = root != null
    var onClose: (() -> Unit)? = null
    fun rootView(): View? = root

    private var L = Look(h.skin())
    private val card get() = L.card
    private val sub get() = L.sub
    private val fg get() = L.fg

    // ------------------------------------------------------------ Öffnen / Schließen

    private var keepState = false
    private var lastEps: Pair<Podcast.Pod, List<Podcast.Ep>>? = null
    private val statusViews = HashMap<String, TextView>()

    /** Nach dem Drehen neu aufbauen und die aktuelle Seite behalten. */
    fun relayout(frame: FrameLayout) {
        hide(keep = true)
        keepState = true
        show(frame)
    }

    fun show(frame: FrameLayout) {
        if (root != null) return
        land = a.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        L = Look(h.skin())
        val r = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            background = BgDrawable(L.k.bg, L.k, d)
            isClickable = true
        }
        // Kopf
        val head = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(8), dp(8), dp(8), dp(4))
        }
        head.addView(btn("‹", 28f) { back() })
        title = TextView(a).apply {
            textSize = 21f
            setTextColor(fg)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        if (!media) head.addView(btn("Suchen", 15f) { showSearch() })
        head.addView(btn("✕", 22f) { hide() })
        r.addView(head)

        scroll = ScrollView(a)
        list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(26))
        }
        scroll.addView(list)
        val body = LinearLayout(a).apply { orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }

        // Player unten
        bar = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(L.bar)
            setPadding(dp(14), dp(10), dp(14), dp(20))
        }
        val top = LinearLayout(a).apply {
            orientation = if (land) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (land) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        barArt = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        if (land) top.addView(barArt, LinearLayout.LayoutParams(dp(150), dp(150)).apply { bottomMargin = dp(12) })
        else top.addView(barArt, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
        val tt = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        barText = tt
        barTitle = TextView(a).apply { textSize = 15f; setTextColor(fg); maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        barSub = TextView(a).apply { textSize = 12f; setTextColor(sub); setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }
        tt.addView(barTitle)
        tt.addView(barSub)
        if (land) {
            barTitle.gravity = Gravity.CENTER
            barSub.gravity = Gravity.CENTER
            barTitle.maxLines = 3
            top.addView(tt, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else top.addView(tt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(top)
        seek = SeekBar(a).apply {
            progressTintList = android.content.res.ColorStateList.valueOf(L.accent)
            thumbTintList = android.content.res.ColorStateList.valueOf(L.accent)
            progressBackgroundTintList = android.content.res.ColorStateList.valueOf(L.sub)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(sb: SeekBar?, v: Int, fromUser: Boolean) {
                    if (fromUser) tPos.text = Podcast.fmtTime(v * 1000L)
                }
                override fun onStartTrackingTouch(sb: SeekBar?) { dragging = true }
                override fun onStopTrackingTouch(sb: SeekBar?) {
                    dragging = false
                    h.seekTo((sb?.progress ?: 0) * 1000L)
                }
            })
        }
        bar.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(6) })
        val times = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        tPos = TextView(a).apply { textSize = 12f; setTextColor(sub) }
        tLeft = TextView(a).apply { textSize = 12f; setTextColor(sub); gravity = Gravity.END }
        times.addView(tPos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        times.addView(tLeft, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        bar.addView(times)
        timesRow = times
        val ctr = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        ctr.addView(round("−15") { h.seekBy(-15_000) })
        bPlay = round("▶", big = true) { h.toggle(); refresh() }
        ctr.addView(bPlay)
        ctr.addView(round("+30") { h.seekBy(30_000) })
        bSpeed = round("1,0×") {
            val steps = floatArrayOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)
            val cur = h.speed()
            val next = steps.firstOrNull { it > cur + 0.01f } ?: steps[0]
            h.setSpeed(next)
            refresh()
        }
        ctr.addView(bSpeed)
        bar.addView(ctr)
        ctrRow = ctr
        bar.addOnLayoutChangeListener { _, l, t, rr, b, ol, ot, orr, ob ->
            if (rr - l != orr - ol || b - t != ob - ot) bar.post { fitBar() }
        }
        if (land) {
            // links 1/3 Steuerung, rechts 2/3 Liste
            bar.gravity = Gravity.CENTER
            body.addView(bar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f))
            body.addView(scroll, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 2f))
        } else {
            body.addView(scroll, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
            body.addView(bar)
        }
        r.addView(body, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))

        frame.addView(r, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        r.isFocusableInTouchMode = true
        r.requestFocus()
        r.setOnKeyListener { _, code, e ->
            if (code == KeyEvent.KEYCODE_BACK && e.action == KeyEvent.ACTION_UP) { back(); true } else false
        }
        root = r
        barKey = ""
        val c = h.current()
        if (media) {
            keepState = false
            showMediaFolder()
        } else if (keepState) {
            keepState = false
            when (screen) {
                "episodes" -> curPod?.let { showEpisodes(it) } ?: showHome()
                "search" -> showSearch()
                "results" -> search(lastQuery)
                else -> showHome()
            }
        } else if (c != null && screen == "home") showEpisodes(c.first) else showHome()
        refresh()
    }

    fun hide(keep: Boolean = false) {
        closeViewer()
        root?.let { (it.parent as? ViewGroup)?.removeView(it) }
        root = null
        statusViews.clear()
        if (!keep) {
            screen = "home"
            onClose?.invoke()
        }
    }

    fun back() {
        if (viewer != null) { closeViewer(); return }
        if (media) {
            if (folders.size > 1) { folders.removeAt(folders.size - 1); showMediaFolder() } else hide()
            return
        }
        when (screen) {
            "home" -> hide()
            "results" -> showSearch()
            "episodes" -> if (epBack == "results") search(lastQuery) else showHome()
            else -> showHome()
        }
    }

    // ------------------------------------------------------------ Bausteine

    private fun btn(t: String, sp: Float, onClick: () -> Unit) = TextView(a).apply {
        text = t
        textSize = sp
        setTextColor(fg)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { onClick() }
    }

    private fun round(t: String, big: Boolean = false, onClick: () -> Unit) = TextView(a).apply {
        text = t
        textSize = if (big) 24f else 15f
        setTextColor(if (big) L.onAccent else fg)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (big) h.accent() else col("#2A3138"))
        }
        val s = if (big) dp(64) else dp(52)
        layoutParams = LinearLayout.LayoutParams(s, s).apply { setMargins(dp(6), 0, dp(6), 0) }
        setOnClickListener { onClick() }
    }

    private fun msg(t: String) {
        list.addView(TextView(a).apply {
            text = t
            textSize = 14f
            setTextColor(sub)
            setPadding(dp(6), dp(16), dp(6), dp(16))
        })
    }

    private fun art(iv: ImageView, url: String) {
        iv.setImageDrawable(GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(L.chip) })
        if (url.isEmpty()) return
        iv.tag = url
        h.io {
            val b = if (url.startsWith("content:")) h.localBitmap(android.net.Uri.parse(url), 300) else h.bitmap(url)
            h.ui { if (b != null && iv.tag == url) iv.setImageBitmap(b) }
        }
    }

    private fun row(artUrl: String, t1: String, t2: String, dim: Boolean = false, onClick: () -> Unit, onLong: (() -> Unit)? = null, into: LinearLayout? = null): LinearLayout {
        val r = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(10), dp(8), dp(10), dp(8))
            background = GradientDrawable().apply { cornerRadius = dp(12).toFloat(); setColor(card) }
            setOnClickListener { onClick() }
            if (onLong != null) setOnLongClickListener { onLong(); true }
        }
        if (artUrl.isNotEmpty()) {
            val iv = ImageView(a).apply {
                scaleType = ImageView.ScaleType.CENTER_CROP
                clipToOutline = true
                background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(L.chip) }
            }
            art(iv, artUrl)
            r.addView(iv, LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
        }
        val tt = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        tt.addView(TextView(a).apply {
            text = t1
            textSize = 15f
            setTextColor(if (dim) sub else fg)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (t2.isNotEmpty()) tt.addView(TextView(a).apply {
            tag = "t2"
            text = t2
            textSize = 12f
            setTextColor(sub)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        r.addView(tt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        (into ?: list).addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
            setMargins(0, dp(4), 0, dp(4))
        })
        return r
    }

    // ------------------------------------------------------------ Seiten

    fun showHome() {
        screen = "home"
        title.text = "Podcasts"
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val subs = Podcast.subs(h.prefs)
        val dls = Podcast.downloads(a, h.prefs)
        if (subs.isNotEmpty()) {
            val busy = Podcast.progress.size
            list.addView(btn(if (busy > 0) "⬇  Lädt $busy Folge(n) …  · nochmal prüfen" else "⬇  Neue Folgen jetzt herunterladen", 15f) {
                Toast.makeText(a, "Suche neue Folgen …", Toast.LENGTH_SHORT).show()
                h.loadNew { n ->
                    Toast.makeText(a, if (n == 0) "Keine neuen Folgen – alles schon da" else "$n neue Folge(n) werden heruntergeladen", Toast.LENGTH_LONG).show()
                    if (root != null && screen == "home") showHome()
                }
            }.apply {
                setTextColor(L.onAccent)
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(12), dp(12), dp(12))
                background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(h.accent()) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), dp(6), dp(4), dp(6))
            })
        }
        if (dls.isNotEmpty()) {
            list.addView(TextView(a).apply {
                text = "HERUNTERGELADEN  (lange drücken = löschen)"
                textSize = 12f
                setTextColor(h.accent())
                setPadding(dp(6), dp(8), 0, dp(4))
            })
            for ((pod, ep) in dls) {
                row(ep.art.ifEmpty { pod.art }, ep.title, pod.title + (if (ep.durationSec > 0) " · " + Podcast.fmtTime(ep.durationSec * 1000) else ""), onClick = {
                    h.play(pod, ep)
                    refresh()
                }, onLong = {
                    Podcast.deleteDownload(a, h.prefs, ep)
                    Toast.makeText(a, "Download gelöscht", Toast.LENGTH_SHORT).show()
                    showHome()
                })
            }
        }
        if (subs.isEmpty()) {
            msg("Noch keine Podcasts abonniert.\nOben auf „Suchen“ tippen, z. B. nach „Tagesschau“, „Deutschlandfunk“ oder einem Thema.")
            return
        }
        list.addView(TextView(a).apply {
            text = "MEINE ABOS  (lange drücken = abbestellen)"
            textSize = 12f
            setTextColor(h.accent())
            setPadding(dp(6), dp(8), 0, dp(4))
        })
        for (p in subs) {
            row(p.art, p.title, p.author, onClick = { showEpisodes(p) }, onLong = {
                android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setMessage("„${p.title}“ abbestellen?")
                    .setPositiveButton("Abbestellen") { _, _ -> Podcast.toggleSub(h.prefs, p); showHome() }
                    .setNegativeButton("Abbrechen", null)
                    .show()
            })
        }
    }

    fun showSearch() {
        screen = "search"
        title.text = "Podcast suchen"
        list.removeAllViews()
        val et = EditText(a).apply {
            hint = "Name, Thema oder RSS-Adresse"
            setText(lastQuery)
            setSingleLine(true)
            setTextColor(fg)
            setHintTextColor(sub)
            imeOptions = EditorInfo.IME_ACTION_SEARCH
        }
        val go = {
            val q = et.text.toString().trim()
            if (q.isNotEmpty()) {
                (a.getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager).hideSoftInputFromWindow(et.windowToken, 0)
                search(q)
            }
        }
        et.setOnEditorActionListener { _, id, _ -> if (id == EditorInfo.IME_ACTION_SEARCH) { go(); true } else false }
        val r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL }
        r.addView(et, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        r.addView(btn("Los", 16f) { go() })
        list.addView(r)
        msg("Gesucht wird nach Podcasts und nach einzelnen Folgen – z. B. „Toskana“ findet auch Folgen anderer Podcasts, die so heißen.\n\nStatt eines Namens kannst du auch die RSS-Adresse eines Podcasts einfügen.")
        et.requestFocus()
    }

    private fun search(q: String) {
        lastQuery = q
        screen = "results"
        title.text = "„$q“"
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        msg("Suche …")
        h.io {
            val pods: List<Podcast.Pod>
            val eps = ArrayList<Pair<Podcast.Pod, Podcast.Ep>>()
            try {
                if (q.startsWith("http")) {
                    pods = listOfNotNull(Podcast.feed(a, q, true)?.first)
                } else {
                    pods = Podcast.search(q)
                    // zuerst passende Folgen aus den eigenen Abos (alle Folgen des Feeds)
                    val low = q.lowercase(Locale.GERMANY)
                    for (sp in Podcast.subs(h.prefs)) {
                        val f = Podcast.cachedFeed(a, sp.feed) ?: continue
                        val pod = if (f.first.art.isEmpty()) sp else f.first
                        for (e in f.second) if (e.title.lowercase(Locale.GERMANY).contains(low)) eps.add(pod to e)
                    }
                    val more = try { Podcast.searchEpisodes(q) } catch (_: Exception) { emptyList() }
                    for (x in more) if (eps.none { it.second.url == x.second.url }) eps.add(x)
                }
            } catch (e: Exception) {
                h.ui { if (screen == "results") { list.removeAllViews(); msg("Suche nicht möglich: ${e.message}") } }
                return@io
            }
            h.ui {
                if (screen != "results" || lastQuery != q) return@ui
                list.removeAllViews()
                if (pods.isEmpty() && eps.isEmpty()) msg("Nichts gefunden.")
                if (pods.isNotEmpty()) list.addView(head("PODCASTS"))
                for (p in pods.take(if (eps.isEmpty()) 40 else 12)) {
                    val subbed = Podcast.isSub(h.prefs, p.feed)
                    val rw = row(p.art, p.title, p.author, onClick = { showEpisodes(p) })
                    rw.addView(TextView(a).apply {
                        text = if (subbed) "✓" else "＋ Abo"
                        textSize = 14f
                        setTextColor(if (subbed) h.accent() else fg)
                        setPadding(dp(10), dp(8), dp(4), dp(8))
                        setOnClickListener {
                            val now = Podcast.toggleSub(h.prefs, p)
                            text = if (now) "✓" else "＋ Abo"
                            setTextColor(if (now) h.accent() else fg)
                            Toast.makeText(a, if (now) "Abonniert" else "Abo entfernt", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
                if (eps.isNotEmpty()) list.addView(head("FOLGEN  (tippen = abspielen · lange drücken = ganzer Podcast)"))
                val cur = h.current()
                for ((p, e) in eps) {
                    val info = StringBuilder(p.title)
                    if (e.date > 0) info.append(" · ").append(dateFmt.format(Date(e.date)))
                    if (e.durationSec > 0) info.append(" · ").append(Podcast.fmtTime(e.durationSec * 1000))
                    if (cur?.second?.url == e.url) info.append(" · ▶ läuft")
                    row(p.art, e.title, info.toString(), onClick = {
                        h.play(p, e)
                        refresh()
                        Toast.makeText(a, "„${e.title}“", Toast.LENGTH_SHORT).show()
                    }, onLong = { showEpisodes(p) })
                }
            }
        }
    }

    private fun head(t: String) = TextView(a).apply {
        text = t
        textSize = 12f
        setTextColor(h.accent())
        setPadding(dp(6), dp(12), 0, dp(4))
    }

    private var epBack = "home"
    private var epFilter = ""

    fun showEpisodes(pod: Podcast.Pod) {
        if (screen != "episodes") epBack = screen
        if (curPod?.feed != pod.feed) epFilter = ""
        screen = "episodes"
        curPod = pod
        title.text = pod.title
        list.removeAllViews()
        scroll.scrollTo(0, 0)
        val cached = Podcast.cachedFeed(a, pod.feed)
        if (cached != null) renderEpisodes(cached.first, cached.second) else msg("Folgen werden geladen …")
        h.io {
            val res = try { Podcast.feed(a, pod.feed, force = true) } catch (e: Exception) {
                h.ui { if (cached == null && curPod == pod) { list.removeAllViews(); msg("Feed nicht erreichbar: ${e.message}") } }
                null
            }
            if (res != null) h.ui { if (screen == "episodes" && curPod == pod) renderEpisodes(res.first, res.second) }
        }
    }

    private fun startDownload(pod: Podcast.Pod, e: Podcast.Ep) {
        Toast.makeText(a, "Download gestartet", Toast.LENGTH_SHORT).show()
        Podcast.download(a, h.prefs, pod, e) { ok ->
            h.ui {
                Toast.makeText(a, if (ok) "„${e.title}“ heruntergeladen" else "Download fehlgeschlagen", Toast.LENGTH_SHORT).show()
                updateStatus()
            }
        }
    }

    private fun updateStatus() {
        for ((url, tv) in statusViews) {
            val pr = Podcast.progress[url]
            val ep = lastEps?.second?.firstOrNull { it.url == url }
            when {
                pr != null -> { tv.text = "⬇ $pr %"; tv.setTextColor(h.accent()) }
                ep != null && Podcast.isDownloaded(a, ep) -> { tv.text = "✓⬇"; tv.setTextColor(h.accent()) }
                else -> { tv.text = "⬇"; tv.setTextColor(fg) }
            }
        }
    }

    private fun renderEpisodes(p0: Podcast.Pod, eps: List<Podcast.Ep>) {
        lastEps = p0 to eps
        statusViews.clear()
        val pod = if (p0.art.isEmpty() && curPod != null) Podcast.Pod(p0.feed, p0.title, p0.author, curPod!!.art) else p0
        val y = scroll.scrollY
        list.removeAllViews()
        // Kopf mit Abo-Knopf
        val subbed = Podcast.isSub(h.prefs, pod.feed)
        val hd = row(pod.art, pod.title, pod.author, onClick = {})
        hd.addView(TextView(a).apply {
            text = if (subbed) "✓ Abonniert" else "＋ Abonnieren"
            textSize = 14f
            setTextColor(if (subbed) h.accent() else L.onAccent)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (subbed) L.chip else h.accent())
            }
            setOnClickListener { Podcast.toggleSub(h.prefs, pod); renderEpisodes(p0, eps) }
        })
        val box = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        if (eps.size > 6) {
            val et = EditText(a).apply {
                hint = "🔍  In ${eps.size} Folgen suchen …"
                setText(epFilter)
                setSingleLine(true)
                textSize = 15f
                setTextColor(fg)
                setHintTextColor(sub)
                imeOptions = EditorInfo.IME_ACTION_DONE
                addTextChangedListener(object : android.text.TextWatcher {
                    override fun beforeTextChanged(x: CharSequence?, st: Int, c: Int, af: Int) {}
                    override fun onTextChanged(x: CharSequence?, st: Int, b: Int, c: Int) {}
                    override fun afterTextChanged(x: android.text.Editable?) {
                        val f = x?.toString()?.trim() ?: ""
                        if (f == epFilter) return
                        epFilter = f
                        fillEpisodes(pod, p0, eps, box)
                    }
                })
            }
            list.addView(et, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                setMargins(dp(4), dp(6), dp(4), 0)
            })
        }
        list.addView(box)
        fillEpisodes(pod, p0, eps, box)
        if (eps.isEmpty()) msg("Keine Folgen gefunden.")
        else msg("⬇ = herunterladen (zum Hören ohne Internet) · lange drücken = gehört / Download löschen")
        scroll.post { scroll.scrollTo(0, y) }
    }

    private fun fillEpisodes(pod: Podcast.Pod, p0: Podcast.Pod, all: List<Podcast.Ep>, box: LinearLayout) {
        box.removeAllViews()
        statusViews.clear()
        val low = epFilter.lowercase(Locale.GERMANY)
        val eps = if (low.isEmpty()) all else all.filter { it.title.lowercase(Locale.GERMANY).contains(low) }
        if (eps.isEmpty() && all.isNotEmpty()) box.addView(TextView(a).apply {
            text = "Keine Folge mit „$epFilter“."
            textSize = 14f
            setTextColor(sub)
            setPadding(dp(6), dp(16), dp(6), dp(16))
        })
        val cur = h.current()
        for (e in eps) {
            val pos = Podcast.pos(h.prefs, e)
            val done = Podcast.done(h.prefs, e)
            val playing = cur?.second?.url == e.url
            val info = StringBuilder()
            if (e.date > 0) info.append(dateFmt.format(Date(e.date)))
            if (e.durationSec > 0) info.append(" · ").append(Podcast.fmtTime(e.durationSec * 1000))
            when {
                playing -> info.append(" · ▶ läuft")
                done -> info.append(" · ✓ gehört")
                pos > 0 && e.durationSec > 0 -> info.append(" · noch ").append(Podcast.fmtTime(e.durationSec * 1000 - pos))
                pos > 0 -> info.append(" · angefangen")
            }
            val rw = row("", e.title, info.toString(), dim = done && !playing, into = box, onClick = {
                h.play(pod, e)
                renderEpisodes(p0, all)
            }, onLong = {
                val dl = Podcast.isDownloaded(a, e)
                val items = arrayOf(if (done) "Als ungehört markieren" else "Als gehört markieren",
                    if (dl) "Download löschen" else "Herunterladen")
                android.app.AlertDialog.Builder(a, android.R.style.Theme_Material_Dialog_Alert)
                    .setTitle(e.title)
                    .setItems(items) { _, w ->
                        if (w == 0) Podcast.setDone(h.prefs, e, !done)
                        else if (dl) Podcast.deleteDownload(a, h.prefs, e)
                        else startDownload(pod, e)
                        renderEpisodes(p0, all)
                    }
                    .show()
            })
            val st = TextView(a).apply {
                textSize = 15f
                gravity = Gravity.CENTER
                setPadding(dp(12), dp(10), dp(6), dp(10))
                setOnClickListener {
                    when {
                        Podcast.progress.containsKey(e.url) -> {}
                        Podcast.isDownloaded(a, e) -> Toast.makeText(a, "Heruntergeladen (${Podcast.sizeMb(a, e)}) – lange drücken zum Löschen", Toast.LENGTH_SHORT).show()
                        else -> { startDownload(pod, e); updateStatus() }
                    }
                }
            }
            statusViews[e.url] = st
            rw.addView(st)
        }
        updateStatus()
    }

    // ------------------------------------------------------------ Mediathek

    /** Ordnerpfad: (docId, Name); erstes Element = gewählter Hauptordner. */
    private val folders = ArrayList<Pair<String, String>>()
    private var mediaTree = ""
    private val coverCache = java.util.concurrent.ConcurrentHashMap<String, android.net.Uri>()
    private var viewer: View? = null

    /** Wurde in den Einstellungen ein anderer Mediathek-Ordner gewählt? */
    fun mediaStale() = media && (h.prefs.getString("mediaTree", "") ?: "") != mediaTree

    private fun showMediaFolder() {
        screen = "media"
        list.removeAllViews()
        val tree = h.prefs.getString("mediaTree", "") ?: ""
        if (tree.isEmpty()) {
            title.text = "Mediathek"
            msg("Noch kein Ordner gewählt.\n\nIn den Einstellungen unter „Mediathek“ einen Ordner wählen, z. B. „Hörspiele“ mit Unterordnern wie „Pippi Langstrumpf“. Darin können MP3s, Videos, Bilder und PDFs liegen.")
            list.addView(btn("Ordner wählen …", 17f) { h.openSettings(9) }.apply {
                setTextColor(L.onAccent)
                background = GradientDrawable().apply { cornerRadius = dp(22).toFloat(); setColor(h.accent()) }
            }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
                gravity = Gravity.CENTER_HORIZONTAL; topMargin = dp(8)
            })
            return
        }
        if (tree != mediaTree || folders.isEmpty()) {
            mediaTree = tree
            folders.clear()
            coverCache.clear()
            val rid = try { Media.rootId(tree) } catch (_: Exception) { "" }
            folders.add(rid to "Mediathek")
        }
        val (docId, name) = folders.last()
        title.text = if (folders.size == 1) "Mediathek" else name
        scroll.scrollTo(0, 0)
        msg("Lade …")
        h.io {
            val items = try { Media.list(a, tree, docId) } catch (e: Exception) {
                h.ui {
                    if (screen != "media") return@ui
                    list.removeAllViews()
                    msg("Ordner nicht lesbar – bitte in den Einstellungen neu wählen.\n(${e.message})")
                }
                return@io
            }
            h.ui { if (screen == "media" && folders.lastOrNull()?.first == docId) renderMedia(items) }
        }
    }

    private fun renderMedia(items: List<Media.Item>) {
        list.removeAllViews()
        val dirs = items.filter { it.dir }
        val cover = Media.cover(items)
        val files = items.filter { !it.dir && it !== cover }
        if (items.isEmpty()) msg("Dieser Ordner ist leer.")
        val big = h.prefs.getBoolean("mediaBig", true)
        if (dirs.isNotEmpty()) {
            if (big) tiles(dirs) else for (d in dirs) {
                val rw = row("", "📁  " + d.name, "", onClick = { open(d) })
                rw.minimumHeight = dp(56)
            }
        }
        val audio = files.filter { it.kind == "audio" }
        mediaArt = cover?.uri?.toString() ?: ""
        val pod = Podcast.Pod("media:" + folders.last().first, folders.last().second, "Mediathek", mediaArt)
        val eps = audio.map { epOf(it) }
        val cur = h.current()
        val pending = ArrayList<Pair<Media.Item, () -> Unit>>()
        for (f in files) {
            val icon = when (f.kind) { "audio" -> "♪"; "video" -> "🎬"; "image" -> "🖼"; else -> "📄" }
            val timed = f.kind == "audio" || f.kind == "video"
            var state = ""
            if (f.kind == "audio") {
                val ep = epOf(f)
                val pos = Podcast.pos(h.prefs, ep)
                state = when {
                    cur?.second?.url == ep.url -> "▶ läuft"
                    Podcast.done(h.prefs, ep) -> "✓ gehört"
                    pos > 0 -> "bei " + Podcast.fmtTime(pos)
                    else -> ""
                }
            }
            val known = durMs[f.docId]
            fun label(ms: Long?): String = listOf(
                if (ms == null) "…" else if (ms > 0) Podcast.fmtTime(ms) else "",
                state
            ).filter { it.isNotEmpty() }.joinToString(" · ")
            val info = when {
                timed -> label(known)
                f.kind == "image" -> ""
                else -> Media.sizeLabel(f.size)
            }
            val rw = row("", f.title, info, onClick = {
                when (f.kind) {
                    "audio" -> {
                        val i = audio.indexOf(f)
                        h.setQueue(eps.drop(i + 1))
                        h.play(pod, eps[i])
                        renderMedia(items)
                        refresh()
                    }
                    "video" -> h.playVideo(f.uri, f.title)
                    "image" -> showImage(f)
                    else -> openDoc(f)
                }
            }, onLong = if (f.kind == "audio") ({
                val ep = epOf(f)
                Podcast.setDone(h.prefs, ep, !Podcast.done(h.prefs, ep))
                renderMedia(items)
            }) else null)
            rw.addView(TextView(a).apply {
                text = icon
                textSize = 20f
                setTextColor(fg)
                gravity = Gravity.CENTER
                setPadding(dp(10), 0, dp(4), 0)
            }, 0)
            if (timed && known == null) {
                val tv = rw.findViewWithTag<TextView>("t2")
                if (tv != null) pending.add(f to { tv.text = label(durMs[f.docId]) })
            }
        }
        if (pending.isNotEmpty()) {
            val folder = folders.lastOrNull()?.first
            h.io {
                for ((f, upd) in pending) {
                    if (screen != "media" || folders.lastOrNull()?.first != folder) return@io
                    durMs[f.docId] = try { Media.duration(a, f) } catch (_: Throwable) { 0L }
                    h.ui { if (root != null) upd() }
                }
            }
        }
    }

    private val durMs = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private var mediaArt = ""

    private fun epOf(f: Media.Item) = Podcast.Ep(f.docId, f.title, 0L, f.uri.toString(), (durMs[f.docId] ?: 0L) / 1000, mediaArt)

    private fun open(d: Media.Item) {
        folders.add(d.docId to d.name)
        showMediaFolder()
    }

    /** Große Bild-Kacheln für Unterordner (für Kinder gut zu treffen). */
    private fun tiles(dirs: List<Media.Item>) {
        val cols = if (land) 4 else 2
        val tileH = dp(if (land) 150 else 170)
        var r: LinearLayout? = null
        dirs.forEachIndexed { i, d ->
            if (i % cols == 0) {
                r = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
                list.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, tileH).apply { setMargins(0, dp(4), 0, dp(4)) })
            }
            val t = FrameLayout(a).apply {
                background = GradientDrawable().apply { cornerRadius = dp(16).toFloat(); setColor(tileColor(d.name)) }
                clipToOutline = true
                setOnClickListener { open(d) }
            }
            val iv = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
            val ph = TextView(a).apply {
                text = d.name.trim().take(1).uppercase(Locale.GERMANY)
                textSize = 48f
                setTextColor(Color.argb(150, 255, 255, 255))
                gravity = Gravity.CENTER
            }
            t.addView(ph, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            t.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            t.addView(TextView(a).apply {
                text = d.name
                textSize = 16f
                setTextColor(Color.WHITE)
                maxLines = 2
                ellipsize = TextUtils.TruncateAt.END
                setPadding(dp(10), dp(6), dp(10), dp(8))
                setBackgroundColor(Color.argb(150, 0, 0, 0))
            }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM))
            r!!.addView(t, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f).apply { setMargins(dp(4), 0, dp(4), 0) })
            loadCover(d, iv)
        }
        val rest = (cols - dirs.size % cols) % cols
        repeat(rest) { r?.addView(View(a), LinearLayout.LayoutParams(0, 1, 1f).apply { setMargins(dp(4), 0, dp(4), 0) }) }
    }

    private fun tileColor(name: String): Int {
        val c = intArrayOf(col("#E4572E"), col("#29A3A3"), col("#F3A712"), col("#6A4C93"), col("#4CAF50"), col("#1E88E5"), col("#D81B60"), col("#8D6E63"))
        return c[Math.abs(name.hashCode()) % c.size]
    }

    private fun loadCover(d: Media.Item, iv: ImageView) {
        iv.tag = d.docId
        h.io {
            val u = coverCache[d.docId] ?: run {
                val c = try { Media.cover(Media.list(a, mediaTree, d.docId))?.uri } catch (_: Exception) { null }
                (c ?: android.net.Uri.EMPTY).also { coverCache[d.docId] = it }
            }
            val b = if (u == android.net.Uri.EMPTY) null else h.localBitmap(u, 400)
            if (b != null) h.ui { if (iv.tag == d.docId) iv.setImageBitmap(b) }
        }
    }

    private fun openDoc(f: Media.Item) {
        try {
            a.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW)
                .setDataAndType(f.uri, f.mime.ifEmpty { "*/*" })
                .addFlags(android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION))
        } catch (_: Exception) {
            Toast.makeText(a, "Keine App zum Öffnen von „${f.name}“ gefunden", Toast.LENGTH_LONG).show()
        }
    }

    private fun showImage(f: Media.Item) {
        val fr = root ?: return
        val box = FrameLayout(a).apply {
            setBackgroundColor(Color.BLACK)
            isClickable = true
            setOnClickListener { closeViewer() }
        }
        val iv = ImageView(a).apply { scaleType = ImageView.ScaleType.FIT_CENTER }
        box.addView(iv, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        box.addView(btn("✕", 26f) { closeViewer() }, FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.TOP or Gravity.END))
        (fr.parent as? ViewGroup)?.addView(box, FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        viewer = box
        h.io {
            val b = h.localBitmap(f.uri, 2048)
            h.ui { if (viewer === box) { if (b != null) iv.setImageBitmap(b) else { closeViewer(); openDoc(f) } } }
        }
    }

    private fun closeViewer() {
        viewer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        viewer = null
    }

    // ------------------------------------------------------------ Player-Leiste

    /** Tasten und Bild so verkleinern, dass im schmalen/niedrigen Bereich nichts abgeschnitten wird. */
    private fun fitBar() {
        if (root == null || bar.width == 0) return
        val w = bar.width - bar.paddingLeft - bar.paddingRight
        val m = dp(6)
        // 3 kleine + 1 große Taste (1,25×) + 8 Ränder
        val small = ((w - 8 * m) / 4.25f).toInt().coerceIn(dp(34), dp(52))
        val big = (small * 1.25f).toInt()
        for (n in 0 until ctrRow.childCount) {
            val v = ctrRow.getChildAt(n) as TextView
            val s = if (v === bPlay) big else small
            val lp = v.layoutParams as LinearLayout.LayoutParams
            if (lp.width != s) {
                lp.width = s; lp.height = s
                v.layoutParams = lp
                v.textSize = (if (v === bPlay) 24f else 15f) * s / (if (v === bPlay) dp(64) else dp(52)).toFloat()
            }
        }
        if (!land) return
        // Querformat: Coverbild nur so groß, wie Platz ist
        val inner = bar.height - bar.paddingTop - bar.paddingBottom
        val need = barText.height + dp(42) + timesRow.height + big + dp(6) + dp(12)
        val free = inner - need
        val lp = barArt.layoutParams as LinearLayout.LayoutParams
        val s = free.coerceAtMost(dp(150))
        val want = if (s < dp(44)) 0 else s
        if (lp.width != want) {
            lp.width = want; lp.height = want
            barArt.visibility = if (want == 0) View.GONE else View.VISIBLE
            barArt.layoutParams = lp
        }
    }

    /** Ein Download ist fertig: Startseite aktualisieren. */
    fun downloadsChanged() {
        if (root != null && screen == "home") showHome()
    }

    private var barKey = ""

    fun refresh() {
        if (root == null) return
        if (Podcast.progress.isNotEmpty()) updateStatus()
        val c = h.current()
        bar.visibility = if (c == null) View.GONE else View.VISIBLE
        if (c == null) return
        if (barKey != c.second.url) {
            barKey = c.second.url
            barTitle.text = c.second.title
            barSub.text = c.first.title
            art(barArt, c.second.art.ifEmpty { c.first.art })
        }
        val dur = h.duration()
        val pos = h.position()
        if (!dragging) {
            seek.max = (dur / 1000).toInt().coerceAtLeast(1)
            seek.progress = (pos / 1000).toInt()
            tPos.text = Podcast.fmtTime(pos)
        }
        tLeft.text = if (dur > 0) "−" + Podcast.fmtTime(dur - pos) else ""
        bPlay.text = if (h.isPlaying()) "❚❚" else "▶"
        bSpeed.text = String.format(Locale.GERMANY, "%.1f×", h.speed()).replace(".0×", ",0×")
    }
}
