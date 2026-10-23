import SwiftUI

struct LumaRunStudioArt: View {
    let scene: LumaRunStudioScene
    let logicalHeight: Double
    private let resources = LumaRunStudioResources.shared

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color(red: 23 / 255, green: 25 / 255, blue: 24 / 255)
                .frame(width: 393, height: logicalHeight)
            image(resources.frame)
                .frame(width: 393, height: 462).offset(y: 105)
            artwork.frame(width: 335, height: 384, alignment: .topLeading)
                .clipShape(RoundedRectangle(cornerRadius: 10))
                .offset(x: 29, y: 136)
            controls
        }
        .frame(width: 393, height: logicalHeight, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func image(_ value: CGImage) -> some View {
        Image(decorative: value, scale: 1).resizable().interpolation(.high)
    }

    private var artwork: some View {
        ZStack(alignment: .topLeading) {
            image(resources.source.canonical)
            if scene.params.q > 0 {
                ZStack(alignment: .topLeading) {
                    image(scene.pair.a.image)
                    image(scene.pair.b.image).opacity(min(1, max(0, scene.pair.mix)))
                }
                .frame(width: 335, height: 384)
                .mask(alignment: .topLeading) {
                    image(resources.roiMask).frame(width: 275, height: 300)
                        .offset(x: 30 + scene.params.roiX, y: 42 + scene.params.roiY)
                }
                image(resources.source.canonical).opacity(min(1, max(0, scene.compare)))
            }
        }
        .frame(width: 335, height: 384, alignment: .topLeading)
    }

    private var controls: some View {
        Canvas { context, _ in
            var bracket = resources.bracket
            bracket = bracket.applying(CGAffineTransform(translationX: 59 + scene.params.roiX,
                                                         y: 178 + scene.params.roiY))
            context.opacity = min(1, max(0, scene.appearance.bracketAlpha))
            context.stroke(bracket, with: .color(accent), style: StrokeStyle(lineWidth: 1, lineCap: .round))
            context.opacity = 1

            let low = 24 + 345 * scene.params.low - scene.appearance.anticipation + scene.appearance.lowOffset
            let high = 24 + 345 * scene.params.high + scene.appearance.anticipation + scene.appearance.highOffset
            context.fill(Path(roundedRect: CGRect(x: 24, y: 629, width: 345, height: 4), cornerRadius: 2),
                         with: .color(Color(red: 72 / 255, green: 78 / 255, blue: 67 / 255)))
            context.fill(Path(roundedRect: CGRect(x: low, y: 629, width: max(0, high - low), height: 4), cornerRadius: 2),
                         with: .color(Color(red: 205 / 255, green: 217 / 255, blue: 121 / 255)))
            let radius = 10 * scene.appearance.handleScale
            for x in [low, high] {
                let rect = CGRect(x: x - radius, y: 631 - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: rect.offsetBy(dx: 0, dy: 1.5)), with: .color(.black.opacity(0.3)))
                context.fill(Path(ellipseIn: rect), with: .color(accent))
                context.stroke(Path(ellipseIn: rect.insetBy(dx: 0.5, dy: 0.5)),
                               with: .color(.white.opacity(0.16)), lineWidth: 1)
                var mark = Path(); mark.move(to: CGPoint(x: x, y: 628)); mark.addLine(to: CGPoint(x: x, y: 634))
                context.stroke(mark, with: .color(Color(red: 55 / 255, green: 64 / 255, blue: 34 / 255)),
                               style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
            }

            context.fill(Path(roundedRect: CGRect(x: 24, y: 670, width: 345, height: 60), cornerRadius: 12),
                         with: .color(Color(red: 36 / 255, green: 39 / 255, blue: 36 / 255)))
            let dial = Path(ellipseIn: CGRect(x: 308, y: 679, width: 42, height: 42))
            context.stroke(dial, with: .color(Color(red: 72 / 255, green: 78 / 255, blue: 67 / 255)), lineWidth: 1)
            for index in 0..<8 {
                let theta = Double(index) * .pi / 4
                var tick = Path()
                tick.move(to: CGPoint(x: 329 + cos(theta) * 17, y: 700 + sin(theta) * 17))
                tick.addLine(to: CGPoint(x: 329 + cos(theta) * 19, y: 700 + sin(theta) * 19))
                context.stroke(tick, with: .color(.white.opacity(0.2)), lineWidth: 1)
            }
            let angle = scene.params.angle * .pi / 180
            let dx = cos(angle), dy = sin(angle)
            var direction = Path()
            direction.move(to: CGPoint(x: 329 - 11 * dx, y: 700 - 11 * dy))
            direction.addLine(to: CGPoint(x: 329 + 11 * dx, y: 700 + 11 * dy))
            direction.move(to: CGPoint(x: 329 + 7 * dx - 3 * dy, y: 700 + 7 * dy + 3 * dx))
            direction.addLine(to: CGPoint(x: 329 + 11 * dx, y: 700 + 11 * dy))
            direction.addLine(to: CGPoint(x: 329 + 7 * dx + 3 * dy, y: 700 + 7 * dy - 3 * dx))
            context.stroke(direction, with: .color(accent), style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
        }
        .frame(width: 393, height: logicalHeight, alignment: .topLeading)
    }

    private var accent: Color { Color(red: 216 / 255, green: 230 / 255, blue: 139 / 255) }
}
