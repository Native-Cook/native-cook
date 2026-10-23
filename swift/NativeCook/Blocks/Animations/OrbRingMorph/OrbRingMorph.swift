import SwiftUI
import UIKit

/// A splash sequence: a split blue orb pulses, spins, and grows into a dark glass sphere; a ring of
/// four gradient arcs draws around it, flattens into a vertical pill, and the pill opens into a card.
struct OrbRingMorph: View {
    /// The two lines under the orb.
    var title = "Your face,\nis your wallet"
    var background = Color(hex: 0x05060C)
    /// The ring's gradient, from where each arc starts to where it ends.
    var ringColors: [Color] = [Color(hex: 0x3D6BFF), Color(hex: 0x9B6BFF), Color(hex: 0xFFE3F4)]
    /// Plays the sequence by itself every `loop` seconds.
    var autoPlay = false
    /// Called when the card shows during a run started by hand.
    var onReveal: () -> Void = {}
    /// Called when a run started by hand is back at rest.
    var onDone: () -> Void = {}

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    private enum Mode { case rest, once, loop }

    @State private var mode = Mode.rest
    /// When the run's clock read 0; moved on by however long a finger held it still.
    @State private var runStart = Date.distantPast
    @State private var heldSince: Date? = nil
    @State private var downAt: Date? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// Changes when a run by hand starts, which restarts the task that follows it.
    @State private var runs = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var revealHaptic = UIImpactFeedbackGenerator(style: .soft)

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            TimelineView(.animation(paused: mode == .rest || heldSince != nil)) { timeline in
                let seq = sequence(at: timeline.date)
                let t = reduceMotion ? OrbRingMorphScene.still(seq) : seq
                ZStack(alignment: .topLeading) {
                    Canvas { context, size in
                        OrbRingMorphScene(t: t, ring: ringColors).draw(in: &context, size: size)
                    }
                    caption(at: t)
                        .position(x: size.width / 2, y: size.height * M.textY)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .opacity(reduceMotion ? OrbRingMorphScene.dip(seq) : 1)
            }
        }
        .ignoresSafeArea()
        .background(background.ignoresSafeArea())
        .contentShape(Rectangle())
        .gesture(press)
        .accessibilityElement()
        .accessibilityLabel(Text("Play the animation"))
        .accessibilityHint(Text("Hold to pause it"))
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { tap() }
        .preferredColorScheme(.dark)
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loopTask() }
        .task(id: runs) { await followRun() }
    }

    private func caption(at t: Double) -> some View {
        let shown = OrbRingMorphScene.easeOut(OrbRingMorphScene.clamp((t - M.textInAt) / M.textInFor))
        let gone = OrbRingMorphScene.easeInOut(OrbRingMorphScene.clamp((t - M.textOutAt) / M.textOutFor))
        return Text(title)
            .font(.system(size: 15, weight: .semibold))
            .lineSpacing(M.line - UIFont.systemFont(ofSize: 15, weight: .semibold).lineHeight)
            .multilineTextAlignment(.center)
            .foregroundStyle(Color.white.opacity(0.9))
            .padding(.horizontal, 32)
            .opacity(shown * (1 - gone))
            .offset(y: M.textRise * (1 - shown))
    }

    // MARK: Clock

    /// Seconds into the run at `date`: 0 at rest, held still while a finger is down.
    private func sequence(at date: Date) -> Double {
        guard mode != .rest else { return 0 }
        let seq = max(0, (heldSince ?? date).timeIntervalSince(runStart))
        if mode == .loop { return seq.truncatingRemainder(dividingBy: Self.loop) }
        return seq < Self.loop ? seq : 0
    }

    /// A touch-down starts a timer: lifted before `holdAfter` it is a tap, held longer it freezes the run.
    private var press: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard downAt == nil else { return }
                let now = Date.now
                downAt = now
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(M.holdAfter), tolerance: .zero)
                    guard downAt == now else { return }
                    hold()
                }
            }
            .onEnded { _ in
                downAt = nil
                if heldSince != nil { release() } else { tap() }
            }
    }

    private func tap() {
        touches += 1
        tapHaptic.impactOccurred()
        // A tap starts a run unless one by hand is going.
        guard mode != .once else { return }
        mode = .once
        runStart = .now
        runs += 1
    }

    private func hold() {
        touches += 1
        let now = Date.now
        // A hold during autoPlay finishes the loop's current run once and stops there.
        if mode == .loop {
            let seq = now.timeIntervalSince(runStart)
            runStart += (seq / Self.loop).rounded(.down) * Self.loop
            mode = .once
            runs += 1
        }
        if mode != .rest { heldSince = now }
    }

    private func release() {
        touches += 1
        guard let since = heldSince else { return }
        runStart += Date.now.timeIntervalSince(since)
        heldSince = nil
    }

    /// Follows a run by hand: the reveal's haptic and callback, then rest.
    private func followRun() async {
        guard mode == .once else { return }
        if sequence(at: .now) < M.cardAt {
            guard await wait(until: M.cardAt) else { return }
            revealHaptic.impactOccurred()
            onReveal()
        }
        guard await wait(until: Self.loop) else { return }
        mode = .rest
        onDone()
    }

    /// Waits for the run's clock to reach `target`; false when the task was cancelled.
    private func wait(until target: Double) async -> Bool {
        while !Task.isCancelled {
            if heldSince != nil {
                try? await Task.sleep(for: .seconds(0.05))
                continue
            }
            let left = target - max(0, Date.now.timeIntervalSince(runStart))
            if left <= 0 { return true }
            try? await Task.sleep(for: .seconds(left), tolerance: .zero)
        }
        return false
    }

    /// autoPlay: a run every `loop` seconds, from the moment the screen appeared or came back to rest.
    private func loopTask() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            while mode != .rest || heldSince != nil || downAt != nil, !Task.isCancelled {
                try? await Task.sleep(for: .seconds(0.1))
            }
        }
        guard !Task.isCancelled else { return }
        runStart = .now
        mode = .loop
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private typealias M = OrbRingMorphScene

