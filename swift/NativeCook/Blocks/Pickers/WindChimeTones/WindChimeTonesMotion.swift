import Foundation
import CoreGraphics

/// Direct translation of wct-motion.ts. Time is seconds, distance is screen points.
enum WindChimeTonesMotion {
    static let loop = 10.0
    static let piece = 8.0
    static let speed = 420.0
    static let play = 0.7, start = 0.8, pause = 8.6
    static let notes: [(Double, Double, Double)] = [
        (0.180, 0.58, 587), (0.730, 0.36, 880), (1.090, 0.43, 740),
        (1.820, 0.65, 659), (2.160, 0.32, 988), (2.910, 0.40, 1175),
        (3.370, 0.53, 880), (3.660, 0.30, 740), (4.420, 0.72, 587),
        (5.070, 0.39, 988), (5.480, 0.46, 659), (6.230, 0.61, 880),
        (6.890, 0.34, 1175), (7.570, 0.49, 740)
    ]
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t-a)/(b-a))) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1-x, 3) }
    static func timing(_ x: Double) -> Double { x < 0.5 ? 2*x*x : 1-pow(-2*x+2, 2)/2 }
    struct Body {
        var d, ux, uy, half, mass: Double
    }
    struct Pose {
        var x = 0.0, y = 0.0, rotation = 0.0, scaleX = 1.0, scaleY = 1.0
    }
    struct Frame {
        var playing = false
        var music = -1.0
        var cut = piece
        var laps = 1
        var finger = 0.0
        var press = 0.0
        var power = 1.0
        var glow = 0.0
        var time = 0.0
        var kick: Double { music >= 0 ? pushAt(music, cut, laps) : 0 }
        var progress: Double { music < 0 ? 0.52 : min(1, 0.52 + ((music.truncatingRemainder(dividingBy: piece)+piece).truncatingRemainder(dividingBy: piece))/piece*0.3) }
        func ride(_ body: Body, _ reduced: Bool) -> Pose {
            guard !reduced, music >= 0, power >= 0.001 else { return Pose() }
            let k = kickAt(music, body.d, cut, laps, body.mass)
            let near = kickAt(music, body.d-body.half*abs(body.ux), cut, laps, body.mass).x
            let far = kickAt(music, body.d+body.half*abs(body.ux), cut, laps, body.mass).x
            let sign = body.ux > 0 ? 1.0 : body.ux < 0 ? -1.0 : 0.0
            let rock = body.uy*(far-near)*sign/(2*body.half)
            let stretch = 1+min(0.035,max(-0.035,k.v/3000*power))
            return Pose(x:k.x*body.ux*power,y:k.x*body.uy*power,rotation:rock*power,
                        scaleX:1/sqrt(stretch),scaleY:stretch)
        }
    }
    static func frameAt(_ t: Double) -> Frame {
        func tap(_ at: Double) -> Double {
            easeOut(seg(t, at-0.18, at-0.03)) * (1-easeOut(seg(t, at+0.08, at+0.24)))
        }
        let finger = max(tap(play), tap(pause))
        return Frame(playing: t >= play && t < pause, music: t >= start ? t-start : -1,
                     cut: pause-start, finger: finger, press: finger, time: t)
    }
    static func kickAt(_ t: Double, _ distance: Double, _ cut: Double, _ laps: Int, _ mass: Double = 1) -> (x: Double, v: Double) {
        let m = sqrt(mass), w = 17/m, a = 5/m
        var x = 0.0, v = 0.0
        for (at, amplitude, _) in notes where at <= cut {
            for lap in 0..<laps {
                let tau = t-(at-Double(lap)*piece)-distance/speed
                if tau > 0 && tau < 1.2*m {
                    let amp = amplitude*11/m, e = exp(-a*tau)
                    x += amp*e*sin(w*tau)
                    v += amp*e*(w*cos(w*tau)-a*sin(w*tau))
                }
            }
        }
        return (x,v)
    }
    static func pushAt(_ t: Double, _ cut: Double, _ laps: Int) -> Double {
        var p = 0.0
        for (at, amplitude, _) in notes where at <= cut {
            for lap in 0..<laps {
                let tau = t-(at-Double(lap)*piece)
                if tau > 0 && tau < 1.5 { p += amplitude*exp(-6*tau)*cos(28*tau) }
            }
        }
        return p
    }
    static func ringsAt(_ t: Double, _ cut: Double, _ laps: Int) -> [Double] {
        var result: [Double] = []
        if t >= 0 {
            for lap in 0..<laps {
                for (at, amplitude, _) in notes.reversed() where at <= cut {
                    let age = t-(at-Double(lap)*piece)
                    if age >= 0 && age < 2.2 && result.count < 16 { result += [age, amplitude] }
                }
            }
        }
        while result.count < 16 { result += [-1, 0] }
        return result
    }
    /// Replay the one history-dependent value at 60 Hz for deterministic debug frames.
    static func pinned(_ t: Double) -> Frame {
        var f = frameAt(t)
        var at = 0.0, glow = 0.0
        while at < t {
            let dt = min(1.0/60, t-at)
            at += dt
            let s = frameAt(at)
            glow += (min(1, abs(s.kick))-glow)*min(1, dt*6)
        }
        f.glow = glow
        return f
    }
}

struct WindChimeTonesLayout {
    var w, h, sx, k, top: Double
    init(_ w: Double, _ h: Double, _ safeTop: Double, _ safeBottom: Double) {
        let top=max(safeTop,44), bottom=max(safeBottom,20)
        self.w=w; self.h=h; self.top=top; sx=w/402; k=(h-top-bottom)/(874-62-34)
    }
    func y(_ value: Double) -> Double { value < 62 ? value*top/62 : top+(value-62)*k }
    var cx: Double { 200*sx }
    var cy: Double { y(290) }
    var radius: Double { 101*sx }
    func distance(_ x: Double, _ y: Double) -> Double { hypot(x-cx, y-cy) }
    func toolX(_ i: Int) -> Double { [37.0,152,270][i]*sx }
    func body(_ x: Double, _ y: Double, _ half: Double, _ mass: Double) -> WindChimeTonesMotion.Body {
        let d = max(1,distance(x,y))
        return WindChimeTonesMotion.Body(d:d,ux:(x-cx)/d,uy:(y-cy)/d,half:half,mass:mass)
    }
    func toolBody(_ i: Int) -> WindChimeTonesMotion.Body { body(toolX(i)+47*sx,y(510)+45*k,47*sx,1) }
    var playerBody: WindChimeTonesMotion.Body { body((37+327/2.0)*sx,y(625)+40,327*sx/2,2.4) }
    var powerBody: WindChimeTonesMotion.Body { body(317*sx,y(292),32,0.6) }
}
