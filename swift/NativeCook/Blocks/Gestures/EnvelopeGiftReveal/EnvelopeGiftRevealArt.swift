import CoreText
import SwiftUI
import UIKit

enum EnvelopeGiftRevealPalette {
    static let table = Color(red: 0x1D / 255, green: 0x1B / 255, blue: 0x18 / 255)
    static let ink = Color(red: 0xF3 / 255, green: 0xEC / 255, blue: 0xDF / 255)
    static let mute = ink.opacity(0.58)
    static let faint = ink.opacity(0.36)
    static let paper = Color(red: 0xEF / 255, green: 0xE6 / 255, blue: 0xD5 / 255)
    static let paperHigh = Color(red: 0xF7 / 255, green: 0xF0 / 255, blue: 0xE2 / 255)
    static let paperLow = Color(red: 0xDC / 255, green: 0xCF / 255, blue: 0xB8 / 255)
    static let paperInside = Color(red: 0xCD / 255, green: 0xBF / 255, blue: 0xA5 / 255)
    static let crease = Color(red: 96 / 255, green: 74 / 255, blue: 40 / 255).opacity(0.3)
    static let pen = Color(red: 0x22 / 255, green: 0x31 / 255, blue: 0x4B / 255)
    static let wax = Color(red: 0x7C / 255, green: 0x1E / 255, blue: 0x22 / 255)
    static let waxHigh = Color(red: 0xB2 / 255, green: 0x3A / 255, blue: 0x3A / 255)
    static let waxLow = Color(red: 0x4C / 255, green: 0x0F / 255, blue: 0x13 / 255)
    static let card = Color(red: 0xF6 / 255, green: 0xF0 / 255, blue: 0xE4 / 255)
    static let cardEdge = Color(red: 80 / 255, green: 60 / 255, blue: 30 / 255).opacity(0.18)
    static let cardMute = Color(red: 42 / 255, green: 34 / 255, blue: 25 / 255).opacity(0.56)
    static let letterpress = Color(red: 0x2E / 255, green: 0x4A / 255, blue: 0x3C / 255)
    static let cta = ink
    static let ctaInk = table
}

