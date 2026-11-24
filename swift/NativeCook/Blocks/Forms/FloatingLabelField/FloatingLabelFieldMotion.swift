import SwiftUI

/*
 Floating Label Field: the script and the clock of the demo.

 Everything the loop shows is a pure function of the loop time `t` (seconds, 0 ..< loop): the
 three fields' poses, the button and the ghost finger. A person's typing produces the same poses
 from tweens, so the field never knows which of the two drives it.
*/
enum FloatingLabelFieldMotion {
    static let loop: Double = 11
    static let idle: Double = 2
    static let returnLen: Double = 0.75
    static let startDelay: Double = 0.35
    static let tFilled: Double = 8.1

    static let name = "Mara Lindqvist"
    static let emailTypo = "mara@larchco"
    static let email = "mara@larch.co"
    static let password = "Tamarack42#"

    static let focusAt: [Double] = [0.85, 2.25, 5.05]
    static let blurAt: [Double] = [2.25, 5.05, 8.35]
    private static let nameTypeAt = 1.05
    private static let emailTypeAt = 2.5
    private static let keyGap = 0.075
    static let submitAt = 3.55
    private static let fixAt = 4.35
    private static let passwordTypeAt = 5.3
    private static let passwordKeyGap = 0.15
    private static let readyAt = 7.0
    private static let gleamAt = 7.25
    private static let gleamLen = 0.8
    private static let tapAt = 8.35
    private static let doneAt = 8.5
    private static let resetAt = 9.3
    private static let clearAt = 9.55
    private static let sinkAt = 9.5

    static let focusS = 0.2
    static let floatS = 0.42
    static let drawS = 0.6
    static let retractS = 0.45
    static let checkS = 0.35
    static let errorS = 0.3
    static let errorClearS = 0.22
    static let shakeS = 0.45
    static let dotS = 0.14
    static let strengthS = 0.3
    static let readyS = 0.4
    static let gleamS = 0.8

    struct FieldPose {
        var focus = 0.0, float = 0.0, lineStart = 0.0, lineEnd = 0.0, valid = 0.0, error = 0.0, shake = 0.0
        var frame = -1
        var count = 0.0, caret = 0.0, strength = 0.0, text = 1.0
    }

    struct ButtonPose { var ready = 0.0, gleam = 0.0, press = 0.0, done = 0.0 }
    struct DotPose { var target = 0, alpha = 0.0, scale = 1.0 }

    // MARK: Validation

