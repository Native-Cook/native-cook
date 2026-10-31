package com.ngocdevv.nativecook.blocks.lists.clayrelightsettings

import android.app.Activity
import android.content.ContextWrapper
import androidx.core.view.WindowCompat
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
fun ClayRelightSettings(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val fontScale = LocalDensity.current.fontScale
    val haptics = LocalHapticFeedback.current
    val owner = LocalLifecycleOwner.current
    val state = remember(autoPlay) { ClayRelightSettingsState(autoPlay) }
    val fonts = remember { ClayRelightSettingsTheme.fonts(context) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f) }
    var active by remember { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = owner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            state.setActive(active)
            reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    val clock = remember { mutableDoubleStateOf(0.0) }
    val revision = state.revision
    val dark by remember(reduced) { derivedStateOf { state.snapshot(clock.doubleValue,reduced).theme > 0.5 } }
    DisposableEffect(dark) {
        var host = context
        while (host is ContextWrapper && host !is Activity) host = host.baseContext
        val window = (host as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it,it.decorView) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !dark
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    val shaders = remember {
        if (Build.VERSION.SDK_INT >= 33) ClayRelightSettingsRenderer() else null
    }
    LaunchedEffect(active,reduced,revision) {
        if (!active) return@LaunchedEffect
        var horizon = state.snapshot(state.time(),reduced).m > 0.47
        do {
            withFrameNanos {
                val now = state.time()
                clock.doubleValue = now
                val next = state.snapshot(now,reduced).m > 0.47
                if (next != horizon && state.isUserDriven(now,reduced) && !reduced) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                horizon = next
            }
        } while (isActive && (!reduced || state.time() < max(state.light.end,state.chrome.end)+0.3))
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val width = maxWidth.value
        val cardW = width-32
        val top = WindowInsets.statusBars.getTop(LocalDensity.current)/density
        val heroTop = top+68
        val listTop = heroTop+358
        Canvas(Modifier.fillMaxSize().pointerInput(reduced,width,top) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val p = down.position/density
                val sky = p.x >= 16 && p.x <= width-16 && p.y >= heroTop && p.y < heroTop+232
                val switchX = width-16-20-51
                val toggle = p.x >= switchX-12 && p.x <= switchX+63 && p.y >= heroTop+232+22.5-12 && p.y <= heroTop+232+53.5+12
                if (!sky && !toggle) return@awaitEachGesture
                down.consume()
                val tracker = VelocityTracker()
                tracker.addPosition(down.uptimeMillis,down.position)
                if (sky) state.beginDrag(reduced) else state.switchPress(true,reduced)
                var finish = down
                var cancelled = false
                do {
                    val event = awaitPointerEvent()
                    val change = event.changes.firstOrNull { it.id == down.id }
                    if (change == null) { cancelled = true; break }
                    finish = change
                    tracker.addPosition(change.uptimeMillis,change.position)
                    if (sky && change.pressed) state.drag(((change.position.x-down.position.x)/density).toDouble(),cardW*0.85)
                    change.consume()
                } while (finish.pressed)
                if (sky) state.endDrag((tracker.calculateVelocity().x/density).toDouble(),cardW*0.85,reduced)
                else {
                    if (!cancelled && finish.uptimeMillis-down.uptimeMillis <= 800 &&
                        (finish.position-down.position).getDistance()/density < 10) {
                        val f = state.snapshot(state.time(),reduced)
                        state.setTarget(if (f.on == 1.0) 0.0 else 1.0,reduced = reduced)
                        haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
                    }
                    state.switchPress(false,reduced)
                }
            }
        }) {
            val f = state.snapshot(clock.doubleValue,reduced)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            canvas.scale(density,density)
            ClayRelightSettingsDrawing(canvas,fonts,density,shaders,fontScale).draw(width,size.height/density,top,f,if (reduced) 0.0 else clock.doubleValue,reduced)
            canvas.restore()
        }
        // Semantics use the same fixed layout; the animated content remains draw-only.
        Box(Modifier.offset(16.dp,heroTop.dp).size(cardW.dp,232.dp).semantics {
            contentDescription = "Appearance. A clay house on a hill; drag across the sky to move the sun"
            stateDescription = if (dark) "Night" else "Day"
            progressBarRangeInfo = ProgressBarRangeInfo(state.snapshot(clock.doubleValue,reduced).m.toFloat().coerceIn(0f,1f),0f..1f)
            setProgress { value -> state.setTarget(if (value > 0.5f) 1.0 else 0.0,reduced = reduced); true }
            customActions = listOf(
                CustomAccessibilityAction("Night") { state.setTarget(1.0,reduced = reduced); true },
                CustomAccessibilityAction("Day") { state.setTarget(0.0,reduced = reduced); true })
        })
        Box(Modifier.offset((width-16-20-51).dp,(heroTop+254.5f).dp).size(51.dp,31.dp)
            .toggleable(dark,role = Role.Switch) {
                val f = state.snapshot(state.time(),reduced)
                state.setTarget(if (f.on == 1.0) 0.0 else 1.0,reduced = reduced)
                haptics.performHapticFeedback(HapticFeedbackType.ToggleOn)
            }.semantics { contentDescription = "Dark mode" })
        ClayRelightSettingsTheme.rows.forEachIndexed { i,row ->
            Box(Modifier.offset(16.dp,(listTop+i*60).dp).size(cardW.dp,60.dp).semantics {
                contentDescription = row.first; role = Role.Button
            })
        }
    }
}

