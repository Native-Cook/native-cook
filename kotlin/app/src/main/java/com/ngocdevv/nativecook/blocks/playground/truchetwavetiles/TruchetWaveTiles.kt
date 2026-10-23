package com.ngocdevv.nativecook.blocks.playground.truchetwavetiles

import android.graphics.Bitmap
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.graphics.Path
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

private const val SEGMENT_WIDTH = 92f
private const val SEGMENT_HEIGHT = 36f
private const val BAR_PAD = 4f
private const val SWATCH = SEGMENT_HEIGHT + BAR_PAD * 2
private const val CONTROLS_GAP = 10f
private const val CONTROLS_BOTTOM = 18f
private const val CONTROLS_WIDTH = SEGMENT_WIDTH * 3 + BAR_PAD * 2 + CONTROLS_GAP + SWATCH
// The glass behind the controls: what lies under them, blurred, then darkened.
private const val GLASS_BLUR = 14f
private const val GRAIN_STRENGTH = 0.09f
// The rake starts after PAN_SLOP dp; a touch lifted within TAP_MS that never got that far is a tap.
private const val PAN_SLOP = 4f
private const val TAP_MS = 500L

private fun rgb(c: FloatArray, alpha: Float = 1f) =
    android.graphics.Color.argb((clamp01(alpha) * 255 + 0.5f).toInt(), c[0].toInt(), c[1].toInt(), c[2].toInt())

private fun mix(a: FloatArray, b: FloatArray, t: Float, alpha: Float = 1f) = android.graphics.Color.argb(
    (clamp01(alpha) * 255 + 0.5f).toInt(),
    (a[0] + (b[0] - a[0]) * t + 0.5f).toInt(),
    (a[1] + (b[1] - a[1]) * t + 0.5f).toInt(),
    (a[2] + (b[2] - a[2]) * t + 0.5f).toInt(),
)

private fun composeColor(c: FloatArray) = Color(c[0] / 255f, c[1] / 255f, c[2] / 255f)

/** The motif paths of one frame: resting tiles in one path, turning ones as lifted slabs. */
private class TruchetScene {
    val lines = Path()
    val fills = Path()
    val shadows = Path()
    val faces = Path()
    val edges = Path()
    val liftedLines = Path()
    val liftedFills = Path()
    var most = 0f
    var fill = 0f
    var width = 0f

    fun reset() {
        lines.rewind()
        fills.rewind()
        shadows.rewind()
        faces.rewind()
        edges.rewind()
        liftedLines.rewind()
        liftedFills.rewind()
        most = 0f
    }
}

/**
 * Adds a tile's motif to a path: the edges of two corner caps (top left and bottom right), turned
 * by (cos, sin) about the centre and scaled by `scale`. `closed` adds the first cap as a filled shape.
 */
private fun addMotif(b: Path, cx: Float, cy: Float, side: Float, cos: Float, sin: Float, scale: Float, reach: Float, round: Float, closed: Boolean) {
    val half = side / 2
    val r = reach * side
    // Filled, only the top-left cap counts: with the caps reaching the far corners it is half the
    // tile, the classic triangle tile.
    for (k in 0 until (if (closed) 1 else 2)) {
        val sign = if (k == 0) -1f else 1f
        val ox = sign * half
        val oy = sign * half
        val p0x = ox - sign * r
        val p0y = oy
        val p1x = ox
        val p1y = oy - sign * r
        // Control points: along the tangents for a quarter circle, along the chord for a line.
        val a0x = p0x + ((p1x - p0x) / 3) * (1 - round)
        val a0y = p0y + (-sign * KAPPA * r) * round + ((p1y - p0y) / 3) * (1 - round)
        val a1x = p1x + (-sign * KAPPA * r) * round + ((p0x - p1x) / 3) * (1 - round)
        val a1y = p1y + ((p0y - p1y) / 3) * (1 - round)
        fun x(px: Float, py: Float) = cx + px * scale * cos - py * scale * sin
        fun y(px: Float, py: Float) = cy + px * scale * sin + py * scale * cos
        if (closed) {
            b.moveTo(x(ox, oy), y(ox, oy))
            b.lineTo(x(p0x, p0y), y(p0x, p0y))
        } else {
            b.moveTo(x(p0x, p0y), y(p0x, p0y))
        }
        b.cubicTo(x(a0x, a0y), y(a0x, a0y), x(a1x, a1y), y(a1x, a1y), x(p1x, p1y), y(p1x, p1y))
        if (closed) b.close()
    }
}

