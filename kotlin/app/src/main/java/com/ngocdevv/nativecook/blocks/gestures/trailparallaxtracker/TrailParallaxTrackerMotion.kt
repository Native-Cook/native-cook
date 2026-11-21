package com.ngocdevv.nativecook.blocks.gestures.trailparallaxtracker
import kotlin.math.*
internal data class TrailParallaxTrackerFrame(var d:Double=0.0,var v:Double=0.0,var run:Double=0.0,var phase:Double=0.0,var pose:Double=14.0,var face:Double=1.0,var hop:Double=0.0,var lean:Double=0.0,var squash:Double=1.0,var bubS:Double=1.0,var bubT:Double=0.0,var skidAge:Double= -1.0,var cardFrom:Double=0.0,var cardTo:Double=0.0,var cardDir:Double=1.0,var cardAge:Double=10.0,var time:Double=0.0)
internal object TrailParallaxTrackerMath {
 fun seg(t:Double,a:Double,b:Double)=((t-a)/(b-a)).coerceIn(0.0,1.0)
 fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
 fun smooth(a:Double,b:Double,x:Double):Double{val k=seg(x,a,b);return k*k*(3-2*k)}
 fun out(k:Double)=1-(1-k).pow(3)
 fun ease(k:Double)=if(k<.5)4*k*k*k else 1-(-2*k+2).pow(3)/2
 fun spring(t:Double,from:Double,to:Double,v:Double=0.0,d:Double=19.0,k:Double=195.0):Double{
  if(t<=0)return from;val x=from-to;val w=sqrt(k);val z=d/(2*w)
  if(z<1){val wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))}
  val s=sqrt(max(z*z-1,1e-6));val r1= -w*(z-s);val r2= -w*(z+s);val c2=(v-r1*x)/(r2-r1);val c1=x-c2;return to+c1*exp(r1*t)+c2*exp(r2*t)
 }
 fun ground(x:Double,base:Double):Double{val a=2*PI*x/520;return base+17*sin(a+2.3)+6*sin(2*a+1.25)+3*sin(3*a+.2)}
 fun run(v:Double)=smooth(30.0,45.0,abs(v))
 fun cadence(v:Double,run:Double)=mix(min(abs(v)*24/(52*93/48.0),2.4),3.1,run)
 fun pose(phase:Double,v:Double,run:Double,wave:Double= -1.0):Double{if(wave>=0)return wave;if(abs(v)<.3)return 14.0;val f=phase-floor(phase);return if(run>.5)8+(floor(f*6)%6)else floor(f*8)%8}
 fun lean(v:Double,run:Double,skid:Double):Double{val lean=mix(min(abs(v)*.3,8.0),4.0,run);val brake=if(skid>=0&&skid<.4)sin(PI*min(skid/.4,1.0))else 0.0;return lean*(1-brake)-10*brake}
 fun waypoint(distance:Double):Int{var index=0;for(i in 1 until TrailParallaxTrackerWorld.waypoints.size)if(distance>=TrailParallaxTrackerWorld.waypoints[i].at)index=i;return index}
 fun day(d:Double):Double{val q=max(d,0.0)/2.66;val f=q-floor(q);return 310-(floor(q)+smooth(.8,1.0,f))}
 fun odo(value:Double,column:Int):Double{val b=10.0.pow(column);val base=floor(value/b);val digit=((base%10)+10)%10;if(column==0)return digit+value-floor(value);val r=(((value%b)+b)%b)-(b-1);return digit+max(0.0,r)}
 fun cardProgress(age:Double)=(spring(age,0.0,1.0,0.0,30.5,322.3)/.98).coerceIn(0.0,1.0)
}
internal object TrailParallaxTrackerScore {
 private val keys=arrayOf(doubleArrayOf(0.0,0.0),doubleArrayOf(.06,-3.33),doubleArrayOf(.12,0.0),doubleArrayOf(1.2,21.0),doubleArrayOf(1.95,22.0),doubleArrayOf(2.55,3.0),doubleArrayOf(2.75,1.3),doubleArrayOf(3.0,1.3),doubleArrayOf(3.6,24.4),doubleArrayOf(4.75,24.4),doubleArrayOf(5.2,1.4),doubleArrayOf(5.7,1.2),doubleArrayOf(5.95,108.4),doubleArrayOf(6.5,40.0),doubleArrayOf(7.0,14.0),doubleArrayOf(7.3,3.0),doubleArrayOf(7.4,1.6),doubleArrayOf(8.0,2.3),doubleArrayOf(8.3,0.0))
 private val distances=DoubleArray(keys.size).apply{for(i in 1 until keys.size)this[i]=this[i-1]+(keys[i-1][1]+keys[i][1])/2*(keys[i][0]-keys[i-1][0])}
 fun distance(t:Double):Pair<Double,Double>{
  if(t<=8.3){var i=0;while(i<keys.size-2&&t>keys[i+1][0])i++;val t0=keys[i][0];val dt=(t-t0).coerceIn(0.0,keys[i+1][0]-t0);val a=(keys[i+1][1]-keys[i][1])/(keys[i+1][0]-t0);return distances[i]+keys[i][1]*dt+.5*a*dt*dt to keys[i][1]+a*dt}
  if(t>=11.4)return 0.0 to 0.0
  val s=t-8.3;val total=11.4-8.3;val r=.514;val speed=distances.last()/(total-r)
  fun ramp(s:Double):Double{val k=(s/r).coerceIn(0.0,1.0);return r*(k*k*k-k.pow(4)/2)}
  val covered:Double;val sp:Double
  if(s<r){covered=ramp(s);sp=TrailParallaxTrackerMath.smooth(0.0,r,s)}else if(s<total-r){covered=r/2+s-r;sp=1.0}else{val u=total-s;covered=total-r-ramp(u);sp=TrailParallaxTrackerMath.smooth(0.0,r,u)}
  return distances.last()-speed*covered to -speed*sp
 }
 private val phases=DoubleArray(1201).apply{var p=0.0;for(i in 1..1200){val t=(i-.5)/100;val v=distance(t).second;p+=TrailParallaxTrackerMath.cadence(v,TrailParallaxTrackerMath.run(v))/100;this[i]=p}}
 private data class Event(val t:Double,val from:Int,val to:Int,val dir:Int)
 private val events:Pair<List<Event>,List<Double>> by lazy {
  val cards=ArrayList<Event>();val skids=ArrayList<Double>();var previousW=0;var previousV=0.0;var t=.002
  while(t<=12){val(d,v)=distance(t);val w=TrailParallaxTrackerMath.waypoint(d);if(w!=previousW){cards+=Event(t,previousW,w,if(w>previousW)1 else -1);previousW=w};if(abs(previousV)>=14&&abs(v)<14)skids+=t;previousV=v;t+=.002};cards to skids
 }
 fun frame(raw:Double):TrailParallaxTrackerFrame{
  val m=TrailParallaxTrackerMath;val t=((raw%12)+12)%12;val(d,v)=distance(t);val f=TrailParallaxTrackerFrame();f.time=t;f.d=d;f.v=v;f.run=m.run(v)
  val x=t*100;val i=min(x.toInt(),1199);f.phase=m.mix(phases[i],phases[i+1],x-i);f.pose=m.pose(f.phase,v,f.run);f.face=if(t>=8.3&&t<11.48)-1.0 else 1.0
  fun hop(start:Double):Double{val k=m.seg(t,start,start+.16);return if(k>0&&k<1)sin(PI*k)else 0.0}
  f.hop=max(hop(8.22),hop(11.4));for(s in events.second)if(s<=t)f.skidAge=t-s
  val antic=if(t<.12)sin(PI*m.seg(t,0.0,.12))else 0.0;f.lean=m.lean(v,f.run,f.skidAge)-4*antic
  val crouch=if(t>=5.6&&t<5.7)m.seg(t,5.6,5.7).pow(3)else if(t>=5.7)max(0.0,m.spring(t-5.7,1.0,0.0))else 0.0;f.squash=1-.08*crouch-.1*f.hop+.015*sin(2*PI*6*t/12)
  if(t>=.2&&t<11.55){val k=m.seg(t,.2,.48);f.bubS=1-k.pow(3);f.bubT=m.ease(k)}else if(t>=11.55){f.bubS=m.mix(max(0.0,m.spring(t-11.55,0.0,1.0)),1.0,m.seg(t,11.85,12.0));f.bubT=1-m.out(m.seg(t,11.55,11.8))}
  var e=events.first.last();var age=t+12-e.t;for(event in events.first)if(event.t<=t){e=event;age=t-event.t};f.cardFrom=e.from.toDouble();f.cardTo=e.to.toDouble();f.cardDir=e.dir.toDouble();f.cardAge=age;return f
 }
}
internal class TrailParallaxTrackerEngine {
 var f=TrailParallaxTrackerScore.frame(0.0);var automatic=true;var held=false
 private var origin=0.0;private var prior=0.0;private var lastTouch=0.0;private var startDistance=0.0;private var anchor=0.0;private var previousD=0.0
 private var mode=0;private var target=0.0;private var releaseVelocity=0.0;private var homeFrom=0.0;private var homeTime=0.0;private var faceTarget=1.0;private var faceTime= -99.0;private var braceTime= -99.0;private var skidTime= -99.0;private var waveTime= -99.0;private var bubbleTime= -99.0;private var cardTime= -99.0;private var bubbleV=0.0
 fun frame(now:Double,autoPlay:Boolean,pin:Double?,manual:Boolean,reduced:Boolean):TrailParallaxTrackerFrame{
  if(origin==0.0){origin=now;prior=now;automatic=autoPlay&&!manual&&!reduced};val dt=(now-prior).coerceIn(0.0,.05);prior=now
  if(automatic){f=TrailParallaxTrackerScore.frame(pin?:now-origin);return f}
  val m=TrailParallaxTrackerMath;val oldV=f.v
  if(!held){
   if(mode==2){val duration=(abs(homeFrom)/58+.7).coerceIn(.8,3.1);val p=min((now-homeTime)/duration,1.0);f.d=homeFrom*(1-m.ease(p));if(p>=1){f.d=0.0;mode=3;homeTime=now;if(faceTarget!=1.0){faceTarget=1.0;faceTime=now}}}
   else if(mode==3){f.d=0.0;if(now-homeTime>.45){automatic=true;origin=now;mode=0;f=TrailParallaxTrackerScore.frame(0.0);return f}}
   else if(f.d<0||f.d>150){val b=if(f.d<0)0.0 else 150.0;releaseVelocity+=(-157.9*(f.d-b)-22.1*releaseVelocity)*dt;f.d+=releaseVelocity*dt;mode=0;target=b}
   else if(mode==1){val decay= -1000*ln(.9972);val old=releaseVelocity;val velocity=old*exp(-decay*dt);releaseVelocity=velocity;f.d+=(old-velocity)/decay;if(abs(velocity)<.6){mode=0;target=(f.d*10).roundToInt()/10.0}}
   else{releaseVelocity+=(-157.9*(f.d-target)-22.1*releaseVelocity)*dt;f.d+=releaseVelocity*dt}
   if(autoPlay&&!manual&&pin==null&&!reduced&&mode==0&&now-lastTouch>2&&abs(f.d-target)<.02&&abs(releaseVelocity)<.08){mode=2;homeFrom=f.d;homeTime=now}
  }
  val measured=if(dt>0)(f.d-previousD)/dt else 0.0;f.v=m.mix(oldV,measured,.35);previousD=f.d;f.run=if(reduced)0.0 else m.run(f.v);if(!reduced)f.phase+=m.cadence(f.v,f.run)*dt
  if(!held&&mode!=3&&abs(f.v)>.6&&(if(f.v<0)-1.0 else 1.0)!=faceTarget){faceTarget=if(f.v<0)-1.0 else 1.0;faceTime=now}
  if(abs(oldV)>=14&&abs(f.v)<14&&now-skidTime>.5)skidTime=now
  val way=m.waypoint(f.d);if(way!=f.cardTo.toInt()){f.cardFrom=f.cardTo;f.cardDir=if(way>f.cardTo)1.0 else -1.0;f.cardTo=way.toDouble();cardTime=now}
  f.cardAge=now-cardTime;f.skidAge=if(now-skidTime<.5)now-skidTime else -1.0
  val turn=m.seg(now,faceTime,faceTime+.16);f.hop=if(reduced)0.0 else if(turn>0&&turn<1)sin(PI*turn)else 0.0;f.face=if(turn<.5&&f.hop>0)-faceTarget else faceTarget
  val age=now-braceTime;val brace=if(age>=0&&age<.6)(if(held&&age<.08)age/.08 else max(0.0,m.spring(age-.08,1.0,0.0)))else 0.0
  val waveAge=now-waveTime;val wave=if(waveAge>=0&&waveAge<.5)intArrayOf(16,17,18,17,16)[min(4,(waveAge/.1).toInt())].toDouble()else -1.0
  f.pose=m.pose(f.phase,f.v,f.run,wave);f.lean=if(reduced)0.0 else m.lean(f.v,f.run,f.skidAge);f.squash=if(reduced)1.0 else 1-.1*f.hop-.06*brace+.015*sin(PI*now)
  val show=if((abs(f.d)<.6&&abs(f.v)<1.5&&mode!=2)||now-bubbleTime<2)1.0 else 0.0;bubbleV+=(-195*(f.bubS-show)-19*bubbleV)*dt;f.bubS=max(0.0,f.bubS+bubbleV*dt);f.bubT=m.mix(f.bubT,if(show==1.0)0.0 else 1.0,min(1.0,dt*10));f.time=now-origin;return f
 }
 fun begin(now:Double){automatic=false;held=true;mode=0;releaseVelocity=0.0;target=f.d;startDistance=f.d;anchor=f.d;previousD=f.d;faceTarget=f.face;braceTime=now;cardTime=now-f.cardAge;lastTouch=now;if(abs(f.v)>14)skidTime=now}
 fun move(translation:Double,now:Double){var d=startDistance-translation/24;if(d<0){val over= -d*24;d= -over/(1+over/120)/24}else if(d>150){val over=(d-150)*24;d=150+over/(1+over/120)/24};f.d=d;if((d-anchor)*faceTarget>0)anchor=d else if((anchor-d)*faceTarget>4/24.0){faceTarget= -faceTarget;faceTime=now;anchor=d};lastTouch=now}
 fun end(velocity:Double,now:Double,cancelled:Boolean){held=false;lastTouch=now;val v=if(cancelled)0.0 else -velocity/24;releaseVelocity=v;if(f.d>=0&&f.d<=150&&abs(v)>=.6)mode=1 else{mode=0;target=(f.d.coerceIn(0.0,150.0)*10).roundToInt()/10.0}}
 fun tap(x:Double,y:Double,base:Double,now:Double){val foot=TrailParallaxTrackerMath.ground(200+f.d*24,base);if(abs(x-200)<36&&y>foot-107&&y<foot+10){waveTime=now;bubbleTime=now};lastTouch=now}
 fun adjust(d:Double,now:Double){begin(now);f.d=d.coerceIn(0.0,150.0);previousD=f.d;end(0.0,now,true)}
}
