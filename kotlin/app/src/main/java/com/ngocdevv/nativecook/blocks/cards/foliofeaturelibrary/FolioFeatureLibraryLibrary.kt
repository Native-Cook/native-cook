package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.min
import kotlin.math.roundToInt

/** The Library screen: the header card, the book list under it, the bar with the status pill, and Generate New. */
@Composable
internal fun FolioLibrary(
    model: FolioFeatureLibraryModel,
    assets: FolioFeatureLibraryAssets,
    host: FolioScrollHost,
) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val visible = model.libraryVisible
    FolioHitGate(interactive = visible, hidden = !visible) {
        // `libraryStyle`: the whole library recedes to the left by 22% of the width while the detail pushes in.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val progress = model.detailProgress.value(clock.frame.doubleValue)
                    translationX = if (model.reduced) 0f else -progress * metrics.width * 0.22f * unit
                },
        ) {
            // `listShift`: the list rides up behind the header card while it contracts.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val now = clock.frame.doubleValue
                        val hc = model.headerCollapse.value(now)
                        val eased = if (model.reduced) (if (hc > 0.5f) 1f else 0f) else EaseOutStrong.transform(hc)
                        val tileTop = metrics.top - 6f
                        translationY = -(model.introSize - (tileTop + FOLIO_COLLAPSED_TILE)) * eased * unit
                    },
            ) {
                FolioProgressiveBlurViewport(
                    enabled = visible,
                    overflowing = { host.library.maxValue > 4f * unit },
                    backgroundColor = FolioSurfaceBg,
                    modifier = Modifier.fillMaxSize(),
                ) {
                    FolioLibraryList(model, assets, host)
                }
            }
            FolioHeaderCard(model, assets)
            FolioBar(
                model = model,
                backLabel = "Back to Explore",
                backIcon = FolioIcon.ChevronBack,
                onBack = { model.closeLibrary() },
                trailingLabel = "Share collection",
                trailingIcon = FolioIcon.ShareOutline,
                onTrailing = { model.showShare() },
                trailingIconSize = 20f,
                animatedStatus = true,
                modifier = Modifier.align(Alignment.TopStart),
            )
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(bottom = (metrics.bottom + 14f).dp),
                contentAlignment = Alignment.Center,
            ) {
                FolioButton(
                    label = "Generate New",
                    onClick = { model.openTopics() },
                    reduced = model.reduced,
                    pill = true,
                    icon = FolioButtonIcon.Add,
                )
            }
        }
    }
}

@Composable
private fun FolioLibraryList(
    model: FolioFeatureLibraryModel,
    assets: FolioFeatureLibraryAssets,
    host: FolioScrollHost,
) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(host.library)
            .heightIn(min = (metrics.height + model.introSize).dp)
            .padding(start = 18.dp, end = 18.dp, top = (model.introSize + 18f).dp, bottom = (130f + metrics.bottom).dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(start = 2.dp, end = 2.dp, bottom = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolioText(
                text = "FILTER LIBRARY",
                size = 12f,
                color = Color(0xFF7A7A7A),
                weight = FontWeight.Medium,
                letterSpacing = 0.8f,
            )
            Row(
                modifier = Modifier
                    .height(32.dp)
                    .folioShadow(FolioShadowAllBooks) { 16f }
                    .clip(RoundedCornerShape(16.dp))
                    .background(FolioWhite)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolioText(text = "All Books", size = 14f, color = FolioInkBar, weight = FontWeight.Medium)
                FolioIconView(FolioIcon.ChevronDown, 15f, Color(0xFF2A2A2A))
            }
        }
        if (model.generated) {
            // FadeInDown (420 ms, strong ease-out) for the new book, and the other card slides down under it (320 ms).
            Box(
                Modifier.graphicsLayer {
                    val p = enteringProgress(model, clock.frame.doubleValue, 0.42)
                    alpha = p
                    translationY = -14f * (1f - p) * unit
                },
            ) {
                val ritualsBook = folioBook(FolioBookId.Rituals)
                FolioBookCard(
                    model = model,
                    label = "Read Small Rituals",
                    onClick = { model.openDetail(FolioBookId.Rituals) },
                    name = ritualsBook.title,
                    nameLines = Int.MAX_VALUE,
                    author = ritualsBook.author,
                    blurb = ritualsBook.blurb,
                    cover = {
                        if (model.coverReady) {
                            Box(
                                Modifier.graphicsLayer {
                                    alpha = folioFadeIn(clock.frame.doubleValue, model.coverReadyAt, 0.0, 0.36, model.reduced)
                                },
                            ) {
                                FolioCover(FolioBookId.Rituals, large = false, assets = assets)
                            }
                        } else {
                            FolioCoverPlaceholder(model)
                        }
                    },
                )
            }
        }
        val noticingBook = folioBook(FolioBookId.Noticing)
        Box(
            Modifier.graphicsLayer {
                if (model.generated && !model.reduced) {
                    val p = EaseOutStrong.transform(
                        folioClamp01(((clock.frame.doubleValue - model.generatedAt) / 0.32).toFloat()),
                    )
                    translationY = -FOLIO_BOOK_SLOT * (1f - p) * unit
                }
            },
        ) {
            FolioBookCard(
                model = model,
                label = "Read A Brief Tour of Almost Everything",
                onClick = { model.openDetail(FolioBookId.Noticing) },
                name = noticingBook.title,
                nameLines = 2,
                author = noticingBook.author,
                blurb = noticingBook.blurb,
                cover = { FolioCover(FolioBookId.Noticing, large = false, assets = assets) },
            )
        }
    }
}