/// Everything on screen at one moment of the run, drawn from `t` alone.
struct OrbRingMorphScene {
    let t: Double
    let ring: [Color]

    // After a touch autoPlay waits this long, and for the run to finish, before it starts over.
    static let userHold = 3.0
    // Holding a finger down this long freezes the sequence until it is lifted.
    static let holdAfter = 0.3

    // The timeline, in seconds from the start of a run. The orb rests still until pulseAt.
    static let pulseAt = 0.8
    static let pulseFor = 0.8
    static let textInAt = 1.0
    static let textInFor = 0.5
    static let shrinkAt = 1.6
    static let shrinkFor = 0.6
    static let spinAt = 1.6
    static let spinFor = 0.9
    static let haloAt = 2.0
    static let haloFor = 0.8
    static let growAt = 2.2
    static let glassFor = 0.6
    static let dotsAt = 2.8
    static let dotStagger = 0.07
    static let dotFloat = 1.4
    static let ringAt = 3.0
    static let ringStagger = 0.08
    static let ringDraw = 0.7
    // Seconds for one turn of the ring, and for one breath of its arcs.
    static let ringTurn = 4.0
    static let breath = 1.6
    static let contractAt = 4.6
    static let contractFor = 0.7
    static let textOutAt = 4.6
    static let textOutFor = 0.4
    static let dotsOutAt = 4.6
    static let dotsOutFor = 0.4
    // The pill that the ring becomes hands over to the card over `swap` seconds before cardAt.
    static let swap = 0.15
    static let cardAt = 5.3
    static let toneFor = 0.5
    static let iconAt = 5.55
    static let iconFor = 0.5
    static let iconDotAt = 5.95
    static let cardOutAt = 6.4
    static let cardOutFor = 0.6
    static let orbInAt = 7.2
    static let orbInFor = 0.8

    // One spring for the sphere's growth, the card's opening and the icon's dot.
    static let springMass = 1.0
    static let springStiffness = 170.0
    static let springDamping = 16.0

