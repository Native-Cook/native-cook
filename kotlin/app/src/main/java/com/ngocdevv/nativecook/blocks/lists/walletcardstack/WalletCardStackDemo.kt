package com.ngocdevv.nativecook.blocks.lists.walletcardstack

import android.app.Activity
import android.content.pm.ApplicationInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun WalletCardStackDemo() {
    val context = LocalContext.current
    val freezeAt = remember(context) {
        val debuggable = context.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0
        if (debuggable) {
            (context as? Activity)?.intent?.getStringExtra("freezeAt")?.toFloatOrNull()
        } else {
            null
        }
    }
    WalletCardStack(
        autoPlay = freezeAt == null,
        freezeAt = freezeAt,
    )
}

@Preview
@Composable
private fun WalletCardStackDemoPreview() {
    WalletCardStackDemo()
}
