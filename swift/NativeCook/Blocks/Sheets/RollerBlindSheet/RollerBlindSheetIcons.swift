import SwiftUI

/*
 Line icons for the room's controls and the scenes printed on the blind, drawn from the same SVG
 path data as the React Native `RollerBlindIcons.tsx` on a 24-pt grid with round caps.
 */
enum RollerBlindSheetIcon {
    case bulb, lamp, thermo, fan, moon, book, sleep, sunrise

    /// (path data, stroke width) pairs in 24-pt view-box units.
    private var parts: [(Path, Double)] {
        typealias I = RollerBlindSheetIcons
        switch self {
        case .bulb: return [(I.bulb, 1.6)]
        case .lamp: return [(I.lamp, 1.6)]
        case .thermo: return [(I.thermo, 1.6)]
        case .fan: return [(I.fanHub, 1.6), (I.fanBlades, 1.4)]
        case .moon: return [(I.moon, 1.6)]
        case .book: return [(I.book, 1.6)]
        case .sleep: return [(I.sleep, 1.6)]
        case .sunrise: return [(I.sunrise, 1.6)]
        }
    }

    func view(color: Color, size: Double = 22) -> some View {
        let parts = self.parts
        return Canvas { ctx, _ in
            ctx.scaleBy(x: size / 24, y: size / 24)
            for (path, width) in parts {
                ctx.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
            }
        }
        .frame(width: size, height: size)
    }
}

enum RollerBlindSheetIcons {
    static let bulb = svg("M9 17.5h6M10 20.5h4M8.2 14.2A6 6 0 1 1 15.8 14.2c-.8.7-1.3 1.6-1.3 2.6V17h-5v-.2c0-1-.5-1.9-1.3-2.6Z")
    static let lamp = svg("M8 3.5h8l3 8H5l3-8ZM12 11.5v8M8 20.5h8")
    static let thermo = svg("M10 4.5a2 2 0 1 1 4 0v9.3a4 4 0 1 1-4 0V4.5ZM12 10v6")
    static let fanHub = Path(ellipseIn: CGRect(x: 12 - 1.6, y: 12 - 1.6, width: 3.2, height: 3.2))
    static let fanBlades = svg("M12 10.4C11 7 12 3.5 14.6 4.2c2.2.6 1 4.6-2.6 6.2ZM13.6 12c3.4-1 6.9 0 6.2 2.6-.6 2.2-4.6 1-6.2-2.6ZM12 13.6c1 3.4 0 6.9-2.6 6.2-2.2-.6-1-4.6 2.6-6.2ZM10.4 12C7 13 3.5 12 4.2 9.4c.6-2.2 4.6-1 6.2 2.6Z")
    static let moon = svg("M19.5 14.5A8 8 0 0 1 9.5 4.5a8 8 0 1 0 10 10Z")
    static let book = svg("M12 6.5C10 5 7 4.5 4 5v13c3-.5 6 0 8 1.5 2-1.5 5-2 8-1.5V5c-3-.5-6 0-8 1.5ZM12 6.5v13")
    static let sleep = svg("M5 8h5l-5 6h5M13 5h6l-6 7h6M14 17h4l-4 4h4")
    static let sunrise = svg("M3.5 18h17M7 18a5 5 0 0 1 10 0M12 4.5v4M5.6 9.6l1.8 1.8M18.4 9.6l-1.8 1.8M7 21h10")

    /// SVG path data (M L H V C A Z, absolute and relative, compact numbers) as a Path.
    static func svg(_ d: String) -> Path {
        var tokens: [String] = []
        var number = ""
        func flush() { if !number.isEmpty { tokens.append(number); number = "" } }
        for ch in d {
            if ch.isLetter { flush(); tokens.append(String(ch)) }
            else if ch == " " || ch == "," { flush() }
            else if ch == "-" { flush(); number = "-" }
            else if ch == "." && number.contains(".") { flush(); number = "." }
            else { number.append(ch) }
        }
        flush()
        var path = Path()
        var i = 0
        var cur = CGPoint.zero
        var start = CGPoint.zero
        var cmd = "M"
        func num() -> Double { defer { i += 1 }; return i < tokens.count ? Double(tokens[i]) ?? 0 : 0 }
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
        let nu = rx * rx * ry * ry - rx * rx * y1 * y1 - ry * ry * x1 * x1
        let den = rx * rx * y1 * y1 + ry * ry * x1 * x1
        var coef = sqrt(max(0, nu / den))
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
        func pt(_ ex: Double, _ ey: Double) -> CGPoint {
            CGPoint(x: cx + rx * cp * ex - ry * sp * ey, y: cy + rx * sp * ex + ry * cp * ey)
        }
        for _ in 0..<n {
            let c0 = cos(t), s0 = sin(t), c1 = cos(t + step), s1 = sin(t + step)
            path.addCurve(to: pt(c1, s1),
                          control1: pt(c0 - k * s0, s0 + k * c0),
                          control2: pt(c1 + k * s1, s1 - k * c1))
            t += step
        }
    }
}
