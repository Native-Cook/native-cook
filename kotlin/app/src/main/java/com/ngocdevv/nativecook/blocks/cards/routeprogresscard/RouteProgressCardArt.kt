package com.ngocdevv.nativecook.blocks.cards.routeprogresscard

import com.ngocdevv.nativecook.shared.sigmaToRadius
import android.graphics.BlurMaskFilter
import android.graphics.Paint
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.PathParser
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/** How a leg is travelled: picks the icon and the drawing in the middle of the card. */
enum class Travel { Walk, Drive, Fly }

private val Ink = Color.White.copy(alpha = 0.9f)

private fun outline(commands: String): Path = PathParser().parsePathString(commands).toPath()

// Two footprints, each a sole and a heel.
private val Steps = outline(
    "M 3.7 8.2 C 3.7 5.77 5 3.8 6.6 3.8 C 8.2 3.8 9.5 5.77 9.5 8.2 C 9.5 10.63 8.2 12.6 6.6 12.6 C 5 12.6 3.7 10.63 3.7 8.2 Z " +
        "M 5 15.7 C 5 14.54 5.9 13.6 7 13.6 C 8.1 13.6 9 14.54 9 15.7 C 9 16.86 8.1 17.8 7 17.8 C 5.9 17.8 5 16.86 5 15.7 Z " +
        "M 10.7 6 C 10.7 3.57 12 1.6 13.6 1.6 C 15.2 1.6 16.5 3.57 16.5 6 C 16.5 8.43 15.2 10.4 13.6 10.4 C 12 10.4 10.7 8.43 10.7 6 Z " +
        "M 11.2 13.5 C 11.2 12.34 12.1 11.4 13.2 11.4 C 14.3 11.4 15.2 12.34 15.2 13.5 C 15.2 14.66 14.3 15.6 13.2 15.6 C 12.1 15.6 11.2 14.66 11.2 13.5 Z",
)

// A car seen from the front: body, windscreen line and wheels are stroked, the headlights filled.
private val Car = outline(
    "M 3 15 L 3 10 Q 3 8.5 4.2 8 L 5.5 4.5 Q 6 3 7.6 3 L 12.4 3 Q 14 3 14.5 4.5 L 15.8 8 Q 17 8.5 17 10 L 17 15 Z " +
        "M 5.2 8 L 14.8 8 M 5.2 15 L 5.2 17 M 14.8 15 L 14.8 17",
)
private val CarLights = outline(
    "M 5.3 11.4 C 5.3 10.79 5.79 10.3 6.4 10.3 C 7.01 10.3 7.5 10.79 7.5 11.4 C 7.5 12.01 7.01 12.5 6.4 12.5 C 5.79 12.5 5.3 12.01 5.3 11.4 Z " +
        "M 12.5 11.4 C 12.5 10.79 12.99 10.3 13.6 10.3 C 14.21 10.3 14.7 10.79 14.7 11.4 C 14.7 12.01 14.21 12.5 13.6 12.5 C 12.99 12.5 12.5 12.01 12.5 11.4 Z",
)

// A plane seen from above, nose up.
private val Plane = outline(
    "M 10 1.5 Q 11.4 1.5 11.4 4 L 11.4 7.6 L 18 11.6 L 18 13.4 L 11.4 11.4 L 11.4 15.4 L 13.6 17 L 13.6 18.4 L 10 17.4 " +
        "L 6.4 18.4 L 6.4 17 L 8.6 15.4 L 8.6 11.4 L 2 13.4 L 2 11.6 L 8.6 7.6 L 8.6 4 Q 8.6 1.5 10 1.5 Z",
)

private const val ROAD_TOP = 92f
private const val ROAD_BOTTOM = 140f
private val Road = outline("M 160 92 L 170 92 L 241 140 L 89 140 Z")
private val RoadEdges = outline("M 160 92 L 89 140 M 170 92 L 241 140")
private val Pointer = outline("M 165 111 L 175.5 131 L 165 126.5 L 154.5 131 Z")
private val NextArrow = outline("M 298.5 24.5 L 304 28 L 298.5 31.5 Z")

