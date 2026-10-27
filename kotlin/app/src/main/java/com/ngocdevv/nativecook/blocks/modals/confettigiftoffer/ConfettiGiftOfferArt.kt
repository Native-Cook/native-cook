package com.ngocdevv.nativecook.blocks.modals.confettigiftoffer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

private val Blue = Color(0xFF3A7FE9)
private val BlueDark = Color(0xFF2F6AD0)
private val CheckBase = Color(0xFFA6E7DD)
private val CheckLight = Color(0xFFF3FBFA)

/** Miso, one pose, `contentFit="contain"` in a square (art.tsx:9). */
@Composable
internal fun ConfettiGiftOfferMascot(bitmap: ImageBitmap, size: Dp, modifier: Modifier = Modifier) {
    Image(
        bitmap = bitmap,
        contentDescription = null,
        contentScale = ContentScale.Fit,
        filterQuality = FilterQuality.High,
        modifier = modifier.size(size),
    )
}

/**
 * The reference's gift (art.tsx:19), drawn on a 270 × 310 dp board: an upright checked box with a
 * heavy blue outline, its lid knocked askew by a striped tail, a two-loop bow on top.
 * Two rocks build anticipation, the box squats, then the lid kicks free just before the cut.
 * [open] is the 0 to 1 progress of the lid, read inside the layers so only they redraw.
 */
@Composable
internal fun ConfettiGiftOfferGift(tail: ImageBitmap, open: () -> Float, modifier: Modifier = Modifier) {
    val parser = remember { GiftPaths() }
    Box(
        modifier
            .requiredSize(270.dp, 310.dp)
            .graphicsLayer {
                // shake (art.tsx:21)
                val p = open()
                val wave = if (p >= 1f) 0f else sin(min(p / .72f, 1f) * Math.PI.toFloat() * 4f) * (.6f + p * .6f)
                translationX = wave * 3.4f * density
                rotationZ = wave * 4f
            },
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // squat (art.tsx:22), pivoting on the foot of the box
                    transformOrigin = TransformOrigin(0.5f, 1f)
                    val p = open()
                    val k = if (p < .6f) 0f else if (p < .82f) (p - .6f) / .22f else 1f - (p - .82f) / .18f
                    translationY = 6f * k * density
                    scaleY = 1f - .06f * k
                    scaleX = 1f + .04f * k
                },
        ) {
            Image(
                bitmap = tail,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                filterQuality = FilterQuality.High,
                modifier = Modifier.offset(100.dp, 4.dp).size(190.dp),
            )
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) { drawBase() }
            }
        }
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // pop (art.tsx:23): the lid kicks up and tips, pivoting near its left end
                    transformOrigin = TransformOrigin(0.01f, 0.52f)
                    val p = max(0f, (open() - .78f) / .22f)
                    val e = 1f - (1f - min(1f, p)).pow(3)
                    translationY = -44f * e * density
                    rotationZ = -12f * e
                },
        ) {
            Canvas(Modifier.fillMaxSize()) {
                scale(density, density, pivot = Offset.Zero) { drawLid(parser) }
            }
        }
    }
}

/** The bow's SVG path data (art.tsx:34–36), parsed once. */
private class GiftPaths {
    val bowLeft: Path = PathParser().parsePathString("M114 96C74 104 30 76 38 38 46 2 96 6 108 46c4 14 6 32 6 50Z").toPath()
    val bowRight: Path = PathParser().parsePathString("M114 96c2-30 12-62 40-74 30-12 56 16 40 42-14 22-44 32-80 32Z").toPath()
    val creases: Path = PathParser().parsePathString("M62 34c18 10 34 32 44 54M176 36c-18 6-38 26-54 52").toPath()
}

/** Fills [shape] with the 40 × 40 checks of art.tsx:16: a mint field with pale squares on the diagonal. */
private fun DrawScope.fillChecks(shape: Path) {
    val bounds = shape.getBounds()
    clipPath(shape) {
        drawRect(CheckBase, Offset(bounds.left, bounds.top), Size(bounds.width, bounds.height))
        val firstColumn = floor(bounds.left / 40f).toInt()
        val lastColumn = ceil(bounds.right / 40f).toInt()
        val firstRow = floor(bounds.top / 40f).toInt()
        val lastRow = ceil(bounds.bottom / 40f).toInt()
        for (column in firstColumn..lastColumn) {
            for (row in firstRow..lastRow) {
                drawRect(CheckLight, Offset(column * 40f, row * 40f), Size(20f, 20f))
                drawRect(CheckLight, Offset(column * 40f + 20f, row * 40f + 20f), Size(20f, 20f))
            }
        }
    }
}

private fun roundedRectPath(x: Float, y: Float, width: Float, height: Float, radius: Float): Path =
    Path().apply { addRoundRect(RoundRect(x, y, x + width, y + height, CornerRadius(radius))) }

/** The box, its shadow line and its ribbon (art.tsx:26–29), in board units. */
private fun DrawScope.drawBase() {
    drawRoundRect(Color(0xFFCDD0E6), Offset(8f, 300f), Size(240f, 5f), CornerRadius(2.5f))
    val box = roundedRectPath(5f, 160f, 243f, 140f, 14f)
    fillChecks(box)
    drawPath(box, Blue, style = Stroke(width = 5f))
    drawRect(Blue, Offset(108f, 162.5f), Size(34f, 135f))
}