/** Adds a square of half side `half` centred on (cx, cy), turned by (cos, sin). */
private fun addSquare(b: Path, cx: Float, cy: Float, half: Float, cos: Float, sin: Float) {
    b.moveTo(cx - half * cos + half * sin, cy - half * sin - half * cos)
    b.lineTo(cx + half * cos + half * sin, cy + half * sin - half * cos)
    b.lineTo(cx + half * cos - half * sin, cy + half * sin + half * cos)
    b.lineTo(cx - half * cos - half * sin, cy - half * sin + half * cos)
    b.close()
}

/** Builds the frame's paths; only tiles reaching into [yMin, yMax] when a band is given. */
private fun TruchetMotion.build(scene: TruchetScene, yMin: Float = Float.NEGATIVE_INFINITY, yMax: Float = Float.POSITIVE_INFINITY) {
    scene.reset()
    val look = styleAt(morph.value)
    scene.fill = look.fill
    scene.width = look.width * side
    for (i in 0 until count) {
        val rad = angle[i] * PI.toFloat() / 180
        val c = cos(rad)
        val s = sin(rad)
        val cx = (i % COLUMNS + 0.5f) * side
        val cy = top + (i / COLUMNS + 0.5f) * side
        if (cy + side < yMin || cy - side > yMax) continue
        val off = abs(angle[i] - turns[i] * 90f)
        if (off <= 0.5f) {
            addMotif(scene.lines, cx, cy, side, c, s, 1f, look.reach, look.round, false)
            if (look.fill > 0.001f) addMotif(scene.fills, cx, cy, side, c, s, 1f, look.reach, look.round, true)
            continue
        }
        val lift = sin(PI.toFloat() * clamp01(1 - off / 90))
        scene.most = max(scene.most, lift)
        val scale = 1 + LIFT * lift
        val half = side * scale / 2
        addSquare(scene.shadows, cx, cy + LIFT_SHADOW * lift, half * (0.94f + 0.06f * lift), c, s)
        addSquare(scene.faces, cx, cy, half, c, s)
        // Only a tile well off the ground gets a light edge; one settling in blends back in.
        if (lift > 0.3f) addSquare(scene.edges, cx, cy, half, c, s)
        addMotif(scene.liftedLines, cx, cy, side, c, s, scale, look.reach, look.round, false)
        if (look.fill > 0.001f) addMotif(scene.liftedFills, cx, cy, side, c, s, scale, look.reach, look.round, true)
    }
}

/** Paints, the grain and the reveal circle, made once. */
private class TruchetPaints {
    val fill = Paint().apply { isAntiAlias = true }
    val shadow = Paint().apply { isAntiAlias = true }
    val body = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    val sheen = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        color = android.graphics.Color.argb(56, 255, 255, 255)
    }
    val drop = Paint().apply {
        isAntiAlias = true
        color = android.graphics.Color.argb(87, 0, 0, 0)
        maskFilter = BlurMaskFilter(sigmaToRadius(5f), BlurMaskFilter.Blur.NORMAL)
    }
    val edge = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 1f
    }
    val rim = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 3f
        maskFilter = BlurMaskFilter(sigmaToRadius(4f), BlurMaskFilter.Blur.NORMAL)
    }
    val ring = Paint().apply {
        isAntiAlias = true
        style = Paint.Style.STROKE
        strokeWidth = 10f
        maskFilter = BlurMaskFilter(sigmaToRadius(8f), BlurMaskFilter.Blur.NORMAL)
    }
    val grainPaint = Paint().apply { isFilterBitmap = false }
    val circle = Path()
    var grain: Bitmap? = null

    /**
     * A fine print grain: a speck per 1/1.5 dp cell, a little lighter or darker than what lies
     * beneath, from the same hash the React Native shader uses. Drawn once into a bitmap the size
     * of the screen, so it costs one bitmap draw a frame on every Android version.
     */
    fun grain(w: Float, h: Float): Bitmap {
        val gw = ceil(w * 1.5f).toInt().coerceAtLeast(1)
        val gh = ceil(h * 1.5f).toInt().coerceAtLeast(1)
        grain?.let { if (it.width == gw && it.height == gh) return it }
        val pixels = IntArray(gw * gh)
        for (y in 0 until gh) {
            for (x in 0 until gw) {
                // hash(p): p = fract(p * (123.34, 456.21)); p += dot(p, p + 45.32); fract(p.x * p.y)
                var px = fract(x * 123.34f)
                var py = fract(y * 456.21f)
                val d = px * (px + 45.32f) + py * (py + 45.32f)
                px += d
                py += d
                val g = fract(px * py)
                val a = (abs(g - 0.5f) * GRAIN_STRENGTH * 255 + 0.5f).toInt()
                val v = if (g > 0.5f) 255 else 0
                pixels[y * gw + x] = (a shl 24) or (v shl 16) or (v shl 8) or v
            }
        }
        val bitmap = Bitmap.createBitmap(pixels, gw, gh, Bitmap.Config.ARGB_8888)
        grain = bitmap
        return bitmap
    }
}

