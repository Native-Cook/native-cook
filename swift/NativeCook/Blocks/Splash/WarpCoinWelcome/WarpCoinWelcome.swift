import SwiftUI
import UIKit

/// The welcome screen of Arcwell, an invented bank. A slow star field drifts out of a vanishing
/// point behind a large brushed-silver coin; the warp spins up, the coin dives into it, comes back
/// out of the warp toward the viewer, swoops close and lands with a settle while the streaks slow
/// back down to stars. 300 seeded particles in a tunnel are drawn as lines from their previous to
/// their current projected position, so speed is length; the coin is a thick disc with a reeded rim,
/// a concentric brushed face whose highlight follows the light and an embossed mark.
///
/// Drag up to warp faster (down drifts back); the coin tilts with the drag. Tap the coin to spin it
/// once. 2 s after the last touch the loop starts again from rest. Reduced motion: the rest pose, still.
struct WarpCoinWelcome: View {
    static let loop = 7.0
    static let idle = 2.0

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = WarpCoinWelcomeTouch()
    @State private var awake = false

    var body: some View {
        ZStack {
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let height = geometry.size.height / k
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                    let state = frame(timeline.date)
                    Canvas { context, _ in
                        context.scaleBy(x: k, y: k)
                        WarpCoinWelcomeArt.draw(context, height: height, state)
                    }
                }
                .contentShape(Rectangle())
                .gesture(drag(k: k, height: height))
            }
            .ignoresSafeArea()
            WarpCoinWelcomeChrome()
        }
        .background(WarpCoinWelcomeArt.color(0x05060B).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: awake) {
            // Outside autoPlay, run the clock only until the scene is back at rest after the last touch.
            guard awake, !autoPlay else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, let release = touch.release, clock > release + Self.idle + 1.2 {
                    awake = false
                    return
                }
            }
        }
        .accessibilityElement(children: .combine)
        .accessibilityLabel("Arcwell. Banking at the speed of light.")
    }

    // MARK: Time

    private func frame(_ now: Date) -> WarpCoinWelcomeFrame {
        typealias M = WarpCoinWelcomeMotion
        if reducedMotion { return M.restFrame() }
        let clock = max(0, now.timeIntervalSince(epoch))
        return M.frame(clock: clock, touch: touch, autoPlay: autoPlay)
    }

    // MARK: Touch

    private func drag(k: CGFloat, height: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reducedMotion else { return }
                let clock = Date().timeIntervalSince(epoch)
                let point = CGPoint(x: value.location.x / k, y: value.location.y / k)
                if !touch.touching {
                    touch.begin(at: point, clock: clock)
                    if !awake { awake = true }
                }
                if touch.move(to: point) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.8)
                }
            }
            .onEnded { _ in
                guard !reducedMotion, touch.touching else { return }
                let clock = Date().timeIntervalSince(epoch)
                if touch.end(clock: clock, coinY: WarpCoinWelcomeMotion.vanishY(height) + 162) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                } else {
                    UIImpactFeedbackGenerator(style: .soft).impactOccurred(intensity: 0.5)
                }
            }
    }
}

// MARK: - Chrome

/// The static product screen over the scene: page indicator, header, headline and the two buttons.
struct WarpCoinWelcomeChrome: View {
    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 5) {
                ForEach(0..<5, id: \.self) { i in
                    Capsule().fill(Color.white.opacity(i == 0 ? 0.9 : 0.22)).frame(height: 2)
                }
            }
            .padding(.horizontal, 12)
            .padding(.top, 10)
            HStack(spacing: 8) {
                Canvas { context, size in
                    WarpCoinWelcomeArt.drawLogo(context, center: CGPoint(x: size.width / 2, y: size.height / 2), size: 18)
                }
                .frame(width: 18, height: 18)
                Text("Welcome to Arcwell")
                    .font(.system(size: 15))
                    .foregroundStyle(Color.white)
            }
            .frame(height: 20)
            .padding(.leading, 16)
            .padding(.top, 10)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(["BANKING AT", "THE SPEED", "OF LIGHT"], id: \.self) { line in
                    Text(line)
                        .font(.system(size: 34, weight: .black))
                        .foregroundStyle(Color.white)
                        .frame(height: 36, alignment: .leading)
                }
            }
            .padding(.leading, 24)
            .padding(.top, 14)
            .accessibilityElement(children: .combine)
            Spacer(minLength: 0)
            HStack(spacing: 8) {
                button("Log in", fill: WarpCoinWelcomeArt.color(0x1E2127), ink: .white)
                button("Sign up", fill: WarpCoinWelcomeArt.color(0xF4F5F7), ink: WarpCoinWelcomeArt.color(0x0A0B0E))
            }
            .padding(.horizontal, 16)
            .padding(.bottom, 16)
        }
    }

    private func button(_ title: String, fill: Color, ink: Color) -> some View {
        Button {} label: {
            Text(title)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(ink)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Capsule().fill(fill))
        }
        .buttonStyle(.plain)
    }
}

