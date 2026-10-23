import Foundation
import UIKit

enum BeamCheckoutStepperF {
    static let head = 0
    static let glow = 1
    static let velocity = 2
    static let tone = 3
    static let warm = 4
    static let burst = 5
    static let surge = 6
    static let present = 7
    static let panelHeight = 8
    static let panelFrom = 9
    static let panelTo = 10
    static let panelMix = 11
    static let error = 12
    static let focusX = 13
    static let focusY = 14
    static let focusW = 15
    static let focusH = 16
    static let focusAlpha = 17
    static let ctaPress = 18
    static let ctaSplit = 19
    static let ctaLens = 20
    static let ctaSpin = 21
    static let ctaDone = 22
    static let backPress = 23
    static let segLead = 24
    static let segTrail = 25
    static let segPress = 26
    static let drag = 27
    static let nodeOn = 28
    static let nodeDone = 33
    static let nodeActive = 38
    static let nodeFlash = 43
    static let nodeShake = 48
    static let nodePress = 53
    static let rowActive = 58
    static let rowDone = 63
    static let rowRise = 68
    static let rowSweep = 73
    static let rowPress = 78
    static let fieldFloat = 83
    static let fieldError = 90
    static let size = 97
}

struct BeamCheckoutStepperForm {
    var values = Array(repeating: "", count: 7)
    var step = 0
    var furthest = 0
    var express = false
    var errorField = -1
    var errorMessage = ""
    var paid = false
    var newOrder = false
    var restarting = false

    var total: String { express ? "$184.00" : "$172.00" }

    var cta: String {
        if step < 3 || restarting { return "Continue" }
        if step == 3 { return "\(paid ? "Paid" : "Pay") \(total)" }
        return newOrder ? "New order" : "Paid \(total)"
    }

    static func format(_ field: Int, _ value: String) -> String {
        if field == 0 { return value.trimmingCharacters(in: .whitespacesAndNewlines) }
        if field < 3 { return value }
        let digits = value.filter(\.isNumber)
        if field == 3 { return String(digits.prefix(5)) }
        if field == 4 {
            let raw = Array(digits.prefix(16))
            return stride(from: 0, to: raw.count, by: 4).map { String(raw[$0..<min($0 + 4, raw.count)]) }.joined(separator: " ")
        }
        if field == 5 {
            let raw = String(digits.prefix(4))
            return raw.count >= 2 ? String(raw.prefix(2)) + "/" + String(raw.dropFirst(2)) : raw
        }
        return String(digits.prefix(4))
    }

    static func luhn(_ value: String) -> Bool {
        let digits = value.compactMap(\.wholeNumberValue)
        guard digits.count == 16, digits.contains(where: { $0 != 0 }) else { return false }
        let sum = digits.enumerated().reduce(0) { partial, pair in
            var digit = pair.element
            if pair.offset.isMultiple(of: 2) {
                digit *= 2
                if digit > 9 { digit -= 9 }
            }
            return partial + digit
        }
        return sum.isMultiple(of: 10)
    }

    func validation(now: Date = .now) -> (Int, String)? {
        if step == 0 {
            let email = values[0].trimmingCharacters(in: .whitespacesAndNewlines)
            if email.range(of: #"^[^\s@]+@[^\s@]+\.[^\s@]+$"#, options: .regularExpression) == nil {
                return (0, "Enter a valid email")
            }
        }
        if step == 1 {
            if values[1].trimmingCharacters(in: .whitespaces).count < 4 { return (1, "Enter your street address") }
            if values[2].trimmingCharacters(in: .whitespaces).count < 2 { return (2, "Enter your city") }
            if values[3].count != 5 || !values[3].allSatisfy(\.isNumber) { return (3, "Enter a 5-digit ZIP") }
        }
        if step == 2 {
            if !Self.luhn(values[4]) { return (4, "Check the card number") }
            let expiry = values[5].split(separator: "/", omittingEmptySubsequences: false)
            let month = expiry.count == 2 ? Int(expiry[0]) ?? 0 : 0
            let year = expiry.count == 2 && expiry[1].count == 2 ? 2000 + (Int(expiry[1]) ?? 0) : 0
            let calendar = Calendar.current
            let current = calendar.component(.year, from: now) * 12 + calendar.component(.month, from: now)
            if month < 1 || month > 12 || year * 12 + month < current { return (5, "Check the expiry date") }
            if !(3...4).contains(values[6].count) || !values[6].allSatisfy(\.isNumber) { return (6, "Enter the 3-digit code") }
        }
        return nil
    }
}

