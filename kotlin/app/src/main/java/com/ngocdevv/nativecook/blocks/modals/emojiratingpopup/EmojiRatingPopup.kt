package com.ngocdevv.nativecook.blocks.modals.emojiratingpopup

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationVector1D
import androidx.compose.animation.core.EaseInCubic
import androidx.compose.animation.core.EaseInOutQuad
import androidx.compose.animation.core.EaseOutQuad
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.BookmarkBorder
import androidx.compose.material.icons.outlined.ChatBubbleOutline
import androidx.compose.material.icons.rounded.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
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
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

// The timeline of one loop, in seconds: the dialog pops in, the stars appear, a finger runs along
// them, the comment is typed, Submit is pressed and the dialog leaves in a burst of hearts.
const val LOOP = 10f
private const val OPEN_AT = 0.8f
private val RateAt = listOf(2.4f, 2.9f, 3.4f, 3.9f, 4.4f)
private const val FOCUS_AT = 4.8f
private const val TYPE_AT = 4.9f
private const val TYPE_EVERY = 0.08f
private const val PRESS_AT = 6.2f
private const val SUBMIT_AT = 6.3f

// After a touch the loop waits this long, then starts again from the page.
private const val USER_HOLD = 3f

// A spring of 450 ms with damping ratio 0.75: stiffness (2π / 0.45)².
private const val OPEN_DAMPING = 0.75f
private const val OPEN_STIFFNESS = 195f
private const val CLOSE_MS = 350
private const val FADE_MS = 200
private const val CONTENT_AFTER = 700
private const val CONTENT_MS = 700
private const val FACE_MS = 150
private const val MOOD_MS = 250
private const val SQUASH_MS = 70
private const val BURST_MS = 800
private const val SUBMIT_COLOR_MS = 250
private const val SHRINK = 0.9f
private const val POP_FROM = 0.85f
private const val POP_RISE = 24f
private const val SCRIM_OPACITY = 0.6f

// The face for each number of stars (0 means none yet): the grin is the face at rest.
private val FaceOf = floatArrayOf(3f, 0f, 1f, 2f, 3f, 4f)

// The header's field and the wave over it, for none yet and for one to five stars.
private val Field = listOf(0xFFF7D774, 0xFFF29C9C, 0xFFF5BE8C, 0xFFF7D774, 0xFFC9E49A, 0xFF9AE3B5).map { Color(it) }
private val WaveColors = listOf(0xFFFBE59A, 0xFFF7B9B9, 0xFFF8D3AE, 0xFFFBE59A, 0xFFDCF0B8, 0xFFBDF0CF).map { Color(it) }

private const val DIALOG_WIDTH = 336f
private const val DIALOG_HEIGHT = 490f
private const val DIALOG_RADIUS = 32
private const val PADDING = 20
private const val BOX_HEIGHT = 84
private const val BUTTON_HEIGHT = 48
private const val FACE_TOP = 52f
private const val COPY_TOP = 146
private const val STARS_TOP = 228
private const val STARS_HEIGHT = 48
private const val LOWER_TOP = 290
private val Scrim = Color(0xFF3C4350)
private val Blue = Color(0xFF4AA3DF)
private val SubmitOn = Color(0xFF2F7DE1)
private val SubmitOff = Color(0xFF8A8A8A)
private val Ink = Color(0xFF111318)
private val Mute = Color(0xFF6E7380)
private val BoxEdge = Color(0xFFC9D6E4)
private val BoxFill = Color(0xFFF7F9FC)
private val StarYellow = Color(0xFFFFBE2E)
private val Like = Color(0xFFFF4D6A)
private val StoryRing = Color(0xFFFF7A59)
private val StorySeen = Color(0xFFD5D8DE)
private val CardShadow = Color(0xFF111318)
private const val MAX_COMMENT = 140
private val PageColor = Color(0xFFF3F4F7)

private fun photo(id: String, w: Int, h: Int = w) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$w&h=$h&q=80"

private class Story(val name: String, val photo: String)

