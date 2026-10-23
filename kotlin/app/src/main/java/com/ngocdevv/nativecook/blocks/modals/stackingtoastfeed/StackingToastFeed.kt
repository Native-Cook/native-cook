package com.ngocdevv.nativecook.blocks.modals.stackingtoastfeed

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.max

val StackingToastMessages = listOf(
    ToastMessage(ToastKind.Error, "Error", "That password isn't right. Can we help you ", "recover your password", "?"),
    ToastMessage(ToastKind.Warning, "Warning", "Looks like you've exceeded your limit. ", "Need an upgrade?"),
    ToastMessage(ToastKind.Info, "Info", "We've updated a few things. Check the ", "patch notes", "!"),
    ToastMessage(ToastKind.Success, "Success", "Items added to cart. Yay!"),
)

// One loop, in seconds: four toasts arrive in turn, the pile collapses, then four swipes clear it.
const val LOOP = 10f
private val ArriveAt = listOf(0.7f, 1.5f, 2.4f, 3.3f)
private const val COLLAPSE_AT = 4.3f

private class Swipe(val at: Float, val direction: Int)

// Each swipe: when it starts and where it goes (1 right, -1 left).
private val Swipes = listOf(Swipe(5.5f, 1), Swipe(6.5f, -1), Swipe(7.1f, 1), Swipe(7.7f, -1))

// The spread is put back this long into the loop, when nothing is on screen.
private const val RESET_AT = 0.4f

// After a touch the loop waits this long, then starts again from the empty page.
private const val USER_HOLD = 3f

// A spring of 500 ms with damping ratio 0.9: stiffness (2π / 0.5)².
private const val COLLAPSE_DAMPING = 0.9f
private const val COLLAPSE_STIFFNESS = 158f
private const val MARGIN = 20
private val PageColor = Color(0xFFF2F2F4)
private val Ink = Color(0xFF111318)
private val Mute = Color(0xFF6E7380)
private val ShadowInk = Color(0xFF111318)

private fun photo(id: String, w: Int, h: Int = w) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=$h&q=80"

private class Experience(val title: String, val meta: String, val price: String, val photo: String)

