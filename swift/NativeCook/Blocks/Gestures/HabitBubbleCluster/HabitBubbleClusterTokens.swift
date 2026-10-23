import SwiftUI

enum HabitBubbleClusterTokens {
    enum Color {
        static let bg = "#0E1013"
        static let ink = "#F3F4F2"
        static let inkSoft = "rgba(243,244,242,0.60)"
        static let inkFaint = "rgba(243,244,242,0.36)"
        static let hairline = "rgba(255,255,255,0.10)"
        static let selected = "#F3F4F2"
        static let selectedInk = "#111316"
        static let finger = "rgba(255,255,255,0.14)"
        static let fingerRing = "rgba(255,255,255,0.42)"
    }

    struct Hue {
        let tint: (Double, Double, Double)
        let top: String
        let bottom: String
        let ink: String
        let icon: String
    }

    static let hue: [String: Hue] = [
        "purple": .init(tint: (0.66, 0.73, 0.86), top: "#B9C6DF", bottom: "#8193B5", ink: "#1C2333", icon: "#E4EAF5"),
        "green": .init(tint: (0.62, 0.78, 0.66), top: "#B7D3BD", bottom: "#7FA38A", ink: "#17261C", icon: "#E3F0E6"),
        "yellow": .init(tint: (0.88, 0.79, 0.60), top: "#E8D5AE", bottom: "#BFA372", ink: "#2B2213", icon: "#F6EEDD"),
        "orange": .init(tint: (0.86, 0.66, 0.54), top: "#E3BBA4", bottom: "#B98468", ink: "#2D1C13", icon: "#F6E6DC"),
        "red": .init(tint: (0.82, 0.62, 0.62), top: "#DDB2B0", bottom: "#AE7C7B", ink: "#2C1717", icon: "#F5E3E2"),
        "glass": .init(tint: (0.80, 0.82, 0.85), top: "#D3D7DD", bottom: "#9CA3AD", ink: "#1C1F24", icon: "#EEF0F3"),
    ]

    struct Habit {
        let id: String
        let name: String
        let icon: String
        let hue: String
        let r: Double
        let x: Double
        let y: Double
    }

    static let social: [Habit] = [
        .init(id: "wave", name: "Say hi to a neighbour", icon: "hand-left-outline", hue: "purple", r: 78, x: -40, y: -155),
        .init(id: "star", name: "Thank someone", icon: "star-outline", hue: "purple", r: 78, x: 119, y: -51),
        .init(id: "gift", name: "Send a small gift", icon: "basket-outline", hue: "red", r: 28, x: 51, y: -223),
        .init(id: "ball", name: "Team sport", icon: "basketball-outline", hue: "orange", r: 33, x: 78, y: -162),
        .init(id: "friend", name: "Call a friend", icon: "person-outline", hue: "orange", r: 34, x: -140, y: -100),
        .init(id: "art", name: "Draw together", icon: "color-palette-outline", hue: "green", r: 53, x: -107, y: -15),
        .init(id: "host", name: "Host friends", icon: "bed-outline", hue: "yellow", r: 43, x: -7, y: -33),
        .init(id: "magic", name: "Plan a surprise", icon: "sparkles-outline", hue: "yellow", r: 43, x: -104, y: 87),
        .init(id: "home", name: "Family dinner", icon: "home-outline", hue: "green", r: 50, x: 110, y: 85),
        .init(id: "tea", name: "Tea with mum", icon: "cafe-outline", hue: "red", r: 28, x: -130, y: 156),
        .init(id: "heart", name: "Date night", icon: "heart-outline", hue: "green", r: 53, x: -47, y: 172),
        .init(id: "wallet", name: "Split the bill", icon: "wallet-outline", hue: "orange", r: 33, x: 44, y: 155),
        .init(id: "ride", name: "Ride to work", icon: "bicycle-outline", hue: "yellow", r: 43, x: 121, y: 188),
        .init(id: "note", name: "Write a note", icon: "mail-outline", hue: "glass", r: 25, x: 65, y: 4),
    ]

