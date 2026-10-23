// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.Bitmap
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import android.graphics.BlendMode
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console.SkiaCompat.C
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console.SkiaCompat.c
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console.SkiaCompat.rgba
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

data class Tilt(val x: Double, val y: Double)

// Specimen box in drawing units, for page-level layers aligned to it.
object Specimen {
    const val top = 16.0
    const val height = 340.0
}

private val TRANSFER_AT = Tactile.transferAt
private val transferTop = Tactile.transfer[0].map { c(it) }
private val transferTip = Tactile.transfer[1].map { c(it) }
private val clear = rgba("#00000000")

/** The light's tip color for a channel mix (also the spill, pool and lamp). */
fun tipColor(mix: Double): C = SkiaCompat.interpolateColors(mix, TRANSFER_AT, transferTip)

// System face for the painted text (reflection and lifted object), as RN
// Skia's matchFont({ fontFamily: "sans-serif" }) resolves it on Android.
fun bakedTypeface(weight: Int): Typeface = Typeface.create(Typeface.SANS_SERIF, weight, false)

/** The selector glyphs (glyphs.tsx, viewBox 26) as paths. */
object GlyphPaths {
    val sun: Path = Path().apply {
        addCircle(13f, 13f, 6f, Path.Direction.CW)
        for (i in 0 until 8) {
            val a = i * PI / 4
            moveTo((13 + sin(a) * 10).toFloat(), (13 + cos(a) * 10).toFloat())
            lineTo((13 + sin(a) * 12).toFloat(), (13 + cos(a) * 12).toFloat())
        }
    }

    // "M3 9H9L17 3V23L9 17H3Z M22 10V16"
    val speaker: Path = Path().apply {
        moveTo(3f, 9f); lineTo(9f, 9f); lineTo(17f, 3f); lineTo(17f, 23f); lineTo(9f, 17f); lineTo(3f, 17f); close()
        moveTo(22f, 10f); lineTo(22f, 16f)
    }
    val size = intArrayOf(50, 46)
}

/** Everything the artwork reads, sampled once per draw. */
class ArtState(
    val fill: Double,
    val slosh: Double,
    val mix: Double,
    val lightAlpha: Double,
    val lightBlur: Double,
    val active: Double,
    val trackPress: Double,
    val dialPress: Double,
    val glowPress: Double,
    val brightPress: Double,
    val volumePress: Double,
    val selectorMix: Double,
    val dialRotation: Double,
    val readout: Double,
    val numberInk: Double,
    val numberAlpha: Double,
    val labelAlpha: Double,
    val mode: Int,
    val reduced: Boolean,
    val tilt: Tilt,
) {
    companion object {
        fun of(control: TactileControl, tilt: Tilt) = ArtState(
            fill = control.fill(),
            slosh = control.slosh(),
            mix = control.lightMix(),
            lightAlpha = control.lightAlpha(),
            lightBlur = control.lightBlur(),
            active = control.active.get(),
            trackPress = control.trackPress.get(),
            dialPress = control.dialPress.get(),
            glowPress = control.glowPress(),
            brightPress = control.brightPress.get(),
            volumePress = control.volumePress.get(),
            selectorMix = control.selectorMix(),
            dialRotation = control.dialRotation(),
            readout = control.readout(),
            numberInk = control.numberInk(),
            numberAlpha = control.numberAlpha(),
            labelAlpha = control.labelAlpha(),
            mode = control.mode,
            reduced = control.reduced,
            tilt = tilt,
        )
    }
}

/**
 * The device artwork in drawing units (housing top-left at 0,0), a port of
 * `device-art.tsx`. Used by the specimen canvas, by the floor reflection
 * (baked, mirrored) so the reflection shows the same live light, knob and
 * spill, and as the face of the lifted 3D object (baked).
 */
class DeviceArtPainter {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val path = Path()
    private val housingClip = Path().apply { addRoundRect(RectF(1f, 1f, 999f, 283f), 141f, 141f, Path.Direction.CW) }
    private val trackRect = RectF(G.Track.x.toFloat(), G.Track.y.toFloat(), (G.Track.x + G.Track.width).toFloat(), (G.Track.y + G.Track.height).toFloat())
    private val trackClip = Path().apply { addRoundRect(trackRect, 52f, 52f, Path.Direction.CW) }
    private val dialClip = Path().apply {
        addRoundRect(RectF((G.Dial.x - 100).toFloat(), (G.Dial.y - 100).toFloat(), (G.Dial.x + 100).toFloat(), (G.Dial.y + 100).toFloat()), 100f, 100f, Path.Direction.CW)
    }
    private val titleFace = bakedTypeface(400)
    private val numberFace = bakedTypeface(500)

