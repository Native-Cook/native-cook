import SwiftUI
import UIKit

/*
 Parachute Drop Alert — "Parcelpoint", a delivery app whose "your parcel is 4 minutes away" alert
 arrives by parachute.

 The card drops in from above the screen under a coral-and-cream canopy, swinging like a pendulum
 and drifting, lands on the screen with a squash; the canopy deflates, drapes over the card's top
 edge and the wind pulls it away. "Track" and "Later" both send it off: a fresh canopy blooms out of
 its top and lifts it away (ParachuteDropAlertMotion.swift has the timeline). Drag the card and it
 swings from its lines like a pendulum (g 2000 pt/s², 200 pt rope) under the finger; let go and it
 springs back to its place (8 rad/s, damping 0.6) while the swing dies out. 2 s after the last
 touch it lifts away and the loop starts again.
 Reduced motion: no loop; the alert sits in place, Track / Later fade it out (0.3 s), and it fades
 back 2 s later.
 */
struct ParachuteDropAlert: View {
    /// Plays the 9 s drop-land-lift loop until touched.
    var autoPlay = false

    static let loop = ParachuteDropAlertMotion.loop

    private typealias M = ParachuteDropAlertMotion
    private typealias Art = ParachuteDropAlertArt

    static let ink = Art.col("#1D2B3D")
    static let mute = Art.col("#6A7482")
    static let coral = Art.col("#E2553C")

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = ParachuteDropAlertEngine()
    @State private var card: Image?
    @State private var cardFor: M.Geo?
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
                .onAppear { makeCard(g) }
                .onChange(of: g) { makeCard(g) }
        }
        .background(Art.col("#F3EEE7").ignoresSafeArea())
        .preferredColorScheme(.light)
    }

    private func makeCard(_ g: M.Geo) {
        guard cardFor != g else { return }
        cardFor = g
        card = Art.cardSprite(g)
    }

    private func stage(_ g: M.Geo, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let card = card
        return ZStack(alignment: .topLeading) {
            ParachuteDropAlertApp(g: g).equatable()
            appTexts(g, top: top)
                .allowsHitTesting(false)

            TimelineView(.animation(paused: !(auto || busy))) { timeline in
                let f = engine.step(timeline.date, g: g, auto: auto, reduced: reduced, ready: card != nil)
                Canvas { c, _ in
                    Art.alert(c, g, card.map { c.resolve($0) }, f.pose, pressed: f.pressed)
                }
                .frame(width: g.w, height: g.h)
                .opacity(f.pose.fade)
            }
            .allowsHitTesting(false)

            Color.clear
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(drag(g, reduced: reduced))
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Your parcel is 4 min away. Leo is on Linden Street now. Door code 2417 is ready. Track or Later.")
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func appTexts(_ g: M.Geo, top: Double) -> some View {
        let rows = [("Linen napkins, set of 4", "Parcel locker 14 · Thu", "2 items"),
                    ("Warm desk lamp bulb", "Front door · Fri", "1 item"),
                    ("Oat milk frother", "Front door · Sat", "1 item")]
        let cw = g.w - 32
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                Text("PARCELPOINT")
                    .font(.system(size: 12, weight: .bold)).kerning(3).foregroundStyle(Self.coral)
                Text("Package arriving")
                    .font(.system(size: 34, weight: .bold)).kerning(-0.6).foregroundStyle(Self.ink)
                Text("Today · 2 deliveries on the way")
                    .font(.system(size: 15, weight: .medium)).foregroundStyle(Art.col("#3E4B5C"))
            }
            .frame(width: g.w - 48, alignment: .leading)
            .offset(x: 24, y: top + 14)

            // Card 1: its head row.
            HStack(alignment: .top, spacing: 8) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Ceramic mug set")
                        .font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.ink).lineLimit(1)
                    Text("from Kiln & Clay · 2 items")
                        .font(.system(size: 13, weight: .medium)).foregroundStyle(Self.mute).lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .trailing, spacing: 0) {
                    Text("ETA")
                        .font(.system(size: 11, weight: .bold)).kerning(1.2).foregroundStyle(Self.mute)
                    Text("6:42 pm")
                        .font(.system(size: 20, weight: .bold)).monospacedDigit().foregroundStyle(Self.ink)
                }
            }
            .padding(.leading, 72)
            .padding(.trailing, 16)
            .padding(.top, 18)
            .frame(width: cw, alignment: .topLeading)
            .offset(x: 16, y: g.card1Y)

            // Card 1: the route's stops and the courier.
            ForEach(0..<4, id: \.self) { i in
                let labels = ["Packed", "Shipped", "Out for delivery", "Arriving"]
                let x = 30 + (g.w - 32 - 60) * Double(i) / 3
                let left = i == 0 ? x - 10 : i == 3 ? x - 74 : x - 42
                let align: Alignment = i == 0 ? .leading : i == 3 ? .trailing : .center
                Text(labels[i])
                    .font(.system(size: 11, weight: .semibold))
                    .foregroundStyle(i == 3 ? Self.ink : Self.mute)
                    .lineLimit(1)
                    .fixedSize()
                    .frame(width: 84, alignment: align)
                    .offset(x: 16 + left, y: g.card1Y + 116)
            }
            Text("Courier Leo · Van 12 · 0.6 mi away")
                .font(.system(size: 13, weight: .medium)).foregroundStyle(Self.ink)
                .offset(x: 32, y: g.card1Y + 148)

            Text("Also coming")
                .font(.system(size: 17, weight: .bold)).foregroundStyle(Self.ink)
                .offset(x: 24, y: g.sectionY)

            ForEach(0..<g.rows, id: \.self) { i in
                HStack(spacing: 0) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text(rows[i].0)
                            .font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.ink).lineLimit(1)
                        Text(rows[i].1)
                            .font(.system(size: 13, weight: .medium)).foregroundStyle(Self.mute).lineLimit(1)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text(rows[i].2)
                        .font(.system(size: 13, weight: .semibold)).monospacedDigit().foregroundStyle(Self.mute)
                }
                .padding(.leading, 72)
                .padding(.trailing, 18)
                .frame(width: cw, height: 68)
                .offset(x: 16, y: g.rowsY + Double(i) * 78)
            }

            HStack(spacing: 0) {
                ForEach(Array(["Home", "Track", "Send", "Profile"].enumerated()), id: \.offset) { i, label in
                    Text(label)
                        .font(.system(size: 11, weight: .semibold))
                        .foregroundStyle(i == 1 ? Self.coral : Art.col("#8A93A0"))
                        .frame(maxWidth: .infinity)
                }
            }
            .frame(width: g.w)
            .offset(y: g.tabY + 32)
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
    }

    private func drag(_ g: M.Geo, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !down {
                    down = true
                    engine.onIdle = { busy = false }
                    if engine.begin(at: v.startLocation, g: g, reduced: reduced, auto: autoPlay && !reduced) { busy = true }
                }
                engine.move(to: v.location, velocity: v.velocity, reduced: reduced)
            }
            .onEnded { _ in
                down = false
                if engine.end(reduced: reduced) && reduced {
                    // Faded out, back 2 s later: keep the clock running until it is back.
                    busy = true
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(2800))
                        if !down { busy = false }
                    }
                } else if reduced {
                    busy = false
                }
            }
    }
}

