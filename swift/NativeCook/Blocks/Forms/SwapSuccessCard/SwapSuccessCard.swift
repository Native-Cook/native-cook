import SwiftUI

struct SwapSuccessCardToken: Equatable {
    var symbol: String
    var balance: Double
    var disc: Color
    var dot: Color
    static let samples = [
        Self(symbol: "VLA", balance: 1456, disc: .white, dot: Color(hex: 0x1a1a1a)),
        Self(symbol: "ORO", balance: 12678, disc: Color(hex: 0xf7931a), dot: Color(hex: 0xa35b00)),
    ]
}

struct SwapSuccessCardExchange {
    let from: SwapSuccessCardToken
    let to: SwapSuccessCardToken
    let amount: Double
    let received: Double
}

private enum SwapSuccessCardPalette {
    static let sky = [Color(hex: 0x12917c), Color(hex: 0x0e6e66), Color(hex: 0x0a4a47)]
    static let frame = [Color(hex: 0x16a870), Color(hex: 0x0f8a5e), Color(hex: 0x0b4a36), Color(hex: 0x0a1210), Color(hex: 0x0b0b0b)]
    static let frameAt = [0.0, 0.16, 0.44, 0.66, 1.0]
    static let poolColors = [Color.rgba(9, 11, 10, 0.9), Color.rgba(9, 11, 10, 0.78), Color.rgba(9, 11, 10, 0)]
    static let poolAt = [0.0, 0.6, 1.0]
    static let panel = Color(hex: 0x0d0d0d)
    static let well = Color(hex: 0x151615)
    static let hairline = Color.white.opacity(0.08)
    static let ink = Color.white
    static let mute = Color(hex: 0x8e9794)
    static let faint = Color(hex: 0x5f6b67)
    static let mint = Color(hex: 0x1ee39a)
    static let mintDeep = Color(hex: 0x0fb67a)
    static let mintLight = Color(hex: 0x7cfbdd)
    static let buttonInk = Color(hex: 0x06281c)
}

private enum SwapSuccessCardMetrics {
    static let card = (width: 342.0, height: 340.0, radius: 10.0, margin: 30.0)
    static let halo = 10.0
    static let spill = 44.0
    static let panel = (x: 12.0, y: 60.0, height: 217.0, radius: 8.0)
    static let well = (inset: 6.0, y: 105.0, height: 106.0, radius: 8.0)
    static let button = (x: 17.0, y: 287.0, height: 37.0, radius: 5.0, squeeze: 4.0)
    static let done = (height: 232.0, panelY: 20.0, panelHeight: 188.0)
    static let doneTop = (card.height - done.height) / 2
    static let slot = 101.0
    static let rowTop = 28.0
    static let rowHeight = 70.0
    static let textLeft = 18.0
    static let switchSize = 28.0
    static let switchY = 97.0
    static let ring = (y: doneTop + done.panelY + 48, radius: 29.0, stroke: 5.0, disc: 23.5)
    static let ringCanvas = 96.0
    static let successTitleY = doneTop + done.panelY + 92
    static let keyboardGap = 14.0
    static let pool = (x: 0.52, y: 0.5, rx: 0.48, ry: 0.42)
    static let titleSize = 14.5
    static let titleHeight = 19.0
    static let modeSize = 12.0
    static let modeHeight = 16.0
    static let slotLabelSize = 12.5
    static let slotLabelHeight = 17.0
    static let amountSize = 27.0
    static let amountHeight = 34.0
    static let amountSlot = 40.0
    static let amountRight = 112.0
    static let caret = (width: 1.5, height: 28.0, gap: 1.5)
    static let chipHeight = 28.0
    static let chipRadius = 9.0
    static let symbolSize = 12.5
    static let symbolHeight = 16.0
    static let balanceSize = 11.0
    static let balanceHeight = 15.0
    static let maxSize = 10.0
    static let maxHeight = 14.0
    static let successSize = 19.5
    static let successHeight = 26.0
    static let lineSize = 10.5
    static let lineHeight = 14.0
    static let maxLength = 11
}

private enum SwapSuccessCardMotion {
    static let loop = 9.6
    static let tapAt = 0.8
    static let typeAt = 1.1
    static let typeEvery = 0.15
    static let switchAt = 2.75
    static let pressAt = 3.9
    static let pressFor = 0.15
    static let leaveAt = 7.3
    static let userHold = 3.0
    static let blink = loop / 8
    static let contentOut = 0.28
    static let contractAt = 0.24
    static let contractFor = 0.34
    static let dotAt = 0.5
    static let dotIn = 0.08
    static let bloomAt = 0.62
    static let bloom = (omega: 15.0, zeta: 0.55)
    static let dotScale = 0.17
    static let checkAt = 0.74
    static let checkFor = 0.3
    static let titleAt = 0.7
    static let titleFor = 0.48
    static let titleRise = 22.0
    static let titleBlur = 12.0
    static let linesAt = 1.08
    static let lineGap = 0.1
    static let lineFor = 0.3
    static let lineRise = 4.0
    static let swapEnd = 1.6
    static let outFor = 0.4
    static let emptyFor = 0.2
    static let inFor = 0.7
    static let turnHalf = outFor + emptyFor / 2
    static let turnEnd = outFor + emptyFor + inFor
    static let outScale = 0.96
    static let inScale = 0.94
    static let inBlur = 16.0
    static let flip = (mass: 1.0, stiffness: 190.0, damping: 17.0)
    static let cross = (grow: 0.05, shrink: 0.1, dim: 0.7)
    static let pressIn = 0.08
    static let pressOut = 0.16
    static let keyboard = 0.25
    static let busyPoll = 0.1
    static let unflipWait = 0.7
    static let frameLimit = 0.064
    static let energyThreshold = 6e-9
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ value: Double, from: Double, to: Double) -> Double {
        let u = clamp((value - from) / (to - from)); return u * u * (3 - 2 * u)
    }
    static func springLeft(_ t: Double) -> Double {
        guard t > 0 else { return 1 }
        let omega = bloom.omega; let zeta = bloom.zeta; let damped = omega * sqrt(1 - zeta * zeta)
        return exp(-zeta * omega * t) * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
    }
    static func shownSwap(_ swap: Double, turn: Double) -> Double { turn >= turnHalf ? -1 : swap }
}

