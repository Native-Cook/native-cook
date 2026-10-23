package com.ngocdevv.nativecook.blocks.gestures.worldsunprojection

import kotlin.math.*

internal data class WorldSunProjectionPoint(val x:Double,val y:Double)
internal data class WorldSunProjectionVector(val x:Double,val y:Double,val z:Double){
    fun dot(v:WorldSunProjectionVector)=x*v.x+y*v.y+z*v.z
    operator fun plus(v:WorldSunProjectionVector)=WorldSunProjectionVector(x+v.x,y+v.y,z+v.z)
    operator fun times(s:Double)=WorldSunProjectionVector(x*s,y*s,z*s)
    fun unit()=this*(1/max(1e-12,sqrt(dot(this))))
}
internal data class WorldSunProjectionPose(val yaw:Double=0.0,val pitch:Double= -8.5,val q:Double=0.0)
internal class WorldSunProjectionCamera(p:WorldSunProjectionPose){
    private val y=p.yaw*PI/180;private val t=p.pitch*PI/180
    val forward=WorldSunProjectionVector(sin(y)*cos(t),sin(t),cos(y)*cos(t))
    val right=WorldSunProjectionVector(cos(y),0.0,-sin(y))
    val up=WorldSunProjectionVector(-sin(y)*sin(t),cos(t),-cos(y)*sin(t))
    fun project(d:WorldSunProjectionVector):WorldSunProjectionPoint?{val z=d.dot(forward);return if(z<=.06)null else WorldSunProjectionPoint(196.5+510*d.dot(right)/z,426-510*d.dot(up)/z)}
}
internal object WorldSunProjectionMath{
    val rest=WorldSunProjectionPose();val reduced=WorldSunProjectionPose(4.0,20.0,.5)
    val yawSamples=doubleArrayOf(-22.0,-12.0,0.0,4.0,8.0,15.0,22.0)
    val pitchSamples=doubleArrayOf(-14.0,-8.5,4.0,20.0,34.0,48.0,58.0)
    fun clamp(x:Double,lo:Double=0.0,hi:Double=1.0)=x.coerceIn(lo,hi)
    fun smooth(x:Double):Double{val u=clamp(x);return u*u*(3-2*u)}
    fun mix(a:Double,b:Double,u:Double)=a+(b-a)*u
    fun mix(a:WorldSunProjectionPose,b:WorldSunProjectionPose,u:Double)=WorldSunProjectionPose(mix(a.yaw,b.yaw,u),mix(a.pitch,b.pitch,u),mix(a.q,b.q,u))
    fun bounded(p:WorldSunProjectionPose)=WorldSunProjectionPose(clamp(p.yaw,-22.0,22.0),clamp(p.pitch,-14.0,58.0),clamp(p.q))
    fun authored(seconds:Double):WorldSunProjectionPose{val t=max(0.0,seconds)%8.0
        if(t<1||t>=7.55)return rest
        if(t<3){val e=smooth((t-1)/2);return WorldSunProjectionPose(8*e,mix(-8.5,48.0,e),.34*e)}
        if(t<4.9)return WorldSunProjectionPose(8.0,48.0,mix(.34,1.0,smooth((t-3)/1.9)))
        if(t<5.75)return WorldSunProjectionPose(8.0,48.0,1.0)
        return mix(WorldSunProjectionPose(8.0,48.0,1.0),rest,smooth((t-5.75)/1.8))
    }
    fun sun(q:Double):WorldSunProjectionVector{val a=(-6+30*q)*PI/180;val e=(-.5+64*sin(PI*q/2))*PI/180;return WorldSunProjectionVector(sin(a)*cos(e),sin(e),cos(a)*cos(e))}
    fun time(q:Double):String{val minute=309+(clamp(q)*350).roundToInt();return "%02d:%02d".format(java.util.Locale.US,minute/60,minute%60)}
    fun carry(v:Double,age:Double):Double{if(age<=0||age>=.6)return 0.0;val w=2*PI/.35;val z=.85;val wd=w*sqrt(1-z*z);val initial=2*tanh(v/(2*w))*w;val gate=if(age<.42)1.0 else 1-smooth((age-.42)/.18);return initial/wd*exp(-z*w*age)*sin(wd*age)*gate}
    fun released(p:WorldSunProjectionPose,vy:Double,vp:Double,age:Double,reduced:Boolean)=if(reduced)p else bounded(p.copy(yaw=p.yaw+carry(vy,age),pitch=p.pitch+carry(vp,age)))
    fun weights(p:WorldSunProjectionPose):List<Pair<Int,Double>>{
        fun bracket(a:DoubleArray,v:Double):Triple<Int,Int,Double>{val x=clamp(v,a.first(),a.last());if(x<=a.first())return Triple(0,0,0.0);for(i in 1 until a.size)if(x<=a[i])return Triple(i-1,i,(x-a[i-1])/(a[i]-a[i-1]));return Triple(a.lastIndex,a.lastIndex,0.0)}
        val x=bracket(yawSamples,p.yaw);val y=bracket(pitchSamples,p.pitch)
        return listOf(y.first*7+x.first to (1-x.third)*(1-y.third),y.first*7+x.second to x.third*(1-y.third),y.second*7+x.first to (1-x.third)*y.third,y.second*7+x.second to x.third*y.third).filter{it.second>0}
    }
}
