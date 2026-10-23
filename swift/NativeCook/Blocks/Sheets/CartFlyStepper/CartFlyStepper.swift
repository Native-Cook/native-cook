import SwiftUI
import UIKit

struct CartFlyStepperOption: Hashable {
    let name: String
    let price: String
}

/// A product sheet whose "+" sends a small token along an arc into the basket for every item
/// added: tokens overlap in a burst, the basket kicks as each lands, and both counts roll.
struct CartFlyStepper: View {
    var title = "Ginger-Soy Salmon Fillet"
    var description = "edamame, cucumber, avocado,\nherbs, white rice"
    var price = "$9.95"
    var optionsTitle = "Options"
    var optionalLabel = "Optional"
    var options = [
        CartFlyStepperOption(name: "Vegan", price: "+ 0,00"),
        CartFlyStepperOption(name: "Extra avocado", price: "+ 1,50"),
        CartFlyStepperOption(name: "Chicken instead of salmon", price: "+ 2,00"),
    ]
    /// Taps "+" by itself every `loop` seconds, singly and in held bursts, and empties the basket at the end.
    var autoPlay = false
    /// Called with the stepper's count whenever it changes by hand.
    var onChange: (Int) -> Void = { _ in }

    static let loop = 10.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// autoPlay's round start, or nil when the events made by hand are shown.
    @State private var loopStart: Double? = nil
    @State private var hand: [CartFlyStepperScene.Event] = []
    @State private var baseStep = 0
    @State private var baseBasket = 0
    @State private var held = false
    @State private var settled = true
    @State private var checked: Set<Int> = []
    @State private var repeatTask: Task<Void, Never>? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var selectionHaptic = UISelectionFeedbackGenerator()

