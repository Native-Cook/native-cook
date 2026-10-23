package com.ngocdevv.nativecook.blocks.inputs.attachmenttrayinput

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.ViewConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.EaseOutStrong
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.roundToInt
import kotlin.math.sqrt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

enum class AttachedFileKind { Pdf, Image, Folder }

/**
 * @param meta The line under the name in the preview: type, size, length.
 * @param title Heading of the summary in the preview.
 * @param summary Text of the summary in the preview.
 */
data class AttachedFile(
    val name: String,
    val kind: AttachedFileKind,
    val meta: String,
    val title: String,
    val summary: String,
)

private const val WIDTH = 350f

// The composer with its tray open; the tray grows upwards inside this box.
private const val HEIGHT = 174f
private const val RADIUS = 24f

// Inset of the tray and of the text area from the outer edge.
private const val PAD = 4f
private const val TRAY_H = 40f
private const val TRAY_INSET = 4f
private const val CHIP_H = 32f
private const val CHIP_GAP = 6f
private const val NAME_MAX = 118f
private const val CARD_R = 20f
private const val TEXT_H = 48f
private const val CONTROL = 36f
private const val TOOL = 30f
private const val PREVIEW_W = 300f

// The preview hangs from the bottom edge of the chips, over the text area.
private const val PREVIEW_TOP = 44f
private const val SUMMARY_H = 138f

// How far above its place a chip starts.
private const val DROP_HEIGHT = 22f
private const val HOLD = 300L
private const val RETRACT = 160
private const val FOLD_DELAY = 150

private val Ink = Color(0xFF111111)

/** The same spring as the other stacks, from its mass, stiffness and damping. */
private fun physicalSpring(mass: Float, stiffness: Float, damping: Float) =
    spring<Float>(dampingRatio = damping / (2f * sqrt(stiffness * mass)), stiffness = stiffness / mass)

private val SlotSpring = physicalSpring(1f, 300f, 26f)
private val DropSpring = physicalSpring(1f, 380f, 20f)
private val TraySpring = physicalSpring(1f, 320f, 24f)
private val PreviewSpring = physicalSpring(1f, 320f, 24f)
private val TileSpring = physicalSpring(1f, 420f, 30f)

// autoPlay loop, in seconds from its start.
private val ADD_AT = floatArrayOf(0.95f, 1.85f, 2.75f)

// A scripted tap holds its button down this long before it acts.
private const val PRESS_LEAD = 0.15f
private const val HOLD_AT = 3.9f
private const val RELEASE_AT = 6.5f
private val REMOVE_AT = floatArrayOf(7.3f, 7.85f, 8.4f)
private const val LOOP = 10f

// After the last touch, the loop waits this long before it starts again.
private const val RESUME = 3f

private val EaseOut = CubicBezierEasing(0f, 0f, 0.58f, 1f)
private val EaseIn = CubicBezierEasing(0.42f, 0f, 1f, 1f)

// Outlines, the same strings in the three stacks: 20 dp boxes for the toolbar, 18 dp boxes for
// the file icons, 12 dp boxes for the cross and the spark.
private fun outline(d: String) = PathParser().parsePathString(d).toPath()

