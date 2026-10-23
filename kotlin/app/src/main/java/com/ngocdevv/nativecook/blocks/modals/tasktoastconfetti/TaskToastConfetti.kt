package com.ngocdevv.nativecook.blocks.modals.tasktoastconfetti

import android.os.Build
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.CardGiftcard
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Notifications
import androidx.compose.material.icons.outlined.PeopleOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.draw.dropShadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.shadow.Shadow
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.font.Typeface
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.TAU
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin

private val Blue = ArtBlue
private val BlueDeep = Color(0xFF0B45E0)
private val Page = Color(0xFFFDFDFF)
private val RowColor = Color(0xFFF2F3F7)
private val Ink = Color(0xFF14161F)
private val Mute = Color(0xFFA3A7B5)
private val BoxIdle = Color(0xFFC3C7D2)
private val Done = Color(0xFFA9AEBB)
private val Orange = Color(0xFFF39A10)
private val ToastColor = Color(0xFFF04E1D)
private val Mic = Color(0xFFFFE9CF)

private const val MARGIN = 16f

/** Below the status bar: the gift icon's row, then the title's row. */
private const val HEADER_BAR = 112f
private const val BANNER = 56f
private const val ROW_HEIGHT = 60f
private const val ROW_GAP = 12f
private const val TOAST_HEIGHT = 52f
private const val HINT_HEIGHT = 62f
private const val NEXT_WIDTH = 262f
private const val NEXT_HEIGHT = 128f
private const val MASCOT = 64f
private const val TAB_BAR = 66f

/** A tall, condensed heavy face for the title and the bubbles, as the design has. */
private val Display = FontFamily(Typeface(android.graphics.Typeface.create("sans-serif-condensed", android.graphics.Typeface.BOLD)))

/** Lines centred in their line height, as React Native sets them. */
private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun display(size: Float, lineHeight: Float, letterSpacing: Float = 0f) = TextStyle(
    fontFamily = Display, fontSize = size.sp, lineHeight = lineHeight.sp, letterSpacing = letterSpacing.sp,
    lineHeightStyle = Centered,
)

private fun plain(size: Float, lineHeight: Float, weight: FontWeight, letterSpacing: Float = 0f) = TextStyle(
    fontSize = size.sp, lineHeight = lineHeight.sp, fontWeight = weight, letterSpacing = letterSpacing.sp,
    lineHeightStyle = Centered,
)

private class Tab(val label: String, val icon: ImageVector)

private val TABS = listOf(
    Tab("Home", Icons.Filled.Home),
    Tab("Teams", Icons.Outlined.PeopleOutline),
    Tab("Progress", Icons.Outlined.BarChart),
    Tab("Discover", Icons.Outlined.Explore),
)

/** A straight mix of two colours, with no gamma, like React Native's `interpolateColor` with `gamma: 1`. */
private fun mix(a: Color, b: Color, t: Float) = Color(
    a.red + (b.red - a.red) * t,
    a.green + (b.green - a.green) * t,
    a.blue + (b.blue - a.blue) * t,
)

/** 1 while the task is ticked: in with the tick, out as it is unticked. */
private fun ticked(t: Float) = seg(t, TICK_AT, TICK_AT + TICK_IN) * (1f - seg(t, UNTICK_AT, UNTICK_AT + UNTICK_IN))

/** React Native's box shadow: a blur radius and a downward offset, in dp. */
private fun Modifier.boxShadow(shape: androidx.compose.ui.graphics.Shape, color: Color, blur: Float, y: Float) =
    dropShadow(shape, Shadow(radius = blur.dp, color = color, offset = DpOffset(0.dp, y.dp)))

/**
 * A task list for today. Ticking the first task strikes the text through, drops a springy toast
 * from the top while confetti bursts out of it, and a speech bubble pops with the next hint beside
 * a mascot. Everything on screen is a function of one clock, so a touch and the autoPlay loop run
 * the same motion.
 *
 * @param task the task that gets ticked.
 * @param otherTasks the tasks under it, which stay as they are.
 * @param reward the small chip at the left of the toast.
 * @param autoPlay ticks the task by itself every [LOOP] seconds and unticks it again.
 * @param onComplete called when the task is ticked by a touch.
 */
