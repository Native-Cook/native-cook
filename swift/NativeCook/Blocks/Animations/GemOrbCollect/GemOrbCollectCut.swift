import Foundation
import simd

// The same clipped girdle, crown, and pavilion half-spaces as gem-cut.ts.
enum GemOrbCollectCut {
    struct Corner {
        let point: SIMD3<Double>
        let normal: SIMD3<Double>
    }
    private struct Edge {
        let first: Int
        let second: Int
        let planeA: Int
        let planeB: Int
    }

    private struct Topology {
        let vertices: [SIMD3<Double>]
        let edges: [Edge]
    }

    static let planes: [SIMD4<Float>] = {
        let sides = 7
        let sector = 2 * Double.pi / Double(sides)
        let apothem = cos(Double.pi / Double(sides))
        let corner = 0.935
        let tangent = (corner - apothem * cos(Double.pi / Double(sides))) / sin(Double.pi / Double(sides))
        let cutRadius = hypot(apothem, tangent)
        var raw: [SIMD4<Double>] = []

        func add(_ x: Double, _ y: Double, _ z: Double, _ offset: Double) {
            let magnitude = sqrt(x * x + y * y + z * z)
            raw.append(SIMD4(x / magnitude, y / magnitude, z / magnitude, offset / magnitude))
        }

        for index in 0..<sides {
            let angle = Double.pi / 2 + (Double(index) + 0.5) * sector
            let x = cos(angle)
            let y = sin(angle)
            add(cos(angle + sector / 2), sin(angle + sector / 2), 0, corner / cutRadius)
            add(x * 0.58, y * 0.58, 1, 0.28 + 0.50 * 0.58)
            add(x, y, -0.80, 0.94)
            add(cos(angle + sector / 2), sin(angle + sector / 2), -1.50, 1.32)
        }
        add(0, 0, 1, 0.28)
        add(0, 0, -1, 0.84)
        let extent = topology(for: raw).vertices.reduce(0.0) { max($0, hypot($1.x, $1.y)) }
        return raw.map { plane in
            let scaled = SIMD3(plane.x * extent, plane.y * extent, plane.z)
            let magnitude = simd_length(scaled)
            return SIMD4(Float(scaled.x / magnitude), Float(scaled.y / magnitude),
                         Float(scaled.z / magnitude), Float(plane.w / magnitude))
        }
    }()

    private static let cutTopology: Topology = topology(for: planes.map {
        SIMD4(Double($0.x), Double($0.y), Double($0.z), Double($0.w))
    })

    private static let corners: [Corner] = cutTopology.vertices.map { point in
        var normal = SIMD3<Double>(repeating: 0)
        for plane in planes {
            let candidate = SIMD3(Double(plane.x), Double(plane.y), Double(plane.z))
            if abs(simd_dot(candidate, point) - Double(plane.w)) < 0.00001 {
                normal += candidate
            }
        }
        return Corner(point: point, normal: normal / max(simd_length(normal), 0.00001))
    }

    static func rows(yaw: Double, pitch: Double, roll: Double) -> (SIMD4<Float>, SIMD4<Float>, SIMD4<Float>) {
        let cy = cos(yaw), sy = sin(yaw), cp = cos(pitch), sp = sin(pitch)
        let cr = cos(roll), sr = sin(roll)
        return (
            SIMD4(Float(cy * cr), Float(-cy * sr), Float(sy), 0),
            SIMD4(Float(cp * sr + sp * sy * cr), Float(cp * cr - sp * sy * sr), Float(-sp * cy), 0),
            SIMD4(Float(sp * sr - cp * sy * cr), Float(sp * cr + cp * sy * sr), Float(cp * cy), 0)
        )
    }

    static func outline(row0: SIMD4<Float>, row1: SIMD4<Float>, row2: SIMD4<Float>) -> [SIMD4<Float>] {
        let a = SIMD3(Double(row0.x), Double(row0.y), Double(row0.z))
        let b = SIMD3(Double(row1.x), Double(row1.y), Double(row1.z))
        let c = SIMD3(Double(row2.x), Double(row2.y), Double(row2.z))
        let vertices = cutTopology.vertices.map { vertex -> SIMD2<Double> in
            let viewZ = simd_dot(c, vertex)
            let projection = 7 / max(7 - viewZ, 0.001)
            return SIMD2(simd_dot(a, vertex) * projection, simd_dot(b, vertex) * projection)
        }
        let visible = planes.map { plane in
            7 * simd_dot(c, SIMD3(Double(plane.x), Double(plane.y), Double(plane.z))) > Double(plane.w)
        }
        var result: [SIMD4<Float>] = []
        for edge in cutTopology.edges where visible[edge.planeA] != visible[edge.planeB] {
            let first = vertices[edge.first]
            let second = vertices[edge.second]
            var normal = SIMD2(second.y - first.y, first.x - second.x)
            let magnitude = simd_length(normal)
            if magnitude < 0.000001 { continue }
            normal /= magnitude
            let offset = simd_dot(normal, first)
            let sign = offset < 0 ? -1.0 : 1.0
            result.append(SIMD4(Float(normal.x * sign), Float(normal.y * sign), Float(offset * sign), 0))
        }
        while result.count < 32 { result.append(SIMD4(0, 0, 10, 0)) }
        return Array(result.prefix(32))
    }

