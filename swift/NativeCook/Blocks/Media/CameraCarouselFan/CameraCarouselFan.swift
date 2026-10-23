import SwiftUI
import UIKit
import simd

/*
 Camera Carousel Fan — a camera shelf: a glossy 3D camera floats over black with eight instant
 photos orbiting it on a tilted ring; picking another camera tumbles the camera end over end
 and the photos flip over to that camera's film look.

 The camera is drawn, not loaded: each model is a handful of extruded rounded rectangles and
 circles (CameraCarouselFanModel.swift), rotated and projected in perspective (focal 760 pt)
 and painted back to front with smooth Lambert + Blinn shading; lenses get a glass gradient.

 The selection is one number p (0 … 4, the camera index). Everything follows from it:
   tumble    φ = (p − round p) · 180°, about the x axis, with 0.35 φ about y; the model swaps
             at the edge-on moment (|p − round p| = ½). Seen from 12° above, idle yaw ±7°
             (period 4 s) and a 4 pt bob (period 4 s).
   ring      radius 172, tilted 15°, turns by −135° per camera (+ ±6° idle sway, period 8 s);
             each photo faces the viewer with a yaw of 0.55·sin(angle) and its own tilt.
   photos    each photo flips about its own vertical axis by (p − round p) · 180°, 0.06 s after
             the one before it, and shows the round(p) camera's film look.
   labels    name and spec fade and rise 6 pt between cameras; the thumbnail strip slides 72 pt
             per camera under a fixed outline, the nearest thumbnail scaled to 1.15.

 autoPlay timeline (loop = 8 s), every move easeInOutBack (≈4 % wind-up and overshoot):
   0.0 – 1.2   rest on OKA 35
   1.2 – 2.4   flip to RF-2
   2.4 – 3.6   rest
   3.6 – 4.8   flip to PIP
   4.8 – 5.9   rest
   5.9 – 7.4   spin back through RF-2 to OKA 35
   7.4 – 8.0   rest (same frame as 0.0; the last photo lands 0.42 s after the camera)

 Touch: dragging sideways turns the camera with the finger (a screen's 80 % per camera, rubber
 band past the ends); release springs (period 0.55 s, damping 0.72) to the nearest camera,
 thrown 0.18 s ahead. Tapping the camera goes to the next one; tapping a thumbnail goes to it.
 Light haptic on each camera passed and on landing. 2 s after the last touch the shelf eases
 back to OKA 35 and the loop starts over.
 Reduced motion: no loop, no tumble, no orbit and no flips; changing camera cross-fades.
 */

struct CameraCarouselFan: View {
    var autoPlay = false

