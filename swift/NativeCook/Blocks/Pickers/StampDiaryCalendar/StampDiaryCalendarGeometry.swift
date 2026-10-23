import Foundation

/// Stamp Diary Calendar: every size and position of the screen, from theme.ts.
/// Positions are reference points: the pin's screen at 402 x 874 pt whose top safe area is 62 pt.
/// Foundation only, so the regression harness can compile it with swiftc.
enum StampDiaryCalendarTheme {
    static let refWidth = 402.0
    static let refHeight = 874.0
    static let refSafeTop = 62.0

    // space
    static let titleInset = 18.5
    static let headerCenterInset = 36.0
    static let headerCenterY = 96.0
    static let paperPitch = 28.0
    static let paperPhaseX = 26.0
    static let paperPhaseY = 1.3
    static let selectionInset = 4.0

    // radii
    static let headerRadius = 22.0
    static let selectionRadius = 8.0

    // sizes
    static let headerSize = 44.0
    static let closeGlyph = 14.0
    static let closeStroke = 2.0
    static let dot = 3.6
    static let dotGap = 7.6
    static let gridLine = 1.0
    static let selectionLine = 1.0

    // glass (SwiftUI blur radius standing in for expo-blur intensity 24, tint dark)
    static let glassIntensity = 24.0

    // menu
    static let menuWidth = 196.0
    static let menuRow = 44.0
    static let menuRadius = 16.0
    static let menuGap = 8.0
    static let menuPadX = 16.0
    static let menuTextSize = 15.0
    static let menuScaleFrom = 0.96
    static let menuInSeconds = 0.160
    static let menuOutSeconds = 0.120

    // grid
    static let weekdayTop = 452.5
    static let gridTop = 488.5
    static let rowHeight = 55.33
    static let rows = 6
    static let columnWidth = 402.0 / 7.0
    static let heroBottom = weekdayTop

    // type
    static let weekdaySize = 9.5
    static let daySize = 11.0
    static let yearSize = 17.0
    static let yearBaseline = 398.3
    static let monthSize = 30.0
    static let monthBaseline = 432.3
    static let sfAscent = 0.952
    static let sfLineHeight = 1.2

    // perforation
    static let perforationRadius = 0.265

    static let monthNames = [
        "January", "February", "March", "April", "May", "June",
        "July", "August", "September", "October", "November", "December",
    ]
    static let weekdays = ["S", "M", "T", "W", "T", "F", "S"]
}

/// A stamp in the collage: top-left corner, size, and dy, how much lower its right edge sits.
struct StampDiaryCalendarQuad: Equatable {
    var x: Double
    var y: Double
    var w: Double
    var h: Double
    var dy: Double
}

struct StampDiaryCalendarPileStamp {
    let art: Int
    let quad: StampDiaryCalendarQuad
    let blurTop: Double
    let blurBottom: Double
    let soft: Double
}

struct StampDiaryCalendarMemory {
    let day: Int
    let art: Int
    let w: Double
    let h: Double
    let twin: Int
}

enum StampDiaryCalendarData {
    static let pile: [StampDiaryCalendarPileStamp] = [
        .init(art: 5, quad: .init(x: 288, y: -34, w: 120, h: 150, dy: 10), blurTop: 0, blurBottom: 0, soft: 1),
        .init(art: 0, quad: .init(x: 233, y: 48, w: 122, h: 152, dy: 11), blurTop: 0, blurBottom: 0, soft: 1),
        .init(art: 1, quad: .init(x: 181, y: 100, w: 119, h: 150, dy: 12), blurTop: 0, blurBottom: 0, soft: 1),
        .init(art: 2, quad: .init(x: 129, y: 150, w: 121, h: 160, dy: 12), blurTop: 0, blurBottom: 0, soft: 1),
        .init(art: 4, quad: .init(x: 78, y: 229, w: 119, h: 130, dy: 9), blurTop: 0, blurBottom: 0, soft: 1),
        .init(art: 3, quad: .init(x: 24, y: 291, w: 122, h: 168, dy: 10), blurTop: 0, blurBottom: 2.6, soft: 0.55),
        .init(art: 6, quad: .init(x: -30, y: 355, w: 122, h: 150, dy: 10), blurTop: 1.6, blurBottom: 5, soft: 0.25),
    ]

    static let memories: [StampDiaryCalendarMemory] = [
        .init(day: 5, art: 0, w: 44, h: 44, twin: 1),
        .init(day: 9, art: 1, w: 35, h: 44, twin: 2),
        .init(day: 10, art: 2, w: 35, h: 44, twin: 3),
        .init(day: 12, art: 4, w: 44, h: 44, twin: 4),
        .init(day: 19, art: 6, w: 44, h: 44, twin: -1),
        .init(day: 23, art: 7, w: 35, h: 44, twin: -1),
        .init(day: 30, art: 8, w: 35, h: 44, twin: -1),
    ]

    static let freeSpot = StampDiaryCalendarQuad(x: 236, y: 214, w: 120, h: 150, dy: 10)
}

/// The postmark texture (an alpha field, drawn once per day).
enum StampDiaryCalendarPostmarkSpec {
    static let width = 512
    static let height = 256
    static let ringX = 384.0
    static let ringY = 128.0
    static let outer = 112.0
    static let inner = 78.0
    static let outerStroke = 9.0
    static let innerStroke = 6.0
    static let dayFont = 62.0
    static let arcFont = 25.0
    static let waves = 4
    static let waveFrom = 18.0
    static let waveTo = 262.0
    static let waveGap = 27.0
    static let waveAmp = 7.0
    static let waveLength = 66.0
    static let waveStroke = 7.0
    static let softness = 2.2
    static let fontName = "Helvetica-Bold"
}

/// Where the postmark sits on a stamp.
enum StampDiaryCalendarInkMark {
    static let x = 0.17
    static let y = -0.11
    static let radius = 0.27
    static let rotation = -0.17
}

/// Ink and paper colours used inside the shader (sRGB). Mirrored as constants in the .metal file.
enum StampDiaryCalendarShaderColors {
    static let paperBack = (0.93, 0.905, 0.85)
    static let inkDry = (0.13, 0.14, 0.2)
    static let inkWet = (0.16, 0.2, 0.38)
}
