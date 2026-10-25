package com.ngocdevv.nativecook.blocks.splash.stainedglasssplash

import android.graphics.RuntimeShader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WbTwilight
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/*
 Stained Glass Splash — the launch of "Chapel", a meditation app. A rose window waits dim in a
 stone wall; the sun crosses it and the panes light one by one (outer rings first, the gold rose of
 the app's mark last), throwing their colours down a dusty shaft onto the flagstones. Then the
 light floods out of the window into cream and the home screen rises through it.

 Drawing: one Canvas with one AGSL shader (StainedGlassSplashShader.kt) for wall, window, beams,
 dust and floor, and the flood; below Android 13, StainedGlassSplashFallback.kt. Uniforms come from
 one scene object written by the frame loop. The wordmark and the home screen are composed once
 and read the scene only in graphicsLayer lambdas.

 Touch: during the splash, drag across the window to move the sun: the beam follows the finger and
 every pane it passes lights and stays lit. Light all of it and the splash carries on into the
 flood and the home screen; otherwise, 2 s after the finger lifts, the panes go dark again (0.9 s)
 and the loop restarts. The home screen does not take the gesture.
 Reduced motion: no loop; the home screen on cream.

 LOOP = 9 s:
   0.0 – 0.8  rest: the window at a quarter glow in its stone, the wordmark at 55 %.
   0.8 – 4.4  the sun crosses: panes light by their key, the beam moves with it, the floor fills.
   4.4 – 5.6  the projection peaks.
   5.6 – 6.4  flood: warm light pours out of the window into cream; the home rises in 5.8 – 6.25.
   6.4 – 7.6  home.
   7.8 – 9.0  home fades as the cream drains back into the window and the wordmark returns.
*/

private const val LOOP = 9f
/** The lit range of keys at rest (nothing lit). */
private const val DARK = -0.12f
private const val MODE_AUTO = 0
private const val MODE_USER = 1
private const val MODE_RETURN = 2
private const val IDLE = 2f
private const val RETURN = 0.9f
/** Where the loop picks up after a finger has lit every pane: the floor's peak. */
private const val RESUME_LIT = 4.4f

private val Ink = Color(0xFF2B2119)
private val InkSoft = Color(0x9E2B2119)
private val Ruby = Color(0xFF9B2D2B)
private val CreamCard = Color(0xC7FFFBF3)

/** The window's centre and radius in dp. */
internal class StainedGlassLayout(val cx: Float, val cy: Float, val r: Float)

private fun stainedLayout(width: Float, height: Float, insetTop: Float): StainedGlassLayout {
    val r = min(width * 0.42f, height * 0.2f)
    return StainedGlassLayout(width / 2f, insetTop + 70f + r, r)
}

private fun clamp01(x: Float) = min(1f, max(0f, x))
private fun seg(t: Float, a: Float, b: Float) = clamp01((t - a) / (b - a))
private fun easeInOut(x: Float): Float {
    val c = clamp01(x)
    return if (c < 0.5f) 4f * c * c * c else 1f - (-2f * c + 2f).pow(3) / 2f
}
private fun easeOut(x: Float): Float {
    val c = clamp01(x)
    return 1f - (1f - c).pow(3)
}

/** One frame of the splash: the lit range of keys, the beam, the flood, the home and the wordmark. */
internal class StainedGlassScene {
    var lo = DARK
    var hi = DARK
    var beam = -0.2f
    var keyMix = 1f
    var flood = 0f
    var home = 0f
    var homeOut = 0f
    var mark = 0.55f

    fun copyFrom(a: StainedGlassScene) {
        lo = a.lo; hi = a.hi; beam = a.beam; keyMix = a.keyMix
        flood = a.flood; home = a.home; homeOut = a.homeOut; mark = a.mark
    }

    fun blend(a: StainedGlassScene, b: StainedGlassScene, e: Float) {
        lo = a.lo + (b.lo - a.lo) * e
        hi = a.hi + (b.hi - a.hi) * e
        beam = a.beam + (b.beam - a.beam) * e
        keyMix = a.keyMix + (b.keyMix - a.keyMix) * e
        flood = a.flood + (b.flood - a.flood) * e
        home = a.home + (b.home - a.home) * e
        homeOut = a.homeOut + (b.homeOut - a.homeOut) * e
        mark = a.mark + (b.mark - a.mark) * e
    }
}

