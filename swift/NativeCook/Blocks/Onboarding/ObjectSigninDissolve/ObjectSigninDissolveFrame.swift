import SwiftUI

struct ObjectSigninDissolveFrame: View {
    let driver: ObjectSigninDissolveDriver
    let layout: ObjectSigninDissolveLayout
    let reduce: Bool
    typealias M = ObjectSigninDissolveMotion

    var body: some View {
        let f = driver.frame, l = layout, s = l.scale
        let radius = reduce ? (f.look.wipeP > 0.5 || f.lightMix > 0.01 ? l.reach : 0) : l.reach * M.ease(f.look.wipeP)
        let origin = CGPoint(x: l.width / 2 - 112 * s, y: l.cy - 218 * s)
        ZStack(alignment: .topLeading) {
            ObjectSigninDissolvePage(light: false, layout: l, glow: f.glow, pressed: driver.pressed)
            ObjectSigninDissolvePage(light: true, layout: l, glow: f.glow, pressed: driver.pressed)
                .compositingGroup()
                .mask(alignment: .topLeading) {
                    Circle().fill(.white).frame(width: radius * 2, height: radius * 2)
                        .offset(x: -80 - radius, y: l.height + 80 - radius)
                }
                .opacity(reduce ? f.lightMix : 1)
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.objectSigninDissolvePear(.float2(origin), .floatArray(uniforms(f))))
                .frame(width: 224 * s, height: 396 * s, alignment: .topLeading)
                .offset(x: origin.x, y: origin.y)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private func uniforms(_ f: ObjectSigninDissolveDriver.Frame) -> [Float] {
        let k = f.look, l = layout, s = l.scale
        // RN intentionally fixes AA at 1/3 pt, even on a 2x display.
        var u: [Double] = [l.width / 2, l.cy, 1.0 / 3, 1, k.yaw * .pi / 180, k.scale * s, k.key]
        u += [(-214 + 410 * k.c) * s, k.roll, k.pitchP, k.seam, (f.clock * 8 * s).truncatingRemainder(dividingBy: 5 * s)]
        u += [-80, l.height + 80, reduce ? 0 : l.reach * M.ease(k.wipeP), reduce ? f.lightMix : 0]
        u += [1, 62.0 / 255, 165.0 / 255, 1, 210.0 / 255, 62.0 / 255, 1, 246.0 / 255, 216.0 / 255, 5 * s, 1.2 * s]
        return u.map(Float.init)
    }
}
