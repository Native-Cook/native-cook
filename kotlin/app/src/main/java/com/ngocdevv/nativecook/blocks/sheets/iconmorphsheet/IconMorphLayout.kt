package com.ngocdevv.nativecook.blocks.sheets.iconmorphsheet

import kotlin.math.round

internal data class IconMorphLayout(val width: Double, val height: Double, val topInset: Double, val bottomInset: Double) {
    val compact = height < 780
    val buttonTop = height - bottomInset - (if (compact) 16 else 8) - 56
    val stageHeight = if (compact) 208.0 else 264.0
    val stageTop = buttonTop - 32 - stageHeight
    val headerY = topInset + (if (compact) 20 else 22)
    val avatarSize = if (compact) 36.0 else 44.0
    val avatarY = headerY + (if (compact) 44 else 66)
    val nameY = headerY + 104
    val titleY = headerY + (if (compact) 88 else 142)
    val titleSize = if (compact) 22.0 else 28.0
    val titleLine = if (compact) 28.0 else 36.0
    private val titleBottom = titleY + titleLine * (if (compact) 1.5 else 2.5)
    val balanceTop = round(((titleBottom + buttonTop) / 2 - 24) / 4) * 4
    val balanceVisible = balanceTop >= titleBottom + 16 && balanceTop + 48 <= buttonTop - 16
    val copyBottom = stageTop - 8
    val unit = if (compact) 66.0 else 84.0
    val dotsTop = buttonTop - 30
    fun sheetHeight(position: Double) = IconMorphTimeline.sheetHeight(position, if (compact) 466.0 else 522.0, if (compact) 486.0 else 542.0)
    fun travel(frame: IconMorphFrame, reduced: Boolean) = if (reduced) 0.0 else (1 - frame.open) * sheetHeight(frame.position) + frame.drag
}
