import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Loads bundled art/fonts and caches static text faces and paper paths.
/// Layout and typography use points; text bitmaps are rendered at 3× to match the reference.
final class TapRidePassResources {
    static let shared = TapRidePassResources()
    static let pixel = 1.0 / Double(UIScreen.main.scale)
    static func ui(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(
            red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255,
            alpha: alpha)
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
            blue: Double(hex & 255) / 255, opacity: alpha)
    }
    let backdrop: UIImage
    let validator: UIImage
    let card: UIImage
    let coil: UIImage
    var cardFace = UIImage()
    var ticketFace = UIImage()
    var headerA = UIImage()
    var headerB = UIImage()
    var eyebrow = UIImage()
    var done = UIImage()
    var oldBalance = UIImage()
    var newBalance = UIImage()
    var ticketRollY = 0.0
    var cachedCardWidth = 0.0
    var cachedTicketWidth = 0.0
    var balance = -1.0
    var backgroundPath = Path()
    var shadePath = Path()
    var readerPath = Path()
    var readerInsetPath = Path()
    var cardSmokePath = Path()
    var shadowPath = Path()
    private var geometry: TapRidePassGeometry?
    private var paperSize = CGSize.zero
    private var paperOutline = Path()
    private var paperCut = Path()
    private var paperPerf = Path()
    /// Caches the rounded outline, side-notch cutout and perforation for a paper size in points.
    func paperPaths(_ width: Double, _ height: Double, _ ticketWidth: Double) -> (Path, Path, Path) {
        let size = CGSize(width: width, height: height)
        if paperSize != size {
            paperSize = size
            paperOutline = Path(
                roundedRect: CGRect(origin: .zero, size: size), cornerRadius: 18 * width / ticketWidth)
            paperCut = paperOutline
            let notchRadius = 12 * width / ticketWidth
            let rowY = height * 0.74
            paperCut.addEllipse(
                in: CGRect(
                    x: -notchRadius, y: rowY - notchRadius, width: notchRadius * 2, height: notchRadius * 2))
            paperCut.addEllipse(
                in: CGRect(
                    x: width - notchRadius, y: rowY - notchRadius, width: notchRadius * 2,
                    height: notchRadius * 2))
            paperPerf = Path()
            paperPerf.move(to: CGPoint(x: 22 * width / ticketWidth, y: rowY))
            paperPerf.addLine(to: CGPoint(x: width - 22 * width / ticketWidth, y: rowY))
        }
        return (paperOutline, paperCut, paperPerf)
    }
    private var fonts: [String: UIFont] = [:]
    init() {
        for name in ["Archivo-ExtraBold", "Archivo-SemiBold", "Archivo-Medium", "JetBrainsMono-Medium"] {
            if let url = Bundle.main.url(forResource: "TapRidePass-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        func load(_ name: String, _ ext: String) -> UIImage {
            guard let url = Bundle.main.url(forResource: "TapRidePass-\(name)", withExtension: ext),
                let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                let cgImage = CGImageSourceCreateImageAtIndex(
                    source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            else { return UIImage() }
            return UIImage(cgImage: cgImage)
        }
        backdrop = load("backdrop", "jpg")
        validator = load("validator", "webp")
        card = load("card", "webp")
        coil = load("card-coil", "webp")
    }
    /// Resolves the bundled Archivo/JetBrains faces and caches each point-size variant.
    func font(_ name: String, _ size: Double) -> UIFont {
        let key = "\(name)/\(size)"
        if let font = fonts[key] { return font }
        let font =
            UIFont(name: name == "Mono" ? "JetBrainsMono-Medium" : "Archivo-\(name)", size: size)
            ?? .systemFont(ofSize: size)
        fonts[key] = font
        return font
    }
    /// Rasterizes a static face at 3× with the standard colour range.
    func render(_ width: Double, _ height: Double, _ draw: (CGContext) -> Void) -> UIImage {
        let rendererFormat = UIGraphicsImageRendererFormat()
        rendererFormat.scale = 3
        rendererFormat.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: rendererFormat).image {
            renderer in draw(renderer.cgContext)
        }
    }
    /// Measures typographic advance in points, including explicit tracking.
    func width(_ text: String, _ face: String, _ size: Double, _ tracking: Double = 0) -> Double {
        let attributedText = NSAttributedString(
            string: text, attributes: [.font: font(face, size), .kern: tracking])
        return CTLineGetTypographicBounds(CTLineCreateWithAttributedString(attributedText), nil, nil, nil)
    }
    // Skia measureText returns the union of raster glyph bounds, including the A8 mask rim;
    // it does not return the typographic advance used by the native text layout engine.
    private func skiaWidth(_ text: String, _ face: String, _ size: Double) -> Double {
        let font = font(face, size) as CTFont
        let chars = Array(text.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        var bounds = [CGRect](repeating: .zero, count: glyphs.count)
        CTFontGetBoundingRectsForGlyphs(font, .horizontal, glyphs, &bounds, glyphs.count)
        var union = CGRect.null
        var x = 0.0
        for i in glyphs.indices {
            if !bounds[i].isEmpty {
                union = union.union(bounds[i].integral.insetBy(dx: -1, dy: -1).offsetBy(dx: x, dy: 0))
            }
            x += advances[i].width
        }
        return union.isNull ? 0 : union.width
    }
    /// Rounds the native line height to the screen pixel grid, expressed in points.
    private func textHeight(_ face: String, _ size: Double) -> Double {
        ceil(font(face, size).lineHeight / Self.pixel) * Self.pixel
    }
    /// Draws a Core Text line in a top-aligned line box, or at the supplied baseline.
    /// Coordinates, font size, line height and tracking are points; right alignment uses advance width.
    func text(
        _ context: CGContext, _ text: String, _ x: Double, _ rowY: Double, _ face: String, _ size: Double,
        _ hex: Int, _ tracking: Double = 0, _ lineHeight: Double? = nil, _ right: Bool = false,
        _ baseline: Bool = false
    ) {
        let font = font(face, size)
        let attributedText = NSAttributedString(
            string: text, attributes: [.font: font, .kern: tracking, .foregroundColor: Self.ui(hex)])
        let textLine = CTLineCreateWithAttributedString(attributedText)
        let width = CTLineGetTypographicBounds(textLine, nil, nil, nil)
        let baselineY =
            baseline ? rowY : rowY + font.ascender + ((lineHeight ?? textHeight(face, size)) - font.lineHeight) / 2
        context.saveGState()
        context.translateBy(x: x - (right ? width : 0), y: baselineY)
        context.scaleBy(x: 1, y: -1)
        context.textMatrix = .identity
        context.textPosition = .zero
        CTLineDraw(textLine, context)
        context.restoreGState()
    }
    /// Draws the line badge with its caption centred by typographic advance.
    func roundel(_ context: CGContext, _ x: Double, _ rowY: Double, _ size: Double) {
        context.setFillColor(Self.ui(0xE8452C).cgColor)
        context.fillEllipse(in: CGRect(x: x, y: rowY, width: size, height: size))
        let fontSize = size * 0.42
        let width = width("R2", "ExtraBold", fontSize)
        text(context, "R2", x + (size - width) / 2, rowY, "ExtraBold", fontSize, 0xffffff, 0, size)
    }
    /// Rebuilds geometry paths when needed, then rasterizes card/header/ticket faces when size or balance changes.
    func prepare(_ layout: TapRidePassGeometry, _ currentBalance: Double, _ balanceAfterFare: Double) {
        if geometry != layout {
            geometry = layout
            backgroundPath = Path(CGRect(x: 0, y: 0, width: layout.width, height: layout.height))
            shadePath = Path(CGRect(x: 0, y: 0, width: layout.width, height: layout.safeTop + 170))
            readerPath = Path(
                ellipseIn: CGRect(
                    x: layout.readerX - layout.readerRadiusX, y: layout.readerEllipseY - layout.readerRadiusY,
                    width: layout.readerRadiusX * 2, height: layout.readerRadiusY * 2))
            readerInsetPath = Path(
                ellipseIn: CGRect(
                    x: layout.readerX - layout.readerRadiusX * 0.97,
                    y: layout.readerEllipseY - layout.readerRadiusY * 0.97, width: layout.readerRadiusX * 1.94,
                    height: layout.readerRadiusY * 1.94))
            cardSmokePath = Path(
                roundedRect: CGRect(x: 3, y: 3, width: layout.cardWidth - 6, height: layout.cardHeight - 6),
                cornerRadius: layout.cardWidth * 0.034)
            shadowPath = Path(
                roundedRect: CGRect(x: 6, y: 6, width: layout.cardWidth - 12, height: layout.cardHeight - 8),
                cornerRadius: 18)
        }
        guard
            cachedCardWidth != layout.cardWidth || cachedTicketWidth != layout.ticketWidth
                || balance != currentBalance
        else { return }
        cachedCardWidth = layout.cardWidth
        cachedTicketWidth = layout.ticketWidth
        balance = currentBalance
        let cardWidth = layout.cardWidth
        let cardHeight = layout.cardHeight
        let ticketWidth = layout.ticketWidth
        let ticketHeight = layout.ticketHeight
        cardFace = render(cardWidth, cardHeight) { context in
            roundel(context, 50, 52, 20)
            // Skia text is positioned by its baseline, including the tiny roundel caption.
            context.clear(CGRect(x: 50, y: 52, width: 20, height: 20))
            context.setFillColor(Self.ui(0xE8452C).cgColor)
            context.fillEllipse(in: CGRect(x: 50, y: 52, width: 20, height: 20))
            text(
                context, "R2", 60 - skiaWidth("R2", "ExtraBold", 8.5) / 2, 65.2, "ExtraBold", 8.5, 0xffffff, 0,
                nil, false, true)
            text(context, "RIVERLINE", 77, 66, "Mono", 11, 0xF3EEE6, 0, nil, false, true)
            text(
                context, "30 DAYS", cardWidth - 58 - skiaWidth("30 DAYS", "Mono", 11), 66, "Mono", 11, 0xC9BFB1, 0,
                nil, false, true)
            let rowY = cardHeight * 0.47
            context.setStrokeColor(Self.ui(0xE8452C).cgColor)
            context.setLineWidth(1.6)
            context.move(to: CGPoint(x: 104, y: rowY))
            context.addLine(to: CGPoint(x: cardWidth - 60, y: rowY))
            context.strokePath()
            context.setFillColor(Self.ui(0xE8452C).cgColor)
            for i in 0..<6 {
                let stopRadius = i == 2 ? 4.2 : 2.6
                let x = 104 + (cardWidth - 164) / 5 * Double(i)
                context.fillEllipse(
                    in: CGRect(
                        x: x - stopRadius, y: rowY - stopRadius, width: stopRadius * 2, height: stopRadius * 2))
            }
            text(context, "HBG · OML · 4 STOPS", 104, rowY + 20, "Mono", 9, 0xC9BFB1, 0, nil, false, true)
            text(context, "Commuter", 104, cardHeight - 72, "SemiBold", 15, 0xF3EEE6, 0, nil, false, true)
            text(context, "0417 2048", 104, cardHeight - 55, "Mono", 11, 0xC9BFB1, 0, nil, false, true)
            text(
                context, "BALANCE", cardWidth - 58 - skiaWidth("BALANCE", "Mono", 9), cardHeight - 82, "Mono", 9,
                0xC9BFB1, 0, nil, false, true)
            text(
                context, String(format: "$%.2f", currentBalance),
                cardWidth - 58 - skiaWidth(String(format: "$%.2f", currentBalance), "ExtraBold", 24),
                cardHeight - 55, "ExtraBold", 24, 0xF3EEE6, 0, nil, false, true)
        }
        eyebrow = render(layout.width - 48, 20) { context in
            roundel(context, 0, 0, 20)
            text(context, "RIVERLINE · NORTHBOUND", 28, 0, "Mono", 11, 0x9A9084, 1.6, 20)
        }
        headerA = render(layout.width - 48, 90) { context in
            text(context, "Harbor Gate", 0, 12, "ExtraBold", 38, 0xF3EEE6, -0.8, 42)
            text(context, "Platform 2 · next train in 2 min", 0, 58, "Medium", 15, 0x9A9084, 0, 20)
        }
        headerB = render(layout.width - 48, 90) { context in
            text(context, "You’re through", 0, 12, "ExtraBold", 38, 0xF3EEE6, -0.8, 42)
            text(context, "Gate 3 open · next train in 2 min", 0, 58, "Medium", 15, 0x9A9084, 0, 20)
        }
        done = render(140, 48) { context in
            let outline = UIBezierPath(
                roundedRect: CGRect(x: 0.5, y: 0.5, width: 139, height: 47), cornerRadius: 24)
            context.setFillColor(Self.ui(0xffffff, 0.1).cgColor)
            context.addPath(outline.cgPath)
            context.fillPath()
            context.setStrokeColor(Self.ui(0xffffff, 0.16).cgColor)
            context.addPath(outline.cgPath)
            context.setLineWidth(1)
            context.strokePath()
            text(context, "Done", (140 - width("Done", "SemiBold", 16)) / 2, 0, "SemiBold", 16, 0xF3EEE6, 0, 48)
        }
        let hairline = Self.pixel
        let codeY = 66.0
        let stationY = 118.0
        let gridY = stationY + textHeight("Medium", 13) + 22
        let labelHeight = textHeight("Mono", 10)
        let row1 = 12 + labelHeight + 4 + 32 + 10 + hairline
        let row2 = 12 + labelHeight + 4 + 26 + 10
        ticketRollY = gridY + hairline + 12 + labelHeight
        oldBalance = render(ticketWidth / 2 - 22, 30) { context in
            text(context, String(format: "$%.2f", currentBalance), 0, 4, "SemiBold", 19, 0x17140F, 0, 26)
        }
        newBalance = render(ticketWidth / 2 - 22, 30) { context in
            text(context, String(format: "$%.2f", balanceAfterFare), 0, 4, "SemiBold", 19, 0x17140F, 0, 26)
        }
        ticketFace = render(ticketWidth, ticketHeight) { context in
            roundel(context, 22, 22, 18)
            text(context, "SINGLE RIDE", 48, 22, "Mono", 10.5, 0x7C746A, 1.4, 18)
            text(context, "31 OCT · 08:42", ticketWidth - 22, 22, "Mono", 10.5, 0x7C746A, 1.4, 18, true)
            text(context, "HBG", 22, codeY, "ExtraBold", 46, 0x17140F, -1.2, 50)
            text(context, "OML", ticketWidth - 22, codeY, "ExtraBold", 46, 0x17140F, -1.2, 50, true)
            text(context, "Harbor Gate", 22, stationY, "Medium", 13, 0x7C746A)
            text(context, "Old Mill", ticketWidth - 22, stationY, "Medium", 13, 0x7C746A, 0, nil, true)
            let left = 22 + width("HBG", "ExtraBold", 46, -1.2)
            let right = ticketWidth - 22 - width("OML", "ExtraBold", 46, -1.2)
            let arrowX = (left + right - 60) / 2
            let arrowY = codeY + 18
            context.setStrokeColor(Self.ui(0xE8452C).cgColor)
            context.setLineWidth(2.2)
            context.move(to: CGPoint(x: arrowX, y: arrowY + 10))
            context.addLine(to: CGPoint(x: arrowX + 52, y: arrowY + 10))
            context.strokePath()
            for x in [14.0, 27, 40] {
                context.setFillColor(Self.ui(0xF4F0E8).cgColor)
                context.fillEllipse(in: CGRect(x: arrowX + x - 2.8, y: arrowY + 7.2, width: 5.6, height: 5.6))
                context.setLineWidth(1.8)
                context.strokeEllipse(in: CGRect(x: arrowX + x - 2.8, y: arrowY + 7.2, width: 5.6, height: 5.6))
            }
            context.setLineWidth(2.2)
            context.setLineJoin(.round)
            context.move(to: CGPoint(x: arrowX + 51, y: arrowY + 4))
            context.addLine(to: CGPoint(x: arrowX + 58, y: arrowY + 10))
            context.addLine(to: CGPoint(x: arrowX + 51, y: arrowY + 16))
            context.strokePath()
            context.setStrokeColor(Self.ui(0x000000, 0.18).cgColor)
            context.setLineWidth(1.0 / 3)
            for rowY in [gridY + hairline / 2, gridY + hairline + row1 - hairline / 2] {
                context.move(to: CGPoint(x: 22, y: rowY))
                context.addLine(to: CGPoint(x: ticketWidth - 22, y: rowY))
                context.strokePath()
            }
            context.move(to: CGPoint(x: ticketWidth / 2 + hairline / 2, y: gridY + hairline))
            context.addLine(to: CGPoint(x: ticketWidth / 2 + hairline / 2, y: gridY + hairline + row1 + row2))
            context.strokePath()
            for (title, value, x, rowY, isFare) in [
                ("FARE", "$2.75", 22.0, gridY + hairline, true),
                ("BALANCE", "", ticketWidth / 2 + 16 + hairline, gridY + hairline, false),
                ("STOPS", "4 · 11 min", 22.0, gridY + hairline + row1, false),
                ("GATE", "3", ticketWidth / 2 + 16 + hairline, gridY + hairline + row1, false),
            ] {
                text(context, title, x, rowY + 12, "Mono", 10, 0x7C746A, 1.6)
                if !value.isEmpty {
                    text(
                        context, value, x, rowY + 12 + labelHeight + 4, isFare ? "ExtraBold" : "SemiBold",
                        isFare ? 28 : 19, 0x17140F, isFare ? -0.4 : 0, isFare ? 32 : 26)
                }
            }
            let stubY = ticketHeight * 0.74 + 20
            let gateHeight = textHeight("SemiBold", 18)
            let smallHeight = textHeight("Mono", 10.5)
            let total = gateHeight + 6 + smallHeight
            context.setFillColor(Self.ui(0x1FAF6A).cgColor)
            context.fillEllipse(in: CGRect(x: 22, y: stubY + (gateHeight - 9) / 2, width: 9, height: 9))
            text(context, "Gate 3 open", 40, stubY, "SemiBold", 18, 0x17140F)
            text(context, "Valid until 10:42", 22, stubY + gateHeight + 6, "Mono", 10.5, 0x7C746A, 0.4)
            let bars = [
                2, 1, 1, 3, 1, 2, 1, 1, 2, 3, 1, 1, 2, 1, 3, 1, 2, 2, 1, 1, 3, 1, 1, 2, 1, 2, 3, 1, 1, 2, 1, 1, 2,
                3, 1, 2,
            ]
            let barcodeWidth = bars.enumerated().reduce(0.0) {
                $0 + Double($1.element) + ($1.offset % 3 == 0 ? 1.5 : 1)
            }
            var x = ticketWidth - 22 - barcodeWidth
            context.setFillColor(Self.ui(0x17140F).cgColor)
            for (i, barWidth) in bars.enumerated() {
                if i % 2 == 0 {
                    context.fill(CGRect(x: x, y: stubY + (total - 38) / 2, width: Double(barWidth), height: 38))
                }
                x += Double(barWidth) + (i % 3 == 0 ? 1.5 : 1)
            }
        }
    }
}
