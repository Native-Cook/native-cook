import SwiftUI
import UIKit

private enum StarfieldPaymentSuccessStyle {
    static let black = Color.black, tint = Color(hex: 0x1a0c0c), ember = [255.0, 122.0, 26.0], emberY = 1.09, emberWidth = 1.25, emberHeight = 0.3
    static let tile = Color(hex: 0x1c1c1e), label = Color(hex: 0x77777c), placeholder = Color(hex: 0x6b6b70), unit = Color(hex: 0x8a8a8e), card = [Color(hex: 0x2b3139), Color(hex: 0x474a50)], cardName = Color(hex: 0xa4a7ad), share = Color(hex: 0x2a2a2e), pressed = 0.72
    static let close = 38.0, closeInset = 36.0, topGap = 15.0, amount = 54.0, amountRow = 66.0, caret = 60.0, chip = 32.0, cardHeight = 72.0, cardMargin = 24.0, key = 64.0, keyFlash = 72.0, padMargin = 11.0, padGap = 59.0, padGapClosed = 25.0, rowMin = 50.0, button = 56.0, confirmMargin = 32.0, barMargin = 28.0, barGap = 10.0, buttonLift = 2.0, padEnd = 8.0, bottomMin = 12.0
    static let headTop = 26.0, title = 16.0, titleLine = 20.0, amountTop = 15.0, amountTracking = -1.2, unitGap = 1.0, emptyUnitGap = 12.0, selectGap = 8.0, selectSize = 22.0, selectStroke = 1.8, caretWidth = 2.5, caretRadius = 1.25, caretMargin = 2.0
    static let chipTop = 9.0, chipPadding = 13.0, chipGap = 7.0, chipIcon = 13.0, chipText = 13.0, chipLine = 16.0, dividerTop = 29.0, dividerAlpha = 0.14, payingTop = 32.0, cardTop = 17.0, cardRadius = 18.0, cardBorder = 1.0, cardBorderAlpha = 0.22, cardLeft = 13.0, cardRight = 16.0, cardTextGap = 16.0, cardChevron = 20.0
    static let artWidth = 50.0, artHeight = 42.0, artRadius = 11.0, artCanvasWidth = 34.0, artCanvasHeight = 26.0, planet = Color(hex: 0xff8a1e), ring = Color(hex: 0x2a2c30), ringStroke = 1.8, planetStroke = 3.4
    static let cardNumber = 17.0, cardNumberLine = 21.0, cardNameText = 10.5, cardNameLine = 13.0, cardNameTop = 3.0, cardNameTracking = 0.4, digit = 28.0, digitLine = 34.0, deleteSize = 24.0, deleteStroke = 1.8, wideText = 15.0, wideLine = 20.0, wideGap = 8.0
    static let waitingAt = 0.514, waitingLift = 15.0, waitingText = 24.0, waitingLine = 30.0, waitingTracking = -0.4, waitingHintTop = 19.0, waitingHint = 15.0, waitingHintLine = 20.0
    static let resultAt = 0.517, resultLift = 14.0, done = 22.0, doneLine = 28.0, doneTracking = -0.3, sumHeight = 60.0, sumTop = 22.0, sumBottom = 6.0, sumSign = 40.0, sumSignGap = 4.0, sumWhole = 50.0, sumTracking = -1.0, sumCents = 28.0, arrowSize = 17.0, shareSize = 19.0, shareStroke = 1.8, shareBorderAlpha = 0.12
    static let closeRadius = 11.0, closeIcon = 18.0, closeStroke = 1.8, closeHit = 8.0, falloffAt = [0.0, 0.2, 0.4, 0.6, 0.8, 1.0], falloff = [1.0, 0.905, 0.655, 0.345, 0.095, 0.0], emberAlpha = 0.9
    static let keys = [["1", "2", "3"], ["4", "5", "6"], ["7", "8", "9"], ["", "0", "back"]]
}

private enum StarfieldPaymentSuccessMotion {
    static let confirmAfter = 0.4, confirmDamping = 17.0, confirmStiffness = 190.0, confirmMass = 1.0, confirmOut = 0.22, flashOn = 0.04, flashOff = 0.35, float = 140.0, sink = 100.0, backTravel = 28.0, rowStagger = 0.06, textRise = 26.0, resultRise = 14.0, resultStagger = 0.08, clockEnd = 3600.0
    static let digitIn = 0.14, caretPeriod = 1.0, caretFadeAt = 0.5, caretGoneAt = 0.56, caretBackAt = 0.94, pressScale = 0.97, buttonCSS = 0.11, cardCSS = 0.12, shareAfter = 0.07, barDrop = 0.5, extraRise = 16.0, idlePoll = 0.1, frameLimit = 0.064, energyThreshold = 6e-9
    static let css = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))
}

