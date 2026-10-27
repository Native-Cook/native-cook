import SwiftUI
import UIKit

// The five illustrations of `SpectrumArtwork.tsx`, drawn at React Native's own coordinates on a
// 300 x 285 stage. Every container is a top-leading `ZStack` with a fixed frame and its children are
// placed with `.offset`, the way `position: 'absolute'` places them.

private typealias P = SpectrumNewsCarouselPalette

// MARK: - Helpers

/// One CSS `box-shadow`: offset, blur and colour. The blur radius of CSS is twice the Gaussian
/// radius SwiftUI takes, so it is halved when drawn.
struct SpectrumNewsCarouselShadow {
    let color: Color
    let blur: CGFloat
    let y: CGFloat
}

private extension View {
    /// The box shadows of an opaque shape. Each one is drawn from the shape's outline (a chain of
    /// `.shadow` modifiers would cast the second one from the first one's shadow), so every
    /// shadow gets its own copy of the shape, in `underlay`, behind the content.
    func spectrumBoxShadows<S: Shape>(
        _ shape: S,
        underlay: Color,
        _ first: SpectrumNewsCarouselShadow,
        _ second: SpectrumNewsCarouselShadow? = nil
    ) -> some View {
        background {
            ZStack {
                shape
                    .fill(underlay)
                    .shadow(color: first.color, radius: first.blur / 2, x: 0, y: first.y)
                if let second {
                    shape
                        .fill(underlay)
                        .shadow(color: second.color, radius: second.blur / 2, x: 0, y: second.y)
                }
            }
        }
    }
}

/// A text at a fixed size (`allowFontScaling={false}`), left-aligned unless told otherwise.
private func artText(
    _ string: String,
    _ size: Double,
    _ color: UInt32,
    weight: SpectrumNewsCarouselWeight = .regular,
    kerning: Double = 0,
    lineHeight: Double? = nil,
    alignment: TextAlignment = .leading
) -> some View {
    SpectrumNewsCarouselLabel(
        string,
        style: SpectrumNewsCarouselTextStyle(
            size: size,
            lineHeight: lineHeight,
            kerning: kerning,
            weight: weight,
            color: P.rgb(color)
        ),
        scale: 1,
        alignment: alignment
    )
}

private func artGradient(_ colors: [UInt32]) -> LinearGradient {
    // `expo-linear-gradient` runs top to bottom with the colours evenly spaced.
    LinearGradient(colors: colors.map { P.rgb($0) }, startPoint: .top, endPoint: .bottom)
}

/// The bundled photograph of the fifth page and the briefing.
enum SpectrumNewsCarouselPhoto {
    static let name = "SpectrumNewsCarousel-waterfront-garden"

    /// Decoded once, before the first frame that needs it.
    static let image: UIImage = {
        guard
            let url = Bundle.main.url(forResource: name, withExtension: "webp"),
            let source = UIImage(contentsOfFile: url.path)
        else {
            assertionFailure("Missing Spectrum News Carousel photograph")
            return UIImage()
        }
        return source.preparingForDisplay() ?? source
    }()
}

// MARK: - Shapes

/// `SpectrumMark`: the emblem, a dark disc with 23 pale dots on a spiral, drawn in a 72-unit box.
struct SpectrumNewsCarouselMark: View {
    let size: CGFloat

