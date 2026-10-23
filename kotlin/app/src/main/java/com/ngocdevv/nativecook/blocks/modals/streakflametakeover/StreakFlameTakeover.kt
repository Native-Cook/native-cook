package com.ngocdevv.nativecook.blocks.modals.streakflametakeover

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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Page = Color(0xFF0B0D10)
private val CardColor = Color(0xFF14171C)
private val CardLine = Color(0xFF2A2E36)
private val Day = Color(0xFF5C6270)
private val Orange = Color(0xFFFF7A18)
private val Blue = Color(0xFF1CB0F6)

// The count goes from this faint white to this gold.
private val CountGhost = Color(1f, 1f, 1f, 0.1f)
private val CountGold = Color(0xFFFFD84A)
private val CountGlow = Color(255, 150, 0)

private const val COIN_WIDTH = 84f
private const val COIN_HEIGHT = 58f
private const val COUNT_SIZE = 88f
private const val COUNT_LINE = 98f
private const val MARGIN = 20f
private const val CARD_HEIGHT = 112f
private const val TRACK = 12f
private const val BUTTON = 56f
private const val GLOW = 460f
private val DAYS = listOf("Su", "Mo", "Tu", "We", "Th", "Fr", "Sa")

private class Spark(val dx: Float, val dy: Float, val size: Float, val color: Color)

/** Fourteen sparks leave the flame in a ring, 80 to 130 points out. */
private val SPARKS = List(14) { i ->
    val angle = i / 14f * TAU + 0.3f
    val reach = 80f + (i * 37) % 50
    Spark(cos(angle) * reach, sin(angle) * reach, 5f + (i % 3) * 3f, if (i % 3 == 1) Color(0xFFFF7A18) else Color(0xFFFFD23A))
}

/** The glow riding the head of the week's fill: React Native's `0 0 16px 4px`, a sigma of 8 round a disc 4 dp larger. */
private val FillGlow = SoftRoundRect(Color(255, 170, 0).copy(alpha = 0.8f), 8f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Float, lineHeight: Float, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = letterSpacing.sp, lineHeightStyle = Centered,
)

private val CountStyle = style(COUNT_SIZE, COUNT_LINE, FontWeight.Black, -2f).copy(fontFeatureSettings = "tnum")

/** A straight mix of two colours, alpha included, like React Native's `interpolateColor` with `gamma: 1`. */
private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
    a.alpha + (b.alpha - a.alpha) * t,
)

/**
 * A streak screen: a gold coin spins, pops into a flame mascot, and the day count rolls up by one.
 * The flame lifts, a week bar fills and the buttons slide up; after a pause everything slides away
 * and the coin comes back. One clock drives all of it, so a touch and autoPlay run the same motion.
 *
 * @param days the streak before today; the count rolls up to the next number.
 * @param weekDays how many days of the week the bar fills, 0 to 7.
 * @param weekLabel the heading inside the week card.
 * @param autoPlay plays by itself every [LOOP] seconds and ends on the coin again.
 * @param onContinue called when Continue is pressed by a touch.
 */
