import Foundation

enum LouvreVentThermostatMotion {
    static let loop=9.0, pitch=20.8, perC=41.6
    static func wrap(_ t:Double)->Double { (t.truncatingRemainder(dividingBy:loop)+loop).truncatingRemainder(dividingBy:loop) }
    static func clamp(_ v:Double,_ a:Double,_ b:Double)->Double { min(max(v,a),b) }
    static func seg(_ t:Double,_ a:Double,_ b:Double)->Double { clamp((t-a)/(b-a),0,1) }
    static func mix(_ a:Double,_ b:Double,_ k:Double)->Double { a+(b-a)*k }
    static func smooth(_ a:Double,_ b:Double,_ v:Double)->Double { let k=seg(v,a,b); return k*k*(3-2*k) }
    static func out(_ k:Double)->Double { 1-pow(1-k,3) }
    static func inOut(_ k:Double)->Double { k<0.5 ? 4*k*k*k : 1-pow(-2*k+2,3)/2 }
    static func rounded(_ v:Double)->Double { floor(v+0.5) }
    static func temperature(_ input:Double)->Double {
        let t=wrap(input)
        if t<0.62 { return 24 }
        if t<2.1 { return 24+6*inOut(seg(t,0.62,2.1)) }
        if t<3.2 { return 30 }
        if t<4.5 { return 30-12.5*inOut(seg(t,3.2,4.5)) }
        if t<5.7 { return 17.5 }
        if t<6.9 { let x=6*(t-5.7); return 17.5+6.5*(1-exp(-x)*(1+x))/(1-exp(-7.2)*8.2) }
        return 24
    }
    static func velocity(_ t:Double)->Double { (temperature(t+0.004)-temperature(t-0.004))/0.008*perC }
    static func heat(_ t:Double)->Double { (0..<8).reduce(0) { $0+temperature(t-0.06*Double($1)) }/8 }
    static func theta(_ t:Double,_ v:Double)->Double { clamp(4+52*(clamp(t,16,32)-16)/16+22*tanh(v/260),-68,68) }
    static func shut(_ th:Double,_ a:Double)->Double { (th<0 ? -1 : 1)*max(abs(th)-a,0) }
    static func clackDip(_ tau:Double)->Double { tau<=0 || tau>0.7 ? 0 : 35.25*exp(-18*tau)*sin(24*tau) }
    static func clackDelay(_ y:Double,_ dir:Double)->Double { 0.012*clamp((dir>0 ? y-254 : 722-y)/pitch,0,23) }
    static func press(_ y:Double,_ thumb:Double,_ pressure:Double)->Double { let d=(y-thumb)/pitch; return 12*pressure*exp(-d*d/4.5) }
    static func clack(_ t:Double)->(Double,Double) {
        var age=9.0, dir=1.0
        for (start,d) in [(2.1,1.0),(4.5,-1.0),(6.9,1.0)] where t>=start { age=t-start; dir=d }
        return (age,dir)
    }
    static func touch(_ t:Double)->(Double,Double) {
        if t>=0.5 && t<2.2 { return (430+(temperature(t)-24)*perC,t<0.62 ? out(seg(t,0.5,0.62)) : 1-seg(t,2.1,2.18)) }
        if t>=3.08 && t<4.6 { let y=690+(temperature(t)-30)*perC; return (y,(t<3.2 ? out(seg(t,3.08,3.2)) : 1-seg(t,4.5,4.58))*smooth(250,320,y)) }
        if t>=5.58 && t<6 { return (400+(temperature(t)-17.5)*perC,t<5.7 ? out(seg(t,5.58,5.7)) : 1-seg(t,5.84,5.94)) }
        return (488,0)
    }
    static func crossing(_ level:Double,_ a:Double,_ b:Double)->Double {
        var lo=a, hi=b; let up=temperature(b)>temperature(a)
        for _ in 0..<14 { let m=(lo+hi)/2, v=temperature(m); if up ? v>=level : v<=level { hi=m } else { lo=m } }
        return hi
    }
    static func glint(_ level:Double,_ t:Double)->Double {
        let t1=temperature(t), t0=temperature(t-0.08)
        guard t1 != t0, t1>t0 ? level>t0 && level<=t1 : level<t0 && level>=t1 else { return 0 }
        return clamp(1-(t-crossing(level,t-0.08,t))/0.08,0,1)
    }
    static func nudge(_ t:Double)->Double {
        var n=0.0
        for a in [0.5,3.08,5.58] { n=max(n,2*out(seg(t,a,a+0.12))*(1-inOut(seg(t,a+0.12,a+0.32)))) }
        let t1=temperature(t), t0=temperature(t-0.15)
        if t1 != t0 {
            let level=t1>t0 ? floor(t1) : ceil(t1)
            if t1>t0 ? level>t0 : level<t0 { let tc=crossing(level,t-0.15,t), k=1-(t-tc)/0.15; n=max(n,2*k*k*(1-smooth(150,420,abs(velocity(tc))))) }
        }
        return n
    }
    static func convectionRate(_ h:Double)->Double { h>=24 ? mix(60,220,clamp((h-24)/6,0,1))/90 : mix(60,-80,clamp((24-h)/6.5,0,1))/90 }
    static let convection:[Double] = {
        var values=[0.0], sum=0.0
        for i in 0..<450 { sum+=convectionRate(heat((Double(i)+0.5)*loop/450))*loop/450; values.append(sum) }
        let whole=max(1,rounded(sum)); return values.map { $0*whole/sum }
    }()
    static func conv(_ t:Double)->Double { let u=wrap(t)/loop*450, i=min(Int(floor(u)),449); return mix(convection[i],convection[i+1],u-Double(i)) }
    static func drums(_ t:Double)->[Double] {
        let q=2*(t-16), n=floor(q), d=n+smooth(0.4,0.6,q-n), m=floor(d)
        let units=16+floor(m/2)+(Int(m)%2 != 0 ? d-m : 0)
        let tens=floor(units/10)+(Int(floor(units))%10==9 ? units-floor(units) : 0)
        return [d-2*floor(d/2),units,tens]
    }
    struct Scene {
        var temperature=24.0, velocity=0.0, s0=16.0, k0=3.0
        var blades=[Double](repeating:0,count:52)
        var heat=24.0, conv=0.0, nudge=0.0, breath=0.0, time=0.0
        var drums=[0.0,24.0,2.0]
    }
    static func row(_ t:Double,thetaAt:(Double)->Double,glintAt:(Double)->Double,thumb:Double,pressure:Double,clackAge:Double,clackDirection:Double,slam:Double)->(Double,Double,[Double]) {
        let s0=2*(t-16), k0=floor(s0-(722-488)/pitch)-1
        var blades=[Double]()
        for i in 0..<26 {
            let k=k0+Double(i), y=488-(k-s0)*pitch
            var th=thetaAt(abs(k-s0))
            if slam>0 { th=mix(th,68,slam) } else if slam<0 { th=mix(th,0,-slam) }
            th=shut(th,press(y,thumb,pressure))
            let tau=clackAge-clackDelay(y,clackDirection)
            th=shut(th,clackDip(tau))
            let cg=tau>0 && tau<0.4 ? 0.9*exp(-tau/0.06) : 0
            blades.append(contentsOf:[th,max(cg,glintAt(16+k/2))])
        }
        return (s0,k0,blades)
    }
    static func scene(_ input:Double)->Scene {
        let t=wrap(input), temp=temperature(t), thumb=touch(t), cl=clack(t)
        let row=row(temp,thetaAt:{ let td=t-0.016*$0; return theta(temperature(td),velocity(td)) },glintAt:{ glint($0,t) },thumb:thumb.0,pressure:thumb.1,clackAge:cl.0,clackDirection:cl.1,slam:0)
        var s=Scene(); s.temperature=temp; s.velocity=velocity(t); s.s0=row.0; s.k0=row.1; s.blades=row.2
        s.heat=heat(t); s.conv=conv(t); s.nudge=nudge(t); s.time=t; s.drums=drums(temp)
        s.breath=3*sin(2*Double.pi*3*t/loop)+(t>=2.5 && t<3.2 ? 8*sin(2*Double.pi*seg(t,2.5,3.2)) : 0)
        return s
    }
    /// Closed-form spring; RN's live withSpring defaults to mass four, unlike the scripted helpers.
    static func spring(_ t:Double,_ from:Double,_ to:Double,_ velocity:Double,_ damping:Double,_ stiffness:Double,mass:Double=4)->Double {
        let w=sqrt(stiffness/mass), z=damping/(2*sqrt(stiffness*mass)), x=from-to
        if z<1 { let wd=w*sqrt(1-z*z); return to+exp(-z*w*t)*(x*cos(wd*t)+(velocity+z*w*x)/wd*sin(wd*t)) }
        let ss=sqrt(max(0,z*z-1)), r1 = -w*(z-ss), r2 = -w*(z+ss)
        if abs(r1-r2)<0.000001 { return to+exp(-w*t)*(x+(velocity+w*x)*t) }
        let c2=(velocity-r1*x)/(r2-r1); return to+(x-c2)*exp(r1*t)+c2*exp(r2*t)
    }
}
