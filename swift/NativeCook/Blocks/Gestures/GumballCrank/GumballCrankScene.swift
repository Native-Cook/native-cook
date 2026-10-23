import UIKit

final class GumballCrankRenderer {
    private let back: UIImage
    private let front: UIImage
    private let ballSprites: [UIImage]

    init() {
        GumballCrankFonts.register()
        ballSprites = GumballCrankColor.balls.map(Self.makeBallSprite)
        back = Self.renderStatic(Self.drawStaticBack)
        front = Self.renderStatic(Self.drawStaticFront)
    }

    func draw(in context: CGContext, model: GumballCrankSimulation, layout: GumballCrankLayout) {
        context.saveGState()
        context.translateBy(x: layout.origin.x, y: layout.origin.y)
        context.scaleBy(x: layout.scale, y: layout.scale)
        back.draw(in: CGRect(origin: .zero, size: GumballCrankMetrics.stage))

        context.saveGState()
        context.addEllipse(in: CGRect(x: 80, y: 133, width: 242, height: 242))
        context.clip()
        for i in model.ballX.indices {
            let r = model.ballR[i]
            ballSprites[model.ballColor[i]].draw(in: CGRect(x: model.ballX[i] - r, y: model.ballY[i] - r, width: r * 2, height: r * 2))
        }
        context.restoreGState()

        front.draw(in: CGRect(origin: .zero, size: GumballCrankMetrics.stage))
        Self.drawHandle(context, model)
        Self.drawCapsule(context, model)
        Self.drawFlap(context, model)
        if model.scrim > 0.005 {
            context.setFillColor(GumballCrankColor.ink.withAlphaComponent(model.scrim * 0.42).cgColor)
            context.fill(CGRect(x: -layout.origin.x / layout.scale, y: -layout.origin.y / layout.scale, width: layout.width / layout.scale, height: layout.height / layout.scale))
            Self.drawCapsule(context, model)
        }
        Self.drawCard(context, model)
        Self.drawFlyingCoins(context, model)
        Self.drawWallet(context, model)
        Self.drawHint(context, model)
        if model.ghostAlpha > 0.01 {
            let radius = 22 * (1 - 0.12 * model.ghostPress)
            Self.circle(context, x: model.ghostX, y: model.ghostY, r: radius, color: GumballCrankColor.ink.withAlphaComponent(0.14 * model.ghostAlpha))
            Self.strokeCircle(context, x: model.ghostX, y: model.ghostY, r: radius - 0.5, color: GumballCrankColor.ink.withAlphaComponent(0.36 * model.ghostAlpha), width: 1)
        }
        context.restoreGState()
    }

    private static func renderStatic(_ drawing: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = false
        return UIGraphicsImageRenderer(size: GumballCrankMetrics.stage, format: format).image { drawing($0.cgContext) }
    }

    private static func drawStaticBack(_ context: CGContext) {
        linearGradient(context, rect: CGRect(x: -200, y: -200, width: 802, height: 814), colors: [GumballCrankColor.wallTop, GumballCrankColor.wallBottom], locations: [0, 1], start: CGPoint(x: 0, y: 0), end: CGPoint(x: 0, y: 614))
        radialGradient(context, rect: CGRect(x: -200, y: -200, width: 802, height: 1014), colors: [UIColor(rgb: 0xFFFAEE, alpha: 0.75), UIColor(rgb: 0xFFFAEE, alpha: 0)], locations: [0, 1], center: CGPoint(x: 60, y: 120), radius: 420)
        linearGradient(context, rect: CGRect(x: -200, y: 614, width: 802, height: 486), colors: [GumballCrankColor.tableEdge, GumballCrankColor.tableTop, GumballCrankColor.tableFront], locations: [0, 0.18, 1], start: CGPoint(x: 0, y: 614), end: CGPoint(x: 0, y: 778))
        for k in 0 ..< 26 {
            let alpha = 0.05 + 0.06 * CGFloat((k * 37) % 5) / 5
            let y = 622 + CGFloat(k) * 6.3 + sin(CGFloat(k) * 1.7) * 2
            let path = UIBezierPath(); path.move(to: CGPoint(x: -20, y: y)); var x: CGFloat = 0
            while x <= 442 { path.addQuadCurve(to: CGPoint(x: x + 40, y: y + sin(CGFloat(k) * 0.6 + x * 0.02) * 1.2), controlPoint: CGPoint(x: x + 20, y: y + sin(CGFloat(k) * 0.9 + x * 0.013) * 2.4)); x += 40 }
            context.setStrokeColor(GumballCrankColor.tableDeep.withAlphaComponent(alpha).cgColor); context.setLineWidth(0.8); context.addPath(path.cgPath); context.strokePath()
        }
        shadowShape(context, color: UIColor(rgb: 0x5B3A1C, alpha: 0.22), blur: 3) { context.fill(CGRect(x: -200, y: 612, width: 802, height: 10)) }
        shadowShape(context, color: UIColor(rgb: 0x3D2410, alpha: 0.38), blur: 9) { context.fillEllipse(in: CGRect(x: 69, y: 604, width: 290, height: 30)) }
        drawBody(context)
        radialGradient(context, path: UIBezierPath(ovalIn: CGRect(x: 79, y: 132, width: 244, height: 244)).cgPath, colors: [UIColor.white.withAlphaComponent(0.10), UIColor(rgb: 0x786450, alpha: 0.10), UIColor(rgb: 0x784623, alpha: 0.28)], locations: [0, 0.75, 1], center: CGPoint(x: 181, y: 224), radius: 128)
        drawText(context, GumballCrankCopy.title, x: 16, baseline: 40, font: GumballCrankFonts.serif(34), color: GumballCrankColor.ink)
        drawText(context, GumballCrankCopy.subtitle, x: 16, baseline: 64, font: GumballCrankFonts.regular(15), color: GumballCrankColor.mute)
    }

