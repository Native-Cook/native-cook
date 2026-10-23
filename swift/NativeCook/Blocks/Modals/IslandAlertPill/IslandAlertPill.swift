import SwiftUI

struct IslandAlertPillDevice: Equatable {
    var name: String
    var detail: String
    /// Charge in percent; the first device follows the island's charge instead.
    var charge: Int
    var symbol: String
    /// Background of the icon's tile and the colour of its glyph.
    var tint: Color
    var ink: Color

    static let samples = [
        IslandAlertPillDevice(name: "This Phone", detail: "Low Power Mode", charge: 10, symbol: "iphone", tint: Color(hex: 0xffe3e0), ink: Color(hex: 0xff3b30)),
        IslandAlertPillDevice(name: "Watch", detail: "On wrist · 2 days left", charge: 64, symbol: "applewatch", tint: Color(hex: 0xe2ecff), ink: Color(hex: 0x2f6bff)),
        IslandAlertPillDevice(name: "Earbuds", detail: "In case · Connected", charge: 82, symbol: "headphones", tint: Color(hex: 0xe0f5e6), ink: Color(hex: 0x24a148)),
        IslandAlertPillDevice(name: "Tablet", detail: "At home · Charging", charge: 47, symbol: "ipad", tint: Color(hex: 0xfff1d6), ink: Color(hex: 0xe08a00)),
        IslandAlertPillDevice(name: "Keyboard", detail: "Paired · Desk", charge: 91, symbol: "circle.grid.3x3.fill", tint: Color(hex: 0xeee6ff), ink: Color(hex: 0x7a4dff)),
        IslandAlertPillDevice(name: "Pencil", detail: "Paired · Last used today", charge: 28, symbol: "pencil", tint: Color(hex: 0xddf4f6), ink: Color(hex: 0x0f9fae)),
    ]
}

enum IslandAlertPillMotion {
    // The timeline of one loop, in seconds from its start: the pill rests, the alert grows it into
    // the island, the track draws, the battery charges, the knob snaps back, the island shrinks.
    static let loop = 9.0
    static let pulseAt = 0.0
    static let expandAt = 0.8
    static let drawAt = 1.6
    static let chargeAt = 2.4
    static let chargeFor = 2.5
    static let snapAt = 5.4
    static let collapseAt = 6.2
    // After a touch the loop waits this long, then starts again from the compact pill.
    static let userHold = 3.0

    static let pulseFor = 0.4
    static let pulseLow = 0.5
    static let shakeFor = 0.04
    static let shakePoints = 2.0
    // Springs as (duration, damping ratio).
    static let expandSpring = Spring(duration: 0.45, bounce: 1 - 0.72)
    static let collapseSpring = Spring(duration: 0.45, bounce: 1 - 0.85)
    static let popSpring = Spring(duration: 0.35, bounce: 1 - 0.6)
    static let snapSpring = Spring(duration: 0.7, bounce: 1 - 0.45)
    static let drawFor = 0.5
    static let labelFor = 0.3
    static let contentOut = 0.15
    static let fade = 0.2
    static let breath = 0.6
    static let glowLow = 0.55
    static let glowRest = 0.8

