package com.ngocdevv.nativecook.blocks.sheets.stagecardsheet

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.FileDownload
import androidx.compose.material.icons.outlined.IosShare
import androidx.compose.material.icons.outlined.People
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.util.lerp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

class Permission(val icon: ImageVector, val title: String, val caption: String)

val SamplePermissions = listOf(
    Permission(Icons.Outlined.People, "Allow collaboration", "Anyone with the link can edit and add tracks"),
    Permission(Icons.Outlined.FileDownload, "Allow downloads", "Anyone with the link can download audio"),
    Permission(Icons.Outlined.AccountCircle, "Require account", "Only people with an account will be able to listen"),
)

/** Seconds for one pass of the autoPlay loop. */
const val LOOP = 9f

// The timeline, in seconds from the start of a loop.
private const val TAP_AT = 0.9f
private const val DIALOG_AT = 4.2f
private const val CONTINUE_PRESS_AT = 5.5f
private const val CONFIRM_AT = 5.75f
/** Seconds after the last touch before the loop starts again from rest. */
private const val USER_HOLD = 3f

private val Page = Color(0xFFFDFCFD)
private val Backdrop = Color(0xFF000000)
private val Behind = Color(0xFFC9C8CD)
private val Ink = Color(0xFF111113)
private val Body = Color(0xFF3C3C43)
private val Mute = Color(0xFF7C7C83)
private val Faint = Color(0xFF9A9AA0)
private val Well = Color(0xFFF2F2F4)
private val Line = rgba(60, 60, 67, 0.12f)
private val Danger = Color(0xFFE5252A)
private val Accent = Color(0xFF2B7BE8)
private val On = Color(0xFF34C759)
private val Off = Color(0xFFE9E9EB)
private val Avatar = Color(0xFF8E8E93)
private val ArtStops = arrayOf(
    0f to Color(0xFFF9A8DC),
    0.38f to Color(0xFFEE72D6),
    0.74f to Color(0xFFD43BE0),
    1f to Color(0xFFB81AD2),
)
private val Shade = rgba(120, 0, 150, 1f)
private val Glow = rgba(226, 72, 240, 0.42f)
private val Contact = rgba(110, 0, 130, 0.16f)
private val GlowPage = rgba(232, 96, 246, 0.10f)
private val Scrim = Color.Black.copy(alpha = 0.32f)
private val DialogFill = rgba(250, 250, 252, 0.97f)
private val DialogLine = rgba(60, 60, 67, 0.3f)
private const val HAIRLINE = 1f / 3f

private const val MARGIN = 20f
private const val CLOSE = 36f
// The page is a sheet over a darkened screen, with the page it came from peeking out behind it.
private const val SHEET_GAP = 10f
private const val SHEET_RADIUS = 14f
// The artwork as a hero, and as the small cover above the panel (from the sheet's top).
private const val ART_CLOSED_MAX = 330f
private const val ART_OPEN = 120f
private const val ART_RADIUS_CLOSED = 22f
private const val ART_RADIUS_OPEN = 14f
private const val ART_TOP_CLOSED = 104f
private const val ART_TOP_OPEN = 24f
private const val TITLE_GAP = 24f
private const val TITLE_GAP_OPEN = 14f
private const val TITLE_HEIGHT = 32f
private const val CAPTION_HEIGHT = 20f
// The title shrinks with the artwork, from its top edge.
private const val TITLE_SCALE_OPEN = 0.78f
private const val PANEL_GAP = 20f
private const val BUTTON_HEIGHT = 52f
private const val FOOTNOTE_HEIGHT = 32f
private const val DOCK_HEIGHT = BUTTON_HEIGHT + 12f + FOOTNOTE_HEIGHT
private const val ROW_HEIGHT = 64f
private const val SWITCH_WIDTH = 51f
private const val SWITCH_HEIGHT = 31f
private const val DIALOG_WIDTH = 270f

