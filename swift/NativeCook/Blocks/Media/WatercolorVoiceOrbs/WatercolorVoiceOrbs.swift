import SwiftUI
import UIKit

/*
 Watercolor Voice Orbs — a voice picker whose orb is a living watercolour that repaints itself for
 each voice.

 Look: a black screen. "Choose a voice" (24 pt semibold, white) centred at 17 % of the height; the
 orb (radius 22 % of the width) centred at 42 %, with a soft glow of its mid colour behind it
 (radius 1.25 R, blur 34, 35 %). Under it the voice's name (18 pt semibold), its line (14 pt, white
 50 %) and a pager of four 6 pt dots (14 pt apart, the current one white, stretching between dots).
 A white "Continue" pill (52 pt tall, 24 pt from the sides) at the bottom.
 The orb is a Metal shader (WatercolorVoiceOrbs.metal): domain-warped value noise (two fbm warps
 of five octaves) painted deep → mid → light, with a bright side facing each voice's direction, a
 darker rim and a soft light upper left. The noise drifts on circles of the loop's phase, so the
 texture flows and comes back to itself every loop. Voices: Lagoon (blue, bright at the top), Dune
 (terracotta, bright lower right), Moss (green, bright at the left), Dusk (violet, bright below);
 each also sits elsewhere in the noise and twists its middle by its own amount, and between two
 voices every parameter is blended, so a swipe makes the clouds flow and turn into the next ones while the next palette seeps in
 through them (where the warp field is low first) instead of fading over.

 autoPlay timeline (loop = 10 s); v is the voice position, 0…3:
   0.0 – 1.4   Lagoon at rest; the orb breathes 1 → 1.02 (period 2.5 s, four per loop).
   1.4, 3.6, 5.8   a swipe to the next voice: v eases over 0.85 s (cubic in-out), the orb squashes
               1 − 0.11·e^(−2.4u)·sin(7.5u) (to ~0.94, then ~1.02), the names slide 24 pt per
               voice and cross-fade, the pager dot travels and stretches.
   +0.85       the voice lands and speaks a 1.1 s preview: the orb swells up to 3 % and its glow
               brightens with four syllables.
   7.8 – 8.8   a glide back through Moss and Dune to Lagoon (1.0 s), which speaks again.
   9.9 – 10.0  rest; the frame at 10.0 is the one at 0.0.

 Touch: drag sideways to move between voices continuously (a voice per 55 % of the width, a
 rubber band past the ends, a light tick at each voice); release settles on the nearest voice (or
 the next one in the direction of a quick flick) in 0.45 s, squashes and speaks. A tap makes the orb
 give and speak. 2 s after the last touch the picker glides back to Lagoon (0.9 s) and the loop
 starts again from its rest.
 Reduced motion: no flow, breathing, squash or speech; the picker shows Lagoon, and a drag or a tap
 changes voice with a 0.25 s cross-fade.
 */

struct WatercolorVoiceOrbs: View {
    var autoPlay = false

