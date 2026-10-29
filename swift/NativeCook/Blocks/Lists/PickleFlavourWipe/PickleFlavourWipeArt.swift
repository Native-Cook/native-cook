import SwiftUI
import UIKit

/// Pages, layout and drawing of Pickle Flavour Wipe, in a 393 pt wide design space.
enum PickleFlavourWipeArt {
    typealias M = PickleFlavourWipeMotion

    static let width = 393.0
    static let cream: UInt32 = 0xFFF4E0
    static let gold: UInt32 = 0xFFC93C
    static let leafLight: UInt32 = 0x6BBF4A
    static let leafDark: UInt32 = 0x2E7A2C

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: alpha)
    }

    static func uiColor(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 0xFF) / 255, green: CGFloat((hex >> 8) & 0xFF) / 255,
                blue: CGFloat(hex & 0xFF) / 255, alpha: alpha)
    }

    // MARK: Pages

    enum Food: Int { case carrot, lime, garlic }

    /// An ingredient around the jar: offset from the jar's centre (pt), drawn width, turn (degrees).
    struct Item {
        var food: Food
        var x: Double
        var y: Double
        var w: Double
        var turn: Double
        var flip = false
    }

    /// A small leaf drawn in code, placed like an item.
    struct Leaf {
        var x: Double
        var y: Double
        var len: Double
        var turn: Double
    }

    struct Page {
        var title: String
        var subtitle: String
        var line: String
        var price: String
        var weight: String
        var cta: String
        var top: UInt32
        var mid: UInt32
        var low: UInt32
        var cut: UInt32
        var jar: Int? // index into the jar images; nil for the line-up page
        var pickle: String // the label's last line
        var items: [Item]
        var leaves: [Leaf]
    }

    static let pages: [Page] = [
        Page(title: "Mango", subtitle: "Mixed Pickle", line: "Green mango, carrot & lime in mustard oil",
             price: "$6.90", weight: "350 g jar", cta: "Add to basket",
             top: 0x8A3BC4, mid: 0x6E22A8, low: 0x4E1588, cut: 0xD2A6F5, jar: 0, pickle: "MIXED PICKLE",
             items: [Item(food: .carrot, x: -118, y: 40, w: 172, turn: -18),
                     Item(food: .lime, x: 124, y: 72, w: 146, turn: 8)],
             leaves: [Leaf(x: -150, y: -46, len: 34, turn: -38), Leaf(x: 146, y: -30, len: 30, turn: 42),
                      Leaf(x: -46, y: 150, len: 28, turn: 196)]),
        Page(title: "Lime", subtitle: "Chilli Pickle", line: "Sun-cured lime & green chilli, nice and sharp",
             price: "$6.50", weight: "350 g jar", cta: "Add to basket",
             top: 0xEA3B4C, mid: 0xD21F35, low: 0xA3112B, cut: 0xFFB0B8, jar: 1, pickle: "CHILLI PICKLE",
             items: [Item(food: .lime, x: -122, y: 66, w: 154, turn: -10),
                     Item(food: .carrot, x: 120, y: 38, w: 164, turn: 18, flip: true)],
             leaves: [Leaf(x: -146, y: -40, len: 32, turn: -30), Leaf(x: 150, y: -44, len: 34, turn: 50),
                      Leaf(x: 58, y: 152, len: 26, turn: 160)]),
        Page(title: "Garlic", subtitle: "Whole Clove Pickle", line: "Slow-pickled cloves with red chilli & fennel",
             price: "$7.20", weight: "350 g jar", cta: "Add to basket",
             top: 0x5DAAE8, mid: 0x3D8FD6, low: 0x2869B3, cut: 0xBFE2FF, jar: 2, pickle: "GARLIC PICKLE",
             items: [Item(food: .garlic, x: -120, y: 68, w: 156, turn: -8),
                     Item(food: .garlic, x: 128, y: 88, w: 108, turn: 20, flip: true)],
             leaves: [Leaf(x: -148, y: -36, len: 30, turn: -42), Leaf(x: 144, y: -26, len: 32, turn: 36),
                      Leaf(x: -60, y: 152, len: 26, turn: 200)]),
        Page(title: "Trio", subtitle: "Tasting Box", line: "Order your favourites now!",
             price: "$18.50", weight: "3 × 350 g", cta: "Add the box",
             top: 0x35A36B, mid: 0x1F8050, low: 0x125F3A, cut: 0xA5EBC4, jar: nil, pickle: "",
             items: [Item(food: .carrot, x: -112, y: 156, w: 150, turn: -22),
                     Item(food: .lime, x: 18, y: 178, w: 100, turn: 12),
                     Item(food: .garlic, x: 132, y: 156, w: 100, turn: -10)],
             leaves: [Leaf(x: -156, y: -120, len: 30, turn: -30), Leaf(x: 158, y: -108, len: 28, turn: 60),
                      Leaf(x: -40, y: 96, len: 24, turn: 170)]),
    ]

    // MARK: Layout

    struct Layout {
        var height: Double
        var top: Double
        var bottom: Double

        var barY: Double { top + 26 }
        var titleY: Double { top + 150 }
        var jarY: Double { top + 366 }
        var ctaTop: Double { height - bottom - 16 - 56 }
        var lineY: Double { ctaTop - 64 }
        var dotsY: Double { ctaTop - 30 }
    }

    static let jarH = 272.0
    static let trioH = 140.0
    static let trioGap = 118.0

    // MARK: Artwork

    private static func load(_ name: String) -> UIImage {
        guard let path = Bundle.main.path(forResource: "PickleFlavourWipe-\(name)", ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    static let foods: [UIImage] = [load("carrot"), load("lime"), load("garlic")]

    /// Where each jar's blank label band sits, as a share of the jar's height (centre).
    private static let labelCentre = [0.597, 0.558, 0.625]

    /// Each jar with its label printed once (3×), so a frame draws one bitmap.
    static let jars: [UIImage] = ["jar-mango", "jar-lime", "jar-garlic"].enumerated().map { index, name in
        let jar = load(name)
        let page = pages[index]
        let h = jarH
        let w = h * Double(jar.size.width / max(jar.size.height, 1))
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format)
        let image = renderer.image { ctx in
            jar.draw(in: CGRect(x: 0, y: 0, width: w, height: h))
            let cx = w / 2, cy = h * labelCentre[index]
            func line(_ text: String, _ font: UIFont, _ y: Double, _ hex: UInt32, _ alpha: Double = 1, kern: Double = 0) {
                let attrs: [NSAttributedString.Key: Any] = [
                    .font: font, .foregroundColor: uiColor(hex, alpha), .kern: kern,
                ]
                let s = NSAttributedString(string: text, attributes: attrs)
                let size = s.size()
                s.draw(at: CGPoint(x: cx - (size.width - kern) / 2, y: y - size.height / 2))
            }
            line("jarful", UIFont(name: "AvenirNext-Heavy", size: 13) ?? .systemFont(ofSize: 13, weight: .black),
                 cy - 23, cream)
            let g = ctx.cgContext
            g.setStrokeColor(uiColor(gold, 0.9).cgColor)
            g.setLineWidth(0.8)
            g.move(to: CGPoint(x: cx - 16, y: cy - 12))
            g.addLine(to: CGPoint(x: cx + 16, y: cy - 12))
            g.strokePath()
            line(page.title, UIFont(name: "Georgia-BoldItalic", size: 23) ?? .italicSystemFont(ofSize: 23), cy + 5, cream)
            line(page.pickle, .systemFont(ofSize: 6.5, weight: .bold), cy + 25, gold, 1, kern: 1.6)
        }
        return image.preparingForDisplay() ?? image
    }

    /// The backdrop's tonal marks (leaves and chilli curves on a jittered grid), 440 × 1000 pt, 2×.
    static let pattern: UIImage = {
        let size = CGSize(width: 440, height: 1000)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = false
        let image = UIGraphicsImageRenderer(size: size, format: format).image { ctx in
            let g = ctx.cgContext
            let step = 58.0
            for row in 0..<18 {
                for col in 0..<8 {
                    let seed = Double(row * 31 + col * 7) + 0.5
                    let x = Double(col) * step + (row % 2 == 0 ? 0 : step / 2) + (M.hash(seed) - 0.5) * 22
                    let y = Double(row) * step + (M.hash(seed + 1.3) - 0.5) * 22
                    let turn = M.hash(seed + 2.1) * 2 * Double.pi
                    let light = M.hash(seed + 3.7) > 0.62
                    g.saveGState()
                    g.translateBy(x: x, y: y)
                    g.rotate(by: turn)
                    let ink = light ? UIColor(white: 1, alpha: 0.07) : UIColor(white: 0, alpha: 0.085)
                    if M.hash(seed + 5.9) > 0.5 {
                        // A leaf: two arcs.
                        let p = UIBezierPath()
                        p.move(to: CGPoint(x: -15, y: 0))
                        p.addQuadCurve(to: CGPoint(x: 15, y: 0), controlPoint: CGPoint(x: 0, y: -13))
                        p.addQuadCurve(to: CGPoint(x: -15, y: 0), controlPoint: CGPoint(x: 0, y: 13))
                        ink.setFill()
                        p.fill()
                    } else {
                        // A chilli curve: a thick arc with round ends.
                        let p = UIBezierPath(arcCenter: .zero, radius: 14, startAngle: -2.2, endAngle: -0.5, clockwise: true)
                        p.lineWidth = 8
                        p.lineCapStyle = .round
                        ink.setStroke()
                        p.stroke()
                    }
                    g.restoreGState()
                }
            }
        }
        return image.preparingForDisplay() ?? image
    }()

    // MARK: Scene

    /// The page `from`, then page `to` revealed by the slash edge (or cross-faded under reduced motion).
    static func drawScene(_ c: GraphicsContext, _ l: Layout, _ scene: M.Scene, ambient a: Double) {
        let r = scene.reveal
        if scene.fade >= 0 {
            drawPage(c, l, pages[scene.from], age: scene.fromAge, push: 0, bgPush: 0, ambient: a)
            var top = c
            top.opacity = scene.fade
            drawPage(top, l, pages[scene.to], age: scene.toAge, push: 0, bgPush: 0, ambient: a)
            return
        }
        if scene.from == scene.to || r <= 0 {
            drawPage(c, l, pages[scene.from], age: scene.fromAge, push: 0, bgPush: 0, ambient: a)
            return
        }
        let dir = scene.dir
        let cr = min(max(r, 0), 1.2)
        drawPage(c, l, pages[scene.from], age: scene.fromAge, push: -36 * dir * cr, bgPush: -14 * dir * cr, ambient: a)

        let edge = Edge(l, reveal: r, dir: dir)
        // The new page casts a soft shadow onto the old one.
        let n = CGVector(dx: edge.n.dx * -dir, dy: edge.n.dy * -dir) // towards the old side
        var shadow = Path()
        shadow.move(to: edge.top)
        shadow.addLine(to: edge.bottom)
        shadow.addLine(to: CGPoint(x: edge.bottom.x + n.dx * 30, y: edge.bottom.y + n.dy * 30))
        shadow.addLine(to: CGPoint(x: edge.top.x + n.dx * 30, y: edge.top.y + n.dy * 30))
        shadow.closeSubpath()
        let mid = edge.mid
        c.fill(shadow, with: .linearGradient(
            Gradient(colors: [Color.black.opacity(0.26), Color.black.opacity(0)]),
            startPoint: mid, endPoint: CGPoint(x: mid.x + n.dx * 30, y: mid.y + n.dy * 30)))

        var revealed = c
        revealed.clip(to: edge.region)
        let rest = 1 - min(max(r, 0), 1)
        drawPage(revealed, l, pages[scene.to], age: scene.toAge, push: 24 * dir * rest, bgPush: 14 * dir * rest, ambient: a)
        // A sheen just inside the new page.
        let inward = CGVector(dx: -n.dx, dy: -n.dy)
        revealed.fill(edge.region, with: .linearGradient(
            Gradient(colors: [Color.white.opacity(0.16), Color.white.opacity(0)]),
            startPoint: mid, endPoint: CGPoint(x: mid.x + inward.dx * 46, y: mid.y + inward.dy * 46)))

        // The cut line itself.
        var cut = Path()
        cut.move(to: edge.top)
        cut.addLine(to: edge.bottom)
        let page = pages[scene.to]
        c.stroke(cut, with: .color(color(page.cut)), lineWidth: 3.2)
        c.stroke(cut, with: .color(Color.white.opacity(0.75)), lineWidth: 1)
    }

    /// The slash: a straight "/" edge at 64° that sweeps across the screen.
    struct Edge {
        var top: CGPoint
        var bottom: CGPoint
        var region: Path
        var n: CGVector // unit normal pointing right-down

        var mid: CGPoint { CGPoint(x: (top.x + bottom.x) / 2, y: (top.y + bottom.y) / 2) }

        init(_ l: Layout, reveal r: Double, dir: Double) {
            let w = PickleFlavourWipeArt.width, h = l.height
            let s = h / 2 / tan(64 * Double.pi / 180)
            let travel = w + 2 * s + 48
            let ex = dir > 0 ? (w + s + 24) - r * travel : (-s - 24) + r * travel
            top = CGPoint(x: ex + s, y: -2)
            bottom = CGPoint(x: ex - s, y: h + 2)
            let len = hypot(2 * s, h)
            n = CGVector(dx: h / len, dy: 2 * s / len)
            var p = Path()
            if dir > 0 {
                p.move(to: top)
                p.addLine(to: CGPoint(x: max(w, top.x) + 60, y: -2))
                p.addLine(to: CGPoint(x: max(w, top.x) + 60, y: h + 2))
                p.addLine(to: bottom)
            } else {
                p.move(to: CGPoint(x: min(0, bottom.x) - 60, y: -2))
                p.addLine(to: top)
                p.addLine(to: bottom)
                p.addLine(to: CGPoint(x: min(0, bottom.x) - 60, y: h + 2))
            }
            p.closeSubpath()
            region = p
        }
    }

    // MARK: Page

    static func drawPage(_ c: GraphicsContext, _ l: Layout, _ page: Page, age: Double, push: Double, bgPush: Double,
                         ambient a: Double) {
        let full = CGRect(x: -2, y: -2, width: width + 4, height: l.height + 4)
        c.fill(Path(full), with: .linearGradient(
            Gradient(stops: [.init(color: color(page.top), location: 0), .init(color: color(page.mid), location: 0.46),
                             .init(color: color(page.low), location: 1)]),
            startPoint: CGPoint(x: 0, y: 0), endPoint: CGPoint(x: 0, y: l.height)))
        c.draw(Image(uiImage: pattern), in: CGRect(x: -24 + bgPush, y: 0, width: 440, height: 1000))
        // A soft light behind the hero.
        let glowC = CGPoint(x: 196 + push, y: l.jarY - 10)
        c.fill(Path(ellipseIn: CGRect(x: glowC.x - 230, y: glowC.y - 230, width: 460, height: 460)),
               with: .radialGradient(Gradient(colors: [Color.white.opacity(0.17), Color.white.opacity(0)]),
                                     center: glowC, startRadius: 0, endRadius: 230))

        var g = c
        g.translateBy(x: push, y: 0)
        let jx = 196.0, jy = l.jarY
        if let jar = page.jar {
            // Items behind the jar, the jar's shadow, the jar, then the leaves in front.
            for (i, item) in page.items.enumerated() {
                drawItem(g, item, i: i, jx: jx, jy: jy, age: age, ambient: a)
            }
            let v = M.jarIn(age)
            let dy = 300 * (1 - v) + 3 * M.wave(a, 5, 0.4)
            let turn = -6 + 20 * (1 - v) + 0.8 * M.wave(a, 4, 1.1)
            let scale = 0.9 + 0.1 * v
            let image = jars[jar]
            let w = jarH * Double(image.size.width / image.size.height)
            drawShadow(g, x: jx + 10, y: jy + jarH * 0.43 + dy, w: 230, h: 66, alpha: 0.3 * M.clamp(v))
            var j = g
            j.translateBy(x: jx, y: jy + dy)
            j.rotate(by: .degrees(turn))
            j.scaleBy(x: scale, y: scale)
            j.draw(Image(uiImage: image), in: CGRect(x: -w / 2, y: -jarH / 2, width: w, height: jarH))
        } else {
            for (i, item) in page.items.enumerated() {
                drawItem(g, item, i: i, jx: jx, jy: jy, age: age, ambient: a)
            }
            // The line-up: left, right, then the centre jar in front.
            for j in [0, 2, 1] {
                let v = M.trioJarIn(age, j)
                let dx = Double(j - 1) * trioGap
                let dy = 260 * (1 - v) + 2.5 * M.wave(a, 5, Double(j) * 0.9)
                let turn = Double(j - 1) * 4 + 16 * (1 - v)
                let image = jars[[2, 1, 0][j]]
                let w = trioH * Double(image.size.width / image.size.height)
                drawShadow(g, x: jx + dx + 6, y: jy - 14 + trioH * 0.44 + dy, w: 140, h: 40, alpha: 0.28 * M.clamp(v))
                var k = g
                k.translateBy(x: jx + dx, y: jy - 14 + dy)
                k.rotate(by: .degrees(turn))
                k.draw(Image(uiImage: image), in: CGRect(x: -w / 2, y: -trioH / 2, width: w, height: trioH))
            }
        }
        for (i, leaf) in page.leaves.enumerated() {
            drawLeaf(g, leaf, i: i, jx: jx, jy: jy, age: age, ambient: a)
        }

        drawTitle(g, l, page, age: age)
        drawCopy(g, l, page, age: age)
    }

    private static func drawShadow(_ c: GraphicsContext, x: Double, y: Double, w: Double, h: Double, alpha: Double) {
        guard alpha > 0.005 else { return }
        var s = c
        s.translateBy(x: x, y: y)
        s.scaleBy(x: 1, y: h / w)
        s.fill(Path(ellipseIn: CGRect(x: -w / 2, y: -w / 2, width: w, height: w)),
               with: .radialGradient(Gradient(colors: [Color.black.opacity(alpha), Color.black.opacity(0)]),
                                     center: .zero, startRadius: 0, endRadius: w / 2))
    }

    private static func drawItem(_ c: GraphicsContext, _ item: Item, i: Int, jx: Double, jy: Double, age: Double,
                                 ambient a: Double) {
        let v = M.ingredientIn(age, i)
        guard v > 0.001 else { return }
        let image = foods[item.food.rawValue]
        let h = item.w * Double(image.size.height / image.size.width)
        let x = jx + item.x * v
        let y = jy + item.y * v + 5 * M.wave(a, 5, 1.3 + Double(i) * 2.1)
        let turn = item.turn - 30 * (1 - v) + 3 * M.wave(a, 4, Double(i) * 1.7)
        let scale = 0.2 + 0.8 * v
        var g = c
        g.opacity = M.clamp(v * 3)
        g.translateBy(x: x, y: y)
        g.rotate(by: .degrees(turn))
        g.scaleBy(x: item.flip ? -scale : scale, y: scale)
        g.draw(Image(uiImage: image), in: CGRect(x: -item.w / 2, y: -h / 2, width: item.w, height: h))
    }

    private static func drawLeaf(_ c: GraphicsContext, _ leaf: Leaf, i: Int, jx: Double, jy: Double, age: Double,
                                 ambient a: Double) {
        let v = M.ingredientIn(age, i + 2)
        guard v > 0.001 else { return }
        var g = c
        g.opacity = M.clamp(v * 3)
        g.translateBy(x: jx + leaf.x * v, y: jy + leaf.y * v + 6 * M.wave(a, 5, 2.4 + Double(i) * 1.9))
        g.rotate(by: .degrees(leaf.turn + 40 * (1 - v) + 8 * M.wave(a, 3, Double(i) * 2.3)))
        let s = leaf.len * (0.3 + 0.7 * v)
        var p = Path()
        p.move(to: CGPoint(x: -s / 2, y: 0))
        p.addQuadCurve(to: CGPoint(x: s / 2, y: 0), control: CGPoint(x: -s * 0.05, y: -s * 0.46))
        p.addQuadCurve(to: CGPoint(x: -s / 2, y: 0), control: CGPoint(x: s * 0.05, y: s * 0.4))
        g.fill(p, with: .linearGradient(Gradient(colors: [color(leafLight), color(leafDark)]),
                                        startPoint: CGPoint(x: 0, y: -s * 0.25), endPoint: CGPoint(x: 0, y: s * 0.2)))
        var rib = Path()
        rib.move(to: CGPoint(x: -s * 0.45, y: 0))
        rib.addQuadCurve(to: CGPoint(x: s * 0.42, y: -s * 0.02), control: CGPoint(x: 0, y: -s * 0.08))
        g.stroke(rib, with: .color(color(0x1F5A1E, 0.55)), lineWidth: 0.9)
    }

    // MARK: Type

    static let titleFont = Font.custom("AvenirNext-Heavy", fixedSize: 92)

    private static func drawTitle(_ c: GraphicsContext, _ l: Layout, _ page: Page, age: Double) {
        let v = M.titleIn(age)
        let text = c.resolve(Text(page.title).font(titleFont).foregroundColor(.white))
        let size = text.measure(in: CGSize(width: 1000, height: 200))
        let fit = min(1, 330 / max(size.width, 1))
        let speed = M.titleSpeed(age) * 300 // pt/s
        var g = c
        g.translateBy(x: 196 - 300 * (1 - v), y: l.titleY)
        g.rotate(by: .degrees(-7))
        g.scaleBy(x: fit, y: fit)
        // A soft drop shadow, then the smear ghosts trailing behind, then the title.
        var shadow = g
        shadow.opacity = 0.16 * M.clamp(v * 2)
        shadow.draw(c.resolve(Text(page.title).font(titleFont).foregroundColor(.black)), at: CGPoint(x: 0, y: 4))
        if speed > 40 {
            for k in 1...4 {
                var ghost = g
                ghost.opacity = [0.32, 0.2, 0.12, 0.06][k - 1] * M.clamp(speed / 600)
                ghost.draw(text, at: CGPoint(x: -speed * 0.011 * Double(k) / fit, y: 0))
            }
        }
        g.draw(text, at: .zero)

        let copy = M.copyIn(age)
        guard copy > 0.001 else { return }
        var sub = g
        sub.opacity = copy
        sub.scaleBy(x: 1 / fit, y: 1 / fit)
        let subtitle = c.resolve(Text(page.subtitle).font(.system(size: 15, weight: .semibold))
            .foregroundColor(color(cream, 0.92)))
        sub.draw(subtitle, at: CGPoint(x: size.width * fit / 2 - 4, y: 60 * fit + 8 * (1 - copy)), anchor: .trailing)
    }

    private static func drawCopy(_ c: GraphicsContext, _ l: Layout, _ page: Page, age: Double) {
        let copy = M.copyIn(age)
        if copy > 0.001 {
            var g = c
            g.opacity = copy
            let line: Text
            if page.jar == nil {
                line = Text("Order your ").foregroundColor(.white)
                    + Text("favourites").foregroundColor(color(gold))
                    + Text(" now!").foregroundColor(.white)
            } else {
                line = Text(page.line).foregroundColor(color(cream, 0.88))
            }
            let size: CGFloat = page.jar == nil ? 21 : 15
            g.draw(line.font(.system(size: size, weight: page.jar == nil ? .bold : .medium)),
                   at: CGPoint(x: 196, y: l.lineY + 10 * (1 - copy)))
        }
        c.draw(Text(page.price).font(.system(size: 26, weight: .bold)).foregroundColor(.white),
               at: CGPoint(x: 24, y: l.ctaTop + 19), anchor: .leading)
        c.draw(Text(page.weight).font(.system(size: 12, weight: .medium)).foregroundColor(color(cream, 0.72)),
               at: CGPoint(x: 24, y: l.ctaTop + 43), anchor: .leading)
        let button = CGRect(x: 179, y: l.ctaTop, width: 190, height: 56)
        c.fill(Path(roundedRect: button.offsetBy(dx: 0, dy: 3), cornerRadius: 28), with: .color(Color.black.opacity(0.14)))
        c.fill(Path(roundedRect: button, cornerRadius: 28), with: .color(color(cream)))
        c.draw(Text(page.cta).font(.system(size: 16, weight: .semibold)).foregroundColor(color(page.low)),
               at: CGPoint(x: button.midX, y: button.midY))
    }

    // MARK: Chrome (not wiped)

    static func drawChrome(_ c: GraphicsContext, _ l: Layout, _ scene: M.Scene) {
        let y = l.barY
        for i in 0..<3 {
            let w = i == 1 ? 12.0 : 18.0
            c.fill(Path(roundedRect: CGRect(x: 24, y: y - 6.5 + Double(i) * 5.5, width: w, height: 2.2), cornerRadius: 1.1),
                   with: .color(.white))
        }
        let mark = c.resolve(Text("jarful").font(.custom("AvenirNext-Heavy", fixedSize: 24)).foregroundColor(.white))
        let size = mark.measure(in: CGSize(width: 300, height: 60))
        c.draw(mark, at: CGPoint(x: 196 - 5, y: y))
        c.fill(Path(ellipseIn: CGRect(x: 196 - 5 + size.width / 2 + 3, y: y + 3, width: 7, height: 7)),
               with: .color(color(gold)))

        let bag = CGPoint(x: 349, y: y)
        c.fill(Path(ellipseIn: CGRect(x: bag.x - 20, y: bag.y - 20, width: 40, height: 40)),
               with: .color(Color.white.opacity(0.16)))
        var body = Path(roundedRect: CGRect(x: bag.x - 7.5, y: bag.y - 4, width: 15, height: 12.5), cornerRadius: 2.5)
        body.move(to: CGPoint(x: bag.x - 4, y: bag.y - 4))
        body.addCurve(to: CGPoint(x: bag.x + 4, y: bag.y - 4), control1: CGPoint(x: bag.x - 4, y: bag.y - 10.5),
                      control2: CGPoint(x: bag.x + 4, y: bag.y - 10.5))
        c.stroke(body, with: .color(.white), style: StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round))

        // Page dots: the active capsule slides with the edge.
        let r = scene.fade >= 0 ? scene.fade : M.clamp(scene.reveal)
        let count = M.pages
        let gap = 16.0, wide = 22.0
        let total = Double(count - 1) * gap + wide
        var x = 196 - total / 2
        for i in 0..<count {
            let share = (i == scene.from ? 1 - r : 0) + (i == scene.to ? r : 0)
            let w = 8 + (wide - 8) * share
            c.fill(Path(roundedRect: CGRect(x: x, y: l.dotsY - 4, width: w, height: 8), cornerRadius: 4),
                   with: .color(Color.white.opacity(0.4 + 0.55 * share)))
            x += w + gap - 8
        }
    }
}
