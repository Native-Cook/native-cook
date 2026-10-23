package com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.theme

import androidx.compose.ui.graphics.Color
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.features.cards.model.CardFinish
import com.ngocdevv.nativecook.blocks.gestures.rewardcardreveal.shared.graphics.cssColor

const val CARD_ASPECT_RATIO = 63f / 88f
const val CARD_HEIGHT_RATIO = 88f / 63f

object CollectionCardBackColors {
    val background = cssColor("#e5e9ef")
    val patternDark = cssColor("#cfd5df")
    val edge = cssColor("rgba(255, 255, 255, 0.76)")
    val edgeInner = cssColor("rgba(255, 255, 255, 0.48)")
    val seal = cssColor("#737b89")
    val markerBorder = cssColor("rgba(105, 115, 132, 0.19)")
    val markerInk = cssColor("rgba(105, 115, 132, 0.22)")
}

object CollectionGalleryColors {
    val background = cssColor("#e9edf3")
    val text = cssColor("#202631")
}

object CollectionCardBackdropColors {
    val background: Color = CollectionCardBackColors.background
    val pattern = cssColor("#b9c3cf")
    val seal: Color = CollectionCardBackColors.seal
}

data class CardTemplatePalette(
    val frame: Color,
    val frameLight: Color,
    val header: Color,
    val panel: Color,
    val panelDeep: Color,
    val ink: Color,
    val mutedInk: Color,
    val artBorder: Color,
    val energy: Color,
    val energyInk: Color,
)

val CARD_TEMPLATE_PALETTES: Map<CardFinish, CardTemplatePalette> = mapOf(
    CardFinish.RareHolo to CardTemplatePalette(
        frame = cssColor("#b6b7b2"),
        frameLight = cssColor("#f1eee6"),
        header = cssColor("#ef7e55"),
        panel = cssColor("#efaa7b"),
        panelDeep = cssColor("#d45f3f"),
        ink = cssColor("#17140f"),
        mutedInk = cssColor("#4f342b"),
        artBorder = cssColor("#7d3c2b"),
        energy = cssColor("#d85333"),
        energyInk = cssColor("#fff2df"),
    ),
    CardFinish.DoubleRare to CardTemplatePalette(
        frame = cssColor("#b7bab4"),
        frameLight = cssColor("#f5f2e8"),
        header = cssColor("#b5d74c"),
        panel = cssColor("#d6ea85"),
        panelDeep = cssColor("#8bad3e"),
        ink = cssColor("#171b0d"),
        mutedInk = cssColor("#3f4d24"),
        artBorder = cssColor("#59752d"),
        energy = cssColor("#356b32"),
        energyInk = cssColor("#f4ffe0"),
    ),
    CardFinish.SpecialIllustration to CardTemplatePalette(
        frame = cssColor("#c5d2da"),
        frameLight = cssColor("#f7fbfc"),
        header = cssColor("#75c4e4"),
        panel = cssColor("#a9dded"),
        panelDeep = cssColor("#4b9ec7"),
        ink = cssColor("#10202b"),
        mutedInk = cssColor("#315467"),
        artBorder = cssColor("#3a7295"),
        energy = cssColor("#2f83b0"),
        energyInk = cssColor("#effcff"),
    ),
    CardFinish.HyperRare to CardTemplatePalette(
        frame = cssColor("#b88832"),
        frameLight = cssColor("#fff0a8"),
        header = cssColor("#d9a441"),
        panel = cssColor("#edcd75"),
        panelDeep = cssColor("#a97222"),
        ink = cssColor("#23170a"),
        mutedInk = cssColor("#5e421a"),
        artBorder = cssColor("#83551a"),
        energy = cssColor("#4f2d72"),
        energyInk = cssColor("#fff2bc"),
    ),
)

fun CardFinish.palette(): CardTemplatePalette = CARD_TEMPLATE_PALETTES.getValue(this)
