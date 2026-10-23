package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

// Shared UI-thread values. Only choices and milestones are read in composition;
// moving values are consumed in drawing, placement and graphics-layer callbacks.
internal class AuraDrive {
    val press = Array(4) { AuraValue(0.0) }
    val friendPress = Array(5) { AuraValue(0.0) }
    var phase by mutableStateOf(AuraPhase.Rest)
    var chosen by mutableIntStateOf(-1)
    var leaving by mutableIntStateOf(-1)
    var amount by mutableIntStateOf(AuraFlow.Pull.amountDefault.toInt())
    val formed = AuraValue(0.0)
    val unformed = AuraValue(0.0)
    val pullX = AuraValue(0.0)
    val pullY = AuraValue(0.0)
    val clock = AuraValue(0.0)
    var fromX = 0.0
    var fromY = 0.0
    val wide = AuraValue(0.0)
    val nudge = AuraValue(-1.0)
    val weight = AuraValue(AuraFlow.Pull.amountDefault / AuraFlow.Pull.amountMax)
    val entered = AuraValue(0.0)
    val shown = AuraValue(0.0)
    val sinkClock = AuraValue(0.0)
    val receiptClock = AuraValue(AuraFlow.Send.rippleLife)
    var receiptX = 0.0
    val identity = AuraValue(0.0)
    val chip = AuraValue(0.0)
    val offsets = Array(20) { AuraValue(0.0) }
    val values = arrayOf(formed, unformed, pullX, pullY, clock, wide, nudge,
        weight, entered, shown, sinkClock, receiptClock, identity, chip) + press + friendPress + offsets
}