    static let loop = CameraCarouselFanTimeline.loop
    typealias TL = CameraCarouselFanTimeline
    typealias V3 = CameraCarouselFanV3

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var autoStart: Date? = nil
    @State private var idleOrigin = Date.now
    @State private var takeoverAt: Date? = nil
    @State private var manualP = 0.0
    @State private var spring: (date: Date, from: Double, v0: Double, to: Double)? = nil
    @State private var returning: (date: Date, from: Double, dur: Double)? = nil
    @State private var dragStartP: Double? = nil
    @State private var moved = false
    @State private var lastIndex = 0
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    struct Shown {
        var p: Double
        var clock: Double
        var idle: Double
        var photoP: [Double]
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: reduceMotion && dragStartP == nil)) { timeline in
                let s = shown(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, sz in
                        CameraCarouselFanRender.scene(&ctx, size: sz, shown: s, reduce: reduceMotion)
                    }
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .gesture(drag(width: size.width))
                    .accessibilityElement()
                    .accessibilityLabel("Camera \(CameraCarouselFanCameras.all[index(s.p)].name)")
                    .accessibilityAddTraits(.isButton)

                    header(size: size, insets: insets, p: s.p)
                    labels(size: size, p: s.p)
                    strip(size: size, p: s.p)
                    footer(size: size, insets: insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0.03, green: 0.03, blue: 0.035).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            haptic.prepare()
            idleOrigin = .now
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    private func index(_ p: Double) -> Int { min(4, max(0, Int(p.rounded()))) }

    // MARK: Time

    private func shown(at date: Date) -> Shown {
        if let autoStart, date >= autoStart, !reduceMotion {
            let t = date.timeIntervalSince(autoStart)
            let photos = (0..<8).map { TL.p(t - Double($0) * TL.stagger) }
            return Shown(p: TL.p(t), clock: t, idle: 1, photoP: photos)
        }
        if let r = returning, let autoStart {
            let u = date.timeIntervalSince(r.date) / r.dur
            let p = r.from + (0 - r.from) * TL.easeInOutBack(u)
            return Shown(p: p, clock: date.timeIntervalSince(autoStart), idle: TL.smoothstep(0, 1, u),
                         photoP: Array(repeating: p, count: 8))
        }
        var p = manualP
        if let sp = spring, dragStartP == nil {
            p = TL.spring(date.timeIntervalSince(sp.date), from: sp.from, velocity: sp.v0, to: sp.to)
        }
        let idle = takeoverAt.map { 1 - TL.smoothstep(0, 0.4, date.timeIntervalSince($0)) } ?? (autoPlay ? 0 : 1)
        return Shown(p: p, clock: date.timeIntervalSince(idleOrigin), idle: reduceMotion ? 0 : idle,
                     photoP: Array(repeating: p, count: 8))
    }

    private func takeOver(_ now: Date) {
        resume?.cancel()
        let s = shown(at: now)
        manualP = s.p
        if let a = autoStart { idleOrigin = a }
        if autoStart != nil || returning != nil || takeoverAt == nil { takeoverAt = now }
        autoStart = nil
        returning = nil
        spring = nil
    }

    private func drag(width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if dragStartP == nil {
                    takeOver(now)
                    dragStartP = manualP
                    moved = false
                    lastIndex = index(manualP)
                }
                if !moved, abs(value.translation.width) > 6 { moved = true }
                guard moved, let start = dragStartP else { return }
                var p = start - value.translation.width / (width * 0.8)
                // Rubber band past the first and last camera.
                if p < 0 { p = -0.35 * (1 - exp(p / 0.35)) }
                if p > 4 { p = 4 + 0.35 * (1 - exp(-(p - 4) / 0.35)) }
                manualP = p
                let i = index(p)
                if i != lastIndex {
                    lastIndex = i
                    haptic.impactOccurred(intensity: 0.6)
                }
            }
            .onEnded { value in
                let now = Date.now
                dragStartP = nil
                let current = manualP
                if moved {
                    let v = -value.velocity.width / (width * 0.8)
                    let near = current.rounded()
                    let target = min(4, max(0, min(near + 1, max(near - 1, (current + v * 0.18).rounded()))))
                    settle(at: now, to: target, velocity: v)
                } else {
                    settle(at: now, to: Double((index(current) + 1) % 5), velocity: 0)
                }
                scheduleResume()
            }
    }

    private func go(to k: Int) {
        let now = Date.now
        takeOver(now)
        settle(at: now, to: Double(k), velocity: 0)
        scheduleResume()
    }

    private func settle(at now: Date, to target: Double, velocity: Double) {
        haptic.impactOccurred(intensity: 0.8)
        lastIndex = Int(target)
        if reduceMotion {
            manualP = target
            spring = nil
        } else {
            spring = (now, manualP, velocity, target)
        }
    }

    private func scheduleResume() {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            let from = shown(at: now).p
            let dur = 0.5 + 0.3 * abs(from)
            manualP = from
            spring = nil
            returning = (now, from, dur)
            autoStart = now.addingTimeInterval(dur)
            takeoverAt = nil
        }
    }

    // MARK: Chrome

    private func header(size: CGSize, insets: EdgeInsets, p: Double) -> some View {
        HStack {
            Text("CAMERA SHELF")
                .tracking(2)
            Spacer()
            Text(String(format: "%02d / 05", index(p) + 1))
                .monospacedDigit()
        }
        .font(.system(size: 11, weight: .semibold, design: .monospaced))
        .foregroundStyle(.white.opacity(0.45))
        .padding(.horizontal, 24)
        .frame(width: size.width)
        .offset(y: insets.top + 14)
        .allowsHitTesting(false)
    }

    private func labels(size: CGSize, p: Double) -> some View {
        let cam = CameraCarouselFanCameras.all[index(p)]
        let d = abs(p - p.rounded())
        let a = 1 - TL.smoothstep(0.08, 0.42, d)
        return VStack(spacing: 8) {
            Text(cam.name)
                .font(.system(size: 26, weight: .bold, design: .monospaced))
                .foregroundStyle(.white)
            Text(cam.spec)
                .font(.system(size: 11, weight: .medium, design: .monospaced))
                .tracking(0.5)
                .foregroundStyle(.white.opacity(0.5))
        }
        .lineLimit(1)
        .opacity(a)
        .offset(y: 6 * (1 - a))
        .frame(width: size.width)
        .offset(y: size.height * 0.63)
        .allowsHitTesting(false)
    }

    private func strip(size: CGSize, p: Double) -> some View {
        let spacing = 72.0
        let y = size.height * 0.755
        return ZStack(alignment: .topLeading) {
            ForEach(0..<5, id: \.self) { k in
                let near = max(0, 1 - abs(Double(k) - p))
                let x = size.width / 2 + (Double(k) - p) * spacing
                VStack(spacing: 10) {
                    CameraCarouselFanThumb(index: k)
                        .frame(width: 56, height: 44)
                        .scaleEffect(1 + 0.15 * near)
                    Text(CameraCarouselFanCameras.all[k].name)
                        .font(.system(size: 9, weight: .semibold, design: .monospaced))
                        .foregroundStyle(.white.opacity(0.35 + 0.65 * near))
                        .lineLimit(1)
                        .fixedSize()
                }
                .opacity(0.4 + 0.6 * near)
                .frame(width: 64)
                .contentShape(Rectangle())
                .onTapGesture { go(to: k) }
                .offset(x: x - 32, y: y - 22)
                .accessibilityElement()
                .accessibilityLabel(CameraCarouselFanCameras.all[k].name)
                .accessibilityAddTraits(.isButton)
            }
            // The fixed outline the thumbnails slide under.
            RoundedRectangle(cornerRadius: 14, style: .continuous)
                .strokeBorder(.white.opacity(0.9), lineWidth: 1.5)
                .frame(width: 70, height: 58)
                .offset(x: size.width / 2 - 35, y: y - 29)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .mask(
            LinearGradient(stops: [.init(color: .clear, location: 0.02), .init(color: .black, location: 0.22),
                                   .init(color: .black, location: 0.78), .init(color: .clear, location: 0.98)],
                           startPoint: .leading, endPoint: .trailing)
        )
    }

    private func footer(size: CGSize, insets: EdgeInsets) -> some View {
        let y = size.height - max(insets.bottom, 16) - 12 - 44
        return HStack {
            Text("Swipe to change camera")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(.white.opacity(0.4))
            Spacer()
            Button {
                haptic.impactOccurred(intensity: 0.5)
            } label: {
                Image(systemName: "chevron.down")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(.black)
                    .frame(width: 44, height: 44)
                    .background(Circle().fill(.white))
            }
            .accessibilityLabel("Close")
        }
        .padding(.horizontal, 24)
        .frame(width: size.width)
        .offset(y: y)
    }
}

