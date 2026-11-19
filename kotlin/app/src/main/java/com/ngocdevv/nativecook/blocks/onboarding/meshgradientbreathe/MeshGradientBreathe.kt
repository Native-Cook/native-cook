package com.ngocdevv.nativecook.blocks.onboarding.meshgradientbreathe

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Seconds: one box breath, and the onboarding film. Equal to the React Native LOOP. */
const val LOOP = MgbMotion.LOOP

private typealias M = MgbMotion

private val Ink = Color(0xFF1F2333)
private val InkSoft = Ink.copy(alpha = 0.66f)
private val Backdrop = Color(0xFFD8DCEE)

private val titles = listOf("Take a slower minute", "Find your rhythm", "Your first session")
private val bodies = listOf(
    "Follow the light as it fills and empties. Three slow breaths are enough to start.",
    "",
    "No sound, no streaks. Just the light, for as long as you chose.",
)
private const val SWITCH_S = 0.8 // a rhythm change blends the old breath into the new one

private const val AUTO = 0
private const val MANUAL = 1
private const val RET = 2

private const val VIS_HOLD = 0
private const val VIS_GO = 1
private const val VIS_RET = 2

private fun nowS() = System.nanoTime() / 1e9

private class MgbTween(val from: Double, val to: Double, val at: Double, val duration: Double) {
    fun value(now: Double) = from + (to - from) * M.ease((now - at) / duration)
}

private class MgbVis(val kind: Int, val target: Int, val from: DoubleArray, val at: Double)

/** Everything the screen keeps, as snapshot state, so draw lambdas and callbacks always read the current value. */
private class MgbState {
    var now by mutableDoubleStateOf(nowS())
    var origin by mutableDoubleStateOf(nowS())
    var loopStart by mutableDoubleStateOf(nowS())
    var mode by mutableIntStateOf(AUTO)
    var vis by mutableStateOf<MgbVis?>(null)
    var retEnd by mutableDoubleStateOf(Double.MAX_VALUE)
    var session by mutableStateOf(MgbTween(0.0, 0.0, -100.0, 0.7))
    var step by mutableIntStateOf(0)
    var rhythm by mutableIntStateOf(0)
    var minutes by mutableIntStateOf(3)
    var switchFrom by mutableDoubleStateOf(0.0)
    var switchAt by mutableDoubleStateOf(-100.0)
    var breathOrigin by mutableDoubleStateOf(0.0)
    var idleToken by mutableIntStateOf(0)
    var awake by mutableStateOf(false)
    var reduced by mutableStateOf(false)
    var film by mutableStateOf(true)

    fun amb(now: Double) = if (reduced) 0.0 else now - origin

    /** The mode in effect at [now] and the time the film's loop counts from. */
    fun liveMode(now: Double): Pair<Int, Double> =
        if (mode == RET && now >= retEnd) AUTO to retEnd else mode to loopStart

    fun filmT(now: Double): Double {
        val (_, start) = liveMode(now)
        val t = (now - start) % M.LOOP
        return if (t < 0) t + M.LOOP else t
    }

    fun breath(now: Double): Pair<Double, MgbMotion.Breath> {
        val a = amb(now)
        val b = M.breath(a - breathOrigin, rhythm)
        val since = a - switchAt
        val blend = if (since < SWITCH_S) M.clamp01(since / SWITCH_S) else 1.0
        val full = if (reduced) 0.5 else switchFrom + (b.full - switchFrom) * blend
        return full to b
    }

    fun visibility(now: Double): DoubleArray {
        val (m, _) = liveMode(now)
        if (m == AUTO) {
            val t = filmT(now)
            return DoubleArray(3) { M.stepVis(it, t) }
        }
        val v = vis ?: return DoubleArray(3) { if (it == step) 1.0 else 0.0 }
        val e = now - v.at
        return when (v.kind) {
            VIS_HOLD -> v.from
            VIS_GO -> DoubleArray(3) { j ->
                if (j == v.target) {
                    if (e < 0.2) v.from[j] else v.from[j] + (1 - v.from[j]) * M.ease((e - 0.2) / 0.34)
                } else {
                    v.from[j] * (1 - M.easeInQuad(M.clamp01(e / 0.2)))
                }
            }
            else -> {
                val delay = if (v.from[0] > 0.5) 0.0 else 0.3
                DoubleArray(3) { j ->
                    if (j == 0) {
                        if (e < delay) v.from[0] else v.from[0] + (1 - v.from[0]) * M.ease((e - delay) / 0.6)
                    } else {
                        v.from[j] * (1 - M.easeInQuad(M.clamp01(e / 0.3)))
                    }
                }
            }
        }
    }
}

