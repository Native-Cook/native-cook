import SwiftUI

// Application-wide design tokens. Feature-specific palettes stay inside their feature.
// Mirrors src/shared/theme/tokens.ts.
enum AppColors {
    static let background = Color(css: "#09090d")
    static let backgroundRaised = Color(css: "#111118")
    static let surface = Color(css: "#17171f")
    static let surfaceSoft = Color(css: "rgba(255, 255, 255, 0.055)")
    static let border = Color(css: "rgba(255, 255, 255, 0.12)")
    static let borderStrong = Color(css: "rgba(255, 255, 255, 0.2)")
    static let text = Color(css: "#f8f7fb")
    static let textMuted = Color(css: "#a7a5b2")
    static let textSubtle = Color(css: "#73717f")
    static let accent = Color(css: "#d8ff67")
    static let accentInk = Color(css: "#111506")
    static let white = Color(css: "#ffffff")
    static let black = Color(css: "#000000")
}

enum Spacing {
    static let xs: CGFloat = 4
    static let sm: CGFloat = 8
    static let md: CGFloat = 12
    static let lg: CGFloat = 16
    static let xl: CGFloat = 24
    static let xxl: CGFloat = 32
    static let huge: CGFloat = 48
}

enum Radii {
    static let sm: CGFloat = 10
    static let md: CGFloat = 16
    static let lg: CGFloat = 24
    static let pill: CGFloat = 999
}

enum TypeScale {
    static let display: CGFloat = 40
    static let title: CGFloat = 25
    static let heading: CGFloat = 18
    static let body: CGFloat = 15
    static let label: CGFloat = 12
    static let micro: CGFloat = 10
}

enum RewardCardMotion {
    /// Reanimated `withSpring(value, { duration: 400, dampingRatio: 0.8 })`.
    static let touchSpring = Animation.spring(response: 0.4, dampingFraction: 0.8)
    /// Seconds before the card settles back to the centre after a touch.
    static let settleDelay: TimeInterval = 0.26
}

/// Shared cubic-bezier curves used by the RN sources.
enum Curves {
    static func easeOutExpoLike(_ duration: TimeInterval) -> Animation {
        .timingCurve(0.23, 1, 0.32, 1, duration: duration)
    }
}