    // Reanimated's default easing, and the ones the block names.
    static func inOutQuad(_ t: Double) -> Double { t < 0.5 ? 2 * t * t : 1 - 2 * (1 - t) * (1 - t) }
    static func outCubic(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
    static func inOutSine(_ t: Double) -> Double { (1 - cos(Double.pi * t)) / 2 }
    static func linear(_ t: Double) -> Double { t }

    /// The bolt dims and comes back once.
    static func pulse(_ t: Double) -> Double {
        if t <= 0 || t >= pulseFor * 2 { return 1 }
        return t < pulseFor
            ? 1 - (1 - pulseLow) * inOutQuad(t / pulseFor)
            : pulseLow + (1 - pulseLow) * inOutQuad((t - pulseFor) / pulseFor)
    }

    /// Two small shakes sideways as the island opens.
    static func shake(_ t: Double) -> Double {
        let points = [0, shakePoints, -shakePoints, shakePoints, -shakePoints, 0]
        guard t > 0, t < shakeFor * 5 else { return 0 }
        let i = Int(t / shakeFor)
        return points[i] + (points[i + 1] - points[i]) * inOutQuad((t - Double(i) * shakeFor) / shakeFor)
    }
}

private typealias M = IslandAlertPillMotion

private enum Palette {
    static let red = Color(hex: 0xff3b30)
    static let track = Color(hex: 0x333333)
    static let page = Color(hex: 0xf2f2f5)
    static let green = Color(hex: 0x34c759)
    static let ringTrack = Color(hex: 0xececf0)
    static let ink = Color(hex: 0x111114)
    static let mute = Color(hex: 0x7c7c83)
    static let rule = Color.rgba(60, 60, 67, 0.22)
    static let batteryEdge = Color.rgba(60, 60, 67, 0.35)
}

private enum Metrics {
    static let compactWidth = 280.0
    static let compactHeight = 40.0
    static let expandedHeight = 100.0
    static let expandedMargin = 20.0
    static let expandedGap = 51.0
    static let compactRadius = 20.0
    static let expandedRadius = 40.0
    static let sidePadding = 20.0
    static let labelWidth = 74.0
    static let trackHeight = 8.0
    static let chipWidth = 44.0
    static let chipHeight = 24.0
    static let grabHeight = 48.0
    /// How many devices the widget at the top shows as rings.
    static let rings = 4
    /// Below this a charge is drawn red.
    static let low = 20
    static let ringSize = 62.0
    static let ringStroke = 6.0
    static let rowHeight = 62.0
}

private func clamp(_ value: Double, _ low: Double, _ high: Double) -> Double {
    min(high, max(low, value))
}

/// A page and, on top of it, a dynamic-island pill showing a low battery. An alert grows the pill
/// into an island with a track, a knob that carries the percent, and the time left; the knob can
/// be dragged to preview a charge and springs back. A long press expands the pill, a tap on the
/// page shrinks it.
struct IslandAlertPill: View {
    /// Plays the alert by itself, in a loop, and leaves the pill compact.
    var autoPlay = false
    /// The charge the alert opens at, in percent.
    var level = 10.0
    /// The charge the autoPlay run reaches.
    var chargedTo = 38.0
    /// Written at the right of the expanded island.
    var remaining = "~15m"
    /// What the page under the island lists.
    var devices = IslandAlertPillDevice.samples
    /// Called with the percent while the knob is dragged.
    var onLevelChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var isOpen = false
    @State private var expanded = false
    /// True while something moves, so the clock only runs then.
    @State private var live = false
    @State private var pulseStart = Date.distantPast
    @State private var shakeStart = Date.distantPast
    /// 0 is the compact pill, 1 the island; the spring may pass either end.
    @State private var grow = IslandAlertPillChannel(0)
    /// How much of the track is drawn, how far the knob has popped in, and the label's opacity.
    @State private var draw = IslandAlertPillChannel(0)
    @State private var pop = IslandAlertPillChannel(0)
    @State private var label = IslandAlertPillChannel(0)
    @State private var glow = IslandAlertPillChannel(M.glowRest)
    @State private var charge = IslandAlertPillChannel(10)
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var touching = false
    @State private var lastReported = -1
    @State private var dragging = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .medium)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let compactTop = insets.top >= 54 ? 10.0 : insets.top + 6
            let expandedTop = compactTop + Metrics.expandedGap
            let expandedWidth = screen.width - Metrics.expandedMargin * 2
            let trackWidth = expandedWidth - Metrics.sidePadding * 2 - Metrics.labelWidth

