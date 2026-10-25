package com.ngocdevv.nativecook.blocks.lists.aquariumfeedlist

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ngocdevv.nativecook.shared.cssBoxShadow

private val BorderOff = Color(0x29DCF8FF)
private val BorderOn = Color(0xB3FFD680)
private val GlassOff = Color(0x80082E3C)
private val GlassOn = Color(0x8F2A4C40)

/**
 * One log entry: a pane of glass in the water (fish swim behind it), an icon tile, the title and
 * its detail, the time and a tag. It never recomposes while the list scrolls; the feeding glow and
 * the press are read in its draw and layer lambdas only.
 *
 * React Native frosts the glass with a still BlurView; Compose cannot blur what lies behind a view,
 * so here the pane is the same tinted glass without the frost.
 */
@Composable
internal fun AquariumCard(index: Int, entry: AqEntry, focus: AqFocus, onPress: (Int) -> Unit) {
    val tint = Color(entry.tint)
    val radius = 22.dp
    val description = remember(entry) { "${entry.title}, ${entry.detail}, ${entry.time}" }
    Row(
        Modifier
            .fillMaxWidth()
            .height(AQ_CARD_H.dp)
            .graphicsLayer {
                val press = if (focus.card.intValue == index) focus.press.floatValue else 0f
                val k = 1 - 0.025f * press
                scaleX = k; scaleY = k
            }
            .cssBoxShadow(Color(0x47011018), 14.dp, 30.dp, radius)
            .cssBoxShadow(Color(0x4D011018), 1.dp, 2.dp, radius)
            .drawBehind {
                val on = if (focus.card.intValue == index) focus.amt.floatValue.coerceIn(0f, 1f) else 0f
                val r = CornerRadius(radius.toPx())
                drawRoundRect(lerp(GlassOff, GlassOn, on), cornerRadius = r)
                // The inset shade along the bottom and the glass's lit top edge.
                drawRect(Color(0x4000141E), Offset(radius.toPx() * 0.6f, size.height - 1.dp.toPx()), Size(size.width - radius.toPx() * 1.2f, 1.dp.toPx()))
                drawRect(Color(0x6BE1FAFF), Offset(14.dp.toPx(), 0f), Size(size.width - 28.dp.toPx(), 1.dp.toPx()))
                val bw = 1.dp.toPx()
                drawRoundRect(
                    lerp(BorderOff, BorderOn, on),
                    topLeft = Offset(bw / 2, bw / 2),
                    size = Size(size.width - bw, size.height - bw),
                    cornerRadius = CornerRadius(radius.toPx() - bw / 2),
                    style = Stroke(bw),
                )
            }
            .pointerInput(index) {
                // On press in, as in React Native; the touch is not consumed, so the list still scrolls.
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    onPress(index)
                }
            }
            .semantics {
                contentDescription = description
                role = Role.Button
            }
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Box(
            Modifier
                .size(44.dp)
                .background(tint.copy(alpha = 0x26 / 255f), RoundedCornerShape(14.dp)),
            contentAlignment = Alignment.Center,
        ) {
            Icon(entry.icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start)) {
                BasicText(
                    entry.title,
                    modifier = Modifier.weight(1f).alignByBaseline(),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    style = TextStyle(color = Color(0xFFF0FBFC), fontSize = 17.sp, fontWeight = FontWeight.Bold),
                )
                BasicText(
                    entry.time,
                    modifier = Modifier.alignByBaseline(),
                    style = TextStyle(color = Color(0x8CDEF6FA), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, fontFeatureSettings = "tnum"),
                )
            }
            BasicText(
                entry.detail,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                style = TextStyle(color = Color(0xC7DEF6FA), fontSize = 14.sp, fontWeight = FontWeight.Medium),
            )
            BasicText(
                entry.tag.uppercase(),
                modifier = Modifier.padding(top = 3.dp),
                style = TextStyle(color = tint, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.4.sp),
            )
        }
    }
}
