import SwiftUI

/// The block's icons, drawn from the same SVG path data and view boxes as the React Native `icons.tsx`.
enum SavedPlacesMapIcons {
    struct Icon {
        /// Path data in view-box units.
        let paths: [Path]
        let viewBox: CGRect
        /// `preserveAspectRatio="none"` stretches the view box to the box; otherwise it is fitted (meet).
        let stretch: Bool
    }

    // MARK: SVG path data

    static func svg(_ d: String) -> Path {
        var tokens: [String] = []
        var number = ""
        func flush() { if !number.isEmpty { tokens.append(number); number = "" } }
        for ch in d {
            if ch.isLetter { flush(); tokens.append(String(ch)) }
            else if ch == " " || ch == "," { flush() }
            else if ch == "-" { flush(); number = "-" }
            else { number.append(ch) }
        }
        flush()
        var path = Path()
        var i = 0
        var cur = CGPoint.zero
        var start = CGPoint.zero
        var cmd = "M"
        func num() -> Double { defer { i += 1 }; return Double(tokens[i]) ?? 0 }
        while i < tokens.count {
            if let first = tokens[i].first, first.isLetter { cmd = tokens[i]; i += 1 }
            let rel = cmd == cmd.lowercased()
            let base = rel ? cur : .zero
            switch cmd.uppercased() {
            case "M":
                cur = CGPoint(x: base.x + num(), y: base.y + num()); start = cur; path.move(to: cur)
                cmd = rel ? "l" : "L"
            case "L":
                cur = CGPoint(x: base.x + num(), y: base.y + num()); path.addLine(to: cur)
            case "H":
                cur = CGPoint(x: (rel ? cur.x : 0) + num(), y: cur.y); path.addLine(to: cur)
            case "V":
                cur = CGPoint(x: cur.x, y: (rel ? cur.y : 0) + num()); path.addLine(to: cur)
            case "C":
                let c1 = CGPoint(x: base.x + num(), y: base.y + num())
                let c2 = CGPoint(x: base.x + num(), y: base.y + num())
                let e = CGPoint(x: base.x + num(), y: base.y + num())
                path.addCurve(to: e, control1: c1, control2: c2); cur = e
            case "Q":
                let c = CGPoint(x: base.x + num(), y: base.y + num())
                let e = CGPoint(x: base.x + num(), y: base.y + num())
                path.addQuadCurve(to: e, control: c); cur = e
            case "A":
                let rx = num(), ry = num(), rot = num(), large = num() != 0, sweep = num() != 0
                let e = CGPoint(x: base.x + num(), y: base.y + num())
                addArc(&path, from: cur, to: e, rx: rx, ry: ry, rotation: rot, large: large, sweep: sweep)
                cur = e
            case "Z":
                path.closeSubpath(); cur = start
            default:
                i += 1
            }
        }
        return path
    }

