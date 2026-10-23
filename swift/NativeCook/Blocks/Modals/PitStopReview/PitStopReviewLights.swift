import SwiftUI

/// Full-screen overlay for the moment the five start lights become the composer's five stars (and back). A light
/// leaves the gantry, arcs down with a short comet trail and morphs (signed-distance blend in the lamp shader) from a
/// red lamp into the white outline star that lands in its slot; on Close the stars rise back into red lamps.
/// Window coordinates: `top` is the hero box's window y, `slots` the star centres (x, y) when the composer is open.
struct PitStopReviewLights: View {
    typealias Motion = PitStopReviewMotion
    typealias G = PitStopReviewTheme.Gantry
    typealias ST = PitStopReviewTheme.Stars

    let width: CGFloat
    let height: CGFloat
    let scale: CGFloat
    let top: CGFloat
    let slots: [CGPoint]
    let scene: Double

    private static let trail = 3
    private static let trailStep = 0.07

    /// Point on the arc from the gantry light to the star slot, u 0…1.
    static func arc(gx: Double, gy: Double, px: Double, py: Double, i: Int, u: Double, s: Double) -> (Double, Double) {
        let cx = (gx + px) / 2 + Double(i - 2) * 16 * s, cy = gy + (py - gy) * 0.12 - 40 * s
        let a = 1 - u
        return (a * a * gx + 2 * a * u * cx + u * u * px, a * a * gy + 2 * a * u * cy + u * u * py)
    }

    private var inFlight: Bool {
        (0..<5).contains { i in
            let a = Motion.flyIn(scene, i), b = Motion.flyBack(scene, i)
            return (a > 0 && a < 1) || (b > 0 && b < 1)
        }
    }

    private func uniforms() -> (lamp: [Float], look: [Float]) {
        var lamp: [Float] = [], look: [Float] = []
        let sc = Double(scale)
        let lift = (1 - Motion.panel(scene)) * Motion.M.panelRise * sc
        for i in 0..<5 {
            let fin = Motion.flyIn(scene, i), back = Motion.flyBack(scene, i)
            let going = fin > 0 && fin < 1, returning = back > 0 && back < 1
            let u = going ? fin : 1 - back
            let gx = (G.cx + Double(i - 2) * G.pitch) * sc, gy = Double(top) + G.cy * sc
            let hasSlots = slots.count >= 5
            let px = hasSlots ? Double(slots[i].x) : gx, py = (hasSlots ? Double(slots[i].y) : gy) + lift
            let m = Motion.smooth((u - 0.2) / 0.6)
            let r = (G.radius + (ST.radius - G.radius) * m) * sc
            let live = (going || returning) && hasSlots
            for k in 0...Self.trail {
                let uk = Motion.clamp(going ? u - Double(k) * Self.trailStep : u + Double(k) * Self.trailStep)
                let (x, y) = Self.arc(gx: gx, gy: gy, px: px, py: py, i: i, u: uk, s: sc)
                lamp.append(contentsOf: [Float(x), Float(y), Float(r * (1 - Double(k) * 0.18)), Float(m)])
                // Head: full; trail: fainter, with no stroke so it reads as light, not as extra stars.
                let alpha = live ? (k == 0 ? 1 : (0.32 - Double(k) * 0.08) * (1 - abs(m - 0.5))) : 0
                look.append(contentsOf: [Float(alpha), k == 0 ? 1 : 0.6, k == 0 ? Float(2 * sc) : 0, 0])
            }
        }
        return (lamp, look)
    }

    var body: some View {
        let boxY = top + (G.cy - 3.2 * G.radius) * scale
        ZStack(alignment: .topLeading) {
            // Warm-up: compiles the lamp shader on mount (1 pt, transparent sprite) instead of on the first flight.
            Rectangle().fill(.white).frame(width: 1, height: 1)
                .colorEffect(ShaderLibrary.pitStopReviewLamps(.float2(0, 0), .floatArray([0, 0, 1, 0]), .floatArray([0.01, 1, 2, 0])))
                .opacity(0.01)
            if inFlight {
                let u = uniforms()
                Rectangle().fill(.white).frame(width: width, height: max(1, height - boxY))
                    .colorEffect(ShaderLibrary.pitStopReviewLamps(.float2(0, Float(boxY)), .floatArray(u.lamp), .floatArray(u.look)))
                    .offset(y: boxY)
            }
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}
