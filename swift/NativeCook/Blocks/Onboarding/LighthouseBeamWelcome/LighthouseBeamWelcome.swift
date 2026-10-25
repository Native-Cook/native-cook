import SwiftUI
import UIKit

/*
 Lighthouse Beam Welcome — the onboarding of "Beacon", a navigation and safety app. Night sea at
 dusk, a lighthouse on its rocks, and onboarding text hidden in the fog that shows only where the
 turning beam falls (with a 0.8 s afterglow).

 Touch: press and drag anywhere above the controls and the beam turns to point at the finger
 (azimuth solved from the finger's position, followed by a spring, ζ 0.85); a quick horizontal
 swipe (> 650 pt/s) changes page (0.6 s, ease-in-out cubic). "Get started" goes to the next page.
 2 s after the last touch the lantern settles back across the text and the page returns to the
 first (0.8 s), then the loop runs from t = 0. Reduced motion: no loop and no turning; the beam
 rests on the text and pages cross over 300 ms.

 The scene is one Metal layer effect over the pages' text (an image drawn once and slid per page);
 the tower is a Canvas drawn once; the lamp and the dots are a small Canvas per frame.
 */
struct LighthouseBeamWelcome: View {
    /// Two full turns of the lantern, with a page change behind each, on a 10 s loop.
    var autoPlay = false

    static let loop = LighthouseBeamWelcomeMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = LighthouseBeamWelcomeEngine()
    /// Keeps the timeline running outside autoPlay while something moves.
    @State private var awake = false

    private typealias Art = LighthouseBeamWelcomeArt

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
        let g = LighthouseBeamWelcomeGeo(w: size.width, h: size.height, top: top, bottom: bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let running = auto || awake
        let content = Art.content(g, scale: min(3, max(1, displayScale)))
        let dotsY = g.h - bottom - 16 - 56 - 30
        return ZStack(alignment: .topLeading) {
            Art.background

            TimelineView(.animation(paused: !running)) { timeline in
                let n = timeline.date.timeIntervalSinceReferenceDate
                let frame = engine.step(n, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    Canvas { c, _ in
                        // A full-screen fill at the lowest alpha a pixel can hold, so the layer (and
                        // with it the shader) covers the whole screen and not only the text's bounds.
                        // The shader reads alpha above it as ink.
                        c.fill(Path(CGRect(x: 0, y: 0, width: g.w, height: g.h)), with: .color(Color.black.opacity(1.0 / 255)))
                        c.draw(content, in: CGRect(x: -frame.page * g.w, y: g.contentY, width: g.w * 3, height: g.contentH))
                    }
                    .frame(width: size.width, height: size.height)
                    .layerEffect(
                        ShaderLibrary.lighthouseBeamWelcome(
                            .float2(max(1, g.w), max(1, g.h)),
                            .float(g.u),
                            .float2(g.lampX, g.lampY),
                            .float(g.horizon),
                            .float4(frame.phi[0], frame.phi[1], frame.phi[2], frame.phi[3]),
                            .float2(frame.phi[4], frame.phi[5]),
                            .float2(frame.ambient, LighthouseBeamWelcomeMotion.loop)
                        ),
                        maxSampleOffset: .zero
                    )

                    Canvas { c, _ in
                        Art.drawTower(c, g)
                    }
                    .frame(width: size.width, height: size.height)

                    Canvas { c, _ in
                        Art.drawLamp(c, g, flare: LighthouseBeamWelcomeMotion.facing(frame.phi[0]))
                        Art.drawDots(c, cx: g.w / 2, y: dotsY, page: frame.page)
                    }
                    .frame(width: size.width, height: size.height)
                }
                .onChange(of: frame.idle, initial: true) { _, idle in
                    if idle && !auto { awake = false }
                }
            }
            .allowsHitTesting(false)

            // Press and drag above the controls to aim the beam; flick sideways to change page.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: max(1, dotsY - 16))
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            engine.aim(v.location, g: g, reduced: reduced)
                        }
                        .onEnded { v in
                            engine.aimEnded(velocityX: v.velocity.width, translationX: v.translation.width, auto: auto, reduced: reduced)
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("A lighthouse beam reveals the app's features. Drag to aim the beam, swipe to change page.")

            HStack(alignment: .center) {
                Text("BEACON")
                    .font(.system(size: 13, weight: .bold))
                    .kerning(3.2)
                    .foregroundStyle(Art.cream)
                Spacer(minLength: 0)
                Text("Skip")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Art.cream.opacity(0.62))
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: max(0, size.width - 48))
            .padding(.leading, 24)
            .padding(.top, top + 12)
            .allowsHitTesting(false)

            Button {
                awake = true
                engine.next(auto: auto, reduced: reduced)
            } label: {
                Text("Get started")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Art.rgb(0x1B1E3D))
                    .frame(width: max(0, size.width - 48), height: 56)
            }
            .buttonStyle(LighthouseBeamWelcomeButtonStyle())
            .accessibilityLabel("Get started")
            .padding(.leading, 24)
            .padding(.bottom, bottom + 16)
            .frame(width: size.width, height: size.height, alignment: .bottomLeading)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

/// The amber pill: a three-stop face, two shadows, a top inner highlight, a 0.97 press.
private struct LighthouseBeamWelcomeButtonStyle: ButtonStyle {
    private typealias Art = LighthouseBeamWelcomeArt

