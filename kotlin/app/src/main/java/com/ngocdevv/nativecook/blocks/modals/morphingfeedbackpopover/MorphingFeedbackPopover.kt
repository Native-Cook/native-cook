package com.ngocdevv.nativecook.blocks.modals.morphingfeedbackpopover

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Keyboard
import androidx.compose.material.icons.outlined.MonitorHeart
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.Sync
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.max
import kotlin.math.min

// "Help" in an invented notes app, with a Feedback pill that stretches into a popover.
// The stretch's state is MorphingFeedbackPopoverEngine; this file is the page, the popover's content and the touches.

/** What was typed and the face that was picked (0 worst … 3 best, −1 none). */
data class MorphingFeedback(val text: String, val mood: Int)

private const val COPY_BACK = "Settings"
private const val COPY_TITLE = "Help"
private const val COPY_LABEL = "Feedback"
private const val COPY_THANKS = "Thanks!"
private const val COPY_SEND = "Send"
private const val COPY_VERSION = "Tidemark 4.2 (318)"
private val COPY_MOODS = listOf("Bad", "Okay", "Good", "Great")

// Palette: a cool paper, one deep sea ink, one mint accent.
private val PAPER = Color(0xFFECF0EE)
private val CARD = Color(0xFFFBFCFB)
private val INK = Color(0xFF14302D)
private val INK_SOFT = Color(0xFF6C7F7B)
private val SEA = Color(0xFF2E7F74)
private val HAIRLINE = rgba(20, 48, 45, 0.10f)
private val POPOVER = Color(0xFF17332F)
private val ON_DARK = Color(0xFFF1F7F5)
private val ON_DARK_SOFT = rgba(241, 247, 245, 0.62f)
private val FIELD = rgba(241, 247, 245, 0.07f)
private val FIELD_EDGE = rgba(241, 247, 245, 0.09f)
private val MINT = Color(0xFF9DE6D1)
private val SCRIM = Color(0xFF0D1F1D)

// Layout, in dp.
private const val SIDE = 16f
private const val ROW = 52f
private const val PILL_WIDTH = 132f
private const val PILL_HEIGHT = 44f
private const val PILL_BOTTOM = 28f // above the safe area
private const val POPOVER_MAX = 361f
private const val POPOVER_HEIGHT = 232f
private const val PAD = 12f
private const val FIELD_HEIGHT = 150f
private const val FIELD_PAD = 14f
private const val ROW_TOP = 176f // faces and Send
private const val ROW_HEIGHT = 40f
private const val FACE_LEFT = 14f
private const val FACE_STEP = 44f
private const val FACE = 40f
private const val SEND_WIDTH = 76f
private const val GHOST = 46f
private const val GHOST_PRESS = 0.16f
private const val PAN_SLOP = 6f
private const val TAP_SLOP = 10f
/** Room around a content part for a face's pop and Send's shadow, which a translucent layer would clip. */
private const val BLEED = 20f

// The check's corners in its 18 dp box.
private const val CHECK = 18f
private const val CHECK_STROKE = 2.2f
private const val SHORT_X = 3.5f
private const val SHORT_Y = 9.5f
private const val SHORT_LENGTH = 5.66f
private const val SHORT_ANGLE = 45f
private const val LONG_X = 7.5f
private const val LONG_Y = 13.5f
private const val LONG_LENGTH = 11.01f
private const val LONG_ANGLE = -50.5f

// Mouths for the four moods, in a 28 unit box.
private val MOUTHS = listOf("M9.5 19.2 Q14 14.8 18.5 19.2", "M10 18 L18 18", "M9.5 16.6 Q14 20.6 18.5 16.6", "M9 15.6 Q14 23 19 15.6 Z")

private fun style(size: Int, weight: FontWeight, color: Color, line: Int = 0, spacing: Float = 0f, tabular: Boolean = false, align: TextAlign = TextAlign.Unspecified) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = if (line > 0) line.sp else TextUnit.Unspecified,
    letterSpacing = spacing.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
    textAlign = align,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val INPUT_STYLE = style(16, FontWeight.Normal, ON_DARK, 20)