    // Static shaders (drawing units never change).
    private val housingShader = SkiaCompat.linear(0f, 0f, 0f, 284f, Tactile.housing.map { c(it) }, floatArrayOf(0f, 0.48f, 1f))
    private val housingStroke = SkiaCompat.linear(0f, 0f, 0f, 284f, SkiaCompat.colors(0xFF646464, 0xFF343434, 0xFF191919), floatArrayOf(0f, 0.3f, 1f))
    private val trackStroke = SkiaCompat.linear(
        0f, G.Track.y.toFloat(), 0f, (G.Track.y + G.Track.height).toFloat(),
        SkiaCompat.colors(0xFF101010, 0xFF343434, 0xFF565656), floatArrayOf(0f, 0.75f, 1f),
    )
    private val glassShader = SkiaCompat.linear(0f, (G.Track.y + 4).toFloat(), 0f, (G.Track.y + 38).toFloat(), listOf(rgba("#FFFFFF1C"), rgba("#FFFFFF00")))
    private val bezelShader = SkiaCompat.linear(0f, (G.Dial.y - 100).toFloat(), 0f, (G.Dial.y + 100).toFloat(), Tactile.bezel.map { c(it) }, floatArrayOf(0f, 0.44f, 1f))
    private val bezelStroke = SkiaCompat.linear(0f, (G.Dial.y - 100).toFloat(), 0f, (G.Dial.y + 100).toFloat(), SkiaCompat.colors(0xFF8D8D8D, 0xFF454545, 0xFF171717), floatArrayOf(0f, 0.38f, 1f))
    private val hubShader = SkiaCompat.linear(0f, (G.Dial.y - 70).toFloat(), 0f, (G.Dial.y + 70).toFloat(), Tactile.dial.map { c(it) })
    private val sheenShader = SkiaCompat.sweep(
        G.Dial.x.toFloat(), G.Dial.y.toFloat(),
        listOf(rgba("#FFFFFF00"), rgba("#FFFFFF30"), rgba("#FFFFFF00"), rgba("#FFFFFF00"), rgba("#FFFFFF14"), rgba("#FFFFFF00"), rgba("#FFFFFF00")),
        floatArrayOf(0f, 0.08f, 0.2f, 0.5f, 0.58f, 0.7f, 1f),
    )
    private val bevelColors = listOf(rgba("#FFFFFF00"), rgba("#FFFFFF2A"), rgba("#FFFFFF00"))
    private val grabColors = listOf(rgba("#FFFFFFCC"), rgba("#FFFFFF00"))
    private val blur14 = SkiaCompat.blur(14f)
    private val blur26 = SkiaCompat.blur(26f)
    private val blur16 = SkiaCompat.blur(16f)
    private val blur7 = SkiaCompat.blur(7f)
    private val blur2 = SkiaCompat.blur(2f)
    private val blur1 = SkiaCompat.blur(1f)
    private val blur3 = SkiaCompat.blur(3f)

    private fun reset(paint: Paint = p): Paint {
        paint.reset()
        paint.isAntiAlias = true
        return paint
    }

    private fun alpha(v: Double) = (v.coerceIn(0.0, 1.0) * 255).roundToInt()

