import SwiftUI

struct CoinPileCollectFrame: View {
    typealias R = CoinPileCollectResources
    let frame: CoinPileCollectDriver.Frame
    let geometry: Geometry
    let resources: R
    struct Geometry {
        let width: Double, height: Double, scale: Double, ox: Double, oy: Double, px: Double
        var bottom: Double { (height - oy) / scale }
    }
    private var atlas: Image { Image(decorative: R.atlas, scale: 1) }
    private var coins: [Float] {
        var result: [Float] = []; result.reserveCapacity(208)
        for i in 0..<26 {
            let o = i * 32, a = frame.a
            result += [a[o], a[o + 1], a[o + 4], a[o + 8] + 2 * a[o + 12], a[o + 5], a[o + 6], a[o + 7], a[o + 9]].map(Float.init)
        }
        return result
    }
    private var exchangeAxis: [Float] {
        let sn = sin(frame.theta), cs = cos(frame.theta)
        var ax = sn, ay = -cs * sin(0.16), face = 0.0
        if cs * cos(0.16) < 0 { ax = -ax; ay = -ay; face = 1 }
        // RN evaluates xcoinPose in Double, then packs its uniforms as Float32.
        return [ax, ay, 0, face].map(Float.init)
    }
    var body: some View {
        let g = geometry, art = CoinPileCollectArt(res: resources)
        ZStack(alignment: .topLeading) {
            page
            ZStack(alignment: .topLeading) {
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in
                    art.background(context, bottom: g.bottom, theta: frame.theta)
                }
                .frame(width: 402, height: g.height / g.scale - min(g.oy / g.scale, 0), alignment: .topLeading)
                Rectangle().fill(.white)
                    .frame(width: 172, height: 162, alignment: .topLeading)
                    .colorEffect(ShaderLibrary.coinPileCollectExchange(.float(g.px), .floatArray(exchangeAxis), .image(atlas)))
                    .clipShape(R.squircle(0, 0, 172, 162, 36))
                    .offset(x: 24, y: 478)
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in art.exchangeCopy(context) }
                    .frame(width: 172, height: 162, alignment: .topLeading)
                    .clipShape(R.squircle(0, 0, 172, 162, 36))
                    .offset(x: 24, y: 478)
                Rectangle().fill(.white)
                    .frame(width: 174, height: 340, alignment: .topLeading)
                    .colorEffect(ShaderLibrary.coinPileCollectPile(.float(g.px), .floatArray(coins), .image(atlas)))
                    .clipShape(R.squircle(0, 0, 174, 340, 40))
                    .offset(x: 204, y: 250)
                Canvas(opaque: false, colorMode: .nonLinear) { context, _ in art.hopper(context, frame.a) }
                    .frame(width: 174, height: 340, alignment: .topLeading)
                    .offset(x: 204, y: 250)
            }
            .frame(width: 402, height: g.height / g.scale - min(g.oy / g.scale, 0), alignment: .topLeading)
            .scaleEffect(g.scale, anchor: .topLeading)
            .offset(x: g.ox, y: g.oy)
            page.mask(alignment: .topLeading) {
                let copyBottom = g.oy + (664 + 6 + 34 * 0.708 * resources.fs + 2 * 36 * resources.fs + 8) * g.scale
                let fadeH = max(16 * g.scale, min(96 * g.scale, g.height - copyBottom))
                LinearGradient(stops: [.init(color: .white.opacity(0), location: 0), .init(color: .white.opacity(0.72), location: 0.55), .init(color: .white, location: 1)], startPoint: .top, endPoint: .bottom)
                    .frame(width: g.width, height: fadeH, alignment: .topLeading)
                    .offset(y: g.height - fadeH)
            }
        }
        .frame(width: g.width, height: g.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
    private var page: some View {
        Rectangle().fill(.white)
            .frame(width: geometry.width, height: geometry.height, alignment: .topLeading)
            .colorEffect(ShaderLibrary.coinPileCollectPage(.float2(geometry.width, geometry.height)))
    }
}
