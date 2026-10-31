import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Retains decoded artwork, measured text bitmaps and blurred masks outside the frame loop.
/// Text sizes and mask dimensions are points; rasterization uses the reference display's 3× scale.
final class ParcelLockerPickupResources {
    static let shared = ParcelLockerPickupResources()
    private static var scaled: [Int: ParcelLockerPickupResources] = [:]

    /// Reuses a label set for each quantized Dynamic Type scale instead of rasterizing per frame.
    static func forScale(_ scale: Double) -> ParcelLockerPickupResources {
        if abs(scale - 1) < 0.0001 {
            return shared
        }
        let key = Int(scale * 10000)
        if let cached = scaled[key] {
            return cached
        }
        let resource = ParcelLockerPickupResources(fontScale: scale)
        scaled[key] = resource
        return resource
    }
    let fontScale: Double
    let wall: UIImage
    let compartment: UIImage
    let parcel: UIImage
    private var masks: [String: UIImage] = [:]
    private let maskContext = CIContext(options: [
        .workingColorSpace: NSNull(), .outputColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!,
    ])

    /// Caches a padded white alpha mask; sigma is in points and the Core Image blur works in pixels.
    func mask(_ width: Double, _ height: Double, _ radius: Double, _ sigma: Double) -> UIImage {
        let key = "\(width)/\(height)/\(radius)/\(sigma)"
        if let image = masks[key] {
            return image
        }
        let padding = ceil(sigma * 4)
        let size = CGSize(width: width + padding * 2, height: height + padding * 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.preferredRange = .standard
        let source = UIGraphicsImageRenderer(size: size, format: format).image { _ in
            UIColor.white.setFill()
            UIBezierPath(
                roundedRect: CGRect(x: padding, y: padding, width: width, height: height), cornerRadius: radius
            ).fill()
        }
        guard let bitmap = source.cgImage else {
            return source
        }
        let input = CIImage(cgImage: bitmap)
        let output = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * 3]).cropped(
            to: input.extent)
        let image =
            maskContext.createCGImage(output, from: output.extent).map {
                UIImage(cgImage: $0, scale: 3, orientation: .up)
            } ?? source
        masks[key] = image
        return image
    }

    /// Bitmap plus its unpadded layout size, used by drawing and accessible hit areas.
    struct Label {
        let image: UIImage
        let size: CGSize
    }
    var labels: [String: Label] = [:]

    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(
            .sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
            blue: Double(hex & 255) / 255, opacity: alpha)
    }

    static func uiColor(_ hex: Int) -> UIColor {
        UIColor(
            red: CGFloat(hex >> 16 & 255) / 255, green: CGFloat(hex >> 8 & 255) / 255, blue: CGFloat(hex & 255) / 255,
            alpha: 1)
    }
    private static let registeredFonts: Void = {
        for face in ["Archivo-Medium", "Archivo-SemiBold", "Archivo-ExtraBold", "JetBrainsMono-Medium"] {
            if let url = Bundle.main.url(forResource: "ParcelLockerPickup-\(face)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static let artwork: [String: UIImage] = {
        func load(_ name: String, _ fileExtension: String) -> UIImage {
            guard let url = Bundle.main.url(forResource: "ParcelLockerPickup-\(name)", withExtension: fileExtension),
                let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                let bitmap = CGImageSourceCreateImageAtIndex(
                    source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            else {
                return UIImage()
            }
            return UIImage(cgImage: bitmap)
        }
        return [
            "wall": load("wall", "jpg"), "compartment": load("compartment", "webp"), "parcel": load("parcel", "webp"),
        ]
    }()
    init(fontScale: Double = 1) {
        self.fontScale = fontScale
        _ = Self.registeredFonts
        wall = Self.artwork["wall"]!
        compartment = Self.artwork["compartment"]!
        parcel = Self.artwork["parcel"]!
        makeLabels()
    }

    /// Rasterizes one TextKit label with the reference's tracking, line height and scaling caps.
    /// Font size, optional line height and tracking are points; measured bounds round up to ⅓ pt.
    private func label(
        _ key: String, _ text: String, _ face: String, _ size: Double, _ height: Double? = nil, _ tracking: Double = 0,
        _ color: Int = 0xF4EFE8
    ) {
        let cap =
            key == "titleA" || key == "titleB"
            ? 1.2 : key == "eyebrow" || key == "subA" || key == "subB" ? 1.3 : Double.greatestFiniteMagnitude
        let multiplier = min(fontScale, cap)
        let font = UIFont(name: face, size: size * multiplier) ?? UIFont.systemFont(ofSize: size * multiplier)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: Self.uiColor(color)]
        if tracking != 0 {
            attributes[.kern] = tracking
        }
        if let height {
            let lineHeight = height * multiplier
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight {
                attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
            }
        }
        // Match RCTTextLayoutManager: TextKit, no leading or container padding,
        // and a measured box rounded upward at the display's 3x pixel scale.
        let storage = NSTextStorage(string: text, attributes: attributes)
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 10000, height: 10000))
        container.lineFragmentPadding = 0
        container.lineBreakMode = .byClipping
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)
        layout.ensureLayout(for: container)
        let measured = layout.usedRect(for: container).size
        let width = ceil(measured.width * 3) / 3
        let height = ceil(measured.height * 3) / 3
        container.size = CGSize(width: width, height: height)
        layout.ensureLayout(for: container)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let image = UIGraphicsImageRenderer(size: CGSize(width: width + 8, height: height + 8), format: format).image {
            _ in

            layout.drawGlyphs(forGlyphRange: layout.glyphRange(for: container), at: CGPoint(x: 4, y: 4))
        }
        labels[key] = Label(image: image, size: CGSize(width: width, height: height))
    }

    /// Builds both sheet faces and header variants once, retaining their measured text heights.
    private func makeLabels() {
        label("eyebrow", "NORTHWIND LOCKERS · LOBBY", "JetBrainsMono-Medium", 11, nil, 1.6, 0xA39A90)
        label("titleA", "Locker 14B", "Archivo-ExtraBold", 36, 40, -0.7)
        label("titleB", "Enjoy your parcel", "Archivo-ExtraBold", 36, 40, -0.7)
        label("subA", "Middle column, third row · blinking amber", "Archivo-Medium", 15, 20, 0, 0xA39A90)
        label("subB", "Locker 14B is closed and free again", "Archivo-Medium", 15, 20, 0, 0xA39A90)
        label("brand", "NORTHWIND PARCELS", "JetBrainsMono-Medium", 10.5, nil, 1.4, 0xA39A90)
        label("weight", "MEDIUM · 2.4 KG", "JetBrainsMono-Medium", 10.5, nil, 1.4, 0xA39A90)
        label("ready", "Your parcel is ready", "Archivo-SemiBold", 22, 28)
        label("order", "Order 48-1173 · picked up by 9 Nov", "Archivo-Medium", 14, 19, 0, 0xA39A90)
        for digit in ["4", "8", "1", "7"] {
            label(digit, digit, "JetBrainsMono-Medium", 24)
        }
        label("open", "Open locker", "Archivo-SemiBold", 17, nil, 0, 0x1B1308)
        label("picked", "Picked up", "Archivo-ExtraBold", 26, nil, -0.4)
        label("closed", "6:42 PM · Locker 14B closed", "Archivo-Medium", 14, 19, 0, 0xA39A90)
        label("item", "Order 48-1173", "Archivo-SemiBold", 17)
        label("meta", "1 parcel · medium · 2.4 kg", "Archivo-Medium", 13, nil, 0, 0xA39A90)
        label("done", "Done", "Archivo-SemiBold", 16)
    }
}
