package com.ngocdevv.nativecook.blocks.cards.sunrisecocktailpour

import com.ngocdevv.nativecook.blocks.cards.sunrisecocktailpour.SunriseCocktailPourMotion as M
import kotlin.math.*

internal class SunriseCocktailPourDriver(auto: Boolean, private val buzz: (Boolean)->Unit) {
    var f=M.Frame()
    var now=0.0
    var running=false
    var stirring=false
    private var autoStart=0.0
    private var lastTouch=-100.0
    private var mode=if(auto) 0 else 1
    private var resumeAsked=false
    private var pouredAt=-1.0; private var stirredAt=-1.0
    private var servedAt: Double?=null
    private var shiftAt: Double?=null; private var shiftFrom=0.0
    private var blendAt: Double?=null; private var blendFrom=0.0
    private var fadeAt: Double?=null; private var fadeFrom=0.0
    private var redTween: SunriseCocktailPourTween?=null
    private var riseTween: SunriseCocktailPourTween?=null
    private var mixTween: SunriseCocktailPourTween?=null
    private var noteBefore=0; private var noteAfter=0; private var noteAt=-100.0
    private var noteFrom=doubleArrayOf(1.0,0.0,0.0)
    private fun setStep(step: Int) {
        if(min(2,step)!=min(2,f.step)) {
            val current=DoubleArray(3) { note(it,false).first }
            noteFrom=current;noteBefore=min(2,f.step);noteAfter=min(2,step);noteAt=now
        }
        f.step=step
    }
    private fun fresh() {
        setStep(0)
        f=M.Frame(phase=f.phase,shift=f.shift,finger=f.finger,fingerPress=f.fingerPress)
        pouredAt=-1.0; stirredAt=-1.0
        redTween=null; riseTween=null; mixTween=null
    }
    fun takeOver() {
        lastTouch=now; resumeAsked=false
        if(mode==0) {
            val t=(now-autoStart)%M.LOOP
            noteBefore=0; noteAfter=0; noteAt=-100.0
            for((boundary,before,after) in listOf(Triple(M.POUR_START,0,1),Triple(M.STIR_START,1,2),Triple(7.6,2,0))) if(t>boundary) {
                noteBefore=before;noteAfter=after;noteAt=now-t+boundary
            }
            noteFrom=DoubleArray(3) { if(it==noteBefore) 1.0 else 0.0 }
            mode=1; fadeAt=now; fadeFrom=f.finger; f.fingerPress=0.0
            val step=f.step
            if(abs(f.shift)>0.001) { shiftFrom=f.shift; shiftAt=now; if(f.shift>0) fresh() }
            if(step==1 && f.red<0.999) pouredAt=now-1.2
            if(step==2 && f.mix<0.999) stirredAt=now-0.4
            f.stream=0.0
        }
        running=true
    }
    private fun pour(reduced: Boolean) {
        if(f.step!=0) return
        setStep(1)
        if(reduced) {
            redTween=SunriseCocktailPourTween(f.red,now)
            riseTween=SunriseCocktailPourTween(f.rise,now)
        } else { pouredAt=now; buzz(false) }
    }
    private fun stir(reduced: Boolean) {
        if(f.step!=1) return
        setStep(2)
        if(reduced) mixTween=SunriseCocktailPourTween(f.mix,now) else { stirredAt=now; buzz(false) }
    }
    private fun serve(reduced: Boolean) {
        if(f.step!=2) return
        buzz(true)
        if(reduced) { setStep(0); fresh() } else { setStep(3); servedAt=now; shiftAt=null }
    }
    fun next(reduced: Boolean) {
        takeOver()
        when(f.step) { 0->pour(reduced); 1->stir(reduced); 2->serve(reduced) }
    }
    fun drag(dx: Double,dy: Double,vx: Double,scale: Double,reduced: Boolean) {
        lastTouch=now
        if(f.step==1 && hypot(dx,dy)>40) stir(reduced)
        if(f.step==2 && !reduced) f.swirl+=vx/(scale*60)*0.016*4
    }
    fun end() { stirring=false; lastTouch=now }
    fun note(i: Int,reduced: Boolean): Pair<Double,Double> {
        if(mode==0) {
            val t=(now-autoStart)%M.LOOP
            var before=0; var after=0; var p=1.0
            for((a,b,c) in listOf(Triple(M.POUR_START,0,1),Triple(M.STIR_START,1,2),Triple(7.6,2,0))) if(t>a) {
                before=b; after=c; p=M.cssOut(M.seg(t,a,a+0.26))
            }
            val start=if(before==i) 1.0 else 0.0; val target=if(after==i) 1.0 else 0.0
            val a=start+(target-start)*p
            return Pair(a,6*(1-a))
        }
        val p=M.cssOut(M.seg(now,noteAt,noteAt+0.26))
        val start=noteFrom[i]; val target=if(noteAfter==i) 1.0 else 0.0
        val a=start+(target-start)*p
        return Pair(a,if(reduced) 0.0 else 6*(1-a))
    }
    fun tick(dt: Double,auto: Boolean,reduced: Boolean): M.Frame {
        now+=dt
        if(mode==0 && auto) { f=M.at((now-autoStart)%M.LOOP); return f.copy() }
        if(!reduced) f.phase=(f.phase+M.SPEED*dt)%(2*PI)
        fadeAt?.let { a-> f.finger=fadeFrom*(1-M.defaultEase(M.seg(now,a,a+0.15))); if(now>=a+0.15) fadeAt=null }
        if(pouredAt>=0) { M.pour(now-pouredAt,f); if(f.rise>=1) pouredAt=-1.0 }
        if(stirredAt>=0) {
            val old=f.mix; M.stir(now-stirredAt,f); f.mix=max(old,f.mix)
            if(now-stirredAt>1.6) stirredAt=-1.0
        }
        redTween?.let { f.red=it.value(now); if(it.finished(now)) redTween=null }
        riseTween?.let { f.rise=it.value(now); if(it.finished(now)) riseTween=null }
        mixTween?.let { f.mix=it.value(now); if(it.finished(now)) mixTween=null }
        shiftAt?.let { a-> f.shift=shiftFrom*(1-M.defaultEase(M.seg(now,a,a+0.4))); if(now>=a+0.4) shiftAt=null }
        blendAt?.let { a->
            f.mix=blendFrom+(1-blendFrom)*M.defaultEase(M.seg(now,a,a+0.3))
            if(now>=a+0.3) { blendAt=null; setStep(2); serve(false) }
        }
        servedAt?.let { a->
            val tau=now-a
            if(tau<0.7) f.shift=-3.4*M.bezier(tau/0.7,0.77,0.0,0.175,1.0)
            else {
                if(f.step==3) { setStep(0); fresh() }
                f.shift=3.4*(1-M.cssOut(M.seg(tau,0.7,1.6)))
                if(tau>=1.6) { f.shift=0.0; servedAt=null }
            }
        }
        if(auto && !stirring && now-lastTouch>=2) {
            if(!resumeAsked) {
                resumeAsked=true
                if(f.step==2) serve(false) else if(f.step==1) { blendAt=now; blendFrom=f.mix }
            } else if(f.step==0 && abs(f.shift)<0.001 && pouredAt<0) {
                resumeAsked=false; autoStart=now; mode=0
            }
        }
        running=!reduced || redTween!=null || riseTween!=null || mixTween!=null || now<noteAt+0.26
        return f.copy()
    }
}

private data class SunriseCocktailPourTween(val from: Double,val at: Double) {
    fun value(now: Double)=from+(1-from)*M.defaultEase(M.seg(now,at,at+0.3))
    fun finished(now: Double)=now>=at+0.3
}
