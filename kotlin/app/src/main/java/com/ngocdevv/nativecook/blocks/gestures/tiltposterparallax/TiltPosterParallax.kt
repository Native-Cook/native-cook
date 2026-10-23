package com.ngocdevv.nativecook.blocks.gestures.tiltposterparallax

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import kotlin.math.roundToInt

/** One autoplay period in milliseconds, matching React Native's LOOP_MS. */
internal const val LOOP = 10_000.0

@Composable
fun TiltPosterParallax(autoPlay: Boolean = false) {
    val density = LocalDensity.current
    val reduceMotion = rememberReducedMotion()
    val typography = rememberTiltPosterParallaxTypography()
    val safeTop = (WindowInsets.safeDrawing.getTop(density) / density.density).dp
    val engine = remember(autoPlay, reduceMotion) {
        TiltPosterParallaxEngine(
            autoPlay = autoPlay && !reduceMotion,
            reduceMotion = reduceMotion,
        )
    }

    TiltPosterParallaxClock(engine = engine)
    TiltPosterParallaxSensor(engine = engine, enabled = !reduceMotion)

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Tokens.COLOR.bg),
    ) {
        val stageWidth = maxWidth
        val stageHeight = maxHeight
        val cardWidthPoints = (stageWidth.value * Tokens.LAYOUT.cardWidthRatio).roundToInt().toFloat()
        val cardWidth = cardWidthPoints.dp
        val cardHeightPoints = (cardWidthPoints * Tokens.LAYOUT.cardAspect).roundToInt().toFloat()
        val cardHeight = cardHeightPoints.dp
        val art = remember(cardWidthPoints, cardHeightPoints, density.density) {
            makeTiltPosterParallaxArt(cardWidthPoints, cardHeightPoints, density.density)
        }
        val cardLeft = (stageWidth.value - cardWidthPoints).dp / 2f
        val cardTop = safeTop + Tokens.LAYOUT.cardTop.dp
        val infoTop = cardTop + cardHeight + Tokens.LAYOUT.titleGap.dp
        val infoWidth = stageWidth.value - Tokens.LAYOUT.gutter.toFloat() * 2f
        val firstPillCentreX = Tokens.LAYOUT.gutter + (infoWidth - Tokens.LAYOUT.pillGap) / 4.0
        val pillsY = Tokens.TYPE.titleLineHeight +
            Tokens.LAYOUT.infoGap +
            Tokens.TYPE.metaLineHeight +
            Tokens.LAYOUT.infoGap +
            Tokens.LAYOUT.pillsMarginTop
        val watchPointX = firstPillCentreX - cardLeft.value
        val watchPointY = cardHeightPoints +
            Tokens.LAYOUT.titleGap +
            pillsY +
            Tokens.LAYOUT.pillHeight / 2.0

        SideEffect {
            engine.setGeometry(
                cardWidth = cardWidthPoints.toDouble(),
                cardHeight = cardHeightPoints.toDouble(),
                watchPointX = watchPointX,
                watchPointY = watchPointY,
            )
        }

        TiltPosterParallaxBackdrop(
            engine = engine,
            art = art,
            stageWidth = stageWidth,
            stageHeight = stageHeight,
        )
        TiltPosterParallaxTopBar(
            stageWidth = stageWidth,
            safeTop = safeTop,
            typography = typography,
        )
        TiltPosterParallaxPoster(
            engine = engine,
            art = art,
            typography = typography,
            cardWidth = cardWidth,
            cardHeight = cardHeight,
            left = cardLeft,
            top = cardTop,
            reduceMotion = reduceMotion,
        )
        TiltPosterParallaxInfo(
            engine = engine,
            stageWidth = stageWidth,
            top = infoTop,
            typography = typography,
        )
        if (autoPlay) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val centre = Offset(
                    x = cardLeft.toPx() + engine.fingerX.dp.toPx(),
                    y = cardTop.toPx() + engine.fingerY.dp.toPx(),
                )
                val radius = Tokens.LAYOUT.fingerRadius.dp.toPx()
                drawCircle(
                    color = Tokens.COLOR.finger.copy(alpha = Tokens.COLOR.finger.alpha * engine.fingerAlpha),
                    radius = radius,
                    center = centre,
                )
                drawCircle(
                    color = Tokens.COLOR.fingerRing.copy(alpha = Tokens.COLOR.fingerRing.alpha * engine.fingerAlpha),
                    radius = radius,
                    center = centre,
                    style = Stroke(width = Tokens.LAYOUT.fingerStroke.dp.toPx()),
                )
            }
        }
    }
}
