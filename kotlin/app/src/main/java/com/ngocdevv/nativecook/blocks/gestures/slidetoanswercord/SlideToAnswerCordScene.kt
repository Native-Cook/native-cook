package com.ngocdevv.nativecook.blocks.gestures.slidetoanswercord

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Canvas as AndroidCanvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.Shader
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** Native-Canvas painter. The simulation and this painter both use dp; its caller applies density once. */
internal class SlideToAnswerCordScene(
    private val assets: SlideToAnswerCordAssets,
) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val backCoil = Path()
    private val frontCoil = Path()
    private var grain: Bitmap? = null
    private var grainW = -1
    private var grainH = -1
    private var backdrop: Bitmap? = null
    private var backdropW = -1
    private var backdropH = -1
    private var blurSource: Bitmap? = null
    private var blurredBackdrop: Bitmap? = null
    private var blurInput = IntArray(0)
    private var blurWork = IntArray(0)

    fun draw(
        canvas: AndroidCanvas,
        sim: SlideToAnswerCordSimulation.Sim,
        geometry: SlideToAnswerCordSimulation.Geometry,
        buttonsY: Double,
        declineScale: Float,
        acceptScale: Float,
        pixelScale: Float = 1f,
    ) {
        val w = geometry.w.toFloat()
        val h = geometry.h.toFloat()
        val ring = SlideToAnswerCordSimulation.ringState(sim)
        val handset = SlideToAnswerCordSimulation.handsetPoint(sim, geometry, Tokens.HANDSET.comX, Tokens.HANDSET.comY)
        val conn = sim.wConn
        val off = sim.wOff
        val face = gammaMix(Tokens.COLORS.ring, Tokens.COLORS.connected, conn).let {
            gammaMix(it, Tokens.COLORS.off, off)
        }
        val ground = gammaMix(
            gammaMix(Tokens.COLORS.ground, Tokens.COLORS.groundConnected, conn),
            Tokens.COLORS.groundEnded,
            off,
        )

        val renderScale = pixelScale.coerceAtLeast(.1f)
        val physicalWidth = ceil(w * renderScale).toInt().coerceAtLeast(1)
        val physicalHeight = ceil(h * renderScale).toInt().coerceAtLeast(1)
        val logicalRect = RectF(0f, 0f, w, h)
        val background = ensureBackdrop(physicalWidth, physicalHeight)
        background.eraseColor(Tokens.COLORS.skyTop.toArgb())
        val backdropCanvas = AndroidCanvas(background)
        backdropCanvas.scale(renderScale, renderScale)
        drawSky(backdropCanvas, w, h, ground)
        if (!Tokens.REPLICA) {
            drawField(
                backdropCanvas,
                w,
                h,
                handset.x.toFloat(),
                handset.y.toFloat(),
                (Tokens.GLASS.field * geometry.s).toFloat(),
                face,
                Tokens.GLASS.fieldAlpha * (1.0 - sim.look),
            )
            drawWash(backdropCanvas, sim, handset, geometry, face)
            drawChevrons(backdropCanvas, sim, handset, geometry)
        }
        // RN applies screen-blended FractalNoise after the atmospheric layers.
        drawGrain(backdropCanvas, w, h)
        drawPulse(backdropCanvas, ring, handset, geometry, face)
        buildCoil(sim, geometry)
        backdropCanvas.save()
        backdropCanvas.translate((Tokens.CORD.replicaRimDx * geometry.s).toFloat(), 0f)
        stroke(
            backdropCanvas,
            frontCoil,
            (Tokens.CORD.replicaWidth - 1.0) * geometry.s,
            Tokens.COLORS.replicaRim,
        )
        backdropCanvas.restore()
        backdropCanvas.save()
        backdropCanvas.translate((-Tokens.CORD.replicaRimDx * 0.8 * geometry.s).toFloat(), 0f)
        stroke(
            backdropCanvas,
            frontCoil,
            (Tokens.CORD.replicaWidth - 1.0) * geometry.s,
            Tokens.COLORS.replicaRimLeft,
        )
        backdropCanvas.restore()
        stroke(
            backdropCanvas,
            frontCoil,
            Tokens.CORD.replicaWidth * geometry.s,
            Tokens.COLORS.replicaCord,
        )
        drawHandset(backdropCanvas, sim, geometry, ring.buzz)
        if (!Tokens.REPLICA) {
            // Freeze the fully painted scene before adding controls. Both glass surfaces sample this
            // same immutable blurred frame, then the controls and their mask-filter halos stay on the
            // software canvas so API 26 hardware rendering cannot discard them.
            val blurredBackground = blurBackdrop(
                source = background,
                logicalWidth = ceil(w.toDouble()).toInt().coerceAtLeast(1),
                logicalHeight = ceil(h.toDouble()).toInt().coerceAtLeast(1),
                radius = Tokens.GLASS.blur.roundToInt(),
            )
            drawControls(
                backdropCanvas,
                sim,
                geometry,
                buttonsY,
                declineScale,
                acceptScale,
                blurredBackground,
                logicalRect,
            )
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = null
        paint.maskFilter = null
        paint.xfermode = null
        paint.isFilterBitmap = true
        canvas.drawBitmap(background, null, logicalRect, paint)
    }

    private fun ensureBackdrop(width: Int, height: Int): Bitmap {
        if (backdrop == null || backdropW != width || backdropH != height) {
            backdropW = width
            backdropH = height
            backdrop = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        }
        return backdrop!!
    }

    private fun drawSky(canvas: AndroidCanvas, w: Float, h: Float, ground: Color) {
        val c = Tokens.COLORS
        val values = listOf(c.sky[0], c.sky[1], c.sky[2], c.sky[3], ground, c.sky[5])
        paint.shader = LinearGradient(0f, 0f, 0f, h, values.map(Color::toArgb).toIntArray(), c.skyStops.map { it.toFloat() }.toFloatArray(), Shader.TileMode.CLAMP)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.shader = null
    }

    /** Cached seed-zero two-octave value noise; Android has no public Skia FractalNoise node. */
    private fun drawGrain(canvas: AndroidCanvas, w: Float, h: Float) {
        val bw = ceil(w.toDouble()).toInt().coerceAtLeast(1)
        val bh = ceil(h.toDouble()).toInt().coerceAtLeast(1)
        if (grain == null || grainW != bw || grainH != bh) {
            grainW = bw
            grainH = bh
            val pixels = IntArray(bw * bh)
            val base = (Tokens.COLORS.grain * 255).roundToInt().coerceIn(0, 255)
            for (y in 0 until bh) for (x in 0 until bw) {
                // freqX/freqY are 0.9, octaves=2, matching CallScene's FractalNoise parameters.
                val n = fbm(x * .9f, y * .9f)
                pixels[y * bw + x] = android.graphics.Color.argb((base * (.35f + n * .65f)).roundToInt(), 255, 255, 255)
            }
            grain = Bitmap.createBitmap(pixels, bw, bh, Bitmap.Config.ARGB_8888)
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
        canvas.drawBitmap(grain ?: return, 0f, 0f, paint)
        paint.xfermode = null
        paint.alpha = 255
    }

    private fun drawField(
        canvas: AndroidCanvas,
        w: Float,
        h: Float,
        x: Float,
        y: Float,
        radius: Float,
        color: Color,
        opacity: Double,
    ) {
        if (opacity <= 0.0) return
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = RadialGradient(
            x,
            y,
            radius,
            intArrayOf(color.toArgb(), android.graphics.Color.TRANSPARENT),
            floatArrayOf(0f, 1f),
            Shader.TileMode.CLAMP,
        )
        paint.alpha = (opacity * 255).roundToInt()
        canvas.drawRect(0f, 0f, w, h, paint)
        paint.alpha = 255
        paint.shader = null
    }

    private fun drawWash(canvas: AndroidCanvas, sim: SlideToAnswerCordSimulation.Sim, point: SlideToAnswerCordSimulation.Point, g: SlideToAnswerCordSimulation.Geometry, color: Color) {
        if (sim.reduced || sim.phase == SlideToAnswerCordSimulation.PHASE.ringing || sim.phaseT >= Tokens.WASH.duration) return
        val u = (sim.phaseT / Tokens.WASH.duration).coerceIn(0.0, 1.0)
        val radius = (u * hypot(g.w, g.h)).toFloat().coerceAtLeast(1f)
        val clear = android.graphics.Color.TRANSPARENT
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = RadialGradient(point.x.toFloat(), point.y.toFloat(), radius, intArrayOf(clear, color.copy(alpha = (.5 * (1 - u)).toFloat()).toArgb(), clear), floatArrayOf(0f, .85f, 1f), Shader.TileMode.CLAMP)
        canvas.drawCircle(point.x.toFloat(), point.y.toFloat(), radius, paint)
        paint.shader = null
    }

    private fun drawChevrons(canvas: AndroidCanvas, sim: SlideToAnswerCordSimulation.Sim, point: SlideToAnswerCordSimulation.Point, g: SlideToAnswerCordSimulation.Geometry) {
        if (sim.phase != SlideToAnswerCordSimulation.PHASE.ringing || sim.grab || sim.reduced) return
        val phase = (sim.clock % Tokens.CHEVRON.period) / Tokens.CHEVRON.period
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (2.2 * g.s).toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (0.7 * (1.0 - sim.look) * 255).roundToInt()
        for (i in 0 until 3) {
            val u = (phase + i / 3.0) % 1.0
            val y = point.y - (Tokens.HANDSET.comY + 26 + u * Tokens.CHEVRON.rise * 2) * g.s
            val x = point.x
            path.reset()
            path.moveTo((x - 7 * g.s).toFloat(), (y + 4 * g.s).toFloat())
            path.lineTo(x.toFloat(), (y - 2 * g.s).toFloat())
            path.lineTo((x + 7 * g.s).toFloat(), (y + 4 * g.s).toFloat())
            canvas.drawPath(path, paint)
        }
        paint.alpha = 255
    }

    private fun drawPulse(canvas: AndroidCanvas, ring: SlideToAnswerCordSimulation.RingState, p: SlideToAnswerCordSimulation.Point, g: SlideToAnswerCordSimulation.Geometry, color: Color) {
        if (ring.pulse < 0.0) return
        val u = 1 - (1 - ring.pulse).pow(2)
        val radius = (Tokens.RING.pulseFrom + (Tokens.RING.pulseTo - Tokens.RING.pulseFrom) * u) * g.s
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = (2 * g.s).toFloat()
        paint.color = color.toArgb()
        paint.alpha = (0.55 * (1 - ring.pulse) * 255).roundToInt()
        paint.maskFilter = BlurMaskFilter(3f, BlurMaskFilter.Blur.SOLID)
        canvas.drawCircle(p.x.toFloat(), p.y.toFloat(), radius.toFloat(), paint)
        paint.maskFilter = null
        paint.alpha = 255
    }

    private fun drawHandset(
        canvas: AndroidCanvas,
        sim: SlideToAnswerCordSimulation.Sim,
        g: SlideToAnswerCordSimulation.Geometry,
        buzz: Double,
    ) {
        val n = Tokens.CORD.links
        val x = sim.px[n].toFloat()
        val y = sim.py[n].toFloat()
        val rotation = (SlideToAnswerCordSimulation.handsetRotation(sim.th) + buzz) * 180.0 / PI
        val lit = 1.0 - (1.0 - Tokens.HANDSET.offOpacity) * sim.wOff
        val pink = (1.0 - sim.wConn) * lit
        val green = sim.wConn * lit
        val sourceA = sim.look * swingWeight(sim.clock % Tokens.REPLICA_SWING.period, 0)
        val sourceRest = sim.look * swingWeight(sim.clock % Tokens.REPLICA_SWING.period, 1) +
            (1.0 - sim.look)
        val sourceB = sim.look * swingWeight(sim.clock % Tokens.REPLICA_SWING.period, 2)
        canvas.save()
        canvas.translate(x, y)
        canvas.rotate(rotation.toFloat())
        canvas.scale(g.s.toFloat(), g.s.toFloat())
        canvas.translate(
            -Tokens.REPLICA_SWING.attach[0].toFloat(),
            -Tokens.REPLICA_SWING.attach[1].toFloat(),
        )
        drawSourceAsset(canvas, assets.sourceA, sourceA * pink, green = false)
        drawSourceAsset(canvas, assets.sourceRest, sourceRest * pink, green = false)
        drawSourceAsset(canvas, assets.sourceB, sourceB * pink, green = false)
        drawSourceAsset(canvas, assets.sourceA, sourceA * green, green = true)
        drawSourceAsset(canvas, assets.sourceRest, sourceRest * green, green = true)
        drawSourceAsset(canvas, assets.sourceB, sourceB * green, green = true)
        canvas.restore()
    }

    private fun drawSourceAsset(
        canvas: AndroidCanvas,
        bitmap: Bitmap,
        opacity: Double,
        green: Boolean,
    ) {
        if (opacity <= 0.001) return
        val destination = RectF(-100f, -70f, 160f, 280f)
        paint.alpha = (opacity * 255).roundToInt()
        paint.isFilterBitmap = true
        paint.xfermode = PorterDuffXfermode(PorterDuff.Mode.ADD)
        paint.colorFilter = if (green) GREEN_FILTER else null
        canvas.drawBitmap(bitmap, null, destination, paint)
        paint.colorFilter = null
        paint.xfermode = null
        paint.alpha = 255
    }

    private fun swingWeight(time: Double, take: Int): Double {
        for (index in 0 until SOURCE_KEYS.lastIndex) {
            val first = SOURCE_KEYS[index]
            val second = SOURCE_KEYS[index + 1]
            if (time >= first[0] && time <= second[0]) {
                val progress = (time - first[0]) / (second[0] - first[0])
                val from = if (first[1].toInt() == take) 1.0 - progress else 0.0
                val to = if (second[1].toInt() == take) progress else 0.0
                return from + to
            }
        }
        return if (take == 0) 1.0 else 0.0
    }

    private fun drawControls(
        canvas: AndroidCanvas,
        sim: SlideToAnswerCordSimulation.Sim,
        g: SlideToAnswerCordSimulation.Geometry,
        buttonsY: Double,
        declineScale: Float,
        acceptScale: Float,
        blurredBackground: Bitmap,
        backgroundRect: RectF,
    ) {
        val half = Tokens.LAYOUT.button * g.s / 2
        val left = g.w / 2 - Tokens.LAYOUT.buttonGap * g.s
        val right = g.w / 2 + Tokens.LAYOUT.buttonGap * g.s
        val redX = left + (g.w / 2 - left) * sim.wConn
        val redAlpha = 1 - .55 * sim.wOff
        val dragged = if (sim.grab) SlideToAnswerCordSimulation.dragDistance(sim, g) else 0.0
        val declineGlow = SlideToAnswerCordSimulation.clamp(dragged / (Tokens.DRAG.decline * g.s))
        canvas.save()
        canvas.scale(declineScale, declineScale, redX.toFloat(), buttonsY.toFloat())
        drawHalo(
            canvas,
            redX,
            buttonsY,
            half + 6,
            Tokens.COLORS.decline,
            redAlpha * (.35 + .65 * declineGlow),
        )
        drawGlass(
            canvas,
            redX,
            buttonsY,
            half * 2,
            half * 2,
            Tokens.GLASS.decline,
            redAlpha,
            blurredBackground,
            backgroundRect,
        )
        drawGlyph(canvas, redX, buttonsY, g.s, lifted = false, alpha = redAlpha)
        canvas.restore()

        val morph = if (sim.reduced && sim.phase == SlideToAnswerCordSimulation.PHASE.connected) 1.0 else sim.wConn
        val pillW = half * 2 + (g.w - Tokens.LAYOUT.side * 2 * g.s - half * 2) * morph
        val pillH = half * 2 + (Tokens.GLASS.pillH * g.s - half * 2) * morph
        val cx = right + (g.w / 2 - right) * morph
        val cy = buttonsY + Tokens.GLASS.pillY * g.s * morph
        val acceptGlow = if (sim.phase == SlideToAnswerCordSimulation.PHASE.ringing) SlideToAnswerCordSimulation.clamp(-dragged / (Tokens.DRAG.answer * g.s)) else 0.0
        canvas.save()
        canvas.scale(acceptScale, acceptScale, right.toFloat(), buttonsY.toFloat())
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.5f
        paint.color = Tokens.COLORS.accept.toArgb()
        val acceptAlpha = 1 - .55 * sim.wOff
        paint.alpha = ((acceptAlpha * .45 * (1 - morph)) * 255).roundToInt()
        canvas.drawCircle(right.toFloat(), buttonsY.toFloat(), (half + 6 + 8 * acceptGlow).toFloat(), paint)
        paint.alpha = 255
        drawHalo(
            canvas,
            right,
            buttonsY,
            half + 6,
            Tokens.COLORS.accept,
            acceptAlpha * (.35 + .65 * acceptGlow) * (1 - morph),
        )
        drawGlass(
            canvas,
            cx,
            cy,
            pillW,
            pillH,
            gammaMix(Tokens.GLASS.accept, Tokens.GLASS.neutral, morph),
            acceptAlpha,
            blurredBackground,
            backgroundRect,
        )
        if (morph < 1.0) {
            drawGlyph(
                canvas,
                right,
                buttonsY,
                g.s,
                lifted = true,
                alpha = (1 - min(1.0, morph * 3)) * acceptAlpha,
            )
        }
        canvas.restore()
    }

    /** Samples and blurs the already-painted scene bitmap before tinting it, matching Skia BackdropBlur. */
    private fun drawGlass(canvas: AndroidCanvas, cx: Double, cy: Double, w: Double, h: Double, tint: Color, opacity: Double, blurredBackground: Bitmap, backgroundRect: RectF) {
        val rect = RectF((cx - w / 2).toFloat(), (cy - h / 2).toFloat(), (cx + w / 2).toFloat(), (cy + h / 2).toFloat())
        val radius = (h / 2).toFloat()
        // RN applies the parent Group opacity to the sampled backdrop, tint, and edge together.
        val layer = canvas.saveLayerAlpha(rect, (opacity * 255).roundToInt().coerceIn(0, 255))
        canvas.save()
        path.reset()
        path.addRoundRect(rect, radius, radius, Path.Direction.CW)
        canvas.clipPath(path)
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.xfermode = null
        canvas.drawBitmap(blurredBackground, null, backgroundRect, paint)
        canvas.restore()
        paint.style = Paint.Style.FILL
        paint.color = tint.toArgb()
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = 1.2f
        paint.alpha = 255
        paint.shader = LinearGradient(0f, rect.top, 0f, rect.bottom, intArrayOf(Tokens.GLASS.edgeTop.toArgb(), Tokens.GLASS.edgeBottom.toArgb()), null, Shader.TileMode.CLAMP)
        canvas.drawRoundRect(rect, radius, radius, paint)
        paint.shader = null
        paint.alpha = 255
        canvas.restoreToCount(layer)
    }

    private fun blurBackdrop(source: Bitmap, logicalWidth: Int, logicalHeight: Int, radius: Int): Bitmap {
        // Platform difference: a logical-resolution separable box blur approximates Skia's
        // BackdropBlur deterministically on API 26, where no public equivalent is available.
        if (blurSource == null || blurSource!!.width != logicalWidth || blurSource!!.height != logicalHeight) {
            blurSource = Bitmap.createBitmap(logicalWidth, logicalHeight, Bitmap.Config.ARGB_8888)
        }
        paint.style = Paint.Style.FILL
        paint.alpha = 255
        paint.shader = null
        paint.maskFilter = null
        paint.xfermode = null
        paint.isFilterBitmap = true
        AndroidCanvas(blurSource!!).drawBitmap(
            source,
            null,
            RectF(0f, 0f, logicalWidth.toFloat(), logicalHeight.toFloat()),
            paint,
        )
        val count = logicalWidth * logicalHeight
        if (blurredBackdrop == null || blurredBackdrop!!.width != logicalWidth || blurredBackdrop!!.height != logicalHeight) {
            blurredBackdrop = Bitmap.createBitmap(logicalWidth, logicalHeight, Bitmap.Config.ARGB_8888)
            blurInput = IntArray(count)
            blurWork = IntArray(count)
        }
        blurSource!!.getPixels(blurInput, 0, logicalWidth, 0, 0, logicalWidth, logicalHeight)
        boxBlur(blurInput, blurWork, logicalWidth, logicalHeight, radius, horizontal = true)
        boxBlur(blurWork, blurInput, logicalWidth, logicalHeight, radius, horizontal = false)
        blurredBackdrop!!.setPixels(blurInput, 0, logicalWidth, 0, 0, logicalWidth, logicalHeight)
        return blurredBackdrop!!
    }

    private fun boxBlur(source: IntArray, out: IntArray, width: Int, height: Int, radius: Int, horizontal: Boolean) {
        val window = radius * 2 + 1
        val major = if (horizontal) height else width
        val minor = if (horizontal) width else height
        for (line in 0 until major) {
            var a = 0
            var r = 0
            var g = 0
            var b = 0
            fun pixel(index: Int): Int = if (horizontal) source[line * width + index.coerceIn(0, width - 1)] else source[index.coerceIn(0, height - 1) * width + line]
            for (i in -radius..radius) {
                val c = pixel(i)
                a += c ushr 24
                r += c shr 16 and 255
                g += c shr 8 and 255
                b += c and 255
            }
            for (i in 0 until minor) {
                val value = (a / window shl 24) or (r / window shl 16) or (g / window shl 8) or (b / window)
                if (horizontal) out[line * width + i] = value else out[i * width + line] = value
                val old = pixel(i - radius)
                val next = pixel(i + radius + 1)
                a += (next ushr 24) - (old ushr 24)
                r += (next shr 16 and 255) - (old shr 16 and 255)
                g += (next shr 8 and 255) - (old shr 8 and 255)
                b += (next and 255) - (old and 255)
            }
        }
    }

    private fun drawHalo(canvas: AndroidCanvas, cx: Double, cy: Double, radius: Double, color: Color, opacity: Double) {
        if (opacity <= 0.0) return
        paint.style = Paint.Style.FILL
        paint.color = color.toArgb()
        paint.alpha = (opacity * 255).roundToInt().coerceIn(0, 255)
        paint.maskFilter = BlurMaskFilter(16f, BlurMaskFilter.Blur.NORMAL)
        canvas.drawCircle(cx.toFloat(), cy.toFloat(), radius.toFloat(), paint)
        paint.maskFilter = null
        paint.alpha = 255
    }

    private fun drawGlyph(canvas: AndroidCanvas, cx: Double, cy: Double, scale: Double, lifted: Boolean, alpha: Double = 1.0) {
        canvas.save()
        canvas.translate(cx.toFloat(), cy.toFloat())
        if (lifted) canvas.rotate(-135f)
        canvas.scale(scale.toFloat(), scale.toFloat())
        paint.style = Paint.Style.FILL
        paint.color = android.graphics.Color.WHITE
        paint.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        path.reset()
        path.moveTo(-12f, 1f)
        path.cubicTo(-12f, -6f, -6f, -8f, 0f, -8f)
        path.cubicTo(6f, -8f, 12f, -6f, 12f, 1f)
        path.lineTo(12f, 4f)
        path.cubicTo(12f, 5.5f, 10.5f, 6f, 9.5f, 5.6f)
        path.lineTo(5.5f, 4.2f)
        path.cubicTo(4.6f, 3.9f, 4f, 3f, 4f, 2f)
        path.lineTo(4f, -1.5f)
        path.cubicTo(1.5f, -2.4f, -1.5f, -2.4f, -4f, -1.5f)
        path.lineTo(-4f, 2f)
        path.cubicTo(-4f, 3f, -4.6f, 3.9f, -5.5f, 4.2f)
        path.lineTo(-9.5f, 5.6f)
        path.cubicTo(-10.5f, 6f, -12f, 5.5f, -12f, 4f)
        path.close()
        canvas.drawPath(path, paint)
        paint.alpha = 255
        canvas.restore()
    }

    private fun stroke(canvas: AndroidCanvas, source: Path, width: Double, color: Color) {
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = width.toFloat()
        paint.strokeCap = Paint.Cap.ROUND
        paint.strokeJoin = Paint.Join.ROUND
        paint.color = color.toArgb()
        paint.alpha = 255
        canvas.drawPath(source, paint)
    }

    private fun buildCoil(
        simulation: SlideToAnswerCordSimulation.Sim,
        geometry: SlideToAnswerCordSimulation.Geometry,
    ) {
        val cord = Tokens.CORD
        backCoil.reset()
        frontCoil.reset()
        val links = cord.links
        val turnsPerLink = cord.replicaTurns / links
        val steps = ceil(turnsPerLink * 2 * cord.samples).toInt()
        var roll = 0.0
        val swingTime = simulation.clock % Tokens.REPLICA_SWING.period
        for (index in 0 until cord.replicaRoll.lastIndex) {
            val first = cord.replicaRoll[index]
            val second = cord.replicaRoll[index + 1]
            if (swingTime >= first[0] && swingTime <= second[0]) {
                roll = first[1] + (second[1] - first[1]) *
                    (swingTime - first[0]) / (second[0] - first[0])
            }
        }
        var lastSide = 0
        for (i in 0 until links) {
            val i0 = max(0, i - 1)
            val i3 = min(links, i + 2)
            val x0 = simulation.px[i0]
            val y0 = simulation.py[i0]
            val x1 = simulation.px[i]
            val y1 = simulation.py[i]
            val x2 = simulation.px[i + 1]
            val y2 = simulation.py[i + 1]
            val x3 = simulation.px[i3]
            val y3 = simulation.py[i3]
            for (k in 0 until steps) {
                if (i > 0 && k == 0) continue
                val u = k.toDouble() / steps
                val u2 = u * u
                val u3 = u2 * u
                val x = 0.5 * (
                    2 * x1 + (-x0 + x2) * u +
                        (2 * x0 - 5 * x1 + 4 * x2 - x3) * u2 +
                        (-x0 + 3 * x1 - 3 * x2 + x3) * u3
                    )
                val y = 0.5 * (
                    2 * y1 + (-y0 + y2) * u +
                        (2 * y0 - 5 * y1 + 4 * y2 - y3) * u2 +
                        (-y0 + 3 * y1 - 3 * y2 + y3) * u3
                    )
                val tx = 0.5 * (
                    (-x0 + x2) + 2 * (2 * x0 - 5 * x1 + 4 * x2 - x3) * u +
                        3 * (-x0 + 3 * x1 - 3 * x2 + x3) * u2
                    )
                val ty = 0.5 * (
                    (-y0 + y2) + 2 * (2 * y0 - 5 * y1 + 4 * y2 - y3) * u +
                        3 * (-y0 + 3 * y1 - 3 * y2 + y3) * u2
                    )
                val tangent = hypot(tx, ty).takeIf { it > 0.0 } ?: 1.0
                val phase = 2 * PI * turnsPerLink * (i + u) + cord.replicaPhase + roll
                val offset = cord.replicaWave * geometry.s * sin(phase)
                val px = x - ty / tangent * offset
                val py = y + tx / tangent * offset
                val side = 1
                val target = frontCoil
                if (side != lastSide) target.moveTo(px.toFloat(), py.toFloat()) else target.lineTo(px.toFloat(), py.toFloat())
                lastSide = side
            }
        }
    }

    private fun gammaMix(a: Color, b: Color, t: Double): Color {
        val u = t.coerceIn(0.0, 1.0)
        fun channel(start: Float, end: Float): Float {
            val startLinear = start.toDouble().pow(2.2)
            val endLinear = end.toDouble().pow(2.2)
            return (startLinear + (endLinear - startLinear) * u).pow(1.0 / 2.2).toFloat()
        }
        return Color(
            red = channel(a.red, b.red),
            green = channel(a.green, b.green),
            blue = channel(a.blue, b.blue),
            alpha = channel(a.alpha, b.alpha),
        )
    }

    private fun fbm(x: Float, y: Float): Float = (hash(x, y) * .65f + hash(x * 2f, y * 2f) * .35f)
    private fun hash(x: Float, y: Float): Float = ((sin(x * 12.9898 + y * 78.233) * 43758.5453) % 1.0).let { if (it < 0) (it + 1).toFloat() else it.toFloat() }

    private companion object {
        val SOURCE_KEYS = arrayOf(
            doubleArrayOf(0.0, 0.0),
            doubleArrayOf(1.0, 1.0),
            doubleArrayOf(1.875, 2.0),
            doubleArrayOf(2.9, 1.0),
            doubleArrayOf(4.0, 0.0),
        )
        val GREEN_FILTER = ColorMatrixColorFilter(
            ColorMatrix(
                floatArrayOf(
                    -0.599f, 1.142f, 0.457f, 0f, 0f,
                    0.462f, 0.495f, 0.043f, 0f, 0f,
                    0.144f, 1.631f, -0.775f, 0f, 0f,
                    0f, 0f, 0f, 1f, 0f,
                ),
            ),
        )
    }
}
