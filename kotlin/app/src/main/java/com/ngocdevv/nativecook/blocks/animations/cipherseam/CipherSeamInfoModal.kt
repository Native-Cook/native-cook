package com.ngocdevv.nativecook.blocks.animations.cipherseam

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

private val SheetColor = Color(0xFF201332)

/** The fade of `animationType="fade"` on Android: the platform's short, decelerating fade. */
private const val FADE_MS = 200
private val FadeEase = Easing { 1f - (1f - it) * (1f - it) }

/**
 * The `Modal` of the React Native file: a 60% black backdrop over the whole screen, fading in and
 * out, and on it a sheet of `#201332` with 28 dp corners, 24 dp of padding and at most [maxHeight]
 * dp tall. It holds "One boundary. Two representations." and two lines of copy in a scroll view
 * whose edges blur when the copy does not fit, and a Got it button. The backdrop takes every touch
 * and does not close the sheet; the back button and Got it do ([onClose]).
 *
 * @param visible `info`
 */
@Composable
internal fun CipherSeamInfoModal(visible: Boolean, maxHeight: Float, onClose: () -> Unit) {
    val fade = remember { Animatable(0f) }
    LaunchedEffect(visible) {
        fade.animateTo(if (visible) 1f else 0f, tween(FADE_MS, easing = FadeEase))
    }
    if (!visible && fade.value <= 0f) return
    BackHandler(enabled = visible, onBack = onClose)

    val density = LocalDensity.current
    val scroll = rememberScrollState()
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = fade.value }
            .background(Color(0x99000000))
            // The backdrop swallows what the sheet does not use, so nothing under it is touched.
            .pointerInput(Unit) {
                awaitPointerEventScope {
                    while (true) awaitPointerEvent().changes.forEach { it.consume() }
                }
            }
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .heightIn(max = maxHeight.dp)
                .clip(RoundedCornerShape(28.dp))
                .background(SheetColor)
                .padding(24.dp),
        ) {
            CipherSeamBlurViewport(
                enabled = visible,
                overflowing = { scroll.maxValue > 4f * density.density },
                backgroundColor = SheetColor,
                modifier = Modifier.weight(1f, fill = false),
            ) {
                Column(Modifier.verticalScroll(scroll)) {
                    BasicText(
                        text = "One boundary. Two representations.",
                        modifier = Modifier.padding(bottom = 18.dp),
                        style = TextStyle(
                            color = Color(0xFFF4EAFE),
                            fontSize = cipherSeamSp(24f),
                            lineHeight = cipherSeamSp(29f),
                            fontWeight = FontWeight.SemiBold,
                            lineHeightStyle = CipherSeamLineHeightStyle,
                        ),
                    )
                    ModalCopy("The card and the encoded pattern move as one plane. A fixed violet seam clips each representation, so the transformation stays attached to the card.")
                    ModalCopy("This is an interactive visual example. It does not process, store, or encrypt payment information.")
                }
            }
            Box(
                Modifier
                    .padding(top = 8.dp)
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(Color(0xFFF0E5FF))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null,
                        role = Role.Button,
                        onClick = onClose,
                    ),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(
                    text = "Got it",
                    style = TextStyle(
                        color = Color(0xFF120A22),
                        fontSize = cipherSeamSp(14f),
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        lineHeightStyle = CipherSeamLineHeightStyle,
                    ),
                )
            }
        }
    }
}

/** `modalCopy`: 15 dp text on 23 dp lines in `#C5B7D5`, 18 dp below. */
@Composable
private fun ModalCopy(text: String) {
    BasicText(
        text = text,
        modifier = Modifier.padding(bottom = 18.dp),
        style = TextStyle(
            color = Color(0xFFC5B7D5),
            fontSize = cipherSeamSp(15f),
            lineHeight = cipherSeamSp(23f),
            lineHeightStyle = CipherSeamLineHeightStyle,
        ),
    )
}
