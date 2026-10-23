package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.ui.text.font.FontWeight

internal enum class PitStopReviewFace(
    val file: String,
    val weight: FontWeight,
) {
    Medium("medium", FontWeight.Medium),
    SemiBold("semibold", FontWeight.SemiBold),
    Bold("bold", FontWeight.Bold),
    ExtraBold("extrabold", FontWeight.ExtraBold),
    Black("black", FontWeight.Black),
}
