package com.ngocdevv.nativecook.blocks.inputs.liquidsearchmorph

import android.view.HapticFeedbackConstants
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

internal val PAPER = Color(0xFFF3F0EA)
internal val INK = Color(0xFF17150F)
internal val MUTED = Color(0xFF8B8579)
internal val CREAM = Color(0xFFF6F1E7)
internal val CARD = Color(0xFFFFFFFF)
internal val SAND = Color(0xFFE5DFD4)

// Layout, in dp.
internal const val SIDE = 20f
private const val PILL_TOP = 156f
private const val PILL_WIDTH = 214f
private const val PILL_HEIGHT = 46f
private const val BAR_HEIGHT = 50f
private const val CANCEL_WIDTH = 74f
internal const val GRID_GAP = 12f

// The morph runs on one value, 0 (pill) … 1 (search), at a constant rate; the pill gives each edge
// its own eased share of it. Opening takes OPEN_MS, closing CLOSE_MS.
private const val OPEN_MS = 760
private const val CLOSE_MS = 640
// The home page shrinks by HOME_SINK and blurs (FROST dp) under a veil while the search is open.
private const val HOME_SINK = 0.06f
private const val FROST = 7f

// autoPlay (seconds): open, type, read the results, close, rest.
private const val LOOP = 8.6
private const val OPEN_AT = 0.8
private const val TYPE_AT = 1.95
private const val KEY = 0.36
private const val CLOSE_AT = 5.9
private const val PRESS = 0.16
// After a touch, autoPlay starts over once the search has been closed and empty this long.
private const val RESUME_AFTER_MS = 4000L

/** Reanimated's default timing curve, `Easing.inOut(Easing.quad)`. */
internal val InOutQuad = CubicBezierEasing(0.455f, 0.03f, 0.515f, 0.955f)

/**
 * A shop's home page with a small ink-black search pill. Tapping it pulls the pill up to the top and
 * across the screen like a drop of ink, over a page that blurs and sinks back; recent searches pop
 * in under it. Typing filters a grid of pieces live, the tiles flowing to their new places as the
 * matches change, and Cancel pours the bar back into the pill.
 *
 * @param autoPlay opens the search, types [script], lets the results settle and closes again, over
 * and over, until a touch takes over.
 */
