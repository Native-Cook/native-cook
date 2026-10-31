import SwiftUI

// The same painter's order and geometry as cho-art.ts. Static paths are built once.
enum CashHandoffOnboardingArt {
    typealias M = CashHandoffOnboardingMotion
    struct Geometry {
        let panel: Path
        let washClip: Path
        init(_ l: M.Layout) {
            panel = Path(CGRect(x: 0, y: 0, width: l.width, height: l.panel))
            washClip = Path(roundedRect: CGRect(x: 0, y: -40, width: l.width, height: l.panel + 40), cornerRadius: 34)
        }
    }
    static let skin = CashHandoffOnboardingResources.color(0x8a5636)
    static let line = CashHandoffOnboardingResources.color(0x2a170c)
    static let arm: Path = {
        var p = Path(); p.move(to: CGPoint(x: -260, y: -20)); p.addLine(to: CGPoint(x: 0, y: -22))
        p.addLine(to: CGPoint(x: 0, y: 22)); p.addLine(to: CGPoint(x: -260, y: 26)); p.closeSubpath(); return p
    }()
    static let palm = Path(roundedRect: CGRect(x: -6, y: -26, width: 52, height: 50), cornerRadius: 20)
    static let bill = Path(roundedRect: CGRect(x: -46, y: -23, width: 92, height: 46), cornerRadius: 5)
    static let inner = Path(roundedRect: CGRect(x: -40, y: -17, width: 80, height: 34), cornerRadius: 3)
    static let disc = Path(ellipseIn: CGRect(x: -13, y: -13, width: 26, height: 26))
    static let creases = [Path(ellipseIn: CGRect(x: 8, y: 2, width: 30, height: 12)), Path(ellipseIn: CGRect(x: 8, y: -8, width: 30, height: 12))]
    static let sideDiscs = [-32.0, 32.0].map { Path(ellipseIn: CGRect(x: $0 - 4, y: -4, width: 8, height: 8)) }
    // Skia interpolates straight sRGB components. Subdivide once to preserve this
    // curve through SwiftUI's premultiplied interpolation; no per-frame image processing.
    static let washGradient: Gradient = {
        var stops: [Gradient.Stop] = []
        let colors = [(150.0, 255.0, 140.0, 0.95), (110.0, 235.0, 150.0, 0.45), (110.0, 235.0, 150.0, 0.0)]
        let positions = [0.0, 0.45, 1.0]
        for segment in 0..<2 {
            let a = colors[segment], b = colors[segment + 1]
            for i in 0..<64 {
                let k = Double(i) / 64
                stops.append(.init(color: Color(.sRGB, red: M.mix(a.0, b.0, k) / 255,
                    green: M.mix(a.1, b.1, k) / 255, blue: M.mix(a.2, b.2, k) / 255,
                    opacity: M.mix(a.3, b.3, k)), location: M.mix(positions[segment], positions[segment + 1], k)))
            }
        }
        stops.append(.init(color: Color(.sRGB, red: 110/255, green: 235/255, blue: 150/255, opacity: 0), location: 1))
        return Gradient(stops: stops)
    }()
    static func stroke(_ c: inout GraphicsContext, _ p: Path, _ w: Double, _ color: Color) {
        c.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: .round, lineJoin: .round))
    }
    static func finger(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ a: Double, _ l1: Double, _ l2: Double, _ bend: Double, _ w: Double, _ color: Color) {
        let x1 = x + cos(a) * l1, y1 = y + sin(a) * l1
        var p = Path(); p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: x1, y: y1))
        p.addLine(to: CGPoint(x: x1 + cos(a + bend) * l2, y: y1 + sin(a + bend) * l2))
        stroke(&c, p, w + 3, line); stroke(&c, p, w, color)
    }
    static func hand(_ context: GraphicsContext, _ x: Double, _ y: Double, _ dir: Double, _ curl: Double, _ up: Bool, _ layer: Int) {
        var c = context; c.translateBy(x: x, y: y); c.scaleBy(x: dir, y: 1)
        let s = up ? -1.0 : 1.0
        if layer == 0 {
            c.fill(arm, with: .color(skin)); stroke(&c, arm, 3, line)
            c.fill(palm, with: .color(skin)); stroke(&c, palm, 3, line)
            c.fill(creases[up ? 1 : 0], with: .color(CashHandoffOnboardingResources.color(0x6a3f26)))
            let a = (up ? -0.9 : 0.9) + curl * (up ? 0.5 : -0.5)
            finger(&c, 14, up ? -18 : 18, a, 20, 15, s * curl * 0.9, 13, skin)
        } else {
            for i in 0..<4 {
                let len = i == 0 || i == 3 ? 0.85 : 1.0
                finger(&c, 42, -18 + Double(i) * 12, (Double(i) - 1.5) * 0.06 + s * curl * 1.1,
                       22 * len, 18 * len, s * curl * 1.4, 12, i % 2 == 0 ? skin : CashHandoffOnboardingResources.color(0x93603e))
            }
        }
    }
    static func cash(_ context: GraphicsContext, _ x: Double, _ y: Double, _ rot: Double, _ fan: Double) {
        var c = context; c.translateBy(x: x, y: y); c.rotate(by: .degrees(rot))
        for i in 0..<3 {
            var b = c; b.rotate(by: .degrees(Double(i - 1) * (6 + 10 * fan))); b.translateBy(x: Double(i - 1) * 3, y: Double(i - 1) * -3)
            b.fill(bill, with: .color(CashHandoffOnboardingResources.color(i == 2 ? 0x9bd48a : 0x86c574)))
            stroke(&b, bill, 2.6, CashHandoffOnboardingResources.color(0x2f5a26))
            stroke(&b, inner, 1.4, CashHandoffOnboardingResources.color(0x4f8a42))
            if i == 2 {
                let light = CashHandoffOnboardingResources.color(0xc7ecb8)
                b.fill(disc, with: .color(light)); stroke(&b, disc, 1.6, CashHandoffOnboardingResources.color(0x2f5a26))
                var text = b; text.translateBy(x: -5, y: 6)
                text.fill(CashHandoffOnboardingResources.shared.dollar, with: .color(CashHandoffOnboardingResources.color(0x2f5a26)))
                for path in sideDiscs { b.fill(path, with: .color(light)) }
            }
        }
    }
    static func draw(_ context: GraphicsContext, _ l: M.Layout, _ sc: M.Scene, _ t: Double, _ now: Double, _ geometry: Geometry) {
        var c = context
        c.clip(to: geometry.panel)
        let since = now - sc.caughtAt
        if sc.caughtAt > -5 && since >= 0 && since < 1.8 {
            var wash = c
            wash.clip(to: geometry.washClip)
            wash.opacity = min(1, since / 0.15) * (1 - M.smooth((since - 0.5) / 1.3))
            wash.fill(geometry.panel, with: .radialGradient(washGradient, center: CGPoint(x: l.palm.x + sc.rOff, y: l.palm.y), startRadius: 0, endRadius: 120 + 260 * M.smooth(since / 0.9)))
        }
        let bob = sin(2 * .pi * 2 * t / M.LOOP) * 2
        hand(c, 70 + sc.gOff, l.top + 300 + bob, 1, sc.gCurl, false, 0)
        hand(c, l.width - 70 + sc.rOff, l.top + 372 - bob, -1, sc.rCurl, true, 0)
        cash(c, sc.cx, sc.cy + (sc.caughtAt < 0 ? bob : -bob), sc.rot, sc.fan)
        hand(c, 70 + sc.gOff, l.top + 300 + bob, 1, sc.gCurl, false, 1)
        hand(c, l.width - 70 + sc.rOff, l.top + 372 - bob, -1, sc.rCurl, true, 1)
        if sc.caughtAt > -5 && since >= 0 && since < 1.6 {
            var ring = c; ring.opacity = 1 - M.smooth((since - 0.4) / 0.6)
            let d = 30 + (1 - exp(-since * 6)) * 46
            for i in 0..<8 {
                let a = Double(i) / 8 * .pi * 2, r = 3 + Double(i % 2) * 2
                stroke(&ring, Path(ellipseIn: CGRect(x: l.palm.x + sc.rOff + cos(a) * d - r, y: l.palm.y + sin(a) * d - r, width: r * 2, height: r * 2)), 2, .white)
            }
            var text = c; text.opacity = sin(M.clamp(since / 1.6) * .pi)
            text.translateBy(x: l.palm.x + sc.rOff - 26, y: l.palm.y - 60 - M.easeOut(since / 1.6) * 40)
            text.fill(CashHandoffOnboardingResources.shared.plus, with: .color(.white))
        }
    }
}
