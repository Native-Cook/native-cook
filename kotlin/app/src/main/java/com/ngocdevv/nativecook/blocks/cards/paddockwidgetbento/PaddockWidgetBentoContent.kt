package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val PaddockSurface = Color.White.copy(alpha = 0.08f)
private val PaddockSecondary = Color.White.copy(alpha = 0.62f)
private val PaddockNoFontPadding = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false))

@Composable
internal fun PaddockWidgetBentoSecondaryPage(
    scale: Float,
    state: PaddockWidgetBentoContentState,
    order: List<PaddockWidgetBentoCardID>,
    isEditing: Boolean,
    onMove: (PaddockWidgetBentoCardID, Int) -> Unit,
    onToggleEditing: () -> Unit,
    onRestoreOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp * scale, vertical = 28.dp * scale),
        verticalArrangement = Arrangement.spacedBy(18.dp * scale),
    ) {
        Text(PaddockWidgetBentoTitle(state.selectedTab), color = Color.White, fontSize = 30.sp * scale, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
        when (state.selectedTab) {
            PaddockWidgetBentoDestination.Calendar -> PaddockCalendarContent(scale, state)
            PaddockWidgetBentoDestination.Results -> PaddockResultsContent(scale)
            PaddockWidgetBentoDestination.Circuits -> PaddockCircuitsContent(scale, state)
            else -> PaddockCustomizeContent(scale, state, order, isEditing, onMove, onToggleEditing, onRestoreOrder)
        }
    }
}

@Composable
internal fun PaddockWidgetBentoDestinationContent(
    destination: PaddockWidgetBentoDestination,
    state: PaddockWidgetBentoContentState,
    order: List<PaddockWidgetBentoCardID>,
    isEditing: Boolean,
    onMove: (PaddockWidgetBentoCardID, Int) -> Unit,
    onToggleEditing: () -> Unit,
    onRestoreOrder: () -> Unit,
    onReaction: () -> Unit,
    modifier: Modifier = Modifier,
) {
    when (destination) {
        PaddockWidgetBentoDestination.Laps -> PaddockLapsContent(state, modifier)
        PaddockWidgetBentoDestination.Reflex -> PaddockReflexContent(state, onReaction, modifier)
        PaddockWidgetBentoDestination.Pitcast -> PaddockPitcastContent(modifier)
        PaddockWidgetBentoDestination.Stats -> PaddockStatsContent(state, modifier)
        PaddockWidgetBentoDestination.Glossary -> PaddockGlossaryContent(state, modifier)
        PaddockWidgetBentoDestination.Education -> PaddockLessonsContent(state, modifier)
        PaddockWidgetBentoDestination.WidgetStudio,
        PaddockWidgetBentoDestination.Banner -> PaddockWidgetStudioContent(state, modifier)
        PaddockWidgetBentoDestination.Guide -> PaddockGuideContent(modifier)
        PaddockWidgetBentoDestination.HomeCards,
        PaddockWidgetBentoDestination.Customize -> PaddockCustomizeContent(1f, state, order, isEditing, onMove, onToggleEditing, onRestoreOrder, modifier)
        else -> Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Local Turn Nine content", color = Color.White, style = PaddockNoFontPadding)
        }
    }
}

@Composable
private fun PaddockLapsContent(
    state: PaddockWidgetBentoContentState,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterVertically),
    ) {
        Text("Every lap counts", color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
        Text("This week: 18.4 km", color = Color.White, style = PaddockNoFontPadding)
        Text("Today's laps: ${state.lapCount}", color = Color.White, style = PaddockNoFontPadding)
        Button(onClick = { state.lapCount += 1 }) {
            Text("Log a lap", style = PaddockNoFontPadding)
        }
    }
}

