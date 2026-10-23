import Foundation
import simd

/*
 The data behind Camera Carousel Fan: five invented cameras built from a few extruded shapes
 (rounded rectangles and circles pushed out along an axis), the eight drawn "photos" that orbit
 them, the film looks that tint those photos, and the autoPlay timeline.

 Units are points at a 393 pt wide screen; x right, y up, z towards the viewer.
 */

typealias CameraCarouselFanV3 = SIMD3<Double>
typealias CameraCarouselFanV2 = SIMD2<Double>

// MARK: Geometry

struct CameraCarouselFanPart {
    enum Kind { case solid, glass(Int), decal(Int) }
    var profile: [CameraCarouselFanV2]
    var depth: Double
    var origin: CameraCarouselFanV3
    var u = CameraCarouselFanV3(1, 0, 0)
    var v = CameraCarouselFanV3(0, 1, 0)
    var n = CameraCarouselFanV3(0, 0, 1)
    var color: CameraCarouselFanV3
    var gloss: Double
    var kind: Kind = .solid
    /// Alternate side quads are darkened, like a knurled focus ring.
    var stripes = false
    /// Added to the depth the faces are sorted by: lenses sit in front of the trim around them.
    var bias = 0.0
}

struct CameraCarouselFanFace {
    /// Side quads: [back k, back k+1, front k+1, front k]. Caps: the profile.
    var pts: [CameraCarouselFanV3]
    var normal: CameraCarouselFanV3
    /// Side quads only: the smoothed normals of the quad's two long edges (k and k+1).
    var n0: CameraCarouselFanV3? = nil
    var n1: CameraCarouselFanV3? = nil
    var color: CameraCarouselFanV3
    var gloss: Double
    /// −1 for a solid face, else the glass style of a lens's front.
    var glass = -1
    /// −1, or the index of the face this decal lies on (drawn right after it).
    var parent = -1
    var center: CameraCarouselFanV3
    /// Caps: the profile's v axis and half its height, for the top-to-bottom sheen; glass: its radius.
    var axis = CameraCarouselFanV3(0, 1, 0)
    var half = 0.0
    var bias = 0.0
}

struct CameraCarouselFanCamera {
    let name: String
    let spec: String
    let accent: CameraCarouselFanV3
    let look: Int
    let faces: [CameraCarouselFanFace]
}

enum CameraCarouselFanGeo {
    static func roundedRect(_ w: Double, _ h: Double, _ r: Double, seg: Int = 4) -> [CameraCarouselFanV2] {
        let cx = w / 2 - r, cy = h / 2 - r
        let centers = [CameraCarouselFanV2(cx, -cy), CameraCarouselFanV2(cx, cy), CameraCarouselFanV2(-cx, cy), CameraCarouselFanV2(-cx, -cy)]
        var pts: [CameraCarouselFanV2] = []
        for k in 0..<4 {
            for s in 0...seg {
                let a = (Double(k) - 1 + Double(s) / Double(seg)) * .pi / 2
                pts.append(centers[k] + CameraCarouselFanV2(cos(a), sin(a)) * r)
            }
        }
        return pts
    }

    static func circle(_ r: Double, seg: Int = 28) -> [CameraCarouselFanV2] {
        (0..<seg).map { i in
            let a = Double(i) / Double(seg) * 2 * .pi
            return CameraCarouselFanV2(cos(a) * r, sin(a) * r)
        }
    }

    static func normalize(_ v: CameraCarouselFanV3) -> CameraCarouselFanV3 {
        let l = simd_length(v)
        return l < 1e-9 ? CameraCarouselFanV3(0, 0, 1) : v / l
    }

