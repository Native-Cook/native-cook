import SwiftUI

/// A page with a "Generate cards" button. The sheet that rises is blue and holds a dashed
/// placeholder that wobbles while something is made; then a white blob swells inside it, the sheet
/// turns white and three real cards fan out of the placeholder with a Done button. Nothing is
/// generated here: `preparing` seconds stand in for the work.
struct PlaceholderRevealSheet: View {
    var pageTitle = "Automations"
    var generateLabel = "Generate cards"
    var title = "Preparing"
    var subtitle = "Generating card preview"
    var doneTitle = "ALL SET"
    var doneCaption = "This is a preview. The full cards will be delivered later."
    /// The title of the front card of the result.
    var cardTitle = "Founder Playbook"
    var stopLabel = "STOP"
    var doneLabel = "DONE"
    /// Seconds the placeholder wobbles before the real cards replace it.
    var preparing = 5.15
    /// Presses "Generate cards" and "Done" by itself every `PlaceholderRevealSheet.loop` seconds.
    var autoPlay = false
    /// Called when the generation starts, and when the cards are shown.
    var onGenerate: (() -> Void)? = nil
    var onReady: (() -> Void)? = nil
    /// Called when the sheet is dismissed, by Stop, Done or the loop.
    var onClose: ((_ finished: Bool) -> Void)? = nil

    /// Seconds for one pass of the autoPlay loop.
    static let loop = 10.0
    // The timeline, in seconds from the start of a loop.
    private static let pressAt = 0.8
    private static let openAt = 0.95
    private static let pressDoneAt = 8.5
    private static let closeAt = 8.8
    /// Seconds after the last touch before the loop starts again from rest.
    private static let userHold = 3.0

