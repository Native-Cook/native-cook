import SwiftUI

// MARK: - Curves

enum CreditCardFlipFormCurve {
    /// Reanimated's default timing curve, `Easing.inOut(Easing.quad)`.
    static let inOutQuad = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))
    static let easeInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let easeOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.23, y: 1), endControlPoint: UnitPoint(x: 0.32, y: 1))
    /// A reel catching on its detent: overshoots by a hair and settles.
    static let spin = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.16, y: 0.9), endControlPoint: UnitPoint(x: 0.3, y: 1.06))
    /// CSS's default `ease`, used by the CVV ring's pulse.
    static let cssEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))

    static func outQuad(_ k: Double) -> Double { 1 - (1 - k) * (1 - k) }
}

// MARK: - Tween

/// A number moving to a target on a curve, sampled against the clock.
struct CreditCardFlipFormTween {
    private var from: Double
    private(set) var target: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve = CreditCardFlipFormCurve.inOutQuad

    init(_ value: Double) {
        from = value
        target = value
    }

    var end: Date { start.addingTimeInterval(duration) }

    func value(at date: Date) -> Double {
        guard duration > 0 else { return target }
        let k = min(max(date.timeIntervalSince(start) / duration, 0), 1)
        return from + (target - from) * curve.value(at: k)
    }

    mutating func animate(to value: Double, at date: Date, duration: Double, curve: UnitCurve = CreditCardFlipFormCurve.inOutQuad) {
        from = self.value(at: date)
        target = value
        start = date
        self.duration = duration
        self.curve = curve
    }

    mutating func set(_ value: Double) {
        from = value
        target = value
        duration = 0
    }
}

// MARK: - Spring

/// Reanimated's `withSpring({ duration, dampingRatio })` in closed form: the stiffness is solved
/// (mass 1) so the energy falls to Reanimated's threshold after 1.5 × the duration; a new target
/// starts from the current value and velocity, as Reanimated hands velocity on.
struct CreditCardFlipFormSpring {
    private var from: Double
    private var velocity: Double = 0
    private(set) var target: Double
    private var start = Date.distantPast
    private let omega: Double
    private let zeta: Double
    private let settle: Double

    init(_ value: Double, duration: Double, dampingRatio: Double) {
        from = value
        target = value
        zeta = dampingRatio
        omega = sqrt(CreditCardFlipFormSpring.stiffness(duration: duration, dampingRatio: dampingRatio))
        settle = duration * 1.5
    }

    var end: Date { start.addingTimeInterval(settle) }

    private func state(at date: Date) -> (x: Double, v: Double) {
        let t = date.timeIntervalSince(start)
        if t <= 0 { return (from, velocity) }
        if t >= settle { return (target, 0) }
        let x0 = from - target
        let v0 = velocity
        let envelope = exp(-zeta * omega * t)
        if zeta < 1 {
            let wd = omega * sqrt(1 - zeta * zeta)
            let a = (v0 + zeta * omega * x0) / wd
            let x = envelope * (x0 * cos(wd * t) + a * sin(wd * t))
            let v = -zeta * omega * x + envelope * (-x0 * wd * sin(wd * t) + a * wd * cos(wd * t))
            return (target + x, v)
        }
        let b = v0 + omega * x0
        let x = envelope * (x0 + b * t)
        return (target + x, -omega * x + envelope * b)
    }

    func value(at date: Date) -> Double { state(at: date).x }

    mutating func animate(to value: Double, at date: Date) {
        let now = state(at: date)
        from = now.x
        velocity = now.v
        // Velocity that would carry the first frame away from the target is dropped.
        if (value > from && velocity < 0) || (value < from && velocity > 0) { velocity = 0 }
        target = value
        start = date
    }

    mutating func set(_ value: Double) {
        from = value
        target = value
        velocity = 0
        start = .distantPast
    }

    /// Lets go at `value` moving at `speed` (per s), springing to `target`; the speed is kept as given.
    mutating func release(from value: Double, speed: Double, to target: Double, at date: Date) {
        from = value
        velocity = speed
        self.target = target
        start = date
    }

    static func stiffness(duration: Double, dampingRatio zeta: Double) -> Double {
        let threshold = 6e-9
        let settling = duration * 1.5
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (1 + w * settling) * exp(-w * settling)
            let vt = xt * -w + w * exp(-w * settling)
            return (0.5 * k * xt * xt + 0.5 * vt * vt) / (0.5 * k) - threshold
        }
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > threshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        return k.isFinite ? k : 170
    }
}

