import CoreImage
import CoreText
import SwiftUI
import UIKit

final class AiFeatureTourResources {
    static let shared = AiFeatureTourResources()
    private var fontNames: [String: String] = [:]
    private var lines: [String: CTLine] = [:]
    private let imageContext = CIContext(options: [.workingColorSpace: NSNull()])
    private var masks: [String: CGImage] = [:]
    private var maskOrder: [String] = []

    private init() {
        for weight in ["Regular", "Medium", "SemiBold", "Bold"] {
            if let url = Bundle.main.url(forResource: "AiFeatureTour-Inter-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
                if let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider),
                   let name = font.postScriptName { fontNames[weight] = name as String }
            }
        }
    }
    // Rasterizing in pin coordinates makes sigma scale with an icon's complete transform,
    // just as Skia's respectCTM=true mask blur does.
    func blurredRound(_ width: Double, _ height: Double, _ radius: Double, _ sigma: Double) -> CGImage? {
        let key = "\(width):\(height):\(radius):\(sigma)"
        if let mask = masks[key] { return mask }
        let pad = ceil(3 * sigma)
        let w = Int(ceil(width) + 2 * pad)
        let h = Int(ceil(height) + 2 * pad)
        guard let cg = CGContext(data: nil, width: w, height: h, bitsPerComponent: 8,
                                 bytesPerRow: w * 4, space: CGColorSpaceCreateDeviceRGB(),
                                 bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue) else { return nil }
        cg.setFillColor(CGColor(gray: 1, alpha: 1))
        cg.addPath(CGPath(roundedRect: CGRect(x: pad, y: pad, width: width, height: height),
                         cornerWidth: radius, cornerHeight: radius, transform: nil))
        cg.fillPath()
        guard let image = cg.makeImage() else { return nil }
        let blurred = CIImage(cgImage: image).applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma])
        guard let mask = imageContext.createCGImage(blurred, from: CGRect(x: 0, y: 0, width: w, height: h)) else { return nil }
        if maskOrder.count >= 96 { masks.removeValue(forKey: maskOrder.removeFirst()) }
        maskOrder.append(key)
        masks[key] = mask
        return mask
    }
    func line(_ text: String, _ weight: String, _ size: Double) -> CTLine {
        let key = "\(weight):\(size):\(text)"
        if let line = lines[key] { return line }
        let font = CTFontCreateWithName((fontNames[weight] ?? "Inter-\(weight)") as CFString, size, nil)
        let attributed = NSAttributedString(string: text, attributes: [
            kCTFontAttributeName as NSAttributedString.Key: font,
            kCTForegroundColorFromContextAttributeName as NSAttributedString.Key: true,
        ])
        let line = CTLineCreateWithAttributedString(attributed)
        lines[key] = line
        return line
    }
}
