package com.ngocdevv.nativecook.blocks.onboarding.fighterprofiletour

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerInputScope
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.imageResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

// Port of FighterProfileTour.tsx. Stage coordinates are dp; the stage (393 wide) is scaled to fit.

private val YELLOW = Color(0xFFFFDB35)
private val CTA_INK = Color(0xFF071F69)
private val CHECK_INK = Color(0xFF15318C)
private val DASH = Color(0xFFFFA900)

private fun rgba(r: Int, g: Int, b: Int, a: Float) = Color(r / 255f, g / 255f, b / 255f, a)

// Card surfaces are translucent shades of the page, so a fighter's colour runs through all of them.
private val SHADE = rgba(12, 16, 60, 0.35f)
private val SHADE_SELECTED = rgba(12, 16, 60, 0.25f)
private val SHADE_SOFT = rgba(12, 16, 60, 0.31f)
private val LIFT = rgba(255, 255, 255, 0.12f)
private val HALO_FILL = rgba(255, 255, 255, 0.055f)
private val PRESS = Color.White.copy(alpha = 0x18 / 255f)
private val WHITE_86 = rgba(255, 255, 255, 0.86f)
private val WHITE_78 = rgba(255, 255, 255, 0.78f)

private fun lerpColor(a: Color, b: Color, p: Float) =
    Color(mix(a.red, b.red, p), mix(a.green, b.green, p), mix(a.blue, b.blue, p), mix(a.alpha, b.alpha, p))

// ---------------------------------------------------------------------------------------------
// Helpers

/** Lays the child out at exactly this size, even when it is larger than the incoming constraints. */
private fun Modifier.exactSize(width: Float, height: Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width.dp.roundToPx(), height.dp.roundToPx()))
    layout(placeable.width, placeable.height) { placeable.place(0, 0) }
}

private fun Modifier.at(x: Float, y: Float) = offset(x.dp, y.dp)

private fun Modifier.hiddenUnless(active: Boolean) = if (active) this else clearAndSetSemantics { }

/** `maxFontSizeMultiplier` 1.15: sizes are capped against the system font scale. */
@Composable
private fun T(
    text: String,
    size: Float,
    color: Color,
    modifier: Modifier = Modifier,
    weight: FontWeight = FontWeight.Normal,
    lineHeight: Float? = null,
    spacing: Float = 0f,
    capped: Boolean = true,
    maxLines: Int = Int.MAX_VALUE,
    softWrap: Boolean = true,
    align: TextAlign? = null,
    italic: Boolean = false,
    tabular: Boolean = false,
    shadow: Shadow? = null,
    onTextLayout: (TextLayoutResult) -> Unit = {},
) {
    val fontScale = LocalDensity.current.fontScale
    val k = if (capped) min(fontScale, 1.15f) / fontScale else 1f
    val style = TextStyle(
        color = color,
        fontSize = (size * k).sp,
        fontWeight = weight,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        letterSpacing = (spacing / fontScale).sp,
        lineHeight = if (lineHeight != null) (lineHeight * k).sp else TextUnit.Unspecified,
        lineHeightStyle = if (lineHeight != null) {
            LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)
        } else {
            null
        },
        fontFeatureSettings = if (tabular) "tnum" else null,
        textAlign = align ?: TextAlign.Unspecified,
        shadow = shadow,
    )
    Text(
        text = text,
        modifier = modifier,
        style = style,
        maxLines = maxLines,
        softWrap = softWrap,
        overflow = if (maxLines == 1 && softWrap) TextOverflow.Ellipsis else TextOverflow.Clip,
        onTextLayout = onTextLayout,
    )
}

@Composable
private fun Glyph(icon: ImageVector, size: Float, tint: Color, modifier: Modifier = Modifier) {
    Icon(imageVector = icon, contentDescription = null, tint = tint, modifier = modifier.size(size.dp))
}

private fun DrawScope.drawArtLocal(image: ImageBitmap?, r: StageRect) {
    if (image == null) return
    withTransform({
        translate(r.x, r.y)
        scale(r.width / image.width, r.height / image.height, Offset.Zero)
    }) {
        drawImage(image, filterQuality = FilterQuality.High)
    }
}

/** Draws the art stretched into [r] (dp, relative to the draw origin) like `contentFit="fill"`. */
private fun DrawScope.drawArt(image: ImageBitmap?, r: StageRect) {
    val px = density
    withTransform({ scale(px, px, Offset.Zero) }) { drawArtLocal(image, r) }
}

private fun DrawScope.roundRectPath(x: Float, y: Float, w: Float, h: Float, radius: Float) = Path().apply {
    addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(radius, radius)))
}

