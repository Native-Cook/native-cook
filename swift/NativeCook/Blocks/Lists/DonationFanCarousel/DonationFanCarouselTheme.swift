import SwiftUI

/// Colours, measurements and fan values of the React Native block: `theme.ts`, plus the values its
/// `StyleSheet`s and `ReactionMarks.tsx` write inline. Everything is in points of a 402 pt wide
/// screen and is multiplied by the stage scale where the React Native code multiplies by `scale`.
enum DonationFanCarouselTheme {
    /// `colors` in `theme.ts`.
    enum Colors {
        static let background = Color(hex: 0xFFFFFF)
        static let ink = Color(hex: 0x070707)
        static let detailAmount = Color(hex: 0x303030)
        static let secondary = Color(hex: 0x8F8F8F)
        static let accent = Color(hex: 0xC8F77D)
        static let accentDark = Color(hex: 0x99CE4A)
        static let heart = Color(hex: 0xF43D43)
        static let heartDark = Color(hex: 0xB81421)
        /// `styles.card.backgroundColor`.
        static let cardFill = Color(hex: 0xE8E8E8)
        /// `styles.card.shadowColor` and `shadowOpacity`.
        static let cardShadow = Color(hex: 0x171717, opacity: 0.13)
        /// `styles.heart.shadowColor` and `shadowOpacity`.
        static let heartShadow = Color(hex: 0x8B0F18, opacity: 0.22)
        /// `styles.coin.shadowColor` and `shadowOpacity`.
        static let coinShadow = Color(hex: 0x4A691E, opacity: 0.22)
        /// Gradient stop at offset 0 of the heart (`#FF8B8E`).
        static let heartLight = Color(hex: 0xFF8B8E)
        /// Stroke of the heart's highlight (`#FFD2D2`).
        static let heartHighlight = Color(hex: 0xFFD2D2)
        /// `styles.coinInner.borderColor` (`rgba(255,255,255,0.72)`).
        static let coinInnerBorder = Color(hex: 0xFFFFFF, opacity: 0.72)
        /// `styles.seed.backgroundColor` (`rgba(255,255,255,0.75)`).
        static let coinSeed = Color(hex: 0xFFFFFF, opacity: 0.75)
    }

    /// `layout` in `theme.ts`, measured from the 748 x 462 source frame.
    enum Layout {
        static let sourceWidth: CGFloat = 748
        /// The 402 pt screen the numbers are normalised to (`scale = stageWidth / 402`).
        static let referenceWidth: CGFloat = 402
        /// Stage = window width - 16, with the source frame's 462 / 748 aspect.
        static let stageInset: CGFloat = 16
        static let stageAspectHeight: CGFloat = 462

        enum Header {
            static let top: CGFloat = 22.3
            static let side: CGFloat = 59
            static let fontSize: CGFloat = 15
            static let counterSize: CGFloat = 12.5
            static let lineHeight: CGFloat = 19
            /// `styles.counter.top`, a relative offset that is not scaled.
            static let counterTop: CGFloat = -1.3
            static let headingTracking: CGFloat = -0.35
            static let counterTracking: CGFloat = -0.25
        }

        enum Deck {
            static let centerX: CGFloat = 199.3
            static let centerY: CGFloat = 121
            static let card: CGFloat = 134.7
            static let radius: CGFloat = 9
            static let border: CGFloat = 1.2
            /// `styles.card.shadowRadius` and `shadowOffset.height`.
            static let shadowRadius: CGFloat = 4
            static let shadowOffsetY: CGFloat = 2
            /// `styles.activeBorder.zIndex`.
            static let activeBorderZ: Double = 130
        }

        enum Metadata {
            static let top: CGFloat = 203
            static let nameSize: CGFloat = 9.3
            static let detailSize: CGFloat = 9
            static let nameLine: CGFloat = 11.7
            static let detailLine: CGFloat = 12
            static let nameTracking: CGFloat = -0.1
            static let detailTracking: CGFloat = -0.08
        }
    }

    /// `fan` in `theme.ts`: one value per distance 0, 1, 2 and 3 from the active portrait.
    enum Fan {
        static let x: [Double] = [0, 64, 87, 94]
        static let y: [Double] = [0, 1, 3, 5]
        static let scale: [Double] = [1, 0.76, 0.63, 0.52]
        static let rotate: [Double] = [0, 7, 9, 11]
    }

    /// The three hearts of `Heart` in `ReactionMarks.tsx`.
    enum Hearts {
        struct Placement {
            let x: Double
            let y: Double
            let rotate: Double
            let size: Double
            let rise: Double
        }

        static let placements: [Placement] = [
            Placement(x: 45, y: -59, rotate: 13, size: 24, rise: 16),
            Placement(x: -54, y: -15, rotate: -10, size: 25, rise: 9),
            Placement(x: 16, y: 34, rotate: 16, size: 28, rise: 13),
        ]
        /// `amount * 1.35 - index * 0.08`.
        static let staggerGain = 1.35
        static let staggerStep = 0.08
        /// `interpolate(delayed, [0, 1], [0.72, 1])`.
        static let scaleFrom = 0.72
        static let shadowRadius: CGFloat = 3
        static let shadowOffsetY: CGFloat = 2
        /// `viewBox="0 0 32 30"`.
        static let viewBoxWidth: CGFloat = 32
        static let viewBoxHeight: CGFloat = 30
        /// `strokeWidth="1.7"` of the highlight.
        static let highlightWidth: CGFloat = 1.7
    }

    /// The coin of `ReactionMarks`.
    enum Coin {
        static let size: CGFloat = 22
        static let border: CGFloat = 1
        /// `interpolate(amount, [0, 1], [0.74, 1])`.
        static let scaleFrom = 0.74
        static let shadowRadius: CGFloat = 4
        static let shadowOffsetY: CGFloat = 2
        /// `coinInner`: 72 % of the coin's content box, a 1 pt border, radius 8 and 3 pt of padding.
        static let innerFraction: CGFloat = 0.72
        static let innerBorder: CGFloat = 1
        static let innerRadius: CGFloat = 8
        static let innerPaddingBottom: CGFloat = 3
        static let innerGap: CGFloat = 2
        /// The three seeds: width 2, heights 4, 6 and 3.5, `borderRadius: 2` (not scaled).
        static let seedWidth: CGFloat = 2
        static let seedHeights: [CGFloat] = [4, 6, 3.5]
        static let seedRadius: CGFloat = 2
        /// `t < 2500 ? 0 : 3` in `coinStyle`.
        static let secondHalfStartMs = 2500.0
    }

    /// Font files: React Native asks iOS for the family "Avenir Next" at weights 500, 600 and 400.
    enum Fonts {
        static let medium = "AvenirNext-Medium"
        static let demiBold = "AvenirNext-DemiBold"
        static let regular = "AvenirNext-Regular"
    }
}