private enum SwapSuccessCardDrawing {
    static func rounded(_ rect: CGRect, radius: Double) -> Path { RoundedRectangle(cornerRadius: radius, style: .circular).path(in: rect) }
    static func fill(_ path: Path, shading: GraphicsContext.Shading, blur: Double = 0, in context: GraphicsContext) {
        guard blur > 0 else { context.fill(path, with: shading); return }
        // Skia softens coverage before shading, keeping the gradient's color at each point.
        context.drawLayer { layer in
            var mask = layer; mask.opacity = 1; mask.addFilter(.blur(radius: blur))
            mask.fill(path, with: .color(.white))
            var paint = layer; paint.opacity = 1; paint.blendMode = .sourceIn
            paint.fill(Path(path.boundingRect.insetBy(dx: -blur * 4, dy: -blur * 4)), with: shading)
        }
    }
    static func stroke(_ path: Path, color: Color, width: Double = 1, blur: Double = 0, in context: GraphicsContext) {
        var layer = context
        if blur > 0 { layer.addFilter(.blur(radius: blur)) }
        layer.stroke(path, with: .color(color), lineWidth: width)
    }
    static func gradient(_ colors: [Color], at positions: [Double]) -> Gradient {
        Gradient(stops: zip(colors, positions).map { Gradient.Stop(color: $0, location: $1) })
    }
    static let check: Path = {
        var path = Path(); path.move(to: CGPoint(x: -9, y: 0.5)); path.addLine(to: CGPoint(x: -3, y: 6.5)); path.addLine(to: CGPoint(x: 9.5, y: -6)); return path
    }()
}

private struct SwapSuccessCardChrome: View {
    let width: Double
    let swap: Double
    let turn: Double
    let press: Double
    let still: Bool
    private typealias S = SwapSuccessCardMetrics
    private typealias M = SwapSuccessCardMotion
    private typealias P = SwapSuccessCardPalette
    private typealias D = SwapSuccessCardDrawing
    var body: some View {
        Canvas { context, _ in
            let s = M.shownSwap(swap, turn: turn)
            let contracted = M.smooth(s, from: M.contractAt, to: M.contractAt + M.contractFor)
            let emptied = 1 - M.smooth(s, from: 0, to: M.contentOut)
            let top = S.spill + S.doneTop * contracted
            let height = S.card.height + (S.done.height - S.card.height) * contracted
            let blur = still || turn < M.turnHalf ? 0 : M.inBlur * (1 - M.smooth(turn, from: M.outFor + M.emptyFor, to: M.turnEnd - 0.15))
            let panelWidth = width - S.panel.x * 2
            let halo = CGRect(x: S.spill - S.halo, y: top - S.halo, width: width + S.halo * 2, height: height + S.halo * 2)
            D.fill(D.rounded(halo, radius: S.card.radius + 6), shading: .color(.white.opacity(0.045)), blur: blur, in: context)
            D.stroke(D.rounded(CGRect(x: halo.minX + 0.5, y: halo.minY, width: halo.width - 1, height: halo.height), radius: S.card.radius + 6), color: .white.opacity(0.07), blur: blur, in: context)
            let frame = CGRect(x: S.spill, y: top, width: width, height: height)
            D.fill(D.rounded(frame, radius: S.card.radius), shading: .linearGradient(D.gradient(P.frame, at: P.frameAt), startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: top + height)), blur: blur, in: context)
            context.fillEllipse(center: CGPoint(x: S.spill + width * S.pool.x, y: top + height * S.pool.y), radii: CGSize(width: width * S.pool.rx, height: height * S.pool.ry), gradient: D.gradient(P.poolColors, at: P.poolAt))
            D.stroke(D.rounded(CGRect(x: S.spill + 0.5, y: top, width: width - 1, height: height), radius: S.card.radius), color: .white.opacity(0.1), blur: blur, in: context)
            let panel = CGRect(x: S.spill + S.panel.x, y: top + S.panel.y + (S.done.panelY - S.panel.y) * contracted, width: panelWidth, height: S.panel.height + (S.done.panelHeight - S.panel.height) * contracted)
            D.fill(D.rounded(panel, radius: S.panel.radius), shading: .color(P.panel), blur: blur, in: context)
            D.stroke(D.rounded(CGRect(x: panel.minX + 0.5, y: panel.minY, width: panel.width - 1, height: panel.height), radius: S.panel.radius), color: P.hairline, blur: blur, in: context)
            var form = context; form.opacity = emptied
            let well = CGRect(x: S.spill + S.panel.x + S.well.inset, y: S.spill + S.panel.y + S.well.y, width: panelWidth - S.well.inset * 2, height: S.well.height)
            D.fill(D.rounded(well, radius: S.well.radius), shading: .color(P.well), blur: blur, in: form)
            D.stroke(D.rounded(well.insetBy(dx: 0.5, dy: 0.5), radius: S.well.radius), color: .white.opacity(0.05), blur: blur, in: form)
            let barY = S.spill + S.button.y
            let bar = CGRect(x: S.spill + S.button.x + S.button.squeeze * press, y: barY, width: width - S.button.x * 2 - S.button.squeeze * 2 * press, height: S.button.height)
            D.fill(D.rounded(bar, radius: S.button.radius), shading: .linearGradient(D.gradient([P.mintDeep, P.mint, P.mint], at: [0, 0.45, 1]), startPoint: CGPoint(x: 0, y: barY), endPoint: CGPoint(x: 0, y: barY + S.button.height)), blur: blur, in: form)
            var glow = form
            glow.clip(to: D.rounded(CGRect(x: S.spill + S.button.x + S.button.squeeze, y: barY, width: width - (S.button.x + S.button.squeeze) * 2, height: S.button.height), radius: S.button.radius))
            glow.fillEllipse(center: CGPoint(x: S.spill + width / 2, y: barY + S.button.height * 1.05), radii: CGSize(width: width * 0.36, height: S.button.height * 0.95), gradient: D.gradient([P.mintLight, P.mintLight.opacity(0.5), P.mintLight.opacity(0)], at: [0, 0.45, 1]))
            D.fill(D.rounded(bar, radius: S.button.radius), shading: .color(.black.opacity(0.3 * press)), in: form)
        }
        .frame(width: width + S.spill * 2, height: S.card.height + S.spill * 2)
    }
}

