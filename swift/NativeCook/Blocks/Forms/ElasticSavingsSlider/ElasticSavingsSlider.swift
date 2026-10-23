import SwiftUI

struct ElasticSavingsSliderSavingOption {
    var label: String
    /// What it takes off the yearly bill, in units of the currency.
    var saving: Double

    static let samples = [
        Self(label: "Daily savings", saving: 35), Self(label: "LED lighting", saving: 70),
        Self(label: "Thermostat", saving: 53), Self(label: "Smart thermostat", saving: 76),
        Self(label: "Solar panels", saving: 407), Self(label: "Heat pump", saving: 222),
    ]
}

private enum ElasticSavingsSliderPalette {
    static let backdrop = Color(hex: 0x202622)
    static let banner = Color(hex: 0x333e38)
    static let card = Color(hex: 0xfbfcf7)
    static let sage = Color(hex: 0xedf0e7)
    static let sagePressed = Color(hex: 0xe1e6da)
    static let coral = Color(hex: 0xf65a3a)
    static let ink = Color(hex: 0x1d231f)
    static let ring = Color(hex: 0xcbd6ce)
    static let stop = Color(hex: 0x515753)
    static let bannerInk = Color(hex: 0xf2f5f0)
    static let sky = [Color(hex: 0x6d98de), Color(hex: 0xa9c6ee), Color(hex: 0xd6e3f3)]
    static let roof = Color(hex: 0x3a4763)
    static let roofEdge = Color(hex: 0xd4683b)
    static let glass = Color(hex: 0xcbd9ec)
    static let mullions = Color(hex: 0xf4f7fb)
    static let wing = Color(hex: 0xb99f9d)
    static let wingRoof = Color(hex: 0xdd7444)
}

private enum ElasticSavingsSliderMetrics {
    static let side = 18.0
    static let banner = 92.0
    static let picture = 64.0
    static let cardPadding = 25.0
    static let cardRadius = 46.0
    static let panel = 180.0
    static let amount = 86.0
    static let amountStretch = 1.24
    static let chip = 44.0
    static let chipGap = 6.0
    static let check = 22.0
    static let track = 60.0
    static let trackInset = 5.0
    static let thumb = track - trackInset * 2
    static let boltInset = thumb / 2
    static let stopDots = 4
    static let button = 60.0
    static let thinPerPoint = 0.2
    static let thinMost = 9.0
    static let leanPerPoint = 0.9
    static let leanMost = 28.0
    static let give = 14.0
    static let squashY = 0.72
    static let squashX = 1.1
    static let addressSize = 15.0
    static let addressHeight = 19.0
    static let hintSize = 13.5
    static let hintHeight = 17.5
    static let hintLeading = hintHeight - UIFont.systemFont(ofSize: hintSize).lineHeight
    static let fineTuneSize = 14.5
    static let fineTuneTextHeight = 18.0
    static let fineTuneHeight = 36.0
    static let fineTuneInset = 11.0
    static let billLabelSize = 14.5
    static let billLabelHeight = 18.0
    static let savedSize = 15.0
    static let savedTextHeight = 19.0
    static let savedHeight = 31.0
    static let savedMinWidth = 71.0
    static let chipLabelSize = 13.0
    static let chipLabelHeight = 18.0
    static let reportSize = 16.5
    static let reportHeight = 21.0
}

private enum ElasticSavingsSliderMotion {
    static let loop = 7.5
    static let drags = [
        (at: 0.8, to: 1, lasts: 0.35), (at: 1.9, to: 2, lasts: 0.3),
        (at: 2.4, to: 4, lasts: 0.55), (at: 3.8, to: 5, lasts: 0.35),
        (at: 4.45, to: 6, lasts: 0.35), (at: 6.0, to: 0, lasts: 0.55),
    ]
    static let userHold = 3.0
    static let resumeAfter = 1.3
    static let boltSpring = (mass: 1.0, stiffness: 150.0, damping: 13.0)
    static let snapSpring = (mass: 1.0, stiffness: 210.0, damping: 11.0)
    static let checkSpring = (mass: 0.8, stiffness: 260.0, damping: 15.0)
    static let digitSpring = (mass: 0.7, stiffness: 300.0, damping: 9.0)
    static let chipFor = 0.2
    static let checkOut = 0.14
    static let digitFade = 0.16
    static let countFor = 0.36
    static let callFor = 0.14
    static let pressFor = 0.12
    static let springFrameLimit = 0.064
    static let springEnergyThreshold = 6e-9
    static let cssEase = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.25, y: 0.1), endControlPoint: UnitPoint(x: 0.25, y: 1))
    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 1.0 / 3, y: 1), endControlPoint: UnitPoint(x: 2.0 / 3, y: 1))
}