private enum StarfieldPaymentSuccessNumbers {
    private static let numeric = try! NSRegularExpression(pattern: #"^[+-]?(?:(?:[0-9]+(?:\.[0-9]*)?|\.[0-9]+)(?:[eE][+-]?[0-9]+)?|Infinity)$"#)
    private static let grouping = try! NSRegularExpression(pattern: #"(?<=[A-Za-z0-9_])(?=(?:[0-9]{3})+(?![0-9]))"#)
    private static let whitespace: CharacterSet = {
        var set = CharacterSet(charactersIn: "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D}\u{0020}\u{00A0}\u{1680}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}\u{FEFF}"); set.insert(charactersIn: "\u{2000}"..."\u{200A}"); return set
    }()
    static func rounded(_ value: Double) -> Double { guard value.isFinite else { return value }; let low = floor(value); return value - low < 0.5 ? low : low + 1 }
    static func number(_ text: String) -> Double {
        let clean = text.trimmingCharacters(in: whitespace); if clean.isEmpty { return 0 }; let bytes = Array(clean.utf8)
        if bytes.count > 2 && bytes[0] == 48 {
            let radix = bytes[1] == 120 || bytes[1] == 88 ? 16 : bytes[1] == 98 || bytes[1] == 66 ? 2 : bytes[1] == 111 || bytes[1] == 79 ? 8 : 0
            if radix > 0 {
                let width = radix == 16 ? 4 : radix == 8 ? 3 : 1; var bits: [UInt8] = []
                for byte in bytes.dropFirst(2) {
                    let digit = (48...57).contains(byte) ? Int(byte - 48) : (65...70).contains(byte) ? Int(byte - 65) + 10 : (97...102).contains(byte) ? Int(byte - 97) + 10 : radix
                    guard digit < radix else { return .nan }; for shift in (0..<width).reversed() { bits.append(UInt8((digit >> shift) & 1)) }
                }
                guard let first = bits.firstIndex(of: 1) else { return 0 }; let significant = Array(bits[first...])
                if significant.count <= 53 { return Double(significant.reduce(UInt64(0)) { ($0 << 1) | UInt64($1) }) }
                var exponent = significant.count - 1; if exponent > 1023 { return .infinity }
                var fraction = significant.prefix(53).reduce(UInt64(0)) { ($0 << 1) | UInt64($1) }
                if significant[53] == 1 && (significant.dropFirst(54).contains(1) || fraction & 1 == 1) { fraction += 1 }
                if fraction == UInt64(1) << 53 { fraction >>= 1; exponent += 1 }; if exponent > 1023 { return .infinity }
                return Double(bitPattern: UInt64(exponent + 1023) << 52 | (fraction & 0x000f_ffff_ffff_ffff))
            }
        }
        guard numeric.firstMatch(in: clean, range: NSRange(location: 0, length: (clean as NSString).length)) != nil else { return .nan }; return Double(clean) ?? .nan
    }
    static func string(_ value: Double) -> String {
        if value.isNaN { return "NaN" }; if value == .infinity { return "Infinity" }; if value == -.infinity { return "-Infinity" }; if value == 0 { return "0" }
        let magnitude = abs(value), raw = String(magnitude).lowercased().split(separator: "e"), parts = String(raw[0]).split(separator: ".", omittingEmptySubsequences: false)
        var digits = parts.joined(), point = parts[0].count + (raw.count > 1 ? Int(raw[1]) ?? 0 : 0)
        while digits.first == "0" && digits.count > 1 { digits.removeFirst(); point -= 1 }; while digits.last == "0" && digits.count > 1 { digits.removeLast() }
        let sign = value < 0 ? "-" : ""
        if magnitude >= 1e-6 && magnitude < 1e21 {
            if point <= 0 { return sign + "0." + String(repeating: "0", count: -point) + digits }; if point >= digits.count { return sign + digits + String(repeating: "0", count: point - digits.count) }
            let at = digits.index(digits.startIndex, offsetBy: point); return sign + String(digits[..<at]) + "." + String(digits[at...])
        }
        let fraction = String(digits.dropFirst()), power = point - 1
        return sign + String(digits.prefix(1)) + (fraction.isEmpty ? "" : "." + fraction) + "e" + (power >= 0 ? "+" : "") + String(power)
    }
    static func money(_ value: Double) -> (String, String) {
        let cents = rounded(value * 100), whole = string(floor(cents / 100)), remainder = string(cents.truncatingRemainder(dividingBy: 100))
        return (grouping.stringByReplacingMatches(in: whole, range: NSRange(location: 0, length: (whole as NSString).length), withTemplate: ","), String(repeating: "0", count: max(0, 2 - remainder.count)) + remainder)
    }
    static func units(_ text: String) -> [[UInt16]] { text.utf16.map { [$0] } }
}

private struct StarfieldPaymentSuccessConfirm {
    private(set) var value = 0.0
    private var target = 0.0, from = 0.0, velocity = 0.0, energy = 0.0
    private var start = Date.distantPast, last = Date.distantPast, mode = 0
    mutating func set(_ next: Double) { value = next; target = next; velocity = 0; mode = 0 }
    mutating func move(_ next: Double, at date: Date, still: Bool) {
        if still || value == next { set(next); return }
        if next == 0 { if mode != 1 || target != next { from = value; start = date }; target = next; mode = 1; advance(date); return }
        if mode == 2 && target == next { advance(date); return }; if mode != 2 { velocity = 0; last = date }
        if velocity * (next - value) < 0 { velocity = 0 }; target = next; energy = 0.5 * StarfieldPaymentSuccessMotion.confirmStiffness * pow(value - next, 2); mode = 2; advance(date)
    }
    mutating func advance(_ date: Date) {
        let m = StarfieldPaymentSuccessMotion.self
        if mode == 1 { let t = StarfieldPaymentSuccessMath.clamp01(date.timeIntervalSince(start) / m.confirmOut), eased = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2; value = from + (target - from) * eased; if t == 1 { mode = 0 }; return }
        guard mode == 2 else { return }; let dt = min(m.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(m.confirmStiffness / m.confirmMass), decay = m.confirmDamping / (2 * m.confirmMass), x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay), b = (velocity + decay * x) / damped, wave = x * cos(damped * dt) + b * sin(damped * dt), attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt))); value = target + attenuation * wave
        } else { let b = velocity + omega * x, attenuation = exp(-omega * dt); value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt) }
        let current = 0.5 * m.confirmStiffness * pow(value - target, 2) + 0.5 * m.confirmMass * velocity * velocity
        if energy == 0 || current / energy <= m.energyThreshold { value = target; velocity = 0; mode = 0 }
    }
}

private enum StarfieldPaymentSuccessPhase { case form, waiting, done, leaving }
private enum StarfieldPaymentSuccessEventKind { case hold([UInt16]), key([UInt16]), press, confirm, leave, cycle(Date) }
private struct StarfieldPaymentSuccessEvent { let at: Date; let order: Int; let kind: StarfieldPaymentSuccessEventKind }
private struct StarfieldPaymentSuccessLoopKey: Equatable { let enabled: Bool; let digits: String; let check: Double; let touches: Int }