@Composable
private fun BoxScope.PressHighlight(pressed: Boolean, radius: Float = 10f) {
    val amount by animateFloatAsState(if (pressed) 1f else 0f, tween(120, easing = EASE_OUT), label = "press")
    Box(
        Modifier.matchParentSize().drawBehind {
            drawRoundRect(
                PRESS.copy(alpha = PRESS.alpha * amount),
                cornerRadius = CornerRadius(radius.dp.toPx(), radius.dp.toPx()),
            )
        },
    )
}

/** Fades and shifts its content with the scene time: `arrival * (1 - departure)`. */
@Composable
private fun MotionLayer(
    ctl: TourController,
    modifier: Modifier = Modifier,
    enter: Pair<Float, Float>? = null,
    exit: Pair<Float, Float>? = null,
    shift: Float = 10f,
    scaleFrom: Float = 1f,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        modifier.graphicsLayer {
            val t = ctl.time()
            val arrival = if (enter != null) ramp(t, enter.first, enter.second) else 1f
            val departure = if (exit != null) ramp(t, exit.first, exit.second) else 0f
            alpha = arrival * (1f - departure)
            translationY = (shift * (1f - arrival) - shift * departure) * density
            val s = mix(scaleFrom, 1f, arrival)
            scaleX = s
            scaleY = s
        },
        content = content,
    )
}

// ---------------------------------------------------------------------------------------------
// Pieces

@Composable
private fun Heading(ctl: TourController, word: String, rest: String, step: Int) {
    val enter = when (step) { 1 -> null; 2 -> 0.18f to 0.205f; else -> 0.595f to 0.62f }
    val exit = when (step) { 1 -> 0.14f to 0.168f; 2 -> 0.48f to 0.505f; else -> null }
    val top = when (step) { 3 -> 109f; 2 -> 94f; else -> 98f }
    MotionLayer(ctl, Modifier.at(24f, top).width(345.dp), enter, exit, shift = 8f) {
        Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
            T(
                word, if (step == 2) 35f else 39f, YELLOW, Modifier.alignByBaseline().semantics { heading() },
                weight = FontWeight.ExtraBold, lineHeight = if (step == 2) 42f else 47f, spacing = -1f,
            )
            T(rest, 26f, Color.White, Modifier.alignByBaseline(), weight = FontWeight.ExtraBold, spacing = -0.65f)
        }
    }
}

/** A roster card. Every one can be picked; picking the chosen one again starts the tour. */
@Composable
private fun RosterRow(
    ctl: TourController,
    fighter: Fighter,
    index: Int,
    isSelected: Boolean,
    accent: Color,
    interactive: Boolean,
    art: ImageBitmap?,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val tierColor by animateColorAsState(accent, tween(420, easing = EASE_OUT), label = "tier")
    val fit = containIn(fighter, fighter.rowFit)
    val label = if (isSelected) {
        "${fighter.name}, ${fighter.tier} tier, selected. Explore ${fighter.name}"
    } else {
        "${fighter.name}, ${fighter.tier} tier"
    }
    // The chosen card's art is handed to the travelling actor the moment the tour begins.
    MotionLayer(
        ctl,
        Modifier.fillMaxWidth(),
        exit = if (isSelected) 0.12f to 0.15f else (0.125f + index * 0.005f) to (0.185f + index * 0.005f),
        shift = if (isSelected) 0f else -6f,
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(ROW_HEIGHT.dp)
                .clip(RoundedCornerShape(12.dp))
                .semantics { contentDescription = label; this.selected = isSelected }
                .clickable(interaction, null, enabled = interactive, role = Role.Button) { ctl.pick(index) },
        ) {
            // The chosen card's surface is drawn by the morph, which later becomes the hero halo.
            if (!isSelected) Box(Modifier.matchParentSize().background(SHADE, RoundedCornerShape(12.dp)))
            PressHighlight(pressed, 12f)
            Column(Modifier.at(27f, 30f)) {
                T("${fighter.tier} TIER", 11f, tierColor, weight = FontWeight.SemiBold, spacing = 0.2f)
                Spacer(Modifier.height(2.dp))
                T(fighter.name, 21f, Color.White, weight = FontWeight.ExtraBold, spacing = -0.4f)
            }
            Box(
                Modifier
                    .at(ART_BOX_X - ROW_X, ART_BOX_TOP)
                    .size(149.dp, 112.dp)
                    .graphicsLayer {
                        alpha = if (isSelected) 1f - (if (ctl.time() >= 0.118f) 1f else 0f) else 1f
                    }
                    .drawBehind { drawArt(art, fit) },
            )
        }
    }
}

