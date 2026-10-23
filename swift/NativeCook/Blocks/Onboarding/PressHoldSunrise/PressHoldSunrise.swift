import SwiftUI

/// A welcome screen whose one button has to be held: it grows until it fills its disc, then
/// bursts over the screen and a tunnel of shapes, dark and orange in turn, carries the app from
/// the light welcome into its dark first page. Tapping the mark there goes back the same way.
struct PressHoldSunrise: View {
    /// The app's name under its mark.
    var name = "Motes."
    var tagline = "Tiny changes,\nremarkable results."
    /// Under the bolt, inside the button once it has grown.
    var buttonLabel = "Get Started"
    /// The line at the bottom of the welcome.
    var hint = "PRESS AND HOLD TO GET STARTED"
    /// The first page's heading.
    var heading = "Create a few impactful habits\nand adjust them over time."
    var primaryLabel = "Create first habit"
    var secondaryLabel = "Continue with an account"
    /// The button and the orange shapes, top to bottom.
    var accent = [Color(hex: 0xFBD28A), Color(hex: 0xF5A742)]
    /// Number of pages the pager's arc stands for; the first is the one shown.
    var pages = 5
    /// Holds the button by itself every `PressHoldSunriseMotion.loop` seconds, and comes back to the welcome.
    var autoPlay = false
    /// Called when the hold completes and the tunnel starts.
    var onDone: (() -> Void)? = nil
    var onPrimary: (() -> Void)? = nil
    var onSecondary: (() -> Void)? = nil
    /// Called when the mark on the first page is tapped and the welcome returns.
    var onBack: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var calm
    /// The scene time while nothing moves: 0 on the welcome, `atB` on the first page.
    @State private var rest = 0.0
    /// The scene on its way from one time to another.
    @State private var run: Run? = nil
    @State private var runTask: Task<Void, Never>? = nil
    /// The hold in progress: whose it is, for the haptic at its end and for a touch that interrupts.
    @State private var holder: Holder? = nil
    /// A finger is on the button, and one is on the screen.
    @State private var pressing = false
    @State private var touching = false
    @State private var dark = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var doneHaptic = UINotificationFeedbackGenerator()

    private enum Holder { case hand, auto }

    private struct Run {
        let from: Double
        let to: Double
        let start: Date
        let duration: Double
        /// An early release, which follows a spring; everything else runs at one second a second.
        let springy: Bool

        func scene(at date: Date) -> Double {
            let x = min(max(date.timeIntervalSince(start) / duration, 0), 1)
            return from + (to - from) * (springy ? M.releaseEase(x) : x)
        }
    }

    private var atB: Bool { run == nil && rest == M.atB }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let cx = size.width / 2
            let cy = (size.height * Metrics.discAt).rounded()
            let nightY = (size.height * Metrics.nightDiscAt).rounded()
            // The tunnel's shapes grow until they reach the corner furthest from the disc.
            let reach = hypot(cx, max(cy, size.height - cy)) * PressHoldSunriseShapes.reach
            let bottom = max(insets.bottom + 6, 20)
            let field = PressHoldSunriseHalftone(top: cy - Metrics.fieldAbove, bottom: cy + Metrics.fieldBelow)

