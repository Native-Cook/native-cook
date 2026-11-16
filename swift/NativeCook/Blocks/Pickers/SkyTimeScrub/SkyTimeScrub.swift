import SwiftUI
import UIKit

/// The approved React Native screen, with the same 402 × 874 reference layout and 10 s film.
struct SkyTimeScrub: View {
    static let loop = SkyTimeScrubMotion.loop
    var autoPlay: Bool
    var sound: Bool
    var haptics: Bool
    var turbidity: Double
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @Environment(\.dynamicTypeSize) private var dynamicType
    @Environment(\.displayScale) private var displayScale
    @State private var driver: SkyTimeScrubDriver
    @State private var scheduleOpen = false
    @State private var hintDismissed = false
    @GestureState private var gestureActive = false
    @State private var gesturePending = false

    init(autoPlay: Bool = false, now: Double = 1086, sound: Bool = false, haptics: Bool = true,
         turbidity: Double = 2, solar: SkyTimeScrubMotion.Day = .init(), detents: [Double]? = nil,
         adaptExposure: Bool = true, exposureUp: Double = 0.18, exposureDown: Double = 0.6,
         onChange: ((Double) -> Void)? = nil, onScrub: ((Double) -> Void)? = nil,
         onScrubStart: (() -> Void)? = nil, onScrubEnd: ((Double) -> Void)? = nil) {
        self.autoPlay = autoPlay; self.sound = sound; self.haptics = haptics; self.turbidity = turbidity
        _ = SkyTimeScrubResources.register
        let d = SkyTimeScrubDriver(day: solar, now: now, detents: detents, adapt: adaptExposure, up: exposureUp, down: exposureDown)
        d.onChange = onChange; d.onScrub = onScrub; d.onScrubStart = onScrubStart; d.onScrubEnd = onScrubEnd
        _driver = State(initialValue: d)
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-skyTimeScrubTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    private var fontScale: Double {
        let category: UIContentSizeCategory
        switch dynamicType {
        case .xSmall: category = .extraSmall
        case .small: category = .small
        case .medium: category = .medium
        case .large: category = .large
        case .xLarge: category = .extraLarge
        case .xxLarge: category = .extraExtraLarge
        case .xxxLarge: category = .extraExtraExtraLarge
        case .accessibility1: category = .accessibilityMedium
        case .accessibility2: category = .accessibilityLarge
        case .accessibility3: category = .accessibilityExtraLarge
        case .accessibility4: category = .accessibilityExtraExtraLarge
        case .accessibility5: category = .accessibilityExtraExtraExtraLarge
        @unknown default: category = .large
        }
        // RN PixelRatio.getFontScale() uses the body's scaled point size / 17 on iOS.
        return UIFont.preferredFont(forTextStyle: .body, compatibleWith: UITraitCollection(preferredContentSizeCategory: category)).pointSize / 17
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let layout = SkyTimeScrubLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom, fontScale: fontScale)
            TimelineView(.animation(paused: scenePhase != .active)) { timeline in
                let pose = driver.frame(at: timeline.date, auto: autoPlay, reduce: reduceMotion, pin: pinnedTime, projection: layout.projection)
                SkyTimeScrubFrame(pose: pose, layout: layout, day: driver.timeline.day, now: driver.timeline.now,
                                  turbidity: turbidity, reduce: reduceMotion, displayScale: displayScale,
                                  scheduleOpen: $scheduleOpen, hintDismissed: $hintDismissed,
                                  travel: { driver.travel($0, reduce: reduceMotion) },
                                  gridCue: { driver.feedback.cue("onset") }, touchTargets: AnyView(touchTargets(pose, layout)))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { driver.feedback.configure(sound: sound, haptics: haptics) }
        .onChange(of: sound) { driver.feedback.configure(sound: sound, haptics: haptics) }
        .onChange(of: haptics) { driver.feedback.configure(sound: sound, haptics: haptics) }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active) }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending {
                driver.end(velocityX: 0, reduce: reduceMotion); gesturePending = false
            }
        }
    }
    private func touchTargets(_ pose: SkyTimeScrubMotion.Pose, _ l: SkyTimeScrubLayout) -> some View {
        let sun = SkyTimeScrubMotion.solar(pose.minutes, driver.timeline.day)
        let night = sun.elevation < -2
        let y = night ? SkyTimeScrubMotion.arch(sun.x, l.arcCentre / l.s, l.arcAmplitude / l.s) * l.s
            : l.horizon - SkyTimeScrubMotion.sunHeight(sun.elevation, driver.timeline.day, l.projection) * l.s
        return ZStack(alignment: .topLeading) {
            Color.clear.contentShape(Rectangle())
                .frame(width: l.width - 40 * l.s, height: 88 * l.s)
                .gesture(scrubGesture(sun: false, scale: l.s))
                .accessibilityElement()
                .accessibilityLabel("Time of day")
                .accessibilityValue(SkyTimeScrubMotion.label(pose.minutes, driver.timeline.day, driver.timeline.now))
                .accessibilityHint("Hold and slide to preview the light. Swipe up or down to adjust by fifteen minutes.")
                .accessibilityAdjustableAction { direction in
                    driver.travel(pose.minutes + (direction == .increment ? 15 : -15), reduce: reduceMotion)
                }
                .offset(x: 20 * l.s, y: l.rulerY - 40 * l.s)
            Color.clear.contentShape(Rectangle()).frame(width: 48, height: 48)
                .gesture(scrubGesture(sun: true, scale: l.s))
                .accessibilityHidden(true)
                .offset(x: night && pose.held < 0.3 ? -1000 : sun.x * l.s - 24, y: y - 24)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        // The reference chip owns its 48 pt slot before the ruler; let its Button keep the overlap.
        .allowsHitTesting(true)
    }
    private func scrubGesture(sun: Bool, scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .global)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                if !driver.fingerDown { driver.begin(sun: sun); gesturePending = true }
                driver.move(translationX: value.translation.width / scale)
            }
            .onEnded { value in
                gesturePending = false
                driver.end(velocityX: value.velocity.width / scale, reduce: reduceMotion)
            }
    }
}