// MARK: - Touch

/// Touch state read by the timeline; a plain class, so writing it never re-renders the view.
final class WarpCoinWelcomeTouch {
    var touching = false
    var start = 0.0
    var engageFrom = 0.0
    var release: Double?
    /// Loop time = clock - base (wrapped).
    var base = 0.0
    /// Loop time held while the finger drives the scene.
    var heldStory = 0.0
    var dragX = 0.0, dragY = 0.0
    var releaseX = 0.0, releaseY = 0.0
    var spinStart = -10.0
    /// Travel bookkeeping: the last frame's travel, and the offset added to the loop's travel after a resume.
    var lastTravel = 0.0
    var lastClock = -1.0
    var lastStory = 0.0
    var travelOffset = 0.0
    var resumeApplied = true
    var lastSpeed = WarpCoinWelcomeMotion.restSpeed
    private var anchor = CGPoint.zero
    private var downPoint = CGPoint.zero
    private var moved = 0.0
    private var full = false

    /// The travel the last frame's speed adds up to now, so a resume carries on without a jump.
    func lastSpeedStep(_ clock: Double) -> Double {
        lastClock < 0 ? 0 : lastSpeed * min(0.1, max(0, clock - lastClock))
    }

    /// 0 → 1 blend from the loop's pose to the finger's control pose.
    func engage(_ clock: Double) -> Double {
        if touching { return engageFrom + (1 - engageFrom) * WarpCoinWelcomeMotion.easeOut((clock - start) / 0.45) }
        guard let release else { return 0 }
        let resume = release + WarpCoinWelcome.idle
        if clock < resume {
            return engageFrom + (1 - engageFrom) * WarpCoinWelcomeMotion.easeOut((clock - start) / 0.45)
        }
        return 1 - WarpCoinWelcomeMotion.easeOut((clock - resume) / 0.45)
    }

    /// The drag the control pose follows: live while touching, then a critically damped return to zero.
    func drag(_ clock: Double) -> (Double, Double) {
        if touching { return (dragX, dragY) }
        guard let release else { return (0, 0) }
        let age = max(0, clock - release), tau = 0.16
        let k = (1 + age / tau) * exp(-age / tau)
        return (releaseX * k, releaseY * k)
    }

    func begin(at point: CGPoint, clock: Double) {
        let current = engage(clock)
        let held = touching || (release.map { clock < $0 + WarpCoinWelcome.idle } ?? false)
        if !held { heldStory = lastStory }
        engageFrom = current
        touching = true
        start = clock
        release = nil
        resumeApplied = false
        anchor = point
        downPoint = point
        moved = 0
        dragX = 0
        dragY = 0
        full = false
    }

    /// Returns true when the drag passes full warp, for a haptic.
    func move(to point: CGPoint) -> Bool {
        dragX = point.x - anchor.x
        dragY = point.y - anchor.y
        moved = max(moved, hypot(point.x - downPoint.x, point.y - downPoint.y))
        let c = WarpCoinWelcomeMotion.control(dragY)
        if c > 0.9, !full { full = true; return true }
        if c < 0.7 { full = false }
        return false
    }

    /// Returns true when the touch was a tap on the coin (it spins).
    func end(clock: Double, coinY: Double) -> Bool {
        touching = false
        release = clock
        releaseX = dragX
        releaseY = dragY
        let tap = moved < 10 && clock - start < 0.35
            && abs(downPoint.x - 196.5) < 150 && abs(downPoint.y - coinY) < 95
        if tap, clock - spinStart > 1.0 {
            spinStart = clock
            return true
        }
        return false
    }
}

// MARK: - Motion

struct WarpCoinWelcomePose {
    /// ln of the depth (1 at rest), world offset from the vanishing point at depth 1, angles in radians.
    var lnZ = 0.0, x = 0.0, y = 162.0, pitch = 1.13, roll = 0.0, twist = 0.0, spin = 0.0, hop = 0.0

    func mixed(_ o: Self, _ k: Double) -> Self {
        func m(_ a: Double, _ b: Double) -> Double { a + (b - a) * k }
        return Self(lnZ: m(lnZ, o.lnZ), x: m(x, o.x), y: m(y, o.y), pitch: m(pitch, o.pitch), roll: m(roll, o.roll),
                    twist: m(twist, o.twist), spin: m(spin, o.spin), hop: m(hop, o.hop))
    }
}

