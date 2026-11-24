package com.ngocdevv.nativecook.blocks.lists.springreorderchecklist

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars

private val PAGE = Color(0xFFF5F2EA)
private val INK = Color(0xFF26302B)
private val MUTE = Color(0xFF7B8078)
private val GREEN = Color(0xFF2F7D5B)
private val TRACK = Color(38, 48, 43).copy(alpha = 0.12f)
private const val SIDE = 20

/** The packing list of Stowe, an invented travel app. */
internal val STOWE_ITEMS = listOf(
    ChecklistItem("passport", "Passport", "Front pocket", packed = true),
    ChecklistItem("jacket", "Rain jacket", "Carry-on · 420 g"),
    ChecklistItem("charger", "Charger and cable", "Carry-on · 90 g", packed = true),
    ChecklistItem("shoes", "Walking shoes", "Suitcase · 1 pair"),
    ChecklistItem("shirts", "Linen shirts", "Suitcase · 3"),
    ChecklistItem("adapter", "Plug adapter", "Carry-on · Type F", packed = true),
    ChecklistItem("toiletries", "Toiletries bag", "Suitcase · under 100 ml"),
    ChecklistItem("sunscreen", "Sunscreen", "Suitcase · SPF 50"),
    ChecklistItem("book", "Paperback", "Carry-on · for the flight"),
    ChecklistItem("bottle", "Water bottle", "Carry-on · empty at security"),
    ChecklistItem("socks", "Socks", "Suitcase · 5 pairs", packed = true),
    ChecklistItem("hat", "Sun hat", "Suitcase · packable"),
    ChecklistItem("meds", "Travel meds", "Carry-on · 1 pouch", packed = true),
    ChecklistItem("tote", "Folding tote", "Suitcase · for the market"),
)

private val START = STOWE_ITEMS.count { it.packed }
private val PROGRESS_EASE = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)

/** The packing list of Stowe, an invented travel app, around the checklist. */
@Composable
fun StoweListScreen(autoPlay: Boolean = true) {
    BlockSystemBars(darkIcons = true)
    val top = WindowInsets.safeDrawing.asPaddingValues().calculateTopPadding()
    var packed by remember { mutableIntStateOf(START) }
    val progress = remember { Animatable(START / STOWE_ITEMS.size.toFloat()) }
    LaunchedEffect(packed) {
        progress.animateTo(packed / STOWE_ITEMS.size.toFloat(), tween(320, easing = PROGRESS_EASE))
    }

    Column(Modifier.fillMaxSize().background(PAGE).padding(top = top + 8.dp)) {
        Column(Modifier.padding(start = SIDE.dp, end = SIDE.dp, bottom = 12.dp)) {
            Text("Leaving Friday, 7:40 am", style = TextStyle(color = MUTE, fontSize = 14.sp, fontWeight = FontWeight.Medium))
            Text(
                "Porto, 6 days",
                style = TextStyle(color = INK, fontSize = 32.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
                modifier = Modifier.padding(top = 2.dp).semantics { heading() },
            )
            Row(
                Modifier.padding(top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(TRACK),
                ) {
                    Box(
                        Modifier
                            .fillMaxSize()
                            .graphicsLayer {
                                scaleX = progress.value
                                transformOrigin = TransformOrigin(0f, 0.5f)
                            }
                            .background(GREEN, RoundedCornerShape(3.dp)),
                    )
                }
                Text(
                    "$packed of ${STOWE_ITEMS.size} packed",
                    style = TextStyle(color = INK, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
        }
        SpringReorderChecklist(
            items = STOWE_ITEMS,
            autoPlay = autoPlay,
            onPackedChange = { packed = it },
            modifier = Modifier.weight(1f),
        )
    }
}
