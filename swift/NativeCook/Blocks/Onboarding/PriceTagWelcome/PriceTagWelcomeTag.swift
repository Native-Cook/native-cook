import SwiftUI

/// A price tag: hangs from a pin near its top edge (the pendulum); frozen, it reads "Wait 24h".
struct PriceTagWelcomeTag: View {
    let item: PriceTagWelcomeTheme.Item
    let index: Int
    let frame: PriceTagWelcomeMotion.Frame
    let reduced: Bool
    let mounted: Bool
    let unit: Double
    let fontScale: Double

    var body: some View {
        let tag = item.tag
        let width = tag.width * unit
        let height = tag.height * unit
        let originX = PriceTagWelcomeMotion.wrapX(restX: item.x, offset: frame.x)
        let centerX = item.tag.centerX - item.x + originX
        let pivot = PriceTagWelcomeMotion.pivotAnchor()
        let look = PriceTagWelcomeMotion.frostLook(
            freeze: frame.freezeTime[index], thaw: frame.thawTime[index], still: reduced
        )
        let price = PriceTagWelcomeTheme.Typography.price
        ZStack {
            Capsule()
                .fill(PriceTagWelcomeTheme.Colors.tag)
                .frame(width: width, height: height)
                .shadow(color: .black.opacity(0.10), radius: 3 * unit, x: 0, y: 1.5 * unit)
                .shadow(color: .black.opacity(0.07), radius: 10 * unit, x: 0, y: 4 * unit)
            // The crisp price hands over to its blurred copy as the ice takes it.
            Text(item.price)
                .font(.system(size: price.size * fontScale, weight: price.weight))
                .foregroundStyle(price.color)
                .opacity(1 - look.lost)
            if mounted && look.on {
                if look.lost > 0 {
                    blurredPrice(look: look, width: width, height: height)
                }
                tagFrost(look: look, width: width, height: height)
            }
            waitLabel(look: look, width: width)
        }
        .frame(width: width, height: height)
        .rotationEffect(.degrees(frame.theta[index]), anchor: UnitPoint(x: pivot.x, y: pivot.y))
        .rotationEffect(.degrees(tag.tilt))
        .position(
            x: centerX * unit,
            y: (tag.centerY - PriceTagWelcomeTheme.rowTop) * unit
        )
    }

    /// The same figures with a real Gaussian blur (0 ... priceBlur), bounded by the tag, fading to the ghost.
    private func blurredPrice(look: PriceTagWelcomeMotion.Look, width: Double, height: Double) -> some View {
        let price = PriceTagWelcomeTheme.Typography.price
        return Text(item.price)
            .font(.system(size: price.size * fontScale, weight: price.weight))
            .foregroundStyle(price.color)
            .blur(radius: look.lost * PriceTagWelcomeMotion.Etch.priceBlur * fontScale)
            .frame(width: width, height: height)
            .clipped()
            .opacity(look.priceAlpha)
    }

    /// The ice over a tag: the same front, from the tag's centre, in the tag's own frame.
    private func tagFrost(look: PriceTagWelcomeMotion.Look, width: Double, height: Double) -> some View {
        let geometry = PriceTagWelcomeFrostGeometry.make(item: item, unit: unit, fontScale: fontScale)
        return Rectangle()
            .fill(Color.white)
            .frame(width: width, height: height)
            .layerEffect(
                ShaderLibrary.priceTagWelcomeTagFrost(
                    .float2(width / 2, height / 2),
                    .float(look.grow * geometry.span),
                    .float(geometry.span),
                    .float(look.melt),
                    .float(frame.clock),
                    .float(fontScale),
                    .float(look.fade),
                    .float(5.3 + 2.3 * Double(index)),
                    .float2(width, height),
                    .float(PriceTagWelcomeTheme.Frost.tagCell),
                    .float(PriceTagWelcomeTheme.Frost.tagAlpha)
                ),
                maxSampleOffset: .zero
            )
            .allowsHitTesting(false)
    }

    /// "Wait 24h", etched into the frosted tag (shrinks to fit a narrow tag).
    private func waitLabel(look: PriceTagWelcomeMotion.Look, width: Double) -> some View {
        let style = PriceTagWelcomeTheme.Typography.wait
        let size = min(
            style.size * fontScale,
            (width - 2 * PriceTagWelcomeTheme.Space.tagPad * unit) / PriceTagWelcomeTheme.waitEm
        )
        return PriceTagWelcomeEtch(
            timer: false, look: look, reduced: reduced, drop: PriceTagWelcomeTheme.Space.etchDrop * unit
        ) {
            PriceTagWelcomeEdgedText(
                text: PriceTagWelcomeTheme.Copy.wait, size: size, weight: style.weight, tracking: 0,
                color: PriceTagWelcomeTheme.Colors.etch, edge: PriceTagWelcomeTheme.Colors.etchLight,
                edgeOffset: 0.8
            )
        }
    }
}
