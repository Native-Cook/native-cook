package com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer

import android.content.res.AssetManager
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.SweepGradient
import android.graphics.Typeface
import android.os.Build
import android.util.Log
import androidx.core.graphics.PathParser
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerMotion as M
import com.ngocdevv.nativecook.blocks.media.wovenspeakerplayer.WovenSpeakerPlayerTheme as Theme
import java.util.Locale
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.tan

/**
 * The screen in one canvas, in the RN order: the page, the lamp's wash, the dome (the weave shader, or the strand fallback), then in design pt the
 * display (glass, bloom, arc, handle, bezel, its content: time, the row, the covers, the amber line, the play / pause glyph, the two discs, the
 * title and artist) and the page's three lines and pill. Everything is in dp: the caller scales the canvas by the density.
 */
internal class WovenSpeakerPlayerRenderer(private val assets: AssetManager) {
    private fun face(name: String) = Typeface.createFromAsset(assets, "wovenspeakerplayer/wovenspeakerplayer_$name.ttf")
    private val fBold = face("InterDisplay-Bold")
    private val fReg = face("Inter-Regular")
    private val fMed = face("Inter-Medium")
    private val fSemi = face("Inter-SemiBold")
    private var pass: Any? = null
    private val fallback by lazy { WovenSpeakerPlayerFallback() }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG)
    private val bloomPaint = Paint().apply { xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD) }
    private val rect = RectF()
    private val path = Path()
    private val clip = Path()

    private fun p(d: String): Path = try {
        PathParser.createPathFromPathData(d)
    } catch (error: RuntimeException) {
        Path()
    }

    private fun a(c: Int, alpha: Double) = Color.argb((alpha.coerceIn(0.0, 1.0) * 255).toInt(), Color.red(c), Color.green(c), Color.blue(c))

    // ---- shapes (RN `wsp-geometry.ts`) ----

    private fun f(v: Double) = String.format(Locale.US, "%.4f", v)

    /** a continuous-corner rectangle (the iOS squircle, corner smoothing 0.6), centred on 0, 0, as path data */
    private fun squircle(size: Double, radius: Double): Path {
        val x = -size / 2
        val y = -size / 2
        val w = size
        val h = size
        val budget = max(min(w, h) / 2, 0.01)
        val r = min(radius, budget)
        var xi = 0.6
        if ((1 + xi) * r > budget) xi = max(budget / max(r, 0.01) - 1, 0.0)
        val pp = (1 + xi) * r
        val arcMeasure = 90 * (1 - xi)
        val arcLen = sin(Math.toRadians(arcMeasure / 2)) * r * Math.sqrt(2.0)
        val alpha = (90 - arcMeasure) / 2
        val p3p4 = r * tan(Math.toRadians(alpha / 2))
        val beta = 45 * xi
        val c = p3p4 * cos(Math.toRadians(beta))
        val d = c * tan(Math.toRadians(beta))
        val b = (pp - arcLen - c - d) / 3
        val aa = 2 * b
        val sb = StringBuilder()
        fun cmd(vararg v: Double) = v.joinToString(" ") { f(it) }
        sb.append("M ${f(x + w - pp)} ${f(y)} ")
        sb.append("c ${cmd(aa, 0.0, aa + b, 0.0, aa + b + c, d)} ")
        sb.append("a ${cmd(r, r)} 0 0 1 ${cmd(arcLen, arcLen)} ")
        sb.append("c ${cmd(d, c, d, b + c, d, aa + b + c)} ")
        sb.append("L ${f(x + w)} ${f(y + h - pp)} ")
        sb.append("c ${cmd(0.0, aa, 0.0, aa + b, -d, aa + b + c)} ")
        sb.append("a ${cmd(r, r)} 0 0 1 ${cmd(-arcLen, arcLen)} ")
        sb.append("c ${cmd(-c, d, -(b + c), d, -(aa + b + c), d)} ")
        sb.append("L ${f(x + pp)} ${f(y + h)} ")
        sb.append("c ${cmd(-aa, 0.0, -(aa + b), 0.0, -(aa + b + c), -d)} ")
        sb.append("a ${cmd(r, r)} 0 0 1 ${cmd(-arcLen, -arcLen)} ")
        sb.append("c ${cmd(-d, -c, -d, -(b + c), -d, -(aa + b + c))} ")
        sb.append("L ${f(x)} ${f(y + pp)} ")
        sb.append("c ${cmd(0.0, -aa, 0.0, -(aa + b), d, -(aa + b + c))} ")
        sb.append("a ${cmd(r, r)} 0 0 1 ${cmd(arcLen, -arcLen)} ")
        sb.append("c ${cmd(c, -d, b + c, -d, aa + b + c, -d)} Z")
        return p(sb.toString())
    }

    private val cover = squircle(Theme.COVER_SIZE, Theme.COVER_R)
    private val castArcs = Path().apply {
        val size = Theme.ROW_CAST
        val r1 = (size * 0.46).toFloat()
        val r2 = (size * 0.28).toFloat()
        val dy = (size * 0.06).toFloat()
        addArc(RectF(-r1, -r1 - dy, r1, r1 - dy), 150f, 240f)
        addArc(RectF(-r2, -r2 - dy, r2, r2 - dy), 150f, 240f)
    }
    private val castFoot = Path().apply {
        val size = Theme.ROW_CAST
        moveTo(0f, (-size * 0.06 + size * 0.02).toFloat())
        lineTo((size * 0.42).toFloat(), (size * 0.5).toFloat())
        lineTo((-size * 0.42).toFloat(), (size * 0.5).toFloat())
        close()
    }
    private val wave = Path().apply {
        val size = Theme.ROW_WAVE
        val hs = doubleArrayOf(0.3, 0.62, 1.0, 0.55, 0.28)
        for (i in hs.indices) {
            val x = ((i - 2) * size * 0.22).toFloat()
            val hh = (hs[i] * size * 0.5).toFloat()
            moveTo(x, -hh)
            lineTo(x, hh)
        }
    }

    private fun chevron(dir: Int, size: Double): Path = Path().apply {
        val h = (size * 0.5).toFloat()
        val w = (size * 0.5).toFloat()
        for (k in 0 until 2) {
            val x0 = dir * (k * w - w)
            moveTo(x0 - dir * w * 0.5f, -h)
            lineTo(x0 + dir * w * 0.5f, 0f)
            lineTo(x0 - dir * w * 0.5f, h)
            close()
        }
    }

    private val prevChevron = chevron(-1, Theme.DISC_GLYPH)
    private val nextChevron = chevron(1, Theme.DISC_GLYPH)

    // the play <-> pause glyph: top-left, top-right, bottom-right, bottom-left of each of two quads
    private val pauseL = doubleArrayOf(-6.5, -7.4, -2.1, -7.4, -2.1, 7.4, -6.5, 7.4)
    private val pauseR = doubleArrayOf(2.1, -7.4, 6.5, -7.4, 6.5, 7.4, 2.1, 7.4)
    private val playL = doubleArrayOf(-4.8, -8.2, 2.0, -4.04, 2.0, 4.04, -4.8, 8.2)
    private val playR = doubleArrayOf(2.0, -4.04, 8.6, 0.0, 8.6, 0.0, 2.0, 4.04)

    private fun glyphPath(g: Double): Path {
        path.rewind()
        for (q in 0 until 2) {
            val aa = if (q == 0) playL else playR
            val bb = if (q == 0) pauseL else pauseR
            path.moveTo((aa[0] + (bb[0] - aa[0]) * g).toFloat(), (aa[1] + (bb[1] - aa[1]) * g).toFloat())
            for (i in 1 until 4) path.lineTo((aa[i * 2] + (bb[i * 2] - aa[i * 2]) * g).toFloat(), (aa[i * 2 + 1] + (bb[i * 2 + 1] - aa[i * 2 + 1]) * g).toFloat())
            path.close()
        }
        return path
    }

    // the cover: a duotone poster in its own 68 x 68 box (centred on 0, 0)
    private val profile = "M-34 34 C-33 25 -28 20 -18 17 C-14 16 -12 14 -12 11 L-12 8 C-17 4 -20 -3 -19 -10 C-18 -20 -10 -26 -2 -25 C7 -24 12 -18 12 -12 " +
        "L15 -5.5 C15.5 -4.4 14.8 -3.8 13.2 -3.4 L12.8 -1 C13.6 -0.2 13.3 0.8 12.4 1.2 C12.9 2.4 12.6 4 11 4.6 C10 6.4 7.6 7 4.6 6.6 " +
        "L4.4 10 C6 13.5 11 15.5 18 17 C27 19 32 25 34 34 Z"
    private val bun = "M-16 -16 C-22 -16 -25 -22 -21.5 -26.5 C-17.5 -30.5 -10.5 -28.5 -10.5 -22.5 Z"

    private class Art(val figure: Path, val sx: Float, val sy: Float, val sr: Float, val rimDx: Float, val rimDy: Float)

    private fun mirrorX(svg: String): String {
        var k = 0
        return Regex("[MLCZ]|-?\\d*\\.?\\d+").replace(svg) { m ->
            val t = m.value
            if (t.matches(Regex("[MLCZ]"))) {
                k = 0
                t
            } else {
                val v = if (k++ % 2 == 0) -t.toDouble() else t.toDouble()
                (if (v == v.toLong().toDouble()) v.toLong().toString() else v.toString())
            }
        }
    }

    private val arts: List<Art> = listOf(
        Triple(false, true, doubleArrayOf(14.0, -8.0, 19.0)),
        Triple(true, false, doubleArrayOf(-12.0, -12.0, 16.0)),
        Triple(false, false, doubleArrayOf(2.0, 12.0, 25.0)),
    ).map { (mirror, hasBun, sun) ->
        val svg = if (hasBun) "$profile $bun" else profile
        val len = hypot(sun[0], sun[1]).takeIf { it != 0.0 } ?: 1.0
        Art(p(if (mirror) mirrorX(svg) else svg), sun[0].toFloat(), sun[1].toFloat(), sun[2].toFloat(), ((sun[0] / len) * 0.9).toFloat(), ((sun[1] / len) * 0.9 - 0.3).toFloat())
    }

    // ---- text ----

    private fun font(tf: Typeface, size: Double, color: Int) {
        text.typeface = tf
        text.textSize = size.toFloat()
        text.letterSpacing = 0f
        text.color = color
        text.alpha = Color.alpha(color)
    }

    /** width of a run: the glyphs' advances plus the tracking (em) between them */
    private fun runWidth(s: String, size: Double, track: Double): Float {
        text.letterSpacing = 0f
        return text.measureText(s) + (s.length - 1).coerceAtLeast(0) * (track * size).toFloat()
    }

    /** a run drawn glyph by glyph (so the tracking and the centring are exactly the RN ones): its left edge at x, baseline y */
    private fun run(c: Canvas, s: String, x: Float, y: Float, size: Double, track: Double) {
        var cx = x
        text.letterSpacing = 0f
        for (ch in s) {
            val t = ch.toString()
            c.drawText(t, cx, y, text)
            cx += text.measureText(t) + (track * size).toFloat()
        }
    }

    private fun centred(c: Canvas, s: String, tf: Typeface, size: Double, track: Double, color: Int, alpha: Double, cxx: Float, y: Float) {
        if (alpha <= 0.002) return
        font(tf, size, color)
        text.alpha = (alpha.coerceIn(0.0, 1.0) * Color.alpha(color)).toInt()
        val w = runWidth(s, size, track)
        run(c, s, cxx - w / 2, y, size, track)
    }

    private fun shaderPass(): WovenSpeakerPlayerShaderPass? {
        if (Build.VERSION.SDK_INT < 33) return null
        (pass as? WovenSpeakerPlayerShaderPass)?.let { return it }
        return try {
            WovenSpeakerPlayerShaderPass().also { pass = it }
        } catch (error: IllegalArgumentException) {
            Log.w("WovenSpeakerPlayer", "Runtime shader unavailable; using the Canvas fallback", error)
            null
        }
    }

    // ---- the frame ----

    class Params(val queue: List<WovenSpeakerPlayerTrack>, val duration: Double, val accent: Int, val accentTail: Int, val ink: Int, val mute: Int, val ambient: Boolean, val strands: Int, val fibre: Double)

    fun draw(c: Canvas, w: Float, h: Float, density: Float, l: Theme.Layout, e: WovenSpeakerPlayerEngine, pr: Params) {
        val look = e.look
        val s = l.s
        fill.shader = null
        fill.color = Theme.page
        c.drawRect(0f, 0f, w, h, fill)
        val cx = l.cx.toFloat()
        val cy = l.cy.toFloat()
        // the lamp's wash: the display's red reaches the page while it plays
        if (look.wash > 0.002) {
            fill.shader = RadialGradient(cx, cy, (Theme.WASH_R * s).toFloat(), a(pr.accent, look.wash), a(pr.accent, 0.0), Shader.TileMode.CLAMP)
            c.drawRect(0f, 0f, w, h, fill)
            fill.shader = null
        }
        // the dome
        val sp = shaderPass()
        if (sp != null && Build.VERSION.SDK_INT >= 33) {
            sp.draw(c, cx, cy, s.toFloat(), density, look, pr.duration, Theme.rgbOf(pr.accent), pr.fibre, pr.strands)
        } else {
            fallback.draw(c, cx, cy, s.toFloat(), look, pr.duration, pr.accent, pr.strands)
        }
        // design pt on the 402 x 874 canvas
        val ox = (l.cx - 201 * s).toFloat()
        val oy = l.y(0.0).toFloat()
        c.save()
        c.translate(ox, oy)
        c.scale(s.toFloat(), s.toFloat())
        c.save()
        c.translate(201f, l.domeY.toFloat())
        displayBase(c, look, pr)
        clip.rewind()
        clip.addCircle(0f, 0f, Theme.DISPLAY_R.toFloat(), Path.Direction.CW)
        c.save()
        c.clipPath(clip)
        nowPlaying(c, look, e, pr)
        c.restore()
        c.restore()
        pageText(c, look, e, pr)
        c.restore()
    }

    // ---- the display ----

    private fun displayBase(c: Canvas, look: WovenSpeakerPlayerLook, pr: Params) {
        fill.color = Theme.bezelDark
        c.drawCircle(0f, 0f, Theme.BEZEL_OUTER.toFloat(), fill)
        c.save()
        clip.rewind()
        clip.addCircle(0f, 0f, Theme.DISPLAY_R.toFloat(), Path.Direction.CW)
        c.clipPath(clip)
        val dr = Theme.DISPLAY_R.toFloat()
        fill.shader = LinearGradient(0f, -dr, 0f, dr, Theme.glassTop, Theme.glassBottom, Shader.TileMode.CLAMP)
        c.drawCircle(0f, 0f, dr, fill)
        fill.shader = null
        // the bloom: two soft lobes behind the cover, the lamp coming up before the mesh answers
        bloomPaint.alpha = (255 * look.bloom.coerceIn(0.0, 1.0)).toInt()
        val layer = c.saveLayer(-dr, -dr, dr, dr, bloomPaint)
        for (side in intArrayOf(-1, 1)) {
            val lx = (side * Theme.BLOOM_DX).toFloat()
            val ly = Theme.BLOOM_DY.toFloat()
            val rr = look.bloomR.toFloat()
            fill.shader = RadialGradient(lx, ly, rr, intArrayOf(a(Theme.bloom, 0xB0 / 255.0), a(Theme.bloom, 0x44 / 255.0), a(Theme.bloom, 0.0)), floatArrayOf(0f, 0.55f, 1f), Shader.TileMode.CLAMP)
            c.drawCircle(lx, ly, rr, fill)
        }
        fill.shader = null
        c.restoreToCount(layer)
        // the arc rides a faint track just inside the glass
        stroke.shader = null
        stroke.color = a(Color.WHITE, 0.07)
        stroke.strokeWidth = 1f
        c.drawCircle(0f, 0f, Theme.ARC_R.toFloat(), stroke)
        c.save()
        c.rotate(-90f)
        path.rewind()
        val ar = Theme.ARC_R.toFloat()
        path.addArc(RectF(-ar, -ar, ar, ar), 0f, max((look.pos / pr.duration) * 360, 4.0).toFloat())
        stroke.shader = SweepGradient(0f, 0f, intArrayOf(pr.accent, pr.accentTail, pr.accentTail), floatArrayOf(0f, 0.58f, 1f))
        stroke.strokeWidth = Theme.ARC_W.toFloat()
        c.drawPath(path, stroke)
        stroke.shader = null
        c.restore()
        // the handle on the arc's head
        if (look.handle > 0.002) {
            val ang = -PI / 2 + (look.pos / pr.duration) * 2 * PI
            c.save()
            c.translate((Theme.ARC_R * cos(ang)).toFloat(), (Theme.ARC_R * sin(ang)).toFloat())
            val k = (1 + 0.6 * look.head).toFloat()
            c.scale(k, k)
            fill.color = a(pr.accentTail, look.handle)
            c.drawCircle(0f, 0f, 3f, fill)
            fill.color = a(Color.WHITE, 0.28 * look.handle)
            c.drawCircle(0f, 0f, 3f, fill)
            c.restore()
        }
        c.restore()
        // the bezel: a thin metal ring that catches the key light on the upper left, and the lamp's red reflection
        stroke.shader = SweepGradient(
            0f, 0f, intArrayOf(Theme.bezelLo, Color.rgb(0x46, 0x47, 0x4C), Theme.bezelHi, Color.rgb(0x46, 0x47, 0x4C), Theme.bezelLo), floatArrayOf(0f, 0.3f, 0.625f, 0.85f, 1f),
        )
        stroke.strokeWidth = Theme.BEZEL_W.toFloat()
        c.drawCircle(0f, 0f, Theme.BEZEL_RING.toFloat(), stroke)
        stroke.shader = null
        stroke.color = a(Color.BLACK, 0.6)
        stroke.strokeWidth = 0.6f
        c.drawCircle(0f, 0f, (Theme.DISPLAY_R + 0.4).toFloat(), stroke)
        if (look.refl > 0.002) {
            val deg = -90 + (look.pos / pr.duration) * 360
            path.rewind()
            val br = Theme.BEZEL_RING.toFloat()
            path.addArc(RectF(-br, -br, br, br), (deg - 15).toFloat(), 30f)
            stroke.color = a(pr.accent, look.refl * 0.85)
            stroke.strokeWidth = 1.5f
            stroke.maskFilter = BlurMaskFilter(1.2f, BlurMaskFilter.Blur.NORMAL)
            c.drawPath(path, stroke)
            stroke.maskFilter = null
        }
    }

    private fun trackCover(c: Canvas, i: Int, variant: Int, look: WovenSpeakerPlayerLook, e: WovenSpeakerPlayerEngine) {
        val dist = (i - e.indexL) * M.SLIDE_SPAN + e.slideL.value
        val op = M.clamp01(1 - abs(dist) / (M.SLIDE_SPAN * 0.9))
        if (op <= 0.002) return
        val size = Theme.COVER_SIZE
        val half = (size / 2).toFloat()
        val art = arts[((variant % arts.size) + arts.size) % arts.size]
        val layer = c.saveLayerAlpha(-90f + (dist * M.COVER_FOLLOW).toFloat(), -60f, 90f + (dist * M.COVER_FOLLOW).toFloat(), 60f, (255 * op).toInt())
        c.translate((dist * M.COVER_FOLLOW).toFloat(), 0f)
        c.save()
        c.clipPath(cover)
        rect.set(-half, -half, half, half)
        fill.shader = LinearGradient(-half, -half, half * 0.5f, half, Theme.coverTop, Theme.coverBottom, Shader.TileMode.CLAMP)
        c.drawRect(rect, fill)
        c.save()
        val k = (size / 68).toFloat()
        c.scale(k, k)
        // the low sun and its glow
        fill.shader = RadialGradient(art.sx, art.sy, art.sr * 1.9f, intArrayOf(a(Theme.sunGlow, 0x66 / 255.0), a(Theme.sunGlow, 0.0)), null, Shader.TileMode.CLAMP)
        c.drawCircle(art.sx, art.sy, art.sr * 1.9f, fill)
        fill.shader = LinearGradient(art.sx, art.sy - art.sr, art.sx, art.sy + art.sr, Theme.sunGlow, Theme.sun, Shader.TileMode.CLAMP)
        c.drawCircle(art.sx, art.sy, art.sr, fill)
        // the silhouette, with a rim of light on its sun side
        fill.shader = null
        fill.color = Theme.rim
        c.save()
        c.translate(art.rimDx, art.rimDy)
        c.drawPath(art.figure, fill)
        c.restore()
        fill.shader = LinearGradient(0f, -26f, 0f, 34f, Theme.figure, Theme.figureLo, Shader.TileMode.CLAMP)
        c.drawPath(art.figure, fill)
        fill.shader = null
        fill.color = a(Color.BLACK, 0.09)
        for (kk in 0 until 34) {
            val y = -34f + kk * 2 + 1
            c.drawRect(-34f, y, 34f, y + 0.45f, fill)
        }
        c.restore()
        val dim = 0.2 * M.clamp01(look.turn)
        if (dim > 0.002) {
            fill.color = a(Color.BLACK, dim)
            c.drawRect(rect, fill)
        }
        c.restore()
        stroke.shader = null
        stroke.color = a(Color.WHITE, 0.12)
        stroke.strokeWidth = 0.5f
        c.drawPath(cover, stroke)
        c.restoreToCount(layer)
    }

    private fun nowPlaying(c: Canvas, look: WovenSpeakerPlayerLook, e: WovenSpeakerPlayerEngine, pr: Params) {
        // the time: one run, each character centred in a tabular cell so the label counts without wobbling
        font(fMed, Theme.TIME_SIZE, Theme.time)
        text.alpha = (0.92 * 255).toInt()
        val w0 = text.measureText("0")
        val wc = text.measureText(":")
        val xm = -(3 * w0 + wc) / 2
        val cells = floatArrayOf(xm - w0, xm, xm + w0, xm + w0 + wc, xm + 2 * w0 + wc)
        val cellW = floatArrayOf(w0, w0, wc, w0, w0)
        val label = M.timeLabel(look.pos)
        val padded = if (label.length < 5) " $label" else label
        c.save()
        c.translate(0f, Theme.TIME_Y.toFloat())
        val ts = M.mix(1.0, Theme.TIME_BIG / Theme.TIME_SIZE, min(look.turn, 1.08)).toFloat()
        c.scale(ts, ts)
        val timeY = (0.364 * Theme.TIME_SIZE).toFloat()
        for (i in 0 until 5) {
            val ch = padded[i]
            if (ch == ' ') continue
            val wch = text.measureText(ch.toString())
            c.drawText(ch.toString(), cells[i] + (cellW[i] - wch) / 2, timeY, text)
        }
        c.restore()
        // the row: cast, the avatar, the waveform
        c.save()
        c.translate(0f, Theme.ROW_Y.toFloat())
        stroke.shader = null
        stroke.color = a(Theme.glyph, 0.82)
        stroke.strokeWidth = 1.2f
        c.save()
        c.translate(-Theme.ROW_DX.toFloat(), 0f)
        c.drawPath(castArcs, stroke)
        fill.shader = null
        fill.color = a(Theme.glyph, 0.82)
        c.drawPath(castFoot, fill)
        c.restore()
        fill.color = Theme.avatar
        c.drawCircle(0f, 0f, (Theme.ROW_AVATAR / 2).toFloat(), fill)
        font(fSemi, Theme.ROW_AVATAR_INK, Theme.avatarInk)
        c.drawText("N", -text.measureText("N") / 2, (0.364 * Theme.ROW_AVATAR_INK).toFloat(), text)
        c.save()
        c.translate(Theme.ROW_DX.toFloat(), 0f)
        stroke.color = a(Theme.glyph, 0.82)
        c.drawPath(wave, stroke)
        c.restore()
        c.restore()
        // the covers
        for ((i, tr) in pr.queue.withIndex()) trackCover(c, i, tr.cover ?: i, look, e)
        // the amber line across the cover (static, as in the shot) and the centre control
        val dimCover = 1 - 0.2 * M.clamp01(look.turn)
        val layer = c.saveLayerAlpha(-60f, -30f, 60f, 30f, (255 * dimCover).toInt())
        val edge = (Theme.COVER_SIZE / 2).toFloat()
        fill.shader = LinearGradient(-edge, 0f, edge, 0f, intArrayOf(a(Theme.amber, 0.0), Theme.amber, Theme.amber, a(Theme.amber, 0.0)), floatArrayOf(0f, 0.18f, 0.82f, 1f), Shader.TileMode.CLAMP)
        rect.set(-edge, -0.3f, edge, 0.3f)
        c.drawRoundRect(rect, 0.3f, 0.3f, fill)
        fill.shader = null
        c.save()
        c.scale(look.press.toFloat(), look.press.toFloat())
        val gp = glyphPath(look.glyph)
        stroke.color = a(Theme.glyph, 0.9)
        stroke.strokeWidth = 1.4f
        c.drawPath(gp, stroke)
        fill.shader = LinearGradient(0f, -8f, 0f, 8f, Color.rgb(0xF4, 0xF2, 0xFB), Theme.glyphLo, Shader.TileMode.CLAMP)
        c.drawPath(gp, fill)
        fill.shader = null
        c.restore()
        c.restoreToCount(layer)
        // previous and next
        for (d in intArrayOf(-1, 1)) {
            c.save()
            c.translate((d * Theme.DISC_DX).toFloat(), 0f)
            fill.color = a(Theme.discFill, 0.12)
            c.drawCircle(0f, 0f, (Theme.DISC_SIZE / 2).toFloat(), fill)
            stroke.color = a(Theme.discFill, 0.32)
            stroke.strokeWidth = 0.6f
            c.drawCircle(0f, 0f, (Theme.DISC_SIZE / 2 - 0.3).toFloat(), stroke)
            c.translate(d * 0.8f, 0f)
            fill.color = a(Theme.glyph, 0.88)
            c.drawPath(if (d < 0) prevChevron else nextChevron, fill)
            c.restore()
        }
        // the titles and artists, sliding with the covers
        for ((i, tr) in pr.queue.withIndex()) {
            val dist = (i - e.indexL) * M.SLIDE_SPAN + e.slideL.value
            val op = M.clamp01(1 - abs(dist) / (M.SLIDE_SPAN * 0.7))
            if (op <= 0.002) continue
            c.save()
            c.translate((dist * 0.6).toFloat(), 0f)
            centred(c, tr.title, fSemi, Theme.TRACK_SIZE, 0.0, pr.ink, op, 0f, (Theme.TRACK_Y + 0.364 * Theme.TRACK_SIZE).toFloat())
            centred(c, (tr.subtitle ?: "").uppercase(Locale.US), fMed, Theme.ARTIST_SIZE, Theme.ARTIST_TRACK, Theme.displayMute, op, 0f, (Theme.ARTIST_Y + 0.364 * Theme.ARTIST_SIZE).toFloat())
            c.restore()
        }
    }

    // ---- the page's three lines and the pill (design pt on the 402 x 874 canvas) ----

    private fun pageText(c: Canvas, look: WovenSpeakerPlayerLook, e: WovenSpeakerPlayerEngine, pr: Params) {
        val titleY = (Theme.TITLE_Y + 0.364 * Theme.TITLE_SIZE).toFloat()
        val lineY = (Theme.LINE_Y + 0.364 * Theme.LINE_SIZE).toFloat()
        val pillY = (Theme.PILL_Y + Theme.PILL_H / 2 + 0.364 * Theme.PILL_SIZE).toFloat()
        // the headline and the current track's title cross-fade with a 4 pt rise
        c.save()
        c.translate(0f, (-4 * look.title).toFloat())
        centred(c, Theme.HEADLINE, fBold, Theme.TITLE_SIZE, -0.021, pr.ink, 1 - M.smooth(0.0, 0.5, look.title), 201f, titleY)
        c.restore()
        c.save()
        c.translate(201f, Theme.TITLE_Y.toFloat())
        c.scale((1 + 0.006 * min(look.pulse, 1.0)).toFloat(), 1f)
        c.translate(-201f, -Theme.TITLE_Y.toFloat())
        c.translate(0f, (4 * (1 - look.title)).toFloat())
        val fade = M.smooth(0.5, 1.0, look.title)
        val mixA = Theme.WASH_TITLE * M.clamp01(look.wash / Theme.WASH_BASE) * (if (pr.ambient) 1 else 0)
        val inkB = Color.rgb(
            Math.round(Color.red(pr.ink) + (Color.red(pr.accent) - Color.red(pr.ink)) * mixA).toInt(),
            Math.round(Color.green(pr.ink) + (Color.green(pr.accent) - Color.green(pr.ink)) * mixA).toInt(),
            Math.round(Color.blue(pr.ink) + (Color.blue(pr.accent) - Color.blue(pr.ink)) * mixA).toInt(),
        )
        for ((i, tr) in pr.queue.withIndex()) {
            val dist = (i - e.indexL) * M.SLIDE_SPAN * 2 + e.slideL.value
            val op = M.clamp01(1 - abs(dist) / (M.SLIDE_SPAN * 1.6)) * fade
            if (op <= 0.002) continue
            c.save()
            c.translate(dist.toFloat(), 0f)
            font(fBold, Theme.TITLE_SIZE, pr.ink)
            text.shader = LinearGradient(0f, (Theme.TITLE_Y - 14).toFloat(), 0f, (Theme.TITLE_Y + 22).toFloat(), pr.ink, inkB, Shader.TileMode.CLAMP)
            text.alpha = (op * 255).toInt()
            val wTitle = runWidth(tr.title, Theme.TITLE_SIZE, -0.021)
            run(c, tr.title, 201f - wTitle / 2, titleY, Theme.TITLE_SIZE, -0.021)
            text.shader = null
            c.restore()
        }
        c.restore()
        // the line under it
        c.save()
        c.translate(0f, (-4 * look.line).toFloat())
        centred(c, Theme.SUBTITLE, fReg, Theme.LINE_SIZE, -0.013, pr.mute, 1 - M.smooth(0.0, 0.5, look.line), 201f, lineY)
        c.restore()
        c.save()
        c.translate(0f, (4 * (1 - look.line)).toFloat())
        centred(c, Theme.SUBTITLE_PLAYING, fReg, Theme.LINE_SIZE, -0.013, pr.mute, M.smooth(0.5, 1.0, look.line), 201f, lineY)
        c.restore()
        // the pill
        val pc = (Theme.PILL_Y + Theme.PILL_H / 2).toFloat()
        val ps = e.pillPress.value.toFloat()
        c.save()
        c.scale(ps, ps, 201f, pc)
        stroke.shader = null
        stroke.color = Theme.pill
        stroke.strokeWidth = Theme.PILL_STROKE.toFloat()
        rect.set((201 - Theme.PILL_W / 2).toFloat(), Theme.PILL_Y.toFloat(), (201 + Theme.PILL_W / 2).toFloat(), (Theme.PILL_Y + Theme.PILL_H).toFloat())
        c.drawRoundRect(rect, (Theme.PILL_H / 2).toFloat(), (Theme.PILL_H / 2).toFloat(), stroke)
        c.save()
        c.translate(0f, (-4 * look.pill).toFloat())
        centred(c, Theme.PILL_TEXT, fMed, Theme.PILL_SIZE, -0.006, Theme.pill, 1 - M.smooth(0.0, 0.5, look.pill), 201f, pillY)
        c.restore()
        c.save()
        c.translate(0f, (4 * (1 - look.pill)).toFloat())
        centred(c, Theme.PILL_PLAYING, fMed, Theme.PILL_SIZE, -0.006, Theme.pill, M.smooth(0.5, 1.0, look.pill), 201f, pillY)
        c.restore()
        c.restore()
    }
}
