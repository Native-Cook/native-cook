package com.ngocdevv.nativecook.blocks.media.scattergridalbum

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.structuralEqualityPolicy
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

// Sized for the largest it is drawn at (132 × 176 open) at 3×.
private fun photo(id: String) = "https://images.unsplash.com/photo-$id?auto=format&fit=crop&w=396&h=528&q=80"

/** Eight photos in grid order: row by row, skipping the middle cell. */
private val DefaultPhotos = listOf(
    photo("1433086966358-54859d0ed716"),
    photo("1441974231531-c6227db76b6e"),
    photo("1454496522488-7a8e488e8606"),
    photo("1464822759023-fed622ff2c3b"),
    photo("1472214103451-9374bd1c798e"),
    photo("1506905925346-21bda4d32df4"),
    photo("1526772662000-3f88f10405ff"),
    photo("1531366936337-7c912a4589a7"),
)

private val IconLabels = listOf("Share", "Like", "Info", "Delete")
private const val HEART = 1

/** The name carries the heading's shadow twice, as in Swift (on the name, then on the whole heading). */
private val NameShadow = Color.Black.copy(alpha = 1f - (1f - HeadingShadow.alpha) * (1f - HeadingShadow.alpha))

/**
 * An album screen. Open, its photos lie scattered and tilted over a pale blue and mint gradient that
 * fills the screen, under the album's name; a tap folds it: the gradient shrinks into a square card,
 * the photos fly into a 3×3 grid on it around the name, and a row of icons (share, like, info,
 * delete) rises under it. A tap on the card unfolds it again.
 *
 * @param photos Eight photo addresses, in grid order (row by row, skipping the middle cell).
 * @param autoPlay Folds, likes, unlikes and unfolds by itself every [LOOP] seconds.
 */
