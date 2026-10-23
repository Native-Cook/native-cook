package com.ngocdevv.nativecook.blocks.cards.moodcardshuffle

import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.input.pointer.util.addPointerInputChange
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.size.Size
import coil3.toBitmap
import com.ngocdevv.nativecook.shared.PRESS_SCALE
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * @param region Small caps line above the name, e.g. "Yên Bái · Vietnam".
 * @param blurb One sentence about the place; it is set on at most two lines.
 * @param photo An Unsplash photo id (`photo-…`), landscape.
 */
data class MoodCardShufflePlace(val name: String, val region: String, val blurb: String, val photo: String)

/** A mood and its places: the deck always holds four cards (fewer are repeated). */
data class MoodCardShuffleMood(val label: String, val places: List<MoodCardShufflePlace>)

private val Ink = Color(0xFF11110D)
private val Sans = FontFamily.SansSerif

private fun photoUrl(id: String, width: Int) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=$width&q=80"

private val Moods = listOf(
    MoodCardShuffleMood(
        "Quiet",
        listOf(
            MoodCardShufflePlace("Mù Cang Chải", "Yên Bái · Vietnam", "Layered rice terraces, misty hills and slow mornings in the highlands.", "photo-1609412058473-c199497c3c5d"),
            MoodCardShufflePlace("Black Forest", "Baden · Germany", "Fog-soft spruce trails, wood-fired saunas and nobody in a hurry.", "photo-1732130318657-c8740c0f5215"),
            MoodCardShufflePlace("Blue Ridge", "Virginia · USA", "Ridge after ridge fading into blue, best seen from a porch at dusk.", "photo-1552152370-fb05b25ff17d"),
            MoodCardShufflePlace("Trà Quế", "Quảng Nam · Vietnam", "Bicycle lanes through the herb gardens, lanterns once the sun goes down.", "photo-1608335715837-1994a535d5c3"),
        ),
    ),
    MoodCardShuffleMood(
        "Nature",
        listOf(
            MoodCardShufflePlace("Hoàng Su Phì", "Hà Giang · Vietnam", "Terraces stacked like contour lines, bright green before the harvest.", "photo-1480996408299-fc0e830b5db1"),
            MoodCardShufflePlace("Hoh Valley", "Washington · USA", "Moss-hung rainforest and rivers the colour of glacier milk.", "photo-1542273917363-3b1817f69a2d"),
            MoodCardShufflePlace("Central Otago", "South Island · New Zealand", "Rolling hills, open roads and quiet valleys stretching into the distance.", "photo-1612441804231-77a36b284856"),
            MoodCardShufflePlace("Bieszczady", "Podkarpacie · Poland", "Cloud pouring through old beech forest along the eastern border.", "photo-1488330890490-c291ecf62571"),
        ),
    ),
    MoodCardShuffleMood(
        "Artisanal Food",
        listOf(
            MoodCardShufflePlace("Sa Pa", "Lào Cai · Vietnam", "Hand-milled sticky rice, smoked pork and corn wine at the Sunday market.", "photo-1711060266983-92bd378c850c"),
            MoodCardShufflePlace("Banaue", "Ifugao · Philippines", "Heirloom red rice grown on terraces two thousand years old.", "photo-1548710332-3e03e8220a79"),
            MoodCardShufflePlace("Jatiluwih", "Bali · Indonesia", "Farm kitchens cooking straight from the fields outside the window.", "photo-1715755455989-76413081ad10"),
            MoodCardShufflePlace("Campo Imperatore", "Abruzzo · Italy", "Shepherds' pecorino, saffron and lentils from the high plains.", "photo-1601281426795-9fedf41df8d1"),
        ),
    ),
    MoodCardShuffleMood(
        "Wild",
        listOf(
            MoodCardShufflePlace("Dolomites", "South Tyrol · Italy", "Limestone spires, iron-rung climbs and huts above the clouds.", "photo-1611080402167-ed75bae6df32"),
            MoodCardShufflePlace("Julian Alps", "Triglav · Slovenia", "Glacial valleys, emerald rivers and trails with no signal at all.", "photo-1610552050890-fe99536c2615"),
            MoodCardShufflePlace("Tongass", "Alaska · USA", "The wettest, wildest rainforest in the north, and bears on the shore.", "photo-1476362555312-ab9e108a0b7e"),
            MoodCardShufflePlace("Huayhuash", "Áncash · Peru", "Ten days of passes over 4,500 m, dawn turning the peaks pink.", "photo-1500964757637-c85e8a162699"),
        ),
    ),
)