    private static func drawBody(_ context: CGContext) {
        let body = UIBezierPath(); body.move(to: CGPoint(x: 115, y: 392)); body.addLine(to: CGPoint(x: 287, y: 392)); body.addCurve(to: CGPoint(x: 315, y: 588), controlPoint1: CGPoint(x: 293, y: 472), controlPoint2: CGPoint(x: 315, y: 530)); body.addQuadCurve(to: CGPoint(x: 303, y: 600), controlPoint: CGPoint(x: 315, y: 600)); body.addLine(to: CGPoint(x: 99, y: 600)); body.addQuadCurve(to: CGPoint(x: 87, y: 588), controlPoint: CGPoint(x: 87, y: 600)); body.addCurve(to: CGPoint(x: 115, y: 392), controlPoint1: CGPoint(x: 87, y: 530), controlPoint2: CGPoint(x: 109, y: 472)); body.close()
        linearGradient(context, path: body.cgPath, colors: [GumballCrankColor.enamelDeep, GumballCrankColor.enamel, GumballCrankColor.enamelLight, GumballCrankColor.enamel, UIColor(rgb: 0x9E1C25), GumballCrankColor.enamelDeep], locations: [0, 0.1, 0.27, 0.48, 0.8, 1], start: CGPoint(x: 87, y: 0), end: CGPoint(x: 315, y: 0))
        context.saveGState(); context.addPath(body.cgPath); context.clip()
        context.setShadow(offset: .zero, blur: 7, color: UIColor.white.withAlphaComponent(0.28).cgColor); context.setFillColor(UIColor.white.withAlphaComponent(0.28).cgColor); context.fill(CGRect(x: 139, y: 386, width: 22, height: 220)); context.setShadow(offset: .zero, blur: 0)
        linearGradient(context, rect: CGRect(x: 87, y: 560, width: 228, height: 40), colors: [UIColor(rgb: 0x280004, alpha: 0), UIColor(rgb: 0x280004, alpha: 0.35)], locations: [0, 1], start: CGPoint(x: 0, y: 560), end: CGPoint(x: 0, y: 600)); context.restoreGState()
        radialGradient(context, path: UIBezierPath(ovalIn: CGRect(x: 159, y: 420, width: 84, height: 84)).cgPath, colors: [GumballCrankColor.chromeHi, GumballCrankColor.chromeMid, GumballCrankColor.chromeLow, GumballCrankColor.chromeDeep], locations: [0, 0.35, 0.75, 1], center: CGPoint(x: 184, y: 441), radius: 76)
        roundRect(context, CGRect(x: 198, y: 430, width: 6, height: 18), radius: 3, color: GumballCrankColor.slot)
        linearGradient(context, path: UIBezierPath(roundedRect: CGRect(x: 146, y: 527, width: 110, height: 60), cornerRadius: 23).cgPath, colors: [GumballCrankColor.chromeHi, GumballCrankColor.chromeMid, GumballCrankColor.chromeDeep], locations: [0, 0.5, 1], start: CGPoint(x: 0, y: 527), end: CGPoint(x: 0, y: 587))
        linearGradient(context, path: UIBezierPath(roundedRect: CGRect(x: 151, y: 532, width: 100, height: 50), cornerRadius: 18).cgPath, colors: [UIColor(rgb: 0x140A08), UIColor(rgb: 0x2B1612), UIColor(rgb: 0x3A1E17)], locations: [0, 0.5, 1], start: CGPoint(x: 0, y: 532), end: CGPoint(x: 0, y: 582))
        chrome(context, path: UIBezierPath(roundedRect: CGRect(x: 79, y: 600, width: 244, height: 14), cornerRadius: 6).cgPath, start: 79, end: 323)
    }

