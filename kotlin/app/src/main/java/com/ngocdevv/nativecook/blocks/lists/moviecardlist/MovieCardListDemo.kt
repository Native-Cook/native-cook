package com.ngocdevv.nativecook.blocks.lists.moviecardlist

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview

@Composable
fun MovieCardListDemo() {
    MovieCardList(autoPlay = true)
}

@Preview
@Composable
private fun MovieCardListDemoPreview() {
    MovieCardListDemo()
}
