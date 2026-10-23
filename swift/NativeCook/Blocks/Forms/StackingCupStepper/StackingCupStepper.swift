import SwiftUI
import UIKit

let StackingCupStepperLoop = 9.6

struct StackingCupStepperCupSize: Equatable {
    var label: String
    var volume: String
    var caffeine: Double
    var body: Double
    var scale: Double
    static let samples = [
        StackingCupStepperCupSize(label: "Small", volume: "237 ml", caffeine: 75, body: 62, scale: 1),
        StackingCupStepperCupSize(label: "Medium", volume: "355 ml", caffeine: 110, body: 84, scale: 1.02),
        StackingCupStepperCupSize(label: "Large", volume: "473 ml", caffeine: 150, body: 76, scale: 1.3),
        StackingCupStepperCupSize(label: "Extra Large", volume: "591 ml", caffeine: 190, body: 90, scale: 1.38)
    ]
}

struct StackingCupStepperOrder {
    let size: StackingCupStepperCupSize
    let quantity: Int
    let caffeine: Double
}

private enum StackingCupStepperStyle {
    static let hours = ["8 AM", "10 AM", "12 PM", "2 PM"]
    static let background = Color.black, sheet = Color(hex: 0x171717), hairline = Color(hex: 0x242424), chip = Color(hex: 0x242424), ink = Color.white, titleInk = Color(hex: 0xdadada), mute = Color(hex: 0x8c8c8c)
    static let pill = Color(hex: 0x222222), pillPressed = Color(hex: 0x303030), pillInk = Color(hex: 0x6c6c6c), volumeOff = Color(hex: 0x2e2e2e)
    static let round = Color(hex: 0x2b2b2b), roundPressed = Color(hex: 0x3d3d3d), confirmPressed = Color(hex: 0xbdbdbd)
    static let early = Color(hex: 0xc78a4e), now = Color(hex: 0xf2f2f2), late = Color(hex: 0x3f9c7b)
    static let subtitleInk = Color(hex: 0xb4b4b4), subtitleIcon = Color(hex: 0x4a4a4a), timeFill = Color(hex: 0x1a1a1a), timeInk = Color(hex: 0xd0d0d0), pointer = Color(hex: 0xc9c9c9), hourInk = Color(hex: 0x8a8a8a), unitInk = Color(hex: 0x9a9a9a), stepper = Color(hex: 0x1f1f1f)
    static let margin = 16.0, nav = 44.0, navInset = 4.0, navButton = 48.0, backIcon = 22.0, infoIcon = 26.0, title = 17.0, titleLine = 22.0
    static let subtitleTop = 26.0, subtitleHeight = 22.0, subtitleInset = 13.0, subtitle = 15.0, subtitleLine = 20.0, subtitleInfo = 16.0
    static let ticks = 28, tickLeft = 82.0, tickStep = 12.3, tickAt = 10, timelineTop = 18.0, timeline = 96.0, tickBottom = 30.0, tickWidth = 1.5, tickLong = 20.0, tickShort = 15.0
    static let suggestedWidth = 120.0, timeHeight = 26.0, timeInset = 10.0, suggestedText = 12.0, suggestedLine = 16.0, timeSpacing = 1.0, pointerTop = 5.0, pointerWidth = 8.0, pointerHeight = 6.0
    static let hourWidth = 60.0, hourBottom = 6.0, hour = 10.0, hourLine = 13.0, hourTicks = 8
    static let sheetTop = 20.0, sheetRadius = 22.0, header = 58.0, drink = 16.0, drinkLine = 21.0, chipHeight = 28.0, chipInset = 14.0, chipText = 13.0, chipLine = 17.0
    static let ground = 30.0, headroom = 10.0, minArea = 150.0, maxCups = 5
    static let figureLabel = 15.0, figureLabelLine = 20.0, figureTop = 1.0, figure = 30.0, figureLine = 36.0, unitInset = 6.0, unit = 13.0, unitLine = 18.0
    static let sizesTop = 26.0, pillHeight = 37.0, pillInset = 15.0, pillGap = 7.0, pillText = 15.0, pillLine = 20.0, volumeTop = 6.0, volume = 10.5, volumeLine = 13.0
    static let stepperTop = 26.0, stepperWidth = 145.0, stepperHeight = 38.0, stepperInset = 3.0, roundSize = 32.0, roundIcon = 18.0, roundHit = 8.0, count = 15.0, countLine = 20.0
    static let confirmTop = 40.0, confirm = 40.0, confirmText = 15.0, confirmLine = 20.0, bottomSafe = 16.0, bottomInset = 16.0
    static let slots: [[CGPoint]] = [
        [CGPoint(x: 0, y: 0)],
        [CGPoint(x: -40, y: 0), CGPoint(x: 40, y: 0)],
        [CGPoint(x: -46, y: -30), CGPoint(x: 46, y: -30), CGPoint(x: 0, y: 2)],
        [CGPoint(x: -50, y: -32), CGPoint(x: 50, y: -32), CGPoint(x: -38, y: 3), CGPoint(x: 38, y: 3)],
        [CGPoint(x: -38, y: -33), CGPoint(x: 38, y: -33), CGPoint(x: -74, y: 2), CGPoint(x: 74, y: 2), CGPoint(x: 0, y: 5)]
    ]
}

