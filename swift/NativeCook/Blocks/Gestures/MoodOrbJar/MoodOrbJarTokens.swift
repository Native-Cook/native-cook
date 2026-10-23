import SwiftUI

/// Every colour, size, string and motion value of the block, copied from the React Native block's
/// `theme.ts` and `motion.ts`. Sizes are points on a 402 pt wide screen, scaled by width / 402.
///
enum MoodOrbJarTokens {
    static let stageWidth = 402.0
    /// QA-only replica switch, at its shipped value (`theme.ts REPLICA`).
    static let replica = false

    // MARK: Colours (theme.ts COLORS, the shipped values)

    enum Palette {
        static let background = Color(hex: 0xF4FDFE)
        static let backgroundBottom = Color(hex: 0xE3F0FB)
        static let card = Color(hex: 0xF7FCFF)
        static let cardHex: UInt32 = 0xF7FCFF
        static let label = Color(hex: 0x3A3A3F)
        static let secondary = Color(hex: 0x8F9298)
        static let cardShadow = Color.rgba(60, 100, 160, 0.08)
        static let ink = Color(hex: 0x16233A)
        static let mute = Color.rgba(22, 35, 58, 0.55)
        static let faint = Color.rgba(22, 35, 58, 0.35)
        static let glass = Color.rgba(255, 255, 255, 0.38)
        static let glassBack = Color.rgba(214, 230, 246, 0.55)
        static let glassEdge = Color.rgba(255, 255, 255, 0.95)
        static let glassShade = Color.rgba(110, 150, 200, 0.28)
        static let floorShadow = Color.rgba(70, 110, 170, 0.18)
        static let lid: [Color] = ([0x8E949C, 0xD8DDE3, 0xF7F8FA, 0xB4BAC2, 0x7C828A] as [UInt32])
            .map { Color(hex: $0) }
        static let lidStops = [0.0, 0.2, 0.42, 0.75, 1.0]
        static let lidRidge = Color.rgba(60, 66, 74, 0.25)
        static let print = Color(hex: 0x0E0E10)
        static let danger = Color(hex: 0xF4545E)
        static let dangerInk = Color(hex: 0xFFFFFF)
        static let dotsBackground = Color(hex: 0xEEF1F5)
        static let sheetLine = Color.rgba(22, 35, 58, 0.1)
        static let dotsInk = Color(hex: 0x5B616B)
        static let aim = Color.rgba(22, 35, 58, 0.18)
    }

    // MARK: Moods, from awful to great (theme.ts MOODS)

    struct Mood {
        let name: String
        let base: UInt32
        let light: UInt32
        let deep: UInt32
        let tags: String
        let assetName: String
    }

    static let moods: [Mood] = [
        Mood(name: "Awful", base: 0xA24CE6, light: 0xE0B0FF, deep: 0x6A22A8, tags: "Angry, Overwhelmed", assetName: "MoodOrbJar-orb-awful.png"),
        Mood(name: "Bad", base: 0x5E7DF6, light: 0xBCCBFF, deep: 0x2F4CC2, tags: "Disgusted, Friends", assetName: "MoodOrbJar-orb-bad.png"),
        Mood(name: "Neutral", base: 0x3FC6E6, light: 0xB4F0FF, deep: 0x1689A8, tags: "Work, Tired", assetName: "MoodOrbJar-orb-neutral.png"),
        Mood(name: "Good", base: 0x57D27A, light: 0xC2F5CF, deep: 0x22964A, tags: "Calm, Partner", assetName: "MoodOrbJar-orb-good.png"),
        Mood(name: "Great", base: 0xF4545E, light: 0xFFC2B8, deep: 0xB8243A, tags: "Family, Sunshine", assetName: "MoodOrbJar-orb-great.png")
    ]

    enum Space {
        static let extraSmall = 4.0
        static let small = 8.0
        static let cardPadding = 12.0
        static let large = 16.0
        static let cardGap = 18.0
        static let avatarTop = 25.0
    }

