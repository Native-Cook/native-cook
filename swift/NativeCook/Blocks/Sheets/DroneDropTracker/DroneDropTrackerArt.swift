import CoreText
import SwiftUI
import UIKit

struct DroneDropTrackerTownMark {
    let path: Path
    let color: Color
    var stroke = 0.0
}

final class DroneDropTrackerResources {
    static let shared = DroneDropTrackerResources()
    private var cachedGeometry: DroneDropTrackerGeometry?
    private var town: [DroneDropTrackerTownMark] = []
    private var routeGeometry: DroneDropTrackerGeometry?
    private var route = Path()
    init() {
        for name in ["Inter-Regular", "Inter-SemiBold", "Inter-Bold", "Ionicons"] {
            if let url = Bundle.main.url(forResource: "DroneDropTracker-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }
    func font(_ weight: String, _ size: Double) -> Font { .custom(weight == "SemiBold" ? "Inter-SemiBold" : "Inter24pt-\(weight)", fixedSize: size) }
    func routePath(_ g: DroneDropTrackerGeometry) -> Path {
        if routeGeometry != g {
            route = Path()
            for (i, p) in DroneDropTrackerMotion.route.enumerated() {
                let point = CGPoint(x: p.x * g.width, y: g.top + p.y * g.mapH)
                if i == 0 { route.move(to: point) } else { route.addLine(to: point) }
            }
            routeGeometry = g
        }
        return route
    }
    // A retained vector picture: prepare every static path once, preserving sharp edges at 1–1.5x zoom.
    func map(_ g: DroneDropTrackerGeometry) -> [DroneDropTrackerTownMark] {
        if cachedGeometry == g { return town }
        let w = g.width, h = g.mapH
        var marks: [DroneDropTrackerTownMark] = []
        func fill(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ color: Int, _ r: Double = 0, _ a: Double = 1) {
            marks.append(.init(path: DroneDropTrackerArt.rect(x, y, w, h, r), color: DroneDropTrackerArt.color(color, a)))
        }
        func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ color: Int, _ a: Double = 1) {
            marks.append(.init(path: Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h)), color: DroneDropTrackerArt.color(color, a)))
        }
        func line(_ x: Double, _ y: Double, _ xx: Double, _ yy: Double) {
            var path = Path(); path.move(to: CGPoint(x: x, y: y)); path.addLine(to: CGPoint(x: xx, y: yy))
            marks.append(.init(path: path, color: .white, stroke: 16))
        }
        func house(_ x: Double, _ y: Double, _ size: Double, _ roof: Int, _ wall: Int) {
            fill(x - size / 2, y - size * 0.15, size, size * 0.55, wall, 4)
            var path = Path()
            path.move(to: CGPoint(x: x - size * 0.6, y: y - size * 0.1))
            path.addLine(to: CGPoint(x: x, y: y - size * 0.62))
            path.addLine(to: CGPoint(x: x + size * 0.6, y: y - size * 0.1)); path.closeSubpath()
            marks.append(.init(path: path, color: DroneDropTrackerArt.color(roof)))
            fill(x - size * 0.1, y + size * 0.12, size * 0.2, size * 0.28, 0x7A5A44)
        }
            fill(-w, g.top - h, w * 3, h * 3, 0xBFE6C9)
            for i in 0..<4 { for j in 0..<5 {
                fill((0.02 + Double(i) * 0.25) * w, g.top + h * (0.02 + Double(j) * 0.22), w * 0.21, h * 0.18, 0xD3F0D9, 14)
            } }
            for i in 0..<4 { line(0, g.top + h * (0.2 + Double(i) * 0.22), w, g.top + h * (0.2 + Double(i) * 0.22)) }
            for i in 0..<4 { line((0.245 * Double(i) + 0.005) * w, g.top - 20, (0.245 * Double(i) + 0.005) * w, g.top + h + 20) }
            for i in 0..<18 {
                let x = ((Double(i) * 0.137).truncatingRemainder(dividingBy: 1) * 0.96 + 0.02) * w
                let y = g.top + h * ((Double(i) * 0.311).truncatingRemainder(dividingBy: 1) * 0.94 + 0.03)
                oval(x - 7, y - 6, 18, 18, 0x2E6B4A, 0.18)
                oval(x - 9, y - 9, 18, 18, i % 3 != 0 ? 0x5DBB6B : 0x46A65A)
            }
            let roofs = [0xFF8A65, 0x7E9CFF, 0xFFC857, 0xB98BFF]
            for i in 0..<9 {
                house((0.1 + (Double(i) * 0.29).truncatingRemainder(dividingBy: 1) * 0.8) * w, g.top + h * (0.1 + (Double(i) * 0.43).truncatingRemainder(dividingBy: 1) * 0.8), 26, roofs[i % 4], 0xFFF6EA)
            }
            let sx = 0.16 * w, sy = g.top + 0.2 * h
            fill(sx - 26, sy - 22, 52, 36, 0xFFFFFF, 8)
            fill(sx - 26, sy - 30, 52, 12, 0xFF6B5A, 6)
            let hx = 0.82 * w, hy = g.top + 0.8 * h
            house(hx, hy, 40, 0x2F9E62, 0xFFFFFF)
            oval(hx - 22, hy + 22, 44, 12, 0x2F9E62, 0.2)
        cachedGeometry = g; town = marks
        return marks
    }
}

