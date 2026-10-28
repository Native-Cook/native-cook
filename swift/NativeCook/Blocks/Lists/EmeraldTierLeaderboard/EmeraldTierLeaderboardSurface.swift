import SwiftUI

// Port of recastOptics.ts (uniform packing) and EmeraldRecastSurface.tsx (the Skia base scene).

enum EmeraldTierOptics {
    static let WIDTH = 393.0
    static let HEIGHT = 335.0
    static let UNIFORM_FLOATS = 36

    private static func finite(_ value: Double, _ fallback: Double = 0) -> Double { value.isFinite ? value : fallback }
    private static func unit(_ value: Double) -> Double { max(0, min(1, finite(value))) }

    /// motion vec4, four rectangle vec4s, four material vec4s (the same 36 floats RN packs).
    static func writeUniforms(_ frame: EmeraldTierRecast.Frame) -> [Float] {
        var values = [Float](repeating: 0, count: UNIFORM_FLOATS)
        values[0] = Float(unit(frame.phase)); values[1] = Float(unit(frame.response))
        values[2] = Float(unit(frame.responsePhase)); values[3] = frame.reduced ? 1 : 0
        for i in 0..<4 {
            let body = frame.surfaces[EmeraldTierRecast.SURFACE_ORDER[i]]
            let width = max(0, finite(body.width)), height = max(0, finite(body.height))
            values[4 + i * 4] = Float(finite(body.x)); values[5 + i * 4] = Float(finite(body.y))
            values[6 + i * 4] = Float(width); values[7 + i * 4] = Float(height)
            values[20 + i * 4] = Float(max(0, min(finite(body.radius), width / 2, height / 2)))
            values[21 + i * 4] = Float(max(0, min(15, finite(body.depth))))
            values[22 + i * 4] = Float(unit(body.glow)); values[23 + i * 4] = 0
        }
        return values
    }
}

/// Four glass bodies share a single bounded scene. Native labels/photos are separate siblings above it.
/// The RN scene lets a WebGPU pass draw the optics when it can and otherwise draws them in the Skia
/// shader (`flags.y = 1`); this port always uses the shader path with the optics on.
struct EmeraldTierSurface: View {
    let frame: EmeraldTierRecast.Frame
    let solid: Bool

    var body: some View {
        let v = EmeraldTierOptics.writeUniforms(frame)
        Rectangle()
            .fill(Color.black)
            .colorEffect(ShaderLibrary.emeraldRecast(
                .float4(v[0], v[1], v[2], v[3]),
                .float2(solid ? 1 : 0, 1),
                .float4(v[4], v[5], v[6], v[7]), .float4(v[8], v[9], v[10], v[11]),
                .float4(v[12], v[13], v[14], v[15]), .float4(v[16], v[17], v[18], v[19]),
                .float4(v[20], v[21], v[22], v[23]), .float4(v[24], v[25], v[26], v[27]),
                .float4(v[28], v[29], v[30], v[31]), .float4(v[32], v[33], v[34], v[35])))
            .frame(width: EmeraldTierOptics.WIDTH, height: EmeraldTierOptics.HEIGHT)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }
}
