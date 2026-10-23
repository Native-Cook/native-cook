import CoreText
import SwiftUI
import UIKit

enum GlassDiscOnboardingPalette {
    static let page = GlassDiscOnboardingUIColor.make(hex: 0xF4EFE6)
    static let ink = GlassDiscOnboardingUIColor.make(hex: 0x1D2B22)
    static let mute = GlassDiscOnboardingUIColor.make(hex: 0x5E6A61)
    static let brand = GlassDiscOnboardingUIColor.make(hex: 0x2F5D45)
    static let block = GlassDiscOnboardingUIColor.make(hex: 0xE6DFD0)
    static let park = GlassDiscOnboardingUIColor.make(hex: 0xCFE0BE)
    static let parkEdge = GlassDiscOnboardingUIColor.make(hex: 0xB9D1A4)
    static let tree = GlassDiscOnboardingUIColor.make(hex: 0xB5CF9C)
    static let water = GlassDiscOnboardingUIColor.make(hex: 0xB9D6E3)
    static let waterEdge = GlassDiscOnboardingUIColor.make(hex: 0xA3C7D8)
    static let street = UIColor.white
    static let streetCase = GlassDiscOnboardingUIColor.make(hex: 0xD9D0BE)
    static let major = GlassDiscOnboardingUIColor.make(hex: 0xF6DDA8)
    static let majorCase = GlassDiscOnboardingUIColor.make(hex: 0xE2C384)
    static let label = GlassDiscOnboardingUIColor.make(hex: 0x7A7466)
    static let route = GlassDiscOnboardingUIColor.make(hex: 0xE8642E)
    static let card = UIColor.white
    static let glass = UIColor.white.withAlphaComponent(0.72)
}

struct GlassDiscOnboardingLayout: Equatable {
    let width: CGFloat
    let height: CGFloat
    let top: CGFloat
    let bottom: CGFloat

    var domeX: CGFloat { width / 2 }
    var domeY: CGFloat { height + 52 }
    var lensY: CGFloat { height * 0.5 }
    var coverRadius: CGFloat { hypot(width, height) }
    var cardY: CGFloat { height - max(bottom, 16) - 8 - 164 }
}

struct GlassDiscOnboardingPages {
    let intro: UIImage
    let map: UIImage

    static func make(layout: GlassDiscOnboardingLayout) -> Self {
        GlassDiscOnboardingFonts.register()
        let format = UIGraphicsImageRendererFormat()
        format.opaque = true
        format.scale = UIScreen.main.scale
        let size = CGSize(width: layout.width, height: layout.height)
        let intro = UIGraphicsImageRenderer(size: size, format: format).image { output in
            drawIntro(in: output.cgContext, layout: layout)
        }
        let map = UIGraphicsImageRenderer(size: size, format: format).image { output in
            drawMap(in: output.cgContext, layout: layout)
            drawChrome(in: output.cgContext, layout: layout)
        }
        return Self(intro: intro, map: map)
    }

    static func routePath(layout: GlassDiscOnboardingLayout) -> Path {
        let width = layout.width
        let height = layout.height
        var path = Path()
        path.move(to: CGPoint(x: width * 0.2, y: height * 0.33))
        path.addCurve(
            to: CGPoint(x: width * 0.56, y: height * 0.31),
            control1: CGPoint(x: width * 0.3, y: height * 0.27),
            control2: CGPoint(x: width * 0.46, y: height * 0.25)
        )
        path.addCurve(
            to: CGPoint(x: width * 0.74, y: height * 0.5),
            control1: CGPoint(x: width * 0.66, y: height * 0.37),
            control2: CGPoint(x: width * 0.62, y: height * 0.45)
        )
        path.addCurve(
            to: CGPoint(x: width * 0.6, y: height * 0.6),
            control1: CGPoint(x: width * 0.86, y: height * 0.55),
            control2: CGPoint(x: width * 0.78, y: height * 0.62)
        )
        path.addCurve(
            to: CGPoint(x: width * 0.24, y: height * 0.52),
            control1: CGPoint(x: width * 0.42, y: height * 0.58),
            control2: CGPoint(x: width * 0.3, y: height * 0.6)
        )
        path.addCurve(
            to: CGPoint(x: width * 0.2, y: height * 0.33),
            control1: CGPoint(x: width * 0.18, y: height * 0.45),
            control2: CGPoint(x: width * 0.12, y: height * 0.4)
        )
        return path
    }

