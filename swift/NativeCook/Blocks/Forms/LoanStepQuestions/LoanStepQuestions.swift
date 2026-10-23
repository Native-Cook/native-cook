import SwiftUI
import UIKit
import CoreText

let LoanStepQuestionsLoop = 9.6

struct LoanStepQuestionsLoan: Equatable {
    let amount: Double
    let months: Double
    let monthly: Double
    let total: Double
}

private enum LoanStepQuestionsStyle {
    static let questions = ["How much would you like to borrow?", "Over how many months would you like to repay?", "Here is your loan. Does it look right?"]
    static let labels = ["Amount", "Months", "Monthly payment"]
    static let background = Color.white, ink = Color(hex: 0x3a3f58), pale = Color(hex: 0xc9ccd6), ticks = Color(hex: 0xc9ceea), blue = Color(hex: 0x3f5bd9), strip = Color(hex: 0xf4f5f8), footer = Color(hex: 0xd9dbe1), track = Color(hex: 0xc4c8dc)
    static let margin = 24.0, bar = 44.0, question = 28.0, questionLine = 36.0, labelAt = 98.0, label = 16.0, labelLine = 22.0
    static let figure = 80.0, figureBase = 198.0, figureTracking = -2.0, rulerAt = 256.0, stripAt = 300.0, stripHeight = 46.0, stripRadius = 8.0, stripGap = 10.0, summaryAt = 236.0, content = stripAt + stripHeight, lead = 0.24
    static let footerHeight = 56.0, trackHeight = 3.0, footerSafe = 12.0, minimumLead = 8.0
    static let end = 11.0, endRise = 4.0, rulerLeftInset = 26.0, rulerRightInset = 34.0, rulerHitHeight = 72.0, rulerBounds = 112.0
    static let stripText = 13.0, stripLine = 18.0, collapseRight = margin - 11, collapseIcon = 22.0, actionIcon = 18.0, actionText = 13.0, actionLine = 18.0, actionSpacing = 0.6, actionGap = 8.0
    static let dot = 56.0, dotAlpha = 0.16
}

private enum LoanStepQuestionsMotion {
    static let turn = 0.9, out = 0.52, inAt = 0.42, stagger = 0.12, parts = 5, travel = 70.0, smear = 7.0, dip = 14.0
    static let ghost = 0.26, ghostFrom = 0.3, ghostLeft = 0.4, fine = 10.0, glide = 0.16, rubber = 0.25
    static let stiffness = 190.0, damping = 24.0, mass = 1.0, frameLimit = 0.064, energyThreshold = 6e-9
    static let loop = LoanStepQuestionsLoop, autoAmount = 47000.0, autoMonths = 29.0, dragAmountAt = 0.8, dragAmountFor = 1.4, nextAt = 2.6, dragMonthsAt = 3.8, dragMonthsFor = 1.1, summaryAt = 5.3, restartAt = 7.6, pressFor = 0.14, userHold = 3.0, turnPoll = 0.1, resumeRest = 0.5
    static let line = 0.5, dotDragIn = 0.14, dotDragOut = 0.22, dotPressIn = 0.12, dotPressHold = 0.18, dotPressOut = 0.24, dotReach = 0.7
    static let fillCSS = 0.35, opacityCSS = 0.1, panOffset = 6.0, panFailure = 16.0
}

private enum LoanStepQuestionsCurve {
    case linear, quadratic, outQuadratic, outCubic, inOutCubic
    func sample(_ t: Double) -> Double {
        switch self {
        case .linear: return t
        case .quadratic: return t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        case .outQuadratic: return 1 - pow(1 - t, 2)
        case .outCubic: return 1 - pow(1 - t, 3)
        case .inOutCubic: return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
        }
    }
}