    /// Turns parts into faces. A decal keeps only its front, tied to the front of the part it names.
    static func faces(_ parts: [CameraCarouselFanPart]) -> [CameraCarouselFanFace] {
        var faces: [CameraCarouselFanFace] = []
        var frontOf: [Int] = []
        for part in parts {
            let p = part.profile
            let m = p.count
            func point(_ q: CameraCarouselFanV2, _ z: Double) -> CameraCarouselFanV3 {
                part.origin + part.u * q.x + part.v * q.y + part.n * z
            }
            let hz = part.depth / 2
            var glassStyle = -1
            var parent = -1
            switch part.kind {
            case .glass(let s): glassStyle = s
            case .decal(let of): parent = frontOf[of]
            case .solid: break
            }
            let ys = p.map(\.y)
            let half = ((ys.max() ?? 0) - (ys.min() ?? 0)) / 2
            if parent < 0 {
                // Edge normals, then the smoothed normal at each profile point.
                var edge: [CameraCarouselFanV3] = []
                for k in 0..<m {
                    let d = p[(k + 1) % m] - p[k]
                    edge.append(normalize(part.u * d.y - part.v * d.x))
                }
                for k in 0..<m {
                    let k1 = (k + 1) % m
                    let a = normalize(edge[(k + m - 1) % m] + edge[k])
                    let b = normalize(edge[k] + edge[k1])
                    var color = part.color
                    if part.stripes && k % 2 == 1 { color *= 0.62 }
                    let quad = [point(p[k], -hz), point(p[k1], -hz), point(p[k1], hz), point(p[k], hz)]
                    faces.append(CameraCarouselFanFace(pts: quad, normal: edge[k], n0: a, n1: b,
                                                       color: color, gloss: part.gloss,
                                                       center: (quad[0] + quad[1] + quad[2] + quad[3]) / 4, bias: part.bias))
                }
                faces.append(CameraCarouselFanFace(pts: p.reversed().map { point($0, -hz) }, normal: -part.n,
                                                   color: part.color, gloss: part.gloss,
                                                   center: part.origin - part.n * hz, axis: part.v, half: half, bias: part.bias))
            }
            frontOf.append(faces.count)
            let r = (p.map { simd_length($0) }.max() ?? 1)
            faces.append(CameraCarouselFanFace(pts: p.map { point($0, hz) }, normal: part.n,
                                               color: part.color, gloss: part.gloss, glass: glassStyle, parent: parent,
                                               center: part.origin + part.n * hz, axis: part.v,
                                               half: glassStyle >= 0 ? r : half, bias: part.bias))
        }
        return faces
    }
}

// MARK: The five cameras

enum CameraCarouselFanCameras {
    typealias P = CameraCarouselFanPart
    typealias G = CameraCarouselFanGeo
    static let up = (u: CameraCarouselFanV3(1, 0, 0), v: CameraCarouselFanV3(0, 0, -1), n: CameraCarouselFanV3(0, 1, 0))

    static func c(_ r: Double, _ g: Double, _ b: Double) -> CameraCarouselFanV3 { CameraCarouselFanV3(r, g, b) }

    /// A short cylinder standing on a top plate.
    static func knob(_ r: Double, _ h: Double, _ x: Double, _ y: Double, _ z: Double, _ color: CameraCarouselFanV3, _ gloss: Double) -> P {
        P(profile: G.circle(r, seg: 18), depth: h, origin: CameraCarouselFanV3(x, y, z), u: up.u, v: up.v, n: up.n, color: color, gloss: gloss)
    }

    static func decal(_ profile: [CameraCarouselFanV2], _ x: Double, _ y: Double, _ z: Double, _ color: CameraCarouselFanV3, _ on: Int, gloss: Double = 0.6) -> P {
        P(profile: profile, depth: 0, origin: CameraCarouselFanV3(x, y, z), color: color, gloss: gloss, kind: .decal(on))
    }

    static let all: [CameraCarouselFanCamera] = [oka, rf2, pip, moss, half]

    /// Orange top plate on a plum body, a big pearl lens.
    static let oka: CameraCarouselFanCamera = {
        let plum = c(0.20, 0.15, 0.23), orange = c(0.78, 0.33, 0.12)
        let parts: [P] = [
            P(profile: G.roundedRect(172, 78, 16), depth: 62, origin: CameraCarouselFanV3(0, -16, 0), color: plum, gloss: 0.3),
            P(profile: G.roundedRect(172, 34, 12), depth: 64, origin: CameraCarouselFanV3(0, 40, 0), color: orange, gloss: 0.85),
            P(profile: G.roundedRect(70, 26, 9), depth: 48, origin: CameraCarouselFanV3(0, 68, -4), color: c(0.72, 0.29, 0.10), gloss: 0.85),
            knob(9, 8, 56, 60, 6, c(0.84, 0.38, 0.15), 0.9),
            knob(13, 6, -54, 59, 4, c(0.16, 0.13, 0.17), 0.5),
            P(profile: G.circle(42), depth: 26, origin: CameraCarouselFanV3(0, -14, 44), color: c(0.10, 0.10, 0.11), gloss: 0.5, stripes: true, bias: 6),
            P(profile: G.circle(31), depth: 6, origin: CameraCarouselFanV3(0, -14, 59), color: c(0.08, 0.08, 0.09), gloss: 0.4, kind: .glass(0), bias: 8),
            decal(G.circle(5.5, seg: 14), 62, 4, 31, c(0.80, 0.12, 0.12), 0),
            decal(G.roundedRect(28, 12, 3), 0, 68, 20, c(0.05, 0.05, 0.07), 2, gloss: 1),
        ]
        return CameraCarouselFanCamera(name: "OKA 35", spec: "35MM · F/2.8 · WARM 400", accent: c(0.85, 0.38, 0.14), look: 0,
                                       faces: G.faces(parts))
    }()