private struct BeamCheckoutStepperLane {
    var index: Int
    var at: Double
    var duration: Double
    var target: Double
    var from: Double
    var started = false
    var kind: Int
    var omega: Double
    var zeta: Double
}

enum BeamCheckoutStepperEvent {
    case settled(Int)
    case cleared
    case restarted
    case newOrder
    case paid
    case haptic(Int)
}

final class BeamCheckoutStepperMotion {
    private let h = 1.0 / 240.0
    var frame = Array(repeating: 0.0, count: BeamCheckoutStepperF.size)
    private var velocity = Array(repeating: 0.0, count: BeamCheckoutStepperF.size)
    private var presses = Array(repeating: 0.0, count: BeamCheckoutStepperF.size)
    private var lanes: [BeamCheckoutStepperLane] = []
    var events: [BeamCheckoutStepperEvent] = []
    var time = 0.0
    private(set) var labelMix = 1.0
    private var labelStarted = -1.0
    private var remainder = 0.0
    var step = 0
    var furthest = 0
    var express = false
    private var filled = Array(repeating: false, count: 7)
    var focusField = -1
    var errorField = -1
    var still: Bool
    private var mode = "idle"
    private var started = 0.0
    private var arrival = 0.0
    private var target = 0
    private var origin = 0
    private var flags = 0
    var silent = false
    var dragging = false
    private var grab = 0.0
    private var paidAt = -1.0
    private var newOrderSent = false
    private var pendingPanel = -1
    private var panelSwapAt = 0.0

    init(still: Bool) {
        self.still = still
        frame = Self.rest(0, 0, false, filled, -1, -1)
        frame[BeamCheckoutStepperF.present] = 0
        tween(BeamCheckoutStepperF.present, 1, 0.3)
    }

    static func bezier(_ t: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        if t <= 0 { return 0 }
        if t >= 1 { return 1 }
        var lo = 0.0
        var hi = 1.0
        var u = t
        for _ in 0..<18 {
            let q = 1 - u
            let x = 3 * q * q * u * x1 + 3 * q * u * u * x2 + u * u * u
            if x < t { lo = u } else { hi = u }
            u = (lo + hi) / 2
        }
        let q = 1 - u
        return 3 * q * q * u * y1 + 3 * q * u * u * y2 + u * u * u
    }

    private static func clamp(_ x: Double, _ lo: Double, _ hi: Double) -> Double { min(hi, max(lo, x)) }

    private static func ease(_ t: Double, _ kind: Int) -> Double {
        let u = clamp(t, 0, 1)
        if kind == 1 { return bezier(u, 0.23, 1, 0.32, 1) }
        if kind == 2 { return bezier(u, 0.77, 0, 0.175, 1) }
        return u
    }

    private static func height(_ step: Int, _ error: Double) -> Double {
        [108.0, 232, 172, 180, 68][step] + 88 + 24 * error
    }

