package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.model

import androidx.annotation.DrawableRes

sealed interface GalleryItem {
    val id: String

    data class Owned(
        val cardId: String,
        val count: Int,
        override val id: String,
        @DrawableRes val image: Int,
        val name: String,
    ) : GalleryItem

    data class Locked(
        override val id: String,
        val lockCount: Int,
    ) : GalleryItem
}
