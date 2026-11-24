package com.ngocdevv.nativecook.blocks.modals.morphingcarddialog

import android.content.Context
import android.graphics.BitmapFactory
import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RenderEffect
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

// "Weekend tables": a restaurant guide whose cards open into a dialog by morphing.
// The morph's state is MorphingCardDialogEngine; this file is the screen, the content and the touches.

// Palette: one warm neutral family, one accent.
private val PAPER = Color(0xFFF3EEE6)
private val CARD = Color(0xFFFFFCF7)
private val INK = Color(0xFF1B1713)
private val INK_SOFT = Color(0xFF857B6F)
private val FILL = Color(0xFFF2ECE3)
private val HAIRLINE = rgba(27, 23, 19, 0.10f)
private val EMBER = Color(0xFFBF4A26)
private val SCRIM = Color(0xFF1A130C)
private val PHOTO_BACK = Color(0xFF2A221B)
private val PLUS_BAR = Color(0xFFFBF7F0)
private val SHADOW_NEAR = Color(58, 40, 24)
private val SHADOW_FAR = Color(40, 26, 14)

// Layout, in dp.
private const val SIDE = 16f
private const val GAP = 12f
private const val HEADER = 124f
private const val CARD_TEXT = 60f // the strip under a card's photo
private const val CARD_ASPECT = 1.22f // a card is never taller than this × its width
private const val DIALOG_SIDE = 20f
private const val DIALOG_MAX = 380f
private const val PHOTO_RATIO = 0.6f // the dialog photo's height / width
private const val PAD_CARD = 12f
private const val PAD_DIALOG = 20f
// Title and subtitle are set once at the dialog's size and scaled down for the card, so both ends match exactly.
private const val TITLE_SIZE = 26
private const val TITLE_LINE = 31
private const val TITLE_SCALE = 15f / TITLE_SIZE
private const val SUBTITLE_SIZE = 15
private const val SUBTITLE_LINE = 20
private const val SUBTITLE_SCALE = 13f / SUBTITLE_SIZE
private const val TITLE_TOP_CARD = 9f // below the photo
private const val TITLE_TOP_DIALOG = 18f
private const val SUBTITLE_TOP_CARD = 30f
private const val SUBTITLE_TOP_DIALOG = 52f
// The "+" that turns into the "×".
private const val PLUS = 30f
private const val PLUS_INSET_CARD = 40f
private const val PLUS_INSET_DIALOG_X = 44f
private const val PLUS_INSET_DIALOG_Y = 12f
private const val CLOSE_SCALE = 32f / PLUS
private const val PLUS_TURN = 135f // degrees
// Detail content, measured down from the dialog photo's bottom edge.
private const val BODY_TOP = 86f
private const val BODY_LINE = 21
private const val FACTS_TOP = 163f
private const val FACTS_HEIGHT = 54f
private const val ACTION_TOP = 233f
private const val ACTION_HEIGHT = 50f
private const val DETAIL_HEIGHT = 303f
// The ghost finger.
private const val GHOST = 46f
private const val GHOST_PRESS = 0.16f
private const val GHOST_GRAB_Y = 110f // where on the dialog it takes hold, from the top
/** Room around a detail block for its blur and the button's shadow, which a translucent layer would clip. */
private const val BLEED = 36f
/** What the light backdrop blur at full strength comes to here: a few dp of blur and a faint white veil. */
private const val SCENE_BLUR_DP = 3.5f
private const val SCENE_VEIL = 0.05f
private const val HIT_SLOP = 8f
private const val PAN_SLOP = 6f

private class DialogLayout(val cards: List<MorphRect>, val target: MorphRect, val photoHeight: Float)

private fun makeLayout(width: Float, height: Float, top: Float, bottom: Float, count: Int): DialogLayout {
    val gridTop = top + HEADER
    val gridBottom = height - max(bottom, 12f) - 4f
    val rows = (count + 1) / 2
    val cardWidth = (width - SIDE * 2 - GAP) / 2f
    val cardHeight = min((gridBottom - gridTop - GAP * (rows - 1)) / rows, cardWidth * CARD_ASPECT)
    val cards = List(count) { i ->
        MorphRect(SIDE + (i % 2) * (cardWidth + GAP), gridTop + (i / 2) * (cardHeight + GAP), cardWidth, cardHeight)
    }
    val dialogWidth = min(width - DIALOG_SIDE * 2, DIALOG_MAX)
    val photoHeight = (dialogWidth * PHOTO_RATIO).roundToInt().toFloat()
    val dialogHeight = photoHeight + DETAIL_HEIGHT
    val target = MorphRect(
        (width - dialogWidth) / 2f,
        top + max(8f, (height - top - bottom - dialogHeight) / 2f),
        dialogWidth,
        dialogHeight,
    )
    return DialogLayout(cards, target, photoHeight)
}

