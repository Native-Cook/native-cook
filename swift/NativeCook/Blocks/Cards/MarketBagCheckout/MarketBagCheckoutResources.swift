import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Screen and safe-area geometry in points, matching the reference row and bag anchors.
struct MarketBagCheckoutGeometry: Equatable {
    let width: Double
    let height: Double
    let top: Double
    let bottom: Double
    var cardY: Double { top + 108 }
    var cardWidth: Double { width - 32 }
    var cardHeight: Double { 12 + 46 * 5 + 46 }
    var buttonY: Double { height - bottom - 16 - 56 }
    var hookY: Double { cardY + cardHeight + 26 }
    var rimY: Double { hookY + 56 }
    var bagLength: Double { max(120, min(160, buttonY - rimY - 34 - 34)) }
    var centerX: Double { width / 2 }
    func rowY(_ i: Int) -> Double { cardY + 12 + 23 + 46 * Double(i) }
}

/// Decodes bundled artwork and rasterizes type once, then caches the static counter and frosted card.
/// Cached bitmaps use the reference 3× scale so point-sized artwork retains its pixel alignment.
final class MarketBagCheckoutResources {
    static let shared = MarketBagCheckoutResources()

    /// A pre-rendered text line and its logical point dimensions, before the bitmap padding.
    struct Label {
        let image: UIImage
        let canvas: Image
        let width: Double
        let height: Double
    }
    let counter: UIImage
    let images: [UIImage]
    let canvasImages: [Image]
    private var backgroundCanvas = Image(uiImage: UIImage())
    private(set) var buttonPath = Path()
    let tickCircle = Path(ellipseIn: CGRect(x: -9, y: -9, width: 18, height: 18))
    let tickCheck: Path = {
        var path = Path()
        path.move(to: CGPoint(x: -3.5, y: 0.3))
        path.addLine(to: CGPoint(x: -1, y: 2.6))
        path.addLine(to: CGPoint(x: 3.6, y: -2.4))
        return path
    }()
    var labels: [String: Label] = [:]
    private var cachedGeometry: MarketBagCheckoutGeometry?
    private var background = UIImage()
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(
            red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255, alpha: alpha)
    }

    /// Registers bundled fonts, eagerly decodes product images and prepares the fixed copy.
    init() {
        for name in ["Archivo-ExtraBold", "Archivo-SemiBold", "Archivo-Medium", "JetBrainsMono-Medium"] {
            if let url = Bundle.main.url(forResource: "MarketBagCheckout-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        func load(_ name: String, _ fileExtension: String = "webp") -> UIImage {
            guard
                let url = Bundle.main.url(forResource: "MarketBagCheckout-\(name)", withExtension: fileExtension),
                let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                let cgImage = CGImageSourceCreateImageAtIndex(
                    source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            else { return UIImage() }
            return UIImage(cgImage: cgImage)
        }
        counter = load("counter", "jpg")
        let decoded = MarketBagCheckoutMotion.items.map { load($0.key) }
        images = decoded
        canvasImages = decoded.map { Image(uiImage: $0) }
        labels["eyebrow"] = label(
            "CORNER GREENGROCER · PICKUP", "JetBrainsMono-Medium", 11, 0x6C7568, tracking: 1.6)
        labels["title"] = label("Your basket", "Archivo-ExtraBold", 36, 0x1F2A22, tracking: -0.7, height: 40)
        labels["totalLabel"] = label("Total", "Archivo-SemiBold", 15, 0x6C7568)
        labels["total"] = label("$11.40", "Archivo-ExtraBold", 20, 0x1F2A22)
        labels["pay"] = label("Hold to pay $11.40", "Archivo-SemiBold", 17, 0xF2EAD8)
        labels["paid"] = label("Paid · ready at the counter", "Archivo-SemiBold", 17, 0xF2EAD8)
        for (i, item) in MarketBagCheckoutMotion.items.enumerated() {
            labels["name\(i)"] = label(item.name, "Archivo-SemiBold", 15, 0x1F2A22)
            labels["meta\(i)"] = label(item.meta, "Archivo-Medium", 12, 0x6C7568)
            labels["price\(i)"] = label(String(format: "$%.2f", item.price), "JetBrainsMono-Medium", 14, 0x1F2A22)
        }
    }

    /// Rasterizes a label with point-sized type and tracking, retaining its unpadded alignment width.
    private func label(
        _ text: String, _ face: String, _ size: Double, _ hex: UInt32, tracking: Double = 0, height: Double? = nil
    ) -> Label {
        let font = UIFont(name: face, size: size) ?? UIFont.systemFont(ofSize: size)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: Self.color(hex)]
        if tracking != 0 { attributes[.kern] = tracking }
        let width = (text as NSString).size(withAttributes: attributes).width
        let height = height ?? ceil(font.lineHeight)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let bitmap = UIGraphicsImageRenderer(
            size: CGSize(width: ceil(width + 4), height: height + 4), format: format
        ).image { _ in
            (text as NSString).draw(
                at: CGPoint(x: 0, y: (height - font.lineHeight) / 2), withAttributes: attributes)
        }
        return Label(image: bitmap, canvas: Image(uiImage: bitmap), width: width, height: height)
    }
    // Match RCTText's independently pixel-rounded line boxes at the reference 3× scale.
    private func labelOffset(_ name: String) -> Double {
        if name.hasPrefix("meta") { return -2.0 / 3 }
        if name.hasPrefix("name") || name == "totalLabel" { return 1.0 / 3 }
        if name.hasPrefix("price") || name == "pay" || name == "paid" { return -1.0 / 6 }
        return -1.0 / 3
    }

    /// Positions cached type in points; alignment is 0 for leading, 0.5 for centered, 1 for trailing.
    func drawLabel(_ context: GraphicsContext, _ name: String, _ x: Double, _ y: Double, align: Double = 0) {
        guard let label = labels[name] else { return }
        context.draw(
            label.canvas,
            in: CGRect(
                x: x - label.width * align, y: y + labelOffset(name), width: label.image.size.width,
                height: label.image.size.height))
    }

    /// Rebuilds only when geometry changes: counter, frosted panel, static labels and brass hook.
    /// The panel blur uses a 16-point radius at the cached 3× scale.
    func page(_ geometry: MarketBagCheckoutGeometry) -> Image {
        if cachedGeometry == geometry { return backgroundCanvas }
        cachedGeometry = geometry
        buttonPath = Path(
            roundedRect: CGRect(x: 16, y: geometry.buttonY, width: geometry.cardWidth, height: 56),
            cornerRadius: 28)
        let size = CGSize(width: geometry.width, height: geometry.height)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        let renderer = UIGraphicsImageRenderer(size: size, format: format)
        let base = renderer.image { rendererContext in
            let context = rendererContext.cgContext
            Self.color(0xEFE9DD).setFill()
            context.fill(CGRect(origin: .zero, size: size))
            let scale = max(geometry.width / counter.size.width, geometry.height / counter.size.height)
            counter.draw(
                in: CGRect(
                    x: (geometry.width - counter.size.width * scale) / 2,
                    y: (geometry.height - counter.size.height * scale) / 2, width: counter.size.width * scale,
                    height: counter.size.height * scale))
            let gradient = CGGradient(
                colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                colors: [Self.color(0xFAF6EE, 0.85).cgColor, Self.color(0xFAF6EE, 0).cgColor] as CFArray,
                locations: [0, 1])!
            context.drawLinearGradient(
                gradient, start: .zero, end: CGPoint(x: 0, y: geometry.top + 120), options: [])
        }
        let input = CIImage(cgImage: base.cgImage!)
        let blurred = input.clampedToExtent().applyingFilter(
            "CIGaussianBlur", parameters: [kCIInputRadiusKey: 16 * 3]
        ).cropped(to: input.extent)
        let cgImage = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
            .createCGImage(blurred, from: input.extent)!
        background = renderer.image { rendererContext in
            let context = rendererContext.cgContext
            base.draw(at: .zero)
            context.saveGState()
            UIBezierPath(
                roundedRect: CGRect(
                    x: 16, y: geometry.cardY, width: geometry.cardWidth, height: geometry.cardHeight),
                cornerRadius: 24
            ).addClip()
            UIImage(cgImage: cgImage, scale: 3, orientation: .up).draw(at: .zero)
            Self.color(0xFFFDF8, 0.66).setFill()
            context.fill(CGRect(x: 16, y: geometry.cardY, width: geometry.cardWidth, height: geometry.cardHeight))
            context.restoreGState()
            Self.color(0xFFFFFF, 0.75).setStroke()
            let border = UIBezierPath(
                roundedRect: CGRect(
                    x: 16.5, y: geometry.cardY + 0.5, width: geometry.cardWidth - 1,
                    height: geometry.cardHeight - 1), cornerRadius: 23.5)
            border.lineWidth = 1
            border.stroke()
            for i in 0..<5 {
                Self.color(0x1F2A22, 0.06).setFill()
                context.fillEllipse(in: CGRect(x: 29, y: geometry.rowY(i) - 20, width: 40, height: 40))
            }
            func text(_ key: String, _ x: Double, _ y: Double, _ align: Double = 0) {
                guard let label = labels[key] else { return }
                label.image.draw(at: CGPoint(x: x - label.width * align, y: y + labelOffset(key)))
            }
            text("eyebrow", 24, geometry.top + 12)
            text("title", 24, geometry.top + 12 + labels["eyebrow"]!.height + 8)
            let totalY = geometry.cardY + 12 + 46 * 5
            Self.color(0x1F2A22, 0.16).setFill()
            context.fill(CGRect(x: 16, y: totalY, width: geometry.cardWidth, height: 1.0 / 3))
            text("totalLabel", 34, totalY + (46 - labels["totalLabel"]!.height) / 2)
            text("total", geometry.width - 34, totalY + (46 - labels["total"]!.height) / 2, 1)
            let shadowFormat = UIGraphicsImageRendererFormat()
            shadowFormat.scale = 3
            let hookSource = UIGraphicsImageRenderer(size: CGSize(width: 50, height: 50), format: shadowFormat)
                .image { hookContext in
                    Self.color(0x281E14, 0.25).setFill()
                    hookContext.cgContext.fillEllipse(in: CGRect(x: 16, y: 16, width: 18, height: 18))
                }
            let hookInput = CIImage(cgImage: hookSource.cgImage!)
            let hookBlur = hookInput.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 12]).cropped(
                to: hookInput.extent)
            if let shadow = CIContext().createCGImage(hookBlur, from: hookInput.extent) {
                UIImage(cgImage: shadow, scale: 3, orientation: .up).draw(
                    at: CGPoint(x: geometry.centerX + 3 - 25, y: geometry.hookY + 4 - 25))
            }
            context.saveGState()
            context.addEllipse(in: CGRect(x: geometry.centerX - 8, y: geometry.hookY - 8, width: 16, height: 16))
            context.clip()
            let hookGradient = CGGradient(
                colorsSpace: CGColorSpace(name: CGColorSpace.sRGB),
                colors: [Self.color(0xF6DFA2).cgColor, Self.color(0xC79A45).cgColor, Self.color(0x7C5A22).cgColor]
                    as CFArray, locations: [0, 0.5, 1])!
            let center = CGPoint(x: geometry.centerX - 3, y: geometry.hookY - 3)
            context.drawRadialGradient(
                hookGradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: 10,
                options: [.drawsAfterEndLocation])
            context.restoreGState()
        }
        backgroundCanvas = Image(uiImage: background)
        return backgroundCanvas
    }
}
