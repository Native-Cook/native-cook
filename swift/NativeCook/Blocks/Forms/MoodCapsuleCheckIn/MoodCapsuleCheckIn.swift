import SwiftUI
import UIKit

let MoodCapsuleCheckInLoop = 9.0

enum MoodCapsuleCheckInGlyph: String { case spark, bloom, dot, petal, clover }

struct MoodCapsuleCheckInMood: Equatable {
    var name: String
    var light: String
    var base: String
    var deep: String
    var tint: String
    var ink: String
    var glyph: MoodCapsuleCheckInGlyph
    var emotions: [String]
    static let samples = [
        MoodCapsuleCheckInMood(name: "Very unpleasant", light: "#8B66F2", base: "#6A3FD8", deep: "#4A27B4", tint: "#C9B6FF", ink: "#2A1670", glyph: .spark, emotions: ["Angry", "Anxious", "Drained"]),
        MoodCapsuleCheckInMood(name: "Unpleasant", light: "#4C84FF", base: "#2F63EE", deep: "#1C3FC2", tint: "#ABC6FF", ink: "#0F2A8A", glyph: .bloom, emotions: ["Sad", "Tired", "Stressed"]),
        MoodCapsuleCheckInMood(name: "Neutral", light: "#78D8E4", base: "#45BCCB", deep: "#2F99AB", tint: "#CAF7FD", ink: "#0E5560", glyph: .dot, emotions: ["Calm", "Content", "Indifferent"]),
        MoodCapsuleCheckInMood(name: "Pleasant", light: "#F3D83A", base: "#E6C417", deep: "#CDA70E", tint: "#FFF27E", ink: "#6B5600", glyph: .petal, emotions: ["Happy", "Grateful", "Relaxed"]),
        MoodCapsuleCheckInMood(name: "Very pleasant", light: "#FFA26A", base: "#F58A4B", deep: "#E3672A", tint: "#FFD6B6", ink: "#7A3210", glyph: .clover, emotions: ["Joyful", "Excited", "Proud"])
    ]
}

struct MoodCapsuleCheckInCheckIn {
    let mood: MoodCapsuleCheckInMood
    let emotion: String?
    let context: String?
    let scope: Int
}

private enum MoodCapsuleCheckInStyle {
    static let contexts = ["Work", "Family", "Friends", "Health"]
    static let background = Color(hex: 0x0e0e10), mute = Color(hex: 0x8e8e93), tile = Color(hex: 0x1c1c1e)
    static let segmentOn = "#1F1F22", segmentInk = "#D6D6DA"
    static let greyFill = Color.white.opacity(0.1), greyEdge = Color.white.opacity(0.13), greyMark = Color.white.opacity(0.34), chipFill = Color.white.opacity(0.2)
    static let designWidth = 402.0, rowAt = 0.52
    static let restWidth = 62.0, restHeight = 103.0, restGap = 9.5, smallWidth = 42.0, smallHeight = 73.0, largeWidth = 158.0, largeHeight = 306.0, openGap = 10.0
    static let markRest = 27.0, markSmall = 17.0, markLarge = 31.0, markY = -95.0
    static let chipWidth = 116.0, chipHeight = 38.0, chipRadius = 14.0, chipY = [-52.0, -11.0], chipText = 15.0, chipTextInset = 6.0
    static let sendY = 85.0, sendRadius = 34.5, questionY = -103.0, questionText = 20.0, nameY = -212.0, nameText = 21.0, textWidth = 320.0
    static let glowBlur = 16.0, bloomBlur = 34.0, textBlur = 8.0, contentBlur = 12.0, softBlur = 3.0, hazeRest = 120.0, hazeOpen = 340.0
    static let segmentsWidth = 289.0, segmentsHeight = 54.0, segmentsInset = 7.0, segment = (segmentsWidth - segmentsInset * 2) / 2
    static let segmentFill = Color.white.opacity(0.035), segmentBorder = Color.white.opacity(0.08), segmentText = 16.0, segmentLine = 20.0
    static let tileRight = 27.0, tileTop = 4.0, tileWidth = 48.0, tileHeight = 94.0, tileRadius = 24.0, tilePadding = 4.0, tileScale = 0.08
    static let settingsSize = 22.0, historySize = 23.0, segmentBottom = 24.0, segmentSafe = 16.0, spacing = -0.2
    static let none = -1, chip = 10, send = 12, inside = 20
    static let arrowWidth = 3.2, checkWidth = 3.4, greyStroke = 1.0, glassStroke = 1.5, outlineStroke = 2.0
}

private enum MoodCapsuleCheckInMotion {
    struct MoodCapsuleCheckInSpring { let stiffness: Double; let damping: Double; let mass: Double }
    static let openSpring = MoodCapsuleCheckInSpring(stiffness: 260, damping: 20, mass: 1)
    static let closeSpring = MoodCapsuleCheckInSpring(stiffness: 260, damping: 28, mass: 1)
    static let glideSpring = MoodCapsuleCheckInSpring(stiffness: 150, damping: 20, mass: 1)
    static let thumbSpring = MoodCapsuleCheckInSpring(stiffness: 300, damping: 26, mass: 1)
    static let chipSpring = MoodCapsuleCheckInSpring(stiffness: 420, damping: 18, mass: 1)
    static let swipe = 120.0, rubber = 0.3, throwTime = 0.15, press = 0.06, pressIn = 0.12, chipDip = 0.9
    static let sentIn = 0.28, sentHold = 0.9, sentTail = 0.001, outline = 0.22, veil = 0.26, reducedDip = 0.2
    static let loop = MoodCapsuleCheckInLoop, autoMood = 3, tapAt = 0.8, swipesAt = [(2.0, 2), (3.0, 3), (4.0, 4), (5.0, 3)]
    static let chipAt = 6.0, closeAt = 7.2, autoPress = 0.12, userHold = 3.0, closeFor = 0.7
    static let tapDistance = 12.0, tapDuration = 0.5, panOffset = 10.0, panFailure = 18.0, frameLimit = 0.064, energyThreshold = 6e-9
}