    /// A rangefinder: cognac leather under a black top plate, silver trim.
    static let rf2: CameraCarouselFanCamera = {
        let silver = c(0.78, 0.79, 0.82)
        let parts: [P] = [
            P(profile: G.roundedRect(182, 70, 12), depth: 52, origin: CameraCarouselFanV3(0, -14, 0), color: c(0.46, 0.22, 0.12), gloss: 0.12),
            P(profile: G.roundedRect(182, 36, 12), depth: 54, origin: CameraCarouselFanV3(0, 39, 0), color: c(0.13, 0.13, 0.14), gloss: 0.95),
            knob(8, 8, 64, 61, 4, silver, 1),
            knob(14, 6, -52, 60, 2, c(0.15, 0.15, 0.16), 0.7),
            P(profile: G.circle(38), depth: 5, origin: CameraCarouselFanV3(0, -10, 28), color: silver, gloss: 1),
            P(profile: G.circle(35), depth: 30, origin: CameraCarouselFanV3(0, -10, 44), color: c(0.09, 0.09, 0.10), gloss: 0.6, stripes: true, bias: 6),
            P(profile: G.circle(25), depth: 4, origin: CameraCarouselFanV3(0, -10, 60), color: c(0.06, 0.06, 0.07), gloss: 0.4, kind: .glass(1), bias: 8),
            decal(G.roundedRect(32, 14, 3), -54, 40, 27, c(0.12, 0.16, 0.24), 1, gloss: 1),
            decal(G.roundedRect(18, 14, 3), 44, 40, 27, c(0.12, 0.16, 0.24), 1, gloss: 1),
        ]
        return CameraCarouselFanCamera(name: "RF-2", spec: "RANGEFINDER · F/2 · MONO", accent: c(0.62, 0.36, 0.20), look: 1,
                                       faces: G.faces(parts))
    }()

    /// A red plastic toy camera with a cream flash and lens.
    static let pip: CameraCarouselFanCamera = {
        let cream = c(0.93, 0.89, 0.80)
        let parts: [P] = [
            P(profile: G.roundedRect(158, 98, 28), depth: 60, origin: CameraCarouselFanV3(0, -6, 0), color: c(0.80, 0.15, 0.12), gloss: 0.7),
            P(profile: G.roundedRect(56, 24, 7), depth: 36, origin: CameraCarouselFanV3(40, 54, -2), color: cream, gloss: 0.5),
            knob(8, 8, -46, 46, 4, cream, 0.6),
            P(profile: G.circle(40), depth: 18, origin: CameraCarouselFanV3(0, -12, 39), color: cream, gloss: 0.6, bias: 6),
            P(profile: G.circle(26), depth: 5, origin: CameraCarouselFanV3(0, -12, 50), color: c(0.06, 0.08, 0.08), gloss: 0.4, kind: .glass(2), bias: 8),
            decal(G.roundedRect(44, 14, 4), 40, 54, 16, c(0.98, 0.97, 0.92), 1, gloss: 1),
            decal(G.roundedRect(24, 16, 4), -52, 24, 30, c(0.07, 0.07, 0.08), 0, gloss: 1),
            decal(G.roundedRect(150, 7, 3.4), 0, -44, 30, c(0.97, 0.94, 0.86), 0),
        ]
        return CameraCarouselFanCamera(name: "PIP", spec: "TOY · PLASTIC LENS · RED 200", accent: c(0.88, 0.20, 0.16), look: 2,
                                       faces: G.faces(parts))
    }()

