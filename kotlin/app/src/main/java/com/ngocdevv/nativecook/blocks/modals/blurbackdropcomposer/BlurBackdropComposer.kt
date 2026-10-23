package com.ngocdevv.nativecook.blocks.modals.blurbackdropcomposer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
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
import androidx.compose.material.icons.automirrored.outlined.Backspace
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.outlined.AccountCircle
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.MicNone
import androidx.compose.material.icons.outlined.Public
import androidx.compose.material.icons.outlined.SentimentSatisfied
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowUpward
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableFloatState
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// One loop: the page at rest, then three openings, each with its own photo behind the composer.
private const val REST = 0.8f
private const val OPENING = 2.7f
private const val OPENINGS = 3
const val LOOP = 9f

// Inside an opening: the sheet rises, the name is typed, the sheet drops.
private const val TYPE_AT = 0.6f
private const val TYPE_EVERY = 0.09f
private const val CLOSE_AT = 1.75f
private val Names = listOf("Tokyo", "Maldives", "Swiss Alps")

// After a touch the loop waits this long, then starts again from the page.
private const val USER_HOLD = 3f

// A spring of 450 ms with damping ratio 0.9: stiffness (2π / 0.45)².
private const val OPEN_DAMPING = 0.9f
private const val OPEN_STIFFNESS = 195f
private const val CLOSE_MS = 350
private const val FADE_MS = 200

// Ease-in cubic.
private val CloseEasing = CubicBezierEasing(0.32f, 0f, 0.67f, 0f)

// The cards fade in and rise once the sheet is up: when they start, how long all of them take,
// the gap between two, and how far each rises.
private const val CARDS_AFTER = 0.3f
private const val CARDS_FOR = 0.6f
private const val CARD_STAGGER = 0.1f
private const val CARD_RISE = 14f

// The photo drifts from this scale to the next while the composer is up.
private const val DRIFT_FROM = 1.05f
private const val DRIFT_TO = 1.1f
private const val DRIFT_SECONDS = 6f

private val Accent = Color(0xFFE8643A)
private val Ink = Color(0xFF111114)
private val Mute = Color(0xFF7C7C83)
private val Field = Color(0xFFF1F1F4)
private val ButtonFill = Color(0xFF1F1F22)
private val TabGrey = Color(0xFF9A9AA1)
private val SheetFill = Color(0xFF20323A)

private val Glass = Color.White.copy(alpha = 0.2f)
private val GlassEdge = Color.White.copy(alpha = 0.32f)
private val GlassTop = Color.White.copy(alpha = 0.55f)
private val GlassIcon = Color.White.copy(alpha = 0.24f)

private val KeyboardFill = Color(0xFFD2D4DA)
private val KeyLit = Color(0xFFB9BCC4)
private val KeyDark = Color(0xFFABAFB9)
private val KeyDone = Color(0xFF0A7AFF)
private val KeyboardIcon = Color(0xFF4B4B52)

private const val MARGIN = 20
private const val SEARCH_HEIGHT = 38
private const val HERO_HEIGHT = 176
private const val HERO_RADIUS = 24
private const val GLASS_RADIUS = 22f
private const val KEY_GAP = 6
private const val KEY_HEIGHT = 44
private val KeyRows = listOf("qwertyuiop", "asdfghjkl", "zxcvbnm")
private val Suggestions = listOf("I", "The", "I’m")
private const val MAX_NAME = 22

private class Tilted(val photo: Int, val x: Float, val y: Int, val angle: Float)

// Where the collage's tilted cards sit: centre as a fraction of the width, top, tilt. Drawn in this
// order, so the middle card lies on top.
private val Collage = listOf(
    Tilted(0, 0.2f, 34, -14f),
    Tilted(1, 0.8f, 30, 12f),
    Tilted(2, 0.5f, 8, -3f),
)

private class Tab(val label: String, val icon: ImageVector)

