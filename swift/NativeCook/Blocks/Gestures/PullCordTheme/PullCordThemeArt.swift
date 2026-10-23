import SwiftUI

enum PullCordThemeArt {
    static func draw(
        context: inout GraphicsContext,
        size: CGSize,
        snapshot: PullCordThemeSnapshot,
        masks: PullCordThemePageMasks?
    ) {
        let full = CGRect(origin: .zero, size: size)
        let pageShader = shader(snapshot, role: 0)
        context.fill(Path(full), with: .shader(pageShader, bounds: full))
        if let masks {
            drawMask(masks.mute, role: 2, context: &context, rect: full, snapshot: snapshot)
            drawMask(masks.ink, role: 1, context: &context, rect: full, snapshot: snapshot)
            drawMask(masks.accent, role: 3, context: &context, rect: full, snapshot: snapshot)
        }
        drawAir(context: &context, rect: full, snapshot: snapshot)
        drawLamp(context: &context, snapshot: snapshot)
        drawChain(context: &context, snapshot: snapshot)
    }

    private static func shader(_ snapshot: PullCordThemeSnapshot, role: Double) -> Shader {
        let geometry = snapshot.geometry
        let bulb = PullCordThemeSimulation.lampPoint(
            geometry,
            angle: snapshot.angle,
            localX: 0,
            localY: PullCordThemeTokens.Lamp.bulbY
        )
        let lamp = PullCordThemeLight.lampColor(heat: snapshot.heat)
        let brightness = PullCordThemeLight.brightness(snapshot.heat) * snapshot.flicker
        let axis = CGPoint(x: sin(snapshot.angle), y: cos(snapshot.angle))
        let chain = shadowPoints(snapshot)
        return ShaderLibrary.pullCordThemePage(
            .float2(bulb),
            .float2(axis),
            .float(PullCordThemeTokens.Lamp.depth * geometry.scale),
            .float2(snapshot.range.low, snapshot.range.high),
            .float(snapshot.front),
            .float(PullCordThemeTokens.Motion.Front.band),
            .float(snapshot.reduced ? 1 : 0),
            .float(brightness),
            .float3(lamp.x, lamp.y, lamp.z),
            .float(geometry.scale),
            .float(snapshot.adaptation),
            .float(role),
            .floatArray(chain)
        )
    }

    private static func shadowPoints(_ snapshot: PullCordThemeSnapshot) -> [Float] {
        var result = snapshot.chain.flatMap { [Float($0.x), Float($0.y)] }
        let last = snapshot.chain.count - 1
        let dx = snapshot.chain[last].x - snapshot.chain[last - 1].x
        let dy = snapshot.chain[last].y - snapshot.chain[last - 1].y
        let length = max(hypot(dx, dy), 1)
        let reach = (PullCordThemeTokens.Chain.pullHeight + PullCordThemeTokens.Chain.ball)
            * snapshot.geometry.scale
        result.append(Float(snapshot.chain[last].x + dx / length * reach))
        result.append(Float(snapshot.chain[last].y + dy / length * reach))
        return result
    }

    private static func drawMask(
        _ image: UIImage,
        role: Double,
        context: inout GraphicsContext,
        rect: CGRect,
        snapshot: PullCordThemeSnapshot
    ) {
        let shader = shader(snapshot, role: role)
        context.drawLayer { layer in
            layer.draw(Image(uiImage: image), in: rect)
            layer.blendMode = .sourceIn
            layer.fill(Path(rect), with: .shader(shader, bounds: rect))
        }
    }

