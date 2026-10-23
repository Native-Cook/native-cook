import CoreText
import SwiftUI
import UIKit

// Drawing for the pendulum wave on a SwiftUI GraphicsContext, step for step as the React Native
// Skia drawing: a perspective camera orbiting the scene, a walnut frame drawn as shaded boxes,
// V-strung brass bobs with light trails, their shadows on the table, and the comb. Everything is
// painted back to front (faces and bobs are sorted by depth each frame), lit by one light from
// above, a little to the left and front.

/// The camera on the screen: centre, focal length, position and its right, up and forward axes.
struct PendulumWaveView {
    var cx: Double
    var cy: Double
    var f: Double
    var c: SIMD3<Double>
    var r: SIMD3<Double>
    var u: SIMD3<Double>
    var w: SIMD3<Double>

    /// What the camera looks at.
    static let targetY = 6.2

    /// The camera for an orbit `cam`, on a canvas of the given size.
    init(_ cam: PendulumWaveCamera, width: Double, focal: Double, centreY: Double) {
        let yaw = cam.yaw * .pi / 180
        let pitch = cam.pitch * .pi / 180
        let c = SIMD3(cam.dist * cos(pitch) * sin(yaw), Self.targetY + cam.dist * sin(pitch), cam.dist * cos(pitch) * cos(yaw))
        let w = PendulumWaveDrawing.norm(SIMD3(-c.x, Self.targetY - c.y, -c.z))
        // right = forward × up, up' = right × forward.
        let r = PendulumWaveDrawing.norm(SIMD3(-w.z, 0, w.x))
        let u = SIMD3(r.y * w.z - r.z * w.y, r.z * w.x - r.x * w.z, r.x * w.y - r.y * w.x)
        self.cx = width / 2
        self.cy = centreY
        self.f = focal
        self.c = c
        self.r = r
        self.u = u
        self.w = w
    }

    /// World point to screen: x, y and depth.
    func project(_ x: Double, _ y: Double, _ z: Double) -> SIMD3<Double> {
        let dx = x - c.x
        let dy = y - c.y
        let dz = z - c.z
        let depth = max(0.1, dx * w.x + dy * w.y + dz * w.z)
        let sx = dx * r.x + dy * r.y + dz * r.z
        let sy = dx * u.x + dy * u.y + dz * u.z
        return SIMD3(cx + f * sx / depth, cy - f * sy / depth, depth)
    }

    func project(_ p: SIMD3<Double>) -> SIMD3<Double> { project(p.x, p.y, p.z) }

    func point(_ x: Double, _ y: Double, _ z: Double) -> CGPoint {
        let p = project(x, y, z)
        return CGPoint(x: p.x, y: p.y)
    }
}

enum PendulumWaveDrawing {
    private typealias T = PendulumWaveTuning

    // The table's extent, and the frame: beam, posts at the row's ends, feet.
    static let tableX = 14.0
    static let tableZ = 16.0
    static let postZ = Double(PendulumWaveTuning.count - 1) / 2 * PendulumWaveTuning.spacing + 1.3
    // Toward the light.
    static let light = norm(SIMD3(-0.35, 1, 0.3))
    static let wood = SIMD3(0.42, 0.25, 0.14)