@Composable
private fun PaddockReflexContent(
    state: PaddockWidgetBentoContentState,
    onReaction: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.fillMaxSize(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(22.dp, Alignment.CenterVertically),
    ) {
        Text(state.reactionText, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
        Button(onClick = onReaction) {
            Text(PaddockReactionButtonTitle(state.reactionText), style = PaddockNoFontPadding)
        }
    }
}

@Composable
private fun PaddockPitcastContent(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("The weekend brief · Alex Vale / Sam Ives", color = Color.White, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
        PaddockSectionText("A cleaner first lap — Leave room before the first turn.")
        PaddockSectionText("Watch the weather — Coastal cloud can change quickly.")
        PaddockSectionText("One thing to notice — How the field settles after the restart.")
    }
}

@Composable
private fun PaddockStatsContent(
    state: PaddockWidgetBentoContentState,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            PaddockSegment("Latest", state.latestStats, Modifier.weight(1f)) {
                state.latestStats = true
            }
            PaddockSegment("Season", !state.latestStats, Modifier.weight(1f)) {
                state.latestStats = false
            }
        }
        Text(if (state.latestStats) "Harbor · 8 laps · 42 points" else "Season · 64 laps · 286 points", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
    }
}

@Composable
private fun PaddockGlossaryContent(
    state: PaddockWidgetBentoContentState,
    modifier: Modifier,
) {
    val terms = listOf(
        "Apex" to "The innermost point of a corner's racing line.",
        "Braking point" to "The place where a driver begins slowing for a turn.",
        "Kerb" to "The raised painted edge beside the racing surface.",
        "Slipstream" to "Reduced air resistance behind another car.",
        "Sector" to "One timed section of a complete lap.",
        "Stint" to "A continuous run between pit stops.",
        "Undercut" to "Pitting earlier to gain time on fresher tyres.",
        "Warm-up lap" to "The preparation lap before the timed start.",
    )
    val matches = if (state.searchQuery.isEmpty()) terms else terms.filter { it.first.contains(state.searchQuery, ignoreCase = true) }
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        BasicTextField(
            value = state.searchQuery,
            onValueChange = { state.searchQuery = it },
            modifier = Modifier.semantics { contentDescription = "Search glossary" },
            textStyle = androidx.compose.ui.text.TextStyle(color = Color.White, fontSize = 16.sp, platformStyle = PlatformTextStyle(includeFontPadding = false)),
            singleLine = true,
            decorationBox = { field ->
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .background(PaddockSurface)
                        .padding(14.dp),
                ) {
                    if (state.searchQuery.isEmpty()) {
                        Text("Search glossary", color = PaddockSecondary, style = PaddockNoFontPadding)
                    }
                    field()
                }
            },
        )
        matches.forEach { term ->
            Column(Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                Text(term.first, color = Color.White, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
                Text(term.second, color = PaddockSecondary, style = PaddockNoFontPadding)
            }
        }
    }
}

@Composable
private fun PaddockLessonsContent(
    state: PaddockWidgetBentoContentState,
    modifier: Modifier,
) {
    val lessons = listOf(
        "Find the apex" to "Look through the corner and choose the inside point that leaves a clean exit.",
        "Leave room" to "Keep a predictable lane when another car is already alongside.",
        "Read the weather" to "Watch surface colour and wind before choosing a braking reference.",
    )
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
    ) {
        lessons.forEach { lesson ->
            val expanded = state.expandedLesson == lesson.first
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { state.expandedLesson = if (expanded) "" else lesson.first }
                    .padding(vertical = 16.dp),
            ) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(lesson.first, color = Color.White, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
                    Spacer(Modifier.weight(1f))
                    Icon(if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown, null, tint = Color.White)
                }
                if (expanded) {
                    Text(lesson.second, color = PaddockSecondary, modifier = Modifier.padding(top = 8.dp), style = PaddockNoFontPadding)
                }
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        }
    }
}

