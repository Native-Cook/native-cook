package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion as M
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingMotion.GL
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme.Layout

/** Stateless counterpart of ato-dust.ts: 768 ambient + two families of 384 streamed motes. */
internal class AuroraTeammatesOnboardingDust {
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG).apply{xfermode=PorterDuffXfermode(PorterDuff.Mode.ADD)}
    private fun hash(x:Double,y:Double):Double {val s=sin(x*127.1+y*311.7+1.7)*43758.5453;return s-floor(s)}
    private fun bez(a:Double,c:Double,b:Double,t:Double):Double {val s=1-t;return a*s*s+c*2*s*t+b*t*t}
    fun draw(c:Canvas,l:Layout,sc:DoubleArray){
        val time=sc[GL.phase]/(2*PI)*9;val cx=sc[GL.coreX];val cy=sc[GL.coreY];val flare=sc[GL.flare];val finale=sc[GL.sinkMix]>0
        val homeX=if(finale)sc[GL.sinkX]else cx;val homeY=if(finale)sc[GL.sinkY]else cy
        val right=doubleArrayOf(.62*sc[GL.w0]+.86*sc[GL.w1]+.62*sc[GL.w2],sc[GL.w0]+.66*sc[GL.w1]+.9*sc[GL.w2],.78*sc[GL.w0]+sc[GL.w1]+sc[GL.w2])
        for(i in 0 until 1536){val fi=i.toDouble();var x=cx;var y=cy;var size=0.0;var alpha=0.0;var tint=.5;var white=.4;var masked=true
            if(i<768){
                val k=2+floor(hash(fi,3.17)*3);val life=9/k;val s=time/life+hash(fi,1.31);val cycle=floor(s)%k;val ph=s-floor(s)
                val r1=hash(fi,cycle+.5);val r2=hash(fi+17,cycle);val r3=hash(fi+41,cycle);val r4=hash(fi+73,cycle)
                val x0=r1*2-1;val theta=x0*1.1+(r2-.5)*.4;val age=ph*life;val speed=(18+46*r3)*(1+.8*flare)
                x=cx+x0*l.half*.85+sin(theta)*speed*age+sin(age*1.7+fi)*5-sc[GL.tiltX]*10*(.35+.9*r4)
                y=cy+6-cos(theta)*speed*age-sc[GL.tiltY]*7*(.35+.9*r4)
                alpha=M.smooth(0.0,.14,ph)*(1-M.smooth(.35,.9,ph))*(.06+.34*r4*r4)*(1+1.6*flare)
                size=(.45+1.1*r4.pow(3));tint=x0*.5+.5;white=.6
            } else {
                val arriving=i>=1152;val j=if(arriving)i-1152 else i-768;val obj=j/64;val jf=j.toDouble()
                val page=if(arriving)sc[GL.to].toInt()else sc[GL.from].toInt()
                if(!finale&&(sc[GL.u]<=0||obj>=M.count(page)))continue
                val o=(if(finale)7+obj else M.first(page)+obj)*M.STRIDE
                var ox=sc[o];var oy=sc[o+1];var radius=max(max(sc[o+6],sc[o+7]),18.0)*sc[o+4];var prog=sc[o+(if(arriving)12 else 11)]
                if(finale&&arriving){val ang=PI*(.1+.8*obj/5);ox=homeX+cos(ang)*l.w*.4;oy=homeY-sin(ang)*l.s*70;radius=26*l.s;prog=M.clamp((sc[GL.burst]-.05*abs(obj-2.5))/.8)}
                val t=if(arriving)M.clamp(prog*1.35-hash(jf,21.9)*.35)else M.clamp((prog-hash(jf,5.3)*.45)/.55)
                if(radius<=0||t<=0||t>=1)continue
                val a1=hash(jf,if(arriving)23.3 else 7.1)*2*PI;val rr=sqrt(hash(jf,if(arriving)25.1 else 9.7));val reach=if(arriving).85 else .9
                val gx=ox+cos(a1)*radius*reach*rr;val gy=oy+sin(a1)*.6*radius*reach*rr
                val side=(hash(jf,if(arriving)27.7 else 11.3)-.5)*(if(arriving).8 else .9)*hypot(gx-homeX,gy-homeY)
                val mx=(gx+homeX)/2+side;val my=(gy+homeY)/2-(if(arriving)60.0 else if(finale)0.0 else 40.0)
                val ease=if(arriving)1-(1-t).pow(3)else t*t
                if(arriving){x=bez(homeX,mx,gx,ease);y=bez(homeY,my,gy,ease)}else{x=bez(gx,mx,homeX,ease);y=bez(gy,my,homeY,ease)}
                masked=!finale
                alpha=if(arriving)sin(PI*t).pow(.8)*(.4+.6*hash(jf,29.3))else sin(PI*t).pow(.7)*(.35+.55*hash(jf,13.1))*(if(finale)1.7 else 1.0)
                size=if(arriving)(.6+1.7*hash(jf,31.9)) else (.6+1.5*hash(jf,15.7))*(if(finale)1.6 else 1.0)
                tint=hash(jf,if(arriving)4.4 else 2.2);white=if(arriving).35 else .4
            }
            if(masked){val xn=abs(x-cx)/max(l.half,1.0);val edge=sc[GL.sheetTop]+(if(xn<1)sc[GL.dip]*(1-M.smooth(.2,1.0,xn))else 0.0);alpha*=1-M.smooth(-6.0,0.0,y-edge)}
            if(alpha<.003||size<=0)continue
            val side=M.clamp(tint)
            val red=M.mix(if(i<768).94 else 1.0,M.mix(.64,right[0],side),white)
            val green=M.mix(if(i<768).98 else 1.0,M.mix(.72,right[1],side),white)
            val blue=M.mix(1.0,M.mix(1.0,right[2],side),white)
            paint.color=Color.rgb((red*255).toInt(),(green*255).toInt(),(blue*255).toInt());paint.alpha=(M.clamp(alpha*.16)*255).toInt();c.drawCircle(x.toFloat(),y.toFloat(),(size*2.1).toFloat(),paint)
            paint.alpha=(M.clamp(alpha)*255).toInt();c.drawCircle(x.toFloat(),y.toFloat(),(size*.62).toFloat(),paint)
        }
    }
}
