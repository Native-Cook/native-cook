package com.ngocdevv.nativecook.blocks.navigation.elevatorfloornav

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/*
 Elevator Floor Nav — "Merrow & Hale", a department store whose section navigation is its lift.
 A port of ElevatorFloorNav.tsx (the reference) and the SwiftUI block; the numbers are the same.

 Look: a green marble lobby; above the doorway an enamel floor dial (1 – 5) on a brass half-moon
 with a sunburst and two hall lanterns; the doorway itself in a brass surround, showing the floor
 the car is on (each a poster drawn in code: "Shoes", "Fashion", "Beauty", "Home", "Café", with
 three arched display windows and prices); two brushed-steel doors with vision panels; below, a
 brass panel of five glass buttons with the sections' names.

 Tap a floor: its button dips and lights amber, the doors close (0.6 s, a small bump as they
 meet), the car rides 0.4 s a floor (smootherstep) while the store slides by behind the vision
 panels, blurred with speed, and the needle follows; on arrival the lantern of the direction
 glows, a ring of light leaves the dial, the button goes out, the doors open on the new section
 and the header rolls to its name. A floor called during a trip is queued. 2 s after the last
 touch the car goes home to floor 1 at a quick pace and the loop starts again from t = 0
 (ElevatorFloorNavMotion.kt, LOOP = 9 s). Reduced motion: no loop; a floor opens at once.
*/

private val INK = Color(0xFFF3EAD7)
private val BRASS = Color(0xFFD9B66E)
private const val LABEL_H = 18f

private fun nowSeconds() = System.nanoTime() / 1e9

private class ElevatorArtSet(val scene: ElevatorScene, val layers: ElevatorLayers, val painter: ElevatorPainter)

private class ElevatorController(val auto: Boolean, val reduced: Boolean) {
    enum class Mode { Auto, Touch, Back }

    var now by mutableDoubleStateOf(0.0)
    var awake by mutableStateOf(true)
    var mode = Mode.Auto
    var loopStart = -1.0
    var lastTouch = 0.0
    var trip = ElevatorTrip(1, 1, -99.0, 1.0)
    var prev = ElevatorTrip(1, 1, -99.0, 1.0)
    var pending = 0
    /** Under reduced motion the floor simply changes. */
    var still = 1
    private var rang = false
    private var poseAt = Double.NaN
    private var cached: ElevatorPose? = null

    fun pose(at: Double = now): ElevatorPose {
        val c = cached
        if (c != null && at == poseAt) return c
        val p = when {
            reduced && mode == Mode.Auto -> ElevatorMotion.restAt(still)
            mode != Mode.Auto -> ElevatorMotion.mergeBell(ElevatorMotion.tripPose(trip, at), ElevatorMotion.tripPose(prev, at))
            !auto || loopStart < 0 -> ElevatorMotion.restAt(1)
            else -> ElevatorMotion.loopPose((((at - loopStart) % ElevatorMotion.LOOP) + ElevatorMotion.LOOP) % ElevatorMotion.LOOP)
        }
        cached = p
        poseAt = at
        return p
    }

    fun startTrip(to: Int, at: Double, pace: Double) {
        prev = trip
        trip = ElevatorTrip(trip.to, to, at, pace)
        cached = null
    }

    /** One frame; returns true when a trip the finger called has just arrived (for a tick). */
    fun frame(at: Double): Boolean {
        if (loopStart < 0) loopStart = at
        now = at
        if (mode == Mode.Auto) return false
        val bellNow = pose(at).ding.let { max(it[0], it[1]) }
        val arrived = mode == Mode.Touch && bellNow > 0.8 && !rang
        rang = bellNow > 0.8
        val tr = trip
        if (!ElevatorMotion.tripDone(tr, at)) return arrived
        if (pending > 0) {
            val to = pending
            pending = 0
            if (to != tr.to) startTrip(to, at, 1.0)
            return arrived
        }
        if (mode == Mode.Touch && at - lastTouch > ElevatorMotion.IDLE_BEFORE_BACK) {
            mode = Mode.Back
            if (tr.to != 1) startTrip(1, at, ElevatorMotion.BACK_PACE)
        } else if (mode == Mode.Back && bellNow < 0.04) {
            mode = Mode.Auto
            loopStart = at
        }
        cached = null
        return arrived
    }

    /** Hands the loop's trip to the finger's clock, so nothing jumps. */
    fun takeOver(at: Double) {
        if (mode == Mode.Touch) return
        if (mode == Mode.Auto) {
            val l = ElevatorMotion.LOOP
            val t = if (auto && loopStart >= 0) (((at - loopStart) % l) + l) % l else 0.0
            if (auto && t >= 1.3 && t < 5.4) {
                trip = ElevatorTrip(1, 4, at - (t - 1.3), 1.0)
                prev = ElevatorTrip(4, 1, at - (t + l - 5.4), 1.0)
            } else {
                val back = if (t >= 5.4) t - 5.4 else t + l - 5.4
                trip = ElevatorTrip(4, 1, at - back, 1.0)
                prev = ElevatorTrip(1, 4, at - back - 4.1, 1.0)
            }
        }
        mode = Mode.Touch
        cached = null
    }

