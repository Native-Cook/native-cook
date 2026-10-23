import Foundation

enum SavedPlacesMapMotion {
    static let LOOP = 7.5
    static let RESUME_AFTER = 2.0
    static let RISE = 150.0
    static let CONTROLS_FADE = (0.1, 0.5)

    enum T {
        static let rise = 1.0
        static let clusterIn = 1.5
        static let press = 2.8
        static let check = 2.9
        static let unpress = 4.0
        static let uncheck = 4.08
        static let fall = 4.75
        static let clusterOut = 4.75
    }

    struct SpringSpec {
        let omega: Double
        let zeta: Double
        let amp: Double
        init(omega: Double, zeta: Double, amp: Double = 0) {
            self.omega = omega; self.zeta = zeta; self.amp = amp
        }
    }

    enum SPRINGS {
        static let sheetRise = SpringSpec(omega: 7.5, zeta: 0.72)
        static let sheetFall = SpringSpec(omega: 8, zeta: 1)
        static let roll = SpringSpec(omega: 20, zeta: 0.7)
        static let pop = SpringSpec(omega: 18, zeta: 0.4, amp: 0.22)
        static let press = SpringSpec(omega: 22, zeta: 0.6)
    }

    enum CLUSTER {
        static let dur = 1.0
        static let span = 0.4
        static let glide = 0.6
        static let arc = 0.15
        static let endScale = 0.55
        static let fadeFrom = 0.7
        static let landFrom = 0.75
        static let gulp = 0.06
        static let bubbleFrom = 0.9
    }

    enum GRAB {
        static let scale = 1.12
        static let lead = 0.15
        static let fadeIn = 0.12
        static let fadeOut = 0.15
        static let hold = 0.15
    }

    static let TOGGLE_GAP = 0.3

    enum PRESS {
        static let checkbox = 0.9
        static let tile = 0.97
        static let duration = 0.1
    }

    enum VISIT {
        static let fill = 0.15
        static let draw = (0.05, 0.3)
        static let roll = 0.06
        static let badge = (0.06, 0.26)
        static let fadeOut = 0.15
        static let reducedFade = 0.2
    }

    enum DRAG {
        static let rubber = 5.0
        static let project = 0.15
        static let slop = 6.0
    }

    struct VisitFrame {
        let fill: Double
        let draw: Double
        let roll: Double
        let badge: Double
        let pop: Double
    }

    struct Frame {
        let sheet: Double
        let cluster: Double
        let fill: Double
        let draw: Double
        let roll: Double
        let badge: Double
        let visitPop: Double
        let tapIndex: Int
        let tapPop: Double
        let press: Double
        let pressTarget: Int
        let grab: Double

        static let rest = Frame(sheet: 0, cluster: 0, fill: 0, draw: 0, roll: 0, badge: 0,
                                visitPop: 1, tapIndex: -1, tapPop: 1, press: 0,
                                pressTarget: SavedPlacesMapPlayback.Hit.checkbox.rawValue, grab: 0)
    }

    static func normalized(_ time: Double) -> Double {
        (time.truncatingRemainder(dividingBy: LOOP) + LOOP).truncatingRemainder(dividingBy: LOOP)
    }

