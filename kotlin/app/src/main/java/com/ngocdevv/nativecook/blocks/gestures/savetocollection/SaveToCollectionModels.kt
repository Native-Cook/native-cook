package com.ngocdevv.nativecook.blocks.gestures.savetocollection

import kotlin.math.roundToInt

internal data class SaveToCollectionFrame(
    val x: Float,
    val y: Float,
    val width: Float,
    val height: Float,
) {
    val centerX: Float get() = x + width / 2f
    val centerY: Float get() = y + height / 2f

    fun contains(px: Float, py: Float): Boolean {
        return px in x..(x + width) && py in y..(y + height)
    }
}

internal data class SaveToCollectionPhoto(
    val asset: String,
    val title: String,
    val place: String,
)

internal data class SaveToCollectionLayout(
    val width: Float,
    val height: Float,
    val safeTop: Float,
    val safeBottom: Float,
    val columnWidth: Float,
    val gridTop: Float,
    val tiles: List<SaveToCollectionFrame>,
    val barY: Float,
    val saveX: Float,
    val saveY: Float,
    val sheetY: Float,
) {
    companion object {
        fun create(width: Float, height: Float, safeTop: Float, safeBottom: Float): SaveToCollectionLayout {
            val columnWidth = (width - SaveToCollectionTokens.Edge * 2f - SaveToCollectionTokens.Gap) / 2f
            val heightScale = columnWidth / 181f
            val gridTop = safeTop + SaveToCollectionTokens.GridTop
            val tiles = buildList {
                SaveToCollectionTokens.ColumnHeights.forEachIndexed { column, heights ->
                    var y = gridTop
                    heights.forEach { designHeight ->
                        val tileHeight = (designHeight * heightScale).roundToInt().toFloat()
                        add(
                            SaveToCollectionFrame(
                                x = SaveToCollectionTokens.Edge + column * (columnWidth + SaveToCollectionTokens.Gap),
                                y = y,
                                width = columnWidth,
                                height = tileHeight,
                            ),
                        )
                        y += tileHeight + SaveToCollectionTokens.Gap
                    }
                }
            }
            val barY = height - maxOf(safeBottom, SaveToCollectionTokens.Edge) -
                SaveToCollectionTokens.BarGap - SaveToCollectionTokens.BarHeight
            val saveX = width - SaveToCollectionTokens.Edge - SaveToCollectionTokens.SaveSize / 2f
            return SaveToCollectionLayout(
                width = width,
                height = height,
                safeTop = safeTop,
                safeBottom = safeBottom,
                columnWidth = columnWidth,
                gridTop = gridTop,
                tiles = tiles,
                barY = barY,
                saveX = saveX,
                saveY = barY + SaveToCollectionTokens.BarHeight / 2f,
                sheetY = safeTop + SaveToCollectionTokens.SheetTop,
            )
        }
    }
}

internal enum class SaveToCollectionHaptic {
    Lift,
    Magnet,
    Select,
    Success,
}

internal enum class SaveToCollectionEase {
    Linear,
    EaseOut,
    EaseInOut,
}
