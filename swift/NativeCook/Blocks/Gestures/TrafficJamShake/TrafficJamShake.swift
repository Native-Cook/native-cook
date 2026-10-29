import SwiftUI
import UIKit

// A commute card for an invented app, Laneway: "Heavy traffic on Ring Rd". Its hero is a flat pile
// of chunky extruded cars that wobble, lean, stretch and bump each other, each on its own spring
// (see TrafficJamShakeSim). Drag anywhere to shake the jam; tap a car to make it hop and honk.
//
// autoPlay timeline (TrafficJamShakeMotion.loop = 8 s), the simulation stepped from rest at t = 0:
//   0.00-0.75  rest (a 0.35 pt engine-idle tremble)
//   0.75-0.95  a ghost finger lands on the red front car
//   0.95       tap: the red car hops 28 pt and honks; it lands at ~1.25 s and bumps its neighbours
//   1.70-1.95  the ghost finger moves to the pile's centre
//   1.95-5.00  it shakes the ground left-right (0.66 s swings, up to 10 pt, smoothstep envelope
//              in over 1.95-2.6 s and out over 4.45-5.0 s); the finger fades out over 5.0-5.25 s
//   2.80-4.80  peak: cars lag, lean, stretch into limos, bump, honk and hop
//   5.00-6.40  the pile settles on its springs
//   6.40-7.20  what is left fades out (smoothstep), so the frame is exactly the rest frame
//   7.20-8.00  rest, identical to t = 0

enum TrafficJamShakePalette {
    static let background = Color(hex: 0xECEBE7)
    static let ink = Color(hex: 0x1B1B1F)
    static let outline = Color(hex: 0x1E1E24)
    static let shadow = Color(hex: 0x141418)
    static let window = Color(hex: 0x121216)
    static let light = Color(hex: 0xFFE01F)
    static let plate = Color(hex: 0xF4F1E8)
    static let card = Color.white
    static let red = Color(hex: 0xD7263D)
    static let redSoft = Color(hex: 0xFBE3E6)
    static let green = Color(hex: 0x22A447)
    static let orange = Color(hex: 0xF28A0C)
    static let jam = Color(hex: 0xE0102A)
    /// Per body colour: front, top (14 % white), side (12 % black). Pink, blue, green, orange, red.
    static let bodies: [(front: Color, top: Color, side: Color)] = [
        (Color(hex: 0xF38BF0), Color(hex: 0xF59BF2), Color(hex: 0xD67AD3)),
        (Color(hex: 0x139FE6), Color(hex: 0x34ACEA), Color(hex: 0x118CCA)),
        (Color(hex: 0x17A833), Color(hex: 0x37B450), Color(hex: 0x14942D)),
        (Color(hex: 0xF48C10), Color(hex: 0xF69C31), Color(hex: 0xD77B0E)),
        (Color(hex: 0xE1121F), Color(hex: 0xE5333E), Color(hex: 0xC6101B)),
    ]
}

/// Where the pile sits on screen: design point → screen point.
private struct TrafficJamShakeFrame {
    var origin: CGPoint
    var scale: Double

    func point(_ x: Double, _ y: Double) -> CGPoint {
        CGPoint(x: origin.x + x * scale, y: origin.y + y * scale)
    }

    /// The pile's bounds in design points (with the hard shadow).
    static let bounds: CGRect = {
        let M = TrafficJamShakeMotion.self
        var r = CGRect.null
        for c in M.cars {
            let top = c.y - c.hb - c.hc + 0.82 * c.l * M.dirY
            r = r.union(CGRect(x: c.x - c.w / 2, y: top, width: c.w + c.l * M.dirX, height: c.y + 6 - top))
        }
        return r
    }()

    /// Fits the pile in `region`: centred, bottom-aligned (room above for hops).
    init(region: CGRect) {
        let b = Self.bounds
        let s = min(1.1, (region.width - 24) / b.width, (region.height - 30) / b.height)
        scale = s
        origin = CGPoint(x: region.midX - b.midX * s, y: region.maxY - b.maxY * s)
    }
}

