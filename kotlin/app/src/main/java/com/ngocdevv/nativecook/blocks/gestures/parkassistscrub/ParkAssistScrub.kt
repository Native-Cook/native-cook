package com.ngocdevv.nativecook.blocks.gestures.parkassistscrub

import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*
import java.util.Locale

import com.ngocdevv.nativecook.blocks.gestures.parkassistscrub.ParkAssistScrubTheme as T
import com.ngocdevv.nativecook.blocks.gestures.parkassistscrub.ParkAssistScrubMotion as M

@Composable
fun ParkAssistScrub(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val density = LocalDensity.current
    val haptics = LocalHapticFeedback.current
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var active by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f) }
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val driver = remember(autoPlay,haptics) {
        ParkAssistScrubDriver(autoPlay,reduced) { kind ->
            haptics.performHapticFeedback(when(kind) {
                "beep" -> HapticFeedbackType.SegmentTick
                "parked" -> HapticFeedbackType.Confirm
                else -> HapticFeedbackType.ContextClick
            })
        }
    }
    LaunchedEffect(driver,reduced) { driver.updateReduced(reduced) }
    LaunchedEffect(driver,active) {
        if (active) {
            var last = withFrameNanos { it }
            while (true) {
                withFrameNanos { now -> driver.tick((now-last)/1e9); last = now }
            }
        }
    }
    val regular = remember(context) { T.font(context,"Regular") }
    val semibold = remember(context) { T.font(context,"SemiBold") }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val shader = remember { if (Build.VERSION.SDK_INT >= 33) RuntimeShader(PARK_ASSIST_SCRUB_AGSL) else null }
    val top = WindowInsets.systemBars.getTop(density)/density.density
    val bottom = WindowInsets.systemBars.getBottom(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize().background(T.page)) {
        val w = maxWidth.value; val h = maxHeight.value
        val headTop = top+8; val stageTop = headTop+64
        val buttonTop = h-max(bottom,16f)-8-64
        val stageH = min(560f,buttonTop-24-stageTop)
        val parked by remember(driver) { derivedStateOf { driver.frame.parked > 0.5 } }
        Box(Modifier.fillMaxSize().pointerInput(driver,w,buttonTop,density.density) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed=false)
                val p = down.position/density.density
                driver.begin(p.x >= 20 && p.x <= w-20 && p.y >= buttonTop && p.y <= buttonTop+64)
                var cancelled = true
                var tapValid = true
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if ((change.position-down.position).getDistance() > 10*density.density || change.uptimeMillis-down.uptimeMillis > 500) tapValid = false
                        if (!change.pressed) { cancelled = false; break }
                        if ((change.position-down.position).getDistance() > 600*density.density) break
                    }
                } finally { driver.end(cancelled,tapValid) }
            }
        }) {
            Canvas(Modifier.fillMaxSize()) {
                val f = driver.frame
                val canvas = drawContext.canvas.nativeCanvas
                val d = density.density
                val gap = M.gapAt(f.s)
                canvas.save(); canvas.scale(d,d)
                fun rect(x: Float,y: Float,rw: Float,rh: Float,radius: Float,color: Int) {
                    paint.shader = null; paint.style = Paint.Style.FILL; paint.color = color
                    canvas.drawRoundRect(x,y,x+rw,y+rh,radius,radius,paint)
                }
                fun text(value: String,x: Float,y: Float,size: Float,lineHeight: Float,color: Int,alpha: Double = 1.0,center: Boolean = false,label: Boolean = false) {
                    paint.shader = null; paint.style = Paint.Style.FILL; paint.typeface = if (label) regular else semibold
                    paint.textSize = size; paint.color = color; paint.alpha = (255*alpha.coerceIn(0.0,1.0)).toInt()
                    paint.textAlign = if (center) Paint.Align.CENTER else Paint.Align.LEFT
                    paint.fontFeatureSettings = "tnum"
                    val baseline = y+(lineHeight-paint.fontMetrics.descent-paint.fontMetrics.ascent)/2
                    canvas.drawText(value,x,baseline,paint)
                }
                text("Park Assist",20f,headTop,24f,30f,T.ink.toArgb())
                text(if (f.parked > 0.5) "Parked · 30 cm to the wall" else "Hold the button to reverse into the bay",
                    20f,headTop+32,14f,20f,T.mute.toArgb(),label=true)
                canvas.save(); canvas.translate(0f,stageTop); canvas.clipRect(0f,0f,w,stageH)
                if (Build.VERSION.SDK_INT >= 33 && shader != null) {
                    val pose = M.poseAt(f.s)
                    shader.setFloatUniform("uDensity",d)
                    shader.setFloatUniform("uView",w,stageH,58f,d)
                    shader.setFloatUniform("uCam",0.75f,1.9f,0.62f,0f)
                    shader.setFloatUniform("uCar",pose.x.toFloat(),pose.y.toFloat(),pose.heading.toFloat(),
                        ((if (f.out > 0.5) -1 else 1)*M.steerAt(f.s)).toFloat())
                    shader.setFloatUniform("uAssist",gap.toFloat(),f.assist.toFloat(),f.brake.toFloat(),min(1.0,f.hazard).toFloat())
                    // Draw just this pass in physical pixels; AGSL divides its input by density.
                    canvas.save(); canvas.scale(1/d,1/d)
                    paint.color = android.graphics.Color.WHITE; paint.alpha = 255; paint.shader = shader
                    canvas.drawRect(0f,0f,w*d,stageH*d,paint); paint.shader = null
                    canvas.restore()
                } else {
                    ParkAssistScrubFallback.draw(canvas,paint,w,stageH,f)
                }
                val readAlpha = 0.35+0.65*f.assist
                canvas.saveLayerAlpha(0f,0f,w,stageH,(255*readAlpha).toInt())
                paint.typeface = semibold; paint.textSize = 11f
                val labelHeight = paint.fontMetrics.descent-paint.fontMetrics.ascent
                val readHeight = labelHeight+34+16+2
                rect(16f,12f,140f,readHeight,16f,android.graphics.Color.argb(184,14,15,18))
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = T.line.toArgb(); paint.alpha = 255
                canvas.drawRoundRect(16.5f,12.5f,155.5f,12+readHeight-0.5f,15.5f,15.5f,paint)
                // RN letterSpacing = 0.8 pt; use per-glyph positions for this four-letter label.
                paint.style = Paint.Style.FILL; paint.typeface = semibold; paint.textSize = 11f; paint.color = T.mute.toArgb()
                paint.textAlign = Paint.Align.LEFT
                var labelX = 31f
                for (ch in "REAR") { val glyph = ch.toString(); canvas.drawText(glyph,labelX,21f-paint.fontMetrics.ascent,paint); labelX += paint.measureText(glyph)+0.8f }
                text(if (gap >= 10) "10 m" else String.format(Locale.US,"%.2f m",gap),31f,21f+labelHeight,28f,34f,T.gapColor(gap).toArgb())
                canvas.restore(); canvas.restore()
                canvas.save()
                canvas.scale((1-0.03*f.holding).toFloat(),(1-0.03*f.holding).toFloat(),w/2,buttonTop+32)
                val button = RectF(20f,buttonTop,w-20,buttonTop+64)
                val clip = android.graphics.Path().apply { addRoundRect(button,32f,32f,android.graphics.Path.Direction.CW) }
                canvas.clipPath(clip)
                rect(20f,buttonTop,w-40,64f,32f,T.card.toArgb())
                rect(20f,buttonTop,(w-40)*max(0.0001,f.s).toFloat(),64f,0f,android.graphics.Color.argb(71,255,138,61))
                text("Hold to park",w/2,buttonTop,17f,64f,T.ink.toArgb(),1-f.parked,center=true)
                text("Drive out",w/2,buttonTop,17f,64f,T.ink.toArgb(),f.parked,center=true)
                paint.style = Paint.Style.STROKE; paint.shader = null; paint.color = T.line.toArgb(); paint.alpha = 255; paint.strokeWidth = 1f
                canvas.drawRoundRect(20.5f,buttonTop+0.5f,w-20.5f,buttonTop+63.5f,31.5f,31.5f,paint)
                canvas.restore()
                val fingerScale = (1-0.12*f.holding).toFloat()
                canvas.saveLayerAlpha(0f,buttonTop-10,w,buttonTop+74,(255*0.85*f.finger).toInt())
                canvas.scale(fingerScale,fingerScale,w/2,buttonTop+32)
                paint.style = Paint.Style.FILL; paint.color = android.graphics.Color.argb(89,255,255,255)
                canvas.drawCircle(w/2,buttonTop+32,22f,paint)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f; paint.color = android.graphics.Color.argb(204,255,255,255)
                canvas.drawCircle(w/2,buttonTop+32,21.25f,paint)
                canvas.restore(); canvas.restore()
            }
            Box(Modifier.offset(20.dp,headTop.dp).size((w-40).dp,52.dp).semantics {
                heading(); contentDescription = "Park Assist. " + if (parked) "Parked · 30 cm to the wall" else "Hold the button to reverse into the bay"
            })
            Box(Modifier.offset(16.dp,(stageTop+12).dp).size(140.dp,65.dp).semantics {
                contentDescription = "Gap to the wall"
                stateDescription = String.format(Locale.US,"%.2f metres",M.gapAt(driver.frame.s))
                liveRegion = LiveRegionMode.Polite
            })
            Box(Modifier.offset(20.dp,buttonTop.dp).size((w-40).dp,64.dp).semantics {
                role = Role.Button; contentDescription = if (parked) "Drive out" else "Hold to park"
                stateDescription = if (parked) "Parked" else "Reverses the car while held"
                onClick(label=if (parked) "Drive out" else "Hold to park") {
                    if (parked) driver.accessibilityOut() else driver.accessibilityHold(); true
                }
                customActions = listOf(CustomAccessibilityAction("Stop reversing") { driver.end(); true })
            })
        }
    }
}