@MainActor
private final class StarfieldPaymentSuccessState: ObservableObject {
    private typealias M = StarfieldPaymentSuccessMotion
    private typealias P = StarfieldPaymentSuccessSpaceMotion
    @Published var revision = 0
    @Published private(set) var touches = 0
    private(set) var digitUnits: [UInt16] = []
    var digits: String { String(decoding: digitUnits, as: UTF16.self) }
    private(set) var confirmUp = false, phase = StarfieldPaymentSuccessPhase.form, sent = 0.0
    private(set) var autoKey: String?, autoPressed = false, elapsed = -1.0, leaving = 0.0, confirm = StarfieldPaymentSuccessConfirm()
    private(set) var digitStarts: [Date?] = [], caretAt = Date.now
    var maxDigits = 6.0, duration = 2.7, still = false
    var onConfirm: ((Double) -> Void)?, onSuccess: ((Double) -> Void)?, onReview: ((Double) -> Void)?, onShare: ((Double) -> Void)?, onCurrencyPress: (() -> Void)?, onCardPress: (() -> Void)?, onClose: (() -> Void)?
    private var origin: Date?, leaveOrigin: Date?, arrivedAt = Date.distantPast, confirmTask: Task<Void, Never>?, automatic = false, past: Bool?, caretKey = "empty-false"
    var busy: Bool { phase != .form }
    var amount: Double { let number = StarfieldPaymentSuccessNumbers.number(digits); return number.isNaN || number == 0 ? 0 : number }
    var check: Double { duration + P.checkAfter }
    func touched() { autoKey = nil; autoPressed = false; touches += 1; revision &+= 1 }
    private func caret() { let next = digitUnits.isEmpty ? "empty-\(busy)" : digitUnits.map(String.init).joined(separator: ","); if next != caretKey { caretKey = next; caretAt = .now } }
    private func setDigits(_ next: [UInt16]) {
        let count = next.count
        if count < digitStarts.count { digitStarts.removeLast(digitStarts.count - count) }
        else if count > digitStarts.count { digitStarts.append(contentsOf: Array(repeating: still ? nil : Date.now, count: count - digitStarts.count)) }
        digitUnits = next; caret(); revision &+= 1
    }
    private func showConfirm(_ up: Bool) { confirmUp = up; confirm.move(up ? 1 : 0, at: .now, still: still); revision &+= 1 }
    func type(_ key: String) { type(Array(key.utf16), back: key == "back") }
    private func type(_ key: [UInt16], back: Bool = false) {
        let next = back ? Array(digitUnits.dropLast()) : digitUnits.isEmpty && key == [48] ? [] : Double(digitUnits.count) < maxDigits ? digitUnits + key : digitUnits
        setDigits(next); confirmTask?.cancel()
        if next.isEmpty { showConfirm(false) }
        else if !confirmUp { confirmTask = Task { @MainActor [weak self] in do { try await Task.sleep(nanoseconds: UInt64(M.confirmAfter * 1e9)); try Task.checkCancellation(); self?.showConfirm(true) } catch { return } } }
    }
    func key(_ key: String) { touched(); UIImpactFeedbackGenerator(style: .light).impactOccurred(); type(key) }
    func clear() { confirmTask?.cancel(); setDigits([]); showConfirm(false) }
    func start(auto: Bool, at date: Date = .now) {
        guard !busy && amount > 0 else { return }; automatic = auto
        if !auto { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }; sent = amount; phase = .waiting; onConfirm?(sent); leaving = 0; leaveOrigin = nil; elapsed = 0; origin = date; caret(); react(); revision &+= 1
    }
    func leave(at date: Date = .now) { guard phase == .done else { return }; phase = .leaving; clear(); leaving = 0; leaveOrigin = date; caret(); revision &+= 1 }
    private func react() {
        let now = elapsed >= check
        if now && past == false { phase = .done; arrivedAt = .now; if !automatic { UINotificationFeedbackGenerator().notificationOccurred(.success) }; onSuccess?(sent); caret() }; past = now
    }
    func advance(_ date: Date) {
        confirm.advance(date); if let origin { elapsed = min(M.clockEnd, max(0, date.timeIntervalSince(origin))) }
        if let leaveOrigin { leaving = min(1, max(0, date.timeIntervalSince(leaveOrigin) / P.returnTime)); if leaving == 1 { elapsed = -1; leaving = 0; origin = nil; self.leaveOrigin = nil; phase = .form; caret() } }; react(); revision &+= 1
    }
    func currency() { touched(); onCurrencyPress?() }
    func card() { touched(); UIImpactFeedbackGenerator(style: .light).impactOccurred(); onCardPress?() }
    func review() { touched(); UIImpactFeedbackGenerator(style: .light).impactOccurred(); onReview?(sent); leave() }
    func share() { touched(); UIImpactFeedbackGenerator(style: .light).impactOccurred(); onShare?(sent); leave() }
    func close() { touched(); UIImpactFeedbackGenerator(style: .light).impactOccurred(); onClose?(); if busy { leave() } else { clear() } }
    func stop() { confirmTask?.cancel() }
    func autoplay(enabled: Bool, amount: String) async {
        guard enabled else { return }; var queue: [StarfieldPaymentSuccessEvent] = [], order = 0
        func add(_ at: Date, _ kind: StarfieldPaymentSuccessEventKind) { order += 1; queue.append(StarfieldPaymentSuccessEvent(at: at, order: order, kind: kind)) }
        func cycle(_ origin: Date) {
            for (index, key) in StarfieldPaymentSuccessNumbers.units(amount).enumerated() {
                let at = P.firstKeyAt + Double(index) * P.keyEvery; add(origin.addingTimeInterval(at), .hold(key)); add(origin.addingTimeInterval(at + P.keyFor), .key(key))
            }
            add(origin.addingTimeInterval(P.pressAt), .press); add(origin.addingTimeInterval(P.pressAt + P.pressFor), .confirm)
            add(origin.addingTimeInterval(P.pressAt + P.pressFor + check + P.leaveAfter), .leave); add(origin.addingTimeInterval(P.loop), .cycle(origin.addingTimeInterval(P.loop)))
        }
        do {
            if touches > 0 {
                try await Task.sleep(nanoseconds: UInt64(P.userHold * 1e9)); try Task.checkCancellation()
                while busy { if Date.now.timeIntervalSince(arrivedAt) >= P.leaveAfter { leave() }; try await Task.sleep(nanoseconds: UInt64(M.idlePoll * 1e9)); try Task.checkCancellation() }; clear()
            }
            cycle(.now)
            while !Task.isCancelled {
                queue.sort { $0.at == $1.at ? $0.order < $1.order : $0.at < $1.at }; let event = queue.removeFirst(), wait = event.at.timeIntervalSinceNow
                if wait > 0 { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)) }; try Task.checkCancellation()
                switch event.kind {
                case let .hold(key): autoKey = String(decoding: key, as: UTF16.self)
                case let .key(key): autoKey = nil; type(key)
                case .press: autoPressed = true
                case .confirm: autoPressed = false; start(auto: true)
                case .leave: leave()
                case let .cycle(origin): cycle(origin)
                }; revision &+= 1
            }
        } catch { return }
    }
}