private fun loadPhoto(context: Context, name: String): ImageBitmap =
    context.assets.open("morphingcarddialog/$name").use { BitmapFactory.decodeStream(it) }.asImageBitmap()

private fun style(size: Int, weight: FontWeight, color: Color, line: Int = 0, spacing: Float = 0f, tabular: Boolean = false) = TextStyle(
    color = color,
    fontSize = size.sp,
    fontWeight = weight,
    lineHeight = if (line > 0) line.sp else TextUnit.Unspecified,
    letterSpacing = spacing.sp,
    fontFeatureSettings = if (tabular) "tnum" else null,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
)

private val TITLE_STYLE = style(TITLE_SIZE, FontWeight.Bold, INK, TITLE_LINE, -0.3f)
private val SUBTITLE_STYLE = style(SUBTITLE_SIZE, FontWeight.Medium, INK_SOFT, SUBTITLE_LINE)

/** Gives the content a fixed size at the parent's top-left corner, whatever room the parent has. */
private fun Modifier.pinned(width: Float, height: Float) = layout { measurable, _ ->
    val placeable = measurable.measure(Constraints.fixed(width.dp.roundToPx(), height.dp.roundToPx()))
    layout(0, 0) { placeable.place(0, 0) }
}

/** One blur at a time, rebuilt only when its radius changes. Null below API 31 and when there is nothing to blur. */
private class BlurCache {
    private var radius = 0f
    private var effect: RenderEffect? = null

    fun effect(sigmaPx: Float): RenderEffect? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S || sigmaPx < 0.3f) return null
        val rounded = (sigmaPx * 4f).roundToInt() / 4f
        if (rounded != radius || effect == null) {
            radius = rounded
            val r = sigmaToRadius(rounded)
            effect = BlurEffect(r, r, TileMode.Clamp)
        }
        return effect
    }
}

/** A 4:3 photo covering the box, centred. */
private fun DrawScope.drawCover(image: ImageBitmap, left: Float, top: Float, width: Float, height: Float) {
    val k = max(width / image.width, height / image.height)
    val dx = left + (width - image.width * k) / 2f
    val dy = top + (height - image.height * k) / 2f
    // Placed with floats: whole-pixel destinations make the photo shiver while the frame morphs.
    clipRect(left, top, left + width, top + height) {
        withTransform({
            translate(dx, dy)
            scale(k, k, Offset.Zero)
        }) {
            drawImage(image)
        }
    }
}

/** The round "+" button, centred on (cx, cy), in the scope's units. Turned by 135° it reads as "×". */
private fun DrawScope.drawPlus(shadow: SoftRoundRect, cx: Float, cy: Float, scale: Float, degrees: Float, fill: Color) {
    withTransform({
        translate(cx, cy)
        rotate(degrees, Offset.Zero)
        scale(scale, scale, Offset.Zero)
    }) {
        val r = PLUS / 2f
        drawIntoCanvas { shadow.draw(it.nativeCanvas, -r, -r + 2f, PLUS, PLUS, r) }
        drawCircle(fill, r, Offset.Zero)
        // The inset top highlight.
        clipRect(-r, -r, r, 0f) {
            drawCircle(Color.White.copy(alpha = 0.14f), r - 0.5f, Offset.Zero, style = Stroke(1f))
        }
        drawRoundRect(PLUS_BAR, Offset(-6f, -0.9f), Size(12f, 1.8f), CornerRadius(1f))
        drawRoundRect(PLUS_BAR, Offset(-0.9f, -6f), Size(1.8f, 12f), CornerRadius(1f))
    }
}

/**
 * @param autoPlay plays the 9 s loop until a finger takes over; it resumes 2 s after the last touch.
 */
