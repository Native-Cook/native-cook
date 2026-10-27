package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import android.os.Build
import androidx.compose.foundation.Image
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.snapping.SnapLayoutInfoProvider
import androidx.compose.foundation.gestures.snapping.SnapPosition
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.layer.drawLayer
import androidx.compose.ui.graphics.rememberGraphicsLayer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

/** The layout coordinates the model needs to measure where a card is (`measureInWindow` in the React Native block). */
internal class FolioFrames {
    var root: LayoutCoordinates? = null
    var carousel: LayoutCoordinates? = null
    var recent: LayoutCoordinates? = null
    var plus: LayoutCoordinates? = null
}

/** Tells the model when a scroll view is being dragged or flung (`onScrollBeginDrag` / `onMomentumScrollEnd`). */
@Composable
private fun FolioWatchScroll(model: FolioFeatureLibraryModel, key: String, scrolling: () -> Boolean) {
    LaunchedEffect(model, key) {
        var previous = false
        snapshotFlow(scrolling).collect { active ->
            if (active != previous) {
                previous = active
                model.scrollingChanged(key, active)
            }
        }
    }
}

/** The Explore screen: the scrolling home and the floating Home and Explore pill with its plus button. */
@Composable
internal fun FolioExplore(
    model: FolioFeatureLibraryModel,
    assets: FolioFeatureLibraryAssets,
    host: FolioScrollHost,
    frames: FolioFrames,
    blurSource: FolioBlurSource,
) {
    val metrics = model.metrics
    val density = LocalDensity.current
    val visible = model.exploreVisible
    val categoryScroll = rememberScrollState()
    FolioWatchScroll(model, "outer") { host.outer.isScrollInProgress }
    FolioWatchScroll(model, "carousel") { host.carousel.isScrollInProgress }
    FolioWatchScroll(model, "categories") { categoryScroll.isScrollInProgress }

    FolioHitGate(interactive = visible, hidden = !visible) {
        FolioProgressiveBlurViewport(
            enabled = visible,
            overflowing = { host.outer.maxValue > 4f * density.density },
            backgroundColor = FolioScreenBg,
            modifier = Modifier
                .fillMaxSize()
                .onGloballyPositioned { blurSource.viewportInWindow = it.positionInWindow() },
            contentLayer = blurSource.layer,
            alwaysRecord = true,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(host.outer)
                    .padding(top = (metrics.top - 14f).coerceAtLeast(0f).dp, bottom = (metrics.bottom + 110f).dp),
            ) {
                FolioSectionTitle("Featured", top = 22f)
                FolioCarousel(model, assets, host, frames)
                FolioSectionTitle("Invites", top = 22f)
                FolioInvite(model, assets)
                FolioSectionRow("Popular", top = 26f)
                FolioCategoryChips(model, categoryScroll)
                Column(Modifier.padding(horizontal = 19.dp)) {
                    FolioApps.forEachIndexed { index, app ->
                        val shown = model.activeCategory == "Health & Fitness" ||
                            (index + FolioCategories.indexOf(model.activeCategory)) % 2 == 0
                        if (shown) FolioAppRow(app, assets) { model.showApp(app) }
                    }
                }
                FolioSectionRow("Recently added", top = 14f)
                Column(
                    Modifier
                        .padding(horizontal = 19.dp)
                        .onGloballyPositioned { frames.recent = it },
                ) {
                    FolioRecent.forEach { app ->
                        FolioAppRow(app, assets) { model.openFeature(0, FolioReturnOrigin.Recent) }
                    }
                }
            }
        }
    }
    FolioFloatingNav(model, frames, blurSource)
}

/** `sectionTitle`: 22 sp, weight 600, margins 22 above and 14 below. */
@Composable
private fun FolioSectionTitle(text: String, top: Float) {
    FolioText(
        text = text,
        size = 22f,
        color = FolioInk,
        weight = FontWeight.SemiBold,
        letterSpacing = -0.4f,
        maxScale = 1.7f,
        modifier = Modifier.padding(start = 19.dp, end = 19.dp, top = top.dp, bottom = 14.dp),
    )
}

