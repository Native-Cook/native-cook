package com.ngocdevv.nativecook.blocks.text.weightwavewall

import android.content.res.AssetManager
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/*
 Weight Wave Wall: a wall of one word, every letter on its own weight of a variable font (Epilogue,
 100 hairline to 900 black), with bands of black letters rolling through it like surf.

 Timeline of the self-playing loop (LOOP = 7 s, two wave periods of 3.5 s):
   a letter's weight follows s = ½ + ½·sin(2π·(d·(u + 0.06·row) − t / 3.5) + 0.9·group), where u is the
   letter's place along its row (0 … 1, ten letters), rows go in groups of four whose waves run in
   opposite directions (d = ±1), and s is sharpened with smoothstep(0.18, 0.82). The weight is
   100 + 800·s, taken in 33 steps of 25; the letter's opacity is 0.42 + 0.58·s. Each letter advances by
   its own weight's width, so the black bands push their neighbours along the row. The frame at t = 0 is
   the resting state, and t = 7 s is the same frame.

 Touch: a finger drops a stone into the wall. The letters near it go black (a Gaussian of 80 dp) and
 rings run outward from it (wavelength 140 dp, one ring a second, fading over 220 dp); the touch's
 field fades in over 0.35 s (strong ease-out) and follows the finger. 2 s after the last touch the
 loop starts over from t = 0 while the touch's field fades out over 0.6 s (ease-in-out). Reduced
 motion: the wall holds the first frame and touch does nothing.
*/

/** The self-playing loop, in seconds. */
const val LOOP = 7f

private object M {
    const val PERIOD = 3.5f
    const val GROUP_ROWS = 4
    const val ROW_SLANT = 0.06f
    const val GROUP_PHASE = 0.9f
    const val LEVELS = 33
    const val TOUCH_RADIUS = 80f
    const val RING_LENGTH = 140f
    const val RING_PERIOD = 1f
    const val RING_FADE = 220f
    const val BLEND_IN = 0.35f
    const val BLEND_OUT = 0.6f
    const val IDLE = 2f
}

private val EaseInOut: Easing = CubicBezierEasing(0.42f, 0f, 0.58f, 1f)

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

private fun smoothstep(a: Float, b: Float, x: Float): Float {
    val t = ((x - a) / (b - a)).coerceIn(0f, 1f)
    return t * t * (3f - 2f * t)
}

/** A number animating towards a target, sampled against the frame clock. */
private class Tween(val from: Float, val to: Float, val start: Long, val duration: Float, val easing: Easing) {
    fun value(at: Long): Float {
        if (duration <= 0f) return to
        val t = (seconds(start, at) / duration).coerceIn(0f, 1f)
        return from + (to - from) * easing.transform(t)
    }
}

/**
 * A wall of one word on black, every letter on its own weight of a variable font, with bands of black
 * letters rolling through it like surf; a finger drops a stone and rings run out from it.
 */