    private static func drawStaticFront(_ context: CGContext) {
        context.saveGState(); context.setStrokeColor(UIColor.white.withAlphaComponent(0.5).cgColor); context.setLineWidth(16); context.setLineCap(.round); context.setShadow(offset: .zero, blur: 5, color: UIColor.white.withAlphaComponent(0.5).cgColor); context.addArc(center: CGPoint(x: 201, y: 254), radius: 108, startAngle: .pi * 1.09, endAngle: .pi * 1.43, clockwise: false); context.strokePath(); context.restoreGState()
        context.saveGState(); context.setStrokeColor(UIColor.white.withAlphaComponent(0.85).cgColor); context.setLineWidth(3); context.setLineCap(.round); context.addArc(center: CGPoint(x: 201, y: 254), radius: 112, startAngle: .pi * 1.12, endAngle: .pi * 1.34, clockwise: false); context.strokePath(); context.restoreGState()
        strokeCircle(context, x: 201, y: 254, r: 121.2, color: UIColor.white.withAlphaComponent(0.55), width: 1.6)
        chrome(context, path: UIBezierPath(roundedRect: CGRect(x: 103, y: 370, width: 196, height: 22), cornerRadius: 8).cgPath, start: 103, end: 299)
        let lid = UIBezierPath(); lid.move(to: CGPoint(x: 126, y: 144)); lid.addCurve(to: CGPoint(x: 201, y: 110), controlPoint1: CGPoint(x: 126, y: 114), controlPoint2: CGPoint(x: 163, y: 110)); lid.addCurve(to: CGPoint(x: 276, y: 144), controlPoint1: CGPoint(x: 239, y: 110), controlPoint2: CGPoint(x: 276, y: 114)); lid.close()
        chrome(context, path: lid.cgPath, start: 126, end: 276)
        chrome(context, path: UIBezierPath(roundedRect: CGRect(x: 122, y: 138, width: 158, height: 9), cornerRadius: 4.5).cgPath, start: 122, end: 280)
        radialGradient(context, path: UIBezierPath(ovalIn: CGRect(x: 189, y: 89, width: 24, height: 24)).cgPath, colors: [GumballCrankColor.chromeHi, GumballCrankColor.chromeMid, GumballCrankColor.chromeDeep], locations: [0, 0.4, 1], center: CGPoint(x: 197, y: 91), radius: 19)
        circle(context, x: 197, y: 97, r: 3, color: UIColor.white.withAlphaComponent(0.9))
    }