private enum StarfieldPaymentSuccessIconPaths {
    static let paths: [String: Path] = [
        "close": Path { p in
            p.move(to: CGPoint(x: 18, y: 6)); p.addLine(to: CGPoint(x: 6, y: 18))
            p.move(to: CGPoint(x: 6, y: 6)); p.addLine(to: CGPoint(x: 18, y: 18))
        },
        "select": Path { p in
            p.move(to: CGPoint(x: 7, y: 15)); p.addLine(to: CGPoint(x: 12, y: 20)); p.addLine(to: CGPoint(x: 17, y: 15))
            p.move(to: CGPoint(x: 7, y: 9)); p.addLine(to: CGPoint(x: 12, y: 4)); p.addLine(to: CGPoint(x: 17, y: 9))
        },
        "swap": Path { p in
            p.move(to: CGPoint(x: 17, y: 1)); p.addLine(to: CGPoint(x: 21, y: 5)); p.addLine(to: CGPoint(x: 17, y: 9))
            p.move(to: CGPoint(x: 3, y: 11)); p.addLine(to: CGPoint(x: 3, y: 9))
            p.addArc(center: CGPoint(x: 7, y: 9), radius: 4, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
            p.addLine(to: CGPoint(x: 21, y: 5))
            p.move(to: CGPoint(x: 7, y: 23)); p.addLine(to: CGPoint(x: 3, y: 19)); p.addLine(to: CGPoint(x: 7, y: 15))
            p.move(to: CGPoint(x: 21, y: 13)); p.addLine(to: CGPoint(x: 21, y: 15))
            p.addArc(center: CGPoint(x: 17, y: 15), radius: 4, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
            p.addLine(to: CGPoint(x: 3, y: 19))
        },
        "chevron": Path { p in
            p.move(to: CGPoint(x: 9, y: 18)); p.addLine(to: CGPoint(x: 15, y: 12)); p.addLine(to: CGPoint(x: 9, y: 6))
        },
        "delete": Path { p in
            p.move(to: CGPoint(x: 21, y: 4)); p.addLine(to: CGPoint(x: 8, y: 4))
            p.addLine(to: CGPoint(x: 1, y: 12)); p.addLine(to: CGPoint(x: 8, y: 20)); p.addLine(to: CGPoint(x: 21, y: 20))
            p.addArc(center: CGPoint(x: 21, y: 18), radius: 2, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
            p.addLine(to: CGPoint(x: 23, y: 6))
            p.addArc(center: CGPoint(x: 21, y: 6), radius: 2, startAngle: .degrees(0), endAngle: .degrees(-90), clockwise: true)
            p.closeSubpath()
            p.move(to: CGPoint(x: 18, y: 9)); p.addLine(to: CGPoint(x: 12, y: 15))
            p.move(to: CGPoint(x: 12, y: 9)); p.addLine(to: CGPoint(x: 18, y: 15))
        },
        "arrow": Path { p in
            p.move(to: CGPoint(x: 5, y: 12)); p.addLine(to: CGPoint(x: 19, y: 12))
            p.move(to: CGPoint(x: 12, y: 5)); p.addLine(to: CGPoint(x: 19, y: 12)); p.addLine(to: CGPoint(x: 12, y: 19))
        },
        "share": Path { p in
            p.move(to: CGPoint(x: 4, y: 12)); p.addLine(to: CGPoint(x: 4, y: 20))
            p.addArc(center: CGPoint(x: 6, y: 20), radius: 2, startAngle: .degrees(180), endAngle: .degrees(90), clockwise: true)
            p.addLine(to: CGPoint(x: 18, y: 22))
            p.addArc(center: CGPoint(x: 18, y: 20), radius: 2, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
            p.addLine(to: CGPoint(x: 20, y: 12))
            p.move(to: CGPoint(x: 16, y: 6)); p.addLine(to: CGPoint(x: 12, y: 2)); p.addLine(to: CGPoint(x: 8, y: 6))
            p.move(to: CGPoint(x: 12, y: 2)); p.addLine(to: CGPoint(x: 12, y: 15))
        },
    ]
    // Keep the SVG's rounded endpoints instead of assuming they lie on the planet's circle.
    static let planetArc: Path = {
        let start = CGPoint(x: 10.2, y: 9.2), end = CGPoint(x: 24.9, y: 11.6), radius = 8.0
        let dx = (start.x - end.x) / 2, dy = (start.y - end.y) / 2
        let factor = sqrt((radius * radius - dx * dx - dy * dy) / (dx * dx + dy * dy))
        let center = CGPoint(x: (start.x + end.x) / 2 + factor * dy, y: (start.y + end.y) / 2 - factor * dx)
        var p = Path(); p.move(to: start)
        p.addArc(center: center, radius: radius,
                 startAngle: .radians(atan2(start.y - center.y, start.x - center.x)),
                 endAngle: .radians(atan2(end.y - center.y, end.x - center.x)), clockwise: false)
        return p
    }()
}

private struct StarfieldPaymentSuccessIcon: View {
    let name: String
    let size: Double
    let color: Color
    var strokeWidth = 2.0

    var body: some View {
        Canvas { context, _ in
            var context = context
            context.scaleBy(x: size / 24, y: size / 24)
            if let path = StarfieldPaymentSuccessIconPaths.paths[name] {
                context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: strokeWidth, lineCap: .round, lineJoin: .round))
            }
        }.frame(width: size, height: size).allowsHitTesting(false).accessibilityHidden(true)
    }
}

private struct StarfieldPaymentSuccessCardArt: View {
    private typealias S = StarfieldPaymentSuccessStyle
    var body: some View {
        RoundedRectangle(cornerRadius: S.artRadius, style: .continuous).fill(.white)
            .frame(width: S.artWidth, height: S.artHeight)
            .overlay {
                Canvas { context, _ in
                    context.fill(Path(ellipseIn: CGRect(x: 9, y: 5, width: 16, height: 16)), with: .color(S.planet))
                    var ring = context
                    ring.translateBy(x: 17, y: 13); ring.rotate(by: .degrees(-20))
                    ring.stroke(Path(ellipseIn: CGRect(x: -14.5, y: -4.2, width: 29, height: 8.4)), with: .color(S.ring), lineWidth: S.ringStroke)
                    context.stroke(StarfieldPaymentSuccessIconPaths.planetArc, with: .color(S.planet), lineWidth: S.planetStroke)
                }.frame(width: S.artCanvasWidth, height: S.artCanvasHeight)
            }.allowsHitTesting(false).accessibilityHidden(true)
    }
}


private enum StarfieldPaymentSuccessCSS {
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



private struct StarfieldPaymentSuccessHitShape: Shape {
    let original: CGRect
    let flash: CGRect?
    func path(in rect: CGRect) -> Path { var path = Path(); path.addRect(original); if let flash { path.addRect(flash) }; return path }
}

private struct StarfieldPaymentSuccessHit: View {
    let width: Double
    let height: Double
    let label: String
    var disabled = false
    var slop = 0.0
    var flash: Double? = nil
    var hold: ((Bool) -> Void)? = nil
    let activate: () -> Void
    @State private var began = false
    @State private var inside = false
    @State private var activatedAt: Date?
    @State private var region: CGRect?
    @State private var pendingOut: Task<Void, Never>?
    @GestureState private var active = false
    private static let retention = 20.0, bottomRetention = 30.0, minimumPress = 0.13
    private var extraX: Double { max(slop, ((flash ?? width) - width) / 2) }
    private var extraY: Double { max(slop, ((flash ?? height) - height) / 2) }
    private func contains(_ point: CGPoint) -> Bool {
        guard let region else { return false }
        return point.x > region.minX - Self.retention - slop && point.x < region.maxX + Self.retention + slop && point.y > region.minY - Self.retention - slop && point.y < region.maxY + Self.bottomRetention + slop
    }
    private func pressIn() { inside = true; activatedAt = .now; hold?(true) }
    private func pressOut() {
        inside = false; guard let hold else { return }; let wait = max(0, Self.minimumPress - Date.now.timeIntervalSince(activatedAt ?? .now))
        if wait == 0 { hold(false); return }
        pendingOut = Task { @MainActor in do { try await Task.sleep(nanoseconds: UInt64(wait * 1e9)); try Task.checkCancellation(); hold(false) } catch { return } }
    }
    var body: some View {
        let original = CGRect(x: extraX - slop, y: extraY - slop, width: width + slop * 2, height: height + slop * 2)
        let light = flash.map { CGRect(x: extraX + (width - $0) / 2, y: extraY + (height - $0) / 2, width: $0, height: $0) }
        GeometryReader { proxy in
            Color.clear.contentShape(StarfieldPaymentSuccessHitShape(original: original, flash: light))
            .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .global).updating($active) { _, active, _ in active = true }
                .onChanged { event in if !began { pendingOut?.cancel(); let frame = proxy.frame(in: .global); region = CGRect(x: frame.minX + extraX, y: frame.minY + extraY, width: width, height: height); began = true; pressIn() }; let next = contains(event.location); if next != inside { if next { pressIn() } else { pressOut() } } }
                .onEnded { _ in guard began else { return }; began = false; let perform = inside; if inside { pressOut() }; if perform { activate() } })
            .onChange(of: active) { _, next in if !next { DispatchQueue.main.async { if !active && began { began = false; if inside { pressOut() } } } } }
            .onDisappear { pendingOut?.cancel(); hold?(false) }.allowsHitTesting(!disabled).disabled(disabled)
            .accessibilityElement(children: .ignore).accessibilityAddTraits(.isButton).accessibilityLabel(label).accessibilityAction { if !disabled { activate() } }
        }.frame(width: width + extraX * 2, height: height + extraY * 2).offset(x: -extraX, y: -extraY)
    }
}

private enum StarfieldPaymentSuccessText {
    static func font(_ size: Double, _ weight: UIFont.Weight, tabular: Bool) -> UIFont {
        tabular ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight) : UIFont.systemFont(ofSize: size, weight: weight)
    }
    static func width(_ value: String, font: UIFont, tracking: Double, pixel: Double) -> Double {
        let raw = value.components(separatedBy: .newlines).map { ($0 as NSString).size(withAttributes: [.font: font, .kern: tracking]).width }.max() ?? 0
        return ceil(raw * pixel) / pixel
    }
    static func height(_ value: String, font: UIFont, line: Double, width: Double, tracking: Double, oneLine: Bool) -> Double {
        if oneLine { return line }; let paragraph = NSMutableParagraphStyle(); paragraph.minimumLineHeight = line; paragraph.maximumLineHeight = line
        let raw = (value as NSString).boundingRect(with: CGSize(width: max(1, width), height: .greatestFiniteMagnitude), options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: [.font: font, .kern: tracking, .paragraphStyle: paragraph], context: nil).height
        return max(line, ceil(raw / line) * line)
    }
}

