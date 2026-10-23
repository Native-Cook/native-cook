package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.sp

private val GrassMuted = Color.White.copy(alpha = 0.60f)

@Composable
internal fun GrassTimerEditorial(
    assets: GrassTimerCarouselAssets,
    textScale: Float,
    referenceOrigin: Dp,
    point: (Float) -> Dp,
) {
    var selectedMix by remember { mutableStateOf<String?>(null) }

    Column(
        Modifier.offset(x = point(30f), y = referenceOrigin + point(371f)),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.Text(
                text = "Mixes",
                color = Color.White,
                fontSize = (18f * textScale).sp,
                fontWeight = FontWeight.Medium,
            )
            Spacer(Modifier.width(point(8f)))
            ChevronRight(Modifier.size(point(13f)), GrassMuted)
        }
        Spacer(Modifier.height(point(4f)))
        androidx.compose.material3.Text(
            text = "Personalized soundscapes to focus, relax\nand sleep.",
            color = GrassMuted,
            fontSize = (16.7f * textScale).sp,
            lineHeight = (22.4f * textScale).sp,
        )
    }

    Row(Modifier.offset(x = point(14f), y = referenceOrigin + point(453f))) {
        MixDisc(
            image = assets["orb-ocean"],
            label = "Ocean",
            playing = selectedMix == "Ocean",
            textScale = textScale,
            point = point,
            onClick = { selectedMix = if (selectedMix == "Ocean") null else "Ocean" },
        )
        Spacer(Modifier.width(point(25f)))
        MixDisc(
            image = assets["orb-waves"],
            label = "Waves",
            playing = selectedMix == "Waves",
            textScale = textScale,
            point = point,
            onClick = { selectedMix = if (selectedMix == "Waves") null else "Waves" },
        )
        Spacer(Modifier.width(point(25f)))
        MixDisc(
            image = assets["orb-airplane"],
            label = "Airplane",
            playing = selectedMix == "Airplane",
            textScale = textScale,
            point = point,
            onClick = { selectedMix = if (selectedMix == "Airplane") null else "Airplane" },
        )
    }

    Column(Modifier.offset(x = point(30f), y = referenceOrigin + point(631f))) {
        androidx.compose.material3.Text(
            text = "Sleep Stories",
            color = Color.White,
            fontSize = (18f * textScale).sp,
            fontWeight = FontWeight.Medium,
        )
        androidx.compose.material3.Text(
            text = "Relax and drift off to these original stories",
            color = GrassMuted,
            fontSize = (16.7f * textScale).sp,
            lineHeight = (22.4f * textScale).sp,
        )
    }

    Row(
        Modifier
            .offset(x = point(14f), y = referenceOrigin + point(689f))
            .width(point(388f))
            .height(point(182f)),
    ) {
        Image(
            bitmap = assets["story-mountains"],
            contentDescription = "Mountain sleep story",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(point(272f))
                .height(point(182f))
                .clip(RoundedCornerShape(point(36f))),
        )
        Spacer(Modifier.width(point(17f)))
        Image(
            bitmap = assets["story-clouds"],
            contentDescription = "Cloud sleep story",
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .width(point(95f))
                .height(point(182f))
                .clip(
                    RoundedCornerShape(
                        topStart = point(36f),
                        bottomStart = point(36f),
                    ),
                ),
        )
    }
}

@Composable
private fun MixDisc(
    image: androidx.compose.ui.graphics.ImageBitmap,
    label: String,
    playing: Boolean,
    textScale: Float,
    point: (Float) -> Dp,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(point(105f))
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                role = Role.Button
                contentDescription = "$label, ${if (playing) "playing" else "paused"}"
            },
    ) {
        Box(
            contentAlignment = Alignment.Center,
            modifier = Modifier
                .size(point(105f))
                .border(point(1.5f), Color.White.copy(alpha = 0.30f), CircleShape),
        ) {
            Image(
                bitmap = image,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(point(98f))
                    .clip(CircleShape),
            )
            Canvas(
                Modifier
                    .size(point(38f))
                    .shadow(point(3f), CircleShape),
            ) {
                if (playing) {
                    val barWidth = size.width * 0.16f
                    val gap = size.width * 0.12f
                    val left = size.width * 0.5f - gap * 0.5f - barWidth
                    drawRoundRect(
                        Color.White,
                        topLeft = Offset(left, size.height * 0.18f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.64f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.25f),
                    )
                    drawRoundRect(
                        Color.White,
                        topLeft = Offset(size.width * 0.5f + gap * 0.5f, size.height * 0.18f),
                        size = androidx.compose.ui.geometry.Size(barWidth, size.height * 0.64f),
                        cornerRadius = androidx.compose.ui.geometry.CornerRadius(barWidth * 0.25f),
                    )
                } else {
                    val path = Path().apply {
                        moveTo(size.width * 0.34f, size.height * 0.20f)
                        lineTo(size.width * 0.78f, size.height * 0.50f)
                        lineTo(size.width * 0.34f, size.height * 0.80f)
                        close()
                    }
                    drawPath(path, Color.White)
                }
            }
        }
        Spacer(Modifier.height(point(10f)))
        androidx.compose.material3.Text(
            text = label,
            color = Color.White,
            fontSize = (14f * textScale).sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun ChevronRight(modifier: Modifier, color: Color) {
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.30f, size.height * 0.16f)
            lineTo(size.width * 0.70f, size.height * 0.50f)
            lineTo(size.width * 0.30f, size.height * 0.84f)
        }
        drawPath(path, color, style = Stroke(width = size.width * 0.14f))
    }
}
