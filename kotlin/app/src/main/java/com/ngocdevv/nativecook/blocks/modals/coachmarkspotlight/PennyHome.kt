package com.ngocdevv.nativecook.blocks.modals.coachmarkspotlight

// Penny's home screen under the tour: header, the month card, recent spends, the add button and the tab bar.
// Everything sits at positions from pennyLayout(), so the tour's cutouts land exactly on the real elements.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rgba
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal val PAPER = listOf(Color(0xFFF8F5F0), Color(0xFFF3EFE8), Color(0xFFECE7DE))
internal val CMS_CARD = Color(0xFFFFFDF9)
internal val CMS_INK = Color(0xFF1E2420)
internal val CMS_MUTE = Color(0xFF6E746F)
internal val CMS_GREEN = Color(0xFF2F7D5B)

private const val SIDE = 16f
private const val BRAND_H = 28f
private const val CARD_H = 164f
private const val ROW_H = 58f
private const val TAB_H = 50f
private const val FAB = 58f

internal class PennyLayout(
    val top: Float,
    val card: CmsBox,
    val fab: CmsBox,
    /** The Insights tab item (icon and label). */
    val tab: CmsBox,
    val tabTop: Float,
    val tabBottom: Float,
    val recentTop: Float,
    val rows: Int,
)

internal fun pennyLayout(width: Float, height: Float, insetTop: Float, insetBottom: Float): PennyLayout {
    val top = insetTop + 8f
    val card = CmsBox(SIDE, top + 146f, width - SIDE * 2f, CARD_H, 22f)
    val tabBottom = max(insetBottom, 8f)
    val tabTop = height - tabBottom - TAB_H
    val fab = CmsBox(width - 20f - FAB, tabTop - 18f - FAB, FAB, FAB, FAB / 2f)
    val tabW = width / TABS.size
    val tab = CmsBox(tabW * TOUR_TAB + (tabW - 80f) / 2f, tabTop + 3f, 80f, 44f, 14f)
    val recentTop = card.y + card.h + 22f
    val rows = max(2, min(RECENT.size, floor((tabTop - recentTop - 30f) / ROW_H).toInt()))
    return PennyLayout(top, card, fab, tab, tabTop, tabBottom, recentTop, rows)
}

private fun money(n: Float) = "$" + String.format(Locale.US, "%,.2f", n)

internal fun cmsSvg(d: String): Path = PathParser().parsePathString(d).toPath()

/** Draws in an SVG viewBox `vb` units wide, scaled to the canvas. */
internal inline fun DrawScope.inViewBox(vb: Float, block: DrawScope.() -> Unit) {
    scale(size.width / vb, size.width / vb, pivot = Offset.Zero) { block() }
}

internal fun DrawScope.strokePath(path: Path, color: Color, width: Float) {
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

/** A band `dy` px tall along the inside top edge of a rounded box: RN's `inset 0 dy 0`. */
internal fun insetBand(size: Size, radius: Float, dy: Float): Path {
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(radius))) }
    return Path().apply { op(outer, inner, PathOperation.Difference) }
}

/** Fill, then a 1 dp inner highlight on top (RN `inset 0 1px 0 rgba(255,255,255,a)`). */
internal fun Modifier.fillWithHighlight(color: Color, radius: Dp, highlight: Float): Modifier = drawWithCache {
    val r = radius.toPx()
    val band = insetBand(size, r, 1.dp.toPx())
    onDrawBehind {
        drawRoundRect(color, cornerRadius = CornerRadius(r))
        drawPath(band, Color.White.copy(alpha = highlight))
    }
}

private val COIN_P = cmsSvg("M10 16.5 V7.8 H13.1 A2.6 2.6 0 0 1 13.1 13 H10")
private val CHEVRON = cmsSvg("M2 3.8 L5 6.8 L8 3.8")
private val PLUS = cmsSvg("M12 5 V19 M5 12 H19")

@Composable
private fun PennyMark() {
    Canvas(Modifier.size(24.dp)) {
        inViewBox(24f) {
            drawCircle(CMS_GREEN, 11f, Offset(12f, 12f))
            drawCircle(Color(0xFF9ED2B6), 7.6f, Offset(12f, 12f), style = Stroke(1.2f))
            strokePath(COIN_P, Color(0xFFF4FBF7), 1.8f)
        }
    }
}

