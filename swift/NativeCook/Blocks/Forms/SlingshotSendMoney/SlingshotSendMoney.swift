import SwiftUI
import UIKit

private enum SlingshotSendMoneyStyle {
    static let stops = [0.0, 0.13, 0.21, 0.29, 0.37, 0.47, 0.53, 0.59, 0.645, 0.72, 0.79, 0.86, 0.94, 1]
    static let pulledStops = [0.0, 0.19, 0.33, 0.45, 0.54, 0.61, 0.66, 0.71, 0.76, 0.81, 0.86, 0.91, 0.96, 1]
    static let satin = ["#85625f", "#82605e", "#6b504e", "#453233", "#33282a", "#2f2526", "#402e30", "#694c4e", "#735553", "#594546", "#483436", "#4a2f31", "#5b3132", "#5e3030"]
    static let heat = ["#c9801f", "#c2781f", "#b0691d", "#8f561b", "#6e431c", "#5f3d2a", "#6a4a3e", "#5d423c", "#47302c", "#3a2622", "#3d2420", "#45241e", "#5a2c28", "#693229"]
    static let glow = "#ffb347", avatarFill = "#f3cfcb", avatarInk = "#6e4b4a", success = "#7fe62c"
    static let ink = Color.white
    static let glowAlpha = 0.3, hintAlpha = 0.6, armedAlpha = 0.9
    static let roundFill = Color.rgba(255, 255, 255, 0.13), roundPressed = Color.rgba(255, 255, 255, 0.26)
    static let pillFill = Color(hex: 0x87595a), pillPressed = Color(hex: 0x6c4648)
    static let avatar = 68.0, avatarTop = 6.0, avatarShadowY = 6.0, avatarShadowBlur = 16.0, avatarShadowAlpha = 0.22
    static let initialsSize = 28.0, initialsLine = 34.0, nameSize = 14.0, nameLine = 18.0, nameGap = 8.0
    static let amountSize = 64.0, amountLine = 76.0, amountTracking = -0.6
    static let hintSize = 22.0, hintLine = 28.0, hintTracking = 10.0, armedTracking = 6.0
    static let round = 56.0, roundMargin = 20.0, roundHit = 8.0, roundScale = 0.92, roundTransition = 0.12
    static let closeSize = 30.0, editSize = 22.0, check = 62.0, tickWidth = 5.0
    static let pillWidth = 184.0, pillHeight = 52.0, pillScale = 0.96, pillTransition = 0.11
    static let pillLabelSize = 16.0, pillLabelLine = 20.0
    static let amountAt = 0.37, hintAt = 0.578, bottomRowAt = 0.885, sentGap = 12.0, rowSafeGap = 12.0
    static let sentSize = 14.0, sentLine = 18.0, pillOffscreenGap = 8.0
}

private enum SlingshotSendMoneyMotion {
    struct SlingshotSendMoneySpring { let mass: Double; let stiffness: Double; let damping: Double }
    static let amountSprings = [SlingshotSendMoneySpring(mass: 1, stiffness: 170, damping: 16), SlingshotSendMoneySpring(mass: 1, stiffness: 118, damping: 13.5), SlingshotSendMoneySpring(mass: 1, stiffness: 84, damping: 11.5), SlingshotSendMoneySpring(mass: 1, stiffness: 60, damping: 10)]
    static let hintSprings = [SlingshotSendMoneySpring(mass: 1, stiffness: 110, damping: 14), SlingshotSendMoneySpring(mass: 1, stiffness: 80, damping: 12), SlingshotSendMoneySpring(mass: 1, stiffness: 58, damping: 10.5), SlingshotSendMoneySpring(mass: 1, stiffness: 42, damping: 9)]
    static let heatSpring = SlingshotSendMoneySpring(mass: 1, stiffness: 130, damping: 20)
    static let catchSpring = SlingshotSendMoneySpring(mass: 1, stiffness: 260, damping: 12)
    static let pop = SlingshotSendMoneySpring(mass: 1, stiffness: 260, damping: 15)
    static let pillSpring = SlingshotSendMoneySpring(mass: 1, stiffness: 190, damping: 19)
    static let amountTravel = 0.245, fingerRatio = 0.8, overpull = 56.0, hintFollow = 0.62, armAt = 0.72
    static let sideRatio = 0.32, sideMax = 46.0, tiltArm = 420.0
    static let ghostAlpha = [1.0, 0.36, 0.24, 0.14], ghostAt = 22.0, strainAt = 150.0, stretch = 0.24, squeeze = 0.07
    static let flownScale = 0.4, fadeFrom = 0.3, fadeTo = 0.72, wording = 0.14
    static let hintOut = 0.22, catchAfter = 0.15, catchIn = 0.09, catchScale = 0.1, confirmAfter = 0.9
    static let tickAfter = 0.12, tickFor = 0.22, fade = 0.25, pillOut = 0.26, checkOut = 0.18
    static let hintBackAfter = 0.32, hintBack = 0.35, returnTime = 1.0
    static let loop = 9.0, testAt = 0.8, testFor = 0.5, testUp = 1.45, testDepth = 0.42
    static let pullAt = 2.3, pullFor = 0.95, pullUp = 4.3, tremble = 0.02, trembleSpeed = 9.0, wander = 34.0
    static let doneAt = 7.0, pressFor = 0.15, userHold = 3.0, sendingPoll = 0.2
    static let frameLimit = 0.064, energyThreshold = 6e-9, panDistance = 10.0
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ value: Double, _ from: Double, _ to: Double) -> Double { let t = clamp((value - from) / (to - from)); return t * t * (3 - 2 * t) }
    static func pull(_ dy: Double, travel: Double) -> Double {
        let y = max(0, dy) * fingerRatio
        guard y > travel else { return y }
        let over = y - travel; return travel + overpull * over / (over + overpull)
    }
}

private enum SlingshotSendMoneyCurve {
    case quadratic, outQuadratic, outCubic, inCubic
    func sample(_ t: Double) -> Double {
        switch self {
        case .quadratic: return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        case .outQuadratic: return 1 - pow(1 - t, 2)
        case .outCubic: return 1 - pow(1 - t, 3)
        case .inCubic: return t * t * t
        }
    }
}

