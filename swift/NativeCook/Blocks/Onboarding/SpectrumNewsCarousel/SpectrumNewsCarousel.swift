import SwiftUI
import UIKit

// Spectrum News Carousel: the welcome tour of an invented news app, Spectrum. A warm welcome window
// grows from a small rounded card to the whole screen over a field of seeded particles, then five
// pages follow: the emblem, ten fictional publication badges around a news icon, a person choosing
// topics, a summary panel with a count and an audio chip, and a photograph beside a question card.
// "Continue with Apple" at the foot opens a daily briefing.
//
// One 22 s loop (autoPlay), t in seconds, every frame a function of t (SpectrumNewsCarouselMotion):
//   0.00-1.75  the welcome window opens (rounded card to full screen); the warm glow comes in at 0.6
//              and the emblem page fades in over the first 0.5 s; the footer takes touches from 1.5.
//   2.50       page 0 at rest.
//   6.45, 8.70, 11.117, 12.833   four forward swipes (the reference's own finger positions).
//   15.4, 16.2, 17.1, 19.0       four swipes back, the last ones through slow finger preludes (to 20.4).
//   21.4-22.0  the window closes again to its first frame and the glow fades, so the loop ends where it began.
// The particles drift on whole harmonics of the loop and change density, spread and colour with the
// page position.
//
// Touch: a finger on the page area stops the clock; a horizontal drag follows the finger (rubber-band
// past either end) and settles on a spring (400 ms, damping ratio 0.8) by distance (18% of the width)
// or speed (450 pt/s); the dots jump to a page. Two seconds after the last touch the loop restarts from
// the resting time of the page that was reached. Reduce Motion: the full welcome page, pages change in
// place and the particles hold still.
//
// `previewTime` freezes the loop at one moment (and switches touch off): the lead uses it to compare
// frames. `onOpenBriefing` is called when Continue with Apple is pressed, `onPageChange` with the page
// that was reached.

struct SpectrumNewsCarousel: View {
    /// One period of the loop in seconds (`LOOP` in the React Native file).
    static let loop: Double = 22

    var autoPlay = false
    var previewTime: Double?
    var onOpenBriefing: (() -> Void)?
    var onPageChange: ((Int) -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var engine: SpectrumNewsCarouselEngine

    init(
        autoPlay: Bool = false,
        previewTime: Double? = nil,
        onOpenBriefing: (() -> Void)? = nil,
        onPageChange: ((Int) -> Void)? = nil
    ) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onOpenBriefing = onOpenBriefing
        self.onPageChange = onPageChange
        _engine = State(
            initialValue: SpectrumNewsCarouselEngine(
                autoPlay: autoPlay,
                previewTime: previewTime,
                reduced: UIAccessibility.isReduceMotionEnabled
            )
        )
        _ = SpectrumNewsCarouselPhoto.image
    }

    var body: some View {
        GeometryReader { geo in
            // The page is laid out on the whole window, as React Native's `useWindowDimensions` sees it,
            // and drawn under the status bar and the home indicator.
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            SpectrumNewsCarouselScreen(
                engine: engine,
                size: size,
                safeTop: insets.top,
                safeBottom: insets.bottom,
                reduced: reduceMotion
            )
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .background(SpectrumNewsCarouselPalette.ground.ignoresSafeArea())
        // `<StatusBar style="dark" />`: dark status bar content on the light page.
        .preferredColorScheme(.light)
        .onAppear {
            engine.configure(
                autoPlay: autoPlay,
                previewTime: previewTime,
                onOpenBriefing: onOpenBriefing,
                onPageChange: onPageChange
            )
            engine.setReduced(reduceMotion)
            engine.setForeground(scenePhase == .active)
            engine.start()
        }
        .onDisappear {
            engine.teardown()
        }
        .onChange(of: reduceMotion) { _, value in
            engine.setReduced(value)
        }
        .onChange(of: scenePhase) { _, value in
            engine.setForeground(value == .active)
        }
        .onChange(of: autoPlay) { _, value in
            engine.configure(
                autoPlay: value,
                previewTime: previewTime,
                onOpenBriefing: onOpenBriefing,
                onPageChange: onPageChange
            )
        }
        .onChange(of: previewTime) { _, value in
            engine.configure(
                autoPlay: autoPlay,
                previewTime: value,
                onOpenBriefing: onOpenBriefing,
                onPageChange: onPageChange
            )
        }
    }
}

// MARK: - Screen

/// The tour or the briefing, on the whole window. The tour is drawn from one `TimelineView`, which is
/// paused whenever nothing moves.
struct SpectrumNewsCarouselScreen: View {
    private typealias M = SpectrumNewsCarouselMotion
    private typealias S = SpectrumNewsCarouselTextStyle
    private typealias P = SpectrumNewsCarouselPalette

