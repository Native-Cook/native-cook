import SwiftUI

/// Calls `content` with the value as it animates; the closure's own mapping (ramps, lerps) runs
/// on every frame, which SwiftUI's modifiers would otherwise interpolate linearly end to end.
private struct MorphingModeSheetReader<Value: VectorArithmetic, Content: View>: View, Animatable {
    var value: Value
    @ViewBuilder let content: (Value) -> Content

    var animatableData: Value {
        get { value }
        set { value = newValue }
    }

    var body: some View { content(value) }
}

/// A prompt screen with a sheet over it. The sheet opens as a compact list of modes; touching the
/// model at the right of the first row grows the same sheet upward into a list of models, while
/// the two lists cross-fade inside it, and a pick shrinks it back. The page behind blurs and dims
/// while the sheet is up. The grabber drags the sheet between its two heights.
struct MorphingModeSheet: View {
    /// The rows of the compact sheet; the first one shows the chosen model at its right.
    var modes = M.modes
    /// The rows of the tall sheet.
    var models = M.models
    /// Plays the whole interaction by itself, over and over, and gives way to a touch.
    var autoPlay = false
    var wordmark = "Briar"
    var placeholder = "Ask anything..."
    var compactTitle = "Choose a mode"
    var tallTitle = "Models"
    var incognitoLabel = "Incognito mode"
    var incognitoCaption = "Activity won't be saved"
    var onModeChange: ((MorphingModeSheetMode) -> Void)? = nil
    var onModelChange: ((MorphingModeSheetModel) -> Void)? = nil
    var onIncognitoChange: ((Bool) -> Void)? = nil

    private typealias M = MorphingModeSheetMotion
    private typealias Rows = MorphingModeSheetRowMetrics

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // 0 below the screen, 1 up; and 0 compact, 1 tall.
    @State private var show = 0.0
    @State private var grow = 0.0
    @State private var grabbed = 0.0
    /// 1 while the drawn keyboard is up, as it is on the page at rest.
    @State private var keys = 1.0
    @State private var dragging = false
    @State private var mode = 0
    @State private var model = 0
    @State private var incognito = false
    @State private var isOpen = false
    @State private var isTall = false
    @State private var touches = 0
    @State private var autoValue = false

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
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func scene(screen: CGSize, insets: EdgeInsets) -> some View {
        let sheetWidth = screen.width - M.side * 2
        let bottom = max(insets.bottom - 14, M.side)
        let listHeight = Double(models.count) * (Rows.modelHeight + Rows.modelGap) + Double(models.filter { $0.heading != nil }.count) * Rows.headingHeight
        // As tall as the list needs, and no taller than the screen allows.
        let tallHeight = min(M.headerHeight + listHeight + 16, screen.height - insets.top - bottom - 24)
        let reach = tallHeight - M.compactHeight

        return MorphingModeSheetReader(value: AnimatablePair(show, grow)) { pair in
            let shown = pair.first
            let grown = pair.second
            ZStack(alignment: .topLeading) {
                page(screen: screen, insets: insets)
                    .blur(radius: M.blur * shown)
                Color.black
                    .opacity(M.dim * shown)
                    .frame(width: screen.width, height: screen.height)
                    .allowsHitTesting(false)
                if isOpen {
                    Color.clear
                        .frame(width: screen.width, height: screen.height)
                        .contentShape(Rectangle())
                        .onTapGesture {
                            touched()
                            close()
                        }
                        .accessibilityLabel("Close")
                        .accessibilityAddTraits(.isButton)
                }
                let tall = M.lerp(M.compactHeight, tallHeight, grown)
                // The shadow sits outside the clip that keeps the two lists to the sheet's shape.
                sheet(width: sheetWidth, tallHeight: tallHeight, grown: grown, reach: reach)
                    .frame(width: sheetWidth, height: tall, alignment: .top)
                    .background(M.sheet, in: RoundedRectangle(cornerRadius: M.sheetRadius))
                    .clipShape(RoundedRectangle(cornerRadius: M.sheetRadius))
                    .background(
                        RoundedRectangle(cornerRadius: M.sheetRadius)
                            .fill(M.sheet)
                            .shadow(color: .black.opacity(0.16), radius: 32, y: 12)
                    )
                    .offset(x: M.side, y: screen.height - bottom - tall + (1 - shown) * (tall + bottom + 24))
                    .allowsHitTesting(isOpen)
            }
        }
    }