private struct SlingshotSendMoneyScalar {
    private(set) var value: Double
    private(set) var target: Double
    private(set) var active = false
    private var from: Double
    private var velocity = 0.0, energy = 0.0, duration = 0.0
    private var start = Date.distantPast, last = Date.distantPast
    private var curve = SlingshotSendMoneyCurve.quadratic
    private var constants: SlingshotSendMoneyMotion.SlingshotSendMoneySpring?
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func jump(_ next: Double) { value = next; target = next; velocity = 0; constants = nil; active = false }
    mutating func time(_ next: Double, duration: Double, curve: SlingshotSendMoneyCurve = .quadratic, at date: Date, still: Bool = false, force: Bool = false) {
        if still || (!force && value == next) { jump(next); return }
        if !(active && constants == nil && target == next) { from = value; start = date }
        target = next; self.duration = duration; self.curve = curve; constants = nil; velocity = 0; active = true
    }
    mutating func spring(_ next: Double, constants: SlingshotSendMoneyMotion.SlingshotSendMoneySpring, at date: Date, still: Bool = false) {
        if still { jump(next); return }
        if self.constants != nil && target == next { return }
        if value == next { jump(next); return }
        let continuing = self.constants != nil && active
        if !continuing { velocity = 0; last = date }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; self.constants = constants; active = true
        energy = 0.5 * constants.stiffness * pow(value - next, 2)
        advance(date)
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        guard let constants else {
            let t = SlingshotSendMoneyMotion.clamp(date.timeIntervalSince(start) / duration)
            value = from + (target - from) * curve.sample(t)
            if t == 1 { active = false }; return
        }
        let dt = min(SlingshotSendMoneyMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(constants.stiffness / constants.mass), decay = constants.damping / (2 * constants.mass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped
            let wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt)))
            value = target + attenuation * wave
        } else {
            let b = velocity + omega * x, attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * constants.stiffness * pow(value - target, 2) + 0.5 * constants.mass * velocity * velocity
        if energy == 0 || current / energy <= SlingshotSendMoneyMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private indirect enum SlingshotSendMoneyAnimation {
    case time(Double, Double, SlingshotSendMoneyCurve, Bool)
    case spring(Double, SlingshotSendMoneyMotion.SlingshotSendMoneySpring, Bool)
    case sequence([SlingshotSendMoneyAnimation])
}

// A delayed replacement keeps the preceding trajectory alive until its own child starts.
private final class SlingshotSendMoneyAnimationNode {
    private(set) var value: Double
    private(set) var finished = false
    private var scalar: SlingshotSendMoneyScalar?
    private var previous: SlingshotSendMoneyAnimationNode?
    private var child: SlingshotSendMoneyAnimationNode?
    private var queued: [SlingshotSendMoneyAnimation] = []
    private var deadline: Date?
    private var delayed: SlingshotSendMoneyAnimation?
    init(_ kind: SlingshotSendMoneyAnimation, value: Double, at date: Date, previous: SlingshotSendMoneyAnimationNode? = nil) {
        self.value = value
        switch kind {
        case let .time(target, duration, curve, still):
            var next = previous?.scalar ?? SlingshotSendMoneyScalar(value)
            next.time(target, duration: duration, curve: curve, at: date, still: still, force: true); scalar = next; self.value = next.value
        case let .spring(target, constants, still):
            var next = previous?.scalar ?? SlingshotSendMoneyScalar(value)
            next.spring(target, constants: constants, at: date, still: still); scalar = next; self.value = next.value
        case let .sequence(kinds):
            queued = kinds
            startNext(at: date, previous: previous)
        }
    }
    init(delay: Double, kind: SlingshotSendMoneyAnimation, value: Double, at date: Date, previous: SlingshotSendMoneyAnimationNode?) {
        self.value = value; deadline = date.addingTimeInterval(delay); delayed = kind; self.previous = previous
    }
    private func startNext(at date: Date, previous: SlingshotSendMoneyAnimationNode?) {
        guard !queued.isEmpty else { finished = true; return }
        child = SlingshotSendMoneyAnimationNode(queued.removeFirst(), value: value, at: date, previous: previous)
        value = child!.value
    }
    func advance(_ date: Date) {
        guard !finished else { return }
        if let deadline, let delayed {
            if date < deadline {
                previous?.advance(date); value = previous?.value ?? value
                if previous?.finished == true { previous = nil }
                return
            }
            child = SlingshotSendMoneyAnimationNode(delayed, value: value, at: date, previous: previous)
            self.deadline = nil; self.delayed = nil; previous = nil
        }
        if var scalar {
            scalar.advance(date); self.scalar = scalar; value = scalar.value; finished = !scalar.active; return
        }
        guard let child else { finished = true; return }
        child.advance(date); value = child.value
        if child.finished {
            if queued.isEmpty { finished = true } else { startNext(at: date, previous: child) }
        }
    }
}

private struct SlingshotSendMoneyController {
    private(set) var value: Double
    private var root: SlingshotSendMoneyAnimationNode?
    init(_ value: Double) { self.value = value }
    mutating func time(_ target: Double, _ duration: Double, curve: SlingshotSendMoneyCurve = .quadratic, at date: Date) {
        if value == target { root = nil; value = target; return }
        root = SlingshotSendMoneyAnimationNode(.time(target, duration, curve, false), value: value, at: date, previous: root)
    }
    mutating func delay(_ delay: Double, _ kind: SlingshotSendMoneyAnimation, at date: Date, still: Bool = false) {
        root = SlingshotSendMoneyAnimationNode(delay: still ? 0 : delay, kind: kind, value: value, at: date, previous: root)
    }
    mutating func advance(_ date: Date) { root?.advance(date); value = root?.value ?? value; if root?.finished == true { root = nil } }
}

private enum SlingshotSendMoneyNumbers {
    private static let grouping = try! NSRegularExpression(pattern: "(?<=[A-Za-z0-9_])(?=(?:[0-9]{3})+(?![0-9]))")
    static func string(_ value: Double) -> String {
        if value.isNaN { return "NaN" }; if value == .infinity { return "Infinity" }; if value == -.infinity { return "-Infinity" }; if value == 0 { return "0" }
        let magnitude = abs(value), raw = String(magnitude).lowercased().split(separator: "e")
        let parts = String(raw[0]).split(separator: ".", omittingEmptySubsequences: false)
        var digits = parts.joined(), point = parts[0].count + (raw.count > 1 ? Int(raw[1]) ?? 0 : 0)
        while digits.first == "0" { digits.removeFirst(); point -= 1 }; while digits.last == "0" && digits.count > 1 { digits.removeLast() }
        let sign = value < 0 ? "-" : ""
        if magnitude >= 1e-6 && magnitude < 1e21 {
            if point <= 0 { return sign + "0." + String(repeating: "0", count: -point) + digits }
            if point >= digits.count { return sign + digits + String(repeating: "0", count: point - digits.count) }
            let split = digits.index(digits.startIndex, offsetBy: point); return sign + digits[..<split] + "." + digits[split...]
        }
        let rest = String(digits.dropFirst()), exponent = point - 1
        return sign + String(digits.prefix(1)) + (rest.isEmpty ? "" : "." + rest) + "e" + (exponent >= 0 ? "+" : "") + String(exponent)
    }
    static func money(_ symbol: String, _ value: Double) -> String {
        let text = fixed(value), range = NSRange(location: 0, length: (text as NSString).length)
        return symbol + grouping.stringByReplacingMatches(in: text, range: range, withTemplate: ",")
    }
    private static func fixed(_ value: Double) -> String {
        guard value.isFinite, abs(value) < 1e21 else { return string(value) }
        // Number.toFixed rounds the exact binary value, including decimal ties.
        let bits = abs(value).bitPattern, exponent = Int((bits >> 52) & 0x7ff)
        let significand = (bits & 0x000f_ffff_ffff_ffff) | (exponent == 0 ? 0 : UInt64(1) << 52)
        let coefficient = significand * 25, shift = (exponent == 0 ? -1074 : exponent - 1023 - 52) + 2
        var digits: String
        if shift < 0 {
            let right = -shift; var rounded = right >= 64 ? 0 : coefficient >> right
            if right < 64 { let remainder = coefficient & ((UInt64(1) << right) - 1); if remainder >= UInt64(1) << (right - 1) { rounded += 1 } }
            digits = String(rounded)
        } else {
            digits = String(coefficient)
            for _ in 0..<shift {
                var doubled = "", carry = 0
                for digit in digits.reversed() { let next = digit.wholeNumberValue! * 2 + carry; doubled = String(next % 10) + doubled; carry = next / 10 }
                digits = (carry > 0 ? String(carry) : "") + doubled
            }
        }
        digits = String(repeating: "0", count: max(0, 3 - digits.count)) + digits
        let split = digits.index(digits.endIndex, offsetBy: -2)
        return (value < 0 ? "-" : "") + digits[..<split] + "." + digits[split...]
    }
}

private struct SlingshotSendMoneyEchoState {
    var x = SlingshotSendMoneyScalar(0)
    var y = SlingshotSendMoneyScalar(0)
}

private enum SlingshotSendMoneyStage { case idle, sending, sent }

@MainActor
private final class SlingshotSendMoneyState: ObservableObject {
    @Published var revision = 0
    @Published private(set) var touches = 0
    var amount = 4.5
    var onSend: ((Double) -> Void)?
    var onSent: ((Double) -> Void)?
    var onDone: (() -> Void)?
    var onClose: (() -> Void)?
    var onEdit: (() -> Void)?
    var still = false, height = 0.0, safeTop = 0.0
    private(set) var stage = SlingshotSendMoneyStage.idle
    private(set) var autoPressed = false
    private(set) var aimX = 0.0, aimY = 0.0
    private(set) var flown = false
    private(set) var byHand = false
    private(set) var holding = false
    private(set) var amountShown = SlingshotSendMoneyController(1)
    private(set) var hintShown = SlingshotSendMoneyController(1)
    private(set) var avatarShown = SlingshotSendMoneyController(1)
    private(set) var caught = SlingshotSendMoneyController(0)
    private(set) var check = SlingshotSendMoneyController(0)
    private(set) var tick = SlingshotSendMoneyController(0)
    private(set) var sentShown = SlingshotSendMoneyController(0)
    private(set) var pill = SlingshotSendMoneyController(0)
    private(set) var corners = SlingshotSendMoneyController(1)
    private(set) var wording = SlingshotSendMoneyScalar(0)
    private(set) var heat = SlingshotSendMoneyScalar(0)
    private(set) var amounts = Array(repeating: SlingshotSendMoneyEchoState(), count: 4)
    private(set) var hints = Array(repeating: SlingshotSendMoneyEchoState(), count: 4)
    private var oldArrived = false, oldArmed = false
    private var origin: Date?
    private var lastClock = -1.0
    private var generation = 0
    var travel: Double { height * SlingshotSendMoneyMotion.amountTravel }
    var rise: Double { height * SlingshotSendMoneyStyle.amountAt - (safeTop + SlingshotSendMoneyStyle.avatarTop + SlingshotSendMoneyStyle.avatar / 2) }
    var armed: Bool { !flown && aimY >= travel * SlingshotSendMoneyMotion.armAt }

    func configure(amount: Double, height: Double, safeTop: Double, still: Bool, onSend: ((Double) -> Void)?, onSent: ((Double) -> Void)?, onDone: (() -> Void)?, onClose: (() -> Void)?, onEdit: (() -> Void)?) {
        self.amount = amount; self.height = height; self.safeTop = safeTop; self.still = still
        self.onSend = onSend; self.onSent = onSent; self.onDone = onDone; self.onClose = onClose; self.onEdit = onEdit
    }
    private func haptic(_ success: Bool = false, medium: Bool = false) {
        if success { UINotificationFeedbackGenerator().notificationOccurred(.success) }
        else { UIImpactFeedbackGenerator(style: medium ? .medium : .light).impactOccurred() }
    }
    func pullTo(_ dx: Double, _ dy: Double, at date: Date = .now) {
        guard !flown else { return }
        aimX = max(-SlingshotSendMoneyMotion.sideMax, min(SlingshotSendMoneyMotion.sideMax, dx * SlingshotSendMoneyMotion.sideRatio))
        aimY = SlingshotSendMoneyMotion.pull(dy, travel: travel)
        retargetFollowers(at: date); react()
    }
    private func react() {
        let nowArmed = armed
        if nowArmed != oldArmed && byHand && !flown { haptic(medium: nowArmed) }
        oldArmed = nowArmed
        let nowArrived = check.value > 0 && flown
        if nowArrived && !oldArrived { stage = .sent; if byHand { haptic(true) }; onSent?(amount) }
        oldArrived = nowArrived
    }
    func fire(at date: Date = .now) {
        guard !flown else { return }
        flown = true; aimX = 0
        if still { amountShown.time(0, SlingshotSendMoneyMotion.fade, at: date) }
        else {
            aimY = -rise
            caught.delay(SlingshotSendMoneyMotion.catchAfter, .sequence([
                .time(1, SlingshotSendMoneyMotion.catchIn, .outQuadratic, still),
                .spring(0, SlingshotSendMoneyMotion.catchSpring, still)
            ]), at: date, still: still)
        }
        hintShown.time(0, SlingshotSendMoneyMotion.hintOut, at: date)
        avatarShown.delay(SlingshotSendMoneyMotion.confirmAfter, .time(0, SlingshotSendMoneyMotion.fade, .quadratic, false), at: date, still: still)
        check.delay(SlingshotSendMoneyMotion.confirmAfter, still ? .time(1, SlingshotSendMoneyMotion.fade, .quadratic, false) : .spring(1, SlingshotSendMoneyMotion.pop, false), at: date)
        tick.delay(SlingshotSendMoneyMotion.confirmAfter + SlingshotSendMoneyMotion.tickAfter, .time(1, SlingshotSendMoneyMotion.tickFor, .outCubic, false), at: date)
        sentShown.delay(SlingshotSendMoneyMotion.confirmAfter + SlingshotSendMoneyMotion.tickAfter, .time(1, SlingshotSendMoneyMotion.fade, .quadratic, false), at: date)
        pill.delay(SlingshotSendMoneyMotion.confirmAfter, still ? .time(1, SlingshotSendMoneyMotion.fade, .quadratic, false) : .spring(1, SlingshotSendMoneyMotion.pillSpring, false), at: date)
        corners.delay(0, .time(0, SlingshotSendMoneyMotion.fade, .quadratic, false), at: date)
        retargetFollowers(at: date)
        stage = .sending; onSend?(amount); react(); revision &+= 1
    }
    func letGoOf(at date: Date = .now) { guard !flown else { return }; aimX = 0; aimY = 0; retargetFollowers(at: date) }
    func letGo(at date: Date = .now) { if aimY >= travel * SlingshotSendMoneyMotion.armAt { fire(at: date) } else { letGoOf(at: date); react() } }
    func done(at date: Date = .now) {
        stage = .idle; autoPressed = false
        check.time(0, SlingshotSendMoneyMotion.checkOut, at: date)
        tick.time(0, SlingshotSendMoneyMotion.checkOut, at: date)
        sentShown.time(0, SlingshotSendMoneyMotion.checkOut, at: date)
        pill.time(0, SlingshotSendMoneyMotion.pillOut, curve: .inCubic, at: date)
        avatarShown.time(1, SlingshotSendMoneyMotion.fade, at: date)
        hintShown.delay(SlingshotSendMoneyMotion.hintBackAfter, .time(1, SlingshotSendMoneyMotion.hintBack, .quadratic, false), at: date)
        amountShown.delay(SlingshotSendMoneyMotion.checkOut, .time(1, SlingshotSendMoneyMotion.fade, .quadratic, false), at: date)
        aimX = 0; aimY = 0; flown = false
        corners.delay(SlingshotSendMoneyMotion.pillOut, .time(1, SlingshotSendMoneyMotion.fade, .quadratic, false), at: date)
        retargetFollowers(at: date); react(); revision &+= 1
    }
    func touched() { autoPressed = false; touches += 1; origin = nil; lastClock = -1 }
    func beginPan() { letGoOf(); byHand = true; holding = true; touched(); react() }
    func finalizePan() { holding = false; touched(); react() }
    func activate() { touched(); byHand = true; fire() }
    func pressDone() { touched(); haptic(); onDone?(); done() }
    func pressClose() { touched(); haptic(); onClose?() }
    func pressEdit() { touched(); haptic(); onEdit?() }

    func autoplay(enabled: Bool) async {
        generation += 1; let token = generation
        origin = nil; lastClock = -1
        defer { if generation == token { origin = nil; lastClock = -1 } }
        guard enabled else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(SlingshotSendMoneyMotion.userHold * 1e9))
                guard !holding else { return }
                while stage == .sending {
                    try await Task.sleep(nanoseconds: UInt64(SlingshotSendMoneyMotion.sendingPoll * 1e9))
                }
                if stage == .sent {
                    done()
                    try await Task.sleep(nanoseconds: UInt64(SlingshotSendMoneyMotion.returnTime * 1e9))
                }
            }
            try Task.checkCancellation()
            byHand = false; origin = .now; lastClock = -1
            // Cancellation stops the clock and resume timers, leaving independent springs alive.
            while !Task.isCancelled { try await Task.sleep(nanoseconds: 60_000_000_000) }
        } catch { return }
    }
    private func runClock(_ date: Date) {
        guard let origin else { return }
        let t = max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: SlingshotSendMoneyMotion.loop)
        let was = lastClock > t ? -1 : lastClock
        let reach = travel / SlingshotSendMoneyMotion.fingerRatio
        if t >= SlingshotSendMoneyMotion.testAt && t < SlingshotSendMoneyMotion.testUp {
            let ease = SlingshotSendMoneyCurve.outCubic.sample(SlingshotSendMoneyMotion.clamp((t - SlingshotSendMoneyMotion.testAt) / SlingshotSendMoneyMotion.testFor))
            pullTo(0, reach * SlingshotSendMoneyMotion.testDepth * ease, at: date)
        } else if t >= SlingshotSendMoneyMotion.pullAt && t < SlingshotSendMoneyMotion.pullUp {
            let held = t - SlingshotSendMoneyMotion.pullAt - SlingshotSendMoneyMotion.pullFor
            let ramp = SlingshotSendMoneyMotion.smooth(t, SlingshotSendMoneyMotion.pullAt, SlingshotSendMoneyMotion.pullAt + SlingshotSendMoneyMotion.pullFor)
            let depth = held < 0 ? ramp : 1 + SlingshotSendMoneyMotion.tremble * sin(held * SlingshotSendMoneyMotion.trembleSpeed)
            let side = held < 0 ? SlingshotSendMoneyMotion.wander * ramp : SlingshotSendMoneyMotion.wander * cos(.pi * held / (SlingshotSendMoneyMotion.pullUp - SlingshotSendMoneyMotion.pullAt - SlingshotSendMoneyMotion.pullFor))
            pullTo(side / SlingshotSendMoneyMotion.sideRatio, reach * depth, at: date)
        }
        if (was < SlingshotSendMoneyMotion.testUp && t >= SlingshotSendMoneyMotion.testUp) || (was < SlingshotSendMoneyMotion.pullUp && t >= SlingshotSendMoneyMotion.pullUp) { letGo(at: date) }
        if was < SlingshotSendMoneyMotion.doneAt && t >= SlingshotSendMoneyMotion.doneAt { autoPressed = true }
        if was < SlingshotSendMoneyMotion.doneAt + SlingshotSendMoneyMotion.pressFor && t >= SlingshotSendMoneyMotion.doneAt + SlingshotSendMoneyMotion.pressFor { onDone?(); done(at: date) }
        lastClock = t
    }
    private func retargetFollowers(at date: Date) {
        let hintY = flown ? 0 : aimY * SlingshotSendMoneyMotion.hintFollow
        for index in amounts.indices {
            amounts[index].x.spring(aimX, constants: SlingshotSendMoneyMotion.amountSprings[index], at: date, still: still)
            amounts[index].y.spring(aimY, constants: SlingshotSendMoneyMotion.amountSprings[index], at: date, still: still)
            hints[index].x.spring(aimX, constants: SlingshotSendMoneyMotion.hintSprings[index], at: date, still: still)
            hints[index].y.spring(hintY, constants: SlingshotSendMoneyMotion.hintSprings[index], at: date, still: still)
        }
        let wordTarget = flown || armed ? 1.0 : 0.0
        if wording.target != wordTarget { wording.time(wordTarget, duration: SlingshotSendMoneyMotion.wording, at: date) }
        heat.spring(flown ? 0 : max(0, aimY) / travel, constants: SlingshotSendMoneyMotion.heatSpring, at: date, still: still)
    }
    func advance(_ date: Date) {
        runClock(date)
        amountShown.advance(date); hintShown.advance(date); avatarShown.advance(date); caught.advance(date)
        check.advance(date); tick.advance(date); sentShown.advance(date); pill.advance(date); corners.advance(date)
        retargetFollowers(at: date)
        for index in amounts.indices {
            amounts[index].x.advance(date); amounts[index].y.advance(date); hints[index].x.advance(date); hints[index].y.advance(date)
        }
        wording.advance(date); heat.advance(date)
        react(); revision &+= 1
    }
}