@Composable
private fun PaddockWidgetStudioContent(
    state: PaddockWidgetBentoContentState,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Pin to your dashboard", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
        Text("Choose a card for your Turn Nine dashboard.", color = PaddockSecondary, style = PaddockNoFontPadding)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(160.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color.White.copy(alpha = 0.10f)),
            contentAlignment = Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(paddockWidgetIcon(state.selectedWidget), null, modifier = Modifier.size(42.dp), tint = Color.White)
                Text(state.selectedWidget, color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
            }
        }
        listOf("Lap count", "Race weekend", "Circuit").forEach { option ->
            val selected = state.selectedWidget == option
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .clickable { state.selectedWidget = option },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(paddockWidgetIcon(option), null, tint = Color.White)
                Text(option, color = Color.White, modifier = Modifier.padding(start = 12.dp), style = PaddockNoFontPadding)
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                }
            }
        }
        val pinned = state.pinnedWidgets.contains(state.selectedWidget)
        Button(
            onClick = {
                if (!state.pinnedWidgets.contains(state.selectedWidget)) {
                    state.pinnedWidgets.add(state.selectedWidget)
                }
            },
            enabled = !pinned,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
                disabledContainerColor = Color.White.copy(alpha = 0.12f),
                disabledContentColor = Color.White.copy(alpha = 0.5f),
            ),
        ) {
            Text(if (pinned) "Pinned" else "Pin to dashboard", fontWeight = FontWeight.SemiBold, style = PaddockNoFontPadding)
        }
    }
}

@Composable
private fun PaddockGuideContent(modifier: Modifier) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        PaddockSectionText("1. Hold a quick card for a moment.")
        PaddockSectionText("2. Move it to a new slot.")
        PaddockSectionText("3. Release, then tap Done.")
    }
}

@Composable
private fun PaddockCalendarContent(
    scale: Float,
    state: PaddockWidgetBentoContentState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp * scale)) {
        listOf("Harbor Circuit", "Juniper Park").forEach { venue ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp * scale))
                    .background(PaddockSurface)
                    .padding(16.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("$venue · Race weekend", color = Color.White, modifier = Modifier.weight(1f), style = PaddockNoFontPadding)
                Switch(
                    checked = state.savedWeekends.contains(venue),
                    onCheckedChange = { saved ->
                        state.savedWeekends = if (saved) state.savedWeekends + venue else state.savedWeekends - venue
                    },
                    modifier = Modifier.semantics { contentDescription = "$venue Race weekend" },
                )
            }
        }
    }
}

@Composable
private fun PaddockResultsContent(scale: Float) {
    Column {
        listOf("Mira Chen" to 42, "Alex Vale" to 38, "Sam Ives" to 34, "Noah Reed" to 29).forEachIndexed { index, racer ->
            Row(
                modifier = Modifier.fillMaxWidth().height(58.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("${index + 1}", color = PaddockSecondary, modifier = Modifier.width(30.dp * scale), style = PaddockNoFontPadding)
                Text(racer.first, color = Color.White, style = PaddockNoFontPadding)
                Spacer(Modifier.weight(1f))
                Text("${racer.second} pts", color = PaddockSecondary, style = PaddockNoFontPadding)
            }
            HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
        }
    }
}

@Composable
private fun PaddockCircuitsContent(
    scale: Float,
    state: PaddockWidgetBentoContentState,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp * scale)) {
        listOf("Harbor", "Juniper", "Mesa").forEach { circuit ->
            val selected = state.selectedCircuit == circuit
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp * scale)
                    .clickable { state.selectedCircuit = circuit },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                PaddockCircuitOutline(circuit, selected, Modifier.width(80.dp).height(44.dp))
                Text(circuit, color = Color.White, modifier = Modifier.padding(start = 8.dp), style = PaddockNoFontPadding)
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Icon(Icons.Default.CheckCircle, null, tint = Color.White)
                }
            }
        }
        Text("${state.selectedCircuit} Circuit · Local venue guide", color = PaddockSecondary, style = PaddockNoFontPadding)
    }
}

