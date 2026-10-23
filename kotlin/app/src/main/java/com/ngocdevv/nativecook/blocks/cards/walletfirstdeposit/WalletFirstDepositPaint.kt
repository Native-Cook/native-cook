package com.ngocdevv.nativecook.blocks.cards.walletfirstdeposit

import android.graphics.BlurMaskFilter
import android.graphics.Paint as NativePaint
import android.graphics.Path as NativePath
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.roundToInt
import com.ngocdevv.nativecook.shared.sigmaToRadius

internal data class WalletGeo(val u: Float, val y0: Float, val fontScale: Float, val density: Float)

internal fun WalletGeo.x(value: Float) = value * u
internal fun WalletGeo.y(value: Float) = y0 + value * u
private fun WalletGeo.box(box: BoxRef) = Rect(x(box.x), y(box.y), x(box.x + box.w), y(box.y + box.h))

private fun Color.alpha(alpha: Float) = copy(alpha = this.alpha * alpha.coerceIn(0f, 1f))

private fun DrawScope.rounded(rect: Rect, radius: Float, color: Color, alpha: Float = 1f) {
    drawRoundRect(color = color.alpha(alpha), topLeft = rect.topLeft, size = rect.size, cornerRadius = CornerRadius(radius, radius))
}

private fun DrawScope.rounded(box: BoxRef, geo: WalletGeo, color: Color, alpha: Float = 1f) {
    rounded(geo.box(box), geo.x(box.r), color, alpha)
}

internal fun interpolate(a: Float, b: Float, amount: Float) = a + (b - a) * amount

private fun lerpBox(a: BoxRef, b: BoxRef, amount: Float) = BoxRef(
    interpolate(a.x, b.x, amount), interpolate(a.y, b.y, amount),
    interpolate(a.w, b.w, amount), interpolate(a.h, b.h, amount), interpolate(a.r, b.r, amount),
)

private fun gradientStops(stops: List<Color>, alpha: Float = 1f): Array<Pair<Float, Color>> =
    stops.mapIndexed { index, color ->
        val position = if (stops.size == 5) floatArrayOf(0f, 0.22f, 0.5f, 0.72f, 1f)[index]
        else index.toFloat() / (stops.size - 1).coerceAtLeast(1)
        position to color.alpha(alpha)
    }.toTypedArray()

private fun Brush.diagonal(stops: List<Color>, start: Offset, end: Offset, alpha: Float = 1f): Brush =
    Brush.linearGradient(*gradientStops(stops, alpha), start = start, end = end, tileMode = TileMode.Clamp)

private fun DrawScope.drawOrb(center: Offset, radius: Float, stops: List<Color>, alpha: Float = 1f) {
    if (alpha <= 0.001f || radius <= 0.2f) return
    val k = 0.72f * radius
    drawCircle(
        brush = Brush.linearGradient(*gradientStops(stops, alpha), start = center + Offset(-k, k), end = center + Offset(k, -k), tileMode = TileMode.Clamp),
        radius = radius,
        center = center,
    )
    drawCircle(
        brush = Brush.radialGradient(
            0f to Color(0x000A1040), 0.62f to Color(0x000A1040), 1f to Color(0x380A1040).alpha(alpha),
            center = center + Offset(0.35f * radius, -0.35f * radius),
            radius = 1.45f * radius,
        ),
        radius = radius,
        center = center,
    )
}

private fun DrawScope.arrow(center: Offset, size: Float, downLeft: Boolean, color: Color, width: Float) {
    val h = size / 2f
    val path = Path()
    if (downLeft) {
        path.moveTo(center.x + h, center.y - h)
        path.lineTo(center.x - h, center.y + h)
        path.moveTo(center.x - h, center.y - h * 0.15f)
        path.lineTo(center.x - h, center.y + h)
        path.lineTo(center.x + h * 0.15f, center.y + h)
    } else {
        path.moveTo(center.x - h, center.y + h)
        path.lineTo(center.x + h, center.y - h)
        path.moveTo(center.x - h * 0.15f, center.y - h)
        path.lineTo(center.x + h, center.y - h)
        path.lineTo(center.x + h, center.y + h * 0.15f)
    }
    drawPath(path, color, style = androidx.compose.ui.graphics.drawscope.Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round))
}