private val Note = outline(
    "M 16.4 9.4 L 16.4 13.4 C 16.4 15.06 15.06 16.4 13.4 16.4 L 6.6 16.4 C 4.94 16.4 3.6 15.06 3.6 13.4 L 3.6 6.6 C 3.6 4.94 4.94 3.6 6.6 3.6 L 10.6 3.6 M 15.2 2.4 L 15.2 7.2 M 12.8 4.8 L 17.6 4.8",
)
private val Wand = outline(
    "M 4 16 L 11.4 8.6 M 14.4 2.2 C 14.4 4.58 15.42 5.6 17.8 5.6 C 15.42 5.6 14.4 6.62 14.4 9 C 14.4 6.62 13.38 5.6 11 5.6 C 13.38 5.6 14.4 4.58 14.4 2.2 Z M 7.2 4.6 L 7.21 4.6 M 15.8 12.4 L 15.81 12.4",
)
private val Lasso = outline(
    "M 16.4 10 C 16.4 13.53 13.53 16.4 10 16.4 C 6.47 16.4 3.6 13.53 3.6 10 C 3.6 6.47 6.47 3.6 10 3.6 C 13.53 3.6 16.4 6.47 16.4 10 Z",
)
private val Marquee = outline(
    "M 10.2 3.8 L 6.2 3.8 C 4.87 3.8 3.8 4.87 3.8 6.2 L 3.8 10.2 M 3.8 13.2 C 3.8 14.53 4.87 15.6 6.2 15.6 L 8.4 15.6 M 13.2 3.8 C 14.53 3.8 15.6 4.87 15.6 6.2 L 15.6 8.4",
)
private val Cursor = outline("M 10.2 10.2 L 17.2 12.6 L 14 14 L 12.6 17.2 Z")
private val Mark = outline(
    "M 12.2 10 C 12.2 12.54 10.14 14.6 7.6 14.6 C 5.06 14.6 3 12.54 3 10 C 3 7.46 5.06 5.4 7.6 5.4 C 10.14 5.4 12.2 7.46 12.2 10 Z M 17 10 C 17 12.54 14.94 14.6 12.4 14.6 C 9.86 14.6 7.8 12.54 7.8 10 C 7.8 7.46 9.86 5.4 12.4 5.4 C 14.94 5.4 17 7.46 17 10 Z",
)
private val Arrow = outline("M 10 15.2 L 10 5 M 5.8 9.2 L 10 5 L 14.2 9.2")
private val Cross = outline("M 3.2 3.2 L 8.8 8.8 M 8.8 3.2 L 3.2 8.8")
private val Spark = outline(
    "M 5 2.8 C 5 5.88 6.32 7.2 9.4 7.2 C 6.32 7.2 5 8.52 5 11.6 C 5 8.52 3.68 7.2 0.6 7.2 C 3.68 7.2 5 5.88 5 2.8 Z M 9.8 0.8 C 9.8 2.34 10.46 3 12 3 C 10.46 3 9.8 3.66 9.8 5.2 C 9.8 3.66 9.14 3 7.6 3 C 9.14 3 9.8 2.34 9.8 0.8 Z",
)

private val PdfPage = outline(
    "M 5.4 1.6 L 10.4 1.6 L 14.6 5.8 L 14.6 14.4 C 14.6 15.5 13.7 16.4 12.6 16.4 L 5.4 16.4 C 4.3 16.4 3.4 15.5 3.4 14.4 L 3.4 3.6 C 3.4 2.5 4.3 1.6 5.4 1.6 Z",
)
private val PdfFold = outline("M 10.4 1.6 L 14.6 5.8 L 11.8 5.8 C 11.03 5.8 10.4 5.17 10.4 4.4 Z")
private val PdfLines = outline("M 6.2 9.6 L 11.8 9.6 M 6.2 12.6 L 9.8 12.6")
private val ImageTile = outline(
    "M 6.2 2.2 L 11.8 2.2 C 14.01 2.2 15.8 3.99 15.8 6.2 L 15.8 11.8 C 15.8 14.01 14.01 15.8 11.8 15.8 L 6.2 15.8 C 3.99 15.8 2.2 14.01 2.2 11.8 L 2.2 6.2 C 2.2 3.99 3.99 2.2 6.2 2.2 Z",
)
private val ImageHills = outline("M 4.8 12.4 L 7.6 9 L 9.8 11.4 L 11.4 9.8 L 13.4 12.4 M 11.6 6.2 L 11.61 6.2")
private val FolderTab = outline(
    "M 1.8 8 L 1.8 4.4 C 1.8 3.52 2.52 2.8 3.4 2.8 L 6.6 2.8 L 8.4 4.8 L 14.6 4.8 C 15.48 4.8 16.2 5.52 16.2 6.4 L 16.2 8 Z",
)
private val FolderBody = outline(
    "M 4 6 L 14 6 C 15.22 6 16.2 6.98 16.2 8.2 L 16.2 13.6 C 16.2 14.82 15.22 15.8 14 15.8 L 4 15.8 C 2.78 15.8 1.8 14.82 1.8 13.6 L 1.8 8.2 C 1.8 6.98 2.78 6 4 6 Z",
)

/** One of the three selection tools: the part drawn dashed, the part drawn solid. */
private class ToolGlyph(val dashed: Path?, val solid: Path?, val label: String)

private val Tools = listOf(
    ToolGlyph(null, Wand, "Magic select"),
    ToolGlyph(Lasso, null, "Lasso"),
    ToolGlyph(Marquee, Cursor, "Marquee"),
)

