import SwiftUI
import UIKit

/*
 Quiet Signal Onboarding: a restrained five-page introduction for the invented Quiet Signal on near-black.
 Monochrome line diagrams (a busy profile with a document and swinging moons, two waves crossing round a
 lit profile, a phone carrying sound to both sides, sound rings around a listening head, and a circular
 emblem that draws itself), each with a white heading, grey body copy and a dark Continue button at the
 foot. The last Continue opens a local silent session preview.

 autoPlay (loop = 34 s): the pages play in turn. Each scene rises out of the dark (page 1 at 0 s, 2 at 9,
 3 at 15.25, 4 at 22, 5 at 28.25), its words come out of a soft focus 0.45 s later, and it fades
 (at 8.15, 14.4, 21.4, 27.5 and 32.5 s) before the next one starts. Every diagram runs from the one loop
 clock; the emblem's arc sweeps 28.3 to 29.5 s, becomes a ring at 29.9 s and its inner line draws 30.5 to
 31.8 s.

 Touch: a swipe or Continue changes the page (a 400 ms spring, damping ratio 0.8, carrying the finger's
 speed); a touch anywhere pauses the tour and it resumes 2 s after the last one, from the page that was
 chosen. Reduce Motion shows the page still, complete; buttons and swipes switch pages at once.

 The clock, the page position and the quiet period are in QuietSignalOnboardingConductor; the five drawings
 are in QuietSignalOnboardingDiagrams.
*/
struct QuietSignalOnboarding: View {
    /// `LOOP`: one pass of the five pages, in seconds.
    static let loop: Double = 34

    /// The tour plays by itself and resumes after a touch.
    let autoPlay: Bool
    /// Freezes the loop at this time (seconds) and ignores touch, for deterministic inspection.
    let previewTime: Double?
    /// Called when the last Continue opens the session preview.
    let onBeginSession: (() -> Void)?
    /// Called with the page (0 to 4) whenever the page on screen changes.
    let onPageChange: ((Int) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.scenePhase) private var scenePhase

    @State private var conductor: QuietSignalOnboardingConductor
    /// `page`: the page the screen last announced.
    @State private var page: Int
    /// `session`: the session preview replaces the tour.
    @State private var session = false
    @State private var sessionMode = "A quiet break"
    /// Bumped by the conductor when something changes that a paused timeline would not redraw.
    @State private var revision = 0

    /// The three directions of the session preview.
    private static let modes = ["A little focus", "A quiet break", "An easy evening"]

    init(
        autoPlay: Bool = false,
        previewTime: Double? = nil,
        onBeginSession: (() -> Void)? = nil,
        onPageChange: ((Int) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onBeginSession = onBeginSession
        self.onPageChange = onPageChange
        let conductor = QuietSignalOnboardingConductor(autoPlay: autoPlay, previewTime: previewTime)
        _conductor = State(initialValue: conductor)
        _page = State(initialValue: conductor.initialPage)
    }

    var body: some View {
        let _ = conductor.syncEnvironment(reduced: reduceMotion, foreground: scenePhase == .active)
        let _ = revision
        GeometryReader { geo in
            // The page is laid out on the whole window, as React Native's `useWindowDimensions` and
            // `useSafeAreaInsets` see it, and drawn under the status bar and the home indicator.
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .background(QuietSignalOnboardingPalette.ground)
                .ignoresSafeArea()
        }
        .background(QuietSignalOnboardingPalette.ground.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            let binding = $revision
            conductor.onChange = { binding.wrappedValue &+= 1 }
            conductor.appeared()
        }
        .onDisappear {
            // The conductor must not keep the screen's state alive.
            conductor.onChange = {}
        }
    }

    // MARK: - Stage

    @ViewBuilder
    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let largeText = QuietSignalOnboardingType.fontScale(dynamicTypeSize) > 1.35
        if session && !conductor.previewing {
            sessionScreen(insets: insets)
        } else {
            tourScreen(size: size, insets: insets, largeText: largeText)
        }
    }

