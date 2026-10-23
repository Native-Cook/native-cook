package com.ngocdevv.nativecook.blocks.text.rgbglitchheadline

import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/*
 RGB Glitch Headline: a still, light geometric headline that is hit by bursts of signal interference.

 Timeline of the self-playing loop (LOOP = 4.8 s); the headline is clean and still outside the bursts,
 which is also the resting frame:
   0.90–1.18  burst, strength 0.7
   2.05–2.21  short burst, strength 0.45
   3.30–3.74  long burst, strength 1.0
 A burst is stepped, never eased: every 40 ms it picks a new frame from a seeded hash (the same every
 loop): 2–5 horizontal bands of the headline knocked sideways by 8–50 % of the cap height, a red copy and
 a cyan copy split apart by 4–14 % of it (added together they make white again), sometimes a 10° shear of
 the whole block, a band whose white layer drops out, and up to two thin noise lines. Each step's
 strength is the burst's strength times 0.55–1.
 The "LIVE" dot pulses with a 1.2 s period, four times a loop.

 Touch: a tap fires a 0.3 s burst (strength 0.85). Dragging tears the band under the finger sideways
 with the finger (0.9 × the drag, at most 40 % of the width) while the rest jams lightly (strength
 0.35), the colour split growing with the tear; letting go snaps it back with a 0.25 s burst. 2 s after
 the last touch the loop starts over. Reduced motion: the headline stays clean, the dot still, and touch
 does nothing.
*/

/** The self-playing loop, in seconds. */
const val LOOP = 4.8f

private class Burst(val start: Float, val duration: Float, val amount: Float, val seed: Int)

/** The loop's bursts, by their start in the loop. */
private val Bursts = listOf(
    Burst(0.9f, 0.28f, 0.7f, 11),
    Burst(2.05f, 0.16f, 0.45f, 23),
    Burst(3.3f, 0.44f, 1.0f, 37),
)

private object M {
    const val STEP = 0.04f
    const val TAP = 0.3f
    const val TAP_AMOUNT = 0.85f
    const val RELEASE = 0.25f
    const val RELEASE_AMOUNT = 0.7f
    const val JAM = 0.35f
    const val IDLE = 2f
    const val PULSE = 1.2f
    /** Where the middle of the headline sits, as a share of the screen's height. */
    const val CENTRE = 0.43f
}

private val Back = Color(0xFF050506)
private val Lift = Color(0xFF111116)
private val Red = Color(0xFFFF2A55)
private val Cyan = Color(0xFF00F0FF)
private val Ink = Color.White

private class Tear(val y: Float, val dx: Float, val start: Long)
private class Shot(val start: Long, val duration: Float, val amount: Float, val seed: Int)

private fun seconds(from: Long, to: Long) = (to - from) / 1e9f

private fun stepFrame(seed: Int, e: Float, amount: Float, block: RgbGlitchHeadlineType.Block): RgbGlitchHeadlineFrame {
    val step = (e / M.STEP).toInt()
    val strength = amount * (0.55f + 0.45f * RgbGlitchHeadlineFrame.random(seed * 977 + step))
    return RgbGlitchHeadlineFrame.make(seed, step, strength, block)
}

/**
 * A still, light geometric headline hit by stepped bursts of signal interference: bands knocked
 * sideways, a red and a cyan copy split apart, a shear, scan lines. Tap to interfere, drag to tear.
 */
