import SwiftUI

private enum PriceGlyphScatterSliderPalette {
    static let page = Color(hex: 0xF3F4F9)
    static let card = Color(hex: 0xFFFFFF)
    static let purple = Color(hex: 0x5B4FD6)
    static let trackRest = Color(hex: 0xE6E7F2)
    static let ink = Color(hex: 0x1B1B2F)
    static let body = Color(hex: 0x6E6E85)
    static let mute = Color(hex: 0x9A9AB0)
    static let shadow = Color(red: 60 / 255, green: 60 / 255, blue: 120 / 255)
}

enum PriceGlyphScatterSliderMetrics {
    static let margin = 20.0
    static let cardTop = 212.0
    static let cardHeight = 164.0
    static let cardRadius = 22.0
    static let inset = 22.0
    static let trackWidth = 218.0
    static let trackHeight = 6.0
    static let rowTop = 52.0
    static let rowHeight = 44.0
    static let priceGap = 16.0
    static let priceWidth = 75.0
    static let thumb = 24.0
    static let thumbGrown = 28.0
    static let ring = 3.0
    static let priceSize = 26.0
    static let dollarSize = 22.0
    static let minValue = 40
    static let maxValue = 600
    static let step = 5
    static let nights = 3
    /// Bounds of the scatter at full amplitude.
    static let scatterY = 14.0
    static let scatterX = 4.0
    static let scatterRotation = 14.0
    /// Thumb speed (pt/s along the track) that scatters the glyphs fully.
    static let fullSpeed = 220.0
    static let buttonHeight = 54.0
    static let tileTop = 52.0
    static let tileHeight = 84.0
    static let tileGap = 12.0
    /// How a day's budget splits between the three tiles.
    static let shares: [(label: String, icon: String, share: Double)] = [("Food", "fork.knife", 0.4), ("Transport", "tram.fill", 0.2), ("Tickets", "ticket.fill", 0.4)]
}

enum PriceGlyphScatterSliderMotion {
    static let loop = 8.0
    /// The loop's drags: start and end times. The first leaves the configured value, the last returns to it.
    static let segments: [(start: Double, end: Double)] = [(0.9, 2.1), (3.3, 4.5), (5.6, 6.6)]
    /// Where the drags stop in between (as 0…1 along the track): $520, then $95.
    static let stops = [0.857, 0.098]
    /// The places the drags visit, in order: home, the stops, home again.
    static func path(home: Double) -> [Double] { [home] + stops + [home] }
    static let wobbleFor = 0.8
    static let smoothing = 0.05
    static let releaseResponse = 0.42
    static let releaseDamping = 0.55
    static let grow = 0.2
    static let shrink = 0.25
    static let resume = 2.0
    static let returnFor = 0.6

