import CoreText
import SwiftUI
import UIKit

final class HoloCashCardResources {
    static let shared = HoloCashCardResources()
    struct Label { let image: UIImage; let width: Double; let height: Double }
    private var words: [Double: (Path, Double)] = [:]
    private var fonts: [String: UIFont] = [:]
    private var rims: [Double: Path] = [:]
    private var cardLabels: [String: Label] = [:]
    private var icons: [String: Path] = [:]
    init() {
        for name in ["Anton-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold"] {
            if let url = Bundle.main.url(forResource: "HoloCashCard-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        for text in ["YOUR BANK", "WON'T DO THIS", "DEBIT"] { _ = cardLabel(text) }
    }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    func font(_ size: Double, _ weight: String = "Bold", tabular: Bool = false) -> UIFont {
        let key = "\(size)|\(weight)|\(tabular)"
        if let cached = fonts[key] { return cached }
        let f = UIFont(name: weight == "Anton" ? "Anton-Regular" : (weight == "Bold" ? "Inter24pt-Bold" : "Inter-\(weight)"), size: size)
            ?? UIFont.systemFont(ofSize: size, weight: .bold)
        guard tabular else { fonts[key] = f; return f }
        let desc = f.fontDescriptor.addingAttributes([.featureSettings: [
            [UIFontDescriptor.FeatureKey.type: kNumberSpacingType, .selector: kMonospacedNumbersSelector]
        ]])
        let result = UIFont(descriptor: desc, size: size)
        fonts[key] = result
        return result
    }
    func cardLabel(_ text: String) -> Label {
        if let cached = cardLabels[text] { return cached }
        let face = font(10) as CTFont
        let chars = Array(text.utf16)
        var glyphs = Array(repeating: CGGlyph(), count: chars.count)
        CTFontGetGlyphsForCharacters(face, chars, &glyphs, chars.count)
        var advances = Array(repeating: CGSize.zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(face, .horizontal, glyphs, &advances, glyphs.count)
        var x = 0.0
        let points = advances.map { advance -> CGPoint in
            let p = CGPoint(x: x, y: 0); x += advance.width; return p
        }
        let ascent = ceil(CTFontGetAscent(face))
        let h = ascent + ceil(CTFontGetDescent(face))
        let format = UIGraphicsImageRendererFormat(); format.scale = 3
        let image = UIGraphicsImageRenderer(size: CGSize(width: ceil(x + 4), height: ceil(h + 4)), format: format).image { renderer in
            let cg = renderer.cgContext
            cg.setFillColor(UIColor.white.cgColor)
            cg.translateBy(x: 2, y: 2 + ascent); cg.scaleBy(x: 1, y: -1)
            CTFontDrawGlyphs(face, glyphs, points, glyphs.count, cg)
        }
        let result = Label(image: image, width: x, height: h); cardLabels[text] = result
        return result
    }
    func baseline(_ context: GraphicsContext, _ text: String, _ x: Double, _ y: Double, _ size: Double) {
        let item = cardLabel(text)
        context.draw(Image(uiImage: item.image), at: CGPoint(x: x - 2, y: y - ceil(CTFontGetAscent(font(size) as CTFont)) - 2), anchor: .topLeading)
    }
    func word(_ width: Double, _ height: Double) -> (Path, Double) {
        if let cached = words[width] { return cached }
        let f = font(height * 0.58, "Anton") as CTFont
        let chars = Array("LOUDCASH".utf16)
        var glyphs = Array(repeating: CGGlyph(), count: chars.count)
        CTFontGetGlyphsForCharacters(f, chars, &glyphs, chars.count)
        var advances = Array(repeating: CGSize.zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(f, .horizontal, glyphs, &advances, chars.count)
        let sx = (width - 28) / advances.reduce(0) { $0 + $1.width }
        let path = CGMutablePath(); var x = 0.0
        for (i, glyph) in glyphs.enumerated() {
            if let outline = CTFontCreatePathForGlyph(f, glyph, nil) {
                let transform = CGAffineTransform(a: sx, b: 0, c: 0, d: -1, tx: 14 + x * sx, ty: height * 0.69)
                path.addPath(outline, transform: transform)
            }
            x += advances[i].width
        }
        let result = (Path(path), sx); words[width] = result
        return result
    }
    func rim(_ width: Double, _ height: Double) -> Path {
        if let path = rims[width] { return path }
        let path = Path(roundedRect: CGRect(x: 0.75, y: 0.75, width: width - 1.5, height: height - 1.5), cornerRadius: 15.5)
        rims[width] = path
        return path
    }
    func icon(_ name: String) -> Path {
        if let p = icons[name] { return p }
        let p = CGMutablePath()
        func m(_ x: Double, _ y: Double) { p.move(to: CGPoint(x: x, y: y)) }
        func l(_ x: Double, _ y: Double) { p.addLine(to: CGPoint(x: x, y: y)) }
        func c(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ x: Double, _ y: Double) {
            p.addCurve(to: CGPoint(x: x, y: y), control1: CGPoint(x: x1, y: y1), control2: CGPoint(x: x2, y: y2))
        }
        // SVG endpoint arc, preserving the almost-closed circles in the reference paths.
        func arc(_ radius: Double, _ large: Bool, _ sweep: Bool, _ x: Double, _ y: Double) {
            let a = p.currentPoint, dx = (a.x - x) / 2, dy = (a.y - y) / 2
            let r = max(radius, hypot(dx, dy)), q = max(0, (r*r - dx*dx - dy*dy) / max(1e-12, dx*dx + dy*dy))
            let sign = large == sweep ? -1.0 : 1.0
            let cx = (a.x + x)/2 + sign * sqrt(q) * dy, cy = (a.y + y)/2 - sign * sqrt(q) * dx
            let start = atan2(a.y - cy, a.x - cx), end = atan2(y - cy, x - cx)
            p.addArc(center: CGPoint(x: cx, y: cy), radius: r, startAngle: start, endAngle: end, clockwise: !sweep)
        }
        switch name {
        case "eye":
            m(2,12); c(4.6,7.6,8,5.4,12,5.4); c(16,5.4,19.4,7.6,22,12)
            c(19.4,16.4,16,18.6,12,18.6); c(8,18.6,4.6,16.4,2,12); p.closeSubpath()
            m(12,9); arc(3,true,false,12.01,9)
        case "chat":
            m(4,12); arc(8,true,true,7.4,18.5); l(4,20); l(5.2,16.6); arc(7.9,false,true,4,12); p.closeSubpath()
        case "down": m(12,4); l(12,16); m(6.5,11); l(12,16.5); l(17.5,11); m(5,20); l(19,20)
        case "up": m(12,20); l(12,8); m(6.5,13); l(12,7.5); l(17.5,13); m(5,4); l(19,4)
        case "info": m(12,3); arc(9,true,false,12.01,3); m(12,11); l(12,17); m(12,7.5); l(12,8)
        case "wallet":
            m(3,7); l(19,7); arc(2,false,true,21,9); l(21,18); arc(2,false,true,19,20)
            l(5,20); arc(2,false,true,3,18); p.closeSubpath(); m(3,7); l(15,4); l(15,7); m(16,13.5); l(18,13.5)
        case "grid":
            for (x,y) in [(12.0,3.0),(6.0,9.0),(18.0,9.0),(12.0,15.0)] {
                m(x,y); l(x+3,y+3); l(x,y+6); l(x-3,y+3); p.closeSubpath()
            }
        case "ring": m(12,4); arc(8,true,false,12.01,4); m(12,8); arc(4,true,false,12.01,8)
        default: m(12,3); arc(9,true,false,12.01,3); m(8,12.5); l(10.8,15.3); l(16.5,9.5)
        }
        let result = Path(p); icons[name] = result; return result
    }
    func drawIcon(_ context: GraphicsContext, _ name: String, _ x: Double, _ y: Double,
                  _ size: Double = 22, _ color: UInt32 = 0x111111, _ width: Double = 1.9) {
        var c = context; c.translateBy(x: x, y: y); c.scaleBy(x: size / 24, y: size / 24)
        c.stroke(icon(name), with: .color(Self.color(color)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
}

struct HoloCashCardLayout {
    let width: Double, height: Double, topInset: Double, bottomInset: Double
    var cw: Double { width - 36 }
    var ch: Double { (cw / 1.6).rounded() }
    var top: Double { topInset + 6 }
    var cardY: Double { top + 52 }
    var balanceY: Double { cardY + ch + 8 }
    var actionsY: Double { balanceY + 136 }
    var loyalY: Double { balanceY + 196 }
    var tabsY: Double { height - bottomInset - 8 - 64 }
}
