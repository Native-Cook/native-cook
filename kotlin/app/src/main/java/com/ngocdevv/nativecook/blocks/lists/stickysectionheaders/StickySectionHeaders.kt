package com.ngocdevv.nativecook.blocks.lists.stickysectionheaders

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableFloatState
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.cssBoxShadow
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

private const val TOP_BAR_H = 44
private const val LIST_TOP_H = 212

/** November's spend so far, and seeded bar heights for the summary (no randomness). */
private val NOV_SPENT = LEDGER_SECTIONS.filter { it.month == 0 }.sumOf { daySpent(it) }
private val BARS = List(18) { i -> 0.18f + 0.82f * abs((sin(i * 1.71 + 0.4) * cos(i * 0.53)).toFloat()) }

@Composable
private fun Summary() {
    val hair = with(LocalDensity.current) { 1f.toDp() }
    val shape = RoundedCornerShape(20.dp)
    Column(Modifier.fillMaxWidth().height(LIST_TOP_H.dp).padding(top = 2.dp)) {
        Text(
            "Activity",
            modifier = Modifier.padding(start = SIDE.dp),
            style = TextStyle(fontSize = 34.sp, lineHeight = 41.sp, fontWeight = FontWeight.ExtraBold, color = INK, letterSpacing = (-0.6).sp),
        )
        Box(
            Modifier
                .padding(start = SIDE.dp, end = SIDE.dp, top = 12.dp)
                .fillMaxWidth()
                .height(136.dp)
                .cssBoxShadow(Color(0x1A2A2218), 10.dp, 22.dp, 20.dp)
                .clip(shape)
                .background(Brush.verticalGradient(0f to Color(0xFFFFFEFB), 0.6f to Color(0xFFFBFAF6), 1f to Color(0xFFF6F4EF)))
                .border(hair, HAIR, shape),
        ) {
            Box(Modifier.padding(horizontal = 16.dp).fillMaxWidth().height(1.dp).background(Color(1f, 1f, 1f, 0.95f)))
            Row(Modifier.fillMaxSize().padding(18.dp), verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text("Spent in November", fontSize = 13.sp, color = MUTE, fontWeight = FontWeight.Medium)
                    Text(
                        formatMoney(NOV_SPENT, false),
                        modifier = Modifier.padding(top = 4.dp),
                        style = TextStyle(fontSize = 32.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, color = INK, letterSpacing = (-0.6).sp, fontFeatureSettings = TNUM),
                    )
                    Text(
                        buildAnnotatedString {
                            withStyle(SpanStyle(color = INCOME, fontWeight = FontWeight.SemiBold)) { append("18% less") }
                            append(" than October")
                        },
                        modifier = Modifier.padding(top = 6.dp),
                        fontSize = 13.sp,
                        color = MUTE,
                    )
                }
                Row(Modifier.padding(start = 12.dp).height(56.dp), horizontalArrangement = Arrangement.spacedBy(3.dp), verticalAlignment = Alignment.Bottom) {
                    BARS.forEachIndexed { i, h ->
                        Box(
                            Modifier
                                .width(4.dp)
                                .height((8f + h * 46f).dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i == BARS.size - 1) INK else Color(28 / 255f, 27 / 255f, 25 / 255f, 0.16f)),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun Footer() {
    Column(
        Modifier.fillMaxSize().padding(top = 36.dp, end = (RAIL_W - SIDE).dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("That’s everything since June", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = INK)
        Text("Older activity lives in Statements.", fontSize = 13.sp, color = MUTE, modifier = Modifier.padding(top = 4.dp))
    }
}

@Composable
private fun TopBar(scrollY: MutableFloatState) {
    Row(Modifier.fillMaxWidth().height(TOP_BAR_H.dp).padding(horizontal = SIDE.dp), verticalAlignment = Alignment.CenterVertically) {
        Text("Accounts", modifier = Modifier.weight(1f), fontSize = 17.sp, color = INK, fontWeight = FontWeight.Medium)
        Text(
            "Activity",
            modifier = Modifier.graphicsLayer {
                val u = clamp01((scrollY.floatValue - 36f) / 28f)
                alpha = u
                translationY = (1f - u) * 6f * density
            },
            fontSize = 17.sp,
            fontWeight = FontWeight.SemiBold,
            color = INK,
        )
        Text("Export", modifier = Modifier.weight(1f), textAlign = TextAlign.End, fontSize = 17.sp, color = INK, fontWeight = FontWeight.Medium)
    }
}

/** Ledger's activity history: day sections whose headers stick, get pushed, and shrink into a pill with a running total. */
@Composable
fun StickySectionHeaders(autoPlay: Boolean = true) {
    BlockSystemBars(darkIcons = true)
    val reduced = rememberLiveReducedMotion()
    val bars = WindowInsets.systemBars.asPaddingValues()
    val scrollY = remember { mutableFloatStateOf(0f) }
    Column(Modifier.fillMaxSize().background(BG)) {
        Box(Modifier.fillMaxWidth().background(BG).padding(top = bars.calculateTopPadding())) { TopBar(scrollY) }
        StickySectionList(
            sections = LEDGER_SECTIONS,
            months = LEDGER_MONTHS,
            monthNames = LEDGER_MONTH_NAMES,
            listTopHeight = LIST_TOP_H.dp,
            modifier = Modifier.fillMaxWidth().weight(1f).fillMaxHeight(),
            autoPlay = autoPlay,
            reducedMotion = reduced,
            bottomInset = bars.calculateBottomPadding(),
            scrollY = scrollY,
            listTop = { Summary() },
            footer = { Footer() },
        )
    }
}
