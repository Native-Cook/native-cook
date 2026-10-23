package com.ngocdevv.nativecook.blocks.lists.workoutstacklist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun WorkoutStackListDemo() {
    WorkoutStackList(autoPlay = true)
}

@Preview
@Composable
private fun WorkoutStackListDemoPreview() {
    WorkoutStackListDemo()
}
