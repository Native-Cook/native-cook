import CoreText
import SwiftUI

/// Skia Text uses raw glyph advances, without native text shaping or the unused theme tracking tokens.
enum ObjectSigninDissolveResources {
    struct Line {
        let path: Path
        let width: Double
    }
    static let register: Void = {
        for name in ["InstrumentSerif-Italic", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "ObjectSigninDissolve-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static func line(_ text: String, _ name: String, _ size: Double) -> Line {
        _ = register
        guard let url = Bundle.main.url(forResource: "ObjectSigninDissolve-\(name)", withExtension: "ttf"),
              let provider = CGDataProvider(url: url as CFURL), let cg = CGFont(provider) else {
            return Line(path: Path(), width: 0)
        }
        let font = CTFontCreateWithGraphicsFont(cg, size, nil, nil)
        let chars = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &ids, ids.count)
        var widths = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &widths, ids.count)
        var p = Path(), x = 0.0
        for i in ids.indices {
            if let raw = CTFontCreatePathForGlyph(font, ids[i], nil) {
                let path = Path(raw).applying(CGAffineTransform(scaleX: 1, y: -1))
                p.addPath(path, transform: CGAffineTransform(translationX: x, y: 0))
            }
            x += widths[i].width
        }
        return Line(path: p, width: x)
    }
    static let word = line("Pome", "InstrumentSerif-Italic", 54)
    static let tag = line("Taste comes first", "Inter-Regular", 13)
    static let email = line("Continue with email", "Inter-SemiBold", 16)
    static let passkey = line("Passkey", "Inter-Medium", 14)
    static let phone = line("Phone", "Inter-Medium", 14)
    static let legal = ["By continuing you agree to our ", "Terms", " and ", "Privacy Policy"].map { line($0, "Inter-Regular", 11) }
    static let legalB = line("Ripe is a decision. Make it.", "Inter-Regular", 11)
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func mixHex(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        let t = ObjectSigninDissolveMotion.clamp(t)
        var value: UInt32 = 0
        for shift in [16, 8, 0] {
            let x = Double(a >> shift & 255), y = Double(b >> shift & 255)
            value |= UInt32(floor(x + (y - x) * t + 0.5)) << shift
        }
        return value
    }
    static func rgb(_ h: UInt32) -> [Double] {
        [Double(h >> 16 & 255) / 255, Double(h >> 8 & 255) / 255, Double(h & 255) / 255]
    }
    static func textPath(_ line: Line, _ x: Double, _ baseline: Double, _ scale: Double) -> Path {
        line.path.applying(CGAffineTransform(a: scale, b: 0, c: 0, d: scale, tx: x, ty: baseline))
    }
    static func icon(_ kind: Int, _ x: Double, _ y: Double) -> Path {
        var p = Path()
        if kind == 0 {
            p.addRoundedRect(in: CGRect(x: x - 8, y: y - 6, width: 16, height: 12), cornerSize: CGSize(width: 2.5, height: 2.5))
            p.move(to: CGPoint(x: x - 7, y: y - 4.2)); p.addLine(to: CGPoint(x: x, y: y + 1)); p.addLine(to: CGPoint(x: x + 7, y: y - 4.2))
        } else if kind == 1 {
            p.addEllipse(in: CGRect(x: x - 8, y: y - 4, width: 8, height: 8))
            p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x + 8, y: y))
            p.move(to: CGPoint(x: x + 5, y: y)); p.addLine(to: CGPoint(x: x + 5, y: y + 3.5))
            p.move(to: CGPoint(x: x + 8, y: y)); p.addLine(to: CGPoint(x: x + 8, y: y + 3))
        } else {
            p.addRoundedRect(in: CGRect(x: x - 5, y: y - 8, width: 10, height: 16), cornerSize: CGSize(width: 2.6, height: 2.6))
            p.move(to: CGPoint(x: x - 1.6, y: y + 5)); p.addLine(to: CGPoint(x: x + 1.6, y: y + 5))
        }
        return p
    }
}
