import SwiftUI

/// Calls `content` with the value as it animates; the closure's own mapping (ramps, lerps) runs
/// on every frame, which SwiftUI's modifiers would otherwise interpolate linearly end to end.
private struct WidgetGuideSheetReader<Value: VectorArithmetic, Content: View>: View, Animatable {
    var value: Value
    @ViewBuilder let content: (Value) -> Content

    var animatableData: Value {
        get { value }
        set { value = newValue }
    }

    var body: some View { content(value) }
}

/// A guide in a sheet: a miniature home screen shows, one slide at a time, how to add a widget. A
/// single number moves between slides and every part of the picture (the empty slot, the plus,
/// the picker that rises inside it, the widgets that drop in, the blurred page and its menu) is a
/// function of it, so the slides cross-fade, step backwards, and follow a swipe or the chevrons.
struct WidgetGuideSheet: View {
    /// What the guide says under the picture, one line of text per slide.
    var captions = M.captions
    /// Plays the whole guide by itself, over and over, and gives way to a touch.
    var autoPlay = false
    var title = "Widget Guide"
    /// Called when a slide settles, with its place in `captions`.
    var onSlideChange: ((Int) -> Void)? = nil
    var onClose: (() -> Void)? = nil

    private typealias M = WidgetGuideSheetMotion
    private typealias Scene = WidgetGuideSheetSceneMetrics

