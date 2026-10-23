package com.ngocdevv.nativecook.blocks.gestures.pullcordtheme

import android.content.Context
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import kotlin.math.hypot
import kotlin.math.roundToInt

private const val PullCordThemeTapSlop = 8f
private const val PullCordThemeTapSeconds = 0.25f

private const val PullCordThemeReadingLabel =
    "Chapter Seven. Low Water. By the time the tide turned, Mara had counted the boats twice. Eleven out, ten back. " +
        "She wrote the number in the ledger anyway, in pencil, the way her father had taught her: nothing in ink until morning. " +
        "The lamp above the desk swung a little whenever the door opened, and the light slid across the page and back. " +
        "Outside, fog had taken the breakwater. The last ferry’s horn came through it late and flattened, like a voice from another room. " +
        "She left the window open. If the eleventh boat came in, she wanted to hear it before she saw it. " +
        "Her father used to say the sea kept its own books, and that it was never late with a payment, only with the news. " +
        "Page 112 of 304. 9 min left in chapter."

private fun pullCordThemeReducedMotion(context: Context): Boolean =
    Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/**
 * A reading page whose pendant lamp is its Day/Night switch. The fixed-step simulation, light field and all drawing
 * values are direct ports of the approved React Native block.
 */
@Composable
fun PullCordTheme(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val reducedMotion = remember(context) { pullCordThemeReducedMotion(context) }
    val painter = remember(context) { PullCordThemePainter(context) }
    var engine by remember { mutableStateOf<PullCordThemeEngine?>(null) }
    var frameTick by remember { mutableLongStateOf(0L) }
    var day by remember { mutableStateOf(true) }

    LaunchedEffect(engine) {
        val current = engine ?: return@LaunchedEffect
        var previous = 0L
        while (true) {
            withFrameNanos { now ->
                if (previous != 0L) {
                    current.step(((now - previous) / 1_000_000_000.0).toFloat())
                    if (current.takeClick()) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                    val nextDay = current.light == 1f
                    if (nextDay != day) day = nextDay
                    frameTick++
                }
                previous = now
            }
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF1EBE0))
            .clipToBounds(),
    ) {
        Canvas(
            modifier = Modifier
                .fillMaxSize()
                .semantics { contentDescription = PullCordThemeReadingLabel }
                .onSizeChanged { size ->
                    if (size.width <= 0 || size.height <= 0) return@onSizeChanged
                    val old = engine?.geometry
                    if (old?.width != size.width.toFloat() || old.height != size.height.toFloat()) {
                        engine = PullCordThemeEngine(
                            pullCordThemeGeometry(size.width.toFloat(), size.height.toFloat()),
                            reducedMotion,
                            autoPlay,
                        )
                        day = true
                    }
                }
                .pointerInput(engine) {
                    val current = engine ?: return@pointerInput
                    awaitPointerEventScope {
                        while (true) {
                            val first = awaitPointerEvent(PointerEventPass.Main).changes.firstOrNull { it.pressed } ?: continue
                            val pointerId = first.id
                            val downPosition = first.position
                            val downTime = first.uptimeMillis
                            var lastPosition = downPosition
                            var moved = false
                            val velocity = VelocityTracker().apply { addPosition(downTime, downPosition) }
                            current.input(PullCordThemeInput.Down(downPosition.x, downPosition.y))
                            first.consume()

                            var finished = false
                            while (!finished) {
                                val event = awaitPointerEvent(PointerEventPass.Main)
                                val change = event.changes.firstOrNull { it.id == pointerId }
                                if (change == null) {
                                    current.input(PullCordThemeInput.Up(0f, 0f, cancelled = true))
                                    break
                                }
                                velocity.addPosition(change.uptimeMillis, change.position)
                                lastPosition = change.position
                                val scale = current.geometry.scale
                                if (hypot(lastPosition.x - downPosition.x, lastPosition.y - downPosition.y) > PullCordThemeTapSlop * scale) {
                                    moved = true
                                }
                                if (change.pressed) {
                                    val v = velocity.calculateVelocity()
                                    current.input(PullCordThemeInput.Move(change.position.x, change.position.y, v.x, v.y))
                                    change.consume()
                                } else {
                                    val v = velocity.calculateVelocity()
                                    current.input(PullCordThemeInput.Up(v.x, v.y))
                                    val elapsed = (change.uptimeMillis - downTime) / 1000f
                                    if (!moved && elapsed <= PullCordThemeTapSeconds) {
                                        current.input(PullCordThemeInput.Tap(lastPosition.x, lastPosition.y))
                                    }
                                    change.consume()
                                    finished = true
                                }
                            }
                        }
                    }
                },
        ) {
            if (frameTick >= 0L) engine?.let { painter.draw(drawContext.canvas.nativeCanvas, it) }
        }

        engine?.let { current ->
            val rest = current.restPullCentre()
            val hitPixels = PullCordThemeHitSize * current.geometry.scale
            val hitDp = with(density) { hitPixels.toDp() }
            Box(
                Modifier
                    .offset {
                        IntOffset(
                            (rest.x - hitPixels / 2f).roundToInt(),
                            (rest.y - hitPixels / 2f).roundToInt(),
                        )
                    }
                    .size(hitDp)
                    .semantics {
                        role = Role.Switch
                        contentDescription = "Reading lamp. Pull the chain to switch between the Day and Night page"
                        stateDescription = if (day) "Day page" else "Night page"
                        onClick("Pull chain") {
                            current.input(PullCordThemeInput.Toggle)
                            true
                        }
                    },
            )
        }
    }
}
