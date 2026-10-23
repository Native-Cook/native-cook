package com.ngocdevv.nativecook.blocks.lists.foryouinsights

data class ForYouInsightsArticleSection(
    val heading: String,
    val body: String,
)

data class ForYouInsightsArticle(
    val key: String,
    val title: String,
    val summary: String,
    val readTime: String,
    val sections: List<ForYouInsightsArticleSection>,
    val depths: List<Double>,
)

data class ForYouInsightsHabit(
    val icon: ForYouInsightsHabitIcon,
    val color: Long,
    val title: String,
    val body: String,
)

enum class ForYouInsightsHabitIcon {
    SUNNY,
    LEAF,
    WALK,
}

enum class ForYouInsightsSection {
    INSIGHTS,
    LONGEVITY,
    BROWSE,
}

object ForYouInsightsContent {
    const val label = "BASED ON YOUR HEALTH"
    const val tip = "Try breaking your morning run into intervals, alternating two minutes of hard effort with one minute of walking. You will get a great cardio boost in just 25 minutes."

    val articles = listOf(
        ForYouInsightsArticle(
            key = "morning",
            title = "Good Sleep Habits Start as Soon as You Wake Up",
            summary = "Your daily routine can make or break your nightly rest.",
            readTime = "5m Read",
            sections = listOf(
                ForYouInsightsArticleSection(
                    heading = "Begin with light",
                    body = "Spend a few quiet minutes near a bright window or outdoors after you wake. Morning light gives the day a clear opening cue.",
                ),
                ForYouInsightsArticleSection(
                    heading = "Keep a daytime rhythm",
                    body = "Movement, meals and breaks at roughly familiar times make it easier to notice when your energy rises and falls.",
                ),
                ForYouInsightsArticleSection(
                    heading = "Choose one wind-down cue",
                    body = "Dim a lamp, put away one screen or make a warm drink. A small repeatable cue is easier to keep than a whole routine.",
                ),
            ),
            depths = listOf(-1.0, -0.1, 1.0),
        ),
        ForYouInsightsArticle(
            key = "food",
            title = "5 Tricks That Unlock All the Best Stuff in Your Food",
            summary = "How you pair and prepare your ingredients matters.",
            readTime = "3m Read",
            sections = listOf(
                ForYouInsightsArticleSection(
                    heading = "Balance the plate",
                    body = "Pair something crisp with something soft, and something fresh with something warm. Contrast makes simple food easier to enjoy.",
                ),
                ForYouInsightsArticleSection(
                    heading = "Use colour as a prompt",
                    body = "Two or three natural colours are a practical reminder to vary ingredients without turning lunch into a calculation.",
                ),
                ForYouInsightsArticleSection(
                    heading = "Prepare one flexible base",
                    body = "Cook a grain, wash greens or make a dressing once. A useful base lowers the effort of the next meal.",
                ),
            ),
            depths = listOf(-1.0, 0.25, 1.0),
        ),
        ForYouInsightsArticle(
            key = "run",
            title = "10 Minute Intervals That Build Real Stamina",
            summary = "Short efforts with easy walks make a run easier to repeat.",
            readTime = "4m Read",
            sections = listOf(
                ForYouInsightsArticleSection(
                    heading = "Warm up gradually",
                    body = "Start with easy walking, then a relaxed jog. The first interval should feel like an invitation, not a test.",
                ),
                ForYouInsightsArticleSection(
                    heading = "Keep the pattern simple",
                    body = "Two minutes of steady running, one minute of walking. Repeat only while your form stays comfortable.",
                ),
                ForYouInsightsArticleSection(
                    heading = "Finish with room left",
                    body = "Walk until your breathing settles. A session you can repeat is worth more than one that empties the tank.",
                ),
            ),
            depths = listOf(-1.0, -0.15, 1.0),
        ),
    )

    val habits = listOf(
        ForYouInsightsHabit(
            icon = ForYouInsightsHabitIcon.SUNNY,
            color = 0xFFE2A031,
            title = "Begin with light",
            body = "Step outside or sit by a bright window for a quiet start to the day.",
        ),
        ForYouInsightsHabit(
            icon = ForYouInsightsHabitIcon.LEAF,
            color = 0xFF47A76D,
            title = "Balance the plate",
            body = "Pair a fresh ingredient with something warm, and crisp textures with soft ones.",
        ),
        ForYouInsightsHabit(
            icon = ForYouInsightsHabitIcon.WALK,
            color = 0xFF378DDD,
            title = "Warm up gradually",
            body = "Begin with easy walking, then a relaxed jog before your first interval.",
        ),
    )
}

data class ForYouInsightsTipFrame(
    val ghost: Double = 1.0,
    val stream: Double = 1.0,
    val caret: Double = 0.0,
    val shimmer: Double = -1.0,
)

data class ForYouInsightsFrame(
    val tip: ForYouInsightsTipFrame = ForYouInsightsTipFrame(),
    val press: Double = 0.0,
    val progress: Double = 0.0,
    val driftTime: Double = 0.0,
    val drift: Double = 0.0,
    val touchX: Double = 0.0,
    val touchY: Double = 0.0,
    val dragY: Double = 0.0,
    val actorIndex: Int = 0,
    val lens: Double = 0.0,
    val readerLive: Boolean = false,
    val scrollToken: Int = 0,
)

data class ForYouInsightsActorFrame(
    val visible: Double = 0.0,
    val x: Double = 0.0,
    val y: Double = 0.0,
    val width: Double = 0.0,
    val height: Double = 0.0,
    val radius: Double = 0.0,
    val scale: Double = 1.0,
    val tiltX: Double = 0.0,
    val tiltY: Double = 0.0,
    val lift: Double = 0.0,
    val separation: Double = 0.0,
)
