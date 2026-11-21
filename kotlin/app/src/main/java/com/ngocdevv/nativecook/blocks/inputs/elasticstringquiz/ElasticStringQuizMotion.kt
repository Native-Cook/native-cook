package com.ngocdevv.nativecook.blocks.inputs.elasticstringquiz
import kotlin.math.*

internal object ElasticStringQuizMotion {
// Numeric event/timeline port of RN esq-motion.ts at 58acf81b; all distances are reference points.
object G { val hubX=201.0; val hubY=300.0; val shoulderY=343.0; val beadY=640.0; val wordY=239.0; val fieldX=201.0; val fieldY=400.0; val corner=10.0; val bx=mutableListOf(81.0,201.0,321.0) }
object SPRING { object pop { val d=19.0; val k=195.0 }; object snap { val d=30.5; val k=322.3 }; object cord { val d=9.0; val k=420.0 } }
val LREST=mutableListOf(hypot(120.0,43.0)+297.0,340.0,hypot(120.0,43.0)+297.0)
val PULSE_SPEED=1400.0
data class Vec2(var x:Double,var y:Double)
data class RopePoints(var x:MutableList<Double>,var y:MutableList<Double>)
private fun nonzero(a:Double,b:Double)=if(a!=0.0)a else b
private val <T> List<T>.length:Double get()=size.toDouble()
private operator fun <T> MutableList<T>.get(i:Double)=get(i.toInt())
private operator fun <T> MutableList<T>.set(i:Double,v:T){set(i.toInt(),v)}
private fun <T> List<T>.safe(i:Double)=getOrNull(i.toInt())
var LOOP:Double = 12.0
var NEG:Double = -1000000.0
var POS:Double = 1000000.0
var MAXL:Double = 10.0
var PULL:Double = 120.0
var ARM:Double = 44.0
object TR {
val st = mutableListOf(0.0,0.06,0.12)
val d = 14.0
val k = 180.0
val squash = 0.26
val ring = 0.5
val rel = 0.62
val nx = 0.7
val out = 1.2
val settle = 2.05
val end = 2.2
val over = 0.6
val mask = 273.0
val roll = 64.0
}
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
var s:Double = sqrt(max(((z * z) - 1.0),0.000001))
var r1:Double = (-w0 * ((z - s)))
var r2:Double = (-w0 * ((z + s)))
var c2:Double = (((v0 - (r1 * x0))) / ((r2 - r1)))
var c1:Double = (x0 - c2)
return ((to + (c1 * exp((r1 * t)))) + (c2 * exp((r2 * t))))
}
var CROSS:Double = run {
var lo:Double = 0.0
var hi:Double = 0.6
run {
var n:Double = 0.0
while((n < 40.0)) {
try {
var mid:Double = (((lo + hi)) / 2.0)
if((springAt(mid,1.0,0.0,0.0,TR.d,TR.k) > 0.0)) {
lo = mid
} else {
hi = mid
}
} finally { n += 1.0 }
}
}
return@run hi
}
var U_SQ:Double = (TR.st[2.0] + CROSS)
fun endAt(u:Double,i:Double,recap:Double):Double {
var q:Double = (u - TR.st[i])
if((u < TR.out)) {
if(((recap == 1.0) || (q >= CROSS))) {
return 0.0
}
return (if((q <= 0.0)) 1.0 else max(springAt(q,1.0,0.0,0.0,TR.d,TR.k),0.0))
}
var r:Double = ((u - TR.out) - TR.st[i])
return (if((r <= 0.0)) 0.0 else springAt(r,0.0,1.0,0.0,TR.d,TR.k))
}
fun squashAt(u:Double):Double {
var q:Double = (((u - U_SQ)) / TR.squash)
if(((q <= 0.0) || (q >= 1.0))) {
return 1.0
}
if((q < 0.4)) {
return mix(1.0,1.18,easeOut((q / 0.4)))
}
if((q < 0.75)) {
return mix(1.18,0.92,easeInOut((((q - 0.4)) / 0.35)))
}
return mix(0.92,1.0,easeInOut((((q - 0.75)) / 0.25)))
}
fun elastic(dy:Double):Double {
if((dy >= 0.0)) {
return (dy / ((1.0 + (dy / 160.0))))
}
return (-8.0 * ((1.0 - exp((dy / 30.0)))))
}
fun elasticInv(s:Double):Double {
if((s >= 0.0)) {
return (((160.0 * min(s,150.0))) / ((160.0 - min(s,150.0))))
}
return (30.0 * ln(max((1.0 + (s / 8.0)),0.01)))
}
fun elasticSlope(dy:Double):Double {
if((dy < 0.0)) {
return 0.2
}
var q:Double = (1.0 + (dy / 160.0))
return (1.0 / ((q * q)))
}
var dg:Double = hypot((G.bx[2.0] - G.hubX),(G.shoulderY - G.hubY))
var DIRX:MutableList<Double> = mutableListOf((((G.bx[0.0] - G.hubX)) / dg),0.0,(((G.bx[2.0] - G.hubX)) / dg))
var DIRY:MutableList<Double> = mutableListOf((((G.shoulderY - G.hubY)) / dg),1.0,(((G.shoulderY - G.hubY)) / dg))
var VERT:Double = (G.beadY - G.shoulderY)
data class Script(
var n:Double,
var answers:MutableList<Double>,
var lens:MutableList<Double>,
var cx:MutableList<Double>,
var cA:Double,
var cB:Double,
var cW:Double,
var score:Double,
var top:Double,
var arm:Double
)
data class Ev(
var rm:Double,
var held:MutableList<Double>,
var hs:MutableList<Double>,
var hb:MutableList<Double>,
var relT:MutableList<Double>,
var relS:MutableList<Double>,
var relV:MutableList<Double>,
var relB:MutableList<Double>,
var tapT:MutableList<Double>,
var tapS0:MutableList<Double>,
var haloOn:MutableList<Double>,
var haloOff:MutableList<Double>,
var pulseT:MutableList<Double>,
var pulseK:MutableList<Double>,
var pend:MutableList<Double>,
var snapT:MutableList<Double>,
var snapS:MutableList<Double>,
var snapB:MutableList<Double>,
var dimT:MutableList<Double>,
var pl:MutableList<Double>,
var pi:MutableList<Double>,
var pop:MutableList<Double>,
var popI:Double,
var rejT:Double,
var wA:Double,
var swT:Double,
var wB:Double,
var nxT:Double,
var shownT:Double,
var sF:Double,
var sTo:Double,
var sT0:Double,
var sDur:Double,
var bonusT:Double,
var transT:Double,
var trRecap:Double,
var trAt:Double,
var recapT:Double,
var recapOffT:Double
)
fun initEv(score:Double,rm:Double=0.0):Ev {
fun n3(v:Double):MutableList<Double> { return mutableListOf(v,v,v) }
return Ev(rm=rm,held=n3(0.0),hs=n3(0.0),hb=n3(0.0),relT=n3(NEG),relS=n3(0.0),relV=n3(0.0),relB=n3(0.0),tapT=n3(NEG),tapS0=n3(0.0),haloOn=n3(POS),haloOff=n3(NEG),pulseT=n3(NEG),pulseK=n3(0.0),pend=n3(0.0),snapT=n3(POS),snapS=n3(0.0),snapB=n3(0.0),dimT=n3(NEG),pl=mutableListOf(0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0),pi=n3(0.0),pop=mutableListOf(NEG,NEG,NEG,NEG),popI=0.0,rejT=NEG,wA=0.0,swT=NEG,wB=0.0,nxT=NEG,shownT=NEG,sF=score,sTo=score,sT0=NEG,sDur=0.0,bonusT=NEG,transT=NEG,trRecap=0.0,trAt=POS,recapT=POS,recapOffT=NEG)
}
fun cloneEv(e:Ev):Ev {
return Ev(rm=e.rm,held=e.held.toMutableList(),hs=e.hs.toMutableList(),hb=e.hb.toMutableList(),relT=e.relT.toMutableList(),relS=e.relS.toMutableList(),relV=e.relV.toMutableList(),relB=e.relB.toMutableList(),tapT=e.tapT.toMutableList(),tapS0=e.tapS0.toMutableList(),haloOn=e.haloOn.toMutableList(),haloOff=e.haloOff.toMutableList(),pulseT=e.pulseT.toMutableList(),pulseK=e.pulseK.toMutableList(),pend=e.pend.toMutableList(),snapT=e.snapT.toMutableList(),snapS=e.snapS.toMutableList(),snapB=e.snapB.toMutableList(),dimT=e.dimT.toMutableList(),pl=e.pl.toMutableList(),pi=e.pi.toMutableList(),pop=e.pop.toMutableList(),popI=e.popI,rejT=e.rejT,wA=e.wA,swT=e.swT,wB=e.wB,nxT=e.nxT,shownT=e.shownT,sF=e.sF,sTo=e.sTo,sT0=e.sT0,sDur=e.sDur,bonusT=e.bonusT,transT=e.transT,trRecap=e.trRecap,trAt=e.trAt,recapT=e.recapT,recapOffT=e.recapOffT)
}
fun sAt(e:Ev,i:Double,t:Double):Double {
if((e.held[i] == 1.0)) {
return e.hs[i]
}
if(((e.tapT[i] > (NEG / 2.0)) && (t >= e.tapT[i]))) {
return mix(e.tapS0[i],52.0,easeInOut(seg(t,e.tapT[i],(e.tapT[i] + 0.14))))
}
var q:Double = (t - e.relT[i])
if(((q < 0.0) || (q > 3.0))) {
return 0.0
}
var raw:Double = springAt(q,e.relS[i],0.0,e.relV[i],SPRING.cord.d,SPRING.cord.k)
return (if((raw < 0.0)) (-18.0 * tanh((-raw / 18.0))) else raw)
}
fun bendAt(e:Ev,i:Double,t:Double):Double {
if((e.held[i] == 1.0)) {
return e.hb[i]
}
var q:Double = (t - e.relT[i])
if(((q < 0.0) || (q > 3.0))) {
return 0.0
}
return springAt(q,e.relB[i],0.0,0.0,SPRING.cord.d,SPRING.cord.k)
}
fun haloAt(e:Ev,i:Double,t:Double):Double {
var on:Double = (if((t >= e.haloOn[i])) easeOut(seg(t,e.haloOn[i],(e.haloOn[i] + ((if((e.rm == 1.0)) 0.2 else 0.1))))) else 0.0)
var off:Double = (if(((e.haloOff[i] > e.haloOn[i]) && (t >= e.haloOff[i]))) (1.0 - easeOut(seg(t,e.haloOff[i],(e.haloOff[i] + 0.3)))) else 1.0)
return (on * off)
}
var haloLevel:(Ev,Double,Double)->Double = ::haloAt
fun addPluck(e:Ev,i:Double,a1:Double,a2:Double,a3:Double,t0:Double,tau:Double):Unit {
var slot:Double = e.pi[i]
var b:Double = ((((i * 3.0) + slot)) * 5.0)
e.pl[b] = a1
e.pl[(b + 1.0)] = a2
e.pl[(b + 2.0)] = a3
e.pl[(b + 3.0)] = t0
e.pl[(b + 4.0)] = tau
e.pi[i] = (((slot + 1.0)) % 3.0)
}
fun releaseCord(e:Ev,i:Double,t:Double,s:Double,v:Double,b:Double):Unit {
e.held[i] = 0.0
e.tapT[i] = NEG
e.relT[i] = t
e.relS[i] = s
e.relV[i] = v
e.relB[i] = b
}
fun firePulse(e:Ev,i:Double,t:Double,kind:Double):Unit {
e.pulseT[i] = t
e.pulseK[i] = kind
e.pend[i] = (if((kind == 1.0)) 1.0 else 0.0)
}
fun pushPop(e:Ev,t0:Double,t1:Double):Unit {
var s:Double = e.popI
e.pop[(s * 2.0)] = t0
e.pop[((s * 2.0) + 1.0)] = t1
e.popI = (1.0 - s)
}
fun wordAt(e:Ev,t:Double):Double {
return (if((t >= e.nxT)) e.wB else e.wA)
}
fun arrive(e:Ev,i:Double,ta:Double,correct:Boolean,sc:Script,mode:Double):Unit {
var cur:Double = wordAt(e,ta)
e.pend[i] = 0.0
var rm:Boolean = (e.rm == 1.0)
if(correct) {
var last:Boolean = ((mode == 1.0) || (cur >= (sc.n - 1.0)))
var quick:Boolean = ((ta - e.shownT) < 4.5)
e.wA = cur
e.swT = (ta + ((if(rm) 0.0 else 0.07)))
e.sF = e.sTo
e.sTo = ((e.sF + 250.0) + ((if(quick) 15.0 else 0.0)))
e.sT0 = (ta + 0.07)
e.sDur = (if(rm) 0.0 else 0.6)
if(quick) {
e.bonusT = (ta + 0.17)
}
e.haloOff[i] = (ta + ((if(rm) 0.6 else 0.1)))
var t1:Double = (ta + 0.82)
if((mode == 1.0)) {
e.trAt = (ta + 0.53)
t1 = e.trAt
e.nxT = POS
} else {
if(last) {
e.recapT = (ta + 0.9)
e.nxT = POS
} else {
e.wB = (cur + 1.0)
e.nxT = (ta + ((if(rm) 0.2 else 0.82)))
e.shownT = e.nxT
}
}
if(!rm) {
pushPop(e,ta,t1)
}
} else {
if(rm) {
e.dimT[i] = ta
e.haloOff[i] = (ta + 0.3)
} else {
e.rejT = ta
var st:Double = (ta + 0.05)
e.snapT[i] = st
e.snapS[i] = sAt(e,i,st)
e.snapB[i] = bendAt(e,i,st)
e.haloOff[i] = ta
run {
var j:Double = 0.0
while((j < 3.0)) {
try {
if((j != i)) {
addPluck(e,j,3.0,0.0,-0.33,st,0.25)
}
} finally { j += 1.0 }
}
}
}
}
}
fun startTransition(e:Ev,T0:Double,sc:Script,fromRecap:Double):Unit {
e.transT = T0
e.trRecap = fromRecap
e.trAt = POS
if((e.rm != 1.0)) {
var li:Double = (e.popI.toInt().xor(1.0.toInt()).toDouble())
if(((e.pop[(li * 2.0)] > (NEG / 2.0)) && (abs((e.pop[((li * 2.0) + 1.0)] - T0)) < 1e-9))) {
e.pop[((li * 2.0) + 1.0)] = (T0 + TR.rel)
} else {
pushPop(e,T0,(T0 + TR.rel))
}
}
var cur:Double = wordAt(e,T0)
e.wA = cur
if(!(((e.swT >= e.shownT) && (e.swT <= (T0 + 0.01))))) {
e.swT = T0
}
e.wB = 0.0
e.nxT = (T0 + TR.nx)
e.shownT = e.nxT
e.sF = e.sTo
e.sTo = sc.score
e.sT0 = (T0 + 0.3)
e.sDur = 0.0
e.bonusT = NEG
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
e.held[i] = 0.0
e.tapT[i] = NEG
if(((e.haloOn[i] < T0) && (e.haloOff[i] < e.haloOn[i]))) {
e.haloOff[i] = T0
}
} finally { i += 1.0 }
}
}
}
fun pull(e:Ev,i:Double,t:Double,a:Double,b:Double,rel:Double,to:Double):Unit {
if(((t >= a) && (t < rel))) {
e.held[i] = 1.0
e.hs[i] = (to * easeInOut(seg(t,a,b)))
}
if((t >= rel)) {
releaseCord(e,i,rel,to,0.0,0.0)
firePulse(e,i,rel,1.0)
}
}
fun autoEventsAt(t:Double,sc:Script):Ev {
var e:Ev = initEv(sc.score)
e.shownT = -1.1
var A:Double = sc.cA
var B:Double = sc.cB
var Wc:Double = sc.cW
if((t >= 0.5)) {
e.haloOn[A] = 0.5
}
pull(e,A,t,0.6,1.5,1.7,PULL)
var a1:Double = (1.7 + (LREST[A] / PULSE_SPEED))
if((t >= a1)) {
arrive(e,A,a1,true,sc,0.0)
}
if((t >= 3.8)) {
e.haloOn[Wc] = 3.8
e.haloOff[Wc] = NEG
}
pull(e,Wc,t,3.9,4.8,4.8,PULL)
var a2:Double = (4.8 + (LREST[Wc] / PULSE_SPEED))
if((t >= a2)) {
arrive(e,Wc,a2,false,sc,0.0)
}
if((t >= 6.8)) {
addPluck(e,0.0,7.0,2.5,1.0,6.8,0.9)
}
if((t >= 6.95)) {
addPluck(e,1.0,7.0,2.5,1.0,6.95,0.9)
}
if((t >= 7.1)) {
addPluck(e,2.0,7.0,2.5,1.0,7.1,0.9)
}
if((t >= 7.9)) {
e.haloOn[B] = 7.9
e.haloOff[B] = NEG
}
pull(e,B,t,8.0,8.7,8.7,PULL)
var a3:Double = (8.7 + (LREST[B] / PULSE_SPEED))
if((t >= a3)) {
arrive(e,B,a3,true,sc,1.0)
}
if((t >= e.trAt)) {
startTransition(e,e.trAt,sc,0.0)
}
return e
}
data class Frame(
var t:Double,
var e:Ev,
var s:MutableList<Double>,
var bend:MutableList<Double>,
var hx:Double,
var hy:Double,
var bx:MutableList<Double>,
var by:MutableList<Double>,
var beadSc:MutableList<Double>,
var beadA:MutableList<Double>,
var tip:Double,
var halo:MutableList<Double>,
var labelA:MutableList<Double>,
var labelSc:MutableList<Double>,
var labelDy:MutableList<Double>,
var labelBlur:MutableList<Double>,
var heat:MutableList<Double>,
var heatHot:MutableList<Double>,
var width:MutableList<Double>,
var pulseFrac:MutableList<Double>,
var pulseAmp:MutableList<Double>,
var pulseK:MutableList<Double>,
var draw:MutableList<Double>,
var cordA:MutableList<Double>,
var bodyW:MutableList<Double>,
var ropeTau:MutableList<Double>,
var mode:Double,
var u:Double,
var ballR:Double,
var arcA:Double,
var arcSweep:Double,
var arcRot:Double,
var hubA:Double,
var shockR:Double,
var shockA:Double,
var fx:Double,
var fy:Double,
var gain:Double,
var cool:Double,
var w:Double,
var lx:MutableList<Double>,
var ly:MutableList<Double>,
var ls:MutableList<Double>,
var la:MutableList<Double>,
var sF:Double,
var sTo:Double,
var sT0:Double,
var sDur:Double,
var bonusA:Double,
var hudA:Double,
var scoreA:Double,
var roll:Double,
var recapA:Double,
var waveFrom:Double
)
var BIG_POP:Double = 25.5
fun compose(e:Ev,t:Double,sc:Script):Frame {
var rm:Boolean = (e.rm == 1.0)
var s:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var bend:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
s[i] = sAt(e,i,t)
bend[i] = bendAt(e,i,t)
} finally { i += 1.0 }
}
}
var hx:Double = G.hubX
var hy:Double = G.hubY
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
var p:Double = (max(s[i],0.0) * 0.06)
hx += (p * DIRX[i])
hy += (p * DIRY[i])
} finally { i += 1.0 }
}
}
if(((t >= e.rejT) && (t < (e.rejT + 0.24)))) {
var k:Double = (((t - e.rejT)) / 0.24)
hx += (((4.0 * sin((((2.0 * PI) * 25.0) * ((t - e.rejT))))) * ((1.0 - k))) * ((1.0 - k)))
}
var bx:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var by:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var beadSc:MutableList<Double> = mutableListOf(1.0,1.0,1.0)
var beadA:MutableList<Double> = mutableListOf(1.0,1.0,1.0)
var halo:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var labelA:MutableList<Double> = mutableListOf(1.0,1.0,1.0)
var labelSc:MutableList<Double> = mutableListOf(1.0,1.0,1.0)
var labelDy:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var labelBlur:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var heat:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var heatHot:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var width:MutableList<Double> = mutableListOf(2.0,2.0,2.0)
var pulseFrac:MutableList<Double> = mutableListOf(-1.0,-1.0,-1.0)
var pulseAmp:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var pulseK:MutableList<Double> = mutableListOf(0.0,0.0,0.0)
var draw:MutableList<Double> = mutableListOf(1.0,1.0,1.0)
var cordA:MutableList<Double> = mutableListOf(1.0,1.0,1.0)
var bodyW:MutableList<Double> = mutableListOf(8.0,8.0,8.0)
var ropeTau:MutableList<Double> = mutableListOf(-1.0,-1.0,-1.0)
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
var slack:Double = 0.0
run {
var j:Double = 0.0
while((j < 3.0)) {
try {
if((j != i)) {
slack += (0.02 * max(s[j],0.0))
}
} finally { j += 1.0 }
}
}
bx[i] = (G.bx[i] + bend[i])
by[i] = ((G.beadY + s[i]) - slack)
halo[i] = haloAt(e,i,t)
if(((rm && (t >= e.dimT[i])) && (t < (e.dimT[i] + 0.7)))) {
beadA[i] = (1.0 - (0.6 * sin((PI * seg(t,e.dimT[i],(e.dimT[i] + 0.7))))))
}
var q:Double = (t - e.relT[i])
if(((e.held[i] == 1.0) || (((e.tapT[i] > (NEG / 2.0)) && (t >= e.tapT[i]))))) {
labelDy[i] = (0.85 * s[i])
} else {
if(((q >= 0.0) && (q < 3.0))) {
labelDy[i] = (0.85 * springAt(q,e.relS[i],0.0,e.relV[i],SPRING.snap.d,SPRING.snap.k))
}
}
var sp:Double = max(s[i],0.0)
heat[i] = clamp01((sp / PULL))
if(rm) {
heat[i] = max(heat[i],(0.55 * halo[i]))
}
run {
var slot:Double = 0.0
while((slot < 3.0)) {
try {
var b:Double = ((((i * 3.0) + slot)) * 5.0)
var q:Double = (t - e.pl[(b + 3.0)])
if(((q >= 0.0) && (q < 4.0))) {
heat[i] = max(heat[i],((0.7 * clamp01((abs(e.pl[b]) / 7.0))) * exp((-q / e.pl[(b + 4.0)]))))
}
} finally { slot += 1.0 }
}
}
heatHot[i] = heat[i]
width[i] = 2.0
if(((e.pulseK[i] > 0.0) && (t >= e.pulseT[i]))) {
var dist:Double = (((t - e.pulseT[i])) * PULSE_SPEED)
var hr:Double = clamp01((e.relS[i] / PULL))
if(((e.pulseK[i] == 1.0) && (dist < LREST[i]))) {
pulseFrac[i] = (dist / LREST[i])
pulseAmp[i] = seg(dist,0.0,18.0)
pulseK[i] = 1.0
heatHot[i] = max(heat[i],(hr * ((1.0 - (0.25 * pulseFrac[i])))))
} else {
if((e.pulseK[i] == 2.0)) {
var lv:Double = (if((i == 1.0)) (0.55 * LREST[1.0]) else VERT)
if((dist < lv)) {
pulseFrac[i] = (dist / LREST[i])
pulseAmp[i] = ((0.6 * ((1.0 - (dist / lv)))) * seg(dist,0.0,18.0))
pulseK[i] = 2.0
heatHot[i] = max(heat[i],((hr * 0.5) * ((1.0 - (dist / lv)))))
}
}
}
}
if((t >= e.snapT[i])) {
var qs:Double = (t - e.snapT[i])
pulseFrac[i] = -1.0
if((qs < 0.725)) {
ropeTau[i] = qs
labelA[i] = (1.0 - seg(qs,0.0,0.42))
halo[i] = 0.0
} else {
draw[i] = easeOut(seg(qs,0.72,1.07))
beadSc[i] = (if((qs < 1.07)) 0.0 else max(0.0,springAt((qs - 1.07),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)))
labelA[i] = 1.0
labelSc[i] = (if((qs < 1.13)) 0.0 else max(0.0,springAt((qs - 1.13),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)))
labelDy[i] = 0.0
}
}
} finally { i += 1.0 }
}
}
var fx:Double = G.fieldX
var fy:Double = G.fieldY
var gain:Double = 1.0
var best:Double = -1.0
var bs:Double = 0.0
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
if((s[i] > bs)) {
bs = s[i]
best = i
}
} finally { i += 1.0 }
}
}
if((best >= 0.0)) {
var k:Double = min((bs / PULL),1.0)
fx += ((0.45 * k) * ((bx[best] - G.fieldX)))
fy += ((0.45 * k) * ((by[best] - G.fieldY)))
gain += (0.35 * k)
}
var ballR:Double = 2.5
var arcA:Double = 0.0
var arcSweep:Double = 0.0
var arcRot:Double = 0.0
var latest:Double = NEG
var shockR:Double = 0.0
var shockA:Double = 0.0
run {
var p:Double = 0.0
while((p < 2.0)) {
try {
var t0:Double = e.pop[(p * 2.0)]
var t1:Double = e.pop[((p * 2.0) + 1.0)]
if(((t0 < (NEG / 2.0)) || (t < t0))) {
continue
}
var g:Double = springAt((t - t0),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)
if((t >= t1)) {
g *= springAt((t - t1),1.0,0.0,0.0,SPRING.snap.d,SPRING.snap.k)
}
ballR += (BIG_POP * max(g,0.0))
gain += (0.2 * ((1.0 - easeOut(seg(t,t0,(t0 + 0.4))))))
if((t0 > latest)) {
latest = t0
arcSweep = easeOut(seg(t,t0,(t0 + 0.2)))
arcA = (arcSweep * ((1.0 - seg(t,t1,(t1 + 0.2)))))
arcRot = (((t - t0)) * PI)
}
} finally { p += 1.0 }
}
}
var arrival:Double = max(latest,e.rejT)
if(((!rm && (t >= arrival)) && (t < (arrival + 0.32)))) {
var q:Double = seg(t,arrival,(arrival + 0.32))
shockR = (40.0 * easeOut(q))
shockA = ((0.85 * ((1.0 - q))) * ((1.0 - q)))
}
if(!rm) {
var life:Double = (seg(t,0.0,0.25) * ((1.0 - seg(t,11.55,11.95))))
gain += ((0.025 * sin((((2.0 * PI) * t) / 3.0))) * life)
fy += ((3.0 * sin((((2.0 * PI) * t) / 4.0))) * life)
}
var cool:Double = 0.0
if((t >= e.rejT)) {
cool = (seg(t,e.rejT,(e.rejT + 0.12)) * ((1.0 - easeInOut(seg(t,(e.rejT + 0.77),(e.rejT + 1.27))))))
}
gain -= (0.15 * cool)
var showB:Boolean = (t >= e.nxT)
var w:Double = (if(showB) e.wB else e.wA)
var n:Double = (sc.lens.safe(w) ?: 0.0)
var stg:Double = (if((n >= 6.0)) 0.06 else 0.07)
var rollIn:Boolean = (((!rm && showB) && (e.transT > (NEG / 2.0))) && (e.nxT == (e.transT + TR.nx)))
var lx:MutableList<Double> = mutableListOf<Double>()
var ly:MutableList<Double> = mutableListOf<Double>()
var ls:MutableList<Double> = mutableListOf<Double>()
var la:MutableList<Double> = mutableListOf<Double>()
run {
var k:Double = 0.0
while((k < MAXL)) {
try {
var x:Double = 0.0
var y:Double = 0.0
var sc1:Double = 1.0
var a:Double = (if((k < n)) 1.0 else 0.0)
var cx:Double = (sc.cx.safe(((w * MAXL) + k)) ?: G.hubX)
if((((k < n) && !showB) && (t >= e.swT))) {
if(rm) {
a = (1.0 - seg(t,e.swT,(e.swT + 0.2)))
} else {
var land:Double = (e.swT + (stg * k))
var p:Double = easeIn(seg(t,land,(land + 0.26)))
y = (38.0 * p)
x = ((((G.hubX - cx)) * 0.55) * p)
sc1 = (1.0 - (0.7 * p))
a = (1.0 - seg(p,0.45,1.0))
if(((t >= (land + 0.26)) && (t < (land + 1.6)))) {
ballR += max(0.0,springAt(((t - land) - 0.26),0.0,0.0,52.0,SPRING.pop.d,SPRING.pop.k))
}
}
} else {
if((((k < n) && showB) && (t < (e.nxT + 2.0)))) {
if(rm) {
a = seg(t,e.nxT,(e.nxT + 0.2))
} else {
if(rollIn) {
var tk:Double = ((t - e.nxT) - (0.05 * k))
var pr:Double = (if((tk <= 0.0)) 0.0 else (if((tk > 0.9)) 1.0 else mix(springAt(tk,0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k),1.0,easeOut(seg(tk,0.6,0.9)))))
y = (TR.roll * ((1.0 - pr)))
} else {
var tk:Double = ((t - e.nxT) - (0.05 * k))
var pr:Double = (if((tk <= 0.0)) 0.0 else (if((tk > 0.72)) 1.0 else mix(springAt(tk,0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k),1.0,easeOut(seg(tk,0.5,0.72)))))
y = (((G.hubY - G.wordY)) * ((1.0 - pr)))
x = (((G.hubX - cx)) * ((1.0 - pr)))
sc1 = pr
a = (if((tk > 0.0)) 1.0 else 0.0)
}
}
}
}
lx.add(x)
ly.add(y)
ls.add(sc1)
la.add(a)
} finally { k += 1.0 }
}
}
var bonusA:Double = (if((t >= e.bonusT)) ((0.7 * seg(t,e.bonusT,(e.bonusT + 0.2))) * ((1.0 - seg(t,(e.bonusT + 1.0),(e.bonusT + 1.2))))) else 0.0)
var hudA:Double = 1.0
var recapA:Double = (if((t >= e.recapT)) easeOut(seg(t,e.recapT,(e.recapT + 0.3))) else 0.0)
if(((e.recapOffT > e.recapT) && (t >= e.recapOffT))) {
recapA *= (1.0 - seg(t,e.recapOffT,(e.recapOffT + 0.25)))
}
var treeA:Double = (1.0 - recapA)
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
cordA[i] *= treeA
beadA[i] *= treeA
labelA[i] *= treeA
} finally { i += 1.0 }
}
}
var mode:Double = 0.0
var tip:Double = 0.0
var waveFrom:Double = NEG
var u:Double = (t - e.transT)
if((((u >= 0.0) && (u < TR.end)) && !rm)) {
mode = 6.0
tip = 1.0
waveFrom = ((e.transT + TR.out) - 0.1)
var rec:Double = e.trRecap
var fin:Double = easeOut(seg(u,TR.settle,(TR.end - 0.05)))
run {
var i:Double = 0.0
while((i < 3.0)) {
try {
var raw:Double = mix(endAt(u,i,rec),1.0,fin)
var v:Double = clamp01(((abs((mix(endAt((u + 0.01),i,rec),1.0,fin) - mix(endAt((u - 0.01),i,rec),1.0,fin))) / 0.02) / 7.0))
var over:Double = (max((raw - 1.0),0.0) * TR.over)
var p:Double = min(raw,1.0)
draw[i] = p
by[i] += (over * LREST[i])
beadSc[i] = (if((p <= 0.0005)) 0.0 else mix(0.25,1.0,easeOut(seg(p,0.04,0.35))))
labelSc[i] = easeOut(seg(p,0.0,0.6))
labelA[i] = treeA
labelDy[i] = 0.0
labelBlur[i] = 0.0
bodyW[i] = mix(8.0,3.5,easeOut(v))
heat[i] = max((heat[i] * ((1.0 - seg(u,0.0,0.2)))),(0.55 * v))
heatHot[i] = heat[i]
pulseFrac[i] = -1.0
halo[i] *= (1.0 - seg(u,0.0,0.15))
} finally { i += 1.0 }
}
}
ballR *= squashAt(u)
var qr:Double = seg(u,U_SQ,(U_SQ + TR.ring))
if(((u >= U_SQ) && (qr < 1.0))) {
shockR = mix(20.0,60.0,easeOut(qr))
shockA = (0.35 * ((1.0 - qr)))
gain += (0.14 * sin((PI * seg(u,U_SQ,(U_SQ + 0.5)))))
} else {
shockR = 0.0
shockA = 0.0
}
} else {
if(((e.transT > (NEG / 2.0)) && (u >= TR.end))) {
waveFrom = ((e.transT + TR.out) - 0.1)
}
}
var roll:Double = (if(((((!rm && showB) && (e.transT > (NEG / 2.0))) && (e.nxT == (e.transT + TR.nx))) && (t < (e.nxT + 1.3)))) 1.0 else 0.0)
return Frame(t=t,e=e,s=s,bend=bend,hx=hx,hy=hy,bx=bx,by=by,beadSc=beadSc,beadA=beadA,tip=tip,halo=halo,labelA=labelA,labelSc=labelSc,labelDy=labelDy,labelBlur=labelBlur,heat=heat,heatHot=heatHot,width=width,pulseFrac=pulseFrac,pulseAmp=pulseAmp,pulseK=pulseK,draw=draw,cordA=cordA,bodyW=bodyW,ropeTau=ropeTau,mode=mode,u=u,ballR=ballR,arcA=arcA,arcSweep=arcSweep,arcRot=arcRot,hubA=treeA,shockR=shockR,shockA=shockA,fx=fx,fy=fy,gain=gain,cool=cool,w=w,lx=lx,ly=ly,ls=ls,la=la,sF=e.sF,sTo=e.sTo,sT0=e.sT0,sDur=e.sDur,bonusA=(bonusA * hudA),hudA=hudA,scoreA=(hudA * ((1.0 - recapA))),roll=roll,recapA=recapA,waveFrom=waveFrom)
}
fun frameAt(tIn:Double,sc:Script):Frame {
var t:Double = (((((tIn % LOOP)) + LOOP)) % LOOP)
return compose(autoEventsAt(t,sc),t,sc)
}
data class Pts(
var x:MutableList<Double>,
var y:MutableList<Double>,
var s:MutableList<Double>,
var total:Double
)
fun waveAt(pl:MutableList<Double>,i:Double,u:Double,t:Double,from:Double):Double {
var x:Double = 0.0
if((pl.length < 45.0)) {
return 0.0
}
run {
var slot:Double = 0.0
while((slot < 3.0)) {
try {
var b:Double = ((((i * 3.0) + slot)) * 5.0)
var t0:Double = pl[(b + 3.0)]
if(((t0 < from) || (t < t0))) {
continue
}
var q:Double = (t - t0)
if((q > 6.0)) {
continue
}
var env:Double = exp((-q / pl[(b + 4.0)]))
var w:Double = (((2.0 * PI) * 7.0) * q)
x += (env * (((((pl[b] * sin((PI * u))) * sin(w)) + ((pl[(b + 1.0)] * sin(((2.0 * PI) * u))) * sin((2.0 * w)))) + ((pl[(b + 2.0)] * sin(((3.0 * PI) * u))) * sin((3.0 * w))))))
} finally { slot += 1.0 }
}
}
return x
}
fun cordPoints(i:Double,hx:Double,hy:Double,beadY:Double,bend:Double,pl:MutableList<Double>,t:Double,from:Double):Pts {
var xs:MutableList<Double> = mutableListOf<Double>()
var ys:MutableList<Double> = mutableListOf<Double>()
var vx:Double = G.bx[i]
var vTop:Double = hy
if((i != 1.0)) {
var dx:Double = (vx - hx)
var dy:Double = (G.shoulderY - hy)
var len:Double = hypot(dx,dy)
var R:Double = G.corner
var cx0:Double = (vx - (((dx / len)) * R))
var cy0:Double = (G.shoulderY - (((dy / len)) * R))
var ND:Double = 9.0
run {
var k:Double = 0.0
while((k < ND)) {
try {
var q:Double = (k / ND)
xs.add((hx + (((cx0 - hx)) * q)))
ys.add((hy + (((cy0 - hy)) * q)))
} finally { k += 1.0 }
}
}
var NC:Double = 6.0
run {
var k:Double = 0.0
while((k <= NC)) {
try {
var q:Double = (k / NC)
var a:Double = (((1.0 - q)) * ((1.0 - q)))
var b:Double = ((2.0 * q) * ((1.0 - q)))
var c:Double = (q * q)
xs.add((((a * cx0) + (b * vx)) + (c * vx)))
ys.add((((a * cy0) + (b * G.shoulderY)) + (c * ((G.shoulderY + R)))))
} finally { k += 1.0 }
}
}
vTop = (G.shoulderY + R)
} else {
xs.add(hx)
ys.add(hy)
}
var NV:Double = (if((i == 1.0)) 68.0 else 60.0)
var vlen:Double = (beadY - vTop)
run {
var k:Double = 1.0
while((k <= NV)) {
try {
var u:Double = (k / NV)
var x:Double = (if((i == 1.0)) (hx + (((vx - hx)) * u)) else vx)
x += ((((bend * u) * u) * u) + waveAt(pl,i,u,t,from))
xs.add(x)
ys.add((vTop + (vlen * u)))
} finally { k += 1.0 }
}
}
var ss:MutableList<Double> = mutableListOf(0.0)
run {
var k:Double = 1.0
while((k < xs.length)) {
try {
ss.add((ss[(k - 1.0)] + hypot((xs[k] - xs[(k - 1.0)]),(ys[k] - ys[(k - 1.0)]))))
} finally { k += 1.0 }
}
}
return Pts(x=xs,y=ys,s=ss,total=ss[(ss.length - 1.0)])
}
fun addBump(p:Pts,pos:Double,amp:Double):Unit {
if((amp <= 0.0)) {
return
}
var n:Double = p.x.length
var nx:MutableList<Double> = mutableListOf<Double>()
var ny:MutableList<Double> = mutableListOf<Double>()
run {
var k:Double = 0.0
while((k < n)) {
try {
var a:Double = max((k - 1.0),0.0)
var b:Double = min((k + 1.0),(n - 1.0))
var tx:Double = (p.x[b] - p.x[a])
var ty:Double = (p.y[b] - p.y[a])
var l:Double = nonzero(hypot(tx,ty),1.0)
nx.add((-ty / l))
ny.add((tx / l))
} finally { k += 1.0 }
}
}
run {
var k:Double = 0.0
while((k < n)) {
try {
var d:Double = (((p.s[k] - pos)) / 12.0)
if(((d > 4.0) || (d < -4.0))) {
continue
}
var g:Double = ((exp((-d * d)) * 12.0) * amp)
p.x[k] += (nx[k] * g)
p.y[k] += (ny[k] * g)
} finally { k += 1.0 }
}
}
}
fun pointAt(p:Pts,a:Double):Vec2 {
var n:Double = p.x.length
if((a <= 0.0)) {
return Vec2(x=p.x[0.0],y=p.y[0.0])
}
run {
var k:Double = 1.0
while((k < n)) {
try {
if((p.s[k] >= a)) {
var q:Double = (((a - p.s[(k - 1.0)])) / max((p.s[k] - p.s[(k - 1.0)]),0.000001))
return Vec2(x=mix(p.x[(k - 1.0)],p.x[k],q),y=mix(p.y[(k - 1.0)],p.y[k],q))
}
} finally { k += 1.0 }
}
}
return Vec2(x=p.x[(n - 1.0)],y=p.y[(n - 1.0)])
}
var ROPE_N:Double = 12.0
var ROPE_STEPS:Double = 87.0
var GRAV:Double = 2400.0
fun bakeRope(i:Double):MutableList<Double> {
var p:Pts = cordPoints(i,G.hubX,G.hubY,G.beadY,0.0,mutableListOf<Double>(),0.0,POS)
var px:MutableList<Double> = mutableListOf<Double>()
var py:MutableList<Double> = mutableListOf<Double>()
run {
var k:Double = 0.0
while((k < ROPE_N)) {
try {
var q:Vec2 = pointAt(p,(((p.total * k)) / ((ROPE_N - 1.0))))
px.add(q.x)
py.add(q.y)
} finally { k += 1.0 }
}
}
var rest:Double = (p.total / ((ROPE_N - 1.0)))
var dt:Double = (1.0 / 120.0)
var ox:MutableList<Double> = mutableListOf<Double>()
var oy:MutableList<Double> = mutableListOf<Double>()
run {
var k:Double = 0.0
while((k < ROPE_N)) {
try {
var a:Double = max((k - 1.0),0.0)
var b:Double = min((k + 1.0),(ROPE_N - 1.0))
var tx:Double = (px[b] - px[a])
var ty:Double = (py[b] - py[a])
var l:Double = nonzero(hypot(tx,ty),1.0)
var v:Double = (760.0 * ((1.0 - (k / ((ROPE_N - 1.0))))))
ox.add((px[k] - ((((tx / l)) * v) * dt)))
oy.add((py[k] - ((((ty / l)) * v) * dt)))
} finally { k += 1.0 }
}
}
var out:MutableList<Double> = mutableListOf<Double>()
fun save():Unit {
run {
var k:Double = 0.0
while((k < ROPE_N)) {
try {
out.addAll(mutableListOf(px[k],py[k]))
} finally { k += 1.0 }
}
}
}
save()
var bx0:Double = px[(ROPE_N - 1.0)]
var by0:Double = py[(ROPE_N - 1.0)]
run {
var step:Double = 1.0
while((step <= ROPE_STEPS)) {
try {
var tt:Double = (step * dt)
run {
var k:Double = 0.0
while((k < (ROPE_N - 1.0))) {
try {
var vx:Double = (((px[k] - ox[k])) * 0.996)
var vy:Double = (((py[k] - oy[k])) * 0.996)
ox[k] = px[k]
oy[k] = py[k]
px[k] += vx
py[k] += (vy + ((GRAV * dt) * dt))
} finally { k += 1.0 }
}
}
px[(ROPE_N - 1.0)] = bx0
py[(ROPE_N - 1.0)] = (by0 + (((0.5 * GRAV) * tt) * tt))
ox[(ROPE_N - 1.0)] = px[(ROPE_N - 1.0)]
oy[(ROPE_N - 1.0)] = py[(ROPE_N - 1.0)]
run {
var it:Double = 0.0
while((it < 4.0)) {
try {
run {
var k:Double = (ROPE_N - 2.0)
while((k >= 0.0)) {
try {
var dx:Double = (px[(k + 1.0)] - px[k])
var dy:Double = (py[(k + 1.0)] - py[k])
var d:Double = nonzero(hypot(dx,dy),1.0)
var diff:Double = (((d - rest)) / d)
if(((k + 1.0) == (ROPE_N - 1.0))) {
px[k] += (dx * diff)
py[k] += (dy * diff)
} else {
px[k] += ((dx * diff) * 0.5)
py[k] += ((dy * diff) * 0.5)
px[(k + 1.0)] -= ((dx * diff) * 0.5)
py[(k + 1.0)] -= ((dy * diff) * 0.5)
}
} finally { k -= 1.0 }
}
}
} finally { it += 1.0 }
}
}
save()
} finally { step += 1.0 }
}
}
return out
}
var ROPE:MutableList<MutableList<Double>> = mutableListOf(bakeRope(0.0),bakeRope(1.0),bakeRope(2.0))
fun ropeAt(rope:MutableList<Double>,q:Double,s0:Double,b0:Double):RopePoints {
var fi:Double = min(max((q * 120.0),0.0),ROPE_STEPS)
var f0:Double = floor(fi)
var f1:Double = min((f0 + 1.0),ROPE_STEPS)
var k:Double = (fi - f0)
var w:Double = (1.0 - easeOut(seg(q,0.0,0.15)))
var xs:MutableList<Double> = mutableListOf<Double>()
var ys:MutableList<Double> = mutableListOf<Double>()
run {
var n:Double = 0.0
while((n < ROPE_N)) {
try {
var a:Double = (((f0 * ROPE_N) * 2.0) + (n * 2.0))
var b:Double = (((f1 * ROPE_N) * 2.0) + (n * 2.0))
var u:Double = (n / ((ROPE_N - 1.0)))
xs.add((mix(rope[a],rope[b],k) + ((((b0 * u) * u) * u) * w)))
ys.add((mix(rope[(a + 1.0)],rope[(b + 1.0)],k) + ((s0 * u) * w)))
} finally { n += 1.0 }
}
}
return RopePoints(x=xs,y=ys)
}
}