    var body: some View {
        Canvas { context, canvasSize in
            let unit = canvasSize.width / 72
            let disc = CGRect(x: unit, y: unit, width: 70 * unit, height: 70 * unit)
            context.fill(Path(ellipseIn: disc), with: .color(P.rgb(0x17171B)))
            for index in 0..<23 {
                let angle = Double(index) * 2.4
                let radius = 5 + (Double(index) / 22).squareRoot() * 19
                let centreX = 36 + cos(angle) * radius
                let centreY = 36 + sin(angle) * radius
                let dot = index % 4 == 0 ? 2.2 : 1.5
                let box = CGRect(
                    x: CGFloat(centreX - dot) * unit,
                    y: CGFloat(centreY - dot) * unit,
                    width: CGFloat(dot * 2) * unit,
                    height: CGFloat(dot * 2) * unit
                )
                context.fill(Path(ellipseIn: box), with: .color(P.rgb(0xF7F6F2)))
            }
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

/// The news icon's glyph (40-unit box): a page with its folded edge, drawn as two strokes.
private struct SpectrumNewsCarouselNewsGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 40
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * unit, y: rect.minY + y * unit)
        }
        var path = Path()
        // `<Rect x={11} y={8} width={23} height={26} rx={2} />`.
        path.addRoundedRect(
            in: CGRect(origin: point(11, 8), size: CGSize(width: 23 * unit, height: 26 * unit)),
            cornerSize: CGSize(width: 2 * unit, height: 2 * unit),
            style: .circular
        )
        // `M7 13H5v18q0 3 3 3h23`
        path.move(to: point(7, 13))
        path.addLine(to: point(5, 13))
        path.addLine(to: point(5, 31))
        path.addQuadCurve(to: point(8, 34), control: point(5, 34))
        path.addLine(to: point(31, 34))
        // `M16 14h12M16 18h12`
        path.move(to: point(16, 14))
        path.addLine(to: point(28, 14))
        path.move(to: point(16, 18))
        path.addLine(to: point(28, 18))
        // `M16 22h4v5h-4z`
        path.move(to: point(16, 22))
        path.addLine(to: point(20, 22))
        path.addLine(to: point(20, 27))
        path.addLine(to: point(16, 27))
        path.closeSubpath()
        // `M24 23h5M24 27h5`
        path.move(to: point(24, 23))
        path.addLine(to: point(29, 23))
        path.move(to: point(24, 27))
        path.addLine(to: point(29, 27))
        return path
    }
}

/// The person's shoulders (76 x 91 box): `M5 78c0-23 14-35 33-35s33 12 33 35q-33 9-66 0Z`.
private struct SpectrumNewsCarouselShoulders: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 76
        func point(_ x: CGFloat, _ y: CGFloat) -> CGPoint {
            CGPoint(x: rect.minX + x * unit, y: rect.minY + y * unit)
        }
        var path = Path()
        path.move(to: point(5, 78))
        path.addCurve(to: point(38, 43), control1: point(5, 55), control2: point(19, 43))
        path.addCurve(to: point(71, 78), control1: point(57, 43), control2: point(71, 55))
        path.addQuadCurve(to: point(5, 78), control: point(38, 87))
        path.closeSubpath()
        return path
    }
}

/// The audio chip's play triangle (13 x 16 box): `M2 1 12 8 2 15Z`.
private struct SpectrumNewsCarouselPlayGlyph: Shape {
    func path(in rect: CGRect) -> Path {
        let unit = rect.width / 13
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 2 * unit, y: rect.minY + 1 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 12 * unit, y: rect.minY + 8 * unit))
        path.addLine(to: CGPoint(x: rect.minX + 2 * unit, y: rect.minY + 15 * unit))
        path.closeSubpath()
        return path
    }
}

// MARK: - Illustration

/// `SpectrumIllustration`: the page's artwork on its 300 x 285 stage, scaled to the screen about its
/// top-left corner (`scale`) and laid out at the scaled size.
struct SpectrumNewsCarouselIllustration: View {
    let page: Int
    /// The displayed loop time, for the badges and the audio wave.
    let t: Double
    let scale: CGFloat

    var body: some View {
        stage
            .frame(width: 300, height: 285, alignment: .topLeading)
            .scaleEffect(scale, anchor: .topLeading)
            .frame(width: 300 * scale, height: 285 * scale, alignment: .topLeading)
    }

    @ViewBuilder
    private var stage: some View {
        switch page {
        case 0:
            SpectrumNewsCarouselWelcomeMark()
        case 1:
            SpectrumNewsCarouselNewsSources(t: t)
        case 2:
            SpectrumNewsCarouselTopicSelection(scale: scale)
        case 3:
            SpectrumNewsCarouselStorySummary(t: t)
        default:
            SpectrumNewsCarouselStoryQuestions()
        }
    }
}