@Composable
fun StreakFlameTakeover(
    modifier: Modifier = Modifier,
    days: Int = 1824,
    weekDays: Int = 2,
    streakLabel: String = "day streak",
    weekLabel: String = "This week",
    shareLabel: String = "SHARE MILESTONE",
    continueLabel: String = "CONTINUE",
    autoPlay: Boolean = false,
    onShare: (() -> Unit)? = null,
    onContinue: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = rememberLoopClock(autoPlay)
    val t = { clock.seconds.value }
    val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
    val measurer = rememberTextMeasurer()

    /** How far the flame, count and label have lifted, and how far they have slid away at the end. */
    fun lift(now: Float): Float {
        if (now >= BACK_AT || reduced) return 0f
        val up = easeInOut(seg(now, LIFT_AT, LIFT_AT + LIFT_IN)) * -LIFT
        return up + easeInOut(seg(now, OUT_AT, OUT_AT + OUT_IN)) * (OUT_SLIDE + LIFT)
    }

    /** 1 while the screen is up, fading to 0 at the end; 0 again until the resting count returns. */
    fun present(now: Float) = if (now >= BACK_AT) 0f else 1f - seg(now, OUT_AT, OUT_AT + OUT_IN)

    /** A button's fade and the points it is still below its place, sliding in and then away. */
    fun Modifier.buttonSlide(index: Int): Modifier = graphicsLayer {
        val now = t()
        val at = BUTTONS_AT + index * BUTTON_STAGGER
        val p = easeOut(seg(now, at, at + BUTTON_IN))
        val away = if (now >= BACK_AT) 0f else easeInOut(seg(now, OUT_AT, OUT_AT + OUT_IN)) * OUT_SLIDE
        alpha = p * present(now)
        translationY = (if (reduced) 0f else (1f - p) * BUTTON_SLIDE + away) * density
        compositingStrategy = CompositingStrategy.ModulateAlpha
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Page)
            .semantics { contentDescription = "${days + 1} $streakLabel" }
            .clickable(interactionSource = null, indication = null) { clock.touch(TAP_AT, false) },
    ) {
        val width = maxWidth.value
        val height = maxHeight.value
        val flameY = max(insetTop + 150f, height * 0.33f)
        val countTop = flameY + FLAME_SIZE / 2f + 6f
        val labelTop = countTop + COUNT_LINE - 6f
        val cardTop = labelTop + 52f - LIFT
        val cardWidth = width - MARGIN * 2f
        val trackWidth = cardWidth - 32f
        val bottom = insetBottom + 8f

        val head = (days + 1).toString().dropLast(1)
        val from = days.toString().takeLast(1)
        val to = ((days + 1) % 10).toString()
        val headText = remember(head, measurer) { measurer.measure(head, CountStyle) }
        val fromText = remember(from, measurer) { measurer.measure(from, CountStyle) }
        val toText = remember(to, measurer) { measurer.measure(to, CountStyle) }

        // The coin, spinning, over a faint gold shadow that stays.
        Box(Modifier.offset(x = (width / 2f - COIN_WIDTH / 2f).dp, y = (flameY - COIN_HEIGHT / 2f).dp).size(COIN_WIDTH.dp, COIN_HEIGHT.dp)) {
            Box(
                Modifier
                    .align(Alignment.TopCenter)
                    .offset(y = (COIN_HEIGHT - 6f).dp)
                    .wrapContentSize(unbounded = true)
                    .size((COIN_WIDTH + 20f).dp, 18.dp)
                    .background(Color(255, 200, 0).copy(alpha = 0.1f), RoundedCornerShape(9.dp)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val now = t()
                        val gone = seg(now, POP_AT, POP_AT + COIN_OUT_IN)
                        val back = seg(now, BACK_AT, BACK_AT + BACK_IN)
                        val spin = if (reduced) 1f else max(0.08f, abs(cos(now / COIN_PERIOD * TAU)))
                        alpha = if (now < POP_AT) 1f else if (now >= BACK_AT) back else 1f - gone
                        scaleX = spin * (1f - 0.4f * gone)
                        scaleY = 1f - 0.4f * gone
                    }
                    .clip(CircleShape)
                    .background(Brush.verticalGradient(0f to Color(0xFFFFE066), 0.55f to Color(0xFFFFBF00), 1f to Color(0xFFE08A00)))
                    .border(3.dp, Color(0xFFF2A100), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Box(Modifier.size(46.dp, 32.dp).background(Color(255, 244, 190).copy(alpha = 0.55f), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(24.dp), tint = Color(214, 120, 0).copy(alpha = 0.9f))
                }
            }
        }

        // A warm glow comes up behind the flame as it pops, and follows it up.
        Canvas(
            Modifier
                .offset(x = (width / 2f - GLOW / 2f).dp, y = (flameY - GLOW / 2f).dp)
                .size(GLOW.dp)
                .graphicsLayer {
                    val now = t()
                    val p = seg(now, POP_AT, POP_AT + 0.5f)
                    alpha = p * present(now)
                    translationY = lift(now) * density
                    scaleX = if (reduced) 1f else 0.6f + 0.4f * easeOut(p)
                    scaleY = scaleX
                },
        ) {
            drawCircle(
                Brush.radialGradient(
                    0f to Color(255, 170, 0).copy(alpha = 0.34f), 0.45f to Color(255, 120, 0).copy(alpha = 0.1f), 1f to Color(255, 120, 0).copy(alpha = 0f),
                    center = center, radius = size.width / 2f,
                ),
            )
        }

        Wings(
            Modifier
                .offset(x = (width / 2f - WINGS_WIDTH / 2f).dp, y = (flameY - WINGS_HEIGHT / 2f - 20f).dp)
                .size(WINGS_WIDTH.dp, WINGS_HEIGHT.dp)
                .graphicsLayer {
                    val p = seg(t(), POP_AT + 0.05f, POP_AT + 0.05f + WINGS_FOR)
                    alpha = if (reduced || p == 0f || p == 1f) 0f else 1f - p * 0.3f
                    scaleX = 0.7f + 0.3f * easeOut(p)
                    scaleY = 0.8f + 0.2f * sin(p * PI.toFloat() * 3f)
                },
        )

        Flame(
            glint = { seg(t(), GLINT_AT, GLINT_AT + GLINT_FOR) },
            // The sparkle by the shades flashes with each glint pass.
            sparkle = {
                val now = t()
                max(sin(PI.toFloat() * seg(now, POP_AT + 0.3f, POP_AT + 0.9f)), sin(PI.toFloat() * seg(now, GLINT_AT + 0.2f, GLINT_AT + 0.8f)))
            },
            modifier = Modifier
                .offset(x = (width / 2f - FLAME_SIZE / 2f).dp, y = (flameY - FLAME_SIZE / 2f).dp)
                .size(FLAME_SIZE.dp)
                .graphicsLayer {
                    val now = t()
                    val gone = present(now)
                    if (now < POP_AT || gone == 0f) {
                        alpha = 0f
                    } else if (reduced) {
                        alpha = min(seg(now, POP_AT, POP_AT + REDUCED_FADE), gone)
                    } else {
                        val pop = POP_FROM + (1f - POP_FROM) * spring(now - POP_AT, POP_STIFFNESS, POP_DAMPING)
                        val breathe = BREATHE * (1f - cos(now / BREATHE_PERIOD * TAU)) / 2f * seg(now, LIFT_AT, LIFT_AT + LIFT_IN)
                        alpha = gone
                        translationY = lift(now) * density
                        scaleX = pop * (1f + breathe)
                        scaleY = scaleX
                    }
                },
        )

        // Sparks leave the flame in a ring as it pops.
        Canvas(Modifier.fillMaxSize()) {
            val p = seg(t(), POP_AT + 0.1f, POP_AT + 0.1f + SPARKS_FOR)
            if (p == 0f || p == 1f) return@Canvas
            scale(density, density, pivot = Offset.Zero) {
                for (spark in SPARKS) {
                    drawCircle(
                        spark.color, spark.size / 2f * (1f - 0.6f * p),
                        Offset(width / 2f + spark.dx * easeOut(p), flameY + spark.dy * easeOut(p)),
                        alpha = 1f - p * p,
                    )
                }
            }
        }

        // The day count: the last digit rolls up to the next one in a window one line tall.
        Canvas(
            Modifier
                .offset(y = countTop.dp)
                .fillMaxWidth()
                .height(COUNT_LINE.dp)
                .graphicsLayer {
                    val now = t()
                    alpha = if (now >= BACK_AT) seg(now, BACK_AT, BACK_AT + BACK_IN) else present(now)
                    translationY = lift(now) * density
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                },
        ) {
            val now = t()
            val color = mix(CountGhost, CountGold, if (now >= BACK_AT) 0f else seg(now, BRIGHTEN_AT, BRIGHTEN_AT + BRIGHTEN_FOR))
            // The glow fades with the text, as a text shadow does on iOS.
            val glow = androidx.compose.ui.graphics.Shadow(CountGlow.copy(alpha = 0.45f * color.alpha), Offset.Zero, 18.dp.toPx())
            val roll = if (now >= BACK_AT) 0f else if (reduced) seg(now, ROLL_AT, ROLL_AT + REDUCED_FADE) else easeInOut(seg(now, ROLL_AT, ROLL_AT + ROLL_FOR))
            val window = max(fromText.size.width, toText.size.width).toFloat()
            val left = (size.width - headText.size.width - window) / 2f
            val line = COUNT_LINE.dp.toPx()
            drawText(headText, color, Offset(left, 0f), shadow = glow)
            clipRect(left + headText.size.width - line, 0f, left + headText.size.width + window + line, line) {
                drawText(fromText, color, Offset(left + headText.size.width, -line * roll), shadow = glow)
                drawText(toText, color, Offset(left + headText.size.width, line - line * roll), shadow = glow)
            }
        }
        Text(
            streakLabel,
            Modifier
                .offset(y = labelTop.dp)
                .fillMaxWidth()
                .graphicsLayer {
                    val now = t()
                    alpha = seg(now, LABEL_AT, LABEL_AT + LABEL_IN) * present(now)
                    translationY = lift(now) * density
                },
            color = Color.White, style = style(22f, 28f, FontWeight.ExtraBold), textAlign = TextAlign.Center,
        )

        // The week card: its heading, the day names and a track that fills.
        Column(
            Modifier
                .offset(x = MARGIN.dp, y = cardTop.dp)
                .size(cardWidth.dp, CARD_HEIGHT.dp)
                .graphicsLayer {
                    val now = t()
                    val p = easeOut(seg(now, CARD_AT, CARD_AT + CARD_IN))
                    val out = easeInOut(seg(now, OUT_AT, OUT_AT + OUT_IN))
                    alpha = p * present(now)
                    translationY = (if (reduced) 0f else (1f - p) * CARD_SLIDE + (if (now >= BACK_AT) 0f else out * OUT_SLIDE)) * density
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                }
                .dropShadow(RoundedCornerShape(22.dp), Shadow(radius = 40.dp, color = Color.Black.copy(alpha = 0.45f), offset = DpOffset(0.dp, 18.dp)))
                .background(CardColor, RoundedCornerShape(22.dp))
                // A faint light along the inside of the top edge.
                .drawWithContent {
                    drawContent()
                    clipRect(bottom = 3.dp.toPx()) {
                        val inset = 1.5.dp.toPx()
                        drawRoundRect(
                            Color.White.copy(alpha = 0.05f), Offset(inset, inset), Size(size.width - inset * 2, size.height - inset * 2),
                            CornerRadius(22.dp.toPx() - inset), style = Stroke(1.dp.toPx()),
                        )
                    }
                }
                .border(1.dp, CardLine, RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterVertically),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(weekLabel, Modifier.weight(1f), color = Color.White, style = style(16f, 20f, FontWeight.ExtraBold))
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.LocalFireDepartment, null, Modifier.size(15.dp), tint = Orange)
                    Text("$weekDays of 7", color = Color(0xFF9AA1AE), style = style(14f, 18f, FontWeight.Bold))
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                DAYS.forEachIndexed { i, day ->
                    Text(
                        day, Modifier.width(30.dp),
                        color = if (i == weekDays - 1) Orange else Day, style = style(13f, 16f, FontWeight.ExtraBold), textAlign = TextAlign.Center,
                    )
                }
            }
            Canvas(Modifier.width(trackWidth.dp).height(TRACK.dp)) {
                val now = t()
                val fillP = easeInOut(seg(now, FILL_AT, FILL_AT + FILL_FOR))
                val glowP = seg(now, FILL_AT, FILL_AT + FILL_FOR + 0.4f)
                val filled = trackWidth * weekDays / 7f
                scale(density, density, pivot = Offset.Zero) {
                    drawRoundRect(Color(0xFF1F232A), size = Size(trackWidth, TRACK), cornerRadius = CornerRadius(TRACK / 2f))
                    val w = filled * fillP
                    if (w > 0f) {
                        drawRoundRect(
                            Brush.horizontalGradient(listOf(Orange, Color(0xFFFFC800)), startX = 0f, endX = w),
                            size = Size(w, TRACK), cornerRadius = CornerRadius(min(TRACK / 2f, w / 2f)),
                        )
                    }
                    // The glow rides the head of the fill and fades out once it has arrived.
                    val a = 0.9f * sin(PI.toFloat() * glowP)
                    if (a > 0f) {
                        val gx = -9f + filled * easeInOut(min(1f, glowP * 1.7f))
                        drawIntoCanvas { FillGlow.draw(it.nativeCanvas, gx - 4f, -5f - 4f, 30f, 30f, 15f, a) }
                        drawCircle(Color(0xFFFFC23A), 11f, Offset(gx + 11f, -5f + 11f), alpha = a)
                    }
                }
            }
        }

        // Share, a raised blue button with a darker foot, and Continue under it.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = (bottom + 52f).dp)
                .fillMaxWidth()
                .height(BUTTON.dp)
                .buttonSlide(0)
                .dropShadow(RoundedCornerShape(16.dp), Shadow(radius = 24.dp, color = Color(28, 176, 246).copy(alpha = 0.25f), offset = DpOffset(0.dp, 10.dp)))
                .background(Color(0xFF1593D0), RoundedCornerShape(16.dp))
                .clickable(interactionSource = null, indication = null, role = Role.Button) { onShare?.invoke() },
        ) {
            Row(
                Modifier.fillMaxSize().padding(bottom = 4.dp).background(Blue, RoundedCornerShape(16.dp)),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.IosShare, null, Modifier.size(20.dp), tint = Color.White)
                Text(shareLabel, color = Color.White, style = style(16f, 20f, FontWeight.Black, 0.8f))
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = bottom.dp)
                .fillMaxWidth()
                .height(44.dp)
                .buttonSlide(1)
                .clickable(interactionSource = null, indication = null, role = Role.Button) {
                    view.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
                    clock.touch(OUT_AT, true)
                    onContinue?.invoke()
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(continueLabel, color = Blue, style = style(16f, 20f, FontWeight.Black, 0.8f))
        }
    }
}
