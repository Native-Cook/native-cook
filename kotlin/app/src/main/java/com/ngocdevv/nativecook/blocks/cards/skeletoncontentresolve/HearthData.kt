package com.ngocdevv.nativecook.blocks.cards.skeletoncontentresolve

import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// The two feeds of Hearth, an invented podcast app. A refresh swaps one for the other.

internal enum class CoverMotif { Sun, Rings, Bars, Stripes, Dots, Arc, Steps }

@Immutable
internal data class CoverSpec(val motif: CoverMotif, val g0: Long, val g1: Long, val g2: Long, val ink: Long, val accent: Long) {
    val ground get() = listOf(Color(g0), Color(g1), Color(g2))
    val inkColor get() = Color(ink)
    val accentColor get() = Color(accent)
}

@Immutable
internal data class Episode(val title: String, val show: String, val minutes: Int, val day: String, val cover: CoverSpec)

@Immutable
internal data class Feed(
    val eyebrow: String,
    val titleA: String,
    val titleB: String,
    val show: String,
    val minutes: Int,
    val cover: CoverSpec,
    val episodes: List<Episode>,
)

internal val FEEDS = listOf(
    Feed(
        "New this week", "The Long Way", "Round the Coast", "Salt & Signal", 42,
        CoverSpec(CoverMotif.Sun, 0xFFF2B36B, 0xFFE9824A, 0xFFB4472F, 0xFF2C2233, 0xFFFBE3B0),
        listOf(
            Episode("How Bread Learned to Rise", "Crumb Theory", 31, "Today", CoverSpec(CoverMotif.Bars, 0xFFE8DCC4, 0xFFD9C7A3, 0xFFC6AE84, 0xFF5A3D24, 0xFFB8552F)),
            Episode("Field Guide to Night Trains", "Slow Departures", 54, "Mon", CoverSpec(CoverMotif.Rings, 0xFF274046, 0xFF1E3138, 0xFF15232A, 0xFF7FB6A8, 0xFFF0C987)),
            Episode("The Map That Was Wrong", "Paper Coasts", 27, "Sun", CoverSpec(CoverMotif.Stripes, 0xFFD7E0D2, 0xFFB9CDB8, 0xFF93B19B, 0xFF2F4A43, 0xFFF3EAD7)),
            Episode("Notes From a Quiet Kitchen", "Hearthside", 38, "Fri", CoverSpec(CoverMotif.Arc, 0xFF3A2430, 0xFF532B33, 0xFF7A3A32, 0xFFF08A4B, 0xFFF6D7A6)),
        ),
    ),
    Feed(
        "Fresh for you", "Small Hours,", "Big Weather", "Overcast Club", 36,
        CoverSpec(CoverMotif.Rings, 0xFF31405F, 0xFF27324D, 0xFF1A2136, 0xFF9FB4E8, 0xFFF2C36B),
        listOf(
            Episode("Why Rivers Bend", "Loose Gravel", 44, "Today", CoverSpec(CoverMotif.Steps, 0xFFCFE0DC, 0xFFA9C9C4, 0xFF7FADA9, 0xFF1F4A4C, 0xFFF5EFE0)),
            Episode("Last Orchard on the Line", "Branch Lines", 58, "Today", CoverSpec(CoverMotif.Dots, 0xFFF0D9A8, 0xFFE6BF78, 0xFFD99F52, 0xFF6B3A1D, 0xFFFFF3D6)),
            Episode("Letters to a Lost Umbrella", "Odd Post", 22, "Tue", CoverSpec(CoverMotif.Sun, 0xFFE6C9D0, 0xFFCF9FB0, 0xFFA86D8A, 0xFF3C2340, 0xFFFBE9D2)),
            Episode("Tuning a House by Ear", "Room Tone", 49, "Sat", CoverSpec(CoverMotif.Bars, 0xFF2B2A3F, 0xFF39355A, 0xFF4D4378, 0xFFB9A8F0, 0xFFF2C9A0)),
        ),
    ),
)

internal val NOW_PLAYING = CoverSpec(CoverMotif.Dots, 0xFF20424A, 0xFF17323A, 0xFF10242B, 0xFF8FD0C4, 0xFFF0C987)

/** Bar widths while loading, dp: fixed per slot, so the skeleton never shows what is coming. */
internal object Placeholder {
    const val EYEBROW = 88f
    const val TITLE_A = 132f
    const val TITLE_B = 164f
    const val META = 124f
    const val PLAY = 86f
    val ROW_TITLE = floatArrayOf(172f, 204f, 150f, 186f)
    val ROW_META = floatArrayOf(118f, 96f, 132f, 104f)
    const val DAY = 34f
}
