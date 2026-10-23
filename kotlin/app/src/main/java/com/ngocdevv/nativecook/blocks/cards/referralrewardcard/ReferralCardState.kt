package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import com.ngocdevv.nativecook.R

// A card owns one animation clock and one announcement callback for its lifetime.
@Composable
internal fun rememberReferralState(
    width: Float,
    autoPlay: Boolean,
    reduced: Boolean,
): ReferralState {
    val context = LocalContext.current
    val view = LocalView.current
    val haptics = remember(context) { ReferralHaptics(context) }
    val state =
        remember {
            ReferralState(
                haptics::play,
                { who, count, opening ->
                    val name = ReferralFriends.names[who]
                    val message =
                        if (opening) {
                            val status =
                                context.getString(
                                    if (count < 0) {
                                        R.string.referralrewardcard_joined
                                    } else {
                                        R.string.referralrewardcard_pending
                                    },
                                )
                            "$name. $status."
                        } else {
                            context.getString(
                                R.string.referralrewardcard_announcement,
                                name,
                                count * ReferralPalette.pointsPerFriend,
                            )
                        }
                    @Suppress("DEPRECATION")
                    view.announceForAccessibility(message)
                },
                autoPlay,
            )
        }
    SideEffect {
        state.setReducedMotion(reduced)
        state.width = width.toDouble()
    }
    LaunchedEffect(state) { while (true) withFrameNanos { state.frame(it / 1e6) } }
    return state
}
