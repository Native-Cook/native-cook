package com.ngocdevv.nativecook.blocks.navigation.depthsidedrawer

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Logout
import androidx.compose.material.icons.outlined.FavoriteBorder
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.GraphicsLayerScope
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
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
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.bleed
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import com.ngocdevv.nativecook.shared.surface
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class DrawerItem(val id: String, val label: String, val icon: ImageVector)

private val Items = listOf(
    DrawerItem("home", "Home", Icons.Outlined.Home),
    DrawerItem("categories", "Categories", Icons.Outlined.GridView),
    DrawerItem("cart", "My Cart", Icons.Outlined.ShoppingBag),
    DrawerItem("wishlist", "Wish List", Icons.Outlined.FavoriteBorder),
    DrawerItem("orders", "Track Order", Icons.Outlined.Inventory2),
    DrawerItem("account", "Account", Icons.Outlined.Person),
    DrawerItem("settings", "Settings", Icons.Outlined.Settings),
)
private const val PHOTO = "https://images.unsplash.com/photo-1544005313-94ddf0286df2?auto=format&fit=crop&w=208&h=208&q=80"

private val Menu = Color(0xFF52349F)
private val Page = Color(0xFFFAFCFE)
private val Shadow = rgba(30, 15, 77, 0.35f)
private val Hairline = Color.White.copy(alpha = 0.14f)
private val Mark = Color.White.copy(alpha = 0.12f)
private val Faint = Color.White.copy(alpha = 0.6f)

// The open page is a card scaled about the middle of its left edge, which stops PEEK dp short of
// the screen's right edge, and never over the menu's labels.
private const val PEEK = 131f
private const val OPEN_MIN = 262f
private const val SCALE = 0.78f

// Degrees about the card's left edge: its right edge goes away, like a door swung aside.
private const val TILT = 14f
private const val PERSPECTIVE = 900f
private const val LIFT = -12f
private const val RADIUS = 30f

/**
 * A card behind the page: how small, how far further left and how faint it ends, and the power
 * of the page's progress it follows, which makes it trail.
 */
private class Ghost(val scale: Float, val back: Float, val alpha: Float, val trail: Float)

private val Ghosts = listOf(Ghost(0.72f, 18f, 0.18f, 1.4f), Ghost(0.66f, 34f, 0.09f, 1.8f))

// Mass 1, stiffness 140, damping 17; and stiffness 220, damping 28.
private val OpenSpring = spring(dampingRatio = 0.7184f, stiffness = 140f, visibilityThreshold = 0.0005f)
private val CloseSpring = spring(dampingRatio = 0.9439f, stiffness = 220f, visibilityThreshold = 0.0005f)

// Reduced motion: the page only slides, on a short curve.
private const val SLIDE = 250
private val EaseOutCubic = CubicBezierEasing(0.215f, 0.61f, 0.355f, 1f)

// The menu's rows come in one after another, in seconds from the moment the page starts to move.
private const val ROW_DELAY = 0.1f
private const val ROW_GAP = 0.035f
private const val ROW_IN = 0.35f
private const val ROW_SHIFT = 24f
private const val ROWS_OUT = 150

// The profile, a hairline, the items, a hairline, log out.
private fun revealFor(items: Int) = ROW_DELAY + (items + 3) * ROW_GAP + ROW_IN

// A chosen row is marked, the page changes behind the menu, and then the drawer closes.
private const val MARK_IN = 150
private const val CHOICE_HOLD = 400L

// A drag opens the drawer when it starts in this band on the left of the closed page.
private const val EDGE = 80f
private const val FLING = 300f

private const val ROW = 44f
private const val PROFILE_LEAD = 155f
private const val PROFILE_LEAD_MIN = 56f

// From the top of the profile to the bottom of log out with seven items, plus a margin.
private const val MENU_BODY = 504f
private const val SHADOW_BLEED = 72
private const val USER_HOLD = 3000L
private const val LOOP = 9f

private class Step(val at: Float, val open: Boolean = false, val choose: Int? = null)

// The loop that plays by itself: when each state is due, in seconds from the start of a loop.
private val Script = listOf(
    Step(0.8f, open = true),
    Step(3.0f, choose = 3),
    Step(3.4f, open = false),
    Step(5.4f, open = true),
    Step(7.2f, choose = 0),
    Step(7.6f, open = false),
)

private fun easeOut(t: Float): Float {
    val c = t.coerceIn(0f, 1f)
    return 1f - (1f - c).pow(3)
}