    fun call(floor: Int, at: Double) {
        lastTouch = at
        if (reduced) {
            still = floor
            cached = null
            now = at
            return
        }
        takeOver(at)
        // A floor called while the car is busy waits for this trip to finish.
        if (ElevatorMotion.tripDone(trip, at)) startTrip(floor, at, 1.0) else pending = floor
        awake = true
    }
}

@Composable
fun ElevatorFloorNav(modifier: Modifier = Modifier, autoPlay: Boolean = false) {
    val view = LocalView.current
    val reduced = rememberReducedMotion()
    val auto = autoPlay && !reduced
    val c = remember(auto, reduced) { ElevatorController(auto, reduced) }

    BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFF10221C)).clipToBounds()) {
        val density = LocalDensity.current.density
        val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
        val bottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        var art by remember { mutableStateOf<ElevatorArtSet?>(null) }

        // The resting frame's art is baked off the main thread first; the other floors (and their
        // blurred copies) follow on the same thread while the loop already plays.
        LaunchedEffect(w, h, top, bottom, density) {
            // The demo hides the status bar and the insets animate for a moment: wait for them to
            // settle (cancellable) so a bake does not restart on every inset frame.
            delay(120)
            val fonts = ElevatorFonts()
            val scene = ElevatorScene(w, h, top, bottom)
            val scale = min(2f, density)
            val layers = withContext(Dispatchers.Default) { ElevatorFloorNavArt.bakeBase(scene, fonts, scale) }
            art = ElevatorArtSet(scene, layers, ElevatorPainter(ElevatorFonts()))
            withContext(Dispatchers.Default) { ElevatorFloorNavArt.bakeRest(scene, fonts, scale, layers, coroutineContext[Job]) }
        }

        val current = art
        LaunchedEffect(current, c, c.awake) {
            if (current == null || !c.awake) return@LaunchedEffect
            while (true) {
                var arrived = false
                withFrameNanos { n -> arrived = c.frame(n / 1e9) }
                if (arrived) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
                // With nothing playing and no finger, the frame stays as it is.
                if (!auto && c.mode == ElevatorController.Mode.Auto) {
                    c.awake = false
                    break
                }
            }
        }

        Canvas(Modifier.fillMaxSize()) {
            val a = current ?: return@Canvas
            val pose = c.pose()
            scale(density, density, Offset.Zero) {
                a.painter.frame(drawContext.canvas.nativeCanvas, a.scene, a.layers, pose)
            }
        }

        Column(
            Modifier.offset(x = 20.dp, y = (top + 8).dp).width((w - 40).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            BasicText("MERROW & HALE", style = TextStyle(color = INK, fontFamily = FontFamily.Serif, fontSize = 17.sp, letterSpacing = 4.5.sp))
            Box(Modifier.fillMaxWidth().height((LABEL_H + 2).dp).clipToBounds()) {
                for (f in 1..ElevatorMotion.FLOORS) {
                    BasicText(
                        "Floor $f · ${ElevatorStore[f - 1].name}",
                        Modifier
                            .fillMaxWidth()
                            .graphicsLayer {
                                val p = c.pose()
                                var y = LABEL_H
                                var a = 0f
                                if (p.labelTo == f) {
                                    y = ((1 - p.labelK) * LABEL_H).toFloat()
                                    a = p.labelK.toFloat()
                                } else if (p.labelFrom == f && p.labelFrom != p.labelTo) {
                                    y = (-p.labelK * LABEL_H).toFloat()
                                    a = (1 - p.labelK).toFloat()
                                }
                                alpha = a
                                translationY = y * density
                            },
                        style = TextStyle(
                            color = BRASS, fontSize = 13.sp, lineHeight = LABEL_H.sp, fontWeight = FontWeight.SemiBold,
                            letterSpacing = 0.4.sp, textAlign = TextAlign.Center,
                        ),
                        maxLines = 1,
                    )
                }
            }
        }

        val s = current?.scene
        if (s != null) {
            for (i in 0 until ElevatorMotion.FLOORS) {
                val side = s.buttonR * 2 + 8 + 12
                ElevatorButton(
                    "Floor ${i + 1}, ${ElevatorStore[i].name}",
                    Modifier
                        .offset(x = (s.buttonX[i] - s.buttonR - 4 - 6).dp, y = (s.buttonY - s.buttonR - 4 - 6).dp)
                        .size(side.dp),
                ) {
                    view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                    c.call(i + 1, nowSeconds())
                }
            }
        }
    }
}

@Composable
private fun ElevatorButton(label: String, modifier: Modifier, action: () -> Unit) {
    val currentAction by rememberUpdatedState(action)
    Box(
        modifier
            .semantics {
                role = Role.Button
                contentDescription = label
                onClick { currentAction(); true }
            }
            .pointerInput(Unit) { detectTapGestures(onTap = { currentAction() }) },
    )
}