/// Page 1's art: the emblem and its wordmark (`welcomeMark`).
private struct SpectrumNewsCarouselWelcomeMark: View {
    var body: some View {
        VStack(spacing: 12) {
            SpectrumNewsCarouselMark(size: 90)
            artText("Spectrum", 27, 0x111111, weight: .heavy, kerning: -0.6, alignment: .center)
        }
        .padding(.top, 135)
        .frame(width: 300, height: 285, alignment: .top)
    }
}

// MARK: - Page 2: sources

/// Ten fictional publication badges around a blue news icon (`NewsSources`).
private struct SpectrumNewsCarouselNewsSources: View {
    let t: Double

    private struct Badge {
        let name: String
        let color: UInt32
        let x: CGFloat
        let y: CGFloat
        let size: CGFloat
    }

    /// `BADGES`.
    private static let badges: [Badge] = [
        Badge(name: "DAILY", color: 0x2163D2, x: 50, y: 78, size: 30),
        Badge(name: "N", color: 0x18171C, x: 129, y: 54, size: 33),
        Badge(name: "POST", color: 0x8B1B40, x: 209, y: 69, size: 29),
        Badge(name: "S", color: 0xDB5839, x: 28, y: 113, size: 22),
        Badge(name: "LOOP", color: 0x143D36, x: 242, y: 114, size: 29),
        Badge(name: "F", color: 0x1E2228, x: 69, y: 129, size: 26),
        Badge(name: "ECHO", color: 0xE2B589, x: 186, y: 105, size: 32),
        Badge(name: "R", color: 0x5B92B7, x: 99, y: 87, size: 22),
        Badge(name: "M", color: 0x3D725B, x: 170, y: 43, size: 23),
        Badge(name: "O", color: 0xAA629F, x: 235, y: 63, size: 21),
    ]

    var body: some View {
        ZStack(alignment: .topLeading) {
            Circle()
                .fill(P.rgba(0xEAF2F84D))
                .frame(width: 230, height: 230)
                .offset(x: 35, y: 26)
            Circle()
                .fill(P.rgba(0xC4E6F15E))
                .frame(width: 175, height: 175)
                .offset(x: 62.5, y: 53.5)
            ForEach(0..<10, id: \.self) { index in
                badge(index)
            }
            newsIcon
        }
        .frame(width: 300, height: 285, alignment: .topLeading)
    }

    /// `Badge`: it drifts on a small Lissajous loop that closes once per period.
    private func badge(_ index: Int) -> some View {
        let badge = Self.badges[index]
        let turn = t / SpectrumNewsCarouselMotion.loop * Double.pi * 2
        let driftX = CGFloat(sin(turn * 2 + Double(index)) * 3)
        let driftY = CGFloat(cos(turn * 3 + Double(index)) * 4)
        let textColor: UInt32 = index == 6 ? 0x7D4930 : 0xFFFFFF
        return ZStack {
            Circle().fill(P.rgb(badge.color))
            artText(
                badge.name,
                badge.name.count > 1 ? 6 : 14,
                textColor,
                weight: .heavy,
                alignment: .center
            )
        }
        .frame(width: badge.size, height: badge.size)
        // `boxShadow: '0 3px 5px #55556630'`.
        .shadow(color: P.rgba(0x55556630), radius: 2.5, x: 0, y: 3)
        .offset(x: badge.x + driftX, y: badge.y - 34 + driftY)
    }

    /// The blue disc with the news glyph and its two shadows.
    private var newsIcon: some View {
        Circle()
            .fill(artGradient([0x24BDD8, 0x137BCB, 0x1962B4]))
            .frame(width: 102, height: 102)
            .overlay {
                SpectrumNewsCarouselNewsGlyph()
                    .stroke(P.rgb(0xF2FDFF), style: StrokeStyle(lineWidth: 2, lineCap: .butt, lineJoin: .miter))
                    .frame(width: 40, height: 40)
            }
            .spectrumBoxShadows(
                Circle(),
                underlay: P.rgb(0x137BCB),
                SpectrumNewsCarouselShadow(color: P.rgba(0x287A9E55), blur: 14, y: 9),
                SpectrumNewsCarouselShadow(color: P.rgba(0x1B659950), blur: 3, y: 2)
            )
            .offset(x: 99, y: 79)
    }
}

