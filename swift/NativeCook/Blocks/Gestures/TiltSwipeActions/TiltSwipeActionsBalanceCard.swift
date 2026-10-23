import SwiftUI

/// The source's white account card.
struct TiltSwipeActionsBalanceCard: View {
    let model: TiltSwipeActionsModel

    private typealias Colour = TiltSwipeActionsColor

    var body: some View {
        HStack(spacing: TiltSwipeActionsTokens.BalanceCard.gap) {
            ZStack {
                RoundedRectangle(cornerRadius: TiltSwipeActionsLayout.cardBadgeRadius, style: .circular).fill(Colour.plum)
                Circle().strokeBorder(Colour.pink, lineWidth: TiltSwipeActionsTokens.BalanceCard.ringWidth).frame(width: TiltSwipeActionsTokens.BalanceCard.ring, height: TiltSwipeActionsTokens.BalanceCard.ring)
            }
            .frame(width: TiltSwipeActionsLayout.cardBadge, height: TiltSwipeActionsLayout.cardBadge)
            VStack(alignment: .leading, spacing: 0) {
                TiltSwipeActionsLabel(text: "Bank account", style: TiltSwipeActionsType.cardLabel, color: Colour.cardLabel)
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    TiltSwipeActionsLabel(
                        text: TiltSwipeActionsData.format(model.balance.value),
                        style: TiltSwipeActionsType.balance,
                        color: Colour.plumInk,
                        tabular: true
                    )
                    TiltSwipeActionsLabel(text: " $", style: TiltSwipeActionsType.currency, color: Colour.cardCurrency)
                }
            }
        }
        .padding(.horizontal, TiltSwipeActionsTokens.BalanceCard.horizontalPadding)
        .frame(
            width: TiltSwipeActionsFlat.width - TiltSwipeActionsFlat.card.left - TiltSwipeActionsFlat.card.right,
            height: TiltSwipeActionsFlat.card.height,
            alignment: .leading
        )
        .background(
            RoundedRectangle(cornerRadius: TiltSwipeActionsFlat.card.radius, style: .circular)
                .fill(Colour.white)
                .shadow(color: Colour.slabShadow.opacity(TiltSwipeActionsTokens.BalanceCard.shadowOpacity), radius: TiltSwipeActionsTokens.BalanceCard.shadowRadius, x: 0, y: TiltSwipeActionsTokens.BalanceCard.shadowY)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Bank account balance")
    }
}

/// The source's Overview tab: bank cards side by side, each with a plum badge over its top-left corner.
struct TiltSwipeActionsCardsView: View {
    let model: TiltSwipeActionsModel

    private typealias Colour = TiltSwipeActionsColor
    private let cards = TiltSwipeActionsFlat.cards

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach([-1, 0, 1], id: \.self) { index in
                card
                    .offset(x: cards.left + Double(index) * cards.pitch, y: cards.badgeTop)
            }
        }
        .frame(width: TiltSwipeActionsFlat.planeWidth, height: TiltSwipeActionsFlat.planeHeight, alignment: .topLeading)
    }

    private var card: some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                TiltSwipeActionsLabel(
                    text: "Bank account",
                    style: TiltSwipeActionsTextStyle(
                        size: TiltSwipeActionsTokens.Cards.labelSize,
                        lineHeight: TiltSwipeActionsType.cardLabel.lineHeight,
                        fontName: TiltSwipeActionsTokens.Font.postScriptMedium
                    ),
                    color: Colour.cardLabel
                )
                HStack(alignment: .firstTextBaseline, spacing: 0) {
                    TiltSwipeActionsLabel(
                        text: TiltSwipeActionsData.format(model.balance.value),
                        style: TiltSwipeActionsTextStyle(
                            size: TiltSwipeActionsTokens.Cards.balanceSize,
                            lineHeight: TiltSwipeActionsTokens.Cards.balanceLineHeight,
                            fontName: TiltSwipeActionsTokens.Font.postScriptSemibold,
                            kerning: TiltSwipeActionsType.balance.kerning
                        ),
                        color: Colour.cardInk
                    )
                    TiltSwipeActionsLabel(
                        text: " $",
                        style: TiltSwipeActionsTextStyle(
                            size: TiltSwipeActionsTokens.Cards.currencySize,
                            lineHeight: TiltSwipeActionsTokens.Cards.balanceLineHeight,
                            fontName: TiltSwipeActionsTokens.Font.postScriptMedium
                        ),
                        color: Colour.cardCurrency
                    )
                }
                .padding(.leading, TiltSwipeActionsTokens.Cards.amountInset)
            }
            .padding(.leading, TiltSwipeActionsTokens.Cards.leftInset)
            .padding(.bottom, TiltSwipeActionsTokens.Cards.bottomInset)
            .frame(width: cards.width, height: cards.height, alignment: .bottomLeading)
            .background(
                RoundedRectangle(cornerRadius: cards.radius, style: .circular)
                    .fill(Colour.white)
                    .shadow(color: Colour.slabShadow.opacity(TiltSwipeActionsTokens.Cards.shadowOpacity), radius: TiltSwipeActionsTokens.Cards.shadowRadius, x: 0, y: TiltSwipeActionsTokens.Cards.shadowY)
            )
            .offset(y: cards.top - cards.badgeTop)
            ZStack {
                RoundedRectangle(cornerRadius: TiltSwipeActionsTokens.Cards.badgeRadius, style: .circular).fill(Colour.plum)
                ring
            }
            .frame(width: cards.badge, height: cards.badge)
            .offset(x: cards.badgeLeft)
        }
        .frame(width: cards.width, height: cards.top + cards.height - cards.badgeTop, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Bank account balance")
    }

    /// A pink ring whose top quarter is the light pink: the border's top side has its own colour.
    private var ring: some View {
        ZStack {
            arc(0.875, 1, Colour.pink)
            arc(0, 0.625, Colour.pink)
            arc(0.625, 0.875, Colour.pinkGlow)
        }
        .frame(width: TiltSwipeActionsTokens.Cards.ring, height: TiltSwipeActionsTokens.Cards.ring)
    }

    private func arc(_ from: CGFloat, _ to: CGFloat, _ color: Color) -> some View {
        Circle()
            .trim(from: from, to: to)
            .stroke(color, style: StrokeStyle(lineWidth: TiltSwipeActionsTokens.Cards.ringWidth, lineCap: .butt))
            .padding(TiltSwipeActionsTokens.Cards.ringWidth / 2)
    }
}
