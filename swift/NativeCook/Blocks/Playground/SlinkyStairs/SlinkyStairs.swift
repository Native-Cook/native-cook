import SwiftUI
import UIKit

/*
 Slinky Stairs — "Desk Toys", a toy-physics playground: a steel slinky on a three-step maple
 staircase on a walnut desk, under a warm lamp.

 It walks down by itself: the top end tips over and lands on the next step, the coils pour through
 the arch, the last end flips over onto the new stack (SlinkyStairsMotion.swift has the timeline).
 Grab the top coil and pull: the slinky stretches from its stack after the finger, rubber-banding
 past 300 pt; let go and it collapses back on an underdamped spring (16 rad/s, damping 0.32),
 squashing below its height and ringing. A touch while it walks lets the current step finish at 4x
 first. 2 s after the last touch it is lifted back to the top step (0.9 s) and the loop starts again.
 Reduced motion: no loop; the slinky rests on the top step, and a drag still stretches it.
 */
struct SlinkyStairs: View {
    /// Plays the 9 s walk-down-and-lift loop until touched.
    var autoPlay = false

    static let loop = SlinkyStairsMotion.loop

    private typealias M = SlinkyStairsMotion
    private typealias Art = SlinkyStairsArt

    static let labels = ["Ready", "Step 1 of 3", "Step 2 of 3", "Step 3 of 3", "Landed", "Ready"]
    static let ink = Art.col("#F1E9DE")
    static let mute = Art.col("rgba(241,233,222,0.62)")
    static let faint = Art.col("rgba(241,233,222,0.42)")
    static let amber = Art.col("#F0BE7E")

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = SlinkyStairsEngine()
    @State private var busy = false
    @State private var down = false

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let g = Art.layout(w: proxy.size.width + insets.leading + insets.trailing,
                               h: proxy.size.height + insets.top + insets.bottom,
                               top: insets.top, bottom: insets.bottom)
            stage(g, top: insets.top)
                .frame(width: g.w, height: g.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Art.col("#212B2A").ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(_ g: M.Geo, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            SlinkyStairsScene(g: g, top: top).equatable()

            TimelineView(.animation(paused: !(auto || busy))) { timeline in
                let f = engine.step(timeline.date, g: g, auto: auto)
                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in Art.frame(c, g, f.pose, touch: f.touch) }
                        .frame(width: g.w, height: g.h)
                    panelLabel(f.pose.label, reduced: reduced)
                        .offset(x: 36, y: g.panelY + 16)
                }
                .frame(width: g.w, height: g.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            texts(g, top: top)
                .allowsHitTesting(false)

            Color.clear
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(drag(g, top: top))
                .accessibilityElement()
                .accessibilityLabel("A steel slinky walking down three wooden steps. Drag its top coil to stretch it.")
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    /// The step label, rolling from one line to the next (width: the panel row minus the chip).
    private func panelLabel(_ label: Double, reduced: Bool) -> some View {
        let roll = reduced ? 0.0 : 26.0
        return ZStack(alignment: .topLeading) {
            ForEach(0..<Self.labels.count, id: \.self) { i in
                let d = Double(i) - label
                Text(Self.labels[i])
                    .font(.system(size: 22, weight: .bold)).monospacedDigit()
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                    .padding(.top, 1)
                    .opacity(max(0, 1 - abs(d)))
                    .offset(y: d * roll)
            }
        }
        .frame(width: 180, height: 30, alignment: .topLeading)
        .clipped()
    }

    private func texts(_ g: M.Geo, top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                Text("DESK TOYS")
                    .font(.system(size: 12, weight: .bold)).kerning(3).foregroundStyle(Self.amber)
                Text("Slinky")
                    .font(.system(size: 34, weight: .bold)).kerning(-0.6).foregroundStyle(Self.ink)
                Text("A walk down three maple steps")
                    .font(.system(size: 15, weight: .medium)).foregroundStyle(Self.mute)
            }
            .frame(width: g.w - 48, alignment: .leading)
            .offset(x: 24, y: top + 14)

            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Spacer(minLength: 0)
                    Text("Spring steel")
                        .font(.system(size: 12, weight: .semibold)).foregroundStyle(Self.amber)
                        .padding(.horizontal, 12)
                        .frame(height: 26)
                        .background(Capsule().fill(Art.col("rgba(240,190,126,0.14)")))
                }
                .frame(height: 30)
                Text("Drag the top coil to stretch it, then let go")
                    .font(.system(size: 14, weight: .medium)).foregroundStyle(Self.mute)
                    .lineLimit(1).minimumScaleFactor(0.8)
                HStack(spacing: 18) {
                    ForEach([("40", "coils"), ("1.7 s", "per step"), ("92 g", "weight")], id: \.1) { item in
                        (Text(item.0).foregroundStyle(Self.ink).fontWeight(.semibold).monospacedDigit()
                            + Text(" " + item.1).foregroundStyle(Self.faint))
                            .font(.system(size: 13, weight: .medium))
                    }
                }
                .padding(.top, 8)
            }
            .padding(.horizontal, 20)
            .padding(.top, 16)
            .frame(width: g.w - 32, height: g.panelH, alignment: .topLeading)
            .offset(x: 16, y: g.panelY)
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
    }

    private func drag(_ g: M.Geo, top: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !down {
                    down = true
                    engine.onIdle = { busy = false }
                    if engine.begin(at: v.startLocation, g: g, top: top, auto: autoPlay && !reduceMotion) { busy = true }
                }
                engine.move(to: v.location, velocity: v.velocity, g: g)
            }
            .onEnded { _ in
                down = false
                engine.end()
            }
    }
}

