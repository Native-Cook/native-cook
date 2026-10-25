package com.ngocdevv.nativecook.blocks.gestures.zipperunlock

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow

/*
 The journal under the denim. The header, the week, three note cards, a memory and the New note
 button; each comes in 40 ms after the one before it (0.36 s ease-out, 18 dp rise, from scale
 0.98), driven by the seconds since the halves started to open, read only in graphicsLayer, so
 nothing here recomposes while it plays.
*/

private class ZipNote(val title: String, val body: String, val meta: String, val tint: Color)

private val NOTES = listOf(
    ZipNote("Things the sea taught me", "Tide pools at seven. Bring the green notebook and the good pencil.", "10:12 · Morning", Color(0xFF3A5A8C)),
    ZipNote("Gift list", "Fountain pen for Mara, a linen scarf, the plum jam from the Saturday market.", "Yesterday · Lists", Color(0xFFC2852F)),
    ZipNote("Half a poem", "Some doors are opened by the hand that waits. Some by the one that knocks twice.", "Thu · Drafts", Color(0xFF6E8B68)),
)

private class ZipDay(val day: String, val num: Int, val notes: Boolean, val today: Boolean = false)

private val WEEK = listOf(
    ZipDay("Mon", 20, true),
    ZipDay("Tue", 21, false),
    ZipDay("Wed", 22, true),
    ZipDay("Thu", 23, true),
    ZipDay("Fri", 24, true),
    ZipDay("Sat", 25, true, today = true),
    ZipDay("Sun", 26, false),
)

private val Paper = Color(0xFFF3EEE4)
private val Ink = Color(0xFF1F2433)
private val InkSoft = Color(31 / 255f, 36 / 255f, 51 / 255f, 0.6f)
private val Brown = Color(0xFF9A6B2F)
private val Navy = Color(0xFF22365E)

private fun Modifier.reveal(since: () -> Float, index: Int) = graphicsLayer {
    val r = zipRevealOf(since(), index)
    alpha = r
    translationY = (1 - r) * 18.dp.toPx()
    scaleX = 0.98f + 0.02f * r
    scaleY = 0.98f + 0.02f * r
}

@Composable
private fun ZipLockGlyph() {
    val arc = remember {
        Path().apply {
            moveTo(6f, 8f)
            lineTo(6f, 5.8f)
            arcTo(androidx.compose.ui.geometry.Rect(6f, 2.8f, 12f, 8.8f), 180f, 180f, false)
            lineTo(12f, 8f)
        }
    }
    Canvas(Modifier.size(18.dp)) {
        val k = size.width / 18f
        scale(k, k, pivot = Offset.Zero) {
            drawRoundRect(Ink, Offset(3.5f, 8f), Size(11f, 8f), CornerRadius(2.2f))
            drawPath(arc, Ink, style = Stroke(width = 1.8f, cap = StrokeCap.Round))
        }
    }
}

