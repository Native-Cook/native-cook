package com.ngocdevv.nativecook.blocks.text.springletters

import android.graphics.BlurMaskFilter
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// A pastel stage: a lilac wall, a peach floor, candy letters with a chunky extruded side.
private const val WALL_TOP = 0xFFEFE8FB.toInt()
private const val WALL_LOW = 0xFFE4DAF6.toInt()
private const val FLOOR_NEAR = 0xFFF9DCCF.toInt()
private const val FLOOR_FAR = 0xFFFDEEE6.toInt()
private const val HINT = 0xFF9A88AB.toInt()
private val Faces = intArrayOf(
    0xFFFF6B5B.toInt(), 0xFFFFA13A.toInt(), 0xFFFFCF3D.toInt(), 0xFF3FC98D.toInt(),
    0xFF4B8CFF.toInt(), 0xFFA26CFF.toInt(), 0xFFFF7FB3.toInt(), 0xFF2FC4D6.toInt(),
)
// The extruded side darkens towards this plum.
private const val SIDE_SHADE = 0xFF3B1D48.toInt()
private const val SHADOW = 0xFF5B3A6B.toInt()

// Type: a heavy condensed sans from the system, sized so the widest word fits.
private const val MAX_SIZE = 150.0
private const val SIDE_MARGIN = 22.0
// Gap between letters at rest (share of the type size).
private const val GAP = 0.05
// The extruded side: layers, and each layer's step down and to the right (dp).
private const val DEPTH = 8
private const val STEP_X = 0.75f
private const val STEP_Y = 1.05f
// The floor line, as a share of the screen's height, and the wall's foot just behind it.
private const val FLOOR_AT = 0.6
private const val HORIZON_BEHIND = 0.36
// A tap: lifted within this long, having moved less than this far (dp). Two make a double tap
// when the second comes down within DOUBLE_TAP_MS of the first lifting.
private const val TAP_MS = 500L
private const val TAP_SLOP = 20f
private const val DOUBLE_TAP_MS = 280L

/** The system's condensed sans at its heaviest (the RN version asks Skia for weight 800). */
private fun heavyCondensed(): Typeface {
    val base = Typeface.create("sans-serif-condensed", Typeface.NORMAL)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        Typeface.create(base, 800, false)
    } else {
        Typeface.create("sans-serif-condensed", Typeface.BOLD)
    }
}

/** Outlines of every letter of every word, from the font, and the boxes the physics needs. */
private class Letters(width: Double) {
    val size: Double
    val gap: Double
    // Per word: each letter's outline, the middle of its outline, and its box.
    val paths: List<List<Path>>
    val centres: List<DoubleArray>
    val boxes: List<DoubleArray>

    init {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = heavyCondensed() }
        paint.textSize = 100f
        var widest = 0.0
        for (word in Words) {
            var total = GAP * 100 * (word.length - 1)
            for (letter in word) total += bounds(outline(paint, letter))[2]
            widest = max(widest, total)
        }
        // Never a zero, negative or infinite size, even before the first layout.
        size = if (widest > 0) max(8.0, min(MAX_SIZE, (100 * (width - SIDE_MARGIN * 2)) / widest)) else MAX_SIZE
        gap = size * GAP
        paint.textSize = size.toFloat()
        val p = ArrayList<List<Path>>()
        val c = ArrayList<DoubleArray>()
        val b = ArrayList<DoubleArray>()
        for (word in Words) {
            val outlines = word.map { outline(paint, it) }
            val boxes = outlines.map { bounds(it) }
            p.add(outlines)
            c.add(DoubleArray(word.length * 2) { boxes[it / 2].let { r -> if (it % 2 == 0) r[0] + r[2] / 2 else r[1] + r[3] / 2 } })
            b.add(DoubleArray(word.length * 2) { boxes[it / 2][2 + it % 2] })
        }
        paths = p
        centres = c
        boxes = b
    }

    companion object {
        /**
         * The outline of one letter, its baseline on y = 0, like Skia's `MakeFromText`. A variable
         * font's glyph can be built of overlapping contours, whose seams the gloss and shade strokes
         * would trace: the union with nothing merges them into one outline.
         */
        fun outline(paint: Paint, letter: Char): Path {
            val raw = Path()
            paint.getTextPath(letter.toString(), 0, 1, 0f, 0f, raw)
            return Path().apply { if (!op(raw, Path(), Path.Op.UNION)) set(raw) }
        }

        /** The tight bounds of a path (x, y, width, height): of its curves, not its control points. */
        fun bounds(path: Path): DoubleArray {
            val points = path.approximate(0.1f)
            if (points.isEmpty()) return doubleArrayOf(0.0, 0.0, 60.0, 60.0)
            var minX = Float.MAX_VALUE
            var minY = Float.MAX_VALUE
            var maxX = -Float.MAX_VALUE
            var maxY = -Float.MAX_VALUE
            var i = 0
            while (i < points.size) {
                minX = min(minX, points[i + 1])
                maxX = max(maxX, points[i + 1])
                minY = min(minY, points[i + 2])
                maxY = max(maxY, points[i + 2])
                i += 3
            }
            return doubleArrayOf(minX.toDouble(), minY.toDouble(), (maxX - minX).toDouble(), (maxY - minY).toDouble())
        }
    }
}

