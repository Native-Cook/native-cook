import CoreImage
import CoreText
import ImageIO
import SwiftUI
import UIKit

final class IslandSleepDialResources {
    static let shared = IslandSleepDialResources()
    struct Label {
        let image: Image
        let size: CGSize
        let width: Double
    }
    private var labels: [String: Label] = [:]
    private lazy var ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
    private var glows: [String: Image] = [:]
    private(set) var halos: [Image] = []
    init() {
        for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "IslandSleepDial-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        var haloCount = 61
        #if DEBUG
        // A pinned frame cannot drag, so it only needs the resting halo.
        if UserDefaults.standard.object(forKey: "islandSleepDialTime") != nil { haloCount = 1 }
        #endif
        halos = (0..<haloCount).map { index in
            guard let url = Bundle.main.url(forResource: "IslandSleepDial-halo-\(index)", withExtension: "png"),
                  let source = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
            else { return Image(uiImage: UIImage()) }
            return Image(uiImage: UIImage(cgImage: image, scale: 3, orientation: .up))
        }
    }
    func label(_ text: String, _ weight: String, _ size: Double, _ hex: Int, tracking: Double = 0, tabular: Bool = false) -> Label {
        let key = "\(text)|\(weight)|\(size)|\(hex)|\(tracking)|\(tabular)"
        if let cached = labels[key] { return cached }
        let name = weight == "SemiBold" ? "Inter-SemiBold" : weight == "Icon" ? "Ionicons" : "Inter24pt-\(weight)"
        var font = UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        if tabular {
            let descriptor = font.fontDescriptor.addingAttributes([.featureSettings: [[UIFontDescriptor.FeatureKey.type: 6, .selector: 0]]])
            font = UIFont(descriptor: descriptor, size: size)
        }
        var attributes: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: IslandSleepDialArt.uiColor(hex),
        ]
        if tracking != 0 { attributes[.kern] = tracking }
        let string = NSAttributedString(string: text, attributes: attributes)
        let width = string.size().width
        let dimensions = CGSize(width: ceil(width) + 4, height: ceil(font.lineHeight) + 4)
        let format = UIGraphicsImageRendererFormat(); format.scale = 3
        let bitmap = UIGraphicsImageRenderer(size: dimensions, format: format).image { _ in
            string.draw(at: CGPoint(x: 2, y: 2))
        }
        let label = Label(image: Image(uiImage: bitmap), size: dimensions, width: width)
        labels[key] = label
        return label
    }
    // Each Gaussian result is generated once; there is no Core Image work in the frame loop.
    func glow(_ radius: Double, sigma: Double, color: Int) -> Image {
        let key = "\(radius)|\(sigma)|\(color)"
        if let image = glows[key] { return image }
        let name: String? = sigma == 10 && color == 0xFFD36B
            ? "window-\(Int((radius / 0.32).rounded()))"
            : sigma == 4 && color == 0xFFF2A6 ? "firefly-\(Int(radius))" : nil
        if let name,
           let url = Bundle.main.url(forResource: "IslandSleepDial-\(name)", withExtension: "png"),
           let source = CGImageSourceCreateWithURL(url as CFURL, nil),
           let cg = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary) {
            let image = Image(uiImage: UIImage(cgImage: cg, scale: 3, orientation: .up))
            glows[key] = image
            return image
        }
        // Uncommon display widths use this one-time fallback, cached before animation begins.
        let pad = sigma * 4
        let side = (radius + pad) * 2
        let format = UIGraphicsImageRendererFormat(); format.scale = 3
        let bitmap = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { r in
            r.cgContext.setFillColor(IslandSleepDialArt.uiColor(color).cgColor)
            r.cgContext.fillEllipse(in: CGRect(x: pad, y: pad, width: radius * 2, height: radius * 2))
        }
        let source = CIImage(cgImage: bitmap.cgImage!)
        let blurred = source.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * 3])
        let cg = ci.createCGImage(blurred, from: source.extent)!
        let image = Image(uiImage: UIImage(cgImage: cg, scale: 3, orientation: .up))
        glows[key] = image
        return image
    }
    func prepare(_ s: Double) {
        _ = glow(s * 0.32, sigma: 10, color: 0xFFD36B)
        _ = glow(3, sigma: 4, color: 0xFFF2A6)
        _ = glow(2, sigma: 4, color: 0xFFF2A6)

        _ = label("Tonight", "SemiBold", 14, 0x9FA9E0, tracking: 0.5)
        _ = label("Sleep well", "Bold", 28, 0x2A3470)
        _ = label("Rain on a tin roof", "SemiBold", 13, 0xC9D2FF)
        for t in ["Set alarm", "Alarm set"] { _ = label(t, "SemiBold", 17, 0x141A3C) }
        for q in 16...44 {
            let h = Double(q) / 4
            _ = label(IslandSleepDialMotion.duration(h), "Bold", 44, 0xFFFFFF, tabular: true)
            _ = label("Bedtime 11:00 PM  ·  Wake \(IslandSleepDialMotion.wake(h))", "Regular", 14, 0xC9D2FF)
        }
        _ = label(String(UnicodeScalar(62570)!), "Icon", 16, 0xC9D2FF)
        _ = label(String(UnicodeScalar(61709)!), "Icon", 20, 0x141A3C)
    }
}
