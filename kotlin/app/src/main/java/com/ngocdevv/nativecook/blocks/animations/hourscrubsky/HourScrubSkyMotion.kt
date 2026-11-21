package com.ngocdevv.nativecook.blocks.animations.hourscrubsky
import kotlin.math.*

internal object HourScrubSkyMotion {
 const val LOOP=12.0
 fun clamp(x:Double)=x.coerceIn(0.0,1.0)
 fun seg(t:Double,a:Double,b:Double)=clamp((t-a)/(b-a))
 fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
 fun smooth(a:Double,b:Double,x:Double):Double { val k=seg(x,a,b);return k*k*(3-2*k) }
 fun out(k:Double)=1-(1-k).pow(3)
 fun inside(k:Double)=k*k*k
 fun inOut(k:Double)=if(k<.5)4*k*k*k else 1-(-2*k+2).pow(3)/2
 fun spring(t:Double,from:Double,to:Double,v:Double,d:Double,k:Double):Double {
  if(t<=0)return from;val x=from-to;val w=sqrt(k);val z=d/(2*w)
  if(z<1) { val wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t)) }
  val s=sqrt(max(z*z-1,1e-6));val r1=-w*(z-s);val r2=-w*(z+s);val c2=(v-r1*x)/(r2-r1);return to+(x-c2)*exp(r1*t)+c2*exp(r2*t)
 }
 private val knots=arrayOf(doubleArrayOf(.3,0.0,0.0),doubleArrayOf(.6,.4,2.6),doubleArrayOf(2.4,5.0,2.4),doubleArrayOf(3.0,5.75,1.2),doubleArrayOf(4.0,6.5,.7),doubleArrayOf(4.6,7.25,2.0),doubleArrayOf(5.55,12.25,6.5),doubleArrayOf(6.6,17.25,1.4),doubleArrayOf(8.3,19.0,1.0),doubleArrayOf(8.7,19.8,2.9),doubleArrayOf(10.1,23.6,2.9),doubleArrayOf(10.4,24.0,0.0))
 fun hour(t:Double):Pair<Double,Double> {
  if(t<=.3)return 0.0 to 0.0;if(t>=10.4)return 24.0 to 0.0;var i=0;while(t>knots[i+1][0])i++
  val a=knots[i];val b=knots[i+1];val dt=b[0]-a[0];val s=(t-a[0])/dt;val s2=s*s;val s3=s2*s
  val h=(2*s3-3*s2+1)*a[1]+(s3-2*s2+s)*dt*a[2]+(-2*s3+3*s2)*b[1]+(s3-s2)*dt*b[2]
  val v=((6*s2-6*s)*a[1]+(3*s2-4*s+1)*dt*a[2]+(-6*s2+6*s)*b[1]+(3*s2-2*s)*dt*b[2])/dt;return h to v
 }
 fun timeForHour(h:Double):Double { if(h<=0)return .2;var a=.3;var b=10.4;repeat(30) { val m=(a+b)/2;if(hour(m).first<h)a=m else b=m };return (a+b)/2 }
 fun frame(input:Double):HourScrubSkyFrame {
  val t=((input%12)+12)%12;val hr=hour(t)
  val hp=if(t<11.2)hr.first else if(t<11.8)max(0.0,spring(t-11.2,24.0,0.0,0.0,22.1,157.9)) else 0.0
  val cs=if(t<.3)0.0 else min(t-.3,.5);val rs=if(t<10.6)0.0 else min(t-10.6,.8)
  var pill=0.0;var disc=1.0
  if(t>=.3 && t<11.2)pill=spring(t-.3,0.0,1.0,0.0,19.0,195.0) else if(t>=11.2 && t<11.8)pill=max(0.0,spring(t-11.2,1.0,0.0,0.0,22.1,157.9))
  val dark=if(t<11.2)seg(t,.3,.46) else 1-seg(t,11.2,11.36)
  if(t>=.2 && t<.3)disc=mix(1.0,.9,inside(seg(t,.2,.3))) else if(t>=.3 && t<1.2)disc=spring(t-.3,.9,1.0,0.0,19.0,195.0) else if(t>=10.4 && t<10.7)disc=1+.06*sin(PI*seg(t,10.4,10.7))
  val play=if(t<.2)1.0 else if(t<.3)1-inside(seg(t,.2,.3)) else if(t<11.3)0.0 else clamp(spring(t-11.3,0.0,1.0,0.0,19.0,195.0))
  val pause=if(t<.3)0.0 else if(t<10.4)clamp(spring(t-.3,0.0,1.0,0.0,19.0,195.0)) else if(t<10.5)1-inside(seg(t,10.4,10.5)) else 0.0
  val reset=if(t<10.4)0.0 else if(t<11.2)clamp(spring(t-10.4,0.0,1.0,0.0,30.5,322.3)) else if(t<11.3)1-inside(seg(t,11.2,11.3)) else 0.0
  val rot=if(t<10.4)-90.0 else if(t<11.3)spring(t-10.4,-90.0,0.0,0.0,30.5,322.3) else 0.0
  fun pulse(a:Double)=if(t>=a && t<a+.36)sin(PI*seg(t,a,a+.36)) else 0.0
  return HourScrubSkyFrame(hr.first,hr.second,hp,cs,rs,pill,dark,disc,play,pause,reset,rot,max(pulse(1.4),pulse(9.45)),t)
 }
 fun sun(h:Double):Pair<Double,Double> { val a=(h-.5)/12*PI;return (201+135*sin(a)) to (540-450*cos(a)) }
 fun front(h:Double):Double {
  if(h<5.5)return -210.0;if(h<6.5) { val s=h-5.5;val s2=s*s;val s3=s2*s;return (2*s3-3*s2+1)*(-210)+(s3-2*s2+s)*1300+(-2*s3+3*s2)*536+(s3-s2)*250 }
  if(h<7)return 536+250*(h-6.5);if(h<7.25)return 661+960*(h-7);if(h<17.25)return 1400.0;if(h<19)return mix(1100.0,-210.0,(h-17.25)/1.75);return -210.0
 }
 private val temps=arrayOf(doubleArrayOf(0.0,29.0),doubleArrayOf(3.0,30.0),doubleArrayOf(6.6,28.0),doubleArrayOf(7.4,27.0),doubleArrayOf(9.0,26.0),doubleArrayOf(12.0,24.0),doubleArrayOf(15.0,22.0),doubleArrayOf(18.0,22.0),doubleArrayOf(20.0,24.0),doubleArrayOf(22.0,26.0),doubleArrayOf(24.0,29.0))
 fun temp(hour:Double):Double { val h=hour.coerceIn(0.0,24.0);var i=0;while(i<temps.size-2 && h>temps[i+1][0])i++;val a=temps[i];val b=temps[i+1];return mix(a[1],b[1],seg(h,a[0],b[0])) }
 private val conditions=arrayOf(doubleArrayOf(0.0,0.0),doubleArrayOf(3.25,1.0),doubleArrayOf(5.25,0.0),doubleArrayOf(9.25,1.0),doubleArrayOf(14.25,0.0),doubleArrayOf(17.25,2.0),doubleArrayOf(19.25,0.0))
 fun conditionRoll(h:Double):DoubleArray { val out=doubleArrayOf(9.0,9.0,9.0);var cur=0;for(i in 1 until conditions.size)if(h>=conditions[i][0]-.15)cur=conditions[i][1].toInt();for(i in 1 until conditions.size) { val hk=conditions[i][0];if(abs(h-hk)<.15) { val k=inOut(seg(h,hk-.15,hk+.15));out[conditions[i-1][1].toInt()]=-k;out[conditions[i][1].toInt()]=1-k;return out } };out[cur]=0.0;return out }
 fun pillLabel(i:Int):String { val clock=(12+i)%24;val hr=if(clock%12==0)12 else clock%12;return (if(12+i>=24)"Tom " else "")+hr+(if(clock<12)" AM" else " PM") }
}
