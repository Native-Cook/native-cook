package com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.blocks.inputs.particleribbonvoice.ParticleRibbonVoiceTheme as Theme
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.min

/**
 * Particle Ribbon Voice: a voice assistant's resting screen: two thin lines of type and one ribbon of light, a sheet of particles that twists along its
 * length. Edge-on stretches are knife-thin white lines, face-on stretches open into bands of sparks. The user's voice sends twists from the left, the
 * assistant's answer sends them back from the right, thinking winds the sheet into a thread, and a finger can part the ribbon where it touches it. Hold
 * the screen to talk (the request's words appear as it is heard and the talk control fills), let go and the ribbon thinks, then answers "Booked for 8 pm."
 * word by word.
 *
 * LOOP = 9 s ([ParticleRibbonVoiceMotion.LOOP]): rest, the press at 0.55, the voice 0.78-3.0 (peak 2.2), release at 3.0 and the thread until 4.4, the reply
 * from the right edge 4.4-6.55, a finger parts the ribbon 6.6-7.4, the question returns at 7.1, rest from 8.2. A touch takes over; two seconds after the
 * last one the live frame blends back to the loop. Reduce Motion: no loop, no twists; a still band whose width follows the level, thinking is a slow
 * pulse, which end is brighter says who is speaking. The ribbon is an AGSL shader on API 33+, a line drawing below.
 * Not ported from the RN props: controlled `state`, `level`, `mode`, `touchable`, `colors`, `width`, `centerY`, `twistSpeed`, `quality`, `bloom`,
 * `background`, `font`, `paused`, `renderCaption`.
 */
@Composable
fun ParticleRibbonVoice(
    autoPlay: Boolean = false,
    sound: Boolean = true,
    onStateChange: ((Int) -> Unit)? = null,
    onPressIn: (() -> Unit)? = null,
    onPressOut: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val densityOwner = LocalDensity.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = rememberLiveReducedMotion()
    val vibe = remember(context, sound) { ParticleRibbonVoiceHaptics.create(context, sound) }
    DisposableEffect(vibe) { onDispose { vibe.release() } }
    val tick = remember { mutableIntStateOf(0) }
    var active by remember(lifecycle) { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var a11yState by remember { mutableIntStateOf(0) }
    BlockSystemBars(darkIcons = false)

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    val show = remember { Animatable(0f) }
    LaunchedEffect(reduced) { show.animateTo(1f, tween(if (reduced) 0 else 320)) }

    BoxWithConstraints(Modifier.fillMaxSize().background(Color(Theme.page))) {
        val w = maxWidth.value.toDouble()
        val h = maxHeight.value.toDouble()
        val insetTop = with(densityOwner) { WindowInsets.statusBars.getTop(densityOwner).toDp().value }.toDouble()
        val insetBottom = with(densityOwner) { WindowInsets.navigationBars.getBottom(densityOwner).toDp().value }.toDouble()
        val layout = remember(w, h, insetTop) { Theme.Layout(w, h, insetTop) }
        val dens = densityOwner.density.toDouble()
        val renderer = remember { ParticleRibbonVoiceRenderer(context.assets) }
        val engine = remember(autoPlay, reduced, layout) { ParticleRibbonVoiceEngine(autoPlay, reduced, layout.s, layout.yc(0.0)) }
        val cbState by rememberUpdatedState(onStateChange)
        val cbIn by rememberUpdatedState(onPressIn)
        val cbOut by rememberUpdatedState(onPressOut)
        SideEffect {
            engine.cue = { beat -> vibe.emit(beat) }
            engine.onPress = { down -> if (down) cbIn?.invoke() else cbOut?.invoke() }
            engine.onMode = { m ->
                a11yState = m
                cbState?.invoke(m)
            }
        }
        LaunchedEffect(engine, active) {
            if (!active) return@LaunchedEffect
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = if (previous == 0L) 0.016 else min((nanos - previous) / 1e9, 0.05)
                    previous = nanos
                    engine.frame(dt)
                    tick.intValue++
                }
            }
        }

        Canvas(
            Modifier.fillMaxSize().pointerInput(engine, dens, layout) {
                val ids = HashMap<Long, Int>()
                var next = 0
                while (true) {
                    val event = awaitPointerEventScope { awaitPointerEvent() }
                    for (change in event.changes) {
                        val key = change.id.value
                        val x = change.position.x / dens
                        val y = change.position.y / dens
                        if (change.changedToDown()) {
                            val id = next++ % 8
                            ids[key] = id
                            engine.down(id, x, y)
                            change.consume()
                        } else if (change.changedToUp()) {
                            val id = ids.remove(key)
                            if (id != null) engine.up(id)
                            change.consume()
                        } else if (change.pressed) {
                            val id = ids[key]
                            if (id != null) engine.move(id, x, y)
                            change.consume()
                        }
                    }
                    tick.intValue++
                }
            },
        ) {
            tick.intValue
            val c = drawContext.canvas.nativeCanvas
            val scale = drawContext.density.density
            c.save()
            c.scale(scale, scale)
            renderer.draw(c, size.width / scale, size.height / scale, layout, engine, show.value.toDouble(), insetBottom)
            c.restore()
        }

        // one control for the screen reader: the action toggles listening
        Box(
            Modifier.fillMaxSize().semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = "${Theme.LABEL}. ${if (a11yState == 0) "${Theme.GREETING} ${Theme.IDLE}" else Theme.STATES[a11yState]}"
                stateDescription = Theme.STATES[a11yState]
                onClick(label = if (a11yState == 1) Theme.STOP else Theme.START) {
                    engine.toggleListening()
                    tick.intValue++
                    true
                }
            },
        )
    }
}
