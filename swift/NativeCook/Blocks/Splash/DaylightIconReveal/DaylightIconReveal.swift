import SwiftUI
import UIKit

// Daylight Icon Reveal: the welcome screen of an invented app, Daylight. A glossy split day-and-night
// icon (sun over pale hills on the left, cratered moon and stars over a blue wave on the right) rises
// oversized from below and settles in the upper third; the headline, a line of copy, a violet Continue
// button and three lines of small legal print come out of a soft focus underneath it.
//
// Timeline of one 8.8 s loop (autoPlay), t in seconds. The loop starts and ends on the empty
// lavender page:
//   0.35-4.00  the icon follows measured tracks (DaylightIconRevealScript.poses): a faint cropped corner
//              at 0.4, the whole plane by 1.3, shrinking through 2.5 with about 3% undershoot, settled at
//              4 s at half the screen's width. It is a perspective plane: a projective transform of the
//              artwork, not a 3D scene.
//   1.40-2.60  a warm and a cool glow (day left, night right) fade in behind the icon.
//   1.75-3.00  the headline rises from below out of a 6 pt blur on the reference's lift curve;
//   1.80-3.00  the copy follows 0.1 s behind it;
//   2.15-3.40  the button after them; the small print comes in at 3.25 (an 8 pt rise).
//   2.50-3.40  one light sweep crosses the icon (ease-in-out, 18 degrees).
//   4.00-7.20  at rest.
//   7.20-7.55  the copy leaves upwards into a blur.
//   7.45-8.15  the icon recedes (4% smaller, blurred); the glow is gone by 8.1.
//
// Touch: a finger on the button stops the clock; 2 s after it lifts the screen settles (0.24 s) and the
// loop starts again from 0.65 s later. Continue (a tap, only between 2.25 s and 7.2 s) stops the loop at the
// settled screen and rewrites the copy ("Make room for your rhythm.", button "Replay welcome"); Replay
// welcome starts the loop again. Without autoPlay the screen sits settled and Replay plays the
// introduction once (4 s). Under Reduce Motion everything is in place and only the text fades.
//
// `previewTime` freezes the loop at one moment (and switches touch off): the lead uses it to compare
// frames. `onContinue` is called when Continue is pressed.

struct DaylightIconReveal: View {
    /// One period of the loop in seconds (`LOOP` in the React Native file).
    static let loop: Double = 8.8

    var autoPlay = false
    var previewTime: Double?
    var onContinue: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller: DaylightIconRevealController

