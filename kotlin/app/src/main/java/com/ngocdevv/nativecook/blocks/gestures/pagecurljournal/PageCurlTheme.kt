package com.ngocdevv.nativecook.blocks.gestures.pagecurljournal

import androidx.compose.ui.graphics.Color

// Page Curl Journal: every colour, size and position. Positions are points on the 402 x 874 reference
// screen, measured from the source recording and scaled with the screen width.

internal object REF {
    const val WIDTH = 402f
    const val HEIGHT = 874f
}

/** The edges of the pages still to turn, seen at the right edge of the book. */
internal val STACK_INK = Color(0xFFD9D9DD)

internal object INK {
    val paper = Color(0xFFFFFFFF)
    val text = Color(0xFF000000)
    val eyebrow = Color(0xFF6E6E73)
    val mute = Color(0xFF636367)
    val footnote = Color(0xFF727276)
    val author = Color(0xFF8E8E92)
    val caption = Color(0xFF7A7A7E)
    val iconLine = Color(0xFFE3E3E6)
}

/** One text line or block: top of the line box, font size, weight, line height. */
internal class Line(val top: Float, val size: Float, val weight: Int, val line: Float)

internal object TYPE {
    val eyebrow = Line(104.3f, 13.6f, 500, 18f)
    val brand = Line(124.6f, 30.0f, 800, 37f)
    val title = Line(129.1f, 25.7f, 700, 31f)
    val intro = Line(586.2f, 14.1f, 400, 20f)
    val hint = Line(816.2f, 14.2f, 500, 18f)
    val folio = Line(816.2f, 14f, 400, 18f)
    val rowTitle = Line(0f, 14.7f, 600, 18f)
    val rowBody = Line(0f, 13.6f, 400, 17f)
    val footnote = Line(712.7f, 11.2f, 400, 13.7f)
    val body = Line(273f, 14.1f, 400, 19.7f)
    val cover = Line(214.2f, 32.3f, 700, 37.3f)
    val caption = Line(416.4f, 9.9f, 600, 12f)
    val author = Line(444.9f, 26.1f, 700, 31f)
}

/** Source spacing scale (20 Oct); measured positions above remain authoritative. */
internal object SPACE {
    const val XS = 4f
    const val SM = 8f
    const val MD = 12f
    const val LG = 16f
    const val XL = 24f
    const val XXL = 32f
    const val BODY = 48f
}

internal object LAYOUT {
    // The mark: left, top, size, corner radius.
    const val MARK_LEFT = 145f
    const val MARK_TOP = 314.7f
    const val MARK_SIZE = 112.6f
    const val MARK_RADIUS = 25.5f

    const val BODY_LEFT = 47.8f

    // The two feature rows on the preface.
    const val ROW_ICON_X = 62f
    const val ROW_TEXT_LEFT = 101.5f
    val ROW_TITLE_TOP = floatArrayOf(313.1f, 402.8f)
    const val ROW_ICON = 30.5f
    val ROW_ICON_Y = floatArrayOf(341.7f, 431f)
    const val ROW_GAP = 3.4f

    const val SHIELD_TOP = 689f
    const val SHIELD_SIZE = 15.5f

    const val HINT_ARROW_SIZE = 13.5f
    const val HINT_ARROW_GAP = 8f

    // The cover's coloured sparkle: centre and size.
    const val SPARKLE_CX = 201.5f
    const val SPARKLE_CY = 154f
    const val SPARKLE_SIZE = 47f
}

/** The invented brand. The source's wordmark dots its i's with sparkles; ours does the same. */
internal object BRAND {
    const val NAME = "Ritualbook"
    const val MARK = "Ri"
}

internal class Row(val title: String, val body: String)

internal object COPY {
    const val MEET = "Meet"
    const val INTRO = "Your personal ‘Great Book of Habits’, written\nand completed by you, one day at a time."
    const val HINT = "Turn the page"
    const val PREFACE = "Preface"
    const val PREFACE_TITLE = "Your Data Is Yours"
    val ROWS = listOf(
        Row("Track Habits", "Feel proud of every small step you take\ntoward your goals."),
        Row("Review Records", "Learn from your setbacks and your wins\nas the weeks go by."),
    )
    const val FOOTNOTE = "Everything you write stays private on this iPhone.\nNo servers, no tracking, no account, no ads."
    const val FOREWORD = "Foreword"
    const val FOREWORD_TITLE = "How It Works"
    const val BODY =
        "Every day, your book automatically adds\none fresh page. It’s up to you to make the\nmost of it.\n\n" +
            "Missed a day? Don’t worry about it. You can\nalways turn back a page and fill in\nany blanks as you go.\n\n" +
            "Every time you check in on your daily habits,\nyou move a step closer to finishing all the\nchapters of your own unfolding story.\n\n" +
            "Now, before your book finds its rightful\nplace on your home screen, all that’s left is\nto make the commitment to the journey\nahead..."
    const val COVER = "The Great Book\nof Habits"
    const val CAPTION = "TO BE WRITTEN BY"
    const val AUTHOR = "Untitled Author"
    val PAGES = listOf("Title page", "Preface", "Foreword", "Cover")
}

/** Icon colours, read from the source frames. */
internal object ICON {
    val check = listOf(Color(0xFF6FB6F5), Color(0xFF8C8DF0), Color(0xFFB08BEF))
    val bars = listOf(Color(0xFF79A6F2), Color(0xFF9B8CEE))
    val barsBase = Color(0xFFB9A5F2)
    val sparkle = listOf(Color(0xFFFFC83D), Color(0xFF3FA0FF), Color(0xFF8A63F0), Color(0xFFFF7A8A), Color(0xFFFFC83D))
    val markInk = Color(0xFF141414)
    val curlDark = Color(0xFF8F8F93)
    val curlMid = Color(0xFFC9C9CC)
    val curlLight = Color(0xFFF2F2F4)
}

/**
 * Page-curl shading. Fitted against the source's mid-turn frame with the numpy twin of the shader
 * on our own pages: edge mean 3.69 px, p90 9.55, colour dE 2.32.
 */
internal object CURL {
    const val RADIUS = 22.77f // roll radius where the fold meets the grabbed corner, pt
    const val RADIUS_SLOPE = 0.01f // the roll widens along the fold (a cone, as on iOS)
    const val RADIUS_MIN = 1f
    const val SHADOW = 0.413f // cast shadow on the page beneath, darkest at the roll
    const val SHADOW_WIDTH = 1.6f
    const val EDGE_SHADOW = 0.334f // contact shadow under the lying flap's edge
    const val EDGE_SHADOW_WIDTH = 3.62f
    const val FRONT_LIGHT = 0.793f // ambient on the rising front of the roll
    const val BACK_LIGHT = 0.414f // ambient on the back of the roll, the fold's dark line
    const val BACK_GAMMA = 0.455f
    const val BACK_PAPER = 0.962f // the back side of the sheet
    const val SHOW_THROUGH = 0.218f // how much of the printed front reads through the back
    const val GRAIN = 0.018f // paper grain on the back, a seeded hash per device pixel
    const val EDGE_INK = 0.08f // the sheet's cut edge reads as thickness
    const val SHEEN = 0.06f // soft specular band on the roll where it faces the key light
}
