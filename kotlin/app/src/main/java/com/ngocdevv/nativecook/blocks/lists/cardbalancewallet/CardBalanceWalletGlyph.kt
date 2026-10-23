package com.ngocdevv.nativecook.blocks.lists.cardbalancewallet

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

internal enum class CardBalanceWalletGlyphKind {
    Bell,
    Plus,
    Arrow,
    Scan,
    Grid,
    Chart,
    Chat,
    Wallet,
    Network,
}

@Composable
internal fun CardBalanceWalletGlyph(
    kind: CardBalanceWalletGlyphKind,
    color: Color,
    size: Float,
    modifier: Modifier = Modifier,
) {
    val outline = remember(kind) {
        val data = when (kind) {
            CardBalanceWalletGlyphKind.Bell -> "M6 16.5V11C6 7.4 8.6 4.8 12 4.8C15.4 4.8 18 7.4 18 11V16.5L19.6 18.3H4.4Z"
            CardBalanceWalletGlyphKind.Plus -> "M12 4V20M4 12H20"
            CardBalanceWalletGlyphKind.Arrow -> "M4 12H19.5M13.5 5.8L19.8 12L13.5 18.2"
            CardBalanceWalletGlyphKind.Scan -> "M3.5 8.5V6.5C3.5 4.8 4.8 3.5 6.5 3.5H8.5M15.5 3.5H17.5C19.2 3.5 20.5 4.8 20.5 6.5V8.5M20.5 15.5V17.5C20.5 19.2 19.2 20.5 17.5 20.5H15.5M8.5 20.5H6.5C4.8 20.5 3.5 19.2 3.5 17.5V15.5"
            CardBalanceWalletGlyphKind.Chart -> "M3.5 20.5H20.5"
            else -> ""
        }
        if (data.isEmpty()) Path() else PathParser().parsePathString(data).toPath()
    }
    val bellClapper = remember(kind) {
        if (kind == CardBalanceWalletGlyphKind.Bell) {
            PathParser().parsePathString("M10 20.6C10.4 21.4 11.1 21.8 12 21.8C12.9 21.8 13.6 21.4 14 20.6").toPath()
        } else {
            Path()
        }
    }
    val height = if (kind == CardBalanceWalletGlyphKind.Network) size * 0.62f else size
    Canvas(modifier = modifier.then(Modifier.size(size.dp, height.dp))) {
        val viewWidth = if (kind == CardBalanceWalletGlyphKind.Network) 22f else 24f
        val viewHeight = if (kind == CardBalanceWalletGlyphKind.Network) 14f else 24f
        val scaleX = this.size.width / viewWidth
        val scaleY = this.size.height / viewHeight
        withTransform({ scale(scaleX, scaleY, Offset.Zero) }) {
            when (kind) {
                CardBalanceWalletGlyphKind.Bell -> {
                    drawPath(outline, color, style = Stroke(1.9f, join = StrokeJoin.Round))
                    drawPath(bellClapper, color, style = Stroke(1.9f, cap = StrokeCap.Round))
                }
                CardBalanceWalletGlyphKind.Plus -> drawPath(outline, color, style = Stroke(2.2f, cap = StrokeCap.Round))
                CardBalanceWalletGlyphKind.Arrow -> drawPath(outline, color, style = Stroke(2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                CardBalanceWalletGlyphKind.Scan -> {
                    drawPath(outline, color, style = Stroke(2f, cap = StrokeCap.Round))
                    listOf(Offset(7.2f, 7.2f), Offset(12.8f, 7.2f), Offset(7.2f, 12.8f), Offset(12.8f, 12.8f)).forEach {
                        drawRoundRect(color, it, Size(4f, 4f), CornerRadius(1.3f))
                    }
                }
                CardBalanceWalletGlyphKind.Grid -> {
                    drawRoundRect(color, Offset(3.5f, 3.5f), Size(7.5f, 9.5f), CornerRadius(1.8f))
                    drawRoundRect(color, Offset(13f, 3.5f), Size(7.5f, 6f), CornerRadius(1.8f))
                    drawRoundRect(color, Offset(3.5f, 15f), Size(7.5f, 5.5f), CornerRadius(1.8f))
                    drawRoundRect(color, Offset(13f, 11.5f), Size(7.5f, 9f), CornerRadius(1.8f))
                }
                CardBalanceWalletGlyphKind.Chart -> {
                    drawRoundRect(color, Offset(6f, 10f), Size(3f, 9f), CornerRadius(1.2f))
                    drawRoundRect(color, Offset(10.6f, 5f), Size(3f, 14f), CornerRadius(1.2f))
                    drawRoundRect(color, Offset(15.2f, 12f), Size(3f, 7f), CornerRadius(1.2f))
                    drawPath(outline, color, style = Stroke(1.9f, cap = StrokeCap.Round))
                }
                CardBalanceWalletGlyphKind.Chat -> {
                    val bubble = PathParser().parsePathString("M5 5H19C20.1 5 21 5.9 21 7V15C21 16.1 20.1 17 19 17H13.5L9 20.5V17H5C3.9 17 3 16.1 3 15V7C3 5.9 3.9 5 5 5Z").toPath()
                    drawPath(bubble, color)
                    listOf(8.3f, 12f, 15.7f).forEach { drawCircle(Color(0xFF8E8E93), 1.2f, Offset(it, 11f)) }
                }
                CardBalanceWalletGlyphKind.Wallet -> {
                    val flap = PathParser().parsePathString("M4 7.5C4 6.1 5.1 5 6.5 5H16.5L18 7.5Z").toPath()
                    drawPath(flap, color.copy(alpha = 0.55f))
                    drawRoundRect(color, Offset(3f, 7f), Size(18f, 13f), CornerRadius(3f))
                    drawRoundRect(Color.White, Offset(14f, 11.5f), Size(7f, 4.5f), CornerRadius(1.6f))
                    drawCircle(color, 1f, Offset(16.6f, 13.75f))
                }
                CardBalanceWalletGlyphKind.Network -> {
                    drawRoundRect(Color(0xFFE52A22), Offset(1.6f, 1.6f), Size(10.8f, 10.8f), CornerRadius(3.2f))
                    drawRoundRect(Color(0xE6F89C14), Offset(9.6f, 1.6f), Size(10.8f, 10.8f), CornerRadius(3.2f))
                }
            }
        }
    }
}
