package com.ngocdevv.nativecook.blocks.cards.lenticulareditionpass

import kotlin.math.*

internal data class LenticularEditionPassPoint(val x: Double=0.0,val y: Double=0.0)
internal data class LenticularEditionPassPose(val yaw: Double,val pitch: Double,val roll: Double) {
    operator fun plus(b: LenticularEditionPassPose)=LenticularEditionPassPose(yaw+b.yaw,pitch+b.pitch,roll+b.roll)
    operator fun minus(b: LenticularEditionPassPose)=LenticularEditionPassPose(yaw-b.yaw,pitch-b.pitch,roll-b.roll)
    operator fun times(b: Double)=LenticularEditionPassPose(yaw*b,pitch*b,roll*b)
    companion object {
        val Zero=LenticularEditionPassPose(0.0,0.0,0.0)
        val Rest=LenticularEditionPassPose(-9.0,3.0,-2.0)
    }
}
internal data class LenticularEditionPassFrame(val pose: LenticularEditionPassPose,
    val angularVelocity: LenticularEditionPassPose=LenticularEditionPassPose.Zero,
    val explicitViewB: Double?=null,val collectionMix: Double=0.0)

internal object LenticularEditionPassGeometry {
    const val width=333.0;const val height=211.0;const val focal=800.0
    val center=LenticularEditionPassPoint(196.5,366.5)
    val localCorners=listOf(LenticularEditionPassPoint(-166.5,-105.5),LenticularEditionPassPoint(166.5,-105.5),
        LenticularEditionPassPoint(166.5,105.5),LenticularEditionPassPoint(-166.5,105.5))
    val ridgeTerms=DoubleArray(230) { j -> val u=(j+.5)*1.45-166.5;.035*u+.8*sin(2*PI*u/333) }
    /** Row-major Rz * Rx * Ry, shared by projection, inverse hit, and the optical shader. */
    fun rotation(pose: LenticularEditionPassPose): DoubleArray {
        val y=pose.yaw*PI/180;val p=pose.pitch*PI/180;val r=pose.roll*PI/180
        val cy=cos(y);val sy=sin(y);val cp=cos(p);val sp=sin(p);val cr=cos(r);val sr=sin(r)
        return doubleArrayOf(cr*cy-sr*sp*sy,-sr*cp,cr*sy+sr*sp*cy,
            sr*cy+cr*sp*sy,cr*cp,sr*sy-cr*sp*cy,-cp*sy,sp,cp*cy)
    }
    fun bow(p: LenticularEditionPassPoint)=3.5*(1-(2*p.x/width).pow(2))*(1-(2*p.y/height).pow(2))
    fun project(local: LenticularEditionPassPoint,pose: LenticularEditionPassPose,depth: Double?=null): LenticularEditionPassPoint {
        val m=rotation(pose);val z=depth?:bow(local)
        val x=m[0]*local.x+m[1]*local.y+m[2]*z;val y=m[3]*local.x+m[4]*local.y+m[5]*z
        val worldZ=m[6]*local.x+m[7]*local.y+m[8]*z;val scale=focal/(focal-worldZ)
        return LenticularEditionPassPoint(center.x+x*scale,center.y+y*scale)
    }
    fun corners(pose: LenticularEditionPassPose)=localCorners.map {project(it,pose,0.0)}
    fun valid(pose: LenticularEditionPassPose)=corners(pose).all {it.x.isFinite()&&it.y.isFinite()&&it.x>=21.5&&it.x<=371.5&&it.y>=242.5&&it.y<=507.5}
    fun constrain(desired: LenticularEditionPassPose): LenticularEditionPassPose {
        if(valid(desired)) return desired
        var low=0.0;var high=1.0
        repeat(12) { val q=(low+high)/2;if(valid(LenticularEditionPassPose.Rest+(desired-LenticularEditionPassPose.Rest)*q)) low=q else high=q }
        return LenticularEditionPassPose.Rest+(desired-LenticularEditionPassPose.Rest)*low
    }
    fun actualVelocity(desired: LenticularEditionPassPose,velocity: LenticularEditionPassPose): LenticularEditionPassPose {
        if(valid(desired)) return velocity
        val h=.0001
        return (constrain(desired+velocity*h)-constrain(desired-velocity*h))*(1/(2*h))
    }
    fun inverseHit(screen: LenticularEditionPassPoint,pose: LenticularEditionPassPose): LenticularEditionPassPoint? {
        val m=rotation(pose);val px=(screen.x-center.x)/focal;val py=(screen.y-center.y)/focal
        val cx=m[6]*focal;val cy=m[7]*focal;val cz=m[8]*focal
        val dx=m[0]*px+m[3]*py-m[6];val dy=m[1]*px+m[4]*py-m[7];val dz=m[2]*px+m[5]*py-m[8]
        if(abs(dz)<=.0001) return null
        val plane=-cz/dz;var t=plane
        repeat(6) {
            val x=cx+t*dx;val y=cy+t*dy;val z=cz+t*dz
            val a=1-(2*x/width).pow(2);val b=1-(2*y/height).pow(2)
            val bx=-28*x/(width*width)*b;val by=-28*y/(height*height)*a
            val derivative=dz-bx*dx-by*dy
            if(abs(derivative)<=.0001) return null
            t=min(plane+8,max(plane-8,t-(z-3.5*a*b)/derivative))
        }
        val point=LenticularEditionPassPoint(cx+t*dx,cy+t*dy)
        val qx=abs(point.x)-(width/2-6);val qy=abs(point.y)-(height/2-6)
        val distance=hypot(max(qx,0.0),max(qy,0.0))+min(max(qx,qy),0.0)-6
        return if(t>0&&distance<=0) point else null
    }
    fun ridgeWeight(index: Int,frame: LenticularEditionPassFrame)=frame.explicitViewB?.let {LenticularEditionPassMath.clamp(it)}
        ?:LenticularEditionPassMath.smooth((frame.pose.yaw+ridgeTerms[index]+.7)/1.4)
    fun meanViewB(frame: LenticularEditionPassFrame)=frame.explicitViewB?.let {LenticularEditionPassMath.clamp(it)}
        ?:ridgeTerms.sumOf {LenticularEditionPassMath.smooth((frame.pose.yaw+it+.7)/1.4)}/230
}

