package com.ngocdevv.nativecook.blocks.pickers.louvreventthermostat

import kotlin.math.*

internal object LouvreVentThermostatMotion {
    const val LOOP=9.0; const val PITCH=20.8; const val PER_C=41.6
    fun wrap(t:Double)=((t%LOOP)+LOOP)%LOOP
    fun clamp(v:Double,a:Double,b:Double)=v.coerceIn(a,b)
    fun seg(t:Double,a:Double,b:Double)=clamp((t-a)/(b-a),0.0,1.0)
    fun mix(a:Double,b:Double,k:Double)=a+(b-a)*k
    fun smooth(a:Double,b:Double,v:Double):Double { val k=seg(v,a,b); return k*k*(3-2*k) }
    fun out(k:Double)=1-(1-k).pow(3)
    fun inOut(k:Double)=if(k<.5) 4*k*k*k else 1-(-2*k+2).pow(3)/2
    fun rounded(v:Double)=floor(v+.5)
    fun temperature(input:Double):Double {
        val t=wrap(input)
        return when {
            t<.62 -> 24.0
            t<2.1 -> 24+6*inOut(seg(t,.62,2.1))
            t<3.2 -> 30.0
            t<4.5 -> 30-12.5*inOut(seg(t,3.2,4.5))
            t<5.7 -> 17.5
            t<6.9 -> { val x=6*(t-5.7); 17.5+6.5*(1-exp(-x)*(1+x))/(1-exp(-7.2)*8.2) }
            else -> 24.0
        }
    }
    fun velocity(t:Double)=(temperature(t+.004)-temperature(t-.004))/.008*PER_C
    fun heat(t:Double)=(0..<8).sumOf { temperature(t-.06*it) }/8
    fun theta(t:Double,v:Double)=clamp(4+52*(clamp(t,16.0,32.0)-16)/16+22*tanh(v/260),-68.0,68.0)
    fun shut(th:Double,a:Double)=(if(th<0) -1 else 1)*max(abs(th)-a,0.0)
    fun clackDip(t:Double)=if(t<=0||t>.7) 0.0 else 35.25*exp(-18*t)*sin(24*t)
    fun clackDelay(y:Double,dir:Double)=.012*clamp((if(dir>0) y-254 else 722-y)/PITCH,0.0,23.0)
    fun press(y:Double,thumb:Double,pressure:Double):Double { val d=(y-thumb)/PITCH; return 12*pressure*exp(-d*d/4.5) }
    fun clack(t:Double):Pair<Double,Double> {
        var age=9.0; var dir=1.0
        for((start,d) in listOf(2.1 to 1.0,4.5 to -1.0,6.9 to 1.0)) if(t>=start) { age=t-start; dir=d }
        return age to dir
    }
    fun touch(t:Double):Pair<Double,Double> {
        if(t>=.5&&t<2.2) return 430+(temperature(t)-24)*PER_C to if(t<.62) out(seg(t,.5,.62)) else 1-seg(t,2.1,2.18)
        if(t>=3.08&&t<4.6) { val y=690+(temperature(t)-30)*PER_C; return y to (if(t<3.2) out(seg(t,3.08,3.2)) else 1-seg(t,4.5,4.58))*smooth(250.0,320.0,y) }
        if(t>=5.58&&t<6) return 400+(temperature(t)-17.5)*PER_C to if(t<5.7) out(seg(t,5.58,5.7)) else 1-seg(t,5.84,5.94)
        return 488.0 to 0.0
    }
    fun crossing(level:Double,a:Double,b:Double):Double {
        var lo=a; var hi=b; val up=temperature(b)>temperature(a)
        repeat(14) { val m=(lo+hi)/2; val v=temperature(m); if(if(up)v>=level else v<=level)hi=m else lo=m }
        return hi
    }
    fun glint(level:Double,t:Double):Double {
        val t1=temperature(t); val t0=temperature(t-.08)
        if(t1==t0 || !(if(t1>t0) level>t0&&level<=t1 else level<t0&&level>=t1)) return 0.0
        return clamp(1-(t-crossing(level,t-.08,t))/.08,0.0,1.0)
    }
    fun nudge(t:Double):Double {
        var n=0.0
        for(a in listOf(.5,3.08,5.58)) n=max(n,2*out(seg(t,a,a+.12))*(1-inOut(seg(t,a+.12,a+.32))))
        val t1=temperature(t); val t0=temperature(t-.15)
        if(t1!=t0) { val level=if(t1>t0)floor(t1) else ceil(t1); if(if(t1>t0)level>t0 else level<t0) { val tc=crossing(level,t-.15,t); val k=1-(t-tc)/.15; n=max(n,2*k*k*(1-smooth(150.0,420.0,abs(velocity(tc))))) } }
        return n
    }
    fun convectionRate(h:Double)=if(h>=24) mix(60.0,220.0,clamp((h-24)/6,0.0,1.0))/90 else mix(60.0,-80.0,clamp((24-h)/6.5,0.0,1.0))/90
    private val convection:List<Double> = run {
        val values=mutableListOf(0.0); var sum=0.0
        repeat(450) { sum+=convectionRate(heat((it+.5)*LOOP/450))*LOOP/450; values.add(sum) }
        val whole=max(1.0,rounded(sum)); values.map { it*whole/sum }
    }
    fun conv(t:Double):Double { val u=wrap(t)/LOOP*450; val i=min(floor(u).toInt(),449); return mix(convection[i],convection[i+1],u-i) }
    fun drums(t:Double):DoubleArray {
        val q=2*(t-16); val n=floor(q); val d=n+smooth(.4,.6,q-n); val m=floor(d)
        val units=16+floor(m/2)+(if(m.toInt()%2!=0)d-m else 0.0)
        val tens=floor(units/10)+(if(floor(units).toInt()%10==9)units-floor(units) else 0.0)
        return doubleArrayOf(d-2*floor(d/2),units,tens)
    }
    data class Scene(
        val temperature:Double=24.0,val velocity:Double=0.0,val s0:Double=16.0,val k0:Double=3.0,
        val blades:DoubleArray=DoubleArray(52),val heat:Double=24.0,val conv:Double=0.0,val nudge:Double=0.0,
        val breath:Double=0.0,val time:Double=0.0,val drums:DoubleArray=doubleArrayOf(0.0,24.0,2.0)
    )
    data class Row(val s0:Double,val k0:Double,val blades:DoubleArray)
    fun row(t:Double,thetaAt:(Double)->Double,glintAt:(Double)->Double,thumb:Double,pressure:Double,clackAge:Double,clackDirection:Double,slam:Double):Row {
        val s0=2*(t-16); val k0=floor(s0-(722-488)/PITCH)-1; val blades=DoubleArray(52)
        repeat(26) { i ->
            val k=k0+i; val y=488-(k-s0)*PITCH
            var th=thetaAt(abs(k-s0)); th=if(slam>0)mix(th,68.0,slam) else if(slam<0)mix(th,0.0,-slam) else th
            th=shut(th,press(y,thumb,pressure)); val tau=clackAge-clackDelay(y,clackDirection); th=shut(th,clackDip(tau))
            val cg=if(tau>0&&tau<.4).9*exp(-tau/.06) else 0.0
            blades[i*2]=th; blades[i*2+1]=max(cg,glintAt(16+k/2))
        }
        return Row(s0,k0,blades)
    }
    fun scene(input:Double):Scene {
        val t=wrap(input); val temp=temperature(t); val thumb=touch(t); val cl=clack(t)
        val row=row(temp,{ val td=t-.016*it; theta(temperature(td),velocity(td)) },{glint(it,t)},thumb.first,thumb.second,cl.first,cl.second,0.0)
        return Scene(temp,velocity(t),row.s0,row.k0,row.blades,heat(t),conv(t),nudge(t),3*sin(2*PI*3*t/LOOP)+(if(t>=2.5&&t<3.2)8*sin(2*PI*seg(t,2.5,3.2)) else 0.0),t,drums(temp))
    }
    fun spring(t:Double,from:Double,to:Double,velocity:Double,damping:Double,stiffness:Double,mass:Double=4.0):Double {
        val w=sqrt(stiffness/mass); val z=damping/(2*sqrt(stiffness*mass)); val x=from-to
        if(z<1) { val wd=w*sqrt(1-z*z); return to+exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/wd*sin(wd*t)) }
        val ss=sqrt(max(0.0,z*z-1)); val r1=-w*(z-ss); val r2=-w*(z+ss)
        if(abs(r1-r2)<.000001) return to+exp(-w*t)*(x+(velocity+w*x)*t)
        val c2=(velocity-r1*x)/(r2-r1); return to+(x-c2)*exp(r1*t)+c2*exp(r2*t)
    }
}
