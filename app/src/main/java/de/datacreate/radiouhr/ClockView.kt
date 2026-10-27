package de.datacreate.radiouhr

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.util.AttributeSet
import android.view.View
import java.util.Calendar
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/** Gezeichnete Uhren: Nixie-Röhren, 7-Segment, Klappziffern, Zeigeruhren. */
class ClockView @JvmOverloads constructor(ctx: Context, attrs: AttributeSet? = null) : View(ctx, attrs) {

    var style = ClockStyle.NIXIE
        set(v) {
            field = v
            setLayerType(if (v == ClockStyle.NIXIE || v == ClockStyle.SEGMENT) LAYER_TYPE_SOFTWARE else LAYER_TYPE_HARDWARE, null)
            invalidate()
        }
    var litColor = col("#8CFF5A")
        set(v) { field = v; invalidate() }

    private var hh = 0
    private var mm = 0
    private var ss = 0
    private val d = resources.displayMetrics.density
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val tp = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }

    fun setTime(cal: Calendar) {
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val s = cal.get(Calendar.SECOND)
        if (h != hh || m != mm || s != ss) {
            hh = h; mm = m; ss = s
            invalidate()
        }
    }

    private fun digits() = intArrayOf(hh / 10, hh % 10, mm / 10, mm % 10)

    override fun onDraw(canvas: Canvas) {
        if (width <= 0 || height <= 0) return
        when (style) {
            ClockStyle.TEXT -> {}
            ClockStyle.NIXIE -> nixie(canvas)
            ClockStyle.SEGMENT -> segment(canvas)
            ClockStyle.FLIP -> flip(canvas)
            ClockStyle.ANALOG_BRAUN -> braun(canvas)
            ClockStyle.ANALOG_WOOD -> woodClock(canvas)
        }
    }

    private fun alpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

    // ------------------------------------------------------------ Nixie

    private fun nixie(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        var cw = min(w / 4.9f, h * 0.6f)
        val ch = cw / 0.6f
        if (ch > h) cw = h * 0.6f
        val tubeH = cw / 0.6f
        val colonW = cw * 0.5f
        val gap = cw * 0.08f
        val total = cw * 4 + colonW + gap * 4
        var x = (w - total) / 2f
        val top = (h - tubeH) / 2f
        val dg = digits()
        val orange = col("#FF9A3C")
        for (i in 0 until 4) {
            drawTube(c, RectF(x, top, x + cw, top + tubeH), dg[i], orange)
            x += cw + gap
            if (i == 1) {
                val cx = x + colonW / 2f - gap / 2f
                val on = ss % 2 == 0
                p.style = Paint.Style.FILL
                p.color = if (on) orange else alpha(orange, 50)
                if (on) p.setShadowLayer(cw * 0.12f, 0f, 0f, col("#FF6A00"))
                c.drawCircle(cx, top + tubeH * 0.38f, cw * 0.06f, p)
                c.drawCircle(cx, top + tubeH * 0.66f, cw * 0.06f, p)
                p.clearShadowLayer()
                x += colonW
            }
        }
    }

    private fun drawTube(c: Canvas, r: RectF, digit: Int, orange: Int) {
        val rad = r.width() * 0.32f
        // Sockel
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = col("#111111")
        c.drawRoundRect(RectF(r.left + r.width() * 0.08f, r.bottom - r.height() * 0.1f, r.right - r.width() * 0.08f, r.bottom), 4 * d, 4 * d, p)
        val glass = RectF(r.left, r.top, r.right, r.bottom - r.height() * 0.07f)
        p.shader = LinearGradient(glass.left, 0f, glass.right, 0f,
            intArrayOf(col("#2A1E16"), col("#120C08"), col("#1E150F")), null, Shader.TileMode.CLAMP)
        c.drawRoundRect(glass, rad, rad, p)
        p.shader = null
        // Gitter
        c.save()
        c.clipRect(glass)
        p.strokeWidth = 0.7f * d
        p.color = col("#22D08040")
        var gx = glass.left
        val step = glass.width() / 7f
        while (gx < glass.right) {
            c.drawLine(gx, glass.top, gx + step * 0.5f, glass.bottom, p)
            gx += step
        }
        c.restore()
        // Ziffern-Stapel (unbeleuchtet)
        tp.typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
        tp.textSize = glass.height() * 0.72f
        tp.clearShadowLayer()
        tp.color = alpha(orange, 22)
        val by = glass.centerY() - (tp.descent() + tp.ascent()) / 2f
        for (n in 0..9) if (n != digit) c.drawText(n.toString(), glass.centerX(), by, tp)
        // leuchtende Ziffer
        tp.color = orange
        tp.setShadowLayer(glass.width() * 0.22f, 0f, 0f, col("#FF5A00"))
        c.drawText(digit.toString(), glass.centerX(), by, tp)
        tp.clearShadowLayer()
        tp.color = col("#FFD9B0")
        tp.textSize = glass.height() * 0.72f
        tp.strokeWidth = 0f
        c.drawText(digit.toString(), glass.centerX(), by, tp)
        // Glanz
        p.color = col("#18FFFFFF")
        c.drawRoundRect(RectF(glass.left + glass.width() * 0.12f, glass.top + rad * 0.4f, glass.left + glass.width() * 0.24f, glass.bottom - rad * 0.6f), 6 * d, 6 * d, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1 * d
        p.color = col("#44FFFFFF")
        c.drawRoundRect(glass, rad, rad, p)
        p.style = Paint.Style.FILL
    }

    // ------------------------------------------------------------ 7-Segment

    private val masks = intArrayOf(0x3F, 0x06, 0x5B, 0x4F, 0x66, 0x6D, 0x7D, 0x07, 0x7F, 0x6F)

    private fun segment(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        var cw = min(w / 5.2f, h * 0.55f)
        var ch = cw / 0.55f
        if (ch > h * 0.92f) {
            ch = h * 0.92f
            cw = ch * 0.55f
        }
        val gap = cw * 0.22f
        val colonW = cw * 0.5f
        val total = cw * 4 + gap * 3 + colonW
        var x = (w - total) / 2f
        val top = (h - ch) / 2f
        val dg = digits()
        for (i in 0 until 4) {
            val hide = i == 0 && dg[0] == 0
            drawDigit7(c, x, top, cw, ch, if (hide) -1 else dg[i])
            x += cw + gap
            if (i == 1) {
                val on = ss % 2 == 0
                p.color = if (on) litColor else alpha(litColor, 28)
                if (on) p.setShadowLayer(cw * 0.12f, 0f, 0f, litColor)
                val t = cw * 0.15f
                val cx = x - gap / 2f + colonW / 2f - gap * 0.3f
                c.drawRect(cx - t / 2, top + ch * 0.3f - t / 2, cx + t / 2, top + ch * 0.3f + t / 2, p)
                c.drawRect(cx - t / 2, top + ch * 0.7f - t / 2, cx + t / 2, top + ch * 0.7f + t / 2, p)
                p.clearShadowLayer()
                x += colonW - gap * 0.4f
            }
        }
    }

    private fun drawDigit7(c: Canvas, x: Float, y: Float, cw: Float, ch: Float, digit: Int) {
        val mask = if (digit < 0) 0 else masks[digit]
        val t = cw * 0.17f
        val g = t * 0.18f
        val l = x + t / 2
        val r = x + cw - t / 2
        val tp = y + t / 2
        val md = y + ch / 2
        val bt = y + ch - t / 2
        c.save()
        c.translate(0.08f * (y + ch / 2f), 0f)
        c.skew(-0.08f, 0f)
        // a b c d e f g
        val segs = arrayOf(
            floatArrayOf(l + g, tp, r - g, tp),
            floatArrayOf(r, tp + g, r, md - g),
            floatArrayOf(r, md + g, r, bt - g),
            floatArrayOf(l + g, bt, r - g, bt),
            floatArrayOf(l, md + g, l, bt - g),
            floatArrayOf(l, tp + g, l, md - g),
            floatArrayOf(l + g, md, r - g, md)
        )
        for (i in 0 until 7) {
            val on = (mask shr i) and 1 == 1
            p.style = Paint.Style.FILL
            p.color = if (on) litColor else alpha(litColor, 22)
            if (on) p.setShadowLayer(t * 0.6f, 0f, 0f, alpha(litColor, 180)) else p.clearShadowLayer()
            val s = segs[i]
            c.drawPath(segPath(s[0], s[1], s[2], s[3], t), p)
        }
        p.clearShadowLayer()
        c.restore()
    }

    private fun segPath(x1: Float, y1: Float, x2: Float, y2: Float, t: Float): Path {
        val path = Path()
        val h = t / 2f
        if (y1 == y2) {
            path.moveTo(x1, y1)
            path.lineTo(x1 + h, y1 - h)
            path.lineTo(x2 - h, y1 - h)
            path.lineTo(x2, y1)
            path.lineTo(x2 - h, y1 + h)
            path.lineTo(x1 + h, y1 + h)
        } else {
            path.moveTo(x1, y1)
            path.lineTo(x1 + h, y1 + h)
            path.lineTo(x1 + h, y2 - h)
            path.lineTo(x1, y2)
            path.lineTo(x1 - h, y2 - h)
            path.lineTo(x1 - h, y1 + h)
        }
        path.close()
        return path
    }

    // ------------------------------------------------------------ Klappziffern

    private fun flip(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val gap = w * 0.04f
        var cardW = (w - gap) / 2f
        var cardH = cardW * 0.78f
        if (cardH > h * 0.95f) {
            cardH = h * 0.95f
            cardW = cardH / 0.78f
        }
        val total = cardW * 2 + gap
        val left = (w - total) / 2f
        val top = (h - cardH) / 2f
        drawCard(c, RectF(left, top, left + cardW, top + cardH), String.format("%02d", hh))
        drawCard(c, RectF(left + cardW + gap, top, left + total, top + cardH), String.format("%02d", mm))
    }

    private fun drawCard(c: Canvas, r: RectF, txt: String) {
        val rad = r.width() * 0.07f
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = col("#88000000")
        c.drawRoundRect(RectF(r.left, r.top + 4 * d, r.right, r.bottom + 4 * d), rad, rad, p)
        val mid = r.centerY()
        c.save()
        c.clipRect(r.left, r.top, r.right, mid)
        p.shader = LinearGradient(0f, r.top, 0f, mid, col("#3A3A3A"), col("#262626"), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        c.restore()
        c.save()
        c.clipRect(r.left, mid, r.right, r.bottom)
        p.shader = LinearGradient(0f, mid, 0f, r.bottom, col("#202020"), col("#141414"), Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        c.restore()
        p.shader = null
        tp.typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
        tp.textSize = r.height() * 0.78f
        tp.color = col("#EDEDED")
        tp.clearShadowLayer()
        val by = mid - (tp.descent() + tp.ascent()) / 2f
        c.drawText(txt, r.centerX(), by, tp)
        p.color = col("#000000")
        c.drawRect(r.left, mid - 1.5f * d, r.right, mid + 1.5f * d, p)
        p.color = col("#555555")
        c.drawRect(r.left - 2 * d, mid - 5 * d, r.left + 3 * d, mid + 5 * d, p)
        c.drawRect(r.right - 3 * d, mid - 5 * d, r.right + 2 * d, mid + 5 * d, p)
    }

    // ------------------------------------------------------------ Zeigeruhren

    private fun hand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, tail: Float, width: Float, color: Int) {
        val a = Math.toRadians(deg.toDouble())
        val sx = sin(a).toFloat()
        val cy2 = -cos(a).toFloat()
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = width
        p.color = color
        c.drawLine(cx - sx * tail, cy - cy2 * tail, cx + sx * len, cy + cy2 * len, p)
        p.style = Paint.Style.FILL
    }

    private fun braun(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 6 * d
        p.style = Paint.Style.FILL
        p.color = col("#30000000")
        c.drawCircle(cx, cy + 4 * d, r, p)
        p.shader = RadialGradient(cx, cy - r * 0.3f, r * 1.4f, col("#FFFFFF"), col("#ECEAE4"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1 * d
        p.color = col("#22000000")
        c.drawCircle(cx, cy, r, p)
        for (i in 0 until 60) {
            val a = Math.toRadians(i * 6.0)
            val big = i % 5 == 0
            val l1 = r * (if (big) 0.80f else 0.88f)
            val l2 = r * 0.93f
            p.strokeWidth = if (big) r * 0.025f else r * 0.008f
            p.color = if (big) col("#222222") else col("#999999")
            c.drawLine(
                cx + sin(a).toFloat() * l1, cy - cos(a).toFloat() * l1,
                cx + sin(a).toFloat() * l2, cy - cos(a).toFloat() * l2, p
            )
        }
        p.style = Paint.Style.FILL
        tp.typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        tp.textSize = r * 0.13f
        tp.color = col("#222222")
        tp.clearShadowLayer()
        for (n in 1..12) {
            val a = Math.toRadians(n * 30.0)
            val tx = cx + sin(a).toFloat() * r * 0.65f
            val ty = cy - cos(a).toFloat() * r * 0.65f - (tp.descent() + tp.ascent()) / 2f
            c.drawText(n.toString(), tx, ty, tp)
        }
        hand(c, cx, cy, (hh % 12 + mm / 60f) * 30f, r * 0.5f, r * 0.08f, r * 0.06f, col("#1E1E1E"))
        hand(c, cx, cy, (mm + ss / 60f) * 6f, r * 0.76f, r * 0.08f, r * 0.042f, col("#1E1E1E"))
        hand(c, cx, cy, ss * 6f, r * 0.84f, r * 0.2f, r * 0.012f, col("#F5B000"))
        p.color = col("#F5B000")
        c.drawCircle(cx, cy, r * 0.04f, p)
    }

    private fun woodClock(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4 * d
        p.style = Paint.Style.FILL
        p.color = col("#66000000")
        c.drawCircle(cx, cy + 4 * d, r, p)
        p.shader = RadialGradient(cx - r * 0.2f, cy - r * 0.3f, r * 1.3f, col("#9A6136"), col("#3E220E"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        // geschnitzte Kerben
        p.color = col("#55000000")
        for (i in 0 until 24) {
            val a = Math.toRadians(i * 15.0 + 7.5)
            c.drawCircle(cx + sin(a).toFloat() * r * 0.89f, cy - cos(a).toFloat() * r * 0.89f, r * 0.045f, p)
        }
        p.color = col("#33FFD9A8")
        for (i in 0 until 24) {
            val a = Math.toRadians(i * 15.0 + 7.5)
            c.drawCircle(cx + sin(a).toFloat() * r * 0.89f - r * 0.01f, cy - cos(a).toFloat() * r * 0.89f - r * 0.012f, r * 0.02f, p)
        }
        // Zifferblatt
        val fr = r * 0.78f
        p.shader = RadialGradient(cx, cy - fr * 0.3f, fr * 1.3f, col("#FBF3DE"), col("#E3D2AC"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, fr, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = r * 0.02f
        p.color = col("#5A3519")
        c.drawCircle(cx, cy, fr, p)
        p.strokeWidth = r * 0.006f
        c.drawCircle(cx, cy, fr * 0.9f, p)
        c.drawCircle(cx, cy, fr * 0.62f, p)
        p.style = Paint.Style.FILL
        for (i in 0 until 60) {
            if (i % 5 == 0) continue
            val a = Math.toRadians(i * 6.0)
            p.color = col("#7A5A3A")
            c.drawCircle(cx + sin(a).toFloat() * fr * 0.95f, cy - cos(a).toFloat() * fr * 0.95f, r * 0.008f, p)
        }
        val roman = arrayOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")
        tp.typeface = Typeface.create("serif", Typeface.BOLD)
        tp.textSize = fr * 0.15f
        tp.color = col("#2B1A0E")
        tp.clearShadowLayer()
        for (n in 0 until 12) {
            val a = Math.toRadians(n * 30.0)
            val tx = cx + sin(a).toFloat() * fr * 0.76f
            val ty = cy - cos(a).toFloat() * fr * 0.76f - (tp.descent() + tp.ascent()) / 2f
            c.drawText(roman[n], tx, ty, tp)
        }
        ornateHand(c, cx, cy, (hh % 12 + mm / 60f) * 30f, fr * 0.5f, fr * 0.07f)
        ornateHand(c, cx, cy, (mm + ss / 60f) * 6f, fr * 0.78f, fr * 0.055f)
        p.color = col("#C8963E")
        c.drawCircle(cx, cy, fr * 0.05f, p)
        p.color = col("#5A3519")
        c.drawCircle(cx, cy, fr * 0.02f, p)
    }

    private fun ornateHand(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, w: Float) {
        c.save()
        c.rotate(deg, cx, cy)
        p.style = Paint.Style.FILL
        p.color = col("#1E120A")
        val path = Path()
        path.moveTo(cx - w * 0.18f, cy + len * 0.12f)
        path.lineTo(cx - w * 0.18f, cy - len * 0.62f)
        path.lineTo(cx - w * 0.9f, cy - len * 0.74f)
        path.lineTo(cx, cy - len)
        path.lineTo(cx + w * 0.9f, cy - len * 0.74f)
        path.lineTo(cx + w * 0.18f, cy - len * 0.62f)
        path.lineTo(cx + w * 0.18f, cy + len * 0.12f)
        path.close()
        c.drawPath(path, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = w * 0.18f
        c.drawCircle(cx, cy - len * 0.42f, w * 0.45f, p)
        p.style = Paint.Style.FILL
        c.restore()
    }
}
