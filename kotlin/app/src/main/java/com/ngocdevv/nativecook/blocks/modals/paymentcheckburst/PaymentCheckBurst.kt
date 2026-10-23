package com.ngocdevv.nativecook.blocks.modals.paymentcheckburst

import android.graphics.BlurMaskFilter
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.tan

class PaymentCheckBurstRow(val label: String, val value: String)

private val Green = Color(0xFF2FBF71)
private val GreenLight = Color(0xFF7BEAA9)
private val GreenDeep = Color(0xFF16A05A)
private val Page = Color(0xFFF3F4F6)
private val Paper = Color.White
private val Ink = Color(0xFF101318)
private val ValueInk = Color(0xFF1F2328)
private val Label = Color(0xFF8A8F98)
private val Rule = Color(0xFFDFE2E7)
private val SilverInk = Color(0xFF5F636B)
private val RewardInk = Color(0xFF3D4048)

private const val CHECK_SIZE = 148f
private const val CHECK_SMALL = 72f
private const val MARGIN = 16f
private const val CARD_RADIUS = 22f
private const val PAD = 20f
private const val HEAD_HEIGHT = 48f
private const val AMOUNT_HEIGHT = 92f
private const val RULE_HEIGHT = 24f
private const val ROW_HEIGHT = 38f
private const val TOTAL_HEIGHT = 48f
private const val TOOTH = 12f
private const val TEETH_HEIGHT = 8f
private const val PUNCH = 10f
private const val BAR_HEIGHT = 56f
private const val BAR_GAP = 18f

/** Room round the receipt's drawn paper for its shadow. */
private const val SHADOW_ROOM = 28f

/** The perspective of the flap, in dp, as React Native's `perspective: 500`. */
private const val FLAP_PERSPECTIVE = 500f

private val DefaultRows = listOf(
    PaymentCheckBurstRow("To", "Mia Carter"),
    PaymentCheckBurstRow("Email", "hello@example.com"),
    PaymentCheckBurstRow("Method", "E-Transfer"),
    PaymentCheckBurstRow("Date", "June 12, 2024 · 9:41"),
)

/** The tick inside the check, in the check's 148 dp box. */
private val CheckMark = Path().apply {
    moveTo(48f, 76f)
    lineTo(66f, 94f)
    lineTo(102f, 56f)
}

private val CheckGlow = SoftRoundRect(Green.copy(alpha = 0.45f), 12f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Float, lineHeight: Float, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = letterSpacing.sp, lineHeightStyle = Centered,
)

private fun money(cents: Int) = "${cents / 100}.${(cents % 100).toString().padStart(2, '0')}"

/**
 * The receipt's paper: rounded at the top, torn into teeth at the bottom, with a half-circle punch
 * on each side at [punchY]. Drawn [SHADOW_ROOM] in from the canvas edges.
 */
private fun paper(width: Float, height: Float, punchY: Float) = Path().apply {
    val o = SHADOW_ROOM
    val r = CARD_RADIUS
    val bottom = o + height
    moveTo(o, o + r)
    arcTo(Rect(o, o, o + 2 * r, o + 2 * r), 180f, 90f, false)
    lineTo(o + width - r, o)
    arcTo(Rect(o + width - 2 * r, o, o + width, o + 2 * r), 270f, 90f, false)
    lineTo(o + width, o + punchY - PUNCH)
    arcTo(Rect(o + width - PUNCH, o + punchY - PUNCH, o + width + PUNCH, o + punchY + PUNCH), -90f, -180f, false)
    lineTo(o + width, bottom)
    val count = (width / TOOTH).roundToInt()
    val step = width / count
    for (i in count downTo 1) {
        lineTo(o + (i - 0.5f) * step, bottom + TEETH_HEIGHT)
        lineTo(o + (i - 1) * step, bottom)
    }
    lineTo(o, o + punchY + PUNCH)
    arcTo(Rect(o - PUNCH, o + punchY - PUNCH, o + PUNCH, o + punchY + PUNCH), 90f, -180f, false)
    close()
}

/** A blurred copy of the paper, offset down, for its two shadows (Skia's blur is a sigma). */
private class PaperShadow(color: Color, sigma: Float, val dy: Float) {
    val paint = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        // Hardware canvases only honour mask filters from API 28.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }
}