    companion object {
        /** Resolution of the baked face recording (the lifted object's texture). */
        const val BAKED_SCALE = 2f

        // <FractalNoise freqX={0.65} freqY={0.65} octaves={1} seed={7} />, rasterized
        // at device resolution once per scale and shared by every painter.
        private val noise = FractalNoise(0.65f, 0.65f, 1, 7f)
        private val noiseCache = java.util.concurrent.ConcurrentHashMap<Float, Bitmap>()
        private val pending = java.util.concurrent.ConcurrentHashMap.newKeySet<Float>()
        // Bumped when a raster lands; draw passes read it, so they redraw then.
        private val noiseVersion = androidx.compose.runtime.mutableIntStateOf(0)
        private val layerCache = java.util.concurrent.ConcurrentHashMap<Float, Layers>()
        private val layerPending = java.util.concurrent.ConcurrentHashMap.newKeySet<Float>()
        private val layerVersion = androidx.compose.runtime.mutableIntStateOf(0)

        /** Blocking: rasterizes (or returns) the grain for a scale. */
        fun noiseFor(pxPerUnit: Float): Bitmap =
            noiseCache.getOrPut(pxPerUnit) { noise.bitmap(1000f, 284f, pxPerUnit) }

        /** Non-blocking for the draw pass: null until the background raster is ready. */
        fun noiseOrSchedule(pxPerUnit: Float): Bitmap? {
            noiseVersion.intValue
            noiseCache[pxPerUnit]?.let { return it }
            if (pending.add(pxPerUnit)) {
                Thread {
                    noiseFor(pxPerUnit)
                    android.os.Handler(android.os.Looper.getMainLooper()).post { noiseVersion.intValue++ }
                }.start()
            }
            return null
        }
    }

