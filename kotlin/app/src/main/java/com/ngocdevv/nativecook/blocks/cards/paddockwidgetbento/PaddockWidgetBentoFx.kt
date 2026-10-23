package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import android.graphics.BlurMaskFilter
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RenderEffect
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin


/** The pit stop's motion blur: behind every tile in flight, a smear of its own colour, stronger with speed. */
@Composable
internal fun PaddockPitTrails(scale: Float, runtime: PaddockWidgetBentoRuntime) {
    val density = LocalDensity.current.density
    val tiles = remember {
        val q = PaddockTheme.quick
        listOf(
            Triple(q.left, PaddockWidgetBentoMotion.slotY[0].toFloat(), q), Triple(q.left, PaddockWidgetBentoMotion.slotY[1].toFloat(), q), Triple(q.left, PaddockWidgetBentoMotion.slotY[2].toFloat(), q),
            Triple(PaddockTheme.pitcast.left, PaddockTheme.pitcast.top, PaddockTheme.pitcast), Triple(PaddockTheme.stats.left, PaddockTheme.stats.top, PaddockTheme.stats),
            Triple(PaddockTheme.glossary.left, PaddockTheme.glossary.top, PaddockTheme.glossary), Triple(PaddockTheme.education.left, PaddockTheme.education.top, PaddockTheme.education),
            Triple(PaddockTheme.hero.left, PaddockTheme.hero.top, PaddockTheme.hero), Triple(PaddockTheme.guide.left, PaddockTheme.guide.top, PaddockTheme.guide), Triple(PaddockTheme.banner.left, PaddockTheme.banner.top, PaddockTheme.banner),
        )
    }
    val radii = remember {
        floatArrayOf(31f, 31f, 31f, 28f, 28f, 31f, 31f, 28f, 28f, 28f)
    }
    Canvas(Modifier.requiredSize((PaddockTheme.REFERENCE_WIDTH * scale).dp, (PaddockTheme.gridHeight * scale).dp)) {
        val pit = runtime.pit.value
        if (pit <= 0.0) return@Canvas
        val ys = runtime.frame.value.cardY
        val s = scale * density
        for (i in 0 until PaddockWidgetBentoMotion.Tile.count) {
            val tile = tiles[i]
            val top = if (i < 3) (ys[PaddockWidgetBentoCardID.entries[i]] ?: tile.second.toDouble()).toFloat() else tile.second
            val head = PaddockWidgetBentoMotion.pitTile(i, pit)
            val alpha = PaddockWidgetBentoMotion.smearAlpha(head.speed)
            if (alpha < 0.01) continue
            val tail = PaddockWidgetBentoMotion.pitTile(i, pit - PaddockWidgetBentoMotion.Pit.ghosts * PaddockWidgetBentoMotion.Pit.ghostStep)
            val left = min(head.x, tail.x)
            val right = max(head.x, tail.x) + tile.third.width
            val y = (head.y + tail.y) / 2
            val x0 = (tile.first + tail.x + tile.third.width / 2).toFloat() * s
            val k = (head.x - tail.x).toFloat() * s
            if (kotlin.math.abs(k) < 0.5f) continue
            val color = PaddockTheme.tileColors[i]
            drawRoundRect(
                brush = Brush.horizontalGradient(
                    listOf(color.copy(alpha = 0f), color.copy(alpha = min(1.0, alpha).toFloat())),
                    startX = x0,
                    endX = x0 + k,
                ),
                topLeft = Offset((tile.first + left).toFloat() * s, (top + y).toFloat() * s),
                size = Size((right - left).toFloat() * s, tile.third.height * s),
                cornerRadius = CornerRadius(radii[i] * s),
            )
        }
    }
}

/**
 * Gooey neighbours: while the cards are arranged, the three capsules are drawn again under the real cards, blurred and
 * alpha-thresholded as one layer (API 31+), so where two come close they join with a liquid bridge of mixed colour.
 */
