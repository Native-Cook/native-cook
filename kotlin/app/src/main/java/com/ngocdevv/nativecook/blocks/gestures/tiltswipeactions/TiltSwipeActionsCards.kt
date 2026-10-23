package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/** RollingAmount.tsx: a balance that rolls to its new value, one decimal, no trailing ".0". */
@Composable
internal fun RollingAmount(
    screen: TiltScreen,
    style: TiltText,
    color: Color,
    modifier: Modifier = Modifier,
    fontSize: Float = style.fontSize,
    lineHeight: Float = style.lineHeight,
) {
    TiltLabel(
        text = formatBalance(screen.shownBalance.value),
        style = style,
        color = color,
        modifier = modifier,
        fontSize = fontSize,
        lineHeight = lineHeight,
        tabular = true,
    )
}

/** BalanceCard.tsx: the source's white account card. */
@Composable
internal fun BalanceCard(screen: TiltScreen) {
    val shadow = remember { TiltShadow(TiltColor.slabShadow, TiltDetail.cardShadowRadius) }
    val width = 402f - TiltFlat.Card.left - TiltFlat.Card.right
    Row(
        modifier = Modifier
            .at(TiltFlat.Card.left, TiltFlat.Card.top)
            .width(width.dp)
            .height(TiltFlat.Card.height.dp)
            .semantics(mergeDescendants = true) { contentDescription = "Bank account balance" }
            .drawBehind {
                inDp {
                    val w = size.width / density
                    val h = size.height / density
                    shadow.draw(
                        this,
                        0f,
                        TiltDetail.cardShadowY,
                        w,
                        h,
                        TiltFlat.Card.radius,
                        TiltDetail.cardShadowOpacity,
                    )
                    drawRoundRect(TiltColor.white, Offset.Zero, Size(w, h), CornerRadius(TiltFlat.Card.radius))
                }
            }
            .padding(horizontal = TiltDetail.cardPadding.dp),
        horizontalArrangement = Arrangement.spacedBy(TiltDetail.cardGap.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(TiltLayout.cardBadge.dp)
                .background(TiltColor.plum, RoundedCornerShape(TiltLayout.cardBadgeRadius.dp))
                .drawBehind {
                    inDp {
                        ring(TiltDetail.cardRing, TiltDetail.cardRingBorder, TiltColor.pink, TiltColor.pink)
                    }
                },
        )
        Column {
            TiltLabel("Bank account", TiltType.cardLabel, TiltColor.cardLabel)
            Row {
                RollingAmount(
                    screen = screen,
                    style = TiltType.balance,
                    color = TiltColor.plumInk,
                    modifier = Modifier.alignByBaseline(),
                )
                TiltLabel(" $", TiltType.currency, TiltColor.cardCurrency, Modifier.alignByBaseline())
            }
        }
    }
}

/**
 * A circle with a [border] wide ring, centred in the current drawing area. [topColor] paints the
 * top side (the 90 degrees between the two upper diagonals) the way a per-side border colour does.
 */
private fun DrawScope.ring(
    diameter: Float,
    border: Float,
    color: Color,
    topColor: Color,
) {
    val centre = Offset(size.width / density / 2f, size.height / density / 2f)
    val radius = diameter / 2f - border / 2f
    val topLeft = Offset(centre.x - radius, centre.y - radius)
    val box = Size(radius * 2f, radius * 2f)
    val stroke = Stroke(width = border, cap = StrokeCap.Butt)
    drawArc(color, startAngle = -45f, sweepAngle = 270f, useCenter = false, topLeft = topLeft, size = box, style = stroke)
    drawArc(topColor, startAngle = -135f, sweepAngle = 90f, useCenter = false, topLeft = topLeft, size = box, style = stroke)
}

/** CardsView.tsx: the source's Overview tab (f005), bank cards side by side with a plum badge on each. */
@Composable
internal fun CardsView(screen: TiltScreen) {
    Box(Modifier.fillMaxSize()) {
        // The middle of a carousel: one card to the left, one to the right.
        for (i in -1..1) {
            OverviewCard(screen, TiltFlat.Cards.left + i * TiltFlat.Cards.pitch)
        }
    }
}

@Composable
private fun OverviewCard(screen: TiltScreen, left: Float) {
    val cards = TiltFlat.Cards
    val shadow = remember { TiltShadow(TiltColor.slabShadow, TiltDetail.overviewShadowRadius) }
    val cardTop = cards.top - cards.badgeTop
    Box(
        modifier = Modifier
            .at(left, cards.badgeTop)
            .size(cards.width.dp, (cards.top + cards.height - cards.badgeTop).dp)
            .semantics(mergeDescendants = true) { contentDescription = "Bank account balance" },
    ) {
        Column(
            modifier = Modifier
                .at(0f, cardTop)
                .size(cards.width.dp, cards.height.dp)
                .drawBehind {
                    inDp {
                        val w = size.width / density
                        val h = size.height / density
                        shadow.draw(
                            this,
                            0f,
                            TiltDetail.overviewShadowY,
                            w,
                            h,
                            cards.radius,
                            TiltDetail.overviewShadowOpacity,
                        )
                        drawRoundRect(TiltColor.white, Offset.Zero, Size(w, h), CornerRadius(cards.radius))
                    }
                }
                .padding(
                    start = TiltDetail.overviewPaddingLeft.dp,
                    bottom = TiltDetail.overviewPaddingBottom.dp,
                ),
            verticalArrangement = Arrangement.Bottom,
        ) {
            TiltLabel(
                "Bank account",
                TiltType.cardLabel,
                TiltColor.cardLabel,
                fontSize = TiltDetail.overviewLabelSize,
                weight = 500,
            )
            Row(Modifier.padding(start = TiltDetail.overviewAmountInset.dp)) {
                RollingAmount(
                    screen = screen,
                    style = TiltType.balance,
                    color = TiltColor.cardInk,
                    modifier = Modifier.alignByBaseline(),
                    fontSize = TiltDetail.overviewBalanceSize,
                    lineHeight = TiltDetail.overviewBalanceLine,
                )
                TiltLabel(
                    " $",
                    TiltType.currency,
                    TiltColor.cardCurrency,
                    Modifier.alignByBaseline(),
                    fontSize = TiltDetail.overviewCurrencySize,
                )
            }
        }
        Box(
            modifier = Modifier
                .at(cards.badgeLeft, 0f)
                .size(cards.badge.dp)
                .background(TiltColor.plum, RoundedCornerShape(TiltDetail.overviewBadgeRadius.dp))
                .drawBehind {
                    // borderTopColor is the same pink at 35%.
                    inDp {
                        ring(
                            TiltDetail.overviewRing,
                            TiltDetail.overviewRingBorder,
                            TiltColor.pink,
                            TiltColor.pinkGlow,
                        )
                    }
                },
        )
    }
}