private val CART = cmsSvg("M2.5 3.5 H4.6 L6.4 12.6 H15.3 L16.9 6.2 H5.3")
private val CUP = cmsSvg("M4 7 H14 V11.5 A4 4 0 0 1 10 15.5 H8 A4 4 0 0 1 4 11.5 Z M14 8.4 H15.4 A2 2 0 0 1 15.4 12.4 H14 M6.5 2.8 V4.6 M9.5 2.8 V4.6")
private val TRAIN_BODY = cmsSvg("M7.5 2.8 H12.5 A3 3 0 0 1 15.5 5.8 V11.3 A3 3 0 0 1 12.5 14.3 H7.5 A3 3 0 0 1 4.5 11.3 V5.8 A3 3 0 0 1 7.5 2.8 Z")
private val TRAIN_LINES = cmsSvg("M4.8 9.2 H15.2 M7 17.2 L8.2 14.4 M13 17.2 L11.8 14.4")
private val LEAF = cmsSvg("M4 16 C4 9 8.5 4.5 16 4 C15.6 11.5 11 16 4 16 Z M4 16 L10.5 9.5")

@Composable
private fun SpendIcon(kind: SpendGlyph, color: Color) {
    Canvas(Modifier.size(20.dp)) {
        inViewBox(20f) {
            val w = 1.7f
            when (kind) {
                SpendGlyph.Cart -> {
                    strokePath(CART, color, w)
                    drawCircle(color, 1.2f, Offset(7.4f, 16f), style = Stroke(w))
                    drawCircle(color, 1.2f, Offset(14.2f, 16f), style = Stroke(w))
                }
                SpendGlyph.Cup -> strokePath(CUP, color, w)
                SpendGlyph.Train -> {
                    strokePath(TRAIN_BODY, color, w)
                    strokePath(TRAIN_LINES, color, w)
                }
                SpendGlyph.Leaf -> strokePath(LEAF, color, w)
            }
        }
    }
}

private val HOME = cmsSvg("M4.5 11 L13 4 L21.5 11 V19 A1.5 1.5 0 0 1 20 20.5 H15.5 V14.5 H10.5 V20.5 H6 A1.5 1.5 0 0 1 4.5 19 Z")
private val BUDGETS_BOX = cmsSvg("M7.2 5 H18.8 A3.2 3.2 0 0 1 22 8.2 V16.3 A3.2 3.2 0 0 1 18.8 19.5 H7.2 A3.2 3.2 0 0 1 4 16.3 V8.2 A3.2 3.2 0 0 1 7.2 5 Z")
private val BUDGETS_LINES = cmsSvg("M4 9.5 H22 M16.5 14.5 H18.5")
private val INSIGHTS = cmsSvg("M5 20 V13 M10.3 20 V8.5 M15.6 20 V11.5 M20.9 20 V4.5")
private val PROFILE_BODY = cmsSvg("M5 20.5 C6.3 16.4 9.3 14.6 13 14.6 C16.7 14.6 19.7 16.4 21 20.5")

@Composable
private fun TabGlyph(kind: String, color: Color) {
    Canvas(Modifier.size(26.dp, 24.dp)) {
        inViewBox(26f) {
            val w = 1.8f
            when (kind) {
                "Home" -> strokePath(HOME, color, w)
                "Budgets" -> {
                    strokePath(BUDGETS_BOX, color, w)
                    strokePath(BUDGETS_LINES, color, w)
                }
                "Insights" -> strokePath(INSIGHTS, color, w)
                else -> {
                    drawCircle(color, 4.2f, Offset(13f, 8.5f), style = Stroke(w))
                    strokePath(PROFILE_BODY, color, w)
                }
            }
        }
    }
}

