import Foundation

struct PosterGlowCarouselMotion {
    static let loop: Double = 14
    static let loopMilliseconds = 14_000.0
    static let idleResume: Double = 3.5

    private(set) var autoStart: Date?
    private(set) var autoRunning = false
    private(set) var manualPicker = 1.0
    private(set) var manualScroll = 0.0
    private(set) var rowGeneration = 0
    private(set) var pickerTransition: PosterGlowCarouselPickerTransition?

    var hasPickerTransition: Bool { pickerTransition != nil }

    struct Frame {
        let clock: Double
        let scroll: Double
        let pickerOpacity: Double
        let autoPress: Double
        let backPress: Double
        let rowGeneration: Int
    }

    init(autoPlay: Bool) {
        autoStart = nil
        pickerTransition = nil
        if autoPlay {
            autoRunning = true
            autoStart = nil
        }
    }

    mutating func startAuto(at date: Date, reset: Bool = true) {
        autoStart = date
        autoRunning = true
        pickerTransition = nil
        if reset {
            manualPicker = 1
            manualScroll = 0
            rowGeneration += 1
        }
    }

    mutating func stopForReducedMotion() {
        guard autoRunning else { return }
        autoRunning = false
        autoStart = nil
        manualPicker = 1
        manualScroll = 0
        pickerTransition = nil
    }

    mutating func stopAuto(at date: Date, maxScroll: Double, reduced: Bool) {
        guard autoRunning else { return }
        let current = frame(at: date, maxScroll: maxScroll, reduced: reduced)
        // Match the RN takeover: the layer that was accepting touches stays fully present,
        // rather than leaving a partially transparent manual picker behind.
        manualPicker = current.pickerOpacity > 0.5 ? 1 : 0
        manualScroll = current.scroll
        autoRunning = false
        autoStart = nil
        pickerTransition = nil
    }

    mutating func pickProfile(_ index: Int, at date: Date, reduced: Bool) {
        guard index < 3 else { return }
        animatePicker(to: 0, at: date, reduced: reduced)
    }

    mutating func showPicker(at date: Date, reduced: Bool) {
        animatePicker(to: 1, at: date, reduced: reduced)
    }

    mutating func animatePicker(to target: Double, at date: Date, reduced: Bool) {
        let from = pickerOpacity(at: date)
        let duration = reduced ? 200.0 : 240.0
        manualPicker = target
        pickerTransition = PosterGlowCarouselPickerTransition(from: from, to: target, start: date, duration: duration)
    }

    mutating func finishPickerTransition() {
        if let transition = pickerTransition { manualPicker = transition.to }
        pickerTransition = nil
    }

    mutating func cancelPickerTransition(at date: Date) {
        guard pickerTransition != nil else { return }
        manualPicker = pickerOpacity(at: date)
        pickerTransition = nil
    }

    private func pickerOpacity(at date: Date) -> Double {
        guard let transition = pickerTransition else { return manualPicker }
        let progress = Self.clamp((date.timeIntervalSince(transition.start) * 1_000) / transition.duration)
        let eased = progress < 0.5 ? 2 * progress * progress : 1 - pow(-2 * progress + 2, 2) / 2
        return transition.from + (transition.to - transition.from) * eased
    }

    mutating func setManualScroll(_ raw: Double, maxScroll: Double) {
        if raw < 0 {
            manualScroll = raw * 0.55
        } else if raw > maxScroll {
            manualScroll = maxScroll + (raw - maxScroll) * 0.55
        } else {
            manualScroll = raw
        }
    }

    mutating func settleManualScroll(maxScroll: Double) {
        manualScroll = min(max(0, manualScroll), maxScroll)
    }

    mutating func setManualScrollClamped(_ value: Double, maxScroll: Double) {
        manualScroll = min(max(0, value), maxScroll)
    }

