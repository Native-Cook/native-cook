import SwiftUI
import UIKit

/// The launch splash of Driftwing, a wind-down app. On a dark stage lit softly from the top, a wisp of
/// cyan smoke rises, curls and condenses into a glowing veined butterfly; "Driftwing" and its tagline
/// fade in, the wings beat twice, then the butterfly blows apart into billowing smoke and sparkling
/// dust that clears back to the dark stage. The smoke is one Metal shader (domain-warped fbm whose
/// noise loops on a circle) masked by a butterfly distance field; the dust and type are a Canvas.
///
/// Drag to stir the smoke around the finger and pull the butterfly apart; it re-forms on release,
/// and 2 s later dissolves and the loop carries on. Reduced motion: the formed butterfly, still.
struct SmokeButterflySplash: View {
    static let loop = 8.0
    /// The story time of the held, formed pose; a touch blends to it and the loop resumes from it.
    static let peak = 5.0
    static let idle = 2.0

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = SmokeButterflySplashTouch()
    @State private var awake = false

    var body: some View {
        GeometryReader { geometry in
            let size = geometry.size
            let scale = min(size.width / 393, size.height / 852)
            let origin = CGPoint(x: (size.width - 393 * scale) / 2, y: (size.height - 852 * scale) / 2)
            TimelineView(.animation(paused: reducedMotion || scenePhase != .active || !(autoPlay || awake))) { timeline in
                let state = frame(timeline.date)
                ZStack {
                    Rectangle()
                        .fill(Color.black)
                        .colorEffect(Self.shader(state, size: size))
                    Canvas { context, _ in
                        context.translateBy(x: origin.x, y: origin.y)
                        context.scaleBy(x: scale, y: scale)
                        SmokeButterflySplashArt.draw(context, state)
                    }
                }
            }
            .contentShape(Rectangle())
            .gesture(drag(origin: origin, scale: scale))
            .task(id: awake) {
                // Outside autoPlay, run the clock only until the dissolve after the last touch is over.
                guard awake, !autoPlay else { return }
                while !Task.isCancelled {
                    try? await Task.sleep(for: .milliseconds(250))
                    let clock = Date().timeIntervalSince(epoch)
                    if !touch.touching, let release = touch.release,
                       clock > release + Self.idle + (Self.loop - Self.peak) + 0.2 {
                        awake = false
                        return
                    }
                }
            }
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Driftwing. Breathe out. Let the day dissolve.")
            .accessibilityAddTraits(.isImage)
        }
        .background(Color(red: 0.008, green: 0.016, blue: 0.039))
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
    }

    // MARK: Time

    private func frame(_ now: Date) -> SmokeButterflySplashFrame {
        typealias M = SmokeButterflySplashMotion
        if reducedMotion {
            return SmokeButterflySplashFrame(params: M.params(Self.peak), clock: Self.peak, story: Self.peak)
        }
        let clock = max(0, now.timeIntervalSince(epoch))
        var story: Double
        let engage = touch.engage(clock)
        if let release = touch.release, !touch.touching, clock >= release + Self.idle {
            // Resumed: the story clock restarts from the held pose and dissolves.
            let base = release + Self.idle - Self.peak
            story = autoPlay ? M.wrap(clock - base) : min(Self.loop, clock - base)
        } else if autoPlay {
            story = M.wrap(clock - touch.base)
        } else {
            story = touch.resumed ? min(Self.loop, clock - touch.base) : 0
        }
        if story >= Self.loop { story = 0 }
        var params = M.params(story)
        if engage > 0 { params = params.mixed(M.params(Self.peak), engage) }
        var state = SmokeButterflySplashFrame(params: params, clock: autoPlay ? M.wrap(clock) : story, story: story)
        state.engage = engage
        state.stir = touch.stir(clock)
        state.pull = touch.pull(clock)
        state.finger = touch.finger
        state.velocity = touch.velocity
        state.drag = touch.drag
        return state
    }

