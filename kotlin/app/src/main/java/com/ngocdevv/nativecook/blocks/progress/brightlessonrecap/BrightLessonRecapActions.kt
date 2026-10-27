package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import android.content.Context
import android.content.Intent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.FADE_END
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.SOURCE_END
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.enter
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.fraction
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.phase
import kotlin.math.min
import kotlin.math.roundToInt

private val PressCurve = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** Text shared by the Share button, as in the React Native `Share.share` call (BrightLessonRecap.tsx:151). */
private const val SHARE_MESSAGE = "Perfect lesson: 45 XP, 100% accuracy, 3:40."

/**
 * The area under the stats (BrightLessonRecap.tsx:148-159): the Share button and the CLAIM XP key
 * in a row, with "+45 XP" rising out of the key after a claim, or, in the flow layout, the success
 * line above a full-width key and a "Replay celebration" link below. It fades in as the last pill
 * lands and out with the rest of the scene; after a claim it stays fully visible.
 */
@Composable
internal fun BrightLessonRecapActions(
    controller: BrightLessonRecapController,
    metrics: BrightLessonRecapMetrics,
    type: BrightLessonRecapTypography,
) {
    val flow = metrics.flow
    val claimed = controller.claimed
    val enabled = controller.actionsEnabled && controller.touchEnabled
    Box(
        Modifier
            .fillMaxWidth()
            .then(
                if (claimed) {
                    Modifier
                } else {
                    Modifier.brightLessonRecapGroupAlpha {
                        val t = controller.time
                        (phase(t, 1.9, 2.35) * (1 - phase(t, SOURCE_END, FADE_END))).toFloat()
                    }
                },
            ),
    ) {
        Column(Modifier.fillMaxWidth().padding(top = if (flow) 8.dp else 0.dp)) {
            if (flow) SuccessLine(claimed, type, Modifier.padding(bottom = 13.dp))
            Box(Modifier.fillMaxWidth()) {
                if (flow) {
                    ClaimKey(controller, metrics, type, enabled, Modifier.fillMaxWidth())
                } else {
                    Row(
                        Modifier.fillMaxWidth().height(IntrinsicSize.Min),
                        horizontalArrangement = Arrangement.spacedBy(9.dp),
                    ) {
                        ShareButton(controller, enabled, Modifier.width(48.dp).heightIn(min = 48.dp).fillMaxHeight())
                        ClaimKey(controller, metrics, type, enabled, Modifier.weight(1f))
                    }
                }
                Reward(controller, type)
            }
            if (flow) ReplayLink(controller, type, enabled)
        }
        if (!flow && claimed) {
            SuccessLine(
                claimed, type,
                Modifier.brightLessonRecapPinned(fillWidth = true) { _, _ -> IntOffset(0, (-30).dp.roundToPx()) },
            )
        }
    }
}

/** "A perfect finish. You earned this." or, after a claim, "45 XP added to this demo session." (BrightLessonRecap.tsx:149). */
@Composable
private fun SuccessLine(claimed: Boolean, type: BrightLessonRecapTypography, modifier: Modifier) {
    BasicText(
        if (claimed) "45 XP added to this demo session." else "A perfect finish. You earned this.",
        style = type.style(BrightLessonRecapFace.System, 12f, 18f, Color(0xFF5D7463), align = TextAlign.Center),
        modifier = modifier.fillMaxWidth().semantics { liveRegion = LiveRegionMode.Polite },
    )
}

/**
 * The physical blue key (BrightLessonRecap.tsx:152-155): its face sits 4 dp proud of the darker base
 * and drops flush while held. Holding it pauses autoplay; a tap claims.
 */
