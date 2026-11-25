import Foundation

// Option A: lateral a (1 at rest, 0 on the socket axis), depth z (1 out, 0 seated).
enum BackOnlinePlugMotion {
    static let period = 7.0 / 3
    static let restDX = -64.0, restDY = 92.0, restDistance = hypot(restDX, restDY)
    static let offline = [46.0, 59, 52], online = [31.0, 107, 64], flash = [44.0, 138, 82]
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t-a)/(b-a)) }
    static func out(_ x: Double) -> Double { 1-pow(1-x, 3) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    static func mod(_ x: Double, _ m: Double) -> Double { (x.truncatingRemainder(dividingBy: m)+m).truncatingRemainder(dividingBy: m) }
    static func mix(_ a: [Double], _ b: [Double], _ u: Double) -> [Double] { zip(a,b).map { $0+($1-$0)*u } }
    static func bounce(_ u: Double) -> Double { u < 0 || u >= 0.35 ? 0 : -0.03*exp(-12*u)*sin(30*u) }
    static func lateral(_ t: Double) -> Double {
        if t >= 1 && t < 1.6 { return 1-ease(seg(t,1,1.6)) }
        if t >= 1.6 && t < 5.3 { return 0 }
        if t >= 5.3 && t < 5.9 { return ease(seg(t,5.3,5.9)) }
        return 1
    }
    static func depth(_ t: Double) -> Double {
        if t >= 1.55 && t < 1.85 { return 1-pow(seg(t,1.55,1.85),2) }
        if t >= 1.85 && t < 4.8 { return bounce(t-1.85) }
        if t >= 4.8 && t < 5.3 { return out(seg(t,4.8,5.3)) }
        return 1
    }
    static func wall(_ t: Double) -> [Double] {
        if t >= 1.85 && t < 5 {
            let a = seg(t,1.85,1.97)
            return a < 1 ? mix(offline,flash,a) : mix(flash,online,ease(seg(t,1.97,2.6)))
        }
        return t >= 5 ? mix(online,offline,ease(seg(t,5,5.6))) : offline
    }
    static func flip(_ t: Double) -> Double {
        if t >= 1.9 && t < 5.1 { return seg(t,1.9,2.3) }
        return t >= 5.1 ? 1-seg(t,5.1,5.5) : 0
    }
    static func chip(_ t: Double) -> Int { min(3,1+Int(floor(seg(t,2.3,4.6)*3))) }
    struct Frame {
        var a = 1.0, z = 1.0, sway = 0.0, wall = offline, flip = 0.0
        var lineOnline = false, chip = 1, spark = 0.0
        static let off = Frame()
        static let on = Frame(a: 0, z: 0, wall: online, flip: 1, lineOnline: true, chip: 3)
    }
    static func frame(_ t: Double, _ c: Double, _ start: Double, _ a: Double? = nil, _ z: Double? = nil, _ env: Double = 1) -> Frame {
        let a = a ?? lateral(t), z = z ?? depth(t), f = flip(t)
        return Frame(a:a,z:z,sway:z <= 0.5 ? 0 : sin(2 * .pi*(c-start)/period)*a*env,
                     wall:wall(t),flip:f,lineOnline:f >= 0.5,chip:chip(t),spark:seg(t,1.82,2.05))
    }
    struct Touch {
        var manual = false, touching = false
        var loopStart = 0.0, resumeAt = -1.0, lastTouch = -100.0
        // Plug: still, drag, seat, unseat, return. Screen: offline, seat, unseat.
        var pk = 0, pFrom = 1.0, pAt = 0.0, dragDist = 0.0
        var vk = 0, vAt = 0.0, chip = 1, env = 1.0
        func seatTimes(_ from: Double) -> (Double,Double) { let push = max(0,0.6*from-0.05); return (push,push+0.3) }
        func plug(_ c: Double) -> (Double,Double) {
            switch pk {
            case 1: return (clamp(pFrom-dragDist/restDistance),1)
            case 2:
                let u = c-pAt, align = 0.6*pFrom, times = seatTimes(pFrom)
                let a = align > 0 ? pFrom*(1-ease(clamp(u/align))) : 0
                return (a,u < times.1 ? 1-pow(seg(u,times.0,times.1),2) : bounce(u-times.1))
            case 3: return (ease(seg(c-pAt,0.5,1.1)),out(seg(c-pAt,0,0.5)))
            case 4: return (pFrom+(1-pFrom)*out(seg(c,pAt,pAt+0.35)),1)
            default: return (pFrom,vk == 1 ? 0 : 1)
            }
        }
        func beat(_ c: Double) -> Double { vk == 1 ? min(1.85+(c-vAt),4.79) : vk == 2 ? min(4.8+(c-vAt),5.9) : 0 }
        func frame(_ c: Double) -> Frame {
            if !manual { return BackOnlinePlugMotion.frame(mod(c-loopStart,7),c,loopStart) }
            let p = plug(c)
            var f = BackOnlinePlugMotion.frame(beat(c),c,loopStart,p.0,p.1,env)
            f.chip = vk == 2 ? chip : f.chip; f.spark = vk == 1 ? f.spark : 0
            return f
        }
        func online(_ c: Double) -> Bool {
            if manual { return vk == 1 && c >= vAt }
            let t = mod(c-loopStart,7); return t >= 1.85 && t < 4.8
        }
        mutating func takeOver(_ c: Double) {
            resumeAt = -1; lastTouch = c
            guard !manual else { return }
            manual = true; let t = mod(c-loopStart,7)
            pk = 0; pFrom = 1; vk = 0
            if t >= 1 && t < 4.8 {
                pk = 2; pAt = c-(t-1); vk = 1; vAt = c-(t-1.85)
            } else if t >= 4.8 && t < 5.9 {
                pk = 3; pAt = c-(t-4.8); vk = 2; vAt = pAt; chip = 3
            }
        }
        mutating func seat(_ c: Double) {
            let from = plug(c).0
            pk = 2; pFrom = from; pAt = c; vk = 1; vAt = c+seatTimes(from).1
        }
        mutating func unseat(_ c: Double) { chip = BackOnlinePlugMotion.chip(beat(c)); pk = 3; pAt = c; vk = 2; vAt = c }
        func canGrab(_ c: Double) -> Bool { plug(c).1 >= 1 && !(vk == 1 && c >= vAt) }
        mutating func grab(_ c: Double) {
            pFrom = plug(c).0; pk = 1; dragDist = 0
            if vk == 1 { vk = 0 }
        }
        mutating func drag(_ dx: Double, _ dy: Double, _ c: Double) { dragDist = -(dx*restDX+dy*restDY)/restDistance; lastTouch = c }
        mutating func release(_ c: Double, tap: Bool) {
            let a = plug(c).0
            if tap || a*restDistance <= 40 { seat(c); return }
            pk = 4; pFrom = a; pAt = c
        }
        mutating func toggle(_ c: Double) {
            if vk == 1 { if c >= vAt { unseat(c) }; return }
            if pk == 3 && plug(c).1 < 1 { return }
            seat(c)
        }
        func settled(_ c: Double) -> Bool {
            if pk == 1 || (pk == 2 && c < vAt+0.8) { return false }
            if pk == 3 && c < pAt+1.1 { return false }
            if pk == 4 && c < pAt+0.35 { return false }
            return vk != 2 || c >= vAt+1.1
        }
        mutating func step(_ c: Double, dt: Double, autoPlay: Bool) {
            guard manual else { return }
            let target = touching && pk == 1 ? 0.0 : 1.0
            if env != target { env = abs(target-env) < 0.002 ? target : env+(target-env)*(1-exp(-dt/0.12)) }
            guard autoPlay && !touching && c-lastTouch >= 2 && settled(c) else { return }
            if vk == 1 { unseat(c); return }
            if plug(c).0 < 1 { pFrom = plug(c).0; pk = 4; pAt = c; return }
            if resumeAt < 0 { resumeAt = loopStart+ceil((c-loopStart)/period)*period; return }
            if c >= resumeAt && env == 1 { let start = resumeAt; self = Touch(); loopStart = start }
        }
    }
}
