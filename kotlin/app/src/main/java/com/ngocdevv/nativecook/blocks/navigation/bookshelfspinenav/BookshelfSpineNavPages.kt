package com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.navigation.bookshelfspinenav.BookshelfSpineNavMotion.shade
import com.ngocdevv.nativecook.shared.cssBoxShadow
import kotlin.math.max
import kotlin.math.roundToInt

// The two pages above the shelf (BookshelfPages.tsx): the library's home at rest, and a section,
// which the open book becomes. Both are static; only their layers move.

private val Cream = Color(0xFFF4EADB)
private val Muted = Color(0x99F4EADB)
private val Ink = Color(0xFF2B231D)
private val InkSoft = Color(0x9E2B231D)

/** A small drawn cover: two tones, a foil rule and the title. */
@Composable
private fun BookshelfMiniCover(tone: Pair<Long, Long>, label: String, width: Float, height: Float) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        Modifier
            .size(width.dp, height.dp)
            .cssBoxShadow(Color(0x66000000), 1.dp, 2.dp, 3.dp)
            .cssBoxShadow(Color(0x40000000), 6.dp, 14.dp, 3.dp)
            .clip(shape)
            .background(
                Brush.linearGradient(
                    0f to shade(tone.first, 0.12f), 0.45f to Color(tone.first), 1f to Color(tone.second),
                    start = Offset.Zero, end = Offset.Infinite,
                ),
            )
            .drawWithContent {
                drawContent()
                drawRect(Color(0x40000000), size = androidx.compose.ui.geometry.Size(4.dp.toPx(), size.height))
                drawRect(Color(0x99FFECC8), Offset(8.dp.toPx(), 10.dp.toPx()), androidx.compose.ui.geometry.Size(size.width - 14.dp.toPx(), 1.dp.toPx()))
            }
            .padding(6.dp),
        contentAlignment = Alignment.BottomStart,
    ) {
        BasicText(
            label,
            Modifier.padding(start = 3.dp),
            style = TextStyle(color = Color(0xEBFFF0D7), fontFamily = FontFamily.Serif, fontWeight = FontWeight.Bold, fontSize = max(9f, width * 0.16f).sp),
            maxLines = 3,
            overflow = TextOverflow.Clip,
        )
    }
}

