package com.ngocdevv.nativecook.blocks.onboarding.walletunpackwelcome

import android.view.HapticFeedbackConstants
import androidx.activity.compose.LocalActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.max

private val Ink = Color(0xFF111111)
private val TaglineInk = Color(0xFF333333)
private val Rule = Color(0xFF888888)
private val TeaserInk = Color(0x99555555)
private val CardTop = Color(0xFF232323)
private val CardBottom = Color(0xFF0E0E0E)
private val ChipTop = Color(0xFFE8D2A0)
private val ChipBottom = Color(0xFFB39459)
private val ChipLine = Color(122, 98, 56).copy(alpha = 0.7f)
private val CardInk = Color(0xFFE5E5E5)
private val CardMute = Color(0xFFA8A8A8)
private val CardLabelInk = Color(0xFFCFCFCF)

// Layout, in dp: the header from the safe area's top, the footer from its bottom, the hero a
// little below the middle.
private const val BACK_TOP = 40f
private const val OVERLINE_TOP = 127f
private const val TITLE_TOP = 144f
private const val HERO_AT = 0.545f
private const val TAGLINE_UP = 211f
private const val RULE_UP = 147f
private const val RULE_X = 48f
private const val SUBTITLE_UP = 92f
private const val BUTTON_UP = 74f
private const val BUTTON_HEIGHT = 44f
private const val MARGIN = 24f
private const val TEASER_UP = 49f
private const val TEASER_X = 72f
private const val FADE = 120f
private const val CARD_W = 200f
private const val CARD_H = 128f
private const val CARD_RADIUS = 12f
/** The perspective the card tips back in, in dp from the eye. */
private const val PERSPECTIVE = 700f
private const val PRESSED = 0.97f

private val Centered = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.None)

private fun walletText(
    size: Float,
    lineHeight: Float,
    weight: FontWeight,
    color: Color,
    spacing: Float = 0f,
    align: TextAlign = TextAlign.Center,
    family: FontFamily = FontFamily.Default,
) = TextStyle(
    color = color,
    fontSize = size.sp,
    lineHeight = lineHeight.sp,
    fontWeight = weight,
    fontFamily = family,
    letterSpacing = spacing.sp,
    textAlign = align,
    lineHeightStyle = Centered,
    platformStyle = PlatformTextStyle(includeFontPadding = false),
)

/**
 * A welcome screen whose hero changes hands: the black card tips back and drops behind a green
 * card wallet, two notes rise out of the wallet's pocket and slide back in, and the card rises
 * out of it and lands where it started. A tap on the hero plays it.
 *
 * @param teaser The large lines under the button, cut off by it and by the screen's edge.
 * @param autoPlay Plays the hero's sequence by itself every [LOOP] seconds, from the card back to the card.
 */
