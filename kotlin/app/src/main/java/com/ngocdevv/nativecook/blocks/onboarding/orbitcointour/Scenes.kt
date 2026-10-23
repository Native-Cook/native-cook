package com.ngocdevv.nativecook.blocks.onboarding.orbitcointour

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin

// The three cards the coins open into, in stage dp. `reveal` runs from 0 to 1 once a scene is in
// front, and each part of its card comes in over its own stretch of that. It is read while
// drawing only, so a card is not composed again for every frame.

data class SceneText(val title: String, val subtitle: String)

val SampleScenes = listOf(
    SceneText("Savings Account", "Pay. Track. Save. Invest."),
    SceneText("The 3-in-1 Credit Card", "Assured 2% cashback"),
    SceneText("Get powerful Insights", "Track all your spends"),
)

private const val CARD_X = CX - 152f
private const val CARD_WIDTH = 304f
private const val ROW = 56f
private const val SPEND_ROW = 48f

// The balance counts up from this much less.
private const val COUNT_FROM = 436f

private val Links = listOf("Credit Card", "Loans", "Investments")

private class Spend(val name: String, val amount: Float, val bar: Float)

private val Spends = listOf(Spend("Shopping", 14140f, 100f), Spend("Food", 8890f, 34f), Spend("Entertainment", 4103f, 24f), Spend("Bill Payments", 2372f, 12f))

// A right arrow in a 16 × 14 box.
private val Arrow = Path().apply {
    moveTo(2f, 7f); lineTo(14f, 7f)
    moveTo(9f, 2f); lineTo(14f, 7f); lineTo(9f, 12f)
}

// The chart: thin lines rising to the right, the first quarter green, the rest dotted grey.
private const val CHART_LINES = 36
private const val CHART_GREEN = 9
private const val CHART_LEFT = 24f
private const val CHART_STEP = 7.3f
private const val CHART_BASE = 96f
private val ChartDots = PathEffect.dashPathEffect(floatArrayOf(2f, 3f))

private fun lineHeight(i: Int) = 14f + 70f * (i / (CHART_LINES - 1f)).pow(1.5f) + 6f * sin(i * 1.7f)

internal fun money(symbol: String, value: Float): String {
    var digits = value.roundToInt().toString()
    var out = ""
    while (digits.length > 3) {
        out = "," + digits.takeLast(3) + out
        digits = digits.dropLast(3)
    }
    return symbol + digits + out
}

/** The balance while it counts up to [value] over the middle of the reveal. */
private fun count(reveal: Float, value: Float): Float {
    val u = 1f - clamp01((reveal - 0.1f) / 0.65f)
    return value - COUNT_FROM * u * u * u
}

