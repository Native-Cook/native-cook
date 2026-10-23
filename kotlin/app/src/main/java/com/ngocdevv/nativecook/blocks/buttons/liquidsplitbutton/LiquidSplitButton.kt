package com.ngocdevv.nativecook.blocks.buttons.liquidsplitbutton

import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.PRESS_SCALE_MS
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberBloom
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import com.ngocdevv.nativecook.shared.surface
import android.graphics.ColorMatrixColorFilter
import android.graphics.Shader
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.annotation.RequiresApi
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay

enum class LiquidSplitOption { Circle, Triangle }

private const val HEIGHT = 52f
private const val PILL_W = 114f
private const val CIRCLE = 52f
private const val GAP = 12f
private const val DONE_W = 104f
private const val WIDTH = CIRCLE + GAP + CIRCLE + GAP + DONE_W

// Centres and widths of the three pieces once split: ○, △ and the "Done" pill.
private val PIECE_CX = floatArrayOf(CIRCLE / 2, CIRCLE + GAP + CIRCLE / 2, WIDTH - DONE_W / 2)
private val PIECE_W = floatArrayOf(CIRCLE, CIRCLE, DONE_W)

// The canvas overflows the button by this much on every side to fit the blur the goo is made from.
private val BLEED = 40.dp
private const val SPLIT_MS = 1150
private const val AUTO_OPEN_MS = 2000
private const val AUTO_CLOSED_MS = 1400

private const val GOO_BLUR = 9f
private const val RIM = 1.1f

// Mid-transition the glass whites out and swells by this much.
private const val INFLATE = 4f

private val Ink = Color(0xFF0B0B0D)
private val ShadowColor = Color(0xFF1E222C)
private val LabelStyle = TextStyle(color = Ink, fontSize = 17.sp, fontWeight = FontWeight.Medium)

private fun seg(s: Float, from: Float, to: Float) = ((s - from) / (to - from)).coerceIn(0f, 1f)
private fun smooth(u: Float) = u * u * (3f - 2f * u)

/** How far the pieces have moved from the single pill (0) to their own places (1). */
private fun spread(s: Float): Float {
    val u = seg(s, 0.12f, 1f)
    return 1f - (1f - u) * (1f - u) * (1f - u)
}

private fun flash(s: Float) = smooth(seg(s, 0.03f, 0.2f)) * (1f - smooth(seg(s, 0.45f, 0.65f)))
private fun pieceCx(index: Int, s: Float) = WIDTH / 2 + (PIECE_CX[index] - WIDTH / 2) * spread(s)

/** Rebuilds [path] as the three pieces. `inset` shrinks them (rim), `dy` drops them (shadow). */
private fun blob(path: Path, x0: Float, y0: Float, s: Float, inset: Float, dy: Float): Path {
    val m = spread(s)
    val grow = INFLATE * flash(s) - inset
    val h = HEIGHT + grow * 2
    path.reset()
    for (i in 0 until 3) {
        val w = PILL_W + (PIECE_W[i] - PILL_W) * m + grow * 2
        val x = x0 + pieceCx(i, s) - w / 2
        val y = y0 + dy - grow
        path.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(h / 2)))
    }
    return path
}

/** Still pieces for the cross-fade fallback: the pill alone, or the split trio. */
private fun still(path: Path, x0: Float, y0: Float, split: Boolean, inset: Float): Path {
    val h = HEIGHT - inset * 2
    path.reset()
    fun add(cx: Float, width: Float) {
        val w = width - inset * 2
        val x = x0 + cx - w / 2
        val y = y0 + inset
        path.addRoundRect(RoundRect(x, y, x + w, y + h, CornerRadius(h / 2)))
    }
    if (split) {
        for (i in 0 until 3) add(PIECE_CX[i], PIECE_W[i])
    } else {
        add(WIDTH / 2, PILL_W)
    }
    return path
}

private fun rimBrush(y: Float) = Brush.verticalGradient(
    0f to Color(0xFFF1F2F5),
    0.55f to Color(0xFFCFD1D6),
    1f to Color(0xFFF6F7FA),
    startY = y,
    endY = y + HEIGHT,
)

private fun bodyBrush(y: Float) = Brush.verticalGradient(
    0f to Color(0xFFCBCDD2),
    0.55f to Color(0xFFB9BCC2),
    1f to Color(0xFFD5D7DC),
    startY = y,
    endY = y + HEIGHT,
)

