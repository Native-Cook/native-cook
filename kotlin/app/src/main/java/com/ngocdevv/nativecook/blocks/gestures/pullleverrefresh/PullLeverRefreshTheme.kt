package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

import android.graphics.Color.parseColor
import androidx.compose.ui.graphics.Color as ComposeColor
import androidx.compose.ui.text.font.FontWeight

/** Compose representations derived from the canonical React Native values in Tokens.kt. */
internal object PullLeverRefreshTheme {
    internal object Color {
        val background = pullLeverRefreshColor(PullLeverRefreshTokens.Color.background)
        val track = pullLeverRefreshColor(PullLeverRefreshTokens.Color.track)
        val trackDot = pullLeverRefreshColor(PullLeverRefreshTokens.Color.trackDot)
        val fill = pullLeverRefreshColor(PullLeverRefreshTokens.Color.fill)
        val fillDot = pullLeverRefreshColor(PullLeverRefreshTokens.Color.fillDot)
        val pivot = pullLeverRefreshColor(PullLeverRefreshTokens.Color.pivot)
        val ink = pullLeverRefreshColor(PullLeverRefreshTokens.Color.ink)
        val mute = pullLeverRefreshColor(PullLeverRefreshTokens.Color.mute)
        val finger = pullLeverRefreshColor(PullLeverRefreshTokens.Color.finger)
        val dimFill = pullLeverRefreshColor(PullLeverRefreshTokens.Color.dimFill)
        val arcOff = pullLeverRefreshColor(PullLeverRefreshTokens.Color.arcOff)
        val shadow = pullLeverRefreshColor(PullLeverRefreshTokens.Color.shadow)
        val shaftLightStart = pullLeverRefreshColor(PullLeverRefreshTokens.Color.shaftLightStart)
        val shaftLightEnd = pullLeverRefreshColor(PullLeverRefreshTokens.Color.shaftLightEnd)
        val shaftLight = listOf(shaftLightStart, shaftLightEnd)
        val specular = pullLeverRefreshColor(PullLeverRefreshTokens.Color.specular)
        val knobLit = pullLeverRefreshColor(PullLeverRefreshTokens.Color.knobLit)
    }

    val Artboard = PullLeverRefreshTokens.Artboard
    val Lever = PullLeverRefreshTokens.Lever
    val Layout = PullLeverRefreshTokens.Layout

    internal object Type {
        internal data class Caption(
            val fontSize: Float,
            val lineHeight: Float,
            val fontWeight: FontWeight,
            val letterSpacing: Float,
        )

        val caption = Caption(
            fontSize = PullLeverRefreshTokens.Type.captionFontSize,
            lineHeight = PullLeverRefreshTokens.Type.captionLineHeight,
            fontWeight = FontWeight(PullLeverRefreshTokens.Type.captionFontWeight),
            letterSpacing = PullLeverRefreshTokens.Type.captionLetterSpacing,
        )
    }
}

private fun pullLeverRefreshColor(token: String): ComposeColor {
    if (token.startsWith("#")) {
        return ComposeColor(parseColor(token))
    }
    val channels = token
        .removePrefix("rgba(")
        .removeSuffix(")")
        .split(',')
        .map { channel -> channel.trim() }
    return ComposeColor(
        red = channels[0].toFloat() / 255f,
        green = channels[1].toFloat() / 255f,
        blue = channels[2].toFloat() / 255f,
        alpha = channels[3].toFloat(),
    )
}
