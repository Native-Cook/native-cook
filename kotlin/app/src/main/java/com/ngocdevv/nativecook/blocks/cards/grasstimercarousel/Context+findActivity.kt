package com.ngocdevv.nativecook.blocks.cards.grasstimercarousel

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal fun Context.findGrassTimerActivity(): Activity? {
    var candidate = this
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate
        candidate = candidate.baseContext
    }
    return candidate as? Activity
}
