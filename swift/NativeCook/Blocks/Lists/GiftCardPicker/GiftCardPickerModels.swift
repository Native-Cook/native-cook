import SwiftUI
import UIKit

enum GiftCardPickerTheme {
    static let referenceWidth: CGFloat = 402
    static let referenceHeight: CGFloat = 874
    static let page = Color(hex: 0xF2F2F6)
    static let tile = Color(hex: 0xFBFBFD)
    static let ink = Color(hex: 0x111114)
    static let ink2 = Color(hex: 0x5C5C62)
    static let muted = Color(hex: 0x8A8A90)
    static let orange = Color(hex: 0xFC4D00)
    static let lightSheet = Color(hex: 0xF5F5F9)
    static let skeleton = Color(hex: 0x28282E)
    static let skeletonBackground = Color(hex: 0x19191C)
    static let button = Color(hex: 0xECEBF0)
    static let loop: TimeInterval = 19.87
    static let resumeAfter: TimeInterval = 3.5

    static func scale(_ width: CGFloat) -> CGFloat { width / referenceWidth }
}

struct GiftCardPickerStock: Identifiable {
    enum Mark { case rings, petals, ribbon, lens, wordmark }

    let id: String
    let name: String
    let change: Int
    let lines: (String, String)
    let tail: String
    let mark: Mark
    let markBackground: Color
    var brand: Color {
        let colors: [String: UInt32] = ["mira": 0x1D5FD6, "tilecraft": 0x1F7A5C, "reelbox": 0xB3121B, "voltex": 0x4A8A12, "nova": 0x1D4FB0]
        return Color(hex: colors[id] ?? 0)
    }
    let backdropTint: Color
    let imageName: String
}

enum GiftCardPickerData {
    static let stocks = [
        GiftCardPickerStock(id: "mira", name: "Mira", change: -18, lines: ("Connects billions through the world's", "most popular apps while using a"), tail: "dv", mark: .rings, markBackground: Color(hex: 0x1F6FF2), backdropTint: Color(hex: 0x1B2A8A), imageName: "GiftCardPicker-card-mira.jpg"),
        GiftCardPickerStock(id: "tilecraft", name: "Tilecraft", change: -16, lines: ("Equips the modern world with essential", "software and massive cloud syste"), tail: "m", mark: .petals, markBackground: Color(hex: 0xF4F4F6), backdropTint: Color(hex: 0x9AA25C), imageName: "GiftCardPicker-card-tilecraft.jpg"),
        GiftCardPickerStock(id: "reelbox", name: "Reelbox", change: -33, lines: ("Entertains millions of households with an", "endless library of hit movies, seri"), tail: "e", mark: .ribbon, markBackground: Color(hex: 0x2A0D10), backdropTint: Color(hex: 0x3A0004), imageName: "GiftCardPicker-card-reelbox.jpg"),
        GiftCardPickerStock(id: "voltex", name: "Voltex", change: 40, lines: ("Powers the brains of the world's most", "advanced AI with unmatched ha"), tail: "r", mark: .lens, markBackground: Color(hex: 0x6FB51A), backdropTint: Color(hex: 0x2C5A10), imageName: "GiftCardPicker-card-voltex.jpg"),
        GiftCardPickerStock(id: "nova", name: "Nova", change: -12, lines: ("Facilitates millions of daily payments as", "the invisible backbone of global "), tail: "c", mark: .wordmark, markBackground: Color(hex: 0x1D4FB0), backdropTint: Color(hex: 0x2C58C8), imageName: "GiftCardPicker-card-nova.jpg")
    ]
}

enum GiftCardPickerTiming {
    static let present = 0.30
    static let dismiss = 0.24
    static let discover = 0.70
    static let discovering = 2.40
    static let revealAmount = 0.60
    static let closeFade = 0.09
    static let pushDelay = 0.03
    static let push = 0.25
    static let contentDelay = 0.53
    static let content = 0.20
    static let logo = 0.24
    static let labelSwitch = 0.27
    static let backdrop = 0.45
    static let description = 0.15
    static let pressIn = 0.12
    static let pressOut = 0.15
    static let reducedFade = 0.20
}
