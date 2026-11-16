import SwiftUI

/// cpc-art's vector layers, with the original glyph baselines, strokes, gradients and blur sigmas.
struct CoinPileCollectArt {
    typealias R = CoinPileCollectResources
    typealias M = CoinPileCollectMotion
    let res: R
    var fs: Double { res.fs }
    static func ellipse(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
    }
    static func round(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r)
    }
    static func gradient(_ colors: [Color], _ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, stops: [Double]? = nil) -> GraphicsContext.Shading {
        let gradient = Gradient(stops: colors.enumerated().map { .init(color: $0.element, location: stops?[$0.offset] ?? Double($0.offset) / Double(colors.count - 1)) })
        return .linearGradient(gradient, startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }
    static func blur(_ c: GraphicsContext, _ path: Path, _ color: Color, _ sigma: Double) {
        var c = c; c.addFilter(.blur(radius: sigma)); c.fill(path, with: .color(color))
    }
    func line(_ c: GraphicsContext, _ key: String, _ x: Double, _ y: Double, _ color: Color, stroke: Bool = false) {
        let p = res.lines[key]!.path.applying(CGAffineTransform(translationX: x, y: y))
        if stroke { c.stroke(p, with: .color(color), lineWidth: 1) } else { c.fill(p, with: .color(color)) }
    }

    func background(_ c: GraphicsContext, bottom: Double, theta: Double) {
        avatars(c); back(c)
        let baseline = 158 + 56 * 0.708 * fs
        line(c, "title1", 24, baseline, R.color(0xF7F4EE))
        line(c, "title2", 24, baseline + 56 * fs, R.color(0xF7F4EE))
        lime(c); magenta(c, theta); navy(c, bottom)
        let first = 664 + 6 + 34 * 0.708 * fs
        for (i, key) in ["premium1", "premium2", "premium3"].enumerated() {
            line(c, key, 24, first + Double(i) * 36 * fs, R.color(0xF7F4EE))
        }
    }
    private func avatars(_ c: GraphicsContext) {
        let colors: [UInt32] = [0x7C6CF0, 0xF2B92E, 0xB7F04A]
        for k in 0..<3 {
            let cx = 362 - Double(k) * 28, cy = 86.0
            c.fill(Self.ellipse(cx - 22.5, cy - 22.5, 45, 45), with: .color(R.color(0x07040C)))
            let circle = Self.ellipse(cx - 20, cy - 20, 40, 40)
            c.fill(circle, with: .color(R.color(colors[k])))
            c.fill(circle, with: Self.gradient([.white.opacity(0.28), .white.opacity(0), .black.opacity(0.18)], cx - 20, cy - 20, cx + 20, cy + 20, stops: [0, 0.5, 1]))
            let mono = res.monograms[2 - k], shift = k == 0 ? 0.0 : 6.0
            let path = mono.path.applying(CGAffineTransform(translationX: cx - mono.width / 2 + shift, y: cy + 5.6))
            c.fill(path, with: .color(R.color(k == 0 ? 0xF7F4EE : 0x14131C)))
        }
    }
    private func back(_ c: GraphicsContext) {
        var p = Path(); p.move(to: CGPoint(x: 44, y: 130)); p.addLine(to: CGPoint(x: 27, y: 130))
        p.move(to: CGPoint(x: 34, y: 123)); p.addLine(to: CGPoint(x: 27, y: 130)); p.addLine(to: CGPoint(x: 34, y: 137))
        c.stroke(p, with: .color(R.color(0xF7F4EE)), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
    }
    private func lime(_ context: GraphicsContext) {
        var c = context
        let tile = R.squircle(24, 290, 172, 180, 36)
        c.fill(tile, with: .color(R.color(0x6CA824))); c.clip(to: tile); c.translateBy(x: 24, y: 290)
        let first = 18 + 20 * 0.708 * fs
        line(c, "protect1", 18, first, R.color(0xF7FBEA)); line(c, "protect2", 18, first + 24 * fs, R.color(0xF7FBEA))
        let cx = 18.0, cy = 88.0, cw = 136.0, ch = 80.0
        Self.blur(c, Self.round(cx, cy + 5, cw, ch, 11), R.color(0x03141B, 0.42), 5)
        let card = Self.round(cx, cy, cw, ch, 10)
        c.fill(card, with: Self.gradient([R.color(0x0B1450), R.color(0x1D2D86)], cx, cy, cx + cw, cy + ch))
        c.fill(card, with: Self.gradient([.white.opacity(0.2), .white.opacity(0), R.color(0x788CFF, 0.16)], cx, cy, cx + cw * 0.9, cy + ch, stops: [0, 0.45, 1]))
        line(c, "halden", cx + 12, cy + 26, R.color(0xF7F4EE, 0.82))
        line(c, "cardUrl", cx + cw - 12 - res.lines["cardUrl"]!.width, cy + ch - 10, R.color(0xF7F4EE, 0.5))
        let film = Self.round(cx - 5, cy - 5, cw + 10, ch + 10, 15)
        c.fill(film, with: .color(R.color(0xEBF5FF, 0.07))); c.stroke(film, with: .color(R.color(0xF0F8FF, 0.55)), lineWidth: 1.1)
        var crease = Path()
        crease.move(to: CGPoint(x: cx - 3, y: cy + 14))
        crease.addCurve(to: CGPoint(x: cx + 64, y: cy - 2), control1: CGPoint(x: cx + 22, y: cy + 6), control2: CGPoint(x: cx + 40, y: cy + 22))
        crease.move(to: CGPoint(x: cx + cw + 3, y: cy + ch - 18))
        crease.addCurve(to: CGPoint(x: cx + cw - 70, y: cy + ch + 3), control1: CGPoint(x: cx + cw - 22, y: cy + ch - 8), control2: CGPoint(x: cx + cw - 40, y: cy + ch - 24))
        crease.move(to: CGPoint(x: cx + 6, y: cy + ch + 3))
        crease.addCurve(to: CGPoint(x: cx + 44, y: cy + ch + 3), control1: CGPoint(x: cx + 18, y: cy + ch - 10), control2: CGPoint(x: cx + 30, y: cy + ch - 4))
        c.stroke(crease, with: .color(.white.opacity(0.22)), style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
        Self.blur(c, Self.ellipse(cx + 6, cy + 4, 34, 10), .white.opacity(0.35), 3)
    }
    private func navy(_ context: GraphicsContext, _ bottom: Double) {
        var c = context
        let tile = R.squircle(204, 598, 174, max(bottom + 48 - 598, 162), 36)
        c.fill(tile, with: Self.gradient([R.color(0x2B3868), R.color(0x10173D)], 204, 598, 204 + 174 * 0.3, 858))
        c.clip(to: tile); c.translateBy(x: 204, y: 598)
        var grid = Path()
        for i in -48..<22 {
            let x = Double(i) * 12
            grid.move(to: CGPoint(x: x, y: 0)); grid.addLine(to: CGPoint(x: x + 576, y: 640))
            grid.move(to: CGPoint(x: x + 576, y: 0)); grid.addLine(to: CGPoint(x: x, y: 640))
        }
        c.stroke(grid, with: .color(R.color(0x96AAFF, 0.1)), lineWidth: 0.8)
        line(c, "haldenBig", 18, 140, R.color(0xF7F4EE, 0.5), stroke: true)
    }
    private func magenta(_ context: GraphicsContext, _ theta: Double) {
        var c = context
        let tile = R.squircle(24, 478, 172, 162, 36)
        c.fill(tile, with: Self.gradient([R.color(0xA94696), R.color(0x8F3A84)], 24, 478, 67, 640))
        c.clip(to: tile); c.translateBy(x: 24, y: 478)
        let wide = abs(cos(theta)) * 50 + 5
        Self.blur(c, Self.ellipse(100 - wide, 70 - 50, wide * 2, 100), R.color(0x3A0C36, 0.45), 9)
    }
    func exchangeCopy(_ c: GraphicsContext) {
        line(c, "exchange1", 16, 150 - 20 * fs, R.color(0xF7F4EE))
        line(c, "exchange2", 16, 150, R.color(0xF7F4EE))
    }
    func hopper(_ context: GraphicsContext, _ a: [Double]) {
        var c = context
        c.clip(to: R.squircle(0, 0, 174, 340, 40))
        for i in 0..<26 {
            let o = i * 32, g = a[o + 8]
            if g < 0.06 || a[o + 9] < 0.5 { continue }
            var bloom = c; bloom.blendMode = .plusLighter; bloom.opacity = min(g, 1)
            let point = CGPoint(x: a[o], y: a[o + 1]), radius = 35.0 * 2.3
            bloom.fill(Self.ellipse(point.x - radius, point.y - radius, radius * 2, radius * 2), with: .radialGradient(Gradient(colors: [R.color(0x3FE9FF, 0.28), R.color(0x3FE9FF, 0)]), center: point, startRadius: 0, endRadius: radius))
        }
        let ctl = 1024, press = a[ctl + 7], bulge = a[ctl + 8], pillA = a[ctl + 11], lit = a[ctl + 10], flow = a[ctl + 22]
        line(c, "earned", 18, 34, R.color(0xF7F4EE)); line(c, "period", 18, 88, R.color(0xF7F4EE))
        let k = M.clamp01(lit)
        let color = Color(.sRGB, red: M.mix(244.0 / 255, 248.0 / 255, k), green: M.mix(193.0 / 255, 216.0 / 255, k), blue: M.mix(58.0 / 255, 120.0 / 255, k), opacity: 1)
        amount(c, a[ctl + 12], 18, 68, color)
        let sx = 1 - 0.03 * press, sy = sx * (1 + bulge)
        c.translateBy(x: 99, y: 298); c.scaleBy(x: sx, y: sy); c.translateBy(x: -99, y: -298)
        let rr = Self.round(24, 268, 150, 60, 30)
        Self.blur(c, Self.round(26, 274, 146, 58, 30), R.color(0x03141B, 0.5 * pillA * (1 - 0.6 * press)), 7)
        var body = c; body.opacity = pillA
        body.fill(rr, with: Self.gradient([R.color(0xF7C943), R.color(0xF1B92E)], 0, 268, 0, 328))
        if flow > 0.01 && lit > 0.01 {
            var rim = c; rim.opacity = 0.6 * flow * lit
            var p = Path(); p.move(to: CGPoint(x: 46, y: 268.5)); p.addLine(to: CGPoint(x: 152, y: 268.5))
            rim.stroke(p, with: Self.gradient([R.color(0x3FE9FF, 0), R.color(0x3FE9FF), R.color(0x3FE9FF, 0)], 46, 0, 152, 0, stops: [0, 0.5, 1]), style: StrokeStyle(lineWidth: 1, lineCap: .round))
        }
        line(c, "collect", 99 - res.lines["collect"]!.width / 2, 298 + 17 * 0.708 * fs / 2, R.color(0x3A2600, pillA))
    }
    private func digit(_ c: GraphicsContext, _ q: Double, _ from: Double, _ x: Double, _ base: Double, _ alpha: Double, _ color: Color) {
        guard alpha >= 0.01 else { return }
        let fl = floor(q), fraction = q - fl
        let roll = from >= 1 ? 0 : M.smooth(from, 1, fraction)
        let d0 = (Int(fl) % 10 + 10) % 10, d1 = (d0 + 1) % 10, size = 30 * fs
        let a0 = alpha * (1 - M.smooth(0, 0.6, roll)), a1 = alpha * M.smooth(0.4, 1, roll)
        func draw(_ id: Int, _ y: Double, _ a: Double, _ blur: Double) {
            guard a > 0.003 else { return }
            var c = c; c.opacity = a
            if blur > 0 { c.addFilter(.blur(radius: blur)) }
            c.fill(res.digits[id].applying(CGAffineTransform(translationX: x, y: y)), with: .color(color))
        }
        draw(d0, base - roll * size * 0.3, a0, roll > 0.02 ? roll * size * 0.05 : 0)
        draw(d1, base + (1 - roll) * size * 0.3, a1, roll < 0.98 ? (1 - roll) * size * 0.05 : 0)
    }
    private func amount(_ c: GraphicsContext, _ cents: Double, _ x0: Double, _ base: Double, _ color: Color) {
        let v = max(cents, 0), dollars = v / 100, size = 30 * fs
        let adv = (0.674 - 0.01) * size, dollarAdv = (0.666 - 0.01) * size, dotAdv = (0.338 - 0.01) * size
        let nd = 1 + M.smooth(9.5, 10, dollars) + M.smooth(99.5, 100, dollars) + M.smooth(999.5, 1000, dollars)
        let xDot = x0 + dollarAdv + nd * adv
        c.fill(res.dollar.applying(CGAffineTransform(translationX: x0, y: base)), with: .color(color))
        c.fill(res.dot.applying(CGAffineTransform(translationX: xDot, y: base)), with: .color(color))
        for k in 0..<6 {
            let x = k < 2 ? xDot + dotAdv + Double(1 - k) * adv : xDot - Double(k - 1) * adv
            let alpha: Double
            switch k { case 3: alpha = M.smooth(9.5, 10, dollars); case 4: alpha = M.smooth(99.5, 100, dollars); case 5: alpha = M.smooth(999.5, 1000, dollars); default: alpha = 1 }
            digit(c, v / pow(10, Double(k)), k == 0 ? 1 : k == 1 ? 0.7 : 0.97, x, base, alpha, color)
        }
    }
}
