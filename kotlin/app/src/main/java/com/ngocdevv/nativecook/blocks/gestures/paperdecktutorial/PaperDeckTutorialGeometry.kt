package com.ngocdevv.nativecook.blocks.gestures.paperdecktutorial

/**
 * The sizes the deck is drawn at, in dp, from the window as the React Native file works them out.
 * [width] and [height] are the card (`min(0.9 * window, 410)` wide), [screenHeight] the window's height
 * (the last card flies up by a fraction of it) and [introTravel] how far below its place the cover
 * starts (`screenHeight * 0.125`).
 */
internal class PaperDeckTutorialGeometry(
    val width: Double,
    val height: Double,
    val screenHeight: Double,
) {
    val introTravel: Double = screenHeight * 0.125
}
