package com.ngocdevv.nativecook.blocks.progress.brightlessonrecap

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.FADE_END
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.MASCOT_FROM
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.SOURCE_END
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.TITLE_SWAP
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.dock
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.enter
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.fraction
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.mascotCenter
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.mascotX
import com.ngocdevv.nativecook.blocks.progress.brightlessonrecap.BrightLessonRecapMotion.phase
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

private val Gold = Color(0xFFFFB100)

/**
 * The top of the screen (the `Pressable` hero in BrightLessonRecap.tsx:138-143): broad green bands
 * sweep in, the otter enters from the left over six sparkles, and "Brilliant work!" bursts in large,
 * then shrinks into place as the otter drifts right and compacts. A tap replays the story.
 * Every child is positioned against the hero's box, like `position: absolute` in the original.
 */
@Composable
internal fun BrightLessonRecapHero(
    controller: BrightLessonRecapController,
    metrics: BrightLessonRecapMetrics,
    type: BrightLessonRecapTypography,
    mascot: ImageBitmap,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .height(metrics.heroHeight.dp)
            .then(if (metrics.flow) Modifier.clipToBounds() else Modifier)
            .semantics { role = Role.Button; contentDescription = "Replay lesson celebration" }
            .brightLessonRecapPress(
                enabled = controller.mascotEnabled && controller.touchEnabled,
                onPress = controller::replay,
            ),
    ) {
        Bands(controller, metrics)
        val sparkleCenter = if (metrics.flow) 120f else metrics.height * 0.376f - metrics.sectionTop
        repeat(6) { index -> HeroSparkle(index, controller, sparkleCenter) }
        Mascot(controller, metrics, mascot)
        if (!metrics.flow) {
            BigTitle(controller, metrics, type)
            FinalTitle(controller, metrics, type)
            Subtitle(controller, metrics, type)
        }
    }
}

/** Three diagonal green bands, 1.5 windows wide, sweeping in from the left (BrightLessonRecap.tsx:139). */
@Composable
private fun Bands(controller: BrightLessonRecapController, metrics: BrightLessonRecapMetrics) {
    val top = if (metrics.flow) 70f else metrics.height * 0.275f - metrics.sectionTop
    val bandsHeight = if (metrics.flow) 160f else metrics.height * 0.385f
    Column(
        Modifier
            .brightLessonRecapPinned(width = (metrics.width * 1.5f).dp, height = bandsHeight.dp) { _, _ ->
                IntOffset((-metrics.width * 0.25f).dp.roundToPx(), top.dp.roundToPx())
            }
            .graphicsLayer {
                val t = controller.time
                alpha = if (t < TITLE_SWAP) enter(fraction(t, MASCOT_FROM, 0.533)).toFloat() else 0f
                translationX = if (controller.reduced) {
                    0f
                } else {
                    (-(1 - enter(fraction(t, MASCOT_FROM, 0.55))) * metrics.width.dp.toPx()).toFloat()
                }
                rotationZ = -12f
            },
    ) {
        Box(Modifier.fillMaxWidth().weight(0.18f).background(Color(0xFFDCF5C6)))
        Box(Modifier.fillMaxWidth().weight(0.64f).background(Color(0xFFB4E892)))
        Box(Modifier.fillMaxWidth().weight(0.18f).background(Color(0xFF92D577)))
    }
}

/** One of six four-point sparkles on a ring round the otter (`Sparkle`, BrightLessonRecap.tsx:87-94). */
@Composable
private fun HeroSparkle(index: Int, controller: BrightLessonRecapController, centerY: Float) {
    val angle = index * PI / 3
    val glyphSize = if (index % 3 == 1) 14.dp else 22.dp
    BrightLessonRecapSparkleGlyph(
        color = if (index % 2 == 1) Color(0xFF78D64B) else Color(0xFF8FDF5F),
        size = glyphSize,
        modifier = Modifier
            .brightLessonRecapPinned(width = glyphSize, height = glyphSize) { parentWidth, _ ->
                IntOffset((parentWidth * 0.5f).roundToInt() - 9.dp.roundToPx(), (centerY - 9f).dp.roundToPx())
            }
            .graphicsLayer {
                val t = controller.time
                val p = phase(t, TITLE_SWAP, 1.5)
                alpha = if (controller.reduced) 0f else (p * (1 - phase(t, SOURCE_END, FADE_END))).toFloat()
                translationX = (cos(angle) * 116).dp.toPx().toFloat()
                translationY = (sin(angle) * 104).dp.toPx().toFloat()
                val s = (0.82 + 0.22 * sin(t * PI * 4 / BRIGHT_LESSON_RECAP_LOOP * 1.5 + index * 1.7)).toFloat()
                scaleX = s
                scaleY = s
            },
    )
}