// The flight's arc: a quadratic curve between two dots.
private val ArcFrom = Offset(40f, 132f)
private val ArcTo = Offset(290f, 132f)
private val ArcControl = Offset(165f, 56f)
private val Arc = outline("M 40 132 Q 165 56 290 132")
private val ArcDashes = PathEffect.dashPathEffect(floatArrayOf(3f, 4f))

/** Cubic ease-in-out, for the marker's run. */
internal fun inOut(t: Float) = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

private fun mix(a: Float, b: Float, t: Float) = a + (b - a) * t

/** A blurred fill for the markers' halos. Mask filters only work on hardware canvases from API 28. */
internal class Halo(color: Color, sigma: Float) {
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color.toArgb()
        maskFilter = BlurMaskFilter(sigmaToRadius(sigma), BlurMaskFilter.Blur.NORMAL)
    }

    fun circle(scope: DrawScope, x: Float, y: Float, r: Float) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scope.drawIntoCanvas { it.nativeCanvas.drawCircle(x, y, r, paint) }
    }

    fun path(scope: DrawScope, shape: Path) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) scope.drawIntoCanvas { it.nativeCanvas.drawPath(shape.asAndroidPath(), paint) }
    }
}

/** The leg's icon, drawn in a 20 × 20 box. */
@Composable
internal fun TravelIcon(travel: Travel, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        scale(density, density, pivot = Offset.Zero) {
            when (travel) {
                Travel.Walk -> drawPath(Steps, Ink)
                Travel.Drive -> {
                    drawPath(Car, Ink, style = Stroke(1.4f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    drawPath(CarLights, Ink)
                }
                Travel.Fly -> drawPath(Plane, Ink)
            }
        }
    }
}

/**
 * Everything drawn on an open card, in its 330 × 204 box: the glow, the progress ring, the outline of
 * Next Step, the bar beside the stops, and the leg's own drawing. [along] is how far the marker is.
 */
internal fun DrawScope.drawFace(travel: Travel, glow: Color, accent: Color, soft: Halo, sharp: Halo, along: Float, seconds: Float, nextPress: Float) {
    withTransform({
        translate(165f, -14f)
        scale(240f, 138f, Offset.Zero)
    }) {
        drawCircle(
            Brush.radialGradient(0f to glow, 0.5f to glow.copy(alpha = 0.55f), 1f to glow.copy(alpha = 0f), center = Offset.Zero, radius = 1f),
            radius = 1f,
            center = Offset.Zero,
        )
    }

    // The ring fills clockwise from the top.
    drawCircle(Color.White.copy(alpha = 0.16f), 13f, Offset(165f, 28f), style = Stroke(1.5f))
    drawArc(accent, -90f, 360f * along, false, Offset(152f, 15f), Size(26f, 26f), style = Stroke(1.5f, cap = StrokeCap.Round))

    drawRoundRect(Color.White.copy(alpha = 0.14f * nextPress), Offset(242f, 16f), Size(70f, 24f), CornerRadius(12f))
    drawRoundRect(Color.White.copy(alpha = 0.35f), Offset(242f, 16f), Size(70f, 24f), CornerRadius(12f), style = Stroke(1f))
    drawPath(NextArrow, Ink)

    drawRoundRect(accent, Offset(18f, 155f), Size(2.5f, 31f), CornerRadius(1.25f))

    when (travel) {
        Travel.Walk -> drawTrack(glow, accent, soft, along)
        Travel.Drive -> drawRoad(accent, sharp, seconds)
        Travel.Fly -> drawArc(accent, soft, along)
    }
}

/** Walking: a thick track filled up to a glowing knob. */
private fun DrawScope.drawTrack(glow: Color, accent: Color, halo: Halo, along: Float) {
    val knob = 27f + 276f * along
    drawRoundRect(Color.White.copy(alpha = 0.1f), Offset(18f, 96f), Size(294f, 12f), CornerRadius(6f))
    drawRoundRect(
        Brush.horizontalGradient(listOf(glow, accent), startX = 18f, endX = knob),
        Offset(18f, 96f), Size(knob - 12f, 12f), CornerRadius(6f),
    )
    halo.circle(this, knob, 102f, 12f)
    drawCircle(Color(0xFFECE8FF), 9f, Offset(knob, 102f))
    drawCircle(Color.White.copy(alpha = 0.7f), 9f, Offset(knob, 102f), style = Stroke(1f))
    drawCircle(Color.White.copy(alpha = 0.6f), 4f, Offset(knob, 102f))
}

private fun roadHalf(y: Float) = 5f + 71f * (y - ROAD_TOP) / (ROAD_BOTTOM - ROAD_TOP)

/** Driving: a road in perspective whose centre dashes run towards the viewer, with a pointer swaying in its lane. */
private fun DrawScope.drawRoad(accent: Color, halo: Halo, seconds: Float) {
    drawPath(Road, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0f), Color.White.copy(alpha = 0.1f)), startY = ROAD_TOP, endY = ROAD_BOTTOM))
    drawPath(RoadEdges, Color.White.copy(alpha = 0.3f), style = Stroke(1f))

    val phase = (seconds * 1.3f) % 1f
    val dash = Path()
    for (k in 0 until 5) {
        // Depth is squared, so the dashes spread and speed up as they come closer.
        val near = (k + phase) / 5f
        val far = min(1f, near + 0.09f)
        val y0 = ROAD_TOP + (ROAD_BOTTOM - ROAD_TOP) * near * near
        val y1 = ROAD_TOP + (ROAD_BOTTOM - ROAD_TOP) * far * far
        dash.rewind()
        dash.moveTo(165f - roadHalf(y0) * 0.06f, y0)
        dash.lineTo(165f + roadHalf(y0) * 0.06f, y0)
        dash.lineTo(165f + roadHalf(y1) * 0.06f, y1)
        dash.lineTo(165f - roadHalf(y1) * 0.06f, y1)
        dash.close()
        drawPath(dash, Color.White.copy(alpha = 0.25f + 0.6f * near))
    }

    withTransform({ translate(3f * sin(seconds * 1.7f), 0f) }) {
        halo.path(this, Pointer)
        drawPath(Pointer, accent)
    }
}

