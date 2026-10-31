package com.ngocdevv.nativecook.blocks.forms.saladgaugebuilder

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.pow

/** Seconds. The empty bowl at both ends is the recording's cut point. */
const val LOOP = 8.0
private const val HOLD = 2.0
private const val EMPTY = 0.6
private val Ink = Color(0xFF2B1838)
private val Green = Color(0xFF33C27A)
private val Background = Color(0xFFE4C6EC)
private val Names = listOf("Tomato", "Salmon", "Greens", "Egg", "Avocado")
private val DropTimes = listOf(0.8, 1.5, 2.2, 2.9, 3.6)

/*
 * React Native reference, in dp: 270 dp bowl at (width/2, height*.47),
 * 11 gauge ticks at radii 150–168 from 195° to 345°.
 * 0–.8 rest; ingredients at .8/1.5/2.2/2.9/3.6, each scaling/fading over .45 s
 * with ease-out back (1.7). Needle steps: 1-exp(-5*x)*cos(9*x), one fifth each.
 * 4.1–5.9 full; 5.9–6.78 ingredients lift out over .6 s, stagger .07 s;
 * 5.9–7 needle returns with ease-in-out cubic; 7–8 rest.
 * Chip toggles take over the current selection. After 2 s, drain over .6 s,
 * then restart. Reduced motion shows the full bowl and stops the frame clock.
 */
private fun clamp(x: Double) = x.coerceIn(0.0, 1.0)
private fun segment(t: Double, a: Double, b: Double) = clamp((t - a) / (b - a))
private fun cubic(x: Double) = if (x < 0.5) 4 * x * x * x else 1 - (-2 * x + 2).pow(3) / 2
private fun springStep(x: Double) = if (x <= 0) 0.0 else 1 - exp(-5 * x) * cos(9 * x)
private data class BowlFrame(val amounts: List<Double> = List(5) { 0.0 }, val needle: Double = 0.0)

private fun bowlAt(t: Double): BowlFrame {
    // The remaining spring tail after 7 s is subpixel; make both rest endpoints exact.
    if (t >= 7.0) return BowlFrame()
    val amounts = DropTimes.mapIndexed { i, at ->
        val out = segment(t, 5.9 + i * 0.07, 6.5 + i * 0.07)
        if (out > 0) -(1 - out) else segment(t, at, at + 0.45)
    }
    val needle = DropTimes.sumOf { springStep(t - it) / Names.size } - cubic(segment(t, 5.9, 7.0))
    return BowlFrame(amounts, needle)
}

private class BowlClock {
    // Snapshot reads are confined to drawing/layer lambdas, never the screen composition.
    var frame by mutableStateOf(BowlFrame())
    var elapsed = 0.0
    var origin = 0.0
    private var manual = false
    private var on = BooleanArray(5)
    private var changed = DoubleArray(5) { -10.0 }
    private var lastTap = -10.0

    fun reset(reduced: Boolean) {
        manual = false
        on.fill(false)
        origin = elapsed
        frame = if (reduced) BowlFrame(List(5) { 1.0 }, 1.0) else BowlFrame()
    }

    fun toggle(i: Int) {
        if (!manual) {
            on = BooleanArray(5) { frame.amounts[it] > 0.5 }
            changed.fill(-10.0)
            manual = true
        }
        on[i] = !on[i]
        changed[i] = elapsed
        lastTap = elapsed
    }

    fun advance(dt: Double, autoPlay: Boolean) {
        elapsed += dt
        if (manual) {
            val drain = clamp((elapsed - lastTap - HOLD) / EMPTY)
            var target = 0.0
            val amounts = Names.indices.map { i ->
                val x = clamp((elapsed - changed[i]) / 0.45)
                var a = if (on[i]) x else -(1 - x)
                if (a == 1.0 && drain > 0) a = -(1 - drain)
                if (a == -1.0) a = 0.0
                if (on[i]) target += 1 - drain
                a
            }
            val needle = frame.needle + (target / Names.size - frame.needle) * (1 - exp(-9 * dt))
            frame = BowlFrame(amounts, needle)
            if (drain >= 1) {
                manual = false
                on.fill(false)
                origin = elapsed
            }
        } else if (autoPlay) frame = bowlAt((elapsed - origin) % LOOP)
    }
}