struct WarpCoinWelcomeFrame {
    /// Continuous loop phase for twinkle and hover (seconds, 0 ..< loop).
    var phase = 0.0
    /// Distance travelled through the tunnel (depth units; only its fraction matters).
    var travel = 0.0
    /// Signed speed in depth units per second (0.125 at rest).
    var speed = WarpCoinWelcomeMotion.restSpeed
    var pose = WarpCoinWelcomePose()
    var glint = 0.0
    var vanishShift = 0.0
    var hover = 1.0

    /// 0 at rest speed, 1 at full warp.
    var warp: Double { min(1, max(0, (abs(speed) - WarpCoinWelcomeMotion.restSpeed) / WarpCoinWelcomeMotion.boost)) }
}

/// The loop's timeline as pure functions of the loop time.
enum WarpCoinWelcomeMotion {
    /// One tunnel length per loop at rest.
    static let restSpeed = 1.0 / 7.0
    /// Extra speed at full warp: 9 extra tunnel lengths over the loop, spread by the warp curve (∫w = 3.45 s).
    static let boost = 9.0 / 3.45

    static func vanishY(_ height: Double) -> Double { 0.45 * height }

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeOut(_ x: Double) -> Double { let u = clamp01(x); return 1 - pow(1 - u, 3) }
    static func easeIn(_ x: Double) -> Double { let u = clamp01(x); return u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        let u = clamp01(x); return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func smooth(_ x: Double) -> Double { let u = clamp01(x); return u * u * (3 - 2 * u) }
    static func smoother(_ x: Double) -> Double { let u = clamp01(x); return u * u * u * (u * (u * 6 - 15) + 10) }
    static func wrap(_ t: Double) -> Double {
        let r = t.truncatingRemainder(dividingBy: WarpCoinWelcome.loop)
        return r < 0 ? r + WarpCoinWelcome.loop : r
    }

    /// Warp level: smoothstep up over 0.8–2.0 s, full until 3.9 s, smoothstep down over 3.9–5.8 s.
    static func warp(_ t: Double) -> Double {
        if t < 0.8 || t >= 5.8 { return 0 }
        if t < 2.0 { return smooth((t - 0.8) / 1.2) }
        if t < 3.9 { return 1 }
        return 1 - smooth((t - 3.9) / 1.9)
    }

    /// ∫₀ᵗ warp: the closed form of the smoothstep's integral is x³ − x⁴/2.
    static func warpArea(_ t: Double) -> Double {
        func f(_ x: Double) -> Double { x * x * x - x * x * x * x / 2 }
        if t < 0.8 { return 0 }
        if t < 2.0 { return 1.2 * f((t - 0.8) / 1.2) }
        if t < 3.9 { return 0.6 + (t - 2.0) }
        if t < 5.8 { let x = (t - 3.9) / 1.9; return 2.5 + 1.9 * (x - f(x)) }
        return 3.45
    }

    /// Travel over one loop is exactly 10 tunnel lengths, so every particle is back where it started.
    static func travel(_ t: Double) -> Double { restSpeed * t + boost * warpArea(t) }
    static func speed(_ t: Double) -> Double { restSpeed + boost * warp(t) }

    /// Step response of a spring (damping ratio 0.62, period 1.0 s) from 1 to 0 with zero start velocity,
    /// forced to exactly 0 after 1.8 s so the loop closes.
    static func settle(_ tau: Double) -> Double {
        guard tau > 0 else { return 1 }
        let zeta = 0.62, wn = 2 * Double.pi / 1.0
        let wd = wn * sqrt(1 - zeta * zeta), decay = zeta * wn
        let v = exp(-decay * tau) * (cos(wd * tau) + decay / wd * sin(wd * tau))
        return v * (1 - smooth((tau - 1.4) / 0.4))
    }

    static func pose(_ t: Double) -> WarpCoinWelcomePose {
        var p = WarpCoinWelcomePose()
        let far = log(9.0), dip = log(0.93), close = log(0.62)
        if t < 2.65 {
            let a = easeOut(seg(t, 0.85, 1.15))
            let f = easeIn(seg(t, 1.15, 2.25))
            let h = easeInOut(seg(t, 2.25, 2.65))
            p.lnZ = dip * a + (far - dip) * f
            p.pitch = 1.13 - 0.12 * a + 0.10 * f
            p.roll = 0.25 * f
            p.twist = -0.9 * f + 1.8 * h
        } else if t < 3.6 {
            let r = smoother(seg(t, 2.65, 3.6))
            p.lnZ = far + (close - far) * r
            p.x = -50 * r
            p.y = 162 - 52 * r
            p.pitch = 1.11 + 0.15 * r
            p.roll = 0.25 - 0.53 * r
            p.twist = 0.9 - 0.55 * r
        } else {
            let s = settle(t - 3.6)
            p.lnZ = close * s
            p.x = -50 * s
            p.y = 162 - 52 * s
            p.pitch = 1.13 + 0.13 * s
            p.roll = -0.28 * s
            p.twist = 0.35 * s
        }
        return p
    }

    static func glint(_ t: Double) -> Double {
        guard t > 2.05, t < 2.95 else { return 0 }
        let s = sin(Double.pi * (t - 2.05) / 0.9); return s * s
    }

    /// Warp control from the vertical drag: up is faster (1 = full warp), down drifts back (to −0.5).
    static func control(_ dragY: Double) -> Double {
        let c = -dragY / 240
        if c > 1 { let over = c - 1; return 1 + over / (1 + over * 2) * 0.5 }
        return max(-0.5, c)
    }

    static func controlPose(_ dx: Double, _ dy: Double) -> (pose: WarpCoinWelcomePose, speed: Double) {
        let c = control(dy)
        var p = WarpCoinWelcomePose()
        p.lnZ = log(1 + 0.25 * max(c, 0))
        p.x = dx * 0.15
        p.pitch = 1.13 - 0.22 * c
        p.roll = min(max(dx / 300, -1), 1) * 0.35
        let speed = c >= 0 ? restSpeed + boost * c : restSpeed + 0.8 * c
        return (p, speed)
    }

    static func spin(_ clock: Double, _ start: Double) -> (Double, Double) {
        let u = (clock - start) / 1.0
        guard u > 0, u < 1 else { return (0, 0) }
        return (2 * Double.pi * easeInOut(u), 14 * sin(Double.pi * u))
    }

    static func restFrame() -> WarpCoinWelcomeFrame {
        var f = WarpCoinWelcomeFrame()
        f.hover = 0
        return f
    }

    /// One frame from the clock (seconds since the view appeared) and the touch state.
    static func frame(clock: Double, touch: WarpCoinWelcomeTouch, autoPlay: Bool) -> WarpCoinWelcomeFrame {
        var f = WarpCoinWelcomeFrame()
        f.phase = wrap(clock)
        let engage = touch.engage(clock)
        let (dx, dy) = touch.drag(clock)
        let control = controlPose(dx, dy)
        let held = touch.touching || (touch.release.map { clock < $0 + WarpCoinWelcome.idle } ?? false)

        var story: Double
        if held {
            story = touch.heldStory
        } else {
            if let release = touch.release, !touch.resumeApplied {
                // Resume: the loop restarts from rest, and its travel continues from where the finger left it.
                touch.base = release + WarpCoinWelcome.idle
                touch.resumeApplied = true
                touch.travelOffset = touch.lastTravel + touch.lastSpeedStep(clock) - travel(wrap(clock - touch.base))
            }
            story = autoPlay ? wrap(clock - touch.base) : 0
        }

        var pose = pose(story)
        var speed = speed(story)
        if engage > 0 {
            pose = pose.mixed(control.pose, engage)
            speed = speed + (control.speed - speed) * engage
        }
        let (spin, hop) = spin(clock, touch.spinStart)
        pose.spin += spin
        pose.hop += hop

        let travelNow: Double
        if held {
            let dt = touch.lastClock < 0 ? 0 : min(0.1, max(0, clock - touch.lastClock))
            travelNow = touch.lastTravel + speed * dt
        } else {
            travelNow = travel(story) + touch.travelOffset
        }
        touch.lastTravel = travelNow
        touch.lastClock = clock
        touch.lastStory = story
        touch.lastSpeed = speed

        f.travel = travelNow
        f.speed = speed
        f.pose = pose
        f.glint = glint(story) * (1 - engage)
        f.vanishShift = -dx * 0.12 * engage
        return f
    }
}

// MARK: - Art

/// The tunnel, the coin and the glint, drawn in a 393-wide design space.
enum WarpCoinWelcomeArt {
    static let count = 340
    static let focal = 393.0
    static let radius = 134.0
    static let thickness = 30.0
    static let segments = 120
    /// Direction to the light (upper left, in front) and the half vector with the view direction.
    static let light = normalize((-0.35, -0.85, -0.40))
    static let half = normalize((light.0, light.1, light.2 - 1))