@Composable
fun RgbGlitchHeadline(
    modifier: Modifier = Modifier,
    lines: List<String> = listOf("NOTHING", "IS", "STATIC"),
    channel: String = "CH 07",
    programme: String = "Night broadcast",
    live: String = "Live",
    subtitle: String = "Season two airs Friday at midnight.",
    action: String = "Remind me",
    hint: String = "Tap to interfere · drag to tear",
    /** Plays the interference by itself, every [LOOP] seconds round. */
    autoPlay: Boolean = false,
    onAction: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val type = remember(context) {
        RgbGlitchHeadlineType(Typeface.createFromAsset(context.assets, "rgbglitchheadline/rgbglitchheadline_Jost-Light.ttf"))
    }
    val painter = remember { GlitchPainter() }
    val playing by rememberUpdatedState(autoPlay)
    val heading by rememberUpdatedState(lines)

    var now by remember { mutableLongStateOf(System.nanoTime()) }
    var loopStart by remember { mutableLongStateOf(System.nanoTime()) }
    var shot by remember { mutableStateOf<Shot?>(null) }
    var tear by remember { mutableStateOf<Tear?>(null) }
    /** From the first touch until the loop starts over. */
    var held by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var dragging by remember { mutableStateOf(false) }
    val paused = reduced || (!autoPlay && shot == null && tear == null)

    fun glitch(at: Long, block: RgbGlitchHeadlineType.Block): RgbGlitchHeadlineFrame? {
        if (reduced) return null
        tear?.let { t ->
            val step = (seconds(t.start, at) / M.STEP).toInt()
            val frame = RgbGlitchHeadlineFrame.make(101, step, M.JAM, block)
            val half = block.capHeight * 0.28f
            frame.bands += RgbGlitchHeadlineFrame.Band(t.y - half, t.y + half, t.dx)
            val split = min(abs(t.dx) * 0.12f, block.capHeight * 0.2f)
            frame.split = if (t.dx < 0f) -max(abs(frame.split), split) else max(abs(frame.split), split)
            return frame
        }
        shot?.let { s ->
            val e = seconds(s.start, at)
            if (e >= 0f && e < s.duration) return stepFrame(s.seed, e, s.amount, block)
        }
        if (!playing || held) return null
        val e = seconds(loopStart, at).mod(LOOP)
        for (b in Bursts) if (e >= b.start && e < b.start + b.duration) return stepFrame(b.seed, e - b.start, b.amount, block)
        return null
    }

    fun fire(seed: Int, duration: Float, amount: Float) {
        shot = Shot(System.nanoTime(), duration, amount, seed)
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    LaunchedEffect(paused) {
        if (paused) return@LaunchedEffect
        while (isActive) withFrameNanos { now = System.nanoTime() }
    }
    LaunchedEffect(Unit) { loopStart = System.nanoTime() }
    // 2 s after the last touch the loop starts over from its clean frame.
    LaunchedEffect(touches) {
        if (touches == 0 || dragging) return@LaunchedEffect
        delay((M.IDLE * 1000).toLong())
        if (dragging) return@LaunchedEffect
        shot = null
        held = false
        loopStart = System.nanoTime()
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Back)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val block = type.block(lines, width, height * M.CENTRE)
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = max(insets.calculateBottomPadding().value, 16f)

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics {
                    heading()
                    contentDescription = heading.joinToString(" ")
                }
                .pointerInput(width, height) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        dragging = true
                        held = true
                        touches++
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        var tick = 0
                        while (true) {
                            val change = awaitPointerEvent().changes.firstOrNull() ?: break
                            if (!change.pressed) break
                            if (reduced) continue
                            val tx = (change.position.x - down.position.x) / density
                            val ty = (change.position.y - down.position.y) / density
                            if (hypot(tx, ty) <= 6f && tear == null) continue
                            val limit = width * 0.4f
                            val dx = (tx * 0.9f).coerceIn(-limit, limit)
                            tear = Tear(change.position.y / density, dx, tear?.start ?: System.nanoTime())
                            val notch = (dx / 40f).toInt()
                            if (notch != tick) {
                                tick = notch
                                view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            }
                            change.consume()
                        }
                        dragging = false
                        touches++
                        if (!reduced) {
                            val released = tear != null
                            tear = null
                            fire(300 + touches, if (released) M.RELEASE else M.TAP, if (released) M.RELEASE_AMOUNT else M.TAP_AMOUNT)
                        }
                    }
                },
        ) {
            val frame = glitch(now, block)
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density, density)
                painter.draw(c, width, height, block, frame)
                c.restore()
            }
        }

        // Top bar.
        Row(
            Modifier
                .fillMaxWidth()
                .offset(y = top + 16.dp)
                .height(20.dp)
                .padding(horizontal = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(channel.uppercase(), style = mono(12f, FontWeight.SemiBold, Ink.copy(alpha = 0.75f)), maxLines = 1)
            Spacer(Modifier.width(10.dp))
            BasicText(programme.uppercase(), style = mono(12f, FontWeight.Normal, Ink.copy(alpha = 0.38f)), maxLines = 1)
            Spacer(Modifier.weight(1f).width(12.dp))
            Box(
                Modifier
                    .size(7.dp)
                    .graphicsLayer {
                        alpha = if (reduced) 1f else 0.35f + 0.65f * (0.5f + 0.5f * cos(2f * PI.toFloat() * seconds(loopStart, now) / M.PULSE))
                    }
                    .background(Red, CircleShape),
            )
            Spacer(Modifier.width(10.dp))
            BasicText(live.uppercase(), style = mono(12f, FontWeight.SemiBold, Red), maxLines = 1)
        }

        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (block.bottom + block.capHeight * 0.5f + 28f).dp)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BasicText(subtitle, style = plain(15f, FontWeight.Normal, Ink.copy(alpha = 0.6f)), maxLines = 2)
            Box(
                Modifier
                    .size(168.dp, 48.dp)
                    .border(1.dp, Ink.copy(alpha = 0.4f), CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                        if (!reduced) fire(300 + touches + 1, M.TAP, M.TAP_AMOUNT)
                        touches++
                        held = true
                        onAction?.invoke()
                    },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(action, style = plain(15f, FontWeight.Medium, Ink, 0.4f), maxLines = 1)
            }
        }

        BasicText(
            hint.uppercase(),
            style = mono(11f, FontWeight.Medium, Ink.copy(alpha = 0.34f), 1.6f).copy(textAlign = TextAlign.Center),
            maxLines = 1,
            modifier = Modifier
                .fillMaxWidth()
                .offset(y = (height - bottom - 24f - 14f).dp),
        )
    }
}

