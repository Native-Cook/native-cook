import SwiftUI

/// SwiftUI port of the approved RN screen. The source's default explorer returns to its 0.8x reading.
struct SparkSphereScore: View {
    var autoPlay = false
    var onBack: (() -> Void)?
    var onInfo: (() -> Void)?
    var onPreviousWeek: (() -> Void)?
    static let loop = SparkSphereScoreMotion.loop
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var driver = SparkSphereScoreDriver()
    @State private var pinCancelled = false
    @GestureState private var rulerActive = false
    @GestureState private var probeActive = false
    private typealias M = SparkSphereScoreMotion

    init(autoPlay: Bool = false, onBack: (() -> Void)? = nil, onInfo: (() -> Void)? = nil, onPreviousWeek: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.onBack = onBack; self.onInfo = onInfo; self.onPreviousWeek = onPreviousWeek
        _ = SparkSphereScoreResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if !pinCancelled, let i = args.firstIndex(of: "-sparkSphereScoreTime"), i + 1 < args.count,
           let value = Double(args[i + 1]), value.isFinite, value >= 0 { return value }
        #endif
        return nil
    }
    private var fontScale: Double {
        // RN clamps layout and native text to 1.0 ... 1.2; the canvas numbers do not scale with Dynamic Type.
        switch dynamicTypeSize {
        case .xLarge: return 19.0 / 17
        case .xxLarge, .xxxLarge, .accessibility1, .accessibility2, .accessibility3, .accessibility4, .accessibility5: return 1.2
        default: return 1
        }
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let g = SparkSphereScoreLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom, fontScale: fontScale)
            stage(g)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onChange(of: autoPlay) { driver.setReducedMotion(reduceMotion) }
        .onChange(of: reduceMotion) { driver.setReducedMotion(reduceMotion) }
        .onChange(of: scenePhase) { driver.suspend() }
        .onChange(of: rulerActive) { if !rulerActive { driver.cancelRuler() } }
        .onChange(of: probeActive) { if !probeActive { driver.cancelProbe() } }
    }

    private func stage(_ g: SparkSphereScoreLayout) -> some View {
        let art = SparkSphereScoreArt(g)
        let a11y = driver.accessibilityReading, score = M.score(a11y), delta = score - 32.2
        return ZStack(alignment: .topLeading) {
            SparkSphereScoreResources.color(0x08080A)
            TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil || (reduceMotion && !driver.activeAnimation))) { timeline in
                let scene = driver.frame(at: timeline.date, auto: autoPlay, reduce: reduceMotion, pin: pinnedTime)
                SparkSphereScoreFrame(scene: scene, g: g, art: art, pixelScale: displayScale, reduce: reduceMotion)
            }
            Color.clear.contentShape(Rectangle())
                .frame(width: 2 * g.discR, height: 2 * g.discR)
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($probeActive) { _, state, _ in state = true }
                    .onChanged { value in
                        if !driver.probeDown {
                            synchronize(at: value.time)
                            driver.beginProbe(point: value.startLocation, layout: g)
                        }
                        if value.translation != .zero {
                            driver.moveProbe(point: value.location, velocity: value.velocity, translation: value.translation, layout: g)
                        }
                    }
                    .onEnded { driver.endProbe(translation: $0.translation) })
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isImage)
                .accessibilityLabel("VITAL AGE \(M.fixed1(score)), \(M.fixed1(abs(delta))) years \(delta > 0.049 ? "older" : "younger")")
                .offset(x: g.discCx - g.discR, y: g.discCy - g.discR)
            Color.clear.contentShape(Rectangle())
                .frame(width: g.width, height: g.rulerZoneH)
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($rulerActive) { _, state, _ in state = true }
                    .onChanged { value in
                        if !driver.rulerDown {
                            synchronize(at: value.time)
                            driver.beginRuler(x: value.startLocation.x, layout: g)
                        }
                        if value.translation != .zero { driver.moveRuler(x: value.location.x, velocity: value.velocity.width, layout: g) }
                    }
                    .onEnded { driver.endRuler(velocity: $0.velocity.width) })
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Pace of aging")
                .accessibilityHint("Swipe up or down to change the pace by a tenth.")
                .accessibilityValue("\(M.fixed1(a11y))x. VITAL AGE, \(M.fixed1(score))")
                .accessibilityAdjustableAction { direction in
                    synchronize(at: Date())
                    switch direction {
                    case .increment: driver.adjust(1)
                    case .decrement: driver.adjust(-1)
                    @unknown default: break
                    }
                }
                .offset(y: g.rulerTop)
            if let onBack { chrome("Back", g.x(27.7) - 14, g.yA(83.1) - 22, onBack) }
            if let onInfo { chrome("About vital age", g.x(365.8) - 22, g.yA(82.8) - 22, onInfo) }
            if let onPreviousWeek { chrome("Previous week", g.x(121.2) - 18, g.yA(127.8) - 22, onPreviousWeek) }
            // Canvas typography is exposed as text at its own screen location, without drawing a second visible layer.
            semanticText("LONGEVITY", frame: CGRect(x: 60 * g.s, y: g.yA(66), width: g.width - 120 * g.s, height: 32 * g.s), header: true)
            semanticText("Next update in 6 days", frame: CGRect(x: 60 * g.s, y: g.yA(84), width: g.width - 120 * g.s, height: 16 * g.s))
            semanticText("AUG 19  -  AUG 26", frame: CGRect(x: 145 * g.s, y: g.yA(116), width: 112 * g.s, height: 20 * g.s))
            semanticText(SparkSphereScoreResources.titles[M.band(a11y)] + ". " + SparkSphereScoreResources.bodies[M.band(a11y)],
                         frame: CGRect(x: g.cardX, y: g.cardTop, width: g.cardW, height: g.cardH))
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
    }
    private func synchronize(at date: Date) {
        _ = driver.frame(at: date, auto: autoPlay, reduce: reduceMotion, pin: pinnedTime)
        pinCancelled = true
    }
    private func chrome(_ label: String, _ x: Double, _ y: Double, _ action: @escaping () -> Void) -> some View {
        Button(action: action) { Color.clear.contentShape(Rectangle()) }
            .buttonStyle(.plain).frame(width: 44, height: 44).accessibilityLabel(label).offset(x: x, y: y)
    }
    private func semanticText(_ text: String, frame: CGRect, header: Bool = false) -> some View {
        Color.clear.frame(width: frame.width, height: frame.height)
            .accessibilityElement(children: .ignore).accessibilityLabel(text)
            .accessibilityAddTraits(header ? .isHeader : [])
            .allowsHitTesting(false).offset(x: frame.minX, y: frame.minY)
    }
}