    static func glints(row0: SIMD4<Float>, row1: SIMD4<Float>, row2: SIMD4<Float>,
                       centreX: Double, centreY: Double, radius: Double, stretch: Double,
                       charge: Double, flare: Double, time: Double) -> [SIMD4<Float>] {
        let a = SIMD3(Double(row0.x), Double(row0.y), Double(row0.z))
        let b = SIMD3(Double(row1.x), Double(row1.y), Double(row1.z))
        let c = SIMD3(Double(row2.x), Double(row2.y), Double(row2.z))
        let lamps = [SIMD3(-0.36, 0.47, 0.81), SIMD3(0.5, 0.12, 0.86), SIMD3(-0.42, -0.44, 0.79)]
        var points: [(Double, Double, Double)] = []
        var ranked: [Double] = []
        for (index, corner) in corners.enumerated() {
            let view = SIMD3(simd_dot(a, corner.point), simd_dot(b, corner.point),
                             simd_dot(c, corner.point))
            let normal = SIMD3(simd_dot(a, corner.normal), simd_dot(b, corner.normal),
                               simd_dot(c, corner.normal))
            var strength = 0.0
            if normal.z * (7 - view.z) - normal.x * view.x - normal.y * view.y > 0.15 {
                for lamp in lamps {
                    let facing = simd_dot(normal, lamp)
                    if facing > 0 { strength += pow(facing, 26) }
                }
            }
            let projection = 7 / max(7 - view.z, 0.001)
            let x = centreX + radius * view.x * projection / sqrt(stretch)
            let y = centreY - radius * view.y * projection * stretch
            strength = min(1, strength * (0.95 + 0.65 * charge + 1.2 * flare)
                * (0.88 + 0.12 * sin(time * .pi / 2 + Double(index) * 1.7)))
            let dx = (x - centreX) * sqrt(stretch) / max(radius, 0.01)
            let dy = (y - centreY) / (max(radius, 0.01) * stretch)
            let clearRadius = 0.54 + 0.26 * flare
            strength *= min(1, max(0, (hypot(dx, dy) - clearRadius) / 0.10))
            points.append((x, y, strength))
            ranked.append(strength)
        }
        ranked.sort(by: >)
        while ranked.count < 6 { ranked.append(0) }
        let idleThreshold = ranked[3] + (ranked[4] - ranked[3]) * charge
        let threshold = idleThreshold + (ranked[5] - idleThreshold) * flare
        let rendered = points.compactMap { x, y, strength -> SIMD4<Float>? in
            let gain = min(1, max(0, strength - threshold) * 2.8)
                * min(1, 0.48 + 0.35 * charge + 0.52 * flare)
            if gain < 0.005 { return nil }
            let scale = radius * (0.14 + (0.34 + 0.10 * flare) * gain) / 128
            return SIMD4(Float(x), Float(y), Float(gain), Float(scale))
        }
        var output = Array(rendered.prefix(8))
        while output.count < 8 { output.append(.zero) }
        return output
    }

    private static func topology(for planes: [SIMD4<Double>]) -> Topology {
        var vertices: [SIMD3<Double>] = []
        for first in planes.indices {
            for second in (first + 1)..<planes.count {
                for third in (second + 1)..<planes.count {
                    let a = planes[first], b = planes[second], c = planes[third]
                    let na = a.xyz, nb = b.xyz, nc = c.xyz
                    let crossBC = simd_cross(nb, nc)
                    let determinant = simd_dot(na, crossBC)
                    if abs(determinant) < 0.000001 { continue }
                    let point = (a.w * crossBC + b.w * simd_cross(nc, na) + c.w * simd_cross(na, nb)) / determinant
                    if planes.contains(where: { simd_dot($0.xyz, point) > $0.w + 0.000001 }) { continue }
                    if vertices.contains(where: { simd_length($0 - point) < 0.00001 }) { continue }
                    vertices.append(point)
                }
            }
        }
        var edges: [Edge] = []
        for first in planes.indices {
            for second in (first + 1)..<planes.count {
                var endpoint: Int?
                for index in vertices.indices {
                    let point = vertices[index]
                    let onFirst = abs(simd_dot(planes[first].xyz, point) - planes[first].w) < 0.00001
                    let onSecond = abs(simd_dot(planes[second].xyz, point) - planes[second].w) < 0.00001
                    if onFirst && onSecond {
                        if let endpoint {
                            edges.append(Edge(first: endpoint, second: index, planeA: first, planeB: second))
                            break
                        }
                        endpoint = index
                    }
                }
            }
        }
        return Topology(vertices: vertices, edges: edges)
    }
}

private extension SIMD4 where Scalar == Double {
    var xyz: SIMD3<Double> { SIMD3(x, y, z) }
}
