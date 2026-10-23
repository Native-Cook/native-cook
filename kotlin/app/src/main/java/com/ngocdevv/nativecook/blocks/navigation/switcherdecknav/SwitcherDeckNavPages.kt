package com.ngocdevv.nativecook.blocks.navigation.switcherdecknav

import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Typeface
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asComposePath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.layout
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.PathParser
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import kotlin.math.roundToInt

// The four pages of "Slow Departures", a photo journal of trains and window seats: the journal
// feed, one entry, the route drawn as a map and the writer's profile. They are still pictures of
// an app, laid out at the full size of the screen; the switcher shrinks and turns them.

/**
 * @param tint Background of the page, also used for the switcher's chip icon.
 */
data class SwitcherDeckPage(val title: String, val tint: Color, val ink: Color)

internal val SwitcherDeckPages = listOf(
    SwitcherDeckPage("Journal", Color(0xFFF2EDE4), Color(0xFF1D1A16)),
    SwitcherDeckPage("Window seat", Color(0xFF12110F), Color(0xFFF1ECE3)),
    SwitcherDeckPage("Route map", Color(0xFFDFE6E4), Color(0xFF24302F)),
    SwitcherDeckPage("Profile", Color(0xFF17212B), Color(0xFFEFE7DA)),
)

/** The page with index [index] (0 journal, 1 entry, 2 map, 3 profile), laid out at the screen's size. */
@Composable
internal fun SwitcherDeckPageContent(index: Int, width: Float, height: Float, top: Float, bottom: Float) {
    when (index) {
        0 -> JournalPage(width, top, bottom)
        1 -> EntryPage(width, height, top, bottom)
        2 -> MapPage(width, top, bottom)
        else -> ProfilePage(width, top, bottom)
    }
}

// MARK: Type

private val Sans = FontFamily.SansSerif
private val Serif = FontFamily.Serif

private fun sans(size: Float, weight: FontWeight, color: Color, spacing: Float = 0f) =
    TextStyle(fontFamily = Sans, fontSize = size.sp, fontWeight = weight, color = color, letterSpacing = spacing.sp)

private fun serif(size: Float, color: Color, bold: Boolean = false, italic: Boolean = false, lineHeight: TextUnit = TextUnit.Unspecified, spacing: Float = 0f) =
    TextStyle(
        fontFamily = Serif,
        fontSize = size.sp,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        fontStyle = if (italic) FontStyle.Italic else FontStyle.Normal,
        color = color,
        lineHeight = lineHeight,
        letterSpacing = spacing.sp,
    )

private val Rust = Color(0xFFA0502F)
private val Muted = Color(0xFF8A8073)

@Composable
private fun Kicker(text: String, color: Color, modifier: Modifier = Modifier) =
    BasicText(text, modifier, style = sans(11f, FontWeight.Bold, color, 1.6f))

@Composable
private fun Meta(text: String, color: Color, modifier: Modifier = Modifier) =
    BasicText(text, modifier, style = sans(11f, FontWeight.Bold, color, 1.4f))

@Composable
private fun StatLabel(text: String, color: Color = Muted) =
    BasicText(text.uppercase(), style = sans(11f, FontWeight.SemiBold, color, 0.6f))

@Composable
private fun StatValue(text: String, color: Color) =
    BasicText(text, style = sans(17f, FontWeight.SemiBold, color).copy(fontFeatureSettings = "tnum"))

@Composable
private fun TileTitle(text: String, color: Color, size: Float = 17f) = BasicText(text, style = serif(size, color, bold = true))

@Composable
private fun TileMeta(text: String, color: Color = Muted) = BasicText(text, style = sans(12f, FontWeight.Medium, color))

/** A hairline above and below. */
private fun Modifier.hairlines(color: Color) = drawBehind {
    val hair = 1f
    drawRect(color, size = androidx.compose.ui.geometry.Size(size.width, hair))
    drawRect(color, topLeft = androidx.compose.ui.geometry.Offset(0f, size.height - hair), size = androidx.compose.ui.geometry.Size(size.width, hair))
}

/** Lays the content out [by] higher than where its parent puts it, like a negative top margin. */
private fun Modifier.pullUp(by: Dp) = layout { measurable, constraints ->
    val placeable = measurable.measure(constraints)
    val shift = by.roundToPx()
    layout(placeable.width, (placeable.height - shift).coerceAtLeast(0)) { placeable.place(0, -shift) }
}

// MARK: Photos

