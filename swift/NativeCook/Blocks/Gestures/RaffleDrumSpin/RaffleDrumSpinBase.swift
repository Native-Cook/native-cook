import SwiftUI

/// The native counterpart of recordBase: none of these inputs depend on story time.
struct RaffleDrumSpinBase: View, Equatable {
    let size: CGSize

    private static let resting: RaffleDrumSpinMotion.Frame = {
        var frame = RaffleDrumSpinMotion.frameAt(0)
        frame.glow = 0
        frame.dim = 0
        return frame
    }()

    var body: some View {
        ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(Color(red: 0.025, green: 0.013, blue: 0.052))
                .colorEffect(ShaderLibrary.raffleDrumSpinBackground(
                    .float2(max(1, size.width), max(1, size.height)),
                    .float2(Float(205 * size.width / 402), Float(148 * size.height / 874)),
                    .float(0), .float(0)
                ))
            Canvas { context, _ in
                RaffleDrumSpinDraw.base(context: &context, size: size, frame: Self.resting)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .drawingGroup(opaque: true)
        .allowsHitTesting(false)
    }
}