@Composable
fun ScatterGridAlbum(
    name: String = "Highlands",
    year: String = "2025",
    photos: List<String> = DefaultPhotos,
    autoPlay: Boolean = false,
    onFoldChange: ((Boolean) -> Unit)? = null,
    onLike: ((Boolean) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    ScatterGridAlbumSystemBars()
    val still = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    // Read by the loop, the flows and the touch handlers, which outlive a recomposition.
    val latestStill by rememberUpdatedState(still)
    val folds by rememberUpdatedState(onFoldChange)
    val likes by rememberUpdatedState(onLike)

    // The clock in seconds since the block appeared (read only while drawing; the drift counts from
    // the same moment); the events made by hand; the autoPlay's loop start, null while a touch holds it off.
    val epoch = remember { System.nanoTime() }
    val clock = remember { mutableDoubleStateOf(0.0) }
    var hand by remember { mutableStateOf(Events()) }
    var loopStart by remember { mutableStateOf<Double?>(null) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // Keeps the clock running after a touch until everything is still.
    var awake by remember { mutableStateOf(false) }
    val images = remember { mutableStateListOf<ImageBitmap?>().apply { repeat(SLOTS.size) { add(null) } } }
    // The photos are in (or the wait for them is over): the autoPlay may start.
    var ready by remember { mutableStateOf(false) }
    // Open, the photos drift, so the clock runs; folded and still, it stops.
    val running = loopStart != null || awake || (!hand.folded && !still)

    fun time() = (System.nanoTime() - epoch) / 1e9

    fun eventsAt(now: Double): Events = loopStart?.let { autoEvents(now, it) } ?: hand

    // Changes only when something happens, not every frame.
    val current = remember { derivedStateOf(structuralEqualityPolicy()) { eventsAt(clock.doubleValue) } }
    val events = remember { { current.value } }
    val folded by remember { derivedStateOf { current.value.folded } }
    val heartOn by remember { derivedStateOf { current.value.heartOn } }

    /** Folded on screen now, whether by hand or by the autoPlay. */
    fun isFolded() = loopStart?.let { autoEvents(time(), it).folded } ?: hand.folded

    /** A touch takes over from the autoPlay loop (from what is on screen), which starts again later. */
    fun takeOver() {
        loopStart?.let {
            hand = autoEvents(time(), it)
            loopStart = null
        }
        awake = true
        touches++
    }

    fun like() {
        takeOver()
        val on = !hand.heartOn
        hand = hand.copy(heartOn = on, heartAt = time())
        if (on) {
            view.performHapticFeedback(
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.LONG_PRESS,
            )
        }
        likes?.invoke(on)
    }

    fun press(k: Int, down: Boolean) {
        takeOver()
        val now = time()
        hand = if (down) {
            hand.copy(downAt = hand.downAt.toMutableList().also { it[k] = now })
        } else {
            hand.copy(upAt = hand.upAt.toMutableList().also { it[k] = now })
        }
    }

    LaunchedEffect(running) {
        if (!running) return@LaunchedEffect
        while (true) withFrameNanos { clock.doubleValue = (it - epoch) / 1e9 }
    }

    LaunchedEffect(Unit) {
        snapshotFlow { folded }.drop(1).collect { folds?.invoke(it) }
    }

    LaunchedEffect(photos) {
        coroutineScope {
            photos.take(SLOTS.size).forEachIndexed { i, address ->
                launch { images[i] = loadScatterGridAlbumPhoto(context, address) }
            }
        }
        ready = true
    }
    LaunchedEffect(Unit) {
        delay((PRELOAD_WAIT * 1000).toLong())
        ready = true
    }

    LaunchedEffect(autoPlay, touches, ready) {
        if (!autoPlay) {
            // Let the clock stop once a touch has played out.
            if (touches == 0) return@LaunchedEffect
            delay((SETTLE * 1000).toLong())
            if (!anyPressed(hand)) awake = false
            return@LaunchedEffect
        }
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            if (anyPressed(hand)) return@LaunchedEffect
            // Back to the open album the way a tap on the card goes there, then the loop starts again.
            // Everything has been still for seconds, so the photos' order does not matter here.
            if (hand.folded) {
                val now = time()
                hand = hand.copy(from = snapshot(hand, IntArray(SLOTS.size) { it }, now, latestStill), folded = false, at = now)
                delay((SETTLE * 1000).toLong())
            }
        } else if (!ready) {
            // The first loop waits for the photos, so a recording never shows a grey card.
            return@LaunchedEffect
        }
        hand = Events()
        loopStart = time()
    }

    val measurer = rememberTextMeasurer()
    val paints = remember { ScatterGridAlbumPaints() }
    // Text is set in dp, like the rest of the block, so the system font scale does not move the layout.
    val nameText = remember(name, measurer, density) {
        measurer.measure(
            name,
            TextStyle(
                color = Color.White,
                fontSize = with(density) { NAME_SIZE.dp.toSp() },
                fontWeight = FontWeight.ExtraBold,
                letterSpacing = with(density) { NAME_TRACKING.dp.toSp() },
                shadow = Shadow(NameShadow, Offset.Zero, with(density) { HEADING_SHADOW_RADIUS.dp.toPx() }),
            ),
        )
    }
    val yearStyle = TextStyle(
        color = YearColor,
        fontWeight = FontWeight.SemiBold,
        shadow = Shadow(HeadingShadow, Offset.Zero, with(density) { HEADING_SHADOW_RADIUS.dp.toPx() }),
    )
    val yearText = remember(year, measurer, density) {
        measurer.measure(year, yearStyle.copy(fontSize = with(density) { YEAR_SIZE.dp.toSp() }))
    }
    val yearCellText = remember(year, measurer, density) {
        measurer.measure(year, yearStyle.copy(fontSize = with(density) { YEAR_FOLDED_SIZE.dp.toSp() }))
    }
    val text = HeadingText(nameText, yearText, yearCellText)

    BoxWithConstraints(modifier.fillMaxSize()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value.toDouble()
        val bottom = insets.calculateBottomPadding().value.toDouble()
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        val nameWidth = nameText.size.width / density.density.toDouble()
        val layout = remember(width, height, top, bottom, nameWidth) { layout(width, height, top, bottom, nameWidth) }
        val latestLayout by rememberUpdatedState(layout)

        fun toggle(fold: Boolean) {
            takeOver()
            val now = time()
            if (hand.folded == fold) return
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
            hand = hand.copy(from = snapshot(hand, latestLayout.ranks, now, latestStill), folded = fold, at = now)
        }

        fun tap(x: Double, y: Double) {
            val f = isFolded()
            // Open: a tap anywhere folds. Folded: only a tap on the card unfolds.
            if (f && !latestLayout.card.contains(x, y)) return
            toggle(!f)
        }

        val album = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures { tap(it.x / density.density.toDouble(), it.y / density.density.toDouble()) }
            }
            .semantics {
                contentDescription = "$name, $year"
                role = Role.Button
                onClick(label = if (folded) "Open the album" else "Fold the album into a grid") {
                    toggle(!isFolded())
                    true
                }
            }

        if (still) {
            // Reduced motion: the two layouts cross-fade in place.
            Box(album) {
                val fade = { fadeAmount(events(), clock.doubleValue) }
                Scene(layout, images, text, paints, card = { 1.0 }, photo = { 1.0 }, driftT = { null }, alpha = { if (fade() > 0) 1f else 0f })
                Scene(layout, images, text, paints, card = { 0.0 }, photo = { 0.0 }, driftT = { null }, alpha = { (1 - fade()).toFloat() })
            }
        } else {
            Box(album) {
                Scene(
                    layout,
                    images,
                    text,
                    paints,
                    card = { cardProgress(events(), clock.doubleValue) },
                    photo = { photoProgress(it, layout.ranks[it], events(), clock.doubleValue) },
                    driftT = { clock.doubleValue },
                    alpha = { 1f },
                )
            }
        }

        for (k in 0 until 4) {
            key(k) {
                Icon(
                    k = k,
                    center = layout.icons[k],
                    clock = clock,
                    events = events,
                    still = still,
                    folded = folded,
                    liked = k == HEART && heartOn,
                    onPress = { down -> press(k, down) },
                    onClick = { if (k == HEART) like() },
                )
            }
        }
    }
}

