package com.ngocdevv.nativecook.blocks.cards.trophysavingsmilestone

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.BlockSystemBars

/** Faithful Paper Applause port of the approved RN reference, with native local goal controls. */
@Composable
fun TrophySavingsMilestone(
    autoPlay: Boolean = false,
    previewTime: Double? = null,
    previewReducedMotion: Boolean = false,
    modifier: Modifier = Modifier,
) {
    var composerPresented by rememberSaveable { mutableStateOf(false) }
    var summaryPresented by rememberSaveable { mutableStateOf(false) }
    val ribbon = remember { TrophySavingsMilestoneRibbonState() }

    fun closeComposer() { composerPresented = false; ribbon.updateOverlay(false) }
    fun closeSummary() { summaryPresented = false; ribbon.updateOverlay(false) }

    BlockSystemBars(darkIcons = true)
    MaterialTheme(colorScheme = lightColorScheme(primary = Color(0xFF222222))) {
        // Scale the complete 393 × 852 reference once, matching the other faithful card ports.
        CompositionLocalProvider(LocalDensity provides Density(LocalDensity.current.density, 1f)) {
            BoxWithConstraints(modifier.fillMaxSize().background(Color(0xFFEFEFEF))) {
                val scale = minOf(maxWidth.value / 393f, maxHeight.value / 852f)
                Box(
                    Modifier.align(Alignment.Center).requiredSize(393.dp, 852.dp)
                        .graphicsLayer { scaleX = scale; scaleY = scale },
                ) {
                    TrophySavingsMilestoneBackdrop()
                    TrophySavingsMilestonePaperField(Modifier.size(393.dp, 350.dp))
                    TrophySavingsMilestoneHero(ribbon, autoPlay, previewTime, previewReducedMotion, composerPresented || summaryPresented)
                    Box(Modifier.offset(x=21.5.dp,y=363.dp).size(350.dp,38.dp)
                        .semantics(mergeDescendants=true) { heading(); contentDescription="You Did It! 🎯" }) {
                        TrophySavingsMilestoneLabel("You Did It!",31f,FontWeight.Bold,Color(0xFF222222),
                            Modifier.offset(x=82.5.dp,y=(-0.5).dp).size(150.dp,38.dp),lineHeight=38f,letterSpacing=0.15f)
                        TrophySavingsMilestoneTargetGlyph(Modifier.offset(x=234.dp,y=6.dp).size(32.dp,26.dp))
                    }
                    TrophySavingsMilestoneLabel(
                        "You've successfully reached your savings\ngoal for School Fees. Every little step added\nup to something great!",
                        14.5f, FontWeight.Normal, Color(0xFF444444),
                        Modifier.offset(x = 26.5.dp, y = 412.5.dp).size(340.dp, 60.dp),
                        TextAlign.Center, lineHeight = 19.3f,
                    )
                    TrophySavingsMilestoneLabel(
                        "Milestone Details", 15f, FontWeight.Medium, Color(0xFF222222),
                        Modifier.offset(x = 20.dp, y = 503.dp).size(353.dp, 20.dp),
                    )
                    TrophySavingsMilestoneMetric(
                        "Target Reached", "₦70,000", 0, Color(0xFF1BA14D), Color(0xFFF5FEF9),
                        Modifier.offset(x = 20.dp, y = 537.dp).size(353.dp, 73.dp),
                    )
                    TrophySavingsMilestoneMetric(
                        "Duration", "3 months", 1, Color(0xFF327FE9), Color(0xFFF6F9FE),
                        Modifier.offset(x = 20.dp, y = 617.dp).size(173.dp, 73.dp),
                    )
                    TrophySavingsMilestoneMetric(
                        "Transfers", "5", 2, Color(0xFFDB0DD1), Color(0xFFFFF5FE),
                        Modifier.offset(x = 200.dp, y = 617.dp).size(173.dp, 73.dp),
                    )
                    TrophySavingsMilestoneButton(
                        "Set New Goal", Color(0xFF222222), Color.White,
                        Modifier.offset(x = 29.dp, y = 738.dp).size(187.dp, 49.dp),
                    ) { ribbon.updateOverlay(true); composerPresented = true }
                    TrophySavingsMilestoneButton(
                        "View Summary", Color(0xFFF7F7F7), Color(0xFF222222),
                        Modifier.offset(x = 232.dp, y = 738.dp).size(134.dp, 49.dp),
                    ) { ribbon.updateOverlay(true); summaryPresented = true }
                }
            }
        }

        if (composerPresented) {
            TrophySavingsMilestoneGoalComposer(onDismiss = ::closeComposer)
        }
        if (summaryPresented) {
            AlertDialog(
                onDismissRequest = ::closeSummary,
                title = { Text("Savings Summary") },
                text = { Text("School Fees\nTarget reached: ₦70,000\nDuration: 3 months\nTransfers: 5") },
                confirmButton = {
                    TextButton(onClick = ::closeSummary) { Text("Done") }
                },
            )
        }
    }
}

@Composable
private fun TrophySavingsMilestoneBackdrop() {
    Canvas(Modifier.fillMaxSize()) { drawIntoCanvas { canvas ->
        canvas.nativeCanvas.save(); canvas.nativeCanvas.scale(density,density)
        canvas.nativeCanvas.drawPicture(TrophySavingsMilestonePaper.backdrop); canvas.nativeCanvas.restore()
    } }
}

@Composable
private fun TrophySavingsMilestoneMetric(
    title: String,
    value: String,
    kind: Int,
    accent: Color,
    fill: Color,
    modifier: Modifier,
) {
    Row(
        modifier.background(fill, RoundedCornerShape(24.dp)).padding(horizontal = 16.dp)
            .semantics(mergeDescendants = true) {},
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        TrophySavingsMilestoneMetricIcon(kind, accent, Modifier.size(32.dp))
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            TrophySavingsMilestoneLabel(title, 13f, color = Color(0xFF868686),lineHeight=15.5f)
            TrophySavingsMilestoneLabel(value, 17f, FontWeight.SemiBold, Color(0xFF222222),lineHeight=20.5f)
        }
    }
}

@Composable
private fun TrophySavingsMilestoneButton(
    label: String,
    fill: Color,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier.graphicsLayer { alpha = if (pressed) 0.88f else 1f }
            .background(fill, CircleShape)
            .clickable(interactionSource = interaction, indication = null, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        TrophySavingsMilestoneLabel(label, 14f, FontWeight.Medium, color)
    }
}

@Composable
private fun TrophySavingsMilestoneLabel(
    value: String,
    size: Float,
    weight: FontWeight = FontWeight.Normal,
    color: Color,
    modifier: Modifier = Modifier,
    align: TextAlign = TextAlign.Start,
    lineHeight: Float = size * 1.2f,
    letterSpacing: Float = 0f,
) {
    Box(modifier, contentAlignment = if (align == TextAlign.Center) Alignment.Center else Alignment.CenterStart) {
        Text(
            value, color = color, fontSize = size.sp, fontWeight = weight, textAlign = align,
            lineHeight = lineHeight.sp, letterSpacing=letterSpacing.sp,
            style = TextStyle(platformStyle = PlatformTextStyle(includeFontPadding = false)),
        )
    }
}
