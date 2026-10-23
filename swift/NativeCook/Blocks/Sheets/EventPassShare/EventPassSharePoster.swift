import CoreText
import SwiftUI
import UIKit

/// The poster is static and sampled by two shaders, so rasterize its many glyphs and paths once.
struct EventPassSharePoster: View {
    var scale = 1.0

    var body: some View {
        Image(uiImage: EventPassSharePosterRaster.image)
            .resizable()
            .interpolation(.high)
            .frame(width: 251 * scale, height: 251 * scale)
            .clipShape(RoundedRectangle(cornerRadius: 13 * scale, style: .continuous))
            .overlay(
                RoundedRectangle(cornerRadius: 13 * scale, style: .continuous)
                    .strokeBorder(.black.opacity(0.035), lineWidth: 0.5 * scale)
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Paws, People and Purpose event poster, Saturday July 18, 2 to 4 PM at Goodfolk Coffee in West Los Angeles")
    }
}

@MainActor
private enum EventPassSharePosterRaster {
    static let image: UIImage = render()
    private static let side = 251.0

    private static func render() -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { renderer in
            let context = renderer.cgContext
            context.setFillColor(color(0xFAF9F7).cgColor)
            context.fill(CGRect(x: 0, y: 0, width: side, height: side))

            text("FIELD & FELLOW", x: 38, baseline: 23, font: font("Georgia-Bold", size: 7),
                 color: color(0x164F3C), kern: -0.2, in: context)
            text("COMMON", x: 215, baseline: 21, font: .systemFont(ofSize: 5.3, weight: .heavy),
                 color: color(0x252A27), kern: 0.8, alignment: .right, in: context)
            text("GROUND", x: 215, baseline: 26.3, font: .systemFont(ofSize: 5.3, weight: .heavy),
                 color: color(0x252A27), kern: 0.8, alignment: .right, in: context)

            text("PAWS, PEOPLE &", x: 38, baseline: 70, font: .systemFont(ofSize: 22, weight: .regular),
                 color: color(0x164F3C), kern: -0.7, in: context)
            text("PURPOSE", x: 38, baseline: 91, font: .systemFont(ofSize: 22, weight: .regular),
                 color: color(0x164F3C), kern: -0.7, in: context)
            text("A COMMUNITY CONVERSATION ON HOW WE SHOW UP FOR SHELTER DOGS", x: 38, baseline: 100.5,
                 font: font("Courier", size: 4.5), color: color(0x26322D), kern: -0.16, in: context)

            drawPortraitStrip(in: context)
            for (index, name) in ["ARI", "MAYA", "JULES", "NOAH", "LEA"].enumerated() {
                text(name, x: 38 + Double(index) * 29, baseline: 141, font: font("Courier-Bold", size: 4.2),
                     color: color(0x313632), in: context)
            }

            context.saveGState()
            context.translateBy(x: 92, y: 171)
            context.rotate(by: -4 * .pi / 180)
            context.translateBy(x: -92, y: -171)
            text("Saturday July 18", x: 39, baseline: 174, font: font("Georgia-Italic", size: 14),
                 color: color(0xB89965), in: context)
            context.restoreGState()

            let programme = ["PANEL  ·  FIRESIDE CHAT", "TRAINING Q&A", "SILENT AUCTION FUNDRAISER", "LOCAL VENDORS"]
            for (index, line) in programme.enumerated() {
                text(line, x: 150, baseline: 164 + Double(index) * 5, font: font("Courier-Bold", size: 4.5),
                     color: color(0x303631), in: context)
            }
            text("2–4 PM", x: 38, baseline: 204, font: font("Courier-Bold", size: 5.5), color: color(0x303631), in: context)
            text("GOODFOLK COFFEE, WEST LA", x: 38, baseline: 212, font: font("Courier-Bold", size: 5.5), color: color(0x303631), in: context)
            text("SUPPORTED BY", x: 38, baseline: 233, font: font("Courier", size: 3.6), color: color(0x585B57), in: context)
            text("GOOD DOGS", x: 47, baseline: 246, font: .systemFont(ofSize: 5.2, weight: .bold), color: color(0x333A35), in: context)
            text("PET KIND", x: 96, baseline: 246, font: font("Georgia-Italic", size: 5.5), color: color(0x333A35), in: context)

            context.setStrokeColor(color(0x333A35).cgColor)
            context.setLineWidth(1)
            context.move(to: CGPoint(x: 152, y: 246))
            context.addCurve(to: CGPoint(x: 168, y: 246), control1: CGPoint(x: 157, y: 237), control2: CGPoint(x: 163, y: 237))
            context.addCurve(to: CGPoint(x: 152, y: 246), control1: CGPoint(x: 162, y: 243), control2: CGPoint(x: 158, y: 243))
            context.move(to: CGPoint(x: 160, y: 238))
            context.addLine(to: CGPoint(x: 160, y: 248))
            context.strokePath()
        }
    }

    private enum Alignment { case left, right }

    private static func text(
        _ string: String,
        x: Double,
        baseline: Double,
        font: UIFont,
        color: UIColor,
        kern: Double = 0,
        alignment: Alignment = .left,
        in context: CGContext
    ) {
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font,
            .foregroundColor: color,
            .kern: kern,
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: attributes))
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        let originX = alignment == .right ? x - width : x
        context.saveGState()
        context.textMatrix = .identity
        context.translateBy(x: 0, y: side)
        context.scaleBy(x: 1, y: -1)
        context.textPosition = CGPoint(x: originX, y: side - baseline)
        // Passing UIFont directly preserves the system face; recreating it by name can become Times.
        CTLineDraw(line, context)
        context.restoreGState()
    }

    private static func drawPortraitStrip(in context: CGContext) {
        let rect = CGRect(x: 38, y: 105, width: 145, height: 29)
        context.saveGState()
        context.clip(to: rect)
        let image = EventPassShareAssets.portraitStrip
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let size = CGSize(width: image.size.width * scale, height: image.size.height * scale)
        image.draw(in: CGRect(x: rect.midX - size.width / 2, y: rect.midY - size.height / 2, width: size.width, height: size.height))
        context.restoreGState()
    }

    private static func font(_ name: String, size: Double) -> UIFont {
        UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
    }

    private static func color(_ hex: UInt32) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: 1
        )
    }
}
