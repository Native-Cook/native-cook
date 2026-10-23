package com.ngocdevv.nativecook.blocks.media.glowfileuploader

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.exp
import kotlin.math.sqrt

internal const val ZONE_HEIGHT = 162f
private const val ZONE_RADIUS = 28f
private val ZoneShape = RoundedCornerShape(ZONE_RADIUS.dp)
private val ZoneFill = Color(0xFF1B1E22)

// Hairlines running "/" at 45°, this far apart (measured across them), drifting right.
private const val HAIRLINE_GAP = 10f
private val Hairline = Color.White.copy(alpha = 0.028f)

// The inner glow, as in the Swift build: two white strokes on the zone's outline, a wide one blurred
// a lot and a thin core blurred a little, clipped to the zone. A stroke w wide blurred with σ lights
// a point d inside the edge by Φ((w/2 − d)/σ) − Φ((−w/2 − d)/σ); the widths and blurs are fitted so
// the light from the bottom edge inward matches the design (grey 163/137/91/64 at 0.5/3/8/14 dp, at
// the design's moment of the breathing). It is drawn as gradients with that profile, which
// looks the same on every API level: a radial one in each corner square, and a linear one along
// each edge, the edges split along the corners' diagonals so no point is lit twice. It is then
// masked from [MASK_TOP] at the top to 1 from [MASK_FULL] of the height down. A crisp rim sits on the edge.
private const val GLOW_WIDTH = 10f
private const val GLOW_SIGMA = 14f
private const val CORE_WIDTH = 5.5f
private const val CORE_SIGMA = 5f
private const val GLOW_DEPTH = 44f
private const val GLOW_STOPS = 19
private val GLOW_CORNER = maxOf(ZONE_RADIUS, GLOW_DEPTH)
private const val MASK_TOP = 0.78f
private const val MASK_FULL = 0.45f
private const val RIM = 1f
private const val RIM_ALPHA = 0.25f

// The clip badge: its centre this far below the zone's top, its disc, border and glyph, and the
// halo behind it (a blurred white disc).
private const val BADGE_CENTER = 34f
private const val BADGE = 32f
private val BadgeFill = Color(0xFF0F1114)
private val BadgeBorder = Color(0xFFD9DADC)
private const val BADGE_BORDER = 1.5f
private const val BADGE_GLYPH = 17f
private const val BADGE_GLYPH_STROKE = 1.3f
private const val HALO = 46f
private const val HALO_SIGMA = 8f
private const val HALO_STOPS = 12
private const val PULSE_STROKE = 1.5f

private const val TEXT_GAP = 12f
private const val HEADING_SIZE = 15f
private const val HEADING_LINE = 18f
private const val HINT_GAP = 2f
private const val HINT_SIZE = 10.5f
private const val HINT_LINE = 12.5f
private const val BUTTON_GAP = 13f
private val Heading = Color(0xFFF4F4F5)
private val Hint = Color(0xFF7C7F85)
private val HintLink = Color(0xFF9A9DA2)

// Select files.
private const val SELECT_WIDTH = 104f
private const val SELECT_HEIGHT = 34f
private const val SELECT_LABEL = 11.5f
private val SelectFill = Color(0xFF23262A).copy(alpha = 0.85f)
private val SelectBorder = Color(0xFF474A4F)
private val SelectLabel = Color(0xFFF4F4F5)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/** The normal distribution's CDF (Abramowitz–Stegun 7.1.26 for erf), enough for a glow. */
private fun phi(x: Float): Float {
    val z = x / sqrt(2f)
    val t = 1f / (1f + 0.3275911f * kotlin.math.abs(z))
    val y = 1f - (((((1.061405429f * t - 1.453152027f) * t) + 1.421413741f) * t - 0.284496736f) * t + 0.254829592f) * t * exp(-z * z)
    return 0.5f * (1f + if (z >= 0) y else -y)
}

/** The light of a stroke [w] wide blurred with [sigma], [d] dp inside the edge. */
private fun strokeAt(d: Float, w: Float, sigma: Float) = phi((w / 2f - d) / sigma) - phi((-w / 2f - d) / sigma)

/** The glow's light at [d] dp inside the edge: the wide stroke and the core, one over the other. */
private fun glowAt(d: Float) = 1f - (1f - strokeAt(d, GLOW_WIDTH, GLOW_SIGMA)) * (1f - strokeAt(d, CORE_WIDTH, CORE_SIGMA))

/** The halo's light at [r] dp from its centre (a disc of [HALO] blurred with [HALO_SIGMA]). */
private fun haloAt(r: Float) = phi((HALO / 2f - r) / HALO_SIGMA)

private val HaloReach = HALO / 2f + HALO_SIGMA * 3f

/**
 * The drop zone: the glowing card with the clip badge, the hint and Select files. Animated values
 * are read only while drawing.
 */
