import SwiftUI
import UIKit

/// An energy check-in emblem for an invented wellness app, Vessa: one glowing shape filled like a
/// thermal camera image morphs star → diamond → circle → clover → blob and back, swapping its hot
/// and cool palettes, while the state's name crossfades under it. Tap for the next state, drag
/// sideways to scrub the morph.
struct ThermalShapeMorph: View {
    /// Plays the five morphs by itself every `loop` seconds.
    var autoPlay = false

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    /// A move of the morph position `u` started by a finger (or the ease back to rest).
    private struct Manual {
        var from: Double
        var to: Double
        var start: Date
        var duration: Double
        var back: Bool
        /// The loop clock held while a finger is in charge, eased to `tTo` on the way back.
        var tFrom: Double
        var tTo: Double
    }

    /// nil while the loop drives the emblem.
    @State private var manual: Manual?
    /// When the loop's clock read 0.
    @State private var origin = Date()
    @State private var dragBase: Double?
    @State private var dragMoved = false
    @State private var touches = 0
    @State private var lastWhole = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            let size = proxy.size
            TimelineView(.animation(paused: reduceMotion)) { timeline in
                let pose = pose(at: timeline.date)
                let cy = size.height / 2 + S.centerShift
                ZStack(alignment: .topLeading) {
                    Canvas { context, canvasSize in
                        S.draw(in: &context, size: canvasSize, pose: pose)
                    }
                    labels(pose: pose)
                        .frame(width: size.width)
                        .position(x: size.width / 2, y: cy)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .accessibilityElement()
                .accessibilityLabel(Text("Energy state: \(S.states[pose.shown].name). \(S.states[pose.shown].line)"))
                .accessibilityHint(Text("Tap for the next state"))
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { tap() }
            }
        }
        .ignoresSafeArea()
        .background(Color(hex: 0x07060B).ignoresSafeArea())
        .contentShape(Rectangle())
        .gesture(drag)
        .preferredColorScheme(.dark)
        .task(id: touches) { await resumeLoop() }
    }

    // MARK: Labels

    /// Eyebrow, the five state names and lines stacked (each with its own opacity), and the hint.
    /// Laid out around the emblem's centre.
    private func labels(pose: S.Pose) -> some View {
        ZStack {
            Text("VESSA · ENERGY CHECK-IN")
                .font(.system(size: 11, weight: .semibold))
                .tracking(2.2)
                .foregroundStyle(Color(hex: 0x7D7896))
                .offset(y: S.eyebrowY)
            ForEach(0..<S.states.count, id: \.self) { k in
                let a = pose.captionAlpha(k)
                let dy = pose.captionShift(k)
                Group {
                    Text(S.states[k].name)
                        .font(.system(size: 30, weight: .semibold))
                        .tracking(-0.3)
                        .foregroundStyle(Color(hex: 0xF3F0FA))
                        .offset(y: S.nameY + dy)
                    Text(S.states[k].line)
                        .font(.system(size: 15))
                        .foregroundStyle(Color(hex: 0x9D98B5))
                        .offset(y: S.lineY + dy * 0.6)
                }
                .opacity(a)
            }
            Text("Tap to shift · drag to scrub")
                .font(.system(size: 12, weight: .medium))
                .foregroundStyle(Color(hex: 0x4F4A66))
                .offset(y: S.hintY)
        }
        .fixedSize()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: Clock

    private func pose(at date: Date) -> S.Pose {
        if let man = manual {
            let k = S.clamp(date.timeIntervalSince(man.start) / max(man.duration, 0.0001))
            let e = man.back ? S.easeOutBack(k) : S.easeInOutCubic(k)
            let u = man.from + (man.to - man.from) * e
            let t: Double
            if reduceMotion {
                t = 0
            } else if autoPlay {
                t = man.tFrom + (man.tTo - man.tFrom) * S.easeInOutCubic(k)
            } else {
                t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: Self.loop)
            }
            return S.Pose(u: u, t: t)
        }
        if reduceMotion { return S.Pose(u: 0, t: 0) }
        let t = date.timeIntervalSince(origin).truncatingRemainder(dividingBy: Self.loop)
        return autoPlay ? S.Pose.auto(t) : S.Pose(u: 0, t: t)
    }

    /// The morph position now, whoever drives it.
    private func currentU(_ date: Date) -> Double { pose(at: date).u }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if dragBase == nil {
                    let p = pose(at: now)
                    dragBase = p.u
                    dragMoved = false
                    lastWhole = Int(floor(p.u + 0.5))
                    haptic.prepare()
                    manual = Manual(from: p.u, to: p.u, start: now, duration: 0, back: false, tFrom: p.t, tTo: p.t)
                    touches += 1
                }
                if abs(value.translation.width) > 6 { dragMoved = true }
                guard dragMoved, let base = dragBase, var man = manual else { return }
                let u = base - value.translation.width / S.dragPerState
                man.from = u
                man.to = u
                man.start = now
                man.duration = 0
                manual = man
                let whole = Int(floor(u + 0.5))
                if whole != lastWhole {
                    lastWhole = whole
                    haptic.impactOccurred(intensity: 0.7)
                }
            }
            .onEnded { _ in
                let moved = dragMoved
                dragBase = nil
                dragMoved = false
                if moved {
                    settle(to: (currentU(.now)).rounded(), duration: 0.45)
                } else {
                    tap()
                }
                touches += 1
            }
    }

    private func tap() {
        let u = currentU(.now)
        haptic.impactOccurred(intensity: 0.8)
        settle(to: floor(u + 0.02) + 1, duration: 0.75)
        touches += 1
    }

    private func settle(to target: Double, duration: Double) {
        let now = Date.now
        let p = pose(at: now)
        let t = manual?.tTo ?? p.t
        manual = Manual(from: p.u, to: target, start: now, duration: reduceMotion ? 0 : duration,
                        back: true, tFrom: t, tTo: t)
    }

    /// 2 s after the last touch the emblem eases back to the resting star and the loop restarts.
    private func resumeLoop() async {
        guard autoPlay, touches > 0 else { return }
        try? await Task.sleep(for: .seconds(2))
        guard !Task.isCancelled, dragBase == nil, let man = manual else { return }
        let now = Date.now
        let u = currentU(now)
        let home = (u / 5).rounded() * 5
        let ease = reduceMotion ? 0 : S.returnFor
        manual = Manual(from: u, to: home, start: now, duration: ease, back: false,
                        tFrom: man.tTo, tTo: man.tTo < Self.loop / 2 ? 0 : Self.loop)
        try? await Task.sleep(for: .seconds(ease))
        guard !Task.isCancelled else { return }
        origin = .now
        manual = nil
    }
}

