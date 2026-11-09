import SwiftUI
import UIKit

// Murmur's studio switch. The six-second loop and all material/light values are
// shared with the React Native reference; no implicit animation drives the lamp.
struct OnAirToggle: View {
    var autoPlay = false
    static let loop: TimeInterval = 6

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @Environment(\.displayScale) private var displayScale
    @State private var playback = OnAirTogglePlayback()
    @State private var visible = false
    @State private var interaction = 0
    @State private var tracking = false
    @State private var dragging = false
    @State private var tapFailed = false
    @State private var touchBegan = Date()
    @State private var grab = 0.0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @GestureState private var gestureActive = false

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: !visible || scene != .active ||
                                    !playback.isAnimating(reduced: reduced, autoPlay: autoPlay))) { timeline in
                let frame = playback.frame(at: timeline.date, reduced: reduced, autoPlay: autoPlay)
                let state = frame.state
                let ink = OnAirToggleDrawing.mix(0x1D1A1A, 0xF3EEEA, OnAirToggleMotion.inkMix(state.room), rounded: true)
                let cy = size.height * 0.48
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in
                        OnAirToggleDrawing.scene(context, size: size, state: state,
                                                 cross: frame.cross, time: frame.meterTime, reduced: reduced)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)

                    heading(safe: safe, width: size.width, ink: ink)
                    status(state: state, ink: ink)
                        .frame(width: size.width, height: 24)
                        .offset(y: cy + 74 + 18 + 30)
                    footer(safe: safe, width: size.width, state: state, ink: ink)
                        .frame(width: size.width, height: size.height, alignment: .bottom)

                    Color.clear
                        .frame(width: 324, height: 198)
                        .contentShape(Rectangle())
                        .gesture(switchGesture)
                        .offset(x: size.width / 2 - 162, y: cy - 90)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel("On air")
                        .accessibilityValue(state.room > 0.5 ? "On" : "Off")
                        .accessibilityAddTraits([.isButton, .isToggle])
                        .accessibilityAction { toggle() }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .preferredColorScheme(state.room > 0.5 ? .dark : .light)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .onAppear {
            playback.reset(at: .now)
            visible = true
            haptic.prepare()
        }
        .onDisappear { visible = false; playback.suspend() }
        .onChange(of: reduced) { _, _ in reset() }
        .onChange(of: autoPlay) { _, _ in reset() }
        .onChange(of: scene) { _, phase in
            if phase == .active { playback.resume(at: .now) } else { playback.suspend() }
            interaction += 1
        }
        .onChange(of: gestureActive) { _, active in
            guard !active else { return }
            Task { @MainActor in
                await Task.yield()
                guard tracking, !gestureActive else { return }
                if dragging { settle(to: playback.state.pos > 0.5 ? 1 : 0) }
                tracking = false
                dragging = false
            }
        }
        .task(id: interaction) {
            // One wake-up at the end of a transition lets an idle/off timeline
            // pause. Frame rendering never publishes or writes SwiftUI state.
            guard visible, scene == .active,
                  let delay = playback.transitionRemaining(reduced: reduced, autoPlay: autoPlay) else { return }
            try? await Task.sleep(for: .seconds(delay + 0.02))
            guard !Task.isCancelled, visible, scene == .active else { return }
            _ = playback.frame(at: .now, reduced: reduced, autoPlay: autoPlay)
            interaction += 1
        }
    }

    private func heading(safe: EdgeInsets, width: CGFloat, ink: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 0) {
                Text("Murmur").font(.system(size: 20, weight: .heavy)).tracking(-0.4)
                Spacer(minLength: 0)
                Text("Studio A · 48 kHz").font(.system(size: 13, weight: .semibold)).opacity(0.6)
            }
            .frame(height: 32)
            Text("EPISODE 48 · TUESDAY")
                .font(.system(size: 12, weight: .bold)).tracking(2).opacity(0.5)
                .padding(.top, 22)
            Text("The Quiet Hours")
                .font(.system(size: 34, weight: .bold)).tracking(-0.8)
                .frame(height: 40, alignment: .leading).padding(.top, 6)
            Text("Ada Kole with two guests from the night shift")
                .font(.system(size: 15)).opacity(0.62)
                .frame(height: 20, alignment: .leading).padding(.top, 4)
        }
        .foregroundStyle(ink)
        .frame(width: width - 48, alignment: .leading)
        .offset(x: 24, y: safe.top + 8)
        .allowsHitTesting(false)
    }

    private func status(state: OnAirToggleState, ink: Color) -> some View {
        let g = OnAirToggleMotion.clamp01(state.glow)
        return HStack(spacing: 8) {
            Circle()
                .fill(OnAirToggleDrawing.mix(0xB5AEA8, 0xFF4B3A, g, rounded: true))
                .frame(width: 8, height: 8)
                .shadow(color: OnAirToggleDrawing.color(0xFF4B3A, 0.9 * g), radius: 6)
            Text("Off air")
                .opacity(OnAirToggleMotion.clamp01((0.5 - state.room) * 3))
                .overlay(alignment: .leading) {
                    Text("On air").opacity(OnAirToggleMotion.clamp01((state.room - 0.5) * 3))
                }
        }
        .font(.system(size: 17, weight: .semibold)).tracking(0.2)
        .foregroundStyle(ink)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func footer(safe: EdgeInsets, width: CGFloat, state: OnAirToggleState, ink: Color) -> some View {
        let room = state.room
        let border = room > 0.5 ? Color.white.opacity(0.1 * room) :
            OnAirToggleDrawing.color(0x1D1A1A, 0.08 * (1 - room))
        return VStack(spacing: 0) {
            HStack(alignment: .top) {
                VStack(alignment: .leading, spacing: 3) {
                    Text("Mic 1 · Ribbon").font(.system(size: 16, weight: .bold))
                    Text("Input −12 dB").font(.system(size: 14)).opacity(0.62)
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 3) {
                    Text("Guest line").font(.system(size: 16, weight: .bold))
                    Text("2 waiting").font(.system(size: 14)).opacity(0.62)
                }
            }
            .padding(.vertical, 16).padding(.horizontal, 18)
            .padding(1 / displayScale)
            .background(.white.opacity(OnAirToggleMotion.mixN(0.55, 0.06, room)), in: RoundedRectangle(cornerRadius: 22))
            .overlay { RoundedRectangle(cornerRadius: 22).strokeBorder(border, lineWidth: 1 / displayScale) }
            Text("Tap the switch or slide the lamp to go live.")
                .font(.system(size: 13)).opacity(0.5)
                .frame(maxWidth: .infinity).padding(.top, 12)
        }
        .foregroundStyle(ink)
        .padding(.horizontal, 20).padding(.bottom, safe.bottom + 14)
        .frame(width: width)
        .allowsHitTesting(false)
    }

    private var switchGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                if !tracking { tracking = true; tapFailed = false; touchBegan = .now }
                if hypot(value.translation.width, value.translation.height) >= 10 { tapFailed = true }
                guard !reduced else { return }
                if !dragging, hypot(value.translation.width, value.translation.height) >= 6 {
                    _ = playback.frame(at: .now, reduced: reduced, autoPlay: autoPlay)
                    grab = playback.state.pos
                    playback.beginDrag()
                    dragging = true
                    interaction += 1
                }
                if dragging {
                    playback.drag(to: OnAirToggleMotion.rubberBand(grab + value.translation.width / 152))
                }
            }
            .onEnded { value in
                if dragging {
                    settle(to: playback.state.pos > 0.5 ? 1 : 0)
                } else if !tapFailed, Date().timeIntervalSince(touchBegan) <= 0.5,
                          hypot(value.translation.width, value.translation.height) < (reduced ? 10 : 6),
                          CGRect(x: 0, y: 0, width: 324, height: 198).contains(value.location) {
                    toggle()
                }
                tracking = false
                dragging = false
            }
    }

    private func toggle() {
        let frame = playback.frame(at: .now, reduced: reduced, autoPlay: autoPlay)
        settle(to: (reduced ? frame.cross : frame.state.pos) > 0.5 ? 0 : 1)
    }

    private func settle(to target: Double) {
        _ = playback.frame(at: .now, reduced: reduced, autoPlay: autoPlay)
        playback.settle(to: target, reduced: reduced)
        haptic.impactOccurred()
        haptic.prepare()
        interaction += 1
    }

    private func reset() {
        playback.reset(at: .now)
        tracking = false
        dragging = false
        interaction += 1
    }
}

