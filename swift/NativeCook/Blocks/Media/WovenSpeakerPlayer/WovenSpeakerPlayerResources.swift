import SwiftUI
import CoreText
import UIKit

enum WovenSpeakerPlayerResources {
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static let titles = ["Lowlight", "Paper Moons", "Static Bloom"]
    static let artists = ["NORTH OF EIGHT", "JUNO VALE", "HALVES"]
    static let register: Void = {
        for name in ["InterDisplay-Bold", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "WovenSpeakerPlayer-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    struct Run {
        var path: Path, width: Double
    }
    static func font(_ face: String, _ size: Double) -> CTFont {
        _ = register
        if let url = Bundle.main.url(forResource: "WovenSpeakerPlayer-" + face, withExtension: "ttf"),
           let data = CGDataProvider(url: url as CFURL), let cg = CGFont(data) {
            return CTFontCreateWithGraphicsFont(cg, size, nil, nil)
        }
        return CTFontCreateWithName(face as CFString, size, nil)
    }
    static func run(_ face: String, _ size: Double, _ text: String, _ tracking: Double = 0) -> Run {
        let font = font(face, size), chars = Array(text.utf16)
        var ids = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &ids, chars.count)
        var widths = [CGSize](repeating: .zero, count: ids.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &widths, ids.count)
        var path = Path(), x = 0.0
        for i in ids.indices {
            if let glyph = CTFontCreatePathForGlyph(font, ids[i], nil) {
                path.addPath(Path(glyph), transform: CGAffineTransform(a: 1, b: 0, c: 0, d: -1, tx: x, ty: 0))
            }
            x += widths[i].width + (i < ids.count - 1 ? tracking * size : 0)
        }
        return Run(path: path, width: x)
    }
    static let headline = run("InterDisplay-Bold", 38, "Press play.", -0.021)
    static let pageTitles = titles.map { run("InterDisplay-Bold", 38, $0, -0.021) }
    static let displayTitles = titles.map { run("Inter-SemiBold", 15, $0) }
    static let displayArtists = artists.map { run("Inter-Medium", 8.5, $0, 0.12) }
    static let avatar = run("Inter-SemiBold", 9, "N")
    static let lines = ["Introducing Aria OS", "Aria OS · Living room"].map { run("Inter-Regular", 17, $0, -0.013) }
    static let pills = ["Learn more", "Open queue"].map { run("Inter-Medium", 15, $0, -0.006) }
    static let digits = "0123456789:".map { run("Inter-Medium", 12, String($0)) }
    static func draw(_ c: GraphicsContext, _ run: Run, _ x: Double, _ y: Double,
                     _ shading: GraphicsContext.Shading, _ opacity: Double = 1) {
        var c = c; c.opacity *= max(0, min(1, opacity))
        c.fill(run.path.applying(CGAffineTransform(translationX: x, y: y)), with: shading)
    }
    static func circle(_ r: Double, _ x: Double = 0, _ y: Double = 0) -> Path {
        Path(ellipseIn: CGRect(x: x-r, y: y-r, width: r*2, height: r*2))
    }
    static func arc(_ r: Double, _ start: Double, _ span: Double) -> Path {
        var p = Path(); p.addArc(center: .zero, radius: r, startAngle: .degrees(start), endAngle: .degrees(start+span), clockwise: false); return p
    }
    static func glyph(_ g: Double) -> Path {
        let play = [[-4.8,-8.2,2,-4.04,2,4.04,-4.8,8.2], [2,-4.04,8.6,0,8.6,0,2,4.04]]
        let pause = [[-6.5,-7.4,-2.1,-7.4,-2.1,7.4,-6.5,7.4], [2.1,-7.4,6.5,-7.4,6.5,7.4,2.1,7.4]]
        var p = Path()
        for q in 0..<2 {
            for i in 0..<4 {
                let x = play[q][i*2] + (pause[q][i*2] - play[q][i*2])*g
                let y = play[q][i*2+1] + (pause[q][i*2+1] - play[q][i*2+1])*g
                if i == 0 { p.move(to: CGPoint(x: x,y: y)) } else { p.addLine(to: CGPoint(x: x,y: y)) }
            }; p.closeSubpath()
        }; return p
    }
    static func chevron(_ d: Double) -> Path {
        var p = Path()
        for k in 0..<2 {
            let x = d * (Double(k)*4.5 - 4.5)
            p.move(to: CGPoint(x: x-d*2.25,y: -4.5)); p.addLine(to: CGPoint(x: x+d*2.25,y: 0))
            p.addLine(to: CGPoint(x: x-d*2.25,y: 4.5)); p.closeSubpath()
        }; return p
    }
    // Same 0.6 smoothing construction as addSquircle, including its small clockwise corner arcs.
    static let cover: Path = {
        let r = 20.0, xi = 0.6, p = (1+xi)*r
        let measure = 90*(1-xi), arcLen = sin(measure/2 * .pi/180)*r*sqrt(2)
        let alpha = (90-measure)/2, p34 = r*tan(alpha/2 * .pi/180), beta = 45*xi
        let c = p34*cos(beta * .pi/180), d = c*tan(beta * .pi/180)
        let b = (p-arcLen-c-d)/3, a = 2*b
        var corner = Path(); corner.move(to: CGPoint(x: 36-p,y: -36))
        corner.addCurve(to: CGPoint(x: 36-p+a+b+c,y: -36+d),
                        control1: CGPoint(x: 36-p+a,y: -36), control2: CGPoint(x: 36-p+a+b,y: -36))
        let start = corner.currentPoint!, end = CGPoint(x: start.x+arcLen,y: start.y+arcLen)
        let mid = CGPoint(x: (start.x+end.x)/2,y: (start.y+end.y)/2)
        let h = sqrt(max(0,r*r-arcLen*arcLen/2)), root = sqrt(2.0)
        let center = CGPoint(x: mid.x-h/root,y: mid.y+h/root)
        corner.addArc(center: center, radius: r,
                      startAngle: .radians(atan2(start.y-center.y,start.x-center.x)),
                      endAngle: .radians(atan2(end.y-center.y,end.x-center.x)), clockwise: false)
        corner.addCurve(to: CGPoint(x: 36,y: -36+p),
                        control1: CGPoint(x: end.x+d,y: end.y+c), control2: CGPoint(x: end.x+d,y: end.y+b+c))
        var result = Path()
        for i in 0..<4 {
            let t = CGAffineTransform(rotationAngle: Double(i) * .pi / 2)
            let section = corner.applying(t)
            if i == 0 { result.addPath(section) }
            else {
                // Connect each corner with the same straight edge as the source path.
                result.addLine(to: CGPoint(x: 36-p,y: -36).applying(t))
                section.forEach { el in
                    switch el {
                    case .move: break
                    case .line(let p): result.addLine(to: p)
                    case .quadCurve(let p,let c): result.addQuadCurve(to: p,control: c)
                    case .curve(let p,let c1,let c2): result.addCurve(to: p,control1: c1,control2: c2)
                    case .closeSubpath: break
                    }
                }
            }
        }; result.closeSubpath(); return result
    }()
}

