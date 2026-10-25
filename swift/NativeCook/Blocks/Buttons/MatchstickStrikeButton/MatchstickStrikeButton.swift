import SwiftUI
import UIKit

/*
 Matchstick Strike Button — "Hearth", a candle shop's evening ritual whose call to action is lit
 with a real match.

 Look: a dark walnut table, a product card with an amber candle jar ("Fig & Cedar"), and a big
 oxblood matchbox with a sandpaper striker along its top edge, a printed cream border and the CTA
 "Strike to start" debossed in it. A long match lies above the box.

 Swipe the match head across the striker: fast enough (650 pt/s over 56 pt) and it catches —
 sparks are thrown back against the stroke and fall, the head flares into a fireball, a sulphur
 puff rises and a teardrop flame settles (a Metal shader that leans against the motion and
 flickers). Too slow, and only a dark scrape mark is left on the strip. Carry the flame under the
 CTA and its letters catch one by one (a white flash, then a gold glow); let go and the rest catch
 in a 60 ms chain. 2 s after the last touch the flame is blown out, the letters cool and a fresh
 match fades in at rest (0.9 s); then the loop starts again from t = 0
 (MatchstickStrikeButtonMotion.swift). Reduced motion: no loop; the resting screen, and a strike
 still lights the CTA.
*/
struct MatchstickStrikeButton: View {
    /// Plays the 8 s strike-and-light loop until touched.
    var autoPlay = false

    static let loop = MatchstickStrikeButtonMotion.loop
    private typealias M = MatchstickStrikeButtonMotion
    private typealias Art = MatchstickStrikeButtonArt

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = MatchstickStrikeButtonEngine()
    @State private var handActive = false
    @State private var idleTask: Task<Void, Never>? = nil
    @State private var medium = UIImpactFeedbackGenerator(style: .medium)
    @State private var select = UISelectionFeedbackGenerator()