    static func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }
    static func segment(_ time: Double, _ start: Double, _ end: Double) -> Double {
        clamp01((time - start) / (end - start))
    }
    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }

    static func bezier(_ progress: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp01(progress)
        if x == 0 || x == 1 { return x }
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let value = (low + high) / 2
            let inverse = 1 - value
            let at = 3 * inverse * inverse * value * x1
                + 3 * inverse * value * value * x2
                + value * value * value
            if at < x { low = value } else { high = value }
        }
        let value = (low + high) / 2
        let inverse = 1 - value
        return 3 * inverse * inverse * value * y1
            + 3 * inverse * value * value * y2
            + value * value * value
    }

    static func easeOut(_ progress: Double) -> Double { bezier(progress, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ progress: Double) -> Double { bezier(progress, 0.77, 0, 0.175, 1) }

    static func springStep(_ seconds: Double, _ spring: SpringSpec) -> Double {
        guard seconds > 0 else { return 0 }
        if spring.zeta >= 1 {
            return 1 - exp(-spring.omega * seconds) * (1 + spring.omega * seconds)
        }
        let damped = spring.omega * sqrt(1 - spring.zeta * spring.zeta)
        return 1 - exp(-spring.zeta * spring.omega * seconds)
            * (cos(damped * seconds) + spring.zeta * spring.omega / damped * sin(damped * seconds))
    }

    static func springValue(from: Double, to: Double, velocity: Double, seconds: Double, spring: SpringSpec) -> Double {
        guard seconds > 0 else { return from }
        let offset = from - to
        if spring.zeta >= 1 {
            return to + exp(-spring.omega * seconds)
                * (offset + (velocity + spring.omega * offset) * seconds)
        }
        let damped = spring.omega * sqrt(1 - spring.zeta * spring.zeta)
        return to + exp(-spring.zeta * spring.omega * seconds)
            * (offset * cos(damped * seconds)
               + (velocity + spring.zeta * spring.omega * offset) / damped * sin(damped * seconds))
    }

    static func popScale(_ seconds: Double) -> Double {
        guard seconds > 0, seconds <= 1.2 else { return 1 }
        let spring = SPRINGS.pop
        let damped = spring.omega * sqrt(1 - spring.zeta * spring.zeta)
        return 1 + spring.amp * exp(-spring.zeta * spring.omega * seconds) * sin(damped * seconds)
    }

    static func band(_ value: Double) -> Double {
        if value > 1 { return 1 + (value - 1) / (1 + DRAG.rubber * (value - 1)) }
        if value < 0 { return value / (1 - DRAG.rubber * value) }
        return value
    }

    static func sheetAt(_ time: Double) -> Double {
        if time < T.rise { return 0 }
        if time < T.fall { return springStep(time - T.rise, SPRINGS.sheetRise) }
        let seconds = time - T.fall
        if seconds > 1.6 { return 0 }
        return 1 - springStep(seconds, SPRINGS.sheetFall)
    }

    static func clusterAt(_ time: Double) -> Double {
        if time < T.fall { return segment(time, T.clusterIn, T.clusterIn + CLUSTER.dur) }
        return 1 - segment(time, T.clusterOut, T.clusterOut + CLUSTER.dur)
    }

    static func pressAt(_ time: Double) -> Double {
        func press(_ start: Double) -> Double {
            let dip = start + PRESS.duration
            if time < start { return 0 }
            if time < dip { return easeOut(segment(time, start, dip)) }
            return 1 - springStep(time - dip, SPRINGS.press)
        }
        return max(press(T.press), press(T.unpress))
    }

    static func grabAt(_ time: Double) -> Double {
        func grab(_ move: Double) -> Double {
            let start = move - GRAB.lead
            let end = move + GRAB.hold
            return easeOut(segment(time, start, start + GRAB.fadeIn))
                * (1 - easeOut(segment(time, end, end + GRAB.fadeOut)))
        }
        return max(grab(T.rise), grab(T.fall))
    }

    static func visitFrame(direction: Int, seconds: Double, reduced: Bool) -> VisitFrame {
        if reduced {
            let progress = segment(seconds, 0, VISIT.reducedFade)
            let value = direction == 1 ? progress : 1 - progress
            return VisitFrame(fill: value, draw: value, roll: value, badge: value, pop: 1)
        }
        if direction == 1 {
            return VisitFrame(
                fill: easeOut(segment(seconds, 0, VISIT.fill)),
                draw: easeOut(segment(seconds, VISIT.draw.0, VISIT.draw.1)),
                roll: springStep(seconds - VISIT.roll, SPRINGS.roll),
                badge: easeOut(segment(seconds, VISIT.badge.0, VISIT.badge.1)),
                pop: popScale(seconds - VISIT.roll)
            )
        }
        let out = 1 - easeOut(segment(seconds, 0, VISIT.fadeOut))
        return VisitFrame(fill: out, draw: out,
                          roll: 1 - springStep(seconds - 0.02, SPRINGS.roll),
                          badge: out, pop: 1)
    }

    static func glyphProgress(cluster: Double, delay: Double) -> Double {
        easeInOut(segment(cluster, delay, delay + CLUSTER.glide))
    }

    // MARK: 3D (motion.ts TILT, STAND, BEAM, SHADOW, RIPPLE)

    enum TILT {
        static let pitch = 60.0
        static let perspective = 2700.0
        static let scale = 1.8
        static let originX = 603.0
        static let originY = 1140.0
        static let lift = -225.0
        static let max = 1.08
        static let norm = 1.55
        static let fog = (0.25, 1.0)
    }
    enum STAND {
        static let start = 0.25
        static let step = 0.07
        static let span = 0.45
        static let back = 1.4
        static let stalk = 96.0
    }
    enum BEAM {
        static let h = 360.0
        static let w = 22.0
        static let core = 4.0
        static let opacity = 0.9
        static let flare = 4.0
    }
    enum SHADOW {
        static let len = 150.0
        static let w = 30.0
        static let angle = 28.0
        static let opacity = 0.34
    }
    enum RIPPLE {
        static let d = 44.0
        static let grow = 1.9
        static let at = 0.55
    }

    static func tiltOf(_ sheet: Double) -> Double { sheet < 0 ? 0 : sheet > TILT.max ? TILT.max : sheet }

    struct Projected { let x: Double; let y: Double; let f: Double; let m: Double }

    static func project(_ x: Double, _ y: Double, _ p: Double) -> Projected {
        let s = 1 + (TILT.scale - 1) * p
        let th = TILT.pitch * p * .pi / 180
        let dx = (x - TILT.originX) * s
        let dy = (y - TILT.originY) * s
        let z = dy * sin(th)
        let f = TILT.perspective / (TILT.perspective - z)
        return Projected(x: TILT.originX + dx * f,
                         y: TILT.originY + dy * cos(th) * f + TILT.lift * p,
                         f: f, m: (f * s) / (1 + (TILT.norm - 1) * p))
    }

    static func backOut(_ p: Double) -> Double {
        let c = STAND.back
        let q = clamp01(p) - 1
        return 1 + (c + 1) * q * q * q + c * q * q
    }
    static func standLinear(_ p: Double, _ rank: Int) -> Double {
        let a = STAND.start + STAND.step * Double(rank)
        return segment(p, a, a + STAND.span)
    }
    static func standOf(_ p: Double, _ rank: Int) -> Double { backOut(standLinear(p, rank)) }

    struct Disc { let x: Double; let y: Double; let m: Double; let st: Double; let gx: Double; let gy: Double }
    static func discAt(_ cx: Double, _ cy: Double, _ p: Double, _ rank: Int, _ radius: Double) -> Disc {
        let g = project(cx, cy, p)
        let st = standOf(p, rank)
        return Disc(x: g.x, y: g.y - st * (STAND.stalk + radius) * g.m, m: g.m, st: st, gx: g.x, gy: g.y)
    }

    static func autoplay(_ elapsed: Double) -> Frame {
        let time = normalized(elapsed)
        let visit: VisitFrame
        if time >= T.check, time < T.uncheck {
            visit = visitFrame(direction: 1, seconds: time - T.check, reduced: false)
        } else if time >= T.uncheck {
            visit = visitFrame(direction: 0, seconds: time - T.uncheck, reduced: false)
        } else {
            visit = visitFrame(direction: 0, seconds: 99, reduced: false)
        }
        return Frame(sheet: sheetAt(time), cluster: clusterAt(time), fill: visit.fill,
                     draw: visit.draw, roll: visit.roll, badge: visit.badge,
                     visitPop: visit.pop, tapIndex: -1, tapPop: 1,
                     press: pressAt(time), pressTarget: SavedPlacesMapPlayback.Hit.checkbox.rawValue,
                     grab: grabAt(time))
    }
}
