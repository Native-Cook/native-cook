import SwiftUI
import UIKit

private typealias M = NocturneProgramCarouselMotion
private typealias P = NocturneProgramCarouselPalette

// MARK: - Layout numbers

/// The sizes `NocturneProgramCarousel` works out from the window (`useWindowDimensions`, the safe
/// area insets and the user's text size).
struct NocturneProgramCarouselMetrics {
    /// The window's size in points.
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    /// React Native's `fontScale`.
    let fontScale: Double

    /// `largeText = fontScale > 1.35`: the header moves into the scroll content.
    var largeText: Bool { fontScale > 1.35 }

    /// `Math.min(width * .76, 370)`.
    var cardWidth: CGFloat { min(size.width * 0.76, 370) }

    /// `cardWidth * (.735 / .7556)`: the distance between two cards' left edges.
    var stride: CGFloat { cardWidth * (0.735 / 0.7556) }

    /// `largeText ? 210 : Math.max(206, Math.min(304, height - insets.top - insets.bottom - 380))`.
    var artHeight: CGFloat {
        largeText ? 210 : max(206, min(304, size.height - safeTop - safeBottom - 380))
    }

    /// `artHeight + 150`.
    var minimumCardHeight: CGFloat { artHeight + 150 }

    /// `Math.max(insets.bottom - 6, 8)`: the bottom padding of the footer and of the tab bar.
    var footerBottomInset: CGFloat { max(safeBottom - 6, 8) }
}

/// The three pages of the tour (`PAGES`), character for character.
enum NocturneProgramCarouselPages {
    struct Page {
        let title: String
        let body: String
    }

    static let items: [Page] = [
        Page(
            title: "An evening that fits you",
            body: "A small, thoughtful routine.\nMake it yours, one night at a time."),
        Page(
            title: "Notice your rhythm",
            body: "Check in with your evenings.\nSee the habits you are building."),
        Page(
            title: "Make space to unwind",
            body: "Slow down and notice how you feel.\nA little attention goes a long way."),
    ]
}

// MARK: - The welcome

/// The starry welcome: a crescent mark with a halo and drifting sparks over "Welcome to a quieter
/// evening". It is full screen, centred a little above the middle (`paddingBottom: 90`), and hands
/// over to the program between 1.5 and 2 s.
struct NocturneProgramCarouselWelcome: View {
    let t: Double
    let held: Bool
    let reduced: Bool
    let fontScale: Double

    var body: some View {
        let opacity = held ? 0 : M.welcomeOpacity(t: t)
        let scale = reduced ? 1 : M.welcomeScale(t: t)
        VStack(spacing: 16) {
            mark
            NocturneProgramCarouselLabel(
                text: "Welcome to\na quieter evening",
                style: .welcome,
                fontScale: fontScale
            )
        }
        .padding(.bottom, 90)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .scaleEffect(CGFloat(scale))
        .opacity(opacity)
        .allowsHitTesting(false)
    }

    /// `welcomeMark`: 44 x 44 with a 180 x 180 halo centred on it and the sparks around it.
    private var mark: some View {
        ZStack {
            Circle()
                .fill(
                    RadialGradient(
                        stops: [
                            Gradient.Stop(color: P.rgb(0x3E7BFF, opacity: 0.34), location: 0),
                            Gradient.Stop(color: P.rgb(0x3E7BFF, opacity: 0.1), location: 0.45),
                            Gradient.Stop(color: P.rgb(0x3E7BFF, opacity: 0), location: 1),
                        ],
                        center: .center,
                        startRadius: 0,
                        endRadius: 90
                    )
                )
                .frame(width: 180, height: 180)
            if !reduced && t < 2 {
                ForEach(0..<Self.sparks.count, id: \.self) { index in
                    NocturneProgramCarouselSpark(spark: Self.sparks[index], t: t)
                }
            }
            NocturneProgramCarouselMark(size: 44)
        }
        .frame(width: 44, height: 44)
    }

    /// `SPARKS`: eleven stars on a golden-angle spiral around the mark.
    private static let sparks: [NocturneProgramCarouselSparkSpec] = (0..<11).map { index in
        let number = Double(index)
        let angle = number * 2.399 + 0.4
        let radius = 58 + Double((index * 37) % 64)
        return NocturneProgramCarouselSparkSpec(
            x: cos(angle) * radius,
            y: sin(angle) * radius * 0.72 - 22,
            size: index % 4 == 0 ? 3 : 2,
            delay: fmod(number * 0.13, 0.6),
            color: index % 3 == 0 ? P.rgb(0x7BE3D8) : P.rgb(0x8FB1FF)
        )
    }
}

struct NocturneProgramCarouselSparkSpec {
    let x: Double
    let y: Double
    let size: CGFloat
    let delay: Double
    let color: Color
}

