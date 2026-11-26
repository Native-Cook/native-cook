package com.ngocdevv.nativecook.blocks.playground.spinningtoptoy

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.opengl.GLSurfaceView
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.VelocityTracker
import android.widget.Button
import android.widget.FrameLayout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.*

@Composable
fun SpinningTopToy(autoPlay: Boolean=false,modifier: Modifier=Modifier) {
    BlockSystemBars(darkIcons=true)
    val context=LocalContext.current
    val view=remember(context) { SpinningTopToyView(context) }
    val reduced=rememberLiveReducedMotion()
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(view) { onDispose { view.gl.onPause() } }
    LaunchedEffect(view,lifecycle) {
        lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) {
            view.gl.onResume(); view.motion.last = -1.0
            try { while(true) withFrameNanos { view.advance(it/1e9) } }
            finally { view.gl.onPause(); view.motion.last = -1.0 }
        }
    }
    AndroidView(factory={view},modifier=modifier.fillMaxSize(),update={ it.configure(autoPlay,reduced) })
}

@SuppressLint("ViewConstructor", "ClickableViewAccessibility")
internal class SpinningTopToyView(context: Context): FrameLayout(context) {
    val motion=SpinningTopToyMotion()
    private val density=resources.displayMetrics.density
    val gl=GLSurfaceView(context)
    private val renderer=SpinningTopToyRenderer(context,density)
    private var auto=false; private var flicksSeen=0; private var landsSeen=0
    private val chrome=SpinningTopToyChrome(context)
    private val top=Button(context); private val pill=Button(context)
    private var tracker: VelocityTracker?=null
    private var startX=0f; private var startY=0f; private var startTime=0L; private var movedSamples=0
    init {
        setBackgroundColor(Color.rgb(142,135,128)); importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO
        gl.setEGLContextClientVersion(3); gl.setEGLConfigChooser(8,8,8,8,0,0)
        gl.preserveEGLContextOnPause=true; gl.setRenderer(renderer); gl.renderMode=GLSurfaceView.RENDERMODE_WHEN_DIRTY
        gl.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; addView(gl,LayoutParams(-1,-1))
        chrome.importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO; addView(chrome,LayoutParams(-1,-1))
        for((button,label) in listOf(top to "Spin the top",pill to "Flick to spin")) {
            button.text=""; button.contentDescription=label; button.background=null
            button.stateListAnimator=null; button.isSoundEffectsEnabled=false
            button.minimumWidth=0; button.minimumHeight=0; button.minWidth=0; button.minHeight=0
            button.setOnClickListener { motion.spin(); render() }; addView(button)
        }
        top.setOnTouchListener { _,e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    tracker?.recycle(); tracker=VelocityTracker.obtain().also { it.addMovement(e) }
                    startX=e.x; startY=e.y; startTime=e.eventTime; movedSamples=0; motion.touchDown()
                }
                MotionEvent.ACTION_MOVE -> { tracker?.addMovement(e); movedSamples++ }
                MotionEvent.ACTION_UP -> {
                    tracker?.addMovement(e); tracker?.computeCurrentVelocity(1000)
                    val dx=(e.x-startX)/density; val dy=(e.y-startY)/density
                    var vx=(tracker?.xVelocity ?: 0f)/density; var vy=(tracker?.yVelocity ?: 0f)/density
                    if(movedSamples<2 && hypot(vx,vy)<0.001f) {
                        val dt=max(0.016f,(e.eventTime-startTime)/1000f); vx=dx/dt; vy=dy/dt
                    }
                    val speed=hypot(vx,vy)
                    motion.spin(if(hypot(dx,dy)>=12 && speed>=300) speed.toDouble() else 0.0,true)
                    motion.touchUp(); tracker?.recycle(); tracker=null
                    parent?.requestDisallowInterceptTouchEvent(false)
                }
                MotionEvent.ACTION_CANCEL -> { motion.touchUp(); tracker?.recycle(); tracker=null; parent?.requestDisallowInterceptTouchEvent(false) }
            }; render(); true
        }
        pill.setOnTouchListener { _,e ->
            when(e.actionMasked) {
                MotionEvent.ACTION_DOWN -> motion.touchDown(true)
                MotionEvent.ACTION_UP -> { if(e.x>=0 && e.x< pill.width && e.y>=0 && e.y<pill.height) motion.spin(); motion.touchUp() }
                MotionEvent.ACTION_CANCEL -> motion.touchUp()
            }; render(); true
        }
    }
    override fun onLayout(changed: Boolean,l: Int,t: Int,r: Int,b: Int) {
        gl.layout(0,0,width,height); chrome.layout(0,0,width,height)
        fun place(v: View,x: Float,y: Float,w: Float,h: Float) {
            v.layout((x*density).roundToInt(),(y*density).roundToInt(),((x+w)*density).roundToInt(),((y+h)*density).roundToInt())
        }
        val cx=width/density/2; val sy=height/density/852
        place(top,62f,288f,294f,318f); place(pill,cx-164.5f,799*sy-29,329f,58f)
        render()
    }
    fun configure(autoPlay: Boolean,reduced: Boolean) { auto=autoPlay; motion.setReduced(reduced); render() }
    fun advance(now: Double) {
        if(!renderer.ready) { motion.last = -1.0; return }
        motion.step(now,auto)
        if(motion.flicks!=flicksSeen) {
            flicksSeen=motion.flicks
            performHapticFeedback(if(android.os.Build.VERSION.SDK_INT>=34) HapticFeedbackConstants.SEGMENT_TICK else HapticFeedbackConstants.CLOCK_TICK)
            announceForAccessibility("Spinning")
        }
        if(motion.lands!=landsSeen) {
            landsSeen=motion.lands; performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            announceForAccessibility("Down after ${String.format(java.util.Locale.US,"%.1f",motion.duration)} seconds")
        }
        render()
    }
    private fun render() { renderer.frame=motion.frame; gl.requestRender(); chrome.frame=motion.frame; chrome.invalidate() }
}