    private func page(screen: CGSize, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear

            HStack {
                AsyncImage(url: URL(string: M.avatar), transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image { image.resizable().scaledToFill().transition(.opacity) }
                }
                .frame(width: 36, height: 36)
                .background(M.avatarTint)
                .clipShape(Circle())
                .overlay(Circle().strokeBorder(.white, lineWidth: 2))
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    iconButton("clock")
                    iconButton("square.and.pencil")
                }
            }
            .padding(.horizontal, 16)
            .frame(width: screen.width, height: 40)
            .offset(y: insets.top + 6)

            VStack(spacing: 6) {
                HStack(spacing: 10) {
                    Image(systemName: "sparkles")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.white)
                        .frame(width: 34, height: 34)
                        .background(
                            LinearGradient(colors: [M.orangeLight, M.orange, M.orangeDeep], startPoint: .topLeading, endPoint: .bottomTrailing),
                            in: RoundedRectangle(cornerRadius: 11)
                        )
                        .rotationEffect(.degrees(-8))
                    Text(wordmark)
                        .font(.system(size: 36, weight: .heavy))
                        .tracking(-1.2)
                        .foregroundStyle(M.ink)
                        .frame(height: 42)
                    Text("PRO")
                        .font(.system(size: 10, weight: .heavy))
                        .tracking(0.8)
                        .foregroundStyle(M.tealDeep)
                        .padding(.horizontal, 7)
                        .frame(height: 20)
                        .overlay(RoundedRectangle(cornerRadius: 6).strokeBorder(M.teal, lineWidth: 1.5))
                        .padding(.top, 4)
                }
                Text("Where good questions begin")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(M.mute)
                    .frame(height: 20)
            }
            .frame(width: screen.width)
            .offset(y: insets.top + 118)

            Button {
                touched()
                open()
            } label: {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 2) {
                        Capsule().fill(M.teal).frame(width: 2, height: 22).opacity(keys)
                        Text(placeholder)
                            .font(.system(size: 17))
                            .foregroundStyle(M.mute)
                    }
                    .frame(height: 22)
                    Spacer(minLength: 0)
                    HStack {
                        HStack(spacing: 8) {
                            Image(systemName: "plus")
                                .font(.system(size: 17))
                                .frame(width: 36, height: 36)
                                .overlay(Circle().strokeBorder(M.outline, lineWidth: M.hairline))
                            HStack(spacing: 5) {
                                Image(systemName: "globe").font(.system(size: 13, weight: .medium))
                                Text("Web").font(.system(size: 14, weight: .semibold))
                            }
                            .foregroundStyle(M.tealDeep)
                            .padding(.horizontal, 12)
                            .frame(height: 36)
                            .background(M.webFill, in: Capsule())
                            .overlay(Capsule().strokeBorder(M.webBorder, lineWidth: M.hairline))
                        }
                        Spacer(minLength: 0)
                        HStack(spacing: 14) {
                            Image(systemName: "mic").font(.system(size: 18))
                            Image(systemName: "waveform")
                                .font(.system(size: 16, weight: .semibold))
                                .foregroundStyle(.white)
                                .frame(width: 38, height: 38)
                                .background(M.teal, in: Circle())
                                .compositingGroup()
                                .shadow(color: M.teal.opacity(0.4), radius: 8, y: 3)
                        }
                    }
                    .foregroundStyle(M.ink)
                }
                .padding(.horizontal, 16)
                .padding(.top, 18)
                .padding(.bottom, 12)
                .frame(width: screen.width - (M.pageMargin - 4) * 2, height: 112)
                .background(.white, in: RoundedRectangle(cornerRadius: 26))
                .overlay(RoundedRectangle(cornerRadius: 26).strokeBorder(M.outline, lineWidth: M.hairline))
                .compositingGroup()
                .shadow(color: M.inputShadow.opacity(0.08), radius: 24, y: 10)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(placeholder)
            .offset(x: M.pageMargin - 4, y: insets.top + 226)

            HStack(spacing: 8) {
                ForEach(M.ideas, id: \.label) { idea in
                    HStack(spacing: 6) {
                        Image(systemName: idea.icon)
                            .font(.system(size: 13))
                            .foregroundStyle(M.mute)
                        Text(idea.label)
                            .font(.system(size: 14, weight: .medium))
                            .foregroundStyle(M.ideaInk)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(.white, in: Capsule())
                    .overlay(Capsule().strokeBorder(M.outline, lineWidth: M.hairline))
                    .fixedSize()
                }
            }
            .padding(.leading, M.pageMargin - 4)
            .frame(width: screen.width, alignment: .leading)
            .clipped()
            .offset(y: insets.top + 356)

            MorphingModeSheetKeyboard(width: screen.width, bottomInset: insets.bottom)
                .offset(y: (1 - keys) * (MorphingModeSheetKeyboardMetrics.height + insets.bottom + 12))
                .frame(width: screen.width, height: screen.height, alignment: .bottom)
        }
        .frame(width: screen.width, height: screen.height, alignment: .topLeading)
    }

    private func iconButton(_ symbol: String) -> some View {
        Image(systemName: symbol)
            .font(.system(size: 16))
            .foregroundStyle(M.ink)
            .frame(width: 38, height: 38)
            .background(.white, in: Circle())
            .overlay(Circle().strokeBorder(M.outline, lineWidth: M.hairline))
    }

    private func sheet(width: Double, tallHeight: Double, grown: Double, reach: Double) -> some View {
        ZStack(alignment: .top) {
            // The compact list.
            VStack(spacing: Rows.modeGap) {
                ForEach(Array(modes.enumerated()), id: \.offset) { index, item in
                    MorphingModeSheetModeRow(mode: item, selected: index == mode) {
                        touched()
                        pickMode(index)
                    } trailing: {
                        if index == 0 { valueButton }
                    }
                }
                MorphingModeSheetSwitchRow(label: incognitoLabel, caption: incognitoCaption, on: incognito) {
                    touched()
                    incognito.toggle()
                    onIncognitoChange?(incognito)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, M.headerHeight)
            .frame(width: width, height: M.compactHeight, alignment: .top)
            .opacity(1 - M.ramp(grown, 0, 0.4))
            .allowsHitTesting(!isTall)
            .frame(width: width, height: tallHeight, alignment: .top)

            // The tall list.
            ScrollView(showsIndicators: false) {
                VStack(spacing: 0) {
                    ForEach(Array(models.enumerated()), id: \.offset) { index, item in
                        MorphingModeSheetModelRow(model: item, index: index, selected: index == model, grow: grown) {
                            touched()
                            choose(index)
                            collapse()
                        }
                    }
                }
                .padding(.bottom, 16)
            }
            .padding(.top, M.headerHeight)
            .frame(width: width, height: tallHeight, alignment: .top)
            .opacity(M.ramp(grown, 0.3, 0.7))
            .allowsHitTesting(isTall)

            header(width: width, grown: grown, reach: reach)
        }
        .frame(width: width, height: tallHeight, alignment: .top)
    }

    /// The model chosen, at the right of the first mode row; touching it opens the list of models.
    private var valueButton: some View {
        Button {
            touched()
            expand()
        } label: {
            HStack(spacing: 2) {
                Text(models[model].name)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(M.valueInk)
                Image(systemName: "chevron.right")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(M.mute)
            }
            .padding(.leading, 10)
            .padding(.trailing, 6)
            .frame(height: 26)
            .background(.white.opacity(0.8), in: Capsule())
            .contentShape(Rectangle())
        }
        .buttonStyle(MorphingModeSheetValueStyle(forced: autoValue))
        .accessibilityLabel("Choose a model")
    }

    private func header(width: Double, grown: Double, reach: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear
            Capsule().fill(M.grabber).frame(width: 36, height: 5)
                .frame(width: width)
                .offset(y: 8)
            Text(compactTitle)
                .modifier(Title())
                .opacity(1 - M.ramp(grown, 0.1, 0.5))
                .offset(x: 18, y: 22)
            Text(tallTitle)
                .modifier(Title())
                .opacity(M.ramp(grown, 0.4, 0.8))
                .offset(x: 18, y: 22)
            Button {
                touched()
                close()
            } label: {
                Image(systemName: "xmark")
                    .font(.system(size: 11, weight: .bold))
                    .foregroundStyle(M.closeInk)
                    .frame(width: 28, height: 28)
                    .background(M.closeFill, in: Circle())
                    .padding(8)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-8)
            .accessibilityLabel("Close")
            .offset(x: width - 14 - 28, y: 20)
        }
        .frame(width: width, height: M.headerHeight, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(pan(reach: reach))
        .frame(width: width, height: M.headerHeight, alignment: .topLeading)
        .frame(maxHeight: .infinity, alignment: .top)
    }

    private func pan(reach: Double) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                if !dragging {
                    dragging = true
                    grabbed = grow
                    touched()
                }
                instantly { grow = min(1, max(0, grabbed - value.translation.height / reach)) }
            }
            .onEnded { value in
                dragging = false
                let vy = value.velocity.height
                if grabbed < 0.5 && (value.translation.height > M.dismissDrag || vy > M.snapVelocity * 2) {
                    close()
                    return
                }
                let up = vy < -M.snapVelocity || (abs(vy) <= M.snapVelocity && grow > 0.5)
                if up { expand() } else { collapse() }
            }
    }

    // MARK: Stages

    private func instantly(_ change: () -> Void) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t, change)
    }

    private var settle: Animation { reduceMotion ? .easeInOut(duration: M.fade) : M.morphSpring }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoValue = false
        touches += 1
    }

    private func open() {
        guard !isOpen else { return }
        isOpen = true
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.riseSpring) { show = 1 }
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.keysDown) { keys = 0 }
    }

    private func close() {
        isOpen = false
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.dropCurve) { show = 0 }
        withAnimation(reduceMotion ? .easeInOut(duration: M.fade) : M.keysUp.delay(M.keysUpAfter)) { keys = 1 }
        isTall = false
        withAnimation(settle) { grow = 0 }
    }

    private func expand() {
        isTall = true
        withAnimation(settle) { grow = 1 }
    }

    private func collapse() {
        isTall = false
        withAnimation(settle) { grow = 0 }
    }

    private func choose(_ index: Int) {
        model = index
        onModelChange?(models[index])
    }

    private func pickMode(_ index: Int) {
        mode = index
        onModeChange?(modes[index])
    }

    /// Everything back as it was at the start, with no animation.
    private func rest() {
        instantly {
            show = 0
            grow = 0
            grabbed = 0
            keys = 1
            autoValue = false
            isOpen = false
            isTall = false
            mode = 0
            model = 0
            incognito = false
        }
    }

    // MARK: autoPlay

    private var steps: [(at: Double, run: () -> Void)] {
        [
            (M.openAt, open),
            (M.valueTapAt, { autoValue = true }),
            (M.valueTapAt + M.tapFor, { autoValue = false; expand() }),
            (M.pickAt, { model = M.picked }),
            (M.collapseAt, collapse),
            (M.researchAt, { mode = 1 }),
            (M.incognitoOnAt, { incognito = true }),
            (M.incognitoOffAt, { incognito = false }),
            (M.closeAt, close),
            (M.restAt, rest),
        ]
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

    private struct Title: ViewModifier {
        func body(content: Content) -> some View {
            content
                .font(.system(size: 18, weight: .bold))
                .tracking(-0.3)
                .foregroundStyle(M.ink)
                .frame(height: 24)
        }
    }
}