@Composable
internal fun PaddockGooLayer(scale: Float, runtime: PaddockWidgetBentoRuntime) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
    val pad = PaddockWidgetBentoMotion.Goo.pad.toFloat()
    val q = PaddockTheme.quick
    val colors = remember { listOf(PaddockTheme.violet, PaddockTheme.orange, PaddockTheme.yellow) }
    val density = LocalDensity.current.density
    val width = (q.width + 2 * pad) * scale
    val height = (PaddockWidgetBentoMotion.slotY[2] - PaddockWidgetBentoMotion.slotY[0] + q.height + 2 * pad).toFloat() * scale
    Box(
        Modifier.offset(((q.left - pad) * scale).dp, ((PaddockWidgetBentoMotion.slotY[0] - pad).toFloat() * scale).dp)
            .requiredSize(width.dp, height.dp)
            .graphicsLayer {
                val handles = runtime.frame.value.handlesOpacity
                alpha = if (handles > 0.01) min(1.0, handles).toFloat() else 0f
                val sigma = (PaddockWidgetBentoMotion.Goo.blur * scale * density).toFloat()
                val blur = RenderEffect.createBlurEffect(sigma, sigma, Shader.TileMode.DECAL)
                val matrix = ColorMatrix(
                    floatArrayOf(
                        1f, 0f, 0f, 0f, 0f,
                        0f, 1f, 0f, 0f, 0f,
                        0f, 0f, 1f, 0f, 0f,
                        0f, 0f, 0f, PaddockWidgetBentoMotion.Goo.gain.toFloat(), -PaddockWidgetBentoMotion.Goo.bias.toFloat() * 255f,
                    ),
                )
                renderEffect = RenderEffect.createColorFilterEffect(ColorMatrixColorFilter(matrix), blur).asComposeRenderEffect()
            },
    ) {
        Canvas(Modifier.requiredSize(width.dp, height.dp)) {
            val f = runtime.frame.value
            if (f.handlesOpacity <= 0.01) return@Canvas
            val s = scale * density
            val time = if (f.loopTime >= 0.0) f.loopTime else runtime.clock.value
            for (pass in 0..1) {
                for (id in PaddockWidgetBentoCardID.entries) {
                    val held = f.selectedID == id
                    if ((pass == 1) != held) continue
                    val cx = (pad + q.width / 2) * s
                    val cy = (pad + (f.cardY[id] ?: PaddockWidgetBentoMotion.slotY[0]).toFloat() - PaddockWidgetBentoMotion.slotY[0].toFloat() + q.height / 2) * s
                    withTransform({
                        translate(cx, cy)
                        rotate(if (held) f.rotationDegrees.toFloat() else PaddockWidgetBentoMotion.jiggle(id.ordinal, time, f.handlesOpacity).toFloat(), Offset.Zero)
                        if (held) scale(f.selectedScale.toFloat(), f.selectedScale.toFloat(), Offset.Zero)
                    }) {
                        drawRoundRect(
                            colors[id.ordinal],
                            topLeft = Offset(-q.width / 2 * s, -q.height / 2 * s),
                            size = Size(q.width * s, q.height * s),
                            cornerRadius = CornerRadius(PaddockTheme.radiusQuick * s),
                        )
                    }
                }
            }
        }
    }
}

/** Glyph outlines of "exclusive" / "widgets" in Montserrat ExtraBold, one path per glyph, in pixels. */
internal class PaddockLettering(fonts: PaddockFonts, px: Float) {
    val first: List<Path>
    val second: List<Path>

    init {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            typeface = fonts.typefaces.getValue(PaddockFace.ExtraBold)
            textSize = PaddockTheme.heroGlyph * px
        }
        fun glyphs(text: String, x: Float, baseline: Float): List<Path> {
            var pen = x * px
            return text.map { ch ->
                val path = Path()
                paint.getTextPath(ch.toString(), 0, 1, pen, baseline * px, path)
                pen += paint.measureText(ch.toString())
                path
            }
        }
        first = glyphs("exclusive", PaddockTheme.heroFirstX, PaddockTheme.heroFirst)
        second = glyphs("widgets", PaddockTheme.heroSecondX, PaddockTheme.heroSecond)
    }
}

