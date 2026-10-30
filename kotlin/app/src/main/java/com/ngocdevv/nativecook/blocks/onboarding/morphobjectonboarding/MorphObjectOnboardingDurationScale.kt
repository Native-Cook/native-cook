package com.ngocdevv.nativecook.blocks.onboarding.morphobjectonboarding

import androidx.compose.ui.MotionDurationScale
import kotlinx.coroutines.withContext

internal suspend fun <T> withContextDurationScaleOne(block: suspend () -> T): T {
    return withContext(object : MotionDurationScale {
        override val scaleFactor = 1f
    }) {
        block()
    }
}