private struct MoodCapsuleCheckInScalar {
    private(set) var value: Double
    private(set) var active = false
    private var target: Double, from: Double
    private var velocity = 0.0, energy = 0.0, duration = 0.0
    private var start = Date.distantPast, last = Date.distantPast
    private var constants: MoodCapsuleCheckInMotion.MoodCapsuleCheckInSpring?
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func jump(_ value: Double) { self.value = value; target = value; active = false; constants = nil; velocity = 0 }
    mutating func time(_ next: Double, duration: Double, at date: Date = .now, still: Bool, force: Bool = false) {
        if still || (!force && value == next) { jump(next); return }
        if !(active && constants == nil && target == next) { start = date; from = value }
        target = next; self.duration = duration; constants = nil; active = true; velocity = 0
    }
    mutating func spring(_ next: Double, constants: MoodCapsuleCheckInMotion.MoodCapsuleCheckInSpring, at date: Date = .now, still: Bool) {
        if still { jump(next); return }
        if self.constants != nil && target == next { advance(date); return }
        if value == next { jump(next); return }
        let continuing = self.constants != nil && active
        if !continuing { velocity = 0; last = date }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; self.constants = constants; active = true; energy = 0.5 * constants.stiffness * pow(value - next, 2)
        advance(date)
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        guard let constants else {
            let t = MoodCapsuleCheckInGeometry.clamp(date.timeIntervalSince(start) / duration), u = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
            value = from + (target - from) * u; if t == 1 { active = false }; return
        }
        let dt = min(MoodCapsuleCheckInMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(constants.stiffness / constants.mass), decay = constants.damping / (2 * constants.mass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped
            let wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt))); value = target + attenuation * wave
        } else {
            let b = velocity + omega * x, attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * constants.stiffness * pow(value - target, 2) + 0.5 * constants.mass * velocity * velocity
        if energy == 0 || current / energy <= MoodCapsuleCheckInMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private enum MoodCapsuleCheckInSentPhase { case idle, drawing, holding(Date), tail(Date) }

@MainActor
private final class MoodCapsuleCheckInState: ObservableObject {
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var selected: Int?
    private(set) var emotion: Int?
    private(set) var context: Int?
    private(set) var scope = 0
    private(set) var open = MoodCapsuleCheckInScalar(0)
    private(set) var focus: MoodCapsuleCheckInScalar
    private(set) var opened = 0.0
    private(set) var aim: Int
    private(set) var pressed = MoodCapsuleCheckInStyle.none
    private(set) var outline = MoodCapsuleCheckInScalar(0)
    private(set) var thumb = MoodCapsuleCheckInScalar(0)
    private(set) var sent = MoodCapsuleCheckInScalar(0)
    private(set) var firstDip = MoodCapsuleCheckInScalar(1)
    private(set) var secondDip = MoodCapsuleCheckInScalar(1)
    private(set) var veil = MoodCapsuleCheckInScalar(1)
    private(set) var downs: [MoodCapsuleCheckInScalar]
    private(set) var sendDown = MoodCapsuleCheckInScalar(0)
    private var forgetPending = false
    private var sentPhase = MoodCapsuleCheckInSentPhase.idle
    private var dragFrom = 0.0
    var moods: [MoodCapsuleCheckInMood]
    var contexts: [String] = MoodCapsuleCheckInStyle.contexts
    var still = false
    var onMoodChange: ((MoodCapsuleCheckInMood) -> Void)?
    var onSubmit: ((MoodCapsuleCheckInCheckIn) -> Void)?
    var onSettings: (() -> Void)?
    var onHistory: (() -> Void)?
    init(moods: [MoodCapsuleCheckInMood]) {
        self.moods = moods; aim = min(MoodCapsuleCheckInMotion.autoMood, moods.count - 1)
        focus = MoodCapsuleCheckInScalar(Double(aim)); downs = moods.map { _ in MoodCapsuleCheckInScalar(0) }
    }
    func configure(moods: [MoodCapsuleCheckInMood], contexts: [String], still: Bool, onMoodChange: ((MoodCapsuleCheckInMood) -> Void)?, onSubmit: ((MoodCapsuleCheckInCheckIn) -> Void)?, onSettings: (() -> Void)?, onHistory: (() -> Void)?) {
        self.moods = moods; self.contexts = contexts; self.still = still
        self.onMoodChange = onMoodChange; self.onSubmit = onSubmit; self.onSettings = onSettings; self.onHistory = onHistory
        if downs.count < moods.count { downs.append(contentsOf: (downs.count..<moods.count).map { _ in MoodCapsuleCheckInScalar(0) }) }
        if downs.count > moods.count { downs.removeLast(downs.count - moods.count) }
    }
    private func move(_ next: Double, focusValue: Bool, spring: MoodCapsuleCheckInMotion.MoodCapsuleCheckInSpring, at date: Date) {
        if focusValue { focus.spring(next, constants: spring, at: date, still: still) }
        else { open.spring(next, constants: spring, at: date, still: still) }
        if still { veil.jump(0.2); veil.time(1, duration: MoodCapsuleCheckInMotion.veil, at: date, still: true) }
    }
    func touched() { touches += 1 }
    func choose(_ index: Int, auto: Bool, at date: Date = .now) {
        guard moods.indices.contains(index) else { return }
        if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        focus.jump(Double(index)); aim = index; opened = 1; selected = index; forgetPending = false
        onMoodChange?(moods[index]); move(1, focusValue: false, spring: MoodCapsuleCheckInMotion.openSpring, at: date); revision &+= 1
    }
    func glide(_ index: Int, auto: Bool, at date: Date = .now) {
        guard moods.indices.contains(index) else { return }
        if selected != index {
            if !auto { UISelectionFeedbackGenerator().selectionChanged() }
            emotion = nil; selected = index; onMoodChange?(moods[index])
        }
        aim = index; move(Double(index), focusValue: true, spring: MoodCapsuleCheckInMotion.glideSpring, at: date); revision &+= 1
    }
    private func forget() {
        emotion = nil; context = nil; sent.jump(0); sentPhase = .idle; forgetPending = false
    }
    func close(auto: Bool, fromSubmission: Bool = false, at date: Date = .now) {
        guard selected != nil || fromSubmission else { return }
        if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        opened = 0; selected = nil; forgetPending = true
        move(0, focusValue: false, spring: MoodCapsuleCheckInMotion.closeSpring, at: date)
        if !open.active { forget() }; revision &+= 1
    }
    func pick(_ which: Int, auto: Bool, at date: Date = .now) {
        guard let selected, moods.indices.contains(selected) else { return }
        if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        func next(_ value: Int?, count: Int) -> Int? { value == nil ? 0 : value! + 1 < count ? value! + 1 : nil }
        if which == 0 {
            emotion = next(emotion, count: moods[selected].emotions.count); firstDip.jump(0)
            if still { firstDip.time(1, duration: MoodCapsuleCheckInMotion.reducedDip, at: date, still: true) }
            else { firstDip.spring(1, constants: MoodCapsuleCheckInMotion.chipSpring, at: date, still: false) }
        } else {
            context = next(context, count: contexts.count); secondDip.jump(0)
            if still { secondDip.time(1, duration: MoodCapsuleCheckInMotion.reducedDip, at: date, still: true) }
            else { secondDip.spring(1, constants: MoodCapsuleCheckInMotion.chipSpring, at: date, still: false) }
        }
        revision &+= 1
    }
    func submit(at date: Date = .now) {
        guard let selected, moods.indices.contains(selected) else { return }
        let mood = moods[selected]
        UINotificationFeedbackGenerator().notificationOccurred(.success)
        onSubmit?(MoodCapsuleCheckInCheckIn(mood: mood, emotion: emotion.flatMap { mood.emotions.indices.contains($0) ? mood.emotions[$0] : nil }, context: context.flatMap { contexts.indices.contains($0) ? contexts[$0] : nil }, scope: scope))
        // A sequence starts its first timing afresh; its hold starts on the completion frame.
        sent = MoodCapsuleCheckInScalar(sent.value); sent.time(1, duration: MoodCapsuleCheckInMotion.sentIn, at: date, still: false, force: true)
        sentPhase = .drawing; revision &+= 1
    }
    func pickScope(_ index: Int, auto: Bool = false, at date: Date = .now) {
        touched()
        if !auto { UISelectionFeedbackGenerator().selectionChanged() }
        scope = index; thumb.spring(Double(index), constants: MoodCapsuleCheckInMotion.thumbSpring, at: date, still: still)
        outline.time(Double(index), duration: MoodCapsuleCheckInMotion.outline, at: date, still: still); revision &+= 1
    }
    func setPressed(_ value: Int, at date: Date = .now) {
        guard pressed != value else { return }; pressed = value
        for index in downs.indices { downs[index].time(value == index ? 1 : 0, duration: MoodCapsuleCheckInMotion.pressIn, at: date, still: still) }
        sendDown.time(value == MoodCapsuleCheckInStyle.send ? 1 : 0, duration: MoodCapsuleCheckInMotion.pressIn, at: date, still: still); revision &+= 1
    }
    func act(_ target: Int, auto: Bool) {
        if selected == nil { if moods.indices.contains(target) { choose(target, auto: auto) } }
        else if target == MoodCapsuleCheckInStyle.send { submit() }
        else if target == MoodCapsuleCheckInStyle.chip || target == MoodCapsuleCheckInStyle.chip + 1 { pick(target - MoodCapsuleCheckInStyle.chip, auto: auto) }
        else if target == MoodCapsuleCheckInStyle.none { close(auto: auto) }
        else if target < moods.count && target != selected { glide(target, auto: auto) }
    }
    func beginPan() { dragFrom = focus.value; touched() }
    func pan(_ translation: Double, scale: Double) {
        guard opened != 0 else { return }
        let raw = dragFrom - translation / (MoodCapsuleCheckInMotion.swipe * scale), last = Double(moods.count - 1)
        focus.jump(raw < 0 ? raw * MoodCapsuleCheckInMotion.rubber : raw > last ? last + (raw - last) * MoodCapsuleCheckInMotion.rubber : raw); revision &+= 1
    }
    func endPan(_ velocity: Double, scale: Double) {
        guard opened != 0 && !moods.isEmpty else { return }
        let thrown = floor(focus.value - velocity * MoodCapsuleCheckInMotion.throwTime / (MoodCapsuleCheckInMotion.swipe * scale) + 0.5)
        let next = Int(min(Double(min(moods.count - 1, aim + 1)), max(Double(max(0, aim - 1)), thrown)))
        glide(next, auto: false)
    }
    func settings() { touched(); onSettings?() }
    func history() { touched(); onHistory?() }
    func accessibility(_ direction: Int) {
        guard !moods.isEmpty else { return }
        let current = selected ?? aim, next = min(moods.count - 1, max(0, current + direction))
        if selected == nil { choose(next, auto: false) } else { glide(next, auto: false) }
    }
    func accessibilityActivate() { if selected == nil { choose(aim, auto: false) } else { submit() } }
    func advance(_ date: Date) {
        open.advance(date); focus.advance(date); outline.advance(date); thumb.advance(date); veil.advance(date)
        firstDip.advance(date); secondDip.advance(date); sent.advance(date); sendDown.advance(date)
        for index in downs.indices { downs[index].advance(date) }
        if forgetPending && !open.active { forget() }
        switch sentPhase {
        case .idle: break
        case .drawing: if !sent.active { sentPhase = .holding(date.addingTimeInterval(MoodCapsuleCheckInMotion.sentHold - MoodCapsuleCheckInMotion.sentIn)) }
        case let .holding(deadline): if date >= deadline { sentPhase = .tail(date.addingTimeInterval(MoodCapsuleCheckInMotion.sentTail)) }
        case let .tail(deadline): if date >= deadline { sentPhase = .idle; close(auto: true, fromSubmission: true, at: date) }
        }
        revision &+= 1
    }
    func autoplay(enabled: Bool) async {
        guard enabled && !moods.isEmpty else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(MoodCapsuleCheckInMotion.userHold * 1e9))
                while true {
                    if scope != 0 { pickScope(0, auto: true) }
                    if selected == nil { break }
                    close(auto: true)
                    try await Task.sleep(nanoseconds: UInt64(MoodCapsuleCheckInMotion.closeFor * 1e9))
                }
            }
            var origin = Date.now
            while !Task.isCancelled {
                let events: [(Double, Int)] = [(MoodCapsuleCheckInMotion.tapAt, 0), (MoodCapsuleCheckInMotion.tapAt + MoodCapsuleCheckInMotion.autoPress, 1)] + MoodCapsuleCheckInMotion.swipesAt.enumerated().map { ($0.element.0, $0.offset + 2) } + [(MoodCapsuleCheckInMotion.chipAt, 6), (MoodCapsuleCheckInMotion.chipAt + MoodCapsuleCheckInMotion.autoPress, 7), (MoodCapsuleCheckInMotion.closeAt, 8)]
                for (seconds, event) in events {
                    let wait = origin.addingTimeInterval(seconds).timeIntervalSinceNow
                    if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation()
                    switch event {
                    case 0: setPressed(min(MoodCapsuleCheckInMotion.autoMood, moods.count - 1))
                    case 1: setPressed(MoodCapsuleCheckInStyle.none); act(min(MoodCapsuleCheckInMotion.autoMood, moods.count - 1), auto: true)
                    case 2...5: glide(min(MoodCapsuleCheckInMotion.swipesAt[event - 2].1, moods.count - 1), auto: true)
                    case 6: setPressed(MoodCapsuleCheckInStyle.chip)
                    case 7: setPressed(MoodCapsuleCheckInStyle.none); act(MoodCapsuleCheckInStyle.chip, auto: true)
                    default: close(auto: true)
                    }
                }
                origin = origin.addingTimeInterval(MoodCapsuleCheckInMotion.loop)
                let wait = origin.timeIntervalSinceNow; if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }
            }
        } catch { return }
    }
}

