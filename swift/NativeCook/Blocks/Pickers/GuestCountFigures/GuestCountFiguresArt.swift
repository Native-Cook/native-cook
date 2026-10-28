import SwiftUI

/*
 Guest Count Figures — the little people, drawn in code.

 Each figure stands with its feet on (0, 0), x centred, up is negative y, in points. Every part is
 a rounded rectangle or an ellipse filled with a left-lit gradient (14 % lighter on the left, 22 %
 darker on the right), so the flat colours read like painted figurines. Adults are 115 pt tall,
 children 76 pt, each scaled a little by its own height.
*/
enum GuestCountFiguresArt {
    struct Body {
        var h: Double
        var headW: Double
        var headH: Double
        var torsoTop: Double
        var torsoBot: Double
        var torsoW: Double
        var armW: Double
        var legW: Double
        var shoeW: Double
        var shoeH: Double
        var neckW: Double
        /// How far the head sits below the figure's height (a shorter neck).
        var headDrop: Double
    }

    static let adult = Body(h: 118, headW: 17, headH: 19, torsoTop: -92, torsoBot: -56, torsoW: 24,
                            armW: 7, legW: 10, shoeW: 13, shoeH: 5, neckW: 6, headDrop: 3)
    static let child = Body(h: 76, headW: 15.5, headH: 16.5, torsoTop: -58, torsoBot: -33, torsoW: 19,
                            armW: 6, legW: 8, shoeW: 11, shoeH: 4, neckW: 5, headDrop: 0)

    enum Hair { case beanie, short, bun, long, puffs }
    enum Outer { case none, vest, coat }
    enum Extra { case none, bag, lanyard, ring, dots }

    struct Look {
        var kid: Bool
        var scale: Double
        var skin: UInt32
        var hair: UInt32
        var hairKind: Hair
        var hat: UInt32 = 0
        var top: UInt32
        var sleeve: UInt32
        var outer: Outer = .none
        var outerColor: UInt32 = 0
        var bottom: UInt32
        var shorts = false
        var shoes: UInt32
        var extra: Extra = .none
        var extraColor: UInt32 = 0
    }

    static let looks: [Look] = [
        Look(kid: false, scale: 1.0, skin: 0xC68A62, hair: 0x3A2A22, hairKind: .beanie, hat: 0x2FB5A8,
             top: 0xF1ECE2, sleeve: 0xC68A62, bottom: 0x8C8648, shoes: 0x5A3B28,
             extra: .bag, extraColor: 0x6B4A33),
        Look(kid: false, scale: 1.04, skin: 0xE2B48E, hair: 0x6B4428, hairKind: .short,
             top: 0x9FC0DE, sleeve: 0x9FC0DE, outer: .vest, outerColor: 0x263447,
             bottom: 0xC49A5A, shoes: 0x2B2B2B, extra: .lanyard, extraColor: 0x3A6FD8),
        Look(kid: false, scale: 0.97, skin: 0x8D5A3C, hair: 0x1F1A1A, hairKind: .bun,
             top: 0xEDE6DA, sleeve: 0xD9A33A, outer: .coat, outerColor: 0xD9A33A,
             bottom: 0x3C3F48, shoes: 0x1E1E1E),
        Look(kid: false, scale: 1.0, skin: 0xF0C9A8, hair: 0xA2512E, hairKind: .long,
             top: 0xB9A3D6, sleeve: 0xB9A3D6, bottom: 0x4B6A93, shoes: 0xECE8E1),
        Look(kid: true, scale: 1.0, skin: 0x7A4B32, hair: 0x2A1E1A, hairKind: .puffs,
             top: 0xF08CA6, sleeve: 0x7A4B32, bottom: 0xF08CA6, shorts: true, shoes: 0xF2C94C,
             extra: .ring, extraColor: 0x6FD3C2),
        Look(kid: true, scale: 0.9, skin: 0xD9A07A, hair: 0x3B2A20, hairKind: .short,
             top: 0xE8873A, sleeve: 0xE8873A, bottom: 0x5B4A8C, shoes: 0x2B2B2B,
             extra: .dots, extraColor: 0xF7C58F),
        Look(kid: true, scale: 0.95, skin: 0xF2CBAA, hair: 0xE3B04B, hairKind: .short,
             top: 0xD9534F, sleeve: 0xD9534F, bottom: 0x2E3A59, shorts: true, shoes: 0x3D7DD8),
    ]

    // MARK: Colour

    static func rgb(_ v: UInt32) -> (Double, Double, Double) {
        (Double((v >> 16) & 0xFF) / 255, Double((v >> 8) & 0xFF) / 255, Double(v & 0xFF) / 255)
    }
    static func color(_ v: UInt32, _ a: Double = 1) -> Color {
        let c = rgb(v)
        return Color(.sRGB, red: c.0, green: c.1, blue: c.2, opacity: a)
    }
    static func lighten(_ v: UInt32, _ k: Double) -> Color {
        let c = rgb(v)
        return Color(.sRGB, red: c.0 + (1 - c.0) * k, green: c.1 + (1 - c.1) * k, blue: c.2 + (1 - c.2) * k)
    }
    static func darken(_ v: UInt32, _ k: Double) -> Color {
        let c = rgb(v)
        return Color(.sRGB, red: c.0 * (1 - k), green: c.1 * (1 - k), blue: c.2 * (1 - k))
    }

