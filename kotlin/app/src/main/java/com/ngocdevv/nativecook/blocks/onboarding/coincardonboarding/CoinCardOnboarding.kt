package com.ngocdevv.nativecook.blocks.onboarding.coincardonboarding

import android.app.Activity
import android.content.ContextWrapper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.channels.Channel
import kotlin.math.*

@Composable
fun CoinCardOnboarding(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val resources = remember(context) { CoinCardOnboardingResources(context) }
    val art = remember(resources) { CoinCardOnboardingArt(resources) }
    val audio = remember(context, reduced) { if (reduced) null else CoinCardOnboardingAudio(context) }
    val driver = remember { CoinCardOnboardingDriver() }
    val pos = remember { mutableDoubleStateOf(0.0) }
    val wake = remember { Channel<Unit>(Channel.CONFLATED) }
    val density = LocalDensity.current
    val top = WindowInsets.safeDrawing.getTop(density) / density.density
    val bottom = WindowInsets.safeDrawing.getBottom(density) / density.density
    fun buzz(kind: Int) { view.performHapticFeedback(if (kind == 0) HapticFeedbackConstants.CLOCK_TICK else HapticFeedbackConstants.VIRTUAL_KEY) }
    fun goTo(n: Int) { driver.goTo(n); buzz(1); audio?.play("page"); wake.trySend(Unit) }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(driver, autoPlay, reduced, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                if ((!autoPlay || reduced) && !driver.moving()) { wake.receive(); previous = 0L }
                withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    val before = floor(driver.p + 0.5)
                    driver.step(dt, autoPlay, reduced)
                    if (floor(driver.p + 0.5) != before) { audio?.play("flip"); buzz(0) }
                    pos.doubleValue = driver.p
                }
            }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xfffdf7fb))) {
        val w = maxWidth.value
        val h = maxHeight.value
        val current by remember { derivedStateOf { floor(pos.doubleValue + 0.5).toInt().coerceIn(0, 2) } }
        Canvas(Modifier.fillMaxSize().semantics {
            contentDescription = CoinCardOnboardingMotion.titles[current].replace('\n', ' ') + ". " + CoinCardOnboardingMotion.bodies[current].replace('\n', ' ')
            stateDescription = "Page ${current + 1} of 3"
            progressBarRangeInfo = ProgressBarRangeInfo(current.toFloat(), 0f..2f, 1)
            setProgress { value -> goTo(value.roundToInt()); true }
            customActions = listOf(CustomAccessibilityAction("Increment") { goTo(current + 1); true }, CustomAccessibilityAction("Decrement") { goTo(current - 1); true })
        }.pointerInput(driver, w, reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                driver.begin(); wake.trySend(Unit)
                val start = down.position
                val tracker = VelocityTracker(); tracker.addPosition(down.uptimeMillis, start)
                var dragging = false
                try {
                    do {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        val dx = (change.position.x - start.x) / density.density
                        if (change.pressed && (dragging || abs(dx) > 8)) {
                            driver.update(dx.toDouble(), w.toDouble(), !dragging)
                            dragging = true
                            change.consume()
                        }
                    } while (change.pressed && event.changes.any { it.pressed })
                } finally {
                    if (driver.end((tracker.calculateVelocity().x / density.density).toDouble(), w.toDouble())) audio?.play("page")
                    wake.trySend(Unit)
                }
            }
        }) {
            drawIntoCanvas {
                val canvas = it.nativeCanvas
                canvas.save(); canvas.scale(density.density, density.density)
                art.draw(canvas, w, h, top, pos.doubleValue, reduced)
                canvas.restore()
            }
        }
        Row(Modifier.offset(20.dp, (top + 8).dp).width((w - 40).dp).height(40.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(32.dp).background(Color(0xffe0479a), CircleShape), contentAlignment = Alignment.Center) {
                    repeat(4) { i -> Box(Modifier.size(16.dp, 3.dp).graphicsLayer { rotationZ = i * 45f }.background(Color.White, CircleShape)) }
                }
                CoinCardOnboardingText("Sorel", resources.title, 17f, 22f, Color(0xff1b1b1f))
            }
            CoinCardOnboardingButton("Skip to the last page", Modifier.height(34.dp), { goTo(2) }) {
                Box(Modifier.fillMaxHeight().background(Color.White, CircleShape).padding(horizontal = 16.dp), contentAlignment = Alignment.Center) {
                    CoinCardOnboardingText("Skip", resources.medium, 14f, 18f, Color(0xff1b1b1f))
                }
            }
        }
        Box(Modifier.offset(24.dp, (top + 420).dp).width((w - 48).dp).height(150.dp)) {
            repeat(3) { i ->
                Column(Modifier.fillMaxWidth().graphicsLayer {
                    val d = pos.doubleValue - i
                    alpha = CoinCardOnboardingMotion.clamp(1 - abs(d) * 1.8).toFloat()
                    translationX = (-d * 44 * density.density).toFloat()
                }.then(if (i != current) Modifier.clearAndSetSemantics {} else Modifier), horizontalAlignment = Alignment.CenterHorizontally) {
                    CoinCardOnboardingText(CoinCardOnboardingMotion.titles[i], resources.title, 31f, 37f, Color(0xff141416), -0.4f)
                    Spacer(Modifier.height(14.dp))
                    CoinCardOnboardingText(CoinCardOnboardingMotion.bodies[i], resources.regular, 15f, 21f, Color(0xff55555c))
                }
            }
        }
        CoinCardOnboardingDashes(pos, Modifier.offset(y = (h - bottom - 154).dp).fillMaxWidth())
        Column(Modifier.offset(20.dp, (h - bottom - 136).dp).width((w - 40).dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            CoinCardOnboardingButton("Get started", Modifier.fillMaxWidth().height(54.dp), { buzz(1) }) {
                Box(Modifier.fillMaxSize().background(Color(0xff0d0d0f), CircleShape), contentAlignment = Alignment.Center) {
                    CoinCardOnboardingText("Get Started", resources.medium, 16f, 21f, Color.White)
                }
            }
            CoinCardOnboardingButton("Sign in", Modifier.fillMaxWidth().height(54.dp), { buzz(0) }) {
                Box(Modifier.fillMaxSize().border(1.5.dp, Color(0xff141416), CircleShape), contentAlignment = Alignment.Center) {
                    CoinCardOnboardingText("Sign In", resources.medium, 16f, 21f, Color(0xff141416))
                }
            }
        }
    }
}

