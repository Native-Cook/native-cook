package com.ngocdevv.nativecook.blocks.gestures.boosterpackopening

/** How rare a card is. It picks the sound a card arrives with. */
internal enum class CardRarity(
    val cue: String,
) {
    Common("common"),
    Rare("rare"),
    Epic("epic"),
}

/** The rarities a pack holds, once each: only their sounds are loaded. */
internal fun cardAudioCues(rarities: List<CardRarity>): List<CardRarity> = rarities.distinct()
