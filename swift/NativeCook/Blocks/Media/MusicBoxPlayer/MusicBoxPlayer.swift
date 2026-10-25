import SwiftUI
import UIKit

/*
 Music Box Player — "Lullaby", a sleep-sounds app playing "Willow Waltz" on an open music box: a
 brass cylinder studded with pins, a steel comb of 18 teeth, a crank on the front.

 Turn the crank (a circular drag around its hub; it only turns forward, like a ratchet) or roll the
 cylinder down with a finger: the cylinder turns a quarter of the crank's angle, every pin that
 passes the comb bends its tooth back and lets it ring (decaying in 0.42 s, a warm glow at its tip),
 a note floats up, the governor's fan spins and the playhead follows. Let go and the cylinder runs
 on and slows (friction 1.6 /s). 2 s after the last touch (once it has stopped) it eases to the
 song's start (0.9 s) and the loop plays again (MusicBoxPlayerMotion.swift has the timeline).
 Reduced motion: no loop; the crank still plays it.
 */
struct MusicBoxPlayer: View {
    /// Plays the 8 s crank-the-song loop until touched.
    var autoPlay = false

    static let loop = MusicBoxPlayerMotion.loop

    private typealias M = MusicBoxPlayerMotion
    private typealias Art = MusicBoxPlayerArt

    static let ink = Art.col("#F1ECFF")
    static let mute = Art.col("rgba(241,236,255,0.62)")
    static let faint = Art.col("rgba(241,236,255,0.42)")
    static let gold = Art.col("#F3C987")

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = MusicBoxPlayerEngine()
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
        .background(Art.col("#161328").ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func stage(_ g: M.Geo, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            MusicBoxPlayerScene(g: g).equatable()

            TimelineView(.animation(paused: !(auto || busy))) { timeline in
                let p = engine.step(timeline.date, g: g, auto: auto)
                Canvas { c, _ in Art.frame(c, g, p) }
                    .frame(width: g.w, height: g.h)
            }
            .allowsHitTesting(false)

            texts(g, top: top)
                .allowsHitTesting(false)

            Color.clear
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(drag(g))
                .accessibilityElement()
                .accessibilityLabel("Willow Waltz on a music box. Turn the crank, or roll the cylinder down, to play it.")
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func texts(_ g: M.Geo, top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                Text("LULLABY")
                    .font(.system(size: 12, weight: .bold)).kerning(3).foregroundStyle(Self.gold)
                Text("Willow Waltz")
                    .font(.system(size: 34, weight: .bold)).kerning(-0.6).foregroundStyle(Self.ink)
                Text("Music box · 3/4 · Sleep sounds")
                    .font(.system(size: 15, weight: .medium)).foregroundStyle(Self.mute)
            }
            .frame(width: g.w - 48, alignment: .leading)
            .offset(x: 24, y: top + 14)

            ZStack(alignment: .topLeading) {
                HStack(alignment: .center, spacing: 12) {
                    VStack(alignment: .leading, spacing: 1) {
                        Text("Willow Waltz")
                            .font(.system(size: 17, weight: .bold)).foregroundStyle(Self.ink).lineLimit(1)
                        Text("Brass comb, 18 notes · Ida Morrow")
                            .font(.system(size: 13, weight: .medium)).foregroundStyle(Self.faint).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text("Turn the crank")
                        .font(.system(size: 13, weight: .semibold)).foregroundStyle(Self.gold)
                }
                .padding(.horizontal, 20)
                .padding(.top, 14)
                .frame(width: g.w - 32, alignment: .topLeading)

                HStack(spacing: 8) {
                    ForEach(Array(["Timer 30 min", "Fade out", "Night light"].enumerated()), id: \.offset) { i, label in
                        Text(label)
                            .font(.system(size: 12, weight: .semibold))
                            .foregroundStyle(i == 0 ? Self.gold : Self.mute)
                            .lineLimit(1)
                            .padding(.horizontal, 12)
                            .frame(height: 32)
                            .background(Capsule().fill(i == 0 ? Art.col("rgba(243,201,135,0.16)") : Art.col("rgba(241,236,255,0.08)")))
                    }
                }
                .frame(width: g.w - 72, alignment: .leading)
                .offset(x: 20, y: g.panelH - 22 - 32)
            }
            .frame(width: g.w - 32, height: g.panelH, alignment: .topLeading)
            .offset(x: 16, y: g.panelY)
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
    }

    private func drag(_ g: M.Geo) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !down {
                    down = true
                    engine.onIdle = { busy = false }
                    if engine.begin(at: v.startLocation, g: g, auto: autoPlay && !reduceMotion) { busy = true }
                }
                engine.move(to: v.location, g: g)
            }
            .onEnded { _ in
                down = false
                engine.end()
            }
    }
}

/// The loop clock and the cylinder under a finger, stepped once a frame like the RN frame callback.
@MainActor
final class MusicBoxPlayerEngine {
    typealias M = MusicBoxPlayerMotion
    enum Mode { case auto, touch, back }

    private static let backFor = 0.9
    private static let noteLife = 1.7

    private struct Sim {
        var a = 0.0
        var omega = 0.0
        var grab = false
        /// 0 the crank, 1 the cylinder.
        var how = 0
        var lastAng = 0.0
        var lastY = 0.0
        var lastMove = 0.0
        var lastTouch = 0.0
        var last = [Double](repeating: -1, count: MusicBoxPlayerMotion.teeth)
        var recent: [(Int, Double)] = []
        var backAt = -1.0
        var backFrom = 0.0
        var backTo = 0.0
        var tick = -1.0
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

