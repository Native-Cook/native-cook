package com.ngocdevv.nativecook.blocks.media.comparerevealslider

import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private val PAGE = Color(0xFFF2EEE6)
private val INK = Color(0xFF211D18)
private val MUTE = Color(0xFF7D7468)
private val CARD = Color(0xFFFBF9F4)
private val HAIR = INK.copy(alpha = 0.1f)
private val WARM = Color(0xFF3C2814)
private val PHOTO_BG = Color(0xFFD9D2C4)

private val SIDE = 16.dp
private val RADIUS = 26.dp
private const val DOT = 46f
private const val MAX_ASPECT = 1.5f

private val FACTS = listOf("6 weeks" to "Start to finish", "\$8,420" to "Total spend", "14 m²" to "Floor area")
private val OPTIONS = listOf("Day", "Evening")

private fun ts(
    size: Float,
    color: Color,
    weight: FontWeight = FontWeight.Normal,
    line: Float? = null,
    spacing: Float = 0f,
    tabular: Boolean = false,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    letterSpacing = spacing.sp,
    lineHeight = if (line != null) line.sp else TextUnit.Unspecified,
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

/**
 * A before / after photo under a divider with mass. Drag the glass handle: it squashes under the
 * finger, stretches with speed, rubber-bands at the photo's edges and, let go, is carried on by a
 * spring that overshoots and settles. Day / Evening crossfades both photos under the divider.
 */
@Composable
fun CompareRevealSlider(autoPlay: Boolean = true) {
    val context = LocalContext.current
    val view = LocalView.current
    val reduced = remember {
        Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f
    }
    val film = autoPlay && !reduced
    val controller = remember {
        CompareController(
            film,
            reduced,
            onPress = { view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP) },
            onStop = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) },
        )
    }
    SideEffect { controller.film = film }
    // the page is light: dark status bar icons, as the RN block's <StatusBar style="dark" />
    BlockSystemBars(darkIcons = true)
    LaunchedEffect(controller) {
        while (true) withFrameNanos { controller.frame(it / 1_000_000_000.0) }
    }

    val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val navBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    BoxWithConstraints(Modifier.fillMaxSize().background(PAGE)) {
        val compact = maxHeight < 740.dp
        val contentW = min(maxWidth.value, 480f).dp
        val w = contentW - SIDE * 2
        val top = statusTop + if (compact) 4.dp else 8.dp
        val headerH = if (compact) 50.dp else 58.dp
        val gap = if (compact) 10.dp else 16.dp
        val factsH = if (compact) 54.dp else 64.dp
        val bottom = max(navBottom.value, 12f).dp + if (compact) 6.dp else 10.dp
        val photoH = (maxHeight - top - headerH - gap * 3 - factsH - 32.dp - bottom).value
            .coerceIn(w.value * 0.9f, w.value * MAX_ASPECT)
            .roundToInt().toFloat().dp
        val titleSize = if (compact) 26f else 30f
        SideEffect { controller.widthDp = w.value }

        Column(
            Modifier
                .align(Alignment.TopCenter)
                .width(contentW)
                .padding(start = SIDE, end = SIDE, top = top),
        ) {
            Row(Modifier.height(headerH).fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f).padding(end = 12.dp)) {
                    Text(
                        "Roomwork · Project 214",
                        style = ts(14f, MUTE, FontWeight.Medium, 18f, tabular = true),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "Linden Street",
                        style = ts(titleSize, INK, FontWeight.Bold, titleSize + 5f, -0.4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Box(
                    Modifier
                        .padding(bottom = 2.dp)
                        .size(38.dp)
                        .dropShadow(CircleShape, Shadow(radius = 14.dp, color = WARM, offset = DpOffset(0.dp, 6.dp), alpha = 0.08f))
                        .dropShadow(CircleShape, Shadow(radius = 2.dp, color = WARM, offset = DpOffset(0.dp, 1.dp), alpha = 0.14f))
                        .background(CARD, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.IosShare, "Share", tint = INK, modifier = Modifier.size(19.dp))
                }
            }

            Spacer(gap)
            PhotoStage(controller, w, photoH)
            Spacer(gap)
            Facts(factsH, compact)
            Spacer(gap)
            LightControl(controller, w)
        }
    }
}

@Composable
private fun Spacer(height: Dp) {
    Box(Modifier.height(height))
}

