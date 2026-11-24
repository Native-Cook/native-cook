package com.ngocdevv.nativecook.blocks.modals.coachmarkspotlight

import androidx.compose.ui.graphics.Color

// Penny, a budgeting app. Names, places and amounts are invented.

data class CoachStep(val title: String, val body: String)

/** One step per highlighted element, in tour order: the add button, the month card, the Insights tab. */
val STEPS = listOf(
    CoachStep("Log a spend in seconds", "Tap + after you pay. Penny picks the category, you just check the amount."),
    CoachStep("Your month at a glance", "See what’s left before payday. It updates every time you log a spend."),
    CoachStep("Find where it goes", "Insights breaks your spending down by week, so surprises show up early."),
)

internal class Category(val name: String, val spent: Float, val color: Color)

internal val CATEGORIES = listOf(
    Category("Groceries", 412.3f, Color(0xFF3E8E6A)),
    Category("Eating out", 286.9f, Color(0xFFE58A5B)),
    Category("Transport", 164.0f, Color(0xFF5B8BD9)),
    Category("Home", 421.4f, Color(0xFFC9A23E)),
)

internal const val BUDGET = 2000f

internal enum class SpendGlyph { Cart, Cup, Train, Leaf }

internal class Spend(val place: String, val category: String, val amount: Float, val time: String, val color: Color, val glyph: SpendGlyph)

internal val RECENT = listOf(
    Spend("Greenway Market", "Groceries", 38.45f, "Today, 6:12 PM", Color(0xFF3E8E6A), SpendGlyph.Cart),
    Spend("Little Fern Café", "Eating out", 6.8f, "Today, 8:41 AM", Color(0xFFE58A5B), SpendGlyph.Cup),
    Spend("Metro card top-up", "Transport", 25.0f, "Yesterday", Color(0xFF5B8BD9), SpendGlyph.Train),
    Spend("Hollis Plant Co.", "Home", 19.9f, "Sun, Nov 22", Color(0xFFC9A23E), SpendGlyph.Leaf),
)

internal val TABS = listOf("Home", "Budgets", "Insights", "Profile")
/** The tab the tour points at. */
internal const val TOUR_TAB = 2