    // MARK: Layout (theme.ts LAYOUT)

    enum Layout {
        static let side = 16.0
        static let header = 44.0
        static let icon = 44.0
        static let lidWidth = 192.0 // lid.png, 576 x 110 px
        static let lidHeight = 36.7
        static let lidOverlap = 10.0 // the lid sits this far down over the neck
        static let jarTop = 47.0 // the mouth, below the header
        static let jarImageWidth = 280.0 * 1.01 // jar.png, 840 x 1010 px
        static let jarImageHeight = 326.7
        static let jarImageMouthY = 10.0
        static let jarWidth = 276.0 * 1.01
        static let jarHeight = 300.0 // mouth to the bottom of the glass base
        static let base = 2.0
        static let wall = 6.0
        static let shoulder = 70.0
        static let corner = 46.0
        static let mouthHalf = 84.0 * 1.01
        static let printSize = 30.0
        static let printStroke = 5.0
        static let printY = 0.724
        static let labelY = 0.12 // the month and count label, of the jar's height below the mouth
        static let pickerTop = 400.0
        static let pickerHeight = 96.0
        static let pickerOrb = 20.2 // radius
        static let pickerOrbY = 41.0 // from the card's top
        static let slotGap = 67.33 // between picker orb centres
        static let slotDx = 0.8 // the row's centre, right of the screen's
        static let slotLabelHeight = 63.0
        static let labelSize = 13.0
        static let cardRadius = 16.0
        static let entriesTop = 512.0
        static let entryHeight = 106.0
        static let sectionHeight = 26.0
        static let rowHeight = 68.0
        static let hairlineInset = 70.0
        static let entryGap = 18.0
        static let entryOrb = 22.0 // radius
        static let orb = 25.0 // radius in the jar
    }

    /// The seeded pile: the mood of each orb, in the order the pile is dropped (theme.ts PILE_MOODS).
    static let pileMoods = [3, 0, 1, 2, 0, 1, 2, 4, 0, 1, 2, 0, 3, 2, 0, 3]

    /// The accepted reference pile: x/y on the 1206 px-wide source frame, mood and face turn.
    static let replicaPile: [(x: Double, y: Double, mood: Int, turn: Double)] = [
        (484, 899, 3, 0), (634, 885, 1, -4), (942, 884, 2, 128), (311, 919, 0, -84),
        (554, 1019, 4, 0), (717, 1009, 2, 116), (867, 1015, 0, -90), (272, 1061, 0, -16),
        (418, 1024, 0, 44), (632, 1154, 0, -40), (295, 1205, 3, 12), (489, 1145, 1, -4),
        (785, 1140, 1, 100), (937, 1146, 1, -45), (415, 1279, 0, -75), (566, 1276, 1, -90),
        (711, 1281, 0, 0), (852, 1270, 2, -8)
    ]
    static let replicaPlateY = 490.0
    static let replicaPlateHeight = 950.0
    static let replicaCut = 2.0

    // MARK: Copy (theme.ts COPY)

    enum Copy {
        static let today = "Today"
        static let entriesWord = "entries"
        static let month = "OCTOBER"
        static let replicaEntry = (tags: "Ashamed, Partner", time: "4:21 PM")
        static let print = "US?"
        static let back = "Back"
        static let calendar = "Calendar"
        static let more = "More"
        static let undo = "Undo"
        static let now = "Just now"

        static func add(_ mood: String) -> String {
            "Add \(mood)"
        }

        /// The two entries the screen starts with (mood index, tags, time).
        static let entries: [(mood: Int, tags: String, time: String)] = [
            (1, "Disgusted, Friends", "4:20 PM"),
            (2, "Work, Tired", "1:05 PM")
        ]
    }

    // MARK: Motion (motion.ts), seconds unless the name says otherwise

    enum Motion {
        static let step = 1.0 / 120.0
        static let maxSteps = 30 // per frame: the sim keeps wall-clock time through dropped frames

