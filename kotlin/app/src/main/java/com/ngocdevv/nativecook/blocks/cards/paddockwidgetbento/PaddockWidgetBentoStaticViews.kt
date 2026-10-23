package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowOutward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contrast
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SmartDisplay
import androidx.compose.material.icons.automirrored.filled.LibraryBooks
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.Painter
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import kotlin.math.max


@Composable
internal fun rememberPaddockTextBook(scale: Float, fonts: PaddockFonts): PaddockTextBook {
    val measurer = rememberTextMeasurer(cacheSize = 64)
    val density = LocalDensity.current
    return remember(measurer, density, scale, fonts) { PaddockTextBook(measurer, density, scale, fonts) }
}

/** A symbol centred at (cx, cy) in a [size] square, in tile points. */
internal fun DrawScope.paddockIcon(painter: Painter, cx: Float, cy: Float, size: Float, color: Color, px: Float) {
    translate((cx - size / 2) * px, (cy - size / 2) * px) {
        with(painter) { draw(Size(size * px, size * px), colorFilter = ColorFilter.tint(color)) }
    }
}

private val PressEase = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** A bento tile: its pit-stop pose, a 0.97 press (100 ms in, 180 ms out), and `extras` above the face. */
@Composable
private fun PaddockTile(
    index: Int,
    rect: PaddockTheme.R,
    scale: Float,
    runtime: PaddockWidgetBentoRuntime,
    label: String,
    radius: Float,
    fill: Color?,
    clips: Boolean,
    hero: Boolean,
    onClick: () -> Unit,
    draw: DrawScope.() -> Unit,
    extras: @Composable BoxScope.() -> Unit = {},
) {
    val density = LocalDensity.current.density
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val press by animateFloatAsState(if (pressed) 0.97f else 1f, tween(if (pressed) 100 else 180, easing = PressEase), label = "press")
    Box(
        Modifier.offset((rect.left * scale).dp, (rect.top * scale).dp)
            .requiredSize((rect.width * scale).dp, (rect.height * scale).dp)
            .graphicsLayer {
                val p = PaddockWidgetBentoMotion.pitTile(index, runtime.pit.value)
                val shake = if (hero) PaddockWidgetBentoMotion.slapPose(runtime.peel.value.slap).shake else 0.0
                translationX = ((p.x + shake) * scale * density).toFloat()
                translationY = (p.y * scale * density).toFloat()
                rotationZ = p.rotate.toFloat()
                scaleX = p.sx.toFloat()
                scaleY = p.sy.toFloat()
            },
    ) {
        val shape = RoundedCornerShape((radius * scale).dp)
        Box(
            Modifier.fillMaxSize()
                .graphicsLayer { scaleX = press; scaleY = press }
                .then(if (clips) Modifier.clip(shape) else Modifier)
                .drawBehind {
                    if (fill != null) drawRoundRect(fill, cornerRadius = CornerRadius(radius * scale * density))
                    draw()
                }
                .semantics { contentDescription = label; role = Role.Button }
                .clickable(interaction, indication = null, onClick = onClick),
        )
        extras()
    }
}

