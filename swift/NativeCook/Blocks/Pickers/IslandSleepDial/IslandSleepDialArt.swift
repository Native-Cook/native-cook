import SwiftUI
import UIKit

final class IslandSleepDialArt {
    typealias M = IslandSleepDialMotion
    let w, h, cx, cy, r, s: Double
    let resources = IslandSleepDialResources.shared
    private let island: Image
    private let ticks: Path
    private let ring: Path
    private let bedtime: Path
    private let starPaths: [Path]
    private let background: Path
    private let buttonPath: Path
    private var chipPaths: [Double: Path] = [:]
    private static var cache: [String: IslandSleepDialArt] = [:]
    static func cached(_ size: CGSize) -> IslandSleepDialArt {
        let key = "\(size.width),\(size.height)"
        if let art = cache[key] { return art }
        let art = IslandSleepDialArt(size)
        cache[key] = art
        return art
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func uiColor(_ hex: Int) -> UIColor {
        UIColor(red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255,
                blue: Double(hex & 255) / 255, alpha: 1)
    }
    static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    init(_ size: CGSize) {
        w = size.width; h = size.height; cx = w / 2
        cy = (h * 0.44).rounded(); r = (w * 0.4).rounded(); s = (w * 0.2).rounded()
        let s = s
        ring = Self.circle(cx, cy, r)
        bedtime = Self.circle(cx, cy - r, 15)
        background = Path(CGRect(origin: .zero, size: size))
        buttonPath = Path(roundedRect: CGRect(x: 0, y: 0, width: size.width - 40, height: 56), cornerRadius: 28)
        let width = size.width, height = size.height
        var stars: [Path] = []
        for i in 0..<46 {
            let x = Double(i * 97 % 389) * width / 389
            let y = Double(i * 61 % 433) * (height * 0.7) / 433
            let radius = 1 + Double(i % 3) * 0.5
            stars.append(Self.circle(x, y, radius))
        }
        starPaths = stars
        var ticks = Path()
        for k in 0..<12 {
            let a = -.pi / 2 + Double(k) / 12 * .pi * 2
            ticks.addPath(Self.circle(cx + cos(a) * (r - 22), cy + sin(a) * (r - 22), k % 3 == 0 ? 2.4 : 1.4))
        }
        self.ticks = ticks
        let format = UIGraphicsImageRendererFormat(); format.scale = 3
        // Island geometry never changes; only this bitmap's y origin follows the bob.
        let bitmap = UIGraphicsImageRenderer(size: CGSize(width: s * 3, height: s * 3), format: format).image { renderer in
            let c = renderer.cgContext
            c.translateBy(x: s * 1.5, y: s)
            func fill(_ color: Int, _ path: CGPath) { c.setFillColor(Self.uiColor(color).cgColor); c.addPath(path); c.fillPath() }
            let rock = CGMutablePath()
            rock.move(to: .init(x: -s * 1.05, y: 0))
            rock.addQuadCurve(to: .init(x: -s * 0.1, y: s * 1.35), control: .init(x: -s * 0.6, y: s * 0.9))
            rock.addQuadCurve(to: .init(x: s * 1.05, y: 0), control: .init(x: s * 0.25, y: s * 0.8)); rock.closeSubpath()
            fill(0x5B4E8C, rock)
            let rock2 = CGMutablePath()
            rock2.move(to: .init(x: -s * 0.2, y: 0))
            rock2.addQuadCurve(to: .init(x: -s * 0.1, y: s * 1.35), control: .init(x: s * 0.1, y: s * 0.7))
            rock2.addQuadCurve(to: .init(x: s * 1.05, y: 0), control: .init(x: s * 0.5, y: s * 0.6)); rock2.closeSubpath()
            fill(0x463C73, rock2)
            c.setFillColor(Self.uiColor(0x4F9E63).cgColor)
            c.fillEllipse(in: .init(x: -s * 1.1, y: -s * 0.32, width: s * 2.2, height: s * 0.6))
            c.setFillColor(Self.uiColor(0x62B873).cgColor)
            c.fillEllipse(in: .init(x: -s, y: -s * 0.36, width: s * 2, height: s * 0.5))
            for k in 0..<3 {
                let px = s * (0.45 + Double(k) * 0.22), py = -s * 0.1 - Double(k) * s * 0.03
                let sz = s * (0.34 - Double(k) * 0.06)
                let tree = CGMutablePath()
                tree.move(to: .init(x: px, y: py - sz * 1.8))
                tree.addLine(to: .init(x: px + sz * 0.5, y: py)); tree.addLine(to: .init(x: px - sz * 0.5, y: py)); tree.closeSubpath()
                fill(k % 2 == 1 ? 0x2E6B55 : 0x367A5F, tree)
            }
            let hx = -s * 0.35, hy = -s * 0.12
            c.setFillColor(Self.uiColor(0x8A6A8F).cgColor)
            c.fill(CGRect(x: hx - s * 0.32, y: hy - s * 0.42, width: s * 0.64, height: s * 0.42))
            let roof = CGMutablePath()
            roof.move(to: .init(x: hx - s * 0.42, y: hy - s * 0.4))
            roof.addLine(to: .init(x: hx, y: hy - s * 0.78)); roof.addLine(to: .init(x: hx + s * 0.42, y: hy - s * 0.4)); roof.closeSubpath()
            fill(0xE2667A, roof)
        }
        island = Image(uiImage: bitmap)
        resources.prepare(s)
    }
    func text(_ c: GraphicsContext, _ text: String, _ weight: String, _ size: Double, _ hex: Int,
              _ x: Double, _ y: Double, tracking: Double = 0, tabular: Bool = false) {
        let label = resources.label(text, weight, size, hex, tracking: tracking, tabular: tabular)
        c.draw(label.image, in: CGRect(x: x - label.width / 2 - 2, y: y - 2, width: label.size.width, height: label.size.height))
    }
    func glow(_ c: GraphicsContext, _ x: Double, _ y: Double, _ radius: Double, _ sigma: Double, _ color: Int, _ alpha: Double) {
        var layer = c; layer.opacity *= alpha
        let side = sigma == 14 ? 176 : (radius + sigma * 4) * 2
        let image = sigma == 14 ? resources.halos[max(0, min(60, Int(((radius - 26) * 10).rounded())))]
            : resources.glow(radius, sigma: sigma, color: color)
        layer.draw(image, in: CGRect(x: x - side / 2, y: y - side / 2, width: side, height: side))
    }
    func draw(_ c: GraphicsContext, _ f: M.Frame, reduced: Bool, top: Double, bottom: Double) {
        c.fill(background, with: .linearGradient(
            Gradient(colors: [Self.color(0x141A3C), Self.color(0x26306A), Self.color(0x3B3F86)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: h)))
        let calm = M.clamp((f.h - 4) / 7)
        for i in 0..<46 where Double(i) < 16 + calm * 30 {
            let tw = reduced ? 0.7 : 0.45 + 0.4 * sin(f.t * 2 * .pi * Double(1 + i % 3) / M.loop + Double(i))
            c.fill(starPaths[i], with: .color(Self.color(0xFFFFFF, tw)))
        }
        let stroke = StrokeStyle(lineWidth: 10, lineCap: .round)
        c.stroke(ring, with: .color(Self.color(0xFFFFFF, 0.12)), style: stroke)
        var arc = Path()
        arc.addArc(center: CGPoint(x: cx, y: cy), radius: r, startAngle: .degrees(-90), endAngle: .degrees(-90 + f.h / 12 * 360), clockwise: false)
        c.stroke(arc, with: .color(Self.color(0x9FB2FF, 0.9)), style: stroke)
        c.fill(ticks, with: .color(Self.color(0xFFFFFF, 0.4)))
        c.fill(bedtime, with: .color(Self.color(0x2A3470)))
        c.stroke(bedtime, with: .color(Self.color(0x9FB2FF)), lineWidth: 2)
        let bob = reduced ? 0 : sin(f.t * 2 * .pi * 2 / M.loop) * 5 - f.cloud * 40
        let iy = cy + bob
        c.draw(island, in: CGRect(x: cx - s * 1.5, y: iy - s, width: s * 3, height: s * 3))
        let hx = cx - s * 0.35, hy = iy - s * 0.12
        let g = 0.35 + 0.65 * calm
        glow(c, hx, hy - s * 0.2, s * 0.32, 10, 0xFFD36B, 0.35 * g)
        for dx in [-0.2, 0.06] {
            c.fill(Path(CGRect(x: hx + s * dx, y: hy - s * 0.3, width: s * 0.14, height: s * 0.14)), with: .color(Self.color(0xFFD36B, g)))
        }
        for i in 0..<Int(floor(calm * 7)) {
            let ph = f.t * 2 * .pi / M.loop + Double(i) * 1.7
            let fx = cx + cos(ph * (i % 2 == 1 ? 1 : -1) + Double(i)) * s * (0.8 + 0.1 * Double(i))
            let fy = iy - s * 0.6 + sin(ph * 2 + Double(i)) * s * 0.25 - Double(i) * 3
            glow(c, fx, fy, reduced ? 2 : 3, 4, 0xFFF2A6, 0.8)
        }
        let a = -.pi / 2 + f.h / 12 * .pi * 2
        let mx = cx + cos(a) * r, my = cy + sin(a) * r
        let haloR = 26 + (f.drag * 60).rounded() / 10
        glow(c, mx, my, haloR, 14, 0xFFF4C9, 0.25 + 0.2 * f.drag)
        c.fill(Self.circle(mx, my, 20 + 3 * f.drag), with: .color(Self.color(0xFFF1BF)))
        c.fill(Self.circle(mx + 8, my - 5, 17 + 3 * f.drag), with: .color(Self.color(0x2A3470)))
        if f.cloud > 0 {
            var bank = c; bank.opacity = 0.95 * f.cloud
            bank.drawLayer { layer in
                for k in 0..<6 {
                    let side = k % 2 == 1 ? 1.0 : -1.0
                    let x = cx + side * (w * 0.75 - f.cloud * w * (0.45 + 0.08 * Double(k >> 1)))
                    let y = cy + s * (0.2 + 0.35 * Double(k >> 1)) - s * 0.3
                    for (dx, dy, r) in [(0.0, 0.0, 0.5), (0.45, 0.1, 0.38), (-0.45, 0.12, 0.36)] {
                        layer.fill(Self.circle(x + s * dx, y + s * dy, s * r), with: .color(Self.color(0xE9ECFF)))
                    }
                }
            }
        }
        text(c, "Tonight", "SemiBold", 14, 0x9FA9E0, cx - 1.0 / 3, top + 12, tracking: 0.5)
        text(c, M.duration(f.h), "Bold", 44, 0xFFFFFF, cx - 1.0 / 6, top + 12 + 17, tabular: true)
        text(c, "Bedtime 11:00 PM  ·  Wake \(M.wake(f.h))", "Regular", 14, 0xC9D2FF, cx - 1.0 / 3, top + 12 + 17 + 53 + 1.0 / 3)
        var sleep = c; sleep.opacity = f.cloud
        text(sleep, "Sleep well", "Bold", 28, 0x2A3470, cx, cy - 20 + (1 - f.cloud) * 10)
        drawChip(c, h - bottom - 18 - 56 - 12 - 34)
    }
    func drawChip(_ c: GraphicsContext, _ y: Double) {
        let label = resources.label("Rain on a tin roof", "SemiBold", 13, 0xC9D2FF)
        let width = label.width + 16 + 8 + 28
        if chipPaths[y] == nil {
            chipPaths[y] = Path(roundedRect: CGRect(x: cx - width / 2, y: y, width: width, height: 34), cornerRadius: 17)
        }
        c.fill(chipPaths[y]!, with: .color(Self.color(0xFFFFFF, 0.1)))
        text(c, String(UnicodeScalar(62570)!), "Icon", 16, 0xC9D2FF, cx - width / 2 + 14 + 8, y + 9 - 1.0 / 3)
        text(c, "Rain on a tin roof", "SemiBold", 13, 0xC9D2FF, cx + 12, y + 9)
    }
    func drawButton(_ c: GraphicsContext, _ width: Double, asleep: Bool) {
        c.fill(buttonPath, with: .color(Self.color(0xFFF1BF)))
        let title = asleep ? "Alarm set" : "Set alarm"
        let label = resources.label(title, "SemiBold", 17, 0x141A3C)
        let total = label.width + 28
        text(c, String(UnicodeScalar(61709)!), "Icon", 20, 0x141A3C, width / 2 - total / 2 + 10 - 1.0 / 3, 18 - 1.0 / 3)
        text(c, title, "SemiBold", 17, 0x141A3C, width / 2 + 14 - 1.0 / 3, 17.0 + 2.0 / 3)
    }
}