@Composable
private fun StatCard(ctl: TourController, index: Int, fighter: Fighter, accent: Color) {
    val stat = fighter.stats[index]
    MotionLayer(
        ctl,
        Modifier.size(STAT_CARD.dp, 100.dp),
        enter = (0.278f + index * 0.005f) to (0.306f + index * 0.005f),
        exit = (0.50f + index * 0.005f) to (0.53f + index * 0.005f),
        scaleFrom = 0.97f,
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .background(SHADE, RoundedCornerShape(9.dp))
                .padding(start = 14.dp, end = 14.dp, top = 11.dp),
        ) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Glyph(STAT_ICONS[index], 27f, accent)
                T(stat.value, 17f, Color.White, weight = FontWeight.ExtraBold, spacing = -0.3f, tabular = true)
            }
            T(STAT_LABELS[index], 12f, Color.White, Modifier.padding(top = 8.dp))
            Box(
                Modifier
                    .padding(top = 9.dp)
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(LIFT),
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .graphicsLayer {
                            scaleX = stat.amount * ramp(ctl.time(), 0.30f + index * 0.005f, 0.335f + index * 0.005f)
                            transformOrigin = TransformOrigin(0f, 0f)
                        }
                        .background(YELLOW, RoundedCornerShape(3.dp)),
                )
            }
        }
    }
}

/** Stat cards slide sideways like the reference: the demo glides through them, a finger can too. */
@Composable
private fun StatStrip(ctl: TourController, fighter: Fighter, accent: Color) {
    Row(
        Modifier
            .wrapContentWidth(Alignment.Start, unbounded = true)
            .graphicsLayer { translationX = -ctl.stripOffset() * density },
        horizontalArrangement = Arrangement.spacedBy(STAT_GAP.dp),
    ) {
        fighter.stats.indices.forEach { StatCard(ctl, it, fighter, accent) }
    }
}

@Composable
private fun Metric(
    ctl: TourController,
    label: String,
    yours: Float,
    theirs: Float,
    index: Int,
    cardIndex: Int,
    accent: Color,
) {
    fun grow() = ramp(
        ctl.time(),
        0.655f + cardIndex * 0.005f + index * 0.002f,
        0.69f + cardIndex * 0.005f + index * 0.002f,
    )
    Column(Modifier.padding(bottom = 5.dp)) {
        T(label, 12f, Color.White, Modifier.padding(bottom = 3.dp), lineHeight = 15f)
        Box(Modifier.fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(LIFT)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .graphicsLayer {
                        scaleX = theirs * grow()
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .background(accent, RoundedCornerShape(1.5.dp)),
            )
        }
        Box(Modifier.padding(top = 2.dp).fillMaxWidth().height(3.dp).clip(RoundedCornerShape(1.5.dp)).background(LIFT)) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .graphicsLayer {
                        scaleX = yours * grow()
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                    .background(YELLOW, RoundedCornerShape(1.5.dp)),
            )
        }
    }
}

@Composable
private fun MatchCard(
    ctl: TourController,
    index: Int,
    fighter: Fighter,
    you: Fighter,
    art: ImageBitmap?,
    active: Boolean,
    accent: Color,
) {
    val expanded = if (index == 0) ctl.firstExpanded else ctl.secondExpanded
    val isOpen = (if (index == 0) ctl.firstTarget else ctl.secondTarget) > 0.5f
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    val head = containIn(fighter, StageRect(12f, 9f, 87f, 101f))
    fun opening() = ramp(ctl.time(), 0.632f + index * 0.005f, 0.657f + index * 0.005f) * expanded.value

    Box(
        Modifier
            .at(0f, index * 205f)
            .fillMaxWidth()
            .height(192.dp)
            .graphicsLayer {
                val enter = ramp(ctl.time(), 0.6f + index * 0.006f, 0.628f + index * 0.006f)
                alpha = enter
                translationY = (12f * (1f - enter) - (if (index == 1) 128f * (1f - ctl.firstExpanded.value) else 0f)) * density
                val s = 0.975f + 0.025f * enter
                scaleX = s
                scaleY = s
                transformOrigin = TransformOrigin(0.5f, 0f)
            },
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    scaleY = (64f + 128f * opening()) / 192f
                    transformOrigin = TransformOrigin(0f, 0f)
                }
                .background(SHADE, RoundedCornerShape(11.dp)),
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .zIndex(2f)
                .clip(RoundedCornerShape(11.dp))
                .semantics { contentDescription = "${fighter.name} matchup statistics, ${fighter.tier} tier" }
                .clickable(interaction, null, enabled = active, role = Role.Button) { ctl.toggle(index == 1) },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clip(RoundedCornerShape(topStart = 11.dp, topEnd = 11.dp)),
            ) {
                // The counter lunges in from the right, the opponent's side, just after the VS slash.
                Box(
                    Modifier
                        .at(head.x, head.y)
                        .size(head.width.dp, head.height.dp)
                        .graphicsLayer {
                            val enter = ramp(ctl.time(), 0.6f + index * 0.006f, 0.638f + index * 0.006f, true)
                            translationX = 46f * (1f - enter) * density
                            val s = 1.12f - 0.12f * enter
                            scaleX = s
                            scaleY = s
                        }
                        .drawBehind { drawArt(art, StageRect(0f, 0f, head.width, head.height)) },
                )
            }
            PressHighlight(pressed, 11f)
            Column(Modifier.padding(start = 111.dp, end = 30.dp, top = 13.dp)) {
                T(fighter.name, 20f, Color.White, weight = FontWeight.ExtraBold, spacing = -0.3f)
                T("${fighter.tier} Tier", 13f, accent, Modifier.padding(top = 4.dp), capped = false)
            }
            Glyph(
                Icons.Filled.KeyboardArrowUp, 17f, accent,
                Modifier
                    .align(Alignment.TopEnd)
                    .offset((-16).dp, 23.dp)
                    .graphicsLayer { rotationZ = 180f * (1f - expanded.value) },
            )
        }
        Box(
            Modifier
                .at(0f, 64f)
                .fillMaxWidth()
                .height(128.dp)
                .clipToBounds()
                .hiddenUnless(isOpen),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(128.dp)
                    .graphicsLayer {
                        alpha = ramp(ctl.time(), 0.642f + index * 0.005f, 0.667f + index * 0.005f)
                        translationY = -128f * (1f - opening()) * density
                    },
            ) {
                Box(
                    Modifier
                        .at(head.x, head.y - 64f)
                        .size(head.width.dp, head.height.dp)
                        .drawBehind { drawArt(art, StageRect(0f, 0f, head.width, head.height)) },
                )
                Column(
                    Modifier
                        .at(111f, 0f)
                        .width(219.dp)
                        .semantics(mergeDescendants = true) {
                            contentDescription = METRIC_LABELS.mapIndexed { metric, label ->
                                "$label: ${fighter.name} ${Math.round(fighter.metrics[metric] * 100)}, " +
                                    "${you.name} ${Math.round(you.metrics[metric] * 100)}"
                            }.joinToString(". ")
                        },
                ) {
                    METRIC_LABELS.forEachIndexed { metric, label ->
                        Metric(ctl, label, you.metrics[metric], fighter.metrics[metric], metric, index, accent)
                    }
                }
            }
        }
    }
}

