import SwiftUI

struct TiltSwipeActionsTabPanel: View {
    typealias Colour = TiltSwipeActionsColor
    typealias Flat = TiltSwipeActionsFlat

    let model: TiltSwipeActionsModel
    let reduceMotion: Bool
    let displayScale: CGFloat

    private static let tabs: [(icon: TiltSwipeActionsTabIcon.Kind, label: String)] = [
        (.pie, "Overview"), (.wallet, "Wallet"), (.person, "Spending"),
    ]
    private static let menuItems: [(label: String, icon: TiltSwipeActionsFeather, tint: Color)] = [
        ("Income", .plus, Colour.income),
        ("Expense", .minus, Colour.pink),
        ("Transfer", .arrowUpRight, Colour.transfer),
    ]

    var body: some View {
        let progress = model.menu.value
        let open = min(max(progress, 0), 1)
        ZStack(alignment: .topLeading) {
            panel
            Colour.menuScrim.opacity(open)
            menuCard(progress: progress)
            TiltSwipeActionsFabGlow(model: model, reduceMotion: reduceMotion)
            fab(open: open)
        }
        .frame(width: Flat.planeWidth, height: Flat.planeHeight, alignment: .topLeading)
    }

    private var panel: some View {
        let panelHeight = Flat.planeHeight - Flat.panel.top
        let hairline = 1 / displayScale
        return ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(Colour.deskEdge)
                .frame(width: Flat.panel.left, height: panelHeight + Flat.panel.radiusTop)
                .offset(y: Flat.panel.top - Flat.panel.radiusTop)
            UnevenRoundedRectangle(
                topLeadingRadius: Flat.panel.radiusTop,
                bottomLeadingRadius: Flat.panel.radiusBottom,
                bottomTrailingRadius: 0,
                topTrailingRadius: 0
            )
            .fill(
                LinearGradient(
                    stops: [
                        .init(color: Colour.panelEdge, location: 0),
                        .init(color: Colour.panel, location: TiltSwipeActionsTokens.Panel.fadeHeight / TiltSwipeActionsTokens.Panel.fadeSpan),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            )
            .frame(width: Flat.planeWidth - Flat.panel.left, height: panelHeight)
            .offset(x: Flat.panel.left, y: Flat.panel.top)
            RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.lens / 2, style: .continuous)
                .fill(Colour.lens)
                .overlay(RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.lens / 2, style: .continuous).strokeBorder(Colour.lensRim, lineWidth: hairline))
                .overlay(alignment: .top) {
                    Rectangle()
                        .fill(Colour.glassEdge)
                        .frame(height: TiltSwipeActionsTokens.Panel.lensHighlightHeight)
                        .padding(.horizontal, TiltSwipeActionsTokens.Panel.lensHighlightInset)
                        .offset(y: TiltSwipeActionsTokens.Panel.lensHighlightY)
                }
                .shadow(color: Colour.slabShadow.opacity(TiltSwipeActionsTokens.Panel.lensShadowOpacity), radius: TiltSwipeActionsTokens.Panel.lensShadowRadius, y: TiltSwipeActionsTokens.Panel.lensShadowY)
                .frame(width: TiltSwipeActionsLayout.lens, height: TiltSwipeActionsLayout.lens)
                .offset(x: model.lens.value - TiltSwipeActionsLayout.lens / 2, y: Flat.tabY - TiltSwipeActionsLayout.lens / 2)
            ForEach(Array(Self.tabs.enumerated()), id: \.offset) { index, item in
                TiltSwipeActionsTabIcon(kind: item.icon, color: index == model.tab ? Colour.panelIconActive : Colour.panelIcon)
                    .scaleEffect(model.tabScales[index].value)
                    .frame(width: TiltSwipeActionsTokens.Panel.tabHit, height: TiltSwipeActionsTokens.Panel.tabHit)
                    .offset(x: Flat.tabs[index] - TiltSwipeActionsTokens.Panel.tabHit / 2, y: Flat.tabY - TiltSwipeActionsTokens.Panel.tabHit / 2)
                    .accessibilityLabel(item.label)
            }
        }
    }

    private func menuCard(progress: Double) -> some View {
        let p = min(max(progress, 0), 1)
        return VStack(spacing: 0) {
            ForEach(Array(Self.menuItems.enumerated()), id: \.offset) { index, item in
                let denominator = 1 - TiltSwipeActionsMotion.Timing.menuStagger * 2
                let rowProgress = min(max((p - Double(index) * TiltSwipeActionsMotion.Timing.menuStagger) / denominator, 0), 1)
                HStack(spacing: 0) {
                    TiltSwipeActionsLabel(text: item.label, style: TiltSwipeActionsType.menu, color: Colour.menuInk)
                    Spacer(minLength: 0)
                    Circle()
                        .fill(item.tint.opacity(TiltSwipeActionsTokens.Panel.menuGlyphAlpha))
                        .overlay {
                            TiltSwipeActionsFeatherIcon(icon: item.icon, size: TiltSwipeActionsTokens.Panel.menuGlyphSize, color: item.tint)
                        }
                        .frame(width: Flat.menu.icon, height: Flat.menu.icon)
                }
                .frame(height: Flat.menu.item)
                .scaleEffect(model.menuScales[index].value)
                .opacity(rowProgress)
                .offset(y: reduceMotion ? 0 : (1 - rowProgress) * TiltSwipeActionsTokens.Panel.menuRise)
                .accessibilityLabel(item.label)
            }
        }
        .padding(.top, Flat.menu.padTop)
        .padding(.leading, Flat.menu.padLeft)
        .padding(.trailing, Flat.menu.padRight)
        .frame(width: Flat.menu.width, height: Flat.menu.height, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: Flat.menu.radius, style: .continuous)
                .fill(Colour.white)
                .shadow(color: Colour.slabShadow.opacity(TiltSwipeActionsTokens.Panel.menuShadowOpacity), radius: TiltSwipeActionsTokens.Panel.menuShadowRadius, y: TiltSwipeActionsTokens.Panel.menuShadowY)
        )
        .scaleEffect(reduceMotion ? 1 : TiltSwipeActionsTokens.Panel.menuScaleClosed + TiltSwipeActionsTokens.Panel.menuScaleRange * progress, anchor: .bottomTrailing)
        .opacity(p)
        .offset(x: Flat.menu.left, y: Flat.menu.top)
        .accessibilityHidden(!model.menuOpen)
    }

    private func fab(open: Double) -> some View {
        RoundedRectangle(cornerRadius: Flat.fab.radius, style: .continuous)
            .fill(Colour.white)
            .overlay {
                ZStack {
                    TiltSwipeActionsFeatherIcon(icon: .plus, size: TiltSwipeActionsTokens.Panel.fabGlyph, color: Colour.pink).opacity(1 - open)
                    TiltSwipeActionsFeatherIcon(icon: .plus, size: TiltSwipeActionsTokens.Panel.fabGlyph, color: Colour.menuPlus).opacity(open)
                }
            }
            .shadow(color: Colour.deskEdge.opacity(TiltSwipeActionsTokens.Panel.fabShadowOpacity), radius: TiltSwipeActionsTokens.Panel.fabShadowRadius, y: TiltSwipeActionsTokens.Panel.fabShadowY)
            .frame(width: Flat.fab.size, height: Flat.fab.size)
            .scaleEffect(model.fabScale.value)
            .offset(x: Flat.fab.left, y: Flat.fab.top)
            .accessibilityLabel(model.menuOpen ? "Close actions" : "Open actions")
    }
}