/** The height of a library row plus its 12 dp margin: how far the other card starts above where it ends up. */
private const val FOLIO_BOOK_SLOT = 141f

private fun enteringProgress(model: FolioFeatureLibraryModel, now: Double, seconds: Double): Float {
    if (model.reduced) return 1f
    return EaseOutStrong.transform(folioClamp01(((now - model.generatedAt) / seconds).toFloat()))
}

@Composable
private fun FolioBookCard(
    model: FolioFeatureLibraryModel,
    label: String,
    onClick: () -> Unit,
    name: String,
    nameLines: Int,
    author: String,
    blurb: String,
    cover: @Composable () -> Unit,
) {
    Box(Modifier.padding(bottom = 12.dp)) {
        FolioPressable(
            onClick = onClick,
            scale = 0.98f,
            reduced = model.reduced,
            label = label,
            modifier = Modifier
                .fillMaxWidth()
                .folioShadow(FolioCardShadow) { 20f }
                .clip(RoundedCornerShape(20.dp))
                .background(FolioWhite)
                .padding(12.dp),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.Top) {
                cover()
                Column(Modifier.weight(1f).padding(top = 4.dp)) {
                    FolioText(
                        text = name,
                        size = 16f,
                        lineHeight = 20f,
                        color = FolioInk,
                        weight = FontWeight.Medium,
                        maxLines = nameLines,
                    )
                    FolioText(text = author, size = 13f, color = FolioInkSoft, modifier = Modifier.padding(top = 3.dp))
                    FolioText(
                        text = blurb,
                        size = 13f,
                        lineHeight = 16f,
                        color = FolioInkSoft,
                        maxLines = 3,
                        modifier = Modifier.padding(top = 10.dp),
                    )
                }
            }
        }
    }
}

/** `coverPlaceholder`: a grey cover with a light band sweeping across it every 0.9 s while the cover is made. */
@Composable
private fun FolioCoverPlaceholder(model: FolioFeatureLibraryModel) {
    val clock = LocalFolioClock.current
    val unit = LocalDensity.current.density
    Box(
        Modifier
            .size(width = 74.dp, height = 105.dp)
            .clip(RoundedCornerShape(6.dp))
            .background(Color(0xFFD6D6D6)),
    ) {
        Box(
            Modifier
                .fillMaxHeight()
                .width(60.dp)
                .graphicsLayer {
                    alpha = if (model.reduced) 0f else 0.5f
                    translationX = folioInterpolate(
                        model.shimmer(clock.frame.doubleValue),
                        floatArrayOf(0f, 1f),
                        floatArrayOf(-100f, 160f),
                    ) * unit
                }
                .background(Brush.horizontalGradient(listOf(Color(0x00FFFFFF), Color(0xCCFFFFFF), Color(0x00FFFFFF)))),
        )
    }
}

/**
 * `headerCard` with its orb and content: the full app card shrinks into a small tile around its orb, which
 * stays where it is, while the list rides up behind it. All of it is a function of `collapse` (0 to 1).
 */
