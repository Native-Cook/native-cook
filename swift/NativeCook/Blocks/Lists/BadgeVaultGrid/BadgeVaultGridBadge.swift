import SwiftUI

struct BadgeVaultGridBadge {
    let id: String
    let name: String
    let tile: Int
    let a: SIMD3<Float>
    let b: SIMD3<Float>
    let c: SIMD3<Float>
    let earned: String?
    let initialHave: Int
    let need: Int
    let unit: String
    let blurb: String
    let rarity: Int
    let stats: [(String, String)]
    var locked: Bool { need > 0 }
    static let unlockDate = "13 Oct 2026"
    static func rgb(_ value: UInt32) -> SIMD3<Float> {
        SIMD3(Float((value >> 16) & 255) / 255, Float((value >> 8) & 255) / 255, Float(value & 255) / 255)
    }
    static let all: [Self] = [
        .init(id: "first-light", name: "First Light", tile: 0, a: rgb(0xFF7A2F), b: rgb(0xC7352B), c: rgb(0xFFE08A),
              earned: "9 Jan 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Twenty sessions started before sunrise.", rarity: 12, stats: [("06:02", "Earliest"), ("20", "Sessions"), ("4 wk", "Span")]),
        .init(id: "night-shift", name: "Night Shift", tile: 1, a: rgb(0x3D45E0), b: rgb(0x1A1D6E), c: rgb(0xF6F1DC),
              earned: "23 Jan 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Ten sessions finished after midnight.", rarity: 9, stats: [("00:47", "Latest"), ("10", "Sessions"), ("3 wk", "Span")]),
        .init(id: "summit", name: "Summit", tile: 2, a: rgb(0x1CC4E8), b: rgb(0x0D6FA3), c: rgb(0xFFFFFF),
              earned: "2 Feb 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Climbed 8,848 metres in a single month.", rarity: 6, stats: [("8,848 m", "Climbed"), ("17", "Days"), ("612 m", "Best day")]),
        .init(id: "spark", name: "Spark", tile: 3, a: rgb(0xFFD21F), b: rgb(0xFF9A1F), c: rgb(0xFFFFFF),
              earned: "14 Feb 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Fifty quick sessions under five minutes.", rarity: 21, stats: [("50", "Sessions"), ("3:40", "Average"), ("9", "Best day")]),
        .init(id: "deep-dive", name: "Deep Dive", tile: 4, a: rgb(0x14D8B2), b: rgb(0x0A8A8E), c: rgb(0xD9FFF5),
              earned: "1 Mar 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Four hours of focus without one break.", rarity: 4, stats: [("4:12 h", "Longest"), ("31", "Sessions"), ("96%", "Finished")]),
        .init(id: "iron-month", name: "Iron Month", tile: 5, a: rgb(0x9BE21A), b: rgb(0x3F9E12), c: rgb(0xF2FFD0),
              earned: nil, initialHave: 12, need: 30, unit: "days",
              blurb: "Show up thirty days in a row.", rarity: 3, stats: [("12", "Days so far"), ("18", "To go"), ("30", "Target")]),
        .init(id: "trailhead", name: "Trailhead", tile: 6, a: rgb(0xFF4D6D), b: rgb(0xB3123F), c: rgb(0xFFE7EC),
              earned: "19 Mar 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Tried every kind of session at least once.", rarity: 17, stats: [("8", "Kinds"), ("11", "Days"), ("2", "Favourites")]),
        .init(id: "seven-up", name: "Seven Up", tile: 7, a: rgb(0xA45BFF), b: rgb(0x5E2BC4), c: rgb(0xF0E5FF),
              earned: "30 Mar 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Seven sessions in a single day.", rarity: 8, stats: [("7", "Sessions"), ("3:05 h", "Total"), ("26 min", "Average")]),
        .init(id: "bullseye", name: "Bullseye", tile: 8, a: rgb(0xFF3B30), b: rgb(0xB01212), c: rgb(0xFFFFFF),
              earned: "11 Apr 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Hit the weekly goal exactly, to the minute.", rarity: 2, stats: [("300", "Minutes"), ("0", "Over"), ("5", "Sessions")]),
        .init(id: "evergreen", name: "Evergreen", tile: 9, a: rgb(0x2ED158), b: rgb(0x0F7A3B), c: rgb(0xD9FFE3),
              earned: "2 May 2026", initialHave: 0, need: 0, unit: "",
              blurb: "A hundred days without missing a week.", rarity: 11, stats: [("100", "Days"), ("15", "Weeks"), ("71", "Sessions")]),
        .init(id: "long-haul", name: "Long Haul", tile: 10, a: rgb(0xFF9F0A), b: rgb(0xB85C00), c: rgb(0xFFF1D6),
              earned: "28 May 2026", initialHave: 0, need: 0, unit: "",
              blurb: "A thousand kilometres, one step at a time.", rarity: 5, stats: [("1,000 km", "Distance"), ("148", "Days"), ("6.8 km", "Average")]),
        .init(id: "gemcutter", name: "Gemcutter", tile: 11, a: rgb(0x5AC8FA), b: rgb(0x1E78D6), c: rgb(0xFFFFFF),
              earned: "16 Jun 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Beat a personal best five times running.", rarity: 7, stats: [("5", "Records"), ("12 d", "Span"), ("+9%", "Gain")]),
        .init(id: "hot-streak", name: "Hot Streak", tile: 12, a: rgb(0xFF5E3A), b: rgb(0xD11A2A), c: rgb(0xFFC83D),
              earned: "4 Jul 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Fourteen days, and the streak is alive.", rarity: 14, stats: [("14", "Days"), ("19", "Sessions"), ("41 min", "Average")]),
        .init(id: "orbit", name: "Orbit", tile: 13, a: rgb(0x7D5CFF), b: rgb(0x2C1E8F), c: rgb(0xFFD86B),
              earned: "21 Aug 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Three hundred and sixty-five sessions.", rarity: 1, stats: [("365", "Sessions"), ("212", "Days"), ("1.7", "Per day")]),
        .init(id: "full-week", name: "Full Week", tile: 14, a: rgb(0xFF2D92), b: rgb(0xA3125E), c: rgb(0xFFE0F0),
              earned: nil, initialHave: 6, need: 7, unit: "days",
              blurb: "Seven days out of seven.", rarity: 19, stats: [("7", "Days"), ("9", "Sessions"), ("38 min", "Average")]),
        .init(id: "steady", name: "Steady", tile: 15, a: rgb(0x1FB6FF), b: rgb(0x0A4FA8), c: rgb(0xFFFFFF),
              earned: "9 Sep 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Same hour, every day, for three weeks.", rarity: 6, stats: [("07:30", "Hour"), ("21", "Days"), ("±4 min", "Drift")]),
        .init(id: "crowned", name: "Crowned", tile: 16, a: rgb(0xFFC531), b: rgb(0xD98A00), c: rgb(0xFFF6D0),
              earned: nil, initialHave: 2, need: 5, unit: "wins",
              blurb: "Finish first in five weekly leagues.", rarity: 1, stats: [("2", "Wins so far"), ("3", "To go"), ("5", "Target")]),
        .init(id: "keystone", name: "Keystone", tile: 17, a: rgb(0x34E0C2), b: rgb(0x128F7C), c: rgb(0xEFFFFA),
              earned: "1 Oct 2026", initialHave: 0, need: 0, unit: "",
              blurb: "Completed every badge of one set.", rarity: 3, stats: [("6", "Badges"), ("1", "Set"), ("9 mo", "Span")]),
    ]
}
