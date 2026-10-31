package com.ngocdevv.nativecook.blocks.buttons.livelikebubble

import android.graphics.Canvas as NativeCanvas
import android.graphics.Paint
import android.graphics.Color as NativeColor
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlin.math.*

@Composable
fun LiveLikeBubble(autoPlay: Boolean = false) {
    val context = LocalContext.current
    val view = LocalView.current
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current
    val theme = remember(context) { LiveLikeBubbleTheme(context) }
    val driver = remember { LiveLikeBubbleDriver() }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f) }
    val lifecycleOwner = LocalLifecycleOwner.current
    var active by remember { mutableStateOf(lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, _ ->
            active = lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
            if (active) reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    DisposableEffect(context) {
        val observer = object : android.database.ContentObserver(android.os.Handler(android.os.Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced = Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f) == 0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    LaunchedEffect(autoPlay,reduced) { driver.configure(autoPlay,reduced) }
    val running = active && ((!driver.manual && autoPlay && !reduced) || !driver.idle)
    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        var previous = withFrameNanos { it }
        while (true) {
            withFrameNanos { now ->
                driver.tick((now-previous)/1_000_000_000.0) { haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove) }
                previous = now
            }
        }
    }
    DisposableEffect(view) {
        val activity = generateSequence(view.context) { (it as? android.content.ContextWrapper)?.baseContext }
            .filterIsInstance<android.app.Activity>().firstOrNull()
        val controller = activity?.let { androidx.core.view.WindowCompat.getInsetsController(it.window,view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller?.isAppearanceLightStatusBars = previous }
    }
    val renderer = remember { if (Build.VERSION.SDK_INT >= 33) LiveLikeBubbleRuntime() else null }
    val paint = remember { Paint(Paint.ANTI_ALIAS_FLAG) }
    val top = WindowInsets.statusBars.getTop(density)/density.density
    val bottom = WindowInsets.navigationBars.getBottom(density)/density.density
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val w = maxWidth.value; val h = maxHeight.value
        val ppu = floor(w*0.32f+0.5f)
        val centreY = top+floor((h-top-bottom)*0.42f+0.5f)
        val buttonY = h-max(bottom,16f)-8f-36f
        val fs = floor(ppu*0.5f+0.5f)
        Canvas(Modifier.fillMaxSize().pointerInput(w,h,ppu,buttonY) {
            awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                driver.takeOver()
                val p = down.position/density.density
                var button = p.x >= w/2-36 && p.x <= w/2+36 && p.y >= buttonY-36 && p.y <= buttonY+36
                if (button) { driver.begin(((p.x-w/2)/ppu).toDouble()); down.consume() }
                try {
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (!change.pressed) break
                        if (button && (change.position-down.position).getDistance()/density.density > 600f) {
                            driver.end(); button = false
                        }
                    }
                } finally { driver.end() }
            }
        }) {
            val frame = driver.frame()
            val hearts = driver.hearts()
            drawIntoCanvas { composeCanvas ->
                val c = composeCanvas.nativeCanvas
                c.save(); c.scale(density.density,density.density)
                paint.reset(); paint.isAntiAlias = true
                if (Build.VERSION.SDK_INT >= 33 && renderer != null) {
                    renderer.draw(c,w,h,ppu,centreY,density.density,frame,hearts,reduced)
                } else drawLiveLikeBubbleFallback(c,paint,w,h,ppu,centreY,frame,hearts)
                paint.shader = null
                paint.color = LiveLikeBubbleTheme.live; paint.style = Paint.Style.FILL
                paint.typeface = theme.semibold; paint.textSize = 13f; paint.letterSpacing = 0.6f/13f
                val liveWidth = paint.measureText("LIVE")+20f
                c.drawRoundRect(20f,top+10f,20f+liveWidth,top+36f,8f,8f,paint)
                paint.color = NativeColor.WHITE
                c.drawText("LIVE",30f,top+23f-(paint.ascent()+paint.descent())/2,paint)
                paint.typeface = theme.medium; paint.textSize = 15f; paint.letterSpacing = 0f
                paint.color = LiveLikeBubbleTheme.ink
                c.drawText("2.4K watching",20f+liveWidth+10f,top+23f-(paint.ascent()+paint.descent())/2,paint)
                c.save()
                val countX = w/2+0.02f*ppu+0.95f*ppu/2
                val pop = (1+0.07*max(0.0,frame.swell)).toFloat()
                c.scale(pop,pop,countX,centreY)
                paint.typeface = theme.semibold; paint.textSize = fs; paint.color = NativeColor.WHITE
                paint.fontFeatureSettings = "'tnum' 1"; paint.letterSpacing = -1f/fs
                val count = frame.count.toString()
                c.drawText(count,countX-paint.measureText(count)/2,centreY-(paint.ascent()+paint.descent())/2,paint)
                c.restore()
                paint.typeface = theme.medium; paint.textSize = 15f; paint.fontFeatureSettings = null; paint.letterSpacing = 0f
                paint.color = LiveLikeBubbleTheme.mute
                val caption = "likes in the last minute"
                c.drawText(caption,w/2-paint.measureText(caption)/2,centreY+ppu*1.35f-paint.ascent(),paint)
                c.save(); val press = (1-0.08*frame.press).toFloat(); c.scale(press,press,w/2,buttonY)
                paint.color = NativeColor.WHITE
                paint.setShadowLayer(12f,0f,8f,NativeColor.argb(31,30,30,60))
                c.drawCircle(w/2,buttonY,36f,paint); paint.clearShadowLayer()
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1f; paint.color = LiveLikeBubbleTheme.line
                c.drawCircle(w/2,buttonY,35.5f,paint); paint.style = Paint.Style.FILL
                drawLiveLikeBubbleHeart(c,paint,w/2,buttonY,30f,28f,LiveLikeBubbleTheme.live)
                c.restore()
                c.save(); val fingerScale = (1-0.12*frame.press).toFloat(); c.scale(fingerScale,fingerScale,w/2,buttonY)
                val opacity = (0.85*frame.finger).toFloat()
                paint.color = NativeColor.argb((255*0.18f*opacity).roundToInt(),30,30,60)
                c.drawCircle(w/2,buttonY,22f,paint)
                paint.color = NativeColor.argb((255*0.35f*opacity).roundToInt(),30,30,60)
                paint.style = Paint.Style.STROKE; paint.strokeWidth = 1.5f
                c.drawCircle(w/2,buttonY,21.25f,paint); paint.style = Paint.Style.FILL
                c.restore(); c.restore()
            }
        }
        Box(Modifier.offset(20.dp,(top+10).dp).size(190.dp,26.dp).semantics {
            contentDescription = "LIVE, 2.4K watching"
        })
        // The count invalidates semantics only when its integer changes, never for every draw frame.
        val count by remember { derivedStateOf { driver.frame().count } }
        Box(Modifier.offset { IntOffset(((w/2+0.02f*ppu)*density.density).roundToInt(),((centreY-fs*0.75f)*density.density).roundToInt()) }
            .size((0.95f*ppu).dp,(fs*1.5f).dp).semantics {
                contentDescription = "$count likes in the last minute"; liveRegion = LiveRegionMode.Polite
            })
        Box(Modifier.offset((w/2-36).dp,(buttonY-36).dp).size(72.dp).semantics {
            role = Role.Button; contentDescription = "Send a like"
            onClick(label = "Hold to send many") { driver.begin(0.0); driver.end(); true }
        })
    }
}