/// A thumbnail of one camera, drawn once.
struct CameraCarouselFanThumb: View, Equatable {
    let index: Int
    var body: some View {
        Canvas { ctx, sz in
            let m = CameraCarouselFanM3.y(-0.45) * CameraCarouselFanM3.x(0.22)
            CameraCarouselFanRender.camera(&ctx, CameraCarouselFanCameras.all[index], m: m, scale: 0.26,
                                           center: CGPoint(x: sz.width / 2, y: sz.height / 2 + 2), alpha: 1)
        }
        .allowsHitTesting(false)
    }
}

// MARK: Rendering

struct CameraCarouselFanM3 {
    var r0, r1, r2: CameraCarouselFanV3
    func apply(_ v: CameraCarouselFanV3) -> CameraCarouselFanV3 {
        CameraCarouselFanV3(simd_dot(r0, v), simd_dot(r1, v), simd_dot(r2, v))
    }
    static func x(_ a: Double) -> Self {
        Self(r0: .init(1, 0, 0), r1: .init(0, cos(a), -sin(a)), r2: .init(0, sin(a), cos(a)))
    }
    static func y(_ a: Double) -> Self {
        Self(r0: .init(cos(a), 0, sin(a)), r1: .init(0, 1, 0), r2: .init(-sin(a), 0, cos(a)))
    }
    static func z(_ a: Double) -> Self {
        Self(r0: .init(cos(a), -sin(a), 0), r1: .init(sin(a), cos(a), 0), r2: .init(0, 0, 1))
    }
    static func * (a: Self, b: Self) -> Self {
        let c0 = CameraCarouselFanV3(b.r0.x, b.r1.x, b.r2.x)
        let c1 = CameraCarouselFanV3(b.r0.y, b.r1.y, b.r2.y)
        let c2 = CameraCarouselFanV3(b.r0.z, b.r1.z, b.r2.z)
        func row(_ r: CameraCarouselFanV3) -> CameraCarouselFanV3 { .init(simd_dot(r, c0), simd_dot(r, c1), simd_dot(r, c2)) }
        return Self(r0: row(a.r0), r1: row(a.r1), r2: row(a.r2))
    }
}