    static func rest(_ step: Int, _ furthest: Int, _ express: Bool, _ filled: [Bool], _ error: Int, _ focus: Int) -> [Double] {
        let f = BeamCheckoutStepperF.self
        var result = Array(repeating: 0.0, count: f.size)
        let finished = step == 4
        result[f.head] = Double(step)
        result[f.warm] = finished ? 1 : 0
        result[f.burst] = 1
        result[f.present] = 1
        result[f.panelHeight] = height(step, error >= 0 ? 1 : 0)
        result[f.panelFrom] = Double(step)
        result[f.panelTo] = Double(step)
        result[f.panelMix] = 1
        result[f.error] = error >= 0 ? 1 : 0
        result[f.ctaSplit] = (1...3).contains(step) ? 1 : 0
        result[f.ctaDone] = finished ? 1 : 0
        result[f.segLead] = express ? 1 : 0
        result[f.segTrail] = express ? 1 : 0
        for i in 0..<5 {
            let done = finished || i < furthest
            let active = i == step && !finished
            result[f.nodeOn + i] = i <= step ? 1 : done ? 0.4 : 0
            result[f.nodeDone + i] = done && !active ? 1 : 0
            result[f.nodeActive + i] = active ? 1 : 0
            result[f.rowActive + i] = active ? 1 : 0
            result[f.rowDone + i] = done && !active ? 1 : 0
            result[f.rowRise + i] = active || (finished && i == 4) ? 1 : 0
            result[f.rowSweep + i] = active ? 1 : 0
        }
        for i in 0..<7 {
            result[f.fieldFloat + i] = filled[i] || i == focus ? 1 : 0
            result[f.fieldError + i] = i == error ? 1 : 0
        }
        if focus >= 0 {
            let rects = [(16.0, 52.0, 322.0), (16, 52, 322), (16, 116, 198), (222, 116, 116), (16, 52, 322), (16, 116, 157), (181, 116, 157)]
            result[f.focusX] = rects[focus].0
            result[f.focusY] = rects[focus].1
            result[f.focusW] = rects[focus].2
            result[f.focusH] = 56
            result[f.focusAlpha] = 1
        }
        return result
    }

    private func cancel(_ index: Int) { lanes.removeAll { $0.index == index } }

    private func lane(_ index: Int, _ target: Double, _ duration: Double, _ delay: Double, _ kind: Int, _ omega: Double = 0, _ zeta: Double = 0) {
        if delay == 0 {
            cancel(index)
            if frame[index] == target && abs(velocity[index]) < 0.00001 { return }
        }
        lanes.append(.init(index: index, at: time + delay, duration: duration, target: target, from: frame[index], kind: kind, omega: omega, zeta: zeta))
    }

    private func tween(_ index: Int, _ target: Double, _ duration: Double, _ delay: Double = 0, _ curve: Int = 1) {
        let f = BeamCheckoutStepperF.self
        let fade = still && ((index >= f.nodeOn && index < f.nodeFlash) || (index >= f.rowActive && index < f.rowPress) || index == f.ctaSplit || index == f.ctaDone)
        lane(index, target, fade ? 0.2 : duration, delay, fade ? 0 : curve)
    }

    private func spring(_ index: Int, _ target: Double, _ omega: Double, _ zeta: Double, _ delay: Double = 0) {
        lane(index, target, still ? 0.2 : 1.5, delay, still ? 0 : 3, omega, zeta)
    }

    private func impulse(_ index: Int, _ value: Double) {
        cancel(index)
        frame[index] = value
        velocity[index] = 0
    }

    private func fadeFocus() {
        let f = BeamCheckoutStepperF.self
        focusField = -1
        tween(f.focusAlpha, 0, 0.16)
        for i in 0..<7 { tween(f.fieldFloat + i, filled[i] ? 1 : 0, 0.18) }
    }

    private func clearMajor() {
        let f = BeamCheckoutStepperF.self
        lanes.removeAll { lane in
            let n = lane.index
            return n < f.fieldFloat && n != f.present && !(n >= f.focusX && n <= f.focusAlpha) && n != f.segLead && n != f.segTrail
        }
        flags = 0
        started = time
        origin = step
        pendingPanel = -1
        tween(f.drag, 0, 0.12)
        tween(f.tone, 0, 0.2)
        for i in 0..<5 { tween(f.nodeShake + i, 0, 0.15) }
    }

    private func departure(_ from: Int) {
        let f = BeamCheckoutStepperF.self
        let completed = from < furthest || mode == "advance" || mode == "pay"
        tween(f.nodeDone + from, completed ? 1 : 0, 0.22)
        tween(f.rowDone + from, completed ? 1 : 0, 0.36)
        tween(f.nodeActive + from, 0, 0.36)
        tween(f.rowActive + from, 0, 0.36)
        tween(f.rowRise + from, 0, 0.36)
        tween(f.rowSweep + from, 0, 0.36)
        fadeFocus()
    }

