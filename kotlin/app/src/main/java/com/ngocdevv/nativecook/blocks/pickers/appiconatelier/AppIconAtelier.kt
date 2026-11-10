package com.ngocdevv.nativecook.blocks.pickers.appiconatelier

import android.content.Context
import android.app.Activity
import android.content.ContextWrapper
import android.graphics.Canvas
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.VelocityTracker
import android.view.View
import android.view.WindowInsets as AppIconAtelierWindowInsets
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import kotlin.math.*

@Composable
fun AppIconAtelier(autoPlay:Boolean=false) {
    val context=LocalContext.current
    val view=LocalView.current
    var shown by remember { mutableIntStateOf(0) }
    var applied by remember { mutableIntStateOf(0) }
    var updated by remember { mutableStateOf(false) }
    DisposableEffect(view) {
        val activity=generateSequence(view.context) { (it as? ContextWrapper)?.baseContext }.filterIsInstance<Activity>().firstOrNull()
        val controller=activity?.let { WindowCompat.getInsetsController(it.window,view) }
        val previous=controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars=true
        onDispose { if(previous!=null)controller.isAppearanceLightStatusBars=previous }
    }
    val reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f
    val surface=remember(context,autoPlay,reduced){AppIconAtelierSurface(context,autoPlay,reduced)}
    DisposableEffect(surface){onDispose{surface.dispose()}}
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value.toDouble()
        val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value.toDouble()
        val L=AppIconAtelierTheme.Layout(maxWidth.value.toDouble(),maxHeight.value.toDouble(),top,bottom)
        AndroidView(factory={surface},modifier=Modifier.fillMaxSize(),update={ it.onState={s,a,u->shown=s;applied=a;updated=u} })
        // Semantics-only controls leave raw pointer routing to the native surface.
        Box(Modifier.offset(y=(top+44).dp).size(maxWidth,(L.eyebrowTop-top-44).dp).semantics {
            contentDescription="Amora app icon"
            stateDescription=AppIconAtelierTheme.glasses[shown].name
            customActions=listOf(CustomAccessibilityAction("Next glass"){surface.pick((surface.driver.frame.shown+1).coerceAtMost(4));true},CustomAccessibilityAction("Previous glass"){surface.pick((surface.driver.frame.shown-1).coerceAtLeast(0));true},CustomAccessibilityAction("Preview on the Home Screen"){surface.preview();true})
        })
        for(i in 0..4)Box(Modifier.offset(x=(L.chip(i.toDouble())-26).dp,y=L.chipsTop.dp).size(52.dp).semantics {
            role=Role.Button;contentDescription=AppIconAtelierTheme.glasses[i].name;selected=i==shown
            onClick{surface.pick(i);true}
        })
        Box(Modifier.offset(x=16.dp,y=L.ctaTop.dp).size((L.w-32).dp,56.dp).semantics {
            role=Role.Button;contentDescription=if(updated)"Icon Updated" else if(shown==applied)"Current Icon" else "Use This Icon"
            if(shown==applied&&!updated)disabled()
            onClick{surface.apply();true}
        })
        Box(Modifier.offset(x=16.dp,y=L.cardY.dp).size(L.cardW.dp,168.dp).semantics { contentDescription="Home Screen preview. Amora uses ${AppIconAtelierTheme.glasses[applied].name}." })
    }
}

