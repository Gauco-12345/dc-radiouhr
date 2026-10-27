package de.datacreate.radiouhr

import android.animation.ValueAnimator
import android.content.Context
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Senderskala wie beim Röhrenradio: Frequenzband mit Sendernamen und rotem Zeiger. */
class DialView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    private var names: List<String> = emptyList()
    private var target = 0
    private var pos = 0f
    private var lit = false
    private var anim: ValueAnimator? = null
    var onPick: ((Int) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setStations(list: List<String>, index: Int, playing: Boolean) {
        val first = names.isEmpty()
        names = list
        lit = playing
        if (index != target || first) {
            target = index
            anim?.cancel()
            if (first) {
                pos = index.toFloat()
            } else {
                anim = ValueAnimator.ofFloat(pos, index.toFloat()).apply {
                    duration = 700
                    addUpdateListener {
                        pos = it.animatedValue as Float
                        invalidate()
                    }
                    start()
                }
            }
        }
        invalidate()
    }

    private fun scaleLeft() = 18 * d
    private fun scaleRight() = width - 18 * d
    private fun xFor(i: Float): Float {
        val n = maxOf(1, names.size)
        return scaleLeft() + (scaleRight() - scaleLeft()) * (i + 0.5f) / n
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0f) return
        val r = RectF(2 * d, 2 * d, w - 2 * d, h - 2 * d)
        val rad = 8 * d
        // Glas mit warmem Licht
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, col("#F4E4B8"), col("#D6BC84"), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        if (lit) {
            p.shader = RadialGradient(w / 2f, h * 0.6f, w * 0.6f, col("#66FFB050"), col("#00FFB050"), Shader.TileMode.CLAMP)
            c.drawRoundRect(r, rad, rad, p)
        }
        p.shader = null

        // Frequenzband
        val lineY = h * 0.42f
        p.color = col("#3B2A1A")
        p.strokeWidth = 1.2f * d
        c.drawLine(scaleLeft(), lineY, scaleRight(), lineY, p)
        tp.typeface = Typeface.create("serif", Typeface.BOLD)
        tp.textSize = 9 * d
        tp.color = col("#3B2A1A")
        val steps = 40
        for (i in 0..steps) {
            val x = scaleLeft() + (scaleRight() - scaleLeft()) * i / steps
            val big = i % 8 == 0
            val mid = i % 4 == 0
            val len = if (big) 9 * d else if (mid) 6 * d else 3.5f * d
            c.drawLine(x, lineY - len, x, lineY, p)
            if (big) c.drawText((88 + i / 2).toString(), x, lineY - 11 * d, tp)
        }
        tp.textSize = 7.5f * d
        tp.textAlign = Paint.Align.LEFT
        c.drawText("UKW  MHz", r.left + 6 * d, r.top + 10 * d, tp)
        tp.textAlign = Paint.Align.CENTER

        // Sendernamen
        val n = names.size
        if (n > 0) {
            tp.typeface = Typeface.create("serif", Typeface.NORMAL)
            tp.textSize = (if (n > 6) 9.5f else 11f) * d
            val slot = (scaleRight() - scaleLeft()) / n
            names.forEachIndexed { i, name ->
                val x = xFor(i.toFloat())
                val row = if (n > 6 && i % 2 == 1) 1 else 0
                val y = lineY + (16 + row * 13) * d
                tp.color = if (i == target) col("#7A1010") else col("#4A3522")
                val avail = if (n > 6) slot * 1.9f else slot * 0.95f
                val txt = TextUtils.ellipsize(name, tp, avail, TextUtils.TruncateAt.END).toString()
                c.drawText(txt, x, y, tp)
            }
            // Zeiger
            val nx = xFor(pos)
            p.color = col("#55C8102E")
            p.strokeWidth = 5 * d
            c.drawLine(nx, r.top + 5 * d, nx, r.bottom - 5 * d, p)
            p.color = col("#C8102E")
            p.strokeWidth = 2 * d
            c.drawLine(nx, r.top + 5 * d, nx, r.bottom - 5 * d, p)
        }

        // Messingrahmen
        p.style = Paint.Style.STROKE
        p.strokeWidth = 3 * d
        p.color = col("#C8963E")
        c.drawRoundRect(r, rad, rad, p)
        p.strokeWidth = 1 * d
        p.color = col("#66000000")
        c.drawRoundRect(RectF(r.left + 3 * d, r.top + 3 * d, r.right - 3 * d, r.bottom - 3 * d), rad * 0.7f, rad * 0.7f, p)
        p.style = Paint.Style.FILL
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_UP && names.isNotEmpty()) {
            val rel = (e.x - scaleLeft()) / (scaleRight() - scaleLeft())
            val i = (rel * names.size).toInt().coerceIn(0, names.size - 1)
            onPick?.invoke(i)
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean {
        super.performClick()
        return true
    }
}