    private func panel(_ destination: Int, _ arrival: Double, _ delay: Double) {
        let f = BeamCheckoutStepperF.self
        let transitioning = frame[f.panelMix] < 0.999 && frame[f.panelTo] != frame[f.panelFrom]
        if !transitioning {
            frame[f.panelFrom] = Double(step)
            frame[f.panelTo] = Double(destination)
            frame[f.panelMix] = 0
        } else {
            pendingPanel = destination
            panelSwapAt = time + 0.12
        }
        errorField = -1
        tween(f.error, 0, 0.16)
        for i in 0..<7 { tween(f.fieldError + i, 0, 0.16) }
        if still {
            tween(f.panelMix, 0.4, 0.12, delay, 0)
            tween(f.panelHeight, Self.height(destination, 0), h, delay + 0.12, 0)
            tween(f.panelMix, 1, 0.18, max(delay + 0.12, arrival), 0)
        } else {
            if transitioning {
                tween(f.panelMix, frame[f.panelMix] >= 0.4 ? 0.4 : 0.3, 0.12, 0, 0)
                tween(f.panelMix, 0.4, max(0.02, arrival - 0.12), 0.12, 0)
            } else {
                tween(f.panelMix, 0.3, 0.16, delay + 0.06, 0)
                tween(f.panelMix, 0.4, max(0.02, arrival - delay - 0.22), delay + 0.22, 0)
            }
            tween(f.panelHeight, Self.height(destination, 0), 0.5, delay + 0.3, 2)
            tween(f.panelMix, 1, 0.38, arrival, 0)
        }
    }

    private func ignition(_ destination: Int, _ strength: Double, _ haptic: Int) {
        let f = BeamCheckoutStepperF.self
        if !still {
            impulse(f.nodeFlash + destination, strength)
            impulse(f.burst, mode == "pay" ? 2.6 : strength >= 1 ? 2 : 1.5)
            impulse(f.glow, mode == "advance" ? 1.6 : 1.2)
        }
        spring(f.nodeOn + destination, 1, 26, 0.55)
        tween(f.nodeActive + destination, mode == "pay" ? 0 : 1, still ? 0.2 : 0.18, 0, still ? 0 : 1)
        tween(f.nodeDone + destination, 0, 0.22)
        tween(f.rowDone + destination, 0, 0.22)
        tween(f.rowActive + destination, mode == "pay" ? 0 : 1, 0.24)
        spring(f.rowRise + destination, 1, 22, 0.5)
        tween(f.rowSweep + destination, mode == "pay" ? 0 : 1, 0.42)
        events.append(.haptic(haptic))
    }

    func advance() {
        let f = BeamCheckoutStepperF.self
        guard step < 3 else { return }
        clearMajor()
        mode = "advance"
        target = step + 1
        arrival = 0.54
        furthest = max(furthest, target)
        departure(step)
        if !still {
            tween(f.head, Double(target), 0.42, 0.12, 2)
            tween(f.glow, 1, 0.08, 0.12)
        } else { tween(f.head, Double(target), h, 0.12, 0) }
        panel(target, 0.54, 0)
        spring(f.ctaSplit, 1, 18, 0.8, 0.3)
    }

    func go(_ destination: Int) {
        let f = BeamCheckoutStepperF.self
        guard step != 4, mode != "pay", paidAt < 0, (0...furthest).contains(destination) else { return }
        clearMajor()
        mode = "go"
        target = destination
        arrival = min(0.64, 0.32 + 0.11 * abs(Double(destination) - frame[f.head]))
        departure(step)
        tween(f.head, Double(destination), still ? h : arrival, still ? 0.12 : 0, 2)
        if !still { tween(f.glow, 1, 0.08) }
        panel(destination, arrival, 0)
        spring(f.ctaSplit, destination > 0 ? 1 : 0, 18, 0.8)
    }

    func focus(_ field: Int) {
        let f = BeamCheckoutStepperF.self
        focusField = field
        for i in 0..<7 { tween(f.fieldFloat + i, filled[i] || i == field ? 1 : 0, 0.18) }
        if field < 0 { tween(f.focusAlpha, 0, 0.16); return }
        let rects = [(16.0, 52.0, 322.0), (16, 52, 322), (16, 116, 198), (222, 116, 116), (16, 52, 322), (16, 116, 157), (181, 116, 157)]
        let rect = [rects[field].0, rects[field].1, rects[field].2, 56]
        for i in 0..<4 {
            if frame[f.focusAlpha] < 0.001 { impulse(f.focusX + i, rect[i]) }
            else { spring(f.focusX + i, rect[i], 26, 0.82) }
        }
        tween(f.focusAlpha, 1, 0.14)
    }

