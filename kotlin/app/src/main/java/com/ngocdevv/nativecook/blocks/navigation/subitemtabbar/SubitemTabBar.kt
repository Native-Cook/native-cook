package com.ngocdevv.nativecook.blocks.navigation.subitemtabbar

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.Login
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Book
import androidx.compose.material.icons.outlined.Brush
import androidx.compose.material.icons.outlined.Build
import androidx.compose.material.icons.outlined.CalendarToday
import androidx.compose.material.icons.outlined.Code
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.Eco
import androidx.compose.material.icons.outlined.Flight
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MailOutline
import androidx.compose.material.icons.outlined.PersonOutline
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material.icons.outlined.ViewInAr
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WorkOutline
import androidx.compose.material.icons.outlined.WorkspacePremium
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlurEffect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.SoftRoundRect
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import com.ngocdevv.nativecook.shared.sigmaToRadius
import kotlinx.coroutines.delay
import kotlin.math.max
import kotlin.math.sqrt

data class Chip(val label: String, val icon: ImageVector)

/** @param rows The chips of the tab's panel, row by row. A tab without rows opens the sign-in form. */
data class Tab(val label: String, val icon: ImageVector, val rows: List<List<Chip>>? = null)

val SampleTabs = listOf(
    Tab(
        "Work", Icons.Outlined.WorkOutline,
        listOf(
            listOf(Chip("All", Icons.Outlined.GridView), Chip("Design", Icons.Outlined.Brush), Chip("Dev", Icons.Outlined.Code)),
            listOf(Chip("Motion", Icons.Outlined.AutoAwesome), Chip("Research", Icons.Outlined.Search), Chip("Branding", Icons.Outlined.Verified)),
            listOf(Chip("3D", Icons.Outlined.ViewInAr)),
        ),
    ),
    Tab(
        "About", Icons.Outlined.PersonOutline,
        listOf(
            listOf(Chip("Story", Icons.Outlined.Book), Chip("Skills", Icons.Outlined.StarBorder), Chip("Contact", Icons.Outlined.MailOutline)),
            listOf(Chip("Resume", Icons.Outlined.Description), Chip("Awards", Icons.Outlined.WorkspacePremium), Chip("Timeline", Icons.Outlined.CalendarToday)),
        ),
    ),
    Tab(
        "Journal", Icons.AutoMirrored.Outlined.MenuBook,
        listOf(
            listOf(Chip("All", Icons.Outlined.GridView), Chip("Design", Icons.Outlined.Brush), Chip("Code", Icons.Outlined.Code), Chip("Life", Icons.Outlined.Eco)),
            listOf(Chip("Travel", Icons.Outlined.Flight), Chip("Process", Icons.Outlined.Settings), Chip("Insights", Icons.Outlined.Lightbulb)),
            listOf(Chip("Tools", Icons.Outlined.Build)),
        ),
    ),
    Tab("Login", Icons.AutoMirrored.Outlined.Login),
)

/** The period of the autoPlay loop, in seconds, and the tab it presses at each moment of it. */
internal const val LOOP = 10f

// The last press is on the tab that is open, which closes the bar.
private val Steps = listOf(0.8f to 0, 2.6f to 1, 4.2f to 2, 5.8f to 3, 7.4f to 0, 8.8f to 0)

// After a touch the loop waits this long, closes the bar and starts over.
private const val USER_HOLD = 3f

private val ChipFill = Color(0xFF1A1A1C)
private val ChipSelected = Color(0xFF2E2E30)
private val ChipInk = Color(0xFFA1A1A6)
private val ChipInkSelected = Color.White
private val FieldFill = Color(0xFF1A1A1C)
private val Placeholder = Color(0xFF6E6E73)
private val Hairline = Color.White.copy(alpha = 0.08f)

// The page's placeholders: shapes, the card under the title, the outline of a group.
private val Shape = Color(0xFFE4E4E8)
private val Card = Color(0xFFF6F6F8)
private val Outline = Color(0xFFEEEEF1)

