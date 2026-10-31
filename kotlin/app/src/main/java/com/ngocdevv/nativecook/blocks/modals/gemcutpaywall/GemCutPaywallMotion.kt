package com.ngocdevv.nativecook.blocks.modals.gemcutpaywall

import kotlin.math.*

internal object GemCutPaywallMotion {
    const val LOOP = 10.0
    const val IDLE_SPIN = 0.3
    const val PITCH = 0.45
    fun clamp01(x: Double) = x.coerceIn(0.0,1.0)
    fun seg(t: Double,a: Double,b: Double) = clamp01((t-a)/(b-a))
    fun easeInOut(x: Double) = if(x<0.5) 4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun easeQuadratic(x: Double) = if(x<0.5)2*x*x else 1-(-2*x+2).pow(2)/2
    fun easeOut(x: Double) = 1-(1-x).pow(3)
    data class Frame(var cut: Double=0.0,var yaw: Double=0.0,var pitch: Double=PITCH,var spark: Double=0.0,var finger: Double=0.0,var fingerOn: Int=0,var press: Double=0.0)
    private fun pulse(t: Double,at: Double,amp: Double): Double {
        if(t<at-0.05) return 0.0
        return amp*easeOut(seg(t,at-0.05,at+0.08))*exp(-2.6*max(0.0,t-at-0.08))
    }
    fun frameAt(t: Double): Frame {
        val cut= easeInOut(seg(t,0.5,2.3))-0.5*easeInOut(seg(t,5.68,6.38))-0.5*easeInOut(seg(t,7.48,8.18))
        val k=seg(t,3.4,9.2); val lead=0.06; val x=(k-lead)/(1-lead)
        val g=if(k<lead) 0.5*k*k/lead else 0.5*lead+(1-lead)*(1-(1-x).pow(4))/4
        val yaw=IDLE_SPIN*t+(2*PI-IDLE_SPIN*LOOP)*g/(0.5*lead+(1-lead)/4)
        val pitch=PITCH+0.1*sin(PI*seg(t,3.4,5.0))
        val spark=(pulse(t,2.3,1.0)+pulse(t,6.38,0.45)+pulse(t,8.18,0.18))*(1-seg(t,9.4,10.0))
        var finger: Double; var on=0; var press=0.0
        if(t<2.3+0.4) finger=easeOut(seg(t,0.32,0.5))*(1-easeOut(seg(t,2.35,2.55)))
        else if(t<3.72+0.5) { finger=easeOut(seg(t,3.25,3.4))*(1-easeOut(seg(t,3.72,3.9)));on=1 }
        else {
            val at=if(t<7.4-0.3)5.6 else 7.4; on=if(at==5.6)2 else 3
            finger=easeOut(seg(t,at-0.15,at))*(1-easeOut(seg(t,at+0.12,at+0.3)))
            press=easeOut(seg(t,at,at+0.08))*(1-easeOut(seg(t,at+0.12,at+0.3)))
        }
        return Frame(cut,yaw,pitch,spark,finger,on,press)
    }
    fun flickOffset(t: Double) = -0.35+0.9*easeInOut(seg(t,3.4,3.72))
    private fun rx(v: DoubleArray,a: Double) = doubleArrayOf(v[0],cos(a)*v[1]-sin(a)*v[2],sin(a)*v[1]+cos(a)*v[2])
    private fun ry(v: DoubleArray,a: Double) = doubleArrayOf(cos(a)*v[0]+sin(a)*v[2],v[1],-sin(a)*v[0]+cos(a)*v[2])
    fun glints(yaw: Double,pitch: Double,cut: Double,spark: Double): DoubleArray {
        val result=DoubleArray(27)
        for(i in 0..8) {
            val a=i*PI/4
            val point=if(i<8)doubleArrayOf(0.8*cos(a),0.17,0.8*sin(a)) else doubleArrayOf(0.18,0.316,0.1)
            val normal=if(i<8)doubleArrayOf(0.5664*cos(a),0.8241,0.5664*sin(a)) else doubleArrayOf(0.0,1.0,0.0)
            val p=rx(ry(doubleArrayOf(point[0],point[1]+0.28,point[2]),yaw),pitch);val n=rx(ry(normal,yaw),pitch)
            val d=2*n[2];val reflX=d*n[0];val reflY=d*n[1];val reflZ= -1+d*n[2]
            val top=max(0.0,reflY*0.949+reflZ*0.316);val ring=max(0.0,reflX*0.139+reflY*0.324+reflZ*0.936)
            val strength=min(1.0,(top.pow(24)+ring.pow(60))*(1+2*spark))*cut*cut*(if(n[2]>0.05)1 else 0)
            val k=6/(6-p[2]); result[i*3]=p[0]*k;result[i*3+1]= -p[1]*k;result[i*3+2]=strength
        }
        return result
    }
    fun bezier(x: Double,x1: Double=0.77,y1: Double=0.0,x2: Double=0.175,y2: Double=1.0): Double {
        fun value(t: Double,a: Double,b: Double)=3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t
        var lo=0.0;var hi=1.0
        repeat(24) { val m=(lo+hi)/2;if(value(m,x1,x2)<x)lo=m else hi=m }
        return value((lo+hi)/2,y1,y2)
    }
    // Reanimated 4 duration spring, including its perceptual duration and energy threshold.
    fun spring(t: Double,duration: Double,damping: Double,from: Double,to: Double,velocity: Double): Double {
        val mass=4.0;val threshold=6e-9;val displacement=from-to
        val v=if((to>from&&velocity<0)||(to<from&&velocity>0))0.0 else velocity
        fun energy(x: Double,speed: Double,stiffness: Double)=0.5*stiffness*x*x+0.5*mass*speed*speed
        fun difference(stiffness: Double): Double {
            val omega=sqrt(stiffness/mass)*damping;val settle=duration*1.5;val envelope=exp(-omega*settle)
            val x=(displacement+(v+displacement*omega)*settle)*envelope
            val speed= -omega*x+(v+displacement*omega)*envelope;val initial=energy(displacement,v,stiffness)
            return if(initial==0.0)0.0 else energy(x,speed,stiffness)/initial-threshold
        }
        var low=Math.ulp(1.0);var high=8000.0;var stiffness=(low+high)/2
        val direction=if(difference(high)>=difference(low))1.0 else -1.0;var iterations=100
        while(abs(difference(stiffness))>threshold*1e-3&&iterations>0) {
            iterations--;if(difference(stiffness)*direction<0)low=stiffness else high=stiffness;stiffness=(low+high)/2
        }
        val omega=sqrt(stiffness/mass);val wd=omega*sqrt(1-damping*damping);val envelope=exp(-damping*omega*t)
        val fragment=envelope*(sin(wd*t)*(v+damping*omega*displacement)/wd+displacement*cos(wd*t))
        val speed= -damping*omega*fragment+envelope*(cos(wd*t)*(v+damping*omega*displacement)-wd*displacement*sin(wd*t))
        val initial=energy(displacement,velocity,stiffness)
        return if(initial==0.0||energy(fragment,speed,stiffness)/initial<=threshold)to else to+fragment
    }
}
