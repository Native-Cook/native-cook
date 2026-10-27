package com.ngocdevv.nativecook.blocks.cards.foliofeaturelibrary

import androidx.compose.ui.graphics.Color

/*
 Content, colours and small value types of the Folio Feature Library, copied from the constants at the
 top of FolioFeatureLibrary.tsx and from its StyleSheet. Every name and sentence is invented.
*/

/** Which artwork a feature card shows (`art` in the React Native `FEATURES`). */
internal enum class FolioArt { BookGreen, Globe, Portrait, Recipe }

/** One card of the Featured carousel; `title` carries its own line break. */
internal class FolioFeature(
    val title: String,
    val name: String,
    val body: String,
    val art: FolioArt,
    val ink: Color,
)

/** A row of Popular or Recently added; `icon` indexes the eight app icons. */
internal class FolioApp(val icon: Int, val name: String, val handle: String, val body: String)

internal enum class FolioScreen { Explore, Loading, Library, Detail }

internal enum class FolioBookId { Rituals, Noticing }

/** A book of the library. */
internal class FolioBook(
    val title: String,
    val author: String,
    val blurb: String,
    val summary: String,
    val lessons: List<String>,
)

/** A rectangle in dp, measured relative to the block's root (React Native `CardFrame`). */
internal data class FolioFrame(val x: Float, val y: Float, val width: Float, val height: Float)

/** Where a library was opened from, so closing it returns to the same frame (`ReturnOrigin`). */
internal sealed interface FolioReturnOrigin {
    data class Feature(val index: Int) : FolioReturnOrigin
    data object Recent : FolioReturnOrigin
    data object Plus : FolioReturnOrigin
}

/** `OpeningOperation`: the loading lens runs from [startedAt] for [duration] seconds. */
internal class FolioOpening(val startedAt: Double, val duration: Double) {
    val deadline: Double get() = startedAt + duration
}

/** `GenerationOperation`: a book is being created until [deadline]; its cover follows [coverDelay] later. */
internal class FolioGeneration(
    val deadline: Double,
    val topics: List<String>,
    val notifyHost: Boolean,
    val coverDelay: Double,
)

/** Window size, safe-area insets (dp) and font scale, the numbers React Native reads from hooks. */
internal data class FolioMetrics(
    val width: Float,
    val height: Float,
    val top: Float,
    val bottom: Float,
    val fontScale: Float,
) {
    /** `cardWidth = Math.min(248, width * 0.63)`. */
    val cardWidth: Float get() = minOf(248f, width * 0.63f)
}

internal const val FOLIO_AUTHOR_DELAY = 4.4
internal const val FOLIO_COLLAPSED_TILE = 76f

internal val FolioFeatures = listOf(
    FolioFeature(
        title = "Non-fiction\nsummaries",
        name = "Non-fiction summaries",
        body = "This app gives you detailed outlines of popular non-fiction books to help you learn various topics easily.",
        art = FolioArt.BookGreen,
        ink = Color(0xFF0B4A2E),
    ),
    FolioFeature(
        title = "Places to\nremember",
        name = "Places to remember",
        body = "Keep track of places you have been and dream of visiting with beautiful cards.",
        art = FolioArt.Globe,
        ink = Color(0xFF0D3A73),
    ),
    FolioFeature(
        title = "Portrait\nstudio",
        name = "Portrait studio",
        body = "Turn your photos into hand-drawn illustrated portraits in a few seconds.",
        art = FolioArt.Portrait,
        ink = Color(0xFF0B5A5A),
    ),
    FolioFeature(
        title = "Pantry\nrecipes",
        name = "Pantry recipes",
        body = "Snap your fridge and get recipes you can make tonight.",
        art = FolioArt.Recipe,
        ink = Color(0xFF7A3B12),
    ),
)

internal val FolioCategories = listOf("Lifestyle", "Health & Fitness", "Mindfulness", "Cooking", "Learning")