    fun draw(c: Canvas, s: ArtState, fx: Effects, baked: Boolean, pxPerUnit: Float, blockOnGrain: Boolean = false) {
        // The static sub-layers, pre-rasterized at this scale (see Layers).
        val layers = if (blockOnGrain) null else layersOrSchedule(pxPerUnit)
        val H = G.Track.height
        val extent = G.Track.width * s.fill
        val fillX = G.Track.x - H
        val fw = extent + H
        val fr = min(H / 2, extent / 2)
        // Meniscus: the leading edge's horizontal radius follows the slosh spring,
        // scaled down with the slice so a thin light deforms proportionally.
        val ry = fr
        val rx = if (!fx.meniscus) ry else {
            val raw = ry + s.slosh * (ry / (H / 2))
            max(ry * 0.55, min(min(ry * 1.7, raw), fw / 2))
        }
        // Fades the last hairline at ~0 instead of leaving a 1px sliver.
        val presence = min(1.0, extent / 8)
        // The halo grows with length so a few percent never reads as a blurred blob.
        val reach = min(1.0, extent / H).let { it * it }
        val glowWidth = max(1.0, extent)
        val endX = (G.Track.x + glowWidth).toFloat()
        val colors = listOf(
            SkiaCompat.interpolateColors(s.mix, TRANSFER_AT, transferTop),
            SkiaCompat.interpolateColors(s.mix, TRANSFER_AT, transferTip),
        )
        val alpha = s.lightAlpha * (0.35 + 0.65 * s.active)
        // Light spill: what the light illuminates around it. Strength grows with
        // length; the knob rim catches it only as the light gets close.
        val tip = G.Track.x + extent
        val cy = G.Track.y + H / 2
        val tipColor = tipColor(s.mix)
        val spill = if (fx.spill) alpha * min(1.0, extent / 8) * (0.22 + 0.3 * s.fill) else 0.0
        val knobLight = if (fx.spill) spill * max(0.0, min(1.0, (tip - 380) / 220)) * 1.6 else 0.0
        // Tilt: highlight band along the top bevel and the knob sheen angle.
        val bevelX = 500 + s.tilt.x * 320
        val sheenRotate = -0.6 + if (fx.tilt) s.tilt.x * 0.7 else 0.0
        // Keep blur filters fixed: crossfade cached sharp/soft paints instead.
        val soft = min(1.0, s.lightBlur / 14)
        val softAlpha = alpha * presence * soft
        val sharpAlpha = alpha * presence * (1 - soft)
        // Colored halo the light casts on the housing. Static under Reduce Motion;
        // only the press swell is motion.
        val glow = alpha * presence * reach * (0.42 + if (s.reduced) 0.0 else s.glowPress * 0.18 + s.lightBlur / 60)
        val brightnessWell = SkiaCompat.interpolateColors(s.selectorMix, doubleArrayOf(0.0, 1.0), listOf(c(0xFF151515), c(0xFF222222)))
        val volumeWell = SkiaCompat.interpolateColors(s.selectorMix, doubleArrayOf(0.0, 1.0), listOf(c(0xFF222222), c(0xFF151515)))
        val brightnessRim = (1 - s.selectorMix) * 0.75
        val volumeRim = s.selectorMix * 0.75
        // Grab: a finger on the track wakes the light's tip — a soft highlight just
        // inside the leading edge, so the light reads as the thing being held.
        val grab = (if (s.reduced) 0.0 else s.trackPress * 0.34) * alpha
        val grabX = max(G.Track.x + H / 2, tip - H / 2).toFloat()
        // Selector press: the pressed well darkens as it sinks.
        val brightSink = s.brightPress * 0.42
        val volumeSink = s.volumePress * 0.42

        layers?.let { c.drawBitmap(it.base, null, it.bounds, bitmapPaint) } ?: drawBase(c, pxPerUnit, blockOnGrain)
        if (fx.tilt) {
            c.save(); c.clipPath(housingClip)
            reset(); p.shader = SkiaCompat.linear((bevelX - 220).toFloat(), 0f, (bevelX + 220).toFloat(), 0f, bevelColors)
            c.drawRect(0f, 0f, 1000f, 18f, p)
            c.restore()
        }
        if (fx.spill) {
            c.save(); c.clipPath(housingClip)
            reset(); p.blendMode = BlendMode.SCREEN; p.alpha = alpha(spill)
            p.shader = SkiaCompat.radial(tip.toFloat(), cy.toFloat(), 330f, listOf(tipColor, clear))
            c.drawCircle(tip.toFloat(), cy.toFloat(), 330f, p)
            c.restore()
        }
        layers?.let { c.drawBitmap(it.track, null, it.bounds, bitmapPaint) } ?: drawTrackWell(c)
        // Halo.
        reset(); p.alpha = alpha(glow); p.maskFilter = blur26
        p.shader = SkiaCompat.linear(G.Track.x.toFloat(), 0f, endX, 0f, colors)
        r.set(G.Track.x.toFloat(), G.Track.y.toFloat(), (G.Track.x + glowWidth).toFloat(), (G.Track.y + H).toFloat())
        c.drawRoundRect(r, fr.toFloat(), fr.toFloat(), p)
        // The light, clipped to the track.
        c.save(); c.clipPath(trackClip)
        val fillShader = SkiaCompat.linear(G.Track.x.toFloat(), 0f, endX, 0f, colors)
        r.set(fillX.toFloat(), G.Track.y.toFloat(), (fillX + fw).toFloat(), (G.Track.y + H).toFloat())
        if (sharpAlpha > 0) {
            reset(); p.alpha = alpha(sharpAlpha); p.shader = fillShader
            c.drawRoundRect(r, rx.toFloat(), ry.toFloat(), p)
        }
        if (softAlpha > 0) {
            reset(); p.alpha = alpha(softAlpha); p.shader = fillShader; p.maskFilter = blur14
            c.drawRoundRect(r, rx.toFloat(), ry.toFloat(), p)
        }
        if (grab > 0) {
            reset(); p.blendMode = BlendMode.SCREEN; p.alpha = alpha(grab)
            p.shader = SkiaCompat.radial(grabX, cy.toFloat(), (H * 0.62).toFloat(), grabColors)
            c.drawCircle(grabX, cy.toFloat(), (H * 0.62).toFloat(), p)
        }
        if (fx.glass) {
            reset(); p.shader = glassShader
            c.drawRect(G.Track.x.toFloat(), (G.Track.y + 4).toFloat(), (G.Track.x + G.Track.width).toFloat(), (G.Track.y + 38).toFloat(), p)
        }
        c.restore()
        // Selector wells: the fill follows the handoff; the inner shadows are static.
        for (mode in 0..1) {
            val (cx, cyy, rad) = if (mode == 0) Triple(G.Brightness.x, G.Brightness.y, G.Brightness.radius) else Triple(G.Volume.x, G.Volume.y, G.Volume.radius)
            reset(); p.setColor((if (mode == 0) brightnessWell else volumeWell).toLong())
            c.drawCircle(cx.toFloat(), cyy.toFloat(), rad.toFloat(), p)
        }
        layers?.let { c.drawBitmap(it.wells, null, it.bounds, bitmapPaint) } ?: drawWellShadows(c)
        if (fx.rim) for (mode in 0..1) {
            val (cx, cyy, rad) = if (mode == 0) Triple(G.Brightness.x, G.Brightness.y, G.Brightness.radius) else Triple(G.Volume.x, G.Volume.y, G.Volume.radius)
            reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 2.2f
            p.setColor(c(if (mode == 0) Tactile.warm[1] else Tactile.cool[1]).withAlpha((if (mode == 0) brightnessRim else volumeRim).toFloat()).toLong())
            c.drawCircle(cx.toFloat(), cyy.toFloat(), (rad - 1.5).toFloat(), p)
        }
        for (mode in 0..1) {
            val (cx, cyy, rad) = if (mode == 0) Triple(G.Brightness.x, G.Brightness.y, G.Brightness.radius) else Triple(G.Volume.x, G.Volume.y, G.Volume.radius)
            val sink = if (mode == 0) brightSink else volumeSink
            if (sink <= 0) continue
            reset(); p.color = android.graphics.Color.BLACK; p.alpha = alpha(sink)
            c.drawCircle(cx.toFloat(), cyy.toFloat(), rad.toFloat(), p)
        }
        // The knob.
        val dx = G.Dial.x.toFloat()
        val dy = G.Dial.y.toFloat()
        c.save()
        val k = (1 - s.dialPress * 0.025).toFloat()
        c.scale(k, k, dx, dy)
        val atRest = s.dialPress == 0.0
        if (atRest && layers != null) c.drawBitmap(layers.knob, null, layers.bounds, bitmapPaint) else Perf.section("DeviceArt:knobBodyLive") { drawKnobBody(c) }
        c.save(); c.rotate(Math.toDegrees(s.dialRotation).toFloat(), dx, dy)
        reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 3.3f; p.strokeCap = Paint.Cap.ROUND
        p.color = android.graphics.Color.toArgb(c(Tactile.groove).toLong())
        for (i in 0 until 28) {
            val a = i * PI * 2 / 28
            c.drawLine(
                (G.Dial.x + sin(a) * 85).toFloat(), (G.Dial.y - cos(a) * 85).toFloat(),
                (G.Dial.x + sin(a) * 96).toFloat(), (G.Dial.y - cos(a) * 96).toFloat(), p,
            )
        }
        c.restore()
        if (fx.spill && knobLight > 0) {
            c.save(); c.clipPath(dialClip)
            reset(); p.blendMode = BlendMode.SCREEN; p.alpha = alpha(knobLight)
            p.shader = SkiaCompat.radial((G.Dial.x - 130).toFloat(), dy, 150f, listOf(tipColor, clear))
            c.drawCircle(dx, dy, 100f, p)
            c.restore()
        }
        if (fx.sheen) {
            c.save(); c.rotate(Math.toDegrees(sheenRotate).toFloat(), dx, dy)
            reset(); p.blendMode = BlendMode.SCREEN; p.shader = sheenShader
            c.drawCircle(dx, dy, 99f, p)
            c.restore()
        }
        if (atRest && layers != null) c.drawBitmap(layers.hub, null, layers.bounds, bitmapPaint) else Perf.section("DeviceArt:knobHubLive") { drawKnobHub(c) }
        c.restore()

        if (baked) drawBakedOverlays(c, s)
    }


