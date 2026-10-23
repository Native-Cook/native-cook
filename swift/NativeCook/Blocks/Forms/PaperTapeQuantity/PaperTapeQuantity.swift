import SwiftUI

struct PaperTapeQuantityFinish {
    var name: String
    /// The bead's colour, its lit side and its dark side.
    var color: Color
    var light: Color
    var dark: Color
    static let samples = [
        Self(name: "Cyber-Tech", color: Color(hex: 0xf08a1c), light: Color(hex: 0xffd08a), dark: Color(hex: 0xa85408)),
        Self(name: "Blue-Steel", color: Color(hex: 0x2f4de0), light: Color(hex: 0x93a6ff), dark: Color(hex: 0x16268f)),
    ]
}

private enum PaperTapeQuantityPalette {
    static let surface = Color(hex: 0xe3e3e1)
    static let ink = Color(hex: 0x2d2d2f)
    static let text = Color(hex: 0x4a4a4c)
    static let mute = Color(hex: 0x8b8a88)
    static let accent = Color(hex: 0xf26a1b)
}

private enum PaperTapeQuantityMetrics {
    static let margin = 16.0
    static let nav = 44.0
    static let round = 40.0
    static let speaker = 172.0
    static let bead = 30.0
    static let lane = 132.0
    static let row = 46.0
    static let barGap = 22.0
    static let nameSize = 26.0
    static let nameHeight = 32.0
    static let priceSize = 20.0
    static let priceHeight = 26.0
    static let descriptionSize = 15.0
    static let descriptionHeight = 20.0
    static let captionSize = 12.0
    static let captionHeight = 16.0
    static let finishSize = 19.0
    static let finishHeight = 24.0
    static let addSize = 21.0
    static let addHeight = 26.0
    static let totalSize = 15.0
    static let totalHeight = 20.0
    static let deliveryHeight = 24.0
    static let raisedDark = (x: 5.0, y: 6.0, radius: 12.0, alpha: 0.16)
    static let raisedLight = (x: -4.0, y: -4.0, radius: 10.0, alpha: 0.9)
    static let sunkDark = (x: 4.0, y: 5.0, radius: 10.0, alpha: 0.13)
    static let sunkLight = (x: -4.0, y: -4.0, radius: 10.0, alpha: 0.85)
}

private enum PaperTapeQuantityMotion {
    static let mass = 1.0
    static let stiffness = 330.0
    static let damping = 23.0
    static let nudge = 0.14
    static let nudgeIn = 0.09
    static let rubber = 0.25
    static let flick = 0.12
    static let holdIn = 0.06
    static let holdOut = 0.16
    static let totalFade = 0.14
    static let loop = 7.4
    static let firstAt = 0.8
    static let stepEvery = 0.9
    static let turnPause = 0.4
    static let pressFor = 0.14
    static let presses = [1, 1, 1, -1, -1, -1]
    static let userHold = 3.0
    static let returnFor = 0.8
    static let pressTransition = 0.12
    static let frameLimit = 0.064
    static let energyThreshold = 6e-9
    static func quad(_ t: Double) -> Double {
        let u = min(1, max(0, t))
        return u < 0.5 ? 2 * u * u : 1 - pow(-2 * u + 2, 2) / 2
    }
}

/// The tape keeps physical velocity between steps, but a caught tape becomes a direct value.
/// Springs use Reanimated's capped frame time, reversal clipping and relative rest energy.
private struct PaperTapeQuantityScalar {
    private(set) var value: Double
    private var velocity = 0.0
    private var target: Double
    private var from: Double
    private var start = Date.distantPast
    private var last = Date.distantPast
    private var duration = 0.0
    private var energy = 0.0
    private var returnTo: Double? = nil
    private var mode = Mode.fixed
    private enum Mode { case fixed, timing, spring }
    private typealias M = PaperTapeQuantityMotion
    init(_ value: Double) { self.value = value; target = value; from = value }

