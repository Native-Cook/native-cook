import CoreText
import MetalKit
import UIKit

/// Original face textures and OFL Inter fonts, loaded from this block's resources.
enum MetacardUnifyFanAssets {
    static let copy = ["Unify the limit of", "all your cards", "Meet the first", "Metacard", "onefold", "Tap the hub to unify", "Tap the card to split"]
    private static let fonts: [String] = ["Bold", "Regular"].map { weight in
        let url = Bundle.main.url(forResource: "MetacardUnifyFan-Inter-\(weight)", withExtension: "ttf")!
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        let provider = CGDataProvider(url: url as CFURL)!
        return CGFont(provider)!.postScriptName! as String
    }

    static func image(_ name: String, loader: MTKTextureLoader) throws -> MTLTexture {
        let url = Bundle.main.url(forResource: "MetacardUnifyFan-\(name)", withExtension: "png")!
        return try loader.newTexture(URL: url, options: [.SRGB: false, .origin: MTKTextureLoader.Origin.topLeft])
    }

    static func text(_ index: Int, loader: MTKTextureLoader) throws -> MTLTexture {
        let size = index > 4 ? 13.0 : index == 4 ? 24.0 : 28.0
        let tracking = index > 4 ? 0.2 : index == 4 ? -0.6 : -0.4
        let font = UIFont(name: fonts[index > 4 ? 1 : 0], size: size)!
        let ink = UIColor(red: 244 / 255, green: 244 / 255, blue: 242 / 255, alpha: 1)
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: tracking, .foregroundColor: ink]
        let string = NSAttributedString(string: copy[index], attributes: attributes)
        let bounds = string.boundingRect(with: CGSize(width: 402, height: 60), options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        let mark = index > 4 ? 10.0 : index == 4 ? 22.0 : 0.0
        let gap = mark > 0 ? 8.0 : 0.0
        let left = (402 - bounds.width - mark - gap) / 2
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: CGSize(width: 402, height: 60), format: format).image { context in
            string.draw(at: CGPoint(x: left + mark + gap, y: (60 - bounds.height) / 2))
            if mark > 0 {
                let cg = context.cgContext
                cg.translateBy(x: left + mark / 2, y: 30 + (index == 4 ? 1 : 0))
                cg.scaleBy(x: mark / 24, y: mark / 24)
                cg.addPath(markPath())
                cg.setFillColor(ink.cgColor)
                cg.drawPath(using: .eoFill)
            }
        }
        return try loader.newTexture(cgImage: image.cgImage!, options: [.SRGB: false])
    }

    private static func markPath() -> CGPath {
        let a = 10.5
        let b = a * tan(Double.pi / 8)
        let points = [CGPoint(x: -b, y: -a), CGPoint(x: b, y: -a), CGPoint(x: a, y: -b), CGPoint(x: a, y: b),
                      CGPoint(x: b, y: a), CGPoint(x: -b, y: a), CGPoint(x: -a, y: b), CGPoint(x: -a, y: -b)]
        let path = CGMutablePath()
        let trim = tan(Double.pi / 8)
        for i in 0..<8 {
            let p = points[i]
            let previous = points[(i + 7) % 8]
            let next = points[(i + 1) % 8]
            let before = hypot(previous.x - p.x, previous.y - p.y)
            let after = hypot(next.x - p.x, next.y - p.y)
            let start = CGPoint(x: p.x + (previous.x - p.x) / before * trim, y: p.y + (previous.y - p.y) / before * trim)
            let end = CGPoint(x: p.x + (next.x - p.x) / after * trim, y: p.y + (next.y - p.y) / after * trim)
            if i == 0 { path.move(to: start) } else { path.addLine(to: start) }
            path.addQuadCurve(to: end, control: p)
        }
        path.closeSubpath()
        let slot = CGMutablePath()
        slot.move(to: CGPoint(x: -4.75, y: -3.95))
        slot.addLine(to: CGPoint(x: 4.75, y: -3.95))
        slot.addQuadCurve(to: CGPoint(x: 6.25, y: -2.45), control: CGPoint(x: 6.25, y: -3.95))
        slot.addLine(to: CGPoint(x: 6.25, y: 2.45))
        slot.addQuadCurve(to: CGPoint(x: 4.75, y: 3.95), control: CGPoint(x: 6.25, y: 3.95))
        slot.addLine(to: CGPoint(x: -4.75, y: 3.95))
        slot.addQuadCurve(to: CGPoint(x: -6.25, y: 2.45), control: CGPoint(x: -6.25, y: 3.95))
        slot.addLine(to: CGPoint(x: -6.25, y: -2.45))
        slot.addQuadCurve(to: CGPoint(x: -4.75, y: -3.95), control: CGPoint(x: -6.25, y: -3.95))
        slot.closeSubpath()
        path.addPath(slot, transform: CGAffineTransform(rotationAngle: -22 * .pi / 180))
        return path
    }
}
