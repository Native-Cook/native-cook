package com.ngocdevv.nativecook.blocks.buttons.pricingsegmentedbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.surface
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorProducer
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.invisibleToUser
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.pow
import kotlinx.coroutines.delay

enum class PricingValue { Free, Monthly, Annual }

private const val WIDTH = 336f
private const val HEIGHT = 48f

// Gap between the track and the black pill, and between the black pill and the white one.
private const val INSET = 3f
private const val INNER_INSET = 2f
private const val PILL_W = WIDTH / 2 - INSET * 2
private const val PILL_H = HEIGHT - INSET * 2

// How far the black pill travels between Free and Premium.
private const val SHIFT = WIDTH / 2
private const val KNOB_W = (PILL_W - INNER_INSET * 2) / 2
private const val KNOB_H = PILL_H - INNER_INSET * 2
private const val KNOB_X = SHIFT + INSET + INNER_INSET

// The canvas overflows the control by this much on every side to fit the drop shadow.
private val BLEED = 48.dp
private const val PLAN_MS = 480
private const val BILLING_MS = 380
private const val AUTO_PAUSE_MS = 1500L
private const val STRETCH = 1.4f

private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

private val TrackColor = Color(0xFFFAFAFA)
private val TrackEdge = Color(0xFFE6E6E6)
private val PillColor = Color(0xFF050505)
private val Muted = Color(0xFFB3B3B3)
private val MutedOnBlack = Color(0xFF8A8A8A)
private val LabelStyle = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
private val SmallStyle = TextStyle(color = Color(0xFFCDCDCD), fontSize = 8.5.sp, fontWeight = FontWeight.Medium)

private fun seg(t: Float, from: Float, to: Float) = ((t - from) / (to - from)).coerceIn(0f, 1f)

// A pill's two ends follow the same progress on different curves, so whichever end faces the
// direction of travel moves first and the pill stretches on the way, in both directions.
private fun lead(x: Float) = 1f - (1f - x.coerceIn(0f, 1f)).pow(STRETCH)
private fun lag(x: Float) = x.coerceIn(0f, 1f).pow(STRETCH)

private fun DrawScope.pill(color: Color, x: Float, y: Float, left: Float, right: Float, height: Float, scale: Float, alpha: Float = 1f) {
    val w = (right - left) * scale
    val h = height * scale
    drawRoundRect(
        color = color,
        topLeft = Offset(x + (left + right) / 2 - w / 2, y + (HEIGHT - h) / 2),
        size = Size(w, h),
        cornerRadius = CornerRadius(h / 2),
        alpha = alpha,
    )
}

/**
 * Pricing switch. "Free | Premium" where choosing Premium slides the black pill across and opens it
 * into a nested "Monthly | Annual" switch with its own white pill; choosing Free closes it again.
 *
 * @param value Controlled selection. Leave `null` to let the control keep its own.
 * @param autoPlay Walks Free → Monthly → Annual → Monthly → Free by itself, pausing on each.
 */