@Composable
fun MorphingCardDialog(
    modifier: Modifier = Modifier,
    autoPlay: Boolean = true,
    places: List<MorphingCardDialogPlace> = MorphingCardDialogPlaces,
    onReserve: (MorphingCardDialogPlace) -> Unit = {},
) {
    BlockSystemBars(darkIcons = true)
    val reducedMotion = rememberLiveReducedMotion()
    val context = LocalContext.current
    val view = LocalView.current
    val photos = remember(places) { places.map { loadPhoto(context, it.photo) } }
    val engine = remember { MorphingCardDialogEngine() }
    val reserve by rememberUpdatedState(onReserve)
    val shown by rememberUpdatedState(places)

    BoxWithConstraints(modifier.fillMaxSize().background(PAPER).clipToBounds()) {
        val density = LocalDensity.current
        val width = maxWidth.value
        val height = maxHeight.value
        val bars = WindowInsets.systemBars
        val top = bars.getTop(density) / density.density
        val bottom = bars.getBottom(density) / density.density
        val layout = remember(width, height, top, bottom, places.size) { makeLayout(width, height, top, bottom, places.size) }
        val target = layout.target
        val textWidth = target.width - PAD_DIALOG * 2

        engine.cards = layout.cards
        engine.target = target
        engine.photoHeight = layout.photoHeight
        engine.closeHit = MorphRect(target.width - PLUS_INSET_DIALOG_X - 1f, PLUS_INSET_DIALOG_Y - 1f, PLUS + 2f, PLUS + 2f)
        engine.actionHit = MorphRect(PAD_DIALOG, layout.photoHeight + ACTION_TOP, target.width - PAD_DIALOG * 2, ACTION_HEIGHT)
        engine.grabX = target.x + target.width / 2f
        engine.grabY = target.y + GHOST_GRAB_Y
        engine.playing = autoPlay && !reducedMotion
        engine.reduced = reducedMotion

        LaunchedEffect(engine) {
            while (true) withFrameNanos { engine.frame(it) }
        }

        fun impact() {
            view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        }

        Box(
            Modifier
                .fillMaxSize()
                // Initial pass: while a dialog is out this layer takes every touch; otherwise only the cards'.
                .pointerInput(engine) {
                    val slop = PAN_SLOP * density.density
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        val scale = density.density
                        val x = down.position.x / scale
                        val y = down.position.y / scale
                        if (engine.active < 0) {
                            val index = engine.cards.indexOfFirst { it.contains(x, y) }
                            if (index < 0) return@awaitEachGesture
                            // A card: press in, open on a tap that ends on it.
                            down.consume()
                            engine.touch()
                            engine.fingers += 1
                            engine.pressIn(index)
                            try {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    if (!change.pressed) {
                                        if (engine.cards[index].contains(change.position.x / scale, change.position.y / scale) && engine.open(index)) impact()
                                        break
                                    }
                                }
                            } finally {
                                engine.fingers = max(0, engine.fingers - 1)
                                engine.pressOut()
                            }
                        } else {
                            // The dialog's layer: a drag on the dialog, or a tap on "×", the button or outside.
                            down.consume()
                            engine.touch()
                            engine.fingers += 1
                            val frame = engine.target
                            val onClose = engine.closeHit.contains(x - frame.x, y - frame.y, HIT_SLOP)
                            val onAction = !onClose && engine.actionHit.contains(x - frame.x, y - frame.y)
                            if (engine.goal == 1f) {
                                if (onClose) engine.closeTo = 1f else if (onAction) engine.actionTo = 1f
                            }
                            val tracker = VelocityTracker()
                            tracker.addPosition(down.uptimeMillis, down.position)
                            var panning = false
                            var from = down.position
                            try {
                                while (true) {
                                    val change = awaitPointerEvent(PointerEventPass.Initial).changes.firstOrNull { it.id == down.id } ?: break
                                    change.consume()
                                    tracker.addPosition(change.uptimeMillis, change.position)
                                    if (!change.pressed) {
                                        if (panning) {
                                            if (engine.dragging) {
                                                val speed = tracker.calculateVelocity()
                                                if (engine.dragEnd(speed.x / scale, speed.y / scale)) impact()
                                            }
                                        } else if (engine.goal == 1f) {
                                            val ux = change.position.x / scale
                                            val uy = change.position.y / scale
                                            if (engine.closeHit.contains(ux - frame.x, uy - frame.y, HIT_SLOP) || !frame.contains(ux, uy)) {
                                                engine.close(0f)
                                                impact()
                                            } else if (engine.actionHit.contains(ux - frame.x, uy - frame.y)) {
                                                impact()
                                                shown.getOrNull(engine.active)?.let { reserve(it) }
                                            }
                                        }
                                        break
                                    }
                                    if (!panning && (change.position - down.position).getDistance() > slop) {
                                        panning = true
                                        from = change.position
                                        engine.closeTo = 0f
                                        engine.actionTo = 0f
                                        if (engine.goal == 1f && frame.contains(x, y)) engine.dragBegin()
                                    }
                                    if (panning && engine.dragging) {
                                        engine.dragMove((change.position.x - from.x) / scale, (change.position.y - from.y) / scale)
                                    }
                                }
                            } finally {
                                engine.fingers = max(0, engine.fingers - 1)
                                engine.closeTo = 0f
                                engine.actionTo = 0f
                                if (engine.dragging) engine.dragEnd(0f, 0f)
                            }
                        }
                    }
                },
        ) {
            Scene(engine, places, photos, layout, top, width, textWidth)

            // The backdrop: a faint veil for the light blur's tint, then the dim.
            Canvas(Modifier.fillMaxSize()) {
                engine.tick.longValue
                val u = engine.sceneAmount
                if (u > 0f) {
                    drawRect(Color.White.copy(alpha = SCENE_VEIL * u))
                    drawRect(SCRIM.copy(alpha = SCENE_DIM * u))
                }
            }

            DialogFrame(engine, places, photos, textWidth)

            Ghost(engine)
        }
    }
}

