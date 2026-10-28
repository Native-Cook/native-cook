import SwiftUI

/*
 Prism Beam Folder — the artwork, drawn in code: the folder (back panel with its tab, a front
 panel that tilts open) and the four items (peach, cherries, aubergine, chili), each in a 48-unit
 box centred on (0, 0). The Compose and React Native ports draw the same paths with the same
 numbers.
*/
enum PrismBeamFolderArt {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255, opacity: a)
    }

    // MARK: Folder (104 × 84, centred on (0, 0); the tab rises to y = -50)

    static func backPath() -> Path {
        var p = Path()
        p.move(to: CGPoint(x: -52, y: -44))
        p.addQuadCurve(to: CGPoint(x: -46, y: -50), control: CGPoint(x: -52, y: -50))
        p.addLine(to: CGPoint(x: -22, y: -50))
        p.addQuadCurve(to: CGPoint(x: -17, y: -47.5), control: CGPoint(x: -19.5, y: -50))
        p.addLine(to: CGPoint(x: -13, y: -43.5))
        p.addQuadCurve(to: CGPoint(x: -9, y: -42), control: CGPoint(x: -11.2, y: -42))
        p.addLine(to: CGPoint(x: 42, y: -42))
        p.addQuadCurve(to: CGPoint(x: 52, y: -32), control: CGPoint(x: 52, y: -42))
        p.addLine(to: CGPoint(x: 52, y: 32))
        p.addQuadCurve(to: CGPoint(x: 42, y: 42), control: CGPoint(x: 52, y: 42))
        p.addLine(to: CGPoint(x: -42, y: 42))
        p.addQuadCurve(to: CGPoint(x: -52, y: 32), control: CGPoint(x: -52, y: 42))
        p.closeSubpath()
        return p
    }

    /// The front panel; `o` tilts it open (its top edge drops 8 and spreads 3 each side).
    static func frontPath(_ o: Double) -> Path {
        let top = -30 + 8 * o
        let half = 52 + 3 * o
        var p = Path()
        p.move(to: CGPoint(x: -half, y: top + 7))
        p.addQuadCurve(to: CGPoint(x: -half + 7, y: top), control: CGPoint(x: -half, y: top))
        p.addLine(to: CGPoint(x: half - 7, y: top))
        p.addQuadCurve(to: CGPoint(x: half, y: top + 7), control: CGPoint(x: half, y: top))
        p.addLine(to: CGPoint(x: 52, y: 32))
        p.addQuadCurve(to: CGPoint(x: 42, y: 42), control: CGPoint(x: 52, y: 42))
        p.addLine(to: CGPoint(x: -42, y: 42))
        p.addQuadCurve(to: CGPoint(x: -52, y: 32), control: CGPoint(x: -52, y: 42))
        p.closeSubpath()
        return p
    }

    static func drawBack(_ c: inout GraphicsContext) {
        c.fill(backPath(), with: .linearGradient(
            Gradient(colors: [hex(0x3A9AD3), hex(0x2C7FB8)]),
            startPoint: CGPoint(x: 0, y: -50), endPoint: CGPoint(x: 0, y: 42)))
        // The inside of the folder, in shadow just above the front panel.
        c.fill(Path(CGRect(x: -48, y: -40, width: 96, height: 12)), with: .linearGradient(
            Gradient(colors: [hex(0x0B2A44, 0), hex(0x0B2A44, 0.45)]),
            startPoint: CGPoint(x: 0, y: -40), endPoint: CGPoint(x: 0, y: -28)))
    }

    static func drawFront(_ c: inout GraphicsContext, open o: Double, light: Double) {
        let path = frontPath(o)
        let top = -30 + 8 * o
        c.fill(path, with: .linearGradient(
            Gradient(colors: [hex(0x8EDCF9), hex(0x56BDEC)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: 42)))
        // Beam light spilling over the front.
        if light > 0.001 {
            c.fill(path, with: .linearGradient(
                Gradient(colors: [hex(0xFFE9F6, 0.55 * light), hex(0xFFE9F6, 0)]),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: top + 40)))
        }
        var hi = Path()
        hi.move(to: CGPoint(x: -52 - 3 * o + 7, y: top + 0.75))
        hi.addLine(to: CGPoint(x: 52 + 3 * o - 7, y: top + 0.75))
        c.stroke(hi, with: .color(.white.opacity(0.55)), style: StrokeStyle(lineWidth: 1.5, lineCap: .round))
        var lo = Path()
        lo.move(to: CGPoint(x: -42, y: 41.25))
        lo.addLine(to: CGPoint(x: 42, y: 41.25))
        c.stroke(lo, with: .color(hex(0x2C7FB8, 0.7)), lineWidth: 1.5)
    }

    // MARK: Items

    static func drawItem(_ c: inout GraphicsContext, kind: Int) {
        switch kind {
        case 0: peach(&c)
        case 1: cherries(&c)
        case 2: aubergine(&c)
        default: chili(&c)
        }
    }

    private static func leaf(_ c: inout GraphicsContext, _ p: Path, from: CGPoint, to: CGPoint) {
        c.fill(p, with: .linearGradient(Gradient(colors: [hex(0x7BD36B), hex(0x2E8B57)]),
                                        startPoint: from, endPoint: to))
    }

    static func peach(_ c: inout GraphicsContext) {
        let body = Path(ellipseIn: CGRect(x: -19, y: -15, width: 38, height: 36))
        c.fill(body, with: .radialGradient(
            Gradient(stops: [.init(color: hex(0xFFD08A), location: 0),
                             .init(color: hex(0xFF8A5B), location: 0.55),
                             .init(color: hex(0xE2474F), location: 1)]),
            center: CGPoint(x: -6, y: -6), startRadius: 0, endRadius: 28))
        var crease = Path()
        crease.move(to: CGPoint(x: 1, y: -13))
        crease.addQuadCurve(to: CGPoint(x: -3, y: 6), control: CGPoint(x: -8, y: -4))
        c.stroke(crease, with: .color(hex(0xC93A44, 0.5)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        c.fill(Path(ellipseIn: CGRect(x: -13, y: -9, width: 8, height: 6)), with: .color(.white.opacity(0.35)))
        var stem = Path()
        stem.move(to: CGPoint(x: 1, y: -13))
        stem.addLine(to: CGPoint(x: -1, y: -19))
        c.stroke(stem, with: .color(hex(0x6B4B2A)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        var l = Path()
        l.move(to: CGPoint(x: 2, y: -14))
        l.addQuadCurve(to: CGPoint(x: 16, y: -22), control: CGPoint(x: 6, y: -24))
        l.addQuadCurve(to: CGPoint(x: 2, y: -14), control: CGPoint(x: 14, y: -13))
        l.closeSubpath()
        leaf(&c, l, from: CGPoint(x: 6, y: -22), to: CGPoint(x: 12, y: -14))
    }

    static func cherries(_ c: inout GraphicsContext) {
        var stems = Path()
        stems.move(to: CGPoint(x: -8, y: 2))
        stems.addQuadCurve(to: CGPoint(x: 6, y: -20), control: CGPoint(x: -6, y: -12))
        stems.move(to: CGPoint(x: 9, y: 4))
        stems.addQuadCurve(to: CGPoint(x: 6, y: -20), control: CGPoint(x: 12, y: -8))
        c.stroke(stems, with: .color(hex(0x4E7D2B)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        var l = Path()
        l.move(to: CGPoint(x: 6, y: -20))
        l.addQuadCurve(to: CGPoint(x: 18, y: -18), control: CGPoint(x: 14, y: -25))
        l.addQuadCurve(to: CGPoint(x: 6, y: -20), control: CGPoint(x: 12, y: -14))
        l.closeSubpath()
        leaf(&c, l, from: CGPoint(x: 8, y: -24), to: CGPoint(x: 16, y: -16))
        for (x, y) in [(-9.0, 10.0), (9.0, 12.0)] {
            c.fill(Path(ellipseIn: CGRect(x: x - 10, y: y - 10, width: 20, height: 20)), with: .radialGradient(
                Gradient(stops: [.init(color: hex(0xFF6A7A), location: 0),
                                 .init(color: hex(0xD0142F), location: 0.6),
                                 .init(color: hex(0x7A0019), location: 1)]),
                center: CGPoint(x: x - 3, y: y - 3), startRadius: 0, endRadius: 14))
            c.fill(Path(ellipseIn: CGRect(x: x - 6, y: y - 6.5, width: 4.5, height: 3.2)), with: .color(.white.opacity(0.7)))
        }
    }

    static func aubergine(_ c: inout GraphicsContext) {
        var body = Path()
        body.move(to: CGPoint(x: -2, y: -14))
        body.addCurve(to: CGPoint(x: 10, y: 8), control1: CGPoint(x: 5, y: -15), control2: CGPoint(x: 9, y: -4))
        body.addCurve(to: CGPoint(x: 2, y: 25), control1: CGPoint(x: 11, y: 18), control2: CGPoint(x: 8, y: 25))
        body.addCurve(to: CGPoint(x: -9, y: 4), control1: CGPoint(x: -5, y: 25), control2: CGPoint(x: -8, y: 14))
        body.addCurve(to: CGPoint(x: -2, y: -14), control1: CGPoint(x: -10, y: -6), control2: CGPoint(x: -8, y: -13))
        body.closeSubpath()
        c.fill(body, with: .radialGradient(
            Gradient(stops: [.init(color: hex(0xA779E0), location: 0),
                             .init(color: hex(0x5B2A86), location: 0.55),
                             .init(color: hex(0x2E1247), location: 1)]),
            center: CGPoint(x: -3, y: 2), startRadius: 0, endRadius: 24))
        var hi = Path()
        hi.move(to: CGPoint(x: -5, y: -4))
        hi.addQuadCurve(to: CGPoint(x: -2, y: 16), control: CGPoint(x: -7, y: 8))
        c.stroke(hi, with: .color(.white.opacity(0.4)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
        var stem = Path()
        stem.addRoundedRect(in: CGRect(x: -1.5, y: -22, width: 3, height: 8), cornerSize: CGSize(width: 1.5, height: 1.5))
        c.fill(stem, with: .color(hex(0x4E8A3A)))
        var cap = Path()
        cap.move(to: CGPoint(x: -9, y: -11))
        cap.addQuadCurve(to: CGPoint(x: 0, y: -8), control: CGPoint(x: -4, y: -6))
        cap.addQuadCurve(to: CGPoint(x: 9, y: -11), control: CGPoint(x: 4, y: -6))
        cap.addQuadCurve(to: CGPoint(x: 0, y: -17), control: CGPoint(x: 6, y: -17))
        cap.addQuadCurve(to: CGPoint(x: -9, y: -11), control: CGPoint(x: -6, y: -17))
        cap.closeSubpath()
        c.fill(cap, with: .linearGradient(Gradient(colors: [hex(0x6CC46A), hex(0x2F7D3A)]),
                                          startPoint: CGPoint(x: 0, y: -17), endPoint: CGPoint(x: 0, y: -8)))
    }

    static func chili(_ c: inout GraphicsContext) {
        var pod = Path()
        pod.move(to: CGPoint(x: -8, y: -14))
        pod.addCurve(to: CGPoint(x: 12, y: 22), control1: CGPoint(x: 10, y: -14), control2: CGPoint(x: 16, y: 8))
        pod.addCurve(to: CGPoint(x: -12, y: -8), control1: CGPoint(x: 6, y: 12), control2: CGPoint(x: -2, y: -4))
        pod.addQuadCurve(to: CGPoint(x: -8, y: -14), control: CGPoint(x: -12, y: -14))
        pod.closeSubpath()
        c.fill(pod, with: .linearGradient(
            Gradient(stops: [.init(color: hex(0xFF6A45), location: 0),
                             .init(color: hex(0xE0201E), location: 0.5),
                             .init(color: hex(0xA50F18), location: 1)]),
            startPoint: CGPoint(x: -8, y: -14), endPoint: CGPoint(x: 12, y: 22)))
        var hi = Path()
        hi.move(to: CGPoint(x: -2, y: -10))
        hi.addQuadCurve(to: CGPoint(x: 9, y: 8), control: CGPoint(x: 8, y: -6))
        c.stroke(hi, with: .color(.white.opacity(0.45)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        c.fill(Path(ellipseIn: CGRect(x: -15, y: -18, width: 11, height: 7)), with: .color(hex(0x4E9A3A)))
        var stem = Path()
        stem.move(to: CGPoint(x: -9, y: -16))
        stem.addQuadCurve(to: CGPoint(x: -3, y: -24), control: CGPoint(x: -8, y: -23))
        c.stroke(stem, with: .color(hex(0x3E7D2B)), style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
    }
}
