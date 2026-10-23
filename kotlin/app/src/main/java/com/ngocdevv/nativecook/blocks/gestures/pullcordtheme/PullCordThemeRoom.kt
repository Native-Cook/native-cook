package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import android.os.Build
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeMotion.Hero
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Cord
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.Liquid
import com.ngocdevv.nativecook.blocks.gestures.pullcordtheme.PullCordThemeTheme.ReplicaArt as A
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * One theme's room, built on the accepted replica: the generated wall, the bulb sprites, the stile and rail
 * light, then the upgrade's additions (warm light rising in the glass, the pull cord). Drawn twice; the
 * light one is masked by the reveal in [PullCordThemeCanvas]. Port of CordScene.tsx's RoomScene.
 * Draws on an android.graphics.Canvas scaled to dp, like the Skia canvas of the React Native block.
 */
internal class PullCordThemeRoom(private val g: PullCordThemeGeometry) {
    private val w = g.w.toFloat()
    private val h = g.h.toFloat()
    private val s = g.s.toFloat()
    private val px = s / 3f

    private val sw = (A.spriteW * A.spriteScale * g.s).toFloat()
    private val sh = (A.spriteH * A.spriteScale * g.s).toFloat()
    private val sx = (-A.anchorX * A.spriteScale * g.s).toFloat()
    private val sy = (-A.anchorY * A.spriteScale * g.s).toFloat()

    /** The glass starts under the socket. */
    private val glassTop = ((A.anchorY + 60) * A.spriteScale * g.s).toFloat()
    private val glassBottom = (705 * A.spriteScale * g.s - A.anchorY * A.spriteScale * g.s).toFloat()
    private val glassMid = (glassTop + glassBottom) / 2f

    private val paint = Paint()
    private val matrix = Matrix()
    private val rect = RectF()
    private val path = Path()
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val multiply = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    private val plus = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val dstIn = PorterDuffXfermode(PorterDuff.Mode.DST_IN)

    // Hardware canvases only honour mask filters from API 28; below it the soft edges are left hard.
    private val softEdges = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    private fun blur(sigma: Double) = BlurMaskFilter(sigmaToRadius(sigma.toFloat()), BlurMaskFilter.Blur.NORMAL)
    private val stileBlur = blur(3.0)
    private val motesBlur = blur(1.2)
    private val specularBlur = blur(5.0)
    private val beadBlur = blur(0.6)
    private val edgeBlur = blur(Hero.edge / 2)

    private val interiorFilter = skiaMatrixFilter(A.interior)
    private val wallTintFilter = skiaMatrixFilter(A.wallTint)
    private val warmColorMatrix = ColorMatrix()

    // Gradients centred on the origin; a local matrix moves them to the glass centre each frame.
    private val ambientShader = radial((w * A.ambientR).toFloat(), A.ambient.map { it.toArgb() }.toIntArray(), null)
    private val spillShader = radial(
        (w * A.spillR).toFloat(), A.spillFall.map { it.toArgb() }.toIntArray(), A.spillStops,
    )
    private val haloShader = radial(
        (A.halo * g.s).toFloat(), intArrayOf(A.haloColor.toArgb(), Color(31, 125, 255, 0).toArgb()), null,
    )
    private val warmHaloShader = radial(
        (A.halo * 1.4 * g.s).toFloat(), intArrayOf(Liquid.spill.toArgb(), Liquid.spillFar.toArgb()), null,
    )
    private val liquidShader = LinearGradient(
        0f, glassTop, 0f, glassBottom, Liquid.top.toArgb(), Liquid.bottom.toArgb(), Shader.TileMode.CLAMP,
    )
    private val stileColors = intArrayOf(Color(36, 104, 210, 0).toArgb(), A.stileColor.toArgb(), Color(36, 104, 210, 0).toArgb())

    // The pull's gradients depend on the palette only: built once per theme.
    private val darkPull = PullShaders(PullCordThemeTheme.DARK)
    private val lightPull = PullShaders(PullCordThemeTheme.LIGHT)

