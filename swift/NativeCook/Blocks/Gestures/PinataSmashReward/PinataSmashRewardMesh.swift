import SwiftUI

/// The exact RN 12×12 topology and vertex placement. Metal interpolates UV and shade per triangle.
enum PinataSmashRewardMesh {
    private typealias R = PinataSmashRewardResources
    typealias Frame = PinataSmashRewardMotion.Frame
    struct Vertex { let x, y, shade, u, v: Double }
    static func place(_ f: Frame, _ u: Double, _ v: Double) -> Vertex {
        let x = R.ox + u * R.k, y = R.oy + v * R.k
        var dx = x - R.cx, dy = y - R.cy
        let nx = dx / R.rx, ny = dy / R.ry, dd = 1 - nx * nx - ny * ny
        let depth = dd > 0 ? 26 * sqrt(dd) : 0
        let sy = sin(f.yaw), cy = cos(f.yaw)
        let shade = 1 - 0.22 * sy * max(-1, min(1, nx))
        dx = dx * cy + depth * sy
        let sqc = cos(f.sqAng), sqs = sin(f.sqAng)
        if f.sq != 0 {
            let a = dx * sqc + dy * sqs, b = -dx * sqs + dy * sqc
            let a2 = a * (1 - f.sq), b2 = b * (1 + f.sq * 0.5)
            dx = a2 * sqc - b2 * sqs; dy = a2 * sqs + b2 * sqc
        }
        dx *= 1 - 0.03 * f.press; dy *= 1 - 0.03 * f.press
        let ax = R.ox + R.layout.ribbon_attach_point.x * R.k
        let ay = R.oy + R.layout.ribbon_attach_point.y * R.k
        let rx = R.cx + dx - ax, ry = R.cy + dy - ay
        return Vertex(x: f.ax + rx * cos(f.roll) - ry * sin(f.roll),
                      y: f.ay + rx * sin(f.roll) + ry * cos(f.roll),
                      shade: max(0.6, min(1, shade)), u: u, v: v)
    }
    static func vertices(_ f: Frame, tail: Bool) -> [Vertex] {
        let box = tail ? R.layout.tail_box : R.layout.figure_box
        let pad = tail ? 40.0 : 14.0
        let x0 = tail ? max(0, box.x - pad) : box.x - pad
        let y0 = tail ? max(0, box.y - pad) : box.y - pad
        let x1 = tail ? min(1080, box.x + box.w + pad) : box.x + box.w + pad
        let y1 = tail ? min(1140, box.y + box.h + pad) : box.y + box.h + pad
        var result: [Vertex] = []
        for j in 0...12 { for i in 0...12 {
            let u = x0 + Double(i) * ((x1 - x0) / 12), v = y0 + Double(j) * ((y1 - y0) / 12)
            var bu = u, bv = v
            if tail && f.tail != 0 {
                let root = R.layout.tail_root, dx = u - root.x, dy = v - root.y
                let a = f.tail * min(1, hypot(dx, dy) / 300)
                bu = root.x + dx * cos(a) - dy * sin(a)
                bv = root.y + dx * sin(a) + dy * cos(a)
            }
            let p = place(f, bu, bv)
            result.append(Vertex(x: p.x, y: p.y, shade: p.shade, u: u, v: v))
        } }
        return result
    }
    static func uniforms(_ vertices: [Vertex]) -> [Float] {
        var data: [Float] = []
        // Twelve conservative row bounds reject most cells before any barycentric test.
        for j in 0..<12 {
            let row = Array(vertices[(j * 13)..<((j + 2) * 13)])
            data += [row.map(\.x).min()!, row.map(\.y).min()!, row.map(\.x).max()!, row.map(\.y).max()!].map(Float.init)
        }
        for p in vertices { data += [p.x, p.y, p.shade, R.ox + p.u * R.k, R.oy + p.v * R.k].map(Float.init) }
        return data
    }
}
