import SwiftUI

struct PaymentCheckBurstRow: Equatable {
    var label: String
    var value: String
}

/// A payment receipt: a green check pops with confetti, shrinks to the top while the receipt card
/// slides up and its amount counts, then a silver bar peels back to show a reward and coins spill.
/// One clock drives all of it, so a touch and the autoPlay loop run the same motion.
struct PaymentCheckBurst: View {
    /// The amount in cents, counted up on the card.
    var amount = 57862
    var currency = "CA $"
    var title = "Payment Complete"
    var subtitle = "Your transfer is on its way."
    /// The receipt's number, shown at its top right.
    var reference = "NC-20481"
    /// The fee row above the total.
    var fee = "$0.00"
    var rows = [
        PaymentCheckBurstRow(label: "To", value: "Mia Carter"),
        PaymentCheckBurstRow(label: "Email", value: "hello@example.com"),
        PaymentCheckBurstRow(label: "Method", value: "E-Transfer"),
        PaymentCheckBurstRow(label: "Date", value: "June 12, 2024 · 9:41"),
    ]
    var claimLabel = "Peel to claim your reward"
    /// The reward the bar reveals.
    var reward = "+400 points"
    /// Plays by itself every `PaymentCheckBurstMotion.loop` seconds and ends on a white screen again.
    var autoPlay = false
    /// Called when the claim bar is pressed by a touch.
    var onClaim: (() -> Void)? = nil

    private typealias M = PaymentCheckBurstMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = PaymentCheckBurstClock()
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private static let green = Color(hex: 0x2fbf71)
    private static let greenLight = Color(hex: 0x7beaa9)
    private static let greenDeep = Color(hex: 0x16a05a)
    private static let page = Color(hex: 0xf3f4f6)
    private static let ink = Color(hex: 0x101318)
    private static let value = Color(hex: 0x1f2328)
    private static let label = Color(hex: 0x8a8f98)
    private static let rule = Color(hex: 0xdfe2e7)
    private static let silverInk = Color(hex: 0x5f636b)
    private static let rewardInk = Color(hex: 0x3d4048)

    private static let checkSize = 148.0
    private static let checkSmall = 72.0
    private static let margin = 16.0
    private static let cardRadius = 22.0
    private static let pad = 20.0
    private static let headHeight = 48.0
    private static let amountHeight = 92.0
    private static let ruleHeight = 24.0
    private static let rowHeight = 38.0
    private static let totalHeight = 48.0
    private static let tooth = 12.0
    private static let teethHeight = 8.0
    private static let punch = 10.0
    private static let barHeight = 56.0
    private static let barGap = 18.0
    /// Room round the receipt's drawn paper for its shadow.
    private static let shadowRoom = 28.0

