package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import androidx.compose.ui.graphics.Color

// Page Curl Journal: every colour, size and position. Positions are points on the 402 x 874 reference
// screen and scale with the screen width, exactly like the React Native reference.

internal object REF {
    const val WIDTH = 402f
    const val HEIGHT = 874f
}

internal object PageCurlJournalAssets {
    const val ROOT = "pagecurljournal"
    const val ROMAN = "$ROOT/pagecurljournal_Newsreader.ttf"
    const val ITALIC = "$ROOT/pagecurljournal_NewsreaderItalic.ttf"
    const val PAPER = "$ROOT/pagecurljournal_paper.jpg"
}

/** The edges of the pages still to turn: the paper's own edge, a shade darker. */
internal val STACK_INK = Color(0xFFD8CDB8)

/** Ivory stock, warm near-black ink and one muted oxblood for the ornaments. */
internal object PAPER {
    val base = Color(0xFFF6F0E3)
    val ink = Color(0xFF2B241C)
    val mute = Color(0xFF7C6E5B)
    val rule = Color(0xFFCDBFA8)
    val accent = Color(0xFF8A3B2E)
    val foil = Color(0xFF6B4E1F)
}

/** The page furniture on the reference: margins, running head, folio and binding shade. */
internal object BOOK {
    const val MARGIN = 48f
    const val HEAD = 92f
    const val FOLIO = 806f
    const val GUTTER = 30f
}

/** Only the hint position is read by the gesture engine. */
internal object TYPE {
    const val HINT_TOP = 806f
}

/** Four-point spacing scale. */
internal object SPACE {
    const val XS = 4f
    const val SM = 8f
    const val MD = 12f
    const val LG = 16f
    const val XL = 24f
    const val XXL = 32f
    const val BODY = 48f
}

/** The invented brand and every line printed in the journal. */
internal object COPY {
    data class Row(val title: String, val body: String)

    const val PRESENTS = "Ritualbook presents"
    const val TITLE = "The Great Book\nof Habits"
    const val SUBTITLE = "a journal written one day at a time"
    const val SEAL_TOP = "VOL. I"
    const val SEAL_MID = "MMXXVI"
    const val SEAL_BOTTOM = "RITUALBOOK"
    const val IMPRINT = "RITUALBOOK  ·  FIRST EDITION"
    const val HINT = "turn the page"
    const val HEAD_PREFACE = "THE GREAT BOOK OF HABITS"
    const val HEAD_FOREWORD = "HOW IT WORKS"
    const val PREFACE = "PREFACE"
    const val PREFACE_TITLE = "Your Data Is Yours"
    val ROWS = listOf(
        Row(
            "Track habits",
            "Mark the small things you do each day and watch them add up. A tick is enough; a line of notes is better. " +
                "Nothing is graded, and nothing is lost when you miss.",
        ),
        Row(
            "Review records",
            "Every Sunday the book turns back to the week behind you: what held, what slipped, and the one habit worth " +
                "carrying into the next.",
        ),
    )
    const val FOOTNOTE = "* Everything you write stays on this iPhone. No servers, no tracking, no account, no ads."
    const val FOREWORD = "FOREWORD"
    const val FOREWORD_TITLE = "How It Works"
    val BODY = listOf(
        " your book adds one fresh page. It is yours to fill, a line or a whole page, and it waits patiently for you if you are late.",
        "Missed a day? Turn back a page and fill in the blank; nobody is keeping score but you.",
        "Each check-in moves you a step closer to the last chapter of a story that only you can write.",
    )
    const val BODY_LEAD = "EVERY MORNING"
    const val SIGNOFF = "with care, the makers of Ritualbook"
    const val BELONGS = "THIS BOOK BELONGS TO"
    const val AUTHOR = "Untitled Author"
    const val BEGUN = "begun on the 23rd of October"
    val PAGES = listOf("Title page", "Preface", "Foreword", "Ownership page")
}

/** Page-curl shading, fitted to the approved React Native reference. */
internal object CURL {
    const val RADIUS = 22.77f
    const val RADIUS_SLOPE = 0.01f
    const val RADIUS_MIN = 1f
    const val SHADOW = 0.413f
    const val SHADOW_WIDTH = 1.6f
    const val EDGE_SHADOW = 0.334f
    const val EDGE_SHADOW_WIDTH = 3.62f
    const val FRONT_LIGHT = 0.793f
    const val BACK_LIGHT = 0.414f
    const val BACK_GAMMA = 0.455f
    const val BACK_PAPER = 0.962f
    const val SHOW_THROUGH = 0.218f
    const val GRAIN = 0.018f
    const val EDGE_INK = 0.08f
    const val SHEEN = 0.06f
}