enum EnvelopeGiftRevealAssets {
    private static let registered: Void = {
        for file in [
            "EnvelopeGiftReveal-InstrumentSerif-Regular",
            "EnvelopeGiftReveal-CoveredByYourGrace",
            "EnvelopeGiftReveal-Inter-Medium",
            "EnvelopeGiftReveal-Inter-SemiBold",
        ] {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("Missing Envelope Gift Reveal font: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    private static let tableCache = NSCache<NSString, UIImage>()
    private static let paperCache = NSCache<NSString, UIImage>()

    static func registerFonts() { _ = registered }
    static func serif(_ size: CGFloat) -> Font { registerFonts(); return .custom("InstrumentSerif-Regular", fixedSize: size) }
    static func hand(_ size: CGFloat) -> Font { registerFonts(); return .custom("CoveredByYourGrace", fixedSize: size) }
    static func ui(_ size: CGFloat) -> Font { registerFonts(); return .custom("Inter-Medium", fixedSize: size) }
    static func uiBold(_ size: CGFloat) -> Font { registerFonts(); return .custom("Inter-SemiBold", fixedSize: size) }

    static func table(size: CGSize) -> UIImage {
        let key = "\(Int(size.width.rounded()))x\(Int(size.height.rounded()))" as NSString
        if let cached = tableCache.object(forKey: key) { return cached }
        let format = UIGraphicsImageRendererFormat.preferred()
        format.scale = UIScreen.main.scale
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let context = renderer.cgContext
            context.setFillColor(UIColor(red: 0x1D / 255, green: 0x1B / 255, blue: 0x18 / 255, alpha: 1).cgColor)
            context.fill(CGRect(origin: .zero, size: size))

            let centre = CGPoint(x: size.width * 0.22, y: size.height * 0.12)
            let colors = [
                UIColor(red: 0.30, green: 0.235, blue: 0.16, alpha: 0.72).cgColor,
                UIColor(red: 0.18, green: 0.14, blue: 0.10, alpha: 0.28).cgColor,
                UIColor.clear.cgColor,
            ] as CFArray
            let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors, locations: [0, 0.42, 1])!
            context.drawRadialGradient(gradient, startCenter: centre, startRadius: 0, endCenter: centre, endRadius: size.width * 0.95, options: [])

            context.setLineWidth(0.34)
            for x in stride(from: 0.0, through: size.width, by: 1.3) {
                context.setStrokeColor(UIColor.white.withAlphaComponent(Int(x * 10).isMultiple(of: 3) ? 0.012 : 0.005).cgColor)
                context.move(to: CGPoint(x: x, y: 0)); context.addLine(to: CGPoint(x: x, y: size.height)); context.strokePath()
            }
            for y in stride(from: 0.0, through: size.height, by: 1.45) {
                context.setStrokeColor(UIColor.black.withAlphaComponent(Int(y * 10).isMultiple(of: 4) ? 0.02 : 0.008).cgColor)
                context.move(to: CGPoint(x: 0, y: y)); context.addLine(to: CGPoint(x: size.width, y: y)); context.strokePath()
            }

            var state: UInt64 = 0xF4A7C15
            for _ in 0 ..< Int(size.width * size.height / 45) {
                state = state &* 6_364_136_223_846_793_005 &+ 1
                let x = CGFloat((state >> 16) & 0xFFFF) / 65535 * size.width
                state = state &* 6_364_136_223_846_793_005 &+ 1
                let y = CGFloat((state >> 16) & 0xFFFF) / 65535 * size.height
                let alpha = CGFloat((state >> 48) & 0xFF) / 255 * 0.035
                context.setFillColor(UIColor(white: state.isMultiple(of: 2) ? 1 : 0, alpha: alpha).cgColor)
                context.fill(CGRect(x: x, y: y, width: 0.65, height: 0.65))
            }
        }
        tableCache.setObject(image, forKey: key)
        return image
    }

    static func paper(size: CGSize, seed: UInt64) -> UIImage {
        let key = "\(Int(size.width))x\(Int(size.height))-\(seed)" as NSString
        if let cached = paperCache.object(forKey: key) { return cached }
        let format = UIGraphicsImageRendererFormat.preferred()
        format.scale = UIScreen.main.scale
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let context = renderer.cgContext
            var state = seed &+ 0x9E3779B97F4A7C15
            for _ in 0 ..< Int(size.width * size.height / 18) {
                state = state &* 2_862_933_555_777_941_757 &+ 3_037_000_493
                let x = CGFloat((state >> 16) & 0xFFFF) / 65535 * size.width
                state = state &* 2_862_933_555_777_941_757 &+ 3_037_000_493
                let y = CGFloat((state >> 16) & 0xFFFF) / 65535 * size.height
                let alpha = 0.008 + CGFloat((state >> 48) & 0xFF) / 255 * 0.035
                context.setFillColor(UIColor(red: 0.3, green: 0.22, blue: 0.1, alpha: alpha).cgColor)
                let side = state.isMultiple(of: 9) ? 1.1 : 0.45
                context.fillEllipse(in: CGRect(x: x, y: y, width: side, height: side * 0.55))
            }
            context.setLineWidth(0.3)
            for index in 0 ..< 36 {
                state = state &* 2_862_933_555_777_941_757 &+ 3_037_000_493
                let x = CGFloat((state >> 16) & 0xFFFF) / 65535 * size.width
                let y = CGFloat((state >> 32) & 0xFFFF) / 65535 * size.height
                context.setStrokeColor(UIColor(red: 0.38, green: 0.28, blue: 0.14, alpha: 0.045).cgColor)
                context.move(to: CGPoint(x: x, y: y))
                context.addLine(to: CGPoint(x: min(size.width, x + CGFloat(4 + index % 8)), y: y + CGFloat(index % 3 - 1)))
                context.strokePath()
            }
        }
        paperCache.setObject(image, forKey: key)
        return image
    }
}
