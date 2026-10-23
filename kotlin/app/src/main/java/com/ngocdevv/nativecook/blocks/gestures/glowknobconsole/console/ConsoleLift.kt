// The block is built for Android 12 (API 31) and newer; the demo checks before showing it.
@file:androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.S)

package com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.console

import android.graphics.Picture
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Perf
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.remember
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.AnimationEngine
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.Shared
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SharedValue
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.SpringConfig
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.cancelAnimation
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withDelay
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withSpring
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.anim.withTiming
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.GestureDetectorState
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.PanGesture
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.TapGesture
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.gesture.gestureDetector
import com.ngocdevv.nativecook.blocks.gestures.glowknobconsole.platform.Haptics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sign

// Hold this long on the housing to pick the console up.
private const val HOLD = 320L
// A half turn per 260dp of horizontal drag; pitch follows vertical drag.
private const val YAW_PER_DP = PI / 260
private const val PITCH_PER_DP = 0.006
private const val PITCH_MAX = 0.75
private val RAISE = SpringConfig(stiffness = 210.0, damping = 17.0, mass = 1.0)
private val SETTLE = SpringConfig(stiffness = 150.0, damping = 19.0, mass = 1.0)
// Frames (ms) both drawings are up at a handoff: the 3D frame showing the
// new state reaches the screen a frame or two after the shared value flips.
private const val OVERLAP = 70.0
private val LOWER = SpringConfig(stiffness = 240.0, damping = 26.0, mass = 1.0, overshootClamping = true)

// The light as it is when picked up.
private fun glowOf(control: TactileControl): Glow {
    val fill = control.fill()
    val c = tipColor(control.lightMix())
    // sRGB → linear, the scene's working space.
    fun lin(v: Float): Double = if (v <= 0.04045) v / 12.92 else ((v + 0.055) / 1.055).pow(2.4)
    return Glow(
        tip = G.Track.x + G.Track.width * fill,
        color = doubleArrayOf(lin(c.r), lin(c.g), lin(c.b)),
        power = control.lightAlpha() * min(1.0, 0.25 + fill),
    )
}

// Stages.
private const val REST = 0
private const val PICKING = 1
private const val HELD = 2
private const val LOWERING = 3

/**
 * Pick-up choreography of the console (console-lift.tsx useConsoleLift).
 * Holding the housing records the live artwork (DeviceArt, baked) into a
 * Picture the scene paints into the face texture, and swaps the 2D drawing
 * for the 3D object at the same pixels; the object then rises toward the
 * viewer and follows the finger. A flick turns it over to its back; a tap
 * sets it down, face up.
 */
