import SwiftUI

enum MergingStepDotsIcon {
    case sparkles
    case bell
    case seal
}

struct MergingStepDotsStep: Equatable {
    var title: String
    /// Two lines, split with a line break.
    var body: String
    var icon: MergingStepDotsIcon
    /// The icon's colour and the soft colour of the tile behind it.
    var tint: Color
    var tile: Color

    static let samples = [
        MergingStepDotsStep(title: "Set it up once", body: "Three quick steps and\neverything is ready for you.", icon: .sparkles, tint: Color(hex: 0x0b6cf9), tile: Color(hex: 0xe8f1ff)),
        MergingStepDotsStep(title: "Stay in the loop", body: "Only the nudges you ask for,\nnever the noise.", icon: .bell, tint: Color(hex: 0x1cbd51), tile: Color(hex: 0xe7f8ee)),
        MergingStepDotsStep(title: "You're all set", body: "Tap finish and\nstart exploring.", icon: .seal, tint: Color(hex: 0xf59e0b), tile: Color(hex: 0xfff4e5)),
    ]
}

/// The controls of an onboarding flow, with a page per step above them. The pager is one green pill
/// that stretches from the first dot to the current one, swallowing the dots on its way; "Back"
/// comes out from under the main button, which gives it room, and on the last step the button's
/// label changes to "Finish" behind a check that pops in.
struct MergingStepDots: View {
    /// The pages, two or more; the last one turns the main button into "Finish".
    var steps = MergingStepDotsStep.samples
    var continueLabel = "Continue"
    var backLabel = "Back"
    var finishLabel = "Finish"
    /// Goes through the steps and back by itself every `loop` seconds.
    var autoPlay = false
    /// Called with the step the flow moved to.
    var onStep: ((Int) -> Void)? = nil
    /// Called when "Finish" is pressed.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var step = 0
    /// The step, on its way from one to the next: the pill and the pages follow it.
    @State private var progress = Glide(0, M.dotsSpring)
    /// 0 with the main button alone, 1 once it has made room for "Back".
    @State private var bar = Glide(0, M.barSpring)
    /// 0 for "Continue", 1 for "Finish".
    @State private var swap = Tween(0)
    @State private var check = Glide(0, M.popSpring)
    /// 0 to 1 once after "Finish": the pill flashes and the pager hops.
    @State private var party = Tween(1)
    /// A button held down by the autoPlay loop.
    @State private var autoPressed: Pressed? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// The clock runs while something is on its way, and for a moment after.
    @State private var moving = false
    @State private var moves = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var doneHaptic = UINotificationFeedbackGenerator()

    private enum Pressed {
        case next
        case back
    }

    private var last: Int { steps.count - 1 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let bottom = max(insets.bottom, 12) + Metrics.bottom
            let rowWidth = proxy.size.width - Metrics.side * 2

            TimelineView(.animation(paused: !moving)) { timeline in
                let p = progress.value(at: timeline.date)
                let bump = sin(.pi * party.value(at: timeline.date))

                ZStack {
                    Palette.background.ignoresSafeArea()

                    VStack(spacing: 0) {
                        pages(p)
                        pager(p, bump: bump)
                            .padding(.bottom, Metrics.pagerGap - Metrics.pill / 2)
                        buttons(
                            bar: bar.value(at: timeline.date), swap: swap.value(at: timeline.date),
                            check: max(0, check.value(at: timeline.date)), width: rowWidth
                        )
                    }
                    .padding(.bottom, bottom - insets.bottom)
                }
            }
        }
        .preferredColorScheme(.light)
        .onAppear { tapHaptic.prepare() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
        .task(id: moves) {
            guard moves > 0 else { return }
            try? await Task.sleep(for: .seconds(M.settle))
            if !Task.isCancelled { moving = false }
        }
    }

    // MARK: The parts