private typealias S = ThermalShapeMorphScene

/// Everything on screen at one moment, drawn from the morph position `u` and the loop time `t`.
enum ThermalShapeMorphScene {
    // Layout, in points from the emblem's centre (which sits 40 pt above the screen's middle).
    static let centerShift = -40.0
    static let radius = 104.0
    static let eyebrowY = -186.0
    static let nameY = 184.0
    static let lineY = 218.0
    static let dotsY = 258.0
    static let hintY = 296.0
    static let dragPerState = 120.0
    static let returnFor = 0.9

    // The loop: rest, then five morphs of `morphFor` seconds, `step` apart.
    static let restFor = 0.8
    static let morphFor = 1.2
    static let step = 1.45

    struct State {
        let name: String
        let line: String
        /// 0 hot … 1 cool.
        let cool: Double
        /// Direction of the bright side of the fill, degrees (y down).
        let angle: Double
        let core: CGPoint
        let coreRadius: Double
        let coreAlpha: Double
    }

    static let states: [State] = [
        State(name: "Spark", line: "Quick, bright and ready to move", cool: 0, angle: 100,
              core: CGPoint(x: 0, y: 0.06), coreRadius: 0.78, coreAlpha: 0.95),
        State(name: "Steady", line: "Even, grounded and calm", cool: 1, angle: 55,
              core: CGPoint(x: 0.25, y: 0.38), coreRadius: 0.62, coreAlpha: 0.8),
        State(name: "Glow", line: "Warm, full and generous", cool: 0, angle: -75,
              core: CGPoint(x: 0.12, y: -0.34), coreRadius: 0.66, coreAlpha: 0.9),
        State(name: "Bloom", line: "Open and slowly growing", cool: 1, angle: -160,
              core: CGPoint(x: 0, y: 0), coreRadius: 0.85, coreAlpha: 0.9),
        State(name: "Drift", line: "Loose, easy, going with it", cool: 0.8, angle: -320,
              core: CGPoint(x: 0.3, y: 0.28), coreRadius: 0.6, coreAlpha: 0.75),
    ]

    // MARK: Pose

