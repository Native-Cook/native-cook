import SwiftUI

/// One product sticker: shadow + render with its die-cut outline, its frost and the etched countdown.
struct PriceTagWelcomeSticker: View {
    let item: PriceTagWelcomeTheme.Item
    let index: Int
    let frame: PriceTagWelcomeMotion.Frame
    let reduced: Bool
    let mounted: Bool
    let unit: Double
    let fontScale: Double

    var body: some View {
        let originX = PriceTagWelcomeMotion.wrapX(restX: item.x, offset: frame.x)
        let press = frame.press[index]
        let scale = 1 - (1 - PriceTagWelcomeMotion.Press.scale) * press
        let shadowY = 4 - (4 - 2) * press
        let look = PriceTagWelcomeMotion.frostLook(
            freeze: frame.freezeTime[index], thaw: frame.thawTime[index], still: reduced
        )
        let width = item.width * unit
        let height = item.height * unit
        ZStack {
            Image(uiImage: PriceTagWelcomeAssets.image("\(item.id)-shadow"))
                .resizable()
                .interpolation(.high)
                .opacity(0.14)
                .scaleEffect(scale)
                .offset(y: shadowY * unit)
            ZStack {
                Image(uiImage: PriceTagWelcomeAssets.image(item.id))
                    .resizable()
                    .interpolation(.high)
                    .frame(width: width, height: height)
                    .overlay {
                        if mounted && look.on {
                            PriceTagWelcomeStickerFrost(
                                item: item, index: index, look: look, clock: frame.clock,
                                unit: unit, fontScale: fontScale
                            )
                        }
                    }
                PriceTagWelcomeCountdown(
                    item: item, look: look, freezeTime: frame.freezeTime[index],
                    reduced: reduced, unit: unit, fontScale: fontScale
                )
            }
            .frame(width: width, height: height)
            .scaleEffect(scale)
        }
        .frame(width: width, height: height, alignment: .center)
        .position(
            x: (originX + item.width / 2) * unit,
            y: (item.y - PriceTagWelcomeTheme.rowTop + item.height / 2) * unit
        )
        .accessibilityHidden(true)
    }
}
