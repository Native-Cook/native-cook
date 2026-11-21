package com.ngocdevv.nativecook.blocks.lists.arrowrankcards
import kotlin.math.*

internal class ArrowRankCardsEngine {
 private val m=ArrowRankCardsMotion
 var autoPlay=true;var reduced=false;var auto=true;var pin: Double?=null
 var time=0.0;var now=0.0;var live=m.rest();var start=m.rest();var touching=false
 private var bookmarkAt = -10.0
 val bookmarkScale: Double get() { val t=now-bookmarkAt;if(reduced || t>3)return 1.0;val a=22.0/8;val w=sqrt(360.0/4-a*a);return 1-.14*exp(-a*t)*(cos(w*t)+a/w*sin(w*t)) }
 var rulerOn=false;var rulerValue=0.0;var saved=BooleanArray(3);var buzz: (String)->Unit={}
 private var release = -1.0;private var commit=false;private var velocity=0.0;private var lastTouch=0.0
 private var rulerStart=0.0;private var rulerVelocity=0.0;private var wrongAt = -1.0;private var hintAt = -1.0
 private var wrongOnce=false;private var threshold=false;private var returnAt = -1.0;private var returnStart=m.rest()
 private var previousSlats=0;private var previousRank=0;private var lastSelection = -1.0
 fun frame()=if(auto)m.frame(pin?:time) else live
 fun step(raw: Double) {
  val dt=raw.coerceIn(0.0,.05);now+=dt
  if(pin!=null)return
  if(auto) { if(!reduced)time=(time+dt)%9;return }
  if(rulerOn) {
   if(!touching) { val previous=rulerVelocity;val v=previous*exp(-dt*7);rulerVelocity=v;rulerValue+=(previous-v)/7
    if(abs(v)<.25) { rulerValue=m.mix(rulerValue,floor(rulerValue+.5),min(1.0,dt*20));if(abs(rulerValue-floor(rulerValue+.5))<.001) { live=m.rest(floor(rulerValue+.5).toInt());rulerOn=false } }
   }
   if(rulerOn) { val n=floor(rulerValue);val p=rulerValue-n;val f=m.rest(n.toInt());val a=m.shortest(f.head,m.heading(f.next));live=f.copy(p=p,q=if(reduced)f.q else m.slats(p),drum=p,flat=if(reduced)1.0 else 0.0,head=if(reduced)f.head else m.mix(f.head,a,m.smooth(p)),tail=if(reduced)f.tail else m.mix(f.tail,a,m.smooth(p)),ticks=-rulerValue*12) }
  } else if(release>=0) {
   val previous=release;release+=dt
   live=if(reduced)start.copy(q=DoubleArray(9),drum=if(commit)m.seg(release,0.0,.2) else 0.0,stretch=0.0,flat=1.0) else m.release(start,release,commit,velocity)
   val landing=if(reduced).2 else .3;if(commit && previous<landing && release>=landing)buzz("medium")
   if(release>=if(reduced).2 else .78) { live=m.rest(if(commit)start.next else start.index);release = -1.0 }
  } else if(!touching && !reduced && hintAt>=0) { val u=m.seg(now-hintAt,0.0,.36);live=live.copy(stretch=18*sin(PI*u),beckon=-6*sin(PI*2*u));if(u==1.0)hintAt = -1.0 }
  if(wrongAt>=0 && now-wrongAt<.24 && !reduced) { val v=6*sin((now-wrongAt)/.24*PI*4);live=live.copy(shake=v,twitch=v) } else live=live.copy(shake=0.0,twitch=0.0)
  if(autoPlay && !reduced && !touching && release<0 && !rulerOn && now-lastTouch>2) {
   if(returnAt<0) { returnAt=now;returnStart=live };val u=m.seg(now-returnAt,0.0,.65);val from=returnStart
   val f=m.rest(from.index);val p=m.smooth(u)
   live=if(from.index==0)m.rest() else f.copy(next=0,p=p,q=m.slats(p),drum=p,head=m.mix(from.head,m.shortest(from.head,0.0),p),tail=m.mix(from.tail,m.shortest(from.tail,0.0),p))
   if(u==1.0) { time=0.0;auto=true;returnAt = -1.0 }
  }
  val count=live.q.count { it>=.5 };if(count>0 && count%3==0 && count!=previousSlats && now-lastSelection>.08) { buzz("selection");lastSelection=now };previousSlats=count
  val rank=floor(rulerValue+.5).toInt();if(rulerOn && rank!=previousRank && now-lastSelection>=.1) { buzz("selection");lastSelection=now };previousRank=rank
 }
 fun begin() { live=frame();start=live;auto=false;pin=null;release = -1.0;returnAt = -1.0;touching=true;rulerOn=false;hintAt = -1.0;wrongAt = -1.0;wrongOnce=false;threshold=false;lastTouch=now;buzz("light") }
 fun move(x: Double,y: Double) { val a=m.heading(start.index)*PI/180;val dot=x*cos(a)+y*sin(a);val p=m.clamp(start.p+dot/170,0.0,1.15)
  if(dot < -10 && !wrongOnce) { wrongOnce=true;wrongAt=now;buzz("rigid") };val b=m.slats(start.p);val n=m.slats(p)
  live=start.copy(p=p,q=if(reduced)start.q else DoubleArray(9) { m.clamp(start.q[it]+n[it]-b[it]) },stretch=if(reduced)0.0 else m.clamp(start.stretch+72*(p-start.p),0.0,36.0))
  if(p>=.5 && !threshold) { threshold=true;buzz("light") }
 }
 fun end(vx: Double,vy: Double,success: Boolean=true) { val a=m.heading(live.index)*PI/180;velocity=if(success)(vx*cos(a)+vy*sin(a))/170 else 0.0;commit=success && live.p+velocity*.12>=.5;start=live;release=0.0;touching=false;lastTouch=now }
 fun rulerBegin() { val position=if(rulerOn)rulerValue else frame().index.toDouble();begin();rulerOn=true;rulerValue=position;rulerStart=position;rulerVelocity=0.0 }
 fun rulerMove(x: Double) { rulerValue=rulerStart-x/12 }
 fun rulerEnd(vx: Double) { touching=false;rulerVelocity=m.clamp(-vx/12,-12.0,12.0);lastTouch=now }
 fun hint() { begin();touching=false;hintAt=now }
 fun bookmark() { begin();touching=false;val i=if(live.drum<.5)live.index else live.next;saved[i]=!saved[i];bookmarkAt=now }
 fun advance() { begin();live=live.copy(p=1.0);end(0.0,0.0) }
}