@Composable
fun PricingSegmentedButton(
    value: PricingValue? = null,
    onValueChange: (PricingValue) -> Unit = {},
    modifier: Modifier = Modifier,
    freeLabel: String = "Free",
    premiumLabel: String = "Premium",
    monthlyLabel: String = "Monthly",
    annualLabel: String = "Annual",
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    var pressed by remember { mutableStateOf(false) }
    var inner by remember { mutableStateOf(PricingValue.Free) }
    // Premium reopens on whichever billing period was chosen last.
    var lastBilling by remember { mutableStateOf(PricingValue.Monthly) }
    // Which way autoPlay is walking through the options.
    var forward by remember { mutableStateOf(true) }
    val selected = value ?: inner
    val premium = selected != PricingValue.Free
    val billingChoice = if (premium) selected else lastBilling
    val controlled by rememberUpdatedState(value != null)
    val notify by rememberUpdatedState(onValueChange)

    fun select(next: PricingValue) {
        if (next == selected) return
        if (next != PricingValue.Free) lastBilling = next
        if (!controlled) inner = next
        notify(next)
    }

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    pressed = true
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> pressed = false
            }
        }
    }

    // Re-armed after every change, so a tap simply restarts the pause.
    LaunchedEffect(autoPlay, reducedMotion, pressed, selected) {
        if (!autoPlay || reducedMotion || pressed) return@LaunchedEffect
        delay(AUTO_PAUSE_MS)
        if (selected == PricingValue.Free) forward = true
        if (selected == PricingValue.Annual) forward = false
        select(
            when {
                selected != PricingValue.Monthly -> PricingValue.Monthly
                forward -> PricingValue.Annual
                else -> PricingValue.Free
            },
        )
    }

    // 0 = Free, 1 = Premium. Read only while drawing, so a change never recomposes.
    val plan: State<Float> = animateFloatAsState(
        targetValue = if (premium) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else PLAN_MS, easing = EaseInOut),
        label = "plan",
    )
    // 0 = Monthly, 1 = Annual.
    val billing: State<Float> = animateFloatAsState(
        targetValue = if (billingChoice == PricingValue.Annual) 1f else 0f,
        animationSpec = tween(if (reducedMotion) 0 else BILLING_MS, easing = EaseInOut),
        label = "billing",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.17f), sigma = 13f) }

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            Canvas(Modifier.fillMaxSize()) {
                surface(BLEED) { x, y, _, _ ->
                    // The white pill grows into place as the black one arrives.
                    val open = seg(plan.value, 0.4f, 1f)
                    drawIntoCanvas {
                        shadow.draw(it.nativeCanvas, x + 16f, y + 18f, WIDTH - 32f, HEIGHT - 8f, (HEIGHT - 8f) / 2)
                    }
                    drawRoundRect(TrackEdge, Offset(x - 0.5f, y - 0.5f), Size(WIDTH + 1f, HEIGHT + 1f), CornerRadius(HEIGHT / 2 + 0.5f))
                    drawRoundRect(TrackColor, Offset(x, y), Size(WIDTH, HEIGHT), CornerRadius(HEIGHT / 2))
                    pill(
                        PillColor, x, y,
                        left = INSET + SHIFT * lag(plan.value),
                        right = INSET + PILL_W + SHIFT * lead(plan.value),
                        height = PILL_H, scale = 1f,
                    )
                    pill(
                        Color.White, x, y,
                        left = KNOB_X + KNOB_W * lag(billing.value),
                        right = KNOB_X + KNOB_W + KNOB_W * lead(billing.value),
                        height = KNOB_H, scale = 0.6f + 0.4f * open, alpha = open,
                    )
                }
            }
        }

        // Labels are decoration here; the segments below carry the semantics.
        Box(Modifier.matchParentSize().clearAndSetSemantics {}) {
            Box(Modifier.width(SHIFT.dp).fillMaxHeight(), contentAlignment = Alignment.Center) {
                BasicText(freeLabel, style = LabelStyle, maxLines = 1, color = { lerp(Color.White, Muted, plan.value) })
            }
            // "Premium" lifts away while the two billing labels grow out of its small print.
            Column(
                modifier = Modifier
                    .offset(x = SHIFT.dp)
                    .width(SHIFT.dp)
                    .fillMaxHeight()
                    .graphicsLayer {
                        val gone = seg(plan.value, 0f, 0.5f)
                        alpha = 1f - gone
                        translationY = -6f * gone * density
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BasicText(premiumLabel, style = LabelStyle.copy(color = Muted), maxLines = 1)
                BasicText("$monthlyLabel · $annualLabel", style = SmallStyle, maxLines = 1)
            }
            BillingLabel(monthlyLabel, KNOB_X, plan) { lerp(Color.Black, MutedOnBlack, billing.value) }
            BillingLabel(annualLabel, KNOB_X + KNOB_W, plan) { lerp(MutedOnBlack, Color.Black, billing.value) }
        }

        // The three hit areas stay in place whatever is selected, so a press is never cut short by
        // its own segment being swapped out. While Free is selected the two right-hand quarters
        // both mean "Premium", and only the first is announced.
        Row(Modifier.matchParentSize().selectableGroup()) {
            Segment(freeLabel, !premium, 2f, interaction) { select(PricingValue.Free) }
            Segment(if (premium) monthlyLabel else premiumLabel, selected == PricingValue.Monthly, 1f, interaction) {
                select(if (premium) PricingValue.Monthly else lastBilling)
            }
            Segment(annualLabel, selected == PricingValue.Annual, 1f, interaction, hidden = !premium) {
                select(if (premium) PricingValue.Annual else lastBilling)
            }
        }
    }
}

/** An invisible hit area; everything visible is drawn by the canvas and the labels above it. */
@Composable
private fun RowScope.Segment(
    label: String,
    isSelected: Boolean,
    weight: Float,
    interaction: MutableInteractionSource,
    hidden: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .weight(weight)
            .fillMaxHeight()
            .selectable(
                selected = isSelected,
                interactionSource = interaction,
                indication = null,
                role = Role.RadioButton,
                onClick = onClick,
            )
            .semantics {
                contentDescription = label
                if (hidden) invisibleToUser()
            },
    )
}

@Composable
private fun BillingLabel(text: String, x: Float, plan: State<Float>, color: ColorProducer) {
    Box(
        modifier = Modifier
            .offset(x = x.dp)
            .width(KNOB_W.dp)
            .fillMaxHeight()
            .graphicsLayer {
                val open = seg(plan.value, 0.4f, 1f)
                alpha = open
                scaleX = 0.6f + 0.4f * open
                scaleY = 0.6f + 0.4f * open
                translationY = 9f * (1f - open) * density
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text, style = LabelStyle, maxLines = 1, color = color)
    }
}
