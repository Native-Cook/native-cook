package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import android.graphics.Path
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import kotlin.math.max

// Reuse one native clip path per receipt layer; clipOutPath is available from
// the block's minimum API 26 and removes exactly the incoming circle's ink.
@Composable
internal fun Modifier.auraReceiptClip(machine: AuraMachine, incoming: Boolean, density: Float, unit: Double): Modifier {
    val circle = remember { Path() }
    return graphicsLayer {
        val writing = machine.previous != null
        val progress = (machine.drive.receiptClock.value / AuraFlow.Views.receiptFade).coerceIn(0.0, 1.0)
        alpha = if (machine.reduced && writing) (if (incoming) progress else 1 - progress).toFloat() else 1f
    }.drawWithContent {
        if (machine.previous == null || machine.reduced) {
            drawContent()
        } else {
            val radius = max(if (incoming) 0.0 else 0.001,
                machine.drive.receiptClock.value * AuraFlow.Send.rippleSpeed / 1000) * unit * density
            circle.rewind()
            circle.addCircle((machine.drive.receiptX * unit * density).toFloat(),
                (AURA_LAND_Y * unit * density).toFloat(), radius.toFloat(), Path.Direction.CW)
            val canvas = drawContext.canvas.nativeCanvas
            canvas.save()
            if (incoming) canvas.clipPath(circle) else canvas.clipOutPath(circle)
            drawContent()
            canvas.restore()
        }
    }
}