@Composable
fun SaladGaugeBuilder(
    modifier: Modifier = Modifier,
    brand: String = "Bowlful",
    title: String = "Fill up\nyour bowl",
    autoPlay: Boolean = false,
) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val clock = remember { BowlClock() }
    val art = remember(context, density.density) { SaladGaugeBuilderArt(context, density.density) }
    val family = remember(art) { FontFamily(androidx.compose.ui.text.font.Typeface(art.typeface)) }
    val fontStyle = remember(family) { TextStyle(
        fontFamily = family, color = Ink,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ) }
    val systemStyle = remember { TextStyle(
        fontFamily = FontFamily.SansSerif, color = Ink,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
    ) }
    BlockSystemBars(darkIcons = true)
    LaunchedEffect(clock, reduced) { clock.reset(reduced) }
    LaunchedEffect(clock, lifecycle, reduced, autoPlay) {
        if (reduced) return@LaunchedEffect
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) withFrameNanos { now ->
                val dt = if (previous == 0L) 0.0 else (now - previous) / 1e9
                previous = now
                clock.advance(dt, autoPlay)
            }
        }
    }
    val safe = WindowInsets.safeDrawing.asPaddingValues()
    fun toggle(i: Int) {
        if (reduced) return
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        clock.toggle(i)
    }
    BoxWithConstraints(modifier.fillMaxSize().background(Background)) {
        val width = maxWidth.value
        val height = maxHeight.value
        val top = safe.calculateTopPadding().value
        val bottom = safe.calculateBottomPadding().value
        Column(Modifier.offset(24.dp, (top + 8).dp).fillMaxWidth().padding(end = 48.dp)) {
            Row(Modifier.fillMaxWidth().height(34.dp), horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically) {
                BasicText(brand, style = fontStyle.copy(fontSize = 18.sp))
                BasicText("Lunch · 12:30", style = systemStyle.copy(
                    fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = Ink.copy(alpha = 0.6f),
                ))
            }
            BasicText(title, Modifier.padding(top = 12.dp), style = fontStyle.copy(
                fontSize = 34.sp, lineHeight = 40.sp, letterSpacing = (-1).sp,
            ))
        }
        Canvas(Modifier.fillMaxSize().semantics { contentDescription = "Salad bowl gauge, up to 500 kcal." }) {
            val f = clock.frame
            drawIntoCanvas { canvas ->
                val c = canvas.nativeCanvas
                c.save()
                c.scale(density.density, density.density)
                art.draw(c, width, height, f.amounts, f.needle)
                c.restore()
            }
        }
        Column(Modifier.align(Alignment.BottomCenter).padding(start = 20.dp, end = 20.dp, bottom = (bottom + 14).dp).fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(bottom = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                Names.forEachIndexed { i, name ->
                    Box(Modifier.height(38.dp).drawBehind {
                        val a = clock.frame.amounts[i]
                        val on = if (a > 0) clamp(a * 2) else 0.0
                        drawRoundRect(Ink.copy(alpha = (0.06 + 0.94 * on).toFloat()), cornerRadius = CornerRadius(19.dp.toPx()))
                    }.pointerInput(reduced) { detectTapGestures { toggle(i) } }
                        .semantics { role = Role.Button; contentDescription = "Toggle $name"; onClick { toggle(i); true } }
                        .padding(horizontal = 11.dp), contentAlignment = Alignment.Center) {
                        for (selected in listOf(false, true)) BasicText(name,
                            Modifier.graphicsLayer { alpha = if ((clock.frame.amounts[i] > 0.25) == selected) 1f else 0f },
                            style = systemStyle.copy(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = if (selected) Color.White else Ink),
                        )
                    }
                }
            }
            Box(Modifier.fillMaxWidth().height(58.dp).drawBehind {
                drawRoundRect(if (clock.frame.needle > 0.9) Green else Ink, cornerRadius = CornerRadius(29.dp.toPx()))
            }.semantics { role = Role.Button }, contentAlignment = Alignment.Center) {
                BasicText("Pick your toppings", Modifier.graphicsLayer {
                    alpha = (1 - clamp((clock.frame.needle - 0.9) / 0.1)).toFloat()
                }, style = fontStyle.copy(fontSize = 15.sp, color = Color.White))
                BasicText("Order bowl · $12.40", Modifier.graphicsLayer {
                    alpha = clamp((clock.frame.needle - 0.9) / 0.1).toFloat()
                }, style = fontStyle.copy(fontSize = 15.sp, color = Color.White))
            }
        }
    }
}
