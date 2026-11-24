package com.ngocdevv.nativecook.blocks.text.glyphreflowedit

import android.graphics.Paint
import android.graphics.Typeface
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.IN_LENGTH
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.IN_STAGGER
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.IN_START
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.LOOP
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.MORPH_A1
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.OUT_END
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.OUT_STAGGER
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.RESUME_AFTER
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.SCRUB_BACK0
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.SPREAD
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.SPRING_DAMPING
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.SPRING_STIFFNESS
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.easeInOutCubic
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.easeOutCubic
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.ghostOpacityAt
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.ghostPressAt
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.mix
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.progressAt
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.rubber
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.seg
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.toneAt
import com.ngocdevv.nativecook.blocks.text.glyphreflowedit.GlyphReflowEditMotion.toneIndicatorAt
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

/*
 Glyph Reflow Edit — "Quill", a writing assistant rewrites a paragraph and the letters both versions share glide to
 their new places. A port of the React Native block (GlyphReflowEdit.tsx); the numbers are the same.

 autoPlay (LOOP = 9 s, GlyphReflowEditMotion.kt): rest on the original; a ghost finger taps Shorter at 0.9 s and the
 text morphs 0.95 – 2.35 s; hold; the ghost drags the bar back 3.55 – 4.35 s; the pill moves to Warmer and the text
 morphs 4.85 – 6.25 s; hold; back to the original 7.05 – 7.85 s; the pill returns 7.95 – 8.2 s; rest.

 Touch: tap a chip (a second tap on the chip shown toggles between the two versions), or scrub the bar, which
 rubber-bands past its ends and settles with the finger's velocity into a spring (stiffness 170, damping 22.2).
 2 s after the last touch it returns to the original and the loop restarts. Reduced motion: the first rewrite, in
 place, no loop; chips swap the text at once and the bar still scrubs.

 Drawing: every glyph is measured once (GlyphReflowEditLayout.kt); one Canvas draws them per frame from the morph
 value with single-character drawText calls, in eight alpha buckets of prebuilt paints like the RN version.
 */

class GlyphReflowVersion(val label: String, val text: String)

const val GLYPH_REFLOW_DOCUMENT = "Note to the team"

const val GLYPH_REFLOW_ORIGINAL =
    "Hi everyone, I wanted to let you know that the launch of the new onboarding has been moved to next Thursday because we still need a little more time to finish testing the payment screens. Please send me anything that is still open by Tuesday."

val GLYPH_REFLOW_VERSIONS = listOf(
    GlyphReflowVersion(
        "Shorter",
        "Hi all, the new onboarding now launches next Thursday. We need more time to test the payment screens. Please send open items by Tuesday.",
    ),
    GlyphReflowVersion(
        "Warmer",
        "Hi everyone, thank you for your patience. We are moving the new onboarding to next Thursday so the payment screens get the testing they deserve. If anything is still open, send it my way by Tuesday.",
    ),
    GlyphReflowVersion(
        "Clearer",
        "The new onboarding launch moves to next Thursday. Testing of the payment screens is not finished. Send me every open item by Tuesday.",
    ),
)

private val DESK = Color(0xFFE8EBE4)
private val PAPER = Color(0xFFFBFBF6)
private val INK = Color(0xFF1F2522)
private val INK_SOFT = Color(0xFF6C756F)
private val HAIRLINE = Color(31 / 255f, 37 / 255f, 34 / 255f, 0.1f)
private val ACCENT = Color(0xFF2C6E51)
private val ACCENT_SOFT = Color(0xFFD9E7DF)

private const val SIDE = 16f
private const val MAX_WIDTH = 560f
private const val HEADER = 56f
private const val CARD_PAD = 20f
private const val GAP = 12f
private const val CHIPS = 44f
private const val BAR = 58f
private const val BAR_LABELS = 20f
private const val STATS = 68f
private const val THUMB = 28f
private val SIZES = intArrayOf(31, 30, 29, 28, 27, 26, 25, 24, 23, 22, 21, 20, 19, 18, 17, 16)
private const val LEADING = 1.38f
private const val BUCKETS = 8

