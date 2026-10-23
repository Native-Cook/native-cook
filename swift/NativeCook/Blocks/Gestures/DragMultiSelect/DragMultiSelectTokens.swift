import SwiftUI

/// Single source-of-truth mirror of the approved React Native theme.ts and motion.ts.
enum DragMultiSelectTokens {
    enum Color {
        static let background: UInt32 = 0xFBFAFD
        static let ink: UInt32 = 0x3A393E
        static let mute: UInt32 = 0xAEAEB2
        static let check: UInt32 = 0xB8B8BD
        static let note: UInt32 = 0xB4B4B9
        static let nav: UInt32 = 0x8E8E93
        static let tray: UInt32 = 0xE3C454
        static let trayLine: UInt32 = 0x9C7F22
        static let blue: UInt32 = 0x5B9BEE
        static let blueDeep: UInt32 = 0x3F86E6
        static let band: UInt32 = 0xD6E8FC
        static let bandSeam = RGBA(r: 255, g: 255, b: 255, a: 0.45)
        static let gloss = [RGBA(r: 255, g: 255, b: 255, a: 0), RGBA(r: 255, g: 255, b: 255, a: 0.85), RGBA(r: 255, g: 255, b: 255, a: 0)]
        static let circle: UInt32 = 0xC7C7CC
        static let toolbar: UInt32 = 0x2C2C2E
        static let toolbarInk: UInt32 = 0xFFFFFF
        static let white: UInt32 = 0xFFFFFF
        static let finger = RGBA(r: 60, g: 60, b: 67, a: 0.22)
        static let fingerEdge = RGBA(r: 60, g: 60, b: 67, a: 0.35)
    }

    struct RGBA: Equatable { let r: Int; let g: Int; let b: Int; let a: Double }

