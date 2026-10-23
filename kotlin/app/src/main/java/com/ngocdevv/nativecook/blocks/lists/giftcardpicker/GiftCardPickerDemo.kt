package com.ngocdevv.nativecook.blocks.lists.giftcardpicker

import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.activity.compose.LocalActivity
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun GiftCardPickerDemo() {
    val activity = LocalActivity.current
    val freezeAt = remember(activity) {
        val debug = ((activity?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debug) activity?.intent?.getStringExtra("freezeAt")?.toLongOrNull() else null
    }
    val manual = remember(activity) {
        val debug = ((activity?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        debug && activity?.intent?.getBooleanExtra("manual", false) == true
    }
    GiftCardPicker(autoPlay = freezeAt == null && !manual, freezeAt = freezeAt)
}

@Preview
@Composable
private fun GiftCardPickerDemoPreview() {
    GiftCardPickerDemo()
}
