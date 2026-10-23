import SwiftUI

/// PostScript name of the bundled font (see Resources/Fonts; registered by `CardCarouselOnboardingFonts`).
enum AppFont {
    /// `CaveatBrush-Regular.ttf`
    static let caveatBrush = "CaveatBrush-Regular"
}

extension Color {
    /// `Color(cardCarouselHex: 0x002434)` — convenience for the hex colours used throughout the RN styles.
    init(cardCarouselHex hex: UInt32, opacity: Double = 1) {
        let red = Double((hex >> 16) & 0xFF) / 255
        let green = Double((hex >> 8) & 0xFF) / 255
        let blue = Double(hex & 0xFF) / 255
        self.init(red: red, green: green, blue: blue, opacity: opacity)
    }
}