/** A Smash-style VS beat between learning the fighter and meeting its counters. */
@Composable
private fun VersusSlash(ctl: TourController, accent: Color, height: Float) {
    Box(Modifier.exactSize(WIDTH, height).clearAndSetSemantics { }) {
        Box(
            Modifier
                .at(-40f, -60f)
                .exactSize(WIDTH + 80f, height + 120f)
                .graphicsLayer {
                    val t = ctl.time()
                    alpha = if (t > 0.566f && t < 0.586f) 0.16f * sin(Math.PI.toFloat() * ramp(t, 0.566f, 0.586f)) else 0f
                }
                .background(Color.White),
        )
        Box(
            Modifier
                .at(-60f, 452f)
                .exactSize(520f, 14f)
                .graphicsLayer {
                    val t = ctl.time()
                    val p = ramp(t, 0.552f, 0.59f, true)
                    alpha = if (t > 0.552f && t < 0.6f) sin(Math.PI.toFloat() * ramp(t, 0.552f, 0.6f)) else 0f
                    // rotate(-22deg) then translateX along the rotated axis, as the RN transform list does.
                    val angle = (-22f * Math.PI / 180.0).toFloat()
                    val travel = mix(-430f, 430f, p)
                    rotationZ = -22f
                    translationX = travel * cos(angle) * density
                    translationY = travel * sin(angle) * density
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxWidth().height(14.dp).graphicsLayer { alpha = 0.45f }.background(accent, RoundedCornerShape(7.dp)))
            Box(Modifier.fillMaxWidth().height(3.dp).background(Color.White, RoundedCornerShape(1.5.dp)))
        }
        Box(
            Modifier
                .at(0f, 414f)
                .fillMaxWidth()
                .graphicsLayer {
                    val t = ctl.time()
                    val arrive = ramp(t, 0.556f, 0.574f)
                    val leave = ramp(t, 0.588f, 0.606f)
                    alpha = arrive * (1f - leave)
                    val s = mix(1.6f, 1f, arrive) + leave * 0.18f
                    scaleX = s
                    scaleY = s
                    rotationZ = -8f
                },
            contentAlignment = Alignment.TopCenter,
        ) {
            val density = LocalDensity.current
            T(
                "VS", 78f, YELLOW, capped = false, weight = FontWeight.Black, lineHeight = 86f, spacing = -4f, italic = true,
                shadow = Shadow(Color(7 / 255f, 31 / 255f, 105 / 255f, 0.55f), Offset(0f, with(density) { 4.dp.toPx() }), 0f),
            )
        }
    }
}

/** One fighter's page gradient; picking a fighter cross-fades them. Villager's is the base. */
@Composable
private fun PageLayer(fighter: Fighter, index: Int, weight: Motion) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer { alpha = if (index == 0) 1f else weight.value }
            .background(Brush.verticalGradient(fighter.page)),
    )
}

