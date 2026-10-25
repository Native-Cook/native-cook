package com.ngocdevv.nativecook.blocks.onboarding.constellationstepswelcome

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationEndReason
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/*
 Constellation Steps Welcome — the onboarding of "Orrery", a stargazing app, ported from
 ConstellationStepsWelcome.tsx. A deep night sky (one AGSL shader: navy gradient, a Milky Way of
 fbm dust with a dark rift, two twinkling layers of hashed stars; points and gradients below
 Android 13) turns round the pole above the screen. Each step brings a constellation to the centre
 and draws it: its stars bloom as glowing lines reach them, the brightest is named, and a faint
 line drawing of its figure fades in. Constellations are android.graphics drawing in the same
 layer (ConstellationStepsWelcomeArt.kt); the text is Compose.

 autoPlay (LOOP = 10 s, ConstellationStepsWelcomeMotion.kt):
   0.0 – 1.6   rest: step 1, Cygnus's stars waiting.
   1.6 – 3.6   Cygnus is drawn, Deneb named, the swan fades in.
   3.6 – 4.4   the sky turns 48° to Cassiopeia; the text rolls; 4.4 – 6.2 it is drawn.
   6.2 – 7.0   on to Orion; 7.0 – 8.4 drawn.
   8.4 – 9.4   the sky turns back home while every line and figure fades. Rest to 10.0.

 Touch: swipe sideways and the sky turns with the finger (rubber-banding past the ends); let go
 and it settles on the nearest step (spring 800 ms, damping 0.85, carrying the flick), whose
 constellation then draws itself (1.4 s). "Continue" steps on (the last one starts over). A tick
 per step. 2 s after the last touch the sky turns home and fades (900 ms) and the loop restarts.
 Reduced motion: step 1 drawn, no loop and no twinkle; steps change with short fades.
 */

private val Ink = Color(0xFFF3EEE4)
private val Muted = Color(0xFFF3EEE4).copy(alpha = 0.62f)
private val Gold = Color(0xFFF2D48A)
internal val SkyBackground = Color(0xFF070A1A)
private const val PANEL_H = 256f
private const val IDLE_MS = 2000L
private const val RETURN_MS = 900
private const val DRAW_MS = 1400
private val EaseInOut = CubicBezierEasing(0.77f, 0f, 0.175f, 1f)
private val QuadInOut = Easing { t -> if (t < 0.5f) 2f * t * t else 1f - (-2f * t + 2f) * (-2f * t + 2f) / 2f }

private val Titles = listOf("Meet the Swan", "Find the Queen", "Follow the Hunter")
private val Bodies = listOf(
    "Cygnus flies down the Milky Way. Its brightest star, Deneb, marks the tail.",
    "Five bright stars draw Cassiopeia's W, circling the pole all year.",
    "Orion's belt of three stars points the way to the brightest star of all.",
)
private val Captions = listOf("CYGNUS · HIGH IN THE EAST", "CASSIOPEIA · NORTH, ALL NIGHT", "ORION · RISING AFTER 22:00")

private fun sspring(ms: Int, damping: Float) = (2.0 * PI / (ms / 1000.0)).let { spring<Float>(dampingRatio = damping, stiffness = (it * it).toFloat()) }

private class SkyState(auto: Boolean, reduced: Boolean) {
    val t = mutableFloatStateOf(0f)
    var manual by mutableStateOf(!auto)
    val frame = mutableIntStateOf(0)
    var mtime = 0f
    val theta = Animatable(0f)
    val drawn = arrayOf(Animatable(if (reduced) 1f else 0f), Animatable(0f), Animatable(0f))
    val fade = Animatable(1f)
    var wake by mutableIntStateOf(0)
    var lastStep = -1
    var resume: Job? = null
    private val pose = SkyPose()

    /** Reads state: call inside draw / graphicsLayer lambdas. */
    fun pose(): SkyPose {
        if (!manual) return skyLoopPose(t.floatValue, pose)
        frame.intValue
        val th = theta.value
        pose.theta = th
        for (k in 0 until STEPS) pose.drawn[k] = drawn[k].value
        pose.fade = fade.value
        pose.step = max(0f, min(STEPS - 1f, th / StepAngle))
        pose.time = mtime
        return pose
    }
}