enum CameraCarouselFanRender {
    typealias V3 = CameraCarouselFanV3
    typealias TL = CameraCarouselFanTimeline
    static let focal = 760.0
    static let light = CameraCarouselFanGeo.normalize(V3(-0.5, 0.75, 0.6))
    static let halfway = CameraCarouselFanGeo.normalize(light + V3(0, 0, 1))

    static func color(_ c: V3, _ alpha: Double = 1) -> Color {
        Color(red: min(1, max(0, c.x)), green: min(1, max(0, c.y)), blue: min(1, max(0, c.z)), opacity: alpha)
    }

    static func shade(_ c: V3, _ gloss: Double, _ n: V3) -> V3 {
        let lambert = max(0, simd_dot(n, light))
        let spec = pow(max(0, simd_dot(n, halfway)), 28) * gloss * 0.85
        let rim = pow(max(0, 1 - abs(n.z)), 4) * 0.08
        return c * (0.26 + 0.84 * lambert) + V3(repeating: spec + rim)
    }

    static func project(_ q: V3, _ center: CGPoint) -> CGPoint {
        let k = focal / (focal - q.z)
        return CGPoint(x: center.x + q.x * k, y: center.y - q.y * k)
    }

    static let glassStops: [[(Double, V3)]] = [
        [(0, V3(0.99, 0.99, 0.98)), (0.35, V3(0.90, 0.88, 0.85)), (0.75, V3(0.60, 0.58, 0.57)), (1, V3(0.28, 0.27, 0.28))],
        [(0, V3(0.60, 0.66, 0.82)), (0.18, V3(0.22, 0.26, 0.38)), (0.6, V3(0.05, 0.06, 0.10)), (1, V3(0.02, 0.02, 0.03))],
        [(0, V3(0.58, 0.88, 0.84)), (0.2, V3(0.12, 0.36, 0.37)), (0.65, V3(0.03, 0.08, 0.09)), (1, V3(0.01, 0.02, 0.02))],
    ]

    /// A camera's visible faces, projected, with their depth.
    struct Drawn {
        var z: Double
        var path: Path
        var shading: GraphicsContext.Shading
        var stroke: Bool
        /// A faint light edge on caps that face the viewer, like a bevel.
        var rim = 0.0
        var decals: [(Path, GraphicsContext.Shading)]
    }

