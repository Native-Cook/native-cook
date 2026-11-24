package com.ngocdevv.nativecook.blocks.modals.morphingcarddialog

// The block's own content: an invented restaurant guide. Photos are credited in the block's notes.
data class MorphingCardDialogFact(val value: String, val label: String)

data class MorphingCardDialogPlace(
    val key: String,
    val name: String,
    val area: String,
    val about: String,
    val facts: List<MorphingCardDialogFact>,
    /** File name under assets/morphingcarddialog/ (a 4:3 photo). */
    val photo: String,
)

internal const val COPY_BRAND = "Forkful"
internal const val COPY_TITLE = "Weekend tables"
internal val COPY_MEALS = listOf("Brunch", "Dinner", "Late")
internal const val COPY_RESERVE = "Reserve a table"

private fun facts(rating: String, next: String, price: String) = listOf(
    MorphingCardDialogFact(rating, "Rating"),
    MorphingCardDialogFact(next, "Next table"),
    MorphingCardDialogFact(price, "Price"),
)

val MorphingCardDialogPlaces = listOf(
    MorphingCardDialogPlace(
        "ember", "Ember & Rye", "Canal Quarter",
        "Squash, peppers and beets roasted over coals, with a short list of natural reds. The counter seats face the fire.",
        facts("4.8", "7:30 PM", "$$$"), "morphingcarddialog_veg.webp",
    ),
    MorphingCardDialogPlace(
        "lume", "Osteria Lume", "Old Mill Row",
        "Hand-cut tagliatelle, slow tomato and a wedge of aged cheese grated at the table. Loud, warm and always full.",
        facts("4.7", "8:15 PM", "$$"), "morphingcarddialog_pasta.webp",
    ),
    MorphingCardDialogPlace(
        "saltwater", "Saltwater", "Harbour Steps",
        "Seared scallops and line-caught fish, plated simply. Ask for a window table when the boats come in.",
        facts("4.9", "6:45 PM", "$$$$"), "morphingcarddialog_scallop.webp",
    ),
    MorphingCardDialogPlace(
        "quay", "Quay Nine", "North Quay",
        "Charred cauliflower, black rice cakes and a view of the water under strings of lights. Good for a long evening.",
        facts("4.6", "9:00 PM", "$$"), "morphingcarddialog_lights.webp",
    ),
    MorphingCardDialogPlace(
        "oak", "Hollow Oak", "Fernhill",
        "Crisp duck leg, herb dumplings and whatever came out of the garden that morning. Twelve tables, one sitting.",
        facts("4.8", "7:00 PM", "$$$"), "morphingcarddialog_duck.webp",
    ),
    MorphingCardDialogPlace(
        "lantern", "Tin Lantern", "Market Lane",
        "Thick pork chops with sweet peppers, cooked in cast iron and carried to the table still spitting.",
        facts("4.5", "8:45 PM", "$$"), "morphingcarddialog_chop.webp",
    ),
)