/**
 * @param autoPlay plays the 8 s loop until a finger takes over; it resumes 2 s after the last touch.
 * @param onSend called with what was typed and the face that was picked.
 */
@Composable
fun MorphingFeedbackPopover(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    onSend: (MorphingFeedback) -> Unit = {},
) {
    BlockSystemBars(darkIcons = true)
    val reducedMotion = rememberLiveReducedMotion()
    val view = LocalView.current
    val focus = LocalFocusManager.current
    val engine = remember { MorphingPopoverEngine() }
    val send by rememberUpdatedState(onSend)

    BoxWithConstraints(modifier.fillMaxSize().background(PAPER).clipToBounds()) {
        val screen = LocalDensity.current
        val width = maxWidth.value
        val height = maxHeight.value
        val bars = WindowInsets.systemBars
        val top = bars.getTop(screen) / screen.density
        val bottom = bars.getBottom(screen) / screen.density
        val ime = WindowInsets.ime
        val pillBottom = height - max(bottom, 16f) - PILL_BOTTOM
        val popoverWidth = min(width - SIDE * 2, POPOVER_MAX)
        val sendLeft = popoverWidth - PAD - SEND_WIDTH
        val pillTop = pillBottom - PILL_HEIGHT

        engine.anchorWidth = PILL_WIDTH
        engine.anchorHeight = PILL_HEIGHT
        engine.sizeWidth = popoverWidth
        engine.sizeHeight = POPOVER_HEIGHT
        engine.centre = width / 2f
        engine.bottom = pillBottom
        engine.belowPill = height - pillBottom
        engine.fieldLeft = PAD
        engine.fieldTop = PAD
        engine.fieldHeight = FIELD_HEIGHT
        engine.sendLeft = sendLeft
        engine.rowTop = ROW_TOP
        engine.rowHeight = ROW_HEIGHT
        engine.faceLeft = FACE_LEFT
        engine.faceStep = FACE_STEP
        engine.faceSize = FACE
        engine.sendWidth = SEND_WIDTH
        engine.ghostPillY = pillBottom - PILL_HEIGHT / 2f
        engine.playing = autoPlay && !reducedMotion
        engine.reduced = reducedMotion

        LaunchedEffect(engine, ime, screen) {
            while (true) {
                withFrameNanos {
                    engine.keyboard = ime.getBottom(screen) / screen.density
                    engine.frame(it)
                }
            }
        }

        fun bump() {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        fun tick() {
            view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        }

        fun success() {
            val kind = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK
            view.performHapticFeedback(kind)
        }

        fun answer(code: Int, mood: Int) {
            when (code) {
                INSIDE_FACE -> tick()
                INSIDE_SENT -> {
                    success()
                    focus.clearFocus()
                    send(MorphingFeedback(engine.field.value.text, mood))
                }
            }
        }

        Box(
            Modifier
                .fillMaxSize()
                // Initial pass: the frame's touches and, while it is open, the ones outside it. The rest reach the page.
                .pointerInput(engine) {
                    val panSlop = PAN_SLOP * density
                    val tapSlop = TAP_SLOP * density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val scale = density
                        val x = down.position.x / scale
                        val y = down.position.y / scale
                        val inFrame = engine.frameContains(x, y)
                        // The text field handles its own touches.
                        if (engine.goal == 1f && inFrame && engine.fieldContains(x, y)) {
                            engine.touch()
                            return@awaitEachGesture
                        }
                        if (!inFrame) {
                            if (engine.goal != 1f) return@awaitEachGesture
                            // Outside the open popover: a tap closes it.
                            down.consume()
                            engine.touch()
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                if (!change.pressed) {
                                    if ((change.position - down.position).getDistance() <= tapSlop && engine.dismiss()) {
                                        bump()
                                        focus.clearFocus()
                                    }
                                    break
                                }
                            }
                            return@awaitEachGesture
                        }
                        // The pill or the popover: a tap, or a drag on the open popover.
                        down.consume()
                        engine.touch()
                        engine.fingers += 1
                        if (engine.goal == 0f) engine.pressTo = 1f else engine.inside(x, y, 0)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var panning = false
                        var from = down.position
                        try {
                            while (true) {
                                val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                change.consume()
                                tracker.addPosition(change.uptimeMillis, change.position)
                                if (!change.pressed) {
                                    if (panning) {
                                        if (engine.dragging && engine.dragEnd(tracker.calculateVelocity().y / scale)) {
                                            bump()
                                            focus.clearFocus()
                                        }
                                    } else if (engine.goal == 0f) {
                                        if (engine.open()) bump()
                                    } else {
                                        val mood = engine.mood
                                        val code = engine.inside(change.position.x / scale, change.position.y / scale, 1)
                                        answer(code, if (code == INSIDE_SENT) mood else engine.mood)
                                    }
                                    break
                                }
                                if (!panning && (change.position - down.position).getDistance() > panSlop) {
                                    panning = true
                                    from = change.position
                                    engine.pressTo = 0f
                                    engine.sendTo = 0f
                                    if (engine.goal == 1f) engine.dragBegin()
                                }
                                if (panning && engine.dragging) engine.dragMove((change.position.y - from.y) / scale)
                            }
                        } finally {
                            engine.fingers = max(0, engine.fingers - 1)
                            engine.pressTo = 0f
                            engine.sendTo = 0f
                            if (engine.dragging) engine.dragEnd(0f)
                        }
                    }
                },
        ) {
            Page(top)
            BasicText(
                COPY_VERSION,
                Modifier.fillMaxWidth().offset(y = (pillTop - 30f).dp),
                style = style(12, FontWeight.Normal, INK_SOFT, tabular = true, align = TextAlign.Center),
            )

            Canvas(Modifier.fillMaxSize()) {
                engine.tick.longValue
                if (engine.scrim > 0f) drawRect(SCRIM.copy(alpha = engine.scrim))
            }

            PopoverFrame(engine, popoverWidth, sendLeft)

            Ghost(engine)
        }
    }
}