private struct MoodCapsuleCheckInLoopKey: Equatable { let enabled: Bool; let touches: Int; let count: Int }

private enum MoodCapsuleCheckInGeometry {
    private typealias S = MoodCapsuleCheckInStyle
    private typealias M = MoodCapsuleCheckInMotion
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ time: Double, _ from: Double = 0, _ to: Double = 1) -> Double {
        let value = clamp((time - from) / (to - from)); return value * value * (3 - 2 * value)
    }
    static func rowAt(focus: Double, open: Double, count: Int) -> [Double] {
        guard count > 0 else { return [] }
        let opened = clamp(open), gap = S.restGap + (S.openGap - S.restGap) * opened
        var row: [Double] = []; row.reserveCapacity(count * 4)
        var x = 0.0
        for index in 0..<count {
            let chosen = smooth(1 - abs(Double(index) - focus))
            let width = S.restWidth + (S.smallWidth + (S.largeWidth - S.smallWidth) * chosen - S.restWidth) * open
            let height = S.restHeight + (S.smallHeight + (S.largeHeight - S.smallHeight) * chosen - S.restHeight) * open
            row.append(contentsOf: [x + width / 2, width, height, chosen]); x += width + gap
        }
        let inside = min(Double(count - 1), max(0, focus)), before = Int(floor(inside)), after = min(count - 1, before + 1)
        let focused = row[before * 4] + (row[after * 4] - row[before * 4]) * (inside - Double(before))
        let middle = (x - gap) / 2
        let anchor = middle + (focused - middle + (focus - inside) * M.swipe) * opened
        for index in 0..<count { row[index * 4] -= anchor }
        return row
    }
    static func hit(x: Double, y: Double, focus: Double, open: Double, count: Int) -> Int {
        guard count > 0 else { return S.none }
        let row = rowAt(focus: focus, open: open, count: count)
        if open == 1, focus >= 0, focus < Double(count), focus.rounded(.towardZero) == focus {
            let dx = x - row[Int(focus) * 4]
            if hypot(dx, y - S.sendY) < S.sendRadius + 6 { return S.send }
            if abs(dx) < S.chipWidth / 2 + 4 {
                if y >= S.chipY[0] - 4 && y < S.chipY[0] + S.chipHeight + 1 { return S.chip }
                if y >= S.chipY[1] - 1 && y < S.chipY[1] + S.chipHeight + 4 { return S.chip + 1 }
            }
            if abs(dx) < S.largeWidth / 2 && abs(y) < S.largeHeight / 2 { return S.inside }
        }
        for index in 0..<count {
            if abs(x - row[index * 4]) < row[index * 4 + 1] / 2 + 5 && abs(y) < row[index * 4 + 2] / 2 + 8 { return index }
        }
        return S.none
    }
    private static func quantized(_ value: Double) -> Double {
        Double(String(format: "%.3f", locale: Locale(identifier: "en_US_POSIX"), value)) ?? value
    }
    private static func wavy(lobes: Int, radius: Double, wave: Double) -> Path {
        var path = Path()
        for index in 0..<120 {
            let angle = Double(index) / 120 * .pi * 2, radius = radius + wave * cos(Double(lobes) * angle)
            let point = CGPoint(x: quantized(radius * sin(angle)), y: quantized(-radius * cos(angle)))
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath(); return path
    }
    private static func star(points: Int, inner: Double) -> Path {
        var path = Path()
        for index in 0..<(points * 2) {
            let angle = Double(index) / Double(points * 2) * .pi * 2, radius = index.isMultiple(of: 2) ? 1 : inner
            let point = CGPoint(x: quantized(radius * sin(angle)), y: quantized(-radius * cos(angle)))
            if index == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath(); return path
    }
    private static let spark = star(points: 8, inner: 0.66)
    private static let bloom = wavy(lobes: 8, radius: 0.86, wave: 0.12)
    private static let dot = Path(ellipseIn: CGRect(x: -0.8, y: -0.8, width: 1.6, height: 1.6))
    private static let petal = wavy(lobes: 5, radius: 0.8, wave: 0.2)
    private static let clover = wavy(lobes: 6, radius: 0.8, wave: 0.18)
    static func glyph(_ glyph: MoodCapsuleCheckInGlyph) -> Path {
        switch glyph { case .spark: return spark; case .bloom: return bloom; case .dot: return dot; case .petal: return petal; case .clover: return clover }
    }
    static let arrow: Path = {
        var path = Path(); path.move(to: CGPoint(x: 0, y: 12)); path.addLine(to: CGPoint(x: 0, y: -11))
        path.move(to: CGPoint(x: -9.5, y: -1.5)); path.addLine(to: CGPoint(x: 0, y: -11)); path.addLine(to: CGPoint(x: 9.5, y: -1.5)); return path
    }()
    static let check: Path = {
        var path = Path(); path.move(to: CGPoint(x: -11, y: 1)); path.addLine(to: CGPoint(x: -3.5, y: 8.5)); path.addLine(to: CGPoint(x: 11.5, y: -7.5)); return path
    }()
}

private enum MoodCapsuleCheckInDrawing {
    static func stroke(_ path: Path, color: Color, width: Double, context: GraphicsContext) {
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    static func mask(_ path: Path, shading: GraphicsContext.Shading, sigma: Double, opacity: Double, width: Double? = nil, context: GraphicsContext) {
        var paint = context; paint.opacity *= opacity
        if sigma <= 0 {
            if let width { paint.stroke(path, with: shading, lineWidth: width) } else { paint.fill(path, with: shading) }
            return
        }
        // Skia blurs coverage before the gradient; shading the blurred mask keeps its stops fixed.
        paint.drawLayer { layer in
            var coverage = layer; coverage.addFilter(.blur(radius: sigma))
            if let width { coverage.stroke(path, with: .color(.white), lineWidth: width) } else { coverage.fill(path, with: .color(.white)) }
            layer.blendMode = .sourceIn
            layer.fill(Path(path.boundingRect.insetBy(dx: -(sigma * 4 + (width ?? 0)), dy: -(sigma * 4 + (width ?? 0)))), with: shading)
        }
    }
}

private struct MoodCapsuleCheckInParagraph: UIViewRepresentable, Equatable {
    let text: String
    let size: Double
    let medium: Bool
    let color: Color
    static func == (lhs: Self, rhs: Self) -> Bool { lhs.text == rhs.text && lhs.size == rhs.size && lhs.medium == rhs.medium && lhs.color == rhs.color }
    static func font(_ size: Double, medium: Bool) -> UIFont { .systemFont(ofSize: size, weight: medium ? .medium : .regular) }
    static func height(_ size: Double, medium: Bool) -> Double { font(size, medium: medium).lineHeight }
    func makeUIView(context: Context) -> UILabel {
        let label = UILabel(); label.numberOfLines = 1; label.textAlignment = .center; label.lineBreakMode = .byTruncatingTail
        label.isAccessibilityElement = false; label.backgroundColor = .clear; return label
    }
    func updateUIView(_ label: UILabel, context: Context) {
        label.attributedText = NSAttributedString(string: text, attributes: [.font: Self.font(size, medium: medium), .foregroundColor: UIColor(color), .kern: MoodCapsuleCheckInStyle.spacing])
    }
    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? {
        CGSize(width: proposal.width ?? 0, height: Self.height(size, medium: medium))
    }
}

private struct MoodCapsuleCheckInLine: View {
    private typealias S = MoodCapsuleCheckInStyle
    let text: String
    let size: Double
    let color: Color
    let weight: Double
    var body: some View {
        MoodCapsuleCheckInParagraph(text: text, size: size, medium: false, color: color).equatable()
            .frame(width: S.textWidth, height: MoodCapsuleCheckInParagraph.height(size, medium: false))
            .padding(.vertical, 24).blur(radius: S.textBlur * (1 - weight)).opacity(weight).clipped()
            .allowsHitTesting(false).accessibilityHidden(true)
    }
}

private struct MoodCapsuleCheckInCapsule: View {
    private typealias S = MoodCapsuleCheckInStyle
    private typealias M = MoodCapsuleCheckInMotion
    private typealias F = MoodCapsuleCheckInGeometry
    private typealias C = MoodCapsuleCheckInColor
    private typealias D = MoodCapsuleCheckInDrawing
    @ObservedObject var state: MoodCapsuleCheckInState
    let index: Int
    let mood: MoodCapsuleCheckInMood
    let row: [Double]
    let designSize: CGSize
    let emotionLabel: String
    let contextLabel: String
    private var center: CGPoint { CGPoint(x: designSize.width / 2 + row[index * 4], y: designSize.height * S.rowAt) }
    private var innerScale: Double { row[index * 4 + 2] / S.largeHeight }
    private var pressScale: Double { 1 - M.press * state.downs[index].value }
    private var chosen: Double { F.clamp(state.open.value) * row[index * 4 + 3] }
    private var contentTop: Double { S.chipY[0] - 16 }
    private var contentHeight: Double { S.sendY + S.sendRadius - S.chipY[0] + 32 }
    var body: some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in drawBody(context) }
            contents
                .frame(width: S.largeWidth, height: contentHeight, alignment: .topLeading)
                .clipped().blur(radius: S.contentBlur * (1 - F.smooth(chosen, 0.5, 1))).opacity(F.smooth(chosen, 0.3, 0.9)).clipped()
                .scaleEffect(innerScale * pressScale, anchor: UnitPoint(x: 0.5, y: -contentTop / contentHeight))
                .offset(x: center.x - S.largeWidth / 2, y: center.y + contentTop)
        }.frame(width: designSize.width, height: designSize.height, alignment: .topLeading)
            .allowsHitTesting(false).accessibilityHidden(true)
    }
    private func drawBody(_ context: GraphicsContext) {
        let at = index * 4, width = row[at + 1], height = row[at + 2], share = row[at + 3]
        let lit = 1 - F.clamp(state.open.value) * (1 - share), grey = 1 - lit
        let soft = S.softBlur * 4 * share * (1 - share) * F.clamp(state.open.value)
        let shape = Path(roundedRect: CGRect(x: -width / 2, y: -height / 2, width: width, height: height), cornerRadius: width / 2)
        var body = context; body.translateBy(x: center.x, y: center.y); body.scaleBy(x: pressScale, y: pressScale)
        var halo = body; halo.opacity *= 0.62 * chosen; halo.addFilter(.blur(radius: S.bloomBlur)); halo.fill(shape, with: .color(C.color(mood.base)))
        halo = body; halo.opacity *= 0.5 * lit * (1 - chosen); halo.addFilter(.blur(radius: S.glowBlur)); halo.fill(shape, with: .color(C.color(mood.base)))
        var greyPaint = body; greyPaint.opacity *= grey
        greyPaint.fill(shape, with: .color(S.greyFill)); greyPaint.stroke(shape, with: .color(S.greyEdge), lineWidth: S.greyStroke)
        let top = CGPoint(x: 0, y: -height / 2), bottom = CGPoint(x: 0, y: height / 2)
        let fill = GraphicsContext.Shading.linearGradient(Gradient(stops: [.init(color: C.color(mood.light), location: 0), .init(color: C.color(mood.base), location: 0.5), .init(color: C.color(mood.deep), location: 1)]), startPoint: top, endPoint: bottom)
        D.mask(shape, shading: fill, sigma: soft, opacity: lit, context: body)
        let glass = GraphicsContext.Shading.linearGradient(Gradient(stops: [.init(color: .white.opacity(0.5), location: 0), .init(color: .white.opacity(0.06), location: 0.5), .init(color: .white.opacity(0.2), location: 1)]), startPoint: top, endPoint: bottom)
        D.mask(shape, shading: glass, sigma: soft, opacity: lit, width: S.glassStroke, context: body)
        var edge = body; edge.opacity *= state.outline.value * chosen; edge.stroke(shape, with: .color(C.color(mood.tint)), lineWidth: S.outlineStroke)
        let size = S.markRest + (S.markSmall + (S.markLarge - S.markSmall) * share - S.markRest) * F.clamp(state.open.value)
        var mark = body; mark.translateBy(x: 0, y: S.markY * chosen * height / S.largeHeight); mark.scaleBy(x: size / 2, y: size / 2)
        var greyMark = mark; greyMark.opacity *= grey; greyMark.fill(F.glyph(mood.glyph), with: .color(S.greyMark))
        mark.opacity *= lit; mark.fill(F.glyph(mood.glyph), with: .color(C.color(mood.tint)))
    }
    private var contents: some View {
        ZStack(alignment: .topLeading) {
            ForEach(0..<2, id: \.self) { which in
                chip(which).scaleEffect(M.chipDip + (1 - M.chipDip) * (which == 0 ? state.firstDip.value : state.secondDip.value))
                    .offset(x: (S.largeWidth - S.chipWidth) / 2, y: S.chipY[which] - contentTop)
            }
            Canvas { context, _ in
                var button = context; button.translateBy(x: S.sendRadius, y: S.sendRadius)
                button.scaleBy(x: 1 - M.press * state.sendDown.value, y: 1 - M.press * state.sendDown.value)
                button.fill(Path(ellipseIn: CGRect(x: -S.sendRadius, y: -S.sendRadius, width: S.sendRadius * 2, height: S.sendRadius * 2)), with: .color(C.color(mood.tint)))
                var arrow = button; arrow.opacity *= 1 - F.smooth(state.sent.value, 0, 0.5); D.stroke(F.arrow, color: C.color(mood.ink), width: S.arrowWidth, context: arrow)
                D.stroke(F.check.trimmedPath(from: 0, to: F.smooth(state.sent.value, 0.4, 1)), color: C.color(mood.ink), width: S.checkWidth, context: button)
            }.frame(width: S.sendRadius * 2, height: S.sendRadius * 2)
                .offset(x: S.largeWidth / 2 - S.sendRadius, y: S.sendY - S.sendRadius - contentTop)
        }
    }
    private func chip(_ which: Int) -> some View {
        let own = index == state.selected
        let picked = which == 0 ? own && state.emotion != nil : state.context != nil
        let text: String = {
            if which == 0, own, let emotion = state.emotion { return mood.emotions.indices.contains(emotion) ? mood.emotions[emotion] : "" }
            if which == 1, let context = state.context { return state.contexts.indices.contains(context) ? state.contexts[context] : "" }
            return which == 0 ? emotionLabel : contextLabel
        }()
        return ZStack {
            RoundedRectangle(cornerRadius: S.chipRadius, style: .circular).fill(picked ? C.color(mood.tint) : S.chipFill)
            MoodCapsuleCheckInParagraph(text: text, size: S.chipText, medium: true, color: picked ? C.color(mood.ink) : C.directBlend(mood.tint, "#FFFFFF", 0.4)).equatable()
                .frame(width: S.chipWidth - S.chipTextInset * 2, height: MoodCapsuleCheckInParagraph.height(S.chipText, medium: true))
        }.frame(width: S.chipWidth, height: S.chipHeight)
    }
}

