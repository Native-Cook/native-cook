import SwiftUI
import UIKit

/*
 Paint Mixer Picker — "Hue Room": pick a wall colour by mixing paint. A glazed bowl seen from
 above, five tins around it; drops swirl into the paint and stirring blends them; the colour's
 name and code update and the room card behind takes the colour.

 Touch: drag from a tin to lift a drop (it hangs above the fingertip) and let go over the paint to
 drop it in (light haptic on landing); let go elsewhere and it flies back. Drag inside the bowl to
 stir: the swirl follows the finger's angular speed and mixing follows how far it has turned.
 2 s after the last touch the bowl washes back to white (0.85 s) and the loop restarts. Reduced
 motion: no loop, no swirl; drops land at once and the colour fades in evenly.

 The bowl is one Metal shader over its square; the table is a Canvas drawn once; the room is an
 image rendered once; the tins, drops, stick and label are one Canvas per frame.
 */
struct PaintMixerPicker: View {
    /// Drops yellow and teal in, stirs them to "Morning Fern" and washes back, on a 9 s loop.
    var autoPlay = false

    static let loop = PaintMixerPickerMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = PaintMixerPickerEngine()
    /// Keeps the timeline running outside autoPlay while something moves.
    @State private var awake = false
    @State private var touching = false

    private typealias Art = PaintMixerPickerArt

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, top: insets.top, bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, top: Double, bottom: Double) -> some View {
        let g = PaintMixerPickerGeo(w: size.width, h: size.height, top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let running = auto || awake
        let room = Art.room(g, scale: min(3, max(1, displayScale)))
        // The bowl's shader covers a square around the rim, with room for the shadow.
        let ox = g.cx - g.rim - 40
        let oy = g.cy - g.rim - 30
        let side = 2 * g.rim + 80
        return ZStack(alignment: .topLeading) {
            Canvas { c, _ in
                Art.drawTable(c, g)
            }
            .frame(width: size.width, height: size.height)

            TimelineView(.animation(paused: !running)) { timeline in
                let n = timeline.date.timeIntervalSinceReferenceDate
                let frame = engine.step(n, g: g, auto: auto, reduced: reduced)
                let m = frame.mix
                let d = Self.dropArgs(m, ox: ox, oy: oy)
                let rp = Self.rippleArgs(m, ox: ox, oy: oy)
                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in
                        Art.drawCard(c, g, shown: frame.shown, room: room)
                    }
                    .frame(width: size.width, height: size.height)

                    Rectangle()
                        .colorEffect(ShaderLibrary.paintMixerPicker(
                            .float2(g.cx - ox, g.cy - oy),
                            .float(g.rim),
                            .float(g.paint),
                            .float(g.u),
                            .float4(m.baseA.0, m.baseA.1, m.baseA.2, m.baseW),
                            d.a[0], d.a[1], d.a[2], d.a[3],
                            d.p[0], d.p[1], d.p[2], d.p[3],
                            rp[0], rp[1], rp[2],
                            .float2(m.wash.0, m.wash.1)
                        ))
                        .frame(width: max(1, side), height: max(1, side))
                        .padding(.leading, ox)
                        .padding(.top, oy)

                    Canvas { c, _ in
                        Art.drawDynamic(c, g, m, shown: frame.shown, label: frame.label)
                    }
                    .frame(width: size.width, height: size.height)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .onChange(of: frame.idle, initial: true) { _, idle in
                    if idle && !auto { awake = false }
                }
            }
            .allowsHitTesting(false)

            // Drag a tin's drop into the bowl, or drag in the bowl to stir.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            if !touching {
                                touching = true
                                engine.down(v.startLocation, g: g)
                            }
                            engine.move(v.location, velocity: v.velocity, g: g)
                        }
                        .onEnded { _ in
                            touching = false
                            engine.up(g: g, auto: auto)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Paint mixing bowl. Drag a tin's drop into the bowl, drag in the bowl to stir.")

            VStack(alignment: .leading, spacing: 4) {
                Text("HUE ROOM")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2.4)
                    .foregroundStyle(Art.rgb(0x8A7F72))
                Text("Mix your wall colour")
                    .font(.system(size: 28, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(Art.ink)
                Text("Drop paint into the bowl, then stir.")
                    .font(.system(size: 15, weight: .regular))
                    .foregroundStyle(Art.inkSoft)
            }
            .frame(width: max(0, size.width - 48), alignment: .leading)
            .padding(.leading, 24)
            .padding(.top, top + 8)
            .allowsHitTesting(false)

            Button {
                UINotificationFeedbackGenerator().notificationOccurred(.success)
            } label: {
                Text("Apply to wall")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Art.rgb(0xF5F0E8))
                    .frame(width: max(0, size.width - 48), height: 52)
            }
            .buttonStyle(PaintMixerPickerButtonStyle())
            .padding(.leading, 24)
            .padding(.top, g.buttonY)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

extension PaintMixerPicker {
    /// The shader's four drop slots: absorbance and weight, then position (in the shader's square),
    /// mixed share and winding; empty slots are zero.
    fileprivate static func dropArgs(_ m: PaintMixerPickerMix, ox: Double, oy: Double) -> (a: [Shader.Argument], p: [Shader.Argument]) {
        var a: [Shader.Argument] = []
        var p: [Shader.Argument] = []
        for i in 0..<4 {
            if i < m.drops.count {
                let d = m.drops[i]
                a.append(.float4(d.a.0, d.a.1, d.a.2, PaintMixerPickerMotion.k * d.amt * m.fade))
                p.append(.float4(d.x - ox, d.y - oy, d.m, d.tw))
            } else {
                a.append(.float4(0, 0, 0, 0))
                p.append(.float4(0, 0, 0, 0))
            }
        }
        return (a, p)
    }

    /// The shader's three ripple slots: centre (in the shader's square), age and strength.
    fileprivate static func rippleArgs(_ m: PaintMixerPickerMix, ox: Double, oy: Double) -> [Shader.Argument] {
        (0..<3).map { i in
            guard i < m.ripples.count else { return .float4(0, 0, 0, 0) }
            let r = m.ripples[i]
            return .float4(r.x - ox, r.y - oy, r.age, r.str)
        }
    }
}

/// The dark pill: two shadows, a faint top inner highlight, a 0.97 press.
private struct PaintMixerPickerButtonStyle: ButtonStyle {
    private typealias Art = PaintMixerPickerArt

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        configuration.label
            .background {
                shape
                    .fill(Art.ink)
                    .overlay {
                        // inset 0 1px 0 rgba(255,255,255,0.12)
                        shape
                            .inset(by: 0.5)
                            .stroke(
                                LinearGradient(
                                    stops: [
                                        .init(color: Color.white.opacity(0.12), location: 0),
                                        .init(color: Color.white.opacity(0), location: 0.06),
                                    ],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ),
                                lineWidth: 1
                            )
                    }
                    .shadow(color: Art.rgb(0x2B2620, 0.18), radius: 12, x: 0, y: 10)
                    .shadow(color: Art.rgb(0x2B2620, 0.3), radius: 1, x: 0, y: 1)
            }
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(
                configuration.isPressed ? .easeInOut(duration: 0.11) : .spring(duration: 0.35, bounce: 0.3),
                value: configuration.isPressed
            )
    }
}

