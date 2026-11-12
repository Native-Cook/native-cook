import SwiftUI

/// An unwarped registered image supplies the stitchable shader's texture layer.
struct PinataSmashRewardMeshImage: View {
    let image: CGImage
    let uniforms: [Float]
    private typealias R = PinataSmashRewardResources
    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.clear
            Image(decorative: image, scale: 1)
                .resizable().interpolation(.medium)
                .frame(width: 1080 * R.k, height: 1140 * R.k, alignment: .topLeading)
                .offset(x: R.ox, y: R.oy)
        }
        .frame(width: 402, height: 874, alignment: .topLeading)
        .drawingGroup(opaque: false, colorMode: .nonLinear)
        .layerEffect(ShaderLibrary.pinataSmashRewardMesh(.floatArray(uniforms)),
                     maxSampleOffset: CGSize(width: 600, height: 1000))
    }
}