    private static func drawIntro(in context: CGContext, layout: GlassDiscOnboardingLayout) {
        let width = layout.width
        let height = layout.height
        context.setFillColor(GlassDiscOnboardingPalette.page.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))
        let colors = [
            GlassDiscOnboardingUIColor.make(hex: 0xE8642E, alpha: 0).cgColor,
            GlassDiscOnboardingUIColor.make(hex: 0xE8642E, alpha: 0.06).cgColor,
        ] as CFArray
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 1]) {
            context.drawLinearGradient(
                gradient,
                start: CGPoint(x: 0, y: height * 0.5),
                end: CGPoint(x: 0, y: height),
                options: []
            )
        }

        let margin: CGFloat = 24
        let brandY = layout.top + 20
        context.setFillColor(GlassDiscOnboardingPalette.brand.cgColor)
        context.fillEllipse(in: CGRect(x: margin, y: brandY, width: 28, height: 28))
        let mark = CGMutablePath()
        mark.move(to: CGPoint(x: margin + 7, y: brandY + 18))
        mark.addCurve(
            to: CGPoint(x: margin + 20, y: brandY + 13),
            control1: CGPoint(x: margin + 9, y: brandY + 6),
            control2: CGPoint(x: margin + 21, y: brandY + 6)
        )
        mark.addCurve(
            to: CGPoint(x: margin + 13, y: brandY + 13),
            control1: CGPoint(x: margin + 19, y: brandY + 20),
            control2: CGPoint(x: margin + 11, y: brandY + 19)
        )
        mark.addCurve(
            to: CGPoint(x: margin + 21, y: brandY + 20),
            control1: CGPoint(x: margin + 15, y: brandY + 8),
            control2: CGPoint(x: margin + 23, y: brandY + 12)
        )
        stroke(context, mark, color: GlassDiscOnboardingPalette.page, width: 2)
        text(context, "strolls", x: margin + 36, baseline: brandY + 21, font: .semibold(20), color: .ink)

        let headlineY = layout.top + 108
        for (index, line) in ["Walks worth taking,", "five minutes from", "your door."].enumerated() {
            text(context, line, x: margin, baseline: headlineY + CGFloat(index) * 40, font: .semibold(34), color: .ink)
        }
        for (index, line) in ["Routes drawn by people who live there,", "with the coffee stop marked."].enumerated() {
            text(context, line, x: margin, baseline: headlineY + 128 + CGFloat(index) * 22, font: .regular(16), color: .mute)
        }
    }

    private static func drawMap(in context: CGContext, layout: GlassDiscOnboardingLayout) {
        let width = layout.width
        let height = layout.height
        context.setFillColor(GlassDiscOnboardingPalette.block.cgColor)
        context.fill(CGRect(x: 0, y: 0, width: width, height: height))

        context.saveGState()
        context.translateBy(x: width / 2, y: height / 2)
        context.rotate(by: -.pi * 14 / 180)
        let span = hypot(width, height)
        let streets = CGMutablePath()
        for k in -12...12 {
            let x = CGFloat(k * 52 + (k * 7) % 11)
            streets.move(to: CGPoint(x: x, y: -span))
            streets.addLine(to: CGPoint(x: x, y: span))
            let y = CGFloat(k * 46 + (k * 5) % 9)
            streets.move(to: CGPoint(x: -span, y: y))
            streets.addLine(to: CGPoint(x: span, y: y))
        }
        stroke(context, streets, color: GlassDiscOnboardingPalette.streetCase, width: 8.5)
        stroke(context, streets, color: GlassDiscOnboardingPalette.street, width: 6.5)
        context.restoreGState()

        let park = CGMutablePath()
        park.move(to: CGPoint(x: width * 0.04, y: height * 0.23))
        park.addCurve(
            to: CGPoint(x: width * 0.62, y: height * 0.24),
            control1: CGPoint(x: width * 0.2, y: height * 0.17),
            control2: CGPoint(x: width * 0.46, y: height * 0.18)
        )
        park.addCurve(
            to: CGPoint(x: width * 0.52, y: height * 0.37),
            control1: CGPoint(x: width * 0.7, y: height * 0.27),
            control2: CGPoint(x: width * 0.66, y: height * 0.36)
        )
        park.addCurve(
            to: CGPoint(x: width * 0.08, y: height * 0.37),
            control1: CGPoint(x: width * 0.36, y: height * 0.38),
            control2: CGPoint(x: width * 0.2, y: height * 0.4)
        )
        park.addCurve(
            to: CGPoint(x: width * 0.04, y: height * 0.23),
            control1: CGPoint(x: width * -0.02, y: height * 0.34),
            control2: CGPoint(x: width * -0.02, y: height * 0.26)
        )
        fill(context, park, color: GlassDiscOnboardingPalette.park)
        stroke(context, park, color: GlassDiscOnboardingPalette.parkEdge, width: 1.5)
        var random = GlassDiscOnboardingRandom(seed: 7)
        context.saveGState()
        context.addPath(park)
        context.clip()
        context.setFillColor(GlassDiscOnboardingPalette.tree.cgColor)
        for _ in 0..<70 {
            let x = width * (0.04 + 0.62 * random.next())
            let y = height * (0.18 + 0.2 * random.next())
            let radius = 3 + 4 * random.next()
            context.fillEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
        }
        context.restoreGState()
        context.setFillColor(GlassDiscOnboardingPalette.water.cgColor)
        context.fillEllipse(in: CGRect(x: width * 0.3, y: height * 0.27, width: width * 0.16, height: height * 0.045))

        let canal = CGMutablePath()
        canal.move(to: CGPoint(x: -20, y: height * 0.66))
        canal.addCurve(
            to: CGPoint(x: width + 20, y: height * 0.56),
            control1: CGPoint(x: width * 0.3, y: height * 0.6),
            control2: CGPoint(x: width * 0.62, y: height * 0.7)
        )
        stroke(context, canal, color: GlassDiscOnboardingPalette.waterEdge, width: 44)
        stroke(context, canal, color: GlassDiscOnboardingPalette.water, width: 40)

        let roads = CGMutablePath()
        roads.move(to: CGPoint(x: width * 0.86, y: -20))
        roads.addCurve(
            to: CGPoint(x: width * 0.7, y: height + 20),
            control1: CGPoint(x: width * 0.8, y: height * 0.3),
            control2: CGPoint(x: width * 0.88, y: height * 0.6)
        )
        roads.move(to: CGPoint(x: -20, y: height * 0.47))
        roads.addCurve(
            to: CGPoint(x: width + 20, y: height * 0.42),
            control1: CGPoint(x: width * 0.3, y: height * 0.44),
            control2: CGPoint(x: width * 0.6, y: height * 0.47)
        )
        stroke(context, roads, color: GlassDiscOnboardingPalette.majorCase, width: 13)
        stroke(context, roads, color: GlassDiscOnboardingPalette.major, width: 11)

        text(context, "KESTREL PARK", x: width * 0.22, baseline: height * 0.215, font: .medium(10), color: .custom(0x6F8D5B))
        text(context, "Wend Canal", x: width * 0.08, baseline: height * 0.645, font: .medium(10), color: .custom(0x5E8AA0))
        text(context, "Marlow Road", x: width * 0.42, baseline: height * 0.452, font: .medium(10), color: .label)
        context.saveGState()
        context.translateBy(x: width * 0.84, y: height * 0.2)
        context.rotate(by: -.pi * 82 / 180)
        text(context, "Hatch Lane", x: 0, baseline: 0, font: .medium(10), color: .label)
        context.restoreGState()
        text(context, "Corn Exchange", x: width * 0.5, baseline: height * 0.79, font: .medium(10), color: .label)
    }

    private static func drawChrome(in context: CGContext, layout: GlassDiscOnboardingLayout) {
        let width = layout.width
        let y = layout.top + 12
        let height: CGFloat = 48
        shadowBox(context, rect: CGRect(x: 16, y: y, width: height, height: height), radius: 24)
        context.setFillColor(GlassDiscOnboardingPalette.glass.cgColor)
        context.fillEllipse(in: CGRect(x: 16, y: y, width: height, height: height))
        context.setStrokeColor(UIColor.white.withAlphaComponent(0.9).cgColor)
        context.setLineWidth(1)
        context.strokeEllipse(in: CGRect(x: 16.5, y: y + 0.5, width: height - 1, height: height - 1))
        let chevron = CGMutablePath()
        chevron.move(to: CGPoint(x: 43, y: y + 17))
        chevron.addLine(to: CGPoint(x: 36, y: y + 24))
        chevron.addLine(to: CGPoint(x: 43, y: y + 31))
        stroke(context, chevron, color: GlassDiscOnboardingPalette.ink, width: 2.2)

        let searchX: CGFloat = 72
        let searchWidth = width - 88
        let search = CGRect(x: searchX, y: y, width: searchWidth, height: height)
        shadowBox(context, rect: search, radius: 24)
        roundedFill(context, rect: search, radius: 24, color: GlassDiscOnboardingPalette.glass)
        roundedStroke(context, rect: search.insetBy(dx: 0.5, dy: 0.5), radius: 24, color: UIColor.white.withAlphaComponent(0.9), width: 1)
        context.setStrokeColor(GlassDiscOnboardingPalette.mute.cgColor)
        context.setLineWidth(1.8)
        context.strokeEllipse(in: CGRect(x: searchX + 16, y: y + 16.5, width: 12, height: 12))
        let handle = CGMutablePath()
        handle.move(to: CGPoint(x: searchX + 26.5, y: y + 27))
        handle.addLine(to: CGPoint(x: searchX + 30, y: y + 30.5))
        stroke(context, handle, color: GlassDiscOnboardingPalette.mute, width: 1.8)
        text(context, "Search walks near you", x: searchX + 40, baseline: y + 29, font: .regular(15), color: .mute)

        let card = CGRect(x: 16, y: layout.cardY, width: width - 32, height: 164)
        shadowBox(context, rect: card, radius: 24, blur: 24, dy: 8)
        roundedFill(context, rect: card, radius: 24, color: .white)
        roundedStroke(context, rect: card.insetBy(dx: 0.5, dy: 0.5), radius: 24, color: GlassDiscOnboardingUIColor.make(hex: 0x1D2B22, alpha: 0.08), width: 1)
        text(context, "Canal loop", x: 36, baseline: layout.cardY + 36, font: .semibold(18), color: .ink)
        text(context, "3.2 km · 45 min · Easy", x: 36, baseline: layout.cardY + 58, font: .regular(13), color: .mute)
        context.setFillColor(GlassDiscOnboardingPalette.route.cgColor)
        context.fillEllipse(in: CGRect(x: width - 52, y: layout.cardY + 24, width: 12, height: 12))
        context.setStrokeColor(GlassDiscOnboardingUIColor.make(hex: 0xE8642E, alpha: 0.25).cgColor)
        context.setLineWidth(3)
        context.strokeEllipse(in: CGRect(x: width - 55, y: layout.cardY + 21, width: 18, height: 18))

        var chipX: CGFloat = 36
        for chip in ["Shade", "Coffee stop", "Step-free"] {
            let chipWidth = textWidth(chip, font: .medium(12)) + 20
            roundedFill(
                context,
                rect: CGRect(x: chipX, y: layout.cardY + 72, width: chipWidth, height: 26),
                radius: 13,
                color: GlassDiscOnboardingUIColor.make(hex: 0xEEF1EC)
            )
            text(context, chip, x: chipX + 10, baseline: layout.cardY + 89, font: .medium(12), color: .brand)
            chipX += chipWidth + 8
        }
        let button = CGRect(x: 36, y: layout.cardY + 100, width: width - 72, height: 44)
        roundedFill(context, rect: button, radius: 22, color: GlassDiscOnboardingPalette.brand)
        text(context, "Start walk", x: button.midX, baseline: button.minY + 27, font: .semibold(15), color: .white, alignment: .center)
    }

    private enum TextColor {
        case ink, mute, brand, label, white, custom(UInt32)

        var uiColor: UIColor {
            switch self {
            case .ink: GlassDiscOnboardingPalette.ink
            case .mute: GlassDiscOnboardingPalette.mute
            case .brand: GlassDiscOnboardingPalette.brand
            case .label: GlassDiscOnboardingPalette.label
            case .white: .white
            case let .custom(value): GlassDiscOnboardingUIColor.make(hex: value)
            }
        }
    }

    private enum TextAlignment { case leading, center }

    private static func text(
        _ context: CGContext,
        _ value: String,
        x: CGFloat,
        baseline: CGFloat,
        font: UIFont,
        color: TextColor,
        alignment: TextAlignment = .leading
    ) {
        let attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font as CTFont,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): color.uiColor.cgColor,
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: value, attributes: attributes))
        let width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        context.saveGState()
        context.translateBy(x: alignment == .center ? x - width / 2 : x, y: baseline)
        context.scaleBy(x: 1, y: -1)
        context.textPosition = .zero
        CTLineDraw(line, context)
        context.restoreGState()
    }

    private static func textWidth(_ value: String, font: UIFont) -> CGFloat {
        let line = CTLineCreateWithAttributedString(NSAttributedString(
            string: value,
            attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font as CTFont]
        ))
        return CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
    }

    private static func fill(_ context: CGContext, _ path: CGPath, color: UIColor) {
        context.addPath(path)
        context.setFillColor(color.cgColor)
        context.fillPath()
    }

    private static func stroke(_ context: CGContext, _ path: CGPath, color: UIColor, width: CGFloat) {
        context.addPath(path)
        context.setStrokeColor(color.cgColor)
        context.setLineWidth(width)
        context.setLineCap(.round)
        context.setLineJoin(.round)
        context.strokePath()
    }

    private static func roundedFill(_ context: CGContext, rect: CGRect, radius: CGFloat, color: UIColor) {
        context.setFillColor(color.cgColor)
        context.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
        context.fillPath()
    }

    private static func roundedStroke(_ context: CGContext, rect: CGRect, radius: CGFloat, color: UIColor, width: CGFloat) {
        context.setStrokeColor(color.cgColor)
        context.setLineWidth(width)
        context.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
        context.strokePath()
    }

    private static func shadowBox(
        _ context: CGContext,
        rect: CGRect,
        radius: CGFloat,
        blur: CGFloat = 16,
        dy: CGFloat = 6
    ) {
        context.saveGState()
        context.setShadow(offset: CGSize(width: 0, height: dy), blur: blur / 2, color: GlassDiscOnboardingUIColor.make(hex: 0x1D2B22, alpha: 0.16).cgColor)
        roundedFill(context, rect: rect, radius: radius, color: GlassDiscOnboardingUIColor.make(hex: 0x1D2B22, alpha: 0.16))
        context.restoreGState()
    }
}

