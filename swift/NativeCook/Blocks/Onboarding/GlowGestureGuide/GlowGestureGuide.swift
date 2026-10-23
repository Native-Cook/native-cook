import SwiftUI

struct GlowGestureGuideRow {
    var title: String
    var subtitle: String
    var gesture: GlowGestureGuideKind

    static let samples = [
        GlowGestureGuideRow(title: "Go forward", subtitle: "Tap the screen", gesture: .tap),
        GlowGestureGuideRow(title: "Pause and Seek", subtitle: "Hold and slide left or right", gesture: .hold),
        GlowGestureGuideRow(title: "Go back", subtitle: "Tap the left edge", gesture: .edge),
        GlowGestureGuideRow(title: "Move between stories", subtitle: "Swipe left or right", gesture: .swipe),
    ]
}

/// A damped spring (mass 1, stiffness 322.27, damping 30.52) sampled against the timeline's date.
private struct GlowGestureGuideSpring {
    static let stiffness = 322.27
    static let damping = 30.52
    static let settle = 0.9

    var from: Double
    var velocity = 0.0
    var to: Double
    var start = Date.distantPast

    func value(at date: Date) -> Double {
        let t = max(0, date.timeIntervalSince(start))
        guard t < Self.settle else { return to }
        let w0 = Self.stiffness.squareRoot()
        let zeta = Self.damping / (2 * w0)
        let wd = w0 * (1 - zeta * zeta).squareRoot()
        let a = from - to
        let b = (velocity + zeta * w0 * a) / wd
        return to + exp(-zeta * w0 * t) * (a * cos(wd * t) + b * sin(wd * t))
    }

    mutating func retarget(_ target: Double, at date: Date, still: Bool) {
        let v = still ? 0 : (value(at: date + 0.004) - value(at: date - 0.004)) / 0.008
        from = still ? target : value(at: date)
        velocity = v
        to = target
        start = date
    }
}

/// A guide to the gestures of a story viewer on a deep blue-to-violet screen with a warm glow
/// drifting behind it. A frosted highlight springs from row to row, stretching as it travels; the
/// row it lands on grows and brightens and its hand icon acts the gesture out.
struct GlowGestureGuide: View {
    var title = "Watching Stories"
    var subtitle = "You can use these gestures to control playback."
    var rows = GlowGestureGuideRow.samples
    var footer = "Tap to keep watching"
    /// The row highlighted first.
    var initialIndex = 0
    /// Walks the highlight through the rows by itself, twice every `loop` seconds.
    var autoPlay = false
    var onSelect: ((Int, GlowGestureGuideRow) -> Void)? = nil
    /// Called when the footer is tapped: the guide is done.
    var onDone: (() -> Void)? = nil

    private static let sky = [Color(hex: 0x1d4f8f), Color(hex: 0x3a2c8a), Color(hex: 0x2a1259)]
    private static let floor = Color.rgba(12, 8, 24, 0.85)
    private static let glow: [Gradient.Stop] = [
        .init(color: .rgba(255, 138, 61, 0.62), location: 0),
        .init(color: .rgba(224, 65, 47, 0.38), location: 0.45),
        .init(color: .rgba(224, 65, 47, 0), location: 1),
    ]

    // Layout, in points.
    private static let margin = 24.0
    private static let headerTop = 48.0
    private static let subtitleWidth = 280.0
    private static let rowsTop = 200.0
    private static let rowHeight = 80.0
    private static let rowGap = 14.0
    private static let pitch = rowHeight + rowGap
    private static let icon = 46.0
    private static let iconLeft = 20.0
    private static let textLeft = iconLeft + icon + 18
    /// A row's title and subtitle, stacked.
    private static let textHeight = 43.0
    private static let radius = 20.0
    private static let footerGap = 40.0
    private static let floorHeight = 160.0
    // The glow: its radius, where its orbit is centred (a share of the width, points below the first
    // row's top) and the orbit's radii.
    private static let glowR = 170.0
    private static let glowX = 0.62
    private static let glowY = 70.0
    private static let orbitX = 46.0
    private static let orbitY = 64.0

