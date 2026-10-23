import Foundation

struct SkyScreenTimeHour: Equatable {
    var productive: Double
    var neutral: Double
    var distracted: Double
    var pickups: Int
    var notifications: Int
    var minutes: Double { productive + neutral + distracted }
    var quality: Double { minutes > 0 ? productive / minutes : 0.9 }
}

enum SkyScreenTimeData {
    static let today: [SkyScreenTimeHour] = {
        let totals = [3.0, 0, 13, 25, 0, 0, 0, 17, 16, 3, 0, 1.5, 6.5, 0.5, 2.8, 0, 4.1, 5, 1.5, 19, 0.5, 19.6, 0, 0]
        let pickups = [1, 0, 4, 7, 0, 0, 0, 5, 5, 1, 0, 1, 2, 0, 1, 0, 1, 2, 0, 5, 0, 5, 0, 0]
        let notifications = [1, 0, 2, 3, 0, 0, 0, 3, 2, 0, 0, 1, 1, 0, 2, 0, 3, 1, 0, 2, 0, 3, 0, 0]
        return totals.enumerated().map { index, total in
            let neutral = index == 2 ? 3.5 : (index == 7 ? 3.4 : 0)
            let distracted = index == 14 || index == 16 ? total : 0
            return SkyScreenTimeHour(productive: total - neutral - distracted, neutral: neutral,
                                     distracted: distracted, pickups: pickups[index], notifications: notifications[index])
        }
    }()

    static func hours(day: Int) -> [SkyScreenTimeHour] {
        guard day == 1 else { return today }
        return today.map {
            let total = $0.minutes * 1.2
            return SkyScreenTimeHour(productive: total * 0.62, neutral: total * 0.13,
                                     distracted: total * 0.25, pickups: $0.pickups + ($0.minutes > 5 ? 1 : 0),
                                     notifications: $0.notifications)
        }
    }

    static func total(day: Int) -> SkyScreenTimeHour {
        hours(day: day).reduce(SkyScreenTimeHour(productive: 0, neutral: 0, distracted: 0, pickups: 0, notifications: 0)) {
            SkyScreenTimeHour(productive: $0.productive + $1.productive, neutral: $0.neutral + $1.neutral,
                              distracted: $0.distracted + $1.distracted, pickups: $0.pickups + $1.pickups,
                              notifications: $0.notifications + $1.notifications)
        }
    }

    static func quality(hour: Double, day: Int) -> Double {
        let values = hours(day: day)
        let position = min(23, max(0, hour))
        let left = Int(position)
        let right = min(23, left + 1)
        return values[left].quality + (values[right].quality - values[left].quality) * (position - Double(left))
    }

    static func timeLabel(_ index: Int) -> String {
        let hour = index % 12 == 0 ? 12 : index % 12
        return "\(hour) \(index < 12 ? "AM" : "PM")"
    }

    static func duration(_ minutes: Double) -> String {
        let value = Int(minutes.rounded())
        return value >= 60 ? "\(value / 60)h\(String(format: "%02d", value % 60))" : "\(value)m"
    }
}

struct SkyScreenTimeFrame: Equatable {
    var hour: Double = 3
    var selection: Double = 0
    var quality: Double = 0.9
    var storm: Double = 0
    var focusSeconds: Int = 0
    var selectedIndex: Int? { selection >= 0.5 ? min(23, max(0, Int(hour.rounded()))) : nil }
    var title: String { quality >= 0.75 ? "Productive" : (quality >= 0.45 ? "Balanced" : "Distracted") }
    var titleOpacities: [Double] {
        func fade(_ value: Double) -> Double {
            let t = min(1, max(0, value))
            return t * t * (3 - 2 * t)
        }
        let productive = fade((quality - 0.71) / 0.08)
        let distracted = 1 - fade((quality - 0.41) / 0.08)
        return [productive, 1 - productive - distracted, distracted]
    }
}

/// Scripted time freezes in the background; the local focus deadline keeps real elapsed time.
final class SkyScreenTimeMotion {
    enum Cadence: Equatable {
        case stopped, seconds, animation
        var interval: Double { self == .seconds ? 1 : 1 / 60 }
    }

    private(set) var day = 0
    private(set) var autoPlay = false
    private(set) var quiet = false
    private(set) var active = true
    private var appeared = false
    private var origin = 0.0
    private var pausedAt: Double?
    private var selectedHour: Double?
    private var fingerDown = false
    private var interruptedAt: Double?
    private var returnStarted: Double?
    private var returnFrame = SkyScreenTimeFrame()
    private var resetRequested = false
    private var focusStarted: Double?
    static let focusDuration = 25 * 60

    private func logicalTime(_ at: Double) -> Double { pausedAt ?? at }

    func appear(autoPlay: Bool, quiet: Bool, at: Double) {
        guard !appeared else { return }
        appeared = true
        self.autoPlay = autoPlay
        self.quiet = quiet
        origin = logicalTime(at)
    }

    func setAutoPlay(_ enabled: Bool, at: Double) {
        autoPlay = enabled
        let now = logicalTime(at)
        origin = now
        if !enabled && !resetRequested { returnStarted = nil }
        if selectedHour != nil { interruptedAt = now }
    }

