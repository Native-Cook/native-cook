package com.ngocdevv.nativecook.blocks.modals.growdeletedialog

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseInQuad
import androidx.compose.animation.core.EaseOutCubic
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Article
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Schedule
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

val GrowDeleteDialogFiles = listOf(
    FileItem("Project brief.pdf", "2.4 MB", Color(0xFFFDE8E8), Icons.Filled.Description, Color(0xFFE5484D), "Edited 2h ago"),
    FileItem(
        "Team offsite.png", "5.1 MB", Color(0xFFE3ECFD), Icons.Filled.Image, Color(0xFF3B6FE0), "Added yesterday",
        "https://images.unsplash.com/photo-1522202176988-66273c2fd55f?auto=format&fit=crop&w=140&h=140&q=80",
    ),
    FileItem("Budget 2026.xlsx", "860 KB", Color(0xFFE3F6EA), Icons.Filled.GridView, Color(0xFF22A35A), "Shared with 4"),
    FileItem("Meeting notes.txt", "12 KB", Color(0xFFFFF2D9), Icons.AutoMirrored.Filled.Article, Color(0xFFD98A00), "Edited Monday"),
)

// One loop, in seconds: the trash of the second row is pressed, the dialog grows out of it, the
// icon shakes, Confirm is pressed, the dialog turns into its success version, closes, and the row
// collapses; at the end the row returns.
const val LOOP = 9f
private const val TARGET_ROW = 1
private const val PRESS_AT = 0.8f
private const val OPEN_AT = 1.1f
private const val SHAKE_AT = 2.6f
private const val CONFIRM_PRESS_AT = 3.6f
private const val CONFIRM_AT = 3.8f
private const val CLOSE_AT = 6.2f
private const val DELETE_AT = 6.4f
private const val RETURN_AT = 8.2f

// After a touch the loop waits this long, then starts again from the list.
private const val USER_HOLD = 3f

// Springs of 500 ms (damping ratio 0.82) and 420 ms (0.9): stiffness (2π / duration)².
private const val GROW_DAMPING = 0.82f
private const val GROW_STIFFNESS = 158f
private const val SHRINK_DAMPING = 0.9f
private const val SHRINK_STIFFNESS = 224f
private const val SHRINK_SETTLE_MS = 600
private const val CONTENT_AFTER = 250
private const val CONTENT_MS = 700
private const val SWAP_MS = 400
private const val DRAW_MS = 400
private const val RIPPLE_MS = 600
private const val CLOSE_MS = 500
private const val FADE_MS = 200
private const val SHAKE_DEGREES = 8f
private const val SHAKE_MS = 70
private const val CLOSE_SCALE = 0.9f
private const val SCRIM_OPACITY = 0.4f

private const val DIALOG_WIDTH = 320f
private const val DIALOG_HEIGHT = 248f
private const val RADIUS_FROM = 11f
private const val RADIUS_TO = 28f
private const val TILE = 58f
private const val TILE_RADIUS = 18
private const val BUTTON_HEIGHT = 50f
private const val BUTTON_GAP = 12f
private const val SIDE = 24f
private const val FULL_WIDTH = DIALOG_WIDTH - SIDE * 2
private const val BUTTON_WIDTH = (FULL_WIDTH - BUTTON_GAP) / 2

private val Red = TrashInk
private val RedTile = Color(0xFFFDECED)
private val Green = Color(0xFF52B992)
private val GreenInk = Color(0xFF4CB890)
private val GreenTile = Color(0xFFE8F8F1)
private val PageColor = Color(0xFFF5F5F7)
private val Ink = Color(0xFF111318)
private val Mute = Color(0xFF71757E)
private val Blue = Color(0xFF3B6FE0)
private val TabGrey = Color(0xFF9A9DA5)
private val Scrim = Color(0xFF0B0D12)
private val CardShadow = Color(0xFF111318)

private class Share(val label: String, val share: Float, val color: Color)

