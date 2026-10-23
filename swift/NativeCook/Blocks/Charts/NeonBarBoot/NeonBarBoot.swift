import SwiftUI

/// One set of figures the card can show: the total, and the bars as fractions of the tallest.
struct NeonBarBootSet {
    var total: Double
    var bars: [Double]

    static let samples = [
        NeonBarBootSet(total: 29924.95, bars: [0.35, 0.63, 0.9, 0.8, 0.55, 0.62, 0.4, 0.62, 0.76, 0.82, 0.6, 0.55, 0.7, 0.78, 0.66, 0.54, 0.45]),
        NeonBarBootSet(total: 31208.4, bars: [0.48, 0.52, 0.71, 0.93, 0.84, 0.58, 0.66, 0.5, 0.69, 0.88, 0.95, 0.72, 0.6, 0.67, 0.81, 0.74, 0.62]),
        NeonBarBootSet(total: 27412.1, bars: [0.6, 0.44, 0.52, 0.68, 0.73, 0.49, 0.38, 0.57, 0.64, 0.53, 0.71, 0.86, 0.79, 0.58, 0.47, 0.6, 0.69]),
    ]
}

private enum K {
    static let background = Color(hex: 0x0C0C10)
    static let spotlight = Color.rgba(74, 47, 166, 0.55)
    static let spotlightOut = Color.rgba(74, 47, 166, 0)
    static let cardFill = Color.rgba(255, 255, 255, 0.03)
    static let cardEdge = Color.rgba(91, 69, 201, 0.45)
    static let cardEdgeBright = Color.rgba(140, 110, 255, 0.75)
    static let cardGlow = Color.rgba(110, 70, 230, 0.22)
    static let cardGlowOut = Color.rgba(110, 70, 230, 0)
    static let barTop = Color(hex: 0x8A5BFF)
    static let barMiddle = Color(hex: 0x5B35D6)
    static let barTail = Color.rgba(91, 53, 214, 0)
    // React Native sets the glow paint's alpha outright, so only the colour of BAR_GLOW is used.
    static let barGlow = Color.rgba(160, 120, 255, 1)
    static let sweep = Color.rgba(255, 255, 255, 0.1)
    static let violet = Color(hex: 0x7B4DE0)
    static let frost = Color.rgba(255, 255, 255, 0.25)
    static let caption = Color(hex: 0xA9A6C0)
    static let ink = Color.white
    static let valueGlow = Color.rgba(157, 123, 255, 0.45)

    // Layout, in points.
    static let cardWidth = 345.0
    static let cardHeight = 380.0
    static let cardRadius = 24.0
    static let cardPadding = 28.0
    static let barWidth = 4.0
    static let barTallest = 190.0
    // The bars' last share fades into the card.
    static let barFade = 0.15
    static let square = 80.0
    static let squareRadius = 18.0
    // Where the frosted square rests over the violet one.
    static let overlap = 24.0
    static let frostBlur = 12.0
    static let sweepWidth = 60.0
    // How bright the tip of a bar lights up when it arrives.
    static let tipGlow = 0.45
    static let spotRadius = 330.0
    // Skia blur sigmas of the bars' haze and the tips' glow.
    static let hazeBlur = 4.0
    static let tipBlur = 5.0
    static let hazeAlpha = 0.35
    static let valueSize = 30.0
    static let valueLine = 38.0
    // A text shadow radius of 10 in React Native is a blur of about 5 in SwiftUI.
    static let valueGlowRadius = 5.0

    // Motion, in seconds since the boot started.
    static let wobbleFor = 0.8
    static let riseAt = 0.8
    static let riseFor = 0.6
    static let cardAt = 0.8
    static let cardFor = 0.6
    static let cardDrop = 120.0
    static let growAt = 1.4
    static let growFor = 0.55
    static let growGap = 0.035
    // The bars overshoot their height by this share as they arrive.
    static let overshoot = 0.03
    static let numberAt = 1.4
    static let sweepAt = 3.0
    static let sweepFor = 0.9
    // Breathing on the bar heights once they stand: amplitude, period, and the lag from bar to bar.
    static let breath = 0.03
    static let breathPeriod = 2.4
    static let breathLag = 0.12
    // A refresh moves every bar to its new height, a little after its left neighbour.
    static let refreshFor = 0.7
    static let refreshGap = 0.02
    // Seconds since the power-down started: bars sink right to left, the card drops, the squares return.
    static let sinkFor = 0.35
    static let sinkGap = 0.025
    static let cardOutAt = 0.35
    static let cardOutFor = 0.45
    static let returnAt = 0.6
    static let returnFor = 0.65
    static let downDone = returnAt + returnFor

