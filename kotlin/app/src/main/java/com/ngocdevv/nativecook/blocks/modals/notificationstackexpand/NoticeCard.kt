package com.ngocdevv.nativecook.blocks.modals.notificationstackexpand

// One notification's face: avatar, sender, time and two lines. Static; the stack moves it from outside.

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathOperation
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Fill
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rgba

internal val NSE_BG = listOf(Color(0xFFF4F5F7), Color(0xFFEDEFF2), Color(0xFFE6E9EE))
internal val NSE_CARD = Color(0xFFFCFCFD)
internal val NSE_INK = Color(0xFF1B1F24)
internal val NSE_MUTE = Color(0xFF69707A)
internal val NSE_ACCENT = Color(0xFF2D6F64)
internal val NSE_DANGER = Color(0xFFE2484D)
private val BODY = Color(0xFF3C434C)

private const val AVATAR = 38f

internal fun svg(d: String): Path = PathParser().parsePathString(d).toPath()

/** Draws in an SVG viewBox `vb` units wide, scaled to the canvas. */
internal inline fun DrawScope.inViewBox(vb: Float, block: DrawScope.() -> Unit) {
    scale(size.width / vb, size.width / vb, pivot = Offset.Zero) { block() }
}

internal fun DrawScope.strokePath(path: Path, color: Color, width: Float, join: StrokeJoin = StrokeJoin.Round) {
    drawPath(path, color, style = Stroke(width = width, cap = StrokeCap.Round, join = join))
}

/** A band `dy` dp tall along the inside top (dy > 0) or bottom (dy < 0) edge of a rounded box: RN's `inset 0 dy 0`. */
internal fun insetBand(size: Size, radius: Float, dy: Float): Path {
    val outer = Path().apply { addRoundRect(RoundRect(0f, 0f, size.width, size.height, CornerRadius(radius))) }
    val inner = Path().apply { addRoundRect(RoundRect(0f, dy, size.width, size.height + dy, CornerRadius(radius))) }
    return Path().apply { op(outer, inner, PathOperation.Difference) }
}

/** The card material: two soft shadows, the card colour and a 1 dp inner highlight on top. */
internal fun Modifier.nseCardSurface(): Modifier = this
    .cssBoxShadow(rgba(30, 38, 48, 0.08f), 1.dp, 2.dp, RADIUS.dp)
    .cssBoxShadow(rgba(30, 38, 48, 0.07f), 10.dp, 24.dp, RADIUS.dp)
    .drawWithCache {
        val r = RADIUS.dp.toPx()
        val band = insetBand(size, r, 1.dp.toPx())
        onDrawBehind {
            drawRoundRect(NSE_CARD, cornerRadius = CornerRadius(r))
            drawPath(band, Color.White.copy(alpha = 0.9f))
        }
    }

private val CAL_FRAME = svg("M5.7 3.5 H14.3 A3.2 3.2 0 0 1 17.5 6.7 V14.3 A3.2 3.2 0 0 1 14.3 17.5 H5.7 A3.2 3.2 0 0 1 2.5 14.3 V6.7 A3.2 3.2 0 0 1 5.7 3.5 Z")
private val CAL_LINES = svg("M2.8 8 H17.2 M6.5 1.8 V5 M13.5 1.8 V5")
private val FILE_PATH = svg("M5 2.5 H11.5 L15.5 6.5 V16 A1.6 1.6 0 0 1 13.9 17.6 H5 A1.6 1.6 0 0 1 3.4 16 V4.1 A1.6 1.6 0 0 1 5 2.5 Z M11.2 2.8 V6.8 H15.2")
private val HASH = svg("M3.2 0.8 L2.4 8.2 M6.6 0.8 L5.8 8.2 M0.9 3 H8.2 M0.6 6 H7.9")
private val PAGE = svg("M2 0.9 H5.4 L7.4 2.9 V8.1 H2 Z")