    mutating func set(_ next: Double) {
        value = next; target = next; velocity = 0; mode = .fixed; returnTo = nil
    }
    mutating func time(to next: Double, at date: Date = .now, duration: Double, then destination: Double? = nil) {
        from = value; target = next; start = date; self.duration = duration
        returnTo = destination; velocity = 0; mode = .timing
        if duration == 0 { set(next) }
    }
    mutating func spring(to next: Double, at date: Date = .now, velocity configured: Double = 0, still: Bool) {
        if still { set(next); return }
        if mode == .spring, target == next { return }
        let previous = mode == .spring ? velocity : 0
        velocity = previous + configured
        if velocity * (next - value) < 0 { velocity = 0 }
        target = next
        energy = 0.5 * M.stiffness * pow(value - target, 2) + 0.5 * M.mass * configured * configured
        if mode != .spring { last = date }
        mode = .spring
        returnTo = nil
    }
    mutating func advance(at date: Date) {
        switch mode {
        case .fixed: break
        case .timing:
            let age = max(0, date.timeIntervalSince(start))
            value = from + (target - from) * M.quad(duration > 0 ? age / duration : 1)
            if age >= duration {
                value = target
                if let destination = returnTo {
                    mode = .fixed
                    spring(to: destination, at: date, still: false)
                } else { mode = .fixed }
            }
        case .spring:
            let dt = min(M.frameLimit, max(0, date.timeIntervalSince(last)))
            last = date
            let decay = M.damping / (2 * M.mass)
            let omega = sqrt(M.stiffness / M.mass)
            let a = value - target
            if decay < omega {
                let damped = sqrt(omega * omega - decay * decay)
                let b = (velocity + decay * a) / damped
                let wave = a * cos(damped * dt) + b * sin(damped * dt)
                let attenuation = exp(-decay * dt)
                velocity = attenuation * (-decay * wave + damped * (-a * sin(damped * dt) + b * cos(damped * dt)))
                value = target + attenuation * wave
            } else {
                let b = velocity + omega * a
                let attenuation = exp(-omega * dt)
                value = target + attenuation * (a + b * dt)
                velocity = attenuation * (velocity - omega * b * dt)
            }
            let currentEnergy = 0.5 * M.stiffness * pow(value - target, 2) + 0.5 * M.mass * velocity * velocity
            if energy == 0 || currentEnergy / energy <= M.energyThreshold { set(target) }
        }
    }
}

/// A product in soft clay with a paper-tape quantity roller and a total that follows it.
struct PaperTapeQuantity: View {
    var quantity: Double
    var max: Int
    var price: Double
    var symbol: String
    var name: String
    var description: String
    var finishes: [PaperTapeQuantityFinish]
    var finish: Int
    var finishLabel: String
    var addLabel: String
    var deliveryLabel: String
    var accent: Color
    var autoPlay: Bool
    var onChange: ((Int) -> Void)?
    var onAdd: ((Int, Double) -> Void)?
    var onBack: (() -> Void)?
    var onBag: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var count: Int
    @State private var chosen: Int
    @State private var touches = 0
    @State private var place: PaperTapeQuantityScalar
    @State private var upHeld = PaperTapeQuantityScalar(0)
    @State private var downHeld = PaperTapeQuantityScalar(0)
    @State private var caught = 1.0
    @State private var activationTranslation = 0.0
    @State private var dragging = false
    @State private var panBegan = false
    @GestureState private var panActive = false
    @State private var previousFrame: Date? = nil
    @State private var previousPlace: Double? = nil
    @State private var smear = 0.0
    @State private var nearest: Int
    @State private var totalStart: Date? = nil
    @State private var oldTotals: [Total] = []
    @State private var totalSequence = 0
    @State private var stepHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var pressHaptic = UIImpactFeedbackGenerator(style: .medium)
    @State private var tickHaptic = UISelectionFeedbackGenerator()
    @State private var limitHaptic = UINotificationFeedbackGenerator()
    private typealias S = PaperTapeQuantityMetrics
    private typealias R = PaperTapeQuantityRollerMetrics
    private typealias M = PaperTapeQuantityMotion
    private typealias P = PaperTapeQuantityPalette

