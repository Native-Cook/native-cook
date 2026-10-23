import SwiftUI

struct SplitBillClaimItem: Equatable {
    var name: String
    var price: Double
    static let samples = [
        Self(name: "Sparkling Water - 0,5 l", price: 1.5),
        Self(name: "Ginger Lemonade - 0,5 l", price: 3.25),
        Self(name: "Pizza - Margherita", price: 21),
    ]
}

private enum SplitBillClaimPalette {
    static let sheet = Color(hex: 0xf3f5f8)
    static let ink = Color(hex: 0x1f2a3d)
    static let mute = Color(hex: 0x7c8798)
    static let grip = Color(hex: 0xc3cad6)
    static let tint = Color(hex: 0xedf3ff)
    static let accent = Color(hex: 0x1e6bf2)
    static let touch = Color(hex: 0x8a94a6)
}

private enum SplitBillClaimMetrics {
    static let margin = 22.0
    static let card = 96.0
    static let cardGap = 8.0
    static let cardRadius = 6.0
    static let gripX = 30.0
    static let nameX = 54.0
    static let priceInset = 26.0
    static let avatar = 46.0
    static let avatarSmall = 22.0
    static let badge = 18.0
    static let seatX = nameX + avatarSmall / 2
    static let seatY = 15.0
    static let nameLift = 12.0
    static let namePush = 22.0
    static let touch = 50.0
    static let touchX = 88.0
    static let panel = 60.0
    static let pay = 58.0
    static let barGap = 6.0
    static let headerBar = 40.0
    static let backSize = 17.0
    static let backHeight = 22.0
    static let titleSize = 24.0
    static let titleHeight = 30.0
    static let hintSize = 14.0
    static let hintHeight = 19.0
    static let hintLeading = hintHeight - UIFont.systemFont(ofSize: hintSize).lineHeight
    static let total = 74.0
    static let totalSize = 15.0
    static let totalHeight = 20.0
    static let itemSize = 15.5
    static let itemHeight = 20.0
    static let countSize = 15.0
    static let countHeight = 20.0
    static let shareSize = 20.0
    static let shareHeight = 25.0
    static let paySize = 16.0
    static let payHeight = 21.0
    static let barHeight = panel + barGap + pay
}

private enum SplitBillClaimMotion {
    static let loop = 9.8
    static let grabs = [0.8, 2.9, 5.3, 7.0]
    static let grabFor = 0.15
    static let pullFor = 0.6
    static let holdFor = 0.35
    static let pullTo = 98.0
    static let userHold = 3.0
    static let resetFor = 0.9
    static let pullMax = 104.0
    static let claimAt = 68.0
    static let back = (mass: 1.0, stiffness: 260.0, damping: 22.0)
    static let hop = 0.46
    static let hopHeight = 20.0
    static let bar = (mass: 1.0, stiffness: 190.0, damping: 20.0)
    static let barAfter = 0.32
    static let barOut = 0.26
    static let count = 0.52
    static let rise = 0.42
    static let riseAfter = 0.18
    static let riseStagger = 0.09
    static let riseDistance = 25.0
    static let touchIn = 0.14
    static let touchOut = 0.16
    static let payPress = 0.11
    static let frameLimit = 0.064
    static let energyThreshold = 6e-9
    static let gamma = 2.2
    static let pullCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.3, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))
    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ value: Double, from: Double, to: Double) -> Double {
        let u = clamp((value - from) / (to - from))
        return u * u * (3 - 2 * u)
    }
    static func money(_ cents: Double, symbol: String) -> String {
        let remainder = number(cents.truncatingRemainder(dividingBy: 100))
        return symbol + number(floor(cents / 100)) + "." + String(repeating: "0", count: max(0, 2 - remainder.count)) + remainder
    }
    private static func number(_ value: Double) -> String {
        if value.isNaN { return "NaN" }
        if value == .infinity { return "Infinity" }
        if value == -.infinity { return "-Infinity" }
        if value == 0 { return "0" }
        let magnitude = abs(value)
        let raw = String(magnitude).lowercased().split(separator: "e")
        let parts = String(raw[0]).split(separator: ".", omittingEmptySubsequences: false)
        var digits = parts.joined()
        var point = parts[0].count + (raw.count > 1 ? Int(raw[1]) ?? 0 : 0)
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
}

