import CoreGraphics
import SwiftUI

enum SendMoneyCardsSpec {
    struct Box {
        var x: Double
        var y: Double
        var width: Double
        var height: Double
        var radius: Double
    }

    struct Pose {
        var lift = 0.0
        var arm = 0.0
        var sheet = 0.0
        var fade = 0.0
        var hintLift = 0.0
        var closing = false
    }

    struct Payment: Identifiable {
        let id: Int
        let merchant: String
        let date: String
        let amount: String
        let incoming: Bool
    }

    struct Person: Identifiable {
        let id: String
        let name: String
        let image: String
    }

    static let network = "NUVA"
    static let balance = "$ 15,000.75"
    static let last4 = "1234"
    static let valid = "12/35"
    static let holder = "RAYAN ALMASRI"

    static let favourites = [
        Person(id: "farid", name: "Farid", image: "SendMoneyCards-farid.jpg"),
        Person(id: "shadi", name: "Shadi", image: "SendMoneyCards-shadi.jpg"),
        Person(id: "cyrus", name: "Cyrus", image: "SendMoneyCards-cyrus.jpg"),
    ]

    static let contacts = [
        Person(id: "annette", name: "Noor Rahman", image: "SendMoneyCards-emoji-annette.png"),
        Person(id: "cameron", name: "Isabella Montgomery", image: "SendMoneyCards-emoji-cameron.png"),
        Person(id: "jane", name: "Imani Brooks", image: "SendMoneyCards-emoji-jane.png"),
        Person(id: "wade", name: "Yara Haddad", image: "SendMoneyCards-emoji-wade.png"),
        Person(id: "devon", name: "Ravi Menon", image: "SendMoneyCards-emoji-devon.png"),
    ]

    static let payments = [
        Payment(id: 0, merchant: "Orchard", date: "May 24 , 2024", amount: "- $103.56", incoming: false),
        Payment(id: 1, merchant: "Maple", date: "May 24 , 2024", amount: "+ $1,000.97", incoming: true),
        Payment(id: 2, merchant: "Orchard", date: "May 24 , 2024", amount: "- $103.56", incoming: false),
        Payment(id: 3, merchant: "Orchard", date: "May 24 , 2024", amount: "+ $103.56", incoming: true),
    ]

    enum Colour {
        static let screen = color(0xD5DAE6)
        static let ink = color(0x121214)
        static let inkSoft = color(0x3C3C43)
        static let muted = color(0x6E6E76)
        static let chip = color(0xC3C6D0)
        static let row = color(0xC2C5D2)
        static let rowIcon = color(0xB4B5C1)
        static let blue = color(0x0016E9)
        static let blueLift = color(0x003BEF)
        static let blueText = color(0x0A2CF0)
        static let cardChip = color(0xB6CFE5)
        static let white = Color.white
        static let black = Color.black
        static let pillMuted = color(0xB6B6BC)
        static let sheetMuted = color(0x8E8E93)
        static let addNew = color(0xE9E9EE)

        private static func color(_ value: UInt32) -> Color {
            Color(.sRGB,
                  red: Double((value >> 16) & 255) / 255,
                  green: Double((value >> 8) & 255) / 255,
                  blue: Double(value & 255) / 255,
                  opacity: 1)
        }
    }

    enum FontName {
        static let regular = "Poppins-Regular"
        static let medium = "Poppins-Medium"
        static let semibold = "Poppins-SemiBold"
    }

    enum FontSize {
        static let pill = 15.5
        static let hint = 15.3
        static let brand = 21.0
        static let balance = 34.8
        static let number = 16.0
        static let valid = 15.0
        static let tiny = 8.0
        static let action = 11.7
        static let sectionTitle = 24.0
        static let sectionLead = 25.0
        static let rowName = 14.0
        static let rowMeta = 12.0
        static let rowAmount = 11.0
        static let sheetTitle = 41.2
        static let sheetTitleLine = 46.7
        static let contactsTitle = 22.0
        static let contactsLine = 27.0
        static let contact = 14.0
        static let label = 12.0
    }

    enum Pill {
        static let width = 305.0
        static let height = 45.0
        static let top = 8.0
        static let radius = 22.5
        static let padX = 19.3
        static let padRight = 16.0
        static let avatar = 28.0
        static let avatarStep = 18.0
    }

    enum Island {
        static let width = 126.0
        static let height = 37.0
        static let top = 11.0
    }

    enum Tab {
        static let width = 184.0
        static let depth = 35.0
        static let overlap = 22.0
        static let radius = 36.0
        static let arrowY = 61.0
        static let bottom = 87.0
        static let arrowFollow = 0.33
    }