private const val MARGIN = 20f
private const val TITLE_TOP = 14f
private const val BAR_WIDTH = 345f
private const val BAR_RADIUS = 20f

// The bar's bottom edge above the bottom of the screen. A bottom inset taller than a gesture bar's
// is a row of buttons, which the bar stays clear of.
private const val BAR_BOTTOM = 24f
private const val GESTURE_INSET = 34f
private const val BUTTONS_CLEARANCE = 8f

// The bar's shadow: its drop and the sigma of its blur (a 24 dp blur).
private const val BAR_SHADOW_Y = 8f
private const val BAR_SHADOW_SIGMA = 12f
private const val TAB_ROW = 66f
private const val TAB_ICON = 22f
private const val PAD = 12f
private const val GAP = 8f
private const val CHIP_HEIGHT = 30f
private const val CHIP_ICON = 13f
private const val FIELD_HEIGHT = 44f
private const val FIELD_ICON = 16f
private const val LINK_HEIGHT = 16f
private const val FORM_HEIGHT = PAD * 2 + FIELD_HEIGHT * 3 + GAP * 3 + LINK_HEIGHT

// The page behind an open panel: blurred (from Android 12 on) and washed with white.
private const val BLUR = 12f
private const val WASH = 0.4f

// The bar's height follows a spring that overshoots by about a twentieth: mass 1.
private const val SPRING_STIFFNESS = 250f
private const val SPRING_DAMPING = 23f

// Milliseconds. The page blurs; a tab and a chip cross-fade to their other look.
private const val VEIL = 300
private const val TAB_FADE = 200
private const val CHIP_FADE = 200

// A panel's pieces come in one after the other in reading order, each fading in while it rises,
// and leave together, faster.
private const val REVEAL_DELAY = 80
private const val STAGGER = 25
private const val REVEAL_IN = 300
private const val REVEAL_OUT = 150
private const val RISE = 6f

// A cubic ease-out.
private val Ease = CubicBezierEasing(0.33f, 1f, 0.68f, 1f)

private fun text(size: Float, lineHeight: Float, weight: FontWeight, color: Color) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None),
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

private val ChipStyle = text(13f, 16f, FontWeight.Medium, ChipInk)
private val FieldStyle = text(14f, 18f, FontWeight.Normal, Color.White)
private val ForgotStyle = text(12f, LINK_HEIGHT, FontWeight.Normal, Placeholder)

private fun panelHeight(tab: Tab): Float {
    val rows = tab.rows ?: return FORM_HEIGHT
    return PAD * 2 + rows.size * CHIP_HEIGHT + (rows.size - 1) * GAP
}

private fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/** An icon drawn in a box of [size] dp, tinted while drawing so a changing colour does not recompose. */
@Composable
private fun Glyph(icon: ImageVector, size: Float, modifier: Modifier = Modifier, color: () -> Color) {
    val painter = rememberVectorPainter(icon)
    Box(modifier.size(size.dp).drawBehind { with(painter) { draw(this@drawBehind.size, colorFilter = ColorFilter.tint(color())) } })
}

/**
 * One piece of a panel: it fades in and rises when its panel opens, [index] steps after the first,
 * and the pieces leave together, faster. With animations off in the system it appears at once.
 */
@Composable
private fun Reveal(shown: Boolean, index: Int, still: Boolean, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val progress = remember { Animatable(0f) }
    LaunchedEffect(shown) {
        if (shown) {
            progress.animateTo(1f, tween(REVEAL_IN, delayMillis = REVEAL_DELAY + index * STAGGER, easing = Ease))
        } else {
            progress.animateTo(0f, tween(REVEAL_OUT, easing = Ease))
        }
    }
    Box(
        modifier.graphicsLayer {
            alpha = progress.value
            translationY = if (still) 0f else RISE.dp.toPx() * (1f - progress.value)
        },
    ) { content() }
}

