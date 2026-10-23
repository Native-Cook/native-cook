package com.ngocdevv.nativecook.blocks.cards.swipedowndeck

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class SwipeDownDeckArt { Barrel, Pills, Capsules, Tablet, Plate, Bowl, Cup }

data class SwipeDownDeckCard(
    val title: String,
    /** The card's flat colour. */
    val color: Color,
    val art: SwipeDownDeckArt,
    val level: Int,
    val badge: String,
    val tags: List<String>,
    /** The chips that show this card. */
    val filters: List<String>,
    val description: String,
)

val SwipeDownDeckSamples = listOf(
    SwipeDownDeckCard(
        "Ice Bath", Color(0xFF2F5BE6), SwipeDownDeckArt.Barrel, 1, "Updated", listOf("Protocols", "Recovery"), listOf("Protocols"),
        "Ice baths involve immersion in cold water to reduce inflammation, enhance recovery, and build mental resilience.",
    ),
    SwipeDownDeckCard(
        "Magnesium", Color(0xFFF07A25), SwipeDownDeckArt.Pills, 2, "Updated", listOf("Supplements", "Sleep"), listOf("Supplements"),
        "Magnesium is a mineral used in over 300 processes in the body, important for sleep, muscles and calm nerves.",
    ),
    SwipeDownDeckCard(
        "Zinc", Color(0xFF2F5BE6), SwipeDownDeckArt.Capsules, 1, "New", listOf("Supplements", "Immunity"), listOf("New", "Supplements"),
        "Zinc is an essential trace mineral that supports immune function, wound healing and protein production.",
    ),
    SwipeDownDeckCard(
        "Vitamin D", Color(0xFFF0B21E), SwipeDownDeckArt.Tablet, 3, "Updated", listOf("Supplements", "Immunity"), listOf("Supplements"),
        "Vitamin D, often called the sunshine vitamin, is crucial for bone health, immune function and mood.",
    ),
    SwipeDownDeckCard(
        "Carnivore Diet", Color(0xFFC4451C), SwipeDownDeckArt.Plate, 2, "Updated", listOf("Diets", "Performance"), listOf("Diets"),
        "The carnivore diet focuses on animal-based foods, said to reduce inflammation and improve digestion for some.",
    ),
    SwipeDownDeckCard(
        "Vegan Diet", Color(0xFF7FD43A), SwipeDownDeckArt.Bowl, 1, "Updated", listOf("Diets", "Longevity"), listOf("Diets"),
        "A vegan diet excludes all animal products, relying on plant foods rich in fibre, vitamins and antioxidants.",
    ),
    SwipeDownDeckCard(
        "Water Fast", Color(0xFF2EA8E8), SwipeDownDeckArt.Cup, 3, "New", listOf("Protocols", "Longevity"), listOf("New", "Protocols"),
        "Water fasting means consuming only water for a set period, to promote autophagy and cellular repair.",
    ),
)

private const val PAD = 16
private const val PILL_H = 26
private const val ROUND = 32
private const val PLATE = 184
private const val ART = 150
private const val RADIUS = 28
private val Wash = Color.Black.copy(alpha = 0.18f)
private val WashPressed = Color.Black.copy(alpha = 0.32f)