/** The bow and the askew lid (art.tsx:31–41), in board units. */
private fun DrawScope.drawLid(paths: GiftPaths) {
    // The bow: two fat lobes meeting at a knot, in the reference's flat blue.
    drawPath(paths.bowLeft, Blue)
    drawPath(paths.bowRight, Blue)
    drawPath(paths.creases, BlueDark, style = Stroke(width = 5f, cap = StrokeCap.Round))
    rotate(-13f, pivot = Offset(4f, 162f)) {
        val lid = roundedRectPath(2f, 107f, 146f, 55f, 10f)
        fillChecks(lid)
        drawPath(lid, Blue, style = Stroke(width = 5f))
        drawRect(Blue, Offset(86f, 109.5f), Size(30f, 50f))
    }
    drawOval(Blue, Offset(99f, 82f), Size(30f, 24f))
}

/** A small SVG icon drawn from its path data on its own viewBox. */
@Composable
private fun ConfettiGiftOfferSvg(
    width: Dp,
    height: Dp,
    viewBox: Float,
    viewBoxHeight: Float = viewBox,
    paths: List<Pair<String, Color>>,
) {
    val parsed = remember { paths.map { (data, color) -> PathParser().parsePathString(data).toPath() to color } }
    Canvas(Modifier.size(width, height)) {
        val factor = size.width / viewBox
        scale(factor, size.height / viewBoxHeight, pivot = Offset.Zero) {
            parsed.forEach { (path, color) -> drawPath(path, color) }
        }
    }
}

/** The Apple mark of the first sign-in button (ConfettiGiftOffer.tsx:59), 17 × 20. */
@Composable
internal fun ConfettiGiftOfferAppleLogo() {
    ConfettiGiftOfferSvg(
        17.dp, 20.dp, 17f, 20f,
        listOf(
            "M14.1 10.6c0-2.6 2.1-3.8 2.2-3.9-1.2-1.8-3.1-2-3.7-2-1.6-.2-3.1.9-3.9.9-.8 0-2-.9-3.4-.9C3.6 4.8 1.9 5.8 1 7.4c-1.9 3.3-.5 8.1 1.3 10.8.9 1.3 1.9 2.7 3.3 2.7 1.3-.1 1.8-.9 3.4-.9 1.6 0 2 .9 3.4.8 1.4 0 2.3-1.3 3.2-2.6 1-1.5 1.4-2.9 1.4-3-.1 0-2.9-1.1-2.9-4.6ZM11.6 3c.7-.9 1.2-2 1.1-3.2-1 0-2.3.7-3 1.6-.7.8-1.2 2-1.1 3.1 1.1.1 2.3-.6 3-1.5Z" to Color.White,
        ),
    )
}

/** The four-colour G of the second sign-in button (ConfettiGiftOffer.tsx:60), 18 × 18. */
@Composable
internal fun ConfettiGiftOfferGoogleLogo() {
    ConfettiGiftOfferSvg(
        18.dp, 18.dp, 18f, 18f,
        listOf(
            "M17.6 9.2c0-.6-.1-1.2-.2-1.8H9v3.4h4.8a4.1 4.1 0 0 1-1.8 2.7v2.2h2.9c1.7-1.6 2.7-3.9 2.7-6.5Z" to Color(0xFF4285F4),
            "M9 18c2.4 0 4.5-.8 5.9-2.2L12 13.6c-.8.5-1.8.9-3 .9-2.3 0-4.3-1.6-5-3.7h-3v2.3A9 9 0 0 0 9 18Z" to Color(0xFF34A853),
            "M4 10.8a5.4 5.4 0 0 1 0-3.5V5H1a9 9 0 0 0 0 8.1l3-2.3Z" to Color(0xFFFBBC05),
            "M9 3.6c1.3 0 2.5.5 3.4 1.3L15 2.4A9 9 0 0 0 1 5l3 2.3c.7-2.1 2.7-3.7 5-3.7Z" to Color(0xFFEA4335),
        ),
    )
}

/** A four-point sparkle on a 20 × 20 viewBox (ConfettiGiftOffer.tsx:95). */
@Composable
internal fun ConfettiGiftOfferStar(size: Dp, color: Color) {
    ConfettiGiftOfferSvg(
        size, size, 20f, 20f,
        listOf("M10 0c.8 6.2 3.3 8.9 10 10-6.7 1.1-9.2 3.8-10 10-.8-6.2-3.3-8.9-10-10 6.7-1.1 9.2-3.8 10-10Z" to color),
    )
}

/** The grey cross of the close chip (ConfettiGiftOffer.tsx:86), 20 × 20. */
@Composable
internal fun ConfettiGiftOfferCloseIcon() {
    val path = remember { PathParser().parsePathString("m4.5 4.5 11 11M15.5 4.5l-11 11").toPath() }
    Canvas(Modifier.size(20.dp)) {
        val factor = size.width / 20f
        scale(factor, factor, pivot = Offset.Zero) {
            drawPath(path, Color(0xFF8E8E93), style = Stroke(width = 2.2f, cap = StrokeCap.Round))
        }
    }
}
