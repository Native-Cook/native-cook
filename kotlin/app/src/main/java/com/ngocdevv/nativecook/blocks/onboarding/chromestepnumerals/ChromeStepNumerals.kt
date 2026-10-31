package com.ngocdevv.nativecook.blocks.onboarding.chromestepnumerals

import android.app.Activity
import android.content.ContextWrapper
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import android.provider.Settings
import android.text.Layout
import android.text.StaticLayout
import android.text.TextPaint
import androidx.annotation.RequiresApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlinx.coroutines.isActive
import kotlin.math.*

@Composable
fun ChromeStepNumerals(autoPlay: Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    val density=LocalDensity.current
    val haptic=LocalHapticFeedback.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var reduced by remember(context) { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) {
        val observer=object: ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
            }
        }
        context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,observer)
        onDispose { context.contentResolver.unregisterContentObserver(observer) }
    }
    val auto=autoPlay && !reduced
    val driver=remember { ChromeStepNumeralsDriver(auto) }
    val art=remember(context) { ChromeStepNumeralsArt(context) }
    val frame=remember(driver) { mutableStateOf(driver.frame) }
    var wake by remember(driver) { mutableIntStateOf(0) }
    LaunchedEffect(auto,reduced) {
        driver.configure(auto)
        frame.value=driver.sample(driver.now(),auto,reduced)
        wake++
    }
    val page by remember(driver) { derivedStateOf { floor(frame.value.r.coerceIn(0.0,2.0)+0.5).toInt() } }
    val safeTop=WindowInsets.safeDrawing.getTop(density)/density.density.toDouble()
    val safeBottom=WindowInsets.safeDrawing.getBottom(density)/density.density.toDouble()
    // Only a step boundary causes a composition/haptic; per-frame values are read in drawing.
    var previousPage by remember(driver) { mutableIntStateOf(0) }
    LaunchedEffect(page) {
        if(page!=previousPage && driver.manual) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        previousPage=page
    }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }
            .filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val before=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(before!=null) controller?.isAppearanceLightStatusBars=before }
    }
    LaunchedEffect(driver,wake,lifecycle,auto,reduced) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            driver.active(true)
            try {
                do {
                    withFrameNanos { frame.value=driver.sample(driver.now(),auto,reduced) }
                } while(isActive && driver.needsFrames(auto))
            } finally { driver.active(false) }
        }
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val geometry=ChromeStepNumeralsTheme.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),safeTop,safeBottom)
        Canvas(Modifier.fillMaxSize().pointerInput(driver,reduced,geometry) {
            awaitEachGesture {
                val down=awaitFirstDown(requireUnconsumed=false)
                val start=down.position
                var end=start
                val tracker=VelocityTracker()
                tracker.addPosition(down.uptimeMillis,start)
                driver.begin(auto,reduced); frame.value=driver.frame; wake++
                var released=false
                try {
                    do {
                        val event=awaitPointerEvent()
                        val change=event.changes.firstOrNull { it.id==down.id } ?: break
                        end=change.position; tracker.addPosition(change.uptimeMillis,end)
                        driver.touched()
                        if(change.pressed) {
                            driver.drag(((end.x-start.x)/density.density).toDouble(),geometry.w,auto,reduced)
                            frame.value=driver.frame
                        }
                        if(driver.dragging) change.consume()
                        if(!change.pressed) released=true
                    } while(change.pressed)
                } finally {
                    val wasDragging=driver.dragging
                    val velocity=tracker.calculateVelocity()
                    driver.release((velocity.x/density.density).toDouble(),geometry.w,reduced)
                    val dx=(end.x-start.x)/density.density
                    val dy=(end.y-start.y)/density.density
                    val ex=end.x/density.density; val ey=end.y/density.density
                    if(!wasDragging && released && abs(dx)<10 && abs(dy)<10 &&
                        ex>=24 && ex<=geometry.w-24 && ey>=geometry.buttonTop && ey<=geometry.buttonTop+52) {
                        driver.next(auto,reduced)
                    }
                    frame.value=driver.frame; wake++
                }
            }
        }) {
            val snapshot=frame.value
            drawIntoCanvas { art.draw(it.nativeCanvas,geometry,density.density,snapshot,reduced) }
        }
        Spacer(Modifier.offset(20.dp,(safeTop+8).dp).size(70.dp,24.dp).semantics {
            role=Role.Button; contentDescription="← Back"
        })
        Spacer(Modifier.offset((geometry.w-60).dp,(safeTop+8).dp).size(40.dp,24.dp).semantics {
            role=Role.Button; contentDescription="Skip"
        })
        Spacer(Modifier.offset(32.dp,geometry.titleTop.dp).size((geometry.w-64).dp,94.dp).semantics {
            contentDescription=ChromeStepNumeralsTheme.titles[page]+". "+ChromeStepNumeralsTheme.lines[page]
        })
        Spacer(Modifier.offset(0.dp,geometry.dotsTop.dp).size(geometry.w.dp,8.dp).semantics {
            contentDescription="Step ${page+1} of 3"
        })
        Spacer(Modifier.offset(24.dp,geometry.buttonTop.dp).size((geometry.w-48).dp,52.dp).semantics {
            role=Role.Button; contentDescription=if(page==2) "Get started" else "Next"
            onClick {
                driver.next(auto,reduced); frame.value=driver.frame; wake++; true
            }
        })
    }
}