    // MARK: - The tour

    /// The tour: the pages in a scroll view with progressive blur, and Continue at the foot.
    private func tourScreen(size: CGSize, insets: EdgeInsets, largeText: Bool) -> some View {
        let available = size.height - insets.top - insets.bottom - 430
        let ceiling: CGFloat = largeText ? 230 : 300
        let diagram = min(size.width - 80, max(220, available), ceiling)
        return VStack(spacing: 0) {
            QuietSignalOnboardingScroller(edgeSize: 30, alignment: largeText ? .top : .center, events: readingEvents) {
                pageStack(width: size.width, diagram: diagram, largeText: largeText)
                    .padding(.vertical, largeText ? 16 : 0)
            }
            footer
        }
        .padding(.top, insets.top + 16)
        .padding(.bottom, insets.bottom + 10)
    }

    /// The five scenes stacked, drawn from the loop time of one timeline, with the swipe on top of them.
    /// A swipe, a tap on the buttons and the accessibility actions all change the page through the conductor.
    private func pageStack(width: CGFloat, diagram: CGFloat, largeText: Bool) -> some View {
        let current = QuietSignalOnboardingPage.all[min(4, max(0, page))]
        let label = "Page \(page + 1) of 5. \(current.heading) \(current.copy) \(current.art)"
        return TimelineView(.animation(paused: reduceMotion || conductor.previewing)) { timeline in
            let frame = conductor.frame(at: timeline.date)
            ZStack(alignment: .top) {
                ForEach(QuietSignalOnboardingPage.all) { item in
                    QuietSignalOnboardingScene(
                        page: item, frame: frame, reduced: reduceMotion, diagram: diagram, largeText: largeText)
                }
            }
            .frame(maxWidth: .infinity)
            .opacity(conductor.previewing ? 1 : frame.presence)
            .onChange(of: frame.page, initial: true) { _, next in announce(next) }
        }
        .overlay { pageSwipe(width: width) }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: conductor.go(to: page + 1)
            case .decrement: conductor.go(to: page - 1)
            @unknown default: break
            }
        }
    }

    /// `announcePage`: the page on screen changed.
    private func announce(_ next: Int) {
        page = next
        conductor.remember(QuietSignalOnboardingMotion.anchors[next])
        onPageChange?(next)
    }

    /// `Gesture.Pan()` of the pages.
    private func pageSwipe(width: CGFloat) -> some View {
        let conductor = self.conductor
        return QuietSignalOnboardingPan(
            enabled: !conductor.previewing,
            onBegan: { conductor.panBegan() },
            onChanged: { conductor.panChanged(translation: $0, width: Double(width)) },
            onEnded: { translation, velocity, success in
                conductor.panEnded(translation: translation, velocity: velocity, success: success, width: Double(width))
            }
        )
    }

    /// `readingHandlers`: any touch or scroll on the page pauses the tour.
    private var readingEvents: QuietSignalOnboardingScrollEvents {
        let conductor = self.conductor
        return QuietSignalOnboardingScrollEvents(
            touchBegan: { conductor.readingTouchBegan() },
            touchEnded: { conductor.readingTouchEnded() },
            dragBegan: { conductor.readingDragBegan() },
            dragEnded: { conductor.readingDragEnded() },
            momentumBegan: { conductor.readingMomentumBegan() },
            momentumEnded: { conductor.readingMomentumEnded() }
        )
    }

    /// The Continue button: `paddingHorizontal: 50`.
    private var footer: some View {
        QuietSignalOnboardingTourButton(
            label: page == 4 ? "Continue to a quiet moment" : "Continue",
            reduced: reduceMotion,
            background: QuietSignalOnboardingPalette.continueFill,
            onPressIn: { conductor.pressBegan() },
            onPressOut: { conductor.pressEnded(page: page) },
            onPress: continueTour
        ) {
            QuietSignalOnboardingSwapText(text: "Continue", style: .cta, reduced: reduceMotion)
        }
        .padding(.horizontal, 50)
    }

    /// `continueTour`: the next page, or from the last one the session preview.
    private func continueTour() {
        guard !conductor.previewing else { return }
        if page < 4 {
            conductor.go(to: page + 1)
            return
        }
        conductor.beginSession()
        session = true
        onBeginSession?()
    }

    // MARK: - The session preview

    /// "Your moment is ready.": a silent session preview in a scroll view, arriving as a short cascade.
    private func sessionScreen(insets: EdgeInsets) -> some View {
        QuietSignalOnboardingScroller(edgeSize: 30, alignment: .top, events: nil) {
            VStack(spacing: 0) {
                VStack(spacing: 0) {
                    QuietSignalOnboardingLabel(text: "QUIET SIGNAL", style: .eyebrow)
                    QuietSignalOnboardingDiagramView(stage: 4, time: 0, reduced: true, size: 210, visible: true)
                }
                .modifier(QuietSignalOnboardingRise(order: 0, reduced: reduceMotion))

                VStack(spacing: 0) {
                    QuietSignalOnboardingLabel(text: "Your moment is ready.", style: .sessionTitle)
                    QuietSignalOnboardingLabel(text: "Choose a direction for this 20-minute session.", style: .body)
                        .padding(.top, 10)
                }
                .modifier(QuietSignalOnboardingRise(order: 1, reduced: reduceMotion))

                VStack(spacing: 9) {
                    ForEach(Self.modes, id: \.self) { mode in
                        modeButton(mode)
                    }
                }
                .frame(maxWidth: .infinity)
                .padding(.top, 24)
                .modifier(QuietSignalOnboardingRise(order: 2, reduced: reduceMotion))

                VStack(spacing: 0) {
                    QuietSignalOnboardingLabel(text: "20:00", style: .sessionTime)
                        .padding(.top, 28)
                    QuietSignalOnboardingSwapText(
                        text: "\(sessionMode) · silent session preview", style: .previewCaption, reduced: reduceMotion
                    )
                    .padding(.top, 9)
                    QuietSignalOnboardingLabel(
                        text: "This preview is yours to explore.\nNo audio is playing.", style: .previewNote
                    )
                    .padding(.top, 18)
                }
                .modifier(QuietSignalOnboardingRise(order: 3, reduced: reduceMotion))

                backButton
                    .padding(.top, 26)
            }
            .padding(.horizontal, 28)
            .padding(.top, insets.top + 30)
            .padding(.bottom, insets.bottom + 16)
        }
    }

    private func modeButton(_ mode: String) -> some View {
        let selected = sessionMode == mode
        return QuietSignalOnboardingTourButton(
            label: "\(mode)\(selected ? ", selected" : "")",
            reduced: reduceMotion,
            background: selected ? QuietSignalOnboardingPalette.modeSelectedFill : QuietSignalOnboardingPalette.modeFill,
            onPressIn: { conductor.pressBegan() },
            onPressOut: { conductor.pressEnded(page: page) },
            onPress: {
                sessionMode = mode
                conductor.selectionTick()
            }
        ) {
            let ink = selected ? QuietSignalOnboardingPalette.modeSelectedInk : QuietSignalOnboardingPalette.modeInk
            QuietSignalOnboardingLabel(text: mode, style: .tourLabel(color: ink))
        }
    }

    private var backButton: some View {
        QuietSignalOnboardingTourButton(
            label: "Back to the introduction",
            reduced: reduceMotion,
            background: QuietSignalOnboardingPalette.backFill,
            onPressIn: { conductor.pressBegan() },
            onPressOut: { conductor.pressEnded(page: page) },
            onPress: {
                session = false
                conductor.returnToIntroduction()
            }
        ) {
            QuietSignalOnboardingLabel(
                text: "Back to the introduction", style: .tourLabel(color: QuietSignalOnboardingPalette.tourInk))
        }
    }
}