    // Sizes in points. The orb's centre sits at orbY of the screen's height, the text's at textY.
    static let orbY = 0.45
    static let textY = 0.78
    static let orb = 56.0
    static let orbSmall = 28.0
    static let sphere = 120.0
    static let pulse = 0.15
    // The orb is two half discs, slid along their seam by this share of the radius.
    static let split = 0.1
    static let seam = 80.0
    static let halo = 110.0
    static let ringRadius = 84.0
    static let ringWidth = 6.0
    static let arc = 70.0
    static let breathAmount = 0.15
    // The ring flattens into a vertical pill pillWidth wide and 2 × pillHalf + pillWidth tall.
    static let pillHalf = 45.0
    static let pillWidth = 8.0
    static let card = 140.0
    static let cardRadius = 28.0
    static let iconRadius = 26.0
    static let iconWidth = 4.0
    static let iconDot = 5.0
    static let dotStart = 62.0
    static let textRise = 8.0
    static let line = 20.0
    // Blur sigmas of the glows.
    static let orbGlow = 14.0
    static let ringGlow = 6.0
    static let cardGlow = 18.0
    static let dotGlow = 3.0

    // Each dot: angle in degrees, distance from the centre, diameter, and orbit speed in radians a second.
    static let dots: [(Double, Double, Double, Double)] = [
        (-70, 100, 3.5, 0.35),
        (-5, 112, 2.5, -0.3),
        (60, 96, 3, 0.32),
        (125, 116, 2, -0.34),
        (190, 104, 4, 0.28),
        (250, 120, 2.5, -0.3),
    ]

    // Under reduced motion the run jumps between still moments, fading through black at each jump.
    static let stills: [(Double, Double)] = [(0, 0), (1.6, 4.2), (4.6, 6.1), (6.6, 0)]
    static let dipFor = 0.2

    static let haloInner: UInt32 = 0x0D1020
    static let haloOuter: UInt32 = 0x141A36
    static let glow: UInt32 = 0x2F5BFF
    static let orbLight: [UInt32] = [0x8FB3FF, 0x2F5BFF, 0x1A3FE6]
    static let orbDark: [UInt32] = [0x5A86FF, 0x1F4BFF, 0x0B2BC9]
    static let glass: [UInt32] = [0x26305C, 0x0C1024, 0x05060C]
    static let rim: [UInt32] = [0x8FB0FF, 0x5F78FF, 0x9B6BFF]
    static let dot: UInt32 = 0x7F9BFF
    static let cardStart: UInt32 = 0x4D7BFF
    static let cardFill: UInt32 = 0x1A1D2B
    static let iconDotFill: UInt32 = 0x7FA8FF
    static let bounce: UInt32 = 0x3D6BFF

    // MARK: Curves

    static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func lerp(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func easeInOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }

    /// 0 at both ends, 1 in the middle, flat where it starts and ends.
    static func bump(_ x: Double) -> Double { x <= 0 || x >= 1 ? 0 : 0.5 - 0.5 * cos(2 * .pi * x) }

