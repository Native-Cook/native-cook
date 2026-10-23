package com.ngocdevv.nativecook.blocks.modals.pitstopreview

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
internal fun PitStopReviewTabPage(
    tab: PitStopReviewTab,
    scale: Float,
    saved: Set<String>,
    setSaved: (Set<String>) -> Unit,
    cards: List<Boolean>,
    setCards: (List<Boolean>) -> Unit,
    circuit: String,
    setCircuit: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val subtitle = when (tab) {
        PitStopReviewTab.Calendar -> "Keep a weekend on your list"
        PitStopReviewTab.Results -> "Turn Nine standings"
        PitStopReviewTab.Circuits -> "Explore the circuit guides"
        PitStopReviewTab.Customize -> "Choose which cards appear on Home"
        PitStopReviewTab.Home -> ""
    }
    Column(
        modifier
            .fillMaxSize()
            .background(PitStopReviewTheme.background)
            .padding(horizontal = (24f * scale).dp, vertical = (28f * scale).dp),
        verticalArrangement = Arrangement.spacedBy((18f * scale).dp),
    ) {
        Text(
            tab.name,
            color = PitStopReviewTheme.ink,
            fontSize = pitStopReviewSp(30f, scale),
            fontWeight = FontWeight.Bold,
            style = pitStopReviewTextStyle(),
        )
        Text(
            subtitle,
            color = PitStopReviewTheme.muted,
            fontSize = pitStopReviewSp(15f, scale),
            style = pitStopReviewTextStyle(),
        )
        when (tab) {
            PitStopReviewTab.Calendar -> listOf("Harbor Circuit", "Juniper Park").forEach { venue ->
                PitStopReviewToggleRow(
                    "$venue · Race weekend",
                    saved.contains(venue),
                ) { checked -> setSaved(if (checked) saved + venue else saved - venue) }
            }
            PitStopReviewTab.Results -> pitStopReviewRacers.forEachIndexed { index, racer ->
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = PitStopReviewTheme.muted)
                    Text(racer.first, Modifier.weight(1f).padding(start = 14.dp), color = PitStopReviewTheme.ink)
                    Text("${racer.second} pts", color = PitStopReviewTheme.muted)
                }
            }
            PitStopReviewTab.Circuits -> {
                listOf("Harbor", "Juniper", "Mesa").forEach { name ->
                    PitStopReviewCircuitRow(name, circuit == name) { setCircuit(name) }
                }
                Text("$circuit Circuit · A short track guide", color = PitStopReviewTheme.muted)
            }
            PitStopReviewTab.Customize -> {
                pitStopReviewCardLabels.forEachIndexed { index, label ->
                    PitStopReviewToggleRow(label, cards[index]) { checked ->
                        setCards(cards.toMutableList().also { values -> values[index] = checked })
                    }
                }
                TextButton(onClick = { setCards(listOf(true, true, true)) }) {
                    Text("Restore cards", color = PitStopReviewTheme.ink)
                }
            }
            PitStopReviewTab.Home -> Unit
        }
    }
}

@Composable
internal fun PitStopReviewDestinationContent(
    destination: PitStopReviewDestination,
    passSaved: Boolean,
    setPassSaved: (Boolean) -> Unit,
    displayName: String,
    setDisplayName: (String) -> Unit,
    saved: Set<String>,
    setSaved: (Set<String>) -> Unit,
    cards: List<Boolean>,
    setCards: (List<Boolean>) -> Unit,
    laps: Int,
    setLaps: (Int) -> Unit,
    reaction: String,
    reactionAction: () -> Unit,
    circuit: String,
    setCircuit: (String) -> Unit,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(520.dp)
            .verticalScroll(rememberScrollState())
            .padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        when (destination) {
            PitStopReviewDestination.Pass -> {
                Text("Club Pass", color = PitStopReviewTheme.ink, fontWeight = FontWeight.Bold)
                listOf("Weekend notes", "Personal home cards", "Saved race weekends").forEach { item ->
                    Text("•  $item", color = PitStopReviewTheme.muted)
                }
                PitStopReviewToggleRow(if (passSaved) "Pass saved" else "Save pass", passSaved, setPassSaved)
            }
            PitStopReviewDestination.Profile -> {
                BasicTextField(
                    displayName,
                    setDisplayName,
                    Modifier
                        .fillMaxWidth()
                        .height(52.dp)
                        .background(Color.White.copy(alpha = 0.08f), RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp)
                        .semantics { contentDescription = "Display name" },
                    textStyle = androidx.compose.ui.text.TextStyle(color = PitStopReviewTheme.ink),
                )
                Text("Your dashboard display name.", color = PitStopReviewTheme.muted)
            }
            PitStopReviewDestination.Calendar -> listOf("Harbor Circuit", "Juniper Park").forEach { venue ->
                PitStopReviewToggleRow("$venue · Race weekend", saved.contains(venue)) { checked ->
                    setSaved(if (checked) saved + venue else saved - venue)
                }
            }
            PitStopReviewDestination.Reflex -> {
                Box(Modifier.fillMaxWidth().height(300.dp), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(24.dp)) {
                        Text(reaction, color = PitStopReviewTheme.ink, fontWeight = FontWeight.Bold)
                        Button(onClick = reactionAction) {
                            Text(
                                when {
                                    reaction == "GO" -> "Tap now"
                                    reaction == "Get ready…" -> "Wait for GO"
                                    reaction.endsWith(" ms") -> "Try again"
                                    else -> "Start test"
                                },
                            )
                        }
                    }
                }
            }
            PitStopReviewDestination.Customize -> {
                pitStopReviewCardLabels.forEachIndexed { index, label ->
                    PitStopReviewToggleRow(label, cards[index]) { checked ->
                        setCards(cards.toMutableList().also { values -> values[index] = checked })
                    }
                }
                TextButton(onClick = { setCards(listOf(true, true, true)) }) { Text("Restore cards") }
            }
            PitStopReviewDestination.Laps -> {
                Text("Every lap counts", color = PitStopReviewTheme.ink, fontWeight = FontWeight.Bold)
                Text("This week · 18.4 km", color = PitStopReviewTheme.muted)
                Text("Today's logged laps: $laps", color = PitStopReviewTheme.muted)
                Button(onClick = { setLaps(laps + 1) }) { Text("Log a lap") }
            }
            PitStopReviewDestination.Pitcast -> {
                Text("The weekend brief · Alex Vale / Sam Ives", color = PitStopReviewTheme.ink, fontWeight = FontWeight.Bold)
                Text("A cleaner first lap — Leave room before the first turn.", color = PitStopReviewTheme.muted)
                Text("Watch the weather — Coastal cloud can change quickly.", color = PitStopReviewTheme.muted)
                Text("One thing to notice — How the field settles after the restart.", color = PitStopReviewTheme.muted)
            }
            PitStopReviewDestination.Results -> pitStopReviewRacers.forEachIndexed { index, racer ->
                Row(Modifier.fillMaxWidth().height(56.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", color = PitStopReviewTheme.muted)
                    Text(racer.first, Modifier.weight(1f).padding(start = 14.dp), color = PitStopReviewTheme.ink)
                    Text("${racer.second} pts", color = PitStopReviewTheme.muted)
                }
            }
            PitStopReviewDestination.Circuits -> {
                listOf("Harbor", "Juniper", "Mesa").forEach { name ->
                    PitStopReviewCircuitRow(name, circuit == name) { setCircuit(name) }
                }
                Text("$circuit Circuit · A short track guide", color = PitStopReviewTheme.muted)
            }
        }
    }
}