// One value moves the artwork and reveals the panel, so they read as one object. Damping ratio is
// Reanimated's damping over 2 * sqrt(stiffness * mass).
private val Spring = spring<Float>(dampingRatio = 19f / (2f * sqrt(130f)), stiffness = 130f)
private val DialogSpring = spring<Float>(dampingRatio = 17f / (2f * sqrt(260f)), stiffness = 260f)
private const val FADE = 180
/** Seconds the spinner shows before the sheet opens. */
private const val LOADING = 0.7f
private const val BUSY_IN = 160
private const val SPIN = 0.7f
// Rows of the panel rise one after another.
private const val ROWS = 8
private const val ROW_IN = 450f
private const val ROW_STAGGER = 60f
private const val ROW_RISE = 40f
private const val PANEL_IN = ROW_IN + ROW_STAGGER * ROWS
private const val PANEL_OUT = 200
// The artwork waits for the panel to clear before it grows back.
private const val GROW_DELAY = 110L
private const val STREAK_WIPE = 520
private const val REST_FADE = 350

// Reanimated's default curve is quad in-out.
private val QuadInOut = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)
private val QuadOut = CubicBezierEasing(0.5f, 1f, 0.89f, 1f)
private val QuadIn = CubicBezierEasing(0.11f, 0f, 0.5f, 0f)
private val CubicOut = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)
// CSS `ease`, the switch knob's transition.
private val CssEase = CubicBezierEasing(0.25f, 0.1f, 0.25f, 1f)

// Rays of light across the artwork, fanning out from its lower left, in a 100 x 100 box:
// x1, y1, x2, y2, width, peak opacity.
private val Rays = listOf(
    floatArrayOf(-6f, 80f, 104f, 30f, 1.5f, 0.95f),
    floatArrayOf(6f, 76f, 102f, 46f, 0.7f, 0.9f),
    floatArrayOf(14f, 84f, 98f, 56f, 0.5f, 0.8f),
    floatArrayOf(0f, 72f, 90f, 22f, 0.45f, 0.85f),
    floatArrayOf(22f, 70f, 106f, 38f, 1.0f, 0.9f),
    floatArrayOf(-8f, 88f, 80f, 64f, 1.1f, 0.75f),
    floatArrayOf(30f, 64f, 100f, 26f, 0.4f, 0.8f),
    floatArrayOf(10f, 92f, 94f, 72f, 0.45f, 0.6f),
    floatArrayOf(42f, 60f, 106f, 18f, 0.65f, 0.85f),
    floatArrayOf(16f, 80f, 74f, 58f, 3.2f, 0.28f),
    floatArrayOf(-4f, 74f, 96f, 36f, 5f, 0.16f),
)
// Four-pointed glints near the rays' ends: x, y, size.
private val Glints = listOf(floatArrayOf(80f, 20f, 3.2f), floatArrayOf(66f, 31f, 1.8f), floatArrayOf(90f, 46f, 1.4f))

private fun glint(x: Float, y: Float, r: Float) = Path().apply {
    val k = r * 0.22f
    moveTo(x, y - r)
    lineTo(x + k, y - k)
    lineTo(x + r, y)
    lineTo(x + k, y + k)
    lineTo(x, y + r)
    lineTo(x - k, y + k)
    lineTo(x - r, y)
    lineTo(x - k, y - k)
    close()
}

/** The button greys out as the sheet opens: a straight mix from ink to light grey. */
private fun buttonColor(t: Float) = Color(
    lerp(17f, 214f, t) / 255f,
    lerp(17f, 214f, t) / 255f,
    lerp(19f, 219f, t) / 255f,
)

private enum class Stage { Closed, Loading, Open }

/**
 * A project page whose artwork is a hero until "Enable link sharing" is pressed: then it shrinks
 * into a cover with a streak of light across it while the sharing panel rises below. "Make project
 * private" asks first in an alert, and Continue folds everything back. The autoPlay loop presses
 * the same buttons; nothing is shared or deleted here.
 *
 * @param generatedOn Written under "Reset link".
 * @param autoPlay Opens the sheet and closes it again by itself every [LOOP] seconds.
 * @param onEnable Called when the sheet has opened, i.e. the link exists.
 * @param onMakePrivate Called when "Continue" in the dialog makes the project private.
 */