    private class PullShaders(palette: PullCordThemePalette) {
        val crimp = horizontal(Cord.crimpWidth.toFloat(), palette.metal, palette.metalStops)
        val bead = horizontal(Cord.beadWidth.toFloat(), palette.brass, palette.brassStops)

        private fun horizontal(width: Float, colors: List<Color>, stops: FloatArray) = LinearGradient(
            -width / 2, 0f, width / 2, 0f, colors.map { it.toArgb() }.toIntArray(), stops, Shader.TileMode.CLAMP,
        )
    }

    private fun radial(radius: Float, colors: IntArray, stops: FloatArray?) =
        RadialGradient(0f, 0f, radius, colors, stops, Shader.TileMode.CLAMP)

    /** Draws the room for [light] or dark from the frame's published values. */
    fun draw(canvas: Canvas, light: Boolean, f: PullCordThemeFrame, assets: PullCordThemeAssets?) {
        val palette = if (light) PullCordThemeTheme.LIGHT else PullCordThemeTheme.DARK
        val cx = f.cx.toFloat()
        val cy = f.cy.toFloat()
        val level = f.level.toFloat()
        val side = f.side.toFloat()
        val clock = f.clock

        // Neon hum in the dark room: 0.92-1 over 3 s (a divisor of the loop, so the loop stays seamless).
        val hum = Hero.humLow + (1 - Hero.humLow) * (0.5 + 0.5 * sin((2 * PI * clock) / Hero.humPeriod))
        val on = (if (light) 0.35f + 0.65f * level else (f.neon * hum * (1 - 0.7 * level)).toFloat()).coerceIn(0f, 1f)
        // Light room: the tungsten warms up and flickers right after the commit.
        val fresh = if (f.themeNow == 1) f.commit else 99.0
        val warmFlicker = flickerAt(fresh)
        val warmGlow = if (light) 0.5f + 0.5f * level else 0.8f * level
        val leftLit = on * (0.65f - 0.35f * side)
        val rightLit = on * (0.65f + 0.35f * side)
        val levelY = glassBottom - level * (glassBottom - glassTop)
        // dstIn only masks where the sprite draws: keep the liquid inside the sprite's bounds.
        val liquidH = max(0f, sy + sh - levelY)

        // Wall.
        paint.reset()
        paint.color = (if (light) PullCordThemeTheme.LIGHT.wallTop else Color(0xFF0D0E10)).toArgb()
        canvas.drawRect(0f, 0f, w, h, paint)
        val wallImage = if (light) assets?.wallLight else assets?.wall
        if (wallImage != null) {
            rect.set((A.wallX * g.s).toFloat(), 0f, ((A.wallX + A.wallW) * g.s).toFloat(), h)
            drawBitmap(canvas, wallImage, rect, 1f, null, if (light) null else wallTintFilter)
        }

        // The bulb's light on the wall: blue in the dark room (the replica), warm in the light one.
        if (!light) {
            fillRect(canvas, 0f, 0f, w, h, on, screen, ambientShader, cx, cy)
            fillRect(canvas, 0f, 0f, w, h, on, screen, spillShader, cx, cy)
            val haloRadius = (A.halo * g.s).toFloat()
            // A circle filled with a gradient that is transparent at its rim: the same as a rect of the same bounds.
            prepare(on, screen, haloShader, cx, cy)
            canvas.drawCircle(cx, cy, haloRadius, paint)
            paint.reset()
            paint.color = A.railColor.toArgb()
            paint.alpha = alphaOf(on * A.railColor.alpha)
            paint.xfermode = screen
            canvas.drawRect(0f, (A.railY[0] * px).toFloat(), w, (A.railY[1] * px).toFloat(), paint)
            drawStile(canvas, A.stileLeft[0] * px, A.stileLeft[1] * px, leftLit)
            drawStile(canvas, A.stileRight[0] * px, A.stileRight[1] * px, rightLit)
        }
        prepare(warmGlow, null, warmHaloShader, cx, cy)
        canvas.drawCircle(cx, cy, (A.halo * 1.4 * g.s).toFloat(), paint)

        // The bulb, about its pivot; origin at the socket's top centre.
        canvas.save()
        canvas.translate(g.pivotX.toFloat(), g.pivotY.toFloat())
        canvas.rotate((-f.angle * 180.0 / PI).toFloat())
        canvas.translate(0f, (g.socketTop - g.pivotY).toFloat())
        paint.reset()
        paint.color = (if (light) PullCordThemeTheme.LIGHT.wire else A.wireColor).toArgb()
        canvas.drawRect(
            (-A.wireWidth / 2).toFloat(), (g.pivotY - g.socketTop).toFloat(),
            (A.wireWidth / 2).toFloat(), 4f, paint,
        )
        if (light) {
            if (assets != null) {
                // Its soft cast shadow on the light wall moves with the swing.
                val shadow = assets.shadow
                rect.set(0f, 0f, shadow.widthDp, shadow.heightDp)
                rect.offset(sx - shadow.padDp + (Hero.shadowDx * g.s).toFloat(), sy - shadow.padDp + (Hero.shadowDy * g.s).toFloat())
                drawBitmap(canvas, shadow.bitmap, rect, Hero.shadowAlpha.toFloat(), null, null)
                rect.set(sx, sy, sx + sw, sy + sh)
                // Identity once the ember has warmed up; skipping the filter then draws the same pixels.
                val ember = emberAt(fresh)
                val tint = if (ember >= 1f) null else {
                    warmColorMatrix.setScale(1f, 0.5f + 0.5f * ember, 0.15f + 0.85f * ember, 1f)
                    ColorMatrixColorFilter(warmColorMatrix)
                }
                drawBitmap(canvas, assets.warm, rect, warmFlicker, null, tint)
            }
        } else if (assets != null) {
            // The replica's sprites are on black: a screen blend keeps only their light.
            rect.set(sx, sy, sx + sw, sy + sh)
            drawBitmap(canvas, assets.dim, rect, 1f - on, screen, null)
            drawBitmap(canvas, assets.lit, rect, on, screen, interiorFilter)
            val bloom = assets.bloom
            rect.set(0f, 0f, bloom.widthDp, bloom.heightDp)
            rect.offset(sx - bloom.padDp, sy - bloom.padDp)
            drawBitmap(canvas, bloom.bitmap, rect, on * A.bloomOpacity.toFloat(), screen, null)
        }
        // Warm light rising inside the glass like a liquid, shaped by the warm sprite's own alpha.
        if (assets != null && liquidH > 0f) {
            rect.set(sx, sy, sx + sw, sy + sh)
            canvas.saveLayerAlpha(rect, alphaOf(if (light) Liquid.lightAlpha else 1f))
            paint.reset()
            paint.isAntiAlias = true
            paint.shader = liquidShader
            canvas.drawRect(sx, levelY, sx + sw, levelY + liquidH, paint)
            drawBitmap(canvas, assets.warm, rect, 1f, dstIn, null)
            canvas.restore()
        }
        // The glass highlight slides across as the bulb swings.
        canvas.save()
        canvas.translate(0f, glassMid)
        val specX = (-Hero.spec * g.s * side - 42 * g.s).toFloat()
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color(255, 255, 255, 255).toArgb()
        paint.alpha = alphaOf(0.32f)
        paint.xfermode = screen
        if (softEdges) paint.maskFilter = specularBlur
        canvas.drawRoundRect(specX, -40 * s, specX + 12 * s, -40 * s + 52 * s, 6 * s, 6 * s, paint)
        canvas.restore()
        canvas.restore()

        // Dust motes drifting in the warm cone under the bulb.
        if (light) drawMotes(canvas, cx, cy, clock, 0.55f * (0.4f + 0.6f * level))

        if (!light && assets != null) {
            // The source's baked light field around the bulb (as in the replica), moving with it.
            canvas.save()
            canvas.translate(cx - w, 0f)
            rect.set(0f, 0f, w * 2, h)
            drawBitmap(canvas, assets.lightMul, rect, on, multiply, null)
            drawBitmap(canvas, assets.lightAdd, rect, on * 0.25f, plus, null)
            canvas.restore()
        }

        drawCord(canvas, f, palette)
    }