internal val FolioApps = listOf(
    FolioApp(0, "Weight room tracker", "@kaito", "Track your workouts, log sets, and make every session count."),
    FolioApp(1, "Clear air today", "@priya", "Get real-time air quality updates for the streets you walk."),
    FolioApp(2, "Stretch break coach", "@dani", "Get daily stretch routines tailored to your day."),
    FolioApp(3, "Interval timer", "@unai", "Accurate intervals for runs, rows and rests."),
    FolioApp(4, "Rep counter", "@noor", "Log your lifts, sets, and weights effortlessly."),
)

internal val FolioRecent = listOf(
    FolioApp(5, "Daily spend tracker", "@moonlit", "Quickly log and manage your daily expenses with ease."),
    FolioApp(6, "Fridge planner", "@awa", "Plan meals around what is already waiting in your fridge."),
    FolioApp(7, "Liner notes", "@kasra", "Keep a note on every album you fall in love with."),
    FolioApp(3, "Night sky log", "@ines", "Note the stars you found and the clouds that hid them."),
    FolioApp(0, "Plant days", "@theo", "Gentle reminders for every pot on the windowsill."),
)

internal val FolioTopics = listOf(
    "Family", "Productivity", "Mindfulness", "Career", "Leadership", "Communication",
    "Psychology", "Parenting", "Marketing", "Design", "Art",
)

/** A first pick widens the list, as the reference does, so the sheet suggests neighbouring ideas. */
internal val FolioMoreTopics = listOf(
    "Personal development", "Entrepreneurship", "Financial literacy", "Emotional intelligence", "Innovation",
)

internal fun folioBook(id: FolioBookId): FolioBook = when (id) {
    FolioBookId.Rituals -> FolioRituals
    FolioBookId.Noticing -> FolioNoticing
}

private val FolioRituals = FolioBook(
    title = "Small Rituals",
    author = "Mira Wells",
    blurb = "A practical guide to building good habits, breaking bad ones, and mastering the small, consistent actions that lead to remarkable results.",
    summary = "“Small Rituals” is a gentle, practical guide to changing a day by changing its smallest moments. Mira Wells shows how a walk without your phone, a page before breakfast or one promise you can keep quietly adds up, why the repeatable step matters more than the impressive one, and how to shape your rooms, your evenings and your calendar so the good choice becomes the easy one. It draws on habit research, design and plain everyday experience to make lasting change feel lighter, kinder and entirely within reach.",
    lessons = listOf(
        "Start with a moment you already have: tie each new ritual to something you do every day, so it never needs a reminder.",
        "Make the next step small enough to repeat, even on a tired evening; consistency carries further than intensity.",
        "Shape the room, not just the will: put the book on the pillow and the phone in another room.",
        "Notice what feels better, not just what gets done, and let that feeling choose what you keep.",
    ),
)

private val FolioNoticing = FolioBook(
    title = "A Brief Tour of Almost Everything",
    author = "Theo Marsh",
    blurb = "A curious walk through science, from the first stars to the dinosaurs and the people who worked it all out.",
    summary = "A familiar street still has something to show you. Look for a new color, listen for a quiet sound, or ask a better question. This short read explores how paying attention can turn an ordinary day into a small discovery.",
    lessons = listOf(
        "Look at a familiar place with fresh eyes.",
        "Leave a little space for the unexpected.",
        "Keep one small observation from each day.",
    ),
)

// Colours of the StyleSheet (hex as written there; `#RRGGBBAA` becomes ARGB).
internal val FolioScreenBg = Color(0xFFEFEFEF)
internal val FolioSurfaceBg = Color(0xFFF6F6F6)
internal val FolioInk = Color(0xFF111111)
internal val FolioInkSoft = Color(0xFF555555)
internal val FolioInkBody = Color(0xFF3A3A3A)
internal val FolioInkText = Color(0xFF2A2A2A)
internal val FolioInkBar = Color(0xFF1F1F1F)
internal val FolioWhite = Color(0xFFFFFFFF)
internal val FolioGreen = Color(0xFF13775E)
internal val FolioGreenBusy = Color(0xFF7FBFAE)
internal val FolioBlue = Color(0xFF1E6FEF)
internal val FolioDark = Color(0xFF2E2E2E)
internal val FolioMuted = Color(0xFF8A8A8A)