private struct OnAirToggleState {
    var pos = 0.0
    var glow = 0.0
    var room = 0.0
    var press = 0.0
    var timer = 0.0
}

private struct OnAirToggleFrame {
    var state: OnAirToggleState
    var cross: Double
    var meterTime: Double
}

private enum OnAirToggleMotion {
    static let settle = 0.93
    static let hold = 2.0
    static let fade = 0.3

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOutQuad(_ x: Double) -> Double { 1 - (1 - x) * (1 - x) }
    static func easeOutCubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOutCubic(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func mixN(_ a: Double, _ b: Double, _ u: Double) -> Double { a + (b - a) * u }
    static func inkMix(_ room: Double) -> Double {
        let u = clamp01((room - 0.4) / 0.2)
        return u * u * (3 - 2 * u)
    }

    static func springUnit(_ s: Double) -> Double {
        guard s > 0 else { return 0 }
        let zeta = 0.72
        let w = 2 * Double.pi / 0.5
        let wd = w * sqrt(1 - zeta * zeta)
        let dev = exp(-zeta * w * s) * (cos(wd * s) + zeta * w / wd * sin(wd * s))
        let tail = 1 - easeInOutCubic(seg(s, 0.6, 0.78))
        return 1 - dev * tail
    }

    static func ignite(_ u: Double) -> Double {
        if u <= 0 { return 0 }
        if u < 0.06 { return 0.55 * (u / 0.06) }
        if u < 0.12 { return mixN(0.55, 0.15, (u - 0.06) / 0.06) }
        if u < 0.18 { return mixN(0.15, 0.75, (u - 0.12) / 0.06) }
        if u < 0.24 { return mixN(0.75, 0.4, (u - 0.18) / 0.06) }
        return mixN(0.4, 1, easeOutCubic(seg(u, 0.24, 0.6)))
    }

    static func loopAt(_ t: Double) -> OnAirToggleState {
        var pos = 0.0
        if t >= 0.8 && t < 0.92 { pos = -0.03 * easeOutQuad(seg(t, 0.8, 0.92)) }
        else if t >= 0.92 && t < 3.9 { pos = -0.03 + 1.03 * springUnit(t - 0.92) }
        else if t >= 3.9 && t < 4 { pos = 1 + 0.03 * easeOutQuad(seg(t, 3.9, 4)) }
        else if t >= 4 { pos = 1.03 - 1.03 * springUnit(t - 4) }
        let pressA = easeOutQuad(seg(t, 0.8, 0.92)) * (1 - easeOutQuad(seg(t, 0.92, 1.12)))
        let pressB = easeOutQuad(seg(t, 3.9, 4)) * (1 - easeOutQuad(seg(t, 4, 4.2)))
        let breathe = seg(t, 1.7, 2) * (1 - seg(t, 3.7, 4))
        let pulse = 1 + 0.05 * breathe * sin(2 * Double.pi * 5 * t / OnAirToggle.loop)
        let glow = ignite(t - 1.1) * (1 - easeInOutCubic(seg(t, 4.1, 4.5))) * pulse
        let room = easeInOutCubic(seg(t, 1.15, 1.85)) * (1 - easeInOutCubic(seg(t, 4.15, 4.85)))
        return OnAirToggleState(pos: pos, glow: glow, room: room, press: pressA + pressB,
                                timer: room > 0.5 ? max(0, t - 1.3) : 0)
    }

    static func barLevel(_ index: Int, _ time: Double) -> Double {
        let i = Double(index)
        let seed = sin((i + 1) * 12.9898) * 43758.5453
        let h = seed - floor(seed)
        let env = 0.35 + 0.65 * sin(Double.pi * (i + 0.5) / 32)
        let v = 0.5 + 0.22 * sin(2 * Double.pi * (7 * time / OnAirToggle.loop + h)) +
            0.16 * sin(2 * Double.pi * (11 * time / OnAirToggle.loop + 2.3 * h + 0.21 * i)) +
            0.12 * sin(2 * Double.pi * (17 * time / OnAirToggle.loop + 0.37 * i))
        return clamp01(v) * env
    }

    static func rubberBand(_ raw: Double) -> Double {
        let over = raw > 1 ? raw - 1 : raw < 0 ? raw : 0
        guard over != 0 else { return raw }
        let band = (over < 0 ? -1.0 : 1.0) * abs(over) * 0.35 / (1 + abs(over) * 3)
        return (raw > 1 ? 1 : 0) + band
    }
}

// Only gesture preview smoothing needs accumulated values. These are private,
// non-observable frame data, never @State/Published writes in the render loop.
// Autoplay itself is always the pure loopAt(t) above.
private final class OnAirTogglePlayback {
    private enum OnAirToggleMode { case loop, dragging, settling }
    private var mode = OnAirToggleMode.loop
    private var lastDate: Date?
    private var elapsed = 0.0
    private var origin = 0.0
    private var released = 0.0
    private var target = 0.0
    private var from = OnAirToggleState()
    private var fromCross = 0.0
    private var cross = 0.0
    private var onSince = 0.0
    private var dragTimerRunning = false
    private var timerOffset = 0.0
    private var suspended = false
    private(set) var state = OnAirToggleState()

