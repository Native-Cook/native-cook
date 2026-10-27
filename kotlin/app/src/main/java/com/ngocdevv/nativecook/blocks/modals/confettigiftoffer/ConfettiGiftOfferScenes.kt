package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import android.os.SystemClock
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import java.util.Locale
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * A scene's scroll view with its progressive blur (`ProgressiveBlurViewport` > `ScrollView`,
 * ConfettiGiftOffer.tsx:220). On a normal screen the content is a stack of absolutely placed
 * pieces on a canvas as tall as the window; with large text or a short screen it flows from
 * `inset.top + 64` and scrolls. [absolute] holds the one piece that stays absolute in the flow.
 */
@Composable
private fun ConfettiGiftOfferScrollFrame(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    scroll: ScrollState,
    active: Boolean,
    fade: Color,
    absolute: @Composable BoxScope.() -> Unit = {},
    content: @Composable () -> Unit,
) {
    val touch = active && controller.touchEnabled
    ConfettiGiftOfferScrollWatcher(
        state = scroll,
        enabled = touch,
        onBeginDrag = controller::scrollBeginDrag,
        onEndDrag = controller::scrollEndDrag,
        onMomentumBegin = controller::momentumBegin,
        onMomentumEnd = controller::momentumEnd,
    )
    ConfettiGiftOfferBlurViewport(scroll, enabled = active, fade = fade, modifier = Modifier.fillMaxSize()) {
        Box(Modifier.fillMaxSize().confettiGiftOfferScroll(scroll, touch)) {
            if (!metrics.largeText) {
                Box(Modifier.fillMaxWidth().heightIn(min = metrics.height.dp)) { content() }
            } else {
                Box(Modifier.fillMaxWidth()) {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(
                                start = 24.dp,
                                end = 24.dp,
                                top = (metrics.insetTop + 64f).dp,
                                bottom = (metrics.insetBottom + 24f).dp,
                            ),
                    ) { content() }
                    absolute()
                }
            }
        }
    }
}

/** A vertical scroll that, when off, leaves no pointer node behind, so touches reach whatever lies beneath. */
private fun Modifier.confettiGiftOfferScroll(state: ScrollState, enabled: Boolean): Modifier =
    if (enabled) this.verticalScroll(state) else this

/** `place` (ConfettiGiftOffer.tsx:213): absolute at [top] reference points in a normal layout, in the flow otherwise. */
private fun Modifier.place(metrics: ConfettiGiftOfferMetrics, top: Float, height: Float? = null): Modifier {
    if (metrics.largeText) return this.fillMaxWidth()
    val placed = this.fillMaxWidth().offset(y = (top * metrics.u).dp)
    return if (height == null) placed else placed.height((height * metrics.u).dp)
}

@Composable
private fun ConfettiGiftOfferHeadline(text: AnnotatedString, family: FontFamily, modifier: Modifier = Modifier) {
    val density = LocalDensity.current
    BasicText(
        text = text,
        style = density.confettiGiftOfferText(Color(0xFF1C1C1E), 50f, 49f, null, -0.6f, family, cap = 1.35f),
        modifier = modifier.fillMaxWidth().semantics { heading() },
    )
}

private val Accent = Color(0xFF45BA51)

/** "We have a gift! Just for you!" over the checked box and the Open now button (ConfettiGiftOffer.tsx:220–224). */
@Composable
internal fun ConfettiGiftOfferGiftScene(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    assets: ConfettiGiftOfferAssets,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
    active: Boolean,
    scroll: ScrollState,
) {
    ConfettiGiftOfferScrollFrame(controller, metrics, scroll, active, fade = Color(0xFFE7EDFE)) {
        ConfettiGiftOfferHeadline(
            text = AnnotatedString("We have a gift!\nJust for you!"),
            family = assets.headline,
            modifier = Modifier.place(metrics, 133f),
        )
        Box(Modifier.place(metrics, 338f, 310f), contentAlignment = Alignment.Center) {
            Box(
                Modifier.graphicsLayer {
                    val scale = if (metrics.largeText) .9f else metrics.w
                    scaleX = scale
                    scaleY = scale
                },
            ) { ConfettiGiftOfferGift(assets.tail, controller::openProgress) }
        }
        Box(Modifier.place(metrics, 765f), contentAlignment = Alignment.TopCenter) {
            ConfettiGiftOfferButton(
                label = "Open now",
                onPress = controller::open,
                ownership = ownership,
                reduced = reduced,
                enabled = active && controller.touchEnabled,
                modifier = Modifier.width(163.dp),
            )
        }
    }
}