/** The Help page behind the pill. */
@Composable
private fun Page(top: Float) {
    var diagnostics by remember { mutableStateOf(true) }
    Column(Modifier.fillMaxWidth().padding(start = SIDE.dp, end = SIDE.dp, top = (top + 8f).dp)) {
        Row(Modifier.height(24.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            Box(Modifier.size(11.dp, 17.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronLeft, null, Modifier.requiredSize(30.dp), tint = SEA)
            }
            BasicText(COPY_BACK, style = style(17, FontWeight.Normal, SEA))
        }
        Spacer(Modifier.height(8.dp))
        BasicText(COPY_TITLE, Modifier.semantics { heading() }, style = style(32, FontWeight.ExtraBold, INK, 38, -0.4f))
        Spacer(Modifier.height(16.dp))
        Group {
            PageRow(Icons.AutoMirrored.Outlined.MenuBook, "Getting started", chevron = true)
            PageRow(Icons.Outlined.Keyboard, "Keyboard shortcuts", chevron = true, divider = true)
            PageRow(Icons.Outlined.Download, "Import notes", value = "3 apps", chevron = true, divider = true)
        }
        Spacer(Modifier.height(20.dp))
        Group {
            PageRow(Icons.Outlined.Sync, "Sync", value = "Up to date")
            PageRow(Icons.Outlined.MonitorHeart, "Share diagnostics", divider = true) {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
                    Switch(
                        checked = diagnostics,
                        onCheckedChange = { diagnostics = it },
                        colors = SwitchDefaults.colors(checkedTrackColor = SEA, checkedThumbColor = Color.White, checkedBorderColor = SEA),
                    )
                }
            }
        }
        Spacer(Modifier.height(20.dp))
        Group {
            PageRow(Icons.Outlined.Notifications, "What’s new", value = "4.2", chevron = true)
        }
    }
}

@Composable
private fun Group(content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .cssBoxShadow(rgba(20, 48, 45, 0.05f), 6.dp, 18.dp, 18.dp)
            .cssBoxShadow(rgba(20, 48, 45, 0.06f), 1.dp, 2.dp, 18.dp)
            .background(CARD, RoundedCornerShape(18.dp)),
    ) {
        content()
    }
}

