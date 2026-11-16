import SwiftUI

/// Paths and chrome copied from pls-icons.ts and PrintLineScrub.tsx, without SF Symbol substitutions.
enum PrintLineScrubArt {
    typealias R = PrintLineScrubResources
    private static let mark: [[Double]] = [[5.5, 14.5, 14.5, 5.5], [9, 19, 19, 9], [5.5, 9.5, 9.5, 5.5]]
    private static let fingers: [[Double]] = [[8.6, 11, 8.6, 6.4], [11.6, 10.6, 11.6, 4.6], [14.6, 10.8, 14.6, 5.6], [17.4, 11.6, 17.4, 8.4]]
    private static let cursor: Path = {
        var p = Path(); p.move(to: CGPoint(x: 5.2, y: 3.6))
        p.addLines([CGPoint(x: 19.6, y: 10.9), CGPoint(x: 12.9, y: 12.8), CGPoint(x: 10.7, y: 19.6)])
        p.closeSubpath(); return p
    }()
    private static let palm: Path = {
        var p = Path(); p.move(to: CGPoint(x: 8.6, y: 11)); p.addLine(to: CGPoint(x: 8.6, y: 16.2))
        p.addCurve(to: CGPoint(x: 13, y: 20), control1: CGPoint(x: 8.6, y: 18.4), control2: CGPoint(x: 10.3, y: 20))
        p.addCurve(to: CGPoint(x: 17.4, y: 15.6), control1: CGPoint(x: 15.8, y: 20), control2: CGPoint(x: 17.4, y: 18.1))
        p.addLine(to: CGPoint(x: 17.4, y: 11.6)); p.move(to: CGPoint(x: 8.6, y: 13.6))
        p.addLine(to: CGPoint(x: 6.4, y: 11.6)); return p
    }()

    static func circle(_ context: GraphicsContext, x: Double, y: Double, radius: Double, color: Color) {
        context.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color))
    }

    static func gizmo(_ context: GraphicsContext, _ k: PrintLineScrubMotion.Look, _ g: PrintLineScrubGeometry) {
        let s = g.scale
        var c = context
        c.translateBy(x: g.axisX, y: g.planeY(k.h)); c.scaleBy(x: 1, y: k.gizDir)
        c.opacity = 0.7 + 0.3 * k.press
        c.drawLayer { layer in
            layer.stroke(R.segment(0, 0, 14 * s, -14 * 0.2 * s), with: .color(R.color(0xF04A52)), style: StrokeStyle(lineWidth: 2 * s, lineCap: .round))
            layer.stroke(R.segment(0, 0, -14 * 0.55 * s, 14 * 0.62 * s), with: .color(R.color(0x4FD27A)), style: StrokeStyle(lineWidth: 2 * s, lineCap: .round))
            let length = min(140, 96 + (116 - 96) * k.press + 0.06 * abs(k.speed)) * s
            let end = -length + 10 * s
            layer.stroke(R.segment(0, 0, 0, end), with: .color(R.color(0x3F8CFF)), style: StrokeStyle(lineWidth: 2 * s, lineCap: .round))
            var p = Path(); p.move(to: CGPoint(x: 0, y: end - 10 * s))
            p.addLine(to: CGPoint(x: -5.5 * s, y: end)); p.addLine(to: CGPoint(x: 5.5 * s, y: end)); p.closeSubpath()
            layer.fill(p, with: .color(R.color(0x3F8CFF)))
        }
    }

    static func chrome(_ context: GraphicsContext, _ g: PrintLineScrubGeometry) {
        let s = g.scale, header = g.headerRect, dock = g.dockRect
        let hx = header.minX, hy = header.minY, hh = header.height
        circle(context, x: hx + 26 * s, y: hy + 28 * s, radius: 22 * s, color: R.color(0xFBFAF8))
        var logo = context
        logo.translateBy(x: hx + 26 * s, y: hy + 28 * s); logo.scaleBy(x: 0.8 * s, y: 0.8 * s); logo.translateBy(x: -12, y: -12)
        for m in mark { logo.stroke(R.segment(m[0], m[1], m[2], m[3]), with: .color(R.color(0x17161A)), style: StrokeStyle(lineWidth: 3.2, lineCap: .round)) }
        R.draw(context, R.file, x: hx + 62 * s, baseline: hy + hh / 2 + 0.36 * 12 * s, scale: s, color: R.color(0x4B4846))
        let chevronX = hx + 62 * s + ((R.file.positions.last ?? 0) + 14) * s + 8 * s
        var cc = context
        cc.translateBy(x: chevronX, y: hy + hh / 2 - 5.2 * s); cc.scaleBy(x: 0.52 * s, y: 0.52 * s); cc.translateBy(x: -12, y: -9)
        var chevron = Path(); chevron.move(to: CGPoint(x: 7, y: 9.5)); chevron.addLine(to: CGPoint(x: 12, y: 14.5)); chevron.addLine(to: CGPoint(x: 17, y: 9.5))
        cc.stroke(chevron, with: .color(R.color(0x4B4846)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
        let ws = CGRect(x: header.maxX - 4 * s - 108 * s, y: hy + (hh - 44 * s) / 2, width: 108 * s, height: 44 * s)
        context.fill(Path(roundedRect: ws, cornerRadius: 22 * s), with: .color(R.color(0xFFD60A)))
        R.draw(context, R.workspace, x: ws.minX + (ws.width - R.workspace.width * s) / 2,
               baseline: ws.midY + 0.35 * 15 * s, scale: s, color: R.color(0x111111))
        let x = dock.minX + 6 * s, y = dock.minY + 6 * s
        for i in 0..<3 {
            let cx = x + Double(i) * 52 * s + 22 * s, cy = y + 22 * s
            let color = i == 0 ? R.color(0xFFD60A) : i == 1 ? R.color(0xCFE0F7) : Color.white.opacity(0.7)
            circle(context, x: cx, y: cy, radius: 22 * s, color: color)
            var icon = context
            icon.translateBy(x: cx, y: cy); icon.scaleBy(x: 0.8 * s, y: 0.8 * s); icon.translateBy(x: -12, y: -12)
            if i == 0 {
                icon.fill(cursor, with: .color(R.color(0x17161A)))
                icon.stroke(cursor, with: .color(R.color(0x17161A)), style: StrokeStyle(lineWidth: 1.4, lineJoin: .round))
            } else if i == 1 {
                for f in fingers { icon.stroke(R.segment(f[0], f[1], f[2], f[3]), with: .color(R.color(0x17161A)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round)) }
                icon.stroke(palm, with: .color(R.color(0x17161A)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round, lineJoin: .round))
            } else { circle(context, x: cx, y: cy, radius: 2.2 * s, color: R.color(0x17161A)) }
        }
    }
}