@Composable
private fun ChipButton(chip: Chip, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    val fill = animateColorAsState(if (selected) ChipSelected else ChipFill, tween(CHIP_FADE, easing = Ease), label = "chip")
    val ink = animateColorAsState(if (selected) ChipInkSelected else ChipInk, tween(CHIP_FADE, easing = Ease), label = "chip ink")
    Row(
        Modifier
            .height(CHIP_HEIGHT.dp)
            .clip(CircleShape)
            .drawBehind { drawRect(fill.value) }
            .clickable(remember { MutableInteractionSource() }, indication = null, enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { this.selected = selected }
            .padding(horizontal = PAD.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(chip.icon, CHIP_ICON) { ink.value }
        BasicText(chip.label, style = ChipStyle, color = { ink.value }, maxLines = 1)
    }
}

/** A tab: its grey face and, over it while its panel is open, the white one with a heavier label. */
@Composable
private fun TabButton(tab: Tab, active: Boolean, activeColor: Color, inactiveColor: Color, modifier: Modifier, onClick: () -> Unit) {
    val lit = animateFloatAsState(if (active) 1f else 0f, tween(TAB_FADE, easing = Ease), label = "tab")
    Box(
        modifier
            .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Tab, onClick = onClick)
            .semantics {
                contentDescription = tab.label
                selected = active
            },
        contentAlignment = Alignment.Center,
    ) {
        Column(Modifier.graphicsLayer { alpha = 1f - lit.value }, horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(tab.icon, TAB_ICON) { inactiveColor }
            Spacer(Modifier.height(4.dp))
            BasicText(tab.label, style = text(11f, 14f, FontWeight.Normal, inactiveColor), maxLines = 1)
        }
        Column(Modifier.graphicsLayer { alpha = lit.value }, horizontalAlignment = Alignment.CenterHorizontally) {
            Glyph(tab.icon, TAB_ICON) { activeColor }
            Spacer(Modifier.height(4.dp))
            BasicText(tab.label, style = text(11f, 14f, FontWeight.SemiBold, activeColor), maxLines = 1)
        }
    }
}

@Composable
private fun Field(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    icon: ImageVector,
    enabled: Boolean,
    cursor: Color,
    keyboardOptions: KeyboardOptions,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable () -> Unit = {},
) {
    Row(
        Modifier.fillMaxWidth().height(FIELD_HEIGHT.dp).background(FieldFill, RoundedCornerShape(12.dp)).padding(start = 14.dp, end = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Glyph(icon, FIELD_ICON) { Placeholder }
        BasicTextField(
            value,
            onValueChange,
            Modifier.weight(1f),
            enabled = enabled,
            singleLine = true,
            textStyle = FieldStyle,
            cursorBrush = SolidColor(cursor),
            keyboardOptions = keyboardOptions,
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box(contentAlignment = Alignment.CenterStart) {
                    if (value.isEmpty()) BasicText(placeholder, style = FieldStyle.copy(color = Placeholder), maxLines = 1)
                    inner()
                }
            },
        )
        trailing()
    }
}

/** The page behind the bar: placeholders only. */
@Composable
private fun Page() {
    Box(Modifier.padding(bottom = 4.dp).size(68.dp, 20.dp).background(Shape, RoundedCornerShape(8.dp)))
    Row(
        Modifier.fillMaxWidth().height(64.dp).background(Card, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(36.dp).background(Shape, CircleShape))
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.size(96.dp, 8.dp).background(Shape, CircleShape))
            Box(Modifier.size(60.dp, 6.dp).background(Shape, CircleShape))
        }
        Box(Modifier.size(16.dp).background(Shape, RoundedCornerShape(5.dp)))
    }
    for (widths in listOf(listOf(72f, 72f, 100f), listOf(72f, 100f, 100f, 84f))) {
        Column(Modifier.fillMaxWidth().border(1.dp, Outline, RoundedCornerShape(16.dp)).padding(horizontal = 14.dp)) {
            widths.forEachIndexed { row, width ->
                Row(
                    Modifier.fillMaxWidth().height(44.dp).drawBehind {
                        if (row < widths.size - 1) drawRect(Outline, Offset(0f, size.height - 1.dp.toPx()), size.copy(height = 1.dp.toPx()))
                    },
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(Modifier.size(20.dp).background(Shape, CircleShape))
                    Box(Modifier.size(width.dp, 8.dp).background(Shape, CircleShape))
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(24.dp, 6.dp).background(Shape, CircleShape))
                }
            }
        }
    }
}