private fun photoUrl(id: String, width: Float) =
    "https://images.unsplash.com/$id?auto=format&fit=crop&w=${(width * 3f).roundToInt()}&q=80"

// Train station photos and airplane window photos, each used once.
private const val S_HERO = "photo-1553184257-604db3e574a8"
private const val S_TALL = "photo-1532105956626-9569c03602f6"
private const val S_DUSK = "photo-1693572322416-9ea4b36482ce"
private const val S_STOP = "photo-1527839321757-ad3a2f2be351"
private const val S_ARCH = "photo-1495313196544-7d1adf4e628f"
private const val S_HALL = "photo-1604212399401-5fd807a65e18"
private const val S_RAILS = "photo-1521075821520-ae40128c1ea0"
private const val W_HERO = "photo-1543797414-a0c3ad076f7c"
private const val W_A = "photo-1488085061387-422e29b40080"
private const val W_B = "photo-1545132147-d037e6c54cfd"
private const val W_C = "photo-1603277103691-756354934c2d"
private const val W_D = "photo-1507812984078-917a274065be"
private const val W_E = "photo-1591536813111-b6dccb6f67e0"
private const val W_F = "photo-1542640662-fd5f54cc23b8"

/** An Unsplash photo cropped to fill [width] × [height], fading in over 250 ms once it arrives. */
@Composable
private fun Photo(id: String, width: Float, height: Float, radius: Float = 0f, placeholder: Color = Color.Transparent) {
    val context = LocalContext.current
    val request = remember(id, width, context) { ImageRequest.Builder(context).data(photoUrl(id, width)).crossfade(250).build() }
    Box(
        Modifier
            .size(width.dp, height.dp)
            .clip(RoundedCornerShape(radius.dp))
            .background(placeholder),
    ) {
        AsyncImage(model = request, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
    }
}

// MARK: Shared pieces

/** The small pill at the bottom of every page that the switcher is pulled up from. */
@Composable
private fun Handle(bottom: Float, ink: Color) {
    Box(Modifier.fillMaxSize().padding(bottom = (bottom + HANDLE_BOTTOM).dp), contentAlignment = Alignment.BottomCenter) {
        Box(Modifier.size(54.dp, HANDLE_HEIGHT.dp).background(ink.copy(alpha = 0.5f), RoundedCornerShape(2.5.dp)))
    }
}

// The handle pill sits HANDLE_BOTTOM dp above the bottom safe area; the demo's virtual finger pulls its centre.
internal const val HANDLE_BOTTOM = 12f
internal const val HANDLE_HEIGHT = 5f

private fun svgPath(data: String) = PathParser.createPathFromPathData(data).asComposePath()

// MARK: Journal

@Composable
private fun JournalPage(width: Float, top: Float, bottom: Float) {
    val ink = Color(0xFF1D1A16)
    val paper = Color(0xFFF2EDE4)
    val inner = width - 40f
    val half = (inner - 12f) / 2f
    Box(Modifier.fillMaxSize().background(paper)) {
        Column(
            Modifier
                .padding(top = (top + 10f).dp, start = 20.dp, end = 20.dp)
                .wrapContentHeight(Alignment.Top, unbounded = true),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Kicker("WEEK 41 · THE AUTUMN LINE", Rust)
                Spacer(Modifier.weight(1f))
                Box(Modifier.size(34.dp).background(ink, CircleShape), contentAlignment = Alignment.Center) {
                    BasicText("IM", style = serif(13f, paper, bold = true))
                }
            }
            BasicText("Slow Departures", Modifier.padding(top = 6.dp), style = serif(40f, ink, bold = true, spacing = -0.8f))
            BasicText("Notes from platforms and window seats", Modifier.padding(top = 2.dp), style = serif(16f, Color(0xFF6F665B), italic = true))
            Box(Modifier.padding(top = 20.dp)) { Photo(S_HERO, inner, inner * 0.56f, 18f, Color(0xFFDDD5C8)) }
            Meta("06:12 · LISBOURNE CENTRAL", Rust, Modifier.padding(top = 14.dp))
            BasicText(
                "The first train hums before the light does",
                Modifier.padding(top = 4.dp),
                style = serif(22f, ink, bold = true, lineHeight = 27.sp, spacing = -0.2f),
            )
            BasicText(
                "Steam off the coffee cart, a conductor counting under his breath, and the departures board flipping to green one line at a time.",
                Modifier.padding(top = 6.dp),
                style = serif(15f, Color(0xFF6F665B), lineHeight = 21.sp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Row(Modifier.padding(top = 22.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                JournalTile(S_TALL, "Calder Bay", "8 Oct · 4 photos", half, ink)
                JournalTile(S_DUSK, "Platform 9, dusk", "6 Oct · 7 photos", half, ink)
            }
        }
        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(150.dp)
                .background(Brush.verticalGradient(listOf(paper.copy(alpha = 0f), paper))),
        )
        Handle(bottom, ink)
    }
}

@Composable
private fun JournalTile(id: String, title: String, info: String, side: Float, ink: Color) {
    Column(Modifier.width(side.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Photo(id, side, side, 14f, Color(0xFF3A3631))
        TileTitle(title, ink)
        TileMeta(info)
    }
}

// MARK: Entry

@Composable
private fun EntryPage(width: Float, height: Float, top: Float, bottom: Float) {
    val ink = Color(0xFFF1ECE3)
    val base = Color(0xFF12110F)
    val heroH = height * 0.54f
    val thumb = (width - 40f - 16f) / 3f
    Box(Modifier.fillMaxSize().background(base)) {
        Column(Modifier.wrapContentHeight(Alignment.Top, unbounded = true)) {
            Box(Modifier.size(width.dp, heroH.dp)) {
                Photo(W_HERO, width, heroH)
                Box(
                    Modifier.fillMaxSize().background(
                        Brush.verticalGradient(
                            0f to base.copy(alpha = 0.55f),
                            0.22f to base.copy(alpha = 0f),
                            0.55f to base.copy(alpha = 0f),
                            1f to base,
                        ),
                    ),
                )
            }
            Column(
                Modifier.pullUp(118.dp).padding(horizontal = 20.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Meta("ENTRY 17 · 9 OCTOBER", Color(0xFFE3A27D))
                BasicText("Window seat, over the Vessmark coast", style = serif(31f, ink, bold = true, lineHeight = 36.sp, spacing = -0.4f))
            }
            Row(
                Modifier
                    .padding(start = 20.dp, end = 20.dp, top = 20.dp)
                    .fillMaxWidth()
                    .hairlines(ink.copy(alpha = 0.22f))
                    .padding(vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((label, value) in listOf("Altitude" to "11,280 m", "Local time" to "07:40", "Flight" to "2 h 15")) {
                    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                        StatLabel(label)
                        StatValue(value, ink)
                    }
                }
            }
            BasicText(
                "The wing caught the sun before the cabin did. Below, the fjords were still ink, and someone three rows back had already fallen asleep against the glass.",
                Modifier.padding(start = 20.dp, end = 20.dp, top = 18.dp),
                style = serif(16f, Color(0xFFBDB5A8), lineHeight = 24.sp),
            )
            Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for (id in listOf(W_A, W_B, W_C)) Photo(id, thumb, thumb, 14f, Color(0xFF3A3631))
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(start = 20.dp, end = 20.dp, top = (top + 6f).dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val back = remember { svgPath("M9 2 L3 9 L9 16") }
            Box(
                Modifier
                    .size(40.dp)
                    .background(base.copy(alpha = 0.45f), CircleShape)
                    .border(with(LocalDensity.current) { 1f.toDp() }, ink.copy(alpha = 0.35f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Canvas(Modifier.size(12.dp, 18.dp)) {
                    scale(density, density, pivot = androidx.compose.ui.geometry.Offset.Zero) {
                        drawPath(back, ink, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                    }
                }
            }
            Spacer(Modifier.weight(1f))
            Box(
                Modifier.height(28.dp).background(ink.copy(alpha = 0.9f), RoundedCornerShape(14.dp)).padding(horizontal = 12.dp),
                contentAlignment = Alignment.Center,
            ) {
                BasicText("SEAT 34A", style = sans(11f, FontWeight.Bold, base, 1.2f))
            }
        }
        Handle(bottom, ink)
    }
}

// MARK: Map

// The drawn map: coastlines, the rail line, a flight arc and the stops, in a 400 × 520 box.
private val Land = listOf(
    "M -10 40 C 60 30 120 70 150 120 C 175 165 140 210 170 250 C 200 290 260 270 300 300 C 340 330 330 400 380 430 L 410 430 L 410 -10 L -10 -10 Z",
    "M -10 360 C 40 340 80 380 90 420 C 100 460 70 500 120 530 L -10 530 Z",
    "M 220 470 C 250 440 300 450 320 480 C 335 505 300 530 260 530 L 215 530 C 205 510 205 490 220 470 Z",
)
private const val RAIL = "M 48 96 C 90 120 120 160 140 210 C 158 255 200 280 250 300 C 300 320 320 360 340 400"
private const val FLIGHT = "M 340 400 C 300 470 160 470 70 440"
private const val PLANE = "M -9 0 L 9 0 M 2 -7 L 5 0 L 2 7 M -7 -3 L -8 0 L -7 3"

private class MapStop(val x: Float, val y: Float, val name: String, val right: Boolean)

private val Stops = listOf(
    MapStop(48f, 96f, "Lisbourne", true),
    MapStop(140f, 210f, "Hallow Junction", true),
    MapStop(250f, 300f, "Vessmark", false),
    MapStop(340f, 400f, "Orrin Fjord", false),
    MapStop(70f, 440f, "Calder Bay", true),
)

private class MapPaints {
    val land = Land.map { PathParser.createPathFromPathData(it) }
    val rail = PathParser.createPathFromPathData(RAIL)
    val flight = PathParser.createPathFromPathData(FLIGHT)
    val plane = PathParser.createPathFromPathData(PLANE)
    val fill = Paint(Paint.ANTI_ALIAS_FLAG)
    val stroke = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    val railDash = DashPathEffect(floatArrayOf(7f, 7f), 0f)
    val flightDash = DashPathEffect(floatArrayOf(2f, 6f), 0f)
    val text = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) Typeface.create(Typeface.SANS_SERIF, 600, false) else Typeface.DEFAULT_BOLD
    }
    val small = Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface = Typeface.SANS_SERIF; textSize = 11f }
}

@Composable
private fun MapPage(width: Float, top: Float, bottom: Float) {
    val ink = Color(0xFF24302F)
    val paints = remember { MapPaints() }
    Box(Modifier.fillMaxSize().background(Color(0xFFDFE6E4))) {
        Column(Modifier.padding(top = (top + 10f).dp).wrapContentHeight(Alignment.Top, unbounded = true)) {
            Column(Modifier.padding(horizontal = 20.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Kicker("ROUTE · THE AUTUMN LINE", Color(0xFF3F6F69))
                BasicText("2,140 km, six stops", Modifier.padding(top = 6.dp), style = serif(32f, ink, bold = true, spacing = -0.8f))
            }
            Canvas(Modifier.padding(top = 6.dp).size(width.dp, (width * 1.3f).dp)) {
                drawIntoCanvas { drawMap(it.nativeCanvas, size.width / 400f, paints, ink.toArgb()) }
            }
        }
        Row(
            Modifier
                .align(Alignment.BottomCenter)
                .padding(start = 16.dp, end = 16.dp, bottom = (bottom + 36f).dp)
                .fillMaxWidth()
                .drawBehind {
                    // A soft shadow under the card: 0 10 30 at 16%.
                    drawIntoCanvas {
                        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                            color = android.graphics.Color.TRANSPARENT
                            setShadowLayer(26f * density, 0f, 10f * density, Color(0x2924302F).toArgb())
                        }
                        it.nativeCanvas.drawRoundRect(0f, 0f, size.width, size.height, 22f * density, 22f * density, paint.apply { color = 0xFFFBF8F1.toInt() })
                    }
                }
                .background(Color(0xFFFBF8F1), RoundedCornerShape(22.dp))
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Photo(S_STOP, 64f, 64f, 14f)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                StatLabel("Next stop")
                TileTitle("Vessmark Central", ink, 18f)
                TileMeta("14:05 · Platform 3")
            }
            StatValue("38 min", Color(0xFFB4562F))
        }
        Handle(bottom, ink)
    }
}

private fun drawMap(canvas: android.graphics.Canvas, k: Float, p: MapPaints, ink: Int) {
    canvas.save()
    canvas.scale(k, k)
    val stroke = p.stroke
    stroke.pathEffect = null
    stroke.strokeCap = Paint.Cap.BUTT
    stroke.strokeJoin = Paint.Join.MITER
    stroke.color = 0xFFC9D3D0.toInt()
    stroke.strokeWidth = 0.7f
    for (i in 0 until 9) canvas.drawLine(i * 50f, 0f, i * 50f, 520f, stroke)
    for (i in 0 until 11) canvas.drawLine(0f, i * 50f, 400f, i * 50f, stroke)
    p.fill.color = 0xFFEFE9DC.toInt()
    stroke.color = 0xFFB9B29F.toInt()
    stroke.strokeWidth = 1f
    for (land in p.land) {
        canvas.drawPath(land, p.fill)
        canvas.drawPath(land, stroke)
    }
    val rust = 0xFFB4562F.toInt()
    stroke.color = ink
    stroke.strokeWidth = 5f
    stroke.strokeCap = Paint.Cap.ROUND
    canvas.drawPath(p.rail, stroke)
    stroke.color = 0xFFEFE9DC.toInt()
    stroke.strokeWidth = 2f
    stroke.strokeCap = Paint.Cap.BUTT
    stroke.pathEffect = p.railDash
    canvas.drawPath(p.rail, stroke)
    stroke.color = rust
    stroke.strokeCap = Paint.Cap.ROUND
    stroke.pathEffect = p.flightDash
    canvas.drawPath(p.flight, stroke)
    stroke.pathEffect = null
    stroke.strokeJoin = Paint.Join.ROUND
    canvas.save()
    canvas.translate(205f, 456f)
    canvas.rotate(-168f)
    canvas.drawPath(p.plane, stroke)
    canvas.restore()
    p.text.color = ink
    p.text.textSize = 14f
    for ((i, stop) in Stops.withIndex()) {
        val r = if (i == 2) 9f else 6.5f
        p.fill.color = if (i == 2) rust else 0xFFFBF8F1.toInt()
        canvas.drawCircle(stop.x, stop.y, r, p.fill)
        stroke.color = ink
        stroke.strokeWidth = 2.2f
        canvas.drawCircle(stop.x, stop.y, r, stroke)
        val w = p.text.measureText(stop.name)
        canvas.drawText(stop.name, if (stop.right) stop.x + 14f else stop.x - 14f - w, stop.y + 5f, p.text)
    }
    p.fill.color = ink
    canvas.drawRect(300f, 28f, 370f, 29.5f, p.fill)
    p.small.color = ink
    canvas.drawText("100 km", 335f - p.small.measureText("100 km") / 2f, 20f, p.small)
    canvas.restore()
}

// MARK: Profile

@Composable
private fun ProfilePage(width: Float, top: Float, bottom: Float) {
    val ink = Color(0xFFEFE7DA)
    val soft = Color(0xFF8FA0AD)
    val cell = (width - 40f - 8f) / 3f
    val grid = listOf(S_ARCH, W_D, S_HALL, W_E, S_RAILS, W_F)
    Box(Modifier.fillMaxSize().background(Color(0xFF17212B))) {
        Column(
            Modifier
                .padding(top = (top + 18f).dp, start = 20.dp, end = 20.dp)
                .wrapContentHeight(Alignment.Top, unbounded = true),
        ) {
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(76.dp).background(Color(0xFFE3A27D), CircleShape), contentAlignment = Alignment.Center) {
                    BasicText("IM", style = serif(28f, Color(0xFF17212B), bold = true))
                }
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("Ines Marlow", style = serif(26f, ink, bold = true, lineHeight = 30.sp, spacing = -0.4f))
                    TileMeta("Collecting quiet platforms since 2019", soft)
                }
            }
            Row(
                Modifier
                    .padding(top = 26.dp)
                    .fillMaxWidth()
                    .hairlines(ink.copy(alpha = 0.2f))
                    .padding(vertical = 16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                for ((value, label) in listOf("41" to "trips", "1.3k" to "photos", "18.2k" to "km by rail")) {
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        BasicText(value, style = serif(28f, ink, bold = true).copy(fontFeatureSettings = "tnum"))
                        StatLabel(label, soft)
                    }
                }
            }
            Kicker("SAVED FRAMES", Color(0xFFE3A27D), Modifier.padding(top = 26.dp, bottom = 12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                for (row in 0 until 2) {
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        for (column in 0 until 3) Photo(grid[row * 3 + column], cell, cell * 1.18f, 6f, Color(0xFF24303B))
                    }
                }
            }
            Column(
                Modifier
                    .padding(top = 16.dp)
                    .fillMaxWidth()
                    .background(ink.copy(alpha = 0.07f), RoundedCornerShape(18.dp))
                    .border(with(LocalDensity.current) { 1f.toDp() }, ink.copy(alpha = 0.16f), RoundedCornerShape(18.dp))
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                TileTitle("Next trip", ink, 16f)
                TileMeta("Orrin Fjord by the night train · 3 Nov", soft)
            }
        }
        Handle(bottom, ink)
    }
}
