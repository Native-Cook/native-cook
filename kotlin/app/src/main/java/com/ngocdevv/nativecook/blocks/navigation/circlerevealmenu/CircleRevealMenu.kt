package com.ngocdevv.nativecook.blocks.navigation.circlerevealmenu

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicText
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
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * @param title The item of the menu.
 * @param headline The first of the two lines at the top of its page.
 * @param caption Under the photos: the current project.
 * @param nextCaption Under the photos: the next project.
 */
data class Section(val title: String, val headline: String, val line: String, val caption: String, val nextCaption: String)

val SampleSections = listOf(
    Section("Works", "Smart & clean design", "That’s Norde.studio", "Architects concept", "Weekend Sprint"),
    Section("Process", "How we work", "Four steps, no surprises", "Discover and define", "Design and build"),
    Section("About", "A small studio", "Six people in Tallinn", "The team", "The workshop"),
    Section("Contact", "Say hello", "hello@norde.studio", "New projects", "Open roles"),
)

// Black and white, as the page asks for, and twice the size of the widest place in the row.
private fun photo(id: String) = "https://images.unsplash.com/$id?auto=format&fit=crop&w=374&h=320&q=80&sat=-100"

val SamplePhotos = listOf(photo("photo-1496442226666-8d4d0e62e6e9"), photo("photo-1513635269975-59663e0ac1ad"), photo("photo-1540959733332-eab4deabeeaf"))

private val Paper = Color.White
private val Ink = Color(0xFF111111)
private val Grey = Color(0xFF9A9A9A)
private val Slab = Color(0xFFB9B4AE)
private val Tile = Color(0xFF1F1E1C)
private val TileFar = Color(0xFF3A3835)
private val Menu = Color(0xFF124A3B)
private val Muted = Color(0xFF5E8C7C)
private val Hairline = Color.White.copy(alpha = 0.1f)

private const val MARGIN = 24f

// The top bar under the status bar, and the menu button's lines in it.
private const val BAR_TOP = 8f
private const val BAR = 44f
private const val LINE_WIDTH = 18f
private const val LINE = 2f
private const val LINE_GAP = 4.5f

// The button's centre from the right edge.
private const val BUTTON_INSET = 33f
private const val LOGO = 24f

// Everything hangs from the bar's top, at these distances on a screen with DESIGN dp under the
// bar; a shorter screen draws them closer together.
private const val DESIGN = 804f
private const val HEADLINE_TOP = 117f
private const val PHOTOS_TOP = 271f
private const val PHOTO_HEIGHT = 160f
private val PhotoWidths = listOf(96f, 110f, 187f)
private const val PHOTO_GAP = 8f
private const val CAPTIONS_TOP = 603f
private const val SLAB_TOP = 690f
private const val ITEMS_TOP = 190f
private const val ITEM_PITCH = 84f
private const val ITEM_LEFT = 30f
private const val STRIP = 64f

// The least bottom inset the strip is drawn for.
private const val STRIP_INSET = 34f

// The page under the circle is this much smaller and lower.
private const val PAGE_SHRINK = 0.04f
private const val PAGE_SINK = 12f
private const val RISE = 16f

// The logo turns white while this much of the circle's edge passes over it.
private const val EDGE = 24f

const val LOOP = 9f

// Opening: the circle, and then the items one after another, the strip last.
private const val CIRCLE_IN = 0.55f
private const val LIST_AFTER = 0.35f
private const val LIST_IN = 0.55f
private const val STAGGER = 0.07f
private const val ITEM_IN = 0.3f
private const val STRIP_AFTER = 0.33f

// Closing: the items in reverse, and then the circle.
private const val LIST_OUT = 0.4f
private const val CIRCLE_AFTER = 0.25f
private const val CIRCLE_OUT = 0.45f

// A chosen item turns white, and the menu closes a moment later.
private const val SWAP = 0.2f
private const val CHOSEN_HOLD = 0.4f
private val EaseInOut = CubicBezierEasing(0.65f, 0f, 0.35f, 1f)

// The loop starts again this long after the last touch.
private const val USER_HOLD = 3f

