import CoreImage
import CoreText
import SwiftUI
import UIKit

/// Static page and shaped text are cached; the glass samples exactly this page, excluding the live event row.
final class IslandGlassOrbArtwork {
    struct TextRun {
        var path: Path, width: Double, baseline: Double
    }
    static let register: Void = {
        for name in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Display-Light"] {
            if let url = Bundle.main.url(forResource: "IslandGlassOrb-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static let fonts: [String: CGFont] = {
        var result: [String: CGFont] = [:]
        for name in ["Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Display-Light"] {
            if let url = Bundle.main.url(forResource: "IslandGlassOrb-" + name, withExtension: "ttf"),
               let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) { result[name] = font }
        }
        return result
    }()
    private static let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
    private static var cache: [String: IslandGlassOrbArtwork] = [:]
    let page: UIImage, blur: UIImage
    let mattes: [UIImage]
    let text: [String: TextRun], words: [(run: TextRun, x: Double, y: Double)]
    let height: Double
    static func get(height: Double, scale: Double, reply: String) -> IslandGlassOrbArtwork {
        let key = "\(height)-\(scale)-\(reply)"
        if let item = cache[key] { return item }
        let item = IslandGlassOrbArtwork(height: height, scale: scale, reply: reply)
        // A single geometry is resident for this full-screen block.
        cache = [key: item]; return item
    }
    static func ink(_ a: Double = 1) -> Color { Color(.sRGB, red: 247 / 255, green: 247 / 255, blue: 245 / 255, opacity: a) }
    static func shape(_ string: String, size: Double, weight: Int = 400, track: Double = 0, display: Bool = false, tnum: Bool = false) -> TextRun {
        _ = register
        let name = display ? "Inter-Display-Light" : weight == 600 ? "Inter-SemiBold" : weight == 500 ? "Inter-Medium" : "Inter-Regular"
        let base = Self.fonts[name].map { CTFontCreateWithGraphicsFont($0, size, nil, nil) } ?? CTFontCreateUIFontForLanguage(.system, size, nil)!
        let features = CTFontDescriptorCreateWithAttributes([kCTFontFeatureSettingsAttribute: [[kCTFontFeatureTypeIdentifierKey: kNumberSpacingType, kCTFontFeatureSelectorIdentifierKey: kMonospacedNumbersSelector]]] as CFDictionary)
        let font = tnum ? CTFontCreateCopyWithAttributes(base, size, nil, features) : base
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [NSAttributedString.Key(kCTFontAttributeName as String): font, .kern: track * size]))
        let result = CGMutablePath()
        for object in CTLineGetGlyphRuns(line) as NSArray {
            let run = object as! CTRun, n = CTRunGetGlyphCount(run)
            let attrs = CTRunGetAttributes(run) as NSDictionary
            let face = attrs[kCTFontAttributeName] as! CTFont
            var glyphs = [CGGlyph](repeating: 0, count: n), points = [CGPoint](repeating: .zero, count: n)
            CTRunGetGlyphs(run, CFRange(location: 0, length: 0), &glyphs)
            CTRunGetPositions(run, CFRange(location: 0, length: 0), &points)
            let baseline = (CTFontGetAscent(face) - CTFontGetDescent(face)) / 2
            for i in 0..<n {
                if let p = CTFontCreatePathForGlyph(face, glyphs[i], nil) {
                    result.addPath(p, transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: points[i].x, ty: baseline - points[i].y))
                }
            }
        }
        return TextRun(path: Path(result), width: CTLineGetTypographicBounds(line, nil, nil, nil), baseline: (CTFontGetAscent(font) - CTFontGetDescent(font)) / 2)
    }
    private static func gaussian(_ image: UIImage, sigma: Double) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let src = CIImage(cgImage: cg)
        let result = src.clampedToExtent().applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * image.scale]).cropped(to: src.extent)
        guard let out = ci.createCGImage(result, from: src.extent) else { return image }
        return UIImage(cgImage: out, scale: image.scale, orientation: .up)
    }
    private static func renderer(_ size: CGSize, _ scale: Double, _ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = scale; format.opaque = true; format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
    }
    private init(height: Double, scale: Double, reply: String) {
        _ = Self.register; self.height = height
        mattes = ["igo-ring-matte", "igo-ring-spec", "igo-floor-matte"].map { name in Bundle.main.url(forResource: "IslandGlassOrb-" + name, withExtension: "png").flatMap { UIImage(contentsOfFile: $0.path) } ?? UIImage() }
        text = [
            "ask": Self.shape("Ask", size: 24, track: -0.013),
            "day": Self.shape("THURSDAY", size: 13, weight: 600, track: 0.06),
            "date": Self.shape("12", size: 32, weight: 300, track: -0.021, display: true, tnum: true),
            "tomorrow": Self.shape("TOMORROW", size: 13, weight: 600, track: 0.06),
            "nori": Self.shape("Nori", size: 13, weight: 500), "calendar": Self.shape("Calendar", size: 13, weight: 500),
            "events": Self.shape("2 Events", size: 15, weight: 600, track: -0.01),
            "range": Self.shape("6:00 – 7:30", size: 13, tnum: true), "pm": Self.shape("PM", size: 13, weight: 500, track: 0.02),
            "time": Self.shape("12:01", size: 17, weight: 600, track: -0.013, tnum: true)
        ]
        var x = 45.0, y = 66.0
        let space = Self.shape("a a", size: 24, track: -0.013).width - 2 * Self.shape("a", size: 24, track: -0.013).width
        words = reply.split(whereSeparator: { $0.isWhitespace }).map {
            let run = Self.shape(String($0), size: 24, track: -0.013)
            if x + run.width > 357 { x = 45; y += 29 }
            let item = (run: run, x: x, y: y); x += run.width + space; return item
        }
        let size = CGSize(width: 402, height: height)
        let wall = Bundle.main.url(forResource: "IslandGlassOrb-wallpaper", withExtension: "jpg").flatMap { UIImage(contentsOfFile: $0.path) } ?? UIImage()
        let wallpaper = Self.wallpaper(wall, height: height, scale: scale)
        let frosted = Self.gaussian(wallpaper, sigma: 12)
        let texts = text
        page = Self.renderer(size, scale) { c in
            wallpaper.draw(at: .zero)
            // Same static 3% hash film as GRAIN; every grain cell is one third of a canonical point.
            if let grain = Self.grain(height: height) {
                c.saveGState()
                c.interpolationQuality = .none
                UIImage(cgImage: grain).draw(in: CGRect(origin: .zero, size: size), blendMode: .normal, alpha: 0.03)
                c.restoreGState()
            }
            Self.dock(c, wallpaper: frosted, height: height)
            for wx in [28.0, 212.0] {
                let rect = CGRect(x: wx, y: 79, width: 162, height: 161)
                c.saveGState(); c.addPath(CGPath(roundedRect: rect, cornerWidth: 24, cornerHeight: 24, transform: nil)); c.clip()
                frosted.draw(at: .zero); c.setFillColor(UIColor.white.withAlphaComponent(0.16).cgColor); c.fill(rect); c.restoreGState()
                Self.rim(c, x: wx, top: 0.55, bottom: 0.16)
            }
            Self.round(c, CGRect(x: 46, y: 106, width: 127, height: 44), 22, UIColor.white.withAlphaComponent(0.14))
            Self.icon(c, "nori-mark", x: 60, y: 114)
            Self.draw(c, texts["ask"]!, x: 98, cy: 128, alpha: 0.85)
            for (i, cx) in [74.0, 142.0].enumerated() {
                Self.round(c, CGRect(x: cx - 29, y: 166, width: 58, height: 58), 29, UIColor.white.withAlphaComponent(0.16))
                c.saveGState(); c.setAlpha(0.85); Self.icon(c, i == 0 ? "icon-camera" : "icon-voice-bars", x: cx - 13, y: 182); c.restoreGState()
            }
            Self.draw(c, texts["day"]!, x: 232, cy: 104, alpha: 0.85)
            Self.draw(c, texts["date"]!, x: 230.5, cy: 128)
            Self.draw(c, texts["tomorrow"]!, x: 232, cy: 160, alpha: 0.6)
            Self.draw(c, texts["nori"]!, x: 109 - texts["nori"]!.width / 2, cy: 254)
            Self.draw(c, texts["calendar"]!, x: 293 - texts["calendar"]!.width / 2, cy: 254)
        }
        let blurred = Self.gaussian(page, sigma: 18)
        blur = Self.renderer(CGSize(width: 402, height: 330), 1) { _ in blurred.draw(in: CGRect(origin: .zero, size: size)) }
    }
    static func artScale(_ h: Double) -> Double { min(1, max(0.35, (762 + min(0, h - 874) - 16 - 276) / 434)) }
    private static func wallpaper(_ wall: UIImage, height: Double, scale: Double) -> UIImage {
        // Fixed photo coordinates preserve the tilted torus. The sky uses the original texture,
        // so compact devices never acquire a mirrored or stretched edge scanline.
        let a = artScale(height), s = 1.15 * a, ox = -14.2455 * a + 201 * (1-a), oy = 15.4295 * a + 276 * (1-a)
        let raw = renderer(CGSize(width: 402, height: height), scale) { c in
            wall.draw(in: CGRect(x: 0, y: 0, width: 402, height: 2185))
            c.saveGState()
            let mask = CGGradient(colorsSpace: CGColorSpaceCreateDeviceGray(), colors: [UIColor.clear.cgColor, UIColor.white.cgColor] as CFArray, locations: [0, 1])!
            c.beginTransparencyLayer(auxiliaryInfo: nil)
            wall.draw(in: CGRect(x: ox, y: oy, width: 402*s, height: 874*s))
            c.setBlendMode(.destinationIn)
            c.drawLinearGradient(mask, start: CGPoint(x: 0,y: 100), end: CGPoint(x: 0,y: 180), options: [.drawsBeforeStartLocation,.drawsAfterEndLocation])
            c.endTransparencyLayer(); c.restoreGState()
        }
        guard let cg = raw.cgImage else { return raw }
        let result = CIImage(cgImage: cg).applyingFilter("CIColorMatrix", parameters: [
            "inputRVector": CIVector(x: 1.35,y: 0,z: 0,w: 0), "inputGVector": CIVector(x: 0,y: 1.35,z: 0,w: 0),
            "inputBVector": CIVector(x: 0,y: 0,z: 1.35,w: 0), "inputBiasVector": CIVector(x: -0.569*0.35,y: -0.620*0.35,z: -0.565*0.35,w: 0)])
        return ci.createCGImage(result, from: result.extent).map { UIImage(cgImage: $0, scale: scale, orientation: .up) } ?? raw
    }
    private static func dock(_ c: CGContext, wallpaper: UIImage, height: Double) {
        let y = 762 + min(0, height - 874), rect = CGRect(x: 16,y: y,width: 370,height: 84)
        c.saveGState(); c.addPath(CGPath(roundedRect: rect,cornerWidth: 34,cornerHeight: 34,transform: nil)); c.clip()
        wallpaper.draw(at: .zero); c.setFillColor(UIColor.white.withAlphaComponent(0.16).cgColor); c.fill(rect); c.restoreGState()
        c.setStrokeColor(UIColor.white.withAlphaComponent(0.4).cgColor); c.setLineWidth(2)
        c.addPath(CGPath(roundedRect: rect.insetBy(dx: 1,dy: 1),cornerWidth: 33,cornerHeight: 33,transform: nil)); c.strokePath()
        let glyphs = ["M27 18 C13 10 12 35 27 31 M33 29 C47 37 48 12 33 16 M22 25 L38 25", "M20 17 L31 13 L43 23 L38 39 L23 43 L15 30 Z M23 22 L35 22 L38 32 L27 36 L21 29 Z", "M17 18 Q17 15 20 15 L36 15 Q39 15 39 18 L39 30 L30 30 L24 36 L24 30 L17 30 Z M31 37 L43 37 L43 24", "M18 39 L18 30 Q18 25 24 25 L36 25 Q42 25 42 19 M14 39 L22 39 M38 19 L42 15 L46 19 M29 22 L29 28"]
        for (i, glyph) in glyphs.enumerated() {
            c.saveGState(); c.translateBy(x: 36 + Double(i)*90,y: y+12)
            round(c,CGRect(x: 0,y: 0,width: 60,height: 60),19,UIColor(red: 0.68,green: 0.78,blue: 0.68,alpha: 0.4))
            c.setStrokeColor(UIColor.white.withAlphaComponent(0.6).cgColor); c.setLineWidth(2); c.addPath(CGPath(roundedRect: CGRect(x: 1,y: 1,width: 58,height: 58),cornerWidth: 18,cornerHeight: 18,transform: nil)); c.strokePath()
            c.setStrokeColor(UIColor.white.cgColor); c.setLineWidth(2.5); c.setLineCap(.round); c.setLineJoin(.round)
            c.addPath(IslandGlassOrbSVG.path(glyph)); c.strokePath(); c.restoreGState()
        }
    }
    private static func grain(height: Double) -> CGImage? {
        let w = 1206, h = Int(ceil(height * 3))
        var bytes = [UInt8](repeating: 0, count: w * h)
        for y in 0..<h { for x in 0..<w {
            let s = sin(Float(x) * 127.1 + Float(y) * 311.7) * 43758.5453
            bytes[y * w + x] = UInt8(max(0, min(255, (s - floor(s)) * 255)))
        } }
        guard let provider = CGDataProvider(data: Data(bytes) as CFData) else { return nil }
        return CGImage(width: w, height: h, bitsPerComponent: 8, bitsPerPixel: 8, bytesPerRow: w, space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGBitmapInfo(rawValue: 0), provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
    }
    private static func draw(_ c: CGContext, _ run: TextRun, x: Double, cy: Double, alpha: Double = 1) {
        c.saveGState(); c.translateBy(x: x, y: cy); c.setFillColor(UIColor(red: 247 / 255, green: 247 / 255, blue: 245 / 255, alpha: alpha).cgColor)
        c.addPath(run.path.cgPath); c.fillPath(); c.restoreGState()
    }
    private static func round(_ c: CGContext, _ rect: CGRect, _ radius: Double, _ color: UIColor) {
        c.setFillColor(color.cgColor); c.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil)); c.fillPath()
    }
    private static func rim(_ c: CGContext, x: Double, top: Double, bottom: Double) {
        c.saveGState(); let path = CGPath(roundedRect: CGRect(x: x + 0.5, y: 79.5, width: 161, height: 160), cornerWidth: 23.5, cornerHeight: 23.5, transform: nil)
        c.addPath(path); c.setLineWidth(1); c.replacePathWithStrokedPath(); c.clip()
        let g = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [UIColor.white.withAlphaComponent(top).cgColor, UIColor.white.withAlphaComponent(bottom).cgColor] as CFArray, locations: [0, 1])!
        c.drawLinearGradient(g, start: CGPoint(x: 0, y: 79), end: CGPoint(x: 0, y: 240), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]); c.restoreGState()
    }
    private static func icon(_ c: CGContext, _ name: String, x: Double, y: Double) {
        guard let url = Bundle.main.url(forResource: "IslandGlassOrb-" + name, withExtension: "svg"), let xml = try? String(contentsOf: url, encoding: .utf8) else { return }
        c.saveGState(); c.translateBy(x: x, y: y); c.setStrokeColor(UIColor.white.cgColor); c.setLineWidth(1.75); c.setLineCap(.round); c.setLineJoin(.round)
        let regex = try! NSRegularExpression(pattern: "<path[^>]* d=\"([^\"]+)\"")
        let ns = xml as NSString
        for match in regex.matches(in: xml, range: NSRange(location: 0, length: ns.length)) {
            c.addPath(IslandGlassOrbSVG.path(ns.substring(with: match.range(at: 1)))); c.strokePath()
        }
        if name == "icon-camera" { c.strokeEllipse(in: CGRect(x: 9.55, y: 11.05, width: 6.9, height: 6.9)) }
        c.restoreGState()
    }
}