private fun plain(size: Float, weight: FontWeight, color: Color, tracking: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    textAlign = TextAlign.Center,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun mono(size: Float, weight: FontWeight, color: Color, tracking: Float = 2f) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    fontFamily = FontFamily.Monospace,
    letterSpacing = tracking.sp,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** Draws the backdrop, the headline (clean or one glitch frame) and its scan lines, in dp. */
private class GlitchPainter {
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    private val add = PorterDuffXfermode(PorterDuff.Mode.ADD)
    private val shear = Matrix()
    private val scan = Path()
    private var backKey: Any? = null
    private var backShader: Shader? = null

    fun draw(c: android.graphics.Canvas, width: Float, height: Float, block: RgbGlitchHeadlineType.Block, frame: RgbGlitchHeadlineFrame?) {
        val key = Triple(width, height, block.centreY)
        if (key != backKey) {
            backKey = key
            backShader = RadialGradient(block.centreX, block.centreY, height * 0.7f, Lift.toArgb(), Back.toArgb(), Shader.TileMode.CLAMP)
        }
        fill.xfermode = null
        fill.shader = backShader
        c.drawRect(0f, 0f, width, height, fill)
        fill.shader = null

        val cap = block.capHeight
        val top = block.top - cap * 0.5f
        val bottom = block.bottom + cap * 0.5f
        if (frame != null) {
            val strips = frame.strips(top, bottom)
            val layers = listOf(Triple(Red, frame.split, true), Triple(Cyan, -frame.split, true), Triple(Ink, 0f, false))
            for ((colour, shift, additive) in layers) {
                fill.color = colour.toArgb()
                fill.xfermode = if (additive) add else null
                for (strip in strips) {
                    if (strip.hidden && !additive) continue
                    c.save()
                    c.clipRect(0f, strip.y0, width, strip.y1)
                    c.translate(strip.dx + frame.jolt + shift, 0f)
                    if (frame.shear != 0f) {
                        // x' = x − shear · (y − centre): the tops lean right.
                        shear.setValues(floatArrayOf(1f, -frame.shear, frame.shear * block.centreY, 0f, 1f, 0f, 0f, 0f, 1f))
                        c.concat(shear)
                    }
                    c.drawPath(block.path, fill)
                    c.restore()
                }
            }
            fill.xfermode = null
            for ((y, alpha) in frame.noise) {
                fill.color = Ink.copy(alpha = alpha).toArgb()
                c.drawRect(0f, y, width, y + 1f, fill)
                fill.color = Cyan.copy(alpha = alpha * 0.6f).toArgb()
                c.drawRect(0f, y + 2f, width, y + 3f, fill)
            }
        } else {
            fill.color = Ink.toArgb()
            c.drawPath(block.path, fill)
        }

        // Scan lines over the headline: a 1 dp dark line every 4 dp.
        scan.reset()
        var y = top
        while (y < bottom) {
            scan.addRect(0f, y, width, y + 1f, Path.Direction.CW)
            y += 4f
        }
        fill.color = Back.copy(alpha = 0.2f).toArgb()
        c.drawPath(scan, fill)
    }
}