    init(
        quantity: Double = 1, max: Int = 4, price: Double = 49, symbol: String = "$",
        name: String = "Halo Speaker", description: String = "Desk speaker · 12-hour battery",
        finishes: [PaperTapeQuantityFinish] = PaperTapeQuantityFinish.samples, finish: Int = 1,
        finishLabel: String = "Finish", addLabel: String = "Add to cart",
        deliveryLabel: String = "Order today to get it by Friday", accent: Color = PaperTapeQuantityPalette.accent,
        autoPlay: Bool = false, onChange: ((Int) -> Void)? = nil,
        onAdd: ((Int, Double) -> Void)? = nil, onBack: (() -> Void)? = nil, onBag: (() -> Void)? = nil
    ) {
        self.quantity = quantity; self.max = max; self.price = price; self.symbol = symbol
        self.name = name; self.description = description; self.finishes = finishes; self.finish = finish
        self.finishLabel = finishLabel; self.addLabel = addLabel; self.deliveryLabel = deliveryLabel
        self.accent = accent; self.autoPlay = autoPlay; self.onChange = onChange
        self.onAdd = onAdd; self.onBack = onBack; self.onBag = onBag
        let first = Self.initialQuantity(quantity, max: max)
        _count = State(initialValue: first); _chosen = State(initialValue: finish)
        _place = State(initialValue: PaperTapeQuantityScalar(Double(first)))
        _nearest = State(initialValue: first)
    }

