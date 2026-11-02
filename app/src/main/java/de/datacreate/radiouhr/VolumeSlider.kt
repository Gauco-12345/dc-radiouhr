package de.datacreate.radiouhr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View

/** Senkrechter Lautstärke-Schieber am Bildschirmrand: hoch = lauter, runter = leiser. */
class VolumeSlider @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var dragging = false
        private set
    var onChange: ((Float) -> Unit)? = null

    var fillColor = col("#FF8A3D")
        set(v) { field = v; invalidate() }
    var trackColor = col("#33FFFFFF")
        set(v) { field = v; invalidate() }
    var iconColor = Color.WHITE
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    private fun barTop() = 6 * d
    private fun barBottom() = height - 30 * d

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val bw = (w * 0.42f).coerceAtLeast(6 * d) * (if (dragging) 1.35f else 1f)
        val cx = w / 2f
        val top = barTop()
        val bottom = barBottom()
        if (bottom <= top) return
        r.set(cx - bw / 2, top, cx + bw / 2, bottom)
        p.style = Paint.Style.FILL
        p.color = trackColor
        c.drawRoundRect(r, bw / 2, bw / 2, p)
        val fy = bottom - (bottom - top) * value
        if (value > 0.005f) {
            p.color = fillColor
            c.drawRoundRect(RectF(r.left, fy, r.right, bottom), bw / 2, bw / 2, p)
        }
        // Griff
        p.color = iconColor
        c.drawCircle(cx, fy.coerceIn(top + bw / 2, bottom - bw / 2), bw * 0.85f, p)
        // Prozent beim Schieben
        if (dragging) {
            p.textAlign = Paint.Align.CENTER
            p.textSize = 11 * d
            p.color = iconColor
            c.drawText("${Math.round(value * 100)}", cx, (fy - bw * 1.4f).coerceAtLeast(top + 10 * d), p)
        }
        // Lautsprecher-Symbol unten
        val s = 7 * d
        val sy = height - 14 * d
        val path = Path()
        path.moveTo(cx - s, sy - s * 0.4f)
        path.lineTo(cx - s * 0.45f, sy - s * 0.4f)
        path.lineTo(cx + s * 0.2f, sy - s)
        path.lineTo(cx + s * 0.2f, sy + s)
        path.lineTo(cx - s * 0.45f, sy + s * 0.4f)
        path.lineTo(cx - s, sy + s * 0.4f)
        path.close()
        p.color = iconColor
        c.drawPath(path, p)
        if (value > 0.01f) {
            p.style = Paint.Style.STROKE
            p.strokeWidth = 1.5f * d
            c.drawArc(RectF(cx - s * 0.3f, sy - s * 0.6f, cx + s * 0.9f, sy + s * 0.6f), -50f, 100f, false, p)
            p.style = Paint.Style.FILL
        }
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragging = true
                parent?.requestDisallowInterceptTouchEvent(true)
                update(e)
            }
            MotionEvent.ACTION_MOVE -> update(e)
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                update(e)
                dragging = false
                invalidate()
            }
        }
        return true
    }

    private fun update(e: MotionEvent) {
        val nv = ((barBottom() - e.y) / (barBottom() - barTop())).coerceIn(0f, 1f)
        if (nv != value) {
            value = nv
            onChange?.invoke(nv)
        } else invalidate()
    }
}
