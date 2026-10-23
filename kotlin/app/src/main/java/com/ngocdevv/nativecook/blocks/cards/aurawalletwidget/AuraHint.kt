package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.R

// Only a new kind of sentence crossfades. Amount steps update its number in
// place, and two scalable lines reserve room for either language.
@Composable
internal fun AuraHint(machine: AuraMachine, modifier: Modifier = Modifier) {
    val drive = machine.drive
    val sent = machine.sent
    val kind = when {
        sent != null -> R.string.aurawalletwidget_hint_sent
        drive.phase == AuraPhase.Sending -> R.string.aurawalletwidget_hint_sending
        drive.phase == AuraPhase.Armed -> R.string.aurawalletwidget_hint_armed
        drive.phase == AuraPhase.Pulling && drive.amount > 0 -> R.string.aurawalletwidget_hint_pulling
        drive.phase == AuraPhase.Pulling -> R.string.aurawalletwidget_hint_idle_pull
        else -> R.string.aurawalletwidget_hint_rest
    }
    val name = AuraPeople.names.getOrElse(sent?.friend ?: drive.chosen) { "" }
    val amount = sent?.cents?.div(100)?.toInt() ?: drive.amount
    val copy = stringResource(kind, amount, name)
    val height = AuraPalette.Page.hintLine * LocalDensity.current.fontScale * 2
    Box(modifier.height(height.dp).semantics {
        contentDescription = copy
        liveRegion = LiveRegionMode.Polite
    }) {
        AnimatedContent(
            targetState = kind to copy,
            contentKey = { it.first },
            modifier = Modifier.fillMaxWidth().height(height.dp).clearAndSetSemantics {},
            transitionSpec = {
                val curve = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
                fadeIn(tween(AuraFlow.Views.hintFade.toInt(), easing = curve)) togetherWith
                    fadeOut(tween(AuraFlow.Views.hintFade.toInt(), easing = curve))
            },
            label = "Aura hint",
        ) { sentence ->
            BasicText(sentence.second, Modifier.fillMaxWidth(), maxLines = 2,
                style = auraTextStyle(13.0, 18.0, 1.0, color = Color.White.copy(alpha = 0.6f),
                    tracking = 0.0, align = TextAlign.Center))
        }
    }
}