/// Closed-form springs retain velocity when the target changes, including the bolt's continuous
/// chase. Timing uses the same explicit clock so stop changes and drawing cannot drift apart.
private struct ElasticSavingsSliderScalar {
    private var from: Double
    private var to: Double
    private var initialVelocity = 0.0
    private var start = Date.distantPast
    private var duration = 0.0
    private var mass = 1.0
    private var stiffness = 1.0
    private var damping = 1.0
    private var springElapsed = 0.0
    private var lastFrame = Date.distantPast
    private var mode = Mode.fixed
    private enum Mode { case fixed, spring, quadraticInOut, quadraticOut }

    init(_ value: Double) { from = value; to = value }

    func value(at date: Date) -> Double { sample(at: date).position }

    private func sample(at date: Date) -> (position: Double, velocity: Double) {
        let t = max(0, date.timeIntervalSince(start))
        switch mode {
        case .fixed: return (to, 0)
        case .quadraticInOut, .quadraticOut:
            let u = duration > 0 ? min(1, t / duration) : 1
            let eased: Double
            let speed: Double
            if mode == .quadraticOut {
                eased = 1 - (1 - u) * (1 - u)
                speed = 2 * (1 - u)
            } else {
                eased = u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
                speed = u < 0.5 ? 4 * u : 4 * (1 - u)
            }
            return (from + (to - from) * eased, duration > 0 ? (to - from) * speed / duration : 0)
        case .spring:
            let springTime = springElapsed + min(ElasticSavingsSliderMotion.springFrameLimit, max(0, date.timeIntervalSince(lastFrame)))
            let omega = sqrt(stiffness / mass)
            let decay = damping / (2 * mass)
            let damped = sqrt(omega * omega - decay * decay)
            let a = from - to
            let b = (initialVelocity + decay * a) / damped
            let wave = a * cos(damped * springTime) + b * sin(damped * springTime)
            let attenuation = exp(-decay * springTime)
            let displacement = attenuation * wave
            let velocity = attenuation * (-decay * wave + damped * (-a * sin(damped * springTime) + b * cos(damped * springTime)))
            // Reanimated measures initial energy using the configured velocity, which is zero.
            let initialEnergy = 0.5 * stiffness * a * a
            let energy = 0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
            if initialEnergy == 0 || energy / initialEnergy <= ElasticSavingsSliderMotion.springEnergyThreshold {
                return (to, 0)
            }
            return (to + displacement, velocity)
        }
    }

    mutating func advance(at date: Date) {
        guard mode == .spring else { return }
        springElapsed += min(ElasticSavingsSliderMotion.springFrameLimit, max(0, date.timeIntervalSince(lastFrame)))
        lastFrame = date
        let current = sample(at: date)
        if current.position == to && current.velocity == 0 { mode = .fixed }
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        initialVelocity = 0
        mode = .fixed
    }

    mutating func time(to target: Double, at date: Date = .now, duration: Double, out: Bool = false) {
        from = value(at: date)
        to = target
        initialVelocity = 0
        start = date
        self.duration = duration
        mode = out ? .quadraticOut : .quadraticInOut
    }

    mutating func spring(to target: Double, at date: Date = .now, constants: (mass: Double, stiffness: Double, damping: Double)) {
        if mode == .spring, to == target, mass == constants.mass, stiffness == constants.stiffness, damping == constants.damping {
            advance(at: date)
            return
        }
        let continuing = mode == .spring
        let previousFrame = lastFrame
        let current = sample(at: continuing ? previousFrame : date)
        from = current.position
        initialVelocity = continuing ? current.velocity : 0
        if initialVelocity * (target - current.position) < 0 { initialVelocity = 0 }
        to = target
        start = date
        lastFrame = continuing ? previousFrame : date
        springElapsed = 0
        mass = constants.mass
        stiffness = constants.stiffness
        damping = constants.damping
        mode = .spring
        // A new target uses the preceding frame's state, then advances toward the new target.
        advance(at: date)
    }
}