@Composable
private fun CoinCardOnboardingText(text: String, face: FontFamily, size: Float, lineHeight: Float, color: Color, track: Float = 0f) {
    val density = LocalDensity.current
    // RN disables font scaling for all of this screen's UI copy.
    Text(text, color = color, textAlign = TextAlign.Center, style = TextStyle(fontFamily = face, fontSize = (size / density.fontScale).sp, lineHeight = (lineHeight / density.fontScale).sp, letterSpacing = (track / density.fontScale).sp, platformStyle = PlatformTextStyle(includeFontPadding = false)))
}

@Composable
private fun CoinCardOnboardingDashes(pos: DoubleState, modifier: Modifier) {
    val p = pos.doubleValue
    Row(modifier.clearAndSetSemantics {}, horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally)) {
        repeat(3) { i ->
            val k = CoinCardOnboardingMotion.clamp(1 - abs(p - i)).toFloat()
            Box(Modifier.width((6 + 16 * k).dp).height(4.dp).graphicsLayer { alpha = 0.3f + 0.7f * k }.background(Color(0xff141416), RoundedCornerShape(2.dp)))
        }
    }
}

@Composable
private fun CoinCardOnboardingButton(label: String, modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(modifier.graphicsLayer { scaleX = if (pressed) 0.97f else 1f; scaleY = scaleX }
        .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick)
        .semantics(mergeDescendants = true) { contentDescription = label }, contentAlignment = Alignment.Center) { content() }
}
