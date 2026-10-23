import SwiftUI
import UIKit

/*
 Spatial Orbit Mixer — five instruments as glowing orbs on rings round a listener's head.

 Look: deep indigo-black (#07070F) with a vignette and still star dust. The floor is seen from
 40° above (rings 72, 112 and 152 pt, white 18 % hairlines; ticks every 10° and 0 · 90 · 180 ·
 270 on the outer one; two dashed arches over the head), scaled to the screen's width. In the
 middle a listener seen from behind (a grey head on shoulders, dark over-ear headphones with
 lime accents) on a soft shadow and a lime under-glow.
 Five sources orbit: Vocals (lime #B6F23A, inner ring), Keys (violet #A98BFF) and Guitar (amber
 #FFC247) on the middle ring, Drums (coral #FF6A5C) and Bass (cyan #35D6FF) outside. Each orb
 is a lit sphere with a halo, a white pictogram, a ring that pulses out on its beat, a comet
 tail of 20 dots (where it was over the last 0.8 s), a stem down to a dot on the floor and a
 faint wedge of light towards the head. Above: "Spatial Mix" and "Head tracking on"; below: a
 segmented control (Stereo, Spatial, Fixed), five level bars, a play button and the track.

 Motion (SpatialOrbitMixerModel.swift): every source turns a whole number of times per loop
 (Keys twice, Vocals and Guitar once, Bass once the other way; Drums sway ±12°), bobs ±6 pt
 and pulses (radius +25 %, a ring out to 2.6×) on its own beat; the orbs are drawn back to front
 around the head.

 autoPlay timeline (loop = 10 s):
   0.0 – 1.2   idle orbits.
   1.0 – 3.6   a finger (a soft white disc) takes the Drums (it grows 30 %, its wedge lights, a
               readout shows level and direction), drags it from the outer ring to the inner ring
               on the left (1.3 – 2.9, ease in-out), holds, and lets go at 3.3.
   4.0 – 5.6   the view turns 25° and back, as if the phone turned.
   6.0 – 6.6   STEREO: every orb slides onto a line in front of the head (ease-out-back).
   7.4 – 8.0   SPATIAL again: they spring back out to their orbits (the Drums to its own ring).
   8.0 – 10.0  idle; the orbits have made whole turns, so 10.0 is the frame at 0.0.

 Touch: drag an orb across the floor (it grows, its wedge lights and a readout follows it, a
 selection haptic every 15° round the head); dropped within 12 pt of a ring it settles onto it
 (0.25 s) and orbits from there. Drag the empty floor to turn the view (0.35° per point) and
 tilt it (24° – 56°); Fixed stops the turning. Tap Stereo, Spatial or Fixed. 2 s after the last
 touch everything eases back over 1 s and the loop starts again.
 Reduced motion: no loop, orbits, pulses or tails; the sources rest on their rings. Dragging and
 the segments still work.
 */

struct SpatialOrbitMixer: View {
    var autoPlay = false

    static let loop = SpatialOrbitMixerModel.loop
    typealias M = SpatialOrbitMixerModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var origin = Date.now
    /// On the block's clock: when the loop starts, and when the ease back to it began.
    @State private var autoStart: Double? = nil
    @State private var returnStart: Double? = nil
    @State private var manual = M.Manual()
    @State private var grab: (orb: Int, dx: Double, dz: Double, yaw: Double, elevation: Double)? = nil
    @State private var lastTick = 0
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UISelectionFeedbackGenerator()

