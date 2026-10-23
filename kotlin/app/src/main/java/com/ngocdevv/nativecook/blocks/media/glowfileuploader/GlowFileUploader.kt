package com.ngocdevv.nativecook.blocks.media.glowfileuploader

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlin.math.sqrt

/**
 * A file to upload: its [name], and the share (0 to 1) at which its first upload fails, or null
 * when it succeeds. A retry always succeeds.
 */
@Immutable
data class UploadFile(val name: String, val failsAt: Float? = null)

private val DefaultFiles = listOf(
    UploadFile("Website_styles.pdf"),
    UploadFile("profile_styles.css", failsAt = 0.64f),
    UploadFile("brand_guide.fig"),
    UploadFile("hero_photo.png", failsAt = 0.40f),
)

private val Background = Color(0xFF0E1013)
private val TitleTop = Color(0xFF4C4F55)
private val TitleBottom = Color(0xFFD3D4D6)

private const val SIDE = 20f
private const val TITLE_TOP = 28f
// The title is sized by eye to the design, not by number: "Upload a file" spans about 273 dp, as in
// the design and the Swift build (SF at 50 pt with −1.75 tracking). Roboto is wider than SF, so the
// same width comes from 50 sp with no tracking here. Lines 54 apart and 16 to the zone, as in Swift,
// put the zone's top 152 dp below the title's top inset (28 + 2 × 54 + 16).
private const val TITLE_SIZE = 50f
private const val TITLE_LINE = 54f
private const val TITLE_TRACKING = 0f
private const val ZONE_GAP = 16f
private const val LIST_GAP = 24f

// The buttons: their bottom on the bottom safe area's edge (the navigation bar's top), or this far
// above the screen's bottom when the screen has no inset there; their height, corners, gap and label.
private const val BUTTONS_LIFT = 0f
private const val BUTTONS_FLOOR = 16f
private const val BUTTON_HEIGHT = 48f
private const val BUTTON_LABEL = 12.5f
private val ButtonShape = RoundedCornerShape(22.dp)
private const val BUTTON_GAP = 8f
private const val CANCEL_WIDTH = 93f
private val CancelFill = Color(0xFF191C20)
private val CancelBorder = Color(0xFF3A3D42)
private val CancelLabel = Color(0xFFE8E9EA)
private val ContinueOff = Color(0xFF202327)
private val ContinueOn = Color(0xFF2A2E33)
private val ContinueLabelOff = Color(0xFF5E6166)
private val ContinueLabelOn = Color(0xFFF4F4F5)
private val SheenLight = Color.White.copy(alpha = 0.12f)
private val UploadedCheck = Color(0xFF46C35A)
private const val LABEL_CHECK = 15f
private const val LABEL_CHECK_STROKE = 2f

/** The label's check path is drawn for a 16 dp box. */
private const val LABEL_CHECK_BOX = 16f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

/**
 * A file upload screen: a drop zone with a soft white glow on its inner edge, and a list of files
 * that upload with a ring and a fill, fail with a shake and a red pill, and are retried. Continue
 * turns on when every file is done.
 *
 * @param files The files Select files adds, in turn (three at most in the list).
 * @param autoPlay Adds two files (one fails and is retried) and continues by itself every [LOOP] seconds.
 * @param onContinue Called with the files' names when Continue is pressed.
 */