    /// The pages, one over the other: a page is in place when the progress is on its index, and
    /// fades out half a step away.
    private func pages(_ p: Double) -> some View {
        ZStack {
            ForEach(steps.indices, id: \.self) { index in
                let by = Double(index) - p
                VStack(spacing: 0) {
                    MergingStepDotsIconView(icon: steps[index].icon, tint: steps[index].tint, tile: steps[index].tile)
                        .frame(width: Metrics.icon, height: Metrics.icon)
                        .scaleEffect(reduceMotion ? 1 : 1 - Metrics.zoom * min(1, max(-1, by)))
                        .frame(width: Metrics.tile, height: Metrics.tile)
                        .background(RoundedRectangle(cornerRadius: Metrics.tileRadius, style: .continuous).fill(steps[index].tile))
                    Text(steps[index].title)
                        .font(.system(size: 28, weight: .bold))
                        .foregroundStyle(Palette.ink)
                        .lineLimit(1)
                        .frame(height: 34)
                        .padding(.top, 36)
                    Text(steps[index].body)
                        .font(.system(size: 16))
                        .lineSpacing(Metrics.bodyLeading)
                        .padding(.vertical, Metrics.bodyLeading / 2)
                        .multilineTextAlignment(.center)
                        .foregroundStyle(Palette.mute)
                        .lineLimit(2)
                        .padding(.top, 12)
                }
                .opacity(min(1, max(0, 1 - 2 * abs(by))))
                .offset(x: reduceMotion ? 0 : Metrics.slide * by)
                .accessibilityElement(children: .combine)
                .accessibilityHidden(index != step)
            }
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .contentShape(Rectangle())
        // A swipe over the page: on to the next step, or back; it does not finish the flow.
        .gesture(
            DragGesture(minimumDistance: 16).onEnded { value in
                if value.translation.width < -M.swipe || value.velocity.width < -M.swipeSpeed {
                    swiped(1)
                } else if value.translation.width > M.swipe || value.velocity.width > M.swipeSpeed {
                    swiped(-1)
                }
            }
        )
    }

    /// The dots, and the one green pill that reaches from the first to the current one.
    private func pager(_ p: Double, bump: Double) -> some View {
        let width = Metrics.pill + Metrics.spacing * Double(last)
        return Color.clear
            .frame(width: width, height: Metrics.pill)
            .overlay {
                Canvas { context, _ in
                    let pad = Metrics.pagerPad
                    let pill = CGRect(x: pad, y: pad, width: max(Metrics.dot * 2, Metrics.pill + Metrics.spacing * p), height: Metrics.pill)
                    context.fill(Path(roundedRect: pill, cornerRadius: Metrics.pill / 2), with: .color(Palette.green.mixed(Palette.greenBright, bump).color))
                    for index in steps.indices {
                        // The pill's end is half a step short of the next dot when it sets off, and covers it a quarter later.
                        let reached = index == 0 ? 1 : min(1, max(0, (p - Double(index) + 0.5) / 0.25))
                        let centre = CGPoint(x: pad + Metrics.pill / 2 + Metrics.spacing * Double(index), y: pad + Metrics.pill / 2)
                        let dot = CGRect(x: centre.x - Metrics.dot / 2, y: centre.y - Metrics.dot / 2, width: Metrics.dot, height: Metrics.dot)
                        context.fill(Path(ellipseIn: dot), with: .color(Palette.dot.mixed(Palette.white, reached).color))
                    }
                }
                .frame(width: width + Metrics.pagerPad * 2, height: Metrics.pill + Metrics.pagerPad * 2)
            }
            .offset(y: -Metrics.hop * bump)
            .accessibilityElement()
            .accessibilityLabel("Step \(step + 1) of \(steps.count)")
    }

    private func buttons(bar: Double, swap: Double, check: Double, width: Double) -> some View {
        let shift = (Metrics.backWidth + Metrics.buttonGap) * bar
        // A label leaves upwards and its successor arrives from below, both through a blur.
        let gone = min(1, max(0, swap / M.swapOut))
        let here = min(1, max(0, (swap - M.swapIn) / (1 - M.swapIn)))
        return ZStack(alignment: .topLeading) {
            // Under the main button, which hides it until it has moved over.
            Button(action: back) {
                Text(backLabel)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Palette.ink)
                    .lineLimit(1)
                    .frame(width: Metrics.backWidth, height: Metrics.button)
            }
            .buttonStyle(PillStyle(fill: Palette.grey, pressedFill: Palette.greyPressed, held: autoPressed == .back))
            .scaleEffect(Metrics.backScale + (1 - Metrics.backScale) * bar)
            .opacity(min(1, max(0, bar * 1.5)))
            .allowsHitTesting(step > 0)
            .accessibilityHidden(step == 0)

            Button(action: forward) {
                ZStack {
                    Text(continueLabel)
                        .opacity(1 - gone)
                        .blur(radius: Metrics.labelBlur * gone)
                        .offset(y: -Metrics.labelSlide * gone)
                    HStack(spacing: 8) {
                        MergingStepDotsCheck()
                            .stroke(Palette.blue, style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                            .frame(width: Metrics.checkDisc, height: Metrics.checkDisc)
                            .background(Circle().fill(.white))
                            .scaleEffect(check)
                        Text(finishLabel)
                    }
                    .opacity(here)
                    .blur(radius: Metrics.labelBlur * (1 - here))
                    .offset(y: Metrics.labelSlide * (1 - here))
                }
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(width: max(0, width - shift), height: Metrics.button)
            }
            .buttonStyle(PillStyle(fill: Palette.blue, pressedFill: Palette.bluePressed, held: autoPressed == .next))
            .accessibilityLabel(step == last ? finishLabel : continueLabel)
            // Placed by layout, not by an offset: an offset button still takes the touches of the place it left.
            .padding(.leading, shift)
        }
        .frame(width: width, height: Metrics.button, alignment: .topLeading)
    }

    // MARK: Behaviour

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoPressed = nil
        touches += 1
    }