    private static func shader(_ s: SmokeButterflySplashFrame, size: CGSize) -> Shader {
        let p = s.params
        let theta = 2 * Double.pi * s.clock / loop
        let speed = hypot(s.velocity.dx, s.velocity.dy)
        let k = speed > 1500 ? 1500 / speed : 1
        return ShaderLibrary.smokeButterflySplashField(
            .float4(Float(size.width), Float(size.height), Float(theta), Float(p.light)),
            .float4(Float(p.rise), Float(p.column), Float(p.halo), Float(p.form)),
            .float4(Float(p.spread), Float(p.flap), Float(p.veins), Float(s.pull)),
            .float4(Float(s.finger.x), Float(s.finger.y), Float(s.stir), Float(p.ember)),
            .float4(Float(s.velocity.dx * k / 1500), Float(s.velocity.dy * k / 1500), Float(s.drag.dx), Float(s.drag.dy))
        )
    }

    // MARK: Touch

    private func drag(origin: CGPoint, scale: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reducedMotion else { return }
                let clock = Date().timeIntervalSince(epoch)
                let point = CGPoint(x: (value.location.x - origin.x) / scale, y: (value.location.y - origin.y) / scale)
                if !touch.touching {
                    touch.begin(at: point, clock: clock, autoPlay: autoPlay)
                    if !awake { awake = true }
                }
                if touch.move(to: point, clock: clock) {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.7)
                }
            }
            .onEnded { _ in
                guard !reducedMotion, touch.touching else { return }
                touch.end(clock: Date().timeIntervalSince(epoch))
                UIImpactFeedbackGenerator(style: .light).impactOccurred(intensity: 0.5)
            }
    }
}

/// Touch state read by the timeline; a plain class, so writing it never re-renders the view.
final class SmokeButterflySplashTouch {
    var touching = false
    var start = 0.0
    var engageFrom = 0.0
    var release: Double?
    /// Story time = clock - base (autoPlay wraps it).
    var base = 0.0
    var resumed = false
    var finger = CGPoint(x: 196.5, y: 360)
    var velocity = CGVector.zero
    var drag = CGVector.zero
    var pullRaw = 0.0
    var pullAtRelease = 0.0
    var torn = false
    private var anchor = CGPoint.zero
    private var last = CGPoint.zero
    private var lastClock = 0.0

    func engage(_ clock: Double) -> Double {
        let held = touching || (release.map { clock < $0 + SmokeButterflySplash.idle } ?? false)
        guard held else { return 0 }
        return engageFrom + (1 - engageFrom) * SmokeButterflySplashMotion.easeOut((clock - start) / 0.45)
    }

    func stir(_ clock: Double) -> Double {
        if touching { return SmokeButterflySplashMotion.easeOut((clock - start) / 0.25) }
        guard let release, clock - release < 4 else { return 0 }
        return exp(-(clock - release) / 0.45)
    }

    func pull(_ clock: Double) -> Double {
        if touching { return pullRaw }
        guard let release else { return 0 }
        let age = max(0, clock - release), tau = 0.13
        return pullAtRelease * (1 + age / tau) * exp(-age / tau)
    }

    func begin(at point: CGPoint, clock: Double, autoPlay: Bool) {
        let current = engage(clock)
        if let release, clock >= release + SmokeButterflySplash.idle {
            base = release + SmokeButterflySplash.idle - SmokeButterflySplash.peak
            resumed = true
        }
        engageFrom = current
        touching = true
        start = clock
        release = nil
        anchor = point
        last = point
        lastClock = clock
        finger = point
        velocity = .zero
        drag = .zero
        pullRaw = 0
        torn = false
    }

    /// Returns true when the wings tear (pull passes 0.6), for a haptic.
    func move(to point: CGPoint, clock: Double) -> Bool {
        let dt = clock - lastClock
        if dt > 0.004 {
            let raw = CGVector(dx: (point.x - last.x) / dt, dy: (point.y - last.y) / dt)
            velocity = CGVector(dx: velocity.dx + (raw.dx - velocity.dx) * 0.3, dy: velocity.dy + (raw.dy - velocity.dy) * 0.3)
            last = point
            lastClock = clock
        }
        finger = point
        drag = CGVector(dx: point.x - anchor.x, dy: point.y - anchor.y)
        pullRaw = min(1, hypot(drag.dx, drag.dy) / 220)
        if pullRaw > 0.6, !torn { torn = true; return true }
        if pullRaw < 0.4 { torn = false }
        return false
    }

    func end(clock: Double) {
        touching = false
        release = clock
        pullAtRelease = pullRaw
    }
}

struct SmokeButterflySplashParams {
    var rise = 0.0, column = 0.0, halo = 0.0, form = 0.0, spread = 0.0, flap = 0.0, veins = 0.0
    var title = 0.0, tagline = 0.0, light = 0.65, ember = 1.0

