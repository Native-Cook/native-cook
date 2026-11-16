import SwiftUI

struct PourJarGoalFrame: View {
    let driver: PourJarGoalDriver
    let layout: PourJarGoalLayout
    let fonts: PourJarGoalResources.Fonts
    let reduced: Bool
    let autoEnabled: Bool
    typealias R = PourJarGoalResources
    typealias M = PourJarGoalMotion

    var body: some View {
        let scene = driver.scene
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.pourJarGoal(.floatArray(uniforms(scene))))
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                chrome(context, scene)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private func uniforms(_ scene: PourJarGoalScene) -> [Float] {
        let l = layout, j = l.jar
        let meanY = M.vessel.volumeY(scene.amount / 3100)
        var data: [Double] = [l.width, l.height, j.minX, j.minY, j.width, j.height, meanY,
                    M.clamp(scene.amount / 3100), reduced ? 0 : 12, scene.dropX, scene.fingerY,
                    scene.stream, scene.dropY, scene.dropRadius, scene.impact, reduced ? 1 : 0]
        data += M.bubbles(scene.bubbleTime, meanY, scene.heights)
        data += scene.shaderHeights
        data += M.shaderWalls
        return data.map(Float.init)
    }
    private func circle(_ c: GraphicsContext, _ x: Double, _ y: Double, _ radius: Double, _ color: Color) {
        c.fill(Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2)), with: .color(color))
    }
    private func rounded(_ rect: CGRect, _ radius: Double) -> Path { Path(roundedRect: rect, cornerRadius: radius) }
    private func line(_ c: GraphicsContext, _ points: [CGPoint], _ color: Color, _ width: Double) {
        var p = Path()
        if let first = points.first { p.move(to: first) }
        for point in points.dropFirst() { p.addLine(to: point) }
        c.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round))
    }
    private func shadow(_ c: GraphicsContext, _ path: Path, _ x: Double, _ y: Double, _ blur: Double, _ color: Color) {
        var context = c
        // Preserve RN's blur input; native falloff is checked by pinned parity.
        context.addFilter(.shadow(color: color, radius: blur, x: x, y: y, options: .shadowOnly))
        context.fill(path, with: .color(R.white))
    }
    private func chrome(_ context: GraphicsContext, _ scene: PourJarGoalScene) {
        let l = layout, s = l.s, f = fonts, cy = l.headerY + 22 * s
        let back = Path(ellipseIn: CGRect(x: l.x(22), y: cy - 19 * s, width: 38 * s, height: 38 * s))
        shadow(context, back, 0, 2 * s, 4 * s, R.color(0x101D29, 11.0 / 255))
        context.fill(back, with: .color(R.white))
        circle(context, l.x(361), cy, 19 * s, R.white)
        line(context, [CGPoint(x: l.x(43), y: l.headerY + 17 * s), CGPoint(x: l.x(38), y: cy),
                       CGPoint(x: l.x(43), y: l.headerY + 27 * s)], R.ink, 1.4 * s)
        for x in [355.0, 361, 367] { circle(context, l.x(x), cy, 0.95 * s, R.ink) }

        let e = M.clamp(scene.toast), titleFade = l.largeType ? 1 : 1 - M.segment(e, 0, 0.35)
        let titleShift = l.largeType || reduced ? 0 : -5 * s * M.segment(e, 0, 0.35)
        let title = "Sora, spring 2027"
        for (index, text) in (l.largestType ? title.components(separatedBy: " ") : [title]).enumerated() {
            let baseline = l.headerY + (l.largestType ? f.title.size * (0.75 + Double(index) * 1.1) : 28 * s)
            R.draw(context, f.title, text, l.width / 2 - f.title.line(text).width / 2, baseline + titleShift, R.ink, titleFade)
        }
        for tick in [1.0, 0.75, 0.5, 0.25] {
            let text = "\(Int(tick * 100))%"
            let y = l.jar.minY + max(tick == 1 ? 12 : 0, M.vessel.volumeY(tick)) * l.jar.height / 422 + f.mono.size / 3
            R.draw(context, f.mono, text, l.jar.maxX + 16 * s, y,
                   abs(scene.amount / 3100 - tick) < 0.01 ? R.ink : R.mute)
        }
        let warm = M.segment(scene.amount / 3100, 0.9, 1)
        let ink = Color(.sRGB, red: floor(16 + 26 * warm + 0.5) / 255,
                        green: floor(29 - 8 * warm + 0.5) / 255, blue: floor(41 - 23 * warm + 0.5) / 255, opacity: 1)
        amount(context, scene.displayAmount, ink)
        let pct = Int(floor(M.clamp(scene.percentAmount / 3100) * 100))
        if l.largestType {
            R.draw(context, f.body, "of $3,100", l.width / 2 - f.body.line("of $3,100").width / 2,
                   l.subtitleY + f.body.size, R.mute)
            R.draw(context, f.body, "\(pct)%", l.width / 2 - f.body.line("75%").width / 2,
                   l.subtitleY + f.body.size * 2.2, R.mute)
        } else {
            R.draw(context, f.body, "of $3,100 · \(pct)%", l.width / 2 - f.body.line("of $3,100 · 75%").width / 2,
                   l.subtitleY + 13 * s, R.mute)
        }
        let cardW = (l.largeType ? 358 : 172) * s
        let cardH = l.largeType ? (l.cardH - 8 * s) / 2 : l.cardH
        let weeks = Int(ceil(max(0, floor(3100 - scene.statAmount + 0.5)) / 40))
        for i in 0..<2 {
            let x = i == 0 || l.largeType ? l.x(22) : l.x(208)
            let y = i == 1 && l.largeType ? l.cardsY + cardH + 8 * s : l.cardsY
            let path = rounded(CGRect(x: x, y: y, width: cardW, height: cardH), 20 * s)
            shadow(context, path, 0, s, s, R.color(0x101D29, 10.0 / 255))
            shadow(context, path, 0, 5 * s, 12 * s, R.color(0x101D29, 8.0 / 255))
            context.fill(path, with: .color(R.white))
            context.stroke(path, with: .color(R.color(0xDFE3E8)), lineWidth: 0.5 * s)
            R.draw(context, f.label, i == 0 ? "Left to go" : "At $40 a week", x + 16 * s,
                   y + (l.largeType ? 12 + 12 * l.fontScale * 0.75 : 24) * s, R.mute)
            R.draw(context, f.stat, i == 0 ? M.money(3100 - scene.statAmount) : "\(weeks) weeks", x + 16 * s,
                   y + (l.largeType ? cardH / s - 16 : 56) * s, R.ink)
        }
        var buttonContext = context
        let press = reduced ? 1 : 1 - 0.03 * M.clamp(Double(scene.touching - 1))
        buttonContext.translateBy(x: l.x(139), y: l.buttonY + l.buttonH / 2)
        buttonContext.scaleBy(x: press, y: press)
        buttonContext.translateBy(x: -l.x(139), y: -l.buttonY - l.buttonH / 2)
        buttonContext.fill(rounded(CGRect(x: l.x(22), y: l.buttonY, width: 234 * s, height: l.buttonH), 22 * s), with: .color(R.ink))
        let buttonBaseline = l.buttonY + l.buttonH / 2 + f.button.size * 0.35
        R.draw(buttonContext, f.button, "Pour in", l.x(22) + 117 * s - f.button.line("Pour in").width / 2, buttonBaseline, R.white)
        let autoPath = rounded(CGRect(x: l.x(266), y: l.buttonY, width: 114 * s, height: l.buttonH), 22 * s)
        context.fill(autoPath, with: .color(R.white))
        context.stroke(autoPath, with: .color(R.color(0xDFE3E8)), lineWidth: 0.5 * s)
        let auto = autoEnabled ? (l.largestType ? "On" : "Auto on") : "Auto"
        R.draw(context, f.button, auto, l.x(323) - f.button.line(auto).width / 2, buttonBaseline, R.ink)
        toast(context, scene)
    }
    private func amount(_ context: GraphicsContext, _ value: Double, _ ink: Color) {
        let l = layout, font = fonts.number, cx = l.width / 2, baseline = l.amountBaseline
        let digitW = (0...9).map { font.line(String($0)).width }.max()! - font.size * 0.025
        let dollarW = font.line("$").width - font.size * 0.025, commaW = font.line(",").width
        let total = dollarW + 4 * digitW + commaW
        var c = context
        c.translateBy(x: cx, y: baseline); c.scaleBy(x: 0.92, y: 1); c.translateBy(x: -cx, y: -baseline)
        let roll = M.segment(value, 999, 1000)
        let compact = M.money(value)
        R.draw(c, font, compact, cx - font.line(compact).width / 2, baseline, ink, 1 - roll)
        c.opacity *= roll
        var x = cx - total / 2
        R.draw(c, font, "$", x, baseline, ink); x += dollarW
        for place in [1000.0, 100, 10, 1] {
            digit(c, value, place, x, baseline, ink)
            x += digitW
            if place == 1000 { R.draw(c, font, ",", x, baseline, ink); x += commaW }
        }
    }
    private func digit(_ context: GraphicsContext, _ value: Double, _ place: Double,
                       _ x: Double, _ baseline: Double, _ ink: Color) {
        let font = fonts.number, d = M.digit(value, place, reduced)
        let first = String(Int(floor(d)) % 10)
        if reduced { R.draw(context, font, first, x, baseline, ink); return }
        let k = M.smooth((d - floor(d) - 0.7) / 0.3), travel = font.size * 0.28
        let a0 = 1 - M.smooth(min(k / 0.6, 1)), a1 = M.smooth(max((k - 0.4) / 0.6, 0))
        R.draw(context, font, first, x, baseline - k * travel, ink, a0, max(0.001, k * font.size * 0.045))
        R.draw(context, font, String((Int(floor(d)) + 1) % 10), x, baseline + (1 - k) * travel,
               ink, a1, max(0.001, (1 - k) * font.size * 0.045))
    }
    private func toast(_ context: GraphicsContext, _ scene: PourJarGoalScene) {
        let l = layout, s = l.s, e = M.clamp(scene.toast), font = fonts.toast
        let money = M.money(scene.delta), text = money + " poured into Sora"
        if l.largeType {
            var c = context; c.opacity *= scene.toast
            c.translateBy(x: 0, y: reduced ? 0 : -38 * s * (1 - scene.toast))
            let path = rounded(CGRect(x: l.jar.minX, y: l.jar.minY + 17 * s,
                                      width: l.jar.width, height: font.size * 3.5 + 20 * s), 24 * s)
            shadow(c, path, 0, 9 * s, 11 * s, R.color(0x15202C, 48.0 / 255))
            c.fill(path, with: .color(R.color(0x2E3844)))
            let cy = l.jar.minY + 25 * s + font.size * 0.5
            circle(c, l.jar.minX + 22 * s, cy, 11.5 * s, R.color(0xFF4A30))
            line(c, [CGPoint(x: l.jar.minX + 18 * s, y: cy), CGPoint(x: l.jar.minX + 21 * s, y: cy + 3 * s),
                     CGPoint(x: l.jar.minX + 26 * s, y: cy - 2 * s)], R.white, 1.5 * s)
            R.draw(c, font, money, l.jar.minX + 42 * s, l.jar.minY + 25 * s + font.size, R.white)
            R.draw(c, font, "poured", l.width / 2 - font.line("poured").width / 2,
                   l.jar.minY + 25 * s + font.size * 2.12, R.white)
            R.draw(c, font, "into Sora", l.width / 2 - font.line("into Sora").width / 2,
                   l.jar.minY + 25 * s + font.size * 3.24, R.white)
            return
        }
        let cy = l.headerY + 22 * s, maxW = l.x(339) - l.x(63) - 8 * s
        let pillW = min(maxW, font.line(text).width + 52 * s)
        let titleW = fonts.title.line("Sora, spring 2027").width
        let w = titleW + 28 * s + (pillW - titleW - 28 * s) * e, h = (30 + 10 * e) * s
        let rect = CGRect(x: l.width / 2 - w / 2, y: cy - h / 2, width: w, height: h)
        var fill = context; fill.opacity *= M.segment(e, 0, 0.35)
        let path = rounded(rect, h / 2)
        shadow(fill, path, 0, 6 * s, 10 * s, R.color(0x15202C, (0.16 * e * 1000).rounded() / 1000))
        fill.fill(path, with: .color(R.color(0x2E3844)))
        let content = M.segment(e, 0.6, 1), left = l.width / 2 - pillW / 2
        var c = context; c.opacity *= content; c.translateBy(x: 0, y: reduced ? 0 : 5 * s * (1 - content))
        circle(c, left + 20 * s, cy, (reduced ? 11 : 6 + 5 * content) * s, R.color(0xFF4A30))
        line(c, [CGPoint(x: left + 16 * s, y: cy), CGPoint(x: left + 19 * s, y: cy + 3 * s),
                 CGPoint(x: left + 24 * s, y: cy - 2 * s)], R.white, 1.5 * s)
        R.draw(c, font, text, left + 38 * s, cy + font.size * 0.36, R.white)
    }
}