    /// A spring from 0 to 1 released at rest, `t` seconds after it starts.
    static func spring(_ t: Double) -> Double {
        guard t > 0 else { return 0 }
        let w0 = (springStiffness / springMass).squareRoot()
        let zeta = springDamping / (2 * (springStiffness * springMass).squareRoot())
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * w0 * t) * (cos(wd * t) + (zeta * w0 / wd) * sin(wd * t))
    }

    /// The moment of the run shown under reduced motion.
    static func still(_ seq: Double) -> Double {
        var shown = 0.0
        for (at, moment) in stills where seq >= at { shown = moment }
        return shown
    }

    /// The screen's opacity under reduced motion: it fades through black around each jump.
    static func dip(_ seq: Double) -> Double {
        var alpha = 1.0
        for (at, _) in stills.dropFirst() { alpha = min(alpha, clamp(abs(seq - at) / dipFor)) }
        return alpha
    }

    // MARK: Drawing

    private static func color(_ hex: UInt32, _ alpha: Double) -> Color { Color(hex: hex, opacity: clamp(alpha)) }

    private static func mix(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
        func channel(_ c: UInt32, _ shift: UInt32) -> Double { Double((c >> shift) & 0xff) }
        return .rgba(
            lerp(channel(a, 16), channel(b, 16), t),
            lerp(channel(a, 8), channel(b, 8), t),
            lerp(channel(a, 0), channel(b, 0), t),
            1
        )
    }

    /// A path through 33 points of an ellipse's arc, angles in degrees.
    private static func arcPath(_ cx: Double, _ cy: Double, _ rx: Double, _ ry: Double, from: Double, sweep: Double, close: Bool) -> Path {
        var path = Path()
        let steps = 32
        for i in 0...steps {
            let a = (from + sweep * Double(i) / Double(steps)) * .pi / 180
            let p = CGPoint(x: cx + rx * cos(a), y: cy + ry * sin(a))
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        if close { path.closeSubpath() }
        return path
    }

    private static func circle(_ cx: Double, _ cy: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: cx - r, y: cy - r, width: r * 2, height: r * 2))
    }

    private static func radial(_ x: Double, _ y: Double, _ r: Double, _ colors: [Color], _ stops: [Double]) -> GraphicsContext.Shading {
        .radialGradient(
            Gradient(stops: zip(colors, stops).map { Gradient.Stop(color: $0, location: $1) }),
            center: CGPoint(x: x, y: y), startRadius: 0, endRadius: max(r, 0.01)
        )
    }

    private static func linear(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double, _ colors: [Color]) -> GraphicsContext.Shading {
        .linearGradient(Gradient(colors: colors), startPoint: CGPoint(x: x0, y: y0), endPoint: CGPoint(x: x1, y: y1))
    }

    /// Runs `draw` in a layer blurred by `sigma`.
    private static func blurred(_ context: inout GraphicsContext, _ sigma: Double, _ draw: (inout GraphicsContext) -> Void) {
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: sigma))
            draw(&layer)
        }
    }

    /// The orb: two half discs, slid apart along their seam, that merge into a dark glass sphere.
    private static func drawOrb(_ context: inout GraphicsContext, _ cx: Double, _ cy: Double, d: Double, rot: Double, glass g: Double, alpha: Double, glow: Double) {
        guard alpha > 0.001, d > 0.5 else { return }
        let r = d / 2
        blurred(&context, orbGlow) { $0.fill(circle(cx, cy, r * 1.15), with: .color(color(Self.glow, alpha * (glow * (1 - g) + 0.3 * g)))) }

        if g < 1 {
            let seam = Self.seam + rot
            let ux = cos(seam * .pi / 180)
            let uy = sin(seam * .pi / 180)
            let slide = split * r * (1 - g)
            let a = alpha * (1 - g)
            for (k, c) in [orbLight, orbDark].enumerated() {
                let sign = k == 0 ? -1.0 : 1.0
                let hx = cx + ux * slide * sign
                let hy = cy + uy * slide * sign
                context.fill(
                    arcPath(hx, hy, r, r, from: seam + (k == 0 ? 0 : 180), sweep: 180, close: true),
                    with: radial(hx - 0.35 * r, hy - 0.4 * r, 1.3 * r, [color(c[0], a), color(c[1], a), color(c[2], a)], [0, 0.6, 1])
                )
            }
        }
        if g > 0 {
            let a = alpha * g
            context.fill(circle(cx, cy, r), with: radial(cx - 0.3 * r, cy - 0.35 * r, 1.25 * r, [color(glass[0], a), color(glass[1], a), color(glass[2], a)], [0, 0.55, 1]))
            // Light bouncing up from below, and a small highlight up on the left.
            blurred(&context, 6) {
                $0.fill(Path(ellipseIn: CGRect(x: cx - 0.6 * r, y: cy + 0.55 * r - 0.18 * r, width: 1.2 * r, height: 0.36 * r)), with: .color(color(bounce, 0.25 * a)))
            }
            blurred(&context, 3) { layer in
                layer.translateBy(x: cx - 0.38 * r, y: cy - 0.48 * r)
                layer.rotate(by: .degrees(-35))
                layer.fill(Path(ellipseIn: CGRect(x: -0.22 * r, y: -0.12 * r, width: 0.44 * r, height: 0.24 * r)), with: .color(.white.opacity(0.22 * a)))
            }
            context.stroke(
                circle(cx, cy, r - 0.75),
                with: linear(cx - 0.7 * r, cy - 0.7 * r, cx + 0.7 * r, cy + 0.7 * r, [color(rim[0], 0.95 * a), color(rim[1], 0.15 * a), color(rim[2], 0.6 * a)]),
                lineWidth: 1.5
            )
        }
    }

    func draw(in context: inout GraphicsContext, size: CGSize) {
        typealias S = OrbRingMorphScene
        let cx = size.width / 2
        let cy = size.height * S.orbY
        let contract = S.easeInOut(S.clamp((t - S.contractAt) / S.contractFor))

        // The halo the sphere sits in.
        let halo = S.easeOut(S.clamp((t - S.haloAt) / S.haloFor))
        let haloAlpha = 0.6 * halo * (1 - contract)
        if haloAlpha > 0.001 {
            let r = S.halo * (0.85 + 0.15 * halo) * (1 - 0.1 * contract)
            context.fill(S.circle(cx, cy, r), with: S.radial(cx, cy, r, [S.color(S.haloInner, haloAlpha), S.color(S.haloOuter, haloAlpha)], [0.4, 1]))
            context.stroke(S.circle(cx, cy, r - 0.5), with: .color(S.color(S.glow, 0.2 * haloAlpha)), lineWidth: 1)
        }

        // The orb, then the sphere it becomes.
        if t >= S.orbInAt {
            let back = S.easeOut(S.clamp((t - S.orbInAt) / S.orbInFor))
            S.drawOrb(&context, cx, cy, d: S.orb * (0.6 + 0.4 * back), rot: 0, glass: 0, alpha: back, glow: 0.5)
        } else {
            let pulse = S.bump((t - S.pulseAt) / S.pulseFor)
            let shrink = S.easeInOut(S.clamp((t - S.shrinkAt) / S.shrinkFor))
            let grow = S.spring(t - S.growAt)
            let glass = S.easeInOut(S.clamp((t - S.growAt) / S.glassFor))
            let rot = 360 * S.easeInOut(S.clamp((t - S.spinAt) / S.spinFor))
            var d = t < S.growAt ? S.lerp(S.orb, S.orbSmall, shrink) : S.lerp(S.orbSmall, S.sphere, grow)
            d *= (1 - S.pulse * pulse) * (1 - 0.35 * contract)
            S.drawOrb(&context, cx, cy, d: d, rot: rot, glass: glass, alpha: 1 - contract, glow: 0.5 + 0.3 * pulse)
        }

        // Dots that drift out from the sphere on slow orbits.
        let dotsOut = 1 - S.easeInOut(S.clamp((t - S.dotsOutAt) / S.dotsOutFor))
        if t >= S.dotsAt, dotsOut > 0 {
            for (i, (angle, distance, diameter, speed)) in S.dots.enumerated() {
                let f = S.easeOut(S.clamp((t - S.dotsAt - Double(i) * S.dotStagger) / S.dotFloat))
                guard f > 0 else { continue }
                let a = angle * .pi / 180 + speed * (t - S.dotsAt)
                let r = S.lerp(S.dotStart, distance, f)
                let alpha = S.clamp(f * 3) * dotsOut
                let x = cx + cos(a) * r
                let y = cy + sin(a) * r
                S.blurred(&context, S.dotGlow) { $0.fill(S.circle(x, y, diameter * 0.75), with: .color(S.color(S.dot, 0.6 * alpha))) }
                context.fill(S.circle(x, y, diameter / 2), with: .color(S.color(S.dot, alpha)))
            }
        }

        // The ring: four arcs on an ellipse that flattens into a vertical pill.
        let ringAlpha = 1 - S.clamp((t - (S.cardAt - S.swap)) / S.swap)
        if t >= S.ringAt, ringAlpha > 0 {
            let rx = S.ringRadius * (1 - contract)
            let ry = S.lerp(S.ringRadius, S.pillHalf, contract)
            let turn = 360 * (t - S.ringAt) / S.ringTurn
            let stroke = S.lerp(S.ringWidth, S.pillWidth, contract)
            for i in 0..<4 {
                let drawn = S.easeOut(S.clamp((t - S.ringAt - Double(i) * S.ringStagger) / S.ringDraw))
                let breath = 1 + S.breathAmount * sin(2 * .pi * (t - S.ringAt) / S.breath + Double(i) * .pi / 2) * (1 - contract)
                let span = S.lerp(S.arc * drawn * breath, 90, contract)
                guard span >= 0.5 else { continue }
                let from = -90 + Double(i) * 90 + turn - span / 2
                let a0 = from * .pi / 180
                let a1 = (from + span) * .pi / 180
                let x0 = cx + rx * cos(a0), y0 = cy + ry * sin(a0)
                let x1 = cx + rx * cos(a1), y1 = cy + ry * sin(a1)
                let path = S.arcPath(cx, cy, rx, ry, from: from, sweep: span, close: false)
                let glowColors = ring.map { $0.opacity(0.55 * ringAlpha) }
                S.blurred(&context, S.ringGlow) {
                    $0.stroke(path, with: S.linear(x0, y0, x1, y1, glowColors), style: StrokeStyle(lineWidth: stroke + 4, lineCap: .round, lineJoin: .round))
                }
                context.stroke(path, with: S.linear(x0, y0, x1, y1, ring.map { $0.opacity(ringAlpha) }), style: StrokeStyle(lineWidth: stroke, lineCap: .round, lineJoin: .round))
            }
        }

        // The card the pill opens into, with a ring icon drawn in it.
        let cardIn = S.clamp((t - (S.cardAt - S.swap)) / S.swap)
        let cardOut = S.easeIn(S.clamp((t - S.cardOutAt) / S.cardOutFor))
        let cardAlpha = cardIn * (1 - cardOut)
        guard cardAlpha > 0.001, t < S.orbInAt else { return }
        let s = S.spring(t - S.cardAt)
        let scale = 1 - 0.06 * cardOut
        let w = S.lerp(S.pillWidth, S.card, s) * scale
        let h = S.lerp(S.pillHalf * 2 + S.pillWidth, S.card, s) * scale
        let r = min(S.lerp(S.pillWidth / 2, S.cardRadius, S.clamp(s)) * scale, w / 2, h / 2)
        let tone = S.easeInOut(S.clamp((t - S.cardAt) / S.toneFor))
        let rect = CGRect(x: cx - w / 2, y: cy - h / 2, width: w, height: h)
        let box = Path(roundedRect: rect, cornerRadius: r, style: .circular)
        S.blurred(&context, S.cardGlow) { $0.fill(box, with: .color(S.color(S.glow, cardAlpha * S.lerp(0.7, 0.22, tone)))) }
        context.fill(box, with: .color(S.mix(S.cardStart, S.cardFill, tone).opacity(cardAlpha)))
        context.fill(box, with: S.linear(cx, cy - h / 2, cx, cy, [.white.opacity(0.08 * cardAlpha), .white.opacity(0)]))
        context.stroke(
            Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: r, style: .circular),
            with: .color(.white.opacity(0.08 * cardAlpha * tone)),
            lineWidth: 1
        )

        let icon = S.easeInOut(S.clamp((t - S.iconAt) / S.iconFor))
        if icon > 0 {
            let ir = S.iconRadius * scale
            let path = S.arcPath(cx, cy, ir, ir, from: -90, sweep: 360 * icon, close: false)
            let glowColors = ring.map { $0.opacity(0.6 * cardAlpha) }
            S.blurred(&context, 5) {
                $0.stroke(path, with: S.linear(cx - ir, cy - ir, cx + ir, cy + ir, glowColors), style: StrokeStyle(lineWidth: S.iconWidth + 3, lineCap: .round, lineJoin: .round))
            }
            context.stroke(path, with: S.linear(cx - ir, cy - ir, cx + ir, cy + ir, ring.map { $0.opacity(cardAlpha) }), style: StrokeStyle(lineWidth: S.iconWidth, lineCap: .round, lineJoin: .round))
        }
        let pop = S.spring(t - S.iconDotAt)
        if pop > 0 {
            context.fill(S.circle(cx, cy, S.iconDot * pop * scale), with: .color(S.color(S.iconDotFill, cardAlpha)))
        }
    }
}

#Preview {
    OrbRingMorph(autoPlay: true)
}