/// The loop clock and the finger's spring, stepped once a frame like the RN frame callback.
@MainActor
final class SlinkyStairsEngine {
    typealias M = SlinkyStairsMotion
    enum Mode { case auto, touch, back }

    private static let backFor = 0.9
    private static let maxPull = 300.0

    private struct Sim {
        var k = 0
        var px = 0.0, py = 0.0, vx = 0.0, vy = 0.0
        var grab = false
        var offX = 0.0, offY = 0.0
        var fingerX = 0.0, fingerY = 0.0
        var lastTouch = 0.0
        var ff = -1.0
        var ffTo = 0.0
        var backAt = -1.0
        var snapped = true
        var tick = 0
    }

    struct Frame {
        var pose: M.Pose
        var touch: (Double, Double, Double)
    }

    private var last: Date?
    private var now = 0.0
    private var loopStart = 0.0
    private var mode = Mode.auto
    private var s = Sim()
    private var ignoring = false
    var onIdle: (() -> Void)?

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    /// Where the top coil rests on step k.
    private func restTop(_ g: M.Geo, _ k: Int) -> (Double, Double) {
        (g.baseX[k], g.baseY[k] - Double(M.n - 1) * g.pitch)
    }

    private func backToLoop(_ at: Double, auto: Bool) {
        s = Sim()
        mode = .auto
        loopStart = at
        if !auto {
            let idle = onIdle
            DispatchQueue.main.async { idle?() }
        }
    }

