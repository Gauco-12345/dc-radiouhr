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
}

/** Vollbild-Bereich für Podcasts: Abos, Suche, Folgen, Player. */
class PodcastPanel(private val h: PodcastHost) {
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
    private var dragging = false
    private var screen = "home"
    private var curPod: Podcast.Pod? = null
    private var lastQuery = ""
    private val dateFmt = SimpleDateFormat("d. MMM yyyy", Locale.GERMANY)

    val visible get() = root != null

    private val bg = col("#0F1215")
    private val card = col("#1B2026")
    private val sub = col("#9AA3AD")

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
        val land = a.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
        val r = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(bg)
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
            setTextColor(Color.WHITE)
            typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
            setSingleLine(true)
            ellipsize = TextUtils.TruncateAt.END
        }
        head.addView(title, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        head.addView(btn("Suchen", 15f) { showSearch() })
        head.addView(btn("✕", 22f) { hide() })
        r.addView(head)

        scroll = ScrollView(a)
        list = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(4), dp(12), dp(12))
        }
        scroll.addView(list)
        val body = LinearLayout(a).apply { orientation = if (land) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL }

        // Player unten
        bar = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(col("#161B20"))
            setPadding(dp(14), dp(10), dp(14), dp(12))
        }
        val top = LinearLayout(a).apply {
            orientation = if (land) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (land) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        barArt = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP }
        if (land) top.addView(barArt, LinearLayout.LayoutParams(dp(150), dp(150)).apply { bottomMargin = dp(12) })
        else top.addView(barArt, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
        val tt = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        barTitle = TextView(a).apply { textSize = 15f; setTextColor(Color.WHITE); maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
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
        val c = h.current()
        if (keepState) {
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
        root?.let { (it.parent as? ViewGroup)?.removeView(it) }
        root = null
        statusViews.clear()
        if (!keep) screen = "home"
    }

    fun back() {
        when (screen) {
            "home" -> hide()
            "results" -> showSearch()
            else -> showHome()
        }
    }

    // ------------------------------------------------------------ Bausteine

    private fun btn(t: String, sp: Float, onClick: () -> Unit) = TextView(a).apply {
        text = t
        textSize = sp
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setPadding(dp(12), dp(6), dp(12), dp(6))
        setOnClickListener { onClick() }
    }

    private fun round(t: String, big: Boolean = false, onClick: () -> Unit) = TextView(a).apply {
        text = t
        textSize = if (big) 24f else 15f
        setTextColor(if (big) Color.BLACK else Color.WHITE)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (big) h.accent() else col("#2A3138"))
        }
        val s = if (big) dp(64) else dp(52)
        layoutParams = LinearLayout.LayoutParams(s, s).apply { setMargins(dp(10), 0, dp(10), 0) }
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
        iv.setImageDrawable(GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(col("#2A3138")) })
        if (url.isEmpty()) return
        iv.tag = url
        h.io {
            val b = h.bitmap(url)
            h.ui { if (b != null && iv.tag == url) iv.setImageBitmap(b) }
        }
    }

    private fun row(artUrl: String, t1: String, t2: String, dim: Boolean = false, onClick: () -> Unit, onLong: (() -> Unit)? = null): LinearLayout {
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
                background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(col("#2A3138")) }
            }
            art(iv, artUrl)
            r.addView(iv, LinearLayout.LayoutParams(dp(54), dp(54)).apply { rightMargin = dp(12) })
        }
        val tt = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        tt.addView(TextView(a).apply {
            text = t1
            textSize = 15f
            setTextColor(if (dim) sub else Color.WHITE)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        if (t2.isNotEmpty()) tt.addView(TextView(a).apply {
            text = t2
            textSize = 12f
            setTextColor(sub)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
        })
        r.addView(tt, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        list.addView(r, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT).apply {
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
        if (dls.isNotEmpty()) {
            list.addView(TextView(a).apply {
                text = "HERUNTERGELADEN  (lange drücken = löschen)"
                textSize = 12f
                setTextColor(h.accent())
                setPadding(dp(6), dp(8), 0, dp(4))
            })
            for ((pod, ep) in dls) {
                row(ep.art.ifEmpty { pod.art }, ep.title, pod.title + " · " + Podcast.sizeMb(a, ep), onClick = {
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
            setTextColor(Color.WHITE)
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
        msg("Tipp: Statt eines Namens kannst du auch die RSS-Adresse eines Podcasts einfügen.")
        et.requestFocus()
    }

    private fun search(q: String) {
        lastQuery = q
        screen = "results"
        title.text = "„$q“"
        list.removeAllViews()
        msg("Suche …")
        h.io {
            val res = try {
                if (q.startsWith("http")) listOfNotNull(Podcast.feed(a, q, true)?.first) else Podcast.search(q)
            } catch (e: Exception) {
                h.ui { list.removeAllViews(); msg("Suche nicht möglich: ${e.message}") }
                return@io
            }
            h.ui {
                if (screen != "results") return@ui
                list.removeAllViews()
                if (res.isEmpty()) msg("Nichts gefunden.")
                for (p in res) {
                    val subbed = Podcast.isSub(h.prefs, p.feed)
                    val rw = row(p.art, p.title, p.author, onClick = { showEpisodes(p) })
                    rw.addView(TextView(a).apply {
                        text = if (subbed) "✓" else "＋ Abo"
                        textSize = 14f
                        setTextColor(if (subbed) h.accent() else Color.WHITE)
                        setPadding(dp(10), dp(8), dp(4), dp(8))
                        setOnClickListener {
                            val now = Podcast.toggleSub(h.prefs, p)
                            text = if (now) "✓" else "＋ Abo"
                            setTextColor(if (now) h.accent() else Color.WHITE)
                            Toast.makeText(a, if (now) "Abonniert" else "Abo entfernt", Toast.LENGTH_SHORT).show()
                        }
                    })
                }
            }
        }
    }

    fun showEpisodes(pod: Podcast.Pod) {
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
                else -> { tv.text = "⬇"; tv.setTextColor(Color.WHITE) }
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
            setTextColor(if (subbed) h.accent() else Color.BLACK)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(if (subbed) col("#2A3138") else h.accent())
            }
            setOnClickListener { Podcast.toggleSub(h.prefs, pod); renderEpisodes(p0, eps) }
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
            val rw = row("", e.title, info.toString(), dim = done && !playing, onClick = {
                h.play(pod, e)
                renderEpisodes(p0, eps)
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
                        renderEpisodes(p0, eps)
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
        if (eps.isEmpty()) msg("Keine Folgen gefunden.")
        else msg("⬇ = herunterladen (zum Hören ohne Internet) · lange drücken = gehört / Download löschen")
        scroll.post { scroll.scrollTo(0, y) }
    }

    // ------------------------------------------------------------ Player-Leiste

    fun refresh() {
        if (root == null) return
        if (Podcast.progress.isNotEmpty()) updateStatus()
        val c = h.current()
        bar.visibility = if (c == null) View.GONE else View.VISIBLE
        if (c == null) return
        if (barTitle.text != c.second.title) {
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
