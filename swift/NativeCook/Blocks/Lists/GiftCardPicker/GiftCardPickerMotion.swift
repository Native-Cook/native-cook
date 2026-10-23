import Foundation

// Pure time sampling: the display clock never writes view state.
struct GiftCardPickerMotion {
    struct Track {
        enum Curve { case linear, quadratic, bezier(Double, Double, Double, Double), spring(Double, Double, Double, Double) }
        struct Segment {
            var from: Double
            var to: Double
            var start: Double
            var duration: Double
            var curve: Curve
            var end: Double { start + duration }
        }
        var initial = 0.0
        var segments: [Segment] = []

        func value(_ time: Double) -> Double {
            var result = initial
            for s in segments {
                if time < s.start { break }
                if time >= s.end { result = s.to; continue }
                let t = max(0, time - s.start)
                let p = t / s.duration
                switch s.curve {
                case .linear: result = s.from + (s.to - s.from) * p
                case .quadratic:
                    let eased = p < 0.5 ? 2 * p * p : 1 - pow(-2 * p + 2, 2) / 2
                    result = s.from + (s.to - s.from) * eased
                case let .bezier(x1, y1, x2, y2): result = s.from + (s.to - s.from) * Self.bezier(p, x1, y1, x2, y2)
                case let .spring(stiffness, damping, mass, velocity):
                    result = Self.spring(s.from - s.to, velocity, stiffness, damping, mass, t).x + s.to
                }
            }
            return result
        }

        mutating func animate(to target: Double, at time: Double, duration: Double, curve: Curve = .quadratic, delay: Double = 0) {
            let current = value(time)
            initial = current
            segments = [Segment(from: current, to: target, start: time + delay, duration: duration, curve: curve)]
        }

        mutating func set(_ value: Double) { initial = value; segments = [] }

        func velocity(_ time: Double) -> Double {
            guard let segment = segments.last(where: { time >= $0.start && time < $0.end }),
                  case let .spring(k, c, m, v) = segment.curve else { return 0 }
            return Self.spring(segment.from - segment.to, v, k, c, m, time - segment.start).v
        }

        static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
            func sample(_ t: Double, _ a: Double, _ b: Double) -> Double {
                3 * (1 - t) * (1 - t) * t * a + 3 * (1 - t) * t * t * b + t * t * t
            }
            var lo = 0.0
            var hi = 1.0
            for _ in 0..<24 {
                let mid = (lo + hi) / 2
                if sample(mid, x1, x2) < x { lo = mid } else { hi = mid }
            }
            return sample((lo + hi) / 2, y1, y2)
        }

        static func spring(_ x0: Double, _ v0: Double, _ k: Double, _ c: Double, _ m: Double, _ t: Double) -> (x: Double, v: Double) {
            let w = sqrt(k / m)
            let z = c / (2 * sqrt(k * m))
            if z >= 1 {
                let b = v0 + w * x0
                let envelope = exp(-w * t)
                return (envelope * (x0 + b * t), envelope * (b - w * (x0 + b * t)))
            }
            let wd = w * sqrt(1 - z * z)
            let b = (v0 + z * w * x0) / wd
            let shape = x0 * cos(wd * t) + b * sin(wd * t)
            let derivative = -x0 * wd * sin(wd * t) + b * wd * cos(wd * t)
            let envelope = exp(-z * w * t)
            return (envelope * shape, envelope * (derivative - z * w * shape))
        }

        // Reanimated 4.5.1 duration spring: mass 4, threshold 6e-9 and 1.5× perceptual duration.
        static func snapStiffness(displacement x0: Double, velocity v0: Double) -> Double {
            if abs(x0) + abs(v0) < 1e-12 { return 900 }
            func difference(_ k: Double) -> Double {
                let w = sqrt(k / 4) * 0.8
                let t = 0.4 * 1.5
                let e = exp(-w * t)
                let x = (x0 + (v0 + x0 * w) * t) * e
                let v = x * -w + (v0 + x0 * w) * e
                return (k * x * x + 4 * v * v) / (k * x0 * x0 + 4 * v0 * v0) - 6e-9
            }
            var lo = Double.ulpOfOne
            var hi = 8_000.0
            var k = (lo + hi) / 2
            for _ in 0..<100 {
                let d = difference(k)
                if abs(d) <= 6e-12 { break }
                if d > 0 { lo = k } else { hi = k }
                k = (lo + hi) / 2
            }
            return k
        }