internal fun DrawScope.drawWalletPillArrow(center: Offset, size: Float, downLeft: Boolean, color: Color, width: Float) {
    arrow(center, size, downLeft, color, width)
}

private fun DrawScope.drawDashedLine(from: Offset, to: Offset, color: Color, width: Float, on: Float, off: Float) {
    drawLine(color, from, to, width, cap = StrokeCap.Butt, pathEffect = PathEffect.dashPathEffect(floatArrayOf(on, off)))
}

internal fun DrawScope.drawWalletBackdrop(frame: WalletFrame, geo: WalletGeo, bannerPress: Float) {
    drawRect(Page)
    val x = geo::x
    val y = geo::y

    // Header: a five-stop avatar orb, notification dot, and the pale settings disc.
    drawOrb(Offset(x(100f), y(268f)), x(54.5f), AvatarOrb)
    if (frame.dot < 1f) drawCircle(Dot.alpha(1f - frame.dot), x(5.5f) * (1f - 0.5f * frame.dot), Offset(x(141.5f), y(226.5f)))
    drawCircle(Card, x(60f), Offset(x(1097f), y(269f)))
    val nut = Path()
    repeat(6) { index ->
        val angle = index * PI.toFloat() / 3f - PI.toFloat() / 2f
        val point = Offset(x(1098f) + cos(angle) * x(20f), y(267.5f) + sin(angle) * x(20f))
        if (index == 0) nut.moveTo(point.x, point.y) else nut.lineTo(point.x, point.y)
    }
    nut.close()
    drawPath(nut, Nut, style = androidx.compose.ui.graphics.drawscope.Stroke(x(11f), cap = StrokeCap.Round, join = StrokeJoin.Round))
    drawCircle(Card, x(9.5f), Offset(x(1098.5f), y(267.5f)))

    // Verification banner, including the exact partial progress and press scale.
    val bannerAlpha = 1f - frame.banner
    if (bannerAlpha > 0.001f) {
        val pressScale = (1f - 0.03f * frame.banner) * (1f - 0.03f * bannerPress)
        withTransform({
            translate(0f, -x(30f) * frame.banner)
            scale(pressScale, pressScale, Offset(x(Banner.x + Banner.w / 2f), y(Banner.y + Banner.h / 2f)))
        }) {
            rounded(Banner, geo, Card, bannerAlpha)
            val track = Rect(x(57f), y(994.5f), x(1148f), y(1006.5f))
            rounded(track, x(6f), Track, bannerAlpha)
            val fraction = 0.428f + (1f - 0.428f) * frame.fill
            val width = track.width * fraction
            val fadeWidth = min(x(80f) * (1f - frame.fill), width)
            drawRoundRect(
                brush = Brush.linearGradient(
                    0f to BarStart.alpha(bannerAlpha),
                    max(0.01f, (width - fadeWidth) / width) to BarEnd.alpha(bannerAlpha),
                    1f to BarEnd.copy(alpha = 0f),
                    start = Offset(track.left, track.center.y), end = Offset(track.left + width, track.center.y), tileMode = TileMode.Clamp,
                ),
                topLeft = track.topLeft,
                size = Size(width, track.height),
                cornerRadius = CornerRadius(x(6f), x(6f)),
            )
        }
    }

    // Receive / Send pills. Their labels are rendered by the text layer.
    listOf(frame.pill0, frame.pill1).forEachIndexed { index, amount ->
        if (amount > 0.001f) {
            val box = if (index == 0) BoxRef(46.3f, 722.4f, 330.9f, 127.2f, 63.6f) else BoxRef(394.2f, 722.4f, 330.5f, 127.2f, 63.6f)
            withTransform({
                scale(0.9f + 0.1f * amount, 0.9f + 0.1f * amount, Offset(x(box.x + box.w / 2f), y(box.y + box.h / 2f)))
            }) { rounded(box, geo, Ink, min(1f, amount * 2f)) }
        }
    }

    // Transactions: heading chip, card, hairline and two logos with independent 50 ms entrances.
    if (frame.txHead > 0.001f) {
        val headingRise = x(48f) * (1f - frame.txHead)
        rounded(Rect(x(984.8f), y(942.9f) + headingRise, x(1153f), y(1028f) + headingRise), x(42.5f), Pill, frame.txHead)
    }
    if (frame.txCard > 0.001f) {
        val cardRise = x(48f) * (1f - frame.txCard)
        drawRoundRect(Card.alpha(frame.txCard), Offset(x(TxCard.x), y(TxCard.y) + cardRise), Size(x(TxCard.w), x(TxCard.h)), CornerRadius(x(TxCard.r), x(TxCard.r)))
        drawRect(Hairline.alpha(frame.txCard), Offset(x(22.25f), y(1264.75f) + cardRise), Size(x(1159.25f), x(1.5f)))
    }
    if (frame.txRow0 > 0.001f) {
        val row0Rise = x(48f) * (1f - frame.txRow0)
        drawCafeLogo(Offset(x(130f), y(1160f) + row0Rise), x(60.5f), geo, frame.txRow0)
    }
    if (frame.txRow1 > 0.001f) {
        val row1Rise = x(48f) * (1f - frame.txRow1)
        drawOrb(Offset(x(129.5f), y(1380f) + row1Rise), x(61f), DieterOrb, frame.txRow1)
    }

    // Three action cards morph into four payee tiles. Tile starts are staggered by 40 ms.
    val actionValues = floatArrayOf(frame.card0, frame.card1, frame.card2)
    actionValues.forEachIndexed { index, amount ->
        rounded(lerpBox(actionBox(index), payeeBox(index), amount), geo, Card)
    }
    if (frame.card3 > 0.001f) {
        val fourth = payeeBox(3)
        rounded(fourth.copy(x = fourth.x + 150f * (1f - frame.card3)), geo, Card, frame.card3)
    }

    // Action icons in the outgoing row.
    val actionAlpha = 1f - frame.actionsOut
    if (actionAlpha > 0.001f) {
        val iconXs = floatArrayOf(120.5f, 509.3f, 897.9f)
        listOf(Color(0xFF9580D6), Color(0xFF62B98B), Color(0xFF6555CA)).forEachIndexed { index, color ->
            val center = Offset(x(iconXs[index]), y(1335.5f))
            withTransform({ scale(0.94f + 0.06f * actionAlpha, 0.94f + 0.06f * actionAlpha, center) }) {
                if (index == 0) {
                    drawLine(color.alpha(actionAlpha), center - Offset(x(40f), 0f), center + Offset(x(40f), 0f), x(10f), StrokeCap.Round)
                    drawLine(color.alpha(actionAlpha), center - Offset(0f, x(40f)), center + Offset(0f, x(40f)), x(10f), StrokeCap.Round)
                } else arrow(center, x(66f), index == 2, color.alpha(actionAlpha), x(10f))
            }
        }
    }

    // Payee orbs and white badge glyphs. The orb itself starts at 0.6 scale, as in RN.
    val orbValues = floatArrayOf(frame.orb0, frame.orb1, frame.orb2, frame.orb3)
    orbValues.forEachIndexed { index, amount ->
        if (amount > 0.001f) {
            val box = payeeBox(index)
            val center = Offset(x(box.x + box.w / 2f), y(1785.5f))
            withTransform({ scale(0.6f + 0.4f * amount, 0.6f + 0.4f * amount, center) }) {
                drawOrb(center, x(61f), PayeeOrbs[index], min(1f, amount * 3f))
                val badgeCenter = center + Offset(x(34f), x(34f))
                drawCircle(Color.White.alpha(min(1f, amount * 3f)), x(23f), badgeCenter)
                drawBadgeGlyph(index, badgeCenter, x(19f), min(1f, amount * 3f))
            }
        }
    }

    // Analytics card, flat empty chart and exact funded chart coordinates.
    drawRoundRect(Pill, Offset(x(1007f), y(interpolate(1715.5f, 2039.4f, frame.reflow2))), Size(x(146f), x(84f)), CornerRadius(x(42f), x(42f)))
    val chartTop = interpolate(1823.2f, 2147.5f, frame.reflow2)
    val chartBottom = chartTop + interpolate(476.8f, 427.5f, frame.plot)
    drawRoundRect(Card, Offset(x(22.25f), y(chartTop)), Size(x(1159.25f), x(582.3f)), CornerRadius(x(78f), x(78f)))
    val flatAlpha = 1f - frame.plotA
    if (flatAlpha > 0.001f) {
        val flatTop = chartTop + 294.8f
        val flatHeight = chartBottom - flatTop
        drawRect(
            Brush.linearGradient(listOf(PlotTop.alpha(flatAlpha), Card.alpha(flatAlpha)), Offset(x(600f), y(flatTop)), Offset(x(600f), y(chartBottom))),
            Offset(x(72f), y(flatTop)), Size(x(1056f), x(flatHeight)),
        )
        drawDashedLine(Offset(x(72f), y(flatTop)), Offset(x(1128f), y(flatTop)), Dash.alpha(flatAlpha), x(2.5f), x(6f), x(4f))
    }
    drawChart(frame, geo, chartTop, chartBottom)
}