/// Six savings, a giant bill, and a fat elastic slider. The fill follows the finger while its
/// bolt trails on a spring; the stops switch options and the amounts respond through one path.
struct ElasticSavingsSlider: View {
    var bill: Double
    var symbol: String
    var options: [ElasticSavingsSliderSavingOption]
    var address: String
    var hint: String
    var fineTuneLabel: String
    var billLabel: String
    var reportLabel: String
    var autoPlay: Bool
    var onChange: (([Bool], Double) -> Void)?
    var onFineTune: (() -> Void)?
    var onReport: (([Bool], Double) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var on: [Bool]
    @State private var saved = Tween(0)
    @State private var trackWidth = 316.0
    @State private var touches = 0
    @State private var reach = ElasticSavingsSliderScalar(ElasticSavingsSliderMetrics.thumb)
    @State private var bolt = ElasticSavingsSliderScalar(ElasticSavingsSliderMetrics.thumb)
    @State private var driving = false
    @State private var byHand = false
    @State private var grip = 0.0
    @State private var dragging = false
    @State private var touchingScreen = false
    @State private var lastNearest = 0
    @State private var initiallyReported = false
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    private typealias S = ElasticSavingsSliderMetrics
    private typealias M = ElasticSavingsSliderMotion
    private typealias P = ElasticSavingsSliderPalette

    init(
        bill: Double = 1326,
        symbol: String = "$",
        options: [ElasticSavingsSliderSavingOption] = ElasticSavingsSliderSavingOption.samples,
        address: String = "13 Alondra Blvd",
        hint: String = "Fine-tune your home\nfor more accurate results",
        fineTuneLabel: String = "Fine-tune", billLabel: String = "Estimated Annual Bill",
        reportLabel: String = "See your full report", autoPlay: Bool = false,
        onChange: (([Bool], Double) -> Void)? = nil,
        onFineTune: (() -> Void)? = nil,
        onReport: (([Bool], Double) -> Void)? = nil
    ) {
        self.bill = bill
        self.symbol = symbol
        self.options = options
        self.address = address
        self.hint = hint
        self.fineTuneLabel = fineTuneLabel
        self.billLabel = billLabel
        self.reportLabel = reportLabel
        self.autoPlay = autoPlay
        self.onChange = onChange
        self.onFineTune = onFineTune
        self.onReport = onReport
        _on = State(initialValue: options.map { _ in false })
    }

    private var saving: Double { options.indices.reduce(0) { $0 + ($1 < on.count && on[$1] ? options[$1].saving : 0) } }
    private var left: Double { max(0, bill - saving) }
    private var room: Double { trackWidth - S.trackInset * 2 }
    private var step: Double { (room - S.thumb) / Double(max(1, options.count)) }
    private var rows: [[Int]] {
        stride(from: 0, to: options.count, by: 2).map { Array($0..<min($0 + 2, options.count)) }.reversed()
    }

    var body: some View {
        GeometryReader { proxy in
            let screenWidth = proxy.size.width + proxy.safeAreaInsets.leading + proxy.safeAreaInsets.trailing
            let width = screenWidth - S.side * 2
            let track = width - S.cardPadding * 2
            let bottom = max(proxy.safeAreaInsets.bottom, 12) - proxy.safeAreaInsets.bottom
            TimelineView(.animation) { timeline in
                VStack(spacing: 8) {
                    banner
                    card(width: track, date: timeline.date)
                }
                .frame(width: width)
                .frame(width: screenWidth, height: max(0, proxy.size.height - bottom))
                .padding(.bottom, bottom)
                .offset(x: -proxy.safeAreaInsets.leading)
                .background(P.backdrop.ignoresSafeArea())
                .contentShape(Rectangle())
                .simultaneousGesture(DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !touchingScreen {
                            touchingScreen = true
                            touched()
                        }
                    }
                    .onEnded { _ in touchingScreen = false }
                )
                .onChange(of: timeline.date) { _, date in advance(at: date) }
            }
            .onAppear { trackWidth = track }
            .onChange(of: track) { _, next in trackWidth = next }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            tapHaptic.prepare()
            if !initiallyReported {
                initiallyReported = true
                onChange?(on, left)
            }
        }
        .onChange(of: saving) { _, next in saved.retarget(next, duration: M.countFor, curve: M.cubicOut) }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private var banner: some View {
        HStack(spacing: 0) {
            ElasticSavingsSliderPicture()
                .frame(width: S.picture, height: S.picture)
            VStack(alignment: .leading, spacing: 0) {
                Text(address)
                    .font(.system(size: S.addressSize, weight: .heavy))
                    .tracking(-0.4)
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(height: S.addressHeight)
                Text(hint)
                    .font(.system(size: S.hintSize))
                    .tracking(-0.35)
                    .foregroundStyle(P.bannerInk)
                    .lineSpacing(S.hintLeading)
                    .padding(.vertical, S.hintLeading / 2)
                    .lineLimit(2)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, 14)
            .padding(.trailing, 4)
            Button { onFineTune?() } label: {
                Text(fineTuneLabel)
                    .font(.system(size: S.fineTuneSize, weight: .medium))
                    .foregroundStyle(P.bannerInk)
                    .frame(height: S.fineTuneTextHeight)
                    .padding(.horizontal, S.fineTuneInset)
                    .frame(height: S.fineTuneHeight)
                    .overlay(Capsule().strokeBorder(P.bannerInk, lineWidth: 1.5))
            }
            .buttonStyle(FineTuneStyle())
        }
        .padding(.leading, 13)
        .padding(.trailing, 19)
        .frame(height: S.banner)
        .background(RoundedRectangle(cornerRadius: S.banner / 2, style: .continuous).fill(P.banner))
    }

    private func card(width: Double, date: Date) -> some View {
        VStack(spacing: 0) {
            panel(width: width, date: date)
            VStack(spacing: S.chipGap) {
                ForEach(rows, id: \.first) { row in
                    HStack(spacing: S.chipGap + 1) {
                        ForEach(row, id: \.self) { index in
                            ElasticSavingsSliderChip(label: options[index].label, on: index < on.count && on[index], date: date) { toggle(index) }
                                .frame(maxWidth: .infinity)
                        }
                    }
                }
            }
            .padding(.top, 10)
            slider(date: date)
                .padding(.top, 27)
            Button { onReport?(on, left) } label: {
                Text(reportLabel)
                    .font(.system(size: S.reportSize, weight: .bold))
                    .tracking(-0.3)
                    .foregroundStyle(P.ink)
                    .frame(height: S.reportHeight)
                    .frame(maxWidth: .infinity)
                    .frame(height: S.button)
            }
            .buttonStyle(ReportStyle())
            .padding(.top, 9)
        }
        .padding(S.cardPadding)
        .background(RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).fill(P.card))
    }