    private static func wrapPi(_ x: Double) -> Double {
        var y = M.posMod(x, 2 * .pi)
        if y > .pi { y -= 2 * .pi }
        return y
    }

    /// Turns the cylinder to angle a, plucking every pin that passes the comb on the way.
    private func advance(_ a: Double, _ at: Double) -> Bool {
        if a <= s.a { return false }
        let hit = M.passed(s.a, a)
        for p in hit {
            let j = M.pins[p].tooth
            s.last[j] = at
            s.recent.append((j, at))
        }
        while let first = s.recent.first, at - first.1 > Self.noteLife { s.recent.removeFirst() }
        if s.recent.count > 20 { s.recent.removeFirst(s.recent.count - 20) }
        s.a = a
        return !hit.isEmpty
    }

    private func pluck(_ at: Double) {
        if at - s.tick > 0.09 {
            s.tick = at
            tick.selectionChanged()
        }
    }

    func step(_ date: Date, g: M.Geo, auto: Bool) -> M.Pose {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = min(max(raw, 0), 1.0 / 20)
        now += dt
        let at = now
        if mode == .touch {
            if !s.grab {
                s.omega *= exp(-dt * 1.6)
                if s.omega < 0.05 { s.omega = 0 }
                if advance(s.a + s.omega * dt, at) { pluck(at) }
            } else if at - s.lastMove > 0.08 {
                s.omega *= exp(-dt * 12)
            }
            if !s.grab && s.omega == 0 && at - s.lastTouch > 2 {
                s.backAt = at
                s.backFrom = s.a
                s.backTo = (s.a / (.pi * 2)).rounded() * .pi * 2
                mode = .back
            }
        } else if mode == .back {
            let newest = s.recent.last?.1 ?? -10
            if at - s.backAt >= Self.backFor && at - newest > Self.noteLife {
                s = Sim()
                mode = .auto
                loopStart = at
                if !auto {
                    let idle = onIdle
                    DispatchQueue.main.async { idle?() }
                }
            }
        }
        if mode == .auto {
            let t = auto ? (at - loopStart).truncatingRemainder(dividingBy: M.loop) : 0
            return M.loopPose(g, max(0, t))
        }
        let turn = Double.pi * 2
        if mode == .back {
            let k = min(1, (at - s.backAt) / Self.backFor)
            let e = k * k * (3 - 2 * k)
            let a = s.backFrom + (s.backTo - s.backFrom) * e
            let from = M.posMod(s.backFrom, turn) / turn
            return M.poseFrom(g, a, a * M.crankRatio, at, s.last, s.recent, from * (1 - e))
        }
        return M.poseFrom(g, s.a, s.a * M.crankRatio, at, s.last, s.recent, M.posMod(s.a, turn) / turn)
    }

    /// Takes the cylinder from wherever the loop has it, with its ringing teeth and notes.
    private func takeOver(_ at: Double, auto: Bool) {
        s = Sim()
        s.lastTouch = at
        if auto {
            let t = (at - loopStart).truncatingRemainder(dividingBy: M.loop)
            let base = at - t
            s.a = M.turnsAt(t) * .pi * 2
            for p in 0..<M.pinCount {
                let tp = M.plucks[p]
                if tp > t { continue }
                let j = M.pins[p].tooth
                if base + tp > s.last[j] { s.last[j] = base + tp }
                if t - tp < Self.noteLife { s.recent.append((j, base + tp)) }
            }
            // Keep the recent plucks in time order.
            s.recent.sort { $0.1 < $1.1 }
        }
        mode = .touch
    }

    /// A finger lands; false when it is ignored (not on the crank or the cylinder).
    func begin(at e: CGPoint, g: M.Geo, auto: Bool) -> Bool {
        if mode == .back {
            ignoring = true
            return false
        }
        let onCrank = hypot(e.x - g.hubX, e.y - g.hubY) < g.arm + 64
        let onCylinder = e.x > g.cylX0 - 10 && e.x < g.cylX1 + 10 && e.y > g.cylY - g.cylR - 30 && e.y < g.combY + 20
        if !onCrank && !onCylinder {
            ignoring = true
            return false
        }
        ignoring = false
        let at = now
        if mode == .auto { takeOver(at, auto: auto) }
        s.grab = true
        s.how = onCrank ? 0 : 1
        s.lastAng = atan2(e.y - g.hubY, e.x - g.hubX)
        s.lastY = e.y
        s.lastMove = at
        s.lastTouch = at
        light.impactOccurred()
        return true
    }

    func move(to e: CGPoint, g: M.Geo) {
        if ignoring { return }
        let at = now
        var d = 0.0
        if s.how == 0 {
            let ang = atan2(e.y - g.hubY, e.x - g.hubX)
            d = Self.wrapPi(ang - s.lastAng) / M.crankRatio
            s.lastAng = ang
        } else {
            d = (e.y - s.lastY) / (g.cylR * 1.6)
            s.lastY = e.y
        }
        // A ratchet: it only turns forward.
        if d > 0 {
            let dt = max(1.0 / 240, at - s.lastMove)
            s.omega = 0.6 * s.omega + 0.4 * min(14, d / dt)
            s.lastMove = at
            if advance(s.a + d, at) { pluck(at) }
        }
        s.lastTouch = at
    }

    func end() {
        if ignoring {
            ignoring = false
            return
        }
        let at = now
        s.grab = false
        s.lastTouch = at
        if at - s.lastMove > 0.1 { s.omega = 0 }
    }
}