private enum SplitBillClaimCurve {
    case quad, linear, outCubic, pull
    func value(_ t: Double) -> Double {
        let u = SplitBillClaimMotion.clamp(t)
        switch self {
        case .quad: return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
        case .linear: return u
        case .outCubic: return 1 - pow(1 - u, 3)
        case .pull: return SplitBillClaimMotion.pullCurve.value(at: u)
        }
    }
}

private struct SplitBillClaimScalar {
    private(set) var value: Double
    private var target: Double
    private var from: Double
    private var velocity = 0.0
    private var energy = 0.0
    private var last = Date.distantPast
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve = SplitBillClaimCurve.quad
    private var constants: (mass: Double, stiffness: Double, damping: Double)? = nil
    private var active = false
    init(_ value: Double) { self.value = value; target = value; from = value }
    var finished: Bool { !active }
    mutating func jump(_ next: Double) { value = next; target = next; velocity = 0; constants = nil; active = false }
    mutating func time(to next: Double, duration: Double, curve: SplitBillClaimCurve = .quad, at date: Date = .now, still: Bool = false) {
        if still { jump(next); return }
        advance(at: date)
        if value == next { jump(next); return }
        let continuing = active && constants == nil && target == next
        if !continuing { from = value; start = date }
        target = next; self.duration = duration; self.curve = curve; constants = nil; velocity = 0; active = true
    }
    mutating func spring(to next: Double, constants: (mass: Double, stiffness: Double, damping: Double), at date: Date = .now, still: Bool = false) {
        if still { jump(next); return }
        if self.constants != nil, target == next { return }
        let continuing = self.constants != nil && active
        advance(at: date)
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next; self.constants = constants
        energy = 0.5 * constants.stiffness * pow(value - target, 2)
        if !continuing { last = date }
        active = true
    }
    mutating func advance(at date: Date) {
        guard active else { return }
        guard let constants else {
            let t = SplitBillClaimMotion.clamp(date.timeIntervalSince(start) / duration)
            value = from + (target - from) * curve.value(t)
            if t == 1 { active = false }
            return
        }
        let dt = min(SplitBillClaimMotion.frameLimit, max(0, date.timeIntervalSince(last))); last = date
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
        if energy == 0 || current / energy <= SplitBillClaimMotion.energyThreshold { value = target; velocity = 0; active = false }
    }
}

private enum SplitBillClaimDrawing {
    static let hair: Path = {
        var path = Path(); path.move(to: CGPoint(x: 14.6, y: 21))
        path.addCurve(to: CGPoint(x: 24.2, y: 8), control1: CGPoint(x: 13.4, y: 12.6), control2: CGPoint(x: 17.6, y: 8))
        path.addCurve(to: CGPoint(x: 33.4, y: 20.6), control1: CGPoint(x: 31, y: 8), control2: CGPoint(x: 34.6, y: 12.4))
        path.addCurve(to: CGPoint(x: 27.4, y: 15), control1: CGPoint(x: 32.4, y: 17.6), control2: CGPoint(x: 30.4, y: 15.6))
        path.addCurve(to: CGPoint(x: 15.8, y: 15.6), control1: CGPoint(x: 23.6, y: 16.8), control2: CGPoint(x: 18.6, y: 16.6))
        path.addCurve(to: CGPoint(x: 14.6, y: 21), control1: CGPoint(x: 15.2, y: 17.2), control2: CGPoint(x: 15, y: 19))
        path.closeSubpath(); return path
    }()
    static func avatar(size: Double, in context: GraphicsContext) {
        var face = context; face.scaleBy(x: size / 48, y: size / 48)
        face.clip(to: Path(ellipseIn: CGRect(x: 0, y: 0, width: 48, height: 48)))
        face.fill(Path(CGRect(x: 0, y: 0, width: 48, height: 48)), with: .color(Color(hex: 0xdfe7f5)))
        face.fill(Path(ellipseIn: CGRect(x: 5, y: 36, width: 38, height: 26)), with: .color(Color(hex: 0x27364f)))
        face.fill(Path(roundedRect: CGRect(x: 20, y: 28, width: 8, height: 10), cornerRadius: 3), with: .color(Color(hex: 0xe7b38f)))
        face.fill(Path(ellipseIn: CGRect(x: 15.4, y: 11.5, width: 17.2, height: 20)), with: .color(Color(hex: 0xf2c5a3)))
        face.fill(hair, with: .color(Color(hex: 0x232733)))
    }
    static func color(_ t: Double, from: Color, to: Color) -> Color {
        let u = SplitBillClaimMotion.clamp(t); let gamma = SplitBillClaimMotion.gamma
        func channels(_ color: Color) -> [Double] {
            var r = CGFloat(0); var g = CGFloat(0); var b = CGFloat(0); var a = CGFloat(0)
            UIColor(color).getRed(&r, green: &g, blue: &b, alpha: &a)
            return [Double(r), Double(g), Double(b)]
        }
        let values = zip(channels(from), channels(to)).map { floor(pow(pow($0, gamma) * (1 - u) + pow($1, gamma) * u, 1 / gamma) * 255 + 0.5) }
        return Color.rgba(values[0], values[1], values[2], 1)
    }
}