internal data class LenticularEditionPassSupportSample(val latent: Double,val latentVelocity: Double,val angle: Double,val velocity: Double) {
    companion object {val Zero=LenticularEditionPassSupportSample(0.0,0.0,0.0,0.0)}
}
/** Exact cubic-forced latent ODE, sampled analytically with a finite .60-second tail. */
internal data class LenticularEditionPassVelocitySupport(val q0: Double=0.0,val dq0: Double=0.0,val k: Double=0.0) {
    private fun forced(a: Double): Pair<Double,Double> {
        val omega=2*PI/.4;val c=.78*omega;val beta=omega*sqrt(1-.78*.78);val w2=omega*omega;val duration=.12
        val b3=2*k/(duration*duration*duration);val b2=-3*k/(duration*duration)-6*c*b3/w2
        val b1=-(4*c*b2+6*b3)/w2;val b0=k-(2*c*b1+2*b2)/w2
        val cc=q0-b0;val d=(dq0-b1+c*cc)/beta;val e=exp(-c*a);val co=cos(beta*a);val si=sin(beta*a)
        return Pair(((b3*a+b2)*a+b1)*a+b0+e*(cc*co+d*si),
            (3*b3*a+2*b2)*a+b1+e*((-c*cc+beta*d)*co+(-c*d-beta*cc)*si))
    }
    fun sample(age: Double): LenticularEditionPassSupportSample {
        if(age>=.60) return LenticularEditionPassSupportSample.Zero
        val q: Double;val v: Double
        if(age<=0) {q=q0;v=dq0}
        else if(age<=.12) {val f=forced(age);q=f.first;v=f.second}
        else {
            val start=forced(.12);val s=age-.12;val omega=2*PI/.4;val c=.78*omega;val beta=omega*sqrt(1-.78*.78)
            val cc=start.first;val d=(start.second+c*cc)/beta;val e=exp(-c*s);val co=cos(beta*s);val si=sin(beta*s)
            q=e*(cc*co+d*si);v=e*((-c*cc+beta*d)*co+(-c*d-beta*cc)*si)
        }
        val u=LenticularEditionPassMath.clamp((age-.40)/.20)
        val envelope=1-u*u*(3-2*u);val rate=if(age>.40) -6*u*(1-u)/.20 else 0.0
        val latent=q*envelope;val derivative=v*envelope+q*rate;val t=tanh(latent)
        return LenticularEditionPassSupportSample(latent,derivative,2*t,2*(1-t*t)*derivative)
    }
    fun retarget(velocity: Double,elapsed: Double): LenticularEditionPassVelocitySupport {
        val current=sample(elapsed)
        return LenticularEditionPassVelocitySupport(current.latent,current.latentVelocity,LenticularEditionPassMath.clamp(velocity,-1200.0,1200.0)/600)
    }
}

