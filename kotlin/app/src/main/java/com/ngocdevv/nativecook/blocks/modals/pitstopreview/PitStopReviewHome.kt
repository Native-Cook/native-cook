package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Podcasts
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@Composable
internal fun PitStopReviewHome(
    snapshot: State<PitStopReviewRuntime.Snapshot>,
    assets: PitStopReviewAssets,
    fonts: PitStopReviewFonts,
    scale: Float,
    displayName: String,
    cards: List<Boolean>,
    onWrite: () -> Unit,
    onDestination: (PitStopReviewDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = PitStopReviewTheme
    Box(modifier.fillMaxWidth().height((1030f * scale).dp)) {
        Box(
            Modifier
                .fillMaxWidth()
                .height((PitStopReviewTheme.PageTone.height * scale).dp)
                .background(
                    Brush.verticalGradient(
                        *PitStopReviewTheme.PageTone.stops.zip(PitStopReviewTheme.PageTone.colors).toTypedArray(),
                    ),
                ),
        )
        PitStopReviewHero(
            snapshot,
            assets,
            scale,
            Modifier
                .fillMaxWidth()
                .height((PitStopReviewTheme.Hero.height * scale).dp)
                .offset(y = (PitStopReviewTheme.Hero.top * scale).dp),
        )
        PitStopReviewClubPass(fonts, scale) { onDestination(PitStopReviewDestination.Pass) }
        Box(
            Modifier
                .offset(
                    x = ((theme.REFERENCE_WIDTH - PitStopReviewTheme.Avatar.right - PitStopReviewTheme.Avatar.hit) * scale).dp,
                    y = ((theme.BAR_CENTER - PitStopReviewTheme.Avatar.hit / 2f) * scale).dp,
                )
                .requiredSize((PitStopReviewTheme.Avatar.hit * scale).dp)
                .semantics {
                    contentDescription = "$displayName profile"
                    role = Role.Button
                }
                .clickable { onDestination(PitStopReviewDestination.Profile) },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .requiredSize((PitStopReviewTheme.Avatar.ring * scale).dp)
                    .clip(CircleShape)
                    .background(Color.White.copy(alpha = 0.16f))
                    .border(0.7.dp, Color.White.copy(alpha = 0.22f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Image(
                    assets.profile,
                    contentDescription = null,
                    modifier = Modifier
                        .requiredSize((PitStopReviewTheme.Avatar.photo * scale).dp)
                        .clip(CircleShape),
                    contentScale = ContentScale.Crop,
                )
            }
        }

        PitStopReviewLine(
            "Enjoying Turn Nine?",
            PitStopReviewTheme.HeroText.centerX - 201f,
            PitStopReviewTheme.HeroText.titleBaseline,
            PitStopReviewTheme.HeroText.titleSize,
            fonts.families.getValue(PitStopReviewFace.Bold),
            theme.ink,
            scale,
            PitStopReviewTheme.HeroText.titleTracking,
            width = theme.REFERENCE_WIDTH,
            textAlign = TextAlign.Center,
        )
        val subtitleTop = PitStopReviewTheme.HeroText.subtitleBaseline -
            (PitStopReviewTheme.HeroText.subtitleLineHeight - 1.219f * PitStopReviewTheme.HeroText.subtitleSize) / 2f -
            theme.ASCENT * PitStopReviewTheme.HeroText.subtitleSize
        Text(
            "Share a quick review — help more fans find their next race-day companion.",
            modifier = Modifier
                .offset(
                    x = ((PitStopReviewTheme.HeroText.centerX - PitStopReviewTheme.HeroText.subtitleWidth / 2f) * scale).dp,
                    y = (subtitleTop * scale).dp,
                )
                .width((PitStopReviewTheme.HeroText.subtitleWidth * scale).dp),
            color = theme.ink,
            fontFamily = fonts.families.getValue(PitStopReviewFace.Medium),
            fontSize = pitStopReviewSp(PitStopReviewTheme.HeroText.subtitleSize, scale),
            lineHeight = pitStopReviewSp(PitStopReviewTheme.HeroText.subtitleLineHeight, scale),
            textAlign = TextAlign.Center,
            style = pitStopReviewTextStyle(),
        )
        PitStopReviewWriteButton(snapshot, fonts, scale, onWrite)
        Row(
            Modifier.offset(
                x = ((theme.REFERENCE_WIDTH -
                    (PitStopReviewTheme.Pager.active + PitStopReviewTheme.Pager.dot * 2f + PitStopReviewTheme.Pager.gap * 2f)) / 2f * scale).dp,
                y = (PitStopReviewTheme.Pager.top * scale).dp,
            ),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier
                    .requiredSize((PitStopReviewTheme.Pager.active * scale).dp, (4f * scale).dp)
                    .background(Color.White, CircleShape),
            )
            Box(Modifier.width((PitStopReviewTheme.Pager.gap * scale).dp))
            repeat(2) { index ->
                Box(
                    Modifier
                        .requiredSize((PitStopReviewTheme.Pager.dot * scale).dp)
                        .background(Color.White.copy(alpha = 0.38f), CircleShape),
                )
                if (index == 0) Box(Modifier.width((PitStopReviewTheme.Pager.gap * scale).dp))
            }
        }
        PitStopReviewRail(scale, onDestination)
        PitStopReviewBento(fonts, scale, cards, onDestination)
        PitStopReviewStats(fonts, scale) { onDestination(PitStopReviewDestination.Results) }
    }
}

@Composable
private fun PitStopReviewClubPass(fonts: PitStopReviewFonts, scale: Float, onClick: () -> Unit) {
    val theme = PitStopReviewTheme
    Row(
        Modifier
            .offset(
                x = (PitStopReviewTheme.ClubPass.left * scale).dp,
                y = ((theme.BAR_CENTER - PitStopReviewTheme.ClubPass.height / 2f) * scale).dp,
            )
            .height((PitStopReviewTheme.ClubPass.height * scale).dp)
            .clip(CircleShape)
            .background(theme.violet)
            .semantics {
                contentDescription = "Club Pass"
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(
                start = (PitStopReviewTheme.ClubPass.padLeft * scale).dp,
                end = (PitStopReviewTheme.ClubPass.padRight * scale).dp,
            ),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Canvas(
            Modifier.requiredSize(
                (PitStopReviewTheme.ClubPass.badge * scale).dp,
                (PitStopReviewTheme.ClubPass.badge * scale).dp,
            ),
        ) {
            drawCircle(Color.White)
            val center = this.center
            val halfWidth = PitStopReviewTheme.ClubPass.sparkleWidth * scale * density / 2f
            val halfHeight = PitStopReviewTheme.ClubPass.sparkleHeight * scale * density / 2f
            val path = Path().apply {
                moveTo(center.x, center.y - halfHeight)
                quadraticTo(center.x + halfWidth * 0.18f, center.y - halfHeight * 0.18f, center.x + halfWidth, center.y)
                quadraticTo(center.x + halfWidth * 0.18f, center.y + halfHeight * 0.18f, center.x, center.y + halfHeight)
                quadraticTo(center.x - halfWidth * 0.18f, center.y + halfHeight * 0.18f, center.x - halfWidth, center.y)
                quadraticTo(center.x - halfWidth * 0.18f, center.y - halfHeight * 0.18f, center.x, center.y - halfHeight)
                close()
            }
            drawPath(path, theme.violet)
        }
        Box(Modifier.width((PitStopReviewTheme.ClubPass.gap * scale).dp))
        Text(
            "Club Pass",
            color = Color.White,
            fontFamily = fonts.families.getValue(PitStopReviewFace.SemiBold),
            fontSize = pitStopReviewSp(PitStopReviewTheme.ClubPass.fontSize, scale),
            letterSpacing = pitStopReviewSp(PitStopReviewTheme.ClubPass.letterSpacing, scale),
            lineHeight = pitStopReviewSp(PitStopReviewTheme.ClubPass.lineHeight, scale),
            maxLines = 1,
            style = pitStopReviewTextStyle(),
        )
    }
}

@Composable
private fun PitStopReviewWriteButton(
    snapshot: State<PitStopReviewRuntime.Snapshot>,
    fonts: PitStopReviewFonts,
    scale: Float,
    onClick: () -> Unit,
) {
    val theme = PitStopReviewTheme
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(
        targetValue = if (pressed) PitStopReviewMotion.M.ctaPressed.toFloat() else 1f,
        animationSpec = tween(if (pressed) 90 else 160),
        label = "Pit stop review CTA press",
    )
    Box(
        Modifier
            .offset(
                x = ((PitStopReviewTheme.HeroText.centerX - PitStopReviewTheme.HeroText.ctaWidth / 2f) * scale).dp,
                y = (PitStopReviewTheme.HeroText.ctaTop * scale).dp,
            )
            .requiredSize(
                (PitStopReviewTheme.HeroText.ctaWidth * scale).dp,
                (PitStopReviewTheme.HeroText.ctaHeight * scale).dp,
            )
            .graphicsLayer {
                val auto = PitStopReviewMotion.ctaScale(snapshot.value.scene).toFloat()
                scaleX = auto * pressScale
                scaleY = auto * pressScale
            }
            .clip(CircleShape)
            .background(theme.ink)
            .clickable(
                interactionSource = interaction,
                indication = null,
                role = Role.Button,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Write a review",
            color = Color(0xFF080708),
            fontFamily = fonts.families.getValue(PitStopReviewFace.SemiBold),
            fontSize = pitStopReviewSp(PitStopReviewTheme.HeroText.ctaSize, scale),
            letterSpacing = pitStopReviewSp(PitStopReviewTheme.HeroText.ctaTracking, scale),
            maxLines = 1,
            style = pitStopReviewTextStyle(),
        )
    }
}

@Composable
private fun PitStopReviewRail(
    scale: Float,
    onDestination: (PitStopReviewDestination) -> Unit,
) {
    val theme = PitStopReviewTheme
    val rail = PitStopReviewTheme.Rail
    val railLeft = (theme.REFERENCE_WIDTH - rail.width) / 2f
    Box(
        Modifier
            .offset(x = (railLeft * scale).dp, y = (rail.top * scale).dp)
            .requiredSize((rail.width * scale).dp, (rail.height * scale).dp)
            .clip(CircleShape)
            .background(rail.fill)
            .border(1.dp, rail.stroke, CircleShape),
    ) {
        val actions = listOf(
            "Race weekends" to PitStopReviewDestination.Calendar,
            "Reaction test" to PitStopReviewDestination.Reflex,
            "Customize home cards" to PitStopReviewDestination.Customize,
            "Club Pass" to PitStopReviewDestination.Pass,
        )
        actions.forEachIndexed { index, action ->
            val centerX = rail.iconX[index] - railLeft
            Box(
                Modifier
                    .offset(x = ((centerX - 30f) * scale).dp)
                    .requiredSize((60f * scale).dp, (rail.height * scale).dp)
                    .semantics {
                        contentDescription = action.first
                        role = Role.Button
                    }
                    .clickable { onDestination(action.second) },
                contentAlignment = Alignment.Center,
            ) {
                when (index) {
                    0 -> Icon(
                        Icons.Default.CalendarMonth,
                        contentDescription = null,
                        modifier = Modifier.requiredSize((rail.calendar * scale).dp),
                        tint = theme.ink,
                    )
                    1 -> Icon(
                        Icons.Default.SportsEsports,
                        contentDescription = null,
                        modifier = Modifier.requiredSize((rail.controller * scale).dp),
                        tint = theme.ink,
                    )
                    2 -> PitStopReviewShapesIcon(rail.shapes * scale)
                    else -> PitStopReviewSparkleIcon(rail.sparkle * scale)
                }
            }
        }
        repeat(3) { index ->
            val x = ((rail.iconX[index] + rail.iconX[index + 1]) / 2f - railLeft) * scale
            Box(
                Modifier
                    .offset(
                        x = x.dp,
                        y = ((rail.height - rail.dividerHeight) / 2f * scale).dp,
                    )
                    .requiredSize(1.dp, (rail.dividerHeight * scale).dp)
                    .background(rail.dividerColor),
            )
        }
    }
}

@Composable
private fun PitStopReviewShapesIcon(size: Float) {
    Canvas(Modifier.requiredSize(size.dp)) {
        val unit = this.size.width / 24f
        drawLine(PitStopReviewTheme.ink, Offset(5.5f * unit, 1.5f * unit), Offset(5.5f * unit, 9.5f * unit), 2.6f * unit, StrokeCap.Round)
        drawLine(PitStopReviewTheme.ink, Offset(1.5f * unit, 5.5f * unit), Offset(9.5f * unit, 5.5f * unit), 2.6f * unit, StrokeCap.Round)
        drawCircle(PitStopReviewTheme.ink, 4.3f * unit, Offset(18.5f * unit, 5.5f * unit))
        drawRoundRect(PitStopReviewTheme.ink, Offset(1.3f * unit, 14.3f * unit), androidx.compose.ui.geometry.Size(8.4f * unit, 8.4f * unit), androidx.compose.ui.geometry.CornerRadius(2.2f * unit))
        drawLine(PitStopReviewTheme.ink, Offset(15.2f * unit, 15.2f * unit), Offset(21.8f * unit, 21.8f * unit), 2.6f * unit, StrokeCap.Round)
        drawLine(PitStopReviewTheme.ink, Offset(21.8f * unit, 15.2f * unit), Offset(15.2f * unit, 21.8f * unit), 2.6f * unit, StrokeCap.Round)
    }
}

@Composable
private fun PitStopReviewSparkleIcon(size: Float) {
    Canvas(Modifier.requiredSize(size.dp)) {
        val unit = this.size.width / 24f
        drawCircle(PitStopReviewTheme.ink, 10.6f * unit, center, style = Stroke(2f * unit))
        val path = Path().apply {
            moveTo(12f * unit, 5.4f * unit)
            quadraticTo(12.9f * unit, 11.1f * unit, 18.6f * unit, 12f * unit)
            quadraticTo(12.9f * unit, 12.9f * unit, 12f * unit, 18.6f * unit)
            quadraticTo(11.1f * unit, 12.9f * unit, 5.4f * unit, 12f * unit)
            quadraticTo(11.1f * unit, 11.1f * unit, 12f * unit, 5.4f * unit)
            close()
        }
        drawPath(path, PitStopReviewTheme.ink)
    }
}

@Composable
private fun PitStopReviewBento(
    fonts: PitStopReviewFonts,
    scale: Float,
    cards: List<Boolean>,
    onDestination: (PitStopReviewDestination) -> Unit,
) {
    val bento = PitStopReviewTheme.Bento
    val visibleRows = cards.indices.filter { index -> cards[index] }
    visibleRows.forEachIndexed { visibleIndex, cardIndex ->
        val interaction = remember(cardIndex) { MutableInteractionSource() }
        val pressed by interaction.collectIsPressedAsState()
        val top = bento.top + visibleIndex * bento.tilePitch
        val fill = listOf(bento.violet, bento.orange, bento.yellow)[cardIndex]
        val label = listOf("every lap counts", "Race day Reflex test", "custom home cards")[cardIndex]
        val destination = listOf(
            PitStopReviewDestination.Laps,
            PitStopReviewDestination.Reflex,
            PitStopReviewDestination.Customize,
        )[cardIndex]
        Box(
            Modifier
                .offset(x = (bento.left * scale).dp, y = (top * scale).dp)
                .requiredSize((bento.tileWidth * scale).dp, (bento.tileHeight * scale).dp)
                .graphicsLayer {
                    scaleX = if (pressed) 0.97f else 1f
                    scaleY = scaleX
                }
                .clip(RoundedCornerShape((bento.tileRadius * scale).dp))
                .background(fill)
                .semantics {
                    contentDescription = label
                    role = Role.Button
                }
                .clickable(
                    interactionSource = interaction,
                    indication = null,
                    onClick = { onDestination(destination) },
                ),
        ) {
            when (cardIndex) {
                0 -> PitStopReviewLapsTile(fonts, scale)
                1 -> PitStopReviewReflexTile(fonts, scale)
                else -> PitStopReviewHomeCardsTile(fonts, scale)
            }
        }
    }
    PitStopReviewPitcast(fonts, scale) { onDestination(PitStopReviewDestination.Pitcast) }
}

@Composable
private fun PitStopReviewLapsTile(fonts: PitStopReviewFonts, scale: Float) {
    val bento = PitStopReviewTheme.Bento
    Box(
        Modifier
            .offset(
                x = ((bento.violetBadgeCenterX - bento.violetBadgeWidth / 2f) * scale).dp,
                y = ((bento.tileHeight - bento.violetBadgeHeight) / 2f * scale).dp,
            )
            .requiredSize((bento.violetBadgeWidth * scale).dp, (bento.violetBadgeHeight * scale).dp)
            .border((bento.violetBadgeStroke * scale).dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "TN",
            color = Color.White,
            fontFamily = fonts.families.getValue(PitStopReviewFace.Black),
            fontSize = pitStopReviewSp(bento.badgeSize, scale),
            letterSpacing = pitStopReviewSp(-0.4f, scale),
            style = pitStopReviewTextStyle(),
        )
    }
    PitStopReviewLine("every lap", bento.violetX, bento.violetBaselines[0], bento.violetSize, fonts.families.getValue(PitStopReviewFace.ExtraBold), Color.White, scale, bento.violetTracking)
    PitStopReviewLine("counts", bento.violetX, bento.violetBaselines[1], bento.violetSize, fonts.families.getValue(PitStopReviewFace.ExtraBold), Color.White, scale, bento.violetTracking)
}

@Composable
private fun PitStopReviewReflexTile(fonts: PitStopReviewFonts, scale: Float) {
    val bento = PitStopReviewTheme.Bento
    PitStopReviewTileLabel("Race day", "Reflex test", bento.tileInset, Color.White, fonts, scale)
    Box(
        Modifier
            .offset(
                x = ((bento.tileWidth - bento.orangeChipTrailing - bento.orangeChipWidth) * scale).dp,
                y = ((bento.tileHeight - bento.orangeChipHeight) / 2f * scale).dp,
            )
            .requiredSize((bento.orangeChipWidth * scale).dp, (bento.orangeChipHeight * scale).dp)
            .clip(RoundedCornerShape((bento.orangeChipRadius * scale).dp))
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Default.SportsEsports,
            contentDescription = null,
            modifier = Modifier.requiredSize((bento.chipIcon * scale).dp),
            tint = bento.orange,
        )
    }
}

@Composable
private fun PitStopReviewHomeCardsTile(fonts: PitStopReviewFonts, scale: Float) {
    val bento = PitStopReviewTheme.Bento
    Icon(
        Icons.Default.GridView,
        contentDescription = null,
        modifier = Modifier
            .offset(
                x = ((bento.tileMarkCenter - bento.cardIcon / 2f) * scale).dp,
                y = ((bento.tileHeight - bento.cardIcon) / 2f * scale).dp,
            )
            .requiredSize((bento.cardIcon * scale).dp),
        tint = Color.Black,
    )
    PitStopReviewTileLabel("custom", "home cards", bento.tileTextAfterMark, Color.Black, fonts, scale)
}

@Composable
private fun PitStopReviewTileLabel(
    eyebrow: String,
    title: String,
    x: Float,
    color: Color,
    fonts: PitStopReviewFonts,
    scale: Float,
) {
    val bento = PitStopReviewTheme.Bento
    PitStopReviewLine(eyebrow, x, bento.eyebrowBaseline, bento.eyebrowSize, fonts.families.getValue(PitStopReviewFace.SemiBold), color, scale, bento.eyebrowTracking)
    PitStopReviewLine(title, x, bento.titleBaseline, bento.titleSize, fonts.families.getValue(PitStopReviewFace.Bold), color, scale, bento.titleTracking)
}

@Composable
private fun PitStopReviewPitcast(
    fonts: PitStopReviewFonts,
    scale: Float,
    onClick: () -> Unit,
) {
    val bento = PitStopReviewTheme.Bento
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        Modifier
            .offset(x = (bento.pitcastLeft * scale).dp, y = (bento.top * scale).dp)
            .requiredSize((bento.pitcastWidth * scale).dp, (bento.pitcastHeight * scale).dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.97f else 1f
                scaleY = scaleX
            }
            .clip(RoundedCornerShape((bento.pitcastRadius * scale).dp))
            .background(bento.cream)
            .semantics {
                contentDescription = "Read the weekend brief, Alex Vale and Sam Ives"
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Box(
            Modifier
                .offset(x = (bento.pitcastInset * scale).dp, y = (bento.pitcastInset * scale).dp)
                .requiredSize((bento.pitcastMic * scale).dp)
                .clip(RoundedCornerShape((bento.pitcastMicRadius * scale).dp))
                .background(bento.orange),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Default.Mic,
                contentDescription = null,
                modifier = Modifier.requiredSize((bento.pitcastIcon * scale).dp),
                tint = bento.micInk,
            )
        }
        Box(
            Modifier
                .offset(
                    x = ((bento.pitcastWidth - bento.pitcastArrowInset - bento.pitcastArrow) * scale).dp,
                    y = (bento.pitcastArrowInset * scale).dp,
                )
                .requiredSize((bento.pitcastArrow * scale).dp)
                .background(Color.Black, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                "↗",
                color = Color.White,
                fontSize = pitStopReviewSp(bento.pitcastArrowIcon, scale),
                style = pitStopReviewTextStyle(),
            )
        }
        Row(
            Modifier.offset(
                x = (bento.pitcastInset * scale).dp,
                y = ((bento.pitcastTitleBaseline - PitStopReviewTheme.ASCENT * bento.pitcastTitleSize) * scale).dp,
            ),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                "pit",
                color = Color.Black,
                fontFamily = fonts.families.getValue(PitStopReviewFace.Black),
                fontSize = pitStopReviewSp(bento.pitcastTitleSize, scale),
                letterSpacing = pitStopReviewSp(-0.6f, scale),
                style = pitStopReviewTextStyle(),
            )
            Text(
                "cast",
                color = Color.Black,
                fontFamily = FontFamily.Serif,
                fontStyle = FontStyle.Italic,
                fontWeight = FontWeight.SemiBold,
                fontSize = pitStopReviewSp(bento.pitcastTitleSize, scale),
                letterSpacing = pitStopReviewSp(-0.8f, scale),
                style = pitStopReviewTextStyle(),
            )
        }
        PitStopReviewLine("Every Friday at 6pm", bento.pitcastInset, bento.pitcastSubtitleBaseline, bento.pitcastSubtitleSize, fonts.families.getValue(PitStopReviewFace.Medium), Color.Black, scale)
        PitStopReviewLine("Alex Vale / Sam Ives", bento.pitcastInset, bento.pitcastNamesBaseline, bento.pitcastNamesSize, fonts.families.getValue(PitStopReviewFace.Medium), Color.Black.copy(alpha = 0.9f), scale)
        val icons = listOf(Icons.Default.Headphones, Icons.Default.PlayCircle, Icons.Default.Podcasts)
        icons.forEachIndexed { index, icon ->
            Icon(
                icon,
                contentDescription = null,
                modifier = Modifier
                    .offset(
                        x = ((bento.pitcastGlyphX[index] - bento.pitcastGlyph / 2f) * scale).dp,
                        y = ((bento.pitcastGlyphRow - bento.pitcastGlyph / 2f) * scale).dp,
                    )
                    .requiredSize((bento.pitcastGlyph * scale).dp),
                tint = Color.Black,
            )
        }
    }
}

