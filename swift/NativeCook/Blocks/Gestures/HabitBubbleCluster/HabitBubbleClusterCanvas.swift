import SwiftUI
import UIKit

struct HabitBubbleClusterCanvas: View {
    let model: HabitBubbleClusterModel
    let assets: HabitBubbleClusterAssets

    var body: some View {
        ZStack(alignment: .topLeading) {
            Image(uiImage: assets.backdrop(size: model.size, scale: UIScreen.main.scale))
                .resizable()
                .interpolation(.high)
                .frame(width: model.size.width, height: model.size.height, alignment: .topLeading)

            Canvas { context, _ in
                drawQuiet(context: &context)
                drawBubbles(context: &context)
                drawLabel(context: &context)
                drawFinger(context: &context)
            }
        }
        .frame(width: model.size.width, height: model.size.height, alignment: .topLeading)
        .clipped()
        .accessibilityHidden(true)
    }

    private func drawBubbles(context: inout GraphicsContext) {
        let simulation = model.simulation
        for index in HabitBubbleClusterSimulation.entities.indices {
            guard index != HabitBubbleClusterSimulation.quietIndex else { continue }
            let entity = HabitBubbleClusterSimulation.entities[index]
            let radius = simulation.radius[index]
            guard radius >= 1, let hue = HabitBubbleClusterTokens.hue[entity.hue] else { continue }
            let center = screenPoint(index)
            let charge = simulation.ring == index ? simulation.charge : 0
            let done = simulation.done[index]
            drawGlass(context: &context, center: center, radius: radius, hue: hue, lift: simulation.held == index ? 1 : 0, fill: max(charge, done))
            drawLiquid(context: &context, center: center, radius: radius, level: max(charge, done), hue: hue, calm: model.reducedMotion ? 0 : 1 - done)
            drawIcon(context: &context, entity: entity, index: index, center: center, radius: radius, hue: hue)

            if simulation.burst == index, !model.reducedMotion {
                let progress = clamp01(simulation.burstMS / HabitBubbleClusterTokens.burst.rippleMs)
                let eased = 1 - pow(1 - progress, 3)
                let rippleRadius = radius + 4 + HabitBubbleClusterTokens.burst.rippleReach * eased
                context.stroke(
                    Path(ellipseIn: circle(center: center, radius: rippleRadius)),
                    with: .color(HabitBubbleClusterColor(hue.top, alpha: 0.7 * (1 - progress))),
                    style: StrokeStyle(lineWidth: 1.5 * (1 - progress) + 0.5, lineCap: .round)
                )
            }
        }
        drawHoldRing(context: &context)
    }

