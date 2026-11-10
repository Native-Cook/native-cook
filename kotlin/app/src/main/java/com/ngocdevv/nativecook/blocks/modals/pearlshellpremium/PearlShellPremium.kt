package com.ngocdevv.nativecook.blocks.modals.pearlshellpremium

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle

@Composable
fun PearlShellPremium(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val buzz: (Int)->Unit = remember(vibrator, view) {
        { kind ->
            if (kind == 1 && Build.VERSION.SDK_INT >= 30) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
            else if (kind == 1) vibrator?.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 12, 65, 18), -1))
            else if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (kind == 2) VibrationEffect.EFFECT_TICK else VibrationEffect.EFFECT_CLICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 20 else 10, 45))
        }
    }
    val closeInteraction = remember { MutableInteractionSource() }
    val restoreInteraction = remember { MutableInteractionSource() }
    val ctaInteraction = remember { MutableInteractionSource() }
    val closePressed = closeInteraction.collectIsPressedAsState()
    val restorePressed = restoreInteraction.collectIsPressedAsState()
    val ctaPressed = ctaInteraction.collectIsPressedAsState()
    val pressSpec = tween<Float>(150, easing = CubicBezierEasing(0.23f, 1f, 0.32f, 1f))
    val closePress = animateFloatAsState(if (closePressed.value) 1f else 0f, pressSpec, label = "Close press")
    val restorePress = animateFloatAsState(if (restorePressed.value) 1f else 0f, pressSpec, label = "Restore press")
    val ctaPress = animateFloatAsState(if (ctaPressed.value) 1f else 0f, pressSpec, label = "Start free week press")
    val art = remember(context) { PearlShellPremiumArt(context) }
    val driver = remember(autoPlay, reduced) { PearlShellPremiumDriver(autoPlay, reduced, buzz) }
    val version = remember { mutableLongStateOf(0L) }
    var wake by remember { mutableIntStateOf(0) }
    var plan by remember { mutableIntStateOf(0) }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver, lifecycle, wake) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (driver.moving) withFrameNanos { nanos ->
                val dt = (if (previous == 0L) 0.016 else (nanos-previous)/1e9).coerceIn(0.0, 0.5)
                previous = nanos
                driver.step(dt)
                version.longValue++
            }
        }
    }
    val top = WindowInsets.statusBars.getTop(density)/density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density.toDouble()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val l = remember(maxWidth, maxHeight, top, bottom) {
            PearlShellPremiumLayout(maxWidth.value.toDouble(), maxHeight.value.toDouble(), top, bottom)
        }
        Canvas(Modifier.fillMaxSize()) {
            version.longValue
            drawIntoCanvas { art.draw(it.nativeCanvas, l, driver.frame, density.density, plan, ctaPress.value.toDouble(), reduced, closePress.value.toDouble(), restorePress.value.toDouble()) }
        }
        Box(Modifier
            .offset((l.heroX-l.heroS*1.3).dp, (l.heroY-l.heroS*1.5).dp)
            .size((l.heroS*2.6).dp, (l.heroS*2.1).dp)
            .semantics {
                role = Role.Button
                contentDescription = "A pearl shell"
                onClick("Lift the shell's lip to open it") { driver.toggle(); wake++; version.longValue++; true }
            }
            .pointerInput(driver, l) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val tracker = VelocityTracker()
                    tracker.addPosition(down.uptimeMillis, start)
                    driver.begin(); wake++; version.longValue++
                    down.consume()
                    var released = false
                    try {
                        do {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            if (change.pressed) {
                                driver.drag(((change.position.x-start.x)/density.density).toDouble(), ((change.position.y-start.y)/density.density).toDouble(), l.heroS)
                                version.longValue++
                            } else {
                                driver.end((tracker.calculateVelocity().y/density.density).toDouble(), l.heroS)
                                released = true; wake++; version.longValue++
                            }
                            change.consume()
                        } while (change.pressed)
                    } finally {
                        if (!released) { driver.end(0.0, l.heroS, true); wake++; version.longValue++ }
                    }
                }
            })
        PearlShellPremiumHit(8.0, l.barTop, 44.0, 44.0, "Close", closeInteraction) {}
        PearlShellPremiumHit(l.w-92, l.barTop, 84.0, 44.0, "Restore purchases", restoreInteraction) {}
        Box(Modifier.offset(16.dp, l.plansTop.dp).size((l.w-32).dp, 64.dp).selectableGroup()) {
            for (i in 0..1) {
                val width = (l.w-44)/2
                Box(Modifier.offset((i*(width+12)).dp, 0.dp).size(width.dp, 64.dp)
                    .selectable(i == plan, interactionSource = remember { MutableInteractionSource() }, indication = null, role = Role.RadioButton) {
                        plan = i; buzz(2)
                    }.semantics { contentDescription = if (i == 0) "Yearly, $39.99, Save 44%" else "Monthly, $5.99" })
            }
        }
        PearlShellPremiumHit(16.0, l.ctaTop, l.w-32, 56.0, "Start free week", ctaInteraction) {}
        // Text remains available to TalkBack although its pixels are cached native glyphs.
        Box(Modifier.offset(0.dp, l.titleTop.dp).size(l.w.dp, 44.dp).semantics { heading(); contentDescription = "Tidal Plus" })
        for (i in 0..2) {
            val copy = listOf("Every sleep story and soundscape", "Listen offline, anywhere", "New sessions every week")[i]
            Box(Modifier.offset(0.dp, (l.featTop+i*30).dp).size(l.w.dp, 22.dp).semantics { contentDescription = copy })
        }
        Box(Modifier.offset(0.dp, l.fineTop.dp).size(l.w.dp, 18.dp).semantics {
            contentDescription = if (plan == 0) "Then $39.99 a year. Cancel anytime." else "Then $5.99 a month. Cancel anytime."
        })
    }
}

@Composable
private fun PearlShellPremiumHit(x: Double, y: Double, w: Double, h: Double, label: String, interaction: MutableInteractionSource, action: ()->Unit) {
    Box(Modifier.offset(x.dp, y.dp).size(w.dp, h.dp)
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClickLabel = label, onClick = action)
        .semantics { contentDescription = label })
}