private class Post(
    val name: String,
    val meta: String,
    val text: String,
    val avatar: String,
    val photo: String,
    val likes: String,
    val comments: String,
)

// The page behind the dialog: a row of stories and a feed of travel posts.
private val Stories = listOf(
    Story("You", "photo-1544005313-94ddf0286df2"),
    Story("Lena", "photo-1517841905240-472988babdf9"),
    Story("Marco", "photo-1531891437562-4301cf35b7e4"),
    Story("Aiko", "photo-1487412720507-e7ab37603c6f"),
    Story("Sam", "photo-1535713875002-d1d0cf377fde"),
)
private val Posts = listOf(
    Post(
        "Lena Fischer", "Oia, Santorini · 2h", "Golden hour from the cliffs. Worth every one of those steps.",
        "photo-1517841905240-472988babdf9", "photo-1570077188670-e3a8d69ac5ff", "1.2k", "84",
    ),
    Post(
        "Marco Bellini", "Manarola, Italy · 5h", "Five villages, one train pass and far too much focaccia.",
        "photo-1531891437562-4301cf35b7e4", "photo-1516483638261-f4dbaf036963", "962", "41",
    ),
)

private class BurstHeart(val dx: Float, val rise: Float, val size: Float)

// Six hearts leave the emoji: sideways reach, rise and size.
private val Hearts = listOf(
    BurstHeart(-64f, 96f, 16f), BurstHeart(-38f, 130f, 20f), BurstHeart(-12f, 104f, 14f),
    BurstHeart(14f, 138f, 18f), BurstHeart(40f, 100f, 22f), BurstHeart(66f, 120f, 15f),
)

