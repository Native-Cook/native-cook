package com.ngocdevv.nativecook.blocks.pickers.velvetnapstudio

import kotlin.math.*

internal data class VelvetNapStudioPoint(val x: Double, val y: Double)
internal class VelvetNapStudioField(val bytes: ByteArray) {
    fun value(i: Int): Double {
        val n = (bytes[i * 4].toInt() and 255) or ((bytes[i * 4 + 1].toInt() and 255) shl 8)
        return if (n >= 32768) 1 + (n - 32768) / 32767.0 else 1 - (32768 - n) / 32768.0
    }
    fun angle(i: Int) = (bytes[i * 4 + 2].toInt() and 255) / 255.0 * 2 * PI - PI
    companion object {
        val EMPTY = VelvetNapStudioField(ByteArray(64 * 64 * 4) { if (it % 4 == 1) 128.toByte() else 0 })
        fun encode(value: Double): Int {
            val v = value.coerceIn(0.0, 2.0)
            return (if (v >= 1) 32768 + (v - 1) * 32767 else 32768 - (1 - v) * 32768).roundToInt()
        }
    }
}
internal data class VelvetNapStudioFrame(
    val time: Double = 0.0,
    val authorWeight: Double = 1.0,
    val fields: List<VelvetNapStudioField> = emptyList(),
    val weights: List<Double> = emptyList(),
    val strength: Double = 1.0,
) { companion object { val REST = VelvetNapStudioFrame() } }