@Composable
private fun PhotoStage(controller: CompareController, w: Dp, h: Dp) {
    val context = LocalContext.current
    val density = LocalDensity.current.density
    val shape = RoundedCornerShape(RADIUS)
    var rooms by remember { mutableStateOf<Rooms?>(null) }
    val lift = remember(density) { shadowSprite(12f, density) }
    val contact = remember(density) { shadowSprite(2.5f, density) }
    val glint = remember(density) { glintSprite(density) }
    val glassPath = remember { Path() }
    val layerPaint = remember { Paint() }
    val widthPx = (w.value * density).roundToInt()
    val heightPx = (h.value * density).roundToInt()

    LaunchedEffect(widthPx, heightPx) {
        rooms = withContext(Dispatchers.IO) { loadRooms(context, widthPx, heightPx, density) }
        controller.ready = true
    }

    Box(
        Modifier
            .width(w)
            .height(h)
            .dropShadow(shape, Shadow(radius = 36.dp, color = WARM, offset = DpOffset(0.dp, 18.dp), alpha = 0.2f))
            .dropShadow(shape, Shadow(radius = 4.dp, color = WARM, offset = DpOffset(0.dp, 2.dp), alpha = 0.2f))
            .background(PHOTO_BG, shape)
            .clip(shape)
            .semantics {
                contentDescription = "Before and after, living room"
                customActions = listOf(
                    CustomAccessibilityAction("Move divider right") { controller.nudge(1); true },
                    CustomAccessibilityAction("Move divider left") { controller.nudge(-1); true },
                )
            }
            .pointerInput(controller) {
                awaitEachGesture {
                    val first = awaitFirstDown(requireUnconsumed = false)
                    first.consume()
                    val tracker = VelocityTracker()
                    tracker.addPosition(first.uptimeMillis, first.position)
                    controller.down(first.position.x / this.density)
                    while (true) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == first.id } ?: break
                        tracker.addPosition(change.uptimeMillis, change.position)
                        if (!change.pressed) break
                        change.consume()
                        controller.move(change.position.x / this.density)
                    }
                    controller.up(tracker.calculateVelocity().x / this.density)
                }
            },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            val room = rooms ?: return@Canvas
            drawScene(controller, room, lift, contact, glint, glassPath, layerPaint)
        }
        CompareLabel("Before", Modifier.align(Alignment.TopStart).padding(start = 14.dp, top = 14.dp)) {
            val a = labelBefore(controller.pos.floatValue)
            alpha = a
            translationX = -6.dp.toPx() * (1f - a)
        }
        CompareLabel("After", Modifier.align(Alignment.TopEnd).padding(end = 14.dp, top = 14.dp)) {
            val a = labelAfter(controller.pos.floatValue)
            alpha = a
            translationX = 6.dp.toPx() * (1f - a)
        }
        Box(Modifier.fillMaxSize().border(1.dp, Color.White.copy(alpha = 0.22f), shape))
        Canvas(Modifier.fillMaxSize()) { drawGhost(controller) }
    }
}

@Composable
private fun CompareLabel(text: String, modifier: Modifier, layer: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier
            .graphicsLayer(layer)
            .height(28.dp)
            .background(Color(0xFF1C1610).copy(alpha = 0.46f), shape)
            .border(0.5.dp, Color.White.copy(alpha = 0.22f), shape)
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, style = ts(13f, Color(0xFFFFFAF1), FontWeight.SemiBold, spacing = 0.1f))
    }
}

private fun DrawScope.drawPhoto(photo: Photo, alpha: Float = 1f) {
    if (alpha <= 0f) return
    drawImage(
        photo.image,
        srcOffset = photo.srcOffset,
        srcSize = photo.srcSize,
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
    )
}

private fun DrawScope.drawGlass(photo: Photo, alpha: Float = 1f) {
    if (alpha <= 0f) return
    drawImage(
        photo.glass,
        srcOffset = IntOffset.Zero,
        srcSize = IntSize(photo.glass.width, photo.glass.height),
        dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
        alpha = alpha,
    )
}