@Composable
private fun AppGlyph(app: NoticeApp) {
    Canvas(Modifier.size(20.dp)) {
        inViewBox(20f) {
            if (app == NoticeApp.Calendar) {
                strokePath(CAL_FRAME, Color.White, 1.7f)
                strokePath(CAL_LINES, Color.White, 1.7f)
                drawRoundRect(Color.White, topLeft = Offset(6f, 10.6f), size = Size(3f, 3f), cornerRadius = CornerRadius(0.8f))
            } else {
                strokePath(FILE_PATH, Color.White, 1.6f)
            }
        }
    }
}

@Composable
private fun Badge(kind: NoticeBadge, modifier: Modifier) {
    Box(
        modifier
            .size(17.dp)
            .background(if (kind == NoticeBadge.File) Color(0xFF5C8FD6) else NSE_ACCENT, CircleShape)
            .border(2.dp, NSE_CARD, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(Modifier.size(9.dp)) {
            inViewBox(9f) {
                if (kind == NoticeBadge.Channel) strokePath(HASH, Color.White, 1.25f)
                else drawPath(PAGE, Color.White, style = Fill)
            }
        }
    }
}

private fun initials(name: String): String {
    val parts = name.split(" ")
    return (parts[0].take(1) + (parts.getOrNull(1)?.take(1) ?: "")).uppercase()
}

@Composable
internal fun NseAvatar(notice: Notice, modifier: Modifier = Modifier) {
    val radius: Dp = if (notice.app != null) 10.dp else (AVATAR / 2).dp
    val shape = RoundedCornerShape(radius)
    Box(modifier.size(AVATAR.dp)) {
        Box(
            Modifier
                .size(AVATAR.dp)
                .clip(shape)
                .background(Brush.linearGradient(0f to notice.tint[0], 0.55f to notice.tint[1], 1f to notice.tint[2]))
                .drawWithCache {
                    val r = radius.toPx()
                    val top = insetBand(size, r, 1.dp.toPx())
                    val bottom = insetBand(size, r, -1.dp.toPx())
                    onDrawWithContent {
                        drawContent()
                        drawPath(top, Color.White.copy(alpha = 0.35f))
                        drawPath(bottom, Color.Black.copy(alpha = 0.08f))
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            if (notice.app != null) {
                AppGlyph(notice.app)
            } else {
                Text(
                    initials(notice.from),
                    style = TextStyle(color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, letterSpacing = 0.2.sp),
                )
            }
        }
        if (notice.badge != null) Badge(notice.badge, Modifier.offset((AVATAR - 17f + 3f).dp, (AVATAR - 17f + 3f).dp))
    }
}

/** The card's content only. The caller gives it a background, a shadow and a transform. */
@Composable
internal fun NoticeFace(notice: Notice, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxSize().padding(start = 14.dp, end = 14.dp, top = 13.dp), verticalAlignment = Alignment.Top) {
        NseAvatar(notice, Modifier.padding(top = 2.dp))
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Row(Modifier.fillMaxWidth().height(20.dp)) {
                Text(
                    notice.from,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.SemiBold, color = NSE_INK),
                )
                Text(
                    notice.time,
                    modifier = Modifier.padding(start = 8.dp).alignByBaseline(),
                    style = TextStyle(fontSize = 13.sp, color = NSE_MUTE, fontFeatureSettings = "tnum"),
                )
            }
            Text(
                notice.body,
                modifier = Modifier.padding(top = 3.dp),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(fontSize = 14.sp, lineHeight = 18.sp, color = BODY),
            )
        }
    }
}

/** A plain, still notification card for the rest of the feed. */
@Composable
internal fun NoticeCard(notice: Notice, modifier: Modifier = Modifier) {
    Box(
        modifier
            .fillMaxWidth()
            .height(CARD_H.dp)
            .nseCardSurface()
            .semantics { contentDescription = "${notice.from}, ${notice.time}. ${notice.body}" },
    ) {
        NoticeFace(notice)
    }
}
