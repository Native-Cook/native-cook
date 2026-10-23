package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R

// The owner, route, chip and rolling balance retain the source's optical
// centers. Route movement is a layer transform, independent of text layout.
@Composable
internal fun AuraBalance(machine: AuraMachine, portraits: AuraPortraits, unit: Double, density: Float) {
    val drive = machine.drive
    val active = machine.route
    val person = drive.chosen
    Box(Modifier.offset((12 * unit).dp, (12 * unit).dp).size((338 * unit).dp, (156 * unit).dp)
        .clickable(remember { MutableInteractionSource() }, indication = null) { machine.dismiss() }
        .clearAndSetSemantics {})
    val faceModifier = Modifier.offset((29 * unit).dp, (26 * unit).dp).size((22 * unit).dp)
    if (person >= 0) AuraPortrait(portraits, person, faceModifier.graphicsLayer {
        alpha = drive.identity.value.toFloat()
        translationX = (AuraFlow.Views.routeTravel * unit * density *
            if (machine.reduced) if (active) 1.0 else 0.0 else drive.identity.value).toFloat()
    })
    AuraPortrait(portraits, AuraPeople.owner, faceModifier)
    AuraLine(if (active && person >= 0) "Ana › ${AuraPeople.names[person]}" else "Ana",
        auraTextStyle(12.0, 16.0, unit, tracking = 0.0),
        Modifier.offset((56 * unit).dp, (29 * unit).dp).size((259 * unit).dp, (16 * unit).dp).graphicsLayer {
            alpha = AuraPalette.Details.ownerOpacity.toFloat()
            translationX = (AuraFlow.Views.routeTravel * unit * density *
                if (machine.reduced) if (active) 1.0 else 0.0 else drive.identity.value).toFloat()
        })
    val account = stringResource(R.string.aurawalletwidget_account)
    Box(Modifier.offset((326 * unit - 22).dp, (37 * unit - 22).dp).size(44.dp)
        .semantics { contentDescription = account }
        .clickable(remember { MutableInteractionSource() }, indication = null, role = Role.Button) {},
        contentAlignment = Alignment.Center) {
        Box(Modifier.size((22 * unit).dp).background(Color(0xFF260670).copy(alpha = 0.42f), CircleShape),
            contentAlignment = Alignment.Center) {
            AuraIcon(AuraIcons.info, 22.0, unit, Color.White.copy(alpha = 0.72f), rounded = false)
        }
    }
    AuraChip(machine, unit, density)
    val formatted = AuraPeople.formatCents(machine.displayedBalance)
    val dollars = formatted.substringBefore('.')
    val cents = formatted.substringAfter('.')
    val spoken = stringResource(if (machine.preview) R.string.aurawalletwidget_spoken_after else R.string.aurawalletwidget_spoken_balance,
        dollars, cents)
    Box(Modifier.offset((12 * unit).dp, (77 * unit).dp).size((338 * unit).dp, (42 * unit).dp)
        .semantics { contentDescription = spoken }, contentAlignment = Alignment.Center) {
        AuraNumber("$ $formatted", auraTextStyle(34.0, 42.0, unit, FontWeight.SemiBold, tracking = -0.6), machine.reduced)
    }
}

@Composable
internal fun AuraPortrait(portraits: AuraPortraits, index: Int, modifier: Modifier = Modifier) {
    Canvas(modifier.clearAndSetSemantics {}) {
        drawIntoCanvas { canvas ->
            canvas.nativeCanvas.save()
            canvas.nativeCanvas.translate(size.width / 2, size.height / 2)
            portraits.draw(canvas.nativeCanvas, index, size.width / 2, zoom = false)
            canvas.nativeCanvas.restore()
        }
    }
}
