package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R
import kotlin.math.abs
import kotlin.math.max

// The transformed hit areas follow the glass. Counter-scaling keeps the
// labels and arrows undistorted while Send and Receive exchange their widths.
@Composable
internal fun AuraActions(machine: AuraMachine, unit: Double, density: Float) {
    val labels = arrayOf(stringResource(R.string.aurawalletwidget_settings), stringResource(R.string.aurawalletwidget_swap),
        stringResource(R.string.aurawalletwidget_receive), stringResource(R.string.aurawalletwidget_send))
    val paths = arrayOf(AuraIcons.settings, AuraIcons.swap, AuraIcons.receive, AuraIcons.send)
    val drive = machine.drive
    for (index in 0..3) {
        val baseX = when (index) {
            0 -> 12.0
            1 -> 70.0
            2 -> 128.0
            else -> 254.0
        }
        val baseWidth = when (index) {
            0, 1 -> 52.0
            2 -> 120.0
            else -> 96.0
        }
        val labelled = index >= 2
        val priced = index == 3 && drive.phase != AuraPhase.Rest && !machine.landed && drive.amount > 0
        val spoken = labels[index] + if (priced) " $${drive.amount}" else ""
        var labelWidth by remember { mutableIntStateOf(0) }
        Box(
            Modifier.offset((baseX * unit).dp, (AuraPalette.Actions.y * unit).dp)
                .size((baseWidth * unit).dp, (AuraPalette.Actions.h * unit).dp)
                .graphicsLayer {
                    val trade = if (machine.reduced) if (drive.wide.value < 0.5) 0.0 else 1.0 else drive.wide.value
                    auraActionFrames(trade) { settingsX, swapX, receiveX, sendX, settingsWidth, swapWidth, receiveWidth, sendWidth, _ ->
                        val x = when (index) {
                            0 -> settingsX
                            1 -> swapX
                            2 -> receiveX
                            else -> sendX
                        }
                        val width = when (index) {
                            0 -> settingsWidth
                            1 -> swapWidth
                            2 -> receiveWidth
                            else -> sendWidth
                        }
                        translationX = ((x + width / 2 - baseX - baseWidth / 2) * unit * density).toFloat()
                        scaleX = (width / baseWidth).toFloat()
                    }
                }
                .semantics {
                    role = Role.Button
                    contentDescription = spoken
                    onClick {
                        machine.touch()
                        if (index == 3) machine.send()
                        true
                    }
                }
                .auraButton(machine, index),
            contentAlignment = Alignment.Center,
        ) {
            Box(Modifier.clearAndSetSemantics {}.graphicsLayer {
                val progress = drive.wide.value
                val trade = if (machine.reduced) if (progress < 0.5) 0.0 else 1.0 else progress
                auraActionFrames(trade) { _, _, _, _, settingsWidth, swapWidth, receiveWidth, sendWidth, _ ->
                    val width = when (index) {
                        0 -> settingsWidth
                        1 -> swapWidth
                        2 -> receiveWidth
                        else -> sendWidth
                    }
                    scaleX = (baseWidth / width).toFloat()
                }
                alpha = if (machine.reduced && index >= 2) abs(1 - 2 * progress.coerceIn(0.0, 1.0)).toFloat() else 1f
            }.wrapContentSize(unbounded = true)) {
                Row(
                    Modifier.offset(x = (if (labelled) AuraPalette.Details.actionOpticalX * unit else 0.0).dp)
                        .graphicsLayer {
                            val scale = if (machine.reduced) 1f
                            else (1 + (AuraPalette.Motion.pressScale - 1) * drive.press[index].value).toFloat()
                            scaleX = scale
                            scaleY = scale
                        },
                    horizontalArrangement = Arrangement.spacedBy((if (labelled) AuraPalette.Actions.arrowGap * unit else 0.0).dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (labelled) AuraLine(labels[index], auraTextStyle(15.0, 20.0, unit, FontWeight.Medium),
                        Modifier.onSizeChanged { labelWidth = it.width }.graphicsLayer {
                            alpha = if (index == 2) max(0.0, 1 - drive.wide.value * 2).toFloat() else 1f
                        })
                    if (priced) AuraNumber("$${drive.amount}", auraTextStyle(15.0, 20.0, unit, FontWeight.Medium), machine.reduced)
                    val side = if (labelled) AuraPalette.Actions.arrow else AuraPalette.Actions.icon
                    Box(Modifier.size((side * unit).dp).graphicsLayer {
                        val trade = if (machine.reduced) if (drive.wide.value < 0.5) 0.0 else 1.0 else drive.wide.value
                        translationX = if (index == 2) {
                            (-(labelWidth + AuraPalette.Actions.arrowGap * unit * density) / 2 * trade).toFloat()
                        } else 0f
                    }) {
                        AuraIcon(paths[index], side, unit, modifier = Modifier.graphicsLayer {
                            val progress = if (index == 3 && drive.phase == AuraPhase.Sending)
                                (drive.clock.value / AuraFlow.Views.arrowExitTime).coerceIn(0.0, 1.0) else 0.0
                            alpha = (1 - progress).toFloat()
                            translationY = if (machine.reduced) 0f else (-AuraFlow.Views.arrowExit * unit * density * progress).toFloat()
                        })
                        if (index == 3) AuraIcon(AuraIcons.send, side, unit, modifier = Modifier.graphicsLayer {
                            val progress = if (drive.phase == AuraPhase.Sending) {
                                ((drive.clock.value - AuraFlow.Views.arrowEnterDelay) / AuraFlow.Views.arrowEnterTime)
                                    .coerceIn(0.0, 1.0)
                            } else 0.0
                            alpha = progress.toFloat()
                            translationY = if (machine.reduced) 0f
                            else (AuraFlow.Views.arrowEnter * unit * density * (1 - progress)).toFloat()
                        })
                    }
                }
            }
        }
    }
}