/// Row state survives the parent's counting frames, so a held gesture keeps the same owner.
private final class SplitBillClaimRowState: ObservableObject {
    var x = SplitBillClaimScalar(0)
    var from = SplitBillClaimMotion.pullTo
    var mine: SplitBillClaimScalar
    var touch = SplitBillClaimScalar(0)
    var armed = false
    init(claimed: Bool) { mine = SplitBillClaimScalar(claimed ? 1 : 0) }
    func setClaimed(_ claimed: Bool, still: Bool) {
        objectWillChange.send(); mine.time(to: claimed ? 1 : 0, duration: SplitBillClaimMotion.hop, curve: .linear, still: still)
    }
    func grab() { objectWillChange.send(); touch.time(to: 1, duration: SplitBillClaimMotion.touchIn) }
    func pull(_ to: Double, seconds: Double) {
        objectWillChange.send(); x.time(to: to, duration: seconds, curve: .pull)
    }
    func settle(still: Bool) {
        objectWillChange.send()
        touch.time(to: 0, duration: SplitBillClaimMotion.touchOut, still: still)
        x.spring(to: 0, constants: SplitBillClaimMotion.back, still: still)
    }
    func letGo(still: Bool) -> Bool {
        objectWillChange.send()
        let pulled = x.value
        touch.time(to: 0, duration: SplitBillClaimMotion.touchOut)
        x.spring(to: 0, constants: SplitBillClaimMotion.back, still: still)
        guard pulled >= SplitBillClaimMotion.claimAt else { return false }
        from = pulled; return true
    }
    func advance(at date: Date) {
        let before = (x.value, mine.value, touch.value)
        x.advance(at: date); mine.advance(at: date); touch.advance(at: date)
        if before.0 != x.value || before.1 != mine.value || before.2 != touch.value { objectWillChange.send() }
    }
}

private struct SplitBillClaimAvatar: View {
    let size: Double
    var body: some View {
        Canvas { context, _ in SplitBillClaimDrawing.avatar(size: size, in: context) }.frame(width: size, height: size)
    }
}

