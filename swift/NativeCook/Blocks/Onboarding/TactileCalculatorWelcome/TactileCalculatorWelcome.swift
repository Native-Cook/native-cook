import SwiftUI
import UIKit

// Tactile Calculator Welcome: the welcome screen of an invented app, Tally Calculator. Five oversized
// isometric keys (minus, divide, plus, multiply, equals) march down the screen on a diagonal, drawn like
// a cel-shaded illustration: rounded-square faces, a lit left wall and an ink right wall with two
// pinstripes, raised two-tone symbols and a thin rim ring. Under them sit "Welcome to" and a red
// "Tally Calculator" in Inter, and a flat charcoal Continue pill; the top key slides under a frosted
// status-bar band.
//
// Timeline of one 6.1 s loop (autoPlay), t in seconds. The keys rest at 0 and at the end:
//   0.17-0.67  divide is pressed: the cap sinks 13.4 units in 83 ms (5 frames), holds, then rings back
//              on a damped cosine from 0.67 s;
//   1.17-1.67  plus;  2.20-2.67  multiply;  3.18-3.67  equals;  4.18-4.67  divide;  5.18-5.67  plus.
//   Every press is the same shape: a smoothstep down over 5/60 s, a hold until the release, then
//   exp(-13 s) cos(7 s) from the release (kept above 0).
//
// Touch: a finger on a key presses it (the cap sinks in 110 ms on the strong ease-out, a rigid tap) and
// stops the loop at rest; releasing springs the cap back (420 ms, damping ratio 0.62) and rewrites the
// headline to the operation (old text out in 140 ms, new text up 10 pt in 260 ms). The loop starts again
// 2 s after the last finger leaves. Continue (a tap) stops the loop at rest and rewrites the copy
// ("A little clarity, every day." and "It all adds up.", button "Start again"); Start again plays the loop
// again. Under Reduce Motion the keys do not move by themselves or when pressed, a press of the pill dims
// it instead of scaling it and text swaps are plain cross-fades.
//
// `previewTime` freezes the loop at one moment (and switches touch off): the lead uses it to compare
// frames. `onContinue` is called when Continue is pressed.

struct TactileCalculatorWelcome: View {
    /// One period of the loop in seconds (`LOOP` in the React Native file).
    static let loop: Double = 6.1

    var autoPlay = false
    var previewTime: Double?
    var onContinue: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller: TactileCalculatorWelcomeController