    let engine: SpectrumNewsCarouselEngine
    /// The window's size in points.
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let reduced: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var measuredKey = ""
    @State private var measuredHeight: CGFloat = 0

    var body: some View {
        let fontScale = SpectrumNewsCarouselFontScale.value(dynamicTypeSize)
        // Reading `revision` redraws the paused timeline after a change that does not move anything.
        let _ = engine.revision
        ZStack(alignment: .topLeading) {
            P.ground
            if engine.briefing && !engine.previewing {
                SpectrumNewsCarouselBriefing(
                    engine: engine,
                    size: size,
                    safeTop: safeTop,
                    safeBottom: safeBottom,
                    fontScale: fontScale,
                    reduced: reduced
                )
            } else {
                TimelineView(.animation(paused: !engine.moving)) { timeline in
                    stage(engine.frame(at: timeline.date), fontScale: fontScale)
                }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    // MARK: Stage

    /// The `MaskedView`: the warm backdrop, the particles and the content, seen through the welcome
    /// window.
    private func stage(_ frame: SpectrumNewsCarouselFrame, fontScale: Double) -> some View {
        ZStack(alignment: .topLeading) {
            SpectrumNewsCarouselWarmBackdrop(
                window: frame.window,
                glow: frame.glow,
                position: frame.position,
                size: size
            )
            // `<View style={{ position: 'absolute', top: insets.top, left: 0, right: 0 }}>`.
            SpectrumNewsCarouselParticles(time: frame.t, page: frame.position, width: size.width)
                .offset(y: safeTop)
            content(frame, fontScale: fontScale)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .mask(SpectrumNewsCarouselWindowShape(window: frame.window))
    }

    /// `styles.content`: the page area over the footer, under `insets.top + 10` and over `insets.bottom + 4`.
    private func content(_ frame: SpectrumNewsCarouselFrame, fontScale: Double) -> some View {
        VStack(spacing: 0) {
            viewport(frame, fontScale: fontScale)
            footer(frame, fontScale: fontScale)
        }
        .padding(.top, safeTop + 10)
        .padding(.bottom, safeBottom + 4)
        .frame(width: size.width, height: size.height, alignment: .top)
    }

    // MARK: Page area

    /// The artwork's scale to the screen (`scale`).
    private var artScale: CGFloat {
        let byWidth = (size.width - 64) / 300
        let byHeight = max(0.7, (size.height - safeTop - safeBottom - 365) / 285)
        return min(byWidth, byHeight, 1.08)
    }

    /// The `ProgressiveBlurViewport` and its `ScrollView`: the scene of five pages, with the touch
    /// layer over it and the edge blur when the scene is taller than the viewport.
    private func viewport(_ frame: SpectrumNewsCarouselFrame, fontScale: Double) -> some View {
        let largeText = fontScale > 1.35
        let layoutKey = "\(size.width):\(fontScale)"
        let scale = artScale
        // `paddingTop: Math.max(0, (height - insets.top - insets.bottom - 680) * .18)`.
        let padTop = max(0, (size.height - safeTop - safeBottom - 680) * 0.18)
        let padBottom: CGFloat = largeText ? 16 : 0
        // The scene's height: the tallest page once measured, an estimate until then.
        let estimate = 285 * scale + 210 * CGFloat(max(1, fontScale))
        let sceneHeight = measuredKey == layoutKey ? measuredHeight : estimate
        return GeometryReader { proxy in
            let viewportHeight = proxy.size.height
            let overflows = padTop + sceneHeight + padBottom > viewportHeight + 4
            let edge = min(SpectrumNewsCarouselEdgeBlur.edgeSize, viewportHeight / 2)
            ScrollView(.vertical, showsIndicators: false) {
                scene(frame, fontScale: fontScale, scale: scale, sceneHeight: sceneHeight, layoutKey: layoutKey)
                    .padding(.top, padTop)
                    .padding(.bottom, padBottom)
                    .frame(width: size.width)
                    .frame(minHeight: viewportHeight, alignment: .top)
                    .overlay {
                        SpectrumNewsCarouselTouchSurface(
                            panRect: CGRect(x: 0, y: padTop, width: size.width, height: sceneHeight),
                            panEnabled: !engine.previewing,
                            handlers: touchHandlers
                        )
                    }
                    .background(
                        SpectrumNewsCarouselScrollWatcher(
                            onDragBegan: { engine.scrollDragBegan() },
                            onDragEnded: { engine.scrollDragEnded() },
                            onMomentumBegan: { engine.scrollMomentumBegan() },
                            onMomentumEnded: { engine.scrollMomentumEnded() }
                        )
                    )
            }
            // `alwaysBounceVertical`: React Native's vertical scroll view bounces even when it fits.
            .scrollBounceBehavior(.always)
            // `key={layoutKey}`: a new width or text size starts a fresh scroll view.
            .id(layoutKey)
            .overlay(alignment: .top) {
                if overflows {
                    SpectrumNewsCarouselEdgeBlur(edge: .top, size: edge, reduceTransparency: reduceTransparency)
                }
            }
            .overlay(alignment: .bottom) {
                if overflows {
                    SpectrumNewsCarouselEdgeBlur(edge: .bottom, size: edge, reduceTransparency: reduceTransparency)
                }
            }
        }
        .frame(maxHeight: .infinity)
    }

    /// The touch layer's reports, with the window's width the pan needs.
    private var touchHandlers: SpectrumNewsCarouselTouchHandlers {
        let engine = self.engine
        let width = Double(size.width)
        return SpectrumNewsCarouselTouchHandlers(
            touchStart: { engine.touchStart(count: $0) },
            touchEnd: { engine.touchEnd(remaining: $0) },
            panStart: { engine.panStart() },
            panUpdate: { engine.panUpdate(translationX: $0, width: width) },
            panFinish: { translation, velocity, success in
                engine.panFinish(translationX: translation, velocityX: velocity, success: success, width: width)
            }
        )
    }

    /// The scene: five pages on top of one another, as tall as the tallest of them, clipped.
    private func scene(
        _ frame: SpectrumNewsCarouselFrame,
        fontScale: Double,
        scale: CGFloat,
        sceneHeight: CGFloat,
        layoutKey: String
    ) -> some View {
        let copy = SpectrumNewsCarouselCopy.pages[frame.page]
        let label = "Page \(frame.page + 1) of 5. \(copy.title) \(copy.body) \(copy.art)"
        return ZStack(alignment: .topLeading) {
            ForEach(0..<M.pageCount, id: \.self) { index in
                SpectrumNewsCarouselPage(
                    index: index,
                    width: size.width,
                    frame: frame,
                    reduced: reduced,
                    scale: scale,
                    fontScale: fontScale
                )
            }
        }
        .frame(width: size.width, height: sceneHeight, alignment: .topLeading)
        .clipped()
        .onPreferenceChange(SpectrumNewsCarouselSceneHeightKey.self) { height in
            measure(height, layoutKey: layoutKey)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(Text(verbatim: label))
        .accessibilityAdjustableAction { direction in
            engine.go(frame.page + (direction == .increment ? 1 : -1))
        }
    }

    /// `measureScene`: keeps the tallest height measured for the current width and text size.
    private func measure(_ height: CGFloat, layoutKey: String) {
        let next = ceil(height)
        guard next > 0 else { return }
        if measuredKey == layoutKey && measuredHeight >= next {
            return
        }
        let known = measuredKey == layoutKey ? measuredHeight : 0
        measuredKey = layoutKey
        measuredHeight = max(known, next)
    }

    // MARK: Footer

    /// `styles.footer`: the page dots, the Continue with Apple button and the small print. It takes
    /// no touches and stays out of accessibility until the intro has played.
    private func footer(_ frame: SpectrumNewsCarouselFrame, fontScale: Double) -> some View {
        VStack(spacing: 0) {
            pager(frame)
                .padding(.bottom, 4)
            appleButton(fontScale: fontScale)
            finePrint(fontScale: fontScale)
        }
        .padding(.horizontal, 24)
        .allowsHitTesting(frame.introReady)
        .accessibilityHidden(!frame.introReady)
    }

    /// `pageControls`: five dots. Each one is a 16 pt cell, but React Native's padding makes its press
    /// area 30 pt wide, so the dot sits 15 pt from the cell's left edge, not 8, and the row's dots
    /// land 7 pt right of centre, as they do in React Native.
    private func pager(_ frame: SpectrumNewsCarouselFrame) -> some View {
        HStack(spacing: 0) {
            ForEach(0..<M.pageCount, id: \.self) { index in
                let title = SpectrumNewsCarouselCopy.pages[index].title
                SpectrumNewsCarouselPressable(
                    feedback: .tourButton,
                    reduced: reduced,
                    label: "Page \(index + 1): \(title)",
                    // The press scales about the 16 pt wrapper's centre, 8 pt into the 30 pt area.
                    anchor: UnitPoint(x: 8.0 / 30.0, y: 0.5),
                    onPressIn: { engine.controlPressIn() },
                    onPressOut: { engine.controlPressOut() },
                    onPress: { engine.go(index) }
                ) {
                    Circle()
                        .fill(frame.page == index ? P.rgb(0x2B292E) : P.rgb(0xDAD7D2))
                        .frame(width: 6, height: 6)
                        .frame(width: 30, height: 48)
                }
                .frame(width: 16, height: 48, alignment: .leading)
            }
        }
        .frame(maxWidth: .infinity)
    }

    /// `appleButton` in a `PressScale`: a black 300 pt button, 48 pt tall, radius 7.
    private func appleButton(fontScale: Double) -> some View {
        let style = S.appleLabel
        return SpectrumNewsCarouselPressable(
            feedback: .pressScale,
            reduced: reduced,
            label: "Continue with Apple",
            onPressIn: { engine.controlPressIn() },
            onPressOut: { engine.controlPressOut() },
            onPress: { engine.openBriefing() }
        ) {
            HStack(spacing: 6) {
                // `Ionicons logo-apple`, size 21.
                Image(systemName: "applelogo")
                    .font(.system(size: 21))
                    .foregroundStyle(Color.white)
                SpectrumNewsCarouselLabel("Continue with Apple", style: style, scale: style.scale(for: fontScale))
            }
            .padding(.vertical, 8)
            .frame(width: min(300, size.width - 48))
            .frame(minHeight: 48)
            .background(RoundedRectangle(cornerRadius: 7, style: .circular).fill(Color.black))
        }
        .frame(maxWidth: .infinity)
    }

    /// `finePrint`: two lines, the names of the two documents underlined.
    private func finePrint(fontScale: Double) -> some View {
        let style = S.finePrint
        // The names of the two documents are `styles.link`: underlined, in the text's own colour.
        let words = Text(verbatim: "By signing up for and using Spectrum, you agree to our\n")
            + Text(verbatim: "Terms of Service").underline()
            + Text(verbatim: " & ")
            + Text(verbatim: "Privacy Policy").underline()
        return SpectrumNewsCarouselLabel(text: words, style: style, scale: style.scale(for: fontScale))
            .padding(.top, 20)
            .padding(.bottom, 14)
    }
}