    // MARK: Primitives

    static func shade(_ v: UInt32, _ r: CGRect) -> GraphicsContext.Shading {
        .linearGradient(Gradient(stops: [.init(color: lighten(v, 0.14), location: 0),
                                         .init(color: color(v), location: 0.45),
                                         .init(color: darken(v, 0.22), location: 1)]),
                        startPoint: CGPoint(x: r.minX, y: r.midY), endPoint: CGPoint(x: r.maxX, y: r.midY))
    }

    static func rr(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double,
                   _ r: Double, _ v: UInt32) {
        let rect = CGRect(x: x, y: y, width: w, height: h)
        c.fill(Path(roundedRect: rect, cornerRadius: min(r, w / 2, h / 2)), with: shade(v, rect))
    }

    static func el(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ v: UInt32) {
        let rect = CGRect(x: x, y: y, width: w, height: h)
        c.fill(Path(ellipseIn: rect), with: shade(v, rect))
    }

    static func line(_ c: inout GraphicsContext, _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double,
                     _ w: Double, _ v: UInt32) {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        c.stroke(p, with: .color(color(v)), style: StrokeStyle(lineWidth: w, lineCap: .round))
    }

    // MARK: Shadows

    /// The tight contact shadow and the long soft one to the right, under a figure at (0, 0).
    static func drawShadow(_ c: inout GraphicsContext, slot: Double, k: Double, alpha: Double) {
        func blob(_ cx: Double, _ w: Double, _ h: Double, _ a: Double) {
            var e = c
            e.translateBy(x: cx, y: 0)
            e.scaleBy(x: 1, y: h / w)
            e.fill(Path(ellipseIn: CGRect(x: -w / 2, y: -w / 2, width: w, height: w)),
                   with: .radialGradient(Gradient(stops: [.init(color: color(0x2A2420, a), location: 0),
                                                          .init(color: color(0x2A2420, a * 0.45), location: 0.5),
                                                          .init(color: color(0x2A2420, 0), location: 1)]),
                                         center: .zero, startRadius: 0, endRadius: w / 2))
        }
        blob(slot * 0.55, slot * 1.7 * k, 10, 0.07 * alpha)
        blob(0, slot * 0.9 * k, 7, 0.22 * alpha)
    }

    // MARK: Figure

