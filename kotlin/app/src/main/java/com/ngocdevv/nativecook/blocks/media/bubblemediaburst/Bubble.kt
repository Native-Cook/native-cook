package com.ngocdevv.nativecook.blocks.media.bubblemediaburst

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.SoftRoundRect
import kotlin.math.sqrt

/**
 * A photo in a bubble: [title] is read out by TalkBack and passed to `onSelect`, [tint] shows while
 * the photo loads, and [glow] is the light the glass catches along its bottom edge.
 */
@Immutable
data class BubbleItem(val title: String, val photo: String, val tint: Color, val glow: Color)

// The glass, in shares of the bubble's diameter: the rim darkens, a crescent of light lies along the
// top left with a glint in it, and a coloured reflection along the bottom right.
private val Vignette = arrayOf(
    0f to Color.Black.copy(alpha = 0f),
    0.55f to Color.Black.copy(alpha = 0f),
    0.8f to Color.Black.copy(alpha = 0.1f),
    0.94f to Color.Black.copy(alpha = 0.32f),
    1f to Color.Black.copy(alpha = 0.22f),
)
private const val SHINE_OUTER = 0.465f
private val ShineInner = floatArrayOf(0.628f, 0.678f, 0.61f)
private val Shine = listOf(Color.White.copy(alpha = 0.4f), Color.White.copy(alpha = 0f))
private val Glint = floatArrayOf(0.3f, 0.2f, 0.07f)
private val GlintColors = listOf(Color.White.copy(alpha = 0.55f), Color.White.copy(alpha = 0f))
private const val REFLECT_OUTER = 0.47f
private val ReflectInner = floatArrayOf(0.45f, 0.306f, 0.61f)
private const val REFLECT_ALPHA = 0.5f
private val Rim = Color.White.copy(alpha = 0.25f)

// The soft shadow under each bubble.
private val Shadow = Color.Black.copy(alpha = 0.12f)
private const val SHADOW_Y = 6f
private const val SHADOW_SIGMA = 12f

// A picked bubble: a white ring on its edge and a check on its rim at the top right.
private const val RING = 2f
private const val BADGE = 24f
private val BadgeFill = Color(0xFF1A1A1A)
private const val BADGE_BORDER = 2f
private val BADGE_AT = sqrt(0.5f) / 2f

private fun circle(cx: Float, cy: Float, r: Float) = Path().apply { addOval(Rect(Offset(cx, cy), r)) }

private fun crescent(size: Float, outer: Float, inner: FloatArray) = Path().apply {
    op(circle(size / 2f, size / 2f, outer * size), circle(inner[0] * size, inner[1] * size, inner[2] * size), PathOperation.Difference)
}

/** The glass drawn over a photo: rim shade, top light, glint, bottom reflection and a fine rim. */
private fun Modifier.bubbleGlass(glow: Color) = drawWithCache {
    val s = size.width
    val shine = crescent(s, SHINE_OUTER, ShineInner)
    val reflect = crescent(s, REFLECT_OUTER, ReflectInner)
    val vignette = Brush.radialGradient(*Vignette, center = Offset(s / 2f, s / 2f), radius = s / 2f)
    val reflectBrush = Brush.linearGradient(
        listOf(glow.copy(alpha = REFLECT_ALPHA), glow.copy(alpha = 0f)),
        start = Offset(0.62f * s, 0.99f * s),
        end = Offset(0.5f * s, 0.62f * s),
    )
    val shineBrush = Brush.linearGradient(Shine, start = Offset(0.16f * s, 0.1f * s), end = Offset(0.5f * s, 0.5f * s))
    val glint = Offset(Glint[0] * s, Glint[1] * s)
    val glintBrush = Brush.radialGradient(GlintColors, center = glint, radius = Glint[2] * s)
    val rim = 1.dp.toPx()
    onDrawWithContent {
        drawContent()
        drawCircle(vignette, s / 2f)
        drawPath(reflect, reflectBrush)
        drawPath(shine, shineBrush)
        drawCircle(glintBrush, Glint[2] * s, glint)
        drawCircle(Rim, s / 2f - rim / 2f, style = Stroke(rim))
    }
}

@Composable
private fun Badge(modifier: Modifier) {
    Canvas(modifier.size(BADGE.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            val r = BADGE / 2f - BADGE_BORDER / 2f
            drawCircle(BadgeFill, r, Offset(BADGE / 2f, BADGE / 2f))
            drawCircle(Color.White, r, Offset(BADGE / 2f, BADGE / 2f), style = Stroke(BADGE_BORDER))
            val check = Path().apply {
                moveTo(7.5f, 12.3f)
                lineTo(10.6f, 15.3f)
                lineTo(16.6f, 9.1f)
            }
            drawPath(check, Color.White, style = Stroke(2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

/**
 * One photo in a glass bubble, placed and sized by the clock. [clock] and [events] are read only
 * while drawing, so the bubble moves without recomposing.
 */
@Composable
internal fun Bubble(
    index: Int,
    item: BubbleItem,
    slot: Slot,
    place: Place,
    diameter: Float,
    clock: State<Double>,
    events: () -> Events,
    still: Boolean,
    open: Boolean,
    picked: Boolean,
    onPress: () -> Unit,
) {
    val context = LocalContext.current
    val request = remember(item.photo, context) { ImageRequest.Builder(context).data(item.photo).crossfade(200).build() }
    val shadow = remember { SoftRoundRect(Shadow, SHADOW_SIGMA) }
    val interaction = remember { MutableInteractionSource() }

    fun pick(): Double {
        val e = events()
        return pickAmount(e.pickOn[index], e.pickAt[index], clock.value, still)
    }

    Box(
        Modifier
            .offset((place.restX - diameter / 2f).dp, (place.restY - diameter / 2f).dp)
            .size(diameter.dp)
            .graphicsLayer {
                val e = events()
                val p = pose(slot, place, clock.value, e.openAt, e.closeAt, still)
                val grown = p.scale * (1f + (PICK_SCALE.toFloat() - 1f) * (if (still) 0f else pick().toFloat()))
                translationX = (p.x - place.restX).dp.toPx()
                translationY = (p.y - place.restY).dp.toPx()
                scaleX = grown
                scaleY = grown
                alpha = p.opacity
            }
            .drawBehind {
                scale(density, density, pivot = Offset.Zero) {
                    drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, SHADOW_Y, diameter, diameter, diameter / 2f) }
                }
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .clip(CircleShape)
                .background(item.tint)
                .clickable(interactionSource = interaction, indication = null, enabled = open, role = Role.Button, onClick = onPress)
                .semantics {
                    contentDescription = item.title
                    selected = picked
                },
        ) {
            AsyncImage(
                model = request,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().bubbleGlass(item.glow),
            )
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { alpha = pick().toFloat().coerceIn(0f, 1f) }
                    .border(RING.dp, Color.White, CircleShape),
            )
        }
        // The check sits on the rim, half outside the circle the photo is clipped to.
        Badge(
            Modifier
                .offset((diameter / 2f + diameter * BADGE_AT - BADGE / 2f).dp, (diameter / 2f - diameter * BADGE_AT - BADGE / 2f).dp)
                .graphicsLayer {
                    val a = pick().toFloat()
                    alpha = (a * 2f).coerceIn(0f, 1f)
                    val pop = if (still) 1f else a.coerceAtLeast(0f)
                    scaleX = pop
                    scaleY = pop
                },
        )
    }
}
