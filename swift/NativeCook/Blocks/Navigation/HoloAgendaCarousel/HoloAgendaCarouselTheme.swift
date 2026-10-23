import Foundation

/// Measurements are in the reference's 402 × 778 point safe-area stage.
enum HoloAgendaCarouselTheme {
    static let loop = 13333.0
    static let beat = loop / 4
    static let phases = [0.84, 0.55, 0.56, 0.904]
    static let sessions = [
        Session(label: "WELCOME", overline: "DAY 1 · 09:00", title: "Opening welcome", body: "Doors, coffee and a first look at what the next two days hold.", name: "Mira Solène", role: "Host, Iridia", initials: "MS"),
        Session(label: "2ND DAY", overline: "DAY 2 · 09:30", title: "Second day kickoff", body: "A recap of day one, then straight into the workshops.", name: "Kenji Marlow", role: "Programme lead", initials: "KM"),
        Session(label: "EXPERIENCE", overline: "DAY 2 · 14:00", title: "The experience lab", body: "Hands-on stations: light, sound and motion you can touch.", name: "Ines Okoro", role: "Studio director", initials: "IO"),
        Session(label: "KEYNOTE", overline: "DAY 2 · 17:30", title: "Closing keynote", body: "One idea, forty minutes, and the reason we all came.", name: "Avery Lindqvist", role: "Founder", initials: "AL")
    ]

    struct Session {
        let label: String
        let overline: String
        let title: String
        let body: String
        let name: String
        let role: String
        let initials: String
    }

    struct Pose {
        let x: Double
        let side: Double
        let opacity: Double
        let z: Double
    }

    struct AutoState {
        var pos: Double
        var squeeze: Double
        var buttonScale: Double
        var demoAdded: Bool
        var velocity = 0.0
        var arrival = 0.0
        var age = 900.0
    }

    static func clamp(_ n: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double {
        max(lo, min(hi, n))
    }

    static func mod(_ n: Double, _ divisor: Double = 4) -> Double {
        (n.truncatingRemainder(dividingBy: divisor) + divisor).truncatingRemainder(dividingBy: divisor)
    }

    static func wrap(_ n: Double) -> Double { mod(n + 2) - 2 }
    static func rounded(_ n: Double) -> Double { floor(n + 0.5) }

    static func smooth(_ lo: Double, _ hi: Double, _ n: Double) -> Double {
        let t = clamp((n - lo) / (hi - lo))
        return t * t * (3 - 2 * t)
    }

    static func curve(_ a: Double, _ values: [Double], _ slopes: [Double]) -> Double {
        let j = min(3, Int(floor(a * 2)))
        let t = clamp(a * 2 - Double(j))
        let t2 = t * t
        let t3 = t2 * t
        return (2 * t3 - 3 * t2 + 1) * values[j] + (t3 - 2 * t2 + t) * slopes[j] / 2
            + (-2 * t3 + 3 * t2) * values[j + 1] + (t3 - t2) * slopes[j + 1] / 2
    }

    static func pose(_ d: Double) -> Pose {
        let a = abs(d)
        return Pose(
            x: (d == 0 ? 0 : d > 0 ? 1 : -1) * curve(a, [0, 80, 117, 100, 56], [180, 101, 0, -49, -88]),
            side: curve(a, [192, 164, 136, 120, 104], [0, -56, -40, -32, -32]),
            opacity: 1 - smooth(1.6, 1.95, a),
            z: rounded((2 - a) * 1000) * 2 + (d >= 0 ? 1 : 0)
        )
    }

    static func bezier(_ x: Double, _ x1: Double = 0.23, _ y1: Double = 1,
                       _ x2: Double = 0.32, _ y2: Double = 1) -> Double {
        let x = clamp(x)
        var lo = 0.0
        var hi = 1.0
        var t = x
        for _ in 0..<18 {
            let inv = 1 - t
            let bx = 3 * inv * inv * t * x1 + 3 * inv * t * t * x2 + t * t * t
            if bx < x { lo = t } else { hi = t }
            t = (lo + hi) / 2
        }
        let inv = 1 - t
        return x == 0 || x == 1 ? x : 3 * inv * inv * t * y1 + 3 * inv * t * t * y2 + t * t * t
    }

    static func autoplay(_ ms: Double) -> AutoState {
        let loops = floor(ms / loop)
        let time = mod(ms, loop)
        let beatIndex = floor(time / beat)
        let local = time - beatIndex * beat
        var advance = 0.0
        var squeeze = 1.0
        if local >= 2400 {
            if local < 2530 {
                let p = smooth(0, 1, (local - 2400) / 130)
                advance = 0.03 * p
                squeeze = 1 - 0.03 * p
            } else {
                let p = bezier((local - 2530) / 800, 0.04, 0.85, 0.55, 1)
                advance = 0.03 + 0.97 * p
                squeeze = 0.97 + 0.03 * p
            }
        }
        let sincePress = local - 700
        let buttonScale = beatIndex == 2 && sincePress >= 0 && sincePress < 240
            ? 1 - 0.03 * sin(sincePress / 240 * .pi) : 1
        return AutoState(pos: loops * 4 + beatIndex + advance, squeeze: squeeze,
                         buttonScale: buttonScale,
                         demoAdded: (beatIndex == 2 && local >= 820) || (beatIndex == 3 && local < 3000))
    }

    static let crossings: [Double] = (0..<4).map { index in
        var lo = Double(index) * beat
        var hi = Double(index + 1) * beat
        for _ in 0..<32 {
            let mid = (lo + hi) / 2
            if autoplay(mid).pos < Double(index) + 0.5 { lo = mid } else { hi = mid }
        }
        return (lo + hi) / 2
    }

    static func dynamics(_ ms: Double) -> AutoState {
        var state = autoplay(ms)
        var velocity = 0.0
        var weightTotal = 0.0
        for j in 0..<14 {
            let lag = Double(j) * 25
            let weight = exp(-lag / 70)
            let sample = (autoplay(ms - lag).pos - autoplay(ms - lag - 8).pos) * 125
            velocity += clamp(sample, -8, 8) * weight
            weightTotal += weight
        }
        state.velocity = velocity / weightTotal
        let time = mod(ms, loop)
        var last = crossings[3] - loop
        for crossing in crossings where time >= crossing { last = crossing }
        state.arrival = 1 - bezier((time - last) / 900)
        state.age = min(900, time - last)
        return state
    }

    static func releaseTarget(_ position: Double, _ start: Double, _ velocity: Double) -> Double {
        let origin = rounded(start)
        let projected = position + velocity * 0.16
        let limit = abs(velocity) > 7 && abs(projected - origin) > 1.6 ? 2.0 : 1.0
        return clamp(rounded(projected), origin - limit, origin + limit)
    }

    static func arrivalScale(_ age: Double) -> Double {
        1 + 0.02 * sin(.pi * clamp(age / 420))
    }
}
