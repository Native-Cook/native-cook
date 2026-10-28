package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import kotlin.math.*

/** Exact finite-material feed and cached 17-guide right return from the frozen RN source. */
internal object TrophySavingsMilestoneRibbonGeometry {
    const val MATERIAL_LENGTH = 250.0
    const val FACE_WIDTH = 34.0
    const val TIP_RADIUS = 9.0
    data class Point(val x: Double, val y: Double) {
        operator fun plus(b: Point) = Point(x + b.x, y + b.y)
        operator fun minus(b: Point) = Point(x - b.x, y - b.y)
        operator fun times(b: Double) = Point(x * b, y * b)
        fun unit() = this * (1 / max(0.000001, hypot(x, y)))
        fun normal() = Point(-y, x)
    }
    data class Station(val center: Point, val tangent: Point, val width: Double, val facing: Double, val joinBlend: Double)
    data class Span(val a: Station, val b: Station, val foreground: Boolean)
    data class Roll(val center: Point, val tangent: Point, val width: Double, val opacity: Double, val foreground: Boolean)
    data class Frame(val headDistance: Double, val tailDistance: Double, val bodyLength: Double,
        val tipLength: Double, val coiledLength: Double, val coilScale: Double, val coilJoint: Point,
        val coilWidth: Double, val coilOpacity: Double, val meshOpacity: Double, val spans: List<Span>, val roll: Roll)
    private data class Curve(val a: Point, val b: Point, val c: Point, val d: Point)
    private data class GuidePoint(val distance: Double, val center: Point, val tangent: Point, val facing: Double)
    private data class Guide(val points: List<GuidePoint>, val seam: Double, val length: Double, val feedLength: Double, val boundaries: List<Double>)
    private data class Pairing(val a: Guide, val b: Guide, val mix: Double)
    val FEED_JOINT = Point(35.24, 154.95)
    private fun smooth(x: Double) = TrophySavingsMilestoneMotion.smooth(x)
    private fun clamp(x: Double, low: Double = 0.0, high: Double = 1.0) = x.coerceIn(low, high)
    private fun lerp(a: Double, b: Double, q: Double) = a + (b - a) * q
    private fun pointLerp(a: Point, b: Point, q: Double) = Point(lerp(a.x, b.x, q), lerp(a.y, b.y, q))
    private val curves = listOf(
        Curve(FEED_JOINT, Point(30.0,135.0), Point(56.0,94.0), Point(88.0,115.0)),
        Curve(Point(88.0,115.0), Point(118.0,144.0), Point(156.0,144.0), Point(185.0,133.0)),
        Curve(Point(185.0,133.0), Point(226.0,111.0), Point(268.0,132.0), Point(275.0,160.0)),
        Curve(Point(275.0,160.0), Point(252.0,139.0), Point(122.0,155.0), Point(128.0,169.0)),
        Curve(Point(128.0,169.0), Point(137.0,212.0), Point(177.0,223.0), Point(212.0,207.0)),
        Curve(Point(212.0,207.0), Point(249.0,194.0), Point(280.0,183.0), Point(300.0,188.0)),
    )
    private fun cubic(c: Curve, t: Double): Point {
        val u = 1 - t
        return c.a * (u*u*u) + c.b * (3*u*u*t) + c.c * (3*u*t*t) + c.d * (t*t*t)
    }
    private fun tangent(c: Curve, t: Double): Point {
        val u = 1-t
        return ((c.b-c.a)*(3*u*u) + (c.c-c.b)*(6*u*t) + (c.d-c.c)*(3*t*t)).unit()
    }
    private fun facing(curve: Int, t: Double, release: Double): Double {
        if (curve == 1) return cos(PI * smooth((t-0.25)/0.75))
        if (curve == 2) return -1.0
        if (curve == 3) return -cos(PI*0.5*smooth((t-0.65)/0.35))
        if (curve == 4) return sin(PI*0.5*smooth(t/0.28)) * (1-2*smooth(release/0.38)*sin(PI*t).pow(2))
        if (curve in 5..7) return 1.0
        if (curve >= 8) return -1.0
        return 1.0
    }
    private fun layer(curve: Int, t: Double, release: Double): Boolean {
        if (curve == 4) return release < 0.19
        if (curve == 5) return true
        return curve == 6 || (curve == 7 && t < 0.5)
    }
    private fun returnCurves(release: Double): List<Curve> {
        val lift = smooth(release/0.38)
        val c = curves.toMutableList()
        c[3] = c[3].copy(c=pointLerp(c[3].c,Point(116.0,138.0),lift), d=pointLerp(c[3].d,Point(138.0,145.0),lift))
        c[4] = c[4].copy(a=c[3].d, b=pointLerp(c[4].b,Point(160.0,152.0),lift),
            c=pointLerp(c[4].c,Point(230.0,151.0),lift),d=pointLerp(c[4].d,Point(250.0,172.0),lift))
        c[5] = c[5].copy(a=c[4].d,b=pointLerp(c[5].b,Point(267.0,190.0),lift),c=pointLerp(c[5].c,Point(281.0,198.0),lift))
        val outgoing = c[5].d-c[5].c
        return c + listOf(
            Curve(Point(300.0,188.0),c[5].d+outgoing,Point(337.0,174.0),Point(340.0,194.0)),
            Curve(Point(340.0,194.0),Point(346.0,234.0),Point(305.0,202.0),Point(275.0,142.0)),
            Curve(Point(275.0,142.0),Point(252.0,119.0),Point(205.0,116.0),Point(178.0,128.0)),
            Curve(Point(178.0,128.0),Point(130.0,151.0),Point(105.0,132.0),Point(70.0,115.0)),
            Curve(Point(70.0,115.0),Point(42.0,100.0),Point(30.0,135.0),FEED_JOINT),
        )
    }
    private fun makeGuide(input: List<Curve>, release: Double = 0.0): Guide {
        val points = ArrayList<GuidePoint>(input.size*64+1)
        points.add(GuidePoint(0.0,input[0].a,tangent(input[0],0.0),1.0))
        var distance = 0.0; var prior = input[0].a; var seam = 0.0; var feed = 0.0
        val boundaries = ArrayList<Double>(3)
        for (curve in input.indices) {
            for (i in 1..64) {
                val t = i/64.0; val center = cubic(input[curve],t)
                distance += hypot(center.x-prior.x,center.y-prior.y)
                points.add(GuidePoint(distance,center,tangent(input[curve],t),facing(curve,t,release)))
                prior = center
                if (curve == 7 && i == 32) boundaries.add(distance)
            }
            if (curve == 3) { seam = distance; boundaries.add(distance) }
            if (curve == 5) { feed = distance; boundaries.add(distance) }
        }
        return Guide(points,seam,distance,feed,boundaries)
    }
    private val guide = makeGuide(curves)
    private val returnGuides = Array(17) { makeGuide(returnCurves(it/16.0),it/16.0) }
    private fun pair(release: Double): Pairing {
        if (release < 0) return Pairing(guide,guide,0.0)
        val q = clamp(release)*16; val low = min(15,floor(q).toInt())
        return Pairing(returnGuides[low],returnGuides[low+1],q-low)
    }
    private fun sample(distance: Double, release: Double): Pair<GuidePoint,Boolean> {
        val pair = pair(release); val ga=pair.a; val gb=pair.b; val mix=pair.mix
        val d = clamp(distance,0.0,lerp(ga.length,gb.length,mix))
        var low=0; var high=ga.points.lastIndex
        while(low+1<high) {
            val mid=(low+high)/2
            if(lerp(ga.points[mid].distance,gb.points[mid].distance,mix)<=d) low=mid else high=mid
        }
        val a0=ga.points[low]; val a1=gb.points[low]; val b0=ga.points[high]; val b1=gb.points[high]
        val q=(d-lerp(a0.distance,a1.distance,mix))/max(0.000001,lerp(b0.distance,b1.distance,mix)-lerp(a0.distance,a1.distance,mix))
        val front=if(release<0) d>=guide.seam else layer(floor((low+0.001)/64).toInt(),((low+q)%64)/64,release)
        return GuidePoint(d,pointLerp(pointLerp(a0.center,a1.center,mix),pointLerp(b0.center,b1.center,mix),q),
            pointLerp(pointLerp(a0.tangent,a1.tangent,mix),pointLerp(b0.tangent,b1.tangent,mix),q).unit(),
            lerp(lerp(a0.facing,a1.facing,mix),lerp(b0.facing,b1.facing,mix),q)) to front
    }
    private fun station(distance: Double, opening: Double, head: Double, release: Double): Station {
        val p=sample(distance,release).first
        val projected=0.8+(FACE_WIDTH-0.8)*abs(p.facing)*opening
        return Station(p.center,p.tangent,1.3+(projected-1.3)*smooth(distance/22),p.facing,
            smooth((distance-head+22)/22)*smooth((head-45)/65))
    }
    fun frame(progress: Double, pose: Double = progress): Frame {
        val release=if(pose>=1) clamp(pose-1) else -1.0
        val pair=pair(release); val feed=lerp(pair.a.feedLength,pair.b.feedLength,pair.mix)
        val total=lerp(pair.a.length,pair.b.length,pair.mix)
        val rightTurn=if(release<0) feed else lerp(pair.a.points[7*64].distance,pair.b.points[7*64].distance,pair.mix)
        val gather=if(release<0) 0.0 else smooth((release-0.78)/0.22)
        val head=if(release<0) clamp(progress)*guide.length else feed+(rightTurn-feed)*smooth(release/0.5)+(total-rightTurn)*gather
        val tip=36*smooth((head-45)/65)*(1-gather)
        val body=if(release<0) min(head,MATERIAL_LENGTH-tip) else 214*(1-gather)
        val tail=max(0.0,head-body); val coiled=max(0.0,MATERIAL_LENGTH-body-tip)
        val fullRadius=sqrt(1.6*1.6+0.45*MATERIAL_LENGTH/PI); val radius=sqrt(1.6*1.6+0.45*coiled/PI)
        val opening=smooth((head-24)/50)*(1-0.96*gather)
        val spans=ArrayList<Span>(48)
        if(body>0.000001) {
            val distances=ArrayList<Double>(48); distances.add(tail)
            val steps=max(1,ceil(body/6.5).toInt())
            for(i in 1 until steps) distances.add(tail+body*i/steps)
            for(i in pair.a.boundaries.indices) {
                val boundary=lerp(pair.a.boundaries[i],pair.b.boundaries[i],pair.mix)
                if(boundary>tail && boundary<head) distances.add(boundary)
            }
            distances.add(head); distances.sort()
            for(i in 1 until distances.size) {
                val a=distances[i-1]; val b=distances[i]
                if(b-a>0.000001) spans.add(Span(station(a,opening,head,release),station(b,opening,head,release),sample((a+b)*0.5,release).second))
            }
        }
        val end=sample(head,release)
        return Frame(head,tail,body,tip,coiled,radius/fullRadius,if(release<0) FEED_JOINT else end.first.center,
            1.3+0.8*sin(PI*(1-coiled/MATERIAL_LENGTH)),0.75*(if(release<0) smooth(coiled/10) else smooth((gather-0.72)/0.28)),
            smooth(head/8),spans,Roll(end.first.center,end.first.tangent,station(head,opening,head,release).width,
                smooth(tip/36)*(1-smooth((gather-0.58)/0.24)),end.second))
    }
}