            // The page lists the island's charge too, so it is drawn on the same clock.
            TimelineView(.animation(paused: !live)) { timeline in
                let percent = Int(clamp(charge.value(at: timeline.date), 0, 100).rounded())
                ZStack(alignment: .topLeading) {
                    page(percent: percent, top: expandedTop + Metrics.expandedHeight + 20)
                    island(
                        at: timeline.date, screenWidth: screen.width, compactTop: compactTop,
                        expandedTop: expandedTop, expandedWidth: expandedWidth, trackWidth: trackWidth
                    )
                }
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Palette.page.ignoresSafeArea())
        .preferredColorScheme(.light)
        .statusBarHidden()
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard autoPlay, !touching else { return }
                    touching = true
                    touches += 1
                }
                .onEnded { _ in touching = false }
        )
        .onAppear {
            charge.set(level)
            haptic.prepare()
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(percent: Int, top: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("Batteries")
                .font(.system(size: 34, weight: .heavy))
                .tracking(-1)
                .foregroundStyle(Palette.ink)
                .frame(height: 40)
            Text("\(devices.count) devices · Updated just now")
                .font(.system(size: 15))
                .foregroundStyle(Palette.mute)
                .frame(height: 20)
                .padding(.top, 2)

            HStack(alignment: .top, spacing: 0) {
                ForEach(Array(devices.prefix(Metrics.rings).enumerated()), id: \.offset) { i, device in
                    let value = i == 0 ? percent : device.charge
                    if i > 0 { Spacer(minLength: 0) }
                    VStack(spacing: 8) {
                        IslandAlertPillRing(value: value)
                            .overlay {
                                Image(systemName: device.symbol)
                                    .font(.system(size: 19, weight: .medium))
                                    .foregroundStyle(Palette.ink)
                            }
                        Text("\(value)%")
                            .font(.system(size: 15, weight: .bold))
                            .monospacedDigit()
                            .tracking(-0.2)
                            .foregroundStyle(value < Metrics.low ? Palette.red : Palette.ink)
                            .frame(height: 20)
                    }
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 18)
            .padding(.bottom, 14)
            .modifier(IslandAlertPillCard())
            .padding(.top, 16)

            Text("DEVICES")
                .font(.system(size: 13, weight: .semibold))
                .tracking(0.4)
                .foregroundStyle(Palette.mute)
                .frame(height: 18)
                .padding(.leading, 16)
                .padding(.top, 24)
                .padding(.bottom, 8)

            VStack(spacing: 0) {
                ForEach(Array(devices.enumerated()), id: \.offset) { i, device in
                    row(device, value: i == 0 ? percent : device.charge, ruled: i > 0)
                }
            }
            .modifier(IslandAlertPillCard())
        }
        .padding(.horizontal, 20)
        .padding(.top, top)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background(Palette.page)
        .contentShape(Rectangle())
        .onTapGesture { collapse() }
        .accessibilityLabel("Page")
    }

    private func row(_ device: IslandAlertPillDevice, value: Int, ruled: Bool) -> some View {
        HStack(spacing: 12) {
            Image(systemName: device.symbol)
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(device.ink)
                .frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(device.tint))
            HStack(spacing: 0) {
                VStack(alignment: .leading, spacing: 1) {
                    Text(device.name)
                        .font(.system(size: 17, weight: .semibold))
                        .tracking(-0.3)
                        .foregroundStyle(Palette.ink)
                        .frame(height: 22)
                    Text(device.detail)
                        .font(.system(size: 13))
                        .foregroundStyle(Palette.mute)
                        .frame(height: 17)
                }
                .lineLimit(1)
                Spacer(minLength: 0)
                Text("\(value)%")
                    .font(.system(size: 15, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(value < Metrics.low ? Palette.red : Palette.ink)
                    .frame(height: 20)
                    .padding(.trailing, 8)
                IslandAlertPillBattery(value: value)
            }
            .padding(.trailing, 16)
            .frame(maxHeight: .infinity)
            .overlay(alignment: .top) {
                if ruled { Rectangle().fill(Palette.rule).frame(height: 1 / displayScale) }
            }
        }
        .padding(.leading, 16)
        .frame(height: Metrics.rowHeight)
    }

    // MARK: The island

    private func island(
        at date: Date, screenWidth: Double, compactTop: Double, expandedTop: Double, expandedWidth: Double, trackWidth: Double
    ) -> some View {
        let g = max(grow.value(at: date), 0)
        let width = Metrics.compactWidth + (expandedWidth - Metrics.compactWidth) * g
        let height = Metrics.compactHeight + (Metrics.expandedHeight - Metrics.compactHeight) * g
        let radius = Metrics.compactRadius + (Metrics.expandedRadius - Metrics.compactRadius) * min(g, 1.2)
        let top = compactTop + (expandedTop - compactTop) * g
        let held = clamp(charge.value(at: date), 0, 100)
        let percent = Int(held.rounded())
        let fillEnd = held / 100 * trackWidth
        let chipX = clamp(fillEnd - 4, 0, trackWidth - Metrics.chipWidth)
        let popped = pop.value(at: date)
        let drawn = clamp(draw.value(at: date), 0, 1)
        let shake = M.shake(date.timeIntervalSince(shakeStart))

        return ZStack(alignment: .topLeading) {
            // The long press that opens the pill sits under everything else.
            Color.clear
                .contentShape(Rectangle())
                .onLongPressGesture(minimumDuration: 0.35) {
                    haptic.impactOccurred()
                    expand()
                }
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Battery \(percent) percent")

            HStack {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(Palette.red)
                    .opacity(M.pulse(date.timeIntervalSince(pulseStart)))
                Spacer(minLength: 0)
                Text("\(percent)")
                    .font(.system(size: 16, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Palette.red)
            }
            .padding(.horizontal, 14)
            .frame(width: width, height: height)
            .opacity(clamp(1 - g * 3, 0, 1))
            .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                ZStack(alignment: .topLeading) {
                    Capsule().fill(Palette.track).frame(width: trackWidth, height: Metrics.trackHeight)
                    Capsule().fill(Palette.red).frame(width: fillEnd, height: Metrics.trackHeight)
                }
                .frame(width: trackWidth * drawn, height: Metrics.trackHeight, alignment: .topLeading)
                .clipShape(Capsule())
                .offset(y: Metrics.expandedHeight / 2 - Metrics.trackHeight / 2)

                Capsule().fill(Palette.red)
                    .frame(width: Metrics.chipWidth, height: Metrics.chipHeight)
                    .shadow(color: Palette.red.opacity(0.7), radius: 9)
                    .opacity(glow.value(at: date) * clamp(popped, 0, 1))
                    .offset(x: chipX, y: Metrics.expandedHeight / 2 - Metrics.chipHeight / 2)
                    .allowsHitTesting(false)

                HStack(spacing: 2) {
                    Image(systemName: "bolt.fill")
                        .font(.system(size: 10, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.6))
                    Text("\(percent)")
                        .font(.system(size: 16, weight: .bold))
                        .monospacedDigit()
                        .foregroundStyle(.white)
                }
                .frame(width: Metrics.chipWidth, height: Metrics.chipHeight)
                .background(Capsule().fill(Palette.red))
                .scaleEffect(max(popped, 0))
                .opacity(clamp(popped * 2, 0, 1))
                .offset(x: chipX, y: Metrics.expandedHeight / 2 - Metrics.chipHeight / 2)
                .allowsHitTesting(false)

                Color.clear
                    .frame(width: trackWidth, height: Metrics.grabHeight)
                    .contentShape(Rectangle())
                    .gesture(drag(trackWidth: trackWidth))
                    .offset(y: Metrics.expandedHeight / 2 - Metrics.grabHeight / 2)

                Text(remaining)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Palette.red)
                    .lineLimit(1)
                    .frame(height: 22)
                    .opacity(label.value(at: date))
                    .offset(x: trackWidth + 12, y: Metrics.expandedHeight / 2 - 11)
                    .allowsHitTesting(false)
            }
            .padding(.leading, Metrics.sidePadding)
            .allowsHitTesting(isOpen)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: radius, style: .continuous)
                .fill(.black)
                .shadow(color: .black.opacity(0.16 * clamp(g, 0, 1)), radius: 4, y: 4)
                .shadow(color: .black.opacity(0.24 * clamp(g, 0, 1)), radius: 20, y: 18)
        )
        .offset(x: (screenWidth - width) / 2 + shake, y: top)
    }

    private func drag(trackWidth: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let percent = clamp(value.location.x / trackWidth * 100, 0, 100)
                if !dragging {
                    dragging = true
                    glow.set(1)
                }
                charge.set(percent)
                let rounded = Int(percent.rounded())
                if rounded != lastReported {
                    lastReported = rounded
                    onLevelChange?(rounded)
                }
            }
            .onEnded { _ in
                dragging = false
                glow.go(to: M.glowRest, .timing(M.fade, M.linear))
                charge.go(to: level, .spring(M.snapSpring))
            }
    }

    // MARK: Behaviour

    private func expand() {
        guard !expanded else { return }
        expanded = true
        isOpen = true
        live = true
        let now = Date.now
        if reduceMotion {
            grow.go(to: 1, .timing(M.fade, M.inOutQuad), at: now)
        } else {
            grow.go(to: 1, .spring(M.expandSpring), at: now)
            shakeStart = now
        }
        let wait = M.drawAt - M.expandAt
        draw.go(to: 1, .timing(reduceMotion ? M.fade : M.drawFor, M.outCubic), delay: wait, at: now)
        pop.go(to: 1, reduceMotion ? .timing(M.fade, M.inOutQuad) : .spring(M.popSpring), delay: wait + M.drawFor * 0.5, at: now)
        label.go(to: 1, .timing(M.labelFor, M.inOutQuad), delay: wait + M.drawFor * 0.6, at: now)
    }

    private func collapse() {
        guard expanded else { return }
        expanded = false
        let now = Date.now
        glow.set(M.glowRest)
        draw.go(to: 0, .timing(M.contentOut, M.inOutQuad), at: now)
        pop.go(to: 0, .timing(M.contentOut, M.inOutQuad), at: now)
        label.go(to: 0, .timing(M.contentOut, M.inOutQuad), at: now)
        charge.go(to: level, .timing(M.contentOut, M.inOutQuad), at: now)
        if reduceMotion {
            grow.go(to: 0, .timing(M.fade, M.inOutQuad), at: now)
        } else {
            grow.go(to: 0, .spring(M.collapseSpring), delay: M.contentOut, at: now)
        }
        // The spring has settled by then: the island is a pill again and the clock can stop.
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.contentOut + 1.2), tolerance: .zero)
            guard !expanded else { return }
            isOpen = false
            live = false
        }
    }

    private func startCharge() {
        glow.go(to: M.glowLow, .breath)
        charge.go(to: chargedTo, .timing(M.chargeFor, M.linear))
    }

    private func snapBack() {
        glow.go(to: M.glowRest, .timing(M.fade, M.linear))
        charge.go(to: level, reduceMotion ? .timing(M.fade, M.linear) : .spring(M.snapSpring))
    }

    private func pulseOnce() {
        live = true
        pulseStart = .now
        Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.pulseFor * 2 + 0.1), tolerance: .zero)
            if !expanded { live = false }
        }
    }

    // MARK: The autoPlay loop

    private struct Step {
        let at: Double
        let run: @MainActor (IslandAlertPill) -> Void
    }

    private static let steps = [
        Step(at: M.pulseAt) { $0.pulseOnce() },
        Step(at: M.expandAt) { $0.expand() },
        Step(at: M.chargeAt) { $0.startCharge() },
        Step(at: M.snapAt) { $0.snapBack() },
        Step(at: M.collapseAt) { $0.collapse() },
    ]

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            collapse()
            try? await Task.sleep(for: .seconds(0.6), tolerance: .zero)
        }
        var origin = clock.now
        while !Task.isCancelled {
            for step in Self.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run(self)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

/// A battery ring of the widget: a grey track and the charge drawn clockwise from the top.
private struct IslandAlertPillRing: View {
    let value: Int

    var body: some View {
        let inset = Metrics.ringStroke / 2
        ZStack {
            Circle().inset(by: inset).stroke(Palette.ringTrack, lineWidth: Metrics.ringStroke)
            Circle().inset(by: inset)
                .trim(from: 0, to: clamp(Double(value), 0, 100) / 100)
                .stroke(value < Metrics.low ? Palette.red : Palette.green, style: StrokeStyle(lineWidth: Metrics.ringStroke, lineCap: .round))
                .rotationEffect(.degrees(-90))
        }
        .frame(width: Metrics.ringSize, height: Metrics.ringSize)
    }
}

/// The small horizontal battery at the end of a row.
private struct IslandAlertPillBattery: View {
    let value: Int

    var body: some View {
        let colour = value < Metrics.low ? Palette.red : value > 80 ? Palette.green : Palette.ink
        HStack(spacing: 1) {
            RoundedRectangle(cornerRadius: 3.5, style: .continuous)
                .strokeBorder(Palette.batteryEdge, lineWidth: 1)
                .frame(width: 25, height: 12)
                .overlay(alignment: .leading) {
                    // The fill sits inside the 1 pt edge and 1.5 pt of padding.
                    RoundedRectangle(cornerRadius: 2, style: .continuous)
                        .fill(colour)
                        .frame(width: (25 - 5) * Double(max(8, value)) / 100, height: 12 - 5)
                        .padding(.leading, 2.5)
                }
            UnevenRoundedRectangle(bottomTrailingRadius: 1, topTrailingRadius: 1)
                .fill(Palette.batteryEdge)
                .frame(width: 2, height: 4)
        }
    }
}

/// The white card of the page, with a hairline and a soft shadow.
private struct IslandAlertPillCard: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(maxWidth: .infinity)
            .background(
                RoundedRectangle(cornerRadius: 26, style: .continuous)
                    .fill(.white)
                    .shadow(color: .black.opacity(0.04), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.06), radius: 12, y: 8)
            )
    }
}
