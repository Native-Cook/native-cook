package com.ngocdevv.nativecook.blocks.cards.tumblecardroutine

import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.repeatOnLifecycle
import kotlin.math.*

internal fun tcrRound(value:Double)=floor(value+0.5)

internal object TumbleCardRoutineMotion {
    const val LOOP = 9.0
    private val throwStarts = doubleArrayOf(0.8, 6.1, 7.7)
    fun seg(t:Double,a:Double,b:Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun out(v:Double)=1-(1-v).pow(3)
    fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
    fun bezier(x1:Double,y1:Double,x2:Double,y2:Double,k:Double):Double {
        var lo=0.0; var hi=1.0
        repeat(30) { val u=(lo+hi)/2; val x=3*(1-u)*(1-u)*u*x1+3*(1-u)*u*u*x2+u*u*u; if(x<k)lo=u else hi=u }
        val u=(lo+hi)/2; return 3*(1-u)*(1-u)*u*y1+3*(1-u)*u*u*y2+u*u*u
    }
    fun spring(t:Double,from:Double,to:Double,v:Double,d:Double,k:Double):Double {
        if(t<=0)return from
        val w=sqrt(k); val z=d/(2*w); val wd=w*sqrt(1-z*z); val x=from-to
        return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))
    }
    private val peak=(0..999).maxOf { spring(it/1000.0,0.0,0.0,1.0,15.1,93.4) }
    fun pos(t0:Double):Pair<Double,Double> {
        val t=((t0%LOOP)+LOOP)%LOOP
        val p=when {
            t<.8 -> 0.0
            t<1.35 -> bezier(.05,.7,.1,1.0,seg(t,.8,1.35))
            t<3.45 -> 1.0
            t<4.15 -> 1+mix(.22,.55,.5-.5*cos(PI*seg(t,3.45,4.15)))
            t<4.3 -> 1.55
            t<4.58 -> 1+mix(.55,1.0,bezier(.55,0.0,.25,1.0,seg(t,4.3,4.58)))
            t<6.1 -> 2.0
            t<6.65 -> 2+bezier(.05,.7,.1,1.0,seg(t,6.1,6.65))
            t<7.7 -> 3.0
            t<8.25 -> 3+bezier(.05,.7,.1,1.0,seg(t,7.7,8.25))
            else -> 4.0
        }
        val peek=if(t>=2.9 && t<4.58) if(t<3.2)out(seg(t,2.9,3.2)) else 1.0 else 0.0
        return p to peek
    }
    fun bump(t:Double):Pair<Int,Double> {
        val lands=doubleArrayOf(1.15,4.45,6.45,8.05)
        for(i in lands.indices) { val tau=((t-lands[i])%LOOP+LOOP)%LOOP; if(tau<1.4)return (i+1)%4 to spring(tau,0.0,0.0,1.0,15.1,93.4)/peak }
        return -1 to 0.0
    }
    fun drop(t:Double):Double {
        val starts=doubleArrayOf(1.35,4.58,6.65,8.25); val ends=doubleArrayOf(2.9,6.1,7.7,.8)
        for(i in starts.indices) { val tau=((t-starts[i])%LOOP+LOOP)%LOOP; val span=((ends[i]-starts[i])%LOOP+LOOP)%LOOP; if(tau<span)return max(0.0,spring(tau,182.0,0.0,0.0,22.1,157.9)) }
        return 0.0
    }

    fun reveal(time:Double,index:Int):Double {
        val starts=doubleArrayOf(8.05,1.25,4.45,6.45); val left=doubleArrayOf(1.35,4.58,6.65,8.25)
        val age=((time-starts[index])%LOOP+LOOP)%LOOP; val span=((left[index]-starts[index])%LOOP+LOOP)%LOOP
        return if(age<=span) age.coerceAtMost(.8) else 0.0
    }
    fun anticipation(t: Double): Double {
        for (start in throwStarts) {
            if (t >= start - 0.1 && t < start + 0.2) {
                return out(seg(t, start - 0.1, start)) * (1 - out(seg(t, start, start + 0.2)))
            }
        }
        return 0.0
    }
}

internal data class TumbleCardRoutineCard(val name:String,val w:Float,val h:Float,val r:Float,val x:Float,val y:Float,val tilt:Float,val bump:Float,val shadow:Int)
internal val TumbleCardRoutineCards=listOf(
    TumbleCardRoutineCard("Food",250f,286f,14f,163f,342f,-5f,1.8f,0xffc2410c.toInt()),
    TumbleCardRoutineCard("Water",228f,290f,20f,255f,367f,11f,2.2f,0xff12302d.toInt()),
    TumbleCardRoutineCard("Weight",222f,270f,20f,205f,362f,-6f,1.8f,0xff0b0c0e.toInt()),
    TumbleCardRoutineCard("Activity",300f,300f,16f,201f,359f,0f,2f,0xff2a251b.toInt()))
