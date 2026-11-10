package com.ngocdevv.nativecook.blocks.cards.filamentbulbdimmer

import kotlin.math.*

internal class FilamentBulbDimmerDriver(auto:Boolean) {
    private val m=FilamentBulbDimmerMotion
    var frame=FilamentBulbDimmerMotion.Frame()
    private var mode=if(auto)0 else 1
    private var now=0.0;private var start=0.0;private var lastTouch = -100.0;private var switchedAt = -100.0
    private var thV=0.0;private var sV=0.0;private var twV=0.0;private var held=false;private var clicked=false;private var resume=false
    private var holdX=0.0;private var holdY=0.0;private var grabX=0.0;private var grabY=0.0;private var across=0.0
    var target=0;private var lastStep=16
    private var maxTravel=0.0;private var scrubbing=false;private var touchStarted=0.0
    private val tweens=mutableMapOf<String,Tween>()
    private var togV=0.0;private var pctV=0.0;private var knobV=0.0;private var tog=1.0;private var pct=78.0;private var knob=0.0
    val moving get()=held || target>0 || abs(frame.heat-(if(frame.on==1.0)frame.level else 0.0))>.0001 || tweens.isNotEmpty()
    private data class Tween(val from:Double,val to:Double,val at:Double,val duration:Double,val out:Boolean=false) {
        fun value(t:Double):Double {val x=FilamentBulbDimmerMotion.seg(t,at,at+duration);val p=if(out)FilamentBulbDimmerMotion.bezier(x,.23,1.0,.32,1.0) else FilamentBulbDimmerMotion.bezier(x,.77,0.0,.175,1.0);return from+(to-from)*p}
    }
    private fun tween(key:String,from:Double,to:Double,duration:Double,out:Boolean=false) {tweens[key]=Tween(from,to,now,duration,out)}
    fun takeOver() {lastTouch=now;resume=false;if(mode==0){mode=1;switchedAt = -100.0;tween("finger",frame.finger,0.0,.15);frame.fingerPress=0.0}}
    fun toggle(sound:Boolean,buzz:(Int)->Unit,cue:(String)->Unit) {frame.on=if(frame.on==1.0)0.0 else 1.0;switchedAt=now;buzz(2);if(sound)cue("pull")}
    fun powerSwitch(reduced:Boolean,buzz:(Int)->Unit,cue:(String)->Unit) {takeOver();toggle(true,buzz,cue);if(!reduced)twV+=if(frame.on==1.0).25 else -.25}
    fun choose(i:Double,reduced:Boolean,buzz:(Int)->Unit) {takeOver();val v=i.coerceIn(0.0,4.0);if(reduced){frame.lead=v;frame.trail=v}else{tween("lead",frame.lead,v,.34,true);tween("trail",frame.trail,v,.6)};buzz(0)}
    fun adjust(delta:Double) {takeOver();tween("level",frame.level,(frame.level+delta).coerceIn(0.0,1.0),.2)}
    fun setLevel(l:FilamentBulbDimmerTheme.Layout,x:Double,y:Double,buzz:(Int)->Unit){frame.level=l.levelAt(x,y);tweens.remove("level");val s=(frame.level*20).roundToInt();if(s!=lastStep){lastStep=s;buzz(if(s==0||s==20)1 else 0)}}
    fun step(wall:Double,auto:Boolean,reduced:Boolean,l:FilamentBulbDimmerTheme.Layout,buzz:(Int)->Unit,cue:(String)->Unit):FilamentBulbDimmerMotion.Frame {
        now+=wall;val dt=min(.05,wall)
        if(mode==0 && auto && !reduced){val f=m.frameAt((now-start)%10);if(dt>0){thV=(f.th-frame.th)/dt;sV=(f.s-frame.s)/dt;twV=(f.tw-frame.tw)/dt};frame=f}
        else {
            val iterator=tweens.iterator();while(iterator.hasNext()){val (k,a)=iterator.next();val v=a.value(now);when(k){"level"->frame.level=v;"lead"->frame.lead=v;"trail"->frame.trail=v;"finger"->frame.finger=v};if(now>=a.at+a.duration)iterator.remove()}
            if(!reduced){val h=dt/4;val calm=if(resume)4.0 else 1.0;repeat(4){
                val a:Double;val ss:Double
                if(held){val dx=holdX-grabX-l.anchorX;val dy=holdY-grabY-l.anchorY;val tt=atan2(dx,max(1.0,dy)).coerceIn(-1.0,1.0);var st=hypot(dx,dy)-l.cord;if(st>30)st=30+(st-30)*.2;st=st.coerceIn(-44.0,42.0);a=1600*(tt-frame.th)-80*thV;ss=1600*(st-frame.s)-80*sV;twV+=across*a*h*.9}
                else {a = -m.SWING_W*m.SWING_W*sin(frame.th)-2*m.SWING_Z*calm*m.SWING_W*thV;ss=if(frame.s<-.5)m.GRAV*cos(frame.th)else -m.PULL_W*m.PULL_W*frame.s-2*m.PULL_Z*m.PULL_W*sV}
                val ta = -m.TWIST_W*m.TWIST_W*frame.tw-2*m.TWIST_Z*calm*m.TWIST_W*twV
                thV+=a*h;frame.th+=thV*h;sV+=ss*h;frame.s+=sV*h;twV+=ta*h;frame.tw+=twV*h
            };if(held && !clicked && frame.s>16){clicked=true;toggle(true,buzz,cue)};if(clicked && frame.s<6)clicked=false}
            var heatTarget=if(frame.on==1.0)frame.level else 0.0;val since=now-switchedAt
            if(frame.on==1.0 && since<m.WARM && !reduced)heatTarget*=m.warmUp(since/m.WARM)
            val tau=if(reduced).07 else if(heatTarget>frame.heat).09 else .22
            frame.heat+=(heatTarget-frame.heat)*(1-exp(-dt/tau));frame.time=now%10
            if(auto && !reduced && !held && target==0 && now-lastTouch>=2){
                if(!resume){resume=true;if(frame.on==0.0){frame.on=1.0;switchedAt=now};tween("level",frame.level,.78,.7);tween("lead",frame.lead,2.0,.5,true);tween("trail",frame.trail,2.0,.75)}
                else if(abs(frame.th)<.004 && abs(thV)<.02 && abs(frame.s)<.4 && abs(sV)<2 && abs(frame.tw)<.02 && abs(twV)<.05 && abs(frame.heat-.78)<.002 && abs(frame.level-.78)<.001 && abs(frame.trail-2)<.001){resume=false;frame.th=0.0;frame.s=0.0;frame.tw=0.0;start=now;mode=0}
            }
        }
        // UI-only damped springs; physical bulb integration above always uses the RN four substeps.
        repeat(4){val h=dt/4;togV+=(300*(frame.on-tog)-2*.72*sqrt(300.0)*togV)*h;tog+=togV*h;pctV+=(700*((frame.level*100).roundToInt()-pct)-2*sqrt(700.0)*pctV)*h;pct+=pctV*h;knobV+=(450*((if(target==2)1 else 0)-knob)-2*.7*sqrt(450.0)*knobV)*h;knob+=knobV*h}
        frame.tog=if(reduced)frame.on else tog;frame.shownPct=if(reduced)(frame.level*100).roundToInt().toDouble()else pct;frame.knobPress=knob
        return frame
    }
    fun touch(state:Int,x:Double,y:Double,dx:Double,dy:Double,l:FilamentBulbDimmerTheme.Layout,reduced:Boolean,buzz:(Int)->Unit,cue:(String)->Unit){
        fun swatch(v:Double)=((v-62*l.sx)/(278*l.sx)*4).coerceIn(0.0,4.0)
        when(state){
            0->{maxTravel=0.0;scrubbing=false;touchStarted=now;target=when{l.toggle.contains(x.toFloat(),y.toFloat())->4;x>l.cx-l.rOut-10 && x<l.cx+l.rOut+10 && y>l.cy-l.rOut-10 && y<l.cy+l.leg+(l.rOut-l.rIn)/2+10->2;abs(y-l.dotsY)<24 && x>=62*l.sx-26 && x<=340*l.sx+26->3;abs(x-l.anchorX)<130 && y>l.restY-12 && y<l.restY+3.74*l.r-4->1;else->0};if(target==0)return;takeOver();if(target==1){val p=l.pose(frame);grabX=x-p[0];grabY=y-p[1];holdX=x;holdY=y;across=((grabX*cos(frame.th)-grabY*sin(frame.th))/l.r).coerceIn(-1.0,1.0);held=true;cue("tink");buzz(1)};if(target==2)setLevel(l,x,y,buzz)}
            1->{if(target==0)return;lastTouch=now;maxTravel=max(maxTravel,hypot(dx,dy));when(target){1->{holdX=x;holdY=y};2->setLevel(l,x,y,buzz);3->if(maxTravel>=8 || scrubbing){scrubbing=true;frame.lead=swatch(x);tweens.remove("lead");if(frame.lead.roundToInt()!=frame.trail.roundToInt())buzz(0);if(reduced){frame.trail=frame.lead;tweens.remove("trail")}else tween("trail",frame.trail,frame.lead,.38)}}}
            else->{if(target==0)return;lastTouch=now;maxTravel=max(maxTravel,hypot(dx,dy));val isTap=state==2 && now-touchStarted<=.5;when(target){1->{held=false;if(frame.s>8)cue("release");if(isTap && maxTravel<=6){toggle(true,buzz,cue);if(!reduced){sV+=170;twV+=.5}}};3->{if(scrubbing)choose(frame.lead.roundToInt().toDouble(),reduced,buzz)else if(isTap && maxTravel<=8)choose(swatch(x).roundToInt().toDouble(),reduced,buzz);scrubbing=false};4->if(state==2 && x>=l.toggle.left-20 && x<=l.toggle.right+20 && y>=l.toggle.top-20 && y<=l.toggle.bottom+20)powerSwitch(reduced,buzz,cue)};target=0}
        }
    }
}