    struct Particle { var angle, r, phase, size: Double; var hex, core: UInt32; var cos, sin: Double }

    static let particles: [Particle] = (0..<count).map { i in
        let fi = Double(i)
        let h1 = hash(fi * 1.37 + 0.11), h2 = hash(fi * 2.71 + 3.3), h3 = hash(fi * 0.913 + 7.7)
        let h4 = hash(fi * 5.17 + 1.9), h5 = hash(fi * 3.33 + 9.1)
        let hex: UInt32 = h4 < 0.40 ? 0x4A66FF : h4 < 0.54 ? 0x8A5CFF : h4 < 0.78 ? 0xE8EEFF
            : h4 < 0.87 ? 0x4FD8FF : h4 < 0.95 ? 0xB4FF5A : 0xFF5AD8
        let angle = 2 * Double.pi * h1
        return Particle(angle: angle, r: 0.05 + 0.70 * h2, phase: h3, size: h5, hex: hex, core: lighten(hex), cos: Foundation.cos(angle), sin: Foundation.sin(angle))
    }

    static let faceStops: [Gradient.Stop] = {
        let half: [(UInt32, Double)] = [(0xF4F5F7, 0), (0xC9CCD2, 0.08), (0x9599A1, 0.18), (0x8A8E96, 0.25),
                                       (0xA3A7AE, 0.32), (0xD2D5DA, 0.42)]
        var stops = half.map { Gradient.Stop(color: color($0.0), location: $0.1) }
            + half.map { Gradient.Stop(color: color($0.0), location: $0.1 + 0.5) }
        stops.append(Gradient.Stop(color: color(0xF4F5F7), location: 1))
        return stops
    }()