    static let loop = WatercolorVoiceOrbsModel.loop
    typealias M = WatercolorVoiceOrbsModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var appear = Date.now
    /// autoPlay drives everything while it is on.
    @State private var autoOn = false
    /// Hand-made state: v glides from → to; the last squash and the last landing.
    @State private var glide: (from: Double, to: Double, at: Date, dur: Double) = (0, 0, .distantPast, 0)
    @State private var squashAt = Date.distantPast
    @State private var landedAt = Date.distantPast
    @State private var drag: (start: Double, current: Double)? = nil
    @State private var lastIndex = 0
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    struct Shown {
        var v: Double
        var scale: Double
        var glow: Double
        var tau: Double
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: reduceMotion)) { timeline in
                let s = shown(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    Color.black
                    orb(s, size: size)
                    texts(s, size: size, insets: insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(gesture(width: size.width))
            }
            .ignoresSafeArea()
        }
        .background(Color.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            appear = .now
            haptic.prepare()
            autoOn = autoPlay && !reduceMotion
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func shown(at now: Date) -> Shown {
        let clock = now.timeIntervalSince(appear)
        let e = clock - floor(clock / M.loop) * M.loop
        let tau = 2 * Double.pi * e / M.loop
        if reduceMotion {
            return Shown(v: glide.from + (glide.to - glide.from) * M.clamp(now.timeIntervalSince(glide.at) / 0.25),
                         scale: 1, glow: 0.35, tau: 0)
        }
        let breath = M.breathing(clock)
        if autoOn {
            let a = M.auto(e)
            let speak = M.speak(a.sinceLanding)
            return Shown(v: a.v, scale: breath * M.squash(a.sinceSwipe) + 0.03 * speak, glow: 0.35 + 0.25 * speak, tau: tau)
        }
        let speak = M.speak(now.timeIntervalSince(landedAt))
        return Shown(v: manualV(at: now), scale: breath * M.squash(now.timeIntervalSince(squashAt)) + 0.03 * speak,
                     glow: 0.35 + 0.25 * speak, tau: tau)
    }

    private func manualV(at now: Date) -> Double {
        if let drag { return drag.current }
        let u = glide.dur > 0 ? now.timeIntervalSince(glide.at) / glide.dur : 1
        return glide.from + (glide.to - glide.from) * (glide.dur > 0.6 ? M.easeInOut(u) : M.easeOut(u))
    }

    /// Freezes what autoPlay shows into the hand-made state.
    private func takeOver(at now: Date) {
        resume?.cancel()
        guard autoOn else { return }
        let clock = now.timeIntervalSince(appear)
        let a = M.auto(clock - floor(clock / M.loop) * M.loop)
        glide = (a.v, a.v, now, 0)
        squashAt = now.addingTimeInterval(-a.sinceSwipe)
        landedAt = now.addingTimeInterval(-a.sinceLanding)
        autoOn = false
    }

    // MARK: Touch

    private func gesture(width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if drag == nil {
                    takeOver(at: now)
                    let v = manualV(at: now)
                    drag = (v, v)
                    lastIndex = Int(v.rounded())
                }
                guard let d = drag, abs(value.translation.width) > 4 || d.current != d.start else { return }
                var v = d.start - value.translation.width / (0.55 * width)
                let top = Double(M.voices.count - 1)
                if v < 0 { v *= 0.3 } else if v > top { v = top + (v - top) * 0.3 }
                drag?.current = v
                let i = Int(M.clamp(v, 0, top).rounded())
                if i != lastIndex {
                    lastIndex = i
                    haptic.impactOccurred(intensity: 0.5)
                }
            }
            .onEnded { value in
                let now = Date.now
                guard let d = drag else { return }
                drag = nil
                let top = Double(M.voices.count - 1)
                let moved = abs(value.translation.width) > 4
                var target = d.current.rounded()
                if moved {
                    // A flick: the finger's speed carried for 0.2 s goes past 60 pt.
                    let flick = value.velocity.width * 0.2
                    if abs(flick) > 60 { target = flick < 0 ? floor(d.current) + 1 : ceil(d.current) - 1 }
                } else {
                    target = d.current.rounded()
                }
                target = M.clamp(target, 0, top)
                glide = (d.current, target, now, reduceMotion ? 0.25 : 0.45)
                if !reduceMotion {
                    squashAt = now
                    landedAt = now.addingTimeInterval(moved ? 0.45 : 0.1)
                }
                haptic.impactOccurred(intensity: moved ? 0.8 : 0.6)
                scheduleResume()
            }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            let v = manualV(at: now)
            if abs(v) > 0.001 {
                glide = (v, 0, now, 0.9)
                squashAt = now
                landedAt = now.addingTimeInterval(0.9)
            }
            // Rejoin the loop inside its rest (loop time 9.9 … 1.4), after the glide and the speech.
            let ready = now.addingTimeInterval(abs(v) > 0.001 ? 0.9 + M.speakLength : 0)
            let er = ready.timeIntervalSince(appear).truncatingRemainder(dividingBy: M.loop)
            let wait = (er >= 9.9 || er < 1.4) ? 0 : 9.9 - er
            try? await Task.sleep(for: .seconds(ready.timeIntervalSince(now) + wait))
            guard !Task.isCancelled else { return }
            autoOn = true
        }
    }

    // MARK: Drawing

    private func orb(_ s: Shown, size: CGSize) -> some View {
        let b = M.blend(s.v)
        let radius = 0.25 * size.width
        let center = CGPoint(x: size.width / 2, y: size.height * 0.43)
        return Canvas { ctx, _ in
            // The glow of the voice's mid colour.
            ctx.drawLayer { g in
                g.addFilter(.blur(radius: 34))
                let gr = radius * 1.25 * s.scale
                g.fill(Path(ellipseIn: CGRect(x: center.x - gr, y: center.y - gr, width: 2 * gr, height: 2 * gr)),
                       with: .color(Color(red: b.mid.x, green: b.mid.y, blue: b.mid.z).opacity(s.glow)))
            }
            let r = radius * s.scale
            let rect = CGRect(x: center.x - r, y: center.y - r, width: 2 * r, height: 2 * r)
            let shader = ShaderLibrary.watercolorVoiceOrbs(
                .float2(center), .float(r), .float(s.tau),
                .float3(b.a.deep.x, b.a.deep.y, b.a.deep.z), .float3(b.a.mid.x, b.a.mid.y, b.a.mid.z), .float3(b.a.light.x, b.a.light.y, b.a.light.z),
                .float3(b.b.deep.x, b.b.deep.y, b.b.deep.z), .float3(b.b.mid.x, b.b.mid.y, b.b.mid.z), .float3(b.b.light.x, b.b.light.y, b.b.light.z),
                .float(b.f),
                .float2(b.band.x, b.band.y), .float2(b.offset.x, b.offset.y), .float(b.swirl))
            ctx.fill(Path(ellipseIn: rect.insetBy(dx: -1, dy: -1)), with: .shader(shader, bounds: rect.insetBy(dx: -1, dy: -1)))
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
        .accessibilityElement()
        .accessibilityLabel("Voice \(M.voices[Int(M.clamp(s.v, 0, 3).rounded())].name)")
        .accessibilityAdjustableAction { direction in
            let i = Int(M.clamp(manualV(at: .now), 0, 3).rounded()) + (direction == .increment ? 1 : -1)
            takeOver(at: .now)
            glide = (manualV(at: .now), M.clamp(Double(i), 0, 3), .now, 0.45)
            scheduleResume()
        }
    }

    private func texts(_ s: Shown, size: CGSize, insets: EdgeInsets) -> some View {
        let w = size.width
        let radius = 0.25 * w
        let nameY = size.height * 0.43 + radius + 34
        let pagerY = nameY + 62
        return ZStack(alignment: .topLeading) {
            Text("Choose a voice")
                .font(.system(size: 24, weight: .semibold))
                .foregroundStyle(.white)
                .frame(width: w)
                .offset(y: size.height * 0.17 - 15)

            ForEach(0..<M.voices.count, id: \.self) { i in
                let d = Double(i) - s.v
                let a = 1 - M.smoothstep(0, 0.55, abs(d))
                if a > 0.001 {
                    VStack(spacing: 5) {
                        Text(M.voices[i].name)
                            .font(.system(size: 18, weight: .semibold))
                            .foregroundStyle(.white)
                        Text(M.voices[i].line)
                            .font(.system(size: 14))
                            .foregroundStyle(.white.opacity(0.5))
                    }
                    .lineLimit(1)
                    .frame(width: w)
                    .offset(x: 24 * d, y: nameY)
                    .opacity(a)
                }
            }

            // Pager: the current dot travels and stretches between dots.
            Canvas { ctx, sz in
                let n = M.voices.count
                let x0 = sz.width / 2 - Double(n - 1) * 7
                for i in 0..<n {
                    ctx.fill(Path(ellipseIn: CGRect(x: x0 + Double(i) * 14 - 3, y: 0, width: 6, height: 6)),
                             with: .color(.white.opacity(0.28)))
                }
                let v = M.clamp(s.v, -0.3, Double(n - 1) + 0.3)
                let f = v - floor(v)
                let stretch = 10 * (1 - abs(2 * f - 1))
                let cx = x0 + v * 14
                ctx.fill(Path(roundedRect: CGRect(x: cx - 3 - stretch / 2, y: 0, width: 6 + stretch, height: 6), cornerRadius: 3),
                         with: .color(.white))
            }
            .frame(width: w, height: 6)
            .offset(y: pagerY)

            Text("Continue")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(.black)
                .frame(width: w - 48, height: 52)
                .background(Capsule().fill(.white))
                .offset(x: 24, y: size.height - max(insets.bottom, 16) - 12 - 52)
        }
        .allowsHitTesting(false)
    }
}