/**
 * Gooey look: blur everything, then turn the soft alpha back into a hard edge. Pieces that are
 * close enough share blur, so they stay bridged until they have really moved apart.
 */
@RequiresApi(Build.VERSION_CODES.S)
private fun gooEffect(radiusPx: Float): RenderEffect {
    val threshold = ColorMatrixColorFilter(
        floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 1f, 0f, 0f,
            // Android's offset column is in 0..255, unlike Skia's 0..1.
            0f, 0f, 0f, 20f, -9.5f * 255f,
        ),
    )
    val blur = android.graphics.RenderEffect.createBlurEffect(radiusPx, radiusPx, Shader.TileMode.DECAL)
    return android.graphics.RenderEffect.createColorFilterEffect(threshold, blur).asComposeRenderEffect()
}

/** Null below API 31, where the button falls back to a plain cross-fade. */
@Composable
private fun rememberGoo(): RenderEffect? {
    val density = LocalDensity.current
    return remember(density) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            gooEffect(sigmaToRadius(with(density) { GOO_BLUR.dp.toPx() }))
        } else {
            null
        }
    }
}

/**
 * Glass "Select" pill that whites out and pulls apart, metaball style, into ○, △ and a "Done"
 * pill. Tapping "Done" merges them back.
 *
 * @param expanded Controlled state. Leave null to let the button keep its own.
 * @param onOptionClick One of the two round options was tapped while split.
 * @param autoPlay Split and merge by itself with a pause in each state. Tapping still works.
 */
@Composable
fun LiquidSplitButton(
    label: String = "Select",
    modifier: Modifier = Modifier,
    doneLabel: String = "Done",
    expanded: Boolean? = null,
    onExpandedChange: (Boolean) -> Unit = {},
    onOptionClick: (LiquidSplitOption) -> Unit = {},
    autoPlay: Boolean = false,
) {
    val reducedMotion = rememberReducedMotion()
    var inner by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    val open = expanded ?: inner
    val controlled by rememberUpdatedState(expanded != null)
    val notify by rememberUpdatedState(onExpandedChange)

    fun setOpen(next: Boolean) {
        // The touch target that was just tapped is about to be swapped out, and may never report its release.
        pressed = false
        if (!controlled) inner = next
        notify(next)
    }

    // Re-armed after every change, so a tap simply restarts the pause.
    LaunchedEffect(autoPlay, reducedMotion, pressed, open) {
        if (!autoPlay || reducedMotion || pressed) return@LaunchedEffect
        delay((SPLIT_MS + if (open) AUTO_OPEN_MS else AUTO_CLOSED_MS).toLong())
        setOpen(!open)
    }

    val goo = rememberGoo()
    // Reduced motion, or no RenderEffect to make the goo with: cross-fade the two states instead.
    val crossFade = reducedMotion || goo == null

    // 0 = one "Select" pill, 1 = split into three.
    val split = animateFloatAsState(
        targetValue = if (open) 1f else 0f,
        animationSpec = tween(if (crossFade) 220 else SPLIT_MS, easing = LinearEasing),
        label = "split",
    )
    val scale by animateFloatAsState(
        targetValue = if (pressed) PRESS_SCALE else 1f,
        animationSpec = tween(PRESS_SCALE_MS, easing = EaseOutStrong),
        label = "press",
    )

    Box(
        modifier = modifier
            .size(WIDTH.dp, HEIGHT.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
    ) {
        Box(Modifier.matchParentSize().bleed(BLEED)) {
            if (crossFade) StillLayers(split) else GooLayers(split, goo)
            Canvas(Modifier.fillMaxSize().graphicsLayer { alpha = seg(split.value, 0.5f, 0.75f) }) {
                surface(BLEED) { x, y, _, h -> drawIcons(x, y + h / 2, split.value) }
            }
        }
        BasicText(
            text = label,
            style = LabelStyle,
            maxLines = 1,
            modifier = Modifier
                .align(Alignment.Center)
                .graphicsLayer { alpha = 1f - seg(split.value, 0.02f, 0.16f) }
                .clearAndSetSemantics {},
        )
        Box(Modifier.align(Alignment.CenterEnd).size(DONE_W.dp, HEIGHT.dp), contentAlignment = Alignment.Center) {
            BasicText(
                text = doneLabel,
                style = LabelStyle,
                maxLines = 1,
                modifier = Modifier
                    .graphicsLayer { alpha = seg(split.value, 0.5f, 0.75f) }
                    .clearAndSetSemantics {},
            )
        }

        if (open) {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                Hit("Circle", CIRCLE, { pressed = it }) { onOptionClick(LiquidSplitOption.Circle) }
                Hit("Triangle", CIRCLE, { pressed = it }) { onOptionClick(LiquidSplitOption.Triangle) }
                Hit(doneLabel, DONE_W, { pressed = it }) { setOpen(false) }
            }
        } else {
            Hit(label, PILL_W, { pressed = it }, Modifier.align(Alignment.Center)) { setOpen(true) }
        }
    }
}