@Composable
fun StageCardSheet(
    title: String = "First",
    caption: String = "60fps · 1 track",
    enableLabel: String = "Enable link sharing",
    footnote: String = "This will create a shareable link. Anyone with the link will be able to listen and share this project.",
    generatedOn: String = "September 24, 2024",
    permissions: List<Permission> = SamplePermissions,
    autoPlay: Boolean = false,
    onEnable: (() -> Unit)? = null,
    onMakePrivate: (() -> Unit)? = null,
    onPermissionChange: ((index: Int, on: Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    StageCardSheetDarkBars()
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val items by rememberUpdatedState(permissions)
    val enabled by rememberUpdatedState(onEnable)
    val madePrivate by rememberUpdatedState(onMakePrivate)
    val changed by rememberUpdatedState(onPermissionChange)

    var stage by remember { mutableStateOf(Stage.Closed) }
    var dialog by remember { mutableStateOf(false) }
    var autoPressed by remember { mutableStateOf(false) }
    var on by remember { mutableStateOf(listOf<Boolean>()) }
    var pending by remember { mutableStateOf<Job?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // 0 closed, 1 open: the artwork's size and the panel come from it.
    val sheet = remember { Animatable(0f) }
    val panel = remember { Animatable(0f) }
    val streak = remember { Animatable(0f) }
    val busy = remember { Animatable(0f) }
    val alert = remember { Animatable(0f) }
    // Turns of the spinner, 0 to 1; read while drawing only.
    val spin = remember { mutableFloatStateOf(0f) }

    fun states() = if (on.size == items.size) on else items.map { false }

    fun move(value: Animatable<Float, *>, to: Float, spec: AnimationSpec<Float> = Spring) {
        scope.launch { value.animateTo(to, if (reducedMotion) tween(FADE, easing = QuadInOut) else spec) }
    }

    fun timed(value: Animatable<Float, *>, to: Float, ms: Int, easing: Easing = QuadInOut) {
        scope.launch { value.animateTo(to, tween(ms, easing = easing)) }
    }

    fun open() {
        pending = null
        stage = Stage.Open
        move(sheet, 1f)
        timed(panel, 1f, if (reducedMotion) FADE else PANEL_IN.toInt(), QuadOut)
        if (reducedMotion) scope.launch { streak.snapTo(1f) } else timed(streak, 1f, STREAK_WIPE, CubicOut)
        enabled?.invoke()
    }

    fun enable() {
        if (stage != Stage.Closed) return
        stage = Stage.Loading
        timed(busy, 1f, BUSY_IN)
        pending = scope.launch {
            delay((LOADING * 1000).toLong())
            open()
        }
    }

    fun closeDialog() {
        dialog = false
        autoPressed = false
        move(alert, 0f, DialogSpring)
    }

    fun makePrivate() {
        closeDialog()
        stage = Stage.Closed
        if (reducedMotion) {
            timed(sheet, 0f, FADE)
        } else {
            scope.launch {
                delay(GROW_DELAY)
                sheet.animateTo(0f, Spring)
            }
        }
        timed(panel, 0f, if (reducedMotion) FADE else PANEL_OUT, QuadIn)
        timed(streak, 0f, FADE)
        timed(busy, 0f, if (reducedMotion) FADE else 500)
        madePrivate?.invoke()
    }

    fun askPrivate() {
        dialog = true
        move(alert, 1f, DialogSpring)
    }

    fun toggle(index: Int) {
        val next = states().toMutableList()
        next[index] = !next[index]
        on = next
        changed?.invoke(index, next[index])
    }

    fun rest() {
        pending?.cancel()
        pending = null
        stage = Stage.Closed
        dialog = false
        autoPressed = false
        on = items.map { false }
        for (value in listOf(sheet, panel, streak, busy, alert)) timed(value, 0f, REST_FADE)
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoPressed = false
        touches++
    }

    LaunchedEffect(stage == Stage.Loading) {
        if (stage != Stage.Loading) {
            spin.floatValue = 0f
            return@LaunchedEffect
        }
        val origin = withFrameNanos { it }
        while (true) spin.floatValue = (((withFrameNanos { it } - origin) / 1e9f) % SPIN) / SPIN
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            until(TAP_AT)
            enable()
            until(DIALOG_AT)
            askPrivate()
            until(CONTINUE_PRESS_AT)
            autoPressed = true
            until(CONFIRM_AT)
            makePrivate()
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Backdrop)
            // Sees every touch without taking it from the buttons.
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touched()
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val width = maxWidth.value
        val height = maxHeight.value
        val top = insets.calculateTopPadding().value
        val bottom = max(insets.calculateBottomPadding().value, 16f)
        val sheetTop = top + SHEET_GAP
        val sheetHeight = height - sheetTop
        val artClosed = min(ART_CLOSED_MAX, width - 2 * (MARGIN + 16f))
        // Centre the hero in the room between the close button and the button at the bottom.
        val artTopClosed = max(64f, min(ART_TOP_CLOSED, (sheetHeight - artClosed - 260f) / 2f))
        val titleTopClosed = artTopClosed + artClosed + TITLE_GAP
        val titleTopOpen = ART_TOP_OPEN + ART_OPEN + TITLE_GAP_OPEN
        val panelTop = titleTopOpen + (TITLE_HEIGHT + 2f + CAPTION_HEIGHT) * TITLE_SCALE_OPEN + PANEL_GAP
        val density = LocalDensity.current.density
        val glow = remember { SoftRoundRect(Glow, 36f) }
        val contact = remember { SoftRoundRect(Contact, 5f) }
        val isOpen = stage == Stage.Open

        // The page the sheet came from, peeking out above it.
        Box(
            Modifier
                .offset(x = 18.dp, y = top.dp)
                .size((width - 36f).dp, 40.dp)
                .background(Behind, RoundedCornerShape(SHEET_RADIUS.dp)),
        )

        Box(
            Modifier
                .offset(y = sheetTop.dp)
                .size(width.dp, sheetHeight.dp)
                .clip(RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp))
                .background(Page),
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height((sheetHeight * 0.6f).dp)
                    .background(Brush.verticalGradient(listOf(GlowPage, GlowPage.copy(alpha = 0f)))),
            )

            Box(
                Modifier
                    .offset(x = (width - 16f - CLOSE).dp, y = 16.dp)
                    .size(CLOSE.dp)
                    .background(Well, RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Filled.Close, null, Modifier.size(20.dp), tint = Ink)
            }

            // Under the artwork, so the rows rise from behind the shrinking cover.
            Column(Modifier.offset(y = panelTop.dp).padding(horizontal = MARGIN.dp).fillMaxWidth()) {
                Reveal(0, panel) {
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Action(Icons.Outlined.ContentCopy, "Copy link", Modifier.weight(1f))
                        Action(Icons.Outlined.IosShare, "Share", Modifier.weight(1f))
                    }
                }
                Reveal(1, panel) {
                    Row(
                        Modifier.fillMaxWidth().padding(top = 18.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Refresh, null, Modifier.size(15.dp), tint = Body)
                        Label("Reset link", 14f, 20f, FontWeight.Normal, Body, family = FontFamily.Monospace)
                    }
                }
                Reveal(2, panel) {
                    Label(
                        "Last generated on $generatedOn", 12f, 16f, FontWeight.Normal, Faint,
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    )
                }
                Reveal(3, panel) {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .padding(top = 14.dp)
                            .clickable(enabled = isOpen, role = Role.Button, onClick = ::askPrivate),
                        horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(13.dp), tint = Danger)
                        Label("Make project private", 14f, 20f, FontWeight.Normal, Danger, family = FontFamily.Monospace)
                    }
                }
                Reveal(4, panel) {
                    Label(
                        "Permissions", 12f, 16f, FontWeight.Normal, Faint, TextAlign.Start,
                        Modifier.fillMaxWidth().padding(top = 24.dp, bottom = 2.dp), FontFamily.Monospace, letterSpacing = 0.4f,
                    )
                }
                permissions.forEachIndexed { index, permission ->
                    Reveal(5 + index, panel) {
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .height(ROW_HEIGHT.dp)
                                .drawBehind {
                                    if (index > 0) drawRect(Line, size = Size(size.width, HAIRLINE * this.density))
                                },
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Box(Modifier.size(36.dp).background(Well, RoundedCornerShape(10.dp)), contentAlignment = Alignment.Center) {
                                Icon(permission.icon, null, Modifier.size(18.dp), tint = Body)
                            }
                            Column(Modifier.weight(1f)) {
                                Label(permission.title, 15f, 20f, FontWeight.SemiBold, Ink, TextAlign.Start, letterSpacing = -0.2f)
                                Label(permission.caption, 13f, 17f, FontWeight.Normal, Mute, TextAlign.Start, maxLines = 2)
                            }
                            Toggle(states()[index], enabled = isOpen) { toggle(index) }
                        }
                    }
                }
            }

            // The artwork: a pink-to-violet square lit from the upper left, rays of light wiped in
            // from the left, a flash as they land, a fine light edge and a coloured glow below.
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) {
                    val p = sheet.value
                    val s = lerp(artClosed, ART_OPEN, p)
                    val x = (width - s) / 2f
                    val y = lerp(artTopClosed, ART_TOP_OPEN, p)
                    val radius = lerp(ART_RADIUS_CLOSED, ART_RADIUS_OPEN, p)
                    // Box shadows: a blur of B is a Gaussian of B / 2, and the spread grows the shape.
                    drawIntoCanvas {
                        glow.draw(it.nativeCanvas, x - 6f, y + 24f - 6f, s + 12f, s + 12f, radius + 6f)
                        contact.draw(it.nativeCanvas, x, y + 4f, s, s, radius)
                    }
                    val shape = Path().apply { addRoundRect(RoundRect(x, y, x + s, y + s, CornerRadius(radius))) }
                    clipPath(shape) {
                        drawRect(Brush.linearGradient(*ArtStops, start = Offset(x, y), end = Offset(x + s, y + s)), Offset(x, y), Size(s, s))
                        drawRect(
                            Brush.linearGradient(
                                listOf(Color.White.copy(alpha = 0.42f), Color.White.copy(alpha = 0f)),
                                start = Offset(x, y), end = Offset(x + s * 0.55f, y + s * 0.6f),
                            ),
                            Offset(x, y), Size(s, s),
                        )
                        drawRect(
                            Brush.linearGradient(
                                listOf(Shade.copy(alpha = 0f), Shade.copy(alpha = 0.22f)),
                                start = Offset(x + s * 0.45f, y + s * 0.45f), end = Offset(x + s, y + s),
                            ),
                            Offset(x, y), Size(s, s),
                        )
                        val wipe = streak.value * 116f
                        if (wipe > 0f) {
                            val unit = s / 100f
                            translate(x, y) {
                                scale(unit, unit, pivot = Offset.Zero) {
                                    clipRect(-8f, 0f, -8f + wipe, 100f) {
                                        for (r in Rays) {
                                            val from = Offset(r[0], r[1])
                                            val to = Offset(r[2], r[3])
                                            val brush = Brush.linearGradient(
                                                0f to Color.White.copy(alpha = 0f),
                                                0.62f to Color.White.copy(alpha = r[5]),
                                                1f to Color.White.copy(alpha = 0f),
                                                start = from, end = to,
                                            )
                                            drawLine(brush, from, to, strokeWidth = r[4], cap = StrokeCap.Round)
                                        }
                                        for (g in Glints) drawPath(glint(g[0], g[1], g[2]), Color.White.copy(alpha = 0.9f))
                                    }
                                }
                            }
                        }
                        drawRect(Color.White.copy(alpha = 0.32f * sin(PI.toFloat() * streak.value)), Offset(x, y), Size(s, s))
                    }
                    val edge = HAIRLINE * 2f
                    drawRoundRect(
                        Color.White.copy(alpha = 0.28f),
                        Offset(x + edge / 2f, y + edge / 2f), Size(s - edge, s - edge), CornerRadius(radius - edge / 2f),
                        style = Stroke(edge),
                    )
                }
            }

            Column(
                Modifier
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = sheet.value
                        val s = lerp(1f, TITLE_SCALE_OPEN, p)
                        transformOrigin = TransformOrigin(0.5f, 0f)
                        scaleX = s
                        scaleY = s
                        translationY = lerp(titleTopClosed, titleTopOpen, p) * this.density
                    },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Label(title, 28f, TITLE_HEIGHT, FontWeight.Bold, Ink, letterSpacing = -0.6f)
                Row(Modifier.height(CAPTION_HEIGHT.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(16.dp).background(Avatar, CircleShape), contentAlignment = Alignment.Center) {
                        Icon(Icons.Filled.Lock, null, Modifier.size(9.dp), tint = Color.White)
                    }
                    Label(caption, 15f, 20f, FontWeight.Medium, Mute)
                }
            }

            Column(
                Modifier
                    .offset(y = (sheetHeight - bottom - DOCK_HEIGHT).dp)
                    .padding(horizontal = MARGIN.dp)
                    .fillMaxWidth()
                    .graphicsLayer {
                        val p = min(1f, sheet.value * 2f)
                        alpha = 1f - p
                        translationY = p * ROW_RISE * this.density
                    },
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                val buttonShadow = remember { SoftRoundRect(Ink.copy(alpha = 0.18f), 8f) }
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(BUTTON_HEIGHT.dp)
                        .drawBehind {
                            val r = BUTTON_HEIGHT / 2f * this.density
                            drawIntoCanvas { buttonShadow.draw(it.nativeCanvas, 0f, 6f * this.density, size.width, size.height, r) }
                            drawRoundRect(buttonColor(min(1f, max(0f, sheet.value / 0.4f))), cornerRadius = CornerRadius(r))
                        }
                        .clip(RoundedCornerShape(50))
                        .clickable(enabled = stage == Stage.Closed, role = Role.Button, onClick = ::enable),
                    contentAlignment = Alignment.Center,
                ) {
                    Row(
                        Modifier.graphicsLayer { alpha = 1f - busy.value },
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Filled.Link, null, Modifier.size(18.dp), tint = Color.White)
                        Label(enableLabel, 16f, 20f, FontWeight.SemiBold, Color.White, letterSpacing = -0.2f)
                    }
                    // A ring with its top quarter lit, like a border whose top edge is white.
                    Canvas(Modifier.size(20.dp).graphicsLayer { alpha = busy.value }) {
                        val stroke = Stroke(2.dp.toPx())
                        val inset = stroke.width / 2f
                        val box = Size(size.width - stroke.width, size.height - stroke.width)
                        drawArc(Color.White.copy(alpha = 0.25f), 0f, 360f, false, Offset(inset, inset), box, style = stroke)
                        rotate(spin.floatValue * 360f) {
                            drawArc(Color.White, -135f, 90f, false, Offset(inset, inset), box, style = stroke)
                        }
                    }
                }
                Label(
                    footnote, 12f, 16f, FontWeight.Normal, Faint,
                    modifier = Modifier.fillMaxWidth().height(FOOTNOTE_HEIGHT.dp).padding(horizontal = 12.dp),
                )
            }
        }

        // The alert. It sits over everything and takes the touches while it is up.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(dialog) {
                    if (!dialog) return@pointerInput
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        down.consume()
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = alert.value }.background(Scrim))
            val far = remember { SoftRoundRect(Color.Black.copy(alpha = 0.22f), 24f) }
            val near = remember { SoftRoundRect(Color.Black.copy(alpha = 0.08f), 3f) }
            Column(
                Modifier
                    .width(DIALOG_WIDTH.dp)
                    .graphicsLayer {
                        alpha = min(1f, alert.value * 1.4f)
                        scaleX = 1.12f - 0.12f * alert.value
                        scaleY = scaleX
                    }
                    .drawBehind {
                        val r = 16f * this.density
                        drawIntoCanvas {
                            far.draw(it.nativeCanvas, 0f, 20f * this.density, size.width, size.height, r)
                            near.draw(it.nativeCanvas, 0f, 2f * this.density, size.width, size.height, r)
                        }
                    }
                    .clip(RoundedCornerShape(16.dp))
                    .background(DialogFill),
            ) {
                Column(
                    Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    Label("Make project private?", 17f, 22f, FontWeight.SemiBold, Ink, letterSpacing = -0.3f)
                    Label("This will disable the link and remove everyone from the project.", 13f, 18f, FontWeight.Normal, Body)
                }
                Box(Modifier.fillMaxWidth().height(HAIRLINE.dp).background(DialogLine))
                Row(Modifier.fillMaxWidth().height(46.dp)) {
                    DialogButton("Cancel", Accent, FontWeight.Normal, held = false, enabled = dialog, onClick = ::closeDialog, modifier = Modifier.weight(1f))
                    Box(Modifier.width(HAIRLINE.dp).fillMaxHeight().background(DialogLine))
                    DialogButton("Continue", Danger, FontWeight.SemiBold, held = autoPressed, enabled = dialog, onClick = ::makePrivate, modifier = Modifier.weight(1f))
                }
            }
        }
    }
}

