import SwiftUI

struct HabitBubbleClusterCanvas: View {
    let model: HabitBubbleClusterModel
    let assets: HabitBubbleClusterAssets

    var body: some View {
        ZStack {
            if let speckle = assets.speckle {
                speckle
                    .resizable()
                    .scaledToFill()
            } else {
                HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.bg)
            }
            if model.tab == 1 {
                LinearGradient(
                    colors: [HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.personalTop), HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.personalBottom)],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            Canvas { context, _ in
                drawBubbles(context: &context)
                drawFinger(context: &context)
            }
        }
        .clipped()
        .accessibilityHidden(true)
    }

    private func drawBubbles(context: inout GraphicsContext) {
        let simulation = model.simulation
        let origin = model.origin
        for index in HabitBubbleClusterSimulation.entities.indices {
            let entity = HabitBubbleClusterSimulation.entities[index]
            let radius = simulation.radius[index]
            guard radius >= 1 else { continue }
            let center = CGPoint(x: origin.x + simulation.x[index], y: origin.y + simulation.y[index])
            if entity.quiet {
                drawQuiet(context: &context, center: center, radius: radius)
                continue
            }
            let hue = HabitBubbleClusterTokens.hue[entity.hue] ?? HabitBubbleClusterTokens.hue["glass"]!
            if let sprite = assets.sprite(for: entity.id) {
                var glass = context
                glass.blendMode = .lighten
                let reach = HabitBubbleClusterTokens.atlas.reach
                glass.draw(sprite, in: CGRect(x: center.x - radius * reach, y: center.y - radius * reach, width: radius * reach * 2, height: radius * reach * 2))
            } else {
                let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: rect), with: .radialGradient(
                    Gradient(colors: [HabitBubbleClusterColor(hue.inner, alpha: 0.25), HabitBubbleClusterColor(hue.rim, alpha: 0.72), HabitBubbleClusterColor(hue.light, alpha: 0.8)]),
                    center: center,
                    startRadius: 0,
                    endRadius: radius
                ))
            }
            if simulation.ring == index, simulation.charge > 0 { drawLiquid(context: &context, index: index, center: center, radius: radius, hue: hue) }
            if simulation.done[index] > 0.01 {
                let rect = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
                context.fill(Path(ellipseIn: rect), with: .radialGradient(
                    Gradient(colors: [HabitBubbleClusterColor("#FFFFFF", alpha: simulation.done[index] * 0.95), HabitBubbleClusterColor(hue.rim, alpha: simulation.done[index] * 0.8)]),
                    center: CGPoint(x: center.x - radius * 0.2, y: center.y - radius * 0.25),
                    startRadius: 0,
                    endRadius: radius * 1.2
                ))
            }
            context.stroke(
                Path(ellipseIn: CGRect(x: center.x - radius + HabitBubbleClusterTokens.render.rimWidth / 2, y: center.y - radius + HabitBubbleClusterTokens.render.rimWidth / 2, width: radius * 2 - HabitBubbleClusterTokens.render.rimWidth, height: radius * 2 - HabitBubbleClusterTokens.render.rimWidth)),
                with: .color(HabitBubbleClusterColor(hue.light, alpha: entity.hue == "glass" ? 0.12 : 0.45)),
                lineWidth: HabitBubbleClusterTokens.render.rimWidth
            )
            drawIcon(context: &context, entity: entity, index: index, center: center, radius: radius, hue: hue)
            if simulation.flash[index] >= 0 {
                let progress = simulation.flash[index] / HabitBubbleClusterTokens.burst.flashMs
                let flashRadius = radius + HabitBubbleClusterTokens.render.flashTravel * progress
                context.stroke(Path(ellipseIn: CGRect(x: center.x - flashRadius, y: center.y - flashRadius, width: flashRadius * 2, height: flashRadius * 2)), with: .color(.white.opacity(1 - progress)), lineWidth: HabitBubbleClusterTokens.render.flashWidth * (1 - progress) + HabitBubbleClusterTokens.render.flashMinWidth)
            }
        }
        drawHoldRing(context: &context)
        drawBurst(context: &context)
    }

    private func drawLiquid(context: inout GraphicsContext, index: Int, center: CGPoint, radius: Double, hue: HabitBubbleClusterTokens.Hue) {
        let simulation = model.simulation
        let charge = simulation.charge
        let level = center.y + radius - 2 * radius * charge
        var liquid = Path()
        for step in 0...HabitBubbleClusterTokens.render.liquidSteps {
            let x = center.x - radius + 2 * radius * Double(step) / Double(HabitBubbleClusterTokens.render.liquidSteps)
            let wave = sin(simulation.clock / HabitBubbleClusterTokens.liquid.period1 + Double(step) * HabitBubbleClusterTokens.render.waveStep1) * HabitBubbleClusterTokens.liquid.amp1 + sin(simulation.clock / HabitBubbleClusterTokens.liquid.period2 - Double(step) * HabitBubbleClusterTokens.render.waveStep2) * HabitBubbleClusterTokens.liquid.amp2
            let y = level + wave * sin(.pi * charge)
            if step == 0 { liquid.move(to: CGPoint(x: x, y: y)) } else { liquid.addLine(to: CGPoint(x: x, y: y)) }
        }
        liquid.addLine(to: CGPoint(x: center.x + radius, y: center.y + radius))
        liquid.addLine(to: CGPoint(x: center.x - radius, y: center.y + radius))
        liquid.closeSubpath()
        var clipped = context
        let inset = HabitBubbleClusterTokens.render.liquidInset
        clipped.clip(to: Path(ellipseIn: CGRect(x: center.x - radius + inset, y: center.y - radius + inset, width: radius * 2 - inset * 2, height: radius * 2 - inset * 2)))
        clipped.fill(liquid, with: .linearGradient(
            Gradient(colors: [HabitBubbleClusterColor(hue.light, alpha: HabitBubbleClusterTokens.liquid.top), HabitBubbleClusterColor(hue.rim, alpha: HabitBubbleClusterTokens.liquid.bottom)]),
            startPoint: CGPoint(x: center.x, y: level - HabitBubbleClusterTokens.liquid.amp1),
            endPoint: CGPoint(x: center.x, y: center.y + radius)
        ))
    }

    private func drawIcon(context: inout GraphicsContext, entity: HabitBubbleClusterEntity, index: Int, center: CGPoint, radius: Double, hue: HabitBubbleClusterTokens.Hue) {
        let base = entity.radius * 2 * HabitBubbleClusterTokens.layout.iconScale
        let iconSize = max(1, base * radius / entity.radius)
        let iconOpacity = min(1, radius / 8) * (1 - model.simulation.done[index])
        if iconOpacity > 0.01 {
            var symbol = context.resolve(Image(systemName: symbolName(entity.icon)))
            symbol.shading = .color(HabitBubbleClusterColor(hue.icon, alpha: iconOpacity))
            context.draw(symbol, in: CGRect(x: center.x - iconSize / 2, y: center.y - iconSize / 2, width: iconSize, height: iconSize))
        }
        let checkOpacity = model.simulation.done[index]
        if checkOpacity > 0.01 {
            let size = iconSize * (0.6 + 0.4 * checkOpacity)
            var check = context.resolve(Image(systemName: "checkmark"))
            check.shading = .color(HabitBubbleClusterColor(hue.core, alpha: checkOpacity))
            context.draw(check, in: CGRect(x: center.x - size / 2, y: center.y - size / 2, width: size, height: size))
        }
    }

    private func drawQuiet(context: inout GraphicsContext, center: CGPoint, radius: Double) {
        let haloScale = HabitBubbleClusterTokens.render.quietHaloScale
        let halo = CGRect(x: center.x - radius * haloScale, y: center.y - radius * haloScale, width: radius * haloScale * 2, height: radius * haloScale * 2)
        context.fill(Path(ellipseIn: halo), with: .radialGradient(Gradient(colors: [HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.quiet, alpha: HabitBubbleClusterTokens.render.quietHaloAlpha), .clear]), center: center, startRadius: 0, endRadius: radius * haloScale))
        let disc = CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2)
        context.fill(Path(ellipseIn: disc), with: .radialGradient(Gradient(colors: [HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.quietCore, alpha: HabitBubbleClusterTokens.quietGlassBody), HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.quiet, alpha: HabitBubbleClusterTokens.quietGlassBody)]), center: center, startRadius: 0, endRadius: radius))
        var wedge = Path()
        wedge.move(to: center)
        wedge.addArc(center: center, radius: radius, startAngle: .degrees(HabitBubbleClusterTokens.render.quietStart), endAngle: .degrees(HabitBubbleClusterTokens.render.quietEnd), clockwise: false)
        wedge.closeSubpath()
        context.fill(wedge, with: .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.quiet, alpha: HabitBubbleClusterTokens.quietGlassWedge)))
        drawArcText(context: &context, text: HabitBubbleClusterTokens.quiet.title, center: center, radius: radius * HabitBubbleClusterTokens.render.quietTextRadius, midpoint: -.pi / 2 + HabitBubbleClusterTokens.render.quietTitleOffset, inward: true)
        drawArcText(context: &context, text: HabitBubbleClusterTokens.quiet.hours, center: center, radius: radius * HabitBubbleClusterTokens.render.quietTextRadius + HabitBubbleClusterTokens.render.quietHoursExtra * radius / HabitBubbleClusterTokens.quiet.r, midpoint: .pi / 2 - HabitBubbleClusterTokens.render.quietHoursOffset, inward: false)
        let core = HabitBubbleClusterTokens.render.quietCore * radius / HabitBubbleClusterTokens.quiet.r
        context.fill(Path(ellipseIn: CGRect(x: center.x - core, y: center.y - core, width: core * 2, height: core * 2)), with: .color(.white))
        let coreRing = HabitBubbleClusterTokens.render.quietCoreRing * radius / HabitBubbleClusterTokens.quiet.r
        context.stroke(Path(ellipseIn: CGRect(x: center.x - coreRing, y: center.y - coreRing, width: coreRing * 2, height: coreRing * 2)), with: .color(.white.opacity(HabitBubbleClusterTokens.render.quietCoreRingAlpha)), lineWidth: 1)
    }

    private func drawArcText(context: inout GraphicsContext, text: String, center: CGPoint, radius: Double, midpoint: Double, inward: Bool) {
        let glyphs = text.map { character -> (GraphicsContext.ResolvedText, Double) in
            var glyph = context.resolve(Text(String(character)).font(.system(size: HabitBubbleClusterTokens.type.quietFontSize, weight: HabitBubbleClusterTokens.weight.quiet)))
            glyph.shading = .color(.white.opacity(0.85))
            let width = glyph.measure(in: CGSize(width: 40, height: 20)).width + HabitBubbleClusterTokens.render.arcTracking
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
            transformed.draw(glyph, at: CGPoint(x: 0, y: inward ? 0 : HabitBubbleClusterTokens.type.quietFontSize * 0.35), anchor: .center)
            angle += direction * step
        }
    }

    private func drawHoldRing(context: inout GraphicsContext) {
        let simulation = model.simulation
        guard simulation.ring >= 0, simulation.charge > 0.001 else { return }
        let index = simulation.ring
        let entity = HabitBubbleClusterSimulation.entities[index]
        guard let hue = HabitBubbleClusterTokens.hue[entity.hue] else { return }
        let radius = simulation.radius[index] + HabitBubbleClusterTokens.layout.ringGap
        let center = CGPoint(x: model.origin.x + simulation.x[index], y: model.origin.y + simulation.y[index])
        var arc = Path()
        arc.addArc(center: center, radius: radius, startAngle: .degrees(-90), endAngle: .degrees(-90 + 360 * simulation.charge), clockwise: false)
        context.stroke(arc, with: .color(HabitBubbleClusterColor(hue.rim, alpha: HabitBubbleClusterTokens.render.ringGlowAlpha)), style: StrokeStyle(lineWidth: HabitBubbleClusterTokens.layout.ring + HabitBubbleClusterTokens.render.ringGlowExtra, lineCap: .round))
        context.stroke(arc, with: .color(.white.opacity(HabitBubbleClusterTokens.render.ringAlpha)), style: StrokeStyle(lineWidth: HabitBubbleClusterTokens.layout.ring, lineCap: .round))
    }

    private func drawBurst(context: inout GraphicsContext) {
        let simulation = model.simulation
        guard simulation.burst >= 0 else { return }
        let index = simulation.burst
        let entity = HabitBubbleClusterSimulation.entities[index]
        guard let hue = HabitBubbleClusterTokens.hue[entity.hue] else { return }
        let progress = simulation.burstMS / HabitBubbleClusterTokens.burst.sparkMs
        let eased = 1 - pow(1 - progress, 3)
        let center = CGPoint(x: model.origin.x + simulation.x[index], y: model.origin.y + simulation.y[index])
        for spark in 0..<Int(HabitBubbleClusterTokens.layout.sparks) {
            let angle = Double(spark) / HabitBubbleClusterTokens.layout.sparks * .pi * 2 + Double(index)
            let far = spark.isMultiple(of: 2) ? HabitBubbleClusterTokens.burst.sparkFar : HabitBubbleClusterTokens.burst.sparkNear
            let distance = simulation.radius[index] + HabitBubbleClusterTokens.burst.sparkFrom + far * eased
            let sparkRadius = (spark.isMultiple(of: 2) ? HabitBubbleClusterTokens.render.sparkLarge : HabitBubbleClusterTokens.render.sparkSmall) * (1 - progress * HabitBubbleClusterTokens.render.sparkFade)
            let rect = CGRect(x: center.x + cos(angle) * distance - sparkRadius, y: center.y + sin(angle) * distance - sparkRadius, width: sparkRadius * 2, height: sparkRadius * 2)
            context.fill(Path(ellipseIn: rect), with: .color(HabitBubbleClusterColor(spark.isMultiple(of: 3) ? HabitBubbleClusterTokens.Color.spark : hue.rim, alpha: 1 - progress)))
        }
    }

    private func drawFinger(context: inout GraphicsContext) {
        guard model.fingerAlpha > 0.01 else { return }
        let radius = HabitBubbleClusterTokens.layout.fingerRadius
        let rect = CGRect(x: model.finger.x - radius, y: model.finger.y - radius, width: radius * 2, height: radius * 2)
        context.fill(Path(ellipseIn: rect), with: .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.finger, alpha: model.fingerAlpha)))
        context.stroke(Path(ellipseIn: rect), with: .color(HabitBubbleClusterColor(HabitBubbleClusterTokens.Color.fingerRing, alpha: model.fingerAlpha)), lineWidth: HabitBubbleClusterTokens.render.fingerLine)
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
        case "wallet-outline": "creditcard"
        case "bicycle-outline": "bicycle"
        case "mail-outline": "envelope"
        case "create-outline": "square.and.pencil"
        default: "circle"
        }
    }
}

func HabitBubbleClusterColor(_ value: String, alpha multiplier: Double = 1) -> Color {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.hasPrefix("#") {
        let hex = String(trimmed.dropFirst())
        guard let raw = UInt64(hex, radix: 16) else { return .clear }
        let red = Double((raw >> 16) & 0xff) / 255
        let green = Double((raw >> 8) & 0xff) / 255
        let blue = Double(raw & 0xff) / 255
        return Color(red: red, green: green, blue: blue, opacity: multiplier)
    }
    let numbers = trimmed.drop(while: { $0 != "(" }).dropFirst().dropLast().split(separator: ",").compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
    guard numbers.count >= 3 else { return .clear }
    let alpha = numbers.count > 3 ? numbers[3] : 1
    return Color(red: numbers[0] / 255, green: numbers[1] / 255, blue: numbers[2] / 255, opacity: alpha * multiplier)
}
