package com.ngocdevv.nativecook.blocks.modals.champagnersvp

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.hypot

@Composable
fun ChampagneRsvp(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var reduced by remember(context) { mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f) }
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
    val driver = remember(autoPlay, reduced) {
        ChampagneRsvpDriver(autoPlay, reduced) { success ->
            if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (success) VibrationEffect.EFFECT_CLICK else VibrationEffect.EFFECT_HEAVY_CLICK))
            else vibrator?.vibrate(VibrationEffect.createOneShot(if (success) 12 else 20, 80))
        }
    }
    val art = remember { ChampagneRsvpArt() }
    val regular = remember { FontFamily(Font("champagnersvp/champagnersvp_Inter-Regular.ttf", context.assets)) }
    val medium = remember { FontFamily(Font("champagnersvp/champagnersvp_Inter-Medium.ttf", context.assets)) }
    val semibold = remember { FontFamily(Font("champagnersvp/champagnersvp_Inter-SemiBold.ttf", context.assets)) }
    val display = remember { FontFamily(Font("champagnersvp/champagnersvp_InstrumentSerif-Regular.ttf", context.assets)) }
    var done by remember(driver) { mutableStateOf(false) }
    val tick = remember(driver) { mutableIntStateOf(0) }
    var wake by remember { mutableIntStateOf(0) }
    var pressed by remember { mutableIntStateOf(0) }
    val top = with(density) { WindowInsets.statusBars.getTop(this).toDp().value }
    val bottom = with(density) { WindowInsets.navigationBars.getBottom(this).toDp().value }
    val ink = Color(0xFF2A1F1D)
    val mute = Color(0xFF8F7F7A)
    val curve = remember { CubicBezierEasing(0.23f, 1f, 0.32f, 1f) }
    val replyAlpha by animateFloatAsState(if (done) 1f else 0f, tween(260, easing = curve), label = "reply")
    val ctaPress by animateFloatAsState(if (pressed == 1) 1f else 0f, tween(150, easing = curve), label = "accept press")
    val linkPress by animateFloatAsState(if (pressed == 2) 1f else 0f, tween(150, easing = curve), label = "decline press")

    fun accept() { driver.accept(); done = driver.done; wake++; tick.intValue++ }
    fun decline() { driver.done = false; done = false; tick.intValue++ }

    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (before != null) controller?.isAppearanceLightStatusBars = before }
    }
    LaunchedEffect(driver, lifecycle, wake) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (driver.moving) withFrameNanos { stamp ->
                val dt = (if (previous == 0L) 0.0 else (stamp - previous) / 1e9).coerceIn(0.0, 0.5)
                previous = stamp
                driver.step(dt)
                if (done != driver.done) done = driver.done
                tick.intValue++
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFFFAF3F1))) {
        val l = remember(maxWidth, maxHeight, top, bottom) { ChampagneRsvpLayout(maxWidth.value, maxHeight.value, top, bottom) }
        Canvas(Modifier.matchParentSize().pointerInput(driver, l) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val start = down.position / density.density
                var end = start
                val target = if (start.x >= 16 && start.x <= l.w - 16 && start.y >= l.ctaTop && start.y <= l.ctaTop + 56) 1
                    else if (start.x >= 16 && start.x <= l.w - 16 && start.y >= l.linkTop && start.y <= l.linkTop + 44) 2
                    else if (l.inStage(start.x, start.y)) 3 else 0
                pressed = if (target == 1 || target == 2) target else 0
                if (target == 3) { driver.takeOver(); wake++ }
                down.consume()
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        end = change.position / density.density
                        if (target == 3 && change.pressed) {
                            if (!driver.dragging && (end - start).getDistance() >= 2) driver.beginDrag()
                            driver.drag(end.x - l.stageX, end.x - start.x, l)
                            tick.intValue++
                            if (done != driver.done) done = driver.done
                        }
                        change.consume()
                    } while (change.pressed && event.changes.any { it.pressed })
                } finally {
                    pressed = 0
                    if (target == 3) driver.endDrag()
                    else if (hypot(end.x - start.x, end.y - start.y) < 16) {
                        if (target == 1) accept() else if (target == 2) decline()
                    }
                }
            }
        }) {
            tick.intValue
            drawIntoCanvas { c ->
                c.nativeCanvas.save()
                c.nativeCanvas.scale(density.density, density.density)
                art.draw(c.nativeCanvas, l, driver.frame, density.density)
                c.nativeCanvas.restore()
            }
        }
        @Composable
        fun label(text: String, font: FontFamily, size: Int, height: Int, y: Float, color: Color, tracking: Float = 0f, modifier: Modifier = Modifier) {
            Text(text, modifier.offset(y = y.dp).fillMaxWidth().height(height.dp),
                style = TextStyle(fontFamily = font, fontSize = (size / density.fontScale).sp, lineHeight = (height / density.fontScale).sp,
                    letterSpacing = (tracking / density.fontScale).sp, color = color, textAlign = TextAlign.Center,
                    platformStyle = PlatformTextStyle(includeFontPadding = false)))
        }
        label("THE GLASSHOUSE · LISBON", medium, 12, 16, l.venueTop, Color(0xFFB4893F), 1.6f)
        label("Clara & Theo", display, 44, 48, l.namesTop, ink, modifier = Modifier.semantics { heading() })
        label("Saturday 14 June · 6 pm", regular, 15, 22, l.dateTop, mute)
        label("Will you raise a glass with us?", medium, 15, 22, l.lineTop, ink, modifier = Modifier.graphicsLayer {
            alpha = 1 - replyAlpha; translationY = if (reduced) 0f else 6 * replyAlpha * density.density
        }.semantics { if (done) hideFromAccessibility() })
        label("See you there, Ana. Dancing till late.", medium, 15, 22, l.lineTop, ink, modifier = Modifier.graphicsLayer {
            alpha = replyAlpha; translationY = if (reduced) 0f else 6 * (1 - replyAlpha) * density.density
        }.semantics { liveRegion = LiveRegionMode.Polite; if (!done) hideFromAccessibility() })
        Box(Modifier.offset(x = 16.dp, y = l.ctaTop.dp).width((l.w - 32).dp).height(56.dp)
            .graphicsLayer { scaleX = if (reduced) 1f else 1 - 0.04f * ctaPress; scaleY = scaleX; alpha = if (reduced) 1 - 0.28f * ctaPress else 1f }
            .background(ink, RoundedCornerShape(28.dp)).semantics(mergeDescendants = true) {
                role = Role.Button; onClick { accept(); true }
            }) {
            Text(if (done) "Add to Calendar" else "Joyfully Accept", Modifier.align(androidx.compose.ui.Alignment.Center),
                fontFamily = semibold, fontSize = (17 / density.fontScale).sp, color = Color(0xFFFCF8F6))
        }
        Box(Modifier.offset(x = 16.dp, y = l.linkTop.dp).width((l.w - 32).dp).height(44.dp)
            .graphicsLayer { scaleX = if (reduced) 1f else 1 - 0.04f * linkPress; scaleY = scaleX; alpha = if (reduced) 1 - 0.28f * linkPress else 1f }
            .semantics(mergeDescendants = true) { role = Role.Button; onClick { decline(); true } }) {
            Text(if (done) "Change my reply" else "Regretfully Decline", Modifier.align(androidx.compose.ui.Alignment.Center), fontFamily = medium, fontSize = (15 / density.fontScale).sp, color = mute)
        }
        Box(Modifier.offset(x = l.stageX.dp, y = l.stageY.dp).size((l.heroS * 4.8f).dp, (l.heroS * 3).dp)
            .semantics { role = Role.Image; contentDescription = "Two flutes of champagne. Drag them together to clink" })
        Canvas(Modifier.matchParentSize()) {
            tick.intValue
            val f = driver.frame
            val r = (22 * (1 - 0.12 * f.fingerPress)).toFloat() * density.density
            val center = Offset(l.w / 2 * density.density, (l.ctaTop + 28) * density.density)
            drawCircle(ink.copy(alpha = (0.1 * f.finger).toFloat()), r, center)
            drawCircle(ink.copy(alpha = (0.32 * f.finger).toFloat()), r, center, style = Stroke(1.5f * density.density))
        }
    }
}