/**
 * A breathing onboarding whose background is a mesh gradient that breathes: on the inhale its control points
 * swell away from the glass orb and the light warms, on the exhale they gather back and cool. The orb guides the
 * breath with the phase and a count. Three steps: a segmented control for the rhythm, a wheel for the session
 * length and a pill button.
 *
 * The film (one 16 s loop that is also one box breath) walks the three steps and turns the wheel; Continue, Skip,
 * the rhythm control and the wheel take over, and the film returns 2 s after the last touch. Begin puts the orb in
 * the middle of the screen; End session brings the steps back. With animations removed the light holds at
 * mid-breath and the film does not run.
 */
@Composable
fun MeshGradientBreathe(autoPlay: Boolean = true, modifier: Modifier = Modifier) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val film = autoPlay && !reduced
    val st = remember { MgbState().also { it.mode = if (film) AUTO else MANUAL } }
    st.reduced = reduced
    st.film = film
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    // ---- one clock (paused under reduced motion unless something was just touched)
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            if (!(st.reduced && !st.awake)) st.now = nowS()
        }
    }
    LaunchedEffect(film) {
        if (!film && st.mode != MANUAL) {
            val n = nowS()
            st.vis = MgbVis(VIS_HOLD, 0, st.visibility(n), n)
            st.mode = MANUAL
        }
    }

    // ---- touch
    fun startReturn() {
        val n = nowS()
        val v = st.visibility(n)
        st.vis = MgbVis(VIS_RET, 0, v, n)
        val end = n + (if (v[0] > 0.5) 0.0 else 0.3) + 0.6
        st.retEnd = end
        st.mode = RET
        st.step = 0
        st.minutes = 3
        st.rhythm = 0
        val token = st.idleToken
        scope.launch {
            delay(((end - nowS()) * 1000).toLong().coerceAtLeast(0))
            if (token != st.idleToken || st.mode != RET) return@launch
            st.loopStart = end
            st.retEnd = Double.MAX_VALUE
            st.mode = AUTO
        }
    }

    fun touch() {
        val n = nowS()
        st.now = n
        if (st.mode != MANUAL) {
            // freeze what the film shows as the starting point of manual tweens
            st.vis = MgbVis(VIS_HOLD, 0, st.visibility(n), n)
            st.mode = MANUAL
        }
        st.idleToken += 1
        val token = st.idleToken
        st.awake = true
        scope.launch {
            delay(2000)
            if (token != st.idleToken) return@launch
            st.awake = false
            if (st.film && st.mode == MANUAL && st.session.to == 0.0) startReturn()
        }
    }

    fun goTo(k: Int) {
        val n = nowS()
        val from = st.visibility(n)
        touch()
        st.step = k
        st.vis = MgbVis(VIS_GO, k, from, n)
    }

    fun onPrimary() {
        if (st.step < 2) {
            haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
            goTo(st.step + 1)
            return
        }
        haptics.performHapticFeedback(HapticFeedbackType.Confirm)
        val n = nowS()
        val cur = st.session.value(n)
        touch()
        st.session = MgbTween(cur, 1.0, n, 0.7)
    }

    fun onEnd() {
        val n = nowS()
        val cur = st.session.value(n)
        st.session = MgbTween(cur, 0.0, n, 0.6)
        touch()
    }

    fun onRhythm(r: Int) {
        val n = nowS()
        val cur = st.breath(n).first
        touch()
        st.rhythm = r
        st.switchFrom = cur
        st.switchAt = st.amb(n)
        st.breathOrigin = st.amb(n)
    }

    // ---- the film's step and length, and a light tap as each breath turns once a person is using it
    LaunchedEffect(Unit) {
        snapshotFlow { val n = st.now; if (st.liveMode(n).first == AUTO) M.filmStep(st.filmT(n)) else -1 }
            .collect { if (it >= 0) st.step = it }
    }
    LaunchedEffect(Unit) {
        snapshotFlow { val n = st.now; if (st.liveMode(n).first == AUTO) M.filmLength(st.filmT(n)) else -1 }
            .collect { if (it >= 0) st.minutes = it }
    }
    val hapticsNow by rememberUpdatedState(haptics)
    LaunchedEffect(Unit) {
        var prev = -1
        snapshotFlow { st.breath(st.now).second.phase }.collect { k ->
            if (prev >= 0 && k != prev && st.liveMode(st.now).first != AUTO && (k == 0 || k == 2)) {
                hapticsNow.performHapticFeedback(HapticFeedbackType.ContextClick)
            }
            prev = k
        }
    }

    val insetTop = with(density) { WindowInsets.statusBars.getTop(this).toDp() }
    val insetBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }

    val shader = remember { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(MGB_AGSL) else null }
    val fallback = remember { if (shader == null) MgbFallback() else null }
    val paint = remember { Paint() }
    val lab = remember { FloatArray(75) }
    val off = remember { FloatArray(50) }

    BoxWithConstraints(modifier.fillMaxSize().background(Backdrop)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val compact = height < 740
        val contentW = min(width, 520f) - 48
        val avail = height - insetTop.value - insetBottom.value
        val orbR = if (compact) 54f else min(width * 0.2f, 80f)
        val orbY = insetTop.value + avail * (if (compact) 0.22f else 0.25f)
        val textTop = orbY + orbR * 1.32f + (if (compact) 18 else 30)
        val wheelH = max(96f, min(height * 0.15f, 136f))

        fun sessionNow() = st.session.value(st.now)
        fun orbCY(s: Double) = orbY + (height * 0.44 - orbY) * s

        // the light and the orb
        Box(
            Modifier.fillMaxSize().drawBehind {
                val n = st.now
                val (full, _) = st.breath(n)
                val s = sessionNow()
                val cy = orbCY(s)
                val rad = orbR * (1 + 0.3 * s) * (1 + 0.26 * full)
                M.light(full, lab)
                val amb = st.amb(n) % M.LOOP
                M.offsets(full, amb, 0.5, cy / height, off)
                val ppt = density.density
                if (shader != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    shader.setFloatUniform("size", size.width, size.height)
                    shader.setFloatUniform("off", off)
                    shader.setFloatUniform("lab", lab)
                    shader.setFloatUniform("orb", size.width / 2, (cy * ppt).toFloat(), (rad * ppt).toFloat())
                    shader.setFloatUniform("lift", full.toFloat())
                    shader.setFloatUniform("ppt", ppt)
                    paint.shader = shader
                    drawIntoCanvas { it.nativeCanvas.drawPaint(paint) }
                } else {
                    drawIntoCanvas {
                        fallback?.draw(it.nativeCanvas, size.width, size.height, off, lab,
                            size.width / 2, (cy * ppt).toFloat(), (rad * ppt).toFloat(), full.toFloat())
                    }
                }
            },
        )

        // the guide on the orb
        MgbOrbLabel(st, width) { s -> orbCY(s) }

        val live by remember { derivedStateOf { st.session.value(st.now) < 0.5 } }
        val contentLayer = Modifier.graphicsLayer {
            val s = sessionNow()
            alpha = (1 - s).toFloat()
            translationY = (24 * s).toFloat() * density.density
        }

        // top bar
        Row(
            contentLayer.padding(top = insetTop + 6.dp).padding(horizontal = 24.dp).fillMaxWidth().height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Stillwater", color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text(
                "Skip", color = InkSoft, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(
                    enabled = live, interactionSource = remember { MutableInteractionSource() }, indication = null,
                ) { goTo(2) },
            )
        }

        // the three steps, stacked; only the current one takes touches
        for (k in 0 until 3) {
            Column(
                contentLayer
                    .offset(x = 24.dp, y = textTop.dp)
                    .width(contentW.dp)
                    .graphicsLayer {
                        val v = st.visibility(st.now)[k]
                        alpha = v.toFloat().coerceIn(0f, 1f)
                        translationY = ((1 - v) * 12).toFloat() * density.density
                    },
            ) {
                val tall = if (compact) 33 else 40
                Text(
                    titles[k], color = Ink, fontSize = if (compact) 28.sp else 34.sp, fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.4).sp, lineHeight = tall.sp, maxLines = 1,
                    modifier = Modifier.height(tall.dp),
                )
                val active = st.step == k && live
                when (k) {
                    0 -> Text(bodies[0], color = InkSoft, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp))
                    1 -> {
                        MgbLabel("Rhythm", 14)
                        MgbSegmented(contentW, st.rhythm, active) { onRhythm(it) }
                        MgbLabel("Session length", if (compact) 12 else 20)
                        MgbWheel(contentW, wheelH, st.minutes, active) { touch(); st.minutes = it }
                    }
                    else -> {
                        Text(
                            "${st.minutes} min · ${M.rhythms[st.rhythm].detail}", color = Ink, fontSize = 17.sp,
                            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 10.dp),
                        )
                        Text(bodies[2], color = InkSoft, fontSize = 17.sp, lineHeight = 24.sp, modifier = Modifier.padding(top = 12.dp))
                    }
                }
            }
        }

        // page dots and the primary button
        Column(
            contentLayer.fillMaxSize().padding(bottom = insetBottom + 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.Bottom),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Canvas(Modifier.size(80.dp, 8.dp)) {
                val v = st.visibility(st.now)
                val d = FloatArray(3) { M.clamp01(v[it]).toFloat() }
                val unit = 1.dp.toPx()
                val widths = FloatArray(3) { (8 + 16 * d[it]) * unit }
                var x = (size.width - (widths.sum() + 12 * unit)) / 2
                for (i in 0 until 3) {
                    drawRoundRect(
                        Ink, Offset(x, 0f), Size(widths[i], 8 * unit), CornerRadius(4 * unit),
                        alpha = 0.32f + 0.68f * d[i],
                    )
                    x += widths[i] + 6 * unit
                }
            }
            MgbPill(contentW, if (st.step < 2) "Continue" else "Begin", Ink, Color.White, live) { onPrimary() }
        }

        // End session, once the session has begun
        if (!live) {
            Column(
                Modifier.fillMaxSize().padding(bottom = insetBottom + 16.dp)
                    .graphicsLayer { alpha = sessionNow().toFloat() },
                verticalArrangement = Arrangement.Bottom,
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                MgbPill(contentW, "End session", Color.White.copy(alpha = 0.55f), Ink, true) { onEnd() }
            }
        }
    }
}