private fun Density.dpOffset(x: Float, y: Float) = IntOffset(x.dp.roundToPx(), y.dp.roundToPx())

@Composable
fun ConstellationStepsWelcome(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val density = LocalDensity.current
    val d = density.density
    val scope = rememberCoroutineScope()
    val state = remember(auto, reduced) { SkyState(auto, reduced) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / d
    val safeBottom = insets.getBottom(density) / d
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(CONSTELLATION_SKY_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize().background(SkyBackground)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val panelTop = height - safeBottom - PANEL_H
        val g = remember(width, height, safeTop, panelTop) { SkyGeo(width, height, safeTop + 84f, panelTop) }
        val radius = g.cy - g.py
        val painter = remember(g, d) { ConstellationPainter(g, d) }

        // The no-shader sky's stars are worked out off the main thread, after the size settles.
        var fallback by remember { mutableStateOf<ConstellationSkyFallback?>(null) }
        if (shader == null) {
            LaunchedEffect(width, height) {
                delay(120)
                fallback = withContext(Dispatchers.Default) { ConstellationSkyFallback(SkyGeo(width, height, safeTop + 84f, panelTop)) }
            }
        }

        fun drawStep(k: Int) {
            val a = state.drawn[k]
            if (a.value >= 0.999f) return
            scope.launch { a.animateTo(1f, if (reduced) tween(250) else tween(DRAW_MS, easing = QuadInOut)) }
        }

        fun settleOn(k: Int, velocity: Float) {
            scope.launch {
                if (reduced) state.theta.animateTo(k * StepAngle, tween(250)) else state.theta.animateTo(k * StepAngle, sspring(800, 0.85f), initialVelocity = velocity)
            }
            drawStep(k)
        }

        fun backToRest() {
            scope.launch { state.fade.animateTo(0f, tween(RETURN_MS - 200)) }
            scope.launch {
                val r = state.theta.animateTo(0f, tween(RETURN_MS, easing = EaseInOut))
                if (r.endReason == AnimationEndReason.Finished && auto) {
                    for (a in state.drawn) a.snapTo(0f)
                    state.fade.snapTo(1f)
                    state.t.floatValue = 0f
                    state.manual = false
                }
            }
        }

        fun scheduleResume(ms: Long) {
            if (!auto) return
            state.resume?.cancel()
            state.resume = scope.launch {
                delay(ms)
                state.resume = null
                backToRest()
            }
        }

        fun takeOver() {
            state.resume?.cancel()
            state.resume = null
            if (!state.manual) {
                val p = skyLoopPose(state.t.floatValue, SkyPose())
                val th = p.theta
                val dr = p.drawn.copyOf()
                val fd = p.fade
                state.mtime = state.t.floatValue
                state.lastStep = (th / StepAngle).roundToInt()
                scope.launch { state.theta.snapTo(th) }
                for (k in 0 until STEPS) scope.launch { state.drawn[k].snapTo(dr[k]) }
                scope.launch {
                    state.fade.snapTo(fd)
                    if (fd < 1f) state.fade.animateTo(1f, tween(300))
                }
                state.manual = true
            } else {
                scope.launch { state.theta.stop() }
                if (state.fade.value < 1f) scope.launch { state.fade.animateTo(1f, tween(300)) }
            }
            state.wake += 1
        }

        LaunchedEffect(state, auto, reduced, state.wake) {
            var last = -1L
            while (true) {
                val keep = withFrameNanos { now ->
                    val dt = if (last < 0) 0f else min((now - last) / 1e9f, 0.05f)
                    last = now
                    if (!state.manual) {
                        state.t.floatValue = (state.t.floatValue + dt) % LOOP
                        return@withFrameNanos auto
                    }
                    if (!reduced) state.mtime = (state.mtime + dt) % (LOOP * 6f)
                    // A tick per step a finger turns the sky to.
                    val s = (state.theta.value / StepAngle).roundToInt()
                    if (state.lastStep >= 0 && s != state.lastStep) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    state.lastStep = s
                    state.frame.intValue += 1
                    !reduced || state.theta.isRunning || state.fade.isRunning || state.drawn.any { it.isRunning }
                }
                if (!keep) break
            }
        }

        // The sky and the constellations, one layer redrawn per frame; it takes the swipe.
        Spacer(
            Modifier
                .fillMaxSize()
                .graphicsLayer()
                .drawBehind {
                    val p = state.pose()
                    scale(d, d, pivot = Offset.Zero) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                            shader.setFloatUniform("res", width, height)
                            shader.setFloatUniform("pole", g.px, g.py)
                            shader.setFloatUniform("theta", p.theta)
                            shader.setFloatUniform("phase", 2f * PI.toFloat() * p.time / LOOP)
                            drawRect(brush, Offset.Zero, Size(width, height))
                        } else {
                            val f = fallback
                            if (f != null) {
                                drawIntoCanvas { f.draw(it.nativeCanvas, p.theta) }
                            } else {
                                drawRect(Brush.verticalGradient(0f to Color(6, 8, 23), 0.75f to Color(11, 17, 41), 1f to Color(35, 27, 56)), size = Size(width, height))
                            }
                        }
                        drawIntoCanvas { painter.draw(it.nativeCanvas, p) }
                    }
                }
                .semantics { contentDescription = "The night sky. Swipe sideways to turn it to the next constellation." }
                .pointerInput(radius, reduced, auto) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        takeOver()
                        val from = state.theta.value
                        val sx = down.position.x / d
                        var dx = 0f
                        var active = false
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        try {
                            while (true) {
                                val ev = awaitPointerEvent()
                                val ch = ev.changes.firstOrNull { it.id == down.id } ?: break
                                if (!ch.pressed) break
                                tracker.addPosition(ch.uptimeMillis, ch.position)
                                dx = ch.position.x / d - sx
                                if (!active && abs(dx) > 10f) active = true
                                if (active) {
                                    // In steps, rubber-banding to at most 0.35 of a step past either end.
                                    var u = (from - dx / radius) / StepAngle
                                    if (u < 0f) u = -(-u / (1f - u) * 0.35f)
                                    if (u > STEPS - 1) {
                                        val over = u - (STEPS - 1)
                                        u = STEPS - 1 + over / (1f + over) * 0.35f
                                    }
                                    val th = u * StepAngle
                                    if (!reduced) scope.launch { state.theta.snapTo(th) }
                                    ch.consume()
                                }
                            }
                        } finally {
                            if (active) {
                                val v = -tracker.calculateVelocity().x / d / radius
                                val basis = if (reduced) from - dx / radius else state.theta.value + v * 0.2f
                                val k = max(0, min(STEPS - 1, (basis / StepAngle).roundToInt()))
                                settleOn(k, v)
                            }
                            state.wake += 1
                            scheduleResume(IDLE_MS)
                        }
                    }
                },
        )

        // Header.
        Row(
            Modifier
                .offset { dpOffset(24f, safeTop + 12f) }
                .width((width - 48f).dp)
                .height(36.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(26.dp), contentAlignment = Alignment.Center) {
                    Box(Modifier.width(26.dp).height(14.dp).rotate(-20f).border(1.2.dp, Gold, RoundedCornerShape(13.dp)))
                    Box(Modifier.size(8.dp).background(Gold, CircleShape))
                }
                BasicText("orrery", style = TextStyle(color = Ink, fontSize = 24.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.6).sp))
            }
            BasicText(
                "Skip",
                Modifier.padding(start = 12.dp, top = 8.dp, bottom = 8.dp).semantics { role = Role.Button },
                style = TextStyle(color = Muted, fontSize = 15.sp, fontWeight = FontWeight.SemiBold),
            )
        }
        Box(Modifier.offset { dpOffset(24f, safeTop + 58f) }.width((width - 48f).dp)) {
            SkyRollStack(state, Captions, 16.dp, TextStyle(color = Color(242, 212, 138).copy(alpha = 0.8f), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.sp), lines = 1, center = true)
        }

        // The panel; its scrim reaches 40 dp above it. It takes its own touches.
        Box(
            Modifier
                .offset { dpOffset(0f, panelTop - 40f) }
                .fillMaxWidth()
                .height((height - panelTop + 40f).dp)
                .background(
                    Brush.verticalGradient(
                        0f to Color(7, 10, 26).copy(alpha = 0f),
                        0.3f to Color(7, 10, 26).copy(alpha = 0.82f),
                        1f to Color(6, 8, 22).copy(alpha = 0.96f),
                    ),
                ),
        )
        Box(
            Modifier
                .offset { dpOffset(0f, panelTop) }
                .fillMaxWidth()
                .height((height - panelTop).dp)
                .pointerInput(Unit) { awaitEachGesture { awaitFirstDown(requireUnconsumed = false) } },
        ) {
            Column(
                Modifier.fillMaxSize().padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = (safeBottom + 16f).dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.padding(bottom = 2.dp).height(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        repeat(3) { Box(Modifier.size(8.dp).background(Color(0xFFF3EEE4).copy(alpha = 0.22f), CircleShape)) }
                    }
                    Box(
                        Modifier
                            .size(8.dp)
                            .graphicsLayer { translationX = state.pose().step * 16.dp.toPx() }
                            .background(Gold, CircleShape),
                    )
                }
                SkyRollStack(state, Titles, 36.dp, TextStyle(color = Ink, fontSize = 28.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp), lines = 1, center = false)
                SkyRollStack(state, Bodies, 44.dp, TextStyle(color = Muted, fontSize = 15.sp, lineHeight = 21.sp), lines = 2, center = false)
                SkyContinue(
                    Modifier.padding(top = 8.dp),
                    onPress = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        takeOver()
                        val now = (state.theta.value / StepAngle).roundToInt()
                        settleOn(if (now >= STEPS - 1) 0 else now + 1, 0f)
                        scheduleResume(IDLE_MS + 1200L)
                    },
                )
                BasicText(
                    "Uses your location only to face the right sky",
                    Modifier.fillMaxWidth().padding(top = 4.dp),
                    style = TextStyle(color = Color(0xFFF3EEE4).copy(alpha = 0.42f), fontSize = 13.sp, textAlign = TextAlign.Center),
                )
            }
        }
    }
}

