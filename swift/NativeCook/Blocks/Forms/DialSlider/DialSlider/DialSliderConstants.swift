import SwiftUI

public enum DialConfig {
    /// Horizontal distance (pt) per value unit.
    /// Dense packing so ±100 fits a phone width with tick marks every 5 units.
    public static let tickSpacing: Double = 1.5
    public static let tickHeight: Double = 14
    public static let tickWidth: Double = 2

    /// Height of the tick nearest the center indicator.
    public static let centerTickHeight: Double = 28

    /// Tick mark interval along the ruler.
    public static let tickStep: Double = 5
    /// Major (brighter) ticks every N value units.
    public static let majorTickEvery: Double = 50

    /// Default value range (overridable via preset properties).
    public static let minValue: Double = -100
    public static let maxValue: Double = 100

    /// Preset button + progress ring geometry.
    public static let itemSize: Double = 58
    public static let itemGap: Double = 12
    public static let ringStrokeWidth: Double = 2
    public static let ringBackgroundStrokeWidth: Double = 1.5

    /// How long the centered value badge stays after drag ends.
    public static let valueBadgeDelay: TimeInterval = 0.65

    /// Preset strip fade while the ruler is being dragged.
    public static let presetDimOpacity: Double = 0.06
    /// Fade-out when drag begins — ~1s until presets are fully dimmed.
    public static let presetDimOutDuration: TimeInterval = 1.0
    /// Fade-in when drag ends.
    public static let presetDimInDuration: TimeInterval = 0.28
    /// Value badge appear/disappear.
    public static let valueBadgeInDuration: TimeInterval = 0.18
    public static let valueBadgeOutDuration: TimeInterval = 0.22

    /// Fling coast keeps full release velocity so a fast swipe still travels.
    /// Braking is applied only as the coast settles (snap spring at the end).
    /// Lower deceleration = longer coast; closer to 1 = slipperier.
    public static let flingDeceleration: Double = 0.995
}

public enum DialColors {
    public static let positive = Color(dialSliderHex: 0xFFD700)
    public static let negative = Color(dialSliderHex: 0xFFFFFF)
    public static let minorTick = Color(dialSliderHex: 0x3D3D3D)
    public static let majorTick = Color(dialSliderHex: 0xCCCCCC)
    public static let ringBackground = Color(dialSliderHex: 0x444444)
    public static let background = Color(dialSliderHex: 0x000000)
    public static let originDot = Color(dialSliderHex: 0x666666)
    /// Default `adjustedColor` for `DialSlider`: a dimmed accent for unselected, adjusted presets.
    public static let adjusted = Color(dialSliderHex: 0x8F8129)

    static let minorTickRGB = RGB(hex: 0x3D3D3D)
    static let majorTickRGB = RGB(hex: 0xCCCCCC)
}

struct RGB {
    var r: Double
    var g: Double
    var b: Double

    init(hex: UInt32) {
        r = Double((hex >> 16) & 0xFF)
        g = Double((hex >> 8) & 0xFF)
        b = Double(hex & 0xFF)
    }

    init(r: Double, g: Double, b: Double) {
        self.r = r
        self.g = g
        self.b = b
    }

    func mixed(toward other: RGB, amount t: Double) -> RGB {
        RGB(
            r: jsRound(r + (other.r - r) * t),
            g: jsRound(g + (other.g - g) * t),
            b: jsRound(b + (other.b - b) * t)
        )
    }

    var color: Color {
        Color(red: r / 255, green: g / 255, blue: b / 255)
    }
}

extension Color {
    init(dialSliderHex hex: UInt32, opacity: Double = 1) {
        self.init(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255,
            opacity: opacity
        )
    }
}