private class HeadingText(val name: TextLayoutResult, val year: TextLayoutResult, val yearCell: TextLayoutResult)

/**
 * The white screen, the gradient in its moving frame, the heading and the photos, posed by [card] and
 * [photo] (0 open, 1 folded). Everything is read while drawing, so the scene moves without recomposing.
 */
@Composable
private fun Scene(
    layout: Layout,
    images: List<ImageBitmap?>,
    text: HeadingText,
    paints: ScatterGridAlbumPaints,
    card: () -> Double,
    photo: (Int) -> Double,
    driftT: () -> Double?,
    alpha: () -> Float,
) {
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                val a = alpha()
                this.alpha = a
                compositingStrategy = if (a < 1f) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            },
    ) {
        // The screen, and the card's shadow under the solid card.
        Canvas(Modifier.fillMaxSize()) {
            drawRect(ScreenColor)
            val pose = cardPose(card(), layout)
            if (pose.shadow > 0) {
                scale(density, density, pivot = Offset.Zero) {
                    val r = pose.rect
                    val w = r.width.toFloat()
                    val h = r.height.toFloat()
                    val radius = pose.radius.toFloat()
                    paints.roundRect(this, CardShadow, CARD_SHADOW_SIGMA, r.x.toFloat(), r.y.toFloat() + CARD_SHADOW_Y, w, h, radius, pose.shadow.toFloat())
                    drawRoundRect(BaseColor, Offset(r.x.toFloat(), r.y.toFloat()), Size(w, h), CornerRadius(radius))
                }
            }
        }
        // The gradient, its edges going soft while it moves.
        Canvas(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val feather = cardPose(card(), layout).feather.toFloat()
                    renderEffect = if (feather > 0.2f && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val radius = sigmaToRadius(feather * density)
                        BlurEffect(radius, radius, TileMode.Decal)
                    } else {
                        null
                    }
                },
        ) {
            scale(density, density, pivot = Offset.Zero) { drawCardGradient(cardPose(card(), layout), paints) }
        }
        // The heading, then the photos over it.
        HeadingLine(text.year, layout, card) { it.yearTop to it.yearTopOpacity }
        HeadingLine(text.name, layout, card) { it.name to 1.0 }
        HeadingLine(text.yearCell, layout, card, scales = false) { it.yearCell to it.yearCellOpacity }
        Canvas(Modifier.fillMaxSize()) {
            val t = driftT()
            scale(density, density, pivot = Offset.Zero) {
                for (i in DRAW_ORDER) drawPhoto(images[i], photoPose(i, photo(i), layout, t), paints)
            }
        }
    }
}

/**
 * One line of the heading, set once and moved, shrunk and faded by [card] through [place] (its centre
 * and opacity). While it moves, it is drawn into a layer of its own and the layer is scaled, as Swift's
 * `scaleEffect` does, so the text is not set again at a new size every frame. At rest it is drawn
 * straight, so the folded name stays sharp.
 */
@Composable
private fun HeadingLine(
    line: TextLayoutResult,
    layout: Layout,
    card: () -> Double,
    scales: Boolean = true,
    place: (HeadingPose) -> Pair<Point, Double>,
) {
    val screen = LocalDensity.current
    // Room around the text for its shadow.
    val pad = with(screen) { (HEADING_SHADOW_RADIUS * 2).dp.roundToPx() }
    val w = line.size.width + pad * 2
    val h = line.size.height + pad * 2
    Canvas(
        Modifier
            // At the scene's top left (a Box stacks its children there); the layer moves it.
            .requiredSize(with(screen) { w.toDp() }, with(screen) { h.toDp() })
            .graphicsLayer {
                val c = card()
                val pose = headingPose(c, layout)
                val (at, opacity) = place(pose)
                val s = if (scales) pose.scale.toFloat() else 1f
                translationX = at.x.toFloat() * density - w / 2f
                translationY = at.y.toFloat() * density - h / 2f
                scaleX = s
                scaleY = s
                alpha = opacity.toFloat().coerceIn(0f, 1f)
                val moving = c > 0.001 && c < 0.999
                compositingStrategy = if (moving) CompositingStrategy.Offscreen else CompositingStrategy.Auto
            },
    ) {
        drawText(line, topLeft = Offset(pad.toFloat(), pad.toFloat()))
    }
}

