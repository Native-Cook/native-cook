package com.ngocdevv.nativecook.blocks.text.texteffectkeys
import kotlin.math.*

internal object TextEffectKeysMotion {
// Numeric event/timeline port of RN tek-motion.ts at 58acf81b; all distances are reference points.
data class SpringSpec (var d:Double,var k:Double)
object G { val keyW=181.0; val oldY=443.0; val bubbleH=32.0; val bubblePad=11.0; val bubbleRight=384.0; val fieldTextX=69.0; val stageY=240.0; val keyX=mutableListOf(15.0,206.0); val keyY=mutableListOf(606.0,665.0,724.0,783.0); object field { val y=505.0; val h=34.0 } }
object TYPE {val hero=4.0;val stage=56.0/15.0}
object EF {val big=0.0;val small=1.0;val shake=2.0;val nod=3.0;val explode=4.0;val ripple=5.0;val bloom=6.0;val jitter=7.0}
object SPRING {val glide=SpringSpec(d=22.1,k=157.9);val pop=SpringSpec(d=19.0,k=195.0);val snap=SpringSpec(d=30.5,k=322.3);val chain=SpringSpec(d=16.0,k=180.0)}
data class Vec2(var x:Double,var y:Double)
data class Flight(var x:Double,var y:Double,var rot:Double,var s:Double)
data class Keys(var depth:MutableList<Double>,var kick:MutableList<Double>,var active:MutableList<Double>)
private fun nonzero(a:Double,b:Double)=if(a!=0.0)a else b
private val <T> List<T>.length:Double get()=size.toDouble()
private operator fun <T> MutableList<T>.get(i:Double)=get(i.toInt())
private operator fun <T> MutableList<T>.set(i:Double,v:T){set(i.toInt(),v)}
private fun <T> List<T>.safe(i:Double)=getOrNull(i.toInt())
var LOOP:Double = 8.0
var CYCLE:Double = 2.0
var GRAV:Double = 2200.0
var REST:Double = 0.3
fun seg(t:Double,a:Double,b:Double):Double {
return min(max((((t - a)) / ((b - a))),0.0),1.0)
}
fun clamp01(v:Double):Double {
return min(max(v,0.0),1.0)
}
fun mix(a:Double,b:Double,k:Double):Double {
return (a + (((b - a)) * k))
}
fun easeOut(k:Double):Double {
return (1.0 - ((((1.0 - k)) * ((1.0 - k))) * ((1.0 - k))))
}
fun easeInOut(k:Double):Double {
return (if((k < 0.5)) (((4.0 * k) * k) * k) else (1.0 - ((((-2.0 * k) + 2.0)).pow(3.0) / 2.0)))
}
fun smooth(a:Double,b:Double,x:Double):Double {
var k:Double = seg(x,a,b)
return ((k * k) * ((3.0 - (2.0 * k))))
}
fun springAt(t:Double,from:Double,to:Double,v0:Double,sp:SpringSpec):Double {
if((t <= 0.0)) {
return from
}
var x0:Double = (from - to)
var w0:Double = sqrt(sp.k)
var z:Double = (sp.d / ((2.0 * w0)))
if((z < 1.0)) {
var wd:Double = (w0 * sqrt((1.0 - (z * z))))
var e:Double = exp(((-z * w0) * t))
return (to + (e * (((x0 * cos((wd * t))) + (((((v0 + ((z * w0) * x0))) / wd)) * sin((wd * t)))))))
}
var e:Double = exp((-w0 * t))
return (to + (e * ((x0 + (((v0 + (w0 * x0))) * t)))))
}
fun env(p:Double):Double {
return (smooth(0.05,0.15,p) * ((1.0 - smooth(0.8,0.95,p))))
}
fun hash(i:Double):Double {
var s:Double = (sin(((i * 127.1) + 311.7)) * 43758.5453)
return (s - floor(s))
}
fun explodeAlpha(p:Double):Double {
return (if((p < 0.8)) (1.0 - smooth(0.26,0.46,p)) else smooth(0.8,0.9,p))
}
data class Pose(
var dx:Double,
var dy:Double,
var rot:Double,
var sc:Double,
var w:Double,
var blur:Double
)
fun effectPose(e:Double,p:Double,i:Double,n:Double,u:Double,staged:Boolean):Pose {
var f:Double = (if((n > 1.0)) (i / ((n - 1.0))) else 0.0)
var en:Double = env(p)
if((e == EF.big)) {
return Pose(dx=0.0,dy=0.0,rot=0.0,sc=((0.75 * smooth(0.08,0.3,p)) * ((1.0 - smooth(0.72,0.9,p)))),w=0.0,blur=0.0)
}
if((e == EF.small)) {
var q:Double = (p - 0.07)
return Pose(dx=0.0,dy=0.0,rot=0.0,sc=((-0.35 * smooth(0.08,0.3,q)) * ((1.0 - smooth(0.72,0.88,q)))),w=0.0,blur=0.0)
}
if((e == EF.shake)) {
var s:Double = (sin((((2.0 * PI) * 3.0) * p)) * en)
return Pose(dx=((5.0 * u) * s),dy=0.0,rot=(3.0 * s),sc=0.0,w=((0.25 * u) * abs(s)),blur=0.0)
}
if((e == EF.nod)) {
return Pose(dx=0.0,dy=(((4.0 * u) * sin(((((2.0 * PI) * 3.0) * p) + (PI / 2.0)))) * en),rot=0.0,sc=0.0,w=0.0,blur=0.0)
}
if((e == EF.ripple)) {
var dy:Double = 0.0
var rot:Double = 0.0
run {
var j:Double = 0.0
while((j < 2.0)) {
try {
var q:Double = ((((p - 0.08) - (0.43 * j))) / 0.4)
if(((q < -0.3) || (q > 1.3))) {
continue
}
var z:Double = (((((q * 1.5) - 0.25) - f)) / 0.2)
var b:Double = exp((-z * z))
dy -= ((6.0 * u) * b)
rot += ((((12.0 * z) * b)) / 0.43)
} finally { j += 1.0 }
}
}
return Pose(dx=0.0,dy=(dy * en),rot=(rot * en),sc=0.0,w=0.0,blur=0.0)
}
if((e == EF.explode)) {
var out:Double = easeOut(seg(p,0.2,0.44))
var back:Double = seg(p,0.8,0.96)
var k:Double = (if((p < 0.8)) out else (((((1.0 - back)) * ((1.0 - back))) * ((1.0 - back))) - ((0.08 * sin((PI * back))) * ((1.0 - back)))))
var a:Double = (((((-150.0 + (120.0 * f))) * PI)) / 180.0)
var R:Double = ((60.0 * u) * k)
var spin:Double = (((if((hash(i) > 0.5)) 1.0 else -1.0)) * ((40.0 + (25.0 * hash((i + 9.0))))))
var moving:Double = (if((p < 0.8)) sin((PI * seg(p,0.2,0.5))) else sin((PI * back)))
return Pose(dx=(cos(a) * R),dy=(sin(a) * R),rot=(spin * k),sc=0.0,w=0.0,blur=(3.0 * moving))
}
if((e == EF.bloom)) {
var c:Double = (0.18 + (0.64 * f))
var z:Double = (((p - c)) / 0.12)
var b:Double = ((exp((-z * z)) * smooth(0.0,0.08,p)) * ((1.0 - smooth(0.92,1.0,p))))
return Pose(dx=0.0,dy=0.0,rot=0.0,sc=(((if(staged) 0.45 else 0.22)) * b),w=(((if(staged) 3.5 else (0.9 * u))) * b),blur=0.0)
}
var tau:Double = ((2.0 * PI) * p)
var nx:Double = ((0.6 * sin(((7.0 * tau) + (1.3 * i)))) + (0.4 * sin((((11.0 * tau) + (2.1 * i)) + 0.7))))
var ny:Double = ((0.6 * sin((((9.0 * tau) + (0.7 * i)) + 2.0))) + (0.4 * sin(((13.0 * tau) + (1.9 * i)))))
var nr:Double = ((0.6 * sin((((8.0 * tau) + (2.7 * i)) + 1.1))) + (0.4 * sin(((12.0 * tau) + (0.9 * i)))))
return Pose(dx=(((1.1 * u) * nx) * en),dy=(((1.1 * u) * ny) * en),rot=((2.5 * nr) * en),sc=0.0,w=0.0,blur=0.0)
}
data class Geo(
var n:Double,
var fieldX:MutableList<Double>,
var bubbleX:MutableList<Double>,
var stageX:MutableList<Double>,
var w:MutableList<Double>,
var h:MutableList<Double>,
var bubbleL:Double,
var bubbleW:Double,
var spans:MutableList<MutableList<Double>>
)
var FIELD_BASE:Double = ((G.field.y + (G.field.h / 2.0)) + (((0.71 * 15.0)) / 2.0))
var BUBBLE_BASE:Double = ((G.oldY + (G.bubbleH / 2.0)) + (((0.71 * 15.0)) / 2.0))
var MED:MutableList<Double> = mutableListOf(14.52,8.87,8.47,8.47,8.47,8.47,8.47,6.15)
var REG:MutableList<Double> = mutableListOf(14.17,8.71,8.27,8.27,8.27,8.27,8.27,5.88)
fun makeGeo(fieldAdv:MutableList<Double>,bubbleAdv:MutableList<Double>,inkH:MutableList<Double>,spans:MutableList<MutableList<Double>>):Geo {
    fun centres(adv:MutableList<Double>,x0:Double,k:Double):MutableList<Double> {var pen=x0;return adv.map{a->val x=pen+a*k/2;pen+=a*k;x}.toMutableList()}
    val w=bubbleAdv.sum();val bw=w+22;val bl=384-bw
    return Geo(bubbleAdv.size.toDouble(),centres(fieldAdv,69.0,1.0),centres(bubbleAdv,bl+11,1.0),centres(bubbleAdv,201-w*TYPE.stage/2,TYPE.stage),bubbleAdv,inkH,bl,bw,spans)
}

var DEFAULT_GEO:Geo = makeGeo(REG,MED,mutableListOf(10.65,10.65,8.16,8.16,8.16,8.16,8.16,9.86),mutableListOf(mutableListOf(16.0,230.0,300.0),mutableListOf(16.0,120.0,340.0),mutableListOf(282.0,384.0,443.0)))
var LAND:Double = 37.0
var XK:Double = 2.53
var BURST:Double = 0.9
var LAUNCH_STAGGER:Double = 0.03
data class Throw(
var xa:Double,
var ya:Double,
var xl:Double,
var key:Double,
var lean:Double,
var turns:Double,
var vy0:Double,
var ta:Double,
var tl:Double,
var top:Double,
var vb:Double,
var tb:Double,
var launch:Double,
var hit:Double
)
fun keyRect(k:Double):Vec2 {
return Vec2(x=G.keyX[(k % 2.0)],y=G.keyY[floor((k / 2.0))])
}
val THROWS=mutableListOf(Throw(xa=28.0,ya=360.0,xl=165.0,key=6.0,lean=-26.0,turns=0.0,vy0=677.5175274485524,ta=0.3079625124766147,tl=0.9546323031594779,top=820.0,vb=426.80206185068965,tb=0.38800187440971784,launch=0.9,hit=1.854632303159478),Throw(xa=98.0,ya=250.0,xl=62.0,key=0.0,lean=18.0,turns=0.0,vy0=971.0973174713232,ta=0.4414078715778742,tl=1.0391308235152472,top=643.0,vb=394.4971482786662,tb=0.35863377116242384,launch=0.93,hit=1.9691308235152474),Throw(xa=146.0,ya=180.0,xl=154.0,key=2.0,lean=0.0,turns=1.0,vy0=1118.4945239025535,ta=0.508406601773888,tl=1.1972791985183473,top=702.0,vb=454.65591385134314,tb=0.4133235580466756,launch=0.96,hit=2.1572791985183475),Throw(xa=188.0,ya=146.0,xl=170.0,key=4.0,lean=-12.0,turns=0.0,vy0=1183.4821502667457,ta=0.5379464319394299,tl=1.2856702506659095,top=761.0,vb=493.4977203594765,tb=0.4486342912358877,launch=0.99,hit=2.2756702506659092),Throw(xa=228.0,ya=142.0,xl=234.0,key=1.0,lean=14.0,turns=0.0,vy0=1190.894621702525,ta=0.5413157371375114,tl=1.2161894626999645,top=643.0,vb=445.4166588712191,tb=0.4049242353374719,launch=1.02,hit=2.2361894626999645),Throw(xa=270.0,ya=170.0,xl=256.0,key=3.0,lean=0.0,turns=-1.0,vy0=1137.9938488410207,ta=0.5172699312913731,tl=1.212709622160656,top=702.0,vb=458.99019597372666,tb=0.4172638145215697,launch=1.05,hit=2.262709622160656),Throw(xa=318.0,ya=236.0,xl=342.0,key=5.0,lean=-20.0,turns=0.0,vy0=1002.3123265729101,ta=0.45559651207859553,tl=1.1464457917863529,top=761.0,vb=455.96052460711985,tb=0.4145095678246544,launch=1.08,hit=2.226445791786353),Throw(xa=372.0,ya=330.0,xl=366.0,key=7.0,lean=28.0,turns=0.0,vy0=768.7847553119143,ta=0.34944761605087016,tl=1.016871428522785,top=820.0,vb=440.4997162314637,tb=0.4004542874831488,launch=1.31,hit=2.326871428522785))
var RESEAT:Double = 2.46
var RESEAT_STAGGER:Double = 0.035
var PRESSES:MutableList<MutableList<Double>> = mutableListOf(mutableListOf(EF.explode,0.82,1.65),mutableListOf(EF.ripple,3.17,3.32),mutableListOf(EF.bloom,4.6,6.0))
val HITS=mutableListOf(mutableListOf(6.0,1.854632303159478,1.0),mutableListOf(6.0,2.242634177569196,0.45),mutableListOf(0.0,1.9691308235152474,1.0),mutableListOf(0.0,2.3277645946776713,0.45),mutableListOf(2.0,2.1572791985183475,1.0),mutableListOf(2.0,2.570602756565023,0.45),mutableListOf(4.0,2.2756702506659092,1.0),mutableListOf(4.0,2.724304541901797,0.45),mutableListOf(1.0,2.2361894626999645,1.0),mutableListOf(1.0,2.6411136980374366,0.45),mutableListOf(3.0,2.262709622160656,1.0),mutableListOf(3.0,2.679973436682226,0.45),mutableListOf(5.0,2.226445791786353,1.0),mutableListOf(5.0,2.6409553596110076,0.45),mutableListOf(7.0,2.326871428522785,1.0),mutableListOf(7.0,2.7273257160059337,0.45))
var ACTIVE:MutableList<MutableList<Double>> = mutableListOf(mutableListOf(EF.explode,0.84,3.17),mutableListOf(EF.ripple,3.17,4.6),mutableListOf(EF.bloom,4.6,6.72))
fun pressDepth(t:Double,a:Double,b:Double):Double {
if((t < a)) {
return 0.0
}
if((t < (a + 0.06))) {
return easeOut(seg(t,a,(a + 0.06)))
}
if((t < b)) {
return 1.0
}
return max(0.0,springAt((t - b),1.0,0.0,0.0,SPRING.snap))
}
data class Hero(
var x:Double,
var y:Double,
var rot:Double,
var s:Double,
var lime:Double,
var home:Double,
var font:Double,
var w:Double,
var vx:Double,
var vy:Double,
var blur:Double,
var mb:Double
)
data class Frame(
var rise:Double,
var body:Double,
var squash:Double,
var blip:Double,
var shell:Double,
var sendS:Double,
var arrowY:Double,
var placeholder:Double,
var select:Double,
var hero:MutableList<Hero>,
var depth:MutableList<Double>,
var kick:MutableList<Double>,
var active:MutableList<Double>,
var amp:Double,
var keyP:Double,
var dips:MutableList<Double>,
var still:Double=0.0
)
fun flight(h:Throw,tau:Double,sx:Double):Flight {
var x:Double = ((sx + (((h.xa - sx)) * ((1.0 - exp((((-tau * XK)) / h.ta)))))) + (((h.xl - h.xa)) * smooth((h.ta + (0.2 * ((h.tl - h.ta)))),h.tl,tau)))
var y:Double
var rot:Double
if((tau < h.tl)) {
y = ((BUBBLE_BASE - (h.vy0 * tau)) + (((0.5 * GRAV) * tau) * tau))
var u:Double = (tau / h.tl)
rot = ((h.lean * sin((PI * u))) + ((h.turns * 360.0) * easeInOut(seg(u,0.4,1.0))))
} else {
var tp:Double = (tau - h.tl)
y = (if((tp < h.tb)) ((h.top - (h.vb * tp)) + (((0.5 * GRAV) * tp) * tp)) else h.top)
rot = ((h.turns * 360.0) + (((((if((h.lean >= 0.0)) 1.0 else -1.0)) * 9.0) * exp((-7.0 * tp))) * sin((16.0 * tp))))
}
var s:Double = (1.0 + (((TYPE.hero - 1.0)) * easeOut(seg(tau,0.0,0.16))))
return Flight(x=x,y=y,rot=rot,s=s)
}
fun bloomScrub(t:Double):Double {
if((t < 4.66)) {
return 0.0
}
if((t < 5.2)) {
return (0.62 * easeInOut(seg(t,4.66,5.2)))
}
if((t < 5.6)) {
return mix(0.62,0.3,easeInOut(seg(t,5.2,5.6)))
}
if((t < 6.02)) {
return mix(0.3,1.0,easeInOut(seg(t,5.6,6.02)))
}
return 1.0
}
fun heroAt(t:Double,i:Double,geo:Geo,depth:MutableList<Double>):Hero {
var hero:Hero = Hero(x=geo.fieldX[i],y=FIELD_BASE,rot=0.0,s=1.0,lime=0.0,home=0.0,font=0.0,w=0.0,vx=0.0,vy=0.0,blur=0.0,mb=0.0)
var bx:Double = geo.bubbleX[i]
var h:Throw = THROWS[i]
var tSend:Double = (0.28 + (0.018 * i))
var tReseat:Double = (RESEAT + (RESEAT_STAGGER * i))
var tLift:Double = (3.23 + (0.012 * i))
var tBack:Double = (6.05 + (0.02 * i))
var tHome:Double = (6.7 + (0.045 * i))
if((t < tSend)) {
return hero
}
if((t < h.launch)) {
var k:Double = springAt((t - tSend),0.0,1.0,0.0,SPRING.glide)
hero.x = mix(geo.fieldX[i],bx,k)
hero.y = (mix(FIELD_BASE,BUBBLE_BASE,k) - (22.0 * sin((PI * clamp01(k)))))
hero.s = (1.0 + (0.12 * sin((PI * clamp01(k)))))
hero.home = smooth(0.35,0.75,k)
hero.font = (if((k > 0.5)) 1.0 else 0.0)
return hero
}
hero.font = 1.0
hero.home = 1.0
if((t < tReseat)) {
var f:Flight = flight(h,(t - h.launch),bx)
hero.x = f.x
hero.y = (f.y + ((if(((t - h.launch) > h.tl)) (4.0 * depth[h.key]) else 0.0)))
hero.rot = f.rot
hero.s = f.s
hero.lime = seg((t - h.launch),0.0,0.08)
hero.mb = 1.0
return hero
}
if((t < tLift)) {
var r0:Double = (tReseat - h.launch)
var a:Flight = flight(h,r0,bx)
var b:Flight = flight(h,(r0 - 0.004),bx)
var tr:Double = (t - tReseat)
hero.x = springAt(tr,a.x,bx,(((a.x - b.x)) / 0.004),SPRING.pop)
hero.y = springAt(tr,a.y,BUBBLE_BASE,(((a.y - b.y)) / 0.004),SPRING.pop)
hero.rot = springAt(tr,a.rot,(h.turns * 360.0),0.0,SPRING.pop)
hero.s = max(0.8,springAt(tr,a.s,1.0,0.0,SPRING.pop))
hero.lime = (1.0 - smooth(0.28,0.42,tr))
hero.mb = 1.0
return hero
}
if((t < tHome)) {
var k:Double = springAt((t - tLift),0.0,1.0,0.0,SPRING.glide)
if((t >= tBack)) {
k = springAt((t - tBack),1.0,0.0,0.0,SPRING.pop)
}
var sx:Double = geo.stageX[i]
hero.x = mix(bx,sx,k)
hero.y = mix(BUBBLE_BASE,G.stageY,k)
hero.s = mix(1.0,TYPE.stage,k)
hero.lime = (if((t < tBack)) smooth(0.0,0.3,k) else smooth(0.1,0.5,k))
hero.rot = 0.0
var pr:Double = seg(t,3.42,4.72)
if(((pr > 0.0) && (pr < 1.0))) {
var e:Pose = effectPose(EF.ripple,pr,i,geo.n,(28.0 / 6.0),true)
hero.y += e.dy
hero.rot += e.rot
}
var pb:Double = bloomScrub(t)
if(((pb > 0.0) && (pb < 1.0))) {
var push:Double = 0.0
var total:Double = 0.0
run {
var j:Double = 0.0
while((j < geo.n)) {
try {
var ej:Pose = effectPose(EF.bloom,pb,j,geo.n,1.0,true)
var grow:Double = ((ej.sc * geo.w[j]) * TYPE.stage)
if((j < i)) {
push += grow
}
if((j == i)) {
push += (grow / 2.0)
}
total += grow
} finally { j += 1.0 }
}
}
var e:Pose = effectPose(EF.bloom,pb,i,geo.n,1.0,true)
hero.x += (push - (total / 2.0))
hero.s *= (1.0 + e.sc)
hero.w = e.w
}
return hero
}
var k:Double = springAt((t - tHome),0.0,1.0,0.0,SPRING.glide)
hero.x = mix(bx,geo.fieldX[i],k)
hero.y = (mix(BUBBLE_BASE,FIELD_BASE,k) - (14.0 * sin((PI * clamp01(k)))))
hero.home = (1.0 - smooth(0.25,0.65,k))
hero.font = (if((k > 0.5)) 0.0 else 1.0)
return hero
}
fun keysAt(t:Double):Keys {
var depth:MutableList<Double> = mutableListOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
var kick:MutableList<Double> = mutableListOf(-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0)
var active:MutableList<Double> = mutableListOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
run {
var j:Double = 0.0
while((j < PRESSES.length)) {
try {
var k:Double = PRESSES[j][0.0]
var a:Double = PRESSES[j][1.0]
var b:Double = PRESSES[j][2.0]
depth[k] = max(depth[k],pressDepth(t,a,b))
} finally { j += 1.0 }
}
}
run {
var j:Double = 0.0
while((j < HITS.length)) {
try {
var k:Double = HITS[j][0.0]
var a:Double = HITS[j][1.0]
var s:Double = HITS[j][2.0]
if((t < a)) {
continue
}
var d:Double = (if((t < (a + 0.06))) (s * easeOut(seg(t,a,(a + 0.06)))) else max(0.0,springAt(((t - a) - 0.06),s,0.0,0.0,SPRING.snap)))
depth[k] = max(depth[k],d)
if(((s == 1.0) && (t < (a + 0.8)))) {
kick[k] = seg(t,a,(a + 0.8))
}
} finally { j += 1.0 }
}
}
run {
var j:Double = 0.0
while((j < ACTIVE.length)) {
try {
var k:Double = ACTIVE[j][0.0]
var a:Double = ACTIVE[j][1.0]
var b:Double = ACTIVE[j][2.0]
active[k] = max(active[k],(seg(t,a,(a + 0.12)) * ((1.0 - seg(t,b,(b + 0.12))))))
} finally { j += 1.0 }
}
}
return Keys(depth=depth,kick=kick,active=active)
}
fun frameAt(tIn:Double,geo:Geo):Frame {
var t:Double = (((((tIn % LOOP)) + LOOP)) % LOOP)
var keys:Keys = keysAt(t)
var rise:Double = (if((t < 0.28)) 0.0 else (if((t < 6.7)) springAt((t - 0.28),0.0,1.0,0.0,SPRING.glide) else springAt((t - 6.7),1.0,0.0,0.0,SPRING.glide)))
var body:Double = (if((t < 0.3)) 0.0 else (if((t < 6.72)) springAt((t - 0.3),0.0,1.0,0.0,SPRING.pop) else max(0.0,springAt((t - 6.72),1.0,0.0,0.0,SPRING.glide))))
var squash:Double = (if((t < 0.62)) 0.0 else springAt((t - 0.62),0.04,0.0,0.0,SPRING.pop))
var blip:Double = (if((t < (RESEAT + 0.37))) 0.0 else springAt(((t - RESEAT) - 0.37),0.0,0.0,0.7,SPRING.pop))
var shell:Double = 1.0
if(((t >= BURST) && (t < (RESEAT + 0.6)))) {
var away:Double = 0.0
run {
var i:Double = 0.0
while((i < geo.n)) {
try {
away += (smooth(THROWS[i].launch,(THROWS[i].launch + 0.06),t) * ((1.0 - smooth(0.28,0.42,((t - RESEAT) - (RESEAT_STAGGER * i))))))
} finally { i += 1.0 }
}
}
shell = (1.0 - (((0.6 * away)) / geo.n))
} else {
if(((t >= 3.23) && (t < 6.6))) {
shell = (1.0 - ((0.6 * smooth(3.23,3.42,t)) * ((1.0 - smooth(6.15,6.45,t)))))
}
}
var sendS:Double = 1.0
if(((t >= 0.2) && (t < 0.26))) {
sendS = mix(1.0,0.88,easeOut(seg(t,0.2,0.26)))
} else {
if(((t >= 0.26) && (t < 0.4))) {
sendS = mix(0.88,1.06,easeOut(seg(t,0.26,0.4)))
} else {
if(((t >= 0.4) && (t < 0.52))) {
sendS = mix(1.06,1.0,easeInOut(seg(t,0.4,0.52)))
}
}
}
var arrowY:Double = (-2.0 * sin((PI * seg(t,0.2,0.42))))
var placeholder:Double = (if((t < 6.7)) easeOut(seg(t,0.46,0.66)) else (1.0 - easeInOut(seg(t,6.7,6.9))))
var select:Double = (if((t < 1.0)) (1.0 - easeInOut(seg(t,0.2,0.3))) else easeInOut(seg(t,7.3,7.5)))
var hero:MutableList<Hero> = mutableListOf<Hero>()
run {
var i:Double = 0.0
while((i < geo.n)) {
try {
var a:Hero = heroAt(t,i,geo,keys.depth)
var b:Hero = heroAt((t - (1.0 / 240.0)),i,geo,keys.depth)
a.vx = (((a.x - b.x)) * 240.0)
a.vy = (((a.y - b.y)) * 240.0)
hero.add(a)
} finally { i += 1.0 }
}
}
var dips:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var pr:Double = seg(t,3.42,4.72)
if(((pr > 0.0) && (pr < 1.0))) {
run {
var i:Double = 0.0
while((i < geo.n)) {
try {
var e:Pose = effectPose(EF.ripple,pr,i,geo.n,1.0,true)
var lift:Double = (-e.dy / 6.0)
run {
var s:Double = 0.0
while((s < geo.spans.length)) {
try {
if(((hero[i].x >= geo.spans[s][0.0]) && (hero[i].x <= geo.spans[s][1.0]))) {
dips[s] = max(dips[s],(3.0 * lift))
}
} finally { s += 1.0 }
}
}
} finally { i += 1.0 }
}
}
}
var amp:Double = (1.0 - ((0.6 * smooth(0.82,1.02,t)) * ((1.0 - smooth(6.3,6.5,t)))))
var keyP:Double = (((t % CYCLE)) / CYCLE)
return Frame(rise=rise,body=body,squash=squash,blip=blip,shell=shell,sendS=sendS,arrowY=arrowY,placeholder=placeholder,select=select,hero=hero,depth=keys.depth,kick=keys.kick,active=keys.active,amp=amp,keyP=keyP,dips=dips)
}
data class Live(
var x:MutableList<Double>,
var y:MutableList<Double>,
var vx:MutableList<Double>,
var vy:MutableList<Double>,
var rot:MutableList<Double>,
var s:MutableList<Double>,
var lime:MutableList<Double>,
var home:MutableList<Double>,
var font:MutableList<Double>,
var mode:MutableList<Double>,
var wait:MutableList<Double>,
var rest:MutableList<Double>,
var drag:MutableList<Double>,
var target:Double,
var rise:Double,
var riseV:Double,
var body:Double,
var bodyV:Double,
var depth:MutableList<Double>,
var depthV:MutableList<Double>,
var kick:MutableList<Double>,
var held:Double,
var active:Double,
var effW:MutableList<Double>,
var effT:Double,
var amp:Double,
var finger:Double,
var fx:Double,
var fy:Double,
var fvx:Double,
var fvy:Double,
var sendS:Double,
var sendV:Double,
var hapticT:Double,
var returning:Double
)
fun blankLive():Live {
return Live(x=mutableListOf<Double>(),y=mutableListOf<Double>(),vx=mutableListOf<Double>(),vy=mutableListOf<Double>(),rot=mutableListOf<Double>(),s=mutableListOf<Double>(),lime=mutableListOf<Double>(),home=mutableListOf<Double>(),font=mutableListOf<Double>(),mode=mutableListOf<Double>(),wait=mutableListOf<Double>(),rest=mutableListOf<Double>(),drag=mutableListOf<Double>(),target=0.0,rise=0.0,riseV=0.0,body=0.0,bodyV=0.0,depth=mutableListOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),depthV=mutableListOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),kick=mutableListOf(-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0),held=-1.0,active=-1.0,effW=mutableListOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0),effT=0.0,amp=1.0,finger=0.0,fx=0.0,fy=0.0,fvx=0.0,fvy=0.0,sendS=1.0,sendV=0.0,hapticT=-1.0,returning=0.0)
}
fun homeOf(g:Geo,i:Double,target:Double):Vec2 { return (if((target == 1.0)) Vec2(x=g.bubbleX[i],y=BUBBLE_BASE) else Vec2(x=g.fieldX[i],y=FIELD_BASE)) }
fun stepLive(l:Live,dt:Double,geo:Geo,tnow:Double):Unit {
var n:Double = geo.n
var sub:Double = 4.0
var h:Double = (dt / sub)
fun springTo(x:Double,v:Double,to:Double,k:Double,d:Double):MutableList<Double> {
var a:Double = ((-k * ((x - to))) - (d * v))
var v2:Double = (v + (a * h))
return mutableListOf((x + (v2 * h)),v2)
}
run {
var s:Double = 0.0
while((s < sub)) {
try {
var r:MutableList<Double> = springTo(l.rise,l.riseV,l.target,SPRING.glide.k,SPRING.glide.d)
l.rise = r[0.0]
l.riseV = r[1.0]
var b:MutableList<Double> = springTo(l.body,l.bodyV,l.target,SPRING.pop.k,SPRING.pop.d)
l.body = max(0.0,b[0.0])
l.bodyV = b[1.0]
var sd:MutableList<Double> = springTo(l.sendS,l.sendV,1.0,SPRING.pop.k,SPRING.pop.d)
l.sendS = sd[0.0]
l.sendV = sd[1.0]
} finally { s += 1.0 }
}
}
run {
var k:Double = 0.0
while((k < 8.0)) {
try {
var to:Double = (if((l.held == k)) 1.0 else 0.0)
run {
var s:Double = 0.0
while((s < sub)) {
try {
var r:MutableList<Double> = springTo(l.depth[k],l.depthV[k],to,(if((to == 1.0)) 2400.0 else SPRING.snap.k),(if((to == 1.0)) 90.0 else SPRING.snap.d))
l.depth[k] = r[0.0]
l.depthV[k] = r[1.0]
} finally { s += 1.0 }
}
}
if((l.kick[k] >= 0.0)) {
l.kick[k] = (if(((l.kick[k] + dt) > 0.8)) -1.0 else (l.kick[k] + dt))
}
} finally { k += 1.0 }
}
}
var kb:Double = (1.0 - exp((-dt / 0.07)))
run {
var e:Double = 0.0
while((e < 8.0)) {
try {
l.effW[e] += (((((if((l.active == e)) 1.0 else 0.0)) - l.effW[e])) * kb)
} finally { e += 1.0 }
}
}
l.effT += dt
var staged:Double = 0.0
var busy:Double = 0.0
run {
var i:Double = 0.0
while((i < n)) {
try {
var m:Double = l.mode[i]
if((m == 4.0)) {
staged = 1.0
}
if(((m == 2.0) || (m == 3.0))) {
busy = 1.0
}
if((l.wait[i] > 0.0)) {
l.wait[i] -= dt
continue
}
var tx:Double = 0.0
var ty:Double = 0.0
var ts:Double = 1.0
var tl:Double = 0.0
if((m == 0.0)) {
var p:Vec2 = homeOf(geo,i,l.target)
tx = p.x
ty = p.y
var dist:Double = hypot((l.x[i] - tx),(l.y[i] - ty))
tl = (if((dist > 14.0)) 1.0 else 0.0)
if((dist < 40.0)) {
l.home[i] += (((l.target - l.home[i])) * kb)
l.font[i] = l.target
}
} else {
if((m == 1.0)) {
if((i == 0.0)) {
tx = l.fx
ty = (l.fy + 20.0)
} else {
var dx:Double = (l.x[i] - l.x[(i - 1.0)])
var dy:Double = (l.y[i] - l.y[(i - 1.0)])
var d:Double = max(hypot(dx,dy),0.001)
var gap:Double = ((0.6 * 15.0) * TYPE.hero)
tx = (l.x[(i - 1.0)] + (((dx / d)) * gap))
ty = (l.y[(i - 1.0)] + (((dy / d)) * gap))
}
ts = TYPE.hero
tl = 1.0
l.font[i] = 1.0
} else {
if((m == 4.0)) {
tx = geo.stageX[i]
ty = G.stageY
ts = TYPE.stage
tl = 1.0
l.font[i] = 1.0
l.home[i] = 1.0
}
}
}
if((((m == 0.0) || (m == 1.0)) || (m == 4.0))) {
var sp:SpringSpec = (if((m == 1.0)) SPRING.chain else (if((m == 4.0)) SPRING.glide else SPRING.pop))
run {
var s:Double = 0.0
while((s < sub)) {
try {
var rx:MutableList<Double> = springTo(l.x[i],l.vx[i],tx,sp.k,sp.d)
var ry:MutableList<Double> = springTo(l.y[i],l.vy[i],ty,sp.k,sp.d)
l.x[i] = rx[0.0]
l.vx[i] = rx[1.0]
l.y[i] = ry[0.0]
l.vy[i] = ry[1.0]
} finally { s += 1.0 }
}
}
l.s[i] += (((ts - l.s[i])) * ((1.0 - exp((-dt / 0.09)))))
l.lime[i] += (((tl - l.lime[i])) * ((1.0 - exp((-dt / 0.06)))))
var r:Double = (((((((((l.rot[i] + 180.0)) % 360.0)) + 360.0)) % 360.0)) - 180.0)
l.rot[i] = (r * exp((-dt / 0.08)))
continue
}
if((m == 3.0)) {
l.rest[i] += dt
continue
}
l.s[i] += (((TYPE.hero - l.s[i])) * ((1.0 - exp((-dt / 0.05)))))
l.lime[i] += (((1.0 - l.lime[i])) * ((1.0 - exp((-dt / 0.03)))))
l.font[i] = 1.0
l.home[i] = 1.0
run {
var s:Double = 0.0
while((s < sub)) {
try {
var y0:Double = l.y[i]
l.vy[i] += (GRAV * h)
l.vx[i] *= exp((-h / l.drag[i]))
l.x[i] += (l.vx[i] * h)
l.y[i] += (l.vy[i] * h)
l.rot[i] += ((l.vx[i] * h) * 0.25)
if(((l.x[i] < 14.0) || (l.x[i] > 388.0))) {
l.x[i] = min(max(l.x[i],14.0),388.0)
l.vx[i] = (-0.3 * l.vx[i])
}
if((l.vy[i] > 0.0)) {
run {
var k:Double = 0.0
while((k < 8.0)) {
try {
var kx:Double = G.keyX[(k % 2.0)]
var top:Double = ((G.keyY[floor((k / 2.0))] + LAND) + (4.0 * l.depth[k]))
if(((l.x[i] < (kx + 4.0)) || (l.x[i] > ((kx + G.keyW) - 4.0)))) {
continue
}
if(((y0 <= top) && (l.y[i] >= top))) {
l.y[i] = top
var strength:Double = min(1.0,(l.vy[i] / 1200.0))
if((l.vy[i] > 160.0)) {
l.vy[i] = (-REST * l.vy[i])
l.depth[k] = max(l.depth[k],strength)
l.depthV[k] = 0.0
if((strength > 0.5)) {
l.kick[k] = 0.0
}
l.hapticT = tnow
} else {
l.vy[i] = 0.0
l.mode[i] = 3.0
l.rest[i] = 0.0
}
break
}
} finally { k += 1.0 }
}
}
}
if(((l.y[i] > 832.0) && (l.mode[i] == 2.0))) {
l.y[i] = 832.0
l.vy[i] = 0.0
l.mode[i] = 3.0
}
if((l.mode[i] == 3.0)) {
break
}
} finally { s += 1.0 }
}
}
} finally { i += 1.0 }
}
}
if((((busy != 0.0) && (l.returning == 0.0)) && (l.finger != 1.0))) {
var settled:Boolean = true
run {
var i:Double = 0.0
while((i < n)) {
try {
if((l.mode[i] == 2.0)) {
settled = false
}
if(((l.mode[i] == 3.0) && (l.rest[i] < 0.3))) {
settled = false
}
} finally { i += 1.0 }
}
}
var long:Boolean = false
run {
var i:Double = 0.0
while((i < n)) {
try {
if(((l.mode[i] == 3.0) && (l.rest[i] > 1.2))) {
long = true
}
} finally { i += 1.0 }
}
}
if((settled || long)) {
l.returning = 1.0
var order:Double = 0.0
run {
var i:Double = 0.0
while((i < n)) {
try {
if(((l.mode[i] == 2.0) || (l.mode[i] == 3.0))) {
l.wait[i] = (0.035 * order)
l.mode[i] = 0.0
order += 1.0
}
} finally { i += 1.0 }
}
}
}
}
l.amp += (((((if(((staged != 0.0) || (busy != 0.0))) 0.4 else 1.0)) - l.amp)) * ((1.0 - exp((-dt / 0.12)))))
}
fun liveFrame(l:Live,g:Geo,tnow:Double,n0:Double,reduced:Boolean):Frame {
var n:Double = g.n
var hero:MutableList<Hero> = mutableListOf<Hero>()
var p:Double = ((((((l.effT % CYCLE)) + CYCLE)) % CYCLE) / CYCLE)
var dips:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
run {
var i:Double = 0.0
while((i < n)) {
try {
var x:Double = l.x[i]
var y:Double = l.y[i]
var rot:Double = l.rot[i]
var s:Double = l.s[i]
var w:Double = 0.0
var blur:Double = 0.0
if((l.mode[i] == 4.0)) {
var dx:Double = 0.0
var dy:Double = 0.0
var dr:Double = 0.0
var sc:Double = 0.0
run {
var e:Double = 0.0
while((e < 8.0)) {
try {
var k:Double = l.effW[e]
if((k < 0.002)) {
continue
}
var u:Double = (if((e == EF.ripple)) (28.0 / 6.0) else (if((e == EF.explode)) 2.0 else TYPE.stage))
var a:Pose = effectPose(e,p,i,n,u,true)
dx += (a.dx * k)
dy += (a.dy * k)
dr += (a.rot * k)
sc += ((((if((e == EF.big)) 0.5 else 1.0)) * a.sc) * k)
w += (a.w * k)
blur += (a.blur * k)
} finally { e += 1.0 }
}
}
x = ((201.0 + (((x - 201.0)) * ((1.0 + sc)))) + dx)
y += dy
rot += dr
s *= (1.0 + sc)
if((l.effW[EF.ripple] > 0.01)) {
run {
var sp:Double = 0.0
while((sp < g.spans.length)) {
try {
if(((x >= g.spans[sp][0.0]) && (x <= g.spans[sp][1.0]))) {
dips[sp] = max(dips[sp],(((3.0 * -dy)) / 28.0))
}
} finally { sp += 1.0 }
}
}
}
}
hero.add(Hero(x=x,y=y,rot=rot,s=s,lime=l.lime[i],home=l.home[i],font=l.font[i],w=w,vx=l.vx[i],vy=l.vy[i],blur=blur,mb=(if((((l.mode[i] == 1.0) || (l.mode[i] == 2.0)) || ((l.mode[i] == 0.0) && (l.lime[i] > 0.3)))) 1.0 else 0.0)))
} finally { i += 1.0 }
}
}
var away:Double = 0.0
run {
var i:Double = 0.0
while((i < n)) {
try {
away += l.lime[i]
} finally { i += 1.0 }
}
}
var kick:MutableList<Double> = mutableListOf(-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0,-1.0)
var active:MutableList<Double> = mutableListOf(0.0,0.0,0.0,0.0,0.0,0.0,0.0,0.0)
run {
var k:Double = 0.0
while((k < 8.0)) {
try {
kick[k] = (if((l.kick[k] >= 0.0)) (l.kick[k] / 0.8) else -1.0)
active[k] = l.effW[k]
} finally { k += 1.0 }
}
}
var kp:Double = ((((((((tnow - n0)) % CYCLE)) + CYCLE)) % CYCLE) / CYCLE)
return Frame(rise=l.rise,body=l.body,squash=0.0,blip=0.0,shell=(if((l.target == 1.0)) (1.0 - (((0.6 * away)) / n)) else 1.0),sendS=l.sendS,arrowY=0.0,placeholder=min(max(l.body,0.0),1.0),select=(1.0 - min(max((l.body * 3.0),0.0),1.0)),hero=hero,depth=l.depth.toMutableList(),kick=kick,active=active,amp=l.amp,keyP=(if(reduced) 0.0 else kp),dips=dips)
}
}
