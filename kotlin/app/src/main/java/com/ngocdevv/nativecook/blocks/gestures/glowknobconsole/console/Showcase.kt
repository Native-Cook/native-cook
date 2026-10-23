// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import androidx.compose.foundation.background
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.Shared
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Haptics
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.rememberIncreaseContrast
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.rememberReducedMotion
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.max
import kotlin.math.min

// The specimen keeps phone proportions on tablets: past this width it would
// only grow, not gain detail.
private const val MAX_WIDTH = 520f
// The renderer is built once the power-on has played, off its critical path.
private const val WARM_UP = 1400L
// Height of the lift layer around the housing, in housing heights.
private const val LIFT_ROOM = 3.4

/**
 * The page (console/index.tsx): the tactile Volume / Brightness console
 * standing on a white glass stage.
 */
@Composable
fun Showcase() {
    Perf.recomposed("Showcase")
    val context = LocalContext.current
    // Increase Contrast keeps the object and its states but drops decorative
    // light: spill, glass, sheen, reflection and the page ambience.
    val highContrast = rememberIncreaseContrast()
    val fx = remember(highContrast) {
        if (highContrast) allEffects.copy(spill = false, glass = false, sheen = false, reflection = false, ambient = false)
        else allEffects
    }
    val reduced = rememberReducedMotion()
    val haptics = remember { Haptics(context) }
    val strings = remember { Strings(Locale.getDefault().language == "vi") }
    val control = remember(fx, reduced) { TactileControl(haptics, fx, reduced) }
    val tilt = remember { Shared(Tilt(0.0, 0.0)) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var resumed by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ -> resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(control) {
        control.powerOnOnce()
        onDispose { control.dispose() }
    }
    LaunchedEffect(control, resumed) { control.setForeground(resumed) }

    val density = LocalDensity.current
    val fontScale = density.fontScale
    val largeText = fontScale >= 1.35f
    val liftable = fx.lift && !control.reduced && !largeText
    var warm by remember { mutableStateOf(false) }
    val lift = remember(control) { ConsoleLift(control, fx, tilt, haptics) }
    DisposableEffect(lift) { onDispose { lift.dispose() } }
    lift.setEnabled(liftable && warm)
    LaunchedEffect(liftable) {
        if (!liftable) return@LaunchedEffect
        delay(WARM_UP)
        warm = true
    }

    // The page as seen: white, dimmed by the brightness ambience.
    val dimmed = fx.ambient && !control.reduced
    val pageTone = { if (dimmed) 1 - (1 - max(0.0, min(1.0, control.brightness.get()))) * 0.1 else 1.0 }

    var origin by remember { mutableStateOf<Pair<Float, Float>?>(null) }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color.White)) {
        val rootW = maxWidth.value
        val rootH = maxHeight.value
        val edge = if (rootW < 375f) 16f else 20f
        val width = min(MAX_WIDTH, rootW - 2 * edge)
        val scale = width / 1000f
        StageAmbient(control, fx)
        Box(Modifier.fillMaxSize().padding(horizontal = edge.dp), contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .width(width.dp)
                    .onGloballyPositioned { c ->
                        val p = c.positionInRoot()
                        origin = (p.x / density.density) to (p.y / density.density)
                    },
            ) {
                if (!largeText && (fx.spill || fx.reflection)) {
                    // Absolutely positioned under the specimen: takes no layout space.
                    Box(
                        Modifier
                            .layout { measurable, constraints ->
                                val p = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
                                layout(p.width, 0) { p.place(0, 0) }
                            }
                            .graphicsLayer { alpha = (1 - min(1.0, lift.lift.get() * 1.6)).toFloat() },
                    ) {
                        StageFloor(control, fx, tilt, scale)
                    }
                }
                TactileConsole(
                    control = control,
                    fx = fx,
                    tilt = tilt,
                    highContrast = highContrast,
                    haptics = haptics,
                    strings = strings,
                    pickUp = if (liftable) lift.pickUp else null,
                    standIn = lift.covered,
                )
            }
        }
        // The lift layer spans the page's width and LIFT_ROOM housing heights
        // around the housing, so the vanishing point sits on the object.
        val o = origin
        if (liftable && o != null) {
            val housing = Box(o.first.toDouble(), (o.second + Specimen.top * scale), 1000.0 * scale, 284.0 * scale)
            val height = min(rootH.toDouble(), housing.height * LIFT_ROOM)
            val top = housing.y + housing.height / 2 - height / 2
            val frame = Box(0.0, top, rootW.toDouble(), height)
            LiftLayer(
                lift = lift,
                frame = frame,
                housing = housing.copy(y = housing.y - top),
                active = warm && resumed,
                glow = fx.liftLight,
                fog = pageTone,
            )
        }
        if (liftable) {
            val navBottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp() }
            val hint = if (lift.lifted) strings.t("Drag to turn it over · Tap to set it down", "Kéo để lật · Chạm để đặt xuống")
            else if (lift.used) " " else strings.t("Hold the console to pick it up", "Giữ console để nhấc lên")
            BasicText(
                hint,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(bottom = navBottom + 28.dp)
                    .fillMaxWidth()
                    .graphicsLayer { alpha = if (lift.lifted) 1f else (1 - min(1.0, lift.lift.get() * 2)).toFloat() },
                style = TextStyle(
                    fontFamily = Fonts.manropeRegular,
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = Color(Tactile.hint),
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (fx.tilt && !control.reduced) TiltSource(tilt)
    }
}