private struct LoanStepQuestionsScalar {
    private(set) var value: Double
    private(set) var active = false
    private var target: Double, from: Double
    private var velocity = 0.0, energy = 0.0, duration = 0.0
    private var start = Date.distantPast, last = Date.distantPast
    private var curve = LoanStepQuestionsCurve.quadratic
    private var springing = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func time(_ target: Double, _ duration: Double, _ curve: LoanStepQuestionsCurve, at date: Date) {
        if !(active && !springing && self.target == target) { from = value; start = date }
        self.target = target; self.duration = duration; self.curve = curve; active = true; springing = false; velocity = 0; advance(date)
    }
    mutating func spring(_ target: Double, at date: Date) {
        if springing && self.target == target { advance(date); return }
        if value == target { active = false; return }
        if !springing || !active { last = date; velocity = 0 }
        if velocity * (target - value) < 0 { velocity = 0 }
        self.target = target; springing = true; active = true; energy = 0.5 * LoanStepQuestionsMotion.stiffness * pow(value - target, 2); advance(date)
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        if !springing {
            let t = LoanStepQuestionsMath.clamp(date.timeIntervalSince(start) / duration, 0, 1)
            value = from + (target - from) * curve.sample(t); if t == 1 { active = false }; return
        }
        let m = LoanStepQuestionsMotion.self, dt = min(m.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(m.stiffness / m.mass), decay = m.damping / (2 * m.mass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped, wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt))); value = target + attenuation * wave
        } else {
            let b = velocity + omega * x, attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * m.stiffness * pow(value - target, 2) + 0.5 * m.mass * velocity * velocity
        if energy == 0 || current / energy <= m.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private indirect enum LoanStepQuestionsAnimation {
    case time(Double, Double, LoanStepQuestionsCurve)
    case spring(Double)
    case delay(Double, LoanStepQuestionsAnimation)
    case sequence([LoanStepQuestionsAnimation])
    var target: Double {
        switch self { case let .time(target, _, _), let .spring(target): return target; case let .delay(_, child): return child.target; case let .sequence(children): return children.last?.target ?? 0 }
    }
}

// A delay keeps the old wrapper and its child alive until the new child starts.
private final class LoanStepQuestionsAnimationNode {
    private(set) var value: Double
    private(set) var finished = false
    private var scalar: LoanStepQuestionsScalar?
    private var previous: LoanStepQuestionsAnimationNode?
    private var child: LoanStepQuestionsAnimationNode?
    private var queue: [LoanStepQuestionsAnimation] = []
    private var deadline: Date?
    private var delayed: LoanStepQuestionsAnimation?
    init(_ kind: LoanStepQuestionsAnimation, value: Double, at date: Date, previous: LoanStepQuestionsAnimationNode? = nil) {
        self.value = value
        switch kind {
        case let .time(target, duration, curve):
            var next = previous?.scalar ?? LoanStepQuestionsScalar(value); next.time(target, duration, curve, at: date); scalar = next; self.value = next.value
        case let .spring(target):
            var next = previous?.scalar ?? LoanStepQuestionsScalar(value); next.spring(target, at: date); scalar = next; self.value = next.value
        case let .delay(seconds, next): deadline = date.addingTimeInterval(seconds); delayed = next; self.previous = previous
        case let .sequence(children): queue = children; startNext(at: date, previous: previous)
        }
    }
    private func startNext(at date: Date, previous: LoanStepQuestionsAnimationNode?) {
        guard !queue.isEmpty else { finished = true; return }
        child = LoanStepQuestionsAnimationNode(queue.removeFirst(), value: value, at: date, previous: previous); value = child!.value
    }
    func advance(_ date: Date) {
        guard !finished else { return }
        if let deadline, let delayed {
            if date < deadline { previous?.advance(date); value = previous?.value ?? value; if previous?.finished == true { previous = nil }; return }
            child = LoanStepQuestionsAnimationNode(delayed, value: value, at: date, previous: previous); self.deadline = nil; self.delayed = nil; previous = nil
        }
        if var scalar { scalar.advance(date); self.scalar = scalar; value = scalar.value; finished = !scalar.active; return }
        guard let child else { finished = true; return }; child.advance(date); value = child.value
        if child.finished { if queue.isEmpty { finished = true } else { startNext(at: date, previous: child) } }
    }
}

private struct LoanStepQuestionsController {
    private(set) var value: Double
    private var root: LoanStepQuestionsAnimationNode?
    var active: Bool { root != nil }
    init(_ value: Double) { self.value = value }
    mutating func jump(_ target: Double) { root = nil; value = target }
    mutating func assign(_ kind: LoanStepQuestionsAnimation, at date: Date, still: Bool) {
        if still { jump(kind.target); return }
        if case .time = kind, value == kind.target { jump(kind.target); return }
        root = LoanStepQuestionsAnimationNode(kind, value: value, at: date, previous: root); advance(date)
    }
    mutating func advance(_ date: Date) { root?.advance(date); value = root?.value ?? value; if root?.finished == true { root = nil } }
}

private struct LoanStepQuestionsLimits: Equatable {
    var amount = 25000.0, minAmount = 1000.0, maxAmount = 100000.0, amountStep = 1000.0
    var months = 12.0, minMonths = 6.0, maxMonths = 96.0, rate = 12.9
    func amountPull(_ value: Double) -> Double { (value - minAmount) / amountStep * LoanStepQuestionsRuler.tickStep }
    func monthsPull(_ value: Double) -> Double { (value - minMonths) * LoanStepQuestionsRuler.tickStep }
    var amountReach: Double { amountPull(maxAmount) }
    var monthsReach: Double { monthsPull(maxMonths) }
}

@MainActor
private final class LoanStepQuestionsState: ObservableObject {
    private typealias M = LoanStepQuestionsMotion
    private typealias F = LoanStepQuestionsMath
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var step = 0, from = 0, to = 0
    private(set) var turning = false, sent = false, autoPressed = false, felt = false
    private(set) var amount: Double, months: Double
    private(set) var summed: LoanStepQuestionsLoan?
    private(set) var amountOffset: LoanStepQuestionsController, monthsOffset: LoanStepQuestionsController
    private(set) var progress = LoanStepQuestionsController(1), line = LoanStepQuestionsController(0), dotShown = LoanStepQuestionsController(0)
    private(set) var dotX = 0.0, dotY = 0.0, dotRides = false, dragFrom = 0.0
    private var amountSnap = false, monthsSnap = false
    private var amountTenths: Double?, monthsTicks: Double?
    private var reportedAmount: Double, reportedMonths: Double
    private(set) var glyphs: [[LoanStepQuestionsGlyphHistory]] = Array(repeating: [], count: 3)
    var limits: LoanStepQuestionsLimits
    var still = false, width = 0.0, rulerY = 0.0, nextY = 0.0
    var onChange: ((LoanStepQuestionsLoan) -> Void)?
    var onConfirm: ((LoanStepQuestionsLoan) -> Void)?
    var onCollapse: (() -> Void)?
    init(limits: LoanStepQuestionsLimits) {
        self.limits = limits; amount = F.clamp(limits.amount, limits.minAmount, limits.maxAmount); months = F.clamp(limits.months, limits.minMonths, limits.maxMonths)
        reportedAmount = amount; reportedMonths = months
        amountOffset = LoanStepQuestionsController(limits.amountPull(amount)); monthsOffset = LoanStepQuestionsController(limits.monthsPull(months))
    }
    var loan: LoanStepQuestionsLoan { F.cost(amount, months, limits.rate) }
    var summary: LoanStepQuestionsLoan { summed ?? loan }
    var share: Double {
        let start = from == 2 ? to : from, end = to == 2 ? from : to
        return Double(start) + Double(end - start) * F.smooth(progress.value, 0.3, 0.7)
    }
    var shownOffset: Double { amountOffset.value + (monthsOffset.value - amountOffset.value) * share }
    var dip: Double { from != to && from < 2 && to < 2 && !still ? (from < to ? -1 : 1) * M.dip * sin(2 * .pi * F.smooth(progress.value, 0, 1)) : 0 }
    func glyphHistory(_ figures: [String], at date: Date) {
        for page in 0..<3 {
            let chars = figures[page].unicodeScalars.map(String.init)
            if glyphs[page].count > chars.count { glyphs[page].removeLast(glyphs[page].count - chars.count) }
            for index in chars.indices {
                if index == glyphs[page].count { glyphs[page].append(LoanStepQuestionsGlyphHistory(now: chars[index], before: "", start: nil)) }
                else if glyphs[page][index].now != chars[index] { glyphs[page][index] = LoanStepQuestionsGlyphHistory(now: chars[index], before: glyphs[page][index].now, start: still ? nil : date) }
            }
        }
    }
    func touched() { autoPressed = false; dotShown.jump(0); touches += 1; revision &+= 1 }
    private func replaceOffset(_ page: Int, with kind: LoanStepQuestionsAnimation?, jump: Double? = nil, at date: Date) {
        if page == 0 {
            if amountSnap { felt = false; amountSnap = false }
            if let jump { amountOffset.jump(jump) } else if let kind { amountOffset.assign(kind, at: date, still: still) }
        } else {
            if monthsSnap { felt = false; monthsSnap = false }
            if let jump { monthsOffset.jump(jump) } else if let kind { monthsOffset.assign(kind, at: date, still: still) }
        }
    }
    private func updateLine(at date: Date) { line.assign(.time(Double(step + (sent ? 1 : 0)) / 3, M.line, .outCubic), at: date, still: still) }
    func go(_ next: Int, at date: Date = .now) {
        guard next != step && !turning else { return }
        turning = true; let previous = step; step = next; if next == 2 { summed = loan }
        from = previous; to = next; progress.jump(0); progress.assign(.time(1, still ? M.turn / 2 : M.turn, .linear), at: date, still: still)
        if !progress.active { turning = false }; updateLine(at: date); revision &+= 1
    }
    func next(auto: Bool, at date: Date = .now) {
        guard !turning else { return }
        if step < 2 { if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }; go(step + 1, at: date) }
        else if !sent { if !auto { UINotificationFeedbackGenerator().notificationOccurred(.success) }; sent = true; onConfirm?(summary); updateLine(at: date); revision &+= 1 }
    }
    func previous(at date: Date = .now) {
        guard !turning && step != 0 else { return }; UIImpactFeedbackGenerator(style: .light).impactOccurred(); sent = false; go(step - 1, at: date)
    }
    func restart(at date: Date = .now) {
        felt = false; sent = false
        let firstAmount = limits.amountPull(F.clamp(limits.amount, limits.minAmount, limits.maxAmount)), firstMonths = limits.monthsPull(F.clamp(limits.months, limits.minMonths, limits.maxMonths))
        replaceOffset(0, with: step == 2 ? nil : .spring(firstAmount), jump: step == 2 ? firstAmount : nil, at: date)
        replaceOffset(1, with: step == 2 ? nil : .spring(firstMonths), jump: step == 2 ? firstMonths : nil, at: date)
        go(0, at: date); updateLine(at: date); react(); revision &+= 1
    }
    func beginPan(at date: Date = .now) {
        let current = to == 0 ? amountOffset.value : monthsOffset.value
        replaceOffset(to == 0 ? 0 : 1, with: nil, jump: current, at: date); dragFrom = current; felt = true; touched()
    }
    func pan(_ translation: Double) {
        let page = to == 0 ? 0 : 1, reach = page == 0 ? limits.amountReach : limits.monthsReach, pulled = dragFrom - translation
        let value = pulled < 0 ? pulled * M.rubber : pulled > reach ? reach + (pulled - reach) * M.rubber : pulled
        replaceOffset(page, with: nil, jump: value, at: .now); react(); revision &+= 1
    }
    func endPan(_ velocity: Double, at date: Date = .now) {
        let page = to == 0 ? 0 : 1, reach = page == 0 ? limits.amountReach : limits.monthsReach
        let current = page == 0 ? amountOffset.value : monthsOffset.value, rest = F.clamp(F.rounded((current - velocity * M.glide) / LoanStepQuestionsRuler.tickStep) * LoanStepQuestionsRuler.tickStep, 0, reach)
        replaceOffset(page, with: .spring(rest), at: date)
        if page == 0 { amountSnap = amountOffset.active } else { monthsSnap = monthsOffset.active }
        if !(page == 0 ? amountSnap : monthsSnap) { felt = false }; react(); revision &+= 1
    }
    func nudge(_ by: Int, at date: Date = .now) {
        let page = step == 0 ? 0 : 1, pulled = page == 0 ? limits.amountPull(amount) : limits.monthsPull(months), reach = page == 0 ? limits.amountReach : limits.monthsReach
        replaceOffset(page, with: .spring(F.clamp(F.rounded(pulled / LoanStepQuestionsRuler.tickStep + Double(by)) * LoanStepQuestionsRuler.tickStep, 0, reach)), at: date); react(); revision &+= 1
    }
    func collapse() { touched(); onCollapse?() }
    private func react() {
        let tenths = F.rounded(amountOffset.value / LoanStepQuestionsRuler.tickStep * M.fine), ticks = F.rounded(monthsOffset.value / LoanStepQuestionsRuler.tickStep)
        if tenths != amountTenths {
            if felt, let old = amountTenths, floor(tenths / M.fine) != floor(old / M.fine) { UISelectionFeedbackGenerator().selectionChanged() }
            amountTenths = tenths; amount = F.clamp(limits.minAmount + tenths * limits.amountStep / M.fine, limits.minAmount, limits.maxAmount)
        }
        if ticks != monthsTicks {
            if felt && monthsTicks != nil { UISelectionFeedbackGenerator().selectionChanged() }
            monthsTicks = ticks; months = F.clamp(limits.minMonths + ticks, limits.minMonths, limits.maxMonths)
        }
        if reportedAmount != amount || reportedMonths != months { reportedAmount = amount; reportedMonths = months; onChange?(loan) }
    }
    func autoDrag(_ page: Int, seconds: Double, at date: Date = .now) {
        felt = false
        let start = page == 0 ? limits.amountPull(amount) : limits.monthsPull(months), rest = page == 0 ? limits.amountPull(F.clamp(M.autoAmount, limits.minAmount, limits.maxAmount)) : limits.monthsPull(F.clamp(M.autoMonths, limits.minMonths, limits.maxMonths))
        dragFrom = start; dotRides = true; dotX = width / 2 + F.clamp(rest - start, -(width - LoanStepQuestionsStyle.margin * 2) * M.dotReach, (width - LoanStepQuestionsStyle.margin * 2) * M.dotReach) / 2; dotY = rulerY
        dotShown.assign(.sequence([.time(1, M.dotDragIn, .quadratic), .delay(seconds, .time(0, M.dotDragOut, .quadratic))]), at: date, still: still)
        replaceOffset(page, with: .delay(M.dotDragIn, .time(rest, seconds, .inOutCubic)), at: date); react(); revision &+= 1
    }
    func autoPress(at date: Date = .now) {
        dotRides = false; dotX = width * 0.75; dotY = nextY
        dotShown.assign(.sequence([.time(1, M.dotPressIn, .quadratic), .delay(M.dotPressHold, .time(0, M.dotPressOut, .quadratic))]), at: date, still: still)
        autoPressed = true; revision &+= 1
    }
    func advance(_ date: Date) {
        amountOffset.advance(date); monthsOffset.advance(date); progress.advance(date); line.advance(date); dotShown.advance(date)
        if amountSnap && !amountOffset.active { amountSnap = false; felt = false }
        if monthsSnap && !monthsOffset.active { monthsSnap = false; felt = false }
        if turning && !progress.active { turning = false }; react(); revision &+= 1
    }
    func autoplay(enabled: Bool) async {
        guard enabled else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(M.userHold * 1e9))
                while turning { try await Task.sleep(nanoseconds: UInt64(M.turnPoll * 1e9)); try Task.checkCancellation() }
                restart(); try await Task.sleep(nanoseconds: UInt64((M.turn + M.resumeRest) * 1e9)); try Task.checkCancellation()
            }
            var origin = Date.now
            while !Task.isCancelled {
                let events: [(Double, Int)] = [(M.dragAmountAt, 0), (M.nextAt, 1), (M.nextAt + M.pressFor, 2), (M.dragMonthsAt, 3), (M.summaryAt, 1), (M.summaryAt + M.pressFor, 2), (M.restartAt, 4)]
                for (seconds, event) in events {
                    let wait = origin.addingTimeInterval(seconds).timeIntervalSinceNow
                    if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation()
                    switch event { case 0: autoDrag(0, seconds: M.dragAmountFor); case 1: autoPress(); case 2: autoPressed = false; next(auto: true); case 3: autoDrag(1, seconds: M.dragMonthsFor); default: restart() }
                    revision &+= 1
                }
                origin = origin.addingTimeInterval(M.loop); let wait = origin.timeIntervalSinceNow
                if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
            }
        } catch { return }
    }
}