/** `59:59` counting down from the settle, one tick a second; the loop's clock drives it, a touch hands it to the wall clock. */
@Composable
private fun rememberConfettiGiftOfferCountdown(controller: ConfettiGiftOfferController): String {
    var label by remember { mutableStateOf(countdownLabel(countdownTick(controller.initialTime))) }
    val manual = controller.manual
    LaunchedEffect(manual) {
        if (manual) {
            val start = SystemClock.elapsedRealtime()
            while (true) {
                val seconds = max(0L, 3599L - (SystemClock.elapsedRealtime() - start) / 1000L).toInt()
                label = String.format(Locale.ROOT, "%02d:%02d", seconds / 60, seconds % 60)
                delay(1000)
            }
        } else {
            snapshotFlow { countdownTick(controller.time) }.collect { label = countdownLabel(it) }
        }
    }
    return label
}

/** -1 before the ticket has settled (1.1 s), then whole seconds since (ConfettiGiftOffer.tsx:49). */
private fun countdownTick(time: Double): Int = if (time < 1.1) -1 else floor(time - 1.1).toInt()

private fun countdownLabel(tick: Int): String =
    if (tick < 0) "00:00" else String.format(Locale.ROOT, "59:%02d", 59 - (tick % 60))

/** The "%" watermark: each mark sits at a share of the ticket's width and height and turns about its own centre. */
@Composable
private fun ConfettiGiftOfferWatermark(modifier: Modifier) {
    val density = LocalDensity.current
    val style = density.confettiGiftOfferText(Color(0xFF2D2D2D), 30f, weight = 800)
    Layout(
        content = {
            ConfettiGiftOfferPercents.forEach { (_, _, rotation) ->
                BasicText("%", style = style, modifier = Modifier.graphicsLayer { rotationZ = rotation })
            }
        },
        modifier = modifier,
    ) { measurables, constraints ->
        val loose = Constraints()
        val placeables = measurables.map { it.measure(loose) }
        layout(constraints.maxWidth, constraints.maxHeight) {
            placeables.forEachIndexed { index, placeable ->
                val (left, top, _) = ConfettiGiftOfferPercents[index]
                placeable.place((constraints.maxWidth * left / 100f).roundToInt(), (constraints.maxHeight * top / 100f).roundToInt())
            }
        }
    }
}

/**
 * The ticket's tear line (`styles.perforation`, ConfettiGiftOffer.tsx:259): a dashed 1.5 dp rule in
 * #4A4A4A (dashes of three times its width) with a white 22 dp notch centred on each end.
 */
@Composable
private fun ConfettiGiftOfferPerforation() {
    Box(
        Modifier
            .fillMaxWidth()
            .height(1.5.dp)
            .drawBehind {
                val stroke = 1.5.dp.toPx()
                drawLine(
                    color = Color(0xFF4A4A4A),
                    start = Offset(0f, stroke / 2f),
                    end = Offset(size.width, stroke / 2f),
                    strokeWidth = stroke,
                    pathEffect = PathEffect.dashPathEffect(floatArrayOf(stroke * 3f, stroke * 3f)),
                )
                // `top: -11` is measured from inside the 1.5 dp border, so the notches centre on its bottom edge.
                val notch = 11.dp.toPx()
                drawCircle(Color.White, notch, Offset(0f, stroke))
                drawCircle(Color.White, notch, Offset(size.width, stroke))
            },
    )
}