    func step(_ date: Date, g: M.Geo, auto: Bool) -> Frame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = min(max(raw, 0), 1.0 / 20)
        now += dt
        let at = now
        if mode != .auto {
            let rest = restTop(g, s.k)
            if s.ff >= 0 {
                // The current step finishes at 4x before the finger takes the top coil.
                s.ff += dt * 4
                if s.ff >= s.ffTo {
                    s.ff = -1
                    s.px = rest.0
                    s.py = rest.1
                    s.vx = 0
                    s.vy = 0
                    s.offX = rest.0 - s.fingerX
                    s.offY = rest.1 - s.fingerY
                }
            } else if !s.grab {
                // Underdamped spring back onto the stack, in small steps.
                let w = 16.0
                let z = 0.32
                let n = 4
                let h = dt / Double(n)
                let before = s.py - rest.1
                for _ in 0..<n {
                    s.vx += (-w * w * (s.px - rest.0) - 2 * z * w * s.vx) * h
                    s.vy += (-w * w * (s.py - rest.1) - 2 * z * w * s.vy) * h
                    s.px += s.vx * h
                    s.py += s.vy * h
                }
                if !s.snapped && before < 0 && s.py - rest.1 >= 0 {
                    s.snapped = true
                    light.impactOccurred()
                }
            }
            if mode == .touch && !s.grab && s.ff < 0 && at - s.lastTouch > 2 {
                if s.k == 0 {
                    backToLoop(at, auto: auto)
                } else {
                    s.backAt = at
                    mode = .back
                }
            } else if mode == .back && at - s.backAt >= Self.backFor {
                backToLoop(at, auto: auto)
            }
        }
        let touch = (s.fingerX, s.fingerY, mode == .touch && s.grab ? 1.0 : 0)
        if mode == .auto {
            let t = auto ? (at - loopStart).truncatingRemainder(dividingBy: M.loop) : 0
            return Frame(pose: M.loopPose(max(0, t), g), touch: touch)
        }
        if s.ff >= 0 { return Frame(pose: M.loopPose(s.ff, g), touch: touch) }
        var p = M.Pose()
        if mode == .back {
            let v = min(1, (at - s.backAt) / Self.backFor)
            M.liftRings(g, s.k, v, &p.rings)
            let e = M.easeInOutCubic(v)
            p.shadows = [M.Shadow(x: g.baseX[s.k], y: g.baseY[s.k], r: g.R, alpha: 1 - min(1, v * 4)),
                         M.Shadow(x: g.baseX[0], y: g.baseY[0], r: g.R, alpha: max(0, (v - 0.7) / 0.3))]
            p.label = 4 + e
            return Frame(pose: p, touch: touch)
        }
        M.stretchRings(g, s.k, s.px, s.py, &p.rings)
        p.shadows = [M.Shadow(x: g.baseX[s.k], y: g.baseY[s.k], r: g.R, alpha: 1)]
        p.label = s.k == 3 ? 4 : Double(s.k)
        return Frame(pose: p, touch: touch)
    }

    /// Freezes the loop where it is (finishing a step at 4x if one is under way).
    private func takeOver(_ at: Double, g: M.Geo, auto: Bool) {
        s = Sim()
        s.lastTouch = at
        let t = auto ? (at - loopStart).truncatingRemainder(dividingBy: M.loop) : 0
        if t >= M.walkFrom && t < 6.4 {
            let step = min(2, Int(floor((t - M.walkFrom) / M.stepFor)))
            s.ff = t
            s.ffTo = M.walkFrom + Double(step + 1) * M.stepFor
            s.k = step + 1
        } else if t >= M.liftFrom && t < M.liftFrom + M.liftFor {
            s.ff = t
            s.ffTo = M.liftFrom + M.liftFor
            s.k = 0
        } else {
            s.k = t >= 6.4 && t < M.liftFrom ? 3 : 0
        }
        let rest = restTop(g, s.k)
        s.px = rest.0
        s.py = rest.1
        mode = .touch
    }

    /// A finger lands; false when it is ignored (not on the slinky).
    func begin(at e: CGPoint, g: M.Geo, top: Double, auto: Bool) -> Bool {
        if mode == .back || e.y > g.panelY || e.y < top + 96 {
            ignoring = true
            return false
        }
        let at = now
        if mode == .auto { takeOver(at, g: g, auto: auto) }
        let rest = restTop(g, s.k)
        let nearX = abs(e.x - g.baseX[s.k]) < g.R + 40
        let nearY = e.y > min(rest.1, s.py) - 60 && e.y < g.baseY[s.k] + 20
        if s.ff < 0 && !(nearX && nearY) {
            ignoring = true
            s.lastTouch = at
            // The loop was frozen by this touch, so the clock must run until it comes back.
            return true
        }
        ignoring = false
        s.grab = true
        s.snapped = false
        s.tick = 0
        s.lastTouch = at
        s.fingerX = e.x
        s.fingerY = e.y
        s.offX = s.px - e.x
        s.offY = s.py - e.y
        light.impactOccurred()
        return true
    }

    func move(to e: CGPoint, velocity: CGSize, g: M.Geo) {
        if ignoring { return }
        s.lastTouch = now
        s.fingerX = e.x
        s.fingerY = e.y
        if s.ff >= 0 { return }
        let bx = g.baseX[s.k]
        let by = g.baseY[s.k]
        var dx = e.x + s.offX - bx
        var dy = e.y + s.offY - by
        // It cannot be pushed into the step, nor squashed below half its height.
        dy = min(dy, -Double(M.n - 1) * g.pitch * 0.5)
        let L = hypot(dx, dy)
        if L > Self.maxPull {
            let over = L - Self.maxPull
            let L2 = Self.maxPull + over / (1 + over / 100)
            dx *= L2 / L
            dy *= L2 / L
        }
        s.px = bx + dx
        s.py = by + dy
        s.vx = velocity.width
        s.vy = velocity.height
        let step = Int(floor(hypot(dx, dy) / 60))
        if step != s.tick {
            s.tick = step
            tick.selectionChanged()
        }
    }

    func end() {
        if ignoring {
            ignoring = false
            return
        }
        s.grab = false
        s.lastTouch = now
        s.vx = max(-1500, min(1500, s.vx))
        s.vy = max(-1500, min(1500, s.vy))
    }
}
