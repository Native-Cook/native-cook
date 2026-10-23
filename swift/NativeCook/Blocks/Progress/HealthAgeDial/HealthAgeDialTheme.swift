import SwiftUI
import UIKit

enum HealthAgeDialTheme {
    static let pinWidth = 402.0
    static let pinSafeTop = 62.0
    static let ages = (health: 26.1, actual: 30.0)
    static let restYears = -3.9

    enum Colors {
        static let page = Color(hex: 0xF1EFF8)
        static let ink = Color(hex: 0x0E0E10)
        static let secondary = Color(hex: 0x8B8A92)
        static let label = Color(hex: 0x8E8D94)
        static let years = Color(hex: 0x8D91A2)
        static let blue = Color(hex: 0x0091FF)
        static let chipFill = Color(red: 250 / 255, green: 249 / 255, blue: 253 / 255, opacity: 0.72)
        static let chipBorder = Color.black.opacity(0.12)
        static let chipSelected = Color(hex: 0xFDFDFE)
        static let info = Color(hex: 0xD0CFD5)
        static let infoGlyph = Color(hex: 0x1F1E23)
        static let tick = Color(hex: 0xA0A8D6)
        static let halo = Color(hex: 0xB9DBFA)
        static let card = Color(hex: 0xFDFDFE)
        static let sleep = Color(hex: 0x7366D8)
        static let sleepTint = Color(hex: 0xF3F1FD)
        static let sleepGlyph = Color(hex: 0x6452E4)
        static let hrv = Color(hex: 0xD2376D)
        static let hrvTint = Color(hex: 0xFDEEF3)
        static let hrvGlyph = Color(hex: 0xE2477A)
        static let cardBody = Color(hex: 0x2E2E33)
        static let grid = Color(hex: 0xDCDBE3)
        static let baseline = Color(hex: 0xBDBCC2)
        static let axis = Color(hex: 0xC6C5CC)
        static let guide = Color(hex: 0xCDCCD3)
        static let line = Color(hex: 0xA5B3DD)
        static let lineEnd = Color(hex: 0x9EC3EC)
        static let fillTop = Color(red: 206 / 255, green: 214 / 255, blue: 245 / 255, opacity: 0)
        static let fillBottom = Color(hex: 0xB6D5F7)
        static let chartDot = Color(hex: 0x0A9ADB)
        static let tabText = Color(hex: 0x141416)
        static let tabSelected = Color(red: 200 / 255, green: 199 / 255, blue: 206 / 255, opacity: 0.7)
        static let tabBlue = Color(hex: 0x1E86EE)
        static let white = Color.white
    }

    static let ringStops: [(angle: Double, color: Color)] = [
        (-164, Color(hex: 0xC3E3E7)), (-152, Color(hex: 0xA9DBE2)),
        (-138, Color(hex: 0x96D7E3)), (-122, Color(hex: 0x83D1EA)),
        (-104, Color(hex: 0x7AC7F2)), (-88, Color(hex: 0x7BC0F5)),
        (-70, Color(hex: 0x83BBF7)), (-50, Color(hex: 0x8DB5F6)),
        (-30, Color(hex: 0x9CB7F2)), (-10, Color(hex: 0xACB9F3)),
        (8, Color(hex: 0xB8C0EF)), (28, Color(hex: 0xC9C7EC)),
        (50, Color(hex: 0xD5D1E7)), (72, Color(hex: 0xDBD9E8)),
        (96, Color(hex: 0xDDDDE3)), (130, Color(hex: 0xDEDDE3)),
        (164, Color(hex: 0xE1DFE5)),
    ]

    enum Space {
        static let textInset = 31.0
        static let cardInset = 16.0
        static let chipInset = 18.67
        static let chipGap = 6.0
        static let chipInsetTrailing = 20.0
        static let chipPadLeading = 13.67
        static let chipIconGap = 9.3
        static let statColumn = 211.3
        static let cardGap = 10.33
        static let cardIconLeading = 16.67
        static let cardTextLeading = 77.0
        static let tabBottom = 23.0
    }