@Composable
private fun PitStopReviewToggleRow(
    label: String,
    active: Boolean,
    onChange: (Boolean) -> Unit,
) {
    Row(
        Modifier.fillMaxWidth().height(64.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, Modifier.weight(1f), color = PitStopReviewTheme.ink)
        Switch(checked = active, onCheckedChange = onChange)
    }
}

@Composable
private fun PitStopReviewCircuitRow(name: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(64.dp)
            .background(Color.White.copy(alpha = 0.06f), RoundedCornerShape(16.dp))
            .semantics {
                contentDescription = "$name circuit"
                this.selected = selected
                role = Role.Button
            }
            .clickable(onClick = onClick)
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        PitStopReviewCircuitOutline(name, selected, Modifier.requiredSize(72.dp, 44.dp))
        Text(name, Modifier.weight(1f).padding(start = 14.dp), color = PitStopReviewTheme.ink)
        if (selected) Icon(Icons.Default.CheckCircle, contentDescription = null, tint = PitStopReviewTheme.yellow)
    }
}

@Composable
private fun PitStopReviewCircuitOutline(name: String, selected: Boolean, modifier: Modifier) {
    Canvas(modifier) {
        val unit = size.width / 72f
        val path = Path()
        when (name) {
            "Juniper" -> {
                path.moveTo(8f * unit, 27.28f * unit)
                path.cubicTo(4f * unit, 12f * unit, 24f * unit, 4f * unit, 34f * unit, 7f * unit)
                path.cubicTo(54f * unit, 9f * unit, 69f * unit, 16f * unit, 64f * unit, 30f * unit)
                path.cubicTo(54f * unit, 43f * unit, 18f * unit, 40f * unit, 8f * unit, 27.28f * unit)
            }
            "Mesa" -> {
                path.moveTo(7f * unit, 32f * unit)
                path.lineTo(18f * unit, 8f * unit)
                path.lineTo(48f * unit, 8f * unit)
                path.lineTo(65f * unit, 22f * unit)
                path.lineTo(53f * unit, 39f * unit)
                path.lineTo(25f * unit, 34f * unit)
                path.close()
            }
            else -> {
                path.moveTo(6f * unit, 26f * unit)
                path.cubicTo(8f * unit, 10f * unit, 20f * unit, 5f * unit, 30f * unit, 7f * unit)
                path.cubicTo(47f * unit, 10f * unit, 62f * unit, 7f * unit, 66f * unit, 20f * unit)
                path.cubicTo(68f * unit, 35f * unit, 51f * unit, 42f * unit, 39f * unit, 39f * unit)
                path.cubicTo(22f * unit, 35f * unit, 11f * unit, 39f * unit, 6f * unit, 26f * unit)
            }
        }
        drawPath(
            path,
            if (selected) PitStopReviewTheme.yellow else Color.White.copy(alpha = 0.55f),
            style = Stroke(if (selected) 3f * unit else 2f * unit, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}

private val pitStopReviewCardLabels = listOf("Every lap counts", "Race day reflex test", "Custom home cards")
private val pitStopReviewRacers = listOf("Mira Chen" to 42, "Alex Vale" to 38, "Sam Ives" to 34, "Noah Reed" to 29)

internal fun pitStopReviewSheetTitle(destination: PitStopReviewDestination): String = when (destination) {
    PitStopReviewDestination.Pass -> "Club Pass"
    PitStopReviewDestination.Profile -> "Profile"
    PitStopReviewDestination.Calendar -> "Race weekends"
    PitStopReviewDestination.Reflex -> "Reaction test"
    PitStopReviewDestination.Customize -> "Home cards"
    PitStopReviewDestination.Laps -> "Every lap counts"
    PitStopReviewDestination.Pitcast -> "The weekend brief"
    PitStopReviewDestination.Results -> "Results"
    PitStopReviewDestination.Circuits -> "Circuits"
}
