import SwiftUI

struct DockExpandSheetMode {
    var label: String
    /// A line under the label.
    var caption: String? = nil
    /// An SF Symbol name.
    var icon: String
    var color: Color
}

/// Calls `content` with the value as it animates; the closure's own mapping (ramps, lerps) runs
/// on every frame, which SwiftUI's modifiers would otherwise interpolate linearly end to end.
private struct DockExpandSheetReader<Content: View>: View, Animatable {
    var value: Double
    @ViewBuilder let content: (Double) -> Content

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    var body: some View { content(value) }
}

/// A night-sky screen with a dock of three glass controls. Pressing the sun button merges the dock
/// into one "Modes" pill that grows upward into a full-screen list, in one container whose frame,
/// corner radius and contents move together; closing runs the same motion back. A tooltip tied to
/// the button by a line breathes while the dock is at rest.
struct DockExpandSheet: View {
    /// The rows of the list the dock opens into.
    var modes = M.modes
    /// The mode that starts out ticked, by place in `modes`.
    var selected = 1
    /// Plays the whole interaction by itself, over and over, and gives way to a touch.
    var autoPlay = false
    var place = "Lisbon"
    var title = "Modes"
    var hint = "Modes to match your routine"
    var travelHint = "Long press and slide to time travel"
    var times = M.times
    var onSelect: ((DockExpandSheetMode, Int) -> Void)? = nil

    private typealias M = DockExpandSheetMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // 0 dock, 1 full screen.
    @State private var morph = 0.0
    @State private var pulse = 0.0
    @State private var press = 0.0
    // Which tooltip shows: 0 the first, 1 the second.
    @State private var tip = 0.0
    @State private var ruler = 0.0
    @State private var tick: Double? = nil
    @State private var mode: Int? = nil
    @State private var isOpen = false
    @State private var touches = 0
    @State private var swiping = false