/** The gold Continue pill; it shrinks to 0.97 while pressed. */
@Composable
private fun SkyContinue(modifier: Modifier, onPress: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    Box(
        modifier
            .fillMaxWidth()
            .height(56.dp)
            .graphicsLayer {
                val s = if (pressed) 0.97f else 1f
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(Color.Black.copy(alpha = 0.35f), offsetY = 1.dp, blur = 2.dp, radius = 28.dp)
            .cssBoxShadow(Color(242, 212, 138).copy(alpha = 0.25f), offsetY = 12.dp, blur = 30.dp, radius = 28.dp)
            .clip(RoundedCornerShape(28.dp))
            .background(Gold)
            .drawBehind {
                // inset 0 1 0: a one-dp light band along the inside of the top edge.
                val r = 28.dp.toPx()
                val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
                val inner = Path().apply { addRoundRect(RoundRect(0f, 1.dp.toPx(), size.width, size.height + 1.dp.toPx(), CornerRadius(r))) }
                clipPath(outer) { drawPath(Path.combine(PathOperation.Difference, outer, inner), Color(255, 250, 230).copy(alpha = 0.7f)) }
            }
            .semantics { role = Role.Button }
            .pointerInput(onPress) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { onPress() },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Continue", style = TextStyle(color = Color(0xFF1C1A2E), fontSize = 17.sp, fontWeight = FontWeight.Bold))
    }
}

/** One text of a list, chosen by the continuous step: each rolls inside a clipped box. */
@Composable
private fun SkyRollStack(state: SkyState, items: List<String>, height: Dp, style: TextStyle, lines: Int, center: Boolean) {
    Box(Modifier.fillMaxWidth().height(height).clipToBounds()) {
        items.forEachIndexed { i, text ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(height)
                    .graphicsLayer {
                        val dd = i - state.pose().step
                        alpha = max(0f, 1f - abs(dd) * 1.15f)
                        translationY = dd * height.toPx()
                    },
                contentAlignment = if (center) Alignment.Center else Alignment.CenterStart,
            ) {
                BasicText(text, style = if (center) style.copy(textAlign = TextAlign.Center) else style, maxLines = lines)
            }
        }
    }
}