private enum StackingCupStepperMotion {
    struct StackingCupStepperSpring { let stiffness: Double; let damping: Double; let mass: Double }
    static let stretch = StackingCupStepperSpring(stiffness: 170, damping: 13, mass: 1)
    static let slide = StackingCupStepperSpring(stiffness: 210, damping: 17, mass: 1)
    static let thumb = StackingCupStepperSpring(stiffness: 320, damping: 28, mass: 1)
    static let hopAfter = 0.14, hopFor = 0.56, hopHeight = 10.0, hopSplit = 0.76, hopBounce = 0.16
    static let fall = 0.42, drop = 150.0, side = 46.0, tilt = 16.0, wobble = 7.0, wobbleFade = 8.0, wobbleRate = 17.0, squash = 0.1, squashFade = 10.0, squashRate = 22.0, settled = 1.6, nudge = 2.4
    static let leave = 0.24, leaveRise = 46.0, leaveSide = 26.0, leaveTilt = 14.0, count = 0.45, roll = 9.0, rollFor = 0.22, arriveFade = 0.08
    static let loop = StackingCupStepperLoop, pressFor = 0.14, userHold = 3.0, resetFor = 0.9, resetStep = 0.3
    static let colorFor = 0.2, pressCSS = 0.12, confirmCSS = 0.1, frameLimit = 0.064, energyThreshold = 6e-9
    static let steps: [(Double, String, Int)] = [(0.8, "size", 1), (2, "size", 2), (3.3, "more", 0), (4.5, "more", 0), (5.8, "less", 0), (6.8, "less", 0), (7.8, "size", 0)]
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func hop(_ t: Double) -> Double {
        if t <= 0 || t >= 1 { return 0 }
        if t < hopSplit { return 4 * t * (hopSplit - t) / (hopSplit * hopSplit) }
        let u = (t - hopSplit) / (1 - hopSplit); return hopBounce * 4 * u * (1 - u)
    }
}

private enum StackingCupStepperCurve { case linear, outCubic, inQuad }