    private var first: Int { Self.initialQuantity(quantity, max: max) }
    private var totalKey: TotalKey { TotalKey(count: count, text: Self.money(symbol, price * Double(count))) }
    private static func initialQuantity(_ quantity: Double, max: Int) -> Int {
        let clamped = Swift.min(Double(max), Swift.max(1, floor(quantity + 0.5)))
        return clamped >= Double(max) ? max : Int(clamped)
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let lane = R.reach(max)
            let finishHeight = Swift.max(0, lane - S.barGap)
            let bottom = Swift.max(lane - 38, 24) + Swift.max(insets.bottom - 14, 8)
            let fixed = insets.top + S.nav + 14 + 20 + S.nameHeight + 2 + S.descriptionHeight + 15 + finishHeight + 15 + S.barGap + R.bar + 26 + S.deliveryHeight + bottom
            let heroHeight = Swift.max(120, height - fixed)
            TimelineView(.animation) { timeline in
                VStack(spacing: 0) {
                    navigation
                    PaperTapeQuantitySpeaker(finish: finishes[chosen])
                        .frame(width: S.speaker, height: S.speaker)
                        .frame(width: width - S.margin * 2, height: heroHeight)
                        .background(RoundedRectangle(cornerRadius: 30, style: .continuous).fill(
                            P.surface
                                .shadow(.inner(color: Color.rgba(60, 50, 40, S.sunkDark.alpha), radius: S.sunkDark.radius / 2, x: S.sunkDark.x, y: S.sunkDark.y))
                                .shadow(.inner(color: .white.opacity(S.sunkLight.alpha), radius: S.sunkLight.radius / 2, x: S.sunkLight.x, y: S.sunkLight.y))
                        ))
                        .padding(.top, 14)
                    HStack(alignment: .firstTextBaseline, spacing: 12) {
                        Text(name).font(.system(size: S.nameSize, weight: .bold)).tracking(-0.3).lineLimit(1)
                            .frame(height: S.nameHeight)
                            .frame(maxWidth: .infinity, alignment: .leading)
                        Text(Self.money(symbol, price)).font(.system(size: S.priceSize, weight: .semibold)).monospacedDigit().lineLimit(1)
                            .frame(height: S.priceHeight)
                    }
                    .foregroundStyle(P.ink)
                    .padding(.horizontal, S.margin + 4)
                    .padding(.top, 20)
                    Text(description).font(.system(size: S.descriptionSize)).foregroundStyle(P.mute).lineLimit(1)
                        .frame(height: S.descriptionHeight)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding(.horizontal, S.margin + 4).padding(.top, 2)
                    rule
                    finishChoices(height: finishHeight)
                    rule
                    bar(width: width, lane: lane, date: timeline.date)
                        .padding(.top, S.barGap)
                        .zIndex(1)
                    delivery
                        .padding(.top, 26)
                    Color.clear.frame(height: bottom)
                }
                .padding(.top, insets.top)
                .frame(width: width, alignment: .top)
                .frame(width: width, height: height, alignment: .top)
                .background(P.surface)
                .offset(x: -insets.leading, y: -insets.top)
                .onChange(of: timeline.date) { _, date in advance(at: date) }
            }
        }
        .preferredColorScheme(.light)
        .onAppear {
            stepHaptic.prepare(); pressHaptic.prepare(); tickHaptic.prepare(); limitHaptic.prepare()
            totalStart = reduceMotion ? nil : .now
        }
        .onChange(of: panActive) { _, active in
            if !active {
                // Gesture-state reset can precede onEnded; settle a completed drag first.
                DispatchQueue.main.async {
                    if !panActive { dragging = false; panBegan = false; activationTranslation = 0 }
                }
            }
        }
        .onChange(of: totalKey) { old, next in
            guard old.count != next.count else { return }
            if reduceMotion { oldTotals = []; totalStart = nil }
            else {
                let now = Date.now
                oldTotals.append(Total(id: totalSequence, text: old.text, start: now))
                totalSequence += 1
                totalStart = now
            }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private var navigation: some View {
        HStack {
            round(icon: "chevron.left", label: "Back") { touched(); onBack?() }
            Spacer()
            round(icon: "bag", label: "Bag") { touched(); onBag?() }
        }
        .padding(.horizontal, S.margin).frame(height: S.nav)
    }
    private func round(icon: String, label: String, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                Circle().fill(P.surface).shadow(color: Color.rgba(60, 50, 40, S.raisedDark.alpha), radius: S.raisedDark.radius / 2, x: S.raisedDark.x, y: S.raisedDark.y)
                Circle().fill(P.surface).shadow(color: .white.opacity(S.raisedLight.alpha), radius: S.raisedLight.radius / 2, x: S.raisedLight.x, y: S.raisedLight.y)
                Circle().fill(P.surface)
                Image(systemName: icon).font(.system(size: 20)).foregroundStyle(P.text)
            }
            .frame(width: S.round, height: S.round)
            .contentShape(Rectangle().inset(by: -6))
        }
        .buttonStyle(RoundStyle()).accessibilityLabel(label)
    }
    private var rule: some View {
        Color.black.opacity(0.07).frame(height: 1)
            .shadow(color: .white.opacity(0.75), radius: 0, x: 0, y: 1)
            .padding(.leading, S.margin).padding(.top, 14)
    }
    private func finishChoices(height: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(finishLabel.uppercased()).font(.system(size: S.captionSize, weight: .semibold)).tracking(0.8)
                .foregroundStyle(P.mute).lineLimit(1).frame(height: S.captionHeight)
                .padding(.leading, 4).padding(.bottom, 2)
            ForEach(finishes.indices, id: \.self) { index in
                Button {
                    touched(); tickHaptic.selectionChanged(); chosen = index
                } label: {
                    HStack(spacing: 8) {
                        PaperTapeQuantityBead(finish: finishes[index], on: index == chosen).frame(width: S.bead, height: S.bead)
                        Text(finishes[index].name).font(.system(size: S.finishSize, weight: .medium))
                            .foregroundStyle(index == chosen ? P.ink : P.mute).lineLimit(1).frame(height: S.finishHeight)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading).frame(height: S.row).contentShape(Rectangle())
                }
                .buttonStyle(.plain).accessibilityAddTraits(index == chosen ? .isSelected : [])
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .frame(maxWidth: .infinity, alignment: .leading).frame(height: height)
        .padding(.leading, S.lane).accessibilityElement(children: .contain).accessibilityLabel(finishLabel)
    }
    private func bar(width: Double, lane: Double, date: Date) -> some View {
        ZStack(alignment: .topLeading) {
            PaperTapeQuantityRoller(width: width, margin: S.margin, max: max, place: place.value, upHeld: upHeld.value, downHeld: downHeld.value, canUp: count < max, canDown: count > 1, still: reduceMotion, smear: smear)
                .frame(width: width, height: (lane + R.bleed) * 2 + R.bar)
                .offset(y: -(lane + R.bleed)).allowsHitTesting(false)
            Color.clear.frame(width: R.drumWidth + 12, height: R.bar)
                .contentShape(Rectangle()).offset(x: S.margin + R.drumAt - R.drumWidth / 2 - 6)
                .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .local)
                    .updating($panActive) { _, active, _ in active = true }
                    .onChanged { event in
                        if !panBegan { panBegan = true; activationTranslation = 0; touched() }
                        if !dragging, abs(event.translation.height) > 3 {
                            place.set(place.value); caught = place.value
                            activationTranslation = event.translation.height; dragging = true
                        }
                        if dragging {
                            // The custom vertical criterion resets iOS translation at activation.
                            let raw = caught - (event.translation.height - activationTranslation) / R.pitch
                            place.set(raw < 1 ? 1 - (1 - raw) * M.rubber : raw > Double(max) ? Double(max) + (raw - Double(max)) * M.rubber : raw)
                        }
                    }
                    .onEnded { event in
                        if dragging {
                            dragging = false
                            let speed = -event.velocity.height / R.pitch
                            let to = Swift.min(max, Swift.max(1, Int(floor(place.value + speed * M.flick + 0.5))))
                            place.spring(to: Double(to), velocity: speed, still: reduceMotion)
                            count = to; onChange?(to); touched()
                        }
                        panBegan = false; activationTranslation = 0
                    }
                )
                .accessibilityElement(children: .ignore).accessibilityLabel("Quantity")
                .accessibilityValue(String(count) + " of " + String(max))
                .accessibilityAdjustableAction { direction in touched(); step(direction == .increment ? 1 : -1, felt: true) }
            arrow(up: true)
                .offset(x: S.margin + R.tileAt - 6, y: (R.bar - R.tileHeight) / 2 - 14)
            arrow(up: false)
                .offset(x: S.margin + R.tileAt - 6, y: R.bar / 2)
            Button {
                touched(); pressHaptic.impactOccurred(); onAdd?(count, price * Double(count))
            } label: {
                VStack(alignment: .leading, spacing: 0) {
                    Text(addLabel).font(.system(size: S.addSize, weight: .medium)).foregroundStyle(P.text).lineLimit(1).frame(height: S.addHeight)
                    ZStack(alignment: .leading) {
                        ForEach(oldTotals) { old in
                            totalText(old.text).opacity(1 - M.quad(date.timeIntervalSince(old.start) / M.totalFade))
                        }
                        totalText(Self.money(symbol, price * Double(count)))
                            .opacity(totalStart.map { M.quad(date.timeIntervalSince($0) / M.totalFade) } ?? 1)
                    }.frame(height: S.totalHeight)
                }
                .frame(maxWidth: .infinity, alignment: .leading).frame(height: R.bar).contentShape(Rectangle())
            }
            .buttonStyle(AddStyle())
            .frame(width: width - S.margin * 2 - R.tileAt - R.tileWidth - 14)
            .offset(x: S.margin + R.tileAt + R.tileWidth + 14)
            .accessibilityLabel(addLabel + ", " + String(count) + ", " + Self.money(symbol, price * Double(count)))
        }
        // The roller's canvas makes the stack taller than the bar; everything is placed from the bar's top.
        .frame(width: width, height: R.bar, alignment: .top)
    }
    private func arrow(up: Bool) -> some View {
        Button { touched(); step(up ? 1 : -1, felt: true) } label: {
            Color.clear.frame(width: R.tileWidth + 14, height: R.tileHeight / 2 + 14).contentShape(Rectangle())
        }
        .buttonStyle(ArrowStyle { pressed in hold(up: up, down: pressed) })
        .accessibilityLabel(up ? "One more" : "One fewer")
    }
    private func totalText(_ text: String) -> some View {
        Text(text).font(.system(size: S.totalSize, weight: .medium)).monospacedDigit().foregroundStyle(P.mute).lineLimit(1).frame(height: S.totalHeight)
    }
    private var delivery: some View {
        HStack(spacing: 8) {
            Image(systemName: "clock.fill").font(.system(size: 20))
            Text(deliveryLabel).font(.system(size: S.descriptionSize)).lineLimit(1).frame(height: S.descriptionHeight)
        }
        .foregroundStyle(P.text).frame(maxWidth: .infinity, alignment: .leading)
        .padding(.leading, S.lane).frame(height: S.deliveryHeight)
        .overlay(alignment: .leading) {
            RoundedRectangle(cornerRadius: 1.5, style: .circular).fill(accent).frame(width: 8, height: 8).offset(x: S.margin + 6)
        }
    }
    private func advance(at date: Date) {
        place.advance(at: date); upHeld.advance(at: date); downHeld.advance(at: date)
        if let last = previousFrame, let before = previousPlace, date > last {
            smear = reduceMotion ? 0 : Swift.min(R.smearMax, R.smear * abs(place.value - before) / date.timeIntervalSince(last))
        }
        previousPlace = place.value; previousFrame = date
        let next = Swift.min(max, Swift.max(1, Int(floor(place.value + 0.5))))
        if next != nearest {
            nearest = next
            if dragging { count = next; tickHaptic.selectionChanged(); onChange?(next) }
        }
        oldTotals.removeAll { date.timeIntervalSince($0.start) >= M.totalFade }
    }
    private func step(_ by: Int, felt: Bool) {
        let next = Swift.min(max, Swift.max(1, count + by))
        if next == count {
            if felt { limitHaptic.notificationOccurred(.warning) }
            if !reduceMotion { place.time(to: Double(count) + Double(by) * M.nudge, duration: M.nudgeIn, then: Double(count)) }
            return
        }
        if felt { stepHaptic.impactOccurred() }
        count = next; onChange?(next); place.spring(to: Double(next), still: reduceMotion)
    }
    private func reset() { count = first; place.spring(to: Double(first), still: reduceMotion) }
    private func hold(up: Bool, down: Bool) {
        let duration = reduceMotion ? 0 : down ? M.holdIn : M.holdOut
        if up { upHeld.time(to: down ? 1 : 0, duration: duration) }
        else { downHeld.time(to: down ? 1 : 0, duration: duration) }
    }
    private func touched() { touches += 1 }
    private func loop() async {
        guard autoPlay else { return }
        defer { upHeld.set(0); downHeld.set(0) }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            reset()
            try? await Task.sleep(for: .seconds(M.returnFor))
            guard !Task.isCancelled else { return }
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            var at = M.firstAt
            for (order, by) in M.presses.enumerated() {
                if order > 0, by != M.presses[order - 1] { at += M.turnPause }
                try? await clock.sleep(until: origin + .seconds(at - M.pressFor))
                guard !Task.isCancelled else { return }
                hold(up: by > 0, down: true)
                try? await clock.sleep(until: origin + .seconds(at))
                guard !Task.isCancelled else { return }
                hold(up: by > 0, down: false); step(by, felt: false)
                at += M.stepEvery
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }
    private static func money(_ symbol: String, _ value: Double) -> String {
        let fixed: String
        if value.isNaN { fixed = "NaN" }
        else if value == .infinity { fixed = "Infinity" }
        else if value == -.infinity { fixed = "-Infinity" }
        else if abs(value) >= 1e21 {
            let parts = String(value).lowercased().split(separator: "e")
            var mantissa = String(parts[0])
            if mantissa.hasSuffix(".0") { mantissa = String(mantissa.dropLast(2)) }
            let exponent = parts.count > 1 ? Int(parts[1]) ?? 0 : 0
            fixed = mantissa + "e" + (exponent >= 0 ? "+" : "") + String(exponent)
        } else {
            // toFixed rounds the actual binary value, choosing the larger cent on an exact tie.
            let bits = abs(value).bitPattern
            let exponent = Int((bits >> 52) & 0x7ff)
            let significand = (bits & 0x000f_ffff_ffff_ffff) | (exponent == 0 ? 0 : UInt64(1) << 52)
            let coefficient = significand * 100
            let shift = exponent == 0 ? -1074 : exponent - 1023 - 52
            var cents: String
            if shift < 0 {
                let right = -shift
                var rounded = right >= 64 ? 0 : coefficient >> right
                if right < 64 {
                    let remainder = coefficient & ((UInt64(1) << right) - 1)
                    let half = UInt64(1) << (right - 1)
                    if remainder >= half { rounded += 1 }
                }
                cents = String(rounded)
            } else {
                cents = String(coefficient)
                for _ in 0..<shift {
                    var doubled = ""; var carry = 0
                    for digit in cents.reversed() {
                        let next = digit.wholeNumberValue! * 2 + carry
                        doubled = String(next % 10) + doubled; carry = next / 10
                    }
                    cents = (carry > 0 ? String(carry) : "") + doubled
                }
            }
            cents = String(repeating: "0", count: Swift.max(0, 3 - cents.count)) + cents
            fixed = (value < 0 ? "-" : "") + cents.dropLast(2) + "." + cents.suffix(2)
        }
        let range = NSRange(location: 0, length: (fixed as NSString).length)
        return symbol + grouping.stringByReplacingMatches(in: fixed, range: range, withTemplate: ",")
    }
    private static let grouping = try! NSRegularExpression(pattern: #"\B(?=(\d{3})+(?!\d))"#)
    private struct Total: Identifiable { let id: Int; let text: String; let start: Date }
    private struct TotalKey: Equatable { let count: Int; let text: String }
    private struct LoopKey: Equatable { let on: Bool; let touches: Int }
    private struct RoundStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.scaleEffect(configuration.isPressed ? 0.94 : 1)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressTransition), value: configuration.isPressed)
        }
    }
    private struct AddStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.45 : 1)
                .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: M.pressTransition), value: configuration.isPressed)
        }
    }
    private struct ArrowStyle: ButtonStyle {
        let held: (Bool) -> Void
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.onChange(of: configuration.isPressed) { _, value in held(value) }
        }
    }
}

