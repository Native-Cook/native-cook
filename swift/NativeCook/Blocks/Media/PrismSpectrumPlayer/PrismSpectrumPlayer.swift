import SwiftUI
import UIKit

/*
 Prism Spectrum Player — a "now playing" screen where a white beam enters a glass prism and leaves
 as a rainbow fan; each of the fan's twelve spectral bands is an equaliser bar.

 Touch: drag across the hero to turn the prism (±25°, rubber band past it, momentum on release);
 the fan follows on a spring and opens wider as it turns. The play button plays and pauses (the
 beam brightens and the bars rise, or the beam dims and the bars fall). 2 s after the last touch
 the screen eases back to rest (paused, prism level, 1:12) in 0.8 s and the loop starts again.
 Reduced motion: no loop; the prism still turns under a finger, play/pause fades.

 The hero is one Metal shader over the whole screen; the controls are two Canvases over it (one
 drawn once, one per frame from the pose); the titles are plain Text.
 */
struct PrismSpectrumPlayer: View {
    /// Plays, turns the prism one way and the other, pauses and skips back, on an 8 s loop.
    var autoPlay = false

    static let loop = PrismSpectrumPlayerMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = PrismSpectrumPlayerEngine()
    /// Keeps the timeline running outside autoPlay while something moves.
    @State private var awake = false

    private typealias Art = PrismSpectrumPlayerArt

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
        let g = PrismSpectrumPlayerGeo(w: size.width, h: size.height, top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let running = auto || awake
        return ZStack(alignment: .topLeading) {
            Art.background

            TimelineView(.animation(paused: !running)) { timeline in
                let n = timeline.date.timeIntervalSinceReferenceDate
                let frame = engine.step(n, auto: auto, reduced: reduced)
                let ports = PrismSpectrumPlayerMotion.ports(g, frame.pose.rot)
                let f = PrismSpectrumPlayerMotion.fanShape(frame.pose.fan, frame.pose.play)
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.prismSpectrumPlayer(
                            .float2(max(1, g.w), max(1, g.h)),
                            .float(g.u),
                            .float4(g.cx, g.cy, g.r, frame.pose.rot),
                            .float4(ports.inX, ports.inY, ports.outX, ports.outY),
                            .float4(f.dir, f.spread, frame.pose.beam, frame.pose.play),
                            .float2(frame.ambient, PrismSpectrumPlayerMotion.loop),
                            .float2(g.lmin, g.lmax)
                        ))
                        .frame(width: size.width, height: size.height)
                    Canvas { c, _ in
                        Art.drawDynamic(c, g, frame.pose, pressPlay: frame.press)
                    }
                    .frame(width: size.width, height: size.height)
                }
                .onChange(of: frame.idle, initial: true) { _, idle in
                    if idle && !auto { awake = false }
                }
            }
            .allowsHitTesting(false)

            Canvas { c, _ in
                Art.drawStatic(c, g, top: top, bottom: bottom)
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)

            // The hero: a horizontal drag turns the prism.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: max(1, g.heroBottom - top - 48))
                .padding(.top, top + 48)
                .gesture(
                    DragGesture(minimumDistance: 4)
                        .onChanged { v in
                            awake = true
                            engine.drag(v.translation.width, reduced: reduced)
                        }
                        .onEnded { v in
                            engine.dragEnded(velocity: v.velocity.width, auto: auto)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Glass prism splitting the beam into a spectrum. Drag sideways to turn it.")

            VStack(spacing: 2) {
                Text("PLAYING FROM ALBUM")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.6)
                    .foregroundStyle(Art.rgb(0xEEEAF8, 0.55))
                Text("Glasshouse")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(Art.rgb(0xEEEAF8))
            }
            .frame(width: max(0, size.width - 128))
            .padding(.leading, 64)
            .padding(.top, top + 10)
            .allowsHitTesting(false)

            VStack(alignment: .leading, spacing: 4) {
                Text("Low Tide Lanterns")
                    .font(.system(size: 28, weight: .semibold))
                    .kerning(-0.4)
                    .foregroundStyle(Art.rgb(0xF3F1FA))
                Text("Iva North")
                    .font(.system(size: 17, weight: .regular))
                    .foregroundStyle(Art.rgb(0xEEEAF8, 0.6))
            }
            .lineLimit(1)
            .frame(width: max(0, size.width - 96), alignment: .leading)
            .padding(.leading, 24)
            .padding(.top, g.titleTop)
            .allowsHitTesting(false)

            // The play button's touch area (the button itself is drawn in the dynamic Canvas).
            Circle()
                .fill(Color.clear)
                .contentShape(Circle().inset(by: -8))
                .frame(width: Art.playR * 2, height: Art.playR * 2)
                .padding(.leading, g.w / 2 - Art.playR)
                .padding(.top, g.controlsY - Art.playR)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { _ in
                            awake = true
                            engine.pressIn()
                        }
                        .onEnded { v in
                            let dx = v.location.x - Art.playR
                            let dy = v.location.y - Art.playR
                            let inside = dx * dx + dy * dy <= (Art.playR + 8) * (Art.playR + 8)
                            engine.pressOut()
                            if inside { engine.toggle(auto: auto, reduced: reduced) }
                        }
                )
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Play or pause")
                .accessibilityAction {
                    awake = true
                    engine.toggle(auto: auto, reduced: reduced)
                }

