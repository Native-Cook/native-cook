package com.ngocdevv.nativecook.blocks.sheets.eventpassshare

import android.app.Activity
import android.content.ContextWrapper
import android.content.pm.ApplicationInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun EventPassShareDemo() {
    val context = LocalContext.current
    val activity = generateSequence(context) { (it as? ContextWrapper)?.baseContext }
        .filterIsInstance<Activity>().firstOrNull()
    val manual = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 &&
        activity?.intent?.getBooleanExtra("event-manual", false) == true
    EventPassShare(autoPlay = !manual)
}

@Preview(widthDp = 402, heightDp = 874)
@Composable
private fun EventPassSharePreview() {
    EventPassShareDemo()
}