    struct Pose {
        /// State index this segment starts from (0…4).
        let i: Int
        /// Outline mix toward state i + 1 (may overshoot a little).
        let m: Double
        /// Colour / caption mix toward state i + 1, 0…1.
        let q: Double
        /// Loop time, seconds.
        let t: Double
        /// Morph position: i + m.
        var u: Double { Double(i) + m }

        init(u: Double, t: Double) {
            let f = floor(u)
            i = ThermalShapeMorphScene.wrap(Int(f))
            m = u - f
            q = ThermalShapeMorphScene.clamp(u - f)
            self.t = t
        }

        init(i: Int, m: Double, q: Double, t: Double) {
            self.i = ThermalShapeMorphScene.wrap(i)
            self.m = m
            self.q = q
            self.t = t
        }

        static func auto(_ t: Double) -> Pose {
            for k in 0..<5 {
                let s = restFor + step * Double(k)
                if t < s { return Pose(i: k, m: 0, q: 0, t: t) }
                if t < s + morphFor {
                    let p = (t - s) / morphFor
                    return Pose(i: k, m: easeInOutBack(p), q: easeInOutCubic(p), t: t)
                }
            }
            return Pose(i: 0, m: 0, q: 0, t: t)
        }

        /// The state whose caption is showing.
        var shown: Int { q < 0.5 ? i : ThermalShapeMorphScene.wrap(i + 1) }

        func captionAlpha(_ k: Int) -> Double {
            if k == i { return 1 - smoothstep(0.3, 0.5, q) }
            if k == ThermalShapeMorphScene.wrap(i + 1) { return smoothstep(0.5, 0.7, q) }
            return 0
        }

        func captionShift(_ k: Int) -> Double {
            if k == i { return -8 * smoothstep(0.3, 0.5, q) }
            if k == ThermalShapeMorphScene.wrap(i + 1) { return 8 * (1 - smoothstep(0.5, 0.7, q)) }
            return 0
        }
    }

    // MARK: Shapes

    /// Radius (in units of R) of state `k` at angle `a` (radians, y down).
    static func shapeRadius(_ k: Int, _ a: Double) -> Double {
        switch wrap(k) {
        case 0:
            let p = 0.72
            return 1.06 * pow(pow(abs(cos(a)), p) + pow(abs(sin(a)), p), -1 / p)
        case 1:
            let n = 3.4
            let b = a - .pi / 4
            return 0.80 * pow(pow(abs(cos(b)), n) + pow(abs(sin(b)), n), -1 / n)
        case 2:
            return 0.86
        case 3:
            return 0.84 * (1 - 0.12 * cos(4 * a) - 0.04 * cos(8 * a))
        default:
            return 0.85 * (1 + 0.075 * cos(2 * a + 0.6) + 0.06 * sin(3 * a + 1.1) + 0.045 * cos(5 * a + 2.0))
        }
    }

    static let samples = 180

    /// The outline's radii (in points) for this pose, before the glow scales.
    static func radii(_ pose: Pose) -> [Double] {
        let tau = 2 * Double.pi
        let w1 = tau * 2 * pose.t / ThermalShapeMorph.loop
        let w2 = tau * 3 * pose.t / ThermalShapeMorph.loop
        let swell = 1 + 0.035 * sin(.pi * pose.q)
        var out = [Double](repeating: 0, count: samples)
        for j in 0..<samples {
            let a = tau * Double(j) / Double(samples)
            let r0 = shapeRadius(pose.i, a)
            let r1 = shapeRadius(pose.i + 1, a)
            let living = 1 + 0.010 * sin(3 * a + w1) + 0.006 * sin(5 * a - w2)
            out[j] = (r0 + (r1 - r0) * pose.m) * living * swell * radius
        }
        return out
    }

    /// A smooth closed path through the sampled outline, scaled about the centre.
    static func outline(_ radii: [Double], center c: CGPoint, scale: Double) -> Path {
        let n = radii.count
        var pts = [CGPoint](repeating: .zero, count: n)
        for j in 0..<n {
            let a = 2 * Double.pi * Double(j) / Double(n)
            let r = radii[j] * scale
            pts[j] = CGPoint(x: c.x + r * cos(a), y: c.y + r * sin(a))
        }
        func mid(_ p: CGPoint, _ q: CGPoint) -> CGPoint { CGPoint(x: (p.x + q.x) / 2, y: (p.y + q.y) / 2) }
        var path = Path()
        path.move(to: mid(pts[n - 1], pts[0]))
        for j in 0..<n {
            path.addQuadCurve(to: mid(pts[j], pts[(j + 1) % n]), control: pts[j])
        }
        path.closeSubpath()
        return path
    }

