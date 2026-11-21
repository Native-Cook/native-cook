package com.ngocdevv.nativecook.blocks.charts.focusinhometiles
import kotlin.math.*

internal class FocusInHomeTilesEngine{
    private val m=FocusInHomeTilesMotion
    private class Channel(var value:Double){private var from=0.0;private var target=0.0;private var start=0.0;private var duration=0.0;private var velocity=0.0;private var cubic=true;private var busy=false
        fun set(x:Double){value=x;busy=false}
        fun timing(to:Double,now:Double,duration:Double,reduced:Boolean,cubic:Boolean=true){if(reduced){set(to);return};from=value;target=to;start=now;this.duration=duration;this.cubic=cubic;busy=true}
        fun spring(to:Double,now:Double,v:Double,reduced:Boolean){if(reduced){set(to);return};from=value;target=to;start=now;duration=0.0;velocity=v;busy=true}
        fun advance(now:Double){if(!busy)return;val m=FocusInHomeTilesMotion;val t=now-start;if(duration>0){val k=m.seg(t,0.0,duration);val e=if(cubic)m.easeInOut(k)else if(k<.5)2*k*k else 1-(-2*k+2).pow(2)/2;value=m.mix(from,target,e);if(k>=1)set(target)}else{value=m.springAt(t,from,target,velocity,30.5/4,322.3/4);if(t>3&&abs(value-target)<.001)set(target)}}
    }
    var autoPlay=true;var reduced=false;var manual=false;var pin:Double?=null;var viewportHeight=874.0
    var onHaptic:((Int)->Unit)?=null
    var frame=m.frameAt(0.0,874.0);private set
    var clock=0.0;private set
    private var auto=true;private var now=0.0;private var phase=0.0;private var touching=false;private var lastTouch=0.0;private var mode=0;private var moved=false;private var downAt=0.0
    private val s=Channel(0.0);private val fp=Channel(2.0);private val budget=Channel(120.0);private val alert=Channel(1.0);private val dark=Channel(0.0);private val lx=Channel(292.0);private val ly=Channel(690.0)
    private var alertOn=true;private var startS=0.0;private var startB=120.0;private var downX=0.0;private var downY=0.0
    fun configure(autoPlay:Boolean,reduced:Boolean){if(this.autoPlay!=autoPlay||this.reduced!=reduced)auto=autoPlay&&!reduced&&!manual;this.autoPlay=autoPlay;this.reduced=reduced;if(manual||reduced)auto=false;publish()}
    fun viewport(height:Double){viewportHeight=height;if(!touching&&dark.value==0.0)ly.set(690-max(0.0,874-height));publish()}
    private fun publish(){clock=if(auto||pin!=null)pin?:phase else now;frame=if(auto||pin!=null)m.frameAt(pin?:phase,viewportHeight)else m.liveFrameAt(FocusInHomeTilesMotion.Live(s.value,fp.value,budget.value,alert.value,dark.value,lx.value,ly.value,if(reduced)1.0 else 0.0))}
    fun advance(dt:Double){now+=dt;if(pin!=null)return;if(auto){phase=(phase+dt)%10;publish();return};listOf(s,fp,budget,alert,dark,lx,ly).forEach{it.advance(now)}
        if(!reduced&&autoPlay&&!manual&&!touching&&now-lastTouch>2){val page=floor(s.value/780+.5).toInt().coerceIn(0,2);val focus=listOf(2.0,1.0,2.0)[page];val settled=abs(s.value-page*780)<.5&&abs(fp.value-focus)<.02&&abs(budget.value-120)<.5&&dark.value<.002
            if(!settled){s.timing(page*780.0,now,.6,false);fp.timing(focus,now,.6,false);budget.timing(120.0,now,.6,false);dark.timing(0.0,now,.6,false);alertOn=true;alert.timing(1.0,now,.2,false,false);lastTouch=now-1.3}else{phase=m.PAGE_REST_T[page];auto=true}
        };publish()
    }
    private fun takeOver(){if(auto||pin!=null){val f=frame;s.set(f.s);fp.set(f.fp);budget.set(f.budget);alert.set(f.alert);alertOn=f.alert>.5;lx.set(f.ox);ly.set(f.oy);val d=if(f.front<1.9){if(f.dir>0)m.clamp01(1-(f.front+.22)/1.54)else m.clamp01((f.front+.22)/1.54)}else 0.0;dark.set(d);if(d>0)dark.timing(0.0,now,.9,false,false);auto=false};pin=null}
    fun begin(x:Double,y:Double){touching=true;takeOver();moved=false;downAt=now;downX=x;downY=y;startS=s.value;startB=budget.value;mode=0;lastTouch=now;if(reduced)return
        val st=84-s.value;if(x>=318&&x<=378&&y>=st-8&&y<=st+97){mode=2;lx.set(frame.ox);ly.set(frame.oy);dark.timing(1.0,now,1.0,false);lx.timing(x,now,.7,false,false);ly.timing(y,now,.7,false,false);onHaptic?.invoke(0);return}
        if(s.value>780*1.9&&abs(x-frame.ox)<frame.ow/2+12&&abs(y-frame.oy)<frame.oh/2+16)mode=1
    }
    private fun setBudget(b:Double){val v=b.coerceIn(40.0,120.0);budget.set(v);if(alertOn&&v<99){alertOn=false;alert.timing(0.0,now,.2,false,false);onHaptic?.invoke(1)}else if(!alertOn&&v>101){alertOn=true;alert.timing(1.0,now,.2,false,false);onHaptic?.invoke(1)}}
    fun move(x:Double,y:Double){val ty=y-downY;if(abs(ty)>6||abs(x-downX)>6)moved=true;if(mode==2){lx.set(x);ly.set(y);return};if(mode==1){setBudget(startB-ty*120/222);return};if(!moved)return;var v=startS-ty;if(v<0)v=-60*(1-exp(v/60))else if(v>1560)v=1560+60*(1-exp(-(v-1560)/60));s.set(v);publish()}
    fun end(x:Double,y:Double,velocityY:Double,cancelled:Boolean=false){touching=false;lastTouch=now
        if(mode==2){dark.timing(0.0,now,1.1,false);return};if(mode==1)return
        if(!moved&&now-downAt<.35){if(!cancelled&&!reduced)for(g in m.GROUPS.indices){val group=m.GROUPS[g];val r=group.rect;val top=r[1]+group.page*780-s.value;if(x>=r[0]&&x<=r[0]+r[2]&&y>=top&&y<=top+r[3]){fp.timing(group.plane,now,.55,false);onHaptic?.invoke(0);break}};return}
        val v=if(cancelled)0.0 else -velocityY;val target=floor((s.value+v*.18)/780+.5).toInt().coerceIn(0,2);val old=floor(startS/780+.5).toInt();if(old!=target)onHaptic?.invoke(2);s.spring(target*780.0,now,v,reduced);if(!reduced&&old!=target)fp.timing(listOf(2.0,1.0,2.0)[target],now,.55,false)
    }
    fun page(delta:Int){takeOver();val p=(floor(s.value/780+.5).toInt()+delta).coerceIn(0,2);s.timing(p*780.0,now,.6,reduced);fp.timing(listOf(2.0,1.0,2.0)[p],now,.55,reduced);lastTouch=now}
    fun focus(g:Int){takeOver();fp.timing(m.GROUPS[g].plane,now,.55,reduced);lastTouch=now}
}
