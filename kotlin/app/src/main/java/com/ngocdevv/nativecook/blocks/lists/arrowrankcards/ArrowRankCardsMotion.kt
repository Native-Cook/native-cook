package com.ngocdevv.nativecook.blocks.lists.arrowrankcards
import kotlin.math.*

internal object ArrowRankCardsMotion {
 const val LOOP=9.0
 fun clamp(v: Double,a: Double=0.0,b: Double=1.0)=max(a,min(b,v))
 fun seg(t: Double,a: Double,b: Double)=clamp((t-a)/(b-a))
 fun mix(a: Double,b: Double,p: Double)=a+(b-a)*p
 fun smooth(p: Double)=p*p*(3-2*p)
 fun wrap(i: Int)=((i%3)+3)%3
 fun heading(i: Int)=doubleArrayOf(0.0,90.0,180.0)[wrap(i)]
 fun pop(p: Double): Double { val u=clamp(p); if(u==0.0 || u==1.0)return u; return 1+2.25*(u-1).pow(3)+1.25*(u-1).pow(2) }
 fun slats(p: Double)=DoubleArray(9) { clamp(.5+(-2+13*p-it-.5)/5) }
 fun rest(index: Int=0): ArrowRankCardsFrame { val i=wrap(index);val a=heading(i);return ArrowRankCardsFrame(index=i,next=wrap(i+1),head=a,tail=a,axis=if(a==90.0 || a==270.0)1.0 else 0.0,reverse=if(a==180.0 || a==270.0)1.0 else 0.0) }
 fun shortest(from: Double,to: Double): Double { val d=((to-from+540)%360)-180;return from+if(d == -180.0)180.0 else d }
 fun beckon(t: Double,from: Double=0.0): Double { val u=t-from;return if(u>0 && u<.56)5*sin(PI*((u%.28)/.28)).pow(2) else 0.0 }
 fun release(held: ArrowRankCardsFrame,elapsed: Double,commit: Boolean,velocity: Double=0.0): ArrowRankCardsFrame {
  var first=0;var last=8
  while(first<8 && held.q[first]>=1)first++
  while(last>0 && held.q[last]<=0)last--
  val q=DoubleArray(9) { i-> val order=max(0,if(commit)i-first else last-i);mix(held.q[i],if(commit)1.0 else 0.0,pop(seg(elapsed,order*.028,order*.028+.26))) }
  val roll=seg(elapsed,.015,.3);val land=smooth(roll);val angle=shortest(held.tail,heading(held.next))
  val handoff=clamp(velocity,-3.0,3.0)*elapsed*(1-seg(elapsed,0.0,.3)).pow(3)
  return held.copy(q=q,p=mix(held.p,if(commit)1.0 else 0.0,land)+handoff,drum=mix(held.drum,if(commit)1.0 else 0.0,land),stretch=held.stretch*(1-pop(seg(elapsed,0.0,.26))),head=if(commit)mix(held.head,angle,pop(seg(elapsed,.06,.56))) else held.head,tail=if(commit)mix(held.tail,angle,pop(seg(elapsed,.26,.56))) else held.tail,ticks=held.ticks-if(commit)12*land else 0.0,beckon=0.0,shake=0.0,twitch=0.0,blur=if(commit)24*roll*(1-roll) else 0.0)
 }
 fun turn(t: Double,index: Int,start: Double,half: Double,end: Double,angle: Double,nextAngle: Double): ArrowRankCardsFrame {
  val p=if(t<half).55*smooth(seg(t,start,half)) else if(t<end)mix(.55,.62,seg(t,half,end)) else 0.0
  val held=rest(index).copy(p=p,q=slats(p),head=angle,tail=angle,stretch=36*clamp(p*2),ticks=-index*12.0)
  if(t<start)return held.copy(beckon=beckon(t,start-.62))
  if(t<end)return held
  return release(held.copy(p=.62,q=slats(.62),stretch=36.0),t-end,true).copy(head=mix(angle,nextAngle,pop(seg(t,end+.06,end+.56))),tail=mix(angle,nextAngle,pop(seg(t,end+.26,end+.56))))
 }
 fun frame(time: Double): ArrowRankCardsFrame {
  val t=((time%LOOP)+LOOP)%LOOP
  if(t<2.3)return turn(t,0,.7,1.25,1.5,0.0,90.0)
  if(t<4.85)return turn(t,1,3.0,3.7,4.05,90.0,180.0)
  if(t<6.9)return turn(t,2,5.6,6.1,6.3,180.0,360.0)
  if(t<8.3) {
   val r=(t-6.9)/(1.4/6);val tick=min(5,floor(r).toInt());val k=r-tick;val roll=seg(k,0.0,.84);val p=smooth(roll)
   return rest(tick).copy(p=p,blur=24*roll*(1-roll),q=DoubleArray(9) { pop(seg(k,it*.022,.64+it*.022)) },drum=p,head=360+720*smooth(seg(t,6.9,8.3)),tail=360+720*smooth(seg(t-.07,6.9,8.23)),stretch=10*sin(PI*p),ticks=-36-12*r)
  }
  return rest().copy(beckon=beckon(t,8.3))
 }
}
