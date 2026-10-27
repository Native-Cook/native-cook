package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.provider.Settings
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.min

/** Seconds between the walkthrough's CTA presses, timed like the reference video. */
private val Walkthrough = listOf(2.5, 3.6, 2.4, 3.0, 2.9)

private tailrec fun Context.activity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activity()
    else -> null
}

/**
 * Trading Bots Onboarding: five onboarding steps and a Pro offer. Intent extras for QA:
 * `tbStep` (0…5) opens a scene, `tbHoldFocus` keeps Pro on its first card, `tbAutoplay` plays
 * the walkthrough on launch.
 */
@Composable
internal fun TradingBotsOnboarding() {
    val context = LocalContext.current
    val intent = remember { context.activity()?.intent }
    val holdFocus = remember { intent?.getBooleanExtra("tbHoldFocus", false) ?: false }
    val systemReduced = remember { Settings.Global.getFloat(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f }
    var forceReduced by remember { mutableStateOf(false) }
    val reduced = systemReduced || forceReduced

    var flow by remember { mutableStateOf(TradingBotsFlow.start(intent?.getIntExtra("tbStep", 0) ?: 0)) }
    var revision by remember { mutableIntStateOf(0) }
    var showQA by remember { mutableStateOf(false) }
    var detail by remember { mutableStateOf<TradingBotsDetail?>(null) }
    var replayToken by remember { mutableIntStateOf(if (intent?.getBooleanExtra("tbAutoplay", false) == true) 1 else 0) }
    var ctaPressed by remember { mutableStateOf(false) }
    var focusResponse by remember { mutableFloatStateOf(0.7f) }
    var proInteracted by remember { mutableStateOf(false) }
    val dim = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    // The reference greys the CTA while a scene changes, then lights it again.
    fun dimCTA() {
        if (reduced) return
        scope.launch {
            dim.animateTo(1f, tween(120, easing = EaseOut))
            delay(280)
            dim.animateTo(0f, tween(500, easing = EaseInOut))
        }
    }

    fun next() {
        val moved = flow.advance(uptimeSeconds()) ?: return
        proInteracted = false
        flow = moved
        dimCTA()
    }

    fun navigate(step: Int) {
        replayToken = 0
        proInteracted = false
        flow = flow.show(step)
        revision++
        dimCTA()
    }

    fun restart(replay: Boolean) {
        flow = TradingBotsFlow()
        revision++
        replayToken = if (replay) replayToken + 1 else 0
    }

    // Pro: purple leads on entry; after 2 s green moves forward unless a card was tapped.
    LaunchedEffect(flow.step, revision, reduced) {
        if (flow.step != TradingBotsPro || reduced || holdFocus) return@LaunchedEffect
        delay(2000)
        if (!proInteracted && flow.proFocus == 0) {
            focusResponse = 0.75f
            flow = flow.focusPro(1)
        }
    }

    // The QA walkthrough: five presses of the CTA, each shown pressed for 0.45 s.
    LaunchedEffect(replayToken) {
        if (replayToken == 0) return@LaunchedEffect
        try {
            for (duration in Walkthrough) {
                delay(((duration - 0.45) * 1000).toLong())
                ctaPressed = true
                delay(450)
                ctaPressed = false
                next()
            }
        } finally {
            ctaPressed = false
        }
    }

    val pro = flow.step == TradingBotsPro
    val proBackdrop by animateFloatAsState(if (pro) 1f else 0f, tween(400, easing = EaseInOut), label = "backdrop")

    BoxWithConstraints(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
        val scale = min(maxWidth.value / CanvasWidth, maxHeight.value / CanvasHeight)
        Box(
            Modifier
                .size(CanvasWidth.dp, CanvasHeight.dp)
                .graphicsLayer { scaleX = scale; scaleY = scale }
                .drawBehind {
                    drawRect(Brush.verticalGradient(0f to Color.Black, 0.62f to Color.Black, 0.93f to hex(0x131313), 1f to hex(0x111111)))
                    drawRect(Brush.verticalGradient(0f to Color.Black, 0.42f to hex(0x131313), 1f to hex(0x111111)), alpha = proBackdrop)
                },
        ) {
            Prewarm()

            AnimatedContent(
                targetState = flow.step to revision,
                transitionSpec = { EnterTransition.None togetherWith fadeOut(if (reduced) snap() else tween(180, easing = EaseOut)) },
                label = "scene",
            ) { (step, _) ->
                when (step) {
                    0 -> WalletScene(reduced)
                    1 -> SelectorScene(flow.selectedBot, { flow = flow.chooseBot(it) }, reduced)
                    2 -> SetupScene(reduced, configure = { navigate(3) }, overview = { detail = TradingBotsDetail.Overview })
                    3 -> AllocationScene(
                        AllocationState(
                            flow.allocation, { flow = flow.allocate(it) },
                            flow.risk, { flow = flow.copy(risk = it) },
                            flow.autoTrading, { flow = flow.copy(autoTrading = it) },
                            flow.maxDailyLoss, { flow = flow.copy(maxDailyLoss = it) },
                        ),
                        reduced,
                    )
                    4 -> ResultsScene(reduced, configure = { navigate(3) }, overview = { detail = TradingBotsDetail.Overview })
                    else -> ProScene(flow.proFocus, focusResponse, { bot ->
                        proInteracted = true
                        focusResponse = 0.7f
                        flow = flow.focusPro(bot)
                    }, reduced)
                }
            }

            Header(
                step = flow.step,
                reduced = reduced,
                goBack = {
                    replayToken = 0
                    flow = flow.back()
                    revision++
                    dimCTA()
                },
                openQA = { showQA = true },
                skip = { if (pro) detail = TradingBotsDetail.Finish else navigate(TradingBotsPro) },
            )

            val title = when {
                pro -> "Start 3 Day Free Trial"
                flow.step == 0 -> "Let’s Get Started"
                flow.step == 1 -> "Choose Your Bots"
                else -> "Continue"
            }
            PrimaryButton(
                title = title,
                pro = pro,
                dim = dim.value,
                pressed = ctaPressed,
                onClick = {
                    replayToken = 0
                    if (pro) detail = TradingBotsDetail.Trial else next()
                },
                modifier = Modifier.at(200.8f, if (flow.step == 1) 803.8f else if (pro) 753.2f else 757f),
                icon = if (pro) { { Glyph(Icons.AutoMirrored.Filled.KeyboardArrowRight, 20f, Color.Black) } } else null,
            )
            if (pro) {
                Box(Modifier.at(200f, 820f)) {
                    Reveal(reduced) {
                        HStack(10f) {
                            PressScale({ detail = TradingBotsDetail.Terms }, label = "Terms of use") { TBText("Terms of use", 15.6f, color = hex(0x585858), tracking = -0.2f) }
                            TBText("|", 15.6f, color = hex(0x404040))
                            PressScale({ detail = TradingBotsDetail.Privacy }, label = "Privacy policy") { TBText("Privacy policy", 15.6f, color = hex(0x585858), tracking = -0.2f) }
                        }
                    }
                }
            } else if (flow.step != 1) {
                PressScale({ navigate(TradingBotsPro) }, label = "skip and start", modifier = Modifier.at(201f, 819f)) {
                    TBText("skip and start", 16f, color = hex(0xdedede), tracking = -0.2f)
                }
            }
        }
    }

    if (showQA) {
        QAHarness(
            step = flow.step,
            reduced = forceReduced,
            setReduced = { forceReduced = it },
            show = { showQA = false; navigate(it) },
            replay = { showQA = false; restart(true) },
            reset = { showQA = false; restart(false) },
            close = { showQA = false },
        )
    }
    detail?.let { current ->
        DetailSheet(current, flow.risk, flow.allocation, close = { detail = null }, replay = { detail = null; restart(false) })
    }
}

/** Back (44 pt disc at 20…64), five-step progress from x 84, Skip at the right edge. */
@Composable
private fun Header(step: Int, reduced: Boolean, goBack: () -> Unit, openQA: () -> Unit, skip: () -> Unit) {
    PressScale(goBack, label = if (step == 0) "Restart wallet introduction" else "Back", onLongPress = openQA, modifier = Modifier.at(42f, 93.3f)) {
        Box(Modifier.size(44.dp).background(hex(0x0d0d0d), CircleShape).border(1.dp, white(0.035f), CircleShape), contentAlignment = Alignment.Center) {
            BackArrow(17f, 13.5f)
        }
    }
    val progressAlpha by animateFloatAsState(if (step == TradingBotsPro) 0f else 1f, if (reduced) snap() else tween(180, easing = EaseOut), label = "progress")
    if (progressAlpha > 0f) {
        PressScale({}, onLongPress = openQA, modifier = Modifier.leading(84f, 93.3f).graphicsLayer { alpha = progressAlpha }.semantics { contentDescription = "Onboarding step ${minOf(step, 4) + 1} of 5" }) {
            HStack(4f) { for (index in 0..4) ProgressPill(index == step, reduced) }
        }
    }
    PressScale(skip, label = "Skip", modifier = Modifier.at(356f, 95.3f)) {
        Box(Modifier.size(44.dp), contentAlignment = Alignment.Center) { TBText("Skip", 15.2f, tracking = -0.2f) }
    }
}

@Composable
private fun ProgressPill(active: Boolean, reduced: Boolean) {
    val width by animateDpAsState(if (active) 43.dp else 8.dp, if (reduced) snap() else swiftSpring(0.42f, 0.9f), label = "pill")
    Box(Modifier.size(width, 5.dp).background(hex(if (active) 0xd8d8d8 else 0x363636), RoundedCornerShape(50)))
}

/**
 * Draws every shader and image once, invisibly, at launch, so the first visit to a scene does not
 * stall on a shader compile or an image decode mid-transition.
 */
@Composable
private fun Prewarm() {
    HStack(0f, Modifier.leading(4f, 870f).graphicsLayer { alpha = 0.011f }) {
        Character(BotKind.Orb, 4f)
        Character(BotKind.Green, 4f)
        Character(BotKind.Purple, 4f)
        Character(BotKind.Red, 4f)
        Character(BotKind.Avatar, 4f)
    }
}
