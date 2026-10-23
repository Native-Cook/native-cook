import SwiftUI
import UIKit

let UnlockingCheckoutStepsLoop = 10.0

struct UnlockingCheckoutStepsCheckoutOption: Equatable {
    var label: String
    var icon: String
    var timeLabel: String
    var placeLabel: String
    var places: [String]
    static let samples = [
        UnlockingCheckoutStepsCheckoutOption(label: "Delivery", icon: "truck", timeLabel: "DELIVERY BY", placeLabel: "DELIVERY TO", places: ["Home", "Office", "Studio"]),
        UnlockingCheckoutStepsCheckoutOption(label: "Pickup", icon: "shopping-bag", timeLabel: "PICKUP BY", placeLabel: "PICKUP AT", places: ["Mill St counter", "Harbour kiosk"])
    ]
}

struct UnlockingCheckoutStepsCheckoutChoice { let option: String; let hour: String; let place: String }

private enum UnlockingCheckoutStepsStyle {
    static let hours = ["7AM", "8AM", "9AM", "10AM", "11AM", "12PM", "1PM", "2PM", "3PM", "4PM", "5PM"]
    static let paper = Color.white, ink = Color(hex: 0x111111), grey = Color(hex: 0x8d8d8d), faint = Color(hex: 0xa8a8a8), hairline = Color(hex: 0xececec), green = Color(hex: 0x34d58a), teal = Color(hex: 0x2a9db3), locked = Color(hex: 0x858585)
    static let drawer = Color(hex: 0xf4f4f4), well = Color(hex: 0xeaeaea), suggested = Color(hex: 0xdcdcdc), returnFill = Color(hex: 0x2c2c2c)
    static let close = Color(hex: 0xc6c6c6), checkBorder = Color(hex: 0xc6f3df), line = Color(hex: 0xe4e4e4), nextDot = Color(hex: 0xc9c9c9), cardBorder = Color(hex: 0xefefef), cardRule = Color(hex: 0xf2f2f2), drawerRule = Color(hex: 0xe6e6e6), placeInk = Color(hex: 0x5a5a5a), donePressed = Color(hex: 0x4fe3a0), returnPressed = Color(hex: 0x444444)
    static let side = 22.0, header = 48.0, dot = 26.0, stepLine = 46.0, card = 160.0, cardPad = 28.0, confirmWidth = 168.0, confirmHeight = 78.0
    static let drawerInset = 12.0, drawerPad = 22.0, sections = [118.0, 138.0, 136.0], controlTop = [42.0, 46.0, 44.0]
    static let optionWidth = 122.0, optionHeight = 58.0, optionGap = 6.0, strip = 72.0, arrow = 36.0, hourWidth = 82.0, hourHeight = 58.0
    static let pickRight = 26.0, optionLandsWidth = 86.0, optionLandsHeight = 24.0, optionLandsTop = 32.0, hourLandsWidth = 82.0, hourLandsHeight = 48.0, hourLandsTop = 68.0
    static let placeRow = 40.0, done = 62.0, doneSide = 30.0, doneGap = 22.0, art = 280.0, topPad = 14.0, stepsTop = 26.0, stageTop = 24.0, safeBottom = 12.0, fitExtra = 8.0
    static let title = 32.0, titleLine = 40.0, titleSpacing = -0.6, closeSize = 34.0, closeHit = 8.0, closeIcon = 17.0, checkIcon = 13.0, checkStroke = 2.5, dotText = 11.0, dotLine = 14.0, lineInset = 8.0, lineHeight = 1.5
    static let stepText = 9.0, stepTextLine = 14.0, stepSpacing = 0.3, stepMeasure = 240.0
    static let orderLeft = 28.0, orderTop = 30.0, orderText = 9.0, orderLine = 12.0, totalTop = 50.0, totalText = 24.0, totalLine = 30.0, totalSpacing = -0.4, placedTop = 90.0, placedText = 10.5, placedLine = 15.0
    static let cardRadius = 3.0, cardStroke = 1.0, shadowY = 8.0, shadowBlur = 22.0, shadowAlpha = 0.08, cardRuleLeft = 138.0, cardRuleTop = 22.0, cardRuleWidth = 1.0
    static let confirmRight = 14.0, confirmRadius = 2.0, confirmGap = 26.0, confirmText = 12.5, confirmLine = 16.0, confirmDisc = 24.0, confirmIcon = 14.0
    static let placeHeadingText = 9.0, placeHeadingLine = 12.0, placeValueText = 10.5, placeValueLine = 14.0, headingTop = 17.0, headingText = 9.5, headingLine = 12.0, headingSpacing = 0.3
    static let optionIcon = 16.0, optionText = 11.5, optionLine = 15.0, iconGap = 8.0, arrowIcon = 15.0, hourText = 13.0, hourLine = 17.0
    static let placeLeft = 24.0, placeText = 12.5, placeLine = 17.0, changeRight = 14.0, changeHit = 12.0, changeText = 10.5, changeLine = 14.0
    static let doneRadius = 2.0, doneText = 13.5, doneLine = 18.0, wonTop = 30.0, wonText = 21.0, wonLine = 26.0, wonSpacing = -0.2, returnTop = 34.0, returnText = 12.0, returnLine = 16.0
}

private enum UnlockingCheckoutStepsMotion {
    struct UnlockingCheckoutStepsSpring { let stiffness: Double; let damping: Double; let mass: Double }
    static let unfold = UnlockingCheckoutStepsSpring(stiffness: 150, damping: 17, mass: 1), fly = UnlockingCheckoutStepsSpring(stiffness: 130, damping: 18, mass: 1), slide = UnlockingCheckoutStepsSpring(stiffness: 240, damping: 17, mass: 1), bounce = UnlockingCheckoutStepsSpring(stiffness: 320, damping: 8, mass: 0.8), won = UnlockingCheckoutStepsSpring(stiffness: 150, damping: 13, mass: 1)
    static let unfoldAfter = 0.18, pressed = 0.9, bounceIn = 0.09, fill = 0.18, ready = 0.38, route = 0.7, routeAfter = 0.35, flood = 0.52, wonAfter = 0.7, checkedAfter = 0.2, checked = 0.32, leave = 0.36, press = 0.15
    static let reducedUnfold = 0.16, reducedSummary = 0.2, reducedFlood = 0.2, reducedWonAfter = 0.12, reducedWon = 0.2, reset = 0.3, loop = UnlockingCheckoutStepsLoop
    static let optionAt = 0.8, hourAt = 2.0, confirmAt = 3.6, doneAt = 5.6, returnAt = 8.2, userHold = 3.0, resumeAfter = 0.9
    static let confirmScale = 0.96, confirmCSS = 0.11, doneRise = 18.0, ghostScale = 1.6, wonScale = 0.7, wonOpacity = 2.5, frameLimit = 0.064, energyThreshold = 6e-9
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double { let u = min(1, max(0, (t - from) / (to - from))); return u * u * (3 - 2 * u) }
}

