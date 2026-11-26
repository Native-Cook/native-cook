package com.ngocdevv.nativecook.blocks.media.jellyfishsleepplayer

import kotlin.math.*

/** The RN clocks, in seconds, independent of rendering and display density. */
internal class JellyfishSleepPlayerMotion {
    data class Frame(val t: Double=0.0,val m: Double=0.0,val c: Double=0.0,val a: Int=0,val b: Int=0,
        val k: Double=0.0,val wave: Double=1.0,val lit: Double=1.0,val prog: Double=0.12,val playing: Boolean=true)
    data class Track(val a: Int,val b: Int,val k: Double,val age: Double)
    companion object {
        const val LOOP=8.0
        fun unit(x: Double)=x.coerceIn(0.0,1.0)
        fun mod(x: Double,n: Double)=x-n*floor(x/n)
        fun ease(x: Double)=if(x<0.5) 2*x*x else 1-(-2*x+2).pow(2)/2
        fun pulse(p: Double): Double {
            val f=mod(p,2.0)/2
            return if(f<0.22) sin(f/0.22*PI/2).pow(2) else cos((f-0.22)/0.78*PI/2).pow(2)
        }
        fun track(t: Double): Track {
            val segments=arrayOf(doubleArrayOf(0.0,2.4,0.0,0.0),doubleArrayOf(2.4,3.2,0.0,1.0),
                doubleArrayOf(3.2,5.0,1.0,1.0),doubleArrayOf(5.0,5.8,1.0,2.0),
                doubleArrayOf(5.8,7.2,2.0,2.0),doubleArrayOf(7.2,8.0,2.0,0.0))
            for(s in segments) if(t>=s[0] && t<s[1]) return Track(s[2].toInt(),s[3].toInt(),if(s[2]==s[3]) 0.0 else ease(unit((t-s[0])/(s[1]-s[0]))),t-s[0])
            return Track(0,0,0.0,0.0)
        }
        fun progress(k: Double,age: Double)=if(k>0) 0.0 else min(1.0,0.12+age/30)
        fun frame(time: Double,waveFrom: Double=0.0,motesFrom: Double=0.0): Frame {
            val t=mod(time,8.0); val tr=track(t)
            return Frame(waveFrom+t,motesFrom+t,pulse(t),tr.a,tr.b,tr.k,prog=progress(tr.k,mod(t-doubleArrayOf(0.0,3.2,5.8)[tr.a]+8,8.0)))
        }
    }
    var automatic=true; var last=-1.0; var start=-1.0; var waveFrom=0.0; var motesFrom=0.0
    var still=false; var p=0.0; var t=0.0; var m=0.0; var amp=1.0; var speed=1.0; var dim=0.0
    var playing=true; var a=0; var b=0; var age=-1.0; var pending=0; var inFor=0.0; var touches=0; var idle=0.0
    var out=Frame(); private set
    private fun takeOver() {
        if(!automatic) return
        val clock=if(start<0) 0.0 else mod(last-start,8.0); val tr=track(clock)
        automatic=false; p=clock; t=waveFrom+clock; m=motesFrom+clock
        a=tr.a; b=tr.b; age=if(tr.a==tr.b) -1.0 else tr.age
        inFor=mod(clock-doubleArrayOf(0.0,3.2,5.8)[tr.a]+8,8.0)
    }
    val changeTime get()=if(still) 0.3 else 0.8
    fun changeTrack(dir: Int): Int {
        takeOver(); idle=0.0
        if(age>=0) { pending+=dir; return (b+pending%3+3)%3 }
        b=(a+dir+3)%3; age=0.0; return b
    }
    fun togglePlay() { takeOver(); idle=0.0; playing=!playing }
    fun touchDown() { takeOver(); touches++; idle=0.0 }
    fun touchUp() { touches=max(0,touches-1); idle=0.0 }
    fun setReduced(value: Boolean) {
        if(value==still) return
        still=value
        if(value) takeOver() else if(!automatic) idle=2.0
    }
    fun step(now: Double,autoPlay: Boolean) {
        val dt=if(last<0) 0.0 else (now-last).coerceIn(0.0,0.05); last=now
        if(automatic) {
            if(autoPlay && !still) {
                if(start<0) start=now
                out=frame(mod(now-start,8.0),waveFrom,motesFrom); return
            }; takeOver()
        }
        val dir=if(playing) 1.0 else -1.0
        amp=unit(amp+dir*dt/0.6); speed=unit(speed+dir*dt); dim=unit(dim-dir*dt/0.6)
        val beforeP=p
        if(playing || amp>0) p+=dt
        t+=dt*speed; m+=dt
        if(playing) inFor+=dt
        if(age>=0) {
            age+=dt
            if(age>=changeTime) {
                a=b; age=-1.0; inFor=0.0
                val next=(a+pending%3+3)%3; pending=0
                if(next!=a) { b=next; age=0.0 }
            }
        }
        val k=if(age>=0) ease(unit(age/changeTime)) else 0.0
        out=Frame(t,if(still) 0.0 else m,if(still) 0.0 else pulse(p)*amp,a,if(age>=0) b else a,k,
            if(still) 0.0 else 1.0,(1-0.15*dim)*(if(still) 1+0.08*sin(2*PI*m/4) else 1.0),progress(k,inFor),playing)
        if(touches>0) { idle=0.0; return }; idle+=dt
        if(!autoPlay || still || idle<2) return
        playing=true
        if(age<0 && a!=0) { b=0; age=0.0 }
        if(!(age<0 && a==0 && amp>=1 && speed>=1 && dim<=0 && floor(p/2)!=floor(beforeP/2))) return
        val over=mod(p,2.0)
        automatic=true; start=now-over; waveFrom=t-over; motesFrom=m-over
        out=frame(over,waveFrom,motesFrom)
    }
}