    private static func drawHandle(_ context: CGContext, _ model: GumballCrankSimulation) {
        let a = model.angle + model.wobble * 0.06, gx = 201 + 34 * sin(a), gy = 462 - 34 * cos(a)
        context.saveGState(); context.setLineCap(.round); context.setLineWidth(9); context.setStrokeColor(UIColor(rgb: 0x1A0A06, alpha: 0.32).cgColor); context.setShadow(offset: .zero, blur: 2.5, color: UIColor(rgb: 0x1A0A06, alpha: 0.32).cgColor); context.move(to: CGPoint(x: 204, y: 466)); context.addLine(to: CGPoint(x: gx + 3, y: gy + 4)); context.strokePath(); context.restoreGState()
        context.setLineCap(.round); context.setLineWidth(9); context.setStrokeColor(GumballCrankColor.chromeMid.cgColor); context.move(to: CGPoint(x: 201, y: 462)); context.addLine(to: CGPoint(x: gx, y: gy)); context.strokePath()
        context.setLineWidth(2.2); context.setStrokeColor(UIColor.white.withAlphaComponent(0.85).cgColor); context.move(to: CGPoint(x: 201 - cos(a) * 2.2, y: 462 - sin(a) * 2.2)); context.addLine(to: CGPoint(x: gx - cos(a) * 2.2, y: gy - sin(a) * 2.2)); context.strokePath()
        radialGradient(context, path: UIBezierPath(ovalIn: CGRect(x: 188, y: 449, width: 26, height: 26)).cgPath, colors: [GumballCrankColor.chromeHi, GumballCrankColor.chromeMid, GumballCrankColor.chromeDeep], locations: [0, 0.4, 1], center: CGPoint(x: 197, y: 457), radius: 23)
        circle(context, x: 201, y: 462, r: 3.5, color: GumballCrankColor.chromeDeep)
        radialGradient(context, path: UIBezierPath(ovalIn: CGRect(x: gx - 10, y: gy - 10, width: 20, height: 20)).cgPath, colors: [UIColor(rgb: 0xFF8A84), GumballCrankColor.enamelLight, GumballCrankColor.enamel, GumballCrankColor.enamelDeep], locations: [0, 0.2, 0.55, 1], center: CGPoint(x: gx - 4, y: gy - 4.5), radius: 17)
        circle(context, x: gx - 3.6, y: gy - 4, r: 2.2, color: UIColor.white.withAlphaComponent(0.85))
    }

    private static func drawFlap(_ context: CGContext, _ model: GumballCrankSimulation) {
        var open = 0.0
        if model.phase == .dispensing, !model.reduced { open = model.phaseTime < 0.5 ? gumballCrankEase(gumballCrankUnitSpan(model.phaseTime, 0.22, 0.5)) : 1 - gumballCrankEase(gumballCrankUnitSpan(model.phaseTime, 0.75, 1.15)) }
        let height = max(2, 50 * cos(open * 1.25) - 4)
        linearGradient(context, path: UIBezierPath(roundedRect: CGRect(x: 154, y: 534, width: 94, height: height), cornerRadius: min(15, height / 2)).cgPath, colors: [GumballCrankColor.chromeHi, GumballCrankColor.chromeMid, GumballCrankColor.chromeLow], locations: [0, 0.5, 1], start: CGPoint(x: 0, y: 534), end: CGPoint(x: 0, y: 534 + height))
    }

    private static func drawCapsule(_ context: CGContext, _ model: GumballCrankSimulation) {
        let pose = model.capsulePose(); guard pose.alpha > 0.01 else { return }
        let radius = 30 * pose.scale, alpha = pose.alpha
        if pose.y > 600 { shadowShape(context, color: UIColor(rgb: 0x3D2410, alpha: 0.4 * alpha), blur: 4) { context.fillEllipse(in: CGRect(x: pose.x - radius * 1.1 + 4, y: pose.y + radius * 0.62, width: radius * 2.4, height: radius * 0.55)) } }
        context.saveGState(); context.translateBy(x: pose.x, y: pose.y); context.rotate(by: pose.rotation)
        let lower = halfCircle(radius: radius, top: false)
        radialGradient(context, path: lower.cgPath, colors: [GumballCrankColor.mintLight.withAlphaComponent(alpha), GumballCrankColor.mint.withAlphaComponent(alpha), GumballCrankColor.mintDeep.withAlphaComponent(alpha)], locations: [0, 0.45, 1], center: CGPoint(x: -radius * 0.4, y: radius * 0.1), radius: radius * 1.8)
        drawCoin(context, x: 0, y: -radius * 0.08, r: radius * 0.42, alpha: alpha)
        context.saveGState(); context.translateBy(x: -pose.split * 62 * pose.scale, y: (-sin(pose.split * .pi) * 34 + pose.split * 4) * pose.scale); context.rotate(by: -pose.split * 2.75)
        let upper = halfCircle(radius: radius, top: true)
        radialGradient(context, path: upper.cgPath, colors: [UIColor.white.withAlphaComponent(0.18 * alpha), UIColor.white.withAlphaComponent(0.1 * alpha), UIColor(rgb: 0x7A3C28, alpha: 0.28 * alpha)], locations: [0, 0.6, 1], center: CGPoint(x: -radius * 0.2, y: -radius * 0.35), radius: radius * 1.2)
        context.setStrokeColor(UIColor.white.withAlphaComponent(0.8 * alpha).cgColor); context.setLineWidth(1.2); context.addPath(upper.cgPath); context.strokePath(); context.restoreGState(); context.restoreGState()
    }

