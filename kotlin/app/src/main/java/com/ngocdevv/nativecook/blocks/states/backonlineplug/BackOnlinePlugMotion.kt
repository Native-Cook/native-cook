package com.ngocdevv.nativecook.blocks.states.backonlineplug

import kotlin.math.*

// Option A uses lateral a (1 at rest, 0 aligned) and depth z (1 out, 0 seated).
internal object BackOnlinePlugMotion {
    const val period = 7.0 / 3
    const val restDX = -64.0
    const val restDY = 92.0
    val restDistance = hypot(restDX,restDY)
    val offline = doubleArrayOf(46.0,59.0,52.0)
    val online = doubleArrayOf(31.0,107.0,64.0)
    val flash = doubleArrayOf(44.0,138.0,82.0)
    fun clamp(x: Double) = x.coerceIn(0.0,1.0)
    fun seg(t: Double,a: Double,b: Double) = clamp((t-a)/(b-a))
    fun out(x: Double) = 1-(1-x).pow(3)
    fun ease(x: Double) = if(x < .5) 2*x*x else 1-(-2*x+2).pow(2)/2
    fun mod(x: Double,m: Double) = ((x%m)+m)%m
    fun mix(a: DoubleArray,b: DoubleArray,u: Double) = DoubleArray(3) { a[it]+(b[it]-a[it])*u }
    fun bounce(u: Double) = if(u < 0 || u >= .35) 0.0 else -.03*exp(-12*u)*sin(30*u)
    fun lateral(t: Double): Double {
        if(t >= 1 && t < 1.6) return 1-ease(seg(t,1.0,1.6))
        if(t >= 1.6 && t < 5.3) return 0.0
        if(t >= 5.3 && t < 5.9) return ease(seg(t,5.3,5.9))
        return 1.0
    }
    fun depth(t: Double): Double {
        if(t >= 1.55 && t < 1.85) return 1-seg(t,1.55,1.85).pow(2)
        if(t >= 1.85 && t < 4.8) return bounce(t-1.85)
        if(t >= 4.8 && t < 5.3) return out(seg(t,4.8,5.3))
        return 1.0
    }
    fun wall(t: Double): DoubleArray {
        if(t >= 1.85 && t < 5) {
            val a = seg(t,1.85,1.97)
            return if(a < 1) mix(offline,flash,a) else mix(flash,online,ease(seg(t,1.97,2.6)))
        }
        return if(t >= 5) mix(online,offline,ease(seg(t,5.0,5.6))) else offline
    }
    fun flip(t: Double) = if(t >= 1.9 && t < 5.1) seg(t,1.9,2.3) else if(t >= 5.1) 1-seg(t,5.1,5.5) else 0.0
    fun chip(t: Double) = min(3,1+floor(seg(t,2.3,4.6)*3).toInt())
    data class Frame(val a: Double = 1.0,val z: Double = 1.0,val sway: Double = 0.0,
                     val wall: DoubleArray = offline,val flip: Double = 0.0,
                     val lineOnline: Boolean = false,val chip: Int = 1,val spark: Double = 0.0)
    val off = Frame()
    val on = Frame(a=0.0,z=0.0,wall=online,flip=1.0,lineOnline=true,chip=3)
    fun frame(t: Double,c: Double,start: Double,a: Double = lateral(t),z: Double = depth(t),env: Double = 1.0): Frame {
        val f = flip(t)
        return Frame(a,z,if(z <= .5) 0.0 else sin(2*PI*(c-start)/period)*a*env,wall(t),f,f >= .5,chip(t),seg(t,1.82,2.05))
    }
    data class Touch(
        var manual: Boolean = false,var touching: Boolean = false,
        var loopStart: Double = 0.0,var resumeAt: Double = -1.0,var lastTouch: Double = -100.0,
        var pk: Int = 0,var pFrom: Double = 1.0,var pAt: Double = 0.0,var dragDist: Double = 0.0,
        var vk: Int = 0,var vAt: Double = 0.0,var chip: Int = 1,var env: Double = 1.0
    ) {
        fun seatTimes(from: Double): Pair<Double,Double> { val push = max(0.0,.6*from-.05); return push to push+.3 }
        fun plug(c: Double): Pair<Double,Double> {
            when(pk) {
                1 -> return clamp(pFrom-dragDist/restDistance) to 1.0
                2 -> {
                    val u = c-pAt; val align = .6*pFrom; val times = seatTimes(pFrom)
                    val a = if(align > 0) pFrom*(1-ease(clamp(u/align))) else 0.0
                    return a to if(u < times.second) 1-seg(u,times.first,times.second).pow(2) else bounce(u-times.second)
                }
                3 -> return ease(seg(c-pAt,.5,1.1)) to out(seg(c-pAt,0.0,.5))
                4 -> return pFrom+(1-pFrom)*out(seg(c,pAt,pAt+.35)) to 1.0
                else -> return pFrom to if(vk == 1) 0.0 else 1.0
            }
        }
        fun beat(c: Double) = if(vk == 1) min(1.85+(c-vAt),4.79) else if(vk == 2) min(4.8+(c-vAt),5.9) else 0.0
        fun frame(c: Double): Frame {
            if(!manual) return BackOnlinePlugMotion.frame(mod(c-loopStart,7.0),c,loopStart)
            val p = plug(c)
            val f = BackOnlinePlugMotion.frame(beat(c),c,loopStart,p.first,p.second,env)
            return f.copy(chip=if(vk == 2) chip else f.chip,spark=if(vk == 1) f.spark else 0.0)
        }
        fun online(c: Double): Boolean {
            if(manual) return vk == 1 && c >= vAt
            val t = mod(c-loopStart,7.0); return t >= 1.85 && t < 4.8
        }
        fun takeOver(c: Double) {
            resumeAt = -1.0; lastTouch = c
            if(manual) return
            manual = true; val t = mod(c-loopStart,7.0)
            pk = 0; pFrom = 1.0; vk = 0
            if(t >= 1 && t < 4.8) { pk = 2; pAt = c-(t-1); vk = 1; vAt = c-(t-1.85) }
            else if(t >= 4.8 && t < 5.9) { pk = 3; pAt = c-(t-4.8); vk = 2; vAt = pAt; chip = 3 }
        }
        fun seat(c: Double) {
            val from = plug(c).first
            pk = 2; pFrom = from; pAt = c; vk = 1; vAt = c+seatTimes(from).second
        }
        fun unseat(c: Double) { chip = BackOnlinePlugMotion.chip(beat(c)); pk = 3; pAt = c; vk = 2; vAt = c }
        fun canGrab(c: Double) = plug(c).second >= 1 && !(vk == 1 && c >= vAt)
        fun grab(c: Double) { pFrom = plug(c).first; pk = 1; dragDist = 0.0; if(vk == 1) vk = 0 }
        fun drag(dx: Double,dy: Double,c: Double) { dragDist = -(dx*restDX+dy*restDY)/restDistance; lastTouch = c }
        fun release(c: Double,tap: Boolean) {
            val a = plug(c).first
            if(tap || a*restDistance <= 40) { seat(c); return }
            pk = 4; pFrom = a; pAt = c
        }
        fun toggle(c: Double) {
            if(vk == 1) { if(c >= vAt) unseat(c); return }
            if(pk == 3 && plug(c).second < 1) return
            seat(c)
        }
        fun settled(c: Double): Boolean {
            if(pk == 1 || (pk == 2 && c < vAt+.8)) return false
            if(pk == 3 && c < pAt+1.1) return false
            if(pk == 4 && c < pAt+.35) return false
            return vk != 2 || c >= vAt+1.1
        }
        fun step(c: Double,dt: Double,autoPlay: Boolean): Touch {
            if(!manual) return this
            val target = if(touching && pk == 1) 0.0 else 1.0
            if(env != target) env = if(abs(target-env) < .002) target else env+(target-env)*(1-exp(-dt/.12))
            if(!autoPlay || touching || c-lastTouch < 2 || !settled(c)) return this
            if(vk == 1) { unseat(c); return this }
            if(plug(c).first < 1) { pFrom = plug(c).first; pk = 4; pAt = c; return this }
            if(resumeAt < 0) { resumeAt = loopStart+ceil((c-loopStart)/period)*period; return this }
            return if(c >= resumeAt && env == 1.0) Touch(loopStart=resumeAt) else this
        }
    }
}