private const val MODE_AUTO = 0
private const val MODE_MANUAL = 1

private fun ink(a: Float) = Color(31 / 255f, 37 / 255f, 34 / 255f, a)

/** The chosen type size's paints and one morph per rewrite. */
private class GlyphPage(val morphs: List<GlyphMorph>, val ink: Paint, val out: Array<Paint>, val incoming: Array<Paint>)

private fun buildPage(original: String, versions: List<GlyphReflowVersion>, textWidth: Float, textRoom: Float, density: Float): GlyphPage {
    val typeface = Typeface.create("sans-serif", Typeface.NORMAL)
    fun paintOf(size: Int, color: Int) = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
        this.typeface = typeface
        textSize = size * density
        this.color = color
        // Each letter is measured and drawn alone: a ligature ("fi") would give its whole advance to the f and none
        // to the i, which then sits under the next letter.
        fontFeatureSettings = "liga 0, clig 0"
    }
    var chosenSize = SIZES.last()
    var morphs: List<GlyphMorph> = emptyList()
    for (size in SIZES) {
        val paint = paintOf(size, INK.toArgb())
        val lineHeight = GlyphLayout.leadingOf(size, LEADING)
        val base = GlyphLayout.layOut(paint, density, original, textWidth, lineHeight)
        val laid = versions.map { GlyphLayout.layOut(paint, density, it.text, textWidth, lineHeight) }
        val tallest = max(base.height, laid.maxOfOrNull { it.height } ?: 0f)
        chosenSize = size
        morphs = versions.mapIndexed { i, v -> GlyphLayout.buildMorph(original, v.text, base, laid[i], textWidth) }
        if (tallest <= textRoom) break
    }
    val inkPaint = paintOf(chosenSize, INK.toArgb())
    val out = Array(BUCKETS) { b -> paintOf(chosenSize, INK.copy(alpha = (b + 1f) / BUCKETS).toArgb()) }
    // New letters arrive in the accent and settle into the ink.
    val incoming = Array(BUCKETS) { b ->
        val alpha = (b + 1f) / BUCKETS
        val toInk = (b.toFloat() / (BUCKETS - 1)).let { it * it }
        val c = Color(
            mix(ACCENT.red, INK.red, toInk),
            mix(ACCENT.green, INK.green, toInk),
            mix(ACCENT.blue, INK.blue, toInk),
            alpha,
        )
        paintOf(chosenSize, c.toArgb())
    }
    return GlyphPage(morphs, inkPaint, out, incoming)
}

/** Draws one morph at [amount] into a native canvas, origin at the card's text corner, positions in dp × [d]. */
private fun drawMorph(canvas: android.graphics.Canvas, m: GlyphMorph, amount: Float, page: GlyphPage, d: Float, ox: Float, oy: Float) {
    val p = min(max(amount, 0f), 1f)
    for (i in m.chars.indices) {
        val start = m.delay[i] * SPREAD
        val k = easeInOutCubic(seg(p, start, start + 1 - SPREAD))
        val lift = 4 * k * (1 - k) * m.arc[i]
        val x = mix(m.ax[i], m.bx[i], k)
        val y = mix(m.ay[i], m.by[i], k) + lift
        canvas.drawText(m.chars, i, 1, (ox + x) * d, (oy + y) * d, page.ink)
    }
    if (p < 1) {
        for (i in m.outChars.indices) {
            val start = m.outDelay[i] * OUT_STAGGER
            val k = seg(p, start, start + OUT_END)
            if (k >= 1) continue
            val b = min(floor((1 - k) * BUCKETS).toInt(), BUCKETS - 1)
            canvas.drawText(m.outChars, i, 1, (ox + m.outX[i]) * d, (oy + m.outY[i] + k * k * GlyphReflowEditMotion.DROP) * d, page.out[b])
        }
    }
    if (p > IN_START) {
        for (i in m.inChars.indices) {
            val start = IN_START + m.inDelay[i] * IN_STAGGER
            val k = seg(p, start, start + IN_LENGTH)
            if (k <= 0) continue
            val b = min(floor(k * BUCKETS).toInt(), BUCKETS - 1)
            canvas.drawText(m.inChars, i, 1, (ox + m.inX[i]) * d, (oy + m.inY[i] + (1 - easeOutCubic(k)) * GlyphReflowEditMotion.RISE) * d, page.incoming[b])
        }
    }
}