    enum Header {
        static let buttonY = 97.7
        static let button = 48.0
        static let inset = 48.0
        static let gridGlyph = 21.0
        static let plusGlyph = 20.0
    }

    enum Card {
        static let inset = 50.0
        static let top = 163.0
        static let height = 275.0
        static let radius = 24.0
        static let padX = 20.0
        static let padRight = 17.5
        static let validRight = 18.0
        static let brandY = 45.0
        static let chipY = 89.0
        static let chipWidth = 26.0
        static let chipHeight = 18.0
        static let chipRadius = 3.0
        static let numberY = 120.0
        static let last4X = 112.3
        static let dotsX = 19.6
        static let dotsCenterY = 120.9
        static let dotSize = 2.7
        static let dotPitch = 7.0
        static let groupPitch = 31.1
        static let validY = 93.0
        static let nameY = 158.0
        static let actionY = 210.0
        static let action = 46.0
        static let actionGlyph = 22.0
        static let labelGap = 4.4
    }

    static let hintY = 109.0

    enum ListLayout {
        static let titleY = 494.0
        static let top = 573.0
        static let rowHeight = 60.0
        static let pitch = 78.0
        static let inset = 49.0
        static let icon = 48.0
        static let iconPad = 5.0
        static let nameGap = 9.0
        static let padRight = 10.0
        static let glyph = 23.0
        static let arrowButton = 46.0
        static let arrowX = 330.5
        static let arrowY = 521.0
        static let arrowGlyph = 24.0
    }

    enum Sheet {
        static let left = 30.0
        static let right = 26.0
        static let top = 79.0
        static let bottom = 16.0
        static let radius = 44.0
        static let titleX = 60.4
        static let titleY = 119.0
        static let closeX = 70.3
        static let closeY = 133.0
        static let closeGlyph = 18.0
        static let avatarsY = 255.7
        static let avatar = 39.0
        static let avatarX = 79.5
        static let avatarStep = 76.0
        static let addNewX = 307.5
        static let contactsTitleY = 340.0
        static let contactsTop = 446.5
        static let contactPitch = 59.6
        static let contactX = 79.2
        static let nameGap = 15.1
        static let buttonX = 62.0
        static let buttonWidth = 283.0
        static let buttonTop = 753.0
        static let buttonHeight = 52.0
    }

    enum SheetGrow {
        static let originY = 30.0
        static let openFadeFrom = 60.0
        static let openFadeTo = 150.0
        static let openScaleFrom = 0.5
        static let openScaleStart = 40.0
        static let openScaleEnd = 230.0
        static let closeFadeFrom = 150.0
        static let closeFadeTo = 250.0
        static let closeOriginY = -212.0
        static let closeTimes = [0.0, 53.0, 187.0, 290.0]
        static let closeScales = [1.0, 0.83, 0.55, 0.45]
    }

    static let pressLift = 63.0
    static let openDrag = 36.0
    static let flickVelocity = 650.0
    static let pressDuration = 450.0
    static let armDuration = 280.0
    static let fallDuration = 285.0
    static let openDuration = 250.0
    static let openDelay = 0.0
    static let closeDuration = 290.0
    static let returnDelay = 0.0
    static let tabOut = 250.0
    static let tabFade = 100.0
    static let releaseDuration = 360.0
    static let fadeDuration = 200.0
    static let returnTimes = [0.0, 57.0, 187.0, 220.0, 253.0, 287.0, 320.0]
    static let returnLifts = [800.0, 620.0, 264.0, 196.0, 97.0, 37.0, 14.0]
    static let returnDuration = 320.0
    static let settleMass = 1.0
    static let settleStiffness = 120.0
    static let settleDamping = 15.0
    static let settleVelocity = -750.0
    static let hintLag = 25.0
    static let hintFollowStiffness = 900.0
    static let hintFollowDamping = 60.0
    static let loop = 10.0
    static let loopMilliseconds = 10_000.0
    static let resumeAfter = 3_500.0
    static let scriptPress = 1_500.0
    static let scriptOpen = 3_300.0
    static let scriptClose = 7_300.0