private fun DrawScope.drawScene(
    c: CompareController,
    room: Rooms,
    lift: Sprite,
    contact: Sprite,
    glint: Sprite,
    glassPath: Path,
    layerPaint: Paint,
) {
    val d = density
    val w = size.width
    val h = size.height
    val pos = c.pos.floatValue
    val evening = c.evening.floatValue
    val stretch = c.stretch.floatValue
    val press = c.press.floatValue
    val lineX = pos * w

    drawPhoto(room.beforeDay)
    drawPhoto(room.beforeEvening, evening)
    clipRect(left = clamp(pos, 0f, 1f) * w, right = w) {
        drawPhoto(room.afterDay)
        drawPhoto(room.afterEvening, evening)
    }
    // a little shade behind the labels
    drawRect(
        Brush.verticalGradient(
            listOf(Color(0xFF18120C).copy(alpha = 0.34f), Color(0xFF18120C).copy(alpha = 0.12f), Color(0xFF18120C).copy(alpha = 0f)),
            0f,
            96f * d,
        ),
        size = Size(w, 96f * d),
    )

    // the divider: a soft glow, the line, and a light running down it
    val glowW = GLOW_W * d
    val warmWhite = Color(0xFFFFFAF0)
    drawRect(
        Brush.horizontalGradient(
            listOf(warmWhite.copy(alpha = 0f), warmWhite.copy(alpha = 0.2f), warmWhite.copy(alpha = 0f)),
            lineX - glowW / 2f,
            lineX + glowW / 2f,
        ),
        Offset(lineX - glowW / 2f, 0f),
        Size(glowW, h),
    )
    val lineW = LINE_W * d
    drawRect(Color(0xFF281E14).copy(alpha = 0.18f), Offset(lineX - lineW / 2f, 0f), Size(lineW + d, h))
    drawRect(Color(0xFFFFFCF6).copy(alpha = 0.88f), Offset(lineX - lineW / 2f, 0f), Size(lineW, h))

    val glintLen = GLINT_LEN * d
    val glintY = -glintLen / 2f + c.glint.floatValue * (h + glintLen)
    val glintAlpha = glintGain(stretch)
    drawImage(
        glint.image,
        topLeft = Offset(lineX - 4f * d - glint.pad, glintY - glintLen / 2f - glint.pad),
        alpha = glintAlpha,
    )
    val coreLen = glintLen * 0.6f
    drawRect(
        Brush.verticalGradient(
            listOf(Color.White.copy(alpha = 0f), Color.White, Color.White.copy(alpha = 0f)),
            glintY - coreLen / 2f,
            glintY + coreLen / 2f,
        ),
        Offset(lineX - lineW / 2f, glintY - coreLen / 2f),
        Size(lineW, coreLen),
        alpha = glintAlpha,
    )

    // the handle: two shadows, glass over the photo, a lit rim, the grabber
    val a = abs(stretch)
    val sx = 1f + PRESS_SQUASH_X * press + STRETCH_X * a
    val sy = 1f - PRESS_SQUASH_Y * press - STRETCH_Y * a
    val hx = lineX - LEAN * d * stretch
    val hy = (h / d * HANDLE_Y).roundToInt() * d
    val hw = HANDLE_W * d
    val hh = HANDLE_H * d

    withTransform({
        translate(hx, hy)
        scale(sx, sy, Offset.Zero)
    }) {
        drawImage(
            lift.image,
            topLeft = Offset(-hw / 2f - lift.pad, -hh / 2f - lift.pad + 9f * d),
            alpha = 0.3f - 0.14f * press,
        )
        drawImage(
            contact.image,
            topLeft = Offset(-hw / 2f - contact.pad, -hh / 2f - contact.pad + 1.5f * d),
            alpha = 0.22f + 0.16f * press,
        )
    }

    // The glass is drawn in screen space, like a backdrop: the blurred scene, inside the moving pill.
    val halfW = hw / 2f * sx
    val halfH = hh / 2f * sy
    val pill = Rect(hx - halfW, hy - halfH, hx + halfW, hy + halfH)
    glassPath.rewind()
    glassPath.addRoundRect(RoundRect(pill, CornerRadius(hw / 2f * sx, hw / 2f * sy)))
    clipPath(glassPath) {
        drawGlass(room.beforeDay)
        drawGlass(room.beforeEvening, evening)
        val soft = 18f * d
        drawIntoCanvas { canvas ->
            canvas.saveLayer(pill, layerPaint)
            drawGlass(room.afterDay)
            drawGlass(room.afterEvening, evening)
            drawRect(
                Brush.horizontalGradient(listOf(Color.Transparent, Color.Black), lineX - soft, lineX + soft),
                pill.topLeft,
                pill.size,
                blendMode = BlendMode.DstIn,
            )
            canvas.restore()
        }
        drawRect(
            Brush.horizontalGradient(
                listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.08f), Color.White.copy(alpha = 0f)),
                lineX - soft,
                lineX + soft,
            ),
            pill.topLeft,
            pill.size,
        )
        drawRect(Color(0xFFFFFCF6).copy(alpha = 0.3f), pill.topLeft, pill.size)
        drawRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.5f),
                0.55f to Color.White.copy(alpha = 0.06f),
                1f to Color.White.copy(alpha = 0.18f),
                startY = pill.top,
                endY = pill.bottom,
            ),
            pill.topLeft,
            pill.size,
        )
    }

    withTransform({
        translate(hx, hy)
        scale(sx, sy, Offset.Zero)
    }) {
        drawRoundRect(
            Brush.verticalGradient(
                0f to Color.White.copy(alpha = 0.95f),
                0.6f to Color.White.copy(alpha = 0.35f),
                1f to Color.White.copy(alpha = 0.6f),
                startY = -hh / 2f,
                endY = hh / 2f,
            ),
            Offset(-hw / 2f, -hh / 2f),
            Size(hw, hh),
            CornerRadius(hw / 2f),
            style = Stroke(1.2f * d),
        )
        val chevron = Color(0xFF211D18).copy(alpha = 0.82f)
        val stroke = 2.2f * d
        fun line(x1: Float, y1: Float, x2: Float, y2: Float) =
            drawLine(chevron, Offset(x1 * d, y1 * d), Offset(x2 * d, y2 * d), stroke, StrokeCap.Round)
        line(-7f, -6f, -12.5f, 0f)
        line(-12.5f, 0f, -7f, 6f)
        line(7f, -6f, 12.5f, 0f)
        line(12.5f, 0f, 7f, 6f)
    }
}

