package com.ngocdevv.nativecook.blocks.onboarding.tactilecalculatorwelcome

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

/**
 * The Continue pill: `PressScale` around the `cta` style of the React Native file.
 *
 * Under a finger the whole surface settles to 0.96 in 150 ms on the strong ease-out and returns on
 * the same curve; the transition retargets mid-flight. Under Reduce Motion it dims to 0.72 instead.
 * A press is kept while the finger stays within 16 dp of the pill (`pressRetentionOffset`); see
 * [tactileCalculatorWelcomePress]. A disabled pill never moves and never answers.
 *
 * Surface: #2D2B2C, radius 28, minimum height 56, padding 20 by 14. [textRole] is the label's text role
 * (Inter Bold 21 on 26, white, capped at 1.6 times).
 */
@Composable
internal fun TactileCalculatorWelcomeButton(
    label: String,
    textRole: TactileCalculatorWelcomeTextRole,
    enabled: Boolean,
    reduced: Boolean,
    onPressIn: () -> Unit,
    onPressOut: () -> Unit,
    onPress: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val currentPressIn by rememberUpdatedState(onPressIn)
    val currentPressOut by rememberUpdatedState(onPressOut)
    val currentPress by rememberUpdatedState(onPress)
    var pressed by remember { mutableStateOf(false) }
    val down = pressed && enabled
    val progress = remember { Animatable(0f) }
    val style = textRole.sized(LocalDensity.current.fontScale)

    LaunchedEffect(down) {
        progress.animateTo(if (down) 1f else 0f, tween(150, easing = TactileCalculatorWelcomeEase))
    }

    Box(
        modifier = modifier
            .graphicsLayer {
                if (reduced) {
                    alpha = 1f - 0.28f * progress.value
                } else {
                    val dip = 1f - 0.04f * progress.value
                    scaleX = dip
                    scaleY = dip
                }
            }
            .background(Color(0xFF2D2B2C), RoundedCornerShape(28.dp))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = label
                onClick(label) {
                    if (!enabled) return@onClick false
                    currentPress()
                    return@onClick true
                }
            }
            .tactileCalculatorWelcomePress(
                enabled = enabled,
                retention = 16.dp,
                onPressIn = {
                    pressed = true
                    currentPressIn()
                },
                onPressOut = {
                    pressed = false
                    currentPressOut()
                },
                onPress = { currentPress() },
            )
            .heightIn(min = 56.dp)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(text = label, style = style)
    }
}
