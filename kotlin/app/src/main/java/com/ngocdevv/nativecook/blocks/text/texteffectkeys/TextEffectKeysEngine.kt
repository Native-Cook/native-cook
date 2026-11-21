package com.ngocdevv.nativecook.blocks.text.texteffectkeys
import kotlin.math.*

internal class TextEffectKeysEngine(var geo:TextEffectKeysMotion.Geo){
    private val m=TextEffectKeysMotion
    var autoPlay=true;var reduced=false;var manual=false;var pin:Double?=null
    var onHaptic:((Int)->Unit)?=null;var onChange:((Int)->Unit)?=null
    var frame=m.frameAt(0.0,geo);private set
    private var live=m.blankLive();private var auto=true;private var phase=0.0;private var now=0.0;private var n0=0.0;private var lastTouch=0.0
    private var downX=0.0;private var downY=0.0;private var travel=0.0
    fun configure(autoPlay:Boolean,reduced:Boolean){if(this.autoPlay!=autoPlay||this.reduced!=reduced)auto=autoPlay&&!reduced&&!manual;this.autoPlay=autoPlay;this.reduced=reduced;if(manual||reduced)auto=false;ensureLive();publish()}
    private fun ensureLive(){if(live.x.isEmpty())load(m.frameAt(0.0,geo),0.0)}
    private fun load(f:TextEffectKeysMotion.Frame,t:Double){val l=m.blankLive()
        for(i in 0..<geo.n.toInt()){val h=f.hero[i];val th=m.THROWS[i];l.x.add(h.x);l.y.add(h.y);l.vx.add(h.vx);l.vy.add(h.vy);l.rot.add(wrap(h.rot+180,360.0)-180);l.s.add(h.s);l.lime.add(h.lime);l.home.add(h.home);l.font.add(h.font);l.mode.add(if(t>=th.launch&&t<m.RESEAT+.035*i)2.0 else if(t>=3.23&&t<6.05)4.0 else 0.0);l.wait.add(0.0);l.rest.add(0.0);l.drag.add(.1)}
        l.target=if(t>=.28&&t<6.7)1.0 else 0.0;l.rise=f.rise;l.body=f.body;l.depth=f.depth.toMutableList();l.active=-1.0
        if(t>=3.23&&t<6.05)for(k in 0..7)if(f.active[k]>.5)l.active=k.toDouble();l.sendS=f.sendS;live=l;n0=now-t
    }
    private fun wrap(t:Double,span:Double)=((t%span)+span)%span
    private fun publish(){frame=if(auto||pin!=null)m.frameAt(pin?:phase,geo)else m.liveFrame(live,geo,now,n0,reduced);frame.still=if(reduced)1.0 else 0.0}
    fun advance(dt:Double){now+=dt;if(pin!=null)return;if(auto){phase=wrap(phase+dt,8.0);publish();return};ensureLive();val previous=live.hapticT;m.stepLive(live,min(dt,1/30.0),geo,now);if(live.hapticT!=previous)onHaptic?.invoke(0)
        val busy=live.mode.contains(2.0)||live.mode.contains(3.0);val staged=live.mode.contains(4.0)
        if(live.finger==0.0&&now-lastTouch>2&&!busy){
            if(live.target!=0.0||live.active>=0||staged){live.active=-1.0;live.target=0.0;for(i in live.mode.indices)if(live.mode[i]!=0.0){live.mode[i]=0.0;live.wait[i]=.045*i};onChange?.invoke(-1)}
            var rest=live.rise<.004&&live.body<.004&&live.held<0;for(i in live.x.indices)if(hypot(live.x[i]-geo.fieldX[i],live.y[i]-m.FIELD_BASE)>.4||abs(live.s[i]-1)>.01||live.home[i]>.02)rest=false
            val kp=wrap(now-n0,2.0)/2;if(rest&&autoPlay&&!manual&&!reduced&&(kp<.04||kp>.975)){phase=if(kp>.5)(kp-1)*2+8 else kp*2;auto=true}
        };publish()
    }
    private fun takeOver(){if(auto||pin!=null){val t=pin?:phase;load(m.frameAt(t,geo),t);auto=false};pin=null;lastTouch=now}
    private fun keyAt(x:Double,y:Double):Int?=(0..7).firstOrNull{val xx=listOf(15.0,206.0)[it%2];val yy=listOf(606.0,665.0,724.0,783.0)[it/2];x>=xx-4&&x<=xx+185&&y>=yy-5&&y<=yy+52}
    private fun isSend(x:Double,y:Double)=hypot(x-366.5,y-522.5)<22
    private fun press(k:Int){if(live.held==k.toDouble())return;live.held=k.toDouble();live.active=k.toDouble();for(i in live.mode.indices){live.mode[i]=4.0;live.wait[i]=0.0};live.returning=0.0;onChange?.invoke(k)}
    fun begin(x:Double,y:Double){takeOver();ensureLive();downX=x;downY=y;travel=0.0;live.fx=x;live.fy=y;live.fvx=0.0;live.fvy=0.0;val k=keyAt(x,y)
        if(isSend(x,y)){live.finger=3.0;live.sendS=.88;live.sendV=0.0}else if(k!=null){live.finger=2.0;press(k);onHaptic?.invoke(1)}else if(y<554&&!reduced){live.finger=1.0;for(i in live.mode.indices){live.mode[i]=1.0;live.wait[i]=0.0};live.active=-1.0;live.returning=0.0};publish()
    }
    fun move(x:Double,y:Double,vx:Double,vy:Double){live.fx=x;live.fy=y;live.fvx=vx;live.fvy=vy;travel=max(travel,hypot(x-downX,y-downY));val k=keyAt(x,y);if(live.finger==2.0&&k!=null&&live.held!=k.toDouble()){press(k);if(now-live.hapticT>=.1){live.hapticT=now;onHaptic?.invoke(0)}};lastTouch=now}
    private fun burst(){for(i in live.x.indices){val h=m.THROWS[i];live.drag[i]=h.ta/m.XK;live.vx[i]=(h.xa-live.x[i])/live.drag[i];live.vy[i]=-h.vy0;live.mode[i]=2.0;live.rest[i]=0.0};live.returning=0.0;onHaptic?.invoke(2)}
    fun end(x:Double,y:Double,cancelled:Boolean=false){
        if(live.finger==1.0){if(cancelled){for(i in live.mode.indices){live.mode[i]=0.0;live.wait[i]=.035*i}}else{val speed=hypot(live.fvx,live.fvy);val k=if(speed>1400)1400/speed else 1.0;for(i in live.mode.indices){live.mode[i]=2.0;live.drag[i]=.3;if(i==0){live.vx[i]=live.fvx*k;live.vy[i]=live.fvy*k}else{val v=hypot(live.vx[i],live.vy[i]);if(v>1400){live.vx[i]*=1400/v;live.vy[i]*=1400/v}}};if(speed>300)onHaptic?.invoke(2)}}
        else if(live.finger==3.0&&!cancelled&&travel<=10&&isSend(x,y)){if(live.target==0.0){live.target=1.0;for(i in live.mode.indices){live.mode[i]=0.0;live.wait[i]=.018*i;live.vx[i]=0.0;live.vy[i]=0.0};onHaptic?.invoke(3)}else if(!reduced)burst();live.sendV=6.0}
        live.held=-1.0;live.finger=0.0;lastTouch=now;publish()
    }
    fun choose(k:Int){begin(listOf(105.5,296.5)[k%2],listOf(627.5,686.5,745.5,804.5)[k/2]);end(0.0,0.0)}
    fun send(){begin(366.5,522.5);end(366.5,522.5)}
}