class ConsoleLift(
    private val control: TactileControl,
    private val fx: Effects,
    private val tilt: Shared<Tilt>,
    private val haptics: Haptics,
) {
    val lift = SharedValue(0.0)
    private val yaw = SharedValue(0.0)
    private val pitch = SharedValue(0.0)
    private val stage = Shared(REST)
    // 1 while the 3D object is drawn (it sits above the 2D drawing)...
    val shown = SharedValue(0.0)
    // ...and 1 while the 2D drawing under it is hidden. The two overlap for a
    // few frames at each handoff, so neither direction shows a gap.
    val covered = SharedValue(0.0)
    private val grab = Shared(0.0 to 0.0)
    var lifted by mutableStateOf(false)
        private set
    var used by mutableStateOf(false)
        private set
    val link = LiftLink()
    private val painter = DeviceArtPainter()

    // Pushes the pose to the scene each frame it changes while the object is
    // up (and once more as it comes to rest).
    private val poseReaction = AnimationEngine.reaction({
        doubleArrayOf(lift.get(), yaw.get(), pitch.get(), tilt.get().x, tilt.get().y) to stage.get()
    }) { now, before ->
        if (now.second != REST || (before != null && before.second != REST)) link.setPose(now.first)
    }

    init {
        link.on(
            face = {
                shown.set(1.0)
                covered.set(withDelay(OVERLAP, withTiming(1.0, 0.0)))
                stage.set(HELD)
                lift.set(withSpring(1.0, RAISE))
            },
            render = {},
        )
    }

    private fun buzz(style: Haptics.Impact) = haptics.impact(style)
    private fun tickFace() = haptics.selection()

    private fun beginLift() {
        if (!link.ready) {
            stage.set(REST)
            return
        }
        buzz(Haptics.Impact.Medium)
        lifted = true
        used = true
        // drawAsPicture(<DeviceArt baked tilt={still} />, 0,0,1000,284)
        val picture = Perf.section("Lift:recordFace") {
            Picture().also { picture ->
                val c = picture.beginRecording(1000, 284)
                painter.draw(c, ArtState.of(control, Tilt(0.0, 0.0)), fx, baked = true, pxPerUnit = DeviceArtPainter.BAKED_SCALE, blockOnGrain = true)
                picture.endRecording()
            }
        }
        link.start(picture, if (fx.liftLight) glowOf(control) else null)
    }

    private fun landed() {
        link.stop()
        lifted = false
        buzz(Haptics.Impact.Soft)
    }

    private fun follow(x: Double, y: Double) {
        val (startYaw, startPitch) = grab.get()
        yaw.set(startYaw + x * YAW_PER_DP)
        val p = startPitch + y * PITCH_PER_DP
        val over = abs(p) - PITCH_MAX
        pitch.set(if (over > 0) sign(p) * (PITCH_MAX + rubberBand(over, 0.25)) else p)
    }

    // Release: rest on whichever face the flick carries it to.
    private fun release(vx: Double, vy: Double) {
        val v = vx * YAW_PER_DP
        val from = restingHalfTurn(yaw.get(), 0.0)
        val to = restingHalfTurn(yaw.get(), v)
        yaw.set(withSpring(to * PI, SETTLE.copy(velocity = v)) { done -> if (done && to != from) tickFace() })
        pitch.set(withSpring(0.0, SETTLE.copy(velocity = vy * PITCH_PER_DP)))
    }

    private fun hold() {
        cancelAnimation(yaw)
        cancelAnimation(pitch)
        grab.set(yaw.get() to pitch.get())
    }

    private fun moving() = stage.get() == PICKING || stage.get() == HELD

    val pickUp = PanGesture().apply {
        activateAfterLongPress = HOLD
        onStart = {
            if (stage.get() == REST) {
                stage.set(PICKING)
                hold()
                beginLift()
            }
        }
        onUpdate = { e -> if (moving()) follow(e.translationX.toDouble(), e.translationY.toDouble()) }
        onEnd = { e, _ -> if (moving()) release(e.velocityX.toDouble(), e.velocityY.toDouble()) }
    }

    val turn = PanGesture().apply {
        onStart = { if (stage.get() == HELD) hold() }
        onUpdate = { e -> if (stage.get() == HELD) follow(e.translationX.toDouble(), e.translationY.toDouble()) }
        onEnd = { e, _ -> if (stage.get() == HELD) release(e.velocityX.toDouble(), e.velocityY.toDouble()) }
    }

    // Set down: face up whichever way it was turned, then lowered onto the
    // stage; the 2D drawing takes over on contact.
    val putDown = TapGesture().apply {
        onEnd = { _, _ ->
            if (stage.get() == HELD) {
                stage.set(LOWERING)
                val target = faceUpYaw(yaw.get())
                val away = abs(target - yaw.get())
                yaw.set(withSpring(target, SpringConfig(stiffness = 200.0, damping = 24.0, mass = 1.0)))
                pitch.set(withSpring(0.0, SpringConfig(stiffness = 200.0, damping = 24.0, mass = 1.0)))
                lift.set(
                    withDelay(
                        min(260.0, away * 90),
                        withSpring(0.0, LOWER) { done ->
                            if (done) {
                                yaw.set(0.0)
                                pitch.set(0.0)
                                covered.set(0.0)
                                shown.set(withDelay(OVERLAP, withTiming(0.0, 0.0)))
                                stage.set(REST)
                                landed()
                            }
                        },
                    ),
                )
            }
        }
    }
    val held = GestureDetectorState(listOf(turn, putDown))

    fun setEnabled(enabled: Boolean) {
        pickUp.enabled = enabled
    }

    // Leaving the screen mid-lift: drop straight back to the 2D drawing.
    fun dispose() {
        link.stop()
        shown.set(0.0)
        covered.set(0.0)
        stage.set(REST)
        lift.set(0.0)
        yaw.set(0.0)
        pitch.set(0.0)
        AnimationEngine.removeReaction(poseReaction)
    }
}