internal class SpinningTopToyChrome(context: Context): View(context) {
    var frame=SpinningTopToyFrame()
    private val density=resources.displayMetrics.density
    private val faces=listOf("DMSerifDisplay-Regular","Inter-Medium","Inter-SemiBold").associateWith {
        Typeface.createFromAsset(context.assets,"spinningtoptoy/spinningtoptoy_$it.ttf")
    }
    private data class Line(val text: String,val paint: Paint,val xs: FloatArray,val width: Float)
    private fun line(s: String,face: String,size: Float,color: Int,tracking: Float=0f): Line {
        val p=Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG or Paint.LINEAR_TEXT_FLAG).apply {
            typeface=faces[face]; textSize=size; this.color=color; fontFeatureSettings="kern"
        }
        val xs=FloatArray(s.length) { p.getRunAdvance(s,0,s.length,0,s.length,false,it)+it*tracking }
        val width=p.getRunAdvance(s,0,s.length,0,s.length,false,s.length)+s.length*tracking
        return Line(s,p,xs,width)
    }
    private val eyebrow=line("SPIN · DESK TOYS","Inter-SemiBold",12f,0xFF3A322C.toInt(),4f)
    private val unit=line("s","DMSerifDisplay-Regular",44f,0xFF17130F.toInt())
    private val pill=line("Flick to spin","Inter-SemiBold",17f,Color.WHITE)
    private val tenths=(0..99).map { String.format(java.util.Locale.US,"%.1f",it/10.0) }
    private val timers=tenths.map { line(it,"DMSerifDisplay-Regular",96f,0xFF17130F.toInt(),-3f) }
    private val keeps=tenths.map { line("Flick it. Keep it up longer than $it s.","Inter-Medium",15f,0xFF2F2925.toInt()) }
    private val downs=tenths.map { line("Down after $it s. Flick again.","Inter-Medium",15f,0xFF2F2925.toInt()) }
    private val bests=tenths.map { line("Best · $it s","Inter-SemiBold",13f,0xFF2B2622.toInt()) }
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private fun draw(c: Canvas,l: Line,x: Float,y: Float) {
        for(i in l.text.indices) c.drawText(l.text,i,i+1,x+l.xs[i],y,l.paint)
    }
    override fun onDraw(c: Canvas) {
        c.save(); c.scale(density,density)
        val f=frame; val sy=height/density/852; val cx=width/density/2; val tx=cx-12
        fun index(s: Double)=(s*10).roundToInt().coerceIn(0,99)
        draw(c,eyebrow,cx-eyebrow.width/2,90*sy)
        val timer=timers[index(f.seconds)]
        draw(c,timer,tx-timer.width/2,172*sy); draw(c,unit,tx+timer.width/2+4,172*sy)
        val copy=if(f.down) downs[index(f.seconds)] else keeps[index(f.best)]
        draw(c,copy,cx-copy.width/2,214*sy)
        c.save(); c.translate(cx,700*sy+15); c.scale(f.bestPop.toFloat(),f.bestPop.toFloat())
        paint.color=0x80FFFFFF.toInt(); c.drawRoundRect(-57.5f,-15f,57.5f,15f,15f,15f,paint)
        val best=bests[index(f.best)]; draw(c,best,-best.width/2,4f); c.restore()
        c.save(); c.translate(cx,799*sy); if(f.pill) c.scale(0.97f,0.97f)
        paint.color=0xFF141210.toInt(); c.drawRoundRect(-164.5f,-29f,164.5f,29f,29f,29f,paint)
        draw(c,pill,-pill.width/2,6f); c.restore(); c.restore()
    }
}