    func setFilled(_ field: Int, _ value: Bool) {
        filled[field] = value
        tween(BeamCheckoutStepperF.fieldFloat + field, value || focusField == field ? 1 : 0, 0.18)
    }

    func reject(_ field: Int) {
        let f = BeamCheckoutStepperF.self
        clearMajor()
        mode = "reject"
        target = step
        errorField = field
        arrival = 0.22
        if frame[f.panelMix] < 1 {
            pendingPanel = step
            panelSwapAt = time + 0.12
            tween(f.panelMix, 0.4, 0.12, 0, 0)
            tween(f.panelMix, 1, 0.18, 0.12, 0)
        }
        if !still {
            tween(f.head, Double(step) + 0.4, 0.22)
            tween(f.glow, 1, 0.08)
        }
        tween(f.tone, 1, 0.11, 0.11)
    }

    func clearError() {
        let f = BeamCheckoutStepperF.self
        errorField = -1
        for i in 0..<7 { tween(f.fieldError + i, 0, 0.16) }
        tween(f.error, 0, 0.3, 0, 2)
        tween(f.panelHeight, Self.height(step, 0), 0.3, 0, 2)
    }

    func pay() {
        let f = BeamCheckoutStepperF.self
        guard step == 3, mode != "pay", paidAt < 0 else { return }
        clearMajor()
        mode = "pay"
        target = 4
        arrival = 1.2
        furthest = 4
        departure(3)
        tween(f.ctaSplit, 0, 0.38, 0, 2)
        if !still {
            tween(f.ctaLens, 1, 0.38, 0, 2)
            tween(f.head, 4, 0.9, 0.3, 2)
            tween(f.glow, 1.3, 0.9, 0.3)
            tween(f.burst, 1.4, 0.9, 0.3)
        } else { tween(f.head, 4, h, 1.2, 0) }
    }

    func restart() {
        let f = BeamCheckoutStepperF.self
        clearMajor()
        mode = "restart"
        target = 0
        arrival = 0.9
        paidAt = -1
        newOrderSent = false
        furthest = 0
        express = false
        errorField = -1
        fadeFocus()
        tween(f.warm, 0, 0.5)
        tween(f.ctaDone, 0, 0.3)
        tween(f.ctaLens, 0, 0.3)
        tween(f.ctaSplit, 0, 0.5)
        tween(f.surge, 0, 0.2)
        tween(f.segLead, 0, 0.3)
        tween(f.segTrail, 0, 0.3)
        tween(f.error, 0, 0.16)
        for i in 0..<7 {
            filled[i] = false
            tween(f.fieldFloat + i, 0, 0.18)
            tween(f.fieldError + i, 0, 0.16)
        }
        for i in 0..<5 {
            tween(f.nodeActive + i, 0, 0.2)
            tween(f.rowActive + i, 0, 0.2)
            tween(f.rowRise + i, 0, 0.2)
            tween(f.rowSweep + i, 0, 0.2)
        }
        if !still { tween(f.glow, 0.8, 0.08) }
        tween(f.head, 0, still ? h : 0.9, still ? 0.42 : 0, 2)
        if frame[f.panelMix] < 1 {
            pendingPanel = 0
            panelSwapAt = time + 0.12
            tween(f.panelMix, 0.4, 0.12, 0, 0)
        } else {
            frame[f.panelFrom] = Double(step)
            frame[f.panelTo] = 0
            frame[f.panelMix] = 0
        }
        tween(f.panelMix, 1, 0.5, 0.3, 0)
        tween(f.panelHeight, Self.height(0, 0), still ? h : 0.5, still ? 0.5 : 0.3, 2)
    }

    func press(_ index: Int, _ down: Bool) { presses[index] = down ? 1 : 0 }

    func changeLabel() {
        labelStarted = time
        labelMix = 0
    }

    func setDelivery(_ choice: Bool) {
        let f = BeamCheckoutStepperF.self
        if express != choice { events.append(.haptic(0)) }
        express = choice
        spring(f.segLead, choice ? 1 : 0, 30, 0.82)
        spring(f.segTrail, choice ? 1 : 0, 18, 0.74)
    }

