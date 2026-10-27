import SwiftUI
import UIKit

private typealias M = NocturneProgramCarouselMotion
private typealias P = NocturneProgramCarouselPalette

// MARK: - Bundled pictures and SVG helpers

/// The two WebP pictures of the cards, decoded once before the first frame that needs them.
enum NocturneProgramCarouselAssets {
    /// `evening-guide.webp`: the guide portrait of the first card.
    static let guide = load("NocturneProgramCarousel-evening-guide")
    /// `body-hologram.webp`: the translucent figure of the third card.
    static let hologram = load("NocturneProgramCarousel-body-hologram")

    private static func load(_ name: String) -> UIImage {
        guard
            let url = Bundle.main.url(forResource: name, withExtension: "webp"),
            let source = UIImage(contentsOfFile: url.path)
        else {
            assertionFailure("Missing Nocturne Program Carousel artwork \(name)")
            return UIImage()
        }
        return source.preparingForDisplay() ?? source
    }
}

/// The one SVG feature `Path` has no call for: an elliptical arc given by its end points.
enum NocturneProgramCarouselSVG {
    /// The points of an SVG arc `A r r 0 large sweep` from `start` to `end` (implementation notes
    /// F.6.5 of the SVG specification, for a circle), including both end points. Angles run in the
    /// y-down plane, so a sweep of 1 is clockwise on the screen.
    static func arc(
        from start: CGPoint,
        to end: CGPoint,
        radius: CGFloat,
        large: Bool,
        sweep: Bool,
        steps: Int
    ) -> [CGPoint] {
        let halfX = (start.x - end.x) / 2
        let halfY = (start.y - end.y) / 2
        let halfChordSquared = halfX * halfX + halfY * halfY
        var r = radius
        var factor = (r * r - halfChordSquared) / halfChordSquared
        if factor < 0 {
            // Radii too small are scaled up to the half chord: a half circle.
            factor = 0
            r = halfChordSquared.squareRoot()
        }
        let sign: CGFloat = large == sweep ? -1 : 1
        let scale = sign * factor.squareRoot()
        let centerX = scale * halfY + (start.x + end.x) / 2
        let centerY = -scale * halfX + (start.y + end.y) / 2
        let startAngle = atan2(start.y - centerY, start.x - centerX)
        var delta = atan2(end.y - centerY, end.x - centerX) - startAngle
        if sweep && delta < 0 {
            delta += 2 * CGFloat.pi
        }
        if !sweep && delta > 0 {
            delta -= 2 * CGFloat.pi
        }
        return (0...steps).map { step in
            let angle = startAngle + delta * CGFloat(step) / CGFloat(steps)
            return CGPoint(x: centerX + r * cos(angle), y: centerY + r * sin(angle))
        }
    }

    /// Appends the arc to `path` as short lines (the current point is the arc's start).
    static func addArc(
        to path: inout Path,
        from start: CGPoint,
        to end: CGPoint,
        radius: CGFloat,
        large: Bool,
        sweep: Bool
    ) {
        let points = arc(from: start, to: end, radius: radius, large: large, sweep: sweep, steps: 32)
        for point in points.dropFirst() {
            path.addLine(to: point)
        }
    }
}

// MARK: - The mark

/// `NocturneMark`: a teal crescent with a blue four-point spark, drawn on a 40 x 40 grid.
struct NocturneProgramCarouselMark: View {
    var size: CGFloat = 34

    /// `M23 5C13 5 7 12 7 20s6 15 16 15c4 0 7-1 10-4-12 3-20-7-15-17 1-3 3-6 5-9Z`.
    private static let crescent: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 23, y: 5))
        path.addCurve(to: CGPoint(x: 7, y: 20), control1: CGPoint(x: 13, y: 5), control2: CGPoint(x: 7, y: 12))
        path.addCurve(to: CGPoint(x: 23, y: 35), control1: CGPoint(x: 7, y: 28), control2: CGPoint(x: 13, y: 35))
        path.addCurve(to: CGPoint(x: 33, y: 31), control1: CGPoint(x: 27, y: 35), control2: CGPoint(x: 30, y: 34))
        path.addCurve(to: CGPoint(x: 18, y: 14), control1: CGPoint(x: 21, y: 34), control2: CGPoint(x: 13, y: 24))
        path.addCurve(to: CGPoint(x: 23, y: 5), control1: CGPoint(x: 19, y: 11), control2: CGPoint(x: 21, y: 8))
        path.closeSubpath()
        return path
    }()

    /// `m27 11 2 5 5 2-5 2-2 5-2-5-5-2 5-2Z`.
    private static let spark: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 27, y: 11))
        path.addLine(to: CGPoint(x: 29, y: 16))
        path.addLine(to: CGPoint(x: 34, y: 18))
        path.addLine(to: CGPoint(x: 29, y: 20))
        path.addLine(to: CGPoint(x: 27, y: 25))
        path.addLine(to: CGPoint(x: 25, y: 20))
        path.addLine(to: CGPoint(x: 20, y: 18))
        path.addLine(to: CGPoint(x: 25, y: 16))
        path.closeSubpath()
        return path
    }()

    var body: some View {
        Canvas { context, canvasSize in
            var ctx = context
            let scale = canvasSize.width / 40
            ctx.scaleBy(x: scale, y: scale)
            ctx.fill(Self.crescent, with: .color(P.moonTeal))
            ctx.fill(Self.spark, with: .color(P.sparkBlue))
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }
}