    func mixed(_ other: Self, _ k: Double) -> Self {
        func m(_ a: Double, _ b: Double) -> Double { a + (b - a) * k }
        return Self(rise: m(rise, other.rise), column: m(column, other.column), halo: m(halo, other.halo),
                    form: m(form, other.form), spread: m(spread, other.spread), flap: m(flap, other.flap),
                    veins: m(veins, other.veins), title: m(title, other.title), tagline: m(tagline, other.tagline),
                    light: m(light, other.light), ember: m(ember, other.ember))
    }
}

struct SmokeButterflySplashFrame {
    var params: SmokeButterflySplashParams
    /// Continuous loop phase for the smoke's drift and the dust (seconds, 0 ..< loop).
    var clock: Double
    /// Story time (seconds, 0 ..< loop) that drives the sparkles' gather and burst.
    var story: Double
    var engage = 0.0
    var stir = 0.0
    var pull = 0.0
    var finger = CGPoint(x: 196.5, y: 360)
    var velocity = CGVector.zero
    var drag = CGVector.zero
}

/// The loop's timeline as pure functions of the story time.
enum SmokeButterflySplashMotion {
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(max((t - a) / (b - a), 0), 1) }
    static func easeOut(_ x: Double) -> Double { let u = min(max(x, 0), 1); return 1 - pow(1 - u, 3) }
    static func easeIn(_ x: Double) -> Double { let u = min(max(x, 0), 1); return u * u * u }
    static func easeInOut(_ x: Double) -> Double {
        let u = min(max(x, 0), 1); return u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2
    }
    static func wrap(_ t: Double) -> Double {
        let r = t.truncatingRemainder(dividingBy: SmokeButterflySplash.loop)
        return r < 0 ? r + SmokeButterflySplash.loop : r
    }
    static func beat(_ t: Double, _ a: Double, _ b: Double) -> Double {
        guard t > a, t < b else { return 0 }
        let s = sin(Double.pi * (t - a) / (b - a)); return s * s
    }

    static func params(_ t: Double) -> SmokeButterflySplashParams {
        var p = SmokeButterflySplashParams()
        p.rise = t < 6.5 ? easeOut(seg(t, 0.5, 2.1)) : 0
        p.column = p.rise * (1 - easeInOut(seg(t, 1.7, 2.9)))
        p.halo = easeOut(seg(t, 1.5, 2.8)) * (1 - easeInOut(seg(t, 6.0, 7.3)))
        p.form = easeInOut(seg(t, 1.6, 3.0)) * (1 - easeIn(seg(t, 5.0, 6.0)))
        p.spread = easeOut(seg(t, 5.0, 6.6)) * (1 - seg(t, 7.3, 7.4))
        p.flap = beat(t, 3.35, 3.9) + beat(t, 3.95, 4.5)
        p.veins = t < 6.5 ? easeOut(seg(t, 2.4, 3.2)) : 0
        p.title = easeOut(seg(t, 2.9, 3.4)) * (1 - easeIn(seg(t, 5.6, 6.1)))
        p.tagline = easeOut(seg(t, 3.05, 3.55)) * (1 - easeIn(seg(t, 5.65, 6.15)))
        // A faint incense-like thread at rest: it feeds the rising wisp and comes back at the end.
        p.ember = 1 - easeInOut(seg(t, 0.6, 1.6)) + easeInOut(seg(t, 6.6, 7.6))
        p.light = 0.65 + 0.35 * max(p.form, p.halo * 0.6)
        return p
    }
}

/// Dust and type, drawn over the shader in the 393 x 852 design space.
enum SmokeButterflySplashArt {
    static let center = CGPoint(x: 196.5, y: 360)

