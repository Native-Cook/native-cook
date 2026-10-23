package com.ngocdevv.nativecook.blocks.gestures.pullleverrefresh

internal enum class PullLeverRefreshLabel(
    val text: String,
) {
    NONE(""),
    REFRESHING(PullLeverRefreshTokens.CAPTION_REFRESHING),
    UPDATED(PullLeverRefreshTokens.CAPTION_UPDATED),
}