@Composable
fun WalletUnpackWelcome(
    modifier: Modifier = Modifier,
    overline: String = "EXPECT MORE FROM YOUR",
    title: String = "WALLET",
    tagline: String = "a prepaid wallet designed for the\ncreditworthy",
    subtitle: String = "A PAYMENT EXPERIENCE THAT'S",
    teaser: String = "FAST,\nCONVENIENT &",
    buttonLabel: String = "Get started",
    cardLabel: String = "CREDIT CARD",
    cardNumber: String = "1234 5678 9123 XXXX",
    cardExpiry: String = "12/25",
    autoPlay: Boolean = false,
    onGetStarted: () -> Unit = {},
    onBack: () -> Unit = {},
) {
    WalletUnpackWelcomeSystemBars()
    val view = LocalView.current
    val still by rememberUpdatedState(rememberReducedMotion())
    val playing by rememberUpdatedState(autoPlay)

    // Seconds since the first frame; when the loop started (-1 while touches have the screen);
    // when a tap started the sequence (-1 when none is playing).
    var now by remember { mutableDoubleStateOf(0.0) }
    var origin by remember { mutableDoubleStateOf(if (autoPlay) 0.0 else -1.0) }
    var playAt by remember { mutableDoubleStateOf(-1.0) }
    var touching by remember { mutableStateOf(false) }
    var lastTouch by remember { mutableDoubleStateOf(0.0) }

    /** Seconds into the sequence at [t]: the loop's while it plays, else a tap's. */
    fun sequenceAt(t: Double): Double {
        if (origin >= 0 && t >= origin) return (t - origin) % LOOP
        if (playAt >= 0 && t - playAt < SEQUENCE_END) return t - playAt
        return 0.0
    }

    val hero by remember { derivedStateOf { heroAt(sequenceAt(now), still) } }
    val walletShown by remember { derivedStateOf { hero.wallet > 0.001f } }

    fun tap() = view.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)

    /** A touch takes the screen from the loop; a sequence it was in the middle of plays on. */
    fun takeOver() {
        lastTouch = now
        if (origin < 0) return
        val q = sequenceAt(now)
        playAt = if (q > OUT_AT && q < SEQUENCE_END) now - q else -1.0
        origin = -1.0
    }

    fun play() {
        if (playAt >= 0 && now - playAt < SEQUENCE_END) return
        tap()
        playAt = now - OUT_AT
    }

    LaunchedEffect(autoPlay) { origin = if (autoPlay) now else -1.0 }

    LaunchedEffect(Unit) {
        val first = withFrameNanos { it }
        while (true) {
            withFrameNanos { frame -> now = (frame - first) / 1e9 }
            // A while after the last touch and the end of the sequence, the loop starts again from rest.
            if (playing && origin < 0 && !touching && now >= max(lastTouch, if (playAt >= 0) playAt + SEQUENCE_END else 0.0) + USER_HOLD) {
                origin = now
                playAt = -1.0
            }
        }
    }

    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(Color.White)
            .pointerInput(Unit) {
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    touching = true
                    takeOver()
                    do {
                        val event = awaitPointerEvent(PointerEventPass.Initial)
                    } while (event.changes.any { it.pressed })
                    touching = false
                    lastTouch = now
                }
            },
    ) {
        val insets = WindowInsets.safeDrawing.asPaddingValues()
        val top = insets.calculateTopPadding().value
        val w = maxWidth.value
        val h = maxHeight.value
        val heroY = h * HERO_AT
        val bottom = h - insets.calculateBottomPadding().value

        // A soft band of shade across the white, like light on brushed metal.
        Box(
            Modifier
                .fillMaxSize()
                .drawBehind {
                    drawRect(
                        Brush.linearGradient(
                            0f to Color.White,
                            0.42f to Color(0xFFDADADA),
                            0.68f to Color.White,
                            1f to Color(0xFFE9E9E9),
                            start = Offset(0f, size.height * 0.15f),
                            end = Offset(size.width, size.height * 0.85f),
                        ),
                    )
                },
        )
        Box(
            Modifier
                .fillMaxWidth()
                .height((top + 30).dp)
                .background(Brush.verticalGradient(listOf(Color(0xFFA9A9A9), Color(0x00A9A9A9)))),
        )

        Box(
            Modifier
                .offset((MARGIN - 12).dp, (top + BACK_TOP - 22).dp)
                .size(44.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { onBack() }
                .semantics { contentDescription = "Back" }
                .padding(start = 12.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Canvas(Modifier.size(20.dp, 12.dp)) {
                val k = density
                val arrow = Path().apply {
                    moveTo(0.75f * k, 6f * k)
                    lineTo(20f * k, 6f * k)
                    moveTo(5.5f * k, 1f * k)
                    lineTo(0.75f * k, 6f * k)
                    lineTo(5.5f * k, 11f * k)
                }
                drawPath(arrow, Ink, style = Stroke(1.5f * k, cap = StrokeCap.Round, join = StrokeJoin.Round))
            }
        }

        BasicText(
            overline,
            Modifier
                .offset(MARGIN.dp, (top + OVERLINE_TOP).dp)
                .width((w - MARGIN * 2).dp)
                .height(12.dp),
            style = walletText(9f, 12f, FontWeight.SemiBold, Ink, spacing = 2f),
            maxLines = 1,
        )
        BasicText(
            title,
            Modifier
                .offset(0.dp, (top + TITLE_TOP).dp)
                .width(w.dp)
                .height(84.dp)
                .semantics { heading() },
            style = walletText(76f, 84f, FontWeight.ExtraLight, Ink, spacing = 1f),
            maxLines = 1,
        )

        // The wallet's back, the notes, the card, the wallet's pocket: in that order, so that
        // what slides in or out of the pocket passes behind its front.
        Box(
            Modifier
                .offset((w / 2 - NOTES_W / 2).dp, (heroY - NOTES_H / 2).dp)
                .size(NOTES_W.dp, NOTES_H.dp),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .size((WALLET_W + 40).dp, (WALLET_H + 40).dp)
                    .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) { play() }
                    .clearAndSetSemantics { contentDescription = "Card and wallet, plays the animation" },
            )
            Canvas(
                Modifier
                    .size(WALLET_W.dp, WALLET_H.dp)
                    .graphicsLayer {
                        alpha = hero.wallet
                        scaleX = hero.walletScale
                        scaleY = hero.walletScale
                    },
            ) { scale(density, density, pivot = Offset.Zero) { drawWalletBack() } }
            if (walletShown) {
                Canvas(
                    Modifier
                        .size(NOTES_W.dp, NOTES_H.dp)
                        .graphicsLayer {
                            alpha = hero.wallet
                            scaleX = hero.walletScale
                            scaleY = hero.walletScale
                        },
                ) { scale(density, density, pivot = Offset.Zero) { drawNotes(hero) } }
            }
            Box(
                Modifier
                    .size(CARD_W.dp, CARD_H.dp)
                    .graphicsLayer {
                        val s = hero
                        alpha = s.card
                        translationY = s.cardY.dp.toPx()
                        rotationZ = s.turn
                        scaleX = s.cardScale
                        scaleY = s.cardScale
                    }
                    .graphicsLayer {
                        rotationX = hero.tip
                        cameraDistance = PERSPECTIVE * density / 72f
                    },
            ) {
                Card(cardLabel, cardNumber, cardExpiry) { hero.face }
            }
            Canvas(
                Modifier
                    .size(WALLET_W.dp, WALLET_H.dp)
                    .graphicsLayer {
                        alpha = hero.wallet
                        scaleX = hero.walletScale
                        scaleY = hero.walletScale
                    },
            ) { scale(density, density, pivot = Offset.Zero) { drawWalletPocket() } }
        }

        BasicText(
            tagline,
            Modifier
                .offset(MARGIN.dp, (bottom - TAGLINE_UP).dp)
                .width((w - MARGIN * 2).dp),
            style = walletText(11f, 20f, FontWeight.Normal, TaglineInk),
        )
        Canvas(
            Modifier
                .offset(RULE_X.dp, (bottom - RULE_UP).dp)
                .size((w - RULE_X * 2).dp, 1.dp),
        ) {
            drawLine(
                Rule,
                Offset(0f, size.height / 2),
                Offset(size.width, size.height / 2),
                strokeWidth = 1.dp.toPx(),
                pathEffect = PathEffect.dashPathEffect(floatArrayOf(1.5.dp.toPx(), 2.5.dp.toPx())),
            )
        }

        BasicText(
            teaser,
            Modifier
                .offset(TEASER_X.dp, (bottom - TEASER_UP).dp)
                .clearAndSetSemantics {},
            style = walletText(36f, 38f, FontWeight.ExtraLight, TeaserInk, align = TextAlign.Start),
            maxLines = 2,
            softWrap = false,
        )
        Box(
            Modifier
                .offset(0.dp, (h - FADE).dp)
                .fillMaxWidth()
                .height(FADE.dp)
                .background(Brush.verticalGradient(listOf(Color(189, 189, 189, 0), Color(189, 189, 189).copy(alpha = 0.9f)))),
        )
        BasicText(
            subtitle,
            Modifier
                .offset(MARGIN.dp, (bottom - SUBTITLE_UP).dp)
                .width((w - MARGIN * 2).dp)
                .height(12.dp),
            style = walletText(9f, 12f, FontWeight.SemiBold, Ink, spacing = 1.5f),
            maxLines = 1,
        )

        val press = remember { MutableInteractionSource() }
        val pressed by press.collectIsPressedAsState()
        Box(
            Modifier
                .offset(MARGIN.dp, (bottom - BUTTON_UP).dp)
                .size((w - MARGIN * 2).dp, BUTTON_HEIGHT.dp)
                .graphicsLayer {
                    scaleX = if (pressed) PRESSED else 1f
                    scaleY = scaleX
                }
                .clickable(press, indication = null, role = Role.Button) {
                    tap()
                    onGetStarted()
                }
                .background(Color.Black, RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.Center,
        ) {
            BasicText(buttonLabel, style = walletText(11f, 14f, FontWeight.Medium, Color.White), maxLines = 1)
        }
    }
}

