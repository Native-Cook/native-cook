import Foundation

struct ForYouInsightsArticle {
    var key: String
    var title: String
    var summary: String
    var readTime: String
    var sections: [(heading: String, body: String)]
    /// Depth of each layer, back to front (LAYER_DEPTHS).
    var depths: [Double]
}

struct ForYouInsightsHabit {
    var symbol: String
    var color: UInt32
    var title: String
    var body: String
}

enum ForYouInsightsContent {
    static let label = "BASED ON YOUR HEALTH"
    static let tip = "Try breaking your morning run into intervals, alternating two minutes of hard effort with one minute of walking. You will get a great cardio boost in just 25 minutes."

    static let articles: [ForYouInsightsArticle] = [
        ForYouInsightsArticle(
            key: "morning", title: "Good Sleep Habits Start as Soon as You Wake Up",
            summary: "Your daily routine can make or break your nightly rest.", readTime: "5m Read",
            sections: [
                ("Begin with light", "Spend a few quiet minutes near a bright window or outdoors after you wake. Morning light gives the day a clear opening cue."),
                ("Keep a daytime rhythm", "Movement, meals and breaks at roughly familiar times make it easier to notice when your energy rises and falls."),
                ("Choose one wind-down cue", "Dim a lamp, put away one screen or make a warm drink. A small repeatable cue is easier to keep than a whole routine."),
            ],
            depths: [-1, -0.1, 1]),
        ForYouInsightsArticle(
            key: "food", title: "5 Tricks That Unlock All the Best Stuff in Your Food",
            summary: "How you pair and prepare your ingredients matters.", readTime: "3m Read",
            sections: [
                ("Balance the plate", "Pair something crisp with something soft, and something fresh with something warm. Contrast makes simple food easier to enjoy."),
                ("Use colour as a prompt", "Two or three natural colours are a practical reminder to vary ingredients without turning lunch into a calculation."),
                ("Prepare one flexible base", "Cook a grain, wash greens or make a dressing once. A useful base lowers the effort of the next meal."),
            ],
            depths: [-1, 0.25, 1]),
        ForYouInsightsArticle(
            key: "run", title: "10 Minute Intervals That Build Real Stamina",
            summary: "Short efforts with easy walks make a run easier to repeat.", readTime: "4m Read",
            sections: [
                ("Warm up gradually", "Start with easy walking, then a relaxed jog. The first interval should feel like an invitation, not a test."),
                ("Keep the pattern simple", "Two minutes of steady running, one minute of walking. Repeat only while your form stays comfortable."),
                ("Finish with room left", "Walk until your breathing settles. A session you can repeat is worth more than one that empties the tank."),
            ],
            depths: [-1, -0.15, 1]),
    ]

    static let habits = [
        ForYouInsightsHabit(symbol: "sun.max.fill", color: 0xE2A031, title: "Begin with light", body: "Step outside or sit by a bright window for a quiet start to the day."),
        ForYouInsightsHabit(symbol: "leaf.fill", color: 0x47A76D, title: "Balance the plate", body: "Pair a fresh ingredient with something warm, and crisp textures with soft ones."),
        ForYouInsightsHabit(symbol: "figure.walk", color: 0x378DDD, title: "Warm up gradually", body: "Begin with easy walking, then a relaxed jog before your first interval."),
    ]
}