@Composable
private fun MgbLabel(text: String, top: Int) {
    Text(
        text, color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(top = top.dp, bottom = 8.dp),
    )
}

/** Breathe in / Hold / Breathe out and the count, on the orb; scales with the breath. */
@Composable
private fun MgbOrbLabel(st: MgbState, width: Float, orbCY: (Double) -> Double) {
    val density = LocalDensity.current
    val count by remember {
        derivedStateOf {
            if (st.reduced) "" else "${max(1, ceil(st.breath(st.now).second.left - 0.001).toInt())}"
        }
    }
    Box(
        Modifier.width(width.dp).height(80.dp).graphicsLayer {
            val n = st.now
            val s = st.session.value(n)
            val full = st.breath(n).first
            translationY = ((orbCY(s) - 40) * density.density).toFloat()
            val k = (0.9 + 0.14 * full + 0.12 * s).toFloat()
            scaleX = k
            scaleY = k
            transformOrigin = TransformOrigin.Center
        },
        contentAlignment = Alignment.TopCenter,
    ) {
        for (k in 0 until 3) {
            val text = when (k) { 0 -> "Breathe in"; 1 -> "Hold"; else -> "Breathe out" }
            Text(
                text, color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 10.dp).graphicsLayer {
                    alpha = if (st.reduced) {
                        if (k == 0) 1f else 0f
                    } else {
                        val b = st.breath(st.now).second
                        val mine = b.phase == k || (k == 1 && b.phase == 3)
                        if (mine) min(1.0, min(b.left / 0.3, (b.length - b.left) / 0.35)).toFloat().coerceIn(0f, 1f) else 0f
                    }
                },
            )
        }
        Text(
            count, color = InkSoft, fontSize = 28.sp, fontWeight = FontWeight.Light, textAlign = TextAlign.Center,
            style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = "tnum"),
            modifier = Modifier.padding(top = 32.dp).width(80.dp),
        )
    }
}