/** `sectionRow`: a title with a round chevron at the right (Popular, Recently added). */
@Composable
private fun FolioSectionRow(text: String, top: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 19.dp, end = 19.dp, top = top.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FolioText(
            text = text,
            size = 22f,
            color = FolioInk,
            weight = FontWeight.SemiBold,
            letterSpacing = -0.4f,
            maxScale = 1.7f,
        )
        Box(
            modifier = Modifier
                .size(34.dp)
                .folioShadow(FolioShadowChevron) { 17f }
                .clip(CircleShape)
                .background(FolioWhite),
            contentAlignment = Alignment.Center,
        ) {
            FolioIconView(FolioIcon.ChevronForward, 16f, Color(0xFF3A3A3A))
        }
    }
}

@Composable
private fun FolioCarousel(
    model: FolioFeatureLibraryModel,
    assets: FolioFeatureLibraryAssets,
    host: FolioScrollHost,
    frames: FolioFrames,
) {
    val cardWidth = model.metrics.cardWidth
    val snapProvider = remember(host.carousel) { SnapLayoutInfoProvider(host.carousel, SnapPosition.Start) }
    val snapFling = rememberSnapFlingBehavior(snapProvider)
    LazyRow(
        state = host.carousel,
        modifier = Modifier
            .fillMaxWidth()
            .onGloballyPositioned { frames.carousel = it },
        contentPadding = PaddingValues(start = 19.dp, end = 19.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        flingBehavior = snapFling,
    ) {
        itemsIndexed(FolioFeatures) { index, feature ->
            FolioPressable(
                onClick = { model.openFeature(index) },
                scale = 0.98f,
                reduced = model.reduced,
                label = "Open ${feature.name}",
                modifier = Modifier.folioFeatureCardFrame(cardWidth),
            ) {
                FolioFeatureCardContent(feature, assets)
            }
        }
    }
}

/** `featureCard`: 330 dp tall, radius 28, white, with the card shadow. */
internal fun Modifier.folioFeatureCardFrame(cardWidth: Float): Modifier = this
    .width(cardWidth.dp)
    .height(330.dp)
    .folioShadow(FolioCardShadow) { 28f }
    .clip(RoundedCornerShape(28.dp))
    .background(FolioWhite)

/** The artwork, title and body of a feature card; the bridge card of the opening animation draws the same. */
@Composable
internal fun FolioFeatureCardContent(feature: FolioFeature, assets: FolioFeatureLibraryAssets) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Image(
            bitmap = assets.art(feature.art),
            contentDescription = null,
            modifier = Modifier.size(172.dp),
            contentScale = ContentScale.Fit,
        )
        FolioText(
            text = feature.title,
            size = 20f,
            lineHeight = 23f,
            color = feature.ink,
            weight = FontWeight.SemiBold,
            align = TextAlign.Center,
            letterSpacing = -0.4f,
            maxScale = 1.5f,
            modifier = Modifier.padding(top = 6.dp),
        )
        FolioText(
            text = feature.body,
            size = 14f,
            lineHeight = 17f,
            color = FolioInkSoft,
            align = TextAlign.Center,
            maxLines = 3,
            modifier = Modifier.padding(top = 10.dp),
        )
    }
}

