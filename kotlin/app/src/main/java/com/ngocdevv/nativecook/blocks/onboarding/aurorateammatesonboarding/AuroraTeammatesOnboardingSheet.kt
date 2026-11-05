package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import android.graphics.Paint
import android.graphics.Path
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.GL
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.clamp
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.mix
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.smooth
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.spring
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme.Layout

internal class AuroraTeammatesOnboardingSheet(l:Layout,paint:Paint) {
    val widths=DoubleArray(8)
    private val xs=Array(8){DoubleArray(3){-1.0}}
    private val ys=Array(8){DoubleArray(3){-1.0}}
    private val lines=Array(8){IntArray(3)}
    init {
        for(i in 0..7)widths[i]=paint.measureText(AuroraTeammatesOnboardingTheme.tokens[i]).toDouble()
        AuroraTeammatesOnboardingTheme.lines.forEachIndexed { p,page->page.forEachIndexed { line,tokens->
            val str=tokens.joinToString(" "){AuroraTeammatesOnboardingTheme.tokens[it]};val left=l.cx-paint.measureText(str)/2
            var prefix=""
            for(i in tokens){xs[i][p]=left+(if(prefix.isEmpty())0.0 else paint.measureText(prefix).toDouble());ys[i][p]=l.titleTop+l.titleCap+line*l.titleStep;lines[i][p]=line;prefix+=AuroraTeammatesOnboardingTheme.tokens[i]+" "}
        }}
    }
    fun token(i:Int,sc:DoubleArray,reduced:Boolean,out:DoubleArray) {
        val from=sc[GL.from].toInt();val to=sc[GL.to].toInt();val u=sc[GL.u];val top=sc[GL.sheetTop];val a=xs[i][from]>=0;val b=xs[i][to]>=0
        if(u<=0||(!a&&!b)){out[0]=if(a)xs[i][from]else 0.0;out[1]=if(a)top+ys[i][from]else 0.0;out[2]=if(a&&u<=0)1.0 else 0.0;out[3]=0.0}
        else if(a&&b){val g=if(reduced)smooth(0.0,1.0,u)else spring(clamp((u-.16)/.6),.8,10.0);out[0]=mix(xs[i][from],xs[i][to],g);out[1]=top+mix(ys[i][from],ys[i][to],g);out[2]=1.0;out[3]=0.0}
        else if(a){val q=if(reduced)smooth(0.0,.45,u)else smooth(0.0,.22,u-.03*lines[i][from]);out[0]=xs[i][from];out[1]=top+ys[i][from]-(if(reduced)0.0 else 10*q);out[2]=1-q;out[3]=if(reduced)0.0 else 7*q}
        else {val q=if(reduced)smooth(.5,1.0,u)else clamp((u-.26-.07*lines[i][to])/.36);val e=if(reduced)q else spring(q,.8,10.0);out[0]=xs[i][to];out[1]=top+ys[i][to]+(if(reduced)0.0 else 12*(1-e));out[2]=if(reduced)q else smooth(0.0,.7,q);out[3]=if(reduced)0.0 else 7*(1-smooth(0.0,.8,q))}
    }
    companion object {
        fun path(top:Double,dip:Double,cx:Double,l:Layout,closed:Boolean):Path=Path().apply {
            val r=l.corner
            if(closed){moveTo(0f,(l.h+2).toFloat());lineTo(0f,(top+r).toFloat())}else moveTo(0f,(top+r).toFloat())
            quadTo(0f,top.toFloat(),r.toFloat(),top.toFloat());lineTo((cx-l.half).toFloat(),top.toFloat())
            cubicTo((cx-l.half+l.c1).toFloat(),top.toFloat(),(cx-l.c2).toFloat(),(top+dip).toFloat(),cx.toFloat(),(top+dip).toFloat())
            cubicTo((cx+l.c2).toFloat(),(top+dip).toFloat(),(cx+l.half-l.c1).toFloat(),top.toFloat(),(cx+l.half).toFloat(),top.toFloat())
            lineTo((l.w-r).toFloat(),top.toFloat());quadTo(l.w.toFloat(),top.toFloat(),l.w.toFloat(),(top+r).toFloat())
            if(closed){lineTo(l.w.toFloat(),(l.h+2).toFloat());close()}
        }
        fun pill(w:Double,h:Double,amp:Double,ph:Double):Path=Path().apply {
            val r=h/2;val ls=w-h;val per=2*ls+2*PI*r
            for(k in 0..159){val s=k/160.0*per;var x:Double;var y:Double;var nx=0.0;var ny=0.0
                if(s<ls){x=-ls/2+s;y=-r;ny=-1.0}
                else if(s<ls+PI*r){val a=-PI/2+(s-ls)/r;nx=cos(a);ny=sin(a);x=ls/2+r*nx;y=r*ny}
                else if(s<2*ls+PI*r){x=ls/2-(s-ls-PI*r);y=r;ny=1.0}
                else {val a=PI/2+(s-2*ls-PI*r)/r;nx=cos(a);ny=sin(a);x=-ls/2+r*nx;y=r*ny}
                val t=2*PI*s/per;val off=amp*(.62*sin(3*t+ph)+.38*sin(5*t-1.3*ph));x+=nx*off;y+=ny*off
                if(k==0)moveTo(x.toFloat(),y.toFloat())else lineTo(x.toFloat(),y.toFloat())
            };close()
        }
    }
}