private struct SwapSuccessCardRing: View {
    let swap: Double
    let turn: Double
    let still: Bool
    private typealias S = SwapSuccessCardMetrics
    private typealias M = SwapSuccessCardMotion
    private typealias P = SwapSuccessCardPalette
    var body: some View {
        Canvas { context, _ in
            let s = M.shownSwap(swap, turn: turn)
            let opacity = M.smooth(s, from: M.dotAt, to: M.dotAt + M.dotIn)
            let left = still ? 1 - M.smooth(s, from: M.bloomAt, to: M.bloomAt + 0.2) : M.springLeft(s - M.bloomAt)
            let centre = CGPoint(x: S.ringCanvas / 2, y: S.ringCanvas / 2)
            var ring = context.scaled(1 - (1 - M.dotScale) * left, about: centre); ring.opacity = opacity
            ring.fill(Path(ellipseIn: CGRect(x: centre.x - S.ring.radius, y: centre.y - S.ring.radius, width: S.ring.radius * 2, height: S.ring.radius * 2)), with: .color(.white))
            ring.fill(Path(ellipseIn: CGRect(x: centre.x - S.ring.disc, y: centre.y - S.ring.disc, width: S.ring.disc * 2, height: S.ring.disc * 2)), with: .linearGradient(Gradient(colors: [P.mintDeep, P.mint]), startPoint: CGPoint(x: 0, y: centre.y - S.ring.disc), endPoint: CGPoint(x: 0, y: centre.y + S.ring.disc)))
            ring.translateBy(x: centre.x, y: centre.y)
            let check = M.smooth(s, from: M.checkAt, to: M.checkAt + M.checkFor)
            ring.stroke(SwapSuccessCardDrawing.check.trimmedPath(from: 0, to: check), with: .color(.white), style: StrokeStyle(lineWidth: 3.4, lineCap: .round, lineJoin: .round))
        }.frame(width: S.ringCanvas, height: S.ringCanvas)
    }
}

private enum SwapSuccessCardNumbers {
    private static let grouping = try! NSRegularExpression(pattern: "(?<=[A-Za-z0-9_])(?=(?:[0-9]{3})+(?![0-9]))")
    private static let numeric = try! NSRegularExpression(pattern: "^[+-]?(?:(?:[0-9]+(?:\\.[0-9]*)?|\\.[0-9]+)(?:[eE][+-]?[0-9]+)?|Infinity)$")
    private static let whitespace: CharacterSet = {
        var set = CharacterSet(charactersIn: "\u{0009}\u{000A}\u{000B}\u{000C}\u{000D}\u{0020}\u{00A0}\u{1680}\u{2028}\u{2029}\u{202F}\u{205F}\u{3000}\u{FEFF}")
        set.insert(charactersIn: "\u{2000}"..."\u{200A}"); return set
    }()
    static func grouped(_ text: String) -> String {
        let parts = text.components(separatedBy: ".")
        let range = NSRange(location: 0, length: (parts[0] as NSString).length)
        return grouping.stringByReplacingMatches(in: parts[0], range: range, withTemplate: ",") + (parts.count > 1 ? "." + parts[1] : "")
    }
    static func cleaned(_ text: String) -> String {
        let ascii = text.replacingOccurrences(of: ",", with: ".").utf8.filter { (48...57).contains($0) || $0 == 46 }
        let parts = String(decoding: ascii, as: UTF8.self).components(separatedBy: ".")
        var whole = parts[0]
        while whole.count > 1 && whole.first == "0" { whole.removeFirst() }
        whole = String(whole.prefix(7))
        return parts.count == 1 ? whole : (whole.isEmpty ? "0" : whole) + "." + String(parts.dropFirst().joined().prefix(2))
    }
    static func number(_ text: String) -> Double {
        let clean = text.trimmingCharacters(in: whitespace)
        if clean.isEmpty { return 0 }
        let bytes = Array(clean.utf8)
        if bytes.count > 2, bytes[0] == 48 {
            let radix = bytes[1] == 120 || bytes[1] == 88 ? 16 : bytes[1] == 98 || bytes[1] == 66 ? 2 : bytes[1] == 111 || bytes[1] == 79 ? 8 : 0
            if radix > 0 {
                let width = radix == 16 ? 4 : radix == 8 ? 3 : 1
                var bits: [UInt8] = []
                for byte in bytes.dropFirst(2) {
                    let digit = (48...57).contains(byte) ? Int(byte - 48) : (65...70).contains(byte) ? Int(byte - 65) + 10 : (97...102).contains(byte) ? Int(byte - 97) + 10 : radix
                    guard digit < radix else { return .nan }
                    for shift in (0..<width).reversed() { bits.append(UInt8((digit >> shift) & 1)) }
                }
                guard let first = bits.firstIndex(of: 1) else { return 0 }
                let significant = Array(bits[first...])
                if significant.count <= 53 { return Double(significant.reduce(UInt64(0)) { ($0 << 1) | UInt64($1) }) }
                var exponent = significant.count - 1
                if exponent > 1023 { return .infinity }
                var fraction = significant.prefix(53).reduce(UInt64(0)) { ($0 << 1) | UInt64($1) }
                if significant[53] == 1 && (significant.dropFirst(54).contains(1) || fraction & 1 == 1) { fraction += 1 }
                if fraction == UInt64(1) << 53 { fraction >>= 1; exponent += 1 }
                if exponent > 1023 { return .infinity }
                return Double(bitPattern: UInt64(exponent + 1023) << 52 | (fraction & 0x000f_ffff_ffff_ffff))
            }
        }
        guard numeric.firstMatch(in: clean, range: NSRange(location: 0, length: (clean as NSString).length)) != nil else { return .nan }
        return Double(clean) ?? .nan
    }
    static func string(_ value: Double) -> String {
        if value.isNaN { return "NaN" }
        if value == .infinity { return "Infinity" }
        if value == -.infinity { return "-Infinity" }
        if value == 0 { return "0" }
        let magnitude = abs(value); let raw = String(magnitude).lowercased().split(separator: "e")
        let parts = String(raw[0]).split(separator: ".", omittingEmptySubsequences: false)
        var digits = parts.joined(); var point = parts[0].count + (raw.count > 1 ? Int(raw[1]) ?? 0 : 0)
        while digits.first == "0" { digits.removeFirst(); point -= 1 }
        while digits.last == "0" && digits.count > 1 { digits.removeLast() }
        let sign = value < 0 ? "-" : ""
        if magnitude >= 1e-6 && magnitude < 1e21 {
            if point <= 0 { return sign + "0." + String(repeating: "0", count: -point) + digits }
            if point >= digits.count { return sign + digits + String(repeating: "0", count: point - digits.count) }
            let split = digits.index(digits.startIndex, offsetBy: point)
            return sign + digits[..<split] + "." + digits[split...]
        }
        let rest = String(digits.dropFirst()); let exponent = point - 1
        return sign + String(digits.prefix(1)) + (rest.isEmpty ? "" : "." + rest) + "e" + (exponent >= 0 ? "+" : "") + String(exponent)
    }
    static func fixed(_ value: Double, places: Int) -> String {
        guard value.isFinite, abs(value) < 1e21 else { return string(value) }
        // Decimal rounding starts with the exact binary value, matching Number.toFixed's ties.
        let bits = abs(value).bitPattern; let exponent = Int((bits >> 52) & 0x7ff)
        let significand = (bits & 0x000f_ffff_ffff_ffff) | (exponent == 0 ? 0 : UInt64(1) << 52)
        let coefficient = significand * (places == 4 ? 625 : 25)
        let shift = (exponent == 0 ? -1074 : exponent - 1023 - 52) + places
        var digits: String
        if shift < 0 {
            let right = -shift; var rounded = right >= 64 ? 0 : coefficient >> right
            if right < 64 {
                let remainder = coefficient & ((UInt64(1) << right) - 1)
                if remainder >= UInt64(1) << (right - 1) { rounded += 1 }
            }
            digits = String(rounded)
        } else {
            digits = String(coefficient)
            for _ in 0..<shift {
                var doubled = ""; var carry = 0
                for digit in digits.reversed() {
                    let next = digit.wholeNumberValue! * 2 + carry
                    doubled = String(next % 10) + doubled; carry = next / 10
                }
                digits = (carry > 0 ? String(carry) : "") + doubled
            }
        }
        digits = String(repeating: "0", count: max(0, places + 1 - digits.count)) + digits
        let split = digits.index(digits.endIndex, offsetBy: -places)
        return (value < 0 ? "-" : "") + digits[..<split] + "." + digits[split...]
    }
}