// MARK: - Pages

/// One entry of `PAGES`: the heading, the body copy and the description of the picture for VoiceOver.
struct QuietSignalOnboardingPage: Identifiable {
    let id: Int
    let heading: String
    let copy: String
    let art: String

    static let all: [QuietSignalOnboardingPage] = [
        QuietSignalOnboardingPage(
            id: 0,
            heading: "We live in an over-\nstimulating world",
            copy: "It makes us tired, stressed, and\nunable to rest",
            art: "A profile, document and winding lines show a busy mind."),
        QuietSignalOnboardingPage(
            id: 1,
            heading: "A new world needs\nnew solutions",
            copy: "Technology that can help us\nto adapt",
            art: "A luminous profile with gentle sound waves and audio bars."),
        QuietSignalOnboardingPage(
            id: 2,
            heading: "That’s why we made Quiet Signal",
            copy: "An antidote to a distracting\nworld. Built on the science\nof sound",
            art: "An outlined phone carries waves between two sets of sound bars."),
        QuietSignalOnboardingPage(
            id: 3,
            heading: "Improve your state of mind\nwith the power of sound",
            copy: "Focus, relax, and unwind.\nBe more present. See the\nmagic in everyday life",
            art: "Concentric rings and diagonal waves surround a profile."),
        QuietSignalOnboardingPage(
            id: 4,
            heading: "Welcome to the sound\nwellness revolution",
            copy: "Welcome to Quiet Signal",
            art: "A complete circle surrounds an original continuous flowing emblem."),
    ]
}