        static func springDuration(displacement: Double, velocity: Double, initialVelocity: Double, stiffness: Double, damping: Double, mass: Double) -> Double {
            // Reanimated advances with the inherited velocity but normalizes its termination
            // energy with the velocity supplied in the new spring config.
            let energy = stiffness * displacement * displacement + mass * initialVelocity * initialVelocity
            guard energy > 0 else { return 0 }
            var lo = 0.0
            var hi = 20.0
            for _ in 0..<48 {
                let mid = (lo + hi) / 2
                let state = spring(displacement, velocity, stiffness, damping, mass, mid)
                if (stiffness * state.x * state.x + mass * state.v * state.v) / energy > 6e-9 { lo = mid } else { hi = mid }
            }
            return hi
        }
    }

    struct Frame {
        var sheet: Double
        var stage: Double
        var push: Double
        var reveal: Double
        var scroll: Double
        var trackFade: Double
        var active: Int
        var backgrounds: [Double]
        var descriptions: [Double]
        var layer: Int
        var commit: Double
        var commitIndex: Int
    }

    var sheet = Track()
    var stage = Track()
    var push = Track()
    var reveal = Track()
    var scroll = Track()
    var trackFade = Track(initial: 1)
    var commit = Track()
    var backgrounds = (0..<5).map { Track(initial: $0 == 0 ? 1 : 0) }
    var descriptions = (0..<5).map { Track(initial: $0 == 0 ? 1 : 0) }
    var active = 0
    var layer = 0
    var dismissEnd: Double?
    var commitEnd: Double?
    var commitIndex = 0

    mutating func shiftTime(by delta: Double) {
        func shifted(_ track: Track) -> Track {
            var copy = track
            for index in copy.segments.indices { copy.segments[index].start += delta }
            return copy
        }
        sheet = shifted(sheet)
        stage = shifted(stage)
        push = shifted(push)
        reveal = shifted(reveal)
        scroll = shifted(scroll)
        trackFade = shifted(trackFade)
        commit = shifted(commit)
        backgrounds = backgrounds.map(shifted)
        descriptions = descriptions.map(shifted)
        if let end = dismissEnd { dismissEnd = end + delta }
        if let end = commitEnd { commitEnd = end + delta }
    }

    func frame(at time: Double) -> Frame {
        if let commitEnd, time >= commitEnd {
            return GiftCardPickerMotion().frame(at: time)
        }
        if let dismissEnd, time >= dismissEnd {
            return GiftCardPickerMotion().frame(at: time)
        }
        return Frame(sheet: sheet.value(time), stage: stage.value(time), push: push.value(time), reveal: reveal.value(time), scroll: scroll.value(time), trackFade: trackFade.value(time), active: active, backgrounds: backgrounds.map { $0.value(time) }, descriptions: descriptions.map { $0.value(time) }, layer: layer, commit: commit.value(time), commitIndex: commitIndex)
    }

    mutating func present(at time: Double, reduced: Bool) {
        guard frame(at: time).sheet <= 0.01 else { return }
        self = Self()
        layer = 1
        sheet.animate(to: 1, at: time, duration: reduced ? 0.2 : 0.3, curve: reduced ? .quadratic : .bezier(0.32, 0.72, 0, 1))
    }

    mutating func dismiss(at time: Double, reduced: Bool) {
        let duration = reduced ? 0.2 : 0.24
        sheet.animate(to: 0, at: time, duration: duration, curve: reduced ? .quadratic : .bezier(0.32, 0.72, 0, 1))
        dismissEnd = time + duration
    }

    mutating func discover(at time: Double) {
        let current = frame(at: time)
        guard current.stage <= 0.001, current.sheet >= 0.99 else { return }
        stage.animate(to: 1, at: time, duration: 0.7, curve: .linear)
        stage.segments.append(.init(from: 1, to: 2, start: time + 0.7 + 2.4, duration: 0.6, curve: .linear))
    }

    mutating func choose(at time: Double, reduced: Bool) {
        let current = frame(at: time)
        guard current.stage >= 1.99, current.push <= 0.001 else { return }
        layer = 2
        push.animate(to: 1, at: time, duration: reduced ? 0.2 : 0.25, curve: reduced ? .quadratic : .bezier(0.23, 1, 0.32, 1), delay: reduced ? 0 : 0.03)
        reveal.animate(to: 1, at: time, duration: 0.2, delay: 0.03 + 0.53)
        reveal.segments.append(.init(from: 1, to: 2, start: time + 0.03 + 0.53 + 0.2, duration: 0.24, curve: .quadratic))
    }

    mutating func commitSelection(at time: Double, reduced: Bool) {
        let current = frame(at: time)
        guard current.layer == 2, current.reveal >= 0.99, current.commit <= 0.001 else { return }
        commitIndex = active
        layer = 3
        if reduced {
            commit.animate(to: 1, at: time, duration: 0.42, curve: .quadratic)
            sheet.animate(to: 0, at: time + 0.2, duration: 0.2, curve: .quadratic)
            commitEnd = time + 0.42
        } else {
            // lift 240 ms, flip 560 ms, hold 700 ms, flight 620 ms, landing 420 ms
            commit.animate(to: 1, at: time, duration: 2.54, curve: .linear)
            sheet.animate(to: 0, at: time + 1.5, duration: 0.62, curve: .bezier(0.32, 0.72, 0, 1))
            commitEnd = time + 2.54
        }
    }

    mutating func pick(_ index: Int, at time: Double) {
        guard index >= 0, index <= 4, index != active else { return }
        let previous = active
        active = index
        if index > previous {
            for i in 0..<index { backgrounds[i].set(1) }
            backgrounds[index].animate(to: 1, at: time, duration: 0.45)
        } else {
            backgrounds[index].set(1)
            for i in (index + 1)..<5 where backgrounds[i].value(time) > 0 {
                backgrounds[i].animate(to: 0, at: time, duration: 0.45)
            }
        }
        for i in 0..<5 { descriptions[i].animate(to: i == index ? 1 : 0, at: time, duration: 0.15) }
    }

    mutating func goTo(_ target: Int, at time: Double, velocity: Double = 0, reduced: Bool) {
        let index = min(4, max(0, target))
        pick(index, at: time)
        if reduced {
            let start = scroll.value(time)
            trackFade.animate(to: 0, at: time, duration: 0.09)
            trackFade.segments.append(.init(from: 0, to: 1, start: time + 0.09, duration: 0.13, curve: .quadratic))
            scroll = Track(initial: start, segments: [.init(from: start, to: Double(index), start: time + 0.09, duration: 0, curve: .linear)])
        } else {
            let x = scroll.value(time) - Double(index)
            let combined = springVelocity(to: Double(index), at: time, velocity: velocity)
            let k = Track.snapStiffness(displacement: x, velocity: combined)
            spring(to: Double(index), at: time, stiffness: k, damping: 2 * 0.8 * sqrt(k * 4), mass: 4, velocity: velocity)
        }
    }

    private func springVelocity(to target: Double, at time: Double, velocity: Double) -> Double {
        let combined = scroll.velocity(time) + velocity
        let current = scroll.value(time)
        return (target > current && combined < 0) || (target < current && combined > 0) ? 0 : combined
    }

    mutating func spring(to target: Double, at time: Double, stiffness: Double, damping: Double, mass: Double, velocity: Double) {
        let combined = springVelocity(to: target, at: time, velocity: velocity)
        let duration = Track.springDuration(displacement: scroll.value(time) - target, velocity: combined, initialVelocity: velocity, stiffness: stiffness, damping: damping, mass: mass)
        scroll.animate(to: target, at: time, duration: duration, curve: .spring(stiffness, damping, mass, combined))
    }

    private var tracks: [Track] { [sheet, stage, push, reveal, scroll, trackFade, commit] + backgrounds + descriptions }
    func nextBoundary(after time: Double, reduced: Bool) -> Double? {
        var points = tracks.flatMap { $0.segments.flatMap { [$0.start, $0.end] } }
        if let dismissEnd { points.append(dismissEnd) }
        if let commitEnd { points.append(commitEnd) }
        if !reduced, let collapse = stage.segments.first {
            points.append(collapse.start + 0.7 * 0.3)
            points.append(collapse.start + 0.7 + 2.4 + 0.6 * 0.55)
        }
        return points.filter { $0 > time + 0.00001 }.min()
    }
    func needsFrames(at time: Double, reduced: Bool) -> Bool {
        let f = frame(at: time)
        if let dismissEnd, time >= dismissEnd { return false }
        if let commitEnd, time >= commitEnd { return false }
        if !reduced, f.sheet > 0, f.push < 1, f.stage > 0.3, f.stage < 1.55 { return true }
        return tracks.contains { track in track.segments.contains { time >= $0.start && time < $0.end } }
    }

    static let cueTimes: [Double] = [0.5, 3.1, 8.6, 10.65, 10.92, 12.67, 12.94, 14.77, 15.04, 17.07, 17.34, 19.5]
    static func script(at time: Double) -> Self {
        var motion = Self()
        for (index, cue) in cueTimes.enumerated() where cue <= time {
            switch index {
            case 0: motion.present(at: cue, reduced: false)
            case 1: motion.discover(at: cue)
            case 2: motion.choose(at: cue, reduced: false)
            case 3, 5, 7, 9:
                let next = motion.scroll.value(cue).rounded() + 1
                motion.spring(to: next, at: cue, stiffness: 20.25, damping: 9, mass: 1, velocity: 1.2)
            case 4, 6, 8, 10: motion.pick(motion.active + 1, at: cue)
            default: motion.dismiss(at: cue, reduced: false)
            }
        }
        return motion
    }
}