    private enum Axis { case horizontal, vertical }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // 0 hidden, 1 up; the slide number; the picker's shimmer.
    @State private var sheet = 0.0
    @State private var step = 0.0
    @State private var shimmer = 1.0
    @State private var drag = 0.0
    @State private var dashStart: Date? = nil
    @State private var slide = 0
    @State private var isOpen = false
    @State private var touches = 0
    @State private var axis: Axis? = nil
    @State private var closing: Task<Void, Never>? = nil

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            scene(screen: screen, insets: insets)
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { closing?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func scene(screen: CGSize, insets: EdgeInsets) -> some View {
        let sheetHeight = screen.height - insets.top
        return ZStack(alignment: .topLeading) {
            // The page behind: a list of coming events.
            VStack(spacing: 10) {
                HStack(alignment: .bottom) {
                    VStack(alignment: .leading, spacing: 0) {
                        Text("MONDAY, 13 OCTOBER")
                            .font(.system(size: 12, weight: .bold))
                            .tracking(0.8)
                            .foregroundStyle(M.mute)
                            .frame(height: 16)
                        Text("Up next")
                            .font(.system(size: 34, weight: .heavy))
                            .tracking(-1)
                            .foregroundStyle(M.ink)
                            .frame(height: 40)
                    }
                    Spacer(minLength: 0)
                    Image(systemName: "plus")
                        .font(.system(size: 20, weight: .medium))
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(M.ink, in: Circle())
                        .padding(.bottom, 2)
                }
                .padding(.bottom, 6)
                ForEach(M.events, id: \.name) { event in
                    HStack(spacing: 14) {
                        VStack(spacing: 0) {
                            Text(event.days)
                                .font(.system(size: 20, weight: .heavy))
                                .foregroundStyle(.white)
                                .frame(height: 22)
                            Text(event.days == "1" ? "DAY" : "DAYS")
                                .font(.system(size: 8.5, weight: .heavy))
                                .tracking(0.6)
                                .foregroundStyle(.white.opacity(0.85))
                                .frame(height: 10)
                        }
                        .frame(width: 52, height: 52)
                        .background(event.color, in: RoundedRectangle(cornerRadius: 15))
                        VStack(alignment: .leading, spacing: 2) {
                            Text(event.name)
                                .font(.system(size: 17, weight: .bold))
                                .tracking(-0.3)
                                .foregroundStyle(M.ink)
                            Text(event.when)
                                .font(.system(size: 13, weight: .medium))
                                .foregroundStyle(M.mute)
                        }
                        .frame(maxWidth: .infinity, alignment: .leading)
                        Image(systemName: event.icon)
                            .font(.system(size: 17))
                            .foregroundStyle(event.color)
                    }
                    .padding(.leading, 12)
                    .padding(.trailing, 18)
                    .frame(height: 76)
                    .background(
                        RoundedRectangle(cornerRadius: 20)
                            .fill(M.rowFill)
                            .shadow(color: .black.opacity(0.04), radius: 8, y: 2)
                    )
                }
            }
            .padding(.horizontal, M.margin)
            .padding(.top, insets.top + 8)
            .frame(width: screen.width, height: screen.height, alignment: .top)
            .background(M.page)
            .overlay {
                if !isOpen {
                    Color.clear
                        .contentShape(Rectangle())
                        .onTapGesture {
                            touched()
                            open()
                        }
                        .accessibilityLabel("Open the guide")
                        .accessibilityAddTraits(.isButton)
                }
            }

            WidgetGuideSheetReader(value: AnimatablePair(sheet, drag)) { pair in
                ZStack(alignment: .topLeading) {
                    Color.black
                        .opacity(M.scrim * min(1, pair.first) * (1 - min(1, pair.second / sheetHeight)))
                        .frame(width: screen.width, height: screen.height)
                        .allowsHitTesting(false)
                    sheetView(screen: screen, insets: insets, sheetHeight: sheetHeight)
                        .offset(y: insets.top + (1 - pair.first) * (sheetHeight + M.overshoot) + pair.second)
                        .allowsHitTesting(isOpen)
                }
            }
        }
    }

    private func sheetView(screen: CGSize, insets: EdgeInsets, sheetHeight: Double) -> some View {
        let total = sheetHeight + M.overshoot
        let phoneWidth = screen.width - M.margin * 2
        let navBottom = max(insets.bottom, 12) + 4
        // The caption sits halfway between the picture and the buttons, however tall the screen is.
        let captionTop = min(
            sheetHeight - navBottom - M.nav - M.navGap - M.captionHeight,
            M.header + Scene.phoneHeight + (sheetHeight - navBottom - M.nav - M.header - Scene.phoneHeight - M.captionHeight) / 2
        )
        return ZStack(alignment: .topLeading) {
            UnevenRoundedRectangle(topLeadingRadius: M.sheetRadius, topTrailingRadius: M.sheetRadius)
                .fill(.white)
                .shadow(color: .black.opacity(0.2), radius: 30, y: -6)

            Text(title)
                .font(.system(size: 13, design: .monospaced))
                .tracking(0.4)
                .foregroundStyle(M.mute)
                .frame(width: screen.width, height: M.header)

            Button {
                touched()
                close()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(M.closeInk)
                    .frame(width: 30, height: 30)
                    .background(M.closeFill, in: Circle())
                    .padding(8)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-8)
            .accessibilityLabel("Close")
            .offset(x: screen.width - 16 - 30, y: 11)

            WidgetGuideSheetReader(value: step) { current in
                WidgetGuideSheetScene(width: phoneWidth, step: current, dashStart: dashStart, shimmer: shimmer, labels: WidgetGuideSheetLabels())
                    .background(
                        RoundedRectangle(cornerRadius: 30)
                            .fill(M.phoneShadow)
                            .shadow(color: M.phoneShadow.opacity(0.25), radius: 20, y: 10)
                    )
                    .offset(x: M.margin, y: M.header)

                ZStack {
                    ForEach(Array(captions.enumerated()), id: \.offset) { index, caption in
                        let near = max(0, 1 - abs(current - Double(index)) * 2.2)
                        Text(caption)
                            .font(.system(size: 19, weight: .bold))
                            .tracking(-0.3)
                            .lineSpacing(3)
                            .multilineTextAlignment(.center)
                            .foregroundStyle(M.ink)
                            .frame(width: screen.width - 56, alignment: .top)
                            .opacity(near)
                            .offset(y: M.lerp(6, 0, near))
                    }
                }
                .frame(width: screen.width - 56, height: M.captionHeight, alignment: .top)
                .offset(x: 28, y: captionTop)
            }

            HStack {
                chevron("chevron.left", label: "Previous", disabled: slide == 0) { go(slide - 1) }
                Spacer(minLength: 0)
                HStack(spacing: 6) {
                    ForEach(captions.indices, id: \.self) { index in
                        Capsule()
                            .fill(index == slide ? M.ink : M.dot)
                            .frame(width: index == slide ? M.dotWide : M.dotSize, height: M.dotSize)
                            .animation(.easeInOut(duration: M.dotFor), value: slide)
                    }
                }
                Spacer(minLength: 0)
                chevron("chevron.right", label: "Next", disabled: slide == captions.count - 1) { go(slide + 1) }
            }
            .padding(.horizontal, M.margin)
            .frame(width: screen.width, height: M.nav)
            .offset(y: total - M.overshoot - navBottom - M.nav)
        }
        .frame(width: screen.width, height: total, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(pan(sheetHeight: sheetHeight))
    }

    private func chevron(_ name: String, label: String, disabled: Bool, action: @escaping () -> Void) -> some View {
        Button {
            touched()
            action()
        } label: {
            Image(systemName: name)
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(M.ink)
                .frame(width: M.nav, height: M.nav)
                .background(M.closeFill, in: Circle())
                .padding(8)
                .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .padding(-8)
        .disabled(disabled)
        .opacity(disabled ? 0.35 : 1)
        .accessibilityLabel(label)
    }

    // MARK: Gestures

    /// One drag for both jobs: sideways over the picture changes the slide, downwards drags the sheet away.
    private func pan(sheetHeight: Double) -> some Gesture {
        DragGesture(minimumDistance: 12)
            .onChanged { value in
                if axis == nil {
                    let sideways = abs(value.translation.width) > abs(value.translation.height)
                    let overPhone = value.startLocation.y > M.header && value.startLocation.y < M.header + Scene.phoneHeight
                    axis = sideways ? (overPhone ? .horizontal : nil) : .vertical
                    if axis == nil { return }
                    touched()
                }
                if axis == .vertical { instantly { drag = max(0, value.translation.height) } }
            }
            .onEnded { value in
                defer { axis = nil }
                switch axis {
                case .horizontal:
                    if value.translation.width < -M.swipeDistance { go(slide + 1) } else if value.translation.width > M.swipeDistance { go(slide - 1) }
                case .vertical:
                    if drag > sheetHeight / 5 || value.velocity.height > M.dismissVelocity {
                        instantly {
                            sheet = 1 - drag / sheetHeight
                            drag = 0
                        }
                        close()
                    } else {
                        withAnimation(M.riseSpring) { drag = 0 }
                    }
                case nil:
                    break
                }
            }
    }

    // MARK: Stages

    private func instantly(_ change: () -> Void) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t, change)
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() { touches += 1 }

    private func go(_ index: Int) {
        let next = min(captions.count - 1, max(0, index))
        slide = next
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.slideCurve) { step = Double(next) }
        if next == 2 && !reduceMotion {
            instantly { shimmer = 0 }
            withAnimation(M.shimmerCurve) { shimmer = 1 }
        }
        onSlideChange?(next)
    }

    private func open() {
        closing?.cancel()
        isOpen = true
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.riseSpring) { sheet = 1 }
        if !reduceMotion { dashStart = .now }
    }