// MARK: - Scene

/// `Scene` (QuietSignalOnboarding.tsx:44): one page's diagram and words. Its opacity follows the script, or the
/// page position while a finger or Reduce Motion has the pages. The words surface out of the dark: a soft focus
/// pull and a 6 pt rise under the fade.
struct QuietSignalOnboardingScene: View {
    let page: QuietSignalOnboardingPage
    let frame: QuietSignalOnboardingFrame
    let reduced: Bool
    let diagram: CGFloat
    let largeText: Bool

    var body: some View {
        let opacity = QuietSignalOnboardingMotion.sceneOpacity(index: page.id, frame: frame, reduced: reduced)
        // A scene that is not on screen skips the soft focus; nothing of it shows.
        let shown = opacity > 0.001
            ? QuietSignalOnboardingMotion.copyProgress(index: page.id, frame: frame, reduced: reduced)
            : 1
        VStack(spacing: 0) {
            QuietSignalOnboardingDiagramView(
                stage: page.id, time: frame.time, reduced: reduced, size: diagram, visible: opacity > 0.001
            )
            .padding(.top, largeText ? 0 : 18)

            VStack(spacing: 0) {
                QuietSignalOnboardingLabel(text: page.heading, style: .heading)
                QuietSignalOnboardingLabel(text: page.copy, style: .body)
                    .padding(.top, 10)
            }
            .padding(.top, largeText ? 24 : 34)
            .modifier(QuietSignalOnboardingFocusPull(shown: shown))
        }
        .padding(.horizontal, 28)
        .frame(maxWidth: .infinity)
        .opacity(opacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// `copyStyle`: `opacity: shown`, `filter: blur((1 - shown) * 6)` and `translateY: (1 - shown) * 6`. React
/// Native's CSS blur draws softer on iOS than SwiftUI's, so the radius is halved.
struct QuietSignalOnboardingFocusPull: ViewModifier {
    let shown: Double

    func body(content: Content) -> some View {
        let radius = (1 - shown) * 6 * 0.5
        if radius > 0.01 {
            content
                .blur(radius: radius)
                .opacity(shown)
                .offset(y: (1 - shown) * 6)
        } else {
            content
                .opacity(shown)
                .offset(y: (1 - shown) * 6)
        }
    }
}
