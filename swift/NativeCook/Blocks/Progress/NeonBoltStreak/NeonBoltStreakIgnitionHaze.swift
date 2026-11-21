import SwiftUI

/// The broad ignition light is calculated once per pixel so overlapping heads use the brightest contribution instead
/// of accumulating enough yellow to flatten the zinc panel.
struct NeonBoltStreakIgnitionHaze: View {
    let frame: NeonBoltStreakFrame
    let size: CGSize

    var body: some View {
        if frame.head.contains(where: { $0 > 0.001 }) {
            Rectangle()
                .fill(.white)
                .colorEffect(shader)
                .blendMode(.plusLighter)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
        }
    }

    private var shader: Shader {
        let scale = size.width / 402
        let vertical = size.height / scale - 874
        let heads = frame.head.map(Float.init)
        let fills = frame.fill.map(Float.init)
        let lights = frame.lit.map(Float.init)
        let offsets = frame.dy.map(Float.init)
        return ShaderLibrary.neonBoltStreakIgnitionHaze(
            .float(Float(scale)),
            .float(Float(vertical / 2)),
            .float(Float(frame.jolt)),
            .float4(heads[0], heads[1], heads[2], heads[3]),
            .float4(heads[4], heads[5], heads[6], 0),
            .float4(fills[0], fills[1], fills[2], fills[3]),
            .float4(fills[4], fills[5], fills[6], 0),
            .float4(lights[0], lights[1], lights[2], lights[3]),
            .float4(lights[4], lights[5], lights[6], 0),
            .float4(offsets[0], offsets[1], offsets[2], offsets[3]),
            .float4(offsets[4], offsets[5], offsets[6], 0)
        )
    }
}