internal object LenticularEditionPassMath {
    const val LOOP=8.0
    fun clamp(x: Double,low: Double=0.0,high: Double=1.0)=min(high,max(low,x))
    fun smooth(x: Double): Double {val q=clamp(x);return q*q*(3-2*q)}
    fun smoothRate(x: Double)=if(x>0&&x<1) 6*x*(1-x) else 0.0
    fun ease(x: Double)=curve(x,.2,.8,.2,1.0).first
    fun io(x: Double)=curve(x,.65,0.0,.35,1.0).first
    fun frame(desired: LenticularEditionPassPose,velocity: LenticularEditionPassPose=LenticularEditionPassPose.Zero,
        explicitViewB: Double?=null,collectionMix: Double=0.0)=LenticularEditionPassFrame(
        LenticularEditionPassGeometry.constrain(desired),LenticularEditionPassGeometry.actualVelocity(desired,velocity),explicitViewB,collectionMix)
    fun rest()=LenticularEditionPassFrame(LenticularEditionPassPose.Rest)
    fun authored(time: Double): LenticularEditionPassFrame {
        val t=max(0.0,time)%LOOP
        if(t<=1.10||t>=7.26) return rest()
        fun segment(a: Double,b: Double,start: LenticularEditionPassPose,end: LenticularEditionPassPose,anticipation: Boolean=false): LenticularEditionPassFrame {
            val q=if(anticipation) curve((t-a)/(b-a),.2,.8,.2,1.0) else curve((t-a)/(b-a),.65,0.0,.35,1.0)
            return frame(start+(end-start)*q.first,(end-start)*(q.second/(b-a)))
        }
        val anticipated=LenticularEditionPassPose(-10.5,3.0,-2.0)
        val b=LenticularEditionPassPose(14.0,-4.0,2.0);val a=LenticularEditionPassPose(-14.0,6.0,-3.0)
        if(t<1.23) return segment(1.10,1.23,LenticularEditionPassPose.Rest,anticipated,true)
        if(t<2.80) return segment(1.23,2.80,anticipated,b)
        if(t<3.38) return frame(b)
        if(t<5.16) return segment(3.38,5.16,b,a)
        if(t<5.70) return frame(a)
        if(t<7.08) return segment(5.70,7.08,a,LenticularEditionPassPose(-9.35,3.0,-2.0))
        val s=finiteSpring(-9.35,0.0,-9.0,t-7.08,.22,.86,.18,0.0)
        return frame(LenticularEditionPassPose(s.first,3.0,-2.0),LenticularEditionPassPose(s.second,0.0,0.0))
    }
    fun rubber(x: Double,limit: Double): Double {
        if(abs(x)<=limit) return x
        val over=abs(x)-limit
        return (if(x<0) -1 else 1)*(limit+over*.35/(1+over/8))
    }
    fun rubberRate(x: Double,limit: Double)=if(abs(x)<=limit) 1.0 else .35/(1+(abs(x)-limit)/8).pow(2)
    private fun dragAxis(value: Double,delta: Double,limit: Double)=rubber(value+delta,limit)+(value-rubber(value,limit))*(1-smooth(abs(delta)/8))
    private fun dragAxisRate(value: Double,delta: Double,limit: Double): Double {
        val direction=if(delta==0.0) 0.0 else if(delta<0) -1.0 else 1.0
        return rubberRate(value+delta,limit)-(value-rubber(value,limit))*smoothRate(abs(delta)/8)*direction/8
    }
    private fun dragDesired(base: LenticularEditionPassFrame,translation: LenticularEditionPassPoint,yawExtra: Double,pitchExtra: Double)=LenticularEditionPassPose(
        dragAxis(base.pose.yaw,.13*translation.x,18.0)+yawExtra,dragAxis(base.pose.pitch,-.08*translation.y,10.0)+pitchExtra,
        dragAxis(base.pose.roll,.02*translation.x,4.0))
    fun dragged(base: LenticularEditionPassFrame,translation: LenticularEditionPassPoint,yawSupport: LenticularEditionPassVelocitySupport,
        pitchSupport: LenticularEditionPassVelocitySupport,supportAge: Double): LenticularEditionPassFrame {
        val sy=yawSupport.sample(supportAge);val sp=pitchSupport.sample(supportAge)
        return frame(dragDesired(base,translation,sy.angle,sp.angle),LenticularEditionPassPose(sy.velocity,sp.velocity,0.0),base.explicitViewB,base.collectionMix)
    }
    fun releaseVelocity(base: LenticularEditionPassFrame,translation: LenticularEditionPassPoint,pointer: LenticularEditionPassPoint,candidateAge: Double,
        inherited: LenticularEditionPassPose?,yawSupport: LenticularEditionPassVelocitySupport,pitchSupport: LenticularEditionPassVelocitySupport,
        supportAge: Double): LenticularEditionPassPose {
        val age=1-smooth(candidateAge/.12);val sy=yawSupport.sample(supportAge);val sp=pitchSupport.sample(supportAge)
        val user=(inherited?:LenticularEditionPassPose(
            .13*clamp(pointer.x,-1200.0,1200.0)*dragAxisRate(base.pose.yaw,.13*translation.x,18.0),
            -.08*clamp(pointer.y,-1200.0,1200.0)*dragAxisRate(base.pose.pitch,-.08*translation.y,10.0),
            .02*clamp(pointer.x,-1200.0,1200.0)*dragAxisRate(base.pose.roll,.02*translation.x,4.0)))*age
        return LenticularEditionPassGeometry.actualVelocity(dragDesired(base,translation,sy.angle,sp.angle),user+LenticularEditionPassPose(sy.velocity,sp.velocity,0.0))
    }
    fun targetView(frame: LenticularEditionPassFrame,pointer: LenticularEditionPassPoint): Int {
        val requested=frame.pose+LenticularEditionPassPose(.13*clamp(pointer.x,-1200.0,1200.0)*.08,
            -.08*clamp(pointer.y,-1200.0,1200.0)*.08,.02*clamp(pointer.x,-1200.0,1200.0)*.08)
        val projected=LenticularEditionPassGeometry.constrain(requested)
        if(projected.yaw>0) return 1
        if(projected.yaw<0) return 0
        return if(LenticularEditionPassGeometry.meanViewB(frame)>.5) 1 else 0
    }
    fun released(from: LenticularEditionPassFrame,view: Int,elapsed: Double): LenticularEditionPassFrame {
        if(elapsed<=0) return from
        val target=LenticularEditionPassPose(if(view==0) -12.0 else 12.0,3.0,-2.0)
        if(elapsed>=.75) return frame(target,explicitViewB=from.explicitViewB,collectionMix=from.collectionMix)
        val y=finiteSpring(from.pose.yaw,from.angularVelocity.yaw,target.yaw,elapsed,.45,.82,.75,.55)
        val p=finiteSpring(from.pose.pitch,from.angularVelocity.pitch,target.pitch,elapsed,.45,.82,.75,.55)
        val r=finiteSpring(from.pose.roll,from.angularVelocity.roll,target.roll,elapsed,.45,.82,.75,.55)
        return frame(LenticularEditionPassPose(y.first,p.first,r.first),LenticularEditionPassPose(y.second,p.second,r.second),from.explicitViewB,from.collectionMix)
    }
    fun cancelled(from: LenticularEditionPassFrame,base: LenticularEditionPassFrame,elapsed: Double): LenticularEditionPassFrame {
        if(elapsed<=0) return from
        if(elapsed>=.24) return base.copy(angularVelocity=LenticularEditionPassPose.Zero)
        val u=elapsed/.24;val q=smooth(u);val rate=smoothRate(u)/.24;val delta=base.pose-from.pose
        return frame(from.pose+delta*q,delta*rate,from.explicitViewB,from.collectionMix)
    }
    fun recovering(from: LenticularEditionPassFrame,elapsed: Double): LenticularEditionPassFrame {
        if(elapsed<=0) return from
        if(elapsed>=.65) return rest()
        val q=curve(elapsed/.65,.65,0.0,.35,1.0);val delta=LenticularEditionPassPose.Rest-from.pose
        return frame(from.pose+delta*q.first,delta*(q.second/.65),from.explicitViewB?.times(1-q.first),from.collectionMix*(1-q.first))
    }
    fun collecting(from: LenticularEditionPassFrame,elapsed: Double): LenticularEditionPassFrame {
        if(elapsed<=0) return from
        return from.copy(angularVelocity=LenticularEditionPassPose.Zero,collectionMix=from.collectionMix+(1-from.collectionMix)*ease(elapsed/.18))
    }
    fun reduced(from: LenticularEditionPassFrame,view: Double,elapsed: Double): LenticularEditionPassFrame {
        val q=ease(elapsed/.18);val old=from.explicitViewB?:0.0
        return LenticularEditionPassFrame(LenticularEditionPassPose.Zero,explicitViewB=old+(view-old)*q,collectionMix=from.collectionMix)
    }
    fun finiteSpring(start: Double,velocity: Double,target: Double,time: Double,response: Double,damping: Double,end: Double,tailStart: Double): Pair<Double,Double> {
        if(time<=0) return start to velocity
        if(time>=end) return target to 0.0
        val omega=2*PI/response;val c=damping*omega;val beta=omega*sqrt(1-damping*damping);val cc=start-target;val d=(velocity+c*cc)/beta
        val e=exp(-c*time);val co=cos(beta*time);val si=sin(beta*time);val raw=e*(cc*co+d*si)
        val v=e*((-c*cc+beta*d)*co+(-c*d-beta*cc)*si);val u=clamp((time-tailStart)/(end-tailStart))
        val g=1-smooth(u);val dg=-smoothRate(u)/(end-tailStart)
        return target+raw*g to v*g+raw*dg
    }
    private fun curve(x: Double,x1: Double,y1: Double,x2: Double,y2: Double): Pair<Double,Double> {
        fun value(t: Double,a: Double,b: Double): Double {val s=1-t;return 3*s*s*t*a+3*s*t*t*b+t*t*t}
        fun rate(t: Double,a: Double,b: Double)=3*(1-t).pow(2)*a+6*(1-t)*t*(b-a)+3*t*t*(1-b)
        if(x<=0) return 0.0 to y1/x1
        if(x>=1) return 1.0 to (1-y2)/(1-x2)
        var lo=0.0;var hi=1.0
        repeat(26) {val m=(lo+hi)/2;if(value(m,x1,x2)<x) lo=m else hi=m}
        val t=(lo+hi)/2
        return value(t,y1,y2) to rate(t,y1,y2)/rate(t,x1,x2)
    }
}