    /** The pull cord and its brass pull. */
    private fun drawCord(canvas: Canvas, f: PullCordThemeFrame, palette: PullCordThemePalette) {
        val last = Cord.points - 1
        val p = f.rope
        path.rewind()
        path.moveTo(p[0].toFloat(), p[1].toFloat())
        for (i in 1 until Cord.points - 1) {
            val midX = (p[i * 2] + p[i * 2 + 2]) / 2
            val midY = (p[i * 2 + 1] + p[i * 2 + 3]) / 2
            path.quadTo(p[i * 2].toFloat(), p[i * 2 + 1].toFloat(), midX.toFloat(), midY.toFloat())
        }
        path.lineTo(p[last * 2].toFloat(), p[last * 2 + 1].toFloat())
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (Cord.width * g.s).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = palette.cord.toArgb()
        canvas.drawPath(path, paint)

        val dx = p[last * 2] - p[last * 2 - 2]
        val dy = p[last * 2 + 1] - p[last * 2 - 1]
        canvas.save()
        canvas.translate(p[last * 2].toFloat(), p[last * 2 + 1].toFloat())
        canvas.rotate((atan2(-dx, dy) * 180.0 / PI).toFloat())
        canvas.scale(s, s)
        val crimpW = Cord.crimpWidth.toFloat()
        val beadW = Cord.beadWidth.toFloat()
        val beadH = Cord.beadHeight.toFloat()
        val crimpH = Cord.crimpHeight.toFloat()
        paint.reset()
        paint.isAntiAlias = true
        val shaders = if (palette === PullCordThemeTheme.LIGHT) lightPull else darkPull
        paint.shader = shaders.crimp
        canvas.drawRoundRect(-crimpW / 2, -1f, crimpW / 2, -1f + crimpH, 1.5f, 1.5f, paint)
        paint.shader = shaders.bead
        canvas.drawRoundRect(
            -beadW / 2, crimpH - 1, beadW / 2, crimpH - 1 + beadH, Cord.beadRadius.toFloat(), Cord.beadRadius.toFloat(), paint,
        )
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1f
        paint.color = palette.cordLit.toArgb()
        if (softEdges) paint.maskFilter = beadBlur
        canvas.drawRoundRect(
            -beadW / 2, crimpH - 1, beadW / 2, crimpH - 1 + beadH, Cord.beadRadius.toFloat(), Cord.beadRadius.toFloat(), paint,
        )
        canvas.restore()
    }

