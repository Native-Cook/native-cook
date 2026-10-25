import SwiftUI
import UIKit

/*
 Jelly Cube Squish — a dessert-bar order screen ("Wobble — Build your jelly") with a translucent
 jelly cube on a plate: lit per vertex (diffuse, a Fresnel sheen, see-through far faces, an inner
 glow, suspended bubbles, additive gloss and a reflected window) and deformed as a soft body. Poke
 it and it dents and wobbles; drag it and the part under the finger stretches, then springs back
 wobbling. Pick a flavour and the new colour rises through it from the plate in a gooey front.

 Layers: a Canvas with the baked backdrop and plate, the cube's contact shadow and caustic; the
 cube's vertex-coloured meshes in a Metal view (SwiftUI cannot shade a triangle from its corners);
 a Canvas with the gloss, the demo finger, the flavour's name and the chips' sliding highlight.
 Deformation, camera and timeline: JellyCubeSquishMath.swift; drawing: JellyCubeSquishArt.swift.

 Touch: springs — squash and lean ω 13 rad/s ζ 0.12, dent ω 20 ζ 0.25, stretch ω 12 ζ 0.15. 2 s
 after the last touch the cube eases to rest (0.9 s), the colour fills back to strawberry if
 needed, and the loop restarts. Reduced motion: no loop, no wobble (the shape follows the finger
 directly); a new flavour shows at once.
 */
struct JellyCubeSquish: View {
    /// Pokes the cube, switches to lime, stretches it, and back to strawberry, on an 8 s loop.
    var autoPlay = false

    static let loop = 8.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = JellyCubeSquishEngine()
    @State private var backdrop: (geo: JellyCubeSquishGeo, image: UIImage)?
    @State private var awake = true

    private typealias P = JellyCubeSquishPaint
    private static let ink = P.color(0x3A1F22)

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            let g = JellyCubeSquishGeo(w: size.width, h: size.height, top: insets.top, bottom: insets.bottom)
            stage(g)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
                .task(id: g) { await bake(g) }
        }
        .background(P.color(0xF1DCD6).ignoresSafeArea())
    }

    private func bake(_ g: JellyCubeSquishGeo) async {
        guard g.w > 0, g.h > 0, backdrop?.geo != g else { return }
        let scale = min(displayScale, 3)
        let image = await Task.detached(priority: .userInitiated) {
            _ = JellyCubeSquishMath.restPoints
            return JellyCubeSquishArt.backdrop(g, scale: scale)
        }.value
        backdrop = (g, image)
    }

    @ViewBuilder
    private func stage(_ g: JellyCubeSquishGeo) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        ZStack(alignment: .topLeading) {
            P.color(0xF1DCD6)
            if let backdrop, backdrop.geo == g {
                TimelineView(.animation(paused: !auto && !awake)) { timeline in
                    let s = engine.step(timeline.date, auto: auto, reduced: reduced, g: g)
                    ZStack(alignment: .topLeading) {
                        Canvas { ctx, _ in
                            ctx.draw(Image(uiImage: backdrop.image), in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                            JellyCubeSquishArt.plateLight(ctx, g, s.d, mid: JellyCubeSquishArt.colourAt(s.colour, 0, 0, JellyCubeSquishMath.half))
                        }
                        JellyCubeSquishMeshView(frame: engine.mesh, number: engine.mesh.number)
                        Canvas { ctx, _ in
                            JellyCubeSquishArt.gloss(ctx, g, s.d)
                            JellyCubeSquishArt.finger(ctx, g, s.finger, alpha: s.fingerAlpha)
                            JellyCubeSquishArt.name(ctx, g, s.colour)
                            JellyCubeSquishArt.chipHighlight(ctx, g, s.colour)
                        }
                    }
                    .frame(width: g.w, height: g.h)
                    .onChange(of: engine.settled, initial: true) { _, settled in
                        if settled && !auto { awake = false }
                    }
                }
                .frame(width: g.w, height: g.h)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            awake = true
                            engine.drag(v, g: g)
                        }
                        .onEnded { _ in
                            awake = true
                            engine.release()
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("A jelly cube on a plate. Poke it or drag it to make it wobble.")
                .accessibilityAddTraits(.allowsDirectInteraction)

                header(g)
                chips(g, reduced: reduced)
                cta(g)
            }
        }
        .frame(width: g.w, height: g.h, alignment: .topLeading)
        .clipped()
    }

    private func header(_ g: JellyCubeSquishGeo) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("WOBBLE · DESSERT BAR")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.8)
                .foregroundStyle(Self.ink.opacity(0.5))
            Text("Build your jelly")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(Self.ink)
            Text("Step 2 of 3 · Pick a flavour")
                .font(.system(size: 15))
                .foregroundStyle(Self.ink.opacity(0.6))
        }
        .frame(width: g.w - 48, alignment: .leading)
        .offset(x: 24, y: g.top + 16)
        .allowsHitTesting(false)
    }

    private func chips(_ g: JellyCubeSquishGeo, reduced: Bool) -> some View {
        HStack(spacing: 8) {
            ForEach(0..<JellyCubeSquishMotion.flavours.count, id: \.self) { i in
                let f = JellyCubeSquishMotion.flavours[i]
                Button {
                    engine.pick(i, reduced: reduced)
                    awake = true
                } label: {
                    HStack(spacing: 6) {
                        Circle()
                            .fill(Color(.sRGB, red: f.rgb.x, green: f.rgb.y, blue: f.rgb.z))
                            .frame(width: 10, height: 10)
                        Text(f.name)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Self.ink)
                            .lineLimit(1)
                    }
                    .padding(.horizontal, 6)
                    .frame(width: g.chipW, height: 44)
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel(f.name)
            }
        }
        .frame(width: g.w - 32, height: 44, alignment: .leading)
        .offset(x: 16, y: g.chipsTop)
    }

    private func cta(_ g: JellyCubeSquishGeo) -> some View {
        Button {} label: {
            HStack {
                Text("Add to order")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(P.color(0xFFF4F1))
                Spacer()
                Text("$6.50")
                    .font(.system(size: 17, weight: .semibold).monospacedDigit())
                    .foregroundStyle(P.color(0xFFF4F1, 0.7))
            }
            .padding(.horizontal, 24)
            .frame(width: g.w - 32, height: 56)
            .background(Capsule().fill(Self.ink))
            .shadow(color: P.color(0x3A1F22, 0.3), radius: 1, y: 1)
            .shadow(color: P.color(0x3A1F22, 0.22), radius: 12, y: 10)
            .contentShape(Capsule())
        }
        .buttonStyle(JellyCubeSquishPress())
        .offset(x: 16, y: g.ctaTop)
    }
}

