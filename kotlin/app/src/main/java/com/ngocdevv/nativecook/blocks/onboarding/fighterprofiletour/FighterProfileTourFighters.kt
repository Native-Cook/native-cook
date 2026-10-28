package com.ngocdevv.nativecook.blocks.onboarding.fighterprofiletour

import androidx.annotation.DrawableRes
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Eco
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.LocalFireDepartment
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import com.ngocdevv.nativecook.R

// Port of fighters.ts.

internal enum class FighterKey { Villager, Lucas, Puff, Rosa, Bowser }

internal data class FighterStat(val value: String, val amount: Float)

internal data class Fighter(
    val key: FighterKey,
    val name: String,
    val tier: String,
    val series: String,
    val seriesIcon: ImageVector,
    @DrawableRes val art: Int,
    /** Art size in pixels, for fitting the full figure. */
    val artWidth: Float,
    val artHeight: Float,
    /** The bust a roster card shows: the art's rect inside the 149x112 art box at the card's right. */
    val rowFit: StageRect,
    /** Weight, run speed, dash speed, air speed, fall speed: displayed value and 0-1 bar. */
    val stats: List<FighterStat>,
    /** Matchup metrics Weight, Air, Run, Dash (0-1), compared against the selected fighter. */
    val metrics: List<Float>,
    val counters: Pair<FighterKey, FighterKey>,
    /** Page colour (top, middle, bottom) and the light accent used for icons and secondary text. */
    val page: List<Color>,
    val accent: Color,
)

internal val STAT_LABELS = listOf("Weight", "Run speed", "Dash speed", "Air speed", "Fall speed")

// Ionicons barbell, walk, chevron-forward, paper-plane, arrow-down (nearest Material glyphs).
internal val STAT_ICONS: List<ImageVector> = listOf(
    Icons.Filled.FitnessCenter,
    Icons.AutoMirrored.Filled.DirectionsWalk,
    Icons.AutoMirrored.Filled.KeyboardArrowRight,
    Icons.AutoMirrored.Filled.Send,
    Icons.Filled.ArrowDownward,
)
internal val METRIC_LABELS = listOf("Weight", "Air", "Run", "Dash")

private fun hex(rgb: Long) = Color(0xFF000000L or rgb)

private fun tierNote(tier: String) = when (tier) {
    "B" -> "above average"
    "C" -> "average"
    "D" -> "below average"
    else -> "well below average"
}