            TimelineView(.animation(paused: run == nil)) { timeline in
                let s = run?.scene(at: timeline.date) ?? rest
                let page = M.pageAlpha(s, calm: calm)
                let disc = M.arrived(s, after: M.discAfter, length: M.discIn, calm: calm)
                let label = M.labelAlpha(s, calm: calm)
                let night = M.night(s, calm: calm)

                ZStack(alignment: .topLeading) {
                    Palette.cream
                    field.fill(Palette.dot)
                    Circle()
                        .fill(Palette.disc)
                        .frame(width: M.disc, height: M.disc)
                        .shadow(color: .black.opacity(0.06), radius: 15, y: 8)
                        .scaleEffect(M.discScale(s))
                        .position(x: cx, y: cy)

                    welcome(cx: cx, top: insets.top, hintY: size.height - max(insets.bottom, 12) - Metrics.hintAbove)
                        .opacity(M.textAlpha(s, calm: calm))

                    Canvas { context, _ in
                        tunnel(&context, s: s, center: CGPoint(x: cx, y: cy), reach: reach, label: label, size: size)
                    }
                    .allowsHitTesting(false)

                    Text(buttonLabel)
                        .font(Font(Metrics.labelFont))
                        .foregroundStyle(Palette.ink)
                        .lineLimit(1)
                        .opacity(label)
                        .position(x: cx, y: cy + Metrics.labelBelow)
                        .allowsHitTesting(false)

                    holdTarget
                        .position(x: cx, y: cy)

                    Group {
                        field.fill(Palette.nightDot).opacity(page * disc)
                        Circle()
                            .fill(Palette.nightDisc)
                            .frame(width: Metrics.nightDisc, height: Metrics.nightDisc)
                            .scaleEffect(0.8 + 0.2 * disc)
                            .opacity(page * disc)
                            .position(x: cx, y: nightY)
                        ForEach(0..<Metrics.balls.count, id: \.self) { index in
                            let ball = Metrics.balls[index]
                            let drop = M.dropped(s, index: index, calm: calm)
                            Circle()
                                .fill(LinearGradient(colors: Palette.ball, startPoint: .top, endPoint: .bottom))
                                .frame(width: ball.size, height: ball.size)
                                .opacity(page * drop.alpha)
                                .position(x: cx + ball.x, y: nightY + ball.y - drop.lift)
                        }
                        pager(drawn: M.pagerDrawn(s, calm: calm))
                            .opacity(page)
                            .position(x: cx, y: nightY + Metrics.pagerBelow + Metrics.pagerSag / 2)
                    }
                    .allowsHitTesting(false)

                    firstPage(s: s, page: page, top: insets.top, bottom: bottom, width: size.width, height: size.height)
                        .allowsHitTesting(atB)
                        .accessibilityHidden(!atB)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .onChange(of: night) { _, now in dark = now }
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(dark ? .dark : .light)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !touching {
                        touching = true
                        touched()
                    }
                }
                .onEnded { _ in touching = false }
        )
        .onAppear { tapHaptic.prepare() }
        .onDisappear { runTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The welcome

    private func welcome(cx: Double, top: Double, hintY: Double) -> some View {
        ZStack(alignment: .topLeading) {
            PressHoldSunriseMark(fill: Palette.ink, dot: Palette.cream)
                .position(x: cx, y: top + Metrics.markY)
            firstLine(name, font: Metrics.nameFont, pitch: 34, y: top + Metrics.nameY, color: Palette.ink)
                .lineLimit(1)
            firstLine(tagline, font: .systemFont(ofSize: 15), pitch: 19, y: top + Metrics.taglineY, color: Palette.body)
            Text(hint)
                .font(.system(size: 10, weight: .medium))
                .tracking(0.6)
                .foregroundStyle(Palette.ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                .padding(.top, hintY - UIFont.systemFont(ofSize: 10, weight: .medium).lineHeight / 2)
        }
        .allowsHitTesting(false)
    }

    /// Text whose first line is centred on `y`, with its lines `pitch` apart.
    private func firstLine(_ text: String, font: UIFont, pitch: Double, y: Double, color: Color) -> some View {
        Text(text)
            .font(Font(font))
            .lineSpacing(max(0, pitch - font.lineHeight))
            .multilineTextAlignment(.center)
            .foregroundStyle(color)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
            .padding(.top, y - font.lineHeight / 2)
    }

    private var holdTarget: some View {
        Circle()
            .fill(.clear)
            .frame(width: Metrics.holdTarget, height: Metrics.holdTarget)
            .contentShape(Circle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !pressing {
                            pressing = true
                            tapHaptic.impactOccurred()
                            press(.hand)
                        }
                    }
                    .onEnded { _ in
                        pressing = false
                        release()
                    }
            )
            .allowsHitTesting(!atB)
            .accessibilityElement()
            .accessibilityLabel("\(buttonLabel). \(hint)")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { press(.hand) }
    }

    // MARK: The tunnel

    /// The orange circle, the shapes that follow it, and the bolt on top.
    private func tunnel(_ context: inout GraphicsContext, s: Double, center: CGPoint, reach: Double, label: Double, size: CGSize) {
        let orange = GraphicsContext.Shading.linearGradient(Gradient(colors: accent), startPoint: CGPoint(x: 0, y: -1), endPoint: CGPoint(x: 0, y: 1))
        let shade = GraphicsContext.Shading.color(Palette.shade)
        let night = GraphicsContext.Shading.color(Palette.night)

        func draw(_ path: Path, _ shading: GraphicsContext.Shading, at y: Double, radius: Double, turn: Double, alpha: Double) {
            guard alpha > 0 else { return }
            var copy = context
            copy.opacity = alpha
            copy.translateBy(x: center.x, y: y)
            copy.rotate(by: .degrees(turn))
            copy.scaleBy(x: radius, y: radius)
            copy.fill(path, with: shading)
        }

        draw(PressHoldSunriseShapes.round, orange, at: center.y - M.buttonLift(s), radius: M.buttonRadius(s, reach: reach, calm: calm), turn: 0, alpha: 1)

        if calm {
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Palette.night.opacity(M.dusk(s))))
        } else {
            let grown: [(Path, GraphicsContext.Shading)] = [
                (PressHoldSunriseShapes.square, shade), (PressHoldSunriseShapes.scallop, orange), (PressHoldSunriseShapes.squircle, shade),
                (PressHoldSunriseShapes.blob, orange), (PressHoldSunriseShapes.round, night),
            ]
            for (index, shape) in grown.enumerated() {
                let now = M.grown(s, index: index, reach: reach)
                draw(shape.0, shape.1, at: center.y, radius: now.radius, turn: Metrics.turns[index] + now.turn, alpha: now.alpha)
            }
            // The way back, the last to shrink first: it lies under the others.
            let shrunk: [(Path, GraphicsContext.Shading, Double)] = [
                (PressHoldSunriseShapes.square, shade, Metrics.turns[0]), (PressHoldSunriseShapes.scallop, orange, Metrics.turns[1]),
                (PressHoldSunriseShapes.round, night, 0),
            ]
            for (order, shape) in shrunk.enumerated() {
                let now = M.shrunk(s, index: 2 - order, reach: reach)
                draw(shape.0, shape.1, at: center.y, radius: now.radius, turn: shape.2 + now.turn, alpha: now.alpha)
            }
        }