private fun fade(reveal: Float, from: Float) = smooth(reveal, from, from + 0.25f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun tourText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    fontFeatureSettings = "tnum",
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val TagStyle = tourText(7f, 10f, FontWeight.Bold, Color(0xFF136F63), 0.4f)
private val CardTitleStyle = tourText(15f, 20f, FontWeight.Medium, Color.White)
private val FigureStyle = tourText(36f, 44f, FontWeight.Normal, Color.White)
private val ActionStyle = tourText(14f, 18f, FontWeight.Normal, Ink)
private val LinkStyle = tourText(14f, 18f, FontWeight.Medium, Ink)
private val SpendStyle = tourText(13f, 18f, FontWeight.Medium, Ink)
private val PayNameStyle = tourText(30f, 36f, FontWeight.Bold, Color.White)
private val OfferStyle = tourText(20f, 26f, FontWeight.SemiBold, Color(0xFF388370))

/** The two lines under a card. */
@Composable
private fun BoxScope.Caption(text: SceneText, color: Color, mute: Color) {
    BasicText(
        text.title,
        style = tourText(28f, 34f, FontWeight.Light, color).copy(textAlign = TextAlign.Center),
        maxLines = 1,
        modifier = Modifier.offset(y = 651.dp).fillMaxWidth(),
    )
    BasicText(
        text.subtitle,
        style = tourText(20f, 26f, FontWeight.Light, mute).copy(textAlign = TextAlign.Center),
        maxLines = 1,
        modifier = Modifier.offset(y = 693.dp).fillMaxWidth(),
    )
}

/** A white card with a coloured block at its top. */
@Composable
private fun CardBody(top: Float, height: Float, block: Float, color: Color) {
    Box(
        Modifier
            .offset(CARD_X.dp, top.dp)
            .size(CARD_WIDTH.dp, height.dp)
            .shadow(14.dp, RoundedCornerShape(14.dp), ambientColor = Color.Black.copy(alpha = 0.3f), spotColor = Color.Black.copy(alpha = 0.3f))
            .background(Color.White, RoundedCornerShape(14.dp)),
    )
    Box(
        Modifier
            .offset(CARD_X.dp, top.dp)
            .size(CARD_WIDTH.dp, block.dp)
            .background(color, RoundedCornerShape(topStart = 14.dp, topEnd = 14.dp)),
    )
}

/** Scene 1: a savings account on a green card, with three links under it. */
@Composable
internal fun BoxScope.Savings(reveal: () -> Float, text: SceneText, balance: Float, symbol: String, enabled: Boolean, onAction: () -> Unit) {
    val hero = Heroes[0]
    val top = hero.y - hero.h / 2f
    val shown by remember(balance) { derivedStateOf { count(reveal(), balance).roundToInt() } }
    CardBody(top, hero.h + ROW * 3f + 8f, hero.h, Color(0xFF136F63))
    Box(
        Modifier
            .offset((CARD_X + CARD_WIDTH - 14f).dp, (top + 16f).dp)
            // Its right edge is 14 dp inside the card's.
            .graphicsLayer {
                translationX = -size.width
                alpha = fade(reveal(), 0f)
            }
            .height(14.dp)
            .background(Color.White, RoundedCornerShape(2.dp))
            .padding(horizontal = 5.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("NORTH BANK", style = TagStyle, maxLines = 1)
    }
    BasicText(text.title, style = CardTitleStyle, maxLines = 1, modifier = Modifier.offset((CARD_X + 24f).dp, (top + 56f).dp).graphicsLayer { alpha = fade(reveal(), 0f) })
    BasicText(money(symbol, shown.toFloat()), style = FigureStyle, maxLines = 1, modifier = Modifier.offset((CARD_X + 24f).dp, (top + 84f).dp).graphicsLayer { alpha = fade(reveal(), 0.08f) })
    Box(
        Modifier
            .offset((CARD_X + 22f).dp, (top + 176f).dp)
            .size((CARD_WIDTH - 44f).dp, 45.dp)
            .graphicsLayer { alpha = fade(reveal(), 0.16f) }
            .background(Color.White, RoundedCornerShape(4.dp))
            .clip(RoundedCornerShape(4.dp))
            // A scene that is not in front is still laid out: it must not take the press.
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onAction),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Add Money", style = ActionStyle, maxLines = 1)
    }
    Links.forEachIndexed { index, label ->
        Row(
            Modifier
                .offset((CARD_X + 22f).dp, (top + hero.h + index * ROW).dp)
                .size((CARD_WIDTH - 44f).dp, ROW.dp)
                .graphicsLayer { alpha = fade(reveal(), 0.3f + index * 0.08f) }
                .drawBehind {
                    val line = 1.dp.toPx()
                    drawRect(if (index == 0) Color(0xFFFA776B) else Color(0xFFECE7E0), Offset(0f, size.height - line), size.copy(height = line))
                },
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(label, style = LinkStyle, maxLines = 1)
            Canvas(Modifier.size(16.dp, 14.dp)) {
                surface(0.dp) { _, _, _, _ -> drawPath(Arrow, Ink, style = Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round)) }
            }
        }
    }
    Caption(text, Ink, Color(0xFF6B655C))
}