    private func rubber(_ value: Double, _ lo: Double, _ hi: Double, _ band: Double) -> Double {
        if value < lo { return lo - band * (1 - exp((value - lo) / band)) }
        if value > hi { return hi + band * (1 - exp((hi - value) / band)) }
        return value
    }

    func beginDrag() {
        let f = BeamCheckoutStepperF.self
        guard step != 4, mode != "pay", paidAt < 0, furthest > 0 else { return }
        clearMajor()
        mode = "drag"
        dragging = true
        grab = frame[f.head]
        tween(f.drag, 1, 0.12)
        tween(f.nodeActive + step, 0.3, 0.12)
        if !still { tween(f.glow, 1, 0.08) }
    }

    private func ping(_ before: Double, _ after: Double, _ flash: Double) {
        guard !still else { return }
        for i in 0..<5 where (before < Double(i) && after >= Double(i)) || (before > Double(i) && after <= Double(i)) {
            impulse(BeamCheckoutStepperF.nodeFlash + i, flash)
            if dragging { events.append(.haptic(0)) }
        }
    }

    func drag(_ displacement: Double, _ speed: Double) {
        let f = BeamCheckoutStepperF.self
        guard dragging else { return }
        let previous = frame[f.head]
        frame[f.head] = rubber(grab + displacement, 0, Double(furthest), 0.18)
        velocity[f.head] = speed
        frame[f.velocity] = speed
        ping(previous, frame[f.head], 0.4)
    }

    func endDrag(_ speed: Double) {
        let f = BeamCheckoutStepperF.self
        guard dragging else { return }
        dragging = false
        mode = "release"
        started = time
        flags = 0
        target = Int(Self.clamp((frame[f.head] + 0.12 * speed).rounded(), 0, Double(furthest)))
        velocity[f.head] = speed
        arrival = 0.4
        if still { tween(f.head, Double(target), h, 0, 0) }
        else { spring(f.head, Double(target), 22, 0.72) }
        tween(f.drag, 0, 0.12)
        if target != step {
            departure(step)
            panel(target, arrival, 0)
        }
        spring(f.ctaSplit, target > 0 ? 1 : 0, 18, 0.8)
    }

    func dragDelivery(_ position: Double) {
        let f = BeamCheckoutStepperF.self
        cancel(f.segLead)
        frame[f.segLead] = rubber(position, 0, 1, 0.08)
        spring(f.segTrail, frame[f.segLead], 18, 0.74)
    }

    func endDelivery(_ speed: Double) -> Bool {
        let selected = frame[BeamCheckoutStepperF.segLead] + 0.1 * speed > 0.5
        setDelivery(selected)
        return selected
    }

    private func integrateLanes() {
        var i = 0
        while i < lanes.count {
            if time + 1e-9 < lanes[i].at { i += 1; continue }
            if !lanes[i].started {
                lanes[i].started = true
                lanes[i].from = frame[lanes[i].index]
                var j = i - 1
                while j >= 0 {
                    if lanes[j].index == lanes[i].index {
                        lanes.remove(at: j)
                        i -= 1
                    }
                    j -= 1
                }
            }
            let lane = lanes[i]
            let previous = frame[lane.index]
            let elapsed = max(0, time - lane.at)
            var finished: Bool
            if lane.kind == 3 {
                let displacement = previous - lane.target
                let acceleration = -lane.omega * lane.omega * displacement - 2 * lane.zeta * lane.omega * velocity[lane.index]
                velocity[lane.index] += acceleration * h
                frame[lane.index] += velocity[lane.index] * h
                finished = (abs(frame[lane.index] - lane.target) < 0.00001 && abs(velocity[lane.index]) < 0.0001) || elapsed >= lane.duration
            } else {
                frame[lane.index] = lane.from + (lane.target - lane.from) * Self.ease(elapsed / lane.duration, lane.kind)
                velocity[lane.index] = (frame[lane.index] - previous) / h
                finished = elapsed + 1e-9 >= lane.duration
            }
            if finished {
                frame[lane.index] = lane.target
                velocity[lane.index] = 0
                lanes.remove(at: i)
            } else { i += 1 }
        }
    }