    /** A door stile lit by the bulb: a vertical gradient, softened. */
    private fun drawStile(canvas: Canvas, x0: Double, x1: Double, opacity: Float) {
        val top = (A.stileGlow[0] * px).toFloat()
        val bottom = (A.stileGlow[2] * px).toFloat()
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = LinearGradient(0f, top, 0f, bottom, stileColors, null, Shader.TileMode.CLAMP)
        paint.alpha = alphaOf(opacity)
        paint.xfermode = screen
        if (softEdges) paint.maskFilter = stileBlur
        canvas.drawRect(x0.toFloat(), top, x1.toFloat(), bottom, paint)
    }

    private fun drawMotes(canvas: Canvas, cx: Float, cy: Float, clock: Double, opacity: Float) {
        path.rewind()
        for (i in 0 until Hero.motes) {
            val m = MOTES[i]
            val y = (m[1] + clock * 0.02 * m[2]) % 1.0
            val spread = 40 + y * 140
            val x = cx + (m[0] - 0.5) * 2 * spread * s + sin(clock * m[2] + m[3]) * 8 * s
            path.addCircle(x.toFloat(), (cy + (40 + y * 380) * s).toFloat(), (m[4] * s).toFloat(), Path.Direction.CW)
        }
        paint.reset()
        paint.isAntiAlias = true
        paint.color = Color(0xFFFFF3D6).toArgb()
        paint.alpha = alphaOf(opacity)
        paint.xfermode = screen
        if (softEdges) paint.maskFilter = motesBlur
        canvas.drawPath(path, paint)
    }