/// A faint star that drifts away from the mark while the welcome holds.
private struct NocturneProgramCarouselSpark: View {
    let spark: NocturneProgramCarouselSparkSpec
    let t: Double

    var body: some View {
        let shown = M.enter(M.segment(t, 0.2 + spark.delay, 0.8 + spark.delay))
            * (1 - M.enter(M.segment(t, 1.35, 1.85)))
        let twinkle = 0.55 + 0.45 * sin(t * 5.1 + spark.delay * 9)
        let drift = 1 + 0.06 * t
        Circle()
            .fill(spark.color)
            .frame(width: spark.size, height: spark.size)
            .background {
                // `boxShadow: 0 0 6px color`: a 3 pt Gaussian.
                Circle().fill(spark.color).blur(radius: 3)
            }
            .opacity(shown * twinkle)
            .offset(x: CGFloat(spark.x * drift), y: CGFloat(spark.y * drift - 6 * t))
    }
}

/// The header of the program: mark, "The Nocturne program" and a line under it.
struct NocturneProgramCarouselHeader: View {
    let fontScale: Double

    var body: some View {
        VStack(spacing: 6) {
            NocturneProgramCarouselMark()
            NocturneProgramCarouselLabel(text: "The Nocturne program", style: .heading, fontScale: fontScale)
                .padding(.top, 8)
            NocturneProgramCarouselLabel(
                text: "A few good habits. A little more calm.",
                style: .subheading,
                fontScale: fontScale
            )
        }
        .padding(.horizontal, 24)
        .frame(maxWidth: .infinity)
    }
}

// MARK: - The card row

/// The row of three cards, slid along by the page position, on a curved shelf.
struct NocturneProgramCarouselTrack: View {
    let frame: NocturneProgramCarouselFrame
    let metrics: NocturneProgramCarouselMetrics
    let region: NocturneProgramCarouselRegion
    /// The `increment` / `decrement` accessibility actions: go to the page `delta` away.
    let onAdjust: (Int) -> Void

    var body: some View {
        let cardWidth = metrics.cardWidth
        let stride = metrics.stride
        let page = NocturneProgramCarouselPages.items[frame.page]
        let shelf = cardWidth + 2 * stride
        let slide = (metrics.size.width - cardWidth) / 2 - CGFloat(frame.position) * stride
        HStack(alignment: .top, spacing: stride - cardWidth) {
            ForEach(0..<NocturneProgramCarouselPages.items.count, id: \.self) { index in
                NocturneProgramCarouselCard(index: index, frame: frame, metrics: metrics)
            }
        }
        // `alignItems: 'stretch'`: the cards' frames are as tall as the tallest.
        .fixedSize(horizontal: false, vertical: true)
        .frame(width: shelf, alignment: .leading)
        .offset(x: slide)
        .frame(width: metrics.size.width, alignment: .leading)
        .clipped()
        .background(NocturneProgramCarouselRegionMarker(region: region))
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(
            "Nocturne tour, page \(frame.page + 1) of 3. \(page.title). \(page.body)"
        )
        .accessibilityAdjustableAction { direction in
            onAdjust(direction == .increment ? 1 : -1)
        }
    }
}

/// One card of the row: the frame that stands back and turns toward the centre, the artwork that
/// lags it, and the copy under it.
private struct NocturneProgramCarouselCard: View {
    let index: Int
    let frame: NocturneProgramCarouselFrame
    let metrics: NocturneProgramCarouselMetrics

    var body: some View {
        let distance = Double(index) - frame.position
        let scale = M.cardScale(distance: distance, reduced: frame.reduced)
        let angle = M.cardAngle(distance: distance, reduced: frame.reduced)
        let shade = M.cardShade(distance: distance)
        card
            .frame(width: metrics.cardWidth, alignment: .top)
            .frame(maxHeight: .infinity, alignment: .top)
            .overlay {
                // Cards waiting at the edges sink into the night.
                P.shade
                    .opacity(shade)
                    .allowsHitTesting(false)
            }
            .clipShape(RoundedRectangle(cornerRadius: 22))
            .modifier(NocturneProgramCarouselCardTilt(scale: CGFloat(scale), angle: CGFloat(angle)))
    }