private struct StarfieldPaymentSuccessKey: View {
    let value: String
    let width: Double
    let held: Bool
    let fontScale: Double
    let action: () -> Void
    @State private var pressed = false
    @State private var flash = Tween(0)
    private typealias S = StarfieldPaymentSuccessStyle
    private typealias M = StarfieldPaymentSuccessMotion
    private var down: Bool { pressed || held }
    var body: some View {
        TimelineView(.animation) { clock in
            ZStack {
                Circle().fill(.white.opacity(0.1)).frame(width: S.keyFlash, height: S.keyFlash).opacity(flash.value(at: clock.date)).allowsHitTesting(false)
                if value == "back" { StarfieldPaymentSuccessIcon(name: "delete", size: S.deleteSize, color: .white, strokeWidth: S.deleteStroke).allowsHitTesting(false) }
                else { Text(value).font(.system(size: S.digit * fontScale, weight: .medium)).foregroundStyle(.white).allowsHitTesting(false) }
            }.frame(width: width, height: S.key).accessibilityHidden(true)
                .overlay(alignment: .topLeading) { StarfieldPaymentSuccessHit(width: width, height: S.key, label: value == "back" ? "Delete" : value, flash: S.keyFlash, hold: { pressed = $0 }, activate: action) }
        }.onChange(of: down) { _, next in flash.retarget(next ? 1 : 0, duration: next ? M.flashOn : M.flashOff, curve: M.css) }
    }
}

private struct StarfieldPaymentSuccessButton<Content: View>: View {
    let width: Double
    let height: Double
    let label: String
    var disabled = false
    var automatic = false
    var shrinks = false
    var duration = StarfieldPaymentSuccessMotion.buttonCSS
    let action: () -> Void
    @ViewBuilder let content: () -> Content
    @State private var pressed = false
    @State private var coverage = Tween(0)
    private var down: Bool { pressed || automatic }
    var body: some View {
        TimelineView(.animation) { clock in
            let amount = coverage.value(at: clock.date)
            content().frame(width: width, height: height).opacity(1 + (StarfieldPaymentSuccessStyle.pressed - 1) * amount)
                .scaleEffect(shrinks ? 1 + (StarfieldPaymentSuccessMotion.pressScale - 1) * amount : 1).allowsHitTesting(false).accessibilityHidden(true)
                .overlay(alignment: .topLeading) { StarfieldPaymentSuccessHit(width: width, height: height, label: label, disabled: disabled, hold: { pressed = $0 }, activate: action) }
        }.onChange(of: down) { _, next in coverage.retarget(next ? 1 : 0, duration: duration, curve: StarfieldPaymentSuccessMotion.css) }
            .accessibilityHidden(disabled)
    }
}

