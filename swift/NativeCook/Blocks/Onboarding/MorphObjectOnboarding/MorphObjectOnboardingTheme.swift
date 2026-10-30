import SwiftUI

// All coordinates are the RN reference's 402 × 874 bottom-aligned design points.
enum MorphObjectOnboardingTheme {
    static let width = 402.0
    static let height = 874.0
    static let scale = width / 291
    static func x(_ pixel: Double) -> Double { (pixel - 101) * scale }
    static func y(_ pixel: Double) -> Double { (pixel - 156) * scale }
    static let titles = [["We really value", "your time"], ["You will", "learn online"], ["You will learn", "correctly"]]
    static let subtitles = [
        ["The classes last for 60 minutes,", "you can schedule for any day"],
        ["You won't need to spend money", "and time going to school."],
        ["Our teachers hold a special", "international certificate"]
    ]
    static let glows = [0xFFB300, 0x7A5CFF, 0xFF6A3D]
    enum C {
        static let background = 0x18171F
        static let card = 0xFFFFFF
        static let ink = 0x111014
        static let subtitle = 0x8B8A92
        static let button = 0x18171F
        static let buttonInk = 0xFFFFFF
        static let dot = 0x4A4954
        static let pill = 0xFFFFFF
        static let skip = 0xFFFFFF
        static let sparkle = 0xFF8FC0
    }
    enum UI {
        static let dots = (x: x(122), y: 68.0, pill: 25.0, dot: 3.8, gap: 8.2, height: 3.6)
        static let skip = CGPoint(x: x(369), y: 68)
        static let cardY = y(530)
        static let cardRadius = 40.0
        static let title = [y(577), y(604)]
        static let subtitle = [y(642), y(661)]
        static let button = CGRect(x: x(162), y: y(715), width: x(330) - x(162), height: y(757) - y(715))
        static let sparkle = (x: x(183), y: y(268), size: 30.0)
    }
    enum Morph {
        static let frames = 33
        static let columns = 6
        static let frameSize = 256
        static let perPage = 16.0
        static let box = CGRect(x: 28, y: 136, width: 305, height: 305)
    }
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func mixedColor(_ a: Int, _ b: Int, _ progress: Double, _ alpha: Double = 1) -> Color {
        func channel(_ shift: Int) -> Double {
            MorphObjectOnboardingMotion.mix(Double((a >> shift) & 255), Double((b >> shift) & 255), progress) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: alpha)
    }
}