private enum UnlockingCheckoutStepsCurve {
    case linear, quadratic, outCubic, inOutCubic
    func sample(_ t: Double) -> Double {
        switch self { case .linear: return t; case .quadratic: return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2; case .outCubic: return 1 - pow(1 - t, 3); case .inOutCubic: return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
    }
}

private struct UnlockingCheckoutStepsScalar {
    private(set) var value: Double
    private(set) var active = false
    private var target: Double, from: Double
    private var velocity = 0.0, energy = 0.0, duration = 0.0
    private var start = Date.distantPast, last = Date.distantPast
    private var curve = UnlockingCheckoutStepsCurve.quadratic
    private var constants: UnlockingCheckoutStepsMotion.UnlockingCheckoutStepsSpring?
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func time(_ target: Double, _ duration: Double, _ curve: UnlockingCheckoutStepsCurve, at date: Date) {
        if !(active && constants == nil && self.target == target) { from = value; start = date }
        self.target = target; self.duration = duration; self.curve = curve; constants = nil; velocity = 0; active = true; advance(date)
    }
    mutating func spring(_ target: Double, _ constants: UnlockingCheckoutStepsMotion.UnlockingCheckoutStepsSpring, at date: Date) {
        if self.constants != nil && self.target == target { advance(date); return }
        if value == target { active = false; return }
        if self.constants == nil || !active { last = date; velocity = 0 }
        if velocity * (target - value) < 0 { velocity = 0 }
        self.target = target; self.constants = constants; active = true; energy = 0.5 * constants.stiffness * pow(value - target, 2); advance(date)
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        guard let constants else {
            let t = min(1, max(0, date.timeIntervalSince(start) / duration)); value = from + (target - from) * curve.sample(t); if t == 1 { active = false }; return
        }
        let dt = min(UnlockingCheckoutStepsMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(constants.stiffness / constants.mass), decay = constants.damping / (2 * constants.mass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped, wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt))); value = target + attenuation * wave
        } else {
            let b = velocity + omega * x, attenuation = exp(-omega * dt); value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * constants.stiffness * pow(value - target, 2) + 0.5 * constants.mass * velocity * velocity
        if energy == 0 || current / energy <= UnlockingCheckoutStepsMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private indirect enum UnlockingCheckoutStepsAnimation {
    case time(Double, Double, UnlockingCheckoutStepsCurve)
    case spring(Double, UnlockingCheckoutStepsMotion.UnlockingCheckoutStepsSpring)
    case delay(Double, UnlockingCheckoutStepsAnimation)
    case sequence([UnlockingCheckoutStepsAnimation])
}

private final class UnlockingCheckoutStepsAnimationNode {
    private(set) var value: Double
    private(set) var finished = false
    private var scalar: UnlockingCheckoutStepsScalar?
    private var previous: UnlockingCheckoutStepsAnimationNode?
    private var child: UnlockingCheckoutStepsAnimationNode?
    private var queue: [UnlockingCheckoutStepsAnimation] = []
    private var deadline: Date?
    private var delayed: UnlockingCheckoutStepsAnimation?
    init(_ kind: UnlockingCheckoutStepsAnimation, value: Double, at date: Date, previous: UnlockingCheckoutStepsAnimationNode? = nil) {
        self.value = value
        switch kind {
        case let .time(target, duration, curve): var next = previous?.scalar ?? UnlockingCheckoutStepsScalar(value); next.time(target, duration, curve, at: date); scalar = next; self.value = next.value
        case let .spring(target, constants): var next = previous?.scalar ?? UnlockingCheckoutStepsScalar(value); next.spring(target, constants, at: date); scalar = next; self.value = next.value
        case let .delay(seconds, next): deadline = date.addingTimeInterval(seconds); delayed = next; self.previous = previous
        case let .sequence(children): queue = children; startNext(at: date, previous: previous)
        }
    }
    private func startNext(at date: Date, previous: UnlockingCheckoutStepsAnimationNode?) {
        guard !queue.isEmpty else { finished = true; return }; child = UnlockingCheckoutStepsAnimationNode(queue.removeFirst(), value: value, at: date, previous: previous); value = child!.value
    }
    func advance(_ date: Date) {
        guard !finished else { return }
        if let deadline, let delayed {
            if date < deadline { previous?.advance(date); value = previous?.value ?? value; if previous?.finished == true { previous = nil }; return }
            child = UnlockingCheckoutStepsAnimationNode(delayed, value: value, at: date, previous: previous); self.deadline = nil; self.delayed = nil; previous = nil
        }
        if var scalar { scalar.advance(date); self.scalar = scalar; value = scalar.value; finished = !scalar.active; return }
        guard let child else { finished = true; return }; child.advance(date); value = child.value
        if child.finished { if queue.isEmpty { finished = true } else { startNext(at: date, previous: child) } }
    }
}

private struct UnlockingCheckoutStepsController {
    private(set) var value: Double
    private var root: UnlockingCheckoutStepsAnimationNode?
    var active: Bool { root != nil }
    init(_ value: Double) { self.value = value }
    mutating func jump(_ value: Double) { root = nil; self.value = value }
    mutating func assign(_ kind: UnlockingCheckoutStepsAnimation, at date: Date) {
        if case let .time(target, _, _) = kind, value == target { jump(target); return }
        root = UnlockingCheckoutStepsAnimationNode(kind, value: value, at: date, previous: root); advance(date)
    }
    mutating func advance(_ date: Date) { root?.advance(date); value = root?.value ?? value; if root?.finished == true { root = nil } }
}

private enum UnlockingCheckoutStepsStep { case details, summary, success }
private enum UnlockingCheckoutStepsHeld: String { case option, hour, confirm, done, `return` }

@MainActor
private final class UnlockingCheckoutStepsState: ObservableObject {
    private typealias M = UnlockingCheckoutStepsMotion
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var step = UnlockingCheckoutStepsStep.details
    private(set) var option: Int?
    private(set) var hour: Int
    private(set) var hourChosen = false
    private(set) var place = 0
    private(set) var held: UnlockingCheckoutStepsHeld?
    private(set) var second = UnlockingCheckoutStepsController(0), third = UnlockingCheckoutStepsController(0), optionOn = UnlockingCheckoutStepsController(0), optionAt = UnlockingCheckoutStepsController(0), optionScale = UnlockingCheckoutStepsController(1), hourOn = UnlockingCheckoutStepsController(0), hourScale = UnlockingCheckoutStepsController(1), ready = UnlockingCheckoutStepsController(0), summary = UnlockingCheckoutStepsController(0), route = UnlockingCheckoutStepsController(0), flood = UnlockingCheckoutStepsController(0), won = UnlockingCheckoutStepsController(0), checked = UnlockingCheckoutStepsController(0), staying = UnlockingCheckoutStepsController(1)
    private var leaving = false
    var options = UnlockingCheckoutStepsCheckoutOption.samples, hours = UnlockingCheckoutStepsStyle.hours, suggestedHour = 2
    var still = false
    var onConfirm: ((UnlockingCheckoutStepsCheckoutChoice) -> Void)?
    var onDone: ((UnlockingCheckoutStepsCheckoutChoice) -> Void)?
    var onReturn: (() -> Void)?
    var onClose: (() -> Void)?
    init(hours: [String], suggestedHour: Int) { self.hours = hours; self.suggestedHour = suggestedHour; hour = min(hours.count - 1, max(0, suggestedHour)) }
    var firstHour: Int { min(hours.count - 1, max(0, suggestedHour)) }
    var shown: UnlockingCheckoutStepsCheckoutOption { options.indices.contains(option ?? 0) ? options[option ?? 0] : UnlockingCheckoutStepsCheckoutOption.samples[0] }
    var choice: UnlockingCheckoutStepsCheckoutChoice { UnlockingCheckoutStepsCheckoutChoice(option: shown.label, hour: hours.indices.contains(hour) ? hours[hour] : "", place: shown.places.isEmpty ? "" : shown.places[place % shown.places.count]) }
    var complete: Bool { option != nil && hourChosen }
    func touched() { touches += 1 }
    private func unfold(_ section: inout UnlockingCheckoutStepsController, at date: Date) {
        section.assign(.delay(still ? 0 : M.unfoldAfter, still ? .time(1, M.reducedUnfold, .quadratic) : .spring(1, M.unfold)), at: date)
    }
    private func bounce(_ scale: inout UnlockingCheckoutStepsController, at date: Date) {
        guard !still else { return }; scale.assign(.sequence([.time(M.pressed, M.bounceIn, .quadratic), .spring(1, M.bounce)]), at: date)
    }
    func pickOption(_ index: Int, at date: Date = .now) {
        guard step == .details && options.indices.contains(index) else { return }
        if option != index { place = 0 }; option = index
        optionAt.assign(.spring(Double(index), M.slide), at: date); optionOn.assign(.time(1, M.fill, .quadratic), at: date); bounce(&optionScale, at: date); unfold(&second, at: date); revision &+= 1
    }
    func moveHour(_ by: Int, choose: Bool, at date: Date = .now) {
        guard step == .details && !hours.isEmpty else { return }; hour = min(hours.count - 1, max(0, hour + by))
        if choose {
            hourChosen = true; hourOn.assign(.time(1, M.fill, .quadratic), at: date); bounce(&hourScale, at: date); unfold(&third, at: date)
            ready.assign(.delay(still ? 0 : M.unfoldAfter, .time(1, M.ready, .outCubic)), at: date)
        }; revision &+= 1
    }
    func changePlace() { if !shown.places.isEmpty { place = (place + 1) % shown.places.count }; revision &+= 1 }
    func goTo(_ next: UnlockingCheckoutStepsStep, at date: Date = .now) {
        step = next; let target = next == .summary ? 1.0 : 0.0
        summary.assign(still ? .time(target, M.reducedSummary, .quadratic) : .spring(target, M.fly), at: date)
        if target == 1 { route.assign(.delay(still ? 0 : M.routeAfter, .time(1, M.route, .inOutCubic)), at: date) } else { route.jump(0) }; revision &+= 1
    }
    func confirm() { guard complete && step == .details else { return }; onConfirm?(choice); goTo(.summary) }
    func done(at date: Date = .now) {
        guard step == .summary else { return }; onDone?(choice); step = .success
        flood.assign(.time(1, still ? M.reducedFlood : M.flood, .inOutCubic), at: date)
        let wonAfter = still ? M.reducedWonAfter : M.flood * M.wonAfter
        won.assign(.delay(still ? 0 : wonAfter, still ? .time(1, M.reducedWon, .quadratic) : .spring(1, M.won)), at: date)
        checked.assign(.delay(still ? 0 : M.flood + M.checkedAfter, .time(1, M.checked, .outCubic)), at: date); revision &+= 1
    }
    func reset(covered: Bool, at date: Date = .now) {
        step = .details; option = nil; hour = firstHour; hourChosen = false; place = 0
        func back(_ value: inout UnlockingCheckoutStepsController) { if covered { value.jump(0) } else { value.assign(.time(0, M.reset, .quadratic), at: date) } }
        back(&second); back(&third); back(&optionOn); back(&optionAt); back(&hourOn); back(&ready); back(&summary); back(&route); revision &+= 1
    }
    private func clear() { flood.jump(0); won.jump(0); checked.jump(0); staying.jump(1); leaving = false }
    func finish(at date: Date = .now) {
        guard step == .success else { return }; onReturn?(); reset(covered: true, at: date)
        staying.assign(.time(0, M.leave, .quadratic), at: date); leaving = true; if !staying.active { clear() }; revision &+= 1
    }
    func close() { onClose?() }
    func advance(_ date: Date) {
        second.advance(date); third.advance(date); optionOn.advance(date); optionAt.advance(date); optionScale.advance(date); hourOn.advance(date); hourScale.advance(date); ready.advance(date); summary.advance(date); route.advance(date); flood.advance(date); won.advance(date); checked.advance(date); staying.advance(date)
        if leaving && !staying.active { clear() }; revision &+= 1
    }
    func autoplay(enabled: Bool) async {
        defer { held = nil; revision &+= 1 }
        guard enabled else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(M.userHold * 1e9)); try Task.checkCancellation()
                if step == .success { finish() } else { reset(covered: false) }
                try await Task.sleep(nanoseconds: UInt64(M.resumeAfter * 1e9)); try Task.checkCancellation()
            }
            var origin = Date.now
            while !Task.isCancelled {
                for (at, what) in [(M.optionAt, UnlockingCheckoutStepsHeld.option), (M.hourAt, .hour), (M.confirmAt, .confirm), (M.doneAt, .done), (M.returnAt, .return)] {
                    let wait = origin.addingTimeInterval(at).timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation()
                    held = what; revision &+= 1
                    let release = origin.addingTimeInterval(at + M.press).timeIntervalSinceNow; if release > 0 { try await Task.sleep(nanoseconds: UInt64(release * 1e9)) }; try Task.checkCancellation()
                    held = nil
                    switch what { case .option: pickOption(0); case .hour: moveHour(0, choose: true); case .confirm: confirm(); case .done: done(); case .return: finish() }; revision &+= 1
                }
                origin = origin.addingTimeInterval(M.loop); let wait = origin.timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
            }
        } catch { return }
    }
}

