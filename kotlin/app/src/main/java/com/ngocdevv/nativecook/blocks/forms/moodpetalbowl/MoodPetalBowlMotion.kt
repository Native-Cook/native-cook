package com.ngocdevv.nativecook.blocks.forms.moodpetalbowl

import kotlin.math.*

internal const val MOOD_PETAL_BOWL_LOOP = 8.0
internal enum class MoodPetalBowlMood(val title: String, val hex: Int) {
    Calm("CALM", 0x0CB782), Low("LOW", 0x567DF0), Bright("BRIGHT", 0xF1DF67), Tense("TENSE", 0xEE1644);
    fun stepped(delta: Int) = entries[(ordinal + delta + 4) % 4]
}
internal data class MoodPetalBowlPoint(val x: Double, val y: Double) {
    fun distance(other: MoodPetalBowlPoint) = hypot(x - other.x, y - other.y)
}
internal data class MoodPetalBowlRGBA(val r: Double, val g: Double, val b: Double, val a: Double = 1.0) {
    companion object {
        fun hex(hex: Int) = MoodPetalBowlRGBA(((hex shr 16) and 255) / 255.0, ((hex shr 8) and 255) / 255.0, (hex and 255) / 255.0)
        fun mix(a: MoodPetalBowlRGBA, b: MoodPetalBowlRGBA, q: Double) = MoodPetalBowlRGBA(
            a.r + (b.r - a.r) * q, a.g + (b.g - a.g) * q, a.b + (b.b - a.b) * q, a.a + (b.a - a.a) * q)
    }
}
internal data class MoodPetalBowlToken(val id: Int, val mood: MoodPetalBowlMood, val center: MoodPetalBowlPoint,
    val angle: Double, val alpha: Double, val batchAge: Double)
internal data class MoodPetalBowlScene(val tokens: List<MoodPetalBowlToken>, val labelWeights: List<Double>,
    val labelOffsets: List<Double>, val capsuleX: Double, val dots: List<MoodPetalBowlRGBA>, val selectedDay: Int,
    val captionAlpha: Double, val captionDay: Int) {
    val dominantMood: MoodPetalBowlMood get() {
        var best = 0
        // Swift's max(by:) retains the first equal maximum.
        for (i in 1..3) if (labelWeights[i] > labelWeights[best]) best = i
        return MoodPetalBowlMood.entries[best]
    }
}
internal data class MoodPetalBowlTransition(val from: MoodPetalBowlScene, val mood: MoodPetalBowlMood, val day: Int,
    val dots: List<MoodPetalBowlRGBA>, val direction: Double, val speedup: Double, val outgoing: List<MoodPetalBowlToken>)
internal data class MoodPetalBowlLayout(val radii: List<Double>, val targets: List<MoodPetalBowlPoint>, val angles: List<Double>)

