package com.ngocdevv.nativecook.blocks.modals.bannergrowcard

import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Ink = Color.White
private val Mute = Color.White.copy(alpha = 0.55f)
private val Line = Color.White.copy(alpha = 0.32f)
private val Cream = Color(255, 240, 225).copy(alpha = 0.78f)
private val BannerColor = Color(0xFF1C1C1E)
private val Warm = Color(0xFF6B4224)

private const val OUTLINE_WIDTH = 272f
private const val OUTLINE_HEIGHT = 424f
private const val OUTLINE_RADIUS = 46f
private const val OUTLINE_STROKE = 1.5f

/** Room around the outline for the side buttons. */
private const val PAD = 4f
private const val O = PAD + OUTLINE_STROKE / 2f
private const val NOTCH_WIDTH = 64f
private const val NOTCH_HEIGHT = 9f

/** The banner starts this far below the top of the outline, and the card ends at its bottom edge. */
private const val INSET = (OUTLINE_WIDTH - SCENE_WIDTH) / 2f
private const val BANNER_TOP = 44f
private const val BANNER_WIDTH = SCENE_WIDTH
private const val BANNER_HEIGHT = 72f
private const val BANNER_RADIUS = 18f
private const val CARD_RADIUS = OUTLINE_RADIUS - INSET
private const val CARD_HEIGHT = SCENE_HEIGHT
private const val ICON = 40f
private const val BUTTON_HEIGHT = 56f
private const val COPY_HEIGHT = 112f

/** The phone's outline, open at the bottom: up the left side, over the top, down the right. */
private val OutlinePath = Path().apply {
    val r = OUTLINE_RADIUS
    moveTo(O, OUTLINE_HEIGHT)
    lineTo(O, r + O)
    arcTo(Rect(O, O, O + 2 * r, O + 2 * r), 180f, 90f, false)
    lineTo(OUTLINE_WIDTH - r + O, O)
    arcTo(Rect(OUTLINE_WIDTH - 2 * r + O, O, OUTLINE_WIDTH + O, O + 2 * r), 270f, 90f, false)
    lineTo(OUTLINE_WIDTH + O, OUTLINE_HEIGHT)
}

/** The phone's buttons: action and volume on the left, power on the right. */
private val ButtonsPath = Path().apply {
    for ((x, from, to) in listOf(Triple(O - 3f, 92f, 112f), Triple(O - 3f, 132f, 168f), Triple(O - 3f, 180f, 216f), Triple(OUTLINE_WIDTH + O + 3f, 150f, 214f))) {
        moveTo(x, from)
        lineTo(x, to)
    }
}

/** Warm light spilling round the card: a wide low glow and a close one. */
private val GlowWide = SoftRoundRect(Color(226, 132, 62).copy(alpha = 0.34f), 35f)
private val GlowClose = SoftRoundRect(Color(240, 150, 70).copy(alpha = 0.22f), 14f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun style(size: Float, lineHeight: Float, weight: FontWeight = FontWeight.Normal, letterSpacing: Float = 0f) = TextStyle(
    fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = letterSpacing.sp, lineHeightStyle = Centered,
)

/** A straight mix of two colours, with no gamma, like React Native's `interpolateColor` with `gamma: 1`. */
private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
)

/**
 * An onboarding screen whose notification preview is drawn inside a phone outline. The outline
 * draws on, a notification slides in and warms, and then it grows into a full photo card that
 * the outline clips. The clock drives all of it, so a touch and autoPlay run the same motion.
 *
 * @param notificationTitle the notification the banner previews, with [notificationBody].
 * @param cardKicker the caption of the grown card, with [cardTitle] and [cardMeta].
 * @param autoPlay grows the banner into the card by itself every [LOOP] seconds and shrinks it back.
 * @param onEnable called when the button is pressed by a touch.
 */