struct DroneDropTrackerArt {
    static func uiColor(_ hex: Int, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color { Color(uiColor(hex, alpha)) }
    static func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double = 0) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }
    static func line(_ c: GraphicsContext, _ x: Double, _ y: Double, _ xx: Double, _ yy: Double, _ hex: Int, _ alpha: Double, _ width: Double) {
        var p = Path(); p.move(to: CGPoint(x: x, y: y)); p.addLine(to: CGPoint(x: xx, y: yy))
        c.stroke(p, with: .color(color(hex, alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }
    static func draw(_ context: GraphicsContext, _ g: DroneDropTrackerGeometry, _ o: DroneDropTrackerFrame, _ reduced: Bool) {
        let pos = DroneDropTrackerMotion.routeAt(o.back > 0 ? 1 - o.back : o.f)
        let px = pos.x * g.width, py = g.top + pos.y * g.mapH
        let z = reduced ? 1 : 1 + 0.5 * o.sheet
        let fx = DroneDropTrackerMotion.mix(g.width / 2, px, o.sheet)
        let fy = DroneDropTrackerMotion.mix(g.top + g.mapH / 2, py, o.sheet)
        var c = context
        c.translateBy(x: (g.width / 2 - fx) * o.sheet, y: (g.focusY - fy) * o.sheet)
        c.translateBy(x: fx, y: fy); c.scaleBy(x: z, y: z); c.translateBy(x: -fx, y: -fy)
        for mark in DroneDropTrackerResources.shared.map(g) {
            if mark.stroke > 0 { c.stroke(mark.path, with: .color(mark.color), style: StrokeStyle(lineWidth: mark.stroke, lineCap: .round)) }
            else { c.fill(mark.path, with: .color(mark.color)) }
        }
        let route = DroneDropTrackerResources.shared.routePath(g)
        c.stroke(route, with: .color(color(0x2F3A4A, 0.5)), style: StrokeStyle(lineWidth: 3, lineCap: .round, dash: [2, 9]))
        if o.back == 0 { c.stroke(route.trimmedPath(from: 0, to: o.f), with: .color(color(0x2F3A4A, 0.9)), style: StrokeStyle(lineWidth: 4, lineCap: .round)) }
        if o.carried < 1 && o.back > 0 && o.back < 1 {
            c.fill(rect(g.width * 0.82 - 9, g.top + g.mapH * 0.8 + 18, 18, 14, 3), with: .color(color(0xD9A066)))
        }
        let hover = reduced ? 0 : sin(o.t * (14 * Double.pi / 9)) * 1.5
        let dy = py - 34 + hover
        c.fill(Path(ellipseIn: CGRect(x: px - 12, y: py, width: 44, height: 14)), with: .color(color(0x1E3A2A, 0.16)))
        let length = 10 + 44 * o.drop
        let sway = reduced ? 0 : sin(o.t * (8 * Double.pi / 9)) * 4 * (1 - o.drop) * (o.f > 0 && o.f < 1 ? 1 : 0.3)
        if o.carried > 0 {
            line(c, px, dy + 8, px + sway, dy + 8 + length, 0x2F3A4A, 0.8 * o.carried, 1.4)
            c.fill(rect(px + sway - 10, dy + 8 + length, 20, 16, 3), with: .color(color(0xD9A066, o.carried)))
            c.fill(rect(px + sway - 10, dy + 14 + length, 20, 3), with: .color(color(0xFF6B5A, o.carried)))
        }
        c.fill(rect(px - 13, dy - 7, 26, 16, 7), with: .color(color(0xF4F6FA)))
        c.stroke(rect(px - 13, dy - 7, 26, 16, 7), with: .color(color(0xC9D1DC)), lineWidth: 1.2)
        c.fill(Path(ellipseIn: CGRect(x: px - 4, y: dy - 3, width: 8, height: 8)), with: .color(color(0x3D7BFF)))
        for k in 0..<4 {
            let ax = px + (k % 2 != 0 ? 22.0 : -22.0), ay = dy + (k < 2 ? -9.0 : 9.0)
            line(c, px, dy, ax, ay, 0xC9D1DC, 1, 3)
            c.fill(Path(ellipseIn: CGRect(x: ax - 13, y: ay - 4, width: 26, height: 8)), with: .color(color(0x2F3A4A, reduced ? 0.5 : 0.18)))
            let a = o.t * (114 * Double.pi / 9) + Double(k)
            line(c, ax - cos(a) * 12, ay - sin(a) * 3.5, ax + cos(a) * 12, ay + sin(a) * 3.5, 0x2F3A4A, 0.6, 2)
        }
    }
}