@Composable
internal fun DropZone(
    clock: State<Double>,
    events: () -> Events,
    still: Boolean,
    selectOn: Boolean,
    selectTouch: MutableInteractionSource,
    onSelect: () -> Unit,
    heading: String,
    selectLabel: String,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ZONE_HEIGHT.dp)
            .graphicsLayer {
                val s = 1f + (FLARE_SCALE * flare(events(), clock.value, still)).toFloat()
                scaleX = s
                scaleY = s
                clip = true
                shape = ZoneShape
            }
            .background(ZoneFill)
            .clickable(interactionSource = null, indication = null, enabled = selectOn, onClickLabel = selectLabel, onClick = onSelect),
    ) {
        Hairlines(clock, still)
        Glow(clock, events, still)
        Column(
            Modifier.fillMaxWidth().padding(top = (BADGE_CENTER - BADGE / 2f).dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Badge(clock, events, still)
            Spacer(Modifier.height(TEXT_GAP.dp))
            Text(
                heading,
                color = Heading,
                fontSize = HEADING_SIZE.sp,
                fontWeight = FontWeight.SemiBold,
                lineHeight = HEADING_LINE.sp,
                maxLines = 1,
                style = TextStyle(lineHeightStyle = Centered),
                modifier = Modifier.height(HEADING_LINE.dp),
            )
            Spacer(Modifier.height(HINT_GAP.dp))
            Text(
                buildAnnotatedString {
                    append("or ")
                    withStyle(SpanStyle(color = HintLink, textDecoration = TextDecoration.Underline)) { append("browse") }
                    append(", 4 MB max")
                },
                color = Hint,
                fontSize = HINT_SIZE.sp,
                lineHeight = HINT_LINE.sp,
                maxLines = 1,
                style = TextStyle(lineHeightStyle = Centered),
                modifier = Modifier.height(HINT_LINE.dp),
            )
            Spacer(Modifier.height(BUTTON_GAP.dp))
            SelectButton(clock, events, still, selectOn, selectTouch, onSelect, selectLabel)
        }
    }
}

@Composable
private fun Hairlines(clock: State<Double>, still: Boolean) {
    Canvas(Modifier.fillMaxWidth().height(ZONE_HEIGHT.dp)) {
        scale(density, density, pivot = Offset.Zero) {
            val w = size.width / density
            val h = size.height / density
            // Lines at 45° that are HAIRLINE_GAP apart sit √2 times that apart along the zone.
            val step = HAIRLINE_GAP * sqrt(2f)
            var x = -h - step + drift(clock.value, still).toFloat() * step
            while (x < w) {
                drawLine(Hairline, Offset(x, h), Offset(x + h, 0f), 1f)
                x += step
            }
        }
    }
}

@Composable
private fun Glow(clock: State<Double>, events: () -> Events, still: Boolean) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(ZONE_HEIGHT.dp)
            // Its own layer, so the vertical mask fades only the glow.
            .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
            .drawWithCache {
                val d = density
                val w = size.width
                val h = size.height
                // Whole pixels, so the eight pieces meet without a seam. The corners are as deep as
                // the glow, so its inner edge is rounded like a blur's (the light near the zone's
                // tighter corner is the edge's brightest).
                val r = Math.round(GLOW_CORNER * d).toFloat()
                val depth = Math.round(GLOW_DEPTH * d).toFloat()
                val edge = Array(GLOW_STOPS) { i ->
                    val t = i / (GLOW_STOPS - 1f)
                    t to Color.White.copy(alpha = glowAt(t * GLOW_DEPTH))
                }
                // In a corner, a point ρ from the arc's centre is r − ρ inside the edge.
                val corner = Array(GLOW_STOPS) { i ->
                    val t = i / (GLOW_STOPS - 1f)
                    t to Color.White.copy(alpha = glowAt(GLOW_CORNER * (1f - t)))
                }
                val top = Brush.verticalGradient(*edge, startY = 0f, endY = depth)
                val bottom = Brush.verticalGradient(*edge, startY = h, endY = h - depth)
                val left = Brush.horizontalGradient(*edge, startX = 0f, endX = depth)
                val right = Brush.horizontalGradient(*edge, startX = w, endX = w - depth)
                val centres = listOf(Offset(r, r), Offset(w - r, r), Offset(r, h - r), Offset(w - r, h - r))
                val corners = centres.map { Brush.radialGradient(*corner, center = it, radius = r) }
                val cornerBoxes = listOf(Offset(0f, 0f), Offset(w - r, 0f), Offset(0f, h - r), Offset(w - r, h - r))
                val mask = Brush.verticalGradient(0f to Color.White.copy(alpha = MASK_TOP), MASK_FULL to Color.White)
                val rim = RIM * d
                val rimRadius = ZONE_RADIUS * d

                fun band(vararg p: Float) = Path().apply {
                    moveTo(p[0], p[1])
                    for (i in 2 until p.size step 2) lineTo(p[i], p[i + 1])
                    close()
                }
                val bands = listOf(
                    top to band(r, 0f, w - r, 0f, w - r, r, w - depth, depth, depth, depth, r, r),
                    bottom to band(r, h, w - r, h, w - r, h - r, w - depth, h - depth, depth, h - depth, r, h - r),
                    left to band(0f, r, r, r, depth, depth, depth, h - depth, r, h - r, 0f, h - r),
                    right to band(w, r, w - r, r, w - depth, depth, w - depth, h - depth, w - r, h - r, w, h - r),
                )

                fun DrawScope.glow(alpha: Float) {
                    for ((brush, path) in bands) drawPath(path, brush, alpha)
                    for (i in 0 until 4) drawRect(corners[i], cornerBoxes[i], Size(r, r), alpha)
                }

                onDrawBehind {
                    val now = clock.value
                    val b = breath(now, still).toFloat()
                    glow(b)
                    val f = flare(events(), now, still).toFloat()
                    if (f > 0f) glow(b * FLARE.toFloat() * f)
                    drawRoundRect(
                        Color.White,
                        Offset(rim / 2f, rim / 2f),
                        Size(w - rim, h - rim),
                        CornerRadius(rimRadius - rim / 2f),
                        Stroke(rim),
                        RIM_ALPHA,
                    )
                    drawRect(mask, blendMode = BlendMode.DstIn)
                }
            },
    )
}