    init(autoPlay: Bool = false, previewTime: Double? = nil, onContinue: (() -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onContinue = onContinue
        _controller = State(initialValue: TactileCalculatorWelcomeController(autoPlay: autoPlay, previewTime: previewTime))
        _ = TactileCalculatorWelcomeFonts.registered
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
            TactileCalculatorWelcomeScreen(
                controller: controller,
                size: size,
                safeBottom: insets.bottom,
                reduced: reduceMotion
            )
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .background(TactileCalculatorWelcomePalette.cream.ignoresSafeArea())
        // `<StatusBar style="dark" />`: dark status bar content on the cream page.
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

/// The page: a scroll view holding the hero (the five keys), the copy and the Continue pill, with
/// the progressive blur over its top edge (and, at large text sizes, its bottom edge too).
struct TactileCalculatorWelcomeScreen: View {
    let controller: TactileCalculatorWelcomeController
    /// The window's size in points.
    let size: CGSize
    let safeBottom: CGFloat
    let reduced: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var contentHeight: CGFloat = 0

    var body: some View {
        let fontScale = tactileCalculatorWelcomeFontScale(dynamicTypeSize)
        // `fontScale > 1.4`: the screen shrinks its illustration and lets the copy scroll.
        let compact = fontScale > 1.4
        let compactScale: CGFloat = compact ? 0.7 : 1
        let width = size.width
        let height = size.height
        let heroHeight: CGFloat = height * 0.625 * compactScale
        let byWidth: CGFloat = width * 0.6125 / 200
        let byHeight: CGFloat = height * 0.1628 / 116
        let keyScale: CGFloat = min(min(byWidth, byHeight), 1.6) * compactScale
        let bottomPadding: CGFloat = max(safeBottom, height * 0.0614)
        // The blur band is 56 pt at the top; at large sizes both edges are 28 pt and show only
        // when the content is taller than the screen (`onlyWhenOverflow`).
        let edgeSize: CGFloat = compact ? 28 : 56
        let overflows = contentHeight > height + 4
        let showTopBlur = !compact || overflows
        let showBottomBlur = compact && overflows

        ScrollView {
            TactileCalculatorWelcomeColumn(
                minHeight: height,
                bottomPadding: bottomPadding,
                heroHeight: heroHeight,
                growsCopy: !compact
            ) {
                TactileCalculatorWelcomeHero(
                    controller: controller,
                    width: width,
                    sceneHeight: height * compactScale,
                    heroHeight: heroHeight,
                    scale: keyScale
                )
                copy(fontScale: fontScale, compact: compact, height: height)
                TactileCalculatorWelcomeButton(
                    label: TactileCalculatorWelcomeCopy.button(started: controller.started),
                    reduced: reduced,
                    fontScale: fontScale,
                    isEnabled: controller.isInteractive,
                    onPressIn: { controller.buttonPressIn() },
                    onPressOut: { controller.buttonPressOut() },
                    onPress: { controller.buttonPress() }
                )
            }
            .background(
                GeometryReader { proxy in
                    Color.clear.preference(key: TactileCalculatorWelcomeHeightKey.self, value: proxy.size.height)
                }
            )
            .background(TactileCalculatorWelcomeNoBounce())
        }
        // `bounces={false}`: SwiftUI has no "never", so the scroll view is switched off below as well.
        .scrollBounceBehavior(.basedOnSize)
        .onPreferenceChange(TactileCalculatorWelcomeHeightKey.self) { contentHeight = $0 }
        .overlay(alignment: .top) {
            if showTopBlur {
                TactileCalculatorWelcomeEdgeBlur(edge: .top, size: edgeSize, reduceTransparency: reduceTransparency)
            }
        }
        .overlay(alignment: .bottom) {
            if showBottomBlur {
                TactileCalculatorWelcomeEdgeBlur(edge: .bottom, size: edgeSize, reduceTransparency: reduceTransparency)
            }
        }
        .frame(width: width, height: height)
        .background(TactileCalculatorWelcomePalette.cream)
        .clipped()
    }

    /// `styles.copy`: the welcome line and the title, centred, 24 pt in from the sides, with
    /// `paddingTop` 6.8% of the window's height (28 at large sizes) and `paddingBottom` 28. The
    /// title sits 11 pt below the line (`marginTop: 11`) and enters 40 ms after it.
    private func copy(fontScale: Double, compact: Bool, height: CGFloat) -> some View {
        let started = controller.started
        let symbol = controller.symbol
        let paddingTop: CGFloat = compact ? 28 : height * 0.068
        return VStack(spacing: 0) {
            TactileCalculatorWelcomeSwapText(
                text: TactileCalculatorWelcomeCopy.headline(started: started, symbol: symbol),
                tabular: symbol != nil && !started,
                style: .welcome,
                fontScale: fontScale,
                reduced: reduced
            )
            TactileCalculatorWelcomeSwapText(
                text: TactileCalculatorWelcomeCopy.title(started: started),
                style: .title,
                fontScale: fontScale,
                reduced: reduced,
                delay: 0.04
            )
            .padding(.top, 11)
        }
        .padding(.horizontal, 24)
        .padding(.top, paddingTop)
        .padding(.bottom, 28)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
    }
}

/// The height of the scroll content, to know whether the copy overflows the screen.
private struct TactileCalculatorWelcomeHeightKey: PreferenceKey {
    static let defaultValue: CGFloat = 0

    static func reduce(value: inout CGFloat, nextValue: () -> CGFloat) {
        value = nextValue()
    }
}

/// Switches the bounce of the enclosing scroll view off (`bounces={false}` in React Native).
private struct TactileCalculatorWelcomeNoBounce: UIViewRepresentable {
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
