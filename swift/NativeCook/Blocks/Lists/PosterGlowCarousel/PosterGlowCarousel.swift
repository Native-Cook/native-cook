import SwiftUI
import UIKit

enum PosterGlowCarouselTabIdentity: String, CaseIterable {
    case home
    case search
    case cinema
    case downloads
    case profile
}

struct PosterGlowCarousel: View {
    static let loop: Double = PosterGlowCarouselMotion.loop

    let autoPlay: Bool
    let onSettings: (() -> Void)?
    let onTabSelected: ((PosterGlowCarouselTabIdentity) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion: PosterGlowCarouselMotion
    @State private var drag = PosterGlowCarouselDragState()
    @State private var resumeWork: DispatchWorkItem?
    @State private var pickerFadeWork: DispatchWorkItem?
    @GestureState private var touchDown = false

    init(
        autoPlay: Bool = false,
        onSettings: (() -> Void)? = nil,
        onTabSelected: ((PosterGlowCarouselTabIdentity) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.onSettings = onSettings
        self.onTabSelected = onTabSelected
        _motion = State(initialValue: PosterGlowCarouselMotion(autoPlay: autoPlay))
        PosterGlowCarouselFont.register()
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(PosterGlowCarouselColor.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .statusBarHidden(false)
        .onAppear {
            if reduceMotion {
                motion.stopForReducedMotion()
                return
            }
            guard autoPlay, !reduceMotion else { return }
            motion.startAuto(at: .now, reset: false)
        }
        .onDisappear {
            resumeWork?.cancel()
            pickerFadeWork?.cancel()
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = PosterGlowCarouselMetrics.feedLayout(PosterGlowCarouselContent.sections)
        let maxScroll = max(0, (layout.labels.last ?? 0) - 739)
        let paused = scenePhase != .active || (!motion.autoRunning && !touchDown && !motion.hasPickerTransition)
        return TimelineView(.animation(paused: paused)) { timeline in
            let frame = motion.frame(at: timeline.date, maxScroll: maxScroll, reduced: reduceMotion)
            ZStack(alignment: .topLeading) {
                PosterGlowCarouselFeedView(width: size.width, height: size.height, scroll: frame.scroll)
                    .id(frame.rowGeneration)
                    .accessibilityHidden(frame.pickerOpacity > 0.5)

                PosterGlowCarouselChromeView(width: size.width, height: size.height, insets: insets,
                                             backPress: frame.backPress,
                                             onBack: showPicker,
                                             onSettings: onSettings)
                    .allowsHitTesting(frame.pickerOpacity <= 0.5)
                    .accessibilityHidden(frame.pickerOpacity > 0.5)

                PosterGlowCarouselTabBar(
                    width: size.width,
                    height: size.height,
                    insets: insets,
                    onTabSelected: onTabSelected
                )
                .allowsHitTesting(frame.pickerOpacity <= 0.5)
                .accessibilityHidden(frame.pickerOpacity > 0.5)

                PosterGlowCarouselPickerView(width: size.width, height: size.height,
                                             autoPress: frame.autoPress,
                                             onPick: pickProfile)
                    .opacity(frame.pickerOpacity)
                    .allowsHitTesting(frame.pickerOpacity > 0.5)
                    .accessibilityHidden(frame.pickerOpacity <= 0.5)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
            .contentShape(Rectangle())
            .simultaneousGesture(touchGesture(maxScroll: maxScroll, reduced: reduceMotion))
            .onChange(of: touchDown) { _, isDown in
                if !isDown {
                    // onEnded normally handles this; DispatchQueue also covers a recognizer that
                    // was cancelled by the gallery's edge back-swipe recognizer.
                    DispatchQueue.main.async {
                        if drag.active { finishTouch(maxScroll: maxScroll) }
                    }
                }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .onChange(of: reduceMotion) { _, reduced in
            resumeWork?.cancel()
            pickerFadeWork?.cancel()
            if reduced {
                motion.stopForReducedMotion()
            } else if autoPlay, !drag.active {
                motion.startAuto(at: .now, reset: true)
            }
        }
    }

    private func touchGesture(maxScroll: Double, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($touchDown) { _, state, _ in state = true }
            .onChanged { value in
                if !drag.active {
                    drag.active = true
                    drag.start = value.startLocation
                    drag.axis = 0
                    // The picker owns touches while it is visible, just as RN gives that layer
                    // pointerEvents="auto". The root still takes over autoplay on that touch.
                    drag.drivesFeed = motion.frame(at: .now, maxScroll: maxScroll, reduced: reduced).pickerOpacity <= 0.5
                    motion.stopAuto(at: .now, maxScroll: maxScroll, reduced: reduced)
                    drag.startScroll = motion.manualScroll
                    resumeWork?.cancel()
                    resumeWork = nil
                }
                guard drag.drivesFeed else { return }
                let dx = Double(value.location.x - drag.start.x)
                let dy = Double(value.location.y - drag.start.y)
                if drag.axis == 0, abs(dx) + abs(dy) > 8 {
                    drag.axis = abs(dx) > abs(dy) ? 1 : 2
                }
                if drag.axis != 1 {
                    motion.setManualScroll(drag.startScroll - dy, maxScroll: maxScroll)
                }
            }
            .onEnded { value in
                finishTouch(maxScroll: maxScroll, value: value)
            }
    }

    private func finishTouch(maxScroll: Double, value: DragGesture.Value? = nil) {
        guard drag.active else { return }
        if drag.drivesFeed, drag.axis != 1, let value {
            // predictedEndTranslation is UIKit's velocity-informed deceleration target.
            // Feed offsets move opposite the finger; clamp it so a release cannot coast beyond
            // either end of the RN ScrollView's range.
            let projectedScroll = drag.startScroll - Double(value.predictedEndTranslation.height)
            withAnimation(.timingCurve(0.3, 0.1, 0.12, 1, duration: 0.42)) {
                motion.setManualScrollClamped(projectedScroll, maxScroll: maxScroll)
            }
        } else {
            withAnimation(.spring(response: 0.45, dampingFraction: 0.8)) {
                motion.settleManualScroll(maxScroll: maxScroll)
            }
        }
        drag = PosterGlowCarouselDragState()
        scheduleResume()
    }

    private func scheduleResume() {
        guard autoPlay, !reduceMotion else { return }
        resumeWork?.cancel()
        let work = DispatchWorkItem {
            guard !reduceMotion else { return }
            let now = Date.now
            motion.animatePicker(to: 1, at: now, reduced: reduceMotion)
            schedulePickerFinish(duration: reduceMotion ? 0.2 : 0.24) {
                guard !reduceMotion else { return }
                motion.startAuto(at: .now, reset: true)
            }
        }
        resumeWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + PosterGlowCarouselMotion.idleResume, execute: work)
    }

    private func pickProfile(_ index: Int) {
        guard index < 3 else { return }
        let wasManual = !motion.autoRunning
        motion.pickProfile(index, at: .now, reduced: reduceMotion)
        schedulePickerFinish(duration: reduceMotion ? 0.2 : 0.24)
        if wasManual { UISelectionFeedbackGenerator().selectionChanged() }
    }

    private func showPicker() {
        motion.showPicker(at: .now, reduced: reduceMotion)
        schedulePickerFinish(duration: reduceMotion ? 0.2 : 0.24)
    }

    private func schedulePickerFinish(duration: Double, completion: (() -> Void)? = nil) {
        pickerFadeWork?.cancel()
        let work = DispatchWorkItem {
            motion.finishPickerTransition()
            completion?()
        }
        pickerFadeWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + duration, execute: work)
    }
}

struct PosterGlowCarouselDragState {
    var active = false
    var drivesFeed = true
    var axis = 0
    var start = CGPoint.zero
    var startScroll = 0.0
}

struct PosterGlowCarouselChromeView: View {
    let width: Double
    let height: Double
    let insets: EdgeInsets
    let backPress: Double
    let onBack: () -> Void
    let onSettings: (() -> Void)?

    var body: some View {
        ZStack(alignment: .topLeading) {
            Button(action: onBack) {
                PosterGlowCarouselIcon(kind: .back, size: PosterGlowCarouselMetrics.chromeSize,
                                       color: PosterGlowCarouselColor.ink)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .opacity(1 - 0.6 * backPress)
            .frame(width: 44, height: 44)
            .position(x: PosterGlowCarouselMetrics.chromeX, y: insets.top + PosterGlowCarouselMetrics.chromeY)
            .accessibilityLabel("Back to profiles")

            Button(action: { onSettings?() }) {
                PosterGlowCarouselIcon(kind: .gear, size: PosterGlowCarouselMetrics.chromeSize - 2,
                                       color: PosterGlowCarouselColor.ink)
                    .frame(width: 44, height: 44)
            }
            .buttonStyle(.plain)
            .frame(width: 44, height: 44)
            .position(x: width - PosterGlowCarouselMetrics.chromeX, y: insets.top + PosterGlowCarouselMetrics.chromeY)
            .accessibilityLabel("Settings")
            .disabled(onSettings == nil)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }
}

struct PosterGlowCarouselTabBar: View {
    let width: Double
    let height: Double
    let insets: EdgeInsets
    let onTabSelected: ((PosterGlowCarouselTabIdentity) -> Void)?

    private let tabs: [PosterGlowCarouselTab] = [
        PosterGlowCarouselTab(id: .home, label: "Home", kind: .home),
        PosterGlowCarouselTab(id: .search, label: "Search", kind: .search),
        PosterGlowCarouselTab(id: .cinema, label: "Cinema", kind: .seat),
        PosterGlowCarouselTab(id: .downloads, label: "Downloads", kind: .download),
        PosterGlowCarouselTab(id: .profile, label: "Profile", kind: .user),
    ]

    var body: some View {
        let tabHeight = PosterGlowCarouselMetrics.tabBar + insets.bottom
        ZStack(alignment: .topLeading) {
            PosterGlowCarouselColor.tabBar
            ForEach(Array(tabs.enumerated()), id: \.element.id) { index, tab in
                Button(action: { onTabSelected?(tab.id) }) {
                    PosterGlowCarouselIcon(kind: tab.kind, size: PosterGlowCarouselMetrics.tabIcon[index],
                                           color: PosterGlowCarouselColor.ink)
                }
                .buttonStyle(.plain)
                .frame(width: 44, height: 44)
                .position(x: PosterGlowCarouselMetrics.tabX[index] / 402 * width,
                          y: PosterGlowCarouselMetrics.tabIconY)
                .accessibilityLabel(tab.label)
                .accessibilityAddTraits(index == 0 ? .isSelected : [])
                .disabled(onTabSelected == nil)
            }
        }
        .frame(width: width, height: tabHeight, alignment: .topLeading)
        .offset(y: height - tabHeight)
        .accessibilityElement(children: .contain)
    }
}

struct PosterGlowCarouselTab: Identifiable {
    let id: PosterGlowCarouselTabIdentity
    let label: String
    let kind: PosterGlowCarouselIcon.Kind
}
