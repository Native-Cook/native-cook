import SwiftUI

/// A transaction row with trailing actions. The content lifts into a glass slab and leans with the
/// finger's velocity; the actions grow in the uncovered strip; past the full-swipe line Delete takes the
/// whole strip. Touches are handled by the model; this only draws.
struct TiltSwipeActionsRowView: View {
    let model: TiltSwipeActionsModel
    let row: TiltSwipeActionsRow
    let reduceMotion: Bool
    let displayScale: CGFloat

    private typealias Layout = TiltSwipeActionsLayout
    private typealias Colour = TiltSwipeActionsColor

    var body: some View {
        let shellHeight = Layout.row * max(row.height.value, 0)
        let flagged = model.flagged.contains(row.key)
        Color.clear
            .frame(width: TiltSwipeActionsFlat.width, height: shellHeight)
            .overlay(alignment: .topTrailing) { strip(height: shellHeight, flagged: flagged) }
            .overlay(alignment: .leading) { content(flagged: flagged) }
            .opacity(row.fade.value)
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("\(row.item.title), \(row.item.when), \(row.item.amount)\(flagged ? ", flagged" : "")")
            .accessibilityHint("Swipe left for actions")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction(named: flagged ? "Unflag" : "Flag") { model.toggleFlag(row.key) }
            .accessibilityAction(named: "Delete") { model.removeNow(row.key) }
    }

    // MARK: Actions

    /// Tiles reveal as the strip passes them; at full swipe Delete stretches over the strip.
    private func strip(height: Double, flagged: Bool) -> some View {
        let width = max(-row.x.value, 0)
        return ZStack(alignment: .topTrailing) {
            flagTile(width: width, flagged: flagged)
                .padding(.trailing, Layout.gutter + Layout.tile + Layout.tileGap)
                .padding(.top, (Layout.row - Layout.tileHeight) / 2)
            deleteTile(width: width)
                .padding(.trailing, Layout.gutter)
                .padding(.top, (Layout.row - Layout.tileHeight) / 2)
        }
        .frame(width: TiltSwipeActionsFlat.width, height: height, alignment: .topTrailing)
        .frame(width: width, height: height, alignment: .trailing)
        .clipped()
        .opacity(row.strip.value)
    }

    private func flagTile(width: Double, flagged: Bool) -> some View {
        let reveal = ((width - Layout.gutter - Layout.tile - Layout.tileGap) / Layout.tile).tiltClamped(0, 1)
        let full = row.full.value
        return VStack(spacing: TiltSwipeActionsTokens.Row.tileContentGap) {
            TiltSwipeActionsFeatherIcon(icon: .flag, size: TiltSwipeActionsTokens.Row.actionIcon, color: Colour.white)
            TiltSwipeActionsLabel(text: flagged ? "Unflag" : "Flag", style: TiltSwipeActionsType.tile, color: Colour.white)
        }
        .frame(width: Layout.tile, height: Layout.tileHeight)
        .background(Colour.plum)
        .clipShape(RoundedRectangle(cornerRadius: Layout.tileRadius, style: .circular))
        .opacity(reveal * (1 - full))
        .scaleEffect((TiltSwipeActionsTokens.Row.tileStartScale + (1 - TiltSwipeActionsTokens.Row.tileStartScale) * reveal) * (1 - TiltSwipeActionsTokens.Row.tileFullFade * full))
    }

    private func deleteTile(width: Double) -> some View {
        let reveal = ((width - Layout.gutter) / Layout.tile).tiltClamped(0, 1)
        let stretched = max(width - Layout.gutter - Layout.tileGap, Layout.tile)
        let red = row.red.value
        return VStack(spacing: TiltSwipeActionsTokens.Row.tileContentGap) {
            ZStack {
                TiltSwipeActionsFeatherIcon(icon: .trash2, size: TiltSwipeActionsTokens.Row.actionIcon, color: Colour.pink).opacity(1 - red)
                TiltSwipeActionsFeatherIcon(icon: .trash2, size: TiltSwipeActionsTokens.Row.actionIcon, color: Colour.white).opacity(red)
            }
            ZStack {
                TiltSwipeActionsLabel(text: "Delete", style: TiltSwipeActionsType.tile, color: Colour.pinkDeep).opacity(1 - red)
                TiltSwipeActionsLabel(text: "Delete", style: TiltSwipeActionsType.tile, color: Colour.white).opacity(red)
            }
        }
        .frame(width: Layout.tile + (stretched - Layout.tile) * row.full.value, height: Layout.tileHeight)
        .background(TiltSwipeActionsGammaMix.colour(red))
        .clipShape(RoundedRectangle(cornerRadius: Layout.tileRadius, style: .circular))
        .scaleEffect(TiltSwipeActionsTokens.Row.tileStartScale + (1 - TiltSwipeActionsTokens.Row.tileStartScale) * reveal, anchor: .trailing)
        .opacity(reveal)
    }

    // MARK: Content