enum TrafficJamShakeDraw {
    private typealias M = TrafficJamShakeMotion
    private typealias P = TrafficJamShakePalette

    private static func poly(_ pts: [CGPoint]) -> Path {
        var p = Path()
        p.addLines(pts)
        p.closeSubpath()
        return p
    }

    private static func face(_ ctx: GraphicsContext, _ pts: [CGPoint], _ color: Color) {
        let p = poly(pts)
        ctx.fill(p, with: .color(color))
        ctx.stroke(p, with: .color(P.outline), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
    }

    /// The quad shrunk towards its centre by `k`.
    private static func inset(_ pts: [CGPoint], _ k: Double) -> [CGPoint] {
        let cx = pts.map(\.x).reduce(0, +) / Double(pts.count)
        let cy = pts.map(\.y).reduce(0, +) / Double(pts.count)
        return pts.map { CGPoint(x: cx + ($0.x - cx) * k, y: cy + ($0.y - cy) * k) }
    }

    /// One car in its own frame (the context is already at the ground point, turned by its angle).
    /// `e` stretches the width and squashes the heights; wheels and lights keep their size.
    static func car(_ ctx: GraphicsContext, _ c: TrafficJamShakeCar, e: Double, z: Double) {
        let colors = P.bodies[c.color]
        let w = c.w * (1 + e)
        let k = max(0.6, 1 - 0.6 * e)
        let hb = c.hb * k
        let hc = c.hc * k
        let dx = c.l * M.dirX
        let dy = c.l * M.dirY
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: x, y: y) }

        // The hard ground shadow stays on the ground and pales as the car lifts.
        let shadow = poly([pt(-w / 2, 6), pt(w / 2, 6), pt(w / 2 + dx, 6 + dy), pt(-w / 2 + dx, 6 + dy)])
        ctx.fill(shadow, with: .color(P.shadow.opacity(max(0.35, 1 - z / 50))))

        var body = ctx
        body.translateBy(x: 0, y: -z)
        let l = -w / 2, r = w / 2
        // Body side and top.
        face(body, [pt(r, 0), pt(r, -hb), pt(r + dx, -hb + dy), pt(r + dx, dy)], colors.side)
        // Wheels on the side's bottom edge.
        let rw = min(8, max(5, 0.3 * c.hb))
        for f in [0.22, 0.78] {
            let cx = r + f * dx
            let cy = f * dy - rw * 0.15
            body.fill(Path(ellipseIn: CGRect(x: cx - rw * 0.62, y: cy - rw, width: rw * 1.24, height: rw * 2)), with: .color(P.window))
            body.fill(Path(ellipseIn: CGRect(x: cx - rw * 0.3, y: cy - rw * 0.52, width: rw * 0.6, height: rw * 1.04)), with: .color(P.plate))
        }
        face(body, [pt(l, -hb), pt(r, -hb), pt(r + dx, -hb + dy), pt(l + dx, -hb + dy)], colors.top)

        // Cabin: set back 18-82 % of the length, narrower at the roof.
        let c0x = 0.18 * dx, c0y = 0.18 * dy
        let cdx = 0.64 * dx, cdy = 0.64 * dy
        let fbl = pt(-0.42 * w + c0x, -hb + c0y)
        let fbr = pt(0.42 * w + c0x, -hb + c0y)
        let ftr = pt(0.33 * w + c0x, -hb - hc + c0y)
        let ftl = pt(-0.33 * w + c0x, -hb - hc + c0y)
        func back(_ p: CGPoint) -> CGPoint { pt(p.x + cdx, p.y + cdy) }
        let side = [fbr, ftr, back(ftr), back(fbr)]
        face(body, side, colors.side)
        let sideWindow = inset(side, 0.7)
        body.fill(poly(sideWindow), with: .color(P.window))
        var pillar = Path()
        pillar.move(to: pt((sideWindow[0].x + sideWindow[3].x) / 2, (sideWindow[0].y + sideWindow[3].y) / 2))
        pillar.addLine(to: pt((sideWindow[1].x + sideWindow[2].x) / 2, (sideWindow[1].y + sideWindow[2].y) / 2))
        body.stroke(pillar, with: .color(colors.side), lineWidth: 2.5)
        face(body, [ftl, ftr, back(ftr), back(ftl)], colors.top)
        let front = [fbl, fbr, ftr, ftl]
        face(body, front, colors.front)
        body.fill(poly(inset(front, 0.78)), with: .color(P.window))