    private func drawGlass(
        context: inout GraphicsContext,
        center: CGPoint,
        radius: Double,
        hue: HabitBubbleClusterTokens.Hue,
        lift: Double,
        fill: Double
    ) {
        let rect = circle(center: center, radius: radius)
        var shadow = context
        shadow.addFilter(.shadow(color: .black.opacity(0.24), radius: 12 + lift * 7, x: 0, y: 7 + lift * 5))
        shadow.fill(Path(ellipseIn: rect.insetBy(dx: 2, dy: 2)), with: .color(.black.opacity(0.10)))

        let tint = Color(red: hue.tint.0, green: hue.tint.1, blue: hue.tint.2)
        context.fill(
            Path(ellipseIn: rect),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: tint.opacity(0.10 + fill * 0.06), location: 0),
                    .init(color: tint.opacity(0.20 + fill * 0.08), location: 0.70),
                    .init(color: tint.opacity(0.31 + fill * 0.08), location: 1),
                ]),
                center: CGPoint(x: center.x - radius * 0.24, y: center.y - radius * 0.32),
                startRadius: 0,
                endRadius: radius * 1.22
            )
        )

        context.stroke(
            Path(ellipseIn: rect.insetBy(dx: 0.6, dy: 0.6)),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.56), .white.opacity(0.10), tint.opacity(0.34)]),
                startPoint: CGPoint(x: center.x - radius, y: center.y - radius),
                endPoint: CGPoint(x: center.x + radius, y: center.y + radius)
            ),
            lineWidth: 1.2
        )
        context.stroke(
            Path(ellipseIn: rect.insetBy(dx: 3.2, dy: 3.2)),
            with: .linearGradient(
                Gradient(colors: [.white.opacity(0.12 + lift * 0.08), .clear, .black.opacity(0.18)]),
                startPoint: CGPoint(x: center.x, y: center.y - radius),
                endPoint: CGPoint(x: center.x, y: center.y + radius)
            ),
            lineWidth: 4
        )

        var upperHighlight = Path()
        upperHighlight.addArc(center: center, radius: max(1, radius - 1.5), startAngle: .degrees(204), endAngle: .degrees(329), clockwise: false)
        context.stroke(upperHighlight, with: .color(.white.opacity(0.20 + lift * 0.08)), style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
    }

    private func drawLiquid(
        context: inout GraphicsContext,
        center: CGPoint,
        radius: Double,
        level: Double,
        hue: HabitBubbleClusterTokens.Hue,
        calm: Double
    ) {
        guard level > 0.002 else { return }
        let surface = center.y + radius - 2 * radius * level
        var path = Path()
        let steps = 24
        for step in 0...steps {
            let x = center.x - radius + 2 * radius * Double(step) / Double(steps)
            let wave = sin(model.simulation.clock / HabitBubbleClusterTokens.liquid.period1 + Double(step) * 0.55) * HabitBubbleClusterTokens.liquid.amp1
                + sin(model.simulation.clock / HabitBubbleClusterTokens.liquid.period2 - Double(step) * 0.9) * HabitBubbleClusterTokens.liquid.amp2
            let y = surface + wave * sin(.pi * clamp01(level)) * calm
            if step == 0 { path.move(to: CGPoint(x: x, y: y)) }
            else { path.addLine(to: CGPoint(x: x, y: y)) }
        }
        path.addLine(to: CGPoint(x: center.x + radius, y: center.y + radius))
        path.addLine(to: CGPoint(x: center.x - radius, y: center.y + radius))
        path.closeSubpath()

        var clipped = context
        clipped.clip(to: Path(ellipseIn: circle(center: center, radius: max(0, radius - 1.5))))
        clipped.fill(
            path,
            with: .linearGradient(
                Gradient(colors: [HabitBubbleClusterColor(hue.top, alpha: 0.92), HabitBubbleClusterColor(hue.bottom, alpha: 0.92)]),
                startPoint: CGPoint(x: center.x, y: surface - 4),
                endPoint: CGPoint(x: center.x, y: center.y + radius)
            )
        )
        context.stroke(Path(ellipseIn: circle(center: center, radius: max(0, radius - 0.75))), with: .color(.white.opacity(0.22 + 0.20 * level)), lineWidth: 1.2)
    }

    private func drawIcon(
        context: inout GraphicsContext,
        entity: HabitBubbleClusterEntity,
        index: Int,
        center: CGPoint,
        radius: Double,
        hue: HabitBubbleClusterTokens.Hue
    ) {
        let simulation = model.simulation
        let base = entity.radius * 2 * HabitBubbleClusterTokens.layout.iconScale
        let iconSize = max(1, base * radius / entity.radius)
        let chargeFade = simulation.ring == index ? 1 - 0.6 * simulation.charge : 1
        let iconOpacity = min(1, radius / 8) * (1 - simulation.done[index]) * chargeFade
        if iconOpacity > 0.01 {
            var symbol = context.resolve(Image(systemName: symbolName(entity.icon)))
            symbol.shading = .color(HabitBubbleClusterColor(hue.icon, alpha: iconOpacity))
            context.draw(symbol, in: CGRect(x: center.x - iconSize / 2, y: center.y - iconSize / 2, width: iconSize, height: iconSize))
        }
        let checkOpacity = simulation.done[index]
        if checkOpacity > 0.01 {
            let completedRadius = max(1, entity.radius * HabitBubbleClusterTokens.hold.doneScale)
            let ratio = min(1.1, radius / completedRadius)
            let size = base * ratio * (0.6 + 0.4 * checkOpacity)
            var check = context.resolve(Image(systemName: "checkmark"))
            check.shading = .color(HabitBubbleClusterColor(hue.ink, alpha: checkOpacity))
            context.draw(check, in: CGRect(x: center.x - size / 2, y: center.y - size / 2, width: size, height: size))
        }
    }

    private func drawQuiet(context: inout GraphicsContext) {
        let index = HabitBubbleClusterSimulation.quietIndex
        let radius = model.simulation.radius[index]
        guard radius >= 1 else { return }
        let center = screenPoint(index)
        let hue = HabitBubbleClusterTokens.hue["glass"]!
        drawGlass(context: &context, center: center, radius: radius, hue: hue, lift: 0, fill: 0)

        var wedge = Path()
        wedge.move(to: center)
        wedge.addArc(center: center, radius: radius * 0.84, startAngle: .degrees(150), endAngle: .degrees(290), clockwise: false)
        wedge.closeSubpath()
        context.fill(wedge, with: .color(.black.opacity(0.22)))
        drawArcText(context: &context, text: HabitBubbleClusterTokens.quiet.title, center: center, radius: radius * 0.68, midpoint: -.pi / 2 + 0.25, inward: true)
        drawArcText(context: &context, text: HabitBubbleClusterTokens.quiet.hours, center: center, radius: radius * 0.68 + 7 * radius / HabitBubbleClusterTokens.quiet.r, midpoint: .pi / 2 - 0.35, inward: false)
        context.fill(Path(ellipseIn: circle(center: center, radius: 3 * radius / HabitBubbleClusterTokens.quiet.r)), with: .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink, alpha: 0.9)))
    }

    private func drawArcText(context: inout GraphicsContext, text: String, center: CGPoint, radius: Double, midpoint: Double, inward: Bool) {
        let glyphs = text.map { character -> (GraphicsContext.ResolvedText, Double) in
            var glyph = context.resolve(Text(String(character)).font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.semibold, size: HabitBubbleClusterTokens.type.quiet)))
            glyph.shading = .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink, alpha: 0.8))
            let width = glyph.measure(in: CGSize(width: 40, height: 20)).width + 1.2
            return (glyph, width)
        }
        let total = glyphs.reduce(0) { $0 + $1.1 }
        let direction = inward ? 1.0 : -1.0
        var angle = midpoint - direction * total / radius / 2
        for (glyph, width) in glyphs {
            let step = width / radius
            let placement = angle + direction * step / 2
            var transformed = context
            transformed.translateBy(x: center.x + cos(placement) * radius, y: center.y + sin(placement) * radius)
            transformed.rotate(by: .radians(placement + (inward ? .pi / 2 : -.pi / 2)))
            transformed.draw(glyph, at: CGPoint(x: 0, y: inward ? 0 : HabitBubbleClusterTokens.type.quiet * 0.35), anchor: .center)
            angle += direction * step
        }
    }

    private func drawHoldRing(context: inout GraphicsContext) {
        let simulation = model.simulation
        guard simulation.ring >= 0, simulation.charge > 0.001 else { return }
        let index = simulation.ring
        let radius = simulation.radius[index] + HabitBubbleClusterTokens.layout.ringGap
        let center = screenPoint(index)
        context.stroke(Path(ellipseIn: circle(center: center, radius: radius)), with: .color(.white.opacity(0.12)), lineWidth: HabitBubbleClusterTokens.layout.ring)
        var arc = Path()
        arc.addArc(center: center, radius: radius, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * simulation.charge), clockwise: false)
        context.stroke(arc, with: .color(.white.opacity(0.9)), style: StrokeStyle(lineWidth: HabitBubbleClusterTokens.layout.ring, lineCap: .round))
    }

    private func drawLabel(context: inout GraphicsContext) {
        let index = model.labelIndex
        guard HabitBubbleClusterSimulation.entities.indices.contains(index) else { return }
        let elapsed = model.simulation.clock - model.labelAtClock
        let inProgress = model.reducedMotion ? 1 : easeOut(elapsed / HabitBubbleClusterTokens.label.inMs)
        let outProgress = clamp01((elapsed - HabitBubbleClusterTokens.label.inMs - HabitBubbleClusterTokens.label.holdMs) / HabitBubbleClusterTokens.label.outMs)
        let alpha = min(clamp01(elapsed / HabitBubbleClusterTokens.label.inMs), 1 - outProgress)
        guard alpha > 0.004 else { return }

        let entity = HabitBubbleClusterSimulation.entities[index]
        let subtitle = model.simulation.doneTarget[index] > 0.5 ? "Done today · tap to reopen" : "Hold to mark it done"
        var title = context.resolve(Text(entity.name).font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.semibold, size: HabitBubbleClusterTokens.type.label)))
        title.shading = .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.ink))
        var sub = context.resolve(Text(subtitle).font(HabitBubbleClusterAssets.font(HabitBubbleClusterTokens.FontName.regular, size: HabitBubbleClusterTokens.type.labelSub)))
        sub.shading = .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.inkSoft))
        let width = max(title.measure(in: CGSize(width: 320, height: 30)).width, sub.measure(in: CGSize(width: 320, height: 30)).width) + 32
        let height = HabitBubbleClusterTokens.layout.labelHeight
        let center = screenPoint(index)
        var x = center.x - width / 2
        x = max(HabitBubbleClusterTokens.layout.gutter, min(x, model.size.width - HabitBubbleClusterTokens.layout.gutter - width))
        let y = center.y - model.simulation.radius[index] - HabitBubbleClusterTokens.layout.labelGap - height + (model.reducedMotion ? 0 : HabitBubbleClusterTokens.label.rise * (1 - inProgress))
        let rect = CGRect(x: x, y: y, width: width, height: height)
        let rounded = Path(roundedRect: rect, cornerRadius: 16)

        var layer = context
        layer.opacity = alpha
        var shadow = layer
        shadow.addFilter(.shadow(color: .black.opacity(0.5), radius: 10, x: 0, y: 8))
        shadow.fill(rounded, with: .color(Color(red: 26 / 255, green: 29 / 255, blue: 33 / 255)))
        layer.fill(rounded, with: .color(Color(red: 26 / 255, green: 29 / 255, blue: 33 / 255)))
        layer.stroke(rounded, with: .color(.white.opacity(0.12)), lineWidth: 1)
        layer.draw(title, at: CGPoint(x: x + 16, y: y + 8), anchor: .topLeading)
        layer.draw(sub, at: CGPoint(x: x + 16, y: y + 27), anchor: .topLeading)
    }

    private func drawFinger(context: inout GraphicsContext) {
        guard model.fingerAlpha > 0.01 else { return }
        let rect = circle(center: model.finger, radius: HabitBubbleClusterTokens.layout.fingerRadius)
        context.fill(Path(ellipseIn: rect), with: .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.finger, alpha: model.fingerAlpha)))
        context.stroke(Path(ellipseIn: rect.insetBy(dx: 0.5, dy: 0.5)), with: .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.fingerRing, alpha: model.fingerAlpha)), lineWidth: 1)
    }

    private func screenPoint(_ index: Int) -> CGPoint {
        CGPoint(x: model.origin.x + model.simulation.x[index], y: model.origin.y + model.simulation.y[index])
    }

    private func circle(center: CGPoint, radius: Double) -> CGRect {
        CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
    }

    private func clamp01(_ value: Double) -> Double { min(1, max(0, value)) }

    private func easeOut(_ value: Double) -> Double {
        let clamped = clamp01(value)
        return 1 - pow(1 - clamped, 3)
    }

    private func symbolName(_ ionicon: String) -> String {
        switch ionicon {
        case "hand-left-outline": "hand.wave"
        case "star-outline": "star"
        case "basket-outline": "basket"
        case "basketball-outline": "basketball"
        case "person-outline": "person"
        case "color-palette-outline": "paintpalette"
        case "bed-outline": "bed.double"
        case "sparkles-outline": "sparkles"
        case "home-outline": "house"
        case "cafe-outline": "cup.and.saucer"
        case "heart-outline": "heart"
        case "wallet-outline": "wallet.bifold"
        case "bicycle-outline": "bicycle"
        case "mail-outline": "envelope"
        case "book-outline": "book.closed"
        case "moon-outline": "moon"
        case "leaf-outline": "leaf"
        case "walk-outline": "figure.walk"
        case "restaurant-outline": "fork.knife"
        case "language-outline": "character.book.closed"
        case "water-outline": "drop"
        case "musical-notes-outline": "music.note"
        case "body-outline": "figure.flexibility"
        case "phone-portrait-outline": "iphone"
        case "medkit-outline": "cross.case"
        case "flower-outline": "camera.macro"
        case "create-outline": "square.and.pencil"
        default: "circle"
        }
    }
}