    static func faces(_ cam: CameraCarouselFanCamera, m: CameraCarouselFanM3, scale: Double, offset: V3,
                      center: CGPoint, alpha: Double) -> [Drawn] {
        let faces = cam.faces
        var out: [Drawn] = []
        var slot = Array(repeating: -1, count: faces.count)
        func view(_ p: V3) -> V3 { m.apply(p * scale) + offset }
        for (i, f) in faces.enumerated() {
            let nv = m.apply(f.normal)
            let cv = view(f.center)
            if simd_dot(nv, V3(0, 0, focal) - cv) <= 0 { continue }
            if f.parent >= 0 && slot[f.parent] < 0 { continue }
            var path = Path()
            let pts = f.pts.map { project(view($0), center) }
            path.addLines(pts)
            path.closeSubpath()
            let shading: GraphicsContext.Shading
            if f.glass >= 0 {
                let u = simd_cross(f.axis, f.normal)
                let focus = project(view(f.center + (f.axis - u) * (0.32 * f.half)), center)
                let k = focal / (focal - cv.z)
                shading = .radialGradient(
                    Gradient(stops: glassStops[f.glass].map { .init(color: color($0.1, alpha), location: $0.0) }),
                    center: focus, startRadius: 0, endRadius: f.half * 1.45 * scale * k)
            } else if let n0 = f.n0, let n1 = f.n1 {
                let a = project(view((f.pts[0] + f.pts[3]) / 2), center)
                let b = project(view((f.pts[1] + f.pts[2]) / 2), center)
                let c0 = color(shade(f.color, f.gloss, m.apply(n0)), alpha)
                let c1 = color(shade(f.color, f.gloss, m.apply(n1)), alpha)
                shading = hypot(b.x - a.x, b.y - a.y) < 0.5 ? .color(c0)
                    : .linearGradient(Gradient(colors: [c0, c1]), startPoint: a, endPoint: b)
            } else {
                let base = shade(f.color, f.gloss, nv)
                let top = project(view(f.center + f.axis * f.half), center)
                let bottom = project(view(f.center - f.axis * f.half), center)
                shading = .linearGradient(Gradient(colors: [color(base * 1.12 + V3(repeating: 0.02), alpha), color(base * 0.86, alpha)]),
                                          startPoint: top, endPoint: bottom)
            }
            if f.parent >= 0 {
                out[slot[f.parent]].decals.append((path, shading))
                slot[i] = slot[f.parent]
            } else {
                slot[i] = out.count
                let rim = f.glass < 0 && f.n0 == nil ? 0.16 * max(0, simd_dot(nv, light)) : 0
                out.append(Drawn(z: cv.z + f.bias * scale, path: path, shading: shading, stroke: f.glass < 0, rim: rim, decals: []))
            }
        }
        return out
    }

    static func draw(_ ctx: inout GraphicsContext, _ d: Drawn) {
        ctx.fill(d.path, with: d.shading)
        if d.stroke { ctx.stroke(d.path, with: d.shading, lineWidth: 0.7) }
        if d.rim > 0.01 { ctx.stroke(d.path, with: .color(.white.opacity(d.rim)), lineWidth: 1) }
        for (p, s) in d.decals { ctx.fill(p, with: s) }
    }

    /// One camera alone (the thumbnails).
    static func camera(_ ctx: inout GraphicsContext, _ cam: CameraCarouselFanCamera, m: CameraCarouselFanM3, scale: Double,
                       center: CGPoint, alpha: Double) {
        for d in faces(cam, m: m, scale: scale, offset: V3(0, 0, 0), center: center, alpha: alpha).sorted(by: { $0.z < $1.z }) {
            draw(&ctx, d)
        }
    }

    // MARK: The scene