        // Body front and its lights.
        face(body, [pt(l, 0), pt(r, 0), pt(r, -hb), pt(l, -hb)], colors.front)
        let lw = max(8, 0.12 * w)
        let lh = max(4, 0.13 * hb)
        let ly = -0.55 * hb - lh / 2
        for x in [l + 0.08 * w, r - 0.08 * w - lw] {
            let rect = Path(CGRect(x: x, y: ly, width: lw, height: lh))
            body.fill(rect, with: .color(P.light))
            body.stroke(rect, with: .color(P.outline), lineWidth: 0.8)
        }
    }

    /// Three strokes fanning out from the car's roof corner, `age` seconds after the honk.
    static func honk(_ ctx: GraphicsContext, _ c: TrafficJamShakeCar, e: Double, z: Double, age: Double, alpha: Double) {
        guard age < M.honkLife, alpha > 0 else { return }
        let p = age / M.honkLife
        let fade = (p < 0.15 ? p / 0.15 : 1 - M.smooth(p, 0.5, 1)) * alpha
        let w = c.w * (1 + e)
        let k = max(0.6, 1 - 0.6 * e)
        let ox = -0.36 * w
        let oy = -c.hb * k - c.hc * k * 0.7 - z
        let r0 = 6 + 10 * p
        let len = 12 * (1 - 0.4 * p)
        var path = Path()
        for (ux, uy) in [(-0.99, -0.15), (-0.75, -0.66), (-0.3, -0.95)] {
            path.move(to: CGPoint(x: ox + ux * r0, y: oy + uy * r0))
            path.addLine(to: CGPoint(x: ox + ux * (r0 + len), y: oy + uy * (r0 + len)))
        }
        ctx.stroke(path, with: .color(P.ink.opacity(fade)), style: StrokeStyle(lineWidth: 3.5, lineCap: .round))
    }
}

struct TrafficJamShake: View {
    var app = "Laneway"
    var route = "Home → Studio"
    var label = "MORNING COMMUTE"
    var title = "Heavy traffic\non Ring Rd"
    var subtitle = "Leave at 8:40 to make your 9:15."
    var hint = "Shake the jam · tap a car to honk"
    var duration = "34 min"
    var delay = "+12 min"
    var arrival = "Arrive 9:14"
    var action = "Remind me at 8:35"
    /// Shakes the jam by itself, every `TrafficJamShakeMotion.loop` seconds.
    var autoPlay = false

