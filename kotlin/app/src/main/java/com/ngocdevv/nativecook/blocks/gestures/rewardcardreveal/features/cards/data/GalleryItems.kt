package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.data

import com.ngocdevv.nativecook.R
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.model.GalleryItem

val galleryItems: List<GalleryItem> = listOf(
    GalleryItem.Owned(
        cardId = "verdant-sproutling",
        count = 1,
        id = "the-awakening",
        image = R.drawable.rewardcardreveal_the_awakening,
        name = "The Awakening",
    ),
    GalleryItem.Locked(
        id = "locked-awakening-set",
        lockCount = 3,
    ),
    GalleryItem.Owned(
        cardId = "tide-fox",
        count = 1,
        id = "wandering-mind",
        image = R.drawable.rewardcardreveal_wandering_mind,
        name = "Wandering Mind",
    ),
    GalleryItem.Locked(
        id = "locked-wandering-set",
        lockCount = 2,
    ),
)