private struct LoanStepQuestionsLoopKey: Equatable { let enabled: Bool; let touches: Int }

private enum LoanStepQuestionsMath {
    private typealias M = LoanStepQuestionsMotion
    private static let grouping = try! NSRegularExpression(pattern: #"\B(?=(\d{3})+(?!\d))"#)
    static func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
        if value.isNaN || low.isNaN || high.isNaN { return .nan }; return min(high, max(low, value))
    }
    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double { let u = clamp((t - from) / (to - from), 0, 1); return u * u * (3 - 2 * u) }
    static func placeOf(_ pages: Int, _ order: Int, _ from: Int, _ to: Int, _ progress: Double) -> Double {
        let here = (pages >> from) & 1, there = (pages >> to) & 1
        if here != 0 && there != 0 { return 0 }
        let way = to > from ? 1.0 : -1.0, spread = 1 + Double(M.parts - 1) * M.stagger
        if here != 0 { let u = clamp(clamp(progress / M.out, 0, 1) * spread - Double(order) * M.stagger, 0, 1); return -way * u * u }
        if there != 0 { let u = 1 - clamp(clamp((progress - M.inAt) / (1 - M.inAt), 0, 1) * spread - Double(order) * M.stagger, 0, 1); return way * u * u * u }
        return 2
    }
    static func rounded(_ value: Double) -> Double {
        guard value.isFinite else { return value }; let lower = floor(value); return value - lower < 0.5 ? lower : lower + 1
    }
    static func number(_ value: Double) -> String {
        if value.isNaN { return "NaN" }; if value == .infinity { return "Infinity" }; if value == -.infinity { return "-Infinity" }; if value == 0 { return "0" }
        let magnitude = abs(value), raw = String(magnitude).lowercased().split(separator: "e")
        let pieces = String(raw[0]).split(separator: ".", omittingEmptySubsequences: false)
        var digits = pieces.joined(), point = pieces[0].count + (raw.count > 1 ? Int(raw[1]) ?? 0 : 0)
        while digits.first == "0" && digits.count > 1 { digits.removeFirst(); point -= 1 }; while digits.last == "0" && digits.count > 1 { digits.removeLast() }
        let sign = value < 0 ? "-" : ""
        if magnitude >= 1e21 || magnitude < 1e-6 { let first = String(digits.removeFirst()), fraction = digits.isEmpty ? "" : "." + digits, power = point - 1; return sign + first + fraction + "e" + (power >= 0 ? "+" : "") + String(power) }
        if point <= 0 { return sign + "0." + String(repeating: "0", count: -point) + digits }
        if point >= digits.count { return sign + digits + String(repeating: "0", count: point - digits.count) }
        let split = digits.index(digits.startIndex, offsetBy: point); return sign + String(digits[..<split]) + "." + String(digits[split...])
    }
    static func grouped(_ value: Double) -> String {
        let text = number(rounded(value)); return grouping.stringByReplacingMatches(in: text, range: NSRange(text.startIndex..., in: text), withTemplate: ",")
    }
    static func short(_ value: Double) -> String { value >= 1000 ? number(rounded(value / 100) / 10) + "k" : number(value) }
    static func cost(_ amount: Double, _ months: Double, _ rate: Double) -> LoanStepQuestionsLoan {
        let monthlyRate = rate / 1200, monthly = monthlyRate == 0 ? amount / months : amount * monthlyRate / (1 - pow(1 + monthlyRate, -months))
        return LoanStepQuestionsLoan(amount: amount, months: months, monthly: rounded(monthly), total: rounded(monthly * months))
    }
}

