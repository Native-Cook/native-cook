import Foundation

/// Every motion value of motion.ts as pure functions of time. t = 0 and t = loop are the pin's rest frame.
enum ForYouInsightsMotion {
    static let loop = 9.0
    static let resumeAfter = 2.0

    enum T {
        static let dimStart = 0.5, dimEnd = 0.8
        static let streamStart = 0.8, streamEnd = 2.3
        static let caretIn = 0.08
        static let caretOutStart = 2.3, caretOutEnd = 2.6
        static let shimmerStart = 2.25, shimmerEnd = 2.9
        static let pressStart = 2.95, pressDuration = 0.12
        static let openStart = 3.07, openDuration = 0.85
        static let closeStart = 6.35, closeDuration = 0.7
    }

    struct Spring {
        var response: Double
        var damping: Double
        /// Reanimated stops duration-configured springs at 1.5 × their perceptual duration.
        var settle: Double

        init(response: Double, damping: Double, settle: Double? = nil) {
            self.response = response
            self.damping = damping
            self.settle = settle ?? response * 4
        }
    }

    enum Springs {
        static let open = Spring(response: 0.5, damping: 0.82)
        static let close = Spring(response: 0.42, damping: 1)
        // Exact zero-velocity closed-form equivalents of Reanimated 4's duration springs. Reanimated
        // derives stiffness with mass 4 and energyThreshold 6e-9, then evaluates the same damped oscillator.
        static let touchOpen = Spring(response: 0.4091038637449126, damping: 0.82, settle: 0.975)
        static let touchClose = Spring(response: 0.4267429458117079, damping: 1, settle: 0.84)
        static let tiltBack = Spring(response: 0.2698843052331667, damping: 0.7, settle: 0.75)
        static let dragBack = Spring(response: 0.2580988056444297, damping: 0.8, settle: 0.63)
        static let lens = Spring(response: 0.2551957282933489, damping: 0.72, settle: 0.69)
    }

    static let pressScale = 0.97
    static let pressInMs = 0.12

    enum Stream {
        static let ghost = 0.16
        static let tokens = 18.0
        static let tokenMove = 0.72
        static let head = 56.0
        static let caretWidth = 2.5, caretInset = 4.0
        static let shimmerWidth = 140.0, shimmerSkew = 18.0
    }

    enum Depth {
        static let perspective = 900.0
        static let liftScale = 0.05
        static let flightTiltX = 9.0, flightTiltY = -11.0
        static let driftPeriod = 2.6
        static let driftTiltY = 7.0, driftTiltX = 4.0
        static let driftRampIn = 0.55, driftRampOut = 0.6
        static let parallax = 0.05
        static let depthScale = 0.03
        static let depthLift = 5.0
        static let overscan = 0.1
        static let heroShadowLevel = 0.35
        static let shadowOpacity = 0.26
        static let shadowOffsetMax = 20.0
        static let sheenOpacity = 0.22
        static let touchTiltY = 12.0, touchTiltX = 9.0, touchTiltRange = 140.0
    }

    enum Reader {
        static let backdrop = (0.08, 0.7)
        static let feedScale = 0.94
        static let rowTitleOut = (0.0, 0.3)
        static let rowTitleRise = 10.0
        static let titleIn = (0.42, 0.95)
        static let titleRise = 28.0
        static let titleScaleFrom = 0.96
        static let bodyIn = (0.45, 0.73)
        static let bodyStagger = 0.05
        static let bodyRise = 22.0
        static let closeIn = (0.7, 1.0)
        static let closeScaleFrom = 0.86
        static let tabOut = (0.0, 0.55)
        static let tabDrop = 120.0
        static let tabHidden = 0.6
        static let handOff = 0.03
    }

    enum Dismiss {
        static let resistance = 0.55, softness = 420.0, distance = 120.0, velocity = 900.0, scaleLoss = 0.12
    }

    // MARK: helpers

    static func clamp01(_ v: Double) -> Double { min(1, max(0, v)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / max(0.0001, b - a)) }
    static func lerp(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }
    static func smooth(_ p: Double) -> Double { let x = clamp01(p); return x * x * (3 - 2 * x) }

    private static func bez(_ t: Double, _ p1: Double, _ p2: Double) -> Double {
        let u = 1 - t
        return 3 * u * u * t * p1 + 3 * u * t * t * p2 + t * t * t
    }

    static func cubic(_ p: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp01(p)
        if x == 0 || x == 1 { return x }
        var lo = 0.0, hi = 1.0
        for _ in 0..<20 {
            let mid = (lo + hi) / 2
            if bez(mid, x1, x2) < x { lo = mid } else { hi = mid }
        }
        return bez((lo + hi) / 2, y1, y2)
    }

    static func easeOut(_ p: Double) -> Double { cubic(p, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ p: Double) -> Double { cubic(p, 0.77, 0, 0.175, 1) }
    /// Reanimated's default timing easing, Easing.inOut(Easing.quad).
    static func inOutQuad(_ p: Double) -> Double { let x = clamp01(p); return x < 0.5 ? 2 * x * x : 1 - 2 * (1 - x) * (1 - x) }

    /// Closed-form spring from 0 to 1 after `tau` seconds.
    static func spring(_ tau: Double, _ s: Spring) -> Double {
        if tau <= 0 { return 0 }
        let w = 2 * Double.pi / s.response
        let z = s.damping
        if z >= 1 { return 1 - exp(-w * tau) * (1 + w * tau) }
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * tau) * (cos(wd * tau) + (z * w) / wd * sin(wd * tau))
    }