    private static func drawCard(_ context: CGContext, _ model: GumballCrankSimulation) {
        var progress = model.card, alpha = min(1, progress * 1.6), drop = 0.0
        if model.phase == .collecting { let u = gumballCrankUnitSpan(model.phaseTime, 0.35, 0.75); alpha = 1 - u; drop = model.reduced ? 0 : 50 * gumballCrankEase(u); progress = 1 }
        guard alpha > 0.01 else { return }
        let rise = model.reduced ? 0 : (1 - progress) * 120, scale = model.reduced ? 1 : 0.72 + 0.28 * progress
        context.saveGState(); context.translateBy(x: 201, y: 656 + rise + drop); context.scaleBy(x: scale, y: scale); context.translateBy(x: -201, y: -656)
        context.saveGState(); context.setShadow(offset: CGSize(width: 4, height: 10), blur: 18, color: UIColor(rgb: 0x1A0C04, alpha: 0.35 * alpha).cgColor); roundRect(context, CGRect(x: 42, y: 420, width: 318, height: 236), radius: 24, color: GumballCrankColor.card.withAlphaComponent(alpha)); context.restoreGState()
        linearGradient(context, path: UIBezierPath(roundedRect: CGRect(x: 42, y: 420, width: 318, height: 236), cornerRadius: 24).cgPath, colors: [GumballCrankColor.card.withAlphaComponent(alpha), UIColor(rgb: 0xFBF4E8, alpha: alpha)], locations: [0, 1], start: CGPoint(x: 0, y: 420), end: CGPoint(x: 0, y: 656))
        drawSpaced(context, GumballCrankCopy.eyebrow, center: 201, baseline: 454, font: GumballCrankFonts.semibold(11), color: GumballCrankColor.mute.withAlphaComponent(alpha), tracking: 1.6)
        let prizeFont = GumballCrankFonts.serif(64), unitFont = GumballCrankFonts.serif(26), prizeWidth = measure(GumballCrankCopy.prize, font: prizeFont), unitWidth = measure(GumballCrankCopy.unit, font: unitFont), total = 30 + 10 + prizeWidth + 8 + unitWidth, start = 201 - total / 2
        drawCoin(context, x: start + 15, y: 504, r: 15, alpha: alpha)
        drawText(context, GumballCrankCopy.prize, x: start + 40, baseline: 524, font: prizeFont, color: GumballCrankColor.ink.withAlphaComponent(alpha))
        drawText(context, GumballCrankCopy.unit, x: start + 40 + prizeWidth + 8, baseline: 524, font: unitFont, color: GumballCrankColor.ink.withAlphaComponent(alpha))
        drawText(context, GumballCrankCopy.cardSubtitle, x: 201, baseline: 554, font: GumballCrankFonts.regular(14), color: GumballCrankColor.mute.withAlphaComponent(alpha), alignment: .center)
        let buttonScale = 1 - 0.03 * model.press; context.saveGState(); context.translateBy(x: 201, y: 606); context.scaleBy(x: buttonScale, y: buttonScale); context.translateBy(x: -201, y: -606)
        roundRect(context, CGRect(x: 66, y: 580, width: 270, height: 52), radius: 26, color: GumballCrankColor.ink.withAlphaComponent(alpha * (1 - 0.12 * model.press)))
        drawText(context, GumballCrankCopy.collect, x: 201, baseline: 612, font: GumballCrankFonts.semibold(16), color: GumballCrankColor.card.withAlphaComponent(alpha), alignment: .center)
        context.restoreGState(); context.restoreGState()
    }

    private static func drawFlyingCoins(_ context: CGContext, _ model: GumballCrankSimulation) {
        guard model.phase == .collecting, !model.reduced else { return }
        for i in 0 ..< 8 {
            let u = gumballCrankUnitSpan(model.phaseTime, Double(i) * 0.05, Double(i) * 0.05 + 0.5)
            if u > 0, u < 1 { let eased = gumballCrankEase(u), x = 131 + (313 - 131) * eased + sin(u * .pi) * (i.isMultiple(of: 2) ? -10 : 18), y = 504 + (32 - 504) * eased - sin(u * .pi) * (70 + Double(i) * 6); drawCoin(context, x: x, y: y, r: 13 - 4 * eased, alpha: 1) }
        }
    }

