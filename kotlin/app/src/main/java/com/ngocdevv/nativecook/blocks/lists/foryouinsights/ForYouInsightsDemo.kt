package com.ngocdevv.nativecook.blocks.lists.foryouinsights

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext

@Composable
fun ForYouInsightsDemo(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manual = (context as? android.app.Activity)?.intent?.getBooleanExtra("insights-manual", false) == true ||
        (context as? android.app.Activity)?.intent?.getStringExtra("manual") == "1"
    val debugBuild = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
    ForYouInsights(autoPlay = !debugBuild || !manual, modifier = modifier)
}