    /// A boxy olive medium-format camera with a waist-level finder.
    static let moss: CameraCarouselFanCamera = {
        let parts: [P] = [
            P(profile: G.roundedRect(148, 108, 12), depth: 88, origin: CameraCarouselFanV3(0, -10, 0), color: c(0.30, 0.36, 0.22), gloss: 0.2),
            P(profile: G.roundedRect(148, 16, 6), depth: 90, origin: CameraCarouselFanV3(0, 52, 0), color: c(0.80, 0.81, 0.83), gloss: 1),
            P(profile: G.roundedRect(96, 22, 6), depth: 70, origin: CameraCarouselFanV3(0, 68, -6), color: c(0.12, 0.12, 0.13), gloss: 0.7),
            P(profile: G.circle(44), depth: 30, origin: CameraCarouselFanV3(0, -14, 59), color: c(0.09, 0.09, 0.10), gloss: 0.6, stripes: true, bias: 6),
            P(profile: G.circle(32), depth: 5, origin: CameraCarouselFanV3(0, -14, 76), color: c(0.06, 0.06, 0.07), gloss: 0.4, kind: .glass(1), bias: 8),
            decal(G.roundedRect(40, 10, 3), -44, 30, 44, c(0.80, 0.81, 0.83), 0, gloss: 1),
        ]
        return CameraCarouselFanCamera(name: "MOSS 6", spec: "6×6 · F/3.5 · GREEN FADE", accent: c(0.45, 0.56, 0.30), look: 3,
                                       faces: G.faces(parts))
    }()

    /// A silver half-frame compact with a blue band.
    static let half: CameraCarouselFanCamera = {
        let silver = c(0.76, 0.77, 0.80)
        let parts: [P] = [
            P(profile: G.roundedRect(170, 86, 18), depth: 44, origin: CameraCarouselFanV3(0, -4, 0), color: silver, gloss: 1),
            knob(7, 6, 60, 42, 0, silver, 1),
            P(profile: G.circle(31), depth: 22, origin: CameraCarouselFanV3(-34, -8, 33), color: silver, gloss: 1, stripes: true, bias: 6),
            P(profile: G.circle(21), depth: 4, origin: CameraCarouselFanV3(-34, -8, 45), color: c(0.06, 0.06, 0.07), gloss: 0.4, kind: .glass(1), bias: 8),
            decal(G.roundedRect(164, 34, 4), 0, -12, 22, c(0.17, 0.27, 0.50), 0, gloss: 0.15),
            decal(G.roundedRect(24, 14, 3), 52, 22, 22, c(0.06, 0.07, 0.10), 0, gloss: 1),
        ]
        return CameraCarouselFanCamera(name: "HALF", spec: "HALF FRAME · F/3.2 · BLUE HOUR", accent: c(0.30, 0.46, 0.85), look: 4,
                                       faces: G.faces(parts))
    }()
}

// MARK: Photos

enum CameraCarouselFanPhotos {
    enum Op {
        case rect(Double, Double, Double, Double, CameraCarouselFanV3)
        /// A vertical gradient filling a rect, top colour then bottom colour.
        case grad(Double, Double, Double, Double, CameraCarouselFanV3, CameraCarouselFanV3)
        case circle(Double, Double, Double, CameraCarouselFanV3)
        case poly([CameraCarouselFanV2], CameraCarouselFanV3)
    }
    typealias V = CameraCarouselFanV3
    static func p(_ pts: Double...) -> [CameraCarouselFanV2] {
        stride(from: 0, to: pts.count, by: 2).map { CameraCarouselFanV2(pts[$0], pts[$0 + 1]) }
    }

