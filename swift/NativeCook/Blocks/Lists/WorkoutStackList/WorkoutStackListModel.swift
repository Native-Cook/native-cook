import CoreText
import SwiftUI
import UIKit

enum WorkoutStackListModel {
    static let screenWidth: CGFloat = 402
    static let loopMilliseconds = 11_750.0
    static let crossfitIndex = 7
    static let deckShift: CGFloat = -1.4
    static let letters = ["M", "T", "W", "T", "F", "S", "S"]
    static let sessionTimes = ["4:30 am", "5:00 am", "5:30 am", "6:00 am"]
    static let weeks = [
        [6, 7, 8, 9, 10, 11, 12],
        [13, 14, 15, 16, 17, 18, 19],
        [20, 21, 22, 23, 24, 25, 26],
        [27, 28, 29, 30, 31, 0, 0],
    ]
    static let dimDays: Set<Int> = [6, 11, 13, 18, 20, 22, 24, 25, 27]
    static let dots = [
        7: "r", 10: "rg", 12: "rg", 14: "r", 15: "rg", 16: "r", 17: "rg",
        19: "r", 21: "rg", 23: "r", 26: "r", 28: "rg", 29: "rg", 30: "r", 31: "r",
    ]

    enum Palette {
        static let background = color(0x0C1217)
        static let white = Color.white
        static let dim = color(0x5F666B)
        static let label = color(0x8E959B)
        static let red = color(0xFE223E)
        static let dotRed = color(0xF2273B)
        static let dotGreen = color(0x3DDC84)
        static let grabber = color(0x4A5257)
        static let tag = color(0xE8232F)
        static let tabInk = color(0x3B3122)
        static let tabPlusInk = color(0x9A9A9A)
        static let tabWash = color(0xFFF8E2, alpha: 0.38)
        static let tabSolid = color(0xFAECC8, alpha: 0.94)

        static func color(_ value: UInt32, alpha: Double = 1) -> Color {
            Color(
                red: Double((value >> 16) & 0xff) / 255,
                green: Double((value >> 8) & 0xff) / 255,
                blue: Double(value & 0xff) / 255,
                opacity: alpha
            )
        }
    }

    enum Header {
        static let top: CGFloat = 66
        static let side: CGFloat = 22
        static let icon: CGFloat = 24
        static let avatar: CGFloat = 26
    }

    enum Deck {
        static let width: CGFloat = 391
        static let height: CGFloat = 330
        static let radius: CGFloat = 26
        static let titleSize: CGFloat = 48
        static let titleAscent: CGFloat = 0.2
        static let positions = [-1.0, 0, 1, 2, 3, 4, 5]
        static let tops = [930.0, 610.7, 445, 288.7, 170.7, 92.7, 40]
        static let scales = [1.05, 1, 0.919, 0.865, 0.781, 0.647, 0.5]
        static let titleTops = [84.3, 84.3, 97.3, 89.6, 66.3, 37.6, 20]
        static let titleScales = [1.0, 1, 1, 0.946, 0.897, 0.866, 0.85]
        static let opacities = [0.0, 1, 1, 1, 1, 1, 0]
        static let fog = [0.0, 0, 0.15, 0.3, 0.38, 0.45, 0.5]
        static let photoZoom: CGFloat = 1.6
        static let photoTop: CGFloat = -45
    }

    enum TabBar {
        static let top: CGFloat = 759
        static let left: CGFloat = 18
        static let width: CGFloat = 366
        static let height: CGFloat = 91
        static let radius: CGFloat = 20
        static let centers: [CGFloat] = [67.5, 129, 200, 270, 332]
        static let iconY: CGFloat = 794
        static let dotY: CGFloat = 813.5
        static let icon: CGFloat = 23
        static let plus: CGFloat = 45
        static let plusIcon: CGFloat = 18
        static let blur: CGFloat = 30
    }

