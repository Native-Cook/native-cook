package com.ngocdevv.nativecook.blocks.sheets.sheetcardhandoff

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.LinearGradientShader
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

enum class TaskStatus { Running, Later }

/**
 * @param status Running (green dot) or later today (white ring).
 * @param progress How far through today's session it is, 0 to 1: the dashes under the time fill up to it.
 */
data class Task(
    val name: String,
    val caption: String,
    val time: String,
    val color: Color,
    val status: TaskStatus = TaskStatus.Running,
    val progress: Float = 0.35f,
)

internal val CARD_HEIGHT = 112.dp
private val RUNNING = Color(0xFF3DDC6C)
private const val DASHES = 14

/** The task as a card: what the list shows, and what the flying card turns into. */
@Composable
internal fun TaskCard(task: Task, width: Dp, modifier: Modifier = Modifier) {
    val running = task.status == TaskStatus.Running
    val filled = Math.round(task.progress * DASHES)
    Column(
        modifier
            .requiredWidth(width)
            .height(CARD_HEIGHT)
            .clip(RoundedCornerShape(20.dp))
            .background(task.color)
            // A light from the top left, as on a printed card.
            .background(LIGHT)
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 14.dp),
    ) {
        Text(task.name, color = Color.White, fontSize = 19.sp, lineHeight = 24.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(
            task.caption,
            modifier = Modifier.padding(top = 3.dp),
            color = Color.White.copy(alpha = 0.86f),
            fontSize = 12.5.sp,
            lineHeight = 16.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(
            Modifier.weight(1f).fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Column {
                Text(task.time, color = Color.White, fontSize = 12.sp, lineHeight = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.2.sp)
                Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                    repeat(DASHES) { index ->
                        Box(
                            Modifier
                                .size(width = 6.dp, height = 2.dp)
                                .clip(RoundedCornerShape(1.dp))
                                .background(Color.White.copy(alpha = if (index < filled) 1f else 0.38f)),
                        )
                    }
                }
            }
            Row(
                Modifier
                    .height(22.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(Color.Black.copy(alpha = 0.12f))
                    .padding(horizontal = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .then(if (running) Modifier.background(RUNNING) else Modifier.border(1.5.dp, Color.White, CircleShape)),
                )
                Text(if (running) "RUNNING" else "LATER", color = Color.White, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.6.sp)
            }
        }
    }
}

// Top left to 60 % across the bottom: white at the start, clear by the middle, a touch of shade at the end.
private val LIGHT = object : ShaderBrush() {
    override fun createShader(size: Size) = LinearGradientShader(
        from = Offset.Zero,
        to = Offset(size.width * 0.6f, size.height),
        colors = listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0f), Color.Black.copy(alpha = 0.06f)),
        colorStops = listOf(0f, 0.55f, 1f),
    )
}