private fun loopScene(t: Float, s: StainedGlassScene) {
    // Ease-out: the first panes catch the sun within half a second of 0.8 s.
    val cross = 1f - (1f - seg(t, 0.8f, 4.4f)).pow(1.6f)
    s.lo = DARK
    s.hi = if (t < 6.4f) DARK + (1.25f - DARK) * cross else DARK
    s.beam = when {
        t < 4.4f -> -0.15f + 1.1f * cross
        t < 6.4f -> 0.95f - 0.45f * easeInOut(seg(t, 4.4f, 5.4f))
        else -> 0.5f - 0.65f * easeInOut(seg(t, 6.4f, 7.8f))
    }
    s.keyMix = 1f
    s.flood = easeInOut(seg(t, 5.6f, 6.4f)) * (1f - easeInOut(seg(t, 7.8f, 8.8f)))
    s.home = easeOut(seg(t, 5.8f, 6.25f))
    s.homeOut = easeInOut(seg(t, 8.0f, 8.5f))
    val lit = clamp01((s.hi - s.lo) / 1.3f)
    s.mark = if (t < 6.4f) 0.55f + 0.45f * lit else 0.55f * easeOut(seg(t, 8.4f, 9.0f))
}

private class StainedState {
    var layout = StainedGlassLayout(0f, 0f, 1f)
    var mode = MODE_AUTO
    var clock = 0f
    var now = 0f
    var lastTouch = 0f
    var holding = false
    var returnAt = 0f
    val scene = StainedGlassScene()
    val from = StainedGlassScene()
    val target = StainedGlassScene()
    // The finger's sun: the lit range, the beam and the key mix, and whether all of it was lit once.
    var hLo = DARK
    var hHi = DARK
    var hBeam = -0.2f
    var hMix = 1f
    var full = false
    var posed = false
    val frame = mutableIntStateOf(0)
    val awake = mutableStateOf(true)
}

/**
 * The launch of "Chapel": a rose window in a stone wall lights pane by pane as the sun crosses it,
 * throws its colours onto the flagstones, then floods into cream and the home screen rises through
 * it. One AGSL shader draws the chapel. A finger moves the sun.
 *
 * @param autoPlay plays the 9 s light-floor-flood-home loop.
 */
@Composable
fun StainedGlassSplash(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val view = LocalView.current
    val screen = LocalDensity.current
    val state = remember { StainedState() }
    val insets = WindowInsets.safeDrawing
    val insetTop = insets.getTop(screen) / screen.density
    val insetBottom = insets.getBottom(screen) / screen.density
    val shader = remember {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) runCatching { RuntimeShader(STAINED_GLASS_SPLASH_AGSL) }.getOrNull() else null
    }
    val brush = remember(shader) { shader?.let { ShaderBrush(it) } }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF15110E))) {
        val width = maxWidth.value
        val height = maxHeight.value
        val l = stainedLayout(width, height, insetTop)
        state.layout = l
        if (!state.posed || reduced) {
            loopScene(if (reduced) 7.0f else 0f, state.scene)
            state.posed = true
        }

        LaunchedEffect(state, auto, reduced, state.awake.value) {
            if (reduced || !state.awake.value) return@LaunchedEffect
            var last = -1L
            while (true) {
                val stop = withFrameNanos { nanos ->
                    val dt = if (last < 0) 1f / 60f else min((nanos - last) / 1e9f, 0.05f)
                    last = nanos
                    stainedFrame(state, dt, auto)
                    state.frame.intValue += 1
                    !auto && state.mode == MODE_AUTO
                }
                if (stop) {
                    state.awake.value = false
                    break
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .semantics { contentDescription = "A rose window. Drag across it to move the sun and light the panes." }
                .pointerInput(state, reduced) {
                    if (reduced) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = screen.density
                        // The home screen and the flood do not take the finger.
                        if (state.scene.flood <= 0.05f) {
                            state.holding = true
                            state.lastTouch = state.now
                            if (state.mode != MODE_USER) {
                                val s = state.scene
                                val b = across(state.layout, down.position.x / d)
                                val lit = s.hi - s.lo > 0.05f
                                if (lit) {
                                    state.hLo = max(-0.05f, s.lo)
                                    state.hHi = min(1.05f, s.hi)
                                } else {
                                    state.hLo = b - 0.06f
                                    state.hHi = b + 0.06f
                                }
                                state.hBeam = b
                                state.hMix = s.keyMix
                                state.full = false
                                state.mode = MODE_USER
                            }
                            if (spread(state, down.position.x / d)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                            state.awake.value = true
                        }
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            if (state.mode == MODE_USER && state.holding) {
                                if (spread(state, change.position.x / d)) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                                state.lastTouch = state.now
                            }
                        }
                        state.holding = false
                        state.lastTouch = state.now
                    }
                },
        ) {
            state.frame.intValue
            val s = state.scene
            val dd = this.density
            scale(dd, dd, pivot = Offset.Zero) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && shader != null && brush != null) {
                    shader.setFloatUniform("size", width, height)
                    shader.setFloatUniform("win", l.cx, l.cy, l.r)
                    shader.setFloatUniform("sweep", s.lo, s.hi, s.beam)
                    shader.setFloatUniform("keyMix", s.keyMix)
                    shader.setFloatUniform("flood", s.flood)
                    shader.setFloatUniform("phase", state.clock / LOOP)
                    drawRect(brush, Offset.Zero, Size(width, height))
                } else {
                    stainedGlassFallback(width, height, l, s)
                }
            }
        }

        // The wordmark under the window.
        Column(
            Modifier
                .fillMaxWidth()
                .offset(y = (l.cy + l.r * 1.04f + 18f).dp)
                .graphicsLayer {
                    state.frame.intValue
                    val s = state.scene
                    alpha = clamp01(s.mark * (1f - min(1f, s.flood * 2.5f)))
                    translationY = 6f * (1f - s.mark) * density
                },
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            BasicText(
                "Chapel",
                style = TextStyle(color = Color(0xFFF4E4C8), fontFamily = FontFamily.Serif, fontStyle = FontStyle.Italic, fontSize = 44.sp, letterSpacing = 0.5.sp),
            )
            BasicText("QUIET MINUTES, EVERY DAY", style = TextStyle(color = Color(0x9EF4E4C8), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 2.4.sp))
        }

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    state.frame.intValue
                    val s = state.scene
                    alpha = clamp01(s.home * (1f - s.homeOut))
                    translationY = (12f * (1f - s.home) - 6f * s.homeOut) * density
                },
        ) {
            StainedGlassHome(insetTop, insetBottom)
        }
    }
}

