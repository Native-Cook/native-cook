import SwiftUI

struct ThermalGlassTilesContent {
    let state: ThermalGlassTilesFrameState
    let resources: ThermalGlassTilesResources
    let fontScale: Double
    let miles: Bool
    typealias M = ThermalGlassTilesMotion
    typealias R = ThermalGlassTilesResources

    struct Item {
        let id: String, x: Double, y: Double, size: Double, width: Double
        var align = 0.0
    }
    static let items = [
        Item(id: "run", x: 79, y: 408, size: 13, width: 130),
        Item(id: "unit", x: 194, y: 453, size: 11, width: 24),
        Item(id: "elapsed", x: 323, y: 404, size: 11, width: 75, align: 1),
        Item(id: "bpm", x: 323, y: 427, size: 11, width: 75, align: 1),
        Item(id: "pace", x: 323, y: 450, size: 11, width: 75, align: 1),
        Item(id: "contactLabel", x: 122, y: 541, size: 11, width: 120, align: 0.5),
        Item(id: "ms", x: 160, y: 598, size: 11, width: 21),
        Item(id: "verticalLabel", x: 280, y: 541, size: 11, width: 122, align: 0.5),
        Item(id: "cm", x: 315, y: 632, size: 11, width: 20)
    ]
    static let route: Path = {
        var p = Path()
        for (i, pt) in ThermalGlassTilesRoute.points.enumerated() {
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }()
    static let arc: Path = {
        var p = Path()
        for i in 0...40 {
            let a = (-150 + Double(i) * 3) * .pi / 180
            let pt = CGPoint(x: 280 + 54 * cos(a), y: 625 + 54 * sin(a))
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        return p
    }()

    @MainActor func draw(_ context: GraphicsContext) {
        let ink = R.color(state.sample.effort > 0.82 ? 0x172326 : 0xF8F5EB), marker = R.color(0xF4EBC1)
        context.stroke(Self.route, with: .color(R.color(0xE6E9DF, 0.8)), lineWidth: 0.8)
        let pt = ThermalGlassTilesRoute.points[Int(floor(M.clamp(state.progress) * 255 + 0.5))]
        context.stroke(Path(ellipseIn: CGRect(x: pt.x - state.ring, y: pt.y - state.ring, width: 2 * state.ring, height: 2 * state.ring)),
                       with: .color(marker.opacity(0.8)), lineWidth: 0.8)
        context.fill(Path(ellipseIn: CGRect(x: pt.x - 3, y: pt.y - 3, width: 6, height: 6)), with: .color(marker))
        context.stroke(Self.arc, with: .color(ink.opacity(0.4)), lineWidth: 0.8)
        let a = (-150 + 120 * M.clamp((state.sample.oscillation - 8) / 5)) * .pi / 180
        let x = 280 + 54 * cos(a), y = 625 + 54 * sin(a)
        var triangle = Path()
        triangle.move(to: CGPoint(x: x - 3, y: y - 4)); triangle.addLine(to: CGPoint(x: x + 3, y: y - 4))
        triangle.addLine(to: CGPoint(x: x, y: y + 4)); triangle.closeSubpath()
        context.fill(triangle, with: .color(marker))
        for i in 0..<7 {
            let alpha = Double(i) <= floor(M.clamp((state.sample.contact - 245) / 50) * 6 + 0.5) ? 1.0 : 0.3
            let cx = 74 + Double(i) * 16, cy = 629 - 16 * sin(Double(i) * .pi / 6)
            context.fill(Path(ellipseIn: CGRect(x: cx - 1.7, y: cy - 1.7, width: 3.4, height: 3.4)), with: .color(ink.opacity(alpha)))
        }
        for item in Self.items { drawMetric(context, item, ink) }
        drawRolling(context, value: state.numbers[0], ints: 2, decimals: 2,
                    x: 79, y: 453, size: 40, width: 142, ink: ink)
        drawRolling(context, value: state.numbers[1], ints: 3, decimals: 0, x: 89, y: 599, size: 28, width: 89, ink: ink)
        drawRolling(context, value: state.numbers[2], ints: 2, decimals: 1, x: 230, y: 632, size: 28, width: 95, ink: ink)
    }

    private func metric(_ id: String) -> String {
        let s = state.sample
        switch id {
        case "run": return "Easy Run"
        case "unit": return miles ? "MI" : "KM"
        case "elapsed": return M.timeText(s.elapsed)
        case "bpm": return "\(Int(floor(s.bpm + 0.5))) bpm"
        case "pace": return "\(M.timeText(s.pace * (miles ? 1.609344 : 1))) /\(miles ? "mi" : "km")"
        case "contactLabel": return "Contact time"
        case "ms": return "MS"
        case "verticalLabel": return "Vertical oscillation"
        default: return "CM"
        }
    }
    @MainActor private func drawMetric(_ context: GraphicsContext, _ item: Item, _ ink: Color) {
        let size = item.size * min(fontScale, 1.6), text = metric(item.id)
        let width = resources.line(text, size).width, fit = min(1, item.width / max(1, width))
        let x = item.id == "unit" && fontScale > 1.2 ? 79 : item.x
        let y = item.id == "unit" && fontScale > 1.2 ? 478 : item.y
        let pulse = item.id == "bpm" ? state.bpmScale : 1
        var c = context
        c.translateBy(x: x, y: y); c.scaleBy(x: fit * pulse, y: pulse)
        resources.draw(c, text, x: -width * item.align, y: 0, size: size, ink: ink)
    }

    @MainActor private func drawRolling(_ context: GraphicsContext, value: Double, ints: Int, decimals: Int,
                                       x: Double, y: Double, size baseSize: Double, width: Double, ink: Color) {
        let size = baseSize * min(fontScale, 1.35), travel = size * 0.55
        let widths = (0...9).map { resources.line(String($0), size, light: true).width }
        let slotW = widths.max()!, dotW = resources.line(".", size, light: true).width
        let totalW = Double(ints) * slotW + (decimals > 0 ? dotW + Double(decimals) * slotW : 0)
        let fit = min(1, width / max(1, totalW)), u = max(0, value)
        let leadUnit = pow(10, Double(decimals + ints - 1)), vis = M.clamp(u - (leadUnit - 1))
        var c = context
        c.translateBy(x: x - (1 - vis) * slotW * fit, y: y); c.scaleBy(x: fit, y: 1)
        var cx = 0.0
        for i in 0..<ints {
            drawDigit(c, u, place: decimals + ints - 1 - i, lead: i < ints - 1,
                      x: cx, size: size, travel: travel, slotW: slotW, widths: widths, ink: ink)
            cx += slotW
        }
        if decimals > 0 {
            resources.draw(c, ".", x: cx, y: 0, size: size, light: true, ink: ink)
            cx += dotW
            for i in 0..<decimals {
                drawDigit(c, u, place: decimals - 1 - i, lead: false,
                          x: cx, size: size, travel: travel, slotW: slotW, widths: widths, ink: ink)
                cx += slotW
            }
        }
    }
    @MainActor private func drawDigit(_ c: GraphicsContext, _ u: Double, place: Int, lead: Bool,
                                     x: Double, size: Double, travel: Double, slotW: Double, widths: [Double], ink: Color) {
        let unit = pow(10, Double(place)), base = floor(u / unit)
        let roll = M.clamp(u.truncatingRemainder(dividingBy: unit) - (unit - 1))
        let a = Int(base) % 10, b = (Int(base) + 1) % 10
        let aT = min(1, roll / 0.45), bT = max(0, (roll - 0.55) / 0.45)
        let aAlpha = 1 - aT * aT * (3 - 2 * aT), bAlpha = bT * bT * (3 - 2 * bT)
        if !lead || base != 0 {
            resources.draw(c, String(a), x: x + (slotW - widths[a]) / 2, y: -roll * travel,
                           size: size, light: true, ink: ink, opacity: aAlpha)
        }
        resources.draw(c, String(b), x: x + (slotW - widths[b]) / 2, y: (1 - roll) * travel,
                       size: size, light: true, ink: ink, opacity: bAlpha)
    }
}