private fun DrawScope.drawCafeLogo(center: Offset, radius: Float, geo: WalletGeo, alpha: Float) {
    val x = geo::x
    drawCircle(Cafe.alpha(alpha), radius, center)
    drawCircle(Color.White.alpha(alpha * 0.9f), radius * 0.84f, center, style = androidx.compose.ui.graphics.drawscope.Stroke(x(3f)))
    val path = Path()
    val hh = radius * 0.42f
    val hw = radius * 0.3f
    path.moveTo(center.x - hw, center.y - hh * 0.55f); path.lineTo(center.x - hw, center.y + hh)
    path.moveTo(center.x + hw, center.y - hh * 0.55f); path.lineTo(center.x + hw, center.y + hh)
    path.moveTo(center.x - hw, center.y + hh * 0.15f); path.lineTo(center.x + hw, center.y + hh * 0.15f)
    drawPath(path, Color.White.alpha(alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(x(7.5f), cap = StrokeCap.Butt))
    val roof = Path()
    roof.moveTo(center.x - hw * 1.45f, center.y - hh * 0.35f); roof.lineTo(center.x, center.y - hh * 1.15f); roof.lineTo(center.x + hw * 1.45f, center.y - hh * 0.35f)
    drawPath(roof, Color.White.alpha(alpha), style = androidx.compose.ui.graphics.drawscope.Stroke(x(6f), cap = StrokeCap.Round))
}

private fun DrawScope.drawBadgeGlyph(index: Int, center: Offset, size: Float, alpha: Float) {
    val path = Path()
    val stroke = androidx.compose.ui.graphics.drawscope.Stroke(size * 0.2f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    when (index) {
        0 -> {
            path.moveTo(center.x - size * 0.35f, center.y - size * 0.35f); path.lineTo(center.x + size * 0.35f, center.y - size * 0.35f)
            path.moveTo(center.x - size * 0.05f, center.y - size * 0.35f); path.lineTo(center.x - size * 0.05f, center.y + size * 0.4f)
            path.moveTo(center.x - size * 0.2f, center.y + size * 0.02f); path.lineTo(center.x + size * 0.18f, center.y + size * 0.02f)
        }
        1 -> {
            path.moveTo(center.x - size * 0.42f, center.y - size * 0.35f); path.lineTo(center.x - size * 0.12f, center.y + size * 0.38f); path.lineTo(center.x + size * 0.18f, center.y - size * 0.35f)
            drawCircle(Color.Black.alpha(alpha), size * 0.11f, center + Offset(size * 0.42f, size * 0.3f))
        }
        2 -> {
            path.moveTo(center.x - size * 0.3f, center.y + size * 0.4f); path.quadraticBezierTo(center.x - size * 0.35f, center.y - size * 0.4f, center.x + size * 0.35f, center.y - size * 0.38f); path.quadraticBezierTo(center.x + size * 0.3f, center.y + size * 0.35f, center.x - size * 0.3f, center.y + size * 0.4f)
        }
        else -> {
            drawRoundRect(Color.Black.alpha(alpha), center - Offset(size * 0.42f, size * 0.3f), Size(size * 0.84f, size * 0.6f), CornerRadius(size * 0.12f, size * 0.12f))
            drawRect(Color.White.alpha(alpha), center - Offset(size * 0.42f, size * 0.12f), Size(size * 0.84f, size * 0.12f))
            return
        }
    }
    drawPath(path, Color.Black.alpha(alpha), style = stroke)
}

private fun DrawScope.drawChart(frame: WalletFrame, geo: WalletGeo, top: Float, bottom: Float) {
    val x = geo::x
    val y = geo::y
    val sy = 0.35f + 0.65f * frame.plot
    val offset = bottom - 2575f
    if (frame.forecast > 0.001f) {
        val forecast = pathFromPoints(ChartForecast, geo, offset, sy, 2575f)
        val area = Path().apply {
            addPath(forecast)
            lineTo(x(1134f), y(bottom)); lineTo(x(470f), y(bottom)); close()
        }
        drawPath(area, Brush.linearGradient(listOf(ForecastTop.alpha(frame.forecast), Card.alpha(frame.forecast)), Offset(x(800f), y(bottom - 245f)), Offset(x(800f), y(bottom))))
        drawPath(forecast, Dash.alpha(frame.forecast), style = androidx.compose.ui.graphics.drawscope.Stroke(x(2.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(x(6f), x(4f)))))
        val trend = pathFromPoints(ChartTrend, geo, offset, sy, 2575f)
        drawPath(trend, Dash.alpha(0.8f * frame.forecast), style = androidx.compose.ui.graphics.drawscope.Stroke(x(2.5f), pathEffect = PathEffect.dashPathEffect(floatArrayOf(x(6f), x(4f)))))
    }
    if (frame.line > 0.001f) {
        val line = pathFromPoints(ChartLine, geo, offset, sy, 2575f)
        val area = Path().apply {
            addPath(line); lineTo(x(470f), y(bottom)); lineTo(x(72f), y(bottom)); close()
        }
        clipRect(left = x(68f), top = y(top), right = x(68f) + (x(470f) - x(68f)) * frame.line, bottom = y(bottom + 40f)) {
            drawPath(area, Peach.alpha(0.95f))
            drawPath(line, Orange, style = androidx.compose.ui.graphics.drawscope.Stroke(x(4.5f), cap = StrokeCap.Round, join = StrokeJoin.Round))
        }
    }
}

private fun pathFromPoints(points: List<PointRef>, geo: WalletGeo, dy: Float, sy: Float, base: Float): Path = Path().apply {
    points.forEachIndexed { index, point ->
        val px = geo.x(point.x)
        val py = geo.y(base + (point.y - base) * sy + dy)
        if (index == 0) moveTo(px, py) else lineTo(px, py)
    }
}

internal fun DrawScope.drawWalletHero(frame: WalletFrame, geo: WalletGeo, cardPress: Float, bannerPress: Float) {
    val x = geo::x
    val y = geo::y
    val u = frame.flight
    val v = 1f - u
    val cx = v * v * 1005.5f + 2f * u * v * 960f + u * u * 595.5f
    val cy = v * v * 882f + 2f * u * v * 190f + u * u * 268.5f
    var scaleValue = lerp(1f, 0.752f, u) * (1f + 0.08f * frame.lift) * (1f - frame.squeeze)
    scaleValue *= 1f - 0.03f * cardPress * clamp01(u)
    var finalCx = cx
    var finalCy = cy
    if (u < 0.05f && bannerPress > 0f) {
        val bs = 1f - 0.03f * bannerPress
        val bcx = Banner.x + Banner.w / 2f
        val bcy = Banner.y + Banner.h / 2f
        finalCx = bcx + (cx - bcx) * bs
        finalCy = bcy + (cy - bcy) * bs
        scaleValue *= bs
    }
    val w = x(232f)
    val h = x(147f)
    val radius = x(18f)
    withTransform({
        translate(x(finalCx), y(finalCy))
        rotate(frame.tilt, pivot = Offset.Zero)
        scale(scaleValue, scaleValue, pivot = Offset.Zero)
        translate(-w / 2f, -h / 2f)
    }) {
        // Ambient and contact shadows remain separate through the full flight.
        drawIntoCanvas { canvas ->
            val native = canvas.nativeCanvas
            NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((255f * interpolate(interpolate(0.42f, 0.2f, clamp01(u)), 0.26f, frame.lift)).roundToInt(), 0x2A, 0x44, 0x66)
                maskFilter = BlurMaskFilter(sigmaToRadius(geo.x(interpolate(interpolate(9f, 4f, clamp01(u)), 20f, frame.lift))), BlurMaskFilter.Blur.NORMAL)
            }.also { paint ->
                val oy = geo.x(interpolate(interpolate(13f, 5f, clamp01(u)), 34f, frame.lift))
                native.drawRoundRect(w * 0.06f, oy + h * 0.06f, w * 0.94f, oy + h * 0.96f, radius, radius, paint)
            }
            NativePaint(NativePaint.ANTI_ALIAS_FLAG).apply {
                color = android.graphics.Color.argb((255f * interpolate(interpolate(0.22f, 0.16f, clamp01(u)), 0f, frame.lift)).roundToInt(), 0x1A, 0x2A, 0x40)
                maskFilter = BlurMaskFilter(sigmaToRadius(geo.x(2.5f)), BlurMaskFilter.Blur.NORMAL)
            }.also { paint -> native.drawRoundRect(w * 0.02f, geo.x(2f), w * 0.98f, geo.x(2f) + h, radius, radius, paint) }
        }

        val bodyColors = listOf(
            Color(0xFF2062D8), Color(0xFF134BCA), Color(0xFF155DD3), Color(0xFF1174E2), Color(0xFF0C8CF2),
            Color(0xFF0C9FFF), Color(0xFF09B5F9), Color(0xFF11E4FE), Color(0xFF12FDFE), Color(0xFF2FF5EC),
        )
        val body = Rect(0f, 0f, w, h)
        drawRoundRect(Brush.linearGradient(*bodyColors.mapIndexed { index, color -> index.toFloat() / 9f to color }.toTypedArray(), start = Offset.Zero, end = Offset(0f, h), tileMode = TileMode.Clamp), body.topLeft, body.size, CornerRadius(radius, radius))
        clipPath(Path().apply { addRoundRect(RoundRect(body, CornerRadius(radius, radius))) }) {
            drawRect(Brush.radialGradient(listOf(Color(0x597FF6FF), Color(0x007FF6FF)), Offset(w * 0.55f, h * 1.05f), w * 0.75f), size = Size(w, h))
            drawRect(Brush.linearGradient(listOf(Color(0x47FFFFFF), Color(0x00FFFFFF)), Offset.Zero, Offset(0f, geo.x(3f))), size = Size(w, geo.x(3f)))
            if (frame.sheen > 0f && frame.sheen < 1f) {
                val sheenX = interpolate(-0.6f * w, 1.6f * w, easeInOut(frame.sheen))
                drawRect(
                    Brush.linearGradient(listOf(Color(0x00FFFFFF), Color(0x8CFFFFFF), Color(0x00FFFFFF)), Offset(sheenX - 0.22f * w, 0f), Offset(sheenX + 0.22f * w, 0.35f * h), tileMode = TileMode.Clamp),
                    size = Size(w, h),
                )
            }
            // Asterisk mark, then the invented coral and amber network squares.
            val center = Offset(geo.x(32f), geo.x(33f))
            repeat(3) { index ->
                val angle = PI.toFloat() / 3f * index + PI.toFloat() / 2f
                val arm = geo.x(11.5f)
                drawLine(Color.White, center + Offset(-cos(angle) * arm, -sin(angle) * arm), center + Offset(cos(angle) * arm, sin(angle) * arm), geo.x(6f), StrokeCap.Round)
            }
            val markCenter = Offset(geo.x(192f), geo.x(28f))
            drawRoundRect(Color.White.alpha(0.95f), markCenter - Offset(geo.x(19f), geo.x(13.5f)), Size(geo.x(38f), geo.x(27f)), CornerRadius(geo.x(8f), geo.x(8f)))
            drawRoundRect(Color(0xFFF23B2E), markCenter - Offset(geo.x(16.5f), geo.x(10.5f)), Size(geo.x(21f), geo.x(21f)), CornerRadius(geo.x(6f), geo.x(6f)))
            drawRoundRect(Color(0xE6FFA41C), markCenter - Offset(geo.x(4.5f), geo.x(10.5f)), Size(geo.x(21f), geo.x(21f)), CornerRadius(geo.x(6f), geo.x(6f)))
        }
    }
}