private struct JellyCubeSquishPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.spring(response: 0.25, dampingFraction: 0.8), value: configuration.isPressed)
    }
}

/// What one frame shows.
struct JellyCubeSquishScene {
    var d = JellyCubeSquishDeform()
    var colour = JellyCubeSquishColour()
    var finger = JellyCubeSquishV3(0, 0, 0)
    var fingerAlpha = 0.0
    var time = 0.0
}

/// The clock, the soft body's springs and the finger, stepped once a frame; builds the meshes.
final class JellyCubeSquishEngine {
    private typealias J = JellyCubeSquishMath
    private typealias A = JellyCubeSquishMotion

    private static let idle = 2.0
    private static let settle = 0.9

    /// One spring: value, velocity, target.
    private struct Spring {
        var x = 0.0
        var v = 0.0
        var target = 0.0

        mutating func step(_ omega: Double, _ zeta: Double, _ dt: Double, reduced: Bool) {
            if reduced {
                x = target
                v = 0
                return
            }
            let n = max(1, Int(ceil(dt * 240)))
            let h = dt / Double(n)
            for _ in 0..<n {
                v += (-omega * omega * (x - target) - 2 * zeta * omega * v) * h
                x += v * h
            }
        }

        var still: Bool { abs(x - target) < 0.002 && abs(v) < 0.01 }
    }

    /// Spring state: the deformation's springs, the touch point and normal, the colour change.
    private struct Soft {
        var sq = Spring()
        var lx = Spring()
        var ly = Spring()
        var dent = Spring()
        var sx = Spring()
        var sy = Spring()
        var sz = Spring()
        var p = JellyCubeSquishV3(0, -JellyCubeSquishMath.half, JellyCubeSquishMath.half)
        var n = JellyCubeSquishV3(0, 1, 0)
        /// From, to, and when the fill began.
        var colour = (from: 0, to: 0, start: -10.0)
    }

    /// 0 loop / rest, 1 a finger is in charge, 2 settling back to rest.
    private var mode = 0
    private var loopT = -1.0
    private var last: Double?
    private var wasAuto: Bool?
    private var lastTouch = 0.0
    /// 1 touching, 0 not, −1 a touch that missed the cube.
    private var touching = 0
    private var touchStarted = false
    private var soft = Soft()
    private var settleAt = -1.0
    private var scene = JellyCubeSquishScene()
    private(set) var settled = false
    let mesh = JellyCubeSquishMeshFrame()
    private let pokeFeel = UIImpactFeedbackGenerator(style: .soft)
    private let releaseFeel = UIImpactFeedbackGenerator(style: .light)
    private let pickFeel = UISelectionFeedbackGenerator()