// MARK: - Card 1: the guide portrait

/// `GuidePortrait`: three numbered plan strips come in from the left, the guide rises in front of
/// them, a bookmark drops from the top edge, and the bottom fades into the card.
struct NocturneProgramCarouselGuidePortrait: View {
    let t: Double
    let reduced: Bool
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        // The guide: opacity 2.25 to 2.9 s, rise 2.25 to 3.1 s.
        let portraitOpacity = reduced ? 1 : M.enter(M.segment(t, 2.25, 2.9))
        let portraitLift = reduced ? 0 : 18 * (1 - M.enter(M.segment(t, 2.25, 3.1)))
        // The bookmark: 3.8 to 4.1 s, linear.
        let bookmark = reduced ? 1 : M.segment(t, 3.8, 4.1)
        let bookmarkLift = reduced ? 0 : -8 * (1 - bookmark)
        Color.clear
            .frame(width: width, height: height)
            .overlay(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    ForEach(0..<3, id: \.self) { index in
                        NocturneProgramCarouselPlanStrip(index: index, t: t, reduced: reduced)
                            .padding(.leading, 8)
                            .padding(.top, 42 + CGFloat(index) * 47)
                    }
                }
            }
            .overlay(alignment: .topTrailing) {
                // `right: -10, top: 8`, `height + 26` tall, `height * 0.86` wide, `contentFit="contain"`.
                Image(uiImage: NocturneProgramCarouselAssets.guide)
                    .resizable()
                    .aspectRatio(contentMode: .fit)
                    .frame(width: height * 0.86, height: height + 26)
                    .offset(x: 10, y: 8)
                    .opacity(portraitOpacity)
                    .offset(y: CGFloat(portraitLift))
            }
            .overlay(alignment: .bottom) {
                // `bottom: -1`, `height * 0.32` tall.
                LinearGradient(
                    stops: [
                        Gradient.Stop(color: P.rgb(0x10275A, opacity: 0), location: 0),
                        Gradient.Stop(color: P.rgb(0x10234C), location: 0.78),
                        Gradient.Stop(color: P.rgb(0x0F2043), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: height * 0.32)
                .offset(y: 1)
                .allowsHitTesting(false)
            }
            .overlay(alignment: .topTrailing) {
                NocturneProgramCarouselBookmark()
                    .padding(.trailing, 10)
                    .opacity(bookmark)
                    .offset(y: CGFloat(bookmarkLift))
            }
            .clipped()
    }
}

/// One numbered strip: 220 pt wide and 13 pt narrower each, fading out down the list.
private struct NocturneProgramCarouselPlanStrip: View {
    let index: Int
    let t: Double
    let reduced: Bool

    private static let names = ["Unwind gently", "Make room for rest", "Find your rhythm"]
    private static let weights: [Double] = [1, 0.42, 0.1]

    var body: some View {
        let start = 2.82 + Double(index) * 0.1
        let p = reduced ? 1 : M.enter(M.segment(t, start, start + 0.43))
        HStack(spacing: 10) {
            nocturneProgramCarouselArtText("0\(index + 1)", size: 11, weight: .bold, color: P.rgb(0xA5C4FF))
            nocturneProgramCarouselArtText(Self.names[index], size: 11, color: P.rgb(0xBDD0F2))
        }
        .padding(.horizontal, 10)
        // `borderTopWidth: 1` takes a point off the box the row is centred in.
        .padding(.top, 1)
        .frame(width: 220 - CGFloat(index) * 13, height: 35, alignment: .leading)
        .background {
            RoundedRectangle(cornerRadius: 7).fill(P.rgba(0x2F62C68C))
        }
        .overlay {
            NocturneProgramCarouselTopEdge(radius: 7, color: P.rgba(0x5C89D94D))
        }
        .opacity(p * Self.weights[index])
        .offset(x: CGFloat(-18 * (1 - p)))
    }
}

/// A one-point border along the top edge of a rounded box and round its upper corners (the
/// `borderTopWidth: 1` of a box with a radius): the box minus the box moved down by 1 pt.
private struct NocturneProgramCarouselTopEdge: View {
    let radius: CGFloat
    let color: Color

