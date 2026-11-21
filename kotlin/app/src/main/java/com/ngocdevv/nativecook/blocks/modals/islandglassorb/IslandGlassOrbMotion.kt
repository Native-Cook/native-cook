package com.ngocdevv.nativecook.blocks.modals.islandglassorb

import kotlin.math.*

/** Value-for-value port of igo-motion.ts. Durations are seconds, lengths are reference points. */
internal object IslandGlassOrbMotion {
    const val LOOP = 10.0
    val blendKeys = intArrayOf(0,1,2,4,5,6,7,8,9,10,11,12,30,37,38,41,42)
    fun seg(t: Double, a: Double, b: Double) = ((t-a)/(b-a)).coerceIn(0.0,1.0)
    fun clamp(v: Double) = v.coerceIn(0.0,1.0)
    fun mix(a: Double,b: Double,k: Double) = a+(b-a)*k
    fun out(k: Double) = 1-(1-k).pow(3)
    fun inside(k: Double) = k*k*k
    fun inOut(k: Double) = if(k<.5)4*k*k*k else 1-(-2*k+2).pow(3)/2
    fun emph(k: Double) = 1-(1-k).pow(4)
    fun smooth(a: Double,b: Double,v: Double): Double { val k=clamp((v-a)/(b-a)); return k*k*(3-2*k) }
    fun spring(t: Double,from: Double,to: Double,v: Double,d: Double,k: Double): Double {
        if(t<=0) return from
        val x0=from-to; val w0=sqrt(k); val z=d/(2*w0)
        if(z<1) {
            val wd=w0*sqrt(1-z*z); val e=exp(-z*w0*t)
            return to+e*(x0*cos(wd*t)+(v+z*w0*x0)/wd*sin(wd*t))
        }
        val s=sqrt(max(z*z-1,1e-6)); val r1=-w0*(z-s); val r2=-w0*(z+s)
        val c2=(v-r1*x0)/(r2-r1); val c1=x0-c2
        return to+c1*exp(r1*t)+c2*exp(r2*t)
    }
    fun mixFrames(a: IslandGlassOrbFrame,b: IslandGlassOrbFrame,k: Double): IslandGlassOrbFrame {
        val av=a.values(); val bv=b.values()
        return IslandGlassOrbFrame.from(DoubleArray(av.size) { mix(av[it],bv[it],k) })
    }
    private val kt=doubleArrayOf(1.5,1.7,1.88,2.2,2.45,2.7,3.0,3.2,3.42,3.7,3.95,4.2,4.5,4.9)
    private val kv=doubleArrayOf(.15,.9,.4,1.0,.35,.8,.45,.6,.9,.45,.3,.9,.4,0.0)
    fun amp(t: Double): Double {
        if(t<1.1) return 0.0
        if(t<1.5) return .15*inOut(seg(t,1.1,1.5))
        if(t<4.9) {
            var i=0; while(i<kt.size-2 && t>=kt[i+1])i++
            val p0=kv[max(i-1,0)]; val p1=kv[i]; val p2=kv[i+1]; val p3=kv[min(i+2,kv.size-1)]
            val u=(t-kt[i])/(kt[i+1]-kt[i])
            return (.5*(2*p1+(-p0+p2)*u+(2*p0-5*p1+4*p2-p3)*u*u+(-p0+3*p1-3*p2+p3)*u*u*u)).coerceIn(0.0,1.05)
        }
        return 0.0
    }
    fun pull(t: Double): Double {
        if(t<3.25)return 0.0
        if(t<3.7)return inOut(seg(t,3.25,3.7))
        if(t<3.85)return 1.0
        if(t<5.2)return spring(t-3.85,1.0,0.0,0.0,10.0,220.0)*(1-seg(t,4.9,5.2))
        return 0.0
    }
    private fun spill(t: Double): Pair<Double,Double> {
        val u=seg(t,7.0,7.7); val y=mix(103.0,250.0,emph(u))
        val env=if(t<7 || t>7.7)0.0 else smooth(0.0,.18,u)*(1-smooth(.55,1.0,u))
        return y to env
    }
    private fun rim(t: Double): Double = exp(-((t-7.38)/.2).pow(2))
    fun fly(t: Double) = spring(t-4.5,0.0,1.0,0.0,16.0,210.0)
    fun offset(t: Double): Pair<Double,Double> {
        if(t<3) return 0.0 to 0.0
        if(t<3.5) { val u=inOut(seg(t,3.0,3.5)); return u to 250*u }
        if(t<4.5) { val y=spring(t-3.5,308.0,440.0,0.0,22.1,157.9); val u=inOut(seg(t,3.95,4.5)); return (1-51*u) to (y-58-68*u) }
        val f=fly(t); return -50*(1-f) to 314*(1-f)
    }
    fun band(t: Double): Triple<Double,Double,Double> {
        val u=seg(t,7.25,7.95); val inside=t>=7.25 && t<=7.95
        return Triple(if(inside)mix(300.0,660.0,inOut(u)) else 300.0,if(inside)smooth(0.0,.12,u)*(1-smooth(.8,1.0,u)) else 0.0,.76+.42*u)
    }
    fun dropShape(travel: Double,broken: Double,ts: Double,antic: Double): IslandGlassOrbFrame {



        val u = clamp(travel / 250.0)
        var free = smooth(40.0, 230.0, travel)
         // the black stays with the island: the drop's cap is gone by 60 pt, so the clear lens shows as soon as it clears the island
         // (the spec had 0.46 → 0 between 80 and 230 pt; that left a wide black dome with shoulders under the narrowing island)
        var cap = 0.46 * (1.0 - smooth(0.0, 60.0, travel))
        var neck = if (travel <= 120.0) mix(28.0, 20.0, travel / 120.0) else mix(20.0, 6.0, clamp((travel - 120.0) / 120.0)) // Ø 20 at 120 pt, Ø 6 at 240
         // a wide meniscus while the funnel is drawn out (smooth, no shoulders), tightening to a hairline thread by 240 pt
        var kJoin = if (travel < 80.0) mix(28.0, 44.0, travel / 80.0) else mix(44.0, 12.0, smooth(80.0, 240.0, travel))
        val base = mix(126.0, 120.0, antic)
        var iw = mix(base, 58.0, smooth(0.25, 1.0, u)) // the island narrows to 58 pt
        if (broken > 0.5) {
        iw = spring(ts, 58.0, 126.0, 0.0, 12.0, 260.0) // it recoils (d 12, k 260)
        if (ts < 0.08) {
        val r = 1.0 - ts / 0.08 // the thread pinches in 80 ms
        neck *= r
        kJoin *= r
        } else {
        val r = smooth(160.0, 90.0, travel) // and re-forms under 150 pt on the way home
        neck *= r
        kJoin *= r
        }
         // a torn-off bead stays a clear circle until it is close enough to be pulled back in
        val near = smooth(160.0, 90.0, travel)
        free = mix(1.0, free, near)
        cap *= near
        }
        val ih = mix(37.0, 40.0, antic) + 0.05 * (126.0 - iw)
        return IslandGlassOrbFrame(free=free, cap=cap, neck=neck, kJoin=kJoin, iw=iw, ih=ih)
    }
    fun frame(tIn: Double): IslandGlassOrbFrame {



        val t = ((tIn % LOOP) + LOOP) % LOOP
        val arrive = 4.678

         // shapes
        val antic = if (t < 0.5) 0.0 else if (t < 0.62) out(seg(t, 0.5, 0.62)) else 1.0 - inOut(seg(t, 0.62, 0.9))
        var m = 0.0
        if (t >= 0.62 && t < arrive) { m = spring(t - 0.62, 0.0, 1.0, 0.0, 19.0, 195.0) }
        else if (t >= arrive) { m = max(0.0, spring(t - arrive, 1.0, 0.0, 0.0, 30.5, 322.3)) } // the bead is swallowed
        if (t > 5.6) { m = 0.0 }
        val pill = if (t < 4.95 || t >= 8.8 + 0.25) 0.0 else max(0.0, spring(t - 4.95, 0.0, 1.0, 0.0, 30.5, 322.3))
        var ban = 0.0
        if (t >= 5.7 + 0.25 && t < 8.8 + 0.25) { ban = spring(t - 5.7 - 0.25, 0.0, 1.0, 0.0, 22.1, 157.9) }
        else if (t >= 8.8 + 0.25 && t < 9.9) { ban = max(0.0, spring(t - 8.8 - 0.25, 1.0, 0.0, 0.0, 22.1, 157.9)) }

         // the drop: intro squash, then the tear-off, the bead's squash on the ring, the flight home
        val offsetValue = offset(t); val dx = offsetValue.first; val dy = offsetValue.second
        val travel = hypot(dx, dy)
        val broken = if (t >= 3.5 && t < arrive) 1.0 else 0.0
        val ts = t - 3.5
        var e = 0.0
        if (t >= 0.78 && t < 1.4) {
        val k = 1.0 - seg(t, 0.78, 1.4)
        e = 0.06 * sin(2.0 * PI * 5.0 * (t - 0.78)) * k * k
        } else if (t >= 3.0 && t < 3.5) {
        e = -0.07 * clamp(travel / 250.0)
        } else if (t >= 3.5 && t < 4.5) {
        e = -0.07 * exp(-ts / 0.04) + 0.06 * sin(2.0 * PI * 3.5 * ts) * exp(-ts / 0.2) // ±6 % squash
        } else if (t >= 4.5 && t < arrive) {
        val v = abs(fly(t + 0.004) - fly(t - 0.004)) / 0.008
        e = -0.22 * clamp((v * hypot(-50.0,314.0)) / 2400.0) // stretch ≤ 1.22 along the flight
        }
        val shape = dropShape(travel, broken, ts, antic)
        val bump = if (t >= arrive - 0.03 && t < arrive + 0.27) sin(PI * seg(t, arrive - 0.03, arrive + 0.27)) else 0.0 // the merge: h 37 → 47 → 37

         // the voice and its line
        val amp = amp(t)
        val ampLag = amp(t - 0.06)
        val mc = clamp(m)
        var lineLen = 0.0
        var lineX = 200.0 + dx * mc
        var lineY = mix(30.0, 58.0, mc) + dy * mc
        if (t >= 1.1 && t < 1.5) { lineLen = 152.0 * out(seg(t, 1.1, 1.5)) }
        else if (t >= 1.5 && t < 4.8) { lineLen = 152.0 }
        else if (t >= 4.8 && t < 5.3) {
        val k = inOut(seg(t, 4.8, 5.3))
        lineLen = mix(152.0, 0.0, k)
        lineX = mix(lineX, 112.0 + 10.5, k)
        lineY = mix(lineY, 40.0, k)
        }
        val mic = if (t < 0.7) 0.0 else if (t < 4.95) out(seg(t, 0.7, 0.85)) else 1.0 - out(seg(t, 4.95, 4.95 + 0.15))

         // thinking: the pill 4950–5250, the spinner to 5950
        val spinA = if (t < 5.2 || t >= 6.1) 0.0 else out(seg(t, 5.2, 5.4)) * (1.0 - seg(t, 5.95, 6.1))
        val spinT = t - 5.2
        val spinBlur = 6.0 * seg(t, 5.95, 6.1)
        val pillWob = if (t >= 5.35 && t < 5.95) 3.0 * sin(2.0 * PI * (t - 5.35)) else 0.0

         // reply (v2 timing + 250 ms)
        val clock = if (t < 5.7 + 0.25) 1.0 else if (t < 9.0 + 0.25) 1.0 - out(seg(t, 5.7 + 0.25, 5.9 + 0.25)) else out(seg(t, 9.0 + 0.25, 9.2 + 0.25))
        val textT = t - 6.0 - 0.25
        val textOut = inside(seg(t, 8.7 + 0.25, 8.9 + 0.25))
        var arcA = out(seg(t, 6.2 + 0.25, 6.8 + 0.25)) * (1.0 - seg(t, 8.95 + 0.25, 9.2 + 0.25))
        if (t < 6.2 + 0.25 || t >= 9.2 + 0.25) { arcA = 0.0 }
        val arcW = mix(140.0, 220.0, out(seg(t, 6.2 + 0.25, 6.8 + 0.25))) * (1.0 - inside(seg(t, 8.8 + 0.25, 9.2 + 0.25)))
        val arcHue = (t - 6.2 - 0.25) / 2.0

         // payoff: the band of arc-coloured light over the ring (the floor 120 ms behind), the widget rims and the calendar row
        val band = IslandGlassOrbMotion.band(t)
        val floor = IslandGlassOrbMotion.band(t - 0.12)
        var row = 0.0
        if (t >= 7.4 + 0.25 && t < 7.7 + 0.25) { row = spring(t - 7.4 - 0.25, 0.0, 1.0, 0.0, 19.0, 195.0) }
        else if (t >= 7.7 + 0.25 && t < 8.6 + 0.25) {
        val r0 = spring(0.3, 0.0, 1.0, 0.0, 19.0, 195.0)
        row = spring(t - 7.7 - 0.25, r0, 0.0, 0.0, 22.1, 157.9) * (1.0 - seg(t, 8.3 + 0.25, 8.6 + 0.25))
        }

        return IslandGlassOrbFrame(
        m= m,
        pill= pill,
        ban= ban,
        antic= antic,
        dx= dx,
        dy= dy,
        e= e,
        amp= amp,
        ampLag= ampLag,
        causticAmp= amp(t - 0.08),
        lineLen= lineLen,
        lineX= lineX,
        lineY= lineY,
        mic= mic,
        spinA= spinA,
        spinT= spinT,
        spinBlur= spinBlur,
        pillWob= pillWob,
        clock= clock,
        textT= textT,
        textOut= textOut,
        arcA= arcA,
        arcW= arcW,
        arcHue= arcHue,
        bandY= band.first,
        bandA= band.second,
        floorY= floor.first,
        floorA= floor.second,
        bandHue= band.third,
        floorHue= floor.third,
        rimL= rim(t),
        rimR= rim(t - 0.06),
        row= row,
        pluckX= 0.0,
        pluckD= 0.0,
        free= shape.free,
        cap= shape.cap,
        neck= shape.neck,
        kJoin= shape.kJoin,
        iw= shape.iw,
        ih= shape.ih + 10.0 * bump,
        spillY= 103.0,
        spillA= 0.0)
    }
    fun live(m: Double,pill: Double,ban: Double,dx: Double,dy: Double,amp: Double,mic: Double,spin: Double,px: Double,pd: Double,t: Double): IslandGlassOrbFrame {
        val mc=clamp(m)
        return frame(0.0).copy(m=m,pill=pill,ban=ban,dx=dx,dy=dy,e=-.05*(dy/52)*mc-.02*(abs(dx)/34)*mc,amp=amp,ampLag=amp,
            lineLen=152*out(seg(m,.55,1.0)),lineX=200+dx*mc,lineY=mix(30.0,58.0,mc)+dy*mc,mic=mic,spinA=spin,spinT=t,spinBlur=0.0,
            clock=1-smooth(.15,.6,ban),textT=10.0,textOut=1-smooth(.55,.95,ban),arcA=smooth(.45,1.0,ban),arcW=220*smooth(.2,1.0,ban),arcHue=t/2,pluckX=px,pluckD=pd)
    }
    fun thinkRest() = frame(5.6).copy(pillWob=0.0,spinT=.25)
    fun replyRest() = frame(8.75).copy(row=0.0,bandA=0.0,floorA=0.0,spillA=0.0,rimL=0.0,rimR=0.0,arcHue=.15)
    fun shapes(f: IslandGlassOrbFrame,oy: Double): IslandGlassOrbShapes {
        val iw=f.iw; val ih=f.ih
        val x1=mix(201-iw/2,82-f.pillWob/2,f.pill); val y1=11.0
        val w1=mix(iw,237+f.pillWob,f.pill); val h1=mix(ih,58.0,f.pill)
        val mc=clamp(f.m); val grow=1+.08*f.amp
        return IslandGlassOrbShapes(mix(x1,11.0,f.ban),mix(y1,10.0,f.ban)+oy,mix(w1,380.0,f.ban),mix(h1,126.0,f.ban),200+f.dx*mc,mix(30.0,58.0,f.m.coerceIn(0.0,1.15))+f.dy*mc+oy,(mix(96.0,mix(196.0,182.0,f.free),f.m)/2)*grow*(1+f.e),(mix(24.0,mix(152.0,182.0,f.free),f.m)/2)*grow*(1-f.e))
    }
}
