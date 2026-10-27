import SwiftUI
import UIKit

/*
 Paper Deck Tutorial: a six-card conversation tutorial on ink black. A "Tutorial" header with its count
 sits between a gear and a lock disc while the illustrated cover deck shows, and a white close disc
 while the tutorial runs. The cover (a puzzled character on violet, two covers peeking above) turns
 edge-on to reveal the first white card; each card carries one serif instruction, an emoji and its
 number, and flies off in the direction it asks for. The tutorial ends on a "This or that" deck with a
 burger-or-apple card.

 autoPlay (loop = 18.5 s): the cover lifts from 0.2 s and turns about its vertical axis 0.6 to 1.12 s with
 its edge-on gap at 0.84 s; the rear sheets appear at 1.1 s and settle by 1.6 s; throws to the right and
 left run 3.4 to 4.0 s and 5.15 to 5.75 s, short right throws at 9.1, 11.3, 12.6 and 14.3 s; the choice
 deck rises 14.9 to 15.2 s and holds to 17.5 s, then the cover returns (17.9 to 18.5 s). A touch anywhere
 stops the loop where it is; 2 s after the last finger leaves, the card springs back and the loop
 continues from the card on top. A card can be dragged and thrown (it bends with the finger, turns
 about where it was grabbed, throws a wider shadow and carries a highlight); `onComplete` runs when
 the sixth card is gone. Reduced Motion: no loop, the first card as a still page; a flick still turns
 the page.

 Every frame is a function of the loop time: the header and the deck each sit in a `TimelineView`
 that is paused whenever nothing moves.
*/
struct PaperDeckTutorial: View {
    /// The seconds of one `autoPlay` loop (`LOOP`).
    static let loop: Double = 18.5

    /// Plays the whole tutorial by itself, in a loop.
    var autoPlay = false
    /// Freezes the loop at this time (folded into one period) and ignores touches.
    var previewTime: Double?
    /// Runs when the last card has been thrown off, or the close disc is pressed.
    var onComplete: (() -> Void)?

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var controller: PaperDeckTutorialController

    init(autoPlay: Bool = false, previewTime: Double? = nil, onComplete: (() -> Void)? = nil) {
        self.autoPlay = autoPlay
        self.previewTime = previewTime
        self.onComplete = onComplete
        PaperDeckTutorialAssets.prepare()
        _controller = State(initialValue: PaperDeckTutorialController(autoPlay: autoPlay, previewTime: previewTime))
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
            PaperDeckTutorialScreen(
                controller: controller,
                size: size,
                safeTop: insets.top,
                safeBottom: insets.bottom,
                reduced: reduceMotion
            )
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .background(PaperDeckTutorialPalette.ground.ignoresSafeArea())
        // `<StatusBar style="light" />`: light status bar content on the black page.
        .preferredColorScheme(.dark)
        .onAppear {
            controller.configure(autoPlay: autoPlay, previewTime: previewTime, onComplete: onComplete)
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
            controller.configure(autoPlay: autoPlay, previewTime: value, onComplete: onComplete)
            controller.sync()
        }
        .onChange(of: autoPlay) { _, value in
            controller.configure(autoPlay: value, previewTime: previewTime, onComplete: onComplete)
            controller.sync()
        }
    }
}

// MARK: - Metrics

/// The sizes of the page, from the window and the text size (`PaperDeckTutorial` and its `styles`).
struct PaperDeckTutorialMetrics {
    /// The window, safe areas included.
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    /// React Native's `fontScale`.
    let fontScale: Double

    /// Large text turns the page into a scroll view with a progressive blur at its edges.
    var large: Bool { fontScale > 1.4 }

    /// `Math.min(screenWidth * 0.9, 410)`.
    var cardWidth: CGFloat { min(size.width * 0.9, 410) }

    /// `Math.max(fontScale > 1.4 ? 520 : 400, Math.min(660, screenHeight * 0.6))`.
    var cardHeight: CGFloat {
        let minimum: CGFloat = large ? 520 : 400
        let preferred = min(660, size.height * 0.6)
        return max(minimum, preferred)
    }

    /// `paddingTop: safe.top + 12` of the scroll content.
    var padTop: CGFloat { safeTop + 12 }

    /// `paddingBottom: Math.max(safe.bottom, 16)`.
    var padBottom: CGFloat { max(safeBottom, 16) }

    /// `height: 62 * Math.max(1, fontScale * 0.8)`.
    var headerHeight: CGFloat { 62 * CGFloat(max(1, fontScale * 0.8)) }

    /// The stage grows to fill the page (`flex: 1`) but is never shorter than the card plus 90 pt.
    var stageHeight: CGFloat {
        let free = size.height - padTop - headerHeight - padBottom
        return max(cardHeight + 90, free)
    }

