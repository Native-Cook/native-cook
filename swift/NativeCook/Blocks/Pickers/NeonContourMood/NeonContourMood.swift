import SwiftUI
import UIKit

/// Value-for-value port of the approved seven-stop ContourMoodPicker demo.
struct NeonContourMood: View {
    var autoPlay: Bool
    static let loop = NeonContourMoodMotion.loop
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = NeonContourMoodDriver()
    @State private var revision = 0
    @State private var reducedAnimating = false
    @State private var pendingTouch = 0
    @State private var buttonStartedAt = Date.distantPast
    @State private var buttonTravel = 0.0
    @GestureState private var gestureActive = false

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = NeonContourMoodResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-neonContourMoodTime"), i + 1 < args.count,
           let t = Double(args[i + 1]), t.isFinite, t >= 0 { return t }
        #endif
        return nil
    }
    var body: some View {
        let _ = revision
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let width = geo.size.width + insets.leading + insets.trailing
            let height = geo.size.height + insets.top + insets.bottom
            let fs = NeonContourMoodLayout.fontMultiplier(dynamicTypeSize)
            let layout = NeonContourMoodLayout(width: width, height: height, top: insets.top, bottom: insets.bottom, fontScale: fs)
            let art = NeonContourMoodArt(layout)
            NeonContourMoodScroll(contentWidth: width, contentHeight: layout.height, viewportHeight: height,
                                  content: stage(layout, art))
            .frame(width: width, height: height, alignment: .topLeading)
            .background(NeonContourMoodTheme.color(0x110713))
            .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onChange(of: autoPlay) { driver.reset(); revision += 1 }
        .onChange(of: reduceMotion) { driver.reset(); revision += 1 }
        .onChange(of: scenePhase) { driver.setActive(scenePhase == .active, at: Date()) }
        .onChange(of: gestureActive) {
            guard !gestureActive, pendingTouch != 0 else { return }
            if pendingTouch <= 2 { driver.finalize(at: Date()) }
            else { driver.buttonUp(pendingTouch == 4, success: false, at: Date(), reduced: reduceMotion) }
            pendingTouch = 0; revision += 1
        }
        .task(id: revision) {
            guard reduceMotion else { return }
            reducedAnimating = true
            do {
                try await Task.sleep(for: .milliseconds(140))
                reducedAnimating = false
                if autoPlay {
                    try await Task.sleep(for: .milliseconds(1860))
                    driver.select(1, at: Date(), reduced: true)
                    reducedAnimating = true
                    try await Task.sleep(for: .milliseconds(140))
                    reducedAnimating = false
                }
            } catch { /* A new touch replaces the source's 2 s idle deadline. */ }
        }
    }
    private func stage(_ l: NeonContourMoodLayout, _ art: NeonContourMoodArt) -> some View {
        TimelineView(.animation(paused: scenePhase != .active || (reduceMotion && !reducedAnimating) || (pinnedTime != nil && !driver.pinIsOverridden && !reduceMotion))) { timeline in
            let frame = driver.frame(at: timeline.date, auto: autoPlay, reduced: reduceMotion, pin: pinnedTime,
                                     widths: art.widths, lengths: art.lengths, scale: l.scale)
            ZStack(alignment: .topLeading) {
                NeonContourMoodFrame(frame: frame, layout: l, art: art)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                Color.clear
                    .frame(width: l.width - 108 * l.scale, height: 28 * l.scale)
                    .offset(x: 48 * l.scale, y: l.titleY - 24 * l.scale)
                    .accessibilityLabel("Market Feelings")
                    .accessibilityAddTraits(.isHeader)
                touchSurface(l, hero: true)
                    .frame(width: l.width, height: max(0, l.heroBottom - l.heroTop))
                    .offset(y: l.heroTop)
                    .accessibilityHidden(true)
                touchSurface(l, hero: false)
                    .frame(width: l.sliderWidth, height: l.sliderHeight + 8)
                    .offset(x: l.sliderX, y: l.sliderY - 4)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("What's your market mood?")
                    .accessibilityValue("\(NeonContourMoodTheme.moods[Int(frame.lab[1])].word.lowercased()), \(Int(frame.lab[1]) + 1) of 7")
                    .accessibilityHint("Swipe up or down to choose a mood")
                    .accessibilityAdjustableAction { direction in
                        let delta = direction == .increment ? 1.0 : -1.0
                        driver.select(NeonContourMoodMotion.clamp(frame.lab[1] + delta, 0, 6), at: Date(), reduced: reduceMotion)
                        revision += 1
                    }
                buttonSurface(l, close: true)
                    .frame(width: max(48, 48 * l.scale), height: max(48, 48 * l.scale))
                    .offset(x: l.closeX - 2 * l.scale, y: l.closeY - 2 * l.scale)
                    .accessibilityLabel("Close market feelings")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction(.default) { activateButton(true) }
                buttonSurface(l, close: false)
                    .frame(width: 342 * l.scale, height: max(48, l.buttonHeight))
                    .offset(x: 32 * l.scale, y: l.buttonY)
                    .accessibilityLabel("Next")
                    .accessibilityHint("Use the selected market mood")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction(.default) { activateButton(false) }
            }
            .frame(width: l.width, height: l.height, alignment: .topLeading)
            .clipped()
        }
    }
    private func touchSurface(_ l: NeonContourMoodLayout, hero: Bool) -> some View {
        Color.clear.contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($gestureActive) { _, state, _ in state = true }
                .onChanged { value in
                    if pendingTouch == 0 {
                        pendingTouch = hero ? 1 : 2
                        driver.begin(at: value.time, reduced: reduceMotion, hero: hero)
                        revision += 1
                    }
                    // Convert the local overlay point back to the source canvas coordinates.
                    let point = CGPoint(x: value.location.x + (hero ? 0 : l.sliderX),
                                        y: value.location.y + (hero ? l.heroTop : l.sliderY - 4))
                    driver.move(value.translation, location: point, layout: l, hero: hero, reduced: reduceMotion, at: value.time)
                    if reduceMotion { revision += 1 }
                }
                .onEnded { value in
                    let point = CGPoint(x: value.location.x + (hero ? 0 : l.sliderX),
                                        y: value.location.y + (hero ? l.heroTop : l.sliderY - 4))
                    driver.end(value.translation, location: point, velocity: value.velocity, layout: l, hero: hero,
                               reduced: reduceMotion, at: value.time)
                    pendingTouch = 0; revision += 1
                })
    }
    private func buttonSurface(_ l: NeonContourMoodLayout, close: Bool) -> some View {
        Color.clear.contentShape(Rectangle())
            .gesture(DragGesture(minimumDistance: 0)
                .updating($gestureActive) { _, state, _ in state = true }
                .onChanged { value in
                    if pendingTouch == 0 {
                        pendingTouch = close ? 4 : 3
                        buttonStartedAt = value.time; buttonTravel = 0
                        driver.buttonDown(close, at: value.time, reduced: reduceMotion)
                        revision += 1
                    }
                    buttonTravel = max(buttonTravel, hypot(value.translation.width, value.translation.height))
                }
                .onEnded { value in
                    let width = close ? max(48, 48 * l.scale) : 342 * l.scale
                    let height = close ? max(48, 48 * l.scale) : max(48, l.buttonHeight)
                    let success = value.location.x >= 0 && value.location.x <= width && value.location.y >= 0 && value.location.y <= height
                        && max(buttonTravel, hypot(value.translation.width, value.translation.height)) <= 10
                        && value.time.timeIntervalSince(buttonStartedAt) <= 0.5
                    driver.buttonUp(close, success: success, at: value.time, reduced: reduceMotion)
                    pendingTouch = 0; revision += 1
                })
    }
    private func activateButton(_ close: Bool) {
        driver.buttonUp(close, success: true, at: Date(), reduced: reduceMotion)
        revision += 1
    }
}