// MARK: - Page 3: topics

/// A person above red topics to see less of and green ones to see more of (`TopicSelection`).
private struct SpectrumNewsCarouselTopicSelection: View {
    let scale: CGFloat

    var body: some View {
        ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                Circle()
                    .fill(P.rgb(0x8C8C8C))
                    .frame(width: 34, height: 34)
                    .offset(x: 21, y: 5)
                SpectrumNewsCarouselShoulders()
                    .fill(P.rgb(0x969696))
                    .frame(width: 76, height: 91)
            }
            .frame(width: 76, height: 91, alignment: .topLeading)
            .offset(x: 112, y: 56)
            columns
                .offset(x: 22, y: 160)
        }
        .frame(width: 300, height: 285, alignment: .topLeading)
        // `translateY: -26 / scale`, in the unscaled stage, so it is 26 points on screen.
        .offset(y: -26 / scale)
    }

    /// `topicColumns`: the topics to see less of, a divider, and the topics to see more of.
    private var columns: some View {
        HStack(alignment: .top, spacing: 10) {
            VStack(alignment: .trailing, spacing: 9) {
                topic("Less noise −", width: 107, background: 0xB52C48, text: 0xFFF8F6)
                topic("Celebrity −", width: 94, background: 0xCF4057, text: 0xFFF8F6)
                topic("Hot takes −", width: 81, background: 0xF0C7CE, text: 0x9E5E68)
            }
            .frame(maxWidth: .infinity, alignment: .trailing)
            Rectangle()
                .fill(P.rgb(0xD4D2CF))
                .frame(width: 2, height: 94)
            VStack(alignment: .leading, spacing: 9) {
                topic("+ Design", width: 103, background: 0x7EBC52, text: 0x3B6C2B)
                topic("+ Our planet", width: 92, background: 0x96CB6F, text: 0x3B6C2B)
                topic("+ Technology", width: 81, background: 0xAFD694, text: 0x3B6C2B)
                topic("+ Culture", width: 70, background: 0xD0E7BA, text: 0x3B6C2B)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: 256, alignment: .topLeading)
    }

    /// `topic`: a 17-point pill.
    private func topic(_ label: String, width: CGFloat, background: UInt32, text: UInt32) -> some View {
        artText(label, 8, text, weight: .semibold)
            .padding(.horizontal, 8)
            .frame(width: width, height: 17, alignment: .leading)
            .background(Capsule().fill(P.rgb(background)))
    }
}

// MARK: - Page 4: summary