    private var now: Double { Date().timeIntervalSinceReferenceDate }

    /// The colour state of a touch-mode morph.
    private func morph(_ t: Double, reduced: Bool) -> JellyCubeSquishColour {
        let c = soft.colour
        if reduced { return JellyCubeSquishColour(from: c.to, to: c.to, fill: 0, roll: 0) }
        return JellyCubeSquishColour(from: c.from, to: c.to, fill: J.easeInOut(J.seg(t, c.start, c.start + 1)), roll: J.easeInOut(J.seg(t, c.start, c.start + 0.3)))
    }

    private func soften(_ k: Double) -> JellyCubeSquishDeform {
        var d = JellyCubeSquishDeform()
        d.sq = soft.sq.x * k
        d.lx = soft.lx.x * k
        d.ly = soft.ly.x * k
        d.dent = soft.dent.x * k
        d.px = soft.p.x
        d.py = soft.p.y
        d.pz = soft.p.z
        d.nx = soft.n.x
        d.ny = soft.n.y
        d.nz = soft.n.z
        d.sx = soft.sx.x * k
        d.sy = soft.sy.x * k
        d.sz = soft.sz.x * k
        return d
    }

    func step(_ date: Date, auto: Bool, reduced: Bool, g: JellyCubeSquishGeo) -> JellyCubeSquishScene {
        let t1 = date.timeIntervalSinceReferenceDate
        let raw = last.map { t1 - $0 } ?? 0
        let fresh = last == nil || raw > 0
        last = t1
        guard fresh else { return scene }
        advance(t1, raw: raw, auto: auto, reduced: reduced)
        JellyCubeSquishArt.build(mesh, g, scene.d, scene.colour, time: scene.time)
        return scene
    }

    private func advance(_ t1: Double, raw: Double, auto: Bool, reduced: Bool) {
        let dt = min(max(raw, 0), 0.05)
        if wasAuto != auto {
            wasAuto = auto
            mode = 0
            loopT = auto ? 0 : -1
        }
        settled = false
        if mode == 0 && loopT >= 0 {
            // A frame that stalls pauses the loop instead of skipping ahead.
            if raw > 0 && raw <= 0.25 { loopT = (loopT + raw).truncatingRemainder(dividingBy: A.loop) }
            let t = loopT
            let d = A.deform(t)
            let f = A.finger(t, d)
            scene = JellyCubeSquishScene(d: d, colour: A.colour(t), finger: f.0, fingerAlpha: f.1, time: t)
            return
        }
        soft.sq.step(13, 0.12, dt, reduced: reduced)
        soft.lx.step(13, 0.12, dt, reduced: reduced)
        soft.ly.step(13, 0.12, dt, reduced: reduced)
        soft.dent.step(20, 0.25, dt, reduced: reduced)
        soft.sx.step(12, 0.15, dt, reduced: reduced)
        soft.sy.step(12, 0.15, dt, reduced: reduced)
        soft.sz.step(12, 0.15, dt, reduced: reduced)
        var k = 1.0
        if mode == 2 {
            k = 1 - J.easeInOut(J.seg(t1, settleAt, settleAt + Self.settle))
            let c = morph(t1, reduced: reduced)
            if k <= 0 && ((c.to == 0 && c.fill >= 1) || (c.from == c.to && c.to == 0)) {
                soft = Soft()
                mode = 0
                loopT = 0
            }
        } else if mode == 1 && auto && touching == 0 && t1 - lastTouch > Self.idle {
            mode = 2
            settleAt = t1
            let c = morph(t1, reduced: reduced)
            let shown = c.fill >= 0.5 ? c.to : c.from
            if shown != 0 { soft.colour = (shown, 0, t1) }
        }
        let colour = morph(t1, reduced: reduced)
        scene = JellyCubeSquishScene(d: soften(k), colour: colour, finger: .zero, fingerAlpha: 0,
                                     time: (t1.truncatingRemainder(dividingBy: A.loop) + A.loop).truncatingRemainder(dividingBy: A.loop))
        let s = soft
        settled = !auto && touching != 1 && mode != 2 && s.sq.still && s.lx.still && s.ly.still && s.dent.still
            && s.sx.still && s.sy.still && s.sz.still && (colour.from == colour.to || colour.fill >= 1)
    }

