import SwiftUI

enum NeonContourMoodTheme {
    struct Mood {
        let word: String
        let tag: String
        let shape: [Double]
        let ramp: [UInt32]
    }
    static let moods: [Mood] = [
        Mood(word: "PANIC", tag: "CAPITULATION", shape: [132, 116, 45, 76, 39, 0, 39, 0], ramp: [0xFFE7DE, 0xFF3344, 0xB0125E, 0x51208D]),
        Mood(word: "FEAR", tag: "TENSION", shape: [132.134746, 107.983216, 61.268647, 49.46817, 50.063416, 0, 50.063416, 1], ramp: [0xFF2738, 0xFF2B24, 0xF81872, 0x0D0ACC]),
        Mood(word: "DOUBT", tag: "CAUTION", shape: [132, 90, 80, 30, 50, 0, 50, 0], ramp: [0xFF3484, 0xE33DA9, 0x9F29CB, 0x4822AB]),
        Mood(word: "CALM", tag: "BALANCE", shape: [106, 106, 106, 0, 50, 0, 50, 0], ramp: [0xC4A8FF, 0x9A7BFF, 0x6945DF, 0x3524B0]),
        Mood(word: "HOPE", tag: "APPETITE", shape: [89, 127, 89, 0, 50, 0, 50, 0], ramp: [0x8AD8FF, 0x48CFEA, 0x2485D7, 0x252B91]),
        Mood(word: "GREED", tag: "MOMENTUM", shape: [104, 118, 89, -18, 70, 0, 70, 0], ramp: [0x9AFFD0, 0x3DDBA4, 0x199E9E, 0x214B9F]),
        Mood(word: "MANIA", tag: "EUPHORIA", shape: [119, 119, 48, 52, 39, 24, 60, 0], ramp: [0xEEFF92, 0xB3F94B, 0x52BF74, 0x197D88])
    ]
    static let offsets = [0.0, 8, 12.864303, 32, 49.385498, 86, 110.118761]
    static let stops = offsets.map { off in
        off < offsets[2] ? off / offsets[2] : off < offsets[4]
            ? 1 + (off - offsets[2]) / (offsets[4] - offsets[2])
            : 2 + (off - offsets[4]) / (offsets[6] - offsets[4])
    }
    static func rgb(_ hex: UInt32) -> [Double] {
        [Double(hex >> 16 & 255) / 255, Double(hex >> 8 & 255) / 255, Double(hex & 255) / 255]
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> Color { color(rgb(hex), alpha) }
    static func color(_ rgb: [Double], _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: rgb[0], green: rgb[1], blue: rgb[2], opacity: alpha)
    }
    static func blend(_ a: [Double], _ b: [Double], _ t: Double) -> [Double] {
        (0..<3).map { a[$0] + (b[$0] - a[$0]) * t }
    }
    static func ramp(_ mood: Double, _ stop: Double, linear: Bool = false) -> [Double] {
        let m = min(6, max(0, mood)), i = Int(floor(m)), j = min(6, i + 1)
        let f = m - Double(i), u = f * f * (3 - 2 * f)
        let k = min(3, max(0, stop)), a = Int(floor(k)), b = min(3, a + 1), v = k - Double(a)
        let low = blend(rgb(moods[i].ramp[a]), rgb(moods[i].ramp[b]), v)
        let high = blend(rgb(moods[j].ramp[a]), rgb(moods[j].ramp[b]), v)
        return blend(low, high, u).map { s in linear ? (s <= 0.04045 ? s / 12.92 : pow((s + 0.055) / 1.055, 2.4)) : s }
    }
}