    func makeBody(configuration: Configuration) -> some View {
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        configuration.label
            .background {
                shape
                    .fill(LinearGradient(
                        stops: [
                            .init(color: Art.rgb(0xFBE2A8), location: 0),
                            .init(color: Art.rgb(0xF3CF86), location: 0.55),
                            .init(color: Art.rgb(0xE5B566), location: 1),
                        ],
                        startPoint: .top,
                        endPoint: .bottom
                    ))
                    .overlay {
                        // inset 0 1px 0 rgba(255,255,255,0.55)
                        shape
                            .inset(by: 0.5)
                            .stroke(
                                LinearGradient(
                                    stops: [
                                        .init(color: Color.white.opacity(0.55), location: 0),
                                        .init(color: Color.white.opacity(0), location: 0.06),
                                    ],
                                    startPoint: .top,
                                    endPoint: .bottom
                                ),
                                lineWidth: 1
                            )
                    }
                    .shadow(color: Art.rgb(0xF3C878, 0.22), radius: 15, x: 0, y: 12)
                    .shadow(color: Art.rgb(0x050819, 0.35), radius: 1, x: 0, y: 1)
            }
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(
                configuration.isPressed ? .easeInOut(duration: 0.11) : .spring(duration: 0.35, bounce: 0.3),
                value: configuration.isPressed
            )
    }
}

/// What one frame shows.
struct LighthouseBeamWelcomeFrame {
    /// φ now and 0.1, 0.22, 0.36, 0.55, 0.8 s ago.
    var phi: [Double]
    var page: Double
    var ambient: Double
    var idle: Bool
}

/// The clock and the hand-driven state, stepped once a frame like the RN frame callback.
@MainActor
final class LighthouseBeamWelcomeEngine {
    private typealias M = LighthouseBeamWelcomeMotion
    /// The lantern's spring under the finger.
    private static let aimK = 140.0
    private static let aimC = 2 * 0.85 * sqrt(aimK)
    private static let returnTime = 0.8

    private var prev: Double?
    private var start: Double?
    private var origin = 0.0
    private var resumeAt: Double?
    // Hand-driven state: the lantern's azimuth (unwrapped), its spring, the trail, the page.
    private var phi = 0.0
    private var phiV = 0.0
    private var target = 0.0
    private var trail: [Double] = [0, 0, 0, 0, 0]
    private var page = LighthouseBeamWelcomePage(0)
    private var touching = false
    private var startX = 0.0

    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)

    private var clock: Double { Date().timeIntervalSinceReferenceDate }

