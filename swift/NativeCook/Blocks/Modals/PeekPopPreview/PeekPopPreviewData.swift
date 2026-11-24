import Foundation

/// "Margin", its six saved articles and their covers are invented. The covers are drawn in code (PeekPopPreviewCover).
struct PeekPopPreviewArticle: Identifiable {
    /// Positions and sizes are in units of the cover's height, measured from its centre.
    enum Shape {
        case circle(x: Double, y: Double, size: Double, color: UInt32, opacity: Double)
        case ring(x: Double, y: Double, size: Double, stroke: Double, color: UInt32, opacity: Double)
        case bar(x: Double, y: Double, width: Double, height: Double, turn: Double, radius: Double, color: UInt32, opacity: Double)
    }

    let id: String
    let title: String
    let source: String
    let minutes: Int
    let topic: String
    /// The first paragraph: what the preview shows.
    let excerpt: String
    let body: [String]
    /// Three-stop gradient, top left to bottom right.
    let colors: [UInt32]
    let shapes: [Shape]

    var meta: String { "\(source) · \(minutes) min read" }

    static let all: [PeekPopPreviewArticle] = [
        PeekPopPreviewArticle(
            id: "slow-software",
            title: "The quiet case for slower software",
            source: "Slow Wire",
            minutes: 6,
            topic: "Software",
            excerpt: "For a decade the answer to every product question was to make it faster. Faster to load, faster to ship, faster to forget. A small group of teams has started asking what happens when a tool is allowed to take its time.",
            body: [
                "The first thing they noticed was the support inbox. When an export took four seconds and said so, people waited. When it took one second and failed without a word a tenth of the time, they wrote in. Speed had been standing in for trust, and it was a poor stand-in.",
                "None of this is an argument for spinners. It is an argument for honesty about what a computer is doing on your behalf. A progress bar that tells the truth is slower than one that lies, and almost everyone prefers it once they have seen both.",
                "The teams that slowed down did not lose their users. They lost the ones who had never been sure why they were there, and kept the ones who had work to finish.",
            ],
            colors: [0x0E5A4E, 0x157A63, 0x2FA07C],
            shapes: [
                .circle(x: -0.12, y: 0.02, size: 0.52, color: 0xF3E9D2, opacity: 1),
                .circle(x: 0.2, y: -0.08, size: 0.3, color: 0xF28B3C, opacity: 1),
                .ring(x: 0.2, y: -0.08, size: 0.46, stroke: 0.012, color: 0xF3E9D2, opacity: 0.7),
                .bar(x: 0, y: 0.34, width: 0.9, height: 0.016, turn: 0, radius: 0, color: 0xF3E9D2, opacity: 0.55),
            ]
        ),
        PeekPopPreviewArticle(
            id: "tide-tables",
            title: "What tide tables taught me about deadlines",
            source: "Harbour Review",
            minutes: 9,
            topic: "Work",
            excerpt: "A tide table is the only schedule I have ever fully believed. It is printed a year ahead, it has never once been renegotiated, and the boats in our harbour plan their whole week around a column of numbers nobody argues with.",
            body: [
                "I started keeping one on my desk the winter our release slipped for the third time. Not as a joke, exactly. I wanted to look at a plan that was allowed to be true.",
                "What the table does well is separate the two things a deadline usually blurs together: when the water will be there, and whether you will be ready for it. The first is fixed. The second is yours. A skipper who misses the tide does not ask the sea for an extension.",
                "We now write two dates on everything. One is the tide, and it does not move. The other is our guess at being ready, and it moves all the time, in the open, where everyone can watch it.",
            ],
            colors: [0x12284F, 0x1D4A8C, 0x3D7CC9],
            shapes: [
                .bar(x: 0, y: -0.2, width: 0.42, height: 0.07, turn: 0, radius: 0.035, color: 0xF4EFE3, opacity: 1),
                .bar(x: 0.04, y: -0.08, width: 0.62, height: 0.07, turn: 0, radius: 0.035, color: 0x9ADBE8, opacity: 1),
                .bar(x: -0.03, y: 0.04, width: 0.5, height: 0.07, turn: 0, radius: 0.035, color: 0xF4EFE3, opacity: 0.9),
                .bar(x: 0.02, y: 0.16, width: 0.72, height: 0.07, turn: 0, radius: 0.035, color: 0x5FB6DD, opacity: 1),
                .circle(x: 0.3, y: -0.3, size: 0.1, color: 0xFFD166, opacity: 1),
            ]
        ),
        PeekPopPreviewArticle(
            id: "old-houses",
            title: "A field guide to the sounds of old houses",
            source: "Lamplight",
            minutes: 4,
            topic: "Home",
            excerpt: "Every old house keeps a short vocabulary. A tick in the hallway at dusk, a long sigh from the stairs around two, a knock in the pipes that arrives exactly when the heating does. Learn the words and the house stops being haunted.",
            body: [
                "The tick is timber. Wood that has spent the day warming gives the heat back in the evening, and each board lets go in its own time. It is loudest in autumn, when days and nights disagree the most.",
                "The sigh is the staircase settling under no weight at all. Builders call it creep. It is the sound of a structure that has been carrying a load for ninety years and is still, very slowly, getting comfortable.",
                "The knock deserves a plumber. Everything else deserves a cup of tea and a little patience.",
            ],
            colors: [0x7A2E1D, 0xB4492A, 0xDE7B45],
            shapes: [
                .ring(x: 0, y: 0.06, size: 0.26, stroke: 0.035, color: 0xF6E7CF, opacity: 1),
                .ring(x: 0, y: 0.06, size: 0.46, stroke: 0.03, color: 0xF6E7CF, opacity: 0.75),
                .ring(x: 0, y: 0.06, size: 0.66, stroke: 0.024, color: 0xF6E7CF, opacity: 0.5),
                .ring(x: 0, y: 0.06, size: 0.86, stroke: 0.018, color: 0xF6E7CF, opacity: 0.28),
                .circle(x: 0, y: 0.06, size: 0.09, color: 0x2B1710, opacity: 1),
            ]
        ),
        PeekPopPreviewArticle(
            id: "night-trains",
            title: "Why night trains are having a second life",
            source: "Platform Nine",
            minutes: 12,
            topic: "Travel",
            excerpt: "The sleeper from the coast leaves at 22:14 and arrives, eight hundred kilometres later, in time for breakfast. Ten years ago the line was a week from closing. This summer it sold out every Friday from May to September.",
            body: [
                "Ask the passengers why and nobody mentions speed. They talk about waking up somewhere. A flight gives you two airports and a headache; a night train hands you the journey as a kind of gift and takes none of your day in return.",
                "The economics are stubborn. A carriage that carries sixty seated people carries twenty-four asleep, and it only earns once a night. Operators who survived did it by selling the bed rather than the distance.",
                "What changed was not the trains. It was what people decided an evening was worth.",
            ],
            colors: [0x23174F, 0x4A2A78, 0x8A3F8C],
            shapes: [
                .bar(x: -0.16, y: 0.04, width: 0.1, height: 0.86, turn: 28, radius: 0.05, color: 0xFFD23F, opacity: 1),
                .bar(x: 0, y: 0.04, width: 0.1, height: 0.7, turn: 28, radius: 0.05, color: 0xFF7FA8, opacity: 1),
                .bar(x: 0.16, y: 0.04, width: 0.1, height: 0.54, turn: 28, radius: 0.05, color: 0xF4EFE3, opacity: 0.92),
                .circle(x: 0.3, y: -0.26, size: 0.13, color: 0xF4EFE3, opacity: 1),
            ]
        ),
        PeekPopPreviewArticle(
            id: "weather-map",
            title: "The map that put colour on the weather",
            source: "Meridian",
            minutes: 7,
            topic: "Science",
            excerpt: "Before the forecast was a picture it was a paragraph. Somebody read it aloud, you listened for the name of your county, and you guessed. The first printed map with a colour scale changed what people thought weather was.",
            body: [
                "The trick was not the colours. It was the legend: eleven steps from pale to dark, each one a promise that the same shade meant the same thing in every town on the page.",
                "Readers took to it in a season. Within two years farmers were writing in to complain about a single misprinted band, which is the surest sign that a chart has become part of how people think.",
                "We still argue about the palette. Nobody argues about the idea.",
            ],
            colors: [0xB8791A, 0xE19A2B, 0xF5C453],
            shapes: [
                .circle(x: -0.3, y: -0.16, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: -0.1, y: -0.16, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: 0.1, y: -0.16, size: 0.13, color: 0xFFF4D6, opacity: 1),
                .circle(x: 0.3, y: -0.16, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: -0.3, y: 0.04, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: -0.1, y: 0.04, size: 0.13, color: 0xD94F30, opacity: 1),
                .circle(x: 0.1, y: 0.04, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: 0.3, y: 0.04, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: -0.3, y: 0.24, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: -0.1, y: 0.24, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: 0.1, y: 0.24, size: 0.13, color: 0x3B2A12, opacity: 1),
                .circle(x: 0.3, y: 0.24, size: 0.13, color: 0xFFF4D6, opacity: 1),
            ]
        ),
        PeekPopPreviewArticle(
            id: "one-loaf",
            title: "Notes from a year of baking one loaf",
            source: "Crumb",
            minutes: 5,
            topic: "Food",
            excerpt: "I baked the same loaf fifty-two times. Same flour, same tin, same oven that runs twelve degrees hot at the back. I wanted to find out how much there was to learn inside a recipe I already knew by heart.",
            body: [
                "Week nine was the first loaf I would have served to someone. Week twenty was the first I would not have apologised for. Nothing in the recipe had changed; I had simply started to notice the dough.",
                "The lesson is not that repetition makes you good. It is that a fixed recipe turns everything else into a variable you can finally see: the weather, the flour, your own impatience at ten to seven.",
                "I am starting again in January. Same loaf.",
            ],
            colors: [0x254D2B, 0x3F7A3A, 0x8DB652],
            shapes: [
                .bar(x: -0.15, y: 0.02, width: 0.3, height: 0.3, turn: 0, radius: 0.03, color: 0xF2EAD6, opacity: 1),
                .circle(x: 0.15, y: 0.02, size: 0.3, color: 0xF0B43C, opacity: 1),
                .bar(x: -0.15, y: -0.28, width: 0.3, height: 0.3, turn: 0, radius: 0.15, color: 0x16301A, opacity: 0.85),
                .bar(x: 0.15, y: 0.32, width: 0.3, height: 0.3, turn: 45, radius: 0.03, color: 0xF2EAD6, opacity: 0.6),
            ]
        ),
    ]
}