private struct LoanStepQuestionsParagraph: UIViewRepresentable, Equatable {
    let text: String
    let size: Double
    let line: Double
    let light: Bool
    let medium: Bool
    let color: Color
    let centered: Bool
    let width: Double
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.text == rhs.text && lhs.size == rhs.size && lhs.line == rhs.line && lhs.light == rhs.light && lhs.medium == rhs.medium && lhs.color == rhs.color && lhs.centered == rhs.centered && lhs.width == rhs.width }
    private var attributes: [NSAttributedString.Key: Any] {
        let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line; paragraph.alignment = centered ? .center : .left
        return [.font: UIFont.systemFont(ofSize: size, weight: light ? .light : medium ? .medium : .regular), .foregroundColor: UIColor(color), .paragraphStyle: paragraph]
    }
    var height: Double {
        let bounds = (text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attributes, context: nil)
        return max(line, ceil(bounds.height / line) * line)
    }
    func makeUIView(context: Context) -> UILabel {
        let label = UILabel(); label.numberOfLines = 0; label.backgroundColor = .clear; label.isAccessibilityElement = false; return label
    }
    func updateUIView(_ label: UILabel, context: Context) { label.attributedText = NSAttributedString(string: text, attributes: attributes) }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? { CGSize(width: width, height: height) }
}