    private func panel(width: Double, date: Date) -> some View {
        let amount = Self.number(left)
        let digits = amount.enumerated().map { ElasticSavingsSliderDigitSlot(id: amount.count - $0.offset, char: String($0.element)) }
        return ZStack(alignment: .topLeading) {
            P.sage
            Text(billLabel)
                .font(.system(size: S.billLabelSize, weight: .medium))
                .tracking(-0.2)
                .foregroundStyle(P.ink)
                .lineLimit(1)
                .frame(height: S.billLabelHeight)
                .offset(x: 21, y: 22)
            HStack(spacing: 6) {
                Text(symbol + Self.number(floor(saved.value(at: date) + 0.5)))
                    .font(.system(size: S.savedSize, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(P.ink)
                    .frame(height: S.savedTextHeight)
                ElasticSavingsSliderTriangle()
                    .fill(P.ink)
                    .frame(width: 11, height: 10)
            }
            .padding(.horizontal, 10)
            .frame(minWidth: S.savedMinWidth, alignment: .trailing)
            .frame(height: S.savedHeight)
            .background(RoundedRectangle(cornerRadius: 5, style: .circular).fill(P.card))
            .padding(.top, 16)
            .padding(.trailing, 16)
            .frame(maxWidth: .infinity, alignment: .trailing)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Saving " + symbol + Self.number(saving))
            HStack(alignment: .bottom, spacing: 0) {
                ElasticSavingsSliderDigit(char: symbol, still: reduceMotion, date: date)
                ForEach(digits) { digit in
                    ElasticSavingsSliderDigit(char: digit.char, still: reduceMotion, date: date)
                }
            }
            .frame(width: width, height: S.amount, alignment: .bottom)
            .scaleEffect(x: 1, y: S.amountStretch, anchor: .bottom)
            .offset(y: S.panel - 15 - S.amount)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(billLabel + " " + symbol + amount)
        }
        .frame(height: S.panel)
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .continuous))
    }

