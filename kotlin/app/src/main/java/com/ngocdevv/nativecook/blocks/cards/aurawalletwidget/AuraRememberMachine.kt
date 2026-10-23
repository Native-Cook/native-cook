package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.ngocdevv.nativecook.R
import kotlinx.coroutines.delay

// An idle widget suspends on its wake channel. Cue and confirmation delays do
// not schedule frames; arrival alone counts displayed frames before revealing.
@Composable
internal fun rememberAuraMachine(reduced: Boolean): AuraMachine {
    val context = LocalContext.current
    val view = LocalView.current
    val machine = remember(context, view) {
        val feedback = AuraHaptics(view)
        AuraMachine(AuraDrive(), feedback::play, announce = { index, dollars ->
            @Suppress("DEPRECATION")
            view.announceForAccessibility(context.getString(R.string.aurawalletwidget_announcement, dollars, AuraPeople.names[index]))
        })
    }
    SideEffect { machine.reduced = reduced }
    LaunchedEffect(machine) {
        machine.wake.trySend(Unit)
        for (signal in machine.wake) {
            while (machine.needsFrames) withFrameNanos { machine.frame(it / 1e6) }
        }
    }
    LaunchedEffect(machine, reduced) {
        if (!reduced) {
            delay(AuraFlow.Cue.first)
            while (!machine.touched) {
                machine.cue()
                delay(AuraFlow.Cue.every)
            }
        }
    }
    val sentId = machine.sent?.id
    LaunchedEffect(machine, sentId) {
        if (sentId != null) {
            delay(AuraFlow.Views.sentHold)
            machine.clearSent(sentId)
        }
    }
    return machine
}