// MARK: - Reel

/// One character on the card, on a reel of its placeholder and the digits 0–9 twice. A typed digit
/// spins down through the first run of digits to its place in the second (11 + digit), so even a 0
/// rolls a full turn; deleting it spins back up to the placeholder. While it spins it dims.
struct CreditCardFlipFormReel {
    private struct Leg {
        var from: Double
        var to: Double
        var start: Date
        var duration: Double
        var speedFrom: Double

        func roll(at date: Date) -> Double {
            guard duration > 0 else { return to }
            let k = min(max(date.timeIntervalSince(start) / duration, 0), 1)
            return from + (to - from) * CreditCardFlipFormCurve.spin.value(at: k)
        }

        func speed(at date: Date) -> Double {
            guard duration > 0 else { return 0 }
            let e = date.timeIntervalSince(start)
            if e <= 0 { return speedFrom }
            if e < 0.04 { return speedFrom + (1 - speedFrom) * CreditCardFlipFormCurve.inOutQuad.value(at: e / 0.04) }
            let fall = duration * 0.8
            let k = min((e - 0.04) / fall, 1)
            return 1 - CreditCardFlipFormCurve.outQuad(k)
        }
    }

    static let spinDuration = 0.56
    static let backDuration = 0.42
    /// The reel dims to this at full speed.
    static let movingAlpha = 0.55

    private var old: Leg?
    private var leg: Leg

    init(_ char: Character?) {
        let stop = Self.stop(char)
        leg = Leg(from: stop, to: stop, start: .distantPast, duration: 0, speedFrom: 0)
    }

    static func stop(_ char: Character?) -> Double {
        guard let char, let digit = char.wholeNumberValue else { return 0 }
        return Double(11 + digit)
    }

    var end: Date { leg.start.addingTimeInterval(leg.duration) }

    func roll(at date: Date) -> Double {
        if let old, date < leg.start { return old.roll(at: date) }
        return leg.roll(at: date)
    }

    func speed(at date: Date) -> Double {
        if let old, date < leg.start { return old.speed(at: date) }
        return leg.speed(at: date)
    }

    /// Spins to `char`; a reel going back waits `delay` first, its last spin still running meanwhile.
    mutating func show(_ char: Character?, at date: Date, delay: Double, still: Bool) {
        let target = Self.stop(char)
        if still {
            old = nil
            leg = Leg(from: target, to: target, start: .distantPast, duration: 0, speedFrom: 0)
            return
        }
        let current = roll(at: date)
        if target == current { return }
        let back = target < current
        let start = date.addingTimeInterval(back ? delay : 0)
        let next = Leg(
            from: roll(at: start),
            to: target,
            start: start,
            duration: back ? Self.backDuration : Self.spinDuration,
            speedFrom: speed(at: start)
        )
        old = leg
        leg = next
    }
}

// MARK: - Palettes

/// Invented card networks, told apart by the first digit of the number.
enum CreditCardFlipFormNetwork: String, CaseIterable {
    case none, kestrel, vela, orrery, nimbus

    static func of(_ digits: String) -> CreditCardFlipFormNetwork {
        switch digits.first {
        case "3": return .kestrel
        case "4": return .vela
        case "5": return .orrery
        case "6": return .nimbus
        default: return .none
        }
    }

    /// The card's gradient (top left to bottom right) and the glow colour of the light on it.
    var palette: [UInt32] {
        switch self {
        case .none: return [0x25262d, 0x454852, 0x9ea3b5]
        case .kestrel: return [0x0b4f4a, 0x13a77c, 0x7cf0c4]
        case .vela: return [0x1c2580, 0x6338e6, 0xa993ff]
        case .orrery: return [0xb8330f, 0xb0124f, 0xffad5c]
        case .nimbus: return [0x0a3d63, 0x0b98d6, 0x8fdcff]
        }
    }

    var title: String {
        switch self {
        case .none: return ""
        case .kestrel: return "Kestrel"
        case .vela: return "Vela"
        case .orrery: return "Orrery"
        case .nimbus: return "Nimbus"
        }
    }
}