private struct SwapSuccessCardScalar {
    private(set) var value: Double
    private var target: Double
    private var from: Double
    private var velocity = 0.0
    private var energy = 0.0
    private var last = Date.distantPast
    private var start = Date.distantPast
    private var duration = 0.0
    private var cubic = false
    private var springing = false
    private var active = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    mutating func jump(_ next: Double) { value = next; target = next; velocity = 0; active = false; springing = false }
    mutating func time(to next: Double, duration: Double, cubic: Bool = false, at date: Date = .now, still: Bool) {
        if still { jump(next); return }
        advance(at: date)
        if value == next { jump(next); return }
        if !(active && !springing && target == next) { from = value; start = date }
        target = next; self.duration = duration; self.cubic = cubic; springing = false; velocity = 0; active = true
    }
    mutating func spring(to next: Double, at date: Date = .now, still: Bool) {
        if still { jump(next); return }
        if springing && target == next { return }
        let continuing = springing && active
        if !continuing { advance(at: date) }
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; springing = true
        energy = 0.5 * SwapSuccessCardMotion.flip.stiffness * pow(value - target, 2)
        if !continuing { last = date }; active = true
    }
    mutating func advance(at date: Date) {
        guard active else { return }
        guard springing else {
            let t = SwapSuccessCardMotion.clamp(date.timeIntervalSince(start) / duration)
            let u = cubic ? 1 - pow(1 - t, 3) : t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
            value = from + (target - from) * u
            if t == 1 { active = false }; return
        }
        let constants = SwapSuccessCardMotion.flip
        let dt = min(SwapSuccessCardMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
        let omega = sqrt(constants.stiffness / constants.mass); let decay = constants.damping / (2 * constants.mass)
        let x = value - target
        if decay < omega {
            let damped = sqrt(omega * omega - decay * decay); let b = (velocity + decay * x) / damped
            let wave = x * cos(damped * dt) + b * sin(damped * dt); let attenuation = exp(-decay * dt)
            velocity = attenuation * (-decay * wave + damped * (-x * sin(damped * dt) + b * cos(damped * dt)))
            value = target + attenuation * wave
        } else {
            let b = velocity + omega * x; let attenuation = exp(-omega * dt)
            value = target + attenuation * (x + b * dt); velocity = attenuation * (velocity - omega * b * dt)
        }
        let current = 0.5 * constants.stiffness * pow(value - target, 2) + 0.5 * constants.mass * velocity * velocity
        if energy == 0 || current / energy <= SwapSuccessCardMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private struct SwapSuccessCardBackdropBlur: UIViewRepresentable {
    let intensity: Double
    func makeCoordinator() -> SwapSuccessCardBlurCoordinator { SwapSuccessCardBlurCoordinator() }
    func makeUIView(context: Context) -> UIVisualEffectView { UIVisualEffectView(effect: nil) }
    func updateUIView(_ view: UIVisualEffectView, context: Context) {
        let state = context.coordinator
        guard state.intensity != intensity else { return }
        state.intensity = intensity; view.effect = nil; state.animator?.stopAnimation(true)
        state.animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { [weak view] in view?.effect = UIBlurEffect(style: .dark) }
        state.animator?.fractionComplete = intensity / 100
    }
    static func dismantleUIView(_ view: UIVisualEffectView, coordinator: SwapSuccessCardBlurCoordinator) {
        coordinator.animator?.stopAnimation(true); coordinator.animator = nil
    }
    final class SwapSuccessCardBlurCoordinator {
        var intensity = -1.0
        var animator: UIViewPropertyAnimator? = nil
    }
}

private final class SwapSuccessCardLatest {
    var tokens: [SwapSuccessCardToken]
    var rate: Double
    var still = false
    var onSwap: ((SwapSuccessCardExchange) -> Void)?
    var onDone: (() -> Void)?
    init(tokens: [SwapSuccessCardToken], rate: Double) { self.tokens = tokens; self.rate = rate }
}

/// Swap and return clocks run independently, including a check that finishes after an early leave.
struct SwapSuccessCard: View {
    var autoPlay: Bool
    var tokens: [SwapSuccessCardToken]
    var rate: Double
    var demoAmount: String
    var title: String
    var modes: [String]
    var fromLabel: String
    var toLabel: String
    var balanceLabel: String
    var maxLabel: String
    var swapLabel: String
    var successTitle: String
    var successLine: String
    var onSwap: ((SwapSuccessCardExchange) -> Void)?
    var onDone: (() -> Void)?
    var onPickToken: ((SwapSuccessCardToken) -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var draft = ""
    @State private var flipped = false
    @State private var mode = 0
    @State private var focused = false
    @State private var autoCaret = false
    @State private var phase = Phase.form
    @State private var receipt = ""
    @State private var touches = 0
    @State private var flip = SwapSuccessCardScalar(0)
    @State private var press = SwapSuccessCardScalar(0)
    @State private var lift = SwapSuccessCardScalar(0)
    @State private var mounted = Date.now
    @State private var swapStart: Date? = nil
    @State private var swap = -1.0
    @State private var turnStart: Date? = nil
    @State private var returnStart: Date? = nil
    @State private var turn = 0.0
    @State private var swappedByHand = false
    @State private var celebrated = false
    @State private var cardBottom = 0.0
    @State private var focusRequest = 0
    @State private var latest: SwapSuccessCardLatest
    @State private var lightHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var successHaptic = UINotificationFeedbackGenerator()
    private typealias S = SwapSuccessCardMetrics
    private typealias M = SwapSuccessCardMotion
    private typealias P = SwapSuccessCardPalette
    private typealias N = SwapSuccessCardNumbers

    init(
        autoPlay: Bool = false, tokens: [SwapSuccessCardToken] = SwapSuccessCardToken.samples,
        rate: Double = 11908.0 / 456, demoAmount: String = "456.00", title: String = "Exchange", modes: [String] = ["Swap", "Limit", "Bridge"],
        fromLabel: String = "From", toLabel: String = "To", balanceLabel: String = "Balance", maxLabel: String = "Max", swapLabel: String = "Swap",
        successTitle: String = "Successful Exchange", successLine: String = "You have successfully exchanged",
        onSwap: ((SwapSuccessCardExchange) -> Void)? = nil, onDone: (() -> Void)? = nil,
        onPickToken: ((SwapSuccessCardToken) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay; self.tokens = tokens; self.rate = rate; self.demoAmount = demoAmount
        self.title = title; self.modes = modes; self.fromLabel = fromLabel; self.toLabel = toLabel
        self.balanceLabel = balanceLabel; self.maxLabel = maxLabel; self.swapLabel = swapLabel
        self.successTitle = successTitle; self.successLine = successLine
        self.onSwap = onSwap; self.onDone = onDone; self.onPickToken = onPickToken
        _latest = State(initialValue: SwapSuccessCardLatest(tokens: tokens, rate: rate))
    }
    private var from: SwapSuccessCardToken { tokens[flipped ? 1 : 0] }
    private var to: SwapSuccessCardToken { tokens[flipped ? 0 : 1] }
    private var price: Double { flipped ? 1 / rate : rate }
    private var value: Double {
        let number = N.number(draft); return number.isNaN ? 0 : number
    }
    private var received: String { value > 0 ? N.fixed(value * price, places: 2) : "" }
    private var shownSwap: Double { M.shownSwap(swap, turn: turn) }
    private var contentOpacity: Double {
        shownSwap >= 0 ? 1 - M.smooth(shownSwap, from: 0, to: M.contentOut) : turn > 0 ? M.smooth(turn, from: M.outFor + M.emptyFor + M.inFor * 0.35, to: M.turnEnd) : 1
    }
    private var cardOpacity: Double {
        turn <= 0 ? 1 : turn < M.turnHalf ? 1 - M.smooth(turn, from: 0, to: M.outFor) : M.smooth(turn, from: M.outFor + M.emptyFor, to: M.outFor + M.emptyFor + M.inFor * 0.6)
    }
    private var cardScale: Double {
        if reduceMotion { return 1 }
        return turn < M.turnHalf ? 1 - (1 - M.outScale) * M.smooth(turn, from: 0, to: M.outFor) : M.inScale + (1 - M.inScale) * M.smooth(turn, from: M.outFor + M.emptyFor, to: M.turnEnd)
    }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screenWidth = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let width = min(S.card.width, screenWidth - S.card.margin * 2)
            let top = (height - S.card.height) / 2
            TimelineView(.animation) { timeline in
                let elapsed = max(0, timeline.date.timeIntervalSince(mounted))
                let blinkTime = reduceMotion ? min(M.loop, elapsed) : elapsed.truncatingRemainder(dividingBy: M.loop)
                let blink = blinkTime.truncatingRemainder(dividingBy: M.blink) < M.blink * 0.55
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        context.fill(Path(CGRect(x: 0, y: 0, width: screenWidth, height: height)), with: .radialGradient(SwapSuccessCardDrawing.gradient(P.sky, at: [0, 0.5, 1]), center: CGPoint(x: screenWidth / 2, y: height * 0.52), startRadius: 0, endRadius: height * 0.62))
                    }.frame(width: screenWidth, height: height).allowsHitTesting(false)
                    Color.clear.frame(width: screenWidth, height: height).contentShape(Rectangle())
                        .onTapGesture { dismissKeyboard() }.accessibilityHidden(true)
                    card(width: width, blink: blink)
                        .opacity(cardOpacity).scaleEffect(cardScale).offset(x: (screenWidth - width) / 2, y: top + lift.value)
                }
                .frame(width: screenWidth, height: height).background(P.sky[2]).clipped().offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in refreshLatest(); advance(at: date) }
                .onAppear { cardBottom = top + S.card.height + S.halo }
                .onChange(of: top) { _, next in cardBottom = next + S.card.height + S.halo }
            }
        }
        .ignoresSafeArea(.keyboard).preferredColorScheme(.dark)
        .onAppear { mounted = .now; lightHaptic.prepare(); successHaptic.prepare(); refreshLatest() }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillShowNotification)) { moveKeyboard($0, shown: true) }
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillHideNotification)) { moveKeyboard($0, shown: false) }
        .task(id: LoopKey(on: autoPlay, touches: touches, amount: demoAmount)) { await loop() }
    }
    private func card(width: Double, blink: Bool) -> some View {
        let panelWidth = width - S.panel.x * 2
        return ZStack(alignment: .topLeading) {
            SwapSuccessCardChrome(width: width, swap: swap, turn: turn, press: press.value, still: reduceMotion)
                .offset(x: -S.spill, y: -S.spill).allowsHitTesting(false)
            success(width: width).offset(y: S.doneTop)
            if phase == .done {
                let intensity = shownSwap < M.titleAt - 0.1 ? 0 : 38 * (1 - M.smooth(shownSwap, from: M.titleAt + 0.05, to: M.titleAt + M.titleFor))
                SwapSuccessCardBackdropBlur(intensity: intensity)
                    .frame(width: panelWidth, height: S.done.panelHeight)
                    .clipShape(RoundedRectangle(cornerRadius: S.panel.radius, style: .circular))
                    .offset(x: S.panel.x, y: S.doneTop + S.done.panelY).allowsHitTesting(false).accessibilityHidden(true)
            }
            SwapSuccessCardRing(swap: swap, turn: turn, still: reduceMotion)
                .frame(width: width, height: S.ringCanvas).offset(y: S.ring.y - S.ringCanvas / 2).allowsHitTesting(false).accessibilityHidden(true)
            form(width: width, blink: blink).opacity(contentOpacity).allowsHitTesting(phase == .form).accessibilityHidden(phase != .form)
        }
        .frame(width: width, height: S.card.height, alignment: .topLeading)
    }
    private func success(width: Double) -> some View {
        let title = M.smooth(shownSwap, from: M.titleAt, to: M.titleAt + M.titleFor)
        let first = M.smooth(shownSwap, from: M.linesAt, to: M.linesAt + M.lineFor)
        let second = M.smooth(shownSwap, from: M.linesAt + M.lineGap, to: M.linesAt + M.lineGap + M.lineFor)
        return Button {
            touched(); leave()
        } label: {
            ZStack(alignment: .top) {
                Text(successTitle).font(.system(size: S.successSize, weight: .semibold)).tracking(-0.2).foregroundStyle(P.ink).lineLimit(1).minimumScaleFactor(0.01)
                    .frame(width: width - 48, height: S.successHeight).opacity(title)
                    .offset(y: S.successTitleY - S.doneTop + (reduceMotion ? 0 : M.titleRise * (1 - title)))
                Text(successLine).font(.system(size: S.lineSize)).foregroundStyle(P.mute).lineLimit(1)
                    .frame(height: S.lineHeight).opacity(first).offset(y: S.successTitleY - S.doneTop + S.successHeight + 8 + M.lineRise * (1 - first))
                Text(receipt).font(.system(size: S.lineSize)).monospacedDigit().foregroundStyle(P.ink).lineLimit(1)
                    .frame(height: S.lineHeight).opacity(second).offset(y: S.successTitleY - S.doneTop + S.successHeight + 8 + S.lineHeight + 1 + M.lineRise * (1 - second))
            }.frame(width: width, height: S.done.height, alignment: .top).contentShape(Rectangle())
        }
        .buttonStyle(PlainStyle()).disabled(phase != .done).allowsHitTesting(phase == .done).accessibilityHidden(phase != .done)
        .accessibilityLabel(successTitle + ". " + successLine + " " + receipt)
    }
    private func form(width: Double, blink: Bool) -> some View {
        let panelWidth = width - S.panel.x * 2
        let amounts = flipped ? [received, draft] : [draft, received]
        return ZStack(alignment: .topLeading) {
            Text(title).font(.system(size: S.titleSize, weight: .medium)).foregroundStyle(P.ink).lineLimit(1).frame(height: S.titleHeight).offset(x: 13, y: 16)
            HStack(spacing: 20) {
                ForEach(modes.indices, id: \.self) { index in
                    Button {
                        touched(); lightHaptic.impactOccurred(); mode = index
                    } label: {
                        Text(modes[index]).font(.system(size: S.modeSize)).foregroundStyle(mode == index ? P.ink : P.faint)
                            .frame(height: S.modeHeight).contentShape(Rectangle().inset(by: -6))
                    }.buttonStyle(PlainStyle()).accessibilityAddTraits(mode == index ? .isSelected : [])
                }
            }
            .padding(.horizontal, 12).frame(height: 27).background(RoundedRectangle(cornerRadius: 5, style: .circular).fill(P.panel))
            .fixedSize().frame(width: width - 12, alignment: .trailing).offset(y: 13)
            ZStack(alignment: .topLeading) {
                Text(fromLabel).font(.system(size: S.slotLabelSize)).foregroundStyle(P.ink).frame(height: S.slotLabelHeight).offset(x: S.textLeft, y: 8)
                Text(toLabel).font(.system(size: S.slotLabelSize)).foregroundStyle(P.ink).frame(height: S.slotLabelHeight).offset(x: S.textLeft, y: S.well.y + 7)
                ZStack(alignment: .topLeading) {
                    ForEach(0..<2, id: \.self) { index in
                        let editable = (index == 0 ? !flipped : flipped) && phase == .form
                        SwapSuccessCardRow(token: tokens[index], width: panelWidth, slot: index == 0 ? flip.value : 1 - flip.value,
                            front: index == 1, editable: editable, amount: amounts[index], caret: editable && (focused || autoCaret),
                            blink: blink, focusRequest: focusRequest, balanceLabel: balanceLabel,
                            onChange: { text in touched(); draft = N.cleaned(text) },
                            onFocus: { touched(); focused = true }, onBlur: { focused = false },
                            onPick: { touched(); lightHaptic.impactOccurred(); onPickToken?(tokens[index]) })
                            .id(tokens[index].symbol)
                    }
                }.frame(width: panelWidth, height: S.rowHeight, alignment: .topLeading).offset(y: S.rowTop)
                HStack {
                    Spacer()
                    Button {
                        touched(); lightHaptic.impactOccurred(); draft = N.string(from.balance)
                    } label: {
                        Text(maxLabel).font(.system(size: S.maxSize)).foregroundStyle(P.ink).frame(height: S.maxHeight).contentShape(Rectangle().inset(by: -10))
                    }.buttonStyle(DimStyle())
                }.frame(width: panelWidth - S.textLeft - 6).offset(y: S.rowTop + 44)
                Text("1 " + from.symbol + " = " + N.grouped(N.fixed(price, places: price >= 1 ? 2 : 4)) + " " + to.symbol)
                    .font(.system(size: S.balanceSize)).monospacedDigit().foregroundStyle(P.mute).lineLimit(1)
                    .frame(width: panelWidth - S.textLeft, height: S.balanceHeight, alignment: .trailing).offset(y: S.rowTop + S.slot + 45)
                Button {
                    touched(); switchTokens(byHand: true)
                } label: {
                    Image(systemName: "arrow.up.arrow.down").font(.system(size: 17)).foregroundStyle(P.buttonInk)
                        .rotationEffect(.degrees(180 * flip.value)).frame(width: S.switchSize, height: S.switchSize)
                        .background(Circle().fill(P.mint).shadow(color: P.mint.opacity(0.55), radius: 6))
                }
                .buttonStyle(SwitchStyle()).accessibilityLabel("Switch the two tokens")
                .offset(x: panelWidth / 2 - S.switchSize / 2 - 5, y: S.switchY - S.switchSize / 2 - 5)
            // The stack is only as tall as its rows; the panel places everything from its top.
            }.frame(width: panelWidth, height: S.panel.height, alignment: .topLeading).offset(x: S.panel.x, y: S.panel.y)
            Button {
                touched(); exchange(byHand: true)
            } label: {
                Text(swapLabel).font(.system(size: S.titleSize, weight: .medium)).foregroundStyle(P.buttonInk).lineLimit(1)
                    .frame(height: S.titleHeight).scaleEffect(1 - 0.02 * press.value)
                    .frame(width: width - S.button.x * 2, height: S.button.height).contentShape(Rectangle())
            }
            .buttonStyle(HoldStyle(hold: hold)).offset(x: S.button.x, y: S.button.y)
        }.frame(width: width, height: S.card.height, alignment: .topLeading)
    }
    private func refreshLatest() {
        latest.tokens = tokens; latest.rate = rate; latest.still = reduceMotion
        latest.onSwap = onSwap; latest.onDone = onDone
    }
    private func dismissKeyboard() { UIApplication.shared.sendAction(#selector(UIResponder.resignFirstResponder), to: nil, from: nil, for: nil) }
    private func touched() { autoCaret = false; touches += 1; refreshLatest() }
    private func hold(_ down: Bool) { press.time(to: down ? 1 : 0, duration: down ? M.pressIn : M.pressOut, still: latest.still) }
    private func switchTokens(byHand: Bool) {
        guard phase == .form else { return }
        if byHand { lightHaptic.impactOccurred() }
        let number = N.number(draft); let value = number.isNaN ? 0 : number
        let price = flipped ? 1 / latest.rate : latest.rate
        let received = value > 0 ? N.fixed(value * price, places: 2) : ""
        dismissKeyboard(); focused = false; draft = received
        flipped.toggle(); flip.spring(to: flipped ? 1 : 0, still: latest.still)
    }
    private func exchange(byHand: Bool) {
        guard phase == .form else { return }
        let number = N.number(draft); let value = number.isNaN ? 0 : number
        guard value > 0 else { if byHand { focusRequest += 1 }; return }
        if byHand { lightHaptic.impactOccurred() }
        swappedByHand = byHand; celebrated = false
        let from = latest.tokens[flipped ? 1 : 0]; let to = latest.tokens[flipped ? 0 : 1]
        let received = N.fixed(value * (flipped ? 1 / latest.rate : latest.rate), places: 2)
        dismissKeyboard(); autoCaret = false
        receipt = N.grouped(N.fixed(value, places: 2)) + " " + from.symbol + " for " + N.grouped(received) + " " + to.symbol
        phase = .done
        latest.onSwap?(SwapSuccessCardExchange(from: from, to: to, amount: value, received: N.number(received)))
        swap = 0; swapStart = .now
    }
    private func leave() {
        guard phase == .done else { return }
        phase = .turning; turnStart = .now; returnStart = nil; turn = 0
    }
    private func emptied(at date: Date) {
        draft = ""; flipped = false; flip.jump(0)
        turnStart = nil; returnStart = date; turn = M.turnHalf
    }
    private func home() {
        swap = -1; swapStart = nil; turn = 0; turnStart = nil; returnStart = nil; phase = .form
        latest.onDone?()
    }
    private func moveKeyboard(_ notification: Notification, shown: Bool) {
        let duration = (notification.userInfo?[UIResponder.keyboardAnimationDurationUserInfoKey] as? NSNumber)?.doubleValue ?? 0
        let screenY = (notification.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? NSValue)?.cgRectValue.minY ?? 0
        let room = shown ? min(0, screenY - S.keyboardGap - cardBottom) : 0
        lift.time(to: room, duration: duration == 0 ? M.keyboard : duration, cubic: true, still: latest.still)
    }
    private func advance(at date: Date) {
        flip.advance(at: date); press.advance(at: date); lift.advance(at: date)
        if let swapStart {
            swap = min(M.swapEnd, max(0, date.timeIntervalSince(swapStart)))
            if !celebrated && swap >= M.checkAt + M.checkFor {
                celebrated = true
                if swappedByHand { successHaptic.notificationOccurred(.success) }
            }
        }
        if let turnStart {
            turn = min(M.turnHalf, max(0, date.timeIntervalSince(turnStart)))
            if turn >= M.turnHalf { emptied(at: date) }
        } else if let returnStart {
            turn = min(M.turnEnd, M.turnHalf + max(0, date.timeIntervalSince(returnStart)))
            if turn >= M.turnEnd { home() }
        }
    }
    private func run(_ action: EventAction) {
        switch action {
        case .tap: autoCaret = true
        case .type(let prefix): draft = prefix
        case .switchTokens: autoCaret = false; switchTokens(byHand: false)
        case .press: hold(true)
        case .exchange: hold(false); exchange(byHand: false)
        case .leave: leave()
        case .cycle: break
        }
    }
    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            while !Task.isCancelled {
                if phase == .done { leave() }
                if phase != .form {
                    try? await Task.sleep(for: .seconds(M.busyPoll)); continue
                }
                dismissKeyboard()
                if flipped {
                    draft = ""
                    try? await Task.sleep(for: .seconds(M.busyPoll))
                    guard !Task.isCancelled else { return }
                    switchTokens(byHand: false)
                    try? await Task.sleep(for: .seconds(M.unflipWait)); continue
                }
                draft = ""; break
            }
        }
        guard !Task.isCancelled else { return }
        let clock = ContinuousClock(); let origin = clock.now
        let units = Array(demoAmount.utf16)
        var events: [Event] = []; var sequence = 0
        func append(_ time: Double, _ action: EventAction) {
            events.append(Event(time: time, sequence: sequence, action: action)); sequence += 1
        }
        func cycle(_ start: Double) {
            append(start + M.tapAt, .tap)
            for index in units.indices {
                append(start + M.typeAt + Double(index) * M.typeEvery, .type(String(decoding: units.prefix(index + 1), as: UTF16.self)))
            }
            append(start + M.switchAt, .switchTokens); append(start + M.pressAt, .press)
            append(start + M.pressAt + M.pressFor, .exchange); append(start + M.leaveAt, .leave)
            append(start + M.loop, .cycle(start + M.loop))
        }
        cycle(0)
        while !Task.isCancelled {
            events.sort { $0.time == $1.time ? $0.sequence < $1.sequence : $0.time < $1.time }
            let event = events.removeFirst()
            try? await clock.sleep(until: origin + .seconds(event.time))
            guard !Task.isCancelled else { return }
            if case .cycle(let next) = event.action { cycle(next) } else { run(event.action) }
        }
    }
    private enum Phase { case form, done, turning }
    private enum EventAction { case tap, type(String), switchTokens, press, exchange, leave, cycle(Double) }
    private struct Event { let time: Double; let sequence: Int; let action: EventAction }
    private struct LoopKey: Equatable { let on: Bool; let touches: Int; let amount: String }
    private struct PlainStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View { configuration.label }
    }
    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.6 : 1) }
    }
    private struct HoldStyle: ButtonStyle {
        let hold: (Bool) -> Void
        func makeBody(configuration: Configuration) -> some View { configuration.label.onChange(of: configuration.isPressed) { _, next in hold(next) } }
    }
    private struct SwitchStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            ZStack {
                Circle().fill(P.panel)
                configuration.label.scaleEffect(configuration.isPressed ? 0.9 : 1)
                    .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.12), value: configuration.isPressed)
            }.frame(width: S.switchSize + 10, height: S.switchSize + 10).contentShape(Rectangle().inset(by: -8))
        }
    }
}

