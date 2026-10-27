package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** What every control tells the story's controller: a finger arrived or left (`PressOwnership`). */
internal class ConfettiGiftOfferPressOwnership(val onPressIn: () -> Unit, val onPressOut: () -> Unit)

/**
 * Press handling of a React Native `Pressable`: `onPressIn` at touch down, `onPressOut` when the
 * finger lifts or the gesture is taken by a scroll view, `onPress` after that if it ended inside.
 */
@Composable
internal fun Modifier.confettiGiftOfferPress(
    enabled: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onPress: () -> Unit,
    onDownChanged: (Boolean) -> Unit = {},
): Modifier {
    val pressIn by rememberUpdatedState(onPressIn)
    val pressOut by rememberUpdatedState(onPressOut)
    val press by rememberUpdatedState(onPress)
    val down by rememberUpdatedState(onDownChanged)
    // A disabled control carries no pointer node at all, so a touch falls through to the layer behind
    // it (React Native's `pointerEvents="none"`).
    if (!enabled) return this
    return pointerInput(Unit) {
        awaitEachGesture {
            awaitFirstDown()
            down(true)
            pressIn()
            val up = waitForUpOrCancellation()
            down(false)
            pressOut()
            if (up != null) press()
        }
    }
}

/**
 * `PressScale` (shared/PressScale.tsx): the whole surface settles to 0.96 in 150 ms on the strong
 * ease-out and returns on the same curve; under Reduce Motion it dims to 0.72 instead.
 */
@Composable
internal fun ConfettiGiftOfferPressScale(
    enabled: Boolean,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    scale: Float = 0.96f,
    content: @Composable () -> Unit,
) {
    var down by remember { mutableStateOf(false) }
    val pressed = down && enabled
    val pressScale by animateFloatAsState(if (pressed && !reduced) scale else 1f, tween(150, easing = ConfettiGiftOfferEaseOut), label = "pressScale")
    val pressAlpha by animateFloatAsState(if (pressed && reduced) 0.72f else 1f, tween(150, easing = ConfettiGiftOfferEaseOut), label = "pressAlpha")
    Box(
        modifier
            .graphicsLayer {
                scaleX = pressScale
                scaleY = pressScale
                alpha = pressAlpha
            }
            .semantics(mergeDescendants = true) {
                role = Role.Button
                if (label != null) {
                    this.contentDescription = label
                }
                onClick(label) {
                    onPress()
                    true
                }
            }
            .confettiGiftOfferPress(enabled, ownership.onPressIn, ownership.onPressOut, onPress) { down = it },
    ) { content() }
}

/** The four button looks of ConfettiGiftOffer.tsx's `Button`. */
internal enum class ConfettiGiftOfferButtonStyle { Primary, Secondary, Mint, Danger }

/** `Button` (ConfettiGiftOffer.tsx:42): a 57 dp capsule with an optional icon and a 17 pt label. */
@Composable
internal fun ConfettiGiftOfferButton(
    label: String,
    onPress: () -> Unit,
    ownership: ConfettiGiftOfferPressOwnership,
    reduced: Boolean,
    enabled: Boolean,
    modifier: Modifier = Modifier.fillMaxWidth(),
    style: ConfettiGiftOfferButtonStyle = ConfettiGiftOfferButtonStyle.Primary,
    minHeight: Dp = 57.dp,
    radius: Dp = 29.dp,
    icon: (@Composable () -> Unit)? = null,
) {
    val density = LocalDensity.current
    val background: Color
    val borderColor: Color?
    val borderWidth: Dp
    val labelColor: Color
    when (style) {
        ConfettiGiftOfferButtonStyle.Primary -> {
            background = Color(0xFF171717)
            borderColor = null
            borderWidth = 0.dp
            labelColor = Color.White
        }
        ConfettiGiftOfferButtonStyle.Secondary -> {
            background = Color(0x59FFFFFF)
            borderColor = Color(0xFFD3D3DD)
            borderWidth = 1.5.dp
            labelColor = Color(0xFF1C1C1E)
        }
        ConfettiGiftOfferButtonStyle.Mint -> {
            background = Color(0xFF45BA51)
            borderColor = Color(0xFF2F7A36)
            borderWidth = 5.dp
            labelColor = Color.White
        }
        ConfettiGiftOfferButtonStyle.Danger -> {
            background = Color.White
            borderColor = Color(0xFFF7CFCB)
            borderWidth = 1.5.dp
            labelColor = Color(0xFFE5484D)
        }
    }
    val shape = RoundedCornerShape(radius)
    ConfettiGiftOfferPressScale(
        enabled = enabled,
        reduced = reduced,
        ownership = ownership,
        onPress = onPress,
        label = label,
        modifier = modifier,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .heightIn(min = minHeight)
                .background(background, shape)
                .then(if (borderColor != null) Modifier.border(borderWidth, borderColor, shape) else Modifier)
                .padding(horizontal = 20.dp + borderWidth, vertical = 14.dp + borderWidth),
            horizontalArrangement = Arrangement.spacedBy(9.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            icon?.invoke()
            BasicText(
                text = label,
                style = density.confettiGiftOfferText(labelColor, 17f, 22f, 600),
            )
        }
    }
}

/** The "Offer expire in" pill (ConfettiGiftOffer.tsx:46): 32 dp tall, a dark red chip with red tabular digits. */
@Composable
internal fun ConfettiGiftOfferCountdown(label: String, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    Box(
        modifier
            .height(32.dp)
            .background(Color(0xFF6E3C36), RoundedCornerShape(9.dp))
            .padding(horizontal = 15.dp)
            .semantics { this.contentDescription = "Offer expires in $label" },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            text = "Offer expire in: $label",
            style = density.confettiGiftOfferText(Color(0xFFFF4D4D), 14f, weight = 600, tabular = true),
        )
    }
}
