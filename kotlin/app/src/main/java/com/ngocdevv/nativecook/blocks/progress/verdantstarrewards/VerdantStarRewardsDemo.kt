package com.ngocdevv.nativecook.blocks.progress.verdantstarrewards

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun VerdantStarRewardsDemo() {
    VerdantStarRewards(modifier = Modifier, autoPlay = true)
}

@Preview
@Composable
private fun VerdantStarRewardsDemoPreview() {
    VerdantStarRewardsDemo()
}