private enum LoanStepQuestionsGlyphMetrics {
    static let figure = UIFont.systemFont(ofSize: LoanStepQuestionsStyle.figure, weight: .light)
    static let end = UIFont.systemFont(ofSize: LoanStepQuestionsStyle.end, weight: .medium)
    // A system font cannot be made from its name: CoreText answers with Times New Roman.
    private static let figureCT = figure as CTFont
    private static let endCT = end as CTFont
    static func advance(_ char: String, figure: Bool) -> Double {
        var units = Array(char.utf16), glyphs = Array(repeating: CGGlyph(0), count: units.count)
        let count = units.count; guard count > 0 else { return 0 }
        let font = figure ? figureCT : endCT
        CTFontGetGlyphsForCharacters(font, &units, &glyphs, count)
        var width = CGSize.zero; CTFontGetAdvancesForGlyphs(font, .horizontal, &glyphs, &width, 1); return width.width
    }
    static let digit = "0123456789".unicodeScalars.map { advance(String($0), figure: true) }.max()! + LoanStepQuestionsStyle.figureTracking
    static func endWidth(_ text: String) -> Double { text.unicodeScalars.reduce(0) { $0 + advance(String($1), figure: false) } }
}

private struct LoanStepQuestionsGlyph: UIViewRepresentable, Equatable {
    let text: String
    let large: Bool
    let color: Color
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.text == rhs.text && lhs.large == rhs.large && lhs.color == rhs.color }
    func makeUIView(context: Context) -> UILabel {
        let label = UILabel(); label.numberOfLines = 1; label.backgroundColor = .clear; label.isAccessibilityElement = false; return label
    }
    func updateUIView(_ label: UILabel, context: Context) { label.text = text; label.font = large ? LoanStepQuestionsGlyphMetrics.figure : LoanStepQuestionsGlyphMetrics.end; label.textColor = UIColor(color) }
}

private struct LoanStepQuestionsDigit: View {
    let char: String
    let previous: String
    let start: Date?
    let width: Double
    let color: Color
    let date: Date
    private var fresh: Double {
        guard let start else { return 1 }; let t = LoanStepQuestionsMath.clamp(date.timeIntervalSince(start) / LoanStepQuestionsMotion.ghost, 0, 1); return 1 - pow(1 - t, 2)
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            if !previous.isEmpty { glyph(previous).opacity(LoanStepQuestionsMotion.ghostLeft * (1 - fresh)) }
            glyph(char).opacity(LoanStepQuestionsMotion.ghostFrom + (1 - LoanStepQuestionsMotion.ghostFrom) * fresh)
        }
    }
    private func glyph(_ text: String) -> some View {
        let advance = LoanStepQuestionsGlyphMetrics.advance(text, figure: true)
        return LoanStepQuestionsGlyph(text: text, large: true, color: color).equatable().frame(width: advance, height: LoanStepQuestionsGlyphMetrics.figure.lineHeight)
            .offset(x: (width - advance) / 2)
    }
}

private struct LoanStepQuestionsCell: Identifiable { let id: Int; let char: String; let x: Double; let width: Double }
private struct LoanStepQuestionsGlyphHistory { let now: String; let before: String; let start: Date? }

private struct LoanStepQuestionsFigure: View {
    let text: String
    let x: Double
    let baseline: Double
    let symbol: String
    let date: Date
    let history: [LoanStepQuestionsGlyphHistory]
    private var cells: [LoanStepQuestionsCell] {
        var cursor = x
        return text.unicodeScalars.enumerated().map { index, scalar in
            let char = String(scalar), isDigit = scalar.value >= 48 && scalar.value <= 57
            let width = isDigit ? LoanStepQuestionsGlyphMetrics.digit : LoanStepQuestionsGlyphMetrics.advance(char, figure: true) + LoanStepQuestionsStyle.figureTracking
            defer { cursor += width }; return LoanStepQuestionsCell(id: index, char: char, x: cursor, width: width)
        }
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(cells) { cell in
                LoanStepQuestionsDigit(char: cell.char, previous: history[cell.id].before, start: history[cell.id].start, width: cell.width, color: cell.char == symbol ? LoanStepQuestionsStyle.pale : LoanStepQuestionsStyle.ink, date: date)
                    .offset(x: cell.x, y: baseline - LoanStepQuestionsGlyphMetrics.figure.ascender)
            }
        }
    }
}

private struct LoanStepQuestionsPart<Content: View>: View {
    private typealias M = LoanStepQuestionsMotion
    let place: Double
    let still: Bool
    let bounds: CGRect
    let size: CGSize
    @ViewBuilder var content: () -> Content
    private var away: Double { min(1, abs(place)) }
    private var sigma: Double { still ? 0 : M.smear * 4 * away * (1 - away) }
    private static var weights: [Double] { let raw = (-4...4).map { exp(-0.5 * pow(Double($0) * 0.75, 2)) }, sum = raw.reduce(0, +); return raw.map { $0 / sum } }
    private var clipped: some View {
        content().frame(width: size.width, height: size.height, alignment: .topLeading).mask(alignment: .topLeading) { Rectangle().frame(width: bounds.width, height: bounds.height).offset(x: bounds.minX, y: bounds.minY) }
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            if sigma == 0 { clipped }
            else {
                // Shift the projected layer only vertically; additive samples approximate Skia's anisotropic blur.
                ForEach(0..<9, id: \.self) { index in clipped.offset(y: Double(index - 4) * 0.75 * sigma).opacity(Self.weights[index]).blendMode(.plusLighter) }
            }
        }.frame(width: size.width, height: size.height, alignment: .topLeading).compositingGroup()
            .mask(alignment: .topLeading) { Rectangle().frame(width: bounds.width, height: bounds.height).offset(x: bounds.minX, y: bounds.minY) }
            .opacity(1 - away).offset(y: still ? 0 : place * M.travel).allowsHitTesting(false).accessibilityHidden(true)
    }
}

