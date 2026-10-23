package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import java.util.Locale

// Native receipt text stays at its final position. Complementary circular
// clips exchange the ink without painting an opaque patch over the glass.
@Composable
internal fun AuraActivity(machine: AuraMachine, portraits: AuraPortraits, unit: Double, density: Float) {
    AuraLine(stringResource(R.string.aurawalletwidget_last), auraTextStyle(11.0, 14.0, unit, color = Color.White.copy(alpha = 0.62f)),
        Modifier.offset((26 * unit).dp, (212 * unit).dp).size((155 * unit).dp, (14 * unit).dp))
    val viewAll = stringResource(R.string.aurawalletwidget_view_all_accessibility)
    Box(Modifier.offset((181 * unit).dp, (219 * unit - 22).dp).size((155 * unit).dp, 44.dp)
        .semantics { contentDescription = viewAll }
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.CenterEnd) {
        AuraLine(stringResource(R.string.aurawalletwidget_view_all),
            auraTextStyle(11.0, 14.0, unit, color = Color.White.copy(alpha = 0.9f), align = TextAlign.End)
                .copy(textDecoration = TextDecoration.Underline), Modifier.clearAndSetSemantics {})
    }
    val old = machine.previous
    if (old != null) AuraReceiptLine(old, portraits, unit,
        Modifier.fillMaxSize().auraReceiptClip(machine, incoming = false, density = density, unit = unit))
    AuraReceiptLine(machine.receipt, portraits, unit,
        Modifier.fillMaxSize().auraReceiptClip(machine, incoming = true, density = density, unit = unit))
    val receipt = machine.receipt
    val dollars = String.format(Locale.US, "%.2f", receipt.cents / 100.0)
    val spoken = if (receipt.friend >= 0) {
        stringResource(
            R.string.aurawalletwidget_spoken_receipt,
            AuraPeople.names[receipt.friend],
            dollars,
        )
    } else stringResource(R.string.aurawalletwidget_spoken_initial, dollars)
    Box(Modifier.offset((28 * unit).dp, (234 * unit).dp).size((306 * unit).dp, (36 * unit).dp)
        .semantics { contentDescription = spoken })
}

@Composable
private fun AuraReceiptLine(receipt: AuraReceipt, portraits: AuraPortraits, unit: Double, modifier: Modifier) {
    Box(modifier.clearAndSetSemantics {}) {
        val mark = Modifier.offset((28 * unit).dp, (234 * unit).dp).size((36 * unit).dp)
        if (receipt.friend >= 0) AuraPortrait(portraits, receipt.friend, mark)
        else Box(mark, contentAlignment = Alignment.Center) {
            val pearl = remember {
                Brush.linearGradient(listOf(Color.White, Color(0xFFCFC1E4)), Offset.Zero, Offset(28.8f, 36f))
            }
            Canvas(Modifier.fillMaxSize()) {
                // The gradient uses design points, just like the SVG's 36-unit viewBox.
                drawContext.canvas.save()
                drawContext.canvas.scale(size.width / 36, size.height / 36)
                drawCircle(pearl, 18f, Offset(18f, 18f))
                drawContext.canvas.restore()
            }
            AuraIcon(AuraIcons.orbit, 24.0, unit, Color(0xFF8A4FD8))
        }
        AuraLine(if (receipt.friend < 0) AuraPeople.title else AuraPeople.names[receipt.friend],
            auraTextStyle(14.0, 18.0, unit, FontWeight.SemiBold),
            Modifier.offset((72 * unit).dp, (236 * unit).dp).size((109 * unit).dp, (18 * unit).dp))
        AuraLine(if (receipt.friend < 0) "${AuraPeople.date} · ${AuraPeople.time}" else stringResource(R.string.aurawalletwidget_just_now),
            auraTextStyle(11.0, 14.0, unit, color = Color.White.copy(alpha = 0.62f)),
            Modifier.offset((72 * unit).dp, (255 * unit).dp).size((109 * unit).dp, (14 * unit).dp))
        AuraLine("${if (receipt.friend < 0) "" else "−"}$${AuraPeople.formatCents(receipt.cents)}",
            auraTextStyle(16.0, 20.0, unit, FontWeight.SemiBold, align = TextAlign.End).copy(fontFeatureSettings = "tnum"),
            Modifier.offset((181 * unit).dp, (249 * unit).dp).size((153 * unit).dp, (20 * unit).dp), Alignment.CenterEnd)
    }
}