    private func slider(date: Date) -> some View {
        let end = reach.value(at: date)
        let following = reduceMotion ? end : bolt.value(at: date)
        let lag = end - following
        let height = S.thumb - min(S.thinMost, abs(lag) * S.thinPerPoint)
        let lean = max(-S.leanMost, min(S.leanMost, lag * S.leanPerPoint))
        let level = on.filter { $0 }.count
        return ZStack(alignment: .topLeading) {
            Capsule().fill(P.backdrop)
            ForEach(0..<S.stopDots, id: \.self) { index in
                Circle()
                    .fill(P.stop)
                    .frame(width: 7, height: 7)
                    .offset(x: S.trackInset + Double(index + 1) * room / Double(S.stopDots + 1) - 3.5, y: S.track / 2 - 3.5)
            }
            Capsule()
                .fill(P.coral)
                .frame(width: max(height, end), height: height)
                .offset(x: S.trackInset, y: (S.track - height) / 2)
            ElasticSavingsSliderBolt()
                .fill(.white)
                .overlay(ElasticSavingsSliderBolt().stroke(.white, style: StrokeStyle(lineWidth: 1.6, lineJoin: .round)))
                .frame(width: 18, height: 22)
                .scaleEffect(1.12)
                .rotationEffect(.degrees(-lean))
                .scaleEffect(1 + abs(lean) / 160)
                .offset(x: S.trackInset - 9 + max(S.thumb, min(following, end)) - S.boltInset, y: S.track / 2 - 11)
        }
        .frame(height: S.track)
        .contentShape(Rectangle())
        .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { event in
                if !dragging {
                    begin(at: event.startLocation.x)
                } else {
                    drag(at: event.location.x)
                }
            }
            .onEnded { _ in finish() }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Options switched on")
        .accessibilityValue(String(level) + " of " + String(options.count))
        .accessibilityAdjustableAction { direction in
            touched()
            let target = min(options.count, max(0, level + (direction == .increment ? 1 : -1)))
            dragTo(target, seconds: 0.25)
        }
    }

    private func advance(at date: Date) {
        reach.advance(at: date)
        let end = reach.value(at: date)
        if reduceMotion { bolt.set(end) }
        else { bolt.spring(to: end, at: date, constants: M.boltSpring) }
        let nearest = min(options.count, max(0, Int(((end - S.thumb) / step).rounded())))
        if nearest != lastNearest {
            lastNearest = nearest
            if driving { arrive(nearest, hand: byHand) }
        }
    }

    private func update(_ next: [Bool]) {
        on = next
        let sum = saving
        onChange?(next, max(0, bill - sum))
    }

    private func arrive(_ level: Int, hand: Bool) {
        if hand { tapHaptic.impactOccurred() }
        update(options.indices.map { $0 < level })
    }

    private func toggle(_ index: Int) {
        touched()
        tapHaptic.impactOccurred()
        var next = on
        if next.indices.contains(index) { next[index].toggle() }
        update(next)
        driving = false
        reach.spring(to: S.thumb + Double(next.filter { $0 }.count) * step, constants: M.snapSpring)
    }

    private func begin(at x: Double) {
        dragging = true
        reach.set(reach.value(at: .now))
        driving = true
        byHand = true
        let end = S.trackInset + reach.value(at: .now)
        let onEnd = x > end - S.thumb - 8 && x < end + 16
        grip = onEnd ? end - x : S.boltInset
        if !onEnd {
            reach.time(to: min(room, max(S.thumb, x - S.trackInset + S.boltInset)), duration: M.callFor, out: true)
        }
        touched()
    }

    private func drag(at x: Double) {
        let to = x - S.trackInset + grip
        let over = to < S.thumb ? to - S.thumb : to > room ? to - room : 0
        reach.set(to - over + S.give * over / (S.give + abs(over)))
    }

    private func finish() {
        let level = min(options.count, max(0, Int(((reach.value(at: .now) - S.thumb) / step).rounded())))
        reach.spring(to: S.thumb + Double(level) * step, constants: M.snapSpring)
        dragging = false
        touched()
    }

    private func dragTo(_ level: Int, seconds: Double) {
        driving = true
        byHand = false
        reach.time(to: S.thumb + Double(level) * step, duration: seconds)
    }