/// What one frame shows.
struct PaintMixerPickerFrame {
    var mix: PaintMixerPickerMix
    var shown: PaintMixerPickerRGB
    var label: PaintMixerPickerArt.Label
    var idle: Bool
}

/// The clock, the script and the hand-driven sim, stepped once a frame like the RN frame callback.
@MainActor
final class PaintMixerPickerEngine {
    private typealias M = PaintMixerPickerMotion
    private static let washHold = 0.9
    /// The name/code roll: 260 ms per line, the new one 60 ms behind.
    private static let labelRoll = 0.32

    private var prev: Double?
    private var now = 0.0
    private var start: Double?
    private var resumeAt: Double?
    private var sim: PaintMixerPickerSim?
    private var mix: PaintMixerPickerMix?
    private var shown: PaintMixerPickerRGB = M.baseRGB
    private var label = PaintMixerPickerArt.Label(cur: 0, prev: 0, since: 1, prevRGB: M.baseRGB)
    private var labelAt = -10.0

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    /// The script's label at t: the current name and, within the roll after a change, the one before.
    private func autoLabel(_ g: PaintMixerPickerGeo, _ t: Double) -> PaintMixerPickerArt.Label {
        let cur = M.nearestName(M.shownColor(g, t))
        for k in 1...7 {
            var back = (t - Double(k) * 0.05).truncatingRemainder(dividingBy: M.loop)
            if back < 0 { back += M.loop }
            let c = M.shownColor(g, back)
            let idx = M.nearestName(c)
            if idx != cur { return .init(cur: cur, prev: idx, since: (Double(k) - 0.5) * 0.05, prevRGB: c) }
        }
        return .init(cur: cur, prev: cur, since: 1, prevRGB: M.baseRGB)
    }