    /// The stage's `paddingTop`: the card top lands at 0.224 of the screen height, or the card is centred.
    var stagePaddingTop: CGFloat {
        if large { return 8 }
        return max(8, size.height * 0.224 - safeTop - 12 - 62)
    }

    /// `minHeight: screenHeight` of the scroll content.
    var contentHeight: CGFloat { padTop + headerHeight + stageHeight + padBottom }

    /// `introTravel`: how far below the card position the cover rests.
    var introTravel: Double { Double(size.height) * 0.125 }

    var layout: PaperDeckTutorialLayout {
        PaperDeckTutorialLayout(
            screenWidth: Double(size.width),
            cardWidth: Double(cardWidth),
            cardHeight: Double(cardHeight)
        )
    }
}

// MARK: - Screen

/// The page: a header and the stage, in a scroll view only at large text sizes.
struct PaperDeckTutorialScreen: View {
    let controller: PaperDeckTutorialController
    /// The window's size in points.
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let reduced: Bool

    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency

    var body: some View {
        let fontScale = PaperDeckTutorialScript.fontScale(dynamicTypeSize)
        let metrics = PaperDeckTutorialMetrics(size: size, safeTop: safeTop, safeBottom: safeBottom, fontScale: fontScale)
        // The progressive blur at the edges only shows when the page is taller than the screen.
        let overflows = metrics.large && metrics.contentHeight > size.height + 4
        Group {
            if metrics.large {
                ScrollView {
                    page(metrics)
                }
                // `bounces={false}`: SwiftUI has no "never", so the scroll view is switched off below as well.
                .scrollBounceBehavior(.basedOnSize)
            } else {
                page(metrics)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .top)
        .background(PaperDeckTutorialPalette.ground)
        .overlay(alignment: .top) {
            if overflows {
                PaperDeckTutorialEdgeBlur(edge: .top, reduceTransparency: reduceTransparency)
            }
        }
        .overlay(alignment: .bottom) {
            if overflows {
                PaperDeckTutorialEdgeBlur(edge: .bottom, reduceTransparency: reduceTransparency)
            }
        }
        .clipped()
        .onChange(of: metrics.layout, initial: true) { _, value in
            controller.updateLayout(value)
        }
    }

    private func page(_ metrics: PaperDeckTutorialMetrics) -> some View {
        VStack(spacing: 0) {
            PaperDeckTutorialHeader(controller: controller, metrics: metrics, reduced: reduced)
            PaperDeckTutorialDeck(controller: controller, metrics: metrics, reduced: reduced)
                .padding(.top, metrics.stagePaddingTop)
                .frame(width: size.width, height: metrics.stageHeight, alignment: metrics.large ? Alignment.center : Alignment.top)
        }
        .padding(.top, metrics.padTop)
        .padding(.bottom, metrics.padBottom)
        .frame(width: size.width, height: metrics.contentHeight, alignment: .top)
        .background {
            if metrics.large {
                PaperDeckTutorialNoBounce()
            }
        }
    }
}

// MARK: - Deck

/// The card stack: the six white sheets and the illustrated cover deck over them, in a
/// `width` x `height` box. It carries the touch layer and the card's accessibility.
struct PaperDeckTutorialDeck: View {
    let controller: PaperDeckTutorialController
    let metrics: PaperDeckTutorialMetrics
    let reduced: Bool

    var body: some View {
        let width = metrics.cardWidth
        let height = metrics.cardHeight
        TimelineView(.animation(paused: !controller.isMoving)) { timeline in
            let pose = controller.pose(at: timeline.date, screenWidth: Double(metrics.size.width))
            let grab = controller.grab
            ZStack(alignment: .topLeading) {
                ForEach([5, 4, 3, 2, 1, 0], id: \.self) { index in
                    let style = PaperDeckTutorialScript.cardStyle(
                        index: index,
                        pose: pose,
                        grab: grab,
                        width: Double(width),
                        height: Double(height),
                        screenHeight: Double(metrics.size.height),
                        reduced: reduced
                    )
                    if style.opacity > 0 {
                        PaperDeckTutorialCard(
                            index: index,
                            pose: pose,
                            style: style,
                            width: width,
                            height: height,
                            reduced: reduced,
                            fontScale: metrics.fontScale
                        )
                        .zIndex(Double(10 - index))
                    }
                }
                PaperDeckTutorialCover(
                    pose: pose,
                    width: width,
                    height: height,
                    reduced: reduced,
                    introTravel: metrics.introTravel
                )
                .zIndex(20)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel(PaperDeckTutorialCopy.label(step: pose.step))
            .accessibilityHint("Swipe the card left or right, or use the adjust actions.")
            .accessibilityAdjustableAction { direction in
                controller.accessibilityAdvance(back: direction == .decrement)
            }
        }
        .frame(width: width, height: height)
        .background(PaperDeckTutorialTouchLayer(controller: controller))
    }
}