    static func drawFigure(_ c: inout GraphicsContext, _ look: Look) {
        let b = look.kid ? child : adult
        let headTop = -b.h + b.headDrop
        let headCY = headTop + b.headH / 2

        // Hair behind the head.
        switch look.hairKind {
        case .long:
            rr(&c, -b.headW / 2 - 1.5, headTop - 1, b.headW + 3, b.headH + 12, 9, look.hair)
        case .bun:
            el(&c, -5.5, headTop - 7.5, 11, 10, look.hair)
        case .puffs:
            el(&c, -b.headW / 2 - 5, headTop + 1, 10, 10, look.hair)
            el(&c, b.headW / 2 - 5, headTop + 1, 10, 10, look.hair)
        default:
            break
        }

        // Legs and shoes.
        let legTop = b.torsoBot - 4
        for s in [-1.0, 1.0] {
            let lx = s * (b.legW / 2 + 0.5)
            if look.shorts {
                let lw = b.legW * 0.78
                rr(&c, lx - lw / 2, legTop, lw, -legTop - b.shoeH + 1.5, lw / 2, look.skin)
                rr(&c, lx - b.legW / 2 - 0.5, legTop, b.legW + 1, -legTop * 0.36, 3, look.bottom)
            } else {
                rr(&c, lx - b.legW / 2, legTop, b.legW, -legTop - b.shoeH + 1.5, b.legW * 0.4, look.bottom)
            }
            rr(&c, lx - b.shoeW / 2 + s * 1, -b.shoeH, b.shoeW, b.shoeH, b.shoeH / 2, look.shoes)
        }
        // Hips.
        rr(&c, -b.torsoW / 2 + 1, b.torsoBot - 6, b.torsoW - 2, 10, 4, look.bottom)

        // Arms, then hands.
        let sleeve = look.outer == .coat ? look.outerColor : look.sleeve
        for s in [-1.0, 1.0] {
            let ax = s * (b.torsoW / 2 + b.armW / 2 - 1.5)
            rr(&c, ax - b.armW / 2, b.torsoTop + 1, b.armW, b.torsoBot - b.torsoTop + 1, b.armW / 2, sleeve)
            let hr = b.armW * 0.55
            el(&c, ax - hr, b.torsoBot + 3 - hr, 2 * hr, 2 * hr, look.skin)
        }

        // Neck and torso.
        let neckTop = headCY + b.headH * 0.3
        rr(&c, -b.neckW / 2, neckTop, b.neckW, b.torsoTop + 3 - neckTop, 1, look.skin)
        rr(&c, -b.torsoW / 2, b.torsoTop, b.torsoW, b.torsoBot - b.torsoTop + 2, b.torsoW * 0.3, look.top)

        switch look.outer {
        case .coat:
            rr(&c, -b.torsoW / 2 - 1, b.torsoTop, b.torsoW + 2, b.torsoBot - b.torsoTop + 22, 7, look.outerColor)
            line(&c, 0.5, b.torsoTop + 5, 0.5, b.torsoBot + 20, 0.9, 0xA8761F)
            rr(&c, -3.5, b.torsoTop - 0.5, 7, 5, 2.5, look.top)
        case .vest:
            let hw = b.torsoW / 2 - 2
            rr(&c, -b.torsoW / 2 - 0.5, b.torsoTop + 1, hw, b.torsoBot - b.torsoTop, 5, look.outerColor)
            rr(&c, b.torsoW / 2 + 0.5 - hw, b.torsoTop + 1, hw, b.torsoBot - b.torsoTop, 5, look.outerColor)
            for y in [b.torsoTop + 13, b.torsoTop + 23] {
                line(&c, -b.torsoW / 2 + 1, y, -2.5, y, 0.8, 0x18212E)
                line(&c, 2.5, y, b.torsoW / 2 - 1, y, 0.8, 0x18212E)
            }
        case .none:
            break
        }

        switch look.extra {
        case .bag:
            line(&c, -b.torsoW / 2 + 2, b.torsoTop + 2, b.torsoW / 2 + 2, b.torsoBot - 6, 2, look.extraColor)
            rr(&c, b.torsoW / 2 - 2, b.torsoBot - 9, 9, 8, 2, look.extraColor)
        case .lanyard:
            line(&c, -3, b.torsoTop + 1, 0, b.torsoTop + 13, 0.9, look.extraColor)
            line(&c, 3, b.torsoTop + 1, 0, b.torsoTop + 13, 0.9, look.extraColor)
            rr(&c, -3, b.torsoTop + 13, 6, 8, 1, 0xF7F5F0)
        case .dots:
            for (dx, dy) in [(-5.0, 6.0), (3.5, 10.0), (-2.0, 16.0), (5.0, 19.0), (-6.0, 21.0)] {
                el(&c, dx - 1.3, b.torsoTop + dy - 1.3, 2.6, 2.6, look.extraColor)
            }
        case .ring:
            let y = b.torsoBot - 6
            el(&c, -17, y - 7, 34, 14, look.extraColor)
            el(&c, -10.5, y - 4, 21, 6, 0x3FA595)
            el(&c, -17, y - 1, 34, 8, look.extraColor)
            for dx in [-11.0, 0.0, 11.0] {
                rr(&c, dx - 1.3, y, 2.6, 6, 1.3, 0xF7FFFD)
            }
            el(&c, 12.5, y - 13, 9, 10, look.extraColor)
            el(&c, 17.8, y - 10, 1.8, 1.8, 0x1E3B36)
        case .none:
            break
        }

        // Head, face, hair in front.
        el(&c, -b.headW / 2, headTop, b.headW, b.headH, look.skin)
        let eyeY = headCY + 1.5
        c.fill(Path(ellipseIn: CGRect(x: -3.6 - 0.8, y: eyeY - 0.8, width: 1.6, height: 1.6)), with: .color(color(0x2A1E1A, 0.75)))
        c.fill(Path(ellipseIn: CGRect(x: 3.0 - 0.8, y: eyeY - 0.8, width: 1.6, height: 1.6)), with: .color(color(0x2A1E1A, 0.75)))

        switch look.hairKind {
        case .beanie:
            rr(&c, -b.headW / 2 - 0.5, headTop + 5, 3.5, 8, 1.5, look.hair)
            rr(&c, b.headW / 2 - 3, headTop + 5, 3.5, 8, 1.5, look.hair)
            rr(&c, -b.headW / 2 - 1, headTop - 3, b.headW + 2, 11, 7, look.hat)
            rr(&c, -b.headW / 2 - 1.5, headTop + 5, b.headW + 3, 4, 2, darkenHex(look.hat, 0.18))
        default:
            el(&c, -b.headW / 2 - 0.5, headTop - 1.5, b.headW + 1, b.headH * 0.46, look.hair)
            rr(&c, -b.headW / 2 - 0.5, headTop + 2, 3, b.headH * 0.38, 1.5, look.hair)
            rr(&c, b.headW / 2 - 2.5, headTop + 2, 3, b.headH * 0.38, 1.5, look.hair)
        }
    }

    static func darkenHex(_ v: UInt32, _ k: Double) -> UInt32 {
        let c = rgb(v)
        let r = UInt32((c.0 * (1 - k) * 255).rounded()), g = UInt32((c.1 * (1 - k) * 255).rounded())
        let bl = UInt32((c.2 * (1 - k) * 255).rounded())
        return (r << 16) | (g << 8) | bl
    }
}
