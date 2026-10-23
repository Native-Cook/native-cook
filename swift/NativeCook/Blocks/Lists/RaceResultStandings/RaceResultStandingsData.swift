import SwiftUI

enum RaceResultStandingsData {
    static let referenceWidth = RaceResultStandingsTheme.REF_W
    static let referenceTop = RaceResultStandingsTheme.REF_TOP
    static let drivers = [
        Driver(name: "Mira Chen", code: "CHE", team: .scarlet, mark: .fin),
        Driver(name: "Alex Vale", code: "VAL", team: .tidewave, mark: .twin),
        Driver(name: "Sam Ives", code: "IVE", team: .scarlet, mark: .stair),
        Driver(name: "Noah Reed", code: "REE", team: .kestrel, mark: .chevron),
        Driver(name: "Theo Marsh", code: "MAR", team: .bluecrest, mark: .twin),
        Driver(name: "Kai Lindgren", code: "LIN", team: .crest, mark: .twin),
    ]
    static let sessions = [
        Session(word: "Race", order: [0, 1, 2, 3, 4, 5], points: [25, 18, 15, 12, 10, 8], delta: [1, 2, 0, 2, 0, 4], fastest: "Jonah Price (1:31.482)"),
        Session(word: "Sprint", order: [1, 3, 0, 5, 2, 4], points: [8, 7, 6, 5, 4, 3], delta: [-1, 3, -2, 1, 0, 5], fastest: "Alex Vale (1:32.906)"),
    ]
    static let slots: [[Int]] = sessions.map { session in
        var values = Array(repeating: 0, count: 6)
        for (slot, driver) in session.order.enumerated() { values[driver] = slot }
        return values
    }

    struct Driver { let name, code: String; let team: Team; let mark: Mark }
    struct Session { let word: String; let order, points, delta: [Int]; let fastest: String }
    enum Team { case scarlet, tidewave, kestrel, bluecrest, crest
        var name: String { switch self { case .scarlet: "Scarlet"; case .tidewave: "Tidewave"; case .kestrel: "Kestrel"; case .bluecrest: "Bluecrest"; case .crest: "Crest Junior" } }
        var color: Color { RaceResultStandingsTheme.color(textHex) }
        var markColor: Color { RaceResultStandingsTheme.color(markHex) }
        private var textHex: UInt32 { switch self { case .scarlet: 0xD72A36; case .tidewave: 0x16BFB7; case .kestrel: 0xEC601B; case .bluecrest: 0x2966CC; case .crest: 0x2B66CB } }
        private var markHex: UInt32 { switch self { case .scarlet: 0xE92432; case .tidewave: 0x00C9C0; case .kestrel: 0xEC601B; case .bluecrest: 0x2966CC; case .crest: 0x2B66CB } }
    }
    enum Mark { case fin, twin, stair, chevron }

    /// The colour of each driver's car on the circuit (dot, trail, tag), with the tag's code colour. Sam Ives and
    /// Kai Lindgren take a light shade of their team so the six cars can be told apart (Bluecrest and Crest
    /// Junior are near-twins, and Sam shares Mira's Scarlet). The list keeps the team colours.
    static let carShade: [(fill: UInt32, ink: UInt32)] = [
        (0xD72A36, 0xFFFFFF), (0x16BFB7, 0xFFFFFF), (0xFF8C93, 0x2A0407),
        (0xEC601B, 0xFFFFFF), (0x2966CC, 0xFFFFFF), (0x8DB4F5, 0x06142E),
    ]
}