@Composable
fun LiquidSearchMorph(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = false,
    script: String = "air",
    onSearch: ((String) -> Unit)? = null,
) {
    val density = LocalDensity.current
    val view = LocalView.current
    val still = rememberReducedMotion()
    val scope = rememberCoroutineScope()
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val focusRequester = remember { FocusRequester() }
    val search by rememberUpdatedState(onSearch)

    var opened by remember { mutableStateOf(false) }
    var query by remember { mutableStateOf(TextFieldValue("")) }
    var focused by remember { mutableStateOf(false) }
    var playing by remember { mutableStateOf(autoPlay) }
    val morph = remember { Animatable(0f) }
    val pillPress = remember { Animatable(0f) }
    val cancelPress = remember { Animatable(0f) }
    // The keyboard's delayed focus and the close's delayed reset, so a quick Cancel can stop the one
    // and a second Cancel cannot start another of the other.
    val pending = remember {
        object {
            var focus: Job? = null
            var reset: Job? = null
            var closing = false
        }
    }

    fun tick() {
        view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    fun takeOver() {
        if (playing) playing = false
    }

    fun type(text: String) {
        query = TextFieldValue(text, TextRange(text.length))
        search?.invoke(text)
    }

    fun openUp(byUser: Boolean) {
        if (opened) return
        opened = true
        if (byUser) tick()
        scope.launch { morph.animateTo(1f, tween(OPEN_MS, easing = LinearEasing)) }
        // The keyboard comes up once the bar has landed, so it does not race the morph.
        if (byUser) {
            pending.focus?.cancel()
            pending.focus = scope.launch {
                delay((OPEN_MS * 0.75).toLong())
                if (opened && !pending.closing) runCatching { focusRequester.requestFocus() }
            }
        }
    }

    fun close(byUser: Boolean) {
        if (!opened || pending.closing) return
        pending.closing = true
        pending.focus?.cancel()
        if (byUser) tick()
        keyboard?.hide()
        focusManager.clearFocus()
        scope.launch { morph.animateTo(0f, tween(CLOSE_MS, easing = LinearEasing)) }
        // What the search showed goes once the pill is whole again.
        pending.reset = scope.launch {
            delay(CLOSE_MS.toLong())
            pending.closing = false
            opened = false
            query = TextFieldValue("")
        }
    }

    fun pulse(press: Animatable<Float, *>) {
        scope.launch {
            press.animateTo(1f, tween(90, easing = InOutQuad))
            press.animateTo(0f, tween(220, easing = InOutQuad))
        }
    }

    // autoPlay: every step is timed from the start of its loop, so the period never drifts.
    LaunchedEffect(playing, script) {
        if (!playing) return@LaunchedEffect
        val times = buildList {
            add(OPEN_AT - PRESS)
            add(OPEN_AT)
            for (i in script.indices) add(TYPE_AT + i * KEY)
            add(CLOSE_AT - PRESS)
            add(CLOSE_AT)
        }
        var origin = System.nanoTime()
        while (true) {
            times.forEachIndexed { step, at ->
                delay(((origin + (at * 1e9).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
                when {
                    step == 0 -> pulse(pillPress)
                    step == 1 -> openUp(byUser = false)
                    step < 2 + script.length -> type(script.take(step - 1))
                    step == 2 + script.length -> pulse(cancelPress)
                    else -> close(byUser = false)
                }
            }
            delay(((origin + (LOOP * 1e9).toLong() - System.nanoTime()) / 1_000_000).coerceAtLeast(0))
            origin += (LOOP * 1e9).toLong()
        }
    }
    // A touch takes over; once the search is closed and left alone, the loop starts over.
    LaunchedEffect(autoPlay, playing, opened) {
        if (autoPlay && !playing && !opened) {
            delay(RESUME_AFTER_MS)
            playing = true
        }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(PAPER).clipToBounds()) {
        val screenW = maxWidth.value
        val screenH = maxHeight.value
        val top = WindowInsets.statusBars.union(WindowInsets.displayCutout).getTop(density) / density.density
        val bottom = WindowInsets.navigationBars.getBottom(density) / density.density
        val closed = Rect(SIDE, top + PILL_TOP, SIDE + PILL_WIDTH, top + PILL_TOP + PILL_HEIGHT)
        // Never inverted or negative: the first measure can report a zero size.
        val open = Rect(16f, top + 8, (screenW - 16 - CANCEL_WIDTH).coerceAtLeast(16f), top + 8 + BAR_HEIGHT)
        val tileWidth = ((screenW - SIDE * 2 - GRID_GAP) / 2).coerceAtLeast(0f)
        val canvasH = closed.bottom + 70
        val t = { morph.value }
        val frost = sigmaToRadius(FROST * density.density)

        // The home page.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val sink = if (still) 1f else 1 - HOME_SINK * (t() * 1.3f).coerceIn(0f, 1f)
                    scaleX = sink
                    scaleY = sink
                    // The page blurs as the veil comes up, a light frost like RN's BlurView.
                    val k = (t() * 1.6f).coerceIn(0f, 1f)
                    renderEffect = if (k > 0.001f) BlurEffect(frost * k, frost * k, TileMode.Decal) else null
                },
        ) {
            Home(top = top, screenW = screenW, screenH = screenH, tileWidth = tileWidth)
        }

        // The veil the search opens over.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = (t() * 1.6f).coerceIn(0f, 1f) }
                .background(PAPER.copy(alpha = 0.66f)),
        )

        // Under the bar: recent searches, or the results.
        if (opened) {
            Box(
                Modifier
                    .offset(y = (open.bottom + 10).dp)
                    .fillMaxWidth()
                    .size(width = screenW.dp, height = (screenH - open.bottom - 10).coerceAtLeast(0f).dp)
                    .graphicsLayer {
                        val k = ((t() - 0.5f) / 0.5f).coerceIn(0f, 1f)
                        alpha = k
                        translationY = if (still) 0f else (1 - k) * 18.dp.toPx()
                    },
            ) {
                val text = query.text
                AnimatedVisibility(visible = text.isEmpty(), enter = EnterTransition.None, exit = fadeOut(tween(150, easing = InOutQuad))) {
                    Suggestions(still = still) { word ->
                        takeOver()
                        type(word)
                    }
                }
                if (text.isNotEmpty()) {
                    Results(
                        query = text,
                        tileWidth = tileWidth,
                        bottomInset = bottom,
                        still = still,
                        onDrag = {
                            keyboard?.hide()
                            focusManager.clearFocus()
                        },
                    )
                }
            }
        }

        // The ink pill, and what rides on it.
        InkPill(
            t = t,
            press = { pillPress.value },
            closed = closed,
            open = open,
            still = still,
            color = INK,
            modifier = Modifier.size(screenW.dp, canvasH.dp),
        )
        Box(
            Modifier
                .offset {
                    val p = inkAt(t(), closed, open, still)
                    IntOffset(((p.dropX - 10) * density.density).roundToInt(), ((p.dropY - 10) * density.density).roundToInt())
                }
                .size(20.dp)
                .graphicsLayer { alpha = if (still) abs(1 - 2 * t()) else 1f },
            contentAlignment = Alignment.Center,
        ) {
            // The glyph fills about 73% of a Material icon's box: 24 dp draws it 18 dp wide.
            Icon(Icons.Rounded.Search, contentDescription = null, tint = CREAM, modifier = Modifier.requiredSize(24.dp))
        }
        // The pill's own words ride its left edge and fade before it stretches.
        Box(
            Modifier
                .offset {
                    val p = inkAt(t(), closed, open, still)
                    IntOffset(
                        ((closed.left + ICON_INSET + 20 + p.left - closed.left) * density.density).roundToInt(),
                        ((closed.top + (p.top + p.bottom) / 2 - closed.center.y) * density.density).roundToInt(),
                    )
                }
                .size(width = 200.dp, height = PILL_HEIGHT.dp)
                .graphicsLayer { alpha = (1 - t() * 5).coerceIn(0f, 1f) * (1 - pillPress.value * 0.2f) },
            contentAlignment = Alignment.CenterStart,
        ) {
            Text("Search ${PIECES.size} pieces", color = CREAM, fontSize = 15.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
        if (!opened) {
            Box(
                Modifier
                    .offset(closed.left.dp, closed.top.dp)
                    .size(closed.width.dp, closed.height.dp)
                    .semantics { contentDescription = "Search ${PIECES.size} pieces" }
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onPress = {
                                scope.launch { pillPress.animateTo(1f, tween(90, easing = InOutQuad)) }
                                tryAwaitRelease()
                                scope.launch { pillPress.animateTo(0f, tween(200, easing = InOutQuad)) }
                            },
                            onTap = {
                                takeOver()
                                openUp(byUser = true)
                            },
                        )
                    },
            )
        }
        if (opened) {
            Row(
                Modifier
                    .offset((open.left + ICON_INSET + 20).dp, open.top.dp)
                    .size((open.width - ICON_INSET - 20 - 10).coerceAtLeast(0f).dp, open.height.dp)
                    .graphicsLayer { alpha = ((t() - 0.72f) / 0.28f).coerceIn(0f, 1f) },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                BasicTextField(
                    value = query,
                    onValueChange = { value ->
                        if (value.text != query.text) {
                            takeOver()
                            search?.invoke(value.text)
                        }
                        query = value
                    },
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(focusRequester)
                        .onFocusChanged { state ->
                            if (state.isFocused && !focused) takeOver()
                            focused = state.isFocused
                        }
                        .semantics { contentDescription = "Search the shop" },
                    textStyle = TextStyle(color = CREAM, fontSize = 17.sp, fontWeight = FontWeight.Medium),
                    cursorBrush = SolidColor(CREAM),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(
                        capitalization = KeyboardCapitalization.None,
                        autoCorrectEnabled = false,
                        keyboardType = KeyboardType.Text,
                        imeAction = ImeAction.Search,
                    ),
                    keyboardActions = KeyboardActions(onSearch = { keyboard?.hide() }),
                    decorationBox = { field ->
                        Box(contentAlignment = Alignment.CenterStart) {
                            if (query.text.isEmpty()) {
                                Text("Sneakers, scents, sizes", color = CREAM.copy(alpha = 0.45f), fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                            }
                            field()
                            // autoPlay types behind its own blinking caret.
                            if (playing && !focused) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(query.text, color = Color.Transparent, fontSize = 17.sp, fontWeight = FontWeight.Medium, maxLines = 1)
                                    Caret(Modifier.padding(start = 1.dp))
                                }
                            }
                        }
                    },
                )
                if (query.text.isNotEmpty()) {
                    Box(
                        Modifier
                            .padding(start = 8.dp)
                            .semantics { contentDescription = "Clear the search" }
                            .pointerInput(Unit) {
                                detectTapGestures {
                                    takeOver()
                                    type("")
                                }
                            },
                    ) {
                        Icon(Icons.Filled.Cancel, contentDescription = null, tint = CREAM.copy(alpha = 0.55f), modifier = Modifier.size(20.dp))
                    }
                }
            }
            Box(
                Modifier
                    .offset((screenW - 16 - CANCEL_WIDTH).dp, open.top.dp)
                    .size(CANCEL_WIDTH.dp, open.height.dp)
                    .graphicsLayer {
                        val k = ((t() - 0.45f) / 0.55f).coerceIn(0f, 1f)
                        alpha = k
                        translationX = if (still) 0f else (1 - k) * 24.dp.toPx()
                        val s = 1 - cancelPress.value * 0.08f
                        scaleX = s
                        scaleY = s
                    }
                    .semantics { contentDescription = "Cancel" }
                    .pointerInput(Unit) {
                        detectTapGestures {
                            takeOver()
                            close(byUser = true)
                        }
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text("Cancel", color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/** The caret autoPlay types behind: on for 45% of a second, then off. */
@Composable
private fun Caret(modifier: Modifier = Modifier) {
    val clock = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        while (true) {
            clock.snapTo(0f)
            clock.animateTo(1f, tween(1000, easing = LinearEasing))
        }
    }
    Box(
        modifier
            .size(width = 2.dp, height = 21.dp)
            .graphicsLayer {
                val phase = clock.value
                alpha = when {
                    phase < 0.45f -> 1f
                    phase < 0.55f -> 1 - (phase - 0.45f) / 0.1f
                    else -> 0f
                }
            }
            .background(CREAM, androidx.compose.foundation.shape.RoundedCornerShape(1.dp)),
    )
}
