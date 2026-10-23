package com.ngocdevv.nativecook.gallery

import androidx.compose.ui.graphics.Color

// The gallery's own look. Blocks bring their colours with them and don't read these.
object GalleryColors {
    val Background = Color(0xFF121210)
    val Surface = Color(0xFF1C1C19)
    val SurfacePressed = Color(0xFF262622)
    val Text = Color(0xFFF4F3EC)
    val TextMuted = Color(0xFF9A998F)
    val Accent = Color(0xFFFFF48D)
    val Border = Text.copy(alpha = 0.1f)
}
