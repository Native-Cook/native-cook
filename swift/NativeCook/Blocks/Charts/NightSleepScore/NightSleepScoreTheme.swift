import SwiftUI

/// Tokens of the block, the same names and values as the React Native theme.ts (design points of a 402 pt screen).
enum NightSleepScoreTheme {
    struct Stages {
        let awake: UInt32, rem: UInt32, core: UInt32, deep: UInt32
        func hex(_ stage: NightSleepScoreStage) -> UInt32 {
            switch stage {
            case .awake: awake
            case .rem: rem
            case .core: core
            case .deep: deep
            }
        }
        func color(_ stage: NightSleepScoreStage) -> Color { Color(hex: hex(stage)) }
        func rgba(_ stage: NightSleepScoreStage) -> NightSleepScoreRGBA { NightSleepScoreRGBA(hex: hex(stage)) }
    }

    enum Day {
        static let ink = NightSleepScoreRGBA(hex: 0x101113)
        static let secondary = NightSleepScoreRGBA(hex: 0x8D8D92)
        static let divider = NightSleepScoreRGBA(hex: 0xE4E3EB)
        static let border = NightSleepScoreRGBA(hex: 0xFFFFFF, a: 0.75)
        static let accent = NightSleepScoreRGBA(hex: 0x128CF0)
        static let highlight = NightSleepScoreRGBA(hex: 0x128CF0)
        static let stages = Stages(awake: 0xEF806B, rem: 0x32B4D5, core: 0x128CF0, deep: 0x4934A2)
    }

    enum Night {
        static let ink = NightSleepScoreRGBA(hex: 0xF4F5FF)
        static let secondary = NightSleepScoreRGBA(r: 214, g: 220, b: 255, a: 0.62)
        static let divider = NightSleepScoreRGBA(hex: 0xFFFFFF, a: 0.13)
        static let border = NightSleepScoreRGBA(hex: 0xFFFFFF, a: 0.16)
        static let accent = NightSleepScoreRGBA(hex: 0x7CC0FF)
        static let highlight = NightSleepScoreRGBA(r: 186, g: 210, b: 255, a: 1)
        static let stages = Stages(awake: 0xFF8F7A, rem: 0x5CE2F6, core: 0x3F9DFF, deep: 0xA58CFF)
        static let trackRing = Color.white.opacity(0.09)
        static let playhead = Color.white
    }

    /// Page and card colours (NightSleepScore.tsx `C`).
    static let page = Color(hex: 0xF1F0F6)
    static let card = Color(red: 246 / 255, green: 246 / 255, blue: 1).opacity(0.96)
    static let teal = Color(hex: 0x13C9BB)
    static let orange = Color(hex: 0xF67D65)
    static let blue = Color(hex: 0x4E63F8)
    static let shadow = Color(hex: 0x2F5BD6).opacity(0.2)

    enum Card {
        static let width = 370.0, height = 317.0, radius = 28.0
        enum Chart {
            static let left = 207.8, top = 152.0, width = 145.5, height = 82.0, touchInset = 12.0
            static let barHeight = 14.0, barRadius = 4.0, barGap = 0.7
            static func level(_ stage: NightSleepScoreStage) -> Double {
                switch stage {
                case .awake: 7.7
                case .rem: 28.8
                case .core: 52.5
                case .deep: 73.4
                }
            }
            static let grid = [18.7, 40.3, 63.0]
            static let gridColor = Color(hex: 0xD3D6E2)
            static let gridDash: [Double] = [1.2, 2.4]
            static let connectorColor = Color(hex: 0xA9CCF3).opacity(0.62)
            static let connectorWidth = 3.2
            static let coreTrack = Color(hex: 0xD2E4F8)
        }
        enum Ring {
            static let cx = 304.8, cy = 66.0, radius = 39.0, stroke = 16.0
        }
        /// Card heights (0 top, 1 bottom) where each layer meets the night front.
        enum At {
            static let label = 28.0 / 317, title = 47.0 / 317, duration = 80.0 / 317, durationValue = 100.0 / 317
            static let ring = 66.0 / 317, divider1 = 134.0 / 317
            static let legend = [163.0 / 317, 185.0 / 317, 207.0 / 317, 229.0 / 317]
            static let chart = 193.0 / 317, divider2 = 247.0 / 317, readout = 276.0 / 317, readoutSub = 298.0 / 317
        }
        enum Meteor {
            static let fromX = 252.0, fromY = 16.0, toX = 160.0, toY = 64.0, tail = 44.0
        }
    }

    enum Aurora {
        static let transitionHalfWidth = 1.4
        static let core = 2.2, hotCore = 0.8, hotCoreOpacity = 0.55
        static let glowWidth = 6.0, glowBlur = 3.5, glowOpacity = 0.75
        static let curtains: [(width: Double, lift: Double, blur: Double, opacity: Double)] = [(14, 8, 6, 0.55), (28, 19, 11, 0.3)]
        static let headGlow = 7.0, headGlowBlur = 5.0, headCore = 2.4
        static let playheadWidth = 1.0
    }