@Composable
private fun MonthCard(box: CmsBox) {
    val spent = CATEGORIES.sumOf { it.spent.toDouble() }.toFloat()
    val share = (spent / BUDGET * 100f).roundToInt()
    val barW = box.w - 32f - (CATEGORIES.size - 1) * 2f
    Box(
        Modifier
            .offset(box.x.dp, box.y.dp)
            .size(box.w.dp, box.h.dp)
            .cssBoxShadow(rgba(52, 44, 30, 0.08f), 1.dp, 2.dp, 22.dp)
            .cssBoxShadow(rgba(52, 44, 30, 0.07f), 12.dp, 28.dp, 22.dp)
            .fillWithHighlight(CMS_CARD, 22.dp, 0.9f)
            .semantics { contentDescription = "Spent this month ${money(spent)}, $share percent of budget" }
            .padding(16.dp),
    ) {
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().height(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Spent in November", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Medium, color = CMS_MUTE))
                Spacer(Modifier.weight(1f))
                Box(
                    Modifier
                        .height(22.dp)
                        .background(Color(0x1F2F7D5B), RoundedCornerShape(11.dp))
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("$share%", style = TextStyle(fontSize = 12.5.sp, fontWeight = FontWeight.Bold, color = CMS_GREEN, fontFeatureSettings = "tnum"))
                }
            }
            Text(
                money(spent),
                Modifier.padding(top = 2.dp).height(34.dp),
                style = TextStyle(fontSize = 28.sp, lineHeight = 34.sp, fontWeight = FontWeight.Bold, color = CMS_INK, fontFeatureSettings = "tnum"),
            )
            Row(
                Modifier
                    .padding(top = 12.dp)
                    .fillMaxWidth()
                    .height(10.dp)
                    .clip(RoundedCornerShape(5.dp))
                    .background(Color(0x121E2420)),
            ) {
                CATEGORIES.forEachIndexed { i, c ->
                    Box(
                        Modifier
                            .padding(start = if (i == 0) 0.dp else 2.dp)
                            .width((barW * c.spent / BUDGET).dp)
                            .fillMaxHeight()
                            .background(c.color),
                    )
                }
            }
            Column(Modifier.padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                CATEGORIES.chunked(2).forEach { pair ->
                    Row(Modifier.fillMaxWidth()) {
                        pair.forEach { c ->
                            Row(
                                Modifier.weight(1f).height(20.dp).padding(end = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Box(Modifier.size(8.dp).background(c.color, CircleShape))
                                Text(
                                    c.name,
                                    Modifier.padding(start = 7.dp).weight(1f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    style = TextStyle(fontSize = 13.sp, color = CMS_INK),
                                )
                                Text(money(c.spent), style = TextStyle(fontSize = 13.sp, color = CMS_MUTE, fontFeatureSettings = "tnum"))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SpendRow(spend: Spend) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_H.dp)
            .semantics { contentDescription = "${spend.place}, ${money(spend.amount)}, ${spend.time}" },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(40.dp).background(spend.color.copy(alpha = 0x1F / 255f), RoundedCornerShape(12.dp)),
            contentAlignment = Alignment.Center,
        ) {
            SpendIcon(spend.glyph, spend.color)
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(spend.place, maxLines = 1, overflow = TextOverflow.Ellipsis, style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CMS_INK))
            Text(
                "${spend.category} · ${spend.time}",
                Modifier.padding(top = 1.dp),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 13.sp, color = CMS_MUTE),
            )
        }
        Text(
            "−${money(spend.amount)}",
            Modifier.padding(start = 10.dp),
            style = TextStyle(fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = CMS_INK, fontFeatureSettings = "tnum"),
        )
    }
}

/** The app screen. Static: the tour moves over it. */
@Composable
internal fun PennyHome(layout: PennyLayout, width: Float) {
    val left = BUDGET - CATEGORIES.sumOf { it.spent.toDouble() }.toFloat()
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to PAPER[0], 0.55f to PAPER[1], 1f to PAPER[2])),
    ) {
        Column(Modifier.offset(SIDE.dp, layout.top.dp).width((width - SIDE * 2f).dp)) {
            Row(Modifier.fillMaxWidth().height(BRAND_H.dp), verticalAlignment = Alignment.CenterVertically) {
                PennyMark()
                Text("Penny", Modifier.padding(start = 8.dp), style = TextStyle(fontSize = 17.sp, fontWeight = FontWeight.Bold, color = CMS_INK))
                Spacer(Modifier.weight(1f))
                Row(
                    Modifier
                        .height(30.dp)
                        .background(Color(0x0F1E2420), RoundedCornerShape(15.dp))
                        .padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                ) {
                    Text("November", style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold, color = CMS_INK))
                    Canvas(Modifier.size(10.dp)) { inViewBox(10f) { strokePath(CHEVRON, CMS_INK, 1.5f) } }
                }
            }
            Text(
                "Good evening, Sam",
                Modifier.padding(top = 12.dp).height(20.dp),
                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, color = CMS_MUTE),
            )
            Row(Modifier.padding(top = 2.dp).height(48.dp)) {
                Text(
                    money(left),
                    Modifier.alignByBaseline(),
                    style = TextStyle(fontSize = 40.sp, lineHeight = 48.sp, fontWeight = FontWeight.Bold, color = CMS_INK, fontFeatureSettings = "tnum", letterSpacing = (-0.5).sp),
                )
                Text(
                    "left",
                    Modifier.padding(start = 8.dp).alignByBaseline(),
                    style = TextStyle(fontSize = 20.sp, fontWeight = FontWeight.Medium, color = CMS_MUTE),
                )
            }
            Text(
                "of ${money(BUDGET)} · 6 days to payday",
                Modifier.padding(top = 2.dp).height(18.dp),
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = CMS_MUTE),
            )
        }

        MonthCard(layout.card)

        Text(
            "Recent",
            Modifier.offset(SIDE.dp, layout.recentTop.dp).height(20.dp),
            style = TextStyle(fontSize = 17.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = CMS_INK),
        )
        Column(Modifier.offset(SIDE.dp, (layout.recentTop + 30f).dp).width((width - SIDE * 2f).dp)) {
            RECENT.take(layout.rows).forEach { SpendRow(it) }
        }

        val fab = layout.fab
        Box(
            Modifier
                .offset(fab.x.dp, fab.y.dp)
                .size(FAB.dp)
                .cssBoxShadow(rgba(20, 60, 42, 0.22f), 2.dp, 4.dp, (FAB / 2f).dp)
                .cssBoxShadow(rgba(20, 60, 42, 0.2f), 12.dp, 26.dp, (FAB / 2f).dp)
                .drawWithCache {
                    val brush = Brush.linearGradient(
                        0f to Color(0xFF4FA27C), 0.5f to CMS_GREEN, 1f to Color(0xFF22604A),
                        start = Offset(size.width * 0.2f, 0f),
                        end = Offset(size.width * 0.8f, size.height),
                    )
                    val band = insetBand(size, size.width / 2f, 1.dp.toPx())
                    onDrawBehind {
                        drawCircle(brush)
                        drawPath(band, Color.White.copy(alpha = 0.3f))
                    }
                }
                .semantics { contentDescription = "Add a spend" },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.size(24.dp)) { inViewBox(24f) { strokePath(PLUS, Color(0xFFF4FBF7), 2.4f) } }
        }

        Box(
            Modifier
                .offset(0.dp, layout.tabTop.dp)
                .fillMaxWidth()
                .height((TAB_H + layout.tabBottom).dp)
                .background(Color(0xFFFBF9F5).copy(alpha = 0.96f)),
        ) {
            Box(Modifier.fillMaxWidth().height(0.5.dp).background(Color(0x241E2420)))
            Row(Modifier.fillMaxWidth().height(TAB_H.dp)) {
                TABS.forEachIndexed { i, tab ->
                    val on = i == 0
                    Column(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .padding(top = 6.dp)
                            .semantics { contentDescription = if (on) "$tab, selected" else tab },
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        TabGlyph(tab, if (on) CMS_GREEN else Color(0xFF8C918C))
                        Text(
                            tab,
                            Modifier.padding(top = 2.dp),
                            style = TextStyle(
                                fontSize = 10.5.sp,
                                fontWeight = if (on) FontWeight.SemiBold else FontWeight.Medium,
                                color = if (on) CMS_GREEN else Color(0xFF8C918C),
                            ),
                        )
                    }
                }
            }
        }
    }
}