    private func close() {
        isOpen = false
        onClose?()
        let duration = reduceMotion ? M.fade : M.dropFor
        withAnimation(reduceMotion ? .easeInOut(duration: duration) : M.dropCurve(duration)) { sheet = 0 }
        closing?.cancel()
        closing = Task { @MainActor in
            try? await Task.sleep(for: .seconds(duration), tolerance: .zero)
            guard !Task.isCancelled else { return }
            // Out of sight now: the next opening starts from the first slide.
            instantly {
                dashStart = nil
                step = 0
                drag = 0
                slide = 0
            }
        }
    }

    /// Everything back as it was at the start, with no animation.
    private func rest() {
        closing?.cancel()
        instantly {
            sheet = 0
            step = 0
            dashStart = nil
            drag = 0
            shimmer = 1
            isOpen = false
            slide = 0
        }
    }

    // MARK: autoPlay

    private var steps: [(at: Double, run: () -> Void)] {
        [(M.openAt, open)]
            + M.slideAt.enumerated().map { index, at in (at, { go(index + 1) }) }
            + [(M.closeAt, close), (M.restAt, rest)]
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run()
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

enum WidgetGuideSheetMotion {
    static let captions = [
        "On your Home Screen, press and hold an empty space.",
        "Tap the plus button in the top left.",
        "Find Daybreak, then choose a widget.",
        "Place your widget(s), then press the Done button in the top right.",
        "Press and hold a widget to customize it.",
    ]

    /// The autoPlay loop, in seconds from its start.
    static let loop = 10.5
    static let openAt = 0.8
    static let slideAt = [2.8, 4.4, 6.0, 7.8]
    static let closeAt = 9.3
    static let restAt = 9.9
    /// Seconds a touch keeps the loop away.
    static let userHold = 3.0

    struct Event {
        let name: String
        let when: String
        let days: String
        let color: Color
        /// An SF Symbol name.
        let icon: String
    }

    static let events = [
        Event(name: "Road trip", when: "Tomorrow · Big Sur", days: "1", color: Color(hex: 0xf4a000), icon: "car.fill"),
        Event(name: "Graduation day", when: "Mon, 20 Oct · Campus hall", days: "7", color: Color(hex: 0xf0476b), icon: "graduationcap.fill"),
        Event(name: "Lena's birthday", when: "Wed, 22 Oct", days: "9", color: Color(hex: 0x1fb3d6), icon: "gift.fill"),
        Event(name: "Mia's birthday", when: "Mon, 3 Nov", days: "21", color: Color(hex: 0x2fcf8a), icon: "balloon.fill"),
        Event(name: "Concert night", when: "Sun, 16 Nov · The Fillmore", days: "34", color: Color(hex: 0x7a55d9), icon: "music.note"),
        Event(name: "Winter break", when: "Fri, 19 Dec", days: "67", color: Color(hex: 0x3a6fe0), icon: "snowflake"),
    ]

    static let page = Color(hex: 0xf4f4f6)
    static let ink = Color(hex: 0x111111)
    static let mute = Color(hex: 0x888888)
    static let dot = Color(hex: 0xd0d0d0)
    static let closeFill = Color(hex: 0xefeff2)
    static let closeInk = Color(hex: 0x6d6d72)
    static let phoneShadow = Color(hex: 0x1d2a8a)
    static let scrim = 0.45
    static let rowFill = Color.white

    static let margin = 20.0
    static let sheetRadius = 28.0
    /// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
    static let overshoot = 40.0
    static let header = 52.0
    static let captionHeight = 56.0
    static let nav = 44.0
    static let navGap = 24.0
    static let dotSize = 7.0
    static let dotWide = 20.0
    static let dotFor = 0.25
    static let swipeDistance = 40.0
    static let dismissVelocity = 900.0

    static let riseSpring = Animation.interpolatingSpring(mass: 1, stiffness: 170, damping: 18)
    static let dropFor = 0.4
    static let slideCurve = Animation.timingCurve(0.645, 0.045, 0.355, 1, duration: 0.7)
    static let shimmerCurve = Animation.timingCurve(0.455, 0.03, 0.515, 0.955, duration: 0.7).delay(0.5)
    static let fade = 0.16

    static func dropCurve(_ duration: Double) -> Animation { .timingCurve(0.5, 0, 1, 1, duration: duration) }

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }
}
