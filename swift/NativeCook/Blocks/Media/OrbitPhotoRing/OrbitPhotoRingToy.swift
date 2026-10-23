import SwiftUI

/// The Tumble style's camera: a soft, glossy toy-like body ray-marched in a Metal shader
/// (`OrbitPhotoRingToy.metal`) so it can turn over freely in true 3D. A glossy shell on top with a
/// carry hump and two dark knobs, a satin body below with a pale base, a deep knurled lens barrel
/// holding a matte white sphere, an accent dot and a small tab on the front. It uses the same
/// pinhole camera as the rest of the scene, so the prints sort in front of and behind it.
struct OrbitPhotoRingToyShape {
    var width: Double
    var height: Double
    var depth: Double
    var shell: Double
    var humpWidth: Double
    var humpHeight: Double
    var lensX: Double
    var lensY: Double
    var lensRadius: Double
    var lensLength: Double
    var shellTone: [Double]
    var bodyTone: [Double]
    var accentTone: [Double]

    private typealias V = OrbitPhotoRingVector

    /// The toy's measures and colours for a camera vector.
    init(_ v: [Double]) {
        let w = v[V.w]
        let h = max(v[V.h], w * 0.6)
        width = w
        height = h
        depth = max(v[V.d], w * 0.42)
        shell = h * max(0.3, v[V.plate] * 1.1)
        humpWidth = v[V.humpW] * 0.72
        humpHeight = v[V.humpH] * 0.7
        lensX = v[V.lensX] * 0.8 - w * 0.08
        lensY = h * 0.17
        lensRadius = v[V.lensR] * 1.35
        lensLength = max(v[V.lensL] * 0.8, w * 0.16)
        shellTone = [v[V.shell], v[V.shell + 1], v[V.shell + 2]]
        bodyTone = [v[V.toyBody], v[V.toyBody + 1], v[V.toyBody + 2]]
        accentTone = [v[V.accent], v[V.accent + 1], v[V.accent + 2]]
    }

    /// A sphere around the whole toy, in its own units.
    var radius: Double {
        let tall = height + 2 * max(humpHeight, 12) + 10
        let deep = depth + 2 * (lensLength + lensRadius * 0.4) + 6
        return 0.5 * ((width + 10) * (width + 10) + tall * tall + deep * deep).squareRoot()
    }
}

enum OrbitPhotoRingToy {
    // Edge softness in screen points: how far outside the silhouette a pixel still takes some colour.
    static let edge = 0.75

    /// Rotation by `a` radians about the axis (x, y, z), normalised here (row-major).
    static func axisRotation(_ x: Double, _ y: Double, _ z: Double, _ a: Double) -> [Double] {
        let n = (x * x + y * y + z * z).squareRoot()
        let len = n > 0 ? n : 1
        let ux = x / len, uy = y / len, uz = z / len
        let c = cos(a), s = sin(a), k = 1 - c
        return [
            c + ux * ux * k, ux * uy * k - uz * s, ux * uz * k + uy * s,
            uy * ux * k + uz * s, c + uy * uy * k, uy * uz * k - ux * s,
            uz * ux * k - uy * s, uz * uy * k + ux * s, c + uz * uz * k,
        ]
    }

    /// The tumble at `t` seconds into the loop, with the body turned `turned` radians round with its
    /// ring, scaled by `weight` (0 none, 1 all of it). The turn is taken the short way round, so
    /// leaving the style never spins the body back.
    static func tumbleMatrix(_ t: Double, turned: Double, weight: Double) -> [Double] {
        typealias M = OrbitPhotoRingMetrics
        typealias P = OrbitPhotoRingMath
        let sway = M.tumbleSway * .pi / 180 * sin(t / M.loop * .pi * 8) * weight
        let m = axisRotation(0.25, 1, 0.1, sway)
        let axis = M.tumbleAxis
        return P.mul3(axisRotation(axis[0], axis[1], axis[2], -P.wrapPi(turned) * weight), m)
    }

    /// Draws the toy camera at `pose`, `alpha` its opacity, `flash` the shutter's flash (0…1). The
    /// context must be untransformed: the shader works in its points.
    static func draw(_ ctx: GraphicsContext, view: OrbitPhotoRingView3, pose: OrbitPhotoRingPose, shape: OrbitPhotoRingToyShape, alpha: Double, flash: Double) {
        guard alpha >= 0.01, pose.s > 0 else { return }
        let r = pose.r
        let s = pose.s
        let bound = shape.radius
        let eyeDistance = pose.z - bound * s
        guard eyeDistance >= 2 else { return }
        // Rows of the inverse rotation (the transpose), and the eye seen from the object.
        let rx = [r[0], r[3], r[6]]
        let ry = [r[1], r[4], r[7]]
        let rz = [r[2], r[5], r[8]]
        let ox = -(rx[0] * pose.x + rx[1] * pose.y + rx[2] * pose.z) / s
        let oy = -(ry[0] * pose.x + ry[1] * pose.y + ry[2] * pose.z) / s
        let oz = -(rz[0] * pose.x + rz[1] * pose.y + rz[2] * pose.z) / s
        let sx = view.cx + view.f * pose.x / pose.z
        let sy = view.cy + view.f * pose.y / pose.z
        let reach = view.f * bound * s / eyeDistance + 2
        let numbers = rx + ry + rz + [ox, oy, oz, sx, sy, reach, bound]
            + [shape.width, shape.height, shape.depth, shape.lensX, shape.lensY, shape.lensRadius, shape.lensLength, shape.humpWidth, shape.humpHeight, shape.shell]
            + shape.shellTone + shape.bodyTone + shape.accentTone
        guard numbers.allSatisfy(\.isFinite) else { return }
        let toy = ShaderLibrary.orbitPhotoRingToy(
            .float2(view.cx, view.cy),
            .float(view.f),
            .float3(ox, oy, oz),
            .float3(rx[0], rx[1], rx[2]),
            .float3(ry[0], ry[1], ry[2]),
            .float3(rz[0], rz[1], rz[2]),
            .float3(shape.width, shape.height, shape.depth),
            .float4(shape.lensX, shape.lensY, shape.lensRadius, shape.lensLength),
            .float3(shape.humpWidth, shape.humpHeight, shape.shell),
            .float3(shape.shellTone[0], shape.shellTone[1], shape.shellTone[2]),
            .float3(shape.bodyTone[0], shape.bodyTone[1], shape.bodyTone[2]),
            .float3(shape.accentTone[0], shape.accentTone[1], shape.accentTone[2]),
            .float4(bound, min(1, max(0, flash)), min(1, max(0, alpha)), edge)
        )
        ctx.fill(Path(CGRect(x: sx - reach, y: sy - reach, width: reach * 2, height: reach * 2)), with: .shader(toy))
    }
}
