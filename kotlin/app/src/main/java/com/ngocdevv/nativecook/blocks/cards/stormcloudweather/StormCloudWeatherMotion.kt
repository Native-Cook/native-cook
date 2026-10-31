package com.ngocdevv.nativecook.blocks.cards.stormcloudweather

import kotlin.math.*

internal object StormCloudWeatherMotion {
    const val LOOP = 9.0
    const val MORPH = 0.8
    data class Weather(val cloud: DoubleArray, val moon: Double, val sun: Double, val bolt: Double, val rain: Double, val puff: Double)
    data class Copy(val label: String, val temp: Int, val wind: Int, val humidity: Int, val sky: String)
    val states = arrayOf(
        Weather(doubleArrayOf(.21,.22,.26),1.0,0.0,1.0,1.0,0.0),
        Weather(doubleArrayOf(.86,.89,.95),0.0,0.0,0.0,1.0,.6),
        Weather(doubleArrayOf(.95,.96,.99),0.0,1.0,0.0,0.0,1.0))
    val copy = arrayOf(Copy("Now",26,14,82,"Thunderstorm"), Copy("2:00",28,9,74,"Showers"),Copy("4:00",34,6,52,"Partly sunny"))
    fun mix(a:Double,b:Double,k:Double) = a+(b-a)*k
    fun ease(k:Double):Double { val x=k.coerceIn(0.0,1.0);return if(x<.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2 }
    fun blend(from:Int,to:Int,k:Double):Weather {
        val a=states[from];val b=states[to]
        return Weather(DoubleArray(3){mix(a.cloud[it],b.cloud[it],k)},mix(a.moon,b.moon,k),mix(a.sun,b.sun,k),mix(a.bolt,b.bolt,k),mix(a.rain,b.rain,k),mix(a.puff,b.puff,k))
    }
    data class Frame(val from:Int=0,val to:Int=0,val m:Double=1.0,val yaw:Double=0.0,val time:Double=0.0,val phase:Double=0.0,val flash:Double=0.0) {
        val weather get()=blend(from,to,m)
        fun number(get:(Copy)->Int)=floor(mix(get(copy[from]).toDouble(),get(copy[to]).toDouble(),m)+.5).toInt()
    }
    fun at(t:Double,reduced:Boolean=false):Frame {
        val from:Int;val to:Int;val m:Double
        when {t>=8->{from=2;to=0;m=ease((t-8)/MORPH)};t>=5.6->{from=1;to=2;m=ease((t-5.6)/MORPH)};t>=2.6->{from=0;to=1;m=ease((t-2.6)/MORPH)};else->{from=0;to=0;m=1.0}}
        return Frame(from,to,m,if(reduced)0.0 else .32*sin(2*PI*t/LOOP),t,t,if(reduced)0.0 else exp(-((t-1.2)/.06).pow(2))+.6*exp(-((t-1.38)/.05).pow(2)))
    }
}

internal class StormCloudWeatherDriver {
    var now=0.0;var time=0.0;var last= -10.0
    var from=0;var to=0;var m=1.0;var morphAt= -10.0;var flash=0.0;var nextFlash=1.2
    var yaw=0.0;var velocity=0.0;var startYaw=0.0;var held=false;var pressed= -1
    fun step(elapsed:Double,autoPlay:Boolean,reduced:Boolean,cue:(String)->Unit):StormCloudWeatherMotion.Frame {
        val dt=elapsed.coerceAtMost(.05);now+=elapsed
        val auto=autoPlay&&!reduced
        if(auto&&!held&&now-last>=2){
            time=(time+elapsed)%StormCloudWeatherMotion.LOOP
            val f=StormCloudWeatherMotion.at(time,reduced)
            from=f.from;to=f.to;m=f.m;flash=f.flash;yaw=f.yaw
        }else{
            m=if(reduced)1.0 else StormCloudWeatherMotion.ease((now-morphAt)/.8)
            if(to==0&&!reduced&&now>nextFlash){nextFlash=now+3.2+Math.random()*2;flash=1.0;cue("thunder")}
            flash*=exp(-dt*9)
            if(!held){velocity+=(-120*yaw-2*sqrt(120.0)*.35*velocity)*dt;yaw+=velocity*dt}
        }
        return StormCloudWeatherMotion.Frame(from,to,m,yaw,time+if(auto)0.0 else now,if(auto)time else now,if(to==0)flash else 0.0)
    }
    fun pick(i:Int,reduced:Boolean,cue:(String)->Unit,buzz:()->Unit){
        last=now;if(i==to)return
        from=if(m>=.5)to else from;to=i;morphAt=now;m=if(reduced)1.0 else 0.0;nextFlash=now+1.2
        if(!reduced)cue("morph");buzz()
    }
    fun begin(){held=true;startYaw=yaw;last=now}
    fun drag(dx:Double){last=now;yaw=(startYaw+dx/220).coerceIn(-.9,.9)}
    fun end(vx:Double){held=false;velocity=vx/220;last=now}
}