    /// The tick inside the check, in the check's 148 pt box.
    private static let checkPath: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 48, y: 76))
        path.addLine(to: CGPoint(x: 66, y: 94))
        path.addLine(to: CGPoint(x: 102, y: 56))
        return path
    }()

    /// The receipt's paper: rounded at the top, torn into teeth at the bottom, with a half-circle
    /// punch on each side at `punchY`. Drawn `shadowRoom` in from the canvas edges.
    private static func paper(width: Double, height: Double, punchY: Double) -> Path {
        let o = shadowRoom, r = cardRadius, bottom = o + height
        var path = Path()
        path.move(to: CGPoint(x: o, y: o + r))
        path.addRelativeArc(center: CGPoint(x: o + r, y: o + r), radius: r, startAngle: .degrees(180), delta: .degrees(90))
        path.addLine(to: CGPoint(x: o + width - r, y: o))
        path.addRelativeArc(center: CGPoint(x: o + width - r, y: o + r), radius: r, startAngle: .degrees(270), delta: .degrees(90))
        path.addLine(to: CGPoint(x: o + width, y: o + punchY - punch))
        path.addRelativeArc(center: CGPoint(x: o + width, y: o + punchY), radius: punch, startAngle: .degrees(-90), delta: .degrees(-180))
        path.addLine(to: CGPoint(x: o + width, y: bottom))
        let count = (width / tooth).rounded()
        let step = width / count
        for i in stride(from: Int(count), to: 0, by: -1) {
            path.addLine(to: CGPoint(x: o + (Double(i) - 0.5) * step, y: bottom + teethHeight))
            path.addLine(to: CGPoint(x: o + Double(i - 1) * step, y: bottom))
        }
        path.addLine(to: CGPoint(x: o, y: o + punchY + punch))
        path.addRelativeArc(center: CGPoint(x: o, y: o + punchY), radius: punch, startAngle: .degrees(90), delta: .degrees(-180))
        path.closeSubpath()
        return path
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: !clock.isPlaying)) { timeline in
                screen(t: clock.time(at: timeline.date), insets: insets, size: size)
            }
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
            .onTapGesture { touch(from: M.tapAt, force: false) }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: touches) { await resume() }
        .accessibilityLabel(title)
    }

    // MARK: Clock

    /// Runs the loop from `from` to its end. A run in progress is left alone unless `force`.
    private func touch(from: Double, force: Bool) {
        guard force || !clock.busy(at: .now) else { return }
        clock.auto = nil
        clock.touch = .init(start: .now, from: from)
        touches += 1
    }

    private func claim() {
        // Only a bar that is on screen can be peeled; the press goes to the peel itself.
        let t = clock.time(at: .now)
        if t < M.receiptAt + 0.6 || t >= M.peelAt { return }
        haptic.impactOccurred()
        touch(from: M.peelAt, force: true)
        onClaim?()
    }

    /// Frees the touch once its run is over and, with autoPlay, starts the loop again from rest.
    private func resume() async {
        if let touch = clock.touch {
            try? await Task.sleep(for: .seconds(touch.remaining), tolerance: .zero)
            guard !Task.isCancelled else { return }
            clock.touch = nil
            try? await Task.sleep(for: .seconds(M.resumeAfter), tolerance: .zero)
            guard !Task.isCancelled else { return }
        }
        if autoPlay { clock.auto = .now }
    }

    // MARK: Screen

    private static func money(_ cents: Int) -> String {
        "\(cents / 100).\(String(format: "%02d", cents % 100))"
    }

    private var cardHeight: Double {
        Self.headHeight + Self.amountHeight + Self.ruleHeight + Double(rows.count) * Self.rowHeight + Self.ruleHeight + Self.totalHeight + 8
    }

    private func screen(t: Double, insets: EdgeInsets, size: CGSize) -> some View {
        let cardWidth = size.width - Self.margin * 2
        let centerY = size.height * 0.42
        let smallY = insets.top + 76
        let titleTop = smallY + Self.checkSmall / 2 + 10
        let cardTop = titleTop + 72
        let barTop = cardTop + cardHeight + Self.teethHeight + Self.barGap
        let toCard = size.height - cardTop

        let titleP = M.easeOut(M.seg(t, M.titleAt, M.titleAt + M.titleIn))
        let subtitleP = M.easeOut(M.seg(t, M.titleAt + 0.06, M.titleAt + 0.06 + M.titleIn))
        let out = M.easeInOut(M.seg(t, M.fadeAt, M.fadeAt + M.fadeIn))
        let counted = Int((Double(amount) * (reduced ? M.seg(t, M.countAt, M.countAt + M.reducedFade) : M.easeOut(M.seg(t, M.countAt, M.countAt + M.countFor)))).rounded())
        let into = reduced ? 1 : (t < M.receiptAt ? 0 : M.spring(t - M.receiptAt, stiffness: M.receiptStiffness, damping: M.receiptDamping))
        let cardOpacity = reduced ? M.seg(t, M.receiptAt, M.receiptAt + M.reducedFade) : (t < M.receiptAt ? 0 : 1)

        return ZStack(alignment: .topLeading) {
            Self.page

            // The whole screen rises and fades at the end.
            ZStack(alignment: .topLeading) {
                check(t: t, centerY: centerY, smallY: smallY)
                    .offset(x: size.width / 2 - Self.checkSize / 2, y: centerY - Self.checkSize / 2)

                Text(title)
                    .font(.system(size: 28, weight: .heavy)).kerning(-0.6).foregroundStyle(Self.ink)
                    .frame(width: size.width, height: 34)
                    .opacity(titleP)
                    .offset(y: titleTop + (reduced ? 0 : (1 - titleP) * 8))
                Text(subtitle)
                    .font(.system(size: 16, weight: .medium)).foregroundStyle(Self.label)
                    .frame(width: size.width, height: 22)
                    .opacity(subtitleP)
                    .offset(y: titleTop + 38 + (reduced ? 0 : (1 - subtitleP) * 8))

                card(t: t, counted: counted, width: cardWidth)
                    .opacity(cardOpacity)
                    .offset(x: Self.margin, y: cardTop + (1 - into) * toCard)

                Canvas { context, _ in
                    PaymentCheckBurstParticles.drawCoins(context, time: t, at: M.coinsAt, origin: CGPoint(x: size.width / 2, y: barTop + Self.barHeight / 2))
                }
                .frame(width: size.width, height: size.height)
                .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .opacity(1 - out)
            .offset(y: reduced ? 0 : -M.rise * out)

            Canvas { context, _ in
                PaymentCheckBurstParticles.drawConfetti(context, time: t, at: M.popAt, size: size)
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func check(t: Double, centerY: Double, smallY: Double) -> some View {
        let pop = t < M.popAt ? 0 : M.spring(t - M.popAt, stiffness: M.popStiffness, damping: M.popDamping)
        let move = M.easeInOut(M.seg(t, M.shrinkAt, M.shrinkAt + M.shrinkIn))
        let small = Self.checkSmall / Self.checkSize
        let size = 1 - (1 - small) * move
        let ripple = M.seg(t, M.popAt + 0.1, M.popAt + 0.1 + M.rippleFor)
        let ripple2 = M.seg(t, M.popAt + 0.25, M.popAt + 0.25 + M.rippleFor * 1.3)
        // The tick draws itself on once the circle has popped.
        let tick = reduced ? 1 : M.easeOut(M.seg(t, M.popAt + 0.12, M.popAt + 0.42))
        let inner = Self.checkSize - 32
        return ZStack {
            Circle().strokeBorder(Self.green, lineWidth: 3)
                .frame(width: inner, height: inner)
                .scaleEffect(1 + M.easeOut(ripple))
                .opacity(reduced || ripple == 0 || ripple == 1 ? 0 : 0.5 * (1 - ripple))
            Circle().fill(Color.rgba(47, 191, 113, 0.35))
                .frame(width: inner, height: inner)
                .scaleEffect(1 + 1.3 * M.easeOut(ripple2))
                .opacity(reduced || ripple2 == 0 || ripple2 == 1 ? 0 : 0.35 * (1 - ripple2))
            Canvas { context, _ in
                let c = Self.checkSize / 2
                var glow = context
                glow.opacity = 0.45
                glow.addFilter(.blur(radius: 12))
                glow.fill(Path(ellipseIn: CGRect(x: c - (c - 14), y: c + 6 - (c - 14), width: (c - 14) * 2, height: (c - 14) * 2)), with: .color(Self.green))
                context.fill(
                    Path(ellipseIn: CGRect(x: 16, y: 16, width: Self.checkSize - 32, height: Self.checkSize - 32)),
                    with: .radialGradient(
                        Gradient(stops: [.init(color: Self.greenLight, location: 0), .init(color: Self.green, location: 0.55), .init(color: Self.greenDeep, location: 1)]),
                        center: CGPoint(x: c, y: 30), startRadius: 0, endRadius: Self.checkSize * 0.75
                    )
                )
                context.stroke(Path(ellipseIn: CGRect(x: 16.75, y: 16.75, width: Self.checkSize - 33.5, height: Self.checkSize - 33.5)), with: .color(.white.opacity(0.35)), lineWidth: 1.5)
                var gloss = context
                gloss.opacity = 0.5
                gloss.fill(
                    Path(ellipseIn: CGRect(x: c - 34, y: 26, width: 68, height: 30)),
                    with: .linearGradient(Gradient(colors: [.white.opacity(0.75), .white.opacity(0)]), startPoint: CGPoint(x: 0, y: 26), endPoint: CGPoint(x: 0, y: 56))
                )
                if tick > 0 {
                    let mark = Self.checkPath.trimmedPath(from: 0, to: tick)
                    let style = StrokeStyle(lineWidth: 10, lineCap: .round, lineJoin: .round)
                    context.stroke(mark.offsetBy(dx: 0, dy: 2), with: .color(.rgba(10, 90, 45, 0.25)), style: style)
                    context.stroke(mark, with: .color(.white), style: style)
                }
            }
            .frame(width: Self.checkSize, height: Self.checkSize)
            .opacity(reduced ? M.seg(t, M.popAt, M.popAt + M.reducedFade) : (t < M.popAt ? 0 : 1))
            .scaleEffect(reduced ? small : pop * size)
            .offset(y: reduced ? smallY - centerY : (smallY - centerY) * move)
        }
        .frame(width: Self.checkSize, height: Self.checkSize)
        .allowsHitTesting(false)
    }

    private func card(t: Double, counted: Int, width: Double) -> some View {
        let punchY = Self.headHeight + Self.amountHeight + Self.ruleHeight / 2
        let room = Self.shadowRoom
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                let paper = Self.paper(width: width, height: cardHeight, punchY: punchY)
                // Two shadows under the paper, a soft deep one and a close one, each drawn on its own.
                for (color, radius, y) in [(Color.rgba(17, 24, 39, 0.10), 22.0, 16.0), (.rgba(17, 24, 39, 0.08), 3, 1)] {
                    var shadowed = context
                    shadowed.addFilter(.shadow(color: color, radius: radius, x: 0, y: y))
                    shadowed.fill(paper, with: .color(.white))
                }
                context.fill(paper, with: .color(.white))
                var rule = Path()
                rule.move(to: CGPoint(x: room + Self.punch + 8, y: room + punchY))
                rule.addLine(to: CGPoint(x: room + width - Self.punch - 8, y: room + punchY))
                context.stroke(rule, with: .color(Self.rule), style: StrokeStyle(lineWidth: 1.5, dash: [6, 5]))
            }
            .frame(width: width + room * 2, height: cardHeight + Self.teethHeight + room * 2)
            .offset(x: -room, y: -room)
            .allowsHitTesting(false)

            receipt(t: t, counted: counted)
                .frame(width: width, height: cardHeight, alignment: .top)

            bars(t: t, width: width)
                .offset(y: cardHeight + Self.teethHeight + Self.barGap)
        }
        .frame(width: width, alignment: .topLeading)
    }

    private func receipt(t: Double, counted: Int) -> some View {
        VStack(spacing: 0) {
            HStack(alignment: .bottom) {
                HStack(spacing: 8) {
                    Circle().fill(Self.green).frame(width: 22, height: 22)
                        .overlay(Image(systemName: "checkmark").font(.system(size: 10, weight: .bold)).foregroundStyle(.white))
                    Text("RECEIPT").font(.system(size: 12, weight: .heavy)).kerning(1.6).foregroundStyle(Self.value).frame(height: 16)
                }
                Spacer(minLength: 0)
                Text("#\(reference)").font(.custom("Menlo", size: 12)).foregroundStyle(Self.label).frame(height: 16)
            }
            .frame(height: Self.headHeight, alignment: .bottom)
            VStack(spacing: 2) {
                Text("Amount paid").font(.system(size: 13, weight: .semibold)).kerning(0.2).foregroundStyle(Self.label).frame(height: 18)
                HStack(alignment: .top, spacing: 3) {
                    Text(currency).font(.system(size: 20, weight: .bold)).foregroundStyle(Self.label)
                        .frame(height: 24).padding(.top, 7)
                    Text(Self.money(counted))
                        .font(.system(size: 46, weight: .heavy)).kerning(-1.4).monospacedDigit().foregroundStyle(Self.ink)
                        .frame(height: 52)
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: Self.amountHeight)
            Color.clear.frame(height: Self.ruleHeight)
            ForEach(Array(rows.enumerated()), id: \.offset) { i, row in
                receiptRow(row, index: i, t: t)
            }
            Self.rule.frame(height: 1).frame(height: Self.ruleHeight)
            receiptRow(PaymentCheckBurstRow(label: "Fee", value: fee), index: rows.count, t: t)
        }
        .padding(.horizontal, Self.pad)
    }

    private func receiptRow(_ row: PaymentCheckBurstRow, index: Int, t: Double) -> some View {
        let at = M.receiptAt + 0.5 + Double(index) * M.rowStagger
        let p = M.easeOut(M.seg(t, at, at + 0.3))
        return HStack {
            Text(row.label).font(.system(size: 15, weight: .medium)).foregroundStyle(Self.label)
            Spacer(minLength: 0)
            Text(row.value).font(.system(size: 15, weight: .semibold)).monospacedDigit().foregroundStyle(Self.value)
        }
        .frame(height: Self.rowHeight)
        .opacity(p)
        .offset(y: reduced ? 0 : (1 - p) * 8)
    }

    private func bars(t: Double, width: Double) -> some View {
        let peel = M.easeInOut(M.seg(t, M.peelAt, M.peelAt + M.peelFor))
        let sheen = M.seg(t, M.sheenAt, M.sheenAt + M.sheenFor)
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return ZStack(alignment: .topLeading) {
            HStack(spacing: 10) {
                Circle()
                    .fill(LinearGradient(colors: [.white, Color(hex: 0xc9cacf), Color(hex: 0x8e9096)], startPoint: UnitPoint(x: 0.2, y: 0), endPoint: UnitPoint(x: 0.8, y: 1)))
                    .overlay(Circle().strokeBorder(Color(hex: 0x9fa1a7), lineWidth: 1))
                    .overlay(Image(systemName: "star.fill").font(.system(size: 10)).foregroundStyle(.white))
                    .frame(width: 26, height: 26)
                Text(reward).font(.system(size: 19, weight: .heavy)).kerning(-0.3).foregroundStyle(Self.rewardInk)
            }
            .frame(width: width, height: Self.barHeight)
            .background(LinearGradient(colors: [Color(hex: 0xfbfbfc), Color(hex: 0xe4e5e8), Color(hex: 0xf5f5f7)], startPoint: .topLeading, endPoint: .bottomTrailing))
            .overlay(alignment: .top) {
                Rectangle().fill(.black).frame(height: 18)
                    .opacity(0.28 * sin(.pi * min(1, peel * 1.1)))
            }
            .clipShape(shape)
            .overlay(shape.strokeBorder(Color(hex: 0xdcdde1), lineWidth: 1))

            Button(action: claim) {
                ZStack {
                    LinearGradient(
                        stops: [
                            .init(color: Color(hex: 0xf6f6f8), location: 0),
                            .init(color: Color(hex: 0xcfd0d5), location: 0.3),
                            .init(color: Color(hex: 0xeceef1), location: 0.5),
                            .init(color: Color(hex: 0xb4b6bc), location: 0.78),
                            .init(color: Color(hex: 0xdcdde1), location: 1),
                        ],
                        startPoint: .topLeading, endPoint: .bottomTrailing
                    )
                    HStack(spacing: 8) {
                        Image(systemName: "gift").font(.system(size: 15))
                        Text(claimLabel).font(.system(size: 16, weight: .semibold))
                    }
                    .foregroundStyle(Self.silverInk)
                    // A band of light leaning across the bar, skewed about its own middle.
                    Canvas { context, size in
                        var layer = context
                        layer.opacity = reduced ? 0 : sin(.pi * sheen)
                        layer.translateBy(x: -90 + (width + 180) * sheen + 27, y: size.height / 2)
                        layer.concatenate(CGAffineTransform(a: 1, b: 0, c: tan(-22 * .pi / 180), d: 1, tx: 0, ty: 0))
                        layer.fill(Path(CGRect(x: -27, y: -size.height / 2 - 10, width: 54, height: size.height + 20)), with: .color(.white.opacity(0.75)))
                    }
                    .allowsHitTesting(false)
                }
                .frame(width: width, height: Self.barHeight)
                .clipShape(shape)
                .background(shape.fill(.white).shadow(color: .rgba(17, 24, 39, 0.12), radius: 5, y: 4))
                .contentShape(shape)
            }
            .buttonStyle(.plain)
            .accessibilityLabel(claimLabel)
            // A flap hinged on its top edge, swinging up and away; it is gone once edge-on.
            .projectionEffect(reduced ? ProjectionTransform() : Self.flap(angle: peel * 88, width: width))
            .opacity(reduced ? 1 - M.seg(t, M.peelAt, M.peelAt + M.reducedFade) : (peel >= 0.98 ? 0 : 1))
        }
        .frame(width: width, height: Self.barHeight, alignment: .topLeading)
    }

    /// `rotateX(angle)` about the top edge with a perspective of 500, as React Native draws the flap.
    private static func flap(angle: Double, width: Double) -> ProjectionTransform {
        var perspective = CATransform3DIdentity
        perspective.m34 = -1 / 500
        var t = CATransform3DMakeTranslation(-width / 2, 0, 0)
        t = CATransform3DConcat(t, CATransform3DMakeRotation(angle * .pi / 180, 1, 0, 0))
        t = CATransform3DConcat(t, perspective)
        t = CATransform3DConcat(t, CATransform3DMakeTranslation(width / 2, 0, 0))
        return ProjectionTransform(t)
    }
}

#Preview {
    PaymentCheckBurst(autoPlay: true)
}
