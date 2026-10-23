import SwiftUI

/// The ice over a sticker: a Metal layer effect over the same render, padded by the rime margin.
struct PriceTagWelcomeStickerFrost: View {
    let item: PriceTagWelcomeTheme.Item
    let index: Int
    let look: PriceTagWelcomeMotion.Look
    let clock: Double
    let unit: Double
    let fontScale: Double

    var body: some View {
        let margin = PriceTagWelcomeTheme.Space.frostMargin * unit
        let geometry = PriceTagWelcomeFrostGeometry.make(item: item, unit: unit, fontScale: fontScale)
        let width = item.width * unit
        let height = item.height * unit
        let reach = 18 * fontScale + 2
        Image(uiImage: PriceTagWelcomeAssets.image(item.id))
            .resizable()
            .interpolation(.high)
            .frame(width: width, height: height)
            .padding(margin)
            .layerEffect(
                ShaderLibrary.priceTagWelcomeStickerFrost(
                    .float2(geometry.originX, geometry.originY),
                    .float(look.grow * geometry.span),
                    .float(geometry.span),
                    .float(look.melt),
                    .float(clock),
                    .float(fontScale),
                    .float(look.fade),
                    .float(1.7 + 3.1 * Double(index)),
                    .float2(width, height),
                    .float(margin),
                    .float(PriceTagWelcomeTheme.Frost.cell)
                ),
                maxSampleOffset: CGSize(width: reach, height: reach)
            )
            .allowsHitTesting(false)
    }
}

/// Frost origin (the tag's centre, in the sticker's padded canvas, pt) and the radius that covers it all.
struct PriceTagWelcomeFrostGeometry {
    let originX: Double
    let originY: Double
    let span: Double

    static func make(item: PriceTagWelcomeTheme.Item, unit: Double, fontScale: Double) -> Self {
        let margin = PriceTagWelcomeTheme.Space.frostMargin * unit
        let canvasWidth = item.width * unit + 2 * margin
        let canvasHeight = item.height * unit + 2 * margin
        let ox = (item.tag.centerX - item.x) * unit + margin
        let oy = (item.tag.centerY - item.y) * unit + margin
        let span = hypot(max(ox, canvasWidth - ox), max(oy, canvasHeight - oy)) + 12 * fontScale
        return Self(originX: ox, originY: oy, span: span)
    }
}
