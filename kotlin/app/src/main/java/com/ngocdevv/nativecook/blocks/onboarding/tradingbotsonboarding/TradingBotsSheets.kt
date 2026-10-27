package com.ngocdevv.nativecook.blocks.onboarding.tradingbotsonboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

internal enum class TradingBotsDetail(val title: String) {
    Overview("Simulation overview"),
    Trial("Demo trial selected"),
    Terms("Showcase terms"),
    Privacy("Showcase privacy"),
    Finish("You're ready to explore"),
}

private const val LocalNote = "This is a local showcase. No wallet is connected, no trades are placed, and no trial or purchase is activated."

/** Local sheets for trial, terms, privacy, overview and Skip on Pro. Nothing leaves the device. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun DetailSheet(detail: TradingBotsDetail, risk: String, allocation: Float, close: () -> Unit, replay: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = hex(0x1c1c1e)) {
        Column(Modifier.fillMaxWidth().padding(30.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(25.dp)) {
            Mark(size = 55f)
            Text(detail.title, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
            if (detail == TradingBotsDetail.Overview) {
                Text("Simulated return: +$1,284.50\nCapital allocation: ${allocation.toInt()}%\nRisk per trade: $risk", color = Color.White, fontSize = 16.sp, lineHeight = 30.sp, textAlign = TextAlign.Center)
            }
            val body = when (detail) {
                TradingBotsDetail.Privacy -> "This showcase makes no network requests and does not collect wallet, account or payment details. Settings live only for this demo session."
                TradingBotsDetail.Terms -> "This is an interactive design reconstruction. Balances, simulated returns and subscription prices are fictional local fixtures; no financial service or subscription is provided."
                else -> LocalNote
            }
            Text(body, color = white(0.6f), fontSize = 16.sp, textAlign = TextAlign.Center)
            Text("Keep exploring", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.background(hex(0x0a84ff), RoundedCornerShape(20.dp)).clickable(onClick = close).padding(horizontal = 20.dp, vertical = 10.dp))
            Text("Replay onboarding", color = hex(0x0a84ff), fontSize = 17.sp, modifier = Modifier.clickable(onClick = replay))
        }
    }
}

/**
 * Deliberately separate from the reconstructed product composition. Hold Back (or the five-step
 * progress) for 0.8 s to open. No automatic replay on launch.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun QAHarness(step: Int, reduced: Boolean, setReduced: (Boolean) -> Unit, show: (Int) -> Unit, replay: () -> Unit, reset: () -> Unit, close: () -> Unit) {
    ModalBottomSheet(onDismissRequest = close, containerColor = hex(0x1c1c1e)) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("Showcase QA", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.align(Alignment.CenterHorizontally))
            Section("INSPECT A SCENE")
            TradingBotsSteps.forEachIndexed { index, name -> SheetRow(name, if (index == step) "✓" else null) { show(index) } }
            Section("MOTION")
            Row(Modifier.fillMaxWidth().background(hex(0x2c2c2e), RoundedCornerShape(10.dp)).padding(horizontal = 16.dp).heightIn(min = 44.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("Force reduced motion", color = Color.White, fontSize = 17.sp, modifier = Modifier.weight(1f))
                Switch(reduced, setReduced)
            }
            Text("The system animation setting is always respected. This override allows repeatable QA without changing device settings.", color = white(0.5f), fontSize = 13.sp)
            SheetRow("Play walkthrough (about 20 seconds)", null, hex(0x0a84ff), replay)
            SheetRow("Reset to wallet", null, hex(0x0a84ff), reset)
            Section("LOCAL PROTOTYPE")
            Text("All money, bot settings and subscription values are fixtures. Trial, terms and privacy actions open local demo sheets. No network, trading or purchase is performed.", color = white(0.5f), fontSize = 13.sp)
        }
    }
}

@Composable
private fun Section(title: String) = Text(title, color = white(0.5f), fontSize = 13.sp, modifier = Modifier.padding(top = 18.dp, start = 4.dp))

@Composable
private fun SheetRow(title: String, trailing: String?, color: Color = Color.White, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().background(hex(0x2c2c2e), RoundedCornerShape(10.dp)).clickable(onClick = onClick).padding(horizontal = 16.dp).heightIn(min = 44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(title, color = color, fontSize = 17.sp, modifier = Modifier.weight(1f))
        if (trailing != null) Text(trailing, color = Color.White, fontSize = 17.sp)
    }
}