internal fun deckText(size: Float, lineHeight: Float, weight: FontWeight, color: Color, spacing: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val PillStyle = deckText(12f, 15f, FontWeight.SemiBold, Color.White)
private val TitleStyle = deckText(30f, 36f, FontWeight.Normal, Color.White, -0.4f)
private val TagStyle = deckText(11f, 14f, FontWeight.SemiBold, Color.White)
private val TextStyleBody = deckText(13f, 18f, FontWeight.Normal, Color.White.copy(alpha = 0.82f)).copy(textAlign = TextAlign.Center)

/** The face of a card: badges and the add button, the illustration on its plate, name, tags, text. */
@Composable
internal fun SwipeDownDeckCardFace(card: SwipeDownDeckCard, width: Dp, height: Dp, enabled: Boolean, onAdd: () -> Unit) {
    val shape = RoundedCornerShape(RADIUS.dp)
    Column(
        Modifier
            .size(width, height)
            .clip(shape)
            .background(card.color)
            // Light from above and a little shade at the foot, so a flat colour reads as a surface.
            .background(Brush.verticalGradient(0f to Color.White.copy(alpha = 0.16f), 0.45f to Color.White.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.18f)))
            .border(1.dp, Color.White.copy(alpha = 0.16f), shape)
            .padding(start = PAD.dp, end = PAD.dp, top = PAD.dp, bottom = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(Modifier.fillMaxWidth().height(ROUND.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill {
                Canvas(Modifier.size(12.dp)) { drawPerson(Color.White) }
                BasicText("${card.level}", style = PillStyle)
            }
            Pill {
                Canvas(Modifier.size(12.dp)) { drawAlert(card.color) }
                BasicText(card.badge, style = PillStyle)
            }
            Spacer(Modifier.weight(1f))
            val source = remember { MutableInteractionSource() }
            val pressed by source.collectIsPressedAsState()
            Box(
                Modifier
                    .size(ROUND.dp)
                    .clip(CircleShape)
                    .background(if (pressed) WashPressed else Wash)
                    .clickable(source, indication = null, enabled = enabled, onClick = onAdd)
                    .semantics { contentDescription = "Add ${card.title} to the deck" },
                contentAlignment = Alignment.Center,
            ) {
                SwipeDownDeckIcon(SwipeDownDeckIcons.Add, Color.White, 16.dp, 1.8f)
            }
        }
        Spacer(Modifier.weight(1f))
        SwipeDownDeckArtPlate(card.art, Modifier.size(PLATE.dp))
        BasicText(card.title, Modifier.padding(top = 18.dp), style = TitleStyle, maxLines = 1)
        Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            for (tag in card.tags) {
                Box(Modifier.height(24.dp).clip(RoundedCornerShape(12.dp)).background(Wash).padding(horizontal = 10.dp), contentAlignment = Alignment.Center) {
                    BasicText(tag, style = TagStyle)
                }
            }
        }
        BasicText(card.description, Modifier.padding(top = 14.dp), style = TextStyleBody, maxLines = 3, overflow = TextOverflow.Ellipsis)
        Spacer(Modifier.weight(1f))
    }
}

@Composable
private fun Pill(content: @Composable () -> Unit) {
    Row(
        Modifier.height(PILL_H.dp).clip(RoundedCornerShape(PILL_H.dp / 2)).background(Wash).padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp),
    ) { content() }
}

// One shape of an illustration, in a 100 × 100 box: an ellipse or a rounded rect, its corner radius,
// a rotation in degrees about its own centre, colour and alpha.
private class ArtShape(val ellipse: Boolean, val x: Float, val y: Float, val w: Float, val h: Float, val r: Float, val deg: Float, val color: Long, val alpha: Float)

private fun e(x: Float, y: Float, w: Float, h: Float, deg: Float, color: Long, alpha: Float = 1f) = ArtShape(true, x, y, w, h, 0f, deg, color, alpha)
private fun r(x: Float, y: Float, w: Float, h: Float, r: Float, deg: Float, color: Long, alpha: Float = 1f) = ArtShape(false, x, y, w, h, r, deg, color, alpha)

private val Shadow = e(18f, 80f, 64f, 12f, 0f, 0x000000, 0.22f)