internal class ClayRelightSettingsRenderer {
    private val hero = RuntimeShader(ClayRelightSettingsShader.HERO)
    private val icons = RuntimeShader(ClayRelightSettingsShader.ICON)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    fun draw(canvas: android.graphics.Canvas, x: Float, y: Float, w: Float, h: Float,
             density: Float, f: ClayRelightSettingsMotion.Frame, phase: Float, icon: Boolean) {
        val shader = if (icon) icons else hero
        val m = f.m
        shader.setFloatUniform("uDensity",density)
        shader.setFloatUniform("uStage",w,h,density,if (icon) 60f else phase)
        shader.setFloatUniform("uSky",m.toFloat(),ClayRelightSettingsMotion.sunAngle(m).toFloat(),
            ClayRelightSettingsMotion.moonAngle(m).toFloat(),ClayRelightSettingsMotion.night(m).toFloat())
        if (icon) {
            shader.setFloatUniform("uGlowA",ClayRelightSettingsMotion.lamp(m,0).toFloat(),
                ClayRelightSettingsMotion.lamp(m,1).toFloat(),ClayRelightSettingsMotion.lamp(m,2).toFloat(),
                ClayRelightSettingsMotion.lamp(m,3).toFloat())
            shader.setFloatUniform("uGlowB",ClayRelightSettingsMotion.lamp(m,4).toFloat())
            shader.setFloatUniform("uDusk",ClayRelightSettingsMotion.dusk(m).toFloat())
            shader.setFloatUniform("uTile",20f)
        } else {
            shader.setFloatUniform("uLamps",ClayRelightSettingsMotion.dusk(m).toFloat(),ClayRelightSettingsMotion.windows(m).toFloat(),ClayRelightSettingsMotion.lamp(m,0).toFloat(),phase)
            shader.setFloatUniform("uLive",0f,0f,f.grab.toFloat(),0f)
        }
        canvas.save()
        // The rest of the screen uses dp. Restore physical pixels for AGSL, then divide by uDensity.
        canvas.scale(1/density,1/density)
        canvas.translate(x*density,y*density)
        paint.shader = shader
        canvas.drawRect(0f,0f,w*density,h*density,paint)
        paint.shader = null
        canvas.restore()
    }
}