            VStack(alignment: .leading, spacing: 2) {
                Text("UP NEXT")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(Art.rgb(0xEEEAF8, 0.45))
                Text("Paper Harbour · Iva North")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Art.rgb(0xEEEAF8, 0.8))
                    .lineLimit(1)
            }
            .frame(width: max(0, size.width - 156), alignment: .leading)
            .padding(.leading, 84)
            .padding(.bottom, bottom + 23)
            .frame(width: size.width, height: size.height, alignment: .bottomLeading)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

/// What one frame shows.
struct PrismSpectrumPlayerFrame {
    var pose: PrismSpectrumPlayerPose
    var press: Double
    var ambient: Double
    var idle: Bool
}

/// The clock and the hand-driven state, stepped once a frame like the RN frame callback.
@MainActor
final class PrismSpectrumPlayerEngine {
    private typealias M = PrismSpectrumPlayerMotion
    /// Radians of prism turn per point of horizontal drag.
    private static let dragGain = 0.0042
    /// The fan's spring (stiffness, damping) under the finger.
    private static let fanK = 420.0
    private static let fanC = 2 * 0.72 * sqrt(fanK)
    private static let returnTime = 0.8

    private var prev: Double?
    private var now = 0.0
    private var reduced = false
    /// The loop runs from `start` (nil while a finger is in charge); `origin` drives the ambient
    /// motion (bars, motes, glints), which never restarts.
    private var start: Double?
    private var origin = 0.0
    private var resumeAt: Double?

    private let rest = M.pose(0)
    private var rot: PrismSpectrumPlayerChannel
    private var play: PrismSpectrumPlayerChannel
    private var beam: PrismSpectrumPlayerChannel
    private var icon: PrismSpectrumPlayerChannel
    private var elapsed: PrismSpectrumPlayerChannel
    private var press = PrismSpectrumPlayerChannel(0)
    private var fan: Double
    private var fanV = 0.0
    private var playing = false
    private var dragging = false
    private var grab = 0.0
    private var pastLimit = false

    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    init() {
        let r = PrismSpectrumPlayerMotion.pose(0)
        rot = PrismSpectrumPlayerChannel(r.rot)
        play = PrismSpectrumPlayerChannel(r.play)
        beam = PrismSpectrumPlayerChannel(r.beam)
        icon = PrismSpectrumPlayerChannel(r.icon)
        elapsed = PrismSpectrumPlayerChannel(r.elapsed)
        fan = r.fan
    }

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    func step(_ n: Double, auto: Bool, reduced: Bool) -> PrismSpectrumPlayerFrame {
        self.reduced = reduced
        let first = prev == nil
        if first {
            origin = n
            if auto { start = n }
        }
        let dt = first ? 0 : min(max(0, n - (prev ?? n)), 1.0 / 30)
        prev = n
        now = n
        if !reduced {
            if start == nil || n < (start ?? n) {
                // The fan follows the prism on a spring; a playing track advances.
                let a = Self.fanK * (rot.value(at: n) - fan) - Self.fanC * fanV
                fanV += a * dt
                fan += fanV * dt
                if playing { elapsed.set(elapsed.value(at: n) + dt) }
            }
            // 2 s after the last touch: ease back to rest, then run the loop from t = 0.
            if let r = resumeAt, n >= r {
                resumeAt = nil
                playing = false
                rot.timing(to: rest.rot, duration: Self.returnTime, at: n, curve: M.easeInOut)
                play.timing(to: rest.play, duration: Self.returnTime, at: n, curve: M.easeInOut)
                beam.timing(to: rest.beam, duration: Self.returnTime, at: n, curve: M.easeInOut)
                icon.timing(to: rest.icon, duration: 0.3, at: n, curve: M.easeInOut)
                elapsed.timing(to: rest.elapsed, duration: Self.returnTime, at: n, curve: M.easeInOut)
                start = n + Self.returnTime + 0.05
            }
        }

        let pose: PrismSpectrumPlayerPose
        let pressAmount: Double
        if let s = start, n >= s {
            pose = M.pose((n - s).truncatingRemainder(dividingBy: M.loop))
            pressAmount = pose.pressPlay
        } else {
            let r = rot.value(at: n)
            pose = PrismSpectrumPlayerPose(
                rot: r,
                fan: reduced ? r : fan,
                play: play.value(at: n),
                beam: beam.value(at: n),
                icon: icon.value(at: n),
                elapsed: elapsed.value(at: n),
                pressPlay: 0,
                pressBack: 0
            )
            pressAmount = press.value(at: n)
        }
        let ambient = reduced ? 0 : (n - origin).truncatingRemainder(dividingBy: M.loop)

        let moving = rot.isMoving(at: n) || play.isMoving(at: n) || beam.isMoving(at: n)
            || icon.isMoving(at: n) || elapsed.isMoving(at: n) || press.isMoving(at: n)
        let fanSettled = reduced || (abs(fan - rot.value(at: n)) < 1e-4 && abs(fanV) < 1e-4)
        let idle = !moving && fanSettled && !dragging && resumeAt == nil && start == nil && (!playing || reduced)
        return PrismSpectrumPlayerFrame(pose: pose, press: pressAmount, ambient: ambient, idle: idle)
    }