// What the autoPlay loop presses: the button, and an item of the open menu.
private class Step(val at: Float, val choose: Int? = null)

private val Script = listOf(Step(0.8f), Step(3.0f, choose = 1), Step(5.6f), Step(7.4f, choose = 0))

private fun ms(seconds: Float) = (seconds * 1000).toInt()

private fun easeOut(t: Float): Float {
    val u = 1f - t.coerceIn(0f, 1f)
    return 1f - u * u * u
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun studioText(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val HeadlineStyle = studioText(30f, 36f, FontWeight.Bold, Ink)
private val LineStyle = studioText(30f, 36f, FontWeight.Normal, Ink)
private val CaptionStyle = studioText(15f, 19f, FontWeight.Normal, Ink)
private val NextStyle = studioText(15f, 19f, FontWeight.Normal, Grey)
private val ItemStyle = studioText(50f, 60f, FontWeight.Normal, Color.White)
private val ActionStyle = studioText(15f, 19f, FontWeight.Medium, Color.White)

/**
 * A studio page with a menu button at its top right. The menu is a dark green sheet that grows
 * out of the button as a circle until it covers the screen, while the button's three lines turn
 * into a cross; the items then rise in one after another. Choosing one closes the menu onto that
 * section's page.
 *
 * @param photos Three photos for the row on the page; each section starts the row with another one.
 * @param actions The two actions at the bottom of the menu.
 * @param autoPlay Opens the menu, picks an item and closes it by itself every [LOOP] seconds.
 */
@Composable
fun CircleRevealMenu(
    sections: List<Section> = SampleSections,
    photos: List<String> = SamplePhotos,
    actions: List<String> = listOf("Call", "Mail"),
    menuColor: Color = Menu,
    autoPlay: Boolean = false,
    onSelect: ((index: Int) -> Unit)? = null,
    onAction: ((index: Int) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val reducedMotion = rememberReducedMotion()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    // The loop outlives a recomposition: it reads what the caller passes through this.
    val onPick by rememberUpdatedState(onSelect)

    var open by remember { mutableStateOf(false) }
    // The chosen section and the one before it.
    var current by remember { mutableIntStateOf(0) }
    var previous by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // The autoPlay loop makes no haptics; a touch does.
    var byHand by remember { mutableStateOf(false) }

    // How much of the way the circle has grown, and how far the list of items has come in.
    val circle = remember { Animatable(0f) }
    val list = remember { Animatable(0f) }
    // How far the items' colours have changed over to the chosen section.
    val swap = remember { Animatable(1f) }
    var closing by remember { mutableStateOf<Job?>(null) }

    fun show() {
        closing?.cancel()
        open = true
        scope.launch { circle.animateTo(1f, tween(ms(CIRCLE_IN), easing = EaseInOut)) }
        scope.launch { list.animateTo(1f, tween(ms(LIST_IN), delayMillis = ms(LIST_AFTER), easing = LinearEasing)) }
    }

    fun hide() {
        closing?.cancel()
        open = false
        scope.launch { list.animateTo(0f, tween(ms(LIST_OUT), easing = LinearEasing)) }
        scope.launch { circle.animateTo(0f, tween(ms(CIRCLE_OUT), delayMillis = ms(CIRCLE_AFTER), easing = EaseInOut)) }
    }

    /** An item of the menu: it turns white, and the menu closes onto its page a moment later. */
    fun choose(index: Int) {
        if (index != current) {
            previous = current
            current = index
            scope.launch {
                swap.snapTo(0f)
                swap.animateTo(1f, tween(ms(SWAP), easing = LinearEasing))
            }
            onPick?.invoke(index)
        }
        closing?.cancel()
        closing = scope.launch {
            delay(ms(CHOSEN_HOLD).toLong())
            hide()
        }
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        byHand = true
        touches++
    }

    fun tick() {
        if (byHand) view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay(ms(USER_HOLD).toLong())
            // The loop starts over on the first section with the menu closed.
            byHand = false
            if (open) hide()
            if (current != 0) {
                previous = current
                current = 0
            }
            // The menu has to be gone before the loop presses the button again.
            delay(ms(LIST_OUT + CIRCLE_OUT).toLong())
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            // The loop presses what a finger presses, through the same functions.
            for (step in Script) {
                until(step.at)
                if (step.choose != null) choose(step.choose) else if (open) hide() else show()
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // Dark status bar icons over the white page, light ones over the menu.
    DisposableEffect(view, open) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !open
        onDispose { if (before != null) controller.isAppearanceLightStatusBars = before }
    }

    BoxWithConstraints(modifier.fillMaxSize().background(Paper).clipToBounds()) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val width = maxWidth.value
        val height = maxHeight.value
        val top = insets.calculateTopPadding().value + BAR_TOP
        // Where something hangs under the bar's top: closer on a screen shorter than the design's.
        val near = min(1f, (height - top) / DESIGN)
        val buttonX = width - BUTTON_INSET
        val buttonY = top + BAR / 2f
        // The circle grows from the button's centre until it has the farthest corner.
        val reach = hypot(max(buttonX, width - buttonX), max(buttonY, height - buttonY))
        val logoAway = buttonX - (MARGIN + LOGO / 2f)
        val stripInset = max(0f, insets.calculateBottomPadding().value - STRIP_INSET)
        val section = sections[min(current, sections.lastIndex)]

        // The button's lines and the logo turn white when the circle's edge has passed them.
        fun overButton() = if (reducedMotion) circle.value else min(1f, reach * circle.value / (LINE_WIDTH / 2f + 5f))
        fun overLogo() = if (reducedMotion) circle.value else ((reach * circle.value - (logoAway - EDGE / 2f)) / EDGE).coerceIn(0f, 1f)

        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    if (!reducedMotion) {
                        translationY = (PAGE_SINK * circle.value).dp.toPx()
                        scaleX = 1f - PAGE_SHRINK * circle.value
                        scaleY = scaleX
                    }
                }
                .then(if (open) Modifier.clearAndSetSemantics {} else Modifier),
        ) {
            Column(Modifier.offset(x = MARGIN.dp, y = (top + HEADLINE_TOP * near).dp)) {
                BasicText(section.headline, style = HeadlineStyle, maxLines = 1)
                BasicText(section.line, style = LineStyle, maxLines = 1)
            }
            // The row runs off the right edge.
            Row(
                Modifier.offset(y = (top + PHOTOS_TOP * near).dp).wrapContentWidth(Alignment.Start, unbounded = true),
                horizontalArrangement = Arrangement.spacedBy(PHOTO_GAP.dp),
            ) {
                PhotoWidths.forEachIndexed { i, photoWidth ->
                    Photo(photos[(i + current) % photos.size], Modifier.size(photoWidth.dp, PHOTO_HEIGHT.dp))
                }
            }
            Row(
                Modifier.offset(x = MARGIN.dp, y = (top + CAPTIONS_TOP * near).dp).width((width - MARGIN * 2f).dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    BasicText(section.caption, style = CaptionStyle, maxLines = 1)
                    Box(Modifier.padding(top = 6.dp).size(4.dp).background(Ink, CircleShape))
                }
                BasicText(section.nextCaption, style = NextStyle, maxLines = 1)
            }
            // The top of the next project.
            Row(
                Modifier
                    .offset(x = MARGIN.dp, y = (top + SLAB_TOP * near).dp)
                    .size((width - MARGIN * 2f).dp, max(0f, height - top - SLAB_TOP * near).dp)
                    .background(Slab)
                    .padding(start = 28.dp, top = 36.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Box(Modifier.size(72.dp, 56.dp).background(Tile))
                Box(Modifier.size(60.dp, 56.dp).background(TileFar))
            }
        }

        Box(
            Modifier.fillMaxSize().drawBehind {
                val grown = circle.value
                val center = Offset(buttonX.dp.toPx(), buttonY.dp.toPx())
                // Without movement the sheet fades in whole.
                if (reducedMotion) {
                    drawCircle(menuColor, reach.dp.toPx(), center, alpha = grown)
                } else if (grown > 0f) {
                    drawCircle(menuColor, reach.dp.toPx() * grown, center)
                }
            },
        )

        Column(Modifier.offset(y = (top + ITEMS_TOP * near).dp).then(if (open) Modifier else Modifier.clearAndSetSemantics {})) {
            sections.forEachIndexed { i, item ->
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(ITEM_PITCH.dp)
                        // An item that cannot be seen takes no touches.
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open, role = Role.Button) {
                            touched()
                            tick()
                            choose(i)
                        }
                        .semantics { selected = i == current }
                        .graphicsLayer {
                            val shown = easeOut((list.value * LIST_IN - i * STAGGER) / ITEM_IN)
                            alpha = shown
                            if (!reducedMotion) translationY = ((1f - shown) * RISE).dp.toPx()
                        }
                        .padding(start = ITEM_LEFT.dp),
                    contentAlignment = Alignment.CenterStart,
                ) {
                    BasicText(
                        item.title,
                        style = ItemStyle,
                        maxLines = 1,
                        // The chosen item turns white as the one before it turns muted.
                        color = { lerp(Muted, Color.White, if (i == current) swap.value else if (i == previous) 1f - swap.value else 0f) },
                    )
                }
            }
        }

        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height((STRIP + stripInset).dp)
                .graphicsLayer { alpha = easeOut((list.value * LIST_IN - STRIP_AFTER) / (LIST_IN - STRIP_AFTER)) }
                .drawBehind { drawRect(Hairline, size = Size(size.width, 1.dp.toPx())) }
                .padding(bottom = stripInset.dp)
                .then(if (open) Modifier else Modifier.clearAndSetSemantics {}),
        ) {
            actions.forEachIndexed { i, label ->
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .drawBehind { if (i > 0) drawRect(Hairline, size = Size(1.dp.toPx(), size.height)) }
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open, role = Role.Button) {
                            touched()
                            tick()
                            onAction?.invoke(i)
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    BasicText(label, style = ActionStyle, maxLines = 1)
                }
            }
        }

        // Two slanted strokes side by side.
        Canvas(
            Modifier
                .offset(x = MARGIN.dp, y = (top + (BAR - LOGO) / 2f).dp)
                .size(LOGO.dp)
                .semantics { contentDescription = "Norde.studio" },
        ) {
            val color = lerp(Ink, Color.White, overLogo())
            for (x in listOf(4f, 14.5f)) {
                rotate(20f, pivot = Offset((x + 3f).dp.toPx(), size.height / 2f)) {
                    drawRect(color, Offset(x.dp.toPx(), 0f), Size(6.dp.toPx(), size.height))
                }
            }
        }

        Canvas(
            Modifier
                .offset(x = (buttonX - BAR / 2f).dp, y = top.dp)
                .size(BAR.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {
                    touched()
                    tick()
                    if (open) hide() else show()
                }
                .semantics { contentDescription = if (open) "Close menu" else "Menu" },
        ) {
            // The three lines become a cross: the outer two turn and meet in the middle, the middle one goes.
            val grown = circle.value
            val cross = if (reducedMotion) (if (grown > 0.5f) 1f else 0f) else min(1f, grown * 2.5f)
            val apart = (LINE_GAP + LINE) * (1f - cross)
            val color = lerp(Ink, Color.White, overButton())
            menuLine(color, -apart, 45f * cross, 1f)
            menuLine(color, 0f, 0f, 1f - cross)
            menuLine(color, apart, -45f * cross, 1f)
        }
    }
}

/** One line of the menu button: [dy] dp from the middle, turned by [degrees]. */
private fun DrawScope.menuLine(color: Color, dy: Float, degrees: Float, alpha: Float) {
    val middle = Offset(size.width / 2f, size.height / 2f + dy.dp.toPx())
    rotate(degrees, pivot = middle) {
        drawRoundRect(
            color,
            Offset(middle.x - LINE_WIDTH.dp.toPx() / 2f, middle.y - LINE.dp.toPx() / 2f),
            Size(LINE_WIDTH.dp.toPx(), LINE.dp.toPx()),
            CornerRadius(LINE.dp.toPx() / 2f),
            alpha = alpha,
        )
    }
}

@Composable
private fun Photo(url: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val request = remember(url, context) { ImageRequest.Builder(context).data(url).crossfade(200).build() }
    AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = modifier.background(Color(0xFFE4E2DE)))
}
