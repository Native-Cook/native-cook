package com.ngocdevv.nativecook.blocks.cards.referralrewardcard

import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.asComposeRenderEffect

// Quantized sigmas share RenderEffects across the footer and capsule. Only their indices
// change during animation; creating a new filter on each frame would repeat GPU setup.
@Composable
internal fun rememberReferralLabelBlur(density: Float): Array<androidx.compose.ui.graphics.RenderEffect?>? =
    remember(density) {
        if (Build.VERSION.SDK_INT >= 31) {
            Array(65) { i ->
                if (i == 0) {
                    null
                } else {
                    android.graphics.RenderEffect
                        .createBlurEffect(
                            i / 16f * density,
                            i / 16f * density,
                            android.graphics.Shader.TileMode.DECAL,
                        ).asComposeRenderEffect()
                }
            }
        } else {
            null
        }
    }
