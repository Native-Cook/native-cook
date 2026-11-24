package com.ngocdevv.nativecook.blocks.modals.peekpoppreview

import androidx.compose.ui.graphics.Color

// "Margin", its six saved articles and their covers are invented. The covers are drawn in code (CoverArt.kt).

/** A cover shape. Positions and sizes are in units of the cover's height, measured from its centre. */
sealed interface CoverShape {
    val color: Color
    val opacity: Float

    data class Circle(val x: Float, val y: Float, val size: Float, override val color: Color, override val opacity: Float = 1f) : CoverShape
    data class Ring(val x: Float, val y: Float, val size: Float, val stroke: Float, override val color: Color, override val opacity: Float = 1f) : CoverShape
    data class Bar(
        val x: Float,
        val y: Float,
        val width: Float,
        val height: Float,
        val turn: Float = 0f,
        val radius: Float = 0f,
        override val color: Color,
        override val opacity: Float = 1f,
    ) : CoverShape
}

/** Three-stop gradient, top left to bottom right, and the shapes over it. */
data class Cover(val colors: List<Color>, val shapes: List<CoverShape>)

data class PeekArticle(
    val key: String,
    val title: String,
    val source: String,
    val minutes: Int,
    val topic: String,
    /** The first paragraph: what the preview shows. */
    val excerpt: String,
    val body: List<String>,
    val cover: Cover,
)

internal object PeekCopy {
    const val BRAND = "Margin"
    const val TITLE = "Reading list"
    const val EDIT = "Edit"
    const val BACK = "Reading list"
    const val ADD = "Save a link"
}

enum class PeekAction(val label: String, val destructive: Boolean) {
    Save("Save offline", false),
    Share("Share", false),
    Archive("Archive", true),
}