private val Shapes = mapOf(
    SwipeDownDeckArt.Barrel to listOf(
        Shadow,
        r(20f, 34f, 60f, 52f, 14f, 0f, 0xc98a4b),
        r(18f, 46f, 64f, 6f, 3f, 0f, 0x9aa3ab),
        r(18f, 70f, 64f, 6f, 3f, 0f, 0x9aa3ab),
        e(17f, 24f, 66f, 22f, 0f, 0xb07235),
        e(22f, 27f, 56f, 16f, 0f, 0xcfeaf8),
        r(30f, 28f, 11f, 9f, 2.5f, 18f, 0xffffff),
        r(45f, 29f, 11f, 9f, 2.5f, -12f, 0xf1faff),
        r(58f, 30f, 10f, 8f, 2.5f, 24f, 0xffffff),
    ),
    SwipeDownDeckArt.Pills to listOf(
        Shadow,
        r(12f, 40f, 58f, 26f, 13f, -32f, 0xf3ece2),
        r(21f, 50f, 40f, 6f, 3f, -32f, 0xffffff, 0.8f),
        r(32f, 46f, 58f, 26f, 13f, 18f, 0xe8decf),
        r(41f, 56f, 40f, 6f, 3f, 18f, 0xffffff, 0.7f),
    ),
    SwipeDownDeckArt.Capsules to listOf(
        Shadow,
        r(16f, 34f, 62f, 26f, 13f, -40f, 0x5cc2ff),
        r(26f, 44f, 42f, 6f, 3f, -40f, 0xb8e6ff),
        r(30f, 50f, 60f, 24f, 12f, -40f, 0x3aa0f0),
        r(40f, 59f, 40f, 6f, 3f, -40f, 0x9ad8ff),
    ),
    SwipeDownDeckArt.Tablet to listOf(
        Shadow,
        e(16f, 36f, 68f, 44f, 0f, 0xd9c9a6),
        e(16f, 28f, 68f, 44f, 0f, 0xf4e9d0),
        r(26f, 48f, 48f, 4f, 2f, 0f, 0xd9c9a6),
    ),
    SwipeDownDeckArt.Plate to listOf(
        e(12f, 76f, 76f, 14f, 0f, 0x000000, 0.22f),
        e(10f, 28f, 80f, 54f, 0f, 0xe9e2d6),
        e(16f, 32f, 68f, 44f, 0f, 0xf6f1e8),
        e(20f, 38f, 34f, 26f, -18f, 0x6e2f1f),
        e(24f, 42f, 22f, 14f, -18f, 0x8a4130),
        e(50f, 36f, 28f, 22f, 0f, 0xffffff),
        e(58f, 42f, 12f, 10f, 0f, 0xf5a623),
        e(52f, 54f, 24f, 18f, 0f, 0xffffff),
        e(59f, 58f, 11f, 9f, 0f, 0xf5a623),
    ),
    SwipeDownDeckArt.Bowl to listOf(
        Shadow,
        e(12f, 34f, 76f, 50f, 0f, 0xefe8dc),
        e(18f, 36f, 64f, 36f, 0f, 0x3f7a2c),
        e(22f, 38f, 22f, 16f, -20f, 0x7bc043),
        e(40f, 37f, 20f, 15f, 15f, 0x5fa83a),
        e(58f, 40f, 18f, 14f, 0f, 0xe2483a),
        e(30f, 52f, 16f, 12f, 0f, 0x9c6b4a),
        e(48f, 52f, 14f, 11f, 0f, 0xe2483a),
        e(62f, 52f, 14f, 10f, 0f, 0xc9a27e),
    ),
    SwipeDownDeckArt.Cup to listOf(
        e(26f, 80f, 48f, 10f, 0f, 0x000000, 0.22f),
        r(28f, 30f, 44f, 54f, 12f, 0f, 0xf1e9dc),
        e(28f, 22f, 44f, 16f, 0f, 0xe3d7c3),
        e(31f, 24f, 38f, 11f, 0f, 0x7fc8ee),
        r(33f, 36f, 6f, 40f, 3f, 0f, 0xffffff, 0.4f),
    ),
)

