package com.ngocdevv.nativecook.blocks.sheets.morphingmodesheet

import android.os.Build
import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.outlined.CompareArrows
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.FlightTakeoff
import androidx.compose.material.icons.outlined.Language
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** The autoPlay loop, in seconds from its start. */
const val LOOP = 9f
private const val OPEN_AT = 0.8f
private const val VALUE_TAP_AT = 1.8f
private const val TAP_FOR = 0.12f
private const val PICK_AT = 3f
private const val COLLAPSE_AT = 4f
private const val RESEARCH_AT = 5.2f
private const val INCOGNITO_ON_AT = 6f
private const val INCOGNITO_OFF_AT = 6.8f
private const val CLOSE_AT = 7.4f
private const val REST_AT = 8.2f
// The model the loop picks, by place in the list.
private const val PICKED = 2
// Seconds a touch keeps the loop away.
private const val USER_HOLD = 3f

private const val AVATAR = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=160&h=160&q=80"

private val IDEAS = listOf(
    Icons.Outlined.Lightbulb to "Explain simply",
    Icons.Outlined.FlightTakeoff to "Plan a weekend",
    Icons.AutoMirrored.Outlined.CompareArrows to "Compare",
)

private val PAGE = Color(0xFFFBFBFA)
private val INK = Color(0xFF16181A)
private val MUTE = Color(0xFF94969B)
private val OUTLINE = Color(0xFFE7E7E5)
private val GRABBER = Color(0xFFD4D4D4)
private val CLOSE_FILL = Color(0xFFECECEC)
private val CLOSE_INK = Color(0xFF6B6D72)
private val VALUE_INK = Color(0xFF5D6066)
private val IDEA_INK = Color(0xFF4A4D52)
private val TEAL = Color(0xFF20B8CD)
private val TEAL_DEEP = Color(0xFF1E8F95)
private val WEB_FILL = Color(0xFFEEF7F7)
private val WEB_BORDER = Color(0xFFCFE9EA)
private val INPUT_SHADOW = Color(0xFF1A1A1A)
private val ORANGE = Color(0xFFF26A1B)
// Shown under the avatar until the photo has loaded.
private val AVATAR_TINT = Color(0xFFD9C9B8)
private val MARK = Brush.linearGradient(listOf(Color(0xFFFF8A3D), ORANGE, Color(0xFFD9480F)))
private const val DIM = 0.08f
// React Native's hairline width doubled, on a 3x screen.
private val HAIRLINE = (2f / 3f).dp

private const val PAGE_MARGIN = 20f
private const val SIDE = 8f
private const val HEADER_HEIGHT = 60f
private val COMPACT_HEIGHT = HEADER_HEIGHT + MODE_HEIGHT.value * 4 + MODE_GAP.value * 3 + 14f
private const val SHEET_RADIUS = 28f
// Blur sigma of the page at full strength: the React Native version's intensity 50.
private const val BLUR = 8f
private const val SNAP_VELOCITY = 500f
private const val DISMISS_DRAG = 90f

// Reanimated's duration springs: stiffness is (2π / duration)².
private val RISE_SPRING = spring<Float>(dampingRatio = 0.85f, stiffness = 158f)
private val MORPH_SPRING = spring<Float>(dampingRatio = 0.88f, stiffness = 195f)
private const val DROP_MS = 400
private val DROP_EASING = CubicBezierEasing(0.5f, 0f, 1f, 1f)
private const val FADE_MS = 160
// The drawn keyboard drops as the sheet rises and comes back after it.
private const val KEYS_DOWN_MS = 260
private const val KEYS_UP_MS = 380
private const val KEYS_UP_AFTER = 120
private val EASE_IN_QUAD = Easing { it * it }
private val EASE_OUT_CUBIC = Easing { 1f - (1f - it) * (1f - it) * (1f - it) }
private const val PRESS_SCALE = 0.88f

private fun lerp(from: Float, to: Float, t: Float) = from + (to - from) * t
private fun ramp(value: Float, from: Float, to: Float) = min(1f, max(0f, (value - from) / (to - from)))