private enum MoodCapsuleCheckInColor {
    static func color(_ css: String) -> Color { MoodCapsuleCheckInCSS.color(css) }
    private static func channels(_ value: Color) -> [Double] {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        UIColor(value).getRed(&red, green: &green, blue: &blue, alpha: &alpha)
        return [Double(red), Double(green), Double(blue), Double(alpha)]
    }
    private static func roundedChannel(_ value: Double) -> Double { floor(value * 255 + 0.5) }
    static func directBlend(_ from: String, _ to: String, _ amount: Double) -> Color {
        // The source helper reads the first six hex digits, independently of alpha.
        func channel(_ hex: String, _ offset: Int) -> Double? {
            let source = hex as NSString
            guard offset < source.length else { return nil }
            var part = source.substring(with: NSRange(location: offset, length: min(2, source.length - offset))).trimmingCharacters(in: .whitespacesAndNewlines)
            let sign = part.hasPrefix("-") ? -1.0 : 1.0
            if part.hasPrefix("+") || part.hasPrefix("-") { part.removeFirst() }
            if part.lowercased().hasPrefix("0x") { part = String(part.dropFirst(2)) }
            let digits = part.prefix { "0123456789abcdefABCDEF".contains($0) }
            return UInt32(digits, radix: 16).map { Double($0) * sign }
        }
        var mixed: [Double] = []
        for offset in [1, 3, 5] {
            guard let a = channel(from, offset), let b = channel(to, offset) else { return .clear }
            let value = a + (b - a) * amount, low = floor(value)
            mixed.append(value - low < 0.5 ? low : low + 1)
        }
        return Color.rgba(mixed[0], mixed[1], mixed[2], 1)
    }
    static func gammaBlend(_ from: String, _ to: String, _ amount: Double) -> Color { gammaBlend(color(from), color(to), amount) }
    static func gammaBlend(_ from: Color, _ to: Color, _ amount: Double) -> Color {
        let a = channels(from), b = channels(to), t = min(1, max(0, amount))
        func mix(_ index: Int) -> Double { roundedChannel(pow(max(0, pow(a[index], 2.2) + (pow(b[index], 2.2) - pow(a[index], 2.2)) * t), 1 / 2.2)) }
        return Color.rgba(mix(0), mix(1), mix(2), a[3] + (b[3] - a[3]) * t)
    }
    static func gammaFocus(_ moods: [MoodCapsuleCheckInMood], focus: Double, key: KeyPath<MoodCapsuleCheckInMood, String>) -> Color {
        guard !moods.isEmpty else { return .clear }
        let at = min(Double(moods.count - 1), max(0, focus)), before = Int(floor(at)), after = min(moods.count - 1, before + 1)
        return gammaBlend(moods[before][keyPath: key], moods[after][keyPath: key], at - Double(before))
    }
}