    private func settle() {
        let f = BeamCheckoutStepperF.self
        step = target
        if errorField < 0 { events.append(.settled(step)) }
        if mode == "restart" {
            events.append(.restarted)
            mode = "idle"
        } else { mode = "settling" }
        let rest = Self.rest(step, furthest, express, filled, errorField, focusField)
        for i in 0..<5 {
            for offset in [f.nodeDone, f.rowDone, f.nodeOn, f.nodeActive, f.rowActive, f.rowRise, f.rowSweep] {
                tween(offset + i, rest[offset + i], 0.2)
            }
        }
    }

    private func advanceMode() {
        let f = BeamCheckoutStepperF.self
        let t = time - started
        if mode == "advance" || mode == "go" || mode == "release" {
            if flags & 1 == 0 && t + 1e-9 >= arrival {
                flags |= 1
                ignition(target, mode == "advance" ? 1 : 0.55, target > origin ? 2 : 1)
            }
            if flags & 1 != 0 && frame[f.panelMix] >= 1 && t >= arrival + (still ? 0.18 : 0.38) - 1e-9 { settle() }
        } else if mode == "reject" {
            if flags & 1 == 0 && t >= 0.22 - 1e-9 {
                flags |= 1
                if !still {
                    impulse(f.burst, 1.5)
                    spring(f.head, Double(step), 24, 0.6)
                }
                tween(f.fieldError + errorField, 1, 0.16)
                tween(f.error, 1, 0.3)
                tween(f.panelHeight, Self.height(step, 1), 0.3)
                focus(errorField)
                events.append(.haptic(3))
                tween(f.tone, 0, 0.5, still ? 1 : 0.68)
            }
            if !still && t >= 0.48 && t <= 0.84 {
                let q = t - 0.48
                frame[f.nodeShake + step] = 7 * sin(2 * .pi * q / 0.12) * (1 - q / 0.36)
            }
            if t >= 0.84 { frame[f.nodeShake + step] = 0 }
            if t >= 1.75 { mode = "settling"; settle() }
        } else if mode == "pay" {
            if flags & 1 == 0 && t >= 1.2 - 1e-9 {
                flags |= 1
                paidAt = time
                ignition(4, 1.2, 4)
                events.append(.paid)
                tween(f.warm, 1, 0.5)
                tween(f.ctaDone, 1, 0.24)
                if !still {
                    tween(f.surge, 1, 0.52, 0, 0)
                    spring(f.ctaLens, 0, 16, 0.62, 0.12)
                }
                tween(f.nodeDone + 4, 1, 0.22, 0.1)
                tween(f.rowDone + 4, 1, 0.22, 0.1)
            }
            if flags & 2 == 0 && t >= 1.5 - 1e-9 {
                flags |= 2
                frame[f.panelFrom] = 3
                frame[f.panelTo] = 4
                frame[f.panelMix] = 0
                tween(f.panelMix, 1, 0.5, 0, 0)
                tween(f.panelHeight, Self.height(4, 0), still ? h : 0.5, still ? 0.15 : 0, 2)
            }
            if !still && t >= 1.2 && t <= 1.75 {
                for i in 0..<4 {
                    let bit = 1 << (i + 2)
                    if flags & bit == 0 && frame[f.surge] >= Double(4 - i) / 4 - 1e-9 {
                        flags |= bit
                        impulse(f.nodeFlash + i, 0.8)
                    }
                }
            }
            if t >= 2 - 1e-9 && frame[f.panelMix] == 1 { settle() }
        } else if mode == "restart" {
            if flags & 1 == 0 && t >= 0.3 && frame[f.panelMix] >= 0.3 {
                flags |= 1
                events.append(.cleared)
            }
            for i in 1..<5 {
                let bit = 1 << (i + 1)
                if flags & bit == 0 && frame[f.head] <= Double(i) {
                    flags |= bit
                    tween(f.nodeOn + i, 0, 0.2)
                    tween(f.nodeDone + i, 0, 0.2)
                    tween(f.rowDone + i, 0, 0.2)
                }
            }
            if flags & 2 == 0 && t >= 0.9 - 1e-9 {
                flags |= 2
                ignition(0, 0.5, 1)
                tween(f.nodeDone, 0, 0.2)
                tween(f.rowDone, 0, 0.2)
                tween(f.nodeFlash, 0, 0.26)
                tween(f.glow, 0, 0.26)
                tween(f.burst, 1, 0.26)
                tween(f.rowRise, 1, 0.26)
                tween(f.rowSweep, 1, 0.26)
                tween(f.nodeOn, 1, 0.26)
            }
            if t >= 1.17 - 1e-9 { settle() }
        }
    }

