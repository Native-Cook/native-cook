package com.ngocdevv.nativecook.blocks.progress.pourjargoal

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

/** The loop opens on $2,240 of $3,100 (72 %), pours $100 and drains back. */
@Composable
fun PourJarGoalDemo() {
    PourJarGoal(autoPlay = true)
}

@Preview
@Composable
private fun PourJarGoalDemoPreview() {
    PourJarGoalDemo()
}