private struct SwapSuccessCardRow: View {
    let token: SwapSuccessCardToken
    let width: Double
    let slot: Double
    let front: Bool
    let editable: Bool
    let amount: String
    let caret: Bool
    let blink: Bool
    let focusRequest: Int
    let balanceLabel: String
    let onChange: (String) -> Void
    let onFocus: () -> Void
    let onBlur: () -> Void
    let onPick: () -> Void
    @State private var pressed = false
    private typealias S = SwapSuccessCardMetrics
    private typealias M = SwapSuccessCardMotion
    private typealias P = SwapSuccessCardPalette
    private typealias N = SwapSuccessCardNumbers
    var body: some View {
        let mid = sin(.pi * M.clamp(slot))
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .leading) {
                HStack(spacing: S.caret.gap) {
                    Text(N.grouped(amount)).font(.system(size: S.amountSize, weight: .light)).monospacedDigit().tracking(-0.3).foregroundStyle(P.ink)
                        .lineLimit(1).minimumScaleFactor(0.6).frame(height: S.amountHeight)
                    if caret {
                        RoundedRectangle(cornerRadius: 1, style: .circular).fill(P.mint).frame(width: S.caret.width, height: S.caret.height).opacity(blink ? 1 : 0)
                    }
                }.accessibilityHidden(editable).allowsHitTesting(false)
                if editable {
                    SwapSuccessCardAmountInput(value: amount, focusRequest: focusRequest, label: token.symbol + " amount", onChange: onChange, onFocus: onFocus, onBlur: onBlur)
                }
            }
            .frame(width: max(0, width - S.textLeft - S.amountRight), height: S.amountSlot, alignment: .leading).offset(x: S.textLeft)
            HStack {
                Spacer()
                Button(action: onPick) {
                    HStack(spacing: 7) {
                        Circle().fill(token.disc).frame(width: 15, height: 15)
                            .overlay(Circle().fill(token.dot).frame(width: 5.5, height: 5.5)).opacity(pressed ? 0.6 : 1)
                        Text(token.symbol).font(.system(size: S.symbolSize, weight: .medium)).foregroundStyle(P.ink).lineLimit(1)
                            .frame(height: S.symbolHeight).opacity(pressed ? 0.6 : 1)
                        Image(systemName: "chevron.down").font(.system(size: 11)).foregroundStyle(P.mute)
                    }
                    .padding(.leading, 9).padding(.trailing, 10).frame(height: S.chipHeight)
                    .background(RoundedRectangle(cornerRadius: S.chipRadius, style: .continuous).fill(Color(hex: 0x171817))
                        .shadow(color: .black.opacity(0.5), radius: 3, x: 0, y: 2))
                    .overlay(RoundedRectangle(cornerRadius: S.chipRadius, style: .continuous).strokeBorder(.white.opacity(0.07), lineWidth: 1))
                }
                .buttonStyle(PressStyle { pressed = $0 }).accessibilityLabel(token.symbol + ", choose a token")
            }.frame(width: width - S.textLeft).offset(y: 7)
            Text(balanceLabel + " : " + N.grouped(N.fixed(token.balance, places: 2)))
                .font(.system(size: S.balanceSize)).monospacedDigit().foregroundStyle(P.mute).lineLimit(1)
                .frame(width: width - S.textLeft, height: S.balanceHeight, alignment: .leading).offset(x: S.textLeft, y: 45)
        }
        .frame(width: width, height: S.rowHeight, alignment: .topLeading)
        .opacity(front ? 1 : 1 - M.cross.dim * mid)
        .scaleEffect(1 + (front ? M.cross.grow : -M.cross.shrink) * mid)
        .offset(y: S.slot * slot)
    }
    private struct PressStyle: ButtonStyle {
        let pressed: (Bool) -> Void
        func makeBody(configuration: Configuration) -> some View { configuration.label.onChange(of: configuration.isPressed) { _, next in pressed(next) } }
    }
}

