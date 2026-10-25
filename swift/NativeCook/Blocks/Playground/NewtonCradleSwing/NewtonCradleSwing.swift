import SwiftUI
import UIKit

/*
 Newton Cradle Swing — "Desk Toys · Momentum": a chrome Newton's cradle on a walnut desk under a
 lamp. Pull a ball (or two, three…) out and let go: the swing passes through the still balls,
 one collision every half period, a light click each time, 3 % of the swing lost per hit.

 One Canvas; the room, the frame and one ball are rendered once into bitmaps, each frame draws
 five balls, ten strings and five contact shadows (NewtonCradleSwingArt). The motion is a folded
 pendulum (NewtonCradleSwingMotion): analytic in autoPlay (loop = 8 s), integrated under the
 finger.

 Modes: 0 loop · 1 finger on a ball · 2 free swing · 3 easing back to rest before the loop
 restarts (2 s after the last touch, 0.9 s ease-in-out) · 4 reduced motion's gentle return.
 */
struct NewtonCradleSwing: View {
    /// Plays the 8 s demo: pull ball 1, release, five seconds of swinging, settle.
    var autoPlay = false

    static let loop = NewtonCradleSwingMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = NewtonCradleSwingEngine()
    /// Keeps the timeline running while a touch's swing is still moving (outside autoPlay).
    @State private var active = false