/** The dark ticket with its watermark, countdown, prices, tear line and Claim button (ConfettiGiftOffer.tsx:229–234). */
@Composable
private fun ConfettiGiftOfferTicket(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
    enabled: Boolean,
    modifier: Modifier,
) {
    val density = LocalDensity.current
    val label = rememberConfettiGiftOfferCountdown(controller)
    val large = metrics.largeText
    Box(modifier.clip(RoundedCornerShape(44.dp)).background(Color(0xFF222222))) {
        ConfettiGiftOfferWatermark(Modifier.matchParentSize())
        Column(if (large) Modifier.fillMaxWidth() else Modifier.fillMaxSize()) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .then(if (large) Modifier.heightIn(min = 300.dp) else Modifier.height((313f * metrics.u).dp))
                    .padding(top = 103.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                ConfettiGiftOfferCountdown(label)
                BasicText(
                    text = "\$4.99",
                    style = density.confettiGiftOfferText(
                        Color(0xFF8C8C8C), 17f, 22f, 600, decoration = TextDecoration.LineThrough,
                    ),
                    modifier = Modifier.padding(top = 27.dp),
                )
                BasicText(
                    text = "\$1.49",
                    style = density.confettiGiftOfferText(Color.White, 60f, 66f, 800, -1.5f, tabular = true),
                    modifier = Modifier.padding(top = 2.dp),
                )
                BasicText(
                    text = "per month",
                    style = density.confettiGiftOfferText(Color(0xFFC2C2C2), 18f, 22f, 500),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            ConfettiGiftOfferPerforation()
            Box(
                (if (large) Modifier else Modifier.weight(1f))
                    .fillMaxWidth()
                    .padding(start = 37.dp, end = 37.dp, top = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                ConfettiGiftOfferButton(
                    label = "Claim your offer",
                    onPress = controller::claim,
                    ownership = ownership,
                    reduced = reduced,
                    enabled = enabled,
                    style = ConfettiGiftOfferButtonStyle.Mint,
                    minHeight = 65.dp,
                    radius = 33.dp,
                )
            }
        }
    }
}

/** "Limited time -70% offer" over the ticket, the raccoon peeking over it (ConfettiGiftOffer.tsx:226–238). */
@Composable
internal fun ConfettiGiftOfferOfferScene(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    assets: ConfettiGiftOfferAssets,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
    active: Boolean,
    scroll: ScrollState,
) {
    val density = LocalDensity.current
    val large = metrics.largeText
    val headline = remember {
        buildAnnotatedString {
            append("Limited time\n")
            withStyle(SpanStyle(color = Accent)) { append("-70%") }
            append(" offer")
        }
    }
    val mascot: @Composable (Modifier) -> Unit = { modifier ->
        Box(modifier.fillMaxWidth(), contentAlignment = Alignment.TopCenter) {
            ConfettiGiftOfferMascot(assets.hearts, 128.dp)
        }
    }
    ConfettiGiftOfferScrollFrame(
        controller, metrics, scroll, active, fade = Color.White,
        absolute = {
            // With big text the raccoon sits at a fixed offset from the top of the content (ConfettiGiftOffer.tsx:235).
            if (large) mascot(Modifier.offset(y = (metrics.insetTop + 64f + 98f - 6f).dp))
        },
    ) {
        ConfettiGiftOfferHeadline(headline, assets.headline, Modifier.place(metrics, 86f))
        val ticketModifier = if (large) {
            Modifier.fillMaxWidth().padding(top = 92.dp)
        } else {
            Modifier
                .fillMaxWidth()
                .offset(y = (262f * metrics.u).dp)
                .padding(start = (16f * metrics.w).dp, end = (17f * metrics.w).dp)
                .height((463f * metrics.u).dp)
        }
        ConfettiGiftOfferTicket(controller, metrics, reduced, ownership, active && controller.touchEnabled, ticketModifier)
        if (!large) mascot(Modifier.offset(y = (210f * metrics.u).dp))
        BasicText(
            text = "First year \$17.99, then \$59.99/year.\nCancel anytime.",
            style = density.confettiGiftOfferText(Color(0xFF8E8E93), 14f, 19f),
            modifier = if (large) {
                Modifier.fillMaxWidth().padding(top = 16.dp, bottom = 8.dp)
            } else {
                Modifier.fillMaxWidth().offset(y = (734f * metrics.u + 16f).dp).padding(horizontal = 24.dp)
            },
        )
    }
}

/**
 * Three marks drift and twinkle around the account mascot: a teal dot, a blue star and a teal
 * star (`Sparkles`, ConfettiGiftOffer.tsx:91). They follow the loop clock, so they hold still
 * once a touch owns the story, and under Reduce Motion they just stay lit.
 */
@Composable
private fun ConfettiGiftOfferSparkles(controller: ConfettiGiftOfferController, reduced: Boolean, modifier: Modifier) {
    Box(modifier) {
        Box(
            Modifier
                .offset(65.dp, (-33).dp)
                .size(12.dp)
                .graphicsLayer {
                    val t = controller.time.toFloat()
                    alpha = if (reduced) 1f else .55f + .45f * sin(t * 3.1f)
                    translationY = if (reduced) 0f else sin(t * 1.7f) * 4f * density
                }
                .background(Color(0xFF8ED9CF), CircleShape),
        )
        Box(
            Modifier
                .offset(42.dp, 6.dp)
                .graphicsLayer {
                    val t = controller.time.toFloat()
                    alpha = if (reduced) 1f else .5f + .5f * sin(t * 2.6f + 1.2f)
                    val scale = if (reduced) 1f else .85f + .2f * sin(t * 2.6f + 1.2f)
                    scaleX = scale
                    scaleY = scale
                },
        ) { ConfettiGiftOfferStar(18.dp, Color(0xFF3D6FE0)) }
        Box(
            Modifier
                .align(Alignment.TopEnd)
                .offset((-62).dp, 12.dp)
                .graphicsLayer {
                    val t = controller.time.toFloat()
                    alpha = if (reduced) 1f else .6f + .4f * sin(t * 2.2f + 2.4f)
                    val scale = if (reduced) 1f else .9f + .15f * sin(t * 2.2f + 2.4f)
                    scaleX = scale
                    scaleY = scale
                },
        ) { ConfettiGiftOfferStar(26.dp, Color(0xFF8ED9CF)) }
    }
}

/** "Now let's create account", the heart-handed raccoon and the two sign-in buttons (ConfettiGiftOffer.tsx:239–250). */
@Composable
internal fun ConfettiGiftOfferSignupScene(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    assets: ConfettiGiftOfferAssets,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
    active: Boolean,
    scroll: ScrollState,
) {
    val density = LocalDensity.current
    val large = metrics.largeText
    val enabled = active && controller.touchEnabled
    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(0f to Color(0xFFD9E3FB), .5f to Color(0xFFE2E5F7), 1f to Color(0xFFEAE9F0))),
        )
        ConfettiGiftOfferScrollFrame(controller, metrics, scroll, active, fade = Color(0xFFE2E5F7)) {
            ConfettiGiftOfferHeadline(
                AnnotatedString("Now let’s\ncreate account"),
                assets.headline,
                Modifier.place(metrics, 122f),
            )
            BasicText(
                text = "Save your progress & reach your goals",
                style = density.confettiGiftOfferText(Color(0xFF8E8E9A), 17f, 24f),
                modifier = if (large) {
                    Modifier.fillMaxWidth().padding(top = 10.dp)
                } else {
                    Modifier.fillMaxWidth().offset(y = (218f * metrics.u + 10f).dp)
                },
            )
            Box(Modifier.place(metrics, 352f, 300f), contentAlignment = Alignment.Center) {
                ConfettiGiftOfferSparkles(controller, reduced, Modifier.matchParentSize())
                ConfettiGiftOfferMascot(assets.love, 312.dp, Modifier.requiredSize(312.dp))
            }
            Column(
                modifier = if (large) {
                    Modifier.fillMaxWidth().padding(top = 24.dp)
                } else {
                    Modifier
                        .fillMaxWidth()
                        .offset(y = (695f * metrics.u).dp)
                        .padding(start = (33f * metrics.w).dp, end = (33f * metrics.w).dp)
                },
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                ConfettiGiftOfferButton(
                    label = "Continue with Apple",
                    onPress = { controller.finish(ConfettiGiftOfferProvider.Apple) },
                    ownership = ownership,
                    reduced = reduced,
                    enabled = enabled,
                    icon = { ConfettiGiftOfferAppleLogo() },
                )
                ConfettiGiftOfferButton(
                    label = "Continue with Google",
                    onPress = { controller.finish(ConfettiGiftOfferProvider.Google) },
                    ownership = ownership,
                    reduced = reduced,
                    enabled = enabled,
                    style = ConfettiGiftOfferButtonStyle.Secondary,
                    icon = { ConfettiGiftOfferGoogleLogo() },
                )
            }
        }
    }
}

