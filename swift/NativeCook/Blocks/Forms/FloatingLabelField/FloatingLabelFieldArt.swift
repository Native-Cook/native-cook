import SwiftUI

/// The header of the Larch sign-up: an autumn valley drawn once, in code. It never animates.
struct FloatingLabelFieldArt: View, Equatable {
    let width: CGFloat
    let height: CGFloat
    let markX: CGFloat
    let markY: CGFloat

    static let page = Color(red: 0xF3 / 255, green: 0xEF / 255, blue: 0xE6 / 255)

    private static func c(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255,
              opacity: a)
    }

    private static func hash(_ i: Double) -> Double {
        let s = sin(i * 12.9898) * 43758.5453
        return s - floor(s)
    }

    private static func ridgeY(_ layer: Double, _ x: Double, _ w: Double, _ base: Double, _ amp: Double) -> Double {
        let u = x / w
        let a = 0.62 * sin(u * (2.1 + layer * 0.9) + layer * 1.7)
        let b = 0.3 * sin(u * (5.3 + layer * 1.3) + layer * 4.1)
        let d = 0.08 * sin(u * 23 + layer * 2.3)
        return base + amp * (a + b + d)
    }

    private static func ridge(_ layer: Int, _ w: Double, _ h: Double, _ base: Double, _ amp: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: 0, y: h))
        var x = 0.0
        while x <= w + 4 {
            p.addLine(to: CGPoint(x: x, y: ridgeY(Double(layer), x, w, base, amp)))
            x += 4
        }
        p.addLine(to: CGPoint(x: w, y: h))
        p.closeSubpath()
        return p
    }

    private static func addTree(_ p: inout Path, _ x: Double, _ y: Double, _ tall: Double) {
        let wide = tall * 0.3
        p.move(to: CGPoint(x: x, y: y - tall))
        let pts: [(Double, Double)] = [
            (0.42, 0.62), (0.24, 0.62), (0.74, 0.3), (0.5, 0.3),
        ]
        for (dx, dy) in pts { p.addLine(to: CGPoint(x: x + wide * dx, y: y - tall * dy)) }
        p.addLine(to: CGPoint(x: x + wide, y: y + 2))
        p.addLine(to: CGPoint(x: x - wide, y: y + 2))
        for (dx, dy) in pts.reversed() { p.addLine(to: CGPoint(x: x - wide * dx, y: y - tall * dy)) }
        p.closeSubpath()
    }

    var body: some View {
        Canvas { ctx, _ in
            let w = Double(width), h = Double(height)
            let bases = [h * 0.46, h * 0.58, h * 0.7, h * 0.84]
            let amps = [h * 0.07, h * 0.075, h * 0.07, h * 0.06]
            let ridgeColors: [UInt32] = [0xD3D9C6, 0xB4C3AA, 0x7E9C83, 0x3B6B54]
            let ridges = (0..<4).map { Self.ridge($0, w, h, bases[$0], amps[$0]) }
            let full = CGRect(x: 0, y: 0, width: w, height: h)

            let sky = Gradient(stops: [
                .init(color: Self.c(0xDCE3D6), location: 0),
                .init(color: Self.c(0xECE6CF), location: 0.34),
                .init(color: Self.c(0xF4E5C6), location: 0.6),
                .init(color: Self.c(0xF3EFE6), location: 1),
            ])
            ctx.fill(Path(full), with: .linearGradient(sky, startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))

            let sun = CGPoint(x: w * 0.7, y: h * 0.4)
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: h * 0.14))
                let r = h * 0.3
                c.fill(Path(ellipseIn: CGRect(x: sun.x - r, y: sun.y - r, width: r * 2, height: r * 2)),
                       with: .color(Self.c(0xFFECBE, 0.55)))
            }
            let sr = h * 0.085
            ctx.fill(Path(ellipseIn: CGRect(x: sun.x - sr, y: sun.y - sr, width: sr * 2, height: sr * 2)),
                     with: .color(Self.c(0xFFF3D2)))

            ctx.fill(ridges[0], with: .color(Self.c(ridgeColors[0])))
            ctx.fill(ridges[1], with: .color(Self.c(ridgeColors[1])))
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 16))
                c.fill(Path(CGRect(x: 0, y: h * 0.5, width: w, height: h * 0.2)), with: .color(Self.c(0xF4EFE2, 0.5)))
            }

            var far = Path()
            let farCount = Int((w / 15).rounded())
            for i in 0..<farCount {
                let d = Double(i)
                let x = ((d + Self.hash(d + 40) * 0.8) / Double(farCount)) * w
                if Self.hash(d + 71) < 0.3 { continue }
                Self.addTree(&far, x, Self.ridgeY(2, x, w, bases[2], amps[2]) + 1, 7 + Self.hash(d + 9) * 7)
            }
            ctx.fill(far, with: .color(Self.c(0x6C8D74)))
            ctx.fill(ridges[2], with: .color(Self.c(ridgeColors[2])))
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 14))
                c.fill(Path(CGRect(x: 0, y: h * 0.7, width: w, height: h * 0.12)), with: .color(Self.c(0xF4EFE2, 0.42)))
            }

            var pines = [Path(), Path()]
            var golds = [Path(), Path(), Path()]
            let count = Int((w / 9).rounded())
            for i in 0..<count {
                let d = Double(i)
                let x = ((d + Self.hash(d + 3) * 0.9) / Double(count)) * w
                if Self.hash(d + 17) < 0.18 { continue }
                let y = Self.ridgeY(3, x, w, bases[3], amps[3]) + 1 + Self.hash(d + 5) * 5
                let tall = 15 + Self.hash(d + 11) * 15
                let stand = sin((x / w) * 7.4 + 0.6) * 0.5 + 0.5
                if Self.hash(d + 29) < 0.14 + stand * 0.42 {
                    Self.addTree(&golds[Int(Self.hash(d + 31) * 3)], x, y, tall * 0.94)
                } else {
                    Self.addTree(&pines[Int(Self.hash(d + 37) * 2)], x, y, tall)
                }
            }
            ctx.fill(pines[0], with: .color(Self.c(0x2C5A46)))
            ctx.fill(pines[1], with: .color(Self.c(0x356650)))
            ctx.fill(golds[0], with: .color(Self.c(0xD9A441)))
            ctx.fill(golds[1], with: .color(Self.c(0xC98A1B)))
            ctx.fill(golds[2], with: .color(Self.c(0xE2B65A)))
            ctx.fill(ridges[3], with: .color(Self.c(ridgeColors[3])))

            let melt = Gradient(stops: [
                .init(color: Self.c(0xF3EFE6, 0), location: 0),
                .init(color: Self.c(0xF3EFE6, 0.8), location: 0.55),
                .init(color: Self.c(0xF3EFE6), location: 1),
            ])
            ctx.fill(Path(CGRect(x: 0, y: h * 0.8, width: w, height: h * 0.2 + 1)),
                     with: .linearGradient(melt, startPoint: CGPoint(x: 0, y: h * 0.8), endPoint: CGPoint(x: 0, y: h)))

            let mark = CGRect(x: markX, y: markY, width: 26, height: 26)
            ctx.fill(Path(roundedRect: mark, cornerRadius: 8, style: .circular), with: .color(Self.c(0x1F5C45)))
            var tree = Path()
            Self.addTree(&tree, Double(markX) + 13, Double(markY) + 20, 15)
            ctx.fill(tree, with: .color(Self.c(0xF1D48A)))
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