/** A row of the panel: fades in and rises 40 pt, each one 60 ms after the one above. */
@Composable
private fun Reveal(index: Int, panel: Animatable<Float, *>, content: @Composable () -> Unit) {
    Box(
        Modifier.graphicsLayer {
            val t = min(1f, max(0f, (panel.value * PANEL_IN - index * ROW_STAGGER) / ROW_IN))
            alpha = t
            translationY = (1f - t) * ROW_RISE * density
        },
    ) { content() }
}

@Composable
private fun Action(icon: ImageVector, label: String, modifier: Modifier) {
    Column(
        modifier.height(48.dp).background(Well, RoundedCornerShape(14.dp)),
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null, Modifier.size(18.dp), tint = Ink)
        Label(label, 13f, 16f, FontWeight.SemiBold, Ink)
    }
}

@Composable
private fun Toggle(on: Boolean, enabled: Boolean, onToggle: () -> Unit) {
    val knob by animateDpAsState(
        if (on) (SWITCH_WIDTH - SWITCH_HEIGHT).dp else 0.dp, tween(200, easing = CssEase), label = "knob",
    )
    Box(
        Modifier
            .size(SWITCH_WIDTH.dp, SWITCH_HEIGHT.dp)
            .clip(RoundedCornerShape(50))
            .background(if (on) On else Off)
            .toggleable(on, enabled = enabled, role = Role.Switch, onValueChange = { onToggle() })
            .padding(2.dp),
    ) {
        Box(
            Modifier
                .offset(x = knob)
                .size((SWITCH_HEIGHT - 4f).dp)
                .shadow(3.dp, CircleShape)
                .background(Color.White, CircleShape),
        )
    }
}