private struct MoodCapsuleCheckInControl: View {
    let label: String
    var selected = false
    let action: () -> Void
    @State private var began = false
    @State private var inside = false
    @GestureState private var active = false
    private static let retention = 20.0, bottomRetention = 30.0
    var body: some View {
        GeometryReader { proxy in
            Color.clear.contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($active) { _, active, _ in active = true }
                    .onChanged { event in
                        began = true
                        inside = event.location.x > -Self.retention && event.location.x < proxy.size.width + Self.retention && event.location.y > -Self.retention && event.location.y < proxy.size.height + Self.bottomRetention
                    }
                    .onEnded { _ in let perform = began && inside; began = false; inside = false; if perform { action() } })
        }
        .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active { began = false; inside = false } } } }
        .accessibilityElement(children: .ignore).accessibilityLabel(label).accessibilityAddTraits(selected ? [.isButton, .isSelected] : [.isButton])
        .accessibilityAction { action() }
    }
}

/// The settled hit geometry stays separate from the spring-drawn capsule row.
struct MoodCapsuleCheckIn: View {
    let moods: [MoodCapsuleCheckInMood]
    let contexts: [String]
    let question: String
    let emotionLabel: String
    let contextLabel: String
    let scopes: [String]
    let autoPlay: Bool
    let onMoodChange: ((MoodCapsuleCheckInMood) -> Void)?
    let onSubmit: ((MoodCapsuleCheckInCheckIn) -> Void)?
    let onSettings: (() -> Void)?
    let onHistory: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var segmentFont = MoodCapsuleCheckInStyle.segmentText
    @StateObject private var state: MoodCapsuleCheckInState
    @State private var pointerBegan = false
    @State private var pointerAt = Date.distantPast
    @State private var tapFailed = false
    @State private var panFailed = false
    @State private var panActivated = false
    @State private var activationTranslation = 0.0
    @State private var tapTimeout: Task<Void, Never>?
    @GestureState private var pointerActive = false
    private typealias S = MoodCapsuleCheckInStyle
    private typealias M = MoodCapsuleCheckInMotion
    private typealias F = MoodCapsuleCheckInGeometry
    private typealias C = MoodCapsuleCheckInColor