@Composable
fun BannerGrowCard(
    modifier: Modifier = Modifier,
    wordmark: String = "amber",
    skipLabel: String = "Skip",
    title: String = "Build your practice",
    subtitle: String = "Get daily motivation and gentle reminders to sit, breathe and slow down.",
    buttonLabel: String = "Enable notifications",
    notificationTitle: String = "Time for your morning sit",
    notificationBody: String = "Ten quiet minutes to begin.",
    cardKicker: String = "TODAY'S PRACTICE",
    cardTitle: String = "Morning sit",
    cardMeta: String = "10 min · Breathwork",
    autoPlay: Boolean = false,
    onEnable: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = rememberLoopClock(autoPlay)
    val t = { clock.seconds.value }
    val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value

    /** 0 = banner, 1 = full card. */
    fun grown(now: Float): Float {
        if (reduced) return if (now >= GROW_AT && now < COLLAPSE_AT) 1f else 0f
        if (now < COLLAPSE_AT) return max(0f, spring(now - GROW_AT, GROW_STIFFNESS, GROW_DAMPING))
        return 1f - easeInOut(seg(now, COLLAPSE_AT, COLLAPSE_AT + COLLAPSE_IN))
    }

    /** 1 while the banner is on screen, with its slide-in and fade. */
    fun bannerIn(now: Float) =
        seg(now, BANNER_AT, BANNER_AT + (if (reduced) REDUCED_FADE else BANNER_IN)) * (1f - seg(now, BANNER_OUT_AT, BANNER_OUT_AT + BANNER_OUT_IN))

    fun warmth(now: Float) = seg(now, WARM_AT, WARM_AT + WARM_IN) * (1f - seg(now, COLLAPSE_AT, COLLAPSE_AT + COLLAPSE_IN))

    fun cardHeight(g: Float) = BANNER_HEIGHT + (CARD_HEIGHT - BANNER_HEIGHT) * g

    BoxWithConstraints(modifier.fillMaxSize().background(Color.Black)) {
        val width = maxWidth.value
        val height = maxHeight.value
        // From the bottom up: the button, the copy, then the outline whose open foot sits above the copy.
        val buttonBottom = insetBottom + 12f
        val copyBottom = buttonBottom + BUTTON_HEIGHT + 36f
        val outlineTop = max(insetTop + 64f, height - copyBottom - COPY_HEIGHT - 28f - OUTLINE_HEIGHT)
        val outlineLeft = (width - OUTLINE_WIDTH) / 2f - PAD
        val cardLeft = (width - BANNER_WIDTH) / 2f
        val cardTop = outlineTop + PAD + BANNER_TOP

        // The wordmark and the skip link.
        Box(Modifier.offset(y = (insetTop + 14f).dp).fillMaxWidth().height(24.dp), contentAlignment = Alignment.Center) {
            Text(
                wordmark, Modifier.padding(start = 8.dp),
                color = Ink, style = style(16f, 20f, letterSpacing = 8f).copy(fontFamily = FontFamily.Monospace), textAlign = TextAlign.Center,
            )
            Row(
                Modifier.align(Alignment.CenterEnd).padding(end = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                val dim = Color.White.copy(alpha = 0.72f)
                Text(skipLabel, color = dim, style = style(15f, 20f, FontWeight.Medium))
                Icon(Icons.AutoMirrored.Filled.ArrowForward, null, Modifier.size(15.dp), tint = dim)
            }
        }

        // Warm light spilling round the card once it is a photo.
        Canvas(Modifier.offset(x = cardLeft.dp, y = cardTop.dp).size(BANNER_WIDTH.dp, CARD_HEIGHT.dp)) {
            val now = t()
            val g = min(1f, grown(now))
            val alpha = g * bannerIn(now)
            if (alpha <= 0f) return@Canvas
            val h = cardHeight(g)
            scale(density, density, pivot = Offset.Zero) {
                drawIntoCanvas {
                    GlowWide.draw(it.nativeCanvas, -2f, 16f - 2f, BANNER_WIDTH + 4f, h + 4f, CARD_RADIUS + 2f, alpha)
                    GlowClose.draw(it.nativeCanvas, 0f, 0f, BANNER_WIDTH, h, CARD_RADIUS, alpha)
                }
                drawRoundRect(Color(0xFF8A4C22), size = Size(BANNER_WIDTH, h), cornerRadius = CornerRadius(CARD_RADIUS), alpha = alpha)
            }
        }

        // The outline, drawn on and off, with its buttons and notch.
        val measure = remember { PathMeasure().apply { setPath(OutlinePath, false) } }
        val segment = remember { Path() }
        Canvas(
            Modifier
                .offset(x = outlineLeft.dp, y = outlineTop.dp)
                .size((OUTLINE_WIDTH + PAD * 2).dp, OUTLINE_HEIGHT.dp)
                .graphicsLayer {
                    val now = t()
                    alpha = if (reduced) seg(now, OUTLINE_AT, OUTLINE_AT + REDUCED_FADE) * (1f - seg(now, OUTLINE_OUT_AT, OUTLINE_OUT_AT + REDUCED_FADE)) else 1f
                },
        ) {
            val now = t()
            val end = if (reduced) 1f else easeInOut(seg(now, OUTLINE_AT, OUTLINE_AT + OUTLINE_IN)) * (1f - easeInOut(seg(now, OUTLINE_OUT_AT, OUTLINE_OUT_AT + OUTLINE_OUT_IN)))
            scale(density, density, pivot = Offset.Zero) {
                if (end > 0f) {
                    segment.rewind()
                    measure.getSegment(0f, measure.length * end, segment, true)
                    drawPath(segment, Line, style = Stroke(OUTLINE_STROKE, cap = StrokeCap.Round))
                }
                drawPath(ButtonsPath, Line, alpha = seg(end, 0.75f, 1f), style = Stroke(2.5f, cap = StrokeCap.Round))
                val notch = 1.5f
                drawRoundRect(
                    Line,
                    Offset((OUTLINE_WIDTH + PAD * 2 - NOTCH_WIDTH) / 2f + notch / 2f, PAD + 14f + notch / 2f),
                    Size(NOTCH_WIDTH - notch, NOTCH_HEIGHT - notch),
                    CornerRadius((NOTCH_HEIGHT - notch) / 2f),
                    alpha = seg(end, 0.4f, 0.7f),
                    style = Stroke(notch),
                )
            }
        }

        // The card: a banner that grows into the photo. It is laid out at full height and clipped.
        Box(
            Modifier
                .offset(x = cardLeft.dp, y = cardTop.dp)
                .size(BANNER_WIDTH.dp, CARD_HEIGHT.dp)
                .graphicsLayer {
                    val now = t()
                    alpha = bannerIn(now)
                    translationY = (if (reduced) 0f else (1f - easeOut(seg(now, BANNER_AT, BANNER_AT + BANNER_IN))) * -14f) * density
                }
                .drawWithContent {
                    val now = t()
                    val g = grown(now)
                    val top = CornerRadius((BANNER_RADIUS + (CARD_RADIUS - BANNER_RADIUS) * min(1f, g)) * density)
                    val bottom = CornerRadius(BANNER_RADIUS * (1f - seg(g, 0.6f, 1f)) * density)
                    val h = cardHeight(g) * density
                    val shape = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, h, top, top, bottom, bottom)) }
                    clipPath(shape) {
                        drawRect(mix(BannerColor, Warm, max(warmth(now), min(g, 1f))))
                        this@drawWithContent.drawContent()
                    }
                    // React Native draws the 1 dp border inside the box.
                    val inset = density / 2f
                    val edge = Path().apply {
                        addRoundRect(
                            RoundRect(
                                inset, inset, size.width - inset, h - inset,
                                CornerRadius(max(0f, top.x - inset)), CornerRadius(max(0f, top.x - inset)),
                                CornerRadius(max(0f, bottom.x - inset)), CornerRadius(max(0f, bottom.x - inset)),
                            ),
                        )
                    }
                    drawPath(edge, Color.White.copy(alpha = 0.14f * (1f - seg(g, 0f, 0.3f))), style = Stroke(density))
                },
        ) {
            // Children sit inside the 1 dp border, as React Native lays them out.
            Scene(
                Modifier
                    .offset(1.dp, 1.dp)
                    .graphicsLayer {
                        val now = t()
                        val g = min(grown(now), 1f)
                        val breathe = if (reduced) 0f else sin(PI.toFloat() * seg(now, BREATHE_AT, BREATHE_AT + BREATHE_FOR)) * BREATHE
                        alpha = max(PEEK * warmth(now), g)
                        scaleX = 1.04f + breathe - 0.04f * g
                        scaleY = scaleX
                    },
            )
            // The notification leaves as the card grows and comes back as it shrinks.
            Row(
                Modifier
                    .offset(1.dp, 1.dp)
                    .size(BANNER_WIDTH.dp, BANNER_HEIGHT.dp)
                    .graphicsLayer {
                        val now = t()
                        val out = if (now < COLLAPSE_AT) {
                            seg(now, LINES_OUT_AT, LINES_OUT_AT + LINES_OUT_IN)
                        } else {
                            1f - seg(now, COLLAPSE_AT + COLLAPSE_IN * 0.5f, COLLAPSE_AT + COLLAPSE_IN)
                        }
                        alpha = 1f - out
                    }
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(11.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier
                        .size(ICON.dp)
                        .graphicsLayer {
                            scaleX = if (reduced) 1f else 0.6f + 0.4f * spring(t() - BANNER_AT - 0.1f, 300f, 18f)
                            scaleY = scaleX
                        }
                        .clip(RoundedCornerShape(10.dp))
                        .background(Brush.verticalGradient(listOf(Color(0xFFF6B25C), Color(0xFFC8692A)))),
                    contentAlignment = Alignment.BottomCenter,
                ) {
                    Box(Modifier.align(Alignment.TopCenter).padding(top = 11.dp).size(16.dp).background(Color(0xFFFFF4E2), CircleShape))
                    Box(Modifier.fillMaxWidth().height(13.dp).background(Color(80, 32, 8).copy(alpha = 0.55f)))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(1.dp)) {
                    NotificationLine(0, t, reduced) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("AMBER", Modifier.weight(1f), color = Mute, style = style(11f, 14f, FontWeight.SemiBold, 0.8f))
                            Text("now", color = Mute, style = style(11f, 14f))
                        }
                    }
                    NotificationLine(1, t, reduced) {
                        Text(notificationTitle, color = Ink, style = style(14f, 18f, FontWeight.SemiBold, -0.2f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    NotificationLine(2, t, reduced) {
                        Text(notificationBody, color = Color.White.copy(alpha = 0.72f), style = style(13f, 17f), maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
            // The caption of the grown card.
            Column(
                Modifier
                    .offset(x = (1f + 20f).dp, y = (1f + SCENE_HEIGHT - 116f).dp)
                    .width((BANNER_WIDTH - 2f - 20f - 18f).dp)
                    .graphicsLayer {
                        val now = t()
                        val p = easeOut(seg(now, CAPTION_AT, CAPTION_AT + CAPTION_IN)) * (1f - seg(now, COLLAPSE_AT, COLLAPSE_AT + 0.25f))
                        alpha = p
                        translationY = (if (reduced) 0f else (1f - p) * 10f) * density
                    },
            ) {
                Text(cardKicker, color = Cream, style = style(11f, 14f, FontWeight.Bold, 1.4f))
                Row(Modifier.padding(top = 6.dp).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                    Column(Modifier.weight(1f)) {
                        Text(cardTitle, color = Ink, style = style(28f, 32f, FontWeight.SemiBold, -0.6f))
                        Text(cardMeta, Modifier.padding(top = 4.dp), color = Cream, style = style(14f, 18f, FontWeight.Medium))
                    }
                    Box(
                        Modifier
                            .size(48.dp)
                            .dropShadow(CircleShape, Shadow(radius = 16.dp, color = Color(30, 10, 0).copy(alpha = 0.35f), offset = DpOffset(0.dp, 6.dp)))
                            .background(Color(0xFFFFF6EC), CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.PlayArrow, null, Modifier.padding(start = 3.dp).size(26.dp), tint = Color(0xFF3A1F0E))
                    }
                }
            }
        }

        // The title and the subtitle.
        Column(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 32.dp, end = 32.dp, bottom = copyBottom.dp)
                .fillMaxWidth()
                .height(COPY_HEIGHT.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(title, color = Ink, style = style(32f, 38f, FontWeight.Medium, -0.8f), textAlign = TextAlign.Center)
            Text(subtitle, color = Mute, style = style(16f, 22f), textAlign = TextAlign.Center)
        }

        // The button dims and shrinks a little while it is held.
        val source = remember { MutableInteractionSource() }
        val pressed by source.collectIsPressedAsState()
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = 20.dp, end = 20.dp, bottom = buttonBottom.dp)
                .fillMaxWidth()
                .height(BUTTON_HEIGHT.dp)
                .graphicsLayer {
                    alpha = if (pressed) 0.6f else 1f
                    scaleX = if (pressed) 0.98f else 1f
                    scaleY = scaleX
                }
                .background(Color.White.copy(alpha = 0.04f), CircleShape)
                .border(1.5.dp, Color.White.copy(alpha = 0.85f), CircleShape)
                .clickable(interactionSource = source, indication = null, role = Role.Button) {
                    if (clock.touch(TAP_AT, false)) {
                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                        onEnable?.invoke()
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Notifications, null, Modifier.size(20.dp), tint = Ink)
            Text(buttonLabel, color = Ink, style = style(17f, 22f, FontWeight.Medium, -0.2f))
        }
    }
}

/** One line of the notification, sliding in a beat after the one above it. */
@Composable
private fun NotificationLine(index: Int, t: () -> Float, reduced: Boolean, content: @Composable () -> Unit) {
    Box(
        Modifier.graphicsLayer {
            val at = BANNER_AT + 0.22f + index * 0.06f
            val p = if (reduced) 1f else easeOut(seg(t(), at, at + 0.35f))
            alpha = p
            translationX = (1f - p) * -8f * density
        },
    ) { content() }
}
