package com.ngocdevv.nativecook.blocks.forms.lacedbootstudio

import kotlin.math.*

internal data class LacedBootStudioPoint(val x:Double=0.0,val y:Double=0.0)
internal data class LacedBootStudioScene(
 var progress:Double=0.0,var trims:DoubleArray=DoubleArray(11),var spanAlpha:DoubleArray=DoubleArray(11){1.0},
 var slacks:DoubleArray=DoubleArray(11){6.375},var tensions:DoubleArray=DoubleArray(6){.15},var automaticSlack:DoubleArray=DoubleArray(11),
 var tailMorph:Double=0.0,var tip:LacedBootStudioPoint=LacedBootStudioPoint(63.0,181.0),var tangent:LacedBootStudioPoint=LacedBootStudioPoint(1.0,0.0),
 var tipBackoff:Double=0.0,var automaticAgletY:Double=0.0,var tipAlpha:Double=1.0,var velocityLag:Double=0.0,var overpull:LacedBootStudioPoint=LacedBootStudioPoint(),
 var materials:DoubleArray=doubleArrayOf(1.0,0.0,0.0),var soles:DoubleArray=doubleArrayOf(1.0,0.0),var savedMix:Double=0.0
){val rows:Int get()=floor(progress+1e-8).toInt().coerceIn(0,6)
 fun snapshot()=copy(trims=trims.copyOf(),spanAlpha=spanAlpha.copyOf(),slacks=slacks.copyOf(),tensions=tensions.copyOf(),automaticSlack=automaticSlack.copyOf(),materials=materials.copyOf(),soles=soles.copyOf())}