    func setQuiet(_ enabled: Bool, at: Double) {
        guard quiet != enabled else { return }
        if enabled && resetRequested { clearSelection(at: at) }
        quiet = enabled
        returnStarted = nil
        resetRequested = false
        origin = logicalTime(at)
        if selectedHour != nil { interruptedAt = logicalTime(at) }
    }

    func setActive(_ enabled: Bool, at: Double) {
        guard active != enabled else { return }
        if enabled {
            let pause = max(0, at - (pausedAt ?? at))
            origin += pause
            interruptedAt = interruptedAt.map { $0 + pause }
            returnStarted = returnStarted.map { $0 + pause }
            pausedAt = nil
        } else {
            let wasScrubbing = fingerDown
            pausedAt = at
            fingerDown = false
            if wasScrubbing && selectedHour != nil { interruptedAt = at }
        }
        active = enabled
    }

    func beginScrub(at: Double) {
        let current = frame(at: at)
        selectedHour = current.hour
        fingerDown = true
        interruptedAt = nil
        returnStarted = nil
        resetRequested = false
    }

    func scrub(hour: Double) { selectedHour = min(23, max(0, hour)) }

    func endScrub(at: Double) {
        fingerDown = false
        interruptedAt = logicalTime(at)
    }

    func chooseDay(_ index: Int, at: Double) {
        day = min(1, max(0, index))
        clearSelection(at: at)
    }

    func reset(at: Double) {
        let current = frame(at: at)
        guard !quiet && current.selection > 0.001 else { clearSelection(at: at); return }
        selectedHour = current.hour
        fingerDown = false
        returnFrame = current
        returnStarted = logicalTime(at)
        resetRequested = true
    }

    private func clearSelection(at: Double) {
        selectedHour = nil
        interruptedAt = nil
        returnStarted = nil
        fingerDown = false
        resetRequested = false
        origin = logicalTime(at)
    }

    func toggleFocus(at: Double) { focusStarted = remainingFocus(at: at) > 0 ? nil : at }

    func remainingFocus(at: Double) -> Int {
        guard let start = focusStarted else { return 0 }
        return max(0, Self.focusDuration - Int(max(0, at - start)))
    }

    func frame(at: Double) -> SkyScreenTimeFrame {
        let now = logicalTime(at)
        var result: SkyScreenTimeFrame
        if let hour = selectedHour {
            let ratio = SkyScreenTimeData.quality(hour: hour, day: day)
            result = SkyScreenTimeFrame(hour: hour, selection: 1, quality: ratio, storm: Self.storm(ratio))
            let idleDeadline = interruptedAt.map { $0 + 2 }
            if resetRequested || (autoPlay && !quiet && !fingerDown && now >= (idleDeadline ?? .infinity)) {
                if returnStarted == nil { returnStarted = idleDeadline ?? now; returnFrame = result }
                let t = Self.smooth((now - (returnStarted ?? now)) / 0.5)
                let daily = SkyScreenTimeData.total(day: day).quality
                result.selection = returnFrame.selection * (1 - t)
                result.quality = returnFrame.quality + (daily - returnFrame.quality) * t
                result.storm = Self.storm(result.quality)
                if t >= 1 { clearSelection(at: at); result = Self.demoFrame(at: 0, day: day) }
            }
        } else if autoPlay && !quiet {
            result = Self.demoFrame(at: max(0, now - origin), day: day)
        } else {
            let ratio = SkyScreenTimeData.total(day: day).quality
            result = SkyScreenTimeFrame(quality: ratio, storm: Self.storm(ratio))
        }
        if quiet { result.storm = 0 }
        result.focusSeconds = remainingFocus(at: at)
        return result
    }

    func cadence(at: Double) -> Cadence {
        guard active else { return .stopped }
        if resetRequested && !quiet { return .animation }
        if autoPlay && !quiet && !fingerDown { return .animation }
        return remainingFocus(at: at) > 0 ? .seconds : .stopped
    }

    static func demoFrame(at: Double, day: Int = 0) -> SkyScreenTimeFrame {
        let t = max(0, at).truncatingRemainder(dividingBy: 8)
        let daily = SkyScreenTimeData.total(day: day).quality
        var hour = 3.0
        var alpha = 0.0
        if t >= 1.3 && t < 1.7 { alpha = smooth((t - 1.3) / 0.4) }
        else if t >= 1.7 && t < 3.3 { alpha = 1; hour = 3 + 11 * smooth((t - 1.7) / 1.6) }
        else if t >= 3.3 && t < 4.4 { alpha = 1; hour = 14 }
        else if t >= 4.4 && t < 5.9 { alpha = 1; hour = 14 + 7 * smooth((t - 4.4) / 1.5) }
        else if t >= 5.9 && t < 6.6 { hour = 21; alpha = 1 - smooth((t - 5.9) / 0.7) }
        let ratio = daily + (SkyScreenTimeData.quality(hour: hour, day: day) - daily) * alpha
        return SkyScreenTimeFrame(hour: hour, selection: alpha, quality: ratio, storm: storm(ratio))
    }

    private static func storm(_ quality: Double) -> Double { min(1, max(0, (0.9 - quality) / 0.9)) }
    private static func smooth(_ value: Double) -> Double {
        let t = min(1, max(0, value))
        return t * t * (3 - 2 * t)
    }
}
