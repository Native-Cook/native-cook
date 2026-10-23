package com.ngocdevv.nativecook.blocks.playground.tangramsnap

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion

private val Ink = Color(TANGRAM_INK)
private val Muted = Color(TANGRAM_MUTED)
internal val TangramBackground = Color(TangramBoard[2])

/** Each finger's speed over its last 100 ms of samples (dp/s), measured by hand. */
private class TangramVelocity {
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
 * A tangram on a dark lacquered board: seven wooden pieces and the faint outline of a figure (a cat,
 * a boat, a runner, a swan). Drag a piece and it is drawn into its place when close; tap to turn it
 * 45°, double-tap to flip it, turn it with two fingers. When the last piece is in, colour floods the
 * figure and the pieces melt into one solid shape. The figures below morph the outline, or the
 * finished figure itself, into the next.
 *
 * @param autoPlay sets the cat piece by piece, fills it, morphs it into the swan and takes it apart, on a loop.
 */
@Composable
fun TangramSnap(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val reducedMotion = rememberReducedMotion()
    val auto = autoPlay && !reducedMotion
    val view = LocalView.current
    val screen = LocalDensity.current
    val engine = remember(reducedMotion) { TangramSnapEngine(auto, reducedMotion) }
    val painter = remember { TangramSnapPainter() }
    val frame = remember { mutableIntStateOf(0) }
    var puzzle by remember { mutableIntStateOf(0) }
    var solved by remember { mutableStateOf(false) }
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    val name = SILHOUETTES[puzzle].name

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(TangramBackground)
            .semantics { contentDescription = "Tangram, $name. Drag the pieces into the outline; tap to turn a piece, double-tap to flip it." },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        engine.setAuto(auto)
        engine.setLayout(width, height, safeTop.toDouble(), safeBottom.toDouble())

        LaunchedEffect(engine) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val elapsed = if (last < 0) null else (now - last) / 1e9
                    last = now
                    if (engine.frame(elapsed)) frame.intValue += 1
                    if (engine.puzzle != puzzle) puzzle = engine.puzzle
                    if (engine.solved != solved) solved = engine.solved
                    val events = engine.events
                    engine.events = 0
                    if (events and BUZZ_SNAP != 0) view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    if (events and BUZZ_SOLVED != 0) {
                        view.performHapticFeedback(
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
                        )
                    }
                    if (events and BUZZ_SELECT != 0) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                    if (events and BUZZ_SOFT != 0) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
            }
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(engine) {
                    // Every finger, with an id and its own speed: the engine sorts them into a drag,
                    // a two-finger turn, taps and double taps.
                    val trackers = HashMap<Long, TangramVelocity>()
                    awaitEachGesture {
                        val d = density.toDouble()
                        val first = awaitFirstDown(requireUnconsumed = false)
                        trackers.clear()
                        val down = TangramVelocity()
                        trackers[first.id.value] = down
                        down.add(first.uptimeMillis, first.position.x / d, first.position.y / d)
                        engine.touchDown(first.id.value, first.position.x / d, first.position.y / d)
                        try {
                            while (trackers.isNotEmpty()) {
                                val event = awaitPointerEvent()
                                for (change in event.changes) {
                                    val id = change.id.value
                                    val px = change.position.x / d
                                    val py = change.position.y / d
                                    val known = trackers[id]
                                    if (known == null) {
                                        if (change.pressed) {
                                            val tracker = TangramVelocity()
                                            trackers[id] = tracker
                                            tracker.add(change.uptimeMillis, px, py)
                                            engine.touchDown(id, px, py)
                                        }
                                        continue
                                    }
                                    known.add(change.uptimeMillis, px, py)
                                    val (vx, vy) = known.velocity()
                                    if (!change.pressed) {
                                        trackers.remove(id)
                                        engine.touchUp(id, px, py, vx, vy)
                                    } else if (change.position != change.previousPosition) {
                                        engine.touchMove(id, px, py, vx, vy)
                                    }
                                }
                            }
                        } finally {
                            // Cut off mid-gesture (cancelled pointer input): lift every finger.
                            if (trackers.isNotEmpty()) {
                                trackers.clear()
                                engine.touchCancel()
                            }
                        }
                    }
                },
        ) {
            frame.intValue
            val d = density
            scale(d, d, pivot = Offset.Zero) {
                drawIntoCanvas { painter.draw(it.nativeCanvas, engine, safeTop.toDouble(), d) }
            }
        }

        Column(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 120.dp, top = (safeTop + 18).dp),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            BasicText(
                "Tangram · No. ${puzzle + 1} of ${SILHOUETTES.size}".uppercase(),
                style = TextStyle(color = Muted, fontSize = 10.5.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 2.4.sp, fontFamily = FontFamily.SansSerif),
            )
            BasicText(name, style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.SemiBold, fontFamily = FontFamily.Serif))
            BasicText(
                if (solved) "Seven pieces, one shape." else "Drag · Tap to turn · Double-tap to flip",
                style = TextStyle(color = Muted, fontSize = 12.5.sp, fontWeight = FontWeight.Medium, fontFamily = FontFamily.SansSerif),
            )
        }
    }
}