private fun fillPaint(color: Color, alpha: Float = color.alpha) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
    this.color = color.copy(alpha = alpha).toArgb()
}

private class PaddockStickerPaints(private val px: Float) {
    private fun stroked(p: Paint) = Paint(p).apply {
        style = Paint.Style.STROKE
        strokeWidth = PaddockWidgetBentoMotion.Peel.border.toFloat() * 2f * px
        strokeJoin = Paint.Join.ROUND
    }

    private val redFill = fillPaint(PaddockTheme.red)
    private val glueFill = fillPaint(Color.White, 0.1f)
    private val backFill = fillPaint(PaddockTheme.stickerBack)
    private val shadowFill = fillPaint(PaddockTheme.stickerShadow, 0.42f).apply {
        // BlurMaskFilter takes a radius; Skia's sigma is 0.57735 * radius + 0.5.
        val sigma = PaddockWidgetBentoMotion.Peel.shadowBlur.toFloat() * px
        maskFilter = BlurMaskFilter(max(1f, (sigma - 0.5f) / 0.57735f), BlurMaskFilter.Blur.NORMAL)
    }
    val rose = fillPaint(PaddockTheme.rose)
    val white = fillPaint(Color.White)
    val red = Pair(redFill, stroked(redFill))
    val glue = Pair(glueFill, stroked(glueFill))
    val shadow = Pair(shadowFill, stroked(shadowFill))
    val back = Pair(backFill, stroked(backFill))
    val shade = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        xfermode = PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP)
        val fold = PaddockTheme.stickerFold
        shader = LinearGradient(0f, 0f, 1f, 0f, fold.toArgb(), fold.copy(alpha = 0f).toArgb(), Shader.TileMode.CLAMP)
    }
    val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = android.graphics.Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 3f * px
        strokeCap = Paint.Cap.ROUND
    }
}

/**
 * "exclusive widgets" as a die-cut sticker. The corner is pulled to `distance` along `angle`: letters past the fold are
 * cut from the card (a faint glue ghost stays) and flip over the fold as the sticker's paper back, shaded at the bend and
 * casting a soft shadow. The slap after a peel presses the lettering and throws impact lines.
 */
