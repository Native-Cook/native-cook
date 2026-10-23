import CoreText
import SwiftUI

/// Drawing: the cassette (in a 362 × 229 design space), the transport keys and the VU meters.
enum TapeReelPhysicsArt {
    typealias M = TapeReelPhysicsModel

    static func rgb(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "TapeReelPhysics-GochiHand-Regular", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()

    static let amber = rgb(0xF2A93B)
    static let size = CGSize(width: 362, height: 229)
    static let supplyCenter = CGPoint(x: 107, y: 138), takeUpCenter = CGPoint(x: 255, y: 138)
    static let leftGuide = CGPoint(x: 81, y: 206), rightGuide = CGPoint(x: 281, y: 206)
    static let guideR = 5.0
    /// The span between the guides runs along y = 211 from x = 81 to 281.
    static let spanY = 211.0, spanX0 = 81.0, spanX1 = 281.0
    static let window = CGRect(x: 54, y: 86, width: 254, height: 105)

    /// The point where tape leaves a pack of radius r for a point p, on the side away from the cassette's middle.
    static func tangent(_ c: CGPoint, _ r: Double, to p: CGPoint, left: Bool) -> CGPoint {
        let dx = p.x - c.x, dy = p.y - c.y
        let d = max(hypot(dx, dy), r + 0.01)
        let phi = atan2(dy, dx), a = acos(r / d)
        let t1 = CGPoint(x: c.x + r * cos(phi + a), y: c.y + r * sin(phi + a))
        let t2 = CGPoint(x: c.x + r * cos(phi - a), y: c.y + r * sin(phi - a))
        return left ? (t1.x < t2.x ? t1 : t2) : (t1.x > t2.x ? t1 : t2)
    }

    /// A slack run of tape from a to b, bowed `sag` points to one side (−1 left, +1 right).
    static func slackRun(_ a: CGPoint, _ b: CGPoint, sag: Double, side: Double) -> Path {
        var p = Path()
        p.move(to: a)
        let dx = b.x - a.x, dy = b.y - a.y, len = max(hypot(dx, dy), 0.01)
        // The normal that points to `side`.
        var nx = -dy / len, ny = dx / len
        if nx * side < 0 { nx = -nx; ny = -ny }
        for i in 1...16 {
            let u = Double(i) / 16
            let o = sag * sin(.pi * u)
            p.addLine(to: CGPoint(x: a.x + dx * u + nx * o, y: a.y + dy * u + ny * o))
        }
        return p
    }

    // MARK: Cassette

    static func cassette(_ ctx: inout GraphicsContext, frame f: M.Frame, t: Double, still: Bool, labelFont: Font) {
        let w = size.width, h = size.height
        let shell = Path(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerRadius: 14, style: .continuous)
        ctx.fill(shell, with: .linearGradient(Gradient(colors: [rgb(0xF0E9D8), rgb(0xD9CFB7)]),
                                              startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        ctx.stroke(shell, with: .color(rgb(0xBDB197)), lineWidth: 1)
        ctx.stroke(Path(roundedRect: CGRect(x: 5, y: 5, width: w - 10, height: h - 10), cornerRadius: 10, style: .continuous),
                   with: .color(.white.opacity(0.35)), lineWidth: 1)
        // Screws.
        for (x, y) in [(14.0, 14.0), (w - 14, 14), (14, h - 14), (w - 14, h - 14), (w / 2, 182)] {
            ctx.fill(Path(ellipseIn: CGRect(x: x - 5, y: y - 5, width: 10, height: 10)), with: .color(rgb(0xBFB49B)))
            var slot = Path()
            slot.move(to: CGPoint(x: x - 3.5 * cos(0.5), y: y - 3.5 * sin(0.5)))
            slot.addLine(to: CGPoint(x: x + 3.5 * cos(0.5), y: y + 3.5 * sin(0.5)))
            ctx.stroke(slot, with: .color(rgb(0x8D836C)), lineWidth: 1.2)
        }

        // Label.
        let label = Path(roundedRect: CGRect(x: 22, y: 12, width: 318, height: 66), cornerRadius: 6)
        ctx.fill(label, with: .color(rgb(0xFBF8F0)))
        ctx.drawLayer { g in
            g.clip(to: label)
            g.fill(Path(CGRect(x: 22, y: 12, width: 318, height: 9)), with: .color(rgb(0xE4572E)))
            g.fill(Path(CGRect(x: 22, y: 21, width: 318, height: 2.5)), with: .color(rgb(0xF2A93B)))
        }
        for y in [55.0, 68.0] {
            var rule = Path()
            rule.move(to: CGPoint(x: 56, y: y)); rule.addLine(to: CGPoint(x: 330, y: y))
            ctx.stroke(rule, with: .color(rgb(0x6C8FD8, 0.55)), lineWidth: 0.8)
        }
        ctx.stroke(Path(ellipseIn: CGRect(x: 28, y: 36, width: 20, height: 20)), with: .color(rgb(0x2B2B2B)), lineWidth: 1.2)
        ctx.draw(Text("A").font(.system(size: 12, weight: .bold)).foregroundColor(rgb(0x2B2B2B)), at: CGPoint(x: 38, y: 46))
        ctx.draw(Text("Night Drive Mix").font(labelFont).foregroundColor(rgb(0x1F3A8A)), at: CGPoint(x: 58, y: 53), anchor: .bottomLeading)
        ctx.draw(Text("C60").font(.system(size: 10, weight: .semibold)).foregroundColor(rgb(0x6B6355)), at: CGPoint(x: 328, y: 34), anchor: .trailing)

        // Window, packs and hubs.
        let win = Path(roundedRect: window, cornerRadius: 10, style: .continuous)
        ctx.fill(win, with: .color(rgb(0x100D0A)))
        let rs = M.supply(f.s), rt = M.takeUp(f.s)
        var extra = 0.0
        if let p = f.pluck {
            extra = p.releasedAt == nil ? p.d : M.ringing(p.x, x: p.x, d: p.d, tau: t - (p.releasedAt ?? t)) * (still ? 0 : 1)
        }
        let as_ = M.supplyAngle(f.s) + 0.03 * abs(extra)
        let at_ = M.takeUpAngle(f.s) - 0.03 * abs(extra)
        ctx.drawLayer { g in
            g.clip(to: win)
            pack(&g, supplyCenter, rs)
            pack(&g, takeUpCenter, rt)
            hub(&g, supplyCenter, angle: as_, omega: M.spin(f.v, rs))
            hub(&g, takeUpCenter, angle: at_, omega: M.spin(f.v, rt))
            // Glare across the glass.
            var glare = Path()
            glare.move(to: CGPoint(x: 120, y: 86)); glare.addLine(to: CGPoint(x: 170, y: 86))
            glare.addLine(to: CGPoint(x: 120, y: 191)); glare.addLine(to: CGPoint(x: 70, y: 191)); glare.closeSubpath()
            g.fill(glare, with: .color(.white.opacity(0.06)))
            var thin = Path()
            thin.move(to: CGPoint(x: 182, y: 86)); thin.addLine(to: CGPoint(x: 194, y: 86))
            thin.addLine(to: CGPoint(x: 144, y: 191)); thin.addLine(to: CGPoint(x: 132, y: 191)); thin.closeSubpath()
            g.fill(thin, with: .color(.white.opacity(0.05)))
        }
        ctx.stroke(win, with: .color(rgb(0xB5AA90)), lineWidth: 1.5)

        // The head opening at the bottom, with its felt pad.
        var mouth = Path()
        mouth.move(to: CGPoint(x: 112, y: h)); mouth.addLine(to: CGPoint(x: 124, y: 198))
        mouth.addLine(to: CGPoint(x: 238, y: 198)); mouth.addLine(to: CGPoint(x: 250, y: h)); mouth.closeSubpath()
        ctx.fill(mouth, with: .color(rgb(0x2A241C)))
        ctx.fill(Path(CGRect(x: 172, y: 213, width: 18, height: 7)), with: .color(rgb(0xC9B48A)))
        for x in [140.0, 222.0] {
            ctx.fill(Path(ellipseIn: CGRect(x: x - 4, y: 216, width: 8, height: 8)), with: .color(rgb(0x0F0C09)))
        }

        // The tape: supply pack → left guide → the span → right guide → take-up pack.
        let leftOut = CGPoint(x: leftGuide.x - guideR, y: leftGuide.y)
        let rightOut = CGPoint(x: rightGuide.x + guideR, y: rightGuide.y)
        var tape = slackRun(tangent(supplyCenter, rs, to: leftOut, left: true), leftOut, sag: f.sag, side: -1)
        tape.addArc(center: leftGuide, radius: guideR, startAngle: .degrees(180), endAngle: .degrees(90), clockwise: true)
        var x = spanX0
        while x < spanX1 {
            x = min(x + 2, spanX1)
            let u = (x - spanX0) / (spanX1 - spanX0)
            tape.addLine(to: CGPoint(x: x, y: spanY + M.span(u, f, t: t, still: still)))
        }
        tape.addArc(center: rightGuide, radius: guideR, startAngle: .degrees(90), endAngle: .degrees(0), clockwise: true)
        tape.addPath(slackRun(rightOut, tangent(takeUpCenter, rt, to: rightOut, left: false), sag: f.sag, side: 1))
        ctx.stroke(tape, with: .color(rgb(0x4A3320)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
        var sheen = ctx
        sheen.translateBy(x: 0, y: -0.7)
        sheen.stroke(tape, with: .color(rgb(0xA88560, 0.55)), style: StrokeStyle(lineWidth: 0.7, lineCap: .round, lineJoin: .round))
        // Steel guides.
        for g in [leftGuide, rightGuide] {
            let r = CGRect(x: g.x - guideR, y: g.y - guideR, width: 2 * guideR, height: 2 * guideR)
            ctx.fill(Path(ellipseIn: r), with: .linearGradient(Gradient(colors: [rgb(0xE4E7EB), rgb(0x868D96)]),
                                                                startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.maxX, y: r.maxY)))
            ctx.fill(Path(ellipseIn: r.insetBy(dx: 3, dy: 3)), with: .color(rgb(0x5A6068)))
        }
    }

    static func pack(_ g: inout GraphicsContext, _ c: CGPoint, _ r: Double) {
        let rect = CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r)
        g.fill(Path(ellipseIn: rect), with: .radialGradient(Gradient(colors: [rgb(0x3D2A18), rgb(0x23160C)]),
                                                            center: c, startRadius: 16, endRadius: r))
        for k in [4.0, 9.0, 15.0, 22.0] where r - k > 17 {
            g.stroke(Path(ellipseIn: rect.insetBy(dx: k, dy: k)), with: .color(.black.opacity(0.22)), lineWidth: 0.6)
        }
        var sheen = Path()
        sheen.addArc(center: c, radius: r - 2.5, startAngle: .degrees(200), endAngle: .degrees(255), clockwise: false)
        g.stroke(sheen, with: .color(rgb(0xA88560, 0.3)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
    }

    /// A hub: a white ring with six teeth and three dark notches. Spinning fast, the teeth smear into rings.
    static func hub(_ g: inout GraphicsContext, _ c: CGPoint, angle: Double, omega: Double) {
        let blur = M.smoothstep(10, 40, abs(omega))
        g.fill(Path(ellipseIn: CGRect(x: c.x - 16, y: c.y - 16, width: 32, height: 32)), with: .color(.white.opacity(0.84)))
        g.fill(Path(ellipseIn: CGRect(x: c.x - 9, y: c.y - 9, width: 18, height: 18)), with: .color(rgb(0x100D0A)))
        var h = g
        h.translateBy(x: c.x, y: c.y)
        h.rotate(by: .radians(angle))
        for k in 0..<6 {
            var tooth = h
            tooth.rotate(by: .degrees(Double(k) * 60))
            tooth.fill(Path(roundedRect: CGRect(x: 5.8, y: -1.3, width: 3.6, height: 2.6), cornerRadius: 0.8),
                       with: .color(.white.opacity(0.84 * (1 - 0.8 * blur))))
        }
        for k in 0..<3 {
            var notch = h
            notch.rotate(by: .degrees(Double(k) * 120 + 30))
            notch.fill(Path(roundedRect: CGRect(x: 10.5, y: -1.6, width: 4.5, height: 3.2), cornerRadius: 1),
                       with: .color(rgb(0x2A241C, 1 - 0.8 * blur)))
        }
        if blur > 0.01 {
            g.stroke(Path(ellipseIn: CGRect(x: c.x - 7.6, y: c.y - 7.6, width: 15.2, height: 15.2)),
                     with: .color(.white.opacity(0.45 * blur)), lineWidth: 2.6)
            g.stroke(Path(ellipseIn: CGRect(x: c.x - 12.75, y: c.y - 12.75, width: 25.5, height: 25.5)),
                     with: .color(rgb(0x2A241C, 0.35 * blur)), lineWidth: 3.2)
        }
    }

    // MARK: Keys

    enum Glyph: Int { case rew, play, ff, pause, stop, rec }

    static func key(_ ctx: inout GraphicsContext, _ glyph: Glyph, rect r: CGRect, pressed: Bool) {
        let sink = pressed ? 2.0 : 0
        // The well the key sits in.
        ctx.fill(Path(roundedRect: r.offsetBy(dx: 0, dy: 4), cornerRadius: 10, style: .continuous), with: .color(rgb(0x0B0A08)))
        let k = r.offsetBy(dx: 0, dy: sink)
        let body = Path(roundedRect: k, cornerRadius: 10, style: .continuous)
        ctx.fill(body, with: .linearGradient(Gradient(colors: pressed ? [rgb(0x232325), rgb(0x1C1C1E)] : [rgb(0x333336), rgb(0x232325)]),
                                             startPoint: CGPoint(x: 0, y: k.minY), endPoint: CGPoint(x: 0, y: k.maxY)))
        var top = Path()
        top.move(to: CGPoint(x: k.minX + 10, y: k.minY + 0.75)); top.addLine(to: CGPoint(x: k.maxX - 10, y: k.minY + 0.75))
        ctx.stroke(top, with: .color(.white.opacity(0.1)), lineWidth: 1)
        if pressed {
            ctx.stroke(body, with: .color((glyph == .rec ? rgb(0xE5484D) : amber).opacity(0.9)), lineWidth: 1.2)
        }
        let color: Color = glyph == .rec ? rgb(0xE5484D) : (pressed ? amber : rgb(0xF1E8D6))
        let c = CGPoint(x: k.midX, y: k.midY)
        var p = Path()
        func tri(_ x: Double, _ dir: Double) {
            p.move(to: CGPoint(x: x - 5 * dir, y: c.y - 6))
            p.addLine(to: CGPoint(x: x + 5 * dir, y: c.y))
            p.addLine(to: CGPoint(x: x - 5 * dir, y: c.y + 6))
            p.closeSubpath()
        }
        switch glyph {
        case .rew: tri(c.x - 4, -1); tri(c.x + 5, -1)
        case .play: tri(c.x + 1, 1)
        case .ff: tri(c.x - 5, 1); tri(c.x + 4, 1)
        case .pause:
            p.addRoundedRect(in: CGRect(x: c.x - 5.5, y: c.y - 6.5, width: 4, height: 13), cornerSize: CGSize(width: 1, height: 1))
            p.addRoundedRect(in: CGRect(x: c.x + 1.5, y: c.y - 6.5, width: 4, height: 13), cornerSize: CGSize(width: 1, height: 1))
        case .stop: p.addRoundedRect(in: CGRect(x: c.x - 5.5, y: c.y - 5.5, width: 11, height: 11), cornerSize: CGSize(width: 1.5, height: 1.5))
        case .rec: p.addEllipse(in: CGRect(x: c.x - 6, y: c.y - 6, width: 12, height: 12))
        }
        ctx.fill(p, with: .color(color))
    }

    // MARK: Meters

    static let marks: [(Double, String)] = [(0, "−20"), (0.35, "−10"), (0.6, "−5"), (0.75, "0"), (1, "+3")]

    static func meter(_ ctx: inout GraphicsContext, rect r: CGRect, level: Double, glow: Double, channel: String) {
        ctx.fill(Path(roundedRect: r, cornerRadius: 14, style: .continuous), with: .color(rgb(0x221E18)))
        let face = r.insetBy(dx: 8, dy: 8)
        let facePath = Path(roundedRect: face, cornerRadius: 8, style: .continuous)
        ctx.fill(facePath, with: .linearGradient(Gradient(colors: [rgb(0xF3E9CF), rgb(0xDCCBA4)]),
                                                 startPoint: CGPoint(x: 0, y: face.minY), endPoint: CGPoint(x: 0, y: face.maxY)))
        ctx.fill(facePath, with: .radialGradient(Gradient(colors: [rgb(0xFFC65C, 0.35 * glow), rgb(0xFFC65C, 0)]),
                                                 center: CGPoint(x: face.midX, y: face.maxY), startRadius: 0, endRadius: face.width * 0.8))
        let pivot = CGPoint(x: face.midX, y: face.maxY - 10)
        let radius = face.width * 0.46
        func at(_ u: Double, _ rr: Double) -> CGPoint {
            let a = (-140 + 100 * u) * .pi / 180
            return CGPoint(x: pivot.x + rr * cos(a), y: pivot.y + rr * sin(a))
        }
        // Scale: a thin arc, a red zone from 0 VU, ticks and numbers.
        var arc = Path()
        arc.addArc(center: pivot, radius: radius, startAngle: .degrees(-140), endAngle: .degrees(-65), clockwise: false)
        ctx.stroke(arc, with: .color(rgb(0x3A3225)), lineWidth: 1)
        var red = Path()
        red.addArc(center: pivot, radius: radius + 1.5, startAngle: .degrees(-65), endAngle: .degrees(-40), clockwise: false)
        ctx.stroke(red, with: .color(rgb(0xC8402F)), lineWidth: 4)
        for i in 0...20 {
            let u = Double(i) / 20
            let major = i % 4 == 0
            var tick = Path()
            tick.move(to: at(u, radius))
            tick.addLine(to: at(u, radius + (major ? 8 : 4.5)))
            ctx.stroke(tick, with: .color(u > 0.75 ? rgb(0xC8402F) : rgb(0x3A3225)), lineWidth: major ? 1.2 : 0.8)
        }
        for (u, s) in marks {
            ctx.draw(Text(s).font(.system(size: 8, weight: .medium, design: .monospaced)).foregroundColor(u > 0.74 ? rgb(0xC8402F) : rgb(0x4A4030)),
                     at: at(u, radius + 16))
        }
        ctx.draw(Text("VU").font(.system(size: 13, weight: .bold)).foregroundColor(rgb(0x3A3225)), at: CGPoint(x: face.midX, y: pivot.y - radius * 0.42))
        ctx.draw(Text(channel).font(.system(size: 10, weight: .semibold)).foregroundColor(rgb(0x6B5F48)),
                 at: CGPoint(x: face.minX + 12, y: face.maxY - 12))
        // Needle and its cap.
        var needle = Path()
        needle.move(to: pivot)
        needle.addLine(to: at(level, radius + 6))
        ctx.stroke(needle, with: .color(rgb(0x1A1612)), style: StrokeStyle(lineWidth: 1.3, lineCap: .round))
        ctx.fill(Path(roundedRect: CGRect(x: face.minX, y: face.maxY - 8, width: face.width, height: 8), cornerRadius: 0),
                 with: .color(rgb(0xCBBB94)))
        ctx.fill(Path(ellipseIn: CGRect(x: pivot.x - 5, y: pivot.y - 5, width: 10, height: 10)), with: .color(rgb(0x1A1612)))
    }
}
