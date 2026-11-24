package com.ngocdevv.nativecook.blocks.modals.notificationstackexpand

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun NotificationStackExpandDemo() {
    NotificationStackExpand(autoPlay = true)
}

@Preview
@Composable
private fun NotificationStackExpandDemoPreview() {
    NotificationStackExpandDemo()
}