/** The page behind the dialog: it sinks back and blurs a little while the dialog is open. */
@Composable
private fun Scene(
    engine: MorphingCardDialogEngine,
    places: List<MorphingCardDialogPlace>,
    photos: List<ImageBitmap>,
    layout: DialogLayout,
    top: Float,
    width: Float,
    textWidth: Float,
) {
    val blur = remember { BlurCache() }
    var meal by remember { mutableIntStateOf(1) }
    Box(
        Modifier.fillMaxSize().graphicsLayer {
            engine.tick.longValue
            val u = engine.sceneAmount
            val s = if (engine.reduced) 1f else mix(1f, SCENE_SCALE, u)
            scaleX = s
            scaleY = s
            renderEffect = blur.effect(SCENE_BLUR_DP * u * density)
        },
    ) {
        Column(Modifier.offset(SIDE.dp, (top + 8f).dp).width((width - SIDE * 2).dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText(COPY_BRAND, Modifier.alignByBaseline(), style = style(15, FontWeight.Bold, EMBER))
                BasicText("${places.size} with a table left", Modifier.alignByBaseline(), style = style(13, FontWeight.Medium, INK_SOFT, tabular = true))
            }
            Spacer(Modifier.height(2.dp))
            BasicText(COPY_TITLE, Modifier.semantics { heading() }, style = style(32, FontWeight.ExtraBold, INK, 38, -0.4f))
            Spacer(Modifier.height(12.dp))
            MealPicker(meal) { meal = it }
        }

        places.forEachIndexed { i, place ->
            val card = layout.cards[i]
            Box(
                Modifier
                    .offset(card.x.dp, card.y.dp)
                    .size(card.width.dp, card.height.dp)
                    // The card hands its place to the dialog's frame while the dialog is out.
                    .graphicsLayer {
                        engine.tick.longValue
                        alpha = if (!engine.reduced && engine.active == i) 0f else 1f
                        val s = if (engine.pressed == i) 1f - (1f - PRESS_SCALE) * engine.pressAmount else 1f
                        scaleX = s
                        scaleY = s
                    }
                    .cssBoxShadow(SHADOW_NEAR.copy(alpha = 0.08f), 8.dp, 20.dp, RADIUS_CARD.dp)
                    .cssBoxShadow(SHADOW_NEAR.copy(alpha = 0.10f), 1.dp, 2.dp, RADIUS_CARD.dp)
                    .clip(RoundedCornerShape(RADIUS_CARD.dp))
                    .background(CARD)
                    .semantics {
                        role = Role.Button
                        contentDescription = "${place.name}, ${place.area}"
                        onClick {
                            engine.touch()
                            engine.open(i)
                        }
                    },
            ) {
                CardFace(place, photos[i], card.width, card.height, textWidth)
            }
        }
    }
}