@Composable
private fun PitStopReviewStats(
    fonts: PitStopReviewFonts,
    scale: Float,
    onClick: () -> Unit,
) {
    val bento = PitStopReviewTheme.Bento
    val density = LocalDensity.current.density
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val top = bento.top + 220f
    Box(
        Modifier
            .offset(x = (bento.left * scale).dp, y = (top * scale).dp)
            .requiredSize((bento.statsWidth * scale).dp, (bento.statsHeight * scale).dp)
            .graphicsLayer {
                scaleX = if (pressed) 0.97f else 1f
                scaleY = scaleX
            }
            .clip(RoundedCornerShape((bento.statsRadius * scale).dp))
            .background(
                Brush.linearGradient(
                    listOf(bento.statsGlow, bento.statsShade),
                    start = androidx.compose.ui.geometry.Offset(0f, bento.statsHeight * scale * density),
                    end = androidx.compose.ui.geometry.Offset(bento.statsWidth * scale * density, 0f),
                ),
            )
            .semantics {
                contentDescription = "Turn Nine stats"
                role = Role.Button
            }
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            ),
    ) {
        Icon(
            Icons.Default.Description,
            contentDescription = null,
            modifier = Modifier
                .offset(x = (bento.statsIconX * scale).dp, y = (bento.statsIconY * scale).dp)
                .requiredSize((bento.statsIconWidth * scale).dp, (bento.statsIconHeight * scale).dp),
            tint = Color.White,
        )
        PitStopReviewLine("Turn Nine", bento.statsTextX, bento.statsEyebrowBaseline, bento.statsEyebrowSize, fonts.families.getValue(PitStopReviewFace.Bold), Color.White, scale)
        PitStopReviewLine("Stats", bento.statsTextX, bento.statsWordBaseline, bento.statsWordSize, fonts.families.getValue(PitStopReviewFace.Bold), Color.White, scale)
        PitStopReviewLine("Stats, records & stories", bento.statsCaptionX, bento.statsCaptionBaselines[0], bento.statsCaptionSize, fonts.families.getValue(PitStopReviewFace.Medium), Color.White, scale)
        PitStopReviewLine("from Turn Nine", bento.statsCaptionX, bento.statsCaptionBaselines[1], bento.statsCaptionSize, fonts.families.getValue(PitStopReviewFace.Medium), Color.White, scale)
    }
}

@Composable
internal fun PitStopReviewLine(
    text: String,
    x: Float,
    baseline: Float,
    size: Float,
    family: FontFamily,
    color: Color,
    scale: Float,
    tracking: Float = 0f,
    width: Float? = null,
    textAlign: TextAlign = TextAlign.Start,
) {
    Text(
        text,
        modifier = Modifier
            .offset(x = (x * scale).dp, y = ((baseline - PitStopReviewTheme.ASCENT * size) * scale).dp)
            .then(if (width != null) Modifier.width((width * scale).dp) else Modifier),
        color = color,
        fontFamily = family,
        fontSize = pitStopReviewSp(size, scale),
        letterSpacing = pitStopReviewSp(tracking, scale),
        textAlign = textAlign,
        maxLines = 1,
        style = pitStopReviewTextStyle(),
    )
}

@Composable
internal fun pitStopReviewSp(value: Float, scale: Float): TextUnit {
    val density = LocalDensity.current
    return (value * scale / density.fontScale).sp
}

internal fun pitStopReviewTextStyle(): TextStyle = TextStyle(
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)
