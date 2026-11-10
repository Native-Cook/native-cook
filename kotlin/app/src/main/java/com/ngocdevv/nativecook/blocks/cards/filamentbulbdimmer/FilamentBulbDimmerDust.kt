package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import kotlin.math.*

internal class FilamentBulbDimmerDust {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private var layout:FilamentBulbDimmerTheme.Layout?=null
    private var homes=emptyArray<DoubleArray>()
    private var history=Array(48){doubleArrayOf(0.0,0.0)}
    private fun prepare(l:FilamentBulbDimmerTheme.Layout) {
        if(layout==l)return;layout=l
        var seed=1234567.0
        fun rnd():Double {seed=(seed*1103515245+12345)%4294967296.0;seed=(seed.toLong() and 0x7fffffff).toDouble();return seed/2147483647}
        fun gauss()=sqrt(-2*ln(max(1e-6,rnd())))*cos(2*PI*rnd())
        val fx=l.anchorX;val fy=l.restY+2.55*l.r;val zl=2.4*l.r
        homes=Array(600){i->val x:Double;val y:Double;if(i%10<7){x=fx+gauss()*2.4*l.r;y=fy+gauss()*2.2*l.r}else{x=rnd()*l.w;y=l.anchorY-20+rnd()*(l.cy-l.anchorY+20)};val z=zl+l.r*(-2.2+7.5*rnd().pow(1.6));val size=.35+.85*rnd().pow(3);doubleArrayOf(x,y,z,size,rnd(),rnd(),rnd(),rnd())}
        history=Array(48){doubleArrayOf(fx,fy)}
    }
    fun draw(c:Canvas,l:FilamentBulbDimmerTheme.Layout,f:FilamentBulbDimmerMotion.Frame) {
        prepare(l);val pose=l.pose(f);for(i in 47 downTo 1)history[i]=history[i-1];history[0]=doubleArrayOf(pose[2],pose[3])
        val t=f.time;val r=l.r;val zl=2.4*r;val lc=FilamentBulbDimmerTheme.light(f.heat,f.colour);val lum=FilamentBulbDimmerMotion.lum(f.heat)
        for(m in homes){val n1=1+floor(m[6]*2.99);val n2=1+floor(m[7]*2.99)
            var x=m[0]+12*sin(2*PI*(n1*t/10+m[4]))+5*sin(2*PI*(2*t/10+m[5]));var y=m[1]+9*sin(2*PI*(n2*t/10+m[5]))-4*cos(2*PI*(t/10+m[6]));val z=m[2]+.35*r*sin(2*PI*(t/10+m[7]))
            var ox=0.0;var oy=0.0
            for(k in 0..46){val hx=history[k][0];val hy=history[k][1];val vx=(hx-history[k+1][0])*60;val vy=(hy-history[k+1][1])*60;val dx=x-hx;val dy=y-hy;val g=exp(-(dx*dx+dy*dy)/(1.7*r).pow(2))*exp(-((z-zl)/(2.4*r)).pow(2));val w=exp(-k/16.0)/60;val curl=sign(dx*vy-dy*vx);ox+=(vx-vy*curl*.5)*w*g;oy+=(vy+vx*curl*.5)*w*g}
            x+=ox*1.15;y+=oy*1.15;val dx=x-pose[2];val dy=y-pose[3];val dist=max(1.0,sqrt(dx*dx+dy*dy+(z-zl).pow(2)));if(dist<1.08*r)continue
            val ct=(z-zl)/dist;val gg=.55;val hg=(1-gg*gg)/(1+gg*gg-2*gg*ct).pow(1.5)*(1-gg).pow(2)/(1+gg)
            val bright=lum*(.18+1.6*hg)/(1+(dist/(2.3*r)).pow(2));val glint=(.5+.5*sin(2*PI*((1+floor(m[4]*3.99))*t/10+m[7]*7))).pow(24);val coc=abs(z-zl)/r*1.5;val radius=m[3]+coc;val energy=(m[3]/radius).pow(2);val gain=bright*(1+2.5*glint)*energy*2.4
            paint.color=Color.argb((gain*160).roundToInt().coerceIn(0,255),(lc[0].pow(1/2.2)*255).toInt(),(lc[1].pow(1/2.2)*255).toInt(),(lc[2].pow(1/2.2)*255).toInt());c.drawCircle(x.toFloat(),y.toFloat(),radius.toFloat(),paint)
        }
    }
}
