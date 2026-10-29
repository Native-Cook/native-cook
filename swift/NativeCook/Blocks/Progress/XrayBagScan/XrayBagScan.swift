import SwiftUI
import UIKit

/// The bag-drop step of an invented airline (Halcyon Air). A lilac carry-on rides the belt through
/// a scanner gantry; the gantry's window is a mask that shows the bag as its x-ray (cyan on navy,
/// contents visible) while the rest of the bag stays a photo. Light curtains glow on the window's
/// edges, a sweep passes while the bag dwells, detection boxes pop on the liquids, headphones and
/// laptop, and the card steps Ready to scan → Scanning… → Checking contents → Bag cleared. The bag
/// leaves on the right while the next one arrives at the rest spot, so the loop is seamless.
///
/// Drag the bag along the belt to scrub the scan; release and the belt carries on. "Scan again"
/// restarts the pass. Reduced motion: the cleared state, still.
struct XrayBagScan: View {
    static let loop = XrayBagScanMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = XrayBagScanTouch()
    @State private var awake = false

    private typealias M = XrayBagScanMotion
    private typealias Art = XrayBagScanArt

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let height = geometry.size.height / k
                let top = Double(safeTop / k)
                let sy = top - 59
                let cardBottom = min(818 + sy, height - max(Double(safeBottom / k), 12) - 0)
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    screen(clock: clock, k: k, height: height, top: top, sy: sy, cardBottom: cardBottom)
                }
            }
            .ignoresSafeArea()
        }
        .background(Art.color(0x857762).ignoresSafeArea())
        .preferredColorScheme(.light)
        .task(id: awake) {
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, clock > touch.restsAfter {
                    awake = false
                    return
                }
            }
        }
    }

    // MARK: Screen

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, height: Double, top: Double, sy: Double, cardBottom: Double) -> some View {
        let still = reducedMotion
        let t = still ? 5.6 : touch.loopTime(clock, autoPlay: autoPlay)
        let d = still ? M.leg1 : touch.belt(clock, autoPlay: autoPlay)
        let lean = still ? 0 : touch.lean(clock, t: t)
        let stage = M.stage(t)
        let cardTop = cardBottom - 210
        ZStack(alignment: .topLeading) {
            Canvas { context, size in
                context.scaleBy(x: k, y: k)
                drawScene(context, width: Double(size.width / k), height: height, sy: sy, t: t, d: d, lean: lean)
            }
            .allowsHitTesting(false)
            .accessibilityElement()
            .accessibilityLabel("A lilac suitcase passing through the bag scanner")

            ZStack(alignment: .topLeading) {
                header(top: top)
                Color.clear
                    .frame(width: 393, height: 360)
                    .contentShape(Rectangle())
                    .gesture(drag(k: k))
                    .position(x: 196.5, y: 252 + sy + 180)
                    .accessibilityHidden(true)
                card(t: t, stage: stage, top: cardTop)
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    private func drawScene(_ ctx: GraphicsContext, width: Double, height: Double, sy: Double, t: Double, d: Double, lean: Double) {
        Art.drawBackground(ctx, width: width, height: height, sy: sy)
        let photo = ctx.resolve(Image(uiImage: Art.photo))
        let xray = ctx.resolve(Image(uiImage: Art.xray))
        let window = Art.windowRect(sy: sy)
        let bagXs = [-1.0, 0, 1].map { M.restX + d - M.spacing * $0 }.filter { abs($0 - 196.5) < 196.5 + Art.imageW / 2 + 12 }

        // Outside the window: the photo.
        var outside = ctx
        var hole = Path(CGRect(x: -20, y: -20, width: width + 40, height: height + 40))
        hole.addRect(window)
        outside.clip(to: hole, style: FillStyle(eoFill: true))
        Art.drawBelt(outside, width: width, d: d, sy: sy)
        for x in bagXs { Art.drawBagShadow(outside, x: x, sy: sy) }
        for x in bagXs { Art.drawBag(outside, photo, x: x, lean: lean, sy: sy) }

        // Inside the window: the x-ray.
        var inside = ctx
        inside.clip(to: Path(window))
        Art.drawWindowBack(inside, sy: sy)
        Art.drawBeltXray(inside, d: d, sy: sy)
        for x in bagXs where x + Art.imageW / 2 > M.winL - 8 && x - Art.imageW / 2 < M.winR + 8 {
            var glow = inside
            glow.addFilter(.blur(radius: 6))
            glow.blendMode = .plusLighter
            glow.opacity = 0.35
            Art.drawBag(glow, xray, x: x, lean: lean, sy: sy)
            Art.drawBag(inside, xray, x: x, lean: lean, sy: sy)
        }
        let bagX = M.restX + d
        let boxes = M.boxes(t: t, bagX: bagX, scrubbing: touch.scrubbing, items: Art.itemOffsets)
        Art.drawBoxes(inside, x: bagX, lean: lean, sy: sy, boxes: boxes, green: M.cleared(t))
        let sweep = M.sweep(t)
        Art.drawWindowFront(inside, sy: sy, sweepY: sweep.y, sweepAlpha: touch.scrubbing ? 0 : sweep.alpha)

        let left = bagXs.map { M.curtain(edge: M.winL, bagX: $0) }.max() ?? 0.35
        let right = bagXs.map { M.curtain(edge: M.winR, bagX: $0) }.max() ?? 0.35
        Art.drawGantry(ctx, sy: sy, beacon: M.beacon(t), scanning: M.scanning(t), cleared: M.cleared(t), t: t)
        Art.drawCurtains(ctx, sy: sy, left: left, right: right)
    }

    // MARK: Header

    private func header(top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 7) {
                Canvas { context, _ in Art.drawBird(context) }
                    .frame(width: 16, height: 14)
                Text("Halcyon Air")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(Art.color(Art.ink))
            }
            .padding(.horizontal, 12)
            .frame(height: 30)
            .background(Capsule().fill(Art.color(0xFFF1DE, 0.8)))
            .fixedSize()
            .position(x: 20 + 56, y: top + 22)
            .accessibilityElement(children: .combine)

            Text("Step 2 of 3")
                .font(.system(size: 13, weight: .semibold))
                .foregroundStyle(Art.color(Art.brown))
                .padding(.horizontal, 12)
                .frame(height: 30)
                .background(Capsule().fill(Art.color(Art.ink, 0.08)))
                .fixedSize()
                .position(x: 373 - 45, y: top + 22)

            Text("Bag check")
                .font(.system(size: 34, weight: .heavy))
                .foregroundStyle(Art.color(Art.ink))
                .fixedSize()
                .frame(width: 353, alignment: .leading)
                .position(x: 20 + 176.5, y: top + 68)
                .accessibilityAddTraits(.isHeader)

            Text("HA 418 · Lisbon → Oslo · Gate E7")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Art.color(Art.brown))
                .fixedSize()
                .frame(width: 353, alignment: .leading)
                .position(x: 20 + 176.5, y: top + 102)
        }
        .frame(width: 393, alignment: .topLeading)
    }

    // MARK: Card

    private func card(t: Double, stage: Double, top: Double) -> some View {
        let s = M.mod(stage, 4)
        let base = Int(floor(s)) % 4
        let f = s - floor(s)
        let fill = M.fill(t)
        let done = M.cleared(t)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 28, style: .continuous)
                .fill(Art.color(Art.card))
                .shadow(color: Art.color(0x2B1A0E, 0.08), radius: 1.5, y: 1)
                .shadow(color: Art.color(0x2B1A0E, 0.16), radius: 15, y: 10)
                .frame(width: 361, height: 210)
                .position(x: 196.5, y: top + 105)

            Canvas { context, _ in Art.drawStatusIcon(context, stage: stage, spin: M.spin(t), check: M.check(t)) }
                .frame(width: 46, height: 46)
                .position(x: 60, y: top + 44)
                .accessibilityHidden(true)

            ZStack(alignment: .leading) {
                statusText(M.statuses[base]).opacity(1 - f).offset(y: -8 * f)
                if f > 0.001 { statusText(M.statuses[(base + 1) % 4]).opacity(f).offset(y: 8 * (1 - f)) }
            }
            .frame(width: 250, height: 28, alignment: .leading)
            .position(x: 94 + 125, y: top + 35)
            .accessibilityElement()
            .accessibilityLabel(M.statuses[f < 0.5 ? base : (base + 1) % 4])

            Text("Carry-on · 7.4 kg · Tag 418 207")
                .font(.system(size: 14))
                .foregroundStyle(Art.color(Art.muted))
                .fixedSize()
                .frame(width: 250, alignment: .leading)
                .position(x: 94 + 125, y: top + 58)

            Canvas { context, size in Art.drawBar(context, width: Double(size.width), fill: fill, done: done) }
                .frame(width: 321, height: 8)
                .position(x: 196.5, y: top + 94)
                .accessibilityElement()
                .accessibilityLabel("Progress")
                .accessibilityValue("\(Int((fill * 100).rounded())) percent")

            ForEach(0..<3, id: \.self) { i in
                let reached = M.clamp((fill - Double(i + 1) / 3 + 0.04) * 25)
                Text(["Scan", "Contents", "Cleared"][i])
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Art.mix(0xB8A08A, i == 2 ? Art.green : Art.ink, reached))
                    .fixedSize()
                    .position(x: 36 + 53.5 + Double(i) * 109, y: top + 114)
                    .accessibilityHidden(true)
            }

            Button {
                restart()
            } label: {
                HStack(spacing: 8) {
                    Canvas { context, _ in Art.drawAgainGlyph(context, tint: Art.color(0xFFF3E3)) }
                        .frame(width: 18, height: 18)
                    Text("Scan again")
                        .font(.system(size: 16, weight: .semibold))
                        .foregroundStyle(Art.color(0xFFF3E3))
                }
                .frame(width: 321, height: 48)
                .background(Capsule().fill(Art.color(Art.ink)))
                .contentShape(Capsule())
            }
            .buttonStyle(XrayBagScanPress())
            .position(x: 196.5, y: top + 166)
            .accessibilityLabel("Scan again")
        }
        .frame(width: 393, alignment: .topLeading)
    }

    private func statusText(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 20, weight: .bold))
            .foregroundStyle(Art.color(Art.ink))
            .lineLimit(1)
            .fixedSize()
    }

    // MARK: Touch

    private func restart() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        if reducedMotion { return }
        touch.restart(clock: Date().timeIntervalSince(epoch))
        if !autoPlay { awake = true }
    }

    private func drag(k: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                if reducedMotion { return }
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching {
                    touch.begin(clock: clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                if touch.move(dx: value.translation.width / k, velocityX: value.velocity.width / k) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
            .onEnded { _ in
                guard touch.touching else { return }
                touch.end(clock: Date().timeIntervalSince(epoch))
            }
    }
}

/// A small press-down for the button.
struct XrayBagScanPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.35, dampingFraction: 0.7), value: configuration.isPressed)
    }
}