    /// `styles.card`: a navy gradient, 18 pt corners, a 1 pt border, `paddingTop: 14`. The React
    /// Native `boxShadow` is not drawn: the card frame clips it away.
    private var card: some View {
        let innerWidth = metrics.cardWidth - 2
        let distance = Double(index) - frame.position
        let parallax = frame.reduced ? 0 : M.clamp(distance, -1, 1) * -Double(metrics.cardWidth * 0.22)
        return VStack(spacing: 0) {
            art(width: innerWidth)
                .offset(x: CGFloat(parallax))
                .accessibilityHidden(true)
            NocturneProgramCarouselCardCopy(
                index: index,
                t: frame.t,
                reduced: frame.reduced,
                fontScale: metrics.fontScale
            )
        }
        .padding(.top, 14)
        .padding(1)
        .frame(width: metrics.cardWidth, alignment: .top)
        .frame(minHeight: metrics.minimumCardHeight, alignment: .top)
        .background {
            LinearGradient(
                stops: [
                    Gradient.Stop(color: P.rgb(0x0B2A70), location: 0),
                    Gradient.Stop(color: P.rgb(0x0A2560), location: 0.45),
                    Gradient.Stop(color: P.rgb(0x071C48), location: 0.75),
                    Gradient.Stop(color: P.rgb(0x061840), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
        }
        .clipShape(RoundedRectangle(cornerRadius: 18))
        .overlay {
            RoundedRectangle(cornerRadius: 18).strokeBorder(P.rgba(0x2A4C9440), lineWidth: 1)
        }
    }

    @ViewBuilder
    private func art(width: CGFloat) -> some View {
        switch index {
        case 0:
            NocturneProgramCarouselGuidePortrait(
                t: frame.t, reduced: frame.reduced, width: width, height: metrics.artHeight)
        case 1:
            NocturneProgramCarouselMiniDashboard(
                t: frame.t, reduced: frame.reduced, width: width, height: metrics.artHeight)
        default:
            NocturneProgramCarouselBodyScan(
                t: frame.t, reduced: frame.reduced, width: width, height: metrics.artHeight)
        }
    }
}

/// `CardFrame`'s transform: `perspective: 900`, then the scale, then `rotateY`, all about the card's
/// centre. Neighbours stand back and turn toward the centre, so the row reads as a curved shelf.
struct NocturneProgramCarouselCardTilt: GeometryEffect {
    var scale: CGFloat
    /// Degrees about the vertical axis.
    var angle: CGFloat

    var animatableData: AnimatablePair<CGFloat, CGFloat> {
        get { AnimatablePair(scale, angle) }
        set {
            scale = newValue.first
            angle = newValue.second
        }
    }

    func effectValue(size: CGSize) -> ProjectionTransform {
        let centerX = size.width / 2
        let centerY = size.height / 2
        var transform = CATransform3DMakeTranslation(-centerX, -centerY, 0)
        transform = CATransform3DConcat(
            transform,
            CATransform3DMakeRotation(angle * CGFloat.pi / 180, 0, 1, 0)
        )
        transform = CATransform3DConcat(transform, CATransform3DMakeScale(scale, scale, 1))
        var lens = CATransform3DIdentity
        lens.m34 = -1 / 900
        transform = CATransform3DConcat(transform, lens)
        transform = CATransform3DConcat(
            transform,
            CATransform3DMakeTranslation(centerX, centerY, 0)
        )
        return ProjectionTransform(transform)
    }
}

// MARK: - Card copy

/// `CardCopy`: the title, the two lines and the page dots. The copy of the second and third card
/// comes out of a soft focus as the card arrives (opacity, a 6 pt rise and a 5 px blur).
private struct NocturneProgramCarouselCardCopy: View {
    let index: Int
    let t: Double
    let reduced: Bool
    let fontScale: Double

    var body: some View {
        let shown = M.copyShown(t: t, index: index, reduced: reduced)
        let page = NocturneProgramCarouselPages.items[index]
        VStack(spacing: 0) {
            NocturneProgramCarouselLabel(text: page.title, style: .cardTitle, fontScale: fontScale)
            NocturneProgramCarouselLabel(text: page.body, style: .cardBody, fontScale: fontScale)
                .padding(.top, 12)
            dots
                .padding(.top, 22)
        }
        .padding(.horizontal, 16)
        .padding(.top, 14)
        .padding(.bottom, 20)
        .frame(maxWidth: .infinity)
        // `filter: blur(5px)` draws softer on iOS than SwiftUI's blur: half the radius.
        .modifier(NocturneProgramCarouselSoftFocus(radius: (1 - shown) * 5 * 0.5))
        .opacity(shown)
        .offset(y: CGFloat((1 - shown) * 6))
    }

    /// `cardDots`: 4 pt dots, the card's own 14 pt wide and white.
    private var dots: some View {
        HStack(spacing: 4) {
            ForEach(0..<NocturneProgramCarouselPages.items.count, id: \.self) { dot in
                RoundedRectangle(cornerRadius: 2)
                    .fill(dot == index ? P.rgb(0xFFFFFF) : P.rgb(0x5D6F99))
                    .frame(width: dot == index ? 14 : 4, height: 4)
            }
        }
        .accessibilityHidden(true)
    }
}

/// A blur that is left out when it has nothing to do.
private struct NocturneProgramCarouselSoftFocus: ViewModifier {
    let radius: Double

    func body(content: Content) -> some View {
        if radius > 0.01 {
            content.blur(radius: CGFloat(radius))
        } else {
            content
        }
    }
}