    private static func drawWallet(_ context: CGContext, _ model: GumballCrankSimulation) {
        let scale = 1 + 0.06 * model.walletBump; context.saveGState(); context.translateBy(x: 340, y: 32); context.scaleBy(x: scale, y: scale); context.translateBy(x: -340, y: -32)
        context.saveGState(); context.setShadow(offset: CGSize(width: 0, height: 3), blur: 5, color: UIColor(rgb: 0x5B3A1C, alpha: 0.18).cgColor); roundRect(context, CGRect(x: 294, y: 12, width: 92, height: 40), radius: 20, color: GumballCrankColor.card.withAlphaComponent(0.86)); context.restoreGState()
        strokeRoundRect(context, CGRect(x: 294.5, y: 12.5, width: 91, height: 39), radius: 19.5, color: .white, width: 1); drawCoin(context, x: 315, y: 32, r: 9, alpha: 1)
        context.saveGState(); context.clip(to: CGRect(x: 294, y: 12, width: 92, height: 40))
        if model.walletMix < 1 { drawText(context, grouped(model.walletPrevious), x: 374, baseline: 37 - 10 * model.walletMix, font: GumballCrankFonts.semibold(15), color: GumballCrankColor.ink.withAlphaComponent(1 - model.walletMix), alignment: .right) }
        drawText(context, grouped(model.wallet), x: 374, baseline: 37 + 10 * (1 - model.walletMix), font: GumballCrankFonts.semibold(15), color: GumballCrankColor.ink.withAlphaComponent(model.walletMix), alignment: .right)
        context.restoreGState(); context.restoreGState()
    }

    private static func drawHint(_ context: CGContext, _ model: GumballCrankSimulation) {
        let copy = [GumballCrankCopy.hint, GumballCrankCopy.busy, GumballCrankCopy.done], under = 1 - model.scrim
        if model.hintMix < 1 { drawText(context, copy[model.hintFrom], x: 201, baseline: 752, font: GumballCrankFonts.medium(15), color: GumballCrankColor.ink.withAlphaComponent((1 - model.hintMix) * 0.85 * under), alignment: .center) }
        drawText(context, copy[model.hint], x: 201, baseline: 752, font: GumballCrankFonts.medium(15), color: GumballCrankColor.ink.withAlphaComponent(model.hintMix * 0.85 * under), alignment: .center)
        let show = model.hint == 0 ? model.hintMix : (model.hintFrom == 0 ? 1 - model.hintMix : 0)
        for i in 0 ..< 4 { circle(context, x: 201 + (Double(i) - 1.5) * 14, y: 722, r: 3.5, color: (i < model.detent ? GumballCrankColor.enamel : GumballCrankColor.ink).withAlphaComponent((i < model.detent ? 1 : 0.2) * show)) }
    }