    private func substep() {
        let f = BeamCheckoutStepperF.self
        time += h
        let previousHead = frame[f.head]
        integrateLanes()
        if pendingPanel >= 0 && time >= panelSwapAt - 1e-9 {
            frame[f.panelFrom] = Double(step)
            frame[f.panelTo] = Double(pendingPanel)
            pendingPanel = -1
        }
        advanceMode()
        if (mode == "go" || mode == "release") && flags & 1 == 0 { ping(previousHead, frame[f.head], 0.6) }
        let glowOwned = lanes.contains { $0.index == f.glow }
        let burstOwned = lanes.contains { $0.index == f.burst }
        let travelling = ["advance", "go", "release", "pay", "restart"].contains(mode) && flags & (mode == "restart" ? 2 : 1) == 0
        if !glowOwned && !travelling && !dragging { frame[f.glow] *= exp(-h / 0.16) }
        if !burstOwned { frame[f.burst] = 1 + (frame[f.burst] - 1) * exp(-h / (paidAt >= 0 ? 0.5 : mode == "reject" ? 0.3 : 0.35)) }
        for i in 0..<5 {
            if !lanes.contains(where: { $0.index == f.nodeFlash + i }) { frame[f.nodeFlash + i] *= exp(-h / 0.22) }
            if mode == "drag" || mode == "go" || mode == "release" {
                let ember = i < furthest ? 0.4 : 0
                let on = ember + (1 - ember) * Self.clamp((frame[f.head] - Double(i) + 0.18) / 0.18, 0, 1)
                if flags & 1 == 0 || dragging { frame[f.nodeOn + i] = on }
            }
        }
        let pressIndices = [f.ctaPress, f.backPress, f.segPress] + Array(f.nodePress..<(f.nodePress + 5)) + Array(f.rowPress..<(f.rowPress + 5))
        for index in pressIndices {
            let value = presses[index]
            frame[index] += (value - frame[index]) * (1 - exp(-h / (value != 0 ? 0.045 : 0.11)))
            if abs(value - frame[index]) < 1e-5 { frame[index] = value }
        }
        frame[f.ctaSplit] = Self.clamp(frame[f.ctaSplit], 0, 1)
        frame[f.velocity] = velocity[f.head]
        if frame[f.ctaLens] > 0 && frame[f.ctaDone] < 0.99999 { frame[f.ctaSpin] += h * .pi * 4 }
        if paidAt >= 0 && !newOrderSent && time - paidAt >= 2.6 {
            newOrderSent = true
            events.append(.newOrder)
        }
        if labelStarted >= 0 { labelMix = Self.clamp((time - labelStarted) / 0.26, 0, 1) }
        if still {
            frame[f.glow] = 0
            frame[f.burst] = 1
            frame[f.surge] = 0
            frame[f.ctaLens] = 0
            frame[f.velocity] = 0
            for i in 0..<5 { frame[f.nodeFlash + i] = 0; frame[f.nodeShake + i] = 0 }
        }
        if mode == "idle" || mode == "settling" {
            if frame[f.panelMix] == 1 { frame[f.panelFrom] = Double(step); frame[f.panelTo] = Double(step) }
            if frame[f.focusAlpha] == 0 { for i in f.focusX...f.focusH { frame[i] = 0 } }
            if abs(frame[f.ctaLens]) < 1e-5 { frame[f.ctaSpin] = 0 }
            if step == 4 && frame[f.surge] == 1 { frame[f.surge] = 0 }
            if lanes.isEmpty {
                let rest = Self.rest(step, furthest, express, filled, errorField, focusField)
                for i in 0..<f.size where abs(frame[i] - rest[i]) < 0.001 {
                    frame[i] = rest[i]
                    velocity[i] = 0
                }
                mode = "idle"
            }
        }
    }

    func tick(_ delta: Double) {
        remainder += max(0, min(delta, 1.0 / 30.0))
        while remainder + 1e-10 >= h {
            remainder -= h
            substep()
        }
    }
}
