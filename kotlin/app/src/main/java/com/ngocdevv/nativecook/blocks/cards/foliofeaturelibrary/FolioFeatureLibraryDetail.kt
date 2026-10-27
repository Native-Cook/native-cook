package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp

/** The Detail screen: a book's cover, title, actions and its three cards, pushed in from the right. */
@Composable
internal fun FolioDetail(
    model: FolioFeatureLibraryModel,
    assets: FolioFeatureLibraryAssets,
    host: FolioScrollHost,
) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val visible = model.detailVisible
    val book = folioBook(model.selectedBookId)
    val bookmarked = model.selectedBookId in model.bookmarks
    FolioHitGate(interactive = visible, hidden = !visible) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = model.detailProgress.value(clock.frame.doubleValue)
                    alpha = if (model.reduced) progress else 1f
                    translationX = if (model.reduced) 0f else (1f - progress) * metrics.width * unit
                }
                .background(FolioSurfaceBg),
        ) {
            FolioProgressiveBlurViewport(
                enabled = visible,
                overflowing = { host.detail.maxValue > 4f * unit },
                backgroundColor = FolioSurfaceBg,
                modifier = Modifier.fillMaxSize(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(host.detail)
                        .padding(
                            start = 18.dp,
                            end = 18.dp,
                            top = (metrics.top + 64f).dp,
                            bottom = (metrics.bottom + 40f).dp,
                        ),
                ) {
                    Box(Modifier.fillMaxWidth().padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                        FolioCover(model.selectedBookId, large = true, assets = assets)
                    }
                    FolioText(
                        text = book.title,
                        size = 27f,
                        lineHeight = 33f,
                        color = FolioInk,
                        align = TextAlign.Center,
                        family = FolioSerif,
                        modifier = Modifier.fillMaxWidth().padding(top = 22.dp),
                    )
                    FolioText(
                        text = book.author,
                        size = 17f,
                        color = FolioInkSoft,
                        align = TextAlign.Center,
                        italic = true,
                        family = FolioSerif,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                    Column(
                        modifier = Modifier.padding(top = 22.dp, bottom = 30.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                    ) {
                        FolioButton(
                            label = if (bookmarked) "Saved to Favorites" else "Save to Favorites",
                            onClick = { model.toggleBookmark() },
                            reduced = model.reduced,
                            secondary = true,
                            icon = if (bookmarked) FolioButtonIcon.Heart else FolioButtonIcon.HeartOutline,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        FolioButton(
                            label = "Read",
                            onClick = { model.readBook() },
                            reduced = model.reduced,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val ready = model.detailReady || model.reduced
                    FolioSection("What’s it about?") {
                        if (ready) {
                            FolioFading(model, delay = 0.0, seconds = 0.32) {
                                FolioParagraph(book.summary)
                            }
                        }
                    }
                    FolioSection("What you’ll learn") {
                        if (ready) {
                            FolioFading(model, delay = 1.0, seconds = 0.32) {
                                Column {
                                    book.lessons.forEach { line ->
                                        Row(
                                            modifier = Modifier.padding(top = 12.dp),
                                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                                        ) {
                                            Box(
                                                Modifier
                                                    .padding(top = 8.dp)
                                                    .size(6.dp)
                                                    .background(FolioBlue, CircleShape),
                                            )
                                            FolioText(
                                                text = line,
                                                size = 15f,
                                                lineHeight = 22f,
                                                color = FolioInkText,
                                                modifier = Modifier.weight(1f),
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                    FolioSection("Meet the author") {
                        if (ready) {
                            FolioFading(model, delay = 2.4, seconds = 0.32) {
                                FolioAuthorRow(model, assets, book)
                            }
                        }
                    }
                }
            }
            FolioBar(
                model = model,
                backLabel = "Back to library",
                backIcon = FolioIcon.ChevronBack,
                onBack = { model.backToLibrary() },
                trailingLabel = if (bookmarked) "Remove from favorites" else "Save to favorites",
                trailingIcon = FolioIcon.ChatbubbleOutline,
                onTrailing = { model.toggleBookmark() },
                trailingIconSize = 19f,
                animatedStatus = false,
                modifier = Modifier.align(Alignment.TopStart),
            )
        }
    }
}

/** `section`: a white card, 22 dp radius, at least 96 dp tall, with a title and its content. */
@Composable
private fun FolioSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 14.dp)
            .folioShadow(FolioShadowSection) { 22f }
            .clip(RoundedCornerShape(22.dp))
            .background(FolioWhite)
            .heightIn(min = 96.dp)
            .padding(18.dp),
    ) {
        FolioText(
            text = title,
            size = 17f,
            color = FolioInk,
            weight = FontWeight.SemiBold,
            modifier = Modifier.padding(bottom = 4.dp),
        )
        content()
    }
}

/** An entering `FadeIn`: the content is laid out at once and fades in after [delay] seconds, from the moment the text was ready. */
@Composable
private fun FolioFading(
    model: FolioFeatureLibraryModel,
    delay: Double,
    seconds: Double,
    content: @Composable () -> Unit,
) {
    val clock = LocalFolioClock.current
    Box(
        Modifier.graphicsLayer {
            alpha = folioFadeIn(clock.frame.doubleValue, model.detailReadyAt, delay, seconds, model.reduced)
        },
    ) { content() }
}

@Composable
internal fun FolioParagraph(text: String, modifier: Modifier = Modifier) {
    FolioText(
        text = text,
        size = 15f,
        lineHeight = 22f,
        color = FolioInkText,
        modifier = modifier.padding(top = 12.dp),
    )
}

@Composable
private fun FolioAuthorRow(model: FolioFeatureLibraryModel, assets: FolioFeatureLibraryAssets, book: FolioBook) {
    val clock = LocalFolioClock.current
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp), verticalAlignment = Alignment.Top) {
        Box(
            Modifier
                .padding(top = 12.dp)
                .size(60.dp)
                .clip(CircleShape)
                .background(Color(0xFFD4D4D4)),
        ) {
            if (model.authorReady) {
                Image(
                    bitmap = assets.author,
                    contentDescription = null,
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            alpha = folioFadeIn(clock.frame.doubleValue, model.authorReadyAt, 0.0, 0.4, model.reduced)
                        },
                    contentScale = ContentScale.Crop,
                )
            }
        }
        Column(Modifier.weight(1f)) {
            val intro = buildAnnotatedString {
                withStyle(SpanStyle(fontWeight = FontWeight.Bold, color = FolioInkBar)) { append(book.author) }
                append(" is a writer and designer who studies attention, routines and the small things that shape a day.")
            }
            FolioRichText(text = intro, size = 15f, color = FolioInkText, lineHeight = 22f, modifier = Modifier.padding(top = 12.dp))
            FolioParagraph("Her essays on everyday habits are read in classrooms, studios and kitchens, and are known for turning research into steps anyone can try tonight.")
            FolioParagraph("Her work draws on behavioral science and years of designing calm, useful products, but is written in a warm, plain style.")
            FolioParagraph("This original sample is included locally, so your reading stays available offline.")
            if (model.authorReady) {
                Row(
                    modifier = Modifier
                        .padding(top = 14.dp)
                        .height(34.dp)
                        .clip(RoundedCornerShape(17.dp))
                        .border(1.dp, Color(0xFFE2E2E2), RoundedCornerShape(17.dp))
                        .padding(start = 8.dp, end = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).background(FolioInk, CircleShape), contentAlignment = Alignment.Center) {
                        FolioText(
                            text = book.author.split(' ').map { it[0] }.joinToString(""),
                            size = 8f,
                            color = FolioWhite,
                            weight = FontWeight.Bold,
                        )
                    }
                    FolioText(
                        text = "1 SOURCE",
                        size = 12f,
                        color = Color(0xFF6A6A6A),
                        letterSpacing = 0.8f,
                        family = FolioMono,
                    )
                }
            }
        }
    }
}