        var bolt = context
        bolt.opacity = label
        bolt.translateBy(x: center.x - 8, y: center.y - M.buttonRise - 8)
        bolt.fill(PressHoldSunriseShapes.bolt, with: .color(.black))
    }

    // MARK: The first page

    private func pager(drawn: Double) -> some View {
        let style = StrokeStyle(lineWidth: 2, lineCap: .round)
        return ZStack {
            PressHoldSunriseArc().trim(from: 0, to: drawn).stroke(Palette.track, style: style)
            PressHoldSunriseArc().trim(from: 0, to: min(drawn, 1 / Double(max(1, pages)))).stroke(Palette.trackOn, style: style)
        }
        .frame(width: Metrics.pagerWidth, height: Metrics.pagerSag)
        .opacity(drawn > 0 ? 1 : 0)
    }

    private func firstPage(s: Double, page: Double, top: Double, bottom: Double, width: Double, height: Double) -> some View {
        let mark = M.arrived(s, after: 0, length: M.markIn, calm: calm)
        let title = M.arrived(s, after: M.headingAfter, length: M.headingIn, calm: calm)
        return ZStack(alignment: .topLeading) {
            Button {
                tapHaptic.impactOccurred()
                goBack()
                onBack?()
            } label: {
                PressHoldSunriseMark(fill: .white, dot: Palette.night)
                    .padding(14)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back to the welcome")
            .opacity(page * mark)
            .position(x: width / 2, y: top + Metrics.markY)

            firstLine(heading, font: Metrics.headingFont, pitch: 27, y: top + Metrics.headingY, color: Palette.nightInk)
                .opacity(page * title)
                .offset(x: M.headingSlide * (1 - title))

            action(primaryLabel, fill: .white, ink: Palette.night, index: 0, s: s, page: page) { onPrimary?() }
                .position(x: width / 2, y: height - bottom - Metrics.action - Metrics.actionStep + Metrics.action / 2)
            action(secondaryLabel, fill: Palette.secondary, ink: .white, index: 1, s: s, page: page) { onSecondary?() }
                .position(x: width / 2, y: height - bottom - Metrics.action / 2)
        }
    }

    private func action(_ label: String, fill: Color, ink: Color, index: Int, s: Double, page: Double, perform: @escaping () -> Void) -> some View {
        let shown = M.arrived(s, after: M.actionAfter + Double(index) * M.actionGap, length: M.actionIn, calm: calm)
        return Button {
            tapHaptic.impactOccurred()
            perform()
        } label: {
            Text(label)
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, maxHeight: .infinity)
                .background(Capsule().fill(fill))
                .contentShape(Capsule())
        }
        .buttonStyle(DimStyle())
        .padding(.horizontal, Metrics.actionMargin)
        .frame(height: Metrics.action)
        .opacity(page * shown)
        .offset(y: M.actionRise * (1 - shown))
    }

    // MARK: Behaviour

    private var scene: Double { run?.scene(at: .now) ?? rest }

    /// Runs the scene from where it is to `to`, and leaves it at rest on `landing`. The loop passes
    /// the moment the run was due, so a late wake-up does not shift it.
    private func begin(to: Double, duration: Double, springy: Bool = false, landing: Double, at date: Date = .now) {
        let from = scene
        runTask?.cancel()
        run = Run(from: from, to: to, start: date, duration: duration, springy: springy)
        let clock = ContinuousClock()
        let start = clock.now - .seconds(Date.now.timeIntervalSince(date))
        runTask = Task { @MainActor in
            if from < M.hold, to > M.hold {
                try? await clock.sleep(until: start + .seconds(M.hold - from), tolerance: .zero)
                guard !Task.isCancelled else { return }
                completed()
            }
            try? await clock.sleep(until: start + .seconds(duration), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest = landing
            run = nil
        }
    }

    /// The finger goes down on the button: the scene runs to the first page unless it is let go in time.
    private func press(_ by: Holder, at date: Date = .now) {
        let s = scene
        guard s < M.hold else { return }
        holder = by
        if by == .hand { doneHaptic.prepare() }
        let to = calm ? M.hold + M.calmFade : M.atB
        begin(to: to, duration: to - s, landing: M.atB, at: date)
    }

    /// Let go before the hold was complete: the button springs back.
    private func release() {
        holder = nil
        let s = scene
        guard run != nil, s > 0, s < M.hold else { return }
        begin(to: 0, duration: M.release, springy: true, landing: 0)
    }

    private func completed() {
        if holder == .hand { doneHaptic.notificationOccurred(.success) }
        holder = nil
        onDone?()
    }

    private func goBack(at date: Date = .now) {
        guard atB else { return }
        let to = calm ? M.atB + M.calmFade : M.end
        // The end of the way back is the welcome at rest.
        begin(to: to, duration: to - M.atB, landing: 0, at: date)
    }

    /// A touch takes over from the autoPlay loop, which starts again from the welcome a while later.
    private func touched() {
        if holder == .auto { release() }
        touches += 1
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            // Three seconds of rest, counted from the end of what the touch set off.
            var quiet = 0.0
            while quiet < M.userHold {
                try? await Task.sleep(for: .seconds(0.1))
                guard !Task.isCancelled else { return }
                quiet = run == nil ? quiet + 0.1 : 0
            }
            // The loop starts over from the welcome.
            while !Task.isCancelled, run != nil || rest != 0 {
                goBack()
                try? await Task.sleep(for: .seconds(0.1))
            }
        }
        // Every step is due at a fixed time after the loop began, and the timers may not run late:
        // by default a long sleep is allowed to, by a tenth of a second and more.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(M.pressAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            press(.auto, at: began + M.pressAt)
            try? await clock.sleep(until: origin + .seconds(M.backAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            goBack(at: began + M.backAt)
            origin += .seconds(M.loop)
            began += M.loop
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.75 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

private typealias M = PressHoldSunriseMotion

private enum Palette {
    static let cream = Color(hex: 0xFBF8F1)
    static let ink = Color(hex: 0x251C21)
    static let body = Color(hex: 0x2B2B2B)
    static let dot = Color(hex: 0xEFE9DC)
    static let disc = Color(hex: 0xFFFDFF)
    static let night = Color(hex: 0x221F23)
    /// The tunnel's dark shapes are a little warmer than the page they end in.
    static let shade = Color(hex: 0x2B2529)
    static let nightDot = Color(hex: 0x2C292D)
    static let nightInk = Color(hex: 0xF4EFE6)
    static let nightDisc = Color(hex: 0x343134)
    static let ball = [Color(hex: 0xFECA78), Color(hex: 0xF9A53A)]
    static let track = Color(hex: 0x3D3A3D)
    static let trackOn = Color(hex: 0xF4D58A)
    static let secondary = Color(hex: 0x3B383B)
}

private enum Metrics {
    // Where things are, in points from the top safe area, the screen's bottom or a disc's centre.
    static let markY = 60.0
    static let nameY = 128.0
    static let taglineY = 178.0
    static let headingY = 111.0
    /// The welcome's disc and the first page's, as fractions of the screen's height.
    static let discAt = 0.61
    static let nightDiscAt = 0.506
    static let nightDisc = 219.0
    static let labelBelow = 39.0
    static let hintAbove = 30.0
    static let pagerBelow = 159.0
    static let pagerWidth = 110.0
    static let pagerSag = 6.0
    static let action = 44.0
    static let actionStep = 53.0
    static let actionMargin = 24.0
    /// The finger does not have to hit the small button: this much round the disc's centre takes the hold.
    static let holdTarget = 132.0
    // The halftone field: 2 pt dots on a 10 pt grid, from above the disc to below it.
    static let grid = 10.0
    static let fieldAbove = 230.0
    static let fieldBelow = 130.0
    static let fieldFade = 70.0
    /// The three balls: x and y from the disc's centre, and the diameter. The last is in front.
    static let balls: [(x: Double, y: Double, size: Double)] = [(-19, -54, 83), (56, -33, 70), (0, 44, 116)]
    /// Turn of each tunnel shape when it is born, in degrees.
    static let turns = [45.0, 0, 10, 0, 0]

    static func serif(_ size: Double) -> UIFont {
        let system = UIFont.systemFont(ofSize: size)
        return system.fontDescriptor.withDesign(.serif).map { UIFont(descriptor: $0, size: size) } ?? system
    }

    static let nameFont = serif(28)
    static let labelFont = serif(16)
    static let headingFont = serif(22)
}

/// The app's mark: a rounded square with a dot low in it.
struct PressHoldSunriseMark: View {
    let fill: Color
    let dot: Color

    var body: some View {
        RoundedRectangle(cornerRadius: 9, style: .continuous)
            .fill(fill)
            .frame(width: 26, height: 30)
            .overlay(alignment: .topLeading) {
                Circle().fill(dot).frame(width: 9, height: 9).offset(x: 9, y: 15)
            }
            .accessibilityHidden(true)
    }
}

/// Dots on a grid across the screen's width, smaller towards the field's edges, which reads as fading out.
struct PressHoldSunriseHalftone: Shape {
    let top: Double
    let bottom: Double

    func path(in rect: CGRect) -> Path {
        var path = Path()
        let width = rect.width
        var y = top + Metrics.grid / 2
        while y < bottom {
            var x = (width / 2).truncatingRemainder(dividingBy: Metrics.grid)
            while x < width {
                let size = M.smooth(min(x, width - x), 0, Metrics.fieldFade) * M.smooth(min(y - top, bottom - y), 0, Metrics.fieldFade)
                if size > 0.08 { path.addEllipse(in: CGRect(x: x - size, y: y - size, width: size * 2, height: size * 2)) }
                x += Metrics.grid
            }
            y += Metrics.grid
        }
        return path
    }
}

/// The pager: a shallow smile across its frame, drawn from the left.
struct PressHoldSunriseArc: Shape {
    func path(in rect: CGRect) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.minY))
        path.addQuadCurve(to: CGPoint(x: rect.maxX, y: rect.minY), control: CGPoint(x: rect.midX, y: rect.minY + rect.height * 2))
        return path
    }
}

#Preview {
    PressHoldSunrise(autoPlay: true)
}