    private enum Phase { case closed, preparing, done }
    private enum Pressed { case none, generate, done }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var phase = Phase.closed
    @State private var autoPressed = Pressed.none
    @State private var ready: Task<Void, Never>? = nil
    @State private var touches = 0
    @State private var touching = false
    /// When the wobble clock started; nil while it is stopped.
    @State private var clockStart: Date? = nil
    @State private var sheet = 0.0
    @State private var pop = 0.0
    @State private var stop = 0.0
    @State private var blob = 0.0
    @State private var reveal = 0.0
    @State private var fan = 0.0

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            screen(width: width, height: height, insets: insets)
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
                .ignoresSafeArea()
        }
        .environment(\.colorScheme, .dark)
        .preferredColorScheme(.dark)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !touching else { return }
                    touching = true
                    touched()
                }
                .onEnded { _ in touching = false }
        )
        .onDisappear { ready?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await playLoop() }
    }

    // MARK: Layout

    private func screen(width: Double, height: Double, insets: EdgeInsets) -> some View {
        let sheetHeight = min(M.sheetHeight, height - insets.top - 160)
        let sheetWidth = width - 2 * M.sheetInset
        let bottom = max(insets.bottom, 16)
        let rest = height - M.sheetInset - sheetHeight - M.lip

        return ZStack(alignment: .topLeading) {
            M.page
            page(width: width, height: height, top: insets.top, bottom: bottom)

            Color.black
                .modifier(Driven(sheet) { content, p in content.opacity(M.dim * p) })
                .allowsHitTesting(false)

            sheetView(width: sheetWidth, height: sheetHeight)
                .frame(width: sheetWidth, height: sheetHeight, alignment: .topLeading)
                .clipShape(RoundedRectangle(cornerRadius: M.sheetRadius, style: .continuous))
                .frame(width: sheetWidth, height: sheetHeight + M.lip, alignment: .top)
                .background {
                    // A darker lip under the sheet's bottom edge, which turns grey with the sheet.
                    Color.clear.modifier(Driven(reveal) { _, p in
                        RoundedRectangle(cornerRadius: M.sheetRadius, style: .continuous).fill(M.lip(p))
                    })
                    .shadow(color: .black.opacity(0.45), radius: 20, y: 18)
                }
                .modifier(Driven(sheet) { content, p in
                    content.offset(x: M.sheetInset, y: rest + (1 - p) * (sheetHeight + M.sheetInset + 24))
                })
                .allowsHitTesting(phase != .closed)
        }
    }

    private func page(width: Double, height: Double, top: Double, bottom: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 12) {
                HStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(pageTitle)
                            .font(.system(size: 34, weight: .heavy))
                            .tracking(-0.8)
                            .foregroundStyle(M.pageInk)
                            .frame(height: 40)
                        Text("2 running · next at 7:30AM")
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(M.pageMute)
                            .frame(height: 20)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "plus")
                        .font(.system(size: 18, weight: .regular))
                        .foregroundStyle(M.pageInk)
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(M.pageCard))
                }
                .padding(.horizontal, 4)
                .padding(.bottom, 8)
                ForEach(M.automations, id: \.title) { item in automation(item) }
            }
            .padding(.horizontal, M.margin)
            .padding(.top, top + 12)
            .frame(width: width, alignment: .leading)

            Button(action: generate) {
                HStack(spacing: 8) {
                    Image(systemName: "sparkles").font(.system(size: 16, weight: .semibold))
                    Text(generateLabel).font(.system(size: 17, weight: .bold))
                }
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: M.pageButtonHeight)
            }
            .buttonStyle(PlaceholderRevealSheetButtonStyle(held: autoPressed == .generate, glow: 12))
            .padding(.horizontal, M.margin)
            .accessibilityLabel(generateLabel)
            .frame(width: width)
            .offset(y: height - bottom - M.pageButtonHeight)
        }
    }

    private func automation(_ item: PlaceholderRevealSheetAutomation) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(item.title)
                .font(.system(size: 24, weight: .heavy))
                .tracking(-0.5)
                .foregroundStyle(M.pageInk)
                .lineLimit(1)
                .frame(height: 30)
            Text(item.text)
                .font(.system(size: 15, weight: .medium))
                .lineSpacing(2)
                .foregroundStyle(M.pageMute)
                .lineLimit(2)
                .padding(.top, 4)
            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 0) {
                    Text(item.time)
                        .font(.system(size: 17, weight: .heavy))
                        .tracking(0.2)
                        .foregroundStyle(M.pageInk)
                        .frame(height: 22)
                    Text(item.every)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(M.pageMute)
                        .frame(height: 20)
                }
                Spacer(minLength: 0)
                let color = item.running ? M.running : M.paused
                HStack(spacing: 6) {
                    Circle().fill(color).frame(width: 8, height: 8)
                    Text(item.running ? "RUNNING" : "PAUSED")
                        .font(.system(size: 13, weight: .heavy))
                        .tracking(0.8)
                        .foregroundStyle(color)
                        .frame(height: 18)
                }
                .padding(.bottom, 2)
            }
            .padding(.top, 18)
        }
        .padding(.horizontal, 22)
        .padding(.top, 20)
        .padding(.bottom, 18)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(M.pageCard)
                .overlay { RoundedRectangle(cornerRadius: 28, style: .continuous).strokeBorder(.white.opacity(0.06), lineWidth: M.hairline) }
        }
    }

    private func sheetView(width: Double, height: Double) -> some View {
        ZStack(alignment: .topLeading) {
            M.blue
            Color.white.modifier(Driven(reveal) { content, p in content.opacity(p) })

            VStack(spacing: 4) {
                Text(title)
                    .font(.system(size: 34, weight: .heavy))
                    .tracking(-0.6)
                    .foregroundStyle(.white)
                    .frame(height: 40)
                Text(subtitle)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.62))
                    .frame(height: 22)
            }
            .frame(width: width)
            .modifier(Driven(reveal) { content, p in
                content.offset(y: 36 - 8 * p).opacity((1 - min(1, p * 2.5)) * (1 - min(1, p * 2)))
            })
            .allowsHitTesting(false)

            VStack(spacing: 6) {
                Text(doneTitle)
                    .font(.system(size: 32, weight: .black))
                    .tracking(0.2)
                    .foregroundStyle(M.blue)
                    .frame(height: 38)
                Text(doneCaption)
                    .font(.system(size: 15, weight: .semibold))
                    .lineSpacing(2)
                    .multilineTextAlignment(.center)
                    .foregroundStyle(M.caption)
            }
            .padding(.horizontal, 44)
            .frame(width: width)
            .modifier(Driven(reveal) { content, p in
                let t = min(1, max(0, p * 2 - 0.6))
                content.offset(y: 30 + 8 * (1 - t)).opacity(t)
            })
            .allowsHitTesting(false)

            placeholder
                .offset(x: (width - M.placeholderWidth) / 2, y: M.placeholderTop)
                .allowsHitTesting(false)

            stopButton
                .frame(width: width)
                .offset(y: M.stopTop)
                .modifier(Driven(stop) { content, s in content.opacity(s) })
                .modifier(Driven(reveal) { content, p in content.opacity(1 - min(1, p * 4)) })
                .allowsHitTesting(phase == .preparing)

            card(side: -1, width: width)
            card(side: 1, width: width)
            card(side: 0, width: width)

            Button { dismiss(finished: true, ms: reduceMotion ? M.fade : M.slide) } label: {
                Text(doneLabel)
                    .font(.system(size: 17, weight: .heavy))
                    .tracking(1)
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: M.buttonHeight)
            }
            .buttonStyle(PlaceholderRevealSheetButtonStyle(held: autoPressed == .done, glow: 10))
            .accessibilityLabel(doneLabel)
            .frame(width: width - 40)
            .modifier(Driven(reveal) { content, p in
                let t = min(1, max(0, (p - 0.3) / 0.7))
                content.opacity(t).offset(y: (1 - t) * 90)
            })
            .offset(x: 20, y: height - 24 - M.buttonHeight)
            .allowsHitTesting(phase == .done)
        }
    }

    /// The dashed placeholder: wobbles and floats on the clock, pops in, and swells with the blob.
    private var placeholder: some View {
        TimelineView(.animation(paused: clockStart == nil)) { timeline in
            let c = clockStart.map { timeline.date.timeIntervalSince($0).truncatingRemainder(dividingBy: M.wobble) / M.wobble } ?? 0
            ZStack {
                RoundedRectangle(cornerRadius: M.placeholderRadius, style: .continuous).fill(.white.opacity(0.16))
                Canvas { context, size in
                    let rect = CGRect(x: M.stroke / 2, y: M.stroke / 2, width: size.width - M.stroke, height: size.height - M.stroke)
                    context.stroke(
                        RoundedRectangle(cornerRadius: M.placeholderRadius, style: .circular).path(in: rect),
                        with: .color(.white.opacity(0.62)),
                        style: StrokeStyle(lineWidth: M.stroke, lineCap: .round, dash: [M.dash, M.gap], dashPhase: -c * (M.dash + M.gap))
                    )
                }
                Circle()
                    .fill(.white.opacity(0.94))
                    .frame(width: M.blobSize, height: M.blobSize)
                    .background {
                        // Two glows: a tight white one and a wide pale-blue one grown by 8 pt.
                        Circle().fill(M.blobHalo).padding(-8).blur(radius: 24)
                        Circle().fill(.white.opacity(0.95)).blur(radius: 9)
                    }
                    .modifier(Driven(blob) { content, b in content.scaleEffect(1.4 * b).opacity(min(1, b * 3)) })
                    .modifier(Driven(reveal) { content, p in content.opacity(1 - min(1, p * 2)) })
            }
            .frame(width: M.placeholderWidth, height: M.placeholderHeight)
            .modifier(Driven(blob) { content, b in content.scaleEffect(1 + 0.1 * b) })
            .modifier(Driven(pop) { content, p in
                content
                    .scaleEffect(0.6 + 0.4 * p)
                    .rotationEffect(.degrees(M.wobbleDegrees * sin(2 * .pi * c)))
                    .offset(y: M.float * sin(2 * .pi * c + 1.2))
                    .opacity(min(1, p * 3))
            })
        }
    }

    private var stopButton: some View {
        Button { dismiss(finished: false, ms: reduceMotion ? M.fade : M.slide) } label: {
            HStack(spacing: 6) {
                Image(systemName: "stop.circle").font(.system(size: 17))
                Text(stopLabel).font(.system(size: 17, weight: .bold)).tracking(0.4)
            }
            .foregroundStyle(.white)
            .padding(.horizontal, 14)
            .frame(height: 36)
        }
        .buttonStyle(PlaceholderRevealSheetStopStyle())
        .padding(10)
        .contentShape(Rectangle())
        .padding(-10)
        .accessibilityLabel(stopLabel)
    }

    /// One of the three cards. `side` is -1 for the left back card, 1 for the right one, 0 for the front.
    private func card(side: Double, width: Double) -> some View {
        let front = side == 0
        return VStack(alignment: .leading, spacing: 0) {
            if front {
                Text(cardTitle)
                    .font(.system(size: 16, weight: .heavy))
                    .tracking(-0.2)
                    .lineLimit(2)
                    .frame(maxWidth: .infinity)
                Text("Hard-won lessons on building from zero")
                    .font(.system(size: 10.5, weight: .semibold))
                    .lineSpacing(0.5)
                    .lineLimit(2)
                    .foregroundStyle(.white.opacity(0.82))
                    .frame(maxWidth: .infinity)
                    .padding(.top, 3)
                Rectangle().fill(.white.opacity(0.4)).frame(height: M.hairline * 2).padding(.vertical, 10)
                ForEach(M.bullets, id: \.self) { line in
                    HStack(alignment: .top, spacing: 6) {
                        Circle().fill(.white).frame(width: 4, height: 4).padding(.top, 5)
                        Text(line)
                            .font(.system(size: 10, weight: .semibold))
                            .lineSpacing(1)
                            .lineLimit(2)
                            .multilineTextAlignment(.leading)
                            .frame(maxWidth: .infinity, alignment: .leading)
                    }
                    .padding(.bottom, 5)
                }
            }
        }
        .multilineTextAlignment(.center)
        .foregroundStyle(.white)
        .padding(.horizontal, 14)
        .padding(.top, 16)
        .frame(width: M.cardWidth, height: M.cardHeight, alignment: .top)
        .background { PlaceholderRevealSheetCardFace(front: front) }
        .modifier(Driven(fan) { content, f in
            content
                .scaleEffect(0.6 + 0.4 * f)
                .rotationEffect(.degrees(M.fanAngle * side * f))
                .offset(x: M.fanSpread * side * f)
                .opacity(min(1, f * 3))
        })
        .offset(x: (width - M.cardWidth) / 2, y: M.cardTop)
        .allowsHitTesting(false)
    }

    // MARK: Actions

    private func animate(_ value: Binding<Double>, to target: Double, _ animation: Animation?) {
        withAnimation(animation) { value.wrappedValue = target }
    }

    private func show() {
        for value in [$pop, $stop, $blob, $reveal, $fan] { animate(value, to: 0, nil) }
        clockStart = nil
        animate($sheet, to: 1, M.timing(reduceMotion ? M.fade : M.slide))
        if reduceMotion {
            animate($pop, to: 1, M.timing(M.fade))
            animate($stop, to: 1, M.timing(M.fade))
            return
        }
        animate($pop, to: 1, M.popSpring.delay(M.popAfter / 1000))
        animate($stop, to: 1, M.timing(M.stopIn).delay(M.stopAfter / 1000))
        clockStart = .now
    }

    private func generate() {
        guard phase == .closed else { return }
        autoPressed = .none
        phase = .preparing
        show()
        onGenerate?()
        ready = Task { @MainActor in
            try? await Task.sleep(for: .seconds(preparing), tolerance: .zero)
            guard !Task.isCancelled else { return }
            showCards()
        }
    }

    private func showCards() {
        ready = nil
        phase = .done
        let blobIn = reduceMotion ? 0 : M.blobIn
        animate($blob, to: 1, M.timing(blobIn))
        animate($reveal, to: 1, M.timing(reduceMotion ? M.fade : M.recolor, curve: M.quadInOut).delay(blobIn / 1000))
        animate($fan, to: 1, reduceMotion ? M.timing(M.fade) : M.fanSpring.delay((M.blobIn + M.fanAfter) / 1000))
        onReady?()
    }

    private func dismiss(finished: Bool, ms: Double) {
        ready?.cancel()
        ready = nil
        autoPressed = .none
        phase = .closed
        withAnimation(M.timing(ms, curve: M.cubicInOut), completionCriteria: .logicallyComplete) {
            sheet = 0
        } completion: {
            guard phase == .closed else { return }
            clockStart = nil
            for value in [$pop, $stop, $blob, $reveal, $fan] { animate(value, to: 0, nil) }
        }
        onClose?(finished)
    }

    private func rest() {
        if phase != .closed { dismiss(finished: false, ms: M.restFade) }
        autoPressed = .none
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoPressed = .none
        touches += 1
    }

    private func playLoop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(Self.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let steps: [(Double, () -> Void)] = [
            (Self.pressAt, { autoPressed = .generate }),
            (Self.openAt, generate),
            (Self.pressDoneAt, { autoPressed = .done }),
            (Self.closeAt, { dismiss(finished: true, ms: reduceMotion ? M.fade : M.slide) }),
        ]
        var origin = clock.now
        while !Task.isCancelled {
            for (at, run) in steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                run()
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

// MARK: Support

private struct PlaceholderRevealSheetAutomation {
    let title: String
    let text: String
    let time: String
    let every: String
    let running: Bool
}

/// Re-evaluates `transform` on every frame of an animation of `value`, so a number that moves
/// under a spring can drive several properties through non-linear maps.
private struct Driven: ViewModifier, Animatable {
    var value: Double
    private let transform: (Content, Double) -> AnyView

    init<Result: View>(_ value: Double, @ViewBuilder _ transform: @escaping (Content, Double) -> Result) {
        self.value = value
        self.transform = { AnyView(transform($0, $1)) }
    }

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    func body(content: Content) -> some View { transform(content, value) }
}

/// A card's face: a vertical teal gradient with a soft light along its top edge, a fine light
/// border and a teal shadow.
private struct PlaceholderRevealSheetCardFace: View {
    let front: Bool

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: M.cardRadius, style: .continuous)
        ZStack(alignment: .top) {
            shape.fill(LinearGradient(colors: front ? [M.teal, M.tealDeep] : [M.tealBack, M.tealBackDeep], startPoint: .top, endPoint: .bottom))
            RoundedRectangle(cornerRadius: M.cardRadius, style: .continuous)
                .fill(LinearGradient(colors: [.white.opacity(0.28), .white.opacity(0)], startPoint: .top, endPoint: .bottom))
                .frame(height: M.cardHeight * 0.4)
            RoundedRectangle(cornerRadius: M.cardRadius - 0.5, style: .continuous)
                .stroke(.white.opacity(0.3), lineWidth: 1)
                .padding(0.5)
        }
        .background {
            shape.fill(M.cardShadow.opacity(0.28)).offset(y: 14).blur(radius: 14)
            shape.fill(M.cardShadow.opacity(0.2)).offset(y: 2).blur(radius: 2)
        }
    }
}