/** A capsule button (the Swift build's glass button), 54 pt tall with a 50 pt pill. */
@Composable
private fun MgbPill(width: Float, label: String, fill: Color, ink: Color, enabled: Boolean, onClick: () -> Unit) {
    Box(Modifier.width(width.dp).height(54.dp), contentAlignment = Alignment.Center) {
        Box(
            Modifier.fillMaxWidth().height(50.dp)
                .shadow(if (fill.alpha < 1f) 2.dp else 10.dp, RoundedCornerShape(25.dp), ambientColor = Ink.copy(alpha = 0.25f), spotColor = Ink.copy(alpha = 0.25f))
                .clip(RoundedCornerShape(25.dp))
                .background(fill)
                .clickable(enabled = enabled, onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = ink, fontSize = 17.sp)
        }
    }
}

/** The rhythm's segmented control: a pale capsule track and a white thumb that slides to the choice. */
@Composable
private fun MgbSegmented(width: Float, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val x by animateFloatAsState(value.toFloat(), spring(dampingRatio = 0.86f, stiffness = 380f), label = "thumb")
    val n = M.rhythms.size
    Box(
        Modifier.width(width.dp).height(34.dp).clip(RoundedCornerShape(17.dp)).background(Color(0x1F767680)),
    ) {
        val segW = (width - 4) / n
        Box(
            Modifier.offset { IntOffset(((2 + segW * x) * density).roundToInt(), (2 * density).roundToInt()) }
                .width(segW.dp).height(30.dp)
                .shadow(3.dp, RoundedCornerShape(15.dp), ambientColor = Color(0x33000000), spotColor = Color(0x33000000))
                .background(Color.White, RoundedCornerShape(15.dp)),
        )
        Row(Modifier.fillMaxSize().padding(2.dp)) {
            M.rhythms.forEachIndexed { i, r ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().clickable(
                        enabled = enabled, interactionSource = remember { MutableInteractionSource() }, indication = null,
                    ) { if (i != value) onChange(i) },
                    contentAlignment = Alignment.Center,
                ) {
                    Text(r.label, color = Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                }
            }
        }
    }
}

