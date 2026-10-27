import SwiftUI

/// The window and its insets, with the scale factors the React Native block derives from them.
/// React Native reads `useWindowDimensions()` (the whole screen) and `useSafeAreaInsets()`.
struct ConfettiGiftOfferMetrics: Equatable {
    var width: CGFloat
    var height: CGFloat
    var top: CGFloat
    var bottom: CGFloat
    /// React Native's `fontScale` (see `ConfettiGiftOfferMetrics.fontScale(for:)`).
    var fontScale: CGFloat

    static let zero = ConfettiGiftOfferMetrics(width: 402, height: 874, top: 62, bottom: 34, fontScale: 1)

    /// "u = height / 874": the reference's vertical unit (its screen is 402 × 874 pt).
    var u: CGFloat { height / 874 }
    /// "w = width / 402".
    var w: CGFloat { width / 402 }
    /// `largeText = fontScale > 1.3 || height < 700` (ConfettiGiftOffer.tsx:211): the scenes flow and scroll.
    var largeText: Bool { fontScale > 1.3 || height < 700 }

    /// The multipliers React Native uses for each Dynamic Type size on iOS.
    static func fontScale(for size: DynamicTypeSize) -> CGFloat {
        switch size {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
}

private struct ConfettiGiftOfferFontScaleKey: EnvironmentKey {
    static let defaultValue: CGFloat = 1
}

extension EnvironmentValues {
    /// React Native's `fontScale`, read by every text of the block.
    var confettiGiftOfferFontScale: CGFloat {
        get { self[ConfettiGiftOfferFontScaleKey.self] }
        set { self[ConfettiGiftOfferFontScaleKey.self] = newValue }
    }
}

/// The block's colours, copied from `styles` in ConfettiGiftOffer.tsx.
enum ConfettiGiftOfferPalette {
    static func hex(_ value: UInt32, alpha: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((value >> 16) & 255) / 255,
            green: Double((value >> 8) & 255) / 255,
            blue: Double(value & 255) / 255,
            opacity: alpha
        )
    }

    static let screen = hex(0xE7EDFE)
    static let ink = hex(0x1C1C1E)
    static let green = hex(0x45BA51)
    static let greenDark = hex(0x2F7A36)
    static let button = hex(0x171717)
    static let grey = hex(0x8E8E93)
    static let blue = hex(0x3A7FE9)
}