    /// The streak's hot core: its colour 35 % of the way to white.
    static func lighten(_ hex: UInt32) -> UInt32 {
        func ch(_ shift: UInt32) -> UInt32 { let v = Double((hex >> shift) & 255); return UInt32((v + (255 - v) * 0.35).rounded()) << shift }
        return ch(16) | ch(8) | ch(0)
    }

    static func hash(_ x: Double) -> Double { let v = Foundation.sin(x * 12.9898) * 43758.5453; return v - floor(v) }

    static func normalize(_ v: (Double, Double, Double)) -> (Double, Double, Double) {
        let l = (v.0 * v.0 + v.1 * v.1 + v.2 * v.2).squareRoot(); return (v.0 / l, v.1 / l, v.2 / l)
    }

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
            .opacity(min(1, max(0, alpha)))
    }

    static func draw(_ context: GraphicsContext, height: Double, _ s: WarpCoinWelcomeFrame) {
        let vp = CGPoint(x: 196.5 + s.vanishShift, y: WarpCoinWelcomeMotion.vanishY(height))
        let warp = s.warp
        let full = CGRect(x: 0, y: 0, width: 393, height: height)

        // Backdrop: near black with a deep blue light at the end of the tunnel that brightens with speed.
        context.fill(Path(full), with: .color(color(0x05060B)))
        context.fill(Path(full), with: .radialGradient(
            Gradient(colors: [color(0x1B2A8F, 0.10 + 0.14 * warp), color(0x1B2A8F, 0)]),
            center: vp, startRadius: 0, endRadius: 260))
        context.fill(Path(full), with: .radialGradient(
            Gradient(colors: [color(0x2B3FD0, 0.03 + 0.05 * warp), color(0x2B3FD0, 0)]),
            center: vp, startRadius: 0, endRadius: 140))

        // Coin placement (weak perspective) and its depth in the tunnel.
        let coin = CoinGeometry(s.pose, vp: vp, hover: s.hover, phase: s.phase)
        let coinDepth = 0.115 * coin.z
        let splits = coinDepth > 0.2

        var glow = context
        glow.blendMode = .plusLighter
        var front: [Int] = []
        let trail = min(0.85, max(0.006, abs(s.speed) * 0.24))
        let forward = s.speed >= 0
        let shift = s.travel - floor(s.travel)
        let twinkleT = 2 * Double.pi * s.phase / WarpCoinWelcome.loop
        for i in 0..<count {
            let p = particles[i]
            var u = p.phase + shift; u -= floor(u)
            let z = 1.04 - u
            if splits, z < coinDepth { front.append(i); continue }
            streak(glow, p, i, z, trail, forward, vp, warp, twinkleT)
        }

        // Scrims keep the headline and the buttons legible over the streaks.
        context.fill(Path(CGRect(x: 0, y: 0, width: 393, height: 300)), with: .linearGradient(
            Gradient(colors: [color(0x05060B, 0.78), color(0x05060B, 0)]), startPoint: .zero, endPoint: CGPoint(x: 0, y: 300)))
        context.fill(Path(CGRect(x: 0, y: height - 230, width: 393, height: 230)), with: .linearGradient(
            Gradient(colors: [color(0x05060B, 0), color(0x05060B, 0.7)]),
            startPoint: CGPoint(x: 0, y: height - 230), endPoint: CGPoint(x: 0, y: height)))

        drawCoin(context, coin, warp: warp)

        for i in front {
            let p = particles[i]
            var u = p.phase + shift; u -= floor(u)
            streak(glow, p, i, 1.04 - u, trail, forward, vp, warp, twinkleT)
        }

        if s.glint > 0.01 {
            let c = coin.center
            let g = s.glint
            glow.fill(Path(ellipseIn: CGRect(x: c.x - 16, y: c.y - 16, width: 32, height: 32)),
                      with: .radialGradient(Gradient(colors: [color(0xFFFFFF, 0.8 * g), color(0x9FB2FF, 0)]),
                                            center: c, startRadius: 0, endRadius: 16))
            glow.fill(Path(ellipseIn: CGRect(x: c.x - 34 * g, y: c.y - 1.2, width: 68 * g, height: 2.4)), with: .color(color(0xFFFFFF, 0.9 * g)))
            glow.fill(Path(ellipseIn: CGRect(x: c.x - 1.2, y: c.y - 22 * g, width: 2.4, height: 44 * g)), with: .color(color(0xFFFFFF, 0.9 * g)))
        }
    }