private struct PlaceholderRevealSheetButtonStyle: ButtonStyle {
    let held: Bool
    /// The blue glow's blur, as a Gaussian radius.
    let glow: Double

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background {
                Capsule()
                    .fill(held || configuration.isPressed ? M.bluePressed : M.blue)
                    .shadow(color: M.blue.opacity(0.35), radius: glow, y: 8)
            }
            .contentShape(Capsule())
    }
}

private struct PlaceholderRevealSheetStopStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.background(Capsule().fill(.white.opacity(configuration.isPressed ? 0.2 : 0)))
    }
}

private typealias M = PlaceholderRevealSheetMetrics

private enum PlaceholderRevealSheetMetrics {
    static let page = Color(hex: 0x121214)
    static let pageCard = Color(hex: 0x232326)
    static let pageInk = Color(hex: 0xf4f4f5)
    static let pageMute = Color(hex: 0x8e8e93)
    static let running = Color(hex: 0x3ddc84)
    static let paused = Color(hex: 0xff9f0a)
    static let blue = Color(hex: 0x41aff3)
    static let bluePressed = Color(hex: 0x2f9ee4)
    static let teal = Color(hex: 0x4fd6d0)
    static let tealDeep = Color(hex: 0x2fbab4)
    static let tealBack = Color(hex: 0x38bfb9)
    static let tealBackDeep = Color(hex: 0x249e99)
    static let cardShadow = Color.rgba(15, 110, 106, 1)
    static let caption = Color(hex: 0x7fc3ee)
    static let blobHalo = Color.rgba(220, 245, 255, 0.7)
    static let hairline = 1.0 / 3