    init(autoPlay: Bool = false, previewTime: Double? = nil, onContinue: (() -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onContinue = onContinue
        _controller = State(initialValue: DaylightIconRevealController(autoPlay: autoPlay, previewTime: previewTime))
        _ = DaylightIconRevealArt.image
    }

    var body: some View {
        GeometryReader { geo in
            // The page is laid out on the whole window, as React Native's `useWindowDimensions`
            // sees it, and drawn under the status bar and the home indicator.
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            DaylightIconRevealScreen(
                controller: controller,
                size: size,
                safeTop: insets.top,
                safeBottom: insets.bottom,
                reduced: reduceMotion
            )
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .background(DaylightIconRevealPalette.ground.ignoresSafeArea())
        // `<StatusBar style="dark" />`: dark status bar content on the light page.
        .preferredColorScheme(.light)
        .onAppear {
            controller.configure(autoPlay: autoPlay, previewTime: previewTime, onContinue: onContinue)
            controller.reduced = reduceMotion
            controller.foreground = scenePhase == .active
            controller.sync()
        }
        .onDisappear {
            controller.stop()
        }
        .onChange(of: reduceMotion) { _, value in
            controller.reduced = value
            controller.sync()
        }
        .onChange(of: scenePhase) { _, value in
            controller.foreground = value == .active
            controller.sync()
        }
        .onChange(of: previewTime) { _, value in
            controller.configure(autoPlay: autoPlay, previewTime: value, onContinue: onContinue)
            controller.sync()
        }
        .onChange(of: autoPlay) { _, value in
            controller.configure(autoPlay: value, previewTime: previewTime, onContinue: onContinue)
            controller.sync()
        }
    }
}

// MARK: - Screen

/// The page: a scroll view holding the hero (icon and glow) and the copy, drawn from the loop time of
/// one `TimelineView`. The timeline is paused whenever nothing moves.
struct DaylightIconRevealScreen: View {
    let controller: DaylightIconRevealController
    /// The window's size in points.
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let reduced: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var contentHeight: CGFloat = 0

    private typealias S = DaylightIconRevealScript

    var body: some View {
        let fontScale = daylightIconRevealFontScale(dynamicTypeSize)
        let compact = fontScale > 1.4
        // The progressive blur at the edges only shows when the copy is taller than the screen.
        let overflows = compact && contentHeight > size.height + 4
        ScrollView {
            TimelineView(.animation(paused: !controller.isMoving)) { timeline in
                page(date: timeline.date, fontScale: fontScale, compact: compact)
            }
        }
        // `bounces={false}`: SwiftUI has no "never", so the scroll view is switched off below as well.
        .scrollBounceBehavior(.basedOnSize)
        .onPreferenceChange(DaylightIconRevealHeightKey.self) { contentHeight = $0 }
        .overlay(alignment: .top) {
            if overflows {
                DaylightIconRevealEdgeBlur(edge: .top, reduceTransparency: reduceTransparency)
            }
        }
        .overlay(alignment: .bottom) {
            if overflows {
                DaylightIconRevealEdgeBlur(edge: .bottom, reduceTransparency: reduceTransparency)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(DaylightIconRevealPalette.ground)
        .clipped()
    }

    // MARK: Page

    private func page(date: Date, fontScale: Double, compact: Bool) -> some View {
        let t = controller.time(at: date)
        let heroHeight = max(compact ? 240 : 320, size.height * (compact ? 0.38 : 0.57))
        let bottomPadding = max(safeBottom, 18)
        return VStack(spacing: 0) {
            DaylightIconRevealHero(
                t: t,
                reduced: reduced,
                width: size.width,
                height: size.height,
                heroHeight: heroHeight,
                compact: compact,
                safeTop: safeTop
            )
            copy(t: t, date: date, fontScale: fontScale, compact: compact, heroHeight: heroHeight, bottomPadding: bottomPadding)
        }
        .frame(width: size.width)
        .padding(.bottom, bottomPadding)
        .background {
            GeometryReader { proxy in
                Color.clear.preference(key: DaylightIconRevealHeightKey.self, value: proxy.size.height)
            }
        }
        .background(DaylightIconRevealNoBounce())
    }

    // MARK: Copy

    /// The headline, the line of copy, the button and the small print (`styles.copy`).
    private func copy(
        t: Double, date: Date, fontScale: Double, compact: Bool, heroHeight: CGFloat, bottomPadding: CGFloat
    ) -> some View {
        let width = size.width
        let height = size.height
        let began = controller.began
        let previous = controller.previousBegan
        let elapsed: Double? = controller.swapping
            ? controller.swapStart.map { max(0, date.timeIntervalSince($0)) }
            : nil
        let horizontalInset: CGFloat = width > 420 ? 40 : 25
        let columnWidth = width - horizontalInset * 2
        let ready = S.isReady(t, reduced: reduced)
        return VStack(spacing: 0) {
            revealed(.title, t: t, distance: height * 0.32) {
                DaylightIconRevealSwapText(
                    text: DaylightIconRevealCopy.title(began: began),
                    previousText: DaylightIconRevealCopy.title(began: previous),
                    elapsed: elapsed, reduced: reduced, delay: 0,
                    style: .title, fontScale: fontScale, isHeader: true
                )
                .frame(maxWidth: .infinity)
            }
            revealed(.subtitle, t: t, distance: height * 0.29) {
                DaylightIconRevealSwapText(
                    text: DaylightIconRevealCopy.subtitle(began: began),
                    previousText: DaylightIconRevealCopy.subtitle(began: previous),
                    elapsed: elapsed, reduced: reduced, delay: 0.05,
                    style: .subtitle, fontScale: fontScale
                )
                .frame(maxWidth: 300)
                .padding(.top, 10)
                .frame(maxWidth: .infinity)
            }
            revealed(.button, t: t, distance: height * 0.25) {
                DaylightIconRevealButton(
                    label: DaylightIconRevealCopy.button(began: began),
                    width: min(columnWidth * 0.66, 300),
                    ready: ready,
                    pressed: controller.pressed,
                    reduced: reduced,
                    fontScale: fontScale,
                    onPressIn: { controller.pressIn() },
                    onPressOut: { controller.pressOut() },
                    onPress: { controller.activate() }
                )
                .padding(.top, 23)
                .frame(maxWidth: .infinity)
            }
            revealed(.note, t: t, distance: 8) {
                DaylightIconRevealSwapText(
                    text: DaylightIconRevealCopy.note(began: began),
                    previousText: DaylightIconRevealCopy.note(began: previous),
                    elapsed: elapsed, reduced: reduced, delay: 0.1,
                    style: .note, fontScale: fontScale
                )
                .padding(.horizontal, 56)
                .padding(.top, 14)
            }
        }
        .padding(.horizontal, horizontalInset)
        .padding(.top, compact ? 16 : 26)
        .padding(.bottom, 12)
        .frame(width: width)
        // `flex: 1` with the content centred: the copy takes what is left under the hero; compact
        // sizes let it grow with the text instead (and scroll).
        .frame(minHeight: compact ? nil : max(0, height - bottomPadding - heroHeight))
        .offset(y: compact ? 0 : height * 0.032)
    }

    /// `Reveal`: a block rises from below out of a soft focus, and leaves upwards into one.
    private func revealed<Content: View>(
        _ block: DaylightIconRevealBlock, t: Double, distance: Double, @ViewBuilder content: () -> Content
    ) -> some View {
        let state = S.reveal(block, t: t, reduced: reduced, distance: distance)
        return content()
            .compositingGroup()
            // React Native's CSS blur draws at about half SwiftUI's radius on iOS (matched on device).
            .blur(radius: state.blur * 0.5)
            .opacity(state.opacity)
            .offset(y: state.translateY)
    }
}

/// The height of the scroll content, to know whether the copy overflows the screen.
private struct DaylightIconRevealHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// Switches the bounce of the enclosing scroll view off (`bounces={false}` in React Native).
private struct DaylightIconRevealNoBounce: UIViewRepresentable {
    func makeUIView(context: Context) -> UIView {
        Probe()
    }

    func updateUIView(_ view: UIView, context: Context) {}

    private final class Probe: UIView {
        override func didMoveToWindow() {
            super.didMoveToWindow()
            var candidate = superview
            while let view = candidate {
                if let scrollView = view as? UIScrollView {
                    scrollView.bounces = false
                    return
                }
                candidate = view.superview
            }
        }
    }
}