    // Static parts of the artwork in paint order, drawn live or into Layers.
    private fun drawBase(c: Canvas, pxPerUnit: Float, blockOnGrain: Boolean) {
        // Drop shadow under the housing.
        reset(); p.color = android.graphics.Color.BLACK; p.alpha = alpha(0.36); p.maskFilter = blur14
        r.set(4f, 12f, 996f, 292f); c.drawRoundRect(r, 140f, 140f, p)
        // Housing.
        reset(); p.shader = housingShader
        r.set(0f, 0f, 1000f, 284f); c.drawRoundRect(r, 142f, 142f, p)
        // Grain.
        val noiseBitmap = if (blockOnGrain) noiseFor(pxPerUnit) else noiseOrSchedule(pxPerUnit)
        if (noiseBitmap != null) {
            c.save(); c.clipPath(housingClip)
            reset(); p.alpha = alpha(0.025); p.blendMode = BlendMode.MULTIPLY; p.isFilterBitmap = true
            c.drawBitmap(noiseBitmap, null, RectF(0f, 0f, 1000f, 284f), p)
            c.restore()
        }
        // Edge.
        reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 2.4f; p.shader = housingStroke
        r.set(1.2f, 1.2f, 998.8f, 282.8f); c.drawRoundRect(r, 140.8f, 140.8f, p)
    }

    private fun drawTrackWell(c: Canvas) {
        // Track well.
        box(
            c, trackRect, 52f, Tactile.well,
            listOf(Triple(0f, 5f, 7f to rgba("#000000B0")), Triple(0f, -1f, 1f to rgba("#FFFFFF18"))), null,
        )
        reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f; p.shader = trackStroke
        c.drawRoundRect(trackRect, 52f, 52f, p)
    }