@Composable
private fun FolioHeaderCard(model: FolioFeatureLibraryModel, assets: FolioFeatureLibraryAssets) {
    val clock = LocalFolioClock.current
    val metrics = model.metrics
    val unit = LocalDensity.current.density
    val full = metrics.width - 20f
    val tileTop = metrics.top - 6f
    val feature = FolioFeatures[model.feature]
    Box(
        modifier = Modifier
            .fillMaxSize()
            .layout { measurable, constraints ->
                val k = model.headerK(clock.frame.doubleValue)
                val w = full + (FOLIO_COLLAPSED_TILE - full) * k
                val h = model.introSize + (FOLIO_COLLAPSED_TILE - model.introSize) * k
                val placeable = measurable.measure(
                    Constraints.fixed((w * unit).roundToInt(), (h * unit).roundToInt()),
                )
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.place(
                        ((metrics.width - w) / 2f * unit).roundToInt(),
                        (tileTop * k * unit).roundToInt(),
                    )
                }
            },
    ) {
        // The card itself is the inner box; the outer one only positions it.
        Box(
            Modifier
                .fillMaxSize()
                .folioShadow(FolioCardShadow) { 30f - 8f * model.headerK(clock.frame.doubleValue) }
                .graphicsLayer {
                    shape = RoundedCornerShape((30f - 8f * model.headerK(clock.frame.doubleValue)).dp)
                    clip = true
                }
                .background(FolioWhite),
        ) {
            Image(
                bitmap = assets.book,
                contentDescription = null,
                modifier = Modifier
                    .offset {
                        val k = model.headerK(clock.frame.doubleValue)
                        val w = full + (FOLIO_COLLAPSED_TILE - full) * k
                        IntOffset(
                            ((w - 60f) / 2f * unit).roundToInt(),
                            ((metrics.top + 2f - tileTop * k) * unit).roundToInt(),
                        )
                    }
                    .size(60.dp),
                contentScale = ContentScale.Fit,
            )
            val accessible = model.introAccessible && model.libraryVisible
            FolioHitGate(
                interactive = accessible,
                hidden = !model.introAccessible,
                fill = false,
                modifier = Modifier.wrapContentSize(Alignment.TopStart, unbounded = true),
            ) {
                Column(
                    modifier = Modifier
                        .width(full.dp)
                        .graphicsLayer {
                            val c = model.collapse(clock.frame.doubleValue)
                            alpha = 1f - min(1f, c * 2.6f)
                            translationY = -14f * c * unit
                        }
                        .onSizeChanged { size ->
                            val h = ceil(size.height / unit)
                            if (abs(h - model.introSize) > 1f) model.introSize = h
                        }
                        .padding(start = 18.dp, end = 18.dp, top = (metrics.top + 76f).dp, bottom = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        FolioChip {
                            Image(
                                bitmap = assets.avatars[0],
                                contentDescription = null,
                                modifier = Modifier.size(18.dp).clip(CircleShape),
                                contentScale = ContentScale.Crop,
                            )
                            FolioText(text = "mirawells", size = 13f, color = FolioInkBar, weight = FontWeight.Medium)
                        }
                        FolioChip {
                            FolioIconView(FolioIcon.GlobeOutline, 15f, Color(0xFF2A2A2A))
                            FolioText(text = "Public", size = 13f, color = FolioInkBar, weight = FontWeight.Medium)
                        }
                    }
                    FolioText(
                        text = feature.name,
                        size = 20f,
                        color = FolioInk,
                        weight = FontWeight.SemiBold,
                        letterSpacing = -0.3f,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 18.dp),
                    )
                    FolioText(
                        text = feature.body,
                        size = 13.5f,
                        lineHeight = 17f,
                        color = FolioInkBody,
                        align = TextAlign.Center,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 60.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        FolioButton(
                            label = "Remix",
                            onClick = { model.openTopics() },
                            reduced = model.reduced,
                            dark = true,
                            icon = FolioButtonIcon.Shuffle,
                            modifier = Modifier.weight(1f),
                        )
                        FolioButton(
                            label = if (model.saved) "Added" else "Get",
                            onClick = { model.toggleSaved() },
                            reduced = model.reduced,
                            dark = true,
                            icon = FolioButtonIcon.ArrowDown,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Box(
                        Modifier
                            .padding(top = 8.dp)
                            .size(width = 56.dp, height = 4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(Color(0xFFE4E4E4)),
                    )
                }
            }
        }
    }
}

@Composable
private fun FolioChip(content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .height(30.dp)
            .clip(RoundedCornerShape(15.dp))
            .background(Color(0xFFF5F5F5))
            .padding(horizontal = 9.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

/**
 * The app bar over a screen: a round back button, the status pill in the middle (Creating content,
 * Searching images) and a round button at the right. [animatedStatus] fades the pill in (the library's does).
 */
@Composable
internal fun FolioBar(
    model: FolioFeatureLibraryModel,
    backLabel: String,
    backIcon: FolioIcon,
    onBack: () -> Unit,
    trailingLabel: String,
    trailingIcon: FolioIcon,
    onTrailing: () -> Unit,
    trailingIconSize: Float,
    animatedStatus: Boolean,
    modifier: Modifier = Modifier,
) {
    val status = model.statusText
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = (model.metrics.top + 8f).dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolioCircleButton(backLabel, backIcon, 22f, onBack)
        AnimatedVisibility(
            visible = status != null,
            enter = fadeIn(tween(durationMillis = if (animatedStatus) 200 else 0, easing = FolioQuadInOut)),
            exit = ExitTransition.None,
        ) {
            Row(
                modifier = Modifier.height(30.dp).padding(horizontal = 10.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FolioWorking()
                FolioText(
                    text = status ?: "",
                    size = 15f,
                    color = FolioInkBar,
                    weight = FontWeight.Medium,
                )
            }
        }
        FolioCircleButton(trailingLabel, trailingIcon, trailingIconSize, onTrailing)
    }
}

@Composable
private fun FolioCircleButton(label: String, icon: FolioIcon, iconSize: Float, onClick: () -> Unit) {
    FolioPressable(
        onClick = onClick,
        scale = 1f,
        label = label,
        modifier = Modifier
            .size(46.dp)
            .folioShadow(FolioShadowCircle) { 23f }
            .clip(CircleShape)
            .background(FolioWhite),
    ) {
        Box(Modifier.align(Alignment.Center)) { FolioIconView(icon, iconSize, FolioInkBar) }
    }
}