    /** The reveal's warm soft edge: a stroked circle, strongest mid-way. */
    fun drawEdge(canvas: Canvas, cx: Float, cy: Float, radius: Float, opacity: Float) {
        val edge = rgba(255, 200, 130, 0.9f)
        paint.reset()
        paint.isAntiAlias = true
        paint.style = Paint.Style.STROKE
        paint.color = edge.toArgb()
        if (softEdges) {
            paint.strokeWidth = Hero.edge.toFloat()
            paint.alpha = alphaOf(edge.alpha * opacity)
            paint.maskFilter = edgeBlur
            canvas.drawCircle(cx, cy, radius, paint)
            return
        }
        // Below API 28 the blur is layered: narrower strokes stacked at a fifth of the strength.
        for (fraction in floatArrayOf(1f, 0.8f, 0.6f, 0.4f, 0.2f)) {
            paint.strokeWidth = Hero.edge.toFloat() * fraction
            paint.alpha = alphaOf(edge.alpha * opacity * 0.2f)
            canvas.drawCircle(cx, cy, radius, paint)
        }
    }

    private fun drawBitmap(
        canvas: Canvas,
        bitmap: Bitmap,
        target: RectF,
        alpha: Float,
        blend: PorterDuffXfermode?,
        filter: ColorMatrixColorFilter?,
    ) {
        paint.reset()
        paint.isAntiAlias = true
        paint.isFilterBitmap = true
        paint.alpha = alphaOf(alpha)
        paint.xfermode = blend
        paint.colorFilter = filter
        canvas.drawBitmap(bitmap, null, target, paint)
    }

    /** Fills a rect with a gradient centred on the origin, moved to ([cx], [cy]). */
    private fun fillRect(
        canvas: Canvas,
        left: Float,
        top: Float,
        right: Float,
        bottom: Float,
        opacity: Float,
        blend: PorterDuffXfermode?,
        shader: RadialGradient,
        cx: Float,
        cy: Float,
    ) {
        prepare(opacity, blend, shader, cx, cy)
        canvas.drawRect(left, top, right, bottom, paint)
    }

    private fun prepare(opacity: Float, blend: PorterDuffXfermode?, shader: RadialGradient, cx: Float, cy: Float) {
        matrix.setTranslate(cx, cy)
        shader.setLocalMatrix(matrix)
        paint.reset()
        paint.isAntiAlias = true
        paint.shader = shader
        paint.alpha = alphaOf(opacity)
        paint.xfermode = blend
    }

    private fun alphaOf(opacity: Float) = (opacity.coerceIn(0f, 1f) * 255f).roundToInt()

    /** Tungsten warming from ember to warm white after the commit. */
    private fun emberAt(t: Double): Float = min(1.0, max(0.0, t / Hero.ember)).toFloat()

    /** Two quick real-bulb dips right after the commit. */
    private fun flickerAt(t: Double): Float {
        val dips = Hero.flickers
        for (i in dips.indices step 2) {
            if (t >= dips[i] && t < dips[i + 1]) return (1 - Hero.flickerDepth).toFloat()
        }
        return 1f
    }

    /** Skia's matrix has offsets in 0..1, Android's in 0..255. */
    private fun skiaMatrixFilter(values: FloatArray): ColorMatrixColorFilter {
        val scaled = values.copyOf()
        for (row in 0 until 4) scaled[row * 5 + 4] = values[row * 5 + 4] * 255f
        return ColorMatrixColorFilter(ColorMatrix(scaled))
    }

    private companion object {
        /** Seeded dust motes for the warm light cone: [x 0..1, y 0..1, speed, phase, size]. */
        val MOTES: List<DoubleArray> = List(Hero.motes) { i ->
            fun seeded(k: Int): Double {
                val v = sin(i * 127.1 + k * 311.7) * 43758.5453
                return v - floor(v)
            }
            doubleArrayOf(seeded(1), seeded(2), 0.4 + seeded(3) * 0.8, seeded(4) * PI * 2, 0.8 + seeded(5) * 1.6)
        }
    }
}