private fun type(size: Int, line: Int, color: Color, weight: FontWeight = FontWeight.Normal) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * A card scaled and turned about the middle of its left edge, seen from [PERSPECTIVE] dp away,
 * and then moved [reach] dp to the right; [pivot] is where that edge is in a layer that is wider
 * than the card. With [still] it only moves.
 */
private fun GraphicsLayerScope.swing(g: Float, endScale: Float, reach: Float, pivot: Float = 0f, still: Boolean = false) {
    translationX = reach.dp.toPx() * g
    if (still) return
    transformOrigin = TransformOrigin(pivot, 0.5f)
    scaleX = 1f - (1f - endScale) * g
    scaleY = scaleX
    rotationY = TILT * g
    translationY = LIFT.dp.toPx() * g
    // The camera's distance is counted in units of 72 pixels.
    cameraDistance = PERSPECTIVE * density / 72f
}

/**
 * A side menu with depth. The menu fills the screen underneath; opening it swings the page aside
 * as a rounded card, scaled down and turned about its left edge, with two faint cards trailing
 * behind it, while the menu's rows come in one after another. Choosing a row changes the page
 * behind the menu and closes the drawer.
 *
 * @param items the menu's destinations; the page starts on the first one.
 * @param photo the address of the round picture next to the name.
 * @param menuColor the menu's colour, which also marks the lit tab of the page.
 * @param autoPlay opens the menu and picks destinations by itself, back at the start every [LOOP] seconds.
 */