    // The autoPlay timeline, in seconds from the start of each loop.
    static let loop = 9.0
    static let boot = 0.8
    static let refresh = 5.0
    static let powerDown = 7.4
    // Seconds after the last touch before the screen powers down and the loop starts again.
    static let userHold = 3.0
    // Without the loop the screen boots once, this long after it opens.
    static let firstBoot = 0.4

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }
    static func easeOut(_ t: Double) -> Double { 1 - pow(1 - clamp01(t), 3) }
    static func easeIn(_ t: Double) -> Double { pow(clamp01(t), 3) }
    static func easeInOut(_ t: Double) -> Double {
        let x = clamp01(t)
        return x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    /// Ease out with a small overshoot past 1 before settling.
    static func arrive(_ t: Double, _ overshoot: Double) -> Double {
        let x = clamp01(t)
        let s = 1.70158 * (overshoot / 0.1)
        return 1 + (s + 1) * pow(x - 1, 3) + s * pow(x - 1, 2)
    }

    /// A kick that starts and ends at 0: a damped swing, as from a tap.
    static func swing(_ t: Double) -> Double {
        if t <= 0 || t >= wobbleFor { return 0 }
        return exp(-5 * t) * sin(t * tau * 2.2)
    }

    static func money(_ value: Double, _ currency: String) -> String {
        let cents = Int((value * 100).rounded())
        let whole = String(cents / 100)
        var grouped = ""
        for (i, character) in whole.enumerated() {
            if i > 0 && (whole.count - i) % 3 == 0 { grouped.append(",") }
            grouped.append(character)
        }
        return "\(currency)\(grouped).\(String(format: "%02d", cents % 100))"
    }
}

/// When the boot, the last refresh and the power-down began; everything on screen is drawn from these.
private struct NeonBarBootRun {
    var bootAt: Date?
    var refreshAt: Date?
    var downAt: Date?
    var fromSet = 0
    var toSet = 0
}

