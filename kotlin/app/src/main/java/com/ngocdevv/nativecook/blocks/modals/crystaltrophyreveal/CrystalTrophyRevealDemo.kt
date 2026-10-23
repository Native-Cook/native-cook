package com.ngocdevv.nativecook.blocks.modals.crystaltrophyreveal

import android.database.ContentObserver
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import kotlin.math.PI
import kotlin.math.max

@Composable
fun CrystalTrophyRevealDemo() {
    CrystalTrophyReveal(autoPlay = true)
}

/** A complete native theatre. The performance is optional; all inputs share its commands. */
@Composable
fun CrystalTrophyReveal(autoPlay: Boolean = false, onClose: () -> Unit = {}, onShare: () -> Unit = {}) {
    val context = LocalContext.current
    val view = LocalView.current
    val window = LocalActivity.current?.window
    var reduced by remember {
        mutableStateOf(Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f)
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE), false, observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    DisposableEffect(window, view) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val bars = WindowInsetsCompat.Type.systemBars()
        val oldBehavior = controller?.systemBarsBehavior
        controller?.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller?.hide(bars)
        onDispose {
            controller?.show(bars)
            if (oldBehavior != null) controller.systemBarsBehavior = oldBehavior
        }
    }
    val renderer = remember(context) { if (Build.VERSION.SDK_INT >= 33) CrystalTrophyRenderer(context) else null }
    val fallback = remember { CrystalTrophyFallback() }
    val typography = remember(context) { CrystalTrophyTypography(context) }
    val input = remember { CrystalTrophyInput() }
    val state = remember(autoPlay) { arrayOf(CrystalTrophyState(autoPlay, reduced || renderer == null)) }
    val redraw = remember { mutableIntStateOf(0) }
    var phase by remember { mutableStateOf(state[0].phase) }
    var copied by remember { mutableStateOf(false) }
    val closeCallback by rememberUpdatedState(onClose)
    val shareCallback by rememberUpdatedState(onShare)
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize()) {
        // Keep the source's phone composition, expanding vertically for the available aspect ratio.
        val pointDp = maxWidth.value / 402f
        val pointPx = pointDp * density.density
        val safe = WindowInsets.safeDrawing
        val top = max(62.0, safe.getTop(density) / pointPx.toDouble())
        val bottom = max(34.0, safe.getBottom(density) / pointPx.toDouble())
        val frame = remember(maxWidth, maxHeight, top, bottom) {
            CrystalTrophyFrame(402.0, maxHeight.value / pointDp.toDouble(), top, bottom)
        }
        LaunchedEffect(autoPlay, reduced, frame) {
            state[0].reduced = reduced || renderer == null
            var last = withFrameNanos { it }
            while (true) {
                val now = withFrameNanos { it }
                val s = CrystalTrophyPerformance.advance(state[0], (now - last) / 1e9, frame.width)
                state[0] = s
                input.held(s, frame)
                if (s.events and 8 != 0) view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
                else if (s.events and 2 != 0) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                else if (s.events and 21 != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                if (s.events and 32 != 0) closeCallback()
                if (s.events and 64 != 0) shareCallback()
                s.events = 0
                if (phase != s.phase) phase = s.phase
                if (copied != (s.copied > 0.5)) copied = s.copied > 0.5
                redraw.intValue++
                last = now
            }
        }
        Canvas(Modifier.fillMaxSize().pointerInput(frame, pointPx) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis, down.position)
                input.begin(state[0], frame, down.position.x / pointPx.toDouble(), down.position.y / pointPx.toDouble())
                down.consume()
                var released = false
                try {
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id }
                        if (change == null || event.changes.count { it.pressed } > 1) break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        input.move(state[0], frame, change.position.x / pointPx.toDouble(), change.position.y / pointPx.toDouble())
                        if (!change.pressed) {
                            input.end(state[0], frame, tracker.calculateVelocity().x / pointPx.toDouble(), false)
                            released = true
                        }
                        change.consume()
                    }
                } finally {
                    if (!released) input.end(state[0], frame, 0.0, true)
                }
            }
        }) {
            redraw.intValue
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(pointPx, pointPx)
            if (renderer != null) renderer.stage(canvas, frame, state[0], pointPx)
            else fallback.draw(canvas, frame, state[0])
            typography.draw(canvas, frame, state[0], renderer)
            canvas.restore()
        }
        fun access(target: String, delta: Double = 0.0): Boolean {
            input.access(state[0], frame, target, delta)
            return true
        }
        if (phase == "closed") {
            Box(Modifier.fillMaxSize().semantics {
                role = Role.Button
                contentDescription = "Closed curtain. Tap or pull it apart to reveal the trophy."
                onClick("Reveal trophy") { access("curtain") }
            })
        } else if (phase == "open") {
            Box(Modifier.offset(32.dp * pointDp, ((frame.titleY - frame.titleSize) * pointDp).dp)
                .size(338.dp * pointDp, (frame.titleSize * 2.2 * pointDp).dp).semantics {
                    heading()
                    contentDescription = "Perfect Month. Won on Oct 13, 2026"
                })
            Box(Modifier.offset(((frame.originX - frame.unit * 1.3 - 24) * pointDp).dp,
                ((frame.originY - frame.unit * 3.05 - 24) * pointDp).dp)
                .size(((frame.unit * 2.6 + 48) * pointDp).dp, ((frame.unit * 3.65 + 48) * pointDp).dp).semantics {
                    contentDescription = "Crystal trophy engraved with 30. Drag to turn it, tap to make it flash."
                    stateDescription = "Perfect month trophy"
                    onClick("Flash trophy") { access("trophy") }
                    customActions = listOf(
                        CustomAccessibilityAction("Turn right") { access("trophy", PI / 12) },
                        CustomAccessibilityAction("Turn left") { access("trophy", -PI / 12) })
                })
            Box(Modifier.offset((frame.shareX * pointDp).dp, (frame.bodyTop * pointDp).dp)
                .size((frame.shareWidth * pointDp).dp, (44 * pointDp).dp).semantics {
                    contentDescription = "Thirty days, thirty sessions. Not a single one missed."
                })
            Box(Modifier.offset((frame.shareX * pointDp).dp, (frame.shareY * pointDp).dp)
                .size((frame.shareWidth * pointDp).dp, (56 * pointDp).dp).semantics {
                    role = Role.Button
                    contentDescription = "Share this trophy"
                    stateDescription = if (copied) "Link copied" else "Ready to share"
                    onClick { access("share") }
                })
            Box(Modifier.offset(((frame.closeX - 2) * pointDp).dp, ((frame.closeY - 2) * pointDp).dp)
                .size((44 * pointDp).dp).semantics {
                    role = Role.Button
                    contentDescription = "Close"
                    onClick { access("close") }
                })
        }
    }
}
