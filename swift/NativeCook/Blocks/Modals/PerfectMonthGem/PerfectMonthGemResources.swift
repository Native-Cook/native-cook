import CoreImage
import CoreText
import SwiftUI
import UIKit

final class PerfectMonthGemResources {
    static let shared = PerfectMonthGemResources()
    struct Glyph {
        let image: Image
        let size: CGSize
        let advance: Double
        let baseline: Double
    }
    struct Sprite {
        let image: Image
        let size: CGSize
    }
    private(set) var shadows: [Sprite] = []
    private(set) var flash: Sprite!
    private(set) var glint: Sprite!
    private var wrappedLines: [String: [String]] = [:]
    private(set) var days: [Glyph] = []
    private(set) var weekdays: [Glyph] = []
    private(set) var tag: Glyph!
    static let names = ["InterDisplay-Bold", "Inter24pt-Regular", "Inter-Medium", "Inter-SemiBold", "Inter24pt-Bold"]
    private init() {
        for name in ["InterDisplay-Bold", "Inter-Regular", "Inter-Medium", "Inter-SemiBold", "Inter-Bold"] {
            if let url = Bundle.main.url(forResource: "PerfectMonthGem-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        days = (1...31).map { glyph(String($0), "Inter-SemiBold", 9) }
        weekdays = ["M", "T", "W", "T", "F", "S", "S"].map { glyph($0, "Inter-SemiBold", 9) }
        tag = glyph("×3", "Inter24pt-Bold", 12)
        // Core Image is used only here. The frame renderer only transforms cached sprites.
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = CIContext(options: [.useSoftwareRenderer: true, .cacheIntermediates: false,
                                          .workingColorSpace: space, .outputColorSpace: space])
        shadows = (0...64).map { index in
            let k = Double(index) / 64
            let width = PerfectMonthGemMotion.mix(186, 128, k)
            let height = PerfectMonthGemMotion.mix(150, 128, k)
            let radius = PerfectMonthGemMotion.mix(22, 64, k)
            return blurred(context, CGSize(width: 320, height: 288), 1, 14) { cg in
                cg.setFillColor(UIColor(red: 106.0 / 255, green: 63.0 / 255, blue: 181.0 / 255, alpha: 0.12).cgColor)
                cg.addPath(CGPath(roundedRect: CGRect(x: 160 - width / 2, y: 144 - height / 2 + 8, width: width, height: height), cornerWidth: radius, cornerHeight: radius, transform: nil))
                cg.fillPath()
            }
        }
        flash = blurred(context, CGSize(width: 216, height: 216), 2, 16) { cg in
            cg.setFillColor(UIColor.white.cgColor)
            cg.fillEllipse(in: CGRect(x: 68, y: 68, width: 80, height: 80))
        }
        glint = blurred(context, CGSize(width: 28, height: 24), 3, 2.5) { cg in
            cg.setFillColor(UIColor.white.cgColor)
            cg.fillEllipse(in: CGRect(x: 10, y: 10, width: 7.36, height: 3.68))
        }
    }
    private func blurred(_ ci: CIContext, _ size: CGSize, _ scale: Double, _ sigma: Double, _ draw: (CGContext) -> Void) -> Sprite {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.preferredRange = .standard
        let source = UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
        guard let cg = source.cgImage else { return Sprite(image: Image(uiImage: source), size: size) }
        let input = CIImage(cgImage: cg)
        let output = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * scale])
        let result = ci.createCGImage(output, from: input.extent) ?? cg
        return Sprite(image: Image(uiImage: UIImage(cgImage: result, scale: scale, orientation: .up)), size: size)
    }
    func sprite(_ c: GraphicsContext, _ image: Sprite, _ x: Double, _ y: Double, _ alpha: Double = 1) {
        var ctx = c
        ctx.opacity *= alpha
        ctx.draw(image.image, in: CGRect(x: x, y: y, width: image.size.width, height: image.size.height))
    }
    func shadow(_ c: GraphicsContext, _ cx: Double, _ cy: Double, _ k: Double) {
        let index = PerfectMonthGemMotion.clamp(k) * 64
        let lo = Int(floor(index)), hi = min(64, Int(ceil(index)))
        if lo == hi { sprite(c, shadows[lo], cx - 160, cy - 144); return }
        let f = index - Double(lo)
        c.drawLayer { layer in
            var ctx = layer
            ctx.blendMode = .plusLighter
            sprite(ctx, shadows[lo], cx - 160, cy - 144, 1 - f)
            sprite(ctx, shadows[hi], cx - 160, cy - 144, f)
        }
    }
    // SwiftUI balances paragraphs; RN uses greedy Core Text word wrapping.
    // Insert the measured breaks so both native renderers use the same lines.
    func lines(_ text: String, _ name: String, _ size: Double, _ width: Double) -> [String] {
        let key = "\(text)/\(name)/\(size)/\(width)"
        if let cached = wrappedLines[key] { return cached }
        let font = UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
        let string = text as NSString
        let setter = CTTypesetterCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        var start = 0
        var result: [String] = []
        while start < string.length {
            let count = max(1, CTTypesetterSuggestLineBreak(setter, start, width))
            result.append(string.substring(with: NSRange(location: start, length: count)).trimmingCharacters(in: .whitespaces))
            start += count
        }
        wrappedLines[key] = result
        return result
    }
    private func glyph(_ string: String, _ name: String, _ size: Double) -> Glyph {
        let font = (UIFont(name: name, size: size) ?? .systemFont(ofSize: size)) as CTFont
        let chars = Array(string.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: chars.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        var x = 0.0
        let positions = advances.map { advance -> CGPoint in
            defer { x += advance.width }
            return CGPoint(x: x, y: 0)
        }
        let pad = 3.0
        let baseline = pad + CTFontGetAscent(font)
        let dims = CGSize(width: ceil(x) + pad * 2, height: ceil(CTFontGetAscent(font) + CTFontGetDescent(font)) + pad * 2)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let bitmap = UIGraphicsImageRenderer(size: dims, format: format).image { r in
            r.cgContext.setFillColor(UIColor.white.cgColor)
            r.cgContext.translateBy(x: pad, y: baseline)
            r.cgContext.scaleBy(x: 1, y: -1)
            CTFontDrawGlyphs(font, glyphs, positions, glyphs.count, r.cgContext)
        }
        return Glyph(image: Image(uiImage: bitmap), size: dims, advance: x, baseline: baseline)
    }
    func draw(_ c: GraphicsContext, _ glyph: Glyph, _ x: Double, _ baseline: Double, _ color: Color, _ alpha: Double = 1) {
        var ctx = c
        ctx.opacity *= alpha
        var image = ctx.resolve(glyph.image)
        image.shading = .color(color)
        ctx.draw(image, in: CGRect(x: x - 3, y: baseline - glyph.baseline, width: glyph.size.width, height: glyph.size.height))
    }
}
