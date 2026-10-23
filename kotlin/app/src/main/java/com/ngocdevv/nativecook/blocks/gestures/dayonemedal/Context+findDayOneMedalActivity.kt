package com.ngocdevv.nativecook.blocks.gestures.dayonemedal

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

internal fun Context.findDayOneMedalActivity(): Activity? {
    var candidate = this
    while (candidate is ContextWrapper) {
        if (candidate is Activity) return candidate
        candidate = candidate.baseContext
    }
    return candidate as? Activity
}