struct StarfieldPaymentSuccess: View {
    var autoPlay: Bool
    var autoPlayAmount: String
    var currency: String
    var symbol: String
    var quoteSymbol: String
    var rate: Double
    var maxDigits: Double
    var cardNumber: String
    var cardName: String
    var processingDuration: Double
    var accent: String
    var title: String
    var payingFromLabel: String
    var confirmLabel: String
    var waitingTitle: String
    var waitingHint: String
    var doneLabel: String
    var reviewLabel: String
    var onConfirm: ((Double) -> Void)?
    var onSuccess: ((Double) -> Void)?
    var onReview: ((Double) -> Void)?
    var onShare: ((Double) -> Void)?
    var onCurrencyPress: (() -> Void)?
    var onCardPress: (() -> Void)?
    var onClose: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var fontScale = 1.0
    @StateObject private var state = StarfieldPaymentSuccessState()
    private typealias S = StarfieldPaymentSuccessStyle
    private typealias M = StarfieldPaymentSuccessMotion
    private typealias P = StarfieldPaymentSuccessSpaceMotion
    private typealias F = StarfieldPaymentSuccessMath
    private typealias N = StarfieldPaymentSuccessNumbers
    private typealias T = StarfieldPaymentSuccessText
    init(autoPlay: Bool = false, autoPlayAmount: String = "100", currency: String = "USD", symbol: String = "$", quoteSymbol: String = "€", rate: Double = 1.087, maxDigits: Double = 6, cardNumber: String = "**** 3256", cardName: String = "ORBIT DEBIT", processingDuration: Double = 2.7, accent: String = "#5b2eff", title: String = "Enter Amount", payingFromLabel: String = "Paying from", confirmLabel: String = "Confirm", waitingTitle: String = "Starting your transaction", waitingHint: String = "Please hold on..", doneLabel: String = "Successfully sent", reviewLabel: String = "Review", onConfirm: ((Double) -> Void)? = nil, onSuccess: ((Double) -> Void)? = nil, onReview: ((Double) -> Void)? = nil, onShare: ((Double) -> Void)? = nil, onCurrencyPress: (() -> Void)? = nil, onCardPress: (() -> Void)? = nil, onClose: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.autoPlayAmount = autoPlayAmount; self.currency = currency; self.symbol = symbol; self.quoteSymbol = quoteSymbol; self.rate = rate; self.maxDigits = maxDigits; self.cardNumber = cardNumber; self.cardName = cardName; self.processingDuration = processingDuration; self.accent = accent; self.title = title; self.payingFromLabel = payingFromLabel; self.confirmLabel = confirmLabel; self.waitingTitle = waitingTitle; self.waitingHint = waitingHint; self.doneLabel = doneLabel; self.reviewLabel = reviewLabel; self.onConfirm = onConfirm; self.onSuccess = onSuccess; self.onReview = onReview; self.onShare = onShare; self.onCurrencyPress = onCurrencyPress; self.onCardPress = onCardPress; self.onClose = onClose
    }
    private var ink: Color { StarfieldPaymentSuccessCSS.color(accent) }
    private var tail: String { "(\(quoteSymbol)1 = \(symbol)\(N.string(rate)))" }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets, width = proxy.size.width + insets.leading + insets.trailing, height = proxy.size.height + insets.top + insets.bottom
            let formTop = insets.top + S.topGap + S.close, headHeight = S.headTop + S.titleLine * fontScale + S.amountTop + S.amountRow + S.chipTop + S.chip
            let cardPartHeight = S.dividerTop + 1 / displayScale + S.payingTop + S.titleLine * fontScale + S.cardTop + S.cardHeight, formEnd = formTop + headHeight + cardPartHeight
            let bottom = max(insets.bottom, S.bottomMin) + S.buttonLift, rise = S.button + bottom + M.extraRise
            let padEnd = height - max(insets.bottom, S.bottomMin) - S.padEnd, row = max(S.rowMin, (padEnd - formEnd - S.padGap) / 4), rowClosed = max(S.rowMin - 6, (padEnd - S.button - formEnd - S.padGapClosed) / 4)
            let _ = configure()
            TimelineView(.animation) { clock in
                ZStack(alignment: .topLeading) {
                    S.black
                    backdrop(width: width, height: height).opacity(backdropOpacity)
                    if state.busy { StarfieldPaymentSuccessSpace(elapsed: state.elapsed, duration: processingDuration, still: reduceMotion, width: width, height: height).opacity(1 - F.smooth(state.leaving, 0, 0.6)).allowsHitTesting(false).accessibilityHidden(true) }
                    ZStack(alignment: .topLeading) {
                        head(width: width, date: clock.date).opacity(chrome(head: true).0).offset(y: formTop + chrome(head: true).1)
                        cardPart(width: width).opacity(chrome(head: false).0).offset(y: formTop + headHeight + chrome(head: false).1)
                        pad(width: width, height: height, formEnd: formEnd, row: row, rowClosed: rowClosed)
                        wideButton(confirmLabel, width: width - S.confirmMargin * 2, auto: state.autoPressed, disabled: !state.confirmUp, shrink: true) { state.touched(); state.start(auto: false) }
                            .offset(x: S.confirmMargin, y: height - bottom - S.button + rise * (1 - state.confirm.value) + (state.elapsed < 0 ? 0 : rise * pow(F.clamp01(state.elapsed / P.drop), 2)))
                    }.frame(width: width, height: height, alignment: .topLeading).allowsHitTesting(!state.busy).accessibilityHidden(state.busy)
                    waiting(width: width).offset(y: height * S.waitingAt - S.waitingLift)
                    result(width: width).offset(y: height * S.resultAt - S.resultLift)
                    bar(width: width, rise: rise).offset(x: S.barMargin, y: height - bottom - S.button).allowsHitTesting(state.phase == .done)
                    close.offset(x: width - S.closeInset - S.close, y: insets.top + S.topGap)
                }.frame(width: width, height: height, alignment: .topLeading).clipped().offset(x: -insets.leading, y: -insets.top)
                    .onChange(of: clock.date) { _, date in state.advance(date) }
            }.onAppear { state.advance(.now) }
        }.background(S.black.ignoresSafeArea()).preferredColorScheme(.dark).ignoresSafeArea(.keyboard)
            .task(id: StarfieldPaymentSuccessLoopKey(enabled: autoPlay, digits: autoPlayAmount, check: processingDuration + P.checkAfter, touches: state.touches)) { await state.autoplay(enabled: autoPlay, amount: autoPlayAmount) }
            .onDisappear { state.stop() }
    }
    private func configure() {
        state.maxDigits = maxDigits; state.duration = processingDuration; state.still = reduceMotion
        state.onConfirm = onConfirm; state.onSuccess = onSuccess; state.onReview = onReview; state.onShare = onShare; state.onCurrencyPress = onCurrencyPress; state.onCardPress = onCardPress; state.onClose = onClose
    }
    private func text(_ value: String, size: Double, line: Double, weight: UIFont.Weight = .regular, color: Color = .white, width: Double, oneLine: Bool = false, tracking: Double = 0, tabular: Bool = false, intrinsic: Bool = false, alignment: TextAlignment = .center) -> some View {
        let font = T.font(size * fontScale, weight, tabular: tabular), line = line * fontScale
        let width = intrinsic ? min(width, T.width(value, font: font, tracking: tracking, pixel: displayScale)) : width
        let height = T.height(value, font: font, line: line, width: width, tracking: tracking, oneLine: oneLine), leading = (line - font.lineHeight) / 2
        return Text(value).font(Font(font)).foregroundStyle(color).tracking(tracking).lineSpacing(line - font.lineHeight).lineLimit(oneLine ? 1 : nil).multilineTextAlignment(alignment)
            .padding(.vertical, leading).frame(width: max(0, width), height: height, alignment: alignment == .leading ? .topLeading : .top).alignmentGuide(.firstTextBaseline) { _ in leading + font.ascender }
    }
    private func backdrop(width: Double, height: Double) -> some View {
        Canvas { context, _ in
            context.fill(Path(CGRect(x: 0, y: 0, width: width, height: height * 0.36)), with: .linearGradient(Gradient(colors: [S.tint, S.black]), startPoint: .zero, endPoint: CGPoint(x: 0, y: height * 0.36)))
            let gradient = Gradient(stops: S.falloffAt.enumerated().map { .init(color: .rgba(S.ember[0], S.ember[1], S.ember[2], S.emberAlpha * S.falloff[$0.offset]), location: $0.element) })
            context.fillEllipse(center: CGPoint(x: width / 2, y: height * S.emberY), radii: CGSize(width: width * S.emberWidth, height: height * S.emberHeight), gradient: gradient)
        }.frame(width: width, height: height).allowsHitTesting(false).accessibilityHidden(true)
    }
    private var backdropOpacity: Double { state.elapsed < 0 ? 1 : state.leaving > 0 ? F.smooth(state.leaving, 0.3, 1) : 1 - F.smooth(state.elapsed, P.dissolveAt, P.dissolveAt + P.dissolveFor) }
    private func chrome(head: Bool) -> (Double, Double) {
        if state.elapsed < 0 { return (1, 0) }
        if state.leaving > 0 { let p = F.smooth(state.leaving, 0.3, 0.8); return (p, reduceMotion ? 0 : (head ? -M.backTravel : M.backTravel) * (1 - p)) }
        let p = F.clamp01((state.elapsed - P.dissolveAt) / P.dissolveFor)
        return (1 - F.smooth(p, 0, head ? 0.8 : 0.5), reduceMotion ? 0 : (head ? -M.float : M.sink) * p * p)
    }
    private func caret(_ date: Date) -> some View {
        let p = max(0, date.timeIntervalSince(state.caretAt)).truncatingRemainder(dividingBy: M.caretPeriod) / M.caretPeriod
        let alpha = p <= M.caretFadeAt ? 1 : p < M.caretGoneAt ? 1 - (p - M.caretFadeAt) / (M.caretGoneAt - M.caretFadeAt) : p <= M.caretBackAt ? 0 : (p - M.caretBackAt) / (1 - M.caretBackAt)
        return RoundedRectangle(cornerRadius: S.caretRadius).fill(ink).frame(width: S.caretWidth, height: S.caret).opacity(alpha).padding(.horizontal, S.caretMargin)
    }
    private func digitOpacity(_ time: Double) -> Double { let value = F.clamp01(time); return value < 0.5 ? 2 * value * value : 1 - pow(-2 * value + 2, 2) / 2 }
    private func amount(width: Double, date: Date) -> some View {
        HStack(spacing: 0) {
            if state.digitUnits.isEmpty { caret(date); text("0.00", size: S.amount, line: S.amountRow, weight: .light, color: S.placeholder, width: width, tracking: S.amountTracking, tabular: true, intrinsic: true) }
            else {
                ForEach(state.digitUnits.indices, id: \.self) { index in
                    let digit = String(decoding: [state.digitUnits[index]], as: UTF16.self), started = state.digitStarts[index]
                    text(digit, size: S.amount, line: S.amountRow, weight: .light, width: width, tracking: S.amountTracking, tabular: true, intrinsic: true).opacity(started.map { digitOpacity(date.timeIntervalSince($0) / M.digitIn) } ?? 1)
                }
                caret(date)
            }
            text(currency, size: S.amount, line: S.amountRow, weight: .light, color: S.unit, width: width, tracking: S.amountTracking, tabular: true, intrinsic: true).padding(.leading, state.digitUnits.isEmpty ? S.emptyUnitGap : S.unitGap)
            StarfieldPaymentSuccessIcon(name: "select", size: S.selectSize, color: S.unit, strokeWidth: S.selectStroke).padding(.leading, S.selectGap)
        }.fixedSize(horizontal: true, vertical: false).frame(height: S.amountRow).allowsHitTesting(false).accessibilityHidden(true)
            .overlay { StarfieldPaymentSuccessHit(width: amountWidth(width), height: S.amountRow, label: "\(title): \(N.string(state.amount)) \(currency)", activate: state.currency) }
    }
    private func amountWidth(_ width: Double) -> Double {
        let font = T.font(S.amount * fontScale, .light, tabular: true)
        let figures = state.digitUnits.isEmpty ? ["0.00"] : state.digitUnits.map { String(decoding: [$0], as: UTF16.self) }
        return figures.reduce(0) { $0 + min(width, T.width($1, font: font, tracking: S.amountTracking, pixel: displayScale)) } + min(width, T.width(currency, font: font, tracking: S.amountTracking, pixel: displayScale)) + S.caretWidth + S.caretMargin * 2 + (state.digitUnits.isEmpty ? S.emptyUnitGap : S.unitGap) + S.selectGap + S.selectSize
    }
    private func chip(_ label: String, width: Double) -> some View {
        HStack(spacing: S.chipGap) {
            StarfieldPaymentSuccessIcon(name: "swap", size: S.chipIcon, color: S.unit)
            text(label, size: S.chipText, line: S.chipLine, weight: .medium, color: S.unit, width: max(0, width - S.chipPadding * 2), oneLine: true, tabular: true, intrinsic: true)
        }.padding(.horizontal, S.chipPadding).frame(height: S.chip).background(S.tile, in: Capsule()).fixedSize(horizontal: true, vertical: false)
    }
    private func head(width: Double, date: Date) -> some View {
        VStack(spacing: 0) {
            text(title, size: S.title, line: S.titleLine, color: S.label, width: width, oneLine: true)
            amount(width: width, date: date).padding(.top, S.amountTop)
            chip("\(quoteSymbol)\(N.money(state.amount / rate).0).\(N.money(state.amount / rate).1) \(tail)", width: width).padding(.top, S.chipTop)
        }.padding(.top, S.headTop).frame(width: width)
    }
    private func cardPart(width: Double) -> some View {
        VStack(spacing: 0) {
            Rectangle().fill(.white.opacity(S.dividerAlpha)).frame(height: 1 / displayScale).padding(.top, S.dividerTop)
            text(payingFromLabel, size: S.title, line: S.titleLine, color: S.label, width: width, oneLine: true).padding(.top, S.payingTop)
            StarfieldPaymentSuccessButton(width: width, height: S.cardTop + S.cardHeight, label: "\(payingFromLabel) \(cardName) \(cardNumber)", duration: M.cardCSS, action: state.card) {
                card(width: width - S.cardMargin * 2).padding(.top, S.cardTop).padding(.horizontal, S.cardMargin)
            }
        }.frame(width: width)
    }
    private func card(width: Double) -> some View {
        HStack(spacing: 0) {
            StarfieldPaymentSuccessCardArt()
            VStack(alignment: .leading, spacing: S.cardNameTop) {
                text(cardNumber, size: S.cardNumber, line: S.cardNumberLine, weight: .medium, width: width - S.cardLeft - S.cardRight - S.cardBorder * 2 - S.artWidth - S.cardTextGap - S.cardChevron, oneLine: true, alignment: .leading)
                text(cardName, size: S.cardNameText, line: S.cardNameLine, weight: .medium, color: S.cardName, width: width - S.cardLeft - S.cardRight - S.cardBorder * 2 - S.artWidth - S.cardTextGap - S.cardChevron, oneLine: true, tracking: S.cardNameTracking, alignment: .leading)
            }.padding(.leading, S.cardTextGap)
            StarfieldPaymentSuccessIcon(name: "chevron", size: S.cardChevron, color: S.cardName)
        }.padding(.leading, S.cardLeft + S.cardBorder).padding(.trailing, S.cardRight + S.cardBorder).frame(width: width, height: S.cardHeight)
            .background(LinearGradient(colors: S.card, startPoint: .leading, endPoint: .trailing), in: RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous))
            .overlay { RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).strokeBorder(.white.opacity(S.cardBorderAlpha), lineWidth: S.cardBorder) }.accessibilityHidden(true)
    }
    private func pad(width: Double, height: Double, formEnd: Double, row: Double, rowClosed: Double) -> some View {
        let rowHeight = row + (rowClosed - row) * state.confirm.value, top = formEnd + S.padGap + (S.padGapClosed - S.padGap) * state.confirm.value, column = (width - S.padMargin * 2) / 3
        return ZStack(alignment: .topLeading) {
            ForEach(S.keys.indices, id: \.self) { index in
                let motion = rowMotion(index)
                HStack(spacing: 0) {
                    ForEach(S.keys[index], id: \.self) { key in
                        if key.isEmpty { Color.clear.frame(width: column, height: S.key).allowsHitTesting(false) }
                        else { StarfieldPaymentSuccessKey(value: key, width: column, held: state.autoKey == key, fontScale: fontScale) { state.key(key) } }
                    }
                }.opacity(motion.0).offset(x: S.padMargin, y: top + rowHeight * (Double(index) + 0.5) - S.key / 2 + motion.1)
            }
        }.frame(width: width, height: height, alignment: .topLeading)
    }
    private func rowMotion(_ index: Int) -> (Double, Double) {
        if state.elapsed < 0 { return (1, 0) }
        if state.leaving > 0 { let p = F.smooth(state.leaving, 0.3 + Double(index) * M.rowStagger, 0.8 + Double(index) * M.rowStagger); return (p, reduceMotion ? 0 : M.backTravel * (1 - p)) }
        let p = F.clamp01((state.elapsed - P.dissolveAt) / P.dissolveFor); return (1 - F.smooth(p, 0, 0.85), reduceMotion ? 0 : M.sink * p * p)
    }
    private func wideButton(_ label: String, width: Double, auto: Bool = false, disabled: Bool = false, shrink: Bool = false, arrow: Bool = false, action: @escaping () -> Void) -> some View {
        StarfieldPaymentSuccessButton(width: width, height: S.button, label: label, disabled: disabled, automatic: auto, shrinks: shrink, action: action) {
            HStack(spacing: S.wideGap) {
                text(label, size: S.wideText, line: S.wideLine, weight: .semibold, width: width, oneLine: true, intrinsic: true)
                if arrow { StarfieldPaymentSuccessIcon(name: "arrow", size: S.arrowSize, color: .white) }
            }.frame(width: width, height: S.button).background(ink, in: Capsule())
        }
    }
    private func waiting(width: Double) -> some View {
        let p = F.smooth(state.elapsed, P.textAt, P.textAt + P.textIn), alpha = p * (1 - F.smooth(state.elapsed, processingDuration, processingDuration + P.waitOut))
        return VStack(spacing: S.waitingHintTop) {
            text(waitingTitle, size: S.waitingText, line: S.waitingLine, weight: .medium, width: width, oneLine: true, tracking: S.waitingTracking)
            text(waitingHint, size: S.waitingHint, line: S.waitingHintLine, color: S.label, width: width, oneLine: true)
        }.opacity(alpha).offset(y: reduceMotion ? 0 : M.textRise * (1 - p)).allowsHitTesting(false)
            .accessibilityElement(children: .ignore).accessibilityLabel("\(waitingTitle). \(waitingHint)").accessibilityHidden(state.phase != .waiting)
    }
    private func resultMotion(_ index: Int) -> (Double, Double) {
        let from = state.check + P.resultAfter + Double(index) * M.resultStagger, p = F.smooth(state.elapsed, from, from + P.resultIn)
        return (p * (1 - F.smooth(state.leaving, 0, 0.5)), reduceMotion ? 0 : M.resultRise * (1 - p))
    }
    private func result(width: Double) -> some View {
        let money = N.money(state.sent), quote = N.money(state.sent / rate), done = resultMotion(0), sum = resultMotion(1), note = resultMotion(2)
        return VStack(spacing: 0) {
            text(doneLabel, size: S.done, line: S.doneLine, weight: .medium, width: width, oneLine: true, tracking: S.doneTracking).opacity(done.0).offset(y: done.1)
            HStack(alignment: .firstTextBaseline, spacing: 0) {
                text("−", size: S.sumSign, line: S.sumHeight, weight: .thin, color: S.placeholder, width: width, intrinsic: true).padding(.trailing, S.sumSignGap)
                text(symbol + money.0, size: S.sumWhole, line: S.sumHeight, width: width, tracking: S.sumTracking, tabular: true, intrinsic: true)
                text("." + money.1, size: S.sumCents, line: S.sumHeight, color: S.placeholder, width: width, tabular: true, intrinsic: true)
            }.fixedSize(horizontal: true, vertical: false).frame(height: S.sumHeight, alignment: .top).opacity(sum.0).offset(y: sum.1).padding(.top, S.sumTop).padding(.bottom, S.sumBottom)
            chip("\(quoteSymbol)\(quote.0).\(quote.1) \(tail)", width: width).padding(.top, S.chipTop).opacity(note.0).offset(y: note.1)
        }.frame(width: width).allowsHitTesting(false).accessibilityElement(children: .ignore).accessibilityLabel("\(doneLabel): \(symbol)\(money.0).\(money.1)").accessibilityHidden(state.phase != .done)
    }
    private func bar(width: Double, rise: Double) -> some View {
        let review = F.backOut((state.elapsed - state.check - P.barAfter) / P.barIn), share = F.backOut((state.elapsed - state.check - P.barAfter - M.shareAfter) / P.barIn), exit = rise * pow(F.clamp01(state.leaving / M.barDrop), 2)
        return HStack(spacing: S.barGap) {
            wideButton(reviewLabel, width: width - S.barMargin * 2 - S.barGap - S.button, arrow: true, action: state.review).offset(y: rise * (1 - review) + exit)
            StarfieldPaymentSuccessButton(width: S.button, height: S.button, label: "Share", action: state.share) {
                StarfieldPaymentSuccessIcon(name: "share", size: S.shareSize, color: .white, strokeWidth: S.shareStroke).frame(width: S.button, height: S.button).background(S.share, in: Circle()).overlay { Circle().strokeBorder(.white.opacity(S.shareBorderAlpha), lineWidth: 1) }
            }.offset(y: rise * (1 - share) + exit)
        }
    }
    private var close: some View {
        StarfieldPaymentSuccessIcon(name: "close", size: S.closeIcon, color: .white, strokeWidth: S.closeStroke).frame(width: S.close, height: S.close).background(S.tile, in: RoundedRectangle(cornerRadius: S.closeRadius, style: .continuous)).allowsHitTesting(false)
            .overlay(alignment: .topLeading) { StarfieldPaymentSuccessHit(width: S.close, height: S.close, label: "Close", slop: S.closeHit, activate: state.close) }
    }
}