    private fun drawWellShadows(c: Canvas) {
        for (mode in 0..1) {
            val (cx, cyy, rad) = if (mode == 0) Triple(G.Brightness.x, G.Brightness.y, G.Brightness.radius) else Triple(G.Volume.x, G.Volume.y, G.Volume.radius)
            boxShadows(
                c, RectF((cx - rad).toFloat(), (cyy - rad).toFloat(), (cx + rad).toFloat(), (cyy + rad).toFloat()), rad.toFloat(),
                listOf(Triple(0f, 2f, 2f to rgba("#000000B0")), Triple(0f, -1f, 1f to rgba("#FFFFFF35"))),
            )
        }
    }

    private fun drawKnobBody(c: Canvas) {
        val dx = G.Dial.x.toFloat()
        val dy = G.Dial.y.toFloat()
        reset(); p.color = android.graphics.Color.BLACK; p.alpha = alpha(0.75); p.maskFilter = blur16
        c.drawCircle(dx, dy + 18, 97f, p)
        reset(); p.shader = bezelShader
        c.drawCircle(dx, dy, 100f, p)
        reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f; p.shader = bezelStroke
        c.drawCircle(dx, dy, 99f, p)
    }

    private fun drawKnobHub(c: Canvas) {
        val dx = G.Dial.x.toFloat()
        val dy = G.Dial.y.toFloat()
        reset(); p.shader = hubShader
        c.drawCircle(dx, dy, 70f, p)
        reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.4f; p.color = 0xFF4C4C4C.toInt()
        c.drawCircle(dx, dy, 70f, p)
        // Center cap with an inner shadow (dx 0, dy 2, σ 3, black).
        reset(); p.color = 0xFF181818.toInt()
        c.drawCircle(dx, dy, 29f, p)
        c.save()
        path.reset(); path.addCircle(dx, dy, 29f, Path.Direction.CW); c.clipPath(path)
        reset(); p.color = android.graphics.Color.BLACK; p.maskFilter = blur3
        val ring = Path().apply {
            fillType = Path.FillType.EVEN_ODD
            addCircle(dx, dy, 29f + 40f, Path.Direction.CW)
            addCircle(dx, dy + 2f, 29f, Path.Direction.CW)
        }
        c.drawPath(ring, p)
        c.restore()
        reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 1f; p.color = 0xFF363636.toInt()
        c.drawCircle(dx, dy, 29f, p)
    }

    /**
     * Static sub-layers rasterized once per scale with Skia's CPU backend
     * (android.graphics on a Bitmap): HWUI otherwise re-rasterizes every
     * blurred path (inner-shadow rings, drop shadows) on the CPU each frame.
     * Covers units [-PAD, 1000+PAD] × [-PAD, 284+PAD]; drawn back 1:1.
     */
    class Layers(val bounds: RectF, val base: Bitmap, val track: Bitmap, val wells: Bitmap, val knob: Bitmap, val hub: Bitmap)

    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun buildLayers(pxPerUnit: Float): Layers {
        // Whole-pixel padding keeps the grain on the same pixel grid.
        val pad = kotlin.math.ceil(60f * pxPerUnit) / pxPerUnit
        val w = kotlin.math.ceil((1000f + 2 * pad) * pxPerUnit).toInt()
        val h = kotlin.math.ceil((284f + 2 * pad) * pxPerUnit).toInt()
        val bounds = RectF(-pad, -pad, -pad + w / pxPerUnit, -pad + h / pxPerUnit)
        fun layer(draw: (Canvas) -> Unit): Bitmap {
            val b = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
            val c = Canvas(b)
            c.scale(pxPerUnit, pxPerUnit)
            c.translate(pad, pad)
            draw(c)
            return b
        }
        return Layers(
            bounds,
            layer { drawBase(it, pxPerUnit, blockOnGrain = true) },
            layer { drawTrackWell(it) },
            layer { drawWellShadows(it) },
            layer { drawKnobBody(it) },
            layer { drawKnobHub(it) },
        )
    }