// The storage card at the top: what each kind of file takes, as a share of the bar.
private val Storage = listOf(
    Share("Documents", 0.34f, Color(0xFFE5484D)),
    Share("Photos", 0.26f, Color(0xFF3B6FE0)),
    Share("Sheets", 0.12f, Color(0xFF22A35A)),
    Share("Other", 0.08f, Color(0xFFF0A100)),
)

private class Tab(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("Recents", Icons.Rounded.Schedule),
    Tab("Shared", Icons.Outlined.People),
    Tab("Browse", Icons.Outlined.Folder),
)

// The check inside the success ring, in a 28 point box.
private val Check = Path().apply {
    moveTo(9.5f, 15.5f); lineTo(13.2f, 19f); lineTo(20.5f, 11f)
}

// The dialog's two shadows: a tight one under it and a wide one further down.
private val DialogShadowNear = SoftRoundRect(Color(0xFF0B0D12).copy(alpha = 0.1f), 5f)
private val DialogShadowFar = SoftRoundRect(Color(0xFF0B0D12).copy(alpha = 0.28f), 28f)
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

internal fun gridText(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    align: TextAlign = TextAlign.Unspecified,
    tracking: Float = 0f,
) = TextStyle(
    color = color,
    letterSpacing = tracking.sp,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun stage(value: Float, start: Float, length: Float) = ((value - start) / length).coerceIn(0f, 1f)

private fun backOut(p: Float) = 1f + 2.70158f * (p - 1f).pow(3) + 1.70158f * (p - 1f).pow(2)

/** A straight mix in RGB, so a fading fill keeps its colour. */
private fun mix(a: Color, b: Color, t: Float) = Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t)

/** A number that moves on its own curve; a new target starts from wherever the number is. */
internal class Channel(initial: Float, private val scope: CoroutineScope) {
    private val animatable = Animatable(initial)
    private var job: Job? = null
    val value get() = animatable.value

    fun go(delayMs: Int = 0, run: suspend Animatable<Float, AnimationVector1D>.() -> Unit) {
        job?.cancel()
        job = scope.launch {
            if (delayMs > 0) delay(delayMs.toLong())
            animatable.run()
        }
    }

    fun set(value: Float) {
        job?.cancel()
        job = scope.launch(start = CoroutineStart.UNDISPATCHED) { animatable.snapTo(value) }
    }
}

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private class Step(val at: Float, val run: () -> Unit)

/**
 * A list of files. A trash button grows into a confirmation dialog: the dialog starts as the
 * button's own rounded square, measured on screen, and spreads to the centre while its content pops
 * in. Confirm turns the icon into a check that draws itself and the two buttons into one; closing
 * the dialog collapses the deleted row. Cancel folds the dialog back into the button it came from.
 *
 * @param autoPlay Presses a trash button by itself, confirms, and brings the file back, in a loop.
 * @param onDelete Called when the file's deletion is confirmed and the dialog is closed.
 */
