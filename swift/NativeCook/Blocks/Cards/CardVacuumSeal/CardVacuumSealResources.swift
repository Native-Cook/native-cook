import CoreText
import SwiftUI
import UIKit

@MainActor
final class CardVacuumSealResources {
    static let register: Void = {
        for weight in ["Medium", "SemiBold", "Bold"] {
            if let url = Bundle.main.url(forResource: "CardVacuumSeal-HankenGrotesk-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    struct Line {
        let path: Path
        let width: Double
        let ascent: Double
    }
    private var lines: [String: Line] = [:]
    private var maps: [Double: Image] = [:]
    private let crease: UIImage
    let avatars: [Image]

    init() {
        _ = Self.register
        let creaseURL = Bundle.main.url(forResource: "CardVacuumSeal-cvs-creases",withExtension: "png")!
        crease = UIImage(contentsOfFile: creaseURL.path)!
        avatars = [Self.image("avatar-tidewell"), Self.image("avatar-oren")]
    }
    static func image(_ name: String) -> Image {
        let url = Bundle.main.url(forResource: "CardVacuumSeal-\(name)", withExtension: "png")!
        return Image(uiImage: UIImage(contentsOfFile: url.path)!)
    }
    static func font(_ size: Double, semi: Bool = false) -> UIFont {
        _ = register
        return UIFont(name: semi ? "HankenGrotesk-SemiBold" : "GcpHankenMedium", size: size)!
    }
    nonisolated static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255)/255, green: Double(hex >> 8 & 255)/255,
              blue: Double(hex & 255)/255, opacity: alpha)
    }
    nonisolated static func uiColor(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double(hex >> 16 & 255)/255, green: Double(hex >> 8 & 255)/255,
                blue: Double(hex & 255)/255, alpha: alpha)
    }
    func line(_ text: String, size: Double, tracking: Double = 0, semi: Bool = false, maxWidth: Double? = nil) -> Line {
        let key = "\(text)|\(size)|\(tracking)|\(semi)|\(maxWidth ?? -1)"
        if let line = lines[key] { return line }
        let font = Self.font(size, semi: semi)
        let attrs: [NSAttributedString.Key: Any] = [.font: font, .kern: tracking*size]
        var ct = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attrs))
        if let width = maxWidth, CTLineGetTypographicBounds(ct,nil,nil,nil) > width {
            let token = CTLineCreateWithAttributedString(NSAttributedString(string: "…", attributes: attrs))
            ct = CTLineCreateTruncatedLine(ct,width,.end,token) ?? ct
        }
        var path = Path()
        for item in CTLineGetGlyphRuns(ct) as! [CTRun] {
            let attributes = CTRunGetAttributes(item) as NSDictionary
            let runFont = attributes[kCTFontAttributeName] as! CTFont
            let count = CTRunGetGlyphCount(item)
            var glyphs = [CGGlyph](repeating: 0,count: count), positions = [CGPoint](repeating: .zero,count: count)
            CTRunGetGlyphs(item,CFRange(location: 0,length: 0),&glyphs)
            CTRunGetPositions(item,CFRange(location: 0,length: 0),&positions)
            for i in glyphs.indices {
                if let outline = CTFontCreatePathForGlyph(runFont,glyphs[i],nil) {
                    let transform = CGAffineTransform(a: 1,b: 0,c: 0,d: -1,tx: positions[i].x,ty: -positions[i].y)
                    path.addPath(Path(outline),transform: transform)
                }
            }
        }
        let line = Line(path: path,width: CTLineGetTypographicBounds(ct,nil,nil,nil),ascent: font.ascender)
        lines[key] = line
        return line
    }
    func draw(_ context: GraphicsContext, _ text: String, x: Double, top: Double, size: Double,
              tracking: Double = 0, semi: Bool = false, color: Color = CardVacuumSealResources.color(0xF4F3F7),
              align: Double = 0, maxWidth: Double? = nil, opacity: Double = 1) {
        let line = line(text,size: size,tracking: tracking,semi: semi,maxWidth: maxWidth)
        var c = context
        c.opacity *= opacity
        c.translateBy(x: x-line.width*align,y: top+line.ascent)
        c.fill(line.path,with: .color(color))
    }

    /// Same native card view as BankCard: encoded sRGB gradient, Hanken Medium, native text top offsets.
    func atlas(pixelScale: Double) -> Image {
        if let image = maps[pixelScale] { return image }
        let cardWidth = Int(ceil(354*pixelScale)), cardHeight = Int(ceil(223*pixelScale))
        let width = max(1218,cardWidth), height = cardHeight+2+825
        let context = CGContext(data: nil,width: width,height: height,bitsPerComponent: 8,bytesPerRow: 0,
                                space: CGColorSpace(name: CGColorSpace.sRGB)!,bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.translateBy(x: 0,y: Double(height)); context.scaleBy(x: pixelScale,y: -pixelScale)
        UIGraphicsPushContext(context)
        let card = CardVacuumSealCardView(pixelScale: pixelScale)
        // Keep the UIView and its labels alive while rendering their layer hierarchy.
        withExtendedLifetime(card) {
            card.layer.render(in: context)
        }
        context.interpolationQuality = .none
        crease.draw(in: CGRect(x: 0,y: Double(cardHeight+2)/pixelScale,width: 1218/pixelScale,height: 825/pixelScale))
        UIGraphicsPopContext()
        let image = Image(decorative: context.makeImage()!,scale: 1)
        maps[pixelScale] = image
        return image
    }
}
