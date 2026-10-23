package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.model


enum class CardFinish(val key: String) {
    RareHolo("rare-holo"),
    DoubleRare("double-rare"),
    SpecialIllustration("special-illustration"),
    HyperRare("hyper-rare"),
}

enum class CardStage(val label: String) {
    Basic("BASIC"),
    Stage1("STAGE 1"),
    Stage2("STAGE 2"),
}

data class CardAttack(
    val name: String,
    val damage: Int,
    val cost: Int,
    val description: String,
)

data class CardAbility(
    val name: String,
    val description: String,
)

data class DemoCard(
    val id: String,
    val number: String,
    val name: String,
    val hp: Int,
    val element: String,
    val rarity: String,
    val finish: CardFinish,
    val description: String,
    val accent: String,
    val accentSoft: String,
    val stage: CardStage,
    val evolvesFrom: String? = null,
    val category: String,
    val height: String,
    val weight: String,
    val ability: CardAbility? = null,
    val attacks: List<CardAttack>,
    val weakness: String,
    val resistance: String,
    val retreat: Int,
    val flavorText: String,
    val illustrator: String,
    val setCode: String,
    val elementSymbol: String,
)