@Composable
fun GrowDeleteDialog(
    autoPlay: Boolean = false,
    files: List<FileItem> = GrowDeleteDialogFiles,
    title: String = "Files",
    deleteTitle: String = "Delete",
    deleteMessage: String = "Are you sure you want to delete?",
    successTitle: String = "Success",
    successMessage: String = "Action is done successfully!",
    cancelLabel: String = "Cancel",
    confirmLabel: String = "Confirm",
    onDelete: ((FileItem) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val given by rememberUpdatedState(files)
    val deleted by rememberUpdatedState(onDelete)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var removed by remember { mutableStateOf(listOf<Int>()) }
    var target by remember { mutableStateOf<Int?>(null) }
    var pressed by remember { mutableStateOf<Int?>(null) }
    var confirmPressed by remember { mutableStateOf(false) }
    var succeeded by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    var phase by remember { mutableIntStateOf(0) }
    // Where each trash button sits in the window, in px.
    val trashes = remember { mutableMapOf<Int, Offset>() }

    // Where the dialog grows from: the trash button's place, in px.
    val originX = remember { mutableFloatStateOf(0f) }
    val originY = remember { mutableFloatStateOf(0f) }
    val grow = remember { Channel(0f, scope) }
    val content = remember { Channel(0f, scope) }
    val done = remember { Channel(0f, scope) }
    val draw = remember { Channel(0f, scope) }
    val ripple = remember { Channel(0f, scope) }
    val shake = remember { Channel(0f, scope) }
    val out = remember { Channel(0f, scope) }

    fun open(index: Int) {
        val button = trashes[index] ?: return
        phase++
        originX.floatValue = button.x
        originY.floatValue = button.y
        target = index
        succeeded = false
        out.set(0f)
        done.set(0f)
        draw.set(0f)
        ripple.set(0f)
        if (reduced) {
            grow.go { animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) }
            content.go { animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) }
            return
        }
        grow.go { animateTo(1f, spring(GROW_DAMPING, GROW_STIFFNESS)) }
        content.go(CONTENT_AFTER) { animateTo(1f, tween(CONTENT_MS, easing = LinearEasing)) }
    }

    /** The dialog is closed: the next one starts from the confirmation. */
    fun finish() {
        grow.set(0f)
        content.set(0f)
        out.set(0f)
        done.set(0f)
        draw.set(0f)
        target = null
        succeeded = false
    }

    fun finishAfter(ms: Int) {
        phase++
        val token = phase
        scope.launch {
            delay(ms.toLong())
            if (token == phase) finish()
        }
    }

    fun fold() {
        content.go { animateTo(0f, tween(150, easing = EaseInOutQuad)) }
        if (reduced) {
            grow.go(100) { animateTo(0f, tween(FADE_MS, easing = EaseInOutQuad)) }
            finishAfter(100 + FADE_MS)
        } else {
            grow.go(100) { animateTo(0f, spring(SHRINK_DAMPING, SHRINK_STIFFNESS)) }
            finishAfter(100 + SHRINK_SETTLE_MS)
        }
    }

    fun confirm() {
        if (succeeded || target == null) return
        succeeded = true
        done.go { animateTo(1f, tween(SWAP_MS, easing = EaseInOutQuad)) }
        draw.go((SWAP_MS * 0.4f).roundToInt()) { animateTo(1f, tween(DRAW_MS, easing = EaseOutCubic)) }
        if (reduced) return
        ripple.set(0f)
        ripple.go { animateTo(1f, tween(RIPPLE_MS, easing = EaseOutQuad)) }
    }

    fun removeFile(index: Int) {
        if (index !in removed) removed = removed + index
        deleted?.invoke(given[index])
    }

    /** Close the success dialog; the file it was about goes. */
    fun close(deleteNow: Boolean) {
        val index = target
        val ms = if (reduced) FADE_MS else CLOSE_MS
        out.go { animateTo(1f, tween(ms, easing = EaseInQuad)) }
        finishAfter(ms)
        if (index != null && deleteNow) removeFile(index)
    }

    fun shakeIcon() {
        if (reduced) return
        shake.go {
            animateTo(SHAKE_DEGREES, tween(SHAKE_MS, easing = EaseInOutQuad))
            for (point in listOf(-SHAKE_DEGREES, SHAKE_DEGREES, -SHAKE_DEGREES, SHAKE_DEGREES)) {
                animateTo(point, tween(SHAKE_MS * 2, easing = EaseInOutQuad))
            }
            animateTo(0f, tween(SHAKE_MS, easing = EaseInOutQuad))
        }
    }

    fun trash(index: Int) {
        if (target != null) return
        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
        open(index)
    }

    fun confirmByHand() {
        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
        if (!succeeded) confirm() else close(true)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            pressed = null
            confirmPressed = false
            removed = emptyList()
            if (target != null) close(false)
            delay(800)
        }
        val steps = listOf(
            Step(PRESS_AT) { pressed = TARGET_ROW },
            Step(OPEN_AT) {
                pressed = null
                open(TARGET_ROW)
            },
            Step(SHAKE_AT) { shakeIcon() },
            Step(CONFIRM_PRESS_AT) { confirmPressed = true },
            Step(CONFIRM_AT) {
                confirmPressed = false
                confirm()
            },
            Step(CLOSE_AT) { close(false) },
            Step(DELETE_AT) { removeFile(TARGET_ROW) },
            Step(RETURN_AT) { removed = emptyList() },
        )
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for (step in steps) {
                until(step.at)
                step.run()
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The status bar's icons are dark over the light page.
    val window = view.context.activity()?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }

    val byHand = !autoPlay || touches > 0

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(PageColor)
            .pointerInput(autoPlay) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    if (autoPlay) touches++
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                }
            },
    ) {
        val insetsTop = WindowInsets.systemBars.asPaddingValues().calculateTopPadding().value
        val screenWidth = maxWidth.value
        val screenHeight = maxHeight.value
        val cx = (screenWidth - DIALOG_WIDTH) / 2f
        val cy = max(insetsTop + 20f, (screenHeight - DIALOG_HEIGHT) / 2f)

        Column(
            Modifier
                .fillMaxSize()
                .then(if (target != null) Modifier.clearAndSetSemantics {} else Modifier)
                .padding(start = 20.dp, end = 20.dp, top = (insetsTop + 8f).dp),
        ) {
            Row(
                Modifier.fillMaxWidth().height(48.dp),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText(title, style = gridText(34f, 40f, FontWeight.ExtraBold, Ink, tracking = -1f))
                if (byHand && removed.isNotEmpty()) {
                    BasicText(
                        "Restore files",
                        Modifier.clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { removed = emptyList() },
                        style = gridText(15f, 20f, FontWeight.SemiBold, Red),
                    )
                } else {
                    Box(
                        Modifier
                            .size(40.dp)
                            .dropShadow(CircleShape, Shadow(4.dp, CardShadow.copy(alpha = 0.08f), offset = DpOffset(0.dp, 2.dp)))
                            .background(Color.White, CircleShape),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(26.dp), tint = Ink)
                    }
                }
            }
            Box(Modifier.height(16.dp))
            StorageCard()
            Row(
                Modifier.padding(top = 24.dp, bottom = 12.dp).fillMaxWidth(),
                horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicText("Recent files", style = gridText(20f, 25f, FontWeight.ExtraBold, Ink, tracking = -0.4f))
                BasicText("See all", style = gridText(15f, 20f, FontWeight.SemiBold, Blue))
            }
            files.forEachIndexed { index, file ->
                FileRow(
                    file = file,
                    removed = index in removed,
                    pressed = pressed == index,
                    onTrashPlaced = { trashes[index] = it },
                    onTrash = { trash(index) },
                )
            }
        }

        TabBar(
            WindowInsets.systemBars.asPaddingValues().calculateBottomPadding(),
            Modifier.align(Alignment.BottomCenter).then(if (target != null) Modifier.clearAndSetSemantics {} else Modifier),
        )

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = SCRIM_OPACITY * (grow.value * 1.5f).coerceIn(0f, 1f) * (1f - out.value) }
                .background(Scrim),
        )

        if (target != null) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(remember { MutableInteractionSource() }, indication = null) { if (succeeded) close(true) else fold() }
                    .semantics { contentDescription = "Dismiss" },
            )
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val g = grow.value
                        val x: Float
                        val y: Float
                        val w: Float
                        val h: Float
                        if (reduced) {
                            x = cx; y = cy; w = DIALOG_WIDTH; h = DIALOG_HEIGHT
                        } else {
                            x = originX.floatValue / density + (cx - originX.floatValue / density) * g
                            y = originY.floatValue / density + (cy - originY.floatValue / density) * g
                            w = TRASH_SIZE + (DIALOG_WIDTH - TRASH_SIZE) * g
                            h = TRASH_SIZE + (DIALOG_HEIGHT - TRASH_SIZE) * g
                        }
                        val p = measurable.measure(Constraints.fixed((w * density).roundToInt(), (h * density).roundToInt()))
                        layout(constraints.maxWidth, constraints.maxHeight) { p.place(IntOffset((x * density).roundToInt(), (y * density).roundToInt())) }
                    }
                    .graphicsLayer {
                        val g = grow.value
                        val e = out.value
                        if (reduced) {
                            alpha = g.coerceIn(0f, 1f) * (1f - e)
                        } else {
                            alpha = min(1f, max(0f, g) * 3f) * (1f - e)
                            val s = 1f - (1f - CLOSE_SCALE) * e
                            scaleX = s
                            scaleY = s
                        }
                    }
                    .drawBehind {
                        val g = grow.value
                        val radius = if (reduced) RADIUS_TO else RADIUS_FROM + (RADIUS_TO - RADIUS_FROM) * min(g, 1.1f)
                        scale(density, density, pivot = Offset.Zero) {
                            val w = size.width / density
                            val h = size.height / density
                            drawIntoCanvas {
                                DialogShadowNear.draw(it.nativeCanvas, 0f, 4f, w, h, radius)
                                DialogShadowFar.draw(it.nativeCanvas, 0f, 24f, w, h, radius)
                            }
                        }
                    }
                    .graphicsLayer {
                        val g = grow.value
                        val radius = if (reduced) RADIUS_TO else RADIUS_FROM + (RADIUS_TO - RADIUS_FROM) * min(g, 1.1f)
                        shape = RoundedCornerShape(radius.dp)
                        clip = true
                    }
                    .background(Color.White),
                contentAlignment = Alignment.Center,
            ) {
                // The content keeps its own size and sits in the middle of the growing frame.
                Dialog(
                    deleteTitle, deleteMessage, successTitle, successMessage, cancelLabel, confirmLabel,
                    succeeded, confirmPressed, content, done, draw, ripple, shake, reduced,
                    onFold = ::fold,
                    onConfirm = ::confirmByHand,
                )
            }
        }
    }
}