/// The loop clock and the finger's pendulum, stepped once a frame like the RN frame callback.
@MainActor
final class ParachuteDropAlertEngine {
    typealias M = ParachuteDropAlertMotion
    enum Mode { case auto, touch, back }

    private static let backFor = 0.9
    private static let rope = 200.0
    private static let gravity = 2000.0

    private struct Sim {
        var x = 0.0, y = 0.0, vx = 0.0, vy = 0.0
        var theta = 0.0, omega = 0.0
        var grab = false
        var offX = 0.0, offY = 0.0
        var lastTouch = 0.0
        var chute = false
        var collapseAt = -1.0
        var backAt = -1.0
        var downAt = 0.0
        var downX = 0.0, downY = 0.0
        var moved = 0.0
        var pressed = -1
    }

    struct Frame {
        var pose: M.Pose
        var pressed: Int
    }

    private var last: Date?
    private var now = 0.0
    private var loopStart = 0.0
    private var started = false
    private var mode = Mode.auto
    private var s = Sim()
    private var ignoring = false
    /// Reduced motion: shown, and since when.
    private var quietShown = true
    private var quietAt = -10.0
    private var lastPose: M.Pose?
    var onIdle: (() -> Void)?

    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let success = UINotificationFeedbackGenerator()

    /// Which button (0 Later, 1 Track) a point on the landed card is in, or -1.
    private func buttonAt(_ g: M.Geo, _ x: Double, _ y: Double) -> Int {
        let lx = x - (g.landX - g.cardW / 2)
        let ly = y - (g.landY - g.cardH / 2)
        for (i, r) in [g.later, g.track].enumerated()
        where lx >= r.minX - 4 && lx <= r.maxX + 4 && ly >= r.minY - 6 && ly <= r.maxY + 6 {
            return i
        }
        return -1
    }

