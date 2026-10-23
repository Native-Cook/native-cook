import SwiftUI

/// Pure Round 3 ambient-field values and SwiftUI shader adapters for EventPassShare.
/// Callers own clocks, follower state, safe-area geometry, and reduced-motion policy.
enum EventPassShareAmbient {
    struct Light {
        let name: String
        let x: Double
        let y: Double
        let radius: Double
        let intensity: Double
        let orbit: Double
        let phase: Double
        let lch: SIMD3<Double>
    }

    struct PageUniforms {
        let size: CGSize
        let scale: Double
        let referenceYAtCanvasZero: Double
        let sheetOffset: Double
        let p3: Double
        let driftAngle: Double
        let tiltX: Double
        let tiltY: Double
        let glow: Double
    }

    struct SheetUniforms {
        let size: CGSize
        let scale: Double
        let referenceYAtCanvasZero: Double
        let sheetOffset: Double
        let p3: Double
        let driftAngle: Double
        let tiltX: Double
        let tiltY: Double
        let glow: Double
        /// Resolved spillUniform: tilt X, shaded tilt Y, and foil light amount.
        let card: SIMD3<Double>
    }

    static let AURORA = Aurora(
        base: SIMD3(0.19, 0.035, 232),
        lights: [
            Light(name: "emerald", x: 112, y: 214, radius: 150, intensity: 0.95, orbit: 26, phase: 0.0, lch: SIMD3(0.63, 0.15, 162)),
            Light(name: "teal", x: 338, y: 352, radius: 128, intensity: 0.85, orbit: 22, phase: 2.1, lch: SIMD3(0.66, 0.115, 203)),
            Light(name: "gold", x: 372, y: 226, radius: 60, intensity: 0.62, orbit: 14, phase: 4.0, lch: SIMD3(0.79, 0.13, 66)),
            Light(name: "violet", x: 52, y: 572, radius: 150, intensity: 0.85, orbit: 24, phase: 1.0, lch: SIMD3(0.5, 0.17, 290)),
            Light(name: "rose", x: 356, y: 652, radius: 104, intensity: 0.6, orbit: 18, phase: 3.3, lch: SIMD3(0.58, 0.16, 345)),
        ]
    )

    struct Aurora {
        let base: SIMD3<Double>
        let lights: [Light]
    }

    struct Parameters {
        let period: Double
        let warp: Double
        let warpScale: Double
        let warpCircle: Double
        let parallax: Double
        let hueY: Double
        let hueX: Double
        let omega: Double
        let sheetSeeThrough: Double
        let sheetTopBleed: Double
        let sheetBleedDepth: Double
        let stageGainTop: Double
        let stageGainMid: Double
        let stageGainBottom: Double
        let stageFadeFrom: Double
        let stageFadeMid: Double
        let stageFadeTo: Double
        let floorReflection: Double
        let floorDepth: Double
        let spillRest: Double
        let spillReach: Double
        let spillStrength: Double
        let spillShift: Double
    }

    static let AMBIENT = Parameters(
        period: 8,
        warp: 16,
        warpScale: 0.0075,
        warpCircle: 0.85,
        parallax: 2.2,
        hueY: 0.02,
        hueX: 0.015,
        omega: 7,
        sheetSeeThrough: 0.2,
        sheetTopBleed: 0.22,
        sheetBleedDepth: 38,
        stageGainTop: 1.25,
        stageGainMid: 0.7,
        stageGainBottom: 0.38,
        stageFadeFrom: 236,
        stageFadeMid: 330,
        stageFadeTo: 690,
        floorReflection: 0.3,
        floorDepth: 70,
        spillRest: 0.12,
        spillReach: 20,
        spillStrength: 0.55,
        spillShift: 1.6
    )

    /// Drift angle in radians; negative input follows JavaScript's positive modulo behavior.
    static func ambientAngle(_ time: Double) -> Double {
        let turns = (time.truncatingRemainder(dividingBy: AMBIENT.period)) / AMBIENT.period
        return (turns < 0 ? turns + 1 : turns) * .pi * 2
    }

    /// Shading tilt and foil light amount shared by the poster foil and ambient spill.
    static func spillUniform(tiltX: Double, tiltY: Double, flipAngle: Double) -> SIMD3<Double> {
        let turn = min(90, max(0, flipAngle <= 90 ? flipAngle : 180 - flipAngle))
        let shadeY = tiltY + turn * 0.2
        let tilt = min(1, hypot(tiltX / 6, tiltY / 7.5))
        let lit = min(1, tilt + sin((turn * .pi) / 180) * 0.8)
        return SIMD3(tiltX, shadeY, lit * lit * (3 - 2 * lit))
    }

    /// In-place semi-implicit integration of the critically damped tilt and glow follower.
    /// State order is x, vx, y, vy, glow, vglow, matching the React Native port.
    static func stepAmbientFollower(
        _ state: inout [Double],
        tiltX: Double,
        tiltY: Double,
        glow: Double,
        deltaTime: Double
    ) {
        precondition(state.count >= 6, "EventPassShare ambient follower requires six values")
        let omega = AMBIENT.omega
        var remaining = min(max(deltaTime, 0), 0.1)
        while remaining > 0 {
            let step = min(remaining, 1.0 / 240.0)
            remaining -= step
            state[1] += (omega * omega * (tiltX - state[0]) - 2 * omega * state[1]) * step
            state[0] += state[1] * step
            state[3] += (omega * omega * (tiltY - state[2]) - 2 * omega * state[3]) * step
            state[2] += state[3] * step
            state[5] += (omega * omega * (glow - state[4]) - 2 * omega * state[5]) * step
            state[4] += state[5] * step
        }
    }

    static func pageBackdrop(_ uniforms: PageUniforms) -> some View {
        Rectangle()
            .fill(Color.black)
            .colorEffect(pageShader(uniforms))
            .accessibilityHidden(true)
            .allowsHitTesting(false)
    }

    static func sheetBackdrop(_ uniforms: SheetUniforms) -> some View {
        Rectangle()
            .fill(Color.black)
            .colorEffect(sheetShader(uniforms))
            .accessibilityHidden(true)
            .allowsHitTesting(false)
    }

    private static func pageShader(_ uniforms: PageUniforms) -> Shader {
        ShaderLibrary.eventPassShareAmbientPage(
            .float2(uniforms.size),
            .float4(
                Float(uniforms.scale),
                Float(uniforms.referenceYAtCanvasZero),
                Float(uniforms.sheetOffset),
                Float(uniforms.p3)
            ),
            .float4(Float(uniforms.driftAngle), Float(uniforms.tiltX), Float(uniforms.tiltY), Float(uniforms.glow))
        )
    }

    private static func sheetShader(_ uniforms: SheetUniforms) -> Shader {
        return ShaderLibrary.eventPassShareAmbientSheet(
            .float2(uniforms.size),
            .float4(
                Float(uniforms.scale),
                Float(uniforms.referenceYAtCanvasZero),
                Float(uniforms.sheetOffset),
                Float(uniforms.p3)
            ),
            .float4(Float(uniforms.driftAngle), Float(uniforms.tiltX), Float(uniforms.tiltY), Float(uniforms.glow)),
            .float4(Float(uniforms.card.x), Float(uniforms.card.y), Float(uniforms.card.z), 0)
        )
    }
}