/** Every value the frame loop, the gestures and the draw lambdas share. Drawn values are state; read them only in draw or layer lambdas. */
private class GlyphController {
    var clock by mutableFloatStateOf(0f)
    var mode by mutableIntStateOf(MODE_AUTO)
    var progress by mutableFloatStateOf(0f)
    var velocity by mutableFloatStateOf(0f)
    var tone by mutableIntStateOf(0)
    var indicator by mutableFloatStateOf(0f)
    var held by mutableIntStateOf(0)
    var ghostShown by mutableFloatStateOf(0f)

    // Read only by the frame loop and the gestures.
    var target = 0f
    var pending = -1
    var returning = false
    var touches = 0
    var seen = 0
    var idle = 0f
    var side = 0

    // Refreshed on every composition.
    var playing = false
    var reduced = false

    fun reset() {
        // Reduced motion: the first rewrite, in place, with the loop stopped.
        clock = 0f
        mode = MODE_AUTO
        returning = false
        pending = -1
        tone = 0
        indicator = 0f
        velocity = 0f
        target = if (reduced) 1f else 0f
        progress = if (reduced) 1f else 0f
    }

    fun step(dt: Float) {
        if (reduced) {
            // No glide: a chip swaps the text at once, and the bar still scrubs under the finger.
            val next = pending
            if (next >= 0) {
                tone = next
                indicator = next.toFloat()
                progress = 1f
                target = 1f
                pending = -1
            } else if (held == 0 && progress != target) {
                progress = target
            }
            return
        }

        if (touches != seen) {
            seen = touches
            idle = 0f
            returning = false
            if (mode == MODE_AUTO) {
                mode = MODE_MANUAL
                ghostShown = 0f
                velocity = 0f
            }
        }

        if (mode == MODE_AUTO) {
            if (!playing) return
            var t = clock + dt
            if (t >= LOOP) t -= LOOP
            clock = t
            progress = progressAt(t)
            tone = toneAt(t)
            indicator = toneIndicatorAt(t)
            ghostShown = ghostOpacityAt(t)
            return
        }

        var p = progress
        var v = velocity
        if (held == 0) {
            idle += dt
            val next = pending
            if (next >= 0 && abs(p) < 0.015f && abs(v) < 0.6f) {
                tone = next
                pending = -1
                target = 1f
            }
            v += (SPRING_STIFFNESS * (target - p) - SPRING_DAMPING * v) * dt
            p += v * dt
            if (abs(target - p) < 0.0008f && abs(v) < 0.01f) {
                p = target
                v = 0f
            }
            velocity = v
            progress = p

            if (playing && !returning && idle >= RESUME_AFTER) {
                returning = true
                pending = -1
                target = 0f
            }
        }

        val wanted = if (returning && abs(p) < 0.015f) 0f else tone.toFloat()
        val shown = indicator
        val moved = shown + (wanted - shown) * (1 - exp(-dt * 14))
        val settled = if (abs(wanted - moved) < 0.002f) wanted else moved
        indicator = settled

        if (returning && p == 0f) {
            tone = 0
            if (settled == 0f) {
                returning = false
                mode = MODE_AUTO
                clock = 0f
            }
        }
    }

    fun onChip(index: Int) {
        touches += 1
        if (tone == index && pending < 0) {
            target = if (progress > 0.5f) 0f else 1f
        } else {
            pending = index
            target = 0f
        }
    }
}