    init(moods: [MoodCapsuleCheckInMood] = MoodCapsuleCheckInMood.samples, contexts: [String] = MoodCapsuleCheckInStyle.contexts, question: String = "How are you feeling?", emotionLabel: String = "Emotions", contextLabel: String = "Context", scopes: [String] = ["Current mood", "Overall today"], autoPlay: Bool = false, onMoodChange: ((MoodCapsuleCheckInMood) -> Void)? = nil, onSubmit: ((MoodCapsuleCheckInCheckIn) -> Void)? = nil, onSettings: (() -> Void)? = nil, onHistory: (() -> Void)? = nil) {
        self.moods = moods; self.contexts = contexts; self.question = question; self.emotionLabel = emotionLabel; self.contextLabel = contextLabel; self.scopes = scopes; self.autoPlay = autoPlay
        self.onMoodChange = onMoodChange; self.onSubmit = onSubmit; self.onSettings = onSettings; self.onHistory = onHistory
        _state = StateObject(wrappedValue: MoodCapsuleCheckInState(moods: moods))
    }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let scale = min(1, width / S.designWidth), designSize = CGSize(width: width / scale, height: height / scale)
            TimelineView(.animation) { timeline in
                let _ = state.configure(moods: moods, contexts: contexts, still: reduceMotion, onMoodChange: onMoodChange, onSubmit: onSubmit, onSettings: onSettings, onHistory: onHistory)
                let row = F.rowAt(focus: state.focus.value, open: state.open.value, count: moods.count)
                ZStack(alignment: .topLeading) {
                    stage(row: row, size: designSize).scaleEffect(scale, anchor: .topLeading).opacity(state.veil.value)
                    overlay(width: width, height: height, scale: scale)
                    tile.offset(x: width - S.tileRight - S.tileWidth, y: insets.top + S.tileTop)
                    segments.offset(x: (width - S.segmentsWidth) / 2, y: height - max(insets.bottom, S.segmentSafe) - S.segmentBottom - S.segmentsHeight)
                }
                .frame(width: width, height: height, alignment: .topLeading).clipped().offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
        }
        .background(S.background.ignoresSafeArea()).preferredColorScheme(.dark)
        .task(id: MoodCapsuleCheckInLoopKey(enabled: autoPlay, touches: state.touches, count: moods.count)) { await state.autoplay(enabled: autoPlay) }
        .onDisappear { tapTimeout?.cancel(); tapTimeout = nil }
    }
    private func stage(row: [Double], size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            haze(row: row, size: size)
            ForEach(moods.indices, id: \.self) { index in
                MoodCapsuleCheckInCapsule(state: state, index: index, mood: moods[index], row: row, designSize: size, emotionLabel: emotionLabel, contextLabel: contextLabel)
            }
            line(question, size: S.questionText, color: S.mute, weight: 1 - F.smooth(state.open.value, 0, 0.5), y: S.questionY, designSize: size)
            ForEach(moods.indices, id: \.self) { index in
                line(moods[index].name, size: S.nameText, color: C.directBlend(moods[index].tint, moods[index].base, 0.3), weight: F.smooth(row[index * 4 + 3], 0.5, 1) * F.smooth(state.open.value, 0.6, 1), y: S.nameY, designSize: size)
            }
        }.frame(width: size.width, height: size.height, alignment: .topLeading).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func line(_ text: String, size: Double, color: Color, weight: Double, y: Double, designSize: CGSize) -> some View {
        MoodCapsuleCheckInLine(text: text, size: size, color: color, weight: weight)
            .offset(x: (designSize.width - S.textWidth) / 2, y: designSize.height * S.rowAt + y - MoodCapsuleCheckInParagraph.height(size, medium: false) / 2 - 24)
    }
    private func haze(row: [Double], size: CGSize) -> some View {
        Canvas { context, _ in
            let frame = Path(CGRect(origin: .zero, size: size)), middle = CGPoint(x: size.width / 2, y: size.height * S.rowAt)
            for index in moods.indices {
                let hex = moods[index].base
                var rest = context; rest.opacity *= 0.2 * (1 - F.clamp(state.open.value))
                rest.fill(frame, with: .radialGradient(Gradient(stops: [.init(color: C.color(hex + "FF"), location: 0), .init(color: C.color(hex + "55"), location: 0.45), .init(color: C.color(hex + "00"), location: 1)]), center: CGPoint(x: middle.x + row[index * 4], y: middle.y), startRadius: 0, endRadius: S.hazeRest))
                var wide = context; wide.opacity *= F.clamp(state.open.value) * row[index * 4 + 3]
                wide.fill(frame, with: .radialGradient(Gradient(stops: [.init(color: C.color(hex + "70"), location: 0), .init(color: C.color(hex + "38"), location: 0.35), .init(color: C.color(hex + "16"), location: 0.7), .init(color: C.color(hex + "0C"), location: 1)]), center: middle, startRadius: 0, endRadius: S.hazeOpen))
            }
        }.frame(width: size.width, height: size.height)
    }
    private var tile: some View {
        VStack(spacing: 0) {
            ZStack {
                Image(systemName: "gearshape.fill").font(.system(size: S.settingsSize)).accessibilityHidden(true)
                MoodCapsuleCheckInControl(label: "Settings", action: state.settings)
            }.frame(width: S.tileWidth, height: (S.tileHeight - S.tilePadding * 2) / 2)
            ZStack {
                Image(systemName: "chart.line.uptrend.xyaxis").font(.system(size: S.historySize)).accessibilityHidden(true)
                MoodCapsuleCheckInControl(label: "History", action: state.history)
            }.frame(width: S.tileWidth, height: (S.tileHeight - S.tilePadding * 2) / 2)
        }.foregroundStyle(S.mute).padding(.vertical, S.tilePadding)
            .frame(width: S.tileWidth, height: S.tileHeight).background(RoundedRectangle(cornerRadius: S.tileRadius, style: .circular).fill(S.tile))
            .opacity(1 - F.smooth(state.open.value, 0, 0.5)).scaleEffect(1 - S.tileScale * F.clamp(state.open.value))
            .allowsHitTesting(state.selected == nil)
    }
    private var segments: some View {
        let base = C.gammaFocus(moods, focus: state.focus.value, key: \.base), tint = C.gammaFocus(moods, focus: state.focus.value, key: \.tint)
        let well = C.gammaBlend(C.color(S.segmentOn), base, 0.2)
        let fill = C.gammaBlend(C.color(S.segmentOn), well, F.clamp(state.open.value))
        let ink = C.gammaBlend(C.color(S.segmentInk), tint, F.clamp(state.open.value))
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: (S.segmentsHeight - S.segmentsInset * 2) / 2, style: .circular).fill(fill)
                .frame(width: S.segment, height: S.segmentsHeight - S.segmentsInset * 2).offset(x: S.segmentsInset + 1 / displayScale + state.thumb.value * S.segment, y: S.segmentsInset + 1 / displayScale)
            HStack(spacing: 0) {
                ForEach(scopes.indices, id: \.self) { index in
                    ZStack {
                        Text(scopes[index]).font(.system(size: segmentFont, weight: state.scope == index ? .medium : .regular))
                            .foregroundStyle(state.scope == index ? ink : S.mute).lineLimit(1)
                            .frame(maxWidth: .infinity).accessibilityHidden(true)
                        MoodCapsuleCheckInControl(label: scopes[index], selected: state.scope == index) { state.pickScope(index) }
                    }.frame(maxWidth: .infinity).frame(height: S.segmentsHeight - S.segmentsInset * 2 - 2 / displayScale)
                }
            }.padding(S.segmentsInset + 1 / displayScale)
        }.frame(width: S.segmentsWidth, height: S.segmentsHeight)
            .background(RoundedRectangle(cornerRadius: S.segmentsHeight / 2, style: .circular).fill(S.segmentFill))
            .overlay(RoundedRectangle(cornerRadius: S.segmentsHeight / 2, style: .circular).strokeBorder(S.segmentBorder, lineWidth: 1 / displayScale))
    }
    private func overlay(width: Double, height: Double, scale: Double) -> some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($pointerActive) { _, active, _ in active = true }
                .onChanged { event in
                    if !pointerBegan {
                        pointerBegan = true; pointerAt = event.time; tapFailed = false; panFailed = false; panActivated = false; activationTranslation = 0
                        state.setPressed(hit(event.startLocation, width: width, height: height, scale: scale)); state.touched()
                        tapTimeout?.cancel()
                        tapTimeout = Task { @MainActor in
                            do {
                                try await Task.sleep(nanoseconds: UInt64(M.tapDuration * 1e9)); try Task.checkCancellation()
                                if pointerBegan && !panActivated { tapFailed = true; state.setPressed(S.none) }
                            } catch { return }
                        }
                    }
                    let dx = event.translation.width, dy = event.translation.height
                    if !panActivated {
                        if abs(dy) > M.panFailure { panFailed = true }
                        if !panFailed && abs(dx) > M.panOffset {
                            panActivated = true; tapFailed = true; activationTranslation = dx; tapTimeout?.cancel(); state.setPressed(S.none); state.beginPan()
                        }
                    }
                    if !tapFailed && (hypot(dx, dy) > M.tapDistance || event.time.timeIntervalSince(pointerAt) > M.tapDuration) { tapFailed = true; state.setPressed(S.none) }
                    if panActivated { state.pan(dx - activationTranslation, scale: scale) }
                }
                .onEnded { event in
                    if panActivated { state.endPan(event.velocity.width, scale: scale) }
                    else if pointerBegan && !tapFailed && hypot(event.translation.width, event.translation.height) <= M.tapDistance && event.time.timeIntervalSince(pointerAt) <= M.tapDuration { state.act(hit(event.location, width: width, height: height, scale: scale), auto: false) }
                    finishPointer()
                })
            .onChange(of: pointerActive) { _, next in if !next { DispatchQueue.main.async { if !pointerActive && pointerBegan { finishPointer() } } } }
            .accessibilityElement(children: .ignore).accessibilityLabel(question)
            .accessibilityValue(state.selected.flatMap { moods.indices.contains($0) ? moods[$0].name : nil } ?? "")
            .accessibilityAdjustableAction { direction in state.accessibility(direction == .increment ? 1 : -1) }
            .accessibilityAction { state.accessibilityActivate() }
            .accessibilityAction(.escape) { state.close(auto: false) }
    }
    private func hit(_ point: CGPoint, width: Double, height: Double, scale: Double) -> Int {
        F.hit(x: (point.x - width / 2) / scale, y: (point.y - height * S.rowAt) / scale, focus: Double(state.aim), open: state.opened, count: moods.count)
    }
    private func finishPointer() { tapTimeout?.cancel(); tapTimeout = nil; pointerBegan = false; panActivated = false; panFailed = false; tapFailed = false; state.setPressed(S.none) }
}

private enum MoodCapsuleCheckInCSS {
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

