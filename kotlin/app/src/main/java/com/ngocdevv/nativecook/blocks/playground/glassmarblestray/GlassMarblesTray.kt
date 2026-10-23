package com.ngocdevv.nativecook.blocks.playground.glassmarblestray

import android.graphics.Bitmap
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

private val Ink = Color(MARBLES_INK)
private val Muted = Color(MARBLES_MUTED)
internal val MarblesBackground = Color(MarblesTable[2])

/** The finger's speed over its last 100 ms of samples (dp/s), measured by hand. */
private class MarblesVelocity {
    private val t = LongArray(64)
    private val x = DoubleArray(64)
    private val y = DoubleArray(64)
    private var n = 0

    fun reset() {
        n = 0
    }

    fun add(time: Long, px: Double, py: Double) {
        if (n == t.size) {
            System.arraycopy(t, 1, t, 0, n - 1)
            System.arraycopy(x, 1, x, 0, n - 1)
            System.arraycopy(y, 1, y, 0, n - 1)
            n -= 1
        }
        t[n] = time
        x[n] = px
        y[n] = py
        n += 1
    }

    fun velocity(): Pair<Double, Double> {
        if (n < 2) return 0.0 to 0.0
        val end = n - 1
        var first = end
        while (first > 0 && t[end] - t[first - 1] <= 100) first -= 1
        val dt = (t[end] - t[first]) / 1000.0
        if (dt < 0.008) return 0.0 to 0.0
        return (x[end] - x[first]) / dt to (y[end] - y[first]) / dt
    }
}

/**
 * Thirteen glass marbles in a walnut tray with carved cups. Dragging tilts the tray in perspective
 * and the marbles roll downhill, click off each other and fall back into cups; a fast shake
 * scatters them into the air; a tap flicks one. Each throws a shadow lit through with its own
 * colour, and the light moves across the floor as the tray tilts.
 *
 * @param autoPlay tilts the tray round in a circle, shakes it, lets the marbles settle home, on a loop.
 */
@Composable
fun GlassMarblesTray(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    label: String = "Parlour games · No. 4",
    title: String = "Glass Marbles",
    subtitle: String = "Twelve cat’s-eyes and a shooter in a walnut tray.",
) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val engine = remember(reducedMotion) { GlassMarblesTrayEngine(auto, reducedMotion) }
    val painter = remember { GlassMarblesTrayPainter() }
    val frame = remember { mutableIntStateOf(0) }
    val tracker = remember { MarblesVelocity() }
    var top by remember { mutableStateOf<Bitmap?>(null) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(MarblesBackground)
            .semantics { contentDescription = "$title. Drag to tilt the tray, shake it to scatter the marbles, tap a marble to flick it." },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        engine.setAuto(auto)
        engine.setLayout(width, height, safeTop.toDouble(), safeBottom.toDouble())
        val trayW = engine.trayW
        val trayH = engine.trayH

        // Bake the tray's top once per size, at the screen's density, off the main thread.
        LaunchedEffect(trayW, trayH, screen.density) {
            val tray = engine.tray ?: return@LaunchedEffect
            top = withContext(Dispatchers.Default) { bakeTrayTop(trayW, trayH, tray, screen.density) }
        }

        LaunchedEffect(engine) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val elapsed = if (last < 0) null else (now - last) / 1e9
                    last = now
                    if (engine.frame(elapsed)) frame.intValue += 1
                    val events = engine.events
                    engine.events = 0
                    if (events and EVENT_CLICK != 0) {
                        view.performHapticFeedback(
                            if (engine.clickHardness > 420) HapticFeedbackConstants.CONTEXT_CLICK else HapticFeedbackConstants.CLOCK_TICK,
                        )
                    }
                    if (events and EVENT_SHAKE != 0) view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                    if (events and EVENT_FLICK != 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val d = density.toDouble()
                        tracker.reset()
                        tracker.add(down.uptimeMillis, down.position.x / d, down.position.y / d)
                        engine.touchDown(down.position.x / d, down.position.y / d, down.uptimeMillis / 1000.0)
                        val id = down.id
                        var lifted = false
                        try {
                            while (true) {
                                val event = awaitPointerEvent()
                                val change = event.changes.firstOrNull { it.id == id } ?: break
                                val px = change.position.x / d
                                val py = change.position.y / d
                                tracker.add(change.uptimeMillis, px, py)
                                if (!change.pressed) {
                                    lifted = true
                                    engine.touchUp(px, py, change.uptimeMillis / 1000.0)
                                    break
                                }
                                if (change.position != change.previousPosition) {
                                    val (vx, vy) = tracker.velocity()
                                    engine.touchMove(px, py, vx, vy)
                                }
                            }
                        } finally {
                            // Cut off mid-drag (cancelled pointer input, or the finger lost): let go of the tray.
                            if (!lifted) engine.touchCancel()
                        }
                    }
                },
        ) {
            frame.intValue
            val d = density
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { painter.draw(it.nativeCanvas, engine, top, safeTop.toDouble(), d) }
            }
        }

        BasicText(
            label.uppercase(),
            modifier = Modifier.padding(start = 24.dp, top = (safeTop + 23).dp),
            style = TextStyle(color = Muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp, fontFamily = FontFamily.SansSerif),
        )
        Column(
            Modifier
                .fillMaxSize()
                .padding(start = 24.dp, end = 24.dp, bottom = (safeBottom + 52).dp),
            verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.Bottom),
        ) {
            BasicText(title, style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif))
            BasicText(subtitle, style = TextStyle(color = Muted, fontSize = 13.5.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif))
        }
        Box(
            Modifier
                .fillMaxSize()
                .padding(start = 20.dp, end = 20.dp, bottom = (safeBottom + 18).dp),
            contentAlignment = Alignment.BottomCenter,
        ) {
            BasicText(
                "Drag to tilt · Shake to scatter · Tap a marble",
                modifier = Modifier.fillMaxWidth(),
                style = TextStyle(
                    color = Muted.copy(alpha = 0.75f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    fontFamily = FontFamily.SansSerif,
                    textAlign = TextAlign.Center,
                ),
            )
        }
    }
}
