package com.ngocdevv.nativecook.blocks.text.rotatingwordheadline

import android.graphics.Picture
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import com.ngocdevv.nativecook.shared.rgba
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/** Cadence palette: warm paper, soft ink, one accent per word. */
private val PAPER = listOf(Color(0xFFF8F5EF), Color(0xFFF3EEE6), Color(0xFFECE5DA))
private val INK = Color(0xFF1F1C18)
private val MUTE = Color(0xFF6D665C)
private val CADENCE_WORDS = listOf("week", "launch", "wedding", "sprint")
private val ACCENTS = listOf(Color(0xFFE0663A), Color(0xFF3A6FD8), Color(0xFFC64B7C), Color(0xFF22936A))

private class Task(val title: String, val meta: String, val who: String, val done: Boolean)
private class Plan(val title: String, val sub: String, val tasks: List<Task>)

/** The preview card under the hero follows the word: one small plan per word. */
private val PLANS = listOf(
    Plan(
        "This week", "Nov 24 – 30 · 9 items",
        listOf(
            Task("Groceries and meal prep", "Mon · 6:30 pm", "AL", true),
            Task("Dentist for Theo", "Wed · 9:15 am", "MK", false),
            Task("Send the rent transfer", "Fri · due", "AL", false),
        ),
    ),
    Plan(
        "App launch", "Ships Dec 12 · 18 days left",
        listOf(
            Task("Freeze the release build", "Dec 3 · Engineering", "JR", true),
            Task("Store screenshots, 6 sizes", "Dec 5 · Design", "SP", false),
            Task("Press note to 40 writers", "Dec 10 · Marketing", "NO", false),
        ),
    ),
    Plan(
        "Our wedding", "June 7 · 64 guests",
        listOf(
            Task("Book the florist", "Deposit \$480", "EM", true),
            Task("Final menu tasting", "Jan 18 · 2 pm", "DR", false),
            Task("Seating plan, draft 2", "12 tables", "EM", false),
        ),
    ),
    Plan(
        "Sprint 23", "2 weeks · 31 points",
        listOf(
            Task("Offline sync for notes", "8 pts · In review", "KT", true),
            Task("Search across calendars", "5 pts · In progress", "BW", false),
            Task("Fix the reminder drift", "3 pts · To do", "KT", false),
        ),
    ),
)

private const val TNUM = "tnum"

/** Warm and cool glows plus 2600 seeded grain dots, recorded once into a Picture (no randomness). */
private fun Modifier.paper(): Modifier = drawWithCache {
    val w = size.width
    val h = size.height
    val d = density
    val linear = Brush.verticalGradient(0f to PAPER[0], 0.55f to PAPER[1], 1f to PAPER[2])
    val glow = Brush.radialGradient(
        0f to rgba(240, 170, 120, 0.22f), 0.55f to rgba(236, 196, 160, 0.08f), 1f to Color(0x00FFFFFF),
        center = Offset(w * 0.92f, h * 0.08f), radius = w * 0.95f,
    )
    val cool = Brush.radialGradient(
        0f to rgba(130, 160, 220, 0.12f), 0.5f to rgba(150, 170, 210, 0.04f), 1f to Color(0x00FFFFFF),
        center = Offset(w * 0.05f, h * 0.78f), radius = w * 0.9f,
    )
    val grain = Picture()
    val c = grain.beginRecording(w.roundToInt().coerceAtLeast(1), h.roundToInt().coerceAtLeast(1))
    val dot = android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG).apply { color = rgba(60, 45, 30, 0.05f).toArgb() }
    for (i in 0 until 2600) {
        val x = (sin(i * 12.9898) * 43758.5453) % 1.0
        val y = (sin(i * 78.233 + 3.1) * 12543.871) % 1.0
        c.drawCircle((abs(x) * w).toFloat(), (abs(y) * h).toFloat(), 0.55f * d, dot)
    }
    grain.endRecording()
    onDrawBehind {
        drawRect(linear)
        drawRect(glow)
        drawRect(cool)
        drawIntoCanvas { it.nativeCanvas.drawPicture(grain) }
    }
}

