import SwiftUI

/// Value-for-value SwiftUI/Metal port of the approved RN focus screen.
struct FlipFocusBadge: View {
    static let loop = 8.1
    var autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.displayScale) private var displayScale
    @State private var driver = FlipFocusBadgeDriver()
    @State private var gesturePending = false
    @GestureState private var gestureActive = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = FlipFocusBadgeResources.register
        _ = FlipFocusBadgeResources.shared
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-flipFocusBadgeTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let layout = FlipFocusBadgeLayout(width: size.width, height: size.height, insetTop: insets.top, insetBottom: insets.bottom)
            stage(layout)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { driver.startSensors() }
        .onDisappear { driver.stopSensors() }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active, at: Date()) }
        .onChange(of: autoPlay) { driver.configure(auto: autoPlay, reduce: reduceMotion, at: Date()) }
        .onChange(of: reduceMotion) { driver.configure(auto: autoPlay, reduce: reduceMotion, at: Date()) }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                gesturePending = false; driver.end(velocity: .zero)
            }
        }
    }
    private func stage(_ l: FlipFocusBadgeLayout) -> some View {
        TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil)) { timeline in
            let f = driver.frame(at: timeline.date, auto: autoPlay, reduce: reduceMotion, pin: pinnedTime)
            ZStack(alignment: .topLeading) {
                FlipFocusBadgeFrame(frame: f, layout: l, displayScale: displayScale, reduce: reduceMotion) { index in
                    driver.select(index, at: Date())
                }
                Color.clear
                    .frame(width: l.width, height: 300 * l.scale)
                    .contentShape(Rectangle())
                    .gesture(DragGesture(minimumDistance: 0)
                        .updating($gestureActive) { _, active, _ in active = true }
                        .onChanged { v in
                            gesturePending = true
                            if !driver.fingerDown { driver.begin(at: v.time) }
                            // RN's gesture event uses physical points; do not divide by layout.scale.
                            driver.move(v.translation)
                        }
                        .onEnded { v in gesturePending = false; driver.end(velocity: v.velocity) })
                    .offset(y: l.badgeTop)
                    .accessibilityElement(children: .ignore)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel(f.running ? "Focus session running, . Flip back to finish." : "\(f.playing ? 10 : f.labB) hour focus badge. Flip your phone, or drag the badge, to start a focus session.")
                    .accessibilityHint("Start focus session")
                    .accessibilityAction(.default) { driver.activate() }
            }
            .frame(width: l.width, height: l.height, alignment: .topLeading)
            .clipped()
        }
    }
}
