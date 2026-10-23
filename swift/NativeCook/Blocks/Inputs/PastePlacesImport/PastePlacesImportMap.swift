import SwiftUI

/// The small paper map of the invented city (Velmora) that the text area unfolds into, card-local.
/// Mirrors map.ts: drawn once into an image and shown on each folding panel; the route and the pins' dots and
/// shadows are drawn per frame on top (PastePlacesImportPaper).
enum PastePlacesImportMap {
    private static var width: Double { PastePlacesImportTheme.card.width }
    private static var height: Double { PastePlacesImportTheme.card.height }

    /// A minimal absolute SVG path parser: M, L, C and Z, which is all the map uses.
    static func path(_ d: String) -> Path {
        var path = Path()
        let tokens = d.split(separator: " ").map(String.init)
        var index = 0
        func number() -> Double {
            defer { index += 1 }
            return Double(tokens[index]) ?? 0
        }
        func point() -> CGPoint {
            let x = number()
            let y = number()
            return CGPoint(x: x, y: y)
        }
        while index < tokens.count {
            let command = tokens[index]
            index += 1
            switch command {
            case "M": path.move(to: point())
            case "L": path.addLine(to: point())
            case "C":
                let c1 = point()
                let c2 = point()
                path.addCurve(to: point(), control1: c1, control2: c2)
            case "Z": path.closeSubpath()
            default: break
            }
        }
        return path
    }

    private static func stroke(_ width: Double, cap: CGLineCap = .round) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: cap, lineJoin: .round)
    }

    /// The whole map in card-local points; `unit` is points per reference px.
    static func draw(_ context: inout GraphicsContext, unit: Double) {
        let colors = PastePlacesImportTheme.MapColors.self
        let shape = PastePlacesImportTheme.MapShape.self
        context.scaleBy(x: unit, y: unit)
        let radius = PastePlacesImportTheme.Radius.card
        let hairline = PastePlacesImportTheme.hairline
        let bounds = CGRect(x: 0, y: 0, width: width, height: height)

        var inner = context
        inner.clip(to: Path(roundedRect: bounds, cornerRadius: radius, style: .circular))
        inner.fill(Path(bounds), with: .color(colors.land))
        for park in shape.parks {
            inner.fill(
                Path(ellipseIn: CGRect(x: park.cx - park.rx, y: park.cy - park.ry, width: 2 * park.rx, height: 2 * park.ry)),
                with: .color(colors.park)
            )
        }

        // Minor streets: a slightly turned grid.
        var grid = inner
        grid.translateBy(x: width / 2, y: height / 2)
        grid.rotate(by: .degrees(shape.minorAngle))
        grid.translateBy(x: -width / 2, y: -height / 2)
        var lines = Path()
        var x = -width
        while x < 2 * width {
            lines.move(to: CGPoint(x: x, y: -height))
            lines.addLine(to: CGPoint(x: x, y: 2 * height))
            x += shape.minorGap
        }
        var y = -height
        while y < 2 * height {
            lines.move(to: CGPoint(x: -width, y: y))
            lines.addLine(to: CGPoint(x: 2 * width, y: y))
            y += shape.minorGap * 1.3
        }
        grid.stroke(lines, with: .color(colors.minor), style: stroke(shape.minorW))

        // River under the avenues, so every avenue crosses it on a bridge.
        let river = path(shape.river)
        inner.stroke(river, with: .color(colors.shore), style: stroke(shape.shoreW))
        inner.stroke(river, with: .color(colors.water), style: stroke(shape.riverW))

        let majors = shape.major.map(path)
        for major in majors { inner.stroke(major, with: .color(colors.casing), style: stroke(shape.casingW)) }
        for major in majors { inner.stroke(major, with: .color(colors.street), style: stroke(shape.majorW)) }

        // Sea over everything it covers.
        let sea = path(shape.sea)
        inner.fill(sea, with: .color(colors.water))
        inner.stroke(sea, with: .color(colors.shore), style: stroke(shape.seaShoreW))

        // Creases where the paper was folded.
        let panels = PastePlacesImportMotion.foldPanels
        for i in 1..<panels {
            let at = height * Double(i) / Double(panels)
            var dark = Path()
            dark.move(to: CGPoint(x: 0, y: at - shape.creaseW / 2))
            dark.addLine(to: CGPoint(x: width, y: at - shape.creaseW / 2))
            var light = Path()
            light.move(to: CGPoint(x: 0, y: at + shape.creaseW / 2))
            light.addLine(to: CGPoint(x: width, y: at + shape.creaseW / 2))
            inner.stroke(dark, with: .color(colors.creaseDark), style: stroke(shape.creaseW, cap: .butt))
            inner.stroke(light, with: .color(colors.creaseLight), style: stroke(shape.creaseW, cap: .butt))
        }

        // The text area's hairline.
        let border = CGRect(x: hairline / 2, y: hairline / 2, width: width - hairline, height: height - hairline)
        context.stroke(
            Path(roundedRect: border, cornerRadius: radius - hairline / 2, style: .circular),
            with: .color(PastePlacesImportTheme.cardBorder),
            style: StrokeStyle(lineWidth: hairline)
        )
    }

    /// The route through the places (Catmull-Rom through the dots), card-local points.
    static func route(unit: Double) -> Path {
        let points = PastePlacesImportTheme.spots.map { CGPoint(x: $0.x * unit, y: $0.y * unit) }
        var path = Path()
        path.move(to: points[0])
        for i in 0..<(points.count - 1) {
            let p0 = points[max(0, i - 1)]
            let p1 = points[i]
            let p2 = points[i + 1]
            let p3 = points[min(points.count - 1, i + 2)]
            path.addCurve(
                to: p2,
                control1: CGPoint(x: p1.x + (p2.x - p0.x) / 6, y: p1.y + (p2.y - p0.y) / 6),
                control2: CGPoint(x: p2.x - (p3.x - p1.x) / 6, y: p2.y - (p3.y - p1.y) / 6)
            )
        }
        return path
    }
}