/// Squashes to `MorphingModeSheetMotion.pressScale` while pressed, or while the autoPlay loop holds it.
private struct MorphingModeSheetValueStyle: ButtonStyle {
    let forced: Bool

    func makeBody(configuration: Configuration) -> some View {
        let down = configuration.isPressed || forced
        configuration.label
            .scaleEffect(down ? MorphingModeSheetMotion.pressScale : 1)
            .animation(.easeInOut(duration: down ? 0.08 : 0.14), value: down)
    }
}

enum MorphingModeSheetMotion {
    static let modes = [
        MorphingModeSheetMode(label: "Search", caption: "Fast answers to everyday questions", icon: "magnifyingglass", pro: true),
        MorphingModeSheetMode(label: "Research", caption: "Deep research on any topic", icon: "square.stack.3d.up", pro: true),
        MorphingModeSheetMode(label: "Labs", caption: "Create projects from scratch", icon: "testtube.2", pro: true),
    ]
    static let models = [
        MorphingModeSheetModel(name: "Best", caption: "Picks the right model for each question"),
        MorphingModeSheetModel(name: "Pulse", caption: "Our fastest model for quick lookups"),
        MorphingModeSheetModel(name: "Orbit", caption: "Careful, balanced and clear"),
        MorphingModeSheetModel(name: "Vega", caption: "The newest model from Vega Labs", tag: .new),
        MorphingModeSheetModel(name: "Atlas", caption: "Broad knowledge, plain writing"),
        MorphingModeSheetModel(name: "Vega Thinking", caption: "Vega with extended reasoning", tag: .new, heading: "Reasoning"),
        MorphingModeSheetModel(name: "Atlas Thinking", caption: "Atlas working through hard problems"),
        MorphingModeSheetModel(name: "Nova Max", caption: "The most capable reasoning model", tag: .max),
        MorphingModeSheetModel(name: "Pulse Thinking", caption: "Pulse with a short reasoning pass"),
    ]