    func step(_ date: Date, g: M.Geo, auto: Bool, reduced: Bool, ready: Bool) -> Frame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = min(max(raw, 0), 1.0 / 20)
        if ready && !started {
            started = true
            loopStart = now
        }
        if started { now += dt }
        let at = now
        if reduced && !quietShown && at - quietAt > 2.3 {
            quietShown = true
            quietAt = at
        }
        if mode == .touch {
            let n = 4
            let h = dt / Double(n)
            for _ in 0..<n {
                var ax = 0.0
                if !s.grab {
                    // Back to its place on a spring; the swing keeps going and dies out.
                    let w = 8.0
                    let z = 0.6
                    ax = -w * w * (s.x - g.landX) - 2 * z * w * s.vx
                    let ay = -w * w * (s.y - g.landY) - 2 * z * w * s.vy
                    s.vx += ax * h
                    s.vy += ay * h
                    s.x += s.vx * h
                    s.y += s.vy * h
                }
                // A pendulum hanging from the card's lines, pushed by the card's sideways acceleration.
                let alpha = -(Self.gravity / Self.rope) * sin(s.theta) - 1.1 * s.omega - (s.grab ? 0 : ax / Self.rope * cos(s.theta))
                s.omega += alpha * h
                s.theta += s.omega * h
            }
            s.theta = max(-0.7, min(0.7, s.theta))
            // A chute still open lands with the card, then deflates.
            if !s.grab && s.chute && s.collapseAt < 0 && hypot(s.x - g.landX, s.y - g.landY) < 4 && hypot(s.vx, s.vy) < 80 {
                s.collapseAt = at
                light.impactOccurred()
            }
            if !s.grab && at - s.lastTouch > 2 {
                s.backAt = at
                mode = .back
            }
        } else if mode == .back && at - s.backAt >= Self.backFor {
            s = Sim()
            mode = .auto
            loopStart = at
            if !auto {
                let idle = onIdle
                DispatchQueue.main.async { idle?() }
            }
        }
        let p = pose(at, g: g, auto: auto, reduced: reduced)
        lastPose = p
        return Frame(pose: p, pressed: mode == .touch ? s.pressed : -1)
    }

    private func pose(_ at: Double, g: M.Geo, auto: Bool, reduced: Bool) -> M.Pose {
        if reduced {
            var p = M.loopPose(5.5, g)
            let k = M.clamp01((at - quietAt) / 0.3)
            p.fade = quietShown ? k : 1 - k
            return p
        }
        if mode == .auto {
            let t = auto ? (at - loopStart).truncatingRemainder(dividingBy: M.loop) : 5.5
            return M.loopPose(max(0, t), g)
        }
        let breath = sin(2 * .pi * 2.1 * at)
        if mode == .back {
            var p = M.loopPose(M.liftAt + (8.0 - M.liftAt) * min(1, (at - s.backAt) / Self.backFor), g)
            if s.collapseAt >= 0 && at - s.collapseAt < 1.2 {
                p.canopies.append(M.collapsing(g, M.hangingCanopy(g, g.landX, g.landY, 0, breath, 1), g.landX, g.landY, at - s.collapseAt))
            }
            return p
        }
        var p = M.Pose(cardX: s.x, cardY: s.y, cardRot: s.theta * 0.6, shown: true, scrim: 0.36)
        if s.chute {
            if s.collapseAt < 0 {
                p.canopies.append(M.hangingCanopy(g, s.x, s.y, s.theta, breath, 1))
            } else if at - s.collapseAt < 1.2 {
                p.canopies.append(M.collapsing(g, M.hangingCanopy(g, g.landX, g.landY, 0, breath, 1), s.x, s.y, at - s.collapseAt))
            }
        }
        return p
    }

    /// Takes the card from wherever the loop has it.
    private func takeOver(_ at: Double, _ p: M.Pose, auto: Bool) {
        s = Sim()
        s.x = p.cardX
        s.y = p.cardY
        s.theta = p.cardRot / 0.6
        s.lastTouch = at
        let t = auto ? (at - loopStart).truncatingRemainder(dividingBy: M.loop) : 5
        if t < M.landAt || t >= M.liftAt {
            s.chute = true
        } else if t < M.landAt + 1.2 {
            s.chute = true
            s.collapseAt = at - (t - M.landAt)
        }
        mode = .touch
    }

    private func dismiss(_ at: Double, g: M.Geo, reduced: Bool) {
        if reduced {
            quietShown = false
            quietAt = at
            return
        }
        s.backAt = at
        if mode == .auto {
            s.collapseAt = -1
            s.x = g.landX
        }
        mode = .back
    }

    /// A finger lands; false when it is ignored (not on the card).
    func begin(at e: CGPoint, g: M.Geo, reduced: Bool, auto: Bool) -> Bool {
        guard let p = lastPose, p.shown, p.fade >= 0.5, mode != .back else {
            ignoring = true
            return false
        }
        let inside = abs(e.x - p.cardX) < g.cardW / 2 + 10 && abs(e.y - p.cardY) < g.cardH / 2 + 10
        if !inside {
            ignoring = true
            return false
        }
        ignoring = false
        let at = now
        if !reduced && mode == .auto { takeOver(at, p, auto: auto) }
        s.downAt = at
        s.downX = e.x
        s.downY = e.y
        s.moved = 0
        s.pressed = abs(p.cardRot) < 0.05 && hypot(p.cardX - g.landX, p.cardY - g.landY) < 6 ? buttonAt(g, e.x, e.y) : -1
        self.g = g
        if reduced { return true }
        s.grab = true
        s.offX = s.x - e.x
        s.offY = s.y - e.y
        s.vx = 0
        s.vy = 0
        s.lastTouch = at
        tick.selectionChanged()
        return true
    }

    private var g: M.Geo?

    func move(to e: CGPoint, velocity: CGSize, reduced: Bool) {
        if ignoring || reduced { return }
        let at = now
        s.moved = max(s.moved, hypot(e.x - s.downX, e.y - s.downY))
        if s.moved > 10 { s.pressed = -1 }
        let dt = max(1.0 / 240, at - s.lastTouch)
        let ax = (velocity.width - s.vx) / dt
        // The finger's sideways acceleration swings the card.
        s.omega += -(ax / Self.rope) * cos(s.theta) * min(dt, 1.0 / 30)
        s.x = e.x + s.offX
        s.y = e.y + s.offY
        s.vx = velocity.width
        s.vy = velocity.height
        s.lastTouch = at
    }

    /// The finger lifts; true when it was a tap on Track or Later (the alert is sent off).
    func end(reduced: Bool) -> Bool {
        if ignoring {
            ignoring = false
            return false
        }
        let at = now
        let tap = s.moved < 10 && at - s.downAt < 0.35 && s.pressed >= 0
        s.grab = false
        s.lastTouch = at
        s.vx = max(-2500, min(2500, s.vx))
        s.vy = max(-2500, min(2500, s.vy))
        s.pressed = -1
        if tap, let g {
            success.notificationOccurred(.success)
            dismiss(at, g: g, reduced: reduced)
        }
        return tap
    }
}