/** The meals filter: Material's segmented buttons at the height of the system control it stands in for. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MealPicker(value: Int, onChange: (Int) -> Unit) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides Dp.Unspecified) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().height(32.dp)) {
            COPY_MEALS.forEachIndexed { i, label ->
                SegmentedButton(
                    selected = i == value,
                    onClick = { onChange(i) },
                    shape = SegmentedButtonDefaults.itemShape(i, COPY_MEALS.size),
                    colors = SegmentedButtonDefaults.colors(
                        activeContainerColor = CARD,
                        activeContentColor = INK,
                        activeBorderColor = rgba(27, 23, 19, 0.14f),
                        inactiveContainerColor = rgba(27, 23, 19, 0.07f),
                        inactiveContentColor = INK,
                        inactiveBorderColor = rgba(27, 23, 19, 0.14f),
                    ),
                    contentPadding = PaddingValues(horizontal = 8.dp),
                    icon = {},
                    modifier = Modifier.height(32.dp),
                ) {
                    Text(label, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                }
            }
        }
    }
}

/** A card at rest. It is drawn exactly as the dialog's frame is at progress 0, so the hand-over is invisible. */
@Composable
private fun CardFace(place: MorphingCardDialogPlace, photo: ImageBitmap, width: Float, height: Float, textWidth: Float) {
    val photoHeight = height - CARD_TEXT
    val plusShadow = remember { SoftRoundRect(rgba(27, 23, 19, 0.22f), 3f) }
    Box(
        Modifier.fillMaxSize().drawBehind {
            drawRect(PHOTO_BACK, size = Size(size.width, photoHeight.dp.toPx()))
            drawCover(photo, 0f, 0f, size.width, photoHeight.dp.toPx())
        },
    )
    BasicText(
        place.name,
        Modifier.pinned(textWidth, TITLE_LINE.toFloat()).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = PAD_CARD.dp.toPx()
            translationY = (photoHeight + TITLE_TOP_CARD).dp.toPx()
            scaleX = TITLE_SCALE
            scaleY = TITLE_SCALE
        },
        style = TITLE_STYLE,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    BasicText(
        place.area,
        Modifier.pinned(textWidth, SUBTITLE_LINE.toFloat()).graphicsLayer {
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = PAD_CARD.dp.toPx()
            translationY = (photoHeight + SUBTITLE_TOP_CARD).dp.toPx()
            scaleX = SUBTITLE_SCALE
            scaleY = SUBTITLE_SCALE
        },
        style = SUBTITLE_STYLE,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
    Canvas(Modifier.fillMaxSize()) {
        scale(density, density, Offset.Zero) {
            drawPlus(plusShadow, width - PLUS_INSET_CARD + PLUS / 2f, height - PLUS_INSET_CARD + PLUS / 2f, 1f, 0f, INK)
        }
    }
}

/** The morphing frame and everything inside it. Each part places itself from the engine's values. */
@Composable
private fun DialogFrame(
    engine: MorphingCardDialogEngine,
    places: List<MorphingCardDialogPlace>,
    photos: List<ImageBitmap>,
    textWidth: Float,
) {
    // Two shadows that cross over: the card's stays, the lifted dialog's fades in.
    val near = remember { listOf(SoftRoundRect(SHADOW_NEAR.copy(alpha = 0.10f), 1f), SoftRoundRect(SHADOW_NEAR.copy(alpha = 0.08f), 10f)) }
    val far = remember { listOf(SoftRoundRect(SHADOW_FAR.copy(alpha = 0.16f), 3f), SoftRoundRect(SHADOW_FAR.copy(alpha = 0.30f), 28f)) }
    val plusShadow = remember { SoftRoundRect(rgba(27, 23, 19, 0.22f), 3f) }
    val outline = remember { Path() }

    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                engine.tick.longValue
                alpha = engine.frameAlpha
            }
            .drawWithContent {
                engine.tick.longValue
                val index = engine.active
                if (index < 0) return@drawWithContent
                val d = density
                val x = engine.frameX
                val y = engine.frameY
                val w = engine.frameWidth
                val h = engine.frameHeight
                val r = engine.radius
                val press = 1f - (1f - PRESS_SCALE) * engine.pressAmount
                withTransform({ scale(press, press, Offset((x + w / 2f) * d, (y + h / 2f) * d)) }) {
                    scale(d, d, Offset.Zero) {
                        drawIntoCanvas {
                            val canvas = it.nativeCanvas
                            far[1].draw(canvas, x, y + 28f, w, h, r, engine.lifted)
                            far[0].draw(canvas, x, y + 2f, w, h, r, engine.lifted)
                            near[1].draw(canvas, x, y + 8f, w, h, r)
                            near[0].draw(canvas, x, y + 1f, w, h, r)
                        }
                    }
                    outline.rewind()
                    outline.addRoundRect(RoundRect(x * d, y * d, (x + w) * d, (y + h) * d, CornerRadius(r * d)))
                    clipPath(outline) {
                        drawRect(CARD, Offset(x * d, y * d), Size(w * d, h * d))
                        drawRect(PHOTO_BACK, Offset(x * d, y * d), Size(w * d, engine.edge * d))
                        photos.getOrNull(index)?.let { drawCover(it, x * d, y * d, w * d, engine.edge * d) }
                        this@drawWithContent.drawContent()
                        // "+" rides up the right edge and turns into "×", a beat behind the frame.
                        val q = engine.q
                        val u = clamp(q, 0f, 1f)
                        val from = engine.cards[index]
                        scale(d, d, Offset.Zero) {
                            drawPlus(
                                plusShadow,
                                x + w - mix(PLUS_INSET_CARD, PLUS_INSET_DIALOG_X, q) + PLUS / 2f,
                                y + mix(from.height - PLUS_INSET_CARD, PLUS_INSET_DIALOG_Y, q) + PLUS / 2f,
                                mix(1f, CLOSE_SCALE, u) * (1f - 0.1f * engine.closePress),
                                PLUS_TURN * engine.lateTitle,
                                INK.copy(alpha = mix(1f, 0.58f, u)),
                            )
                        }
                    }
                }
            },
    ) {
        // Title and subtitle ride in the frame, each a beat behind it.
        Riding(engine, textWidth, TITLE_LINE.toFloat(), TITLE_SCALE, TITLE_TOP_CARD, TITLE_TOP_DIALOG, title = true) {
            places.forEachIndexed { i, place ->
                BasicText(place.name, Modifier.only(engine, i), style = TITLE_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Riding(engine, textWidth, SUBTITLE_LINE.toFloat(), SUBTITLE_SCALE, SUBTITLE_TOP_CARD, SUBTITLE_TOP_DIALOG, title = false) {
            places.forEachIndexed { i, place ->
                BasicText(place.area, Modifier.only(engine, i), style = SUBTITLE_STYLE, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }

        Rise(engine, 0, BODY_TOP, textWidth, BODY_LINE * 3f) {
            places.forEachIndexed { i, place ->
                BasicText(
                    place.about,
                    // Unbounded: three lines can measure a pixel taller than BODY_LINE * 3, and a bounded
                    // height would then drop the third line.
                    Modifier.fillMaxWidth().wrapContentHeight(Alignment.Top, unbounded = true).only(engine, i),
                    style = style(15, FontWeight.Normal, Color(0xFF4A423A), BODY_LINE),
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        Rise(engine, 1, FACTS_TOP, textWidth, FACTS_HEIGHT) {
            Box(Modifier.fillMaxSize().background(FILL, RoundedCornerShape(16.dp)))
            places.forEachIndexed { i, place ->
                Row(Modifier.fillMaxSize().only(engine, i), verticalAlignment = Alignment.CenterVertically) {
                    place.facts.forEachIndexed { k, fact ->
                        Column(
                            Modifier.weight(1f).drawBehind {
                                if (k > 0) drawLine(HAIRLINE, Offset(0f, 0f), Offset(0f, size.height), 1f)
                            },
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp),
                        ) {
                            BasicText(fact.value, style = style(16, FontWeight.Bold, INK, tabular = true), maxLines = 1)
                            BasicText(fact.label, style = style(12, FontWeight.Medium, INK_SOFT), maxLines = 1)
                        }
                    }
                }
            }
        }
        Rise(engine, 2, ACTION_TOP, textWidth, ACTION_HEIGHT) {
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        engine.tick.longValue
                        val s = 1f - 0.03f * engine.actionPress
                        scaleX = s
                        scaleY = s
                    }
                    .cssBoxShadow(rgba(191, 74, 38, 0.28f), 10.dp, 22.dp, (ACTION_HEIGHT / 2f).dp)
                    .cssBoxShadow(rgba(120, 40, 14, 0.30f), 1.dp, 2.dp, (ACTION_HEIGHT / 2f).dp)
                    .background(EMBER, CircleShape)
                    // The inset top highlight.
                    .drawBehind {
                        val stroke = 1.dp.toPx()
                        clipRect(0f, 0f, size.width, size.height / 2f) {
                            drawRoundRect(
                                Color.White.copy(alpha = 0.22f),
                                Offset(stroke / 2f, stroke / 2f),
                                Size(size.width - stroke, size.height - stroke),
                                CornerRadius(size.height / 2f),
                                style = Stroke(stroke),
                            )
                        }
                    }
                    .semantics { role = Role.Button },
                contentAlignment = Alignment.Center,
            ) {
                BasicText(COPY_RESERVE, style = style(17, FontWeight.SemiBold, Color(0xFFFFF8F1)), maxLines = 1)
            }
        }
    }
}

/** Shows the content only while card `index` is the one that is open: the six places share one frame. */
private fun Modifier.only(engine: MorphingCardDialogEngine, index: Int) = graphicsLayer {
    engine.tick.longValue
    alpha = if (engine.active == index) 1f else 0f
}

@Composable
private fun Riding(
    engine: MorphingCardDialogEngine,
    width: Float,
    height: Float,
    cardScale: Float,
    topCard: Float,
    topDialog: Float,
    title: Boolean,
    content: @Composable BoxScope.() -> Unit,
) {
    Box(
        Modifier.pinned(width, height).graphicsLayer {
            engine.tick.longValue
            val late = if (title) engine.lateTitle else engine.lateSubtitle
            transformOrigin = TransformOrigin(0f, 0f)
            translationX = (engine.frameX + mix(PAD_CARD, PAD_DIALOG, late)) * density
            translationY = (engine.frameY + engine.edge + mix(topCard, topDialog, engine.q)) * density
            val s = mix(cardScale, 1f, late)
            scaleX = s
            scaleY = s
        },
        content = content,
    )
}

/** One block of detail content: it comes up out of a blur once the frame is nearly open. */
@Composable
private fun Rise(
    engine: MorphingCardDialogEngine,
    order: Int,
    top: Float,
    width: Float,
    height: Float,
    content: @Composable BoxScope.() -> Unit,
) {
    val blur = remember { BlurCache() }
    Box(
        Modifier
            .pinned(width + BLEED * 2, height + BLEED * 2)
            .graphicsLayer {
                engine.tick.longValue
                val u = engine.rise(order)
                val away = if (engine.reduced) 0f else 1f - u
                alpha = u
                translationX = (engine.frameX + PAD_DIALOG - BLEED) * density
                translationY = (engine.frameY + engine.edge + top + away * CONTENT_RISE - BLEED) * density
                renderEffect = if (u > 0f) blur.effect(away * CONTENT_BLUR * density) else null
            }
            .padding(BLEED.dp),
        content = content,
    )
}

/** The finger autoPlay pretends to have: a soft dot that taps, drags and lets go. */
@Composable
private fun Ghost(engine: MorphingCardDialogEngine) {
    val shadow = remember { SoftRoundRect(rgba(27, 23, 19, 0.18f), 5f) }
    Canvas(Modifier.fillMaxSize()) {
        engine.tick.longValue
        val alpha = if (engine.playing && engine.mode == MODE_AUTO) engine.ghostAlpha else 0f
        if (alpha <= 0f) return@Canvas
        val s = 1f - GHOST_PRESS * engine.ghostPress
        val r = GHOST / 2f
        scale(density, density, Offset.Zero) {
            withTransform({
                translate(engine.ghostX, engine.ghostY)
                scale(s, s, Offset.Zero)
            }) {
                drawIntoCanvas { shadow.draw(it.nativeCanvas, -r, -r + 2f, GHOST, GHOST, r, alpha) }
                drawCircle(rgba(27, 23, 19, 0.20f * alpha), r, Offset.Zero)
                drawCircle(Color.White.copy(alpha = 0.75f * alpha), r - 0.75f, Offset.Zero, style = Stroke(1.5f))
            }
        }
    }
}