@Composable
private fun HeroName(ctl: TourController, name: String) {
    // adjustsFontSizeToFit: shrink until the single line fits.
    var fit by remember(name) { mutableFloatStateOf(1f) }
    MotionLayer(ctl, Modifier.at(20f, 143f).width(353.dp), 0.225f to 0.25f, 0.48f to 0.51f) {
        T(
            name, 55f * fit, YELLOW, Modifier.fillMaxWidth(), weight = FontWeight.ExtraBold, lineHeight = 55f * fit,
            spacing = -1.6f, maxLines = 1, softWrap = false, align = TextAlign.Center,
            onTextLayout = { if (it.didOverflowWidth && fit > 0.4f) fit *= 0.96f },
        )
    }
}

// ---------------------------------------------------------------------------------------------
// Gestures

/**
 * A one-finger horizontal pan: activates past [activateX] dp sideways, fails past [failY] dp
 * vertically, and gives way to a child that already consumed the movement.
 */
private suspend fun PointerInputScope.detectPan(
    activateX: Float,
    failY: Float,
    enabled: () -> Boolean,
    onStart: (translationX: Float) -> Unit,
    onUpdate: (translationX: Float) -> Unit,
    onEnd: (velocityX: Float) -> Unit,
    onCancel: () -> Unit,
) {
    awaitEachGesture {
        val down = awaitFirstDown(requireUnconsumed = false)
        if (!enabled()) return@awaitEachGesture
        val unit = density
        val tracker = VelocityTracker()
        tracker.addPosition(down.uptimeMillis, down.position)
        var active = false
        var finished = false
        try {
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (!active && (event.changes.count { it.pressed } > 1 || change.isConsumed)) break
                val tx = (change.position.x - down.position.x) / unit
                val ty = (change.position.y - down.position.y) / unit
                tracker.addPosition(change.uptimeMillis, change.position)
                if (!active) {
                    if (abs(ty) > failY) break
                    if (change.pressed && abs(tx) > activateX) {
                        active = true
                        onStart(tx)
                    }
                }
                if (active) {
                    if (change.pressed) {
                        onUpdate(tx)
                        change.consume()
                    } else {
                        finished = true
                        onEnd(tracker.calculateVelocity().x / unit)
                        break
                    }
                } else if (!change.pressed) {
                    break
                }
            }
        } finally {
            if (active && !finished) onCancel()
        }
    }
}

// ---------------------------------------------------------------------------------------------
// The block

@Composable
fun FighterProfileTour(
    autoPlay: Boolean = true,
    /** Deterministic preview position in seconds, from 0 to [LOOP]. */
    previewTime: Float? = null,
    previewReducedMotion: Boolean = false,
    onComplete: (() -> Unit)? = null,
) {
    BlockSystemBars(darkIcons = false)
    val ctl = remember { TourController() }
    ctl.autoPlay = autoPlay
    ctl.controlled = previewTime != null
    ctl.onComplete = onComplete

    val density = LocalDensity.current
    val view = LocalView.current
    val systemReduced = rememberLiveReducedMotion()
    val reduced = systemReduced || previewReducedMotion
    SideEffect {
        ctl.reduced = reduced
        ctl.haptic = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    }

    val resources = LocalContext.current.resources
    val bitmaps by produceState<List<ImageBitmap?>>(List(FIGHTERS.size) { null }) {
        value = withContext(Dispatchers.Default) { FIGHTERS.map { ImageBitmap.imageResource(resources, it.art) } }
    }

    var sizeWidth by remember { mutableFloatStateOf(393f) }
    var sizeHeight by remember { mutableFloatStateOf(852f) }
    val bars = WindowInsets.systemBars
    val insetTop = with(density) { bars.getTop(density).toDp().value }
    val insetBottom = with(density) { bars.getBottom(density).toDp().value }
    val available = max(1f, sizeHeight - insetTop - insetBottom)
    // Background always fills the device; content also fits compact phones without clipping its CTA.
    val scale = min(sizeWidth / WIDTH, available / 770f)
    val stageHeight = available / scale + 44f
    val stageWidth = WIDTH * scale
    val stageLeft = (sizeWidth - stageWidth) / 2f
    val stageTop = insetTop - 44f * scale
    ctl.stageWidth = stageWidth

    val you = FIGHTERS[ctl.selected]
    val counters = listOf(you.counters.first, you.counters.second).map { FIGHTERS[FIGHTER_INDEX.getValue(it)] }
    val activeStep = ctl.activeStep

    // One frame loop drives every animation and the demo clock.
    LaunchedEffect(ctl) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                val dt = if (last == 0L) 0f else (now - last) / 1_000_000f
                last = now
                ctl.tick(dt)
            }
        }
    }
    DisposableEffect(autoPlay, previewTime) {
        ctl.progress.cancel()
        ctl.playing = autoPlay && previewTime == null
        if (previewTime != null) ctl.progress.set(clamp(previewTime / LOOP))
        onDispose {
            ctl.playing = false
            ctl.progress.cancel()
            ctl.sweep.cancel()
        }
    }
    // Leaving the screen or the app pauses the demo; coming back settles to a held pose and resumes.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner, ctl) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> {
                    ctl.routeFocused = true
                    ctl.restoreAfterInterruption()
                }
                Lifecycle.Event.ON_PAUSE -> {
                    ctl.routeFocused = false
                    ctl.pauseForInterruption()
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            ctl.routeFocused = false
            ctl.pauseForInterruption()
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(FIGHTERS[0].page[1])
            .clipToBounds()
            .onSizeChanged {
                sizeWidth = with(density) { it.width.toDp().value }
                sizeHeight = with(density) { it.height.toDp().value }
            }
            .pointerInput(ctl) {
                detectPan(
                    activateX = 14f, failY = 18f, enabled = { true },
                    onStart = { ctl.panStart(it) }, onUpdate = { ctl.panUpdate(it) },
                    onEnd = { ctl.panEnd(it) }, onCancel = { ctl.panCancel() },
                )
            },
    ) {
        FIGHTERS.forEachIndexed { index, fighter -> PageLayer(fighter, index, ctl.weights[index]) }
        Box(
            Modifier.layout { measurable, constraints ->
                val placeable = measurable.measure(Constraints.fixed(WIDTH.dp.roundToPx(), stageHeight.dp.roundToPx()))
                layout(constraints.maxWidth, constraints.maxHeight) {
                    placeable.placeWithLayer(stageLeft.dp.roundToPx(), stageTop.dp.roundToPx()) {
                        scaleX = scale
                        scaleY = scale
                        transformOrigin = TransformOrigin(0f, 0f)
                    }
                }
            },
        ) {
            Stage(ctl, you, counters, bitmaps, activeStep, stageHeight)
        }
    }
}

