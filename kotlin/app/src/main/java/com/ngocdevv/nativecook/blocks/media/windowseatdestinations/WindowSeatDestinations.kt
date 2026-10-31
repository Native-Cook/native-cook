package com.ngocdevv.nativecook.blocks.media.windowseatdestinations

import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.min
import kotlin.math.roundToInt

// Exact RN timeline: four 2 s stops in an 8 s loop, each panning 70 dp.
// Shade closes at 1.25–1.60 (cubic in), view swaps at 1.65, shade opens at
// 1.70–2.00 (cubic out). Captions use nested smoothstep at 1.55–1.85.
// A pull beyond half closes/swaps/opens over .60 s, then holds 2 s before resuming.
// Per-frame reads belong to draw/layer lambdas, never whole-screen composition.
private const val LOOP = 8.0
private const val STOP = 2.0
private const val OPEN_H = 368f
private val INK = Color(0xFF13243A)
private val SOFT = Color(0xFF5E6E80)
private data class WindowSeatDestinationsStop(val name: String, val route: String, val fare: String)
private val STOPS = listOf(
    WindowSeatDestinationsStop("Alpine Ridge", "Geneva → Sion · 55 min", "$129"),
    WindowSeatDestinationsStop("Nalu Atoll", "Malé → Nalu · 1 h 20 min", "$214"),
    WindowSeatDestinationsStop("Velmar Harbour", "Porto → Velmar · 1 h 05 min", "$96"),
    WindowSeatDestinationsStop("Amber Dunes", "Ouarzi → Merz · 1 h 40 min", "$158"),
)
private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
private fun seg(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
private fun cubicIn(x: Double) = x * x * x
private fun cubicOut(x: Double): Double { val u = 1 - x; return 1 - u * u * u }
private fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }

private class WindowSeatDestinationsMotion {
    var elapsed = 0.0
    var origin = 0.0
    var clock by mutableDoubleStateOf(0.0)
    var shade by mutableFloatStateOf(0f)
    var view by mutableIntStateOf(0)
    var caption by mutableDoubleStateOf(0.0)
    var pan by mutableFloatStateOf(.175f)
    var touching = false
    var manual = false
    var released = -1.0
    var grabShade = 0f
    var fromShade = 0f
    var advance = false
    var target = 0

    fun frame(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        val t = (elapsed - origin) % LOOP
        clock = t
        if (touching) return
        if (manual) {
            val since = elapsed - released
            if (advance) {
                when {
                    since < .2 -> shade = (fromShade + (1 - fromShade) * cubicIn(since / .2)).toFloat()
                    since < .3 -> { shade = 1f; view = target; caption = target.toDouble() }
                    else -> shade = (1 - cubicOut(clamp((since - .3) / .3))).toFloat()
                }
            } else shade = (fromShade * (1 - cubicOut(clamp(since / .3)))).toFloat()
            pan = min(1f, pan + (dt / STOP).toFloat())
            if (since > 2.6) { manual = false; origin = elapsed - view * STOP }
            return
        }
        if (!autoPlay) return
        val k = floor(t / STOP).toInt()
        val u = t - k * STOP
        shade = (if (u < 1.65) cubicIn(seg(u, 1.25, 1.6)) else 1 - cubicOut(seg(u, 1.7, 2.0))).toFloat()
        view = (if (u < 1.65) k else k + 1) % 4
        caption = k + smooth(seg(u, 1.55, 1.85))
        pan = ((if (u < 1.65) u + .35 else u - 1.65) / STOP).toFloat()
    }

    fun begin() {
        touching = true
        manual = true
        grabShade = shade
        caption = (caption.roundToInt() % 4).toDouble()
        view = caption.toInt()
    }

    fun release(): Boolean {
        touching = false
        fromShade = shade
        advance = shade > .5f
        target = (view + 1) % 4
        pan = 0f
        released = elapsed
        return advance
    }

    fun captionOpacity(k: Int): Float {
        val c = ((caption % 4) + 4) % 4
        val distance = abs(c - k).let { min(it, 4 - it) }
        return (1 - smooth(distance / .5)).toFloat()
    }
}