private final class SwapSuccessCardTextField: UITextField {
    override func caretRect(for position: UITextPosition) -> CGRect { .zero }
    override func selectionRects(for range: UITextRange) -> [UITextSelectionRect] { [] }
}

/// The field receives manual decimal-pad edits while its amount and blinking caret are drawn above it.
private struct SwapSuccessCardAmountInput: UIViewRepresentable {
    let value: String
    let focusRequest: Int
    let label: String
    let onChange: (String) -> Void
    let onFocus: () -> Void
    let onBlur: () -> Void
    func makeCoordinator() -> SwapSuccessCardInputCoordinator { SwapSuccessCardInputCoordinator(self) }
    func makeUIView(context: Context) -> SwapSuccessCardTextField {
        let field = SwapSuccessCardTextField()
        field.delegate = context.coordinator; field.textColor = .clear; field.tintColor = .clear
        field.font = .systemFont(ofSize: SwapSuccessCardMetrics.amountSize)
        field.keyboardType = .decimalPad; field.keyboardAppearance = .dark
        field.borderStyle = .none; field.backgroundColor = .clear
        field.addTarget(context.coordinator, action: #selector(SwapSuccessCardInputCoordinator.edited(_:)), for: .editingChanged)
        return field
    }
    func updateUIView(_ field: SwapSuccessCardTextField, context: Context) {
        let state = context.coordinator; state.parent = self
        if field.text != value { field.text = value }
        field.accessibilityLabel = label
        if state.focusRequest != focusRequest {
            state.focusRequest = focusRequest
            DispatchQueue.main.async { if !field.isFirstResponder { field.becomeFirstResponder() } }
        }
    }
    static func dismantleUIView(_ field: SwapSuccessCardTextField, coordinator: SwapSuccessCardInputCoordinator) { field.resignFirstResponder() }
    final class SwapSuccessCardInputCoordinator: NSObject, UITextFieldDelegate {
        var parent: SwapSuccessCardAmountInput
        var focusRequest: Int
        init(_ parent: SwapSuccessCardAmountInput) { self.parent = parent; focusRequest = parent.focusRequest }
        @objc func edited(_ field: UITextField) { parent.onChange(field.text ?? "") }
        func textFieldDidBeginEditing(_ textField: UITextField) { parent.onFocus() }
        func textFieldDidEndEditing(_ textField: UITextField) { parent.onBlur() }
        func textFieldShouldReturn(_ textField: UITextField) -> Bool { textField.resignFirstResponder(); return true }
        func textField(_ field: UITextField, shouldChangeCharactersIn range: NSRange, replacementString text: String) -> Bool {
            let old = (field.text ?? "") as NSString
            let room = SwapSuccessCardMetrics.maxLength - (old.length - range.length)
            guard text.utf16.count > room else { return true }
            guard room > 0 else { return false }
            var units = Array(text.utf16.prefix(room))
            if let last = units.last, (0xd800...0xdbff).contains(last) { units.removeLast() }
            field.text = old.replacingCharacters(in: range, with: String(decoding: units, as: UTF16.self))
            edited(field); return false
        }
    }
}

#Preview { SwapSuccessCard(autoPlay: true) }
