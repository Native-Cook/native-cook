import CoreImage
import CoreText
import SwiftUI
import UIKit

// Size-dependent art and all text are rasterized once, never in the animation loop.
final class SeasonTierMedalsResources {
    typealias H = SeasonTierMedalsTheme
    let size: CGSize
    let top: Double
    let hairline: Double
    let boundsPath: Path
    let base: UIImage
    let halo: UIImage
    let core: UIImage
    let labels: [[UIImage]]
    let claim: UIImage
    let claimY: Double
    let baseImage: Image
    let haloImage: Image
    let coreImage: Image
    let labelImages: [[Image]]
    private static let faces: [String: String] = {
        var result: [String: String] = [:]
        for name in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "BarlowCondensed-ExtraBold"] {
            guard let url = Bundle.main.url(forResource: "SeasonTierMedals-" + name, withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            if let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider), let ps = font.postScriptName {
                result[name] = ps as String
            }
        }
        return result
    }()
    private static func font(_ weight: String, _ size: Double) -> UIFont {
        let name = weight == "Display" ? "BarlowCondensed-ExtraBold" : "Inter-" + weight
        return UIFont(name: faces[name] ?? name, size: size) ?? .systemFont(ofSize: size)
    }
    private static func ui(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
    }
    private static func bitmap(_ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.opaque = false
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
    }
    private static func text(_ value: String, _ rect: CGRect, _ weight: String, _ size: Double,
                             _ line: Double, _ color: UIColor = .white, _ align: NSTextAlignment = .center,
                             _ tracking: Double = 0, _ tabular: Bool = false) {
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = align
        paragraph.minimumLineHeight = line
        paragraph.maximumLineHeight = line
        var face = font(weight, size)
        if tabular {
            let descriptor = face.fontDescriptor.addingAttributes([
                .featureSettings: [[UIFontDescriptor.FeatureKey.type: kNumberSpacingType,
                                    UIFontDescriptor.FeatureKey.selector: kMonospacedNumbersSelector]]])
            face = UIFont(descriptor: descriptor, size: size)
        }
        var attr: [NSAttributedString.Key: Any] = [.font: face, .foregroundColor: color,
            .paragraphStyle: paragraph, .baselineOffset: max(0, (line - face.lineHeight) / 2)]
        if tracking != 0 { attr[.kern] = tracking }
        (value as NSString).draw(with: rect, options: [.usesLineFragmentOrigin, .usesFontLeading], attributes: attr, context: nil)
    }
    // RN uses circular arcs; UIKit's convenience rounded rectangle smooths the corners.
    private static func roundedPath(_ rect: CGRect, _ radius: Double) -> UIBezierPath {
        let r = min(radius, min(rect.width, rect.height) / 2)
        let path = UIBezierPath()
        path.move(to: CGPoint(x: rect.minX + r, y: rect.minY))
        path.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        path.addArc(withCenter: CGPoint(x: rect.maxX - r, y: rect.minY + r), radius: r, startAngle: -.pi / 2, endAngle: 0, clockwise: true)
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
        path.addArc(withCenter: CGPoint(x: rect.maxX - r, y: rect.maxY - r), radius: r, startAngle: 0, endAngle: .pi / 2, clockwise: true)
        path.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
        path.addArc(withCenter: CGPoint(x: rect.minX + r, y: rect.maxY - r), radius: r, startAngle: .pi / 2, endAngle: .pi, clockwise: true)
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r))
        path.addArc(withCenter: CGPoint(x: rect.minX + r, y: rect.minY + r), radius: r, startAngle: .pi, endAngle: .pi * 1.5, clockwise: true)
        path.close()
        return path
    }
    private static func round(_ rect: CGRect, _ radius: Double, _ color: UIColor) {
        color.setFill(); roundedPath(rect, radius).fill()
    }
    init(size: CGSize, top: Double) {
        self.size = size; self.top = top
        let b = 1 / UIScreen.main.scale
        hairline = b
        boundsPath = Path(CGRect(origin: .zero, size: size))
        let w = size.width, k = top + 62, my = k + 186
        let rc = min(84, w * 0.21)
        let titleWidth = w - 122
        func lines(_ value: String, _ weight: String, _ fontSize: Double) -> Double {
            let face = Self.font(weight, fontSize)
            return ceil((value as NSString).boundingRect(with: CGSize(width: titleWidth, height: 200), options: .usesLineFragmentOrigin, attributes: [.font: face], context: nil).height / face.lineHeight)
        }
        claimY = k + 516 + 16 + max(44, lines("7% back on clothing and shoes", "Medium", 17) * 22 + 2 + lines("Paid as bonus points with a Season card", "Regular", 14) * 19) + 14
        let card = CGRect(x: 24, y: k + 292, width: w - 48, height: 140)
        base = Self.bitmap(size) { ctx in
            Self.text("‹", CGRect(x: 8, y: top + 3, width: 44, height: 38), "Regular", 34, 38)
            let navLine = Self.font("SemiBold", 17).lineHeight
            Self.text("Season perks", CGRect(x: 52, y: top + 2 + (44 - navLine) / 2, width: w - 104, height: navLine), "SemiBold", 17, navLine)
            let help = CGRect(x: w - 43, y: top + 11, width: 26, height: 26)
            UIColor.white.setStroke(); let ring = UIBezierPath(ovalIn: help.insetBy(dx: 1, dy: 1)); ring.lineWidth = 2; ring.stroke()
            Self.text("?", CGRect(x: w - 43, y: top + 13, width: 26, height: 22), "SemiBold", 15, 22)
            Self.round(card, 32, UIColor(red: 66/255, green: 68/255, blue: 74/255, alpha: 0.62))
            UIColor.white.withAlphaComponent(0.14).setStroke()
            let border = Self.roundedPath(card.insetBy(dx: 0.5 / UIScreen.main.scale, dy: 0.5 / UIScreen.main.scale), 32 - b / 2)
            border.lineWidth = 1 / UIScreen.main.scale; border.stroke()
            let trackW = w - 234 - 2 * b
            Self.round(CGRect(x: 48 + b, y: k + 361 + b, width: trackW, height: 10), 5, UIColor.white.withAlphaComponent(0.16))
            let mark = CGRect(x: w - 66 - b, y: k + 357 + b, width: 18, height: 18)
            UIColor.white.setFill(); UIBezierPath(ovalIn: mark).fill()
            let path = UIBezierPath()
            path.move(to: CGPoint(x: mark.minX + 1 + 16 * 0.28, y: mark.minY + 1 + 16 * 0.62))
            path.addLine(to: CGPoint(x: mark.minX + 1 + 8, y: mark.minY + 1 + 16 * 0.4))
            path.addLine(to: CGPoint(x: mark.minX + 1 + 16 * 0.72, y: mark.minY + 1 + 16 * 0.62))
            path.lineWidth = 2.2; path.lineCapStyle = .round; path.lineJoinStyle = .round
            Self.ui(0x141416).setStroke(); path.stroke()
            Self.text("Claim your rewards", CGRect(x: 0, y: k + 470, width: w, height: 32), "SemiBold", 26, 32)
            for i in 0..<2 {
                let y = k + 516 + Double(i) * 148
                // Text wraps at the same available width as RN's flex:1 column.
                let textW = w - 122
                let title = i == 0 ? "7% back on clothing and shoes" : "Double points at cafés"
                let subtitle = i == 0 ? "Paid as bonus points with a Season card" : "Every weekend until the season ends"
                                let titleAttr: [NSAttributedString.Key: Any] = [.font: Self.font("Medium", 17)]
                let titleLines = ceil((title as NSString).boundingRect(with: CGSize(width: textW, height: 200), options: .usesLineFragmentOrigin, attributes: titleAttr, context: nil).height / Self.font("Medium", 17).lineHeight)
                let subLines = ceil((subtitle as NSString).boundingRect(with: CGSize(width: textW, height: 200), options: .usesLineFragmentOrigin, attributes: [.font: Self.font("Regular", 14)], context: nil).height / Self.font("Regular", 14).lineHeight)
                let rowH = max(44, titleLines * 22 + 2 + subLines * 19)
                let rewardH = 32 + rowH + (i == 0 ? 62 : 0)
                Self.round(CGRect(x: 16, y: y, width: w - 32, height: rewardH), 26, Self.ui(0x29292d))
                let iconY = y + 16 + (rowH - 44) / 2
                Self.round(CGRect(x: 32, y: iconY, width: 44, height: 44), 22, Self.ui(i == 0 ? 0xc070d8 : 0xe0a33c))
                if i == 0 {
                    ctx.saveGState(); ctx.translateBy(x: 38, y: iconY + 8)
                    let tee = UIBezierPath()
                    tee.move(to: CGPoint(x: 9, y: 6)); tee.addLine(to: CGPoint(x: 13, y: 4))
                    tee.addCurve(to: CGPoint(x: 19, y: 4), controlPoint1: CGPoint(x: 14, y: 6.5), controlPoint2: CGPoint(x: 18, y: 6.5))
                    for point in [CGPoint(x: 23,y: 6), CGPoint(x: 26,y: 11), CGPoint(x: 22.5,y: 13), CGPoint(x: 21.5,y: 11.5), CGPoint(x: 21.5,y: 24), CGPoint(x: 10.5,y: 24), CGPoint(x: 10.5,y: 11.5), CGPoint(x: 9.5,y: 13), CGPoint(x: 6,y: 11)] { tee.addLine(to: point) }
                    tee.close(); UIColor.white.setFill(); tee.fill(); ctx.restoreGState()
                } else {
                    Self.text("×2", CGRect(x: 32, y: iconY + 11, width: 44, height: 22), "SemiBold", 16, 22)
                }
                let textY = y + 16 + (rowH - (titleLines * 22 + 2 + subLines * 19)) / 2
                Self.text(title, CGRect(x: 90, y: textY, width: textW, height: titleLines * 22), "Medium", 17, 22, .white, .left)
                Self.text(subtitle, CGRect(x: 90, y: textY + titleLines * 22 + 2, width: textW, height: subLines * 19), "Regular", 14, 19, UIColor.white.withAlphaComponent(0.6), .left)
            }
        }
        labels = (0..<3).map { i in
            [
                Self.bitmap(CGSize(width: w, height: 22)) { _ in Self.text(H.kickers[i], CGRect(x: 0,y: 0,width: w,height: 22), "Medium", 15, Self.font("Medium", 15).lineHeight, Self.ui(0xe8e8ea)) },
                Self.bitmap(CGSize(width: w, height: 56)) { _ in Self.text(H.names[i], CGRect(x: 0,y: 0,width: w,height: 56), "Display", 50, 56, Self.ui(0xf4f4f6), .center, 0.5) },
                Self.bitmap(CGSize(width: w - 96 - 2 * b, height: 26)) { _ in Self.text(H.titles[i], CGRect(x: 0,y: 0,width: w - 96 - 2 * b,height: 26), "Medium", 20, 26) },
                Self.bitmap(CGSize(width: 100, height: 24)) { _ in Self.text(H.nums[i], CGRect(x: 0,y: 0,width: 100,height: 24), "Regular", 16, 24, .white, .right, 0, true) },
                Self.bitmap(CGSize(width: w - 96 - 2 * b, height: 20)) { _ in Self.text(H.feet[i], CGRect(x: 0,y: 0,width: w - 96 - 2 * b,height: 20), "Regular", 14, 20, UIColor.white.withAlphaComponent(0.72)) }
            ]
        }
        claim = Self.bitmap(CGSize(width: w - 64, height: 48)) { _ in
            Self.round(CGRect(x: 0,y: 0,width: w - 64,height: 48), 16, Self.ui(0x0d0d0f))
            let line = Self.font("SemiBold", 17).lineHeight
            Self.text("Claim", CGRect(x: 0,y: (48 - line) / 2,width: w - 64,height: line), "SemiBold", 17, line)
        }
        func blurred(_ rx: Double, _ ry: Double, _ sigma: Double) -> UIImage {
            let source = Self.bitmap(size) { _ in
                UIColor.white.setFill()
                UIBezierPath(ovalIn: CGRect(x: w/2 - rx, y: my - ry, width: rx * 2, height: ry * 2)).fill()
            }
            guard let ci = CIImage(image: source), let filter = CIFilter(name: "CIGaussianBlur") else { return source }
            filter.setValue(ci, forKey: kCIInputImageKey)
            // The reference mask blur caps sigma at 128 device pixels.
            filter.setValue(min(sigma * source.scale, 128), forKey: kCIInputRadiusKey)
            guard let result = filter.outputImage, let cg = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!]).createCGImage(result, from: ci.extent) else { return source }
            return UIImage(cgImage: cg, scale: source.scale, orientation: .up)
        }
        halo = blurred(rc * 1.55, rc * 1.05, rc * 0.75)
        core = blurred(rc * 1.05, rc * 0.95, rc * 0.35)
        baseImage = Image(uiImage: base)
        haloImage = Image(uiImage: halo)
        coreImage = Image(uiImage: core)
        labelImages = labels.map { $0.map { Image(uiImage: $0) } }
    }
}