/// A blue summary panel above an article, a count pill and an audio chip (`StorySummary`).
private struct SpectrumNewsCarouselStorySummary: View {
    let t: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            articleBack
                .offset(x: 49, y: 90)
            summaryCard
                .offset(x: 108, y: 61)
            countPill
                .offset(x: 19, y: 196)
            audioChip
                .offset(x: 185, y: 225)
        }
        .frame(width: 300, height: 285, alignment: .topLeading)
    }

    /// The white article behind the panel, turned 6 degrees (`articleBack`).
    private var articleBack: some View {
        let shape = RoundedRectangle(cornerRadius: 10, style: .circular)
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                artText("THE DAILY FIELD", 6, 0x8D8B85, weight: .bold, kerning: 0.9)
                artText("A greener way\nthrough the city", 13, 0x807F79, weight: .bold, lineHeight: 17)
                    .padding(.vertical, 8)
                ForEach(0..<5, id: \.self) { index in
                    RoundedRectangle(cornerRadius: 1.5, style: .circular)
                        .fill(P.rgb(0xD9D7D0))
                        .frame(width: index == 4 ? 85 : 134, height: 3)
                        .padding(.top, 5)
                }
            }
            // The 1 pt border and the 17 pt padding put the content at (18, 18).
            .frame(width: 140, alignment: .topLeading)
            .offset(x: 18, y: 18)
            ZStack {
                Circle().fill(P.rgb(0x354145))
                artText("F", 11, 0xFFFFFF, weight: .bold, alignment: .center)
            }
            .frame(width: 22, height: 22)
            .offset(x: 18, y: 129)
        }
        .frame(width: 176, height: 166, alignment: .topLeading)
        .background(shape.fill(P.rgb(0xFDFCFA)))
        .overlay(shape.strokeBorder(P.rgb(0xE7E5E0), lineWidth: 1))
        .spectrumBoxShadows(
            shape,
            underlay: P.rgb(0xFDFCFA),
            SpectrumNewsCarouselShadow(color: P.rgba(0x3C3F4A20), blur: 16, y: 7)
        )
        .rotationEffect(.degrees(-6))
    }

    /// The blue panel with its label and four summary lines (`summaryCard`).
    private var summaryCard: some View {
        let shape = RoundedRectangle(cornerRadius: 17, style: .circular)
        let widths: [CGFloat] = [125, 104, 116, 87]
        return VStack(alignment: .leading, spacing: 0) {
            artText("THE SHORT VERSION", 7, 0xCCF4FE, weight: .bold, kerning: 0.8)
                .padding(.bottom, 13)
            ForEach(0..<4, id: \.self) { index in
                HStack(spacing: 7) {
                    Circle()
                        .fill(P.rgb(0xC5F2FF))
                        .frame(width: 5, height: 5)
                    RoundedRectangle(cornerRadius: 3, style: .circular)
                        .fill(P.rgb(0xB7EBFA))
                        .frame(width: widths[index], height: 5)
                }
                .padding(.bottom, 11)
            }
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 18)
        .frame(width: 172, height: 128, alignment: .topLeading)
        .background(shape.fill(artGradient([0x28BADB, 0x168ACB, 0x1B68B9])))
        .spectrumBoxShadows(
            shape,
            underlay: P.rgb(0x168ACB),
            SpectrumNewsCarouselShadow(color: P.rgba(0x1E75943F), blur: 18, y: 12),
            SpectrumNewsCarouselShadow(color: P.rgba(0x2F77962F), blur: 3, y: 2)
        )
    }

    /// `countPill`: "16 perspectives" with two tiny source badges hanging off its corner.
    private var countPill: some View {
        artText("16 perspectives", 10, 0xF5FEFF, weight: .semibold)
            .padding(.horizontal, 12)
            .frame(height: 28)
            .background(Capsule().fill(P.rgb(0x26A8D1)))
            .spectrumBoxShadows(
                Capsule(),
                underlay: P.rgb(0x26A8D1),
                SpectrumNewsCarouselShadow(color: P.rgba(0x237B9D40), blur: 8, y: 4)
            )
            .overlay(alignment: .topTrailing) {
                // `tinySources`: `right: -15, top: 18`.
                artText("N  F", 6, 0xD3E1EB)
                    .padding(.horizontal, 5)
                    .padding(.vertical, 2)
                    .background(RoundedRectangle(cornerRadius: 8, style: .circular).fill(P.rgb(0x4D6E80)))
                    .offset(x: 15, y: 18)
            }
    }

    /// `audioChip`: a small player with a moving wave and a play arrow.
    private var audioChip: some View {
        HStack(spacing: 10) {
            SpectrumNewsCarouselAudioWave(t: t)
            SpectrumNewsCarouselPlayGlyph()
                .fill(P.rgb(0xEAFBFF))
                .frame(width: 13, height: 16)
        }
        .frame(width: 78, height: 32)
        .background(Capsule().fill(artGradient([0x30C2DD, 0x1988C7])))
        .spectrumBoxShadows(
            Capsule(),
            underlay: P.rgb(0x1988C7),
            SpectrumNewsCarouselShadow(color: P.rgba(0x21749340), blur: 8, y: 5)
        )
    }
}

/// `AudioWave`: five bars that breathe in opacity and height on whole harmonics of the loop.
private struct SpectrumNewsCarouselAudioWave: View {
    let t: Double