    private func touched() { touches += 1 }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            dragTo(0, seconds: 0.5)
            try? await Task.sleep(for: .seconds(M.resumeAfter))
            guard !Task.isCancelled else { return }
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for drag in M.drags {
                try? await clock.sleep(until: origin + .seconds(drag.at))
                guard !Task.isCancelled else { return }
                dragTo(drag.to, seconds: drag.lasts)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    private static func number(_ value: Double) -> String {
        if value.isNaN { return "NaN" }
        if value == .infinity { return "Infinity" }
        if value == -.infinity { return "-Infinity" }
        if value == 0 { return "0" }
        let magnitude = abs(value)
        let raw = String(magnitude).lowercased().split(separator: "e")
        let mantissa = String(raw[0])
        let exponent = raw.count > 1 ? Int(raw[1]) ?? 0 : 0
        let parts = mantissa.split(separator: ".", omittingEmptySubsequences: false)
        var digits = parts.joined()
        var point = parts[0].count + exponent
        while digits.first == "0" {
            digits.removeFirst()
            point -= 1
        }
        while digits.last == "0" && digits.count > 1 { digits.removeLast() }
        let sign = value < 0 ? "-" : ""
        if magnitude >= 1e-6 && magnitude < 1e21 {
            if point <= 0 { return sign + "0." + String(repeating: "0", count: -point) + digits }
            if point >= digits.count { return sign + digits + String(repeating: "0", count: point - digits.count) }
            let split = digits.index(digits.startIndex, offsetBy: point)
            return sign + digits[..<split] + "." + digits[split...]
        }
        let first = String(digits.prefix(1))
        let rest = String(digits.dropFirst())
        let normalizedExponent = point - 1
        return sign + first + (rest.isEmpty ? "" : "." + rest) + "e" + (normalizedExponent >= 0 ? "+" : "") + String(normalizedExponent)
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
    private struct FineTuneStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.background(Capsule().fill(.white.opacity(configuration.isPressed ? 0.12 : 0)))
        }
    }
    private struct ReportStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(configuration.isPressed ? P.sagePressed : P.sage))
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressFor), value: configuration.isPressed)
        }
    }
}

private struct ElasticSavingsSliderDigitSlot: Identifiable {
    let id: Int
    let char: String
}

private struct ElasticSavingsSliderDigit: View {
    let char: String
    let still: Bool
    let date: Date
    @State private var risen = ElasticSavingsSliderScalar(1)
    private typealias S = ElasticSavingsSliderMetrics
    private typealias M = ElasticSavingsSliderMotion

    var body: some View {
        let r = risen.value(at: date)
        Text(char)
            .font(.system(size: S.amount, weight: .black))
            .tracking(-4)
            .foregroundStyle(ElasticSavingsSliderPalette.coral)
            .frame(height: S.amount)
            .scaleEffect(x: still ? 1 : S.squashX + (1 - S.squashX) * r, y: still ? 1 : S.squashY + (1 - S.squashY) * r, anchor: .bottom)
            .opacity(still ? r : 1)
            .onChange(of: char) { _, _ in pop() }
            .onChange(of: still) { _, _ in pop() }
            .onChange(of: date) { _, now in risen.advance(at: now) }
    }

    private func pop() {
        risen.set(0)
        if still {
            risen.time(to: 1, duration: M.digitFade)
        } else {
            risen.spring(to: 1, constants: M.digitSpring)
        }
    }
}

private struct ElasticSavingsSliderChip: View {
    let label: String
    let on: Bool
    let date: Date
    let onPress: () -> Void
    @State private var popped = ElasticSavingsSliderScalar(0)
    @State private var labelOpacity = Tween(0.16)
    private typealias S = ElasticSavingsSliderMetrics
    private typealias M = ElasticSavingsSliderMotion
    private typealias P = ElasticSavingsSliderPalette