    /// Keeps the clock running until what has just set off has settled.
    private func stir() {
        moving = true
        moves += 1
    }

    /// Moves to a step. `date` is when the move starts: the loop passes the moment it was due,
    /// so that every round moves alike to the frame.
    private func goTo(_ to: Int, at date: Date = .now) {
        let next = min(last, max(0, to))
        guard next != step else { return }
        step = next
        let open = next > 0 ? 1.0 : 0.0
        let done = next == last ? 1.0 : 0.0
        progress.go(to: Double(next), at: date, eased: reduceMotion ? M.jump : nil)
        bar.go(to: open, at: date, eased: reduceMotion ? M.jump : nil)
        swap.retarget(done, at: date, duration: M.swap, curve: .linear)
        check.go(to: done, at: date, eased: next == last && !reduceMotion ? nil : M.popOut)
        stir()
        onStep?(next)
    }

    private func forward() {
        touched()
        if step < last {
            tapHaptic.impactOccurred()
            goTo(step + 1)
            return
        }
        doneHaptic.notificationOccurred(.success)
        if !reduceMotion {
            party = Tween(0)
            party.retarget(1, duration: M.party, curve: .linear)
            stir()
        }
        onDone?()
    }

    private func back() {
        touched()
        tapHaptic.impactOccurred()
        goTo(step - 1)
    }

