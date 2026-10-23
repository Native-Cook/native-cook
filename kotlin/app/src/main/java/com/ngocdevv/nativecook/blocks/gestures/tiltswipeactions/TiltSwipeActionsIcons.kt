package com.ngocdevv.nativecook.blocks.gestures.tiltswipeactions

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
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.unit.dp

/**
 * The Feather glyphs the reference takes from @expo/vector-icons, as the outline paths of the
 * Feather set (24 x 24 viewBox, 2 wide round stroke), so they scale like the icon font does.
 */
internal enum class FeatherIcon(vararg val paths: String) {
    ShoppingCart(
        "M8 21a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        "M19 21a1 1 0 1 0 2 0a1 1 0 1 0 -2 0",
        "M1 1h4l2.68 13.39a2 2 0 0 0 2 1.61h9.72a2 2 0 0 0 2-1.61L23 6H6",
    ),
    Home(
        "M3 9l9-7 9 7v11a2 2 0 0 1-2 2H5a2 2 0 0 1-2-2z",
        "M9 22V12h6v10",
    ),
    Coffee(
        "M18 8h1a4 4 0 0 1 0 8h-1",
        "M2 8h16v9a4 4 0 0 1-4 4H6a4 4 0 0 1-4-4V8z",
        "M6 1v3",
        "M10 1v3",
        "M14 1v3",
    ),
    Navigation("M3 11L22 2L13 21L11 13L3 11Z"),
    Film(
        "M4.18 2H19.82A2.18 2.18 0 0 1 22 4.18V19.82A2.18 2.18 0 0 1 19.82 22H4.18A2.18 2.18 0 0 1 2 19.82V4.18A2.18 2.18 0 0 1 4.18 2Z",
        "M7 2v20",
        "M17 2v20",
        "M2 12h20",
        "M2 7h5",
        "M2 17h5",
        "M17 17h5",
        "M17 7h5",
    ),
    Activity("M22 12h-4l-3 9L9 3l-3 9H2"),
    BookOpen(
        "M2 3h6a4 4 0 0 1 4 4v14a3 3 0 0 0-3-3H2z",
        "M22 3h-6a4 4 0 0 0-4 4v14a3 3 0 0 1 3-3h7z",
    ),
    PlusSquare(
        "M5 3H19A2 2 0 0 1 21 5V19A2 2 0 0 1 19 21H5A2 2 0 0 1 3 19V5A2 2 0 0 1 5 3Z",
        "M12 8v8",
        "M8 12h8",
    ),
    Flag(
        "M4 15s1-1 4-1 5 2 8 2 4-1 4-1V3s-1 1-4 1-5-2-8-2-4 1-4 1z",
        "M4 22v-7",
    ),
    Trash2(
        "M3 6h18",
        "M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6m3 0V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2",
        "M10 11v6",
        "M14 11v6",
    ),
    RotateCcw(
        "M1 4v6h6",
        "M3.51 15a9 9 0 1 0 2.13-9.36L1 10",
    ),
    Plus(
        "M12 5v14",
        "M5 12h14",
    ),
    Minus("M5 12h14"),
    ArrowUpRight(
        "M7 17L17 7",
        "M7 7h10v10",
    ),
}

/** `<Feather name size color />`. */
@Composable
internal fun FeatherGlyph(icon: FeatherIcon, size: Float, color: Color, modifier: Modifier = Modifier) {
    val paths = remember(icon) { icon.paths.map { PathParser().parsePathString(it).toPath() } }
    Canvas(modifier.size(size.dp)) {
        val unit = this.size.minDimension / 24f
        scale(unit, unit, pivot = Offset.Zero) {
            val stroke = Stroke(width = 2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
            paths.forEach { drawPath(it, color, style = stroke) }
        }
    }
}

internal enum class TabKind { Pie, Wallet, Person }

/** TabIcons.tsx: the source's three tab glyphs, outlined (26 x 26 viewBox, 2 wide round stroke). */
@Composable
internal fun TabGlyph(
    kind: TabKind,
    color: Color,
    size: Float = TiltDetail.tabIconSize,
    modifier: Modifier = Modifier,
) {
    val paths = remember(kind) {
        when (kind) {
            TabKind.Pie -> listOf(
                PathParser().parsePathString("M20.72 14.38A9 9 0 1 1 10.97 4.63").toPath(),
                PathParser().parsePathString("M12.4 13V3.2A9.8 9.8 0 0 1 22.2 13Z").toPath(),
            )
            TabKind.Wallet -> listOf(
                PathParser().parsePathString("M15.5 4H6.5A3.5 3.5 0 0 0 3 7.5V20.1A3.5 3.5 0 0 0 6.5 23.6H20.7A3.5 3.5 0 0 0 24.2 20.1V11.8").toPath(),
                PathParser().parsePathString("M3 11.6C9 14.6 17 14.6 24.2 11.8").toPath(),
            )
            TabKind.Person -> listOf(
                PathParser().parsePathString("M7.6 21.4H23A2.6 2.6 0 0 0 25.6 18.8A3 3 0 0 0 22.6 15.8C21 15.4 19.6 13.6 19 11").toPath(),
            )
        }
    }
    Canvas(modifier.size(size.dp)) {
        val unit = this.size.minDimension / 26f
        scale(unit, unit, pivot = Offset.Zero) {
            val stroke = Stroke(width = TiltDetail.tabIconStroke, cap = StrokeCap.Round, join = StrokeJoin.Round)
            when (kind) {
                TabKind.Pie -> paths.forEach { drawPath(it, color, style = stroke) }
                TabKind.Wallet -> {
                    translate(top = TiltDetail.walletIconOffsetY) {
                        paths.forEach { drawPath(it, color, style = stroke) }
                        drawCircle(color, radius = 2.3f, center = Offset(20.5f, 5.5f), style = stroke)
                    }
                }
                TabKind.Person -> {
                    drawCircle(color, radius = 3.6f, center = Offset(15.5f, 7.4f), style = stroke)
                    paths.forEach { drawPath(it, color, style = stroke) }
                }
            }
        }
    }
}

/** Path helper used by drawing code: a rounded rectangle with one radius per corner. */
internal fun cornerPath(
    left: Float,
    top: Float,
    right: Float,
    bottom: Float,
    topLeft: Float,
    topRight: Float,
    bottomRight: Float,
    bottomLeft: Float,
): Path = Path().apply {
    addRoundRect(
        androidx.compose.ui.geometry.RoundRect(
            left = left,
            top = top,
            right = right,
            bottom = bottom,
            topLeftCornerRadius = CornerRadius(topLeft),
            topRightCornerRadius = CornerRadius(topRight),
            bottomRightCornerRadius = CornerRadius(bottomRight),
            bottomLeftCornerRadius = CornerRadius(bottomLeft),
        ),
    )
}
