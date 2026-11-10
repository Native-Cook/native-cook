package com.ngocdevv.nativecook.blocks.onboarding.passkeyglassseal

import kotlin.math.*

internal object PasskeyGlassSealMotion {
    const val LOOP = 10.0
    const val SCAN = 2.5
    const val HOLD = 1.4
    const val FLIP_ZETA = 0.68
    val FLIP_OMEGA = 2*PI*1.25
    fun nearestFace(f:Double)=floor(f+0.5)
    fun faceOf(f:Double)=abs(nearestFace(f)).toInt()%2
    fun flipSpring(t:Double):Double {
        if(t<=0)return 0.0
        val z=FLIP_ZETA;val w=FLIP_OMEGA;val wd=w*sqrt(1-z*z)
        return 1-exp(-z*w*t)*(cos(wd*t)+(z*w/wd)*sin(wd*t))
    }
    const val LIGHT=0; const val UNWIND=1; const val KEY=2; const val TURN=3
    const val FLIP=4; const val SCAN_WEIGHT=5; const val FINGER=6; const val PRESS=7
    fun seg(t:Double,a:Double,b:Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun easeInOut(x:Double)=if(x<0.5)4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeOut(x:Double)=1-(1-x).pow(3)
    fun turnAt(x:Double)=if(x<=0)0.0 else if(x>=1)1.0 else 1-exp(-6*x)*cos(9*x)*(1-x)
    fun at(t:Double):DoubleArray {
        val finger=easeOut(seg(t,0.8,1.0))*(1-easeOut(seg(t,2.75,2.93)))
        return doubleArrayOf(seg(t,1.2,2.6)*(1-easeInOut(seg(t,8.6,9.5))),
            easeInOut(seg(t,2.7,3.9))*(1-easeInOut(seg(t,8.2,9.4))),
            easeOut(seg(t,3.7,4.3))*(1-easeInOut(seg(t,8.2,8.6))),
            turnAt(seg(t,4.4,5.0))*(1-easeInOut(seg(t,8.1,8.4))),
            flipSpring(t-5.2)-flipSpring(t-7.6),
            (1-easeOut(seg(t,1.0,1.3))+easeOut(seg(t,9.2,9.5))).coerceIn(0.0,1.0),finger,finger)
    }
    fun status(f:DoubleArray)=if(faceOf(f[FLIP])==1)"Unlocked" else if(f[UNWIND]>0.02)"Verified" else if(f[LIGHT]>0.02)"Hold still…" else "Scanning…"
    fun bezier(p:Double,x1:Double,y1:Double,x2:Double,y2:Double):Double {
        if(p<=0)return 0.0; if(p>=1)return 1.0
        fun cubic(t:Double,a:Double,b:Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo=0.0; var hi=1.0
        repeat(24){val m=(lo+hi)/2;if(cubic(m,x1,x2)<p)lo=m else hi=m}
        return cubic((lo+hi)/2,y1,y2)
    }
    class Animation(val from:Double,val to:Double,val start:Double,val duration:Double,val curve:Int=0) {
        fun value(now:Double):Double {
            val p=seg(now,start,start+max(0.000001,duration))
            val q=when(curve){1->easeOut(p);2->bezier(p,0.77,0.0,0.175,1.0);3->p;else->if(p<0.5)2*p*p else 1-(-2*p+2).pow(2)/2}
            return from+(to-from)*q
        }
    }
    class FlipSpring(val from:Double,val to:Double,val start:Double,val velocity:Double=0.0,val normalise:Boolean=false) {
        data class State(val position:Double,val velocity:Double,val finished:Boolean)
        fun state(now:Double):State {
            val t=max(0.0,now-start);val w=FLIP_OMEGA;val decay=FLIP_ZETA*w;val wd=w*sqrt(1-FLIP_ZETA*FLIP_ZETA)
            val a=from-to;val b=(velocity+decay*a)/wd;val wave=a*cos(wd*t)+b*sin(wd*t);val e=exp(-decay*t)
            val x=e*wave;val v=e*((-a*wd*sin(wd*t)+b*wd*cos(wd*t))-decay*wave)
            val initialEnergy=velocity*velocity+w*w*a*a
            val finished=now>=start&&(initialEnergy==0.0||(v*v+w*w*x*x)/initialEnergy<6e-9)
            return State(if(finished)to else to+x,if(finished)0.0 else v,finished)
        }
    }
}