private val PaperShadows = listOf(PaperShadow(Color(17, 24, 39).copy(alpha = 0.10f), 22f, 16f), PaperShadow(Color(17, 24, 39).copy(alpha = 0.08f), 3f, 1f))

/**
 * A payment receipt: a green check pops with confetti, shrinks to the top while the receipt card
 * slides up and its amount counts, then a silver bar peels back to show a reward and coins spill.
 * One clock drives all of it, so a touch and the autoPlay loop run the same motion.
 *
 * @param amount the amount in cents, counted up on the card.
 * @param reference the receipt's number, shown at its top right.
 * @param fee the fee row above the total.
 * @param reward the reward the bar reveals.
 * @param autoPlay plays by itself every [LOOP] seconds and ends on a white screen again.
 * @param onClaim called when the claim bar is pressed by a touch.
 */
@Composable
fun PaymentCheckBurst(
    modifier: Modifier = Modifier,
    amount: Int = 57862,
    currency: String = "CA $",
    title: String = "Payment Complete",
    subtitle: String = "Your transfer is on its way.",
    reference: String = "NC-20481",
    fee: String = "$0.00",
    rows: List<PaymentCheckBurstRow> = DefaultRows,
    claimLabel: String = "Peel to claim your reward",
    reward: String = "+400 points",
    autoPlay: Boolean = false,
    onClaim: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = rememberLoopClock(autoPlay)
    val t = { clock.seconds.value }
    val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value

    // Recomposes only when the shown number changes.
    val counted by remember(amount, reduced) {
        derivedStateOf {
            val now = clock.seconds.value
            (amount * (if (reduced) seg(now, COUNT_AT, COUNT_AT + REDUCED_FADE) else easeOut(seg(now, COUNT_AT, COUNT_AT + COUNT_FOR)))).roundToInt()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
            .semantics { contentDescription = title }
            .clickable(interactionSource = null, indication = null) { clock.touch(TAP_AT, false) },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val cardWidth = width - MARGIN * 2
        val centerY = height * 0.42f
        val smallY = insetTop + 76f
        val titleTop = smallY + CHECK_SMALL / 2f + 10f
        val cardTop = titleTop + 72f
        val punchY = HEAD_HEIGHT + AMOUNT_HEIGHT + RULE_HEIGHT / 2f
        val cardHeight = HEAD_HEIGHT + AMOUNT_HEIGHT + RULE_HEIGHT + rows.size * ROW_HEIGHT + RULE_HEIGHT + TOTAL_HEIGHT + 8f
        val barTop = cardTop + cardHeight + TEETH_HEIGHT + BAR_GAP
        val toCard = height - cardTop

        fun claim() {
            // Only a bar that is on screen can be peeled; the press goes to the peel itself.
            val now = clock.seconds.value
            if (now < RECEIPT_AT + 0.6f || now >= PEEL_AT) return
            view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
            clock.touch(PEEL_AT, true)
            onClaim?.invoke()
        }

        // The whole screen rises and fades at the end.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val out = easeInOut(seg(t(), FADE_AT, FADE_AT + FADE_IN))
                    alpha = 1f - out
                    translationY = (if (reduced) 0f else -RISE * out) * density
                },
        ) {
            // The check: two ripples that spread once, a glow, a glossy disc and the tick.
            Box(
                Modifier
                    .offset(x = (width / 2f - CHECK_SIZE / 2f).dp, y = (centerY - CHECK_SIZE / 2f).dp)
                    .size(CHECK_SIZE.dp),
            ) {
                Box(
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = seg(t(), POP_AT + 0.1f, POP_AT + 0.1f + RIPPLE_FOR)
                            alpha = if (reduced || p == 0f || p == 1f) 0f else 0.5f * (1f - p)
                            scaleX = 1f + easeOut(p)
                            scaleY = scaleX
                        }
                        .border(3.dp, Green, CircleShape),
                )
                Box(
                    Modifier
                        .padding(16.dp)
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = seg(t(), POP_AT + 0.25f, POP_AT + 0.25f + RIPPLE_FOR * 1.3f)
                            alpha = if (reduced || p == 0f || p == 1f) 0f else 0.35f * (1f - p)
                            scaleX = 1f + 1.3f * easeOut(p)
                            scaleY = scaleX
                        }
                        .background(Color(47, 191, 113).copy(alpha = 0.35f), CircleShape),
                )
                val measure = remember { PathMeasure().apply { setPath(CheckMark, false) } }
                val tick = remember { Path() }
                Canvas(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val now = t()
                            val small = CHECK_SMALL / CHECK_SIZE
                            if (reduced) {
                                alpha = seg(now, POP_AT, POP_AT + REDUCED_FADE)
                                translationY = (smallY - centerY) * density
                                scaleX = small
                            } else {
                                val pop = if (now < POP_AT) 0f else spring(now - POP_AT, POP_STIFFNESS, POP_DAMPING)
                                val move = easeInOut(seg(now, SHRINK_AT, SHRINK_AT + SHRINK_IN))
                                alpha = if (now < POP_AT) 0f else 1f
                                translationY = (smallY - centerY) * move * density
                                scaleX = pop * (1f - (1f - small) * move)
                            }
                            scaleY = scaleX
                        },
                ) {
                    val now = t()
                    val c = CHECK_SIZE / 2f
                    // The tick draws itself on once the circle has popped.
                    val drawn = if (reduced) 1f else easeOut(seg(now, POP_AT + 0.12f, POP_AT + 0.42f))
                    scale(density, density, pivot = Offset.Zero) {
                        drawIntoCanvas { CheckGlow.draw(it.nativeCanvas, 14f, 20f, CHECK_SIZE - 28f, CHECK_SIZE - 28f, c - 14f) }
                        drawCircle(
                            Brush.radialGradient(0f to GreenLight, 0.55f to Green, 1f to GreenDeep, center = Offset(c, 30f), radius = CHECK_SIZE * 0.75f),
                            c - 16f, Offset(c, c),
                        )
                        drawCircle(Color.White.copy(alpha = 0.35f), c - 16.75f, Offset(c, c), style = Stroke(1.5f))
                        drawOval(
                            Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.75f), Color.White.copy(alpha = 0f)), startY = 26f, endY = 56f),
                            Offset(c - 34f, 26f), Size(68f, 30f), alpha = 0.5f,
                        )
                        if (drawn > 0f) {
                            tick.rewind()
                            measure.getSegment(0f, measure.length * drawn, tick, true)
                            val stroke = Stroke(10f, cap = StrokeCap.Round, join = StrokeJoin.Round)
                            translate(0f, 2f) { drawPath(tick, Color(10, 90, 45).copy(alpha = 0.25f), style = stroke) }
                            drawPath(tick, Color.White, style = stroke)
                        }
                    }
                }
            }

            Text(
                title,
                Modifier
                    .offset(y = titleTop.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = easeOut(seg(t(), TITLE_AT, TITLE_AT + TITLE_IN))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * 8f) * density
                    },
                color = Ink, style = style(28f, 34f, FontWeight.ExtraBold, -0.6f), textAlign = TextAlign.Center,
            )
            Text(
                subtitle,
                Modifier
                    .offset(y = (titleTop + 38f).dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = easeOut(seg(t(), TITLE_AT + 0.06f, TITLE_AT + 0.06f + TITLE_IN))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * 8f) * density
                    },
                color = Label, style = style(16f, 22f, FontWeight.Medium), textAlign = TextAlign.Center,
            )

            // The receipt card, which slides up from the bottom of the screen.
            Box(
                Modifier
                    .offset(x = MARGIN.dp, y = cardTop.dp)
                    .width(cardWidth.dp)
                    .graphicsLayer {
                        val now = t()
                        if (reduced) {
                            alpha = seg(now, RECEIPT_AT, RECEIPT_AT + REDUCED_FADE)
                        } else {
                            val into = if (now < RECEIPT_AT) 0f else spring(now - RECEIPT_AT, RECEIPT_STIFFNESS, RECEIPT_DAMPING)
                            alpha = if (now < RECEIPT_AT) 0f else 1f
                            translationY = (1f - into) * toCard * density
                        }
                    },
            ) {
                // The paper, with its shadows and the dashed rule between the punches.
                Canvas(
                    Modifier
                        .offset((-SHADOW_ROOM).dp, (-SHADOW_ROOM).dp)
                        .wrapContentSize(Alignment.TopStart, unbounded = true)
                        .size((cardWidth + SHADOW_ROOM * 2).dp, (cardHeight + TEETH_HEIGHT + SHADOW_ROOM * 2).dp),
                ) {
                    val sheet = paper(cardWidth, cardHeight, punchY)
                    scale(density, density, pivot = Offset.Zero) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val outline = sheet.asAndroidPath()
                            drawIntoCanvas { canvas ->
                                PaperShadows.forEach { shadow ->
                                    canvas.nativeCanvas.save()
                                    canvas.nativeCanvas.translate(0f, shadow.dy)
                                    canvas.nativeCanvas.drawPath(outline, shadow.paint)
                                    canvas.nativeCanvas.restore()
                                }
                            }
                        }
                        drawPath(sheet, Paper)
                        drawLine(
                            Rule,
                            Offset(SHADOW_ROOM + PUNCH + 8f, SHADOW_ROOM + punchY),
                            Offset(SHADOW_ROOM + cardWidth - PUNCH - 8f, SHADOW_ROOM + punchY),
                            strokeWidth = 1.5f,
                            pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 5f)),
                        )
                    }
                }

                Column(Modifier.fillMaxWidth().height(cardHeight.dp).padding(horizontal = PAD.dp)) {
                    Row(Modifier.fillMaxWidth().height(HEAD_HEIGHT.dp), verticalAlignment = Alignment.Bottom) {
                        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(22.dp).background(Green, CircleShape), contentAlignment = Alignment.Center) {
                                Icon(Icons.Outlined.Check, null, Modifier.size(14.dp), tint = Color.White)
                            }
                            Text("RECEIPT", color = ValueInk, style = style(12f, 16f, FontWeight.ExtraBold, 1.6f))
                        }
                        Text("#$reference", color = Label, style = style(12f, 16f, FontWeight.Normal).copy(fontFamily = FontFamily.Monospace))
                    }
                    Column(
                        Modifier.fillMaxWidth().height(AMOUNT_HEIGHT.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Text("Amount paid", color = Label, style = style(13f, 18f, FontWeight.SemiBold, 0.2f))
                        Row(Modifier.padding(top = 2.dp)) {
                            Text(currency, Modifier.padding(top = 7.dp, end = 3.dp), color = Label, style = style(20f, 24f, FontWeight.Bold))
                            Text(money(counted), color = Ink, style = style(46f, 52f, FontWeight.ExtraBold, -1.4f).copy(fontFeatureSettings = "tnum"))
                        }
                    }
                    Spacer(Modifier.height(RULE_HEIGHT.dp))
                    rows.forEachIndexed { i, row -> ReceiptRow(row, i, t, reduced) }
                    Box(Modifier.fillMaxWidth().height(RULE_HEIGHT.dp), contentAlignment = Alignment.Center) {
                        Box(Modifier.fillMaxWidth().height(1.dp).background(Rule))
                    }
                    ReceiptRow(PaymentCheckBurstRow("Fee", fee), rows.size, t, reduced)
                }

                Box(Modifier.offset(y = (cardHeight + TEETH_HEIGHT + BAR_GAP).dp).fillMaxWidth().height(BAR_HEIGHT.dp)) {
                    // The reward under the bar.
                    Row(
                        Modifier
                            .fillMaxSize()
                            .clip(RoundedCornerShape(16.dp))
                            .background(Brush.linearGradient(listOf(Color(0xFFFBFBFC), Color(0xFFE4E5E8), Color(0xFFF5F5F7))))
                            .border(1.dp, Color(0xFFDCDDE1), RoundedCornerShape(16.dp)),
                        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            Modifier
                                .size(26.dp)
                                .drawBehind {
                                    drawCircle(
                                        Brush.linearGradient(
                                            listOf(Color.White, Color(0xFFC9CACF), Color(0xFF8E9096)),
                                            start = Offset(0.2f * size.width, 0f),
                                            end = Offset(0.8f * size.width, size.height),
                                        ),
                                    )
                                }
                                .border(1.dp, Color(0xFF9FA1A7), CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.Star, null, Modifier.size(13.dp), tint = Color.White)
                        }
                        Text(reward, color = RewardInk, style = style(19f, 24f, FontWeight.ExtraBold, -0.3f))
                    }
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(18.dp)
                            .clip(RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp))
                            .graphicsLayer {
                                val p = easeInOut(seg(t(), PEEL_AT, PEEL_AT + PEEL_FOR))
                                alpha = 0.28f * sin(PI.toFloat() * min(1f, p * 1.1f))
                            }
                            .background(Color.Black),
                    )
                    // The bar itself: a flap hinged on its top edge, swinging up and away; it is gone once edge-on.
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                val p = easeInOut(seg(t(), PEEL_AT, PEEL_AT + PEEL_FOR))
                                alpha = if (reduced) 1f - seg(t(), PEEL_AT, PEEL_AT + REDUCED_FADE) else if (p >= 0.98f) 0f else 1f
                                compositingStrategy = CompositingStrategy.ModulateAlpha
                            }
                            .drawWithContent {
                                val p = easeInOut(seg(t(), PEEL_AT, PEEL_AT + PEEL_FOR))
                                if (reduced || p == 0f) {
                                    drawContent()
                                } else {
                                    // rotateX(angle) about the top edge, seen through a perspective of 500: on the
                                    // card's plane that is a vertical squash and a widening towards the bottom.
                                    val angle = p * 88f * PI.toFloat() / 180f
                                    val m = Matrix()
                                    m.values[Matrix.ScaleY] = cos(angle)
                                    m.values[Matrix.Perspective1] = -sin(angle) / (FLAP_PERSPECTIVE * density)
                                    withTransform({
                                        translate(size.width / 2f, 0f)
                                        transform(m)
                                        translate(-size.width / 2f, 0f)
                                    }) { this@drawWithContent.drawContent() }
                                }
                            }
                            .dropShadow(RoundedCornerShape(16.dp), Shadow(radius = 10.dp, color = Color(17, 24, 39).copy(alpha = 0.12f), offset = DpOffset(0.dp, 4.dp)))
                            .clip(RoundedCornerShape(16.dp))
                            .clickable(interactionSource = null, indication = null, role = Role.Button, onClickLabel = claimLabel) { claim() }
                            .background(
                                Brush.linearGradient(
                                    0f to Color(0xFFF6F6F8), 0.3f to Color(0xFFCFD0D5), 0.5f to Color(0xFFECEEF1), 0.78f to Color(0xFFB4B6BC), 1f to Color(0xFFDCDDE1),
                                ),
                            ),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Outlined.CardGiftcard, null, Modifier.size(18.dp), tint = SilverInk)
                            Text(claimLabel, color = SilverInk, style = style(16f, 20f, FontWeight.SemiBold))
                        }
                        // A band of light leaning across the bar, skewed about its own middle.
                        Canvas(Modifier.fillMaxSize()) {
                            val sheen = seg(t(), SHEEN_AT, SHEEN_AT + SHEEN_FOR)
                            if (reduced || sheen == 0f || sheen == 1f) return@Canvas
                            val lean = Matrix().apply { values[Matrix.SkewX] = tan(-22f * PI.toFloat() / 180f) }
                            scale(density, density, pivot = Offset.Zero) {
                                withTransform({
                                    translate(-90f + (cardWidth + 180f) * sheen + 27f, BAR_HEIGHT / 2f)
                                    transform(lean)
                                }) {
                                    drawRect(Color.White.copy(alpha = 0.75f * sin(PI.toFloat() * sheen)), Offset(-27f, -BAR_HEIGHT / 2f - 10f), Size(54f, BAR_HEIGHT + 20f))
                                }
                            }
                        }
                    }
                }
            }

            Coins(clock, COINS_AT, width / 2f, barTop + BAR_HEIGHT / 2f, Modifier.fillMaxSize())
        }

        Confetti(clock, POP_AT, Modifier.fillMaxSize())
    }
}

@Composable
private fun ReceiptRow(row: PaymentCheckBurstRow, index: Int, t: () -> Float, reduced: Boolean) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .graphicsLayer {
                val at = RECEIPT_AT + 0.5f + index * ROW_STAGGER
                val p = easeOut(seg(t(), at, at + 0.3f))
                alpha = p
                translationY = (if (reduced) 0f else (1f - p) * 8f) * density
            },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(row.label, Modifier.weight(1f), color = Label, style = style(15f, 20f, FontWeight.Medium))
        Text(row.value, color = ValueInk, style = style(15f, 20f, FontWeight.SemiBold).copy(fontFeatureSettings = "tnum"))
    }
}