/// Reanimated's `interpolateColor`, which mixes in gamma-2.2 RGB.
func creditCardFlipFormMix(_ a: UInt32, _ b: UInt32, _ k: Double) -> Color {
    func channel(_ c: UInt32, _ shift: UInt32) -> Double { Double((c >> shift) & 0xff) / 255 }
    func mix(_ shift: UInt32) -> Double {
        let x = pow(channel(a, shift), 2.2)
        let y = pow(channel(b, shift), 2.2)
        return pow(x + (y - x) * k, 1 / 2.2)
    }
    return Color(.sRGB, red: mix(16), green: mix(8), blue: mix(0), opacity: 1)
}

// MARK: - Engine

/// Everything on the card and the pay button that moves, as values of time. The view reads it on
/// every frame of a timeline; actions retarget it.
@MainActor
final class CreditCardFlipFormEngine {
    typealias Field = CreditCardFlipFormField

    // Motion constants (s).
    static let morphDuration = 0.9
    static let shineDuration = 1.1
    static let payFold = 0.38
    static let payDuration = 1.5
    static let checkDuration = 0.38
    static let spinTurn = 0.82

    var numberReels = (0..<16).map { _ in CreditCardFlipFormReel(nil) }
    var expiryReels = (0..<4).map { _ in CreditCardFlipFormReel(nil) }
    var cvvReels = (0..<3).map { _ in CreditCardFlipFormReel(nil) }

    // The palette the card had, the one it turns to, and how far the turn has spread.
    private(set) var fromNetwork = CreditCardFlipFormNetwork.none
    private(set) var toNetwork = CreditCardFlipFormNetwork.none
    var mix = CreditCardFlipFormTween(1)
    var shine = CreditCardFlipFormTween(0)

    var flip = CreditCardFlipFormSpring(0, duration: 0.82, dampingRatio: 0.74)
    var flipFade = CreditCardFlipFormTween(0)
    // The card's focus outline and the form's focus ring glide together on one spring; the CVV's
    // outline on the back (`ring`) only fades.
    static let focusSpring = (duration: 0.52, ratio: 0.86)
    var outline = [CreditCardFlipFormSpring](repeating: CreditCardFlipFormSpring(0, duration: focusSpring.duration, dampingRatio: focusSpring.ratio), count: 4)
    var outlineOn = CreditCardFlipFormTween(0)
    var formRing = [CreditCardFlipFormSpring](repeating: CreditCardFlipFormSpring(0, duration: focusSpring.duration, dampingRatio: focusSpring.ratio), count: 4)
    var formRingOn = CreditCardFlipFormTween(0)
    var ring = CreditCardFlipFormTween(0)
    /// The pay button lights up once the card is complete.
    var ready = CreditCardFlipFormTween(0)

    // The card's tilt (°): set by a dragging finger, sprung flat on release, swung by a new network.
    var tiltX = CreditCardFlipFormSpring(0, duration: CreditCardFlipFormCardMetrics.tiltSpring.duration, dampingRatio: CreditCardFlipFormCardMetrics.tiltSpring.ratio)
    var tiltY = CreditCardFlipFormSpring(0, duration: CreditCardFlipFormCardMetrics.tiltSpring.duration, dampingRatio: CreditCardFlipFormCardMetrics.tiltSpring.ratio)
    private var nudgeAt: Date?

    // The network's mark: the one shown and when it came, and the ones leaving.
    private(set) var mark: (network: CreditCardFlipFormNetwork, at: Date) = (.none, .distantPast)
    private(set) var leavingMarks: [(network: CreditCardFlipFormNetwork, at: Date)] = []
    var markScale = CreditCardFlipFormSpring(1, duration: 0.52, dampingRatio: 0.62)

    // The name: when each letter came, and the letters on their way out.
    struct Ghost {
        let text: [Character]
        let leaving: Set<Int>
        let at: Date
    }
    private(set) var name: [Character] = []
    private(set) var letterTimes: [Date] = []
    private(set) var ghosts: [Ghost] = []
    private(set) var placeholderAt = Date.distantPast
    private(set) var placeholderLeftAt: Date?

    // Paying.
    var fold = CreditCardFlipFormTween(0)
    var check = CreditCardFlipFormTween(0)
    var spinStart: Date?

    var settledAt = Date.distantPast

    private func kick(_ date: Date) {
        if date > settledAt { settledAt = date }
    }

    // MARK: Text

    func showNumber(_ digits: String, at date: Date, still: Bool) {
        let chars = Array(digits)
        for i in 0..<16 {
            numberReels[i].show(i < chars.count ? chars[i] : nil, at: date, delay: Double(15 - i) * 0.022, still: still)
            kick(numberReels[i].end)
        }
    }