internal object MoodPetalBowlGeometry {
    private fun layout(r: List<Int>, xy: List<Pair<Int, Int>>, angles: List<Int>) = MoodPetalBowlLayout(
        r.map(Int::toDouble), xy.map { MoodPetalBowlPoint(it.first.toDouble(), it.second.toDouble()) }, angles.map(Int::toDouble))
    private val layouts = listOf(
        layout(listOf(59,48,54,31,37,41,29), listOf(101 to 258,183 to 295,281 to 282,282 to 350,116 to 346,190 to 376,244 to 366), listOf(20,31,-12,44,11,-27,58)),
        layout(listOf(61,43,56,33,36,39,29), listOf(110 to 282,181 to 317,247 to 258,289 to 326,116 to 347,194 to 376,254 to 357), listOf(-20,31,-12,44,11,-27,58)),
        layout(listOf(53,40,82,34,37,40,29), listOf(110 to 286,168 to 317,230 to 272,296 to 284,138 to 359,203 to 376,258 to 358), listOf(20,31,-12,44,11,-27,58)),
        layout(listOf(61,38,51,31,34,42,26), listOf(106 to 273,202 to 278,279 to 315,286 to 352,115 to 350,192 to 373,205 to 326), listOf(-20,31,-12,44,11,-27,58)))
    fun layout(mood: MoodPetalBowlMood) = layouts[mood.ordinal]
    fun radius(id: Int, mood: MoodPetalBowlMood) = layouts[mood.ordinal].radii[id]
    val spawns = listOf(104 to 151,222 to 122,270 to 160,301 to 130,151 to 145,197 to 105,245 to 135)
        .map { MoodPetalBowlPoint(it.first.toDouble(), it.second.toDouble()) }
    val painterOrder = listOf(0,2,1,3,4,6,5)
    val contours = MoodPetalBowlMood.entries.map(::makeContour)
    private fun makeContour(mood: MoodPetalBowlMood): List<MoodPetalBowlPoint> {
        if (mood == MoodPetalBowlMood.Tense) {
            val vertices = List(24) { i -> val a = i * PI / 12 - PI / 2; val r = if (i % 2 == 0) 1.0 else .82
                MoodPetalBowlPoint(cos(a) * r, sin(a) * r) }
            val poly = buildList {
                for (i in vertices.indices) {
                    val v = vertices[i]
                    fun inset(p: MoodPetalBowlPoint): MoodPetalBowlPoint { val length = p.distance(v); val d = min(3 / 43.0, length * .4)
                        return MoodPetalBowlPoint(v.x + (p.x-v.x)*d/length, v.y + (p.y-v.y)*d/length) }
                    val a = inset(vertices[(i+23)%24]); val b = inset(vertices[(i+1)%24])
                    for (j in 0..<12) { val u=j/11.0; val s=1-u
                        add(MoodPetalBowlPoint(s*s*a.x+2*s*u*v.x+u*u*b.x,s*s*a.y+2*s*u*v.y+u*u*b.y)) }
                }
            }
            val lengths = DoubleArray(poly.size+1)
            for (i in poly.indices) lengths[i+1] = lengths[i] + poly[i].distance(poly[(i+1)%poly.size])
            return List(256) { i -> val at=lengths.last()*i/256; val k=(lengths.indexOfFirst { it>at }.let { if(it<0) poly.size else it }-1).coerceIn(0,poly.size-1)
                val q=(at-lengths[k])/(lengths[k+1]-lengths[k]); val a=poly[k]; val b=poly[(k+1)%poly.size]
                MoodPetalBowlPoint(a.x+(b.x-a.x)*q,a.y+(b.y-a.y)*q) }
        }
        return List(256) { i -> val a=i*2*PI/256; val r=when(mood) {
            MoodPetalBowlMood.Calm -> .8+.2*cos(4*a); MoodPetalBowlMood.Low -> .86+.14*cos(8*a)
            MoodPetalBowlMood.Bright -> .77+.23*cos(4*a); MoodPetalBowlMood.Tense -> 1.0 }
            val phase=if(mood==MoodPetalBowlMood.Calm) PI/4 else 0.0
            MoodPetalBowlPoint(cos(a+phase)*r,sin(a+phase)*r*(if(mood==MoodPetalBowlMood.Low) 1.08 else 1.0)) }
    }
    fun isContained(token: MoodPetalBowlToken): Boolean {
        val a=token.angle*PI/180; val c=cos(a); val s=sin(a); val r=radius(token.id,token.mood)
        for(p in contours[token.mood.ordinal]) { val x=token.center.x+r*(c*p.x-s*p.y); val y=token.center.y+r*(s*p.x+c*p.y)
            if(y>=273 && hypot(x-196.5,y-273)>151.5) return false }
        return true
    }
    fun interpolate(a: MoodPetalBowlToken,b: MoodPetalBowlToken,q: Double) = a.copy(
        center=MoodPetalBowlPoint(a.center.x+(b.center.x-a.center.x)*q,a.center.y+(b.center.y-a.center.y)*q),
        angle=a.angle+(b.angle-a.angle)*q,alpha=a.alpha+(b.alpha-a.alpha)*q)
    fun contained(from: MoodPetalBowlToken,desired: MoodPetalBowlToken): MoodPetalBowlToken {
        if(from.center==desired.center && from.angle==desired.angle) return desired
        if(isContained(desired)) return desired
        var low=0.0; var high=1.0
        repeat(12) { val q=(low+high)/2; if(isContained(interpolate(from,desired,q))) low=q else high=q }
        return interpolate(from,desired,low)
    }
}