// The dialog's two shadows: a tight one under it and a wide one further down.
private val DialogShadowNear = SoftRoundRect(Color(0xFF0E121C).copy(alpha = 0.12f), 5f)
private val DialogShadowFar = SoftRoundRect(Color(0xFF0E121C).copy(alpha = 0.32f), 28f)

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun ratingText(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    align: TextAlign = TextAlign.Unspecified,
    tracking: Float = 0f,
) = TextStyle(
    letterSpacing = tracking.sp,
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun stage(value: Float, start: Float, length: Float) = ((value - start) / length).coerceIn(0f, 1f)

private fun backOut(p: Float) = 1f + 2.70158f * (p - 1f).pow(3) + 1.70158f * (p - 1f).pow(2)

/** A straight mix in RGB, so a fading border keeps its colour. */
private fun mix(a: Color, b: Color, t: Float) =
    Color(a.red + (b.red - a.red) * t, a.green + (b.green - a.green) * t, a.blue + (b.blue - a.blue) * t, a.alpha + (b.alpha - a.alpha) * t)

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
 * A rating dialog over a page: an emoji that changes with the stars (a frown to heart eyes) under a
 * wave that goes from red to green, a comment box and Submit. Stars light up under a finger run along
 * them. Submit sends the dialog away in a burst of hearts, and the "Rate the app" button on the page
 * brings it back.
 *
 * @param autoPlay Runs the whole dialog by itself, in a loop, and leaves the page as it was.
 * @param sampleComment What the autoPlay run types in the box.
 * @param onSubmit Called with the stars (1 to 5) and the comment when Submit is pressed.
 */
@Composable
fun EmojiRatingPopup(
    autoPlay: Boolean = false,
    title: String = "How would you rate the overall user experience of our app?",
    subtitle: String = "Do you find the app easy to use?",
    commentLabel: String = "Can you tell us more?",
    placeholder: String = "We'd love to hear more!",
    cancelLabel: String = "Cancel",
    submitLabel: String = "Submit",
    sampleComment: String = "Great app!",
    onSubmit: ((stars: Int, comment: String) -> Unit)? = null,
    onCancel: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val reduced by rememberUpdatedState(reducedMotion)
    val submitted by rememberUpdatedState(onSubmit)
    val cancelled by rememberUpdatedState(onCancel)
    val sample by rememberUpdatedState(sampleComment)
    val view = LocalView.current
    val scope = rememberCoroutineScope()

    var stars by remember { mutableIntStateOf(0) }
    var comment by remember { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    var typing by remember { mutableStateOf(false) }
    var pressed by remember { mutableStateOf(false) }
    var isOpen by remember { mutableStateOf(false) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    // 0 is the page alone, 1 the dialog in place; the spring may pass 1.
    val open = remember { Channel(0f, scope) }
    val out = remember { Channel(0f, scope) }
    val content = remember { Channel(0f, scope) }
    val burst = remember { Channel(0f, scope) }
    val face = remember { Channel(FaceOf[0], scope) }
    val mood = remember { Channel(0f, scope) }
    val squash = remember { Channel(1f, scope) }
    val bounce = remember { Channel(1f, scope) }
    val active = remember { Channel(0f, scope) }
    val ready = remember { Channel(0f, scope) }

    val canSubmit = stars > 0 && comment.isNotBlank()

    LaunchedEffect(canSubmit) {
        ready.go { animateTo(if (canSubmit) 1f else 0f, tween(SUBMIT_COLOR_MS, easing = LinearEasing)) }
    }
    LaunchedEffect(focused || typing) {
        active.go { animateTo(if (focused || typing) 1f else 0f, tween(FADE_MS, easing = LinearEasing)) }
    }

    fun setRating(value: Int, byHand: Boolean) {
        if (value == stars) return
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)
        stars = value
        face.go { animateTo(FaceOf[value], tween(FACE_MS, easing = EaseInOutQuad)) }
        mood.go { animateTo(value.toFloat(), tween(MOOD_MS, easing = EaseOutQuad)) }
        if (!reduced) {
            squash.go {
                animateTo(0.88f, tween(SQUASH_MS, easing = EaseInOutQuad))
                animateTo(1f, spring(0.5f, 785f))
            }
        }
    }

    fun openDialog() {
        isOpen = true
        burst.set(0f)
        content.set(0f)
        out.set(0f)
        if (reduced) {
            open.go { animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) }
            content.go { animateTo(1f, tween(FADE_MS, easing = EaseInOutQuad)) }
            return
        }
        open.go { animateTo(1f, spring(OPEN_DAMPING, OPEN_STIFFNESS)) }
        content.go(CONTENT_AFTER) { animateTo(1f, tween(CONTENT_MS, easing = LinearEasing)) }
        bounce.go(100) {
            animateTo(1.2f, tween(160, easing = EaseInOutQuad))
            animateTo(1f, spring(0.5f, 439f))
        }
    }

    /** The page is back: the next opening starts from a blank dialog. */
    fun reset() {
        open.set(0f)
        out.set(0f)
        content.set(0f)
        face.set(FaceOf[0])
        mood.set(0f)
        stars = 0
        comment = ""
        focused = false
        typing = false
        isOpen = false
    }

    fun dismiss() {
        out.go {
            animateTo(1f, tween(if (reduced) FADE_MS else CLOSE_MS, easing = EaseInCubic))
            reset()
        }
    }

    fun submit() {
        if (!(stars > 0 && comment.isNotBlank())) return
        submitted?.invoke(stars, comment)
        if (!reduced) burst.go { animateTo(1f, tween(BURST_MS, easing = EaseOutQuad)) }
        dismiss()
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            pressed = false
            typing = false
            if (isOpen) dismiss()
            delay(700)
        }
        val text = sample
        val steps = buildList {
            add(Step(OPEN_AT) { openDialog() })
            RateAt.forEachIndexed { i, at -> add(Step(at) { setRating(i + 1, false) }) }
            add(Step(FOCUS_AT) { typing = true })
            for (i in text.indices) add(Step(TYPE_AT + i * TYPE_EVERY) { comment = text.substring(0, i + 1) })
            add(Step(PRESS_AT) { pressed = true })
            add(Step(SUBMIT_AT) {
                pressed = false
                submit()
            })
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

    val uiDensity = LocalDensity.current
    val ime = WindowInsets.ime

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
        val screenHeight = maxHeight.value
        val dialogWidth = min(DIALOG_WIDTH, maxWidth.value - 48f)
        val top = max(insetsTop + 12f, (screenHeight - DIALOG_HEIGHT) / 2f)

        Page(insetsTop + 8f, isOpen, onRate = ::openDialog)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = SCRIM_OPACITY * open.value.coerceIn(0f, 1f) * (1f - out.value) }
                .background(Scrim),
        )

        if (isOpen) {
            Box(
                Modifier
                    .offset(((maxWidth.value - dialogWidth) / 2f).dp, top.dp)
                    .size(dialogWidth.dp, DIALOG_HEIGHT.dp)
                    .graphicsLayer {
                        val o = open.value
                        val gone = out.value
                        val room = max(0f, top + DIALOG_HEIGHT - (screenHeight - ime.getBottom(uiDensity) / density) + 16f)
                        if (reduced) {
                            alpha = o.coerceIn(0f, 1f) * (1f - gone)
                            translationY = -room * density
                        } else {
                            alpha = min(1f, o * 3f) * (1f - gone)
                            translationY = ((1f - o) * POP_RISE - room) * density
                            val s = (POP_FROM + (1f - POP_FROM) * o) * (1f - (1f - SHRINK) * gone)
                            scaleX = s
                            scaleY = s
                        }
                    }
                    .drawBehind {
                        scale(density, density, pivot = Offset.Zero) {
                            drawIntoCanvas {
                                DialogShadowNear.draw(it.nativeCanvas, 0f, 4f, dialogWidth, DIALOG_HEIGHT, DIALOG_RADIUS.toFloat())
                                DialogShadowFar.draw(it.nativeCanvas, 0f, 24f, dialogWidth, DIALOG_HEIGHT, DIALOG_RADIUS.toFloat())
                            }
                            drawRoundRect(Color.White, Offset.Zero, androidx.compose.ui.geometry.Size(dialogWidth, DIALOG_HEIGHT), CornerRadius(DIALOG_RADIUS.toFloat()))
                        }
                    }
                    .clip(RoundedCornerShape(DIALOG_RADIUS.dp)),
            ) {
                Dialog(
                    dialogWidth, title, subtitle, commentLabel, placeholder, cancelLabel, submitLabel,
                    stars, comment, pressed, canSubmit, content, face, mood, squash, bounce, active, ready,
                    reduced = reducedMotion,
                    onRate = { setRating(it, true) },
                    onComment = { comment = it.take(MAX_COMMENT) },
                    onFocus = { focused = it },
                    onCancel = {
                        cancelled?.invoke()
                        dismiss()
                    },
                    onSubmit = ::submit,
                )
            }
        }

        // Outside the dialog, so the hearts outlive it: the burst is longer than the dialog's exit.
        for (heart in Hearts) {
            Icon(
                Icons.Filled.Favorite, null,
                Modifier
                    .offset((maxWidth.value / 2f - heart.size / 2f).dp, (top + FACE_TOP + FACE_SIZE / 2f - heart.size / 2f).dp)
                    .size((heart.size * 1.25f).dp)
                    .graphicsLayer {
                        val p = burst.value
                        alpha = if (p <= 0f) 0f else 1f - stage(p, 0.55f, 0.45f)
                        translationX = heart.dx * p * density
                        translationY = -heart.rise * p * density
                        val s = 0.5f + 0.5f * stage(p, 0f, 0.3f)
                        scaleX = s
                        scaleY = s
                    },
                tint = Color(0xFFFF4D6A),
            )
        }
    }
}