/** The finger's x as a place across the window (0 left edge … 1 right edge). */
private fun across(l: StainedGlassLayout, x: Float) = (x - (l.cx - l.r)) / (2f * l.r)

/** Widens the lit range to the finger. True the moment the whole window is lit (one haptic). */
private fun spread(s: StainedState, x: Float): Boolean {
    val b = across(s.layout, x)
    s.hLo = min(s.hLo, b - 0.06f)
    s.hHi = max(s.hHi, b + 0.06f)
    s.hBeam = b
    if (!s.full && s.hLo < 0.04f && s.hHi > 0.96f) {
        s.full = true
        return true
    }
    return false
}

private fun stainedFrame(st: StainedState, dt: Float, auto: Boolean) {
    val at = st.now + dt
    st.now = at
    val s = st.scene
    when (st.mode) {
        MODE_AUTO -> {
            val t = if (auto) (st.clock + dt) % LOOP else 0f
            st.clock = t
            loopScene(t, s)
        }
        MODE_USER -> {
            // The key eases from the loop's order to plain left to right (0.3 s).
            st.hMix = max(0f, st.hMix - dt / 0.3f)
            loopScene(0f, s)
            s.lo = st.hLo
            s.hi = st.hHi
            s.beam = st.hBeam
            s.keyMix = st.hMix
            s.mark = 0.55f + 0.45f * min(1f, max(0f, (st.hHi - st.hLo) / 1.0f))
            if (!st.holding && st.full) {
                // The whole window is lit: hand back to the loop at the floor's peak.
                st.from.copyFrom(s)
                st.returnAt = at
                st.clock = RESUME_LIT
                st.mode = MODE_RETURN
            } else if (!st.holding && at - st.lastTouch > IDLE) {
                st.from.copyFrom(s)
                st.returnAt = at
                st.clock = 0f
                st.mode = MODE_RETURN
            }
        }
        else -> {
            // Ease into the loop's frame at the clock it will resume from.
            val e = easeInOut((at - st.returnAt) / RETURN)
            loopScene(st.clock, st.target)
            s.blend(st.from, st.target, e)
            if (e >= 1f) st.mode = MODE_AUTO
        }
    }
}

private class StainedDay(val day: String, val v: Float)
private val Week = listOf(
    StainedDay("M", 0.55f), StainedDay("T", 0.8f), StainedDay("W", 0.35f), StainedDay("T", 0.95f),
    StainedDay("F", 0.7f), StainedDay("S", 0f), StainedDay("S", 0f),
)

private fun Modifier.stainedCard(radius: Float, deep: Boolean = false) = this
    .cssBoxShadow(Color(0x1A3C1E14), 1.dp, 2.dp, radius.dp)
    .cssBoxShadow(if (deep) Color(0x1A3C1E14) else Color(0x143C1E14), if (deep) 10.dp else 8.dp, if (deep) 24.dp else 20.dp, radius.dp)
    .clip(RoundedCornerShape(radius.dp))
    .background(CreamCard)