    var body: some View {
        Canvas { context, size in
            var ctx = context
            let box = CGRect(origin: .zero, size: size)
            ctx.clip(to: Path(roundedRect: box, cornerRadius: radius))
            var path = Path()
            path.addRect(box.insetBy(dx: -20, dy: -20))
            path.addRoundedRect(
                in: box.offsetBy(dx: 0, dy: 1),
                cornerSize: CGSize(width: radius, height: radius)
            )
            ctx.fill(path, with: .color(color), style: FillStyle(eoFill: true))
        }
        .allowsHitTesting(false)
    }
}

/// The 22 x 32 bookmark: a cream ribbon with a darker star.
private struct NocturneProgramCarouselBookmark: View {
    /// `M2 0h18v30l-9-6-9 6Z`.
    private static let ribbon: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 2, y: 0))
        path.addLine(to: CGPoint(x: 20, y: 0))
        path.addLine(to: CGPoint(x: 20, y: 30))
        path.addLine(to: CGPoint(x: 11, y: 24))
        path.addLine(to: CGPoint(x: 2, y: 30))
        path.closeSubpath()
        return path
    }()

    /// `m11 7 1.4 3 3.6.4-2.6 2.5.6 3.5-3-1.6L8 16.4l.6-3.5L6 10.4l3.6-.4Z`.
    private static let star: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 11, y: 7))
        path.addLine(to: CGPoint(x: 12.4, y: 10))
        path.addLine(to: CGPoint(x: 16, y: 10.4))
        path.addLine(to: CGPoint(x: 13.4, y: 12.9))
        path.addLine(to: CGPoint(x: 14, y: 16.4))
        path.addLine(to: CGPoint(x: 11, y: 14.8))
        path.addLine(to: CGPoint(x: 8, y: 16.4))
        path.addLine(to: CGPoint(x: 8.6, y: 12.9))
        path.addLine(to: CGPoint(x: 6, y: 10.4))
        path.addLine(to: CGPoint(x: 9.6, y: 10))
        path.closeSubpath()
        return path
    }()

    var body: some View {
        Canvas { context, _ in
            context.fill(Self.ribbon, with: .color(P.rgb(0xEAD899)))
            context.fill(Self.star, with: .color(P.rgb(0xAC8C46)))
        }
        .frame(width: 22, height: 32)
    }
}

// MARK: - Card 2: the miniature phone

/// `MiniDashboard`: a phone with an evening check-in rises into the card; the routine score rolls up
/// to 78 while its ring fills, and the dial then swells a little.
struct NocturneProgramCarouselMiniDashboard: View {
    let t: Double
    let reduced: Bool
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        // `revealStyle`: opacity 5.1 to 5.65 s, rise 5.1 to 5.8 s.
        let reveal = reduced ? 1 : M.segment(t, 5.1, 5.65)
        let lift = reduced ? 0 : 16 * (1 - M.enter(M.segment(t, 5.1, 5.8)))
        Color.clear
            .frame(width: width, height: height)
            .overlay(alignment: .top) {
                // `top: 12`, `width: 204`, `height + 30` tall (the lower part is cut by the card).
                NocturneProgramCarouselPhone(t: t, reduced: reduced, height: height + 30)
                    .offset(y: 12 + CGFloat(lift))
                    .opacity(reveal)
            }
            .overlay(alignment: .bottom) {
                LinearGradient(
                    colors: [P.rgb(0x0F2043, opacity: 0), P.rgb(0x0F2043)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: 45)
                .offset(y: 1)
                .allowsHitTesting(false)
            }
            .clipped()
    }
}

/// The phone: a dark rounded body with a 3 pt rim, its speaker slot and the check-in.
private struct NocturneProgramCarouselPhone: View {
    let t: Double
    let reduced: Bool
    let height: CGFloat

    var body: some View {
        Color.clear
            .frame(width: 204, height: height)
            .background {
                ZStack {
                    // `boxShadow: 0 6px 12px #02091580`: a 6 pt Gaussian, 6 pt lower.
                    RoundedRectangle(cornerRadius: 25)
                        .fill(P.rgba(0x02091580))
                        .blur(radius: 6)
                        .offset(y: 6)
                    RoundedRectangle(cornerRadius: 25).fill(P.rgb(0x071421))
                }
            }
            .overlay(alignment: .topLeading) {
                NocturneProgramCarouselPhoneContent(t: t, reduced: reduced)
            }
            .overlay(alignment: .topLeading) {
                // `top: 5, left: 78` inside the 3 pt border.
                RoundedRectangle(cornerRadius: 3)
                    .fill(P.rgb(0x020A13))
                    .frame(width: 43, height: 4)
                    .padding(.leading, 81)
                    .padding(.top, 8)
            }
            .overlay {
                RoundedRectangle(cornerRadius: 25).strokeBorder(P.rgb(0x344357), lineWidth: 3)
            }
    }
}