    static func rgb(_ hex: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: a)
    }

    struct Layout {
        var w: Double, h: Double, top: Double
        var cx: Double, cy: Double, scale: Double, segY: Double, rowY: Double, sceneTop: Double
        init(size: CGSize, insets: EdgeInsets) {
            w = size.width; h = size.height; top = insets.top
            let bottom = max(insets.bottom, 16)
            rowY = h - bottom - 30
            segY = rowY - 70
            sceneTop = top + 76
            cx = w / 2
            cy = (sceneTop + segY - 30) / 2 + 16
            scale = min(1.2, (w - 30) / 344)
        }
        func view(_ s: M.Scene) -> M.View { M.View(cx: cx, cy: cy, scale: scale, yaw: s.yaw, elevation: s.elevation) }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let l = Layout(size: size, insets: insets)
            ZStack(alignment: .topLeading) {
                // The backdrop never changes, so it is drawn once under the animated layer.
                Canvas { ctx, _ in Self.backdrop(&ctx, l) }
                    .frame(width: size.width, height: size.height)
                    .accessibilityHidden(true)
                TimelineView(.animation(paused: reduceMotion && grab == nil)) { timeline in
                    let t = timeline.date.timeIntervalSince(origin)
                    Canvas { ctx, _ in draw(&ctx, l, t) }
                        .frame(width: size.width, height: size.height)
                }
                .accessibilityHidden(true)
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: size.width, height: max(0, l.segY - 30 - l.sceneTop))
                    .offset(y: l.sceneTop)
                    .gesture(drag(l))
                    .accessibilityLabel("Spatial mix: five sources round the listener")
                segments(l)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            haptic.prepare()
            origin = .now
            if autoPlay && !reduceMotion { autoStart = 0 }
        }
        .onDisappear { resume?.cancel() }
    }

    // MARK: Time

    private func now() -> Double { Date.now.timeIntervalSince(origin) }

    /// The scene at a clock time, and the clock its beats and bobs follow.
    private func scene(at t: Double) -> (M.Scene, Double) {
        if reduceMotion { return (M.scene(manual, at: t), 0) }
        if let a = autoStart {
            let lt = M.posmod(t - a, Self.loop)
            if t >= a { return (M.auto(lt), lt) }
            if let rs = returnStart {
                return (M.mix(M.scene(manual, at: t), M.auto(lt), M.easeInOut((t - rs) / (a - rs))), lt)
            }
        }
        return (M.scene(manual, at: t), t - manual.anchor)
    }

    /// Freezes whatever plays into the touch scene.
    private func takeOver(_ t: Double) {
        resume?.cancel()
        guard let a = autoStart else { return }
        let (sc, _) = scene(at: t)
        var m = M.Manual()
        m.anchor = a
        for i in 0..<5 {
            let o = sc.orbs[i]
            m.radius[i] = o.r
            m.snapFrom[i] = o.r
            m.offset[i] = o.a - M.idleAngle(i, t - a)
        }
        m.yaw = sc.yaw
        m.elevation = sc.elevation
        m.mode = sc.segment < 0.5 ? 0 : 1
        m.stereoFrom = sc.stereo
        m.stereoTo = m.mode == 0 ? 1 : 0
        m.segmentFrom = sc.segment
        m.segmentTo = m.mode == 0 ? 0 : 1
        m.modeAt = t
        if sc.held >= 0 {
            // The loop's finger lets go.
            let i = sc.held
            m.radius[i] = Self.ring(near: m.radius[i])
            m.snapAt[i] = t
            m.dropped = i
            m.droppedAt = t
        }
        manual = m
        autoStart = nil
        returnStart = nil
    }

    static func ring(near r: Double) -> Double {
        let best = M.rings.min { abs($0 - r) < abs($1 - r) } ?? r
        return abs(best - r) <= 12 ? best : r
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let t = now()
            returnStart = t
            autoStart = t + 1
        }
    }

    private func drag(_ l: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { g in
                let t = now()
                let loc = CGPoint(x: g.location.x, y: g.location.y + l.sceneTop)
                if grab == nil {
                    takeOver(t)
                    let (sc, _) = scene(at: t)
                    let v = l.view(sc)
                    var best = -1, bestD = 34.0
                    for i in 0..<5 {
                        let o = sc.orbs[i]
                        let p = M.project(v, x: o.x, z: o.z, h: o.h)
                        let d = hypot(p.X - loc.x, p.Y - loc.y)
                        if d < bestD { best = i; bestD = d }
                    }
                    if best >= 0 {
                        let f = M.unproject(v, X: loc.x, Y: loc.y)
                        let o = sc.orbs[best]
                        grab = (best, o.x - f.x, o.z - f.z, sc.yaw, sc.elevation)
                        manual.held = best
                        manual.heldX = o.x
                        manual.heldZ = o.z
                        manual.heldAt = t
                        lastTick = Int(floor(M.unpolar(o.x, o.z).a / 15))
                        haptic.selectionChanged()
                    } else {
                        grab = (-1, 0, 0, sc.yaw, sc.elevation)
                    }
                }
                guard let gr = grab else { return }
                if gr.orb >= 0 {
                    let (sc, _) = scene(at: t)
                    let f = M.unproject(l.view(sc), X: loc.x, Y: loc.y)
                    var x = f.x + gr.dx, z = f.z + gr.dz
                    let r = hypot(x, z)
                    if r > 175 { x *= 175 / r; z *= 175 / r }
                    manual.heldX = x
                    manual.heldZ = z
                    let tick = Int(floor(M.unpolar(x, z).a / 15))
                    if tick != lastTick {
                        lastTick = tick
                        haptic.selectionChanged()
                    }
                } else {
                    if manual.mode != 2 {
                        manual.yaw = gr.yaw + g.translation.width * 0.35
                        manual.yawAt = -100
                    }
                    manual.elevation = min(max(gr.elevation - g.translation.height * 0.12, 24), 56)
                }
            }
            .onEnded { _ in
                let t = now()
                if let gr = grab, gr.orb >= 0 {
                    let i = gr.orb
                    let (r, a) = M.unpolar(manual.heldX, manual.heldZ)
                    manual.radius[i] = Self.ring(near: r)
                    manual.snapFrom[i] = r
                    manual.snapAt[i] = t
                    manual.offset[i] = a - M.idleAngle(i, t - manual.anchor)
                    manual.held = -1
                    manual.dropped = i
                    manual.droppedAt = t
                }
                grab = nil
                scheduleResume()
            }
    }

    private func segments(_ l: Layout) -> some View {
        let w = l.w - 80
        return HStack(spacing: 0) {
            ForEach(0..<3, id: \.self) { i in
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: w / 3, height: 40)
                    .onTapGesture { choose(i) }
                    .accessibilityElement()
                    .accessibilityLabel(M.modes[i])
                    .accessibilityAddTraits(.isButton)
            }
        }
        .offset(x: 40, y: l.segY - 20)
    }

    private func choose(_ mode: Int) {
        let t = now()
        takeOver(t)
        let (sc, _) = scene(at: t)
        haptic.selectionChanged()
        manual.mode = mode
        manual.stereoFrom = sc.stereo
        manual.stereoTo = mode == 0 ? 1 : 0
        manual.segmentFrom = sc.segment
        manual.segmentTo = Double(mode)
        manual.modeAt = t
        if mode == 2 {
            manual.yawFrom = sc.yaw
            manual.yaw = 0
            manual.yawAt = t
        }
        scheduleResume()
    }

    // MARK: Drawing

    private func draw(_ ctx: inout GraphicsContext, _ l: Layout, _ t: Double) {
        let (sc, clock) = scene(at: t)
        let v = l.view(sc)

        // Header: whether head tracking is on (the rest of it is in the backdrop).
        let lime = Self.rgb(0xB6F23A)
        ctx.fill(Path(ellipseIn: CGRect(x: 24, y: l.top + 48, width: 6, height: 6)), with: .color(sc.tracking ? lime : Self.rgb(0x5A5A70)))
        ctx.draw(Text(sc.tracking ? "Head tracking on" : "Head tracking off").font(.system(size: 11, weight: .medium)).foregroundColor(Self.rgb(0x8A8AA0)),
                 at: CGPoint(x: 36, y: l.top + 51), anchor: .leading)

        // The floor: a soft glow, three rings, ticks and bearings on the outer one.
        let floorCenter = M.project(v, x: 0, z: 0, h: 0)
        let e = sc.elevation * .pi / 180
        ctx.fill(Path(ellipseIn: CGRect(x: floorCenter.X - 170 * l.scale, y: floorCenter.Y - 170 * l.scale * sin(e),
                                        width: 340 * l.scale, height: 340 * l.scale * sin(e))),
                 with: .radialGradient(Gradient(colors: [Self.rgb(0x6C6CFF, 0.08), Self.rgb(0x6C6CFF, 0)]),
                                       center: CGPoint(x: floorCenter.X, y: floorCenter.Y), startRadius: 0, endRadius: 170 * l.scale))
        for r in M.rings {
            var ring = Path()
            for k in 0...72 {
                let (x, z) = M.polar(r, Double(k) * 5)
                let p = M.project(v, x: x, z: z, h: 0)
                if k == 0 { ring.move(to: CGPoint(x: p.X, y: p.Y)) } else { ring.addLine(to: CGPoint(x: p.X, y: p.Y)) }
            }
            ctx.stroke(ring, with: .color(.white.opacity(0.18)), lineWidth: 1)
        }
        // Two faint arches over the head (front to back, left to right) give the space its height.
        for side in [0, 1] {
            var arch = Path()
            for k in 0...48 {
                let phi = Double(k) / 48 * .pi
                let p = side == 0 ? M.project(v, x: 0, z: 152 * cos(phi), h: 152 * sin(phi))
                                  : M.project(v, x: 152 * cos(phi), z: 0, h: 152 * sin(phi))
                if k == 0 { arch.move(to: CGPoint(x: p.X, y: p.Y)) } else { arch.addLine(to: CGPoint(x: p.X, y: p.Y)) }
            }
            ctx.stroke(arch, with: .color(.white.opacity(0.16)), style: StrokeStyle(lineWidth: 1, dash: [2, 5]))
        }
        var ticks = Path()
        for k in 0..<36 {
            let a = Double(k) * 10
            let (x0, z0) = M.polar(152, a), (x1, z1) = M.polar(k % 3 == 0 ? 159 : 156, a)
            let p0 = M.project(v, x: x0, z: z0, h: 0), p1 = M.project(v, x: x1, z: z1, h: 0)
            ticks.move(to: CGPoint(x: p0.X, y: p0.Y)); ticks.addLine(to: CGPoint(x: p1.X, y: p1.Y))
        }
        ctx.stroke(ticks, with: .color(.white.opacity(0.22)), lineWidth: 0.8)
        for a in [0.0, 90, 180, 270] {
            let (x, z) = M.polar(172, a)
            let p = M.project(v, x: x, z: z, h: 0)
            ctx.draw(Text("\(Int(a))").font(.system(size: 8, weight: .medium, design: .monospaced)).foregroundColor(.white.opacity(0.4)),
                     at: CGPoint(x: p.X, y: p.Y))
        }

        // Stems, floor dots and the wedges of light towards the head.
        let headFloor = CGPoint(x: floorCenter.X, y: floorCenter.Y)
        let headCenter = M.project(v, x: 0, z: 0, h: 40)
        for i in 0..<5 {
            let o = sc.orbs[i]
            let p = M.project(v, x: o.x, z: o.z, h: o.h), g = M.project(v, x: o.x, z: o.z, h: 0)
            var stem = Path()
            stem.move(to: CGPoint(x: p.X, y: p.Y)); stem.addLine(to: CGPoint(x: g.X, y: g.Y))
            ctx.stroke(stem, with: .color(.white.opacity(0.15)), lineWidth: 0.7)
            ctx.fill(Path(ellipseIn: CGRect(x: g.X - 2, y: g.Y - 1.2, width: 4, height: 2.4)), with: .color(Self.rgb(M.sources[i].color, 0.6)))
            let s = orbSize(o, depth: p.depth, l: l, pulse: 0)
            let dx = p.X - headCenter.X, dy = p.Y - headCenter.Y, len = max(hypot(dx, dy), 1)
            let nx = -dy / len * s * 1.1, ny = dx / len * s * 1.1
            var wedge = Path()
            wedge.move(to: CGPoint(x: headCenter.X, y: headCenter.Y))
            wedge.addLine(to: CGPoint(x: p.X + nx, y: p.Y + ny))
            wedge.addLine(to: CGPoint(x: p.X - nx, y: p.Y - ny))
            wedge.closeSubpath()
            ctx.fill(wedge, with: .color((o.lit > 0.01 ? Self.rgb(M.sources[i].color) : .white).opacity(0.035 + 0.12 * o.lit)))
        }

        // Tails: where each orb was over the last 0.8 s.
        if !reduceMotion {
            for k in stride(from: 20, through: 1, by: -1) {
                let (past, _) = scene(at: t - Double(k) * 0.04)
                let pv = l.view(past)
                for i in 0..<5 where past.held != i || sc.held != i {
                    let o = past.orbs[i]
                    let p = M.project(pv, x: o.x, z: o.z, h: o.h)
                    let f = 1 - Double(k) / 21
                    let r = 3.2 * l.scale * f
                    ctx.fill(Path(ellipseIn: CGRect(x: p.X - r, y: p.Y - r, width: 2 * r, height: 2 * r)),
                             with: .color(Self.rgb(M.sources[i].color, 0.45 * f)))
                }
            }
        }

        // Orbs behind the head, the head, then the orbs in front.
        let order = (0..<5).map { i -> (Int, Double) in
            let o = sc.orbs[i]
            return (i, M.project(v, x: o.x, z: o.z, h: o.h).depth)
        }.sorted { $0.1 < $1.1 }
        for (i, d) in order where d < 0 { orb(&ctx, i, sc, v, l, clock) }
        head(&ctx, v, headFloor: headFloor, yaw: sc.yaw, l: l)
        for (i, d) in order where d >= 0 { orb(&ctx, i, sc, v, l, clock) }

        // The readout beside a held orb, and the loop's finger.
        if sc.held >= 0 && sc.chip > 0.01 {
            let o = sc.orbs[sc.held]
            let p = M.project(v, x: o.x, z: o.z, h: o.h)
            let text = ctx.resolve(Text(M.readout(x: o.x, z: o.z)).font(.system(size: 11, weight: .medium, design: .monospaced))
                .foregroundColor(.white.opacity(sc.chip)))
            let ts = text.measure(in: CGSize(width: 300, height: 40))
            let box = CGRect(x: p.X - (ts.width + 20) / 2, y: p.Y - 52 * l.scale - 26, width: ts.width + 20, height: 26)
            ctx.fill(Path(roundedRect: box, cornerRadius: 13), with: .color(Self.rgb(0x15152A, 0.92 * sc.chip)))
            ctx.stroke(Path(roundedRect: box, cornerRadius: 13), with: .color(Self.rgb(M.sources[sc.held].color, 0.6 * sc.chip)), lineWidth: 1)
            ctx.draw(text, at: CGPoint(x: box.midX, y: box.midY))
        }
        if sc.finger > 0.01 {
            let o = sc.orbs[3]
            let p = M.project(v, x: o.x, z: o.z, h: o.h)
            let r = 22.0
            let c = CGRect(x: p.X - r + 6, y: p.Y - r + 10, width: 2 * r, height: 2 * r)
            ctx.fill(Path(ellipseIn: c), with: .color(.white.opacity(0.16 * sc.finger)))
            ctx.stroke(Path(ellipseIn: c), with: .color(.white.opacity(0.5 * sc.finger)), lineWidth: 1.5)
        }

        // The segmented control.
        let segW = l.w - 80
        let seg = CGRect(x: 40, y: l.segY - 20, width: segW, height: 40)
        ctx.fill(Path(roundedRect: seg, cornerRadius: 20), with: .color(.white.opacity(0.07)))
        let ix = seg.minX + 3 + (segW - 6) / 3 * min(max(sc.segment, 0), 2)
        ctx.fill(Path(roundedRect: CGRect(x: ix, y: seg.minY + 3, width: (segW - 6) / 3, height: 34), cornerRadius: 17),
                 with: .color(.white.opacity(0.14)))
        for (i, name) in M.modes.enumerated() {
            let on = max(0, 1 - abs(sc.segment - Double(i)))
            ctx.draw(Text(name).font(.system(size: 13, weight: .medium)).foregroundColor(.white.opacity(0.5 + 0.5 * on)),
                     at: CGPoint(x: seg.minX + segW / 6 * Double(2 * i + 1), y: seg.midY))
        }

        // Levels, play, and the track.
        for i in 0..<5 {
            let lv = reduceMotion ? 0.5 : 0.3 + 0.7 * M.beat(i, clock).pulse
            let bh = 4 + 24 * lv
            ctx.fill(Path(roundedRect: CGRect(x: 24 + Double(i) * 10, y: l.rowY + 14 - bh, width: 5, height: bh), cornerRadius: 2.5),
                     with: .color(Self.rgb(M.sources[i].color)))
        }
        ctx.fill(Path(ellipseIn: CGRect(x: l.w / 2 - 26, y: l.rowY - 26, width: 52, height: 52)), with: .color(.white))
        ctx.fill(Path(roundedRect: CGRect(x: l.w / 2 - 7, y: l.rowY - 8, width: 5, height: 16), cornerRadius: 1.5), with: .color(Self.rgb(0x07070F)))
        ctx.fill(Path(roundedRect: CGRect(x: l.w / 2 + 2, y: l.rowY - 8, width: 5, height: 16), cornerRadius: 1.5), with: .color(Self.rgb(0x07070F)))
        ctx.draw(Text("Low Orbit").font(.system(size: 13, weight: .semibold)).foregroundColor(.white),
                 at: CGPoint(x: l.w - 24, y: l.rowY - 8), anchor: .trailing)
        ctx.draw(Text("Kaito Vale").font(.system(size: 11)).foregroundColor(Self.rgb(0x8A8AA0)),
                 at: CGPoint(x: l.w - 24, y: l.rowY + 9), anchor: .trailing)
    }

    /// What never moves: the background, its glow, the star dust, the vignette, the title and the chip.
    private static func backdrop(_ ctx: inout GraphicsContext, _ l: Layout) {
        let full = Path(CGRect(x: 0, y: 0, width: l.w, height: l.h))
        ctx.fill(full, with: .color(rgb(0x07070F)))
        ctx.fill(full, with: .radialGradient(Gradient(colors: [rgb(0x1A1A3A, 0.55), rgb(0x1A1A3A, 0)]),
                                             center: CGPoint(x: l.cx, y: l.cy), startRadius: 0, endRadius: l.w * 0.8))
        // Star dust.
        for i in 0..<70 {
            let fx = (Double(i) * 0.618034 + 0.11).truncatingRemainder(dividingBy: 1)
            let fy = (Double(i) * 0.754877 + 0.37).truncatingRemainder(dividingBy: 1)
            let r = i % 7 == 0 ? 1.1 : 0.6
            ctx.fill(Path(ellipseIn: CGRect(x: fx * l.w - r, y: fy * l.h - r, width: 2 * r, height: 2 * r)),
                     with: .color(.white.opacity(0.12 + 0.3 * Double(i % 5) / 4)))
        }
        ctx.fill(full, with: .radialGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.6)]),
                                             center: CGPoint(x: l.w / 2, y: l.h / 2), startRadius: l.h * 0.3, endRadius: l.h * 0.75))
        ctx.draw(Text("Spatial Mix").font(.system(size: 22, weight: .semibold)).foregroundColor(.white),
                 at: CGPoint(x: 24, y: l.top + 26), anchor: .leading)
        let chip = CGRect(x: l.w - 24 - 72, y: l.top + 14, width: 72, height: 26)
        ctx.stroke(Path(roundedRect: chip, cornerRadius: 13), with: .color(.white.opacity(0.18)), lineWidth: 1)
        ctx.draw(Text("5 STEMS").font(.system(size: 10, weight: .semibold)).tracking(1.2).foregroundColor(.white.opacity(0.7)),
                 at: CGPoint(x: chip.midX, y: chip.midY))
    }

    private func orbSize(_ o: M.Orb, depth: Double, l: Layout, pulse: Double) -> Double {
        15 * l.scale * o.grow * (1 + 0.15 * depth / 152) * (1 + 0.25 * pulse)
    }

    private func orb(_ ctx: inout GraphicsContext, _ i: Int, _ sc: M.Scene, _ v: M.View, _ l: Layout, _ clock: Double) {
        let o = sc.orbs[i]
        let p = M.project(v, x: o.x, z: o.z, h: o.h)
        let b = reduceMotion ? (pulse: 0.0, phase: 0.5) : M.beat(i, clock)
        let s = orbSize(o, depth: p.depth, l: l, pulse: b.pulse)
        let c = CGPoint(x: p.X, y: p.Y)
        let color = Self.rgb(M.sources[i].color)
        ctx.drawLayer { g in
            g.addFilter(.blur(radius: 8))
            g.fill(Path(ellipseIn: CGRect(x: c.x - 2.2 * s, y: c.y - 2.2 * s, width: 4.4 * s, height: 4.4 * s)),
                   with: .color(color.opacity(0.3 + 0.25 * o.lit)))
        }
        if !reduceMotion {
            let pr = s * (1 + 1.6 * b.phase)
            ctx.stroke(Path(ellipseIn: CGRect(x: c.x - pr, y: c.y - pr, width: 2 * pr, height: 2 * pr)),
                       with: .color(color.opacity(0.5 * (1 - b.phase))), lineWidth: 1.5)
        }
        ctx.fill(Path(ellipseIn: CGRect(x: c.x - s, y: c.y - s, width: 2 * s, height: 2 * s)),
                 with: .radialGradient(Gradient(stops: [
                    .init(color: .white.opacity(0.95), location: 0),
                    .init(color: color, location: 0.45),
                    .init(color: Self.rgb(M.sources[i].color).opacity(0.85), location: 1),
                 ]), center: CGPoint(x: c.x - 0.3 * s, y: c.y - 0.35 * s), startRadius: 0, endRadius: 1.3 * s))
        // The pictogram.
        let k = s / 15
        var gl = Path()
        switch i {
        case 0: // a microphone
            gl.addRoundedRect(in: CGRect(x: c.x - 2.5 * k, y: c.y - 6 * k, width: 5 * k, height: 8 * k), cornerSize: CGSize(width: 2.5 * k, height: 2.5 * k))
            gl.move(to: CGPoint(x: c.x, y: c.y + 2 * k)); gl.addLine(to: CGPoint(x: c.x, y: c.y + 6 * k))
        case 1: // keys
            for d in [-3.5, 0, 3.5] { gl.addRoundedRect(in: CGRect(x: c.x + d * k - 1.2 * k, y: c.y - 5 * k, width: 2.4 * k, height: 10 * k), cornerSize: CGSize(width: 0.8 * k, height: 0.8 * k)) }
        case 2: // a pick
            gl.move(to: CGPoint(x: c.x - 4.5 * k, y: c.y - 4 * k))
            gl.addQuadCurve(to: CGPoint(x: c.x + 4.5 * k, y: c.y - 4 * k), control: CGPoint(x: c.x, y: c.y - 7 * k))
            gl.addQuadCurve(to: CGPoint(x: c.x, y: c.y + 6 * k), control: CGPoint(x: c.x + 3 * k, y: c.y + 2 * k))
            gl.addQuadCurve(to: CGPoint(x: c.x - 4.5 * k, y: c.y - 4 * k), control: CGPoint(x: c.x - 3 * k, y: c.y + 2 * k))
        case 3: // a drum and sticks
            gl.addEllipse(in: CGRect(x: c.x - 4.5 * k, y: c.y - 2 * k, width: 9 * k, height: 7 * k))
            gl.move(to: CGPoint(x: c.x - 5 * k, y: c.y - 6 * k)); gl.addLine(to: CGPoint(x: c.x - 1 * k, y: c.y - 1 * k))
            gl.move(to: CGPoint(x: c.x + 5 * k, y: c.y - 6 * k)); gl.addLine(to: CGPoint(x: c.x + 1 * k, y: c.y - 1 * k))
        default: // a waveform
            for (d, hh) in [(-4.0, 3.0), (-1.3, 6.0), (1.3, 4.5), (4.0, 2.0)] {
                gl.move(to: CGPoint(x: c.x + d * k, y: c.y - hh * k)); gl.addLine(to: CGPoint(x: c.x + d * k, y: c.y + hh * k))
            }
        }
        ctx.stroke(gl, with: .color(.white.opacity(0.95)), style: StrokeStyle(lineWidth: 1.3 * k, lineCap: .round, lineJoin: .round))
    }

    /// The listener: a grey egg with over-ear headphones, the far cup behind it and the near one in front.
    private func head(_ ctx: inout GraphicsContext, _ v: M.View, headFloor: CGPoint, yaw: Double, l: Layout) {
        let k = l.scale
        ctx.drawLayer { g in
            g.addFilter(.blur(radius: 6))
            g.fill(Path(ellipseIn: CGRect(x: headFloor.x - 30 * k, y: headFloor.y - 9 * k, width: 60 * k, height: 18 * k)), with: .color(.black.opacity(0.7)))
            g.fill(Path(ellipseIn: CGRect(x: headFloor.x - 40 * k, y: headFloor.y - 12 * k, width: 80 * k, height: 24 * k)), with: .color(Self.rgb(0xB6F23A, 0.12)))
        }
        let c = M.project(v, x: 0, z: 0, h: 40)
        let center = CGPoint(x: c.X, y: c.Y)
        let cups = [-1.0, 1.0].map { side -> (CGPoint, Double) in
            let p = M.project(v, x: 22 * side, z: 0, h: 38)
            return (CGPoint(x: p.X, y: p.Y), p.depth)
        }
        func cup(_ p: CGPoint) {
            let r = CGRect(x: p.x - 7 * k, y: p.y - 12 * k, width: 14 * k, height: 24 * k)
            ctx.fill(Path(roundedRect: r, cornerRadius: 7 * k), with: .linearGradient(Gradient(colors: [Self.rgb(0x34343F), Self.rgb(0x18181F)]),
                                                                                       startPoint: CGPoint(x: r.minX, y: r.minY), endPoint: CGPoint(x: r.maxX, y: r.maxY)))
            var accent = Path()
            accent.move(to: CGPoint(x: p.x, y: p.y - 7 * k)); accent.addLine(to: CGPoint(x: p.x, y: p.y + 7 * k))
            ctx.stroke(accent, with: .color(Self.rgb(0xB6F23A, 0.9)), style: StrokeStyle(lineWidth: 1.5 * k, lineCap: .round))
        }
        // Shoulders under the head.
        var bust = Path()
        bust.move(to: CGPoint(x: center.x - 38 * k, y: center.y + 38 * k))
        bust.addQuadCurve(to: CGPoint(x: center.x - 13 * k, y: center.y + 13 * k), control: CGPoint(x: center.x - 37 * k, y: center.y + 15 * k))
        bust.addLine(to: CGPoint(x: center.x + 13 * k, y: center.y + 13 * k))
        bust.addQuadCurve(to: CGPoint(x: center.x + 38 * k, y: center.y + 38 * k), control: CGPoint(x: center.x + 37 * k, y: center.y + 15 * k))
        bust.addQuadCurve(to: CGPoint(x: center.x - 38 * k, y: center.y + 38 * k), control: CGPoint(x: center.x, y: center.y + 44 * k))
        bust.closeSubpath()
        ctx.fill(bust, with: .linearGradient(Gradient(colors: [Self.rgb(0x5C5C6C), Self.rgb(0x26262F)]),
                                             startPoint: CGPoint(x: center.x, y: center.y + 12 * k), endPoint: CGPoint(x: center.x, y: center.y + 40 * k)))
        let far = cups[0].1 < cups[1].1 ? 0 : 1
        cup(cups[far].0)
        let egg = CGRect(x: center.x - 20 * k, y: center.y - 26 * k, width: 40 * k, height: 48 * k)
        ctx.fill(Path(ellipseIn: egg), with: .radialGradient(Gradient(colors: [Self.rgb(0xF1F1F5), Self.rgb(0xB9B9C6), Self.rgb(0x6E6E7E)]),
                                                             center: CGPoint(x: center.x - 7 * k, y: center.y - 12 * k), startRadius: 0, endRadius: 34 * k))
        // The headband over the top, from cup to cup.
        var band = Path()
        band.move(to: CGPoint(x: cups[0].0.x, y: cups[0].0.y - 10 * k))
        band.addQuadCurve(to: CGPoint(x: cups[1].0.x, y: cups[1].0.y - 10 * k), control: CGPoint(x: center.x, y: center.y - 50 * k))
        ctx.stroke(band, with: .color(Self.rgb(0x23232B)), style: StrokeStyle(lineWidth: 5 * k, lineCap: .round))
        ctx.stroke(band, with: .color(Self.rgb(0xB6F23A, 0.5)), style: StrokeStyle(lineWidth: 1 * k, lineCap: .round))
        cup(cups[1 - far].0)
    }
}