@Composable
private fun PageRow(
    icon: ImageVector,
    label: String,
    value: String? = null,
    chevron: Boolean = false,
    divider: Boolean = false,
    trailing: @Composable RowScope.() -> Unit = {},
) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(ROW.dp)
            .drawBehind {
                if (divider) drawRect(HAIRLINE, Offset(56.dp.toPx(), 0f), Size(size.width - 56.dp.toPx(), 1f))
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Box(Modifier.size(30.dp).background(rgba(46, 127, 116, 0.12f), RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Icon(icon, null, Modifier.size(18.dp), tint = SEA)
        }
        BasicText(label, Modifier.weight(1f), style = style(16, FontWeight.Normal, INK), maxLines = 1)
        if (value != null) BasicText(value, style = style(15, FontWeight.Normal, INK_SOFT, tabular = true), maxLines = 1)
        trailing()
        if (chevron) {
            Box(Modifier.size(8.dp, 12.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.ChevronRight, null, Modifier.requiredSize(22.dp), tint = rgba(20, 48, 45, 0.28f))
            }
        }
    }
}

/** The stretching frame. Its content stays where it will be, and the frame opens around it. */
@Composable
private fun PopoverFrame(engine: MorphingPopoverEngine, popoverWidth: Float, sendLeft: Float) {
    val near = remember { SoftRoundRect(rgba(10, 30, 28, 0.28f), 1f) }
    val far = remember { SoftRoundRect(rgba(10, 30, 28, 0.26f), 16f) }
    val outline = remember { Path() }

    Box(
        Modifier
            .fillMaxSize()
            .drawWithContent {
                engine.tick.longValue
                val d = density
                val x = engine.frameX
                val y = engine.frameY
                val w = engine.frameWidth
                val h = engine.frameHeight
                val r = engine.radius
                val s = engine.frameScale
                withTransform({ scale(s, s, Offset((x + w / 2f) * d, (y + h / 2f) * d)) }) {
                    scale(d, d, Offset.Zero) {
                        drawIntoCanvas {
                            far.draw(it.nativeCanvas, x, y + 14f, w, h, r)
                            near.draw(it.nativeCanvas, x, y + 1f, w, h, r)
                        }
                    }
                    outline.rewind()
                    outline.addRoundRect(RoundRect(x * d, y * d, (x + w) * d, (y + h) * d, CornerRadius(r * d)))
                    clipPath(outline) {
                        drawRect(POPOVER, Offset(x * d, y * d), Size(w * d, h * d))
                        this@drawWithContent.drawContent()
                    }
                }
            },
    ) {
        Box(
            Modifier
                .size(popoverWidth.dp, POPOVER_HEIGHT.dp)
                .graphicsLayer {
                    engine.tick.longValue
                    translationX = engine.contentX * density
                    translationY = engine.contentY * density
                },
        ) {
            Rise(engine, 0, PAD, PAD, popoverWidth - PAD * 2, FIELD_HEIGHT) {
                FeedbackField(engine)
            }
            Rise(engine, 1, FACE_LEFT, ROW_TOP, FACE_STEP * 3 + FACE, ROW_HEIGHT) {
                Row(horizontalArrangement = Arrangement.spacedBy((FACE_STEP - FACE).dp)) {
                    COPY_MOODS.forEachIndexed { i, name -> MoodFace(engine, i, name) }
                }
            }
            Rise(engine, 2, sendLeft, ROW_TOP, SEND_WIDTH, ROW_HEIGHT) {
                SendButton(engine)
            }
            TravellingLabel(engine)
            ThanksRow(engine, popoverWidth)
        }
    }
}

/** One part of the popover's content: it fades up once the frame is nearly open, one after another. */
@Composable
private fun Rise(
    engine: MorphingPopoverEngine,
    order: Int,
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier
            .offset((x - BLEED).dp, (y - BLEED).dp)
            .size((width + BLEED * 2).dp, (height + BLEED * 2).dp)
            .graphicsLayer {
                engine.tick.longValue
                val u = engine.rise(order)
                alpha = u
                translationY = (if (engine.reduced) 0f else (1f - u) * CONTENT_RISE) * density
            }
            .padding(BLEED.dp),
        content = content,
    )
}