    func showExpiry(_ digits: String, at date: Date, still: Bool) {
        let chars = Array(digits)
        for i in 0..<4 {
            expiryReels[i].show(i < chars.count ? chars[i] : nil, at: date, delay: Double(3 - i) * 0.03, still: still)
            kick(expiryReels[i].end)
        }
    }

    func showCvv(_ digits: String, at date: Date, still: Bool) {
        let chars = Array(digits)
        for i in 0..<3 {
            cvvReels[i].show(i < chars.count ? chars[i] : nil, at: date, delay: Double(2 - i) * 0.04, still: still)
            kick(cvvReels[i].end)
        }
    }

    /// Letters rise in where the name grew and fade up where it shrank, keyed by their place.
    func showName(_ text: String, at date: Date) {
        let next = Array(text)
        if next == name { return }
        var leaving = Set<Int>()
        for i in 0..<name.count where i >= next.count || name[i] != next[i] { leaving.insert(i) }
        if !leaving.isEmpty { ghosts.append(Ghost(text: name, leaving: leaving, at: date)) }
        var times: [Date] = []
        for i in 0..<next.count {
            times.append(i < name.count && name[i] == next[i] ? letterTimes[i] : date)
        }
        if name.isEmpty && !next.isEmpty { placeholderLeftAt = date }
        if !name.isEmpty && next.isEmpty {
            placeholderAt = date
            placeholderLeftAt = nil
        }
        name = next
        letterTimes = times
        kick(date.addingTimeInterval(0.32))
    }

    func prune(at date: Date) {
        ghosts.removeAll { date.timeIntervalSince($0.at) > 0.2 }
        leavingMarks.removeAll { date.timeIntervalSince($0.at) > 0.22 }
    }

    // MARK: Card

    /// A new network: its colours spread from the mark over the old ones while a light crosses.
    func turnTo(_ network: CreditCardFlipFormNetwork, at date: Date, still: Bool) {
        guard network != toNetwork else { return }
        fromNetwork = toNetwork
        toNetwork = network
        mix.set(0)
        mix.animate(to: 1, at: date, duration: still ? 0.3 : Self.morphDuration, curve: CreditCardFlipFormCurve.easeInOut)
        shine.set(0)
        if !still { shine.animate(to: 1, at: date, duration: Self.shineDuration, curve: CreditCardFlipFormCurve.easeInOut) }
        // The card swings a little so the light runs over its surface.
        if !still && network != .none {
            let swing = date.addingTimeInterval(CreditCardFlipFormCardMetrics.nudgeTime)
            nudgeAt = date
            tiltY.set(CreditCardFlipFormCardMetrics.nudge)
            tiltY.animate(to: 0, at: swing)
            kick(tiltY.end)
        }
        if network != mark.network {
            leavingMarks.append((mark.network, date))
            mark = (network, date)
            markScale.set(0.7)
            markScale.animate(to: 1, at: date)
        }
        kick(date.addingTimeInterval(Self.shineDuration))
    }

    func crossLight(at date: Date, still: Bool) {
        shine.set(0)
        if !still {
            shine.animate(to: 1, at: date, duration: Self.shineDuration, curve: CreditCardFlipFormCurve.easeInOut)
            kick(shine.end)
        }
    }

    // MARK: Tilt

    /// The tilt about the horizontal (x) and upright (y) axes at `date`.
    func tilt(at date: Date) -> (x: Double, y: Double) {
        var y = tiltY.value(at: date)
        if let nudgeAt {
            let t = date.timeIntervalSince(nudgeAt)
            if t >= 0 && t < CreditCardFlipFormCardMetrics.nudgeTime {
                y = CreditCardFlipFormCardMetrics.nudge * CreditCardFlipFormCurve.easeOut.value(at: t / CreditCardFlipFormCardMetrics.nudgeTime)
            }
        }
        return (tiltX.value(at: date), y)
    }

    /// A finger holds the card at this tilt.
    func hold(tiltX x: Double, tiltY y: Double) {
        nudgeAt = nil
        tiltX.set(x)
        tiltY.set(y)
    }

    /// The finger lets go: the card springs flat, carrying the finger's speed (° per s).
    func letGo(speedX: Double, speedY: Double, at date: Date) {
        let now = tilt(at: date)
        nudgeAt = nil
        tiltX.release(from: now.x, speed: speedX, to: 0, at: date)
        tiltY.release(from: now.y, speed: speedY, to: 0, at: date)
        kick(tiltX.end)
    }