/**
 * A big bold word standing on a pastel floor, each letter a rigid body on springs: one pulls it
 * back over its slot, one stands it upright, and links to its neighbours chain the word together.
 * Drag a letter and it follows with a lag, swinging, the ones beside it tugged along; let go and
 * it flies, lands, bounces and springs home. Flick up from the floor and every letter jumps.
 * Double tap for the next word: the letters shrink and turn away, and new ones drop in.
 *
 * @param autoPlay Drags and throws letters, flicks the floor and changes the word, on a loop. Off by default.
 */
@Composable
fun SpringLetters(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    hint: String = "Drag a letter · flick the floor · double tap",
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val sim = remember { LettersSim(auto, reducedMotion) }
    val painter = remember { LettersPainter(screen.density) }
    // Bumped once a frame so the live canvas redraws.
    var frame by remember { mutableLongStateOf(0L) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(WALL_TOP))
            .semantics { contentDescription = "A word of letters on springs. Drag a letter, flick the floor, or double tap for the next word." },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val letters = remember(width) { Letters(width) }
        // JavaScript's Math.round.
        val floorY = floor(height * FLOOR_AT + 0.5)

        LaunchedEffect(auto, reducedMotion, letters, floorY, safeTop) {
            sim.looping = auto
            sim.still = reducedMotion
            sim.clock = 0.0
            sim.floor = floorY
            sim.left = 0.0
            sim.right = width
            sim.ceiling = safeTop.toDouble()
            sim.placeWord(0, letters.boxes, letters.gap, false)
        }

        LaunchedEffect(letters) {
            var last = -1L
            var grabs = sim.userGrabs
            var flicks = sim.userFlicks
            var swaps = sim.userSwaps
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 1.0 / 60 else (now - last) / 1e9
                    last = now
                    sim.step(dt, letters.boxes, letters.gap)
                    if (sim.userGrabs > grabs) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (sim.userFlicks > flicks) view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    if (sim.userSwaps > swaps) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    grabs = sim.userGrabs
                    flicks = sim.userFlicks
                    swaps = sim.userSwaps
                    frame = now
                }
            }
        }

        // The wall and the floor never move: drawn once.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind { inDp { painter.drawStage(it, width, height, floorY, letters.size * 0.72) } },
        )
        // The letters, their shadows, the fingertip and the ring of a double tap.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    if (frame < 0) return@drawBehind
                    inDp { painter.drawLive(it, sim, letters, reducedMotion) }
                }
                .pointerInput(sim, letters) {
                    val tracker = VelocityTracker()
                    var lastTapUp = -1L
                    awaitEachGesture {
                        // RN runs a pan from the first touch beside a double tap.
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        tracker.resetTracking()
                        tracker.addPointerInputChange(down)
                        sim.fingerDown(down.position.x / d.toDouble(), down.position.y / d.toDouble())
                        var travel = 0f
                        var upAt = down.position
                        var upTime = -1L
                        var finished = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                tracker.addPointerInputChange(change)
                                travel = max(travel, hypot(change.position.x - down.position.x, change.position.y - down.position.y) / d)
                                if (!change.pressed) {
                                    upAt = change.position
                                    upTime = change.uptimeMillis
                                    break
                                }
                                change.consume()
                                val v = tracker.calculateVelocity()
                                sim.fingerMove(change.position.x / d.toDouble(), change.position.y / d.toDouble(), v.x / d.toDouble(), v.y / d.toDouble())
                            }
                            finished = true
                        } finally {
                            // Cancelled mid-drag (the layout changed, the block closed): let the letter go.
                            if (!finished) sim.fingerUp(0.0, 0.0)
                        }
                        val v = if (upTime >= 0) tracker.calculateVelocity() else null
                        sim.fingerUp((v?.x ?: 0f) / d.toDouble(), (v?.y ?: 0f) / d.toDouble())
                        val tap = upTime >= 0 && upTime - down.uptimeMillis <= TAP_MS && travel < TAP_SLOP
                        if (!tap) {
                            lastTapUp = -1L
                        } else if (lastTapUp >= 0 && down.uptimeMillis - lastTapUp <= DOUBLE_TAP_MS) {
                            lastTapUp = -1L
                            sim.doubleTap(upAt.x / d.toDouble(), upAt.y / d.toDouble(), letters.boxes, letters.gap)
                        } else {
                            lastTapUp = upTime
                        }
                    }
                },
        )

        BasicText(
            hint,
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 28f).dp),
            style = TextStyle(
                color = Color(HINT),
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.2.sp,
                textAlign = TextAlign.Center,
                fontFamily = FontFamily.SansSerif,
            ),
        )
    }
}