/** The black card. [face] is read while drawing: past edge-on the card shows its plain back. */
@Composable
private fun Card(label: String, number: String, expiry: String, face: () -> Float) {
    Box(
        Modifier
            .fillMaxSize()
            .clip(RoundedCornerShape(CARD_RADIUS.dp))
            .background(Brush.linearGradient(listOf(CardTop, CardBottom)))
            .clearAndSetSemantics {},
    ) {
        Box(
            Modifier
                .offset(20.dp, 32.dp)
                .size(28.dp, 22.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(Brush.verticalGradient(listOf(ChipTop, ChipBottom)))
                .drawBehind {
                    val k = density
                    drawRect(ChipLine, Offset(0f, 7 * k), Size(size.width, k))
                    drawRect(ChipLine, Offset(0f, 14 * k), Size(size.width, k))
                    drawRect(ChipLine, Offset(13.5f * k, 7 * k), Size(k, 8 * k))
                },
        )
        // The contactless mark: three arcs opening to the right.
        Canvas(
            Modifier
                .offset((CARD_W - 18 - 14).dp, 36.dp)
                .size(14.dp),
        ) {
            val k = density
            for (r in floatArrayOf(3f, 6f, 9f)) {
                drawArc(
                    CardInk,
                    startAngle = -45f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset((2 - r) * k, (7 - r) * k),
                    size = Size(2 * r * k, 2 * r * k),
                    style = Stroke(1.2f * k, cap = StrokeCap.Round),
                )
            }
        }
        BasicText(
            label,
            Modifier
                .align(Alignment.TopEnd)
                .padding(top = 14.dp, end = 16.dp)
                .height(8.dp),
            style = walletText(6f, 8f, FontWeight.Medium, CardLabelInk, spacing = 1.2f),
            maxLines = 1,
        )
        BasicText(
            number,
            Modifier
                .offset(20.dp, 78.dp)
                .height(16.dp),
            style = walletText(12f, 16f, FontWeight.Normal, CardInk, spacing = 1f, align = TextAlign.Start, family = FontFamily.Monospace),
            maxLines = 1,
        )
        BasicText(
            expiry,
            Modifier
                .offset(20.dp, 98.dp)
                .height(10.dp),
            style = walletText(7f, 10f, FontWeight.Normal, CardMute, align = TextAlign.Start, family = FontFamily.Monospace),
            maxLines = 1,
        )
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer { alpha = 1 - face() }
                .background(CardBottom),
        )
    }
}

/**
 * The screen is light, so the status and navigation bar icons are switched to dark while the
 * block is open (the gallery's activity uses light ones) and put back when it closes.
 */
@Composable
private fun WalletUnpackWelcomeSystemBars() {
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
