import SwiftUI
import UIKit

/// The six steps of the tutorial (`STEPS`): a two-line instruction and an emoji.
enum PaperDeckTutorialCopy {
    static let titles: [String] = [
        "Swipe the card\nto the right",
        "Swipe the card\nto the left",
        "Little questions.\nMany ways to connect.",
        "Pick a theme\nfor the moment.",
        "One card can start\na great conversation.",
        "You are ready.\nLet curiosity lead.",
    ]

    static let symbols: [String] = [
        "\u{1F449}",
        "\u{1F448}",
        "\u{1F0CF}",
        "\u{1F440}",
        "\u{1F5FA}\u{FE0F}",
        "\u{263A}\u{FE0F}",
    ]

    /// The card's accessibility label (`accessibilityLabel` of the card container).
    static func label(step: Int) -> String {
        if step < 6 {
            let title = titles[step].replacingOccurrences(of: "\n", with: " ")
            return "Card \(step + 1) of 6. \(title)"
        }
        return "This or that. Burger or apple?"
    }
}

/// One white sheet of the deck: the paper, the highlight that rides its bend, and its copy.
/// It is drawn at `width` x `height` and placed with a perspective transform, a shadow and an
/// opacity from `PaperDeckTutorialScript.cardStyle`.
struct PaperDeckTutorialCard: View {
    let index: Int
    let pose: PaperDeckTutorialPose
    let style: PaperDeckTutorialCardStyle
    let width: CGFloat
    let height: CGFloat
    let reduced: Bool
    let fontScale: Double

    var body: some View {
        let curve = PaperDeckTutorialScript.curve(index: index, pose: pose, reduced: reduced)
        let sheen = PaperDeckTutorialScript.sheenAmount(index: index, pose: pose, width: Double(width), reduced: reduced)
        let sheenOffset = PaperDeckTutorialScript.sheenOffset(pose: pose, width: Double(width))
        let size = CGSize(width: width, height: height)
        let projection = PaperDeckTutorialScript.face(
            size: size,
            x: style.offsetX,
            y: style.offsetY,
            scale: style.scale,
            roll: style.roll,
            yaw: style.yaw
        )
        ZStack(alignment: .topLeading) {
            PaperDeckTutorialPaper(width: width, height: height, curve: curve)
            if sheen > 0 {
                PaperDeckTutorialSheen(width: width, height: height, amount: sheen, offset: sheenOffset)
            }
            PaperDeckTutorialCardText(index: index, width: width, height: height, fontScale: fontScale)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .compositingGroup()
        .shadow(
            color: Color.black.opacity(style.shadowOpacity),
            radius: CGFloat(style.shadowRadius),
            x: CGFloat(style.shadowX),
            y: CGFloat(style.shadowY)
        )
        .opacity(style.opacity)
        .projectionEffect(projection)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// The sheet: a rounded rectangle whose top and bottom edges bow with the drag (`paper` animated
/// props), filled with a paper-white gradient and outlined 1 pt.
struct PaperDeckTutorialPaper: View {
    let width: CGFloat
    let height: CGFloat
    /// How far the middle of the top edge moves down and of the bottom edge up (negative: the other way).
    let curve: Double

    /// `<SvgGradient>`: `#FFFFFF`, `#FDFBFE` at 0.6, `#F6F4F8`.
    private static let paper = Gradient(stops: [
        Gradient.Stop(color: PaperDeckTutorialPalette.rgb(0xFFFFFF), location: 0),
        Gradient.Stop(color: PaperDeckTutorialPalette.rgb(0xFDFBFE), location: 0.6),
        Gradient.Stop(color: PaperDeckTutorialPalette.rgb(0xF6F4F8), location: 1),
    ])

    var body: some View {
        Canvas { context, _ in
            let bow = CGFloat(curve)
            let path = Self.sheet(width: width, height: height, bow: bow)

            // The gradient runs from (0, 0) to (0.35, 1) of the path's bounding box (SVG's default
            // objectBoundingBox units, with the control points counted as CoreGraphics does), so
            // its stripes are skewed with the box: the fill is drawn in a unit-sized context.
            let top = min(5, 5 + bow)
            let bottom = max(height - 5, height - 5 - bow)
            let box = CGRect(x: 5, y: top, width: width - 10, height: bottom - top)
            let unit = CGAffineTransform(
                a: 1 / box.width, b: 0, c: 0, d: 1 / box.height,
                tx: -box.minX / box.width, ty: -box.minY / box.height
            )
            var scaled = context
            scaled.translateBy(x: box.minX, y: box.minY)
            scaled.scaleBy(x: box.width, y: box.height)
            scaled.fill(
                path.applying(unit),
                with: .linearGradient(Self.paper, startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0.35, y: 1))
            )

            context.stroke(path, with: .color(PaperDeckTutorialPalette.paperStroke), lineWidth: 1)
        }
        .frame(width: width, height: height)
    }

    /// `d` of the `AnimatedPath`: `M26 5 Q... Z`.
    private static func sheet(width w: CGFloat, height h: CGFloat, bow: CGFloat) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: 26, y: 5))
        path.addQuadCurve(to: CGPoint(x: w - 26, y: 5), control: CGPoint(x: w * 0.5, y: 5 + bow))
        path.addQuadCurve(to: CGPoint(x: w - 5, y: 26), control: CGPoint(x: w - 5, y: 5))
        path.addLine(to: CGPoint(x: w - 5, y: h - 26))
        path.addQuadCurve(to: CGPoint(x: w - 26, y: h - 5), control: CGPoint(x: w - 5, y: h - 5))
        path.addQuadCurve(to: CGPoint(x: 26, y: h - 5), control: CGPoint(x: w / 2, y: h - 5 - bow))
        path.addQuadCurve(to: CGPoint(x: 5, y: h - 26), control: CGPoint(x: 5, y: h - 5))
        path.addLine(to: CGPoint(x: 5, y: 26))
        path.addQuadCurve(to: CGPoint(x: 26, y: 5), control: CGPoint(x: 5, y: 5))
        path.closeSubpath()
        return path
    }
}

