package com.ngocdevv.nativecook.blocks.forms.timetargetdial.ui.theme

import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.foundation.clickable

/** Same hex values as the React Native StyleSheets. */
object AppColors {
    val Background = Color.Black
    val Divider = Color(0xFF374151)
    val Grey = Color(0xFF424446)
    val GreyIcon = Color(0xFF63666A)
    val Inactive = Color(0xFF9CA3AF)
    val Teal = Color(0xFF00655C)
    val Yellow = Color(0xFFFFDA50)
    val Tick = Color(0xFFC6C6C6)
    val Label = Color(0xFFD8D8D8)
    val ConfirmText = Color(0xFF504E4E)
    val BackBorder = Color(0xFF333333)
}

/**
 * The React Native source sets these labels in SF Pro Rounded Bold. Android has no rounded system
 * face, so they use the default one; each label asks for the bold weight itself.
 */
val LabelFont: FontFamily = FontFamily.Default

@Composable
fun TimeTargetTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = AppColors.Background,
            surface = AppColors.Background,
        ),
        content = content,
    )
}

/** Equivalent of React Native's `TouchableOpacity`. */
fun Modifier.touchableOpacity(
    activeOpacity: Float = 0.2f,
    onClick: () -> Unit,
): Modifier = composed {
    val interactionSource = remember { MutableInteractionSource() }
    val pressed by interactionSource.collectIsPressedAsState()
    this
        .alpha(if (pressed) activeOpacity else 1f)
        .clickable(interactionSource = interactionSource, indication = null, onClick = onClick)
}