    static let avatar = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=160&h=160&q=80"
    static let ideas: [(icon: String, label: String)] = [
        ("lightbulb", "Explain simply"),
        ("airplane", "Plan a weekend"),
        ("arrow.triangle.branch", "Compare"),
    ]

    /// The autoPlay loop, in seconds from its start.
    static let loop = 9.0
    static let openAt = 0.8
    static let valueTapAt = 1.8
    static let tapFor = 0.12
    static let pickAt = 3.0
    static let collapseAt = 4.0
    static let researchAt = 5.2
    static let incognitoOnAt = 6.0
    static let incognitoOffAt = 6.8
    static let closeAt = 7.4
    static let restAt = 8.2
    /// The model the loop picks, by place in the list.
    static let picked = 2
    /// Seconds a touch keeps the loop away.
    static let userHold = 3.0

    static let page = Color(hex: 0xfbfbfa)
    static let ink = Color(hex: 0x16181a)
    static let mute = Color(hex: 0x94969b)
    static let outline = Color(hex: 0xe7e7e5)
    static let sheet = Color.white
    static let grabber = Color(hex: 0xd4d4d4)
    static let closeFill = Color(hex: 0xececec)
    static let closeInk = Color(hex: 0x6b6d72)
    static let valueInk = Color(hex: 0x5d6066)
    static let ideaInk = Color(hex: 0x4a4d52)
    static let teal = Color(hex: 0x20b8cd)
    static let tealDeep = Color(hex: 0x1e8f95)
    static let webFill = Color(hex: 0xeef7f7)
    static let webBorder = Color(hex: 0xcfe9ea)
    static let inputShadow = Color(hex: 0x1a1a1a)
    static let orange = Color(hex: 0xf26a1b)
    static let orangeLight = Color(hex: 0xff8a3d)
    static let orangeDeep = Color(hex: 0xd9480f)
    /// Shown under the avatar until the photo has loaded.
    static let avatarTint = Color(hex: 0xd9c9b8)
    static let dim = 0.08
    /// React Native's hairline width doubled, on a 3x screen.
    static let hairline = 2.0 / 3