    static func hash(_ x: Double) -> Double { let v = sin(x * 12.9898) * 43758.5453; return v - floor(v) }

    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
            .opacity(min(1, max(0, alpha)))
    }

    static func dot(_ context: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ hex: UInt32, _ alpha: Double) {
        guard alpha > 0.004 else { return }
        context.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(color(hex, alpha)))
    }

    static func draw(_ base: GraphicsContext, _ s: SmokeButterflySplashFrame) {
        let loop = SmokeButterflySplash.loop
        let tau = 2 * Double.pi
        var context = base
        context.blendMode = .plusLighter

        // Ambient motes drifting up through the light; each rises once or twice per loop.
        for i in 0..<26 {
            let h1 = hash(Double(i) + 1), h2 = hash(Double(i) + 11), h3 = hash(Double(i) + 21)
            let phase = (h1 + s.clock / loop * Double(1 + i % 2)).truncatingRemainder(dividingBy: 1)
            let x = 30 + 333 * h2 + 14 * sin(tau * phase + h3 * 6)
            let y = 830 - 720 * phase
            let twinkle = 0.6 + 0.4 * sin(tau * 3 * s.clock / loop + h1 * 6)
            let a = pow(sin(Double.pi * phase), 1.5) * (0.16 + 0.30 * h3) * twinkle
            dot(context, x, y, 0.5 + 0.8 * h3, 0xBFEFFF, a)
        }

        // Sparkles: they gather into the wings as it forms and burst out and up as it dissolves.
        let hide = 1 - s.engage
        if hide > 0.01 {
            for i in 0..<140 {
                let fi = Double(i)
                let side: Double = i % 2 == 0 ? 1 : -1
                let u = hash(fi * 3.1 + 2), v = hash(fi * 7.7 + 5), h4 = hash(fi + 41), h5 = hash(fi + 77)
                let ang = -1.25 + 2.3 * u
                let rad = (0.15 + 0.85 * sqrt(v)) * (ang < 0 ? 1.0 : 0.72)
                let ox = center.x + 150 * side * cos(ang) * rad
                let oy = center.y + 150 * sin(ang) * rad * 0.9
                var dx = ox - center.x, dy = oy - center.y + 20
                let len = max(1, hypot(dx, dy)); dx /= len; dy /= len
                let turn = (h4 - 0.5) * 0.8
                let ux = dx * cos(turn) - dy * sin(turn), uy = dx * sin(turn) + dy * cos(turn)
                let twinkle = 0.55 + 0.45 * sin(tau * 24 * s.clock / loop + h5 * 6.28)
                let r = 0.6 + 1.2 * h5
                let hex: UInt32 = i % 3 == 0 ? 0xCFF8FF : 0x7FE3FF

                let gStart = 1.8 + 0.4 * h4
                let g = seg(s.story, gStart, gStart + 0.8)
                if g > 0, g < 1 {
                    let away = (1 - SmokeButterflySplashMotion.easeOut(g)) * 90 * (0.5 + h5)
                    let a = sin(Double.pi * g) * 0.8 * twinkle * hide
                    dot(context, ox + ux * away, oy + uy * away, r, hex, a)
                }
                let bStart = 5.0 + 0.35 * h4
                let b = seg(s.story, bStart, bStart + 1.5 + 0.4 * h5)
                if b > 0, b < 1 {
                    let travel = SmokeButterflySplashMotion.easeOut(b)
                    let dist = travel * (70 + 120 * h5)
                    let x = ox + ux * dist + 12 * sin(tau * b + h4 * 6)
                    let y = oy + uy * dist - 60 * travel
                    let a = pow(sin(Double.pi * b), 0.8) * twinkle * hide
                    if h5 > 0.8 { dot(context, x, y, r * 3, hex, a * 0.22) }
                    dot(context, x, y, r, hex, a)
                }
            }
        }

        // Type.
        let p = s.params
        var type = base
        type.blendMode = .normal
        if p.title > 0.002 {
            let y = 584 + 8 * (1 - p.title)
            let title = Text("Driftwing").font(.system(size: 40, weight: .light)).kerning(1.5)
            var glow = type
            glow.addFilter(.blur(radius: 12))
            glow.draw(title.foregroundColor(color(0x3CCBFF, 0.55 * p.title)), at: CGPoint(x: 196.5, y: y))
            type.draw(title.foregroundColor(color(0xE6F7FF, p.title)), at: CGPoint(x: 196.5, y: y))
        }
        if p.tagline > 0.002 {
            let y = 624 + 8 * (1 - p.tagline)
            type.draw(Text("Breathe out. Let the day dissolve.").font(.system(size: 15)).foregroundColor(color(0x86A9C8, p.tagline)),
                      at: CGPoint(x: 196.5, y: y))
        }
        type.draw(Text("SLEEP · BREATHE · DRIFT").font(.system(size: 11, weight: .medium)).kerning(3)
            .foregroundColor(color(0x4D6A8C)), at: CGPoint(x: 196.5, y: 792))
    }

    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { SmokeButterflySplashMotion.seg(t, a, b) }
}
