package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import android.content.pm.ApplicationInfo
import androidx.activity.compose.LocalActivity
import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun DonationFanCarouselDemo() {
    val activity = LocalActivity.current
    val freezeAt = activity
        ?.takeIf { it.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE != 0 }
        ?.intent?.getStringExtra("freezeAt")?.toDoubleOrNull()
        ?.takeIf { it.isFinite() && it >= 0 }
    DonationFanCarousel(autoPlay = true, freezeAt = freezeAt)
}

@Preview
@Composable
private fun DonationFanCarouselDemoPreview() {
    DonationFanCarouselDemo()
}