    enum Radius {
        static let chip = 20.0
        static let card = 24.0
        static let tab = 31.5
        static let tabPill = 28.0
    }

    struct TextStyle: Equatable {
        let size: Double
        let weight: UIFont.Weight
        let color: Color
        var line: Double? = nil
        var tracking = 0.0
    }

    enum TypeStyle {
        static let chipTitle = TextStyle(size: 15, weight: .regular, color: Colors.secondary)
        static let chipValue = TextStyle(size: 14.3, weight: .semibold, color: Colors.ink)
        static let number = TextStyle(size: 80, weight: .bold, color: Colors.blue, tracking: -3)
        static let unit = TextStyle(size: 15.2, weight: .semibold, color: Colors.years)
        static let title = TextStyle(size: 32.7, weight: .bold, color: Colors.ink, line: 41)
        static let body = TextStyle(size: 21.85, weight: .regular, color: Colors.secondary, line: 28)
        static let statLabel = TextStyle(size: 15.3, weight: .medium, color: Colors.label)
        static let statValue = TextStyle(size: 19.5, weight: .bold, color: Colors.ink, tracking: -0.4)
        static let heading = TextStyle(size: 21, weight: .semibold, color: Colors.ink)
        static let axis = TextStyle(size: 14, weight: .regular, color: Colors.axis)
        static let guide = TextStyle(size: 13, weight: .regular, color: Colors.guide)
        static let cardTitle = TextStyle(size: 15, weight: .semibold, color: Colors.ink)
        static let cardValue = TextStyle(size: 28, weight: .medium, color: Colors.ink)
        static let cardBody = TextStyle(size: 15, weight: .regular, color: Colors.cardBody, line: 20)
        static let tab = TextStyle(size: 9.8, weight: .semibold, color: Colors.tabText)
    }

    enum Baseline {
        static let chipTitle = 87.3
        static let chipValue = 106.7
        static let number = 325.8
        static let unit = 355.3
        static let title = 512.0
        static let body = 586.8
        static let statLabel = 718.9
        static let statValue = 744.5
    }

    enum Layout {
        enum Chip {
            static let top = 61.33
            static let height = 61.33
            static let icon = 32.7
            static let ring = 31.0
            static let leftShare = 177.33 / 357.33
        }
        enum Dial {
            static let cx = 200.0
            static let cy = 305.33
            static let radius = 133.33
            static let stroke = 40.33
            static let start = -156.0
            static let end = 156.0
            static let tickLength = 20.0
            static let tickWidth = 4.0
            static let dot = 26.0
            static let degreesPerYear = 14.36
            static let maxYears = 9.9
            static let canvasPad = 70.0
            static let edgeSoftness = 1.8
        }
        static let info = (cx: 357.67, cy: 173.0, diameter: 25.0)
        static let numberCenterX = 188.1
        static let unitCenterX = 200.5
        static let halo = (cx: 160.0, cy: 300.0, radius: 228.0)
        static let historyHeading = 802.3
        enum Chart {
            static let top = 826.0
            static let bottom = 946.67
            static let left = 31.0
            static let right = 316.0
            static let columns = 6
            static let base = 860.67
            static let baseRight = 371.0
            static let labelBaseline = 966.3
            static let labelOffset = 6.3
            static let olderBaseline = 846.7
            static let youngerBaseline = 906.0
            static let guideX = 330.0
            static let dotRadius = 5.0
            static let dotRing = 1.6
            static let lineWidth = 3.0
        }
        static let raisingHeading = 1024.3
        static let cards = [(top: 1047.0, height: 161.67), (top: 1219.0, height: 161.67)]
        static let cardIcon = (cy: 43.0, diameter: 46.67, glyph: 24.0)
        static let cardTitleBaseline = 31.6
        static let cardValueBaseline = 64.6
        static let cardBodyBaseline = 100.6
        static let contentBottom = 1380.67
        enum Tab {
            static let width = 292.0
            static let height = 63.0
            static let item = 91.67
            static let pillWidth = 99.0
            static let pillHeight = 55.0
            static let heart = 28.0
            static let petal = 29.0
            static let grid = 26.7
            static let labelGap = 4.0
        }
        static let historyOffset = 550.0
    }