private fun fract(v: Float) = v - floor(v)

/** Draws a motif path as glazed ink: a soft shadow under it, the ink, and a sheen along its top. */
private fun drawInk(canvas: android.graphics.Canvas, p: TruchetPaints, lines: Path, fills: Path, width: Float, ink: FloatArray, fill: Float) {
    if (fill > 0.001f) {
        p.shadow.style = Paint.Style.FILL
        p.shadow.color = android.graphics.Color.argb(51, 0, 0, 0)
        canvas.save()
        canvas.translate(0f, 1.2f)
        canvas.drawPath(fills, p.shadow)
        canvas.restore()
        p.fill.style = Paint.Style.FILL
        p.fill.color = rgb(ink, fill)
        canvas.drawPath(fills, p.fill)
        // A hairline in the same colour closes the seams between neighbouring triangles.
        p.fill.style = Paint.Style.STROKE
        p.fill.strokeWidth = 1.1f
        canvas.drawPath(fills, p.fill)
        p.fill.style = Paint.Style.FILL
    }
    if (width > 0.2f) {
        p.shadow.style = Paint.Style.STROKE
        p.shadow.strokeWidth = width + 1
        p.shadow.strokeCap = Paint.Cap.ROUND
        p.shadow.color = android.graphics.Color.argb(51, 0, 0, 0)
        canvas.save()
        canvas.translate(0f, 1.4f)
        canvas.drawPath(lines, p.shadow)
        canvas.restore()
        p.body.strokeWidth = width
        p.body.color = rgb(ink)
        canvas.drawPath(lines, p.body)
        p.sheen.strokeWidth = width * 0.28f
        canvas.save()
        canvas.translate(-width * 0.12f, -width * 0.18f)
        canvas.drawPath(lines, p.sheen)
        canvas.restore()
    }
}

/** The whole grid in one palette: ground, resting ink, then the turning tiles as lifted slabs. */
private fun paintScene(canvas: android.graphics.Canvas, p: TruchetPaints, scene: TruchetScene, ground: FloatArray, ink: FloatArray, w: Float, h: Float) {
    p.fill.style = Paint.Style.FILL
    p.fill.color = rgb(ground)
    canvas.drawRect(0f, 0f, w, h, p.fill)
    drawInk(canvas, p, scene.lines, scene.fills, scene.width, ink, scene.fill)
    if (scene.most <= 0f) return
    canvas.drawPath(scene.shadows, p.drop)
    p.fill.color = mix(ground, ink, 0.05f * scene.most)
    canvas.drawPath(scene.faces, p.fill)
    p.edge.color = rgb(ink, 0.14f)
    canvas.drawPath(scene.edges, p.edge)
    drawInk(canvas, p, scene.liftedLines, scene.liftedFills, scene.width * (1 + LIFT * 0.5f), ink, scene.fill)
}

private fun palette(list: List<TruchetPalette>, i: Int): TruchetPalette {
    val n = list.size
    if (n == 0) return Palettes[0]
    return list[((i % n) + n) % n]
}

/**
 * A full-screen grid of Truchet tiles whose quarter-circle arcs join into continuous lines in two
 * colours. Tiles turn 90° on a spring in waves that travel out from the finger — a tap sends a
 * ring, a drag paints a trail of flips — so the pattern keeps re-knitting. A glass segmented
 * control morphs the tiles between arcs, diagonals and triangles; the swatch beside it crossfades
 * to the next palette, which grows out of the button as a circle.
 *
 * @param autoPlay Sends waves from set points, cycles the tile styles and the palettes, on a loop.
 * @param palettes Two-colour palettes the swatch button steps through.
 */