    static func scene(_ ctx: inout GraphicsContext, size: CGSize, shown s: CameraCarouselFan.Shown, reduce: Bool) {
        let w = size.width, h = size.height
        let scale = w / 393
        let center = CGPoint(x: w / 2, y: h * 0.35)
        let cams = CameraCarouselFanCameras.all
        let i = min(4, max(0, Int(s.p.rounded())))
        let frac = s.p - s.p.rounded()
        let tau = 2 * Double.pi

        // Spotlight in the accent of the cameras either side of p.
        let lo = min(4, max(0, Int(floor(s.p)))), hi = min(4, lo + 1)
        let mixT = min(1, max(0, s.p - Double(lo)))
        let accent = cams[lo].accent * (1 - mixT) + cams[hi].accent * mixT
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.03, green: 0.03, blue: 0.035)))
        ctx.fill(Path(CGRect(origin: .zero, size: size)),
                 with: .radialGradient(Gradient(colors: [color(accent, 0.26), color(accent, 0.08), color(accent, 0)]),
                                       center: CGPoint(x: center.x, y: center.y - 10 * scale), startRadius: 0, endRadius: 270 * scale))
        // A faint floor under the camera.
        var floor = ctx
        floor.translateBy(x: center.x, y: center.y + 118 * scale)
        floor.scaleBy(x: 1, y: 0.16)
        floor.fill(Path(ellipseIn: CGRect(x: -150 * scale, y: -150 * scale, width: 300 * scale, height: 300 * scale)),
                   with: .radialGradient(Gradient(colors: [.white.opacity(0.10), .white.opacity(0)]),
                                         center: .zero, startRadius: 0, endRadius: 150 * scale))

        // The camera.
        let phi = reduce ? 0 : frac * Double.pi
        let idleYaw = s.idle * 7 * Double.pi / 180 * sin(tau * s.clock / 4)
        let bob = s.idle * 4 * sin(tau * s.clock / 4 + 1) * scale
        let m = CameraCarouselFanM3.y(idleYaw - 0.18) * CameraCarouselFanM3.x(0.21) * CameraCarouselFanM3.x(phi) * CameraCarouselFanM3.y(0.35 * phi)
        let alpha = reduce ? 1 - 2 * abs(frac) : 1
        var items: [(z: Double, face: Drawn?, photo: Int)] = faces(cams[i], m: m, scale: scale * 1.06, offset: V3(0, bob, 0),
                                                                     center: center, alpha: alpha).map { ($0.z, $0, -1) }

        // The photos on their ring.
        let ring = 172 * scale
        let tilt = CameraCarouselFanM3.x(0.26)
        let sway = reduce ? 0 : -s.p * 0.75 * Double.pi + s.idle * 0.1 * sin(tau * s.clock / 8)
        struct Placed { var c: V3; var ex: V3; var ey: V3; var look: Int; var dim: Double; var fade: Double }
        var placed: [Placed] = []
        for k in 0..<8 {
            let a = sway + Double(k) * tau / 8
            let pk = s.photoP[k]
            let kr = pk.rounded()
            let flip = reduce ? 0 : (pk - kr) * Double.pi
            let photoBob = s.idle * 3 * sin(tau * s.clock / 4 + Double(k)) * scale
            let c = tilt.apply(V3(ring * sin(a), CameraCarouselFanPhotos.heights[k] * scale, ring * cos(a))) + V3(0, photoBob + bob * 0.5, 0)
            let rot = tilt * CameraCarouselFanM3.y(0.55 * sin(a) + flip) * CameraCarouselFanM3.z(CameraCarouselFanPhotos.rolls[k] * Double.pi / 180)
            let look = cams[min(4, max(0, Int(kr)))].look
            let dim = 0.42 + 0.58 * TL.smoothstep(-ring, ring * 0.7, c.z)
            placed.append(Placed(c: c, ex: rot.apply(V3(1, 0, 0)), ey: rot.apply(V3(0, 1, 0)), look: look, dim: dim,
                                 fade: reduce ? 1 - 2 * abs(pk - kr) : 1))
            items.append((c.z, nil, k))
        }

        for item in items.sorted(by: { $0.z < $1.z }) {
            if let f = item.face {
                draw(&ctx, f)
                continue
            }
            let pl = placed[item.photo]
            let pw = 64 * scale, ph = 78 * scale
            let tl = project(pl.c - pl.ex * (pw / 2) + pl.ey * (ph / 2), center)
            let tr = project(pl.c + pl.ex * (pw / 2) + pl.ey * (ph / 2), center)
            let bl = project(pl.c - pl.ex * (pw / 2) - pl.ey * (ph / 2), center)
            let t = CGAffineTransform(a: (tr.x - tl.x) / 64, b: (tr.y - tl.y) / 64, c: (bl.x - tl.x) / 78, d: (bl.y - tl.y) / 78,
                                      tx: tl.x, ty: tl.y)
            var pc = ctx
            pc.concatenate(t)
            pc.opacity = pl.fade
            photo(&pc, scene: item.photo, look: pl.look, front: t.a * t.d - t.b * t.c > 0)
            pc.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 64, height: 78), cornerRadius: 2),
                    with: .color(.black.opacity(1 - pl.dim)))
        }
    }

    /// One instant photo in a 64 × 78 frame: a 56 × 56 picture and a written caption.
    static func photo(_ ctx: inout GraphicsContext, scene: Int, look: Int, front: Bool) {
        let paper = Path(roundedRect: CGRect(x: 0, y: 0, width: 64, height: 78), cornerRadius: 2)
        ctx.fill(paper, with: .color(Color(red: 0.96, green: 0.95, blue: 0.92)))
        guard front else {
            ctx.fill(Path(CGRect(x: 8, y: 30, width: 48, height: 1.5)), with: .color(.black.opacity(0.08)))
            return
        }
        var pic = ctx
        let box = CGRect(x: 4, y: 4, width: 56, height: 56)
        pic.clip(to: Path(box))
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: box.minX + x * box.width, y: box.minY + y * box.height) }
        func c(_ v: V3) -> Color { color(CameraCarouselFanPhotos.look(v, look)) }
        for op in CameraCarouselFanPhotos.scenes[scene] {
            switch op {
            case let .rect(x, y, rw, rh, col):
                pic.fill(Path(CGRect(x: box.minX + x * 56, y: box.minY + y * 56, width: rw * 56, height: rh * 56)), with: .color(c(col)))
            case let .grad(x, y, rw, rh, top, bottom):
                pic.fill(Path(CGRect(x: box.minX + x * 56, y: box.minY + y * 56, width: rw * 56, height: rh * 56)),
                         with: .linearGradient(Gradient(colors: [c(top), c(bottom)]), startPoint: pt(0, y), endPoint: pt(0, y + rh)))
            case let .circle(x, y, r, col):
                pic.fill(Path(ellipseIn: CGRect(x: box.minX + (x - r) * 56, y: box.minY + (y - r) * 56, width: 2 * r * 56, height: 2 * r * 56)),
                         with: .color(c(col)))
            case let .poly(pts, col):
                var path = Path()
                path.addLines(pts.map { pt($0.x, $0.y) })
                path.closeSubpath()
                pic.fill(path, with: .color(c(col)))
            }
        }
        // A soft sheen across the print.
        pic.fill(Path(box), with: .linearGradient(Gradient(colors: [.white.opacity(0.16), .white.opacity(0)]),
                                                  startPoint: pt(0, 0), endPoint: pt(0.6, 0.6)))
        // The caption, as if written in pen.
        ctx.fill(Path(roundedRect: CGRect(x: 7, y: 66, width: 26, height: 2.2), cornerRadius: 1.1), with: .color(.black.opacity(0.28)))
        ctx.fill(Path(roundedRect: CGRect(x: 7, y: 71, width: 14, height: 2), cornerRadius: 1), with: .color(.black.opacity(0.16)))
        ctx.fill(Path(ellipseIn: CGRect(x: 52, y: 66, width: 5, height: 5)), with: .color(.black.opacity(0.18)))
    }
}