/** Scene 2: a payment card standing upright, and the offer that pops up over its foot. */
@Composable
internal fun BoxScope.PayCard(reveal: () -> Float, text: SceneText) {
    val hero = Heroes[1]
    Box(
        Modifier
            .offset((hero.x - hero.w / 2f).dp, (hero.y - hero.h / 2f).dp)
            .size(hero.w.dp, hero.h.dp)
            .clip(RoundedCornerShape(hero.r.dp)),
    ) {
        Canvas(Modifier.size(144.dp, hero.h.dp).background(Color(0xFFE86262))) {
            surface(0.dp) { _, _, _, _ ->
                withTransform({
                    translate(-22f, 150f)
                    scale(3.2f, 3.2f, pivot = Offset.Zero)
                }) {
                    drawPath(Letter, Color.Black, alpha = 0.07f)
                }
            }
        }
        Box(Modifier.offset(x = 144.dp).size((hero.w - 144f).dp, hero.h.dp).background(Color.White.copy(alpha = 0.18f)))
        BasicText("peak", style = PayNameStyle, maxLines = 1, modifier = Modifier.offset(20.dp, 24.dp).graphicsLayer { alpha = fade(reveal(), 0f) })
        Column(
            Modifier
                .offset(158.dp, 28.dp)
                .size(28.dp, 40.dp)
                .graphicsLayer { alpha = fade(reveal(), 0f) }
                .background(Color(0xFFD9DCE0), RoundedCornerShape(5.dp)),
            verticalArrangement = Arrangement.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFA9AEB5)))
            Spacer(Modifier.height(12.dp))
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0xFFA9AEB5)))
        }
    }
    Box(
        Modifier
            .offset((CX - 150f).dp, 500.dp)
            .size(300.dp, 76.dp)
            .graphicsLayer {
                // Past its size and back.
                val u = smooth(reveal(), 0.45f, 0.85f) - 1f
                val back = 1f + 2.7f * u * u * u + 1.7f * u * u
                scaleX = 0.8f + 0.2f * back
                scaleY = scaleX
                alpha = smooth(reveal(), 0.45f, 0.6f)
            }
            .shadow(14.dp, RoundedCornerShape(6.dp), ambientColor = Color.Black.copy(alpha = 0.4f), spotColor = Color.Black.copy(alpha = 0.4f))
            .background(Color.White, RoundedCornerShape(6.dp)),
        contentAlignment = Alignment.Center,
    ) {
        BasicText("Lifetime Free", style = OfferStyle, maxLines = 1)
    }
    Caption(text, Color.White, Color.White.copy(alpha = 0.75f))
}

/** Scene 3: where the money went, under a chart that grows from the left. */
@Composable
internal fun BoxScope.Insights(reveal: () -> Float, text: SceneText, balance: Float, symbol: String) {
    val hero = Heroes[2]
    val top = hero.y - hero.h / 2f
    val shown by remember(balance) { derivedStateOf { count(reveal(), balance).roundToInt() } }
    CardBody(top, hero.h + 20f + SPEND_ROW * Spends.size, hero.h, Color(0xFF363B42))
    BasicText("Money", style = CardTitleStyle, maxLines = 1, modifier = Modifier.offset((CARD_X + 24f).dp, (top + 30f).dp).graphicsLayer { alpha = fade(reveal(), 0f) })
    BasicText(
        money(symbol, shown.toFloat()),
        style = FigureStyle.copy(fontSize = 34.sp),
        maxLines = 1,
        modifier = Modifier.offset((CARD_X + 24f).dp, (top + 56f).dp).graphicsLayer { alpha = fade(reveal(), 0.08f) },
    )
    Canvas(Modifier.offset(CARD_X.dp, (top + 110f).dp).size(CARD_WIDTH.dp, 100.dp)) {
        surface(0.dp) { _, _, _, _ ->
            // The lines grow from the baseline, left to right, four at a time.
            val grown = smooth(reveal(), 0.1f, 0.8f) * (CHART_LINES + 4)
            for (i in 0 until CHART_LINES) {
                val part = clamp01((grown - i) / 4f)
                if (part <= 0f) continue
                val x = CHART_LEFT + i * CHART_STEP
                val green = i < CHART_GREEN
                drawLine(
                    if (green) Color(0xFF3BAA5C) else Color(0xFF8A9099),
                    Offset(x, CHART_BASE),
                    Offset(x, CHART_BASE - lineHeight(i) * part),
                    strokeWidth = 2f,
                    pathEffect = if (green) null else ChartDots,
                )
            }
        }
    }
    Spends.forEachIndexed { index, spend ->
        val from = 0.3f + index * 0.08f
        Row(
            Modifier
                .offset((CARD_X + 24f).dp, (top + hero.h + 10f + index * SPEND_ROW).dp)
                .size((CARD_WIDTH - 48f).dp, SPEND_ROW.dp)
                // The row slides up into place and its bar grows from the left.
                .graphicsLayer {
                    val arrived = smooth(reveal(), from, from + 0.3f)
                    alpha = arrived
                    translationY = 8.dp.toPx() * (1f - arrived)
                }
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                BasicText(spend.name, style = SpendStyle, maxLines = 1)
                Spacer(Modifier.height(5.dp))
                Box(
                    Modifier
                        .size(spend.bar.dp, 3.dp)
                        .graphicsLayer {
                            transformOrigin = TransformOrigin(0f, 0.5f)
                            scaleX = smooth(reveal(), from + 0.1f, from + 0.5f)
                        }
                        .background(Ink, RoundedCornerShape(1.5.dp)),
                )
            }
            BasicText(money(symbol, spend.amount), style = SpendStyle, maxLines = 1)
        }
    }
    Caption(text, Color(0xFF4D423B), Color(0xFF4D423B))
}