private val Tabs = listOf(
    Tab("Trips", Icons.Rounded.Flight),
    Tab("Explore", Icons.Outlined.Explore),
    Tab("Saved", Icons.Outlined.BookmarkBorder),
    Tab("Profile", Icons.Outlined.AccountCircle),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun text(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    tracking: Float = 0f,
    align: TextAlign = TextAlign.Unspecified,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val SmallStyle = text(13f, 17f, FontWeight.Normal, Color.White.copy(alpha = 0.72f))
private val BigStyle = text(17f, 22f, FontWeight.Bold, Color.White, -0.3f)

/** One card's share of the reveal, eased out (cubic). */
private fun stage(progress: Float, index: Int): Float {
    val p = ((progress - index * CARD_STAGGER) / (1 - 2 * CARD_STAGGER)).coerceIn(0f, 1f)
    return 1 - (1 - p) * (1 - p) * (1 - p)
}

private class Step(val at: Float, val run: () -> Unit)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * A trips page with a "Create a Trip" button. The button raises a full-screen composer over a
 * blurred photo that changes on every opening: the name field, three frosted cards and a keyboard
 * drawn here, so nothing ever opens the system keyboard. The keys work by touch.
 *
 * @param autoPlay Opens and closes the composer by itself, three times a [LOOP], and leaves the page as it was.
 * @param onCreate Called with the name when Create (or the keyboard's done key) is pressed.
 */
@Composable
fun BlurBackdropComposer(
    autoPlay: Boolean = false,
    title: String = "My Trips",
    createLabel: String = "Create a Trip",
    placeholder: String = "Trip name",
    onCreate: ((name: String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val created by rememberUpdatedState(onCreate)
    val cancelled by rememberUpdatedState(onCancel)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var scene by remember { mutableIntStateOf(0) }
    var name by remember { mutableStateOf("") }
    var lit by remember { mutableStateOf<String?>(null) }
    var isOpen by remember { mutableStateOf(false) }
    var opened by remember { mutableIntStateOf(0) }
    var openings by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // 0 is the page alone, 1 the composer fully up.
    val sheet = remember { Animatable(0f) }
    // Seconds since the composer opened; drives the drift, the cards and the caret. Read while drawing only.
    val clock = remember { mutableFloatStateOf(0f) }

    fun open(index: Int) {
        scene = index % Backdrops.size
        name = ""
        isOpen = true
        clock.floatValue = 0f
        openings++
        scope.launch {
            if (reduced) sheet.animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) else sheet.animateTo(1f, spring(OPEN_DAMPING, OPEN_STIFFNESS))
        }
    }

    fun close() {
        scope.launch {
            // Ease-in cubic, also for the reduced-motion fade.
            sheet.animateTo(0f, tween(if (reduced) FADE_MS else CLOSE_MS, easing = CloseEasing))
            isOpen = false
        }
    }

    fun create() {
        created?.invoke(name)
        close()
    }

    fun type(char: String) {
        view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        name = when {
            char == "back" -> name.dropLast(1)
            name.length >= MAX_NAME -> name
            char == " " -> if (name.isNotEmpty() && !name.endsWith(" ")) "$name " else name
            else -> name + if (name.isEmpty()) char.uppercase() else char
        }
    }

    LaunchedEffect(openings) {
        if (openings == 0) return@LaunchedEffect
        val origin = withFrameNanos { it }
        while (isOpen) clock.floatValue = (withFrameNanos { it } - origin) / 1e9f
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to the page, then the loop from its first second.
            close()
            lit = null
            delay(400)
        }
        val steps = buildList {
            for (k in 0 until OPENINGS) {
                val start = REST + k * OPENING
                add(Step(start) { open(k) })
                val word = Names[k]
                for (i in word.indices) {
                    val at = start + TYPE_AT + i * TYPE_EVERY
                    add(Step(at) {
                        name = word.substring(0, i + 1)
                        lit = word[i].uppercase()
                    })
                    add(Step(at + 0.07f) { lit = null })
                }
                add(Step(start + CLOSE_AT) { close() })
            }
        }.sortedBy { it.at }
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

    // The status bar's icons follow the screen under them: dark on the page, light on the photo.
    val window = view.context.activity()?.window
    DisposableEffect(window, isOpen) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !isOpen
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.White)
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
        val insets = WindowInsets.systemBars.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding()
        val hidden = Modifier.clearAndSetSemantics {}

        Page(title, createLabel, top, if (isOpen) hidden else Modifier) { open(opened++) }
        TabBar(bottom, Modifier.align(Alignment.BottomCenter).then(if (isOpen) hidden else Modifier))

        // Always composed, so its photos are loaded before the first opening. When closed it sits
        // below the screen, where it takes no touches.
        Composer(
            scene = scene, name = name, placeholder = placeholder, lit = lit, isOpen = isOpen,
            top = top, bottom = bottom, sheet = sheet, clock = clock,
            reduced = reducedMotion, screenHeight = maxHeight, keyWidth = (maxWidth - (KEY_GAP * 11).dp) / 10,
            onCancel = {
                cancelled?.invoke()
                close()
            },
            onCreate = ::create,
            onKey = ::type,
            modifier = if (isOpen) Modifier else hidden,
        )
    }
}

@Composable
private fun Page(title: String, createLabel: String, top: Dp, modifier: Modifier, onOpen: () -> Unit) {
    BoxWithConstraints(modifier.fillMaxSize()) {
        val contentWidth = maxWidth - (MARGIN * 2).dp
        Column(Modifier.fillMaxSize().padding(start = MARGIN.dp, end = MARGIN.dp, top = top + 2.dp)) {
            Row(Modifier.fillMaxWidth().height(44.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(34.dp).border(2.dp, Accent, CircleShape).padding(4.dp)) {
                    Photo(AvatarPhoto, Modifier.fillMaxSize().clip(CircleShape))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Glyph(Icons.Rounded.Search, 24.dp, Accent)
                    Glyph(Icons.Rounded.Add, 32.dp, Accent)
                }
            }
            Row(
                Modifier.padding(top = 8.dp).fillMaxWidth().height(SEARCH_HEIGHT.dp)
                    .clip(RoundedCornerShape(12.dp)).background(Field).padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Glyph(Icons.Rounded.Search, 17.dp, Mute)
                BasicText("Search trips, places", style = text(17f, 22f, FontWeight.Normal, Mute, -0.3f))
            }
            BasicText(title, Modifier.padding(top = 20.dp, bottom = 16.dp), style = text(34f, 40f, FontWeight.ExtraBold, Ink, -1f))
            Hero(contentWidth)
            Box(Modifier.padding(top = 20.dp).width(contentWidth).height(158.dp)) {
                val card = (contentWidth.value * 0.36f).roundToInt().dp
                for (item in Collage) {
                    val shape = RoundedCornerShape(18.dp)
                    Box(
                        Modifier
                            .offset(contentWidth * item.x - card / 2, item.y.dp)
                            .size(card, card * 0.76f)
                            .graphicsLayer { rotationZ = item.angle }
                            .dropShadow(shape, Shadow(14.dp, Color(0xFF1A2230).copy(alpha = 0.2f), offset = DpOffset(0.dp, 8.dp)))
                            .background(Color.White, shape)
                            .padding(4.dp),
                    ) {
                        Photo(CollagePhotos[item.photo], Modifier.fillMaxSize().clip(RoundedCornerShape(14.dp)))
                    }
                }
            }
            BasicText(
                "Welcome, Traveler!",
                Modifier.padding(top = 4.dp).fillMaxWidth(),
                style = text(22f, 28f, FontWeight.ExtraBold, Ink, -0.5f, TextAlign.Center),
            )
            BasicText(
                "Create your first trip and start planning your next adventure.",
                Modifier.padding(top = 6.dp, start = 32.dp, end = 32.dp).fillMaxWidth(),
                style = text(15f, 21f, FontWeight.Normal, Mute, align = TextAlign.Center),
            )
            Box(Modifier.padding(top = 20.dp).fillMaxWidth(), contentAlignment = Alignment.Center) {
                PressRow(
                    Modifier
                        .dropShadow(CircleShape, Shadow(12.dp, Color.Black.copy(alpha = 0.22f), offset = DpOffset(0.dp, 6.dp)))
                        .height(52.dp)
                        .background(ButtonFill, CircleShape)
                        .padding(horizontal = 28.dp),
                    onOpen,
                ) {
                    Glyph(Icons.Rounded.Add, 22.dp, Color.White)
                    Spacer(Modifier.width(6.dp))
                    BasicText(createLabel, style = text(17f, 22f, FontWeight.SemiBold, Color.White, -0.2f))
                }
            }
        }
    }
}

@Composable
private fun Hero(width: Dp) {
    val shape = RoundedCornerShape(HERO_RADIUS.dp)
    Box(
        Modifier
            .width(width)
            .height(HERO_HEIGHT.dp)
            .dropShadow(shape, Shadow(20.dp, Color(0xFF0B1A2A).copy(alpha = 0.18f), offset = DpOffset(0.dp, 10.dp)))
            .clip(shape)
            .background(Color(0xFF6D7FA8)),
    ) {
        Photo(HeroPhoto, Modifier.fillMaxSize())
        Box(
            Modifier.fillMaxSize().drawBehind {
                // From the top-left corner to 70% across the bottom edge.
                drawRect(
                    Brush.linearGradient(
                        0f to Color(0xFF0A1028).copy(alpha = 0.55f),
                        0.55f to Color(0xFF0A1028).copy(alpha = 0.15f),
                        1f to Color(0xFF0A1028).copy(alpha = 0f),
                        start = Offset.Zero,
                        end = Offset(size.width * 0.7f, size.height),
                    ),
                )
            },
        )
        Column(Modifier.padding(18.dp)) {
            BasicText("Welcome to Wander!", style = text(21f, 26f, FontWeight.ExtraBold, Color.White, -0.5f))
            BasicText(
                "Learn how to plan every detail of your trip in one place.",
                Modifier.padding(top = 4.dp).width((width - 36.dp) * 0.62f),
                style = text(13f, 18f, FontWeight.Normal, Color.White.copy(alpha = 0.88f)),
            )
        }
        Box(
            Modifier.align(Alignment.TopEnd).padding(16.dp).size(28.dp).background(Color.Black.copy(alpha = 0.28f), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Glyph(Icons.Rounded.Close, 17.dp, Color.White)
        }
        Box(
            Modifier.align(Alignment.BottomStart).padding(18.dp).height(34.dp).background(Color.White, CircleShape).padding(horizontal = 16.dp),
            contentAlignment = Alignment.Center,
        ) {
            BasicText("See How", style = text(14f, 18f, FontWeight.Bold, Accent))
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
            val tint = if (i == 0) Accent else TabGrey
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                // The plane points right, as in the React Native icon.
                Glyph(tab.icon, 24.dp, tint, Modifier.graphicsLayer { rotationZ = if (i == 0) 90f else 0f })
                BasicText(tab.label, style = text(11f, 14f, FontWeight.SemiBold, tint))
            }
        }
    }
}

@Composable
private fun Composer(
    scene: Int,
    name: String,
    placeholder: String,
    lit: String?,
    isOpen: Boolean,
    top: Dp,
    bottom: Dp,
    sheet: Animatable<Float, *>,
    clock: MutableFloatState,
    reduced: Boolean,
    screenHeight: Dp,
    keyWidth: Dp,
    onCancel: () -> Unit,
    onCreate: () -> Unit,
    onKey: (String) -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier
            .fillMaxSize()
            .graphicsLayer {
                val p = sheet.value
                if (reduced) {
                    alpha = p
                    // Faded out it still sits on the page, so it moves away to leave the touches to it.
                    translationY = if (isOpen) 0f else screenHeight.toPx()
                } else {
                    translationY = (1f - min(p, 1f)) * screenHeight.toPx()
                }
            }
            .clipToBounds()
            .background(SheetFill)
            // The page behind must not get the touches that land on the sheet.
            .pointerInput(Unit) { detectTapGestures { } },
    ) {
        Backdrop(
            scene,
            Modifier.fillMaxSize().graphicsLayer {
                val t = min(clock.floatValue / DRIFT_SECONDS, 1f)
                val s = if (reduced) 1f else DRIFT_FROM + (DRIFT_TO - DRIFT_FROM) * t
                scaleX = s
                scaleY = s
            },
        )
        Box(
            Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.34f),
                    0.45f to Color.Black.copy(alpha = 0.08f),
                    1f to Color.Black.copy(alpha = 0.12f),
                ),
            ),
        )
        Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.06f)))

        Row(
            Modifier.padding(top = top + 4.dp, start = MARGIN.dp, end = MARGIN.dp).fillMaxWidth().height(40.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText(
                "Cancel",
                Modifier
                    .offset((-12).dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onCancel)
                    .padding(12.dp),
                style = text(17f, 22f, FontWeight.Medium, Color.White),
            )
            PressRow(
                Modifier
                    .dropShadow(CircleShape, Shadow(8.dp, Color.Black.copy(alpha = 0.16f), offset = DpOffset(0.dp, 3.dp)))
                    .height(36.dp)
                    .background(Color.White, CircleShape)
                    .padding(horizontal = 18.dp),
                onCreate,
            ) {
                BasicText("Create", style = text(16f, 20f, FontWeight.Bold, Accent))
            }
        }

        val caret = @Composable {
            Box(
                Modifier
                    .padding(horizontal = 2.dp)
                    .size(3.dp, 38.dp)
                    .graphicsLayer { alpha = if (((clock.floatValue / 0.5f).toInt()) % 2 == 0) 1f else 0f }
                    .background(Accent, RoundedCornerShape(1.5.dp)),
            )
        }
        Row(
            Modifier
                .padding(top = top + 64.dp, start = MARGIN.dp, end = MARGIN.dp)
                .fillMaxWidth()
                .height(52.dp)
                .graphicsLayer {
                    // The name field settles a beat before the cards.
                    val p = if (reduced) 1f else ((sheet.value - 0.4f) / 0.6f).coerceIn(0f, 1f)
                    alpha = p
                    translationY = (1f - p) * 10.dp.toPx()
                },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (name.isEmpty()) caret()
            BasicText(
                if (name.isEmpty()) placeholder else name,
                style = text(36f, 44f, FontWeight.Bold, if (name.isEmpty()) Color.White.copy(alpha = 0.55f) else Color.White, -0.9f),
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            if (name.isNotEmpty()) caret()
        }

        Reveal(clock, 0, reduced, Modifier.padding(top = top + 134.dp, start = MARGIN.dp, end = MARGIN.dp).fillMaxWidth().height(104.dp)) {
            CardHead(Icons.Rounded.CalendarToday, "Itinerary")
            Row(Modifier.fillMaxWidth().padding(top = 10.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.Bottom) {
                Column {
                    BasicText("Starts", style = SmallStyle)
                    BasicText("Set Dates", style = BigStyle)
                }
                Glyph(Icons.AutoMirrored.Rounded.ArrowForward, 20.dp, Color.White.copy(alpha = 0.9f), Modifier.padding(bottom = 1.dp))
                Column(horizontalAlignment = Alignment.End) {
                    BasicText("Ends", style = SmallStyle)
                    BasicText("Set Dates", style = BigStyle)
                }
            }
        }
        Row(
            Modifier.padding(top = top + 250.dp, start = MARGIN.dp, end = MARGIN.dp).fillMaxWidth().height(112.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Reveal(clock, 1, reduced, Modifier.weight(1f).fillMaxHeight(), Arrangement.SpaceBetween) {
                CardHead(Icons.Outlined.Public)
                Column {
                    BasicText("Timezone", style = BigStyle)
                    BasicText("Bengaluru, India", style = SmallStyle)
                }
            }
            Reveal(clock, 2, reduced, Modifier.weight(1f).fillMaxHeight(), Arrangement.SpaceBetween) {
                CardHead(Icons.Rounded.Image)
                Column {
                    BasicText("Choose Image", style = BigStyle)
                    BasicText("From your photos", style = SmallStyle)
                }
            }
        }

        Keyboard(lit, bottom, keyWidth, onKey, onCreate, Modifier.align(Alignment.BottomCenter))
    }
}

@Composable
private fun CardHead(icon: ImageVector, label: String? = null) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(28.dp).background(GlassIcon, RoundedCornerShape(9.dp)), contentAlignment = Alignment.Center) {
            Glyph(icon, 16.dp, Color.White)
        }
        if (label != null) BasicText(label, style = text(15f, 20f, FontWeight.SemiBold, Color.White.copy(alpha = 0.92f), -0.2f))
    }
}

