import SwiftUI

/// SwiftUI port of the approved, full-screen RN Token Fusion Splash. No sound is present in the reference.
struct TokenFusionSplash: View {
    var autoPlay: Bool = false
    var idleMs: Double? = 5000
    var onMerge: ((String, [String]) -> Void)?
    var onRelease: ((String) -> Void)?
    var onComplete: (() -> Void)?
    static let loop = 7.5
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = TokenFusionSplashDriver()
    @State private var revision = 0
    @State private var pinReleased = false
    @State private var fadeSerial = 0
    @State private var fading = false
    @State private var stopped = false

    init(autoPlay: Bool = false, idleMs: Double? = 5000, onMerge: ((String, [String]) -> Void)? = nil,
         onRelease: ((String) -> Void)? = nil, onComplete: (() -> Void)? = nil) {
        self.autoPlay = autoPlay; self.idleMs = idleMs; self.onMerge = onMerge; self.onRelease = onRelease; self.onComplete = onComplete
        _ = TokenFusionSplashResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "-tokenFusionSplashTime"), index + 1 < args.count,
           let t = Double(args[index + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let g = TokenFusionSplashFrame.TokenFusionSplashGeometry(width: geo.size.width + insets.leading + insets.trailing,
                                                    height: geo.size.height + insets.top + insets.bottom,
                                                    top: insets.top, bottom: insets.bottom)
            stage(g)
                .frame(width: g.width, height: g.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { bindCallbacks(); driver.configure(auto: autoPlay) }
        .onChange(of: autoPlay) { driver.reset(auto: autoPlay); stopped = false; revision += 1 }
        .onChange(of: reduceMotion) { driver.reset(auto: autoPlay); stopped = false; fading = false; fadeSerial += 1; revision += 1 }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active) }
        .task(id: fadeSerial) {
            guard fading else { return }
            do {
                try await Task.sleep(for: .milliseconds(80))
                driver.applyReduced(); revision += 1
                try await Task.sleep(for: .milliseconds(160))
                driver.finishFade(); fading = false; revision += 1
            } catch { /* A subsequent reduced action replaces and cancels the old fade. */ }
        }
    }
    private func stage(_ geometry: TokenFusionSplashFrame.TokenFusionSplashGeometry) -> some View {
        let pin = pinReleased ? nil : pinnedTime
        return ZStack(alignment: .topLeading) {
            TokenFusionSplashResources.color(0x232325)
            TimelineView(.animation(paused: scenePhase != .active || stopped || pin != nil || (reduceMotion && !fading))) { timeline in
                let _ = revision // Touch callbacks invalidate even a paused, pinned or reduced frame.
                let frame = rendered(at: timeline.date, pin: pin)
                ZStack(alignment: .topLeading) {
                    TokenFusionSplashFrame(scene: frame.scene, lag: frame.lag, geometry: geometry, canvasOpacity: frame.opacity)
                    TokenFusionSplashTouches(scene: frame.scene, geometry: geometry,
                                             onTouch: { phase, id, p in touch(phase, id, p, geometry, pin) }, onActivate: activate)
                    if frame.scene.word >= 0.01 {
                        Color.clear.frame(width: geometry.width - 32, height: 40 * geometry.scale)
                            .offset(x: 16, y: geometry.offsetY + frame.scene.wordY * geometry.scale)
                            .accessibilityElement().accessibilityLabel("korra").accessibilityAddTraits(.isHeader)
                            .allowsHitTesting(false)
                    }
                }
                .onChange(of: frame.scene.flood > 0.995) {
                    if !autoPlay && frame.scene.flood > 0.995 { stopped = true }
                }
            }
        }
        .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
        .clipped()
    }
    private func rendered(at date: Date, pin: Double?) -> (scene: TokenFusionSplashMotion.TokenFusionSplashScene, lag: TokenFusionSplashMotion.TokenFusionSplashScene, opacity: Double) {
        if let t = pin { return (TokenFusionSplashMotion.sceneAt(t), TokenFusionSplashMotion.sceneAt(max(0, t - 2 / 60.0)), 1) }
        return driver.frame(at: date, auto: autoPlay, reduced: reduceMotion, idleMs: idleMs)
    }
    private func bindCallbacks() { driver.onMerge = onMerge; driver.onRelease = onRelease; driver.onComplete = onComplete }
    private func touch(_ phase: TokenFusionSplashTouches.TokenFusionSplashTouchPhase, _ id: Int, _ p: CGPoint,
                       _ geometry: TokenFusionSplashFrame.TokenFusionSplashGeometry, _ pin: Double?) {
        bindCallbacks()
        let point = geometry.point(p)
        if reduceMotion {
            if phase == .ended {
                let s = driver.current
                if let i = s.coins.firstIndex(where: { $0.r > 1 && hypot($0.x - point.x, $0.y - point.y) < 30 }) { reduced(i) }
            }
            return
        }
        switch phase {
        case .began: if driver.begin(id, at: point, pin: pin, auto: autoPlay) { pinReleased = true }
        case .moved: driver.move(id, at: point, geometry: geometry)
        case .ended: driver.release(id, cancel: false)
        case .cancelled: driver.release(id, cancel: true)
        }
        revision += 1
    }
    private func reduced(_ i: Int) {
        driver.reducedAction(i, at: Date()); fading = true; fadeSerial += 1; pinReleased = true
    }
    private func activate(_ i: Int) {
        bindCallbacks()
        if i < 0 { driver.skip(); stopped = true; pinReleased = true }
        else if reduceMotion { reduced(i) }
        else { driver.merge(i, pin: pinReleased ? nil : pinnedTime, auto: autoPlay); pinReleased = true }
        revision += 1
    }
}
