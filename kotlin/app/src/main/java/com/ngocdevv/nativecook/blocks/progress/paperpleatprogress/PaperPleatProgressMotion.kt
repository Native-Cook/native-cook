package com.ngocdevv.nativecook.blocks.progress.paperpleatprogress

import kotlin.math.*

/** Seconds; mirrors the committed RN clock, including its bounded live delta. */
internal class PaperPleatProgressMotion {
    data class Frame(var p: Double=0.0,var sw: Double=0.0,var t: Double=0.0,var boost: Double=1.0,
                     var fade: Double=1.0,var lift: Double=1.0,var pct: Double=0.0,var done: Boolean=false)
    companion object {
        const val LOOP = 7.0
        fun seg(t: Double,a: Double,b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
        fun io(x: Double) = if(x < .5) 2*x*x else 1-(-2*x+2).pow(2)/2
        fun open(t: Double): Double = when { t < .4 -> 0.0; t < 4.4 -> io(seg(t,.4,4.4)); t < 5.4 -> 1.0; t < 6.5 -> 1-io(seg(t,5.4,6.5)); else -> 0.0 }
        fun frame(time: Double): Frame { val t=time%LOOP; val p=open(t); val done=t>=4.4 && t<5.4
            return Frame(p=p,sw=if(done) sin(seg(t,4.4,5.4)*PI) else 0.0,t=seg(t,4.4,5.4),pct=p,done=done) }
    }
    var mode=0; var last=-1.0; var start=-1.0; var time=0.0; var fold=-1.0; var p0=0.0; var sw0=0.0
    var pulse=-1.0; var touches=0; var idle=0.0; var watching=false; var still=0.0; var shown=false; var dones=0
    var out=Frame()
    val settled get()=mode==2 && still>=5.4
    fun setStill(enabled: Boolean) { if(enabled && mode!=2) { mode=2; still=0.0; shown=false }
        else if(!enabled && mode==2) { mode=0; start=-1.0 } }
    fun touchDown(sculpture: Boolean) {
        watching=true
        if(mode==2) { shown=shown || still>=.4; still=0.0; return }
        if(mode==0) { mode=1; time=if(start<0) 0.0 else (last-start)%LOOP; fold=-1.0 }
        touches++; idle=0.0
        if(sculpture && fold<0 && time>.4 && time<4.4) pulse=0.0
    }
    fun touchUp() { touches=max(0,touches-1); idle=0.0 }
    fun step(now: Double,autoPlay: Boolean) {
        val dt=if(last<0) 0.0 else (now-last).coerceIn(0.0,.05); last=now
        if(mode==2) {
            val before=still; still+=dt
            if(before<4.4 && still>=4.4 && watching) { dones++; watching=false }
            out=Frame(p=1.0,fade=if(shown) 1.0 else seg(still,0.0,.4),
                lift=1+(if(still>=4.4) .1*sin(seg(still,4.4,5.4)*PI) else 0.0),pct=open(min(still,4.4)),done=still>=4.4); return
        }
        if(mode==0) {
            if(!autoPlay) { mode=1; time=0.0 }
            else { if(start<0) start=now; val before=out.done; out=frame(now-start)
                if(out.done && !before && watching) { dones++; watching=false }; return }
        }
        val f=frame(time)
        if(pulse>=0) { pulse+=dt; if(pulse>=.3) pulse=-1.0 }
        if(fold>=0) {
            fold=min(1.1,fold+dt); val k=1-io(fold/1.1); f.p=p0*k; f.sw=sw0*k; f.pct=p0*k; f.done=false
            if(fold>=1.1) { mode=0; start=now; out=frame(0.0); return }
        }
        f.boost=if(pulse>=0) 1+.4*sin(PI*pulse/.3) else 1.0; out=f
        if(touches>0 || fold>=0) { idle=0.0; return }; idle+=dt
        if(!autoPlay || idle<2) return; fold=0.0; p0=f.p; sw0=f.sw
    }
}