    enum Space { static let xs = 4.0; static let sm = 8.0; static let md = 12.0; static let lg = 16.0; static let gutter = 20.0; static let xl = 24.0; static let xxl = 32.0 }
    enum Layout {
        static let gutter = 20.0; static let nav = 44.0; static let navTop = 6.0; static let row = 48.0; static let listTop = 8.0
        static let check = 19.0; static let checkRadius = 5.0; static let textLeft = 48.0; static let circle = 26.0; static let circleRight = 12.0; static let circleColumn = 64.0
        static let bandInset = 8.0; static let bandRadius = 10.0; static let doneWidth = 68.0; static let doneHeight = 36.0; static let doneRadius = 8.0; static let more = 32.0
        static let toolbarWidth = 328.0; static let toolbarHeight = 46.0; static let toolbarRadius = 14.0; static let toolbarBottom = 1.3
        static let tools = [51.0, 164.5, 243.7, 300.0]; static let toolIcon = 22.0; static let fab = 56.0; static let fabBottom = 18.0; static let edge = Space.xxl * 2 + Space.sm; static let finger = 44.0
    }
    enum Typography {
        static let rowFontSize = 20.0
        static let rowLineHeight = 24.0
        static let rowWeight = 400
        static let navFontSize = 18.0
        static let navLineHeight = 22.0
        static let navWeight = 600
        static let doneFontSize = 17.0
        static let doneLineHeight = 22.0
        static let doneWeight = 600
        static let toolFontSize = 17.5
        static let toolLineHeight = 22.0
        static let toolWeight = 600
    }
    enum Ease { static let out = (0.23, 1.0, 0.32, 1.0); static let inOut = (0.77, 0.0, 0.175, 1.0) }
    enum Select { static let longPress = 320.0; static let dragSlop = 8.0; static let autoScrollMax = 720.0; static let coastDecay = 3.5; static let coastStop = 20.0 }
    struct Spring { let damping: Double; let stiffness: Double; let mass: Double }
    enum Springs { static let band = Spring(damping: 22, stiffness: 380, mass: 0.7); static let circles = Spring(damping: 24, stiffness: 300, mass: 0.8); static let toolbar = Spring(damping: 22, stiffness: 260, mass: 1); static let collapse = Spring(damping: 26, stiffness: 260, mass: 1); static let trash = Spring(damping: 7, stiffness: 380, mass: 0.6); static let ripple = Spring(damping: 20, stiffness: 200, mass: 1) }
    enum Timing { static let corner = 140.0; static let bandFade = 120.0; static let circleSlide = 16.0; static let circleStagger = 18.0; static let toolbarRise = 72.0; static let fab = 200.0; static let roll = 160.0; static let rollLift = 10.0; static let reduced = 180.0; static let scrollHome = 600.0; static let press = 120.0; static let trashUp = 80.0; static let toolStagger = 0.05; static let breathPeriod = 3.25; static let gloss = 460.0; static let ripple = 340.0; static let streamOut = 300.0; static let streamStagger = 55.0; static let restore = 320.0 }
    enum Component {
        static let glossWidth = 90.0
        static let rowWidth = 402.0
        static let toolbarSlot = 120.0
        static let previewScale = 0.15
        static let previewOpacity = 0.6
        static let leanScale = 0.015
        static let ringScale = 0.9
        static let trashRotation = 14.0
        static let trashScale = 0.35
        static let bandMinimumScale = 0.25
        static let bandProgressScale = 0.75
        static let bandOvershootLimit = 1.05
        static let selectedCircleMinimumScale = 0.6
        static let selectedCircleProgressScale = 0.4
        static let glossSkew = -18.0
        static let seamHeight = 1.5
        static let circleBorder = 1.5
        static let selectedCircleInset = 4.0
        static let selectedCircleBorder = 2.0
        static let rippleBorder = 2.0
        static let rippleOpacity = 0.6
        static let noteIcon = 14.0
        static let noteGap = 6.0
        static let checkBorder = 1.2
        static let checkDash = 3.6
        static let ghostPressScale = 0.25
        static let shellOvershootLimit = 1.08
        static let reducedMorphThreshold = 0.5
        static let colorMorphEnd = 0.6
        static let shadowOpacity = 0.3
        static let shadowBreathOpacity = 0.12
        static let shadowRadius = 12.0
        static let shadowBreathRadius = 4.0
        static let shadowY = 8.0
        static let breathScale = 0.025
        static let pressScale = 0.03
        static let plusFadeMultiplier = 3.0
        static let plusRotation = 45.0
        static let toolStart = 0.15
        static let toolDuration = 0.25
        static let toolTravel = 6.0
        static let toolGap = 6.0
        static let toolPadding = 10.0
        static let minimumHit = 44.0
        static let disabledOpacity = 0.5
        static let pressedOpacity = 0.7
        static let fabPressIn = 100.0
        static let fabPressOut = 160.0
        static let listBottomPadding = 32.0
        static let restoreLift = 25.0
        static let maximumFrameDelta = 0.05
        static let holdSlop = 10.0
    }
    enum Demo {
        static let QA_HIDE_FINGER = false
        static let LOOP_MS = 13_000.0
        static let RESUME_MS = 3_500.0
        /// The cross-platform component contract overrides the RN demo delay after a real touch.
        static let USER_RESUME_SECONDS = 2.0
        static let fingerFade = 0.15
        static let holdRow = 5
        static let holdDown = 0.6
        static let drag = [(at: 1.9, row: 5), (at: 2.6, row: 9), (at: 3.0, row: 7), (at: 3.55, row: 12)]
        static let release = 3.75
        static let tapRow = 1
        static let tapAt = 4.1
        static let edgeRow = 14
        static let edgeDown = 4.9
        static let edgeReach = 5.6
        static let edgeUp = 6.9
        static let doneAt = 7.2
        static let scrollHome = 7.5
        static let removeFrom = 8
        static let removeTo = 9
        static let removeDown = 8.7
        static let removeUp = 9.5
        static let removeTrash = 10.1
        static let removeRestore = 11.8
        static let tidy = 0.6
    }
}