    // MARK: the tip stream

    static func streamProgress(_ u: Double) -> Double {
        let x = clamp01(u)
        if x >= 1 { return 1 }
        let n = Stream.tokens
        let k = (x * n).rounded(.down)
        let f = x * n - k
        return (k + smooth(f / Stream.tokenMove)) / n
    }

    static func streamHeads(_ progress: Double, _ widths: [Double]) -> [Double] {
        var total = Stream.head
        for w in widths { total += w }
        let at = clamp01(progress) * total
        var heads: [Double] = []
        var start = 0.0
        for w in widths {
            heads.append(min(w + Stream.head, max(0, at - start)))
            start += w
        }
        return heads
    }

    struct TipPose: Equatable {
        var ghost = 1.0, stream = 1.0, caret = 0.0, shimmer = -1.0, active = false
    }

    static func tipPose(_ e: Double) -> TipPose {
        let dimLen = T.dimEnd - T.dimStart
        let streamLen = T.streamEnd - T.streamStart
        if e < 0 || e >= T.shimmerEnd - T.dimStart { return TipPose() }
        let streaming = e >= dimLen
        let ghost = e >= T.streamEnd - T.dimStart ? 1 : lerp(1, Stream.ghost, easeInOut(e / dimLen))
        let stream = streaming ? streamProgress((e - dimLen) / streamLen) : 0
        let caretIn = seg(e, dimLen - T.caretIn, dimLen)
        let caretOut = 1 - seg(e, T.caretOutStart - T.dimStart, T.caretOutEnd - T.dimStart)
        let shimmerRaw = seg(e, T.shimmerStart - T.dimStart, T.shimmerEnd - T.dimStart)
        return TipPose(
            ghost: ghost, stream: streaming ? stream : 0, caret: smooth(caretIn) * smooth(caretOut),
            shimmer: shimmerRaw > 0 && shimmerRaw < 1 ? easeInOut(shimmerRaw) : -1, active: true)
    }

    // MARK: the depth reader

    struct OpenPose {
        var press = 0.0, g = 0.0, opening = true, driftTime = 0.0, drift = 0.0
    }

    static func openPose(_ t: Double) -> OpenPose {
        let press = t >= T.pressStart && t < T.openStart ? easeOut(seg(t, T.pressStart, T.pressStart + T.pressDuration)) : 0
        var g = 0.0
        var opening = true
        if t >= T.closeStart + T.closeDuration { g = 0 }
        else if t >= T.closeStart { g = 1 - spring(t - T.closeStart, Springs.close); opening = false }
        else if t >= T.openStart + T.openDuration { g = 1 }
        else if t >= T.openStart { g = spring(t - T.openStart, Springs.open) }
        let holdStart = T.openStart + T.openDuration * 0.55
        let hold = T.closeStart - holdStart
        let tau = t - holdStart
        let drift = tau > 0 && tau < hold
            ? smooth(tau / Depth.driftRampIn) * (1 - smooth((tau - (hold - Depth.driftRampOut)) / Depth.driftRampOut))
            : 0
        return OpenPose(press: press, g: max(0, g), opening: opening, driftTime: max(0, tau), drift: drift)
    }

    static func liftOf(_ g: Double) -> Double {
        let x = clamp01(g)
        return sin(Double.pi * min(1, x)) * (1 - Depth.heroShadowLevel) + x * Depth.heroShadowLevel
    }

    static func tiltOf(_ g: Double, _ driftTime: Double, _ drift: Double) -> (x: Double, y: Double) {
        let flight = sin(Double.pi * clamp01(g))
        let phase = 2 * Double.pi * driftTime / Depth.driftPeriod
        return (flight * Depth.flightTiltX + sin(phase * 2) * Depth.driftTiltX * drift,
                flight * Depth.flightTiltY + sin(phase) * Depth.driftTiltY * drift)
    }

    static func separationOf(_ g: Double) -> Double { smooth(seg(g, 0.12, 0.85)) }

    static func layerShift(depth: Double, sep: Double, tiltX: Double, tiltY: Double, width: Double) -> (dx: Double, dy: Double, scale: Double) {
        let ny = tiltY / Depth.driftTiltY
        let nx = tiltX / Depth.driftTiltX
        return (-depth * ny * width * Depth.parallax * 0.5 * sep,
                depth * nx * width * Depth.parallax * 0.35 * sep - depth * Depth.depthLift * sep,
                1 + depth * Depth.depthScale * sep)
    }

    static func rubber(_ dy: Double) -> Double {
        let d = max(0, dy)
        return (d * Dismiss.resistance) / (1 + d / Dismiss.softness)
    }

    static func pose(_ t: Double) -> (tip: TipPose, open: OpenPose) {
        let time = (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        return (tipPose(time - T.dimStart), openPose(time))
    }
}