    func frame(at date: Date, maxScroll: Double, reduced: Bool) -> Frame {
        guard autoRunning else {
            return Frame(clock: 0,
                         scroll: manualScroll,
                         pickerOpacity: pickerOpacity(at: date),
                         autoPress: 0,
                         backPress: 0,
                         rowGeneration: rowGeneration)
        }

        let clock: Double
        if let autoStart {
            let elapsed = max(0, date.timeIntervalSince(autoStart) * 1_000)
            clock = elapsed.truncatingRemainder(dividingBy: Self.loopMilliseconds)
        } else {
            // The first timeline tick becomes the exact zero of the loop. The view's onAppear
            // also seeds this in normal use; keeping the fallback here avoids a transient jump.
            clock = 0
        }
        let fade = reduced ? 200.0 : 240.0
        let out = PosterGlowCarouselMotion.easeOut(PosterGlowCarouselMotion.ramp(clock, 1_460, fade), x1: 0.23, y1: 1, x2: 0.32, y2: 1)
        let back = PosterGlowCarouselMotion.easeOut(PosterGlowCarouselMotion.ramp(clock, 11_640, fade), x1: 0.23, y1: 1, x2: 0.32, y2: 1)
        let scroll = reduced ? 0 : scriptedScroll(clock, maxScroll: maxScroll)
        let autoPress = reduced ? 0 : press(clock, at: 1_300, inMilliseconds: 110, outMilliseconds: 150)
        let backPress = press(clock, at: 11_420, inMilliseconds: 80, outMilliseconds: 140)
        return Frame(clock: clock,
                     scroll: scroll,
                     pickerOpacity: Self.clamp(1 - out + back),
                     autoPress: autoPress,
                     backPress: backPress,
                     rowGeneration: rowGeneration)
    }

    private func scriptedScroll(_ clock: Double, maxScroll: Double) -> Double {
        let steps: [(at: Double, to: Double, ms: Double, home: Bool)] = [
            (2_900, 520, 720, false),
            (4_100, 936, 600, false),
            (5_100, 1_144, 520, false),
            (5_900, 1_664, 720, false),
            (6_800, 2_184, 720, false),
            (7_700, maxScroll, 760, false),
            (8_950, 0, 1_150, true),
        ]
        var y = 0.0
        var from = 0.0
        for step in steps {
            guard clock >= step.at else { break }
            let progress = Self.ramp(clock, step.at, step.ms)
            let eased = step.home
                ? Self.easeOut(progress, x1: 0.45, y1: 0, x2: 0.2, y2: 1)
                : Self.easeOut(progress, x1: 0.3, y1: 0.1, x2: 0.12, y2: 1)
            y = from + (step.to - from) * eased
            from = step.to
        }
        return y
    }

    private func press(_ time: Double, at: Double, inMilliseconds: Double, outMilliseconds: Double) -> Double {
        guard time >= at, time <= at + inMilliseconds + outMilliseconds else { return 0 }
        if time < at + inMilliseconds { return Self.ramp(time, at, inMilliseconds) }
        return 1 - Self.ramp(time, at + inMilliseconds, outMilliseconds)
    }

    static func ramp(_ time: Double, _ start: Double, _ duration: Double) -> Double {
        guard duration > 0 else { return time >= start ? 1 : 0 }
        return clamp((time - start) / duration)
    }

    static func clamp(_ value: Double) -> Double {
        min(1, max(0, value))
    }

    /// Invert x(t) with a short binary search, matching Reanimated's cubic-bezier easing.
    static func easeOut(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let x = clamp(progress)
        var low = 0.0
        var high = 1.0
        var u = x
        for _ in 0..<14 {
            let one = 1 - u
            let value = 3 * one * one * u * x1 + 3 * one * u * u * x2 + u * u * u
            if value < x { low = u } else { high = u }
            u = (low + high) / 2
        }
        return 3 * (1 - u) * (1 - u) * u * y1 + 3 * (1 - u) * u * u * y2 + u * u * u
    }
}

struct PosterGlowCarouselPickerTransition {
    let from: Double
    let to: Double
    let start: Date
    let duration: Double
}