@Composable
fun WeightWaveWall(
    modifier: Modifier = Modifier,
    word: String = "waves",
    kicker: String = "Focus sounds — 04",
    title: String = "Let it roll in.",
    subtitle: String = "Twenty minutes of slow surf for deep work.",
    action: String = "Play waves",
    /** Rolls the waves by itself; the pattern repeats every [LOOP] seconds. */
    autoPlay: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val face = remember(context) { WeightWaveWallFace(context.assets) }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var loopStart by remember { mutableLongStateOf(System.nanoTime()) }
    var blend by remember { mutableStateOf(Tween(0f, 0f, 0L, 0f, EaseOutStrong)) }
    var finger by remember { mutableStateOf(Offset.Zero) }
    var touchStart by remember { mutableLongStateOf(0L) }
    var touches by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val faded by remember { derivedStateOf { blend.to == 0f && blend.value(now) == 0f } }
    val paused = reduced || (!autoPlay && !dragging && faded)

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    LaunchedEffect(Unit) { loopStart = System.nanoTime() }
    // 2 s after the last touch the loop starts over while the touch's field fades out.
    LaunchedEffect(touches) {
        if (touches == 0 || dragging) return@LaunchedEffect
        delay((M.IDLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        val at = System.nanoTime()
        loopStart = at
        blend = Tween(blend.value(at), 0f, at, M.BLEND_OUT, EaseInOut)
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.Black)
            .pointerInput(Unit) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    if (reduced) return@awaitEachGesture
                    val at = System.nanoTime()
                    finger = down.position / density
                    dragging = true
                    touches++
                    touchStart = at
                    val k = blend.value(at)
                    blend = Tween(k, 1f, at, M.BLEND_IN * (1f - k), EaseOutStrong)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    while (true) {
                        val change = awaitPointerEvent().changes.firstOrNull() ?: break
                        if (!change.pressed) break
                        finger = change.position / density
                    }
                    dragging = false
                    touches++
                }
            },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding()

        Canvas(
            Modifier
                .fillMaxSize()
                .clearAndSetSemantics {},
        ) {
            val wall = face.layout(word, width)
            val t = if (reduced) 0f else seconds(loopStart, now).mod(LOOP)
            val k = if (reduced) 0f else blend.value(now)
            val since = seconds(touchStart, now)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                val letters = word.length
                val perRow = letters * 2
                val rows = ceil(height / wall.pitch).toInt() + 1
                val firstBaseline = (height - (rows - 1) * wall.pitch) / 2f + wall.xHeight / 2f
                for (row in 0 until rows) {
                    val group = row / M.GROUP_ROWS
                    val direction = if (group % 2 == 0) 1f else -1f
                    val baseline = firstBaseline + row * wall.pitch
                    val middle = baseline - wall.xHeight / 2f
                    for (column in 0 until 2) {
                        var x = wall.margin + column * (wall.column + wall.gap)
                        for (i in 0 until letters) {
                            val u = (column * letters + i + 0.5f) / perRow
                            var s = 0.5f + 0.5f * sin(2f * PI.toFloat() * (direction * (u + M.ROW_SLANT * row) - t / M.PERIOD) + M.GROUP_PHASE * group)
                            if (k > 0f) {
                                val cx = wall.margin + column * (wall.column + wall.gap) + (i + 0.5f) * wall.column / letters
                                val d = hypot(cx - finger.x, middle - finger.y)
                                val ring = (0.5f + 0.5f * cos(2f * PI.toFloat() * (d / M.RING_LENGTH - since / M.RING_PERIOD))) * exp(-d / M.RING_FADE)
                                val r = d / M.TOUCH_RADIUS
                                val touch = max(exp(-r * r), ring)
                                s += (touch - s) * k
                            }
                            val level = (smoothstep(0.18f, 0.82f, s) * (M.LEVELS - 1)).roundToInt()
                            val glyph = wall.levels[level][i]
                            paint.color = android.graphics.Color.WHITE
                            paint.alpha = ((0.42f + 0.58f * level / (M.LEVELS - 1)) * 255f).roundToInt()
                            c.save()
                            c.translate(x, baseline)
                            c.drawPath(glyph.path, paint)
                            c.restore()
                            x += glyph.advance
                        }
                    }
                }
                c.restore()
            }
        }

        // The status bar and the copy each sit on a fade to black.
        Box(
            Modifier
                .fillMaxWidth()
                .height(top + 44.dp)
                .background(Brush.verticalGradient(0f to Color.Black, 0.5f to Color.Black.copy(alpha = 0.75f), 1f to Color.Transparent)),
        )
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(440.dp + bottom)
                .background(Brush.verticalGradient(0f to Color.Transparent, 0.4f to Color.Black.copy(alpha = 0.92f), 0.55f to Color.Black)),
        )

        Column(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, bottom = bottom + if (bottom.value > 16f) 8.dp else 16.dp),
        ) {
            BasicText(kicker.uppercase(), style = TextStyle(color = Color.White.copy(alpha = 0.55f), fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.Monospace, letterSpacing = 2.sp, platformStyle = NoPadding), maxLines = 1)
            BasicText(
                title,
                style = TextStyle(color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.SemiBold, letterSpacing = (-0.8).sp, platformStyle = NoPadding),
                maxLines = 1,
                modifier = Modifier.padding(top = 12.dp),
            )
            BasicText(
                subtitle,
                style = TextStyle(color = Color.White.copy(alpha = 0.6f), fontSize = 15.sp, platformStyle = NoPadding),
                maxLines = 2,
                modifier = Modifier.padding(top = 8.dp),
            )
            Box(
                Modifier
                    .padding(top = 24.dp)
                    .fillMaxWidth()
                    .height(56.dp)
                    .background(Color.White, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onAction?.invoke() },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(action, style = TextStyle(color = Color.Black, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center, platformStyle = NoPadding), maxLines = 1)
            }
        }
    }
}

private val NoPadding = PlatformTextStyle(includeFontPadding = false)

/** The word's glyphs at each weight step, sized so two words of the heaviest weight fit a row (in dp). */
internal class WeightWaveWallFace(assets: AssetManager) {
    class Glyph(val path: Path, val advance: Float)

    class Wall(
        /** Per weight step (100 … 900 in 33 steps), per letter of the word. */
        val levels: List<List<Glyph>>,
        val size: Float,
        val xHeight: Float,
        /** Row to row. */
        val pitch: Float,
        /** The room one word gets at its heaviest, the gap between the two words and the side margin. */
        val column: Float,
        val gap: Float,
        val margin: Float = 16f,
    )

    private val faces: List<Typeface> = (0 until M.LEVELS).map { level ->
        val weight = 100f + 800f * level / (M.LEVELS - 1)
        Typeface.Builder(assets, "weightwavewall/weightwavewall_Epilogue-VF.ttf")
            .setFontVariationSettings("'wght' $weight")
            .build()
    }
    private val regular: Typeface = Typeface.Builder(assets, "weightwavewall/weightwavewall_Epilogue-VF.ttf")
        .setFontVariationSettings("'wght' 400")
        .build()
    private var wall: Wall? = null
    private var key: Pair<String, Float>? = null

    fun layout(word: String, width: Float): Wall {
        val k = word to width
        wall?.let { if (k == key) return it }
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.textSize = UNIT
        paint.typeface = faces.last()
        val heavyWidth = word.sumOf { paint.measureText(it.toString()).toDouble() }.toFloat()
        // Two heaviest words and a 0.2 em gap fill the width between 16 dp margins.
        val size = (width - 32f) / (2f * heavyWidth / UNIT + 0.2f)
        paint.textSize = size
        paint.typeface = regular
        val xBounds = RectF().also { bounds -> Path().also { paint.getTextPath("x", 0, 1, 0f, 0f, it) }.computeBounds(bounds, true) }
        val xHeight = xBounds.height().let { if (it > 0f) it else size * 0.53f }
        val levels = faces.map { face ->
            paint.typeface = face
            word.map { ch ->
                val s = ch.toString()
                val path = Path()
                paint.getTextPath(s, 0, s.length, 0f, 0f, path)
                Glyph(path, paint.measureText(s))
            }
        }
        return Wall(levels, size, xHeight, xHeight / 0.7f, heavyWidth / UNIT * size, 0.2f * size).also {
            wall = it
            key = k
        }
    }

    companion object {
        const val UNIT = 100f
    }
}
