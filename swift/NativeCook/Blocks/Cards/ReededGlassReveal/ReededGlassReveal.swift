import SwiftUI

/// A value-for-value SwiftUI port of the approved RN account screen and its reeded lens.
struct ReededGlassReveal: View {
    var autoPlay = false
    var autoMaskMs = 8000.0
    var onReveal: (() -> Void)?
    var onMask: (() -> Void)?
    var onAddPlatform: (() -> Void)?
    var glints = 1.0
    var haptics = true
    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var scaledBody = 17.0
    @State private var driver = ReededGlassRevealDriver()
    @State private var showPlatforms = false
    @GestureState private var fingerActive = false
    @State private var fingerPending = false
    typealias R = ReededGlassRevealResources

    init(autoPlay: Bool = false, autoMaskMs: Double = 8000, onReveal: (() -> Void)? = nil,
         onMask: (() -> Void)? = nil, onAddPlatform: (() -> Void)? = nil, glints: Double = 1, haptics: Bool = true) {
        self.autoPlay = autoPlay; self.autoMaskMs = autoMaskMs; self.onReveal = onReveal; self.onMask = onMask
        self.onAddPlatform = onAddPlatform; self.glints = glints; self.haptics = haptics
        _ = R.register
        // Force cached decoding/defocus before the first display-link tick, never inside the frame renderer.
        _ = R.soft; _ = R.haze
    }
    private var pin: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "-reededGlassRevealTime"), index + 1 < args.count,
           let time = Double(args[index + 1]), time.isFinite, time >= 0 { return time }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let l = ReededGlassRevealLayout(width: geo.size.width + insets.leading + insets.trailing,
                                           height: geo.size.height + insets.top + insets.bottom, bottom: insets.bottom)
            stage(l)
                .frame(width: l.width, height: l.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: l) { configure(l) }
                .onChange(of: autoPlay) { configure(l) }
                .onChange(of: reduceMotion) { configure(l) }
                .onChange(of: haptics) { configure(l) }
                .onChange(of: autoMaskMs) { configure(l) }
        }
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active) }
        .onChange(of: fingerActive) {
            if !fingerActive && fingerPending { fingerPending = false; driver.end(0, success: false) }
        }
        .onDisappear { driver.stop() }
        .alert("Linked platforms", isPresented: $showPlatforms) {
            Button("OK", role: .cancel) {}
        } message: { Text("Vidly and Loop are linked in this preview.") }
    }
    private func configure(_ l: ReededGlassRevealLayout) {
        driver.onReveal = onReveal; driver.onMask = onMask
        driver.configure(width: l.width, auto: autoPlay, reduce: reduceMotion, pin: pin, haptics: haptics, autoMask: autoMaskMs / 1000)
    }
    private func stage(_ l: ReededGlassRevealLayout) -> some View {
        let adaptive = scaledBody / 17 > 1.25
        return ZStack(alignment: .topLeading) {
            ReededGlassRevealFrame(driver: driver, layout: l, reduced: reduceMotion, displayScale: displayScale,
                                   adaptive: adaptive, contentOpacity: driver.contentOpacity, glints: glints, fontScale: scaledBody / 17)
            Color.clear
                .frame(width: l.width, height: max(0, l.capsule.minY), alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($fingerActive) { _, state, _ in state = true }
                    .onChanged { v in
                        if !fingerPending { fingerPending = true; driver.begin(v.startLocation.x) }
                        driver.move(v.location.x, translation: v.translation.width, velocity: v.velocity.width)
                    }
                    .onEnded { v in fingerPending = false; driver.end(v.velocity.width, success: true) })
                .accessibilityLabel("Portrait behind reeded glass. Drag across it to clear the glass and show the account number.")
                .accessibilityAddTraits(.isImage)
                .accessibilityAction { driver.toggle() }
            if adaptive { adaptiveDetails(l).opacity(driver.contentOpacity) }
            else {
                Color.clear.frame(width: l.x(280), height: l.x(99))
                    .offset(x: l.x(16), y: l.y(472))
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(driver.revealed ? "Bank Account, number 4829 1730 2644" : "Bank Account, number hidden, ending 2644")
                    .allowsHitTesting(false)
                control(plus: false).frame(width: l.x(60), height: l.x(60))
                    .offset(x: l.x(304.5), y: l.y(520.9) - l.x(30))
                control(plus: true).frame(width: l.x(60), height: l.x(60))
                    .offset(x: l.x(304.5), y: l.y(633.9) - l.x(30))
                Color.clear.frame(width: l.x(250), height: l.x(28))
                    .offset(x: l.x(37.6), y: l.y(639.7) - l.x(20))
                    .accessibilityLabel("Authenticated platform").accessibilityAddTraits(.isHeader).allowsHitTesting(false)
                ForEach(0..<2, id: \.self) { i in
                    Color.clear.frame(width: l.width, height: l.x(60))
                        .offset(y: l.y(i == 0 ? 718.2 : 787.2) - l.x(30))
                        .accessibilityLabel(i == 0 ? "Vidly, 122k followers, + 120" : "Loop, 834k followers, + 220")
                        .allowsHitTesting(false)
                }
            }
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .clipped()
    }
    private func addPlatform() {
        if let onAddPlatform { onAddPlatform() } else { showPlatforms = true }
    }
    private func control(plus: Bool, visible: Bool = false) -> some View {
        Button {
            if visible { driver.pressCue() }
            if plus { addPlatform() } else { driver.toggle() }
        } label: {
            if visible {
                Canvas { c, size in
                    let cx = size.width / 2, cy = size.height / 2
                    c.fill(Path(ellipseIn: CGRect(origin: .zero, size: size)), with: .color(R.color(plus ? 0x242328 : 0xFFFFFD)))
                    let path = (plus ? R.plus : R.swap).applying(CGAffineTransform(translationX: cx - (plus ? 6.15 : 9), y: cy - (plus ? 6.15 : 6)))
                    c.stroke(path, with: .color(R.color(plus ? 0xF7F7F9 : 0x202124)), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
                }
            } else { Circle().fill(.clear).contentShape(Circle()) }
        }
        .allowsHitTesting(driver.contentOpacity > 0)
        .buttonStyle(ReededGlassRevealButtonStyle(hold: { driver.holdButton(plus, feedback: !visible) }, release: { driver.releaseButton(plus) }))
        .accessibilityLabel(plus ? "Add a platform" : driver.targetRevealed ? "Hide account number" : "Show account number")
    }
    /// RN switches to a scrollable 22 pt padded panel once the system font scale exceeds 1.25.
    private func adaptiveDetails(_ l: ReededGlassRevealLayout) -> some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 12) {
                    adaptiveText("Bank Account", size: 17).frame(maxWidth: .infinity, alignment: .leading).accessibilityAddTraits(.isHeader)
                    control(plus: false, visible: true).frame(width: 52, height: 52)
                }
                adaptiveText(driver.revealed ? "4829 1730 2644" : "**** 2644", size: 17, light: true, muted: true, lineHeight: 24)
                    .padding(.top, 12)
                HStack(spacing: 12) {
                    adaptiveText("Authenticated platform", size: 17).frame(maxWidth: .infinity, alignment: .leading).accessibilityAddTraits(.isHeader)
                    control(plus: true, visible: true).frame(width: 52, height: 52)
                }.padding(.top, 28)
                ForEach(0..<2, id: \.self) { i in
                    HStack(alignment: .top, spacing: 16) {
                        Canvas { c, _ in
                            c.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 52, height: 52)), with: .color(R.color(0x222224)))
                            ReededGlassRevealFrame.mark(c, i, 26, 26)
                        }.frame(width: 52, height: 52).accessibilityHidden(true)
                        VStack(alignment: .leading, spacing: 0) {
                            adaptiveText(i == 0 ? "Vidly" : "Loop", size: 16, light: true)
                            adaptiveText(i == 0 ? "122k followers" : "834k followers", size: 16, light: true, muted: true)
                            adaptiveText(i == 0 ? "+ 120" : "+ 220", size: 16)
                        }
                    }.padding(.top, 24)
                }
            }
            .padding(22).padding(.bottom, max(l.bottom, 24) - 22)
        }
        .background(R.color(0x161619), in: UnevenRoundedRectangle(topLeadingRadius: 28, topTrailingRadius: 28))
        .frame(width: l.width - 32, height: l.height - min(l.y(472), l.height * 0.4), alignment: .topLeading)
        .offset(x: 16, y: min(l.y(472), l.height * 0.4))
    }
    private func adaptiveText(_ text: String, size: Double, light: Bool = false, muted: Bool = false, lineHeight: Double? = nil) -> some View {
        let height = lineHeight ?? (size == 16 ? 24 : 23)
        return Text(text).font(.custom(light ? "UrbanistRoman-Light" : "UrbanistRoman-Regular", size: size))
            .foregroundStyle(R.color(muted ? 0x959598 : 0xF7F7F9))
            .lineSpacing(max(0, height - size * 1.2) * scaledBody / 17)
            .fixedSize(horizontal: false, vertical: true)
            .frame(minHeight: height * scaledBody / 17, alignment: .leading)
    }
}

private struct ReededGlassRevealButtonStyle: ButtonStyle {
    let hold: () -> Void
    let release: () -> Void
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.onChange(of: configuration.isPressed) {
            if configuration.isPressed { hold() } else { release() }
        }
    }
}