@Composable
fun GlowFileUploader(
    title: String = "Upload a file\nto start",
    heading: String = "Add files to upload",
    files: List<UploadFile> = DefaultFiles,
    autoPlay: Boolean = false,
    onContinue: ((List<String>) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    GlowFileUploaderSystemBars()
    val still = rememberReducedMotion()
    val view = LocalView.current
    // Read by the loop, the flows and the touch handlers, which outlive a recomposition.
    val latestFiles by rememberUpdatedState(files.ifEmpty { DefaultFiles })
    val continued by rememberUpdatedState(onContinue)

    // The clock in seconds since the block appeared (read only while drawing); the events made by
    // hand; the autoPlay's loop start, null while a touch holds it off.
    val epoch = remember { System.nanoTime() }
    val clock = remember { mutableDoubleStateOf(0.0) }
    var hand by remember { mutableStateOf(Events()) }
    var loopStart by remember { mutableStateOf<Double?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Keeps the clock running after a touch under reduced motion, until everything is still.
    var awake by remember { mutableStateOf(false) }
    val running = autoPlay || !still || awake

    fun time() = (System.nanoTime() - epoch) / 1e9

    fun eventsAt(now: Double): Events = (loopStart?.let { autoEvents(now, it, latestFiles) } ?: hand).visibleAt(now, still)

    // Changes only when something happens (a press, a row added, retried or gone), not every frame.
    val current = remember { derivedStateOf(structuralEqualityPolicy()) { eventsAt(clock.doubleValue) } }
    val events = remember { { current.value } }
    val selectOn by remember { derivedStateOf { current.value.selectOn(clock.doubleValue) } }
    val cancelOn by remember { derivedStateOf { current.value.cancelOn(clock.doubleValue) } }
    val continueOn by remember { derivedStateOf { current.value.continueTappable(clock.doubleValue) } }
    val busy by remember { derivedStateOf { current.value.busy(clock.doubleValue) } }
    val doneIds by remember { derivedStateOf { current.value.rows.filter { clock.doubleValue >= it.doneAt && it.doneAt < it.dismissAt }.map { it.id }.toSet() } }
    val failedIds by remember { derivedStateOf { current.value.rows.filter { clock.doubleValue >= it.failAt && it.failAt < it.dismissAt }.map { it.id }.toSet() } }

    /** A touch takes over from the autoPlay loop, keeping what is on screen; it starts again later. */
    fun takeOver() {
        val now = time()
        loopStart?.let {
            hand = autoEvents(now, it, latestFiles)
            loopStart = null
        }
        hand = hand.visibleAt(now, still)
        awake = true
        touches++
    }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    fun select() {
        takeOver()
        val now = time()
        if (!hand.selectOn(now)) return
        tap()
        hand = hand.add(now, latestFiles)
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) withFrameNanos { clock.doubleValue = (it - epoch) / 1e9 }
    }

    // A success or an error buzz when a file finishes or fails, only when the user is driving.
    LaunchedEffect(Unit) {
        var before = doneIds
        snapshotFlow { doneIds }.collect { now ->
            if (loopStart == null && (now - before).isNotEmpty()) {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK)
            }
            before = now
        }
    }
    LaunchedEffect(Unit) {
        var before = failedIds
        snapshotFlow { failedIds }.collect { now ->
            if (loopStart == null && (now - before).isNotEmpty()) {
                view.performHapticFeedback(if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.REJECT else HapticFeedbackConstants.LONG_PRESS)
            }
            before = now
        }
    }

    LaunchedEffect(autoPlay, touches) {
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            snapshotFlow { busy }.first { !it }
            if (!autoPlay) {
                // Let the clock stop under reduced motion once everything is still.
                awake = false
                return@LaunchedEffect
            }
            // Back to rest the way Cancel goes there, then the loop starts again.
            val now = time()
            val staying = hand.batch(now).size
            if (staying > 0) {
                hand = hand.dismiss(now)
                delay((((staying - 1) * LEAVE_STAGGER + COLLAPSE + 0.05) * 1000).toLong())
            }
        }
        if (!autoPlay) return@LaunchedEffect
        hand = Events()
        // The first loop starts with the clock, so the ambient breathing starts at its low point with it.
        loopStart = if (touches == 0) 0.0 else time()
    }

    // The presses: down when the finger goes down, up when it lifts (the action follows on click).
    val selectTouch = remember { MutableInteractionSource() }
    LaunchedEffect(selectTouch) {
        selectTouch.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    takeOver()
                    hand = hand.copy(selectDownAt = time())
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> hand = hand.copy(selectLiftAt = time())
            }
        }
    }
    val continueTouch = remember { MutableInteractionSource() }
    LaunchedEffect(continueTouch) {
        continueTouch.interactions.collect {
            when (it) {
                is PressInteraction.Press -> {
                    takeOver()
                    hand = hand.copy(continueDownAt = time())
                }
                is PressInteraction.Release, is PressInteraction.Cancel -> hand = hand.copy(continueLiftAt = time())
            }
        }
    }

    Box(modifier.fillMaxSize().background(Background)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val bottom = insets.calculateBottomPadding().value

        Column(Modifier.fillMaxWidth().padding(start = SIDE.dp, end = SIDE.dp, top = (top + TITLE_TOP).dp)) {
            Text(
                title,
                fontSize = TITLE_SIZE.sp,
                fontWeight = FontWeight.Medium,
                letterSpacing = TITLE_TRACKING.sp,
                lineHeight = TITLE_LINE.sp,
                maxLines = 2,
                style = TextStyle(brush = Brush.verticalGradient(listOf(TitleTop, TitleBottom)), lineHeightStyle = Centered),
                // Two lines exactly: Roboto at this size is taller than the line, which would push the zone down.
                modifier = Modifier.height((TITLE_LINE * 2).dp).semantics { heading() },
            )
            Spacer(Modifier.height(ZONE_GAP.dp))
            DropZone(
                clock = clock,
                events = events,
                still = still,
                selectOn = selectOn,
                selectTouch = selectTouch,
                onSelect = ::select,
                heading = heading,
                selectLabel = "Select files",
            )
            Spacer(Modifier.height((LIST_GAP - ROW_GAP).dp))
            for (row in current.value.rows) {
                key(row.id) {
                    val canRetry by remember(row) { derivedStateOf { row.failed(clock.doubleValue) && row.dismissAt >= LATER } }
                    FileRow(
                        row = row,
                        clock = clock,
                        still = still,
                        canRetry = canRetry,
                        retryLabel = "Retry",
                        failedLabel = "Upload failed",
                        onRetryDown = {
                            takeOver()
                            hand = hand.retryDown(row.id, time())
                        },
                        onRetryLift = { hand = hand.retryLift(row.id, time()) },
                        onRetry = {
                            takeOver()
                            val now = time()
                            if (hand.rows.any { it.id == row.id && it.failed(now) }) {
                                tap()
                                hand = hand.retry(row.id, now)
                            }
                        },
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(start = SIDE.dp, end = SIDE.dp, bottom = (if (bottom > 0f) bottom + BUTTONS_LIFT else BUTTONS_FLOOR).dp)
                .height(BUTTON_HEIGHT.dp),
        ) {
            Box(
                Modifier
                    .width(CANCEL_WIDTH.dp)
                    .fillMaxHeight()
                    .background(CancelFill, ButtonShape)
                    .border(1.dp, CancelBorder, ButtonShape)
                    .clickable(interactionSource = null, indication = null, enabled = cancelOn, role = Role.Button) {
                        takeOver()
                        val now = time()
                        if (hand.cancelOn(now)) {
                            tap()
                            hand = hand.dismiss(now)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Cancel",
                    color = CancelLabel,
                    fontSize = BUTTON_LABEL.sp,
                    maxLines = 1,
                    modifier = Modifier.graphicsLayer {
                        alpha = (OFF_ALPHA + (1 - OFF_ALPHA) * events().level(clock.doubleValue, FADE) { cancelOn(it) }).toFloat()
                    },
                )
            }
            Spacer(Modifier.width(BUTTON_GAP.dp))
            ContinueButton(
                clock = clock,
                events = events,
                still = still,
                on = continueOn,
                touch = continueTouch,
                modifier = Modifier.weight(1f),
                onClick = {
                    takeOver()
                    val now = time()
                    if (hand.continueTappable(now)) {
                        tap()
                        val names = hand.batch(now).map { it.name }
                        hand = hand.continued(now)
                        continued?.invoke(names)
                    }
                },
            )
        }
    }
}

@Composable
private fun ContinueButton(
    clock: State<Double>,
    events: () -> Events,
    still: Boolean,
    on: Boolean,
    touch: MutableInteractionSource,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    fun level() = events().level(clock.value, ENABLE_FADE) { continueOn(it) }.toFloat()
    fun mix() = uploadedMix(events(), clock.value).toFloat()
    Box(
        modifier
            .fillMaxHeight()
            .graphicsLayer {
                val e = events()
                val s = if (still) 1f else pressScale(e.continueDownAt, e.continueLiftAt, clock.value, CONTINUE_PRESS).toFloat()
                scaleX = s
                scaleY = s
                clip = true
                shape = ButtonShape
            }
            .drawBehind {
                drawRect(lerp(ContinueOff, ContinueOn, level()))
                // A soft diagonal band ("/") crossing from left to right, once, when it turns on.
                sheen(events(), clock.value, still)?.let { t ->
                    val band = SHEEN_WIDTH.toFloat() * density
                    val across = (size.width + size.height) / sqrt(2f)
                    val c = -band / 2f + (across + band) * t.toFloat()
                    val n = Offset(1f, 1f) / sqrt(2f)
                    drawRect(
                        Brush.linearGradient(
                            listOf(Color.Transparent, SheenLight, Color.Transparent),
                            start = n * (c - band / 2f),
                            end = n * (c + band / 2f),
                        ),
                    )
                }
            }
            .clickable(interactionSource = touch, indication = null, enabled = on, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            "Continue",
            color = ContinueLabelOff,
            fontSize = BUTTON_LABEL.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = (1f - level()) * (1f - mix()) },
        )
        Text(
            "Continue",
            color = ContinueLabelOn,
            fontSize = BUTTON_LABEL.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            modifier = Modifier.graphicsLayer { alpha = level() * (1f - mix()) },
        )
        Row(
            Modifier.graphicsLayer { alpha = mix() },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val check = remember { TrimmedPath(LabelCheck) }
            Canvas(Modifier.size(LABEL_CHECK.dp)) {
                val k = density * LABEL_CHECK / LABEL_CHECK_BOX
                scale(k, k, pivot = Offset.Zero) { check.draw(this, 1f, UploadedCheck, LABEL_CHECK_STROKE * LABEL_CHECK_BOX / LABEL_CHECK) }
            }
            Spacer(Modifier.width(6.dp))
            Text("Uploaded", color = Color.White, fontSize = BUTTON_LABEL.sp, fontWeight = FontWeight.Medium, maxLines = 1)
        }
    }
}

/**
 * The screen is dark, so the status and navigation bar icons are switched to light while the block
 * is open and put back as they were when it closes.
 */
@Composable
private fun GlowFileUploaderSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
