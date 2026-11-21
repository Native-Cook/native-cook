import Foundation

/// Native analytic port of RN frameAt at commit 58acf81b: exact springs, fronts and closed strand phase.
struct GearPuckSpeedLineFrame {
    var values = [Double](repeating: 0, count: 28)
    var g: Double { get { values[0] } set { values[0] = newValue } }
    var press: Double { get { values[1] } set { values[1] = newValue } }
    var flash: Double { get { values[2] } set { values[2] = newValue } }
    var speed: Double { get { values[3] } set { values[3] = newValue } }
    var rate: Double { get { values[4] } set { values[4] = newValue } }
    var amplitude: Double { get { values[5] } set { values[5] = newValue } }
    var crest: Double { get { values[6] } set { values[6] = newValue } }
    var lean: Double { get { values[7] } set { values[7] = newValue } }
    var jitter: Double { get { values[8] } set { values[8] = newValue } }
    var phase: Double { get { values[9] } set { values[9] = newValue } }
    var curtain: Double { get { values[10] } set { values[10] = newValue } }
    var whip: Double { get { values[11] } set { values[11] = newValue } }
    var sway: Double { get { values[12] } set { values[12] = newValue } }
    var color0: Double { get { values[13] } set { values[13] = newValue } }
    var color1: Double { get { values[14] } set { values[14] = newValue } }
    var color2: Double { get { values[15] } set { values[15] = newValue } }
    var frontPX: Double { get { values[16] } set { values[16] = newValue } }
    var frontPR: Double { get { values[17] } set { values[17] = newValue } }
    var frontLX: Double { get { values[18] } set { values[18] = newValue } }
    var frontLR: Double { get { values[19] } set { values[19] = newValue } }
    var packetX: Double { get { values[20] } set { values[20] = newValue } }
    var packetGain: Double { get { values[21] } set { values[21] = newValue } }
    var pluckX: Double { get { values[22] } set { values[22] = newValue } }
    var pluck: Double { get { values[23] } set { values[23] = newValue } }
    var pluckLag: Double { get { values[24] } set { values[24] = newValue } }
    var numK: Double { get { values[25] } set { values[25] = newValue } }
    var drop: Double { get { values[26] } set { values[26] = newValue } }
    var time: Double { get { values[27] } set { values[27] = newValue } }
    static let bands: [SIMD3<Float>] = [SIMD3(0.57647,0.90196,0.24706), SIMD3(0.18431,0.60784,1), SIMD3(1,0.64706,0.12157), SIMD3(1,0.16471,0.23529)]
    func color(at x: Double) -> SIMD3<Float> {
        let a = Self.bands[Int(color0.rounded()).clamped(0,3)]
        let b = Self.bands[Int(color1.rounded()).clamped(0,3)]
        let c = Self.bands[Int(color2.rounded()).clamped(0,3)]
        let p = Float(1 - GearPuckSpeedLineMath.smooth(frontPR-8,frontPR+8,abs(x-frontPX)))
        let q = Float(1 - GearPuckSpeedLineMath.smooth(frontLR-8,frontLR+8,abs(x-frontLX)))
        return (a + (b-a)*p) + (c-(a+(b-a)*p))*q
    }
    func curve(_ x: Double) -> Double {
        let dx = x-crest
        let sigma = 68 * (((dx < 0) == (lean > 0)) ? 1 + 0.3*abs(lean) : 1)
        return 330-(amplitude+jitter)*exp(-dx*dx/(2*sigma*sigma)) + pluck*exp(-pow(x-pluckX,2)/5000)
    }
    var arc: [Float] {
        var out=[Float](repeating:0,count:64),acc=0.0
        let dx=402.0/63
        for i in 1..<64 {
            for j in 0..<4 {let x=Double(i-1)*dx+(Double(j)+0.5)*dx/4;let slope=curve(x+0.5)-curve(x-0.5);acc += (sqrt(1+slope*slope)-1)*dx/4}
            out[i]=Float(acc)
        }
        return out
    }
    var drums: [Double] {
        let a = max(abs(speed),0), fl = floor(a)
        let u = fl + GearPuckSpeedLineMath.smooth(0.3,0.7,a-fl)
        func wrap(_ p:Double)->Double {let m=p-10*floor(p/10);return floor(p/10)+(m>9 ? m-9:0)}
        let t=wrap(u);return [wrap(t),t,u]
    }
    var readout: (cx: Double, base: Double, scale: Double, width: Double) {
        let d = drums
        let cells = 1 + d[1].clamped(0,1) + d[0].clamped(0,1), w = cells*47.12
        let hx = crest.clamped(14+w/2,388-w/2), park = 34.0/76
        var cx = GearPuckSpeedLineMath.mix(22+w*park/2,hx,numK)
        var base = GearPuckSpeedLineMath.mix(368,326,numK)
        var scale = GearPuckSpeedLineMath.mix(park,1,numK)
        cx = GearPuckSpeedLineMath.mix(cx,hx,drop); base = GearPuckSpeedLineMath.mix(base,372,drop)
        scale = GearPuckSpeedLineMath.mix(scale,0.6,drop)
        return (cx,base,scale,w)
    }
}