private val DefaultFiles = listOf(
    AttachedFile(
        name = "folder 3",
        kind = AttachedFileKind.Folder,
        meta = "FOLDER · 12 ITEMS · 48MB",
        title = "Trip planning",
        summary = "Twelve files: three itineraries, two hotel confirmations, the car rental voucher and a shared budget sheet that was last edited on Jan 24.",
    ),
    AttachedFile(
        name = "cloud.png",
        kind = AttachedFileKind.Image,
        meta = "PNG · 1.1MB · 2048 × 1365",
        title = "Cloud over a ridge",
        summary = "A single cumulus cloud above a dark mountain ridge under a clear blue sky, lit from the left in the late afternoon. No people, no text.",
    ),
    AttachedFile(
        name = "contract-agreement brief.pdf",
        kind = AttachedFileKind.Pdf,
        meta = "PDF · 2.3MB · 93 LINES",
        title = "Service Agreement",
        summary = "This Service Agreement is entered into as of Jan 27, by and between the Client and the Provider, each with a mailing address on file. The Provider agrees to perform the services described in Exhibit A, in accordance with the terms and conditions of this Agreement, by the dates set out in its schedule.",
    ),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun trayText(size: Float, lineHeight: Float, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ChipStyle = trayText(14f, 18f, Ink, FontWeight.Medium)
private val FieldStyle = trayText(17f, 22f, Ink)
private val PlaceholderStyle = trayText(17f, 22f, Color(0xFFB5B5B5))
private val ModelStyle = trayText(15f, 20f, Ink, FontWeight.Medium)
private val NameStyle = trayText(16f, 22f, Ink, FontWeight.Medium)
private val MetaStyle = trayText(12f, 16f, Color(0xFF8A8A8A)).copy(fontFamily = FontFamily.Monospace, letterSpacing = 0.4.sp)
private val LabelStyle = trayText(11f, 14f, Color(0xFF8B5CF6), FontWeight.Medium)
    .copy(brush = Brush.horizontalGradient(listOf(Color(0xFF8B5CF6), Color(0xFFF59E0B))))
private val TitleStyle = trayText(15f, 20f, Color(0xFF2A2A2E), FontWeight.SemiBold)
private val SummaryStyle = trayText(12.5f, 17f, Color(0xFF6F6F73))

/** A chip in the tray; one on its way out stays until it has shrunk. */
private data class Entry(val id: Int, val file: AttachedFile, val leaving: Boolean = false)

/** The file in the preview card, and where the card hangs: its left edge and the point of its top edge under the chip. */
private data class Preview(val file: AttachedFile, val left: Float, val origin: Float)

/** Waits until [seconds] after [start], a `System.nanoTime()` reading. */
private suspend fun reach(start: Long, seconds: Float) {
    val wait = (start + (seconds * 1e9).toLong() - System.nanoTime()) / 1_000_000
    if (wait > 0) delay(wait)
}

/** A soft shadow under a rounded shape of the modifier's own size, [dy] dp lower. */
private fun Modifier.softShadow(shadow: SoftRoundRect, radius: Float, dy: Float) = drawBehind {
    val w = size.width / density
    val h = size.height / density
    scale(density, density, pivot = Offset.Zero) {
        drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, dy, w, h, radius.coerceAtMost(h / 2f)) }
    }
}

private fun DrawScope.strokeOutline(outline: Path, color: Color, width: Float, dash: Boolean = false) {
    drawPath(
        outline,
        color,
        style = Stroke(
            width,
            cap = StrokeCap.Round,
            join = StrokeJoin.Round,
            pathEffect = if (dash) PathEffect.dashPathEffect(floatArrayOf(2.3f, 2.5f)) else null,
        ),
    )
}

/** A stroked outline in a [size] box. */
@Composable
private fun Glyph(outline: Path, color: Color, modifier: Modifier = Modifier, size: Dp = 20.dp, strokeWidth: Float = 1.5f, dash: Boolean = false) {
    Canvas(modifier.size(size)) {
        scale(density, density, pivot = Offset.Zero) { strokeOutline(outline, color, strokeWidth, dash) }
    }
}

