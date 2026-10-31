package com.ngocdevv.nativecook.blocks.cards.glasspiggysavings

import kotlin.math.*

internal object GlassPiggySavingsMotion {
    const val LOOP=7.0
    const val SCALE=340.0/900
    val CHIPS=intArrayOf(1,5,20)
    fun clamp(x:Double)=x.coerceIn(0.0,1.0)
    fun seg(t:Double,a:Double,b:Double)=clamp((t-a)/(b-a))
    fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
    fun smooth(k:Double):Double { val x=clamp(k); return x*x*(3-2*x) }
    fun easeOut(k:Double):Double { val x=1-clamp(k); return 1-x*x*x }
    fun easeIn(k:Double)=clamp(k).pow(3)
    fun easeInOut(k:Double):Double { val x=clamp(k); return if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun back(k:Double,c:Double=1.7):Double { val x=clamp(k)-1; return 1+(c+1)*x*x*x+c*x*x }
    fun hash(i:Double):Double { val s=sin(i*12.9898+78.233)*43758.5453; return s-floor(s) }
    fun total(t:Double)=if(t>=6.6) 1236.0 else 1236+20*easeInOut(seg(t,2.52,3.3))
    class Pig {
        var sq=0.0; var sv=0.0; var wob=0.0; var wv=0.0
        fun step(dt:Double) {
            val n=max(1,ceil(dt/(1.0/240)).toInt()); val h=dt/n
            repeat(n) {
                sv+=(-sq*330-sv*9)*h; sq+=sv*h
                wv+=(-wob*260-wv*7)*h; wob+=wv*h
            }
        }
    }
    class Held {
        var on=false; var x=0.0; var y=0.0; var chip=2; var dropAt = -10.0
        var fromX=0.0; var fromY=0.0; var backAt = -10.0
    }
    data class Layout(val w:Double,val h:Double,val top:Double,val bottom:Double) {
        val pigX get()=w/2-170+6
        val pigY get()=top+250
        val slotX get()=pigX+378*SCALE
        val slotY get()=pigY+116*SCALE
        val chipY get()=h-bottom-92
        fun chipX(i:Int)=w/2+(i-1)*112
    }
    class Frame {
        var t=0.0; var now=0.0; val pig=Pig(); val held=Held()
        var bonus=0; var bonusAt = -10.0; var plus=20
        val total get()=GlassPiggySavingsMotion.total(t)+bonus
        val dip get()=sin(seg(t,6.35,6.85)*PI)
        val floatK:Double get() {
            val a=if(t>=2.5 && t<3.6) seg(t,2.5,3.6) else -1.0
            val fb=now-bonusAt; val b=if(fb>=0 && fb<1.1) fb/1.1 else -1.0
            return max(a,b)
        }
        fun receipt():Pair<Double,Double> {
            val a=if(t>=2.6 && t<4.9) t-2.5-0.1 else -1.0
            val fb=now-bonusAt-0.05; val b=if(fb>=0 && fb<2.3) fb else -1.0
            val k=max(a,b); if(k<0) return 0.0 to 90.0
            val out=easeIn((k-1.9)/0.4)
            return (1-out) to ((1-back(k/0.35))*90+out*40)
        }
        fun dot(i:Int):Double {
            var s=1.0
            if(i==2) {
                if(t>=0.9 && t<2.5) s=0.0
                else if(t>=2.5) s=back(seg(t,2.75,3.05))
            }
            if(held.chip==i) {
                if(held.on) s=0.0
                else {
                    val since=now-max(held.dropAt+0.23,held.backAt+0.45)
                    if(since > -0.5 && since<0.35) s=if(since<0) 0.0 else min(s,back(since/0.3))
                }
            }
            return max(0.0,s)
        }
    }
    data class Coin(var x:Double=0.0,var y:Double=0.0,var alpha:Double=0.0,var flip:Double=1.0,var radius:Double=30.0,var clipped:Boolean=false)
    fun coin(f:Frame,l:Layout):Coin {
        val t=f.t; val now=f.now; val hd=f.held; val c=Coin()
        if(hd.on) { c.x=hd.x; c.y=hd.y; c.alpha=1.0; c.flip=cos(now*9) }
        else if(hd.dropAt>0 && now-hd.dropAt<0.3) {
            val k=easeIn(seg(now-hd.dropAt,0.0,0.23))
            c.x=mix(hd.fromX,l.slotX,min(1.0,k*1.6)); c.y=mix(hd.fromY,l.slotY+30,k)
            c.radius=mix(30.0,23.0,k); c.alpha=1.0; c.clipped=true
        } else if(hd.backAt>0 && now-hd.backAt<0.45) {
            val k=back(seg(now-hd.backAt,0.0,0.45))
            c.x=mix(hd.fromX,l.chipX(hd.chip),k); c.y=mix(hd.fromY,l.chipY,k)
            c.alpha=1-smooth(seg(now-hd.backAt,0.3,0.45)); c.flip=cos((now-hd.backAt)*14)
        } else if(t>=0.9 && t<2.5) {
            val pop=back(seg(t,0.9,1.06)); val k=seg(t,1.0,1.8)
            val rise=easeOut(seg(k,0.0,0.62)); val fall=easeIn(seg(k,0.62,1.0))
            c.x=mix(l.chipX(2),l.slotX,easeInOut(k))
            c.y=mix(l.chipY-18*pop,l.slotY-118,rise)+fall*78+back(seg(t,1.8,2.0),1.2)*34+easeIn(seg(t,2.32,2.5))*44
            c.radius=mix(30.0,23.0,min(1.0,k*1.3)); c.alpha=min(1.0,pop*1.4)
            c.flip=if(t<1.8) cos(k*PI*6) else 1.0; c.clipped=t>=1.8
        }
        return c
    }
}