    /// Hands the loop's current pose to the hand-driven state.
    private func takeOver(_ n: Double) {
        if let s = start, n >= s {
            let p = M.pose((n - s).truncatingRemainder(dividingBy: M.loop))
            rot.set(p.rot)
            fan = p.fan
            fanV = 0
            play.set(p.play)
            beam.set(p.beam)
            icon.set(p.icon)
            elapsed.set(p.elapsed)
            playing = p.play > 0.5
        } else if start != nil {
            // Mid-return: keep where the easing got to.
            rot.set(rot.value(at: n))
            play.set(play.value(at: n))
            beam.set(beam.value(at: n))
            elapsed.set(elapsed.value(at: n))
        }
        start = nil
        resumeAt = nil
    }

    private func released(auto: Bool) {
        if auto { resumeAt = clock + 2 }
    }

    func drag(_ dx: Double, reduced: Bool) {
        let n = clock
        if !dragging {
            dragging = true
            takeOver(n)
            grab = rot.value(at: n)
            pastLimit = false
            tick.prepare()
        }
        let raw = grab + dx * Self.dragGain
        rot.set(M.rubberBand(raw, M.rotLimit))
        let over = abs(raw) > M.rotLimit
        if over != pastLimit {
            pastLimit = over
            if over { tick.selectionChanged() }
        }
        if reduced { fan = rot.value(at: n) }
    }

    func dragEnded(velocity: Double, auto: Bool) {
        let n = clock
        dragging = false
        // Momentum: aim a little past where the finger let go, inside the limits.
        let v = velocity * Self.dragGain
        let target = max(-M.rotLimit, min(M.rotLimit, rot.value(at: n) + v * 0.12))
        rot.spring(to: target, duration: 0.45, dampingRatio: 0.8, velocity: v, at: n)
        if reduced { fan = target }
        released(auto: auto)
    }

    func toggle(auto: Bool, reduced: Bool) {
        light.impactOccurred()
        let n = clock
        takeOver(n)
        playing.toggle()
        let next = playing ? 1.0 : 0.0
        let fade = reduced ? 0.3 : 0.6
        play.timing(to: next, duration: fade, at: n, curve: M.easeInOut)
        beam.timing(to: playing ? 1 : M.restBeam, duration: fade, at: n, curve: M.easeInOut)
        icon.timing(to: next, duration: 0.25, at: n, curve: M.easeInOut)
        released(auto: auto)
    }

    func pressIn() {
        let n = clock
        if press.target != 1 {
            light.prepare()
            press.timing(to: 1, duration: 0.11, at: n, curve: M.easeOutStrong)
        }
    }

    func pressOut() {
        press.spring(to: 0, duration: 0.35, dampingRatio: 0.6, velocity: 0, at: clock)
    }
}