        enum Pile {
            static let count = 16
            static let seed = 11
            static let settleSteps = 900
            static let home = 60.0 // spring back to the slot
            static let damping = 4.0 // per second
            static let passes = 3
            static let restSpeed = 18.0 // a landed orb slower than this...
            static let restTime = 0.35 // ...for this long becomes part of the pile
        }

        enum Throw {
            static let gravity = 2200.0
            static let apex = 70.0 // the solved arc peaks this far above the mouth
            static let solved = 0.75 // share of the solved launch in the final velocity
            static let minFlick = 380.0 // upward speed that counts as a flick
            static let lift = 1.15 // held orb scale
            static let spin = 6.0 // radians per second of spin in flight
            static let regrow = 0.3 // the picker's orb grows back
            static let maxSpeed = 2400.0
        }

        enum Undo {
            static let threshold = 96.0
            static let rise = 0.35
            static let fly = 0.55
            static let rowStiffness = 300.0
            static let rowDamping = 28.0
        }

        /// The lid lifts straight up and floats, tilting a little on the way: 0 closed ... 1 lifted.
        enum Lid {
            static let open = 1.0
            static let lift = 20.0
            static let tilt = 0.09
            static let stiffness = 120.0
            static let damping = 9.0 // overshoots, then floats
            static let hold = 2.4
        }

        enum Soft {
            static let stiffness = 420.0 // squash spring: overshoots into a stretch
            static let damping = 14.0
            static let perSpeed = 1.0 / 2600.0 // squash per pt/s of sudden deceleration
            static let max = 0.28
            static let landing = 0.15 // the first impact squashes at least this much
            static let trigger = 140.0 // pt/s of deceleration in one step that counts as an impact
            static let hard = 900.0 // landing speed above this: heavy haptic
            static let soft = 350.0 // below this: light
        }

        enum Tilt {
            static let slosh = 14.0 // pt of pile slosh at full phone tilt
            static let glass = 6.0 // pt of glass highlight drift at full phone tilt
        }

        /// Shared element: the landed orb lifts out, arcs to the list, the card grows from it.
        enum Ghost {
            static let duration = 0.85
            static let lift = 0.3
            static let rise = 46.0
            static let arc = 60.0
            static let grow = 0.36
        }

        enum Ripple {
            static let duration = 0.6
            static let radius = 46.0
        }

        enum JarKnock {
            static let amplitude = 1.5
            static let decay = 14.0
            static let hertz = 9.0
        }

        enum Hop {
            static let every = 2.0
            static let phase = 1.0
            static let speed = 70.0
        }

        enum Aim {
            static let points = 14
            static let step = 0.045
        }

        enum Trail {
            static let copies = 4
            static let gap = 0.028
            static let alpha: [Double] = [0.32, 0.2, 0.12, 0.06]
        }

        enum Reduced {
            static let fade = 0.2
        }

        // Autoplay: 12 s.
        static let loopMS = 12_000.0
        static let resumeMS = 3_500.0

        enum Script {
            static let mood = 4
            static let pick = [1.0, 1.35]
            static let pickBy = [-30.0, -80.0]
            static let release = [-500.0, -1500.0]
            static let swipe = [7.0, 7.55]
            static let swipeBy = -150.0
        }

        // QA only, at their shipped values.
        static let replicaHold = -1.0
        static let qaSpeed = 1.0

        // Entry list animations (Reanimated FadeInDown / FadeOut / LinearTransition, ms).
        static let rowEnter = 0.26
        static let rowExit = 0.16
        static let rowLayout = 0.22
        static let countEnter = 0.26
        static let countExit = 0.2
        static let rowFadeOffset = 25.0 // FadeInDown's travel
    }

    /// The pool: the pile plus four orbs that can be in the air at once.
    static let pileCount = replicaPile.count
    static let poolCount = pileCount + 4

    static let orbImageScale = 144.0 / 132.0 // the orb art is 144 px with a 132 px orb
}