/** A frosted card that fades in and rises into place once the sheet is up. */
@Composable
private fun Reveal(
    clock: MutableFloatState,
    index: Int,
    reduced: Boolean,
    modifier: Modifier,
    arrangement: Arrangement.Vertical = Arrangement.Top,
    content: @Composable () -> Unit,
) {
    val shape = RoundedCornerShape(GLASS_RADIUS.dp)
    Column(
        modifier
            .graphicsLayer {
                val cards = ((clock.floatValue - CARDS_AFTER) / CARDS_FOR).coerceIn(0f, 1f)
                val p = if (reduced) (if (cards > 0f) 1f else 0f) else stage(cards, index)
                alpha = p
                translationY = if (reduced) 0f else (1f - p) * CARD_RISE * density
                val s = if (reduced) 1f else 0.97f + 0.03f * p
                scaleX = s
                scaleY = s
            }
            .background(Glass, shape)
            .drawBehind {
                // The edge is lighter along the top, where the light catches it.
                val r = GLASS_RADIUS.dp.toPx()
                val edge = Brush.verticalGradient(0f to GlassTop, (r / size.height).coerceAtMost(1f) to GlassEdge)
                val w = 1.dp.toPx()
                drawRoundRect(
                    edge,
                    topLeft = Offset(w / 2, w / 2),
                    size = size.copy(width = size.width - w, height = size.height - w),
                    cornerRadius = CornerRadius(r - w / 2),
                    style = androidx.compose.ui.graphics.drawscope.Stroke(w),
                )
            }
            .padding(16.dp),
        verticalArrangement = arrangement,
    ) { content() }
}