    enum Detail {
        static let heroHeight: CGFloat = 412
        static let heroScrollAway: CGFloat = 430
        static let titleInkTop: CGFloat = 368.3
        static let titleLeft: CGFloat = 19
        static let titleSize: CGFloat = 42
        static let titleTracking: CGFloat = 1.2
        static let lettersTop: CGFloat = 121
        static let numbersTop: CGFloat = 158
        static let collapsedShift: CGFloat = 330
        static let weekSide: CGFloat = 14
        static let selected: CGFloat = 40
        static let selectedRadius: CGFloat = 11
        static let letterSize: CGFloat = 14
        static let numberSize: CGFloat = 15
        static let monthRow: CGFloat = 57
        static let grabberTop: CGFloat = 190
        static let grabberWidth: CGFloat = 30
        static let liveRest: CGFloat = 545
        static let liveTop: CGFloat = 215
        static let liveScale: CGFloat = 0.867
        static let quizTop: CGFloat = 245
        static let labelTop: CGFloat = 452
        static let mondayTop: CGFloat = 485
        static let morningTop: CGFloat = 785
        static let listSide: CGFloat = 23
        static let cardRadius: CGFloat = 16
        static let stackTops: [CGFloat] = [407, 482, 562, 683]
        static let stackScales: [CGFloat] = [0.8, 0.86, 0.92, 0.97]
        static let heights: [CGFloat] = [180, 178, 275, 275]
        static let listTops: [CGFloat] = [215, 245, 485, 785]
        static let listScroll: CGFloat = 501
        static let cardWidth: CGFloat = 356
    }

    enum Motion {
        static let scroll = 1_000.0...1_500
        static let open = 3_200.0...3_750
        static let detailScroll = 4_800.0...5_300
        static let month = 6_000.0...6_600
        static let dayTap = 8_400.0
        static let day = 8_450.0...8_950
        static let back = 10_400.0...11_000
        static let scrollSnapMilliseconds = 400.0
        static let scrollDampingRatio = 0.8
        static let openMilliseconds = 550.0
        static let monthMilliseconds = 600.0
        static let dayMilliseconds = 500.0
        static let backMilliseconds = 600.0
        static let deckDrag: CGFloat = 150
        static let resumeAfterMilliseconds = 3_500.0
        static let reducedFadeMilliseconds = 200.0
        static let pressScale = 0.97
        static let pressInMilliseconds = 120.0
        static let pressOutMilliseconds = 150.0
        static let backZoom: CGFloat = 1.12
    }

    enum Kind {
        case live
        case quiz
        case session
    }

    struct Category: Identifiable {
        let id: Int
        let name: String
        let color: Color
        let image: String
    }

    struct Coach {
        let name: String
        let image: String
    }

    struct ClassCard: Identifiable {
        let id: String
        let title: String
        let kind: Kind
        let colors: (UInt32, UInt32)
        let image: String?
        let coach: Coach?
        let time: String?
        let length: String?
    }

    struct Pose {
        let top: CGFloat
        let scale: CGFloat
        let title: CGFloat
        let titleScale: CGFloat
        let opacity: Double
        let fog: Double
    }

    struct Scene: VectorArithmetic {
        var deck: Double
        var open: Double
        var leave: Double
        var scroll: Double
        var month: Double
        var day: Double
        var category: Double

        static let rest = Scene(deck: 0, open: 0, leave: 0, scroll: 0, month: 0, day: 0, category: Double(crossfitIndex))
        static let zero = Scene(deck: 0, open: 0, leave: 0, scroll: 0, month: 0, day: 0, category: 0)

        static func + (lhs: Scene, rhs: Scene) -> Scene {
            Scene(
                deck: lhs.deck + rhs.deck,
                open: lhs.open + rhs.open,
                leave: lhs.leave + rhs.leave,
                scroll: lhs.scroll + rhs.scroll,
                month: lhs.month + rhs.month,
                day: lhs.day + rhs.day,
                category: lhs.category + rhs.category
            )
        }

        static func - (lhs: Scene, rhs: Scene) -> Scene {
            Scene(
                deck: lhs.deck - rhs.deck,
                open: lhs.open - rhs.open,
                leave: lhs.leave - rhs.leave,
                scroll: lhs.scroll - rhs.scroll,
                month: lhs.month - rhs.month,
                day: lhs.day - rhs.day,
                category: lhs.category - rhs.category
            )
        }

        mutating func scale(by rhs: Double) {
            deck *= rhs
            open *= rhs
            leave *= rhs
            scroll *= rhs
            month *= rhs
            day *= rhs
            category *= rhs
        }

        var magnitudeSquared: Double {
            deck * deck + open * open + leave * leave + scroll * scroll + month * month + day * day + category * category
        }
    }