    private static func streak(_ context: GraphicsContext, _ p: Particle, _ i: Int, _ z: Double, _ trail: Double,
                               _ forward: Bool, _ vp: CGPoint, _ warp: Double, _ twinkleT: Double) {
        let tailZ = forward ? min(1.04, z + trail) : max(0.04, z - trail)
        let reach = focal * p.r
        let rho = reach / z, rhoTail = reach / tailZ
        if min(rho, rhoTail) > 560 { return }
        let fade = smoothFade(z)
        let twinkle = 0.78 + 0.22 * Foundation.sin(twinkleT * Double(3 + i % 4) + 6.28 * p.size)
        let alpha = min(1, (0.35 + 0.65 * p.size) * fade * (0.8 + 0.45 * warp) * twinkle)
        if alpha < 0.01 { return }
        var width = max(0.9, (0.5 + 1.4 * p.size) * (0.4 + 0.6 * (1.04 - z)))
        if p.size > 0.94 { width *= 2.4 }
        let head = CGPoint(x: vp.x + p.cos * rho, y: vp.y + p.sin * rho)
        let tail = CGPoint(x: vp.x + p.cos * rhoTail, y: vp.y + p.sin * rhoTail)
        var path = Path()
        path.move(to: tail)
        path.addLine(to: head)
        let length = abs(rho - rhoTail)
        if length > 3 {
            // A soft halo and a core, both fading toward the tail (the vanishing point side).
            context.stroke(path, with: .linearGradient(Gradient(colors: [color(p.hex, 0), color(p.hex, alpha * 0.16)]),
                                                       startPoint: tail, endPoint: head),
                           style: StrokeStyle(lineWidth: width * 2.6 + 1.2, lineCap: .round))
            context.stroke(path, with: .linearGradient(Gradient(colors: [color(p.hex, 0), color(p.core, alpha)]),
                                                       startPoint: tail, endPoint: head),
                           style: StrokeStyle(lineWidth: width, lineCap: .round))
        } else {
            if p.size > 0.75 {
                let r = width * 2
                context.fill(Path(ellipseIn: CGRect(x: head.x - r, y: head.y - r, width: 2 * r, height: 2 * r)),
                             with: .color(color(p.hex, alpha * 0.2)))
            }
            context.stroke(path, with: .color(color(p.core, alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    /// Particles fade in from the far end of the tunnel (z 1.04 → 0.80).
    private static func smoothFade(_ z: Double) -> Double { WarpCoinWelcomeMotion.smooth((1.04 - z) / 0.24) }

    // MARK: Coin

    struct CoinGeometry {
        var center: CGPoint
        var z: Double
        var scale: Double
        /// Coin axes in camera space: e1 (face x), e2 (face y, down), e3 (face normal, toward the camera at rest).
        var e1: (Double, Double, Double)
        var e2: (Double, Double, Double)
        var e3: (Double, Double, Double)
        var opacity: Double

        init(_ pose: WarpCoinWelcomePose, vp: CGPoint, hover: Double, phase: Double) {
            let w = 2 * Double.pi * phase / WarpCoinWelcome.loop
            let y = pose.y + hover * 3 * Foundation.sin(w)
            let roll = pose.roll + hover * 0.03 * Foundation.sin(w + 1.3)
            let pitch = pose.pitch + hover * 0.02 * Foundation.sin(2 * w)
            z = exp(pose.lnZ)
            scale = 1 / z
            center = CGPoint(x: vp.x + pose.x * scale, y: vp.y + y * scale - pose.hop * scale)
            func rotate(_ v: (Double, Double, Double)) -> (Double, Double, Double) {
                var (x, y, z) = v
                // Twist about the face normal (the z axis before the tilt).
                (x, y) = (x * cos(pose.twist) - y * sin(pose.twist), x * sin(pose.twist) + y * cos(pose.twist))
                // Spin about the face's vertical axis.
                (x, z) = (x * cos(pose.spin) + z * sin(pose.spin), -x * sin(pose.spin) + z * cos(pose.spin))
                // Pitch: the top edge tips away from the camera.
                (y, z) = (y * cos(pitch) + z * sin(pitch), -y * sin(pitch) + z * cos(pitch))
                // Roll about the view axis.
                (x, y) = (x * cos(roll) - y * sin(roll), x * sin(roll) + y * cos(roll))
                return (x, y, z)
            }
            e1 = rotate((1, 0, 0))
            e2 = rotate((0, 1, 0))
            e3 = rotate((0, 0, -1))
            opacity = 1 - 0.75 * WarpCoinWelcomeMotion.smooth((z - 3) / 6)
        }

        func transform(front: Bool) -> CGAffineTransform {
            let k = WarpCoinWelcomeArt.radius * scale, n = WarpCoinWelcomeArt.thickness / 2 * scale
            let side: Double = front ? 1 : -1
            return CGAffineTransform(a: side * e1.0 * k, b: side * e1.1 * k, c: e2.0 * k, d: e2.1 * k,
                                     tx: center.x + side * e3.0 * n, ty: center.y + side * e3.1 * n)
        }
    }

    static func dot(_ a: (Double, Double, Double), _ b: (Double, Double, Double)) -> Double { a.0 * b.0 + a.1 * b.1 + a.2 * b.2 }

    static func drawCoin(_ base: GraphicsContext, _ c: CoinGeometry, warp: Double) {
        var context = base
        context.opacity = c.opacity
        let k = radius * c.scale, n = thickness / 2 * c.scale
        let front = c.e3.2 < 0
        let normal = front ? c.e3 : (-c.e3.0, -c.e3.1, -c.e3.2)

        // Soft ambient shadow under the coin, cut out of the streaks.
        let shadowT = CGAffineTransform(a: c.e1.0 * k * 1.1, b: c.e1.1 * k * 1.1, c: c.e2.0 * k * 1.1, d: c.e2.1 * k * 1.1,
                                        tx: c.center.x - c.e3.0 * n * 2, ty: c.center.y - c.e3.1 * n * 2 + 10 * c.scale)
        let shadow = Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2)).applying(shadowT)
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 22 * c.scale))
            layer.fill(shadow, with: .color(.black.opacity(0.5)))
        }

        // Reeded rim: one quad per facet on the side facing the camera, lit by the light and its specular.
        let ux = c.e1.0 * k, uy = c.e1.1 * k, vx = c.e2.0 * k, vy = c.e2.1 * k
        let nx = c.e3.0 * n, ny = c.e3.1 * n
        let step = 2 * Double.pi / Double(segments)
        for i in 0..<segments {
            let a0 = Double(i) * step, mid = a0 + step / 2
            let radial = (cos(mid) * c.e1.0 + sin(mid) * c.e2.0, cos(mid) * c.e1.1 + sin(mid) * c.e2.1,
                          cos(mid) * c.e1.2 + sin(mid) * c.e2.2)
            if radial.2 > 0.02 { continue }
            let lambert = max(0, dot(radial, light))
            let spec = pow(max(0, dot(radial, half)), 24)
            let ridge = i % 2 == 0 ? 1.0 : 0.74
            let facing = max(0, -radial.2)
            let g = min(1, (0.40 + 0.42 * lambert + 0.22 * facing) * ridge * 0.92 + 0.55 * spec)
            let cool = 0.14 * warp * (1 - lambert)
            let fill = Color(red: g * 0.98 + cool * 0.3, green: g * 0.99 + cool * 0.45, blue: min(1, g * 1.02 + cool))
            let b0 = a0 - step * 0.3, b1 = a0 + step * 1.3
            let p0 = CGPoint(x: c.center.x + cos(b0) * ux + sin(b0) * vx, y: c.center.y + cos(b0) * uy + sin(b0) * vy)
            let p1 = CGPoint(x: c.center.x + cos(b1) * ux + sin(b1) * vx, y: c.center.y + cos(b1) * uy + sin(b1) * vy)
            var quad = Path()
            quad.move(to: CGPoint(x: p0.x + nx, y: p0.y + ny))
            quad.addLine(to: CGPoint(x: p1.x + nx, y: p1.y + ny))
            quad.addLine(to: CGPoint(x: p1.x - nx, y: p1.y - ny))
            quad.addLine(to: CGPoint(x: p0.x - nx, y: p0.y - ny))
            quad.closeSubpath()
            context.fill(quad, with: .color(fill))
        }

        // The face, drawn in its own unit-circle coordinates.
        var face = context
        face.concatenate(c.transform(front: front))
        let side: Double = front ? 1 : -1
        let e1 = (side * c.e1.0, side * c.e1.1, side * c.e1.2)
        // Light and half vector projected on the face: the brushed highlight runs along the half vector.
        let hx = dot(half, e1), hy = dot(half, c.e2)
        let phi = atan2(hy, hx)
        var lx = dot(light, e1), ly = dot(light, c.e2)
        let ll = max(0.0001, (lx * lx + ly * ly).squareRoot()); lx /= ll; ly /= ll
        let lambert = max(0, dot(normal, light))
        drawFace(face, phi: phi, lx: lx, ly: ly, lambert: lambert, warp: warp)
    }

