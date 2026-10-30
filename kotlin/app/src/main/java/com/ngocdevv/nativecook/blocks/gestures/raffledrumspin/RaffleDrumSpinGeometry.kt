package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

import kotlin.math.*

/** Triangle mesh matching the React Native GPU reference. Eight floats per vertex. */
internal object RaffleDrumSpinGeometry {
    private data class V(val x: Double, val y: Double, val z: Double) {
        operator fun plus(b: V) = V(x+b.x,y+b.y,z+b.z)
        operator fun minus(b: V) = V(x-b.x,y-b.y,z-b.z)
        operator fun times(s: Double) = V(x*s,y*s,z*s)
        fun cross(b: V) = V(y*b.z-z*b.y,z*b.x-x*b.z,x*b.y-y*b.x)
        fun unit(): V { val l = sqrt(x*x+y*y+z*z).let { if (it == 0.0) 1.0 else it }; return this*(1/l) }
        fun length() = sqrt(x*x+y*y+z*z)
    }
    private class Mesh {
        val data = ArrayList<Float>()
        fun tri(a: Pair<V,V>, b: Pair<V,V>, c: Pair<V,V>, m: Int, s: Int) {
            for ((p,n) in listOf(a,b,c)) {
                data.addAll(listOf(p.x.toFloat(),p.y.toFloat(),p.z.toFloat(),n.x.toFloat(),n.y.toFloat(),n.z.toFloat(),m.toFloat(),s.toFloat()))
            }
        }
        fun tube(path: List<V>, rw: Double, rh: Double, closed: Boolean, m: Int, spin: Int, sides: Int, up: V) {
            val count = path.size
            val rings = path.indices.map { i ->
                val prev = path[if (closed) (i-1+count)%count else max(0,i-1)]
                val next = path[if (closed) (i+1)%count else min(count-1,i+1)]
                val t = (next-prev).unit()
                var side = t.cross(up)
                if (side.length() < 1e-4) side = t.cross(V(0.0,1.0,0.0))
                side = side.unit()
                val lift = side.cross(t).unit()
                (0 until sides).map { j ->
                    val a = j.toDouble()/sides*PI*2
                    val c = cos(a); val s = sin(a)
                    Pair(path[i]+side*(c*rw)+lift*(s*rh), (side*(c/rw)+lift*(s/rh)).unit())
                }
            }
            for (i in 0 until if (closed) count else count-1) for (j in 0 until sides) {
                val r0 = rings[i]; val r1 = rings[(i+1)%count]; val j1 = (j+1)%sides
                tri(r0[j],r1[j],r1[j1],m,spin); tri(r0[j],r1[j1],r0[j1],m,spin)
            }
            if (!closed) for ((i,dir) in listOf(0 to -1.0,count-1 to 1.0)) {
                val n = (path[min(count-1,i+1)]-path[max(0,i-1)]).unit()*dir
                for (j in 0 until sides) tri(path[i] to n,rings[i][j].first to n,rings[i][(j+1)%sides].first to n,m,spin)
            }
        }
        fun box(lo: V, hi: V, m: Int, s: Int) {
            val (x0,y0,z0)=lo; val (x1,y1,z1)=hi
            val faces = listOf(
                listOf(V(x0,y1,z0),V(x1,y1,z0),V(x1,y1,z1),V(x0,y1,z1),V(0.0,1.0,0.0)),
                listOf(V(x0,y0,z1),V(x1,y0,z1),V(x1,y0,z0),V(x0,y0,z0),V(0.0,-1.0,0.0)),
                listOf(V(x0,y0,z1),V(x0,y1,z1),V(x1,y1,z1),V(x1,y0,z1),V(0.0,0.0,1.0)),
                listOf(V(x1,y0,z0),V(x1,y1,z0),V(x0,y1,z0),V(x0,y0,z0),V(0.0,0.0,-1.0)),
                listOf(V(x1,y0,z1),V(x1,y1,z1),V(x1,y1,z0),V(x1,y0,z0),V(1.0,0.0,0.0)),
                listOf(V(x0,y0,z0),V(x0,y1,z0),V(x0,y1,z1),V(x0,y0,z1),V(-1.0,0.0,0.0)))
            for (f in faces) { val n=f[4]; tri(f[0] to n,f[1] to n,f[2] to n,m,s); tri(f[0] to n,f[2] to n,f[3] to n,m,s) }
        }
    }
    private fun lathe(m: Mesh,x0: Double,x1: Double,y: Double,z: Double,rad: (Double)->Double,mat: Int,spin: Int,steps: Int,sides: Int) {
        fun ring(i: Int): List<Pair<V,V>> {
            val u=i.toDouble()/steps; val r=rad(u); val dr=(rad(min(1.0,u+.01))-rad(max(0.0,u-.01)))/(.02*(x1-x0))
            return (0..sides).map { k -> val a=k.toDouble()/sides*PI*2; V(x0+(x1-x0)*u,y+cos(a)*r,z+sin(a)*r) to V(-dr,cos(a),sin(a)).unit() }
        }
        var prev=ring(0)
        for (i in 1..steps) { val next=ring(i); for (k in 0 until sides) { m.tri(prev[k],next[k],next[k+1],mat,spin); m.tri(prev[k],next[k+1],prev[k+1],mat,spin) }; prev=next }
    }
    private data class Outline(val x: Double,val z: Double,val nx: Double,val nz: Double)
    private data class Profile(val o: Double,val y: Double,val nr: Double,val ny: Double,val mat: Int)
    private fun slab(m: Mesh) {
        val hw=1.08; val hd=.56; val y0=-1.44; val y1=-1.3; val rc=.18; val b=.035
        val outline=ArrayList<Outline>()
        for ((cx,cz,a0) in listOf(Triple(hw-rc,hd-rc,0.0),Triple(-(hw-rc),hd-rc,PI/2),Triple(-(hw-rc),-(hd-rc),PI),Triple(hw-rc,-(hd-rc),3*PI/2))) for (i in 0..8) {
            val a=a0+i.toDouble()/8*PI/2; outline += Outline(cx+cos(a)*rc,cz+sin(a)*rc,cos(a),sin(a))
        }
        val p=ArrayList<Profile>()
        for (i in 0..5) { val f=PI/2*(1-i.toDouble()/5); p += Profile(-b+b*cos(f),y1-b+b*sin(f),cos(f),sin(f),if (i<3) 2 else 1) }
        for (i in 0..5) { val f=-PI/2*i/5; p += Profile(-b+b*cos(f),y0+b+b*sin(f),cos(f),sin(f),1) }
        fun at(k:Int,j:Int): Pair<V,V> { val e=outline[k%outline.size]; val q=p[j]; return V(e.x+e.nx*q.o,q.y,e.z+e.nz*q.o) to V(e.nx*q.nr,q.ny,e.nz*q.nr).unit() }
        for (k in outline.indices) for (j in 0 until p.size-1) { val mat=p[j].mat; m.tri(at(k,j),at(k+1,j),at(k+1,j+1),mat,0); m.tri(at(k,j),at(k+1,j+1),at(k,j+1),mat,0) }
        for ((j,ny,mat) in listOf(Triple(0,1.0,2),Triple(p.size-1,-1.0,1))) { val n=V(0.0,ny,0.0); val c=V(0.0,p[j].y,0.0) to n; for (k in outline.indices) m.tri(c,at(k,j).first to n,at(k+1,j).first to n,mat,0) }
    }
    fun build(): FloatArray {
        val m=Mesh(); val tilt=.4; val ct=cos(tilt); val st=sin(tilt)
        for (k in 0 until 6) { val a=k.toDouble()/6*PI+.26; val path=(0 until 120).map { i -> val u=i.toDouble()/120*PI*2; val x=sin(u)*cos(a); val y=cos(u); val z=sin(u)*sin(a); V((ct*x-st*y)*.99,(st*x+ct*y)*.99,z*.99) }; m.tube(path,.022,.022,true,0,1,8,V(0.0,0.0,1.0)) }
        m.tube((0 until 120).map { val u=it.toDouble()/120*PI*2; V(cos(u)*.995,0.0,sin(u)*.995) },.022,.022,true,0,1,8,V(0.0,1.0,0.0))
        for ((x0,x1) in listOf(-1.28 to -.96,.96 to 1.34)) m.tube(listOf(V(x0,0.0,0.0),V(x1,0.0,0.0)),.028,.028,false,0,1,12,V(0.0,1.0,0.0))
        for (s in listOf(-1.0,1.0)) m.tube(listOf(V(s*.97,0.0,0.0),V(s*1.13,0.0,0.0)),.07,.07,false,0,1,16,V(0.0,1.0,0.0))
        m.tube(listOf(V(1.32,.02,0.0),V(1.32,-.2,0.0)),.03,.022,false,0,1,10,V(1.0,0.0,0.0))
        lathe(m,1.29,1.37,-.2,0.0,{ u -> .05+.012*sin(PI*u) },0,1,6,16)
        lathe(m,1.37,1.78,-.2,0.0,{ u -> val end=if(u>.86) sqrt(max(0.0,1-((u-.86)/.14).pow(2))) else 1.0; (.046+.02*sin(PI*min(1.0,u*1.08)).pow(.8))*end },3,1,28,18)
        for (s in listOf(-1.0,1.0)) {
            m.tube(listOf(V(s*1.14,0.0,0.0),V(s*1.2,0.0,0.0)),.075,.09,false,1,0,12,V(0.0,1.0,0.0))
            m.box(V(if(s>0) 1.11 else -1.23,-.15,-.08),V(if(s>0) 1.23 else -1.11,.11,.08),1,0)
            val p0=V(s*1.2,-.1,0.0); val p1=V(s*1.44,-.42,0.0); val p2=V(s*1.44,-1.06,0.0); val p3=V(s*1.02,-1.32,0.0)
            val path=(0..30).map { i -> val t=i.toDouble()/30; val u=1-t; p0*(u*u*u)+p1*(3*u*u*t)+p2*(3*u*t*t)+p3*(t*t*t) }
            m.tube(path,.045,.085,false,1,0,12,V(0.0,0.0,1.0))
        }
        slab(m); return m.data.toFloatArray()
    }
}