// The shop page under the toasts: a featured ride and a short list of experiences.
private const val FEATURE = "photo-1641128324972-af3212f0f6bd"
private val Experiences = listOf(
    Experience("Night lights walking tour", "4.9 · 3 hours", "$48", "photo-1540959733332-eab4deabeeaf"),
    Experience("Five villages by boat", "4.8 · Full day", "$96", "photo-1516483638261-f4dbaf036963"),
    Experience("Pyramids at first light", "4.9 · 6 hours", "$72", "photo-1503177119275-0aa32b3a9368"),
    Experience("Lagoon snorkel morning", "4.7 · 4 hours", "$64", "photo-1573843981267-be1999ff37cd"),
)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

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
 * A page that collects toasts: each one slides in from above with a short shake and pushes the others
 * down, then the pile collapses into a stack and the toasts are swiped away one by one. Every
 * position comes from a toast's place in the list. By hand, tap the page to add one, swipe or tap the
 * x to remove one, long-press the page to collapse or spread the pile.
 *
 * @param autoPlay Adds, collapses and swipes away toasts by itself, in a loop, and leaves the page empty.
 * @param messages The messages a tap on the page goes through, one after the other.
 * @param limit Most toasts kept at once; the oldest leaves when another arrives.
 * @param onDismiss Called when a toast has left, by swipe or by its x.
 * @param onShow Called when a toast arrives.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StackingToastFeed(
    autoPlay: Boolean = false,
    messages: List<ToastMessage> = StackingToastMessages,
    limit: Int = 5,
    onDismiss: ((ToastMessage) -> Unit)? = null,
    onShow: ((ToastMessage) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val given by rememberUpdatedState(messages)
    val maximum by rememberUpdatedState(limit)
    val dismissed by rememberUpdatedState(onDismiss)
    val shown by rememberUpdatedState(onShow)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var items by remember { mutableStateOf(listOf<ToastItem>()) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    var counter by remember { mutableIntStateOf(0) }
    var next by remember { mutableIntStateOf(0) }
    var collapsed by remember { mutableStateOf(false) }
    // 0 spread out, 1 collapsed into a pile.
    val stack = remember { Channel(0f, scope) }

    fun setPile(value: Float) {
        collapsed = value == 1f
        if (reduced) stack.set(value) else stack.go { animateTo(value, spring(COLLAPSE_DAMPING, COLLAPSE_STIFFNESS)) }
    }

    fun push(message: ToastMessage) {
        counter += 1
        val id = counter
        shown?.invoke(message)
        val kept = items.filter { it.leaving == null }
        // The oldest toast makes room by leaving the way a tap on its x would.
        val surplus = max(kept.size + 1 - maximum, 0)
        val leaving = kept.takeLast(surplus).map { it.id }.toSet()
        items = listOf(ToastItem(id, message)) + items.map { if (it.id in leaving) it.leaving(0) else it }
    }

    fun leave(id: Int, direction: Int) {
        items = items.map { if (it.id == id && it.leaving == null) it.leaving(direction) else it }
    }

    fun gone(id: Int) {
        val left = items.firstOrNull { it.id == id }
        items = items.filter { it.id != id }
        if (left != null) dismissed?.invoke(left.message)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            // Back to the empty page, then the loop from its first second.
            items = items.map { if (it.leaving == null) it.leaving(0) else it }
            delay(800)
        }
        val steps = buildList {
            add(Step(RESET_AT) { setPile(0f) })
            ArriveAt.forEachIndexed { i, at -> add(Step(at) { push(given[i % given.size]) }) }
            add(Step(COLLAPSE_AT) { setPile(1f) })
            for (swipe in Swipes) {
                add(Step(swipe.at) { items.firstOrNull { it.leaving == null }?.let { leave(it.id, swipe.direction) } })
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

    // The status bar's icons are dark over the light page.
    val window = view.context.activity()?.window
    DisposableEffect(window) {
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }

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
        val insets = WindowInsets.systemBars.asPaddingValues()
        val bottom = insets.calculateBottomPadding()
        val top = insets.calculateTopPadding()

        Contours(maxWidth.value, maxHeight.value)

        Box(
            Modifier
                .fillMaxSize()
                .combinedClickable(
                    remember { MutableInteractionSource() }, indication = null,
                    onLongClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
                        setPile(if (collapsed) 0f else 1f)
                    },
                    onClick = {
                        view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                        push(messages[next % messages.size])
                        next += 1
                    },
                )
                .semantics { contentDescription = "Page" },
        ) {
            ShopPage(Modifier.padding(start = MARGIN.dp, end = MARGIN.dp, top = top + 4.dp))
            BasicText(
                "Tap to add a toast, hold to stack them",
                Modifier.align(Alignment.BottomCenter).padding(bottom = bottom + 20.dp),
                style = TextStyle(
                    color = Mute, fontSize = 13.sp, lineHeight = 18.sp, fontWeight = FontWeight.Medium, textAlign = TextAlign.Center,
                    lineHeightStyle = Centered, platformStyle = PlatformTextStyle(includeFontPadding = false),
                ),
            )
        }

        val place = items.filter { it.leaving == null }.mapIndexed { i, item -> item.id to i }.toMap()
        for (item in items) {
            key(item.id) {
                ToastCard(
                    item = item,
                    index = place[item.id] ?: 0,
                    stack = { stack.value },
                    width = maxWidth.value - MARGIN * 2,
                    screenWidth = maxWidth.value,
                    reduced = reducedMotion,
                    onLeave = ::leave,
                    onGone = ::gone,
                )
            }
        }
    }
}

private fun text(size: Float, lineHeight: Float, weight: FontWeight, color: Color, tracking: Float = 0f) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    letterSpacing = tracking.sp,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/** The shop page under the toasts. */
@Composable
private fun ShopPage(modifier: Modifier) {
    Column(modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Bottom) {
            Column(Modifier.weight(1f)) {
                BasicText("Good evening, Lena", style = text(15f, 20f, FontWeight.Medium, Mute))
                BasicText("Explore", style = text(34f, 40f, FontWeight.ExtraBold, Ink, -1f))
            }
            Box(Modifier.padding(bottom = 2.dp).size(44.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .dropShadow(CircleShape, Shadow(4.dp, ShadowInk.copy(alpha = 0.08f), offset = DpOffset(0.dp, 2.dp)))
                        .background(Color.White, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.ShoppingBag, null, Modifier.size(22.dp), tint = Ink)
                }
                Box(
                    Modifier
                        .align(Alignment.TopEnd)
                        .offset(2.dp, (-2).dp)
                        .size(18.dp)
                        .background(PageColor, CircleShape)
                        .padding(2.dp)
                        .background(Color(0xFFFF4D4F), CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText("2", style = text(10f, 12f, FontWeight.ExtraBold, Color.White))
                }
            }
        }
        Row(
            Modifier
                .padding(top = 16.dp)
                .fillMaxWidth()
                .height(44.dp)
                .dropShadow(RoundedCornerShape(14.dp), Shadow(1.5.dp, ShadowInk.copy(alpha = 0.06f), offset = DpOffset(0.dp, 1.dp)))
                .background(Color.White, RoundedCornerShape(14.dp))
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, Modifier.size(19.dp), tint = Mute)
            BasicText("Experiences, cities, tours", style = text(16f, 21f, FontWeight.Normal, Mute))
        }
        Box(
            Modifier
                .padding(top = 20.dp)
                .fillMaxWidth()
                .height(196.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFFD9A066)),
        ) {
            Photo(photo(FEATURE, 800, 440), Modifier.fillMaxSize())
            Box(Modifier.fillMaxSize().background(Brush.verticalGradient(0.35f to Color.Black.copy(alpha = 0f), 1f to Color.Black.copy(alpha = 0.55f))))
            Box(
                Modifier.padding(16.dp).height(28.dp).background(Color.White.copy(alpha = 0.92f), CircleShape).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("Trending", style = text(13f, 17f, FontWeight.Bold, Ink))
            }
            Column(Modifier.align(Alignment.BottomStart).padding(18.dp)) {
                BasicText("Sunrise balloon ride", style = text(22f, 27f, FontWeight.ExtraBold, Color.White, -0.5f))
                BasicText("Cappadocia · from $189", Modifier.padding(top = 2.dp), style = text(14f, 19f, FontWeight.Normal, Color.White.copy(alpha = 0.88f)))
            }
        }
        Row(
            Modifier.padding(top = 24.dp, bottom = 4.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicText("Popular near you", style = text(20f, 25f, FontWeight.ExtraBold, Ink, -0.4f))
            BasicText("See all", style = text(15f, 20f, FontWeight.SemiBold, Color(0xFF2F6BFF)))
        }
        val rule = Color(0x333C3C43)
        Experiences.forEachIndexed { i, item ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(80.dp)
                    .drawBehind { if (i > 0) drawRect(rule, size = size.copy(height = 1f)) },
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Photo(photo(item.photo, 160), Modifier.size(60.dp).clip(RoundedCornerShape(14.dp)))
                Column(Modifier.weight(1f)) {
                    BasicText(item.title, maxLines = 1, style = text(16f, 21f, FontWeight.SemiBold, Ink, -0.2f))
                    Row(Modifier.padding(top = 3.dp), horizontalArrangement = Arrangement.spacedBy(4.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.Star, null, Modifier.size(13.dp), tint = Color(0xFFF5A623))
                        BasicText(item.meta, style = text(13f, 17f, FontWeight.Normal, Mute))
                    }
                }
                BasicText(item.price, style = text(16f, 21f, FontWeight.Bold, Ink))
                Box(Modifier.size(30.dp).background(Ink, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Add, null, Modifier.size(20.dp), tint = Color.White)
                }
            }
        }
    }
}

/** A photo that fills its box, cropped, with nothing drawn until it has loaded. */
@Composable
private fun Photo(url: String, modifier: Modifier) {
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier)
}