    func step(_ n: Double, auto: Bool, reduced: Bool) -> LighthouseBeamWelcomeFrame {
        let first = prev == nil
        if first {
            origin = n
            if auto { start = n }
        }
        let dt = first ? 0 : min(max(0, n - (prev ?? n)), 1.0 / 30)
        prev = n
        if !reduced {
            if start == nil || n < (start ?? n) {
                let a = Self.aimK * (target - phi) - Self.aimC * phiV
                phiV += a * dt
                phi += phiV * dt
                // The afterglow follows the beam through low-pass copies with the trail's delays.
                for i in trail.indices {
                    trail[i] += (phi - trail[i]) * (1 - exp(-dt / M.trail[i]))
                }
            }
            if let r = resumeAt, n >= r {
                resumeAt = nil
                target = (phi / (2 * Double.pi)).rounded() * 2 * Double.pi
                page.timing(to: 0, duration: Self.returnTime, at: n)
                start = n + Self.returnTime + 0.1
            }
        }

        var phis: [Double]
        var shown: Double
        if let s = start, n >= s {
            let t = (n - s).truncatingRemainder(dividingBy: M.loop)
            phis = [M.phiAt(t)] + M.trail.map { M.phiBefore(t, $0) }
            shown = M.pageAt(t)
        } else {
            phis = [phi] + trail
            shown = page.value(at: n)
        }
        let ambient = reduced ? 0 : (n - origin).truncatingRemainder(dividingBy: M.loop)
        let settled = reduced || (abs(target - phi) < 1e-4 && abs(phiV) < 1e-4 && trail.allSatisfy { abs($0 - phi) < 1e-4 })
        let idle = settled && !page.isMoving(at: n) && !touching && resumeAt == nil && start == nil
        if phis.count < 6 { phis += Array(repeating: phi, count: 6 - phis.count) }
        shown = max(0, min(Double(M.pages - 1), shown))
        return LighthouseBeamWelcomeFrame(phi: phis, page: shown, ambient: ambient, idle: idle)
    }

    private func takeOver(_ n: Double) {
        if let s = start, n >= s {
            let t = (n - s).truncatingRemainder(dividingBy: M.loop)
            let p = M.phiAt(t)
            phi = p
            phiV = 0
            target = p
            trail = M.trail.map { M.phiBefore(t, $0) }
            page.set(M.pageAt(t))
        } else if start != nil {
            page.set(page.value(at: n))
        }
        start = nil
        resumeAt = nil
    }

    private func released(auto: Bool) {
        if auto { resumeAt = clock + 2 }
    }

    private func goTo(_ index: Int, reduced: Bool) {
        let n = clock
        let next = max(0, min(M.pages - 1, index))
        if next == Int(page.value(at: n).rounded()) { return }
        page.timing(to: Double(next), duration: reduced ? 0.3 : 0.6, at: n)
        tick.selectionChanged()
    }

    func aim(_ point: CGPoint, g: LighthouseBeamWelcomeGeo, reduced: Bool) {
        if !touching {
            touching = true
            startX = point.x
            takeOver(clock)
            target = M.aimAt(point.x - g.lampX, point.y - g.lampY, near: phi)
            tick.prepare()
        } else {
            target = M.aimAt(point.x - g.lampX, point.y - g.lampY, near: target)
        }
        if reduced {
            phi = target
            phiV = 0
            trail = Array(repeating: phi, count: 5)
        }
    }

    func aimEnded(velocityX: Double, translationX: Double, auto: Bool, reduced: Bool) {
        touching = false
        if abs(velocityX) > 650 && abs(translationX) > 40 {
            let current = Int(page.value(at: clock).rounded())
            goTo(current + (velocityX < 0 ? 1 : -1), reduced: reduced)
        }
        released(auto: auto)
    }

    func next(auto: Bool, reduced: Bool) {
        light.impactOccurred()
        takeOver(clock)
        goTo(Int(page.value(at: clock).rounded()) + 1, reduced: reduced)
        released(auto: auto)
    }
}

/// The page as an eased value (Reanimated's withTiming), a pure function of time once started.
struct LighthouseBeamWelcomePage {
    private var from: Double
    private var to: Double
    private var t0 = 0.0
    private var duration = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at n: Double) -> Double {
        if duration <= 0 { return to }
        let x = min(1, max(0, (n - t0) / duration))
        return from + (to - from) * LighthouseBeamWelcomeMotion.easeInOut(x)
    }

    func isMoving(at n: Double) -> Bool { duration > 0 && n < t0 + duration }

    mutating func set(_ value: Double) {
        from = value
        to = value
        duration = 0
    }

    mutating func timing(to goal: Double, duration d: Double, at n: Double) {
        from = value(at: n)
        to = goal
        t0 = n
        duration = d
    }
}
