import SwiftUI

/// Value-for-value SwiftUI port of the approved React Native Coin Pile Collect screen.
struct CoinPileCollect: View {
    static let loop = 8.0
    var autoPlay: Bool
    var amountCents: Double
    var tilt: Bool
    var haptics: Bool
    var sound: Bool
    var onCollect: ((Double, Double) -> Void)?
    var onEmpty: ((Double) -> Void)?
    var onRefill: (() -> Void)?
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @ScaledMetric(relativeTo: .body) private var systemFontScale = 1.0
    @State private var driver: CoinPileCollectDriver
    @State private var resources = CoinPileCollectResources(fs: 1)
    @State private var isEmpty = false
    @State private var reducedStart: Date?
    @GestureState private var gestureActive = false
    @State private var gesturePending = false

    init(autoPlay: Bool = false, amountCents: Double = 22034, tilt: Bool = true,
         haptics: Bool = true, sound: Bool = false,
         onCollect: ((Double, Double) -> Void)? = nil, onEmpty: ((Double) -> Void)? = nil,
         onRefill: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.amountCents = amountCents; self.tilt = tilt
        self.haptics = haptics; self.sound = sound
        self.onCollect = onCollect; self.onEmpty = onEmpty; self.onRefill = onRefill
        _ = CoinPileCollectResources.register
        let driver = CoinPileCollectDriver()
        driver.reset(total: amountCents, autoPlay: autoPlay, reduce: false)
        _driver = State(initialValue: driver)
    }
    private var fontScale: Double { min(max(systemFontScale, 1), 1.12) }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-coinPileCollectTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size, insets.top, insets.bottom)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { configure(reset: true); resources = CoinPileCollectResources(fs: fontScale) }
        .onDisappear { driver.setActive(false, tilt: tilt) }
        .onChange(of: autoPlay) { configure(reset: true) }
        .onChange(of: amountCents) { configure(reset: true) }
        .onChange(of: reduceMotion) { configure(reset: true); reducedStart = nil }
        .onChange(of: tilt) { driver.setActive(scenePhase == .active, tilt: tilt) }
        .onChange(of: haptics) { driver.haptics = haptics }
        .onChange(of: sound) { driver.sound = sound }
        .onChange(of: fontScale) { resources = CoinPileCollectResources(fs: fontScale) }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active, tilt: tilt) }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending { driver.end(); gesturePending = false }
        }
        .task(id: reducedStart) {
            guard reducedStart != nil else { return }
            // 3 × 120 ms fade out, 1.2 s empty, 3 × 120 ms fade in, including two 20 ms end holds.
            do { try await Task.sleep(for: .milliseconds(2250)); reducedStart = nil }
            catch { /* Another touch extends the frame window. */ }
        }
    }
    private func configure(reset: Bool) {
        if reset { driver.reset(total: amountCents, autoPlay: autoPlay, reduce: reduceMotion); isEmpty = false }
        driver.haptics = haptics; driver.sound = sound
        driver.onCollect = onCollect
        driver.onEmpty = { total in isEmpty = true; onEmpty?(total) }
        driver.onRefill = { isEmpty = false; onRefill?() }
        driver.setActive(scenePhase == .active, tilt: tilt)
    }
    private func stage(_ size: CGSize, _ top: Double, _ bottom: Double) -> some View {
        let s = min(size.width / 402, max(size.height - top - bottom, 1) / 714)
        let ox = (size.width - 402 * s) / 2, oy = top - 62 * s
        let g = CoinPileCollectFrame.Geometry(width: size.width, height: size.height, scale: s, ox: ox, oy: oy, px: 1 / (s * displayScale))
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil || (reduceMotion && reducedStart == nil))) { timeline in
                CoinPileCollectFrame(frame: driver.frame(at: timeline.date, pin: pinnedTime), geometry: g, resources: resources)
            }
            Color.clear.contentShape(Rectangle())
                .frame(width: 174 * s, height: 340 * s)
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($gestureActive) { _, active, _ in active = true }
                    .onChanged { value in
                        let p = CGPoint(x: value.location.x / s, y: value.location.y / s)
                        if !gesturePending {
                            driver.begin(CGPoint(x: value.startLocation.x / s, y: value.startLocation.y / s))
                            gesturePending = true
                        }
                        driver.move(p)
                        if reduceMotion { reducedStart = value.time }
                    }
                    .onEnded { _ in driver.end(); gesturePending = false })
                .offset(x: ox + 204 * s, y: oy + 250 * s)
                .accessibilityHidden(true)
            Color.clear.frame(width: 240 * s, height: 120 * s)
                .offset(x: ox + 24 * s, y: oy + 150 * s)
                .accessibilityElement().accessibilityAddTraits(.isHeader).accessibilityLabel("Today’s offers")
                .allowsHitTesting(false)
            Color.clear.frame(width: 174 * s, height: 100 * s)
                .offset(x: ox + 204 * s, y: oy + 250 * s)
                .accessibilityElement().accessibilityLabel(isEmpty ? "Nothing left to collect" : "You earned \(money) in September")
                .allowsHitTesting(false)
            Color.clear.frame(width: 150 * s, height: 60 * s)
                .offset(x: ox + 228 * s, y: oy + 518 * s)
                .accessibilityElement().accessibilityAddTraits(.isButton)
                .accessibilityLabel(isEmpty ? "Nothing left to collect" : "Collect \(money)")
                .accessibilityRespondsToUserInteraction(!isEmpty)
                .disabled(isEmpty)
                .accessibilityHint("Hold to pour the coins into the button, or tap to collect everything.")
                .accessibilityAction { if !isEmpty { driver.pour(); if reduceMotion { reducedStart = Date() } } }
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
    private var money: String { String(format: "$%.2f", max(amountCents, 0) / 100) }
}