    static let openTimes = [0.0, 17.0, 50.0, 83.0, 117.0, 150.0, 183.0, 250.0]
    static let openWidths = [120.0, 190.0, 215.0, 240.0, 262.0, 285.0, 330.0, 346.0]
    static let openTops = [18.0, 18.0, 22.0, 26.0, 30.0, 40.0, 60.0, 79.0]
    static let openBottoms = [50.0, 134.0, 232.0, 333.0, 397.0, 569.0, 639.0, 858.0]
    static let closeTimes = [0.0, 20.0, 53.0, 120.0, 187.0, 220.0, 253.0, 290.0]
    static let closeWidths = [346.0, 330.0, 300.0, 280.0, 258.0, 230.0, 200.0, 160.0]
    static let closeTops = [79.0, 66.0, 58.0, 48.0, 35.0, 25.0, 18.0, 18.0]
    static let closeBottoms = [858.0, 720.0, 660.0, 470.0, 276.0, 161.0, 121.0, 68.0]
    static let pinHeight = 874.0
    static let gooBlur = 14.0
    static let gooAlphaMultiplier = 24.0
    static let gooAlphaBias = -11.5
    static let pressScale = 0.97
    static let pressRetention = 16.0
    static let snapBackMass = 1.0
    static let snapBackStiffness = 220.0
    static let snapBackDamping = 18.0

    static func islandOffset(safeTop: Double) -> Double {
        safeTop > 0 ? safeTop - screenSafeTop : -40
    }

    static func scriptPose(milliseconds time: Double, offscreen: Double, reduced: Bool) -> Pose {
        if reduced {
            let hint = segment(time, scriptPress, fadeDuration) * (1 - segment(time, scriptClose, fadeDuration))
            let fade = segment(time, scriptOpen, fadeDuration) * (1 - segment(time, scriptClose, fadeDuration))
            return Pose(arm: hint, fade: fade)
        }
        var pose = Pose()
        if time >= scriptPress {
            pose.lift = pressLift * easeOutCubic(segment(time, scriptPress, pressDuration))
            pose.arm = easeOutCubic(segment(time, scriptPress, armDuration))
        }
        if time >= scriptOpen {
            pose.lift = pressLift + (offscreen - pressLift) * segment(time, scriptOpen, fallDuration)
            pose.sheet = segment(time, scriptOpen + openDelay, openDuration)
        }
        if time >= scriptClose {
            pose.closing = true
            pose.sheet = 1 - segment(time, scriptClose, closeDuration)
            let returning = time - scriptClose - returnDelay
            if returning < 0 {
                pose.lift = offscreen
            } else if returning < returnDuration {
                pose.lift = table(returning, times: returnTimes, values: returnLifts)
            } else {
                pose.lift = springSettle(returning - returnDuration)
            }
            pose.arm = 1 - easeInOutCubic(segment(time, scriptClose + tabOut, tabFade))
        }
        pose.hintLift = time >= scriptClose ? scriptLift(max(0, time - hintLag), offscreen: offscreen) : pose.lift
        return pose
    }

    private static func scriptLift(_ time: Double, offscreen: Double) -> Double {
        var lift = time >= scriptPress
            ? pressLift * easeOutCubic(segment(time, scriptPress, pressDuration))
            : 0
        if time >= scriptOpen {
            lift = mix(pressLift, offscreen, segment(time, scriptOpen, fallDuration))
        }
        if time >= scriptClose {
            let returning = time - scriptClose - returnDelay
            if returning < 0 {
                lift = offscreen
            } else if returning < returnDuration {
                lift = table(returning, times: returnTimes, values: returnLifts)
            } else {
                lift = springSettle(returning - returnDuration)
            }
        }
        return lift
    }

    static func sheetBox(progress: Double, width: Double, height: Double, offsetY: Double, closing: Bool) -> Box {
        let time = closing ? (1 - progress) * closeDuration : progress * openDuration
        let boxWidth = table(time, times: closing ? closeTimes : openTimes,
                             values: closing ? closeWidths : openWidths)
        let top = table(time, times: closing ? closeTimes : openTimes,
                        values: closing ? closeTops : openTops) + offsetY
        let pinBottom = table(time, times: closing ? closeTimes : openTimes,
                              values: closing ? closeBottoms : openBottoms)
        let bottom = pinBottom + offsetY + (height - offsetY - pinHeight) * (pinBottom / openBottoms.last!)
        let boxHeight = max(1, bottom - top)
        let round = min(boxWidth, boxHeight) / 2
        let square = closing ? 1 - smooth(time / 45) : smooth((time - 183) / 67)
        let radius = mix(round, Sheet.radius, square)
        let fullWidth = width - Sheet.left - Sheet.right
        let x = (width - boxWidth) / 2 + (Sheet.left - (width - fullWidth) / 2) * min(1, boxWidth / fullWidth)
        return Box(x: x, y: top, width: boxWidth, height: boxHeight, radius: radius)
    }

