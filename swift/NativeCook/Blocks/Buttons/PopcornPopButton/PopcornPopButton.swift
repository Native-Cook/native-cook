import SwiftUI
import UIKit

/*
 Popcorn Pop Button — "Reel Night", a movie-night screen whose "Start the movie" button is a little
 copper pan of kernels under a glass lid.

 Hold the button: the pan heats (an orange glow under it, the thermometer arc around the button
 fills gold to red, the kernels jitter), then they pop one by one — each bursts into a puffy corn
 with a squash and a spin, flies under the lid and bounces into the heap — and the heap lifts the
 lid. Let go once 15 have popped and the label rolls to "Enjoy the show"; let go earlier and the
 kernels settle back as the pan cools. 2 s after the last touch the pan tips into the bowl on the
 movie card, fresh kernels drop in and the loop starts again (PopcornPopButtonMotion.swift has the
 timeline). Reduced motion: no loop and no popping; a press rolls the label (a fade) and the pan
 stays still.
 */
struct PopcornPopButton: View {
    /// Plays the 8 s heat-pop-serve loop until touched.
    var autoPlay = false

    static let loop = PopcornPopButtonMotion.loop

    private typealias M = PopcornPopButtonMotion
    private typealias Art = PopcornPopButtonArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = PopcornPopButtonEngine()
    @State private var sprites: [Image] = []
    @State private var spritesFor = 0.0
    @State private var busy = false
    @State private var down = false

    static let ink = Art.col("#F7E6DA")
    static let mute = Art.col("rgba(247,230,218,0.62)")
    static let faint = Art.col("rgba(247,230,218,0.4)")
    static let gold = Art.col("#F4B85C")

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let g = Art.layout(w: proxy.size.width + insets.leading + insets.trailing,
                               h: proxy.size.height + insets.top + insets.bottom,
                               top: insets.top, bottom: insets.bottom)
            stage(g, top: insets.top)
                .frame(width: g.w, height: g.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .onAppear { makeSprites(g) }
                .onChange(of: g.D) { makeSprites(g) }
        }
        .background(Art.col("#16070C").ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func makeSprites(_ g: M.Geo) {
        guard spritesFor != g.D else { return }
        spritesFor = g.D
        sprites = Art.sprites(g)
    }

    private func stage(_ g: M.Geo, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let ready = sprites.count == 10
        let sprites = sprites
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !(auto || busy))) { timeline in
                let p = engine.step(timeline.date, g: g, auto: auto, reduced: reduced, ready: ready)
                ZStack(alignment: .topLeading) {
                    layer(.room, g, top)
                    ZStack(alignment: .topLeading) {
                        layer(.plate, g, top)
                        Canvas { c, _ in Art.heatArc(c, g, p) }
                            .frame(width: g.w, height: g.h)
                        ZStack(alignment: .topLeading) {
                            Canvas { c, _ in Art.panGlow(c, g, p) }
                                .frame(width: g.w, height: g.h)
                            layer(.pan, g, top)
                            Canvas { c, _ in Art.inside(c, g, p, sprites.map { c.resolve($0) }) }
                                .frame(width: g.w, height: g.h)
                            lid(g, p)
                        }
                        .frame(width: g.w, height: g.h, alignment: .topLeading)
                        .offset(x: p.shakeX, y: p.shakeY)
                        label(g, p.label, reduced: reduced)
                    }
                    .frame(width: g.w, height: g.h, alignment: .topLeading)
                    .scaleEffect(1 - 0.03 * p.press, anchor: UnitPoint(x: g.bx / g.w, y: g.by / g.h))
                    Canvas { c, _ in Art.flying(c, g, p, sprites.map { c.resolve($0) }) }
                        .frame(width: g.w, height: g.h)
                    bowl(g, p.bowl)
                }
                .frame(width: g.w, height: g.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            texts(g, top: top)
                .allowsHitTesting(false)

            Color.clear
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(press(g, reduced: reduced))
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Start the movie. Hold the pan to pop the popcorn, then let go.")
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func layer(_ kind: PopcornPopButtonLayer.Kind, _ g: M.Geo, _ top: Double) -> some View {
        let r = Art.rect(kind, g)
        return PopcornPopButtonLayer(kind: kind, g: g, top: top)
            .equatable()
            .offset(x: r.minX, y: r.minY)
    }

    private func lid(_ g: M.Geo, _ p: M.Pose) -> some View {
        let r = Art.rect(.lid, g)
        return PopcornPopButtonLayer(kind: .lid, g: g, top: 0)
            .equatable()
            .rotationEffect(.degrees(p.lidRot), anchor: UnitPoint(x: (g.px - r.minX) / r.width, y: (g.py - r.minY) / r.height))
            .offset(x: r.minX + p.lidX, y: r.minY + p.lidY)
    }

    private func bowl(_ g: M.Geo, _ bump: Double) -> some View {
        let r = Art.rect(.bowl, g)
        let b = 1 + 0.08 * bump
        return PopcornPopButtonLayer(kind: .bowl, g: g, top: 0)
            .equatable()
            .scaleEffect(x: 2 - b, y: b, anchor: UnitPoint(x: (g.bowlX - r.minX) / r.width, y: (g.bowlY + 36 - r.minY) / r.height))
            .offset(x: r.minX, y: r.minY)
    }

    private func label(_ g: M.Geo, _ k: Double, reduced: Bool) -> some View {
        let roll = reduced ? 0.0 : 26.0
        let width = g.D * 0.72
        return VStack(spacing: 2) {
            PopcornPopButtonRollingLine(a: "Hold to pop", b: "Fresh, salted, buttered", k: k, roll: roll, width: width, top: 8) {
                $0.font(.system(size: 12, weight: .semibold)).kerning(1.2).foregroundStyle(Self.faint)
            }
            PopcornPopButtonRollingLine(a: "Start the movie", b: "Enjoy the show", k: k, roll: roll, width: width, top: 1) {
                $0.font(.system(size: 21, weight: .bold)).kerning(-0.2).foregroundStyle(Self.ink)
            }
        }
        .frame(width: width)
        .offset(x: g.bx - width / 2, y: g.by + g.D * 0.215)
    }

    private func texts(_ g: M.Geo, top: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                Text("REEL NIGHT")
                    .font(.system(size: 12, weight: .bold)).kerning(3).foregroundStyle(Self.gold)
                Text("Friday, 8 pm")
                    .font(.system(size: 32, weight: .bold)).kerning(-0.6).foregroundStyle(Self.ink)
                Text("Living room · 4 guests · Sofa seats")
                    .font(.system(size: 15, weight: .medium)).foregroundStyle(Self.mute)
            }
            .frame(width: g.w - 48, alignment: .leading)
            .offset(x: 24, y: top + 14)

            let cardLeft = g.cardX + 86
            VStack(alignment: .leading, spacing: 3) {
                Text("TONIGHT’S PICK")
                    .font(.system(size: 11, weight: .bold)).kerning(1.4).foregroundStyle(Self.gold)
                Text("Moonlit Harbor")
                    .font(.system(size: 20, weight: .semibold)).foregroundStyle(Self.ink).lineLimit(1)
                Text("Drama · 1 h 52 min · Rated 4.6")
                    .font(.system(size: 13, weight: .medium)).monospacedDigit().foregroundStyle(Self.mute).lineLimit(1)
            }
            .frame(width: max(0, g.bowlX - 40 - cardLeft), alignment: .leading)
            .offset(x: cardLeft, y: g.cardY + 17)

            HStack(spacing: 0) {
                ForEach([("22", "Kernels"), ("Sea salt", "Seasoning"), ("3 min", "Pop time")], id: \.1) { item in
                    VStack(spacing: 2) {
                        Text(item.0)
                            .font(.system(size: 16, weight: .semibold)).monospacedDigit().foregroundStyle(Self.ink).lineLimit(1)
                        Text(item.1)
                            .font(.system(size: 12, weight: .medium)).foregroundStyle(Self.faint)
                    }
                    .padding(.top, 6)
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(width: g.w - 32)
            .offset(x: 16, y: g.footerY)
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
    }

    private func press(_ g: M.Geo, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                guard !down else { return }
                down = true
                engine.onIdle = { busy = false }
                if engine.begin(at: v.startLocation, g: g, reduced: reduced, auto: autoPlay && !reduced) { busy = true }
                if reduced {
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(400))
                        if !down { busy = false }
                    }
                }
            }
            .onEnded { _ in
                down = false
                engine.end(reduced: reduced)
                if reduced {
                    Task { @MainActor in
                        try? await Task.sleep(for: .milliseconds(400))
                        if !down { busy = false }
                    }
                }
            }
    }
}