    private fun layersOrSchedule(pxPerUnit: Float): Layers? {
        layerVersion.intValue
        layerCache[pxPerUnit]?.let { return it }
        if (layerPending.add(pxPerUnit)) {
            Thread {
                // A painter of its own: Paint objects are not thread safe.
                layerCache[pxPerUnit] = DeviceArtPainter().buildLayers(pxPerUnit)
                android.os.Handler(android.os.Looper.getMainLooper()).post { layerVersion.intValue++ }
            }.start()
        }
        return null
    }

    /**
     * RN Skia `Box` + inner `BoxShadow`s: the box, then for each inner shadow
     * a blurred ring (outer = box inflated by 10+|d|, inner = box offset by d)
     * clipped to the box (non-AA clip, as drawBox does).
     */
    private fun box(c: Canvas, rect: RectF, radius: Float, color: Long, shadows: List<Triple<Float, Float, Pair<Float, C>>>, fillOverride: C?) {
        reset()
        if (fillOverride != null) p.setColor(fillOverride.toLong()) else p.color = android.graphics.Color.toArgb(c(color).toLong())
        c.drawRoundRect(rect, radius, radius, p)
        boxShadows(c, rect, radius, shadows)
    }

    private fun boxShadows(c: Canvas, rect: RectF, radius: Float, shadows: List<Triple<Float, Float, Pair<Float, C>>>) {
        for ((dx, dy, bc) in shadows) {
            val (blurSigma, color2) = bc
            val ddx = 10 + kotlin.math.abs(dx)
            val ddy = 10 + kotlin.math.abs(dy)
            c.save()
            path.reset(); path.addRoundRect(rect, radius, radius, Path.Direction.CW)
            c.clipPath(path)
            reset()
            p.setColor(color2.toLong())
            p.maskFilter = SkiaCompat.blur(blurSigma)
            val ring = Path()
            ring.fillType = Path.FillType.EVEN_ODD
            ring.addRoundRect(RectF(rect.left - ddx, rect.top - ddy, rect.right + ddx, rect.bottom + ddy), radius + ddx, radius + ddy, Path.Direction.CW)
            ring.addRoundRect(RectF(rect.left + dx, rect.top + dy, rect.right + dx, rect.bottom + dy), radius, radius, Path.Direction.CW)
            c.drawPath(ring, p)
            c.restore()
        }
    }

    /** The native overlays (title, number, selector glyphs) painted in: for the
     * floor reflection and the lifted 3D object's face. */
    private fun drawBakedOverlays(c: Canvas, s: ArtState) {
        val brightGlyph = 1 - s.selectorMix
        for (mode in 0..1) {
            val (cx, cyy) = if (mode == 0) G.Brightness.x to G.Brightness.y else G.Volume.x to G.Volume.y
            val k = GlyphPaths.size[mode] / 26f
            val sink = (1 - (if (mode == 0) s.brightPress else s.volumePress) * 0.08).toFloat()
            c.save()
            c.scale(sink, sink, cx.toFloat(), cyy.toFloat())
            c.translate((cx - 13 * k).toFloat(), (cyy - 13 * k).toFloat())
            c.scale(k, k)
            for (tint in longArrayOf(Tactile.inactive, Tactile.text)) {
                val opacity = if (tint == Tactile.inactive) 1.0 else if (mode == 0) brightGlyph else s.selectorMix
                reset(); p.style = Paint.Style.STROKE; p.strokeWidth = 1.6f
                p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND
                p.setColor(c(tint).withAlpha(opacity.toFloat()).toLong())
                c.drawPath(if (mode == 0) GlyphPaths.sun else GlyphPaths.speaker, p)
            }
            c.restore()
        }
        reset(); p.typeface = titleFace; p.textSize = 31f
        p.setColor(c(Tactile.text).withAlpha(s.labelAlpha.toFloat()).toLong())
        c.drawText(if (s.mode == 0) "Brightness" else "Volume", 118f, (36 + 31).toFloat(), p)
        val numberText = "${jsRound(s.readout).toInt()}%"
        reset(); p.typeface = numberFace; p.textSize = 30f
        val numberX = G.Readout.x + G.Readout.width - p.skiaMeasureWidth(numberText)
        val ink = SkiaCompat.interpolateColors(s.numberInk, doubleArrayOf(0.0, 1.0), listOf(c(Tactile.text), c(Tactile.ink)))
        p.setColor(ink.withAlpha(s.numberAlpha.toFloat()).toLong())
        c.drawText(numberText, numberX.toFloat(), (G.Readout.y + G.Readout.line * 0.78).toFloat(), p)
    }
}
