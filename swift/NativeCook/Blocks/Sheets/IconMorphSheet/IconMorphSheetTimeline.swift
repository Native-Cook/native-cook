import Foundation

/// The authored 16.4-second choreography and shared easing functions.
enum IconMorphSheetTimeline {
    static let period = 16.4
    static let stepDuration = 1.15
    static let closeDuration = 0.46

    struct Frame {
        var position = 0.0
        var open = 1.0
        var contents = 1.0
        var stage = 1.0
        var drag = 0.0
        var edge = 0.0
        var labelOpen = 1.0
        var labelClosed = 0.0
        var labelClosedY = -12.0
        var labelOpenY = 0.0
        var buttonPress = 0.0
        var puckPress = 0.0
        var puck = 0
        var running = 1.0
        var bounce = 0.0
        var lean = 0.0
        var press = 0.0
        var markerX = 201.0
        var markerY = 804.0
        var markerOpacity = 0.0
        var markerPress = 0.0
        var markerKind = 0
    }

    static func clamp(_ n: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { max(lo, min(hi, n)) }
    static func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func progress(_ t: Double, _ start: Double, _ duration: Double) -> Double { clamp((t - start) / duration) }

    static func bezier(_ p: Double, _ x1: Double = 0.23, _ y1: Double = 1, _ x2: Double = 0.32, _ y2: Double = 1) -> Double {
        if p <= 0 { return 0 }
        if p >= 1 { return 1 }
        var lo = 0.0
        var hi = 1.0
        var u = p
        for _ in 0..<20 {
            let v = 1 - u
            let x = 3 * v * v * u * x1 + 3 * v * u * u * x2 + u * u * u
            if x < p { lo = u } else { hi = u }
            u = (lo + hi) / 2
        }
        let v = 1 - u
        return 3 * v * v * u * y1 + 3 * v * u * u * y2 + u * u * u
    }

    static func spring(_ t: Double, _ from: Double, _ to: Double, _ stiffness: Double = 320, _ damping: Double = 22, _ velocity: Double = 0) -> Double {
        if t <= 0 { return from }
        if t >= 1 { return to }
        let d = damping / 2
        let w = sqrt(stiffness - d * d)
        let x = from - to
        return to + exp(-d * t) * (x * cos(w * t) + (velocity + d * x) / w * sin(w * t))
    }

    static func bounceAt(_ t: Double, _ amplitude: Double = 1) -> Double {
        t < 0 || t >= 0.9 ? 0 : amplitude * 1.6 * exp(-6 * t) * sin(2 * .pi * 1.9 * t)
    }

    static func stepEase(_ p: Double) -> Double { bezier(p, 0.45, 0, 0.4, 1) }
    static func sheetEase(_ p: Double) -> Double { bezier(p, 0.32, 0.72, 0, 1) }

    static func sheetHeight(_ position: Double, _ short: Double, _ tall: Double) -> Double {
        let p = clamp(position, 0, 3)
        let step = Int(floor(p))
        return mix(short, tall, mix(Double(step % 2), Double((step + 1) % 2), p - Double(step)))
    }

    static func labelRoll(_ p: Double) -> (outOpacity: Double, inOpacity: Double, outY: Double, inY: Double) {
        let out = bezier(progress(p, 0, 0.45))
        let incoming = bezier(progress(p, 0.45, 0.55))
        return (1 - out, incoming, -12 * out, 12 * (1 - incoming))
    }

    static func rollSheetLabels(_ f: inout Frame, _ from: Frame, _ p: Double, _ opening: Bool) {
        let roll = labelRoll(p)
        if opening {
            f.labelClosed = from.labelClosed * roll.outOpacity
            f.labelOpen = mix(from.labelOpen, 1, roll.inOpacity)
            f.labelClosedY = mix(from.labelClosedY, -12, 1 - roll.outOpacity)
            f.labelOpenY = mix(from.labelOpen == 0 ? 12 : from.labelOpenY, 0, roll.inOpacity)
        } else {
            f.labelOpen = from.labelOpen * roll.outOpacity
            f.labelClosed = mix(from.labelClosed, 1, roll.inOpacity)
            f.labelOpenY = mix(from.labelOpenY, -12, 1 - roll.outOpacity)
            f.labelClosedY = mix(from.labelClosed == 0 ? 12 : from.labelClosedY, 0, roll.inOpacity)
        }
    }

    static func buttonLabel(_ f: Frame, _ index: Int) -> (opacity: Double, y: Double) {
        if index == 0 { return (f.labelClosed, f.labelClosedY) }
        let roll = labelRoll(clamp((f.position - 2.35) / 0.3))
        return (f.labelOpen * (index == 1 ? roll.outOpacity : roll.inOpacity), f.labelOpenY + (index == 1 ? roll.outY : roll.inY))
    }

    static func contentFade(_ open: Double) -> Double {
        let p = progress(open, 0, 0.25)
        return p * p * (3 - 2 * p)
    }

    static func openingStage(_ elapsed: Double) -> Double { sheetEase(progress(elapsed, 0.08, 0.48)) }

    static func releaseEase(_ p: Double, _ distance: Double, _ velocity: Double, _ duration: Double) -> Double {
        let slope = abs(distance) < 0.0001 ? 0 : clamp(velocity * duration / distance, 0, 6)
        let a = slope * 0.23
        return bezier(a * clamp(p) + (1 - a) * clamp(p) * clamp(p))
    }

    static func marker(_ f: inout Frame, _ t: Double, _ down: Double, _ up: Double, _ kind: Int, _ x: Double, _ y: Double) {
        if t < down - 0.12 || t >= up + 0.2 { return }
        f.markerOpacity = min(progress(t, down - 0.12, 0.12), 1 - progress(t, up, 0.2))
        f.markerPress = t < down ? 0 : t < up ? 1 : 1 - progress(t, up, 0.12)
        f.markerKind = kind
        f.markerX = x
        f.markerY = y
    }

    static func buttonBeat(_ t: Double, _ down: Double, _ up: Double) -> Double {
        if t < down || t >= up + 1 { return 0 }
        return t < up ? bezier(progress(t, down, 0.12)) : spring(t - up, 1, 0)
    }

    static func loop(_ seconds: Double, reduced: Bool = false) -> Frame {
        let rounded = ((seconds.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period) * 1e9).rounded() / 1e9
        let t = rounded.truncatingRemainder(dividingBy: period)
        var f = Frame()
        let duration = reduced ? 0.45 : stepDuration
        if t >= 2.05 { f.position = stepEase(progress(t, 2.05, duration)) }
        if t >= 6.2 && t < 6.8 {
            let p = progress(t, 6.2, 0.6)
            let e = p * p * (3 - 2 * p)
            f.position = 1 + 0.5 * e
            f.lean = reduced ? 0 : -0.6 * sin(.pi * p)
        } else if t >= 6.8 && t < 7.1 {
            let p = progress(t, 6.8, 0.3)
            f.position = 1.5 - 0.14 * p * p * (3 - 2 * p)
            f.lean = reduced ? 0 : 0.3 * sin(.pi * p)
        } else if t >= 7.1 && t < 7.4 {
            let p = progress(t, 7.1, 0.3)
            f.position = 1.36 + 0.36 * p * p
            f.lean = reduced ? 0 : -0.8 * p
        } else if t >= 7.4 {
            f.position = mix(1.72, 2, reduced ? bezier(progress(t, 7.4, 0.28)) : releaseEase(progress(t, 7.4, 0.28), 0.28, 2.4, 0.28))
            f.lean = reduced || t >= 8.1 ? 0 : -0.8 * exp(-10 * (t - 7.4)) * (1 - progress(t, 7.8, 0.3))
        }
        let fingerTravel = t < 6.2 ? 0 : t < 7.4 ? 280 * (f.position - 1) : 280 * 0.72
        if t >= 11.12 { f.position = 2 + stepEase(progress(t, 11.12, duration)) }
        if !reduced {
            f.bounce = bounceAt(t - (2.05 + duration)) + bounceAt(t - 4.35, 0.6) + bounceAt(t - 7.68, 0.7) + bounceAt(t - (11.12 + duration))
        }
        if t >= 4.2 && t < 4.35 { f.press = bezier(progress(t, 4.2, 0.11)) }
        else if t >= 4.35 && t < 5.35 { f.press = clamp(spring(t - 4.35, 1, 0)) }
        f.buttonPress = buttonBeat(t, 1.93, 2.05) + buttonBeat(t, 11, 11.12) + buttonBeat(t, 14.33, 14.45) + buttonBeat(t, 15.15, 15.27)
        if t >= 14.45 && t < 15.27 {
            f.open = 1 - (reduced ? progress(t, 14.45, 0.2) : sheetEase(progress(t, 14.45, closeDuration)))
            f.contents = reduced ? f.open : contentFade(f.open)
            f.stage = reduced ? f.open : 1
            rollSheetLabels(&f, Frame(), progress(t, 14.45, 0.26), false)
            f.running = 0
            if t >= (reduced ? 14.65 : 14.91) {
                f.position = 0
                f.stage = 0
            }
        } else if t >= 15.27 {
            f.position = 0
            f.open = reduced ? progress(t, 15.27, 0.2) : sheetEase(progress(t, 15.27, 0.56))
            f.contents = reduced ? f.open : contentFade(f.open)
            f.stage = reduced ? f.open : openingStage(t - 15.27)
            var closed = Frame()
            closed.labelOpen = 0
            closed.labelClosed = 1
            closed.labelClosedY = 0
            closed.labelOpenY = -12
            rollSheetLabels(&f, closed, progress(t, 15.27, 0.26), true)
            f.running = 0
        }
        marker(&f, t, 1.93, 2.05, 0, 201, 804)
        marker(&f, t, 4.2, 4.35, 1, 201, 612)
        marker(&f, t, 6.15, 7.4, 2, 316 - fingerTravel, 716)
        marker(&f, t, 11, 11.12, 0, 201, 804)
        marker(&f, t, 14.33, 14.45, 0, 201, 804)
        marker(&f, t, 15.15, 15.27, 0, 201, 804)
        return f
    }
}