/// One line of the label that rolls from one text to the other.
struct PopcornPopButtonRollingLine<S: View>: View {
    let a: String
    let b: String
    let k: Double
    let roll: Double
    let width: Double
    let top: Double
    let style: (Text) -> S

    var body: some View {
        ZStack(alignment: .top) {
            style(Text(a))
                .lineLimit(1)
                .padding(.top, top)
                .opacity(1 - k)
                .offset(y: -roll * k)
            style(Text(b))
                .lineLimit(1)
                .padding(.top, top)
                .opacity(k)
                .offset(y: roll * (1 - k))
        }
        .frame(width: width, height: 28, alignment: .top)
        .clipped()
    }
}

/// The loop clock and the finger's simulation, stepped once a frame like the RN frame callback.
@MainActor
final class PopcornPopButtonEngine {
    typealias M = PopcornPopButtonMotion
    enum Mode { case auto, touch, back }

    /// The bowl-and-refill under a finger is the loop's 1.6 s one, played in this long.
    private static let backFor = 0.9

    private var last: Date?
    private var now = 0.0
    private var loopStart = 0.0
    private var started = false
    private var mode = Mode.auto
    private var s = M.Story()
    private var holding = false
    private var cook = 0.0
    private var lastTouch = 0.0
    private var backAt = 0.0
    private var ignoring = false
    /// Reduced motion: the label's state, faded with the frame clock.
    private var quietOn = false
    private var quietAt = -10.0
    var onIdle: (() -> Void)?

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()
    private let success = UINotificationFeedbackGenerator()