    static func pillBox(progress: Double, width: Double, offsetY: Double) -> Box {
        let amount = smooth((progress - 0.2) / 0.4)
        let boxWidth = mix(Pill.width, Island.width, amount)
        let boxHeight = mix(Pill.height, Island.height, amount)
        return Box(x: (width - boxWidth) / 2,
                   y: mix(Pill.top, Island.top, amount) + offsetY,
                   width: boxWidth, height: boxHeight, radius: boxHeight / 2)
    }

    static func tabBox(arm: Double, progress: Double, pill: Box) -> Box {
        let depth = Tab.depth * arm * (1 - min(1, progress * 2.5))
        let width = min(Tab.width, pill.width - 12)
        let y = pill.y + pill.height - Tab.overlap
        let height = Tab.overlap + depth
        return Box(x: pill.x + (pill.width - width) / 2,
                   y: y, width: width, height: height, radius: min(Tab.radius, height / 2))
    }

    static func sheetContentTransform(progress: Double, closing: Bool, offsetY: Double, height: Double) -> (scale: Double, opacity: Double, translateY: Double) {
        var scale: Double
        let opacity: Double
        var originY = SheetGrow.originY
        if closing {
            let time = (1 - progress) * closeDuration
            scale = table(time, times: SheetGrow.closeTimes, values: SheetGrow.closeScales)
            originY = SheetGrow.closeOriginY
            opacity = 1 - clamp((time - SheetGrow.closeFadeFrom) / (SheetGrow.closeFadeTo - SheetGrow.closeFadeFrom))
        } else {
            let time = progress * openDuration
            let amount = clamp((time - SheetGrow.openScaleStart) / (SheetGrow.openScaleEnd - SheetGrow.openScaleStart))
            scale = SheetGrow.openScaleFrom + (1 - SheetGrow.openScaleFrom) * (1 - (1 - amount) * (1 - amount))
            opacity = clamp((time - SheetGrow.openFadeFrom) / (SheetGrow.openFadeTo - SheetGrow.openFadeFrom))
        }
        let translateY = (offsetY + originY - height / 2) * (1 - scale)
        return (scale, opacity, translateY)
    }

    static func rubber(_ distance: Double, limit: Double = 120) -> Double {
        let sign = distance < 0 ? -1.0 : 1.0
        let value = abs(distance)
        return sign * limit * (1 - 1 / (value / limit + 1))
    }

    static func easeOutCubic(_ value: Double) -> Double {
        let inverse = 1 - value
        return 1 - inverse * inverse * inverse
    }

    static func easeInOutCubic(_ value: Double) -> Double {
        value < 0.5 ? 4 * value * value * value : 1 - pow(-2 * value + 2, 3) / 2
    }

    static func springSettle(_ milliseconds: Double) -> Double {
        let initial = returnLifts.last!
        let angular = sqrt(settleStiffness / settleMass)
        let dampingRatio = settleDamping / (2 * sqrt(settleStiffness * settleMass))
        let damped = angular * sqrt(1 - dampingRatio * dampingRatio)
        let seconds = milliseconds / 1000
        return exp(-dampingRatio * angular * seconds) *
            (initial * cos(damped * seconds) +
             ((settleVelocity + dampingRatio * angular * initial) / damped) * sin(damped * seconds))
    }

    static func springDisplacement(
        _ milliseconds: Double,
        initial: Double,
        velocity: Double,
        mass: Double,
        stiffness: Double,
        damping: Double
    ) -> Double {
        let angular = sqrt(stiffness / mass)
        let dampingRatio = damping / (2 * sqrt(stiffness * mass))
        let damped = angular * sqrt(max(0.0001, 1 - dampingRatio * dampingRatio))
        let seconds = milliseconds / 1000
        return exp(-dampingRatio * angular * seconds) *
            (initial * cos(damped * seconds) +
             ((velocity + dampingRatio * angular * initial) / damped) * sin(damped * seconds))
    }

    static func segment(_ time: Double, _ start: Double, _ duration: Double) -> Double {
        clamp((time - start) / duration)
    }

    static func clamp(_ value: Double) -> Double {
        min(1, max(0, value))
    }

    static func smooth(_ value: Double) -> Double {
        let amount = clamp(value)
        return amount * amount * (3 - 2 * amount)
    }

    static func mix(_ from: Double, _ to: Double, _ amount: Double) -> Double {
        from + (to - from) * amount
    }

    static func table(_ time: Double, times: [Double], values: [Double]) -> Double {
        if time <= times[0] { return values[0] }
        for index in 1..<times.count where time <= times[index] {
            let amount = (time - times[index - 1]) / (times[index] - times[index - 1])
            return mix(values[index - 1], values[index], amount)
        }
        return values.last!
    }

    static let screenWidth = 402.0
    static let screenHeight = 874.0
    static let screenSafeTop = 62.0
}
