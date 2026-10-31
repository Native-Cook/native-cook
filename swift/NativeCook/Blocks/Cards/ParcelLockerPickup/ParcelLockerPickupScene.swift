import SwiftUI

/// The same complete scene is also sampled behind the frosted panel, keeping the
/// compartment's flicker, spill and moving door in the actual backdrop blur.
/// Layer order is wall/interior, projected door, thickness, additive halo, then sharp LED/header fade.
struct ParcelLockerPickupScene: View {
    var art: ParcelLockerPickupArt
    var pose: ParcelLockerPickupMotion.Pose
    var time: Double

    var body: some View {
        ZStack(alignment: .topLeading) {
            Canvas(colorMode: .nonLinear) { context, _ in
                art.base(&context, pose)
            }
            Canvas(colorMode: .nonLinear) { context, _ in
                art.door(&context, pose)
            }
            .projectionEffect(art.geometry.projection(pose.angle))
            Canvas(colorMode: .nonLinear) { context, _ in
                art.sceneEdge(&context, pose)
            }
            Canvas(colorMode: .nonLinear) { context, _ in
                art.sceneGlow(&context, pose, time)
            }
            .blendMode(.plusLighter)
            Canvas(colorMode: .nonLinear) { context, _ in
                art.sceneFront(&context, pose, time)
            }
        }
        .frame(width: art.geometry.width, height: art.geometry.height, alignment: .topLeading)
    }
}