/** A red page, a blue picture or a yellow folder, drawn in an 18 dp box. */
@Composable
private fun FileIcon(kind: AttachedFileKind, size: Dp = 18.dp) {
    Canvas(Modifier.size(size)) {
        val unit = this.size.width / 18f
        scale(unit, unit, pivot = Offset.Zero) {
            when (kind) {
                AttachedFileKind.Pdf -> {
                    drawPath(PdfPage, Color(0xFFFF5B4F))
                    drawPath(PdfFold, Color(0xFFFFC2BD))
                    strokeOutline(PdfLines, Color.White, 1.3f)
                }
                AttachedFileKind.Image -> {
                    drawPath(ImageTile, Color(0xFF8ECBFF))
                    strokeOutline(ImageHills, Color.White, 1.3f)
                }
                AttachedFileKind.Folder -> {
                    drawPath(FolderTab, Color(0xFFF5A623))
                    drawPath(FolderBody, Color(0xFFFFC83D))
                }
            }
        }
    }
}

/** A button's content dips under a finger, or under the script's. */
@Composable
private fun Dip(
    held: Boolean,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    scale: Float = 0.92f,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val down by interaction.collectIsPressedAsState()
    val dip by animateFloatAsState(if (down || held) scale else 1f, tween(130, easing = EaseOutStrong), label = "dip")
    Box(
        Modifier
            .graphicsLayer {
                scaleX = dip
                scaleY = dip
            }
            .clickable(interactionSource = interaction, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label }
            .then(modifier),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

/**
 * One attachment in the tray. It opens its own room in the row, drops into it from above, and
 * gives the room back when it leaves.
 */
@Composable
private fun Chip(
    file: AttachedFile,
    leaving: Boolean,
    held: Boolean,
    crossHeld: Boolean,
    reducedMotion: Boolean,
    shadow: SoftRoundRect,
    onPlaced: (LayoutCoordinates) -> Unit,
    onHold: (Boolean) -> Unit,
    onRemove: () -> Unit,
    onGone: () -> Unit,
) {
    var holding by remember { mutableStateOf(false) }
    // Share of its width the chip takes in the row, how far it has dropped in, how far it has gone.
    val room = remember { Animatable(0f) }
    val drop = remember { Animatable(0f) }
    val gone = remember { Animatable(0f) }
    val hold by rememberUpdatedState(onHold)
    val fill by animateColorAsState(if (holding || held) Color(0xFFF0F0F0) else Color.White, tween(150, easing = EaseOut), label = "fill")

    LaunchedEffect(Unit) {
        // The row opens first, then the chip lands in the gap.
        if (reducedMotion) {
            launch { room.animateTo(1f, tween(200, easing = EaseOut)) }
            drop.animateTo(1f, tween(200, easing = EaseOut))
        } else {
            launch { room.animateTo(1f, SlotSpring) }
            delay(60)
            drop.animateTo(1f, DropSpring)
        }
    }

    LaunchedEffect(leaving) {
        if (!leaving) return@LaunchedEffect
        launch { gone.animateTo(1f, tween(180, easing = EaseIn)) }
        launch { room.animateTo(0f, tween(300, delayMillis = 80, easing = EaseOutStrong)) }
        delay(450)
        onGone()
    }

    Row(
        Modifier
            // The chip keeps its own width; the row is given the share of it that `room` allows.
            .layout { measurable, constraints ->
                val chip = measurable.measure(Constraints())
                val width = ((chip.width + CHIP_GAP.dp.roundToPx()) * room.value).roundToInt().coerceAtLeast(0)
                val height = TRAY_H.dp.roundToPx()
                layout(width, height) { chip.place(0, (height - chip.height) / 2) }
            }
            .graphicsLayer {
                val d = drop.value
                val g = gone.value
                alpha = d.coerceIn(0f, 1f) * (1f - g)
                if (!reducedMotion) {
                    translationY = -DROP_HEIGHT.dp.toPx() * (1f - d)
                    scaleX = (0.8f + 0.2f * d) * (1f - 0.4f * g)
                    scaleY = (0.8f + 0.2f * d) * (1f - 0.4f * g)
                }
            }
            .onGloballyPositioned(onPlaced)
            .height(CHIP_H.dp)
            .softShadow(shadow, CHIP_H / 2f, 1f)
            .background(fill, CircleShape)
            .border(1.dp, Color(0xFFEBEBEB), CircleShape)
            // Held long enough, the chip shows its preview until the finger lifts.
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = {
                        holding = true
                        hold(true)
                    },
                    onPress = {
                        tryAwaitRelease()
                        if (holding) {
                            holding = false
                            hold(false)
                        }
                    },
                )
            }
            .padding(start = 8.dp, end = 3.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        FileIcon(file.kind)
        BasicText(
            file.name,
            style = ChipStyle,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(start = 6.dp).widthIn(max = NAME_MAX.dp),
        )
        Dip(held = crossHeld, label = "Remove ${file.name}", onClick = onRemove, modifier = Modifier.size(26.dp), scale = 0.8f) {
            Glyph(Cross, Ink, size = 12.dp)
        }
    }
}

/**
 * Prompt composer with a tray of attachments. Files drop into a strip that grows out of the top
 * of the text area, each one pushing the earlier chips along; holding a chip drops a preview card
 * out of it, and a chip's cross shrinks it away, the tray folding shut after the last one.
 *
 * @param value The field's text when the parent owns it. Leave it null and the composer keeps its own.
 * @param autoPlay Attaches three files, previews the newest and removes them again in a loop, without the
 *   keyboard, until the composer is touched.
 * @param files What the attach button adds, one per tap, in this order. autoPlay adds the first three.
 * @param onSend Called with the trimmed text and the attached files, newest first.
 * @param onPreview Called when a chip has been held long enough to open its preview.
 * @param onTool Called with the index of the selection tool that was picked.
 */
@Composable
fun AttachmentTrayInput(
    modifier: Modifier = Modifier,
    value: String? = null,
    onValueChange: ((String) -> Unit)? = null,
    autoPlay: Boolean = false,
    placeholder: String = "Add instructions from attached images...",
    files: List<AttachedFile> = DefaultFiles,
    modelLabel: String = "Nova 2.0",
    summaryLabel: String = "Summary by AI",
    onSend: ((String, List<AttachedFile>) -> Unit)? = null,
    onAttach: ((AttachedFile) -> Unit)? = null,
    onRemove: ((AttachedFile) -> Unit)? = null,
    onPreview: ((AttachedFile) -> Unit)? = null,
    onTool: ((Int) -> Unit)? = null,
    onModel: (() -> Unit)? = null,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val screenDensity = LocalDensity.current
    val controlled = value != null
    val auto = autoPlay && !controlled && !reducedMotion

    var inner by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    // A touch hands the composer to the user until it has been left alone for a while.
    var taken by remember { mutableStateOf(false) }
    var activity by remember { mutableIntStateOf(0) }
    // The chips in the tray, newest first.
    var entries by remember { mutableStateOf(listOf<Entry>()) }
    var preview by remember { mutableStateOf<Preview?>(null) }
    var previewOpen by remember { mutableStateOf(false) }
    var tool by remember { mutableIntStateOf(0) }
    // The script's finger.
    var heldAdd by remember { mutableStateOf(false) }
    var heldChip by remember { mutableStateOf<Int?>(null) }
    var heldCross by remember { mutableStateOf<Int?>(null) }

    val text = value ?: inner
    // The script is running.
    val playing = auto && !taken
    val attached = entries.filter { !it.leaving }
    val trayOpen = attached.isNotEmpty()
    val canSend = text.isNotBlank() || trayOpen
    val idle = auto && taken && !focused && text.isEmpty()

    val open = remember { Animatable(0f) }
    val card = remember { Animatable(0f) }
    val tile by animateFloatAsState(
        tool.toFloat(),
        if (reducedMotion) tween(200, easing = EaseOut) else TileSpring,
        label = "tile",
    )
    val chipScroll = rememberScrollState()
    // Where the chips are. Kept out of the state: it changes on every frame of a scroll and is
    // read only when a preview opens.
    val places = remember {
        object {
            var root: LayoutCoordinates? = null
            val chips = mutableMapOf<Int, LayoutCoordinates>()
            var serial = 0
            var nextFile = 0
        }
    }
    val shadows = remember {
        object {
            val composer = SoftRoundRect(rgba(0, 0, 0, 0.05f), sigma = 12f)
            val small = SoftRoundRect(rgba(0, 0, 0, 0.06f), sigma = 1.5f)
            val preview = SoftRoundRect(rgba(0, 0, 0, 0.12f), sigma = 20f)
        }
    }

    fun add(file: AttachedFile): Int {
        places.serial += 1
        entries = listOf(Entry(places.serial, file)) + entries
        return places.serial
    }

    fun leave(id: Int) {
        entries = entries.map { if (it.id == id) it.copy(leaving = true) else it }
    }

    fun openPreview(id: Int, file: AttachedFile) {
        val root = places.root ?: return
        val chip = places.chips[id]?.takeIf { it.isAttached } ?: return
        val chipX = with(screenDensity) { root.localPositionOf(chip, Offset.Zero).x.toDp().value }
        val chipWidth = with(screenDensity) { chip.size.width.toDp().value }
        val left = (chipX - 6f).coerceIn(0f, WIDTH - PREVIEW_W)
        preview = Preview(file, left, (chipX + chipWidth / 2f - left).coerceIn(0f, PREVIEW_W))
        previewOpen = true
    }

    /** Any touch takes the composer away from the script. */
    fun interrupt() {
        activity += 1
        if (!playing) return
        taken = true
        heldAdd = false
        heldChip = null
        heldCross = null
        previewOpen = false
    }

    fun write(next: String) {
        if (!controlled) inner = next
        onValueChange?.invoke(next)
    }

    // The strip grows out of the top edge of the text area, and folds back into it.
    LaunchedEffect(trayOpen, reducedMotion) {
        when {
            reducedMotion -> open.animateTo(if (trayOpen) 1f else 0f, tween(200, easing = EaseOut))
            trayOpen -> open.animateTo(1f, TraySpring)
            else -> open.animateTo(0f, tween(300, delayMillis = FOLD_DELAY, easing = EaseOutStrong))
        }
    }

    // The preview drops out of the chip it belongs to, and is pulled back into it.
    LaunchedEffect(previewOpen, reducedMotion) {
        when {
            reducedMotion -> card.animateTo(if (previewOpen) 1f else 0f, tween(200, easing = EaseOut))
            previewOpen -> card.animateTo(1f, PreviewSpring)
            else -> card.animateTo(0f, tween(RETRACT, easing = EaseIn))
        }
    }

    // A new chip lands at the front, so the row goes back to its start.
    LaunchedEffect(entries.firstOrNull()?.id) {
        chipScroll.animateScrollTo(0)
    }

    // One loop after another, every step at its own moment after the loop began.
    LaunchedEffect(playing, files) {
        if (!playing || files.isEmpty()) return@LaunchedEffect
        places.nextFile = ADD_AT.size
        var start = System.nanoTime()
        while (true) {
            // The chips this loop has added, newest first.
            val added = mutableListOf<Entry>()
            ADD_AT.forEachIndexed { index, moment ->
                reach(start, moment - PRESS_LEAD)
                heldAdd = true
                reach(start, moment)
                heldAdd = false
                val file = files[index % files.size]
                added.add(0, Entry(add(file), file))
            }
            reach(start, HOLD_AT)
            heldChip = added[0].id
            reach(start, HOLD_AT + HOLD / 1000f)
            openPreview(added[0].id, added[0].file)
            reach(start, RELEASE_AT)
            heldChip = null
            previewOpen = false
            REMOVE_AT.forEachIndexed { index, moment ->
                reach(start, moment - PRESS_LEAD)
                heldCross = added[index].id
                reach(start, moment)
                heldCross = null
                leave(added[index].id)
            }
            reach(start, LOOP)
            start += (LOOP * 1e9).toLong()
        }
    }

    // The loop starts over from an empty tray a few seconds after the last touch.
    LaunchedEffect(idle, activity) {
        if (!idle) return@LaunchedEffect
        delay((RESUME * 1000).toLong())
        previewOpen = false
        entries = entries.map { it.copy(leaving = true) }
        delay(900)
        taken = false
    }

    val outer = RoundedCornerShape(RADIUS.dp)
    val bezel = RoundedCornerShape(10.dp)
    // A chip's preview opens after a shorter hold than the system's long press.
    val baseConfiguration = LocalViewConfiguration.current
    val holdConfiguration = remember(baseConfiguration) {
        object : ViewConfiguration by baseConfiguration {
            override val longPressTimeoutMillis: Long get() = HOLD
        }
    }

    Box(
        modifier.size(WIDTH.dp, HEIGHT.dp).onGloballyPositioned { places.root = it },
        contentAlignment = Alignment.BottomCenter,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .softShadow(shadows.composer, RADIUS, 6f)
                .background(Color(0xFFF5F5F5), outer)
                .border(1.dp, Color(0xFFE8E8E8), outer)
                .padding(PAD.dp),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((TRAY_H * open.value).coerceAtLeast(0f).dp)
                    .clipToBounds(),
            ) {
                CompositionLocalProvider(LocalViewConfiguration provides holdConfiguration) {
                    Row(
                        Modifier
                            .align(Alignment.BottomStart)
                            .wrapContentHeight(Alignment.Bottom, unbounded = true)
                            .height(TRAY_H.dp)
                            .horizontalScroll(chipScroll)
                            .padding(start = TRAY_INSET.dp),
                    ) {
                        for (entry in entries) {
                            key(entry.id) {
                                Chip(
                                    file = entry.file,
                                    leaving = entry.leaving,
                                    held = heldChip == entry.id,
                                    crossHeld = heldCross == entry.id,
                                    reducedMotion = reducedMotion,
                                    shadow = shadows.small,
                                    onPlaced = { places.chips[entry.id] = it },
                                    onHold = { holding ->
                                        interrupt()
                                        if (holding) {
                                            view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                                            onPreview?.invoke(entry.file)
                                            openPreview(entry.id, entry.file)
                                        } else {
                                            previewOpen = false
                                        }
                                    },
                                    onRemove = {
                                        interrupt()
                                        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                        onRemove?.invoke(entry.file)
                                        leave(entry.id)
                                    },
                                    onGone = {
                                        places.chips.remove(entry.id)
                                        entries = entries.filter { it.id != entry.id }
                                    },
                                )
                            }
                        }
                    }
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .softShadow(shadows.small, CARD_R, 1f)
                    .background(Color.White, RoundedCornerShape(CARD_R.dp))
                    .border(1.dp, Color(0xFFE6E6E6), RoundedCornerShape(CARD_R.dp)),
            ) {
                BasicTextField(
                    value = text,
                    onValueChange = { write(it) },
                    maxLines = 2,
                    textStyle = FieldStyle,
                    cursorBrush = SolidColor(Ink),
                    decorationBox = { input ->
                        Box {
                            if (text.isEmpty()) BasicText(placeholder, style = PlaceholderStyle, maxLines = 1)
                            input()
                        }
                    },
                    modifier = Modifier
                        .padding(start = 16.dp, end = 16.dp, top = 14.dp)
                        .fillMaxWidth()
                        .height(TEXT_H.dp)
                        .onFocusChanged { state ->
                            if (state.isFocused == focused) return@onFocusChanged
                            focused = state.isFocused
                            interrupt()
                        },
                )

                Row(
                    Modifier.fillMaxWidth().padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Dip(
                        held = heldAdd,
                        label = "Attach a file",
                        onClick = {
                            interrupt()
                            if (files.isNotEmpty()) {
                                val file = files[places.nextFile % files.size]
                                places.nextFile += 1
                                add(file)
                                view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                onAttach?.invoke(file)
                            }
                        },
                        modifier = Modifier
                            .size(CONTROL.dp)
                            .softShadow(shadows.small, 10f, 1f)
                            .background(Color.White, bezel)
                            .border(1.dp, Color(0xFFE8E8E8), bezel),
                    ) {
                        Glyph(Note, Ink)
                    }

                    Box(Modifier.background(Color(0xFFF3F3F3), bezel).padding(3.dp)) {
                        // The white tile slides under the tool that is picked.
                        Box(
                            Modifier
                                .size(TOOL.dp)
                                .graphicsLayer { translationX = tile * TOOL.dp.toPx() }
                                .softShadow(shadows.small, 8f, 1f)
                                .background(Color.White, RoundedCornerShape(8.dp)),
                        )
                        Row {
                            Tools.forEachIndexed { slot, glyph ->
                                val color by animateColorAsState(
                                    if (slot == tool) Ink else Color(0xFF8D8D8D),
                                    tween(200, easing = EaseOut),
                                    label = "tool",
                                )
                                Box(
                                    Modifier
                                        .size(TOOL.dp)
                                        .clickable(
                                            interactionSource = remember { MutableInteractionSource() },
                                            indication = null,
                                            role = Role.Button,
                                        ) {
                                            tool = slot
                                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                                            onTool?.invoke(slot)
                                        }
                                        .semantics { contentDescription = glyph.label },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    glyph.dashed?.let { Glyph(it, color, dash = true) }
                                    glyph.solid?.let { Glyph(it, color) }
                                }
                            }
                        }
                    }

                    Dip(
                        held = false,
                        label = modelLabel,
                        onClick = { onModel?.invoke() },
                        modifier = Modifier
                            .height(CONTROL.dp)
                            .softShadow(shadows.small, 10f, 1f)
                            .background(Color.White, bezel)
                            .border(1.dp, Color(0xFFE8E8E8), bezel)
                            .padding(start = 8.dp, end = 11.dp),
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Glyph(Mark, Ink)
                            BasicText(modelLabel, style = ModelStyle, maxLines = 1, modifier = Modifier.clearAndSetSemantics {})
                        }
                    }

                    Spacer(Modifier.weight(1f))

                    val disc by animateColorAsState(if (canSend) Ink else Color(0xFFD6D6D6), tween(200, easing = EaseOut), label = "send")
                    Dip(
                        held = false,
                        label = "Send",
                        enabled = canSend,
                        onClick = {
                            interrupt()
                            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
                            onSend?.invoke(text.trim(), attached.map { it.file })
                            write("")
                            entries = entries.map { it.copy(leaving = true) }
                        },
                        modifier = Modifier.size(CONTROL.dp).background(disc, CircleShape),
                    ) {
                        Glyph(Arrow, Color.White, strokeWidth = 1.9f)
                    }
                }
            }
        }

        // The preview hangs over the text area and past the composer's bottom edge.
        val shown = preview
        Column(
            Modifier
                .align(Alignment.TopStart)
                .offset(x = (shown?.left ?: 0f).dp, y = PREVIEW_TOP.dp)
                .wrapContentHeight(Alignment.Top, unbounded = true)
                .width(PREVIEW_W.dp)
                .graphicsLayer {
                    val c = card.value
                    alpha = c.coerceIn(0f, 1f)
                    transformOrigin = TransformOrigin((shown?.origin ?: 40f) / PREVIEW_W, 0f)
                    if (!reducedMotion) {
                        translationY = -10.dp.toPx() * (1f - c)
                        scaleX = 0.6f + 0.4f * c
                        scaleY = 0.6f + 0.4f * c
                    }
                }
                .softShadow(shadows.preview, 20f, 12f)
                .background(Color.White, RoundedCornerShape(20.dp))
                .border(1.dp, Color(0xFFEDEDED), RoundedCornerShape(20.dp))
                .padding(12.dp)
                .then(if (previewOpen) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            // The rows of the preview come up one after another behind the card.
            @Composable
            fun rise(row: Int): Modifier {
                val t by animateFloatAsState(
                    if (previewOpen) 1f else 0f,
                    if (previewOpen) tween(260, delayMillis = 100 + row * 60, easing = EaseOut) else tween(100, easing = EaseOut),
                    label = "rise",
                )
                return Modifier.graphicsLayer {
                    alpha = t
                    if (!reducedMotion) translationY = 8.dp.toPx() * (1f - t)
                }
            }

            BasicText(
                shown?.file?.name ?: "",
                style = NameStyle,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = rise(0).padding(start = 4.dp),
            )
            Row(
                rise(1).padding(start = 4.dp, top = 4.dp).height(16.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FileIcon(shown?.file?.kind ?: AttachedFileKind.Pdf, size = 14.dp)
                BasicText(shown?.file?.meta ?: "", style = MetaStyle, maxLines = 1)
            }
            Box(
                rise(2)
                    .padding(top = 10.dp)
                    .fillMaxWidth()
                    .height(SUMMARY_H.dp)
                    .clip(RoundedCornerShape(14.dp))
                    .border(1.dp, Color(0xFFECECEC), RoundedCornerShape(14.dp)),
            ) {
                Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true).padding(14.dp)) {
                    Row(
                        Modifier.height(14.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Canvas(Modifier.size(12.dp)) {
                            scale(density, density, pivot = Offset.Zero) { drawPath(Spark, Color(0xFF8B5CF6)) }
                        }
                        BasicText(summaryLabel, style = LabelStyle, maxLines = 1)
                    }
                    BasicText(
                        shown?.file?.title ?: "",
                        style = TitleStyle,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 8.dp),
                    )
                    BasicText(shown?.file?.summary ?: "", style = SummaryStyle, modifier = Modifier.padding(top = 6.dp))
                }
                // The text runs out of the box and fades before its edge.
                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .height(44.dp)
                        .background(Brush.verticalGradient(listOf(rgba(255, 255, 255, 0f), Color.White))),
                )
            }
        }
    }
}