@Composable
fun TruchetWaveTiles(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    palettes: List<TruchetPalette> = Palettes,
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val density = LocalDensity.current
    val motion = remember { TruchetMotion() }
    val scene = remember { TruchetScene() }
    // Just the tiles under the controls, drawn again blurred behind their glass.
    val band = remember { TruchetScene() }
    val paints = remember { TruchetPaints() }
    var tick by remember { mutableIntStateOf(0) }
    var selected by remember { mutableIntStateOf(0) }
    // The palette the swatch offers next.
    var nextPalette by remember { mutableIntStateOf(1) }
    val bottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    var barOrigin by remember { mutableStateOf(Offset.Zero) }
    var swatchOrigin by remember { mutableStateOf(Offset.Zero) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF101A2E)),
    ) {
        val w = maxWidth.value
        val h = maxHeight.value
        motion.layout(w, h)
        val swatchX = (w - CONTROLS_WIDTH) / 2 + CONTROLS_WIDTH - SWATCH / 2
        val swatchY = h - bottom - CONTROLS_BOTTOM - SWATCH / 2

        LaunchedEffect(motion, auto, reducedMotion) {
            motion.clock = 0f
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1f / 60f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    if (dt > 0f) {
                        motion.step(dt, auto, reducedMotion)
                        if (motion.angle.size == motion.count) {
                            motion.build(scene)
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                motion.build(band, swatchY - SWATCH / 2, swatchY + SWATCH / 2)
                            }
                        }
                        selected = motion.selected
                        nextPalette = ceil(motion.palette.value - 0.001f).toInt() + 1
                        tick++
                    }
                }
            }
        }

        fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        fun ticked() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

        /** The grid in its palette, a new palette growing out of the swatch, then grain and rings. */
        fun tiles(canvas: android.graphics.Canvas, sc: TruchetScene, full: Boolean) {
            val pv = motion.palette.value
            val from = floor(pv).toInt()
            val grow = pv - from
            val a = palette(palettes, from)
            paintScene(canvas, paints, sc, a.ground, a.ink, w, h)
            if (grow > 0.001f) {
                val b = palette(palettes, from + 1)
                val reach = hypot(max(swatchX, w - swatchX), swatchY) + 20
                val r = max(0.1f, reach * grow)
                paints.circle.rewind()
                paints.circle.addCircle(swatchX, swatchY, r, Path.Direction.CW)
                canvas.save()
                canvas.clipPath(paints.circle)
                paintScene(canvas, paints, sc, b.ground, b.ink, w, h)
                canvas.restore()
                paints.rim.color = rgb(b.ink, 0.5f * (1 - grow))
                canvas.drawCircle(swatchX, swatchY, r, paints.rim)
            }
            if (!full) return
            val ink = palette(palettes, if (grow > 0.5f) from + 1 else from).ink
            canvas.save()
            canvas.scale(1 / 1.5f, 1 / 1.5f)
            canvas.drawBitmap(paints.grain(w, h), 0f, 0f, paints.grainPaint)
            canvas.restore()

            // A faint ring rides each wave front.
            for (wave in motion.waves) {
                val age = motion.elapsed - wave.at
                if (age > RING_LIFE) continue
                paints.ring.color = rgb(ink, 0.16f * (1 - age / RING_LIFE))
                canvas.drawCircle(wave.x, wave.y, age * WAVE_SPEED, paints.ring)
            }
        }

        /** The tiles under a control, for its glass; drawn in its own coordinates. */
        val behind: DrawScope.(Offset) -> Unit = { origin ->
            tick
            if (motion.angle.size == motion.count && motion.count > 0) {
                scale(density.density, density.density, pivot = Offset.Zero) {
                    translate(-origin.x / density.density, -origin.y / density.density) {
                        drawIntoCanvas { c -> tiles(c.nativeCanvas, band, false) }
                    }
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "Truchet tiles" }
                .pointerInput(motion, reducedMotion) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val px = density.density
                        val sx = down.position.x / px
                        val sy = down.position.y / px
                        var panning = false
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            val x = change.position.x / px
                            val y = change.position.y / px
                            if (!change.pressed) {
                                if (!panning && change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                    motion.touched()
                                    motion.sendWave(x, y)
                                    tap()
                                }
                                break
                            }
                            if (!panning && hypot(x - sx, y - sy) >= PAN_SLOP) {
                                panning = true
                                motion.touched()
                                motion.lastX = sx
                                motion.lastY = sy
                                motion.lastTile = motion.tileAt(sx, sy)
                                motion.flip(motion.lastTile, reducedMotion)
                                tap()
                            }
                            if (panning) {
                                motion.touched()
                                motion.paintTo(x, y, reducedMotion)
                                change.consume()
                            }
                        }
                    }
                },
        ) {
            tick
            if (motion.angle.size != motion.count || motion.count == 0) return@Canvas
            scale(density.density, density.density, pivot = Offset.Zero) {
                drawIntoCanvas { c -> tiles(c.nativeCanvas, scene, true) }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = (bottom + CONTROLS_BOTTOM).dp),
            horizontalArrangement = Arrangement.spacedBy(CONTROLS_GAP.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val barShape = RoundedCornerShape(SWATCH / 2)
            Box(
                Modifier
                    .size((SEGMENT_WIDTH * 3 + BAR_PAD * 2).dp, SWATCH.dp)
                    .onGloballyPositioned { barOrigin = it.boundsInRoot().topLeft },
            ) {
                Glass(barShape) { behind(barOrigin) }
                Box(
                    Modifier
                        .offset {
                            tick
                            IntOffset(((BAR_PAD + motion.thumb.value * SEGMENT_WIDTH) * density.density).roundToInt(), (BAR_PAD * density.density).roundToInt())
                        }
                        .size(SEGMENT_WIDTH.dp, SEGMENT_HEIGHT.dp)
                        .shadow(4.dp, CircleShape, ambientColor = Color.Black.copy(alpha = 0.25f), spotColor = Color.Black.copy(alpha = 0.25f))
                        .background(Color.White.copy(alpha = 0.9f), CircleShape),
                )
                Row(Modifier.padding(BAR_PAD.dp)) {
                    Styles.forEachIndexed { i, label ->
                        val on = i == selected
                        Box(
                            Modifier
                                .size(SEGMENT_WIDTH.dp, SEGMENT_HEIGHT.dp)
                                .semantics {
                                    role = Role.Button
                                    contentDescription = label
                                    this.selected = on
                                }
                                .pointerInput(i, reducedMotion) {
                                    awaitEachGesture {
                                        awaitFirstDown()
                                        if (i != motion.selected) {
                                            motion.touched()
                                            motion.chooseStyle(i, reducedMotion)
                                            selected = i
                                            ticked()
                                        }
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            BasicText(
                                label,
                                maxLines = 1,
                                style = TextStyle(
                                    color = if (on) Color(0xFF141821) else Color.White.copy(alpha = 0.78f),
                                    fontSize = 14.sp,
                                    fontWeight = FontWeight.SemiBold,
                                    letterSpacing = 0.1.sp,
                                ),
                            )
                        }
                    }
                }
            }
            val next = palette(palettes, nextPalette)
            Box(
                Modifier
                    .size(SWATCH.dp)
                    .onGloballyPositioned { swatchOrigin = it.boundsInRoot().topLeft }
                    .semantics {
                        role = Role.Button
                        contentDescription = "Next palette"
                    }
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown()
                            motion.touched()
                            motion.nextPalette()
                            ticked()
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Glass(CircleShape) { behind(swatchOrigin) }
                Box(Modifier.size(24.dp).background(composeColor(next.ink), CircleShape), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(12.dp).background(composeColor(next.ground), CircleShape))
                }
            }
        }
    }
}

/**
 * Dark frosted glass: the tiles under it, blurred (Android 12+), darkened, with a hairline rim.
 * Below Android 12 it is a plain translucent dark fill.
 */
@Composable
private fun Glass(shape: Shape, behind: DrawScope.() -> Unit) {
    val density = LocalDensity.current
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                this.shape = shape
                clip = true
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    val r = GLASS_BLUR * density.density
                    renderEffect = BlurEffect(r, r, TileMode.Clamp)
                }
            }
            .drawWithContent {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) behind()
            },
    )
    val tint = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) Color(0x70101218) else Color(0xB80E1016)
    Box(
        Modifier
            .fillMaxSize()
            .background(tint, shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.18f), shape),
    )
}