    static func drawFace(_ face: GraphicsContext, phi: Double, lx: Double, ly: Double, lambert: Double, warp: Double) {
        let disc = Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2))
        face.fill(disc, with: .conicGradient(Gradient(stops: faceStops), center: .zero, angle: .radians(phi)))
        // Lathe lines.
        for j in 0..<10 {
            let r = 0.12 + 0.085 * Double(j)
            face.stroke(Path(ellipseIn: CGRect(x: -r, y: -r, width: 2 * r, height: 2 * r)),
                        with: .color(j % 2 == 0 ? Color.white.opacity(0.06) : Color.black.opacity(0.05)), lineWidth: 0.012)
        }
        // Raised lip and the groove inside it.
        face.stroke(Path(ellipseIn: CGRect(x: -0.95, y: -0.95, width: 1.9, height: 1.9)),
                    with: .linearGradient(Gradient(colors: [Color.white.opacity(0.5), Color.white.opacity(0), Color.black.opacity(0.35)]),
                                          startPoint: CGPoint(x: lx * 0.95, y: ly * 0.95), endPoint: CGPoint(x: -lx * 0.95, y: -ly * 0.95)),
                    lineWidth: 0.1)
        face.stroke(Path(ellipseIn: CGRect(x: -0.888, y: -0.888, width: 1.776, height: 1.776)), with: .color(.black.opacity(0.3)), lineWidth: 0.014)
        face.stroke(Path(ellipseIn: CGRect(x: -0.872, y: -0.872, width: 1.744, height: 1.744)), with: .color(.white.opacity(0.35)), lineWidth: 0.01)

        // Embossed mark: a dark copy away from the light, a light copy toward it, then the mark itself.
        let d = 0.026
        var shade = face
        shade.translateBy(x: -lx * d, y: -ly * d)
        strokeMark(shade, with: .color(color(0x2A2D33, 0.6)))
        var lit = face
        lit.translateBy(x: lx * d, y: ly * d)
        strokeMark(lit, with: .color(Color.white.opacity(0.55)))
        strokeMark(face, with: .conicGradient(Gradient(stops: faceStops), center: .zero, angle: .radians(phi + Double.pi / 2)))

        // Shade by how much the face turns from the light, and pick up the warp's blue on the far edge.
        face.fill(disc, with: .color(.black.opacity(0.5 * (1 - lambert))))
        if warp > 0.01 {
            face.fill(disc, with: .radialGradient(Gradient(colors: [color(0x6E86FF, 0.30 * warp), color(0x6E86FF, 0)]),
                                                  center: CGPoint(x: 0, y: -1), startRadius: 0, endRadius: 1.3))
        }
        face.stroke(Path(ellipseIn: CGRect(x: -0.995, y: -0.995, width: 1.99, height: 1.99)), with: .color(.white.opacity(0.5)), lineWidth: 0.012)
    }

    /// The Arcwell mark in unit coordinates: an arch with a bar, like a rounded A.
    static let markArch: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -0.40, y: 0.48))
        p.addLine(to: CGPoint(x: -0.40, y: 0.03))
        for j in 1...32 {
            let a = Double.pi + Double.pi * Double(j) / 32
            p.addLine(to: CGPoint(x: 0.40 * cos(a), y: 0.03 + 0.40 * sin(a)))
        }
        p.addLine(to: CGPoint(x: 0.40, y: 0.48))
        return p
    }()
    static let markBar: Path = {
        var p = Path()
        p.move(to: CGPoint(x: -0.40, y: 0.19))
        p.addLine(to: CGPoint(x: 0.40, y: 0.19))
        return p
    }()

    static func strokeMark(_ context: GraphicsContext, with shading: GraphicsContext.Shading) {
        context.stroke(markArch, with: shading, style: StrokeStyle(lineWidth: 0.2, lineCap: .butt, lineJoin: .round))
        context.stroke(markBar, with: shading, style: StrokeStyle(lineWidth: 0.12, lineCap: .butt))
    }

    static func drawLogo(_ base: GraphicsContext, center: CGPoint, size: Double) {
        var context = base
        context.translateBy(x: center.x, y: center.y)
        context.scaleBy(x: size / 1.0, y: size / 1.0)
        strokeMark(context, with: .color(.white))
    }
}