    // The highlight springs between rows (response 0.35 s at damping ratio 0.85) and stretches with
    // its distance to the row it is heading for. The row under it grows and brightens.
    private static let stretch = 0.3
    private static let stretchMax = 22.0
    private static let activeScale = 0.05
    private static let titleDim = 0.82
    private static let subtitleDim = 0.55
    private static let subtitleLit = 0.7

    // The autoPlay loop: the highlight stops 1.35 s on each row, twice round from "Go forward", which
    // is where it rests at the start and the end; the glow goes once round its orbit in the same time.
    static let loop = 10.8
    private static let stop = 1.35
    private static let firstHop = 0.65
    private static let hops = [1, 2, 3, 0, 1, 2, 3, 0]
    private static let userHold = 3.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var index: Int? = nil
    @State private var highlight: GlowGestureGuideSpring? = nil
    /// When each row was last chosen: it plays its icon from then.
    @State private var chosen: [Int: Date] = [:]
    @State private var appeared = Date.now
    @State private var touches = 0
    @State private var motionUntil = Date.distantPast
    @State private var idle = false
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private var current: Int { index ?? initialIndex }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom

            // The glow drifts all the time; with reduced motion it stays, and the timeline only runs while
            // the highlight moves.
            TimelineView(.animation(paused: reduceMotion && Date() > motionUntil)) { timeline in
                screen(width: width, height: height, top: insets.top, bottom: insets.bottom, now: timeline.date)
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            appeared = .now
            selectHaptic.prepare()
        }
        .task(id: motionUntil) {
            let wait = motionUntil.timeIntervalSinceNow
            guard wait > 0 else { return }
            try? await Task.sleep(for: .seconds(wait + 0.05))
            if !Task.isCancelled { idle.toggle() }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await runLoop() }
    }

    private func screen(width: Double, height: Double, top: Double, bottom: Double, now: Date) -> some View {
        let rowsTop = top + Self.rowsTop
        let hy = highlight?.value(at: now) ?? Double(current) * Self.pitch
        let target = Double(current) * Self.pitch
        let cardHeight = Self.rowHeight + min(Self.stretchMax, abs(hy - target) * Self.stretch)
        let cardW = width - Self.margin * 2
        let phase = reduceMotion ? 0 : now.timeIntervalSince(appeared) / Self.loop

        return ZStack(alignment: .topLeading) {
            Canvas { context, size in
                context.fill(Path(CGRect(origin: .zero, size: size)), with: .linearGradient(
                    Gradient(colors: Self.sky), startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height)
                ))
                let a = phase * 2 * .pi
                let c = CGPoint(x: width * Self.glowX + Self.orbitX * cos(a), y: rowsTop + Self.glowY + Self.orbitY * sin(a))
                context.fill(
                    Path(ellipseIn: CGRect(x: c.x - Self.glowR, y: c.y - Self.glowR, width: Self.glowR * 2, height: Self.glowR * 2)),
                    with: .radialGradient(Gradient(stops: Self.glow), center: c, startRadius: 0, endRadius: Self.glowR)
                )
                let floor = CGRect(x: 0, y: size.height - Self.floorHeight, width: size.width, height: Self.floorHeight)
                context.fill(Path(floor), with: .linearGradient(
                    Gradient(colors: [Self.floor.opacity(0), Self.floor]), startPoint: CGPoint(x: 0, y: floor.minY), endPoint: CGPoint(x: 0, y: floor.maxY)
                ))
            }
            .frame(width: width, height: height)
            .allowsHitTesting(false)

            VStack(spacing: 8) {
                Text(title)
                    .font(.system(size: 26, weight: .semibold))
                    .frame(height: 32)
                Text(subtitle)
                    .font(.system(size: 16))
                    .lineSpacing(21 - 19.1)
                    .foregroundStyle(.white.opacity(0.6))
                    .multilineTextAlignment(.center)
                    .frame(width: Self.subtitleWidth)
            }
            .foregroundStyle(.white)
            .frame(width: width)
            .offset(y: top + Self.headerTop)
            .allowsHitTesting(false)

            RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
                .fill(.white.opacity(0.13))
                .overlay(
                    RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
                        .fill(LinearGradient(colors: [.white.opacity(0.1), .white.opacity(0)], startPoint: .top, endPoint: .bottom))
                )
                .overlay(
                    RoundedRectangle(cornerRadius: Self.radius, style: .continuous)
                        .strokeBorder(.white.opacity(0.22), lineWidth: 1 / displayScale)
                )
                .frame(width: cardW, height: cardHeight)
                .offset(x: Self.margin, y: rowsTop + hy + (Self.rowHeight - cardHeight) / 2)
                .allowsHitTesting(false)

            ForEach(rows.indices, id: \.self) { i in
                row(i, width: cardW, hy: hy, now: now)
                    .offset(x: Self.margin, y: rowsTop + Double(i) * Self.pitch)
            }

            Button {
                touches += 1
                onDone?()
            } label: {
                Text(footer)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.9))
                    .lineLimit(1)
                    .frame(height: 22)
                    .padding(.horizontal, 16)
                    .padding(.vertical, 10)
            }
            .buttonStyle(GlowGestureGuideFooterStyle())
            .frame(width: width)
            .offset(y: height - max(bottom, 16) - Self.footerGap - 42)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private func row(_ i: Int, width: Double, hy: Double, now: Date) -> some View {
        // 1 when the highlight sits on this row, 0 a row away.
        let active = min(1, max(0, 1 - abs(hy - Double(i) * Self.pitch) / Self.pitch))
        let since = chosen[i].map { min(GlowGestureGuideIcon.playFor, now.timeIntervalSince($0)) } ?? GlowGestureGuideIcon.playFor
        let gesture = rows[i].gesture
        return Button {
            touches += 1
            select(i, auto: false)
        } label: {
            ZStack(alignment: .topLeading) {
                Canvas { context, _ in
                    GlowGestureGuideIcon.draw(gesture, in: context, size: Self.icon, since: since, color: .white)
                }
                .frame(width: Self.icon, height: Self.icon)
                .offset(x: Self.iconLeft, y: (Self.rowHeight - Self.icon) / 2)
                VStack(alignment: .leading, spacing: 0) {
                    Text(rows[i].title)
                        .font(.system(size: 19, weight: .semibold))
                        .frame(height: 23)
                        .opacity(Self.titleDim + (1 - Self.titleDim) * active)
                    Text(rows[i].subtitle)
                        .font(.system(size: 16))
                        .frame(height: 20)
                        .opacity(Self.subtitleDim + (Self.subtitleLit - Self.subtitleDim) * active)
                }
                .foregroundStyle(.white)
                .lineLimit(1)
                .offset(x: Self.textLeft, y: (Self.rowHeight - Self.textHeight) / 2)
            }
            .frame(width: width, height: Self.rowHeight, alignment: .topLeading)
            .scaleEffect(1 + Self.activeScale * active)
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(rows[i].title). \(rows[i].subtitle)")
    }

    // MARK: Behaviour

    private func select(_ next: Int, auto: Bool) {
        guard next != current else { return }
        let now = Date.now
        var spring = highlight ?? GlowGestureGuideSpring(from: Double(current) * Self.pitch, to: Double(current) * Self.pitch)
        spring.retarget(Double(next) * Self.pitch, at: now, still: reduceMotion)
        highlight = spring
        index = next
        if !reduceMotion { chosen[next] = now }
        let until = now + max(GlowGestureGuideSpring.settle, GlowGestureGuideIcon.playFor)
        if until > motionUntil { motionUntil = until }
        if !auto { selectHaptic.selectionChanged() }
        onSelect?(next, rows[next])
    }

    private func runLoop() async {
        guard autoPlay, !rows.isEmpty else { return }
        let clock = ContinuousClock()
        var origin = clock.now
        if touches > 0 {
            try? await clock.sleep(until: origin + .seconds(Self.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            origin = clock.now
            if current != 0 {
                // Back to the first row the way the loop ends, then the loop starts over from there.
                select(0, auto: true)
                origin += .seconds(Self.loop - Self.firstHop - Double(Self.hops.count - 1) * Self.stop)
            }
        }
        while !Task.isCancelled {
            for (k, row) in Self.hops.enumerated() {
                try? await clock.sleep(until: origin + .seconds(Self.firstHop + Double(k) * Self.stop), tolerance: .zero)
                guard !Task.isCancelled else { return }
                select(row % rows.count, auto: true)
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

private struct GlowGestureGuideFooterStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.67 : 1)
    }
}