private enum GlassDiscOnboardingFonts {
    private static let files = [
        "GlassDiscOnboarding-Inter-Regular",
        "GlassDiscOnboarding-Inter-Medium",
        "GlassDiscOnboarding-Inter-SemiBold",
    ]

    static let registered: Void = {
        for file in files {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static func register() { _ = registered }
}

private extension UIFont {
    static func regular(_ size: CGFloat) -> UIFont {
        GlassDiscOnboardingFonts.register()
        return UIFont(name: "Inter24pt-Regular", size: size) ?? .systemFont(ofSize: size)
    }

    static func medium(_ size: CGFloat) -> UIFont {
        GlassDiscOnboardingFonts.register()
        return UIFont(name: "Inter-Medium", size: size) ?? .systemFont(ofSize: size, weight: .medium)
    }

    static func semibold(_ size: CGFloat) -> UIFont {
        GlassDiscOnboardingFonts.register()
        return UIFont(name: "Inter-SemiBold", size: size) ?? .systemFont(ofSize: size, weight: .semibold)
    }
}

private struct GlassDiscOnboardingRandom {
    private var state: Int

    init(seed: Int) { state = seed }

    mutating func next() -> CGFloat {
        state = state * 16_807 % 2_147_483_647
        return CGFloat(state - 1) / 2_147_483_646
    }
}

private enum GlassDiscOnboardingUIColor {
    static func make(hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(
            red: CGFloat((hex >> 16) & 0xFF) / 255,
            green: CGFloat((hex >> 8) & 0xFF) / 255,
            blue: CGFloat(hex & 0xFF) / 255,
            alpha: alpha
        )
    }
}
