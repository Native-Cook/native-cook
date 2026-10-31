package com.ngocdevv.nativecook.blocks.modals.glasstilesfuse

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.*


internal class GlassTilesFuseDriver {
    enum class Key { LINK,PRESS,DRAG_X,DRAG_Y,LIFT,PULL,PULL_Y,RECOIL,STICK,FINGER }
    enum class Curve { DEFAULT_TIMING,OUT,QUAD_OUT,IN_OUT,CONNECT,SPRING }
    data class Step(val from:Double,val to:Double,val start:Double,val duration:Double,val curve:Curve,val zeta:Double=0.8,val velocity:Double=0.0) {
        private val spring=if(curve==Curve.SPRING) GlassTilesFuseSpring(from,to,duration,zeta,velocity) else null
        val end=start+duration*(if(spring==null) 1.0 else 1.5)
        fun springVelocity(now:Double)=spring?.sample(now-start)?.second?:0.0
        fun value(now:Double):Double {
            spring?.let { return it.sample(now-start).first }
            val t=GlassTilesFuseMotion.clamp01((now-start)/duration)
            if(t<=0) return from
            if(t>=1) return to
            val f=when(curve) {
                Curve.DEFAULT_TIMING->if(t<0.5) 2*t*t else 1-(-2*t+2).pow(2)/2
                Curve.OUT->GlassTilesFuseMotion.easeOut(t)
                Curve.QUAD_OUT->1-(1-t).pow(2)
                Curve.IN_OUT->GlassTilesFuseMotion.easeInOut(t)
                Curve.CONNECT->bezier(t)
                Curve.SPRING->t // Handled by the cached spring above.
            }
            return GlassTilesFuseMotion.mix(from,to,f)
        }
    }
    data class Frame(val link:Double=0.0,val press:Double=0.0,val dragX:Double=0.0,val dragY:Double=0.0,val lift:Double=0.0,
        val pull:Double=0.0,val pullY:Double=0.0,val recoil:Double=0.0,val stick:Double=1.0,val finger:Double=0.0,val fingerOn:Int=0,val restTime:Double=0.0)
    var time by mutableStateOf(0.0)
        private set
    var revision by mutableStateOf(0)
        private set
    private var manual=false
    private var holding=false
    private var pulling=false
    private var lastTouch = -100.0
    private var fingerOn=0
    private var lastBand=0
    private val base=mutableMapOf(Key.STICK to 1.0)
    private val plans=mutableMapOf<Key,List<Step>>()
    var buzz:(Int)->Unit={}
    fun value(key:Key,t:Double=time):Double {
        var v=base[key]?:0.0
        for(s in plans[key].orEmpty()) { if(t<s.start) break; v=s.value(t) }
        return v
    }
    private fun set(key:Key,v:Double) { base[key]=v; plans.remove(key) }
    private fun animate(key:Key,to:Double,duration:Double,curve:Curve=Curve.DEFAULT_TIMING,delay:Double=0.0,zeta:Double=0.8,velocity:Double=0.0) {
        val inherited=if(curve==Curve.SPRING) plans[key]?.lastOrNull()?.springVelocity(time)?:0.0 else 0.0
        plans[key]=listOf(Step(value(key),to,time+delay,duration,curve,zeta,velocity+inherited))
    }
    private fun spring(key:Key,duration:Double,zeta:Double,velocity:Double=0.0,to:Double=0.0)=animate(key,to,duration,Curve.SPRING,zeta=zeta,velocity=velocity)
    fun frame(auto:Boolean,reduced:Boolean):Frame {
        val t=time
        val resumed=manual&&value(Key.LIFT)<0.01&&t-lastTouch>3.2&&auto&&!reduced
        if(auto&&!reduced&&(!manual||resumed)) {
            val lt=(if(resumed) t-lastTouch-3.2 else t)%GlassTilesFuseMotion.LOOP
            val fg=GlassTilesFuseMotion.fingerAt(lt)
            return Frame(link=GlassTilesFuseMotion.linkAt(lt),press=max(GlassTilesFuseMotion.pressAt(lt,GlassTilesFuseMotion.T.PRESS),GlassTilesFuseMotion.pressAt(lt,GlassTilesFuseMotion.T.DONE)),pull=GlassTilesFuseMotion.pullAt(lt),
                recoil=GlassTilesFuseMotion.recoilAt(lt),stick=GlassTilesFuseMotion.stickAt(lt),finger=fg.first,fingerOn=fg.second,restTime=t%GlassTilesFuseMotion.LOOP)
        }
        var f=Frame(value(Key.LINK),value(Key.PRESS),value(Key.DRAG_X),value(Key.DRAG_Y),value(Key.LIFT),value(Key.PULL),
            value(Key.PULL_Y),value(Key.RECOIL),value(Key.STICK),value(Key.FINGER),fingerOn,t%GlassTilesFuseMotion.LOOP)
        if(auto&&!reduced&&manual&&value(Key.LIFT)<0.01&&t-lastTouch>2) {
            val a=GlassTilesFuseMotion.clamp01((t-lastTouch-2)/1.1)
            f=f.copy(link=GlassTilesFuseMotion.mix(value(Key.LINK,lastTouch+2),0.0,bezier(a)),stick=1.0,
                dragX=GlassTilesFuseSpring(value(Key.DRAG_X,lastTouch+2),0.0,0.5,0.8,0.0).sample(t-lastTouch-2).first,
                dragY=GlassTilesFuseSpring(value(Key.DRAG_Y,lastTouch+2),0.0,0.5,0.8,0.0).sample(t-lastTouch-2).first)
        }
        return f
    }
    fun needsFrames(auto:Boolean,reduced:Boolean):Boolean = (auto&&!reduced)||holding||plans.values.any { steps->steps.lastOrNull()?.let { time<it.end }?:false }
    fun tick(dt:Double,auto:Boolean,reduced:Boolean) {
        time+=dt
        val k=frame(auto,reduced).link; val band=if(k>0.86) 2 else if(k>0.4) 1 else 0
        val automatic=auto&&!reduced&&(!manual||(value(Key.LIFT)<0.01&&time-lastTouch>3.2))
        if(!automatic&&band>lastBand) buzz(if(band==2) 2 else 0)
        lastBand=band
    }
    fun takeOver(auto:Boolean,reduced:Boolean) {
        val f=frame(auto,reduced)
        if(!manual||(auto&&time-lastTouch>3.2&&value(Key.LIFT)<0.01)) {
            for((k,v) in listOf(Key.LINK to f.link,Key.PRESS to f.press,Key.DRAG_X to f.dragX,Key.DRAG_Y to f.dragY,Key.LIFT to f.lift,
                Key.PULL to f.pull,Key.PULL_Y to f.pullY,Key.RECOIL to f.recoil,Key.STICK to f.stick,Key.FINGER to f.finger)) set(k,v)
            fingerOn=f.fingerOn
            animate(Key.FINGER,0.0,0.15)
        }
        manual=true; lastTouch=time; revision++
    }
    fun press(down:Boolean) { animate(Key.PRESS,if(down) 1.0 else 0.0,if(down) 0.1 else 0.18); revision++ }
    fun button(auto:Boolean,reduced:Boolean) {
        takeOver(auto,reduced)
        val k=value(Key.LINK)
        if(reduced) { set(Key.LINK,if(k>0.5) 0.0 else 1.0); revision++; return }
        set(Key.STICK,1.0)
        if(k>0.5) animate(Key.LINK,0.0,1.15,Curve.CONNECT)
        else {
            buzz(0)
            plans[Key.LINK]=listOf(Step(k,0.5,time,1.6,Curve.CONNECT),Step(0.5,1.0,time+2.5,0.9,Curve.IN_OUT))
        }
        revision++
    }
    fun begin(auto:Boolean,reduced:Boolean) {
        takeOver(auto,reduced); holding=true
        val k=value(Key.LINK)
        if(!reduced&&k>0.4&&k<0.6&&value(Key.STICK)>0.99) { animate(Key.LINK,0.5,0.2); pulling=true }
        else if(k<=0.02) spring(Key.LIFT,0.26,0.8,to=1.0)
        revision++
    }
    fun drag(x:Double,y:Double,vx:Double) {
        lastTouch=time
        if(pulling) {
            val px=if(x>0) x else 0.25*x
            set(Key.PULL,px); set(Key.PULL_Y,0.4*y); set(Key.RECOIL,GlassTilesFuseMotion.TENSION*max(0.0,px))
            if(hypot(GlassTilesFuseMotion.FUSED_W+GlassTilesFuseMotion.FUSED_G+px-value(Key.RECOIL),value(Key.PULL_Y))>GlassTilesFuseMotion.SNAP_DIST) {
                pulling=false; animate(Key.STICK,0.0,0.14,Curve.QUAD_OUT); spring(Key.RECOIL,0.65,0.35)
                animate(Key.LINK,1.0,0.8,Curve.OUT); spring(Key.PULL,0.8,0.9,vx); spring(Key.PULL_Y,0.6,0.8); buzz(1)
            }
        } else if(value(Key.LINK)<=0.02) { set(Key.DRAG_X,x); set(Key.DRAG_Y,y) }
        revision++
    }
    fun end(vx:Double,vy:Double) {
        lastTouch=time; holding=false
        if(pulling) {
            pulling=false; spring(Key.PULL,0.6,0.55,vx); spring(Key.PULL_Y,0.6,0.55); spring(Key.RECOIL,0.6,0.55)
            animate(Key.LINK,1.0,0.9,Curve.IN_OUT,delay=0.9)
        } else {
            spring(Key.LIFT,0.42,0.8); spring(Key.DRAG_X,0.52,0.62,vx); spring(Key.DRAG_Y,0.52,0.62,vy)
        }
        revision++
    }
    companion object {
        fun bezier(x:Double):Double {
            fun at(t:Double,a:Double,b:Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
            var lo=0.0; var hi=1.0
            repeat(24) { val mid=(lo+hi)/2; if(at(mid,0.77,0.175)<x) lo=mid else hi=mid }
            return at((lo+hi)/2,0.0,1.0)
        }
    }
}