private struct SplitBillClaimRow: View {
    let index: Int
    let item: SplitBillClaimItem
    let claimed: Bool
    let symbol: String
    let accent: Color
    let width: Double
    let onToggle: (Int, Bool) -> Void
    let onTouch: () -> Void
    let register: (Int, SplitBillClaimRowState?) -> Void
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @StateObject private var state: SplitBillClaimRowState
    @State private var entered = Date.now
    @State private var began = false
    @State private var activated = false
    @State private var activationTranslation = 0.0
    @State private var failed = false
    @GestureState private var panActive = false
    @State private var lightHaptic = UIImpactFeedbackGenerator(style: .light)
    private typealias S = SplitBillClaimMetrics
    private typealias M = SplitBillClaimMotion
    private typealias P = SplitBillClaimPalette
    private typealias D = SplitBillClaimDrawing
    init(index: Int, item: SplitBillClaimItem, claimed: Bool, symbol: String, accent: Color, width: Double,
         onToggle: @escaping (Int, Bool) -> Void, onTouch: @escaping () -> Void,
         register: @escaping (Int, SplitBillClaimRowState?) -> Void) {
        self.index = index; self.item = item; self.claimed = claimed; self.symbol = symbol; self.accent = accent; self.width = width
        self.onToggle = onToggle; self.onTouch = onTouch; self.register = register
        _state = StateObject(wrappedValue: SplitBillClaimRowState(claimed: claimed))
    }
    var body: some View {
        TimelineView(.animation) { timeline in
            let x = state.x.value; let t = state.mine.value
            let shown = M.smooth(x, from: 18, to: M.claimAt)
            let hopping = t > 0.001 && t < 0.999
            let e = M.smooth(t, from: 0, to: 1)
            let startX = state.from / 2 - 4
            let endX = x + S.seatX
            let hopX = startX + (endX - startX) * e - S.avatar / 2
            let hopY = S.seatY * e - M.hopHeight * 6.75 * t * (1 - t) * (1 - t)
            let intro = reduceMotion ? 1 : SplitBillClaimCurve.quad.value((timeline.date.timeIntervalSince(entered) - M.riseAfter - Double(index) * M.riseStagger) / M.rise)
            ZStack(alignment: .topLeading) {
                ZStack(alignment: .bottomTrailing) {
                    SplitBillClaimAvatar(size: S.avatar)
                        .background(Circle().fill(.white).shadow(color: Color.rgba(24, 39, 75, 0.18), radius: 7, x: 0, y: 6))
                    ZStack {
                        Circle().fill(accent)
                        Capsule().fill(.white).frame(width: 8, height: 2)
                        if !claimed { Capsule().fill(.white).frame(width: 2, height: 8) }
                    }
                    .frame(width: S.badge, height: S.badge).overlay(Circle().strokeBorder(.white, lineWidth: 2))
                    .offset(x: 3, y: 1)
                }
                .frame(width: S.avatar, height: S.avatar)
                .opacity(hopping ? 0 : shown)
                .scaleEffect(0.5 + 0.5 * shown + (x >= M.claimAt ? 0.06 : 0))
                .offset(x: x / 2 - 4 - S.avatar / 2, y: (S.card - S.avatar) / 2)
                .accessibilityHidden(true)
                card(x: x, mine: t).offset(x: x)
                SplitBillClaimAvatar(size: S.avatar)
                    .opacity(claimed ? (t > 0.001 ? 1 : 0) : M.smooth(t, from: 0, to: 0.35))
                    .scaleEffect(1 + (S.avatarSmall / S.avatar - 1) * M.smooth(t, from: 0.2, to: 0.8))
                    .offset(x: hopX, y: (S.card - S.avatar) / 2 + hopY)
                    .allowsHitTesting(false).accessibilityHidden(true)
                Circle().fill(P.touch).frame(width: S.touch, height: S.touch)
                    .opacity(state.touch.value * 0.5).scaleEffect(0.6 + 0.4 * state.touch.value)
                    .offset(x: x + S.touchX - S.touch / 2, y: (S.card - S.touch) / 2)
                    .allowsHitTesting(false).accessibilityHidden(true)
            }
            .frame(width: width, height: S.card).opacity(intro).offset(y: M.riseDistance * (1 - intro))
            .onChange(of: timeline.date) { _, date in state.advance(at: date) }
        }
        .frame(height: S.card)
        .onAppear { entered = .now; lightHaptic.prepare(); register(index, state) }
        .onDisappear { register(index, nil) }
        .onChange(of: claimed) { _, next in state.setClaimed(next, still: reduceMotion) }
        .onChange(of: panActive) { _, active in
            if !active { DispatchQueue.main.async { if !panActive { finalize() } } }
        }
    }
    private func card(x: Double, mine: Double) -> some View {
        let lifted = M.smooth(x, from: 0, to: 40)
        let gripOpacity = 1 - M.smooth(mine, from: 0, to: 0.3) + M.smooth(mine, from: 0.6, to: 1)
        let nameX = S.namePush * (M.smooth(mine, from: 0, to: 0.3) - M.smooth(mine, from: 0.6, to: 1))
        let nameY = -S.nameLift * M.smooth(mine, from: 0.25, to: 0.9)
        let cents = floor(item.price * 100 + 0.5)
        return ZStack(alignment: .topLeading) {
            HStack(spacing: 12) {
                Text(item.name).font(.system(size: S.itemSize, weight: .medium)).foregroundStyle(P.ink).lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading).frame(height: S.itemHeight)
                    .offset(x: nameX, y: nameY).padding(.leading, S.nameX)
                Text(M.money(cents, symbol: symbol)).font(.system(size: S.itemSize, weight: .semibold)).monospacedDigit().foregroundStyle(P.ink)
                    .frame(height: S.itemHeight).fixedSize().padding(.trailing, S.priceInset)
            }.frame(height: S.card)
            HStack(spacing: 3) {
                Capsule().frame(width: 2, height: 13)
                Capsule().frame(width: 2, height: 13)
            }
            .foregroundStyle(D.color(mine, from: P.grip, to: accent)).opacity(gripOpacity)
            .offset(x: S.gripX - 4, y: (S.card - 13) / 2)
        }
        .frame(width: width, height: S.card)
        .background(RoundedRectangle(cornerRadius: S.cardRadius, style: .circular)
            .fill(D.color(M.smooth(mine, from: 0.3, to: 1), from: .white, to: P.tint))
            .shadow(color: Color.rgba(24, 39, 75, 0.05 + 0.07 * lifted), radius: (8 + 16 * lifted) / 2, x: 0, y: 2 + 6 * lifted))
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { event in
                if !began { began = true; activated = false; activationTranslation = 0; failed = false; onTouch() }
                if !activated && abs(event.translation.height) > 14 { failed = true }
                if !failed && (activated || event.translation.width > 10) {
                    if !activated { activationTranslation = event.translation.width; activated = true }
                    state.objectWillChange.send()
                    // iOS resets translation when the custom horizontal criterion activates.
                    let pull = max(0, event.translation.width - activationTranslation)
                    state.x.jump(pull < M.pullMax ? pull : M.pullMax + (pull - M.pullMax) / 4)
                    let past = state.x.value >= M.claimAt
                    if past != state.armed { state.armed = past; lightHaptic.impactOccurred() }
                }
            }
            .onEnded { _ in finalize() }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(item.name + ", " + M.money(cents, symbol: symbol) + (claimed ? ", yours" : ""))
        .accessibilityHint(claimed ? "Swipe right to give it back" : "Swipe right to take it")
        .accessibilityAction { onToggle(index, true) }
    }
    private func finalize() {
        guard began else { return }
        began = false; activated = false; activationTranslation = 0; failed = false; state.armed = false
        if state.letGo(still: reduceMotion) { onToggle(index, true) }
    }
}

