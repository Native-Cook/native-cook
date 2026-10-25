import CoreGraphics
import SwiftUI
import UIKit

/// Small Core Graphics helpers for the jelly's baked art (drawn once, off the main thread, in
/// a UIKit image context: origin top-left, y down, like the React Native Skia canvas).
enum JellyCubeSquishPaint {
    static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    static func cg(_ hex: UInt32, _ a: CGFloat = 1) -> CGColor {
        CGColor(
            srgbRed: CGFloat((hex >> 16) & 0xff) / 255,
            green: CGFloat((hex >> 8) & 0xff) / 255,
            blue: CGFloat(hex & 0xff) / 255,
            alpha: a
        )
    }

    static func ui(_ hex: UInt32, _ a: CGFloat = 1) -> UIColor { UIColor(cgColor: cg(hex, a)) }

    static func color(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: a
        )
    }

    /// Fills a path; with `blur` > 0 it is drawn as a soft shape (Gaussian of that sigma, in pt).
    static func fill(_ c: CGContext, _ path: CGPath, _ hex: UInt32, _ a: CGFloat = 1, blur: CGFloat = 0) {
        if blur > 0 {
            blurred(c, sigma: blur, color: cg(hex, a)) { c in
                c.addPath(path)
                c.fillPath()
            }
            return
        }
        c.saveGState()
        c.setFillColor(cg(hex, a))
        c.addPath(path)
        c.fillPath()
        c.restoreGState()
    }

    /// Strokes a path with round caps and joins (and optional dashes or blur).
    static func stroke(
        _ c: CGContext, _ path: CGPath, _ hex: UInt32, width: CGFloat, _ a: CGFloat = 1,
        blur: CGFloat = 0, dash: [CGFloat]? = nil
    ) {
        let draw: (CGContext) -> Void = { c in
            c.setLineWidth(width)
            c.setLineCap(.round)
            c.setLineJoin(.round)
            if let dash { c.setLineDash(phase: 0, lengths: dash) }
            c.addPath(path)
            c.strokePath()
        }
        if blur > 0 {
            blurred(c, sigma: blur, color: cg(hex, a), draw)
            return
        }
        c.saveGState()
        c.setStrokeColor(cg(hex, a))
        draw(c)
        c.restoreGState()
    }

    /// Draws with a blur: the shapes go far to the left, opaque, and only their shadow lands here.
    static func blurred(_ c: CGContext, sigma: CGFloat, color: CGColor, _ draw: (CGContext) -> Void) {
        let scale = max(1, abs(c.ctm.a))
        let away: CGFloat = 6000
        c.saveGState()
        c.setShadow(offset: CGSize(width: away * scale, height: 0), blur: sigma * 2 * scale, color: color)
        c.translateBy(x: -away, y: 0)
        c.setFillColor(gray: 0, alpha: 1)
        c.setStrokeColor(gray: 0, alpha: 1)
        draw(c)
        c.restoreGState()
    }

    static func gradient(_ colors: [CGColor], _ stops: [CGFloat]?) -> CGGradient {
        CGGradient(colorsSpace: space, colors: colors as CFArray, locations: stops)!
    }

    static func linear(
        _ c: CGContext, _ path: CGPath, from p0: CGPoint, to p1: CGPoint,
        _ colors: [CGColor], _ stops: [CGFloat]?, evenOdd: Bool = false
    ) {
        c.saveGState()
        c.addPath(path)
        c.clip(using: evenOdd ? .evenOdd : .winding)
        c.drawLinearGradient(
            gradient(colors, stops), start: p0, end: p1,
            options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        )
        c.restoreGState()
    }

    static func radial(
        _ c: CGContext, _ path: CGPath, center: CGPoint, radius: CGFloat,
        _ colors: [CGColor], _ stops: [CGFloat]?
    ) {
        c.saveGState()
        c.addPath(path)
        c.clip()
        c.drawRadialGradient(
            gradient(colors, stops), startCenter: center, startRadius: 0, endCenter: center,
            endRadius: radius, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        )
        c.restoreGState()
    }

    static func circle(_ x: CGFloat, _ y: CGFloat, _ r: CGFloat) -> CGPath {
        CGPath(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2), transform: nil)
    }

    static func rect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat) -> CGPath {
        CGPath(rect: CGRect(x: x, y: y, width: w, height: h), transform: nil)
    }

    static func rrect(_ x: CGFloat, _ y: CGFloat, _ w: CGFloat, _ h: CGFloat, _ r: CGFloat) -> CGPath {
        CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil)
    }

    static func line(_ x0: CGFloat, _ y0: CGFloat, _ x1: CGFloat, _ y1: CGFloat) -> CGPath {
        let p = CGMutablePath()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    // MARK: Text

    static func sans(_ size: CGFloat, _ weight: UIFont.Weight) -> UIFont {
        UIFont.systemFont(ofSize: size, weight: weight)
    }

    static func serif(_ size: CGFloat) -> UIFont {
        UIFont(name: "Georgia-Bold", size: size) ?? UIFont.systemFont(ofSize: size, weight: .bold)
    }

    static func width(_ s: String, _ font: UIFont, kern: CGFloat = 0) -> CGFloat {
        var attrs: [NSAttributedString.Key: Any] = [.font: font]
        if kern != 0 { attrs[.kern] = kern }
        return (s as NSString).size(withAttributes: attrs).width
    }

    /// Text with its baseline's left end at (x, y), in the current UIKit context.
    static func text(_ s: String, x: CGFloat, y: CGFloat, _ font: UIFont, _ hex: UInt32, _ a: CGFloat = 1, kern: CGFloat = 0) {
        var attrs: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: ui(hex, a)]
        if kern != 0 { attrs[.kern] = kern }
        (s as NSString).draw(at: CGPoint(x: x, y: y - font.ascender), withAttributes: attrs)
    }

    /// Tracked text; align 0 = left, 0.5 = centre, 1 = right (the trailing track is not counted).
    static func spaced(_ s: String, x: CGFloat, y: CGFloat, _ font: UIFont, _ hex: UInt32, _ a: CGFloat, track: CGFloat, align: CGFloat = 0) {
        let total = width(s, font, kern: track) - track
        text(s, x: x - total * align, y: y, font, hex, a, kern: track)
    }

    // MARK: Grain

    /// A 128 px tile of soft grey noise, made once.
    static let grainTile: CGImage? = {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n)
        var s: UInt32 = 0x9E37_79B9
        for i in 0..<(n * n) {
            s ^= s << 13
            s ^= s >> 17
            s ^= s << 5
            let v = Double(s % 1000) / 1000
            bytes[i] = UInt8(max(0, min(255, 128 + (v - 0.5) * 200)))
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: n,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent
        )
    }()

    /// A 256 × 64 px tile of brushed-metal streaks: noise that changes fast across and slowly along.
    static let brushTile: CGImage? = {
        let w = 256, h = 64
        var rows = [Double](repeating: 0, count: h * 9)
        var s: UInt32 = 0x1234_5677
        for i in 0..<rows.count {
            s ^= s << 13
            s ^= s >> 17
            s ^= s << 5
            rows[i] = Double(s % 1000) / 1000
        }
        var bytes = [UInt8](repeating: 0, count: w * h)
        for y in 0..<h {
            for x in 0..<w {
                // Eight knots along each row (periodic), blended smoothly.
                let u = Double(x) / Double(w) * 8
                let k = Int(u)
                let f = u - Double(k)
                let e = f * f * (3 - 2 * f)
                let a = rows[y * 9 + k % 8], b = rows[y * 9 + (k + 1) % 8]
                let v = a + (b - a) * e
                bytes[y * w + x] = UInt8(max(0, min(255, v * 255)))
            }
        }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(
            width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w,
            space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0),
            provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent
        )
    }()

    /// Brushed streaks over a rect, along x.
    static func brush(_ c: CGContext, _ r: CGRect, alpha: CGFloat) {
        guard let tile = brushTile else { return }
        c.saveGState()
        c.clip(to: r)
        c.setAlpha(alpha)
        c.draw(tile, in: CGRect(x: r.minX, y: r.minY, width: 256, height: 32), byTiling: true)
        c.restoreGState()
    }

    /// Faint static grain over a rect (soft light), shifted per seed so neighbours differ.
    static func grain(_ c: CGContext, _ r: CGRect, alpha: CGFloat, seed: Int) {
        guard let tile = grainTile else { return }
        c.saveGState()
        c.clip(to: r)
        c.setBlendMode(.softLight)
        c.setAlpha(alpha)
        let off = CGFloat((seed * 37) % 128)
        c.draw(tile, in: CGRect(x: r.minX - off, y: r.minY - off, width: 128, height: 128), byTiling: true)
        c.restoreGState()
    }

    /// Draws into an image at `scale` pixels per point (safe off the main thread).
    static func bake(_ size: CGSize, scale: CGFloat, _ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            draw(ctx.cgContext)
        }
    }
}