    enum Rail {
        static let top = 0.7, height = 57.5, radius = 20.0, icon = 30.7, iconCentre = 28.3
        static let textLeft = 54.0, titleTop = 10.3, valueTop = 29.3, textSize = 15.0, valueSize = 14.5, textLine = 18.0
        enum Heart {
            static let left = 16.7, width = 61.6, heartSize = 17.5, heartCentre = 22.3, lineLeft = 20.0
            static let lines: [(top: Double, width: Double)] = [(33.8, 21.7), (40.5, 16)]
            static let lineHeight = 1.7
        }
        static let sleep = (left: 82.7, width: 109.0)
        static let readiness = (left: 195.0, width: 140.3)
        static let fitness = (left: 340.7, width: 112.0)
        static let tile = Color.white
        static let chip = Color(hex: 0xEFEEF3)
        static let chipBorder = Color(hex: 0xCBCAD1)
        static let chipRim = Color.white.opacity(0.85)
        static let line = Color(hex: 0xCFCFD5)
        static let title = Color(hex: 0x8D8D92)
        static let value = Color(hex: 0x101113)
        static let heart = Color(hex: 0xEE3D83)
        static let track = Color(hex: 0xDEDDE4)
        static let gaugeLight = Color(hex: 0xB9D6EE)
        static let gaugeBlue = Color(hex: 0x4A9BE0)
        static let fitnessRed = Color(hex: 0xEE2D6E)
        static let fitnessPink = Color(hex: 0xF7B3CB)
        static let fitnessGreen = Color(hex: 0x55D45A)
        static let fitnessCyan = Color(hex: 0x52C7EE)
    }

    enum Tab {
        static let width = 290.0, height = 60.0, radius = 30.0, bottom = 22.0, padding = 4.0
        static let item = 94.0, itemHeight = 52.0, icon = 26.5, iconCentre = 20.5
        static let labelSize = 10.2, labelLine = 13.0, labelTop = 35.5
        static let edgeHeight = 118.0, edgeSolidFrom = 0.36
        static let lensGlassTint = Color(red: 214 / 255, green: 214 / 255, blue: 224 / 255).opacity(0.55)
        static let lensFallback = Color(red: 226 / 255, green: 226 / 255, blue: 233 / 255).opacity(0.72)
        static let lensSheen = Color.white.opacity(0.55)
        static let lensSheenTo = 0.5
        static let lensRim = Color.white.opacity(0.9)
        static let outline = Color(red: 60 / 255, green: 60 / 255, blue: 90 / 255).opacity(0.12)
        static let glassTint = Color(red: 222 / 255, green: 228 / 255, blue: 250 / 255).opacity(0.6)
        static let fallback = Color(red: 248 / 255, green: 248 / 255, blue: 252 / 255).opacity(0.72)
        static let border = Color.white.opacity(0.9)
        static let active = Color(hex: 0x167FE8)
        static let ink = Color(hex: 0x101113)
        static let cardWhite = Color.white
        static let shadow = Color.black.opacity(0.12)
    }

    /// Readiness gauge, measured on the pin: centre (304, 65.3) in the card.
    enum Gauge {
        static let cx = 304.0, cy = 65.3, r = 44.5, stroke = 11.0
        static let from = 0.53, to = 0.97, lightFrom = 0.72, lightTo = 0.86, dot = 0.842, dotR = 3.4
        static let numberBase = 5.0, numberSize = 30.0
        static let track = Color(hex: 0xE4E3EB), light = Color(hex: 0xB9D6EE), blue = Color(hex: 0x3A8DDE)
    }

    /// Sky palette (sRGB), keyed by the playhead.
    enum Sky {
        static let starCell = 11.0, starChance = 0.6
    }
}

/// A colour as components, so a text colour can be mixed between its day and night values (like interpolateColor).
struct NightSleepScoreRGBA {
    let r: Double, g: Double, b: Double, a: Double
    init(r: Double, g: Double, b: Double, a: Double) { self.r = r / 255; self.g = g / 255; self.b = b / 255; self.a = a }
    init(hex: UInt32, a: Double = 1) {
        self.init(r: Double((hex >> 16) & 255), g: Double((hex >> 8) & 255), b: Double(hex & 255), a: a)
    }
    var color: Color { Color(.sRGB, red: r, green: g, blue: b, opacity: a) }
    func mixed(_ other: NightSleepScoreRGBA, _ t: Double) -> Color {
        let k = min(1, max(0, t))
        return Color(.sRGB, red: r + (other.r - r) * k, green: g + (other.g - g) * k,
                     blue: b + (other.b - b) * k, opacity: a + (other.a - a) * k)
    }
}