/** Runs [block] on the native canvas scaled to dp, like the Skia canvas of the RN version. */
private inline fun DrawScope.inDp(block: (Canvas) -> Unit) {
    drawIntoCanvas { canvas ->
        val c = canvas.nativeCanvas
        c.save()
        c.scale(density, density)
        block(c)
        c.restore()
    }
}

/** Two colours mixed per channel and rounded, like the RN `mix`; [alpha] 0…1. */
private fun mix(a: Int, b: Int, t: Double, alpha: Double = 1.0): Int {
    fun channel(shift: Int): Int {
        val x = (a shr shift) and 0xff
        val y = (b shr shift) and 0xff
        return floor(x + (y - x) * t + 0.5).toInt()
    }
    val al = (alpha * 255).roundToInt().coerceIn(0, 255)
    return (al shl 24) or (channel(16) shl 16) or (channel(8) shl 8) or channel(0)
}

/** Paints, blurs and drawing for a canvas in dp. */
private class LettersPainter(private val density: Float) {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND
    }
    private val blurs = HashMap<Int, BlurMaskFilter>()

    /**
     * A Skia blur of [sigma] dp as Android's radius. Hardware canvases scale mask filters with the
     * canvas and only honour them from API 28; sigmas are rounded to a quarter so they can be reused.
     */
    private fun blur(sigma: Double): BlurMaskFilter? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return null
        val key = (sigma * 4).roundToInt()
        return blurs.getOrPut(key) {
            val px = key / 4f * density
            BlurMaskFilter(((px - 0.5f) / 0.57735f).coerceAtLeast(0.01f) / density, BlurMaskFilter.Blur.NORMAL)
        }
    }

    /** The stage: wall, the soft shadow where wall meets floor, and the floor. Painted once. */
    fun drawStage(c: Canvas, width: Double, height: Double, floor: Double, letterHeight: Double) {
        val w = width.toFloat()
        val h = height.toFloat()
        val horizon = (floor - letterHeight * HORIZON_BEHIND).toFloat()
        fun shaded(shader: Shader): Paint = fill.apply {
            maskFilter = null
            color = 0xFF000000.toInt()
            this.shader = shader
        }
        c.drawRect(0f, 0f, w, horizon, shaded(LinearGradient(0f, 0f, 0f, horizon, WALL_TOP, WALL_LOW, Shader.TileMode.CLAMP)))
        c.drawRect(0f, horizon, w, h, shaded(LinearGradient(0f, horizon, 0f, h, FLOOR_FAR, FLOOR_NEAR, Shader.TileMode.CLAMP)))
        // A soft fold of shadow where the wall meets the floor, and a line of light on the floor's edge.
        c.drawRect(0f, horizon - 26f, w, horizon, shaded(LinearGradient(0f, horizon - 26f, 0f, horizon, 0x005B3A6B, 0x1F5B3A6B, Shader.TileMode.CLAMP)))
        fill.shader = null
        fill.color = 0xB3FFFFFF.toInt()
        c.drawRect(0f, horizon, w, horizon + 1f, fill)
        // A pool of light on the floor under the word, squashed into an ellipse.
        val cy = ((floor + 30) / 0.35).toFloat()
        val r = w * 0.7f
        c.save()
        c.scale(1f, 0.35f)
        c.drawCircle(w / 2, cy, r, shaded(RadialGradient(w / 2, cy, r, 0x73FFFFFF, 0x00FFFFFF, Shader.TileMode.CLAMP)))
        c.restore()
        fill.shader = null
    }

    /** The soft shadow a letter casts on the floor: tighter and darker the closer it is. */
    private fun drawShadow(c: Canvas, x: Double, halfW: Double, lift: Double, floor: Double, scale: Double, alpha: Double) {
        val far = min(1.0, max(0.0, lift) / 320)
        fill.shader = null
        fill.color = mix(SHADOW, SHADOW, 0.0, 0.26 * (1 - far * 0.8) * alpha)
        fill.maskFilter = blur(5 + far * 14)
        val w = halfW * scale * (1.05 - far * 0.35)
        c.drawOval((x + 5 - w).toFloat(), (floor - 4).toFloat(), (x + 5 + w).toFloat(), (floor - 4 + 12 + far * 6).toFloat(), fill)
        if (lift < 14) {
            // Contact: a tight dark line where the letter meets the floor.
            fill.color = mix(SHADOW, SHADOW, 0.0, 0.22 * (1 - lift / 14) * alpha)
            fill.maskFilter = blur(2.5)
            val left = x - halfW * scale * 0.9
            c.drawOval(left.toFloat(), (floor - 2).toFloat(), (left + halfW * scale * 1.8 + 6).toFloat(), (floor + 3).toFloat(), fill)
        }
        fill.maskFilter = null
    }

    /**
     * One candy letter: an extruded side in deepening shades, then the face, lit from the top left
     * with a gloss along its upper edges and a shade along its lower ones.
     */
    private fun drawLetter(c: Canvas, path: Path, cx: Double, cy: Double, x: Double, y: Double, angle: Double, scale: Double, alpha: Double, face: Int, halfH: Double) {
        val degrees = Math.toDegrees(angle).toFloat()
        fun place(dx: Float, dy: Float) {
            c.translate(x.toFloat() + dx, y.toFloat() + dy)
            c.rotate(degrees)
            c.scale(scale.toFloat(), scale.toFloat())
            c.translate(-cx.toFloat(), -cy.toFloat())
        }
        fill.shader = null
        fill.maskFilter = null
        for (k in DEPTH downTo 1) {
            fill.color = mix(face, SIDE_SHADE, 0.38 + (0.22 * k) / DEPTH, alpha)
            c.save()
            place(k * STEP_X, k * STEP_Y)
            c.drawPath(path, fill)
            c.restore()
        }
        c.save()
        place(0f, 0f)
        val top = (cy - halfH).toFloat()
        fill.shader = LinearGradient(
            0f, top, 0f, top + (halfH * 2).toFloat(),
            intArrayOf(mix(face, 0xFFFFFFFF.toInt(), 0.28), mix(face, 0xFFFFFFFF.toInt(), 0.0), mix(face, SIDE_SHADE, 0.08)),
            floatArrayOf(0f, 0.55f, 1f),
            Shader.TileMode.CLAMP,
        )
        // The paint's colour stays opaque so the gradient is not faded; its alpha carries the fade.
        fill.color = 0xFF000000.toInt()
        fill.alpha = (alpha * 255).roundToInt().coerceIn(0, 255)
        c.drawPath(path, fill)
        fill.shader = null
        c.clipPath(path)
        stroke.strokeWidth = 7f
        stroke.color = mix(face, SIDE_SHADE, 0.55, 0.35 * alpha)
        stroke.maskFilter = blur(3.0)
        c.save()
        c.translate(3f, 3.5f)
        c.drawPath(path, stroke)
        c.restore()
        stroke.strokeWidth = 3.5f
        stroke.color = mix(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0.0, 0.55 * alpha)
        stroke.maskFilter = blur(1.6)
        c.save()
        c.translate(-2f, -2.4f)
        c.drawPath(path, stroke)
        c.restore()
        stroke.maskFilter = null
        c.restore()
    }

    fun drawLive(c: Canvas, s: LettersSim, letters: Letters, still: Boolean) {
        // Shadows first, so no letter is darkened by another's.
        for (i in 0 until s.count) {
            if (s.wait[i] > 0) continue
            drawShadow(c, s.x[i], s.halfW[i], s.floor - (s.y[i] + s.halfH[i]), s.floor, 1.0, if (s.still) min(1.0, s.arrive / 0.3) else 1.0)
        }
        if (s.leaveAge >= 0) {
            val boxes = letters.boxes[s.leftWord]
            for (i in 0 until s.leftCount) {
                val e = s.leaving(i)
                drawShadow(c, s.lx[i], boxes[i * 2] / 2, s.floor - s.ly[i] - boxes[i * 2 + 1] / 2, s.floor, 1 - 0.5 * e, 1 - e)
            }
        }

        // The word leaving: each letter shrinks, turns and fades, one after another.
        if (s.leaveAge >= 0) {
            for (i in 0 until s.leftCount) {
                val e = s.leaving(i)
                if (e >= 1) continue
                val ease = e * e
                val turn = if (still) 0.0 else (if (i % 2 == 0) -1 else 1) * 0.9 * ease
                val shrink = if (still) 1.0 else 1 - 0.55 * ease
                val rise = if (still) 0.0 else 46 * ease
                drawLetter(
                    c,
                    letters.paths[s.leftWord][i],
                    letters.centres[s.leftWord][i * 2],
                    letters.centres[s.leftWord][i * 2 + 1],
                    s.lx[i],
                    s.ly[i] - rise,
                    s.la[i] + turn,
                    shrink,
                    1 - e,
                    Faces[i % Faces.size],
                    letters.boxes[s.leftWord][i * 2 + 1] / 2,
                )
            }
        }

        // The word, the held letter drawn last so it is on top.
        val shown = if (s.still) min(1.0, s.arrive / 0.3) else 1.0
        for (pass in 0 until 2) {
            for (i in 0 until s.count) {
                if ((i == s.held) != (pass == 1)) continue
                if (s.wait[i] > 0) continue
                drawLetter(
                    c,
                    letters.paths[s.word][i],
                    letters.centres[s.word][i * 2],
                    letters.centres[s.word][i * 2 + 1],
                    s.x[i],
                    s.y[i],
                    s.a[i],
                    1.0,
                    shown,
                    Faces[i % Faces.size],
                    s.halfH[i],
                )
            }
        }

        // The fingertip, real or scripted, and the ring of a double tap.
        if (s.down) {
            fill.shader = null
            fill.color = 0x61FFFFFF
            c.drawCircle(s.fx.toFloat(), s.fy.toFloat(), 22f, fill)
            stroke.strokeWidth = 1.5f
            stroke.color = 0xCCFFFFFF.toInt()
            c.drawCircle(s.fx.toFloat(), s.fy.toFloat(), 22f, stroke)
        }
        if (s.tapAge >= 0) {
            val t = s.tapAge / 0.6
            stroke.strokeWidth = 2f
            stroke.color = mix(0xFFFFFFFF.toInt(), 0xFFFFFFFF.toInt(), 0.0, 0.85 * (1 - t))
            c.drawCircle(s.tapX.toFloat(), s.tapY.toFloat(), (18 + 50 * (1 - (1 - t) * (1 - t))).toFloat(), stroke)
        }
    }
}
