package de.datacreate.radiouhr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Drehregler für die Lautstärke: 270° Drehbereich, Wert 0..1. */
class KnobView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var dragging = false
        private set
    var onChange: ((Float) -> Unit)? = null
    var accent = Color.parseColor("#FF8A3D")
        set(v) { field = v; invalidate() }

    var bodyColor = 0xFF1C1C1C.toInt()
        set(v) { field = v; invalidate() }
    var trackColor = 0x33FFFFFF
        set(v) { field = v; invalidate() }
    var dotColor = Color.WHITE
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val track = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5 * d; strokeCap = Paint.Cap.ROUND; color = 0x33FFFFFF
    }
    private val arc = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 5 * d; strokeCap = Paint.Cap.ROUND
    }
    private val body = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF1C1C1C.toInt() }
    private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE; strokeWidth = 1 * d; color = 0x55FFFFFF
    }
    private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE }
    private val rect = RectF()

    private val startAngle = 135f
    private val sweep = 270f

    override fun onDraw(canvas: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 5 * d
        rect.set(cx - r, cy - r, cx + r, cy + r)
        track.color = trackColor
        body.color = bodyColor
        ring.color = trackColor
        dot.color = dotColor
        canvas.drawArc(rect, startAngle, sweep, false, track)
        arc.color = accent
        canvas.drawArc(rect, startAngle, sweep * value, false, arc)

        val inner = r - 8 * d
        canvas.drawCircle(cx, cy, inner, body)
        canvas.drawCircle(cx, cy, inner, ring)

        val a = Math.toRadians((startAngle + sweep * value).toDouble())
        val pr = inner - 7 * d
        canvas.drawCircle(cx + (pr * cos(a)).toFloat(), cy + (pr * sin(a)).toFloat(), 3.5f * d, dot)
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
            }
        }
        return true
    }

    private fun update(e: MotionEvent) {
        var deg = Math.toDegrees(atan2((e.y - height / 2f).toDouble(), (e.x - width / 2f).toDouble())).toFloat()
        if (deg < 0) deg += 360f
        var rel = deg - startAngle
        if (rel < 0) rel += 360f
        // toter Bereich unten: zum näheren Ende springen
        if (rel > sweep) rel = if (rel - sweep < (360f - sweep) / 2) sweep else 0f
        val nv = rel / sweep
        if (nv != value) {
            value = nv
            onChange?.invoke(nv)
        }
    }
}
