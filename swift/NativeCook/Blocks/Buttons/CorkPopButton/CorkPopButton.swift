import SwiftUI
import UIKit

/*
 Cork Pop Button — "Maison Vale", a wine shop's order-placed screen whose call to action is a
 bottle of champagne: the "Celebrate" label on its shoulder, gold foil and a wire cage on the neck.

 Drag the cork up: it twists against the finger (sideways drag rocks it), the cage's key spins
 loose and the cork inches out (16 pt at most, falling off: 16 (1 - e^(-pull / 70))). Past 105 pt
 (or a fast flick past 55 pt) it pops: cork and cage fly off with a spin, a cold puff of CO2,
 foam gushes up and runs down the neck, gold bubbles rise across the screen, the order card lifts
 with a sheen, and the line under it rolls to "Cheers". Let go before that and the cork settles
 back. 2 s after the last touch the open bottle sinks away and a corked one rises in its place,
 then the loop starts again (CorkPopButtonMotion.swift has the timeline).
 Reduced motion: no loop; a tap on the cork rolls the line (a fade) and nothing flies.
 */
struct CorkPopButton: View {
    /// Plays the 9 s twist-pop-celebrate loop until touched.
    var autoPlay = false

    static let loop = CorkPopButtonMotion.loop

    private typealias M = CorkPopButtonMotion
    private typealias Art = CorkPopButtonArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = CorkPopButtonEngine()
    @State private var sprites: Art.Sprites?
    @State private var spritesFor: M.Geo?
    @State private var busy = false
    @State private var down = false