    /// SVG elliptical arc (endpoint form) as cubic segments.
    private static func addArc(_ path: inout Path, from p0: CGPoint, to p1: CGPoint, rx rx0: Double, ry ry0: Double,
                               rotation: Double, large: Bool, sweep: Bool) {
        var rx = abs(rx0), ry = abs(ry0)
        if rx == 0 || ry == 0 || p0 == p1 { path.addLine(to: p1); return }
        let phi = rotation * .pi / 180
        let cp = cos(phi), sp = sin(phi)
        let dx = (p0.x - p1.x) / 2, dy = (p0.y - p1.y) / 2
        let x1 = cp * dx + sp * dy, y1 = -sp * dx + cp * dy
        let lambda = x1 * x1 / (rx * rx) + y1 * y1 / (ry * ry)
        if lambda > 1 { rx *= sqrt(lambda); ry *= sqrt(lambda) }
        let num = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var coef = sqrt(max(0, num / den))
        if large == sweep { coef = -coef }
        let cxp = coef * rx * y1 / ry, cyp = -coef * ry * x1 / rx
        let cx = cp * cxp - sp * cyp + (p0.x + p1.x) / 2
        let cy = sp * cxp + cp * cyp + (p0.y + p1.y) / 2
        func angle(_ ux: Double, _ uy: Double, _ vx: Double, _ vy: Double) -> Double {
            atan2(ux * vy - uy * vx, ux * vx + uy * vy)
        }
        let t1 = angle(1, 0, (x1 - cxp) / rx, (y1 - cyp) / ry)
        var dt = angle((x1 - cxp) / rx, (y1 - cyp) / ry, (-x1 - cxp) / rx, (-y1 - cyp) / ry)
        if !sweep && dt > 0 { dt -= 2 * .pi }
        if sweep && dt < 0 { dt += 2 * .pi }
        let n = max(1, Int(ceil(abs(dt) / (.pi / 2))))
        let step = dt / Double(n)
        let k = 4.0 / 3.0 * tan(step / 4)
        var t = t1
        for _ in 0..<n {
            let c0 = cos(t), s0 = sin(t), c1 = cos(t + step), s1 = sin(t + step)
            func pt(_ ex: Double, _ ey: Double) -> CGPoint {
                CGPoint(x: cx + rx * cp * ex - ry * sp * ey, y: cy + rx * sp * ex + ry * cp * ey)
            }
            path.addCurve(to: pt(c1, s1),
                          control1: pt(c0 - k * s0, s0 + k * c0),
                          control2: pt(c1 + k * s1, s1 - k * c1))
            t += step
        }
    }