internal object MoodPetalBowlMath {
    const val LOOP = MOOD_PETAL_BOWL_LOOP
    val days = listOf("Monday","Tuesday","Wednesday","Thursday","Friday","Saturday","Sunday")
    val dateX = listOf(40.0,92.1666666667,144.3333333333,196.5,248.6666666667,300.8333333333,353.0)
    val initialRecords = listOf(MoodPetalBowlMood.Bright,MoodPetalBowlMood.Low,MoodPetalBowlMood.Calm,MoodPetalBowlMood.Calm,null,null,null)
    fun clamp(x: Double,a: Double=0.0,b: Double=1.0)=min(b,max(a,x))
    fun smooth(x: Double): Double { val u=clamp(x);return u*u*(3-2*u) }
    fun ease(x: Double)=bezier(x,.2,.8,.2,1.0)
    fun io(x: Double)=bezier(x,.65,0.0,.35,1.0)
    fun colors(records: List<MoodPetalBowlMood?>,day: Int,mood: MoodPetalBowlMood)=records.mapIndexed { i,saved -> MoodPetalBowlRGBA.hex(if(i==day) mood.hex else saved?.hex?:0xE6E1D6) }
    fun settled(mood: MoodPetalBowlMood=MoodPetalBowlMood.Calm,day: Int=3,records: List<MoodPetalBowlMood?> = initialRecords,poster: Boolean=false): MoodPetalBowlScene {
        val layout=MoodPetalBowlGeometry.layout(mood)
        return MoodPetalBowlScene(List(7) { i -> MoodPetalBowlToken(i,mood,layout.targets[i],layout.angles[i],if(poster&&i<4) 0.0 else 1.0,2.0) },
            List(4) { if(it==mood.ordinal) 1.0 else 0.0 },List(4){0.0},dateX[day],colors(records,day,mood),day,0.0,day)
    }
    fun rest(records: List<MoodPetalBowlMood?> = initialRecords)=settled(records=records,poster=true)
    fun contactTime(id: Int,mood: MoodPetalBowlMood)=(35+sqrt(35.0*35+2*1750*(MoodPetalBowlGeometry.layout(mood).targets[id].y-MoodPetalBowlGeometry.spawns[id].y)))/1750
    fun flight(id: Int,mood: MoodPetalBowlMood,age: Double,bounce: Double): MoodPetalBowlToken {
        val layout=MoodPetalBowlGeometry.layout(mood);val s=max(0.0,age);val t=contactTime(id,mood);val target=layout.targets[id];val spawn=MoodPetalBowlGeometry.spawns[id]
        val sign=if(id%2==0) 1.0 else -1.0;val spin=sign*(65+9*id)
        var p=MoodPetalBowlToken(id,mood,spawn,layout.angles[id]-spin*t,smooth(age/.08),age)
        if(s<t) {val u=s/t;val q=1-(1-u).pow(3)
            p=p.copy(center=MoodPetalBowlPoint(spawn.x+(target.x-spawn.x)*q+sign*8*sin(PI*u).pow(2),spawn.y-35*s+.5*1750*s*s),angle=p.angle+spin*s)
        } else {val v=s-t;val e=1-smooth(v/bounce)
            p=p.copy(center=MoodPetalBowlPoint(target.x+sign*(2+.4*id)*exp(-8*v)*sin(13*v)*e,target.y-(18+2*id)*exp(-7*v)*abs(sin(14*v))*e),
                angle=layout.angles[id]+sign*24*exp(-7*v)*sin(14*v)*e) }
        return p
    }
    fun authored(time: Double,records: List<MoodPetalBowlMood?> = initialRecords): MoodPetalBowlScene {
        val t=max(0.0,time)%LOOP;var s=rest(records);val tokens=s.tokens.toMutableList()
        if(t<3.14) {
            val ids=listOf(0,2,1,3)
            ids.forEachIndexed { rank,id -> tokens[id]=flight(id,MoodPetalBowlMood.Calm,t-1.10-rank*.075,.56) }
            var jostle=0.0
            ids.forEachIndexed { rank,id -> val age=t-(1.10+rank*.075+contactTime(id,MoodPetalBowlMood.Calm));if(age>0&&age<.45) jostle=max(jostle,sin(PI*age/.45).pow(2)) }
            val impulses=listOf(Triple(-3.0,-4.0,-4.0),Triple(2.0,-6.0,5.0),Triple(3.0,-3.0,-3.0))
            for(i in 4..6) {val d=impulses[i-4];val p=tokens[i];tokens[i]=p.copy(center=MoodPetalBowlPoint(p.center.x+d.first*jostle,p.center.y+d.second*jostle),angle=p.angle+d.third*jostle)}
            if(t>=2.82) {val q=ease((t-2.82)/.30);for(i in 0..6) {val p=tokens[i];tokens[i]=p.copy(alpha=p.alpha*(1-q),center=p.center.copy(y=p.center.y+5*q))} }
        } else if(t<5.94) {
            listOf(0,2,1,4,3,5,6).forEachIndexed { rank,id -> tokens[id]=flight(id,MoodPetalBowlMood.Bright,t-3.14-rank*.085,.56) }
            if(t>=5.55) {val q=ease((t-5.55)/.33);for(i in 0..6) {val p=tokens[i];tokens[i]=p.copy(alpha=p.alpha*(1-q),center=p.center.copy(y=p.center.y+5*q))} }
        } else listOf(4,5,6).forEachIndexed { rank,id -> tokens[id]=flight(id,MoodPetalBowlMood.Calm,t-5.94-rank*.070,.56) }
        s=s.copy(tokens=tokens)
        if(t>=2.94&&t<5.72) {val q=ease((t-2.94)/.24);val dots=s.dots.toMutableList();dots[3]=MoodPetalBowlRGBA.mix(MoodPetalBowlRGBA.hex(MoodPetalBowlMood.Calm.hex),MoodPetalBowlRGBA.hex(MoodPetalBowlMood.Bright.hex),q)
            s=s.copy(labelWeights=listOf(1-q,0.0,q,0.0),labelOffsets=listOf(12*q,0.0,-12*(1-q),0.0),dots=dots)
        } else if(t>=5.72&&t<7.25) {val q=ease((t-5.72)/.24);val dots=s.dots.toMutableList();dots[3]=MoodPetalBowlRGBA.mix(MoodPetalBowlRGBA.hex(MoodPetalBowlMood.Bright.hex),MoodPetalBowlRGBA.hex(MoodPetalBowlMood.Calm.hex),q)
            s=s.copy(labelWeights=listOf(q,0.0,1-q,0.0),labelOffsets=listOf(-12*(1-q),0.0,12*q,0.0),dots=dots) }
        return if(t>=7.25) rest(records) else s
    }
    fun transition(from: MoodPetalBowlScene,mood: MoodPetalBowlMood,day: Int,records: List<MoodPetalBowlMood?>,direction: Double,velocityX: Double=0.0)=MoodPetalBowlTransition(
        from,mood,day,colors(records,day,mood),direction,min(.15,abs(velocityX)/6000),from.tokens.map { p -> MoodPetalBowlGeometry.contained(p,p.copy(center=p.center.copy(y=p.center.y+5))) })
    fun transitioning(plan: MoodPetalBowlTransition,elapsed: Double): MoodPetalBowlScene {
        if(elapsed<=0) return plan.from
        val q=ease(elapsed/.24)
        val weights=List(4) { i -> plan.from.labelWeights[i]*(1-q)+if(i==plan.mood.ordinal) q else 0.0 }
        val offsets=List(4) { i -> val target=i==plan.mood.ordinal;val start=if(target&&plan.from.labelWeights[i]==0.0) -12*plan.direction else plan.from.labelOffsets[i]
            if(target) start*(1-q) else start+12*plan.direction*q }
        val tokens=if(elapsed<.25) {val fade=ease(elapsed/.25);plan.from.tokens.zip(plan.outgoing).map { (a,b) -> MoodPetalBowlGeometry.interpolate(a,b,fade).copy(alpha=a.alpha*(1-fade)) } }
        else {val result=plan.from.tokens.toMutableList();listOf(0,2,1,4,3,5,6).forEachIndexed { rank,id -> val delay=max(0.0,rank*.070-rank*plan.speedup/6)
            result[id]=flight(id,plan.mood,elapsed-.25-delay,.50) };result }
        return plan.from.copy(tokens=tokens,labelWeights=weights,labelOffsets=offsets,dots=plan.from.dots.zip(plan.dots).map { MoodPetalBowlRGBA.mix(it.first,it.second,q) },
            capsuleX=plan.from.capsuleX+(dateX[plan.day]-plan.from.capsuleX)*capsuleProgress(elapsed),selectedDay=plan.day,captionAlpha=plan.from.captionAlpha*(1-q))
    }
    fun stirring(from: MoodPetalBowlScene,anchor: MoodPetalBowlPoint,translation: MoodPetalBowlPoint,velocity: MoodPetalBowlPoint): MoodPetalBowlScene {
        if(translation==MoodPetalBowlPoint(0.0,0.0)&&velocity==MoodPetalBowlPoint(0.0,0.0)) return from
        val vx=clamp(velocity.x,-900.0,900.0);val vy=clamp(velocity.y,-900.0,900.0)
        val x=28*tanh((translation.x+.018*vx)/48);val y=20*tanh((translation.y+.018*vy)/48);val rotation=35*tanh(vx/900+translation.x/80)
        return from.copy(tokens=from.tokens.map { p -> if(p.alpha<=0) p else {val w=max(0.0,1-p.center.distance(anchor)/110).pow(2)
            MoodPetalBowlGeometry.contained(p,p.copy(center=MoodPetalBowlPoint(p.center.x+x*w,p.center.y+y*w),angle=p.angle+rotation*w))} })
    }
    fun released(from: MoodPetalBowlScene,base: MoodPetalBowlScene,elapsed: Double,cancelled: Boolean): MoodPetalBowlScene {
        if(elapsed<=0) return from
        val duration=if(cancelled) .24 else .70;if(elapsed>=duration) return base
        val weight=if(cancelled) 1-smooth(elapsed/duration) else springRemaining(elapsed,.48,.72)*(1-smooth(elapsed/duration))
        return from.copy(tokens=base.tokens.zip(from.tokens).map { (a,b) -> MoodPetalBowlGeometry.contained(a,MoodPetalBowlGeometry.interpolate(a,b,weight)) })
    }
    fun recovering(from: MoodPetalBowlScene,elapsed: Double,records: List<MoodPetalBowlMood?>): MoodPetalBowlScene {
        if(elapsed<=0) return from
        val target=rest(records);if(elapsed>=.70) return target
        val q=io(elapsed/.70)
        val tokens=if(elapsed<.20) {val fade=ease(elapsed/.20);from.tokens.map { it.copy(alpha=it.alpha*(1-fade)) }} else {val fade=ease((elapsed-.20)/.50);target.tokens.map { it.copy(alpha=it.alpha*fade) }}
        return from.copy(tokens=tokens,labelWeights=List(4){from.labelWeights[it]+(target.labelWeights[it]-from.labelWeights[it])*q},labelOffsets=from.labelOffsets.map{it*(1-q)},
            dots=from.dots.zip(target.dots).map{MoodPetalBowlRGBA.mix(it.first,it.second,q)},capsuleX=from.capsuleX+(target.capsuleX-from.capsuleX)*q,selectedDay=3,captionAlpha=from.captionAlpha*(1-q))
    }
    private fun capsuleProgress(time: Double): Double {if(time<=0) return 0.0;if(time>=.50) return 1.0
        return 1-springRemaining(time,.30,.85)*(1-smooth((time-.35)/.15)) }
    fun springRemaining(time: Double,response: Double,damping: Double): Double {val omega=2*PI/response;val beta=omega*sqrt(1-damping*damping)
        return exp(-damping*omega*time)*(cos(beta*time)+damping*omega/beta*sin(beta*time)) }
    private fun bezier(value: Double,x1: Double,y1: Double,x2: Double,y2: Double): Double {
        val x=clamp(value);if(x==0.0||x==1.0) return x
        fun f(t: Double,a: Double,b: Double): Double {val s=1-t;return 3*s*s*t*a+3*s*t*t*b+t*t*t}
        var lo=0.0;var hi=1.0;repeat(26) {val m=(lo+hi)/2;if(f(m,x1,x2)<x) lo=m else hi=m}
        return f((lo+hi)/2,y1,y2)
    }
}