private final class SplitBillClaimLatest {
    var cents: [Double]
    var still = false
    var onChange: (([Int]) -> Void)?
    init(items: [SplitBillClaimItem], onChange: (([Int]) -> Void)?) {
        cents = items.map { floor($0.price * 100 + 0.5) }; self.onChange = onChange
    }
}

/// A replacing delay carries its preceding wrapper until its own child begins.
private struct SplitBillClaimBar {
    private var resting: Double
    private var animation: SplitBillClaimBarNode? = nil
    init(_ value: Double) { resting = value }
    var value: Double { animation?.value ?? resting }
    mutating func schedule(shown: Bool, delay: Double, at date: Date, still: Bool) {
        if still { resting = shown ? 1 : 0; animation = nil; return }
        animation = SplitBillClaimBarNode(value: value, shown: shown, at: date.addingTimeInterval(delay), previous: animation)
        advance(at: date)
    }
    mutating func advance(at date: Date) {
        guard let animation else { return }
        animation.advance(at: date)
        if animation.finished { resting = animation.value; self.animation = nil }
    }
}

private final class SplitBillClaimBarNode {
    private(set) var value: Double
    private(set) var finished = false
    private let shown: Bool
    private let starts: Date
    private var previous: SplitBillClaimBarNode?
    private var child: SplitBillClaimScalar? = nil
    init(value: Double, shown: Bool, at date: Date, previous: SplitBillClaimBarNode?) {
        self.value = value; self.shown = shown; starts = date; self.previous = previous
    }
    func advance(at date: Date) {
        guard !finished else { return }
        if date >= starts {
            if child == nil {
                // The previous animation is a delay wrapper, so the child starts with zero velocity.
                previous = nil
                var next = SplitBillClaimScalar(value)
                if shown { next.spring(to: 1, constants: SplitBillClaimMotion.bar, at: date) }
                else { next.time(to: 0, duration: SplitBillClaimMotion.barOut, at: date) }
                child = next
            }
            child?.advance(at: date)
            value = child!.value; finished = child!.finished
        } else if let previous {
            previous.advance(at: date)
            value = previous.value
            if previous.finished { self.previous = nil }
        }
    }
}

