package com.ngocdevv.nativecook.blocks.charts.nightsleepscore

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun NightSleepScoreDemo() {
    val context = LocalContext.current
    val activity = nightSleepScoreActivity(context)
    val debug = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    val manual = debug && (
        activity?.intent?.getBooleanExtra("sleep-manual", false) == true ||
            activity?.intent?.getBooleanExtra("manual", false) == true
        )
    NightSleepScore(autoPlay = !manual)
}