    func step(_ n: Double, g: PaintMixerPickerGeo, auto: Bool, reduced: Bool) -> PaintMixerPickerFrame {
        let first = prev == nil
        if first && auto { start = n }
        let dt = first ? 0 : min(max(0, n - (prev ?? n)), 1.0 / 30)
        prev = n
        now = n
        let s = sim ?? PaintMixerPickerSim(reduced: reduced)
        sim = s
        if let st = start, n >= st {
            let t = (n - st).truncatingRemainder(dividingBy: M.loop)
            let m = M.autoMix(g, t)
            mix = m
            shown = M.shownColor(g, t)
            label = autoLabel(g, t)
            return PaintMixerPickerFrame(mix: m, shown: shown, label: label, idle: false)
        }
        s.step(now: n, dt: dt)
        if s.events != 0 {
            if s.events & PaintMixerPickerSim.eventLand != 0 {
                light.impactOccurred()
            } else if s.events & PaintMixerPickerSim.eventGrab != 0 {
                tick.selectionChanged()
            }
            s.events = 0
        }
        let labelMoving = n - labelAt < Self.labelRoll + 0.05
        let current = s.mix(g, now: n)
        let target = M.mixColor(current)
        let cur = shown
        let settling = abs(target.0 - cur.0) + abs(target.1 - cur.1) + abs(target.2 - cur.2) > 0.002
        let busy = s.busy
        if busy || settling || labelMoving || mix == nil {
            mix = current
            let k = 1 - exp(-dt / 0.18)
            let next = (cur.0 + (target.0 - cur.0) * k, cur.1 + (target.1 - cur.1) * k, cur.2 + (target.2 - cur.2) * k)
            shown = next
            let idx = M.nearestName(next)
            if idx != label.cur {
                labelAt = n
                label = .init(cur: idx, prev: label.cur, since: 0, prevRGB: cur)
            } else if label.since < Self.labelRoll {
                label.since = n - labelAt
            }
        }
        if let r = resumeAt, n >= r, s.held == nil, s.flights.isEmpty {
            resumeAt = nil
            s.startWash(now: n)
            start = n + Self.washHold
        }
        let idle = !busy && !settling && !labelMoving && resumeAt == nil && start == nil
        return PaintMixerPickerFrame(mix: mix ?? current, shown: shown, label: label, idle: idle)
    }

    private func takeOver(_ g: PaintMixerPickerGeo) {
        guard let s = sim else { return }
        let n = clock
        if let st = start, n >= st {
            s.fromAuto(g, t: (n - st).truncatingRemainder(dividingBy: M.loop), now: n)
        }
        start = nil
        resumeAt = nil
    }

    func down(_ p: CGPoint, g: PaintMixerPickerGeo) {
        takeOver(g)
        tick.prepare()
        light.prepare()
        sim?.down(g, p.x, p.y)
    }

    func move(_ p: CGPoint, velocity: CGSize, g: PaintMixerPickerGeo) {
        sim?.move(g, p.x, p.y, velocity.width, velocity.height)
    }

    func up(g: PaintMixerPickerGeo, auto: Bool) {
        let n = clock
        sim?.up(g, now: n)
        if auto { resumeAt = n + 2 }
    }
}