@Composable
private fun DialogButton(text: String, color: Color, weight: FontWeight, held: Boolean, enabled: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    Box(
        modifier
            .fillMaxHeight()
            .background(Color.Black.copy(alpha = if (held || pressed) 0.08f else 0f))
            .clickable(source, null, enabled = enabled, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) { Label(text, 17f, 22f, weight, color) }
}

@Composable
private fun Label(
    text: String,
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    align: TextAlign = TextAlign.Center,
    modifier: Modifier = Modifier,
    family: FontFamily = FontFamily.Default,
    letterSpacing: Float = 0f,
    maxLines: Int = Int.MAX_VALUE,
) = BasicText(
    text,
    modifier,
    TextStyle(
        color = color,
        fontSize = size.sp,
        lineHeight = lineHeight.sp,
        fontWeight = weight,
        fontFamily = family,
        letterSpacing = letterSpacing.sp,
        textAlign = align,
        platformStyle = PlatformTextStyle(includeFontPadding = false),
        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    ),
    maxLines = maxLines,
    overflow = TextOverflow.Ellipsis,
)

/** The sheet sits on a black screen, so the status bar icons go light while the block is on screen. */
@Composable
private fun StageCardSheetDarkBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val previous = controller.isAppearanceLightStatusBars
        controller.isAppearanceLightStatusBars = false
        onDispose { controller.isAppearanceLightStatusBars = previous }
    }
}