private struct UnlockingCheckoutStepsLoopKey: Equatable { let enabled: Bool; let touches: Int }

private enum UnlockingCheckoutStepsColor {
    private static let gamma = 2.2
    private static func channels(_ color: Color) -> (Double, Double, Double, Double) {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha); return (Double(red), Double(green), Double(blue), Double(alpha))
    }
    static func gammaBlend(_ first: Color, _ second: Color, _ t: Double) -> Color {
        let u = min(1, max(0, t)), a = channels(first), b = channels(second)
        func blend(_ x: Double, _ y: Double) -> Double { let start = pow(x, gamma); return (pow(start + (pow(y, gamma) - start) * u, 1 / gamma) * 255).rounded() }
        let alpha = ((a.3 + (b.3 - a.3) * u) * 1000).rounded() / 1000
        return .rgba(blend(a.0, b.0), blend(a.1, b.1), blend(a.2, b.2), (alpha * 255).rounded() / 255)
    }
}

private struct UnlockingCheckoutStepsDeparture: Identifiable { let id: Int; let text: String; let start: Date }
private struct UnlockingCheckoutStepsTextEntry {
    var text: String
    var start: Date?
    var leaving: [UnlockingCheckoutStepsDeparture] = []
}

private final class UnlockingCheckoutStepsTextClock {
    private var entries: [String: UnlockingCheckoutStepsTextEntry] = [:]
    private var nextID = 0
    private static let entering = 0.18, exiting = 0.12
    func update(_ key: String, text: String, exits: Bool, still: Bool, at date: Date) {
        guard var entry = entries[key] else { entries[key] = UnlockingCheckoutStepsTextEntry(text: text, start: still ? nil : date); return }
        entry.leaving.removeAll { date.timeIntervalSince($0.start) >= Self.exiting }
        if entry.text != text {
            if exits && !still { nextID += 1; entry.leaving.append(UnlockingCheckoutStepsDeparture(id: nextID, text: entry.text, start: date)) }
            if !exits || still { entry.leaving.removeAll() }
            entry.text = text; entry.start = still ? nil : date
        }; entries[key] = entry
    }
    func entry(_ key: String) -> UnlockingCheckoutStepsTextEntry { entries[key] ?? UnlockingCheckoutStepsTextEntry(text: "", start: nil) }
    static func opacity(start: Date?, date: Date, exiting: Bool = false) -> Double {
        guard let start else { return 1 }; let t = min(1, max(0, date.timeIntervalSince(start) / (exiting ? Self.exiting : Self.entering))), u = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        return exiting ? 1 - u : u
    }
}

