package de.datacreate.radiouhr

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.RadialGradient
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import java.util.Random
import kotlin.math.min
import kotlin.math.sin

private fun withAlpha(c: Int, a: Int) = Color.argb(a, Color.red(c), Color.green(c), Color.blue(c))

abstract class BaseDrawable : Drawable() {
    protected val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    protected fun fill(c: Canvas, r: RectF, rad: Float, color: Int) {
        p.shader = null
        p.style = Paint.Style.FILL
        p.color = color
        c.drawRoundRect(r, rad, rad, p)
    }

    protected fun grad(c: Canvas, r: RectF, rad: Float, top: Int, bottom: Int) {
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, r.top, 0f, r.bottom, top, bottom, Shader.TileMode.CLAMP)
        c.drawRoundRect(r, rad, rad, p)
        p.shader = null
    }

    protected fun stroke(c: Canvas, r: RectF, rad: Float, color: Int, w: Float) {
        p.shader = null
        p.style = Paint.Style.STROKE
        p.strokeWidth = w
        p.color = color
        c.drawRoundRect(r, rad, rad, p)
        p.style = Paint.Style.FILL
    }
}

/** Tasten im Stil des jeweiligen Designs. */
class ButtonDrawable(
    private val style: BtnStyle,
    private val k: Skin,
    private val pressed: Boolean,
    private val primary: Boolean,
    private val d: Float
) : BaseDrawable() {

    override fun draw(c: Canvas) {
        val b = RectF(bounds)
        if (b.width() <= 0f || b.height() <= 0f) return
        when (style) {
            BtnStyle.FLAT -> {
                fill(c, b, k.corner * d, if (pressed) k.btnPressed else k.btnFill)
                stroke(c, inset(b, 0.5f * d), k.corner * d, k.btnStroke, 1 * d)
            }

            BtnStyle.IVORY -> {
                val depth = 5 * d
                val rad = 5 * d
                // Sockel / Schatten
                fill(c, RectF(b.left, b.top + depth, b.right, b.bottom), rad, col("#1E1208"))
                val top = if (pressed) b.top + depth - 1.5f * d else b.top
                val body = RectF(b.left + 1 * d, top, b.right - 1 * d, top + b.height() - depth)
                grad(
                    c, body, rad,
                    if (pressed) col("#D9C69C") else col("#FBF3DE"),
                    if (pressed) col("#B49F74") else col("#DCC9A0")
                )
                p.color = col("#99FFFFFF")
                p.strokeWidth = 1.2f * d
                c.drawLine(body.left + rad, body.top + 1.5f * d, body.right - rad, body.top + 1.5f * d, p)
                stroke(c, body, rad, col("#55000000"), 1 * d)
            }

            BtnStyle.KEYPAD -> {
                val rad = 10 * d
                if (!pressed) fill(c, RectF(b.left, b.top + 3 * d, b.right, b.bottom), rad, col("#99000000"))
                val body = RectF(b.left, if (pressed) b.top + 2 * d else b.top, b.right, b.bottom - if (pressed) 0f else 3 * d)
                grad(
                    c, body, rad,
                    if (pressed) col("#2A2E34") else col("#4E545C"),
                    if (pressed) col("#3A3F46") else col("#2B2F35")
                )
                stroke(c, body, rad, col("#15171A"), 1.5f * d)
                p.color = col("#33FFFFFF")
                p.strokeWidth = 1 * d
                c.drawLine(body.left + rad, body.top + 1.5f * d, body.right - rad, body.top + 1.5f * d, p)
                if (primary || pressed) {
                    p.color = withAlpha(k.accent, if (pressed) 230 else 120)
                    c.drawRoundRect(
                        RectF(body.centerX() - 8 * d, body.bottom - 6 * d, body.centerX() + 8 * d, body.bottom - 4 * d),
                        1 * d, 1 * d, p
                    )
                }
            }

            BtnStyle.LCD -> {
                val rad = 3 * d
                fill(c, b, rad, if (pressed) col("#1A301A") else col("#0E1A0E"))
                p.strokeWidth = 2 * d
                p.color = if (pressed) k.btnStroke else col("#000000")
                c.drawLine(b.left, b.top + 1 * d, b.right, b.top + 1 * d, p)
                c.drawLine(b.left + 1 * d, b.top, b.left + 1 * d, b.bottom, p)
                p.color = if (pressed) col("#000000") else k.btnStroke
                c.drawLine(b.left, b.bottom - 1 * d, b.right, b.bottom - 1 * d, p)
                c.drawLine(b.right - 1 * d, b.top, b.right - 1 * d, b.bottom, p)
            }

            BtnStyle.NEON -> {
                val rad = 12 * d
                val r = inset(b, 5 * d)
                if (pressed) fill(c, r, rad, withAlpha(k.accent, 60))
                stroke(c, r, rad, withAlpha(k.accent, 35), 9 * d)
                stroke(c, r, rad, withAlpha(k.accent, 70), 5 * d)
                stroke(c, r, rad, withAlpha(Color.WHITE, 200), 1.5f * d)
                stroke(c, r, rad, withAlpha(k.accent, 220), 2.5f * d)
            }

            BtnStyle.GLASS -> {
                val rad = 22 * d
                fill(c, b, rad, if (pressed) col("#40FFFFFF") else col("#1CFFFFFF"))
                val hi = RectF(b.left, b.top, b.right, b.centerY())
                c.save()
                c.clipRect(hi)
                grad(c, b, rad, col("#33FFFFFF"), col("#00FFFFFF"))
                c.restore()
                stroke(c, inset(b, 0.5f * d), rad, col("#55FFFFFF"), 1 * d)
            }

            BtnStyle.ROUND -> {
                val r = min(b.width(), b.height()) / 2f - 3 * d
                val cx = b.centerX()
                val cy = b.centerY()
                p.style = Paint.Style.FILL
                p.shader = null
                p.color = col("#26000000")
                c.drawCircle(cx, cy + 2.5f * d, r, p)
                val top = if (primary) col("#FFA531") else col("#FFFFFF")
                val bot = if (primary) col("#E67800") else col("#E2DED6")
                p.shader = LinearGradient(
                    0f, cy - r, 0f, cy + r,
                    if (pressed) bot else top, if (pressed) top else bot, Shader.TileMode.CLAMP
                )
                c.drawCircle(cx, cy + if (pressed) 1.5f * d else 0f, r, p)
                p.shader = null
                p.style = Paint.Style.STROKE
                p.strokeWidth = 1 * d
                p.color = col("#22000000")
                c.drawCircle(cx, cy + if (pressed) 1.5f * d else 0f, r, p)
                p.style = Paint.Style.FILL
            }

            BtnStyle.WOOD -> {
                val rad = 6 * d
                if (!pressed) fill(c, RectF(b.left, b.top + 3 * d, b.right, b.bottom), rad, col("#AA120A04"))
                val body = RectF(b.left, if (pressed) b.top + 2 * d else b.top, b.right, b.bottom - if (pressed) 0f else 3 * d)
                grad(
                    c, body, rad,
                    if (pressed) col("#6A4224") else col("#94633A"),
                    if (pressed) col("#4A2C15") else col("#5E3B1E")
                )
                c.save()
                c.clipRect(body)
                p.strokeWidth = 1 * d
                val rnd = Random(3)
                for (i in 0 until 7) {
                    p.color = withAlpha(if (i % 2 == 0) Color.BLACK else col("#FFDDAA"), 28)
                    val y0 = body.top + body.height() * (i + 0.5f) / 7f
                    val path = Path()
                    path.moveTo(body.left, y0)
                    var x = body.left
                    val ph = rnd.nextFloat() * 6f
                    while (x <= body.right) {
                        path.lineTo(x, y0 + sin(x / (14 * d) + ph) * 1.5f * d)
                        x += 4 * d
                    }
                    p.style = Paint.Style.STROKE
                    c.drawPath(path, p)
                }
                p.style = Paint.Style.FILL
                c.restore()
                stroke(c, body, rad, col("#2A170A"), 1.5f * d)
                stroke(c, inset(body, 3.5f * d), rad * 0.6f, col("#44FFE0B0"), 1 * d)
            }

            BtnStyle.NONE -> {
                if (pressed) {
                    p.color = col("#22FFFFFF")
                    c.drawCircle(b.centerX(), b.centerY(), min(b.width(), b.height()) / 2f, p)
                }
            }
        }
    }

    private fun inset(r: RectF, v: Float) = RectF(r.left + v, r.top + v, r.right - v, r.bottom - v)
}

