import SwiftUI

struct ReededGlassRevealFrame: View {
    let driver: ReededGlassRevealDriver
    let layout: ReededGlassRevealLayout
    let reduced: Bool
    let displayScale: Double
    let adaptive: Bool
    let contentOpacity: Double
    let glints: Double
    let fontScale: Double
    typealias R = ReededGlassRevealResources
    typealias M = ReededGlassRevealMotion

    var body: some View {
        let l = layout, look = driver.look
        ZStack(alignment: .topLeading) {
            R.color(0xE5E4EC)
            Rectangle().fill(.white)
                .frame(width: l.width, height: ceil(l.groundSolid + 1), alignment: .topLeading)
                .colorEffect(ShaderLibrary.reededGlassRevealGlass(
                    .image(Image(uiImage: R.sharp)), .image(Image(uiImage: R.soft)), .floatArray(glassUniforms(look))))
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                context.fill(Path(CGRect(x: 0, y: l.groundTop, width: l.width, height: l.height - l.groundTop)),
                             with: .linearGradient(Gradient(colors: [R.color(0x0C0C10, 0), R.color(0x0C0C10)]),
                                                   startPoint: CGPoint(x: 0, y: l.groundTop), endPoint: CGPoint(x: 0, y: l.groundSolid)))
                var shadow = context
                shadow.addFilter(.blur(radius: 22 * l.scale))
                let c = l.capsule
                shadow.fill(Path(roundedRect: CGRect(x: c.minX + 6 * l.scale, y: c.minY + 10 * l.scale,
                                                     width: c.width - 12 * l.scale, height: c.height), cornerRadius: 28 * l.scale),
                            with: .color(R.color(0x050407, 0.45)))
                context.fill(Path(roundedRect: CGRect(x: l.x(16), y: l.y(584.5), width: l.x(370), height: l.height - l.y(584.5) + 60),
                                  cornerRadius: l.x(32)), with: .color(R.color(0x161619)))
            }
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.reededGlassRevealCapsule(.image(Image(uiImage: R.haze)), .floatArray(capsuleUniforms(look))))
            if !adaptive {
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                    var c = context
                    c.translateBy(x: 0, y: l.image.minY); c.scaleBy(x: l.scale, y: l.scale)
                    drawThumb(c, look)
                    R.draw(c, R.title1, 144.5, 510.8, 0xF7F7F9, fontScale: min(fontScale, 1.25))
                    drawNumber(c, look)
                    drawButton(c, look, plus: false)
                    drawPanel(c)
                    drawButton(c, look, plus: true)
                }.opacity(contentOpacity)
            }
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private func glassUniforms(_ look: M.Look) -> [Float] {
        let l = layout, img = l.image
        var u: [Double] = [img.minX, img.minY, img.width, img.height]
        u += ReededGlassRevealLayout.ribEdges(l.width)
        u += ReededGlassRevealLayout.seeds
        u += look.depths
        u += [look.mean, 0.015 * (1 + look.breath), l.width / 2, img.minY + 300 * l.scale,
              l.scale, reduced ? M.clamp(1 - look.mean) : -1,
              -2.3, 1.15, 0, 0.4, 0.12, 1.4, 0.9, 1.6, 0.75, 0.7,
              look.light, reduced ? 0.6 * glints : glints, reduced ? -1 : look.run, 4, 11, 17]
        return u.map(Float.init)
    }
    private func capsuleUniforms(_ look: M.Look) -> [Float] {
        let l = layout, img = l.image, c = l.capsule, edges = ReededGlassRevealLayout.ribEdges(l.width)
        var u: [Double] = [img.minX, img.minY, img.width, img.height,
                           c.minX, c.minY, c.width, c.height, l.x(28), l.x(334.5), l.y(520.9), l.x(30),
                           l.x(36), l.y(491.3), l.x(84.2), l.x(59.2)]
        u += R.toP3(0x1F1D20); u += R.toP3(0x121013); u += R.toP3(0xACACB4)
        // capLeft/capRight in RN add 6 SCREEN points, rather than 6 scaled points.
        u += [l.scale, look.lag, M.depthAt(look.depths, c.minX + 6, edges), M.depthAt(look.depths, c.maxX - 6, edges)]
        return u.map(Float.init)
    }
    private func drawNumber(_ c: GraphicsContext, _ look: M.Look) {
        let edges = ReededGlassRevealLayout.ribEdges(layout.width), s = layout.scale
        func depth(_ x: Double) -> Double { M.depthAt(look.depths, x * s, edges) }
        func roll(_ d: Double) -> Double { let p = M.clamp((0.9 - d) / 0.8); return p * p * (3 - 2 * p) }
        let slide = min(1.1, max(-0.1, 1 - depth(110))), dx = R.digitX[8] - 171.9
        for i in 0..<4 {
            let x = 143.4 + Double(i) * R.advances["*"]! * 15
            let p = roll(depth(x + R.advances["*"]! * 15 / 2))
            R.draw(c, R.star, x, 543.3 + 3 - (reduced ? 0 : p * 10), 0x959598,
                   1 - M.smooth(0, reduced ? 0.5 : 0.55, p))
        }
        let chars = Array("48291730")
        for i in 0..<8 {
            let x = R.digitX[i], advance = R.advances[chars[i]]! * 15
            let right = x + advance, gate = right + 1.5 > 171.9 ? (right + 1.5 - 171.9) / dx : -1
            var p = roll(depth(x + advance / 2))
            if gate >= 0 { p = min(p, M.smooth(min(gate, 0.9), min(gate + 0.1, 0.99), slide)) }
            let alpha = min(M.smooth(reduced ? 0.5 : 0.4, 1, p), 1 - 0.75 * look.peek)
            R.draw(c, R.digits[i], x, 543.3 + (reduced ? 0 : (1 - p) * 12), 0x959598, alpha)
        }
        if reduced {
            R.draw(c, R.lastFour, 171.9, 543.3, 0x959598, M.smooth(0.5, 1, look.mean))
            R.draw(c, R.lastFour, R.digitX[8], 543.3, 0x959598, 1 - M.smooth(0, 0.5, look.mean))
        } else { R.draw(c, R.lastFour, 171.9 + slide * dx, 543.3, 0x959598) }
    }
    private func drawThumb(_ context: GraphicsContext, _ look: M.Look) {
        var c = context
        c.translateBy(x: 36, y: 491.3)
        let rect = CGRect(x: 0, y: 0, width: 84.2, height: 59.2)
        let shape = Path(roundedRect: rect, cornerRadius: 4, style: .continuous)
        c.clip(to: shape)
        c.fill(shape, with: .linearGradient(Gradient(stops: [.init(color: R.color(0xC9C9BC), location: 0),
                                                           .init(color: R.color(0x8E927E), location: 0.55),
                                                           .init(color: R.color(0x5B7052), location: 1)]),
                                           startPoint: .zero, endPoint: CGPoint(x: 84.2, y: 59.2)))
        let frost = 0.6 + 0.4 * M.clamp(look.mean)
        c.fill(shape, with: .linearGradient(Gradient(colors: [.white.opacity(0.22 * frost), .white.opacity(0)]),
                                           startPoint: CGPoint(x: 8.42, y: 0), endPoint: CGPoint(x: 50.52, y: 41.44)))
        R.draw(c, R.thumbBrand, 6, 13.6, 0xF4F5EF)
        R.draw(c, R.thumbGroups, 6, 30.4, 0xDADDD2)
        R.draw(c, R.thumbLast, 37.5, 30.9, 0xF4F5EF)
        R.draw(c, R.thumbExpiryLabel, 6, 44.4, 0xDADDD2)
        R.draw(c, R.thumbExpiry, 6, 49.9, 0xF4F5EF)
        var mark = c
        mark.translateBy(x: 72.6, y: 9); mark.scaleBy(x: 1.25, y: 1.25)
        mark.stroke(R.sable, with: .color(R.color(0xF4F5EF)), style: StrokeStyle(lineWidth: 0.75, lineCap: .round, lineJoin: .round))
        var network = c
        network.translateBy(x: 68.2, y: 42.5); network.scaleBy(x: 1.32, y: 1.32)
        network.fill(R.networkA, with: .color(R.color(0xE9473A))); network.fill(R.networkB, with: .color(R.color(0xF2B33D)))
        let hairline = 1 / (displayScale * layout.scale)
        c.stroke(Path(roundedRect: rect.insetBy(dx: hairline / 2, dy: hairline / 2), cornerRadius: 4 - hairline / 2, style: .continuous),
                 with: .color(.white.opacity(0.32)), lineWidth: hairline)
    }
    private func drawButton(_ context: GraphicsContext, _ look: M.Look, plus: Bool) {
        var c = context
        c.translateBy(x: 334.5, y: plus ? 633.9 : 520.9)
        let held = plus ? driver.plusHeld : driver.swapHeld
        let scale = plus ? held : look.press * held
        if reduced { c.opacity = held } else { c.scaleBy(x: scale, y: scale) }
        c.fill(Path(ellipseIn: CGRect(x: -30, y: -30, width: 60, height: 60)), with: .color(R.color(plus ? 0x242328 : 0xFFFFFD)))
        if !plus && !reduced { c.rotate(by: .degrees(look.glyph)) }
        c.translateBy(x: plus ? -6.15 : -9, y: plus ? -6.15 : -6)
        c.stroke(plus ? R.plus : R.swap, with: .color(R.color(plus ? 0xF7F7F9 : 0x202124)),
                 style: StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round))
    }
    private func drawPanel(_ c: GraphicsContext) {
        R.draw(c, R.title2, 37.6, 639.7, 0xF7F7F9, fontScale: fontScale)
        for i in 0..<2 {
            let cy = i == 0 ? 718.2 : 787.2, base = i == 0 ? 723.2 : 792.2
            c.fill(Path(ellipseIn: CGRect(x: 36.9, y: cy - 30, width: 60, height: 60)), with: .color(R.color(0x222224)))
            drawMark(c, i, 66.9, cy)
            R.draw(c, R.names[i], 122.1, base, 0xF7F7F9, fontScale: min(fontScale, 1.15))
            R.draw(c, R.followers[i], 200.5, base, 0x959598, fontScale: min(fontScale, 1.15))
            R.draw(c, R.values[i], 356.5 - R.values[i].width * min(fontScale, 1.15), base, 0xF7F7F9, fontScale: min(fontScale, 1.15))
        }
    }
    static func mark(_ context: GraphicsContext, _ index: Int, _ cx: Double, _ cy: Double) {
        var c = context
        if index == 0 {
            c.translateBy(x: cx - 11.5, y: cy - 8)
            c.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 23, height: 16), cornerRadius: 4.5), with: .color(R.color(0xEE2A2F)))
            c.stroke(R.vidly, with: .color(R.color(0xF7F7F9)), style: StrokeStyle(lineWidth: 2.1, lineCap: .round, lineJoin: .round))
        } else {
            c.translateBy(x: cx - 11, y: cy - 11)
            c.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 22, height: 22)), with: .color(R.color(0x0B0B0D)))
            for (x, y, hex) in [(-0.7, -0.6, UInt32(0x2CF4EF)), (0.7, 0.6, UInt32(0xFE2C55)), (0.0, 0.0, UInt32(0xF7F7F9))] {
                c.stroke(R.loopMark.applying(CGAffineTransform(translationX: x, y: y)), with: .color(R.color(hex)), lineWidth: 1.7)
            }
        }
    }
    private func drawMark(_ c: GraphicsContext, _ i: Int, _ x: Double, _ y: Double) { Self.mark(c, i, x, y) }
}
