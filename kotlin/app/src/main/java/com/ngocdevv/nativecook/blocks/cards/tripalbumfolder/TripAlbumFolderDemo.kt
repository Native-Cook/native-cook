package com.ngocdevv.nativecook.blocks.cards.tripalbumfolder

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.shared.ComponentStage

@Composable
fun TripAlbumFolderDemo() {
    ComponentStage(background = Color(0xFFF5F5F6), height = 440.dp) { TripAlbumFolder(autoPlay = true) }
}