    private typealias S = CartFlyStepperScene

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: loopStart == nil && settled)) { timeline in
                sheet(screen: screen, insets: insets, now: timeline.date.timeIntervalSinceReferenceDate)
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loopTask() }
        .task(id: hand.count) {
            try? await Task.sleep(for: .seconds(S.settle))
            if !Task.isCancelled { settled = true }
        }
        .onDisappear { repeatTask?.cancel() }
    }

    // MARK: Layout

    private func sheet(screen: CGSize, insets: EdgeInsets, now: Double) -> some View {
        let scene = S.scene(at: now, events: events(at: now), baseStep: baseStep, baseBasket: baseBasket)
        let sheetTop = insets.top + S.sheetGap
        let sheetHeight = screen.height - sheetTop
        let panel = max(S.panelMin, min(S.panelMax, screen.height * S.panelShare))
        let barY = sheetHeight - max(insets.bottom, S.barMargin) - 6 - S.bar / 2
        let lift = reduceMotion ? 0 : scene.lift
        let stepP = S.clamp((now - scene.stepAt) / scene.stepFor)
        let basketP = S.clamp((now - scene.basketAt) / S.roll)

        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                Capsule()
                    .fill(Color(hex: S.grabber))
                    .frame(width: S.grabberWidth, height: S.grabberHeight)
                    .frame(maxWidth: .infinity)
                    .padding(.top, 8)
                Canvas { context, size in
                    CartFlyStepperPlate.draw(&context, cx: size.width / 2, cy: size.height / 2, d: panel * S.plateShare, alpha: 1)
                }
                .frame(height: panel)
                .background(Color(hex: S.panel), in: RoundedRectangle(cornerRadius: S.panelRadius, style: .continuous))
                .padding(.top, 11)
                text(title, 20, 26, .semibold, Color(hex: S.ink)).padding(.top, 20)
                text(description, 14, 19, .regular, Color(hex: S.body)).padding(.top, 4)
                text(price, 16, 22, .medium, Color(hex: S.ink)).padding(.top, 8)
                Rectangle().fill(Color(hex: S.divider)).frame(height: 1).padding(.top, 16)
                HStack {
                    text(optionsTitle, 18, 24, .semibold, Color(hex: S.ink))
                    Spacer()
                    text(optionalLabel, 11, 14, .regular, Color(hex: S.pillInk))
                        .padding(.horizontal, 8)
                        .frame(height: 20)
                        .background(Color(hex: S.pill), in: Capsule())
                }
                .padding(.top, 16)
                ForEach(Array(options.enumerated()), id: \.offset) { i, option in
                    row(option, on: checked.contains(i)) { toggle(i) }
                        .padding(.top, 12)
                }
            }
            .padding(.horizontal, S.side)

            // The bottom bar, over a fade that lets the list run out under it.
            VStack(spacing: 0) {
                LinearGradient(colors: [.white.opacity(0), .white], startPoint: .top, endPoint: .bottom).frame(height: S.fade)
                Color.white
            }
            .frame(height: sheetHeight - (barY - S.bar / 2 - S.fade))
            .offset(y: barY - S.bar / 2 - S.fade)
            .allowsHitTesting(false)

            HStack(spacing: 0) {
                button(.remove)
                rolling(scene.stepFrom, scene.stepTo, stepP, size: 18).frame(maxWidth: .infinity)
                button(.add)
            }
            .frame(width: S.stepperWidth, height: S.bar)
            .background(Color(hex: S.stepper), in: Capsule())
            .offset(x: S.side, y: barY - S.bar / 2)

            rolling(scene.basketFrom, scene.basketTo, basketP, size: 16)
                .offset(x: S.basketX + S.basketCountGap, y: barY - S.countLine / 2)

            Canvas { context, _ in
                S.drawOverlay(&context, scene: scene, barY: barY, reduced: reduceMotion)
            }
            .allowsHitTesting(false)
        }
        .frame(width: screen.width, height: sheetHeight, alignment: .topLeading)
        .background(Color.white, in: UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous))
        .clipShape(UnevenRoundedRectangle(topLeadingRadius: S.sheetRadius, topTrailingRadius: S.sheetRadius, style: .continuous))
        .scaleEffect(1 - S.liftScale * lift)
        .offset(y: sheetTop - S.liftRise * lift)
    }

    private func text(_ string: String, _ size: Double, _ line: Double, _ weight: Font.Weight, _ color: Color) -> some View {
        Text(string)
            .font(.system(size: size, weight: weight))
            .lineSpacing(line - UIFont.systemFont(ofSize: size, weight: weight.uiWeight).lineHeight)
            .foregroundStyle(color)
            .fixedSize(horizontal: false, vertical: true)
    }

    private func row(_ option: CartFlyStepperOption, on: Bool, action: @escaping () -> Void) -> some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                text(option.name, 14, 18, .semibold, Color(hex: S.ink))
                text(option.price, 12, 16, .regular, Color(hex: S.mute))
            }
            Spacer()
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .strokeBorder(Color(hex: on ? S.ink : S.checkEdge), lineWidth: 1.5)
                .background(on ? Color(hex: S.ink) : .clear, in: RoundedRectangle(cornerRadius: 6, style: .continuous))
                .overlay { if on { Image(systemName: "checkmark").font(.system(size: 11, weight: .bold)).foregroundStyle(.white) } }
                .frame(width: S.check, height: S.check)
        }
        .padding(.horizontal, 16)
        .frame(height: S.rowHeight)
        .background(Color(hex: S.row), in: RoundedRectangle(cornerRadius: S.rowRadius, style: .continuous))
        .contentShape(Rectangle())
        .onTapGesture(perform: action)
        .accessibilityAddTraits(on ? [.isButton, .isSelected] : .isButton)
    }

    /// A number that rolls from `from` to `to`: up when it grows, down when it shrinks; a fade under reduced motion.
    private func rolling(_ from: Int, _ to: Int, _ p: Double, size: Double) -> some View {
        let dir = to >= from ? 1.0 : -1.0
        let shift = reduceMotion ? 0 : S.countLine
        return ZStack {
            if from != to {
                Text("\(from)")
                    .offset(y: -dir * shift * p)
                    .opacity(1 - p)
            }
            Text("\(to)")
                .offset(y: from != to ? dir * shift * (1 - p) : 0)
                .opacity(from != to ? p : 1)
        }
        .font(.system(size: size, weight: .medium))
        .foregroundStyle(Color(hex: S.ink))
        .frame(minWidth: 28, minHeight: S.countLine, maxHeight: S.countLine)
        .clipped()
    }

    private func button(_ kind: S.Kind) -> some View {
        Color.clear
            .frame(width: S.glyphInset * 2, height: S.bar)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in if !held { pressIn(kind) } }
                    .onEnded { _ in pressOut() }
            )
            .accessibilityElement()
            .accessibilityLabel(Text(kind == .add ? "Add one" : "Remove one"))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { press(kind) }
    }

    // MARK: Clock

    private func events(at now: Double) -> [S.Event] {
        guard let start = loopStart, now >= start else { return hand }
        return S.round(at: now, start: start)
    }

    /// A touch during autoPlay takes over: the round so far becomes the events made by hand.
    private func takeOver(_ now: Double) {
        touches += 1
        guard let start = loopStart else { return }
        if now >= start {
            hand = S.round(at: now, start: start).filter { $0.at <= now }
            baseStep = 0
            baseBasket = 0
        }
        loopStart = nil
        settled = false
    }

    /// Folds events whose animations are over into the counts, so the list stays short.
    private func fold(_ now: Double) {
        var step = baseStep
        var basket = baseBasket
        var i = 0
        while i < hand.count, hand[i].at < now - S.settle {
            switch hand[i].kind {
            case .add:
                step += 1
                basket += 1
            case .remove: step = max(0, step - 1)
            case .reset:
                step = 0
                basket = 0
            case .lift: break
            }
            i += 1
        }
        guard i > 0 else { return }
        baseStep = step
        baseBasket = basket
        hand.removeFirst(i)
    }

    private func press(_ kind: S.Kind) {
        let now = Date.now.timeIntervalSinceReferenceDate
        takeOver(now)
        let before = S.scene(at: now, events: hand, baseStep: baseStep, baseBasket: baseBasket).stepTo
        if kind == .remove, before <= 0 { return }
        fold(now)
        hand.append(S.Event(at: now, kind: kind))
        settled = false
        tapHaptic.impactOccurred()
        onChange(kind == .add ? before + 1 : before - 1)
    }

    /// A press counts at once; held, it repeats after `holdAfter`, every `repeatEvery`.
    private func pressIn(_ kind: S.Kind) {
        held = true
        press(kind)
        repeatTask?.cancel()
        repeatTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(S.holdAfter), tolerance: .zero)
            while !Task.isCancelled {
                press(kind)
                try? await Task.sleep(for: .seconds(S.repeatEvery), tolerance: .zero)
            }
        }
    }

    private func pressOut() {
        repeatTask?.cancel()
        repeatTask = nil
        held = false
        touches += 1
    }

    private func toggle(_ i: Int) {
        takeOver(Date.now.timeIntervalSinceReferenceDate)
        selectionHaptic.selectionChanged()
        if checked.contains(i) { checked.remove(i) } else { checked.insert(i) }
    }

    /// autoPlay: a round every `loop` seconds; after a touch, the basket is emptied and the round starts again.
    private func loopTask() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(S.userHold))
            while held, !Task.isCancelled { try? await Task.sleep(for: .seconds(0.1)) }
        }
        guard !Task.isCancelled, loopStart == nil else { return }
        let now = Date.now.timeIntervalSinceReferenceDate
        let scene = S.scene(at: now, events: hand, baseStep: baseStep, baseBasket: baseBasket)
        if scene.stepTo != 0 || scene.basketTo != 0 {
            // Empty the basket first, then start the round again from rest.
            hand.append(S.Event(at: now, kind: .reset))
            settled = false
            loopStart = now + S.resetRoll + 0.2
        } else {
            hand = []
            baseStep = 0
            baseBasket = 0
            loopStart = now
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private extension Font.Weight {
    var uiWeight: UIFont.Weight {
        switch self {
        case .medium: .medium
        case .semibold: .semibold
        default: .regular
        }
    }
}

/// The block's numbers, and what the screen shows at a moment from the events so far.
enum CartFlyStepperScene {
    enum Kind { case add, remove, reset, lift }

    struct Event {
        let at: Double
        let kind: Kind
    }

    struct Moment {
        var stepFrom: Int
        var stepTo: Int
        var stepAt = -1e9
        var stepFor = CartFlyStepperScene.roll
        var basketFrom: Int
        var basketTo: Int
        var basketAt = -1e9
        var bump = 0.0
        var lift = 0.0
        var pressMinus = 0.0
        var pressPlus = 0.0
        var tokens: [Double] = []
    }

    // After a touch autoPlay waits this long, empties the basket, and starts over from rest.
    static let userHold = 3.0
    // Holding "+" or "−" repeats after holdAfter, every repeatEvery seconds.
    static let holdAfter = 0.4
    static let repeatEvery = 0.15

    // autoPlay's round, in seconds from its start: single taps, a held burst, more taps, a lift as the
    // last token lands, and the counts rolling back to 0.
    static let roundEvents: [(Double, Kind)] = [
        (0.8, .add), (1.4, .add),
        (2.1, .add), (2.28, .add), (2.46, .add), (2.64, .add), (2.82, .add),
        (3.6, .add), (4.1, .add),
        (4.7, .add), (4.88, .add), (5.06, .add),
        (5.6, .lift),
        (8.4, .reset),
    ]

    // Motion, in seconds.
    static let flight = 0.6
    static let pop = 0.15
    static let roll = 0.25
    static let resetRoll = 0.4
    static let pressFor = 0.3
    static let liftFor = 0.6
    static let bumpAmount = 0.32
    static let bumpDecay = 7.0
    static let bumpPeriod = 0.36
    static let bumpFor = 0.6
    // Events older than this have finished every animation they started, and are folded into the counts.
    static let settle = 1.5

    // Sizes in points.
    static let sheetGap = 10.0
    static let sheetRadius = 32.0
    static let side = 20.0
    static let grabberWidth = 36.0
    static let grabberHeight = 5.0
    static let panelShare = 0.3
    static let panelMin = 160.0
    static let panelMax = 280.0
    static let panelRadius = 22.0
    static let plateShare = 0.78
    static let rowHeight = 60.0
    static let rowRadius = 16.0
    static let check = 22.0
    static let bar = 56.0
    static let barMargin = 16.0
    static let fade = 48.0
    static let stepperWidth = 220.0
    static let glyphInset = 32.0
    static let glyph = 14.0
    static let glyphStroke = 2.0
    static let pressRadius = 20.0
    static let basketGap = 40.0
    static let basket = 24.0
    static let basketCountGap = 18.0
    static let token = 30.0
    static let tokenRadius = 9.0
    static let tokenPlate = 22.0
    static let tokenEndScale = 0.45
    // The flight's arc rises this far above the higher of its two ends, at its control point.
    static let arcLift = 120.0
    static let countLine = 24.0
    static let liftScale = 0.04
    static let liftRise = 10.0
    static var basketX: Double { side + stepperWidth + basketGap }

    static let grabber: UInt32 = 0xD0D0D4
    static let panel: UInt32 = 0xF3F3F5
    static let ink: UInt32 = 0x111111
    static let body: UInt32 = 0x555555
    static let mute: UInt32 = 0x888888
    static let pill: UInt32 = 0xEEEEEE
    static let pillInk: UInt32 = 0x666666
    static let row: UInt32 = 0xF2F2F4
    static let checkEdge: UInt32 = 0xC8C8CC
    static let divider: UInt32 = 0xECECEE
    static let stepper: UInt32 = 0xECECEE

    static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x }
    static func pressCurve(_ dt: Double) -> Double { dt < 0 || dt > pressFor ? 0 : sin(.pi * dt / pressFor) }

    /// The basket's kick when a token lands: up to about 1.25, then settling back to 1.
    static func kick(_ dt: Double) -> Double {
        dt < 0 || dt > bumpFor ? 0 : bumpAmount * exp(-bumpDecay * dt) * sin(2 * .pi * dt / bumpPeriod)
    }

    /// autoPlay's current round, at absolute times.
    static func round(at now: Double, start: Double) -> [Event] {
        let base = start + ((now - start) / CartFlyStepper.loop).rounded(.down) * CartFlyStepper.loop
        return roundEvents.map { Event(at: $0.0 + base, kind: $0.1) }
    }

    /// Everything the screen shows at `t`, from the events so far and the counts they started from.
    static func scene(at t: Double, events: [Event], baseStep: Int, baseBasket: Int) -> Moment {
        var s = Moment(stepFrom: baseStep, stepTo: baseStep, basketFrom: baseBasket, basketTo: baseBasket)
        var step = baseStep
        var basket = baseBasket
        for e in events {
            if e.at > t { break }
            let before = step
            switch e.kind {
            case .add:
                step += 1
                s.pressPlus = max(s.pressPlus, pressCurve(t - e.at))
                let landing = e.at + flight
                if landing <= t {
                    s.basketFrom = basket
                    basket += 1
                    s.basketTo = basket
                    s.basketAt = landing
                    s.bump += kick(t - landing)
                } else {
                    s.tokens.append(t - e.at)
                }
            case .remove:
                step = max(0, step - 1)
                s.pressMinus = max(s.pressMinus, pressCurve(t - e.at))
            case .reset:
                if basket != 0 {
                    s.basketFrom = basket
                    basket = 0
                    s.basketTo = 0
                    s.basketAt = e.at
                }
                step = 0
            case .lift:
                let x = clamp((t - e.at) / liftFor)
                s.lift = max(s.lift, x <= 0 || x >= 1 ? 0 : 0.5 - 0.5 * cos(2 * .pi * x))
            }
            if step != before {
                s.stepFrom = before
                s.stepTo = step
                s.stepAt = e.at
                s.stepFor = e.kind == .reset ? resetRoll : roll
            }
        }
        s.bump = min(max(s.bump, -0.3), 0.3)
        return s
    }

    /// A basket in a 24 × 24 box: handles, body, and two slats.
    private static let basketPath: Path = {
        var p = Path()
        p.move(to: CGPoint(x: 6, y: 10)); p.addLine(to: CGPoint(x: 9, y: 4))
        p.move(to: CGPoint(x: 18, y: 10)); p.addLine(to: CGPoint(x: 15, y: 4))
        p.move(to: CGPoint(x: 3, y: 10)); p.addLine(to: CGPoint(x: 21, y: 10))
        p.addLine(to: CGPoint(x: 19.2, y: 19.4))
        p.addQuadCurve(to: CGPoint(x: 17.3, y: 21), control: CGPoint(x: 18.9, y: 21))
        p.addLine(to: CGPoint(x: 6.7, y: 21))
        p.addQuadCurve(to: CGPoint(x: 4.8, y: 19.4), control: CGPoint(x: 5.1, y: 21))
        p.closeSubpath()
        p.move(to: CGPoint(x: 9.5, y: 13.5)); p.addLine(to: CGPoint(x: 9.5, y: 17.5))
        p.move(to: CGPoint(x: 14.5, y: 13.5)); p.addLine(to: CGPoint(x: 14.5, y: 17.5))
        return p
    }()

    /// The stepper's glyphs, the basket, and the tokens in flight, in sheet points.
    static func drawOverlay(_ context: inout GraphicsContext, scene s: Moment, barY: Double, reduced: Bool) {
        let inkColor = Color(hex: ink)
        let minusX = side + glyphInset
        let plusX = side + stepperWidth - glyphInset

        // "−" and "+", each pressed in when tapped.
        for (x, pressed, plus) in [(minusX, s.pressMinus, false), (plusX, s.pressPlus, true)] {
            if pressed > 0 {
                let r = pressRadius * (0.8 + 0.2 * pressed)
                context.fill(Path(ellipseIn: CGRect(x: x - r, y: barY - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.95 * pressed)))
            }
            let g = glyph / 2 * (1 - 0.12 * pressed)
            var mark = Path()
            mark.move(to: CGPoint(x: x - g, y: barY)); mark.addLine(to: CGPoint(x: x + g, y: barY))
            if plus { mark.move(to: CGPoint(x: x, y: barY - g)); mark.addLine(to: CGPoint(x: x, y: barY + g)) }
            context.stroke(mark, with: .color(inkColor), style: StrokeStyle(lineWidth: glyphStroke, lineCap: .round))
        }

        // The basket, kicked by each token that lands in it.
        let scale = reduced ? 1 : 1 + s.bump
        var bin = context
        bin.translateBy(x: basketX, y: barY)
        bin.scaleBy(x: scale, y: scale)
        bin.translateBy(x: -basket / 2, y: -basket / 2)
        bin.stroke(basketPath, with: .color(inkColor), style: StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round))

        // Tokens on their arcs from "+" to the basket.
        guard !reduced else { return }
        let (sx, sy, ex, ey) = (plusX, barY, basketX, barY)
        let cx = (sx + ex) / 2
        let cy = min(sy, ey) - arcLift
        for dt in s.tokens {
            let q = easeIn(clamp(dt / flight))
            let x = (1 - q) * (1 - q) * sx + 2 * (1 - q) * q * cx + q * q * ex
            let y = (1 - q) * (1 - q) * sy + 2 * (1 - q) * q * cy + q * q * ey
            let popped = easeOut(clamp(dt / pop))
            let k = (0.5 + 0.5 * popped) * (1 + (tokenEndScale - 1) * q)
            let alpha = clamp(dt / 0.06) * clamp((flight - dt) / 0.08)
            var piece = context
            piece.translateBy(x: x, y: y)
            piece.rotate(by: .degrees(-12 + 30 * q))
            piece.scaleBy(x: k, y: k)
            let box = CGRect(x: -token / 2, y: -token / 2, width: token, height: token)
            piece.drawLayer { layer in
                layer.addFilter(.blur(radius: 4))
                layer.fill(Path(roundedRect: box.offsetBy(dx: 0, dy: 2), cornerRadius: tokenRadius, style: .circular), with: .color(.black.opacity(0.18 * alpha)))
            }
            piece.fill(Path(roundedRect: box, cornerRadius: tokenRadius, style: .circular), with: .color(.white.opacity(alpha)))
            CartFlyStepperPlate.draw(&piece, cx: 0, cy: 0, d: tokenPlate, alpha: alpha)
        }
    }
}

#Preview {
    CartFlyStepper(autoPlay: true)
}
