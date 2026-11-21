package com.ngocdevv.nativecook.blocks.inputs.elasticstringquiz

import kotlin.math.*

internal class ElasticStringQuizEngine(var script:ElasticStringQuizMotion.Script,val words:List<String>) {
    private val m=ElasticStringQuizMotion
    var autoPlay=true; var reduced=false; var manual=false; var pin:Double?=null
    var onHaptic:((Int)->Unit)?=null; var onSound:((Int,Double)->Unit)?=null; var onAnswer:((String,Int,Boolean)->Unit)?=null; var onRoundEnd:((List<List<String>>)->Unit)?=null
    var frame=m.frameAt(0.0,script); private set
    var matched=MutableList(3){mutableListOf<String>()}; private set
    private var events=m.initEv(script.score); private var now=0.0; private var phase=0.0; private var auto=true; private var touching=false; private var lastTouch=0.0
    private var held:Int?=null; private var dy0=0.0; private var grabOffset=0.0; private var began=0.0; private var moved=0.0; private var velocity=0.0; private var armed=false
    private var strumPoint:Pair<Double,Double>?=null; private var lastStrum=-10.0
    fun configure(autoPlay:Boolean,reduced:Boolean) {
        if(this.autoPlay!=autoPlay||this.reduced!=reduced)auto=autoPlay&&!reduced&&!manual
        if(reduced&&!this.reduced){events=m.initEv(script.score,1.0);now=0.0;pin=null;touching=false}
        this.autoPlay=autoPlay;this.reduced=reduced;events.rm=if(reduced)1.0 else 0.0
        if(manual||reduced)auto=false;publish()
    }
    private fun publish(){frame=if(auto||pin!=null)m.frameAt(pin?:phase,script) else m.compose(events,now,script)}
    fun advance(dt:Double){
        if(pin!=null)return
        if(auto){phase=(phase+dt)%12;publish();return};now+=dt
        for(i in 0..2){
            if(events.tapT[i]>m.NEG/2&&now>=events.tapT[i]+.14){val t=events.tapT[i]+.14;m.releaseCord(events,i.toDouble(),t,52.0,0.0,0.0);m.firePulse(events,i.toDouble(),t,1.0);onHaptic?.invoke(1);onSound?.invoke(i,.6)}
            if(events.pend[i]==1.0&&now>=events.pulseT[i]+m.LREST[i]/m.PULSE_SPEED)arrive(i,events.pulseT[i]+m.LREST[i]/m.PULSE_SPEED)
        }
        if(now>=events.trAt){m.startTransition(events,events.trAt,script,0.0);matched=MutableList(3){mutableListOf()}}
        if(!reduced&&autoPlay&&!manual&&!touching&&now-lastTouch>2&&quiet()){
            val recap=events.recapT<=now&&!(events.recapOffT>events.recapT)
            if(!(recap&&now<events.recapT+3.5)){
                if(!recap&&m.wordAt(events,now)==0.0&&events.sTo==script.score){phase=0.0;auto=true}
                else{if(recap)events.recapOffT=now;m.startTransition(events,if(recap)now-.2 else now,script,if(recap)1.0 else 0.0);matched=MutableList(3){mutableListOf()};lastTouch=now}
            }
        };publish()
    }
    private fun quiet():Boolean{
        for(i in 0..2){
            if(events.held[i]==1.0||events.tapT[i]>m.NEG/2||events.pend[i]==1.0||now-events.relT[i]<1.5)return false
            if(events.snapT[i]<m.POS/2&&now<events.snapT[i]+1.6)return false
            if(m.haloLevel(events,i.toDouble(),now)>.01)return false
            for(s in 0..2){val b=(i*3+s)*5;if(now>=events.pl[b+3]&&abs(events.pl[b])*exp(-(now-events.pl[b+3])/events.pl[b+4])>.03)return false}
        }
        return !(now<events.pop[1]+.6||now<events.pop[3]+.6||(events.nxT<m.POS/2&&now<events.nxT+1)||now<events.transT+2.3||now<events.rejT+1.5)
    }
    private fun takeOver(){
        if(auto||pin!=null){val t=pin?:phase;events=m.autoEventsAt(t,script);for(i in 0..2)if(events.held[i]==1.0)m.releaseCord(events,i.toDouble(),t,events.hs[i],0.0,0.0)
            events.rm=if(reduced)1.0 else 0.0;now=t;auto=false;matched=MutableList(3){mutableListOf()}
            val w=m.wordAt(events,now).toInt();for(j in 0..<min(w,words.size))matched[script.answers[j].toInt()].add(words[j])
        };pin=null;lastTouch=now
    }
    private fun busy()=(now>=events.transT&&now<events.transT+2.2)||now>=events.recapT-.4
    fun begin(i:Int):Boolean{
        takeOver();touching=true
        if(busy()||(events.snapT[i]<m.POS/2&&now>=events.snapT[i]-.06&&now<events.snapT[i]+1.1)||(events.pend[i]==1.0&&reduced)){held=null;return false}
        held=i;val s=m.sAt(events,i.toDouble(),now);val b=m.bendAt(events,i.toDouble(),now)
        events.held[i]=1.0;events.hs[i]=if(reduced)0.0 else s;events.hb[i]=if(reduced)0.0 else b;events.tapT[i]=m.NEG
        if(m.haloLevel(events,i.toDouble(),now)<.5)events.haloOn[i]=now;events.haloOff[i]=m.NEG
        dy0=m.elasticInv(max(s,-7.0));grabOffset=s-m.elastic(dy0);began=now;moved=0.0;velocity=0.0;armed=s>=script.arm;onHaptic?.invoke(0);publish();return true
    }
    fun move(x:Double,y:Double,velocityY:Double){val i=held?:return;moved=max(moved,hypot(x,y));if(reduced)return
        val d=dy0+y;val s=m.elastic(d)+grabOffset;events.hs[i]=s;events.hb[i]=24*tanh(x/48);velocity=velocityY*m.elasticSlope(d)
        val ready=s>=script.arm;if(ready!=armed){armed=ready;onHaptic?.invoke(3)};lastTouch=now;publish()
    }
    fun end(cancelled:Boolean=false){touching=false;lastTouch=now;val i=held?:return;held=null;val s=events.hs[i];val b=events.hb[i]
        if(cancelled){m.releaseCord(events,i.toDouble(),now,s,0.0,b);events.haloOff[i]=now+.15;publish();return}
        if(reduced){events.held[i]=0.0;arrive(i,now);onSound?.invoke(i,.6)}
        else if(moved<8&&now-began<.3){events.held[i]=0.0;events.tapT[i]=now;events.tapS0[i]=s}
        else{val answer=s+velocity*.1>=script.arm;m.releaseCord(events,i.toDouble(),now,s,velocity,b);m.firePulse(events,i.toDouble(),now,if(answer)1.0 else 2.0);if(!answer)events.haloOff[i]=now+.15;onHaptic?.invoke(1);onSound?.invoke(i,m.clamp01(s/96))};publish()
    }
    private fun arrive(i:Int,at:Double){val w=m.wordAt(events,at).toInt();if(w !in words.indices)return;val correct=script.answers[w].toInt()==i;m.arrive(events,i.toDouble(),at,correct,script,0.0)
        onHaptic?.invoke(2);onSound?.invoke(if(correct)3 else 4,1.0);onAnswer?.invoke(words[w],i,correct)
        if(correct){matched[i].add(words[w]);if(w>=words.lastIndex)onRoundEnd?.invoke(matched.map{it.toList()})}
    }
    fun strumBegin(x:Double,y:Double){takeOver();touching=true;strumPoint=x to y}
    fun strumMove(x:Double,y:Double,velocityX:Double){val p=strumPoint?:return
        if(!busy()&&!reduced)for(i in 0..2){val cx=ElasticStringQuizMotion.G.bx[i];if((p.first-cx)*(x-cx)>=0||p.first==x)continue;val yy=m.mix(p.second,y,(cx-p.first)/(x-p.first))
            if(yy<353||yy>620||events.held[i]==1.0||(events.snapT[i]<m.POS/2&&now>=events.snapT[i]&&now<events.snapT[i]+1.1))continue
            val top=if(i==1)300.0 else 353.0;val u=m.clamp01((yy-top)/(640-top));val a=abs(velocityX).div(150).coerceIn(2.0,9.0)*(if(x>p.first)1 else -1)
            m.addPluck(events,i.toDouble(),a*sin(PI*u),a*sin(2*PI*u)/4,a*sin(3*PI*u)/9,now,.9)
            if(now-lastStrum>=.06){onHaptic?.invoke(3);lastStrum=now};onSound?.invoke(i,abs(a)/9)
        };strumPoint=x to y;lastTouch=now
    }
    fun strumEnd(){touching=false;lastTouch=now;strumPoint=null}
    fun continueRound(){takeOver();if(events.recapT>now||events.recapOffT>events.recapT)return;events.recapOffT=now;m.startTransition(events,now-.2,script,1.0);matched=MutableList(3){mutableListOf()};onHaptic?.invoke(1);publish()}
}
