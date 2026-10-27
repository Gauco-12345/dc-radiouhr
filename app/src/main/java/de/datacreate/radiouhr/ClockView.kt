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
import android.graphics.Bitmap
import android.os.SystemClock
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
            ClockStyle.STATION -> station(canvas)
            ClockStyle.CUCKOO -> {
                cuckoo(canvas)
                postInvalidateDelayed(50)
            }
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

    // ------------------------------------------------------------ Bahnhofsuhr

    private fun station(c: Canvas) {
        val cx = width / 2f
        val cy = height / 2f
        val r = min(width, height) / 2f - 4 * d
        p.style = Paint.Style.FILL
        p.shader = null
        p.color = col("#55000000")
        c.drawCircle(cx, cy + 4 * d, r, p)
        p.shader = LinearGradient(cx - r, cy - r, cx + r, cy + r, col("#E8E8E8"), col("#7A7A7A"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, r, p)
        p.shader = null
        val fr = r * 0.9f
        p.color = Color.WHITE
        c.drawCircle(cx, cy, fr, p)
        // Striche
        p.color = col("#111111")
        for (i in 0 until 60) {
            c.save()
            c.rotate(i * 6f, cx, cy)
            if (i % 5 == 0) c.drawRect(cx - fr * 0.035f, cy - fr * 0.95f, cx + fr * 0.035f, cy - fr * 0.7f, p)
            else c.drawRect(cx - fr * 0.012f, cy - fr * 0.95f, cx + fr * 0.012f, cy - fr * 0.87f, p)
            c.restore()
        }
        // Stundenzeiger
        bar(c, cx, cy, (hh % 12 + mm / 60f) * 30f, fr * 0.62f, fr * 0.18f, fr * 0.075f, fr * 0.055f, col("#111111"))
        bar(c, cx, cy, (mm + ss / 60f) * 6f, fr * 0.9f, fr * 0.18f, fr * 0.055f, fr * 0.04f, col("#111111"))
        // Kelle
        c.save()
        c.rotate(ss * 6f, cx, cy)
        p.color = col("#D7141A")
        c.drawRect(cx - fr * 0.012f, cy - fr * 0.6f, cx + fr * 0.012f, cy + fr * 0.3f, p)
        c.drawCircle(cx, cy - fr * 0.6f, fr * 0.1f, p)
        c.restore()
        p.color = col("#D7141A")
        c.drawCircle(cx, cy, fr * 0.03f, p)
    }

    private fun bar(c: Canvas, cx: Float, cy: Float, deg: Float, len: Float, tail: Float, wBase: Float, wTip: Float, color: Int) {
        c.save()
        c.rotate(deg, cx, cy)
        val path = Path()
        path.moveTo(cx - wBase / 2, cy + tail)
        path.lineTo(cx - wTip / 2, cy - len)
        path.lineTo(cx + wTip / 2, cy - len)
        path.lineTo(cx + wBase / 2, cy + tail)
        path.close()
        p.style = Paint.Style.FILL
        p.color = color
        c.drawPath(path, p)
        c.restore()
    }

    // ------------------------------------------------------------ Kuckucksuhr

    private var houseCache: Bitmap? = null

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        houseCache = null
    }

    private fun wd() = min(width * 0.92f, height / 1.72f)

    private fun cuckoo(c: Canvas) {
        val W = wd()
        val cx = width / 2f
        val y0 = (height - W * 1.72f) / 2f
        val yE = y0 + W * 0.42f
        val yB = yE + W * 0.62f
        var bmp = houseCache
        if (bmp == null || bmp.width != width || bmp.height != height) {
            bmp = try { Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888) } catch (e: OutOfMemoryError) { null }
            if (bmp != null) {
                drawHouse(Canvas(bmp), W, cx, y0, yE, yB)
                houseCache = bmp
            }
        }
        if (bmp != null) c.drawBitmap(bmp, 0f, 0f, null) else drawHouse(c, W, cx, y0, yE, yB)

        val t = SystemClock.uptimeMillis() / 1000.0
        // Gewichte (sinken im Lauf der Stunde)
        for (side in intArrayOf(-1, 1)) {
            val wx = cx + side * W * 0.27f
            val drop = (mm + ss / 60f) / 60f * W * 0.2f * (if (side < 0) 1f else 0.7f)
            val wy = yB + W * 0.12f + drop
            p.style = Paint.Style.STROKE
            p.strokeWidth = W * 0.006f
            p.color = col("#C8963E")
            var cy0 = yB + W * 0.06f
            var odd = false
            while (cy0 < wy) {
                val lw = if (odd) W * 0.005f else W * 0.009f
                c.drawOval(RectF(wx - lw, cy0, wx + lw, cy0 + W * 0.016f), p)
                cy0 += W * 0.014f
                odd = !odd
            }
            p.style = Paint.Style.FILL
            pineCone(c, wx, wy, W)
        }
        // Pendel
        val ang = 11.0 * sin(t * 2 * Math.PI / 1.6)
        c.save()
        c.rotate(ang.toFloat(), cx, yB + W * 0.06f)
        p.color = col("#2A170A")
        p.strokeWidth = W * 0.014f
        p.style = Paint.Style.STROKE
        c.drawLine(cx, yB + W * 0.06f, cx, yB + W * 0.46f, p)
        p.style = Paint.Style.FILL
        leaf(c, cx, yB + W * 0.5f, W * 0.075f, W * 0.1f, 0f, col("#8A5A30"), col("#4A2A12"))
        p.color = col("#C8963E")
        c.drawCircle(cx, yB + W * 0.5f, W * 0.022f, p)
        c.restore()

        // Zifferblatt
        woodFace(c, cx, yE + W * 0.31f, W * 0.2f)

        // Kuckuck
        val doorCx = cx
        val doorCy = yE - W * 0.115f
        val dw = W * 0.13f
        val dh = W * 0.14f
        val calling = (mm == 0 && ss < 12) || (mm == 30 && ss < 4)
        val door = RectF(doorCx - dw / 2, doorCy - dh / 2, doorCx + dw / 2, doorCy + dh / 2)
        if (calling) {
            p.color = col("#140A04")
            c.drawRoundRect(door, dw / 2, dw / 2, p)
            val out = ((sin(t * 2 * Math.PI / 1.0) + 1) / 2).toFloat()
            bird(c, doorCx, doorCy + dh * 0.1f, W * 0.07f, out)
            // offene Türflügel
            p.color = col("#6A4424")
            c.drawRect(door.left - dw * 0.35f, door.top + dh * 0.1f, door.left - dw * 0.05f, door.bottom, p)
        } else {
            p.shader = LinearGradient(0f, door.top, 0f, door.bottom, col("#8A5A30"), col("#5A3519"), Shader.TileMode.CLAMP)
            c.drawRoundRect(door, dw / 2, dw / 2, p)
            p.shader = null
            p.style = Paint.Style.STROKE
            p.strokeWidth = W * 0.006f
            p.color = col("#2A170A")
            c.drawRoundRect(door, dw / 2, dw / 2, p)
            c.drawLine(doorCx, door.top + dh * 0.15f, doorCx, door.bottom, p)
            p.style = Paint.Style.FILL
            p.color = col("#C8963E")
            c.drawCircle(doorCx + dw * 0.18f, doorCy + dh * 0.15f, W * 0.008f, p)
        }
    }

    private fun leaf(c: Canvas, x: Float, y: Float, rw: Float, rh: Float, rot: Float, light: Int, dark: Int) {
        c.save()
        c.rotate(rot, x, y)
        val path = Path()
        path.moveTo(x, y - rh)
        path.cubicTo(x + rw * 1.3f, y - rh * 0.4f, x + rw * 0.9f, y + rh * 0.6f, x, y + rh)
        path.cubicTo(x - rw * 0.9f, y + rh * 0.6f, x - rw * 1.3f, y - rh * 0.4f, x, y - rh)
        path.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(x - rw, y - rh, x + rw, y + rh, light, dark, Shader.TileMode.CLAMP)
        c.drawPath(path, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = maxOf(1f, rw * 0.08f)
        p.color = col("#66000000")
        c.drawLine(x, y - rh * 0.8f, x, y + rh * 0.85f, p)
        for (k in 1..3) {
            val yy = y - rh * 0.5f + k * rh * 0.35f
            c.drawLine(x, yy, x + rw * 0.55f, yy - rh * 0.22f, p)
            c.drawLine(x, yy, x - rw * 0.55f, yy - rh * 0.22f, p)
        }
        p.style = Paint.Style.FILL
        c.restore()
    }

    private fun pineCone(c: Canvas, x: Float, top: Float, W: Float) {
        val cw = W * 0.05f
        val chh = W * 0.15f
        p.color = col("#C8963E")
        c.drawCircle(x, top, W * 0.012f, p)
        val r = RectF(x - cw, top + W * 0.01f, x + cw, top + W * 0.01f + chh)
        p.shader = LinearGradient(r.left, 0f, r.right, 0f, col("#8A5424"), col("#3A200C"), Shader.TileMode.CLAMP)
        c.drawOval(r, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = W * 0.004f
        p.color = col("#2A1506")
        var yy = r.top + chh * 0.12f
        var row = 0
        while (yy < r.bottom - chh * 0.08f) {
            val f = 1f - Math.abs((yy - r.centerY()) / (chh / 2f))
            val half = cw * (0.35f + 0.65f * f)
            val off = if (row % 2 == 0) 0f else half / 2.5f
            var xx = x - half + off
            while (xx < x + half) {
                c.drawArc(RectF(xx - cw * 0.22f, yy - chh * 0.04f, xx + cw * 0.22f, yy + chh * 0.05f), 0f, 180f, false, p)
                xx += cw * 0.4f
            }
            yy += chh * 0.08f
            row++
        }
        p.style = Paint.Style.FILL
    }

    private fun bird(c: Canvas, x: Float, y: Float, s: Float, out: Float) {
        val bx = x + s * 0.3f * out
        p.color = col("#8A6A4A")
        c.drawOval(RectF(bx - s * 0.8f, y - s * 0.45f, bx + s * 0.6f, y + s * 0.45f), p)
        p.color = col("#6A4A2A")
        c.drawOval(RectF(bx - s * 0.6f, y - s * 0.25f, bx + s * 0.1f, y + s * 0.3f), p)
        p.color = col("#9A7A5A")
        c.drawCircle(bx + s * 0.55f, y - s * 0.35f, s * 0.35f, p)
        val beak = Path()
        val open = 0.15f + out * 0.2f
        beak.moveTo(bx + s * 0.85f, y - s * 0.42f)
        beak.lineTo(bx + s * 1.35f, y - s * (0.35f + open / 2))
        beak.lineTo(bx + s * 0.85f, y - s * 0.28f)
        beak.close()
        p.color = col("#E0A040")
        c.drawPath(beak, p)
        p.color = col("#000000")
        c.drawCircle(bx + s * 0.62f, y - s * 0.42f, s * 0.07f, p)
    }

    private fun drawHouse(c: Canvas, W: Float, cx: Float, y0: Float, yE: Float, yB: Float) {
        val peakY = y0 + W * 0.1f
        val roofL = cx - W * 0.62f
        val roofR = cx + W * 0.62f
        // Schatten
        p.style = Paint.Style.FILL
        p.color = col("#55000000")
        c.drawRect(cx - W * 0.42f + W * 0.03f, yE + W * 0.03f, cx + W * 0.42f + W * 0.03f, yB + W * 0.1f, p)
        // Korpus
        val body = RectF(cx - W * 0.42f, yE - W * 0.02f, cx + W * 0.42f, yB)
        p.shader = LinearGradient(body.left, 0f, body.right, 0f,
            intArrayOf(col("#5A3519"), col("#8A5A30"), col("#5A3519")), null, Shader.TileMode.CLAMP)
        c.drawRect(body, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = W * 0.004f
        for (i in 0 until 14) {
            p.color = if (i % 2 == 0) col("#22000000") else col("#18FFD9A8")
            val x = body.left + body.width() * (i + 0.5f) / 14f
            c.drawLine(x, body.top, x + W * 0.01f * sin(i.toFloat()), body.bottom, p)
        }
        p.strokeWidth = W * 0.012f
        p.color = col("#2A170A")
        c.drawRect(body, p)
        p.style = Paint.Style.FILL
        // Giebelfeld
        val gable = Path()
        gable.moveTo(body.left, yE)
        gable.lineTo(cx, peakY + W * 0.06f)
        gable.lineTo(body.right, yE)
        gable.close()
        p.shader = LinearGradient(0f, peakY, 0f, yE, col("#7A4A24"), col("#5A3519"), Shader.TileMode.CLAMP)
        c.drawPath(gable, p)
        p.shader = null
        // Dach mit Schindeln
        for (side in intArrayOf(-1, 1)) {
            val roof = Path()
            val ex = if (side < 0) roofL else roofR
            roof.moveTo(cx, peakY)
            roof.lineTo(ex, yE + W * 0.02f)
            roof.lineTo(ex + side * -W * 0.09f, yE + W * 0.06f)
            roof.lineTo(cx, peakY + W * 0.09f)
            roof.close()
            p.color = col("#2E1A0B")
            c.drawPath(roof, p)
            c.save()
            c.clipPath(roof)
            p.style = Paint.Style.STROKE
            p.strokeWidth = W * 0.004f
            p.color = col("#55D9A86A")
            var k = 0f
            while (k < 1.2f) {
                val sx = cx + (ex - cx) * k
                c.drawLine(sx, peakY - W * 0.1f, sx - side * W * 0.05f, yE + W * 0.1f, p)
                k += 0.06f
            }
            p.style = Paint.Style.FILL
            c.restore()
            // geschnitztes Laub an der Dachkante
            val steps = 9
            for (i in 1..steps) {
                val f = i / (steps + 1f)
                val lx = cx + (ex - cx) * f
                val ly = peakY + (yE + W * 0.02f - peakY) * f
                val rot = if (side < 0) -35f else 35f
                leaf(c, lx, ly - W * 0.012f, W * 0.022f, W * 0.04f, rot + (if (i % 2 == 0) 20f else -20f), col("#7A4A24"), col("#2E1A0B"))
            }
        }
        // Vogel auf dem Giebel
        p.color = col("#3A2210")
        c.drawOval(RectF(cx - W * 0.07f, peakY - W * 0.085f, cx + W * 0.05f, peakY - W * 0.02f), p)
        c.drawCircle(cx + W * 0.055f, peakY - W * 0.085f, W * 0.028f, p)
        val tail = Path()
        tail.moveTo(cx - W * 0.06f, peakY - W * 0.06f)
        tail.lineTo(cx - W * 0.13f, peakY - W * 0.11f)
        tail.lineTo(cx - W * 0.11f, peakY - W * 0.04f)
        tail.close()
        c.drawPath(tail, p)
        val bk = Path()
        bk.moveTo(cx + W * 0.08f, peakY - W * 0.095f)
        bk.lineTo(cx + W * 0.11f, peakY - W * 0.085f)
        bk.lineTo(cx + W * 0.08f, peakY - W * 0.075f)
        bk.close()
        c.drawPath(bk, p)
        leaf(c, cx - W * 0.04f, peakY - W * 0.005f, W * 0.025f, W * 0.04f, -70f, col("#7A4A24"), col("#2E1A0B"))
        leaf(c, cx + W * 0.04f, peakY - W * 0.005f, W * 0.025f, W * 0.04f, 70f, col("#7A4A24"), col("#2E1A0B"))
        // Türrahmen Kuckuck
        val dw = W * 0.13f
        val dh = W * 0.14f
        val dcy = yE - W * 0.115f
        p.color = col("#2A170A")
        c.drawRoundRect(RectF(cx - dw / 2 - W * 0.015f, dcy - dh / 2 - W * 0.015f, cx + dw / 2 + W * 0.015f, dcy + dh / 2 + W * 0.01f), dw, dw, p)
        // Laubkranz um das Zifferblatt
        val fcx = cx
        val fcy = yE + W * 0.31f
        val fr = W * 0.2f
        for (i in 0 until 22) {
            val a = i * 360f / 22f
            val rad = Math.toRadians(a.toDouble())
            val lx = fcx + (fr * 1.13f * sin(rad)).toFloat()
            val ly = fcy - (fr * 1.13f * cos(rad)).toFloat()
            leaf(c, lx, ly, W * 0.028f, W * 0.05f, a + 25f, col("#8A5A30"), col("#3A2210"))
        }
        // Eckverzierungen
        for (sx in intArrayOf(-1, 1)) for (sy in intArrayOf(-1, 1)) {
            val ex = cx + sx * W * 0.34f
            val ey = fcy + sy * W * 0.22f
            for (k in 0 until 3) {
                leaf(c, ex, ey, W * 0.026f, W * 0.055f, sx * sy * 45f + (k - 1) * 40f, col("#8A5A30"), col("#3A2210"))
            }
        }
        // Sockel
        val base = RectF(cx - W * 0.48f, yB, cx + W * 0.48f, yB + W * 0.06f)
        p.shader = LinearGradient(0f, base.top, 0f, base.bottom, col("#6A4022"), col("#2E1A0B"), Shader.TileMode.CLAMP)
        c.drawRect(base, p)
        p.shader = null
        p.color = col("#3A2210")
        val n = 12
        val bw = base.width() / n
        for (i in 0 until n) {
            c.drawArc(RectF(base.left + i * bw, base.bottom - bw * 0.5f, base.left + (i + 1) * bw, base.bottom + bw * 0.5f), 0f, 180f, true, p)
        }
    }

    private fun woodFace(c: Canvas, cx: Float, cy: Float, fr: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(cx, cy - fr * 0.3f, fr * 1.3f, col("#FBF3DE"), col("#E3D2AC"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, cy, fr, p)
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = fr * 0.04f
        p.color = col("#3A2210")
        c.drawCircle(cx, cy, fr, p)
        p.strokeWidth = fr * 0.01f
        c.drawCircle(cx, cy, fr * 0.9f, p)
        p.style = Paint.Style.FILL
        val roman = arrayOf("XII", "I", "II", "III", "IIII", "V", "VI", "VII", "VIII", "IX", "X", "XI")
        tp.typeface = Typeface.create("serif", Typeface.BOLD)
        tp.textSize = fr * 0.17f
        tp.color = col("#2B1A0E")
        tp.clearShadowLayer()
        for (n in 0 until 12) {
            val a = Math.toRadians(n * 30.0)
            val tx = cx + sin(a).toFloat() * fr * 0.73f
            val ty = cy - cos(a).toFloat() * fr * 0.73f - (tp.descent() + tp.ascent()) / 2f
            c.drawText(roman[n], tx, ty, tp)
        }
        ornateHand(c, cx, cy, (hh % 12 + mm / 60f) * 30f, fr * 0.5f, fr * 0.08f)
        ornateHand(c, cx, cy, (mm + ss / 60f) * 6f, fr * 0.8f, fr * 0.06f)
        p.color = col("#C8963E")
        c.drawCircle(cx, cy, fr * 0.06f, p)
    }
}