/// The check-in on the phone: a status row, the title and date, a week of days, the score dial with
/// two figures, a bar chart and the sample label. `paddingHorizontal: 13, paddingTop: 16` inside the
/// 3 pt border.
private struct NocturneProgramCarouselPhoneContent: View {
    let t: Double
    let reduced: Bool

    private static let days = ["M", "T", "W", "T", "F", "S", "S"]
    private static let bars: [CGFloat] = [18, 31, 24, 42, 36, 53, 48]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                nocturneProgramCarouselArtText("9:41", size: 7, weight: .bold, color: P.rgb(0xAFC2D4))
                Spacer(minLength: 0)
                nocturneProgramCarouselArtText("••• ▰", size: 7, weight: .bold, color: P.rgb(0xAFC2D4))
            }
            .padding(.bottom, 12)
            nocturneProgramCarouselArtText("Your evening", size: 14, weight: .bold, color: P.rgb(0xE2EDFA))
            nocturneProgramCarouselArtText("MONDAY, OCT 26", size: 7, color: P.rgb(0x7492AB), kerning: 1)
                .padding(.top, 5)
            week
                .padding(.top, 12)
            scoreRow
                .padding(.top, 9)
            nocturneProgramCarouselArtText("A little more consistent", size: 8, color: P.rgb(0x86A0B3))
                .padding(.top, 12)
            chart
                .padding(.top, 8)
            nocturneProgramCarouselArtText(
                "SAMPLE CHECK-IN · NOT A HEALTH SCORE", size: 5, color: P.rgb(0x567486), kerning: 0.25
            )
            .padding(.top, 10)
        }
        .padding(.horizontal, 16)
        .padding(.top, 19)
        .frame(width: 204, alignment: .topLeading)
    }

    private var week: some View {
        HStack(spacing: 6) {
            ForEach(0..<7, id: \.self) { index in
                nocturneProgramCarouselArtText(Self.days[index], size: 7, color: P.rgb(0x85A3BA))
                    .frame(width: 17, height: 17)
                    .overlay {
                        Circle().strokeBorder(index == 0 ? P.rgb(0x64E0CE) : P.rgb(0x27435C), lineWidth: 1)
                    }
            }
        }
    }

    private var scoreRow: some View {
        // `emphasis`: the dial swells by 13% from 7.25 to 8 s.
        let swell = reduced ? 1.13 : 1 + 0.13 * M.enter(M.segment(t, 7.25, 8))
        return HStack(spacing: 6) {
            NocturneProgramCarouselScoreDial(t: t, reduced: reduced)
                .scaleEffect(CGFloat(swell))
            VStack(alignment: .leading, spacing: 12) {
                metric("Wind-down", "22 min")
                metric("This week", "5 evenings")
            }
        }
    }

    private func metric(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            nocturneProgramCarouselArtText(label, size: 8, color: P.rgb(0x7897AA))
            nocturneProgramCarouselArtText(value, size: 11, weight: .semibold, color: P.rgb(0xD0E2EA))
                .padding(.top, 3)
        }
    }

    private var chart: some View {
        VStack(spacing: 0) {
            HStack(alignment: .bottom, spacing: 9) {
                ForEach(0..<7, id: \.self) { index in
                    UnevenRoundedRectangle(topLeadingRadius: 3, topTrailingRadius: 3)
                        .fill(index == 6 ? P.rgb(0x6FE8D6) : P.rgb(0x28617B))
                        .frame(width: 13, height: Self.bars[index])
                }
            }
            .padding(.horizontal, 3)
            .frame(maxWidth: .infinity, minHeight: 59, maxHeight: 59, alignment: .bottomLeading)
            Rectangle().fill(P.rgb(0x1D374B)).frame(height: 1)
        }
    }
}

/// The 90 x 90 score dial: a ring that fills clockwise from the top (blue at the top to teal at the
/// bottom), two rolling digits, and the ROUTINE caption.
private struct NocturneProgramCarouselScoreDial: View {
    let t: Double
    let reduced: Bool

    private static let ringTimes: [Double] = [5.25, 5.75, 6, 6.25, 6.5, 6.9, 7.4]
    private static let ringValues: [Double] = [0, 0.04, 0.2, 0.44, 0.64, 0.78, 0.78]