enum GearPuckSpeedLineScore {
    static let loop=10.0
    private struct Crossing {let t:Double;let from:Int;let to:Int;let x:Double}
    private static func seg(_ t:Double,_ a:Double,_ b:Double)->Double {((t-a)/(b-a)).clamped(0,1)}
    private static func easeOut(_ k:Double)->Double {1-pow(1-k,3)}
    private static func easeInOut(_ k:Double)->Double {k<0.5 ? 4*k*k*k:1-pow(-2*k+2,3)/2}
    private static func spring(_ t:Double,_ from:Double,_ to:Double,_ v:Double,_ d:Double,_ k:Double)->Double {GearPuckSpeedLineMath.spring(t,from,to,v,d,k)}
    private static func climb(_ x:Double)->Double {
        if x<=0{return 0};if x>=1{return 1}
        var lo=0.0,hi=1.0
        for _ in 0..<22 {let u=(lo+hi)/2;let bx=3*(1-u)*(1-u)*u*0.42+3*(1-u)*u*u*0.22+u*u*u;if bx<x {lo=u}else{hi=u}}
        let u=(lo+hi)/2
        return 3*(1-u)*(1-u)*u*0.04+3*(1-u)*u*u+u*u*u
    }
    static func speedAt(_ t:Double)->Double {
        if t<1.68{return 0}
        if t<3.88{return 132*climb(seg(t,1.68,3.88))}
        if t<4.78{return 132}
        if t<6.38{return 132+65*climb(seg(t,4.78,6.38))}
        if t<6.85{return 197}
        if t<8.15{return 197*(1-easeInOut(seg(t,6.85,8.15)))}
        return 0
    }
    private static func band(_ speed:Double)->Int {speed<25 ? 0:speed<120 ? 1:speed<165 ? 2:3}
    private static func crest(_ speed:Double)->Double {56+270*min(abs(speed),200)/200}
    // Same one-time quadrature and threshold crossings as RN, not a sampled animation asset.
    private static let phases:[Double] = {
        var out=[0.0],acc=0.0
        for i in 1...2000 {acc += (6+1.2*speedAt((Double(i)-0.5)*0.005))*0.005;out.append(acc)}
        let scale=(acc/24).rounded()*24/acc
        return out.map{$0*scale}
    }()
    private static let crossings:[Crossing] = {
        var out=[Crossing](),previous=band(speedAt(0))
        for i in 1...10000 {let t=Double(i)*0.001,b=band(speedAt(t));if b != previous {out.append(Crossing(t:t,from:previous,to:b,x:crest(speedAt(t))));previous=b}}
        return out
    }()
    private static func roll(_ t:Double,_ a:Double,_ b:Double,_ from:Double,_ to:Double)->Double {
        if t<b {let k=seg(t,a,b);return from+(to-from)*(0.88*k*k*(3-2*k)+0.12*k*k*k)}
        return spring(t-b,to,to,0.36*(to-from)/(b-a),24,420)
    }
    private static func press(_ t:Double,_ a:Double,_ land:Double)->Double {
        if t<a{return 0};if t<land{return easeOut(seg(t,a,a+0.12))}
        return max(spring(t-land,1,0,0,19,195),-0.08)
    }
    private static func flash(_ age:Double)->Double {age<0 ? 0:age<0.03 ? age/0.03:exp(-(age-0.03)/0.09)}
    static func frame(_ seconds:Double)->GearPuckSpeedLineFrame {
        let t=((seconds.truncatingRemainder(dividingBy:10))+10).truncatingRemainder(dividingBy:10)
        var f=GearPuckSpeedLineFrame()
        f.time=t;f.speed=speedAt(t);f.rate=(speedAt(t+0.01)-speedAt(max(t-0.01,0)))/0.02
        f.lean=((f.speed-speedAt(max(t-0.25,0)))/0.25/70).clamped(-1,1)
        if t>=0.52 && t<4.25 {f.g=roll(t,0.52,1.36,0,2)}
        else if t>=4.25 && t<8.15 {f.g=roll(t,4.25,4.68,2,3)}
        else if t>=8.15 {f.g=roll(t,8.15,8.85,3,0)}
        f.press=t<4.17 ? press(t,0.4,1.36):t<8.05 ? press(t,4.17,4.68):press(t,8.05,8.85)
        f.flash=max(flash(t-1.36),flash(t-4.68),0.7*flash(t-8.85))
        var infl=0.0
        if t>=1.5 && t<4.27 {infl=spring(t-1.5,0,1,0,19,195)}
        else if t>=4.27 && t<4.52 {infl=1-pow(seg(t,4.27,4.52),3)}
        else if t>=4.7 && t<6.85 {infl=spring(t-4.7,0,1,0,19,195)}
        else if t>=6.85 && t<8.15 {infl=GearPuckSpeedLineMath.smooth(0,30,f.speed)}
        f.amplitude=infl*(96+0.12*abs(f.speed));f.crest=crest(f.speed)
        for (land,from,to) in [(1.36,201.0,crest(0)),(4.68,267.0,crest(132))] {
            let duration=max(abs(to-from)/900,0.06),k=(t-land)/duration
            if k>=0 && k<1.25 {f.packetX=from+(to-from)*min(k,1);f.packetGain=k<1 ? 1:1-(k-1)/0.25}
        }
        let home=6.85+(8.15-6.85)*0.6
        if t>=1.6 && t<home {f.numK=spring(t-1.6,0,1,0,22.1,157.9)}
        else if t>=home {f.numK=spring(t-home,1,0,0,22.1,157.9)}
        if t>=4.25 && t<4.74 {f.drop=easeOut(seg(t,4.25,4.4))}
        else if t>=4.74 && t<6.85 {f.drop=max(spring(t-4.74,1,0,0,22.1,157.9),0)}
        let hold=GearPuckSpeedLineMath.smooth(6.38,6.46,t)*(1-GearPuckSpeedLineMath.smooth(6.77,6.85,t))
        f.jitter=hold*sin(2*Double.pi*24*t)
        let phase=t*200,i=min(Int(phase),1999)
        f.phase=GearPuckSpeedLineMath.mix(phases[i],phases[i+1],phase-Double(i))
        f.curtain=f.speed*(0.55+0.45*min(infl,1));f.whip=10*GearPuckSpeedLineMath.smooth(180,190,f.speed)
        f.sway=3*sin(2*Double.pi*4*t/10)
        var last = -1
        for (i,c) in crossings.enumerated() {if c.t<=t {last=i}}
        func crossing(_ i:Int)->Crossing {let c=crossings[(i%crossings.count+crossings.count)%crossings.count];return Crossing(t:i<0 ? c.t-10:c.t,from:c.from,to:c.to,x:c.x)}
        let l=crossing(last),p=crossing(last-1)
        f.color0=Double(p.from);f.color1=Double(p.to);f.color2=Double(l.to)
        f.frontPX=p.x;f.frontPR=700*(t-p.t);f.frontLX=l.x;f.frontLR=700*(t-l.t)
        return f
    }
}