    static let personal: [Habit] = [
        .init(id: "read", name: "Read 20 pages", icon: "book-outline", hue: "purple", r: 70, x: -14, y: -130),
        .init(id: "sleep", name: "In bed by 23:00", icon: "moon-outline", hue: "purple", r: 60, x: 99, y: -58),
        .init(id: "meditate", name: "Meditate", icon: "leaf-outline", hue: "green", r: 53, x: -104, y: -40),
        .init(id: "run", name: "Morning run", icon: "walk-outline", hue: "green", r: 50, x: -3, y: -7),
        .init(id: "cook", name: "Cook at home", icon: "restaurant-outline", hue: "green", r: 46, x: 80, y: 51),
        .init(id: "spanish", name: "Spanish lesson", icon: "language-outline", hue: "yellow", r: 43, x: 98, y: -165),
        .init(id: "water", name: "Two litres of water", icon: "water-outline", hue: "yellow", r: 40, x: -76, y: 52),
        .init(id: "guitar", name: "Practise guitar", icon: "musical-notes-outline", hue: "yellow", r: 38, x: -1, y: 85),
        .init(id: "stretch", name: "Stretch", icon: "body-outline", hue: "orange", r: 34, x: -122, y: -130),
        .init(id: "phone", name: "No phone in bed", icon: "phone-portrait-outline", hue: "orange", r: 31, x: 166, y: -126),
        .init(id: "vitamins", name: "Vitamins", icon: "medkit-outline", hue: "red", r: 28, x: 57, y: 125),
        .init(id: "plants", name: "Water the plants", icon: "flower-outline", hue: "red", r: 26, x: -143, y: 33),
        .init(id: "journal", name: "Journal", icon: "create-outline", hue: "glass", r: 24, x: -58, y: 118),
    ]

    static let quiet = (r: 55.0, x: 0.0, y: 67.0, title: "QUIET HOURS", hours: "22:00–07:00")
    static let cluster = (x: 190.0, y: 380.0)
    static let referenceWidth = 402.0

    enum FontName {
        static let regular = "Inter24pt-Regular"
        static let medium = "Inter-Medium"
        static let semibold = "Inter-SemiBold"
    }

    static let type = (
        segment: 14.0,
        date: 24.0,
        now: 14.0,
        count: 15.0,
        monogram: 14.0,
        label: 14.0,
        labelSub: 12.0,
        quiet: 9.0
    )
    static let space = (xs: 4.0, s: 8.0, m: 12.0, l: 16.0, xl: 24.0)
    static let layout = (
        gutter: 16.0,
        control: 44.0,
        segmentWidth: 196.0,
        dateDrop: 252.0,
        pill: 44.0,
        gap: 3.0,
        iconScale: 0.30,
        ring: 2.0,
        ringGap: 6.0,
        fingerRadius: 22.0,
        labelHeight: 52.0,
        labelGap: 10.0
    )
    static let copy = (
        tabs: ["Social", "Personal"],
        date: "Tuesday, 14 October",
        now: "Hold a habit to mark it done",
        monogram: "AM"
    )

    static let physics = (
        step: 1.0 / 120.0,
        stiffness: 120.0,
        damping: 14.0,
        radiusStiffness: 260.0,
        radiusDamping: 18.0,
        separation: 0.5,
        relaxIterations: 240,
        relaxGravity: 0.04,
        maxFling: 2400.0,
        maxFrameMs: 250.0
    )
    static let hold = (
        delayMs: 120.0,
        chargeMs: 900.0,
        drainMs: 220.0,
        moveTolerance: 10.0,
        squeeze: 0.94,
        ticks: [1.0 / 3.0, 2.0 / 3.0],
        doneScale: 0.62,
        tapMs: 220.0
    )
    static let burst = (flashMs: 240.0, rippleMs: 700.0, rippleReach: 26.0)
    static let idle = (breathe: 0.015, cyclesMin: 2.0)
    static let liquid = (period1: 140.0, period2: 230.0, amp1: 3.0, amp2: 1.6)
    static let wave = (speed: 520.0, reach: 120.0)
    static let stagger = (stepMs: 30.0, per: 60.0)
    static let label = (inMs: 180.0, holdMs: 1600.0, outMs: 220.0, rise: 6.0)
    static let loopMs = 10_000.0
    static let autoplay = (
        resumeDelayMs: 2000.0,
        holdID: "star",
        holdStart: 400.0,
        holdEnd: 1700.0,
        nameID: "home",
        nameAt: 2700.0,
        personalAt: 4200.0,
        socialAt: 6400.0,
        tapAt: 8000.0,
        tapMs: 120.0
    )
}

func HabitBubbleClusterColor(_ value: String, alpha multiplier: Double = 1) -> Color {
    let trimmed = value.trimmingCharacters(in: .whitespacesAndNewlines)
    if trimmed.hasPrefix("#") {
        let hex = String(trimmed.dropFirst())
        guard let raw = UInt64(hex, radix: 16) else { return .clear }
        return Color(
            red: Double((raw >> 16) & 0xff) / 255,
            green: Double((raw >> 8) & 0xff) / 255,
            blue: Double(raw & 0xff) / 255,
            opacity: multiplier
        )
    }
    let values = trimmed.drop(while: { $0 != "(" }).dropFirst().dropLast()
        .split(separator: ",")
        .compactMap { Double($0.trimmingCharacters(in: .whitespaces)) }
    guard values.count >= 3 else { return .clear }
    return Color(
        red: values[0] / 255,
        green: values[1] / 255,
        blue: values[2] / 255,
        opacity: (values.count > 3 ? values[3] : 1) * multiplier
    )
}
