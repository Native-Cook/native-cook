import Foundation

/// Live interaction is sampled independently of rendering; touch-down can grab any in-flight frame exactly.
final class LouvreVentThermostatEngine {
    private typealias M=LouvreVentThermostatMotion
    struct Channel {
        var value:Double, velocity=0.0
        private var from=0.0, target=0.0, initialVelocity=0.0, start=0.0, damping=0.0, stiffness=0.0, duration=0.0
        private(set) var busy=false
        mutating func set(_ x:Double) { value=x; velocity=0; busy=false }
        mutating func spring(to:Double,velocity:Double=0,at:Double,damping:Double,stiffness:Double,reduced:Bool) {
            if reduced { set(to); return }
            from=value; target=to; initialVelocity=(busy ? self.velocity : 0)+velocity
            if (to>value && initialVelocity<0)||(to<value && initialVelocity>0) { initialVelocity=0 }
            start=at; self.damping=damping; self.stiffness=stiffness; duration=0; busy=true
        }
        mutating func timing(to:Double,at:Double,duration:Double,reduced:Bool) { if reduced { set(to); return }; from=value; target=to; start=at; self.duration=duration; busy=true }
        mutating func advance(_ now:Double) {
            guard busy else { return }; let age=now-start
            if duration>0 { let k=M.seg(age,0,duration); value=M.mix(from,target,M.inOut(k)); velocity=0; if k>=1 { set(target) }; return }
            value=M.spring(age,from,target,initialVelocity,damping,stiffness)
            velocity=(M.spring(age+0.00001,from,target,initialVelocity,damping,stiffness)-value)/0.00001
            let initial=0.5*stiffness*pow(from-target,2)+2*initialVelocity*initialVelocity
            let energy=0.5*stiffness*pow(value-target,2)+2*velocity*velocity
            if initial==0 || energy/max(initial,0.000000001)<6e-9 { set(target) }
        }
    }
    var autoPlay=true, reduced=false, manual=false
    var pin:Double?
    var onHaptic:((Int)->Void)?, onChange:((Double)->Void)?, onCommit:((Double)->Void)?
    private(set) var scene=M.scene(0)
    private var auto=true, touching=false, armed=false, homing=false, atLimit=false
    private var now=0.0, time=0.0, lastTouch = -10.0, lastTick = -10.0, wholeAt = -10.0
    private var val=Channel(value:24), pressure=Channel(value:0), slam=Channel(value:0)
    private var previous=24.0, speed=0.0, heat=24.0, conv=0.0, convOffset=0.0
    private var target=24.0, direction=1.0, clackStart = -10.0, clackDirection=1.0
    private var start=24.0, thumb=488.0, startY=488.0, lastBlade=16.0, lastWhole=24.0
    private var history:[(Double,Double,Double)]=[(0,24,0)], crossings:[(Double,Double)]=[]
    init() {
        #if DEBUG
        manual=UserDefaults.standard.bool(forKey:"louvreVentThermostatManual")
        if let raw=UserDefaults.standard.object(forKey:"louvreVentThermostatTime"),let t=Double("\(raw)") { pin=t; time=t; scene=M.scene(t) }
        #endif
    }
    func configure(autoPlay:Bool,reduced:Bool) {
        if self.autoPlay != autoPlay || self.reduced != reduced { auto=autoPlay && !reduced && !manual }
        self.autoPlay=autoPlay; self.reduced=reduced
        if manual { auto=false }
        if reduced && auto { auto=false; val.set(24); scene=M.scene(0) }
        if let pin { scene=M.scene(pin) }
    }
    func advance(_ dt:Double) {
        now+=dt; guard pin==nil else { return }
        if auto && autoPlay && !manual && !reduced { time=M.wrap(time+dt); scene=M.scene(time); scene.conv+=convOffset; return }
        val.advance(now); pressure.advance(now); slam.advance(now)
        let t=val.value, raw=(t-previous)/max(dt,0.00001)*M.perC; previous=t
        speed += (raw-speed)*(1-exp(-dt/0.06))
        history.append((now,t,speed)); if history.count>48 { history.removeFirst() }
        heat += (t-heat)*(1-exp(-dt/(reduced ? 0.07 : 0.25)))
        if !reduced { conv+=M.convectionRate(heat)*dt }
        let b=floor(2*(t-16))
        if b != lastBlade {
            for i in 0..<min(Int(abs(b-lastBlade)),6) { let level=b>lastBlade ? lastBlade+1+Double(i) : lastBlade-Double(i); crossings.append((16+level/2,now)) }
            crossings=Array(crossings.suffix(6)); lastBlade=b
            let whole=floor(t), isWhole=whole != lastWhole
            if isWhole { lastWhole=whole; wholeAt=now }
            if now-lastTick>=0.1 || (isWhole && now-lastTick>=0.05) { lastTick=now; onHaptic?(0) }
            onChange?(M.rounded(2*t)/2)
        }
        if !touching && armed && abs(speed)<40 && abs(t-target)<0.02 {
            armed=false
            if !reduced { clackStart=now; clackDirection=direction; onHaptic?(2) }
            onCommit?(target)
        }
        if autoPlay && !manual && !reduced && !touching && !armed && now-lastTouch>2 && now-clackStart>0.6 {
            if abs(t-24)>0.002 && !homing { homing=true; target=24; direction=t<24 ? 1 : -1; armed=true; val.timing(to:24,at:now,duration:0.9,reduced:false) }
            else if abs(t-24)<=0.002 { convOffset=conv-M.conv(0); time=0; homing=false; auto=true; val.set(24); scene=M.scene(0); scene.conv+=convOffset; return }
        }
        let row=M.row(t,thetaAt:{ dk in
            if self.reduced { return M.theta(t,0) }
            let sample=self.sample(self.now-0.016*dk); return M.theta(sample.0,sample.1)
        },glintAt:{ level in
            if self.reduced { return 0 }
            return self.crossings.filter { abs($0.0-level)<0.01 }.map { M.clamp(1-(self.now-$0.1)/0.08,0,1) }.max() ?? 0
        },thumb:thumb,pressure:reduced ? 0 : pressure.value,clackAge:now-clackStart,clackDirection:clackDirection,slam:reduced ? 0 : slam.value)
        var s=M.Scene(); s.temperature=t; s.velocity=speed; s.s0=row.0; s.k0=row.1; s.blades=row.2; s.heat=heat; s.conv=conv; s.time=now; s.drums=M.drums(t)
        let dw=now-wholeAt; s.nudge=dw<0.15 ? 2*pow(1-dw/0.15,2)*(1-M.smooth(150,420,abs(speed))) : 0; scene=s
    }
    private func sample(_ t:Double)->(Double,Double) {
        for i in history.indices.reversed() where history[i].0<=t {
            if i==history.count-1 { return (history[i].1,history[i].2) }
            let k=(t-history[i].0)/max(history[i+1].0-history[i].0,0.0001)
            return (M.mix(history[i].1,history[i+1].1,k),M.mix(history[i].2,history[i+1].2,k))
        }
        return (history[0].1,history[0].2)
    }
    func begin(y:Double) {
        if auto || pin != nil {
            let s=scene; val.set(s.temperature); previous=s.temperature; speed=s.velocity; heat=s.heat; conv=s.conv
            history=(0..<16).map { let back=Double(15-$0)*0.02; return (now-back,M.temperature(time-back),M.velocity(time-back)) }
            let cl=M.clack(M.wrap(time)); clackStart=now-cl.0; clackDirection=cl.1
            lastBlade=floor(2*(s.temperature-16)); lastWhole=floor(s.temperature); crossings=[]; auto=false
        }
        pin=nil; homing=false; touching=true; lastTouch=now; val.set(val.value); armed=false
        thumb=y; startY=y; start=val.value
        pressure.timing(to:1,at:now,duration:0.11,reduced:reduced); if !reduced { onHaptic?(1) }
    }
    func drag(translation:Double) {
        var t=start+translation/M.perC
        if t>32 || t<16 {
            let lim=t>32 ? 32.0 : 16, over=abs(t-lim)*M.perC
            t=lim+(t>lim ? 1 : -1)*(over/(1+over/120))/M.perC
            if !atLimit { atLimit=true; slam.spring(to:lim==32 ? 1 : -1,at:now,damping:18,stiffness:520,reduced:reduced); onHaptic?(2) }
        } else if atLimit { atLimit=false; slam.spring(to:0,at:now,damping:30.5,stiffness:322.3,reduced:reduced) }
        val.set(t); thumb=startY+translation
    }
    private func glide(_ destination:Double,_ velocity:Double) { target=destination; direction=target>=val.value ? 1 : -1; armed=true; val.spring(to:target,velocity:velocity,at:now,damping:26,stiffness:120,reduced:reduced) }
    func end(velocity:Double,cancelled:Bool=false) {
        let v=cancelled ? 0 : velocity/M.perC
        glide(M.clamp(M.rounded(2*(val.value+v*0.332))/2,16,32),v)
        if atLimit { atLimit=false; slam.spring(to:0,at:now,damping:30.5,stiffness:322.3,reduced:reduced) }
        pressure.timing(to:0,at:now,duration:0.09,reduced:reduced); touching=false; lastTouch=now
    }
    func tap(x:Double,y:Double) {
        guard x>327 && x<381 && y>246 && y<730 else { end(velocity:0); return }
        let at=val.value+(488-y)/M.perC; target=M.clamp(2*M.rounded(at/2),16,32); direction=target>=val.value ? 1 : -1; armed=true; touching=false; lastTouch=now
        val.timing(to:target,at:now,duration:0.62,reduced:reduced); pressure.timing(to:0,at:now,duration:0.09,reduced:reduced)
    }
    func adjust(_ delta:Double) { begin(y:488); glide(M.clamp(M.rounded(2*val.value)/2+delta,16,32),0); touching=false; lastTouch=now; pressure.set(0) }
}