@Composable
private fun Stage(
    ctl: TourController,
    you: Fighter,
    counters: List<Fighter>,
    bitmaps: List<ImageBitmap?>,
    activeStep: Int,
    stageHeight: Float,
) {
    val selectedIndex = ctl.selected
    val youArt = bitmaps[selectedIndex]
    val completed = ctl.completed

    // The halo behind the hero.
    Box(
        Modifier
            .at(-13f, 55f)
            .exactSize(420f, 420f)
            .background(rgba(255, 255, 255, 0.04f), CircleShape),
    )

    // Top bar: step count and Skip.
    Row(
        Modifier
            .at(24f, 49f)
            .width(353.dp)
            .height(38.dp)
            .zIndex(9f),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        T(
            "$activeStep of 3", 15f, rgba(255, 255, 255, 0.86f), capped = false,
            modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite },
        )
        val skipInteraction = remember { MutableInteractionSource() }
        val skipPressed by skipInteraction.collectIsPressedAsState()
        Box(
            Modifier
                .widthIn(min = 60.dp)
                .requiredHeightIn(min = 44.dp)
                .clip(RoundedCornerShape(10.dp))
                .semantics { contentDescription = "Skip to matchups" }
                .clickable(skipInteraction, null, role = Role.Button) { ctl.commit(CommitKind.Skip) },
            contentAlignment = Alignment.Center,
        ) {
            PressHighlight(skipPressed)
            T("Skip", 15f, Color.White, capped = false)
        }
    }

    // The chosen card's own surface morphs into the hero halo behind the fighter.
    Box(
        Modifier
            .exactSize(WIDTH, stageHeight)
            .drawBehind {
                val t = ctl.time()
                val m = ramp(t, 0.12f, 0.26f, true)
                val shape = mixRect(StageRect(ROW_X, rowTop(ctl.ringRow.value), ROW_WIDTH, ROW_HEIGHT), HALO, m)
                val fill = lerpColor(SHADE_SELECTED, HALO_FILL, m)
                val radius = mix(12f, HALO.width / 2f, m)
                val opacity = 1f - ramp(t, 0.48f, 0.535f)
                drawRoundRect(
                    color = fill.copy(alpha = fill.alpha * opacity),
                    topLeft = Offset(shape.x * density, shape.y * density),
                    size = Size(shape.width * density, shape.height * density),
                    cornerRadius = CornerRadius(radius * density, radius * density),
                )
            },
    )

    // Step 1: pick your fighter.
    Box(Modifier.fillMaxSize().hiddenUnless(activeStep == 1)) {
        Heading(ctl, "Pick", "your fighter", 1)
        Column(
            Modifier.at(ROW_X, ROSTER_TOP).width(ROW_WIDTH.dp),
            verticalArrangement = Arrangement.spacedBy((ROW_PITCH - ROW_HEIGHT).dp),
        ) {
            FIGHTERS.forEachIndexed { index, fighter ->
                RosterRow(ctl, fighter, index, index == selectedIndex, you.accent, activeStep == 1, bitmaps[index])
            }
        }
        Box(
            Modifier
                .at(ROW_X, 0f)
                .exactSize(ROW_WIDTH, ROW_HEIGHT + 3f)
                .zIndex(7f)
                .graphicsLayer {
                    alpha = 1f - ramp(ctl.time(), 0.12f, 0.15f)
                    translationY = (rowTop(ctl.ringRow.value) - 1.5f) * density
                }
                .border(2.5.dp, YELLOW, RoundedCornerShape(13.dp)),
        ) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset((-14).dp, 37.5.dp)
                    .size(24.dp)
                    .background(YELLOW, CircleShape),
                contentAlignment = Alignment.Center,
            ) { Glyph(Icons.Filled.Check, 18f, CHECK_INK) }
        }
    }

    // Step 2: know your strengths.
    Box(Modifier.fillMaxSize().hiddenUnless(activeStep == 2)) {
        Heading(ctl, "Know", "your strengths", 2)
        HeroName(ctl, you.name)
        MotionLayer(ctl, Modifier.at(24f, 454f).width(345.dp), 0.265f to 0.29f, 0.49f to 0.52f) {
            Column {
                Row(Modifier.height(29.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp, 4.dp).background(DASH, RoundedCornerShape(2.dp)))
                    T("Overview", 23f, Color.White, capped = false, weight = FontWeight.Bold, spacing = -0.5f)
                }
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier.widthIn(min = 181.dp).height(30.dp).background(SHADE_SOFT, RoundedCornerShape(8.dp)).padding(horizontal = 14.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(you.seriesIcon, 19f, you.accent)
                            T(you.series.uppercase(), 10f, Color.White, weight = FontWeight.Bold, maxLines = 1)
                        }
                    }
                    Box(
                        Modifier.width(100.dp).height(30.dp).background(SHADE_SOFT, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(9.dp), verticalAlignment = Alignment.CenterVertically) {
                            T(you.tier, 19f, you.accent, capped = false, weight = FontWeight.ExtraBold)
                            T("TIER", 10f, Color.White, capped = false, weight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
        MotionLayer(ctl, Modifier.at(24f, 536f).width(345.dp), 0.270f to 0.295f, 0.495f to 0.525f) {
            T(fighterSummary(you), 14f, WHITE_86, lineHeight = 21f)
        }
        Box(
            Modifier
                .at(20f, 612f)
                .width(353.dp)
                .height(100.dp)
                .semantics(mergeDescendants = true) {
                    contentDescription = you.stats.mapIndexed { index, stat -> "${STAT_LABELS[index]} ${stat.value}" }.joinToString(", ")
                }
                .pointerInput(ctl) {
                    detectPan(
                        activateX = 8f, failY = 14f, enabled = { ctl.activeStep == 2 },
                        onStart = { ctl.statsStart() }, onUpdate = { ctl.statsUpdate(it) },
                        onEnd = { ctl.statsEnd(it) }, onCancel = { },
                    )
                },
        ) {
            StatStrip(ctl, you, you.accent)
        }
    }

    // Step 3: find your matchup.
    Box(Modifier.fillMaxSize().hiddenUnless(activeStep == 3)) {
        Heading(ctl, "Find", "your matchup", 3)
        MotionLayer(ctl, Modifier.at(24f, 186f).width(345.dp), enter = 0.598f to 0.623f) {
            Column {
                Row(Modifier.fillMaxWidth().height(29.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(22.dp, 4.dp).background(DASH, RoundedCornerShape(2.dp)))
                    T("Counter Matchups", 23f, Color.White, weight = FontWeight.Bold, spacing = -0.5f)
                    Row(
                        Modifier
                            .weight(1f)
                            .semantics(mergeDescendants = true) { contentDescription = "Yellow bars are ${you.name}, coloured bars are the counter" },
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.End),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(Modifier.size(8.dp).background(YELLOW, CircleShape))
                        T("You", 11f, WHITE_78, weight = FontWeight.SemiBold)
                        Box(Modifier.padding(start = 8.dp).size(8.dp).background(you.accent, CircleShape))
                        T("Them", 11f, WHITE_78, weight = FontWeight.SemiBold)
                    }
                }
                MotionLayer(ctl, enter = 0.603f to 0.628f, shift = 6f) {
                    T(
                        "These are the fighters ${you.name} struggles against. Learn their strengths before your next match.",
                        16f, WHITE_86, Modifier.padding(top = 17.dp), lineHeight = 24f,
                    )
                }
            }
        }
        Box(Modifier.at(20f, 321f).width(353.dp).height(397.dp)) {
            MatchCard(ctl, 0, counters[0], you, bitmaps[FIGHTER_INDEX.getValue(counters[0].key)], activeStep == 3, you.accent)
            MatchCard(ctl, 1, counters[1], you, bitmaps[FIGHTER_INDEX.getValue(counters[1].key)], activeStep == 3, you.accent)
        }
        VersusSlash(ctl, you.accent, stageHeight)
    }

    // The pinned badge the fighter ends up in.
    Box(
        Modifier
            .at(PINNED_BOX.x - 8f, PINNED_BOX.y - 6f)
            .size((PINNED_BOX.width + 16f).dp)
            .zIndex(4f)
            .graphicsLayer {
                val p = ramp(ctl.time(), 0.6f, 0.68f)
                alpha = p
                val s = 0.8f + 0.2f * p
                scaleX = s
                scaleY = s
            }
            .background(rgba(255, 255, 255, 0.08f), CircleShape)
            .border(2.dp, YELLOW, CircleShape),
    )

    // The chosen fighter leaves its card: the clip grows from the card to the page, so the art
    // breaks out of its frame as it lifts, then the same art becomes the hero and later the badge.
    val fromRect = rowArtRect(you, selectedIndex)
    val heroRect = standIn(you, HERO_BOX)
    val pinnedRect = standIn(you, PINNED_BOX)
    Canvas(
        Modifier
            .exactSize(WIDTH, stageHeight)
            .zIndex(5f)
            .semantics { contentDescription = "${you.name}, your fighter" },
    ) {
        val t = ctl.time()
        if (t < 0.118f) return@Canvas
        val open = ramp(t, 0.12f, 0.2f, true)
        val card = StageRect(ROW_X, rowTop(ctl.ringRow.value), ROW_WIDTH, ROW_HEIGHT)
        val clip = mixRect(card, StageRect(0f, 0f, WIDTH, stageHeight), open)
        val art = mixRect(mixRect(fromRect, heroRect, ramp(t, 0.12f, 0.32f, true)), pinnedRect, ramp(t, 0.47f, 0.6f, true))
        scale(density, density, Offset.Zero) {
            clipPath(roundRectPath(clip.x, clip.y, clip.width, clip.height, mix(12f, 0f, open))) {
                drawArtLocal(youArt, art)
            }
        }
    }

    // Footer: step dots and the Continue button.
    Column(
        Modifier
            .at(20f, stageHeight - 88f)
            .width(353.dp)
            .zIndex(8f),
    ) {
        Box(
            Modifier
                .align(Alignment.CenterHorizontally)
                .size(178.dp, 6.dp)
                .semantics { contentDescription = "Step $activeStep of 3" },
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                repeat(3) { Box(Modifier.size(54.dp, 6.dp).background(rgba(255, 255, 255, 0.13f), RoundedCornerShape(3.dp))) }
            }
            Box(
                Modifier
                    .size(54.dp, 6.dp)
                    .graphicsLayer {
                        val t = ctl.time()
                        translationX = mix(mix(0f, 62f, ramp(t, 0.12f, 0.32f, true)), 124f, ramp(t, 0.47f, 0.68f, true)) * density
                    }
                    .background(YELLOW, RoundedCornerShape(3.dp)),
            )
        }
        Spacer(Modifier.height(16.dp))
        val ctaInteraction = remember { MutableInteractionSource() }
        val ctaPressed by ctaInteraction.collectIsPressedAsState()
        val reducedNow = ctl.reduced
        val ctaScale by animateFloatAsState(if (ctaPressed && !reducedNow) 0.97f else 1f, tween(120, easing = EASE_OUT), label = "ctaScale")
        val ctaAlpha by animateFloatAsState(if (ctaPressed && reducedNow) 0.9f else 1f, tween(120, easing = EASE_OUT), label = "ctaAlpha")
        val label = when {
            activeStep == 3 && completed -> "${you.name} is ready to train"
            activeStep == 3 -> "Finish onboarding and start training"
            else -> "Continue to step ${activeStep + 1}"
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(52.dp)
                .semantics { contentDescription = label }
                .clickable(ctaInteraction, null, role = Role.Button) { ctl.commit(CommitKind.Continue) },
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .graphicsLayer {
                        scaleX = ctaScale
                        scaleY = ctaScale
                        alpha = ctaAlpha
                    }
                    .clip(RoundedCornerShape(14.dp))
                    .background(YELLOW),
                contentAlignment = Alignment.Center,
            ) {
                Row(
                    Modifier.graphicsLayer {
                        val t = ctl.time()
                        alpha = if (t < 0.575f) 1f - ramp(t, 0.56f, 0.573f) else ramp(t, 0.58f, 0.595f)
                    },
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    T(
                        if (activeStep == 3) (if (completed) "Ready to train" else "Start training") else "Continue",
                        18f, CTA_INK, weight = FontWeight.ExtraBold, spacing = -0.4f,
                    )
                    Glyph(Icons.AutoMirrored.Filled.KeyboardArrowRight, 22f, CTA_INK)
                }
                CommitShimmer(sweep = { ctl.sweep.value }, strength = { if (ctl.reduced) 0f else 1f })
            }
        }
    }
}
