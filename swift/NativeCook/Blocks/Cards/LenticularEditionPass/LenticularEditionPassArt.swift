import SwiftUI

/// A single inverse-ray lens pass keeps all230 ridges in material coordinates.
struct LenticularEditionPassArt: View {
    let frame: LenticularEditionPassFrame
    let logicalHeight: Double
    private let resources = LenticularEditionPassResources.shared

    var body: some View {
        let rotation = LenticularEditionPassGeometry.rotation(frame.pose)
        ZStack(alignment: .topLeading) {
            LenticularEditionPassResources.color(0xEDEFE9)
            Canvas { context, _ in
                context.draw(resources.paperImage, in: CGRect(x: 0, y: 0, width: 393, height: 1024))
            }
            .frame(width: 393, height: logicalHeight, alignment: .topLeading)

            shadows
                .frame(width: 393, height: logicalHeight, alignment: .topLeading)

            resources.atlasImage
                .resizable()
                .interpolation(.high)
                .frame(width: 350, height: 426, alignment: .topLeading)
                .layerEffect(ShaderLibrary.lenticularEditionPassLens(
                    .float3(rotation[0], rotation[1], rotation[2]),
                    .float3(rotation[3], rotation[4], rotation[5]),
                    .float3(rotation[6], rotation[7], rotation[8]),
                    .float(frame.pose.yaw), .float(frame.explicitViewB ?? -1)),
                    maxSampleOffset: CGSize(width: 40, height: 240))
                .frame(width: 350, height: 265, alignment: .topLeading)
                .clipped()
                .offset(x: 21.5, y: 242.5)
        }
        .frame(width: 393, height: logicalHeight, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var shadows: some View {
        Canvas { context, _ in
            let corners = LenticularEditionPassGeometry.corners(pose: frame.pose)
            let rest = LenticularEditionPassGeometry.corners(pose: LenticularEditionPassPose(yaw: -9, pitch: 3, roll: -2))
            let span = (corners.map(\.x).max()! - corners.map(\.x).min()!)
            let restSpan = rest.map(\.x).max()! - rest.map(\.x).min()!
            let center = LenticularEditionPassGeometry.project(local: .zero, pose: frame.pose)
            let width = 250 * min(1.04, max(0.92, span / restSpan))
            let x = 196.5 + 0.18 * (center.x - 196.5)
            var ambient = context
            ambient.addFilter(.blur(radius: 27))
            ambient.fill(Path(ellipseIn: CGRect(x: x - width / 2, y: 487, width: width, height: 30)),
                         with: .color(LenticularEditionPassResources.color(0x192C35, alpha: 0.14)))
            var contact = context
            contact.translateBy(x: 0, y: 1.5)
            contact.addFilter(.blur(radius: 2.5))
            contact.fill(projectedOutline, with: .color(LenticularEditionPassResources.color(0x192C35, alpha: 0.23)))
        }
    }

    private var projectedOutline: Path {
        let radius = 6.0
        let centers = [CGPoint(x: 160.5, y: -99.5), CGPoint(x: 160.5, y: 99.5),
                       CGPoint(x: -160.5, y: 99.5), CGPoint(x: -160.5, y: -99.5)]
        var path = Path()
        for corner in 0..<4 {
            for step in 0...12 {
                let a = (-90 + Double(corner) * 90 + Double(step) * 7.5) * .pi / 180
                let local = CGPoint(x: centers[corner].x + radius * cos(a), y: centers[corner].y + radius * sin(a))
                let p = LenticularEditionPassGeometry.project(local: local, pose: frame.pose)
                if corner == 0 && step == 0 { path.move(to: p) } else { path.addLine(to: p) }
            }
        }
        path.closeSubpath()
        return path
    }
}