@Composable
private fun Dialog(
    deleteTitle: String,
    deleteMessage: String,
    successTitle: String,
    successMessage: String,
    cancelLabel: String,
    confirmLabel: String,
    succeeded: Boolean,
    confirmPressed: Boolean,
    content: Channel,
    done: Channel,
    draw: Channel,
    ripple: Channel,
    shake: Channel,
    reduced: Boolean,
    onFold: () -> Unit,
    onConfirm: () -> Unit,
) {
    val fade: androidx.compose.ui.graphics.GraphicsLayerScope.(Float, Float) -> Unit = { start, length ->
        val c = content.value
        alpha = if (reduced) (if (c > 0f) 1f else 0f) else stage(c, start, length)
        translationY = if (reduced) 0f else (1f - stage(c, start, length)) * 8f * density
    }

    Box(Modifier.requiredSize(DIALOG_WIDTH.dp, DIALOG_HEIGHT.dp)) {
        Box(Modifier.offset(((DIALOG_WIDTH - TILE) / 2f).dp, 28.dp).size(TILE.dp)) {
            Box(
                Modifier
                    .size(TILE.dp)
                    .graphicsLayer {
                        val r = ripple.value
                        alpha = 0.45f * (1f - r) * (if (r > 0f) 1f else 0f)
                        scaleX = 1f + r * 0.9f
                        scaleY = 1f + r * 0.9f
                    }
                    .border(2.dp, GreenInk, RoundedCornerShape(TILE_RADIUS.dp)),
            )
            Box(
                Modifier
                    .size(TILE.dp)
                    .graphicsLayer {
                        val p = if (reduced) (if (content.value > 0f) 1f else 0f) else backOut(stage(content.value, 0f, 0.4f))
                        alpha = min(1f, p * 2f)
                        scaleX = max(p, 0f)
                        scaleY = max(p, 0f)
                        rotationZ = shake.value
                    }
                    .drawBehind {
                        drawRoundRect(mix(RedTile, GreenTile, done.value), Offset.Zero, size, CornerRadius(TILE_RADIUS.dp.toPx()))
                    },
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Delete, null,
                    Modifier.size(31.dp).graphicsLayer {
                        alpha = 1f - done.value
                        scaleX = 1f - done.value * 0.4f
                        scaleY = 1f - done.value * 0.4f
                    },
                    tint = Red,
                )
                // The check inside the success ring, in a 30 point box.
                Canvas(Modifier.size(30.dp).graphicsLayer { alpha = min(1f, done.value * 3f) }) {
                    scale(density, density, pivot = Offset.Zero) {
                        drawCircle(GreenInk, 12.5f, Offset(15f, 15f), style = Stroke(2.2f))
                        val measure = PathMeasure().apply { setPath(Check, false) }
                        val shown = Path()
                        measure.getSegment(0f, measure.length * draw.value.coerceIn(0f, 1f), shown, true)
                        drawPath(shown, GreenInk, style = Stroke(2.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
        }

        Swapping(deleteTitle, successTitle, 21f, 26f, FontWeight.Bold, Ink, done, Modifier.offset(SIDE.dp, 102.dp).graphicsLayer { fade(0.09f, 0.4f) }, -0.4f)
        Swapping(deleteMessage, successMessage, 15f, 21f, FontWeight.Normal, Mute, done, Modifier.offset(SIDE.dp, 134.dp).graphicsLayer { fade(0.18f, 0.4f) })

        Box(Modifier.offset(SIDE.dp, 176.dp).size(FULL_WIDTH.dp, BUTTON_HEIGHT.dp).graphicsLayer { fade(0.27f, 0.4f) }) {
            Box(
                Modifier
                    .animatedWidth { BUTTON_WIDTH * (1f - done.value) }
                    .height(BUTTON_HEIGHT.dp)
                    .graphicsLayer { alpha = max(0f, 1f - done.value * 1.5f) }
                    .clip(RoundedCornerShape(15.dp))
                    .background(Color(0xFFF2F3F5))
                    .clickable(remember { MutableInteractionSource() }, enabled = !succeeded, indication = null, role = Role.Button, onClick = onFold),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(cancelLabel, style = gridText(16f, 21f, FontWeight.SemiBold, Ink, tracking = -0.2f), maxLines = 1, softWrap = false)
            }
            Box(
                Modifier
                    .layout { measurable, constraints ->
                        val w = ((BUTTON_WIDTH + (FULL_WIDTH - BUTTON_WIDTH) * done.value) * density).roundToInt()
                        val p = measurable.measure(Constraints.fixed(w, (BUTTON_HEIGHT * density).roundToInt()))
                        layout(constraints.maxWidth, constraints.maxHeight) { p.place(((BUTTON_WIDTH + BUTTON_GAP) * (1f - done.value) * density).roundToInt(), 0) }
                    }
                    .graphicsLayer {
                        val s = if (confirmPressed) 0.96f else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .clip(RoundedCornerShape(15.dp))
                    .drawBehind { drawRect(mix(Red, Green, done.value)) }
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onConfirm),
                contentAlignment = Alignment.Center,
            ) {
                BasicText(confirmLabel, style = gridText(16f, 21f, FontWeight.SemiBold, Color.White, tracking = -0.2f), maxLines = 1, softWrap = false)
            }
        }

        Box(
            Modifier
                .offset((DIALOG_WIDTH - 14f - 26f).dp, 14.dp)
                .size(26.dp)
                .graphicsLayer { alpha = 1f - done.value }
                .clip(CircleShape)
                .background(Color(0xFF5F636B))
                .clickable(remember { MutableInteractionSource() }, enabled = !succeeded, indication = null, role = Role.Button, onClick = onFold)
                .semantics { contentDescription = "Close" },
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Filled.Close, null, Modifier.size(16.dp), tint = Color.White)
        }
    }
}

/** Two lines of text one over the other: the confirmation turns into the success. */
@Composable
private fun Swapping(
    first: String,
    second: String,
    size: Float,
    height: Float,
    weight: FontWeight,
    ink: Color,
    done: Channel,
    modifier: Modifier,
    tracking: Float = 0f,
) {
    Box(modifier.size(FULL_WIDTH.dp, height.dp), contentAlignment = Alignment.TopCenter) {
        BasicText(first, Modifier.graphicsLayer { alpha = 1f - done.value }, style = gridText(size, height, weight, ink, TextAlign.Center, tracking))
        BasicText(second, Modifier.graphicsLayer { alpha = done.value }, style = gridText(size, height, weight, ink, TextAlign.Center, tracking))
    }
}

/** Lays the content out [width] dp wide; read while measuring, so it animates without recomposing. */
private fun Modifier.animatedWidth(width: () -> Float) = layout { measurable, constraints ->
    val w = (max(0f, width()) * density).roundToInt()
    val p = measurable.measure(Constraints.fixed(w, constraints.maxHeight.coerceAtMost((BUTTON_HEIGHT * density).roundToInt())))
    layout(w, p.height) { p.place(0, 0) }
}

@Composable
private fun StorageCard() {
    val shape = RoundedCornerShape(22.dp)
    Column(
        Modifier
            .fillMaxWidth()
            .dropShadow(shape, Shadow(1.dp, CardShadow.copy(alpha = 0.04f), offset = DpOffset(0.dp, 1.dp)))
            .dropShadow(shape, Shadow(9.dp, CardShadow.copy(alpha = 0.05f), offset = DpOffset(0.dp, 6.dp)))
            .background(Color.White, shape)
            .padding(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                BasicText("Storage", style = gridText(13f, 17f, FontWeight.SemiBold, Mute))
                BasicText(
                    buildAnnotatedString {
                        append("12.8 GB ")
                        withStyle(SpanStyle(color = Mute, fontSize = 15.sp, fontWeight = FontWeight.Medium, letterSpacing = 0.sp)) { append("of 16 GB used") }
                    },
                    Modifier.padding(top = 2.dp),
                    style = gridText(22f, 28f, FontWeight.ExtraBold, Ink, tracking = -0.5f),
                )
            }
            Box(Modifier.size(36.dp).background(Color(0xFFE3ECFD), RoundedCornerShape(11.dp)), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Cloud, null, Modifier.size(22.dp), tint = Blue)
            }
        }
        Row(
            Modifier.padding(top = 14.dp).fillMaxWidth().height(8.dp).clip(CircleShape).background(Color(0xFFEEF0F3)),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
        ) {
            for (part in Storage) Box(Modifier.weight(part.share).fillMaxHeight().background(part.color))
            Box(Modifier.weight(1f - Storage.sumOf { it.share.toDouble() }.toFloat()))
        }
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(14.dp)) {
            for (part in Storage) {
                Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Box(Modifier.size(8.dp).background(part.color, CircleShape))
                    BasicText(part.label, style = gridText(12f, 16f, FontWeight.Medium, Mute))
                }
            }
        }
    }
}

@Composable
private fun TabBar(bottom: Dp, modifier: Modifier) {
    val hairline = Color(0x333C3C43)
    Row(
        modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.96f))
            .drawBehind { drawRect(hairline, size = size.copy(height = 1f)) }
            .padding(top = 8.dp, bottom = bottom),
    ) {
        Tabs.forEachIndexed { i, tab ->
            val tint = if (i == 0) Blue else TabGrey
            Column(
                Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(2.dp),
            ) {
                Icon(tab.icon, null, Modifier.size(24.dp), tint = tint)
                BasicText(tab.label, style = gridText(11f, 14f, FontWeight.SemiBold, tint))
            }
        }
    }
}
