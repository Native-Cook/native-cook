import SwiftUI

struct NeonBoltStreakFrame {
    var time: Double = 0
    var camera: CGFloat = 0
    var lit = Array(repeating: CGFloat(0), count: 7)
    var fill = Array(repeating: CGFloat(0), count: 7)
    var burn = Array(repeating: CGFloat(0), count: 7)
    var head = Array(repeating: CGFloat(0), count: 7)
    var label = Array(repeating: CGFloat(0), count: 7)
    var after = Array(repeating: CGFloat(0), count: 7)
    var dy = Array(repeating: CGFloat(0), count: 7)
    var count: CGFloat = 3
    var flood: CGFloat = 0
    var flash: CGFloat = 0
    var ghost: CGFloat = 0.07
    var border: CGFloat = 0
    var borderLit = false
    var strikeDay = -1
    var strikeDrop: CGFloat = 0
    var charge: CGFloat = 0
    var jolt: CGFloat = 0
}

enum NeonBoltStreakMotion {
    static let loop = NeonBoltStreak.loop
    static let today = 3
    static let days = ["Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun"]

    static func clamp(_ value: Double) -> CGFloat { CGFloat(min(max(value, 0), 1)) }

    static func segment(_ time: Double, _ start: Double, _ end: Double) -> CGFloat {
        guard end > start else { return time >= end ? 1 : 0 }
        return clamp((time - start) / (end - start))
    }

    static func out(_ value: CGFloat) -> CGFloat { 1 - pow(1 - value, 3) }
    static func backOut(_ value: CGFloat) -> CGFloat { 1 + 2.25 * pow(value - 1, 3) + 1.25 * pow(value - 1, 2) }
    static func inOut(_ value: CGFloat) -> CGFloat {
        value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2
    }

    static func bump(_ time: Double, _ start: Double, _ duration: Double) -> CGFloat {
        guard time > start, time < start + duration else { return 0 }
        return CGFloat(sin((time - start) / duration * .pi))
    }

    static func restFrame() -> NeonBoltStreakFrame {
        var frame = NeonBoltStreakFrame()
        for index in 0..<3 {
            frame.lit[index] = 1
            frame.fill[index] = 1.02
            frame.burn[index] = 1.02
            frame.label[index] = 1
        }
        return frame
    }

    static func ignition(_ elapsed: Double, day: Int, long: Bool, charge: CGFloat, into frame: inout NeonBoltStreakFrame) {
        guard elapsed >= -0.05 else { return }
        let contact = long ? 0.25 : 0.15
        let catchAt = contact + (long ? 0.24 : 0.08)
        let burnDuration = long ? 0.4 : 0.3
        frame.strikeDay = day
        frame.dy[day] = elapsed < contact ? 2 * segment(elapsed, -0.05, 0) : 2 * (1 - out(segment(elapsed, contact, contact + 0.12)))
        if elapsed < contact {
            frame.strikeDrop = pow(clamp(elapsed / contact), 2)
            return
        }
        frame.strikeDrop = 1
        frame.jolt = (long ? 1.5 : 0.8) * (1 - out(segment(elapsed, contact, contact + 0.12)))
        if elapsed < catchAt {
            let spark = elapsed - contact
            if long {
                frame.lit[day] = spark < 0.04 ? 0.35 : spark < 0.09 ? 0 : spark < 0.12 ? 0.15 : spark < 0.16 ? 0 : spark < 0.21 ? 0.6 : 0
                frame.fill[day] = spark < 0.09 ? 0.07 : spark < 0.16 ? 0.05 : 0.2
            } else {
                frame.lit[day] = spark < 0.04 ? 0.35 : 0
                frame.fill[day] = 0.08
            }
            frame.head[day] = frame.lit[day] > 0 ? 1 : 0
            return
        }
        let progress = segment(elapsed, catchAt, catchAt + burnDuration)
        let start: CGFloat = long ? 0.2 : 0.15
        frame.fill[day] = start + (1.02 - start) * (long ? 1 - pow(1 - progress, 2) : out(progress))
        frame.burn[day] = frame.fill[day]
        frame.head[day] = 1 - segment(elapsed, catchAt + burnDuration, catchAt + burnDuration + 0.1)
        let overshoot = 1.15 + 0.20 * charge
        let age = elapsed - catchAt - burnDuration
        frame.lit[day] = age < 0 ? 0.6 + 0.3 * progress : age < 0.06 ? 0.9 + (overshoot - 0.9) * out(clamp(age / 0.06)) : overshoot + (1 - overshoot) * inOut(segment(age, 0.06, 0.32))
        frame.label[day] = out(segment(elapsed - catchAt, 0.08, 0.38))
        let dip = bump(elapsed, catchAt + burnDuration + 0.30, 0.07) + bump(elapsed, catchAt + burnDuration + 0.46, 0.07)
        frame.lit[day] *= 1 - 0.15 * dip
    }