    private func content(flagged: Bool) -> some View {
        let lift = row.lift.value
        let tilt = row.tilt.value
        return ZStack(alignment: .topLeading) {
            slab(lift: lift, tilt: tilt)
                .frame(width: TiltSwipeActionsFlat.width - TiltSwipeActionsTokens.Row.slabInsetX * 2, height: Layout.row - TiltSwipeActionsTokens.Row.slabInsetY * 2)
                .offset(x: TiltSwipeActionsTokens.Row.slabInsetX, y: TiltSwipeActionsTokens.Row.slabInsetY)
            rowLine(flagged: flagged)
        }
        .frame(width: TiltSwipeActionsFlat.width, height: Layout.row, alignment: .topLeading)
        .modifier(TiltSwipeActionsRowEffect(
            x: row.x.value,
            tilt: tilt,
            scale: 1 + (TiltSwipeActionsMotion.Tilt.lift - 1) * lift
        ))
    }

    /// The glass slab the row becomes under the finger, with a sheen that slides against the row.
    private func slab(lift: Double, tilt: Double) -> some View {
        let hairline = max(1 / displayScale, (0.4 * displayScale).rounded() / displayScale)
        let sheenOpacity = lift * (TiltSwipeActionsTokens.Row.sheenBaseOpacity + min(abs(tilt) / TiltSwipeActionsMotion.Tilt.max, 1) * (1 - TiltSwipeActionsTokens.Row.sheenBaseOpacity))
        let skew = tan(TiltSwipeActionsTokens.Row.sheenSkew * Double.pi / 180)
        let sheenHeight = Layout.row - TiltSwipeActionsTokens.Row.slabInsetY * 2
        return RoundedRectangle(cornerRadius: Layout.rowRadius, style: .circular)
            .fill(Colour.slab)
            .shadow(color: Colour.slabShadow.opacity(TiltSwipeActionsTokens.Row.slabShadowOpacity), radius: TiltSwipeActionsTokens.Row.slabShadowRadius, x: 0, y: TiltSwipeActionsTokens.Row.slabShadowY)
            .overlay {
                ZStack(alignment: .topLeading) {
                    LinearGradient(colors: Colour.sheen, startPoint: .leading, endPoint: .trailing)
                        .frame(width: TiltSwipeActionsTokens.Row.sheenWidth, height: sheenHeight)
                        .transformEffect(CGAffineTransform(a: 1, b: 0, c: skew, d: 1, tx: -skew * sheenHeight / 2, ty: 0))
                        .offset(x: TiltSwipeActionsTokens.Row.sheenStartX + row.x.value * TiltSwipeActionsMotion.Timing.sheen)
                        .opacity(sheenOpacity)
                }
                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
                .clipShape(RoundedRectangle(cornerRadius: Layout.rowRadius, style: .circular))
            }
            .overlay(RoundedRectangle(cornerRadius: Layout.rowRadius, style: .circular).strokeBorder(Colour.slabEdge, lineWidth: hairline))
            .opacity(lift)
    }

    private func rowLine(flagged: Bool) -> some View {
        let title = TiltSwipeActionsType.rowTitle
        let sub = TiltSwipeActionsType.rowSub
        return HStack(spacing: 0) {
            ZStack {
                Circle().fill(Colour.plum)
                TiltSwipeActionsFeatherIcon(icon: row.item.icon, size: TiltSwipeActionsTokens.Row.contentIcon, color: row.item.tint)
            }
            .frame(width: Layout.circle, height: Layout.circle)
            .offset(y: TiltSwipeActionsTokens.Row.circleY)
            VStack(alignment: .leading, spacing: 0) {
                TiltSwipeActionsLabel(text: row.item.title, style: title, color: Colour.title, fitsWidth: true)
                TiltSwipeActionsLabel(text: row.item.when, style: sub, color: Colour.subtitle)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.leading, Layout.textLeft - TiltSwipeActionsFlat.rowLeft - Layout.circle)
            if flagged {
                Circle().fill(Colour.plum).frame(width: TiltSwipeActionsTokens.Row.flagDot, height: TiltSwipeActionsTokens.Row.flagDot).padding(.trailing, TiltSwipeActionsTokens.Row.flagGap)
            }
            // The amount sits on the title's line, not the row's centre (as in the source).
            TiltSwipeActionsLabel(text: row.item.amount, style: TiltSwipeActionsType.amount, color: Colour.title, tabular: true)
                .frame(maxHeight: .infinity, alignment: .top)
                .padding(.top, (Layout.row - title.lineHeight - sub.lineHeight) / 2 + TiltSwipeActionsTokens.Row.amountY)
        }
        .padding(.leading, TiltSwipeActionsFlat.rowLeft)
        .padding(.trailing, TiltSwipeActionsFlat.rowRight)
        .frame(width: TiltSwipeActionsFlat.width, height: Layout.row, alignment: .leading)
    }
}

/// `interpolateColor(red, [0, 1], [white, pink])`: Reanimated mixes RGB through a 2.2 gamma.
enum TiltSwipeActionsGammaMix {
    static func colour(_ t: Double) -> Color {
        let k = t.tiltClamped(0, 1)
        let white = (r: 1.0, g: 1.0, b: 1.0)
        let pink = (r: 242.0 / 255, g: 98.0 / 255, b: 147.0 / 255)
        func mix(_ a: Double, _ b: Double) -> Double {
            pow(pow(a, 2.2) * (1 - k) + pow(b, 2.2) * k, 1 / 2.2)
        }
        return Color(.sRGB, red: mix(white.r, pink.r), green: mix(white.g, pink.g), blue: mix(white.b, pink.b), opacity: 1)
    }
}