    var body: some View {
        let pop = popped.value(at: date)
        Button(action: onPress) {
            HStack(spacing: 0) {
                Text(label)
                    .font(.system(size: S.chipLabelSize, weight: .semibold))
                    .tracking(-0.45)
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                    .frame(height: S.chipLabelHeight)
                    .frame(maxWidth: .infinity, alignment: .leading)
                    .opacity(labelOpacity.value(at: date))
                ZStack {
                    Circle().strokeBorder(P.ring, lineWidth: 5)
                    Circle().fill(P.ink)
                        .overlay {
                            ElasticSavingsSliderCheck()
                                .stroke(.white, style: StrokeStyle(lineWidth: 2.6, lineCap: .round, lineJoin: .round))
                        }
                        .scaleEffect(pop)
                        .opacity(min(1, max(0, pop * 3)))
                }
                .frame(width: S.check, height: S.check)
            }
            .padding(.leading, 15)
            .padding(.trailing, 11)
            .frame(height: S.chip)
        }
        .buttonStyle(ChipStyle())
        .accessibilityLabel(label)
        .accessibilityValue(on ? "On" : "Off")
        .onAppear {
            popped.set(on ? 1 : 0)
            labelOpacity.retarget(on ? 1 : 0.16, duration: 0, curve: .linear)
        }
        .onChange(of: on) { _, next in
            if next { popped.spring(to: 1, constants: M.checkSpring) }
            else { popped.time(to: 0, duration: M.checkOut) }
            labelOpacity.retarget(next ? 1 : 0.16, duration: M.chipFor, curve: M.cssEase)
        }
        .onChange(of: date) { _, now in popped.advance(at: now) }
    }

    private struct ChipStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .background(Capsule().fill(configuration.isPressed ? P.sagePressed : P.sage))
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressFor), value: configuration.isPressed)
        }
    }
}

private struct ElasticSavingsSliderTriangle: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.midX, y: rect.maxY))
        path.closeSubpath()
        return path
    }
}

private struct ElasticSavingsSliderBolt: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addLines([(11.2, 1.0), (2.6, 12.6), (8.3, 12.6), (6.6, 21.0), (15.4, 9.2), (9.7, 9.2)].map {
            CGPoint(x: rect.minX + $0.0 * rect.width / 18, y: rect.minY + $0.1 * rect.height / 22)
        })
        path.closeSubpath()
        return path
    }
}

private struct ElasticSavingsSliderCheck: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.addLines([(6.4, 11.4), (9.7, 14.6), (15.7, 8.0)].map {
            CGPoint(x: rect.minX + $0.0 * rect.width / 22, y: rect.minY + $0.1 * rect.height / 22)
        })
        return path
    }
}

private struct ElasticSavingsSliderPicture: View {
    private typealias S = ElasticSavingsSliderMetrics
    private typealias P = ElasticSavingsSliderPalette
    private static let roof = [(0.0, 20.0), (45.0, 11.0), (49.0, 16.0), (0.0, 28.0)]
    private static let roofEdge = [(0.0, 28.0), (49.0, 16.0), (49.0, 21.0), (0.0, 34.0)]
    private static let glass = [(0.0, 34.0), (38.0, 24.5), (38.0, 64.0), (0.0, 64.0)]
    private static let wingRoof = [(28.0, 33.0), (64.0, 28.0), (64.0, 36.0), (28.0, 40.0)]
    private static let wing = [(31.0, 40.0), (64.0, 36.0), (64.0, 64.0), (31.0, 64.0)]

    var body: some View {
        Canvas { context, size in
            func polygon(_ points: [(Double, Double)]) -> Path {
                var path = Path()
                path.addLines(points.map { CGPoint(x: $0.0 * size.width / S.picture, y: $0.1 * size.height / S.picture) })
                path.closeSubpath()
                return path
            }
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(Gradient(colors: P.sky), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)))
            context.fill(polygon(Self.roof), with: .color(P.roof))
            context.fill(polygon(Self.roofEdge), with: .color(P.roofEdge))
            context.fill(polygon(Self.glass), with: .color(P.glass))
            var mullions = Path()
            mullions.move(to: CGPoint(x: 12, y: 31))
            mullions.addLine(to: CGPoint(x: 12, y: 64))
            mullions.move(to: CGPoint(x: 25, y: 27.8))
            mullions.addLine(to: CGPoint(x: 25, y: 64))
            mullions.move(to: CGPoint(x: 0, y: 47))
            mullions.addLine(to: CGPoint(x: 38, y: 42))
            context.stroke(mullions, with: .color(P.mullions), lineWidth: 1.2)
            context.fill(polygon(Self.wing), with: .color(P.wing))
            context.fill(polygon(Self.wingRoof), with: .color(P.wingRoof))
        }
        .clipShape(RoundedRectangle(cornerRadius: 18, style: .circular))
        .accessibilityHidden(true)
    }
}

#Preview {
    ElasticSavingsSlider(autoPlay: true)
}