    var body: some View {
        let progress = reduced ? 0.78 : M.interpolate(t, Self.ringTimes, Self.ringValues)
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                var track = Path()
                track.addEllipse(in: CGRect(x: 9, y: 9, width: 72, height: 72))
                context.stroke(track, with: .color(P.rgb(0x173044)), lineWidth: 4)
                guard progress > 0.001 else { return }
                // The arc starts at the top and runs clockwise: `rotation={-90}` of a circle that
                // starts at three o'clock.
                var arc = Path()
                let steps = max(2, Int((progress * 120).rounded(.up)))
                for step in 0...steps {
                    let angle = -Double.pi / 2 + 2 * Double.pi * progress * Double(step) / Double(steps)
                    let point = CGPoint(x: 45 + 36 * cos(angle), y: 45 + 36 * sin(angle))
                    if step == 0 {
                        arc.move(to: point)
                    } else {
                        arc.addLine(to: point)
                    }
                }
                // `x1=1 x2=0` of the gradient, turned with the circle: blue at the top, teal at the bottom.
                let shading = GraphicsContext.Shading.linearGradient(
                    Gradient(colors: [P.rgb(0x397AFF), P.rgb(0x6AD9DE)]),
                    startPoint: CGPoint(x: 45, y: 9),
                    endPoint: CGPoint(x: 45, y: 81)
                )
                context.stroke(
                    arc,
                    with: shading,
                    style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round)
                )
            }
            .frame(width: 90, height: 90)
            HStack(spacing: 0) {
                NocturneProgramCarouselScoreDigit(t: t, reduced: reduced, tens: true)
                NocturneProgramCarouselScoreDigit(t: t, reduced: reduced, tens: false)
            }
            .frame(height: 32)
            .padding(.leading, 23)
            .padding(.top, 24)
            nocturneProgramCarouselArtText("ROUTINE", size: 6, color: P.rgb(0x6F96A8), kerning: 1)
                .multilineTextAlignment(.center)
                .frame(width: 52)
                .padding(.leading, 19)
                .padding(.top, 59)
        }
        .frame(width: 90, height: 90, alignment: .topLeading)
    }
}

/// One rolling digit: a clipped 22 x 32 column of the figures 0 to 9 that slides up.
private struct NocturneProgramCarouselScoreDigit: View {
    let t: Double
    let reduced: Bool
    let tens: Bool

    private static let tensTimes: [Double] = [5.25, 6.25, 6.5, 6.583, 6.75, 7, 7.35]
    private static let tensValues: [Double] = [0, 0, 1.4, 2.4, 4.4, 6.2, 7]
    private static let onesTimes: [Double] = [5.25, 5.75, 6, 6.25, 6.5, 6.9, 7.1]
    private static let onesValues: [Double] = [0, 0.6, 2.2, 5.2, 7, 7.85, 8]

    var body: some View {
        let value: Double
        if reduced {
            value = tens ? 7 : 8
        } else if tens {
            value = M.interpolate(t, Self.tensTimes, Self.tensValues)
        } else {
            value = M.interpolate(t, Self.onesTimes, Self.onesValues)
        }
        return VStack(spacing: 0) {
            ForEach(0..<10, id: \.self) { digit in
                nocturneProgramCarouselArtText("\(digit)", size: 27, weight: .bold, color: P.rgb(0xDCECF7))
                    .monospacedDigit()
                    .frame(width: 22, height: 32)
            }
        }
        .offset(y: CGFloat(-value * 32))
        .frame(width: 22, height: 32, alignment: .top)
        .clipped()
    }
}

// MARK: - Card 3: the body scan

