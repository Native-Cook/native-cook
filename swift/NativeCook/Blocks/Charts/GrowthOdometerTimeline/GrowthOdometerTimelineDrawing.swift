import UIKit

final class GrowthOdometerTimelineDrawing: UIView {
    var state = GrowthOdometerTimelineSimulation()
    private let avatar = UIImage(named: "GrowthOdometerTimeline-avatar")
    private let badge = UIImage(named: "GrowthOdometerTimeline-badge")
    private let mote = UIImage(named: "GrowthOdometerTimeline-mote")?.withTintColor(UIColor(red: 211 / 255, green: 1, blue: 120 / 255, alpha: 1), renderingMode: .alwaysOriginal)
    private let lime = UIColor(red: 168 / 255, green: 240 / 255, blue: 60 / 255, alpha: 1)

    override init(frame: CGRect) {
        super.init(frame: frame)
        backgroundColor = .clear
        isOpaque = false
    }

    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }

    private func color(_ rgb: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(red: Double((rgb >> 16) & 255) / 255, green: Double((rgb >> 8) & 255) / 255, blue: Double(rgb & 255) / 255, alpha: alpha)
    }

    private func gradient(_ context: CGContext, colors: [UIColor], stops: [CGFloat], from: CGPoint, to: CGPoint) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: stops) else { return }
        context.drawLinearGradient(gradient, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
    }

    private func gradientStroke(_ context: CGContext, path: CGPath, width: Double, colors: [UIColor], stops: [CGFloat], from: Double, to: Double, alpha: Double = 1, lineCap: CGLineCap = .round) {
        context.saveGState()
        context.setAlpha(alpha)
        context.addPath(path)
        context.setLineWidth(width)
        context.setLineCap(lineCap)
        context.replacePathWithStrokedPath()
        context.clip()
        gradient(context, colors: colors, stops: stops, from: CGPoint(x: 0, y: from), to: CGPoint(x: 0, y: to))
        context.restoreGState()
    }

    override func draw(_ rect: CGRect) {
        guard let context = UIGraphicsGetCurrentContext() else { return }
        let m = GrowthOdometerTimelineLayout(width: bounds.width, height: bounds.height)
        guard m.height > 0 else { return }
        drawRail(context, m)
        drawKnob(context, m)
        drawMotes(context, m)
        drawLabels(context, m)
        avatar?.draw(in: CGRect(x: m.x0, y: m.cy - 66, width: 28, height: 28))
        let name = "Nova Lin" as NSString
        let nameFont = UIFont.systemFont(ofSize: 15, weight: .semibold)
        let nameSize = name.size(withAttributes: [.font: nameFont])
        name.draw(at: CGPoint(x: m.x0 + 36, y: m.cy - 66 + (28 - nameSize.height) / 2), withAttributes: [.font: nameFont, .foregroundColor: UIColor.white])
        badge?.draw(in: CGRect(x: m.x0 + 40 + nameSize.width, y: m.cy - 59, width: 14, height: 14))
        let caption = "Followers" as NSString
        let font = UIFont.systemFont(ofSize: 13)
        let height = caption.size(withAttributes: [.font: font]).height
        caption.draw(at: CGPoint(x: m.x0, y: m.cy + 34 + (18 - height) / 2), withAttributes: [.font: font, .foregroundColor: color(0xA9B0B8)])
    }

    private func drawRail(_ context: CGContext, _ m: GrowthOdometerTimelineLayout) {
        let rail = CGMutablePath()
        rail.move(to: CGPoint(x: m.railX, y: 0))
        for y in stride(from: 2.0, through: m.height, by: 2) {
            rail.addLine(to: CGPoint(x: m.rail(at: y), y: y))
        }
        rail.addLine(to: CGPoint(x: m.railX, y: m.height))
        let lightGain = 1 + 0.35 * state.grip + 0.4 * state.tick
        gradientStroke(context, path: rail, width: 1.5,
                       colors: [color(0x27323A, 0), color(0x27323A, 128 / 255), color(0x5E8F2A, 140 / 255), lime, color(0x5E8F2A, 140 / 255), color(0x27323A, 128 / 255), color(0x27323A, 0)],
                       stops: [0, 40 / m.height, (m.cy - 120) / m.height, m.cy / m.height, (m.cy + 120) / m.height, 1 - 40 / m.height, 1], from: 0, to: m.height)
        let age = state.heroAge - 0.3
        let heroAlpha = state.reduced ? 0 : (1 - GrowthOdometerTimelineSimulation.smooth(0.5, 1.3, age)) * GrowthOdometerTimelineSimulation.smooth(0, 0.08, age)
        if heroAlpha > 0 {
            let radius = max(1, GrowthOdometerTimelineSimulation.clamp(age / 0.5, 0, 1) * max(m.cy, m.height - m.cy))
            let hero = color(0xCAFF63)
            gradientStroke(context, path: rail, width: 1.5, colors: [hero.withAlphaComponent(0), hero, hero, hero.withAlphaComponent(0)], stops: [0, 0.12, 0.88, 1], from: m.cy - radius, to: m.cy + radius, alpha: heroAlpha)
        }
        let major = CGMutablePath()
        let minor = CGMutablePath()
        for index in 0...60 {
            let y = m.cy + (Double(index) / 5 - state.p) * 64
            if y < 0 || y > m.height { continue }
            let right = m.rail(at: y) - 16
            let path = index % 5 == 0 ? major : minor
            path.move(to: CGPoint(x: right - (index % 5 == 0 ? 14 : 7), y: y))
            path.addLine(to: CGPoint(x: right, y: y))
        }
        let tickColors = [color(0x39424A, 0), color(0x39424A, 115 / 255), color(0x74806A, 128 / 255), color(0xA8F03C, 230 / 255), color(0x74806A, 128 / 255), color(0x39424A, 115 / 255), color(0x39424A, 0)]
        let stops = [0.0, 72 / m.height, (m.cy - 70) / m.height, m.cy / m.height, (m.cy + 70) / m.height, 1 - 72 / m.height, 1].map { CGFloat($0) }
        gradientStroke(context, path: major, width: 1, colors: tickColors, stops: stops, from: 0, to: m.height, alpha: min(1, lightGain), lineCap: .butt)
        gradientStroke(context, path: minor, width: 1, colors: tickColors, stops: stops, from: 0, to: m.height, alpha: min(1, lightGain) * 0.35, lineCap: .butt)
    }

    private func drawKnob(_ context: CGContext, _ m: GrowthOdometerTimelineLayout) {
        context.saveGState()
        context.translateBy(x: m.knobX, y: m.cy)
        context.scaleBy(x: 1 - 0.04 * state.press, y: 1 - 0.04 * state.press)
        func radial(_ colors: [UIColor], _ stops: [CGFloat], _ center: CGPoint, _ radius: Double) {
            guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: stops) else { return }
            context.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [.drawsAfterEndLocation])
        }
        context.saveGState()
        context.addEllipse(in: CGRect(x: -34, y: -34, width: 68, height: 68))
        context.clip()
        radial([color(0x161C24, 0.9), color(0x161C24, 0.9), color(0x161C24, 0)], [0, 0.59, 1], .zero, 34)
        context.restoreGState()
        context.saveGState()
        context.addEllipse(in: CGRect(x: -20, y: -20, width: 40, height: 40))
        context.clip()
        radial([color(0x252C36), color(0x141920)], [0, 1], CGPoint(x: -10, y: -12), 42)
        context.restoreGState()
        func arc(_ start: Double, _ sweep: Double, _ color: UIColor, _ width: Double) {
            context.setStrokeColor(color.cgColor)
            context.setLineWidth(width)
            context.addArc(center: .zero, radius: 20, startAngle: start * .pi / 180, endAngle: (start + sweep) * .pi / 180, clockwise: false)
            context.strokePath()
        }
        arc(200, 105, color(0xFFFFFF, 26 / 255), 1)
        arc(20, 100, color(0x05080B, 136 / 255), 1)
        arc(155, 55, color(0xA8F03C, 64 / 255), 0.7)
        arc(-35, 70, color(0xE5FF70, 0.08 + 0.5 * state.halo), 0.7)
        for index in 0..<3 {
            let y = -7 + Double(index) * 5.5
            let phase = state.chevronPhase + Double(index) / 3
            let pulse = pow(max(0, cos(phase * .pi * 2)), 8)
            let brightness = state.pressed || state.heroAge < (state.reduced ? 0.2 : 0.6) ? 1 : 0.22 + 0.78 * pulse
            let path = CGMutablePath()
            path.move(to: CGPoint(x: -6, y: y + 3))
            path.addLine(to: CGPoint(x: 0, y: y - 2))
            path.addLine(to: CGPoint(x: 6, y: y + 3))
            context.saveGState()
            context.setAlpha(brightness)
            context.setStrokeColor(color(0xB8F53E).cgColor)
            context.setShadow(offset: .zero, blur: 4, color: color(0xB8F53E, 0.8).cgColor)
            context.setLineWidth(1.75)
            context.setLineCap(.round)
            context.setLineJoin(.round)
            context.addPath(path)
            context.strokePath()
            context.restoreGState()
        }
        context.restoreGState()
    }

    private func drawLabels(_ context: CGContext, _ m: GrowthOdometerTimelineLayout) {
        let font = UIFont.systemFont(ofSize: 11, weight: .medium)
        for (index, month) in GrowthOdometerTimelineSimulation.months.enumerated() {
            let y = m.cy + (Double(index) - state.p) * 64
            guard y > -11, y < m.height + 11 else { continue }
            let focus = GrowthOdometerTimelineLayout.bell((y - m.cy) / 52)
            let fade = max(0, min(1, min(y / 72, (m.height - y) / 72)))
            let text = month as NSString
            let size = text.size(withAttributes: [.font: font])
            let width = ceil(size.width) + 16 + 2 / contentScaleFactor
            let right = max(m.railX - 36 - 16.1 * focus, width + 4)
            let bounds = CGRect(x: right - width, y: y - 11, width: width, height: 22)
            context.saveGState()
            context.setAlpha((0.72 + 0.28 * focus) * fade)
            let pill = UIBezierPath(roundedRect: bounds, cornerRadius: 11)
            color(0xFFFFFF, 0.035).setFill()
            pill.fill()
            lime.withAlphaComponent(0.09 + focus * (0.21 + 0.6 * state.flash)).setStroke()
            pill.lineWidth = 1 / contentScaleFactor
            pill.stroke()
            text.draw(at: CGPoint(x: bounds.midX - size.width / 2, y: y - size.height / 2), withAttributes: [.font: font, .foregroundColor: focus > 0.8 ? UIColor.white : color(0xDDE3E8)])
            context.restoreGState()
        }
    }

    private func haloField(x: Double, y: Double, layout m: GrowthOdometerTimelineLayout) -> Double {
        let level = GrowthOdometerTimelineSimulation.clamp(state.halo, 0, 1)
        let px = x * 402 / m.width
        let py = (y - m.cy) * 874 / m.height
        let grow = (1 + 0.1 * state.surge) * (state.reduced ? 1 : 1 + 0.03 * level * sin(state.time * 1.3823))
        func lobe(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double) -> Double {
            exp(-pow((px - cx) / rx, 2) - pow((py - cy) / ry, 2))
        }
        let crown = (0.45 + 0.55 * level) * grow
        var field = (0.1 + 1.5 * GrowthOdometerTimelineSimulation.smooth(0, 0.55, level)) * lobe(430, -250, 135 * crown, 210 * crown)
        let lower = GrowthOdometerTimelineSimulation.smooth(0.5, 1, level)
        let core = (0.3 + 0.7 * level) * grow
        field += 1.1 * lower * lobe(452, -120 + 150 * level, 95 * core, 170 * core)
        field += 0.45 * GrowthOdometerTimelineSimulation.smooth(0.25, 1, level) * lobe(455, -110, 60 * grow, 240 * grow)
        field += 0.3 * lower * lobe(440, 250, 120 * grow, 200 * grow)
        field *= 1 - GrowthOdometerTimelineSimulation.smooth(150, 400, py)
        field += 0.12 * GrowthOdometerTimelineSimulation.smooth(0.1, 1, level) * lobe(430, -40, 240, 420) * (1 - 0.5 * GrowthOdometerTimelineSimulation.smooth(230, 330, py))
        field *= 1 - 0.78 * exp(-pow((x - m.x0 - 60) / 90, 2) - pow((y - m.cy) / 100, 2))
        let inner = 220 + 84 * exp(-py * py / 24000)
        field *= GrowthOdometerTimelineSimulation.smooth(inner, inner + 65, px)
        field *= 1 + 0.38 * state.surge + 0.5 * max(0, state.p - 12)
        return 1.1 * (1 - exp(-1.35 * field))
    }

    private func drawMotes(_ context: CGContext, _ m: GrowthOdometerTimelineLayout) {
        guard !state.reduced, state.halo > 0 else { return }
        let alpha = 0.46 * GrowthOdometerTimelineSimulation.smooth(0.2, 0.7, state.halo)
        let push = state.surge * 15
        context.saveGState()
        context.setBlendMode(.plusLighter)
        context.setAlpha(alpha)
        for index in 0..<26 {
            let seed = (Double(index) * 0.61803398875).truncatingRemainder(dividingBy: 1)
            let run = state.time * (6 + seed * 8)
            let x = m.width - (seed * 120 + run + push).truncatingRemainder(dividingBy: 120)
            let y = (Double(index) * 83.13 - run - push + m.height * 10).truncatingRemainder(dividingBy: m.height)
            let field = haloField(x: x, y: y, layout: m)
            let size = max(0.0001, (1.2 + seed * 1.8) * GrowthOdometerTimelineSimulation.smooth(0.25, 0.6, field))
            mote?.draw(in: CGRect(x: x - size / 2, y: y - size / 2, width: size, height: size))
        }
        context.restoreGState()
    }
}