// Text and geometry stay in dp. Only the RuntimeShader's incoming pixel coordinate is divided by density.
private class ChromeStepNumeralsArt(context: android.content.Context) {
    private val regular=ChromeStepNumeralsTheme.font(context,"Regular")
    private val medium=ChromeStepNumeralsTheme.font(context,"Medium")
    private val semibold=ChromeStepNumeralsTheme.font(context,"SemiBold")
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var cacheWidth= -1
    private var paragraphs=emptyList<StaticLayout>()
    private val chrome=if(Build.VERSION.SDK_INT>=33) ChromeStepNumeralsRuntime() else null
    private fun text(canvas: android.graphics.Canvas,copy: String,x: Double,y: Double,size: Float,color: Int,face: android.graphics.Typeface,align: Paint.Align=Paint.Align.LEFT) {
        paint.reset(); paint.isAntiAlias=true; paint.typeface=face; paint.textSize=size; paint.color=color; paint.textAlign=align
        canvas.drawText(copy,x.toFloat(),(y-paint.fontMetrics.ascent).toFloat(),paint)
    }
    fun draw(canvas: android.graphics.Canvas,l: ChromeStepNumeralsTheme.Layout,density: Float,f: ChromeStepNumeralsMotion.Frame,reduced: Boolean) {
        canvas.drawColor(ChromeStepNumeralsTheme.page)
        if(Build.VERSION.SDK_INT>=33) chrome?.draw(canvas,l,density,f,reduced)
        canvas.save(); canvas.scale(density,density)
        if(chrome==null) {
            // API 26–32: a legible, flat numeral in the same neutral/amber palette.
            paint.reset(); paint.isAntiAlias=true; paint.color=0x228A847C; paint.style=Paint.Style.FILL
            canvas.drawOval((l.w/2-l.unit*0.7).toFloat(),(l.floorY-8).toFloat(),(l.w/2+l.unit*0.7).toFloat(),(l.floorY+8).toFloat(),paint)
            text(canvas,(floor(f.r.coerceIn(0.0,2.0)+0.5).toInt()+1).toString(),l.w/2,l.cy-l.unit, (l.unit*2).toFloat(),ChromeStepNumeralsTheme.mute,semibold,Paint.Align.CENTER)
        }
        text(canvas,"← Back",20.0,l.top+8,15f,ChromeStepNumeralsTheme.accent,medium)
        text(canvas,"Skip",l.w-20,l.top+8,15f,ChromeStepNumeralsTheme.mute,medium,Paint.Align.RIGHT)
        val width=(l.w-64).roundToInt().coerceAtLeast(1)
        if(cacheWidth!=width) {
            cacheWidth=width
            paragraphs=ChromeStepNumeralsTheme.lines.map { copy ->
                val tp=TextPaint(Paint.ANTI_ALIAS_FLAG).apply { typeface=regular; textSize=16f; color=ChromeStepNumeralsTheme.mute }
                StaticLayout.Builder.obtain(copy,0,copy.length,tp,width).setAlignment(Layout.Alignment.ALIGN_CENTER)
                    .setIncludePad(false).setLineSpacing(23-tp.fontSpacing,1f).build()
            }
        }
        for(i in 0..2) {
            val d=f.r-i; val k=max(0.0,1-abs(d)*1.8)
            if(k==0.0) continue
            canvas.saveLayerAlpha(null,(255*k*k).roundToInt())
            val tx= -d*l.w*0.3
            paint.reset(); paint.isAntiAlias=true; paint.typeface=semibold; paint.textSize=26f
            paint.color=ChromeStepNumeralsTheme.ink; paint.textAlign=Paint.Align.CENTER
            val baseline=l.titleTop+(32-(paint.fontMetrics.descent-paint.fontMetrics.ascent))/2-paint.fontMetrics.ascent
            canvas.drawText(ChromeStepNumeralsTheme.titles[i],(l.w/2+tx).toFloat(),baseline.toFloat(),paint)
            canvas.save(); canvas.translate((32+tx).toFloat(),(l.titleTop+40).toFloat())
            paragraphs[i].draw(canvas); canvas.restore(); canvas.restore()
        }
        val widths=(0..2).map { 8+16*max(0.0,1-abs(f.r-it)) }
        var x=(l.w-widths.sum()-12)/2
        for(i in 0..2) {
            val k=max(0.0,1-abs(f.r-i))
            paint.reset(); paint.isAntiAlias=true; paint.color=if(k>0.5) ChromeStepNumeralsTheme.accent else ChromeStepNumeralsTheme.dot
            canvas.drawRoundRect(x.toFloat(),l.dotsTop.toFloat(),(x+widths[i]).toFloat(),(l.dotsTop+8).toFloat(),4f,4f,paint)
            x+=widths[i]+6
        }
        canvas.save(); val scale=(1-0.04*f.press).toFloat()
        canvas.scale(scale,scale,(l.w/2).toFloat(),(l.buttonTop+26).toFloat())
        paint.reset(); paint.isAntiAlias=true; paint.color=ChromeStepNumeralsTheme.accent
        canvas.drawRoundRect(24f,l.buttonTop.toFloat(),(l.w-24).toFloat(),(l.buttonTop+52).toFloat(),26f,26f,paint)
        paint.typeface=semibold; paint.textSize=17f; paint.textAlign=Paint.Align.CENTER; paint.color=android.graphics.Color.WHITE
        canvas.drawText(if(f.r.coerceIn(0.0,2.0)>=1.5) "Get started" else "Next",(l.w/2).toFloat(),(l.buttonTop+26-(paint.fontMetrics.ascent+paint.fontMetrics.descent)/2).toFloat(),paint)
        canvas.restore()
        if(f.finger>0) {
            val fx=if(f.fingerAt==0) l.w/2 else l.w*f.fingerX
            val fy=if(f.fingerAt==0) l.buttonTop+26 else l.cy+l.unit*0.4
            canvas.save(); val fs=(1-0.12*f.press).toFloat(); canvas.scale(fs,fs,fx.toFloat(),fy.toFloat())
            paint.reset(); paint.isAntiAlias=true; paint.color=android.graphics.Color.argb((255*0.18*0.85*f.finger).roundToInt(),40,30,20)
            canvas.drawCircle(fx.toFloat(),fy.toFloat(),22f,paint)
            paint.style=Paint.Style.STROKE; paint.strokeWidth=1.5f
            paint.color=android.graphics.Color.argb((255*0.35*0.85*f.finger).roundToInt(),40,30,20)
            canvas.drawCircle(fx.toFloat(),fy.toFloat(),21.25f,paint); canvas.restore()
        }
        canvas.restore()
    }
}

@RequiresApi(33)
private class ChromeStepNumeralsRuntime {
    private val shader=RuntimeShader(ChromeStepNumeralsShader.source)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply { shader=this@ChromeStepNumeralsRuntime.shader }
    fun draw(canvas: android.graphics.Canvas,l: ChromeStepNumeralsTheme.Layout,density: Float,f: ChromeStepNumeralsMotion.Frame,reduced: Boolean) {
        shader.setFloatUniform("uDensity",density)
        shader.setFloatUniform("uView",(l.w/2).toFloat(),l.cy.toFloat(),l.unit.toFloat(),density)
        shader.setFloatUniform("uStep",f.r.toFloat(),(if(reduced) 0.3 else ChromeStepNumeralsMotion.yawOf(f.r)).toFloat(),0f,f.bob.toFloat())
        shader.setFloatUniform("uLight",0f,l.floorY.toFloat(),0f,0f)
        canvas.drawRect(0f,0f,(l.w*density).toFloat(),(l.h*density).toFloat(),paint)
    }
}
