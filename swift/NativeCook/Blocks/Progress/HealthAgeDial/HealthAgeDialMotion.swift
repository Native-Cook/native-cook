import Foundation

enum HealthAgeDialMotion {
    static let loop = 10.0
    static let resumeAfter = 2.0
    static let returnDuration = 0.8

    enum Time {
        static let retractStart = 1.6
        static let retractEnd = 2.15
        static let rewindStart = 2.35
        static let scrollDownStart = 3.9
        static let scrollDownEnd = 5.3
        static let revealStart = 5.0
        static let scrollUpStart = 7.2
        static let scrollUpEnd = 8.6
        static let revealFadeStart = 8.1
        static let revealFadeEnd = 8.5
    }

    enum Spring {
        static let dot = (omega: 11.0, zeta: 0.85)
        static let pop = (omega: 16.0, zeta: 0.55)
    }

    enum Trail {
        static let seconds = 0.14
        static let maximum = 64.0
        static let fade = 0.47
    }

    enum Reveal {
        static let line = 0.9
        static let guidesAt = 0.65
        static let guides = 0.3
        static let countAt = [0.25, 0.31]
        static let count = 0.8
        static let badgeAt = [0.25, 0.29]
        static let fade = 0.25
    }

    enum Land {
        static let amplitude = 0.1
        static let decay = 6.0
        static let frequency = 14.0
        static let peak = 0.5577
    }

    enum Flight {
        static let numberFadeEnd = 0.32
        static let crossfadeStart = 0.86
    }

    enum Roll {
        static let window = 0.36
        static let travel = 0.55
        static let falloff = 1.9
    }

    enum Sign {
        static let from = 0.03
        static let to = 0.06
    }

    enum Press {
        static let scale = 0.97
        static let pressIn = 0.1
        static let release = 0.2
    }

    struct LoopFrame {
        let scroll: Double
        let years: Double
        let velocity: Double
        let reveal: Double
        let revealOpacity: Double
    }

    static func clamp(_ value: Double) -> Double { min(max(value, 0), 1) }
    static func segment(_ value: Double, _ from: Double, _ to: Double) -> Double {
        clamp((value - from) / (to - from))
    }
    static func lerp(_ from: Double, _ to: Double, _ progress: Double) -> Double {
        from + (to - from) * progress
    }
    static func smoothstep(_ value: Double) -> Double {
        let x = clamp(value)
        return x * x * (3 - 2 * x)
    }
    static func rollStep(_ value: Double) -> Double {
        let integer = floor(value)
        let fraction = value - integer
        return integer + smoothstep((fraction - 0.5 + Roll.window / 2) / Roll.window)
    }

    static func bezier(_ value: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x = clamp(value)
        if x == 0 || x == 1 { return x }
        var low = 0.0
        var high = 1.0
        for _ in 0..<20 {
            let t = (low + high) / 2
            let inverse = 1 - t
            let at = 3 * inverse * inverse * t * x1 + 3 * inverse * t * t * x2 + t * t * t
            if at < x { low = t } else { high = t }
        }
        let t = (low + high) / 2
        let inverse = 1 - t
        return 3 * inverse * inverse * t * y1 + 3 * inverse * t * t * y2 + t * t * t
    }

    static func easeOut(_ value: Double) -> Double { bezier(value, 0.23, 1, 0.32, 1) }
    static func easeInOut(_ value: Double) -> Double { bezier(value, 0.77, 0, 0.175, 1) }

    static func springDisplacement(_ seconds: Double, displacement: Double, velocity: Double,
                                   spring: (omega: Double, zeta: Double)) -> Double {
        guard seconds > 0 else { return displacement }
        let damped = spring.omega * sqrt(1 - spring.zeta * spring.zeta)
        let envelope = exp(-spring.zeta * spring.omega * seconds)
        if envelope < 0.00001 { return 0 }
        let sine = (velocity + spring.zeta * spring.omega * displacement) / damped
        return envelope * (displacement * cos(damped * seconds) + sine * sin(damped * seconds))
    }

    static func spring(_ seconds: Double, spring: (omega: Double, zeta: Double)) -> Double {
        guard seconds > 0 else { return 0 }
        return 1 - springDisplacement(seconds, displacement: 1, velocity: 0, spring: spring)
    }

    static func pop(_ seconds: Double) -> (scale: Double, opacity: Double) {
        guard seconds > 0 else { return (0.85, 0) }
        return (0.85 + 0.15 * spring(seconds, spring: Spring.pop), easeOut(seconds / 0.15))
    }

    static func landBump(_ seconds: Double) -> Double {
        guard seconds > 0, seconds <= 1.2 else { return 0 }
        return exp(-Land.decay * seconds) * sin(Land.frequency * seconds) / Land.peak
    }

    static func scroll(at time: Double, historyOffset: Double) -> Double {
        if time < Time.scrollDownStart { return 0 }
        if time < Time.scrollDownEnd {
            return historyOffset * easeInOut(segment(time, Time.scrollDownStart, Time.scrollDownEnd))
        }
        if time < Time.scrollUpStart { return historyOffset }
        if time < Time.scrollUpEnd {
            return historyOffset * (1 - easeInOut(segment(time, Time.scrollUpStart, Time.scrollUpEnd)))
        }
        return 0
    }

    static func years(at time: Double, rest: Double) -> Double {
        if time < Time.retractStart { return rest }
        if time < Time.rewindStart {
            return rest * (1 - easeInOut(segment(time, Time.retractStart, Time.retractEnd)))
        }
        return rest * spring(time - Time.rewindStart, spring: Spring.dot)
    }

    static func trail(for velocity: Double) -> Double {
        let length = min(Trail.maximum, abs(velocity) * Trail.seconds)
        return velocity > 0 ? -length : length
    }

    static func landingTime(offset: Double, historyOffset: Double) -> Double {
        var low = Time.scrollDownStart
        var high = Time.scrollDownEnd
        for _ in 0..<40 {
            let middle = (low + high) / 2
            if scroll(at: middle, historyOffset: historyOffset) < offset { low = middle } else { high = middle }
        }
        return (low + high) / 2
    }

    static func loopFrame(at time: Double, rest: Double, degreesPerYear: Double,
                          historyOffset: Double) -> LoopFrame {
        let dt = 1 / 240.0
        let yearValue = years(at: time, rest: rest)
        let previous = years(at: max(0, time - dt), rest: rest)
        let velocity = ((yearValue - previous) / dt) * degreesPerYear
        let reveal = time >= Time.revealStart && time < Time.scrollUpEnd ? time - Time.revealStart : -1
        return LoopFrame(
            scroll: scroll(at: time, historyOffset: historyOffset),
            years: yearValue,
            velocity: velocity,
            reveal: reveal,
            revealOpacity: time < Time.scrollUpEnd ? 1 - segment(time, Time.revealFadeStart, Time.revealFadeEnd) : 1
        )
    }
}