    private static func makeBallSprite(_ color: UIColor) -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 1
        return UIGraphicsImageRenderer(size: CGSize(width: 96, height: 96), format: format).image { renderer in
            radialGradient(renderer.cgContext, path: UIBezierPath(ovalIn: CGRect(x: 0.5, y: 0.5, width: 95, height: 95)).cgPath, colors: [.white, color, color, .black], locations: [0, 0.22, 0.62, 1], center: CGPoint(x: 30, y: 26), radius: 84)
            renderer.cgContext.setFillColor(UIColor.white.withAlphaComponent(0.85).cgColor); renderer.cgContext.fillEllipse(in: CGRect(x: 22, y: 18, width: 18, height: 12))
        }
    }

    private static func drawCoin(_ context: CGContext, x: Double, y: Double, r: Double, alpha: Double) {
        guard alpha > 0.01 else { return }
        radialGradient(context, path: UIBezierPath(ovalIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)).cgPath, colors: [GumballCrankColor.coinLight.withAlphaComponent(alpha), GumballCrankColor.coin.withAlphaComponent(alpha), GumballCrankColor.coinDeep.withAlphaComponent(alpha)], locations: [0, 0.55, 1], center: CGPoint(x: x - r * 0.35, y: y - r * 0.4), radius: r * 1.5)
        strokeCircle(context, x: x, y: y, r: r * 0.72, color: GumballCrankColor.coinDeep.withAlphaComponent(0.7 * alpha), width: max(1, r * 0.12)); circle(context, x: x - r * 0.3, y: y - r * 0.32, r: r * 0.18, color: UIColor.white.withAlphaComponent(0.7 * alpha))
    }

    private enum TextAlignment { case left, center, right }
    private static func drawText(_ context: CGContext, _ value: String, x: Double, baseline: Double, font: UIFont, color: UIColor, alignment: TextAlignment = .left) {
        let attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        let width = measure(value, font: font), start = alignment == .center ? x - width / 2 : alignment == .right ? x - width : x
        NSString(string: value).draw(at: CGPoint(x: start, y: baseline - Double(font.ascender)), withAttributes: attributes)
    }
    private static func drawSpaced(_ context: CGContext, _ value: String, center: Double, baseline: Double, font: UIFont, color: UIColor, tracking: Double) {
        let widths = value.map { measure(String($0), font: font) }, total = widths.reduce(0, +) + tracking * Double(max(0, value.count - 1)); var x = center - total / 2
        for (index, character) in value.enumerated() { drawText(context, String(character), x: x, baseline: baseline, font: font, color: color); x += widths[index] + tracking }
    }
    private static func measure(_ value: String, font: UIFont) -> Double { Double(NSString(string: value).size(withAttributes: [.font: font]).width) }
    private static func grouped(_ value: Double) -> String { let formatter = NumberFormatter(); formatter.numberStyle = .decimal; formatter.groupingSeparator = ","; return formatter.string(from: NSNumber(value: value.rounded())) ?? String(Int(value.rounded())) }
    private static func halfCircle(radius: Double, top: Bool) -> UIBezierPath { let path = UIBezierPath(); path.move(to: CGPoint(x: -radius, y: 0)); path.addArc(withCenter: .zero, radius: radius, startAngle: .pi, endAngle: top ? .pi * 2 : 0, clockwise: top); path.close(); return path }

    private static func linearGradient(_ context: CGContext, rect: CGRect? = nil, path: CGPath? = nil, colors: [UIColor], locations: [CGFloat], start: CGPoint, end: CGPoint) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: locations) else { return }
        context.saveGState(); if let path { context.addPath(path); context.clip() } else if let rect { context.clip(to: rect) }; context.drawLinearGradient(gradient, start: start, end: end, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation]); context.restoreGState()
    }
    private static func radialGradient(_ context: CGContext, rect: CGRect? = nil, path: CGPath? = nil, colors: [UIColor], locations: [CGFloat], center: CGPoint, radius: CGFloat) {
        guard let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map(\.cgColor) as CFArray, locations: locations) else { return }
        context.saveGState(); if let path { context.addPath(path); context.clip() } else if let rect { context.clip(to: rect) }; context.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius, options: [.drawsAfterEndLocation]); context.restoreGState()
    }
    private static func chrome(_ context: CGContext, path: CGPath, start: CGFloat, end: CGFloat) { linearGradient(context, path: path, colors: [GumballCrankColor.chromeLow, GumballCrankColor.chromeHi, GumballCrankColor.chromeMid, GumballCrankColor.chromeDeep, GumballCrankColor.chromeMid, GumballCrankColor.chromeHi, GumballCrankColor.chromeLow], locations: [0, 0.16, 0.34, 0.52, 0.72, 0.88, 1], start: CGPoint(x: start, y: 0), end: CGPoint(x: end, y: 0)) }
    private static func circle(_ context: CGContext, x: Double, y: Double, r: Double, color: UIColor) { context.setFillColor(color.cgColor); context.fillEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
    private static func strokeCircle(_ context: CGContext, x: Double, y: Double, r: Double, color: UIColor, width: Double) { context.setStrokeColor(color.cgColor); context.setLineWidth(width); context.strokeEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
    private static func roundRect(_ context: CGContext, _ rect: CGRect, radius: CGFloat, color: UIColor) { context.setFillColor(color.cgColor); context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: radius).cgPath); context.fillPath() }
    private static func strokeRoundRect(_ context: CGContext, _ rect: CGRect, radius: CGFloat, color: UIColor, width: CGFloat) { context.setStrokeColor(color.cgColor); context.setLineWidth(width); context.addPath(UIBezierPath(roundedRect: rect, cornerRadius: radius).cgPath); context.strokePath() }
    private static func shadowShape(_ context: CGContext, color: UIColor, blur: CGFloat, drawing: () -> Void) { context.saveGState(); context.setShadow(offset: .zero, blur: blur, color: color.cgColor); context.setFillColor(color.cgColor); drawing(); context.restoreGState() }
}