    static func norm(_ v: SIMD3<Double>) -> SIMD3<Double> {
        let l = (v.x * v.x + v.y * v.y + v.z * v.z).squareRoot()
        return v / l
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: min(1, r), green: min(1, g), blue: min(1, b), opacity: a)
    }

    private static func segment(_ a: CGPoint, _ b: CGPoint) -> Path {
        var p = Path()
        p.move(to: a)
        p.addLine(to: b)
        return p
    }

    private static func roundLine(_ width: Double) -> StrokeStyle {
        StrokeStyle(lineWidth: width, lineCap: .round)
    }

    /// A world-space polygon on screen.
    private static func polygon(_ v: PendulumWaveView, _ pts: [SIMD3<Double>]) -> Path {
        var b = Path()
        for (i, q) in pts.enumerated() {
            let p = v.point(q.x, q.y, q.z)
            if i == 0 { b.move(to: p) } else { b.addLine(to: p) }
        }
        b.closeSubpath()
        return b
    }

    struct Box {
        let c: SIMD3<Double>
        let s: SIMD3<Double>
        let alpha: Double
    }

    /// The frame's boxes: the beam (faded by `beamAlpha`), the posts, the feet.
    private static func frameBoxes(_ beamAlpha: Double) -> [Box] {
        [
            Box(c: SIMD3(0, T.beamY + 0.3, 0), s: SIMD3(0.6, 0.6, postZ * 2 + 0.5), alpha: beamAlpha),
            Box(c: SIMD3(0, (T.beamY + 0.6) / 2, -postZ), s: SIMD3(0.5, T.beamY + 0.6, 0.5), alpha: 1),
            Box(c: SIMD3(0, (T.beamY + 0.6) / 2, postZ), s: SIMD3(0.5, T.beamY + 0.6, 0.5), alpha: 1),
            Box(c: SIMD3(0, 0.2, -postZ), s: SIMD3(5.4, 0.4, 0.9), alpha: 1),
            Box(c: SIMD3(0, 0.2, postZ), s: SIMD3(5.4, 0.4, 0.9), alpha: 1),
        ]
    }

    // The six faces of a unit box: outward normal, then the two in-face axes.
    private static let faces: [[Double]] = [
        [1, 0, 0, 0, 1, 0, 0, 0, 1],
        [-1, 0, 0, 0, 1, 0, 0, 0, 1],
        [0, 1, 0, 1, 0, 0, 0, 0, 1],
        [0, -1, 0, 1, 0, 0, 0, 0, 1],
        [0, 0, 1, 1, 0, 0, 0, 1, 0],
        [0, 0, -1, 1, 0, 0, 0, 1, 0],
    ]

    private struct FaceCorners {
        let centre: SIMD3<Double>
        let pts: [SIMD3<Double>]
        let a: SIMD3<Double>
        let d: SIMD3<Double>
    }

    /// One face of a box: its corners in the world.
    private static func faceCorners(_ b: Box, _ f: [Double]) -> FaceCorners {
        let h = b.s / 2
        let centre = SIMD3(b.c.x + f[0] * h.x, b.c.y + f[1] * h.y, b.c.z + f[2] * h.z)
        let a = SIMD3(f[3] * h.x, f[4] * h.y, f[5] * h.z)
        let d = SIMD3(f[6] * h.x, f[7] * h.y, f[8] * h.z)
        return FaceCorners(centre: centre, pts: [centre - a - d, centre + a - d, centre + a + d, centre - a + d], a: a, d: d)
    }

    private static func length(_ v: SIMD3<Double>) -> Double {
        (v.x * v.x + v.y * v.y + v.z * v.z).squareRoot()
    }

    /// A walnut face, lit by the light and grained along its longer side.
    private static func drawFace(_ context: inout GraphicsContext, _ v: PendulumWaveView, _ b: Box, _ f: [Double]) {
        let L = light
        let fc = faceCorners(b, f)
        let lit = 0.32 + 0.68 * max(0, f[0] * L.x + f[1] * L.y + f[2] * L.z)
        let top = f[1] > 0.5 ? 1.12 : 1
        context.fill(polygon(v, fc.pts), with: .color(rgba(wood.x * lit * top, wood.y * lit * top, wood.z * lit * top, b.alpha)))
        // Grain: a few darker lines along the face's longer axis.
        let la = length(fc.a)
        let ld = length(fc.d)
        let along = la > ld ? fc.a : fc.d
        let across = la > ld ? fc.d : fc.a
        let grain = rgba(0.12, 0.06, 0.03, 0.22 * b.alpha)
        for i in 1...3 {
            let t = -0.7 + Double(i) * 0.37
            let o = fc.centre + across * t
            let p0 = v.point(o.x - along.x * 0.98, o.y - along.y * 0.98, o.z - along.z * 0.98)
            let p1 = v.point(o.x + along.x * 0.98, o.y + along.y * 0.98, o.z + along.z * 0.98)
            context.stroke(segment(p0, p1), with: .color(grain), style: roundLine(0.7))
        }
        // A light catch along the top edge of side faces.
        let e0 = v.point(fc.pts[3].x, fc.pts[3].y, fc.pts[3].z)
        let e1 = v.point(fc.pts[2].x, fc.pts[2].y, fc.pts[2].z)
        context.stroke(segment(e0, e1), with: .color(rgba(1, 0.86, 0.66, 0.12 * b.alpha * lit)), style: roundLine(0.8))
    }

    /// One brass bob with its V of string and its light trail.
    private static func drawBob(_ context: inout GraphicsContext, _ v: PendulumWaveView, _ s: PendulumWaveSim, _ k: Int, _ stringAlpha: Double) {
        let b = s.bobAt(k, s.x[k])
        let p = v.project(b.0, b.1, b.2)
        let z = T.rowZ(k)
        // The trail: newest sample first, fading and thinning with age.
        let head = s.trailHead
        var prev = p
        for i in 1..<T.trail {
            let x = s.trail[k][(head - i + T.trail * 2) % T.trail]
            let q0 = s.bobAt(k, x)
            let q = v.project(q0.0, q0.1, q0.2)
            let age = Double(i) / Double(T.trail)
            let len = hypot(q.x - prev.x, q.y - prev.y)
            if len > 0.3 {
                context.stroke(
                    segment(CGPoint(x: prev.x, y: prev.y), CGPoint(x: q.x, y: q.y)),
                    with: .color(rgba(1, 0.86, 0.6, 0.5 * (1 - age) * (1 - age))),
                    style: roundLine(2.2 * (1 - age) + 0.4)
                )
            }
            prev = q
        }
        // The strings.
        let str = rgba(0.92, 0.9, 0.85, 0.5 * stringAlpha)
        let bob = CGPoint(x: p.x, y: p.y)
        context.stroke(segment(v.point(0, T.beamY, z - T.vHalf), bob), with: .color(str), style: roundLine(0.75))
        context.stroke(segment(v.point(0, T.beamY, z + T.vHalf), bob), with: .color(str), style: roundLine(0.75))
        // The bob: brass lit from the upper left, a warm bounce from the table on its lower edge.
        let r = v.f * T.bobR / p.z
        let disc = Path(ellipseIn: CGRect(x: p.x - r, y: p.y - r, width: 2 * r, height: 2 * r))
        context.fill(
            disc,
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgba(1, 0.96, 0.8, 1), location: 0),
                    .init(color: rgba(0.89, 0.7, 0.33, 1), location: 0.22),
                    .init(color: rgba(0.55, 0.37, 0.12, 1), location: 0.62),
                    .init(color: rgba(0.2, 0.12, 0.03, 1), location: 1),
                ]),
                center: CGPoint(x: p.x - r * 0.35, y: p.y - r * 0.42),
                startRadius: 0,
                endRadius: r * 1.45
            )
        )
        let rim = Path(ellipseIn: CGRect(x: p.x - r * 0.9, y: p.y - r * 0.9, width: r * 1.8, height: r * 1.8))
        context.stroke(
            rim,
            with: .linearGradient(
                Gradient(colors: [rgba(1, 0.7, 0.4, 0), rgba(1, 0.72, 0.42, 0.45)]),
                startPoint: CGPoint(x: p.x, y: p.y),
                endPoint: CGPoint(x: p.x, y: p.y + r)
            ),
            style: roundLine(max(0.6, r * 0.18))
        )
        // The highlight: a small disc blurred by a sigma of a tenth of the bob, drawn as the
        // radial falloff such a blur gives, which is far cheaper than a blur layer per bob.
        let sr = r * 0.16
        let sigma = max(0.4, r * 0.1)
        let reach = sr + 2.5 * sigma
        let edge = sr / reach
        context.fill(
            Path(ellipseIn: CGRect(x: p.x - r * 0.38 - reach, y: p.y - r * 0.44 - reach, width: 2 * reach, height: 2 * reach)),
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgba(1, 1, 1, 0.9 * min(1, sr / (1.6 * sigma))), location: 0),
                    .init(color: rgba(1, 1, 1, 0.45), location: edge),
                    .init(color: rgba(1, 1, 1, 0), location: 1),
                ]),
                center: CGPoint(x: p.x - r * 0.38, y: p.y - r * 0.44),
                startRadius: 0,
                endRadius: reach
            )
        )
    }

    /// The comb: a brass rail through the bobs' line with a tooth between each pair.
    private static func drawComb(_ context: inout GraphicsContext, _ v: PendulumWaveView, _ s: PendulumWaveSim) {
        let shown = s.combShown
        if shown <= 0.01 { return }
        let sink = (1 - shown) * 0.8
        var pts: [SIMD3<Double>] = []
        for k in -1...T.count {
            let kk = min(T.count - 1, max(0, k))
            let b = s.bobAt(kk, s.combX)
            let z = k < 0 ? T.rowZ(0) - 0.9 : k >= T.count ? T.rowZ(T.count - 1) + 0.9 : T.rowZ(k)
            pts.append(SIMD3(s.combX, b.1 - sink, z))
        }
        var rail = Path()
        for (i, q) in pts.enumerated() {
            let p = v.point(q.x, q.y, q.z)
            if i == 0 { rail.move(to: p) } else { rail.addLine(to: p) }
        }
        let mid = v.project(s.combX, 5, 0)
        let w = v.f * 0.09 / mid.z
        context.stroke(rail, with: .color(rgba(0.16, 0.1, 0.04, 0.5 * shown)), style: roundLine(w + 1.6))
        context.stroke(rail, with: .color(rgba(0.86, 0.66, 0.32, shown)), style: roundLine(w))
        context.stroke(rail, with: .color(rgba(1, 0.95, 0.8, 0.55 * shown)), style: roundLine(w * 0.3))
        // Teeth between the bobs, so each string sits in its own slot.
        let tooth = rgba(0.8, 0.6, 0.28, shown)
        for k in 0..<(T.count - 1) {
            let y = (pts[k + 1].y + pts[k + 2].y) / 2
            let z = T.rowZ(k) + T.spacing / 2
            context.stroke(segment(v.point(s.combX, y - 0.5, z), v.point(s.combX, y + 0.5, z)), with: .color(tooth), style: roundLine(max(1, w * 0.55)))
        }
    }

    /// Where a world point's shadow falls on the table.
    private static func shadowAt(_ x: Double, _ y: Double, _ z: Double) -> SIMD3<Double> {
        let L = light
        return SIMD3(x - L.x * y / L.y, 0.001, z - L.z * y / L.y)
    }

    /// Soft shadows on the table, cast away from the light. React Native blurs them by a sigma in
    /// device pixels, so the radius here is divided by the display scale.
    private static func drawShadows(_ context: inout GraphicsContext, _ v: PendulumWaveView, _ s: PendulumWaveSim, _ beamAlpha: Double, _ scale: Double) {
        var bobs = Path()
        for k in 0..<T.count {
            let b = s.bobAt(k, s.x[k])
            let c = shadowAt(b.0, b.1, b.2)
            let r = T.bobR * 1.25
            var ring: [SIMD3<Double>] = []
            for i in 0..<10 {
                let a = Double(i) / 10 * .pi * 2
                ring.append(SIMD3(c.x + cos(a) * r, 0.001, c.z + sin(a) * r))
            }
            bobs.addPath(polygon(v, ring))
        }
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 3.5 / scale))
            layer.fill(bobs, with: .color(rgba(0.02, 0.01, 0, 0.42)))
        }
        // The posts and the beam.
        var posts = Path()
        for side in [-1.0, 1.0] {
            let z = side * postZ
            let t = shadowAt(0, T.beamY, z)
            posts.addPath(polygon(v, [SIMD3(-0.25, 0.001, z), SIMD3(0.25, 0.001, z), SIMD3(t.x + 0.25, 0.001, t.z), SIMD3(t.x - 0.25, 0.001, t.z)]))
        }
        let b0 = shadowAt(0, T.beamY, -postZ)
        let b1 = shadowAt(0, T.beamY, postZ)
        let beam = polygon(v, [SIMD3(b0.x - 0.3, 0.001, b0.z), SIMD3(b0.x + 0.3, 0.001, b0.z), SIMD3(b1.x + 0.3, 0.001, b1.z), SIMD3(b1.x - 0.3, 0.001, b1.z)])
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 5 / scale))
            layer.fill(posts, with: .color(rgba(0.02, 0.01, 0, 0.32)))
        }
        context.drawLayer { layer in
            layer.addFilter(.blur(radius: 5 / scale))
            layer.fill(beam, with: .color(rgba(0.02, 0.01, 0, 0.32 * beamAlpha)))
        }
    }

    /// The whole scene for camera `v`; `topness` (0…1) fades the beam for the view from above.
    static func drawScene(_ context: inout GraphicsContext, _ v: PendulumWaveView, _ s: PendulumWaveSim, width: Double, height: Double, topness: Double, scale: Double) {
        // The studio: a dark warm wall with a pool of light behind the frame.
        let all = Path(CGRect(x: 0, y: 0, width: width, height: height))
        context.fill(
            all,
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: rgba(0.05, 0.055, 0.065, 1), location: 0),
                    .init(color: rgba(0.085, 0.075, 0.07, 1), location: 0.55),
                    .init(color: rgba(0.03, 0.025, 0.022, 1), location: 1),
                ]),
                startPoint: .zero,
                endPoint: CGPoint(x: 0, y: height)
            )
        )
        let centre = v.point(0, PendulumWaveView.targetY, 0)
        context.fill(
            all,
            with: .radialGradient(
                Gradient(colors: [rgba(0.32, 0.24, 0.16, 0.5), rgba(0.32, 0.24, 0.16, 0)]),
                center: centre,
                startRadius: 0,
                endRadius: height * 0.55
            )
        )

        // The table: walnut, lit in a pool under the frame.
        let table = polygon(v, [SIMD3(-tableX, 0, -tableZ), SIMD3(tableX, 0, -tableZ), SIMD3(tableX, 0, tableZ), SIMD3(-tableX, 0, tableZ)])
        let o = v.project(0, 0, 0)
        context.fill(
            table,
            with: .radialGradient(
                Gradient(stops: [
                    .init(color: rgba(0.3, 0.19, 0.11, 1), location: 0),
                    .init(color: rgba(0.17, 0.1, 0.055, 1), location: 0.55),
                    .init(color: rgba(0.06, 0.035, 0.02, 1), location: 1),
                ]),
                center: CGPoint(x: o.x, y: o.y),
                startRadius: 0,
                endRadius: v.f * 15 / o.z
            )
        )
        // Long grain lines across the table.
        let grain = rgba(0.05, 0.025, 0.01, 0.18)
        for i in -8...8 {
            let d = Double(i)
            let x = d * 1.05 + sin(d * 3.7) * 0.2
            let p0 = v.point(x, 0, -tableZ)
            let p1 = v.point(x + sin(d * 1.3) * 0.3, 0, tableZ)
            context.stroke(segment(p0, p1), with: .color(grain), style: roundLine(0.8))
        }
        let beamAlpha = 1 - 0.78 * topness
        drawShadows(&context, v, s, beamAlpha, scale)

        // Everything standing on the table, back to front: (depth, kind, index, face).
        let boxes = frameBoxes(beamAlpha)
        var items: [(Double, Int, Int, Int)] = []
        for i in boxes.indices {
            for j in faces.indices {
                let f = faces[j]
                let fc = faceCorners(boxes[i], f)
                let toCam = v.c - fc.centre
                if toCam.x * f[0] + toCam.y * f[1] + toCam.z * f[2] <= 0 { continue }
                // The beam's faces sort as if at the beam's middle line nearest the camera.
                let d = v.project(fc.centre.x, fc.centre.y, i == 0 ? max(-postZ, min(postZ, v.c.z)) : fc.centre.z).z
                items.append((d, 0, i, j))
            }
        }
        var farthest = 0.0
        for k in 0..<T.count {
            let b = s.bobAt(k, s.x[k])
            let d = v.project(b.0, b.1, b.2).z
            farthest = max(farthest, d)
            items.append((d, 1, k, 0))
        }
        items.append((farthest + 0.05, 2, 0, 0))
        // Farthest first (insertion sort, stable like the React Native one).
        if items.count > 1 {
            for i in 1..<items.count {
                let it = items[i]
                var j = i - 1
                while j >= 0 && items[j].0 < it.0 {
                    items[j + 1] = items[j]
                    j -= 1
                }
                items[j + 1] = it
            }
        }
        let stringAlpha = 1 - 0.55 * topness
        for it in items {
            if it.1 == 0 {
                drawFace(&context, v, boxes[it.2], faces[it.3])
            } else if it.1 == 1 {
                drawBob(&context, v, s, it.2, stringAlpha)
            } else {
                drawComb(&context, v, s)
            }
        }
    }

    /// Text right-aligned at `right` with its baseline on y.
    static func trailingText(_ context: inout GraphicsContext, _ text: String, right: Double, baseline y: Double, font: CTFont, color: Color) {
        let attributes: [NSAttributedString.Key: Any] = [
            NSAttributedString.Key(kCTFontAttributeName as String): font,
            NSAttributedString.Key(kCTForegroundColorAttributeName as String): UIColor(color).cgColor,
        ]
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: attributes))
        let width = CTLineGetTypographicBounds(line, nil, nil, nil)
        context.withCGContext { cg in
            cg.saveGState()
            cg.textMatrix = CGAffineTransform(scaleX: 1, y: -1)
            cg.textPosition = CGPoint(x: right - width, y: y)
            CTLineDraw(line, cg)
            cg.restoreGState()
        }
    }
}