    var body: some View {
        let turn = t / SpectrumNewsCarouselMotion.loop * Double.pi * 2
        let heights: [CGFloat] = [7, 13, 18, 11, 6]
        HStack(alignment: .center, spacing: 2) {
            ForEach(0..<5, id: \.self) { index in
                RoundedRectangle(cornerRadius: 1, style: .circular)
                    .fill(P.rgb(0xE4F8FF))
                    .frame(width: 2, height: heights[index])
            }
        }
        .scaleEffect(x: 1, y: CGFloat(0.8 + sin(turn * 11) * 0.2))
        .opacity(0.7 + sin(turn * 9) * 0.25)
    }
}

// MARK: - Page 5: questions

/// A photograph beside a question card and its short answer (`StoryQuestions`).
private struct SpectrumNewsCarouselStoryQuestions: View {
    var body: some View {
        ZStack(alignment: .topLeading) {
            askPill
                .offset(x: 73, y: 63)
            photoCard
                .offset(x: 20, y: 105)
            questionCard
                .offset(x: 118, y: 133)
        }
        .frame(width: 300, height: 285, alignment: .topLeading)
    }

    /// `askPill`: a search field.
    private var askPill: some View {
        HStack(spacing: 7) {
            artText("⌕", 12, 0x389CB1)
            artText("A little more context", 9, 0xA6A5A0)
        }
        .padding(.horizontal, 12)
        .frame(width: 167, height: 29, alignment: .leading)
        .background(Capsule().fill(P.rgb(0xFFFEFC)))
        .spectrumBoxShadows(
            Capsule(),
            underlay: P.rgb(0xFFFEFC),
            SpectrumNewsCarouselShadow(color: P.rgba(0x625A4D18), blur: 8, y: 2)
        )
    }

    /// `photoCard`: the waterfront garden with its caption, turned 4 degrees.
    private var photoCard: some View {
        let shape = RoundedRectangle(cornerRadius: 9, style: .circular)
        return VStack(alignment: .leading, spacing: 0) {
            Image(uiImage: SpectrumNewsCarouselPhoto.image)
                .resizable()
                .scaledToFill()
                .frame(width: 198, height: 119)
                .clipped()
            artText("ROOM FOR THE RIVER", 6, 0x5C675D, weight: .bold, kerning: 1.1)
                .padding(.leading, 10)
                .padding(.top, 8)
        }
        .frame(width: 198, height: 142, alignment: .topLeading)
        .background(P.rgb(0xE5E8DB))
        .clipShape(shape)
        .spectrumBoxShadows(
            shape,
            underlay: P.rgb(0xE5E8DB),
            SpectrumNewsCarouselShadow(color: P.rgba(0x3B362D28), blur: 14, y: 8)
        )
        .rotationEffect(.degrees(-4))
    }

    /// `questionCard`: a dark question over a short answer.
    private var questionCard: some View {
        let shape = RoundedRectangle(cornerRadius: 12, style: .circular)
        return VStack(spacing: 0) {
            artText("What changes when a city makes room for water?", 10, 0xF8F7F4, weight: .semibold, lineHeight: 13)
                .padding(.horizontal, 12)
                .padding(.vertical, 11)
                .frame(maxWidth: .infinity, alignment: .leading)
                .background(P.rgb(0x29292D))
            VStack(alignment: .leading, spacing: 0) {
                artText(
                    "More public space.\nA cooler walk home.\nA river people can reach.",
                    9,
                    0x7E7A73,
                    lineHeight: 14
                )
                artText("3 source perspectives ↗", 6, 0xA7A19A)
                    .padding(.top, 8)
            }
            .padding(12)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
        .frame(width: 169)
        .background(P.rgb(0xFFFDFC))
        .clipShape(shape)
        .spectrumBoxShadows(
            shape,
            underlay: P.rgb(0xFFFDFC),
            SpectrumNewsCarouselShadow(color: P.rgba(0x3A312C28), blur: 18, y: 8),
            SpectrumNewsCarouselShadow(color: P.rgba(0x3A312C15), blur: 2, y: 1)
        )
    }
}