private struct UnlockingCheckoutStepsFadingText<Content: View>: View {
    let entry: UnlockingCheckoutStepsTextEntry
    let date: Date
    @ViewBuilder var text: (String) -> Content
    var body: some View {
        ZStack {
            ForEach(entry.leaving) { old in text(old.text).opacity(UnlockingCheckoutStepsTextClock.opacity(start: old.start, date: date, exiting: true)) }
            text(entry.text).opacity(UnlockingCheckoutStepsTextClock.opacity(start: entry.start, date: date))
        }
    }
}

private struct UnlockingCheckoutStepsHit: View {
    let width: Double
    let height: Double
    let label: String
    var disabled = false
    var selected = false
    var hold: ((Bool) -> Void)? = nil
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var pendingOut: Task<Void, Never>?
    @GestureState private var active = false
    private static let retention = 20.0, bottomRetention = 30.0, minimumPress = 0.13
    private func contains(_ point: CGPoint) -> Bool { point.x > -Self.retention && point.x < width + Self.retention && point.y > -Self.retention && point.y < height + Self.bottomRetention }
    private func pressIn() { inside = true; activatedAt = .now; hold?(true) }
    private func pressOut() {
        inside = false; guard let hold else { return }; let wait = max(0, Self.minimumPress - Date.now.timeIntervalSince(activatedAt ?? .now))
        if wait == 0 { hold(false); return }
        pendingOut = Task { @MainActor in do { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)); try Task.checkCancellation(); hold(false) } catch { return } }
    }
    var body: some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($active) { _, active, _ in active = true }
                .onChanged { event in
                    if !began { pendingOut?.cancel(); began = true; pressIn() }
                    let next = contains(event.location); if next != inside { if next { pressIn() } else { pressOut() } }
                }
                .onEnded { _ in guard began else { return }; began = false; let perform = inside; if inside { pressOut() }; if perform { activate() } })
            .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { pressOut() } } } } }
            .onDisappear { pendingOut?.cancel(); hold?(false) }
            .allowsHitTesting(!disabled).disabled(disabled)
            .accessibilityElement(children: .ignore).accessibilityAddTraits(selected ? [.isButton, .isSelected] : [.isButton]).accessibilityLabel(label)
            .accessibilityAction { if !disabled { activate() } }
    }
}

private enum UnlockingCheckoutStepsText {
    static func width(_ text: String, size: Double, weight: UIFont.Weight, spacing: Double = 0, pixel: Double) -> Double {
        let raw = text.components(separatedBy: .newlines).map { ($0 as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: size, weight: weight), .kern: spacing]).width }.max() ?? 0
        return ceil(raw * pixel) / pixel
    }
    static func height(_ text: String, size: Double, line: Double, weight: UIFont.Weight, spacing: Double = 0, width: Double) -> Double {
        let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line
        let bounds = (text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: [.font: UIFont.systemFont(ofSize: size, weight: weight), .kern: spacing, .paragraphStyle: paragraph], context: nil)
        return max(line, ceil(bounds.height / line) * line)
    }
}