internal class AppIconAtelierSurface(context:Context,auto:Boolean,reduced:Boolean):View(context) {
    val driver=AppIconAtelierDriver(auto,reduced){kind->
        performHapticFeedback(when(kind){1->HapticFeedbackConstants.VIRTUAL_KEY;2->if(Build.VERSION.SDK_INT>=30)HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.VIRTUAL_KEY;else->HapticFeedbackConstants.CLOCK_TICK})
    }
    var onState:(Int,Int,Boolean)->Unit={_,_,_->}
    private var reportedShown=-1;private var reportedApplied=-1;private var reportedUpdated=false
    private val art=AppIconAtelierArt(context)
    private val density=resources.displayMetrics.density.toDouble()
    private var lastNanos=0L
    private var layout=AppIconAtelierTheme.Layout(402.0,874.0,24.0,24.0)
    private var x0=0.0;private var y0=0.0;private var target=-1;private var failed=false
    private var tracking:VelocityTracker?=null
    private var down=false
    private val held=Runnable{if(down&&target==5&&!driver.dragging&&!failed){driver.hold();invalidate()}}
    init { importantForAccessibility=IMPORTANT_FOR_ACCESSIBILITY_NO;isClickable=true }
    fun dispose(){removeCallbacks(held);tracking?.recycle();tracking=null}
    fun pick(i:Int){driver.pick(i);invalidate()}
    fun apply(){driver.apply();invalidate()}
    fun preview(){driver.down();driver.hold();invalidate();postDelayed({driver.up(0.0,layout.heroS);invalidate()},1000)}
    override fun onWindowVisibilityChanged(visibility:Int){super.onWindowVisibilityChanged(visibility);lastNanos=0;if(visibility==VISIBLE)invalidate()}
    override fun onDraw(canvas:Canvas){
        super.onDraw(canvas)
        val now=System.nanoTime();val dt=if(lastNanos==0L)0.0 else min(.5,(now-lastNanos)/1e9);lastNanos=now
        val insets=rootWindowInsets
        val top:Double;val bottom:Double
        if(Build.VERSION.SDK_INT>=30){val bars=insets?.getInsets(AppIconAtelierWindowInsets.Type.systemBars() or AppIconAtelierWindowInsets.Type.displayCutout());top=(bars?.top?:0)/density;bottom=(bars?.bottom?:0)/density}
        else {@Suppress("DEPRECATION") top=(insets?.systemWindowInsetTop?:0)/density;@Suppress("DEPRECATION") bottom=(insets?.systemWindowInsetBottom?:0)/density}
        layout=AppIconAtelierTheme.Layout(width/density,height/density,top,bottom)
        val frame=driver.step(dt)
        canvas.save();canvas.scale(density.toFloat(),density.toFloat());art.draw(canvas,layout,frame,driver.updated,density);canvas.restore()
        if(reportedShown!=frame.shown||reportedApplied!=frame.applied||reportedUpdated!=driver.updated){
            reportedShown=frame.shown;reportedApplied=frame.applied;reportedUpdated=driver.updated
            onState(reportedShown,reportedApplied,reportedUpdated)
        }
        if(driver.moving&&windowVisibility==VISIBLE)postInvalidateOnAnimation()else lastNanos=0
    }
    override fun onTouchEvent(e:MotionEvent):Boolean {
        val x=e.x/density;val y=e.y/density
        when(e.actionMasked){
            MotionEvent.ACTION_DOWN->{
                x0=x;y0=y;failed=false;down=true
                target=if(y>=layout.top+44&&y<=layout.eyebrowTop)5 else if(y>=layout.chipsTop-6&&y<=layout.chipsTop+58)(0..4).firstOrNull{abs(x-layout.chip(it.toDouble()))<=32}?:-1 else if(y>=layout.ctaTop&&y<=layout.ctaTop+56)6 else -1
                tracking=VelocityTracker.obtain();tracking?.addMovement(e)
                if(target==5){driver.down();postDelayed(held,380)}
                invalidate()
            }
            MotionEvent.ACTION_MOVE->{
                tracking?.addMovement(e);tracking?.computeCurrentVelocity(1000)
                val dx=x-x0;val dy=y-y0
                if(hypot(dx,dy)>14)removeCallbacks(held)
                if(target==5){
                    if(!driver.dragging&&!driver.holding&&abs(dy)>24)failed=true
                    if(!driver.dragging&&!driver.holding&&!failed&&abs(dx)>10){driver.startDrag(dx,layout.heroS);removeCallbacks(held)}
                    if(driver.dragging)driver.drag(dx,(tracking?.xVelocity?:0f)/density,layout.heroS)
                };invalidate()
            }
            MotionEvent.ACTION_UP,MotionEvent.ACTION_CANCEL->{
                down=false;removeCallbacks(held);tracking?.addMovement(e);tracking?.computeCurrentVelocity(1000)
                if(target==5)driver.up((tracking?.xVelocity?:0f)/density,layout.heroS,e.actionMasked==MotionEvent.ACTION_CANCEL)
                else if(e.actionMasked==MotionEvent.ACTION_UP&&hypot(x-x0,y-y0)<16){if(target in 0..4)driver.pick(target)else if(target==6)driver.apply();performClick()}
                tracking?.recycle();tracking=null;invalidate()
            }
        }
        return true
    }
    override fun performClick():Boolean {super.performClick();return true}
}