    // MARK: Colour

    struct RGB {
        var r, g, b: Double
        init(_ hex: UInt32) {
            r = Double((hex >> 16) & 0xFF) / 255
            g = Double((hex >> 8) & 0xFF) / 255
            b = Double(hex & 0xFF) / 255
        }
        init(r: Double, g: Double, b: Double) { self.r = r; self.g = g; self.b = b }
        func mix(_ o: RGB, _ k: Double) -> RGB { RGB(r: r + (o.r - r) * k, g: g + (o.g - g) * k, b: b + (o.b - b) * k) }
        func color(_ a: Double = 1) -> Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
    }

    static let hot: [RGB] = [0x14040E, 0x6E0A1F, 0xD8231C, 0xFF7418, 0xFFCF3A, 0xFFF7DA].map(RGB.init)
    static let cold: [RGB] = [0x060619, 0x17168A, 0x3434E0, 0x6E78FF, 0xBFC4FF, 0xF7F6FF].map(RGB.init)
    static let rampStops: [Double] = [0, 0.18, 0.40, 0.62, 0.82, 1]
    static let rimDark = RGB(0xFF2A1F)
    static let rimHot = RGB(0xFF7A3A)
    static let rimCool = RGB(0x8A7BFF)
    static let violet = RGB(0x4A3AFF)
    static let warmGlow = RGB(0xFF5A36)
    static let accentHot = RGB(0xFF8A3D)
    static let accentCool = RGB(0x8C8CFF)
    static let dotDim = RGB(0x34304A)

    /// The fill's look for this pose, mixed between its two states.
    struct Look {
        let cool: Double
        let angle: Double
        let core: CGPoint
        let coreRadius: Double
        let coreAlpha: Double
    }

    static func look(_ pose: Pose) -> Look {
        let a = states[pose.i]
        let b = states[wrap(pose.i + 1)]
        let q = pose.q
        // Angles keep turning one way round the loop: the last state's 100° is written as −260°.
        let angleB = pose.i == 4 ? -260.0 : b.angle
        let tau = 2 * Double.pi
        let drift = CGPoint(x: 0.07 * sin(tau * pose.t / ThermalShapeMorph.loop),
                            y: 0.07 * cos(tau * 2 * pose.t / ThermalShapeMorph.loop))
        return Look(
            cool: lerp(a.cool, b.cool, q),
            angle: lerp(a.angle, angleB, q) + 8 * sin(tau * pose.t / ThermalShapeMorph.loop),
            core: CGPoint(x: lerp(a.core.x, b.core.x, q) + drift.x, y: lerp(a.core.y, b.core.y, q) + drift.y),
            coreRadius: lerp(a.coreRadius, b.coreRadius, q),
            coreAlpha: lerp(a.coreAlpha, b.coreAlpha, q)
        )
    }

    // MARK: Draw

