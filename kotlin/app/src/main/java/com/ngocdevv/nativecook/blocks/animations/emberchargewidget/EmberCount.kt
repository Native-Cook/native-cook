@file:androidx.annotation.RequiresApi(33)

package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.E
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberHeartTone
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.emberTone
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.optical
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.EmberTheme.type
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.enums.NumericTextAlignment
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.records.TransitionShape
import com.ngocdevv.nativecook.blocks.animations.emberchargewidget.contenttransition.views.NumericText
import java.util.Locale
import kotlin.math.cos
import kotlin.math.exp

@Composable
internal fun EmberCount(motion: EmberMotion) {
    val shown = motion.shown
    val reduced = motion.reduced
    val style = emberTextStyle(E.count.size, spacing = type.countTracking, bold = true)
    Row(Modifier.offset(E.count.x.dp, E.count.top.dp), verticalAlignment = Alignment.Bottom) {
        Box(
            Modifier.graphicsLayer {
                motion.revision
                transformOrigin = TransformOrigin(0f, 1f)
                // Android and iOS NumericText have different ascent alignment. Preserve the 60 dp box.
                translationY = optical.countBaseline * density
                val age = motion.ignition * M.ignite
                scaleX = if (motion.reduced || motion.ignition <= 0) 1f else 1 + 0.18f * exp(-9 * age) * cos(12 * age)
                scaleY = scaleX
            },
        ) {
            if (!reduced && (shown == E.restSteps || shown == motion.goal)) key(shown) {
                var landing by remember { mutableIntStateOf(motion.previousShown) }
                // Two frames establish the previous glyphs before the sole endpoint roll.
                LaunchedEffect(shown) {
                    withFrameNanos {}
                    withFrameNanos {}
                    landing = shown
                }
                NumericText(
                    String.format(Locale.US, "%,d", landing), Modifier.height(E.count.h.dp), style = style,
                    duration = M.countRoll, bounce = M.countBounce,
                    shape = TransitionShape(travelRatio = 1f, maxBlurRadius = EmberTheme.drawing.numericBlur),
                )
            } else key("running") {
                // Running ticks never animate, so they cannot queue rolls behind the clock.
                NumericText(
                    String.format(Locale.US, "%,d", shown), Modifier.height(E.count.h.dp), style = style,
                    animationsEnabled = false,
                )
            }
        }
        BasicText(
            emberString(R.string.emberchargewidget_steps),
            Modifier.padding(start = E.count.unitGap.dp, bottom = E.count.unitDrop.dp),
            style = emberTextStyle(type.small, E.line.unit, color = Color(emberTone.onEmber)),
        )
    }
    Box(Modifier.offset(E.heart.x.dp, (E.heart.y + E.heart.rateOffset).dp)) {
        NumericText(
            (if (motion.reached) E.goalBpm else E.restBpm).toString(), Modifier.size(E.heart.w.dp, E.line.bpm.dp),
            style = emberTextStyle(
                type.bpm, spacing = type.bpmTracking, bold = true, color = Color(emberHeartTone.ink),
            ).copy(fontFeatureSettings = "tnum"),
            alignment = NumericTextAlignment.Center, duration = M.bpmRoll, bounce = M.bpmBounce,
            animationsEnabled = !reduced, shape = TransitionShape(maxBlurRadius = EmberTheme.drawing.numericBlur),
        )
    }
}