/** Every mood padded (or trimmed) to exactly four places. */
private fun normalise(moods: List<MoodCardShuffleMood>): List<MoodCardShuffleMood> {
    val list = moods.filter { it.places.isNotEmpty() }
    val safe = list.ifEmpty { Moods }
    return safe.map { m -> MoodCardShuffleMood(m.label, List(COUNT) { m.places[it % m.places.size] }) }
}

private val PillStiffness = stiffnessFor(PILL_DURATION, PILL_DAMPING_RATIO.toDouble())

/**
 * A mood-based place finder: a deck of photo cards over a blurred copy of the front one. Swiping
 * turns the deck like a drum: the front card swings out to the side and tucks in behind while the
 * next one swings in and straightens up. The mood chips in the sheet below riffle the deck into a
 * pile and deal out another set of places. The deck and the backdrop are drawn in one canvas, each
 * card through the same projective 3 × 3 matrix as the RN version, from an engine stepped once a
 * frame; nothing recomposes while it moves.
 *
 * @param autoPlay Turns cards and changes the mood twice, on a loop of 14 seconds.
 * @param onMoodChange Called with a mood's label when the deck is reshuffled to it.
 */
@Composable
fun MoodCardShuffle(
    modifier: Modifier = Modifier,
    moods: List<MoodCardShuffleMood> = Moods,
    title: String = "Where do you feel\nlike going?",
    autoPlay: Boolean = false,
    onMoodChange: ((String) -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val changed by rememberUpdatedState(onMoodChange)
    val list = remember(moods) { normalise(moods) }
    val engine = remember { MoodCardShuffleEngine() }
    val photos = remember { MoodCardShufflePhotos() }
    val painter = remember { MoodCardShufflePainter() }
    var chosen by remember { mutableIntStateOf(0) }
    // Bumped every frame; only the canvas reads it, so a frame redraws without recomposing.
    var tick by remember { mutableLongStateOf(0L) }

    engine.reduced = reduced
    engine.autoPlay = autoPlay
    engine.onLight = { view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK) }
    engine.onSelect = { view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK) }
    engine.onAnnounce = { next, _ ->
        chosen = next
        list.getOrNull(next)?.let { changed?.invoke(it.label) }
    }

    val insets = WindowInsets.safeDrawing
    val safeTop = insets.getTop(density) / density.density
    val safeBottom = insets.getBottom(density) / density.density

    // Every photo of every mood, fetched once (800 px wide); each gets its blurred backdrop as it arrives.
    LaunchedEffect(list, context) {
        val loader = SingletonImageLoader.get(context)
        for (id in list.flatMap { m -> m.places.map { it.photo } }.distinct()) {
            if (photos.cards.containsKey(id)) continue
            launch {
                val request = ImageRequest.Builder(context)
                    .data(photoUrl(id, CARD_PHOTO_PX))
                    .allowHardware(false)
                    .size(Size.ORIGINAL)
                    .build()
                val result = loader.execute(request) as? SuccessResult ?: return@launch
                val made = withContext(Dispatchers.Default) {
                    runCatching {
                        val bitmap = result.image.toBitmap()
                        if (bitmap.width <= 0 || bitmap.height <= 0) return@runCatching null
                        bitmap.setHasMipMap(true)
                        bitmap to moodCardShuffleBackdrop(bitmap)
                    }.getOrNull()
                } ?: return@launch
                photos.cards[id] = made.first
                made.second?.let { photos.blurs[id] = it }
                photos.at[id] = engine.elapsed
            }
        }
    }

    LaunchedEffect(engine) {
        var last = -1L
        while (isActive) {
            withFrameNanos { now ->
                val dt = if (last < 0) 1.0 / 60.0 else (now - last) / 1e9
                last = now
                engine.step(dt, photos.at)
                tick += 1
            }
        }
    }

    var rootY by remember { mutableFloatStateOf(0f) }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color(0xFF2A2820))
            .onGloballyPositioned { rootY = it.positionInRoot().y },
    ) {
        val width = maxWidth.value.toDouble()
        val height = maxHeight.value.toDouble()
        engine.stageWidth = width
        engine.stageHeight = height
        val cardW = cardSizeFor(width).first
        val key = "$cardW|" + list.joinToString("|") { m -> m.places.joinToString("/") { "${it.name}/${it.region}/${it.blurb}/${it.photo}" } }
        if (engine.facesKey != key) {
            engine.facesKey = key
            engine.faces = list.map { m -> m.places.map { painter.face(it, cardW) } }
        }

        Canvas(Modifier.fillMaxSize()) {
            tick
            drawIntoCanvas { canvas ->
                val nc = canvas.nativeCanvas
                nc.save()
                nc.scale(density.density, density.density)
                painter.drawStage(nc, engine, photos, size.width / density.density, size.height / density.density, density.density)
                nc.restore()
            }
        }

        Column(Modifier.fillMaxSize()) {
            Column(
                Modifier.weight(1f).fillMaxWidth().padding(top = (safeTop + 30f).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                BasicText(
                    title,
                    modifier = Modifier.fillMaxWidth().semantics { heading() },
                    style = TextStyle(
                        color = Color.White,
                        fontFamily = Sans,
                        fontWeight = FontWeight.Medium,
                        fontSize = 30.sp,
                        lineHeight = 36.sp,
                        letterSpacing = (-0.4).sp,
                        textAlign = TextAlign.Center,
                        shadow = Shadow(Color.Black.copy(alpha = 0.18f), Offset(0f, density.density), 12f * density.density),
                        lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
                    ),
                )
                Deck(engine, list, chosen, rootY, Modifier.weight(1f).fillMaxWidth().heightIn(min = 280.dp))
                Row(
                    Modifier
                        .height(44.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.14f))
                        .border((1f / density.density).dp, Color.White.copy(alpha = 0.45f), CircleShape)
                        .padding(horizontal = 20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    // The glyph fills about 17 of the icon's 24 units: 16 dp draws it about 12 dp wide, like the SF Symbol
                    // that SymbolView fits (with its padding) into 15 pt.
                    Icon(Icons.Filled.Search, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(16.dp))
                    BasicText(
                        "Search places…",
                        style = TextStyle(color = Color.White.copy(alpha = 0.92f), fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                    )
                }
                Spacer(Modifier.height(24.dp))
            }
            Sheet(
                labels = list.map { it.label },
                chosen = chosen,
                reduced = reduced,
                bottomPadding = max(safeBottom, 16f) + 14f,
                onChip = { index ->
                    engine.touched()
                    engine.choose(index, user = true)
                },
                onSurprise = {
                    engine.touched()
                    engine.surprise(chosen)
                },
            )
        }
    }
}

/** The deck's touch area: drags turn the deck, taps hit the bookmark or a peeking card. */
@Composable
private fun Deck(engine: MoodCardShuffleEngine, list: List<MoodCardShuffleMood>, chosen: Int, rootY: Float, modifier: Modifier) {
    val density = LocalDensity.current
    val label = list.getOrNull(chosen)?.label.orEmpty()
    Box(
        modifier
            .onGloballyPositioned {
                engine.deckTop = ((it.positionInRoot().y - rootY) / density.density).toDouble()
                engine.deckHeight = (it.size.height / density.density).toDouble()
            }
            .semantics { contentDescription = "$label places. Swipe to turn the deck." }
            .pointerInput(engine) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val start = down.position
                    val tracker = VelocityTracker()
                    tracker.addPointerInputChange(down)
                    // 0 undecided, 1 a pan, 2 the pan failed (a tap may still land), 3 a second finger: nothing.
                    var mode = 0
                    var released = false
                    while (!released) {
                        val event = awaitPointerEvent()
                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                        if (mode == 0 && event.changes.count { it.pressed } > 1) mode = 3
                        tracker.addPointerInputChange(change)
                        val dx = (change.position.x - start.x) / density.density
                        val dy = (change.position.y - start.y) / density.density
                        if (mode == 0) {
                            if (abs(dy) >= PAN_FAIL) {
                                mode = 2
                            } else if (abs(dx) >= PAN_SLOP) {
                                mode = 1
                                engine.panStart()
                            }
                        }
                        if (mode == 1) {
                            change.consume()
                            engine.panUpdate(dx.toDouble())
                        }
                        if (!change.pressed) {
                            released = true
                            if (mode == 1) {
                                val v = tracker.calculateVelocity().x / density.density
                                engine.panEnd(if (v.isFinite()) v.toDouble() else 0.0)
                            } else if ((mode == 0 || mode == 2) && change.uptimeMillis - down.uptimeMillis <= TAP_MS) {
                                engine.tap(
                                    (change.position.x / density.density).toDouble(),
                                    (change.position.y / density.density).toDouble() + engine.deckTop,
                                )
                            }
                        }
                    }
                    // The touch was taken away (cancelled): land the deck.
                    if (!released && mode == 1) engine.panEnd(0.0)
                }
            },
    )
}