    private var current: Int { mode ?? selected }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            DockExpandSheetReader(value: morph) { m in
                scene(screen: screen, insets: insets, morph: m)
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(M.night.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func scene(screen: CGSize, insets: EdgeInsets, morph m: Double) -> some View {
        let width = screen.width
        let height = screen.height
        let dockTop = height - insets.bottom - M.dockLift - M.control
        let pillTop = dockTop - (M.mergedHeight - M.control) / 2
        let horizon = insets.top + 250
        let rulerTop = horizon + 36
        let pageT = M.ramp(m, 0.2, 1)
        let grow = M.ramp(m, 0.2, 1)

        return ZStack(alignment: .topLeading) {
            M.night

            // The page: sky and tooltips.
            ZStack(alignment: .topLeading) {
                DockExpandSheetSkyPage(width: width, horizon: horizon, rulerTop: rulerTop, ruler: ruler, times: times)

                ZStack(alignment: .topLeading) {
                    Rectangle().fill(M.yellow).frame(width: M.line, height: M.tooltipGap)
                        .offset(x: M.control / 2 - M.line / 2, y: M.tooltipHeight)
                    tooltip(hint)
                        .scaleEffect(1 + M.pulseScale * pulse, anchor: .bottomLeading)
                }
                .opacity(1 - tip)
                .offset(x: M.margin, y: dockTop - M.tooltipGap - M.tooltipHeight)

                ZStack(alignment: .topLeading) {
                    Rectangle().fill(M.yellow).frame(width: M.line, height: 48)
                        .offset(x: M.travelTipWidth / 2 - M.line / 2, y: -48)
                    Circle().fill(M.yellow).frame(width: 8, height: 8)
                        .offset(x: M.travelTipWidth / 2 - 4, y: -52)
                    tooltip(travelHint, width: M.travelTipWidth)
                }
                .opacity(tip)
                .offset(x: (width - M.travelTipWidth) / 2, y: rulerTop + 92)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .scaleEffect(M.lerp(1, 0.94, pageT))
            .opacity(1 - pageT)

            dock(width: width, m: m)
                .offset(x: M.margin, y: dockTop)
                .allowsHitTesting(!isOpen)

            container(screen: screen, insets: insets, m: m, grow: grow, pillTop: pillTop)
                .allowsHitTesting(isOpen)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private func tooltip(_ text: String, width: CGFloat? = nil) -> some View {
        HStack(spacing: 8) {
            Text(text)
                .font(.system(size: 13, weight: .semibold))
                .tracking(-0.1)
                .foregroundStyle(Color(hex: 0x0d0d0d))
                .lineLimit(1)
                .fixedSize()
            if width != nil { Spacer(minLength: 0) }
            Image(systemName: "xmark")
                .font(.system(size: 8, weight: .bold))
                .foregroundStyle(M.yellow)
                .frame(width: 18, height: 18)
                .background(M.tipClose, in: Circle())
        }
        .padding(.leading, 12)
        .padding(.trailing, 6)
        .frame(width: width, height: M.tooltipHeight)
        .background(M.yellow, in: Capsule())
        .compositingGroup()
        .shadow(color: M.yellow.opacity(0.35), radius: 10)
    }

    private func dock(width: Double, m: Double) -> some View {
        HStack {
            DockExpandSheetReader(value: press) { p in
                Button(action: open) {
                    Image(systemName: "sun.max.fill")
                        .font(.system(size: 18))
                        .foregroundStyle(M.ink)
                        .frame(width: M.control, height: M.control)
                        .background(p > 0.5 ? M.glassPressed : M.glass, in: Circle())
                        .overlay(Circle().strokeBorder(M.glassEdge, lineWidth: M.hairline))
                        .scaleEffect(M.lerp(1, M.pressScale, p))
                }
                .buttonStyle(DockExpandSheetPressStyle { down in
                    if down { touched() }
                    setPress(down)
                })
                .accessibilityLabel("Modes")
            }
            Spacer(minLength: 0)
            HStack(spacing: 7) {
                Image(systemName: "location.fill").font(.system(size: 12)).foregroundStyle(M.location)
                Text(place).font(.system(size: 15, weight: .semibold)).foregroundStyle(M.soft)
            }
            .frame(width: M.centerWidth, height: M.control)
            .background(M.glass, in: Capsule())
            .overlay(Capsule().strokeBorder(M.glassEdge, lineWidth: M.hairline))
            .scaleEffect(x: M.lerp(1, (width - M.margin * 2) / M.centerWidth, M.ramp(m, 0, 0.3)), y: 1)
            Spacer(minLength: 0)
            Image(systemName: "square.grid.2x2.fill")
                .font(.system(size: 15))
                .foregroundStyle(M.soft)
                .frame(width: M.control, height: M.control)
                .background(M.glass, in: Circle())
                .overlay(Circle().strokeBorder(M.glassEdge, lineWidth: M.hairline))
        }
        .frame(width: width - M.margin * 2, height: M.control)
        .opacity(1 - M.ramp(m, 0, 0.2))
    }

    /// The one container: a pill at the dock that grows to the full screen.
    private func container(screen: CGSize, insets: EdgeInsets, m: Double, grow: Double, pillTop: Double) -> some View {
        let w = M.lerp(screen.width - M.margin * 2, screen.width, grow)
        let h = M.lerp(M.mergedHeight, screen.height, grow)
        return ZStack(alignment: .topLeading) {
            M.pill
            LinearGradient(colors: M.list, startPoint: .top, endPoint: .bottom)
                .opacity(M.ramp(m, 0.3, 0.8))

            ZStack(alignment: .topLeading) {
                VStack(spacing: 0) {
                    ForEach(Array(modes.enumerated()), id: \.offset) { index, item in
                        let t = M.ramp(m, 0.45 + Double(index) * 0.04, 0.85 + Double(index) * 0.04)
                        Button {
                            touched()
                            choose(index)
                        } label: {
                            HStack(spacing: 14) {
                                Image(systemName: item.icon)
                                    .font(.system(size: 16))
                                    .foregroundStyle(item.color)
                                    .frame(width: 42, height: 42)
                                    .background(item.color.opacity(0.15), in: RoundedRectangle(cornerRadius: 13))
                                    .overlay(RoundedRectangle(cornerRadius: 13).strokeBorder(item.color.opacity(0.25), lineWidth: M.hairline))
                                VStack(alignment: .leading, spacing: 2) {
                                    Text(item.label)
                                        .font(.system(size: 17, weight: .semibold))
                                        .tracking(-0.3)
                                        .foregroundStyle(M.ink)
                                    if let caption = item.caption {
                                        Text(caption)
                                            .font(.system(size: 13, weight: .medium))
                                            .foregroundStyle(M.muted)
                                    }
                                }
                                .frame(maxWidth: .infinity, alignment: .leading)
                            }
                            .padding(.horizontal, M.margin)
                            .frame(height: M.row)
                            .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(index == current ? .isSelected : [])
                        .opacity(t)
                        .offset(y: M.lerp(16, 0, t))
                    }
                }
                Image(systemName: "checkmark")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(M.tickInk)
                    .frame(width: 24, height: 24)
                    .background(M.tickGreen, in: Circle())
                    .compositingGroup()
                    .shadow(color: M.tickGreen.opacity(0.45), radius: 8)
                    .frame(height: M.row)
                    .frame(maxWidth: .infinity, alignment: .trailing)
                    .padding(.trailing, M.margin)
                    .offset(y: (tick ?? Double(selected)) * M.row)
                    .allowsHitTesting(false)
            }
            .frame(width: w, alignment: .topLeading)
            .offset(y: insets.top + M.rowsTop)

            ZStack {
                HStack {
                    Image(systemName: "arrow.up.arrow.down")
                        .font(.system(size: 13))
                        .foregroundStyle(M.soft)
                        .frame(width: 34, height: 34)
                        .background(M.glass, in: Circle())
                    Spacer(minLength: 0)
                    Button {
                        touched()
                        close()
                    } label: {
                        Image(systemName: "xmark")
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(M.soft)
                            .frame(width: 34, height: 34)
                            .background(M.glass, in: Circle())
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel("Close")
                }
                Text(title)
                    .font(.system(size: 17, weight: .bold))
                    .tracking(-0.2)
                    .foregroundStyle(M.ink)
                    .allowsHitTesting(false)
            }
            .padding(.horizontal, M.margin)
            .frame(width: w, height: M.header)
            .contentShape(Rectangle())
            .gesture(swipe)
            .offset(y: M.lerp(0, insets.top + 4, grow))
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: M.lerp(M.mergedHeight / 2, 0, grow)))
        .opacity(M.ramp(m, 0, 0.12))
        .offset(x: M.lerp(M.margin, 0, grow), y: M.lerp(pillTop, 0, grow))
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { _ in
                if !swiping {
                    swiping = true
                    touched()
                }
            }
            .onEnded { value in
                swiping = false
                if value.translation.height > M.dismissDrag { close() }
            }
    }

    // MARK: Stages

    private func instantly(_ change: () -> Void) {
        var t = Transaction()
        t.disablesAnimations = true
        withTransaction(t, change)
    }

    private func move(to value: Double) -> Animation { reduceMotion ? .easeInOut(duration: M.reducedFor) : M.morphSpring }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() { touches += 1 }

    private func startPulse() {
        guard !reduceMotion else { return }
        instantly { pulse = 0 }
        withAnimation(.timingCurve(0.37, 0, 0.63, 1, duration: M.pulseFor).repeatForever(autoreverses: true)) { pulse = 1 }
    }

    private func stopPulse() {
        withAnimation(.easeInOut(duration: 0.12)) { pulse = 0 }
    }

    private func open() {
        guard !isOpen else { return }
        isOpen = true
        stopPulse()
        withAnimation(move(to: 1)) { morph = 1 }
    }

    private func close() {
        guard isOpen else { return }
        isOpen = false
        withAnimation(move(to: 0)) { morph = 0 }
    }

    private func choose(_ index: Int) {
        mode = index
        withAnimation(.timingCurve(0.215, 0.61, 0.355, 1, duration: reduceMotion ? M.reducedFor : M.tickFor)) { tick = Double(index) }
        onSelect?(modes[index], index)
    }

    private func setPress(_ down: Bool) {
        withAnimation(M.quad(down ? 0.08 : 0.16)) { press = down ? 1 : 0 }
    }

    private func travel(_ on: Bool) {
        withAnimation(M.quad(M.fadeFor)) { tip = on ? 1 : 0 }
        if on {
            withAnimation(.linear(duration: M.travelFor)) { ruler = 1 }
        } else {
            withAnimation(M.quad(M.fadeFor)) { ruler = 0 }
        }
    }

    /// Everything back as it was at the start, with no animation.
    private func rest() {
        instantly {
            morph = 0
            pulse = 0
            press = 0
            tip = 0
            ruler = 0
            tick = nil
            mode = nil
            isOpen = false
        }
    }

    // MARK: autoPlay

    private var steps: [(at: Double, run: () -> Void)] {
        [
            (0, startPulse),
            (M.pressAt, { setPress(true) }),
            (M.openAt, { setPress(false); open() }),
            (M.pickAt, { choose(M.picked) }),
            (M.closeAt, close),
            (M.travelAt, { travel(true) }),
            (M.travelBackAt, { travel(false) }),
            (M.restAt, rest),
        ]
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for step in steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                step.run()
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

/// Reports a press and its release, so the glass button can squash like the rest of the dock.
private struct DockExpandSheetPressStyle: ButtonStyle {
    let onChange: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in onChange(pressed) }
    }
}

enum DockExpandSheetMotion {
    static let modes = [
        DockExpandSheetMode(label: "Classic", caption: "Sunrise, noon and sunset", icon: "star.fill", color: Color(hex: 0x4b8df8)),
        DockExpandSheetMode(label: "Daily", caption: "Your day around the light", icon: "sun.max.fill", color: Color(hex: 0xf7a23b)),
        DockExpandSheetMode(label: "Photography", caption: "Golden and blue hours", icon: "camera.fill", color: Color(hex: 0xc057ea)),
        DockExpandSheetMode(label: "Mindfulness", caption: "Quiet moments to pause", icon: "leaf.fill", color: Color(hex: 0x44d07a)),
        DockExpandSheetMode(label: "Hiking", caption: "Daylight left on the trail", icon: "figure.walk", color: Color(hex: 0xd6a878)),
        DockExpandSheetMode(label: "Moon Watching", caption: "Moonrise and phase", icon: "moon.fill", color: Color(hex: 0x8b6cf6)),
    ]
    static let times = [
        ["Sunrise", "6:08"],
        ["Golden hour", "7:12"],
        ["Solar noon", "12:41"],
        ["Sunset", "19:14"],
    ]

    /// The autoPlay loop, in seconds from its start.
    static let loop = 10.0
    static let pressAt = 2.6
    static let openAt = 2.9
    static let pickAt = 4.6
    static let closeAt = 6.2
    static let travelAt = 7.2
    static let travelBackAt = 9.1
    static let restAt = 9.7
    /// The mode the loop moves the tick to, by place in the list.
    static let picked = 2
    /// Seconds a touch keeps the loop away.
    static let userHold = 3.0

    static let night = Color(hex: 0x05060a)
    static let glass = Color.white.opacity(0.1)
    static let glassPressed = Color.white.opacity(0.25)
    static let glassEdge = Color.white.opacity(0.12)
    static let muted = Color.white.opacity(0.5)
    static let tipClose = Color(hex: 0x14160c)
    static let tickInk = Color(hex: 0x0b0f17)
    /// React Native's hairline width doubled, on a 3x screen.
    static let hairline = 2.0 / 3
    static let pill = Color(hex: 0x161c2c)
    static let list = [Color(hex: 0x101b33), Color(hex: 0x0a0d14)]
    static let ink = Color.white
    static let soft = Color.white.opacity(0.8)
    static let yellow = Color(hex: 0xe9ff70)
    static let tickGreen = Color(hex: 0xb8e04c)
    static let location = Color(hex: 0x3b9be0)

    static let margin = 20.0
    static let control = 48.0
    static let centerWidth = 136.0
    static let mergedHeight = 52.0
    static let dockLift = 32.0
    static let tooltipHeight = 30.0
    static let tooltipGap = 44.0
    static let line = 1.5
    static let row = 68.0
    static let rowsTop = 84.0
    static let header = 52.0
    static let travelTipWidth = 262.0
    static let pressScale = 0.92
    static let pulseScale = 0.03
    static let pulseFor = 0.625
    static let dismissDrag = 80.0

    static let morphSpring = Animation.spring(duration: 0.55, bounce: 0.12)
    static let tickFor = 0.25
    static let travelFor = 1.6
    static let fadeFor = 0.4
    static let reducedFor = 0.16

    /// Reanimated's default easing for `withTiming`.
    static func quad(_ duration: Double) -> Animation { .timingCurve(0.455, 0.03, 0.515, 0.955, duration: duration) }

    static func lerp(_ from: Double, _ to: Double, _ t: Double) -> Double { from + (to - from) * t }
    /// Smooth 0 → 1 between `from` and `to`.
    static func ramp(_ value: Double, _ from: Double, _ to: Double) -> Double {
        let t = min(1, max(0, (value - from) / (to - from)))
        return t * t * (3 - 2 * t)
    }
}