    private static func drawAir(
        context: inout GraphicsContext,
        rect: CGRect,
        snapshot: PullCordThemeSnapshot
    ) {
        let geometry = snapshot.geometry
        let bulb = PullCordThemeSimulation.lampPoint(
            geometry,
            angle: snapshot.angle,
            localX: 0,
            localY: PullCordThemeTokens.Lamp.bulbY
        )
        let lamp = PullCordThemeLight.lampColor(heat: snapshot.heat)
        let brightness = PullCordThemeLight.brightness(snapshot.heat) * snapshot.flicker
            * (snapshot.reduced ? 0.6 : 1)
        let shader = ShaderLibrary.pullCordThemeAir(
            .float2(bulb),
            .float2(sin(snapshot.angle), cos(snapshot.angle)),
            .float(brightness),
            .float3(lamp.x, lamp.y, lamp.z),
            .float(snapshot.reduced ? 0 : snapshot.clock.truncatingRemainder(dividingBy: PullCordTheme.loop)),
            .float(geometry.scale)
        )
        context.drawLayer { layer in
            layer.blendMode = .screen
            layer.fill(Path(rect), with: .shader(shader, bounds: rect))
        }
    }

    private static func drawLamp(context: inout GraphicsContext, snapshot: PullCordThemeSnapshot) {
        let geometry = snapshot.geometry
        let front = snapshot.front * (0.45 + 0.55 * snapshot.heat)
        let inside = snapshot.heat * snapshot.heat
        let bloom = PullCordThemeLight.brightness(snapshot.heat) * snapshot.flicker
        var lamp = context
        lamp.translateBy(x: geometry.pivotX, y: geometry.pivotY)
        lamp.rotate(by: .radians(-snapshot.angle))
        lamp.translateBy(x: 0, y: geometry.arm)
        lamp.scaleBy(x: geometry.scale, y: geometry.scale)

        let cord = CGRect(x: -1.1, y: -PullCordThemeTokens.Lamp.cord, width: 2.2, height: PullCordThemeTokens.Lamp.cord + 1)
        lamp.fill(Path(cord), with: .color(color(PullCordThemeTokens.Shade.night.cord)))
        var dayCord = lamp
        dayCord.opacity = front
        dayCord.fill(Path(cord), with: .color(color(PullCordThemeTokens.Shade.day.cord)))

        drawShell(context: &lamp, colors: PullCordThemeTokens.Shade.night)
        var dayShell = lamp
        dayShell.opacity = front
        drawShell(context: &dayShell, colors: PullCordThemeTokens.Shade.day)

        if bloom > 0.001 {
            var bounce = lamp
            bounce.opacity = bloom * 0.16
            bounce.fill(
                domePath(),
                with: .linearGradient(
                    Gradient(colors: [.clear, color(PullCordThemeTokens.Shade.bounce)]),
                    startPoint: CGPoint(x: 0, y: PullCordThemeTokens.Lamp.rimY * 0.45),
                    endPoint: CGPoint(x: 0, y: PullCordThemeTokens.Lamp.rimY)
                )
            )
        }

        let opening = CGRect(
            x: -PullCordThemeTokens.Lamp.rimHalf + 1,
            y: PullCordThemeTokens.Lamp.rimY - PullCordThemeTokens.Lamp.rimRY + 0.5,
            width: 2 * PullCordThemeTokens.Lamp.rimHalf - 2,
            height: 2 * PullCordThemeTokens.Lamp.rimRY - 1
        )
        lamp.fill(
            Path(ellipseIn: opening),
            with: .radialGradient(
                Gradient(colors: [
                    color(PullCordThemeTokens.Shade.coldCentre),
                    color(PullCordThemeTokens.Shade.coldEdge)
                ]),
                center: CGPoint(x: 0, y: PullCordThemeTokens.Lamp.rimY),
                startRadius: 0,
                endRadius: PullCordThemeTokens.Lamp.rimHalf
            )
        )
        var litInside = lamp
        litInside.opacity = inside
        let lampColor = PullCordThemeLight.lampColor(heat: snapshot.heat)
        litInside.fill(
            Path(ellipseIn: opening),
            with: .radialGradient(
                Gradient(colors: [
                    mixedColor(lampColor, color: PullCordThemeTokens.Shade.litCentre, amount: snapshot.heat),
                    mixedColor(lampColor * 0.6, color: PullCordThemeTokens.Shade.litEdge, amount: snapshot.heat)
                ]),
                center: CGPoint(x: 0, y: PullCordThemeTokens.Lamp.rimY),
                startRadius: 0,
                endRadius: PullCordThemeTokens.Lamp.rimHalf
            )
        )

        drawHousing(context: &lamp, colors: PullCordThemeTokens.SwitchBody.night)
        var dayHousing = lamp
        dayHousing.opacity = front
        drawHousing(context: &dayHousing, colors: PullCordThemeTokens.SwitchBody.day)

        let lip = lipPath()
        lamp.stroke(lip, with: .color(color(PullCordThemeTokens.Shade.night.lip)), lineWidth: PullCordThemeTokens.Lamp.lip)
        var dayLip = lamp
        dayLip.opacity = front
        dayLip.stroke(lip, with: .color(color(PullCordThemeTokens.Shade.day.lip)), lineWidth: PullCordThemeTokens.Lamp.lip)
        var hotLip = lamp
        hotLip.opacity = inside
        hotLip.stroke(lip, with: .color(color(PullCordThemeTokens.Shade.lipLit)), lineWidth: PullCordThemeTokens.Lamp.lip * 0.6)

        drawBulb(context: &lamp, heat: snapshot.heat, inside: inside, bloom: bloom, lampColor: lampColor)
    }

