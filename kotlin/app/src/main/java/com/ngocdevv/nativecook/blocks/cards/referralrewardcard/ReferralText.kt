package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.sp

internal fun referralText(
    size: Int,
    line: Int,
    weight: FontWeight = FontWeight.Normal,
) = TextStyle(
    color = Color.White,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle =
        LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)