    private func swiped(_ by: Int) {
        touched()
        guard step + by >= 0, step + by <= last else { return }
        tapHaptic.impactOccurred()
        goTo(step + by)
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            goTo(0)
        }
        // One round after another, timed from one origin so that rounds do not drift: each button
        // is pressed, then acts.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        let home = M.first + Double(last - 1) * M.between + M.hold
        func reached(_ seconds: Double) async -> Bool {
            try? await clock.sleep(until: origin + .seconds(seconds))
            return !Task.isCancelled
        }
        while !Task.isCancelled {
            for k in 0..<last {
                let due = M.first + Double(k) * M.between
                guard await reached(due - M.pressFor) else { return }
                autoPressed = .next
                guard await reached(due) else { return }
                autoPressed = nil
                goTo(k + 1, at: began + due)
            }
            guard await reached(home - M.pressFor) else { return }
            autoPressed = .back
            guard await reached(home) else { return }
            autoPressed = nil
            goTo(0, at: began + home)
            guard await reached(home + M.after) else { return }
            origin += .seconds(home + M.after)
            began += home + M.after
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct PillStyle: ButtonStyle {
        let fill: Color
        let pressedFill: Color
        let held: Bool

        func makeBody(configuration: Configuration) -> some View {
            let pressed = configuration.isPressed || held
            configuration.label
                .background(Capsule().fill(pressed ? pressedFill : fill))
                .contentShape(Capsule())
                .animation(.easeOut(duration: 0.1), value: pressed)
        }
    }
}

/// A number on its way to a target, read from the time since it set off: a spring that keeps the
/// speed it had when it is sent somewhere else, or a plain ease.
private struct Glide {
    struct Spring {
        let stiffness: Double
        let damping: Double
    }

    private var from: Double
    private var to: Double
    private var velocity = 0.0
    private var start = Date.distantPast
    private let spring: Spring
    /// The length of the ease when the move is not a spring.
    private var eased: Double? = nil

    init(_ value: Double, _ spring: Spring) {
        from = value
        to = value
        self.spring = spring
    }

    func value(at date: Date) -> Double {
        let t = max(0, date.timeIntervalSince(start))
        if let eased {
            let u = min(t / eased, 1)
            return from + (to - from) * (u < 0.5 ? 2 * u * u : 1 - 2 * (1 - u) * (1 - u))
        }
        let (decay, cosine, sine, omega1, pull) = terms(t)
        return to + decay * ((from - to) * cosine + pull / omega1 * sine)
    }

    private func speed(at date: Date) -> Double {
        guard eased == nil else { return 0 }
        let t = max(0, date.timeIntervalSince(start))
        let (decay, cosine, sine, omega1, pull) = terms(t)
        let away = from - to
        let rate = spring.damping / 2
        return decay * ((pull - rate * away) * cosine - (rate * pull / omega1 + away * omega1) * sine)
    }

    /// The parts of the spring's motion at `t`, for a mass of 1: its decay, the turn of its swing,
    /// the swing's rate, and the start speed plus what the damping adds to it.
    private func terms(_ t: Double) -> (Double, Double, Double, Double, Double) {
        let rate = spring.damping / 2
        let omega1 = (spring.stiffness - rate * rate).squareRoot()
        return (exp(-rate * t), cos(omega1 * t), sin(omega1 * t), omega1, velocity + rate * (from - to))
    }

    mutating func go(to target: Double, at date: Date, eased: Double? = nil) {
        let now = value(at: date)
        velocity = speed(at: date)
        from = now
        to = target
        start = date
        self.eased = eased
    }
}

private enum M {
    /// autoPlay: "Continue" at `first` and every `between` after it, `hold` on the last step, then back to the first.
    static let loop = 8.0
    static let first = 1.6
    static let between = 2.0
    static let hold = 2.4
    /// What is left of the loop once it is back on the first step.
    static let after = 2.0
    /// A button the loop presses looks pressed this long before it acts.
    static let pressFor = 0.14
    /// After a touch autoPlay waits this long before it goes back to the first step.
    static let userHold = 3.0