internal object VelvetNapStudioMath {
    const val LOOP = 8.0
    fun clamp(x: Double, lo: Double = 0.0, hi: Double = 1.0) = min(hi, max(lo, x))
    fun smooth(x: Double): Double { val u = clamp(x); return u * u * (3 - 2 * u) }
    fun segment(t: Double, a: Double, b: Double) = smooth((t - a) / (b - a))
    fun inverseSmooth(value: Double) = .5 - sin(asin(1 - 2 * clamp(value)) / 3)
    fun wrap(angle: Double) = atan2(sin(angle), cos(angle))
    fun angle(from: Double, to: Double, mix: Double) = from + wrap(to - from) * clamp(mix)
    private fun hash(value: Int): Double { val x = sin((value + 42).toDouble() * 12.9898) * 43758.5453; return x - floor(x) }
    private fun bezier(t: Double): VelvetNapStudioPoint {
        val u = 1 - t
        return VelvetNapStudioPoint(u*u*u*50 + 3*u*u*t*106 + 3*u*t*t*173 + t*t*t*244,
            u*u*u*203 + 3*u*u*t*190 + 3*u*t*t*78 + t*t*t*65)
    }
    private fun tangent(t: Double): VelvetNapStudioPoint {
        val u = 1 - t; val x = 3*u*u*56 + 6*u*t*67 + 3*t*t*71
        val y = 3*u*u * -13 + 6*u*t * -112 + 3*t*t * -13; val length = hypot(x, y)
        return VelvetNapStudioPoint(x / length, y / length)
    }
    private val path: List<Pair<VelvetNapStudioPoint, Double>> by lazy {
        val points = mutableListOf(bezier(0.0) to 0.0); var length = 0.0
        for (i in 1..128) { val p = bezier(i / 128.0); val old = points[i-1].first; length += hypot(p.x-old.x, p.y-old.y); points += p to length }
        points.map { it.first to it.second / length }
    }
    val pathLength: Double by lazy { (1 until path.size).sumOf { i -> hypot(path[i].first.x-path[i-1].first.x, path[i].first.y-path[i-1].first.y) } }
    private fun nearest(point: VelvetNapStudioPoint): DoubleArray {
        var best = Double.MAX_VALUE; var arc = 0.0; var theta = 0.0
        for (i in 1 until path.size) {
            val a = path[i-1].first; val b = path[i].first; val dx = b.x-a.x; val dy = b.y-a.y
            val h = clamp(((point.x-a.x)*dx+(point.y-a.y)*dy)/(dx*dx+dy*dy))
            val d = hypot(point.x-a.x-dx*h, point.y-a.y-dy*h)
            if (d < best) { best=d; arc=path[i-1].second+(path[i].second-path[i-1].second)*h; theta=atan2(dy,dx) }
        }
        if (arc <= 0) { val a=path[0].first; val t=tangent(0.0); val along=(point.x-a.x)*t.x+(point.y-a.y)*t.y; if(along<0) arc=along/pathLength }
        else if (arc >= 1) { val a=path.last().first; val t=tangent(1.0); val along=(point.x-a.x)*t.x+(point.y-a.y)*t.y; if(along>0) arc=1+along/pathLength }
        return doubleArrayOf(arc,best,theta)
    }
    private fun fanDelay(point: VelvetNapStudioPoint): Double {
        var best=Double.MAX_VALUE; var delay=0.0
        for(k in 0 until 21) { val d=hypot(point.x-(22+263*hash(2*k)),point.y-(36+194*hash(2*k+1))); if(d<best) {best=d;delay=(k%5)*.04} }
        return delay
    }
    // Double CPU values match frozen Swift stamping; rendering reads the exact exported Float32 buffer.
    val grid: Array<DoubleArray> by lazy { Array(4096) { i ->
        val p=VelvetNapStudioPoint((i%64)*307.0/63,(i/64)*274.0/63); val n=nearest(p)
        doubleArrayOf(n[0],n[1],n[2],fanDelay(p))
    } }
    fun brightness(angle: Double) = 1.03 + .45 * clamp((cos(angle)*.8-sin(angle)*.6)/.85,-1.0,1.0)
    fun authored(time: Double) = VelvetNapStudioFrame(time=max(0.0,time)%LOOP)
    fun authoredSample(s: Double,distance: Double,angle: Double,delay: Double,time: Double): Double {
        val t=time%LOOP; if(t<=1 || t>=7.55) return 1.0
        val taper=smooth((s*pathLength+16)/16)*smooth(((1-s)*pathLength+16)/16)
        val band=(1-segment(distance,20.0,33.0))*taper; val reverseBand=(1-segment(distance,21.0,34.0))*taper
        val forwardLead=sqrt(max(0.0,33*33-distance*distance))/pathLength
        val reverseLead=sqrt(max(0.0,34*34-distance*distance))/pathLength
        val forwardArrival=1+1.76*inverseSmooth(s-forwardLead); val reverseArrival=3+1.76*inverseSmooth(1-s-reverseLead)
        val forward=segment(t,forwardArrival,forwardArrival+.09); val backward=segment(t,reverseArrival,reverseArrival+.09)
        val relaxation=1-segment(t,5.75+delay,7.55)
        val bright=(brightness(angle)-1)*band*forward; val dark=(brightness(angle+PI)-1)*reverseBand*backward
        return 1+(bright*(1-backward)+dark)*relaxation
    }
    fun authoredSample(p: DoubleArray,time: Double) = authoredSample(p[0],p[1],p[2],p[3],time)
    fun value(frame: VelvetNapStudioFrame,index: Int): Double {
        var delta=(authoredSample(grid[index],frame.time)-1)*frame.authorWeight
        for(i in frame.fields.indices) delta += (frame.fields[i].value(index)-1)*frame.weights[i]
        return 1+delta*frame.strength
    }
    fun baked(frame: VelvetNapStudioFrame): VelvetNapStudioField {
        val bytes=ByteArray(16384)
        for(i in 0 until 4096) {val n=VelvetNapStudioField.encode(value(frame,i));bytes[i*4]=n.toByte();bytes[i*4+1]=(n shr 8).toByte();bytes[i*4+3]=255.toByte()}
        return VelvetNapStudioField(bytes)
    }
    fun manualBaked(frame: VelvetNapStudioFrame) = baked(frame.copy(authorWeight=0.0))
    fun coalesced(frame: VelvetNapStudioFrame): VelvetNapStudioFrame {
        if(frame.fields.size<3) return frame
        val manual=VelvetNapStudioFrame(authorWeight=0.0,fields=frame.fields,weights=frame.weights,strength=frame.strength)
        return VelvetNapStudioFrame(time=frame.time,authorWeight=frame.authorWeight*frame.strength,fields=listOf(baked(manual)),weights=listOf(1.0))
    }
    fun distance(p: VelvetNapStudioPoint,a: VelvetNapStudioPoint,b: VelvetNapStudioPoint): Double {
        val dx=b.x-a.x;val dy=b.y-a.y;val length=dx*dx+dy*dy
        val u=if(length>0) clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/length) else 0.0
        return hypot(p.x-a.x-dx*u,p.y-a.y-dy*u)
    }
    fun stamped(field: VelvetNapStudioField,from: VelvetNapStudioPoint,to: VelvetNapStudioPoint,angle: Double,width: Double,baseline: VelvetNapStudioFrame=VelvetNapStudioFrame.REST): VelvetNapStudioField {
        val bytes=field.bytes.copyOf()
        for(i in 0 until 4096) {
            val p=VelvetNapStudioPoint((i%64)*307.0/63,(i/64)*274.0/63);val d=distance(p,from,to);val r=width/2
            val influence=1-segment(d,max(0.0,r-7),r+4);if(influence<=0) continue
            val authoredDelta=(authoredSample(grid[i],baseline.time)-1)*baseline.authorWeight*baseline.strength
            val current=field.value(i)+authoredDelta;val ratio=field.value(i)+(brightness(angle)-current)*influence
            val n=VelvetNapStudioField.encode(ratio);bytes[i*4]=n.toByte();bytes[i*4+1]=(n shr 8).toByte()
            val theta=angle(field.angle(i),angle,influence)
            bytes[i*4+2]=(clamp((wrap(theta)+PI)/(2*PI))*255).roundToInt().toByte()
            bytes[i*4+3]=(max((bytes[i*4+3].toInt() and 255)/255.0,influence)*255).roundToInt().toByte()
        }
        return VelvetNapStudioField(bytes)
    }
    fun mixed(from: VelvetNapStudioFrame,to: VelvetNapStudioField,amount: Double): VelvetNapStudioFrame {
        val u=clamp(amount);if(u==0.0) return from
        if(u==1.0) return VelvetNapStudioFrame(time=from.time,authorWeight=from.authorWeight*from.strength,fields=listOf(to),weights=listOf(1.0))
        return from.copy(authorWeight=from.authorWeight*from.strength,weights=from.weights.map{it*(1-u)*from.strength}+u,fields=from.fields+to,strength=1.0)
    }
    fun recovering(frame: VelvetNapStudioFrame,elapsed: Double) = if(elapsed>=.6) VelvetNapStudioFrame.REST else frame.copy(strength=frame.strength*(1-smooth(elapsed/.6)))
}