/// A dashboard boot: two app-icon squares wobble and fly up, a glass card rises with its border
/// drawing round, the total counts up digit by digit and seventeen neon bars grow in a wave, then
/// breathe. Tapping the card refreshes it with the next set of figures; tapping the squares boots it.
struct NeonBarBoot: View {
    var title = "Total Sales"
    var currency = "$"
    var sets = NeonBarBootSet.samples
    /// Boots, refreshes once and powers down by itself every `loop` seconds.
    var autoPlay = false
    /// Called with the set the card moves to on a refresh.
    var onRefresh: ((NeonBarBootSet, Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    @State private var run = NeonBarBootRun()
    @State private var odometer = NeonBarBootOdometer()
    @State private var touches = 0
    @State private var lastTouch = Date.distantPast
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private struct LoopKey: Equatable {
        var autoPlay: Bool
        var touches: Int
    }

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            TimelineView(.animation(paused: run.bootAt == nil)) { timeline in
                let date = timeline.date
                let card = cardRect(size)
                let shift = cardShift(date)
                ZStack(alignment: .topLeading) {
                    Canvas { c, size in draw(c, size, date) }
                    VStack(alignment: .leading, spacing: 0) {
                        Text(title)
                            .font(.system(size: 13))
                            .foregroundStyle(K.caption)
                            .frame(height: 18)
                            .padding(.bottom, 2)
                        odometer.view(at: date, font: .system(size: K.valueSize, weight: .light), lineHeight: K.valueLine, color: K.ink)
                            .shadow(color: K.valueGlow, radius: K.valueGlowRadius)
                    }
                    .fixedSize()
                    .offset(x: card.minX + K.cardPadding, y: card.minY + K.cardPadding + shift)
                    .opacity(cardOpacity(date))
                    .allowsHitTesting(false)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
        }
        .ignoresSafeArea()
        .background(K.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .contentShape(Rectangle())
        .onTapGesture { press() }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(title) \(K.money(sets[run.toSet].total, currency))")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { press() }
        .onAppear { haptic.prepare() }
        .task(id: LoopKey(autoPlay: autoPlay, touches: touches)) { await loop() }
        // A finished power-down leaves the screen at rest.
        .task(id: run.downAt) {
            guard let downAt = run.downAt else { return }
            do {
                try await wait(until: downAt.addingTimeInterval(K.downDone))
                settle(.now)
            } catch {}
        }
    }

    // MARK: Actions

    private func boot(_ date: Date) {
        guard run.bootAt == nil else { return }
        run = NeonBarBootRun(bootAt: date)
        odometer.show(K.money(sets[0].total, currency), at: date, delay: K.numberAt, fresh: true, still: still)
    }

    private func refresh(_ date: Date, byHand: Bool) {
        guard let bootAt = run.bootAt, run.downAt == nil, date.timeIntervalSince(bootAt) >= K.growAt else { return }
        run.fromSet = run.toSet
        run.toSet = (run.toSet + 1) % sets.count
        run.refreshAt = date
        odometer.show(K.money(sets[run.toSet].total, currency), at: date, delay: 0, fresh: false, still: still)
        if byHand {
            haptic.impactOccurred()
            haptic.prepare()
        }
        onRefresh?(sets[run.toSet], run.toSet)
    }

    private func powerDown(_ date: Date) {
        guard run.bootAt != nil, run.downAt == nil else { return }
        run.downAt = date
    }

    private func settle(_ date: Date) {
        if let downAt = run.downAt, date.timeIntervalSince(downAt) >= K.downDone { run = NeonBarBootRun() }
    }

    /// A tap on the squares boots the screen; a tap on the card refreshes it. Either takes over from the loop.
    private func press() {
        let now = Date.now
        settle(now)
        if run.bootAt == nil { boot(now) } else { refresh(now, byHand: true) }
        lastTouch = now
        touches += 1
    }

    private func wait(until date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    /// Each step is stamped with its time on the loop, not the time the task woke, so every loop is identical.
    private func loop() async {
        do {
            if !autoPlay {
                if touches == 0 {
                    let at = Date.now.addingTimeInterval(K.firstBoot)
                    try await wait(until: at)
                    boot(at)
                }
                return
            }
            var start = Date.now
            if touches > 0 {
                let down = lastTouch.addingTimeInterval(K.userHold)
                try await wait(until: down)
                powerDown(down)
                start = down.addingTimeInterval(K.downDone + 0.2)
                try await wait(until: start)
                settle(start)
            }
            while true {
                try await wait(until: start.addingTimeInterval(K.boot))
                boot(start.addingTimeInterval(K.boot))
                try await wait(until: start.addingTimeInterval(K.refresh))
                refresh(start.addingTimeInterval(K.refresh), byHand: false)
                try await wait(until: start.addingTimeInterval(K.powerDown))
                powerDown(start.addingTimeInterval(K.powerDown))
                start = start.addingTimeInterval(K.loop)
                try await wait(until: start)
                settle(start)
            }
        } catch {}
    }

    // MARK: Motion, sampled from the date

    private func sinceBoot(_ date: Date) -> Double { run.bootAt.map { date.timeIntervalSince($0) } ?? -1 }
    private func sinceDown(_ date: Date) -> Double { run.downAt.map { date.timeIntervalSince($0) } ?? -1 }

    private func cardIn(_ date: Date) -> Double {
        let b = sinceBoot(date)
        return b < 0 ? 0 : K.easeOut((b - K.cardAt) / K.cardFor)
    }

    private func cardOut(_ date: Date) -> Double {
        let d = sinceDown(date)
        return d < 0 ? 0 : K.easeIn((d - K.cardOutAt) / K.cardOutFor)
    }

    private func cardShift(_ date: Date) -> Double {
        still ? 0 : K.cardDrop * (1 - cardIn(date)) + K.cardDrop * 1.3 * cardOut(date)
    }

    private func cardOpacity(_ date: Date) -> Double { cardIn(date) * (1 - cardOut(date)) }

    private func cardRect(_ size: CGSize) -> CGRect {
        let width = min(K.cardWidth, size.width - 48)
        let height = min(K.cardHeight, size.height * 0.5)
        return CGRect(x: (size.width - width) / 2, y: size.height / 2 - height / 2 + 24, width: width, height: height)
    }

    // MARK: Drawing

    private func draw(_ c: GraphicsContext, _ size: CGSize, _ date: Date) {
        let card = cardRect(size)
        let centre = CGPoint(x: size.width / 2, y: size.height / 2)
        let b = sinceBoot(date)
        let d = sinceDown(date)

        let whole = Path(CGRect(origin: .zero, size: size))
        let spot = GraphicsContext.Shading.radialGradient(
            Gradient(colors: [K.spotlight, K.spotlightOut]),
            center: CGPoint(x: size.width / 2, y: 0), startRadius: 0, endRadius: K.spotRadius
        )
        c.fill(whole, with: .color(K.background))
        c.fill(whole, with: spot)

        drawSquares(c, centre, b, d, whole, spot)
        drawCard(c, card, date, b)
        drawBars(c, card, date, b, d)
    }

    private func drawSquares(_ c: GraphicsContext, _ centre: CGPoint, _ b: Double, _ d: Double, _ whole: Path, _ spot: GraphicsContext.Shading) {
        let away = -(centre.y + K.square * 2)
        let y: Double
        if still {
            y = 0
        } else if d >= 0 {
            y = away * (1 - K.arrive((d - K.returnAt) / K.returnFor, 0.08))
        } else if b < 0 {
            y = 0
        } else {
            y = away * K.easeIn((b - K.riseAt) / K.riseFor)
        }
        let opacity: Double
        if !still {
            opacity = 1
        } else if d >= 0 {
            opacity = K.clamp01((d - K.returnAt) / 0.3)
        } else {
            opacity = b < 0 ? 1 : 1 - K.clamp01((b - K.riseAt) / 0.3)
        }
        guard opacity > 0 else { return }
        let kick = still || d >= 0 ? 0 : K.swing(b)

        let violetOrigin = CGPoint(x: centre.x - K.overlap / 2, y: centre.y - K.overlap / 2)
        let violetRect = CGRect(x: violetOrigin.x - K.square / 2, y: violetOrigin.y - K.square / 2, width: K.square, height: K.square)
        let frostOrigin = CGPoint(x: centre.x + K.overlap / 2, y: centre.y + K.overlap / 2)
        let frostRect = CGRect(x: frostOrigin.x - K.square / 2, y: frostOrigin.y - K.square / 2, width: K.square, height: K.square)
        let shape = { (rect: CGRect) in Path(roundedRect: rect, cornerRadius: K.squareRadius) }

        // Skia's group transforms, applied about each square's own centre.
        var violet = c
        violet.opacity *= opacity
        violet.translateBy(x: violetOrigin.x, y: violetOrigin.y + y)
        violet.rotate(by: .degrees(-18 * kick))
        violet.scaleBy(x: 1 - 0.15 * kick, y: 1 - 0.15 * kick)
        violet.translateBy(x: -violetOrigin.x, y: -violetOrigin.y)
        let violetTransform = violet.transform
        violet.fill(shape(violetRect), with: .color(K.violet))

        var frost = c
        frost.opacity *= opacity
        frost.translateBy(x: frostOrigin.x + 20 * kick, y: frostOrigin.y + y * 1.08 + 20 * kick)
        frost.rotate(by: .degrees(22 * kick))
        frost.translateBy(x: -frostOrigin.x, y: -frostOrigin.y)
        // A backdrop blur: what lies under the square, drawn again blurred inside its outline.
        var glass = frost
        glass.clip(to: shape(frostRect))
        glass.transform = .identity
        glass.drawLayer { layer in
            layer.addFilter(.blur(radius: K.frostBlur))
            layer.fill(whole, with: .color(K.background))
            layer.fill(whole, with: spot)
            layer.transform = violetTransform
            layer.fill(shape(violetRect), with: .color(K.violet))
        }
        frost.fill(shape(frostRect), with: .color(K.frost))
    }

    /// The card's outline as Skia draws a rounded rect: from the foot of the left side, clockwise.
    private func outline(_ rect: CGRect) -> Path {
        let r = K.cardRadius
        var path = Path()
        path.move(to: CGPoint(x: rect.minX, y: rect.maxY - r))
        path.addLine(to: CGPoint(x: rect.minX, y: rect.minY + r))
        path.addArc(center: CGPoint(x: rect.minX + r, y: rect.minY + r), radius: r, startAngle: .degrees(180), endAngle: .degrees(270), clockwise: false)
        path.addLine(to: CGPoint(x: rect.maxX - r, y: rect.minY))
        path.addArc(center: CGPoint(x: rect.maxX - r, y: rect.minY + r), radius: r, startAngle: .degrees(270), endAngle: .degrees(0), clockwise: false)
        path.addLine(to: CGPoint(x: rect.maxX, y: rect.maxY - r))
        path.addArc(center: CGPoint(x: rect.maxX - r, y: rect.maxY - r), radius: r, startAngle: .degrees(0), endAngle: .degrees(90), clockwise: false)
        path.addLine(to: CGPoint(x: rect.minX + r, y: rect.maxY))
        path.addArc(center: CGPoint(x: rect.minX + r, y: rect.maxY - r), radius: r, startAngle: .degrees(90), endAngle: .degrees(180), clockwise: false)
        path.closeSubpath()
        return path
    }

    private func drawCard(_ c: GraphicsContext, _ card: CGRect, _ date: Date, _ b: Double) {
        let opacity = cardOpacity(date)
        guard opacity > 0 else { return }
        var g = c
        g.opacity *= opacity
        g.translateBy(x: 0, y: cardShift(date))

        let shape = Path(roundedRect: card, cornerRadius: K.cardRadius)
        var inside = g
        inside.clip(to: shape)
        inside.fill(shape, with: .color(K.cardFill))
        inside.fill(Path(card), with: .radialGradient(
            Gradient(colors: [K.cardGlow, K.cardGlowOut]),
            center: CGPoint(x: card.midX, y: card.minY), startRadius: 0, endRadius: card.width * 0.7
        ))
        if !still, b >= K.sweepAt, b <= K.sweepAt + K.sweepFor {
            let x = card.minX - K.sweepWidth + (card.width + K.sweepWidth) * K.easeInOut((b - K.sweepAt) / K.sweepFor)
            inside.fill(Path(CGRect(x: x, y: card.minY, width: K.sweepWidth, height: card.height)), with: .linearGradient(
                Gradient(colors: [K.sweep.opacity(0), K.sweep, K.sweep.opacity(0)]),
                startPoint: CGPoint(x: x, y: 0), endPoint: CGPoint(x: x + K.sweepWidth, y: 0)
            ))
        }

        let border = still ? cardIn(date) : (b < 0 ? 0 : K.easeInOut((b - K.cardAt) / K.cardFor))
        guard border > 0 else { return }
        g.stroke(outline(card).trimmedPath(from: 0, to: border), with: .linearGradient(
            Gradient(stops: [
                .init(color: K.cardEdgeBright, location: 0),
                .init(color: K.cardEdge, location: 0.45),
                .init(color: K.cardEdge, location: 1),
            ]),
            startPoint: CGPoint(x: card.minX, y: card.minY), endPoint: CGPoint(x: card.maxX, y: card.maxY)
        ), lineWidth: 1)
    }

    private func drawBars(_ c: GraphicsContext, _ card: CGRect, _ date: Date, _ b: Double, _ d: Double) {
        guard b >= 0 else { return }
        let scale = card.height / K.cardHeight
        let barsLeft = card.minX + K.cardPadding
        let pitch = (card.width - K.cardPadding * 2) / 17
        let base = card.maxY - K.cardPadding
        let tallest = K.barTallest * scale
        let from = sets[run.fromSet].bars
        let to = sets[run.toSet].bars
        let count = sets[0].bars.count
        let r = run.refreshAt.map { date.timeIntervalSince($0) } ?? -1
        let breathIn = K.clamp01((b - K.growAt - 1.2) / 0.6)
        let shown = still ? K.clamp01((b - K.growAt) / 0.3) : 1
        // Under reduced motion the bars fade together; otherwise they all draw at full alpha.
        let fade = d < 0 || !still ? 1 : 1 - K.clamp01((d - K.cardOutAt) / 0.3)
        let alpha = shown * fade
        guard alpha > 0 else { return }

        var bars: [(rect: CGRect, shading: GraphicsContext.Shading)] = []
        var tips: [(point: CGPoint, lit: Double)] = []
        for i in 0..<count {
            if !still && b < K.growAt + Double(i) * K.growGap { continue }
            let grown = still ? 1 : K.arrive((b - K.growAt - Double(i) * K.growGap) / K.growFor, K.overshoot)
            let k = r < 0 || still ? 1 : K.arrive((r - Double(i) * K.refreshGap) / K.refreshFor, 0.05)
            let target = from[i] + (to[i] - from[i]) * k
            let breath = still ? 1 : 1 + K.breath * breathIn * sin(tau * (b - K.growAt - Double(i) * K.breathLag) / K.breathPeriod)
            let sink = d < 0 ? 1 : (still ? fade : 1 - K.easeIn((d - Double(count - 1 - i) * K.sinkGap) / K.sinkFor))
            let h = tallest * target * grown * breath * (still ? 1 : sink)
            if h < 1 { continue }
            let x = barsLeft + pitch * (Double(i) + 0.5)
            let top = base - h
            let shading = GraphicsContext.Shading.linearGradient(
                Gradient(stops: [
                    .init(color: K.barTop, location: 0),
                    .init(color: K.barMiddle, location: 1 - K.barFade * 2),
                    .init(color: K.barTail, location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: base)
            )
            bars.append((CGRect(x: x, y: top, width: 0, height: h), shading))
            // The top lights up as the bar arrives.
            let lit = K.clamp01((grown - 0.85) / 0.15) * alpha * (d < 0 ? 1 : sink)
            if lit > 0 { tips.append((CGPoint(x: x, y: top + K.barWidth / 2), lit)) }
        }

        // Skia's solid blur: the shape itself, with a blurred copy around it.
        let haze = { (layer: inout GraphicsContext) in
            for bar in bars {
                let rect = CGRect(x: bar.rect.minX - K.barWidth, y: bar.rect.minY, width: K.barWidth * 2, height: bar.rect.height)
                layer.fill(Path(roundedRect: rect, cornerRadius: K.barWidth), with: bar.shading)
            }
        }
        c.drawLayer { layer in
            layer.opacity = K.hazeAlpha * alpha
            layer.drawLayer { blurred in
                blurred.addFilter(.blur(radius: K.hazeBlur))
                haze(&blurred)
            }
            haze(&layer)
        }

        var crisp = c
        crisp.opacity *= alpha
        for bar in bars {
            let rect = CGRect(x: bar.rect.minX - K.barWidth / 2, y: bar.rect.minY, width: K.barWidth, height: bar.rect.height)
            crisp.fill(Path(roundedRect: rect, cornerRadius: K.barWidth / 2), with: bar.shading)
        }

        guard !tips.isEmpty else { return }
        let glow = { (layer: inout GraphicsContext) in
            for tip in tips {
                let dot = CGRect(x: tip.point.x - K.barWidth, y: tip.point.y - K.barWidth, width: K.barWidth * 2, height: K.barWidth * 2)
                layer.fill(Path(ellipseIn: dot), with: .color(K.barGlow.opacity(K.tipGlow * tip.lit)))
            }
        }
        c.drawLayer { layer in
            layer.drawLayer { blurred in
                blurred.addFilter(.blur(radius: K.tipBlur))
                glow(&blurred)
            }
            glow(&layer)
        }
    }
}