private struct LoanStepQuestionsHit: View {
    let width: Double
    let height: Double
    let label: String
    var disabled = false
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
        inside = false; guard let hold else { return }
        let delay = max(0, Self.minimumPress - Date.now.timeIntervalSince(activatedAt ?? .now))
        if delay == 0 { hold(false); return }
        pendingOut = Task { @MainActor in do { try await Task.sleep(nanoseconds: UInt64(delay * 1e9)); try Task.checkCancellation(); hold(false) } catch { return } }
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
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton).accessibilityLabel(label)
            .accessibilityAction { if !disabled { activate() } }
    }
}

private final class LoanStepQuestionsRulerCache {
    private var width = -1.0
    private var path = Path()
    func strip(_ width: Double) -> Path {
        if self.width != width {
            self.width = width
            path = LoanStepQuestionsRuler.strip(left: LoanStepQuestionsStyle.margin + LoanStepQuestionsStyle.rulerLeftInset, right: width - LoanStepQuestionsStyle.margin - LoanStepQuestionsStyle.rulerRightInset, centre: width / 2)
        }
        return path
    }
}

/// Ruler reactions keep the live form distinct from the summary captured when it opens.
struct LoanStepQuestions: View {
    var amount: Double
    var minAmount: Double
    var maxAmount: Double
    var amountStep: Double
    var months: Double
    var minMonths: Double
    var maxMonths: Double
    var rate: Double
    var symbol: String
    var questions: [String]
    var labels: [String]
    var monthlyLabel: String
    var totalLabel: String
    var amountLabel: String
    var termLabel: String
    var rateLabel: String
    var monthsUnit: String
    var previousLabel: String
    var nextLabel: String
    var confirmLabel: String
    var sentLabel: String
    var autoPlay: Bool
    var onChange: ((LoanStepQuestionsLoan) -> Void)?
    var onConfirm: ((LoanStepQuestionsLoan) -> Void)?
    var onCollapse: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @ScaledMetric(relativeTo: .body) private var actionFont = LoanStepQuestionsStyle.actionText
    @StateObject private var state: LoanStepQuestionsState
    @State private var previousHeld = false
    @State private var nextHeld = false
    @State private var pointerBegan = false
    @State private var panActivated = false
    @State private var panFailed = false
    @State private var activationTranslation = 0.0
    @GestureState private var pointerActive = false
    @State private var rulerCache = LoanStepQuestionsRulerCache()
    private typealias S = LoanStepQuestionsStyle
    private typealias M = LoanStepQuestionsMotion
    private typealias F = LoanStepQuestionsMath
    init(amount: Double = 25000, minAmount: Double = 1000, maxAmount: Double = 100000, amountStep: Double = 1000, months: Double = 12, minMonths: Double = 6, maxMonths: Double = 96, rate: Double = 12.9, symbol: String = "$", questions: [String] = LoanStepQuestionsStyle.questions, labels: [String] = LoanStepQuestionsStyle.labels, monthlyLabel: String = "Monthly", totalLabel: String = "Total", amountLabel: String = "Amount", termLabel: String = "Term", rateLabel: String = "Rate", monthsUnit: String = "months", previousLabel: String = "PREV", nextLabel: String = "NEXT", confirmLabel: String = "CONFIRM", sentLabel: String = "SENT", autoPlay: Bool = false, onChange: ((LoanStepQuestionsLoan) -> Void)? = nil, onConfirm: ((LoanStepQuestionsLoan) -> Void)? = nil, onCollapse: (() -> Void)? = nil) {
        self.amount = amount; self.minAmount = minAmount; self.maxAmount = maxAmount; self.amountStep = amountStep; self.months = months; self.minMonths = minMonths; self.maxMonths = maxMonths; self.rate = rate; self.symbol = symbol; self.questions = questions; self.labels = labels; self.monthlyLabel = monthlyLabel; self.totalLabel = totalLabel; self.amountLabel = amountLabel; self.termLabel = termLabel; self.rateLabel = rateLabel; self.monthsUnit = monthsUnit; self.previousLabel = previousLabel; self.nextLabel = nextLabel; self.confirmLabel = confirmLabel; self.sentLabel = sentLabel; self.autoPlay = autoPlay; self.onChange = onChange; self.onConfirm = onConfirm; self.onCollapse = onCollapse
        _state = StateObject(wrappedValue: LoanStepQuestionsState(limits: LoanStepQuestionsLimits(amount: amount, minAmount: minAmount, maxAmount: maxAmount, amountStep: amountStep, months: months, minMonths: minMonths, maxMonths: maxMonths, rate: rate)))
    }
    private var limits: LoanStepQuestionsLimits { LoanStepQuestionsLimits(amount: amount, minAmount: minAmount, maxAmount: maxAmount, amountStep: amountStep, months: months, minMonths: minMonths, maxMonths: maxMonths, rate: rate) }
    private var figures: [String] { [symbol + F.grouped(state.amount), F.number(state.months), symbol + F.grouped(state.summary.monthly)] }
    private func item(_ list: [String], _ index: Int) -> String { list.indices.contains(index) ? list[index] : "" }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let footer = S.footerHeight + max(insets.bottom, S.footerSafe), room = height - insets.top - S.bar - footer - S.content
            let top = insets.top + S.bar + max(S.minimumLead, room * S.lead), rulerY = top + S.rulerAt
            TimelineView(.animation) { timeline in
                let _ = configure(width: width, rulerY: rulerY, nextY: height - footer + S.trackHeight + (S.footerHeight - S.trackHeight) / 2)
                let _ = state.glyphHistory(figures, at: .now)
                let strip = rulerCache.strip(width)
                ZStack(alignment: .topLeading) {
                    pages(width: width, height: height, top: top, strip: strip, date: timeline.date)
                    reading(width: width, top: top)
                    rulerHit(width: width).offset(y: rulerY - S.rulerHitHeight / 2)
                    ZStack {
                        Image(systemName: "arrow.down.right.and.arrow.up.left").font(.system(size: S.collapseIcon)).foregroundStyle(S.ink).accessibilityHidden(true)
                        LoanStepQuestionsHit(width: S.bar, height: S.bar, label: "Short form", activate: state.collapse)
                    }.frame(width: S.bar, height: S.bar).offset(x: width - S.collapseRight - S.bar, y: insets.top)
                    footerView(width: width, height: footer).offset(y: height - footer)
                    dot
                }.frame(width: width, height: height, alignment: .topLeading).clipped().offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
            .onAppear { state.advance(.now) }
        }.background(S.background.ignoresSafeArea()).preferredColorScheme(.light)
            .task(id: LoanStepQuestionsLoopKey(enabled: autoPlay, touches: state.touches)) { await state.autoplay(enabled: autoPlay) }
    }
    private func configure(width: Double, rulerY: Double, nextY: Double) {
        state.limits = limits; state.still = reduceMotion; state.width = width; state.rulerY = rulerY; state.nextY = nextY
        state.onChange = onChange; state.onConfirm = onConfirm; state.onCollapse = onCollapse
    }
    private func paragraph(_ text: String, size: Double, line: Double, light: Bool = false, medium: Bool = false, color: Color = S.ink, centered: Bool = false, width: Double) -> LoanStepQuestionsParagraph {
        LoanStepQuestionsParagraph(text: text, size: size, line: line, light: light, medium: medium, color: color, centered: centered, width: width)
    }
    private func part<Content: View>(_ pages: Int, _ order: Int, bounds: CGRect, size: CGSize, @ViewBuilder content: @escaping () -> Content) -> some View {
        LoanStepQuestionsPart(place: F.placeOf(pages, order, state.from, state.to, state.progress.value), still: reduceMotion, bounds: bounds, size: size, content: content)
    }
    private func pages(width: Double, height: Double, top: Double, strip: Path, date: Date) -> some View {
        let size = CGSize(width: width, height: height), inner = width - S.margin * 2, rulerY = top + S.rulerAt
        return ZStack(alignment: .topLeading) {
            ForEach(0..<3, id: \.self) { page in
                part(1 << page, 0, bounds: CGRect(x: 0, y: top - 30, width: width, height: S.questionLine * 2 + 60), size: size) {
                    paragraph(item(questions, page), size: S.question, line: S.questionLine, light: true, width: inner).equatable().frame(width: inner).offset(x: S.margin, y: top)
                }
                part(1 << page, 1, bounds: CGRect(x: 0, y: top + S.labelAt - 30, width: width, height: 82), size: size) {
                    paragraph(item(labels, page), size: S.label, line: S.labelLine, width: inner).equatable().frame(width: inner).offset(x: S.margin, y: top + S.labelAt)
                }
                part(1 << page, 2, bounds: CGRect(x: 0, y: top + S.figureBase - S.figure - 16, width: width, height: S.figure + 60), size: size) {
                    LoanStepQuestionsFigure(text: figures[page], x: S.margin - 3, baseline: top + S.figureBase, symbol: symbol, date: date, history: state.glyphs[page]).frame(width: width, height: height, alignment: .topLeading)
                }
            }
            part(0b011, 3, bounds: CGRect(x: 0, y: rulerY - S.rulerBounds / 2, width: width, height: S.rulerBounds), size: size) {
                ruler(width: width, rulerY: rulerY, strip: strip).offset(y: state.dip)
            }
            part(0b011, 4, bounds: CGRect(x: 0, y: top + S.stripAt - 30, width: width, height: S.stripHeight + 60), size: size) {
                totals(x: S.margin, y: top + S.stripAt, width: inner, left: monthlyLabel + ": " + symbol + F.grouped(state.loan.monthly), right: totalLabel + ": " + symbol + F.grouped(state.loan.total), rightBlue: true)
            }
            part(0b100, 3, bounds: CGRect(x: 0, y: top + S.summaryAt - 30, width: width, height: S.stripHeight + 60), size: size) {
                totals(x: S.margin, y: top + S.summaryAt, width: inner, left: amountLabel + ": " + symbol + F.grouped(state.summary.amount), right: termLabel + ": " + F.number(state.summary.months) + " " + monthsUnit, rightBlue: false)
            }
            part(0b100, 4, bounds: CGRect(x: 0, y: top + S.summaryAt + S.stripHeight + S.stripGap - 30, width: width, height: S.stripHeight + 60), size: size) {
                totals(x: S.margin, y: top + S.summaryAt + S.stripHeight + S.stripGap, width: inner, left: rateLabel + ": " + F.number(rate) + "%", right: totalLabel + ": " + symbol + F.grouped(state.summary.total), rightBlue: true)
            }
        }.frame(width: width, height: height, alignment: .topLeading)
    }
    private func totals(x: Double, y: Double, width: Double, left: String, right: String, rightBlue: Bool) -> some View {
        let first = paragraph(left, size: S.stripText, line: S.stripLine, medium: true, centered: true, width: width / 2), second = paragraph(right, size: S.stripText, line: S.stripLine, medium: true, color: rightBlue ? S.blue : S.ink, centered: true, width: width / 2)
        let top = y + (S.stripHeight - first.height) / 2
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: S.stripRadius, style: .circular).fill(S.strip).frame(width: width, height: S.stripHeight).offset(x: x, y: y)
            Rectangle().fill(S.background).frame(width: 1, height: S.stripHeight).offset(x: x + width / 2 - 0.5, y: y)
            first.equatable().frame(width: width / 2, height: first.height).offset(x: x, y: top)
            second.equatable().frame(width: width / 2, height: second.height).offset(x: x + width / 2, y: top)
        }
    }
    private func ruler(width: Double, rulerY: Double, strip: Path) -> some View {
        let ends = [[F.short(minAmount), F.short(maxAmount)], [F.number(minMonths), F.number(maxMonths)]]
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                var draw = context
                LoanStepQuestionsRuler.draw(&draw, strip: strip, offset: state.shownOffset, left: S.margin + S.rulerLeftInset, right: width - S.margin - S.rulerRightInset, centre: width / 2, y: rulerY, tickColor: S.ticks, markerColor: S.blue, background: S.background)
            }
            ForEach(0..<2, id: \.self) { page in
                LoanStepQuestionsGlyph(text: ends[page][0], large: false, color: S.ink).equatable().frame(width: LoanStepQuestionsGlyphMetrics.endWidth(ends[page][0]), height: LoanStepQuestionsGlyphMetrics.end.lineHeight)
                    .offset(x: S.margin, y: rulerY + S.endRise - LoanStepQuestionsGlyphMetrics.end.ascender).opacity(page == 0 ? 1 - state.share : state.share)
                LoanStepQuestionsGlyph(text: ends[page][1], large: false, color: S.ink).equatable().frame(width: LoanStepQuestionsGlyphMetrics.endWidth(ends[page][1]), height: LoanStepQuestionsGlyphMetrics.end.lineHeight)
                    .offset(x: width - S.margin - LoanStepQuestionsGlyphMetrics.endWidth(ends[page][1]), y: rulerY + S.endRise - LoanStepQuestionsGlyphMetrics.end.ascender).opacity(page == 0 ? 1 - state.share : state.share)
            }
        }
    }
    private func reading(width: Double, top: Double) -> some View {
        Color.clear.frame(width: width - S.margin * 2, height: S.figureBase).offset(x: S.margin, y: top).allowsHitTesting(false)
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isHeader)
            .accessibilityLabel(item(questions, state.step) + " " + item(labels, state.step) + ": " + (state.step == 1 ? F.number(state.months) + " " + monthsUnit : figures[state.step]))
    }
    private var dot: some View {
        let offset = state.to == 0 ? state.amountOffset.value : state.monthsOffset.value, ridden = state.dotRides ? offset - state.dragFrom : 0
        return Circle().fill(S.blue.opacity(S.dotAlpha)).frame(width: S.dot, height: S.dot).scaleEffect(0.7 + 0.3 * state.dotShown.value).opacity(state.dotShown.value)
            .offset(x: state.dotX - ridden - S.dot / 2, y: state.dotY - S.dot / 2).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func rulerHit(width: Double) -> some View {
        Color.clear.frame(width: width, height: S.rulerHitHeight).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($pointerActive) { _, active, _ in active = true }
                .onChanged { event in
                    guard state.step < 2 && !state.turning else { return }
                    if !pointerBegan { pointerBegan = true; panActivated = false; panFailed = false; activationTranslation = 0 }
                    if !panActivated {
                        if abs(event.translation.height) > M.panFailure { panFailed = true }
                        if !panFailed && abs(event.translation.width) > M.panOffset { panActivated = true; activationTranslation = event.translation.width; state.beginPan() }
                    }
                    if panActivated { state.pan(event.translation.width - activationTranslation) }
                }
                .onEnded { event in if panActivated { state.endPan(event.velocity.width) }; finishPan() })
            .onChange(of: pointerActive) { _, next in if !next { DispatchQueue.main.async { if !pointerActive && pointerBegan { finishPan() } } } }
            .onChange(of: state.step < 2 && !state.turning) { _, enabled in if !enabled { finishPan() } }
            .allowsHitTesting(state.step < 2 && !state.turning).accessibilityHidden(state.step == 2)
            .accessibilityElement(children: .ignore).accessibilityLabel(item(labels, min(state.step, 1)))
            .accessibilityValue(state.step == 0 ? figures[0] : F.number(state.months) + " " + monthsUnit)
            .accessibilityAdjustableAction { direction in state.nudge(direction == .increment ? 1 : -1) }
    }
    private func finishPan() { pointerBegan = false; panActivated = false; panFailed = false }
    private func footerView(width: Double, height: Double) -> some View {
        let actionHeight = height - S.trackHeight, last = state.step == 2, nextDown = nextHeld || state.autoPressed
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(S.footer)
            Rectangle().fill(S.track).frame(height: S.trackHeight)
            Rectangle().fill(S.blue).frame(width: width * state.line.value, height: S.trackHeight)
            HStack(spacing: 0) {
                ZStack(alignment: .top) {
                    HStack(spacing: S.actionGap) {
                        Image(systemName: "chevron.up").font(.system(size: S.actionIcon)).frame(width: S.actionIcon, height: S.actionIcon)
                        actionLabel(previousLabel, width: width / 2)
                    }.fixedSize(horizontal: true, vertical: false).foregroundStyle(state.step == 0 ? S.pale : S.blue).frame(width: width / 2, height: S.footerHeight - S.trackHeight).opacity(previousHeld ? 0.5 : 1).accessibilityHidden(true)
                    LoanStepQuestionsHit(width: width / 2, height: actionHeight, label: previousLabel, disabled: state.step == 0, hold: { previousHeld = $0 }) { state.touched(); state.previous() }
                }.frame(width: width / 2, height: actionHeight)
                ZStack(alignment: .top) {
                    Rectangle().fill(last ? S.blue : S.footer).animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.fillCSS), value: last)
                    HStack(alignment: .top, spacing: S.actionGap) {
                        actionLabel(last ? (state.sent ? sentLabel : confirmLabel) : nextLabel, width: width / 2)
                        nextIcon(last: last).frame(width: S.actionIcon, height: S.actionIcon)
                    }.fixedSize(horizontal: true, vertical: false).foregroundStyle(last ? S.background : S.blue).padding(.top, (S.footerHeight - S.trackHeight - S.actionLine) / 2).transaction { $0.animation = nil }.accessibilityHidden(true)
                    LoanStepQuestionsHit(width: width / 2, height: actionHeight, label: last ? (state.sent ? sentLabel : confirmLabel) : nextLabel, hold: { nextHeld = $0 }) { state.touched(); state.next(auto: false) }
                }.frame(width: width / 2, height: actionHeight).opacity(nextDown ? 0.5 : 1).animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.opacityCSS), value: nextDown)
            }.offset(y: S.trackHeight)
        }.frame(width: width, height: height)
    }
    private func actionLabel(_ text: String, width: Double) -> some View {
        let font = UIFont.systemFont(ofSize: actionFont, weight: .bold), line = S.actionLine * actionFont / S.actionText
        let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: S.actionSpacing, .paragraphStyle: paragraph]
        let measured = text.components(separatedBy: .newlines).map { ($0 as NSString).size(withAttributes: attributes).width }.max() ?? 0
        let textWidth = min(width, measured)
        let bounds = (text as NSString).boundingRect(with: CGSize(width: textWidth, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attributes, context: nil)
        let height = max(line, ceil(bounds.height / line) * line)
        return Text(text).font(.system(size: actionFont, weight: .bold)).tracking(S.actionSpacing).lineSpacing(line - font.lineHeight)
            .padding(.vertical, (line - font.lineHeight) / 2).frame(width: textWidth, height: height).fixedSize()
    }
    @ViewBuilder private func nextIcon(last: Bool) -> some View {
        if last && state.sent {
            ZStack {
                Image(systemName: "checkmark").offset(x: -3)
                Image(systemName: "checkmark").offset(x: 3)
            }.font(.system(size: S.actionIcon))
        } else { Image(systemName: last ? "checkmark" : "chevron.down").font(.system(size: S.actionIcon)) }
    }
}