@Composable
internal fun BookshelfHomePage(modifier: Modifier = Modifier) {
    Column(modifier.padding(top = 6.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        val card = RoundedCornerShape(22.dp)
        Row(
            Modifier
                .fillMaxWidth()
                .cssBoxShadow(Color(0x59000000), 1.dp, 2.dp, 22.dp)
                .cssBoxShadow(Color(0x4D000000), 14.dp, 32.dp, 22.dp)
                .background(Color(0x0FFFF0DC), card)
                .border(0.5.dp, Color(0x24FFF0DC), card)
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            BookshelfMiniCover(0xFFC96F4A to 0xFF4A2226, "The Salt Orchard", 104f, 152f)
            Column(Modifier.weight(1f).padding(top = 4.dp), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                BasicText("CURRENTLY READING", style = TextStyle(color = Color(0xFFE0B36C), fontSize = 11.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.6.sp))
                BasicText(
                    "The Salt Orchard",
                    Modifier.padding(top = 2.dp),
                    style = TextStyle(color = Cream, fontSize = 21.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                )
                BasicText("Ines Calloway", style = TextStyle(color = Muted, fontSize = 14.sp, fontWeight = FontWeight.Medium))
                Box(
                    Modifier
                        .padding(top = 12.dp)
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color(0x1FFFF0DC)),
                ) {
                    Box(
                        Modifier
                            .fillMaxWidth(0.64f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(Brush.horizontalGradient(listOf(Color(0xFFF0C27E), Color(0xFFD9954B)))),
                    )
                }
                BasicText(
                    "64% · Chapter 11 · 38 min left",
                    Modifier.padding(top = 6.dp),
                    style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"),
                )
                Box(
                    Modifier
                        .padding(top = 10.dp)
                        .height(32.dp)
                        .background(Color(0xFFE8B36A), RoundedCornerShape(16.dp))
                        .padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("Continue", style = TextStyle(color = Color(0xFF2A1808), fontSize = 14.sp, fontWeight = FontWeight.Bold))
                }
            }
        }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for ((n, label) in listOf("12" to "day streak", "23" to "books this year", "4 h" to "this week")) {
                val shape = RoundedCornerShape(16.dp)
                Column(
                    Modifier
                        .weight(1f)
                        .height(72.dp)
                        .background(Color(0x0DFFF0DC), shape)
                        .border(0.5.dp, Color(0x1FFFF0DC), shape)
                        .padding(horizontal = 12.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    BasicText(n, style = TextStyle(color = Cream, fontSize = 22.sp, fontWeight = FontWeight.Bold, fontFeatureSettings = "tnum"))
                    BasicText(label, style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1)
                }
            }
        }
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            BasicText("Sections", style = TextStyle(color = Cream, fontSize = 17.sp, fontWeight = FontWeight.Bold))
            BasicText("Sorted by recent", style = TextStyle(color = Muted, fontSize = 12.sp, fontWeight = FontWeight.Medium))
        }
    }
}

/** A section's page: printed on cream paper in the book's colours. [text] is the opacity of its contents. */
@Composable
internal fun BookshelfSectionPage(book: BookshelfBook, text: () -> Float, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier
            .fillMaxSize()
            .cssBoxShadow(Color(0x66000000), 2.dp, 4.dp, 16.dp)
            .cssBoxShadow(Color(0x66000000), 24.dp, 48.dp, 16.dp)
            .clip(shape)
            .background(Brush.verticalGradient(0f to Color(0xFFFBF6EC), 0.6f to Color(0xFFF4ECDD), 1f to Color(0xFFEADFCB))),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(8.dp)
                .background(Brush.verticalGradient(listOf(shade(book.color, 0.15f), Color(book.color), shade(book.color, -0.3f)))),
        )
        Box(Modifier.width(14.dp).fillMaxHeight().background(Color(0x0F50371E)))
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = text() }
                .padding(top = 24.dp, start = 22.dp, end = 22.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Top) {
                Column {
                    BasicText("SECTION", style = TextStyle(color = shade(book.color, -0.1f), fontSize = 11.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp))
                    BasicText(
                        book.section,
                        Modifier.padding(top = 2.dp),
                        style = TextStyle(color = Ink, fontSize = 30.sp, fontWeight = FontWeight.Bold, fontFamily = FontFamily.Serif),
                    )
                    BasicText(book.blurb, style = TextStyle(color = InkSoft, fontSize = 13.sp, fontWeight = FontWeight.Medium, fontFeatureSettings = "tnum"))
                }
                Box(
                    Modifier
                        .size(44.dp)
                        .cssBoxShadow(Color(0x40000000), 2.dp, 6.dp, 22.dp)
                        .background(Color(book.color), CircleShape)
                        .drawWithContent {
                            drawContent()
                            drawArc(
                                Color(0x33FFFFFF), 200f, 140f, false,
                                topLeft = Offset(0.5.dp.toPx(), 0.5.dp.toPx()),
                                size = androidx.compose.ui.geometry.Size(size.width - 1.dp.toPx(), size.height - 1.dp.toPx()),
                                style = androidx.compose.ui.graphics.drawscope.Stroke(1.dp.toPx()),
                            )
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(
                        book.count.toString(),
                        style = TextStyle(color = Color(book.foil), fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, fontFeatureSettings = "tnum"),
                    )
                }
            }
            Box(Modifier.fillMaxWidth().height(1.dp).background(Color(0x292B231D)))
            for (row in book.rows) {
                Row(Modifier.fillMaxWidth().height(62.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    BookshelfMiniCover(row.tone, row.title.replace("“", "").replace("”", ""), 40f, 56f)
                    Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(row.title, style = TextStyle(color = Ink, fontSize = 15.sp, fontWeight = FontWeight.Bold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        BasicText(row.author, style = TextStyle(color = InkSoft, fontSize = 12.sp, fontWeight = FontWeight.Medium), maxLines = 1, overflow = TextOverflow.Ellipsis)
                        if (row.progress > 0f && row.progress < 1f) {
                            Box(
                                Modifier
                                    .padding(top = 6.dp)
                                    .fillMaxWidth()
                                    .height(4.dp)
                                    .clip(RoundedCornerShape(2.dp))
                                    .background(Color(0x1A2B231D)),
                            ) {
                                Box(
                                    Modifier
                                        .fillMaxWidth((row.progress * 100f).roundToInt() / 100f)
                                        .fillMaxHeight()
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(Color(book.color)),
                                )
                            }
                        } else {
                            BasicText(
                                if (row.progress >= 1f) "Finished" else "Not started",
                                Modifier.padding(top = 4.dp),
                                style = TextStyle(color = InkSoft, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                            )
                        }
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier
                    .align(Alignment.CenterHorizontally)
                    .height(36.dp)
                    .background(Color(0x142B231D), RoundedCornerShape(18.dp))
                    .padding(horizontal = 18.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Back to the shelf", style = TextStyle(color = Ink, fontSize = 14.sp, fontWeight = FontWeight.SemiBold))
            }
        }
    }
}
