import SwiftUI

/// Every colour, size, string and motion value of the block, copied from the approved React Native
/// `theme.ts` and `motion.ts`. Sizes are points on a 402 pt wide screen, scaled by width / 402.
enum MoodOrbJarTokens {
    static let stageWidth = 402.0

    enum Palette {
        static let background = Color(hex: 0xF3EFE8)
        static let backgroundBottom = Color(hex: 0xE7E0D3)
        static let card = Color(hex: 0xFBF9F5)
        static let cardHex: UInt32 = 0xFBF9F5
        static let cardEdge = Color.rgba(60, 48, 30, 0.07)
        static let label = Color(hex: 0x4A4338)
        static let secondary = Color(hex: 0x8A8173)
        static let cardShadow = Color.rgba(80, 60, 30, 0.10)
        static let ink = Color(hex: 0x2A251E)
        static let mute = Color.rgba(42, 37, 30, 0.58)
        static let glassBody = Color.rgba(196, 210, 204, 0.16)
        static let glassEdge = Color.rgba(92, 112, 108, 0.42)
        static let glassEdgeSoft = Color.rgba(92, 112, 108, 0.16)
        static let floorShadow = Color.rgba(70, 55, 30, 0.22)
        static let caustic = Color.rgba(255, 236, 196, 0.75)
        static let lid = ([0x6E6A63, 0xC9C4BA, 0xF1EEE8, 0xA9A398, 0x5E5A53] as [UInt32]).map { Color(hex: $0) }
        static let lidStops = [0.0, 0.22, 0.45, 0.78, 1.0]
        static let lidRidge = Color.rgba(40, 34, 26, 0.22)
        static let danger = Color(hex: 0xB9563E)
        static let dangerInk = Color.white
        static let dotsBackground = Color(hex: 0xF1EDE6)
        static let sheetLine = Color.rgba(42, 37, 30, 0.1)
        static let dotsInk = Color(hex: 0x5E574C)
        static let aim = Color.rgba(42, 37, 30, 0.22)
    }

    struct Mood {
        let name: String
        let base: UInt32
        let light: UInt32
        let deep: UInt32
        let tags: String
    }

    static let moods: [Mood] = [
        Mood(name: "Awful", base: 0x6B6F8C, light: 0xC3C5DA, deep: 0x363954, tags: "Overwhelmed, Late night"),
        Mood(name: "Bad", base: 0x5F86A8, light: 0xBCD2E4, deep: 0x2F4F6B, tags: "Tired, Commute"),
        Mood(name: "Neutral", base: 0xB5A486, light: 0xEDE3CF, deep: 0x7A6B50, tags: "Work, Errands"),
        Mood(name: "Good", base: 0x7EA67C, light: 0xCFE2CA, deep: 0x46694A, tags: "Calm, Long walk"),
        Mood(name: "Great", base: 0xE0965A, light: 0xF8D4AE, deep: 0xA55B26, tags: "Family, Sunshine")
    ]

    enum Space {
        static let extraSmall = 4.0
        static let cardPadding = 12.0
    }

    enum Layout {
        static let side = 16.0
        static let header = 44.0
        static let icon = 44.0
        static let lidWidth = 192.0
        static let lidHeight = 36.7
        static let lidOverlap = 10.0
        static let jarTop = 47.0
        static let jarWidth = 276.0 * 1.01
        static let jarHeight = 300.0
        static let base = 2.0
        static let wall = 6.0
        static let shoulder = 70.0
        static let corner = 46.0
        static let mouthHalf = 84.0 * 1.01
        static let labelY = 0.12
        static let pickerTop = 400.0
        static let pickerHeight = 96.0
        static let pickerOrb = 20.2
        static let pickerOrbY = 41.0
        static let slotGap = 67.33
        static let slotDx = 0.8
        static let slotLabelHeight = 63.0
        static let labelSize = 12.0
        static let cardRadius = 16.0
        static let entriesTop = 508.0
        static let entryHeight = 64.0
        static let entryGap = 8.0
        static let entryOrb = 18.0
        static let orb = 25.0
    }

    static let pileMoods = [3, 0, 1, 2, 0, 1, 2, 4, 0, 1, 2, 0, 3, 2, 0, 3]

    enum Copy {
        static let title = "Mood jar"
        static let month = "OCTOBER"
        static let back = "Back"
        static let calendar = "Calendar"
        static let more = "More"
        static let undo = "Undo"
        static let now = "Just now"

        static func add(_ mood: String) -> String { "Add \(mood)" }

        static let entries: [(mood: Int, tags: String, time: String)] = [
            (3, "Calm, Long walk", "4:20 PM"),
            (2, "Work, Errands", "1:05 PM")
        ]
    }

    enum Motion {
        static let step = 1.0 / 120.0
        static let maxSteps = 30

        enum Pile {
            static let settleSteps = 900
            static let home = 60.0
            static let damping = 4.0
            static let passes = 3
            static let restSpeed = 18.0
            static let restTime = 0.35
        }

        enum Throw {
            static let gravity = 2200.0
            static let apex = 70.0
            static let solved = 0.75
            static let minFlick = 380.0
            static let lift = 1.15
            static let regrow = 0.3
            static let maxSpeed = 2400.0
        }

        enum Undo {
            static let threshold = 96.0
            static let rise = 0.35
            static let fly = 0.55
            static let rowStiffness = 300.0
            static let rowDamping = 28.0
        }

        enum Lid {
            static let open = 1.0
            static let lift = 20.0
            static let tilt = 0.09
            static let stiffness = 120.0
            static let damping = 9.0
            static let hold = 1.1
        }

        enum Soft {
            static let stiffness = 420.0
            static let damping = 14.0
            static let perSpeed = 1.0 / 2600.0
            static let max = 0.28
            static let landing = 0.15
            static let trigger = 140.0
            static let hard = 900.0
            static let soft = 350.0
        }

        enum Tilt {
            static let slosh = 14.0
            static let glass = 6.0
        }

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
            static let phase = 0.5
            static let speed = 70.0
            static let last = 6.0
        }

        enum Aim { static let points = 14 }
        enum Trail {
            static let gap = 0.028
            static let alpha = [0.32, 0.2, 0.12, 0.06]
        }
        enum Reduced { static let fade = 0.2 }

        static let loopMS = 10_000.0
        static let resumeMS = 2_000.0

        enum Script {
            static let mood = 4
            static let pick = [1.0, 1.35]
            static let pickBy = [-30.0, -80.0]
            static let release = [-500.0, -1500.0]
            static let swipe = [5.6, 6.15]
            static let swipeBy = -150.0
        }

        static let replicaHold = -1.0
        static let qaSpeed = 1.0
        static let rowEnter = 0.26
        static let rowExit = 0.16
        static let rowLayout = 0.22
        static let countEnter = 0.26
        static let countExit = 0.2
        static let rowFadeOffset = 25.0
    }

    static let pileCount = 18
    static let poolCount = pileCount + 4
}
