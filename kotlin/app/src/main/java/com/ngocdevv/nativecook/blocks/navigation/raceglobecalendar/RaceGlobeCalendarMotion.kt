package com.ngocdevv.nativecook.blocks.navigation.raceglobecalendar
import kotlin.math.*

internal object RaceGlobeCalendarMotion {
 private val d=RaceGlobeCalendarData
 const val LOOP=10.5;val spinV=1/(.2+.5+.7/3)
 val autoTrack=intArrayOf(14,15,16,21,8,2,18,10,14)
 fun clamp(x:Double)=x.coerceIn(0.0,1.0)
 fun seg(t:Double,a:Double,b:Double)=clamp((t-a)/(b-a))
 fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
 fun smooth(k:Double):Double { val x=clamp(k);return x*x*(3-2*x) }
 fun smoothstep(a:Double,b:Double,v:Double)=smooth((v-a)/(b-a))
 fun out(k:Double)=1-(1-clamp(k)).pow(3)
 fun inOut(k:Double):Double { val x=clamp(k);return if(x<.5)4*x*x*x else 1-(-2*x+2).pow(3)/2 }
 fun sine(k:Double)=(1-cos(PI*clamp(k)))/2
 fun spring(t:Double,from:Double,to:Double,v:Double,d:Double,k:Double):Double { if(t<=0)return from;val x=from-to;val w=sqrt(k);val z=d/(2*w);if(z<1) { val wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t)) };return to+exp(-w*t)*(x+(v+w*x)*t) }
 fun dot(a:DoubleArray,b:DoubleArray)=a[0]*b[0]+a[1]*b[1]+a[2]*b[2]
 fun norm(a:DoubleArray):DoubleArray { val l=sqrt(dot(a,a));return a.map { it/if(l==0.0)1.0 else l }.toDoubleArray() }
 fun slerp(a:DoubleArray,b:DoubleArray,k:Double):DoubleArray { val om=acos(dot(a,b).coerceIn(-1.0,1.0));if(om<1e-5)return a;val ka=sin((1-k)*om)/sin(om);val kb=sin(k*om)/sin(om);return DoubleArray(3) { a[it]*ka+b[it]*kb } }
 fun angle(a:DoubleArray,b:DoubleArray)=acos(dot(a,b).coerceIn(-1.0,1.0))*180/PI
 fun lat(v:DoubleArray)=asin(v[1].coerceIn(-1.0,1.0))*180/PI
 fun lon(v:DoubleArray)=atan2(v[0],v[2])*180/PI
 fun wrap(v:Double)=v-360*floor((v+180)/360)
 fun rows(lat:Double,lon:Double):DoubleArray { val l=lon*PI/180;val b=lat*PI/180-d.beta;val cl=cos(l);val sl=sin(l);val cb=cos(b);val sb=sin(b);return doubleArrayOf(cl,0.0,-sl,-sb*sl,cb,-sb*cl,cb*sl,sb,cb*cl) }
 fun project(m:DoubleArray,p:DoubleArray,radius:Double):DoubleArray { val vx=m[0]*p[0]+m[1]*p[1]+m[2]*p[2];val vy=m[3]*p[0]+m[4]*p[1]+m[5]*p[2];val vz=m[6]*p[0]+m[7]*p[1]+m[8]*p[2];val a=vx*vx+vy*vy+(vz-4).pow(2);val b=8*(vz-4);val disc=b*b-4*a*15;var vis=1.0;if(disc>0) { val t=(-b-sqrt(disc))/(2*a);if(t>0 && t<.9999)vis=0.0 };val f=radius*sqrt(15.0);return doubleArrayOf(201+f*vx/(4-vz),372-f*vy/(4-vz),vz,vis) }
 fun hopDip(hop:Double)=smoothstep(20.0,110.0,hop)
 fun zoom(e:Double,hop:Double)=1-.4*sin(PI*clamp(e))*hopDip(hop)
 fun landing(l:Double):DoubleArray { if(l<0)return doubleArrayOf(0.0,0.0,-1.0,0.0,0.0);val p=spring(l,0.0,1.0,0.0,19.0,195.0);val c=if(l<.12)0.0 else spring(l-.12,0.0,1.0,0.0,19.0,195.0);return doubleArrayOf(-10*(1-p),mix(.6,1.0,p),if(l<.5)l/.5 else -1.0,c,out(seg(l,.22,.6))) }
 fun spinAt(tau:Double):Pair<Double,Double> { val v=spinV;if(tau<=0)return 0.0 to 0.0;if(tau<.4)return v*tau*tau/.8 to v*tau/.4;if(tau<.9)return (v*.2+v*(tau-.4)) to v;if(tau<1.6) { val s=(tau-.9)/.7;return (v*.7+v*.7*(1-(1-s).pow(3))/3) to v*(1-s).pow(2) };return 1.0 to 0.0 }
 fun base(t:Double,a:Double,b:Double,lean:Double)=sine(seg(t,a,b).pow(lean))
 fun flight(t:Double,a:Double,b:Double,hop:Double,lean:Double):Double { val e=base(t,a,b,lean);val s=seg(t,b-.3,b+.25);return e+1.4/hop*sin(PI*s)*(1-s) }
 fun arc(t:Double,a:Double,b:Double,lean:Double):Double { val e=base(t,a,b,lean);return clamp(e+.14*sin(PI*e)) }
 fun page(t:Double,a:Double):Double { val k=seg(t,a,a+.5);return if(t<a+.5).45*k*k else spring(t-a-.5,.45,1.0,1.8,22.1,157.9) }
 fun frame(input:Double):RaceGlobeCalendarFrame {

  val t=((input%LOOP)+LOOP)%LOOP;val drift=1.2*sin(2.0*PI*3.0*t/LOOP)
  val f=RaceGlobeCalendarFrame();f.lat=d.LAT[14];f.lon=d.LON[14];f.sun=d.SUN[14];f.t=t
  if (t<4.3) {
   val e=flight(t,0.7,2.75,d.hop1,1.45);val c=slerp(d.W[14],d.W[15],e);f.lat=lat(c);f.lon=lon(c);f.zoom=zoom(e,d.hop1);f.sun=slerp(d.SUN[14],d.SUN[15],out(seg(t,0.7,2.5)));f.cardPos=if (t<0.7) 0.0 else page(t,0.7);f.arcPos=14.0+arc(t,0.7,2.75,1.45);f.comet=1.0;f.selF=14.0+smooth(seg(t,0.8,2.8));f.from=14;f.to=if (t<0.7) 14 else 15;f.leave=seg(t,0.7,1.0);f.land=if (t<0.7) 9.0 else t-2.8;f.calloutOut=if (t<0.7) seg(t,0.6,0.7) else seg(t,4.2,4.3);f.press=if (t<1.2) seg(t,0.6,0.7) else 1.0-seg(t,1.2,1.32);f.press=max(f.press,seg(t,4.2,4.3));f.sheen=if (t>=2.9) inOut(seg(t,2.9,3.6)) else -1.0;f.sheenSlot=1
  } else if (t<7.1) {
   val e=flight(t,4.3,5.6,d.hop2,1.18);val c=slerp(d.W[15],d.W[16],e);f.lat=lat(c);f.lon=lon(c);f.zoom=zoom(e,d.hop2);f.sun=slerp(d.SUN[15],d.SUN[16],seg(t,4.3,5.6));f.cardPos=1.0+page(t,4.3);f.arcPos=15.0+arc(t,4.3,5.6,1.18);f.comet=1.0;f.selF=15.0+smooth(seg(t,4.4,5.7));f.from=15;f.to=16;f.leave=seg(t,4.3,4.6);f.land=t-5.7;f.calloutOut=seg(t,7.0,7.1);f.press=if (t<4.8) 1.0 else 1.0-seg(t,4.8,4.92);f.sheen=if (t>=5.8) inOut(seg(t,5.8,6.5)) else -1.0;f.sheenSlot=2;f.shuffle=seg(t,7.0,7.06);f.shuffleRot=360.0*clamp(spring(t-7.0,0.0,1.0,0.0,30.5,322.3))
  } else {
   val tau=t-7.1;val sp=spinAt(tau);val sc=spinAt(min(1.6,tau*1.6/1.4));f.lat=mix(d.LAT[16],d.LAT[14],smooth(sp.first));f.lon=d.LON[16]+d.spinDLon*sp.first;f.spin=d.spinDLon*sp.second;f.zoom=1.0-0.3*sp.second/spinV;f.sun=slerp(d.SUN[16],d.SUN[14],sp.first);f.cardPos=2.0+6.0*sc.first;f.arcPos=16.0-2.0*smooth(seg(t,7.15,7.6));f.comet=0.0;f.selF=if (t<7.3) 16.0 else 14.0;f.numbered=if (t<8.5) 1.0-seg(t,7.1,7.3) else seg(t,8.5,8.8);f.from=16;f.to=14;f.leave=seg(t,7.05,7.3);f.land=t-8.62;f.shuffle=1.0-seg(t,7.1,7.25);f.shuffleRot=360.0*clamp(spring(t-7.0,0.0,1.0,0.0,30.5,322.3))
  };f.lon+=drift;return f
 }
}