    private func reset() {
        s = M.Story()
        s.emptyFor = Self.backFor
        holding = false
        cook = 0
        lastTouch = 0
        backAt = 0
    }

    private func cornCount(_ at: Double) -> Int {
        var n = 0
        for i in 0..<M.n where s.popAt[i] <= at && s.goneAt[i] > at { n += 1 }
        return n
    }

    func step(_ date: Date, g: M.Geo, auto: Bool, reduced: Bool, ready: Bool) -> M.Pose {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        // A stalled frame pauses the clock instead of skipping ahead.
        let dt = min(max(raw, 0), 1.0 / 20)
        if ready && !started {
            // The loop starts at t = 0 on the first frame that has the art.
            started = true
            loopStart = now
        }
        if started { now += dt }
        let at = now
        if reduced {
            let k = M.clamp01((at - quietAt) / 0.3)
            var p = M.makePose(M.autoStory(0), g)
            p.label = quietOn ? k : 1 - k
            return p
        }
        if mode != .auto {
            if holding {
                s.heat += (1 - s.heat) * (1 - exp(-dt * 1.6))
            } else {
                s.heat = max(0, s.heat - dt * (mode == .back ? 2.5 : 0.6))
            }
            s.press += ((holding ? 1 : 0) - s.press) * (1 - exp(-dt * 28))
            // Heat above the threshold cooks the kernels; they pop in rank order (left-over heat pops a few more).
            if s.heat > 0.45 && mode == .touch { cook += (s.heat - 0.45) * dt * 2.2 }
            var popped = false
            for r in 0..<M.n {
                let i = M.byRank[r]
                let free = s.popAt[i] > at || s.goneAt[i] <= at - s.emptyFor
                if free && mode == .touch && cook >= M.cookNeeded(r) {
                    s.popAt[i] = at
                    s.goneAt[i] = M.never
                    popped = true
                }
            }
            if popped { tick.selectionChanged() }
            let count = cornCount(at)
            let lifted = s.lidAt <= at && s.lidBack < s.lidAt
            if !lifted && count >= M.enough { s.lidAt = at }
            if mode == .touch && !holding && at - lastTouch > 2 {
                // Into the bowl, fresh kernels in, lid down, label back.
                mode = .back
                backAt = at
                s.emptyFor = Self.backFor
                for i in 0..<M.n where s.popAt[i] <= at && s.goneAt[i] > at { s.goneAt[i] = at }
                if s.lidAt <= at && s.lidBack < s.lidAt { s.lidBack = at }
                if s.labelAt <= at && s.labelOff < s.labelAt { s.labelOff = at }
            } else if mode == .back && at > backAt + Self.backFor + 0.05 && s.heat < 0.01 {
                reset()
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
            return M.makePose(M.autoStory(max(0, t)), g)
        }
        s.now = at
        return M.makePose(s, g)
    }

    /// Hands what the loop shows to the simulation, so a press never makes anything jump.
    private func takeOver(_ at: Double, auto: Bool) {
        if mode == .touch {
            // Already under a finger: keep the pan as it is.
            lastTouch = at
            return
        }
        reset()
        lastTouch = at
        if mode == .auto && auto {
            let t = (at - loopStart).truncatingRemainder(dividingBy: M.loop)
            let base = at - t
            var count = 0
            for i in 0..<M.n {
                if M.autoPop[i] <= t {
                    s.popAt[i] = base + M.autoPop[i]
                    count += 1
                }
                if t >= 6.4 { s.goneAt[i] = base + 6.4 }
            }
            s.emptyFor = t >= 6.4 ? 1.6 : Self.backFor
            if t >= 4.6 { s.lidAt = base + 4.6 }
            if t >= 6.4 { s.lidBack = base + 6.4 }
            if t >= 5.0 { s.labelAt = base + 5.0 }
            if t >= 6.4 { s.labelOff = base + 6.4 }
            s.heat = M.autoHeat(t)
            s.press = M.autoPress(t)
            cook = t < 6.4 && count > 0 ? M.cookNeeded(count - 1) + 0.001 : 0
        }
        mode = .touch
    }

    /// A finger lands; false when it is ignored (off the button, or while the pan is being served).
    func begin(at p: CGPoint, g: M.Geo, reduced: Bool, auto: Bool) -> Bool {
        let inside = hypot(p.x - g.bx, p.y - g.by) < g.D / 2 + 6
        if !inside || mode == .back {
            ignoring = true
            return false
        }
        if reduced {
            quietOn.toggle()
            quietAt = now
            light.impactOccurred()
            return true
        }
        takeOver(now, auto: auto)
        holding = true
        light.impactOccurred()
        return true
    }

    func end(reduced: Bool) {
        if ignoring {
            ignoring = false
            return
        }
        if reduced || mode != .touch { return }
        let at = now
        holding = false
        lastTouch = at
        let labelOn = s.labelAt <= at && s.labelOff < s.labelAt
        if !labelOn && cornCount(at) >= M.enough {
            s.labelAt = at
            success.notificationOccurred(.success)
        }
    }
}
