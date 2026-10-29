import SwiftUI
import UIKit

/// The menu and its artwork. Every dish is drawn in code, top-down, once, into three bitmaps:
/// the plate (its light never turns), the food (it spins with the wheel) and a light layer laid
/// over the food (a soft highlight and a darker rim, fixed, so the food turns under the light).
enum PastaPlateWheelArt {
    struct Dish {
        let name: String
        let line: String
        let price: String
        let bowl: Bool
    }

    static let dishes: [Dish] = [
        Dish(name: "Spaghetti Pomodoro", line: "Slow tomato, torn basil, olive oil", price: "$12.50", bowl: false),
        Dish(name: "Penne Arrabbiata", line: "Fiery tomato, garlic, chilli flakes", price: "$13.00", bowl: true),
        Dish(name: "Farfalle al Pesto", line: "Basil pesto, pine nuts, pecorino", price: "$14.50", bowl: false),
        Dish(name: "Fettuccine Bianca", line: "Butter, cream, cracked pepper", price: "$15.00", bowl: true),
        Dish(name: "Baked Mac & Cheese", line: "Three cheeses, crisp crumb top", price: "$11.50", bowl: false),
        Dish(name: "Ricotta Ravioli", line: "Brown butter, sage, lemon zest", price: "$16.00", bowl: true),
        Dish(name: "Lasagna al Forno", line: "Beef ragù, béchamel, golden top", price: "$17.50", bowl: false),
        Dish(name: "Shells & Peas", line: "Cream, sweet peas, smoked ham", price: "$13.50", bowl: true),
    ]

    /// The largest plate's diameter (pt); bitmaps are drawn at this size and only scaled down.
    static let plateSize: CGFloat = 164
    static let scale: CGFloat = 3
    /// Food radius as a fraction of the plate's radius.
    static func foodFraction(bowl: Bool) -> CGFloat { bowl ? 0.76 : 0.75 }

    struct Atlas {
        let plates: [UIImage]   // 0: white plate, 1: dark bowl
        let lights: [UIImage]
        let foods: [UIImage]
        let grain: UIImage
    }

    static let atlas: Atlas = {
        let plates = [plateImage(bowl: false), plateImage(bowl: true)]
        let lights = [lightImage(bowl: false), lightImage(bowl: true)]
        let foods = dishes.indices.map { foodImage($0) }
        return Atlas(plates: plates, lights: lights, foods: foods, grain: grainImage())
    }()

    // MARK: Colour and hash

    static func hash(_ n: Double) -> Double {
        let v = sin(n * 12.9898) * 43758.5453
        return v - floor(v)
    }