/// The RN `theme.ts` contract. Layout remains in the canonical 402 pt artboard; the renderer
/// applies one uniform scale after mapping the artboard's top to the actual safe-area top.
enum RaceResultStandingsTheme {
    static let REF_W = 402.0, REF_TOP = 62.0, ASCENT = 0.968
    enum FONT { static let medium = "Montserrat-Medium", semibold = "Montserrat-SemiBold", bold = "Montserrat-Bold", extrabold = "Montserrat-ExtraBold" }
    enum colors {
        static let ink: UInt32 = 0xFFFFFF, muted: UInt32 = 0x9B9B9B, hairline: UInt32 = 0x413D41, base: UInt32 = 0x020002
        static let backFill: UInt32 = 0x484848, backRing: UInt32 = 0x686868, stopwatch: UInt32 = 0x5A42DB, award: UInt32 = 0xE4232D, wheel: UInt32 = 0xF8C91C
        static let up: UInt32 = 0x83D165, down: UInt32 = 0xE4232D, comet: UInt32 = 0xFFF6D8, cometGlow: UInt32 = 0xF8C91C, shade: UInt32 = 0x000000, plate: UInt32 = 0x141216, plateEdge: UInt32 = 0x2E2A30, lightOff: UInt32 = 0x2A2629
    }
    static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: opacity
        )
    }
    enum backdrop {
        static let centre: [(Double, Double)] = [(0,17),(16.7,23),(33.3,29),(50,35),(66.7,40),(83.3,45),(100,47),(116.7,46),(133.3,43),(150,40),(166.7,33),(183.3,28),(200,22),(216.7,18),(233.3,13),(250,9),(266.7,6),(283.3,4),(300,3),(316.7,1),(333.3,0)]
        static let edge: [(Double, Double)] = [(0,23),(33.3,22),(50,18),(66.7,17),(83.3,12),(100,10),(150,9),(166.7,11),(200,11),(233.3,9),(250,8),(266.7,5),(300,4),(316.7,2),(333.3,1),(350,0)]
        static let edgeProfile = [1.0,0.64,0.36,0.16,0.04,0,0.04,0.16,0.36,0.64,1]
    }
    enum space { static let screenInset=21.67, rowInset=18.33, headerRuleInset=20.33, pointsInset=19.33, textColumn=53.67, nameColumn=66.33, statColumn=227.67, markToCode=5.67, rowPitch=83.15, statPitch=48.67, labelGap=17.33, teamGap=18.92, badgeToDigit=5.0, hit=44.0 }
    enum layout {
        enum back { static let cx=29.17, cy=82.5, d=42.33, ring=0.67 }
        enum title { static let x=123.92, baseline=93.97, size=20.17 }
        enum sort { static let x=272.0, y=80.0, w=8.33, h=14.33, stroke=1.55 }
        enum round { static let x=20.34, baseline=147.18, size=14.17 }
        enum event { static let x=20.07, baseline=177.03, size=22.83 }
        enum podium { static let markRight=[50.33,121.33,194.67], codeX=[54.89,126.22,199.22], markY=203.0, markW=30.0, markH=8.33, baseline=211.07, size=11.83 }
        enum track { static let x=295.0, y=145.67, w=90.0, h=96.67 }
        enum stats { static let valueSize=12.17, labelSize=10.17; enum lap { static let x=53.39, labelX=53.26, baseline=257.07 }; enum day { static let x=53.13, labelX=53.26, baseline=305.36 }; enum pit { static let x=226.53, labelX=226.26, baseline=305.36 } }
        enum headerRule { static let y=356.5 }
        enum rows { static let firstRule=447.83, numberX=18.56, numberBaseline=405.12, numberSize=19.83, nameX=65.62, nameBaseline=403.48, nameSize=18.17, teamX=65.19, teamBaseline=422.4, teamSize=12.17, pointsRight=383.09, pointsBaseline=403.64, pointsSize=18.17, ptsRight=383.34, ptsBaseline=420.31, ptsSize=9.83; enum badge { static let x=21.0,y=418.0,w=6.67,h=7.33 }; static let digitX=32.59,digitBaseline=424.88,digitSize=9.83; enum plate { static let x=52.0,w=262.0,inset=6.0,radius=16.0 }; static let trailX=58.0,trailW=2.33,trailMax=44.0 }
    }
    enum icons { enum stopwatch { static let cx=32.67,cy=258.67,r=9.55,stroke=1.5 }; enum award { static let cx=32.67,cy=302.5,r=6.25,stroke=1.5 }; enum wheel { static let cx=205.83,cy=305.67,r=10.3,stroke=1.7 } }
    enum copy {
        static let results = " Results", round = "Round 09", event = "Harbor GP"
        static let lapLabel = "Fastest Lap", dayValue = "Mira Chen", dayLabel = "Driver of the Day"
        static let pitValue = "Tidewave (2.140s)", pitLabel = "Fastest Pitstop", pts = "PTS"
    }
    enum livery { static let slant=3.2, width=30.0, height=8.33, streakStop=0.75, streakOpacity=0.9 }
}
