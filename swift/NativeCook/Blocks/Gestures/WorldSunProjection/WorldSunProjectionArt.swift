import SwiftUI

struct WorldSunProjectionArt: View {
    let image: CGImage?
    let pose: WorldSunProjectionPose
    let grid: Bool
    var markerAlpha = 1.0

    var body: some View {
        ZStack(alignment: .topLeading) {
            if let image {
                Image(decorative: image, scale: 1).resizable()
                    .frame(width: 393, height: 852)
                    .layerEffect(ShaderLibrary.worldSunProjectionEnvironment(.float2(393, 852), .float(pose.yaw), .float(pose.pitch)),
                                 maxSampleOffset: CGSize(width: 393, height: 852))
                    .clipped()
            } else { WorldSunProjectionResources.color(0x7F9FA2) }
            Canvas { context, _ in
                let camera = WorldSunProjectionCamera(pose)
                let white = WorldSunProjectionResources.color(0xFAF9F3)
                if grid {
                    for elevation in [-15.0, 0, 15, 30, 45, 60, 75] {
                        let directions = (0...96).map { i -> WorldSunProjectionVector in
                            let a = (-100 + Double(i) * 200 / 96) * .pi / 180, e = elevation * .pi / 180
                            return .init(x: sin(a) * cos(e), y: sin(e), z: cos(a) * cos(e))
                        }
                        context.stroke(projectedPath(directions, camera: camera),
                                       with: .color(WorldSunProjectionResources.color(0xCBD9D8, alpha: 0.4)), lineWidth: 0.55)
                    }
                }
                let guide = (0..<64).map { WorldSunProjectionMath.sun(-0.1 + Double($0) / 63 * 1.22) }
                let path = projectedPath(guide, camera: camera)
                context.stroke(path, with: .color(.black.opacity(0.26)), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
                context.stroke(path, with: .color(white.opacity(0.72)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
                var previous: CGPoint?
                for hour in 6...11 {
                    let q = Double(hour * 60 - 309) / 350
                    guard let p = camera.project(WorldSunProjectionMath.sun(q)), p.x > -25, p.x < 360,
                          p.y > 110, p.y < 710 else { continue }
                    if let previous, hypot(p.x - previous.x, p.y - previous.y) < 28 { continue }
                    context.draw(Text(String(format: "%02d:00", hour)).font(.system(size: 9.5)).foregroundStyle(white),
                                 at: CGPoint(x: p.x + 10, y: p.y), anchor: .leading)
                    previous = p
                }
                if let p = camera.project(WorldSunProjectionMath.sun(0.12)) {
                    var tick = Path(); tick.move(to: CGPoint(x: p.x - 4, y: p.y)); tick.addLine(to: CGPoint(x: p.x + 4, y: p.y))
                    tick.move(to: CGPoint(x: p.x, y: p.y - 4)); tick.addLine(to: CGPoint(x: p.x, y: p.y + 4))
                    context.stroke(tick, with: .color(WorldSunProjectionResources.color(0xD5CC47)), lineWidth: 1)
                }
                if let sun = camera.project(WorldSunProjectionMath.sun(pose.q)) {
                    var selection = context; selection.opacity = markerAlpha
                    selection.fill(Path(ellipseIn: CGRect(x: sun.x - 11, y: sun.y - 11, width: 22, height: 22)), with: .color(white))
                    selection.stroke(Path(ellipseIn: CGRect(x: sun.x - 11, y: sun.y - 11, width: 22, height: 22)),
                                   with: .color(.black.opacity(0.23)), lineWidth: 0.8)
                    let center = CGPoint(x: WorldSunProjectionMath.clamp(sun.x + 36, 52, 341),
                                         y: WorldSunProjectionMath.clamp(sun.y - 20, 111, 698))
                    let bubble = CGRect(x: center.x - 36, y: center.y - 12.5, width: 72, height: 25)
                    if abs(center.x - sun.x - 36) > 0.1 || abs(center.y - sun.y + 20) > 0.1 {
                        let dx = sun.x - center.x, dy = sun.y - center.y, length = max(1, hypot(dx, dy))
                        var connector = Path(); connector.move(to: CGPoint(x: center.x + dx / length * 13, y: center.y + dy / length * 13))
                        connector.addLine(to: CGPoint(x: center.x + dx / length * 22, y: center.y + dy / length * 22))
                        selection.stroke(connector, with: .color(white.opacity(0.8)), lineWidth: 0.8)
                    }
                    selection.fill(Path(roundedRect: bubble, cornerRadius: 12.5), with: .color(white))
                    selection.draw(Text(WorldSunProjectionMath.time(pose.q)).font(.system(size: 10.5, weight: .semibold)).monospacedDigit()
                        .foregroundStyle(WorldSunProjectionResources.color(0x293432)), at: center)
                }
                var reticle = Path()
                for sign in [-1.0, 1] {
                    reticle.move(to: CGPoint(x: 196.5 + sign * 5, y: 426)); reticle.addLine(to: CGPoint(x: 196.5 + sign * 8, y: 426))
                    reticle.move(to: CGPoint(x: 196.5, y: 426 + sign * 5)); reticle.addLine(to: CGPoint(x: 196.5, y: 426 + sign * 8))
                }
                context.stroke(reticle, with: .color(white.opacity(0.44)), lineWidth: 0.7)
            }.frame(width: 393, height: 852).clipped()
        }.frame(width: 393, height: 852, alignment: .topLeading).allowsHitTesting(false).accessibilityHidden(true)
    }
    private func projectedPath(_ directions: [WorldSunProjectionVector], camera: WorldSunProjectionCamera) -> Path {
        var result = Path(), previous: WorldSunProjectionVector?
        let near = 0.0600001
        for direction in directions {
            if let previous {
                let z0 = previous.dot(camera.forward), z1 = direction.dot(camera.forward)
                if z0 > near || z1 > near {
                    let denominator = z1 - z0
                    let u = abs(denominator) > 1e-12 ? (near - z0) / denominator : 0
                    let crossing = previous * (1 - u) + direction * u
                    let a = z0 > near ? previous : crossing
                    let b = z1 > near ? direction : crossing
                    if let p = camera.project(a), let q = camera.project(b) {
                        result.move(to: p); result.addLine(to: q)
                    }
                }
            }
            previous = direction
        }
        return result
    }
}