enum GearPuckSpeedLineMath {
    static func mix(_ a:Double,_ b:Double,_ k:Double)->Double { a+(b-a)*k }
    static func smooth(_ a:Double,_ b:Double,_ x:Double)->Double { let k=((x-a)/(b-a)).clamped(0,1);return k*k*(3-2*k) }
    static func spring(_ t:Double,_ from:Double,_ to:Double,_ velocity:Double,_ damping:Double,_ stiffness:Double)->Double {
        guard t>0 else{return from};let w=sqrt(stiffness),z=damping/(2*w),x=from-to
        if z<1 {let wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/wd*sin(wd*t))}
        return to+exp(-w*t)*(x+(velocity+w*x)*t)
    }
    static func step(_ x:Double,_ v:inout Double,_ target:Double,_ dt:Double,_ d:Double=24,_ k:Double=420)->Double {
        var p=x;let h=dt/4
        for _ in 0..<4 {v += (-k*(p-target)-d*v)*h;p += v*h};return p
    }
}

final class GearPuckSpeedLineEngine {
    var automatic = true
    var shown = GearPuckSpeedLineScore.frame(0)
    var target = 0
    var held = false
    var mode = 0
    private var origin = 0.0, prior = 0.0, lastTouch = 0.0
    private var gearV = 0.0, infl = 0.0, inflV = 0.0, numV = 0.0, dropV = 0.0, speedV = 0.0
    private var gearStart = 0.0, gapTime = -99.0, flashTime = -99.0, pluckTime = -99.0, pluckFrom = 0.0
    private var returning = false
    private var phaseOffset = 0.0
    func frame(now: Double, autoPlay: Bool, reduced: Bool, pin: Double?, manualPin: Bool) -> GearPuckSpeedLineFrame {
        if origin == 0 {origin=now;prior=now;automatic=autoPlay && !reduced && !manualPin}
        let dt=(now-prior).clamped(0,0.05);prior=now
        if automatic {shown=GearPuckSpeedLineScore.frame(pin ?? (now-origin));shown.phase += phaseOffset;target=Int(shown.g.rounded()).clamped(0,4);return shown}
        let oldSpeed=shown.speed
        if !held || mode != 1 {shown.g=GearPuckSpeedLineMath.step(shown.g,&gearV,Double(target),dt)}
        if reduced && (!held || mode != 1) {shown.g=Double(target);gearV=0}
        let cap=[0.0,0,132,197,197][target]
        if !held || mode != 2 {
            shown.speed=max(0,min(cap,shown.speed+speedV*dt-(target<2 ? 60:12)*dt))
            speedV *= exp(-dt*2)
        }
        shown.rate=dt>0 ? (shown.speed-oldSpeed)/dt:0
        shown.lean += (shown.rate/70-shown.lean)*(1-exp(-dt/0.15));shown.lean=shown.lean.clamped(-1,1)
        shown.press += ((held && mode == 1 ? 1.0:0)-shown.press)*(1-exp(-dt*16))
        let gap=now-gapTime, inflTarget=(target>=2 ? 1.0:GearPuckSpeedLineMath.smooth(0,30,shown.speed))*(gap<0.25 ? 0:1)
        infl=GearPuckSpeedLineMath.step(infl,&inflV,reduced ? 0:inflTarget,dt,19,195)
        shown.amplitude=max(0,infl)*(96+0.12*shown.speed);shown.crest=56+270*min(shown.speed,200)/200;shown.jitter=0
        shown.numK=GearPuckSpeedLineMath.step(shown.numK,&numV,reduced ? 0:(inflTarget>0.5 || shown.speed>50 ? 1:0),dt,22.1,157.9)
        shown.drop=GearPuckSpeedLineMath.step(shown.drop,&dropV,!reduced && gap<0.42 && shown.numK>0.5 ? 1:0,dt,22.1,157.9).clamped(0,1)
        if !reduced {shown.phase += (6+1.2*shown.speed)*dt}
        shown.curtain=shown.speed*(0.55+0.45*min(infl,1));shown.whip=10*GearPuckSpeedLineMath.smooth(180,190,shown.speed)
        shown.time=now-origin;shown.sway=reduced ? 0:3*sin(2*Double.pi*4*shown.time/10)
        shown.frontPR += 700*dt;shown.frontLR += 700*dt
        let band=shown.speed<25 ? 0:shown.speed<120 ? 1:shown.speed<165 ? 2:3
        if Int(shown.color2) != band {shown.color0=shown.color1;shown.color1=shown.color2;shown.color2=Double(band);shown.frontPX=shown.frontLX;shown.frontPR=shown.frontLR;shown.frontLX=shown.crest;shown.frontLR=reduced ? 1000:0}
        if !held || mode != 3 {shown.pluck=reduced ? 0:GearPuckSpeedLineMath.spring(now-pluckTime,pluckFrom,0,0,11.5,1020)}
        shown.pluckLag=shown.pluck*0.7;shown.packetGain=0
        shown.flash=max(0,1-(now-flashTime)/0.24)
        if autoPlay && !reduced && !manualPin && pin == nil && !held && now-lastTouch>2 {
            returning=true;target=0
            if shown.speed<0.3 && shown.amplitude<0.1 && abs(shown.g)<0.01 && abs(shown.numK)<0.01 {automatic=true;origin=now;returning=false;phaseOffset=shown.phase;shown=GearPuckSpeedLineScore.frame(0);shown.phase=phaseOffset}
        }
        return shown
    }
    func begin(x: Double,y: Double,now: Double,reduced: Bool) {
        if automatic {infl=shown.amplitude/(96+0.12*shown.speed);automatic=false}
        returning=false;held=true;lastTouch=now;gearStart=shown.g
        let bx=69+66*shown.g
        if pow(x-bx,2)+pow(y-664,2)<56*56 {mode=1}
        else if y>612 && y<716 {mode=4}
        else if target>=2 && abs(x-shown.crest)<110 && y>shown.curve(shown.crest)-40 && y<400 {mode=2;speedV=0}
        else if abs(y-330)<90 && !reduced {mode=3}
        else {mode=0}
    }
    func move(x:Double,y:Double,dx:Double,dy:Double) {
        if mode==1 {
            var r=gearStart+dx/66
            if r<0 {r=r/(1-r*3)};if r>4 {r=4+(r-4)/(1+(r-4)*3)}
            let well=r.clamped(0,4).rounded();shown.g=r+0.22*(well-r)*(1-GearPuckSpeedLineMath.smooth(0.18,0.5,abs(r-well)))
        } else if mode==2 {shown.speed=((x-56)/270*200).clamped(0,[0.0,0,132,197,197][target])}
        else if mode==3 {shown.pluckX=x;shown.pluck=(dy<0 ? -1.0:1)*40*abs(dy)/(abs(dy)+40)}
    }
    func end(x:Double,dx:Double,dy:Double,velocity:Double,now:Double,cancelled:Bool) {
        var to=target
        if mode==1 {to=Int((shown.g+(cancelled ? 0:velocity/66*0.12)).rounded()).clamped(0,4);gearV=cancelled ? 0:velocity/66}
        if !cancelled && hypot(dx,dy)<4 && (mode==1 || mode==4) {
            to=Int(((x-69)/66).rounded()).clamped(0,4)
            if mode==1 && (target==2 || target==3) {to=target==2 ? 3:2}
        }
        select(to,now:now)
        if cancelled {flashTime = -99}
        if mode==2 {speedV=cancelled ? 0:velocity*200*0.69/270}
        if mode==3 {pluckTime=now;pluckFrom=shown.pluck}
        held=false;mode=0;lastTouch=now
    }
    func select(_ gear:Int,now:Double) {
        if automatic {infl=shown.amplitude/(96+0.12*shown.speed);automatic=false}
        if (target==2 && gear>=3)||(target>=3 && gear==2) {gapTime=now}
        target=gear.clamped(0,4);flashTime=now;lastTouch=now;returning=false
    }
}

private extension Double {func clamped(_ a:Double,_ b:Double)->Double {Swift.min(Swift.max(self,a),b)}}
private extension Int {func clamped(_ a:Int,_ b:Int)->Int {Swift.min(Swift.max(self,a),b)}}