    static let ink = Color(hex: 0xF5E6D3)
    static let mute = Color.rgba(245, 230, 211, 0.64)
    static let faint = Color.rgba(245, 230, 211, 0.42)
    static let ember = Color(hex: 0xF0A35C)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let s = Art.Scene(w: proxy.size.width + insets.leading + insets.trailing,
                              h: proxy.size.height + insets.top + insets.bottom,
                              top: insets.top, bottom: insets.bottom)
            stage(s, top: insets.top)
                .frame(width: s.w, height: s.h, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x150E0A).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            medium.prepare()
            select.prepare()
        }
        .onDisappear { idleTask?.cancel() }
    }

    private func stage(_ s: Art.Scene, top: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let ignite = M.igniteTimes(s.geo)
        return ZStack(alignment: .topLeading) {
            MatchstickStrikeButtonTable(scene: s).equatable()

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let pose = engine.pose(at: now, geo: s.geo, ignite: ignite, auto: auto)
                let base = CGPoint(x: pose.match.x, y: pose.match.y - 6)
                let fire = Art.fireBounds(pose, base)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in Art.behind(ctx, s, pose) }
                    MatchstickStrikeButtonBox(scene: s).equatable()
                    Canvas { ctx, _ in Art.front(ctx, s, pose) }
                    if pose.lit > 0.005 || pose.flare > 0.005 || pose.puff.strength > 0.01 || pose.curl.strength > 0.01 {
                        Rectangle()
                            .colorEffect(ShaderLibrary.matchstickStrikeButtonFire(
                                .float4(base.x, base.y, pose.lit, pose.flare),
                                .float4(pose.lean, pose.phase * 7, pose.phase * 11, 0),
                                .float4(pose.puff.x, pose.puff.y, pose.puff.age, pose.puff.strength),
                                .float4(pose.curl.x, pose.curl.y, pose.curl.age, pose.curl.strength),
                                .float2(fire.minX, fire.minY)
                            ))
                            .frame(width: fire.width, height: fire.height)
                            .offset(x: fire.minX, y: fire.minY)
                    }
                }
                .frame(width: s.w, height: s.h, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Color.clear
                .frame(width: s.w, height: s.h)
                .contentShape(Rectangle())
                .gesture(pan(s, auto: auto, top: top))

            texts(s, top: top)
                .allowsHitTesting(false)
        }
        .frame(width: s.w, height: s.h, alignment: .topLeading)
        .clipped()
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Strike to start. Swipe the match quickly across the strip on the matchbox to light it.")
    }

    // MARK: Text

    private func texts(_ s: Art.Scene, top: Double) -> some View {
        let jarRight = s.cardX + 18 + ((s.cardH - 32) * 0.8).rounded() + 16
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                Text("HEARTH")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(Self.ember)
                Text("Light the evening")
                    .font(.system(size: 30, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(Self.ink)
                Text("Wind-down ritual · 45 min")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.mute)
            }
            .frame(width: s.w - 40, alignment: .leading)
            .offset(x: 20, y: top + 12)

            VStack(alignment: .leading, spacing: 3) {
                Text("TONIGHT’S CANDLE")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(Self.ember)
                Text("Fig & Cedar")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                Text("Hand-poured soy · 3 wicks")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Self.mute)
                    .lineLimit(1)
                HStack(spacing: 6) {
                    tag("40 h burn")
                    tag("Woody")
                }
                .padding(.top, 6)
            }
            .frame(width: max(0, s.cardX + s.cardW - 16 - jarRight), alignment: .leading)
            .offset(x: jarRight, y: s.cardY + 18)

            HStack(spacing: 0) {
                foot("21:30", "Lights down")
                rule
                foot("Low Embers", "Playlist")
                rule
                foot("30 %", "Lamps")
            }
            .frame(width: s.w - 40)
            .offset(x: 20, y: s.boxY + s.boxH + 30)
        }
    }

    private func tag(_ t: String) -> some View {
        Text(t)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(Self.ink)
            .padding(.horizontal, 10)
            .padding(.vertical, 4)
            .background(Capsule().fill(Color.rgba(245, 230, 211, 0.08)))
            .overlay(Capsule().strokeBorder(Color.rgba(245, 230, 211, 0.18), lineWidth: 0.5))
    }

    private func foot(_ value: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Text(value)
                .font(.system(size: 16, weight: .semibold).monospacedDigit())
                .foregroundStyle(Self.ink)
                .lineLimit(1)
            Text(label)
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Self.faint)
        }
        .frame(maxWidth: .infinity)
    }

    private var rule: some View {
        Rectangle()
            .fill(Color.rgba(245, 230, 211, 0.18))
            .frame(width: 0.5, height: 28)
    }

    // MARK: Touch

    private func pan(_ s: Art.Scene, auto: Bool, top: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                let at = Date.now.timeIntervalSinceReferenceDate
                if !engine.finger {
                    // The finger lands: take over and grab the match where it is.
                    idleTask?.cancel()
                    handActive = true
                    engine.takeOver(at, s.geo)
                    engine.grab = (engine.sim.x - v.startLocation.x, engine.sim.y - v.startLocation.y)
                    engine.finger = true
                    engine.lastTouch = at
                    engine.lastDrag = (engine.sim.x, at)
                    if v.translation == .zero { return }
                }
                move(v, s, at, top: top)
            }
            .onEnded { _ in
                let at = Date.now.timeIntervalSinceReferenceDate
                let sim = engine.sim
                engine.finger = false
                engine.lastTouch = at
                if sim.litAt >= 0 && sim.outAt < 0 {
                    // Whatever the flame did not reach catches in a chain from left to right.
                    var k = 0.0
                    for i in sim.ignite.indices where sim.ignite[i] < 0 {
                        sim.ignite[i] = at + 0.15 + 0.06 * k
                        k += 1
                    }
                }
                idleTask?.cancel()
                idleTask = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(M.idleBeforeBack + M.backFor + 0.3))
                    if !Task.isCancelled && !engine.finger { handActive = false }
                }
            }
    }

    private func move(_ v: DragGesture.Value, _ scene: Art.Scene, _ at: Double, top: Double) {
        let s = engine.sim
        let g = scene.geo
        let x = max(8, min(scene.w - 8, v.location.x + engine.grab.0))
        let y = max(top + 40, min(scene.h - 20, v.location.y + engine.grab.1))
        let dx = x - s.x
        s.x = x
        s.y = y
        let velocityX = v.velocity.width
        s.vx = 0.6 * s.vx + 0.4 * velocityX
        engine.lastTouch = at
        let lit = s.litAt >= 0 && s.outAt < 0
        let onStrip = y > g.strikerTop - 10 && y < g.strikerBottom + 10 && x > g.strikerLeft && x < g.strikerRight
        if !lit && onStrip && s.lift > 0.3 {
            if abs(velocityX) > M.strikeSpeed {
                s.run += abs(dx)
                let dir = dx == 0 ? 1.0 : (dx > 0 ? 1.0 : -1.0)
                if !reduceMotion {
                    s.addSpark(x - 4, y, dir, at)
                    if abs(dx) > 6 { s.addSpark(x, y + 1, dir, at) }
                }
                if s.run >= M.strikeRun {
                    s.litAt = at
                    s.outAt = -1
                    s.px = x
                    s.py = y
                    s.cool = -1
                    for i in s.ignite.indices { s.ignite[i] = -1 }
                    medium.impactOccurred()
                }
            } else if abs(dx) > 0.5 {
                // Too slow: the head only scrapes the grit.
                if at - s.scrape.2 > 0.4 {
                    s.scrape = (x, x, at)
                    select.selectionChanged()
                } else {
                    s.scrape = (s.scrape.0, x, at)
                }
                s.run = 0
            }
        } else if !onStrip {
            s.run = 0
        }
        if lit {
            for i in s.ignite.indices where s.ignite[i] < 0 && abs(x - g.letters[i]) < 14 && y > g.baseline - 46 && y < g.baseline + 44 {
                s.ignite[i] = at
            }
        }
    }
}

/// The walnut table and the product card, drawn once.
private struct MatchstickStrikeButtonTable: View, Equatable {
    let scene: MatchstickStrikeButtonArt.Scene
    var body: some View {
        Canvas { ctx, _ in MatchstickStrikeButtonArt.table(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}

/// The matchbox, drawn once.
private struct MatchstickStrikeButtonBox: View, Equatable {
    let scene: MatchstickStrikeButtonArt.Scene
    var body: some View {
        Canvas { ctx, _ in MatchstickStrikeButtonArt.box(ctx, scene) }
            .frame(width: scene.w, height: scene.h)
    }
}
