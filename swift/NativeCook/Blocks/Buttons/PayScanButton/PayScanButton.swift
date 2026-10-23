import SwiftUI

/// Lavender "PAY" pill. A tap folds it into a round button where the card icon becomes a face-scan
/// frame, a scan line sweeps the face, then the pill widens again to say "COMPLETE" and resets.
struct PayScanButton: View {
    var label = "PAY"
    /// Shown once the scan has finished.
    var doneLabel = "COMPLETE"
    /// Replays pay → scan → complete by itself with a pause between runs.
    var autoPlay = false
    /// Called when a run reaches the "complete" state.
    var onComplete: () -> Void = {}
    var action: () -> Void = {}

    @State private var taps = 0

    var body: some View {
        Button {
            taps += 1
            action()
        } label: {
            Text(label)
        }
        .buttonStyle(Style(label: label, doneLabel: doneLabel, autoPlay: autoPlay, taps: taps, onComplete: onComplete))
        .accessibilityLabel(label)
    }

    private struct Style: ButtonStyle {
        let label: String
        let doneLabel: String
        let autoPlay: Bool
        let taps: Int
        let onComplete: () -> Void

        func makeBody(configuration: Configuration) -> some View {
            Surface(
                label: label, doneLabel: doneLabel, autoPlay: autoPlay, taps: taps,
                isPressed: configuration.isPressed, onComplete: onComplete
            )
        }
    }
}

private enum Metrics {
    static let width = 180.0
    static let height = 60.0
    /// The canvas overflows the button by this much on every side to fit the shadow.
    static let bleed = 30.0
    static let autoPlayPause = 1.5

    // Card icon: centre offset from the pill's centre and half-size; it morphs into the scan frame.
    static let cardDX = -29.5
    static let cardHW = 22.5
    static let cardHH = 14.5
    static let cardR = 3.5
    static let frameHalf = 13.5
    static let frameR = 5.0
    static let frameArm = 7.5
    static let labelLeft = 97.0
    static let ink = Color(hex: 0xf1edff)

    // One run, in seconds. `Frame(END)` equals `Frame(0)`, so runs chain without a reset.
    static let tScan = 1.45
    static let tScanEnd = 3.85
    static let tDone = 4.3
    static let tReturn = 6.6
    static let end = 7.4
}

private func seg(_ t: Double, _ a: Double, _ b: Double) -> Double {
    min(1, max(0, (t - a) / (b - a)))
}

private func easeOut(_ x: Double) -> Double {
    1 - (1 - x) * (1 - x) * (1 - x)
}

private func easeInOut(_ x: Double) -> Double {
    x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
}

/// Everything the button shows at time `t` of a run. Same numbers as the React Native version.
private struct Frame {
    var w, pay, done, detail, morph, bracket, face, icon, outline, scan, scanY: Double

    init(_ t: Double, still: Bool) {
        let away = t < Metrics.tReturn
        let first = t < 3
        // Pill width: shrinks to a circle for the scan, widens again for the result.
        w = first
            ? Metrics.width + (Metrics.height - Metrics.width) * easeInOut(seg(t, 0.45, 0.95))
            : Metrics.height + (Metrics.width - Metrics.height) * easeInOut(seg(t, 4.1, 4.6))
        // Label wipes: fraction of the text revealed from its left edge.
        pay = first ? 1 - easeOut(seg(t, 0, 0.35)) : easeOut(seg(t, 6.75, 7.15))
        done = easeOut(seg(t, Metrics.tDone + 0.1, 4.85)) * (1 - easeOut(seg(t, 6.3, 6.65)))
        // Card stripe and chip.
        detail = first ? 1 - seg(t, 0, 0.25) : easeOut(seg(t, 6.95, 7.3))
        // Card → centred square, then the square's sides open into four brackets.
        morph = away ? easeInOut(seg(t, 0.12, 0.6)) : 0
        bracket = away ? easeOut(seg(t, 0.9, 1.25)) : 0
        face = away ? easeOut(seg(t, 1.1, Metrics.tScan)) : 0
        icon = away ? 1 - seg(t, 4.0, 4.25) : 1
        // The card outline draws itself back in at the end.
        outline = away ? 1 : easeInOut(seg(t, Metrics.tReturn, 7.1))
        scan = seg(t, Metrics.tScan, Metrics.tScan + 0.2) * (1 - seg(t, Metrics.tScanEnd - 0.15, Metrics.tScanEnd))
        scanY = still ? 0 : -cos(tau * (t - Metrics.tScan) / 1.25) * 8
    }
}

