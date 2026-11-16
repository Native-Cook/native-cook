import SwiftUI

struct TokenFusionSplashFrame: View {
    let scene: TokenFusionSplashMotion.TokenFusionSplashScene
    let lag: TokenFusionSplashMotion.TokenFusionSplashScene
    let geometry: TokenFusionSplashGeometry
    var canvasOpacity: Double = 1
    typealias R = TokenFusionSplashResources

    struct TokenFusionSplashGeometry {
        var width: Double, height: Double, top: Double, bottom: Double
        var scale: Double { min(width / 402, (height - top - bottom) / 550) }
        var cy: Double { max(top + 200 * scale, min(height * 400 / 874, height - bottom - 200 * scale)) }
        var offsetX: Double { width / 2 - 201 * scale }
        var offsetY: Double { cy - 400 * scale }
        func point(_ p: CGPoint) -> CGPoint { CGPoint(x: (p.x - offsetX) / scale, y: (p.y - offsetY) / scale) }
        var bounds: (Double, Double, Double, Double) {
            ((12 + 22 * scale - offsetX) / scale, (width - 12 - 22 * scale - offsetX) / scale,
             (top + 12 + 22 * scale - offsetY) / scale, (height - bottom - 12 - 22 * scale - offsetY) / scale)
        }
    }
    var body: some View {
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.tokenFusionSplash(.floatArray(uniforms), .image(R.atlas)))
                .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
                .opacity(canvasOpacity)
            Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                if scene.word > 0 {
                    var c = context
                    c.opacity = scene.word
                    c.translateBy(x: geometry.width / 2 - R.word.width * geometry.scale / 2,
                                  y: geometry.offsetY + (scene.wordY + R.word.ascent) * geometry.scale)
                    c.scaleBy(x: geometry.scale, y: geometry.scale)
                    c.fill(R.word.path, with: .color(R.color(0xFDFBFF)))
                }
            }
            .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private var uniforms: [Float] {
        let s = scene, g = geometry
        var u: [Double] = [g.width, g.height, g.scale, g.offsetX, g.offsetY, s.x, s.y, s.r, s.sphere]
        for i in 0..<6 {
            if i < s.coins.count { let c = s.coins[i]; u += [c.x, c.y, c.r, c.reach] }
            else { u += [0, 0, 0, 0] }
        }
        for i in 0..<6 {
            if i < lag.coins.count { let c = lag.coins[i]; u += [c.x, c.y, c.r, c.reach] }
            else { u += [0, 0, 0, 0] }
        }
        for i in 0..<6 { u += i < R.colors.count ? R.colors[i] : [0, 0, 0] }
        for i in 0..<6 { u.append(i < s.coins.count ? s.coins[i].dye : 0) }
        for i in 0..<6 { u.append(i < s.coins.count ? s.coins[i].lean : 0) }
        u += [s.stretch, s.axis, s.flood, 0.025, s.mark, s.markSize, s.cut, s.ringScale]
        u += R.palette; u += [Double(s.coins.count), 1]
        return u.map(Float.init)
    }
}
