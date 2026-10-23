package com.ngocdevv.nativecook.blocks.sheets.orbitpickssheet

import androidx.activity.compose.LocalActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private fun portrait(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=240&h=240&q=80"

private val PROFILES = listOf(
    Profile(
        "Alex", "5.0", "32 · he/him", "Rugby", 80, Color(0xFFF1C7A3), Color(0xFF6A3F26), Color(0xFF2F5FB8),
        photo = portrait("1506794778202-cad84cf45f1d"), tags = listOf("Coffee", "Hiking", "Jazz"),
    ),
    Profile(
        "Taylor", "5.0", "29 · they/them", "Cycling", 76, Color(0xFFE8B48A), Color(0xFF1C1C24), Color(0xFF7A2430), glasses = true,
        photo = portrait("1531891437562-4301cf35b7e4"), tags = listOf("Film", "Vinyl", "Ramen"),
    ),
    Profile(
        "Morgan", "4.9", "27 · she/her", "Climbing", 71, Color(0xFFC98F66), Color(0xFF3A2118), Color(0xFF2B8A6E),
        photo = portrait("1487412720507-e7ab37603c6f"), tags = listOf("Snow", "Dogs", "Pop"),
    ),
)

/** The autoPlay loop, in seconds from its start. */
const val LOOP = 9.5f
private const val TAP_AT = 0.8f
private const val TAP_FOR = 0.15f
private const val SWIPE_AT = 7f
private const val CLOSE_AT = 8.1f
private const val REST_AT = 8.7f
// Seconds a touch keeps the loop away.
private const val USER_HOLD = 3f

private const val SCRIM = 0.55f
private val EDGE = Color(0xFF8F7BEF)
private const val SHEET_RADIUS = 28f
// The sheet is built taller than it shows, so a spring that overshoots does not lift it off the bottom.
private const val OVERSHOOT = 40f
private const val LOADER_Y = 290f
private const val BADGE_SIZE = 72f
private const val BADGE_SMALL = 40f
private const val BADGE_TOP_Y = 48f
private const val HALO = 132f
private val HALO_FILL = Color(0xFFEFEBFD)
private const val CAPTION_Y = LOADER_Y + 88f
private const val RESULT_TITLE_Y = 80f
private const val PAGER_TOP = 148f
private const val PAGER_HEIGHT = 432f
private val REFRESH = Color(0xFF9A9AAB)
private const val DOTS_GAP = 6f
private const val FINGER = 32f
private const val PAGE_SCALE = 0.94f
private const val PAGE_RADIUS = 24f
private const val PRESS_SCALE = 0.97f
private const val DISMISS_DRAG = 120f
private const val DISMISS_VELOCITY = 900f

// Reanimated's damping 18, stiffness 170 and mass 1, as a damping ratio.
private val RISE_SPRING = spring<Float>(dampingRatio = 18f / (2f * Math.sqrt(170.0).toFloat()), stiffness = 170f)
private const val DROP_MS = 400
private val DROP_EASING = CubicBezierEasing(0.5f, 0f, 1f, 1f)
private const val SWIPE_MS = 480
private const val FINGER_MS = 520
private const val SNAP_MS = 250
private const val FADE_MS = 200
private val CUBIC_OUT = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

/**
 * A discover page with a full-height sheet over it. Tapping the banner raises the sheet while the
 * page recedes behind it; a badge pops in, a ring draws round it and a dozen icon discs spiral in
 * and orbit it, until they are pulled into the badge, which rises and shrinks to become the
 * heading of a card of people to swipe through. One clock, in seconds since the tap, drives all
 * of it, so the loader plays the same way every time.
 *
 * @param profiles The people the sheet goes through; swipe the card sideways for the next one.
 * @param autoPlay Plays the whole interaction by itself, over and over, and gives way to a touch.
 * @param onProfileChange Called with the place of the profile on show whenever a swipe settles.
 */
@Composable
fun OrbitPicksSheet(
    profiles: List<Profile> = PROFILES,
    autoPlay: Boolean = false,
    title: String = "Discover",
    banner: String = "Today's Top Picks",
    bannerCaption: String = "Your personalized daily matches are ready to see!",
    loadingLabel: String = "Doing some magic...",
    resultTitle: String = "Today's Top Picks",
    resultCaption: String = "Three people picked for you today",
    chatLabel: String = "Chat",
    onChat: ((Profile) -> Unit)? = null,
    onProfileChange: ((Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion by rememberUpdatedState(rememberReducedMotion())
    val people by rememberUpdatedState(profiles)
    val chat by rememberUpdatedState(onChat)
    val settled by rememberUpdatedState(onProfileChange)
    val localDensity = LocalDensity.current
    val scope = rememberCoroutineScope()
    val topInset = with(localDensity) { WindowInsets.statusBars.getTop(this).toDp() }
    val bottomInset = with(localDensity) { WindowInsets.navigationBars.getBottom(this).toDp() }

    // 0 hidden, 1 up; the clock in seconds since the tap; the card on show, as a place in the list.
    val sheet = remember { Animatable(0f) }
    val clock = remember { Animatable(0f) }
    val finger = remember { Animatable(0f) }
    val bannerScale = remember { Animatable(1f) }
    var drag by remember { mutableFloatStateOf(0f) }
    var page by remember { mutableFloatStateOf(0f) }
    var pageFrom by remember { mutableFloatStateOf(0f) }
    var isOpen by remember { mutableStateOf(false) }
    var touches by remember { mutableIntStateOf(0) }
    var pageJob by remember { mutableStateOf<Job?>(null) }
    var dragJob by remember { mutableStateOf<Job?>(null) }
    var closeJob by remember { mutableStateOf<Job?>(null) }

    OrbitPicksSheetSystemBars(light = !isOpen)

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        touches++
    }

    fun open() {
        if (isOpen) return
        closeJob?.cancel()
        isOpen = true
        scope.launch {
            clock.snapTo(0f)
            if (reducedMotion) {
                launch { sheet.animateTo(1f, tween(FADE_MS)) }
                clock.snapTo(END)
            } else {
                launch { sheet.animateTo(1f, RISE_SPRING) }
                clock.animateTo(END, tween((END * 1000).toInt(), easing = LinearEasing))
            }
        }
    }

    fun close() {
        if (!isOpen) return
        isOpen = false
        closeJob = scope.launch {
            sheet.animateTo(0f, tween(if (reducedMotion) FADE_MS else DROP_MS, easing = DROP_EASING))
            // Out of sight now: the next opening starts from the beginning.
            clock.snapTo(0f)
            page = 0f
            drag = 0f
        }
    }

    fun tapBanner(down: Boolean) {
        scope.launch { bannerScale.animateTo(if (down) PRESS_SCALE else 1f, tween(if (down) 80 else 140)) }
    }

    fun animatePage(to: Float, ms: Int) {
        pageJob?.cancel()
        pageJob = scope.launch { animate(page, to, animationSpec = tween(ms, easing = CUBIC_OUT)) { value, _ -> page = value } }
    }

    /** What a finger does by itself: drags the card to the next profile. */
    fun swipe() {
        scope.launch {
            finger.snapTo(0f)
            if (!reducedMotion) finger.animateTo(1f, tween(FINGER_MS, easing = LinearEasing))
        }
        animatePage(1f, if (reducedMotion) FADE_MS else SWIPE_MS)
    }

    /** Everything back as it was at the start, with no animation. */
    fun rest() {
        closeJob?.cancel()
        pageJob?.cancel()
        dragJob?.cancel()
        scope.launch {
            for (value in listOf(sheet, clock, finger)) value.snapTo(0f)
            bannerScale.snapTo(1f)
        }
        drag = 0f
        page = 0f
        pageFrom = 0f
        isOpen = false
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        val steps = listOf(
            TAP_AT to { tapBanner(true) },
            TAP_AT + TAP_FOR to { tapBanner(false); open() },
            SWIPE_AT to { swipe() },
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

    BoxWithConstraints(modifier.fillMaxSize().background(INDIGO)) {
        val screenWidth = maxWidth
        val sheetHeight = maxHeight - topInset
        val centre = screenWidth.value / 2f
        val widthPx by rememberUpdatedState(with(localDensity) { screenWidth.toPx() })
        val sheetHeightPx by rememberUpdatedState(with(localDensity) { sheetHeight.toPx() })

        // The page, receding behind the sheet.
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val up = min(1f, sheet.value)
                    val scale = lerp(1f, PAGE_SCALE, up)
                    scaleX = scale
                    scaleY = scale
                    shape = RoundedCornerShape(lerp(0f, PAGE_RADIUS, up).dp)
                    clip = true
                },
        ) {
            PageBehind(
                top = topInset,
                bottom = bottomInset,
                title = title,
                banner = banner,
                bannerCaption = bannerCaption,
                bannerScale = { bannerScale.value },
                onBanner = {
                    touched()
                    open()
                },
                onBannerPress = { down ->
                    if (down) touched()
                    tapBanner(down)
                },
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer { alpha = SCRIM * min(1f, sheet.value) * (1f - ramp(drag, 0f, sheetHeightPx)) }
                    .background(INDIGO),
            )
        }

        // The sheet, built taller than it shows.
        Box(
            Modifier
                .offset(y = topInset)
                .size(screenWidth, sheetHeight + OVERSHOOT.dp)
                .graphicsLayer { translationY = (1f - sheet.value) * (sheetHeightPx + OVERSHOOT * density) + drag }
                .pointerInput(Unit) {
                    // One drag for both jobs: sideways over the cards turns the pager, downwards drags the sheet away.
                    val slop = 12.dp.toPx()
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val tracker = VelocityTracker()
                        tracker.addPosition(down.uptimeMillis, down.position)
                        var axis = ' '
                        var total = Offset.Zero
                        val cardsTop = PAGER_TOP * density
                        val last = (people.size - 1).toFloat()
                        while (true) {
                            val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                            if (change.changedToUp() || !change.pressed) break
                            tracker.addPosition(change.uptimeMillis, change.position)
                            total += change.position - change.previousPosition
                            if (axis == ' ' && total.getDistance() > slop) {
                                val sideways = abs(total.x) > abs(total.y)
                                val overCards = down.position.y > cardsTop && down.position.y < cardsTop + PAGER_HEIGHT * density
                                axis = if (sideways) (if (overCards) 'x' else 'n') else 'y'
                                if (axis == 'x') {
                                    pageJob?.cancel()
                                    pageFrom = page
                                }
                                if (axis == 'y') dragJob?.cancel()
                                if (axis != 'n') touched()
                            }
                            if (axis == 'x') {
                                page = min(last, max(0f, pageFrom - total.x / widthPx))
                                change.consume()
                            } else if (axis == 'y') {
                                drag = max(0f, total.y)
                                change.consume()
                            }
                        }
                        val velocity = tracker.calculateVelocity()
                        if (axis == 'x') {
                            // A flick carries the card on to the next one; a slow drag settles on the nearer one.
                            val target = min(last, max(0f, (page - velocity.x / widthPx / 2f).roundToInt().toFloat()))
                            animatePage(target, SNAP_MS)
                            settled?.invoke(target.toInt())
                        } else if (axis == 'y') {
                            if (drag > DISMISS_DRAG * density || velocity.y > DISMISS_VELOCITY * density) {
                                // From here the sheet is where the finger left it.
                                val from = 1f - drag / sheetHeightPx
                                drag = 0f
                                scope.launch {
                                    sheet.snapTo(from)
                                    close()
                                }
                            } else {
                                dragJob = scope.launch { animate(drag, 0f, animationSpec = RISE_SPRING) { value, _ -> drag = value } }
                            }
                        }
                    }
                },
        ) {
            Box(
                Modifier
                    .offset(x = 18.dp, y = (-10).dp)
                    .size(screenWidth - 36.dp, 60.dp)
                    .background(EDGE, RoundedCornerShape(SHEET_RADIUS.dp)),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        shape = RoundedCornerShape(topStart = SHEET_RADIUS.dp, topEnd = SHEET_RADIUS.dp)
                        clip = true
                    }
                    .background(Color.White),
            ) {
                Box(
                    Modifier
                        .align(Alignment.TopCenter)
                        .padding(top = 8.dp)
                        .size(40.dp, 5.dp)
                        .background(Color(0xFFE2E2EA), CircleShape),
                )
                Text(
                    loadingLabel,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset(y = CAPTION_Y.dp)
                        .graphicsLayer {
                            val t = clock.value
                            alpha = ramp(t, CAPTION_IN, CAPTION_IN + 0.4f) * (1f - ramp(t, CAPTION_OUT, CAPTION_OUT + 0.3f))
                        },
                    color = Color(0xFF6B6B7B),
                    fontSize = 16.sp,
                    lineHeight = 21.sp,
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                )

                Box(
                    Modifier
                        .offset(x = (centre - HALO / 2f).dp, y = (LOADER_Y - HALO / 2f).dp)
                        .size(HALO.dp)
                        .graphicsLayer {
                            val t = clock.value
                            val on = ramp(t, RING_IN - 0.2f, RING_IN + 0.3f) * (1f - ramp(t, RING_OUT, RING_OUT + 0.4f))
                            alpha = on
                            scaleX = lerp(0.6f, 1f, on)
                            scaleY = lerp(0.6f, 1f, on)
                        }
                        .background(HALO_FILL, CircleShape),
                )

                DISCS.forEachIndexed { index, spec -> Disc(spec, index, { clock.value }, centre, LOADER_Y) }
                Ring({ clock.value }, centre, LOADER_Y)

                Badge(
                    BADGE_SIZE.dp,
                    Modifier
                        .offset(x = (centre - BADGE_SIZE / 2f).dp, y = (LOADER_Y - BADGE_SIZE / 2f).dp)
                        .graphicsLayer {
                            val t = clock.value
                            val up = easeOut(ramp(t, BADGE_UP, BADGE_UP + BADGE_UP_FOR))
                            val scale = pop(ramp(t, BADGE_IN, BADGE_IN + BADGE_POP)) * lerp(1f, BADGE_SMALL / BADGE_SIZE, up)
                            translationY = lerp(0f, BADGE_TOP_Y - LOADER_Y, up) * density
                            scaleX = scale
                            scaleY = scale
                        },
                )

                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val t = easeOut(ramp(clock.value, RESULT_IN, RESULT_IN + RESULT_FOR))
                            alpha = t
                            translationY = lerp(40f, 0f, t) * density
                        },
                ) {
                    Text(
                        resultTitle,
                        modifier = Modifier.fillMaxWidth().offset(y = RESULT_TITLE_Y.dp),
                        color = Color(0xFF14141F),
                        fontSize = 24.sp,
                        lineHeight = 30.sp,
                        fontWeight = FontWeight.ExtraBold,
                        letterSpacing = (-0.6).sp,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        resultCaption,
                        modifier = Modifier.fillMaxWidth().offset(y = (RESULT_TITLE_Y + 34f).dp),
                        color = Color(0xFF7B7B8C),
                        fontSize = 15.sp,
                        lineHeight = 20.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )

                    Box(Modifier.offset(y = PAGER_TOP.dp).fillMaxWidth().size(screenWidth, PAGER_HEIGHT.dp)) {
                        people.forEachIndexed { index, profile ->
                            ProfileCard(
                                profile,
                                chatLabel,
                                Modifier
                                    .size(screenWidth, PAGER_HEIGHT.dp)
                                    .graphicsLayer { translationX = (index - page) * widthPx },
                            ) { if (isOpen) chat?.invoke(profile) }
                        }
                        Box(
                            Modifier
                                .offset(x = (centre - FINGER / 2f).dp, y = 220.dp)
                                .size(FINGER.dp)
                                .graphicsLayer {
                                    val f = finger.value
                                    alpha = ramp(f, 0f, 0.12f) * (1f - ramp(f, 0.8f, 1f))
                                    translationX = lerp(70f, -60f, easeOut(f)) * density
                                    val scale = lerp(1f, 0.85f, ramp(f, 0f, 0.2f))
                                    scaleX = scale
                                    scaleY = scale
                                }
                                .background(Color.White.copy(alpha = 0.7f), CircleShape)
                                .border(2.dp, Color(0xFF14141F).copy(alpha = 0.3f), CircleShape),
                        )
                    }

                    // The page dots: the one that is on show stretches.
                    Canvas(Modifier.offset(y = (PAGER_TOP + PAGER_HEIGHT + 20f).dp).fillMaxWidth().size(screenWidth, 6.dp)) {
                        val d = density
                        val widths = people.indices.map { index ->
                            lerp(6f, 16f, 1f - min(1f, abs(page - index))) * d
                        }
                        var x = (size.width - widths.sum() - DOTS_GAP * d * (widths.size - 1)) / 2f
                        widths.forEachIndexed { index, w ->
                            val near = 1f - min(1f, abs(page - index))
                            drawRoundRect(INDIGO, Offset(x, 0f), Size(w, 6f * d), CornerRadius(3f * d), alpha = lerp(0.25f, 1f, near))
                            x += w + DOTS_GAP * d
                        }
                    }
                    Row(
                        Modifier.offset(y = (PAGER_TOP + PAGER_HEIGHT + 44f).dp).fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                    ) {
                        Icon(Icons.Outlined.Schedule, null, tint = REFRESH, modifier = Modifier.size(14.dp))
                        Text("New picks in 14 h", color = REFRESH, fontSize = 13.sp, lineHeight = 17.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        }
    }
}

/**
 * The page behind the sheet is light until the sheet is up, so the status and navigation bar icons
 * follow it (the gallery's activity uses light ones) and are put back when the block closes.
 */
@Composable
private fun OrbitPicksSheetSystemBars(light: Boolean) {
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