@Composable
internal fun ZipperUnlockJournal(since: () -> Float, onLock: () -> Unit) {
    val screen = LocalDensity.current
    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(screen) / screen.density
    val safeBottom = insets.getBottom(screen) / screen.density
    Box(
        Modifier
            .fillMaxSize()
            .background(Brush.verticalGradient(0f to Color(0xFFF6F1E8), 0.5f to Paper, 1f to Color(0xFFE9E1D3))),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = (safeTop + 24).dp, bottom = (safeBottom + 96).dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(Modifier.fillMaxWidth().reveal(since, 0), verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("SAT 25 OCT", style = TextStyle(color = Brown, fontSize = 12.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp))
                    BasicText("Pocket", style = TextStyle(color = Ink, fontSize = 34.sp, lineHeight = 40.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp))
                    BasicText("3 notes · last edit 10:12", style = TextStyle(color = InkSoft, fontSize = 15.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .cssBoxShadow(Color(60 / 255f, 44 / 255f, 20 / 255f, 0.18f), 1.dp, 2.dp, 22.dp)
                        .cssBoxShadow(Color(60 / 255f, 44 / 255f, 20 / 255f, 0.10f), 8.dp, 18.dp, 22.dp)
                        .background(Color(0xFFFBF8F2), CircleShape)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onLock)
                        .semantics { contentDescription = "Lock the journal" },
                    contentAlignment = Alignment.Center,
                ) { ZipLockGlyph() }
            }
            Row(Modifier.fillMaxWidth().reveal(since, 1), horizontalArrangement = Arrangement.SpaceBetween) {
                for (d in WEEK) {
                    val dayShape = RoundedCornerShape(22.dp)
                    Column(
                        Modifier
                            .size(44.dp, 64.dp)
                            .then(
                                if (d.today) {
                                    Modifier
                                        .cssBoxShadow(Color(20 / 255f, 30 / 255f, 60 / 255f, 0.3f), 1.dp, 2.dp, 22.dp)
                                        .cssBoxShadow(Color(20 / 255f, 30 / 255f, 60 / 255f, 0.2f), 8.dp, 18.dp, 22.dp)
                                        .background(Navy, dayShape)
                                } else {
                                    Modifier.background(Color(80 / 255f, 60 / 255f, 30 / 255f, 0.05f), dayShape)
                                },
                            ),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
                    ) {
                        BasicText(d.day, style = TextStyle(color = if (d.today) Paper else InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold))
                        BasicText("${d.num}", style = TextStyle(color = if (d.today) Paper else Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"))
                        Box(
                            Modifier
                                .size(4.dp)
                                .graphicsLayer { alpha = if (d.notes) 1f else 0f }
                                .background(if (d.today) Paper else Color(0xFFC2852F), CircleShape),
                        )
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                NOTES.forEachIndexed { i, n ->
                    val shape = RoundedCornerShape(20.dp)
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .reveal(since, i + 2)
                            .cssBoxShadow(Color(60 / 255f, 44 / 255f, 20 / 255f, 0.14f), 1.dp, 2.dp, 20.dp)
                            .cssBoxShadow(Color(60 / 255f, 44 / 255f, 20 / 255f, 0.10f), 12.dp, 28.dp, 20.dp)
                            .background(Color(0xFFFCFAF5), shape)
                            .border(0.5.dp, Color(80 / 255f, 60 / 255f, 30 / 255f, 0.12f), shape)
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(Modifier.size(8.dp).background(n.tint, CircleShape))
                            BasicText(
                                n.title,
                                modifier = Modifier.weight(1f),
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.SemiBold),
                            )
                        }
                        BasicText(
                            n.body,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            style = TextStyle(color = Color(31 / 255f, 36 / 255f, 51 / 255f, 0.78f), fontSize = 15.sp, lineHeight = 21.sp),
                        )
                        BasicText(n.meta, style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                    }
                }
            }
            val memoryShape = RoundedCornerShape(20.dp)
            Column(
                Modifier
                    .fillMaxWidth()
                    .reveal(since, 5)
                    .background(Color(194 / 255f, 133 / 255f, 47 / 255f, 0.10f), memoryShape)
                    .border(0.5.dp, Color(194 / 255f, 133 / 255f, 47 / 255f, 0.3f), memoryShape)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText("A YEAR AGO TODAY", style = TextStyle(color = Brown, fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.2.sp))
                BasicText(
                    "“Rain on the ferry, and the best soup I have ever had in a paper cup.”",
                    style = TextStyle(color = Ink, fontSize = 16.sp, lineHeight = 22.sp, fontStyle = FontStyle.Italic),
                )
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = (safeBottom + 20).dp)
                .reveal(since, 6),
        ) {
            Row(
                Modifier
                    .height(48.dp)
                    .cssBoxShadow(Color(20 / 255f, 30 / 255f, 60 / 255f, 0.3f), 1.dp, 2.dp, 24.dp)
                    .cssBoxShadow(Color(20 / 255f, 30 / 255f, 60 / 255f, 0.22f), 10.dp, 22.dp, 24.dp)
                    .background(Navy, RoundedCornerShape(24.dp))
                    .padding(horizontal = 24.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                BasicText("+", modifier = Modifier.offset(y = (-2).dp), style = TextStyle(color = Paper, fontSize = 20.sp, fontWeight = FontWeight.Medium))
                BasicText("New note", style = TextStyle(color = Paper, fontSize = 16.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