    /// The card turns over for the CVV, and back for anything else; the outline glides to the part
    /// being edited on the front (fading when none is) and the form's ring to the field being
    /// edited. A newly shown outline or ring appears in place.
    func focus(_ field: Field?, rects: [Field: CGRect], fields: [Field: CGRect], at date: Date, still: Bool) {
        let back = field == .cvv ? 1.0 : 0.0
        if still {
            flipFade.animate(to: back, at: date, duration: 0.26)
            kick(flipFade.end)
        } else if flip.target != back {
            flip.animate(to: back, at: date)
            kick(flip.end)
        }
        if let field, field != .cvv, let r = rects[field] {
            glide(&outline, to: r, jump: still || outlineOn.value(at: date) < 0.05, at: date)
            if outlineOn.target != 1 { outlineOn.animate(to: 1, at: date, duration: 0.2) }
        } else if outlineOn.target != 0 {
            outlineOn.animate(to: 0, at: date, duration: 0.2)
        }
        if let field, let r = fields[field] {
            glide(&formRing, to: r, jump: still || formRingOn.value(at: date) < 0.05, at: date)
            if formRingOn.target != 1 { formRingOn.animate(to: 1, at: date, duration: 0.2) }
        } else if formRingOn.target != 0 {
            formRingOn.animate(to: 0, at: date, duration: 0.2)
        }
        let ringOn = field == .cvv ? 1.0 : 0.0
        if ring.target != ringOn { ring.animate(to: ringOn, at: date, duration: 0.22) }
        kick(date.addingTimeInterval(0.26))
    }

    /// The name grew or the layout changed: the outline follows the part being edited.
    func reframe(_ field: Field?, rects: [Field: CGRect], at date: Date, still: Bool) {
        guard let field, field != .cvv, let r = rects[field] else { return }
        glide(&outline, to: r, jump: still || outlineOn.value(at: date) < 0.05, at: date)
    }

    private func glide(_ springs: inout [CreditCardFlipFormSpring], to r: CGRect, jump: Bool, at date: Date) {
        let values = [r.minX, r.minY, r.width, r.height]
        for i in 0..<4 {
            if jump {
                springs[i].set(values[i])
            } else if springs[i].target != values[i] {
                springs[i].animate(to: values[i], at: date)
                kick(springs[i].end)
            }
        }
    }

    func setReady(_ on: Bool, at date: Date) {
        let target = on ? 1.0 : 0.0
        guard ready.target != target else { return }
        ready.animate(to: target, at: date, duration: 0.24)
        kick(ready.end)
    }

    func placeOutline(_ rect: CGRect) {
        let values = [rect.minX, rect.minY, rect.width, rect.height]
        for i in 0..<4 { outline[i].set(values[i]) }
    }

    /// 0 front … 1 back, and how much of each face shows.
    func turn(at date: Date, still: Bool) -> Double {
        still ? flipFade.value(at: date) : flip.value(at: date)
    }

    // MARK: Pay

    func pay(_ stage: CreditCardFlipFormStage, at date: Date) {
        switch stage {
        case .idle:
            fold.animate(to: 0, at: date, duration: Self.payFold, curve: CreditCardFlipFormCurve.easeInOut)
            check.animate(to: 0, at: date, duration: 0.16)
            spinStart = nil
            kick(fold.end)
        case .paying:
            fold.animate(to: 1, at: date, duration: Self.payFold, curve: CreditCardFlipFormCurve.easeInOut)
            spinStart = date
            kick(date.addingTimeInterval(Self.payFold + Self.payDuration + 0.1))
        case .paid:
            check.animate(to: 1, at: date, duration: Self.checkDuration, curve: CreditCardFlipFormCurve.easeOut)
            kick(check.end)
        }
    }

    func spin(at date: Date) -> Double {
        guard let spinStart else { return 0 }
        return date.timeIntervalSince(spinStart).truncatingRemainder(dividingBy: Self.spinTurn) / Self.spinTurn
    }

    /// Whether anything still moves at `date` (the comet runs round an outline and the spinner
    /// turns for as long as they show).
    func moving(at date: Date) -> Bool {
        date < settledAt || ring.target > 0 || ring.value(at: date) > 0 || outlineOn.target > 0 || outlineOn.value(at: date) > 0
            || spinStart != nil
    }
}

enum CreditCardFlipFormField: Hashable {
    case number, name, expiry, cvv
}

enum CreditCardFlipFormStage {
    case idle, paying, paid
}