private struct StackingCupStepperScalar {
    private(set) var value: Double
    private(set) var active = false
    private var target: Double, from: Double
    private var velocity = 0.0, energy = 0.0, duration = 0.0
    private var start = Date.distantPast, last = Date.distantPast
    private var curve = StackingCupStepperCurve.linear
    private var constants: StackingCupStepperMotion.StackingCupStepperSpring?
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func cancel() { active = false; constants = nil; velocity = 0 }
    mutating func jump(_ value: Double) { self.value = value; target = value; cancel() }
    mutating func time(_ next: Double, duration: Double, curve: StackingCupStepperCurve = .linear, at date: Date = .now, still: Bool) {
        if still || value == next { jump(next); return }
        if !(active && constants == nil && target == next) { start = date; from = value }
        target = next; self.duration = duration; self.curve = curve; constants = nil; active = true; velocity = 0
        advance(date)
    }
    mutating func spring(_ next: Double, constants: StackingCupStepperMotion.StackingCupStepperSpring, at date: Date = .now, still: Bool) {
        if still { jump(next); return }
        if self.constants != nil && target == next { advance(date); return }
        if value == next { jump(next); return }
        let continuing = self.constants != nil && active
        if !continuing { velocity = 0; last = date }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; self.constants = constants; active = true; energy = 0.5 * constants.stiffness * pow(value - next, 2); advance(date)
    }
    mutating func advance(_ date: Date) {
        guard active else { return }
        guard let constants else {
            let t = StackingCupStepperMotion.clamp(date.timeIntervalSince(start) / duration)
            let u: Double
            switch curve { case .linear: u = t; case .outCubic: u = 1 - pow(1 - t, 3); case .inQuad: u = t * t }
            value = from + (target - from) * u; if t == 1 { active = false }; return
        }
        let dt = min(StackingCupStepperMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
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
        if energy == 0 || current / energy <= StackingCupStepperMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private struct StackingCupStepperPiledCup {
    private typealias S = StackingCupStepperStyle
    private typealias M = StackingCupStepperMotion
    let index: Int
    private var present: Bool
    private var lastSlot: CGPoint
    private var leaving = false
    private(set) var x: StackingCupStepperScalar
    private(set) var y: StackingCupStepperScalar
    private(set) var age: StackingCupStepperScalar
    private(set) var away = StackingCupStepperScalar(0)
    init(index: Int, quantity: Int) {
        self.index = index; present = index < quantity
        lastSlot = present ? S.slots[quantity - 1][index] : .zero
        x = StackingCupStepperScalar(lastSlot.x); y = StackingCupStepperScalar(lastSlot.y)
        age = StackingCupStepperScalar(present ? M.settled : -1)
    }
    mutating func update(quantity: Int, still: Bool, at date: Date) {
        let nextPresent = index < quantity, slot = nextPresent ? S.slots[quantity - 1][index] : .zero
        guard nextPresent != present || slot != lastSlot else { return }
        if nextPresent && !present {
            leaving = false; away.jump(0); x.jump(slot.x); y.jump(slot.y); age.jump(0)
            age.time(M.settled, duration: M.settled, at: date, still: still)
        } else if nextPresent {
            x.spring(slot.x, constants: M.slide, at: date, still: still); y.spring(slot.y, constants: M.slide, at: date, still: still)
        } else if present {
            age.cancel(); leaving = true; away.time(1, duration: M.leave, curve: .inQuad, at: date, still: still)
            if !away.active { age.jump(-1); away.jump(0); leaving = false }
        }
        present = nextPresent; lastSlot = slot
    }
    mutating func advance(_ date: Date) {
        x.advance(date); y.advance(date); age.advance(date); away.advance(date)
        if leaving && !away.active { age.jump(-1); away.jump(0); leaving = false }
    }
    func transformed(_ context: GraphicsContext, scale: Double, landed: Double) -> GraphicsContext {
        let fallen = M.clamp(age.value / M.fall), since = age.value - M.fall
        var tilt = M.tilt * (1 - fallen), squash = 0.0
        if since > 0 && age.value < M.settled {
            tilt = -M.wobble * exp(-M.wobbleFade * since) * sin(M.wobbleRate * since)
            squash = M.squash * exp(-M.squashFade * since) * cos(M.squashRate * since)
        } else if age.value >= M.settled && landed > 0 {
            tilt = (index.isMultiple(of: 2) ? -1 : 1) * M.nudge * exp(-M.wobbleFade * landed) * sin(M.wobbleRate * landed)
        }
        let gone = away.value
        var cup = context
        cup.opacity *= age.value < 0 ? 0 : min(1, age.value / M.arriveFade) * (1 - gone * gone)
        cup.translateBy(x: x.value * scale + M.side * (1 - fallen) + M.leaveSide * gone, y: y.value * scale - M.drop * (1 - fallen * fallen) - M.leaveRise * gone * (2 - gone))
        cup.rotate(by: .degrees(tilt + M.leaveTilt * gone))
        cup.scaleBy(x: scale * (1 + squash * 0.6) * (1 - 0.3 * gone), y: scale * (1 - squash) * (1 - 0.3 * gone))
        return cup
    }
}

private struct StackingCupStepperPill: Equatable { let x: Double; let width: Double; let labelWidth: Double; let volumeHeight: Double }
private struct StackingCupStepperSizeItem: Identifiable { let index: Int; let size: StackingCupStepperCupSize; var id: String { size.label } }

@MainActor
private final class StackingCupStepperState: ObservableObject {
    private typealias S = StackingCupStepperStyle
    private typealias M = StackingCupStepperMotion
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var sizeIndex: Int
    private(set) var quantity: Int
    private(set) var figure: Double
    private(set) var autoPressed: String?
    private(set) var body: StackingCupStepperScalar
    private(set) var scale: StackingCupStepperScalar
    private(set) var hop = StackingCupStepperScalar(1)
    private(set) var landed = StackingCupStepperScalar(M.settled)
    private(set) var fit = StackingCupStepperScalar(1)
    private(set) var thumbX = StackingCupStepperScalar(0)
    private(set) var thumbWidth = StackingCupStepperScalar(0)
    private(set) var counted: StackingCupStepperScalar
    private(set) var roll = StackingCupStepperScalar(0)
    private(set) var cups: [StackingCupStepperPiledCup]
    private var hopAfter: Date?
    private var area = CGSize.zero
    private var pills: [StackingCupStepperPill] = []
    private var previousTotal: Double
    private var previousChosen: StackingCupStepperCupSize
    var sizes: [StackingCupStepperCupSize]
    var most: Int
    var still = false
    var onChange: ((StackingCupStepperOrder) -> Void)?
    var onConfirm: ((StackingCupStepperOrder) -> Void)?
    var onBack: (() -> Void)?
    var onInfo: (() -> Void)?
    init(sizes: [StackingCupStepperCupSize], size: Int, quantity: Int, max: Int) {
        let limit = min(S.maxCups, Swift.max(1, max)), initialQuantity = min(limit, Swift.max(1, quantity))
        self.sizes = sizes; most = limit; sizeIndex = min(Swift.max(0, size), Swift.max(0, sizes.count - 1))
        self.quantity = initialQuantity
        let chosen = sizes.indices.contains(sizeIndex) ? sizes[sizeIndex] : StackingCupStepperCupSize.samples[0]
        let total = chosen.caffeine * Double(self.quantity)
        body = StackingCupStepperScalar(chosen.body); scale = StackingCupStepperScalar(chosen.scale); figure = floor(total + 0.5)
        counted = StackingCupStepperScalar(total); previousTotal = total; previousChosen = chosen
        cups = (0..<limit).map { StackingCupStepperPiledCup(index: $0, quantity: initialQuantity) }
    }
    var chosen: StackingCupStepperCupSize { sizes.indices.contains(sizeIndex) ? sizes[sizeIndex] : StackingCupStepperCupSize.samples[0] }
    var order: StackingCupStepperOrder { StackingCupStepperOrder(size: chosen, quantity: quantity, caffeine: chosen.caffeine * Double(quantity)) }
    func configure(sizes: [StackingCupStepperCupSize], most: Int, still: Bool, onChange: ((StackingCupStepperOrder) -> Void)?, onConfirm: ((StackingCupStepperOrder) -> Void)?, onBack: (() -> Void)?, onInfo: (() -> Void)?) {
        self.sizes = sizes; self.most = most; self.still = still; self.onChange = onChange; self.onConfirm = onConfirm; self.onBack = onBack; self.onInfo = onInfo
        if cups.count < most { cups.append(contentsOf: (cups.count..<most).map { StackingCupStepperPiledCup(index: $0, quantity: quantity) }) }
        if cups.count > most { cups.removeLast(cups.count - most) }
    }
    func geometry(area: CGSize, pills: [StackingCupStepperPill], at date: Date = .now) {
        let changed = self.area != area || previousChosen != chosen
        self.area = area; previousChosen = chosen
        if changed { updateFit(at: date) }
        if self.pills != pills { self.pills = pills; updateThumb(at: date) }
        updateTotal(at: date)
    }
    private func updateTotal(at date: Date) {
        let total = order.caffeine
        if total != previousTotal { previousTotal = total; counted.time(total, duration: M.count, curve: .outCubic, at: date, still: still) }
    }
    private func updateThumb(at date: Date) {
        guard pills.indices.contains(sizeIndex) else { return }; let pill = pills[sizeIndex]
        let placed = thumbWidth.value > 0
        if placed { thumbX.spring(pill.x, constants: M.thumb, at: date, still: still); thumbWidth.spring(pill.width, constants: M.thumb, at: date, still: still) }
        else { thumbX.jump(pill.x); thumbWidth.jump(pill.width) }
    }
    private func updateFit(at date: Date) {
        guard area.width != 0 && S.slots.indices.contains(quantity - 1) else { return }
        let slots = S.slots[quantity - 1], reach = slots.map { abs($0.x) }.max() ?? 0, rise = slots.map { -$0.y }.max() ?? 0
        let wide = (reach * 2 + StackingCupStepperCup.width) * chosen.scale + S.margin * 2
        let tall = (StackingCupStepperCup.height(chosen.body) + rise + M.hopHeight) * chosen.scale
        fit.spring(min(1, area.width / wide, (area.height - S.ground - S.headroom) / tall), constants: M.slide, at: date, still: still)
    }
    func touched() { autoPressed = nil; touches += 1; revision &+= 1 }
    func held(_ key: String?) { autoPressed = key; revision &+= 1 }
    func changeSize(_ index: Int, auto: Bool, at date: Date = .now) {
        guard index != sizeIndex && sizes.indices.contains(index) else { return }; let old = chosen, next = sizes[index]
        if !auto { UISelectionFeedbackGenerator().selectionChanged() }
        sizeIndex = index; onChange?(order)
        body.spring(next.body, constants: M.stretch, at: date, still: still); scale.spring(next.scale, constants: M.stretch, at: date, still: still)
        if StackingCupStepperCup.height(next.body) * next.scale > StackingCupStepperCup.height(old.body) * old.scale {
            hop.jump(0)
            if still { hop.jump(1); hopAfter = nil } else { hopAfter = date.addingTimeInterval(M.hopAfter) }
        }
        previousChosen = chosen; updateTotal(at: date); updateThumb(at: date); updateFit(at: date); revision &+= 1
    }
    func step(_ by: Int, auto: Bool, at date: Date = .now) {
        let next = min(most, max(1, quantity + by)); guard next != quantity else { return }
        if !auto { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        quantity = next; onChange?(order); roll.jump(Double(by)); roll.time(0, duration: M.rollFor, curve: .outCubic, at: date, still: still)
        if by > 0 { landed.jump(-M.fall); landed.time(M.settled, duration: M.settled + M.fall, at: date, still: still) }
        for index in cups.indices { cups[index].update(quantity: quantity, still: still, at: date) }
        updateTotal(at: date); updateFit(at: date); revision &+= 1
    }
    func confirm() { touched(); UINotificationFeedbackGenerator().notificationOccurred(.success); onConfirm?(order) }
    func back() { touched(); onBack?() }
    func info() { touched(); onInfo?() }
    func advance(_ date: Date) {
        if let hopAfter, date >= hopAfter { self.hopAfter = nil; hop.time(1, duration: M.hopFor, at: date, still: still) }
        body.advance(date); scale.advance(date); hop.advance(date); landed.advance(date); fit.advance(date); thumbX.advance(date); thumbWidth.advance(date); counted.advance(date); roll.advance(date)
        for index in cups.indices { cups[index].advance(date) }
        figure = floor(counted.value + 0.5); revision &+= 1
    }
    func autoplay(enabled: Bool, startSize: Int, startCups: Int) async {
        guard enabled && !sizes.isEmpty else { return }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(M.userHold * 1e9))
                while quantity != startCups { step(quantity > startCups ? -1 : 1, auto: true); try await Task.sleep(nanoseconds: UInt64(M.resetStep * 1e9)); try Task.checkCancellation() }
                if sizeIndex != startSize { changeSize(startSize, auto: true); try await Task.sleep(nanoseconds: UInt64(M.resetFor * 1e9)); try Task.checkCancellation() }
            }
            var origin = Date.now
            while !Task.isCancelled {
                for (at, what, index) in M.steps {
                    let delay = origin.addingTimeInterval(at).timeIntervalSinceNow
                    if delay > 0 { try await Task.sleep(nanoseconds: UInt64(delay * 1e9)) }; try Task.checkCancellation()
                    held(what == "size" ? "size-\(index)" : what)
                    let release = origin.addingTimeInterval(at + M.pressFor).timeIntervalSinceNow
                    if release > 0 { try await Task.sleep(nanoseconds: UInt64(release * 1e9)) }; try Task.checkCancellation()
                    held(nil)
                    if what == "size" { changeSize(min(index, sizes.count - 1), auto: true) } else { step(what == "more" ? 1 : -1, auto: true) }
                }
                origin = origin.addingTimeInterval(M.loop)
                let delay = origin.timeIntervalSinceNow; if delay > 0 { try await Task.sleep(nanoseconds: UInt64(delay * 1e9)) }
            }
        } catch { return }
    }
}

private struct StackingCupStepperLoopKey: Equatable { let enabled: Bool; let touches: Int; let count: Int; let size: Int; let quantity: Int }

private enum StackingCupStepperText {
    static func width(_ text: String, size: Double, weight: UIFont.Weight, displayScale: Double) -> Double {
        text.components(separatedBy: .newlines).map { ceil(($0 as NSString).size(withAttributes: [.font: UIFont.systemFont(ofSize: size, weight: weight)]).width * displayScale) / displayScale }.max() ?? 0
    }
    static func height(_ text: String, size: Double, line: Double, width: Double, weight: UIFont.Weight = .regular, tabular: Bool = false) -> Double {
        let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line
        let font = tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
        let bounds = (text as NSString).boundingRect(with: CGSize(width: width, height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: [.font: font, .paragraphStyle: paragraph], context: nil)
        return Swift.max(line, ceil(bounds.height / line) * line)
    }
    static func baseline(size: Double, line: Double, weight: UIFont.Weight) -> Double {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        return font.ascender + (line - font.lineHeight) / 2
    }
    static func number(_ value: Double) -> String {
        if value.isNaN { return "NaN" }; if value == .infinity { return "Infinity" }; if value == -.infinity { return "-Infinity" }; if value == 0 { return "0" }
        let magnitude = abs(value), raw = String(magnitude).lowercased().split(separator: "e"), mantissa = String(raw[0]), exponent = raw.count > 1 ? Int(raw[1]) ?? 0 : 0
        let parts = mantissa.split(separator: ".", omittingEmptySubsequences: false)
        var digits = parts.joined(), point = parts[0].count + exponent
        while digits.first == "0" { digits.removeFirst(); point -= 1 }
        while digits.last == "0" && digits.count > 1 { digits.removeLast() }
        let sign = value < 0 ? "-" : ""
        if magnitude >= 1e-6 && magnitude < 1e21 {
            if point <= 0 { return sign + "0." + String(repeating: "0", count: -point) + digits }
            if point >= digits.count { return sign + digits + String(repeating: "0", count: point - digits.count) }
            let split = digits.index(digits.startIndex, offsetBy: point); return sign + digits[..<split] + "." + digits[split...]
        }
        let first = String(digits.prefix(1)), rest = String(digits.dropFirst()), power = point - 1
        return sign + first + (rest.isEmpty ? "" : "." + rest) + "e" + (power >= 0 ? "+" : "") + String(power)
    }
}

private struct StackingCupStepperLayout: Equatable { let area: CGSize; let pills: [StackingCupStepperPill]; let chosen: StackingCupStepperCupSize }
private struct StackingCupStepperFigureLayout {
    let figureWidth: Double
    let figureHeight: Double
    let figureBaseline: Double
    let unitWidth: Double
    let unitHeight: Double
    let unitBaseline: Double
    var height: Double { Swift.max(figureBaseline, unitBaseline) + Swift.max(figureHeight - figureBaseline, unitHeight - unitBaseline) }
}

private struct StackingCupStepperHit: View {
    let width: Double
    let height: Double
    let label: String
    var selected = false
    var disabled = false
    var hold: ((Bool) -> Void)? = nil
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var pendingOut: Task<Void, Never>?
    @GestureState private var active = false
    private static let retention = 20.0, bottomRetention = 30.0, minimumPress = 0.13
    private func contains(_ point: CGPoint) -> Bool {
        point.x > -Self.retention && point.x < width + Self.retention && point.y > -Self.retention && point.y < height + Self.bottomRetention
    }
    private func pressIn() { inside = true; activatedAt = .now; hold?(true) }
    private func pressOut() {
        inside = false
        guard let hold else { return }
        let delay = max(0, Self.minimumPress - Date.now.timeIntervalSince(activatedAt ?? .now))
        if delay == 0 { hold(false); return }
        pendingOut = Task { @MainActor in
            do { try await Task.sleep(nanoseconds: UInt64(delay * 1e9)); try Task.checkCancellation(); hold(false) } catch { return }
        }
    }
    var body: some View {
        Color.clear.frame(width: width, height: height).contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($active) { _, active, _ in active = true }
                .onChanged { event in
                    if !began { pendingOut?.cancel(); began = true; pressIn() }
                    let next = contains(event.location)
                    if next != inside { if next { pressIn() } else { pressOut() } }
                }
                .onEnded { _ in guard began else { return }; began = false; let perform = inside; if inside { pressOut() }; if perform { activate() } })
            .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { pressOut() } } } } }
            .onDisappear { pendingOut?.cancel(); began = false; inside = false; activatedAt = nil; hold?(false) }
            .allowsHitTesting(!disabled)
            .disabled(disabled)
            .accessibilityElement(children: .ignore).accessibilityLabel(label)
            .accessibilityAddTraits(selected ? [.isButton, .isSelected] : [.isButton])
            .accessibilityAction { if !disabled { activate() } }
    }
}

