package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

import kotlin.math.*
import com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding.AuroraTeammatesOnboardingTheme.Layout

/** Pure RN scene math. An integer position rests; its fraction scrubs every layer together. */
internal object AuroraTeammatesOnboardingMotion {
    const val LOOP = 9.0
    const val HOLD = 1.5
    const val TRANS = 1.5
    const val SEG = 3.0
    const val RESUME = 2.0
    const val STRIDE = 13
    const val G = 169
    object GL {
        const val phase=G; const val w0=G+1; const val w1=G+2; const val w2=G+3
        const val flare=G+4; const val coreX=G+5; const val coreY=G+6; const val sheetTop=G+7; const val dip=G+8
        const val dotsY=G+9; const val dotPos=G+10; const val dotStretch=G+11; const val buttonBottom=G+12
        const val sheetMix=G+13; const val from=G+14; const val to=G+15; const val u=G+16
        const val lightAngle=G+17; const val lightIntensity=G+18; const val fingerX=G+19; const val fingerY=G+20
        const val touch=G+21; const val touched=G+22; const val tiltX=G+23; const val tiltY=G+24
        const val dotDim=G+25; const val dotFill=G+26; const val dotFillA=G+27; const val fin=G+28
        const val finGlow=G+29; const val finPop=G+30; const val finCheck=G+31; const val finSheen=G+32
        const val sinkX=G+33; const val sinkY=G+34; const val sinkMix=G+35; const val burst=G+36
    }
    data class Input(var vx:Double=0.0,var x:Double=0.0,var y:Double=0.0,var down:Double=0.0,var drag:Double=0.0,var tiltX:Double=0.0,var tiltY:Double=0.0,var drift:Double=1.0,var holdQ:Double=0.0,var autoVis:Double=0.0,var fin:Double=0.0)
    fun clamp(x:Double)=x.coerceIn(0.0,1.0)
    fun mix(a:Double,b:Double,t:Double)=a+(b-a)*t
    fun smooth(a:Double,b:Double,x:Double):Double { val t=clamp((x-a)/(b-a));return t*t*(3-2*t) }
    fun spring(q:Double,zeta:Double,omega:Double):Double {
        if(q<=0)return 0.0;if(q>=1)return 1.0
        val wd=omega*sqrt(1-zeta*zeta);val e=exp(-zeta*omega*q)
        val s=1-e*(cos(wd*q)+zeta*omega/wd*sin(wd*q));val q8=q.pow(8)
        return s*(1-q8)+q8
    }
    fun wrap(p:Double)=((p%3)+3)%3
    fun posAt(t:Double):Double {val tt=((t%LOOP)+LOOP)%LOOP;val k=floor(tt/SEG);val tau=tt-k*SEG;return if(tau<HOLD)k else k+(tau-HOLD)/TRANS}
    fun clockAt(pos:Double):Double {val p=wrap(pos);val k=floor(p+1e-6);val u=p-k;return if(u<1e-4)k*SEG else k*SEG+HOLD+u*TRANS}
    fun dragToPos(dx:Double,w:Double)=-dx/(w*.85)
    fun page(i:Int)=if(i<2)0 else if(i<7)1 else 2
    fun count(p:Int)=if(p==0)2 else if(p==1)5 else 6
    fun first(p:Int)=if(p==0)0 else if(p==1)2 else 7
    private fun exitRank(i:Int)=if(i<2)if(i==0)0 else 1 else if(i<7)6-i else if(i>=11)12-i else if(i>=9)2+10-i else 4+8-i
    private fun enterRank(i:Int)=if(i<2)if(i==1)0 else 1 else if(i<7)i-2 else i-7
    private fun depth(i:Int)=if(i<2)if(i==1)1.0 else .72 else if(i<7)1-(1-(i-2)*.25)*.6 else .6+.2*((i-7)/2)
    fun compute(pos:Double,amb:Double,l:Layout,reduced:Boolean,f:Input,out:DoubleArray) {
        out.fill(0.0)
        val p=wrap(pos);var from=floor(p+1e-6).toInt();var u=p-from;if(u<1e-5)u=0.0;if(from>=3)from=0;val to=(from+1)%3
        val sq=if(reduced)smooth(0.0,1.0,u)else spring(clamp((u-.22)/.55),.78,10.0)
        val top=mix(l.tops[from],l.tops[to],sq)
        val flare=if(reduced)0.0 else exp(-((u-.34)/.14).pow(2))*smooth(0.0,.08,u)*(1-smooth(.92,1.0,u))
        val blend=smooth(.22,.78,u);val reach=l.w/2-l.half-l.corner-6;val pull=((f.x-l.cx)*.6).coerceIn(-reach,reach)
        val nx=if(reduced)l.cx else l.cx+pull*f.drag;val ny=top+l.depth+18*l.s
        val tx=if(reduced)0.0 else f.tiltX+f.drift*.45*sin(2*PI*amb/LOOP)
        val ty=if(reduced)0.0 else f.tiltY+f.drift*.3*sin(4*PI*amb/LOOP)
        val finOn=from==2&&u==0.0&&f.fin>0;var touched=-1
        if(f.down>.001&&u<.04)for(i in 0..12) {
            if(page(i)!=from)continue
            val r=AuroraTeammatesOnboardingTheme.rest[i];val hw=if(r.w>0)r.w*l.s/2 else 26*l.s;val hh=if(r.h>0)r.h*l.s/2 else 26*l.s
            val rot=-r.rot*PI/180;val dx=f.x-r.x*l.s;val dy=f.y-r.y*l.s
            if(abs(dx*cos(rot)-dy*sin(rot))<=hw&&abs(dx*sin(rot)+dy*cos(rot))<=hh)touched=i
        }
        for(i in 0..12) {
            val o=i*STRIDE;val r=AuroraTeammatesOnboardingTheme.rest[i];val page=page(i);val rx=r.x*l.s;val ry=r.y*l.s;val rr=r.rot*PI/180
            val ba=if(i<2)3.2 else if(i<7)1.4 else 1.8;val by=ba*l.s*(sin(2*PI*3*amb/LOOP+i*1.7)-sin(i*1.7))
            val sa=if(i<2)1.6 else .7;val sw=sa*PI/180*(sin(2*PI*2*amb/LOOP+i*2.3)-sin(i*2.3))
            var x=rx;var y=ry+by;var rot=rr+sw;var rotX=0.0;var scale=1.0;var alpha=r.alpha
            var hw=r.w*l.s/2;var hh=r.h*l.s/2;var content=1.0;var unfold=1.0;var exit=0.0;var enter=0.0
            if(page==from&&u>0) {
                if(reduced){alpha=r.alpha*(1-smooth(0.0,.45,u));content=alpha}
                else {
                    val q=clamp((u-.03*exitRank(i))/.26);exit=q;val path=q*q*(2.4*q-1.4);val down=max(0.0,path)
                    x=mix(rx,nx,path);y=mix(ry+by,ny,path);rot=(rr+sw)*(1-.7*down);rotX=62*PI/180*down;scale=mix(1.0,.45,down)
                    alpha=r.alpha*(1-smooth(.78,1.0,q));val fold=smooth(.05,.6,q);unfold=1-fold
                    hw=mix(r.w*l.s/2,20*l.s,fold);hh=mix(r.h*l.s/2,20*l.s,fold);content=1-smooth(0.0,.3,q)
                }
            } else if(page==to&&u>0) {
                if(reduced){alpha=r.alpha*smooth(.45,1.0,u);content=alpha}
                else {
                    val s0=.22+.055*enterRank(i);val q=clamp((u-s0)/min(.52,1-s0));enter=q;val m=spring(q,.74,11.0);val arc=sin(PI*clamp(m))
                    x=mix(nx,rx,m)+(rx-nx)*.18*arc;y=mix(ny,ry+by,m);rot=(rr+sw)*spring(q,.7,11.0)
                    rotX=72*PI/180*(1-spring(q,.52,12.0));scale=mix(.5,1.0,spring(q,.7,11.0));alpha=r.alpha*smooth(0.0,.12,q)
                    val fold=spring(clamp((q-.08)/.92),.78,10.0);unfold=fold
                    hw=mix(20*l.s,r.w*l.s/2,fold);hh=mix(20*l.s,r.h*l.s/2,fold);content=smooth(.42,.85,q)
                }
            } else if(page!=from){alpha=0.0;content=0.0;x=nx;y=ny;scale=.5;hw=20*l.s;hh=20*l.s;unfold=0.0}
            if(i==touched&&!reduced) {
                val lift=f.down*(1-smooth(0.0,.04,u));val ht=if(hw>0)hw else 40*l.s;val vt=if(hh>0)hh else 40*l.s
                rotX+=(clamp((f.y-y)/vt*.5+.5)-.5)*2*9*PI/180*lift;scale*=1+.07*lift;y-=9*l.s*lift
                out[o+9]=(clamp((f.x-x)/ht*.5+.5)-.5)*-2*11*PI/180*lift
            } else if(touched>=0&&page==from&&!reduced) {
                val back=f.down*(1-smooth(0.0,.04,u));scale*=1-.025*back;alpha*=1-.3*back;content*=1-.2*back
            }
            var drain=0.0
            if(finOn&&page==2&&!reduced){val q=clamp((f.fin-.05*(12-i))/.42);exit=q;drain=sin(PI*q);scale*=1-.035*drain;content*=1-.45*drain;alpha*=1-.12*drain;y+=4*l.s*drain}
            val dp=depth(i);x+=tx*7*l.s*dp;y+=ty*5*l.s*dp;rotX+=-ty*7*PI/180*dp
            out[o]=x;out[o+1]=y;out[o+2]=rot;out[o+3]=rotX;out[o+4]=scale;out[o+5]=alpha;out[o+6]=hw;out[o+7]=hh;out[o+8]=content
            val lean=if(reduced)0.0 else clamp(abs(f.vx)/1800)*if(f.vx<0)-1 else 1
            if(i!=touched)out[o+9]=lean*16*PI/180;out[o+9]+=tx*9*PI/180*dp
            val stretch=1-unfold;out[o+10]=if(reduced)4.0 else 4+46*stretch*(1-stretch)*4*(if(u>0)1 else 0)+36*drain
            out[o+11]=exit;out[o+12]=enter
        }
        out[GL.phase]=2*PI*amb/LOOP;out[GL.w0]=if(from==0)1-blend else if(to==0)blend else 0.0
        out[GL.w1]=if(from==1)1-blend else if(to==1)blend else 0.0;out[GL.w2]=if(from==2)1-blend else if(to==2)blend else 0.0
        out[GL.flare]=flare;out[GL.coreX]=nx;out[GL.coreY]=top+6*l.s;out[GL.sheetTop]=top
        val speed=clamp(abs(f.vx)/1500);out[GL.dip]=l.depth+8*l.s*flare+(if(reduced)0.0 else (4+10*speed)*l.s*max(0.0,f.drag))
        out[GL.dotsY]=top+mix(l.dotCentre[from],l.dotCentre[to],sq)
        val dq=if(reduced)smooth(0.0,1.0,u)else clamp((u-.3)/.45);val dm=if(reduced)dq else spring(dq,.8,11.0)
        out[GL.dotPos]=mix(from.toDouble(),to.toDouble(),dm);out[GL.dotStretch]=if(reduced)0.0 else sin(PI*dq)
        val av=if(reduced)0.0 else f.autoVis;out[GL.dotDim]=av*if(u>0)dm else 1.0;out[GL.dotFill]=if(u>0)1.0 else f.holdQ;out[GL.dotFillA]=if(u>0)0.0 else av*smooth(0.0,.06,f.holdQ)
        val bottom=mix(l.buttonBottom[from],l.buttonBottom[to],sq);out[GL.buttonBottom]=bottom;out[GL.sheetMix]=sq;out[GL.from]=from.toDouble();out[GL.to]=to.toDouble();out[GL.u]=u
        val toward=atan2(f.x-l.cx,-(f.y-l.h*.3))*180/PI;val delta=((((toward-180+540)%360)+360)%360)-180
        var angle=180+delta*f.down+40*tx;if(angle>180)angle-=360
        out[GL.lightAngle]=angle;out[GL.lightIntensity]=42+30*f.down+20*flare;out[GL.fingerX]=f.x;out[GL.fingerY]=f.y;out[GL.touch]=f.down;out[GL.touched]=touched.toDouble();out[GL.tiltX]=tx;out[GL.tiltY]=ty
        val page3=from==2&&f.fin>0;val fin=if(page3)f.fin else 0.0;val after=1-smooth(0.0,.3,u)
        out[GL.fin]=fin;out[GL.finGlow]=if(reduced)0.0 else (exp(-((fin-.5)/.13).pow(2))+.3*smooth(.5,.75,fin))*after
        out[GL.finPop]=if(reduced||!page3)0.0 else .055*exp(-((fin-.52)/.07).pow(2));out[GL.finCheck]=smooth(.46,.6,fin)*after
        out[GL.finSheen]=if(reduced)0.0 else clamp((fin-.5)/.3)*(if(u>0)0 else 1)
        out[GL.sinkX]=l.cx;out[GL.sinkY]=l.h-bottom-l.buttonH/2;out[GL.sinkMix]=if(finOn&&!reduced)1.0 else 0.0;out[GL.burst]=if(finOn&&!reduced)clamp((fin-.5)/.45)else 0.0
    }
}
