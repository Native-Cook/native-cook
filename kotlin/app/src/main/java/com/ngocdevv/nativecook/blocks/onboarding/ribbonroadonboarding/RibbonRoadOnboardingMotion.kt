package com.ngocdevv.nativecook.blocks.onboarding.ribbonroadonboarding

import kotlin.math.*

internal object RibbonRoadOnboardingMotion {
    const val LOOP = 6.4
    const val N = 170
    val blue = arrayOf(-0.2 to 0.5,0.04 to 0.49,0.17 to 0.44,0.25 to 0.33,0.37 to 0.29,0.47 to 0.38,0.5 to 0.54,0.6 to 0.66,0.74 to 0.62,0.8 to 0.44,0.86 to 0.26,0.95 to 0.14,1.15 to 0.1)
    val white = arrayOf(-0.2 to 0.62,0.06 to 0.61,0.15 to 0.55,0.2 to 0.42,0.31 to 0.4,0.41 to 0.5,0.45 to 0.64,0.57 to 0.73,0.72 to 0.71,0.83 to 0.57,0.9 to 0.4,0.98 to 0.27,1.15 to 0.22)
    fun seg(t: Double,a: Double,b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun smooth(x: Double): Double { val k=x.coerceIn(0.0,1.0); return k*k*(3-2*k) }
    fun easeInOut(x: Double) = if(x<0.5)4*x*x*x else 1-(-2*x+2).pow(3)/2
    fun line(points: Array<Pair<Double,Double>>,w: Double,h: Double): Array<DoubleArray> {
        val p=points.map { doubleArrayOf(it.first*w,it.second*h) }
        val dense=mutableListOf<DoubleArray>()
        for(i in 0 until p.size-1) {
            val a=p[max(0,i-1)]; val b=p[i]; val c=p[i+1]; val d=p[min(p.size-1,i+2)]
            for(k in 0 until 24) {
                val t=k/24.0; val t2=t*t; val t3=t2*t
                dense.add(DoubleArray(2) { j -> 0.5*(2*b[j]+(-a[j]+c[j])*t+(2*a[j]-5*b[j]+4*c[j]-d[j])*t2+(-a[j]+3*b[j]-3*c[j]+d[j])*t3) })
            }
        }
        dense.add(p.last())
        val acc=DoubleArray(dense.size)
        for(i in 1 until dense.size)acc[i]=acc[i-1]+hypot(dense[i][0]-dense[i-1][0],dense[i][1]-dense[i-1][1])
        var j=0
        return Array(N) { i ->
            val s=acc.last()*i/(N-1)
            while(j<acc.size-2 && acc[j+1]<s)j++
            val k=(s-acc[j])/max(1e-6,acc[j+1]-acc[j])
            DoubleArray(2) { axis -> dense[j][axis]+(dense[j+1][axis]-dense[j][axis])*k }
        }
    }
}

internal class RibbonRoadOnboardingField {
    val ox=DoubleArray(340); val oy=DoubleArray(340); val vx=DoubleArray(340); val vy=DoubleArray(340)
    fun reset() { ox.fill(0.0); oy.fill(0.0); vx.fill(0.0); vy.fill(0.0) }
    fun step(grab: Int,gi: Int,dx: Double,dy: Double,hold: Double,dt: Double) {
        val len=hypot(dx,dy); val sc=if(len>0.001)110*tanh(len/110)/len else 0.0
        val px=dx*sc*hold; val py=dy*sc*hold
        val n=max(1,ceil(dt/(1.0/120)).toInt()); val h=dt/n
        repeat(n) {
            for(i in 0 until 340) {
                val r=if(i<170)0 else 1
                var tx=0.0; var ty=0.0; var k=260.0; var z=0.28
                if(r==grab && hold>0.001) {
                    val u=(i-r*170-gi)/16.0; val g=exp(-u*u)
                    tx=px*g; ty=py*g; k=900.0; z=0.9
                }
                val c=2*sqrt(k)*z
                vx[i]+=(k*(tx-ox[i])-c*vx[i])*h; vy[i]+=(k*(ty-oy[i])-c*vy[i])*h
                ox[i]+=vx[i]*h; oy[i]+=vy[i]*h
            }
        }
    }
}