/** Flying: an arc between two dots, drawn solid behind a glowing marker that travels along it. */
private fun DrawScope.drawArc(accent: Color, halo: Halo, along: Float) {
    // The first `along` of a quadratic curve is itself one: its control point slides along the first leg.
    val bend = Offset(mix(ArcFrom.x, ArcControl.x, along), mix(ArcFrom.y, ArcControl.y, along))
    val rest = Offset(mix(ArcControl.x, ArcTo.x, along), mix(ArcControl.y, ArcTo.y, along))
    val marker = Offset(mix(bend.x, rest.x, along), mix(bend.y, rest.y, along))

    drawPath(Arc, Color.White.copy(alpha = 0.25f), style = Stroke(1.2f, pathEffect = ArcDashes))
    val flown = Path().apply {
        moveTo(ArcFrom.x, ArcFrom.y)
        quadraticTo(bend.x, bend.y, marker.x, marker.y)
    }
    drawPath(flown, accent, style = Stroke(2f, cap = StrokeCap.Round))

    drawCircle(Color.White.copy(alpha = 0.9f), 3f, ArcFrom)
    drawCircle(Color.White.copy(alpha = 0.6f), 3f, ArcTo, style = Stroke(1.2f))
    halo.circle(this, marker.x, marker.y, 9f)
    drawCircle(Color.White, 4.5f, marker)
}