/** The otter: enters from the left, drifts right and shrinks to 61% as the layout docks (BrightLessonRecap.tsx:124-129). */
@Composable
private fun Mascot(controller: BrightLessonRecapController, metrics: BrightLessonRecapMetrics, mascot: ImageBitmap) {
    Box(
        Modifier
            .brightLessonRecapPinned(width = metrics.mascotSize.dp, height = (metrics.mascotSize * 31f / 30f).dp) { _, _ ->
                IntOffset(((metrics.contentWidth - metrics.mascotSize) / 2f).dp.roundToPx(), 0)
            }
            .graphicsLayer {
                val t = controller.time
                val dock = dock(t)
                alpha = enter(fraction(t, MASCOT_FROM, 0.533)).toFloat()
                translationX = (mascotX(t) * metrics.width).dp.toPx().toFloat()
                val y = if (metrics.flow) {
                    -10 * dock
                } else {
                    metrics.height * mascotCenter(t) - metrics.sectionTop - metrics.mascotSize * 31.0 / 60
                }
                translationY = y.dp.toPx().toFloat()
                val s = (1 - 0.39 * dock).toFloat()
                scaleX = s
                scaleY = s
            },
    ) {
        Image(
            bitmap = mascot,
            contentDescription = null,
            modifier = Modifier.fillMaxSize(),
            contentScale = ContentScale.Fit,
            filterQuality = FilterQuality.High,
        )
    }
}

/** The big "Brilliant work!" on two lines; it shrinks and lifts away as the layout docks (BrightLessonRecap.tsx:131 and 142). */
@Composable
private fun BigTitle(controller: BrightLessonRecapController, metrics: BrightLessonRecapMetrics, type: BrightLessonRecapTypography) {
    val top = metrics.height * 0.769f - metrics.sectionTop
    BasicText(
        "Brilliant\nwork!",
        style = type.style(
            BrightLessonRecapFace.Black, 49f, 62f, Gold,
            letterSpacing = -1.2f, align = TextAlign.Center, cap = 1.4f,
        ),
        modifier = Modifier
            .brightLessonRecapPinned(fillWidth = true) { _, _ -> IntOffset(0, top.dp.roundToPx()) }
            .graphicsLayer {
                val t = controller.time
                val dock = dock(t)
                alpha = if (t < TITLE_SWAP) enter(fraction(t, 0.35, 0.55)).toFloat() else 0f
                translationY = (-metrics.height * 0.06 * dock).dp.toPx().toFloat()
                val s = (1 - 0.18 * dock).toFloat()
                scaleX = s
                scaleY = s
            },
    )
}

/** The small "Brilliant work!" that rises into place from 14.7% of the window height below (BrightLessonRecap.tsx:131 and 142). */
@Composable
private fun FinalTitle(controller: BrightLessonRecapController, metrics: BrightLessonRecapMetrics, type: BrightLessonRecapTypography) {
    val top = metrics.height * 344f / 586f - metrics.sectionTop - 4f
    BasicText(
        "Brilliant work!",
        style = type.style(
            BrightLessonRecapFace.Black, 28f, 34f, Gold,
            letterSpacing = -0.8f, align = TextAlign.Center, cap = 1.6f,
        ),
        modifier = Modifier
            .brightLessonRecapPinned(fillWidth = true) { _, _ -> IntOffset(0, top.dp.roundToPx()) }
            .graphicsLayer {
                val t = controller.time
                alpha = if (t >= TITLE_SWAP) 1f else 0f
                translationY = if (controller.reduced) {
                    0f
                } else {
                    (metrics.height * 0.147 * (1 - enter(fraction(t, TITLE_SWAP, 1.5)))).dp.toPx().toFloat()
                }
            },
    )
}

/** "Take a bow!" in the system font (BrightLessonRecap.tsx:131 and 142). */
@Composable
private fun Subtitle(controller: BrightLessonRecapController, metrics: BrightLessonRecapMetrics, type: BrightLessonRecapTypography) {
    val top = metrics.height * 380f / 586f - metrics.sectionTop
    BasicText(
        "Take a bow!",
        style = type.style(BrightLessonRecapFace.System, 15f, 23f, Color(0xFF555555), align = TextAlign.Center),
        modifier = Modifier
            .brightLessonRecapPinned(fillWidth = true) { _, _ -> IntOffset(0, top.dp.roundToPx()) }
            .graphicsLayer { alpha = enter(fraction(controller.time, 1.367, 1.5)).toFloat() },
    )
}

/**
 * The flow layout's headline and body, in the page flow instead of pinned (BrightLessonRecap.tsx:144),
 * with the same two fades the pinned texts use.
 */
@Composable
internal fun BrightLessonRecapFlowCopy(controller: BrightLessonRecapController, type: BrightLessonRecapTypography) {
    Column(
        Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        BasicText(
            "Brilliant work!",
            style = type.style(
                BrightLessonRecapFace.Black, 30f, 37f, Color(0xFFF0A500),
                letterSpacing = -0.8f, align = TextAlign.Center, cap = 1.6f,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .semantics { heading() }
                .graphicsLayer { alpha = enter(fraction(controller.time, 0.35, 0.55)).toFloat() },
        )
        BasicText(
            "Take a bow!",
            style = type.style(BrightLessonRecapFace.ExtraBold, 16f, 23f, Color(0xFF4B4B4B), align = TextAlign.Center),
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer { alpha = enter(fraction(controller.time, 1.367, 1.5)).toFloat() },
        )
    }
}
