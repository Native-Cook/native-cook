package com.ngocdevv.nativecook.blocks.lists.stickysectionheaders

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/** Ledger palette: a cool paper, warm ink, one green for money in. */
internal val BG = Color(0xFFF3F2EE)
internal val INK = Color(0xFF1C1B19)
internal val MUTE = Color(0xFF77736B)
internal val INCOME = Color(0xFF2C8650)
internal val HAIR = Color(40 / 255f, 34 / 255f, 26 / 255f, 0.12f)

/** Room on the right for the month index. */
internal const val RAIL_W = 40f
internal const val SIDE = 20f

/** Header text positions, shared by the in-list header and the sticky copy so they line up to the pixel. */
internal const val LBL_Y = 8f
internal const val LBL_LH = 22f
internal const val LBL_FS = 17f
internal const val SUB_Y = 31f
internal const val SUB_LH = 17f
internal const val TOT_Y = 18f
internal const val TOT_LH = 20f

internal const val TNUM = "tnum"

/** A one-line text style whose box is exactly [lineHeightDp] tall with the glyphs centred, like an RN lineHeight. */
internal fun ledgerStyle(
    density: Density,
    fontSize: Float,
    lineHeightDp: Float,
    weight: FontWeight,
    color: Color,
    tabular: Boolean = false,
    letterSpacing: Float = 0f,
): TextStyle = TextStyle(
    fontSize = fontSize.sp,
    lineHeight = with(density) { lineHeightDp.dp.toSp() },
    fontWeight = weight,
    color = color,
    letterSpacing = letterSpacing.sp,
    fontFeatureSettings = if (tabular) TNUM else null,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/** The section header styles, measured once by the sticky copy too. */
@Immutable
internal class HeaderStyles(density: Density) {
    val label = ledgerStyle(density, LBL_FS, LBL_LH, FontWeight.Bold, INK, letterSpacing = -0.2f)
    val sub = ledgerStyle(density, 13f, SUB_LH, FontWeight.Normal, MUTE, tabular = true)
    val total = ledgerStyle(density, 15f, TOT_LH, FontWeight.SemiBold, MUTE, tabular = true)
}

internal fun paymentsLabel(count: Int) = if (count == 1) "1 payment" else "$count payments"

@Composable
internal fun SectionHeaderRow(item: LedgerItem.Header, styles: HeaderStyles) {
    Box(Modifier.fillMaxWidth().height(HEADER_H.dp).drawWithCache { onDrawBehind { drawRect(BG) } }) {
        Text(
            item.label,
            style = styles.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.offset(x = SIDE.dp, y = LBL_Y.dp).padding(end = (SIDE + RAIL_W + 110f).dp),
        )
        Text(paymentsLabel(item.count), style = styles.sub, modifier = Modifier.offset(x = SIDE.dp, y = SUB_Y.dp))
        Text(
            formatMoney(-item.spent, true),
            style = styles.total,
            modifier = Modifier.align(Alignment.TopEnd).padding(top = TOT_Y.dp, end = (RAIL_W + 4f).dp),
        )
    }
}

@Composable
internal fun TransactionRow(item: LedgerItem.Row) {
    val txn = item.txn
    val income = txn.cents > 0
    val hairPx = 1f
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .drawWithCache {
                val left = (SIDE + 52f).dp.toPx()
                onDrawBehind {
                    if (!item.last) drawRect(HAIR, topLeft = Offset(left, size.height - hairPx), size = Size(size.width - left, hairPx))
                }
            }
            .padding(start = SIDE.dp, end = (RAIL_W + 4f).dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(40.dp)
                .clip(CircleShape)
                .drawWithCache {
                    val fill = Brush.linearGradient(
                        0f to txn.tint.first, 0.7f to txn.tint.second, 1f to txn.tint.second,
                        start = Offset(size.width * 0.2f, 0f),
                        end = Offset(size.width * 0.8f, size.height),
                    )
                    val d = density
                    onDrawBehind {
                        drawRect(fill)
                        drawRoundRect(
                            Color(1f, 1f, 1f, 0.45f),
                            topLeft = Offset(6f * d, 1f * d),
                            size = Size(size.width - 12f * d, 1f * d),
                            cornerRadius = CornerRadius(1f * d),
                        )
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(txn.initial, color = Color(0xFFFFFDF8), fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp, end = 8.dp)) {
            Text(txn.name, fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = INK, letterSpacing = (-0.1).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(txn.meta, fontSize = 13.sp, color = MUTE, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.padding(top = 2.dp))
        }
        Text(
            formatMoney(txn.cents, true),
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (income) INCOME else INK,
            style = TextStyle(fontFeatureSettings = TNUM),
        )
    }
}

/** Stable header styles for this density (font scale included, so the sticky copy and the rows agree). */
@Composable
internal fun rememberHeaderStyles(): HeaderStyles {
    val density = LocalDensity.current
    return androidx.compose.runtime.remember(density) { HeaderStyles(density) }
}