/** The session length wheel: rows on a drum, a pale selection band, snapping per row. */
@Composable
private fun MgbWheel(width: Float, height: Float, value: Int, enabled: Boolean, onChange: (Int) -> Unit) {
    val items = M.lengths
    val index = items.indexOf(value).coerceAtLeast(0)
    val pos = remember { Animatable(index.toFloat()) }
    var dragging by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val onChangeNow by rememberUpdatedState(onChange)
    val enabledNow by rememberUpdatedState(enabled)
    LaunchedEffect(index) {
        if (!dragging) pos.animateTo(index.toFloat(), spring(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 130f))
    }
    val rowH = 32f
    val angle = 0.6f
    val textPaint = remember { Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER } }
    Canvas(
        Modifier.width(width.dp).height(height.dp).pointerInput(Unit) {
            var lastTick = 0
            detectVerticalDragGestures(
                onDragStart = {
                    if (enabledNow) {
                        dragging = true
                        lastTick = pos.value.roundToInt()
                    }
                },
                onDragEnd = {
                    if (dragging) {
                        dragging = false
                        val target = pos.value.roundToInt().coerceIn(0, items.size - 1)
                        scope.launch { pos.animateTo(target.toFloat(), spring(dampingRatio = 1f, stiffness = 300f)) }
                        onChangeNow(items[target])
                    }
                },
                onDragCancel = { dragging = false },
            ) { change, dy ->
                if (!dragging) return@detectVerticalDragGestures
                change.consume()
                var p = pos.value - dy / (rowH * density)
                val lo = 0f
                val hi = (items.size - 1).toFloat()
                if (p < lo) p = lo - (lo - p) * 0.5f
                if (p > hi) p = hi + (p - hi) * 0.5f
                scope.launch { pos.snapTo(p) }
                val tick = p.roundToInt().coerceIn(0, items.size - 1)
                if (tick != lastTick) {
                    lastTick = tick
                    haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }
        },
    ) {
        val u = 1.dp.toPx()
        val cy = size.height / 2
        drawRoundRect(
            Color(0x17767680), Offset(9 * u, cy - 15 * u), Size(size.width - 18 * u, 30 * u), CornerRadius(8 * u),
        )
        val radius = rowH / angle * u
        textPaint.textSize = 20.sp.toPx()
        val c = drawContext.canvas.nativeCanvas
        items.forEachIndexed { i, m ->
            val d = i - pos.value
            val a = d * angle
            if (abs(a) < 1.45f) {
                val y = cy + radius * sin(a)
                val sy = cos(a)
                val centre = M.clamp01(1.0 - abs(d) * 1.6).toFloat()
                val alpha = (0.32f + 0.68f * centre) * (0.35f + 0.65f * sy)
                textPaint.color = Ink.copy(alpha = alpha.coerceIn(0f, 1f)).toArgb()
                c.save()
                c.translate(size.width / 2, y)
                c.scale(1f, sy)
                c.drawText(if (m == 1) "1 minute" else "$m minutes", 0f, -(textPaint.ascent() + textPaint.descent()) / 2, textPaint)
                c.restore()
            }
        }
    }
}