@Composable
private fun FolioInvite(model: FolioFeatureLibraryModel, assets: FolioFeatureLibraryAssets) {
    FolioPressable(
        onClick = { model.showInvite() },
        scale = 0.98f,
        reduced = model.reduced,
        modifier = Modifier
            .padding(horizontal = 19.dp)
            .fillMaxWidth()
            .folioShadow(FolioCardShadow) { 26f }
            .clip(RoundedCornerShape(26.dp))
            .background(FolioWhite)
            .padding(start = 22.dp, end = 14.dp, top = 26.dp, bottom = 26.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Column(Modifier.weight(1f)) {
                FolioText(
                    text = "You’ve got 1 Invite!",
                    size = 20f,
                    color = FolioInk,
                    weight = FontWeight.SemiBold,
                    letterSpacing = -0.4f,
                    maxScale = 1.6f,
                )
                FolioText(
                    text = "Great apps start with great people. Invite friends with taste who can help improve Folio.",
                    size = 13f,
                    lineHeight = 16f,
                    color = FolioInkBody,
                    modifier = Modifier.padding(top = 10.dp),
                )
            }
            Box(Modifier.size(width = 92.dp, height = 76.dp)) {
                FolioAvatar(assets, 0, size = 40f, x = 0f, y = 6f)
                FolioAvatar(assets, 1, size = 46f, x = 34f, y = 0f)
                FolioAvatar(assets, 2, size = 44f, x = 16f, y = 32f)
                Box(
                    modifier = Modifier
                        .offset(x = 50.dp, y = 54.dp)
                        .size(20.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF8A8794))
                        .border(2.dp, FolioWhite, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    FolioIconView(FolioIcon.Add, 12f, FolioWhite)
                }
            }
        }
    }
}

@Composable
private fun FolioAvatar(assets: FolioFeatureLibraryAssets, index: Int, size: Float, x: Float, y: Float) {
    Image(
        bitmap = assets.avatars[index],
        contentDescription = null,
        modifier = Modifier
            .offset(x = x.dp, y = y.dp)
            .size(size.dp)
            .clip(CircleShape)
            .background(Color(0xFFE4E4E4))
            .border(2.dp, FolioWhite, CircleShape),
        contentScale = ContentScale.Crop,
    )
}

@Composable
private fun FolioCategoryChips(model: FolioFeatureLibraryModel, scroll: ScrollState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(scroll)
            .padding(start = 19.dp, end = 19.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        FolioCategories.forEach { category ->
            val active = model.activeCategory == category
            FolioPressable(
                onClick = { model.selectCategory(category) },
                scale = 1f,
                label = category,
                modifier = Modifier
                    .heightIn(min = 36.dp)
                    .folioShadow(FolioShadowCategory) { 18f }
                    .clip(RoundedCornerShape(18.dp))
                    .background(if (active) Color(0xFF050505) else Color(0xFFFAFAFA))
                    .padding(horizontal = 12.dp),
            ) {
                FolioText(
                    text = category,
                    size = 14f,
                    color = if (active) FolioWhite else FolioInk,
                    weight = FontWeight.Medium,
                    modifier = Modifier.align(Alignment.Center).padding(vertical = 8.dp),
                )
            }
        }
    }
}