private struct PaperTapeQuantityBead: View {
    let finish: PaperTapeQuantityFinish
    let on: Bool
    var body: some View {
        Canvas { context, _ in
            let c = PaperTapeQuantityMetrics.bead / 2
            func circle(_ x: Double, _ y: Double, _ radius: Double, _ color: Color, blur: Double = 0) {
                var copy = context
                if blur > 0 { copy.addFilter(.blur(radius: blur)) }
                copy.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color))
            }
            if on { circle(c, c, 11, finish.color.opacity(0.55), blur: 4) }
            circle(c + 1, c + 2, 9, .black.opacity(0.25), blur: 2)
            circle(c, c, 9.5, Color(hex: 0xf6f5f3))
            let r = on ? 7.0 : 7.6
            context.fill(Path(ellipseIn: CGRect(x: c - r, y: c - r, width: r * 2, height: r * 2)), with: .radialGradient(Gradient(stops: [.init(color: finish.light, location: 0), .init(color: finish.color, location: 0.5), .init(color: finish.dark, location: 1)]), center: CGPoint(x: c - 2.5, y: c - 3), startRadius: 0, endRadius: 10))
            var shine = context; shine.addFilter(.blur(radius: 0.8))
            shine.fill(Path(ellipseIn: CGRect(x: c - 4.6, y: c - 5.4, width: 5, height: 3)), with: .color(.white.opacity(0.75)))
        }.accessibilityHidden(true)
    }
}

