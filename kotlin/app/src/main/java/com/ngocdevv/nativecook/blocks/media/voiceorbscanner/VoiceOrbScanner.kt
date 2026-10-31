package com.ngocdevv.nativecook.blocks.media.voiceorbscanner

import android.app.Activity
import android.content.ContextWrapper
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

@Composable
fun VoiceOrbScanner(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val top = WindowInsets.statusBars.getTop(density) / density.density.toDouble()
    val bottom = WindowInsets.navigationBars.getBottom(density) / density.density.toDouble()
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    val reduced = Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    val art = remember(context) { VoiceOrbScannerArt(context) }
    val audio = remember(context, reduced) { if (reduced) null else VoiceOrbScannerAudio(context) }
    val vibrator = remember(context) { context.getSystemService(Vibrator::class.java) }
    val frame = remember(autoPlay, reduced) { mutableStateOf(VoiceOrbScannerMotion.Frame()) }
    var held by remember { mutableStateOf(false) }
    var direction by remember { mutableDoubleStateOf(0.0) }
    var last by remember { mutableDoubleStateOf(-10.0) }
    var pressed by remember { mutableStateOf(false) }
    fun buzz(kind: Int) {
        if (Build.VERSION.SDK_INT >= 29) vibrator?.vibrate(VibrationEffect.createPredefined(if (kind == 1) VibrationEffect.EFFECT_HEAVY_CLICK else if (kind == 2) VibrationEffect.EFFECT_DOUBLE_CLICK else VibrationEffect.EFFECT_CLICK))
        else vibrator?.vibrate(VibrationEffect.createOneShot(if (kind == 1) 18 else 12, 60))
    }
    fun rescan() {
        last = frame.value.now
        if (frame.value.s > 0.5) { direction = -1.0; audio?.play("merge"); buzz(0) }
    }
    fun begin() {
        last = frame.value.now
        if (frame.value.s > 0.99 && direction == 0.0) { direction = -1.0; audio?.play("merge"); buzz(0); return }
        if (frame.value.s > 0.01) return
        held = true; audio?.play("listen"); buzz(1)
    }
    DisposableEffect(audio) { onDispose { audio?.close() } }
    val view = LocalView.current
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller = activity?.let { WindowCompat.getInsetsController(it.window, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }
    LaunchedEffect(autoPlay, reduced, lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            var previous = 0L
            while (true) {
                withFrameNanos { nanos ->
                    val dt = min(if (previous == 0L) 0.016 else (nanos-previous)/1e9, 0.05)
                    previous = nanos
                    val f = frame.value
                    val now = f.now+dt
                    if (autoPlay && !reduced && !held && now-last >= 2) {
                        direction = 0.0
                        frame.value = VoiceOrbScannerMotion.at((f.t+dt)%9, now)
                    } else {
                        var l = f.l; var s = f.s; var listening = f.listening
                        if (held && s < 0.01) {
                            listening = true; l = min(1.0, l+dt/2.4)
                            if (l >= 1) { listening = false; direction = 1.0; audio?.play("split"); buzz(2) }
                        } else if (direction == 0.0 && s < 0.01) { listening = false; l = max(0.0, l-dt*2.5) }
                        if (direction != 0.0) {
                            s = VoiceOrbScannerMotion.clamp(s+direction*dt/(if (direction > 0) 1.1 else 1.0))
                            if (direction < 0 && s <= 0) { direction = 0.0; l = 0.0 }
                            if (direction > 0 && s >= 1) direction = 0.0
                        }
                        frame.value = f.copy(now = now, l = l, s = s, listening = listening, useLoopPhase = autoPlay && !reduced && now-last >= 2)
                    }
                }
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
        Canvas(Modifier.fillMaxSize().pointerInput(reduced) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val x = down.position.x / density.density
                val y = down.position.y / density.density
                val w = size.width / density.density
                val h = size.height / density.density
                val r = min(118.0, w*0.29); val oy = top+270
                val onOrb = abs(x-w/2.0) <= r+20 && abs(y-oy) <= r+20
                val onPlaylist = x >= 20 && x <= w-20 && y >= h-bottom-94 && y <= h-bottom-18
                if (onOrb) begin()
                if (onPlaylist) pressed = true
                if (onOrb || onPlaylist) down.consume()
                var end = down.position
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id } ?: break
                    end = change.position
                    if (onOrb || onPlaylist) change.consume()
                } while (change.pressed)
                if (onOrb) { last = frame.value.now; held = false }
                if (onPlaylist) { pressed = false; if ((end-down.position).getDistance() < 12*density.density) rescan() }
            }
        }) {
            drawIntoCanvas { art.draw(it.nativeCanvas, size.width / density.density.toDouble(), size.height / density.density.toDouble(), top, bottom, density.density, frame.value, reduced, autoPlay, pressed) }
        }
        val oy = top+270
        BoxWithConstraints(Modifier.fillMaxSize()) {
            val r = min(118.0,maxWidth.value*0.29)
            val cardWidth = (maxWidth.value-64)/3
            if (frame.value.phase == 2) {
                val labels = listOf("Voice tier: Soprano", "Vocal range: 2 octaves", "Breath: 2.2 sec")
                for (i in 0..2) Box(Modifier.offset(x = (24+i*(cardWidth+8)).dp,y = (oy+r+100).dp).width(cardWidth.dp).height(128.dp).semantics { contentDescription = labels[i] })
            }
        }
        Box(Modifier.offset(x = 20.dp, y = (oy-138).dp).fillMaxWidth().height(276.dp).semantics {
            role = Role.Button
            contentDescription = if (frame.value.phase == 2) "Scan again" else "Hold to sing and scan your voice"
            onClick { if (frame.value.phase == 2) rescan() else begin(); true }
        })
        Box(Modifier.align(androidx.compose.ui.Alignment.BottomCenter).padding(bottom = (bottom+18).dp).fillMaxWidth().height(76.dp).semantics {
            role = Role.Button; contentDescription = "Your voice, your playlist. Scan again"; onClick { rescan(); true }
        })
        Box(Modifier.offset(y = (top+8).dp).align(androidx.compose.ui.Alignment.TopEnd).size(38.dp).semantics { role = Role.Button; contentDescription = "Close" })
    }
}