    static let categories = [
        Category(id: 0, name: "Yoga", color: Palette.color(0xF2B21C), image: "WorkoutStackList-cat-yoga.jpg"),
        Category(id: 1, name: "Aerobics", color: Palette.color(0x9E1530), image: "WorkoutStackList-cat-aerobics.jpg"),
        Category(id: 2, name: "Cardio", color: Palette.color(0x4FA9D8), image: "WorkoutStackList-cat-cardio.jpg"),
        Category(id: 3, name: "Acrobatics", color: Palette.color(0x1E8C8C), image: "WorkoutStackList-cat-acrobatics.jpg"),
        Category(id: 4, name: "Running", color: Palette.color(0xB5182B), image: "WorkoutStackList-cat-running.jpg"),
        Category(id: 5, name: "Core", color: Palette.color(0xD9A68A), image: "WorkoutStackList-cat-core.jpg"),
        Category(id: 6, name: "HIIT", color: Palette.color(0xF0A81C), image: "WorkoutStackList-cat-hiit.jpg"),
        Category(id: 7, name: "Crossfit", color: Palette.color(0x8E1222), image: "WorkoutStackList-cat-crossfit.jpg"),
        Category(id: 8, name: "Balling", color: Palette.color(0xE2A419), image: "WorkoutStackList-cat-balling.jpg"),
        Category(id: 9, name: "Strength", color: Palette.color(0x144C52), image: "WorkoutStackList-cat-strength.jpg"),
    ]

    static let dayEight = [
        ClassCard(id: "live8", title: "Monday\nMotivation", kind: .live, colors: (0xF2378F, 0x7A5CF0), image: nil, coach: Coach(name: "Tomás Herrera", image: "WorkoutStackList-coach-1.jpg"), time: nil, length: nil),
        ClassCard(id: "quiz8", title: "What is an\nAntioxidant", kind: .quiz, colors: (0xF7B31C, 0xF39A14), image: "WorkoutStackList-class-antioxidant.png", coach: Coach(name: "Amara Okafor", image: "WorkoutStackList-coach-2.jpg"), time: "8:30 am", length: "20 min"),
        ClassCard(id: "monday8", title: "Monday\nMotivation", kind: .session, colors: (0xEC2B33, 0xE02029), image: "WorkoutStackList-class-monday.png", coach: nil, time: nil, length: "35 min."),
        ClassCard(id: "morning8", title: "Morning\nFlow", kind: .session, colors: (0x2C9A9B, 0x258486), image: "WorkoutStackList-class-morning.png", coach: nil, time: nil, length: "30 min."),
    ]

    static let dayNine = [
        ClassCard(id: "live9", title: "Break a\nSweat", kind: .live, colors: (0x2CB6B0, 0x1B8FA3), image: nil, coach: Coach(name: "Amara Okafor", image: "WorkoutStackList-coach-2.jpg"), time: nil, length: nil),
        ClassCard(id: "quiz9", title: "Sit and\nStretch", kind: .quiz, colors: (0xD9D4C8, 0xBFB8A8), image: nil, coach: Coach(name: "Tomás Herrera", image: "WorkoutStackList-coach-1.jpg"), time: "7:00 am", length: "15 min"),
        ClassCard(id: "run9", title: "Keep on\nRunning", kind: .session, colors: (0x3E7A4A, 0x2F6439), image: "WorkoutStackList-class-running.png", coach: nil, time: nil, length: "40 min."),
        ClassCard(id: "hiit9", title: "High\nIntensity", kind: .session, colors: (0x8E1222, 0x6E0D1A), image: "WorkoutStackList-class-hiit.png", coach: nil, time: nil, length: "25 min."),
    ]

    static func pose(_ distance: Double) -> Pose {
        Pose(
            top: CGFloat(interpolate(distance, input: Deck.positions, output: Deck.tops)),
            scale: CGFloat(interpolate(distance, input: Deck.positions, output: Deck.scales)),
            title: CGFloat(interpolate(distance, input: Deck.positions, output: Deck.titleTops)),
            titleScale: CGFloat(interpolate(distance, input: Deck.positions, output: Deck.titleScales)),
            opacity: interpolate(distance, input: Deck.positions, output: Deck.opacities),
            fog: interpolate(distance, input: Deck.positions, output: Deck.fog)
        )
    }

    static func sample(milliseconds input: Double) -> Scene {
        let time = ((input.truncatingRemainder(dividingBy: loopMilliseconds)) + loopMilliseconds)
            .truncatingRemainder(dividingBy: loopMilliseconds)
        let leave = pin(segment(time, Motion.back))
        let finished = leave >= 1
        return Scene(
            deck: time < Motion.open.upperBound ? 5 * pin(segment(time, Motion.scroll)) : 0,
            open: finished ? 0 : pin(segment(time, Motion.open)),
            leave: finished ? 0 : leave,
            scroll: finished ? 0 : pin(segment(time, Motion.detailScroll)),
            month: finished ? 0 : pin(segment(time, Motion.month)),
            day: finished ? 0 : pin(segment(time, Motion.day)),
            category: Double(crossfitIndex)
        )
    }