private struct PaperTapeQuantitySpeaker: View {
    let finish: PaperTapeQuantityFinish
    var body: some View {
        Canvas { context, _ in
            let c = PaperTapeQuantityMetrics.speaker / 2
            func circle(_ x: Double, _ y: Double, _ radius: Double, _ color: Color, blur: Double = 0) {
                var copy = context; if blur > 0 { copy.addFilter(.blur(radius: blur)) }
                copy.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color))
            }
            func gradient(_ radius: Double, _ center: CGPoint, _ reach: Double, _ colors: [Color], middle: Double) {
                context.fill(Path(ellipseIn: CGRect(x: c - radius, y: c - radius, width: radius * 2, height: radius * 2)), with: .radialGradient(Gradient(stops: [.init(color: colors[0], location: 0), .init(color: colors[1], location: middle), .init(color: colors[2], location: 1)]), center: center, startRadius: 0, endRadius: reach))
            }
            circle(c + 8, c + 11, 62, Color.rgba(60, 50, 40, 0.3), blur: 14)
            circle(c - 6, c - 7, 62, .white.opacity(0.95), blur: 10)
            gradient(66, CGPoint(x: c - 22, y: c - 26), 110, [Color(hex: 0xf6f5f3), Color(hex: 0xe4e3e1), Color(hex: 0xcbcac8)], middle: 0.55)
            gradient(47, CGPoint(x: c + 14, y: c + 18), 80, [Color(hex: 0xe9e8e6), Color(hex: 0xd6d5d3), Color(hex: 0xc2c1bf)], middle: 0.55)
            context.stroke(Path(ellipseIn: CGRect(x: c - 47, y: c - 47, width: 94, height: 94)), with: .color(.white.opacity(0.8)), lineWidth: 1.2)
            for index in 0..<20 {
                let angle = Double(index) / 20 * tau
                let x = c + cos(angle) * 34; let y = c + sin(angle) * 34
                circle(x + 0.6, y + 0.8, 2.3, .white.opacity(0.8)); circle(x, y, 2.3, Color(hex: 0x9f9e9c))
            }
            circle(c, c, 21, finish.color.opacity(0.5), blur: 9)
            circle(c, c, 17, Color(hex: 0xf4f3f1))
            gradient(13.5, CGPoint(x: c - 4.5, y: c - 5.5), 18, [finish.light, finish.color, finish.dark], middle: 0.5)
            var shine = context; shine.addFilter(.blur(radius: 1.4))
            shine.fill(Path(ellipseIn: CGRect(x: c - 8.5, y: c - 9.5, width: 9, height: 5.5)), with: .color(.white.opacity(0.7)))
        }.accessibilityHidden(true)
    }
}

#Preview { PaperTapeQuantity(autoPlay: true) }
