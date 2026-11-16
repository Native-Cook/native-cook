import SwiftUI

/// The approved RN slicer screen, in its original 402 × 874 coordinate system.
struct PrintLineScrub: View {
    var autoPlay: Bool
    static let loop = 7.6
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = PrintLineScrubDriver()
    @GestureState private var gestureActive = false
    @State private var gesturePending = false
    @State private var accessibilityRevision = 0
    private let pinnedTime: Double?

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = PrintLineScrubResources.register
        _ = PrintLineScrubResources.atlas
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-printLineScrubTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { pinnedTime = t }
        else { pinnedTime = nil }
        #else
        pinnedTime = nil
        #endif
    }

    var body: some View {
        GeometryReader { proxy in
            let _ = accessibilityRevision
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let g = PrintLineScrubGeometry(size: size, top: insets.top, bottom: insets.bottom)
            TimelineView(.animation(minimumInterval: 1.0 / 120, paused: scenePhase != .active || pinnedTime != nil ||
                                    (driver.mode == 0 && !gesturePending && (reduceMotion || !autoPlay)))) { timeline in
                let look = pinnedTime.map { PrintLineScrubMotion.pinned($0, reduce: reduceMotion) }
                    ?? driver.frame(at: timeline.date, geometry: g, auto: autoPlay, reduce: reduceMotion)
                ZStack(alignment: .topLeading) {
                    PrintLineScrubFrame(look: look, geometry: g, pixelRatio: displayScale, reduceMotion: reduceMotion)
                        .allowsHitTesting(false)
                        .accessibilityHidden(true)
                    handle(look, g)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .coordinateSpace(name: "PrintLineScrubScreen")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
            .onChange(of: gestureActive) {
                if !gestureActive && gesturePending {
                    driver.end(velocityY: 0, geometry: g, reduce: reduceMotion)
                    gesturePending = false
                }
            }
        }
        .background(PrintLineScrubResources.color(0xECE9E6).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active) }
    }

    private func handle(_ look: PrintLineScrubMotion.Look, _ g: PrintLineScrubGeometry) -> some View {
        Color.clear
            .frame(width: g.capWidth, height: g.capHeight, alignment: .topLeading)
            .contentShape(Rectangle())
            .offset(x: g.capLeft, y: g.capsuleY(look.h, look.duck) - g.capHeight / 2)
            .gesture(DragGesture(minimumDistance: 0, coordinateSpace: .named("PrintLineScrubScreen"))
                .updating($gestureActive) { _, state, _ in state = true }
                .onChanged { value in
                    guard pinnedTime == nil else { return }
                    gesturePending = true
                    if !driver.fingerDown { driver.begin(reduce: reduceMotion) }
                    driver.move(value.translation, geometry: g, reduce: reduceMotion)
                }
                .onEnded { value in
                    gesturePending = false
                    driver.end(velocityY: value.velocity.height, geometry: g, reduce: reduceMotion)
                })
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Layer preview")
            .accessibilityValue("\(driver.accessiblePercent) percent, layer \(driver.accessibleLayer) of 480")
            .accessibilityAdjustableAction { direction in
                guard pinnedTime == nil else { return }
                switch direction {
                case .increment: driver.accessibilityStep(1, reduce: reduceMotion)
                case .decrement: driver.accessibilityStep(-1, reduce: reduceMotion)
                @unknown default: return
                }
                accessibilityRevision += 1
            }
    }
}
