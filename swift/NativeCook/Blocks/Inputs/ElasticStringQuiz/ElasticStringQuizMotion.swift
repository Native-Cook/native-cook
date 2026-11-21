import Foundation

enum ElasticStringQuizMotion {
// Numeric event/timeline port of RN esq-motion.ts at 58acf81b; all distances are reference points.
enum G { static let hubX=201.0; static let hubY=300.0; static let shoulderY=343.0; static let beadY=640.0; static let wordY=239.0; static let fieldX=201.0; static let fieldY=400.0; static let corner=10.0; static let bx=[81.0,201.0,321.0] }
enum SPRING { enum pop { static let d=19.0; static let k=195.0 }; enum snap { static let d=30.5; static let k=322.3 }; enum cord { static let d=9.0; static let k=420.0 } }
static let LREST=[hypot(120.0,43.0)+297.0,340.0,hypot(120.0,43.0)+297.0]
static let PULSE_SPEED=1400.0
struct Vec2 {var x:Double;var y:Double}
struct RopePoints {var x:[Double];var y:[Double]}
private static func nonzero(_ a:Double,_ b:Double)->Double {a != 0 ? a : b}
static let LOOP:Double = 12.0
static let NEG:Double = -1000000.0
static let POS:Double = 1000000.0
static let MAXL:Double = 10.0
static let PULL:Double = 120.0
static let ARM:Double = 44.0
enum TR {
static let st = [0.0,0.06,0.12]
static let d = 14.0
static let k = 180.0
static let squash = 0.26
static let ring = 0.5
static let rel = 0.62
static let nx = 0.7
static let out = 1.2
static let settle = 2.05
static let end = 2.2
static let over = 0.6
static let mask = 273.0
static let roll = 64.0
}
static func seg(_ t:Double,_ a:Double,_ b:Double) -> Double {
return min(max((((t - a)) / ((b - a))),0.0),1.0)
}
static func clamp01(_ v:Double) -> Double {
return min(max(v,0.0),1.0)
}
static func mix(_ a:Double,_ b:Double,_ k:Double) -> Double {
return (a + (((b - a)) * k))
}
static func easeOut(_ k:Double) -> Double {
return (1.0 - ((((1.0 - k)) * ((1.0 - k))) * ((1.0 - k))))
}
static func easeIn(_ k:Double) -> Double {
return ((k * k) * k)
}
static func easeInOut(_ k:Double) -> Double {
return ((k < 0.5) ? (((4.0 * k) * k) * k) : (1.0 - (pow(((-2.0 * k) + 2.0),3.0) / 2.0)))
}
static func springAt(_ t:Double,_ from:Double,_ to:Double,_ v0:Double,_ d:Double,_ k:Double) -> Double {
if (t <= 0.0) {
return from
}
var x0:Double = (from - to)
var w0:Double = sqrt(k)
var z:Double = (d / ((2.0 * w0)))
if (z < 1.0) {
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
static let CROSS:Double = { () -> Double in
var lo:Double = 0.0
var hi:Double = 0.6
do {
var n:Double = 0.0
while (n < 40.0) {
defer { n += 1.0 }
var mid:Double = (((lo + hi)) / 2.0)
if (springAt(mid,1.0,0.0,0.0,TR.d,TR.k) > 0.0) {
lo = mid
} else {
hi = mid
}
}
}
return hi
}()
static let U_SQ:Double = (TR.st[2.0] + CROSS)
static func endAt(_ u:Double,_ i:Double,_ recap:Double) -> Double {
var q:Double = (u - TR.st[i])
if (u < TR.out) {
if ((recap == 1.0) || (q >= CROSS)) {
return 0.0
}
return ((q <= 0.0) ? 1.0 : max(springAt(q,1.0,0.0,0.0,TR.d,TR.k),0.0))
}
var r:Double = ((u - TR.out) - TR.st[i])
return ((r <= 0.0) ? 0.0 : springAt(r,0.0,1.0,0.0,TR.d,TR.k))
}
static func squashAt(_ u:Double) -> Double {
var q:Double = (((u - U_SQ)) / TR.squash)
if ((q <= 0.0) || (q >= 1.0)) {
return 1.0
}
if (q < 0.4) {
return mix(1.0,1.18,easeOut((q / 0.4)))
}
if (q < 0.75) {
return mix(1.18,0.92,easeInOut((((q - 0.4)) / 0.35)))
}
return mix(0.92,1.0,easeInOut((((q - 0.75)) / 0.25)))
}
static func elastic(_ dy:Double) -> Double {
if (dy >= 0.0) {
return (dy / ((1.0 + (dy / 160.0))))
}
return (-8.0 * ((1.0 - exp((dy / 30.0)))))
}
static func elasticInv(_ s:Double) -> Double {
if (s >= 0.0) {
return (((160.0 * min(s,150.0))) / ((160.0 - min(s,150.0))))
}
return (30.0 * log(max((1.0 + (s / 8.0)),0.01)))
}
static func elasticSlope(_ dy:Double) -> Double {
if (dy < 0.0) {
return 0.2
}
var q:Double = (1.0 + (dy / 160.0))
return (1.0 / ((q * q)))
}
static let dg:Double = hypot((G.bx[2.0] - G.hubX),(G.shoulderY - G.hubY))
static let DIRX:[Double] = [(((G.bx[0.0] - G.hubX)) / dg),0.0,(((G.bx[2.0] - G.hubX)) / dg)]
static let DIRY:[Double] = [(((G.shoulderY - G.hubY)) / dg),1.0,(((G.shoulderY - G.hubY)) / dg)]
static let VERT:Double = (G.beadY - G.shoulderY)
struct Script {
var n:Double
var answers:[Double]
var lens:[Double]
var cx:[Double]
var cA:Double
var cB:Double
var cW:Double
var score:Double
var top:Double
var arm:Double
}
final class Ev {
var rm:Double
var held:[Double]
var hs:[Double]
var hb:[Double]
var relT:[Double]
var relS:[Double]
var relV:[Double]
var relB:[Double]
var tapT:[Double]
var tapS0:[Double]
var haloOn:[Double]
var haloOff:[Double]
var pulseT:[Double]
var pulseK:[Double]
var pend:[Double]
var snapT:[Double]
var snapS:[Double]
var snapB:[Double]
var dimT:[Double]
var pl:[Double]
var pi:[Double]
var pop:[Double]
var popI:Double
var rejT:Double
var wA:Double
var swT:Double
var wB:Double
var nxT:Double
var shownT:Double
var sF:Double
var sTo:Double
var sT0:Double
var sDur:Double
var bonusT:Double
var transT:Double
var trRecap:Double
var trAt:Double
var recapT:Double
var recapOffT:Double
init(rm:Double,held:[Double],hs:[Double],hb:[Double],relT:[Double],relS:[Double],relV:[Double],relB:[Double],tapT:[Double],tapS0:[Double],haloOn:[Double],haloOff:[Double],pulseT:[Double],pulseK:[Double],pend:[Double],snapT:[Double],snapS:[Double],snapB:[Double],dimT:[Double],pl:[Double],pi:[Double],pop:[Double],popI:Double,rejT:Double,wA:Double,swT:Double,wB:Double,nxT:Double,shownT:Double,sF:Double,sTo:Double,sT0:Double,sDur:Double,bonusT:Double,transT:Double,trRecap:Double,trAt:Double,recapT:Double,recapOffT:Double) {
self.rm=rm
self.held=held
self.hs=hs
self.hb=hb
self.relT=relT
self.relS=relS
self.relV=relV
self.relB=relB
self.tapT=tapT
self.tapS0=tapS0
self.haloOn=haloOn
self.haloOff=haloOff
self.pulseT=pulseT
self.pulseK=pulseK
self.pend=pend
self.snapT=snapT
self.snapS=snapS
self.snapB=snapB
self.dimT=dimT
self.pl=pl
self.pi=pi
self.pop=pop
self.popI=popI
self.rejT=rejT
self.wA=wA
self.swT=swT
self.wB=wB
self.nxT=nxT
self.shownT=shownT
self.sF=sF
self.sTo=sTo
self.sT0=sT0
self.sDur=sDur
self.bonusT=bonusT
self.transT=transT
self.trRecap=trRecap
self.trAt=trAt
self.recapT=recapT
self.recapOffT=recapOffT
}
}
static func initEv(_ score:Double,_ rm:Double=0.0) -> Ev {
func n3(_ v:Double) -> [Double] { return [v,v,v] }
return Ev(rm:rm,held:n3(0.0),hs:n3(0.0),hb:n3(0.0),relT:n3(NEG),relS:n3(0.0),relV:n3(0.0),relB:n3(0.0),tapT:n3(NEG),tapS0:n3(0.0),haloOn:n3(POS),haloOff:n3(NEG),pulseT:n3(NEG),pulseK:n3(0.0),pend:n3(0.0),snapT:n3(POS),snapS:n3(0.0),snapB:n3(0.0),dimT:n3(NEG),pl:[0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0,0.0,0.0,0.0,NEG,1.0],pi:n3(0.0),pop:[NEG,NEG,NEG,NEG],popI:0.0,rejT:NEG,wA:0.0,swT:NEG,wB:0.0,nxT:NEG,shownT:NEG,sF:score,sTo:score,sT0:NEG,sDur:0.0,bonusT:NEG,transT:NEG,trRecap:0.0,trAt:POS,recapT:POS,recapOffT:NEG)
}
static func cloneEv(_ e:Ev) -> Ev {
return Ev(rm:e.rm,held:Array(e.held),hs:Array(e.hs),hb:Array(e.hb),relT:Array(e.relT),relS:Array(e.relS),relV:Array(e.relV),relB:Array(e.relB),tapT:Array(e.tapT),tapS0:Array(e.tapS0),haloOn:Array(e.haloOn),haloOff:Array(e.haloOff),pulseT:Array(e.pulseT),pulseK:Array(e.pulseK),pend:Array(e.pend),snapT:Array(e.snapT),snapS:Array(e.snapS),snapB:Array(e.snapB),dimT:Array(e.dimT),pl:Array(e.pl),pi:Array(e.pi),pop:Array(e.pop),popI:e.popI,rejT:e.rejT,wA:e.wA,swT:e.swT,wB:e.wB,nxT:e.nxT,shownT:e.shownT,sF:e.sF,sTo:e.sTo,sT0:e.sT0,sDur:e.sDur,bonusT:e.bonusT,transT:e.transT,trRecap:e.trRecap,trAt:e.trAt,recapT:e.recapT,recapOffT:e.recapOffT)
}
static func sAt(_ e:Ev,_ i:Double,_ t:Double) -> Double {
if (e.held[i] == 1.0) {
return e.hs[i]
}
if ((e.tapT[i] > (NEG / 2.0)) && (t >= e.tapT[i])) {
return mix(e.tapS0[i],52.0,easeInOut(seg(t,e.tapT[i],(e.tapT[i] + 0.14))))
}
var q:Double = (t - e.relT[i])
if ((q < 0.0) || (q > 3.0)) {
return 0.0
}
var raw:Double = springAt(q,e.relS[i],0.0,e.relV[i],SPRING.cord.d,SPRING.cord.k)
return ((raw < 0.0) ? (-18.0 * tanh((-raw / 18.0))) : raw)
}
static func bendAt(_ e:Ev,_ i:Double,_ t:Double) -> Double {
if (e.held[i] == 1.0) {
return e.hb[i]
}
var q:Double = (t - e.relT[i])
if ((q < 0.0) || (q > 3.0)) {
return 0.0
}
return springAt(q,e.relB[i],0.0,0.0,SPRING.cord.d,SPRING.cord.k)
}
static func haloAt(_ e:Ev,_ i:Double,_ t:Double) -> Double {
var on:Double = ((t >= e.haloOn[i]) ? easeOut(seg(t,e.haloOn[i],(e.haloOn[i] + (((e.rm == 1.0) ? 0.2 : 0.1))))) : 0.0)
var off:Double = (((e.haloOff[i] > e.haloOn[i]) && (t >= e.haloOff[i])) ? (1.0 - easeOut(seg(t,e.haloOff[i],(e.haloOff[i] + 0.3)))) : 1.0)
return (on * off)
}
static let haloLevel:(Ev,Double,Double) -> Double = haloAt
static func addPluck(_ e:Ev,_ i:Double,_ a1:Double,_ a2:Double,_ a3:Double,_ t0:Double,_ tau:Double) -> Void {
var slot:Double = e.pi[i]
var b:Double = ((((i * 3.0) + slot)) * 5.0)
e.pl[b] = a1
e.pl[(b + 1.0)] = a2
e.pl[(b + 2.0)] = a3
e.pl[(b + 3.0)] = t0
e.pl[(b + 4.0)] = tau
e.pi[i] = (((slot + 1.0))).truncatingRemainder(dividingBy:3.0)
}
static func releaseCord(_ e:Ev,_ i:Double,_ t:Double,_ s:Double,_ v:Double,_ b:Double) -> Void {
e.held[i] = 0.0
e.tapT[i] = NEG
e.relT[i] = t
e.relS[i] = s
e.relV[i] = v
e.relB[i] = b
}
static func firePulse(_ e:Ev,_ i:Double,_ t:Double,_ kind:Double) -> Void {
e.pulseT[i] = t
e.pulseK[i] = kind
e.pend[i] = ((kind == 1.0) ? 1.0 : 0.0)
}
static func pushPop(_ e:Ev,_ t0:Double,_ t1:Double) -> Void {
var s:Double = e.popI
e.pop[(s * 2.0)] = t0
e.pop[((s * 2.0) + 1.0)] = t1
e.popI = (1.0 - s)
}
static func wordAt(_ e:Ev,_ t:Double) -> Double {
return ((t >= e.nxT) ? e.wB : e.wA)
}
static func arrive(_ e:Ev,_ i:Double,_ ta:Double,_ correct:Bool,_ sc:Script,_ mode:Double) -> Void {
var cur:Double = wordAt(e,ta)
e.pend[i] = 0.0
var rm:Bool = (e.rm == 1.0)
if correct {
var last:Bool = ((mode == 1.0) || (cur >= (sc.n - 1.0)))
var quick:Bool = ((ta - e.shownT) < 4.5)
e.wA = cur
e.swT = (ta + ((rm ? 0.0 : 0.07)))
e.sF = e.sTo
e.sTo = ((e.sF + 250.0) + ((quick ? 15.0 : 0.0)))
e.sT0 = (ta + 0.07)
e.sDur = (rm ? 0.0 : 0.6)
if quick {
e.bonusT = (ta + 0.17)
}
e.haloOff[i] = (ta + ((rm ? 0.6 : 0.1)))
var t1:Double = (ta + 0.82)
if (mode == 1.0) {
e.trAt = (ta + 0.53)
t1 = e.trAt
e.nxT = POS
} else {
if last {
e.recapT = (ta + 0.9)
e.nxT = POS
} else {
e.wB = (cur + 1.0)
e.nxT = (ta + ((rm ? 0.2 : 0.82)))
e.shownT = e.nxT
}
}
if !rm {
pushPop(e,ta,t1)
}
} else {
if rm {
e.dimT[i] = ta
e.haloOff[i] = (ta + 0.3)
} else {
e.rejT = ta
var st:Double = (ta + 0.05)
e.snapT[i] = st
e.snapS[i] = sAt(e,i,st)
e.snapB[i] = bendAt(e,i,st)
e.haloOff[i] = ta
do {
var j:Double = 0.0
while (j < 3.0) {
defer { j += 1.0 }
if (j != i) {
addPluck(e,j,3.0,0.0,-0.33,st,0.25)
}
}
}
}
}
}
static func startTransition(_ e:Ev,_ T0:Double,_ sc:Script,_ fromRecap:Double) -> Void {
e.transT = T0
e.trRecap = fromRecap
e.trAt = POS
if (e.rm != 1.0) {
var li:Double = Double(Int(e.popI) ^ Int(1.0))
if ((e.pop[(li * 2.0)] > (NEG / 2.0)) && (abs((e.pop[((li * 2.0) + 1.0)] - T0)) < 1e-9)) {
e.pop[((li * 2.0) + 1.0)] = (T0 + TR.rel)
} else {
pushPop(e,T0,(T0 + TR.rel))
}
}
var cur:Double = wordAt(e,T0)
e.wA = cur
if !(((e.swT >= e.shownT) && (e.swT <= (T0 + 0.01)))) {
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
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
e.held[i] = 0.0
e.tapT[i] = NEG
if ((e.haloOn[i] < T0) && (e.haloOff[i] < e.haloOn[i])) {
e.haloOff[i] = T0
}
}
}
}
static func pull(_ e:Ev,_ i:Double,_ t:Double,_ a:Double,_ b:Double,_ rel:Double,_ to:Double) -> Void {
if ((t >= a) && (t < rel)) {
e.held[i] = 1.0
e.hs[i] = (to * easeInOut(seg(t,a,b)))
}
if (t >= rel) {
releaseCord(e,i,rel,to,0.0,0.0)
firePulse(e,i,rel,1.0)
}
}
static func autoEventsAt(_ t:Double,_ sc:Script) -> Ev {
var e:Ev = initEv(sc.score)
e.shownT = -1.1
var A:Double = sc.cA
var B:Double = sc.cB
var Wc:Double = sc.cW
if (t >= 0.5) {
e.haloOn[A] = 0.5
}
pull(e,A,t,0.6,1.5,1.7,PULL)
var a1:Double = (1.7 + (LREST[A] / PULSE_SPEED))
if (t >= a1) {
arrive(e,A,a1,true,sc,0.0)
}
if (t >= 3.8) {
e.haloOn[Wc] = 3.8
e.haloOff[Wc] = NEG
}
pull(e,Wc,t,3.9,4.8,4.8,PULL)
var a2:Double = (4.8 + (LREST[Wc] / PULSE_SPEED))
if (t >= a2) {
arrive(e,Wc,a2,false,sc,0.0)
}
if (t >= 6.8) {
addPluck(e,0.0,7.0,2.5,1.0,6.8,0.9)
}
if (t >= 6.95) {
addPluck(e,1.0,7.0,2.5,1.0,6.95,0.9)
}
if (t >= 7.1) {
addPluck(e,2.0,7.0,2.5,1.0,7.1,0.9)
}
if (t >= 7.9) {
e.haloOn[B] = 7.9
e.haloOff[B] = NEG
}
pull(e,B,t,8.0,8.7,8.7,PULL)
var a3:Double = (8.7 + (LREST[B] / PULSE_SPEED))
if (t >= a3) {
arrive(e,B,a3,true,sc,1.0)
}
if (t >= e.trAt) {
startTransition(e,e.trAt,sc,0.0)
}
return e
}
struct Frame {
var t:Double
var e:Ev
var s:[Double]
var bend:[Double]
var hx:Double
var hy:Double
var bx:[Double]
var by:[Double]
var beadSc:[Double]
var beadA:[Double]
var tip:Double
var halo:[Double]
var labelA:[Double]
var labelSc:[Double]
var labelDy:[Double]
var labelBlur:[Double]
var heat:[Double]
var heatHot:[Double]
var width:[Double]
var pulseFrac:[Double]
var pulseAmp:[Double]
var pulseK:[Double]
var draw:[Double]
var cordA:[Double]
var bodyW:[Double]
var ropeTau:[Double]
var mode:Double
var u:Double
var ballR:Double
var arcA:Double
var arcSweep:Double
var arcRot:Double
var hubA:Double
var shockR:Double
var shockA:Double
var fx:Double
var fy:Double
var gain:Double
var cool:Double
var w:Double
var lx:[Double]
var ly:[Double]
var ls:[Double]
var la:[Double]
var sF:Double
var sTo:Double
var sT0:Double
var sDur:Double
var bonusA:Double
var hudA:Double
var scoreA:Double
var roll:Double
var recapA:Double
var waveFrom:Double
}
static let BIG_POP:Double = 25.5
static func compose(_ e:Ev,_ t:Double,_ sc:Script) -> Frame {
var rm:Bool = (e.rm == 1.0)
var s:[Double] = [0.0,0.0,0.0]
var bend:[Double] = [0.0,0.0,0.0]
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
s[i] = sAt(e,i,t)
bend[i] = bendAt(e,i,t)
}
}
var hx:Double = G.hubX
var hy:Double = G.hubY
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
var p:Double = (max(s[i],0.0) * 0.06)
hx += (p * DIRX[i])
hy += (p * DIRY[i])
}
}
if ((t >= e.rejT) && (t < (e.rejT + 0.24))) {
var k:Double = (((t - e.rejT)) / 0.24)
hx += (((4.0 * sin((((2.0 * Double.pi) * 25.0) * ((t - e.rejT))))) * ((1.0 - k))) * ((1.0 - k)))
}
var bx:[Double] = [0.0,0.0,0.0]
var by:[Double] = [0.0,0.0,0.0]
var beadSc:[Double] = [1.0,1.0,1.0]
var beadA:[Double] = [1.0,1.0,1.0]
var halo:[Double] = [0.0,0.0,0.0]
var labelA:[Double] = [1.0,1.0,1.0]
var labelSc:[Double] = [1.0,1.0,1.0]
var labelDy:[Double] = [0.0,0.0,0.0]
var labelBlur:[Double] = [0.0,0.0,0.0]
var heat:[Double] = [0.0,0.0,0.0]
var heatHot:[Double] = [0.0,0.0,0.0]
var width:[Double] = [2.0,2.0,2.0]
var pulseFrac:[Double] = [-1.0,-1.0,-1.0]
var pulseAmp:[Double] = [0.0,0.0,0.0]
var pulseK:[Double] = [0.0,0.0,0.0]
var draw:[Double] = [1.0,1.0,1.0]
var cordA:[Double] = [1.0,1.0,1.0]
var bodyW:[Double] = [8.0,8.0,8.0]
var ropeTau:[Double] = [-1.0,-1.0,-1.0]
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
var slack:Double = 0.0
do {
var j:Double = 0.0
while (j < 3.0) {
defer { j += 1.0 }
if (j != i) {
slack += (0.02 * max(s[j],0.0))
}
}
}
bx[i] = (G.bx[i] + bend[i])
by[i] = ((G.beadY + s[i]) - slack)
halo[i] = haloAt(e,i,t)
if ((rm && (t >= e.dimT[i])) && (t < (e.dimT[i] + 0.7))) {
beadA[i] = (1.0 - (0.6 * sin((Double.pi * seg(t,e.dimT[i],(e.dimT[i] + 0.7))))))
}
var q:Double = (t - e.relT[i])
if ((e.held[i] == 1.0) || (((e.tapT[i] > (NEG / 2.0)) && (t >= e.tapT[i])))) {
labelDy[i] = (0.85 * s[i])
} else {
if ((q >= 0.0) && (q < 3.0)) {
labelDy[i] = (0.85 * springAt(q,e.relS[i],0.0,e.relV[i],SPRING.snap.d,SPRING.snap.k))
}
}
var sp:Double = max(s[i],0.0)
heat[i] = clamp01((sp / PULL))
if rm {
heat[i] = max(heat[i],(0.55 * halo[i]))
}
do {
var slot:Double = 0.0
while (slot < 3.0) {
defer { slot += 1.0 }
var b:Double = ((((i * 3.0) + slot)) * 5.0)
var q:Double = (t - e.pl[(b + 3.0)])
if ((q >= 0.0) && (q < 4.0)) {
heat[i] = max(heat[i],((0.7 * clamp01((abs(e.pl[b]) / 7.0))) * exp((-q / e.pl[(b + 4.0)]))))
}
}
}
heatHot[i] = heat[i]
width[i] = 2.0
if ((e.pulseK[i] > 0.0) && (t >= e.pulseT[i])) {
var dist:Double = (((t - e.pulseT[i])) * PULSE_SPEED)
var hr:Double = clamp01((e.relS[i] / PULL))
if ((e.pulseK[i] == 1.0) && (dist < LREST[i])) {
pulseFrac[i] = (dist / LREST[i])
pulseAmp[i] = seg(dist,0.0,18.0)
pulseK[i] = 1.0
heatHot[i] = max(heat[i],(hr * ((1.0 - (0.25 * pulseFrac[i])))))
} else {
if (e.pulseK[i] == 2.0) {
var lv:Double = ((i == 1.0) ? (0.55 * LREST[1.0]) : VERT)
if (dist < lv) {
pulseFrac[i] = (dist / LREST[i])
pulseAmp[i] = ((0.6 * ((1.0 - (dist / lv)))) * seg(dist,0.0,18.0))
pulseK[i] = 2.0
heatHot[i] = max(heat[i],((hr * 0.5) * ((1.0 - (dist / lv)))))
}
}
}
}
if (t >= e.snapT[i]) {
var qs:Double = (t - e.snapT[i])
pulseFrac[i] = -1.0
if (qs < 0.725) {
ropeTau[i] = qs
labelA[i] = (1.0 - seg(qs,0.0,0.42))
halo[i] = 0.0
} else {
draw[i] = easeOut(seg(qs,0.72,1.07))
beadSc[i] = ((qs < 1.07) ? 0.0 : max(0.0,springAt((qs - 1.07),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)))
labelA[i] = 1.0
labelSc[i] = ((qs < 1.13) ? 0.0 : max(0.0,springAt((qs - 1.13),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)))
labelDy[i] = 0.0
}
}
}
}
var fx:Double = G.fieldX
var fy:Double = G.fieldY
var gain:Double = 1.0
var best:Double = -1.0
var bs:Double = 0.0
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
if (s[i] > bs) {
bs = s[i]
best = i
}
}
}
if (best >= 0.0) {
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
do {
var p:Double = 0.0
while (p < 2.0) {
defer { p += 1.0 }
var t0:Double = e.pop[(p * 2.0)]
var t1:Double = e.pop[((p * 2.0) + 1.0)]
if ((t0 < (NEG / 2.0)) || (t < t0)) {
continue
}
var g:Double = springAt((t - t0),0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k)
if (t >= t1) {
g *= springAt((t - t1),1.0,0.0,0.0,SPRING.snap.d,SPRING.snap.k)
}
ballR += (BIG_POP * max(g,0.0))
gain += (0.2 * ((1.0 - easeOut(seg(t,t0,(t0 + 0.4))))))
if (t0 > latest) {
latest = t0
arcSweep = easeOut(seg(t,t0,(t0 + 0.2)))
arcA = (arcSweep * ((1.0 - seg(t,t1,(t1 + 0.2)))))
arcRot = (((t - t0)) * Double.pi)
}
}
}
var arrival:Double = max(latest,e.rejT)
if ((!rm && (t >= arrival)) && (t < (arrival + 0.32))) {
var q:Double = seg(t,arrival,(arrival + 0.32))
shockR = (40.0 * easeOut(q))
shockA = ((0.85 * ((1.0 - q))) * ((1.0 - q)))
}
if !rm {
var life:Double = (seg(t,0.0,0.25) * ((1.0 - seg(t,11.55,11.95))))
gain += ((0.025 * sin((((2.0 * Double.pi) * t) / 3.0))) * life)
fy += ((3.0 * sin((((2.0 * Double.pi) * t) / 4.0))) * life)
}
var cool:Double = 0.0
if (t >= e.rejT) {
cool = (seg(t,e.rejT,(e.rejT + 0.12)) * ((1.0 - easeInOut(seg(t,(e.rejT + 0.77),(e.rejT + 1.27))))))
}
gain -= (0.15 * cool)
var showB:Bool = (t >= e.nxT)
var w:Double = (showB ? e.wB : e.wA)
var n:Double = (sc.lens.safe(w) ?? 0.0)
var stg:Double = ((n >= 6.0) ? 0.06 : 0.07)
var rollIn:Bool = (((!rm && showB) && (e.transT > (NEG / 2.0))) && (e.nxT == (e.transT + TR.nx)))
var lx:[Double] = []
var ly:[Double] = []
var ls:[Double] = []
var la:[Double] = []
do {
var k:Double = 0.0
while (k < MAXL) {
defer { k += 1.0 }
var x:Double = 0.0
var y:Double = 0.0
var sc1:Double = 1.0
var a:Double = ((k < n) ? 1.0 : 0.0)
var cx:Double = (sc.cx.safe(((w * MAXL) + k)) ?? G.hubX)
if (((k < n) && !showB) && (t >= e.swT)) {
if rm {
a = (1.0 - seg(t,e.swT,(e.swT + 0.2)))
} else {
var land:Double = (e.swT + (stg * k))
var p:Double = easeIn(seg(t,land,(land + 0.26)))
y = (38.0 * p)
x = ((((G.hubX - cx)) * 0.55) * p)
sc1 = (1.0 - (0.7 * p))
a = (1.0 - seg(p,0.45,1.0))
if ((t >= (land + 0.26)) && (t < (land + 1.6))) {
ballR += max(0.0,springAt(((t - land) - 0.26),0.0,0.0,52.0,SPRING.pop.d,SPRING.pop.k))
}
}
} else {
if (((k < n) && showB) && (t < (e.nxT + 2.0))) {
if rm {
a = seg(t,e.nxT,(e.nxT + 0.2))
} else {
if rollIn {
var tk:Double = ((t - e.nxT) - (0.05 * k))
var pr:Double = ((tk <= 0.0) ? 0.0 : ((tk > 0.9) ? 1.0 : mix(springAt(tk,0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k),1.0,easeOut(seg(tk,0.6,0.9)))))
y = (TR.roll * ((1.0 - pr)))
} else {
var tk:Double = ((t - e.nxT) - (0.05 * k))
var pr:Double = ((tk <= 0.0) ? 0.0 : ((tk > 0.72) ? 1.0 : mix(springAt(tk,0.0,1.0,0.0,SPRING.pop.d,SPRING.pop.k),1.0,easeOut(seg(tk,0.5,0.72)))))
y = (((G.hubY - G.wordY)) * ((1.0 - pr)))
x = (((G.hubX - cx)) * ((1.0 - pr)))
sc1 = pr
a = ((tk > 0.0) ? 1.0 : 0.0)
}
}
}
}
lx.append(x)
ly.append(y)
ls.append(sc1)
la.append(a)
}
}
var bonusA:Double = ((t >= e.bonusT) ? ((0.7 * seg(t,e.bonusT,(e.bonusT + 0.2))) * ((1.0 - seg(t,(e.bonusT + 1.0),(e.bonusT + 1.2))))) : 0.0)
var hudA:Double = 1.0
var recapA:Double = ((t >= e.recapT) ? easeOut(seg(t,e.recapT,(e.recapT + 0.3))) : 0.0)
if ((e.recapOffT > e.recapT) && (t >= e.recapOffT)) {
recapA *= (1.0 - seg(t,e.recapOffT,(e.recapOffT + 0.25)))
}
var treeA:Double = (1.0 - recapA)
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
cordA[i] *= treeA
beadA[i] *= treeA
labelA[i] *= treeA
}
}
var mode:Double = 0.0
var tip:Double = 0.0
var waveFrom:Double = NEG
var u:Double = (t - e.transT)
if (((u >= 0.0) && (u < TR.end)) && !rm) {
mode = 6.0
tip = 1.0
waveFrom = ((e.transT + TR.out) - 0.1)
var rec:Double = e.trRecap
var fin:Double = easeOut(seg(u,TR.settle,(TR.end - 0.05)))
do {
var i:Double = 0.0
while (i < 3.0) {
defer { i += 1.0 }
var raw:Double = mix(endAt(u,i,rec),1.0,fin)
var v:Double = clamp01(((abs((mix(endAt((u + 0.01),i,rec),1.0,fin) - mix(endAt((u - 0.01),i,rec),1.0,fin))) / 0.02) / 7.0))
var over:Double = (max((raw - 1.0),0.0) * TR.over)
var p:Double = min(raw,1.0)
draw[i] = p
by[i] += (over * LREST[i])
beadSc[i] = ((p <= 0.0005) ? 0.0 : mix(0.25,1.0,easeOut(seg(p,0.04,0.35))))
labelSc[i] = easeOut(seg(p,0.0,0.6))
labelA[i] = treeA
labelDy[i] = 0.0
labelBlur[i] = 0.0
bodyW[i] = mix(8.0,3.5,easeOut(v))
heat[i] = max((heat[i] * ((1.0 - seg(u,0.0,0.2)))),(0.55 * v))
heatHot[i] = heat[i]
pulseFrac[i] = -1.0
halo[i] *= (1.0 - seg(u,0.0,0.15))
}
}
ballR *= squashAt(u)
var qr:Double = seg(u,U_SQ,(U_SQ + TR.ring))
if ((u >= U_SQ) && (qr < 1.0)) {
shockR = mix(20.0,60.0,easeOut(qr))
shockA = (0.35 * ((1.0 - qr)))
gain += (0.14 * sin((Double.pi * seg(u,U_SQ,(U_SQ + 0.5)))))
} else {
shockR = 0.0
shockA = 0.0
}
} else {
if ((e.transT > (NEG / 2.0)) && (u >= TR.end)) {
waveFrom = ((e.transT + TR.out) - 0.1)
}
}
var roll:Double = (((((!rm && showB) && (e.transT > (NEG / 2.0))) && (e.nxT == (e.transT + TR.nx))) && (t < (e.nxT + 1.3))) ? 1.0 : 0.0)
return Frame(t:t,e:e,s:s,bend:bend,hx:hx,hy:hy,bx:bx,by:by,beadSc:beadSc,beadA:beadA,tip:tip,halo:halo,labelA:labelA,labelSc:labelSc,labelDy:labelDy,labelBlur:labelBlur,heat:heat,heatHot:heatHot,width:width,pulseFrac:pulseFrac,pulseAmp:pulseAmp,pulseK:pulseK,draw:draw,cordA:cordA,bodyW:bodyW,ropeTau:ropeTau,mode:mode,u:u,ballR:ballR,arcA:arcA,arcSweep:arcSweep,arcRot:arcRot,hubA:treeA,shockR:shockR,shockA:shockA,fx:fx,fy:fy,gain:gain,cool:cool,w:w,lx:lx,ly:ly,ls:ls,la:la,sF:e.sF,sTo:e.sTo,sT0:e.sT0,sDur:e.sDur,bonusA:(bonusA * hudA),hudA:hudA,scoreA:(hudA * ((1.0 - recapA))),roll:roll,recapA:recapA,waveFrom:waveFrom)
}
static func frameAt(_ tIn:Double,_ sc:Script) -> Frame {
var t:Double = (((((tIn).truncatingRemainder(dividingBy:LOOP)) + LOOP))).truncatingRemainder(dividingBy:LOOP)
return compose(autoEventsAt(t,sc),t,sc)
}
final class Pts {
var x:[Double]
var y:[Double]
var s:[Double]
var total:Double
init(x:[Double],y:[Double],s:[Double],total:Double) {
self.x=x
self.y=y
self.s=s
self.total=total
}
}
static func waveAt(_ pl:[Double],_ i:Double,_ u:Double,_ t:Double,_ from:Double) -> Double {
var x:Double = 0.0
if (pl.length < 45.0) {
return 0.0
}
do {
var slot:Double = 0.0
while (slot < 3.0) {
defer { slot += 1.0 }
var b:Double = ((((i * 3.0) + slot)) * 5.0)
var t0:Double = pl[(b + 3.0)]
if ((t0 < from) || (t < t0)) {
continue
}
var q:Double = (t - t0)
if (q > 6.0) {
continue
}
var env:Double = exp((-q / pl[(b + 4.0)]))
var w:Double = (((2.0 * Double.pi) * 7.0) * q)
x += (env * (((((pl[b] * sin((Double.pi * u))) * sin(w)) + ((pl[(b + 1.0)] * sin(((2.0 * Double.pi) * u))) * sin((2.0 * w)))) + ((pl[(b + 2.0)] * sin(((3.0 * Double.pi) * u))) * sin((3.0 * w))))))
}
}
return x
}
static func cordPoints(_ i:Double,_ hx:Double,_ hy:Double,_ beadY:Double,_ bend:Double,_ pl:[Double],_ t:Double,_ from:Double) -> Pts {
var xs:[Double] = []
var ys:[Double] = []
var vx:Double = G.bx[i]
var vTop:Double = hy
if (i != 1.0) {
var dx:Double = (vx - hx)
var dy:Double = (G.shoulderY - hy)
var len:Double = hypot(dx,dy)
var R:Double = G.corner
var cx0:Double = (vx - (((dx / len)) * R))
var cy0:Double = (G.shoulderY - (((dy / len)) * R))
var ND:Double = 9.0
do {
var k:Double = 0.0
while (k < ND) {
defer { k += 1.0 }
var q:Double = (k / ND)
xs.append((hx + (((cx0 - hx)) * q)))
ys.append((hy + (((cy0 - hy)) * q)))
}
}
var NC:Double = 6.0
do {
var k:Double = 0.0
while (k <= NC) {
defer { k += 1.0 }
var q:Double = (k / NC)
var a:Double = (((1.0 - q)) * ((1.0 - q)))
var b:Double = ((2.0 * q) * ((1.0 - q)))
var c:Double = (q * q)
xs.append((((a * cx0) + (b * vx)) + (c * vx)))
ys.append((((a * cy0) + (b * G.shoulderY)) + (c * ((G.shoulderY + R)))))
}
}
vTop = (G.shoulderY + R)
} else {
xs.append(hx)
ys.append(hy)
}
var NV:Double = ((i == 1.0) ? 68.0 : 60.0)
var vlen:Double = (beadY - vTop)
do {
var k:Double = 1.0
while (k <= NV) {
defer { k += 1.0 }
var u:Double = (k / NV)
var x:Double = ((i == 1.0) ? (hx + (((vx - hx)) * u)) : vx)
x += ((((bend * u) * u) * u) + waveAt(pl,i,u,t,from))
xs.append(x)
ys.append((vTop + (vlen * u)))
}
}
var ss:[Double] = [0.0]
do {
var k:Double = 1.0
while (k < xs.length) {
defer { k += 1.0 }
ss.append((ss[(k - 1.0)] + hypot((xs[k] - xs[(k - 1.0)]),(ys[k] - ys[(k - 1.0)]))))
}
}
return Pts(x:xs,y:ys,s:ss,total:ss[(ss.length - 1.0)])
}
static func addBump(_ p:Pts,_ pos:Double,_ amp:Double) -> Void {
if (amp <= 0.0) {
return
}
var n:Double = p.x.length
var nx:[Double] = []
var ny:[Double] = []
do {
var k:Double = 0.0
while (k < n) {
defer { k += 1.0 }
var a:Double = max((k - 1.0),0.0)
var b:Double = min((k + 1.0),(n - 1.0))
var tx:Double = (p.x[b] - p.x[a])
var ty:Double = (p.y[b] - p.y[a])
var l:Double = nonzero(hypot(tx,ty),1.0)
nx.append((-ty / l))
ny.append((tx / l))
}
}
do {
var k:Double = 0.0
while (k < n) {
defer { k += 1.0 }
var d:Double = (((p.s[k] - pos)) / 12.0)
if ((d > 4.0) || (d < -4.0)) {
continue
}
var g:Double = ((exp((-d * d)) * 12.0) * amp)
p.x[k] += (nx[k] * g)
p.y[k] += (ny[k] * g)
}
}
}
static func pointAt(_ p:Pts,_ a:Double) -> Vec2 {
var n:Double = p.x.length
if (a <= 0.0) {
return Vec2(x:p.x[0.0],y:p.y[0.0])
}
do {
var k:Double = 1.0
while (k < n) {
defer { k += 1.0 }
if (p.s[k] >= a) {
var q:Double = (((a - p.s[(k - 1.0)])) / max((p.s[k] - p.s[(k - 1.0)]),0.000001))
return Vec2(x:mix(p.x[(k - 1.0)],p.x[k],q),y:mix(p.y[(k - 1.0)],p.y[k],q))
}
}
}
return Vec2(x:p.x[(n - 1.0)],y:p.y[(n - 1.0)])
}
static let ROPE_N:Double = 12.0
static let ROPE_STEPS:Double = 87.0
static let GRAV:Double = 2400.0
static func bakeRope(_ i:Double) -> [Double] {
var p:Pts = cordPoints(i,G.hubX,G.hubY,G.beadY,0.0,[],0.0,POS)
var px:[Double] = []
var py:[Double] = []
do {
var k:Double = 0.0
while (k < ROPE_N) {
defer { k += 1.0 }
var q:Vec2 = pointAt(p,(((p.total * k)) / ((ROPE_N - 1.0))))
px.append(q.x)
py.append(q.y)
}
}
var rest:Double = (p.total / ((ROPE_N - 1.0)))
var dt:Double = (1.0 / 120.0)
var ox:[Double] = []
var oy:[Double] = []
do {
var k:Double = 0.0
while (k < ROPE_N) {
defer { k += 1.0 }
var a:Double = max((k - 1.0),0.0)
var b:Double = min((k + 1.0),(ROPE_N - 1.0))
var tx:Double = (px[b] - px[a])
var ty:Double = (py[b] - py[a])
var l:Double = nonzero(hypot(tx,ty),1.0)
var v:Double = (760.0 * ((1.0 - (k / ((ROPE_N - 1.0))))))
ox.append((px[k] - ((((tx / l)) * v) * dt)))
oy.append((py[k] - ((((ty / l)) * v) * dt)))
}
}
var out:[Double] = []
func save() -> Void {
do {
var k:Double = 0.0
while (k < ROPE_N) {
defer { k += 1.0 }
out.append(contentsOf:[px[k],py[k]])
}
}
}
save()
var bx0:Double = px[(ROPE_N - 1.0)]
var by0:Double = py[(ROPE_N - 1.0)]
do {
var step:Double = 1.0
while (step <= ROPE_STEPS) {
defer { step += 1.0 }
var tt:Double = (step * dt)
do {
var k:Double = 0.0
while (k < (ROPE_N - 1.0)) {
defer { k += 1.0 }
var vx:Double = (((px[k] - ox[k])) * 0.996)
var vy:Double = (((py[k] - oy[k])) * 0.996)
ox[k] = px[k]
oy[k] = py[k]
px[k] += vx
py[k] += (vy + ((GRAV * dt) * dt))
}
}
px[(ROPE_N - 1.0)] = bx0
py[(ROPE_N - 1.0)] = (by0 + (((0.5 * GRAV) * tt) * tt))
ox[(ROPE_N - 1.0)] = px[(ROPE_N - 1.0)]
oy[(ROPE_N - 1.0)] = py[(ROPE_N - 1.0)]
do {
var it:Double = 0.0
while (it < 4.0) {
defer { it += 1.0 }
do {
var k:Double = (ROPE_N - 2.0)
while (k >= 0.0) {
defer { k -= 1.0 }
var dx:Double = (px[(k + 1.0)] - px[k])
var dy:Double = (py[(k + 1.0)] - py[k])
var d:Double = nonzero(hypot(dx,dy),1.0)
var diff:Double = (((d - rest)) / d)
if ((k + 1.0) == (ROPE_N - 1.0)) {
px[k] += (dx * diff)
py[k] += (dy * diff)
} else {
px[k] += ((dx * diff) * 0.5)
py[k] += ((dy * diff) * 0.5)
px[(k + 1.0)] -= ((dx * diff) * 0.5)
py[(k + 1.0)] -= ((dy * diff) * 0.5)
}
}
}
}
}
save()
}
}
return out
}
static let ROPE:[[Double]] = [bakeRope(0.0),bakeRope(1.0),bakeRope(2.0)]
static func ropeAt(_ rope:[Double],_ q:Double,_ s0:Double,_ b0:Double) -> RopePoints {
var fi:Double = min(max((q * 120.0),0.0),ROPE_STEPS)
var f0:Double = floor(fi)
var f1:Double = min((f0 + 1.0),ROPE_STEPS)
var k:Double = (fi - f0)
var w:Double = (1.0 - easeOut(seg(q,0.0,0.15)))
var xs:[Double] = []
var ys:[Double] = []
do {
var n:Double = 0.0
while (n < ROPE_N) {
defer { n += 1.0 }
var a:Double = (((f0 * ROPE_N) * 2.0) + (n * 2.0))
var b:Double = (((f1 * ROPE_N) * 2.0) + (n * 2.0))
var u:Double = (n / ((ROPE_N - 1.0)))
xs.append((mix(rope[a],rope[b],k) + ((((b0 * u) * u) * u) * w)))
ys.append((mix(rope[(a + 1.0)],rope[(b + 1.0)],k) + ((s0 * u) * w)))
}
}
return RopePoints(x:xs,y:ys)
}
}

private extension Array {
var length:Double {Double(count)}
subscript(_ index:Double)->Element { get {self[Int(index)]} set {self[Int(index)]=newValue} }
func safe(_ index:Double)->Element? { let i=Int(index); return indices.contains(i) ? self[i] : nil }
}