private struct SlingshotSendMoneyLoopKey: Equatable {
    let enabled: Bool
    let touches: Int
}

private enum SlingshotSendMoneyDrawing {
    static func channels(_ hex: String) -> [Double] {
        let value = UInt32(hex.dropFirst(), radix: 16) ?? 0
        return [Double((value >> 16) & 255), Double((value >> 8) & 255), Double(value & 255)]
    }
    static func color(_ css: String) -> Color { SlingshotSendMoneyCSS.color(css) }
    static let tick: Path = {
        var path = Path(); path.move(to: CGPoint(x: 19.5, y: 32.5)); path.addLine(to: CGPoint(x: 27.5, y: 40)); path.addLine(to: CGPoint(x: 43, y: 23.5)); return path
    }()
}

private struct SlingshotSendMoneyBackground: View {
    let colors: [String]
    let heatColors: [String]
    let glowColor: String
    let heat: Double
    var body: some View {
        Canvas { context, size in
            let h = SlingshotSendMoneyMotion.clamp(heat)
            let stops = SlingshotSendMoneyStyle.stops.enumerated().compactMap { index, stop -> Gradient.Stop? in
                guard colors.indices.contains(index), heatColors.indices.contains(index) else { return nil }
                let a = SlingshotSendMoneyDrawing.channels(colors[index]), b = SlingshotSendMoneyDrawing.channels(heatColors[index])
                let rgb = (0..<3).map { floor(a[$0] + (b[$0] - a[$0]) * h + 0.5) }
                return Gradient.Stop(color: .rgba(rgb[0], rgb[1], rgb[2], 1), location: stop + (SlingshotSendMoneyStyle.pulledStops[index] - stop) * h)
            }
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(Gradient(stops: stops), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            var glow = context; glow.opacity = SlingshotSendMoneyStyle.glowAlpha * h
            let center = CGPoint(x: size.width / 2, y: size.height * 0.1), radius = size.width * 0.9
            let color = SlingshotSendMoneyDrawing.color(glowColor)
            glow.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)), with: .radialGradient(Gradient(colors: [color, color.opacity(0)]), center: center, startRadius: 0, endRadius: radius))
        }
    }
}