private struct TiltSwipeActionsFabGlow: View {
    let model: TiltSwipeActionsModel
    let reduceMotion: Bool

    var body: some View {
        let breath = reduceMotion ? 0.5 : (sin(2 * Double.pi * model.clock / TiltSwipeActionsMotion.Timing.breath) + 1) / 2
        RoundedRectangle(cornerRadius: TiltSwipeActionsFlat.fab.radius + TiltSwipeActionsTokens.Panel.glowRadiusExtra, style: .continuous)
            .fill(TiltSwipeActionsColor.pinkGlow)
            .shadow(color: TiltSwipeActionsColor.pink, radius: TiltSwipeActionsTokens.Panel.glowShadowRadius, y: TiltSwipeActionsTokens.Panel.glowShadowY)
            .frame(width: TiltSwipeActionsFlat.fab.size + TiltSwipeActionsTokens.Panel.glowExtra, height: TiltSwipeActionsFlat.fab.size + TiltSwipeActionsTokens.Panel.glowExtra)
            .scaleEffect(TiltSwipeActionsTokens.Panel.glowScaleBase + TiltSwipeActionsTokens.Panel.glowScaleGain * breath)
            .opacity(TiltSwipeActionsTokens.Panel.glowOpacityBase + TiltSwipeActionsTokens.Panel.glowOpacityGain * breath)
            .offset(x: TiltSwipeActionsFlat.fab.left - TiltSwipeActionsTokens.Panel.glowOffsetX, y: TiltSwipeActionsFlat.fab.top - TiltSwipeActionsTokens.Panel.glowOffsetY)
            .accessibilityHidden(true)
    }
}