    /// The pill and the pages: it overshoots by about 3 points of the 32 between two dots.
    static let dotsSpring = Glide.Spring(stiffness: 170, damping: 16)
    /// The main button's left edge and "Back": the same pace, less bounce over the longer way.
    static let barSpring = Glide.Spring(stiffness: 170, damping: 19.5)
    /// The check's disc pops to 1.15 before it settles.
    static let popSpring = Glide.Spring(stiffness: 300, damping: 18)
    static let popOut = 0.15
    /// The labels change over `swap`: the old one is gone by `swapOut` of it, the new one starts at `swapIn`.
    static let swap = 0.3
    static let swapOut = 0.5
    static let swapIn = 1.0 / 3.0
    static let party = 0.5
    /// Under reduced motion everything changes in one short step.
    static let jump = 0.2
    /// How long the clock keeps running after a change: the springs have settled by then.
    static let settle = 1.4
    static let swipe = 40.0
    static let swipeSpeed = 500.0
}

/// A colour as three numbers, so that two can be mixed.
private struct Tone {
    var r: Double
    var g: Double
    var b: Double

    init(_ hex: UInt32) {
        r = Double((hex >> 16) & 0xff) / 255
        g = Double((hex >> 8) & 0xff) / 255
        b = Double(hex & 0xff) / 255
    }

    var color: Color { Color(.sRGB, red: r, green: g, blue: b) }

    func mixed(_ other: Tone, _ amount: Double) -> Tone {
        var tone = self
        tone.r += (other.r - r) * amount
        tone.g += (other.g - g) * amount
        tone.b += (other.b - b) * amount
        return tone
    }
}

private enum Palette {
    static let background = Color(hex: 0xfdfdfd)
    static let ink = Color(hex: 0x111111)
    static let mute = Color(hex: 0x6b7280)
    static let green = Tone(0x1cbd51)
    /// The pill for a moment when the flow is finished.
    static let greenBright = Tone(0x3ec76b)
    static let dot = Tone(0xd9d9d9)
    static let white = Tone(0xffffff)
    static let blue = Color(hex: 0x0b6cf9)
    static let bluePressed = Color(hex: 0x0957c8)
    static let grey = Color(hex: 0xf2f2f2)
    static let greyPressed = Color(hex: 0xe2e2e2)
}

private enum Metrics {
    /// The pager: dots `spacing` apart, and one green pill that reaches from the first to the current one.
    static let pill = 24.0
    static let dot = 8.0
    static let spacing = 32.0
    /// Room round the pill for the spring's overshoot.
    static let pagerPad = 8.0
    /// From the pager's middle down to the buttons.
    static let pagerGap = 49.0
    static let button = 56.0
    static let side = 26.0
    static let bottom = 22.0
    static let backWidth = 90.0
    static let buttonGap = 8.0
    /// "Back" comes out from under the main button at this size.
    static let backScale = 0.6
    static let checkDisc = 18.0
    static let labelSlide = 6.0
    static let labelBlur = 6.0
    static let tile = 168.0
    static let tileRadius = 44.0
    static let icon = 72.0
    /// What a 16 point line lacks to be 24 tall.
    static let bodyLeading = 24 - UIFont.systemFont(ofSize: 16).lineHeight
    /// A page next to the current one sits this far to its side, and its icon is this much
    /// smaller (after it) or larger (before it).
    static let slide = 40.0
    static let zoom = 0.28
    /// The whole pager hops when the flow is finished.
    static let hop = 10.0
}

/// A check in an 18 × 18 box.
struct MergingStepDotsCheck: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 18
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 5.2 * s, y: rect.minY + 9.3 * s))
        path.addLine(to: CGPoint(x: rect.minX + 7.9 * s, y: rect.minY + 12 * s))
        path.addLine(to: CGPoint(x: rect.minX + 12.9 * s, y: rect.minY + 6.5 * s))
        return path
    }
}

/// The icons, drawn in a 72 × 72 box.
struct MergingStepDotsIconView: View {
    let icon: MergingStepDotsIcon
    let tint: Color
    let tile: Color