/** An illustration on its round, lit plate. */
@Composable
private fun SwipeDownDeckArtPlate(art: SwipeDownDeckArt, modifier: Modifier) {
    Canvas(modifier) {
        val k = density
        val plate = PLATE * k
        drawCircle(
            Brush.radialGradient(listOf(Color.White.copy(alpha = 0.26f), Color.White.copy(alpha = 0.04f)), Offset(plate * 0.5f, plate * 0.38f), plate * 0.6f),
            radius = plate / 2,
        )
        drawCircle(Color.White.copy(alpha = 0.14f), radius = plate / 2 - 0.5f * k, style = Stroke(1f * k))
        val scale = ART / 100f * k
        val inset = (PLATE - ART) / 2f * k
        for (shape in Shapes.getValue(art)) {
            val w = shape.w * scale
            val h = shape.h * scale
            withTransform({
                translate(inset + (shape.x + shape.w / 2) * scale, inset + (shape.y + shape.h / 2) * scale)
                rotate(shape.deg, pivot = Offset.Zero)
            }) {
                val color = Color(0xFF000000 or shape.color).copy(alpha = shape.alpha)
                if (shape.ellipse) {
                    drawOval(color, Offset(-w / 2, -h / 2), Size(w, h))
                } else {
                    drawRoundRect(color, Offset(-w / 2, -h / 2), Size(w, h), CornerRadius(shape.r * scale))
                }
            }
        }
    }
}

// Small icons, in a 16 × 16 box (strokes) or a 12 × 12 box (the pills' filled marks).
private fun DrawScope.drawPerson(color: Color) {
    val k = size.width / 12f
    drawCircle(color, 2.3f * k, Offset(6f * k, 3.2f * k))
    drawRoundRect(color, Offset(3f * k, 6.4f * k), Size(6f * k, 5.2f * k), CornerRadius(2.4f * k))
}

private fun DrawScope.drawAlert(ink: Color) {
    val k = size.width / 12f
    drawCircle(Color.White, 6f * k, Offset(6f * k, 6f * k))
    drawRoundRect(ink, Offset(5.2f * k, 2.6f * k), Size(1.6f * k, 4.6f * k), CornerRadius(0.8f * k))
    drawCircle(ink, 0.95f * k, Offset(6f * k, 9f * k))
}

internal enum class SwipeDownDeckIcons { Add, Search, Menu, Tray, Up }

private fun iconPath(icon: SwipeDownDeckIcons) = Path().apply {
    when (icon) {
        SwipeDownDeckIcons.Add -> {
            moveTo(8f, 2.5f); lineTo(8f, 11.5f)
            moveTo(4.5f, 8f); lineTo(8f, 11.5f); lineTo(11.5f, 8f)
            moveTo(3f, 14f); lineTo(13f, 14f)
        }
        SwipeDownDeckIcons.Search -> {
            addOval(Rect(2f, 2f, 12.2f, 12.2f))
            moveTo(11.6f, 11.6f); lineTo(14.5f, 14.5f)
        }
        SwipeDownDeckIcons.Menu -> for (x in listOf(4f, 8f, 12f)) {
            moveTo(x, 3.5f); lineTo(x, 12.5f)
        }
        SwipeDownDeckIcons.Tray -> {
            moveTo(5f, 1.5f); lineTo(10f, 1.5f)
            arcTo(Rect(8f, 1.5f, 12f, 5.5f), -90f, 90f, false)
            lineTo(12f, 7f)
            moveTo(8.5f, 14f); lineTo(5f, 14f)
            arcTo(Rect(3f, 10f, 7f, 14f), 90f, 90f, false)
            lineTo(3f, 3.5f)
            arcTo(Rect(3f, 1.5f, 7f, 5.5f), 180f, 90f, false)
            moveTo(12f, 9.5f); lineTo(12f, 15f)
            moveTo(9.8f, 12.8f); lineTo(12f, 15f); lineTo(14.2f, 12.8f)
        }
        SwipeDownDeckIcons.Up -> {
            moveTo(8f, 13f); lineTo(8f, 3.5f)
            moveTo(4.5f, 7f); lineTo(8f, 3.5f); lineTo(11.5f, 7f)
        }
    }
}

/** A stroked icon drawn [size] wide, with the stroke [width] in points at that size. */
@Composable
internal fun SwipeDownDeckIcon(icon: SwipeDownDeckIcons, color: Color, size: Dp, width: Float) {
    val path = remember(icon) { iconPath(icon) }
    Canvas(Modifier.size(size)) {
        val k = this.size.width / 16f
        scale(k, k, pivot = Offset.Zero) {
            drawPath(path, color, style = Stroke(width * density / k, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}
