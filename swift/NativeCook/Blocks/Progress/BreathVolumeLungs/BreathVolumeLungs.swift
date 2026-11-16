import SwiftUI
import UIKit

/// The approved RN DotMatrixDrain demo, including its single full-screen sRGB material pass.
struct BreathVolumeLungs: View {
    var autoPlay: Bool
    static let loop = BreathVolumeLungsMotion.loop
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @State private var driver = BreathVolumeLungsDriver()
    @GestureState private var fingerDown = false
    @State private var gesturePending = false
    @State private var introComplete = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = BreathVolumeLungsResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-breathVolumeLungsTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite { return BreathVolumeLungsMotion.clamp(t, 0, Self.loop) }
        #endif
        return nil
    }
    private var fontScale: Double {
        // RCTAccessibilityManager's iOS multipliers, including accessibility sizes.
        switch dynamicTypeSize {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let l = BreathVolumeLungsLayout(width: geo.size.width + insets.leading + insets.trailing,
                                           height: geo.size.height + insets.top + insets.bottom,
                                           safeTop: insets.top, safeBottom: insets.bottom)
            stage(l)
                .frame(width: l.width, height: l.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .task {
            do { try await Task.sleep(for: .milliseconds(180)); introComplete = true }
            catch { /* The initial font fade ends with the screen lifecycle. */ }
        }
        .onChange(of: scenePhase) { driver.suspend() }
        .onChange(of: fingerDown) {
            if !fingerDown && gesturePending { driver.cancel(reduced: reduced); gesturePending = false }
        }
    }
    private func stage(_ l: BreathVolumeLungsLayout) -> some View {
        ZStack(alignment: .topLeading) {
            BreathVolumeLungsResources.color(0x030305)
            TimelineView(.animation(paused: scenePhase != .active || pinnedTime != nil || ((!autoPlay || reduced) && !driver.active && introComplete))) { timeline in
                let f = pinnedTime.map { BreathVolumeLungsFrameState.pinned($0, reduced: reduced) }
                    ?? driver.frame(at: timeline.date, autoPlay: autoPlay, reduced: reduced)
                content(l, frame: f)
            }
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .clipped()
    }
    private func content(_ l: BreathVolumeLungsLayout, frame: BreathVolumeLungsFrameState) -> some View {
        ZStack(alignment: .topLeading) {
            BreathVolumeLungsFrame(frame: frame, layout: l, reduced: reduced, fontScale: fontScale)
                .allowsHitTesting(false)
            Color.clear.contentShape(Rectangle())
                .frame(width: max(0, l.width - 40), height: max(0, l.knobY - 30 * l.scale - l.safeTop - 80 * l.scale))
                .offset(x: 20, y: l.safeTop + 80 * l.scale)
                .gesture(touch(l, scrub: false))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Expiratory Volume")
                .accessibilityHint("Drag the knob, or press and hold, to breathe out. Double tap to play a full exhale.")
                .accessibilityValue(String(format: "%.1f Liter", BreathVolumeLungsMotion.clamp(frame.progress) * 5.5))
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { driver.accessibleTest(reduced: reduced) }
            let scrubX = max(20, l.knobX0 - 24 * l.scale)
            Color.clear.contentShape(Rectangle())
                .frame(width: l.width - scrubX - 20, height: 56 * l.scale)
                .offset(x: scrubX, y: l.knobY - 28 * l.scale)
                .gesture(touch(l, scrub: true))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Expiratory Volume")
                .accessibilityHint("Swipe up or down to adjust by half a litre.")
                .accessibilityValue(String(format: "%.1f Liter", BreathVolumeLungsMotion.clamp(frame.progress) * 5.5))
                .accessibilityAdjustableAction { direction in
                    if direction == .increment { driver.adjust(true, reduced: reduced) }
                    else if direction == .decrement { driver.adjust(false, reduced: reduced) }
                }
                .accessibilityAction { driver.accessibleTest(reduced: reduced) }
            BreathVolumeLungsControls(layout: l, fontScale: fontScale,
                                      reset: { driver.reset(reduced: reduced) }, finish: { driver.finish() })
                .opacity(frame.fontAlpha)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .clipped()
    }
    private func touch(_ l: BreathVolumeLungsLayout, scrub: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, state, _ in state = true }
            .onChanged { value in
                if !gesturePending {
                    _ = driver.frame(at: value.time, autoPlay: autoPlay, reduced: reduced)
                    driver.begin(scrub: scrub); gesturePending = true
                }
                if !scrub && hypot(value.translation.width, value.translation.height) > 32 {
                    driver.end(velocity: 0, travel: l.travel, reduced: reduced)
                } else {
                    driver.move(translation: value.translation.width, velocity: value.velocity.width, travel: l.travel)
                }
            }
            .onEnded { value in
                driver.end(velocity: value.velocity.width, travel: l.travel, reduced: reduced)
                gesturePending = false
            }
    }
}
