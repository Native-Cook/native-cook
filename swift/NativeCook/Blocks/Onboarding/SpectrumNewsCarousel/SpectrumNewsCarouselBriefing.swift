import SwiftUI
import UIKit

private typealias P = SpectrumNewsCarouselPalette
private typealias S = SpectrumNewsCarouselTextStyle

/// `TourButton`: a 48 pt target that answers the finger with a 0.97 press (120 ms), either a filled
/// button with a label or, when `subtle`, a bare label.
struct SpectrumNewsCarouselTourButton: View {
    let title: String
    var subtle = false
    var color: Color = SpectrumNewsCarouselPalette.rgb(0xEAF1FF)
    var background: Color = SpectrumNewsCarouselPalette.rgb(0x256BFF)
    let reduced: Bool
    let fontScale: Double
    let engine: SpectrumNewsCarouselEngine
    let onPress: () -> Void

    var body: some View {
        let style = S.tourLabel(subtle: subtle, color: color)
        SpectrumNewsCarouselPressable(
            feedback: .tourButton,
            reduced: reduced,
            label: title,
            onPressIn: { engine.controlPressIn() },
            onPressOut: { engine.controlPressOut() },
            onPress: onPress
        ) {
            // `styles.press`: minHeight 48, radius 18, padding 15 x 14, the label centred.
            SpectrumNewsCarouselLabel(title, style: style, scale: style.scale(for: fontScale))
                .padding(.horizontal, 15)
                .padding(.vertical, 14)
                .frame(maxWidth: .infinity, minHeight: 48)
                .background(
                    RoundedRectangle(cornerRadius: 18, style: .circular)
                        .fill(subtle ? Color.clear : background)
                )
        }
    }
}

/// The daily briefing that "Continue with Apple" opens: the brand, a headline, a photograph, one
/// story with three ways to look at it, a Save button and a way back to the tour. It scrolls under
/// the same top and bottom edge blur as the tour.
struct SpectrumNewsCarouselBriefing: View {
    let engine: SpectrumNewsCarouselEngine
    let size: CGSize
    let safeTop: CGFloat
    let safeBottom: CGFloat
    let fontScale: Double
    let reduced: Bool

    @Environment(\.accessibilityReduceTransparency) private var reduceTransparency
    @State private var viewport: CGFloat = 0
    @State private var contentHeight: CGFloat = 0

    private static let perspectives = [
        "Public space: who gets to use the waterfront?",
        "Design: how do paths make room for water?",
        "Everyday life: what makes a walk feel welcoming?",
    ]

    var body: some View {
        // The edge blur only shows when the content is taller than the screen.
        let overflows = contentHeight > viewport + 4
        let edge = min(SpectrumNewsCarouselEdgeBlur.edgeSize, viewport / 2)
        ScrollView(.vertical) {
            content
                .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { contentHeight = $0 }
        }
        .scrollBounceBehavior(.always)
        .frame(width: size.width, height: size.height)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { viewport = $0 }
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

    /// `styles.briefing`: padding 27 on the sides, `insets.top + 24` above and `insets.bottom + 20` below.
    private var content: some View {
        VStack(alignment: .leading, spacing: 0) {
            // `briefingBrand`.
            HStack(spacing: 10) {
                SpectrumNewsCarouselMark(size: 34)
                SpectrumNewsCarouselLabel("Spectrum", style: S.briefingWordmark, scale: 1, alignment: .leading)
            }
            label("YOUR DAILY BRIEFING", S.date)
                .padding(.top, 33)
            label("Room for a\ndifferent view.", S.briefingTitle)
                .padding(.top, 12)
            label("A small collection to start your day.", S.briefingIntro)
                .padding(.top, 14)
            photo
                .padding(.top, 27)
            label("PLACES · 4 MIN READ", S.storyCategory)
                .padding(.top, 22)
            label("The city is meeting its river again.", S.storyHeadline)
                .padding(.top, 10)
            label(
                "A new waterfront garden turns a once-forgotten edge into a place to walk, gather and slow down.",
                S.storyBody
            )
            .padding(.top, 12)
            threeWays
                .padding(.vertical, 25)
            SpectrumNewsCarouselTourButton(
                title: engine.saved ? "Saved to your reading list" : "Save this story",
                color: engine.saved ? P.rgb(0x37532D) : P.rgb(0xFBF9F5),
                background: engine.saved ? P.rgb(0xE4EDDF) : P.rgb(0x222126),
                reduced: reduced,
                fontScale: fontScale,
                engine: engine,
                onPress: { engine.toggleSaved() }
            )
            SpectrumNewsCarouselTourButton(
                title: "Back to the tour",
                subtle: true,
                color: P.rgb(0x77746D),
                reduced: reduced,
                fontScale: fontScale,
                engine: engine,
                onPress: { engine.backToTour() }
            )
        }
        .padding(.horizontal, 27)
        .padding(.top, safeTop + 24)
        .padding(.bottom, safeBottom + 20)
        .frame(width: size.width, alignment: .topLeading)
    }

    /// A left-aligned text in one of the briefing's styles.
    private func label(_ string: String, _ style: SpectrumNewsCarouselTextStyle) -> some View {
        SpectrumNewsCarouselLabel(string, style: style, scale: style.scale(for: fontScale), alignment: .leading)
            .frame(maxWidth: .infinity, alignment: .leading)
    }

    /// `briefingPhoto`: full width, 202 pt tall, radius 16.
    private var photo: some View {
        Color.clear
            .frame(maxWidth: .infinity)
            .frame(height: 202)
            .overlay {
                Image(uiImage: SpectrumNewsCarouselPhoto.image)
                    .resizable()
                    .scaledToFill()
            }
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .circular))
            .accessibilityHidden(true)
    }

    /// `perspectives`: a hairline, a small label and three lines, with 25 pt above and below.
    private var threeWays: some View {
        VStack(alignment: .leading, spacing: 0) {
            Rectangle()
                .fill(P.rgb(0xE7E1D7))
                .frame(height: 1)
            VStack(alignment: .leading, spacing: 13) {
                label("THREE WAYS TO LOOK AT IT", S.perspectiveLabel)
                ForEach(Self.perspectives, id: \.self) { line in
                    label(line, S.perspective)
                }
            }
            .padding(.top, 19)
        }
    }
}