/**
 * The lifted console (console-lift.tsx LiftLayer): Filament renders the
 * object offscreen and Compose composites it, with a contact shadow cast from
 * the rendered silhouette that grows and softens as the object rises, and the
 * object mirrored in the glass floor.
 */
@Composable
fun LiftLayer(
    lift: ConsoleLift,
    /** The layer's box in its parent, dp. */
    frame: Box,
    /** The housing's box inside the layer, dp. */
    housing: Box,
    /** Mount the renderer (resumed screen, effect on). */
    active: Boolean,
    /** fx.liftLight: mirror the lifted object in the glass floor. */
    glow: Boolean,
    /** Page tone as seen (ambient dim included), for the floor's fog. */
    fog: () -> Double,
) {
    Perf.recomposed("LiftLayer")
    val density = LocalDensity.current.density
    val scene by produceState<LiftScene?>(null, active, frame, housing) {
        if (!active) return@produceState
        val (materials, studio) = withContext(Dispatchers.Default) {
            DeviceArtPainter.noiseFor(DeviceArtPainter.BAKED_SCALE) // off the pick-up's critical path
            LiftMaterials.compile() to LiftStudio.get()
        }
        value = LiftScene(frame, housing, density, lift.link, materials, studio)
    }
    DisposableEffect(scene) {
        val s = scene
        onDispose { s?.destroy() }
    }
    val s = housing.width / 1000
    val mirror = housing.y + housing.height + (GAP / 2) * s
    val depth = housing.height * SQUASH * DEPTH
    Box(
        Modifier
            .fillMaxSize()
            .clearAndSetSemantics {}
            .then(if (lift.lifted) Modifier.gestureDetector(lift.turn, lift.putDown) else Modifier),
    ) {
        Box(
            Modifier
                .offset(frame.x.dp, frame.y.dp)
                .size(frame.width.dp, frame.height.dp)
                .graphicsLayer { alpha = lift.shown.get().toFloat() },
        ) {
            // Only whether a frame exists is composition state; each new frame
            // is read in the draw lambdas, so it redraws without recomposing.
            val current = scene
            val hasImage by remember(current) { derivedStateOf { current?.image != null } }
            Perf.recomposed("LiftLayer.image")
            if (current != null && hasImage) {
                if (glow) {
                    Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = min(1.0, lift.lift.get() * 1.6).toFloat() }) {
                        val m = (mirror * density).toFloat()
                        withTransform({
                            translate(0f, m)
                            scale(1f, -SQUASH.toFloat(), pivot = Offset.Zero)
                            translate(0f, -m)
                        }) {
                            current.image?.let { drawImage(it, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())) }
                        }
                        drawIntoCanvas { canvas ->
                            val p = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG)
                            p.shader = SkiaCompat.linear(0f, m, 0f, ((mirror + depth) * density).toFloat(), fogColors(fog()), VISIBLE_AT)
                            canvas.nativeCanvas.drawRect(0f, m, this.size.width, this.size.height, p)
                        }
                    }
                }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val h = lift.lift.get()
                            translationY = ((4 + 34 * h) * density).toFloat()
                            val r = SkiaCompat.renderEffectRadius(((5 + 17 * h) * density).toFloat())
                            renderEffect = BlurEffect(r, r, TileMode.Decal)
                        },
                ) {
                    val h = lift.lift.get()
                    val image = current.image ?: return@Canvas
                    drawImage(
                        image,
                        dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt()),
                        colorFilter = ColorFilter.tint(Color(0f, 0f, 0f, (0.34 - 0.14 * h).toFloat()), BlendMode.SrcIn),
                    )
                }
                Canvas(Modifier.fillMaxSize()) {
                    current.image?.let { drawImage(it, dstSize = IntSize(this.size.width.toInt(), this.size.height.toInt())) }
                }
            }
        }
    }
}