@Composable
private fun Page(top: Float, isOpen: Boolean, onRate: () -> Unit) {
    Column(
        Modifier
            .fillMaxSize()
            .then(if (isOpen) Modifier.clearAndSetSemantics {} else Modifier)
            .padding(start = 20.dp, end = 20.dp, top = top.dp),
    ) {
        Row(Modifier.fillMaxWidth().height(48.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText("Discover", style = ratingText(34f, 40f, FontWeight.ExtraBold, Ink, tracking = -1f))
            Row(
                Modifier
                    .height(36.dp)
                    .dropShadow(CircleShape, Shadow(4.dp, CardShadow.copy(alpha = 0.08f), offset = DpOffset(0.dp, 2.dp)))
                    .background(Color.White, CircleShape)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onRate)
                    .padding(horizontal = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Filled.Star, null, Modifier.size(17.dp), tint = StarYellow)
                BasicText("Rate the app", style = ratingText(15f, 20f, FontWeight.SemiBold, Ink, tracking = -0.2f))
            }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 16.dp), horizontalArrangement = Arrangement.SpaceBetween) {
            Stories.forEachIndexed { i, story ->
                Column(Modifier.width(64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(Modifier.size(64.dp).border(2.5.dp, if (i == 0) StorySeen else StoryRing, CircleShape).padding(5.5.dp)) {
                        Photo(photo(story.photo, 120), Modifier.fillMaxSize().clip(CircleShape))
                    }
                    BasicText(story.name, Modifier.padding(top = 6.dp), maxLines = 1, style = ratingText(12f, 16f, FontWeight.Medium, Ink))
                }
            }
        }
        for (post in Posts) PostCard(post)
    }
}