/// `BodyScan`: a translucent hologram of a body over a blueprint grid, framed by corner brackets,
/// with a scan line that travels down and back up before three points of attention light up.
struct NocturneProgramCarouselBodyScan: View {
    let t: Double
    let reduced: Bool
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        // The figure: opacity 10.2 to 10.8 s, settles from 1.04 over 10.2 to 11.2 s.
        let figureOpacity = reduced ? 1 : M.segment(t, 10.2, 10.8)
        let figureScale = reduced ? 1 : 1.04 - 0.04 * M.enter(M.segment(t, 10.2, 11.2))
        // The brackets: opacity 10.9 to 11.3 s, settle from 1.06 over 10.9 to 11.4 s.
        let bracketOpacity = reduced ? 1 : M.segment(t, 10.9, 11.3)
        let bracketScale = reduced ? 1 : 1.06 - 0.06 * M.enter(M.segment(t, 10.9, 11.4))
        P.rgb(0x0E2A66)
            .frame(width: width, height: height)
            .overlay {
                Color.clear
                    .overlay(alignment: .top) {
                        Image(uiImage: NocturneProgramCarouselAssets.hologram)
                            .resizable()
                            .scaledToFill()
                            .frame(width: height * 0.92, height: height * 1.38)
                            .clipped()
                    }
                    .scaleEffect(CGFloat(figureScale))
                    .opacity(figureOpacity)
            }
            .overlay {
                NocturneProgramCarouselGrid()
            }
            .overlay(alignment: .bottom) {
                LinearGradient(
                    colors: [P.rgb(0x0B2A70, opacity: 0), P.rgb(0x0A2560)],
                    startPoint: .top,
                    endPoint: .bottom
                )
                .frame(height: height * 0.3)
                .offset(y: 1)
                .allowsHitTesting(false)
            }
            .overlay(alignment: .topLeading) {
                // `left: 14, right: 14, top: 4, height: 60`.
                HStack(spacing: 0) {
                    NocturneProgramCarouselBracket(right: false)
                    Spacer(minLength: 0)
                    NocturneProgramCarouselBracket(right: true)
                }
                .frame(width: width - 28, height: 60, alignment: .top)
                .scaleEffect(CGFloat(bracketScale))
                .opacity(bracketOpacity)
                .offset(x: 14, y: 4)
                .allowsHitTesting(false)
            }
            .overlay(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    NocturneProgramCarouselHotspot(x: 0.5, y: 0.22, phase: 0, t: t, reduced: reduced, width: width, height: height)
                    NocturneProgramCarouselHotspot(x: 0.585, y: 0.56, phase: 1, t: t, reduced: reduced, width: width, height: height)
                    NocturneProgramCarouselHotspot(x: 0.48, y: 0.86, phase: 2, t: t, reduced: reduced, width: width, height: height)
                }
                .allowsHitTesting(false)
            }
            .overlay(alignment: .topLeading) {
                scan
            }
            .clipped()
            .accessibilityHidden(true)
    }

    /// The scan line: it goes down from 10.5 to 11.15 s and comes back up from 12.6 to 13.25 s,
    /// fading in and out at each end.
    private var scan: some View {
        let down = M.segment(t, 10.5, 11.15)
        let up = M.segment(t, 12.6, 13.25)
        let p = t < 12 ? down : 1 - up
        let fadeDown = M.segment(t, 10.45, 10.6) * (1 - M.segment(t, 11.1, 11.3))
        let fadeUp = M.segment(t, 12.55, 12.7) * (1 - M.segment(t, 13.15, 13.35))
        let visible = reduced ? 0 : min(1, fadeDown + fadeUp)
        let line = P.rgb(0x5FF3E0)
        return VStack(spacing: 0) {
            LinearGradient(
                colors: [P.rgb(0x5FF3E0, opacity: 0), line, P.rgb(0x5FF3E0, opacity: 0)],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(height: 2)
            LinearGradient(
                colors: [P.rgb(0x5FF3E0, opacity: 0.2), P.rgb(0x5FF3E0, opacity: 0)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(height: 26)
        }
        .frame(width: width - 16)
        .opacity(visible)
        .offset(x: 8, y: CGFloat(Double(height) * (0.06 + 0.62 * p)))
        .allowsHitTesting(false)
    }
}

/// The blueprint grid: 18 horizontal and 22 vertical hairlines every 18 pt, at 22% opacity as a group.
private struct NocturneProgramCarouselGrid: View {
    var body: some View {
        Canvas { context, size in
            var ctx = context
            ctx.opacity = 0.22
            ctx.drawLayer { layer in
                let color = P.rgb(0x5D8CE6)
                for index in 0..<18 {
                    var line = Path()
                    line.move(to: CGPoint(x: 0, y: CGFloat(index) * 18))
                    line.addLine(to: CGPoint(x: 400, y: CGFloat(index) * 18))
                    layer.stroke(line, with: .color(color), lineWidth: 0.5)
                }
                for index in 0..<22 {
                    var line = Path()
                    line.move(to: CGPoint(x: CGFloat(index) * 18, y: 0))
                    line.addLine(to: CGPoint(x: CGFloat(index) * 18, y: size.height))
                    layer.stroke(line, with: .color(color), lineWidth: 0.5)
                }
            }
        }
        .allowsHitTesting(false)
    }
}

/// One corner bracket: the top and one side of a 30 x 30 box with a 2.5 pt border and a 6 pt outer radius.
private struct NocturneProgramCarouselBracket: View {
    let right: Bool

    var body: some View {
        Canvas { context, _ in
            // The centre line of the border: 1.25 pt in, so a radius of 6 - 1.25 = 4.75.
            var path = Path()
            if right {
                path.move(to: CGPoint(x: 0, y: 1.25))
                path.addLine(to: CGPoint(x: 24, y: 1.25))
                for step in 1...16 {
                    let angle = 1.5 * Double.pi + 0.5 * Double.pi * Double(step) / 16
                    path.addLine(to: CGPoint(x: 24 + 4.75 * cos(angle), y: 6 + 4.75 * sin(angle)))
                }
                path.addLine(to: CGPoint(x: 28.75, y: 30))
            } else {
                path.move(to: CGPoint(x: 1.25, y: 30))
                path.addLine(to: CGPoint(x: 1.25, y: 6))
                for step in 1...16 {
                    let angle = Double.pi + 0.5 * Double.pi * Double(step) / 16
                    path.addLine(to: CGPoint(x: 6 + 4.75 * cos(angle), y: 6 + 4.75 * sin(angle)))
                }
                path.addLine(to: CGPoint(x: 30, y: 1.25))
            }
            context.stroke(
                path,
                with: .color(P.rgb(0x2F7BFF)),
                style: StrokeStyle(lineWidth: 2.5, lineCap: .butt, lineJoin: .round)
            )
        }
        .frame(width: 30, height: 30)
    }
}

/// A point of attention: a pink core with a glow and a 34 pt halo that pulses.
private struct NocturneProgramCarouselHotspot: View {
    let x: Double
    let y: Double
    let phase: Double
    let t: Double
    let reduced: Bool
    let width: CGFloat
    let height: CGFloat

    var body: some View {
        let shown = reduced ? 0.75 : M.segment(t, 11.6 + phase * 0.25, 12.2 + phase * 0.25)
        let pulse = reduced ? 0 : sin(t * Double.pi * 1.4 + phase * 2) * 0.5 + 0.5
        ZStack {
            Circle()
                .fill(P.rgb(0xFF4FA8))
                .opacity(0.22)
                .frame(width: 34, height: 34)
            // `boxShadow: 0 0 10px #FF4FA8`: a 5 pt Gaussian.
            Circle()
                .fill(P.rgb(0xFF4FA8))
                .frame(width: 9, height: 9)
                .blur(radius: 5)
            Circle()
                .fill(P.rgb(0xFF8FD0))
                .frame(width: 9, height: 9)
        }
        .frame(width: 34, height: 34)
        .opacity(shown * (0.7 + 0.3 * pulse))
        .scaleEffect(CGFloat(0.85 + 0.3 * pulse))
        .offset(x: width * CGFloat(x) - 17, y: height * CGFloat(y) - 17)
    }
}

// MARK: - The app's tab bar

/// The app's tab bar, as the reference shows it: Program is the current tab, Sleep the raised action.
struct NocturneProgramCarouselTabBar: View {
    private enum Tab: CaseIterable {
        case home
        case program
        case sleep
        case statistic
        case profile

        var title: String {
            switch self {
            case .home: return "Home"
            case .program: return "Program"
            case .sleep: return "Sleep"
            case .statistic: return "Statistic"
            case .profile: return "Profile"
            }
        }
    }

    var body: some View {
        HStack(alignment: .bottom, spacing: 0) {
            ForEach(Tab.allCases, id: \.self) { tab in
                if tab == .sleep {
                    sleepTab
                } else {
                    VStack(spacing: 5) {
                        NocturneProgramCarouselTabIcon(kind: iconKind(tab))
                        nocturneProgramCarouselArtText(
                            tab.title,
                            size: 10,
                            weight: .semibold,
                            color: tab == .program ? P.rgb(0xFFFFFF) : P.rgb(0x3A4866)
                        )
                    }
                    .frame(maxWidth: .infinity)
                }
            }
        }
        // `marginHorizontal: -14`, `paddingTop: 2`.
        .padding(.top, 2)
        .padding(.horizontal, -14)
        .accessibilityHidden(true)
    }

    private func iconKind(_ tab: Tab) -> NocturneProgramCarouselTabIcon.Kind {
        switch tab {
        case .home: return .home
        case .program: return .program
        case .sleep: return .program
        case .statistic: return .statistic
        case .profile: return .profile
        }
    }

    /// The raised action: a 66 x 48 blue pill, 16 pt above the row, with a moon.
    private var sleepTab: some View {
        VStack(spacing: 5) {
            ZStack {
                // `boxShadow: 0 8px 22px #2D57E880`: an 11 pt Gaussian, 8 pt lower.
                RoundedRectangle(cornerRadius: 24)
                    .fill(P.rgba(0x2D57E880))
                    .blur(radius: 11)
                    .offset(y: 8)
                RoundedRectangle(cornerRadius: 24)
                    .fill(
                        LinearGradient(
                            colors: [P.rgb(0x5B8DFF), P.rgb(0x2D57E8)],
                            startPoint: .topLeading,
                            endPoint: .bottomTrailing
                        )
                    )
                NocturneProgramCarouselSleepGlyph()
            }
            .frame(width: 66, height: 48)
            .padding(.top, -16)
            nocturneProgramCarouselArtText("Sleep", size: 10, weight: .semibold, color: P.rgb(0x334263))
        }
        .frame(maxWidth: .infinity)
    }
}

/// The 22 x 22 line icons of the tab bar, drawn on a 24 x 24 grid.
private struct NocturneProgramCarouselTabIcon: View {
    enum Kind {
        case home
        case program
        case statistic
        case profile
    }

    let kind: Kind

    private static let ink = P.ground

    /// `M4 11.5 12 5l8 6.5V20a1 1 0 0 1-1 1h-4.5v-5.5h-5V21H5a1 1 0 0 1-1-1Z`.
    private static let house: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 4, y: 11.5))
        path.addLine(to: CGPoint(x: 12, y: 5))
        path.addLine(to: CGPoint(x: 20, y: 11.5))
        path.addLine(to: CGPoint(x: 20, y: 20))
        NocturneProgramCarouselSVG.addArc(
            to: &path, from: CGPoint(x: 20, y: 20), to: CGPoint(x: 19, y: 21), radius: 1, large: false, sweep: true)
        path.addLine(to: CGPoint(x: 14.5, y: 21))
        path.addLine(to: CGPoint(x: 14.5, y: 15.5))
        path.addLine(to: CGPoint(x: 9.5, y: 15.5))
        path.addLine(to: CGPoint(x: 9.5, y: 21))
        path.addLine(to: CGPoint(x: 5, y: 21))
        NocturneProgramCarouselSVG.addArc(
            to: &path, from: CGPoint(x: 5, y: 21), to: CGPoint(x: 4, y: 20), radius: 1, large: false, sweep: true)
        path.closeSubpath()
        return path
    }()

    /// `M3 13h4l3-7 4 13 3-6h4`.
    private static let pulse: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 3, y: 13))
        path.addLine(to: CGPoint(x: 7, y: 13))
        path.addLine(to: CGPoint(x: 10, y: 6))
        path.addLine(to: CGPoint(x: 14, y: 19))
        path.addLine(to: CGPoint(x: 17, y: 13))
        path.addLine(to: CGPoint(x: 21, y: 13))
        return path
    }()

    /// `M8.5 14.2c1.8 2 5.2 2 7 0`.
    private static let smile: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 8.5, y: 14.2))
        path.addCurve(to: CGPoint(x: 15.5, y: 14.2), control1: CGPoint(x: 10.3, y: 16.2), control2: CGPoint(x: 13.7, y: 16.2))
        return path
    }()

    var body: some View {
        switch kind {
        case .program:
            NocturneProgramCarouselMark(size: 22)
        case .home, .statistic, .profile:
            Canvas { context, canvasSize in
                var ctx = context
                let scale = canvasSize.width / 24
                ctx.scaleBy(x: scale, y: scale)
                let color = P.rgb(0x3A4866)
                let lineStyle = StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round)
                switch kind {
                case .home:
                    ctx.fill(Self.house, with: .color(color))
                case .statistic:
                    ctx.stroke(Self.pulse, with: .color(color), style: lineStyle)
                case .profile:
                    var face = Path()
                    face.addEllipse(in: CGRect(x: 3, y: 3, width: 18, height: 18))
                    ctx.fill(face, with: .color(color))
                    var leftEye = Path()
                    leftEye.addEllipse(in: CGRect(x: 7.8, y: 8.8, width: 2.4, height: 2.4))
                    ctx.fill(leftEye, with: .color(Self.ink))
                    var rightEye = Path()
                    rightEye.addEllipse(in: CGRect(x: 13.8, y: 8.8, width: 2.4, height: 2.4))
                    ctx.fill(rightEye, with: .color(Self.ink))
                    ctx.stroke(
                        Self.smile,
                        with: .color(Self.ink),
                        style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round)
                    )
                case .program:
                    break
                }
            }
            .frame(width: 22, height: 22)
        }
    }
}