@Composable
fun DepthSideDrawer(
    modifier: Modifier = Modifier,
    items: List<DrawerItem> = Items,
    name: String = "Mara Ellis",
    place: String = "Portland",
    photo: String = PHOTO,
    logoutLabel: String = "Logout",
    menuColor: Color = Menu,
    autoPlay: Boolean = false,
    onSelect: ((DrawerItem) -> Unit)? = null,
    onLogout: (() -> Unit)? = null,
) {
    val view = LocalView.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Everything the functions below read is state: they are called long after this composition.
    val reduced by rememberUpdatedState(rememberReducedMotion())
    val list by rememberUpdatedState(items)
    val picked by rememberUpdatedState(onSelect)
    val revealTime by rememberUpdatedState(revealFor(items.size))

    var open by remember { mutableStateOf(false) }
    var destination by remember { mutableIntStateOf(0) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }
    // A row was chosen by hand: the drawer closes a moment later unless another touch comes first.
    var chosen by remember { mutableStateOf(false) }

    // 0 with the page in place, 1 with the drawer open; a spring may pass 1 a little.
    val progress = remember { Animatable(0f) }
    // The clock of the rows coming in, and their leaving together.
    val stagger = remember { Animatable(0f) }
    val leave = remember { Animatable(0f) }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    fun slide(to: Float) {
        scope.launch {
            if (reduced) progress.animateTo(to, tween(SLIDE, easing = EaseOutCubic))
            else progress.animateTo(to, if (to == 1f) OpenSpring else CloseSpring)
        }
    }

    fun showRows() {
        scope.launch {
            if (reduced) {
                // Together, with a short fade.
                stagger.snapTo(1f)
                leave.snapTo(1f)
                leave.animateTo(0f, tween(ROWS_OUT))
            } else {
                leave.snapTo(0f)
                stagger.snapTo(0f)
                stagger.animateTo(1f, tween((revealTime * 1000).toInt(), easing = LinearEasing))
            }
        }
    }

    fun hideRows() {
        scope.launch { leave.animateTo(1f, tween(ROWS_OUT)) }
    }

    fun setDrawer(to: Boolean) {
        if (to == open) return
        open = to
        if (to) showRows() else hideRows()
        slide(if (to) 1f else 0f)
    }

    fun go(index: Int) {
        if (index !in list.indices) return
        destination = index
        picked?.invoke(list[index])
    }

    /** A touch takes over from the autoPlay loop, which starts again from rest a while later. */
    fun touched() {
        chosen = false
        touches++
    }

    fun press(to: Boolean) {
        touched()
        tick()
        setDrawer(to)
    }

    fun choose(index: Int) {
        touched()
        tick()
        go(index)
        chosen = true
    }

    fun dragEnded(to: Boolean) {
        if (to != open) tick()
        open = to
        if (!to) hideRows()
        slide(if (to) 1f else 0f)
    }

    LaunchedEffect(chosen, touches) {
        if (!chosen) return@LaunchedEffect
        delay(CHOICE_HOLD)
        chosen = false
        setDrawer(false)
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay(USER_HOLD)
            // Back to rest; the drawer has closed by the time the loop first opens it.
            setDrawer(false)
            destination = 0
        }
        var origin = System.nanoTime()
        suspend fun until(seconds: Float) = delay(max(0L, (origin + (seconds * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            // Each step sets the state that is due at its time, whatever the steps before it did.
            for (step in Script) {
                until(step.at)
                if (step.choose != null) go(step.choose) else setDrawer(step.open)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    // The bars' icons are dark over the page and light over the menu, which the status bar is over
    // once the page is more than half way aside; the gallery's style comes back on exit.
    val overMenu by remember { derivedStateOf { progress.value > 0.5f } }
    val bars = remember(view) { view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) } }
    DisposableEffect(bars) {
        val status = bars?.isAppearanceLightStatusBars
        val navigation = bars?.isAppearanceLightNavigationBars
        onDispose {
            if (status != null) bars.isAppearanceLightStatusBars = status
            if (navigation != null) bars.isAppearanceLightNavigationBars = navigation
        }
    }
    LaunchedEffect(bars, overMenu) {
        bars?.isAppearanceLightStatusBars = !overMenu
        bars?.isAppearanceLightNavigationBars = !overMenu
    }

    val shadow = remember { SoftRoundRect(Shadow, sigma = 20f) }
    val request = remember(photo, context) { ImageRequest.Builder(context).data(photo).build() }

    BoxWithConstraints(modifier.fillMaxSize().background(menuColor)) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding()
        val bottom = insets.calculateBottomPadding()
        val reach by rememberUpdatedState(max(OPEN_MIN, maxWidth.value - PEEK))
        val lead = min(PROFILE_LEAD, max(PROFILE_LEAD_MIN, maxHeight.value - top.value - bottom.value - MENU_BODY))
        val profileTop = top.value + lead
        val rowsTop = profileTop + 96f
        val current = items.getOrElse(destination) { items[0] }

        fun Modifier.reveal(order: Int) = graphicsLayer {
            val shown = easeOut((stagger.value * revealTime - ROW_DELAY - order * ROW_GAP) / ROW_IN) * (1f - leave.value)
            alpha = shown
            translationX = if (reduced) 0f else -ROW_SHIFT.dp.toPx() * (1f - shown)
        }

        Box(
            Modifier.fillMaxSize().pointerInput(Unit) {
                val tracker = VelocityTracker()
                var moving = false
                var from = 0f
                var dragged = 0f
                detectHorizontalDragGestures(
                    onDragStart = { at ->
                        // Closed, the page is dragged from its left edge; open, by the card.
                        moving = if (progress.value < 0.5f) at.x < EDGE.dp.toPx() else at.x > (reach - Ghosts[1].back).dp.toPx()
                        if (moving) {
                            tracker.resetTracking()
                            from = progress.value
                            dragged = 0f
                            touched()
                            if (!open) showRows()
                        }
                    },
                    onDragEnd = {
                        if (moving) {
                            val speed = tracker.calculateVelocity().x / density
                            dragEnded(if (speed > FLING) true else if (speed < -FLING) false else progress.value > 0.5f)
                        }
                        moving = false
                    },
                    onDragCancel = {
                        if (moving) dragEnded(open)
                        moving = false
                    },
                    onHorizontalDrag = { change, amount ->
                        if (moving) {
                            change.consume()
                            tracker.addPosition(change.uptimeMillis, change.position)
                            dragged += amount
                            val to = (from + dragged / reach.dp.toPx()).coerceIn(0f, 1f)
                            scope.launch { progress.snapTo(to) }
                        }
                    },
                )
            },
        ) {
            // The menu, which takes touches only while the drawer is open.
            Box(if (open) Modifier.fillMaxSize() else Modifier.fillMaxSize().clearAndSetSemantics {}) {
                Box(
                    Modifier
                        .offset(11.dp, top - 4.dp)
                        .reveal(0)
                        .size(44.dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open, role = Role.Button) { press(false) }
                        .semantics { contentDescription = "Close menu" },
                    contentAlignment = Alignment.Center,
                ) {
                    Box(Modifier.size(24.dp, 2.dp).rotate(45f).background(Color.White, CircleShape))
                    Box(Modifier.size(24.dp, 2.dp).rotate(-45f).background(Color.White, CircleShape))
                }

                Row(
                    Modifier.offset(24.dp, profileTop.dp).reveal(0).height(52.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AsyncImage(
                        model = request,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.size(52.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.2f)),
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(name, style = type(17, 22, Color.White, FontWeight.SemiBold), maxLines = 1)
                        BasicText(place, style = type(13, 18, Faint), maxLines = 1)
                    }
                }

                Box(Modifier.offset(24.dp, (profileTop + 76f).dp).reveal(1).size(200.dp, 1.dp).background(Hairline))

                items.forEachIndexed { index, item ->
                    val mark by animateFloatAsState(if (index == destination) 1f else 0f, tween(MARK_IN), label = "mark")
                    MenuRow(
                        item.icon,
                        item.label,
                        Modifier
                            .offset(12.dp, (rowsTop + index * ROW).dp)
                            .reveal(index + 2)
                            .drawBehind {
                                val inset = 2.dp.toPx()
                                drawRoundRect(Mark, Offset(0f, inset), Size(size.width, size.height - inset * 2), CornerRadius(size.height / 2), alpha = mark)
                            }
                            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open, role = Role.Button) { choose(index) }
                            .semantics { selected = index == destination },
                    )
                }

                Box(Modifier.offset(24.dp, (rowsTop + items.size * ROW + 20f).dp).reveal(items.size + 2).size(200.dp, 1.dp).background(Hairline))

                MenuRow(
                    Icons.AutoMirrored.Outlined.Logout,
                    logoutLabel,
                    Modifier
                        .offset(12.dp, (rowsTop + items.size * ROW + 40f).dp)
                        .reveal(items.size + 3)
                        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = open, role = Role.Button) {
                            touched()
                            tick()
                            onLogout?.invoke()
                        },
                )
            }

            for (ghost in Ghosts.asReversed()) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            val p = progress.value
                            // Nothing of a ghost shows behind the closed page, so it is not painted there.
                            alpha = if (reduced) 0f else ghost.alpha * (p * 5f).coerceIn(0f, 1f)
                            swing(max(0f, p).pow(ghost.trail), ghost.scale, reach - ghost.back)
                        }
                        .background(Color.White, RoundedCornerShape(RADIUS.dp)),
                )
            }

            // The page's shadow, on a layer of its own that is larger than the page.
            Box(Modifier.fillMaxSize().bleed(SHADOW_BLEED.dp)) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .graphicsLayer { swing(progress.value, SCALE, reach, pivot = SHADOW_BLEED.dp.toPx() / size.width, still = reduced) }
                        .drawBehind {
                            val settled = progress.value.coerceIn(0f, 1f)
                            if (settled > 0f) {
                                surface(SHADOW_BLEED.dp) { x, y, w, h ->
                                    drawIntoCanvas { shadow.draw(it.nativeCanvas, x - 12f, y + 16f, w, h, RADIUS * settled, alpha = settled) }
                                }
                            }
                        },
                )
            }

            // The page is one clipped layer. It stops touches from reaching the menu under it.
            Box(
                Modifier
                    .fillMaxSize()
                    .graphicsLayer {
                        val p = progress.value
                        swing(p, SCALE, reach, still = reduced)
                        shape = RoundedCornerShape((RADIUS * p.coerceIn(0f, 1f)).dp)
                        clip = true
                    }
                    .background(Page)
                    .pointerInput(Unit) {}
                    .then(if (open) Modifier.clearAndSetSemantics {} else Modifier),
            ) {
                ShopPage(
                    title = current.label,
                    index = destination,
                    active = current.id,
                    accent = menuColor,
                    top = top,
                    bottom = bottom,
                    enabled = !open,
                    onMenu = { press(true) },
                    onTab = { id ->
                        touched()
                        tick()
                        go(list.indexOfFirst { it.id == id })
                    },
                )
            }

            if (open) {
                Box(
                    Modifier
                        .fillMaxSize()
                        .padding(start = (reach - Ghosts[1].back).dp)
                        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { press(false) }
                        .semantics { contentDescription = "Close menu" },
                )
            }
        }
    }
}

@Composable
private fun MenuRow(icon: ImageVector, label: String, modifier: Modifier) {
    Row(
        modifier.size(216.dp, ROW.dp).padding(start = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        BasicText(label, style = type(16, 21, Color.White), maxLines = 1)
    }
}
