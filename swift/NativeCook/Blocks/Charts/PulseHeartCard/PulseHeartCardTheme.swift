import SwiftUI

/// Colours and layout tokens of Pulse Heart Card (theme.ts). Every position and size is in "ref pt":
/// points of the pin's 402 pt wide screen. The view multiplies them by `screenWidth / 402`; Y values
/// are measured from the top of the screen, whose safe area ends at 62 pt.
enum PulseHeartCardTheme {
    enum Colors {
        static let background = Color(hex: 0xF3F2F8)
        static let card = Color(hex: 0xF8F7FB)
        static let border = Color(hex: 0xD4D3D9)
        static let ink = Color(hex: 0x000000)
        static let muted = Color(hex: 0x88878D)
        static let faint = Color(hex: 0xB9B8C0)
        static let heart = Color(hex: 0xF42E59)
        static let heartDark = Color(hex: 0xE52650)
        static let bpmUnit = Color(hex: 0x55343D)
        static let flash = Color(hex: 0xFFFFFF)
        static let chartPink = Color(hex: 0xF09CAF)
        static let activeBlue = Color(hex: 0x167FE8)
        static let metricBlue = Color(hex: 0x378DDD)
        static let metricPink = Color(hex: 0xDF61D2)
        static let metricGreen = Color(hex: 0x3ACA9B)
        static let ringRed = Color(hex: 0xEE2462)
        static let ringYellow = Color(hex: 0xF1C13A)
        static let supportValue = Color(hex: 0x55545A)
        static let tabBar = Color.rgba(248, 248, 252, 0.72)
        static let tabGlassTint = Color.rgba(250, 250, 253, 0.55)
        static let tabBorder = Color.rgba(255, 255, 255, 0.9)
        static let shadow = Color.rgba(0, 0, 0, 0.12)
    }

    /// Spacing scale of the pin (4 pt base; values within 1 pt of the grid are snapped).
    enum Space {
        static let hairline = 0.8
        static let chipGap = 4.0
        static let chipPadding = 10.0
        static let chipIconGap = 10.0
        static let tabPadding = 4.0
        static let screenInset = 32.0
        static let metricGap = 26.0
    }

    enum Layout {
        /// Metric chips strip (y 62-120).
        static let stripTop = 62.0
        static let stripHeight = 58.0
        static let stripRadius = 24.0
        static let chipFitness = 137.0
        static let chipHeart = 118.0
        static let chipVitals = 116.0
        static let chipIcon = 30.0
        /// The heart canvas spans the screen width from under the strip to behind the headline.
        static let heartCanvasTop = 120.0
        static let heartCanvasBottom = 500.0
        /// The reading inside the heart. Its centre of scale is the heart's centre (y 290).
        static let numberTop = 231.0
        static let numberSize = 76.0
        static let numberLine = 84.0
        static let numberTracking = -2.2
        static let unitTop = 314.0
        static let unitSize = 16.0
        static let unitLine = 20.0
        static let heartCentreY = 290.0
        static let headlineTop = 448.5
        static let headlineLeft = 33.0
        static let headlineWidth = 338.0
        static let headlineHeight = 72.0
        static let headlineSize = 31.0
        static let headlineLine = 36.0
        static let bodyTop = 524.8
        static let bodyLeft = 33.0
        static let bodyWidth = 338.0
        static let bodyHeight = 82.0
        static let bodySize = 22.0
        static let bodyLine = 26.0
        // Range chart.
        static let chartTop = 631.0
        static let chartLeft = 32.0
        static let chartWidth = 338.0
        static let chartHeight = 100.0
        static let chartTitleSize = 13.8
        static let chartTitleLine = 19.0
        static let chartTitleInset = 1.3
        static let chartPlotTop = 19.0
        static let chartPlotHeight = 64.0
        static let chartBarBlur = 0.8
        static let chartLabelTop = 83.0
        static let chartLabelSize = 11.0
        static let chartLabelLine = 14.0
        static let chartOrigin = 650.0
        // Support metrics.
        static let supportTop = 749.0
        static let supportWidth = 156.0
        static let supportTitleSize = 14.5
        static let supportTitleLine = 18.0
        static let supportValueSize = 21.5
        static let supportValueLine = 26.0
        static let supportValueGap = 6.8
        static let supportNoteSize = 14.5
        static let supportNoteLine = 18.0
        static let supportNextTop = 840.0
        /// Floating tab bar, measured on the pin: 290 x 60 pt, its bottom 22 pt above the screen's.
        static let tabWidth = 290.0
        static let tabHeight = 60.0
        static let tabRadius = 30.0
        static let tabBottom = 22.0
        static let tabItem = 94.0
        static let tabItemHeight = 52.0
        static let tabIcon = 22.0
        static let tabIconCentre = 20.5
        static let tabLabelSize = 11.5
        static let tabLabelLine = 14.0
        static let tabLabelTop = 35.0
    }

    /// Selection lens: glass tinted to the pin's #E2E2E9 pill, a white rim and a sheen over its top half.
    enum Lens {
        static let glassTint = Color.rgba(214, 214, 224, 0.55)
        static let fallback = Color.rgba(226, 226, 233, 0.72)
        static let sheen = Color.rgba(255, 255, 255, 0.55)
        static let sheenTo = 0.5
        static let rim = Color.rgba(255, 255, 255, 0.9)
        static let rimWidth = 1.0
    }

    /// Tab bar lens: a spring slide that stretches 12 % on the way.
    enum TabMotion {
        /// withSpring({ duration: 400, dampingRatio: 0.8 }) is a perceptual-duration spring: SwiftUI's
        /// `.spring(duration:bounce:)` with bounce = 1 - dampingRatio is the same curve.
        static let lens = Animation.spring(duration: 0.4, bounce: 0.2)
        static let stretch = 1.12
        static let stretchSeconds = 0.12
        static let settle = Animation.spring(duration: 0.3, bounce: 0.3)
    }

    /// Reference page geometry.
    static let referenceWidth = 402.0
    static let referenceSafeTop = 62.0
    /// The page scrolls a little: the pin's next row of metrics sits under the tab bar.
    static let referenceHeight = 912.0
}
