package com.ngocdevv.nativecook.blocks.forms.icingnameform

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.State
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/* Icing Name Form — the rolling price and the icing's name (read only inside graphics layers). */

private const val DIGIT_H = 30f
private val Ink = Color(0xFF3B2418)

private fun priceDigit(cents: Float, k: Int) = (floor(cents / 10f.pow(k)) % 10)

@Composable
internal fun IcPrice(from: State<Float>, to: State<Float>, roll: State<Float>) {
    val glyph = TextStyle(fontSize = 26.sp, lineHeight = DIGIT_H.sp, fontWeight = FontWeight.Bold, color = Ink)
    Row(Modifier.semantics { contentDescription = "Total price" }, verticalAlignment = Alignment.CenterVertically) {
        BasicText("$", style = glyph)
        IcPriceColumn(from, to, roll, 3)
        IcPriceColumn(from, to, roll, 2)
        BasicText(".", style = glyph)
        IcPriceColumn(from, to, roll, 1)
        IcPriceColumn(from, to, roll, 0)
    }
}

@Composable
private fun IcPriceColumn(from: State<Float>, to: State<Float>, roll: State<Float>, place: Int) {
    val style = TextStyle(fontSize = 26.sp, lineHeight = DIGIT_H.sp, fontWeight = FontWeight.Bold, color = Ink, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
    Box(Modifier.height(DIGIT_H.dp).width(17.dp).clipToBounds()) {
        // The strip is laid out at its full height (unbounded), or the one-digit box would measure it to one digit.
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val a = priceDigit(from.value, place)
                    val b = priceDigit(to.value, place)
                    translationY = -(a + (b - a) * roll.value) * DIGIT_H * density
                },
        ) {
            for (i in 0..9) {
                Box(Modifier.height(DIGIT_H.dp).width(17.dp), contentAlignment = Alignment.Center) { BasicText(i.toString(), style = style) }
            }
        }
    }
}

@Composable
internal fun IcName(position: State<Float>) {
    Box(Modifier.height(20.dp).width(92.dp).clipToBounds()) {
        Icings.forEachIndexed { i, ic ->
            BasicText(
                ic.name,
                style = TextStyle(fontSize = 15.sp, lineHeight = 20.sp, fontWeight = FontWeight.Bold, color = Ink),
                modifier = Modifier.graphicsLayer {
                    val d = i - position.value
                    alpha = max(0f, 1 - abs(d) * 1.2f)
                    translationY = d * 20 * density
                },
            )
        }
    }
}