    static func uiColor(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: CGFloat(alpha))
    }

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color { Color(uiColor: uiColor(hex, alpha)) }

    /// Linear mix of two hex colours.
    static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> UInt32 {
        func ch(_ s: UInt32) -> UInt32 {
            let x = Double((a >> s) & 255), y = Double((b >> s) & 255)
            return UInt32((x + (y - x) * min(max(t, 0), 1)).rounded())
        }
        return (ch(16) << 16) | (ch(8) << 8) | ch(0)
    }

    // MARK: Pen — the few primitives every dish is drawn with (ported 1:1 to Compose and Skia)

    struct Pen {
        let cg: CGContext

        func circle(_ x: Double, _ y: Double, _ r: Double, _ hex: UInt32, _ a: Double = 1) {
            cg.setFillColor(uiColor(hex, a).cgColor)
            cg.fillEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
        }

        func ellipse(_ x: Double, _ y: Double, _ rx: Double, _ ry: Double, _ angle: Double, _ hex: UInt32, _ a: Double = 1) {
            cg.saveGState()
            cg.translateBy(x: x, y: y)
            cg.rotate(by: angle)
            cg.setFillColor(uiColor(hex, a).cgColor)
            cg.fillEllipse(in: CGRect(x: -rx, y: -ry, width: 2 * rx, height: 2 * ry))
            cg.restoreGState()
        }

        /// An open smooth line through the points (quadratic curves between midpoints), round caps.
        func stroke(_ p: [Double], _ width: Double, _ hex: UInt32, _ a: Double = 1) {
            cg.addPath(smooth(p, closed: false))
            cg.setStrokeColor(uiColor(hex, a).cgColor)
            cg.setLineWidth(width)
            cg.setLineCap(.round)
            cg.setLineJoin(.round)
            cg.strokePath()
        }

        /// A closed smooth shape through the points.
        func blob(_ p: [Double], _ hex: UInt32, _ a: Double = 1) {
            cg.addPath(smooth(p, closed: true))
            cg.setFillColor(uiColor(hex, a).cgColor)
            cg.fillPath()
        }

        /// A closed polygon with straight edges.
        func poly(_ p: [Double], _ hex: UInt32, _ a: Double = 1) {
            let path = CGMutablePath()
            path.move(to: CGPoint(x: p[0], y: p[1]))
            var i = 2
            while i < p.count { path.addLine(to: CGPoint(x: p[i], y: p[i + 1])); i += 2 }
            path.closeSubpath()
            cg.addPath(path)
            cg.setFillColor(uiColor(hex, a).cgColor)
            cg.fillPath()
        }

        func roundRect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ hex: UInt32, _ a: Double = 1) {
            cg.addPath(CGPath(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerWidth: r, cornerHeight: r, transform: nil))
            cg.setFillColor(uiColor(hex, a).cgColor)
            cg.fillPath()
        }

        /// A circle filled with a radial gradient; stops are (hex, alpha, location).
        func radial(_ x: Double, _ y: Double, _ r: Double, _ stops: [(UInt32, Double, Double)]) {
            let colors = stops.map { uiColor($0.0, $0.1).cgColor } as CFArray
            let locations = stops.map { CGFloat($0.2) }
            guard let gradient = CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors, locations: locations) else { return }
            cg.saveGState()
            cg.addEllipse(in: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r))
            cg.clip()
            cg.drawRadialGradient(gradient, startCenter: CGPoint(x: x, y: y), startRadius: 0,
                                  endCenter: CGPoint(x: x, y: y), endRadius: r, options: [])
            cg.restoreGState()
        }

        /// An arc stroke (angles in radians, clockwise on screen).
        func arc(_ x: Double, _ y: Double, _ r: Double, _ from: Double, _ to: Double, _ width: Double, _ hex: UInt32, _ a: Double = 1) {
            let path = CGMutablePath()
            path.addArc(center: CGPoint(x: x, y: y), radius: r, startAngle: from, endAngle: to, clockwise: false)
            cg.addPath(path)
            cg.setStrokeColor(uiColor(hex, a).cgColor)
            cg.setLineWidth(width)
            cg.setLineCap(.round)
            cg.strokePath()
        }

        func push(_ x: Double, _ y: Double, _ angle: Double, _ s: Double = 1) {
            cg.saveGState()
            cg.translateBy(x: x, y: y)
            cg.rotate(by: angle)
            cg.scaleBy(x: s, y: s)
        }

        func pop() { cg.restoreGState() }

        private func smooth(_ p: [Double], closed: Bool) -> CGPath {
            let n = p.count / 2
            let path = CGMutablePath()
            func pt(_ i: Int) -> CGPoint { CGPoint(x: p[2 * i], y: p[2 * i + 1]) }
            func mid(_ i: Int, _ j: Int) -> CGPoint { CGPoint(x: (p[2 * i] + p[2 * j]) / 2, y: (p[2 * i + 1] + p[2 * j + 1]) / 2) }
            if closed {
                path.move(to: mid(n - 1, 0))
                for i in 0..<n { path.addQuadCurve(to: mid(i, (i + 1) % n), control: pt(i)) }
                path.closeSubpath()
            } else {
                path.move(to: pt(0))
                if n > 2 { for i in 1..<(n - 1) { path.addQuadCurve(to: mid(i, i + 1), control: pt(i)) } }
                path.addLine(to: pt(n - 1))
            }
            return path
        }
    }

    // MARK: Shared shapes

    /// Points along a wobbly arc around the centre (a strand of pasta in a nest).
    static func strand(r: Double, a0: Double, sweep: Double, wobble: Double, freq: Double, phase: Double, steps: Int = 12) -> [Double] {
        var p: [Double] = []
        for s in 0...steps {
            let a = a0 + sweep * Double(s) / Double(steps)
            let rr = r + wobble * sin(a * freq + phase)
            p.append(rr * cos(a)); p.append(rr * sin(a))
        }
        return p
    }

    /// A blob outline: radius r with two harmonics.
    static func blobPoints(_ x: Double, _ y: Double, _ r: Double, _ k1: Double, _ k2: Double, _ phase: Double, count: Int = 16) -> [Double] {
        var p: [Double] = []
        for i in 0..<count {
            let a = 2 * Double.pi * Double(i) / Double(count)
            let rr = r * (1 + k1 * sin(3 * a + phase) + k2 * sin(5 * a + 2 * phase))
            p.append(x + rr * cos(a)); p.append(y + rr * sin(a))
        }
        return p
    }

    static func leaf(_ pen: Pen, _ x: Double, _ y: Double, _ len: Double, _ w: Double, _ angle: Double, _ hex: UInt32, _ vein: UInt32) {
        pen.push(x, y, angle)
        pen.blob([-len / 2, 0, -len * 0.18, -w / 2, len * 0.22, -w * 0.42, len / 2, 0, len * 0.22, w * 0.42, -len * 0.18, w / 2], hex)
        pen.ellipse(-len * 0.05, -w * 0.16, len * 0.3, w * 0.16, 0, 0xFFFFFF, 0.12)
        pen.stroke([-len * 0.48, 0, 0, -w * 0.03, len * 0.44, 0], len * 0.035, vein, 0.75)
        pen.pop()
    }

    /// Small specks scattered over the food disc (pepper, chilli, parsley, crumbs).
    static func specks(_ pen: Pen, seed: Double, count: Int, reach: Double, size: Double, colors: [UInt32], alpha: Double) {
        for k in 0..<count {
            let kk = Double(k)
            let rr = reach * sqrt(hash(seed + kk * 4.1))
            let a = 2 * Double.pi * hash(seed + kk * 4.1 + 1.3)
            let s = size * (0.6 + 0.8 * hash(seed + kk * 4.1 + 2.7))
            let hex = colors[k % colors.count]
            pen.ellipse(rr * cos(a), rr * sin(a), s, s * 0.62, 6.28 * hash(seed + kk * 4.1 + 3.9), hex, alpha)
        }
    }

    // MARK: Dishes (unit food disc: radius 1)

    static func drawFood(_ pen: Pen, _ i: Int) {
        let seed = Double(i) * 1000 + 17
        switch i {
        case 0: spaghetti(pen, seed)
        case 1: penne(pen, seed)
        case 2: farfalle(pen, seed)
        case 3: fettuccine(pen, seed)
        case 4: macAndCheese(pen, seed)
        case 5: ravioli(pen, seed)
        case 6: lasagna(pen, seed)
        default: shells(pen, seed)
        }
    }

    static func spaghetti(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0xE8C374, 1, 0), (0xD9AE5C, 1, 0.78), (0xD9AE5C, 0, 1)])
        var strands: [[Double]] = []
        for k in 0..<96 {
            let s = seed + Double(k) * 3
            strands.append(strand(r: 0.16 + 0.76 * sqrt(hash(s + 1)), a0: 2 * .pi * hash(s + 2), sweep: 1.0 + 1.8 * hash(s + 3),
                                  wobble: 0.035, freq: 2.5, phase: 6 * hash(s + 4)))
        }
        for p in strands { pen.stroke(p, 0.046, 0xA2722F, 0.5) }
        for (k, p) in strands.enumerated() {
            let h = hash(seed + Double(k) * 3 + 5)
            pen.stroke(p, 0.03, h < 0.33 ? 0xF3D791 : h < 0.66 ? 0xEBC877 : 0xDDB25E)
            if k % 2 == 0 { pen.stroke(p.map { $0 - 0.006 }, 0.01, 0xFFF4CF, 0.55) }
        }
        // Tomato sauce
        pen.blob(blobPoints(0.04, -0.02, 0.42, 0.1, 0.07, 1.2), 0xA92816)
        pen.radial(-0.02, -0.08, 0.4, [(0xE5502F, 1, 0), (0xD23D24, 0.9, 0.6), (0xC7341F, 0, 1)])
        for k in 0..<18 {
            let s = seed + 300 + Double(k) * 5
            let rr = 0.33 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1)
            let big = hash(s + 2)
            pen.circle(0.04 + rr * cos(a), -0.02 + rr * sin(a), 0.02 + 0.035 * big, k % 3 == 0 ? 0x8E1E12 : 0xE95E3D, k % 3 == 0 ? 0.4 : 0.5)
        }
        for k in 0..<5 {
            let s = seed + 400 + Double(k) * 7
            pen.stroke(strand(r: 0.36 + 0.08 * hash(s), a0: 2 * .pi * hash(s + 1), sweep: 0.7, wobble: 0.03, freq: 3, phase: 2, steps: 6),
                       0.034, 0xEBC877)
        }
        for k in 0..<7 {
            let s = seed + 500 + Double(k) * 3
            pen.circle(-0.14 + 0.2 * hash(s), -0.2 + 0.2 * hash(s + 1), 0.008 + 0.01 * hash(s + 2), 0xFFFFFF, 0.5)
        }
        leaf(pen, 0.06, -0.14, 0.36, 0.18, -0.6, 0x3D8A36, 0x86C76A)
        leaf(pen, 0.2, 0.08, 0.28, 0.15, 0.9, 0x357D30, 0x79BE5E)
        leaf(pen, -0.12, 0.1, 0.26, 0.13, 2.4, 0x418F39, 0x86C76A)
        specks(pen, seed: seed + 600, count: 18, reach: 0.85, size: 0.026, colors: [0xFFF5D8, 0xFBEBC0], alpha: 0.95)
    }

    static func penne(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0xB83E24, 1, 0), (0x8E2716, 1, 0.88), (0x8E2716, 0, 1)])
        let count = 52
        for k in 0..<count {
            let s = seed + Double(k) * 5
            let rr = 0.8 * sqrt(hash(s + 1)), a = 2 * Double.pi * hash(s + 2)
            let len = 0.3 + 0.06 * hash(s + 4), w = 0.13, cut = 0.055
            let shade = 0.4 + 0.6 * Double(k) / Double(count)
            pen.push(rr * cos(a), rr * sin(a), 2 * .pi * hash(s + 3))
            let body = [-len / 2 - cut, -w / 2, len / 2 - cut, -w / 2, len / 2 + cut, w / 2, -len / 2 + cut, w / 2]
            pen.poly(body.enumerated().map { $0.element + ($0.offset % 2 == 0 ? 0.016 : 0.024) }, 0x4A1208, 0.5)
            pen.poly(body, mix(0x9A3A1A, 0xD8743A, shade))
            pen.stroke([-len / 2 + 0.03, 0, len / 2 - 0.03, 0], w * 0.6, mix(0xBE5428, 0xF29A52, shade), 0.9)
            for y in [-0.38, -0.18, 0.18, 0.38] {
                pen.stroke([-len / 2 + 0.012 + cut * y * 2, w * y, len / 2 - 0.012 + cut * y * 2, w * y], 0.006, 0x6E2410, 0.3)
            }
            pen.stroke([-len / 2 + 0.04, -w * 0.13, len / 2 - 0.05, -w * 0.13], w * 0.18, 0xFFD7A0, 0.5)
            pen.ellipse(len / 2 - 0.012, 0, 0.022, w * 0.4, -atan(2 * cut / w), 0x4E1508, 0.85)
            pen.circle(-len * 0.18, w * 0.22, 0.03, 0xC23A20, 0.75)
            pen.circle(len * 0.12, -w * 0.25, 0.022, 0xB0301A, 0.6)
            pen.pop()
        }
        specks(pen, seed: seed + 400, count: 6, reach: 0.7, size: 0.04, colors: [0xF3E6C4], alpha: 0.95)
        specks(pen, seed: seed + 500, count: 34, reach: 0.88, size: 0.018, colors: [0x7E1A0C, 0xE0502E], alpha: 0.9)
        specks(pen, seed: seed + 600, count: 26, reach: 0.85, size: 0.02, colors: [0x4C8B36, 0x6FAE4A], alpha: 0.95)
    }

    static func farfalle(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0x88B04A, 1, 0), (0x6A9236, 1, 0.88), (0x6A9236, 0, 1)])
        let shape: [Double] = [0, -0.035, 0.19, -0.11, 0.215, -0.075, 0.195, -0.04, 0.215, 0, 0.195, 0.04, 0.215, 0.075, 0.19, 0.11,
                               0, 0.035, -0.19, 0.11, -0.215, 0.075, -0.195, 0.04, -0.215, 0, -0.195, -0.04, -0.215, -0.075, -0.19, -0.11]
        let count = 44
        for k in 0..<count {
            let s = seed + Double(k) * 5
            let rr = 0.82 * sqrt(hash(s + 1)), a = 2 * Double.pi * hash(s + 2)
            let shade = 0.45 + 0.55 * Double(k) / Double(count)
            pen.push(rr * cos(a), rr * sin(a), 2 * .pi * hash(s + 3), 1.0 + 0.2 * hash(s + 4))
            pen.poly(shape.enumerated().map { $0.element + ($0.offset % 2 == 0 ? 0.012 : 0.018) }, 0x2F4A16, 0.45)
            pen.poly(shape, mix(0xC2C26A, 0xF2E8A8, shade))
            pen.ellipse(0.12, -0.03, 0.05, 0.022, 0.4, 0x5F8F2E, 0.5)
            pen.ellipse(-0.11, 0.04, 0.045, 0.02, -0.3, 0x5F8F2E, 0.45)
            for sx in [-1.0, 1.0] {
                pen.stroke([sx * 0.05, -0.03, sx * 0.17, -0.085], 0.007, 0x8E9A46, 0.5)
                pen.stroke([sx * 0.05, 0.03, sx * 0.17, 0.085], 0.007, 0x8E9A46, 0.5)
            }
            pen.ellipse(0, 0, 0.032, 0.046, 0, 0x9DA552)
            pen.stroke([-0.012, -0.03, -0.012, 0.03], 0.007, 0x7C8438, 0.7)
            pen.stroke([0.012, -0.03, 0.012, 0.03], 0.007, 0x7C8438, 0.7)
            pen.pop()
        }
        for k in 0..<14 {
            let s = seed + 400 + Double(k) * 4
            let rr = 0.8 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1), ang = 6.28 * hash(s + 2)
            pen.ellipse(rr * cos(a) + 0.008, rr * sin(a) + 0.01, 0.04, 0.022, ang, 0x3A4A18, 0.35)
            pen.ellipse(rr * cos(a), rr * sin(a), 0.04, 0.022, ang, 0xF2E3BA)
            pen.ellipse(rr * cos(a) - 0.008, rr * sin(a) - 0.006, 0.022, 0.01, ang, 0xFFF9E8, 0.9)
        }
        specks(pen, seed: seed + 500, count: 9, reach: 0.75, size: 0.06, colors: [0xFBF4DF], alpha: 0.95)
        leaf(pen, -0.1, -0.12, 0.34, 0.17, 0.5, 0x3D8A36, 0x86C76A)
        leaf(pen, 0.14, 0.02, 0.28, 0.14, 2.1, 0x357D30, 0x79BE5E)
    }

    static func fettuccine(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0xF7EED6, 1, 0), (0xEAD8AC, 1, 0.88), (0xEAD8AC, 0, 1)])
        var ribbons: [[Double]] = []
        for k in 0..<30 {
            let s = seed + Double(k) * 3
            ribbons.append(strand(r: 0.18 + 0.7 * hash(s + 1), a0: 2 * .pi * hash(s + 2), sweep: 1.4 + 1.6 * hash(s + 3),
                                  wobble: 0.05, freq: 2, phase: 6 * hash(s + 4)))
        }
        for p in ribbons { pen.stroke(p, 0.13, 0x9C7A3A, 0.6) }
        for (k, p) in ribbons.enumerated() {
            pen.stroke(p, 0.105, mix(0xE3BC6E, 0xF3D695, hash(seed + Double(k) * 3 + 5)))
            pen.stroke(p.map { $0 - 0.016 }, 0.022, 0xFFF8E2, 0.65)
        }
        for k in 0..<5 {
            let s = seed + 300 + Double(k) * 4
            let rr = 0.7 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1)
            pen.ellipse(rr * cos(a), rr * sin(a), 0.1, 0.06, 6.28 * hash(s + 2), 0xFBF4E2, 0.55)
        }
        specks(pen, seed: seed + 400, count: 70, reach: 0.9, size: 0.01, colors: [0x3A332B, 0x5A5046], alpha: 0.85)
        specks(pen, seed: seed + 500, count: 20, reach: 0.8, size: 0.022, colors: [0x5A9A3E, 0x3F7F2E], alpha: 0.95)
    }

    static func macAndCheese(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0xF3B847, 1, 0), (0xDD962D, 1, 0.88), (0xDD962D, 0, 1)])
        let count = 90
        for k in 0..<count {
            let s = seed + Double(k) * 5
            let rr = 0.86 * sqrt(hash(s + 1)), a = 2 * Double.pi * hash(s + 2)
            let shade = 0.4 + 0.6 * Double(k) / Double(count)
            pen.push(rr * cos(a), rr * sin(a), 2 * .pi * hash(s + 3))
            pen.push(0.01, 0.014, 0)
            pen.arc(0, 0, 0.075, -1.3, 1.3, 0.085, 0xA9661A, 0.5)
            pen.pop()
            pen.arc(0, 0, 0.075, -1.3, 1.3, 0.07, mix(0xEFAE3E, 0xFBD772, shade))
            pen.arc(0, 0, 0.066, -1.0, 0.9, 0.018, 0xFFF0B8, 0.7)
            pen.pop()
        }
        for k in 0..<7 {
            let s = seed + 300 + Double(k) * 4
            let rr = 0.65 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1)
            pen.radial(rr * cos(a), rr * sin(a), 0.12 + 0.08 * hash(s + 2), [(0xA0521A, 0.5, 0), (0xA0521A, 0, 1)])
        }
        specks(pen, seed: seed + 400, count: 110, reach: 0.9, size: 0.022, colors: [0xC98633, 0xA9621F, 0xE9B455, 0x8E4E17], alpha: 0.9)
        for k in 0..<14 {
            let s = seed + 600 + Double(k) * 4
            let rr = 0.75 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1), ang = 6.28 * hash(s + 2)
            let x = rr * cos(a), y = rr * sin(a)
            pen.stroke([x - 0.035 * cos(ang), y - 0.035 * sin(ang), x + 0.035 * cos(ang), y + 0.035 * sin(ang)], 0.015, 0x4F8E33)
        }
    }

    static func ravioli(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0xDDA752, 1, 0), (0xB5752C, 0.92, 0.74), (0xB5752C, 0, 1)])
        var spots: [(Double, Double, Double)] = []
        for k in 0..<5 {
            let a = Double(k) * 2 * .pi / 5 + 0.18
            spots.append((0.53 * cos(a), 0.53 * sin(a), a + 0.3))
        }
        spots.append((0, 0, 0.2))
        let side = 0.46
        for (k, sp) in spots.enumerated() {
            pen.push(sp.0, sp.1, sp.2)
            pen.roundRect(-side / 2 + 0.02, -side / 2 + 0.03, side, side, 0.07, 0x4A2A0E, 0.5)
            pen.roundRect(-side / 2, -side / 2, side, side, 0.07, 0xE6CC92)
            pen.radial(-0.02, -0.03, 0.2, [(0xFFF4D6, 0.95, 0), (0xF2DEAD, 0.7, 0.6), (0xF0DCAA, 0, 1)])
            pen.stroke([-0.13, 0.12, 0, 0.16, 0.13, 0.12], 0.012, 0xD3B87C, 0.5)
            let inset = side / 2 - 0.035
            for j in 0..<7 {
                let u = -inset + 2 * inset * Double(j) / 6
                pen.circle(u, -inset, 0.011, 0xC9AA6A, 0.8)
                pen.circle(u, inset, 0.011, 0xC9AA6A, 0.8)
                pen.circle(-inset, u, 0.011, 0xC9AA6A, 0.8)
                pen.circle(inset, u, 0.011, 0xC9AA6A, 0.8)
            }
            pen.ellipse(-0.06, -0.08, 0.06, 0.025, -0.5, 0xFFFFFF, 0.35 + 0.1 * Double(k % 2))
            pen.pop()
        }
        leaf(pen, 0.0, -0.3, 0.26, 0.12, 0.3, 0x56703A, 0x8BA45E)
        leaf(pen, 0.3, 0.22, 0.24, 0.11, 2.2, 0x4E6833, 0x8BA45E)
        leaf(pen, -0.34, 0.12, 0.25, 0.12, -1.9, 0x56703A, 0x8BA45E)
        leaf(pen, -0.1, 0.36, 0.22, 0.1, 1.2, 0x4E6833, 0x8BA45E)
        for k in 0..<16 {
            let s = seed + 500 + Double(k) * 4
            let rr = 0.85 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1), ang = 6.28 * hash(s + 2)
            let x = rr * cos(a), y = rr * sin(a)
            pen.stroke([x, y, x + 0.03 * cos(ang), y + 0.03 * sin(ang), x + 0.05 * cos(ang + 0.9), y + 0.05 * sin(ang + 0.9)], 0.012, 0xF0CF3A)
        }
        specks(pen, seed: seed + 600, count: 20, reach: 0.9, size: 0.01, colors: [0x3A2A1B], alpha: 0.8)
    }

    static func lasagna(_ pen: Pen, _ seed: Double) {
        pen.blob(blobPoints(0, 0, 0.9, 0.06, 0.04, 0.7), 0xA42C19)
        pen.radial(-0.1, -0.12, 0.85, [(0xC8432A, 1, 0), (0xB43722, 0.6, 0.7), (0xA42C19, 0, 1)])
        specks(pen, seed: seed + 50, count: 16, reach: 0.85, size: 0.03, colors: [0x7E1E10, 0xD9573A], alpha: 0.7)
        pen.push(0, 0, -0.14)
        let side = 1.1, h = side / 2
        pen.roundRect(-h + 0.07, -h + 0.09, side, side, 0.1, 0x5E1E0E, 0.5)
        pen.roundRect(-h + 0.06, -h + 0.075, side, side, 0.1, 0xEFD7A4)
        pen.roundRect(-h + 0.042, -h + 0.052, side, side, 0.1, 0x8E3218)
        pen.roundRect(-h + 0.022, -h + 0.028, side, side, 0.1, 0xF1DDB0)
        pen.roundRect(-h, -h, side, side, 0.1, 0xE3A443)
        for k in 0..<34 {
            let s = seed + 100 + Double(k) * 4
            let x = -h + 0.06 + (side - 0.12) * hash(s), y = -h + 0.06 + (side - 0.12) * hash(s + 1)
            pen.radial(x, y, 0.05 + 0.08 * hash(s + 2), [(0xB0612A, 0.6, 0), (0xB0612A, 0, 1)])
        }
        for k in 0..<20 {
            let s = seed + 300 + Double(k) * 4
            let x = -h + 0.05 + (side - 0.1) * hash(s), y = -h + 0.05 + (side - 0.1) * hash(s + 1)
            pen.circle(x, y, 0.016 + 0.03 * hash(s + 2), 0x84401A, 0.5)
        }
        for k in 0..<30 {
            let s = seed + 500 + Double(k) * 4
            let x = -h + 0.05 + (side - 0.1) * hash(s), y = -h + 0.05 + (side - 0.1) * hash(s + 1)
            let r = 0.014 + 0.026 * hash(s + 2)
            pen.circle(x, y, r, 0xF7D27C, 0.85)
            pen.circle(x - r * 0.3, y - r * 0.3, r * 0.35, 0xFFFFFF, 0.6)
        }
        for k in 0..<5 {
            let s = seed + 700 + Double(k) * 4
            let edge = Int(hash(s) * 4)
            let u = -h + 0.12 + (side - 0.24) * hash(s + 1)
            let pos = [(u, -h + 0.04), (h - 0.04, u), (u, h - 0.04), (-h + 0.04, u)][edge]
            pen.circle(pos.0, pos.1, 0.05 + 0.03 * hash(s + 2), 0xB83A20, 0.85)
        }
        leaf(pen, 0.16, -0.12, 0.3, 0.15, -0.8, 0x3D8A36, 0x86C76A)
        leaf(pen, 0.28, 0.02, 0.24, 0.12, 0.4, 0x357D30, 0x79BE5E)
        pen.pop()
    }

    static func shells(_ pen: Pen, _ seed: Double) {
        pen.radial(0, 0, 1, [(0xF5E8C8, 1, 0), (0xE6CF9C, 1, 0.88), (0xE6CF9C, 0, 1)])
        func peas(_ from: Int, _ to: Int) {
            for k in from..<to {
                let s = seed + 400 + Double(k) * 4
                let rr = 0.82 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1)
                let x = rr * cos(a), y = rr * sin(a)
                pen.circle(x + 0.01, y + 0.014, 0.05, 0x2E4A12, 0.4)
                pen.circle(x, y, 0.05, 0x5E9C2A)
                pen.radial(x - 0.015, y - 0.015, 0.04, [(0xC2E88A, 0.95, 0), (0x8CC44E, 0.5, 0.5), (0x8CC44E, 0, 1)])
            }
        }
        peas(0, 10)
        let count = 52
        for k in 0..<count {
            let s = seed + Double(k) * 5
            let rr = 0.82 * sqrt(hash(s + 1)), a = 2 * Double.pi * hash(s + 2)
            let shade = 0.45 + 0.55 * Double(k) / Double(count)
            pen.push(rr * cos(a), rr * sin(a), 2 * .pi * hash(s + 3), 1.0 + 0.25 * hash(s + 4))
            pen.ellipse(0.012, 0.018, 0.12, 0.095, 0, 0x8F6E35, 0.45)
            pen.ellipse(0, 0, 0.12, 0.095, 0, mix(0xE2B565, 0xF7DA95, shade))
            for j in 0..<7 {
                let ang = -1.15 + 2.3 * Double(j) / 6
                pen.stroke([-0.09, 0, -0.09 + 0.2 * cos(ang), 0.088 * sin(ang) * 1.05], 0.007, 0xC99A4C, 0.55)
            }
            pen.ellipse(0.025, 0, 0.055, 0.04, 0, 0xC58F45, 0.7)
            pen.ellipse(0.03, 0, 0.035, 0.022, 0, 0xFBF0D6)
            pen.circle(-0.1, 0, 0.02, 0xD9AB5C)
            pen.pop()
        }
        peas(10, 28)
        for k in 0..<10 {
            let s = seed + 600 + Double(k) * 4
            let rr = 0.78 * sqrt(hash(s)), a = 2 * Double.pi * hash(s + 1)
            pen.push(rr * cos(a), rr * sin(a), 6.28 * hash(s + 2))
            pen.roundRect(-0.04, -0.035, 0.08, 0.07, 0.015, 0xE4978A)
            pen.roundRect(-0.04, -0.012, 0.08, 0.018, 0.008, 0xF6C9BE, 0.9)
            pen.pop()
        }
        specks(pen, seed: seed + 700, count: 30, reach: 0.9, size: 0.01, colors: [0x3A332B], alpha: 0.8)
    }

    // MARK: Bitmaps

    private static func render(_ side: CGFloat, _ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = false
        format.preferredRange = .standard
        return UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { ctx in
            draw(ctx.cgContext)
        }
    }

    /// The plate (or bowl) under the food, lit from the upper left.
    static func plateImage(bowl: Bool) -> UIImage {
        let d = plateSize
        return render(d) { cg in
            let pen = Pen(cg: cg)
            let c = Double(d) / 2, r = c - 1
            if bowl {
                pen.radial(c, c, r, [(0x1D1B19, 1, 0), (0x1D1B19, 1, 0.77), (0x37332E, 1, 0.85), (0x24211E, 1, 0.94), (0x0E0D0C, 1, 1)])
                pen.radial(c, c, r * 0.78, [(0x2A2723, 1, 0), (0x1B1917, 1, 0.9), (0x121110, 1, 1)])
                pen.arc(c, c, r * 0.88, .pi * 1.08, .pi * 1.55, r * 0.035, 0xFFFFFF, 0.14)
                pen.arc(c, c, r * 0.795, .pi * 0.1, .pi * 0.6, 1.5, 0xFFFFFF, 0.1)
                cg.setStrokeColor(uiColor(0x000000, 0.55).cgColor)
                cg.setLineWidth(1)
                cg.strokeEllipse(in: CGRect(x: c - r + 0.5, y: c - r + 0.5, width: 2 * r - 1, height: 2 * r - 1))
            } else {
                pen.radial(c, c, r, [(0xF6F4EF, 1, 0), (0xF6F4EF, 1, 0.75), (0xFFFFFF, 1, 0.82), (0xF1EEE8, 1, 0.94), (0xD9D5CC, 1, 1)])
                pen.radial(c, c, r * 0.8, [(0x000000, 0, 0.86), (0x6B5E48, 0.1, 0.95), (0x6B5E48, 0, 1)])
                pen.arc(c, c, r * 0.885, .pi * 1.12, .pi * 1.5, r * 0.03, 0xFFFFFF, 0.7)
                pen.arc(c, c, r * 0.89, .pi * 0.15, .pi * 0.55, r * 0.05, 0xC9C3B8, 0.18)
                cg.setStrokeColor(uiColor(0xC7C1B6, 0.8).cgColor)
                cg.setLineWidth(1)
                cg.strokeEllipse(in: CGRect(x: c - r + 0.5, y: c - r + 0.5, width: 2 * r - 1, height: 2 * r - 1))
            }
        }
    }

    /// The food of dish i, centred, filling a square of side 2 · food radius · 1.08.
    static func foodImage(_ i: Int) -> UIImage {
        let fr = Double(plateSize) / 2 * Double(foodFraction(bowl: dishes[i].bowl))
        let side = CGFloat(fr * 2 * 1.08)
        return render(side) { cg in
            let pen = Pen(cg: cg)
            cg.translateBy(x: side / 2, y: side / 2)
            cg.scaleBy(x: fr, y: fr)
            drawFood(pen, i)
        }
    }

    /// The fixed light over the food: a soft highlight upper left and a darker rim (food is mounded).
    static func lightImage(bowl: Bool) -> UIImage {
        let fr = Double(plateSize) / 2 * Double(foodFraction(bowl: bowl))
        let side = CGFloat(fr * 2 * 1.08)
        return render(side) { cg in
            let pen = Pen(cg: cg)
            let c = Double(side) / 2
            pen.radial(c, c, fr * 0.98, [(0x000000, 0, 0), (0x000000, 0, 0.55), (0x2A1608, 0.22, 0.9), (0x2A1608, 0, 1)])
            pen.radial(c - fr * 0.32, c - fr * 0.38, fr * 0.8, [(0xFFFFFF, 0.2, 0), (0xFFFFFF, 0.06, 0.5), (0xFFFFFF, 0, 1)])
        }
    }

    /// 128 × 128 tile of static grain (a hash per pixel), drawn at 3 % over the background.
    static func grainImage() -> UIImage {
        let n = 128
        var bytes = [UInt8](repeating: 0, count: n * n * 4)
        for y in 0..<n {
            for x in 0..<n {
                let v = hash(Double(x) * 1.7 + Double(y) * 113.3 + 0.5)
                let o = (y * n + x) * 4
                let g: UInt8 = v > 0.5 ? 255 : 0
                let a = UInt8(abs(v - 0.5) * 2 * 255)
                // premultiplied RGBA
                let p = UInt8(Int(g) * Int(a) / 255)
                bytes[o] = p; bytes[o + 1] = p; bytes[o + 2] = p; bytes[o + 3] = a
            }
        }
        let provider = CGDataProvider(data: Data(bytes) as CFData)!
        let image = CGImage(width: n, height: n, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: n * 4,
                            space: CGColorSpace(name: CGColorSpace.sRGB)!,
                            bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                            provider: provider, decode: nil, shouldInterpolate: false, intent: .defaultIntent)!
        return UIImage(cgImage: image, scale: 2, orientation: .up)
    }
}