    /// The lip goes from deep blue to light grey with the sheet: a straight mix of the two.
    static func lip(_ t: Double) -> Color {
        Color(.sRGB, red: (0x2a + (0xdf - 0x2a) * t) / 255, green: (0x8f + (0xe4 - 0x8f) * t) / 255, blue: (0xd3 + (0xea - 0xd3) * t) / 255)
    }

    static let margin = 16.0
    // The sheet floats inside the screen's corners with a darker lip under its bottom edge.
    static let sheetInset = 8.0
    static let sheetRadius = 44.0
    static let sheetHeight = 488.0
    static let lip = 5.0
    static let placeholderWidth = 160.0
    static let placeholderHeight = 210.0
    static let placeholderTop = 132.0
    static let placeholderRadius = 22.0
    static let dash = 9.0
    static let gap = 7.0
    static let stroke = 2.5
    static let cardWidth = 156.0
    static let cardHeight = 204.0
    static let cardTop = 142.0
    static let cardRadius = 18.0
    static let stopTop = 372.0
    static let buttonHeight = 52.0
    static let pageButtonHeight = 52.0
    static let blobSize = 120.0

    static let slide = 550.0
    /// Seconds for one turn of the wobble clock; the wobble and the float are one cycle of it.
    static let wobble = 1.6
    static let wobbleDegrees = 3.0
    static let float = 4.0
    static let popSpring = Animation.interpolatingSpring(mass: 1, stiffness: 150, damping: 8)
    static let fanSpring = Animation.interpolatingSpring(mass: 1, stiffness: 150, damping: 11)
    static let popAfter = 250.0
    static let stopAfter = 1000.0
    static let stopIn = 300.0
    static let blobIn = 450.0
    static let recolor = 350.0
    static let fanAfter = 110.0
    static let fanAngle = 9.0
    static let fanSpread = 26.0
    static let fade = 180.0
    static let restFade = 350.0
    /// How dark the page gets under the sheet.
    static let dim = 0.55

    static let automations = [
        PlaceholderRevealSheetAutomation(
            title: "Mental Models Daily",
            text: "Short lessons on the thinking tools careful investors lean on, one model at a time.",
            time: "9:00AM", every: "Every 2 days", running: true
        ),
        PlaceholderRevealSheetAutomation(
            title: "Stoic Mornings",
            text: "A passage and one question to start the day calmly, with a short reflection.",
            time: "7:30AM", every: "Every day", running: true
        ),
        PlaceholderRevealSheetAutomation(
            title: "Founder Playbook",
            text: "Hard-won lessons on building a company, from the first idea to the first hire.",
            time: "6:00PM", every: "Weekly", running: false
        ),
    ]
    static let bullets = ["Talk to ten users before writing code", "Ship something small every week", "Hire for slope, not intercept"]

    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 1), endControlPoint: UnitPoint(x: 0.68, y: 1))
    static let cubicInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.65, y: 0), endControlPoint: UnitPoint(x: 0.35, y: 1))
    static let quadInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    /// A timed animation of `ms` milliseconds, cubic ease-out unless told otherwise.
    static func timing(_ ms: Double, curve: UnitCurve = cubicOut) -> Animation {
        .timingCurve(curve, duration: ms / 1000)
    }
}