internal data class LacedBootStudioCurve(val a:LacedBootStudioPoint,val c1:LacedBootStudioPoint,val c2:LacedBootStudioPoint,val b:LacedBootStudioPoint){
 fun point(t:Double):LacedBootStudioPoint{val u=1-t;return LacedBootStudioPoint(u*u*u*a.x+3*u*u*t*c1.x+3*u*t*t*c2.x+t*t*t*b.x,u*u*u*a.y+3*u*u*t*c1.y+3*u*t*t*c2.y+t*t*t*b.y)}
 fun tangent(t:Double):LacedBootStudioPoint{val u=1-t;val x=3*u*u*(c1.x-a.x)+6*u*t*(c2.x-c1.x)+3*t*t*(b.x-c2.x);val y=3*u*u*(c1.y-a.y)+6*u*t*(c2.y-c1.y)+3*t*t*(b.y-c2.y);val l=max(1e-9,hypot(x,y));return LacedBootStudioPoint(x/l,y/l)}
 fun prefix(t:Double):LacedBootStudioCurve{val ab=LacedBootStudioMath.point(a,c1,t);val bc=LacedBootStudioMath.point(c1,c2,t);val cd=LacedBootStudioMath.point(c2,b,t);val abc=LacedBootStudioMath.point(ab,bc,t);val bcd=LacedBootStudioMath.point(bc,cd,t);return LacedBootStudioCurve(a,ab,abc,LacedBootStudioMath.point(abc,bcd,t))}
 fun segment(low:Double,high:Double):LacedBootStudioCurve{val upper=prefix(high);if(low<=0||high<=0)return upper;val p=LacedBootStudioCurve(upper.b,upper.c2,upper.c1,upper.a).prefix(1-low/high);return LacedBootStudioCurve(p.b,p.c2,p.c1,p.a)}
}
internal data class LacedBootStudioArc(val points:List<LacedBootStudioPoint>,val distances:DoubleArray){
 val length:Double get()=distances.last()
 fun parameter(fraction:Double):Double{val d=LacedBootStudioMath.clamp(fraction)*length;if(d<=0)return 0.0;for(i in 1..31)if(distances[i]>=d){val u=(d-distances[i-1])/max(1e-9,distances[i]-distances[i-1]);return(i-1+u)/31};return 1.0}
 fun nearest(p:LacedBootStudioPoint):Pair<Double,Double>{var fraction=0.0;var distance=Double.POSITIVE_INFINITY;for(i in 1..31){val a=points[i-1];val b=points[i];val dx=b.x-a.x;val dy=b.y-a.y;val u=LacedBootStudioMath.clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/max(1e-9,dx*dx+dy*dy));val d=hypot(p.x-a.x-dx*u,p.y-a.y-dy*u);if(d<distance){fraction=(distances[i-1]+(distances[i]-distances[i-1])*u)/max(1e-9,length);distance=d}};return fraction to distance}
}
internal object LacedBootStudioGeometry{
 val arcs get()=LacedBootStudioTables.arcs
 fun eyelet(row:Int,right:Boolean)=LacedBootStudioPoint(if(right)76-.55*row else 41+.55*row,166.0-22*row)
 fun row(span:Int)=if(span==0)0 else(span+1)/2
 fun endpoints(span:Int):Pair<LacedBootStudioPoint,LacedBootStudioPoint>{if(span==0)return eyelet(0,false) to eyelet(0,true);val r=row(span);return if(span%2==0)eyelet(r,false) to eyelet(r,true) else eyelet(r-1,true) to eyelet(r,false)}
 fun curve(span:Int,slack:Double):LacedBootStudioCurve{val(a,b)=endpoints(span);return LacedBootStudioCurve(a,LacedBootStudioPoint(a.x+(b.x-a.x)*.33,a.y+(b.y-a.y)*.33+slack),LacedBootStudioPoint(a.x+(b.x-a.x)*.67,a.y+(b.y-a.y)*.67+slack),b)}
 val tipRoute=LacedBootStudioCurve(LacedBootStudioPoint(63.0,181.0),LacedBootStudioPoint(67.0,174.0),LacedBootStudioPoint(76.0,176.0),LacedBootStudioPoint(76.0,166.0))
 fun tail(s:LacedBootStudioScene):LacedBootStudioCurve{val c=curve(0,s.slacks[0]);val u=s.tailMorph;return LacedBootStudioCurve(c.a,LacedBootStudioMath.point(LacedBootStudioPoint(43.0,179.0),c.c1,u),LacedBootStudioMath.point(LacedBootStudioPoint(55.0,185.0),c.c2,u),tipRoute.point(arcs[0].parameter(u)))}
 fun local(p:LacedBootStudioPoint):LacedBootStudioPoint{val a=4*PI/180;val x=p.x-272.5;val y=p.y-308;return LacedBootStudioPoint(cos(a)*x+sin(a)*y+58.5,-sin(a)*x+cos(a)*y+160)}
 fun accepts(p:LacedBootStudioPoint):Boolean{val q=local(p);return q.x>=-12&&q.x<129&&q.y>=-12&&q.y<332}
 fun spanProgress(i:Int,f:Double)=if(i==0)f else row(i)+(if(i%2==0).5 else 0.0)+f*.5
 fun activeSpan(p:Double)=if(p<1)0 else(floor((p-1)*2).toInt()+1).coerceIn(1,10)
}
internal object LacedBootStudioMath{
 const val LOOP=8.0
 fun clamp(x:Double,low:Double=0.0,high:Double=1.0)=min(high,max(low,x))
 fun smooth(x:Double):Double{val u=clamp(x);return u*u*(3-2*u)}
 fun point(a:LacedBootStudioPoint,b:LacedBootStudioPoint,u:Double)=LacedBootStudioPoint(a.x+(b.x-a.x)*u,a.y+(b.y-a.y)*u)
 fun residual(age:Double)=1-smooth((age-.12)/.31)
 fun updateTip(s:LacedBootStudioScene){val i=LacedBootStudioGeometry.activeSpan(s.progress);if(i==0){val c=LacedBootStudioGeometry.tail(s);s.tip=c.b;s.tangent=c.tangent(1.0)}else{val c=LacedBootStudioGeometry.curve(i,s.slacks[i]);val t=LacedBootStudioGeometry.arcs[i].parameter(s.trims[i]);s.tip=c.point(t);s.tangent=c.tangent(t)};s.tip=LacedBootStudioPoint(s.tip.x+s.overpull.x-s.tangent.x*s.tipBackoff,s.tip.y+s.overpull.y+s.automaticAgletY-s.tangent.y*s.tipBackoff)}
 fun canonical(progress:Double,tensions:DoubleArray):LacedBootStudioScene{val s=LacedBootStudioScene();s.progress=clamp(progress,0.0,6.0);s.tensions=tensions.map{clamp(it)}.toDoubleArray();s.tailMorph=clamp(progress);s.trims[0]=clamp(progress);for(i in 1..10)s.trims[i]=clamp((progress-LacedBootStudioGeometry.row(i)-(if(i%2==0).5 else 0.0))*2);for(i in 0..10)s.slacks[i]=7.5*(1-s.tensions[LacedBootStudioGeometry.row(i)])+(if(i==0)0.0 else 2*sin(PI*s.trims[i]));updateTip(s);return s}
 fun rest()=canonical(0.0,DoubleArray(6){.15})
 fun finalScene()=canonical(6.0,DoubleArray(6){1.0})
 fun authored(time:Double):LacedBootStudioScene{val t=max(0.0,time)%LOOP;var p=0.0
  if(t>=1&&t<5.3){for(r in 0..5){if(t<1+.48*r)break;p=max(p,r+smooth((t-1-.48*r)/.40))};p=min(6.0,p)}else if(t>=5.3&&t<7.55)p=6*(1-smooth((t-5.3)/2.25))
  var ts=DoubleArray(6){.15};if(t>=4.1&&t<5){for(r in 0..5){val d=.035*(5-r);val u=clamp((t-4.1-d)/(.62-d));ts[r]=.15+.85*(1-(1-u).pow(3))}}else if(t>=5&&t<5.3)ts=DoubleArray(6){1-.85*smooth((t-5)/.3)}
  val s=canonical(p,ts);if(t>=3.88&&t<4.1){val b=sin(PI*(t-3.88)/.22).pow(2);s.tipBackoff=2.5*b;s.automaticSlack=DoubleArray(11){2.625*b};for(i in 0..10)s.slacks[i]+=s.automaticSlack[i]}
  if(t>=4.1&&t<=4.72)s.automaticAgletY=-.9*sin(PI*(t-4.1)/.62).pow(2)
  for(r in 0..5){val start=1+.48*r;val ends=if(r==0)doubleArrayOf(start+.4)else doubleArrayOf(start+.2,start+.4);if(ends.any{t>=it&&t<it+.035})s.tipAlpha=0.0}
  if(t>=5.3&&t<7.55)for(half in 2..11){val boundary=half*.5;val v=1-boundary/6;val inverse=.5-sin(asin(1-2*v)/3);if(abs(t-(5.3+2.25*inverse))<.0175)s.tipAlpha=0.0};updateTip(s);return s
 }
 fun held(c:LacedBootStudioScene,progress:Double,tensions:DoubleArray,age:Double,lag:Double,overpull:LacedBootStudioPoint):LacedBootStudioScene{
  if(age==0.0&&progress==c.progress&&tensions.contentEquals(c.tensions)&&lag==c.velocityLag&&overpull==c.overpull)return c.snapshot()
  val r=canonical(progress,tensions);val original=canonical(c.progress,c.tensions);val f=residual(age);val travel=1-smooth(abs(progress-c.progress)/max(.001,min(1.0,if(progress>=c.progress)6-c.progress else c.progress)))
  r.materials=c.materials.copyOf();r.soles=c.soles.copyOf();r.savedMix=c.savedMix;r.spanAlpha=DoubleArray(11){1+(c.spanAlpha[it]-1)*f*travel};r.velocityLag=lag;r.overpull=overpull
  for(i in 0..10){val ri=LacedBootStudioGeometry.row(i);val tc=c.tensions[ri];val current=r.tensions[ri];val edge=if(current>=tc)1-tc else tc;val wt=if(edge<=0)1.0 else 1-smooth(abs(current-tc)/edge);val automatic=c.slacks[i]-original.slacks[i]-c.velocityLag*(1-tc);r.automaticSlack[i]=automatic*wt*f*travel;r.slacks[i]+=r.automaticSlack[i]+lag*(1-current);r.trims[i]+=(c.trims[i]-original.trims[i])*f*travel}
  r.tailMorph+=(c.tailMorph-original.tailMorph)*f*travel;val tc=c.tensions[5];val current=r.tensions[5];val edge=if(current>=tc)1-tc else tc;val wt=if(edge<=0)1.0 else 1-smooth(abs(current-tc)/edge);r.tipBackoff=c.tipBackoff*f*wt*travel;r.automaticAgletY=c.automaticAgletY*f*wt*travel;r.tipAlpha=1+(c.tipAlpha-1)*f*travel;updateTip(r);val originalTip=c.snapshot();updateTip(originalTip);r.tip=LacedBootStudioPoint(r.tip.x+(c.tip.x-originalTip.tip.x)*f*travel,r.tip.y+(c.tip.y-originalTip.tip.y)*f*travel);return r
 }
 fun released(c:LacedBootStudioScene,age:Double,downAge:Double):LacedBootStudioScene{if(age==0.0)return c.snapshot();val r=c.snapshot();val old=residual(downAge);val f=if(old>1e-9)residual(downAge+age)/old else 0.0;val lag=c.velocityLag*(1-smooth(age/.09));for(i in 0..10){r.automaticSlack[i]=c.automaticSlack[i]*f;val w=1-c.tensions[LacedBootStudioGeometry.row(i)];r.slacks[i]=c.slacks[i]-c.automaticSlack[i]*(1-f)-c.velocityLag*w+lag*w};r.velocityLag=lag;r.tipBackoff*=f;r.automaticAgletY*=f;r.tipAlpha=1+(c.tipAlpha-1)*f;r.overpull=point(c.overpull,LacedBootStudioPoint(),smooth(age/.43));updateTip(r);return r}
 fun recovering(c:LacedBootStudioScene,age:Double):LacedBootStudioScene{if(age<=0)return c.snapshot();val u=smooth(age/.6);if(u>=1)return rest().also{it.materials=c.materials.copyOf();it.soles=c.soles.copyOf()};val ts=DoubleArray(6){c.tensions[it]+(.15-c.tensions[it])*u};val r=held(c,c.progress*(1-u),ts,0.0,c.velocityLag*(1-u),point(c.overpull,LacedBootStudioPoint(),u));val original=canonical(c.progress,c.tensions);for(i in 0..10){val baseline=7.5*(1-r.tensions[LacedBootStudioGeometry.row(i)])+(if(i==0)0.0 else 2*sin(PI*r.trims[i]));r.slacks[i]=baseline+(c.slacks[i]-original.slacks[i])*(1-u);r.automaticSlack[i]=r.slacks[i]-baseline-r.velocityLag*(1-r.tensions[LacedBootStudioGeometry.row(i)]);r.spanAlpha[i]=c.spanAlpha[i]+(1-c.spanAlpha[i])*u};r.tipBackoff=c.tipBackoff*(1-u);r.automaticAgletY=c.automaticAgletY*(1-u);r.tipAlpha=1+(c.tipAlpha-1)*(1-u);r.savedMix=c.savedMix*(1-u);updateTip(r);return r}
 fun resolving(c:LacedBootStudioScene,rows:Int,age:Double):LacedBootStudioScene{if(age<=0)return c.snapshot();val u=smooth(age/.12);val r=canonical(rows.toDouble(),c.tensions);r.materials=c.materials.copyOf();r.soles=c.soles.copyOf();r.savedMix=c.savedMix;for(i in 0..10){if(r.trims[i]>c.trims[i])r.spanAlpha[i]=u;else if(r.trims[i]<c.trims[i]&&u<1){r.trims[i]=c.trims[i];r.slacks[i]=c.slacks[i];r.spanAlpha[i]=c.spanAlpha[i]*(1-u)}};r.tipAlpha=if(u>=1)1.0 else 0.0;return r}
}