    private typealias M = TrafficJamShakeMotion
    private typealias P = TrafficJamShakePalette
    private static let margin: CGFloat = 20
    /// Bottom card: 18 + row 36 + 14 + bar 10 + 8 + labels 16 + 18 + button 52 + 18.
    private static let cardHeight: CGFloat = 190
    /// Top copy below the safe area: 8 + header 32 + 18 + label 16 + 6 + title 76 + 8 + subtitle 20.
    private static let topHeight: CGFloat = 184

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = TrafficJamShakeSim()
    /// Where the loop's time starts.
    @State private var origin = Date()
    /// True from a touch until the pile has settled and the loop has started over.
    @State private var manual = false
    /// When manual mode started, and the simulation time then.
    @State private var manualStart = Date()
    @State private var manualBase = 0.0
    /// When the residual of a touch started to fade out (nil: not fading).
    @State private var settleStart: Date? = nil
    @State private var dragging = false
    @State private var touchStart = Date()
    @State private var moved = false
    /// Honks under reduced motion (no simulation): car index and when.
    @State private var stillHonk: (Int, Date)? = nil
    @State private var lastBumps = 0
    @State private var lastBumpHaptic = Date.distantPast
    @State private var touches = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing, height: geo.size.height + safe.top + safe.bottom)
            let cardTop = size.height - safe.bottom - 16 - Self.cardHeight
            let heroTop = safe.top + Self.topHeight + 12
            let heroBottom = cardTop - 14 - 16 - 12
            let frame = TrafficJamShakeFrame(region: CGRect(x: 0, y: heroTop, width: size.width, height: heroBottom - heroTop))
            let animating = (!reduceMotion && autoPlay) || manual || stillHonk != nil
            ZStack(alignment: .topLeading) {
                P.background
                copy(safe: safe, size: size)
                TimelineView(.animation(paused: !animating)) { context in
                    Canvas { ctx, _ in
                        draw(ctx, frame: frame, date: context.date)
                    }
                }
                .frame(width: size.width, height: size.height)
                .allowsHitTesting(false)
                .accessibilityLabel("A jam of eight cartoon cars")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag(frame: frame))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            sim.reset()
            haptic.prepare()
        }
        .task(id: touches) { await settle() }
    }

    // MARK: Copy

    private func copy(safe: EdgeInsets, size: CGSize) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text(app)
                    .font(.system(size: 20, weight: .heavy))
                    .foregroundStyle(P.ink)
                Spacer()
                Text(route)
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(P.ink)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Capsule().fill(P.card))
            }
            .frame(height: 32)
            .padding(.top, safe.top + 8)
            Text(label)
                .font(.system(size: 12, weight: .semibold))
                .tracking(1.2)
                .foregroundStyle(P.ink.opacity(0.5))
                .frame(height: 16)
                .padding(.top, 18)
            Text(title)
                .font(.system(size: 34, weight: .heavy))
                .lineSpacing(0)
                .foregroundStyle(P.ink)
                .fixedSize(horizontal: false, vertical: true)
                .frame(height: 76, alignment: .topLeading)
                .padding(.top, 6)
            Text(subtitle)
                .font(.system(size: 16))
                .foregroundStyle(P.ink.opacity(0.62))
                .lineLimit(1)
                .minimumScaleFactor(0.8)
                .frame(height: 20)
                .padding(.top, 8)

            Spacer(minLength: 0)

            Text(hint)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(P.ink.opacity(0.45))
                .frame(maxWidth: .infinity)
                .frame(height: 16)
                .padding(.bottom, 14)
            card
                .padding(.horizontal, 16 - Self.margin)
                .padding(.bottom, safe.bottom + 16)
        }
        .padding(.horizontal, Self.margin)
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private var card: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .center, spacing: 10) {
                Text(duration)
                    .font(.system(size: 30, weight: .heavy))
                    .foregroundStyle(P.ink)
                Text(delay)
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(P.red)
                    .padding(.horizontal, 9)
                    .frame(height: 24)
                    .background(Capsule().fill(P.redSoft))
                Spacer(minLength: 0)
                Text(arrival)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(P.ink.opacity(0.55))
            }
            .frame(height: 36)
            GeometryReader { g in
                let segments: [(Double, Color)] = [(0.22, P.green), (0.14, P.orange), (0.30, P.jam), (0.12, P.orange), (0.22, P.green)]
                let gaps = 2.0 * Double(segments.count - 1)
                HStack(spacing: 2) {
                    ForEach(segments.indices, id: \.self) { i in
                        Capsule()
                            .fill(segments[i].1)
                            .frame(width: (g.size.width - gaps) * segments[i].0)
                    }
                }
            }
            .frame(height: 10)
            .padding(.top, 14)
            ZStack {
                Text("Elm Bridge").frame(maxWidth: .infinity, alignment: .leading)
                Text("Ring Rd · 2.4 km jam")
                Text("Studio").frame(maxWidth: .infinity, alignment: .trailing)
            }
            .font(.system(size: 12, weight: .medium))
            .foregroundStyle(P.ink.opacity(0.5))
            .frame(height: 16)
            .padding(.top, 8)
            Text(action)
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(Color.white)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(Capsule().fill(P.ink))
                .padding(.top, 18)
                .accessibilityAddTraits(.isButton)
        }
        .padding(18)
        .frame(height: Self.cardHeight)
        .background(RoundedRectangle(cornerRadius: 26, style: .continuous).fill(P.card))
    }

    // MARK: Drawing

    /// Advances the simulation to `date` and returns the factor its deviations are drawn with, and
    /// the loop time (nil out of the loop).
    private func advance(to date: Date) -> (k: Double, loopTime: Double?) {
        if manual {
            sim.run(until: manualBase + date.timeIntervalSince(manualStart), maxGap: 0.25)
            if let s = settleStart {
                return (1 - M.smooth(date.timeIntervalSince(s), 0, M.settle), nil)
            }
            return (1, nil)
        }
        guard autoPlay, !reduceMotion else { return (0, nil) }
        let t = max(0, date.timeIntervalSince(origin).truncatingRemainder(dividingBy: M.loop))
        if !sim.scripted || t + M.dt < sim.time { sim.reset() }
        sim.run(until: t)
        return (M.window(at: t), t)
    }

    private func draw(_ ctx: GraphicsContext, frame: TrafficJamShakeFrame, date: Date) {
        let (k, loopTime) = advance(to: date)
        var pile = ctx
        pile.translateBy(x: frame.origin.x, y: frame.origin.y)
        pile.scaleBy(x: frame.scale, y: frame.scale)
        let tremble = loopTime.map { t in M.cars.indices.map { M.tremble($0, at: t) } }
        let cars = M.cars
        for (i, c) in cars.enumerated() {
            let b = sim.bodies[i]
            var car = pile
            car.translateBy(x: c.x + b.px * k, y: c.y + b.py * k + (tremble?[i] ?? 0))
            car.rotate(by: .radians(c.angle + b.a * k))
            TrafficJamShakeDraw.car(car, c, e: b.e * k, z: b.z * k)
        }
        for (i, c) in cars.enumerated() {
            let b = sim.bodies[i]
            var car = pile
            car.translateBy(x: c.x + b.px * k, y: c.y + b.py * k)
            car.rotate(by: .radians(c.angle + b.a * k))
            if let (j, at) = stillHonk, j == i {
                TrafficJamShakeDraw.honk(car, c, e: 0, z: 0, age: date.timeIntervalSince(at), alpha: 1)
            } else if manual || loopTime != nil {
                TrafficJamShakeDraw.honk(car, c, e: b.e * k, z: b.z * k, age: b.honk, alpha: k)
            }
        }

        if manual, sim.bumps != lastBumps {
            let now = date
            DispatchQueue.main.async {
                lastBumps = sim.bumps
                if now.timeIntervalSince(lastBumpHaptic) > 0.25 {
                    lastBumpHaptic = now
                    haptic.impactOccurred(intensity: 0.6)
                }
            }
        }

        // The ghost finger of the loop.
        guard let t = loopTime else { return }
        let f = M.finger(at: t)
        guard f.opacity > 0.001 else { return }
        let centre: CGPoint
        if f.onTap {
            let c = cars[M.tapCar]
            centre = frame.point(c.x + 0.1 * c.w, c.y - c.hb * 0.6)
        } else {
            let g = M.target(at: t)
            let b = TrafficJamShakeFrame.bounds
            centre = frame.point(b.midX + 3 * g.x, b.midY + 3 * g.y)
        }
        let r = 21 * f.scale
        let ring = Path(ellipseIn: CGRect(x: centre.x - r, y: centre.y - r, width: 2 * r, height: 2 * r))
        ctx.fill(ring, with: .color(P.ink.opacity(0.1 * f.opacity)))
        ctx.stroke(ring, with: .color(P.ink.opacity(0.5 * f.opacity)), lineWidth: 1.5)
    }

    // MARK: Touch

    /// The car under a screen point, front to back.
    private func carIndex(at p: CGPoint, frame: TrafficJamShakeFrame) -> Int? {
        let x = (p.x - frame.origin.x) / frame.scale
        let y = (p.y - frame.origin.y) / frame.scale
        for i in M.cars.indices.reversed() {
            let c = M.cars[i]
            let b = sim.bodies[i]
            let ax = c.x + b.px
            let ay = c.y + b.py - b.z
            let top = -(c.hb + c.hc) + 0.82 * c.l * M.dirY
            if x >= ax - c.w / 2, x <= ax + c.w / 2 + c.l * M.dirX, y >= ay + top, y <= ay + 4 {
                // Inside the box's sloping outline: below the roof line that rises to the right.
                let rel = (x - (ax + c.w / 2)) / (c.l * M.dirX)
                let roof = ay - c.hb - c.hc + max(0, rel) * c.l * M.dirY * 0.82
                let floor = ay + max(0, rel) * c.l * M.dirY + 4
                if y >= roof, y <= floor { return i }
            }
        }
        return nil
    }

    /// Hands the pile to the finger: from here the simulation runs on real time.
    private func takeOver(at date: Date) {
        if !manual {
            let (k, _) = advance(to: date)
            sim.scale(by: k)
            sim.scripted = false
            sim.tx = 0
            sim.ty = 0
            manualBase = sim.time
            manualStart = date
            lastBumps = sim.bumps
            manual = true
        }
        settleStart = nil
    }

    private func drag(frame: TrafficJamShakeFrame) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date()
                if !dragging {
                    dragging = true
                    moved = false
                    touchStart = now
                    touches += 1
                }
                let t = value.translation
                if !moved, hypot(t.width, t.height) > 8 {
                    moved = true
                    if !reduceMotion {
                        takeOver(at: now)
                        haptic.impactOccurred()
                    }
                }
                guard moved, !reduceMotion else { return }
                // Rubber-banded: the ground follows the finger less and less.
                func band(_ v: Double) -> Double { 0.6 * v / (1 + abs(v) / 60) }
                sim.tx = band(t.width) / frame.scale
                sim.ty = band(t.height) / frame.scale
            }
            .onEnded { value in
                let now = Date()
                dragging = false
                if !moved, now.timeIntervalSince(touchStart) < 0.35,
                   let i = carIndex(at: value.location, frame: frame) {
                    haptic.impactOccurred()
                    if reduceMotion {
                        stillHonk = (i, now)
                    } else {
                        takeOver(at: now)
                        sim.run(until: manualBase + now.timeIntervalSince(manualStart), maxGap: 0.25)
                        sim.tap(i)
                    }
                }
                sim.tx = 0
                sim.ty = 0
                touches += 1
            }
    }

    /// 2 s after the last touch the pile's residual fades out and the loop starts over from rest.
    private func settle() async {
        guard touches > 0 else { return }
        try? await Task.sleep(for: .seconds(M.userHold))
        guard !Task.isCancelled, !dragging else { return }
        if stillHonk != nil { stillHonk = nil }
        guard manual else { return }
        settleStart = .now
        try? await Task.sleep(for: .seconds(M.settle))
        guard !Task.isCancelled, !dragging else { return }
        settleStart = nil
        sim.reset()
        origin = .now
        manual = false
    }
}

#Preview {
    TrafficJamShake(autoPlay: true)
}
