import CoreText
import SwiftUI
import UIKit

enum CareerWebOnboardingArt {
    typealias Theme = CareerWebOnboardingTheme
    typealias M = CareerWebOnboardingMotion
    struct Live {
        var px = Theme.nodes.map(\.x), py = Theme.nodes.map(\.y)
        var grow = [0.0, 0, 0], vib = Array(repeating: 0.0, count: 8)
        var hold = -1, press = 0.0, shoot = -1.0
        var pick = [-1.0, -1, -1]
    }
    struct Label {
        let text: String, size: Double, width: Double
        let font: CTFont
        let glyphs: [CGGlyph]
        let positions: [CGPoint]
        init(_ text: String, _ size: Double, _ medium: Bool = false) {
            _ = CareerWebOnboardingArt.registered
            self.text = text; self.size = size
            let uiFont = UIFont(name: medium ? "Poppins-Medium" : "Poppins-SemiBold", size: size) ?? UIFont.systemFont(ofSize: size)
            font = uiFont as CTFont
            let characters = Array(text.utf16)
            var ids = Array(repeating: CGGlyph(), count: characters.count)
            CTFontGetGlyphsForCharacters(font, characters, &ids, characters.count)
            var advances = Array(repeating: CGSize.zero, count: ids.count)
            CTFontGetAdvancesForGlyphs(font, .horizontal, ids, &advances, ids.count)
            var x = 0.0
            var points: [CGPoint] = []
            for advance in advances {
                points.append(CGPoint(x: x, y: 0))
                x += advance.width
            }
            glyphs = ids; positions = points; width = x
        }
    }
    static let registered: Void = {
        for weight in ["SemiBold", "Medium"] {
            if let url = Bundle.main.url(forResource: "CareerWebOnboarding-Poppins-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    static let titleSize = 100 * Theme.titleWidth / Label(Theme.lines[0], 100).width
    static let lines = Theme.lines.map { Label($0, titleSize) }
    static let dream = Label("Dream", titleSize)
    static let cta = Label("Get Started !", Theme.ctaSize)
    static let roles = Theme.nodes.map { $0.label.map { Label($0, Theme.roleSize, true) } }
    static let people: [UIImage] = ["a", "b", "c"].map { letter in
        guard let url = Bundle.main.url(forResource: "CareerWebOnboarding-person-\(letter)", withExtension: "webp"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func label(_ context: GraphicsContext, _ label: Label, _ x: Double, _ cy: Double, _ anchor: Double, _ hex: Int) {
        // CoreText draws glyph baselines, matching Skia's cy + size * .36 exactly.
        context.withCGContext { cg in
            cg.translateBy(x: x - label.width * anchor, y: cy + label.size * 0.36)
            cg.scaleBy(x: 1, y: -1)
            cg.setFillColor(UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                                   green: CGFloat((hex >> 8) & 255) / 255,
                                   blue: CGFloat(hex & 255) / 255, alpha: 1).cgColor)
            cg.textMatrix = .identity
            CTFontDrawGlyphs(label.font, label.glyphs, label.positions, label.glyphs.count, cg)
        }
    }
    static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    static func stroke(_ context: GraphicsContext, _ path: Path, _ width: Double = 2.4, _ hex: Int = Theme.white, _ alpha: Double = 1) {
        context.stroke(path, with: .color(color(hex, alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    static func drawTitle(_ context: GraphicsContext, _ f: M.Frame) {
        for i in 0..<3 { label(context, lines[i], Theme.titleX, Theme.titleY[i], 0, Theme.white) }
        let dx = Theme.titleX + lines[2].width + 8
        label(context, dream, dx, Theme.titleY[2], 0, Theme.white)
        if f.mark <= 0.002 { return }
        let m = Theme.mark, k = f.mark
        let end = M.mix(m.x0, m.x1, k), top = M.mix(m.top0, m.top1, k), bottom = M.mix(m.bot0, m.bot1, k)
        var band = Path()
        band.move(to: CGPoint(x: m.x0, y: m.top0)); band.addLine(to: CGPoint(x: end, y: top))
        band.addLine(to: CGPoint(x: end + 3, y: (top + bottom) / 2)); band.addLine(to: CGPoint(x: end, y: bottom))
        band.addLine(to: CGPoint(x: m.x0, y: m.bot0)); band.closeSubpath()
        context.fill(band, with: .color(color(Theme.highlight)))
        var clipped = context; clipped.clip(to: band)
        label(clipped, dream, dx, Theme.titleY[2], 0, Theme.ink)
    }
    static func drawArrow(_ context: GraphicsContext, _ f: M.Frame) {
        let a = Theme.arrow
        var path = Path(); path.move(to: a.a); path.addQuadCurve(to: a.b, control: a.c)
        if f.arrow > 0.003 { stroke(context, f.arrow < 0.999 ? path.trimmedPath(from: 0, to: f.arrow) : path) }
        if f.arrow > 0.97 {
            let tx = a.b.x - a.c.x, ty = a.b.y - a.c.y, length = hypot(tx, ty)
            let ux = tx / length, uy = ty / length, head = a.head * (1 + 0.35 * f.head)
            let sine = sin(0.55), cosine = cos(0.55)
            var headPath = Path()
            headPath.move(to: CGPoint(x: a.b.x - head * (ux * cosine - uy * sine), y: a.b.y - head * (uy * cosine + ux * sine)))
            headPath.addLine(to: a.b)
            headPath.addLine(to: CGPoint(x: a.b.x - head * (ux * cosine + uy * sine), y: a.b.y - head * (uy * cosine - ux * sine)))
            stroke(context, headPath)
        }
        let k = 1 + 0.45 * f.pops
        for pop in Theme.pops {
            let x0 = Theme.x(pop.0), y0 = Theme.y(pop.1), x1 = Theme.x(pop.2), y1 = Theme.y(pop.3)
            let mx = (x0 + x1) / 2, my = (y0 + y1) / 2
            var mark = Path()
            mark.move(to: CGPoint(x: mx + (x0 - mx) * k, y: my + (y0 - my) * k))
            mark.addLine(to: CGPoint(x: mx + (x1 - mx) * k, y: my + (y1 - my) * k))
            stroke(context, mark)
        }
    }
    static func drawLinks(_ context: GraphicsContext, _ live: Live, _ t: Double) {
        for l in M.links.indices {
            let on = M.linkOn(l, live.grow)
            if on <= 0.005 { continue }
            let a = M.links[l][0], b = M.links[l][1], ax = live.px[a], ay = live.py[a]
            let bx = ax + (live.px[b] - ax) * on, by = ay + (live.py[b] - ay) * on
            let dx = bx - ax, dy = by - ay
            let length = hypot(dx, dy) == 0 ? 1 : hypot(dx, dy)
            let full = hypot(live.px[b] - ax, live.py[b] - ay)
            let width = Theme.ropeWidth * max(0.5, min(1.2, sqrt(M.homeLen[l] / full)))
            let swing = live.vib[l]
            var path = Path(); path.move(to: CGPoint(x: ax, y: ay))
            path.addQuadCurve(to: CGPoint(x: bx, y: by), control: CGPoint(x: (ax + bx) / 2 - dy / length * swing, y: (ay + by) / 2 + dx / length * swing))
            stroke(context, path, width)
            if on >= 0.999 {
                let u = (t / 3 + Double(l) * 0.29).truncatingRemainder(dividingBy: 1)
                let qx = (ax + bx) / 2 - dy / length * swing
                let qy = (ay + by) / 2 + dx / length * swing
                let sx = (1 - u) * (1 - u) * ax + 2 * (1 - u) * u * qx + u * u * bx
                let sy = (1 - u) * (1 - u) * ay + 2 * (1 - u) * u * qy + u * u * by
                context.fill(circle(sx, sy, 9), with: .radialGradient(Gradient(colors: [color(Theme.highlight, 0.9 * sin(.pi * u)), color(Theme.highlight, 0)]), center: CGPoint(x: sx, y: sy), startRadius: 0, endRadius: 9))
            }
            if on < 0.999 { context.fill(circle(bx, by, width * 0.85), with: .color(.white)) }
        }
    }
    static func drawNodes(_ context: GraphicsContext, _ live: Live) {
        for i in Theme.nodes.indices {
            let node = Theme.nodes[i]
            var c = context; c.translateBy(x: live.px[i], y: live.py[i])
            let scale = i == live.hold ? 1.06 : 1
            c.scaleBy(x: scale, y: scale)
            if i < 3 {
                let ring = live.pick[i]
                if ring >= 0 { stroke(c, circle(0, 0, node.r + 4 + 16 * ring), 3 * (1 - ring) + 0.5, Theme.highlight, 1 - ring) }
                c.fill(circle(0, 0, node.r), with: .color(color(node.bg)))
                c.clip(to: circle(0, 0, node.r))
                c.draw(Image(uiImage: people[i]), in: CGRect(x: -node.r, y: -node.r, width: node.r * 2, height: node.r * 2))
            } else {
                c.fill(circle(0, 0, node.r), with: .color(.white))
                label(c, roles[i][0], 0, -9.5, 0.5, Theme.ink)
                label(c, roles[i][1], 0, 9.5, 0.5, Theme.ink)
            }
        }
    }
    static func drawButton(_ context: GraphicsContext, _ f: M.Frame, _ live: Live) {
        let b = Theme.button, d = Theme.dot
        var c = context
        c.translateBy(x: b.midX, y: b.midY); c.scaleBy(x: 1 - 0.04 * live.press, y: 1 - 0.04 * live.press)
        c.translateBy(x: -b.midX, y: -b.midY)
        c.fill(Path(roundedRect: b, cornerRadius: b.height / 2), with: .color(.white))
        label(c, cta, b.minX + (b.width - d.r * 2 - 10) / 2 + 4, b.midY, 0.5, Theme.ink)
        c.fill(circle(d.x + 2 * f.nudge, d.y, d.r * (1 + 0.04 * f.nudge)), with: .color(color(Theme.ink)))
        c.clip(to: circle(d.x + 2 * f.nudge, d.y, d.r - 2))
        var ox = 5 * f.nudge
        if live.shoot >= 0 { ox = live.shoot < 0.5 ? 40 * pow(live.shoot / 0.5, 2) : -40 * pow(1 - (live.shoot - 0.5) / 0.5, 2) }
        var arrow = Path()
        arrow.move(to: CGPoint(x: d.x - 8 + ox, y: d.y)); arrow.addLine(to: CGPoint(x: d.x + 8 + ox, y: d.y))
        arrow.move(to: CGPoint(x: d.x + 2.5 + ox, y: d.y - 5.5)); arrow.addLine(to: CGPoint(x: d.x + 8 + ox, y: d.y))
        arrow.addLine(to: CGPoint(x: d.x + 2.5 + ox, y: d.y + 5.5)); stroke(c, arrow, 2.2)
    }
    static func drawScene(_ context: GraphicsContext, _ size: CGSize, _ frame: M.Frame, _ live: Live) {
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(color(Theme.bg)))
        let scale = size.width / Theme.refW, dy = size.height - Theme.refH * scale
        var c = context; c.translateBy(x: 0, y: dy); c.scaleBy(x: scale, y: scale)
        for x in stride(from: 12.0, to: Theme.refW, by: 26) {
            var path = Path(); path.move(to: CGPoint(x: x, y: -dy / scale)); path.addLine(to: CGPoint(x: x, y: 900))
            c.stroke(path, with: .color(.white.opacity(0.035)), lineWidth: 1)
        }
        for y in stride(from: 10.0, to: 900, by: 26) {
            var path = Path(); path.move(to: CGPoint(x: 0, y: y)); path.addLine(to: CGPoint(x: Theme.refW, y: y))
            c.stroke(path, with: .color(.white.opacity(0.035)), lineWidth: 1)
        }
        drawTitle(c, frame); drawArrow(c, frame); drawLinks(c, live, frame.t); drawNodes(c, live); drawButton(c, frame, live)
    }
}