/**
 * A prompt screen with a sheet over it. The sheet opens as a compact list of modes; touching the
 * model at the right of the first row grows the same sheet upward into a list of models, while
 * the two lists cross-fade inside it, and a pick shrinks it back. The page behind blurs and dims
 * while the sheet is up. The grabber drags the sheet between its two heights.
 *
 * @param modes The rows of the compact sheet; the first one shows the chosen model at its right.
 * @param models The rows of the tall sheet.
 * @param autoPlay Plays the whole interaction by itself, over and over, and gives way to a touch.
 */
@Composable
fun MorphingModeSheet(
    modes: List<Mode> = DEFAULT_MODES,
    models: List<Model> = DEFAULT_MODELS,
    autoPlay: Boolean = false,
    wordmark: String = "Briar",
    placeholder: String = "Ask anything...",
    compactTitle: String = "Choose a mode",
    tallTitle: String = "Models",
    incognitoLabel: String = "Incognito mode",
    incognitoCaption: String = "Activity won't be saved",
    onModeChange: ((Mode) -> Unit)? = null,
    onModelChange: ((Model) -> Unit)? = null,
    onIncognitoChange: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    MorphingModeSheetSystemBars()
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val modeRows by rememberUpdatedState(modes)
    val modelRows by rememberUpdatedState(models)
    val modeChanged by rememberUpdatedState(onModeChange)
    val modelChanged by rememberUpdatedState(onModelChange)
    val incognitoChanged by rememberUpdatedState(onIncognitoChange)
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInset = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }

    // 0 below the screen, 1 up; and 0 compact, 1 tall.
    val show = remember { Animatable(0f) }
    val grow = remember { Animatable(0f) }
    // 1 while the drawn keyboard is up, as it is on the page at rest.
    val keys = remember { Animatable(1f) }
    var grabbed by remember { mutableFloatStateOf(0f) }
    var dragged by remember { mutableFloatStateOf(0f) }
    var mode by remember { mutableIntStateOf(0) }
    var model by remember { mutableIntStateOf(0) }
    var incognito by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(false) }
    var isTall by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var autoValue by remember { mutableStateOf(false) }

    fun settle(to: Float) = scope.launch {
        if (reducedMotion) grow.animateTo(to, tween(FADE_MS)) else grow.animateTo(to, MORPH_SPRING)
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        autoValue = false
        touches++
    }

    fun open() {
        if (isOpen) return
        isOpen = true
        scope.launch { if (reducedMotion) show.animateTo(1f, tween(FADE_MS)) else show.animateTo(1f, RISE_SPRING) }
        scope.launch { keys.animateTo(0f, tween(if (reducedMotion) FADE_MS else KEYS_DOWN_MS, easing = EASE_IN_QUAD)) }
    }

    fun collapse() {
        isTall = false
        settle(0f)
    }

    fun expand() {
        isTall = true
        settle(1f)
    }

    fun close() {
        isOpen = false
        scope.launch { show.animateTo(0f, tween(if (reducedMotion) FADE_MS else DROP_MS, easing = DROP_EASING)) }
        scope.launch {
            keys.animateTo(
                1f,
                if (reducedMotion) tween(FADE_MS) else tween(KEYS_UP_MS, delayMillis = KEYS_UP_AFTER, easing = EASE_OUT_CUBIC),
            )
        }
        collapse()
    }

    fun choose(index: Int) {
        model = index
        modelChanged?.invoke(modelRows[index])
    }

    fun pickMode(index: Int) {
        mode = index
        modeChanged?.invoke(modeRows[index])
    }

    /** Everything back as it was at the start, with no animation. */
    fun rest() {
        scope.launch {
            show.snapTo(0f)
            grow.snapTo(0f)
            keys.snapTo(1f)
        }
        grabbed = 0f
        autoValue = false
        isOpen = false
        isTall = false
        mode = 0
        model = 0
        incognito = false
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        val steps = listOf(
            OPEN_AT to { open() },
            VALUE_TAP_AT to { autoValue = true },
            VALUE_TAP_AT + TAP_FOR to { autoValue = false; expand() },
            PICK_AT to { model = PICKED },
            COLLAPSE_AT to { collapse() },
            RESEARCH_AT to { mode = 1 },
            INCOGNITO_ON_AT to { incognito = true },
            INCOGNITO_OFF_AT to { incognito = false },
            CLOSE_AT to { close() },
            REST_AT to { rest() },
        )
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, run) in steps) {
                until(at)
                run()
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(PAGE)) {
        val screenWidth = maxWidth
        val screenHeight = maxHeight
        val sheetWidth = screenWidth - SIDE.dp * 2
        val bottom = max(bottomInset.value - 14f, SIDE).dp
        val listHeight = (MODEL_HEIGHT + MODEL_GAP) * models.size.toFloat() + HEADING_HEIGHT * models.count { it.heading != null }.toFloat()
        // As tall as the list needs, and no taller than the screen allows.
        val tallHeight = minOf(HEADER_HEIGHT.dp + listHeight + 16.dp, screenHeight - topInset - bottom - 24.dp)
        val reach by rememberUpdatedState(with(localDensity) { (tallHeight - COMPACT_HEIGHT.dp).toPx() })
        val tallPx by rememberUpdatedState(with(localDensity) { tallHeight.toPx() })
        val bottomPx by rememberUpdatedState(with(localDensity) { bottom.toPx() })
        val screenPx by rememberUpdatedState(with(localDensity) { screenHeight.toPx() })
        val tallVisible by remember { derivedStateOf { grow.value > 0f } }

        // The page.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = BLUR * density * show.value
                        renderEffect = if (radius > 0.01f) BlurEffect(radius, radius, TileMode.Decal) else null
                    }
                },
        ) {
            Row(
                Modifier.offset(y = topInset + 6.dp).fillMaxWidth().height(40.dp).padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                val context = LocalContext.current
                val request = remember(context) { ImageRequest.Builder(context).data(AVATAR).crossfade(200).build() }
                AsyncImage(
                    model = request,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(AVATAR_TINT)
                        .border(2.dp, Color.White, CircleShape),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    IconButton(Icons.Outlined.Schedule)
                    IconButton(Icons.Outlined.Edit)
                }
            }
            Column(
                Modifier.align(Alignment.TopCenter).offset(y = topInset + 118.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    Box(
                        Modifier
                            .size(34.dp)
                            .graphicsLayer { rotationZ = -8f }
                            .background(MARK, RoundedCornerShape(11.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Filled.AutoAwesome, null, tint = Color.White, modifier = Modifier.size(17.dp))
                    }
                    Text(wordmark, color = INK, fontSize = 36.sp, lineHeight = 42.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = (-1.2).sp)
                    Box(
                        Modifier
                            .padding(top = 4.dp)
                            .height(20.dp)
                            .border(1.5.dp, TEAL, RoundedCornerShape(6.dp))
                            .padding(horizontal = 7.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text("PRO", color = TEAL_DEEP, fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 0.8.sp)
                    }
                }
                Text(
                    "Where good questions begin",
                    modifier = Modifier.padding(top = 6.dp),
                    color = MUTE,
                    fontSize = 15.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
            Column(
                Modifier
                    .offset(x = (PAGE_MARGIN - 4f).dp, y = topInset + 226.dp)
                    .size(screenWidth - (PAGE_MARGIN - 4f).dp * 2, 112.dp)
                    .dropShadow(INPUT_SHAPE, Shadow(radius = 24.dp, color = INPUT_SHADOW, offset = DpOffset(0.dp, 10.dp), alpha = 0.08f))
                    .background(Color.White, INPUT_SHAPE)
                    .border(HAIRLINE, OUTLINE, INPUT_SHAPE)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        touched()
                        open()
                    }
                    .padding(start = 16.dp, end = 16.dp, top = 18.dp, bottom = 12.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .padding(end = 2.dp)
                            .size(2.dp, 22.dp)
                            .graphicsLayer { alpha = keys.value }
                            .background(TEAL, RoundedCornerShape(1.dp)),
                    )
                    Text(placeholder, color = MUTE, fontSize = 17.sp, lineHeight = 22.sp)
                }
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Box(Modifier.size(36.dp).border(HAIRLINE, OUTLINE, CircleShape), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Add, null, tint = INK, modifier = Modifier.size(20.dp))
                        }
                        Row(
                            Modifier
                                .height(36.dp)
                                .background(WEB_FILL, CircleShape)
                                .border(HAIRLINE, WEB_BORDER, CircleShape)
                                .padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                        ) {
                            Icon(Icons.Outlined.Language, null, tint = TEAL_DEEP, modifier = Modifier.size(15.dp))
                            Text("Web", color = TEAL_DEEP, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                        Icon(Icons.Outlined.Mic, null, tint = INK, modifier = Modifier.size(21.dp))
                        Box(
                            Modifier
                                .size(38.dp)
                                .dropShadow(CircleShape, Shadow(radius = 8.dp, color = TEAL, offset = DpOffset(0.dp, 3.dp), alpha = 0.4f))
                                .background(TEAL, CircleShape),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Filled.GraphicEq, null, tint = Color.White, modifier = Modifier.size(19.dp))
                        }
                    }
                }
            }
            Row(
                Modifier
                    .offset(y = topInset + 356.dp)
                    .fillMaxWidth()
                    .clipToBounds()
                    .padding(start = (PAGE_MARGIN - 4f).dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                for ((icon, label) in IDEAS) {
                    Row(
                        Modifier
                            .height(36.dp)
                            .background(Color.White, CircleShape)
                            .border(HAIRLINE, OUTLINE, CircleShape)
                            .padding(horizontal = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(icon, null, tint = MUTE, modifier = Modifier.size(15.dp))
                        Text(label, color = IDEA_INK, fontSize = 14.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, maxLines = 1, softWrap = false)
                    }
                }
            }
            Keyboard(
                width = screenWidth,
                bottomInset = bottomInset,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .graphicsLayer { translationY = (1f - keys.value) * (KEYBOARD_HEIGHT + bottomInset + 12.dp).toPx() },
            )
        }

        // Before Android 12 there is no blur: the page fades towards white instead.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            Box(Modifier.fillMaxSize().graphicsLayer { alpha = 0.4f * show.value }.background(Color.White))
        }
        Box(Modifier.fillMaxSize().graphicsLayer { alpha = DIM * show.value }.background(Color.Black))
        if (isOpen) {
            Box(
                Modifier
                    .fillMaxSize()
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        touched()
                        close()
                    },
            )
        }

        // The sheet: its height and its place come from the two runs, read while laying out.
        Box(
            Modifier
                .offset {
                    val tall = lerp(COMPACT_HEIGHT * density, tallPx, grow.value)
                    IntOffset(
                        (SIDE * density).roundToInt(),
                        (screenPx - bottomPx - tall + (1f - show.value) * (tall + bottomPx + 24f * density)).roundToInt(),
                    )
                }
                .layout { measurable, _ ->
                    val w = sheetWidth.roundToPx()
                    val h = lerp(COMPACT_HEIGHT * density, tallPx, grow.value).roundToInt()
                    val placeable = measurable.measure(Constraints.fixed(w, h))
                    layout(w, h) { placeable.place(0, 0) }
                }
                // The shadow sits outside the clip that keeps the two lists to the sheet's shape.
                .dropShadow(SHEET_SHAPE, Shadow(radius = 32.dp, color = Color.Black, offset = DpOffset(0.dp, 12.dp), alpha = 0.16f))
                .graphicsLayer {
                    shape = SHEET_SHAPE
                    clip = true
                }
                .background(Color.White),
        ) {
            // The compact list.
            Column(
                Modifier
                    .zIndex(if (isTall) 0f else 1f)
                    .fillMaxWidth()
                    .height(COMPACT_HEIGHT.dp)
                    .graphicsLayer { alpha = 1f - ramp(grow.value, 0f, 0.4f) }
                    .padding(start = 12.dp, end = 12.dp, top = HEADER_HEIGHT.dp),
                verticalArrangement = Arrangement.spacedBy(MODE_GAP),
            ) {
                modes.forEachIndexed { index, item ->
                    ModeRow(
                        mode = item,
                        selected = index == mode,
                        onPress = {
                            if (!isTall) {
                                touched()
                                pickMode(index)
                            }
                        },
                    ) {
                        if (index == 0) {
                            ValueButton(
                                label = models[model].name,
                                forced = autoValue,
                                onDown = { touched() },
                                onTap = { if (!isTall) expand() },
                            )
                        }
                    }
                }
                SwitchRow(incognitoLabel, incognitoCaption, incognito, {
                    if (!isTall) {
                        touched()
                        incognito = !incognito
                        incognitoChanged?.invoke(incognito)
                    }
                })
            }

            // The tall list.
            Column(
                Modifier
                    .zIndex(if (isTall) 1f else 0f)
                    .fillMaxSize()
                    .graphicsLayer { alpha = ramp(grow.value, 0.3f, 0.7f) }
                    .padding(top = HEADER_HEIGHT.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(bottom = 16.dp),
            ) {
                if (tallVisible) {
                    models.forEachIndexed { index, item ->
                        ModelRow(item, index, selected = index == model, grow = { grow.value }) {
                            if (isTall) {
                                touched()
                                choose(index)
                                collapse()
                            }
                        }
                    }
                }
            }

            // The header: the grabber drags the sheet between its two heights.
            Box(
                Modifier
                    .zIndex(2f)
                    .fillMaxWidth()
                    .height(HEADER_HEIGHT.dp)
                    .draggable(
                        orientation = Orientation.Vertical,
                        state = rememberDraggableState { delta ->
                            dragged += delta
                            val next = min(1f, max(0f, grabbed - dragged / reach))
                            scope.launch { grow.snapTo(next) }
                        },
                        onDragStarted = {
                            grabbed = grow.value
                            dragged = 0f
                            touched()
                        },
                        onDragStopped = { velocity ->
                            val pxScale = localDensity.density
                            if (grabbed < 0.5f && (dragged > DISMISS_DRAG * pxScale || velocity > SNAP_VELOCITY * 2f * pxScale)) {
                                close()
                                return@draggable
                            }
                            val up = velocity < -SNAP_VELOCITY * pxScale || (abs(velocity) <= SNAP_VELOCITY * pxScale && grow.value > 0.5f)
                            if (up) expand() else collapse()
                        },
                    ),
            ) {
                Box(Modifier.align(Alignment.TopCenter).padding(top = 8.dp).size(width = 36.dp, height = 5.dp).background(GRABBER, RoundedCornerShape(2.5.dp)))
                SheetTitle(compactTitle, Modifier.graphicsLayer { alpha = 1f - ramp(grow.value, 0.1f, 0.5f) })
                SheetTitle(tallTitle, Modifier.graphicsLayer { alpha = ramp(grow.value, 0.4f, 0.8f) })
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(x = (-14).dp, y = 20.dp)
                        .size(28.dp)
                        .background(CLOSE_FILL, CircleShape)
                        .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                            touched()
                            close()
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Close, contentDescription = "Close", tint = CLOSE_INK, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

/** The model chosen, at the right of the first mode row; touching it opens the list of models. */
@Composable
private fun ValueButton(label: String, forced: Boolean, onDown: () -> Unit, onTap: () -> Unit) {
    var pressed by remember { mutableStateOf(false) }
    val down = pressed || forced
    val scale by animateFloatAsState(if (down) PRESS_SCALE else 1f, tween(if (down) 80 else 140), label = "value")
    val latestDown by rememberUpdatedState(onDown)
    val latestTap by rememberUpdatedState(onTap)
    Row(
        Modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        latestDown()
                        tryAwaitRelease()
                        pressed = false
                    },
                    onTap = { latestTap() },
                )
            }
            .height(26.dp)
            .background(Color.White.copy(alpha = 0.8f), CircleShape)
            .padding(start = 10.dp, end = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Text(label, color = VALUE_INK, fontSize = 13.sp, lineHeight = 16.sp, fontWeight = FontWeight.SemiBold)
        Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = "Choose a model", tint = MUTE, modifier = Modifier.size(13.dp))
    }
}

private val INPUT_SHAPE = RoundedCornerShape(26.dp)
private val SHEET_SHAPE = RoundedCornerShape(SHEET_RADIUS.dp)

@Composable
private fun SheetTitle(text: String, modifier: Modifier) {
    Text(
        text,
        modifier = Modifier.offset(x = 18.dp, y = 22.dp).then(modifier),
        color = INK,
        fontSize = 18.sp,
        lineHeight = 24.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = (-0.3).sp,
    )
}

@Composable
private fun IconButton(icon: ImageVector) {
    Box(
        Modifier
            .size(38.dp)
            .background(Color.White, CircleShape)
            .border(HAIRLINE, OUTLINE, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = INK, modifier = Modifier.size(19.dp))
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun MorphingModeSheetSystemBars(light: Boolean = true) {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view, light) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = light
        controller.isAppearanceLightNavigationBars = light
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