/// The white moon and star of the raised Sleep button, on a 24 x 24 grid shown at 26 x 26.
private struct NocturneProgramCarouselSleepGlyph: View {
    /// `M14.5 3.5a8.5 8.5 0 1 0 6 13.9A7 7 0 0 1 14.5 3.5Z`.
    private static let moon: Path = {
        let top = CGPoint(x: 14.5, y: 3.5)
        let bottom = CGPoint(x: 20.5, y: 17.4)
        var path = Path()
        path.move(to: top)
        NocturneProgramCarouselSVG.addArc(to: &path, from: top, to: bottom, radius: 8.5, large: true, sweep: false)
        NocturneProgramCarouselSVG.addArc(to: &path, from: bottom, to: top, radius: 7, large: false, sweep: true)
        path.closeSubpath()
        return path
    }()

    var body: some View {
        Canvas { context, canvasSize in
            var ctx = context
            let scale = canvasSize.width / 24
            ctx.scaleBy(x: scale, y: scale)
            ctx.fill(Self.moon, with: .color(P.rgb(0xFFFFFF)))
            var dot = Path()
            dot.addEllipse(in: CGRect(x: 16.2, y: 5.6, width: 2.8, height: 2.8))
            ctx.fill(dot, with: .color(P.rgb(0xFFFFFF)))
        }
        .frame(width: 26, height: 26)
    }
}