@Composable
internal fun PaddockSticker(scale: Float, fonts: PaddockFonts, runtime: PaddockWidgetBentoRuntime) {
    val density = LocalDensity.current.density
    val px = scale * density
    val pad = PaddockWidgetBentoMotion.Goo.pad.toFloat()
    val lettering = remember(fonts, px) { PaddockLettering(fonts, px) }
    val paints = remember(px) { PaddockStickerPaints(px) }
    Canvas(
        Modifier.offset((-pad * scale).dp, (-pad * scale).dp)
            .requiredSize(((PaddockTheme.hero.width + 2 * pad) * scale).dp, ((PaddockTheme.hero.height + 2 * pad) * scale).dp),
    ) {
        val peel = runtime.peel.value
        val slap = PaddockWidgetBentoMotion.slapPose(peel.slap)
        val n = drawContext.canvas.nativeCanvas
        fun letters(first: Paint, second: Paint) {
            lettering.first.forEach { n.drawPath(it, first) }
            lettering.second.forEach { n.drawPath(it, second) }
        }
        fun silhouette(cut: Pair<Paint, Paint>) {
            lettering.first.forEach { n.drawPath(it, cut.second) }
            lettering.second.forEach { n.drawPath(it, cut.second) }
            lettering.first.forEach { n.drawPath(it, cut.first) }
            lettering.second.forEach { n.drawPath(it, cut.first) }
        }
        fun clipHalf(mx: Float, my: Float, ux: Double, uy: Double, side: Double) {
            val deg = (atan2(side * uy, side * ux) * 180.0 / PI).toFloat()
            n.translate(mx, my)
            n.rotate(deg)
            n.clipRect(0f, -4000f, 4000f, 4000f)
            n.rotate(-deg)
            n.translate(-mx, -my)
        }
        fun reflect(mx: Float, my: Float, ux: Double, uy: Double) {
            val along = (atan2(uy, ux) * 180.0 / PI).toFloat() + 90f
            n.translate(mx, my)
            n.rotate(along)
            n.scale(1f, -1f)
            n.rotate(-along)
            n.translate(-mx, -my)
        }
        val root = n.save()
        n.translate(pad * px, pad * px)
        val cx = PaddockTheme.hero.width / 2 * px
        val cy = PaddockTheme.hero.height / 2 * px
        val body = n.save()
        n.translate(cx, cy)
        n.scale(slap.press.toFloat(), slap.press.toFloat())
        n.translate(-cx, -cy)
        if (peel.distance < 0.5) {
            letters(paints.rose, paints.white)
        } else {
            val rad = peel.angle * PI / 180.0
            val ux = cos(rad)
            val uy = sin(rad)
            val mx = (PaddockWidgetBentoMotion.Peel.cornerX * px + ux * peel.distance * px / 2).toFloat()
            val my = (PaddockWidgetBentoMotion.Peel.cornerY * px + uy * peel.distance * px / 2).toFloat()
            // The part still stuck down: the card's own red, printed with the letters.
            var mark = n.save()
            clipHalf(mx, my, ux, uy, 1.0)
            silhouette(paints.red)
            letters(paints.rose, paints.white)
            n.restoreToCount(mark)
            // Glue left where the flap came away.
            mark = n.save()
            clipHalf(mx, my, ux, uy, -1.0)
            silhouette(paints.glue)
            n.restoreToCount(mark)
            // The flap's shadow on the card, a little further along the pull and down.
            mark = n.save()
            n.translate((ux * PaddockWidgetBentoMotion.Peel.shadowOffset * px).toFloat(), (PaddockWidgetBentoMotion.Peel.shadowOffset * px / 2 + uy * PaddockWidgetBentoMotion.Peel.shadowOffset * px).toFloat())
            reflect(mx, my, ux, uy)
            clipHalf(mx, my, ux, uy, -1.0)
            silhouette(paints.shadow)
            n.restoreToCount(mark)
            // The flap: the sticker seen from the back (plain paper), darker at the bend.
            mark = n.save()
            reflect(mx, my, ux, uy)
            clipHalf(mx, my, ux, uy, -1.0)
            n.saveLayer(null, null)
            silhouette(paints.back)
            n.translate(mx, my)
            n.rotate((atan2(-uy, -ux) * 180.0 / PI).toFloat())
            n.scale(PaddockWidgetBentoMotion.Peel.foldShade.toFloat() * px, 1f)
            n.drawRect(-1f, -4000f, 1e4f, 4000f, paints.shade)
            n.restoreToCount(mark)
        }
        n.restoreToCount(body)
        if (slap.lines >= 0.0) {
            val e = slap.lines
            paints.line.alpha = (max(0.0, min(1.0, 1.0 - e)) * 255).toInt()
            val ox = ((PaddockWidgetBentoMotion.Peel.cornerX - 8) * px).toFloat()
            val oy = ((PaddockWidgetBentoMotion.Peel.cornerY - 30) * px).toFloat()
            for (i in 0 until PaddockWidgetBentoMotion.Peel.lines) {
                val a = (-100.0 + i * 120.0 / (PaddockWidgetBentoMotion.Peel.lines - 1)) * PI / 180.0
                val inner = ((PaddockWidgetBentoMotion.Peel.lineInner + PaddockWidgetBentoMotion.Peel.lineReach * e) * px).toFloat()
                val outer = inner + (PaddockWidgetBentoMotion.Peel.lineLength * (1 - e) * px).toFloat()
                n.drawLine(
                    ox + cos(a).toFloat() * inner, oy + sin(a).toFloat() * inner,
                    ox + cos(a).toFloat() * outer, oy + sin(a).toFloat() * outer, paints.line,
                )
            }
        }
        n.restoreToCount(root)
    }
}
