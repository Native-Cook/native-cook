package com.ngocdevv.nativecook.blocks.lists.bankcardpicker

import android.content.pm.ApplicationInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun BankCardPickerDemo() {
    val activity = LocalActivity.current
    val freezeAt = remember(activity) {
        val debug = ((activity?.applicationInfo?.flags ?: 0) and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        if (debug) activity?.intent?.getStringExtra("freezeAt")?.toLongOrNull() else null
    }
    BankCardPicker(autoPlay = freezeAt == null, freezeAt = freezeAt)
}

@Preview
@Composable
private fun BankCardPickerDemoPreview() {
    BankCardPickerDemo()
}