    static let pageMargin = 20.0
    static let side = 8.0
    static let headerHeight = 60.0
    static let compactHeight = headerHeight + MorphingModeSheetRowMetrics.modeHeight * 4 + MorphingModeSheetRowMetrics.modeGap * 3 + 14
    static let sheetRadius = 28.0
    /// Blur radius of the page at full strength (the React Native version's intensity 50).
    static let blur = 8.0
    static let snapVelocity = 500.0
    static let dismissDrag = 90.0
    static let pressScale = 0.88

    static let riseSpring = Animation.spring(duration: 0.5, bounce: 0.15)
    static let morphSpring = Animation.spring(duration: 0.45, bounce: 0.12)
    static let dropCurve = Animation.timingCurve(0.5, 0, 1, 1, duration: 0.4)
    static let fade = 0.16
    /// The drawn keyboard drops as the sheet rises (ease-in quad) and comes back after it (ease-out cubic).
    static let keysDown = Animation.timingCurve(0.11, 0, 0.5, 0, duration: 0.26)
    static let keysUp = Animation.timingCurve(0.33, 1, 0.68, 1, duration: 0.38)
    static let keysUpAfter = 0.12

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }
    static func ramp(_ value: Double, _ from: Double, _ to: Double) -> Double { min(1, max(0, (value - from) / (to - from))) }
}
