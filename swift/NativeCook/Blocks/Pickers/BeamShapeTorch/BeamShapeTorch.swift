import SwiftUI

/// SwiftUI port of the approved BeamControl demo; the light is its own readout.
struct BeamShapeTorch: View {
    var autoPlay = false
    static let loop = 9.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = BeamShapeTorchDriver()
    @State private var running = false
    @State private var interaction = 0
    @State private var pendingGesture = false
    @GestureState private var activeGesture = false

    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-beamShapeTorchTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(BeamShapeTorchLayout(size, insets.top))
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { driver.configure(auto: autoPlay, reduced: reduceMotion, at: Date()) }
        .onChange(of: reduceMotion) { driver.configure(auto: autoPlay, reduced: reduceMotion, at: Date()); running = false; interaction += 1 }
        .onChange(of: autoPlay) { driver.configure(auto: autoPlay, reduced: reduceMotion, at: Date()) }
        .onChange(of: scenePhase) { if scenePhase == .active { driver.resumeClock(at: Date()) } }
        .task(id: interaction) {
            guard interaction > 0 else { return }
            do { try await Task.sleep(for: .seconds(3)); if !driver.fingerDown { running = false } }
            catch { /* Another touch restarts the idle window. */ }
        }
    }
    private func stage(_ layout: BeamShapeTorchLayout) -> some View {
        TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil || !(running || (autoPlay && !reduceMotion)))) { timeline in
            let look = driver.frame(at: timeline.date, layout: layout, pin: pinnedTime)
            ZStack(alignment: .topLeading) {
                BeamShapeTorchFrame(look: look, layout: layout, displayScale: displayScale,
                                    reduced: reduceMotion, sunGrab: driver.sunGrab, fade: driver.fade)
                    .allowsHitTesting(false)
                touchSurface(layout, look)
            }
            .frame(width: layout.width, height: layout.height, alignment: .topLeading)
            .clipped()
        }
    }
    private func touchSurface(_ layout: BeamShapeTorchLayout, _ look: BeamShapeTorchMotion.Look) -> some View {
        let hit = layout.rect(look).insetBy(dx: look.m > 0.9 ? -6 * layout.scale : -14,
                                          dy: look.m > 0.9 ? -6 * layout.scale : -14)
        return Color.clear.contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($activeGesture) { _, state, _ in state = true }
                .onChanged { v in
                    running = true; pendingGesture = true
                    if !driver.fingerDown {
                        driver.begin(v.startLocation, at: v.time, layout: layout)
                    }
                    driver.move(v.location, translation: v.translation, at: v.time, layout: layout)
                }
                .onEnded { v in
                    driver.end(v.location, velocity: v.velocity, at: v.time, layout: layout)
                    pendingGesture = false; interaction += 1
                })
            .simultaneousGesture(MagnifyGesture(minimumScaleDelta: 0)
                .onChanged { v in driver.pinch(v.magnification, at: v.time) }
                .onEnded { v in driver.pinch(v.magnification, at: v.time, ended: true, velocity: v.velocity) })
            .onChange(of: activeGesture) {
                if !activeGesture && pendingGesture {
                    driver.end(.zero, velocity: .zero, at: Date(), layout: layout, cancelled: true)
                    pendingGesture = false; interaction += 1
                }
            }
            .overlay(alignment: .topLeading) {
                Color.clear.frame(width: hit.width, height: hit.height).contentShape(Rectangle())
                    .allowsHitTesting(false)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Beam control")
                    .accessibilityValue(driver.accessibilityValue)
                    .accessibilityHint(driver.accessibilityExpanded ? "Drag up or down to set the brightness, toward or away from the beam to focus it, or drag the sun. Tap to switch it on or off." : "Opens the beam control")
                    .accessibilityAdjustableAction { direction in
                        accessibility(direction == .increment ? "increment" : "decrement")
                    }
                    .accessibilityAction { accessibility("activate") }
                    .accessibilityAction(named: "Focus the beam") { accessibility("narrow") }
                    .accessibilityAction(named: "Widen the beam") { accessibility("widen") }
                    .accessibilityAction(named: "Close the beam control") { accessibility("escape") }
                    .accessibilityAction(.escape) { accessibility("escape") }
                    .offset(x: hit.minX, y: hit.minY)
            }
    }
    private func accessibility(_ action: String) {
        running = true; interaction += 1; driver.accessibility(action, at: Date())
    }
}