/** `AppRow`: a 64 dp round icon with name, handle and a two-line description; it dims while pressed. */
@Composable
private fun FolioAppRow(app: FolioApp, assets: FolioFeatureLibraryAssets, onClick: () -> Unit) {
    FolioPressable(
        onClick = onClick,
        scale = 1f,
        pressedAlpha = 0.6f,
        modifier = Modifier.fillMaxWidth().heightIn(min = 79.dp),
    ) {
        Row(
            modifier = Modifier.align(Alignment.CenterStart),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                bitmap = assets.icons[app.icon],
                contentDescription = null,
                modifier = Modifier.size(64.dp).clip(CircleShape),
                contentScale = ContentScale.Crop,
            )
            Column(Modifier.weight(1f)) {
                FolioText(text = app.name, size = 16f, color = FolioInk, weight = FontWeight.Medium, maxLines = 1)
                FolioText(text = app.handle, size = 13f, color = FolioInkSoft, modifier = Modifier.padding(top = 1.dp))
                FolioText(
                    text = app.body,
                    size = 14f,
                    lineHeight = 17f,
                    color = FolioInkSoft,
                    maxLines = 2,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
    }
}

/**
 * The floating Home and Explore pill and the plus button, 14 dp above the bottom inset. The pill is a
 * frosted glass over the list: a blurred copy of the list's recorded content (API 31+), white at 72%.
 */
@Composable
private fun FolioFloatingNav(model: FolioFeatureLibraryModel, frames: FolioFrames, blurSource: FolioBlurSource) {
    val visible = model.exploreVisible
    val navAlpha = if (model.screen == FolioScreen.Explore) 1f else 0f
    FolioHitGate(interactive = visible, hidden = !visible, modifier = Modifier.graphicsLayer { alpha = navAlpha }) {
        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .offset(y = (-(model.metrics.bottom - 14f)).dp)
                .padding(horizontal = 20.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            FolioNavPill(model, blurSource)
            FolioPressable(
                onClick = { model.openFeature(0, FolioReturnOrigin.Plus) },
                scale = 1f,
                reduced = model.reduced,
                label = "Create a collection",
                modifier = Modifier
                    .size(56.dp)
                    .folioShadow(FolioShadowPlus) { 28f }
                    .clip(CircleShape)
                    .background(Color(0xFF333333))
                    .onGloballyPositioned { frames.plus = it },
            ) {
                Box(Modifier.align(Alignment.Center)) { FolioIconView(FolioIcon.Add, 26f, FolioWhite) }
            }
        }
    }
}

@Composable
private fun FolioNavPill(model: FolioFeatureLibraryModel, blurSource: FolioBlurSource) {
    val blurLayer = rememberGraphicsLayer()
    var pillInWindow by remember { mutableStateOf(Offset.Zero) }
    val shape = RoundedCornerShape(30.dp)
    Row(
        modifier = Modifier
            .folioShadow(FolioShadowNavPill) { 30f }
            .clip(shape)
            .onGloballyPositioned { pillInWindow = it.positionInWindow() }
            .drawBehind {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // expo-blur intensity 40: radius 40 / 4 px, white tint at 40 / 128.
                    val offset = pillInWindow - blurSource.viewportInWindow
                    blurLayer.renderEffect = BlurEffect(10f, 10f, TileMode.Mirror)
                    blurLayer.record { translate(-offset.x, -offset.y) { drawLayer(blurSource.layer) } }
                    drawLayer(blurLayer)
                    drawRect(Color(249, 249, 249, 79))
                }
            }
            .background(Color(0xB8FFFFFF))
            .border(1.dp, Color(0xE6FFFFFF), shape)
            .padding(5.dp),
    ) {
        FolioPressable(
            onClick = { model.scrollHome() },
            scale = 1f,
            reduced = model.reduced,
            label = "Home",
            modifier = Modifier
                .widthIn(min = 92.dp)
                .height(52.dp)
                .clip(RoundedCornerShape(26.dp)),
        ) {
            Box(
                Modifier
                    .align(Alignment.CenterStart)
                    .width(40.dp)
                    .fillMaxSize()
                    .background(Brush.horizontalGradient(listOf(Color(0xFF9FD8FF), Color(0x00D9F0FF))), RoundedCornerShape(26.dp)),
            )
            FolioText(
                text = "Home",
                size = 15f,
                color = Color(0xFF333333),
                weight = FontWeight.Normal,
                modifier = Modifier.align(Alignment.Center),
            )
        }
        Box(
            modifier = Modifier
                .widthIn(min = 92.dp)
                .height(52.dp)
                .folioShadow(FolioShadowNavSelected) { 26f }
                .clip(RoundedCornerShape(26.dp))
                .background(FolioWhite),
            contentAlignment = Alignment.Center,
        ) {
            FolioText(
                text = "Explore",
                size = 15f,
                color = FolioInk,
                weight = FontWeight.Medium,
            )
        }
    }
}