/// The shared cup shape is drawn once per persistent slot; each slot keeps its own fall and exit.
struct StackingCupStepper: View {
    var title: String
    var subtitle: String
    var suggestedTime: String
    var hours: [String]
    var drink: String
    var time: String
    var sizes: [StackingCupStepperCupSize]
    var size: Int
    var quantity: Int
    var max: Int
    var figureLabel: String
    var unit: String
    var confirmLabel: String
    var autoPlay: Bool
    var onChange: ((StackingCupStepperOrder) -> Void)?
    var onConfirm: ((StackingCupStepperOrder) -> Void)?
    var onBack: (() -> Void)?
    var onInfo: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @StateObject private var state: StackingCupStepperState
    @State private var manualHeld: Set<String> = []
    private typealias S = StackingCupStepperStyle
    private typealias M = StackingCupStepperMotion
    private typealias T = StackingCupStepperText
    init(title: String = "Caffeine Window", subtitle: String = "Suggested beverages", suggestedTime: String = "10:30 AM", hours: [String] = StackingCupStepperStyle.hours, drink: String = "Caffè Latte", time: String = "9:41 AM", sizes: [StackingCupStepperCupSize] = StackingCupStepperCupSize.samples, size: Int = 0, quantity: Int = 1, max: Int = 3, figureLabel: String = "Caffeine content", unit: String = "mg", confirmLabel: String = "Confirm", autoPlay: Bool = false, onChange: ((StackingCupStepperOrder) -> Void)? = nil, onConfirm: ((StackingCupStepperOrder) -> Void)? = nil, onBack: (() -> Void)? = nil, onInfo: (() -> Void)? = nil) {
        self.title = title; self.subtitle = subtitle; self.suggestedTime = suggestedTime; self.hours = hours; self.drink = drink; self.time = time; self.sizes = sizes; self.size = size; self.quantity = quantity; self.max = max; self.figureLabel = figureLabel; self.unit = unit; self.confirmLabel = confirmLabel; self.autoPlay = autoPlay
        self.onChange = onChange; self.onConfirm = onConfirm; self.onBack = onBack; self.onInfo = onInfo
        _state = StateObject(wrappedValue: StackingCupStepperState(sizes: sizes, size: size, quantity: quantity, max: max))
    }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let most = min(S.maxCups, Swift.max(1, max)), startCups = min(most, Swift.max(1, quantity))
            let sheetTop = insets.top + S.nav + S.subtitleTop + S.subtitleHeight + S.timelineTop + S.timeline + S.sheetTop
            let sheetHeight = height - sheetTop, bottom = Swift.max(insets.bottom, S.bottomSafe) + S.bottomInset
            let metrics = pillMetrics()
            let fixed = S.header + figureLabelHeight(width: width) + S.figureTop + figureLayout(width: width).height + S.sizesTop + sizesHeight(metrics) + S.stepperTop + S.stepperHeight + S.confirmTop + S.confirm + bottom
            let area = CGSize(width: width, height: Swift.max(S.minArea, sheetHeight - fixed)), layout = StackingCupStepperLayout(area: area, pills: metrics, chosen: state.chosen)
            TimelineView(.animation) { timeline in
                let _ = state.configure(sizes: sizes, most: most, still: reduceMotion, onChange: onChange, onConfirm: onConfirm, onBack: onBack, onInfo: onInfo)
                let shape = StackingCupStepperCupShape(body: state.body.value, lift: M.hopHeight * M.hop(state.hop.value))
                ZStack(alignment: .topLeading) {
                    navigation(width: width).offset(y: insets.top)
                    subtitleRow(width: width).offset(y: insets.top + S.nav + S.subtitleTop)
                    ruler(width: width).offset(y: insets.top + S.nav + S.subtitleTop + S.subtitleHeight + S.timelineTop)
                    sheet(width: width, height: sheetHeight, area: area, bottom: bottom, metrics: metrics, shape: shape).offset(y: sheetTop)
                }.frame(width: width, height: height, alignment: .topLeading).clipped().offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: timeline.date) { _, date in state.advance(date) }
            }
            .onAppear { state.geometry(area: area, pills: metrics) }
            .onChange(of: layout) { _, next in state.geometry(area: next.area, pills: next.pills) }
            .task(id: StackingCupStepperLoopKey(enabled: autoPlay, touches: state.touches, count: sizes.count, size: size, quantity: startCups)) { await state.autoplay(enabled: autoPlay, startSize: size, startCups: startCups) }
        }.background(S.background.ignoresSafeArea()).preferredColorScheme(.dark)
    }
    private func pillMetrics() -> [StackingCupStepperPill] {
        var x = 0.0
        return sizes.map { size in
            let labelWidth = T.width(size.label, size: S.pillText * fontScale, weight: .medium, displayScale: displayScale) + S.pillInset * 2
            let width = Swift.max(labelWidth, T.width(size.volume, size: S.volume * fontScale, weight: .bold, displayScale: displayScale))
            let volumeHeight = T.height(size.volume, size: S.volume * fontScale, line: S.volumeLine * fontScale, width: width)
            defer { x += width + S.pillGap }; return StackingCupStepperPill(x: x, width: width, labelWidth: labelWidth, volumeHeight: volumeHeight)
        }
    }
    private func text(_ value: String, size: Double, line: Double, weight: Font.Weight = .regular, color: Color, oneLine: Bool = false) -> some View {
        Text(value).font(.system(size: size * fontScale, weight: weight)).foregroundStyle(color).lineLimit(oneLine ? 1 : nil).frame(height: line * fontScale)
    }
    private func hold(_ key: String, _ down: Bool) { if down { manualHeld.insert(key) } else { manualHeld.remove(key) } }
    private func figureLabelHeight(width: Double) -> Double { T.height(figureLabel, size: S.figureLabel * fontScale, line: S.figureLabelLine * fontScale, width: width) }
    private func figureLayout(width: Double) -> StackingCupStepperFigureLayout {
        let number = T.number(state.figure), figureFont = UIFont.monospacedDigitSystemFont(ofSize: S.figure * fontScale, weight: .semibold)
        let figureWidth = min(width, ceil((number as NSString).size(withAttributes: [.font: figureFont]).width * displayScale) / displayScale)
        let unitWidth = min(Swift.max(0, width - S.unitInset), T.width(unit, size: S.unit * fontScale, weight: .regular, displayScale: displayScale))
        return StackingCupStepperFigureLayout(figureWidth: figureWidth,
            figureHeight: T.height(number, size: S.figure * fontScale, line: S.figureLine * fontScale, width: figureWidth, weight: .semibold, tabular: true),
            figureBaseline: T.baseline(size: S.figure * fontScale, line: S.figureLine * fontScale, weight: .semibold),
            unitWidth: unitWidth, unitHeight: T.height(unit, size: S.unit * fontScale, line: S.unitLine * fontScale, width: unitWidth),
            unitBaseline: T.baseline(size: S.unit * fontScale, line: S.unitLine * fontScale, weight: .regular))
    }
    private func sizesHeight(_ metrics: [StackingCupStepperPill]) -> Double { S.pillHeight + S.volumeTop + (metrics.map(\.volumeHeight).max() ?? 0) }
    private func pressed(_ key: String) -> Bool { manualHeld.contains(key) || state.autoPressed == key }
    private func navigation(width: Double) -> some View {
        HStack(spacing: 0) {
            ZStack {
                Image(systemName: "arrow.left").font(.system(size: S.backIcon)).foregroundStyle(S.titleInk).accessibilityHidden(true)
                StackingCupStepperHit(width: S.navButton, height: S.nav, label: "Back", activate: state.back)
            }.frame(width: S.navButton, height: S.nav)
            text(title, size: S.title, line: S.titleLine, weight: .semibold, color: S.titleInk, oneLine: true).frame(maxWidth: .infinity)
            ZStack {
                Image(systemName: "info.circle.fill").font(.system(size: S.infoIcon)).foregroundStyle(S.titleInk).accessibilityHidden(true)
                StackingCupStepperHit(width: S.navButton, height: S.nav, label: "About", activate: state.info)
            }.frame(width: S.navButton, height: S.nav)
        }.padding(.horizontal, S.navInset).frame(width: width, height: S.nav)
    }
    private func subtitleRow(width: Double) -> some View {
        HStack {
            text(subtitle, size: S.subtitle, line: S.subtitleLine, color: S.subtitleInk, oneLine: true)
            Spacer(minLength: 0)
            Image(systemName: "info.circle.fill").font(.system(size: S.subtitleInfo)).foregroundStyle(S.subtitleIcon).accessibilityHidden(true)
        }.padding(.horizontal, S.subtitleInset).frame(width: width, height: S.subtitleHeight)
    }
    private func ruler(width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(spacing: S.pointerTop) {
                text(suggestedTime, size: S.suggestedText, line: S.suggestedLine, weight: .medium, color: S.timeInk)
                    .tracking(S.timeSpacing).padding(.horizontal, S.timeInset).frame(height: S.timeHeight).background(Capsule(style: .circular).fill(S.timeFill))
                Canvas { context, _ in
                    var triangle = Path(); triangle.move(to: .zero); triangle.addLine(to: CGPoint(x: S.pointerWidth, y: 0)); triangle.addLine(to: CGPoint(x: S.pointerWidth / 2, y: S.pointerHeight)); triangle.closeSubpath(); context.fill(triangle, with: .color(S.pointer))
                }.frame(width: S.pointerWidth, height: S.pointerHeight)
            }.frame(width: S.suggestedWidth).offset(x: S.tickLeft + Double(S.tickAt) * S.tickStep - S.suggestedWidth / 2)
            ForEach(0..<S.ticks, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1, style: .circular).fill(index < S.tickAt ? S.early : index == S.tickAt ? S.now : S.late)
                    .frame(width: S.tickWidth, height: index.isMultiple(of: 4) ? S.tickLong : S.tickShort)
                    .offset(x: S.tickLeft + Double(index) * S.tickStep - S.tickWidth / 2, y: S.timeline - S.tickBottom - (index.isMultiple(of: 4) ? S.tickLong : S.tickShort))
            }
            ForEach(hours.indices, id: \.self) { index in
                text(hours[index], size: S.hour, line: S.hourLine, weight: .medium, color: S.hourInk, oneLine: true).frame(width: S.hourWidth)
                    .offset(x: S.tickLeft + Double(index * S.hourTicks) * S.tickStep - S.hourWidth / 2, y: S.timeline - S.hourBottom - S.hourLine * fontScale)
            }
        }.frame(width: width, height: S.timeline, alignment: .topLeading).clipped().accessibilityElement(children: .ignore).accessibilityLabel("\(subtitle): \(suggestedTime)")
    }
    private func sheet(width: Double, height: Double, area: CGSize, bottom: Double, metrics: [StackingCupStepperPill], shape: StackingCupStepperCupShape) -> some View {
        let figure = figureLayout(width: width)
        return VStack(spacing: 0) {
            header(width: width)
            pile(area: area, shape: shape)
            Text(figureLabel).font(.system(size: S.figureLabel * fontScale)).foregroundStyle(S.mute)
                .lineSpacing(S.figureLabelLine * fontScale - UIFont.systemFont(ofSize: S.figureLabel * fontScale).lineHeight)
                .multilineTextAlignment(.center).frame(width: width, height: figureLabelHeight(width: width))
            HStack(alignment: .firstTextBaseline, spacing: S.unitInset) {
                Text(T.number(state.figure)).font(.system(size: S.figure * fontScale, weight: .semibold)).foregroundStyle(S.ink).monospacedDigit()
                    .lineSpacing(S.figureLine * fontScale - UIFont.systemFont(ofSize: S.figure * fontScale, weight: .semibold).lineHeight)
                    .frame(width: figure.figureWidth, height: figure.figureHeight, alignment: .top).alignmentGuide(.firstTextBaseline) { _ in CGFloat(figure.figureBaseline) }
                Text(unit).font(.system(size: S.unit * fontScale)).foregroundStyle(S.unitInk)
                    .lineSpacing(S.unitLine * fontScale - UIFont.systemFont(ofSize: S.unit * fontScale).lineHeight)
                    .frame(width: figure.unitWidth, height: figure.unitHeight, alignment: .top).alignmentGuide(.firstTextBaseline) { _ in CGFloat(figure.unitBaseline) }
            }.fixedSize().frame(width: width, height: figure.height).padding(.top, S.figureTop).accessibilityElement(children: .ignore).accessibilityLabel("\(T.number(state.order.caffeine)) \(unit)")
            sizesRow(metrics: metrics).padding(.top, S.sizesTop)
            stepper.padding(.top, S.stepperTop)
            confirm(width: width).padding(.bottom, bottom)
        }.frame(width: width, height: height, alignment: .top)
            .background(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous).fill(S.sheet))
    }
    private func header(width: Double) -> some View {
        HStack(spacing: 0) {
            text(drink, size: S.drink, line: S.drinkLine, weight: .bold, color: S.ink, oneLine: true)
            Spacer(minLength: 0)
            text(time, size: S.chipText, line: S.chipLine, weight: .semibold, color: S.ink).monospacedDigit().padding(.horizontal, S.chipInset).frame(height: S.chipHeight).background(Capsule(style: .circular).fill(S.chip))
        }.padding(.horizontal, S.margin).frame(width: width, height: S.header - 1 / displayScale).frame(width: width, height: S.header, alignment: .top)
            .overlay(alignment: .bottom) { Rectangle().fill(S.hairline).frame(height: 1 / displayScale) }
    }
    private func pile(area: CGSize, shape: StackingCupStepperCupShape) -> some View {
        Canvas { context, _ in
            var pile = context; pile.translateBy(x: area.width / 2, y: area.height - S.ground); pile.scaleBy(x: state.fit.value, y: state.fit.value)
            for cup in state.cups {
                var draw = cup.transformed(pile, scale: state.scale.value, landed: state.landed.value)
                StackingCupStepperCup.draw(&draw, shape: shape)
            }
        }.frame(width: area.width, height: area.height).clipped().accessibilityElement(children: .ignore).accessibilityLabel("\(state.quantity) × \(state.chosen.label) \(drink)").accessibilityAddTraits(.isImage)
    }
    private func sizesRow(metrics: [StackingCupStepperPill]) -> some View {
        let width = (metrics.last.map { $0.x + $0.width }) ?? 0
        return ZStack(alignment: .topLeading) {
            ForEach(sizes.enumerated().map { StackingCupStepperSizeItem(index: $0.offset, size: $0.element) }) { item in
                sizeColumn(item.index, metric: metrics[item.index], rowHeight: sizesHeight(metrics)).offset(x: metrics[item.index].x)
            }
            Capsule(style: .circular).fill(S.ink).frame(width: Swift.max(0, state.thumbWidth.value), height: S.pillHeight).offset(x: state.thumbX.value).allowsHitTesting(false)
            HStack(spacing: S.pillGap) {
                ForEach(sizes.enumerated().map { StackingCupStepperSizeItem(index: $0.offset, size: $0.element) }) { item in
                    let index = item.index
                    Text(item.size.label).font(.system(size: S.pillText * fontScale, weight: .medium)).foregroundStyle(index == state.sizeIndex ? .black : S.pillInk)
                        .lineSpacing(S.pillLine * fontScale - UIFont.systemFont(ofSize: S.pillText * fontScale, weight: .medium).lineHeight).fixedSize(horizontal: true, vertical: true)
                        .frame(width: metrics[index].labelWidth, height: S.pillHeight).animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.colorFor), value: index == state.sizeIndex)
                }
            }.allowsHitTesting(false).accessibilityHidden(true)
        }.frame(width: width, height: sizesHeight(metrics), alignment: .topLeading)
    }
    private func sizeColumn(_ index: Int, metric: StackingCupStepperPill, rowHeight: Double) -> some View {
        let key = "size-\(index)", manualKey = "size-label-\(sizes[index].label)"
        let down = manualHeld.contains(manualKey) || state.autoPressed == key
        return ZStack(alignment: .topLeading) {
            VStack(spacing: S.volumeTop) {
                Capsule(style: .circular).fill(down ? S.pillPressed : S.pill).frame(width: metric.labelWidth, height: S.pillHeight)
                    .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressCSS), value: down)
                Text(sizes[index].volume).font(.system(size: S.volume * fontScale, weight: .bold)).foregroundStyle(index == state.sizeIndex ? S.ink : S.volumeOff)
                    .lineSpacing(S.volumeLine * fontScale - UIFont.systemFont(ofSize: S.volume * fontScale, weight: .bold).lineHeight)
                    .multilineTextAlignment(.center).frame(width: metric.width, height: metric.volumeHeight)
                    .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.colorFor), value: index == state.sizeIndex)
            }.frame(width: metric.width)
            StackingCupStepperHit(width: metric.width, height: rowHeight, label: "\(sizes[index].label), \(sizes[index].volume)", selected: state.sizeIndex == index, hold: { hold(manualKey, $0) }) { state.touched(); state.changeSize(index, auto: false) }
        }.frame(width: metric.width, height: rowHeight, alignment: .topLeading)
    }
    private var stepper: some View {
        HStack(spacing: 0) {
            round(by: -1)
            Spacer(minLength: 0)
            text(String(state.quantity), size: S.count, line: S.countLine, weight: .medium, color: S.ink).monospacedDigit().opacity(1 - abs(state.roll.value)).offset(y: state.roll.value * M.roll).accessibilityHidden(true)
            Spacer(minLength: 0)
            round(by: 1)
        }.padding(.horizontal, S.stepperInset).frame(width: S.stepperWidth, height: S.stepperHeight).background(Capsule(style: .circular).fill(S.stepper))
            .accessibilityElement(children: .contain).accessibilityValue(String(state.quantity))
    }
    private func round(by: Int) -> some View {
        let key = by > 0 ? "more" : "less", down = pressed(key), disabled = by > 0 ? state.quantity >= state.most : state.quantity <= 1
        return ZStack {
            Circle().fill(down ? S.roundPressed : S.round)
                .overlay { Image(systemName: by > 0 ? "plus" : "minus").font(.system(size: S.roundIcon)).foregroundStyle(S.ink) }
                .frame(width: S.roundSize, height: S.roundSize).scaleEffect(down ? 0.9 : 1).opacity(disabled ? 0.4 : 1)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressCSS), value: down)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressCSS), value: disabled).accessibilityHidden(true)
            StackingCupStepperHit(width: S.roundSize + S.roundHit * 2, height: S.roundSize + S.roundHit * 2, label: by > 0 ? "One more" : "One less", disabled: disabled, hold: { hold(key, $0) }) { state.touched(); state.step(by, auto: false) }
        }.frame(width: S.roundSize, height: S.roundSize)
    }
    private func confirm(width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Capsule(style: .circular).fill(manualHeld.contains("confirm") ? S.confirmPressed : S.ink)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.confirmCSS), value: manualHeld.contains("confirm"))
                .frame(width: width - S.margin * 2, height: S.confirm).offset(x: S.margin, y: S.confirmTop)
            text(confirmLabel, size: S.confirmText, line: S.confirmLine, weight: .semibold, color: .black).frame(width: width - S.margin * 2, height: S.confirm).offset(x: S.margin, y: S.confirmTop).accessibilityHidden(true)
            StackingCupStepperHit(width: width, height: S.confirmTop + S.confirm, label: confirmLabel, hold: { hold("confirm", $0) }, activate: state.confirm)
        }.frame(width: width, height: S.confirmTop + S.confirm)
    }
}