/**
 * A page with a black bar of four tabs docked at the bottom. Pressing a tab makes the bar grow
 * upward on a spring to hold that tab's panel (rows of filter chips, or a sign-in form) whose
 * pieces come in one after the other, while the page behind blurs and pales. Another tab swaps
 * the panel and the height; the open tab, or the page, closes the bar.
 *
 * @param activeColor The icon and the label of the tab whose panel is open.
 * @param autoPlay Opens the panels one after the other by itself every [LOOP] seconds.
 * @param onOpen Called with the tab whose panel opened, or null when the bar closed.
 * @param onChip Called with a tab and the place of the chip chosen in it, counted along its rows.
 */
@Composable
fun SubitemTabBar(
    tabs: List<Tab> = SampleTabs,
    emailPlaceholder: String = "Email",
    passwordPlaceholder: String = "Password",
    signInLabel: String = "Sign in",
    forgotLabel: String = "Forgot password?",
    background: Color = Color.White,
    barColor: Color = Color(0xFF0D0D0D),
    activeColor: Color = Color.White,
    inactiveColor: Color = Color(0xFF8E8E93),
    autoPlay: Boolean = false,
    onOpen: ((tab: Int?) -> Unit)? = null,
    onChip: ((tab: Int, chip: Int) -> Unit)? = null,
    onSignIn: ((email: String, password: String) -> Unit)? = null,
    onForgot: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val still = rememberReducedMotion()
    val view = LocalView.current
    val focus = LocalFocusManager.current
    // The loop outlives a recomposition: it reads what the caller passes through these.
    val panels by rememberUpdatedState(tabs)
    val opened by rememberUpdatedState(onOpen)

    // The tab whose panel is open; null while the bar is closed.
    var open by remember { mutableStateOf<Int?>(null) }
    // The chip chosen in each tab, by the tab's place; the first chip until one is pressed.
    var chosen by remember { mutableStateOf(mapOf<Int, Int>()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var concealed by remember { mutableStateOf(true) }
    // Changes on every touch, which restarts the autoPlay loop behind a pause.
    var touches by remember { mutableIntStateOf(0) }

    // The bar's height in dp. Read while measuring only.
    val height = remember { Animatable(TAB_ROW) }
    // How far the page is blurred and washed out, 0 to 1. Read while drawing only.
    val veil = animateFloatAsState(if (open != null) 1f else 0f, tween(VEIL, easing = Ease), label = "veil")
    val line = animateFloatAsState(if (open != null) 1f else 0f, tween(TAB_FADE, easing = Ease), label = "hairline")

    // The screen is light: dark icons on the status bar while it shows.
    DisposableEffect(view) {
        val controller = view.context.activity()?.window?.let { WindowCompat.getInsetsController(it, view) }
        val previous = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = true
        onDispose { if (previous != null) controller.isAppearanceLightStatusBars = previous }
    }

    fun tick() = view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK)

    fun show(next: Int?) {
        if (next == open) return
        focus.clearFocus()
        open = next
        opened?.invoke(next)
    }

    /** A press on a tab opens its panel, or closes the bar when that panel is the open one. */
    fun press(index: Int, byHand: Boolean) {
        // The loop that plays by itself is silent.
        if (byHand) tick()
        show(if (index == open) null else index)
    }

    fun rest() {
        show(null)
        chosen = emptyMap()
        email = ""
        password = ""
        concealed = true
    }

    // With animations off in the system the spring lands at once, which is the jump reduced motion asks for.
    LaunchedEffect(open) {
        val target = TAB_ROW + (open?.let { panelHeight(panels[it]) } ?: 0f)
        height.animateTo(target, spring(dampingRatio = SPRING_DAMPING / (2f * sqrt(SPRING_STIFFNESS)), stiffness = SPRING_STIFFNESS))
    }

    LaunchedEffect(autoPlay, touches) {
        if (!autoPlay) return@LaunchedEffect
        if (touches > 0) {
            delay((USER_HOLD * 1000).toLong())
            rest()
        }
        // Every step is timed from the loop's origin, so a late wake does not push the next one back.
        var origin = System.nanoTime()
        suspend fun until(at: Float) = delay(max(0L, (origin + (at * 1e9f).toLong() - System.nanoTime()) / 1_000_000))
        while (true) {
            for ((at, tab) in Steps) {
                until(at)
                press(tab, byHand = false)
            }
            until(LOOP)
            origin += (LOOP * 1e9f).toLong()
        }
    }

    val canBlur = android.os.Build.VERSION.SDK_INT >= 31 && !still
    val shadow = remember { SoftRoundRect(Color.Black.copy(alpha = 0.25f), BAR_SHADOW_SIGMA) }

    Box(modifier.fillMaxSize().background(background)) {
        val bars = WindowInsets.systemBars.union(WindowInsets.displayCutout).asPaddingValues()
        val navigation = bars.calculateBottomPadding().value
        val bottom = if (navigation > GESTURE_INSET) navigation + BUTTONS_CLEARANCE else BAR_BOTTOM

        Column(
            Modifier
                .matchParentSize()
                .graphicsLayer {
                    val sigma = BLUR.dp.toPx() * veil.value
                    renderEffect = if (canBlur && sigma > 0.5f) {
                        val radius = sigmaToRadius(sigma)
                        BlurEffect(radius, radius, TileMode.Clamp)
                    } else {
                        null
                    }
                }
                .then(if (open != null) Modifier.clearAndSetSemantics {} else Modifier)
                .padding(start = MARGIN.dp, end = MARGIN.dp, top = bars.calculateTopPadding() + TITLE_TOP.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) { Page() }

        // The wash over the page, which also closes the bar.
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { alpha = WASH * veil.value }
                .background(Color.White)
                .then(
                    if (open == null) Modifier else Modifier.clickable(remember { MutableInteractionSource() }, indication = null, onClickLabel = "Close") {
                        touches++
                        show(null)
                    },
                ),
        )

        // The bar rides above the keyboard.
        Box(Modifier.fillMaxSize().imePadding().padding(bottom = bottom.dp), contentAlignment = Alignment.BottomCenter) {
            Box(
                Modifier
                    .width(BAR_WIDTH.dp)
                    .layout { measurable, constraints ->
                        val tall = height.value.dp.roundToPx()
                        val placeable = measurable.measure(constraints.copy(minHeight = tall, maxHeight = tall))
                        layout(placeable.width, tall) { placeable.place(0, 0) }
                    }
                    .drawBehind {
                        scale(density, density, pivot = Offset.Zero) {
                            drawIntoCanvas { shadow.draw(it.nativeCanvas, 0f, BAR_SHADOW_Y, size.width / density, size.height / density, BAR_RADIUS) }
                        }
                        drawRoundRect(barColor, cornerRadius = CornerRadius(BAR_RADIUS.dp.toPx()))
                    }
                    .clip(RoundedCornerShape(BAR_RADIUS.dp)),
            ) {
                tabs.forEachIndexed { t, tab ->
                    val shown = open == t
                    // A panel keeps its place above the tabs: the bar's top edge uncovers it. A panel that
                    // is not shown is invisible but still there, so its controls are switched off.
                    Box(
                        Modifier
                            .matchParentSize()
                            .padding(bottom = TAB_ROW.dp)
                            .wrapContentHeight(Alignment.Bottom, unbounded = true)
                            .then(if (shown) Modifier else Modifier.clearAndSetSemantics {}),
                    ) {
                        Column(Modifier.fillMaxWidth().height(panelHeight(tab).dp).padding(PAD.dp), verticalArrangement = Arrangement.spacedBy(GAP.dp)) {
                            val rows = tab.rows
                            if (rows == null) {
                                Reveal(shown, 0, still) {
                                    Field(
                                        email,
                                        {
                                            touches++
                                            email = it
                                        },
                                        emailPlaceholder,
                                        Icons.Outlined.MailOutline,
                                        enabled = shown,
                                        cursor = activeColor,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Email),
                                    )
                                }
                                Reveal(shown, 1, still) {
                                    Field(
                                        password,
                                        {
                                            touches++
                                            password = it
                                        },
                                        passwordPlaceholder,
                                        Icons.Outlined.Lock,
                                        enabled = shown,
                                        cursor = activeColor,
                                        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.None, autoCorrectEnabled = false, keyboardType = KeyboardType.Password),
                                        visualTransformation = if (concealed) PasswordVisualTransformation() else VisualTransformation.None,
                                    ) {
                                        Box(
                                            Modifier
                                                .size(28.dp, FIELD_HEIGHT.dp)
                                                .clickable(remember { MutableInteractionSource() }, indication = null, enabled = shown, role = Role.Button) {
                                                    touches++
                                                    concealed = !concealed
                                                }
                                                .semantics { contentDescription = if (concealed) "Show password" else "Hide password" },
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Glyph(if (concealed) Icons.Outlined.Visibility else Icons.Outlined.VisibilityOff, FIELD_ICON) { Placeholder }
                                        }
                                    }
                                }
                                Reveal(shown, 2, still) {
                                    val source = remember { MutableInteractionSource() }
                                    val pressed by source.collectIsPressedAsState()
                                    Box(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(FIELD_HEIGHT.dp)
                                            .graphicsLayer { alpha = if (pressed) 0.8f else 1f }
                                            .background(activeColor, RoundedCornerShape(12.dp))
                                            .clickable(source, indication = null, enabled = shown, role = Role.Button) {
                                                touches++
                                                tick()
                                                onSignIn?.invoke(email, password)
                                            },
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        BasicText(signInLabel, style = text(14f, 18f, FontWeight.SemiBold, barColor), maxLines = 1)
                                    }
                                }
                                Reveal(shown, 3, still, Modifier.align(Alignment.CenterHorizontally)) {
                                    BasicText(
                                        forgotLabel,
                                        style = ForgotStyle,
                                        maxLines = 1,
                                        modifier = Modifier.clickable(remember { MutableInteractionSource() }, indication = null, enabled = shown) {
                                            touches++
                                            onForgot?.invoke()
                                        },
                                    )
                                }
                            } else {
                                var place = 0
                                for (row in rows) {
                                    Row(horizontalArrangement = Arrangement.spacedBy(GAP.dp)) {
                                        for (chip in row) {
                                            val here = place++
                                            Reveal(shown, here, still) {
                                                ChipButton(chip, selected = (chosen[t] ?: 0) == here, enabled = shown) {
                                                    touches++
                                                    tick()
                                                    chosen = chosen + (t to here)
                                                    onChip?.invoke(t, here)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Box(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(start = PAD.dp, end = PAD.dp, bottom = TAB_ROW.dp)
                        .fillMaxWidth()
                        .height(1.dp)
                        .graphicsLayer { alpha = line.value }
                        .background(Hairline),
                )

                Row(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(TAB_ROW.dp)) {
                    tabs.forEachIndexed { t, tab ->
                        TabButton(tab, active = open == t, activeColor, inactiveColor, Modifier.weight(1f).height(TAB_ROW.dp)) {
                            touches++
                            press(t, byHand = true)
                        }
                    }
                }
            }
        }
    }
}
