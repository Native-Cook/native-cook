import CoreImage
import CoreText
import ImageIO
import UIKit

/// Baselines and glyph advances deliberately match Skia rather than UIKit text layout.
final class KiteWindArt {
    private var faces: [String: CGFont] = [:]
    private let kite: UIImage?, clouds: UIImage?
    private let kiteShadow: UIImage?, cardShadow: UIImage?
    init() {
        for name in ["InterDisplay-Bold", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "KiteWindCard-\(name)", withExtension: "ttf"),
               let provider = CGDataProvider(url: url as CFURL), let font = CGFont(provider) { faces[name] = font }
        }
        func image(_ name: String) -> UIImage? {
            guard let url = Bundle.main.url(forResource: "KiteWindCard-\(name)", withExtension: "webp"),
                  let src = CGImageSourceCreateWithURL(url as CFURL, nil),
                  let cg = CGImageSourceCreateImageAtIndex(src, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary) else { return nil }
            return UIImage(cgImage: cg)
        }
        kite = image("kite"); clouds = image("clouds")
        // Bake each Gaussian once at 3×; CI's radius is sigma, as in the RN Skia paint.
        func shadow(_ size: CGSize, _ sigma: Double, _ draw: (CGContext) -> Void) -> UIImage? {
            let format = UIGraphicsImageRendererFormat(); format.scale = 3; format.preferredRange = .standard
            let source = UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
            guard let cg = source.cgImage else { return nil }
            let input = CIImage(cgImage: cg)
            let blurred = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma*3])
            return CIContext(options: [.workingColorSpace: NSNull()]).createCGImage(blurred, from: input.extent).map {
                UIImage(cgImage: $0, scale: 3, orientation: .up)
            }
        }
        let loadedKite = kite
        kiteShadow = shadow(CGSize(width: 142, height: 185.2), 2) { c in
            loadedKite?.draw(in: CGRect(x: 12, y: 12, width: 118, height: 161.2))
            c.setBlendMode(.sourceIn); c.setFillColor(Self.color(0x003C8C, 0.25)); c.fill(CGRect(x: 0, y: 0, width: 142, height: 185.2))
        }
        cardShadow = shadow(CGSize(width: 393, height: 168), 13/3) { c in
            c.setFillColor(Self.color(0x003C8C, 0.18))
            c.addPath(UIBezierPath(roundedRect: CGRect(x: 20, y: 20, width: 353, height: 128), cornerRadius: 26).cgPath); c.fillPath()
        }
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> CGColor {
        CGColor(srgbRed: Double((hex>>16)&255)/255, green: Double((hex>>8)&255)/255, blue: Double(hex&255)/255, alpha: alpha)
    }
    static func pose(_ size: CGSize, _ f: KiteWindMotion.Frame) -> (Double, Double, Double) {
        let wn = (KiteWindMotion.wind(f.p)-10)/16, ph = 2*Double.pi*f.t/8, s = 0.25+0.75*wn
        return ((size.width-393)/2+300+10*s*sin(3*ph)+4*sin(7*ph)+f.x,
                (196-18*wn+8*s*sin(5*ph+1)+3*cos(4*ph))*size.height/852+f.y,
                (14+6*s*sin(3*ph+0.6)+2*sin(8*ph))*Double.pi/180)
    }
    private func text(_ c: CGContext, _ copy: String, _ face: String, _ size: Double, _ x: Double, _ y: Double, _ color: UInt32, _ tracking: Double = 0, _ align: Double = 0) {
        let font = faces[face].map { CTFontCreateWithGraphicsFont($0, size, nil, nil) } ?? (UIFont.systemFont(ofSize: size) as CTFont)
        let chars = Array(copy.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count), advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, chars.count)
        let width = advances.reduce(0) { $0+$1.width }+Double(max(0, chars.count-1))*tracking
        var cx = -width*align
        let points = advances.map { a -> CGPoint in let p = CGPoint(x: cx, y: 0); cx += a.width+tracking; return p }
        c.saveGState(); c.translateBy(x: x, y: y); c.scaleBy(x: 1, y: -1)
        c.textMatrix = .identity; c.setFillColor(Self.color(color))
        CTFontDrawGlyphs(font, glyphs, points, glyphs.count, c); c.restoreGState()
    }
    func draw(_ c: CGContext, _ size: CGSize, _ f: KiteWindMotion.Frame) {
        let ox = (size.width-393)/2, fy = size.height/852, w = KiteWindMotion.wind(f.p), wn = (w-10)/16, ph = 2*Double.pi*f.t/8
        if let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: [Self.color(0x1E8EF0), Self.color(0x3FB0FF), Self.color(0x8FD3FF)] as CFArray, locations: [0, 0.6, 1]) {
            c.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: size.height), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        }
        let (kx, ky, rot) = Self.pose(size, f)
        func point(_ y: Double) -> CGPoint { CGPoint(x: kx-y*sin(rot), y: ky+y*cos(rot)) }
        let bridle = point(8), bottom = point(161.2/2-2), ax = ox-30, ay = 820*fy, sag = 60*(1-wn)+18
        c.setStrokeColor(Self.color(0xFFFFFF, 0.85)); c.setLineWidth(1)
        c.move(to: bridle); c.addQuadCurve(to: CGPoint(x: ax, y: ay), control: CGPoint(x: (bridle.x+ax)/2+sag*0.6, y: (bridle.y+ay)/2+sag)); c.strokePath()
        var points = [bottom]
        let ang = rot+Double.pi/2-0.15+0.25*(1-wn)
        for i in 1...10 {
            let d = Double(i)*21, wave = (7+12*wn)*sin(ph*5-Double(i)*0.75)*(Double(i)/10+0.3)
            points.append(CGPoint(x: bottom.x+cos(ang)*d+cos(ang+Double.pi/2)*wave, y: bottom.y+sin(ang)*d+sin(ang+Double.pi/2)*wave))
        }
        c.setStrokeColor(Self.color(0xFFFFFF, 0.9)); c.setLineWidth(1.4); c.addLines(between: points); c.strokePath()
        for i in 1...10 {
            let a = atan2(points[i].y-points[i-1].y, points[i].x-points[i-1].x)+Double.pi/2+0.4*sin(ph*5-Double(i))
            c.saveGState(); c.translateBy(x: points[i].x, y: points[i].y); c.rotate(by: a)
            c.setFillColor(Self.color(i%2 == 1 ? 0xFFD21E : 0xFFB800))
            for sign in [-1.0, 1.0] {
                c.move(to: .zero); c.addLine(to: CGPoint(x: sign*9, y: -5.5)); c.addLine(to: CGPoint(x: sign*9, y: 5.5)); c.closePath(); c.fillPath()
            }
            c.restoreGState()
        }
        // Translate in screen coordinates before rotating, so the shadow stays straight down.
        c.saveGState(); c.translateBy(x: kx, y: ky+8/3); c.rotate(by: rot)
        kiteShadow?.draw(in: CGRect(x: -71, y: -92.6, width: 142, height: 185.2)); c.restoreGState()
        c.saveGState(); c.translateBy(x: kx, y: ky); c.rotate(by: rot)
        kite?.draw(in: CGRect(x: -59, y: -80.6, width: 118, height: 161.2)); c.restoreGState()
        clouds?.draw(in: CGRect(x: ox-50+8*sin(ph), y: 590*fy, width: 491, height: 221.8))
        text(c, "HARBOUR PARK · NE WIND", "Inter-SemiBold", 13, ox+30, 92*fy, 0xDFF1FF, 4)
        text(c, "\(Int(w.rounded()))", "InterDisplay-Bold", 132, ox+26, 214*fy, 0xFFFFFF, -7)
        text(c, "km/h · gusts \(Int((w*1.4).rounded()))", "InterDisplay-Bold", 24, ox+30, 250*fy, 0xFFFFFF)
        text(c, w < 24 ? "Good kite weather until 6 pm." : "Strong gusts: hold on tight.", "Inter-Medium", 16, ox+30, 280*fy, 0xE6F4FF)
        let top = 694*fy
        func y(_ v: Double) -> Double { top+v-694 }
        cardShadow?.draw(in: CGRect(x: ox, y: top-20+8/3, width: 393, height: 168))
        c.setFillColor(Self.color(0xF6FBFF)); c.addPath(UIBezierPath(roundedRect: CGRect(x: ox+20, y: top, width: 353, height: 128), cornerRadius: 26).cgPath); c.fillPath()
        text(c, "Today", "Inter-SemiBold", 15, ox+40, y(724), 0x0E3A66)
        text(c, "\(KiteWindMotion.hour(f.p)) · \(Int(w.rounded())) km/h", "Inter-Medium", 13, ox+353, y(724), 0x5D86AE, 0, 1)
        for i in 0..<10 {
            let h = 8+KiteWindMotion.winds[i]*1.5
            c.setFillColor(Self.color(abs(Double(i)-f.p) < 0.5 ? 0x1E8EF0 : 0xCFE6FA))
            c.addPath(UIBezierPath(roundedRect: CGRect(x: ox+46+Double(i)*31.4, y: y(792)-h, width: 12, height: h), cornerRadius: 4).cgPath); c.fillPath()
        }
        let sx = ox+52+f.p*31.4
        c.setStrokeColor(Self.color(0x1E8EF0)); c.setLineWidth(2); c.move(to: CGPoint(x: sx, y: y(742))); c.addLine(to: CGPoint(x: sx, y: y(796))); c.strokePath()
        c.setFillColor(Self.color(0x1E8EF0)); c.fillEllipse(in: CGRect(x: sx-5, y: y(742)-5, width: 10, height: 10))
        for i in [0.0, 3, 6, 9] { text(c, KiteWindMotion.hour(i), "Inter-Medium", 11, ox+52+i*31.4, y(812), 0x5D86AE, 0, 0.5) }
        if f.finger > 0 { c.setFillColor(Self.color(0x0E3A66, 0.2*f.finger)); c.fillEllipse(in: CGRect(x: sx-22, y: y(768)-22, width: 44, height: 44)) }
    }
}