@Composable
private fun Logo() {
    Box(
        Modifier
            .size(28.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(Brush.verticalGradient(0f to Color(0xFF2C2822), 0.6f to Color(0xFF1A1713), 1f to Color(0xFF0F0D0B))),
    ) {
        Canvas(Modifier.fillMaxSize()) {
            // A 14 pt ring with its right quarter open, turned −30°, and a coral dot.
            val stroke = 2.5.dp.toPx()
            val r = 7.dp.toPx() - stroke / 2f
            val c = Offset(size.width / 2f, size.height / 2f)
            rotate(-30f, c) {
                drawArc(
                    color = Color(0xFFF3EEE6),
                    startAngle = 45f,
                    sweepAngle = 270f,
                    useCenter = false,
                    topLeft = Offset(c.x - r, c.y - r),
                    size = androidx.compose.ui.geometry.Size(r * 2f, r * 2f),
                    style = Stroke(width = stroke, cap = StrokeCap.Butt),
                )
            }
            drawCircle(ACCENTS[0], radius = 2.dp.toPx(), center = Offset(20.dp.toPx(), 9.dp.toPx()))
        }
    }
}

@Composable
private fun PlanView(plan: Plan, k: Int, pos: MutableFloatState, current: Boolean) {
    val accent = ACCENTS[k]
    Column(
        Modifier
            .fillMaxSize()
            // Only the nearest word's plan is read: a discrete index, never the cross-fade's alpha.
            .then(if (current) Modifier else Modifier.clearAndSetSemantics {})
            .graphicsLayer {
                val dd = wordDistance(pos.floatValue, k)
                alpha = clamp01(1f - abs(dd) * 1.6f)
                translationY = dd * -10f * density
            }
            .padding(16.dp),
    ) {
        Row(Modifier.padding(bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.padding(end = 12.dp).size(10.dp, 30.dp).clip(RoundedCornerShape(5.dp)).background(accent))
            Column(Modifier.weight(1f)) {
                Text(plan.title, fontSize = 17.sp, fontWeight = FontWeight.Bold, color = INK, letterSpacing = (-0.2).sp)
                Text(plan.sub, fontSize = 13.sp, color = MUTE, modifier = Modifier.padding(top = 1.dp), style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = TNUM))
            }
            Text("Shared", fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = MUTE)
        }
        for (task in plan.tasks) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(50.dp)
                    .drawWithCache {
                        val hair = 1f
                        onDrawBehind { drawRect(rgba(60, 45, 30, 0.12f), size = androidx.compose.ui.geometry.Size(size.width, hair)) }
                    },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CheckCircle(task.done, accent)
                Column(Modifier.weight(1f)) {
                    Text(
                        task.title,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = if (task.done) MUTE else INK,
                        textDecoration = if (task.done) TextDecoration.LineThrough else null,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(task.meta, fontSize = 12.5.sp, color = MUTE, modifier = Modifier.padding(top = 1.dp), style = androidx.compose.ui.text.TextStyle(fontFeatureSettings = TNUM))
                }
                Box(
                    Modifier.padding(start = 10.dp).size(30.dp).clip(CircleShape).background(accent.copy(alpha = 0x22 / 255f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(task.who, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, color = accent)
                }
            }
        }
    }
}

@Composable
private fun CheckCircle(done: Boolean, accent: Color) {
    Canvas(Modifier.padding(end = 12.dp).size(22.dp)) {
        val c = Offset(size.width / 2f, size.height / 2f)
        if (done) {
            drawCircle(accent)
            // A 9 × 5 L (left and bottom borders, 2 pt), turned −45° and lifted 1 pt.
            val sw = 2.dp.toPx()
            val bw = 9.dp.toPx()
            val bh = 5.dp.toPx()
            rotate(-45f, c) {
                translate(0f, -1.dp.toPx()) {
                    val l = c.x - bw / 2f + sw / 2f
                    val t = c.y - bh / 2f
                    val b = c.y + bh / 2f - sw / 2f
                    drawLine(Color.White, Offset(l, t), Offset(l, b + sw / 2f), strokeWidth = sw)
                    drawLine(Color.White, Offset(l - sw / 2f, b), Offset(c.x + bw / 2f, b), strokeWidth = sw)
                }
            }
        } else {
            val sw = 1.5.dp.toPx()
            drawCircle(rgba(60, 45, 30, 0.3f), radius = size.width / 2f - sw / 2f, style = Stroke(sw))
        }
    }
}

/** Cadence's landing hero around the reusable headline. */
@Composable
fun CadenceHero(autoPlay: Boolean = true) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val pos = remember { mutableFloatStateOf(0f) }
    val bars = WindowInsets.systemBars.asPaddingValues()
    val hair = with(LocalDensity.current) { 1f.toDp() }

    BoxWithConstraints(Modifier.fillMaxSize().background(PAPER[1]).paper()) {
        val w = maxWidth
        val compact = maxHeight < 720.dp
        val side = 24.dp
        val fontSize = min(46f, (w.value * 0.112f).roundToInt().toFloat()).dp

        Column(
            Modifier
                .fillMaxSize()
                .padding(top = bars.calculateTopPadding() + 8.dp, bottom = bars.calculateBottomPadding() + 12.dp, start = side, end = side),
        ) {
            Row(Modifier.fillMaxWidth().height(44.dp), verticalAlignment = Alignment.CenterVertically) {
                Logo()
                Text("Cadence", modifier = Modifier.padding(start = 9.dp), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = INK, letterSpacing = (-0.3).sp)
                Spacer(Modifier.weight(1f))
                Text("Sign in", fontSize = 16.sp, fontWeight = FontWeight.SemiBold, color = INK)
            }

            Row(
                Modifier
                    .padding(top = if (compact) 18.dp else 30.dp)
                    .height(30.dp)
                    .clip(RoundedCornerShape(15.dp))
                    .background(rgba(255, 253, 249, 0.85f))
                    .border(hair, rgba(60, 45, 30, 0.16f), RoundedCornerShape(15.dp))
                    .padding(start = 4.dp, end = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    Modifier.padding(end = 8.dp).height(22.dp).clip(RoundedCornerShape(11.dp)).background(INK).padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("New", color = Color(0xFFF8F5EF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
                Text("Shared timelines for small teams", color = INK, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            }

            RotatingWordHeadline(
                words = CADENCE_WORDS,
                accents = ACCENTS,
                lead = "Plan your",
                tail = "calmly.",
                fontSize = fontSize,
                maxWidth = w - side * 2,
                ink = INK,
                modifier = Modifier.padding(top = if (compact) 14.dp else 22.dp),
                autoPlay = autoPlay,
                reducedMotion = reduced,
                progress = pos,
            )

            Text(
                "Calendars, checklists and timelines in one shared plan, so nobody has to ask what happens next.",
                modifier = Modifier.padding(top = if (compact) 8.dp else 14.dp).widthIn(max = 360.dp),
                fontSize = 17.sp,
                lineHeight = 24.sp,
                color = MUTE,
            )

            Row(Modifier.padding(top = if (compact) 16.dp else 24.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier
                        .height(52.dp)
                        .clip(RoundedCornerShape(26.dp))
                        .background(Brush.verticalGradient(0f to Color(0xFF2E2A24), 0.5f to Color(0xFF1F1C18), 1f to Color(0xFF151310)))
                        .drawWithCache {
                            val edge = rgba(255, 255, 255, 0.18f)
                            onDrawBehind { drawRect(edge, topLeft = Offset(1.dp.toPx(), 0f), size = androidx.compose.ui.geometry.Size(size.width - 2.dp.toPx(), 1.dp.toPx())) }
                        }
                        .semantics { role = Role.Button }
                        .padding(horizontal = 26.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Start free", color = Color(0xFFFAF7F1), fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
                Box(
                    Modifier
                        .height(52.dp)
                        .border(1.dp, rgba(31, 28, 24, 0.16f), RoundedCornerShape(26.dp))
                        .semantics { role = Role.Button }
                        .padding(horizontal = 20.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text("Take the tour", color = INK, fontSize = 17.sp, fontWeight = FontWeight.SemiBold)
                }
            }

            PlanCard(pos, hair, Modifier.padding(top = if (compact) 18.dp else 28.dp).weight(1f, fill = false))

            Text(
                "Free for up to 5 people · No card needed",
                modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                textAlign = TextAlign.Center,
                fontSize = 13.sp,
                color = MUTE,
            )
        }
    }
}

@Composable
private fun PlanCard(pos: MutableFloatState, hair: Dp, modifier: Modifier) {
    val shape = RoundedCornerShape(22.dp)
    // flex: 1 between 196 and 268: takes the room left, clamped.
    Box(
        modifier
            .heightIn(min = 196.dp, max = 268.dp)
            .fillMaxHeight()
            .fillMaxWidth()
            .cssBoxShadow(rgba(0x3a, 0x2a, 0x1a, 0.12f), 14.dp, 28.dp, 22.dp)
            .clip(shape)
            .background(Color(0xFFFFFDF9))
            .border(hair, rgba(60, 45, 30, 0.14f), shape),
    ) {
        Box(Modifier.padding(horizontal = 14.dp).fillMaxWidth().height(1.dp).background(rgba(255, 255, 255, 0.9f)))
        val current by remember(pos) { derivedStateOf { wrapIndex(Math.round(pos.floatValue), PLANS.size) } }
        PLANS.forEachIndexed { k, plan -> PlanView(plan, k, pos, k == current) }
    }
}