@Composable
private fun Keyboard(lit: String?, bottom: Dp, keyWidth: Dp, onKey: (String) -> Unit, onDone: () -> Unit, modifier: Modifier) {
    Column(modifier.fillMaxWidth().background(KeyboardFill).padding(bottom = max(bottom.value - 18f, 6f).dp)) {
        Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
            Suggestions.forEachIndexed { i, word ->
                Box(
                    Modifier.weight(1f).fillMaxHeight().drawBehind {
                        if (i > 0) drawRect(Color.Black.copy(alpha = 0.14f), Offset(0f, 12.dp.toPx()), size.copy(width = 1.dp.toPx(), height = size.height - 24.dp.toPx()))
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(word, style = text(17f, 22f, FontWeight.Normal, Ink))
                }
            }
        }
        Column(Modifier.padding(horizontal = (KEY_GAP / 2f).dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            KeyRows.forEachIndexed { r, row ->
                val holdsLit = lit != null && row.uppercase().contains(lit)
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = if (r == 1) keyWidth / 2 else 0.dp).zIndex(if (holdsLit) 1f else 0f),
                    horizontalArrangement = Arrangement.spacedBy(KEY_GAP.dp, Alignment.CenterHorizontally),
                ) {
                    if (r == 2) {
                        Key(Modifier.padding(end = keyWidth * 0.2f).width(keyWidth * 1.3f), KeyDark, null, null) {
                            Glyph(Icons.Rounded.ArrowUpward, 24.dp, Color.Black)
                        }
                    }
                    for (char in row) {
                        val label = char.uppercase()
                        Key(Modifier.width(keyWidth).zIndex(if (lit == label) 1f else 0f), Color.White, char.toString(), { onKey(char.toString()) }) {
                            BasicText(label, style = text(23f, 28f, FontWeight.Normal, Color.Black))
                            if (lit == label) Callout(label, keyWidth + 18.dp)
                        }
                    }
                    if (r == 2) {
                        Key(Modifier.padding(start = keyWidth * 0.2f).width(keyWidth * 1.3f), KeyDark, "Delete", { onKey("back") }) {
                            Glyph(Icons.AutoMirrored.Outlined.Backspace, 22.dp, Color.Black)
                        }
                    }
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(KEY_GAP.dp)) {
                Key(Modifier.width(keyWidth * 2.3f), KeyDark, null, null) {
                    BasicText("123", style = text(16f, 20f, FontWeight.Normal, Color.Black))
                }
                Key(Modifier.weight(1f), if (lit == " ") KeyLit else Color.White, "Space", { onKey(" ") }) {
                    BasicText("space", style = text(16f, 20f, FontWeight.Normal, Color.Black))
                }
                Key(Modifier.width(keyWidth * 2.3f), KeyDone, "Done", onDone) {
                    BasicText("done", style = text(16f, 20f, FontWeight.SemiBold, Color.White))
                }
            }
        }
        Row(
            Modifier.padding(top = 4.dp).fillMaxWidth().height(40.dp).padding(horizontal = 26.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Glyph(Icons.Outlined.SentimentSatisfied, 27.dp, KeyboardIcon)
            Glyph(Icons.Outlined.MicNone, 27.dp, KeyboardIcon)
        }
    }
}

@Composable
private fun Key(
    modifier: Modifier,
    fill: Color,
    description: String?,
    onClick: (() -> Unit)?,
    content: @Composable () -> Unit,
) {
    Box(
        modifier
            .height(KEY_HEIGHT.dp)
            .drawBehind {
                // A hard one-dp shadow under the key, as on the system keyboard.
                val r = CornerRadius(8.dp.toPx())
                drawRoundRect(Color.Black.copy(alpha = 0.3f), Offset(0f, 1.dp.toPx()), size, r)
                drawRoundRect(fill, cornerRadius = r)
            }
            .then(
                if (onClick != null) {
                    Modifier
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onClick)
                        .semantics { contentDescription = description ?: "" }
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** The enlarged letter over a key while it is typed. */
@Composable
private fun Callout(label: String, width: Dp) {
    val shape = RoundedCornerShape(10.dp)
    Box(
        Modifier
            .offset(y = -(KEY_HEIGHT - 2).dp - (58 - KEY_HEIGHT).dp / 2)
            .requiredSize(width, 58.dp)
            .dropShadow(shape, Shadow(4.dp, Color.Black.copy(alpha = 0.28f), offset = DpOffset(0.dp, 1.dp)))
            .background(Color.White, shape),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(label, style = text(32f, 38f, FontWeight.Normal, Color.Black))
    }
}

/** A row that dims while it is held, like the React Native pressable. */
@Composable
private fun PressRow(modifier: Modifier, onClick: () -> Unit, content: @Composable () -> Unit) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Row(
        modifier
            .graphicsLayer { alpha = if (pressed) 0.8f else 1f }
            .clickable(interaction, indication = null, role = Role.Button, onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}

@Composable
private fun Glyph(icon: ImageVector, size: Dp, tint: Color, modifier: Modifier = Modifier) {
    Image(icon, contentDescription = null, modifier = modifier.size(size), colorFilter = ColorFilter.tint(tint))
}
