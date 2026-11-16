import CoreText
import CoreImage
import SwiftUI
import UIKit

/// The reference HarfBuzz glyph IDs and pen positions, applied to the same bundled font files.
final class CoinPileCollectResources {
    struct Run { let ids: [CGGlyph]; let xs: [Double]; let w: Double }
    struct Line { let path: Path; let width: Double }
    static let runs: [String: Run] = [
        "title1": Run(ids: [95, 203, 148, 130, 251, 425, 221], xs: [0, 553, 1153, 1785, 2357, 2980, 3256], w: 3770),
        "title2": Run(ids: [203, 162, 162, 152, 217, 221], xs: [0, 590, 980, 1360, 1935, 2324], w: 2838),
        "protect1": Run(ids: [81, 217, 203, 533, 152, 142, 533, 379, 251, 203, 233, 217], xs: [0, 661, 1050, 1650, 2080, 2665, 3230, 3660, 3946, 4569, 5169, 5757], w: 6156),
        "protect2": Run(ids: [162, 233, 196, 148, 221], xs: [0, 390, 978, 1587, 2219], w: 2733),
        "earned": Run(ids: [118, 203, 233, 379, 152, 130, 217, 196, 152, 148], xs: [0, 589, 1186, 1768, 2046, 2620, 3183, 3569, 4169, 4743], w: 5361),
        "period": Run(ids: [171, 196, 379, 88, 152, 214, 533, 152, 195, 141, 152, 217], xs: [0, 240, 840, 1118, 1775, 2349, 2966, 3379, 3953, 4794, 5411, 5985], w: 6371),
        "exchange1": Run(ids: [23, 250, 142, 168, 130, 196, 163, 152], xs: [0, 618, 1153, 1712, 2312, 2875, 3475, 4093], w: 4667),
        "exchange2": Run(ids: [142, 233, 217, 217, 152, 196, 142, 251], xs: [0, 559, 1141, 1527, 1903, 2477, 3077, 3636], w: 4246),
        "premium1": Run(ids: [34, 152, 533, 379, 251, 203, 233, 217], xs: [0, 736, 1311, 1741, 2027, 2650, 3250, 3838], w: 4237),
        "premium2": Run(ids: [81, 217, 152, 195, 171, 233, 195, 379, 142, 130, 217, 148], xs: [0, 661, 1050, 1625, 2488, 2749, 3337, 4200, 4486, 5051, 5623, 6012], w: 6644),
        "collect": Run(ids: [13, 203, 189, 189, 152, 142, 228], xs: [0, 711, 1308, 1548, 1788, 2372, 2931], w: 3299),
        "halden": Run(ids: [168, 130, 189, 148, 152, 196], xs: [0, 609, 1181, 1442, 2074, 2649], w: 3258),
        "cardUrl": Run(ids: [39, 1, 57, 19, 23, 64, 384, 13, 1, 84, 19], xs: [0, 737, 1435, 2022, 2749, 3367, 4125, 4377, 5065, 5763, 6442], w: 7169),
    ]
    static let register: Void = {
        for name in ["ExtraBold", "SemiBold"] {
            if let url = Bundle.main.url(forResource: "CoinPileCollect-Onest-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func font(_ size: Double, _ bold: Bool = true) -> CTFont {
        _ = register
        let name = bold ? "ExtraBold" : "SemiBold"
        let url = Bundle.main.url(forResource: "CoinPileCollect-Onest-\(name)", withExtension: "ttf")!
        return CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!, size, nil, nil)
    }
    static func outline(_ id: CGGlyph, _ font: CTFont) -> Path {
        Path(CTFontCreatePathForGlyph(font, id, nil) ?? CGMutablePath()).applying(CGAffineTransform(scaleX: 1, y: -1))
    }
    static func string(_ text: String, _ size: Double) -> Line {
        let f = font(size), chars = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(f, chars, &ids, ids.count)
        var widths = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(f, .horizontal, ids, &widths, ids.count)
        var path = Path(), x = 0.0
        for i in ids.indices {
            path.addPath(outline(ids[i], f), transform: CGAffineTransform(translationX: x, y: 0))
            x += widths[i].width
        }
        return Line(path: path, width: x)
    }
    let fs: Double
    let lines: [String: Line]
    let digits: [Path]
    let dollar: Path
    let dot: Path
    let monograms: [Line]
    init(fs: Double) {
        self.fs = fs
        var lines: [String: Line] = [:]
        func add(_ name: String, _ key: String, _ size: Double, _ track: Double, _ bold: Bool = true, from: Int = 0, to: Int? = nil) {
            let run = Self.runs[key]!, end = to ?? run.ids.count, f = Self.font(size * fs, bold)
            var path = Path()
            for i in from..<end {
                let x = (run.xs[i] - run.xs[from]) * size * fs / 1000 + Double(i - from) * track * size * fs
                path.addPath(Self.outline(run.ids[i], f), transform: CGAffineTransform(translationX: x, y: 0))
            }
            let width = ((end < run.ids.count ? run.xs[end] : run.w) - run.xs[from]) * size * fs / 1000 + Double(end - from - 1) * track * size * fs
            lines[name] = Line(path: path, width: width)
        }
        add("title1", "title1", 56, -0.035); add("title2", "title2", 56, -0.035)
        add("protect1", "protect1", 20, -0.012); add("protect2", "protect2", 20, -0.012)
        add("earned", "earned", 15, -0.004, false); add("period", "period", 15, -0.004, false)
        add("exchange1", "exchange1", 17, -0.008, false); add("exchange2", "exchange2", 17, -0.008, false)
        add("premium1", "premium1", 34, -0.03)
        add("premium2", "premium2", 34, -0.03, from: 0, to: 7)
        add("premium3", "premium2", 34, -0.03, from: 8, to: 12)
        add("collect", "collect", 17, -0.006, false)
        add("halden", "halden", 15, -0.01); add("haldenBig", "halden", 30, -0.02)
        add("cardUrl", "cardUrl", 7, 0.06, false)
        self.lines = lines
        let f = Self.font(30 * fs)
        digits = (367...376).map { Self.outline(CGGlyph($0), f) }
        dollar = Self.outline(453, f); dot = Self.outline(401, f)
        monograms = ["A", "M", "K"].map { Self.string($0, 16 * fs) }
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    /// Same 18 straight segments per superellipse corner (exponent 4.6) as squirclePath in RN.
    static func squircle(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        var p = Path()
        func corner(_ cx: Double, _ cy: Double, _ sx: Double, _ sy: Double, _ from: Double, _ to: Double) {
            for i in 0...18 {
                let t = from + (to - from) * Double(i) / 18
                p.addLine(to: CGPoint(x: cx + sx * r * pow(abs(cos(t)), 2 / 4.6), y: cy + sy * r * pow(abs(sin(t)), 2 / 4.6)))
            }
        }
        let q = Double.pi / 2
        p.move(to: CGPoint(x: x + r, y: y)); p.addLine(to: CGPoint(x: x + w - r, y: y))
        corner(x + w - r, y + r, 1, -1, q, 0)
        p.addLine(to: CGPoint(x: x + w, y: y + h - r)); corner(x + w - r, y + h - r, 1, 1, 0, q)
        p.addLine(to: CGPoint(x: x + r, y: y + h)); corner(x + r, y + h - r, -1, 1, q, 0)
        p.addLine(to: CGPoint(x: x, y: y + r)); corner(x + r, y + r, -1, -1, 0, q)
        p.closeSubpath(); return p
    }
    /// 576 × 192 alpha ridge atlas, generated once from the original outline and Onest glyphs.
    static let atlas: CGImage = {
        let c = CGContext(data: nil, width: 576, height: 192, bitsPerComponent: 8, bytesPerRow: 576 * 4,
                          space: CGColorSpace(name: CGColorSpace.sRGB)!, bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        c.translateBy(x: 0, y: 192); c.scaleBy(x: 1, y: -1)
        c.setShouldAntialias(true); c.setStrokeColor(CGColor(gray: 1, alpha: 1)); c.setLineJoin(.round)
        let points: [CGPoint] = [[46,40],[76,40],[76,82],[116,82],[116,40],[146,40],[146,152],[116,152],[116,110],[76,110],[76,152],[46,152]].map { CGPoint(x: $0[0], y: $0[1]) }
        c.beginPath(); c.addLines(between: points); c.closePath(); c.setLineWidth(5.2); c.strokePath()
        let f = font(168), cap = CTFontGetAscent(f) * 0.7
        for (index, text) in ["$", "€"].enumerated() {
            let line = string(text, 168)
            c.saveGState(); c.translateBy(x: Double(index + 1) * 192 + (192 - line.width) / 2, y: 96 + cap / 2)
            c.addPath(line.path.cgPath); c.setLineWidth(4.6); c.strokePath(); c.restoreGState()
        }
        let image = CIImage(cgImage: c.makeImage()!).applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 1.25])
        return CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!]).createCGImage(image, from: CGRect(x: 0, y: 0, width: 576, height: 192))!
    }()
}