@Composable
private fun Badge(clock: State<Double>, events: () -> Events, still: Boolean) {
    Canvas(
        Modifier
            .size(BADGE.dp)
            .drawWithCache {
                val stops = Array(HALO_STOPS) { i ->
                    val t = i / (HALO_STOPS - 1f)
                    t to Color.White.copy(alpha = haloAt(t * HaloReach))
                }
                val c = Offset(size.width / 2f, size.height / 2f)
                val halo = Brush.radialGradient(*stops, center = c, radius = HaloReach * density)
                onDrawBehind {
                    val wave = haloWave(clock.value, still)
                    val s = lerp(HALO_SCALE_LOW, HALO_SCALE_HIGH, wave).toFloat()
                    scale(s, s, pivot = c) {
                        drawCircle(halo, HaloReach * density, c, lerp(HALO_ALPHA_LOW, HALO_ALPHA_HIGH, wave).toFloat())
                    }
                }
            },
    ) {
        scale(density, density, pivot = Offset.Zero) {
            val now = clock.value
            val e = events()
            val c = Offset(BADGE / 2f, BADGE / 2f)
            ringPulse(e, now, still)?.let { t ->
                val k = easeOut(t).toFloat()
                val radius = BADGE / 2f * (1f + (PULSE_TO.toFloat() - 1f) * k)
                drawCircle(Color.White, radius, c, PULSE_ALPHA.toFloat() * (1f - k), Stroke(PULSE_STROKE))
            }
            drawCircle(BadgeFill, BADGE / 2f, c)
            drawCircle(BadgeBorder, BADGE / 2f - BADGE_BORDER / 2f, c, style = Stroke(BADGE_BORDER))
            paperclip(c, BADGE_GLYPH, Color.White, BADGE_GLYPH_STROKE, badgeTurn(e, now, still).toFloat())
        }
    }
}

@Composable
private fun SelectButton(
    clock: State<Double>,
    events: () -> Events,
    still: Boolean,
    on: Boolean,
    touch: MutableInteractionSource,
    onSelect: () -> Unit,
    label: String,
) {
    fun level(): Float {
        val now = clock.value
        val e = events()
        return (OFF_ALPHA + (1 - OFF_ALPHA) * e.level(now, FADE) { selectOn(it) }).toFloat()
    }
    Box(
        Modifier
            .size(SELECT_WIDTH.dp, SELECT_HEIGHT.dp)
            .graphicsLayer {
                val e = events()
                val s = if (still) 1f else pressScale(e.selectDownAt, e.selectLiftAt, clock.value, SELECT_PRESS).toFloat()
                scaleX = s
                scaleY = s
            }
            .background(SelectFill, CircleShape)
            .drawBehind {
                val stroke = 1.dp.toPx()
                drawRoundRect(
                    SelectBorder,
                    Offset(stroke / 2f, stroke / 2f),
                    Size(size.width - stroke, size.height - stroke),
                    CornerRadius(size.height / 2f),
                    Stroke(stroke),
                    level(),
                )
            }
            .clickable(interactionSource = touch, indication = null, enabled = on, role = Role.Button, onClick = onSelect)
            .semantics { contentDescription = label },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            label,
            color = SelectLabel,
            fontSize = SELECT_LABEL.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = level() },
        )
    }
}
