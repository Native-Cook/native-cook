package com.ngocdevv.nativecook.blocks.inputs.crossstitchinput

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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow

/* Cross Stitch Input — the rolling stitch counter and thread name (read only inside graphics layers). */

internal const val DIGIT_H = 22f
private val Ink = Color(0xFF2E2620)

internal fun csDigitPosition(v: Float, k: Int): Float {
    val p = 10f.pow(k)
    val d = floor(v / p) % 10
    if (k == 0) return v - floor(v / 10) * 10
    val lower = v - floor(v / p) * p
    return d + max(0f, lower - (p - 1))
}

@Composable
internal fun CsDigits(value: State<Float>, places: Int) {
    Row {
        for (k in places - 1 downTo 0) CsDigitColumn(value, k)
    }
}

@Composable
private fun CsDigitColumn(value: State<Float>, place: Int) {
    val style = TextStyle(fontSize = 18.sp, lineHeight = DIGIT_H.sp, fontWeight = FontWeight.Bold, color = Ink, fontFeatureSettings = "tnum", textAlign = TextAlign.Center)
    Box(Modifier.height(DIGIT_H.dp).width(12.dp).clipToBounds()) {
        // The strip is laid out at its full height (unbounded), or the one-digit box would measure it to one digit.
        Column(
            Modifier
                .wrapContentHeight(align = Alignment.Top, unbounded = true)
                .graphicsLayer {
                    val v = max(0f, value.value)
                    translationY = -csDigitPosition(v, place) * DIGIT_H * density
                    alpha = if (place == 0) 1f else min(1f, max(0f, v - 10f.pow(place) + 1))
                },
        ) {
            for (i in 0..10) {
                Box(Modifier.height(DIGIT_H.dp).width(12.dp), contentAlignment = Alignment.Center) {
                    BasicText((i % 10).toString(), style = style)
                }
            }
        }
    }
}

@Composable
internal fun CsThreadName(position: State<Float>) {
    Box(Modifier.height(DIGIT_H.dp).width(96.dp).clipToBounds()) {
        Threads.forEachIndexed { i, th ->
            BasicText(
                th.name,
                style = TextStyle(fontSize = 17.sp, lineHeight = DIGIT_H.sp, fontWeight = FontWeight.Bold, color = Ink),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .graphicsLayer {
                        val d = i - position.value
                        alpha = max(0f, 1 - abs(d) * 1.2f)
                        translationY = d * DIGIT_H * density
                    },
            )
        }
    }
}

