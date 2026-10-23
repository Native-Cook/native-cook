import SwiftUI
import UIKit

struct HealthAgeDialRing: View {
    static let outer = HealthAgeDialTheme.Layout.Dial.radius + HealthAgeDialTheme.Layout.Dial.stroke / 2
    static let size = 2 * (outer + HealthAgeDialTheme.Layout.Dial.canvasPad)

    let years: Double
    let trail: Double
    let lift: Double

    var body: some View {
        let dial = HealthAgeDialTheme.Layout.Dial.self
        let center = Self.size / 2
        let angle = years * dial.degreesPerYear
        let radians = angle * .pi / 180
        let dotCenter = CGPoint(x: center + dial.radius * sin(radians),
                                y: center - dial.radius * cos(radians))
        ZStack(alignment: .topLeading) {
            Circle()
                .trim(from: 0, to: (dial.end - dial.start) / 360)
                .stroke(ringGradient, style: StrokeStyle(lineWidth: dial.stroke, lineCap: .round))
                .rotationEffect(.degrees(dial.start - 90))
                .blur(radius: dial.edgeSoftness)
                .frame(width: dial.radius * 2, height: dial.radius * 2)
                .position(x: center, y: center)

            Capsule()
                .fill(HealthAgeDialTheme.Colors.tick)
                .frame(width: dial.tickWidth, height: dial.tickLength)
                .blur(radius: dial.edgeSoftness * 0.6)
                .position(x: center, y: center - dial.radius)

            HealthAgeDialTrail(angle: angle, length: trail)
                .frame(width: Self.size, height: Self.size)

            Circle()
                .fill(HealthAgeDialTheme.Colors.blue.opacity(0.55 * lift))
                .frame(width: dial.dot * (1 + 0.14 * lift), height: dial.dot * (1 + 0.14 * lift))
                .blur(radius: 12)
                .position(dotCenter)
            Circle()
                .fill(HealthAgeDialTheme.Colors.blue)
                .overlay(Circle().stroke(Color(red: 10 / 255, green: 100 / 255, blue: 190 / 255).opacity(0.35), lineWidth: 1.2))
                .frame(width: dial.dot * (1 + 0.14 * lift), height: dial.dot * (1 + 0.14 * lift))
                .position(dotCenter)
        }
        .frame(width: Self.size, height: Self.size, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var ringGradient: AngularGradient {
        let start = HealthAgeDialTheme.Layout.Dial.start
        var stops = [Gradient.Stop(color: Color(red: 0.71, green: 0.87, blue: 0.90), location: 0)]
        stops += HealthAgeDialTheme.ringStops.compactMap { value in
            let location = (value.angle - start) / 360
            guard location > 0, location < 1 else { return nil }
            return Gradient.Stop(color: value.color, location: location)
        }
        stops.append(.init(color: Color(hexDial: 0xE0DEE5), location: 1))
        return AngularGradient(gradient: Gradient(stops: stops), center: .center,
                               startAngle: .degrees(0), endAngle: .degrees(360))
    }
}

private struct HealthAgeDialTrail: View {
    let angle: Double
    let length: Double

    var body: some View {
        Canvas { context, size in
            guard abs(length) > 0.01 else { return }
            let center = CGPoint(x: size.width / 2, y: size.height / 2)
            let radius = HealthAgeDialTheme.Layout.Dial.radius
            let opacity = HealthAgeDialMotion.clamp(abs(length) / (HealthAgeDialMotion.Trail.maximum * HealthAgeDialMotion.Trail.fade))
            let count = 18
            context.blendMode = .screen
            context.addFilter(.blur(radius: 3))
            for index in 0..<count {
                let p0 = Double(index) / Double(count)
                let p1 = Double(index + 1) / Double(count)
                let a0 = angle + length * (1 - p0)
                let a1 = angle + length * (1 - p1)
                var arc = Path()
                arc.addArc(center: center, radius: radius,
                           startAngle: .degrees(a0 - 90), endAngle: .degrees(a1 - 90),
                           clockwise: length > 0)
                let alpha = opacity * (0.1 + 0.85 * p1)
                context.stroke(arc, with: .color(.white.opacity(alpha)),
                               style: StrokeStyle(lineWidth: HealthAgeDialTheme.Layout.Dial.stroke * 0.86,
                                                  lineCap: index == count - 1 ? .round : .butt))
            }
        }
    }
}

struct HealthAgeDialOdometer: View {
    let years: Double

    var body: some View {
        let style = HealthAgeDialTheme.TypeStyle.number
        let baseFont = UIFont.systemFont(ofSize: style.size, weight: style.weight)
        let font = baseFont.fontDescriptor.withDesign(.rounded)
            .map { UIFont(descriptor: $0, size: style.size) } ?? baseFont
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: style.tracking]
        let parts = ["-", "-3", "-3.", "-3.9"]
        let widths = parts.map { ($0 as NSString).size(withAttributes: attributes).width }
        let lineHeight = round(style.size * 1.2)
        Canvas { context, _ in
            let absolute = abs(years) * 10
            let rawTenths = absolute - floor(absolute / 10) * 10
            let tenths = HealthAgeDialMotion.rollStep(rawTenths)
            let units = HealthAgeDialMotion.rollStep(floor(absolute / 10) + max(0, rawTenths - 9))
            let slots = [
                (origin: 0.0, width: widths[0], position: Double.nan),
                (origin: widths[0], width: widths[1] - widths[0], position: units),
                (origin: widths[1], width: widths[2] - widths[1], position: Double.nan),
                (origin: widths[2], width: widths[3] - widths[2], position: tenths),
            ]
            let font = Font.system(size: style.size, weight: .bold, design: .rounded)
            func draw(_ string: String, x: Double, y: Double, opacity: Double = 1,
                      anchor: UnitPoint = .center) {
                var local = context
                local.opacity = opacity
                local.draw(local.resolve(Text(string).font(font).kerning(style.tracking).foregroundStyle(style.color)),
                           at: CGPoint(x: x, y: y), anchor: anchor)
            }

            let minusOpacity = HealthAgeDialMotion.segment(-years, HealthAgeDialMotion.Sign.from, HealthAgeDialMotion.Sign.to)
            let plusOpacity = HealthAgeDialMotion.segment(years, HealthAgeDialMotion.Sign.from, HealthAgeDialMotion.Sign.to)
            draw("-", x: widths[0], y: lineHeight / 2, opacity: minusOpacity, anchor: .trailing)
            draw("+", x: widths[0], y: lineHeight / 2, opacity: plusOpacity, anchor: .trailing)
            draw(".", x: slots[2].origin + slots[2].width / 2, y: lineHeight / 2)
            for slot in [slots[1], slots[3]] {
                for index in 0...10 {
                    let distance = Double(index) - slot.position
                    let opacity = HealthAgeDialMotion.clamp(1 - abs(distance) * HealthAgeDialMotion.Roll.falloff)
                    draw(String(index % 10), x: slot.origin + slot.width / 2,
                         y: lineHeight / 2 + distance * lineHeight * HealthAgeDialMotion.Roll.travel,
                         opacity: opacity)
                }
            }
        }
        .frame(width: widths[3], height: lineHeight, alignment: .topLeading)
        .accessibilityHidden(true)
    }

    static var measuredWidth: Double {
        let style = HealthAgeDialTheme.TypeStyle.number
        let baseFont = UIFont.systemFont(ofSize: style.size, weight: style.weight)
        let font = baseFont.fontDescriptor.withDesign(.rounded)
            .map { UIFont(descriptor: $0, size: style.size) } ?? baseFont
        return ("-3.9" as NSString).size(withAttributes: [.font: font, .kern: style.tracking]).width
    }
}

private extension Color {
    init(hexDial: UInt32) {
        self.init(red: Double((hexDial >> 16) & 255) / 255,
                  green: Double((hexDial >> 8) & 255) / 255,
                  blue: Double(hexDial & 255) / 255)
    }
}