    func reset(at date: Date) {
        mode = .loop
        lastDate = date
        elapsed = 0
        origin = 0
        released = 0
        target = 0
        from = OnAirToggleState()
        fromCross = 0
        cross = 0
        onSince = 0
        dragTimerRunning = false
        timerOffset = 0
        state = OnAirToggleState()
        suspended = false
    }

    func suspend() { suspended = true; lastDate = nil }
    func resume(at date: Date) { suspended = false; lastDate = date }

    func frame(at date: Date, reduced: Bool, autoPlay: Bool) -> OnAirToggleFrame {
        let gap = suspended ? 0 : max(0, lastDate.map { date.timeIntervalSince($0) } ?? 0)
        let dt = min(0.05, gap)
        let wasAnimating = isAnimating(reduced: reduced, autoPlay: autoPlay)
        if !suspended { lastDate = max(date, lastDate ?? date) }
        // A sleeping off-state has no intermediate drawings to evaluate, but
        // its clock still advances, preserving the reference's meter phase.
        elapsed += wasAnimating ? dt : gap
        // Like the RN callback, the meter reads the origin before a resume.
        let meterTime = elapsed - origin
        if reduced {
            let u = mode == .settling ? OnAirToggleMotion.clamp01((elapsed - released) / 0.3) : 1
            cross = OnAirToggleMotion.mixN(fromCross, target, u)
            state = OnAirToggleState(pos: cross, glow: cross, room: cross, press: 0,
                                     timer: cross > 0.5 ? elapsed - onSince : 0)
        } else if mode == .dragging {
            let k = min(1, dt * 10)
            state.glow = OnAirToggleMotion.mixN(state.glow, OnAirToggleMotion.easeInOutCubic(OnAirToggleMotion.seg(state.pos, 0.5, 0.95)), k)
            state.room = OnAirToggleMotion.mixN(state.room, OnAirToggleMotion.easeInOutCubic(OnAirToggleMotion.seg(state.pos, 0.45, 1)), k)
            state.press = OnAirToggleMotion.mixN(state.press, 0.6, k)
            if state.room > 0.5 && !dragTimerRunning { onSince = elapsed }
            // A zero timer is also the first valid sample. Keep a separate
            // latch so it is not mistaken for an unstarted timer every frame.
            dragTimerRunning = state.room > 0.5
            state.timer = state.room > 0.5 ? elapsed - onSince : 0
        } else if mode == .settling {
            let s = elapsed - released
            state.pos = from.pos + (target - from.pos) * OnAirToggleMotion.springUnit(s)
            state.press *= max(0, 1 - dt * 8)
            if target == 1 {
                state.glow = from.glow + (1 - from.glow) * OnAirToggleMotion.ignite(s - 0.18)
                state.room = from.room + (1 - from.room) * OnAirToggleMotion.easeInOutCubic(OnAirToggleMotion.seg(s, 0.23, 0.93))
            } else {
                state.glow = from.glow * (1 - OnAirToggleMotion.easeInOutCubic(OnAirToggleMotion.seg(s, 0.1, 0.5)))
                state.room = from.room * (1 - OnAirToggleMotion.easeInOutCubic(OnAirToggleMotion.seg(s, 0.15, 0.85)))
            }
            state.timer = state.room > 0.5 ? elapsed - onSince : 0
            if s > OnAirToggleMotion.settle + OnAirToggleMotion.hold && autoPlay {
                let resumeAt = target == 1 ? 1.85 : 4.85
                origin = elapsed - resumeAt
                timerOffset = target == 1 ? state.timer - (resumeAt - 1.3) : 0
                mode = .loop
            }
        } else if autoPlay {
            let t = ((elapsed - origin).truncatingRemainder(dividingBy: OnAirToggle.loop) + OnAirToggle.loop)
                .truncatingRemainder(dividingBy: OnAirToggle.loop)
            state = OnAirToggleMotion.loopAt(t)
            if state.room < 0.5 { timerOffset = 0 }
            if state.timer > 0 { state.timer += timerOffset }
        } else {
            state = OnAirToggleState()
        }
        return OnAirToggleFrame(state: state, cross: cross, meterTime: meterTime)
    }