/** The end of the story: "You're in." (or "All good.") with a Replay button (ConfettiGiftOffer.tsx:251). */
@Composable
internal fun ConfettiGiftOfferFinishScene(
    controller: ConfettiGiftOfferController,
    metrics: ConfettiGiftOfferMetrics,
    assets: ConfettiGiftOfferAssets,
    reduced: Boolean,
    ownership: ConfettiGiftOfferPressOwnership,
    complete: Boolean,
    scroll: ScrollState,
) {
    val density = LocalDensity.current
    Box(Modifier.fillMaxSize().background(Color(0xFFE7EDFE))) {
        ConfettiGiftOfferBlurViewport(scroll, enabled = true, fade = Color(0xFFE7EDFE), modifier = Modifier.fillMaxSize()) {
            Box(Modifier.fillMaxSize().confettiGiftOfferScroll(scroll, controller.touchEnabled)) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(min = metrics.height.dp)
                        .padding(
                            start = 32.dp,
                            end = 32.dp,
                            top = (metrics.insetTop + 90f).dp,
                            bottom = (metrics.insetBottom + 24f).dp,
                        ),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
                ) {
                    ConfettiGiftOfferMascot(assets.love, 200.dp)
                    ConfettiGiftOfferHeadline(
                        AnnotatedString(if (complete) "You’re in." else "All good."),
                        assets.headline,
                    )
                    BasicText(
                        text = if (complete) {
                            "This is a demo: no account was created and no payment was made."
                        } else {
                            "The offer is closed. You can come back whenever you like."
                        },
                        style = density.confettiGiftOfferText(Color(0xFF787188), 15f, 23f),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
                    )
                    ConfettiGiftOfferButton(
                        label = "Replay the gift",
                        onPress = controller::reset,
                        ownership = ownership,
                        reduced = reduced,
                        enabled = controller.touchEnabled,
                    )
                }
            }
        }
    }
}
