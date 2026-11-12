import CoreText
import SwiftUI
import UIKit

/// The original registered artwork, atlas rectangles and unkerned fractional Onest glyph metrics.
enum PinataSmashRewardResources {
    static let k = 1 / (0.835 * (1440.0 / 402))
    static let ox = 35 * k
    static let oy = 226 + 53 * k
    static let register: Void = {
        for weight in ["Medium", "SemiBold"] {
            if let url = Bundle.main.url(forResource: "PinataSmashReward-Onest-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    struct Point: Decodable { let x, y: Double }
    struct Box: Decodable { let x, y, w, h: Double }
    struct Bulb: Decodable { let center: Point; let radius: Double; let color: String }
    struct Layout: Decodable {
        let figure_box: Box
        let ribbon_attach_point, flank_center, tail_root: Point
        let tail_box: Box
        let bulbs: [Bulb]
    }
    struct Sprite: Decodable { let x, y, w, h, cx, cy: Double }
    struct Line { let path: Path; let width: Double }

    static func data(_ name: String, _ ext: String) -> Data {
        guard let url = Bundle.main.url(forResource: "PinataSmashReward-\(name)", withExtension: ext),
              let data = try? Data(contentsOf: url) else { preconditionFailure("Missing piñata asset: \(name).\(ext)") }
        return data
    }
    static let layout = try! JSONDecoder().decode(Layout.self, from: data("layout", "json"))
    static let atlasRects = try! JSONDecoder().decode([String: Sprite].self, from: data("loot-atlas", "json"))
    static func bitmap(_ name: String) -> CGImage {
        let image = UIImage(data: data(name, "png"))!
        return image.preparingForDisplay()?.cgImage ?? image.cgImage!
    }
    static let body = bitmap("pinata-body")
    static let burst = bitmap("pinata-body-burst")
    static let crack1 = bitmap("pinata-crack-1")
    static let crack2 = bitmap("pinata-crack-2")
    static let tail = bitmap("pinata-tail")
    static let atlas = bitmap("loot-atlas")
    static let sprites: [CGImage] = PinataSmashRewardMotion.atlasNames.map {
        let s = atlasRects[$0]!
        return atlas.cropping(to: CGRect(x: s.x, y: s.y, width: s.w, height: s.h))!
    }
    static let pivots = PinataSmashRewardMotion.atlasNames.map { atlasRects[$0]! }

    static func line(_ text: String, _ weight: String, _ size: Double) -> Line {
        _ = register
        let url = Bundle.main.url(forResource: "PinataSmashReward-Onest-\(weight)", withExtension: "ttf")!
        let font = CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url: url as CFURL)!)!, size, nil, nil)
        let chars = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &ids, ids.count)
        var widths = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &widths, ids.count)
        var path = Path(), x = 0.0
        for i in ids.indices {
            if let outline = CTFontCreatePathForGlyph(font, ids[i], nil) {
                path.addPath(Path(outline), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += widths[i].width
        }
        return Line(path: path, width: x)
    }
    static let label = line("Keep tapping", "Medium", 15.5)
    static let done = line("Come back tomorrow", "Medium", 15.5)
    static let title = line("Daily piñata", "SemiBold", 20)
    static let digits = (0...9).map { line(String($0), "SemiBold", 15) }
    static let chipW = line("000", "SemiBold", 15).width
    static let chipWidth = 8 + 22 + 7 + chipW + 14
    static let chipX0 = 402 - 24 - chipWidth
    static let cx = ox + (layout.figure_box.x + layout.figure_box.w / 2) * k
    static let cy = oy + (layout.figure_box.y + layout.figure_box.h * 0.55) * k
    static let rx = layout.figure_box.w / 2 * k
    static let ry = layout.figure_box.h / 2 * k
    static func geo(_ top: Double, _ dy: Double) -> PinataSmashRewardMotion.Geo {
        func point(_ p: Point) -> PinataSmashRewardMotion.Pt { .init(x: ox + p.x * k, y: oy + p.y * k) }
        return .init(chipX: chipX0 + 19, chipY: 76 + top - dy,
                     bulbs: layout.bulbs.map { point($0.center) }, flank: point(layout.flank_center),
                     body: .init(x: cx, y: cy), attach: point(layout.ribbon_attach_point))
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func color(_ hex: String, _ alpha: Double = 1) -> Color {
        color(UInt32(hex.dropFirst(), radix: 16)!, alpha)
    }
    static func text(_ c: GraphicsContext, _ line: Line, _ x: Double, _ y: Double, _ color: Color) {
        var context = c
        context.translateBy(x: x, y: y)
        context.fill(line.path, with: .color(color))
    }
}