    static func clamp(_ t: Double) -> Double { min(1, max(0, t)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeInOutSine(_ x: Double) -> Double { (1 - cos(.pi * x)) / 2 }
    /// d/dx of easeInOutSine.
    static func easeInOutSineSlope(_ x: Double) -> Double { .pi / 2 * sin(.pi * x) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    /// The glyphs' overshoot after a drag stops: starts at 0, swings the other way and dies out.
    static func wobble(_ e: Double, sign: Double, peak: Double) -> Double {
        guard e >= 0 && e < wobbleFor else { return 0 }
        return -0.18 * sign * min(1, peak) * sin(2 * .pi * 2 * e) * exp(-6 * e)
    }
    /// The amplitude after a release: a damped spring from `a0` to 0.
    static func released(_ a0: Double, _ e: Double) -> Double {
        let omega = 2 * .pi / releaseResponse
        let zeta = releaseDamping
        let wd = omega * sqrt(1 - zeta * zeta)
        return a0 * exp(-zeta * omega * e) * (cos(wd * e) + zeta * omega / wd * sin(wd * e))
    }
}

/// How far a glyph leaves its place at full amplitude: y, x and rotation factors.
struct PriceGlyphScatterSliderGlyphFactor {
    var y: Double
    var x: Double
    var rotation: Double
    /// Counted from the right: the last digit, the tens, the hundreds.
    static let digits = [
        Self(y: -1.0, x: 0.6, rotation: 1.0),
        Self(y: 0.7, x: -0.4, rotation: -0.8),
        Self(y: -0.55, x: 0.5, rotation: 0.6),
    ]
    static let dollar = Self(y: 0.35, x: -0.2, rotation: -0.4)
}

/// Everything one frame draws.
struct PriceGlyphScatterSliderFrame: Equatable {
    /// The thumb's place along the track, 0…1.
    var p: Double
    /// The scatter amplitude, −1…1; its sign is the drag's direction.
    var a: Double
    /// 0 at rest, 1 while the thumb is held.
    var grow: Double
    /// The still screen: the thumb at `home`, the glyphs in place.
    static func rest(at home: Double) -> PriceGlyphScatterSliderFrame { PriceGlyphScatterSliderFrame(p: home, a: 0, grow: 0) }
}

enum PriceGlyphScatterSliderScene {
    private typealias S = PriceGlyphScatterSliderMetrics
    private typealias M = PriceGlyphScatterSliderMotion

    /// One eased drag from `from` to `to` over `start…end`: the place and the amplitude at `t`.
    static func drag(_ t: Double, start: Double, end: Double, from: Double, to: Double) -> (p: Double, a: Double) {
        let u = M.seg(t, start, end)
        let p = from + (to - from) * M.easeInOutSine(u)
        let speed = (to - from) * M.easeInOutSineSlope(u) / (end - start) * S.trackWidth
        return (p, max(-1, min(1, speed / S.fullSpeed)))
    }

    /// The self-playing loop at `t`, leaving from and coming back to `home` (the configured value's place).
    static func loopFrame(_ t: Double, home: Double) -> PriceGlyphScatterSliderFrame {
        let path = M.path(home: home)
        var p = home
        var a = 0.0
        for (i, s) in M.segments.enumerated() {
            let from = path[i], to = path[i + 1]
            if t >= s.start && t < s.end {
                let d = drag(t, start: s.start, end: s.end, from: from, to: to)
                p = d.p; a = d.a
            } else if t >= s.end {
                p = to
                let peakSpeed = abs(to - from) * .pi / 2 / (s.end - s.start) * S.trackWidth / S.fullSpeed
                a += M.wobble(t - s.end, sign: to > from ? 1 : -1, peak: peakSpeed)
            }
        }
        return PriceGlyphScatterSliderFrame(p: p, a: a, grow: 0)
    }
}

/// A budget slider whose price readout breaks into glyphs that scatter with the thumb's speed and settle when it stops.
struct PriceGlyphScatterSlider: View {
    var value: Int
    var autoPlay: Bool
    var eyebrow: String
    var title: String
    var subtitle: String
    var cardLabel: String
    var nights: Int
    var hint: String
    var buttonLabel: String
    var onChange: ((Int) -> Void)?
    var onSubmit: ((Int) -> Void)?
    static let loop = PriceGlyphScatterSliderMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var mode: Mode
    @State private var origin = Date.now
    /// Where the thumb rests: the configured value, or the value the user chose when the block does not play by itself.
    @State private var restP: Double
    @State private var touchP: Double
    @State private var lastP: Double
    @State private var lastDate: Date? = nil
    @State private var smoothedSpeed = 0.0
    @State private var touchStart = Date.distantPast
    @State private var grip = 0.0
    @State private var touches = 0
    /// A finger on the button: the block holds where it is until it lifts.
    @State private var pressing = false
    @State private var lastTick: Int
    @GestureState private var panActive = false
    @State private var haptic = UISelectionFeedbackGenerator()
    private typealias S = PriceGlyphScatterSliderMetrics
    private typealias M = PriceGlyphScatterSliderMotion
    private typealias P = PriceGlyphScatterSliderPalette

    init(value: Int = 180, autoPlay: Bool = false, eyebrow: String = "Weekender", title: String = "Daily budget",
         subtitle: String = "How much would you like to spend a day in Lisbon? Food, transport and tickets.",
         cardLabel: String = "Per day", nights: Int = PriceGlyphScatterSliderMetrics.nights,
         hint: String = "Drag the dot — the price settles when you let go.", buttonLabel: String = "Set budget",
         onChange: ((Int) -> Void)? = nil, onSubmit: ((Int) -> Void)? = nil) {
        self.value = value; self.autoPlay = autoPlay; self.eyebrow = eyebrow; self.title = title; self.subtitle = subtitle
        self.cardLabel = cardLabel; self.nights = nights; self.hint = hint; self.buttonLabel = buttonLabel
        self.onChange = onChange; self.onSubmit = onSubmit
        let p = Self.place(of: value)
        _mode = State(initialValue: .loop)
        _restP = State(initialValue: p)
        _touchP = State(initialValue: p)
        _lastP = State(initialValue: p)
        _lastTick = State(initialValue: Self.amount(at: p) / 50)
    }

    private enum Mode: Equatable {
        /// Resting at `restP`, or playing the loop from it.
        case loop
        case touching
        /// The thumb holds at `p`; the scatter springs down from `a0` and the thumb shrinks from `grow0`, since `at`.
        case released(p: Double, a0: Double, grow0: Double, at: Date)
        /// The thumb glides from `from` back to `restP`, since `at`.
        case returning(from: Double, at: Date)
    }

    static func place(of value: Int) -> Double {
        PriceGlyphScatterSliderMotion.clamp(Double(value - PriceGlyphScatterSliderMetrics.minValue) / Double(PriceGlyphScatterSliderMetrics.maxValue - PriceGlyphScatterSliderMetrics.minValue))
    }
    static func amount(at p: Double) -> Int {
        let raw = Double(PriceGlyphScatterSliderMetrics.minValue) + Double(PriceGlyphScatterSliderMetrics.maxValue - PriceGlyphScatterSliderMetrics.minValue) * PriceGlyphScatterSliderMotion.clamp(p)
        return Int((raw / Double(PriceGlyphScatterSliderMetrics.step)).rounded()) * PriceGlyphScatterSliderMetrics.step
    }

    private var paused: Bool {
        switch mode {
        case .loop: return !autoPlay || reduceMotion
        default: return false
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let cardWidth = width - S.margin * 2
            TimelineView(.animation(paused: paused)) { timeline in
                let frame = frame(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    P.page
                    header(top: insets.top, width: width)
                    card(frame: frame, width: cardWidth)
                        .offset(x: S.margin, y: insets.top + S.cardTop)
                    Text(hint).font(.system(size: 13)).foregroundStyle(P.mute).multilineTextAlignment(.center).lineLimit(2)
                        .frame(width: cardWidth)
                        .offset(x: S.margin, y: insets.top + S.cardTop + S.cardHeight + 18)
                    tiles(amount: Self.amount(at: frame.p), width: cardWidth)
                        .offset(x: S.margin, y: insets.top + S.cardTop + S.cardHeight + S.tileTop)
                    button(width: cardWidth)
                        .offset(x: S.margin, y: height - max(insets.bottom, 16) - 20 - S.buttonHeight)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .onChange(of: timeline.date) { _, date in advance(at: date) }
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        // The gesture state resets when the drag ends or the system cancels it, so a cancelled drag still releases.
        .onChange(of: panActive) { _, active in
            if !active { DispatchQueue.main.async { if !panActive { release() } } }
        }
        // A controlled parent moves the rest explicitly; a touch in progress keeps going.
        .onChange(of: value) { _, next in restP = Self.place(of: next) }
        .task(id: touches) { await resume() }
    }

    // MARK: Frames

    private func frame(at date: Date) -> PriceGlyphScatterSliderFrame {
        switch mode {
        case .loop:
            guard autoPlay, !reduceMotion else { return .rest(at: restP) }
            let t = max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: M.loop)
            return PriceGlyphScatterSliderScene.loopFrame(t, home: restP)
        case .touching:
            let grow = reduceMotion ? 1 : M.easeOutCubic(M.seg(date.timeIntervalSince(touchStart), 0, M.grow))
            let a = reduceMotion ? 0 : max(-1, min(1, smoothedSpeed / S.fullSpeed))
            return PriceGlyphScatterSliderFrame(p: touchP, a: a, grow: grow)
        case .released(let p, let a0, let grow0, let at):
            let e = date.timeIntervalSince(at)
            let grow = reduceMotion ? 0 : grow0 * (1 - M.seg(e, 0, M.shrink))
            return PriceGlyphScatterSliderFrame(p: p, a: reduceMotion ? 0 : M.released(a0, e), grow: grow)
        case .returning(let from, let at):
            let e = date.timeIntervalSince(at)
            let d = PriceGlyphScatterSliderScene.drag(e, start: 0, end: M.returnFor, from: from, to: restP)
            return PriceGlyphScatterSliderFrame(p: d.p, a: reduceMotion ? 0 : d.a, grow: 0)
        }
    }

    /// Per frame while a finger is down: the thumb's speed, smoothed, drives the scatter; the value ticks by $50.
    private func advance(at date: Date) {
        guard case .touching = mode else { lastDate = nil; return }
        let dt = min(0.064, max(0.001, date.timeIntervalSince(lastDate ?? date.addingTimeInterval(-1 / 60))))
        lastDate = date
        let speed = (touchP - lastP) * S.trackWidth / dt
        lastP = touchP
        smoothedSpeed += (speed - smoothedSpeed) * (1 - exp(-dt / M.smoothing))
        let tick = Self.amount(at: touchP) / 50
        if tick != lastTick {
            lastTick = tick
            haptic.selectionChanged()
        }
    }

    // MARK: Touch

    private func drag(trackX0: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .updating($panActive) { _, active, _ in active = true }
            .onChanged { event in
                if mode != .touching {
                    let current = frame(at: .now).p
                    let at = (event.startLocation.x - trackX0) / S.trackWidth
                    grip = abs(at - current) * S.trackWidth <= S.thumb ? at - current : 0
                    touchP = current
                    lastP = current
                    smoothedSpeed = 0
                    lastDate = nil
                    touchStart = .now
                    lastTick = Self.amount(at: current) / 50
                    mode = .touching
                    touches += 1
                }
                let next = M.clamp((event.location.x - trackX0) / S.trackWidth - grip)
                if Self.amount(at: next) != Self.amount(at: touchP) { onChange?(Self.amount(at: next)) }
                touchP = next
            }
            .onEnded { _ in release() }
    }

    private func release() {
        guard case .touching = mode else { return }
        let a0 = reduceMotion ? 0 : max(-1, min(1, smoothedSpeed / S.fullSpeed))
        mode = .released(p: touchP, a0: a0, grow0: 1, at: .now)
        touches += 1
    }

    /// Any touch outside the slider (the button) holds the block where it is, so it rests and resumes like a drag:
    /// the playing loop, or a glide home that a resume started. The frame is captured as it is, so nothing jumps.
    private func pauseLoop() {
        switch mode {
        case .loop: guard autoPlay, !reduceMotion else { return }
        case .returning: break
        default: return
        }
        let now = Date.now
        let current = frame(at: now)
        mode = .released(p: current.p, a0: current.a, grow0: current.grow, at: now)
    }

    /// 2 s after the last touch: a playing block glides home and its loop starts again from rest; a still one keeps the chosen value.
    /// A finger on the button keeps the hold; the release counts as the last touch. A timer another touch
    /// superseded does nothing, even when it wakes before `.task(id:)` has cancelled it.
    private func resume() async {
        guard touches > 0 else { origin = .now; return }
        let generation = touches
        try? await Task.sleep(for: .seconds(M.resume))
        guard !Task.isCancelled, touches == generation, !pressing, case .released(let p, _, _, _) = mode else { return }
        if autoPlay {
            if abs(p - restP) > 0.001 {
                mode = .returning(from: p, at: .now)
                try? await Task.sleep(for: .seconds(M.returnFor + 0.05))
                guard !Task.isCancelled, touches == generation, case .returning = mode else { return }
            }
        } else {
            restP = p
        }
        origin = .now
        mode = .loop
    }

    // MARK: Layers

    private func header(top: Double, width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Circle().fill(P.card).frame(width: 36, height: 36)
                .overlay(Image(systemName: "chevron.left").font(.system(size: 14, weight: .semibold)).foregroundStyle(P.ink))
                .offset(x: S.margin, y: top + 12)
            Text(eyebrow.uppercased()).font(.system(size: 12, weight: .bold)).tracking(1.6).foregroundStyle(P.purple)
                .offset(x: S.margin + 4, y: top + 72)
            Text(title).font(.system(size: 30, weight: .bold)).tracking(-0.4).foregroundStyle(P.ink).lineLimit(1)
                .offset(x: S.margin + 4, y: top + 92)
            Text(subtitle).font(.system(size: 15)).foregroundStyle(P.body).lineSpacing(3).lineLimit(2)
                .frame(width: width - S.margin * 2 - 8, alignment: .leading)
                .offset(x: S.margin + 4, y: top + 134)
        }
        .frame(width: width, alignment: .topLeading)
    }

    private func card(frame: PriceGlyphScatterSliderFrame, width: Double) -> some View {
        let amount = Self.amount(at: frame.p)
        let total = amount * nights
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: S.cardRadius, style: .continuous).fill(P.card)
                .shadow(color: P.shadow.opacity(0.12), radius: 15, y: 10)
                .shadow(color: P.shadow.opacity(0.06), radius: 1, y: 1)
            Text(cardLabel.uppercased()).font(.system(size: 11, weight: .bold)).tracking(1.2).foregroundStyle(P.mute)
                .offset(x: S.inset, y: S.inset)
            slider(frame: frame)
                .offset(x: 0, y: S.rowTop)
            readout(amount: amount, a: frame.a)
                .frame(width: S.priceWidth, height: S.rowHeight, alignment: .trailing)
                .offset(x: S.inset + S.trackWidth + S.priceGap, y: S.rowTop)
            Text("$\(S.minValue)").font(.system(size: 12, weight: .medium)).foregroundStyle(P.mute)
                .offset(x: S.inset, y: 108)
            Text("$\(S.maxValue)").font(.system(size: 12, weight: .medium)).foregroundStyle(P.mute)
                .frame(width: S.trackWidth, alignment: .trailing)
                .offset(x: S.inset, y: 108)
            (Text("≈ ").foregroundStyle(P.body) + Text("$\(Self.grouped(total))").font(.system(size: 13, weight: .semibold)).foregroundStyle(P.ink)
                + Text(" for \(nights) nights").foregroundStyle(P.body))
                .font(.system(size: 13))
                .offset(x: S.inset, y: 128)
        }
        .frame(width: width, height: S.cardHeight, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(title)
        .accessibilityValue("$\(amount) per day")
        .accessibilityAdjustableAction { direction in
            let next = M.clamp(frame.p + (direction == .increment ? 1 : -1) * Double(S.step * 5) / Double(S.maxValue - S.minValue))
            mode = .released(p: next, a0: 0, grow0: 0, at: .now)
            onChange?(Self.amount(at: next))
            touches += 1
        }
    }

    private func slider(frame: PriceGlyphScatterSliderFrame) -> some View {
        let size = S.thumb + (S.thumbGrown - S.thumb) * frame.grow
        let x = S.inset + frame.p * S.trackWidth
        let cy = S.rowHeight / 2
        return ZStack(alignment: .topLeading) {
            Capsule().fill(P.trackRest).frame(width: S.trackWidth, height: S.trackHeight)
                .offset(x: S.inset, y: cy - S.trackHeight / 2)
            Capsule().fill(P.purple).frame(width: max(S.trackHeight, frame.p * S.trackWidth), height: S.trackHeight)
                .offset(x: S.inset, y: cy - S.trackHeight / 2)
            Circle().fill(P.card)
                .frame(width: size, height: size)
                .shadow(color: P.purple.opacity(0.35), radius: 4, y: 3)
                .overlay(Circle().fill(P.purple).padding(S.ring))
                .offset(x: x - size / 2, y: cy - size / 2)
        }
        .frame(width: S.inset + S.trackWidth + S.priceGap + S.priceWidth, height: S.rowHeight, alignment: .topLeading)
        .contentShape(Rectangle().inset(by: -12))
        .gesture(drag(trackX0: S.inset))
    }

    /// The price as separate glyphs, laid out right to left; each leaves its place by the amplitude.
    private func readout(amount: Int, a: Double) -> some View {
        let digits = Array(String(amount))
        let digitWidth = Self.glyphWidth("0", size: S.priceSize)
        let dollarWidth = Self.glyphWidth("$", size: S.dollarSize)
        return HStack(spacing: 0) {
            glyph("$", width: dollarWidth, size: S.dollarSize, color: P.mute, factor: .dollar, a: a)
            ForEach(Array(digits.enumerated()), id: \.offset) { index, ch in
                let fromRight = digits.count - 1 - index
                glyph(String(ch), width: digitWidth, size: S.priceSize, color: P.ink,
                      factor: PriceGlyphScatterSliderGlyphFactor.digits[min(fromRight, PriceGlyphScatterSliderGlyphFactor.digits.count - 1)], a: a)
            }
        }
    }

    private func glyph(_ text: String, width: Double, size: Double, color: Color, factor: PriceGlyphScatterSliderGlyphFactor, a: Double) -> some View {
        let m = abs(a)
        return Text(text).font(.system(size: size, weight: .semibold)).monospacedDigit().foregroundStyle(color)
            .frame(width: width, height: S.rowHeight)
            .rotationEffect(.degrees(S.scatterRotation * a * factor.rotation))
            .offset(x: -S.scatterX * a * factor.x, y: S.scatterY * m * factor.y)
            .opacity(1 - 0.15 * m)
    }

    /// Where a day's money goes, following the slider.
    private func tiles(amount: Int, width: Double) -> some View {
        let tileWidth = (width - S.tileGap * 2) / 3
        return HStack(spacing: S.tileGap) {
            ForEach(Array(S.shares.enumerated()), id: \.offset) { _, item in
                VStack(alignment: .leading, spacing: 0) {
                    Circle().fill(Color(hex: 0xEEEDFB)).frame(width: 28, height: 28)
                        .overlay(Image(systemName: item.icon).font(.system(size: 12, weight: .semibold)).foregroundStyle(P.purple))
                    Spacer(minLength: 0)
                    Text(item.label).font(.system(size: 12)).foregroundStyle(P.mute).lineLimit(1)
                    Text("$\(Int((Double(amount) * item.share).rounded()))").font(.system(size: 17, weight: .semibold)).monospacedDigit().foregroundStyle(P.ink).lineLimit(1)
                        .padding(.top, 2)
                }
                .padding(14)
                .frame(width: tileWidth, height: S.tileHeight, alignment: .topLeading)
                .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(P.card).shadow(color: P.shadow.opacity(0.08), radius: 8, y: 4))
            }
        }
        .frame(width: width, height: S.tileHeight)
    }

    /// The press holds the block from the touch down to the lift or cancel, which arms the 2 s resume; only a completed
    /// click submits. An activation without a press (VoiceOver) pauses and arms from the action itself.
    private func button(width: Double) -> some View {
        Button {
            pauseLoop()
            touches += 1
            onSubmit?(Self.amount(at: frame(at: .now).p))
        } label: {
            Text(buttonLabel).font(.system(size: 16, weight: .semibold)).foregroundStyle(.white)
                .frame(width: width, height: S.buttonHeight)
                .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(P.purple))
        }
        .buttonStyle(PressStyle { pressed in
            pressing = pressed
            if pressed { pauseLoop() } else { touches += 1 }
        })
    }

    private struct PressStyle: ButtonStyle {
        var onPressChange: (Bool) -> Void
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.opacity(configuration.isPressed ? 0.8 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
                .onChange(of: configuration.isPressed) { _, pressed in onPressChange(pressed) }
        }
    }

    private static func glyphWidth(_ text: String, size: Double) -> Double {
        let font = UIFont.monospacedDigitSystemFont(ofSize: size, weight: .semibold)
        return ceil((text as NSString).size(withAttributes: [.font: font]).width)
    }

    private static func grouped(_ value: Int) -> String {
        let formatter = NumberFormatter()
        formatter.numberStyle = .decimal
        formatter.groupingSeparator = ","
        return formatter.string(from: NSNumber(value: value)) ?? String(value)
    }
}

#Preview { PriceGlyphScatterSlider(autoPlay: true) }