internal val PEEK_ARTICLES: List<PeekArticle> = listOf(
    PeekArticle(
        key = "slow-software",
        title = "The quiet case for slower software",
        source = "Slow Wire",
        minutes = 6,
        topic = "Software",
        excerpt = "For a decade the answer to every product question was to make it faster. Faster to load, faster to ship, faster to forget. A small group of teams has started asking what happens when a tool is allowed to take its time.",
        body = listOf(
            "The first thing they noticed was the support inbox. When an export took four seconds and said so, people waited. When it took one second and failed without a word a tenth of the time, they wrote in. Speed had been standing in for trust, and it was a poor stand-in.",
            "None of this is an argument for spinners. It is an argument for honesty about what a computer is doing on your behalf. A progress bar that tells the truth is slower than one that lies, and almost everyone prefers it once they have seen both.",
            "The teams that slowed down did not lose their users. They lost the ones who had never been sure why they were there, and kept the ones who had work to finish.",
        ),
        cover = Cover(
            colors = listOf(Color(0xFF0E5A4E), Color(0xFF157A63), Color(0xFF2FA07C)),
            shapes = listOf(
                CoverShape.Circle(-0.12f, 0.02f, 0.52f, Color(0xFFF3E9D2)),
                CoverShape.Circle(0.2f, -0.08f, 0.3f, Color(0xFFF28B3C)),
                CoverShape.Ring(0.2f, -0.08f, 0.46f, 0.012f, Color(0xFFF3E9D2), opacity = 0.7f),
                CoverShape.Bar(0.0f, 0.34f, 0.9f, 0.016f, turn = 0.0f, radius = 0.0f, color = Color(0xFFF3E9D2), opacity = 0.55f),
            ),
        ),
    ),
    PeekArticle(
        key = "tide-tables",
        title = "What tide tables taught me about deadlines",
        source = "Harbour Review",
        minutes = 9,
        topic = "Work",
        excerpt = "A tide table is the only schedule I have ever fully believed. It is printed a year ahead, it has never once been renegotiated, and the boats in our harbour plan their whole week around a column of numbers nobody argues with.",
        body = listOf(
            "I started keeping one on my desk the winter our release slipped for the third time. Not as a joke, exactly. I wanted to look at a plan that was allowed to be true.",
            "What the table does well is separate the two things a deadline usually blurs together: when the water will be there, and whether you will be ready for it. The first is fixed. The second is yours. A skipper who misses the tide does not ask the sea for an extension.",
            "We now write two dates on everything. One is the tide, and it does not move. The other is our guess at being ready, and it moves all the time, in the open, where everyone can watch it.",
        ),
        cover = Cover(
            colors = listOf(Color(0xFF12284F), Color(0xFF1D4A8C), Color(0xFF3D7CC9)),
            shapes = listOf(
                CoverShape.Bar(0.0f, -0.2f, 0.42f, 0.07f, turn = 0.0f, radius = 0.035f, color = Color(0xFFF4EFE3)),
                CoverShape.Bar(0.04f, -0.08f, 0.62f, 0.07f, turn = 0.0f, radius = 0.035f, color = Color(0xFF9ADBE8)),
                CoverShape.Bar(-0.03f, 0.04f, 0.5f, 0.07f, turn = 0.0f, radius = 0.035f, color = Color(0xFFF4EFE3), opacity = 0.9f),
                CoverShape.Bar(0.02f, 0.16f, 0.72f, 0.07f, turn = 0.0f, radius = 0.035f, color = Color(0xFF5FB6DD)),
                CoverShape.Circle(0.3f, -0.3f, 0.1f, Color(0xFFFFD166)),
            ),
        ),
    ),
    PeekArticle(
        key = "old-houses",
        title = "A field guide to the sounds of old houses",
        source = "Lamplight",
        minutes = 4,
        topic = "Home",
        excerpt = "Every old house keeps a short vocabulary. A tick in the hallway at dusk, a long sigh from the stairs around two, a knock in the pipes that arrives exactly when the heating does. Learn the words and the house stops being haunted.",
        body = listOf(
            "The tick is timber. Wood that has spent the day warming gives the heat back in the evening, and each board lets go in its own time. It is loudest in autumn, when days and nights disagree the most.",
            "The sigh is the staircase settling under no weight at all. Builders call it creep. It is the sound of a structure that has been carrying a load for ninety years and is still, very slowly, getting comfortable.",
            "The knock deserves a plumber. Everything else deserves a cup of tea and a little patience.",
        ),
        cover = Cover(
            colors = listOf(Color(0xFF7A2E1D), Color(0xFFB4492A), Color(0xFFDE7B45)),
            shapes = listOf(
                CoverShape.Ring(0.0f, 0.06f, 0.26f, 0.035f, Color(0xFFF6E7CF)),
                CoverShape.Ring(0.0f, 0.06f, 0.46f, 0.03f, Color(0xFFF6E7CF), opacity = 0.75f),
                CoverShape.Ring(0.0f, 0.06f, 0.66f, 0.024f, Color(0xFFF6E7CF), opacity = 0.5f),
                CoverShape.Ring(0.0f, 0.06f, 0.86f, 0.018f, Color(0xFFF6E7CF), opacity = 0.28f),
                CoverShape.Circle(0.0f, 0.06f, 0.09f, Color(0xFF2B1710)),
            ),
        ),
    ),
    PeekArticle(
        key = "night-trains",
        title = "Why night trains are having a second life",
        source = "Platform Nine",
        minutes = 12,
        topic = "Travel",
        excerpt = "The sleeper from the coast leaves at 22:14 and arrives, eight hundred kilometres later, in time for breakfast. Ten years ago the line was a week from closing. This summer it sold out every Friday from May to September.",
        body = listOf(
            "Ask the passengers why and nobody mentions speed. They talk about waking up somewhere. A flight gives you two airports and a headache; a night train hands you the journey as a kind of gift and takes none of your day in return.",
            "The economics are stubborn. A carriage that carries sixty seated people carries twenty-four asleep, and it only earns once a night. Operators who survived did it by selling the bed rather than the distance.",
            "What changed was not the trains. It was what people decided an evening was worth.",
        ),
        cover = Cover(
            colors = listOf(Color(0xFF23174F), Color(0xFF4A2A78), Color(0xFF8A3F8C)),
            shapes = listOf(
                CoverShape.Bar(-0.16f, 0.04f, 0.1f, 0.86f, turn = 28.0f, radius = 0.05f, color = Color(0xFFFFD23F)),
                CoverShape.Bar(0.0f, 0.04f, 0.1f, 0.7f, turn = 28.0f, radius = 0.05f, color = Color(0xFFFF7FA8)),
                CoverShape.Bar(0.16f, 0.04f, 0.1f, 0.54f, turn = 28.0f, radius = 0.05f, color = Color(0xFFF4EFE3), opacity = 0.92f),
                CoverShape.Circle(0.3f, -0.26f, 0.13f, Color(0xFFF4EFE3)),
            ),
        ),
    ),
    PeekArticle(
        key = "weather-map",
        title = "The map that put colour on the weather",
        source = "Meridian",
        minutes = 7,
        topic = "Science",
        excerpt = "Before the forecast was a picture it was a paragraph. Somebody read it aloud, you listened for the name of your county, and you guessed. The first printed map with a colour scale changed what people thought weather was.",
        body = listOf(
            "The trick was not the colours. It was the legend: eleven steps from pale to dark, each one a promise that the same shade meant the same thing in every town on the page.",
            "Readers took to it in a season. Within two years farmers were writing in to complain about a single misprinted band, which is the surest sign that a chart has become part of how people think.",
            "We still argue about the palette. Nobody argues about the idea.",
        ),
        cover = Cover(
            colors = listOf(Color(0xFFB8791A), Color(0xFFE19A2B), Color(0xFFF5C453)),
            shapes = listOf(
                CoverShape.Circle(-0.3f, -0.16f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(-0.1f, -0.16f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(0.1f, -0.16f, 0.13f, Color(0xFFFFF4D6)),
                CoverShape.Circle(0.3f, -0.16f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(-0.3f, 0.04f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(-0.1f, 0.04f, 0.13f, Color(0xFFD94F30)),
                CoverShape.Circle(0.1f, 0.04f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(0.3f, 0.04f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(-0.3f, 0.24f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(-0.1f, 0.24f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(0.1f, 0.24f, 0.13f, Color(0xFF3B2A12)),
                CoverShape.Circle(0.3f, 0.24f, 0.13f, Color(0xFFFFF4D6)),
            ),
        ),
    ),
    PeekArticle(
        key = "one-loaf",
        title = "Notes from a year of baking one loaf",
        source = "Crumb",
        minutes = 5,
        topic = "Food",
        excerpt = "I baked the same loaf fifty-two times. Same flour, same tin, same oven that runs twelve degrees hot at the back. I wanted to find out how much there was to learn inside a recipe I already knew by heart.",
        body = listOf(
            "Week nine was the first loaf I would have served to someone. Week twenty was the first I would not have apologised for. Nothing in the recipe had changed; I had simply started to notice the dough.",
            "The lesson is not that repetition makes you good. It is that a fixed recipe turns everything else into a variable you can finally see: the weather, the flour, your own impatience at ten to seven.",
            "I am starting again in January. Same loaf.",
        ),
        cover = Cover(
            colors = listOf(Color(0xFF254D2B), Color(0xFF3F7A3A), Color(0xFF8DB652)),
            shapes = listOf(
                CoverShape.Bar(-0.15f, 0.02f, 0.3f, 0.3f, turn = 0.0f, radius = 0.03f, color = Color(0xFFF2EAD6)),
                CoverShape.Circle(0.15f, 0.02f, 0.3f, Color(0xFFF0B43C)),
                CoverShape.Bar(-0.15f, -0.28f, 0.3f, 0.3f, turn = 0.0f, radius = 0.15f, color = Color(0xFF16301A), opacity = 0.85f),
                CoverShape.Bar(0.15f, 0.32f, 0.3f, 0.3f, turn = 45.0f, radius = 0.03f, color = Color(0xFFF2EAD6), opacity = 0.6f),
            ),
        ),
    ),
)
