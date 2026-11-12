import SwiftUI

/// A direct port of the approved RN pay screen: two shader passes and the same projected print.
struct GlassPayMorph: View {
    var autoPlay: Bool
    static let loop = 4.4
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var driver = GlassPayMorphDriver()
    @State private var paidFrom = 0.0
    @State private var paidTo = 0.0
    @State private var paidAt: Date?
    @GestureState private var gestureActive = false
    @State private var gesturePending = false
    private typealias M = GlassPayMorphMotion

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        // Registration must precede the first text measurement and frame.
        _ = GlassPayMorphResources.register
    }

    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "-glassPayMorphTime"), index + 1 < args.count,
           let value = Double(args[index + 1]), value.isFinite, value >= 0 { return value }
        #endif
        return nil
    }

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size, insets.top)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onChange(of: autoPlay) { driver.reset(at: Date()) }
        .onChange(of: reduceMotion) { driver.reset(at: Date()) }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active, at: Date()) }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                // GestureState resets on cancellation as well as successful completion (RN onFinalize).
                if reduceMotion { togglePaid() } else { driver.end(velocity: .zero, at: Date()) }
                gesturePending = false
            }
        }
        .task(id: paidAt) {
            guard paidAt != nil else { return }
            do {
                try await Task.sleep(for: .milliseconds(220))
                paidFrom = paidTo; paidAt = nil
            } catch { /* A new tap replaces this explicit cross-fade duration. */ }
        }
    }

    private func stage(_ size: CGSize, _ top: Double) -> some View {
        let scale = size.width / 402, dy = (size.height - 874 * scale) / 2
        let g = GlassPayMorphFrame.Geometry(width: size.width, height: size.height, scale: scale, dy: dy,
                                           top: (top - dy) / scale, px: 1 / (displayScale * scale))
        return ZStack(alignment: .topLeading) {
            GlassPayMorphResources.color(0x1B2135)
            TimelineView(.animation(paused: scenePhase != .active || (reduceMotion && paidAt == nil) || (!reduceMotion && pinnedTime != nil))) { timeline in
                if reduceMotion {
                    let k = paid(at: timeline.date)
                    ZStack(alignment: .topLeading) {
                        GlassPayMorphFrame(scene: M.sceneAt(0), geometry: g)
                        if k > 0.001 { GlassPayMorphFrame(scene: M.sceneAt(M.paidHold), geometry: g).compositingGroup().opacity(k) }
                    }
                } else {
                    let scene = pinnedTime.map { M.sceneAt($0) } ?? driver.frame(at: timeline.date, auto: autoPlay)
                    GlassPayMorphFrame(scene: scene, geometry: g)
                }
            }
            .allowsHitTesting(false)
            Color.clear.contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($gestureActive) { _, state, _ in state = true }
                    .onChanged { v in
                        gesturePending = true
                        guard !reduceMotion else { return }
                        let date = v.time
                        if !driver.fingerDown {
                            driver.begin(CGPoint(x: v.startLocation.x / scale, y: (v.startLocation.y - dy) / scale), at: date)
                        }
                        driver.move(CGSize(width: v.translation.width / scale, height: v.translation.height / scale),
                                    velocity: CGSize(width: v.velocity.width / scale, height: v.velocity.height / scale), at: date)
                    }
                    .onEnded { v in
                        gesturePending = false
                        if reduceMotion { togglePaid() }
                        else { driver.end(velocity: CGSize(width: v.velocity.width / scale, height: v.velocity.height / scale), at: v.time) }
                    })
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Solune card ending 4417. Hold near reader, or tap the card to pay.")
        .accessibilityHint("Pays with the card, or shows the card again after paying.")
        .accessibilityAction {
            if reduceMotion { togglePaid() } else { driver.toggle(at: Date()) }
        }
    }

    private func paid(at date: Date) -> Double {
        guard let start = paidAt else { return paidTo }
        let t = M.clamp01(date.timeIntervalSince(start) / 0.22)
        // Reanimated withTiming's default Easing.inOut(Easing.quad).
        let k = t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
        return paidFrom + (paidTo - paidFrom) * k
    }
    private func togglePaid() {
        let date = Date(), current = paid(at: date)
        paidFrom = current; paidTo = current > 0.5 ? 0 : 1; paidAt = date
    }
}