@Composable
internal fun PaddockWidgetBentoStaticGrid(
    scale: Float,
    guideVisible: Boolean,
    assets: PaddockWidgetBentoAssets,
    fonts: PaddockFonts,
    runtime: PaddockWidgetBentoRuntime,
    onPeelEnd: () -> Unit,
    onAction: (PaddockWidgetBentoDestination) -> Unit,
) {
    val density = LocalDensity.current.density
    val px = scale * density
    val book = rememberPaddockTextBook(scale, fonts)
    val mic = rememberVectorPainter(Icons.Filled.Mic)
    val arrow = rememberVectorPainter(Icons.Filled.ArrowOutward)
    val headphones = rememberVectorPainter(Icons.Filled.Headphones)
    val watch = rememberVectorPainter(Icons.Filled.SmartDisplay)
    val feed = rememberVectorPainter(Icons.Filled.Podcasts)
    val document = rememberVectorPainter(Icons.Filled.Description)
    val books = rememberVectorPainter(Icons.AutoMirrored.Filled.LibraryBooks)
    val half = rememberVectorPainter(Icons.Filled.Contrast)
    val close = rememberVectorPainter(Icons.Filled.Close)

    // Pitcast
    PaddockTile(
        PaddockWidgetBentoMotion.Tile.pitcast, PaddockTheme.pitcast, scale, runtime, "Read the weekend brief, Alex Vale and Sam Ives", PaddockTheme.radiusCard, PaddockTheme.cream, false, false,
        { onAction(PaddockWidgetBentoDestination.Pitcast) },
        draw = {
            val inset = PaddockTheme.pitcastInset
            drawRoundRect(
                PaddockTheme.orange, Offset(inset * px, inset * px), Size(PaddockTheme.mic * px, PaddockTheme.mic * px), CornerRadius(PaddockTheme.radiusMic * px),
            )
            paddockIcon(mic, inset + PaddockTheme.mic / 2, inset + PaddockTheme.mic / 2, PaddockTheme.pitcastIcon, PaddockTheme.micInk, px)
            val ax = PaddockTheme.pitcast.width - PaddockTheme.pitcastArrowInset - PaddockTheme.arrow
            drawCircle(Color.Black, PaddockTheme.arrow / 2 * px, Offset((ax + PaddockTheme.arrow / 2) * px, (PaddockTheme.pitcastArrowInset + PaddockTheme.arrow / 2) * px))
            paddockIcon(arrow, ax + PaddockTheme.arrow / 2, PaddockTheme.pitcastArrowInset + PaddockTheme.arrow / 2, PaddockTheme.pitcastArrowIcon, Color.White, px)
            with(book) {
                line("pit", inset, PaddockTheme.pitcastTitle, PaddockTheme.pitcastTitleSize, PaddockFace.Black, Color.Black, -0.6f)
                val pitWidth = width("pit", PaddockTheme.pitcastTitleSize, PaddockFace.Black, -0.6f)
                line("cast", inset + pitWidth, PaddockTheme.pitcastTitle, PaddockTheme.pitcastTitleSize, null, Color.Black, -0.8f, serifItalic = true)
                line("Every Friday at 6pm", inset, PaddockTheme.pitcastSubtitle, PaddockTheme.pitcastSubtitleSize, PaddockFace.Medium, Color.Black)
                line("Alex Vale / Sam Ives", inset, PaddockTheme.pitcastNames, PaddockTheme.pitcastNamesSize, PaddockFace.Medium, Color.Black, alpha = 0.9f)
            }
            listOf(headphones, watch, feed).forEachIndexed { i, painter ->
                paddockIcon(painter, PaddockTheme.glyphX[i], PaddockTheme.glyphRow, PaddockTheme.glyph, Color.Black, px)
            }
        },
    )

    // Stats
    PaddockTile(
        PaddockWidgetBentoMotion.Tile.stats, PaddockTheme.stats, scale, runtime, "Race Stats. Switch between latest and season records", PaddockTheme.radiusCard, null, true, false,
        { onAction(PaddockWidgetBentoDestination.Stats) },
        draw = {
            drawRect(
                Brush.linearGradient(
                    listOf(PaddockTheme.statsGlow, PaddockTheme.statsShade),
                    start = Offset(size.width * 0.12f, size.height * 0.85f),
                    end = Offset(size.width, size.height * 0.15f),
                ),
            )
            // Contain the 320 x 330 art in its 106 x 110 box.
            val art = assets.statsStack
            val box = PaddockTheme.statsArt
            val fit = minOf(box.width / art.width, box.height / art.height)
            val w = art.width * fit
            val h = art.height * fit
            drawImage(
                art,
                dstOffset = IntOffset(((box.left + (box.width - w) / 2) * px).toInt(), ((box.top + (box.height - h) / 2) * px).toInt()),
                dstSize = IntSize((w * px).toInt(), (h * px).toInt()),
            )
            paddockIcon(document, PaddockTheme.statsIcon.left + PaddockTheme.statsIcon.width / 2, PaddockTheme.statsIcon.top + PaddockTheme.statsIcon.height / 2, PaddockTheme.statsIconSize, Color.White, px)
            with(book) {
                line("Turn Nine", PaddockTheme.statsTextX, PaddockTheme.statsEyebrow, PaddockTheme.statsEyebrowSize, PaddockFace.Bold, Color.White)
                line("Stats", PaddockTheme.statsTextX, PaddockTheme.statsWord, PaddockTheme.statsWordSize, PaddockFace.Bold, Color.White)
                line("Stats, records & stories", PaddockTheme.statsCaptionX, PaddockTheme.caption[0], PaddockTheme.captionSize, PaddockFace.Medium, Color.White)
                line("from Turn Nine", PaddockTheme.statsCaptionX, PaddockTheme.caption[1], PaddockTheme.captionSize, PaddockFace.Medium, Color.White)
            }
        },
    )

    // Glossary and education
    PaddockTile(
        PaddockWidgetBentoMotion.Tile.glossary, PaddockTheme.glossary, scale, runtime, "Race glossary", PaddockTheme.radiusQuick, PaddockTheme.green, false, false,
        { onAction(PaddockWidgetBentoDestination.Glossary) },
        draw = {
            paddockIcon(books, PaddockTheme.tileMarkCenter, PaddockTheme.glossary.height / 2, PaddockTheme.tileIcon, Color.Black, px)
            with(book) {
                line("Turn Nine", PaddockTheme.tileTextAfterMark, PaddockTheme.eyebrow, PaddockTheme.eyebrowSize, PaddockFace.SemiBold, Color.Black, PaddockTheme.eyebrowTracking)
                line("Glossary", PaddockTheme.tileTextAfterMark, PaddockTheme.title, PaddockTheme.tileTitleSize, PaddockFace.Bold, Color.Black, PaddockTheme.tileTitleTracking)
            }
        },
    )
    PaddockTile(
        PaddockWidgetBentoMotion.Tile.education, PaddockTheme.education, scale, runtime, "Race education", PaddockTheme.radiusQuick, PaddockTheme.blue, false, false,
        { onAction(PaddockWidgetBentoDestination.Education) },
        draw = {
            paddockIcon(half, PaddockTheme.tileMarkCenter, PaddockTheme.education.height / 2, PaddockTheme.tileIcon, PaddockTheme.creamInk, px)
            with(book) {
                line("Turn Nine", PaddockTheme.tileTextAfterMark, PaddockTheme.eyebrow, PaddockTheme.eyebrowSize, PaddockFace.SemiBold, Color.White, PaddockTheme.eyebrowTracking)
                line("Education", PaddockTheme.tileTextAfterMark, PaddockTheme.title, PaddockTheme.tileTitleSize, PaddockFace.Bold, Color.White, PaddockTheme.tileTitleTracking)
            }
        },
    )

    // Hero: the die-cut sticker over the red card, the seal above it, and the corner you can drag.
    PaddockTile(
        PaddockWidgetBentoMotion.Tile.hero, PaddockTheme.hero, scale, runtime, "Exclusive widgets. Open Widget Studio", PaddockTheme.radiusCard, PaddockTheme.red, false, true,
        { onAction(PaddockWidgetBentoDestination.WidgetStudio) },
        draw = {},
        extras = {
            PaddockSticker(scale, fonts, runtime)
            Canvas(
                Modifier.offset(((PaddockTheme.sealCenterX - PaddockTheme.sealSize / 2) * scale).dp, ((PaddockTheme.sealCenterY - PaddockTheme.sealSize / 2) * scale).dp)
                    .requiredSize((PaddockTheme.sealSize * scale).dp),
            ) {
                drawCircle(Color.White)
                drawCircle(PaddockTheme.sealStroke, style = Stroke(1f * density))
                val result = book.layout("TN", PaddockTheme.heroSeal, PaddockFace.Black, -0.4f)
                drawText(
                    result,
                    color = PaddockTheme.red,
                    topLeft = Offset((size.width - result.size.width) / 2, (size.height - result.size.height) / 2),
                )
            }
            var total = Offset.Zero
            Box(
                Modifier.offset((PaddockTheme.peelSpot.left * scale).dp, (PaddockTheme.peelSpot.top * scale).dp)
                    .requiredSize((PaddockTheme.peelSpot.width * scale).dp, (PaddockTheme.peelSpot.height * scale).dp)
                    .pointerInput(scale) {
                        detectDragGestures(
                            onDragStart = { total = Offset.Zero; runtime.peelBegan() },
                            onDrag = { change, delta ->
                                change.consume()
                                total += delta
                                runtime.peelMoved((total.x / px).toDouble(), (total.y / px).toDouble())
                            },
                            onDragEnd = { if (runtime.peelEnded()) onPeelEnd() },
                            onDragCancel = { if (runtime.peelEnded()) onPeelEnd() },
                        )
                    },
            )
        },
    )

    if (guideVisible) {
        PaddockTile(
            PaddockWidgetBentoMotion.Tile.guide, PaddockTheme.guide, scale, runtime, "${"how to move cards?"} Hold a quick card and drag it to a new slot, then tap Done to keep the new order.",
            PaddockTheme.radiusCard, PaddockTheme.guideYellow, false, false,
            { onAction(PaddockWidgetBentoDestination.Guide) },
            draw = {
                with(book) {
                    line("how to move cards?", PaddockTheme.guideInset, PaddockTheme.guideTitle, PaddockTheme.guideTitleSize, PaddockFace.ExtraBold, Color.Black)
                    line("Hold a quick card and drag it to a new slot,", PaddockTheme.guideInset, PaddockTheme.guideHelper[0], PaddockTheme.guideHelperSize, PaddockFace.Medium, Color.Black)
                    line("then tap Done to keep the new order.", PaddockTheme.guideInset, PaddockTheme.guideHelper[1], PaddockTheme.guideHelperSize, PaddockFace.Medium, Color.Black)
                }
            },
            extras = {
                val center = Offset(PaddockTheme.guide.width - PaddockTheme.guideCloseInset - PaddockTheme.closeSize / 2, PaddockTheme.closeCenterY)
                Box(
                    Modifier.offset(((center.x) * scale - 22f).dp, ((center.y) * scale - 22f).dp)
                        .requiredSize(44.dp)
                        .semantics { contentDescription = "Close arrangement guide"; role = Role.Button }
                        .clickable { onAction(PaddockWidgetBentoDestination.CloseGuide) },
                ) {
                    Canvas(Modifier.fillMaxSize()) {
                        drawCircle(PaddockTheme.closeFill, PaddockTheme.closeSize / 2 * px)
                        paddockIcon(close, size.width / 2 / px, size.height / 2 / px, 12f, PaddockTheme.guideYellow, px)
                    }
                }
            },
        )
    }

    // Banner
    PaddockTile(
        PaddockWidgetBentoMotion.Tile.banner, PaddockTheme.banner, scale, runtime, "Your race-day space. Open Widget Studio", PaddockTheme.radiusCard, null, true, false,
        { onAction(PaddockWidgetBentoDestination.Banner) },
        draw = {
            val photo = assets.bannerPhoto
            val boxW = PaddockTheme.banner.width
            val boxH = PaddockTheme.banner.height
            val fit = max(boxW / photo.width, boxH / photo.height)
            val srcW = (boxW / fit).toInt()
            val srcH = (boxH / fit).toInt()
            drawImage(
                photo,
                srcOffset = IntOffset((photo.width - srcW) / 2, (photo.height - srcH) / 2),
                srcSize = IntSize(srcW, srcH),
                dstSize = IntSize((boxW * px).toInt(), (boxH * px).toInt()),
            )
            drawRect(
                Brush.linearGradient(
                    listOf(PaddockTheme.bannerShade, PaddockTheme.bannerGlow, PaddockTheme.bannerClear),
                    start = Offset(0f, size.height),
                    end = Offset(size.width, 0f),
                ),
            )
            with(book) {
                val titleLayout = layout("Your race-day space", PaddockTheme.bannerTitleSize, PaddockFace.ExtraBold, -0.6f)
                drawText(titleLayout, color = Color.White, topLeft = Offset(PaddockTheme.bannerPad * px, PaddockTheme.bannerTop * px))
                val subtitle = layout("Build a dashboard that feels like yours.", PaddockTheme.bannerSubtitleSize, PaddockFace.Medium)
                drawText(
                    subtitle, color = Color.White,
                    topLeft = Offset(PaddockTheme.bannerPad * px, PaddockTheme.bannerTop * px + titleLayout.size.height + PaddockTheme.bannerGap * px),
                )
            }
        },
    )
}