    private static func ellipse(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2))
    }
    private static func rrect(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
    }

    // MARK: icons (icons.tsx)

    static let chevronLeft = Icon(paths: [svg("M16 3 L7 12 L16 21")], viewBox: CGRect(x: 5.7, y: 1.7, width: 11.6, height: 20.6), stretch: true)
    static let chevronDown = Icon(paths: [svg("M4 8 L12 16 L20 8")], viewBox: CGRect(x: 2.9, y: 6.9, width: 18.2, height: 10.2), stretch: true)
    static let globe = Icon(paths: [ellipse(12, 12, 9.3, 9.3), ellipse(12, 12, 4.1, 9.3), svg("M2.7 12 H21.3 M4.2 7.2 H19.8 M4.2 16.8 H19.8")],
                            viewBox: CGRect(x: 1.85, y: 1.85, width: 20.3, height: 20.3), stretch: true)
    static let pin = Icon(paths: [svg("M12 22 C8 17.6 5.2 14.6 5.2 10.2 a6.8 6.8 0 0 1 13.6 0 C18.8 14.6 16 17.6 12 22 Z"), ellipse(12, 10.2, 2.6, 2.6)],
                          viewBox: CGRect(x: 4.25, y: 2.45, width: 15.5, height: 20), stretch: true)
    static let bookmarkOutline = Icon(paths: [svg("M6.2 3 H17.8 V21 L12 16.6 L6.2 21 Z")], viewBox: CGRect(x: 5.25, y: 2.05, width: 13.5, height: 19.9), stretch: true)
    static let bars = Icon(paths: [svg("M4.5 20 V10 M10 20 V4 M15.5 20 V12 M21 20 V15")], viewBox: CGRect(x: 3.3, y: 2.8, width: 18.9, height: 18.4), stretch: true)
    static let share = Icon(paths: [svg("M12 15 V3.5 M7.6 7.6 L12 3.2 L16.4 7.6"),
                                    svg("M8 10.5 H6.2 a2.2 2.2 0 0 0 -2.2 2.2 V18 a2.2 2.2 0 0 0 2.2 2.2 H17.8 a2.2 2.2 0 0 0 2.2 -2.2 V12.7 a2.2 2.2 0 0 0 -2.2 -2.2 H16")],
                            viewBox: CGRect(x: 2.9, y: 2.1, width: 18.2, height: 19.2), stretch: true)
    static let gridPlus = Icon(paths: [rrect(3, 3, 7.6, 7.6, 1.6), rrect(3, 13.4, 7.6, 7.6, 1.6), rrect(13.4, 13.4, 7.6, 7.6, 1.6), svg("M17.2 3 V10.6 M13.4 6.8 H21")],
                               viewBox: CGRect(x: 1.8, y: 1.8, width: 20.4, height: 20.4), stretch: true)
    static let locate = Icon(paths: [svg("M21.2 2.8 L3.4 10.6 Q2.6 11 3.5 11.5 L10 13.9 L12.5 20.5 Q12.9 21.4 13.4 20.6 Z")], viewBox: CGRect(x: 0, y: 0, width: 24, height: 24), stretch: false)
    static let mapBookmark = Icon(paths: [svg("M3 3 H25 V29 L14 22 L3 29 Z")], viewBox: CGRect(x: 0, y: 0, width: 28, height: 32), stretch: false)
    static let mapThumb = Icon(paths: [svg("M1 7.4 H4 V16.6 H1 Z M5.2 7.6 L8.2 2.2 Q9 0.6 10.4 1.6 Q11.4 2.4 10.9 4 L10.2 6.2 H14.2 Q15.8 6.4 15.4 8.2 L14.4 14 Q14.1 15.8 12.4 16 L5.6 16.6 Z")],
                               viewBox: CGRect(x: 0, y: 0, width: 16, height: 18), stretch: false)
    static let mapStar = Icon(paths: [svg("M12 1.2 L15.2 8 L22.6 8.9 L17.1 14 L18.6 21.3 L12 17.7 L5.4 21.3 L6.9 14 L1.4 8.9 L8.8 8 Z")],
                              viewBox: CGRect(x: 0, y: 0, width: 24, height: 23), stretch: false)
    static let tick = svg("M7.5 15 L12.5 20 L21.5 9.5")
    static let tickLength = 22.0
    static let tickActualLength = 7.0710678 + 13.8293

    /// View box → box transform of an icon.
    static func transform(_ icon: Icon, in box: CGRect) -> CGAffineTransform {
        let vb = icon.viewBox
        if icon.stretch {
            return CGAffineTransform(translationX: box.minX, y: box.minY)
                .scaledBy(x: box.width / vb.width, y: box.height / vb.height)
                .translatedBy(x: -vb.minX, y: -vb.minY)
        }
        let s = min(box.width / vb.width, box.height / vb.height)
        let ox = box.minX + (box.width - vb.width * s) / 2
        let oy = box.minY + (box.height - vb.height * s) / 2
        return CGAffineTransform(translationX: ox, y: oy).scaledBy(x: s, y: s).translatedBy(x: -vb.minX, y: -vb.minY)
    }

    /// Strokes the icon's paths with a pen of `width` view-box units (scaled with the icon, as SVG does).
    static func stroke(_ context: inout GraphicsContext, _ icon: Icon, in box: CGRect, color: Color, width: Double,
                       cap: CGLineCap = .round, join: CGLineJoin = .round) {
        let t = transform(icon, in: box)
        for p in icon.paths {
            let outline = p.strokedPath(StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: join)).applying(t)
            context.fill(outline, with: .color(color))
        }
    }

    /// A filled icon with a white outline (the map's flags, thumbs and stars).
    static func fillOutlined(_ context: inout GraphicsContext, _ icon: Icon, in box: CGRect, fill: Color, strokeWidth: Double) {
        let t = transform(icon, in: box)
        let p = icon.paths[0]
        let outline = p.strokedPath(StrokeStyle(lineWidth: strokeWidth, lineJoin: .round)).applying(t)
        context.fill(p.applying(t), with: .color(fill))
        context.fill(outline, with: .color(.white))
    }

    static func fill(_ context: inout GraphicsContext, _ icon: Icon, in box: CGRect, color: Color) {
        let t = transform(icon, in: box)
        for p in icon.paths { context.fill(p.applying(t), with: .color(color)) }
    }
}