    fileprivate static let background = Color(hex: 0x120E0B)
    fileprivate static let ink = Color(hex: 0xF3EBE1)
    fileprivate static let inkSoft = Color(hex: 0xAB9B8B)
    private static let cardH = 104.0
    private static let statusWords = ["Ready", "Pulling back", "Swinging"]

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Self.background.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let bottom = max(insets.bottom, 12)
        let g = NewtonCradleSwingGeo(w: size.width, h: size.height)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let art = engine.art(for: g, scale: displayScale)
        return TimelineView(.animation(paused: !auto && !active)) { timeline in
            let f = engine.step(to: timeline.date, auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                Canvas { c, _ in
                    NewtonCradleSwingArt.draw(c, g, art, f.angles, f.finger)
                }
                .frame(width: size.width, height: size.height)
                .contentShape(Rectangle())
                .gesture(drag(g: g, reduced: reduced))
                .accessibilityElement()
                .accessibilityAddTraits(.allowsDirectInteraction)
                .accessibilityLabel("Newton's cradle. Pull a sphere sideways and let go.")

                VStack(alignment: .leading, spacing: 4) {
                    Text("DESK TOYS")
                        .font(.system(size: 11, weight: .bold))
                        .kerning(2.4)
                        .foregroundStyle(Color(hex: 0xD2B48F))
                    Text("Momentum")
                        .font(.system(size: 34, weight: .bold))
                        .kerning(-0.6)
                        .foregroundStyle(Self.ink)
                    Text("Five chrome spheres on a steel frame")
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(Self.inkSoft)
                }
                .frame(width: size.width - 48, alignment: .leading)
                .offset(x: 24, y: insets.top + 18)
                .allowsHitTesting(false)

                card(status: statusText(f.status), energy: f.energy)
                    .frame(width: size.width - 40)
                    .offset(x: 20, y: min(g.baseBottom + 28, size.height - bottom - 40 - Self.cardH))
                    .allowsHitTesting(false)

                Text("Pull a sphere and let go · grab two for a double")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(Self.ink.opacity(0.5))
                    .multilineTextAlignment(.center)
                    .frame(width: size.width - 48, height: size.height - bottom - 14, alignment: .bottom)
                    .offset(x: 24)
                    .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .coordinateSpace(name: "cradle")
            .onChange(of: f.idle) { _, idle in
                if idle { active = false }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func statusText(_ code: Int) -> String {
        let kind = code / 1000
        let hits = code % 1000
        if kind == 2 && hits > 0 { return "\(hits) \(hits == 1 ? "collision" : "collisions")" }
        return Self.statusWords[kind]
    }

    private func card(status: String, energy: Double) -> some View {
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return VStack(alignment: .leading, spacing: 12) {
            HStack(alignment: .top, spacing: 16) {
                VStack(alignment: .leading, spacing: 6) {
                    label("SESSION")
                    Text(status)
                        .font(.system(size: 17, weight: .semibold).monospacedDigit())
                        .foregroundStyle(Self.ink)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                VStack(alignment: .leading, spacing: 6) {
                    label("ENERGY")
                    Capsule()
                        .fill(Color(hex: 0xE7C49B))
                        .scaleEffect(x: max(0.001, energy), y: 1, anchor: .leading)
                        .frame(height: 6)
                        .background(Capsule().fill(Color(hex: 0xFFF0DE, opacity: 0.1)))
                        .clipShape(Capsule())
                        .padding(.top, 8)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Text("Period 1.02 s · 3 % lost at each collision")
                .font(.system(size: 13, weight: .medium).monospacedDigit())
                .foregroundStyle(Self.inkSoft)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
        }
        .padding(.horizontal, 20)
        .padding(.vertical, 16)
        .background(shape.fill(Color(.sRGB, red: 22 / 255, green: 15 / 255, blue: 11 / 255, opacity: 0.78)))
        .overlay(
            shape.strokeBorder(
                LinearGradient(colors: [Color(hex: 0xFFF0DE, opacity: 0.08), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)),
                lineWidth: 1
            )
        )
        .overlay(shape.strokeBorder(Color(hex: 0xFFE8CE, opacity: 0.14), lineWidth: 0.5))
        .background(
            shape.fill(Self.background)
                .shadow(color: .black.opacity(0.35), radius: 1.5, y: 1)
                .shadow(color: .black.opacity(0.32), radius: 18, y: 14)
        )
    }

    private func label(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 11, weight: .semibold))
            .kerning(1.4)
            .foregroundStyle(Self.inkSoft)
    }

    private func drag(g: NewtonCradleSwingGeo, reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named("cradle"))
            .onChanged { value in
                active = true
                engine.touch(at: value.location, g: g)
            }
            .onEnded { _ in
                engine.release(reduced: reduced)
            }
    }
}

/// What one frame shows.
struct NewtonCradleSwingFrame {
    var angles: [Double]
    var finger: Double
    var energy: Double
    /// kind × 1000 + collisions (kind: 0 ready, 1 pulling back, 2 swinging).
    var status: Int
    /// Nothing moves and no ball is held (lets the timeline pause outside autoPlay).
    var idle: Bool
}

/// The pendulum, the autoPlay clock and the touch, stepped once a frame.
@MainActor
final class NewtonCradleSwingEngine {
    private typealias M = NewtonCradleSwingMotion

    private static let returnAfter = 2.0
    private static let returnFor = 0.9

    private var images: NewtonCradleSwingArt.Images?
    private var imagesKey: (NewtonCradleSwingGeo, Double)?

    private var last: Double?
    private var loopStart: Double?
    private var mode: Int?
    private var sim = NewtonCradleSwingSim()
    /// The finger: ball, last ψ, ψ', last time.
    private var grab: (Int, Double, Double, Double)?
    private var touching = false
    private var lastTouch = 0.0
    /// Mode 3 / 4: start time, ψ at start.
    private var back = (0.0, 0.0)
    private var shownPsi = 0.0
    private var group = 1
    private var lastFrame: NewtonCradleSwingFrame?

    private static let click = UIImpactFeedbackGenerator(style: .light)
    private static let grabFeel = UISelectionFeedbackGenerator()

    private static func clock() -> Double {
        Date().timeIntervalSinceReferenceDate
    }

    func art(for g: NewtonCradleSwingGeo, scale: Double) -> NewtonCradleSwingArt.Images {
        if let images, let key = imagesKey, key.0 == g, key.1 == scale { return images }
        let made = NewtonCradleSwingArt.render(g, scale: scale)
        images = made
        imagesKey = (g, scale)
        return made
    }

    func step(to date: Date, auto: Bool, reduced: Bool) -> NewtonCradleSwingFrame {
        let t0 = date.timeIntervalSinceReferenceDate
        if let last, t0 <= last, let lastFrame { return lastFrame }
        let dt = min(last.map { t0 - $0 } ?? 1 / 60, 1 / 30)
        last = t0
        if loopStart == nil { loopStart = t0 }
        if mode == nil { mode = auto ? 0 : 2 }

        var frame: NewtonCradleSwingFrame
        switch mode {
        case 0:
            let t = (t0 - (loopStart ?? t0)).truncatingRemainder(dividingBy: M.loop)
            let kind = t < 1.2 || t > 7.05 ? 0 : t < 2 ? 1 : 2
            frame = NewtonCradleSwingFrame(
                angles: M.ballAngles(M.loopPsi(t), 1), finger: M.loopFinger(t), energy: M.loopEnergy(t),
                status: kind * 1000 + M.loopHits(t), idle: false
            )
        case 1:
            frame = NewtonCradleSwingFrame(
                angles: M.ballAngles(sim.psi, sim.n), finger: 0, energy: min(1, abs(sim.psi) / M.pull),
                status: 1000, idle: false
            )
        default:
            if mode == 4 {
                sim.psi = back.1 * (1 - M.easeInOut(M.seg(t0 - back.0, 0, 0.5)))
                sim.vel = 0
            } else if M.step(&sim, dt) && !reduced {
                Self.click.impactOccurred()
            }
            var shown = sim.psi
            var amp = sim.amplitude
            if mode == 3 {
                let k = M.easeInOut(M.seg(t0 - back.0, 0, Self.returnFor))
                shown *= 1 - k
                amp *= 1 - k
                if k >= 1 {
                    mode = 0
                    loopStart = t0
                    shown = 0
                }
            } else if mode == 2 && auto && t0 - lastTouch > Self.returnAfter {
                mode = 3
                back = (t0, sim.psi)
            }
            let still = amp < 0.004 && shown == 0 && !touching
            frame = NewtonCradleSwingFrame(
                angles: M.ballAngles(shown, sim.n), finger: 0, energy: min(1, amp / M.pull),
                status: amp < 0.004 ? 0 : 2000 + min(999, sim.hits),
                idle: !auto && still
            )
        }
        lastFrame = frame
        return frame
    }

    /// The finger's angle around ball i's pivot, folded onto the side it pulls toward.
    private func pull(_ g: NewtonCradleSwingGeo, _ i: Int, _ p: CGPoint) -> (Double, Int) {
        let theta = atan2(p.x - g.xs[i], p.y - g.pivotY)
        let left = p.x < g.xs[i]
        // Pulling left takes ball i and every ball left of it; pulling right, i and those right of it.
        return left ? (min(0, M.rubberBand(theta)), i + 1) : (max(0, M.rubberBand(theta)), M.balls - i)
    }

    func touch(at p: CGPoint, g: NewtonCradleSwingGeo) {
        let now = Self.clock()
        if !touching {
            touching = true
            let a = lastFrame?.angles ?? M.ballAngles(0, 1)
            var best = -1
            var bestD = g.R * 2.4
            for i in 0..<M.balls {
                let d = hypot(p.x - (g.xs[i] + g.L * sin(a[i])), p.y - (g.pivotY + g.L * cos(a[i])))
                if d < bestD {
                    bestD = d
                    best = i
                }
            }
            guard best >= 0 else { return }
            let q = pull(g, best, p)
            grab = (best, q.0, 0, now)
            sim = NewtonCradleSwingSim(psi: q.0, vel: 0, n: q.1, hits: 0)
            mode = 1
            Self.grabFeel.selectionChanged()
            return
        }
        guard let h = grab else { return }
        let q = pull(g, h.0, p)
        let dt = max(1.0 / 120, now - h.3)
        let v = 0.65 * h.2 + 0.35 * (q.0 - h.1) / dt
        grab = (h.0, q.0, v, now)
        sim = NewtonCradleSwingSim(psi: q.0, vel: v, n: q.1, hits: 0)
    }

    func release(reduced: Bool) {
        touching = false
        guard let h = grab else { return }
        grab = nil
        lastTouch = Self.clock()
        if reduced {
            back = (lastTouch, sim.psi)
            mode = 4
        } else {
            sim = NewtonCradleSwingSim(psi: sim.psi, vel: max(-6, min(6, h.2)), n: sim.n, hits: 0)
            mode = 2
        }
    }
}