    /// Sparkles: three four-point stars, each x, y and radius; `pinch` is how far the sides of a star are pulled in.
    private static let stars: [(Double, Double, Double)] = [(30, 41, 25), (55, 18, 11), (57, 55, 8)]
    private static let pinch = 0.16
    /// The bell's badge: a dot with a gap of the tile's colour round it.
    private static let badge = (x: 51.0, y: 19.0, dot: 8.5, gap: 12.5)
    /// The seal: a disc with ten bumps on its edge, and a check cut out of it.
    private static let sealCore = 24.0
    private static let sealBumps = 10
    private static let sealBump = 8.6
    private static let sealBumpAt = 21.5

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / 72, y: size.height / 72)
            switch icon {
            case .sparkles:
                for (cx, cy, r) in Self.stars { context.fill(Self.star(cx, cy, r), with: .color(tint)) }
            case .bell:
                context.fill(Self.bell, with: .color(tint))
                context.fill(Self.disc(Self.badge.x, Self.badge.y, Self.badge.gap), with: .color(tile))
                context.fill(Self.disc(Self.badge.x, Self.badge.y, Self.badge.dot), with: .color(tint))
            case .seal:
                context.fill(Self.disc(36, 36, Self.sealCore), with: .color(tint))
                for bump in 0..<Self.sealBumps {
                    let angle = Double(bump) / Double(Self.sealBumps) * tau
                    context.fill(Self.disc(36 + Self.sealBumpAt * cos(angle), 36 + Self.sealBumpAt * sin(angle), Self.sealBump), with: .color(tint))
                }
                var check = Path()
                check.move(to: CGPoint(x: 25, y: 37.5))
                check.addLine(to: CGPoint(x: 32.5, y: 45))
                check.addLine(to: CGPoint(x: 47.5, y: 28.5))
                context.stroke(check, with: .color(tile), style: StrokeStyle(lineWidth: 5, lineCap: .round, lineJoin: .round))
            }
        }
        .accessibilityHidden(true)
    }

    private static func disc(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
    }

    private static func star(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
        let k = r * pinch
        var path = Path()
        path.move(to: CGPoint(x: cx, y: cy - r))
        path.addQuadCurve(to: CGPoint(x: cx + r, y: cy), control: CGPoint(x: cx + k, y: cy - k))
        path.addQuadCurve(to: CGPoint(x: cx, y: cy + r), control: CGPoint(x: cx + k, y: cy + k))
        path.addQuadCurve(to: CGPoint(x: cx - r, y: cy), control: CGPoint(x: cx - k, y: cy + k))
        path.addQuadCurve(to: CGPoint(x: cx, y: cy - r), control: CGPoint(x: cx - k, y: cy - k))
        path.closeSubpath()
        return path
    }

    /// The bell and, under it, its clapper.
    private static let bell: Path = {
        func point(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }
        var path = Path()
        path.move(to: point(36, 10.5))
        path.addCurve(to: point(18, 28.8), control1: point(25.2, 10.5), control2: point(18, 18.6))
        path.addLine(to: point(18, 39.6))
        path.addCurve(to: point(13.8, 48.3), control1: point(18, 42.9), control2: point(16.5, 45.6))
        path.addCurve(to: point(15.6, 52.5), control1: point(12.3, 49.8), control2: point(13.2, 52.5))
        path.addLine(to: point(56.4, 52.5))
        path.addCurve(to: point(58.2, 48.3), control1: point(58.8, 52.5), control2: point(59.7, 49.8))
        path.addCurve(to: point(54, 39.6), control1: point(55.5, 45.6), control2: point(54, 42.9))
        path.addLine(to: point(54, 28.8))
        path.addCurve(to: point(36, 10.5), control1: point(54, 18.6), control2: point(46.8, 10.5))
        path.closeSubpath()
        path.move(to: point(29.4, 57))
        path.addCurve(to: point(36, 61.8), control1: point(30.3, 60), control2: point(33, 61.8))
        path.addCurve(to: point(42.6, 57), control1: point(39, 61.8), control2: point(41.7, 60))
        path.closeSubpath()
        return path
    }()
}

#Preview {
    MergingStepDots(autoPlay: true)
}