private struct Surface: View {
    let label: String
    let doneLabel: String
    let autoPlay: Bool
    let taps: Int
    let isPressed: Bool
    let onComplete: () -> Void

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var start: Date?

    var body: some View {
        TimelineView(.animation(paused: start == nil)) { timeline in
            let t = start.map { min(max(timeline.date.timeIntervalSince($0), 0), Metrics.end) } ?? 0
            let f = Frame(t, still: reduceMotion)

            ZStack {
                Canvas { context, _ in draw(context, f) }
                    .padding(-Metrics.bleed)
                    .allowsHitTesting(false)

                wipe(label, reveal: f.pay)
                    .padding(.leading, Metrics.labelLeft)
                    .frame(maxWidth: .infinity, alignment: .leading)
                wipe(doneLabel, reveal: f.done)
            }
            .frame(width: Metrics.width, height: Metrics.height)
        }
        .contentShape(Capsule())
        .scaleEffect(isPressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: isPressed)
        .sensoryFeedback(.impact(weight: .light), trigger: isPressed) { _, pressed in pressed }
        .onChange(of: taps) {
            Task { await run() }
        }
        .task(id: autoPlay) {
            guard autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .seconds(Metrics.autoPlayPause))
                if Task.isCancelled { return }
                await run()
            }
        }
    }

    /// A label wiped in and out from its left edge.
    private func wipe(_ text: String, reveal: Double) -> some View {
        Text(text)
            .font(.system(size: 19, weight: .bold))
            .kerning(0.6)
            .lineLimit(1)
            .fixedSize()
            .foregroundStyle(Metrics.ink)
            .mask { Rectangle().scaleEffect(x: reveal, anchor: .leading) }
            .accessibilityHidden(true)
    }

    /// One pay → scan → complete run. It is a sequence of states, not decoration, so it also plays
    /// with reduced motion (where only the sweeping scan line is frozen).
    private func run() async {
        guard start == nil else { return }
        start = .now
        try? await Task.sleep(for: .seconds(Metrics.tDone))
        if !Task.isCancelled { onComplete() }
        try? await Task.sleep(for: .seconds(Metrics.end - Metrics.tDone))
        start = nil
    }

    private func draw(_ context: GraphicsContext, _ f: Frame) {
        let cx = Metrics.bleed + Metrics.width / 2
        let cy = Metrics.bleed + Metrics.height / 2
        let pill = CGRect(x: cx - f.w / 2, y: Metrics.bleed, width: f.w, height: Metrics.height)

        var shadow = context
        shadow.addFilter(.blur(radius: 9))
        shadow.fill(capsule(pill.offsetBy(dx: 5, dy: 8)), with: .color(.rgba(122, 116, 214, 0.34)))
        context.fill(
            capsule(pill),
            with: .linearGradient(
                Gradient(colors: [Color(hex: 0xb3a7fa), Color(hex: 0xab9ff6)]),
                startPoint: CGPoint(x: 0, y: pill.minY), endPoint: CGPoint(x: 0, y: pill.maxY)
            )
        )

        let ink = GraphicsContext.Shading.color(Metrics.ink)
        let line = StrokeStyle(lineWidth: 1.7, lineCap: .round, lineJoin: .round)

        // Card outline / scan frame: four corners whose arms meet (closed shape) or pull back (brackets).
        let x = cx + Metrics.cardDX * (1 - f.morph)
        let hw = Metrics.cardHW + (Metrics.frameHalf - Metrics.cardHW) * f.morph
        let hh = Metrics.cardHH + (Metrics.frameHalf - Metrics.cardHH) * f.morph
        let r = Metrics.cardR + (Metrics.frameR - Metrics.cardR) * f.morph
        let ax = hw + (Metrics.frameArm - hw) * f.bracket
        let ay = hh + (Metrics.frameArm - hh) * f.bracket
        var outline = Path()
        corner(&outline, x + hw, cy - hh, -1, 1, ax, ay, r, horizontalFirst: true)
        corner(&outline, x + hw, cy + hh, -1, -1, ax, ay, r, horizontalFirst: false)
        corner(&outline, x - hw, cy + hh, 1, -1, ax, ay, r, horizontalFirst: true)
        corner(&outline, x - hw, cy - hh, 1, 1, ax, ay, r, horizontalFirst: false)
        if f.icon > 0, f.outline > 0 {
            var layer = context
            layer.opacity = f.icon
            layer.stroke(outline.trimmedPath(from: 0, to: f.outline), with: ink, style: line)
        }

        if f.detail > 0 {
            let left = cx + Metrics.cardDX - Metrics.cardHW
            let y = cy - Metrics.cardHH + 9.5
            var stripe = Path()
            stripe.move(to: CGPoint(x: left, y: y))
            stripe.addLine(to: CGPoint(x: left + Metrics.cardHW * 2 * f.detail, y: y))
            var layer = context
            layer.opacity = min(1, f.detail * 3)
            layer.stroke(stripe, with: ink, lineWidth: 1.7)

            let chip = CGRect(x: cx + Metrics.cardDX + Metrics.cardHW - 14.5, y: cy + Metrics.cardHH - 11, width: 9.5, height: 6)
            var chipLayer = context
            chipLayer.opacity = seg(f.detail, 0.5, 1)
            chipLayer.stroke(Path(roundedRect: chip, cornerRadius: 1.8), with: ink, lineWidth: 1.5)
        }

        if f.face * f.icon > 0 {
            var face = Path()
            face.move(to: CGPoint(x: cx - 5.5, y: cy - 4.5))
            face.addLine(to: CGPoint(x: cx - 5.5, y: cy - 0.9))
            face.move(to: CGPoint(x: cx + 5.5, y: cy - 4.5))
            face.addLine(to: CGPoint(x: cx + 5.5, y: cy - 0.9))
            face.move(to: CGPoint(x: cx - 4, y: cy + 3.6))
            face.addQuadCurve(to: CGPoint(x: cx + 4, y: cy + 3.6), control: CGPoint(x: cx, y: cy + 7.6))
            var layer = context.scaled(0.6 + 0.4 * f.face, about: CGPoint(x: cx, y: cy))
            layer.opacity = f.face * f.icon
            layer.stroke(face, with: ink, style: line)
        }

        if f.scan > 0 {
            var scan = Path()
            scan.move(to: CGPoint(x: cx - 10.5, y: cy + f.scanY))
            scan.addLine(to: CGPoint(x: cx + 10.5, y: cy + f.scanY))
            var layer = context
            layer.opacity = f.scan
            layer.stroke(scan, with: ink, style: line)
        }
    }

    /// One rounded corner at (px, py) with arms `ax`/`ay` long pointing towards (dx, dy).
    private func corner(
        _ path: inout Path, _ px: Double, _ py: Double, _ dx: Double, _ dy: Double,
        _ ax: Double, _ ay: Double, _ r: Double, horizontalFirst: Bool
    ) {
        let c = r * (1 - 0.5523)
        let h = CGPoint(x: px + dx * ax, y: py)
        let hr = CGPoint(x: px + dx * r, y: py)
        let hc = CGPoint(x: px + dx * c, y: py)
        let v = CGPoint(x: px, y: py + dy * ay)
        let vr = CGPoint(x: px, y: py + dy * r)
        let vc = CGPoint(x: px, y: py + dy * c)
        if horizontalFirst {
            path.move(to: h)
            path.addLine(to: hr)
            path.addCurve(to: vr, control1: hc, control2: vc)
            path.addLine(to: v)
        } else {
            path.move(to: v)
            path.addLine(to: vr)
            path.addCurve(to: hr, control1: vc, control2: hc)
            path.addLine(to: h)
        }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xe7fdff).ignoresSafeArea()
        PayScanButton(autoPlay: true)
    }
}
