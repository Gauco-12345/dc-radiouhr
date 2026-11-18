package de.datacreate.radiouhr

import android.graphics.Bitmap
import android.graphics.drawable.GradientDrawable
import android.text.TextUtils
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import java.util.Locale

/** Was im Radio gerade läuft (für die gemeinsame Player-Leiste). */
class RadioNow(val title: String, val station: String, val art: Bitmap?)

/**
 * Gemeinsame Player-Leiste für Podcast, Mediathek und Radio-Seite.
 * side = true: Querformat-Spalte (großes Bild oben), sonst flache Leiste unten.
 */
class PlayerBar(private val h: PodcastHost, private val side: Boolean) {
    private val a = h.activity
    private val d = a.resources.displayMetrics.density
    private fun dp(v: Int) = (v * d).toInt()
    private val L = Look(h.skin())

    val view: LinearLayout
    private val barArt: ImageView
    private val barText: LinearLayout
    private val barTitle: TextView
    private val barSub: TextView
    private val seek: SeekBar
    private val timesRow: LinearLayout
    private lateinit var tPos: TextView
    private val tLeft: TextView
    private val ctrRow: LinearLayout
    private val bBack: TextView
    private val bPlay: TextView
    private val bFwd: TextView
    private val bSpeed: TextView
    private var dragging = false
    private var key = ""
    private var radio = false

    init {
        view = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(L.bar)
            setPadding(dp(14), dp(10), dp(14), dp(20))
            isClickable = true
        }
        val top = LinearLayout(a).apply {
            orientation = if (side) LinearLayout.VERTICAL else LinearLayout.HORIZONTAL
            gravity = if (side) Gravity.CENTER_HORIZONTAL else Gravity.CENTER_VERTICAL
        }
        barArt = ImageView(a).apply { scaleType = ImageView.ScaleType.CENTER_CROP; clipToOutline = true }
        if (side) top.addView(barArt, LinearLayout.LayoutParams(dp(150), dp(150)).apply { bottomMargin = dp(12) })
        else top.addView(barArt, LinearLayout.LayoutParams(dp(48), dp(48)).apply { rightMargin = dp(10) })
        barText = LinearLayout(a).apply { orientation = LinearLayout.VERTICAL }
        barTitle = TextView(a).apply { textSize = 15f; setTextColor(L.fg); maxLines = 2; ellipsize = TextUtils.TruncateAt.END }
        barSub = TextView(a).apply { textSize = 12f; setTextColor(L.sub); setSingleLine(true); ellipsize = TextUtils.TruncateAt.END }
        barText.addView(barTitle)
        barText.addView(barSub)
        if (side) {
            barTitle.gravity = Gravity.CENTER
            barSub.gravity = Gravity.CENTER
            barTitle.maxLines = 3
            top.addView(barText, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        } else top.addView(barText, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        view.addView(top)
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
        view.addView(seek, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(36)).apply { topMargin = dp(6) })
        timesRow = LinearLayout(a).apply { orientation = LinearLayout.HORIZONTAL }
        tPos = TextView(a).apply { textSize = 12f; setTextColor(L.sub) }
        tLeft = TextView(a).apply { textSize = 12f; setTextColor(L.sub); gravity = Gravity.END }
        timesRow.addView(tPos, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        timesRow.addView(tLeft, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        view.addView(timesRow)
        ctrRow = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
            setPadding(0, dp(6), 0, 0)
        }
        bBack = round("−15") { h.seekBy(-15_000) }
        bPlay = round("▶", big = true) {
            if (radio) h.radioStop() else h.toggle()
            refresh()
        }
        bFwd = round("+30") { h.seekBy(30_000) }
        bSpeed = round("1,0×") {
            val steps = floatArrayOf(0.8f, 1.0f, 1.2f, 1.5f, 1.75f, 2.0f)
            val cur = h.speed()
            h.setSpeed(steps.firstOrNull { it > cur + 0.01f } ?: steps[0])
            refresh()
        }
        for (b in listOf(bBack, bPlay, bFwd, bSpeed)) ctrRow.addView(b)
        view.addView(ctrRow)
        view.addOnLayoutChangeListener { _, l, t, rr, b, ol, ot, orr, ob ->
            if (rr - l != orr - ol || b - t != ob - ot) view.post { fit() }
        }
    }

    private fun round(t: String, big: Boolean = false, onClick: () -> Unit) = TextView(a).apply {
        text = t
        textSize = if (big) 24f else 15f
        setTextColor(if (big) L.onAccent else L.fg)
        gravity = Gravity.CENTER
        background = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(if (big) L.accent else L.chip)
        }
        val s = if (big) dp(64) else dp(52)
        layoutParams = LinearLayout.LayoutParams(s, s).apply { setMargins(dp(6), 0, dp(6), 0) }
        setOnClickListener { onClick() }
    }

    private fun setArt(url: String, bmp: Bitmap?) {
        barArt.background = GradientDrawable().apply { cornerRadius = dp(8).toFloat(); setColor(L.chip) }
        barArt.setImageDrawable(null)
        if (bmp != null) { barArt.setImageBitmap(bmp); return }
        if (url.isEmpty()) return
        barArt.tag = url
        h.io {
            val b = if (url.startsWith("content:")) h.localBitmap(android.net.Uri.parse(url), 300) else h.bitmap(url)
            h.ui { if (b != null && barArt.tag == url) barArt.setImageBitmap(b) }
        }
    }

    /** Tasten und Bild so verkleinern, dass nichts abgeschnitten wird. */
    private fun fit() {
        if (view.width == 0) return
        val w = view.width - view.paddingLeft - view.paddingRight
        val m = dp(6)
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
        if (!side) return
        val inner = view.height - view.paddingTop - view.paddingBottom
        val need = barText.height + (if (radio) 0 else dp(42) + timesRow.height) + big + dp(6) + dp(12)
        val s = (inner - need).coerceAtMost(dp(150))
        val want = if (s < dp(44)) 0 else s
        val lp = barArt.layoutParams as LinearLayout.LayoutParams
        if (lp.width != want) {
            lp.width = want; lp.height = want
            barArt.visibility = if (want == 0) View.GONE else View.VISIBLE
            barArt.layoutParams = lp
        }
    }

    /** Aktualisieren; liefert false, wenn nichts läuft (Leiste ausgeblendet). */
    fun refresh(): Boolean {
        val r = h.radioNow()
        val c = if (r == null) h.current() else null
        if (r == null && c == null) {
            view.visibility = View.GONE
            key = ""
            return false
        }
        view.visibility = View.VISIBLE
        val wasRadio = radio
        radio = r != null
        if (radio != wasRadio) {
            val v = if (radio) View.GONE else View.VISIBLE
            for (x in listOf<View>(seek, timesRow, bBack, bFwd, bSpeed)) x.visibility = v
            view.post { fit() }
        }
        if (r != null) {
            val k = "radio|" + r.station + "|" + r.title
            if (k != key) {
                key = k
                barTitle.text = r.title
                barSub.text = if (r.title == r.station) "Radio" else r.station
                setArt("", r.art)
            }
            bPlay.text = "■"
            return true
        }
        val (pod, ep) = c!!
        if (key != ep.url) {
            key = ep.url
            barTitle.text = ep.title
            barSub.text = pod.title
            setArt(ep.art.ifEmpty { pod.art }, null)
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
        return true
    }
}