private enum SlingshotSendMoneyCSS {
    private static let names: [String: UInt32] = [
        "transparent": 0x00000000,
        "aliceblue": 0xf0f8ffff,
        "antiquewhite": 0xfaebd7ff,
        "aqua": 0x00ffffff,
        "aquamarine": 0x7fffd4ff,
        "azure": 0xf0ffffff,
        "beige": 0xf5f5dcff,
        "bisque": 0xffe4c4ff,
        "black": 0x000000ff,
        "blanchedalmond": 0xffebcdff,
        "blue": 0x0000ffff,
        "blueviolet": 0x8a2be2ff,
        "brown": 0xa52a2aff,
        "burlywood": 0xdeb887ff,
        "burntsienna": 0xea7e5dff,
        "cadetblue": 0x5f9ea0ff,
        "chartreuse": 0x7fff00ff,
        "chocolate": 0xd2691eff,
        "coral": 0xff7f50ff,
        "cornflowerblue": 0x6495edff,
        "cornsilk": 0xfff8dcff,
        "crimson": 0xdc143cff,
        "cyan": 0x00ffffff,
        "darkblue": 0x00008bff,
        "darkcyan": 0x008b8bff,
        "darkgoldenrod": 0xb8860bff,
        "darkgray": 0xa9a9a9ff,
        "darkgreen": 0x006400ff,
        "darkgrey": 0xa9a9a9ff,
        "darkkhaki": 0xbdb76bff,
        "darkmagenta": 0x8b008bff,
        "darkolivegreen": 0x556b2fff,
        "darkorange": 0xff8c00ff,
        "darkorchid": 0x9932ccff,
        "darkred": 0x8b0000ff,
        "darksalmon": 0xe9967aff,
        "darkseagreen": 0x8fbc8fff,
        "darkslateblue": 0x483d8bff,
        "darkslategray": 0x2f4f4fff,
        "darkslategrey": 0x2f4f4fff,
        "darkturquoise": 0x00ced1ff,
        "darkviolet": 0x9400d3ff,
        "deeppink": 0xff1493ff,
        "deepskyblue": 0x00bfffff,
        "dimgray": 0x696969ff,
        "dimgrey": 0x696969ff,
        "dodgerblue": 0x1e90ffff,
        "firebrick": 0xb22222ff,
        "floralwhite": 0xfffaf0ff,
        "forestgreen": 0x228b22ff,
        "fuchsia": 0xff00ffff,
        "gainsboro": 0xdcdcdcff,
        "ghostwhite": 0xf8f8ffff,
        "gold": 0xffd700ff,
        "goldenrod": 0xdaa520ff,
        "gray": 0x808080ff,
        "green": 0x008000ff,
        "greenyellow": 0xadff2fff,
        "grey": 0x808080ff,
        "honeydew": 0xf0fff0ff,
        "hotpink": 0xff69b4ff,
        "indianred": 0xcd5c5cff,
        "indigo": 0x4b0082ff,
        "ivory": 0xfffff0ff,
        "khaki": 0xf0e68cff,
        "lavender": 0xe6e6faff,
        "lavenderblush": 0xfff0f5ff,
        "lawngreen": 0x7cfc00ff,
        "lemonchiffon": 0xfffacdff,
        "lightblue": 0xadd8e6ff,
        "lightcoral": 0xf08080ff,
        "lightcyan": 0xe0ffffff,
        "lightgoldenrodyellow": 0xfafad2ff,
        "lightgray": 0xd3d3d3ff,
        "lightgreen": 0x90ee90ff,
        "lightgrey": 0xd3d3d3ff,
        "lightpink": 0xffb6c1ff,
        "lightsalmon": 0xffa07aff,
        "lightseagreen": 0x20b2aaff,
        "lightskyblue": 0x87cefaff,
        "lightslategray": 0x778899ff,
        "lightslategrey": 0x778899ff,
        "lightsteelblue": 0xb0c4deff,
        "lightyellow": 0xffffe0ff,
        "lime": 0x00ff00ff,
        "limegreen": 0x32cd32ff,
        "linen": 0xfaf0e6ff,
        "magenta": 0xff00ffff,
        "maroon": 0x800000ff,
        "mediumaquamarine": 0x66cdaaff,
        "mediumblue": 0x0000cdff,
        "mediumorchid": 0xba55d3ff,
        "mediumpurple": 0x9370dbff,
        "mediumseagreen": 0x3cb371ff,
        "mediumslateblue": 0x7b68eeff,
        "mediumspringgreen": 0x00fa9aff,
        "mediumturquoise": 0x48d1ccff,
        "mediumvioletred": 0xc71585ff,
        "midnightblue": 0x191970ff,
        "mintcream": 0xf5fffaff,
        "mistyrose": 0xffe4e1ff,
        "moccasin": 0xffe4b5ff,
        "navajowhite": 0xffdeadff,
        "navy": 0x000080ff,
        "oldlace": 0xfdf5e6ff,
        "olive": 0x808000ff,
        "olivedrab": 0x6b8e23ff,
        "orange": 0xffa500ff,
        "orangered": 0xff4500ff,
        "orchid": 0xda70d6ff,
        "palegoldenrod": 0xeee8aaff,
        "palegreen": 0x98fb98ff,
        "paleturquoise": 0xafeeeeff,
        "palevioletred": 0xdb7093ff,
        "papayawhip": 0xffefd5ff,
        "peachpuff": 0xffdab9ff,
        "peru": 0xcd853fff,
        "pink": 0xffc0cbff,
        "plum": 0xdda0ddff,
        "powderblue": 0xb0e0e6ff,
        "purple": 0x800080ff,
        "rebeccapurple": 0x663399ff,
        "red": 0xff0000ff,
        "rosybrown": 0xbc8f8fff,
        "royalblue": 0x4169e1ff,
        "saddlebrown": 0x8b4513ff,
        "salmon": 0xfa8072ff,
        "sandybrown": 0xf4a460ff,
        "seagreen": 0x2e8b57ff,
        "seashell": 0xfff5eeff,
        "sienna": 0xa0522dff,
        "silver": 0xc0c0c0ff,
        "skyblue": 0x87ceebff,
        "slateblue": 0x6a5acdff,
        "slategray": 0x708090ff,
        "slategrey": 0x708090ff,
        "snow": 0xfffafaff,
        "springgreen": 0x00ff7fff,
        "steelblue": 0x4682b4ff,
        "tan": 0xd2b48cff,
        "teal": 0x008080ff,
        "thistle": 0xd8bfd8ff,
        "tomato": 0xff6347ff,
        "turquoise": 0x40e0d0ff,
        "violet": 0xee82eeff,
        "wheat": 0xf5deb3ff,
        "white": 0xffffffff,
        "whitesmoke": 0xf5f5f5ff,
        "yellow": 0xffff00ff,
        "yellowgreen": 0x9acd32ff
    ]
    private static let hex = try! NSRegularExpression(pattern: #"^#([0-9a-fA-F]{3,4}|[0-9a-fA-F]{6}|[0-9a-fA-F]{8})$"#)
    private static let formats: [(String, Bool, NSRegularExpression)] = [
        ("rgb", false, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("rgb", true, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("rgb", true, try! NSRegularExpression(pattern: #"rgba?\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hsl", false, try! NSRegularExpression(pattern: #"hsl\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*\)"#)),
        ("hsl", true, try! NSRegularExpression(pattern: #"hsla\(\s*([-+]?\d*\.?\d+)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+%)\s*,\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hsl", true, try! NSRegularExpression(pattern: #"hsla\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#)),
        ("hwb", false, try! NSRegularExpression(pattern: #"hwb\(\s*([-+]?\d*\.?\d+)\s*([-+]?\d*\.?\d+%)\s*([-+]?\d*\.?\d+%)\s*\)"#)),
        ("hwb", true, try! NSRegularExpression(pattern: #"hwb\(\s*([-+]?\d*\.?\d+)\s*,?\s*([-+]?\d*\.?\d+%)\s*,?\s*([-+]?\d*\.?\d+%)\s*/\s*([-+]?\d*\.?\d+)\s*\)"#))
    ]
    private static func clamp(_ value: Double, _ high: Double = 1) -> Double { min(high, max(0, value)) }
    private static func byte(_ value: Double) -> Double {
        guard value.isFinite else { return 0 }
        let low = floor(value)
        return value - low < 0.5 ? low : low + 1
    }
    private static func hue(_ p: Double, _ q: Double, _ input: Double) -> Double {
        var t = input
        if t < 0 { t += 1 }; if t > 1 { t -= 1 }
        if t < 1 / 6 { return p + (q - p) * 6 * t }
        if t < 1 / 2 { return q }
        if t < 2 / 3 { return p + (q - p) * (2 / 3 - t) * 6 }
        return p
    }
    static func color(_ source: String) -> Color {
        if let value = names[source] { return Color(hex: value >> 8, opacity: Double(value & 255) / 255) }
        let ns = source as NSString, whole = NSRange(location: 0, length: (source as NSString).length)
        if hex.firstMatch(in: source, range: whole) != nil {
            var digits = String(source.dropFirst())
            if digits.count == 3 || digits.count == 4 { digits = digits.map { "\($0)\($0)" }.joined() }
            if digits.count == 6 { digits += "ff" }
            if let value = UInt32(digits, radix: 16) { return Color(hex: value >> 8, opacity: Double(value & 255) / 255) }
        }
        for (kind, alpha, pattern) in formats {
            guard let match = pattern.firstMatch(in: source, range: whole) else { continue }
            let values = (1..<match.numberOfRanges).map { Double(ns.substring(with: match.range(at: $0)).replacingOccurrences(of: "%", with: "")) ?? 0 }
            let opacity = alpha ? byte(clamp(values[3]) * 255) / 255 : 1
            if kind == "rgb" {
                return .rgba(clamp(values[0], 255).rounded(.towardZero), clamp(values[1], 255).rounded(.towardZero), clamp(values[2], 255).rounded(.towardZero), opacity)
            }
            let h = ((values[0].truncatingRemainder(dividingBy: 360) + 360).truncatingRemainder(dividingBy: 360)) / 360
            let second = clamp(values[1] / 100), third = clamp(values[2] / 100)
            let channels: [Double]
            if kind == "hwb" {
                if second + third >= 1 { channels = Array(repeating: second / (second + third), count: 3) }
                else { channels = [h + 1 / 3, h, h - 1 / 3].map { hue(0, 1, $0) * (1 - second - third) + second } }
            } else {
                let q = third < 0.5 ? third * (1 + second) : third + second - third * second
                let p = 2 * third - q
                channels = [h + 1 / 3, h, h - 1 / 3].map { hue(p, q, $0) }
            }
            return .rgba(byte(channels[0] * 255), byte(channels[1] * 255), byte(channels[2] * 255), opacity)
        }
        return .clear
    }
}


private struct SlingshotSendMoneyEcho<SlingshotSendMoneyContent: View>: View {
    let state: SlingshotSendMoneyEchoState
    let aimY: Double
    let rise: Double
    let shown: Double
    let order: Int
    let width: Double
    let height: Double
    @ViewBuilder let content: () -> SlingshotSendMoneyContent
    var body: some View {
        let x = state.x.value, y = state.y.value, lag = abs(aimY - y)
        let strain = min(1, lag / SlingshotSendMoneyMotion.strainAt)
        let up = rise > 0 ? SlingshotSendMoneyMotion.clamp(-y / rise) : 0
        let scale = 1 - (1 - SlingshotSendMoneyMotion.flownScale) * up
        let trailing = order == 0 ? 1 : min(1, lag / SlingshotSendMoneyMotion.ghostAt)
        return content().frame(width: width, height: height)
            .scaleEffect(x: scale * (1 - SlingshotSendMoneyMotion.squeeze * strain), y: scale * (1 + SlingshotSendMoneyMotion.stretch * strain))
            .rotationEffect(.radians(-x / SlingshotSendMoneyMotion.tiltArm))
            .offset(x: x, y: y)
            .opacity(shown * SlingshotSendMoneyMotion.ghostAlpha[order] * trailing * (1 - SlingshotSendMoneyMotion.smooth(up, SlingshotSendMoneyMotion.fadeFrom, SlingshotSendMoneyMotion.fadeTo)))
            .allowsHitTesting(false).accessibilityHidden(true)
    }
}

private struct SlingshotSendMoneyRoundStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.frame(width: SlingshotSendMoneyStyle.round, height: SlingshotSendMoneyStyle.round)
            .background(configuration.isPressed ? SlingshotSendMoneyStyle.roundPressed : SlingshotSendMoneyStyle.roundFill, in: Circle())
            .scaleEffect(configuration.isPressed ? SlingshotSendMoneyStyle.roundScale : 1)
            .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: SlingshotSendMoneyStyle.roundTransition), value: configuration.isPressed)
            .padding(SlingshotSendMoneyStyle.roundHit).contentShape(Rectangle()).padding(-SlingshotSendMoneyStyle.roundHit)
    }
}

private struct SlingshotSendMoneyPillStyle: ButtonStyle {
    let autoPressed: Bool
    func makeBody(configuration: Configuration) -> some View {
        let pressed = configuration.isPressed || autoPressed
        return configuration.label.frame(width: SlingshotSendMoneyStyle.pillWidth, height: SlingshotSendMoneyStyle.pillHeight)
            .background(pressed ? SlingshotSendMoneyStyle.pillPressed : SlingshotSendMoneyStyle.pillFill, in: Capsule())
            .scaleEffect(pressed ? SlingshotSendMoneyStyle.pillScale : 1)
            .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: SlingshotSendMoneyStyle.pillTransition), value: pressed)
            .frame(width: SlingshotSendMoneyStyle.pillWidth, height: SlingshotSendMoneyStyle.pillHeight).contentShape(Rectangle())
    }
}

/// Each line and its ghosts follow the same pull with their own springs.
struct SlingshotSendMoney: View {
    var amount = 4.5
    var symbol = "$"
    var recipient = "Dave"
    var initials: String? = nil
    var hint = "Swipe  down"
    var armedHint = "Release to send"
    var sentLabel = "Sent"
    var doneLabel = "Done"
    var autoPlay = false
    var colors = SlingshotSendMoneyStyle.satin
    var heatColors = SlingshotSendMoneyStyle.heat
    var glowColor = SlingshotSendMoneyStyle.glow
    var avatarColor = SlingshotSendMoneyStyle.avatarFill
    var avatarInk = SlingshotSendMoneyStyle.avatarInk
    var successColor = SlingshotSendMoneyStyle.success
    var onSend: ((Double) -> Void)? = nil
    var onSent: ((Double) -> Void)? = nil
    var onDone: (() -> Void)? = nil
    var onClose: (() -> Void)? = nil
    var onEdit: (() -> Void)? = nil
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var state = SlingshotSendMoneyState()
    @GestureState private var panActive = false
    @State private var panBegan = false
    @State private var panActivated = false
    private typealias S = SlingshotSendMoneyStyle
    private typealias M = SlingshotSendMoneyMotion

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            TimelineView(.animation) { timeline in
                let _ = state.configure(amount: amount, height: height, safeTop: insets.top, still: reduceMotion, onSend: onSend, onSent: onSent, onDone: onDone, onClose: onClose, onEdit: onEdit)
                screen(width: width, height: height, safeTop: insets.top, safeBottom: insets.bottom)
                    .frame(width: width, height: height).background(SlingshotSendMoneyDrawing.color(S.satin[5])).clipped()
                    .offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
        }
        .preferredColorScheme(.dark)
        .task(id: SlingshotSendMoneyLoopKey(enabled: autoPlay, touches: state.touches)) { await state.autoplay(enabled: autoPlay) }
        .onChange(of: panActive) { _, active in
            guard !active else { return }
            // A canceled pan only finalizes; it never sends or springs the aim home.
            Task { @MainActor in
                await Task.yield()
                if !panActive && panBegan { panBegan = false; panActivated = false; state.finalizePan() }
            }
        }
    }
    private func screen(width: Double, height: Double, safeTop: Double, safeBottom: Double) -> some View {
        let amountAt = height * S.amountAt, hintAt = height * S.hintAt
        let avatarTop = safeTop + S.avatarTop
        let rowBottom = max(safeBottom + S.rowSafeGap, height * (1 - S.bottomRowAt) - S.round / 2)
        let shown = SlingshotSendMoneyNumbers.money(symbol, amount)
        let copies = reduceMotion ? [0] : [3, 2, 1, 0]
        let hintY = state.flown ? 0 : state.aimY * M.hintFollow
        let idle = state.stage == .idle
        return ZStack(alignment: .topLeading) {
            SlingshotSendMoneyBackground(colors: colors, heatColors: heatColors, glowColor: glowColor, heat: state.heat.value)
                .frame(width: width, height: height).allowsHitTesting(false).accessibilityHidden(true)
            ZStack(alignment: .topLeading) {
                Color.clear.frame(width: width, height: height)
                ForEach(copies, id: \.self) { order in
                    SlingshotSendMoneyEcho(state: state.amounts[order], aimY: state.aimY, rise: state.rise, shown: state.amountShown.value, order: order, width: width, height: S.amountLine) {
                        Text(shown).font(.system(size: S.amountSize, weight: .bold)).monospacedDigit().tracking(S.amountTracking)
                            .foregroundStyle(S.ink).lineLimit(1).frame(maxWidth: width)
                    }.offset(y: amountAt - S.amountLine / 2)
                }
                ForEach(copies, id: \.self) { order in
                    SlingshotSendMoneyEcho(state: state.hints[order], aimY: hintY, rise: 0, shown: state.hintShown.value, order: order, width: width, height: S.hintLine) {
                        ZStack {
                            Text(hint.uppercased()).tracking(S.hintTracking).padding(.leading, S.hintTracking)
                                .opacity(S.hintAlpha * (1 - state.wording.value))
                            Text(armedHint.uppercased()).tracking(S.armedTracking).padding(.leading, S.armedTracking)
                                .opacity(S.armedAlpha * state.wording.value)
                        }.font(.system(size: S.hintSize, weight: .semibold)).foregroundStyle(S.ink).lineLimit(1).frame(maxWidth: width)
                    }.offset(y: hintAt - S.hintLine / 2)
                }
            }
            .frame(width: width, height: height).contentShape(Rectangle()).gesture(pan)
            .allowsHitTesting(idle).accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton)
            .accessibilityLabel("Send \(shown) to \(recipient)").accessibilityHint("Swipe down and release to send")
            .accessibilityAction { state.activate() }.accessibilityHidden(!idle)

            VStack(spacing: S.nameGap) {
                Text(initials ?? String(decoding: recipient.utf16.prefix(1), as: UTF16.self).uppercased())
                    .font(.system(size: S.initialsSize, weight: .bold)).foregroundStyle(SlingshotSendMoneyDrawing.color(avatarInk))
                    .frame(height: S.initialsLine)
                    .frame(width: S.avatar, height: S.avatar)
                    .background(SlingshotSendMoneyDrawing.color(avatarColor), in: Circle())
                    .shadow(color: .black.opacity(S.avatarShadowAlpha), radius: S.avatarShadowBlur / 2, x: 0, y: S.avatarShadowY)
                Text(recipient).font(.system(size: S.nameSize, weight: .semibold)).foregroundStyle(S.ink).lineLimit(1).frame(height: S.nameLine)
            }
            .frame(width: width).scaleEffect(1 + M.catchScale * state.caught.value)
            .opacity(state.avatarShown.value).offset(y: avatarTop).allowsHitTesting(false)

            HStack {
                Button(action: state.pressClose) { Image(systemName: "xmark").font(.system(size: S.closeSize, weight: .regular)).foregroundStyle(S.ink) }.accessibilityLabel("Close")
                Spacer()
                Button(action: state.pressEdit) { Image(systemName: "pencil").font(.system(size: S.editSize, weight: .regular)).foregroundStyle(S.ink) }.accessibilityLabel("Edit amount")
            }
            .buttonStyle(SlingshotSendMoneyRoundStyle()).frame(width: width - S.roundMargin * 2, height: S.round)
            .opacity(state.corners.value * (1 - M.smooth(state.heat.value, 0.45, 0.8)) * (state.flown ? 0 : 1))
            .offset(x: S.roundMargin, y: height - rowBottom - S.round).allowsHitTesting(idle).accessibilityHidden(!idle)

            VStack(spacing: S.sentGap) {
                Canvas { context, _ in
                    context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: S.check, height: S.check)), with: .color(SlingshotSendMoneyDrawing.color(successColor)))
                    context.stroke(SlingshotSendMoneyDrawing.tick.trimmedPath(from: 0, to: state.tick.value), with: .color(S.ink), style: StrokeStyle(lineWidth: S.tickWidth, lineCap: .round, lineJoin: .round))
                }
                .frame(width: S.check, height: S.check).scaleEffect(reduceMotion ? 1 : state.check.value).opacity(min(1, state.check.value * 4))
                Text(sentLabel).font(.system(size: S.sentSize, weight: .bold)).foregroundStyle(S.ink).lineLimit(1)
                    .frame(height: S.sentLine).opacity(state.sentShown.value)
            }
            .frame(width: width).offset(y: amountAt - S.check / 2).allowsHitTesting(false)
            .accessibilityElement(children: .ignore).accessibilityLabel(sentLabel).accessibilityHidden(state.stage != .sent)

            Button(action: state.pressDone) {
                Text(doneLabel).font(.system(size: S.pillLabelSize, weight: .semibold)).foregroundStyle(S.ink).lineLimit(1).frame(height: S.pillLabelLine)
            }
            .buttonStyle(SlingshotSendMoneyPillStyle(autoPressed: state.autoPressed)).opacity(min(1, state.pill.value * 3))
            .offset(x: (width - S.pillWidth) / 2, y: height - rowBottom - (S.round - S.pillHeight) / 2 - S.pillHeight + (reduceMotion ? 0 : (1 - state.pill.value) * (rowBottom + S.pillHeight + S.pillOffscreenGap)))
            .allowsHitTesting(state.stage == .sent).accessibilityHidden(state.stage != .sent)
        }
    }
    private var pan: some Gesture {
        DragGesture(minimumDistance: 0).updating($panActive) { _, active, _ in active = true }
            .onChanged { value in
                guard state.stage == .idle else { return }
                if !panBegan { panBegan = true; panActivated = false; state.beginPan() }
                if hypot(value.translation.width, value.translation.height) >= M.panDistance { panActivated = true }
                if panActivated { state.pullTo(value.translation.width, value.translation.height) }
            }
            .onEnded { _ in
                guard panBegan else { return }
                if panActivated { state.letGo() }
                panBegan = false; panActivated = false; state.finalizePan()
            }
    }
}