    static func segment(_ value: Double, _ range: ClosedRange<Double>) -> Double {
        guard range.upperBound > range.lowerBound else { return value >= range.upperBound ? 1 : 0 }
        return min(max((value - range.lowerBound) / (range.upperBound - range.lowerBound), 0), 1)
    }

    static func pin(_ value: Double) -> Double {
        cubicBezier(value, x1: 0.4, y1: 0, x2: 0.2, y2: 1)
    }

    static func out(_ value: Double) -> Double {
        cubicBezier(value, x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    }

    static func mix(_ from: Double, _ to: Double, _ amount: Double) -> Double {
        from + (to - from) * amount
    }

    static func mix(_ from: CGFloat, _ to: CGFloat, _ amount: Double) -> CGFloat {
        from + (to - from) * CGFloat(amount)
    }

    static func interpolate(_ value: Double, input: [Double], output: [Double]) -> Double {
        guard let firstInput = input.first, let firstOutput = output.first else { return 0 }
        if value <= firstInput { return firstOutput }
        for index in 0..<input.count - 1 where value <= input[index + 1] {
            let amount = (value - input[index]) / (input[index + 1] - input[index])
            return mix(output[index], output[index + 1], amount)
        }
        return output.last ?? firstOutput
    }

    private static func cubicBezier(_ progress: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        let value = min(max(progress, 0), 1)
        var low = 0.0
        var high = 1.0
        for _ in 0..<18 {
            let middle = (low + high) / 2
            let x = 3 * pow(1 - middle, 2) * middle * x1 + 3 * (1 - middle) * pow(middle, 2) * x2 + pow(middle, 3)
            if x < value { low = middle } else { high = middle }
        }
        let time = (low + high) / 2
        return 3 * pow(1 - time, 2) * time * y1 + 3 * (1 - time) * pow(time, 2) * y2 + pow(time, 3)
    }
}

enum WorkoutStackListAssets {
    static let futuraBold = "Futura-Bold"
    static let futuraMedium = "Futura-Medium"
    static let poppinsRegular = "Poppins-Regular"
    static let poppinsMedium = "Poppins-Medium"
    static let poppinsSemibold = "Poppins-SemiBold"

    private static let fontFiles = [
        "WorkoutStackList-Poppins-Regular",
        "WorkoutStackList-Poppins-Medium",
        "WorkoutStackList-Poppins-SemiBold",
    ]

    private static let imageFiles = WorkoutStackListModel.categories.map(\.image) + [
        "WorkoutStackList-class-antioxidant.png",
        "WorkoutStackList-class-hiit.png",
        "WorkoutStackList-class-monday.png",
        "WorkoutStackList-class-morning.png",
        "WorkoutStackList-class-running.png",
        "WorkoutStackList-coach-1.jpg",
        "WorkoutStackList-coach-2.jpg",
        "WorkoutStackList-hero-crossfit.jpg",
    ]

    static let registered: Void = {
        for file in fontFiles {
            guard let url = Bundle.main.url(forResource: file, withExtension: "ttf") else {
                assertionFailure("Missing Workout Stack List font: \(file).ttf")
                continue
            }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
#if DEBUG
        for font in [poppinsRegular, poppinsMedium, poppinsSemibold] {
            assert(UIFont(name: font, size: 12) != nil, "Missing registered Workout Stack List font: \(font)")
        }
        for image in imageFiles {
            assert(UIImage(named: image) != nil, "Missing Workout Stack List image: \(image)")
        }
#endif
    }()

    static func image(_ name: String) -> UIImage {
        _ = registered
        guard let image = UIImage(named: name) else {
            assertionFailure("Missing Workout Stack List image: \(name)")
            return UIImage()
        }
        return image
    }

    static func font(_ name: String, size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    static func uiFont(_ name: String, size: CGFloat) -> UIFont {
        _ = registered
        guard let font = UIFont(name: name, size: size) else {
            assertionFailure("Missing Workout Stack List font: \(name)")
            return .systemFont(ofSize: size)
        }
        return font
    }
}