    /// Hands the cube to the finger, keeping the shape and colour the loop had.
    private func takeOver() {
        if mode != 1 {
            let d = scene.d
            let c = scene.colour
            let shown = c.from == c.to ? c.from : c.fill >= 0.5 ? c.to : c.from
            var s = Soft()
            s.sq.x = d.sq
            s.lx.x = d.lx
            s.ly.x = d.ly
            s.dent.x = d.dent
            s.sx.x = d.sx
            s.sy.x = d.sy
            s.sz.x = d.sz
            s.p = JellyCubeSquishV3(d.px, d.py, d.pz)
            s.n = JellyCubeSquishV3(d.nx, d.ny, d.nz)
            s.colour = (shown, shown, -10)
            soft = s
        }
        mode = 1
        loopT = -1
        lastTouch = now
    }

    func drag(_ v: DragGesture.Value, g: JellyCubeSquishGeo) {
        let half = J.half
        if !touchStarted {
            touchStarted = true
            let e = v.startLocation
            if e.y > g.nameBase - 34 || e.y < g.top + 100 {
                touching = -1
                return
            }
            takeOver()
            touching = 1
            // Where the finger meets the cube: on the plane facing the camera through its centre,
            // moved toward the eye and clamped onto the box; the dent points into the face it hit.
            let c = g.project(JellyCubeSquishV3(0, 0, half))
            let dx = Double((e.x - c.x) / c.s)
            let dy = Double((e.y - c.y) / c.s)
            let r = J.right, u = J.up, eye = J.toEye
            let P = JellyCubeSquishV3(r.x * dx - u.x * dy + eye.x * half * 1.3,
                                      r.y * dx - u.y * dy + eye.y * half * 1.3,
                                      half - u.z * dy + eye.z * half * 1.3)
            let q = JellyCubeSquishV3(J.clamp(P.x, -half, half), J.clamp(P.y, -half, half), J.clamp(P.z, 0, 2 * half))
            let out = [abs(q.x) / half, abs(q.y) / half, abs(q.z - half) / half]
            var axis = 0
            if out[1] > out[axis] { axis = 1 }
            if out[2] > out[axis] { axis = 2 }
            var nrm = JellyCubeSquishV3(0, 0, 0)
            nrm[axis] = axis == 2 ? (q.z > half ? -1 : 1) : (q[axis] > 0 ? -1 : 1)
            soft.p = q
            soft.n = nrm
            soft.dent.target = 26
            soft.sq.target = 0.06
            pokeFeel.impactOccurred()
            return
        }
        guard touching >= 0 else { return }
        lastTouch = now
        let c = g.project(JellyCubeSquishV3(0, 0, half))
        let tx = Double(v.translation.width / c.s)
        let ty = Double(v.translation.height / c.s)
        let len = hypot(tx, ty)
        if len < 8 { return }
        // Pull: the drag in the camera's plane, rubber-banded to 64 pt.
        let k = 64 * tanh(len / 64) / len
        let r = J.right, u = J.up
        soft.dent.target = 0
        soft.sq.target = -0.03
        soft.sx.target = (r.x * tx - u.x * ty) * k
        soft.sy.target = (r.y * tx - u.y * ty) * k
        soft.sz.target = -u.z * ty * k
    }

    func release() {
        defer { touchStarted = false }
        guard touching >= 0 else { return }
        touching = 0
        lastTouch = now
        let s = soft
        let pull = (s.sx.x * s.sx.x + s.sy.x * s.sy.x + s.sz.x * s.sz.x).squareRoot()
        // Let go: everything springs home; the pull kicks the wobble modes.
        soft.dent.target = 0
        soft.sq.v += 0.03 * pull
        soft.sq.target = 0
        soft.lx.v -= s.sx.x * 3
        soft.lx.target = 0
        soft.ly.v += -s.sy.x * 3 + (pull < 4 ? 40 * s.n.y : 0)
        soft.ly.target = 0
        soft.sx.target = 0
        soft.sy.target = 0
        soft.sz.target = 0
        releaseFeel.impactOccurred()
    }

    func pick(_ i: Int, reduced: Bool) {
        pickFeel.selectionChanged()
        takeOver()
        let c = morph(now, reduced: reduced)
        let shown = c.from == c.to ? c.from : c.fill >= 0.5 ? c.to : c.from
        if shown == i { return }
        soft.colour = (shown, i, now)
        soft.sq.v += 1.2
    }
}