/**
 * One icon under the card, in a 44 dp square: it fades in and rises as the album folds, shrinks while
 * pressed, and (the heart) fills, pops and bursts when liked.
 */
@Composable
private fun Icon(
    k: Int,
    center: Point,
    clock: State<Double>,
    events: () -> Events,
    still: Boolean,
    folded: Boolean,
    liked: Boolean,
    onPress: (Boolean) -> Unit,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val latestPress by rememberUpdatedState(onPress)
    LaunchedEffect(interaction) {
        interaction.interactions.collect {
            when (it) {
                is PressInteraction.Press -> latestPress(true)
                is PressInteraction.Release, is PressInteraction.Cancel -> latestPress(false)
            }
        }
    }
    fun shown(e: Events) = if (still) fadeAmount(e, clock.value) else iconProgress(k, e, clock.value)
    val live by remember(still) { derivedStateOf { events().folded && shown(events()) > 0.5 } }
    val paths = remember {
        val size = ICON_SIZE.toFloat()
        when (k) {
            0 -> listOf(sharePath(size))
            1 -> listOf(heartPath(size))
            2 -> listOf(infoPath(size), infoDotPath(size))
            else -> listOf(trashPath(size))
        }
    }
    val half = ICON_TARGET / 2
    Box(
        Modifier
            .offset((center.x - half).dp, (center.y - half).dp)
            .size(ICON_TARGET.dp)
            .graphicsLayer {
                val e = events()
                val s = shown(e)
                alpha = s.toFloat().coerceIn(0f, 1f)
                translationY = (if (still) 0.0 else ICON_RISE * (1 - s)).toFloat() * density
                val p = pressScale(k, e, clock.value).toFloat()
                scaleX = p
                scaleY = p
            }
            // Hidden from TalkBack, and not a button at all, while the album is open.
            .then(
                if (folded) {
                    Modifier
                        .semantics {
                            contentDescription = IconLabels[k]
                            if (k == HEART) selected = liked
                        }
                        .clickable(interactionSource = interaction, indication = null, enabled = live, role = Role.Button, onClick = onClick)
                } else {
                    Modifier.clearAndSetSemantics {}
                },
            ),
    ) {
        val paints = remember { ScatterGridAlbumPaints() }
        Canvas(Modifier.fillMaxSize()) {
            scale(density, density, pivot = Offset.Zero) {
                val c = half.toFloat()
                val o = c - ICON_SIZE.toFloat() / 2f
                if (k == HEART) {
                    val e = events()
                    val now = clock.value
                    // The dots fly out from the heart's centre, unscaled by its pop.
                    burst(e, now, still)?.let { drawBurst(it, Offset(c, c)) }
                    val fill = heartFill(e, now).toFloat()
                    val pop = heartScale(e, now, still).toFloat()
                    scale(pop, pop, pivot = Offset(c, c)) {
                        translate(o, o) {
                            paints.icon(this, paths[0], ICON_STROKE.toFloat())
                            drawPath(paths[0], IconColor, style = IconStroke)
                            if (fill > 0f) {
                                drawPath(paths[0], HeartColor, alpha = fill)
                                drawPath(paths[0], HeartColor, alpha = fill, style = IconStroke)
                            }
                        }
                    }
                } else {
                    translate(o, o) {
                        paints.icon(this, paths[0], ICON_STROKE.toFloat())
                        drawPath(paths[0], IconColor, style = IconStroke)
                        if (paths.size > 1) {
                            paints.icon(this, paths[1], null)
                            drawPath(paths[1], IconColor)
                        }
                    }
                }
            }
        }
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the block
 * is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun ScatterGridAlbumSystemBars() {
    val window = LocalActivity.current?.window ?: return
    val view = LocalView.current
    DisposableEffect(window, view) {
        val controller = WindowCompat.getInsetsController(window, view)
        val statusBars = controller.isAppearanceLightStatusBars
        val navigationBars = controller.isAppearanceLightNavigationBars
        controller.isAppearanceLightStatusBars = true
        controller.isAppearanceLightNavigationBars = true
        onDispose {
            controller.isAppearanceLightStatusBars = statusBars
            controller.isAppearanceLightNavigationBars = navigationBars
        }
    }
}
