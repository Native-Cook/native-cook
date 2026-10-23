package com.ngocdevv.nativecook.blocks.charts.skyscreentime

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun SkyScreenTimeDemo() {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()
    val manual = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
        activity?.intent?.getBooleanExtra("sky-manual", false) == true
    SkyScreenTime(autoPlay = !manual)
}

@Preview
@Composable
private fun SkyScreenTimePreview() {
    SkyScreenTimeDemo()
}