    static func nameOk(_ v: String) -> Bool {
        v.range(of: #"^\s*\S{2,}\s+\S{2,}"#, options: .regularExpression) != nil
    }

    static func emailOk(_ v: String) -> Bool {
        v.trimmingCharacters(in: .whitespacesAndNewlines)
            .range(of: #"^[^\s@]+@[^\s@]+\.[^\s@]{2,}$"#, options: .regularExpression) != nil
    }

    static func passwordLevel(_ v: String) -> Int {
        let length = v.utf16.count
        if length == 0 { return 0 }
        var points = 0
        if length >= 6 { points += 1 }
        if length >= 10 { points += 1 }
        if v.range(of: "[a-z]", options: .regularExpression) != nil,
           v.range(of: "[A-Z]", options: .regularExpression) != nil { points += 1 }
        if v.range(of: "[0-9]", options: .regularExpression) != nil { points += 1 }
        if v.range(of: "[^A-Za-z0-9]", options: .regularExpression) != nil { points += 1 }
        if points <= 1 { return 1 }
        if points == 2 { return 2 }
        if points <= 4 { return 3 }
        return 4
    }

    static func fieldOk(_ i: Int, _ v: String) -> Bool {
        if i == 0 { return nameOk(v) }
        if i == 1 { return emailOk(v) }
        return passwordLevel(v) >= 3
    }

    static func fieldMessage(_ i: Int, _ v: String) -> String {
        let empty = v.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty
        if i == 0 { return empty ? "Enter your name" : "Add your first and last name" }
        if i == 1 {
            if empty { return "Enter your email" }
            if !v.contains("@") { return "An email address needs an @" }
            return "That address needs a domain, like larch.co"
        }
        return v.isEmpty ? "Choose a password" : "Too easy to guess. Add a number or a symbol"
    }

    // MARK: Script

    private static func typed(_ text: String, _ at: Double, _ gap: Double) -> [(String, Double)] {
        let chars = Array(text)
        return (1...chars.count).map { k in (String(chars[0..<k]), at + Double(k - 1) * gap) }
    }

    private static let steps: [[(String, Double)]] = [
        typed(name, nameTypeAt, keyGap),
        typed(emailTypo, emailTypeAt, keyGap) + [
            ("mara@larchc", fixAt),
            ("mara@larch", fixAt + 0.12),
            ("mara@larch.", fixAt + 0.29),
            ("mara@larch.c", fixAt + 0.39),
            (email, fixAt + 0.49),
        ],
        typed(password, passwordTypeAt, passwordKeyGap),
    ]

    static let values: [[String]] = steps.map { [""] + $0.map(\.0) }
    static let times: [[Double]] = steps.map { [-1] + $0.map(\.1) }

    private static let validAt: [Double] = (0..<3).map { i in
        var k = values[i].count - 1
        if !fieldOk(i, values[i][k]) { return 1e9 }
        while k > 0 && fieldOk(i, values[i][k - 1]) { k -= 1 }
        return times[i][k]
    }

    private static let levelAt: [Double] = (0...4).map { level in
        guard let k = values[2].firstIndex(where: { passwordLevel($0) >= level }) else { return 1e9 }
        return times[2][k]
    }

    // MARK: Curves

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : (x > 1 ? 1 : x) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutCubic(_ x: Double) -> Double { let u = 1 - x; return 1 - u * u * u }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func easeOutBack(_ x: Double) -> Double {
        let c = 1.70158
        let u = x - 1
        return 1 + (c + 1) * u * u * u + c * u * u
    }
    static func shakeX(_ p: Double) -> Double {
        if p <= 0 || p >= 1 { return 0 }
        let u = 1 - p
        return 9 * sin(p * .pi * 2 * 3) * u * u
    }

    static func frameAt(_ i: Int, _ t: Double) -> Int {
        if t >= clearAt { return 0 }
        var k = 0
        for j in 1..<times[i].count where t >= times[i][j] { k = j }
        return k
    }

    // MARK: Loop

    static func autoField(_ i: Int, _ t: Double) -> FieldPose {
        let f0 = focusAt[i]
        let b0 = blurAt[i]
        let k = frameAt(i, t)
        let gone = 1 - seg(t, resetAt, resetAt + 0.25)
        let sinkFrom = sinkAt + Double(i) * 0.06
        let sink = 1 - easeInOutCubic(seg(t, sinkFrom, sinkFrom + floatS))

        var error = 0.0
        var shake = 0.0
        if i == 1 {
            error = easeOutCubic(seg(t, submitAt, submitAt + errorS)) * (1 - seg(t, fixAt, fixAt + errorClearS))
            shake = seg(t, submitAt, submitAt + shakeS)
        }

        var count = 0.0
        var strength = 0.0
        if i == 2 {
            if k >= 1 { for j in 1...k { count += easeOutCubic(seg(t, times[2][j], times[2][j] + dotS)) } }
            for level in 1...4 { strength += easeOutCubic(seg(t, levelAt[level], levelAt[level] + strengthS)) }
            strength *= 1 - easeInOutCubic(seg(t, resetAt, resetAt + 0.3))
        }

        let since = t - max(times[i][k], f0)
        let blink = since < 0.5 ? 1 : clamp01(0.5 + 1.6 * cos((2 * .pi * (since - 0.5)) / 1.06))
        let caret = seg(t, f0 + 0.22, f0 + 0.34) * (1 - seg(t, b0 - 0.04, b0 + 0.04)) * blink

        var p = FieldPose()
        p.focus = easeOutCubic(seg(t, f0, f0 + focusS)) * (1 - seg(t, b0, b0 + focusS))
        p.float = easeOutCubic(seg(t, f0, f0 + floatS)) * sink
        p.lineStart = easeInOutCubic(seg(t, b0, b0 + retractS))
        p.lineEnd = easeInOutCubic(seg(t, f0, f0 + drawS))
        p.valid = easeOutCubic(seg(t, validAt[i], validAt[i] + checkS)) * gone
        p.error = error
        p.shake = shake
        p.frame = k
        p.count = count
        p.caret = caret
        p.strength = strength
        p.text = t >= clearAt ? 1 : gone
        return p
    }

    static func autoButton(_ t: Double) -> ButtonPose {
        ButtonPose(
            ready: easeInOutCubic(seg(t, readyAt, readyAt + readyS)) * (1 - easeInOutCubic(seg(t, resetAt + 0.15, resetAt + 0.55))),
            gleam: seg(t, gleamAt, gleamAt + gleamLen),
            press: seg(t, tapAt, tapAt + 0.1) * (1 - seg(t, tapAt + 0.12, tapAt + 0.3)),
            done: easeOutCubic(seg(t, doneAt, doneAt + 0.35)) * (1 - seg(t, resetAt, resetAt + 0.3))
        )
    }

    static func autoDot(_ t: Double) -> DotPose {
        let taps = [focusAt[0], focusAt[1], focusAt[2], tapAt]
        var pose = DotPose()
        for (k, at) in taps.enumerated() {
            let a = seg(t, at - 0.24, at - 0.06) * (1 - seg(t, at + 0.12, at + 0.36))
            if a > pose.alpha {
                pose.alpha = a
                pose.target = k
                pose.scale = 1 - 0.22 * sin(.pi * seg(t, at - 0.06, at + 0.16))
            }
        }
        return pose
    }
}

// MARK: - Tweens for real input

enum FloatingLabelFieldEase {
    case linear, outCubic, inOutCubic

    func callAsFunction(_ x: Double) -> Double {
        switch self {
        case .linear: return x
        case .outCubic: return FloatingLabelFieldMotion.easeOutCubic(x)
        case .inOutCubic: return FloatingLabelFieldMotion.easeInOutCubic(x)
        }
    }
}

/// A value that moves over time and is read at any instant, so no frame writes state.
struct FloatingLabelFieldTween {
    private var from: Double
    private var target: Double
    private var start = 0.0
    private var duration = 0.0
    private var ease = FloatingLabelFieldEase.linear

    init(_ value: Double = 0) { from = value; target = value }

    var goal: Double { target }

    func value(_ now: Double) -> Double {
        if duration <= 0 { return now < start ? from : target }
        if now <= start { return from }
        if now >= start + duration { return target }
        return from + (target - from) * ease((now - start) / duration)
    }

    mutating func set(_ v: Double) { from = v; target = v; duration = 0; start = 0 }

    mutating func to(_ v: Double, _ seconds: Double, _ curve: FloatingLabelFieldEase = .linear, now: Double, delay: Double = 0) {
        from = value(now)
        target = v
        start = now + delay
        duration = seconds
        ease = curve
    }
}

// MARK: - Colour

struct FloatingLabelFieldRGB {
    let r: Double, g: Double, b: Double

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 255) / 255
        g = Double((hex >> 8) & 255) / 255
        b = Double(hex & 255) / 255
    }

    init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: 1) }

    /// Reanimated's interpolateColor mixes in gamma 2.2.
    func mix(_ o: FloatingLabelFieldRGB, _ t: Double) -> FloatingLabelFieldRGB {
        func m(_ a: Double, _ b: Double) -> Double {
            pow(pow(a, 2.2) + (pow(b, 2.2) - pow(a, 2.2)) * t, 1 / 2.2)
        }
        return FloatingLabelFieldRGB(r: m(r, o.r), g: m(g, o.g), b: m(b, o.b))
    }
}