    func beginDrag() {
        if state.room > 0.5 { onSince = elapsed - state.timer }
        dragTimerRunning = state.room > 0.5
        mode = .dragging
    }

    func drag(to position: Double) { state.pos = position }

    func settle(to destination: Double, reduced: Bool) {
        let wasOn = state.room > 0.5
        from = state
        fromCross = cross
        target = destination
        released = elapsed
        if target == 1 && !wasOn { onSince = elapsed + (reduced ? 0 : 0.2) }
        if target == 1 && wasOn { onSince = elapsed - state.timer }
        mode = .settling
    }

    func isAnimating(reduced: Bool, autoPlay: Bool) -> Bool {
        if reduced { return (mode == .settling && elapsed - released < 0.3) || state.room > 0.5 }
        if mode == .dragging { return true }
        if mode == .settling {
            return autoPlay || target == 1 || elapsed - released < OnAirToggleMotion.settle
        }
        return autoPlay
    }

    func transitionRemaining(reduced: Bool, autoPlay: Bool) -> Double? {
        guard mode == .settling else { return nil }
        let end = reduced ? 0.3 : OnAirToggleMotion.settle + (autoPlay ? OnAirToggleMotion.hold : 0)
        let remaining = end - (elapsed - released)
        return remaining > 0 ? remaining : nil
    }
}

private enum OnAirToggleDrawing {
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        rgba(Double((hex >> 16) & 255), Double((hex >> 8) & 255), Double(hex & 255), alpha)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }

    static func mix(_ a: UInt32, _ b: UInt32, _ u: Double, _ alpha: Double = 1, rounded: Bool = false) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let v = OnAirToggleMotion.mixN(Double((a >> shift) & 255), Double((b >> shift) & 255), u)
            return rounded ? v.rounded() : v
        }
        return rgba(channel(16), channel(8), channel(0), alpha)
    }

    private static func stops(_ light: [UInt32], _ dark: [UInt32], _ room: Double) -> [Color] {
        zip(light, dark).map { mix($0.0, $0.1, room) }
    }

    private static func gradient(_ colors: [Color], _ locations: [Double]? = nil) -> Gradient {
        Gradient(stops: colors.enumerated().map { index, color in
            .init(color: color, location: locations?[index] ?? Double(index) / Double(colors.count - 1))
        })
    }

    private static func vertical(_ y0: Double, _ y1: Double, _ colors: [Color], _ locations: [Double]? = nil) -> GraphicsContext.Shading {
        .linearGradient(gradient(colors, locations), startPoint: CGPoint(x: 0, y: y0), endPoint: CGPoint(x: 0, y: y1))
    }

    private static func horizontal(_ x0: Double, _ x1: Double, _ colors: [Color], _ locations: [Double]? = nil) -> GraphicsContext.Shading {
        .linearGradient(gradient(colors, locations), startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x1, y: 0))
    }

    private static func radial(_ x: Double, _ y: Double, _ radius: Double, _ colors: [Color], _ locations: [Double]? = nil) -> GraphicsContext.Shading {
        .radialGradient(gradient(colors, locations), center: CGPoint(x: x, y: y), startRadius: 0, endRadius: radius)
    }

    private static func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ radius: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: radius)
    }

    private static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }

    private static func line(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x0, y: y0))
        path.addLine(to: CGPoint(x: x1, y: y1))
        return path
    }

    // Skia's Solid mask blur keeps the original interior as well as the blurred
    // exterior. Union the two masks before applying the constant colour once.
    private static func solidBlur(_ context: GraphicsContext, path: Path, radius: Double, color: Color) {
        var layer = context
        layer.clipToLayer { mask in
            mask.opacity = 1
            mask.blendMode = .normal
            mask.drawLayer { blurred in
                blurred.addFilter(.blur(radius: radius))
                blurred.fill(path, with: .color(.white))
            }
            mask.fill(path, with: .color(.white))
        }
        layer.fill(Path(path.boundingRect.insetBy(dx: -4 * radius, dy: -4 * radius)), with: .color(color))
    }

    static func scene(_ c: GraphicsContext, size: CGSize, state s: OnAirToggleState, cross: Double, time: Double, reduced: Bool) {
        let width = size.width, height = size.height
        let cx = width / 2, cy = height * 0.48
        let x0 = cx - 150, y0 = cy - 74
        let room = OnAirToggleMotion.clamp01(s.room)
        let g = OnAirToggleMotion.clamp01(s.glow)
        let kx = cx - 76 + 152 * s.pos
        let screen = Path(CGRect(origin: .zero, size: size))

        c.fill(screen, with: radial(cx, cy - 80, max(width, height) * 0.9,
                                   stops([0xFBFAF8, 0xEEEBE6, 0xDCD7D0], [0x2A1A1C, 0x120C0E, 0x060405], room), [0, 0.55, 1]))
        if g > 0.001 {
            c.fill(screen, with: radial(kx, cy + 30, 300,
                                       [rgba(214, 28, 36, 0.3 * g * room), rgba(214, 28, 36, 0.1 * g * room), rgba(214, 28, 36, 0)], [0, 0.45, 1]))
        }
        for o in [56.0, 128.0, 214.0] {
            let contour = room < 0.5 ? rgba(40, 32, 30, 0.2 * (1 - room * 2)) : rgba(255, 235, 230, 0.15 * (room * 2 - 1))
            c.stroke(rounded(x0 - o, y0 - o * 0.82, 300 + 2 * o, 166 + 2 * o * 0.82, 74 + o), with: .color(contour), lineWidth: 1)
        }

        solidBlur(c, path: rounded(x0 + 8, y0 + 22, 284, 166, 74), radius: 22, color: rgba(40, 28, 26, 0.22 + 0.25 * room))
        solidBlur(c, path: rounded(x0 + 2, y0 + 3, 296, 166, 74), radius: 3, color: rgba(20, 14, 12, 0.35))
        let body = rounded(x0, y0, 300, 166, 74)
        c.fill(body, with: horizontal(x0, x0 + 300,
                                     stops([0x8D8F92, 0xC4C6C8, 0xE1E2E3, 0xB9BBBE, 0x808285],
                                           [0x121112, 0x2A2627, 0x3A3436, 0x2A2627, 0x121112], room), [0, 0.18, 0.5, 0.82, 1]))
        c.fill(body, with: vertical(y0 + 74, y0 + 166, [rgba(0, 0, 0, 0), rgba(0, 0, 0, 0.3)]))
        let top = rounded(x0, y0, 300, 148, 74)
        let channel = rounded(cx - 134, cy - 58, 268, 116, 58)
        c.fill(top, with: vertical(y0, y0 + 148,
                                  stops([0xFDFDFC, 0xE3E4E5, 0xC3C5C8, 0xEDEEEF, 0xA6A8AB],
                                        [0x4A4446, 0x2B2728, 0x1A1718, 0x2E2829, 0x141213], room), [0, 0.3, 0.55, 0.8, 1]))
        var sheen = c
        sheen.clip(to: channel, options: .inverse)
        sheen.opacity = 1 - 0.5 * room
        sheen.fill(top, with: .conicGradient(gradient([
            rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0), rgba(0, 0, 0, 0.12),
            rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.3), rgba(255, 255, 255, 0), rgba(0, 0, 0, 0.12), rgba(255, 255, 255, 0)
        ]), center: CGPoint(x: cx, y: cy), angle: .zero))
        for inset in [5.0, 10.0] {
            c.stroke(rounded(x0 + inset, y0 + inset, 300 - 2 * inset, 148 - 2 * inset, 74 - inset),
                     with: .color(rgba(0, 0, 0, 0.16 + 0.2 * room)), lineWidth: 1)
            c.stroke(rounded(x0 + inset, y0 + inset + 0.8, 300 - 2 * inset, 148 - 2 * inset, 74 - inset),
                     with: .color(rgba(255, 255, 255, 0.55 - 0.43 * room)), lineWidth: 1)
        }
        c.stroke(rounded(x0 + 0.75, y0 + 0.75, 298.5, 146.5, 73.25),
                 with: vertical(y0, y0 + 74, [rgba(255, 255, 255, 0.95 - 0.25 * room), rgba(255, 255, 255, 0)]), lineWidth: 1.5)
        if g > 0.001 {
            var rim = c
            rim.blendMode = .plusLighter
            let light = radial(kx, cy, 210, [rgba(255, 70, 50, 0.85 * g * (0.4 + 0.6 * room)), rgba(255, 70, 50, 0)])
            rim.stroke(rounded(x0 + 1, y0 + 1, 298, 146, 73), with: light, lineWidth: 2)
            rim.clip(to: Path(CGRect(x: x0 - 4, y: y0 + 148, width: 308, height: 22)))
            rim.stroke(rounded(x0 + 1, y0 + 1, 298, 164, 73), with: light, lineWidth: 2)
        }

        c.fill(channel, with: vertical(cy - 58, cy + 58,
                                      stops([0x2E2F32, 0x3A3B3F, 0x5A5C60], [0x070607, 0x0D0B0C, 0x1F1A1B], room), [0, 0.5, 1]))
        var inside = c
        inside.clip(to: channel)
        inside.stroke(line(cx - 76, cy + 2, cx + 76, cy + 2), with: .color(rgba(0, 0, 0, 0.35)),
                      style: StrokeStyle(lineWidth: 4, lineCap: .round))
        inside.stroke(line(cx - 76, cy + 4.5, cx + 76, cy + 4.5), with: .color(rgba(255, 255, 255, 0.08)), lineWidth: 1)
        if g > 0.001 {
            var spill = inside
            spill.blendMode = .plusLighter
            spill.fill(channel, with: radial(kx, cy, 120, [rgba(255, 50, 40, 0.38 * g), rgba(255, 50, 40, 0)]))
        }
        if reduced {
            if cross < 0.999 { knob(inside, x: cx - 76, cy: cy, glow: 0, room: room, press: 0, alpha: 1 - cross) }
            if cross > 0.001 { knob(inside, x: cx + 76, cy: cy, glow: 1, room: room, press: 0, alpha: cross) }
        } else {
            knob(inside, x: kx, cy: cy, glow: s.glow, room: room, press: s.press, alpha: 1)
        }
        let inner = rounded(cx - 141, cy - 60, 282, 130, 65)
        solidBlur(inside, path: inner.strokedPath(StrokeStyle(lineWidth: 14)), radius: 6, color: rgba(0, 0, 0, 0.6))
        c.stroke(rounded(cx - 133.4, cy - 57.4, 266.8, 114.8, 57.4),
                 with: vertical(cy - 58, cy + 58, [rgba(255, 255, 255, 0), rgba(255, 255, 255, 0), rgba(255, 255, 255, 0.5 - 0.3 * room)], [0, 0.55, 1]), lineWidth: 1.2)

        if g > 0.001 {
            let lx = reduced ? cx - 76 + 152 * cross : kx
            let ly = cy - 3
            var bloom = c
            bloom.blendMode = .plusLighter
            bloom.opacity = OnAirToggleMotion.clamp01(s.glow * (0.35 + 0.65 * room))
            bloom.fill(circle(lx, ly, 130), with: radial(lx, ly, 130,
                       [rgba(255, 64, 48, 0.5), rgba(255, 40, 40, 0.2), rgba(255, 30, 30, 0.05), rgba(255, 30, 30, 0)], [0, 0.35, 0.7, 1]))
            var halo = c
            halo.blendMode = .plusLighter
            halo.fill(circle(lx, ly, 50), with: radial(lx, ly, 50,
                      [rgba(255, 140, 100, 0.55 * g), rgba(255, 120, 90, 0.35 * g), rgba(255, 100, 80, 0)], [0, 0.66, 1]))
        }

        let ink: UInt32 = room < 0.5 ? 0x1D1A1A : 0xF3EEEA
        let inkAlpha = room < 0.5 ? 1 - room * 2 : room * 2 - 1
        let timerColor = mix(ink, 0xFF4B3A, room * g, (0.35 + 0.65 * room) * (0.4 + 0.6 * inkAlpha))
        tracked(c, font: timerFont, text: timerText(s.timer), x: cx, baseline: cy + 196, tracking: 1, color: timerColor)
        let mx = cx - 110
        for i in 0..<32 {
            let level = OnAirToggleMotion.barLevel(i, reduced ? 0 : time)
            let h = 3 + 27 * level * room
            let alpha = OnAirToggleMotion.mixN(0.2, 0.55 + 0.45 * level, room) * (0.5 + 0.5 * inkAlpha)
            c.fill(rounded(mx + Double(i) * 7, cy + 248 - h / 2, 3, h, 1.5), with: .color(mix(ink, 0xFF4B3A, room, alpha)))
        }
    }

    private static func knob(_ context: GraphicsContext, x: Double, cy: Double, glow: Double, room: Double, press: Double, alpha: Double) {
        // Apply alpha after composing the entire knob, as Skia saveLayer does.
        if alpha < 1 {
            var group = context
            group.opacity = alpha
            group.drawLayer { layer in
                knobContents(layer, x: x, cy: cy, glow: glow, room: room, press: press)
            }
        } else {
            knobContents(context, x: x, cy: cy, glow: glow, room: room, press: press)
        }
    }

    private static func knobContents(_ c: GraphicsContext, x: Double, cy: Double, glow: Double, room: Double, press: Double) {
        let g = OnAirToggleMotion.clamp01(glow)
        let side = 7 * (1 - 0.55 * press)
        let ky = cy - 3 + 3 * press
        solidBlur(c, path: Path(ellipseIn: CGRect(x: x - 46, y: ky - 38 - 4 * press, width: 104, height: 96)),
                  radius: 9 - 3 * press, color: rgba(0, 0, 0, 0.55 - 0.15 * press))
        c.fill(circle(x, ky + side, 52), with: horizontal(x - 52, x + 52,
               stops([0x7C7E81, 0xC9CBCD, 0x8A8C8F], [0x141213, 0x3A3335, 0x161415], room), [0, 0.45, 1]))
        c.fill(circle(x, ky, 52), with: .conicGradient(gradient(stops(
            [0xF4F4F2, 0xA3A5A8, 0xEDEEEE, 0x8D8F92, 0xF4F4F2, 0xA9ABAE, 0xE3E4E5, 0x909295, 0xF4F4F2],
            [0x5A5254, 0x262223, 0x4B4446, 0x1E1B1C, 0x5A5254, 0x2A2526, 0x463F41, 0x1F1C1D, 0x5A5254], room)),
            center: CGPoint(x: x, y: ky), angle: .zero))
        c.fill(circle(x, ky, 52), with: vertical(ky - 52, ky + 52,
               [rgba(255, 255, 255, 0.35), rgba(255, 255, 255, 0), rgba(0, 0, 0, 0.18)], [0, 0.5, 1]))
        c.stroke(circle(x, ky, 51.25), with: vertical(ky - 52, ky,
                 [rgba(255, 255, 255, 0.9 - 0.2 * room), rgba(255, 255, 255, 0)]), lineWidth: 1.5)
        if g > 0.001 {
            var tint = c
            tint.blendMode = .plusLighter
            tint.fill(circle(x, ky, 52), with: radial(x, ky, 52,
                      [rgba(255, 60, 40, 0), rgba(255, 60, 40, 0.55 * g), rgba(255, 60, 40, 0.08 * g)], [0.62, 0.7, 1]))
        }
        c.stroke(circle(x, ky - 0.5, 39), with: .color(rgba(0, 0, 0, 0.28)), lineWidth: 2)
        c.stroke(circle(x, ky + 1, 39.5), with: .color(rgba(255, 255, 255, 0.55 - 0.35 * room)), lineWidth: 1.2)
        c.fill(circle(x, ky, 36), with: .color(rgba(18, 7, 8)))
        c.fill(circle(x, ky, 34), with: radial(x - 6, ky - 8, 42,
               [mix(0x7A2224, 0x6A1A1C, room), mix(0x4E1013, 0x3E0B0E, room), mix(0x250709, 0x1A0405, room)], [0, 0.5, 1]))
        if g > 0.001 {
            var lit = c
            lit.opacity = g
            lit.fill(circle(x, ky, 34), with: radial(x, ky - 2, 36,
                     [rgba(255, 138, 92), rgba(255, 59, 43), rgba(211, 15, 28), rgba(126, 7, 16)], [0, 0.42, 0.78, 1]))
        }
        for i in 0..<4 {
            let r = 9.0 + 7 * Double(i)
            c.stroke(circle(x, ky, r), with: .color(rgba(255, 200, 190, 0.1 + 0.16 * g)), lineWidth: 1)
            c.stroke(circle(x, ky, r + 1), with: .color(rgba(40, 0, 4, 0.18)), lineWidth: 1)
        }
        if g > 0.01 {
            tracked(c, font: lensFont, text: "ON AIR", x: x, baseline: ky + 3.6, tracking: 1.4,
                    color: rgba(255, 120, 90, 0.9 * g), blur: 3)
        }
        tracked(c, font: lensFont, text: "ON AIR", x: x, baseline: ky + 3.6, tracking: 1.4,
                color: rgba(255, OnAirToggleMotion.mixN(170, 246, g), OnAirToggleMotion.mixN(160, 232, g), OnAirToggleMotion.mixN(0.22, 0.96, g)))
        var caustic = c
        caustic.blendMode = .plusLighter
        caustic.fill(circle(x, ky, 34), with: radial(x + 8, ky + 16, 18,
                     [rgba(255, 120, 100, 0.28 + 0.3 * g), rgba(255, 120, 100, 0)]))
        var dome = c
        dome.translateBy(x: x - 9, y: ky - 15)
        dome.rotate(by: .degrees(-25))
        dome.fill(Path(ellipseIn: CGRect(x: -17, y: -8, width: 34, height: 16)),
                  with: vertical(-8, 8, [rgba(255, 255, 255, 0.5 - 0.15 * g), rgba(255, 255, 255, 0)]))
        c.fill(circle(x - 15, ky - 15, 2.5), with: .color(rgba(255, 255, 255, 0.85)))
    }

    private static let lensFont = OnAirToggleFont(font: UIFont(name: "Helvetica-Bold", size: 10) ?? .systemFont(ofSize: 10, weight: .bold), characters: "ON AIR")
    private static let timerFont = OnAirToggleFont(font: UIFont(name: "Menlo-Regular", size: 30) ?? .monospacedSystemFont(ofSize: 30, weight: .regular), characters: "0123456789:.")

    private static func timerText(_ seconds: Double) -> String {
        let cs = Int(floor(max(0, seconds) * 100))
        return String(format: "%02d:%02d.%02d", cs / 6000, cs / 100 % 60, cs % 100)
    }

    private static func tracked(_ context: GraphicsContext, font: OnAirToggleFont, text: String, x: Double,
                                baseline: Double, tracking: Double, color: Color, blur: Double = 0) {
        let width = text.reduce(0.0) { $0 + (font.widths[$1] ?? 0) } + tracking * Double(text.count - 1)
        func draw(_ c: GraphicsContext, color: Color) {
            var cursor = x - width / 2
            for character in text {
                let resolved = c.resolve(Text(String(character)).font(font.swiftUI).foregroundStyle(color))
                let bounds = resolved.measure(in: CGSize(width: 100, height: 100))
                c.draw(resolved, at: CGPoint(x: cursor, y: baseline - resolved.firstBaseline(in: bounds)), anchor: .topLeading)
                cursor += (font.widths[character] ?? 0) + tracking
            }
        }
        if blur > 0 {
            var masked = context
            masked.clipToLayer { mask in
                mask.opacity = 1
                mask.blendMode = .normal
                mask.drawLayer { layer in
                    layer.addFilter(.blur(radius: blur))
                    draw(layer, color: .white)
                }
                draw(mask, color: .white)
            }
            masked.fill(Path(CGRect(x: x - width / 2 - blur * 4, y: baseline - 50, width: width + blur * 8, height: 100)), with: .color(color))
        } else {
            draw(context, color: color)
        }
    }
}

private struct OnAirToggleFont {
    let swiftUI: Font
    let widths: [Character: Double]

    init(font: UIFont, characters: String) {
        swiftUI = .custom(font.fontName, fixedSize: font.pointSize)
        widths = Dictionary(uniqueKeysWithValues: Set(characters).map { character in
            (character, Double((String(character) as NSString).size(withAttributes: [.font: font]).width))
        })
    }
}