    static func autoplayFrame(at rawTime: Double) -> NeonBoltStreakFrame {
        let time = (rawTime.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        var frame = restFrame()
        frame.time = time
        frame.camera = time < 2.25 ? inOut(segment(time, 0.3, 1.0)) : 1 - inOut(segment(time, 2.25, 2.85))

        let strikes: [(Int, Double, Bool)] = [(3, 1.15, true), (4, 3.60, false), (5, 3.75, false), (6, 3.90, false)]
        if time < 7.4 {
            for (day, start, long) in strikes { ignition(time - start, day: day, long: long, charge: 0, into: &frame) }
        } else {
            let age = time - 7.4
            let reset = inOut(segment(age, 0.08, 0.7))
            for day in 3..<7 {
                frame.lit[day] = age < 0.08 ? 1 - 0.75 * pow(clamp(age / 0.08), 3) : 0.25 * (1 - out(segment(age, 0.08, 0.8)))
                frame.after[day] = age < 0.08 ? clamp(age / 0.08) : 1 - out(segment(age, 0.08, 0.8))
                frame.fill[day] = 1.02 * (1 - reset)
                frame.burn[day] = frame.fill[day]
                frame.label[day] = 1 - inOut(segment(age, 0.08, 0.5))
            }
        }

        frame.count = 3 + backOut(segment(time, 2.75, 3.05))
        for start in [3.60, 3.75, 3.90] { frame.count += backOut(segment(time, start + 0.28, start + 0.58)) }
        frame.count -= 4 * inOut(segment(time, 7.5, 8.1))
        let flashStarts: [(Double, CGFloat)] = [(2.75, 1), (3.88, 0.3), (4.03, 0.3), (4.18, 1)]
        for (start, strength) in flashStarts {
            let age = time - start
            if age >= 0, age < 0.6 { frame.flash = max(frame.flash, strength * pow(1 - CGFloat(age / 0.6), 2)) }
        }

        if time >= 4.55, time < 7.4 {
            let circuit = time - 4.55
            frame.border = 1.01 * inOut(segment(circuit, 0.1, 1.1))
            frame.borderLit = circuit >= 0.1
            frame.flood = inOut(segment(circuit, 1.05, 1.65))
            frame.ghost = 0.12 + 0.18 * (1 - out(segment(circuit, 1.08, 1.55)))
            for day in 0..<7 {
                frame.lit[day] += 0.30 * bump(circuit, 0.1 * Double(day), 0.25) + 0.18 * bump(circuit, 1.1 + 0.06 * Double(day), 0.22)
            }
        } else if time >= 7.4 {
            frame.flood = 1 - inOut(segment(time - 7.4, 0.05, 0.75))
            frame.ghost = 0.12 - 0.05 * inOut(segment(time - 7.4, 0.1, 0.7))
        } else {
            frame.ghost = 0.07 + 0.05 * frame.flash
        }
        return frame
    }

    static func manualFrame(elapsed: Double, day: Int, charge: CGFloat, celebration: Bool, flicker: Bool) -> NeonBoltStreakFrame {
        var frame = restFrame()
        frame.time = elapsed
        frame.charge = charge
        if celebration {
            for index in 0..<7 { frame.lit[index] = 1; frame.fill[index] = 1.02; frame.burn[index] = 1.02; frame.label[index] = 1 }
            frame.count = 7
            frame.flood = inOut(segment(elapsed, 0.15, 0.7))
            frame.border = 1.01 * inOut(segment(elapsed, 0.05, 0.75))
            frame.borderLit = true
            frame.ghost = 0.30 - 0.16 * out(segment(elapsed, 0.5, 1.2))
            return frame
        }
        if flicker {
            let pulse = Self.flicker(at: elapsed)
            frame.lit[day] = pulse
            frame.fill[day] = 1
            frame.burn[day] = 1
            return frame
        }
        ignition(elapsed, day: day, long: false, charge: charge, into: &frame)
        if elapsed > 0.28 {
            let settled = out(segment(elapsed, 0.28, 0.58))
            frame.count = 3 + settled
            frame.flash = (1 - settled) * (0.65 + 0.35 * charge)
        }
        return frame
    }

    static func flicker(at elapsed: Double) -> CGFloat {
        if elapsed < 0.05 { return 0.1 }
        if elapsed < 0.09 { return 0.5 }
        if elapsed < 0.14 { return 0.08 }
        if elapsed < 0.2 { return 0.7 }
        return elapsed < 0.26 ? 0.7 + 0.45 * out(segment(elapsed, 0.2, 0.26)) : 1.15 - 0.15 * inOut(segment(elapsed, 0.26, 0.5))
    }
}
