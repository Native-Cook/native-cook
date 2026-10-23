import CoreText
import UIKit

extension ChromePrizeWheelStage {
    private func color(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(
            red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255, alpha: alpha)
    }

    private func radial(
        _ context: CGContext, x: Double, y: Double, radius: Double,
        colors: [UIColor], stops: [CGFloat], alpha: Double = 1
    ) {
        context.saveGState()
        context.setAlpha(max(0, min(1, alpha)))
        let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: colors.map(\.cgColor) as CFArray, locations: stops)!
        context.drawRadialGradient(
            gradient, startCenter: CGPoint(x: x, y: y), startRadius: 0,
            endCenter: CGPoint(x: x, y: y), endRadius: radius, options: [])
        context.restoreGState()
    }

    private func sprite(
        _ context: CGContext, image: UIImage, x: Double, y: Double, size: Double,
        angle: Double = 0, sx: Double = 1, sy: Double = 1, opacity: Double = 1,
        shadow: Double = 0
    ) {
        guard opacity > 0.0001, size > 0 else { return }
        context.saveGState()
        context.setAlpha(1)
        context.translateBy(x: x, y: y)
        context.scaleBy(x: sx, y: sy)
        context.rotate(by: angle)
        if shadow > 0 {
            context.setShadow(
                offset: CGSize(width: 6 * layout.k, height: 16 * layout.k),
                blur: shadow, color: UIColor.black.withAlphaComponent(0.3).cgColor)
        }
        image.draw(
            in: CGRect(x: -size / 2, y: -size / 2, width: size, height: size), blendMode: .normal,
            alpha: min(1, opacity))
        context.restoreGState()
    }

    func drawBackground(_ context: CGContext) {
        let l = layout
        let v = scene.values
        let backdrop = l.backdrop
        assets.image("wheel-backdrop.jpg").draw(
            in: CGRect(x: backdrop.x, y: backdrop.y, width: backdrop.width, height: backdrop.height))
        for i in 0..<32 {
            let p = Geometry.skyPose(l, Double(i), clock, v.swirl, v.burst, v.tiltX, v.tiltY, config.reduced)
            sprite(
                context, image: assets.stars[Int(p.tile)], x: p.x, y: p.y, size: p.size, angle: p.angle,
                opacity: p.opacity)
        }
        context.saveGState()
        context.translateBy(x: l.cx, y: l.cy)
        context.scaleBy(x: v.wheelScale, y: v.wheelScale)
        context.translateBy(x: -l.cx, y: -l.cy)
        radial(
            context, x: l.cx, y: l.cy, radius: l.radius * 1.35,
            colors: [color(0x2438E0), color(0x2438E0), color(0x2438E0, 0)], stops: [0, 0.6, 1],
            alpha: 0.4 * (1 - 0.7 * v.dim) * v.wheelOpacity)
        radial(
            context, x: l.cx + l.radius * 0.045, y: l.cy + l.radius * 0.075,
            radius: l.radius * 1.14,
            colors: [
                color(0x000006, 0.85), color(0x000006, 0.85), color(0x000006, 0.65),
                color(0x000006, 0.32), color(0x000006, 0.08), color(0x000006, 0),
            ],
            stops: [0, 0.763, 0.816, 0.868, 0.93, 1], alpha: v.wheelOpacity)
        context.restoreGState()
    }

    func drawForeground(_ context: CGContext) {
        drawPointer(context)
        drawParticles(context, near: false)
        drawHero(context)
        drawParticles(context, near: true)
        drawCopy(context)
        let v = scene.values
        if v.touchOn > 0.001 {
            context.setFillColor(UIColor.white.withAlphaComponent(0.14 * v.touchOn).cgColor)
            let circle = CGRect(x: v.touchX - 20, y: v.touchY - 20, width: 40, height: 40)
            context.fillEllipse(in: circle)
            context.setStrokeColor(UIColor.white.withAlphaComponent(0.45 * v.touchOn).cgColor)
            context.setLineWidth(1)
            context.strokeEllipse(in: circle)
        }
    }

    private func drawPointer(_ context: CGContext) {
        let l = layout
        let v = scene.values
        context.saveGState()
        context.setAlpha(1)
        context.translateBy(x: l.cx, y: l.cy)
        context.scaleBy(x: v.wheelScale, y: v.wheelScale)
        context.translateBy(x: l.pointerX - l.cx, y: l.pointerY - l.cy)
        context.saveGState()
        context.translateBy(x: 3 * l.k, y: 4 * l.k)
        context.rotate(by: v.swing)
        let rect = CGRect(x: -20 * l.k, y: -36 * l.k, width: 88 * l.k, height: 72 * l.k)
        assets.image("wheel-pointer-shadow.png").draw(
            in: rect, blendMode: .normal, alpha: (1 - 0.6 * v.dim) * v.wheelOpacity)
        context.restoreGState()
        context.rotate(by: v.swing)
        assets.image("wheel-pointer.png").draw(in: rect, blendMode: .normal, alpha: (1 - 0.6 * v.dim) * v.wheelOpacity)
        context.setBlendMode(.plusLighter)
        let light = 0.025 + (config.reduced ? 0 : 0.014 * sin(clock * 1.5)) + v.tick * 0.65
        radial(
            context, x: 20 * l.k, y: 0, radius: 25 * l.k,
            colors: [color(0xFFD5FC), color(0xF834DA, 0.53), color(0xBD31FF, 0)], stops: [0, 0.5, 1], alpha: light * (1 - 0.6 * v.dim) * v.wheelOpacity)
        context.translateBy(x: 40 * l.k, y: 0)
        context.rotate(by: v.strikes * 2.399963)
        context.setFillColor(color(0xFFF2FF, v.tick * 0.9 * (1 - 0.6 * v.dim) * v.wheelOpacity).cgColor)
        context.fillEllipse(in: CGRect(x: -1.68 * l.k, y: -12 * l.k, width: 3.36 * l.k, height: 24 * l.k))
        context.fillEllipse(in: CGRect(x: -12 * l.k, y: -1.68 * l.k, width: 24 * l.k, height: 3.36 * l.k))
        context.restoreGState()
    }

    private func drawParticles(_ context: CGContext, near: Bool) {
        guard !config.reduced else { return }
        let v = scene.values
        for i in 0..<88 {
            let p = Geometry.particlePose(layout, Double(i), v.burst, v.burstPower)
            guard p.near == near else { continue }
            let blur = near ? max(0, v.burst - 0.4) * 1.8 * layout.k : 0
            sprite(
                context, image: assets.star(Int(p.tile), blur: blur, size: p.size), x: p.x, y: p.y, size: p.size,
                angle: p.angle, opacity: p.opacity)
        }
    }

    private func drawHero(_ context: CGContext) {
        let v = scene.values
        guard v.winner >= 0 else { return }
        let input = Geometry.PoseInput(
            winner: v.winner, theta: v.theta, lift: v.lift, wheelScale: v.wheelScale,
            swayA: v.swayA, swayB: v.swayB, tiltX: v.tiltX, tiltY: v.tiltY,
            hero: v.hero, heroSquash: v.heroSquash, heroX: v.heroX, heroY: v.heroY,
            heroTilt: v.heroTilt, collect: v.collect, time: clock, reduced: config.reduced)
        let p = Geometry.prizePose(layout, input)
        let printed = Geometry.printedPrizeOpacity(v.phase, v.heroSquash, v.winnerPrize)
        let opacity = (1 - printed) * p.opacity
        let prize = Motion.PRIZES[Int(v.winner)]
        sprite(
            context, image: assets.image("prize-\(prize.key).png"), x: p.x, y: p.y + p.baseShift,
            size: p.side, angle: p.angle, sx: p.sx, sy: p.sy, opacity: opacity,
            shadow: (2 + 9 * v.hero) * layout.k)
        // A premultiplied, alpha-clipped light sweep tracks the source's hero shader.
        if opacity > 0.001 && !config.reduced {
            context.saveGState()
            context.translateBy(x: p.x, y: p.y + p.baseShift)
            context.scaleBy(x: p.sx, y: p.sy)
            context.rotate(by: p.angle)
            let rect = CGRect(x: -p.side / 2, y: -p.side / 2, width: p.side, height: p.side)
            context.translateBy(x: 0, y: 0)
            context.scaleBy(x: 1, y: -1)
            context.clip(to: rect, mask: assets.image("prize-\(prize.key).png").cgImage!)
            context.scaleBy(x: 1, y: -1)
            let sweep = (clock / 1.8 - floor(clock / 1.8)) * 2.1 - 1
            context.setBlendMode(.plusLighter)
            context.setAlpha(opacity * max(0, v.hero) * 0.24)
            let gradient = CGGradient(
                colorsSpace: CGColorSpaceCreateDeviceRGB(),
                colors: [color(0xCCD9FF, 0).cgColor, color(0xCCD9FF).cgColor, color(0xCCD9FF, 0).cgColor] as CFArray,
                locations: [0, 0.5, 1])!
            context.drawLinearGradient(
                gradient, start: CGPoint(x: (sweep - 0.12) * p.side, y: -0.034 * p.side),
                end: CGPoint(x: (sweep + 0.12) * p.side, y: 0.034 * p.side), options: [])
            context.restoreGState()
        }
        let b = Geometry.badgePose(layout, input, v.badge)
        sprite(
            context, image: assets.badges[Int(v.winner)], x: b.x, y: b.y, size: b.size, angle: b.angle,
            sx: b.sx, sy: b.sy, opacity: (1 - printed) * b.opacity * (1 - v.collect))
    }

    private func text(
        _ context: CGContext, _ value: String, top: Double, font: UIFont, line: Double,
        color: UIColor, opacity: Double = 1
    ) {
        guard opacity > 0 else { return }
        context.saveGState()
        context.setAlpha(min(1, opacity))
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = .center
        let attributes: [NSAttributedString.Key: Any] = [
            .font: font, .foregroundColor: color, .paragraphStyle: paragraph,
        ]
        (value as NSString).draw(
            in: CGRect(
                x: 24, y: top + (line - font.lineHeight) / 2,
                width: layout.width - 48, height: max(line, font.lineHeight) + 4), withAttributes: attributes)
        context.restoreGState()
    }

    private func drawCopy(_ context: CGContext) {
        let l = layout
        let v = scene.values
        text(
            context, "Daily Spin", top: l.titleTop, font: assets.font(l.titleSize), line: l.titleLine,
            color: color(0xE4E6FF))
        let body = UIFont.systemFont(ofSize: l.bodySize)
        let subtle = color(0xCDD1FF, 0.72)
        text(
            context, "Swipe the wheel. Every slice wins.", top: l.subtitleTop - 8 * v.copySwap,
            font: body, line: l.bodyLine, color: subtle, opacity: 1 - v.copySwap)
        text(
            context, v.winner == 1 ? "Jackpot. The whole chest is yours." : "Nice spin. This one is yours.",
            top: l.subtitleTop + 8 * (1 - v.copySwap), font: body, line: l.bodyLine, color: subtle, opacity: v.copySwap)
        if v.winner >= 0 {
            text(
                context, Motion.PRIZES[Int(v.winner)].label, top: l.labelTop + 8 * (1 - v.caption),
                font: body, line: l.bodyLine, color: subtle, opacity: v.caption)
            drawNumeral(context)
        }
        let p = Geometry.buttonPose(l, v.button, v.buttonPress, v.hintNudge)
        context.saveGState()
        context.translateBy(x: l.cx + p.x, y: l.buttonY)
        context.scaleBy(x: p.scale, y: p.scale)
        let rect = CGRect(x: -p.width / 2, y: -p.height / 2, width: p.width, height: p.height)
        let path = UIBezierPath(roundedRect: rect, cornerRadius: p.height / 2)
        if v.button > 0.001 {
            context.setShadow(
                offset: CGSize(width: 0, height: 6), blur: 18, color: color(0x6F7DFF, 0.42 * v.button).cgColor)
            context.setFillColor(color(0x6F7DFF, v.button).cgColor)
            context.addPath(path.cgPath)
            context.fillPath()
            context.setShadow(offset: .zero, blur: 0)
        }
        context.addPath(path.cgPath)
        context.clip()
        context.setFillColor(color(0x121654, 0.72).cgColor)
        context.fill(rect)
        context.setStrokeColor(color(0xBEC6FF, 0.28 * (1 - v.button)).cgColor)
        context.setLineWidth(2)
        context.addPath(path.cgPath)
        context.strokePath()
        context.saveGState()
        context.setAlpha(v.button)
        let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: [0xEEF0FF, 0xFFFFFF, 0xC3CBFF, 0xA9B4FF].map { color(UInt32($0)).cgColor } as CFArray,
            locations: [0, 0.35, 0.53, 1])!
        context.drawLinearGradient(
            gradient, start: CGPoint(x: 0, y: rect.minY), end: CGPoint(x: 0, y: rect.maxY), options: [])
        context.restoreGState()
        context.setFillColor(color(0xF4F5FF, 0.42).cgColor)
        context.fill(CGRect(x: rect.minX + 20, y: rect.minY, width: p.width - 40, height: 1))
        context.saveGState()
        context.setAlpha(v.hint * (1 - v.button))
        let hintFont = UIFont.systemFont(ofSize: 15, weight: .semibold)
        let hintWidth = ("Swipe to spin" as NSString).size(withAttributes: [.font: hintFont]).width
        let left = -(hintWidth + 32) / 2
        sprite(
            context, image: assets.image("wheel-swipe.png"), x: left + 12, y: -6 * v.button,
            size: 24, angle: config.reduced ? 0 : 0.12 * sin(clock * 2.3), opacity: v.hint * (1 - v.button))
        ("Swipe to spin" as NSString).draw(
            at: CGPoint(x: left + 32, y: -hintFont.lineHeight / 2 - 6 * v.button),
            withAttributes: [.font: hintFont, .foregroundColor: color(0xE4E6FF)])
        context.restoreGState()
        context.setAlpha(v.button)
        let collectFont = UIFont.systemFont(ofSize: 17, weight: .semibold)
        let width = ("Collect" as NSString).size(withAttributes: [.font: collectFont]).width
        ("Collect" as NSString).draw(
            at: CGPoint(x: -width / 2, y: -collectFont.lineHeight / 2 + 6 * (1 - v.button)),
            withAttributes: [.font: collectFont, .foregroundColor: color(0x0B0E3F)])
        context.restoreGState()
    }

    private func drawNumeral(_ context: CGContext) {
        let l = layout
        let v = scene.values
        context.saveGState()
        context.setAlpha(v.caption)
        let font = assets.font(l.numeralSize)
        let line = CTLineCreateWithAttributedString(
            NSAttributedString(string: "×\(Int(max(0, v.count).rounded()))", attributes: [.font: font]))
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        let baseline = l.captionTop + (l.numeralLine - l.numeralSize) / 2 + font.ascender
        context.translateBy(x: l.cx - width / 2, y: baseline)
        context.scaleBy(x: 1, y: -1)
        context.textMatrix = .identity
        context.textPosition = .zero
        context.setTextDrawingMode(.stroke)
        context.setLineWidth(0.65 * l.fontScale)
        context.setStrokeColor(color(0xF4F5FF).cgColor)
        CTLineDraw(line, context)
        context.setTextDrawingMode(.clip)
        context.textPosition = .zero
        CTLineDraw(line, context)
        let gradient = CGGradient(
            colorsSpace: CGColorSpaceCreateDeviceRGB(),
            colors: [0xF4F5FF, 0xAEB5EC, 0xF4F5FF, 0x3A4190, 0xAEB5EC, 0xF4F5FF].map { color(UInt32($0)).cgColor }
                as CFArray,
            locations: [0, 0.42, 0.5, 0.54, 0.78, 1])!
        context.drawLinearGradient(
            gradient, start: CGPoint(x: 0, y: baseline - l.captionTop - 8 * l.fontScale),
            end: CGPoint(x: 0, y: baseline - l.captionTop - 56 * l.fontScale),
            options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        context.restoreGState()
    }
}