@Composable
private fun PostCard(post: Post) {
    val shape = RoundedCornerShape(26.dp)
    Column(
        Modifier
            .padding(bottom = 12.dp)
            .fillMaxWidth()
            .dropShadow(shape, Shadow(1.dp, CardShadow.copy(alpha = 0.04f), offset = DpOffset(0.dp, 1.dp)))
            .dropShadow(shape, Shadow(12.dp, CardShadow.copy(alpha = 0.06f), offset = DpOffset(0.dp, 8.dp)))
            .background(Color.White, shape)
            .padding(16.dp),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Photo(photo(post.avatar, 96), Modifier.size(40.dp).clip(CircleShape))
            Column(Modifier.weight(1f)) {
                BasicText(post.name, style = ratingText(15f, 20f, FontWeight.Bold, Ink, tracking = -0.2f))
                BasicText(post.meta, style = ratingText(13f, 17f, FontWeight.Normal, Mute))
            }
            Icon(Icons.Rounded.MoreHoriz, null, Modifier.size(22.dp), tint = Mute)
        }
        BasicText(post.text, Modifier.padding(top = 12.dp), style = ratingText(15f, 21f, FontWeight.Normal, Ink))
        Photo(photo(post.photo, 720, 440), Modifier.padding(top = 12.dp).fillMaxWidth().height(188.dp).clip(RoundedCornerShape(18.dp)))
        Row(Modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
            val count = ratingText(14f, 18f, FontWeight.SemiBold, Ink)
            Icon(Icons.Filled.Favorite, null, Modifier.size(21.dp), tint = Like)
            BasicText(post.likes, style = count)
            Icon(Icons.Outlined.ChatBubbleOutline, null, Modifier.padding(start = 12.dp).size(19.dp), tint = Ink)
            BasicText(post.comments, style = count)
            Spacer(Modifier.weight(1f))
            Icon(Icons.Outlined.BookmarkBorder, null, Modifier.size(21.dp), tint = Ink)
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

@Composable
private fun Dialog(
    width: Float,
    title: String,
    subtitle: String,
    commentLabel: String,
    placeholder: String,
    cancelLabel: String,
    submitLabel: String,
    stars: Int,
    comment: String,
    pressed: Boolean,
    canSubmit: Boolean,
    content: Channel,
    face: Channel,
    mood: Channel,
    squash: Channel,
    bounce: Channel,
    active: Channel,
    ready: Channel,
    reduced: Boolean,
    onRate: (Int) -> Unit,
    onComment: (String) -> Unit,
    onFocus: (Boolean) -> Unit,
    onCancel: () -> Unit,
    onSubmit: () -> Unit,
) {
    val intro = { stage(content.value, 0f, 0.5f) }
    val introRise: androidx.compose.ui.graphics.GraphicsLayerScope.() -> Unit = {
        val p = intro()
        alpha = p
        translationY = if (reduced) 0f else (1f - p) * 8f * density
    }
    Box(Modifier.fillMaxSize()) {
        Wave(width.dp, { mood.value }, Field, WaveColors)
        Mood(
            { face.value },
            Modifier
                .offset((width / 2f - FACE_SIZE / 2f).dp, FACE_TOP.dp)
                .graphicsLayer {
                    scaleX = bounce.value * (2f - squash.value)
                    scaleY = bounce.value * squash.value
                },
        )

        Column(
            Modifier.offset(24.dp, COPY_TOP.dp).width((width - 48f).dp).graphicsLayer(introRise),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            BasicText(title, style = ratingText(19f, 25f, FontWeight.Bold, Ink, TextAlign.Center, -0.4f))
            BasicText(subtitle, Modifier.padding(top = 6.dp), style = ratingText(15f, 20f, FontWeight.Normal, Mute, TextAlign.Center))
        }

        Row(
            Modifier
                .offset(((width - starsWidth(5)) / 2f).dp, STARS_TOP.dp)
                .height(STARS_HEIGHT.dp)
                .pointerInput(Unit) {
                    val step = (STAR_SIZE + STAR_GAP) * density
                    fun place(x: Float) = (floor(x / step).toInt() + 1).coerceIn(1, 5)
                    awaitEachGesture {
                        val down = awaitFirstDown()
                        onRate(place(down.position.x))
                        drag(down.id) { change ->
                            onRate(place(change.position.x))
                            change.consume()
                        }
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(STAR_GAP.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            for (index in 0 until 5) {
                RatingStar(
                    lit = stars >= index + 1,
                    appear = {
                        if (reduced) {
                            if (content.value > 0f) 1f else 0f
                        } else {
                            backOut(stage(content.value * (CONTENT_MS / 1000f), 0.1f + index * 0.06f, 0.35f))
                        }
                    },
                    reduced = reduced,
                )
            }
        }

        Column(Modifier.offset(PADDING.dp, LOWER_TOP.dp).width((width - PADDING * 2).dp).graphicsLayer(introRise)) {
            val button = RoundedCornerShape(14.dp)
            BasicText(commentLabel, Modifier.padding(bottom = 8.dp), style = ratingText(15f, 20f, FontWeight.SemiBold, Ink, tracking = -0.2f))
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(BOX_HEIGHT.dp)
                    .background(BoxFill, button)
                    .drawBehind {
                        val w = 1.5.dp.toPx()
                        drawRoundRect(
                            mix(BoxEdge, Blue, active.value), Offset(w / 2, w / 2),
                            androidx.compose.ui.geometry.Size(size.width - w, size.height - w), CornerRadius(14.dp.toPx() - w / 2), Stroke(w),
                        )
                    }
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                BasicTextField(
                    comment, onComment,
                    Modifier.fillMaxSize().onFocusChanged { onFocus(it.isFocused) }.semantics { contentDescription = commentLabel },
                    textStyle = ratingText(15f, 20f, FontWeight.Normal, Ink),
                    cursorBrush = SolidColor(Blue),
                    decorationBox = { field ->
                        if (comment.isEmpty()) BasicText(placeholder, style = ratingText(15f, 20f, FontWeight.Normal, Color(0xFF999999)))
                        field()
                    },
                )
            }
            Row(Modifier.padding(top = 16.dp).fillMaxWidth().height(BUTTON_HEIGHT.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .border(1.5.dp, Blue, button)
                        .clip(button)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button, onClick = onCancel),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(cancelLabel, style = ratingText(16f, 21f, FontWeight.SemiBold, Ink, tracking = -0.2f))
                }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .graphicsLayer {
                            val s = if (pressed) 0.96f else 1f
                            scaleX = s
                            scaleY = s
                        }
                        .clip(button)
                        .drawBehind { drawRect(mix(SubmitOff, SubmitOn, ready.value)) }
                        .clickable(remember { MutableInteractionSource() }, enabled = canSubmit, indication = null, role = Role.Button, onClick = onSubmit),
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(submitLabel, style = ratingText(16f, 21f, FontWeight.SemiBold, Color.White, tracking = -0.2f))
                }
            }
        }
    }
}