/// The drawer's clipped height and the floating picks share one spring, while hits follow the live layout.
struct UnlockingCheckoutSteps: View {
    var title: String
    var detailsStep: String
    var confirmationStep: String
    var orderLabel: String
    var total: String
    var placedLabel: String
    var optionLabel: String
    var options: [UnlockingCheckoutStepsCheckoutOption]
    var hours: [String]
    var suggestedHour: Int
    var changeLabel: String
    var confirmLabel: String
    var doneLabel: String
    var successTitle: String
    var returnLabel: String
    var autoPlay: Bool
    var onConfirm: ((UnlockingCheckoutStepsCheckoutChoice) -> Void)?
    var onDone: ((UnlockingCheckoutStepsCheckoutChoice) -> Void)?
    var onReturn: (() -> Void)?
    var onClose: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @StateObject private var state: UnlockingCheckoutStepsState
    @State private var textClock = UnlockingCheckoutStepsTextClock()
    @State private var confirmHeld = false
    @State private var doneHeld = false
    @State private var returnHeld = false
    @State private var touchBegan = false
    @GestureState private var touchActive = false
    private typealias S = UnlockingCheckoutStepsStyle
    private typealias M = UnlockingCheckoutStepsMotion
    private typealias C = UnlockingCheckoutStepsColor
    private typealias T = UnlockingCheckoutStepsText
    init(title: String = "Confirm", detailsStep: String = "DELIVERY DETAILS", confirmationStep: String = "CONFIRMATION", orderLabel: String = "Order 12329", total: String = "$19.11", placedLabel: String = "Placed on\n3 June 2022", optionLabel: String = "SELECT AN OPTION", options: [UnlockingCheckoutStepsCheckoutOption] = UnlockingCheckoutStepsCheckoutOption.samples, hours: [String] = UnlockingCheckoutStepsStyle.hours, suggestedHour: Int = 2, changeLabel: String = "Change", confirmLabel: String = "Confirm", doneLabel: String = "Done", successTitle: String = "Checkout successful!", returnLabel: String = "Return to Dashboard", autoPlay: Bool = false, onConfirm: ((UnlockingCheckoutStepsCheckoutChoice) -> Void)? = nil, onDone: ((UnlockingCheckoutStepsCheckoutChoice) -> Void)? = nil, onReturn: (() -> Void)? = nil, onClose: (() -> Void)? = nil) {
        self.title = title; self.detailsStep = detailsStep; self.confirmationStep = confirmationStep; self.orderLabel = orderLabel; self.total = total; self.placedLabel = placedLabel; self.optionLabel = optionLabel; self.options = options; self.hours = hours; self.suggestedHour = suggestedHour; self.changeLabel = changeLabel; self.confirmLabel = confirmLabel; self.doneLabel = doneLabel; self.successTitle = successTitle; self.returnLabel = returnLabel; self.autoPlay = autoPlay; self.onConfirm = onConfirm; self.onDone = onDone; self.onReturn = onReturn; self.onClose = onClose
        _state = StateObject(wrappedValue: UnlockingCheckoutStepsState(hours: hours, suggestedHour: suggestedHour))
    }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let stageTop = insets.top + S.topPad + S.header + S.stepsTop + S.dot + S.stageTop
            let cardWidth = width - S.side * 2, mapWidth = cardWidth - S.cardPad * 2 + 4
            let cardSummary = S.card + S.placeRow + mapWidth * UnlockingCheckoutStepsArt.mapRatio + 16
            let fit = min(1, (height - stageTop - max(insets.bottom, S.safeBottom)) / (S.card + S.sections.reduce(0, +) + S.fitExtra)), doneTop = stageTop + (cardSummary + S.doneGap) * fit
            TimelineView(.animation) { timeline in
                let _ = configure(date: .now)
                ZStack(alignment: .topLeading) {
                    header(width: width).offset(y: insets.top + S.topPad)
                    steps(width: width).offset(y: insets.top + S.topPad + S.header + S.stepsTop)
                    stage(width: width, cardWidth: cardWidth, cardSummary: cardSummary, mapWidth: mapWidth, date: timeline.date).scaleEffect(fit, anchor: .top).offset(y: stageTop)
                    success(width: width, height: height, safeTop: insets.top, fit: fit, doneTop: doneTop)
                }.frame(width: width, height: height, alignment: .topLeading).contentShape(Rectangle()).clipped()
                    .simultaneousGesture(DragGesture(minimumDistance: 0)
                        .updating($touchActive) { _, active, _ in active = true }
                        .onChanged { _ in if !touchBegan { touchBegan = true; state.touched() } }
                        .onEnded { _ in touchBegan = false })
                    .onChange(of: touchActive) { _, active in if !active { touchBegan = false } }
                    .offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
        }.background(S.paper.ignoresSafeArea()).preferredColorScheme(state.step == .success ? .dark : .light)
            .task(id: UnlockingCheckoutStepsLoopKey(enabled: autoPlay, touches: state.touches)) { await state.autoplay(enabled: autoPlay) }
    }
    private func configure(date: Date) {
        state.options = options; state.hours = hours; state.suggestedHour = suggestedHour; state.still = reduceMotion
        state.onConfirm = onConfirm; state.onDone = onDone; state.onReturn = onReturn; state.onClose = onClose
        let before = state.hour > 0 && hours.indices.contains(state.hour - 1) ? hours[state.hour - 1] : "", current = hours.indices.contains(state.hour) ? hours[state.hour] : "", next = hours.indices.contains(state.hour + 1) ? hours[state.hour + 1] : ""
        for (index, value) in [before, current, next].enumerated() { textClock.update("hour-\(index)", text: value, exits: true, still: reduceMotion, at: date) }
        textClock.update("pill-hour", text: current, exits: false, still: reduceMotion, at: date)
        textClock.update("place", text: state.choice.place, exits: true, still: reduceMotion, at: date)
    }
    private func manual(_ action: () -> Void) { UIImpactFeedbackGenerator(style: .light).impactOccurred(); action() }
    private func text(_ value: String, size: Double, line: Double, weight: UIFont.Weight = .regular, color: Color, width: Double? = nil, spacing: Double = 0, oneLine: Bool = false, centered: Bool = false) -> some View {
        let font = UIFont.systemFont(ofSize: size * fontScale, weight: weight), line = line * fontScale
        let available = width ?? T.width(value, size: size * fontScale, weight: weight, spacing: spacing, pixel: displayScale)
        let height = oneLine ? line : T.height(value, size: size * fontScale, line: line, weight: weight, spacing: spacing, width: available)
        return Text(value).font(Font(font)).foregroundStyle(color).tracking(spacing).lineSpacing(line - font.lineHeight).lineLimit(oneLine ? 1 : nil)
            .multilineTextAlignment(centered ? .center : .leading).padding(.vertical, (line - font.lineHeight) / 2)
            .frame(width: available, height: height, alignment: centered ? .center : .topLeading)
    }
    private func icon(_ name: String, size: Double, color: Color) -> some View {
        UnlockingCheckoutStepsFeatherIcon(name: name, size: size, color: color)
    }
    private func textWidth(_ value: String, size: Double, weight: UIFont.Weight, proposal: Double, spacing: Double = 0) -> Double {
        min(proposal, T.width(value, size: size * fontScale, weight: weight, spacing: spacing, pixel: displayScale))
    }
    private func header(width: Double) -> some View {
        HStack(spacing: 0) {
            text(title, size: S.title, line: S.titleLine, weight: .heavy, color: S.ink, width: width - (S.side + 4) - S.side - S.closeSize, spacing: S.titleSpacing, oneLine: true).accessibilityAddTraits(.isHeader)
            Spacer(minLength: 0)
            ZStack {
                Circle().fill(S.close).frame(width: S.closeSize, height: S.closeSize)
                icon("x", size: S.closeIcon, color: S.paper).accessibilityHidden(true)
                UnlockingCheckoutStepsHit(width: S.closeSize + S.closeHit * 2, height: S.closeSize + S.closeHit * 2, label: "Close", activate: state.close)
            }.frame(width: S.closeSize, height: S.closeSize)
        }.padding(.leading, S.side + 4).padding(.trailing, S.side).frame(width: width, height: S.header)
    }
    private var check: some View {
        Circle().fill(S.green).overlay { Circle().strokeBorder(S.checkBorder, lineWidth: S.checkStroke) }
            .overlay { icon("check", size: S.checkIcon, color: S.paper) }.frame(width: S.dot, height: S.dot).accessibilityHidden(true)
    }
    private func stepName(_ value: String, last: Bool) -> some View {
        let measured = min(S.stepMeasure, T.width(value, size: S.stepText * fontScale, weight: .heavy, spacing: S.stepSpacing, pixel: displayScale))
        let there = last ? M.smooth(state.summary.value, 0.4, 1) : 1 - M.smooth(state.summary.value, 0, 0.6)
        return text(value, size: S.stepText, line: S.stepTextLine, weight: .heavy, color: S.ink, width: measured, spacing: S.stepSpacing, oneLine: true)
            .offset(x: S.lineInset).frame(width: (measured + S.lineInset) * there, height: S.stepTextLine, alignment: .leading).opacity(there).clipped()
    }
    private func steps(width: Double) -> some View {
        HStack(spacing: 0) {
            check
            Rectangle().fill(S.line).frame(width: S.stepLine, height: S.lineHeight).padding(.horizontal, S.lineInset)
            ZStack {
                Circle().fill(S.ink).overlay { text("2", size: S.dotText, line: S.dotLine, weight: .heavy, color: S.paper) }.opacity(1 - M.smooth(state.summary.value, 0.3, 0.7))
                check.opacity(M.smooth(state.summary.value, 0.3, 0.7))
                UnlockingCheckoutStepsHit(width: S.dot, height: S.dot, label: detailsStep, disabled: state.step != .summary) { state.goTo(.details) }
            }.frame(width: S.dot, height: S.dot)
            stepName(detailsStep, last: false)
            Rectangle().fill(S.line).frame(width: S.stepLine, height: S.lineHeight).padding(.horizontal, S.lineInset)
            Circle().fill(C.gammaBlend(S.nextDot, S.ink, (state.summary.value - 0.3) / 0.5)).overlay { text("3", size: S.dotText, line: S.dotLine, weight: .heavy, color: S.paper) }.frame(width: S.dot, height: S.dot)
            stepName(confirmationStep, last: true)
        }.frame(width: width, height: S.dot)
    }
    private func stage(width: Double, cardWidth: Double, cardSummary: Double, mapWidth: Double, date: Date) -> some View {
        let s = state.summary.value, cardHeight = S.card + (cardSummary - S.card) * max(0, s)
        let drawerHeight = (S.sections[0] + S.sections[1] * max(0, state.second.value) + S.sections[2] * max(0, state.third.value)) * (1 - min(1, max(0, s)))
        let stageHeight = cardHeight + drawerHeight + S.doneGap + S.done
        return ZStack(alignment: .topLeading) {
            drawer(width: width, height: drawerHeight, date: date).offset(x: S.side + S.drawerInset, y: cardHeight)
            card(width: cardWidth, height: cardHeight, mapWidth: mapWidth).offset(x: S.side).zIndex(1)
            done(width: width - S.doneSide * 2).opacity(M.smooth(s, 0.6, 1)).offset(x: S.doneSide, y: cardHeight + drawerHeight + S.doneGap + (reduceMotion ? 0 : (1 - min(1, s)) * M.doneRise))
            floatingOption(width: width).zIndex(2)
            floatingHour(width: width, date: date).zIndex(2)
            if state.held == .option { Capsule(style: .circular).fill(S.paper.opacity(0.35)).frame(width: S.optionWidth, height: S.optionHeight).offset(x: S.side + S.drawerInset + S.drawerPad, y: S.card + S.controlTop[0]).allowsHitTesting(false).zIndex(3) }
        }.frame(width: width, height: stageHeight, alignment: .topLeading).allowsHitTesting(state.step != .success)
    }
    private func card(width: Double, height: Double, mapWidth: Double) -> some View {
        let s = state.summary.value, leaving = 1 - M.smooth(s, 0, 0.4), contentLeft = S.cardStroke + S.cardPad
        return ZStack(alignment: .topLeading) {
            text(orderLabel, size: S.orderText, line: S.orderLine, color: S.faint, width: width - contentLeft - S.cardStroke).offset(x: contentLeft, y: S.cardStroke + S.orderTop)
            text(total, size: S.totalText, line: S.totalLine, weight: .heavy, color: C.gammaBlend(S.ink, S.faint, s), width: width - contentLeft - S.cardStroke, spacing: S.totalSpacing).monospacedDigit().offset(x: contentLeft, y: S.cardStroke + S.totalTop)
            text(placedLabel, size: S.placedText, line: S.placedLine, color: S.faint, width: width - contentLeft - S.cardStroke).offset(x: contentLeft, y: S.cardStroke + S.placedTop)
            Rectangle().fill(S.cardRule).frame(width: S.cardRuleWidth, height: S.card - 44).offset(x: S.cardStroke + S.cardRuleLeft, y: S.cardStroke + S.cardRuleTop).opacity(leaving)
            confirm.opacity(leaving).offset(x: width - S.cardStroke - S.confirmRight - S.confirmWidth, y: S.cardStroke + (S.card - S.confirmHeight) / 2)
            extras(width: width - S.cardStroke * 2 - (S.cardPad - 2) * 2, mapWidth: mapWidth).offset(x: S.cardStroke + S.cardPad - 2, y: S.cardStroke + S.card - 2).opacity(M.smooth(s, 0.45, 1)).accessibilityHidden(state.step != .summary)
        }.frame(width: width, height: height, alignment: .topLeading)
            .background(RoundedRectangle(cornerRadius: S.cardRadius, style: .circular).fill(S.paper).shadow(color: .black.opacity(S.shadowAlpha), radius: S.shadowBlur / 2, y: S.shadowY))
            .overlay(RoundedRectangle(cornerRadius: S.cardRadius, style: .circular).strokeBorder(S.cardBorder, lineWidth: S.cardStroke))
    }
    private var confirm: some View {
        let down = confirmHeld || state.held == .confirm
        return ZStack {
            RoundedRectangle(cornerRadius: S.confirmRadius, style: .circular).fill(C.gammaBlend(S.locked, S.green, state.ready.value))
                .overlay {
                    HStack(spacing: S.confirmGap) {
                        text(confirmLabel, size: S.confirmText, line: S.confirmLine, weight: .bold, color: S.paper, width: textWidth(confirmLabel, size: S.confirmText, weight: .bold, proposal: S.confirmWidth))
                        Circle().fill(S.paper).overlay {
                            ZStack {
                                icon("arrow-right", size: S.confirmIcon, color: S.locked).opacity(1 - state.ready.value)
                                icon("arrow-right", size: S.confirmIcon, color: S.green).opacity(state.ready.value)
                            }.rotationEffect(.degrees(90 * (1 - state.ready.value)))
                        }.frame(width: S.confirmDisc, height: S.confirmDisc)
                    }
                }.scaleEffect(down ? M.confirmScale : 1).animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.confirmCSS), value: down).accessibilityHidden(true)
            UnlockingCheckoutStepsHit(width: S.confirmWidth, height: S.confirmHeight, label: confirmLabel, disabled: !state.complete, hold: { confirmHeld = $0 }) { manual { state.confirm() } }
        }.frame(width: S.confirmWidth, height: S.confirmHeight).allowsHitTesting(state.step == .details)
    }
    private func extras(width: Double, mapWidth: Double) -> some View {
        VStack(spacing: 0) {
            Rectangle().fill(S.hairline).frame(height: 1)
            HStack(spacing: 0) {
                text(state.shown.placeLabel, size: S.placeHeadingText, line: S.placeHeadingLine, weight: .heavy, color: S.faint, spacing: S.headingSpacing)
                Spacer(minLength: 0)
                text(state.choice.place, size: S.placeValueText, line: S.placeValueLine, weight: .heavy, color: S.ink)
            }.frame(width: width, height: S.placeRow)
            UnlockingCheckoutStepsRouteMap(width: mapWidth, drawn: state.route.value).frame(width: mapWidth).frame(width: width, alignment: .leading)
        }.frame(width: width, alignment: .leading).allowsHitTesting(false)
    }
    private func drawer(width: Double, height: Double, date: Date) -> some View {
        let drawerWidth = width - (S.side + S.drawerInset) * 2, inner = drawerWidth - S.drawerPad * 2
        return ZStack(alignment: .topLeading) {
            heading(optionLabel, width: inner).offset(y: S.headingTop)
            HStack(spacing: S.optionGap) {
                ForEach(options.indices, id: \.self) { index in
                    ZStack {
                        HStack(spacing: S.iconGap) {
                            icon(options[index].icon, size: S.optionIcon, color: S.grey)
                            text(options[index].label, size: S.optionText, line: S.optionLine, weight: .bold, color: S.grey, width: textWidth(options[index].label, size: S.optionText, weight: .bold, proposal: S.optionWidth))
                        }.accessibilityHidden(true)
                        UnlockingCheckoutStepsHit(width: S.optionWidth, height: S.optionHeight, label: options[index].label, selected: state.option == index) { manual { state.pickOption(index) } }
                    }.frame(width: S.optionWidth, height: S.optionHeight)
                }
            }.offset(y: S.controlTop[0])
            ZStack(alignment: .topLeading) {
                Rectangle().fill(S.drawerRule).frame(width: inner, height: 1)
                heading(state.shown.timeLabel, width: inner).offset(y: S.headingTop)
                hourStrip(width: inner, date: date).offset(y: S.controlTop[1])
            }.frame(width: inner, height: S.sections[1], alignment: .topLeading).opacity(M.smooth(state.second.value, 0.2, 0.9)).allowsHitTesting(state.option != nil).offset(y: S.sections[0])
            ZStack(alignment: .topLeading) {
                Rectangle().fill(S.drawerRule).frame(width: inner, height: 1)
                heading(state.shown.placeLabel, width: inner).offset(y: S.headingTop)
                place(width: inner, date: date).offset(y: S.controlTop[2])
            }.frame(width: inner, height: S.sections[2], alignment: .topLeading).opacity(M.smooth(state.third.value, 0.2, 0.9)).allowsHitTesting(state.hourChosen).offset(y: S.sections[0] + S.sections[1])
        }.frame(width: inner, height: height, alignment: .topLeading).padding(.horizontal, S.drawerPad)
            .frame(width: drawerWidth, height: height, alignment: .topLeading).background(S.drawer).contentShape(Rectangle()).clipped()
            .opacity(1 - M.smooth(state.summary.value, 0.2, 0.7)).allowsHitTesting(state.step == .details).accessibilityHidden(state.step != .details)
    }
    private func heading(_ value: String, width: Double) -> some View { text(value, size: S.headingText, line: S.headingLine, weight: .heavy, color: S.ink, width: textWidth(value, size: S.headingText, weight: .heavy, proposal: width, spacing: S.headingSpacing), spacing: S.headingSpacing) }
    private func hourStrip(width: Double, date: Date) -> some View {
        let slotWidth = (width - S.arrow * 2) / 3
        return HStack(spacing: 0) {
            ZStack {
                icon("chevron-left", size: S.arrowIcon, color: state.hour > 0 ? S.grey : S.suggested).accessibilityHidden(true)
                UnlockingCheckoutStepsHit(width: S.arrow, height: S.strip, label: "Earlier") { state.moveHour(-1, choose: false) }
            }.frame(width: S.arrow, height: S.strip)
            ForEach(0..<3, id: \.self) { slot in
                let entry = textClock.entry("hour-\(slot)")
                ZStack {
                    UnlockingCheckoutStepsFadingText(entry: entry, date: date) { value in text(value, size: S.hourText, line: S.hourLine, weight: .semibold, color: S.grey, width: textWidth(value, size: S.hourText, weight: .semibold, proposal: slotWidth)) }.accessibilityHidden(true)
                    UnlockingCheckoutStepsHit(width: slotWidth, height: S.strip, label: entry.text, disabled: entry.text.isEmpty, selected: slot == 1 && state.hourChosen) { manual { state.moveHour(slot - 1, choose: true) } }
                }.frame(width: slotWidth, height: S.strip)
            }
            ZStack {
                icon("chevron-right", size: S.arrowIcon, color: state.hour < hours.count - 1 ? S.grey : S.suggested).accessibilityHidden(true)
                UnlockingCheckoutStepsHit(width: S.arrow, height: S.strip, label: "Later") { state.moveHour(1, choose: false) }
            }.frame(width: S.arrow, height: S.strip)
        }.frame(width: width, height: S.strip).background(S.well)
    }
    private func place(width: Double, date: Date) -> some View {
        let changeWidth = T.width(changeLabel, size: S.changeText * fontScale, weight: .heavy, pixel: displayScale)
        return ZStack(alignment: .leading) {
            UnlockingCheckoutStepsFadingText(entry: textClock.entry("place"), date: date) { value in text(value, size: S.placeText, line: S.placeLine, weight: .medium, color: S.placeInk, width: textWidth(value, size: S.placeText, weight: .medium, proposal: width - S.placeLeft)) }.offset(x: S.placeLeft).accessibilityElement(children: .ignore).accessibilityLabel(state.choice.place)
            text(changeLabel, size: S.changeText, line: S.changeLine, weight: .heavy, color: S.teal).offset(x: width - S.changeRight - changeWidth).accessibilityHidden(true)
            UnlockingCheckoutStepsHit(width: changeWidth + S.changeHit * 2, height: S.changeLine * fontScale + S.changeHit * 2, label: changeLabel) { manual { state.changePlace() } }
                .offset(x: width - S.changeRight - changeWidth - S.changeHit)
        }.frame(width: width, height: S.strip, alignment: .leading).background(S.well)
    }
    private func done(width: Double) -> some View {
        ZStack {
            RoundedRectangle(cornerRadius: S.doneRadius, style: .circular).fill(doneHeld || state.held == .done ? S.donePressed : S.green)
            text(doneLabel, size: S.doneText, line: S.doneLine, weight: .bold, color: S.paper, width: textWidth(doneLabel, size: S.doneText, weight: .bold, proposal: width)).accessibilityHidden(true)
            UnlockingCheckoutStepsHit(width: width, height: S.done, label: doneLabel, hold: { doneHeld = $0 }) { manual { state.done() } }
        }.frame(width: width, height: S.done).allowsHitTesting(state.step == .summary)
    }
    private func floatingOption(width: Double) -> some View {
        let s = state.summary.value, drawerLeft = S.side + S.drawerInset + S.drawerPad, pickRight = width - S.side - S.pickRight
        let fromLeft = drawerLeft + state.optionAt.value * (S.optionWidth + S.optionGap), fromTop = S.card + S.controlTop[0]
        let w = S.optionWidth + (S.optionLandsWidth - S.optionWidth) * s, h = S.optionHeight + (S.optionLandsHeight - S.optionHeight) * s
        let light = state.optionOn.value * (1 - M.smooth(s, 0.2, 0.8)), background = C.gammaBlend(C.gammaBlend(S.suggested, S.ink, state.optionOn.value), S.ink.opacity(0), s / 0.8)
        return RoundedRectangle(cornerRadius: S.optionHeight / 2, style: .circular).fill(background)
            .overlay {
                HStack(spacing: S.iconGap) {
                    ZStack { icon(state.shown.icon, size: S.optionIcon, color: S.ink).opacity(1 - light); icon(state.shown.icon, size: S.optionIcon, color: S.paper).opacity(light) }
                    text(state.shown.label, size: S.optionText, line: S.optionLine, weight: .bold, color: C.gammaBlend(S.ink, S.paper, light), width: min(w, T.width(state.shown.label, size: S.optionText * fontScale, weight: .bold, pixel: displayScale)), oneLine: true)
                }.fixedSize(horizontal: true, vertical: false)
            }.frame(width: w, height: h).scaleEffect(state.optionScale.value)
            .offset(x: fromLeft + (pickRight - S.optionLandsWidth - fromLeft) * s, y: fromTop + (S.optionLandsTop - fromTop) * s).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func floatingHour(width: Double, date: Date) -> some View {
        let s = state.summary.value, fromLeft = (width - S.hourWidth) / 2, pickRight = width - S.side - S.pickRight
        let fromTop = S.card + S.sections[0] + S.controlTop[1] + (S.strip - S.hourHeight) / 2
        return RoundedRectangle(cornerRadius: S.hourHeight / 2, style: .circular).fill(C.gammaBlend(S.suggested, S.ink, state.hourOn.value))
            .overlay { UnlockingCheckoutStepsFadingText(entry: textClock.entry("pill-hour"), date: date) { value in text(value, size: S.hourText, line: S.hourLine, weight: .bold, color: C.gammaBlend(S.ink, S.paper, state.hourOn.value), width: textWidth(value, size: S.hourText, weight: .bold, proposal: S.hourWidth)) } }
            .frame(width: S.hourWidth, height: S.hourHeight + (S.hourLandsHeight - S.hourHeight) * s).scaleEffect(state.hourScale.value)
            .opacity(state.held == .hour ? 0.8 : M.smooth(state.second.value, 0.3, 0.9))
            .offset(x: fromLeft + (pickRight - S.hourLandsWidth - fromLeft) * s, y: fromTop + (S.hourLandsTop - fromTop) * s).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func success(width: Double, height: Double, safeTop: Double, fit: Double, doneTop: Double) -> some View {
        let f = state.flood.value, wide = M.smooth(f, 0, 0.4), tall = M.smooth(f, 0.08, 1)
        let titleWidth = min(width, T.width(successTitle, size: S.wonText * fontScale, weight: .heavy, spacing: S.wonSpacing, pixel: displayScale))
        let titleHeight = T.height(successTitle, size: S.wonText * fontScale, line: S.wonLine * fontScale, weight: .heavy, spacing: S.wonSpacing, width: titleWidth)
        let blockHeight = S.art + S.wonTop + titleHeight + S.returnTop + S.done, blockTop = safeTop + (height - safeTop - blockHeight) / 2
        return ZStack(alignment: .topLeading) {
            // The full overlay catches touches before the green flood reaches them.
            Color.clear.frame(width: width, height: height).contentShape(Rectangle()).onTapGesture { }.accessibilityHidden(true)
            Rectangle().fill(S.green).frame(width: width - S.doneSide * 2 * (1 - wide), height: S.done * fit + (height - S.done * fit) * tall)
                .offset(x: S.doneSide * (1 - wide), y: doneTop * (1 - tall)).opacity(f > 0 ? state.staying.value : 0)
            text(doneLabel, size: S.doneText, line: S.done, weight: .bold, color: S.paper, width: width, centered: true).frame(width: width, height: S.done)
                .scaleEffect(1 + M.ghostScale * f).offset(y: doneTop).opacity(f > 0 ? 1 - M.smooth(f, 0.15, 0.7) : 0).allowsHitTesting(false).accessibilityHidden(true)
            VStack(spacing: 0) {
                UnlockingCheckoutStepsSuccessArt(size: S.art, checked: state.checked.value)
                text(successTitle, size: S.wonText, line: S.wonLine, weight: .heavy, color: S.paper, width: titleWidth, spacing: S.wonSpacing).padding(.top, S.wonTop).accessibilityAddTraits(.isHeader)
                ZStack {
                    RoundedRectangle(cornerRadius: S.doneRadius, style: .circular).fill(returnHeld || state.held == .return ? S.returnPressed : S.returnFill)
                    text(returnLabel, size: S.returnText, line: S.returnLine, weight: .bold, color: S.paper, width: textWidth(returnLabel, size: S.returnText, weight: .bold, proposal: width - S.doneSide * 2)).accessibilityHidden(true)
                    UnlockingCheckoutStepsHit(width: width - S.doneSide * 2, height: S.done, label: returnLabel, hold: { returnHeld = $0 }) { manual { state.finish() } }
                }.frame(width: width - S.doneSide * 2, height: S.done).padding(.top, S.returnTop)
            }.frame(width: width, height: blockHeight).offset(y: blockTop)
                .frame(width: width, height: height, alignment: .topLeading).scaleEffect(reduceMotion ? 1 : M.wonScale + (1 - M.wonScale) * state.won.value)
                .opacity(min(1, state.won.value * M.wonOpacity) * state.staying.value).accessibilityHidden(state.step != .success)
        }.frame(width: width, height: height, alignment: .topLeading).allowsHitTesting(state.step == .success)
    }
}