    /// Eight little scenes in unit coordinates (0…1, y down).
    static let scenes: [[Op]] = [
        // Red flowers on dark leaves.
        [.grad(0, 0, 1, 1, V(0.11, 0.24, 0.13), V(0.04, 0.11, 0.06)),
         .poly(p(0.0, 0.55, 0.35, 0.42, 0.5, 0.6, 0.2, 0.72), V(0.16, 0.36, 0.18)),
         .poly(p(0.55, 0.95, 0.75, 0.5, 1.0, 0.45, 0.85, 0.8), V(0.14, 0.32, 0.16)),
         .circle(0.30, 0.34, 0.16, V(0.86, 0.16, 0.12)), .circle(0.66, 0.26, 0.13, V(0.92, 0.22, 0.14)),
         .circle(0.52, 0.64, 0.18, V(0.80, 0.12, 0.10)), .circle(0.84, 0.72, 0.11, V(0.90, 0.28, 0.16)),
         .circle(0.30, 0.34, 0.04, V(0.98, 0.80, 0.30)), .circle(0.66, 0.26, 0.035, V(0.98, 0.80, 0.30)),
         .circle(0.52, 0.64, 0.045, V(0.98, 0.80, 0.30))],
        // A white building against a blue sky.
        [.grad(0, 0, 1, 1, V(0.28, 0.52, 0.90), V(0.72, 0.85, 0.98)),
         .poly(p(0.22, 1, 0.22, 0.36, 0.56, 0.18, 0.56, 1), V(0.95, 0.95, 0.96)),
         .poly(p(0.56, 1, 0.56, 0.18, 0.80, 0.32, 0.80, 1), V(0.66, 0.70, 0.78)),
         .rect(0.30, 0.48, 0.08, 0.08, V(0.30, 0.36, 0.46)), .rect(0.42, 0.44, 0.08, 0.08, V(0.30, 0.36, 0.46)),
         .rect(0.30, 0.68, 0.08, 0.08, V(0.30, 0.36, 0.46)), .rect(0.42, 0.66, 0.08, 0.08, V(0.30, 0.36, 0.46))],
        // A portrait in silhouette.
        [.grad(0, 0, 1, 1, V(0.64, 0.62, 0.60), V(0.36, 0.35, 0.34)),
         .circle(0.5, 1.02, 0.36, V(0.12, 0.12, 0.13)), .circle(0.5, 0.46, 0.16, V(0.14, 0.13, 0.13)),
         .circle(0.44, 0.40, 0.05, V(0.30, 0.28, 0.27))],
        // A sunset over the sea.
        [.grad(0, 0, 1, 0.66, V(0.98, 0.68, 0.38), V(0.94, 0.42, 0.48)),
         .circle(0.5, 0.60, 0.15, V(1.0, 0.90, 0.62)),
         .rect(0, 0.64, 1, 0.36, V(0.18, 0.20, 0.34)),
         .rect(0.38, 0.70, 0.24, 0.025, V(1.0, 0.78, 0.52)), .rect(0.43, 0.78, 0.14, 0.02, V(0.96, 0.66, 0.50)),
         .rect(0.46, 0.86, 0.08, 0.02, V(0.90, 0.56, 0.50))],
        // Mountains with snow.
        [.grad(0, 0, 1, 1, V(0.58, 0.75, 0.92), V(0.90, 0.93, 0.95)),
         .poly(p(-0.05, 0.85, 0.34, 0.30, 0.72, 0.85), V(0.44, 0.52, 0.62)),
         .poly(p(0.28, 1, 0.68, 0.40, 1.08, 1), V(0.24, 0.30, 0.37)),
         .poly(p(0.68, 0.40, 0.59, 0.53, 0.66, 0.50, 0.70, 0.55, 0.77, 0.50), V(0.97, 0.97, 0.98)),
         .rect(0, 0.86, 1, 0.14, V(0.20, 0.27, 0.22))],
        // A potted plant on a warm wall.
        [.grad(0, 0, 1, 1, V(0.96, 0.90, 0.76), V(0.88, 0.78, 0.60)),
         .poly(p(0.5, 0.74, 0.18, 0.40, 0.30, 0.20, 0.52, 0.66), V(0.22, 0.50, 0.30)),
         .poly(p(0.5, 0.74, 0.86, 0.34, 0.74, 0.16, 0.48, 0.64), V(0.30, 0.60, 0.36)),
         .poly(p(0.5, 0.74, 0.42, 0.24, 0.56, 0.08, 0.56, 0.66), V(0.18, 0.44, 0.26)),
         .poly(p(0.34, 0.72, 0.66, 0.72, 0.62, 1.0, 0.38, 1.0), V(0.78, 0.42, 0.28))],
        // A city at night.
        [.grad(0, 0, 1, 1, V(0.05, 0.06, 0.17), V(0.20, 0.11, 0.28)),
         .circle(0.78, 0.20, 0.08, V(0.96, 0.93, 0.84)),
         .rect(0.04, 0.50, 0.20, 0.50, V(0.08, 0.08, 0.13)), .rect(0.27, 0.34, 0.19, 0.66, V(0.10, 0.09, 0.16)),
         .rect(0.49, 0.56, 0.20, 0.44, V(0.08, 0.08, 0.13)), .rect(0.72, 0.42, 0.24, 0.58, V(0.10, 0.09, 0.16)),
         .rect(0.09, 0.58, 0.04, 0.04, V(1, 0.82, 0.40)), .rect(0.32, 0.42, 0.04, 0.04, V(1, 0.82, 0.40)),
         .rect(0.38, 0.56, 0.04, 0.04, V(1, 0.82, 0.40)), .rect(0.55, 0.64, 0.04, 0.04, V(1, 0.82, 0.40)),
         .rect(0.78, 0.50, 0.04, 0.04, V(1, 0.82, 0.40)), .rect(0.86, 0.66, 0.04, 0.04, V(1, 0.82, 0.40))],
        // Dunes under a low sun.
        [.grad(0, 0, 1, 1, V(0.98, 0.87, 0.68), V(0.96, 0.72, 0.54)),
         .circle(0.26, 0.30, 0.09, V(1.0, 0.96, 0.82)),
         .poly(p(0, 0.62, 0.40, 0.50, 1, 0.70, 1, 1, 0, 1), V(0.86, 0.58, 0.36)),
         .poly(p(0, 0.82, 0.55, 0.68, 1, 0.86, 1, 1, 0, 1), V(0.68, 0.40, 0.25))],
    ]