/** Ganzflächige Hintergründe (Holz, gebürstetes Metall, Synthwave, Wellen). */
class BgDrawable(private val style: BgStyle, private val k: Skin, private val d: Float) : BaseDrawable() {
    private var cache: Bitmap? = null

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        cache = null
    }

    override fun draw(c: Canvas) {
        val b = bounds
        if (b.width() <= 0 || b.height() <= 0) return
        var bmp = cache
        if (bmp == null || bmp.width != b.width() || bmp.height != b.height()) {
            bmp = try {
                Bitmap.createBitmap(b.width(), b.height(), Bitmap.Config.ARGB_8888)
            } catch (e: OutOfMemoryError) {
                null
            }
            if (bmp == null) {
                c.drawColor(k.bgBottom)
                return
            }
            render(Canvas(bmp), b.width().toFloat(), b.height().toFloat())
            cache = bmp
        }
        c.drawBitmap(bmp, b.left.toFloat(), b.top.toFloat(), null)
    }

    private fun render(c: Canvas, w: Float, h: Float) {
        p.shader = LinearGradient(0f, 0f, 0f, h, k.bgTop, k.bgBottom, Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        when (style) {
            BgStyle.PLAIN -> {}
            BgStyle.WOOD, BgStyle.DARKWOOD -> wood(c, w, h)
            BgStyle.METAL -> metal(c, w, h)
            BgStyle.SYNTH -> synth(c, w, h)
            BgStyle.WAVES -> waves(c, w, h)
        }
    }

    private fun wood(c: Canvas, w: Float, h: Float) {
        val rnd = Random(11)
        p.style = Paint.Style.STROKE
        val vertical = style == BgStyle.DARKWOOD
        val len = if (vertical) h else w
        val across = if (vertical) w else h
        val lines = (across / (5 * d)).toInt().coerceIn(40, 260)
        for (i in 0 until lines) {
            val base = rnd.nextFloat() * across
            val amp = (2 + rnd.nextFloat() * 9) * d
            val freq = 1f / ((60 + rnd.nextFloat() * 160) * d)
            val ph = rnd.nextFloat() * 6.28f
            p.strokeWidth = (0.6f + rnd.nextFloat() * 2.2f) * d
            p.color = if (rnd.nextInt(3) == 0) withAlpha(col("#FFD9A8"), 10 + rnd.nextInt(18))
            else withAlpha(Color.BLACK, 18 + rnd.nextInt(40))
            val path = Path()
            var t = 0f
            while (t <= len) {
                val off = base + sin(t * freq + ph) * amp + sin(t * freq * 3.1f) * amp * 0.25f
                if (vertical) {
                    if (t == 0f) path.moveTo(off, t) else path.lineTo(off, t)
                } else {
                    if (t == 0f) path.moveTo(t, off) else path.lineTo(t, off)
                }
                t += 6 * d
            }
            c.drawPath(path, p)
        }
        // Astlöcher
        for (n in 0 until 3) {
            val cx = rnd.nextFloat() * w
            val cy = rnd.nextFloat() * h
            for (r in 1..6) {
                p.strokeWidth = 1.2f * d
                p.color = withAlpha(Color.BLACK, 40 - r * 4)
                val rx = r * 5f * d * (if (vertical) 0.6f else 1.6f)
                val ry = r * 5f * d * (if (vertical) 1.6f else 0.6f)
                c.drawOval(RectF(cx - rx, cy - ry, cx + rx, cy + ry), p)
            }
        }
        // Lack-Glanz
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, 0f, w, h, col("#22FFFFFF"), col("#00000000"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    private fun metal(c: Canvas, w: Float, h: Float) {
        val rnd = Random(5)
        p.style = Paint.Style.FILL
        var y = 0f
        while (y < h) {
            p.color = if (rnd.nextBoolean()) withAlpha(Color.WHITE, 4 + rnd.nextInt(12))
            else withAlpha(Color.BLACK, 6 + rnd.nextInt(16))
            c.drawRect(0f, y, w, y + 1 * d, p)
            y += 1.5f * d
        }
        p.shader = RadialGradient(w * 0.3f, h * 0.15f, maxOf(w, h) * 0.8f, col("#22FFFFFF"), col("#00FFFFFF"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
    }

    private fun synth(c: Canvas, w: Float, h: Float) {
        val horizon = h * 0.64f
        val cx = w / 2f
        val r = min(w, h) * 0.3f
        // Sonne
        c.save()
        c.clipRect(0f, 0f, w, horizon)
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(0f, horizon - 2 * r, 0f, horizon, col("#66FFD34D"), col("#66FF2BD6"), Shader.TileMode.CLAMP)
        c.drawCircle(cx, horizon - r * 0.35f, r, p)
        p.shader = null
        p.color = k.bgBottom
        var sy = horizon - r * 0.75f
        var gap = 2 * d
        while (sy < horizon) {
            c.drawRect(cx - r, sy, cx + r, sy + gap, p)
            sy += gap * 3.2f
            gap *= 1.25f
        }
        c.restore()
        // Boden
        p.color = col("#0A0318")
        c.drawRect(0f, horizon, w, h, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = 1.2f * d
        p.color = withAlpha(k.accent, 140)
        c.drawLine(0f, horizon, w, horizon, p)
        var gy = 0f
        var step = 4 * d
        while (horizon + gy < h) {
            c.drawLine(0f, horizon + gy, w, horizon + gy, p)
            gy += step
            step *= 1.35f
        }
        for (i in -12..12) {
            c.drawLine(cx + i * w * 0.02f, horizon, cx + i * w * 0.22f, h, p)
        }
        p.style = Paint.Style.FILL
        // Sterne
        val rnd = Random(9)
        for (i in 0 until 60) {
            p.color = withAlpha(Color.WHITE, 40 + rnd.nextInt(120))
            c.drawCircle(rnd.nextFloat() * w, rnd.nextFloat() * horizon * 0.8f, (0.5f + rnd.nextFloat()) * d, p)
        }
    }

    private fun waves(c: Canvas, w: Float, h: Float) {
        p.style = Paint.Style.FILL
        p.shader = RadialGradient(w * 0.5f, 0f, maxOf(w, h) * 0.7f, col("#332EC4E6"), col("#002EC4E6"), Shader.TileMode.CLAMP)
        c.drawRect(0f, 0f, w, h, p)
        p.shader = null
        val layers = arrayOf(col("#142EC4E6"), col("#18FFFFFF"), col("#222EC4E6"), col("#14FFFFFF"))
        layers.forEachIndexed { i, color ->
            val base = h * (0.62f + i * 0.09f)
            val amp = (10 + i * 4) * d
            val path = Path()
            path.moveTo(0f, h)
            var x = 0f
            while (x <= w + 8 * d) {
                path.lineTo(x, base + sin(x / ((70 + i * 25) * d) + i * 1.7f) * amp)
                x += 8 * d
            }
            path.lineTo(w, h)
            path.close()
            p.color = color
            c.drawPath(path, p)
        }
    }
}

/** Rahmen / Flächen hinter Uhr- oder Radiobereich. */
class PanelDrawable(private val style: PanelStyle, private val k: Skin, private val d: Float) : BaseDrawable() {
    override fun draw(c: Canvas) {
        val b = RectF(bounds)
        if (b.width() <= 0f) return
        when (style) {
            PanelStyle.NONE -> {}
            PanelStyle.GRILLE -> {
                val rad = 14 * d
                fill(c, b, rad, col("#2B1A0E"))
                c.save()
                val inner = RectF(b.left + 4 * d, b.top + 4 * d, b.right - 4 * d, b.bottom - 4 * d)
                c.clipRect(inner)
                p.strokeWidth = 1 * d
                p.color = col("#3D2716")
                var x = inner.left
                while (x < inner.right) {
                    c.drawLine(x, inner.top, x, inner.bottom, p)
                    x += 3 * d
                }
                p.color = col("#33000000")
                var y = inner.top
                while (y < inner.bottom) {
                    c.drawLine(inner.left, y, inner.right, y, p)
                    y += 3 * d
                }
                c.restore()
                stroke(c, RectF(b.left + 2 * d, b.top + 2 * d, b.right - 2 * d, b.bottom - 2 * d), rad, col("#C8963E"), 3 * d)
                stroke(c, RectF(b.left + 5 * d, b.top + 5 * d, b.right - 5 * d, b.bottom - 5 * d), rad * 0.8f, col("#55000000"), 1 * d)
            }
            PanelStyle.BEZEL -> {
                val rad = 16 * d
                grad(c, b, rad, col("#2A2A2A"), col("#141414"))
                stroke(c, b, rad, col("#3A3A3A"), 1 * d)
                val screen = RectF(b.left + 8 * d, b.top + 8 * d, b.right - 8 * d, b.bottom - 8 * d)
                grad(c, screen, 8 * d, k.lcdScreen, col("#020402"))
                stroke(c, screen, 8 * d, col("#000000"), 2 * d)
                p.color = col("#0DFFFFFF")
                c.drawRect(screen.left + 4 * d, screen.top + 3 * d, screen.right - 4 * d, screen.top + screen.height() * 0.18f, p)
            }
            PanelStyle.GLASS -> {
                val rad = 26 * d
                fill(c, b, rad, col("#14FFFFFF"))
                c.save()
                c.clipRect(b.left, b.top, b.right, b.top + b.height() * 0.4f)
                grad(c, b, rad, col("#22FFFFFF"), col("#00FFFFFF"))
                c.restore()
                stroke(c, b, rad, col("#33FFFFFF"), 1 * d)
            }
        }
    }
}
