package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget

// Money stays in integer cents through previews, landings and receipt changes.
internal data class AuraReceipt(val id: Int = 0, val friend: Int = -1, val cents: Long = 6000)
