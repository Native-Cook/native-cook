package com.ngocdevv.nativecook.blocks.charts.focusinhometiles
import kotlin.math.*

internal object FocusInHomeTilesMotion {
// Numerical focus/scroll/orange port of RN fht-motion.ts at 58acf81b; all distances are reference points.
data class Group(var page:Double,var plane:Double,var rect:MutableList<Double>)
data class GroupGeo(var cx:Double,var cy:Double,var dy:Double,var scale:Double)
data class Focus(var r:Double,var a:Double)
data class OrangePose(var x:Double,var y:Double,var w:Double,var h:Double,var r:Double,var th:Double)
data class Front(var front:Double,var dir:Double)
data class Degree(var a:Double,var b:Double,var k:Double)
data class PointScale(var x:Double,var y:Double,var k:Double)
data class Dot(var x:Double,var y:Double,var d:Double)
data class BudgetRect(var x:Double,var y:Double,var w:Double,var h:Double,var r:Double)
data class Bar(var x:Double,var top:Double)
val PITCH=780.0;val PLANE_DEFOCUS=9.0;val BAR_FOOT=697.0;val BAR_W=44.0;val PICK_REST=3.0
object G {val header=0.0;val bedroom=1.0;val chat=2.0;val strip=3.0;val chart=4.0;val mode=5.0;val picker=6.0;val alert=7.0}
object SPRING {object glide {val d=22.1;val k=157.9};object pop {val d=19.0;val k=195.0};object snap {val d=30.5;val k=322.3};object detent {val d=26.0;val k=420.0}}
object ORANGE {val dot=Dot(x=170.0,y=670.0,d=8.0);val lamp=Dot(x=292.0,y=690.0,d=200.0);val budget=BudgetRect(x=282.0,y=470.0,w=104.0,h=222.0,r=40.0)}
val GROUPS=mutableListOf(Group(page=0.0,plane=0.0,rect=mutableListOf(16.0,70.0,370.0,117.0)),Group(page=0.0,plane=1.0,rect=mutableListOf(16.0,209.0,370.0,307.0)),Group(page=0.0,plane=2.0,rect=mutableListOf(16.0,540.0,370.0,176.0)),Group(page=1.0,plane=0.0,rect=mutableListOf(16.0,70.0,370.0,117.0)),Group(page=1.0,plane=1.0,rect=mutableListOf(16.0,233.0,370.0,480.0)),Group(page=2.0,plane=0.0,rect=mutableListOf(16.0,70.0,370.0,120.0)),Group(page=2.0,plane=1.0,rect=mutableListOf(16.0,218.0,370.0,222.0)),Group(page=2.0,plane=2.0,rect=mutableListOf(16.0,470.0,252.0,222.0)))
val BARS=mutableListOf(Bar(x=90.0,top=470.0),Bar(x=149.0,top=499.0),Bar(x=208.0,top=529.0),Bar(x=268.0,top=440.0),Bar(x=327.0,top=381.0))
data class Vec2(var x:Double,var y:Double)
data class RopePoints(var x:MutableList<Double>,var y:MutableList<Double>)
private fun nonzero(a:Double,b:Double)=if(a!=0.0)a else b
private val <T> List<T>.length:Double get()=size.toDouble()
private operator fun <T> MutableList<T>.get(i:Double)=get(i.toInt())
private operator fun <T> MutableList<T>.set(i:Double,v:T){set(i.toInt(),v)}
private fun <T> List<T>.safe(i:Double)=getOrNull(i.toInt())
var LOOP:Double = 10.0
fun clamp01(v:Double):Double {
return min(max(v,0.0),1.0)
}
fun seg(t:Double,a:Double,b:Double):Double {
return clamp01((((t - a)) / ((b - a))))
}
fun mix(a:Double,b:Double,k:Double):Double {
return (a + (((b - a)) * k))
}
fun smooth(k:Double):Double {
return ((k * k) * ((3.0 - (2.0 * k))))
}
fun easeOut(k:Double):Double {
return (1.0 - ((((1.0 - k)) * ((1.0 - k))) * ((1.0 - k))))
}
fun easeIn(k:Double):Double {
return ((k * k) * k)
}
fun easeInOut(k:Double):Double {
return (if((k < 0.5)) (((4.0 * k) * k) * k) else (1.0 - ((((-2.0 * k) + 2.0)).pow(3.0) / 2.0)))
}
fun springAt(t:Double,from:Double,to:Double,v0:Double,d:Double,k:Double):Double {
if((t <= 0.0)) {
return from
}
var x0:Double = (from - to)
var w0:Double = sqrt(k)
var z:Double = (d / ((2.0 * w0)))
if((z < 1.0)) {
var wd:Double = (w0 * sqrt((1.0 - (z * z))))
var e:Double = exp(((-z * w0) * t))
return (to + (e * (((x0 * cos((wd * t))) + (((((v0 + ((z * w0) * x0))) / wd)) * sin((wd * t)))))))
}
var s:Double = sqrt(((z * z) - 1.0))
var r1:Double = (-w0 * ((z - s)))
var r2:Double = (-w0 * ((z + s)))
var c2:Double = (((v0 - (r1 * x0))) / ((r2 - r1)))
var c1:Double = (x0 - c2)
return ((to + (c1 * exp((r1 * t)))) + (c2 * exp((r2 * t))))
}
data class Frame(
var s:Double,
var fp:Double,
var front:Double,
var dir:Double,
var grow:MutableList<Double>,
var flood:Double,
var dip:Double,
var degA:Double,
var degB:Double,
var degK:Double,
var pick:Double,
var alert:Double,
var budget:Double,
var ox:Double,
var oy:Double,
var ow:Double,
var oh:Double,
var or:Double,
var oth:Double,
var olamp:Double,
var glyph:Double,
var haze:Double,
var sharp:Double
)
fun paperAt(x:Double,y:Double,front:Double,dir:Double):Double {
if((front >= 1.9)) {
return 1.0
}
var diag:Double = ((((0.35 * x)) / 402.0) + (((0.65 * y)) / 874.0))
var m:Double = smooth(clamp01(((((front - diag) + 0.14)) / 0.28)))
return (if((dir > 0.0)) m else (1.0 - m))
}
fun groupGeo(g:Double,s:Double,fp:Double):GroupGeo {
var grp:Group = GROUPS[g]
var cx:Double = (grp.rect[0.0] + (grp.rect[2.0] / 2.0))
var cy:Double = (grp.rect[1.0] + (grp.rect[3.0] / 2.0))
var u:Double = (s / PITCH)
var par:Double = sin((PI * ((u - floor(u)))))
var near:Double = (((2.0 - grp.plane)) / 2.0)
var dy:Double = (((grp.page * PITCH) - s) - ((6.0 * near) * par))
var scale:Double = (1.0 + (0.03 * clamp01((fp - grp.plane))))
return GroupGeo(cx=cx,cy=cy,dy=dy,scale=scale)
}
fun toScreen(g:Double,s:Double,fp:Double,x:Double,y:Double):PointScale {
var q:GroupGeo = groupGeo(g,s,fp)
return PointScale(x=(q.cx + (((x - q.cx)) * q.scale)),y=((q.cy + (((y - q.cy)) * q.scale)) + q.dy),k=q.scale)
}
fun groupFocus(g:Double,f:Frame):Focus {
if((f.sharp > 0.5)) {
return Focus(r=0.0,a=1.0)
}
var grp:Group = GROUPS[g]
var q:GroupGeo = groupGeo(g,f.s,f.fp)
var m:Double = paperAt(q.cx,(q.cy + q.dy),f.front,f.dir)
var r:Double = (((PLANE_DEFOCUS * abs((grp.plane - f.fp))) + (22.0 * ((1.0 - m)))) + f.haze)
if((g == G.alert)) {
r += (12.0 * ((1.0 - f.alert)))
}
var a:Double = (1.0 - (0.15 * smooth(clamp01((r / 18.0)))))
return Focus(r=r,a=a)
}
var BAR5_FULL:Double = (BAR_FOOT - BARS[4.0].top)
fun barHeight(i:Double,grow:Double):Double {
var full:Double = (BAR_FOOT - BARS[i].top)
return (BAR_W + (((full - BAR_W)) * grow))
}
fun budgetHeight(pct:Double):Double {
return (((ORANGE.budget.h * pct)) / 120.0)
}
fun chainAt(s:Double,fp:Double,grow5:Double,budget:Double):OrangePose {
var h5:Double = (BAR_W + (((BAR5_FULL - BAR_W)) * grow5))
var top5:Double = (BAR_FOOT - h5)
var b0:PointScale = toScreen(G.chart,s,fp,(BARS[4.0].x + (BAR_W / 2.0)),(top5 + (h5 / 2.0)))
var bw:Double = (BAR_W * b0.k)
var bh:Double = (h5 * b0.k)
if((s <= PITCH)) {
var q:Double = (s / PITCH)
var d:PointScale = toScreen(G.chat,s,fp,ORANGE.dot.x,ORANGE.dot.y)
var k:Double = smooth(seg(q,0.04,0.96))
var ks:Double = smooth(seg(q,0.3,0.92))
var w:Double = mix(ORANGE.dot.d,bw,ks)
var h:Double = mix(ORANGE.dot.d,bh,ks)
return OrangePose(x=mix(d.x,b0.x,k),y=mix(d.y,b0.y,k),w=w,h=h,r=(min(w,h) / 2.0),th=0.0)
}
var o:Double = clamp01((((s - PITCH)) / PITCH))
var k:Double = smooth(o)
var hb:Double = budgetHeight(budget)
var B:BudgetRect = ORANGE.budget
var bx:Double = (B.x + (B.w / 2.0))
var by:Double = ((((B.y + B.h) - (hb / 2.0)) + (2.0 * PITCH)) - s)
var w:Double = mix(bw,B.w,k)
var h:Double = mix(bh,hb,k)
return OrangePose(x=min((mix(b0.x,bx,k) + (18.0 * sin((PI * k)))),(390.0 - (w / 2.0))),y=mix(b0.y,by,k),w=w,h=h,r=mix((bw / 2.0),min(B.r,(hb / 2.0)),k),th=k)
}
fun scrollAt(t:Double):Double {
if((t < 1.8)) {
return 0.0
}
if((t < 3.2)) {
return (PITCH * easeInOut(seg(t,1.8,3.2)))
}
if((t < 4.9)) {
return PITCH
}
if((t < 6.3)) {
return (PITCH * ((1.0 + easeInOut(seg(t,4.9,6.3)))))
}
if((t < 9.65)) {
return (2.0 * PITCH)
}
return 0.0
}
fun focusAt(t:Double):Double {
if((t < 0.75)) {
return 0.0
}
if((t < 1.3)) {
return (2.0 * easeInOut(seg(t,0.75,1.3)))
}
if((t < 1.9)) {
return 2.0
}
if((t < 2.45)) {
return (2.0 - (2.0 * easeInOut(seg(t,1.9,2.45))))
}
if((t < 3.0)) {
return 0.0
}
if((t < 3.55)) {
return easeInOut(seg(t,3.0,3.55))
}
if((t < 7.55)) {
return 1.0
}
if((t < 8.1)) {
return (1.0 + easeInOut(seg(t,7.55,8.1)))
}
if((t < 9.65)) {
return 2.0
}
return 0.0
}
fun frontAt(t:Double):Front {
if((t < 1.4)) {
var k:Double = seg(t,0.0,1.4)
return Front(front=mix(-0.22,1.32,mix(k,smooth(k),0.35)),dir=1.0)
}
if((t < 8.4)) {
return Front(front=2.0,dir=1.0)
}
var k:Double = seg(t,8.4,9.6)
return Front(front=mix(-0.22,1.32,mix(k,(k * k),0.4)),dir=-1.0)
}
var DEG_STEPS:MutableList<Double> = mutableListOf(2.3,2.6,2.9)
var DEG_DUR:Double = 0.26
fun degAt(t:Double):Degree {
if((t >= 9.65)) {
return Degree(a=19.0,b=19.0,k=0.0)
}
run {
var i:Double = (DEG_STEPS.length - 1.0)
while((i >= 0.0)) {
try {
var t0:Double = DEG_STEPS[i]
if((t >= t0)) {
return Degree(a=(19.0 + i),b=(20.0 + i),k=easeOut(seg(t,t0,(t0 + DEG_DUR))))
}
} finally { i -= 1.0 }
}
}
return Degree(a=19.0,b=19.0,k=0.0)
}
fun pickAt(t:Double):Double {
if(((t < 6.4) || (t >= 9.65))) {
return 1.0
}
if((t < 7.05)) {
return springAt((t - 6.4),1.0,2.0,0.0,SPRING.detent.d,SPRING.detent.k)
}
return springAt((t - 7.05),2.0,3.0,0.0,SPRING.detent.d,SPRING.detent.k)
}
fun growAt(t:Double,i:Double):Double {
if((t >= 9.65)) {
return 0.0
}
var t0:Double = (if((i < 4.0)) (3.0 + (0.09 * i)) else 3.42)
return springAt((t - t0),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)
}
var RACK_A:Double = 0.75
var RACK_B:Double = 1.35
var SWELL_A:Double = 8.5
var SWELL_B:Double = 9.4
fun frameAt(t:Double,viewportHeight:Double=874.0):Frame {
var s:Double = scrollAt(t)
var fp:Double = focusAt(t)
var front:Double = frontAt(t).front
var dir:Double = frontAt(t).dir
var grow:MutableList<Double> = mutableListOf(growAt(t,0.0),growAt(t,1.0),growAt(t,2.0),growAt(t,3.0),growAt(t,4.0))
var flood:Double = (if((t >= 9.65)) 0.0 else easeOut(seg(t,3.66,4.5)))
var dip:Double = ((2.0 * sin((PI * seg(t,3.66,3.96)))) * ((if((t < 9.65)) 1.0 else 0.0)))
var deg:Degree = degAt(t)
var budget:Double = 120.0
var L:Dot = ORANGE.lamp
var lampY:Double = (L.y - max(0.0,(874.0 - viewportHeight)))
var ox:Double = L.x
var oy:Double = lampY
var ow:Double = L.d
var oh:Double = L.d
var or:Double = (L.d / 2.0)
var oth:Double = 1.0
var olamp:Double = 1.0
if(((t >= RACK_A) && (t < SWELL_A))) {
var c:OrangePose = chainAt(s,fp,grow[4.0],budget)
ox = c.x
oy = c.y
ow = c.w
oh = c.h
or = c.r
oth = c.th
olamp = 0.0
if((t < RACK_B)) {
var k:Double = seg(t,RACK_A,RACK_B)
var kp:Double = easeInOut(k)
var dd:Double = mix(L.d,ORANGE.dot.d,(k * ((2.0 - k))))
ox = mix(L.x,c.x,kp)
oy = mix(lampY,c.y,kp)
ow = dd
oh = dd
or = (dd / 2.0)
olamp = (1.0 - smooth(seg(k,0.55,1.0)))
oth = 0.0
} else {
if((t < 1.9)) {
var bump:Double = springAt((t - RACK_B),0.0,0.0,70.0,SPRING.pop.d,SPRING.pop.k)
ow += bump
oh += bump
or = (ow / 2.0)
}
}
if((t >= 6.3)) {
oy += springAt((t - 6.3),0.0,0.0,150.0,SPRING.pop.d,SPRING.pop.k)
}
} else {
if(((t >= SWELL_A) && (t < SWELL_B))) {
var c:OrangePose = chainAt(s,fp,grow[4.0],budget)
var k:Double = easeInOut(seg(t,SWELL_A,SWELL_B))
ox = mix(c.x,L.x,k)
oy = mix(c.y,lampY,k)
ow = mix(c.w,L.d,k)
oh = mix(c.h,L.d,k)
or = mix(c.r,(L.d / 2.0),smooth(k))
oth = 1.0
olamp = smooth(seg(k,0.05,0.5))
}
}
var glyph:Double = (if((t < 6.3)) 0.0 else (if((t < SWELL_A)) easeOut(seg(t,6.32,6.8)) else (1.0 - seg(t,SWELL_A,(SWELL_A + 0.25)))))
return Frame(s=s,fp=fp,front=front,dir=dir,grow=grow,flood=flood,dip=dip,degA=deg.a,degB=deg.b,degK=deg.k,pick=pickAt(t),alert=1.0,budget=budget,ox=ox,oy=oy,ow=ow,oh=oh,or=or,oth=oth,olamp=olamp,glyph=glyph,haze=(if(((t >= 8.4) && (t < 9.65))) (18.0 * smooth(seg(t,8.4,9.3))) else 0.0),sharp=0.0)
}
data class Live(
var s:Double,
var fp:Double,
var budget:Double,
var alert:Double,
var dark:Double,
var lx:Double,
var ly:Double,
var sharp:Double
)
fun liveFrameAt(L:Live):Frame {
var s:Double = L.s
var sp:Double = min(max(s,0.0),PITCH)
var grow:MutableList<Double> = mutableListOf(easeOut(seg(sp,420.0,700.0)),easeOut(seg(sp,450.0,720.0)),easeOut(seg(sp,480.0,740.0)),easeOut(seg(sp,510.0,760.0)),smooth(seg(sp,560.0,780.0)))
var flood:Double = smooth(seg(sp,640.0,780.0))
var c:OrangePose = chainAt(min(max(s,0.0),(2.0 * PITCH)),L.fp,grow[4.0],L.budget)
var k:Double = easeInOut(clamp01(L.dark))
var lamp:Double = ORANGE.lamp.d
var onBudget:Double = clamp01((((s - PITCH)) / PITCH))
return Frame(s=s,fp=L.fp,front=(if((L.dark > 0.001)) mix(-0.22,1.32,L.dark) else 2.0),dir=-1.0,grow=grow,flood=flood,dip=0.0,degA=22.0,degB=22.0,degK=0.0,pick=PICK_REST,alert=L.alert,budget=L.budget,ox=mix(c.x,L.lx,k),oy=mix(c.y,L.ly,k),ow=mix(c.w,lamp,k),oh=mix(c.h,lamp,k),or=mix(c.r,(lamp / 2.0),smooth(k)),oth=c.th,olamp=smooth(seg(k,0.15,0.85)),glyph=(if((onBudget > 0.98)) (1.0 - seg(k,0.0,0.3)) else 0.0),haze=(18.0 * k),sharp=L.sharp)
}
var PAGE_REST_T:MutableList<Double> = mutableListOf(1.75,4.4,8.15)
}