/** The real field, with autoPlay's text in a copy on top of it, so no keyboard comes up in a recording. */
@Composable
private fun BoxScope.FeedbackField(engine: MorphingPopoverEngine) {
    val open by engine.isOpen
    var value by engine.field
    val shape = RoundedCornerShape(16.dp)
    Box(Modifier.matchParentSize().background(FIELD, shape).border(1.dp, FIELD_EDGE, shape)) {
        BasicTextField(
            value = value,
            onValueChange = { value = it },
            modifier = Modifier
                .fillMaxSize()
                .onFocusChanged {
                    engine.focused = it.isFocused
                    if (it.isFocused) engine.touch()
                }
                .semantics { contentDescription = COPY_LABEL },
            enabled = open,
            textStyle = INPUT_STYLE,
            cursorBrush = SolidColor(MINT),
            decorationBox = { inner -> Box(Modifier.fillMaxSize().padding(FIELD_PAD.dp)) { inner() } },
        )
        TypedCopy(engine)
    }
}

/** Isolated so that a new character recomposes this text and nothing else. */
@Composable
private fun TypedCopy(engine: MorphingPopoverEngine) {
    val count by engine.typed
    if (count > 0) BasicText(SAMPLE.take(count), Modifier.padding(FIELD_PAD.dp), style = INPUT_STYLE)
}

/** A mood face: outlined, or filled once it is picked, with a pop. */
@Composable
private fun MoodFace(engine: MorphingPopoverEngine, index: Int, name: String) {
    val mouth = remember(index) { PathParser().parsePathString(MOUTHS[index]).toPath() }
    Canvas(
        Modifier
            .size(FACE.dp)
            .graphicsLayer {
                engine.tick.longValue
                val pop = engine.facePop(index)
                scaleX = pop
                scaleY = pop
            }
            .semantics {
                role = Role.Button
                contentDescription = name
            },
    ) {
        engine.tick.longValue
        val art = 30.dp.toPx()
        val k = art / 28f
        translate((size.width - art) / 2f, (size.height - art) / 2f) {
            scale(k, k, Offset.Zero) {
                drawFace(index, mouth, false, 1f)
                val on = engine.faceAmount(index)
                if (on > 0f) drawFace(index, mouth, true, on)
            }
        }
    }
}

