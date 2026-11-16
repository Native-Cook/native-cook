import SwiftUI

struct ObjectSigninDissolvePage: View {
    let light: Bool
    let layout: ObjectSigninDissolveLayout
    let glow: ObjectSigninDissolveDriver.Glow
    let pressed: Int
    typealias R = ObjectSigninDissolveResources
    typealias M = ObjectSigninDissolveMotion

    var body: some View {
        Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
            let l = layout, s = l.scale, w = l.width
            let ground: UInt32 = light ? 0xFBF7EE : 0x0A0610
            let pool: UInt32 = light ? 0xFFF1D6 : 0x231B2C
            let poolGlow: UInt32 = light ? 0xFFC2E0 : 0xFF3EA5
            let tinted = R.mixHex(pool, poolGlow, light ? 0.35 : 0.16)
            let center = CGPoint(x: w / 2, y: l.cy), radius = 190 * s * 1.55
            context.fill(Path(CGRect(x: 0, y: 0, width: w, height: l.height)), with: .color(R.color(ground)))
            context.fill(Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: 2 * radius, height: 2 * radius)),
                         with: .radialGradient(Gradient(colors: [R.color(R.mixHex(pool, tinted, glow.pool)), R.color(ground)]), center: center, startRadius: 0, endRadius: radius))
            if glow.zip >= 0 && glow.zip <= 0.42 {
                let u = glow.zip / 0.42, pulseR = (190 + 40 * (1 - (1 - u) * (1 - u))) * s
                var pulse = context; pulse.opacity = 0.06 * (1 - u) * (1 - u)
                pulse.fill(Path(ellipseIn: CGRect(x: center.x - pulseR, y: center.y - pulseR, width: 2 * pulseR, height: 2 * pulseR)),
                           with: .radialGradient(Gradient(colors: [R.color(poolGlow), .clear]), center: center, startRadius: 0, endRadius: pulseR))
            }
            drawFront(context)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }
    private func text(_ c: GraphicsContext, _ line: R.Line, _ x: Double, _ baseline: Double, _ hex: UInt32) {
        c.fill(line.path.applying(CGAffineTransform(scaleX: layout.scale, y: layout.scale).concatenating(CGAffineTransform(translationX: x, y: baseline))), with: .color(R.color(hex)))
    }
    private func drawFront(_ c: GraphicsContext) {
        let l = layout, s = l.scale, w = l.width
        let ink: UInt32 = light ? 0x14101A : 0xF7F3FA
        let mute: UInt32 = light ? 0x8E8579 : 0x7C7585
        let legal: UInt32 = light ? 0xA39A8C : 0x5E5867
        let theme: UInt32 = light ? 0xFFFFFF : 0x150F1B
        let tx = l.x(360), ty = l.yt(81)
        func circle(_ x: Double, _ y: Double, _ r: Double, _ hex: UInt32) {
            c.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: 2 * r, height: 2 * r)), with: .color(R.color(hex)))
        }
        circle(tx, ty, 20 * s, theme)
        if light {
            circle(tx, ty, 5.4, 0xF5A623)
            var rays = Path()
            for i in 0..<8 {
                let a = Double(i) * .pi / 4
                rays.move(to: CGPoint(x: tx + cos(a) * 8.6, y: ty + sin(a) * 8.6))
                rays.addLine(to: CGPoint(x: tx + cos(a) * 11.2, y: ty + sin(a) * 11.2))
            }
            c.stroke(rays, with: .color(R.color(0xF5A623)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
        } else {
            circle(tx, ty, 8.5, 0xF2B86A); circle(tx + 5, ty - 3.5, 7.4, theme)
        }
        let wordW = R.word.width * s, wordY = l.yt(598), wordTop = wordY - 54 * s * 0.7
        let word = R.word.path.applying(CGAffineTransform(scaleX: s, y: s).concatenating(CGAffineTransform(translationX: w / 2 - wordW / 2, y: wordY)))
        c.fill(word, with: .color(R.color(ink)))
        var tint = c; tint.opacity = M.clamp(glow.word) * 0.5
        tint.fill(word, with: .linearGradient(Gradient(colors: [R.color(0xFF8CC8), R.color(0xFF8CC8, 0)]), startPoint: CGPoint(x: 0, y: wordTop), endPoint: CGPoint(x: 0, y: wordTop + 54 * s * 0.9)))
        if glow.zip >= 0 && glow.zip <= 0.24 {
            let sweepX = w / 2 - wordW / 2 - 40 + glow.zip / 0.24 * (wordW + 80)
            var sweep = c; sweep.opacity = 0.5 * sin(.pi * glow.zip / 0.24)
            sweep.fill(word, with: .linearGradient(Gradient(colors: [.clear, .white, .clear]), startPoint: CGPoint(x: sweepX - 40, y: 0), endPoint: CGPoint(x: sweepX + 40, y: 0)))
        }
        text(c, R.tag, w / 2 - R.tag.width * s / 2, l.yt(627), mute)
        button(c, l.pill, 0, R.email, light ? 0x15101B : 0xFBFAFC, light ? 0xFBF7EE : 0x0F0B14)
        button(c, l.half(0), 1, R.passkey, light ? 0xEFE9DD : 0x1A1620, light ? 0x2A2430 : 0xD8D3DF)
        button(c, l.half(1), 2, R.phone, light ? 0xEFE9DD : 0x1A1620, light ? 0x2A2430 : 0xD8D3DF)
        var x = w / 2 - R.legal.reduce(0) { $0 + $1.width * s } / 2
        for (i, line) in R.legal.enumerated() {
            let link = i == 1 || i == 3
            text(c, line, x, l.yb(817), link ? mute : legal)
            if link { c.fill(Path(CGRect(x: x, y: l.yb(817) + 2.2, width: line.width * s, height: 0.9)), with: .color(R.color(legal))) }
            x += line.width * s
        }
        text(c, R.legalB, w / 2 - R.legalB.width * s / 2, l.yb(834), legal)
    }
    private func button(_ context: GraphicsContext, _ rect: CGRect, _ kind: Int, _ line: R.Line, _ ground: UInt32, _ ink: UInt32) {
        var c = context
        let s = layout.scale, radius = (kind == 0 ? 16.0 : 14) * s
        if pressed == kind {
            c.translateBy(x: rect.midX, y: rect.midY); c.scaleBy(x: 0.97, y: 0.97); c.translateBy(x: -rect.midX, y: -rect.midY)
        }
        let shape = Path(roundedRect: rect, cornerRadius: radius)
        c.fill(shape, with: .color(R.color(ground)))
        if kind == 0 {
            var edge = c; edge.clip(to: shape); edge.opacity = M.clamp(glow.pill) * 0.6
            edge.fill(Path(CGRect(x: rect.minX, y: rect.minY, width: rect.width, height: 1.2)), with: .color(R.color(0xFFB3DA)))
        }
        // RN's icons and 12 pt label offset deliberately do not scale with S.
        let iconX = rect.midX - (line.width * s / 2 + (kind == 0 ? 17.0 : 14))
        c.stroke(R.icon(kind, iconX, rect.midY), with: .color(R.color(ink)), style: StrokeStyle(lineWidth: kind == 0 ? 1.7 : 1.6, lineCap: .round, lineJoin: .round))
        text(c, line, rect.midX - line.width * s / 2 + 12, rect.midY + (kind == 0 ? 5.6 : 5) * s, ink)
    }
}
