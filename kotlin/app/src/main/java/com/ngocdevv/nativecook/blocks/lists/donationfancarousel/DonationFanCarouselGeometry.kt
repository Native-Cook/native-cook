package com.ngocdevv.nativecook.blocks.lists.donationfancarousel

import com.ngocdevv.nativecook.blocks.lists.donationfancarousel.DonationFanCarouselTheme.Layout

/**
 * What `DonationFanCarousel` computes from `useWindowDimensions()`: the stage that is centred on
 * the window, the scale that maps the 402 dp reference onto it and the active portrait's size.
 * Every length is in dp.
 */
internal class DonationFanCarouselGeometry(windowWidth: Float, windowHeight: Float) {
    /** `const stageWidth = width - 16`. */
    val stageWidth = windowWidth - Layout.StageInset

    /** `const stageHeight = (462 / 748) * stageWidth`. */
    val stageHeight = (Layout.StageAspectHeight / Layout.SourceWidth) * stageWidth

    /** `const scale = stageWidth / 402`. */
    val scale = stageWidth / Layout.ReferenceWidth

    /** `const size = layout.deck.card * scale`. */
    val cardSize = Layout.Deck.Card * scale

    /** `left: (width - stageWidth) / 2` of `styles.stage`. */
    val stageLeft = (windowWidth - stageWidth) / 2

    /** `top: (height - stageHeight) / 2` of `styles.stage`. */
    val stageTop = (windowHeight - stageHeight) / 2

    /** `left: layout.deck.centerX * scale - size / 2`: where the active portrait's box starts. */
    val deckLeft = Layout.Deck.CenterX * scale - cardSize / 2

    /** `top: layout.deck.centerY * scale - size / 2`. */
    val deckTop = Layout.Deck.CenterY * scale - cardSize / 2

    /** `borderRadius: layout.deck.radius * scale`. */
    val cardRadius = Layout.Deck.Radius * scale
}