    /// The film look of each camera, applied to every colour of a photo.
    static func look(_ c: CameraCarouselFanV3, _ look: Int) -> CameraCarouselFanV3 {
        let l = 0.3 * c.x + 0.59 * c.y + 0.11 * c.z
        var o: CameraCarouselFanV3
        switch look {
        case 0: o = CameraCarouselFanV3(c.x * 1.06 + 0.05, c.y * 0.97 + 0.02, c.z * 0.80)
        case 1:
            let m = (l - 0.5) * 1.18 + 0.5
            o = CameraCarouselFanV3(m * 1.02, m, m * 0.96)
        case 2: o = CameraCarouselFanV3(c.x * 1.10 + 0.06, c.y * 0.86, c.z * 0.84)
        case 3: o = CameraCarouselFanV3(c.x * 0.84 + 0.04, c.y * 0.96 + 0.06, c.z * 0.86 + 0.04)
        default: o = CameraCarouselFanV3(c.x * 0.80, c.y * 0.92 + 0.02, c.z * 1.08 + 0.06)
        }
        return simd_clamp(o, CameraCarouselFanV3(repeating: 0), CameraCarouselFanV3(repeating: 1))
    }

    /// Where each of the eight photos rides on the ring: height (pt) and in-plane tilt (degrees).
    static let heights: [Double] = [-36, -4, -60, -22, -50, 0, -68, -28]
    static let rolls: [Double] = [-16, 10, -6, 20, -12, 6, 14, -22]
}

// MARK: Timeline

enum CameraCarouselFanTimeline {
    /// autoPlay: rest on OKA 35, flip to RF-2, rest, flip to PIP, rest, spin back to OKA 35.
    static let loop = 8.0
    /// (start, end, from, to) of each move.
    static let moves: [(Double, Double, Double, Double)] = [(1.2, 2.4, 0, 1), (3.6, 4.8, 1, 2), (5.9, 7.4, 2, 0)]
    /// Each photo flips this much later than the one before it.
    static let stagger = 0.06

    static func p(_ t: Double) -> Double {
        var t = t.truncatingRemainder(dividingBy: loop)
        if t < 0 { t += loop }
        var value = 0.0
        for (a, b, from, to) in moves {
            if t < a { return value }
            if t < b { return from + (to - from) * easeInOutBack((t - a) / (b - a)) }
            value = to
        }
        return value
    }

    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = min(1, max(0, (x - a) / (b - a)))
        return t * t * (3 - 2 * t)
    }

    /// Ease in and out with a small wind-up before and overshoot after (about 4 %).
    static func easeInOutBack(_ u: Double) -> Double {
        let u = min(1, max(0, u))
        let c2 = 1.0 * 1.525
        if u < 0.5 { return (pow(2 * u, 2) * ((c2 + 1) * 2 * u - c2)) / 2 }
        return (pow(2 * u - 2, 2) * ((c2 + 1) * (u * 2 - 2) + c2) + 2) / 2
    }

    /// A damped spring (period 0.55 s, damping ratio 0.72) from x0 with velocity v0 towards `to`.
    static func spring(_ t: Double, from x0: Double, velocity v0: Double, to: Double) -> Double {
        let w = 2 * Double.pi / 0.55, z = 0.72
        let wd = w * sqrt(1 - z * z)
        let a = x0 - to
        let b = (v0 + z * w * a) / wd
        return to + exp(-z * w * t) * (a * cos(wd * t) + b * sin(wd * t))
    }
}