internal fun TumbleCardRoutineIndex(i:Int)=((i%4)+4)%4
internal class TumbleCardRoutineAssets(private val context:android.content.Context) {
    private val root="tumblecardroutine/tumblecardroutine_"
    val regular=Typeface.createFromAsset(context.assets,root+"Geist-Regular.ttf")
    val semibold=Typeface.createFromAsset(context.assets,root+"Geist-SemiBold.ttf")
    fun bitmap(name:String):Bitmap=context.assets.open(root+name+".jpg").use { BitmapFactory.decodeStream(it)!! }
    val photos=listOf(null,bitmap("photo_water"),null,bitmap("photo_activity")); val avatar=bitmap("avatar")
    fun openIcon(name: String) = context.assets.open("$root$name.svg")
}

@Composable
fun TumbleCardRoutine(autoPlay:Boolean=false) {
    val context=LocalContext.current; val view=LocalView.current; val lifecycle=LocalLifecycleOwner.current.lifecycle
    val assets=remember(context) { TumbleCardRoutineAssets(context) }
    val renderer = remember(assets) { TumbleCardRoutineRenderer(assets) }
    var reduced by remember { mutableStateOf(Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f) }
    DisposableEffect(context) { val o=object:ContentObserver(Handler(Looper.getMainLooper())) { override fun onChange(selfChange:Boolean) { reduced=Settings.Global.getFloat(context.contentResolver,Settings.Global.ANIMATOR_DURATION_SCALE,1f)==0f } }; context.contentResolver.registerContentObserver(Settings.Global.getUriFor(Settings.Global.ANIMATOR_DURATION_SCALE),false,o); onDispose { context.contentResolver.unregisterContentObserver(o) } }
    val activity=LocalActivity.current
    val debug=(context.applicationInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE)!=0
    val manual=remember { debug && activity?.intent?.getBooleanExtra("tumbleCardRoutineManual",false)==true }
    val state=remember { TumbleCardRoutineState().apply { pin=if(debug) activity?.intent?.getStringExtra("tumbleCardRoutineTime")?.toDoubleOrNull() else null; pin?.let { clock=((it%9)+9)%9 } } }
    LaunchedEffect(autoPlay,reduced,lifecycle) { lifecycle.repeatOnLifecycle(Lifecycle.State.RESUMED) { var last=withFrameNanos { it }; while(true) { val now=withFrameNanos { it }; state.advance(((now-last)/1e9).coerceIn(0.0,.05),autoPlay&&!manual,reduced); last=now } } }
    val density=LocalDensity.current
    val top=WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val bottom=WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val scale=maxWidth.value/402f; val pixels=scale*density.density
        val topOff=top/scale-62f; val bottomOff=(maxHeight.value-bottom)/scale-840f
        val height = maxHeight.value / scale
        Canvas(Modifier.fillMaxSize().semantics { contentDescription="Today's routine. Swipe up or down to change cards." }.pointerInput(scale,bottomOff,reduced) {
            awaitEachGesture {
                val down=awaitFirstDown(false); val x=down.position.x/pixels; val y=down.position.y/pixels
                state.takeOver(); var initial=state.position; var origin=tcrRound(initial-.22*state.peek); var completed=false;
                try { var lastY=down.position.y; var lastTime=down.uptimeMillis; var velocity=0.0; var dragged=false
                val canPeek = !reduced && y in (515 + bottomOff)..(719 + bottomOff) && abs(x - 201) > 40
                var checkPeek = canPeek
                do {
                    val timeout = (down.uptimeMillis + 180 - lastTime).coerceAtLeast(1)
                    val event = if (checkPeek) {
                        val result = withTimeoutOrNull(timeout) { awaitPointerEvent() }
                        if (result == null) { state.raisePeek(); checkPeek = false; continue }
                        result
                    } else awaitPointerEvent()
                    val change=event.changes.firstOrNull { it.id==down.id } ?: break
                    val dy=(change.position.y-down.position.y)/pixels
                    if(abs(dy)>8 && !dragged) {
                        dragged=true; checkPeek=false; origin=tcrRound(state.position-.22*state.peek)
                        if(state.peek>.001) state.position=origin+.22*state.peek
                        initial=state.position
                    }
                    if(abs(change.position.x-down.position.x)/pixels>14 && !dragged) break
                    val dt=(change.uptimeMillis-lastTime).coerceAtLeast(1)
                    if (change.position.y != lastY || dt > 90) {
                        velocity=-(change.position.y-lastY).toDouble()/pixels/dt*1000/240
                    }
                    if(dragged && !reduced) { var next=initial-dy/240
                        val edge=next.coerceIn(origin-1,origin+1); val over=abs(next-edge)*240
                        if(next!=edge) next=edge+sign(next-edge)*(over/(1+over/120))/240
                        if(dy>6 && state.peek>0 && next<origin+.22*state.peek) state.lowerPeek()
                        state.position=next; state.rate=velocity; change.consume() }
                    else if(checkPeek && change.uptimeMillis-down.uptimeMillis>=180 && change.pressed) {
                        state.raisePeek(); checkPeek=false
                    }
                    lastY=change.position.y; lastTime=change.uptimeMillis
                    if (!change.pressed) { completed=true; break }
                } while(true)
                if(dragged) { val delta=if(reduced) if(lastY<down.position.y) 1 else -1 else 0; state.settle(if(!completed) origin else if(reduced)origin+delta else tcrRound(state.position+velocity*.14).coerceIn(origin-1,origin+1),if(completed)velocity else 0.0,reduced); view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                else if(y>540+bottomOff && abs(x-201)<38) { state.nod=1.5; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                else if(y<540+bottomOff) { state.press=1.0; view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
                if(!dragged) state.settle(tcrRound(state.position),0.0,reduced)
                } finally {
                    if(!completed) state.settle(origin,0.0,reduced)
                    state.release()
                }
            }
        }) {
            val c=drawContext.canvas.nativeCanvas; val save=c.save(); c.scale(pixels,pixels)
            renderer.draw(c, state, topOff, bottomOff, height, reduced)
            c.restoreToCount(save)
        }
    }
}

internal class TumbleCardRoutineState {
    var clock by mutableDoubleStateOf(0.0); var position by mutableDoubleStateOf(0.0); var peek by mutableDoubleStateOf(0.0)
    var active by mutableStateOf(false)
    var press by mutableDoubleStateOf(0.0); var nod by mutableDoubleStateOf(0.0)
    var pin:Double?=null
    var rate=0.0; var target=0.0; var touched=false; var idle=0.0
    var liveClock by mutableDoubleStateOf(0.0); var drop=0.0; var reveal=.8; var spin=0.0
    private var peekTarget=0.0; private var settling=false; private var springTime=0.0; private var springFrom=0.0; private var springVelocity=0.0
    fun takeOver() {
        if(!active) {
            val p=TumbleCardRoutineMotion.pos(clock); position=if(p.first>=4) p.first-4 else p.first; peek=p.second
            drop=TumbleCardRoutineMotion.drop(clock); reveal=TumbleCardRoutineMotion.reveal(clock,TumbleCardRoutineIndex(floor(position+1e-6).toInt()))
        }
        pin=null; active=true; touched=true; idle=0.0; target=position; settling=false; rate=0.0; peekTarget=peek
    }
    fun raisePeek() { peekTarget=1.0 }
    fun lowerPeek() { peekTarget=0.0 }
    fun settle(value:Double,velocity:Double,reduced:Boolean) {
        target=value
        if(reduced) { position=value; rate=0.0; settling=false; return }
        springFrom=position; springVelocity=velocity; springTime=0.0; rate=velocity; settling=true
    }
    fun release() { touched=false; idle=0.0; peekTarget=0.0 }
    fun advance(dt:Double,auto:Boolean,reduced:Boolean) {
        if(pin!=null) return
        liveClock+=dt
        if(!active) { if(auto&&!reduced)clock=(clock+dt)%TumbleCardRoutineMotion.LOOP; return }
        val step=dt/.3; peek+=(peekTarget-peek).coerceIn(-step,step)
        drop=max(0.0,drop-dt*700); reveal=(reveal+dt).coerceAtMost(.8)
        if(!touched) {
            idle+=dt
            if(reduced) { position=target; rate=0.0; peek=0.0; settling=false }
            else if(settling) {
                val before=floor(position+1e-6); springTime+=dt
                position=TumbleCardRoutineMotion.spring(springTime,springFrom,target,springVelocity,15.1,93.4)
                rate=(TumbleCardRoutineMotion.spring(springTime+.00001,springFrom,target,springVelocity,15.1,93.4)-position)/.00001
                if(floor(position+1e-6)>before) { peek=0.0; peekTarget=0.0; reveal=.8 }
                if(abs(position-target)<.0001&&abs(rate)<.001) { position=target; rate=0.0; settling=false }
            }
            if(idle>=2 && !settling && peek<.002 && drop<.5 && auto&&!reduced) {
                val rests=doubleArrayOf(.35,2.1,5.4,7.45); val i=TumbleCardRoutineIndex(tcrRound(target).toInt()); position=i.toDouble(); clock=rests[i]; active=false
            }
        }
        press=max(0.0,press-dt*5); nod=max(0.0,nod-dt*9)
    }
}
