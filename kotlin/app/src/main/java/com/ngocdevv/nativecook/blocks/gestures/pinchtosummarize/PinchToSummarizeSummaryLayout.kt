package com.ngocdevv.nativecook.blocks.gestures.pinchtosummarize

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.Constraints
import kotlin.math.roundToInt

/** One native measurement shared by the motion clock and the streamed rows. */
internal data class PtsSummaryLayout(
    val rows: List<Row>,
    val entities: List<PtsCopy.Entity>,
    val entityY: Float?,
    val height: Float,
) {
    val revealTotal: Float = PtsFlight.landMs + rows.size * PtsTiming.lineStagger.toFloat() + PtsTiming.lineMs

    enum class Kind { Lead, Title, Body }

    data class Row(
        val text: String,
        val x: Float,
        val y: Float,
        val width: Float,
        val kind: Kind,
        val layout: TextLayoutResult,
        val icon: String? = null,
        val source: Int = 0,
    )
}

@Composable
internal fun rememberPtsSummaryLayout(width: Float, levelIndex: Int): PtsSummaryLayout {
    val density = LocalDensity.current
    val measurer = rememberTextMeasurer()
    return remember(width, levelIndex, density, measurer) {
        val level = PtsCopy.levels[levelIndex.coerceIn(PtsCopy.levels.indices)]
        val rows = mutableListOf<PtsSummaryLayout.Row>()
        val left = PtsLayout.textInset
        val full = width - left * 2f
        var y = 0f
        fun add(text: String, style: TextStyle, x: Float, available: Float, kind: PtsSummaryLayout.Kind, icon: String? = null, source: Int = 0) {
            val wrapped = measurer.measure(
                AnnotatedString(text), style,
                constraints = Constraints(maxWidth = (available * density.density).roundToInt().coerceAtLeast(1)),
            )
            for (index in 0 until wrapped.lineCount) {
                val line = text.substring(wrapped.getLineStart(index), wrapped.getLineEnd(index)).trimEnd()
                val layout = measurer.measure(
                    AnnotatedString(line), style, maxLines = 1,
                    constraints = Constraints(maxWidth = ((available + 8f) * density.density).roundToInt().coerceAtLeast(1)),
                )
                rows += PtsSummaryLayout.Row(line, x, y, available, kind, layout, if (index == 0) icon else null, source)
                y += style.lineHeight.value
            }
        }
        add(level.lead, if (level.tldr) PtsType.tldr else PtsType.lead, left, full, PtsSummaryLayout.Kind.Lead)
        val said = buildString {
            append(level.lead)
            level.points.forEach { append(' ').append(PtsCopy.items[it].body) }
        }
        val entities = PtsCopy.entities.filter { said.contains(it.term) }
        val entityY = if (entities.isNotEmpty()) {
            y += PtsLayout.entityGap
            val top = y
            y += PtsLayout.entityHeight
            top
        } else {
            null
        }
        level.points.map(PtsCopy.items::get).forEachIndexed { index, item ->
            y += if (index == 0 && entityY == null) PtsLayout.leadGap else PtsLayout.itemGap
            val x = left + PtsLayout.itemIndent
            val available = full - PtsLayout.itemIndent
            add(item.title, PtsType.itemTitle, x, available, PtsSummaryLayout.Kind.Title, item.icon, item.source)
            add(item.body, PtsType.itemBody, x, available, PtsSummaryLayout.Kind.Body, source = item.source)
        }
        PtsSummaryLayout(rows, entities, entityY, y)
    }
}