    static func draw(in context: inout GraphicsContext, size: CGSize, pose: Pose) {
        let c = CGPoint(x: size.width / 2, y: size.height / 2 + centerShift)
        let look = look(pose)
        let ramp = (0..<6).map { hot[$0].mix(cold[$0], look.cool) }
        let tau = 2 * Double.pi
        let breath = 1 + 0.018 * sin(tau * 2 * pose.t / ThermalShapeMorph.loop)

        // Stage: a dark violet vignette.
        let full = Path(CGRect(origin: .zero, size: size))
        context.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: RGB(0x120E1F).color(), location: 0),
                .init(color: RGB(0x07060B).color(), location: 0.55),
                .init(color: RGB(0x040307).color(), location: 1),
            ]),
            center: c, startRadius: 0, endRadius: size.height * 0.75))

        let r = radii(pose)
        let shape = outline(r, center: c, scale: 1)
        let rad = look.angle * .pi / 180
        let dir = CGPoint(x: cos(rad), y: sin(rad))
        let darkEnd = CGPoint(x: c.x - dir.x * radius * 0.9, y: c.y - dir.y * radius * 0.9)
        let brightEnd = CGPoint(x: c.x + dir.x * radius * 0.95, y: c.y + dir.y * radius * 0.95)

        // Glows, added onto the stage.
        context.drawLayer { layer in
            layer.blendMode = .plusLighter
            layer.drawLayer { glow in
                glow.addFilter(.blur(radius: 44))
                glow.fill(outline(r, center: c, scale: 1.3 * breath),
                          with: .color(warmGlow.color(lerp(0.42, 0.32, look.cool))))
            }
            layer.drawLayer { glow in
                glow.addFilter(.blur(radius: 24))
                glow.fill(outline(r, center: c, scale: 1.09 * breath), with: .color(violet.color(1)))
            }
            layer.drawLayer { glow in
                glow.addFilter(.blur(radius: 9))
                glow.stroke(shape, with: .color(violet.color(1)), lineWidth: 16)
            }
        }

        // Body: the thermal ramp, the hot core and a cooler band along the edge.
        context.drawLayer { body in
            body.clip(to: shape)
            body.fill(shape, with: .linearGradient(
                Gradient(stops: (0..<6).map { .init(color: ramp[$0].color(), location: rampStops[$0]) }),
                startPoint: darkEnd, endPoint: brightEnd))
            let core = CGPoint(x: c.x + look.core.x * radius, y: c.y + look.core.y * radius)
            let cr = look.coreRadius * radius
            let ca = look.coreAlpha
            body.fill(Path(ellipseIn: CGRect(x: core.x - cr, y: core.y - cr, width: cr * 2, height: cr * 2)),
                      with: .radialGradient(Gradient(stops: [
                          .init(color: ramp[5].color(ca), location: 0),
                          .init(color: ramp[5].color(ca * 0.9), location: 0.22),
                          .init(color: ramp[4].color(ca * 0.7), location: 0.5),
                          .init(color: ramp[3].color(ca * 0.25), location: 0.78),
                          .init(color: ramp[3].color(0), location: 1),
                      ]), center: core, startRadius: 0, endRadius: cr))
            body.drawLayer { edge in
                edge.addFilter(.blur(radius: 10))
                edge.stroke(shape, with: .color(ramp[1].color(0.6)), lineWidth: 24)
            }
        }

        // Rim: a soft glow and a thin hot line, red on the dark side.
        let rimBright = rimHot.mix(rimCool, look.cool)
        let rimShading = GraphicsContext.Shading.linearGradient(
            Gradient(colors: [rimDark.color(), rimDark.mix(rimBright, 0.5).color(), rimBright.color()]),
            startPoint: darkEnd, endPoint: brightEnd)
        context.drawLayer { rim in
            rim.blendMode = .plusLighter
            rim.drawLayer { glow in
                glow.addFilter(.blur(radius: 4))
                glow.opacity = 0.9
                glow.stroke(shape, with: rimShading, lineWidth: 6)
            }
        }
        context.stroke(shape, with: rimShading, lineWidth: 2)

        // Dots: one per state, the shown one stretched into a pill.
        let uC = Double(pose.i) + pose.q
        let accent = accentHot.mix(accentCool, look.cool)
        let dotsY = c.y + dotsY
        let base: Double = 6, grow: Double = 14, gap: Double = 10
        var widths = [Double]()
        for j in 0..<5 {
            var d = abs(uC - Double(j))
            d = min(d, 5 - d)
            widths.append(base + grow * max(0, 1 - d))
        }
        let total = widths.reduce(0, +) + gap * 4
        var x = c.x - total / 2
        for j in 0..<5 {
            var d = abs(uC - Double(j))
            d = min(d, 5 - d)
            let on = max(0, 1 - d)
            let rect = CGRect(x: x, y: dotsY - base / 2, width: widths[j], height: base)
            context.fill(Path(roundedRect: rect, cornerRadius: base / 2), with: .color(dotDim.mix(accent, on).color()))
            x += widths[j] + gap
        }
    }

    // MARK: Math

    static func wrap(_ k: Int) -> Int { ((k % 5) + 5) % 5 }
    static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func lerp(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let k = clamp((x - a) / (b - a))
        return k * k * (3 - 2 * k)
    }
    static func easeInOutCubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    /// About 3 % anticipation and overshoot.
    static func easeInOutBack(_ x: Double) -> Double {
        let c2 = 0.9 * 1.525
        return x < 0.5
            ? (pow(2 * x, 2) * ((c2 + 1) * 2 * x - c2)) / 2
            : (pow(2 * x - 2, 2) * ((c2 + 1) * (x * 2 - 2) + c2) + 2) / 2
    }
    /// Lands with about 4 % overshoot.
    static func easeOutBack(_ x: Double) -> Double {
        let c1 = 1.1, c3 = c1 + 1
        return 1 + c3 * pow(x - 1, 3) + c1 * pow(x - 1, 2)
    }
}