    private static func drawShell(
        context: inout GraphicsContext,
        colors: PullCordThemeTokens.Shade.Shell
    ) {
        let path = domePath()
        context.fill(
            path,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(colors.high), location: 0),
                    .init(color: color(colors.middle), location: 0.3),
                    .init(color: color(colors.middle), location: 0.62),
                    .init(color: color(colors.low), location: 1)
                ]),
                startPoint: CGPoint(x: -PullCordThemeTokens.Lamp.rimHalf, y: 0),
                endPoint: CGPoint(x: PullCordThemeTokens.Lamp.rimHalf, y: 0)
            )
        )
        context.fill(
            path,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .black.opacity(0.32), location: 0),
                    .init(color: .clear, location: 0.5),
                    .init(color: .white.opacity(0.06), location: 1)
                ]),
                startPoint: CGPoint(x: 0, y: PullCordThemeTokens.Lamp.capHeight),
                endPoint: CGPoint(x: 0, y: PullCordThemeTokens.Lamp.rimY)
            )
        )
        var reflection = Path()
        reflection.move(to: CGPoint(x: -6, y: 16))
        reflection.addCurve(
            to: CGPoint(x: -PullCordThemeTokens.Lamp.rimHalf * 0.62, y: PullCordThemeTokens.Lamp.rimY - 6),
            control1: CGPoint(x: -10, y: 30),
            control2: CGPoint(
                x: -PullCordThemeTokens.Lamp.rimHalf * 0.48,
                y: PullCordThemeTokens.Lamp.rimY * 0.56
            )
        )
        var highlight = context
        highlight.addFilter(.blur(radius: 1.4))
        highlight.stroke(
            reflection,
            with: .color(color(colors.specular).opacity(colors.specularAlpha)),
            style: StrokeStyle(lineWidth: 3, lineCap: .round)
        )
        let cap = Path(roundedRect: CGRect(
            x: -PullCordThemeTokens.Lamp.capHalf,
            y: 0,
            width: 2 * PullCordThemeTokens.Lamp.capHalf,
            height: PullCordThemeTokens.Lamp.capHeight + 1
        ), cornerRadius: 2)
        context.fill(
            cap,
            with: .linearGradient(
                Gradient(colors: [color(colors.high), color(colors.middle), color(colors.low)]),
                startPoint: CGPoint(x: -PullCordThemeTokens.Lamp.capHalf, y: 0),
                endPoint: CGPoint(x: PullCordThemeTokens.Lamp.capHalf, y: 0)
            )
        )
    }

    private static func drawHousing(context: inout GraphicsContext, colors: [UInt32]) {
        let body = PullCordThemeTokens.SwitchBody.self
        let left = body.x - body.width / 2
        let foot = body.top + body.height
        let gradient = Gradient(stops: [
            .init(color: color(colors[0]), location: 0),
            .init(color: color(colors[1]), location: 0.35),
            .init(color: color(colors[2]), location: 0.65),
            .init(color: color(colors[3]), location: 1)
        ])
        context.fill(
            Path(roundedRect: CGRect(x: left, y: body.top, width: body.width, height: body.height), cornerRadius: 2.5),
            with: .linearGradient(gradient, startPoint: CGPoint(x: left, y: 0), endPoint: CGPoint(x: left + body.width, y: 0))
        )
        for index in 0..<body.knurls {
            context.fill(
                Path(CGRect(x: left, y: body.top + 6 + Double(index) * 2, width: body.width, height: 0.8)),
                with: .color(.black.opacity(0.28))
            )
        }
        context.fill(
            Path(roundedRect: CGRect(x: left - 0.6, y: foot - 4, width: body.width + 1.2, height: 4), cornerRadius: 1.6),
            with: .linearGradient(gradient, startPoint: CGPoint(x: left, y: 0), endPoint: CGPoint(x: left + body.width, y: 0))
        )
        context.fill(
            Path(ellipseIn: CGRect(x: body.x - 1.6, y: foot - 1.2, width: 3.2, height: 1.6)),
            with: .color(.black.opacity(0.75))
        )
    }

    private static func drawBulb(
        context: inout GraphicsContext,
        heat: Double,
        inside: Double,
        bloom: Double,
        lampColor: SIMD3<Float>
    ) {
        let lamp = PullCordThemeTokens.Lamp.self
        let bulb = CGRect(x: -lamp.bulbR, y: lamp.bulbY - lamp.bulbR, width: 2 * lamp.bulbR, height: 2 * lamp.bulbR)
        context.fill(
            Path(ellipseIn: bulb),
            with: .radialGradient(
                Gradient(colors: [color(PullCordThemeTokens.Shade.glassCold), color(PullCordThemeTokens.Shade.glassColdEdge)]),
                center: CGPoint(x: -6, y: lamp.bulbY + 6),
                startRadius: 0,
                endRadius: lamp.bulbR * 1.3
            )
        )
        var lit = context
        lit.opacity = inside
        let white = SIMD3<Float>(repeating: 1)
        let black = SIMD3<Float>(repeating: 0)
        lit.fill(
            Path(ellipseIn: bulb),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: color(simdMix(lampColor, white, heat)), location: 0),
                    .init(color: color(simdMix(lampColor, white, heat * 0.8)), location: 0.35),
                    .init(color: color(lampColor), location: 0.8),
                    .init(color: color(simdMix(lampColor, black, 0.3)), location: 1)
                ]),
                center: CGPoint(x: 0, y: lamp.bulbY + 5),
                startRadius: 0,
                endRadius: lamp.bulbR
            )
        )
        if bloom > 0.001 {
            var core = context
            core.opacity = bloom
            core.addFilter(.blur(radius: 5))
            let radius = lamp.bulbR * 0.55
            core.fill(
                Path(ellipseIn: CGRect(x: -radius, y: lamp.bulbY + 5 - radius, width: 2 * radius, height: 2 * radius)),
                with: .color(.white)
            )
            var glow = context
            glow.opacity = bloom
            glow.addFilter(.blur(radius: 12))
            let radius2 = lamp.bulbR + 6
            glow.fill(
                Path(ellipseIn: CGRect(x: -radius2, y: lamp.bulbY + 8 - radius2, width: 2 * radius2, height: 2 * radius2)),
                with: .color(color(lampColor))
            )
        }
    }

    private static func drawChain(context: inout GraphicsContext, snapshot: PullCordThemeSnapshot) {
        let scale = snapshot.geometry.scale
        let front = snapshot.front * (0.45 + 0.55 * snapshot.heat)
        drawNickel(context: &context, snapshot: snapshot, colors: PullCordThemeTokens.Chain.night)
        var day = context
        day.opacity = front
        drawNickel(context: &day, snapshot: snapshot, colors: PullCordThemeTokens.Chain.day)

        // Keep the target in the same coordinate space as RN; the drawing above is global.
        _ = scale
    }

    private static func drawNickel(
        context: inout GraphicsContext,
        snapshot: PullCordThemeSnapshot,
        colors: PullCordThemeTokens.Chain.Metal
    ) {
        let points = snapshot.chain
        let scale = snapshot.geometry.scale
        let radius = PullCordThemeTokens.Chain.ball * scale
        let pitch = PullCordThemeTokens.Chain.pitch * scale
        var wire = Path()
        wire.move(to: snapshot.foot)
        let dx = points[0].x - snapshot.foot.x
        let dy = points[0].y - snapshot.foot.y
        let out = hypot(dx, dy)
        if out > radius {
            let count = Int(floor(out / pitch))
            if count >= 1 {
                for index in stride(from: count, through: 1, by: -1) {
                    let distance = out - Double(index) * pitch
                    if distance > radius {
                        drawBall(
                            context: &context,
                            centre: CGPoint(
                                x: snapshot.foot.x + dx / out * distance,
                                y: snapshot.foot.y + dy / out * distance
                            ),
                            radius: radius,
                            colors: colors
                        )
                    }
                }
            }
            drawBall(context: &context, centre: points[0], radius: radius, colors: colors)
        }
        wire.addLine(to: points[0])
        for point in points.dropFirst() {
            wire.addLine(to: point)
        }
        context.stroke(wire, with: .color(color(colors.wire)), lineWidth: PullCordThemeTokens.Chain.wire * scale)
        for point in points.dropFirst() {
            drawBall(context: &context, centre: point, radius: radius, colors: colors)
        }

        let last = points.count - 1
        let pullDX = points[last].x - points[last - 1].x
        let pullDY = points[last].y - points[last - 1].y
        var pull = context
        pull.translateBy(x: points[last].x, y: points[last].y)
        pull.rotate(by: .radians(atan2(-pullDX, pullDY)))
        pull.scaleBy(x: scale, y: scale)
        pull.translateBy(x: 0, y: PullCordThemeTokens.Chain.ball)
        let shape = pullPath()
        pull.fill(
            shape,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: color(colors.rim), location: 0),
                    .init(color: color(colors.body), location: 0.22),
                    .init(color: color(colors.specular).opacity(colors.specularAlpha), location: 0.36),
                    .init(color: color(colors.body), location: 0.62),
                    .init(color: color(colors.rim), location: 1)
                ]),
                startPoint: CGPoint(x: -PullCordThemeTokens.Chain.pullWidth / 2, y: 0),
                endPoint: CGPoint(x: PullCordThemeTokens.Chain.pullWidth / 2, y: 0)
            )
        )
        var specular = Path()
        specular.move(to: CGPoint(x: -1.2, y: 5))
        specular.addCurve(
            to: CGPoint(x: -3, y: 18),
            control1: CGPoint(x: -1.8, y: 9),
            control2: CGPoint(x: -3, y: 13)
        )
        pull.stroke(
            specular,
            with: .color(color(colors.specular).opacity(colors.specularAlpha)),
            style: StrokeStyle(lineWidth: 0.9, lineCap: .round)
        )
    }

    private static func drawBall(
        context: inout GraphicsContext,
        centre: CGPoint,
        radius: Double,
        colors: PullCordThemeTokens.Chain.Metal
    ) {
        context.fill(
            Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: 2 * radius, height: 2 * radius)),
            with: .color(color(colors.rim))
        )
        let bodyRadius = radius * 0.74
        context.fill(
            Path(ellipseIn: CGRect(
                x: centre.x - radius * 0.16 - bodyRadius,
                y: centre.y - radius * 0.18 - bodyRadius,
                width: 2 * bodyRadius,
                height: 2 * bodyRadius
            )),
            with: .color(color(colors.body))
        )
        let specularRadius = radius * 0.28
        context.fill(
            Path(ellipseIn: CGRect(
                x: centre.x - radius * 0.36 - specularRadius,
                y: centre.y - radius * 0.4 - specularRadius,
                width: 2 * specularRadius,
                height: 2 * specularRadius
            )),
            with: .color(color(colors.specular).opacity(colors.specularAlpha))
        )
    }

    private static func domePath() -> Path {
        let lamp = PullCordThemeTokens.Lamp.self
        var path = Path()
        path.move(to: CGPoint(x: -8, y: lamp.capHeight))
        path.addCurve(
            to: CGPoint(x: -lamp.rimHalf, y: lamp.rimY),
            control1: CGPoint(x: -11, y: 28),
            control2: CGPoint(x: -lamp.rimHalf * 0.75, y: lamp.rimY * 0.52)
        )
        path.addLine(to: CGPoint(x: lamp.rimHalf, y: lamp.rimY))
        path.addCurve(
            to: CGPoint(x: 8, y: lamp.capHeight),
            control1: CGPoint(x: lamp.rimHalf * 0.75, y: lamp.rimY * 0.52),
            control2: CGPoint(x: 11, y: 28)
        )
        path.closeSubpath()
        return path
    }

    private static func lipPath() -> Path {
        let lamp = PullCordThemeTokens.Lamp.self
        var path = Path()
        path.addArc(
            center: CGPoint(x: 0, y: lamp.rimY),
            radius: lamp.rimHalf - lamp.lip / 2,
            startAngle: .degrees(0),
            endAngle: .degrees(180),
            clockwise: false,
            transform: CGAffineTransform(scaleX: 1, y: lamp.rimRY / lamp.rimHalf)
                .translatedBy(x: 0, y: lamp.rimY * (lamp.rimHalf / lamp.rimRY - 1))
        )
        return path
    }

    private static func pullPath() -> Path {
        let chain = PullCordThemeTokens.Chain.self
        var path = Path()
        path.move(to: CGPoint(x: -1.6, y: 0))
        path.addLine(to: CGPoint(x: 1.6, y: 0))
        path.addLine(to: CGPoint(x: 1.8, y: 4))
        path.addCurve(
            to: CGPoint(x: chain.pullWidth / 2, y: 19),
            control1: CGPoint(x: 2.6, y: 8),
            control2: CGPoint(x: chain.pullWidth / 2, y: 13)
        )
        path.addQuadCurve(to: CGPoint(x: 0, y: chain.pullHeight), control: CGPoint(x: chain.pullWidth / 2, y: chain.pullHeight))
        path.addQuadCurve(to: CGPoint(x: -chain.pullWidth / 2, y: 19), control: CGPoint(x: -chain.pullWidth / 2, y: chain.pullHeight))
        path.addCurve(
            to: CGPoint(x: -1.8, y: 4),
            control1: CGPoint(x: -chain.pullWidth / 2, y: 13),
            control2: CGPoint(x: -2.6, y: 8)
        )
        path.closeSubpath()
        return path
    }

    private static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }

    private static func color(_ value: SIMD3<Float>) -> Color {
        Color(red: Double(value.x), green: Double(value.y), blue: Double(value.z))
    }

    private static func mixedColor(_ source: SIMD3<Float>, color hex: UInt32, amount: Double) -> Color {
        let target = SIMD3<Float>(
            Float((hex >> 16) & 0xFF) / 255,
            Float((hex >> 8) & 0xFF) / 255,
            Float(hex & 0xFF) / 255
        )
        return color(simdMix(source, target, amount))
    }

    private static func simdMix(_ first: SIMD3<Float>, _ second: SIMD3<Float>, _ amount: Double) -> SIMD3<Float> {
        first + (second - first) * Float(amount)
    }
}