    static let ink = Art.col("#F3EBD6")
    static let mute = Art.col("rgba(243,235,214,0.64)")
    static let gold = Art.col("#D9B56A")

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let g = Art.layout(w: proxy.size.width + insets.leading + insets.trailing,
                               h: proxy.size.height + insets.top + insets.bottom,
                               top: insets.top)
            stage(g, top: insets.top)
                .frame(width: g.w, height: g.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .onAppear { makeSprites(g) }
                .onChange(of: g) { makeSprites(g) }
        }
        .background(Art.col("#0C1E18").ignoresSafeArea())
        .preferredColorScheme(.dark)
    }

    private func makeSprites(_ g: M.Geo) {
        guard spritesFor != g else { return }
        spritesFor = g
        sprites = Art.sprites(g)
    }

    private func stage(_ g: M.Geo, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let sprites = sprites
        let cardRect = Art.layerRect(.card, g)
        let bottleRect = Art.layerRect(.bottle, g)
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: !(auto || busy))) { timeline in
                let p = engine.step(timeline.date, g: g, auto: auto, reduced: reduced, ready: sprites != nil)
                ZStack(alignment: .topLeading) {
                    CorkPopButtonLayer(kind: .room, g: g).equatable()
                    Canvas { c, _ in Art.cardShadows(c, g, p.lift) }
                        .frame(width: g.w, height: g.h)
                    CorkPopButtonLayer(kind: .card, g: g).equatable()
                        .scaleEffect(1 + 0.015 * p.lift,
                                     anchor: UnitPoint(x: (g.cx - cardRect.minX) / cardRect.width, y: (g.cardY + g.cardH / 2 - cardRect.minY) / cardRect.height))
                        .offset(x: cardRect.minX, y: cardRect.minY - 12 * p.lift)
                    Canvas { c, _ in Art.underBottle(c, g, p, sprites.map { c.resolve($0.cork) }) }
                        .frame(width: g.w, height: g.h)
                    CorkPopButtonLayer(kind: .bottle, g: g).equatable()
                        .offset(x: bottleRect.minX, y: bottleRect.minY + p.drop)
                    Canvas { c, _ in
                        if p.corked { Art.cage(c, g, inch: p.inch, loose: p.loose, rock: p.rock, dy: p.drop) }
                    }
                    .frame(width: g.w, height: g.h)
                    if p.bounds.width > 0 {
                        Rectangle()
                            .fill(Color.white)
                            .frame(width: g.w, height: g.h)
                            .colorEffect(ShaderLibrary.CorkPopButtonFoam(
                                .floatArray(p.balls),
                                .float4(p.bounds.minX, p.bounds.minY, p.bounds.width, p.bounds.height)
                            ))
                    }
                    Canvas { c, _ in
                        Art.over(c, g, p, sprites.map { c.resolve($0.cork) }, sprites.map { c.resolve($0.bubble) })
                    }
                    .frame(width: g.w, height: g.h)
                    hint(g, p.label, reduced: reduced)
                }
                .frame(width: g.w, height: g.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            header(g, top: top)
                .allowsHitTesting(false)

            Color.clear
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(drag(g, reduced: reduced))
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Celebrate. Drag the champagne cork up until it pops.")
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func header(_ g: M.Geo, top: Double) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("MAISON VALE")
                .font(.system(size: 12, weight: .bold)).kerning(3).foregroundStyle(Self.gold)
            Text("Thank you, Ines")
                .font(.system(size: 32, weight: .bold)).kerning(-0.6).foregroundStyle(Self.ink)
            Text("Your order is placed and on its way")
                .font(.system(size: 15, weight: .medium)).foregroundStyle(Self.mute)
        }
        .frame(width: g.w - 48, alignment: .leading)
        .offset(x: 24, y: top + 14)
    }

    private func hint(_ g: M.Geo, _ k: Double, reduced: Bool) -> some View {
        let roll = reduced ? 0.0 : 24.0
        return ZStack(alignment: .top) {
            Text("Twist the cork up to celebrate")
                .font(.system(size: 15, weight: .medium)).foregroundStyle(Self.mute)
                .lineLimit(1)
                .padding(.top, 2)
                .opacity(1 - k)
                .offset(y: -roll * k)
            Text("Cheers! See you on Thursday")
                .font(.system(size: 15, weight: .semibold)).foregroundStyle(Self.gold)
                .lineLimit(1)
                .padding(.top, 2)
                .opacity(k)
                .offset(y: roll * (1 - k))
        }
        .frame(width: g.w - 48, height: 24, alignment: .top)
        .clipped()
        .offset(x: 24, y: g.hintY)
    }

    private func drag(_ g: M.Geo, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !down {
                    down = true
                    engine.onIdle = { busy = false }
                    if engine.begin(at: v.startLocation, g: g, reduced: reduced, auto: autoPlay && !reduced) { busy = true }
                }
                engine.move(translation: v.translation, velocity: v.velocity, reduced: reduced)
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

/// The loop clock and the finger's simulation, stepped once a frame like the RN frame callback.
@MainActor
final class CorkPopButtonEngine {
    typealias M = CorkPopButtonMotion
    enum Mode { case auto, touch }

    /// The swap under a finger: the loop's 1.6 s one, played in this long.
    private static let swapTouch = 0.9
    private static let popPull = 105.0

    private struct Sim {
        var inch = 0.0
        var rock = 0.0
        var loose = 0.0
        var strain = 0.0
        var popAt = -1.0
        var backAt = -1.0
        var swapFor = 0.9
        var lastTouch = 0.0
        var grab = false
        var tick = 0
    }

    private var last: Date?
    private var now = 0.0
    private var loopStart = 0.0
    private var started = false
    private var mode = Mode.auto
    private var s = Sim()
    private var ignoring = false
    private var quietOn = false
    private var quietAt = -10.0
    var onIdle: (() -> Void)?

    private let light = UIImpactFeedbackGenerator(style: .light)
    private let medium = UIImpactFeedbackGenerator(style: .medium)
    private let tick = UISelectionFeedbackGenerator()

    func step(_ date: Date, g: M.Geo, auto: Bool, reduced: Bool, ready: Bool) -> M.Pose {
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
        if reduced {
            let k = M.clamp01((at - quietAt) / 0.3)
            var p = M.makePose(M.autoClocks(0), g)
            p.label = quietOn ? k : 1 - k
            return p
        }
        if mode == .touch {
            if !s.grab && s.popAt < 0 {
                // Let go before the pop: the cork settles back and the cage tightens.
                let k = 1 - exp(-dt * 9)
                s.inch -= s.inch * k
                s.rock -= s.rock * k
                s.loose -= s.loose * k
                s.strain -= s.strain * k
            }
            let idle = !s.grab && at - s.lastTouch > 2
            if idle && s.popAt < 0 {
                backToLoop(at, auto: auto)
            } else if idle && s.backAt < 0 && at - s.popAt > 2.2 {
                s.backAt = at
                s.swapFor = Self.swapTouch
            } else if s.backAt >= 0 && at - s.backAt > s.swapFor && at - s.popAt > M.bubblesDone {
                backToLoop(at, auto: auto)
            }
        }
        if mode == .auto {
            let t = auto ? (at - loopStart).truncatingRemainder(dividingBy: M.loop) : 0
            return M.makePose(M.autoClocks(max(0, t)), g)
        }
        let c = M.Clocks(inch: s.inch, rock: s.rock, loose: s.loose,
                         shake: s.strain * 0.8 * sin(2 * .pi * 41 * at), finger: 0,
                         popE: s.popAt >= 0 ? at - s.popAt : -1,
                         swapE: s.backAt >= 0 ? at - s.backAt : -1,
                         swapFor: s.swapFor)
        return M.makePose(c, g)
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

    /// Hands what the loop shows to the simulation, so a touch never makes anything jump.
    private func takeOver(_ at: Double, auto: Bool) {
        s = Sim()
        s.lastTouch = at
        if mode == .auto && auto {
            let t = (at - loopStart).truncatingRemainder(dividingBy: M.loop)
            let c = M.autoClocks(t)
            if t < M.popAt {
                s.inch = c.inch
                s.rock = c.rock
                s.loose = c.loose
            } else {
                s.popAt = at - (t - M.popAt)
                if t >= M.swapAt {
                    s.backAt = at - (t - M.swapAt)
                    s.swapFor = M.swapFor
                }
            }
        }
        mode = .touch
    }

    /// A finger lands; false when it is ignored (away from the cork).
    func begin(at p: CGPoint, g: M.Geo, reduced: Bool, auto: Bool) -> Bool {
        let near = hypot(p.x - g.cx, p.y - (g.lipY - g.corkH * 0.5)) < 84
        if !near {
            ignoring = true
            return false
        }
        ignoring = false
        let at = now
        if reduced {
            quietOn.toggle()
            quietAt = at
            light.impactOccurred()
            return true
        }
        if mode == .touch { s.lastTouch = at } else { takeOver(at, auto: auto) }
        if s.popAt < 0 {
            s.grab = true
            s.tick = 0
            light.impactOccurred()
        }
        return true
    }

    func move(translation: CGSize, velocity: CGSize, reduced: Bool) {
        if ignoring || reduced { return }
        let at = now
        s.lastTouch = at
        if !s.grab || s.popAt >= 0 { return }
        let pull = max(0, -translation.height)
        s.inch = 16 * (1 - exp(-pull / 70))
        s.rock = max(-8, min(8, translation.width * 0.12))
        s.loose = min(1, pull / 95 + abs(translation.width) / 300)
        s.strain = max(0, min(1, (pull - 60) / 45))
        let step = Int(floor(pull / 22))
        if step > s.tick {
            s.tick = step
            tick.selectionChanged()
        } else if step < s.tick {
            s.tick = step
        }
        if pull >= Self.popPull || (pull > 55 && velocity.height < -1100) {
            s.popAt = at
            s.grab = false
            s.inch = 0
            s.rock = 0
            s.loose = 0
            s.strain = 0
            medium.impactOccurred()
        }
    }

    func end(reduced: Bool) {
        if ignoring || reduced {
            ignoring = false
            return
        }
        s.grab = false
        s.lastTouch = now
    }
}
