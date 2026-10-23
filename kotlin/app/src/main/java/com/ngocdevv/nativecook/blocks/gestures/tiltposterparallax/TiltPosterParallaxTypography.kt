package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import android.graphics.Typeface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily

internal class TiltPosterParallaxTypography(
    val serif: FontFamily,
    val regular: FontFamily,
    val medium: FontFamily,
    val semibold: FontFamily,
    val serifTypeface: Typeface,
    val regularTypeface: Typeface,
    val mediumTypeface: Typeface,
    val semiboldTypeface: Typeface,
)

@Composable
internal fun rememberTiltPosterParallaxTypography(): TiltPosterParallaxTypography {
    val context = LocalContext.current
    return remember(context) {
        TiltPosterParallaxTypography(
            serif = FontFamily(Font(Tokens.SERIF_ASSET, context.assets)),
            regular = FontFamily(Font(Tokens.INTER_REGULAR_ASSET, context.assets)),
            medium = FontFamily(Font(Tokens.INTER_MEDIUM_ASSET, context.assets)),
            semibold = FontFamily(Font(Tokens.INTER_SEMIBOLD_ASSET, context.assets)),
            serifTypeface = Typeface.createFromAsset(context.assets, Tokens.SERIF_ASSET),
            regularTypeface = Typeface.createFromAsset(context.assets, Tokens.INTER_REGULAR_ASSET),
            mediumTypeface = Typeface.createFromAsset(context.assets, Tokens.INTER_MEDIUM_ASSET),
            semiboldTypeface = Typeface.createFromAsset(context.assets, Tokens.INTER_SEMIBOLD_ASSET),
        )
    }
}