internal val FIGHTERS: List<Fighter> = listOf(
    Fighter(
        key = FighterKey.Villager, name = "Villager", tier = "E", series = "Animal Crossing", seriesIcon = Icons.Filled.Eco,
        art = R.drawable.fighterprofiletour_villager, artWidth = 1254f, artHeight = 1254f,
        rowFit = StageRect(-18f, -3f, 157f, 157f),
        stats = listOf(
            FighterStat("92", 0.43f), FighterStat("1.40", 0.30f), FighterStat("1.70", 0.61f),
            FighterStat("0.99", 0.46f), FighterStat("1.42", 0.40f),
        ),
        metrics = listOf(0.43f, 0.46f, 0.30f, 0.61f), counters = FighterKey.Bowser to FighterKey.Puff,
        page = listOf(hex(0x2544c5), hex(0x233fc0), hex(0x213aaf)), accent = hex(0x82b5ff),
    ),
    Fighter(
        key = FighterKey.Lucas, name = "Lucas", tier = "C", series = "EarthBound", seriesIcon = Icons.Filled.MusicNote,
        art = R.drawable.fighterprofiletour_lucas, artWidth = 1254f, artHeight = 1254f,
        rowFit = StageRect(-13f, -2f, 175f, 188f),
        stats = listOf(
            FighterStat("82", 0.38f), FighterStat("1.55", 0.45f), FighterStat("1.82", 0.68f),
            FighterStat("1.03", 0.50f), FighterStat("1.58", 0.52f),
        ),
        metrics = listOf(0.38f, 0.50f, 0.45f, 0.68f), counters = FighterKey.Rosa to FighterKey.Bowser,
        page = listOf(hex(0xc4473b), hex(0xb63e34), hex(0x9e332b)), accent = hex(0xffc0b5),
    ),
    Fighter(
        key = FighterKey.Puff, name = "Jigglypuff", tier = "D", series = "Pokémon", seriesIcon = Icons.Filled.AutoAwesome,
        art = R.drawable.fighterprofiletour_jigglypuff, artWidth = 890f, artHeight = 854f,
        rowFit = StageRect(0f, 0f, 150f, 144f),
        stats = listOf(
            FighterStat("68", 0.12f), FighterStat("1.10", 0.18f), FighterStat("1.43", 0.45f),
            FighterStat("1.35", 1f), FighterStat("0.98", 0.10f),
        ),
        metrics = listOf(0.12f, 1f, 0.18f, 0.45f), counters = FighterKey.Bowser to FighterKey.Lucas,
        page = listOf(hex(0xc24a96), hex(0xb4428a), hex(0x9c3776)), accent = hex(0xffc6ea),
    ),
    Fighter(
        key = FighterKey.Rosa, name = "Rosalina", tier = "D", series = "Super Mario Galaxy", seriesIcon = Icons.Filled.Star,
        art = R.drawable.fighterprofiletour_rosalina, artWidth = 1376f, artHeight = 1606f,
        rowFit = StageRect(-22f, -8f, 193f, 225f),
        stats = listOf(
            FighterStat("82", 0.38f), FighterStat("1.80", 0.62f), FighterStat("2.00", 0.78f),
            FighterStat("1.24", 0.80f), FighterStat("1.17", 0.22f),
        ),
        metrics = listOf(0.38f, 0.80f, 0.62f, 0.78f), counters = FighterKey.Lucas to FighterKey.Villager,
        page = listOf(hex(0x238f9c), hex(0x208491), hex(0x1a707c)), accent = hex(0xa2eef5),
    ),
    Fighter(
        key = FighterKey.Bowser, name = "Bowser", tier = "C", series = "Super Mario", seriesIcon = Icons.Filled.LocalFireDepartment,
        art = R.drawable.fighterprofiletour_bowser, artWidth = 1386f, artHeight = 1384f,
        rowFit = StageRect(-12f, -8f, 174f, 174f),
        stats = listOf(
            FighterStat("135", 1f), FighterStat("1.80", 0.62f), FighterStat("2.05", 0.80f),
            FighterStat("1.10", 0.63f), FighterStat("1.86", 0.75f),
        ),
        metrics = listOf(1f, 0.63f, 0.62f, 0.80f), counters = FighterKey.Puff to FighterKey.Rosa,
        page = listOf(hex(0x2f7f52), hex(0x2a744b), hex(0x226240)), accent = hex(0xaef0c4),
    ),
)

internal val FIGHTER_INDEX: Map<FighterKey, Int> = mapOf(
    FighterKey.Villager to 0, FighterKey.Lucas to 1, FighterKey.Puff to 2, FighterKey.Rosa to 3, FighterKey.Bowser to 4,
)

internal fun fighterSummary(fighter: Fighter): String =
    "Super Smash Bros. Ultimate ${fighter.name} is from the ${fighter.series} series and ranks as " +
        "${if (fighter.tier == "E") "an" else "a"} ${fighter.tier} Tier pick (${tierNote(fighter.tier)})."

/** The full figure, contained in `box` and standing on its bottom edge. */
internal fun standIn(fighter: Fighter, box: StageRect): StageRect {
    val ratio = fighter.artWidth / fighter.artHeight
    val width = minOf(box.width, box.height * ratio)
    val height = width / ratio
    return StageRect(box.x + (box.width - width) / 2f, box.y + box.height - height, width, height)
}

/** The figure contained and centred in `box` (the React Native `containIn`). */
internal fun containIn(fighter: Fighter, box: StageRect): StageRect {
    val ratio = fighter.artWidth / fighter.artHeight
    val width = minOf(box.width, box.height * ratio)
    val height = width / ratio
    return StageRect(box.x + (box.width - width) / 2f, box.y + (box.height - height) / 2f, width, height)
}