private fun DrawScope.drawGhost(c: CompareController) {
    val a = c.ghost.floatValue * 0.9f
    if (a <= 0f) return
    val d = density
    val r = DOT * d / 2f * (1.12f - 0.2f * c.press.floatValue)
    val center = Offset(c.pos.floatValue * size.width, (size.height / d * HANDLE_Y).roundToInt() * d)
    translate(center.x, center.y) {
        drawCircle(Color.White.copy(alpha = 0.3f), r, Offset.Zero, alpha = a)
        drawCircle(Color.White.copy(alpha = 0.75f), r - 0.75f * d, Offset.Zero, alpha = a, style = Stroke(1.5f * d))
    }
}

@Composable
private fun Facts(height: Dp, compact: Boolean) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .height(height)
            .dropShadow(shape, Shadow(radius = 20.dp, color = WARM, offset = DpOffset(0.dp, 8.dp), alpha = 0.06f))
            .dropShadow(shape, Shadow(radius = 2.dp, color = WARM, offset = DpOffset(0.dp, 1.dp), alpha = 0.1f))
            .background(CARD, shape),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val valueStyle = ts(if (compact) 16f else 18f, INK, FontWeight.SemiBold, spacing = -0.2f, tabular = true)
        val labelStyle = ts(12.5f, MUTE)
        FACTS.forEachIndexed { i, (value, label) ->
            Column(
                Modifier
                    .weight(1f)
                    .drawBehind { if (i > 0) drawRect(HAIR, Offset.Zero, Size(1f, size.height)) }
                    .padding(horizontal = 14.dp),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(value, style = valueStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(label, style = labelStyle, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LightControl(controller: CompareController, width: Dp) {
    val selected = controller.selected.intValue
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        SingleChoiceSegmentedButtonRow(Modifier.width(width).height(32.dp)) {
            OPTIONS.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = selected == i,
                    onClick = { controller.choose(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, OPTIONS.size),
                    modifier = Modifier.height(32.dp),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = Color(0xFFFFFDF8),
                        activeContentColor = INK,
                        activeBorderColor = INK.copy(alpha = 0.18f),
                        inactiveContainerColor = Color(0xFF767680).copy(alpha = 0.14f),
                        inactiveContentColor = INK,
                        inactiveBorderColor = INK.copy(alpha = 0.18f),
                    ),
                    icon = {},
                    label = {
                        Text(
                            label,
                            style = ts(13f, INK, if (selected == i) FontWeight.SemiBold else FontWeight.Medium),
                        )
                    },
                )
            }
        }
    }
}
