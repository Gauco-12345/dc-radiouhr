package de.datacreate.radiouhr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Drehregler für die Lautstärke: 270° Drehbereich, Wert 0..1, Optik je Design. */
class KnobView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var value = 0.5f
        set(v) {
            field = v.coerceIn(0f, 1f)
            invalidate()
        }
    var dragging = false
        private set
    var onChange: ((Float) -> Unit)? = null

    var style = KnobStyle.MODERN
        set(v) {
            field = v
            setLayerType(if (v == KnobStyle.NEON || v == KnobStyle.LED) LAYER_TYPE_SOFTWARE else LAYER_TYPE_HARDWARE, null)
            invalidate()
        }
    var accent = col("#FF8A3D")
        set(v) { field = v; invalidate() }
    var bodyColor = col("#1C1C1C")
        set(v) { field = v; invalidate() }
    var trackColor = col("#33FFFFFF")
        set(v) { field = v; invalidate() }
    var dotColor = Color.WHITE
        set(v) { field = v; invalidate() }

    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val rect = RectF()
    private val startAngle = 135f
    private val sweep = 270f

    private fun alpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))
    private fun ang() = Math.toRadians((startAngle + sweep * value).toDouble())

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4 * d
        if (r <= 0) return
        p.shader = null
        p.clearShadowLayer()
        when (style) {
            KnobStyle.MODERN -> modern(c, cx, cy, r)
            KnobStyle.BAKELITE -> bakelite(c, cx, cy, r)
            KnobStyle.LED -> led(c, cx, cy, r)
            KnobStyle.NEON -> neon(c, cx, cy, r)
            KnobStyle.THIN -> thin(c, cx, cy, r)
            KnobStyle.BRAUN -> braun(c, cx, cy, r)
            KnobStyle.WOOD -> wood(c, cx, cy, r)
            KnobStyle.GLASS -> glass(c, cx, cy, r)
        }
    }

    private fun arcTrack(c: Canvas, cx: Float, cy: Float, r: Float, w: Float, track: Int, fill: Int) {
        rect.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = track
        c.drawArc(rect, startAngle, sweep, false, p)
        p.color = fill
        if (value > 0.001f) c.drawArc(rect, startAngle, sweep * value, false, p)
        p.style = Paint.Style.FILL
    }

    private fun pointerDot(c: Canvas, cx: Float, cy: Float, dist: Float, size: Float, color: Int) {
        val a = ang()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawCircle(cx + (dist * cos(a)).toFloat(), cy + (dist * sin(a)).toFloat(), size, p)
    }

    private fun pointerLine(c: Canvas, cx: Float, cy: Float, from: Float, to: Float, w: Float, color: Int) {
        val a = ang()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = w
        p.color = color
        c.drawLine(
            cx + (from * cos(a)).toFloat(), cy + (from * sin(a)).toFloat(),
            cx + (to * cos(a)).toFloat(), cy + (to * sin(a)).toFloat(), p
        )
        p.style = Paint.Style.FILL
    }

    private fun ticks(c: Canvas, cx: Float, cy: Float, r1: Float, r2: Float, n: Int, w: Float, color: Int) {
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = color
        for (i in 0..n) {
            val a = Math.toRadians((startAngle + sweep * i / n).toDouble())
            c.drawLine(
                cx + (r1 * cos(a)).toFloat(), cy + (r1 * sin(a)).toFloat(),
                cx + (r2 * cos(a)).toFloat(), cy + (r2 * sin(a)).toFloat(), p
            )
        }
        p.style = Paint.Style.FILL
    }

    private fun modern(c: Canvas, cx: Float, cy: Float, r: Float) {
        arcTrack(c, cx, cy, r, 5 * d, trackColor, accent)
        val inner = r - 8 * d
        p.color = bodyColor
        c.drawCircle(cx, cy, inner, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1 * d
        p.color = trackColor
        c.drawCircle(cx, cy, inner, p)
        p.style = Paint.Style.FILL
        pointerDot(c, cx, cy, inner - 7 * d, 3.5f * d, dotColor)
    }

    private fun bakelite(c: Canvas, cx: Float, cy: Float, r: Float) {
        // Skala 0–10
        ticks(c, cx, cy, r * 0.9f, r, 10, 1.3f * d, col("#E8D2A8"))
        val kr = r * 0.8f
        p.color = col("#88000000")
        c.drawCircle(cx, cy + 2.5f * d, kr, p)
        // Riffelung
        p.shader = RadialGradient(cx - kr * 0.3f, cy - kr * 0.4f, kr * 1.5f, col("#6A4428"), col("#1A0E06"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, kr, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        for (i in 0 until 40) {
            val a = Math.toRadians(i * 9.0)
            p.color = if (i % 2 == 0) col("#55000000") else col("#22FFD9A8")
            c.drawLine(
                cx + (kr * 0.86f * cos(a)).toFloat(), cy + (kr * 0.86f * sin(a)).toFloat(),
                cx + (kr * cos(a)).toFloat(), cy + (kr * sin(a)).toFloat(), p
            )
        }
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx - kr * 0.25f, cy - kr * 0.3f, kr, col("#5A3A22"), col("#24140A"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, kr * 0.82f, p)
        p.shader = null
        pointerLine(c, cx, cy, kr * 0.25f, kr * 0.75f, 3 * d, col("#F3E3C3"))
    }

    private fun led(c: Canvas, cx: Float, cy: Float, r: Float) {
        val n = 15
        val lit = Math.round(value * n)
        for (i in 0 until n) {
            val a = Math.toRadians((startAngle + sweep * (i + 0.5f) / n).toDouble())
            val x = cx + (r * 0.84f * cos(a)).toFloat()
            val y = cy + (r * 0.84f * sin(a)).toFloat()
            val on = i < lit
            p.color = if (on) accent else alpha(accent, 40)
            if (on) p.setShadowLayer(4 * d, 0f, 0f, accent) else p.clearShadowLayer()
            c.drawRect(x - 2.6f * d, y - 2.6f * d, x + 2.6f * d, y + 2.6f * d, p)
        }
        p.clearShadowLayer()
        val inner = r * 0.6f
        p.shader = LinearGradient(0f, cy - inner, 0f, cy + inner, col("#3A3F46"), col("#16181B"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, inner, p)
        p.shader = null
        p.color = accent
        p.textAlign = Paint.Align.CENTER
        p.typeface = Typeface.MONOSPACE
        p.textSize = inner * 0.55f
        c.drawText(Math.round(value * 99).toString(), cx, cy - (p.descent() + p.ascent()) / 2f, p)
    }

    private fun neon(c: Canvas, cx: Float, cy: Float, r: Float) {
        rect.set(cx - r * 0.85f, cy - r * 0.85f, cx + r * 0.85f, cy + r * 0.85f)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = 3 * d
        p.color = alpha(accent, 50)
        c.drawArc(rect, startAngle, sweep, false, p)
        p.setShadowLayer(8 * d, 0f, 0f, accent)
        p.color = accent
        p.strokeWidth = 4 * d
        if (value > 0.001f) c.drawArc(rect, startAngle, sweep * value, false, p)
        p.strokeWidth = 1.5f * d
        p.color = col("#39F3FF")
        p.setShadowLayer(6 * d, 0f, 0f, col("#39F3FF"))
        c.drawCircle(cx, cy, r * 0.55f, p)
        p.clearShadowLayer()
        p.style = Paint.Style.FILL
        p.setShadowLayer(6 * d, 0f, 0f, Color.WHITE)
        pointerDot(c, cx, cy, r * 0.55f, 3.5f * d, Color.WHITE)
        p.clearShadowLayer()
    }

    private fun thin(c: Canvas, cx: Float, cy: Float, r: Float) {
        arcTrack(c, cx, cy, r * 0.85f, 1.5f * d, trackColor, dotColor)
        pointerDot(c, cx, cy, r * 0.85f, 4 * d, dotColor)
    }

    private fun braun(c: Canvas, cx: Float, cy: Float, r: Float) {
        ticks(c, cx, cy, r * 0.88f, r, 10, 1 * d, col("#888888"))
        val kr = r * 0.78f
        p.color = col("#30000000")
        c.drawCircle(cx, cy + 2.5f * d, kr, p)
        p.shader = LinearGradient(0f, cy - kr, 0f, cy + kr, col("#FFFFFF"), col("#DCD8CF"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, kr, p)
        p.shader = LinearGradient(0f, cy - kr, 0f, cy + kr, col("#E4E0D8"), col("#FFFFFF"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, kr * 0.8f, p)
        p.shader = null
        pointerDot(c, cx, cy, kr * 0.6f, 3.5f * d, accent)
    }

    private fun wood(c: Canvas, cx: Float, cy: Float, r: Float) {
        ticks(c, cx, cy, r * 0.9f, r, 10, 1.3f * d, col("#CDB68F"))
        val kr = r * 0.8f
        p.color = col("#88000000")
        c.drawCircle(cx, cy + 2.5f * d, kr, p)
        p.shader = RadialGradient(cx - kr * 0.3f, cy - kr * 0.3f, kr * 1.4f, col("#A06A3C"), col("#4A2A12"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, kr, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 0.8f * d
        for (i in 1..5) {
            p.color = col("#33000000")
            c.drawCircle(cx + kr * 0.08f, cy + kr * 0.05f, kr * i / 6f, p)
        }
        p.strokeWidth = 1.5f * d
        p.color = col("#2A170A")
        c.drawCircle(cx, cy, kr, p)
        p.style = Paint.Style.FILL
        pointerLine(c, cx, cy, kr * 0.3f, kr * 0.8f, 3 * d, col("#C8963E"))
    }

    private fun glass(c: Canvas, cx: Float, cy: Float, r: Float) {
        arcTrack(c, cx, cy, r, 4 * d, col("#22FFFFFF"), accent)
        val inner = r - 8 * d
        p.color = col("#1EFFFFFF")
        c.drawCircle(cx, cy, inner, p)
        p.shader = LinearGradient(0f, cy - inner, 0f, cy, col("#40FFFFFF"), col("#00FFFFFF"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, inner, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1 * d
        p.color = col("#55FFFFFF")
        c.drawCircle(cx, cy, inner, p)
        p.style = Paint.Style.FILL
        pointerDot(c, cx, cy, inner - 7 * d, 3.5f * d, Color.WHITE)
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