    static let historyPoints: [CGPoint] = [
        .init(x: 0, y: 15.1), .init(x: 5.3, y: 14.6), .init(x: 10.7, y: 14.2), .init(x: 16, y: 14.4),
        .init(x: 21.3, y: 15.1), .init(x: 26.7, y: 15.8), .init(x: 32, y: 20.6), .init(x: 37.3, y: 24.9),
        .init(x: 42.7, y: 34.5), .init(x: 48, y: 37.8), .init(x: 53.3, y: 37.4), .init(x: 58.7, y: 36.3),
        .init(x: 64, y: 35), .init(x: 69.3, y: 35), .init(x: 74.7, y: 34), .init(x: 80, y: 28.2),
        .init(x: 85.3, y: 23.7), .init(x: 90.7, y: 22.3), .init(x: 96, y: 23.2), .init(x: 101.3, y: 24.7),
        .init(x: 106.7, y: 25.4), .init(x: 112, y: 31.1), .init(x: 117.3, y: 33.7), .init(x: 122.7, y: 30.3),
        .init(x: 128, y: 38.1), .init(x: 133.3, y: 40.9), .init(x: 138.7, y: 43.8), .init(x: 144, y: 45.7),
        .init(x: 149.3, y: 51.9), .init(x: 154.7, y: 55.8), .init(x: 160, y: 54.1), .init(x: 165.3, y: 53.3),
        .init(x: 170.7, y: 52.2), .init(x: 176, y: 51), .init(x: 181.3, y: 51.8), .init(x: 186.7, y: 53.9),
        .init(x: 192, y: 56.4), .init(x: 197.3, y: 58.4), .init(x: 202.7, y: 56.7), .init(x: 208, y: 55.5),
        .init(x: 213.3, y: 57.5), .init(x: 218.7, y: 59.4), .init(x: 224, y: 59.1), .init(x: 233, y: 58.3),
    ]

    static let months = ["Nov", "Dec", "Jan", "Feb", "Mar", "Apr"]

    enum Copy {
        static let overviewTitle = "Overview"
        static let overviewValue = "5 Excellent"
        static let chipTitle = "Health Age"
        static let chipValue = "-3.9 years"
        static let unit = "YEARS"
        static let title = "Build on Your Healthy Habits"
        static let body = "Your current habits are serving you well. Keep leaning into what’s working to help your body stay younger for years to come."
        static let healthAgeLabel = "Health Age"
        static let healthAgeValue = "26.1 years"
        static let actualAgeLabel = "Actual Age"
        static let actualAgeValue = "30 years"
        static let history = "Health Age History"
        static let older = "Older"
        static let younger = "Younger"
        static let raising = "Raising Your Health Age"
        static let factorTitles = ["Sleep Score", "Heart Rate Variability"]
        static let factorYears = [1.3, 0.3]
        static let factorBodies = [
            "Your average sleep score of 70 suggests you could benefit from more restorative rest to help support healthy aging.",
            "Your overnight HRV of 38.5 signals your body is carrying some stress. Focus on steady recovery to help it bounce back.",
        ]
        static let tabs = ["Insights", "Longevity", "Browse"]
    }
}

private extension Color {
    init(hex: UInt32) {
        self.init(red: Double((hex >> 16) & 255) / 255,
                  green: Double((hex >> 8) & 255) / 255,
                  blue: Double(hex & 255) / 255)
    }
}