@Composable
fun TaskToastConfetti(
    modifier: Modifier = Modifier,
    task: String = "Begin my first habit",
    otherTasks: List<String> = listOf("Drink a glass of water", "Read ten pages"),
    toastLabel: String = "Task completed!",
    reward: String = "+5 ✦",
    hintLabel: String = "Tap to complete",
    nextTitle: String = "Have something in mind?",
    nextLabel: String = "NEXT",
    autoPlay: Boolean = false,
    onComplete: (() -> Unit)? = null,
) {
    val reduced = rememberReducedMotion()
    val view = LocalView.current
    val clock = rememberLoopClock(autoPlay)
    val t = { clock.seconds.value }
    // The width of the task's text, which the strike-through draws across.
    var textWidth by remember { mutableFloatStateOf(0f) }

    val insetTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding().value
    val insetBottom = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().value

    BoxWithConstraints(modifier.fillMaxSize().background(Page)) {
        val width = maxWidth.value
        val header = insetTop + HEADER_BAR
        val firstRow = header + BANNER
        val toastTop = max(insetTop - 6f, 14f)
        val toastWidth = min(width - 2 * 28f, 340f)
        val tabs = TAB_BAR + insetBottom
        val nextBottom = tabs + 22f

        val complete = {
            if (clock.touch()) {
                view.performHapticFeedback(
                    if (Build.VERSION.SDK_INT >= 30) HapticFeedbackConstants.CONFIRM else HapticFeedbackConstants.CONTEXT_CLICK,
                )
                onComplete?.invoke()
            }
        }

        Column {
            // The header: the gift icon's row, then the logo, the title and the avatar.
            Column(
                Modifier
                    .fillMaxWidth()
                    .height(header.dp)
                    .background(Brush.verticalGradient(0f to Blue, 0.7f to Blue, 1f to BlueDeep))
                    .padding(top = insetTop.dp, start = MARGIN.dp, end = MARGIN.dp),
            ) {
                Box(Modifier.fillMaxWidth().height(48.dp).padding(end = 4.dp), contentAlignment = Alignment.CenterEnd) {
                    Icon(Icons.Outlined.CardGiftcard, null, Modifier.size(25.dp), tint = Color.White)
                }
                Row(
                    Modifier.fillMaxWidth().weight(1f).padding(bottom = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Logo()
                    Row(horizontalArrangement = Arrangement.spacedBy(2.dp), verticalAlignment = Alignment.CenterVertically) {
                        Text("Today", color = Color.White, style = display(38f, 44f, -0.6f))
                        Icon(Icons.Filled.KeyboardArrowDown, null, Modifier.padding(top = 6.dp).size(24.dp), tint = Color.White)
                    }
                    Avatar()
                }
            }

            // The banner.
            Row(
                Modifier.fillMaxWidth().height(BANNER.dp).padding(horizontal = 20.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Notifications, null, Modifier.size(22.dp), tint = Blue)
                Text("Get notified about tasks", Modifier.weight(1f), color = Blue, style = plain(17f, 22f, FontWeight.ExtraBold, -0.2f))
                Icon(Icons.Outlined.Close, null, Modifier.size(20.dp), tint = Mute)
            }

            // The task, which a touch ticks.
            TaskRow(
                Modifier
                    .clickable(interactionSource = null, indication = null, role = Role.Checkbox, onClickLabel = task, onClick = complete)
                    .semantics { selected = ticked(t()) > 0.5f },
            ) {
                Box(
                    Modifier.size(28.dp).drawBehind {
                        val on = ticked(t())
                        val stroke = 2.dp.toPx()
                        drawRoundRect(mix(Color.White, Done, on), cornerRadius = CornerRadius(9.dp.toPx()))
                        drawRoundRect(
                            mix(BoxIdle, Done, on),
                            topLeft = Offset(stroke / 2, stroke / 2),
                            size = Size(size.width - stroke, size.height - stroke),
                            cornerRadius = CornerRadius(9.dp.toPx() - stroke / 2),
                            style = Stroke(stroke),
                        )
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Outlined.Check, null,
                        Modifier.size(18.dp).graphicsLayer {
                            val now = t()
                            val into = seg(now, TICK_AT, TICK_AT + TICK_IN)
                            val out = seg(now, UNTICK_AT, UNTICK_AT + UNTICK_IN)
                            // The tick overshoots on its way in: 0.6 -> 1.2 -> 1.
                            val scale = if (into < 0.6f) 0.6f + 0.6f * easeOut(into / 0.6f) else 1.2f - 0.2f * easeOut((into - 0.6f) / 0.4f)
                            alpha = into * (1f - out)
                            scaleX = if (reduced) 1f else scale
                            scaleY = scaleX
                        },
                        tint = Color.White,
                    )
                }
                Text(
                    task,
                    Modifier
                        .graphicsLayer { alpha = 1f - 0.5f * ticked(t()) }
                        .drawWithContent {
                            drawContent()
                            val now = t()
                            val draw = seg(now, TICK_AT + 0.1f, TICK_AT + 0.1f + STRIKE_IN) * (1f - seg(now, UNTICK_AT, UNTICK_AT + UNTICK_IN))
                            drawRoundRect(Ink, Offset(0f, 10.5.dp.toPx()), Size(textWidth * draw, 2.dp.toPx()), CornerRadius(1.dp.toPx()))
                        },
                    color = Ink, style = plain(17f, 22f, FontWeight.SemiBold, -0.2f),
                    maxLines = 1, softWrap = false,
                    onTextLayout = { textWidth = it.size.width.toFloat() },
                )
            }
            otherTasks.forEach { label ->
                Spacer(Modifier.height(ROW_GAP.dp))
                TaskRow(Modifier) {
                    Box(Modifier.size(28.dp).background(Color.White, RoundedCornerShape(9.dp)).border(2.dp, BoxIdle, RoundedCornerShape(9.dp)))
                    Text(label, color = Ink, style = plain(17f, 22f, FontWeight.SemiBold, -0.2f), maxLines = 1)
                }
            }
        }

        // The first hint, under the task. It scales about a point 36 dp in from its top-left corner.
        Box(
            Modifier
                .offset(x = (MARGIN + 2f).dp, y = (firstRow + ROW_HEIGHT + 12f).dp)
                .height(HINT_HEIGHT.dp)
                .graphicsLayer {
                    val now = t()
                    val gone = seg(now, HINT_OUT_AT, HINT_OUT_AT + HINT_OUT_IN)
                    val back = seg(now, HINT_BACK_AT, HINT_BACK_AT + HINT_BACK_IN)
                    val present = if (now < HINT_BACK_AT) 1f - gone else back
                    val scale = if (reduced) 1f else if (now < HINT_BACK_AT) 1f - easeIn(gone) else spring(now - HINT_BACK_AT, POP_STIFFNESS, POP_DAMPING)
                    val bob = if (reduced || present < 1f) 0f else BOB * sin(now / BOB_PERIOD * TAU)
                    alpha = if (reduced) present else min(1f, present * 4f)
                    translationY = bob * density
                    transformOrigin = TransformOrigin(36.dp.toPx() / size.width, 0f)
                    // Fades without an offscreen buffer, which would cut the shadow off at the edges.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    scaleX = max(scale, 0f)
                    scaleY = scaleX
                }
                .boxShadow(CircleShape, Color(214, 120, 0).copy(alpha = 0.30f), 24f, 10f)
                .boxShadow(CircleShape, Color(150, 80, 0).copy(alpha = 0.18f), 4f, 2f)
                .drawBehind {
                    drawRoundRect(Orange, cornerRadius = CornerRadius(size.height / 2))
                    // The tail: a rounded square turned 45 degrees, poking out of the top edge.
                    val side = 22.dp.toPx()
                    val corner = Offset(26.dp.toPx(), -7.dp.toPx())
                    rotate(45f, corner + Offset(side / 2, side / 2)) {
                        drawRoundRect(Orange, corner, Size(side, side), CornerRadius(4.dp.toPx()))
                    }
                }
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Text(hintLabel, color = Color.White, style = display(28f, 34f, -0.3f), maxLines = 1, softWrap = false)
        }

        // The footer: a scribble pointing down, the microphone and the plus.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = MARGIN.dp, end = MARGIN.dp, bottom = (tabs + 14f).dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(Modifier.padding(start = 56.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "Looking 4\ninspiration?", Modifier.graphicsLayer { rotationZ = -3f },
                    color = Scribble, style = display(25f, 27f), textAlign = TextAlign.Center,
                )
                ScribbleArrow()
            }
            Row(
                Modifier.padding(bottom = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.size(62.dp).background(Mic, CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Filled.Mic, null, Modifier.size(26.dp), tint = Blue)
                }
                Box(
                    Modifier
                        .size(74.dp)
                        .boxShadow(CircleShape, Color(26, 92, 255).copy(alpha = 0.35f), 22f, 10f)
                        .boxShadow(CircleShape, Color(10, 40, 140).copy(alpha = 0.25f), 5f, 2f)
                        .background(Blue, CircleShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Outlined.Add, null, Modifier.size(40.dp), tint = Color.White)
                }
            }
        }

        // The tab bar.
        Row(
            Modifier
                .align(Alignment.BottomStart)
                .fillMaxWidth()
                .height(tabs.dp)
                .background(Page)
                .padding(top = 8.dp, bottom = insetBottom.dp),
        ) {
            TABS.forEachIndexed { i, tab ->
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    val color = if (i == 0) Blue else Mute
                    Icon(tab.icon, null, Modifier.size(27.dp), tint = color)
                    Text(tab.label, color = color, style = plain(13f, 16f, FontWeight.SemiBold))
                }
            }
        }

        // The next hint, which grows from its bottom-left corner.
        Column(
            Modifier
                .align(Alignment.BottomEnd)
                .padding(end = MARGIN.dp, bottom = nextBottom.dp)
                .size(NEXT_WIDTH.dp, NEXT_HEIGHT.dp)
                .graphicsLayer {
                    val now = t()
                    val away = seg(now, NEXT_AWAY_AT, NEXT_AWAY_AT + NEXT_AWAY_IN)
                    val pop = if (now < NEXT_AWAY_AT) spring(now - NEXT_AT - 0.08f, POP_STIFFNESS, POP_DAMPING) else 1f - easeIn(away)
                    val visible = now >= NEXT_AT && now < NEXT_AWAY_AT + NEXT_AWAY_IN
                    transformOrigin = TransformOrigin(0f, 1f)
                    // Fades without an offscreen buffer, which would cut the shadow off at the edges.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    if (reduced) {
                        alpha = if (visible) seg(now, NEXT_AT, NEXT_AT + REDUCED_FADE) * (1f - away) else 0f
                    } else {
                        alpha = if (visible) 1f else 0f
                        scaleX = max(pop, 0f)
                        scaleY = scaleX
                    }
                }
                .boxShadow(RoundedCornerShape(40.dp), Color(214, 120, 0).copy(alpha = 0.32f), 36f, 18f)
                .boxShadow(RoundedCornerShape(40.dp), Color(150, 80, 0).copy(alpha = 0.2f), 8f, 3f)
                .drawBehind {
                    drawRoundRect(Orange, cornerRadius = CornerRadius(40.dp.toPx()))
                    // The tail: a rounded square turned 45 degrees, poking out of the left edge.
                    val side = 26.dp.toPx()
                    val corner = Offset(-7.dp.toPx(), size.height - 20.dp.toPx() - side)
                    rotate(45f, corner + Offset(side / 2, side / 2)) {
                        drawRoundRect(Orange, corner, Size(side, side), CornerRadius(5.dp.toPx()))
                    }
                }
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(nextTitle, color = Color.White, style = display(30f, 32f, -0.3f))
            Text(
                "$nextLabel ›", Modifier.align(Alignment.End),
                color = Color.White, style = plain(15f, 18f, FontWeight.Black, 0.4f),
            )
        }

        // The mascot, which hops in beside the next hint.
        Box(
            Modifier
                .align(Alignment.BottomStart)
                .padding(start = max(6f, width - MARGIN - NEXT_WIDTH - MASCOT - 2f).dp, bottom = (nextBottom - 10f).dp)
                .graphicsLayer {
                    val now = t()
                    val away = seg(now, NEXT_AWAY_AT, NEXT_AWAY_AT + NEXT_AWAY_IN)
                    val pop = if (now < NEXT_AWAY_AT) spring(now - NEXT_AT, 220f, 9f) else 1f - easeIn(away)
                    val visible = now >= NEXT_AT && now < NEXT_AWAY_AT + NEXT_AWAY_IN
                    if (reduced) {
                        alpha = if (visible) seg(now, NEXT_AT, NEXT_AT + REDUCED_FADE) * (1f - away) else 0f
                    } else {
                        // It hops once more as the confetti lands, then sits still.
                        val hop = if (now < NEXT_AWAY_AT) -10f * max(0f, sin(min(1f, seg(now, CONFETTI_AT, CONFETTI_AT + 0.45f)) * Math.PI.toFloat())) else 0f
                        val p = max(pop, 0f)
                        alpha = if (visible) 1f else 0f
                        translationY = ((1f - p) * 28f + hop) * density
                        scaleX = p
                        scaleY = p
                        rotationZ = (1f - min(pop, 1f)) * -18f
                    }
                },
        ) { Mascot() }

        if (!reduced) Confetti(clock, CONFETTI_AT, Offset(width / 2f, toastTop + TOAST_HEIGHT / 2f), Modifier.fillMaxSize())

        // The toast, over everything.
        Row(
            Modifier
                .offset(x = ((width - toastWidth) / 2f).dp, y = toastTop.dp)
                .size(toastWidth.dp, TOAST_HEIGHT.dp)
                .graphicsLayer {
                    val now = t()
                    val away = seg(now, TOAST_AWAY_AT, TOAST_AWAY_AT + TOAST_AWAY_IN)
                    val visible = now >= TOAST_AT && now < TOAST_AWAY_AT + TOAST_AWAY_IN
                    // Fades without an offscreen buffer, which would cut the shadow off at the edges.
                    compositingStrategy = CompositingStrategy.ModulateAlpha
                    if (reduced) {
                        alpha = if (visible) seg(now, TOAST_AT, TOAST_AT + REDUCED_FADE) * (1f - seg(now, TOAST_AWAY_AT, TOAST_AWAY_AT + REDUCED_FADE)) else 0f
                    } else {
                        // From above the screen to its place, past it and back; away, it goes up and out again.
                        val drop = spring(now - TOAST_AT, TOAST_STIFFNESS, TOAST_DAMPING)
                        val rise = easeIn(away)
                        val offscreen = toastTop + TOAST_HEIGHT + 16f
                        alpha = if (visible) 1f else 0f
                        translationY = (-offscreen * (1f - drop) - offscreen * rise) * density
                        scaleX = (0.8f + 0.2f * min(drop, 1f)) * (1f - 0.2f * rise)
                        scaleY = scaleX
                    }
                }
                .boxShadow(CircleShape, Color(190, 50, 10).copy(alpha = 0.35f), 28f, 14f)
                .boxShadow(CircleShape, Color(120, 30, 0).copy(alpha = 0.25f), 6f, 3f)
                .background(Brush.verticalGradient(listOf(Color(0xFFFF6A35), ToastColor)), CircleShape)
                .padding(horizontal = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                Modifier.height(28.dp).background(Color.White, CircleShape).padding(horizontal = 10.dp).wrapContentSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(reward, color = ToastColor, style = plain(14f, 18f, FontWeight.Black))
            }
            Text(toastLabel, color = Color.White, style = plain(18f, 22f, FontWeight.ExtraBold, -0.2f), maxLines = 1)
        }
    }
}

/** A task's grey pill: a box and its text. */
@Composable
private fun TaskRow(modifier: Modifier, content: @Composable () -> Unit) {
    Row(
        Modifier
            .padding(horizontal = MARGIN.dp)
            .fillMaxWidth()
            .height(ROW_HEIGHT.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(RowColor)
            .then(modifier)
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) { content() }
}