@Composable
fun GlyphReflowEdit(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    original: String = GLYPH_REFLOW_ORIGINAL,
    versions: List<GlyphReflowVersion> = GLYPH_REFLOW_VERSIONS,
    title: String = GLYPH_REFLOW_DOCUMENT,
) {
    val view = LocalView.current
    val reduced = rememberLiveReducedMotion()
    val playing = autoPlay && !reduced
    val c = remember { GlyphController() }
    SideEffect {
        c.playing = playing
        c.reduced = reduced
    }
    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
    }

    BoxWithConstraints(modifier.fillMaxSize().background(DESK)) {
        val density = LocalDensity.current.density
        val insetTop = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding().value
        val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value
        val width = maxWidth.value
        val height = maxHeight.value

        val column = min(width, MAX_WIDTH)
        val left = (width - column) / 2 + SIDE
        val inner = column - SIDE * 2
        val textWidth = inner - CARD_PAD * 2
        val top = insetTop + 4
        val bottom = max(insetBottom, 12f)
        val cardHeight = max(height - top - bottom - HEADER - GAP - CHIPS - GAP - BAR - GAP - STATS, 180f)
        val textRoom = cardHeight - CARD_PAD * 2
        val cardTop = top + HEADER
        val chipsTop = cardTop + cardHeight + GAP
        val barTop = chipsTop + CHIPS + GAP
        val statsTop = barTop + BAR + GAP
        val count = max(versions.size, 1)
        val chipWidth = inner / count
        val trackLeft = left + THUMB / 2
        val trackWidth = inner - THUMB
        val trackY = barTop + BAR_LABELS + (BAR - BAR_LABELS) / 2

        // The largest type at which every version fits the card, measured once per size.
        val page = remember(original, versions, textWidth, textRoom, density) {
            buildPage(original, versions, textWidth, textRoom, density)
        }
        val stats = remember(original, versions) {
            val base = GlyphLayout.statsOf(original, original)
            val rows = versions.map { GlyphLayout.statsOf(original, it.text) }
            Triple(
                listOf(base.words) + rows.map { it.words },
                listOf(base.read) + rows.map { it.read },
                listOf(base.change) + rows.map { it.change },
            )
        }

        LaunchedEffect(reduced) {
            c.reduced = reduced
            c.reset()
        }
        LaunchedEffect(c) {
            var last = -1L
            while (true) {
                withFrameNanos { now ->
                    val dt = if (last < 0) 0.016f else min((now - last) / 1e6f, 48f) / 1000f
                    last = now
                    if (dt > 0f) c.step(dt)
                }
            }
        }

        val hair = (1f / density).dp
        val font = TextStyle(color = INK)

        // Header.
        Row(
            Modifier
                .offset(left.dp, top.dp)
                .width(inner.dp)
                .height(HEADER.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                Modifier
                    .size(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(ACCENT)
                    .topHighlight(Color.White.copy(alpha = 0.4f), 11.dp, hair),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Q", style = TextStyle(color = Color(0xFFF5F8F2), fontSize = 19.sp, fontWeight = FontWeight.Bold))
            }
            Column(Modifier.weight(1f)) {
                BasicText(title, style = font.copy(fontSize = 17.sp, fontWeight = FontWeight.SemiBold), maxLines = 1, overflow = TextOverflow.Ellipsis)
                BasicText(
                    "Quill suggestion",
                    modifier = Modifier.padding(top = 1.dp),
                    style = TextStyle(color = INK_SOFT, fontSize = 13.sp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText("Saved", style = TextStyle(color = INK_SOFT, fontSize = 13.sp, fontWeight = FontWeight.Medium))
        }

        // The page.
        val cardShape = RoundedCornerShape(22.dp)
        Box(
            Modifier
                .offset(left.dp, cardTop.dp)
                .width(inner.dp)
                .height(cardHeight.dp)
                .cssBoxShadow(ink(0.1f), 12.dp, 32.dp, 22.dp)
                .cssBoxShadow(ink(0.14f), 1.dp, 3.dp, 22.dp)
                .clip(cardShape)
                .background(PAPER)
                .topHighlight(Color.White.copy(alpha = 0.9f), 22.dp, 1.dp)
                .border(hair, HAIRLINE, cardShape)
                .semantics { contentDescription = original },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val morph = page.morphs.getOrNull(min(c.tone, page.morphs.size - 1)) ?: return@Canvas
                val amount = c.progress
                drawIntoCanvas { drawMorph(it.nativeCanvas, morph, amount, page, density, CARD_PAD, CARD_PAD) }
            }
        }

        // Tone chips.
        Box(
            Modifier
                .offset(left.dp, chipsTop.dp)
                .width(inner.dp)
                .height(CHIPS.dp)
                .clip(RoundedCornerShape((CHIPS / 2).dp))
                .background(ink(0.07f)),
        ) {
            Box(
                Modifier
                    .padding(vertical = 3.dp)
                    .width(chipWidth.dp)
                    .fillMaxHeight()
                    .graphicsLayer { translationX = c.indicator * chipWidth * density }
                    .cssBoxShadow(ink(0.3f), 1.dp, 2.dp, ((CHIPS - 6) / 2).dp)
                    .background(INK, RoundedCornerShape(((CHIPS - 6) / 2).dp)),
            )
            Row(Modifier.fillMaxSize()) {
                versions.forEachIndexed { index, version ->
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null,
                                role = Role.Button,
                                onClickLabel = "Rewrite ${version.label.lowercase()}",
                            ) {
                                tick()
                                c.onChip(index)
                            }
                            .semantics { contentDescription = "Rewrite ${version.label.lowercase()}" },
                        contentAlignment = Alignment.Center,
                    ) {
                        val chipText = TextStyle(fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        BasicText(version.label, style = chipText.copy(color = INK))
                        BasicText(
                            version.label,
                            modifier = Modifier.graphicsLayer { alpha = 1 - min(abs(c.indicator - index), 1f) },
                            style = chipText.copy(color = Color(0xFFF5F7F1)),
                        )
                    }
                }
            }
        }

        // The bar: labels, then the track the finger scrubs.
        Row(
            Modifier
                .offset(left.dp, barTop.dp)
                .width(inner.dp)
                .height(BAR_LABELS.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val label = TextStyle(color = INK_SOFT, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            BasicText(
                "Original",
                modifier = Modifier.graphicsLayer { alpha = mix(1f, 0.45f, min(max(c.progress, 0f), 1f)) },
                style = label,
            )
            Box(Modifier.widthIn(min = 72.dp).fillMaxHeight(), contentAlignment = Alignment.CenterEnd) {
                versions.forEachIndexed { index, version ->
                    BasicText(
                        version.label,
                        modifier = Modifier.graphicsLayer { alpha = 1 - min(abs(c.indicator - index), 1f) },
                        style = label.copy(color = ACCENT, fontWeight = FontWeight.SemiBold),
                    )
                }
            }
        }
        val trackTop = barTop + BAR_LABELS
        val trackH = BAR - BAR_LABELS
        Box(
            Modifier
                .offset((left + THUMB / 2).dp, (trackTop + trackH / 2 - 2).dp)
                .width(trackWidth.dp)
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp))
                .background(ink(0.13f)),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        transformOrigin = TransformOrigin(0f, 0.5f)
                        scaleX = min(max(c.progress, 0f), 1f)
                    }
                    .background(ACCENT),
            )
        }
        Box(
            Modifier
                .offset(left.dp, (trackTop + (trackH - THUMB) / 2).dp)
                .size(THUMB.dp)
                .graphicsLayer {
                    val stretch = min(abs(c.velocity) * 0.03f, 0.16f)
                    val press = if (c.held == 1) 1.08f else 1f
                    translationX = c.progress * trackWidth * density
                    scaleX = (1 + stretch) * press
                    scaleY = (1 - stretch * 0.6f) * press
                }
                .cssBoxShadow(ink(0.18f), 4.dp, 12.dp, (THUMB / 2).dp)
                .cssBoxShadow(ink(0.25f), 1.dp, 2.dp, (THUMB / 2).dp)
                .background(PAPER, CircleShape)
                .border(hair, HAIRLINE, CircleShape),
        )
        // Touch area: the track's row, 10 dp taller above and below (RN hitSlop).
        Box(
            Modifier
                .offset(left.dp, (trackTop - 10).dp)
                .width(inner.dp)
                .height((trackH + 20).dp)
                .semantics { contentDescription = "Scrub between the original and the rewrite" }
                .pointerInput(trackWidth, density) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        c.touches += 1
                        c.held = 1
                        c.pending = -1
                        c.velocity = 0f
                        val first = rubber((down.position.x / density - THUMB / 2) / trackWidth)
                        c.side = if (first > 0.5f) 1 else 0
                        c.progress = first
                        c.target = first
                        down.consume()
                        while (true) {
                            val event = awaitPointerEvent()
                            val change = event.changes.firstOrNull { it.id == down.id } ?: break
                            if (!change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            val value = rubber((change.position.x / density - THUMB / 2) / trackWidth)
                            c.progress = value
                            c.target = value
                            c.velocity = tracker.calculateVelocity().x / density / trackWidth
                            val now = if (value > 0.5f) 1 else 0
                            if (now != c.side) {
                                c.side = now
                                tick()
                            }
                            change.consume()
                        }
                        c.held = 0
                        c.idle = 0f
                        if (c.reduced) {
                            val settled = min(max(c.progress, 0f), 1f)
                            c.target = settled
                            c.progress = settled
                        } else {
                            c.target = if (c.progress + c.velocity * 0.16f > 0.5f) 1f else 0f
                        }
                    }
                },
        )

        // Stats.
        Row(
            Modifier
                .offset(left.dp, statsTop.dp)
                .width(inner.dp)
                .height(STATS.dp)
                .background(ACCENT_SOFT, RoundedCornerShape(18.dp)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Stat("Words", stats.first, c)
            StatRule(hair)
            Stat("Read time", stats.second, c)
            StatRule(hair)
            Stat("Length", stats.third, c)
        }

        // The ghost finger of the loop.
        Box(
            Modifier
                .size(44.dp)
                .graphicsLayer {
                    val t = c.clock
                    alpha = if (c.mode == MODE_AUTO && playing) c.ghostShown else 0f
                    var x = left + chipWidth * 0.5f
                    var y = chipsTop + CHIPS / 2
                    if (t >= 4.45f) {
                        x = left + chipWidth * 1.5f
                    } else if (t >= MORPH_A1) {
                        x = trackLeft + (if (t < SCRUB_BACK0) 1f else min(max(c.progress, 0f), 1f)) * trackWidth
                        y = trackY
                    }
                    translationX = (x - 22) * density
                    translationY = (y - 22) * density
                    val s = 1 - ghostPressAt(t) * 0.18f
                    scaleX = s
                    scaleY = s
                }
                .background(ink(0.2f), CircleShape)
                .border(1.dp, Color.White.copy(alpha = 0.75f), CircleShape),
        )
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.Stat(label: String, values: List<String>, c: GlyphController) {
    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.fillMaxWidth().height(26.dp), contentAlignment = Alignment.Center) {
            values.forEachIndexed { index, value ->
                BasicText(
                    value,
                    modifier = Modifier.graphicsLayer {
                        val p = min(max(c.progress, 0f), 1f)
                        alpha = if (index == 0) 1 - p else if (c.tone == index - 1) p else 0f
                    },
                    style = TextStyle(
                        color = INK,
                        fontSize = 20.sp,
                        lineHeight = 26.sp,
                        fontWeight = FontWeight.Bold,
                        fontFeatureSettings = "tnum",
                    ),
                    maxLines = 1,
                )
            }
        }
        Spacer(Modifier.height(2.dp))
        BasicText(label, style = TextStyle(color = Color(0xFF4F5D55), fontSize = 12.sp))
    }
}

@Composable
private fun StatRule(hair: Dp) {
    Box(Modifier.width(hair).height(30.dp).background(ink(0.18f)))
}

/** CSS `inset 0 <thickness> 0 color`: a sharp line along the top inner edge of a rounded box. Apply after its clip. */
private fun Modifier.topHighlight(color: Color, radius: Dp, thickness: Dp) = drawBehind {
    val r = radius.toPx()
    val box = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(r))) }
    val shifted = Path().apply {
        addRoundRect(RoundRect(0f, thickness.toPx(), size.width, size.height + thickness.toPx(), CornerRadius(r)))
    }
    val band = Path().apply { op(box, shifted, PathOperation.Difference) }
    drawPath(band, color)
}
