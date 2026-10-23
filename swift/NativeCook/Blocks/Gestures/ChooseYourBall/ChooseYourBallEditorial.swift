import SwiftUI

// What the screen says about a ball: how its name is set, the three chips, and the colour the
// page takes from it. Mirrors ballEditorial.ts and palette.ts.

enum ChooseYourBallPalette {
    /// Stage ground: the near-black the whole screen stands on (palette.ts STAGE_INK).
    static let stageInk: UInt32 = 0x06080C
    static let gold: UInt32 = 0xF0C548
    static let buttonSurface: UInt32 = 0xF4F7FA
    static let buttonInk: UInt32 = 0x071015
}

/// Dynamic Type is followed up to this multiple and no further, so the header stays off the ball
/// and the ball off the button at every accessibility size (constants.ts MAX_TYPE_SCALE).
let chooseYourBallMaxTypeScale: CGFloat = 1.6

enum ChooseYourBallEditorial {
    /// The name split for setting: everything before the last word, then the last word.
    /// Every ball is "<Something> Ball", so the second line is always BALL.
    static func wordmark(_ label: String) -> (hero: String, tail: String) {
        let parts = label.split(whereSeparator: \.isWhitespace).map(String.init)
        guard parts.count >= 2 else { return (label.uppercased(), "") }
        return (parts.dropLast().joined(separator: " ").uppercased(), parts[parts.count - 1].uppercased())
    }

    /// Every hero word the wordmark can show, so its size is fitted to the widest of them once.
    static let heroes: [String] = ChooseYourBallID.allCases.map { wordmark($0.spec.label).hero }

    static func tierLabel(_ tier: Int) -> String {
        switch tier {
        case 1: "STANDARD"
        case 2: "SITUATIONAL"
        case 3: "RARE"
        default: "CHERISH"
        }
    }

    /// The three chips under the ball, in reading order.
    static func facts(_ spec: ChooseYourBallSpec) -> [String] {
        let multiplier = spec.catchMod == spec.catchMod.rounded()
            ? String(Int(spec.catchMod))
            : String(spec.catchMod)
        return ["×\(multiplier) CATCH", tierLabel(spec.tier), keyword(spec.blurb)]
    }

    /// One word lifted out of the blurb: the first of this list that the blurb contains.
    private static func keyword(_ blurb: String) -> String {
        let known = [
            "night", "cave", "water", "surfing", "fishing", "turn", "first", "friend", "gift",
            "speed", "heavy", "light", "fast", "slow", "moon", "sun", "bug", "dark",
        ]
        let lower = blurb.lowercased()
        return (known.first { lower.contains($0) } ?? "field").uppercased()
    }

    /// The ground colour the page takes from a ball's aura: a lean toward the hue, not a wash.
    /// The reach shrinks as the aura gets brighter, so every ball weighs the same on the page.
    static func ground(aura: UInt32) -> ChooseYourBallRGB {
        let color = ChooseYourBallRGB(hex: aura)
        let luma = 0.2126 * color.red + 0.7152 * color.green + 0.0722 * color.blue
        let reach = 0.3 * (1 - luma * 0.55)
        let ink = ChooseYourBallRGB(hex: ChooseYourBallPalette.stageInk)
        func mix(_ channel: Double, _ ink: Double) -> Double {
            // Rounded to 8 bits like the reference, which builds a hex string here.
            (min(255, ink * 255 + (channel - ink) * 255 * reach)).rounded() / 255
        }
        return ChooseYourBallRGB(
            red: mix(color.red, ink.red), green: mix(color.green, ink.green), blue: mix(color.blue, ink.blue)
        )
    }
}

/// An sRGB colour as three 0...1 channels, for the maths the block does on colours itself.
struct ChooseYourBallRGB: Equatable {
    var red: Double
    var green: Double
    var blue: Double

    init(red: Double, green: Double, blue: Double) {
        self.red = red
        self.green = green
        self.blue = blue
    }

    init(hex: UInt32) {
        red = Double((hex >> 16) & 0xFF) / 255
        green = Double((hex >> 8) & 0xFF) / 255
        blue = Double(hex & 0xFF) / 255
    }

    var color: Color { Color(.sRGB, red: red, green: green, blue: blue, opacity: 1) }

    var uiColor: UIColor { UIColor(red: red, green: green, blue: blue, alpha: 1) }
}

extension Color {
    init(chooseYourBallHex hex: UInt32, opacity: Double = 1) {
        let rgb = ChooseYourBallRGB(hex: hex)
        self.init(.sRGB, red: rgb.red, green: rgb.green, blue: rgb.blue, opacity: opacity)
    }
}