@Composable
fun WindowSeatDestinations(
    modifier: Modifier = Modifier,
    brand: String = "porthole",
    seat: String = "Seat 14A",
    headline: String = "Where to\nnext?",
    action: String = "Search flights",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val activity = LocalActivity.current
    val haptic = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    val reduced = rememberReducedMotion()
    val auto by rememberUpdatedState(autoPlay)
    val motion = remember { WindowSeatDestinationsMotion() }
    val art = remember(context, density.density) { WindowSeatDestinationsArt(context.assets, density.density) }
    val serif = remember(art) { FontFamily(androidx.compose.ui.text.font.Typeface(art.serif)) }
    val italic = remember(art) { FontFamily(androidx.compose.ui.text.font.Typeface(art.italic)) }
    var foreground by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            foreground = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(activity, view) {
        val controller = activity?.window?.let { WindowCompat.getInsetsController(it, view) }
        val oldStatus = controller?.isAppearanceLightStatusBars
        val oldNavigation = controller?.isAppearanceLightNavigationBars
        controller?.isAppearanceLightStatusBars = true
        controller?.isAppearanceLightNavigationBars = true
        onDispose {
            if (oldStatus != null) controller.isAppearanceLightStatusBars = oldStatus
            if (oldNavigation != null) controller.isAppearanceLightNavigationBars = oldNavigation
        }
    }
    LaunchedEffect(foreground, reduced) {
        if (!foreground || reduced) return@LaunchedEffect
        var previous = 0L
        while (true) withFrameNanos { now ->
            val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
            previous = now
            motion.frame(dt, auto)
        }
    }
    val insets = WindowInsets.safeDrawing.asPaddingValues()
    val textStyle = TextStyle(color = INK, platformStyle = PlatformTextStyle(includeFontPadding = false))
    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFF1F4F7))) {
        val frameY = maxHeight.value * .49f - 212
        Box(Modifier.fillMaxSize().pointerInput(reduced) {
            if (reduced) return@pointerInput
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position
                var activated = false
                motion.begin()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change != null) {
                            val delta = change.position - start
                            val dy = delta.y / density.density
                            if (delta.getDistance() / density.density >= 4) activated = true
                            if (activated) {
                                motion.shade = (motion.grabShade + dy / OPEN_H).coerceIn(0f, 1f)
                                change.consume()
                            }
                        }
                    } while (event.changes.any { it.pressed })
                } finally {
                    if (motion.release()) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                }
            }
        }) {
            Canvas(Modifier.fillMaxSize().semantics {
                contentDescription = "Airplane window. Pull the shade down to fly to the next destination."
            }) {
                val canvas = drawContext.canvas.nativeCanvas
                canvas.save()
                canvas.scale(density.density, density.density)
                art.draw(canvas, size.width / density.density, size.height / density.density,
                    motion.clock, motion.shade, motion.view, motion.pan)
                canvas.restore()
            }
            Column(Modifier.offset(y = insets.calculateTopPadding() + 8.dp).padding(horizontal = 24.dp)) {
                Row(Modifier.fillMaxWidth().height(34.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(brand, style = textStyle, fontFamily = italic, fontSize = 28.sp, modifier = Modifier.weight(1f))
                    Box(Modifier.height(28.dp).background(INK.copy(alpha = .08f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center) {
                        Text(seat, style = textStyle, fontSize = 13.sp, fontWeight = FontWeight.Bold, letterSpacing = .3.sp)
                    }
                }
                Text(headline, modifier = Modifier.padding(top = 6.dp), style = textStyle,
                    fontFamily = serif, fontSize = 54.sp, lineHeight = 56.sp, letterSpacing = (-1).sp)
            }
            Box(Modifier.offset(y = (frameY + 424 + 26).dp).padding(horizontal = 28.dp).fillMaxWidth().height(70.dp)) {
                STOPS.forEachIndexed { k, stop ->
                    Column(Modifier.fillMaxWidth().graphicsLayer {
                        alpha = motion.captionOpacity(k)
                        translationY = 12 * (1 - alpha) * density.density
                    }) {
                        Row(Modifier.fillMaxWidth()) {
                            Text(stop.name, modifier = Modifier.weight(1f).alignByBaseline(), style = textStyle,
                                fontFamily = serif, fontSize = 36.sp, lineHeight = 42.sp)
                            Text(stop.fare, modifier = Modifier.alignByBaseline(), style = textStyle,
                                fontSize = 17.sp, fontWeight = FontWeight.Bold)
                        }
                        Text(stop.route, modifier = Modifier.padding(top = 2.dp), style = textStyle,
                            color = SOFT, fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }
        }
        Box(Modifier.align(Alignment.BottomCenter).padding(horizontal = 24.dp).fillMaxWidth()
            // The RN footer is outside GestureDetector: it must intercept these touches.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        event.changes.forEach { it.consume() }
                    } while (event.changes.any { it.pressed })
                }
            }
            .padding(bottom = insets.calculateBottomPadding() + 14.dp)
            .height(58.dp).background(INK, RoundedCornerShape(29.dp))
            .semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
            Text(action, style = textStyle, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
    }
}