// Exact SVG control points from coverArt(), converted to CGPath commands.
extension WovenSpeakerPlayerResources {
    static func figure(_ variant: Int) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: -34, y: 34))
        p.addCurve(to: CGPoint(x: -18, y: 17), control1: CGPoint(x: -33, y: 25), control2: CGPoint(x: -28, y: 20))
        p.addCurve(to: CGPoint(x: -12, y: 11), control1: CGPoint(x: -14, y: 16), control2: CGPoint(x: -12, y: 14))
        p.addLine(to: CGPoint(x: -12, y: 8))
        p.addCurve(to: CGPoint(x: -19, y: -10), control1: CGPoint(x: -17, y: 4), control2: CGPoint(x: -20, y: -3))
        p.addCurve(to: CGPoint(x: -2, y: -25), control1: CGPoint(x: -18, y: -20), control2: CGPoint(x: -10, y: -26))
        p.addCurve(to: CGPoint(x: 12, y: -12), control1: CGPoint(x: 7, y: -24), control2: CGPoint(x: 12, y: -18))
        p.addLine(to: CGPoint(x: 15, y: -5.5))
        p.addCurve(to: CGPoint(x: 13.2, y: -3.4), control1: CGPoint(x: 15.5, y: -4.4), control2: CGPoint(x: 14.8, y: -3.8))
        p.addLine(to: CGPoint(x: 12.8, y: -1))
        p.addCurve(to: CGPoint(x: 12.4, y: 1.2), control1: CGPoint(x: 13.6, y: -0.2), control2: CGPoint(x: 13.3, y: 0.8))
        p.addCurve(to: CGPoint(x: 11, y: 4.6), control1: CGPoint(x: 12.9, y: 2.4), control2: CGPoint(x: 12.6, y: 4))
        p.addCurve(to: CGPoint(x: 4.6, y: 6.6), control1: CGPoint(x: 10, y: 6.4), control2: CGPoint(x: 7.6, y: 7))
        p.addLine(to: CGPoint(x: 4.4, y: 10))
        p.addCurve(to: CGPoint(x: 18, y: 17), control1: CGPoint(x: 6, y: 13.5), control2: CGPoint(x: 11, y: 15.5))
        p.addCurve(to: CGPoint(x: 34, y: 34), control1: CGPoint(x: 27, y: 19), control2: CGPoint(x: 32, y: 25))
        p.closeSubpath()
        if variant == 0 {
        p.move(to: CGPoint(x: -16, y: -16))
        p.addCurve(to: CGPoint(x: -21.5, y: -26.5), control1: CGPoint(x: -22, y: -16), control2: CGPoint(x: -25, y: -22))
        p.addCurve(to: CGPoint(x: -10.5, y: -22.5), control1: CGPoint(x: -17.5, y: -30.5), control2: CGPoint(x: -10.5, y: -28.5))
        p.closeSubpath()
        }
        return variant == 1 ? p.applying(CGAffineTransform(scaleX: -1, y: 1)) : p
    }
}