@RequiresApi(33)
private class LiveLikeBubbleRuntime {
    private val shader = RuntimeShader(LiveLikeBubbleShader.source)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = this@LiveLikeBubbleRuntime.shader }
    fun draw(c: NativeCanvas,w: Float,h: Float,ppu: Float,cy: Float,ratio: Float,f: LiveLikeBubbleMotion.Frame,hs: Array<FloatArray>,reduced: Boolean) {
        shader.setFloatUniform("uView",w,h,ppu,ratio)
        shader.setFloatUniform("uBubble",cy,max(0.0,f.swell).toFloat(),if (reduced) 0f else f.swell.toFloat(),0.3f)
        for (i in hs.indices) shader.setFloatUniform("uH$i",hs[i])
        c.drawRect(0f,0f,w,h,paint)
    }
}

private fun drawLiveLikeBubbleHeart(c: NativeCanvas,p: Paint,x: Float,y: Float,w: Float,h: Float,color: Int) {
    c.save(); c.translate(x,y); val scale = min(w/24,h/22); c.scale(scale,scale); c.translate(-12f,-11f)
    p.color = color; c.drawPath(LiveLikeBubbleTheme.heart,p); c.restore()
}

private fun drawLiveLikeBubbleFallback(c: NativeCanvas,p: Paint,w: Float,h: Float,ppu: Float,cy: Float,f: LiveLikeBubbleMotion.Frame,hs: Array<FloatArray>) {
    c.drawColor(LiveLikeBubbleTheme.page)
    val swell = (1+0.07*max(0.0,f.swell)).toFloat()
    c.save(); c.scale(swell,swell,w/2,cy)
    p.color = LiveLikeBubbleTheme.live
    c.drawRoundRect(w/2-1.1f*ppu,cy-0.62f*ppu,w/2+1.1f*ppu,cy+0.62f*ppu,0.2f*ppu,0.2f*ppu,p)
    c.save(); c.rotate(45f,w/2-0.1f*ppu,cy+0.74f*ppu)
    c.drawRoundRect(w/2-0.29f*ppu,cy+0.55f*ppu,w/2+0.09f*ppu,cy+0.93f*ppu,0.07f*ppu,0.07f*ppu,p); c.restore()
    drawLiveLikeBubbleHeart(c,p,w/2-0.5f*ppu,cy,0.65f*ppu,0.65f*ppu,NativeColor.WHITE)
    c.restore()
    for (heart in hs) if (heart[3] > 0.001f) {
        val y = cy-((heart[1]-1.15f)*cos(0.3f)-heart[2]*sin(0.3f))*ppu
        drawLiveLikeBubbleHeart(c,p,w/2+heart[0]*ppu,y,heart[3]*ppu*1.7f,heart[3]*ppu*1.7f,LiveLikeBubbleTheme.live)
    }
}