/** The home screen the splash opens onto (static: it only fades and rises). */
@Composable
private fun StainedGlassHome(insetTop: Float, insetBottom: Float) {
    Column(
        Modifier
            .fillMaxSize()
            .padding(start = 20.dp, end = 20.dp, top = (insetTop + 18f).dp, bottom = (insetBottom + 10f).dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            BasicText("CHAPEL", style = TextStyle(color = Ruby, fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.2.sp))
            BasicText("Good evening, Mira", style = TextStyle(color = Ink, fontFamily = FontFamily.Serif, fontSize = 32.sp, letterSpacing = (-0.3).sp), maxLines = 1)
            BasicText("Day 14 · 86 minutes this week", style = TextStyle(color = InkSoft, fontSize = 14.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
        }
        val todayShape = RoundedCornerShape(26.dp)
        Column(
            Modifier
                .fillMaxWidth()
                .cssBoxShadow(Color(0x2E3C1E14), 1.dp, 2.dp, 26.dp)
                .cssBoxShadow(Color(0x383C1E14), 16.dp, 34.dp, 26.dp)
                .clip(todayShape)
                .background(Brush.linearGradient(0f to Color(0xFF2B3F8F), 0.55f to Color(0xFF4B2A78), 1f to Color(0xFF7A2440)))
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("TODAY'S SIT", style = TextStyle(color = Color(0xB8FFF0DC), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 2.sp))
            BasicText("Stillwater", style = TextStyle(color = Color(0xFFFFF6E8), fontFamily = FontFamily.Serif, fontSize = 30.sp))
            BasicText("12 min · Breath and stillness", style = TextStyle(color = Color(0xCCFFF0DC), fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Row(Modifier.padding(top = 14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(Color(0xFFFFF6E8)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.PlayArrow, contentDescription = null, tint = Color(0xFF2B3F8F), modifier = Modifier.size(22.dp))
                }
                BasicText("Guided by Ines Varga", style = TextStyle(color = Color(0xCCFFF0DC), fontSize = 13.sp, fontWeight = FontWeight.SemiBold))
            }
        }
        BasicText("Keep going", modifier = Modifier.padding(top = 4.dp), style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Bold))
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            StainedTile("Wind down", "8 min", Color(0xFFE7B45A), Color(0xFFC9733A), Color(0xFF8E3A2E), Modifier.weight(1f))
            StainedTile("Morning light", "10 min", Color(0xFF9FCB9A), Color(0xFF3E8A6A), Color(0xFF22524E), Modifier.weight(1f))
            StainedTile("Steady focus", "5 min", Color(0xFFB9A3E0), Color(0xFF6C55B0), Color(0xFF352A6E), Modifier.weight(1f))
        }
        Column(
            Modifier
                .fillMaxWidth()
                .weight(1f, fill = false)
                .stainedCard(20f)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                BasicText("Your week", style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold))
                BasicText("86 min", style = TextStyle(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = false)
                    .heightIn(min = 28.dp, max = 120.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Week.forEachIndexed { i, d ->
                    Column(Modifier.weight(1f).fillMaxHeight(), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Box(
                            Modifier
                                .weight(1f)
                                .width(12.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color(0x149B2D2B)),
                            contentAlignment = Alignment.BottomCenter,
                        ) {
                            if (d.v > 0f) {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .fillMaxHeight(d.v)
                                        .clip(RoundedCornerShape(6.dp))
                                        .background(if (i == 4) Ruby else Color(0x529B2D2B)),
                                )
                            }
                        }
                        BasicText(d.day, style = TextStyle(color = if (i == 4) Ruby else InkSoft, fontSize = 11.sp, fontWeight = FontWeight.Bold))
                    }
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .height(60.dp)
                .stainedCard(30f, deep = true),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StainedTab(Icons.Filled.WbTwilight, "Today", on = true)
            StainedTab(Icons.Outlined.GridView, "Library")
            StainedTab(Icons.Outlined.Book, "Journal")
            StainedTab(Icons.Outlined.AccountCircle, "You")
        }
    }
}

@Composable
private fun StainedTile(title: String, detail: String, a: Color, b: Color, c: Color, modifier: Modifier) {
    Column(modifier.stainedCard(18f).padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            Modifier
                .padding(bottom = 4.dp)
                .fillMaxWidth()
                .height(64.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Brush.linearGradient(0f to a, 0.55f to b, 1f to c)),
        )
        BasicText(title, style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
        BasicText(detail, style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"), maxLines = 1)
    }
}

@Composable
private fun StainedTab(icon: ImageVector, label: String, on: Boolean = false) {
    val tint = if (on) Ruby else Color(0x732B2119)
    Column(Modifier.widthIn(min = 60.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        BasicText(label, style = TextStyle(color = tint, fontSize = 11.sp, fontWeight = FontWeight.Bold))
    }
}