/// Light skimming the bowed sheet: a soft white band, 0.3 of the card wide, riding the crest of the
/// bend (`sheen` and `sheenClip`).
struct PaperDeckTutorialSheen: View {
    let width: CGFloat
    let height: CGFloat
    let amount: Double
    let offset: Double

    var body: some View {
        ZStack(alignment: .leading) {
            LinearGradient(
                colors: [
                    PaperDeckTutorialPalette.rgb(0xFFFFFF, opacity: 0),
                    PaperDeckTutorialPalette.rgb(0xFFFFFF, opacity: 204.0 / 255.0),
                    PaperDeckTutorialPalette.rgb(0xFFFFFF, opacity: 0),
                ],
                startPoint: UnitPoint(x: 0, y: 0.5),
                endPoint: UnitPoint(x: 1, y: 0.5)
            )
            .frame(width: width * 0.3, height: height)
            .offset(x: CGFloat(offset))
            .opacity(amount * 0.9)
        }
        .frame(width: width, height: height, alignment: .leading)
        .clipShape(RoundedRectangle(cornerRadius: 24))
        .allowsHitTesting(false)
    }
}

/// The card's copy: a serif instruction and an emoji, centred as one column in the card below its
/// top 10 pt, and the card's number 38 pt above the bottom edge.
struct PaperDeckTutorialCardText: View {
    let index: Int
    let width: CGFloat
    let height: CGFloat
    let fontScale: Double

    var body: some View {
        // `maxFontSizeMultiplier`: 1.1 on the title, 1.4 on the emoji and the number.
        let titleScale = CGFloat(min(fontScale, 1.1))
        let bodyScale = CGFloat(min(fontScale, 1.4))
        let titleFont = PaperDeckTutorialFonts.serif(37 * titleScale)
        let titleStyle = PaperDeckTutorialLineStyle(
            font: titleFont,
            lineHeight: 41 * titleScale,
            kern: -0.5,
            color: PaperDeckTutorialPalette.cardTitle
        )
        let symbolStyle = PaperDeckTutorialLineStyle(
            font: UIFont.systemFont(ofSize: 28 * bodyScale),
            lineHeight: 36 * bodyScale,
            color: PaperDeckTutorialPalette.cardTitle
        )
        let numberStyle = PaperDeckTutorialLineStyle(
            font: PaperDeckTutorialFonts.serif(19 * bodyScale),
            lineHeight: 24 * bodyScale,
            color: PaperDeckTutorialPalette.cardNumber
        )
        // `paddingHorizontal: 22`: the title wraps inside the card minus 44 pt.
        let lines = PaperDeckTutorialTypography.lines(
            PaperDeckTutorialCopy.titles[index],
            font: titleFont,
            kern: -0.5,
            width: width - 44
        )
        ZStack(alignment: .top) {
            VStack(spacing: 0) {
                ForEach(0..<lines.count, id: \.self) { row in
                    PaperDeckTutorialLine(text: lines[row], style: titleStyle)
                }
                PaperDeckTutorialLine(text: PaperDeckTutorialCopy.symbols[index], style: symbolStyle)
                    .padding(.top, 40)
            }
            .frame(width: width - 44)
            .frame(width: width, height: height - 10)
            .padding(.top, 10)

            PaperDeckTutorialLine(text: "\u{2013} \(index + 1) \u{2013}", style: numberStyle)
                .frame(width: width)
                .padding(.bottom, 38)
                .frame(width: width, height: height, alignment: .bottom)
        }
        .frame(width: width, height: height, alignment: .top)
    }
}