/** Invisible touch target that only reports its pressed state, so the shared glass can react. */
@Composable
private fun Hit(
    name: String,
    width: Float,
    onPressedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val view = LocalView.current
    val interaction = remember { MutableInteractionSource() }
    val report by rememberUpdatedState(onPressedChange)

    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    report(true)
                    view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> report(false)
            }
        }
    }

    Box(
        modifier
            .size(width.dp, HEIGHT.dp)
            .semantics { contentDescription = name }
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
    )
}

@Composable
private fun GooLayers(split: State<Float>, goo: RenderEffect?) {
    val shadowBlur = rememberBloom(GOO_BLUR.dp)
    val glowBlur = rememberBloom(12.dp)
    val paths = remember { List(5) { Path() } }

    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            renderEffect = shadowBlur
            alpha = 0.3f
        },
    ) {
        surface(BLEED) { x, y, _, _ -> drawPath(blob(paths[0], x, y, split.value, 0f, 7f), ShadowColor) }
    }
    Canvas(
        Modifier.fillMaxSize().graphicsLayer {
            renderEffect = glowBlur
            alpha = flash(split.value) * 0.7f
        },
    ) {
        surface(BLEED) { x, y, _, _ -> drawPath(blob(paths[1], x, y, split.value, 0f, 0f), Color.White) }
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = goo }) {
        surface(BLEED) { x, y, _, _ -> drawPath(blob(paths[2], x, y, split.value, 0f, 0f), rimBrush(y)) }
    }
    Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = goo }) {
        surface(BLEED) { x, y, _, _ -> drawPath(blob(paths[3], x, y, split.value, RIM, 0f), bodyBrush(y)) }
    }
    // Faded by an outer layer so the fade happens after the threshold, not before it.
    Box(Modifier.fillMaxSize().graphicsLayer { alpha = flash(split.value) }) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { renderEffect = goo }) {
            surface(BLEED) { x, y, _, _ -> drawPath(blob(paths[4], x, y, split.value, 0f, 0f), Color.White) }
        }
    }
}

@Composable
private fun StillLayers(split: State<Float>) {
    val shadow = remember { SoftRoundRect(ShadowColor.copy(alpha = 0.3f), sigma = GOO_BLUR) }
    val paths = remember { List(2) { Path() } }

    for (isSplit in listOf(false, true)) {
        Canvas(
            Modifier.fillMaxSize().graphicsLayer { alpha = if (isSplit) split.value else 1f - split.value },
        ) {
            surface(BLEED) { x, y, _, _ ->
                drawIntoCanvas {
                    if (isSplit) {
                        for (i in 0 until 3) {
                            shadow.draw(it.nativeCanvas, x + PIECE_CX[i] - PIECE_W[i] / 2, y + 7f, PIECE_W[i], HEIGHT, HEIGHT / 2)
                        }
                    } else {
                        shadow.draw(it.nativeCanvas, x + (WIDTH - PILL_W) / 2, y + 7f, PILL_W, HEIGHT, HEIGHT / 2)
                    }
                }
                drawPath(still(paths[0], x, y, isSplit, 0f), rimBrush(y))
                drawPath(still(paths[1], x, y, isSplit, RIM), bodyBrush(y))
            }
        }
    }
}

private fun DrawScope.drawIcons(x: Float, cy: Float, s: Float) {
    drawCircle(Ink, radius = 8.5f, center = Offset(x + pieceCx(0, s), cy), style = Stroke(2f))
    val tx = x + pieceCx(1, s)
    val triangle = Path().apply {
        moveTo(tx, cy - 8.6f)
        lineTo(tx + 9.2f, cy + 6.8f)
        lineTo(tx - 9.2f, cy + 6.8f)
        close()
    }
    drawPath(triangle, Ink, style = Stroke(2f, join = StrokeJoin.Round))
}