@Composable
private fun PaddockCustomizeContent(
    scale: Float,
    state: PaddockWidgetBentoContentState,
    order: List<PaddockWidgetBentoCardID>,
    isEditing: Boolean,
    onMove: (PaddockWidgetBentoCardID, Int) -> Unit,
    onToggleEditing: () -> Unit,
    onRestoreOrder: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp * scale),
    ) {
        Text("Quick card order", color = Color.White, fontWeight = FontWeight.Bold, style = PaddockNoFontPadding)
        order.forEach { id ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp * scale))
                    .background(PaddockSurface)
                    .padding(horizontal = 14.dp * scale),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(id.cardName(), color = Color.White, modifier = Modifier.weight(1f), style = PaddockNoFontPadding)
                OutlinedButton(onClick = { onMove(id, -1) }, modifier = Modifier.height(44.dp)) {
                    Text("Up", style = PaddockNoFontPadding)
                }
                OutlinedButton(onClick = { onMove(id, 1) }, modifier = Modifier.height(44.dp).padding(start = 4.dp)) {
                    Text("Down", style = PaddockNoFontPadding)
                }
            }
        }
        Button(onClick = onToggleEditing, modifier = Modifier.height(44.dp)) {
            Text(if (isEditing) "Done" else "Arrange cards", style = PaddockNoFontPadding)
        }
        OutlinedButton(onClick = onRestoreOrder, modifier = Modifier.height(44.dp)) {
            Text("Restore order", style = PaddockNoFontPadding)
        }
        OutlinedButton(onClick = { state.guideVisible = true }, modifier = Modifier.height(44.dp)) {
            Text("Restore guide", style = PaddockNoFontPadding)
        }
    }
}

@Composable
private fun PaddockCircuitOutline(
    circuit: String,
    selected: Boolean,
    modifier: Modifier,
) {
    Canvas(modifier) {
        val pixelsPerPoint = density
        val path = Path()
        path.moveTo(4f * pixelsPerPoint, size.height * 0.6f)
        if (circuit == "Mesa") {
            path.lineTo(18f * pixelsPerPoint, 5f * pixelsPerPoint)
            path.lineTo(58f * pixelsPerPoint, 9f * pixelsPerPoint)
            path.lineTo(72f * pixelsPerPoint, 30f * pixelsPerPoint)
            path.lineTo(35f * pixelsPerPoint, 40f * pixelsPerPoint)
            path.close()
        } else {
            path.cubicTo(6f * pixelsPerPoint, 8f * pixelsPerPoint, 25f * pixelsPerPoint, 2f * pixelsPerPoint, 40f * pixelsPerPoint, 6f * pixelsPerPoint)
            path.cubicTo(60f * pixelsPerPoint, 8f * pixelsPerPoint, 77f * pixelsPerPoint, 12f * pixelsPerPoint, 74f * pixelsPerPoint, 28f * pixelsPerPoint)
            path.cubicTo(55f * pixelsPerPoint, 45f * pixelsPerPoint, 15f * pixelsPerPoint, 42f * pixelsPerPoint, 4f * pixelsPerPoint, size.height * 0.6f)
        }
        drawPath(
            path = path,
            color = if (selected) Color.Yellow else Color.White.copy(alpha = 0.55f),
            style = Stroke(width = (if (selected) 3f else 2f) * pixelsPerPoint, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

@Composable
private fun PaddockSegment(
    text: String,
    selected: Boolean,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) Color.White else PaddockSurface)
            .clickable(onClick = onClick)
            .padding(vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(text, color = if (selected) Color.Black else Color.White, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, style = PaddockNoFontPadding)
    }
}

@Composable
private fun PaddockSectionText(text: String) {
    Text(text, color = Color.White, style = PaddockNoFontPadding)
}

private fun PaddockWidgetBentoTitle(destination: PaddockWidgetBentoDestination): String {
    return destination.name.lowercase().replaceFirstChar { it.uppercase() }
}

private fun PaddockReactionButtonTitle(reaction: String): String {
    return when {
        reaction == "Ready" || reaction.startsWith("Too soon") -> "Start test"
        reaction.endsWith(" ms") -> "Try again"
        else -> "Tap when ready"
    }
}