@Composable
private fun ClaimKey(
    controller: BrightLessonRecapController,
    metrics: BrightLessonRecapMetrics,
    type: BrightLessonRecapTypography,
    enabled: Boolean,
    modifier: Modifier,
) {
    val flow = metrics.flow
    val claimed = controller.claimed
    val shape = RoundedCornerShape(if (flow) 17.dp else 12.dp)
    var pressed by remember { mutableStateOf(false) }
    val active = enabled && !claimed
    LaunchedEffect(active) { if (!active) pressed = false }
    Box(
        modifier
            .background(if (claimed) Color(0xFF1B6847) else Color(0xFF1679C4), shape)
            .padding(bottom = 4.dp)
            .semantics {
                role = Role.Button
                contentDescription = if (claimed) "XP claimed" else "Claim 45 XP"
                if (!active) disabled()
            }
            .brightLessonRecapPress(
                enabled = active,
                onPressIn = {
                    pressed = true
                    controller.pause()
                },
                onPressOut = {
                    pressed = false
                    controller.release()
                },
                onPress = {
                    pressed = false
                    controller.claim()
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .heightIn(min = if (flow) 52.dp else 44.dp)
                .graphicsLayer { translationY = if (pressed && !controller.reduced) 4.dp.toPx() else 0f }
                .background(if (claimed) Color(0xFF2E8A64) else Color(0xFF1F9BEA), shape)
                .padding(horizontal = 15.dp, vertical = if (flow) 16.dp else 10.dp),
            contentAlignment = Alignment.Center,
        ) {
            BrightLessonRecapSwapText(
                text = if (claimed) "XP CLAIMED  ✓" else "CLAIM XP",
                style = type.style(BrightLessonRecapFace.Black, 14f, 20f, Color.White, letterSpacing = 0.7f),
                reduced = controller.reduced,
            )
        }
    }
}

/** The outlined Share button; `PressScale` settles it to 0.96 in 150 ms, or dims it under reduced motion. */
@Composable
private fun ShareButton(controller: BrightLessonRecapController, enabled: Boolean, modifier: Modifier) {
    val context = LocalContext.current
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) pressed = false }
    val reduced = controller.reduced
    val scale by animateFloatAsState(if (pressed && !reduced) 0.96f else 1f, tween(150, easing = PressCurve), label = "shareScale")
    val dim by animateFloatAsState(if (pressed && reduced) 0.72f else 1f, tween(150, easing = PressCurve), label = "shareDim")
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                alpha = dim
            }
            .border(1.dp, Color(0xFFC7D2D8), shape)
            .semantics {
                role = Role.Button
                contentDescription = "Share your result"
            }
            .brightLessonRecapPress(
                enabled = enabled,
                onPressIn = { pressed = true },
                onPressOut = { pressed = false },
                onPress = { share(context) },
            ),
        contentAlignment = Alignment.Center,
    ) {
        BrightLessonRecapShareIcon(Color(0xFF268DDD), 22.dp)
    }
}

/** Opens the system share sheet with the result, and ignores a device that cannot (`Share.share(...).catch`). */
private fun share(context: Context) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, SHARE_MESSAGE)
    }
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { context.startActivity(chooser) }
}

/**
 * "+45 XP" leaving the key after a claim: it rises 56 dp and fades in 700 ms (BrightLessonRecap.tsx:112),
 * 30% in from the right edge of the row, 6 dp above it.
 */
@Composable
private fun Reward(controller: BrightLessonRecapController, type: BrightLessonRecapTypography) {
    BasicText(
        "+45 XP",
        style = type.style(BrightLessonRecapFace.Black, 22f, null, Color(0xFFD98A00), letterSpacing = -0.4f),
        modifier = Modifier
            .brightLessonRecapPinned { parentWidth, childWidth ->
                IntOffset((parentWidth * 0.7f).roundToInt() - childWidth, (-6).dp.roundToPx())
            }
            .graphicsLayer {
                val p = controller.reward.value.toDouble()
                alpha = if (p <= 0 || p >= 1) 0f else (min(1.0, p * 6) * (1 - enter(fraction(p, 0.45, 1.0)))).toFloat()
                translationY = (-56 * enter(p)).dp.toPx().toFloat()
                val s = (0.9 + 0.2 * enter(fraction(p, 0.0, 0.3))).toFloat()
                scaleX = s
                scaleY = s
            },
    )
}

/** The flow layout's "Replay celebration" link under the key (BrightLessonRecap.tsx:158). */
@Composable
private fun ReplayLink(controller: BrightLessonRecapController, type: BrightLessonRecapTypography, enabled: Boolean) {
    var pressed by remember { mutableStateOf(false) }
    LaunchedEffect(enabled) { if (!enabled) pressed = false }
    Box(
        Modifier
            .fillMaxWidth()
            .padding(top = 3.dp)
            .heightIn(min = 48.dp)
            .graphicsLayer { alpha = if (pressed) 0.6f else 1f }
            .semantics {
                role = Role.Button
                contentDescription = "Replay celebration"
            }
            .brightLessonRecapPress(
                enabled = enabled,
                onPressIn = { pressed = true },
                onPressOut = { pressed = false },
                onPress = controller::replay,
            ),
        contentAlignment = Alignment.Center,
    ) {
        BasicText(
            "Replay celebration",
            style = type.style(BrightLessonRecapFace.Black, 13f, null, Color(0xFF597D94)),
        )
    }
}