/// Claimed rows keep their portrait while the share counts and the delayed payment bar follows.
struct SplitBillClaim: View {
    var autoPlay: Bool
    var autoPlayRows: [Int]
    var items: [SplitBillClaimItem]
    var symbol: String
    var title: String
    var hint: String
    var backLabel: String
    var totalLabel: String
    var payLabel: String
    var accent: Color
    var onChange: (([Int]) -> Void)?
    var onPay: ((Double, [Int]) -> Void)?
    var onBack: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var claimed: [Bool]
    @State private var count = 1
    @State private var touches = 0
    @State private var share = SplitBillClaimScalar(0)
    @State private var bar = SplitBillClaimBar(0)
    @State private var rows: [Int: SplitBillClaimRowState] = [:]
    @State private var hintHeight = 19.0
    @State private var latest: SplitBillClaimLatest
    @State private var mediumHaptic = UIImpactFeedbackGenerator(style: .medium)
    private typealias S = SplitBillClaimMetrics
    private typealias M = SplitBillClaimMotion
    private typealias P = SplitBillClaimPalette

    init(
        autoPlay: Bool = false, autoPlayRows: [Int] = [0, 2], items: [SplitBillClaimItem] = SplitBillClaimItem.samples,
        symbol: String = "$", title: String = "Pick your items", hint: String = "Swipe right to assign an item to yourself.",
        backLabel: String = "Back", totalLabel: String = "In total", payLabel: String = "Pay",
        accent: Color = SplitBillClaimPalette.accent,
        onChange: (([Int]) -> Void)? = nil, onPay: ((Double, [Int]) -> Void)? = nil, onBack: (() -> Void)? = nil
    ) {
        self.autoPlay = autoPlay; self.autoPlayRows = autoPlayRows; self.items = items
        self.symbol = symbol; self.title = title; self.hint = hint; self.backLabel = backLabel
        self.totalLabel = totalLabel; self.payLabel = payLabel; self.accent = accent
        self.onChange = onChange; self.onPay = onPay; self.onBack = onBack
        _claimed = State(initialValue: items.map { _ in false })
        _latest = State(initialValue: SplitBillClaimLatest(items: items, onChange: onChange))
    }
    private var cents: [Double] { items.map { floor($0.price * 100 + 0.5) } }
    private var mine: [Int] { claimed.indices.filter { claimed[$0] } }
    private var counted: Double { floor(share.value + 0.5) }
    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let headerHeight = insets.top + 4 + S.headerBar + 18 + S.titleHeight + 6 + hintHeight + 28
            let bottom = max(insets.bottom, 12) + 6
            TimelineView(.animation) { timeline in
                ZStack(alignment: .topLeading) {
                    P.sheet
                    header(top: insets.top)
                        .frame(width: width)
                    VStack(spacing: 0) {
                        HStack {
                            Text(totalLabel).font(.system(size: S.totalSize)).foregroundStyle(P.ink).frame(height: S.totalHeight)
                            Spacer()
                            Text(M.money(cents.reduce(0, +), symbol: symbol)).font(.system(size: S.totalSize, weight: .semibold)).monospacedDigit().foregroundStyle(P.ink)
                                .frame(height: S.totalHeight)
                        }
                        .padding(.horizontal, 4).padding(.top, 12).frame(height: S.total)
                        ForEach(items.indices, id: \.self) { index in
                            SplitBillClaimRow(index: index, item: items[index], claimed: index < claimed.count ? claimed[index] : false,
                                symbol: symbol, accent: accent, width: width - S.margin * 2,
                                onToggle: toggle, onTouch: touched, register: register)
                                .padding(.bottom, S.cardGap)
                        }
                    }
                    .padding(.horizontal, S.margin).frame(width: width, height: max(0, height - headerHeight), alignment: .top)
                    .offset(y: headerHeight)
                    paymentBar
                        .frame(width: width - S.margin * 2, height: S.barHeight)
                        .offset(x: S.margin, y: height - bottom - S.barHeight + (S.barHeight + bottom + 24) * (1 - bar.value))
                        .allowsHitTesting(!mine.isEmpty).accessibilityHidden(mine.isEmpty)
                }
                .frame(width: width, height: height).clipped().offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in refreshLatest(); advance(at: date) }
            }
        }
        .preferredColorScheme(.light)
        .onAppear { mediumHaptic.prepare(); refreshLatest() }
        .task(id: LoopKey(on: autoPlay, order: autoPlayRows, items: items, touches: touches)) { await loop() }
    }
    private func header(top: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button {
                    touched(); onBack?()
                } label: {
                    HStack(spacing: 7) {
                        Canvas { context, _ in
                            var path = Path(); path.move(to: CGPoint(x: 7.5, y: 1.5)); path.addLine(to: CGPoint(x: 1.5, y: 7.5)); path.addLine(to: CGPoint(x: 7.5, y: 13.5))
                            context.stroke(path, with: .color(accent), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                        }.frame(width: 9, height: 15)
                        Text(backLabel).font(.system(size: S.backSize, weight: .medium)).foregroundStyle(accent).frame(height: S.backHeight)
                    }.contentShape(Rectangle().inset(by: -10))
                }.buttonStyle(BackStyle())
                Spacer()
                SplitBillClaimAvatar(size: 30).accessibilityHidden(true)
            }.frame(height: S.headerBar)
            Text(title).font(.system(size: S.titleSize, weight: .bold)).tracking(-0.2).foregroundStyle(P.ink).lineLimit(1)
                .frame(height: S.titleHeight).padding(.top, 18)
            Text(hint).font(.system(size: S.hintSize)).foregroundStyle(P.mute).lineSpacing(S.hintLeading)
                .padding(.vertical, S.hintLeading / 2).lineLimit(2).fixedSize(horizontal: false, vertical: true)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(GeometryReader { measured in
                    Color.clear.onAppear { hintHeight = measured.size.height }
                        .onChange(of: measured.size.height) { _, next in hintHeight = next }
                })
                .padding(.top, 6)
        }
        .padding(.horizontal, S.margin + 2).padding(.top, top + 4).padding(.bottom, 28).background(.white)
    }
    private var paymentBar: some View {
        VStack(spacing: S.barGap) {
            HStack {
                Text(count == 1 ? "1 item" : "\(count) items").font(.system(size: S.countSize)).foregroundStyle(P.ink).frame(height: S.countHeight)
                Spacer()
                Text(M.money(counted, symbol: symbol)).font(.system(size: S.shareSize, weight: .semibold)).monospacedDigit().foregroundStyle(P.ink).frame(height: S.shareHeight)
            }
            .padding(.horizontal, 24).frame(height: S.panel)
            .background(RoundedRectangle(cornerRadius: S.cardRadius, style: .circular).fill(.white)
                .shadow(color: Color.rgba(24, 39, 75, 0.07), radius: 5, x: 0, y: 2))
            Button {
                touched(); mediumHaptic.impactOccurred(); onPay?(counted / 100, mine)
            } label: {
                Text(M.money(counted, symbol: symbol) + " - " + payLabel)
                    .font(.system(size: S.paySize, weight: .semibold)).monospacedDigit().foregroundStyle(.white).lineLimit(1)
                    .frame(height: S.payHeight).frame(maxWidth: .infinity).frame(height: S.pay)
                    .background(RoundedRectangle(cornerRadius: S.cardRadius, style: .circular).fill(accent))
            }.buttonStyle(PayStyle())
        }
    }
    private func refreshLatest() {
        latest.cents = cents; latest.still = reduceMotion; latest.onChange = onChange
    }
    private func apply(_ next: [Bool]) {
        let list = next.indices.filter { next[$0] }
        let sum = list.reduce(0.0) { $0 + ($1 < latest.cents.count ? latest.cents[$1] : .nan) }
        claimed = next
        if !list.isEmpty { count = list.count }
        let now = Date.now
        share.time(to: sum, duration: M.count, curve: .outCubic, at: now)
        let delay = list.isEmpty || bar.value <= 0 ? M.barAfter : 0
        bar.schedule(shown: !list.isEmpty, delay: delay, at: now, still: latest.still)
        latest.onChange?(list)
    }
    private func toggle(_ index: Int, byHand: Bool) {
        if byHand { mediumHaptic.impactOccurred() }
        apply(claimed.enumerated().map { $0.offset == index ? !$0.element : $0.element })
    }
    private func register(_ index: Int, _ state: SplitBillClaimRowState?) { rows[index] = state }
    private func touched() {
        refreshLatest()
        rows.values.forEach { $0.settle(still: latest.still) }
        touches += 1
    }
    private func advance(at date: Date) {
        share.advance(at: date); bar.advance(at: date)
    }
    private func run(_ action: EventAction) {
        switch action {
        case .grab(let index): rows[index]?.grab()
        case .pull(let index): rows[index]?.pull(M.pullTo, seconds: M.pullFor)
        case .release(let index):
            if rows[index]?.letGo(still: latest.still) == true { toggle(index, byHand: false) }
        case .cycle: break
        }
    }
    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            rows.values.forEach { $0.settle(still: latest.still) }
            apply(items.map { _ in false })
            try? await Task.sleep(for: .seconds(M.resetFor))
            guard !Task.isCancelled else { return }
        }
        let order = autoPlayRows + autoPlayRows.reversed()
        let clock = ContinuousClock(); let origin = clock.now
        var events: [Event] = []; var sequence = 0
        func append(_ time: Double, _ action: EventAction) {
            events.append(Event(time: time, sequence: sequence, action: action)); sequence += 1
        }
        func cycle(_ start: Double) {
            for (step, row) in order.enumerated() {
                let at = start + M.grabs[step % M.grabs.count] + Double(step / M.grabs.count) * M.loop
                append(at, .grab(row)); append(at + M.grabFor, .pull(row))
                append(at + M.grabFor + M.pullFor + M.holdFor, .release(row))
            }
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
    private enum EventAction { case grab(Int), pull(Int), release(Int), cycle(Double) }
    private struct Event { let time: Double; let sequence: Int; let action: EventAction }
    private struct LoopKey: Equatable { let on: Bool; let order: [Int]; let items: [SplitBillClaimItem]; let touches: Int }
    private struct BackStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View { configuration.label.opacity(configuration.isPressed ? 0.5 : 1) }
    }
    private struct PayStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            ZStack {
                configuration.label.opacity(configuration.isPressed ? 0.82 : 1).scaleEffect(configuration.isPressed ? 0.985 : 1)
                    .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.payPress), value: configuration.isPressed)
            }.frame(maxWidth: .infinity).frame(height: S.pay).contentShape(Rectangle())
        }
    }
}

#Preview { SplitBillClaim(autoPlay: true) }