private fun DrawScope.drawFace(index: Int, mouth: Path, on: Boolean, alpha: Float) {
    val line = if (on) INK else ON_DARK_SOFT
    val center = Offset(14f, 14f)
    if (on) drawCircle(MINT, 12f, center, alpha)
    drawCircle(if (on) MINT else ON_DARK_SOFT, 12f, center, alpha, Stroke(1.6f))
    drawCircle(line, 1.35f, Offset(10f, 11.4f), alpha)
    drawCircle(line, 1.35f, Offset(18f, 11.4f), alpha)
    if (index == 3) drawPath(mouth, line, alpha, Fill)
    drawPath(mouth, line, alpha, Stroke(1.6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

@Composable
private fun BoxScope.SendButton(engine: MorphingPopoverEngine) {
    Box(
        Modifier
            .matchParentSize()
            .graphicsLayer {
                engine.tick.longValue
                alpha = engine.sendAlpha()
                val s = 1f - SEND_PRESS * engine.sendPress
                scaleX = s
                scaleY = s
            }
            .cssBoxShadow(rgba(6, 22, 20, 0.30f), 4.dp, 12.dp, (ROW_HEIGHT / 2f).dp)
            .background(MINT, CircleShape)
            // The inset top highlight.
            .drawBehind {
                val stroke = 1.dp.toPx()
                clipRect(0f, 0f, size.width, size.height / 2f) {
                    drawRoundRect(
                        Color.White.copy(alpha = 0.45f),
                        Offset(stroke / 2f, stroke / 2f),
                        Size(size.width - stroke, size.height - stroke),
                        CornerRadius(size.height / 2f),
                        style = Stroke(stroke),
                    )
                }
            }
            .semantics {
                role = Role.Button
                contentDescription = COPY_SEND
            },
        contentAlignment = Alignment.Center,
    ) {
        BasicText(COPY_SEND, style = style(16, FontWeight.SemiBold, INK), maxLines = 1)
    }
}

/** "Feedback": the pill's label, which slides up to become the field's placeholder. */
@Composable
private fun TravellingLabel(engine: MorphingPopoverEngine) {
    val density = LocalDensity.current.density
    BasicText(
        COPY_LABEL,
        Modifier
            .graphicsLayer {
                engine.tick.longValue
                translationX = engine.labelX * this.density
                translationY = engine.labelY * this.density
                alpha = engine.labelAlpha
            }
            .semantics {
                role = Role.Button
                contentDescription = COPY_LABEL
                onClick {
                    engine.touch()
                    if (engine.goal == 0f) engine.open() else engine.dismiss()
                }
            },
        style = style(16, FontWeight.SemiBold, ON_DARK, 20),
        maxLines = 1,
        onTextLayout = { engine.labelWidth = it.size.width / density },
    )
}

/** The check that draws itself, and the word. */
@Composable
private fun ThanksRow(engine: MorphingPopoverEngine, popoverWidth: Float) {
    Row(
        Modifier
            .offset((popoverWidth / 2f - PILL_WIDTH / 2f).dp, (POPOVER_HEIGHT - PILL_HEIGHT).dp)
            .size(PILL_WIDTH.dp, PILL_HEIGHT.dp)
            .graphicsLayer {
                engine.tick.longValue
                alpha = engine.thanksAlpha()
            },
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
    ) {
        Canvas(Modifier.size(CHECK.dp)) {
            engine.tick.longValue
            scale(density, density, Offset.Zero) {
                drawStroke(SHORT_X, SHORT_Y, SHORT_LENGTH, SHORT_ANGLE, engine.checkShort())
                drawStroke(LONG_X, LONG_Y, LONG_LENGTH, LONG_ANGLE, engine.checkLong())
            }
        }
        BasicText(COPY_THANKS, style = style(16, FontWeight.SemiBold, ON_DARK, 20), maxLines = 1)
    }
}

/** One stroke of the check: a capsule turned about its left end and stretched from there. */
private fun DrawScope.drawStroke(x: Float, y: Float, length: Float, angle: Float, amount: Float) {
    if (amount <= 0f) return
    withTransform({
        translate(x, y)
        rotate(angle, Offset.Zero)
        scale(max(amount, 0.001f), 1f, Offset.Zero)
    }) {
        drawRoundRect(MINT, Offset(0f, -CHECK_STROKE / 2f), Size(length, CHECK_STROKE), CornerRadius(CHECK_STROKE / 2f))
    }
}

/** The finger autoPlay pretends to have: a soft dot that taps the pill, a face and Send. */
@Composable
private fun Ghost(engine: MorphingPopoverEngine) {
    val shadow = remember { SoftRoundRect(rgba(10, 30, 28, 0.25f), 5f) }
    Canvas(Modifier.fillMaxSize()) {
        engine.tick.longValue
        val alpha = if (engine.playing && engine.mode == MODE_AUTO) engine.ghostAlpha else 0f
        if (alpha <= 0f) return@Canvas
        val s = 1f - GHOST_PRESS * engine.ghostPress
        val r = GHOST / 2f
        scale(density, density, Offset.Zero) {
            withTransform({
                translate(engine.ghostX, engine.ghostY)
                scale(s, s, Offset.Zero)
            }) {
                drawIntoCanvas { shadow.draw(it.nativeCanvas, -r, -r + 2f, GHOST, GHOST, r, alpha) }
                drawCircle(Color.White.copy(alpha = 0.26f * alpha), r, Offset.Zero)
                drawCircle(Color.White.copy(alpha = 0.8f * alpha), r - 0.75f, Offset.Zero, style = Stroke(1.5f))
            }
        }
    }
}