/** The white sheet: the mood chips with a black pill sliding to the chosen one, and "Go Unexpected". */
@Composable
private fun Sheet(
    labels: List<String>,
    chosen: Int,
    reduced: Boolean,
    bottomPadding: Float,
    onChip: (Int) -> Unit,
    onSurprise: () -> Unit,
) {
    val density = LocalDensity.current
    val scroll = rememberScrollState()
    val frames = remember { mutableStateMapOf<Int, Pair<Float, Float>>() }
    val pillX = remember { Animatable(0f) }
    val pillW = remember { Animatable(0f) }
    var viewport by remember { mutableFloatStateOf(0f) }
    val frame = frames[chosen]
    val shadow = remember(density) { SoftRoundRect(Color.Black.copy(alpha = 0.12f), 15f * density.density) }

    LaunchedEffect(chosen, frame) {
        val f = frame ?: return@LaunchedEffect
        if (pillW.value == 0f || reduced) {
            pillX.snapTo(f.first)
            pillW.snapTo(f.second)
        } else {
            val pill = spring<Float>(dampingRatio = PILL_DAMPING_RATIO, stiffness = PillStiffness)
            launch { pillX.animateTo(f.first, pill) }
            launch { pillW.animateTo(f.second, pill) }
        }
        // The row scrolls to keep the chosen chip in view.
        if (viewport > 0f) {
            val target = ((f.first + f.second / 2f - viewport / 2f) * density.density).coerceAtLeast(0f)
            scroll.animateScrollTo(target.coerceAtMost(scroll.maxValue.toFloat()).roundToInt(), tween(300, easing = FastOutSlowInEasing))
        }
    }

    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawIntoCanvas {
                    val r = 30.dp.toPx()
                    shadow.draw(it.nativeCanvas, 0f, (-8).dp.toPx(), size.width, size.height + r, r)
                }
            }
            .clip(RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp))
            .background(Color.White)
            .padding(top = 20.dp, bottom = bottomPadding.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .onSizeChanged { viewport = it.width / density.density }
                .horizontalScroll(scroll)
                .padding(horizontal = 20.dp),
        ) {
            Box(
                Modifier
                    .offset { IntOffset((pillX.value * density.density).roundToInt() - 20.dp.roundToPx(), 0) }
                    .graphicsLayer { alpha = if (pillW.value > 0f) 1f else 0f }
                    .width(pillW.value.dp)
                    .height(44.dp)
                    .clip(CircleShape)
                    .background(Ink),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                labels.forEachIndexed { index, label ->
                    val on = index == chosen
                    Pressable(
                        onClick = { onChip(index) },
                        outer = Modifier
                            .onPlaced {
                                // x from the start of the padded row, as RN measures it.
                                frames[index] = (it.positionInParent().x / density.density + 20f) to it.size.width / density.density
                            }
                            .semantics { selected = on },
                        modifier = Modifier
                            .height(44.dp)
                            .border(1.dp, if (on) Color.Transparent else Color(17, 17, 13).copy(alpha = 0.12f), CircleShape)
                            .padding(horizontal = 21.dp),
                    ) {
                        BasicText(
                            label,
                            style = TextStyle(color = if (on) Color.White else Ink, fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 15.sp),
                        )
                    }
                }
            }
        }

        Pressable(onClick = onSurprise, modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText(
                        "Go Unexpected",
                        style = TextStyle(color = Ink, fontFamily = Sans, fontWeight = FontWeight.SemiBold, fontSize = 21.sp, letterSpacing = (-0.3).sp),
                    )
                    BasicText(
                        "Let the deck pick a mood for you",
                        style = TextStyle(color = Color(17, 17, 13).copy(alpha = 0.55f), fontFamily = Sans, fontWeight = FontWeight.Medium, fontSize = 13.sp),
                    )
                }
                Box(Modifier.size(44.dp).clip(CircleShape).background(Ink), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Shuffle, contentDescription = "Shuffle", tint = Color.White, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

/** A control that shrinks a little while it is held, like the RN Pressable. */
@Composable
private fun Pressable(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    outer: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        outer
            .graphicsLayer {
                val s = if (pressed) PRESS_SCALE else 1f
                scaleX = s
                scaleY = s
            }
            .clickable(interactionSource = interaction, indication = null, onClick = onClick)
            .then(modifier),
        contentAlignment = Alignment.CenterStart,
    ) { content() }
}
