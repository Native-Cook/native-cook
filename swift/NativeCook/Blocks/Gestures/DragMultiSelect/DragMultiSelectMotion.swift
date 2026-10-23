import Foundation
import SwiftUI

/// Every motion value of the block. Distances in pt, times in ms unless named in seconds.
/// These curves are the source block's stronger ease-out and in-out bezier tokens.
enum DragMultiSelectEase {
    private static let outToken = DragMultiSelectTokens.Ease.out
    private static let inOutToken = DragMultiSelectTokens.Ease.inOut
    static let out = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: outToken.0, y: outToken.1),
        endControlPoint: UnitPoint(x: outToken.2, y: outToken.3)
    )
    static let inOut = UnitCurve.bezier(
        startControlPoint: UnitPoint(x: inOutToken.0, y: inOutToken.1),
        endControlPoint: UnitPoint(x: inOutToken.2, y: inOutToken.3)
    )
}

enum DragMultiSelectSelect {
    private typealias Token = DragMultiSelectTokens.Select
    static let longPress = Token.longPress
    /// Vertical travel on the circles column before a range drag starts.
    static let dragSlop = Token.dragSlop
    /// pt/s at the very edge.
    static let autoScrollMax = Token.autoScrollMax
    /// 1/s: momentum after a drag released while auto-scrolling.
    static let coastDecay = Token.coastDecay
    /// pt/s.
    static let coastStop = Token.coastStop
}

typealias DragMultiSelectSpring = DragMultiSelectTokens.Spring

enum DragMultiSelectSprings {
    static let band = DragMultiSelectTokens.Springs.band
    static let circles = DragMultiSelectTokens.Springs.circles
    static let toolbar = DragMultiSelectTokens.Springs.toolbar
    /// Declared source `SPRING.collapse` value. The source layout wrapper does not pass this mass.
    static let collapse = DragMultiSelectTokens.Springs.collapse
    static let trash = DragMultiSelectTokens.Springs.trash
    /// Actual layout-wrapper runtime value: the source transition omits mass and Reanimated's
    /// default is 4. Keep it separate so the declared source config above remains exact.
    static let layoutCollapse = DragMultiSelectSpring(damping: 26, stiffness: 260, mass: 4)
    static let ripple = DragMultiSelectTokens.Springs.ripple
    /// `FadeInDown.springify().damping(18)`: stiffness 900 and mass 4 are Reanimated's defaults.
    /// Swift-only compatibility value; RN has no `restore` spring token.
    static let restore = DragMultiSelectSpring(damping: 18, stiffness: 900, mass: 4)
}

enum DragMultiSelectTiming {
    private typealias Token = DragMultiSelectTokens.Timing
    static let corner = Token.corner
    static let bandFade = Token.bandFade
    static let circleSlide = Token.circleSlide
    static let circleStagger = Token.circleStagger
    static let toolbarRise = Token.toolbarRise
    static let fab = Token.fab
    static let roll = Token.roll
    static let rollLift = Token.rollLift
    static let reduced = Token.reduced
    static let scrollHome = Token.scrollHome
    static let press = Token.press
    static let trashUp = Token.trashUp
    /// Morph progress between tools appearing.
    static let toolStagger = Token.toolStagger
    /// Seconds; four per 13 s loop.
    static let breathPeriod = Token.breathPeriod
    /// A light sweep across a band when its row joins.
    static let gloss = Token.gloss
    /// The circle's ring when a row is picked.
    static let ripple = Token.ripple
    /// A deleted row slides out.
    static let streamOut = Token.streamOut
    static let streamStagger = Token.streamStagger
    static let restore = Token.restore
}

/// Autoplay: one scripted finger on one clock, seconds. Rows are indices into the tasks.
enum DragMultiSelectDemoScript {
    /// QA only: hides the demo's finger for the pixel gate. Ship false.
    private typealias Token = DragMultiSelectTokens.Demo
    static let QA_HIDE_FINGER = Token.QA_HIDE_FINGER
    static let LOOP_MS = Token.LOOP_MS
    /// Source token retained verbatim. Real-touch return-to-rest uses the separate 2 s override below.
    static let RESUME_MS = Token.RESUME_MS
    /// Product requirement for a real touch: return to rest two seconds after the final touch.
    /// The core owns the behavior and should use this override instead of `RESUME_MS`.
    static let USER_RESUME_SECONDS = Token.USER_RESUME_SECONDS

    // Lower-case aliases keep existing Swift call sites readable while preserving source names.
    static let hidesFinger = QA_HIDE_FINGER
    static let loopMS = LOOP_MS
    static let resumeMS = RESUME_MS
    static let userResumeSeconds = USER_RESUME_SECONDS
    static let fingerFade = Token.fingerFade
    /// Long press; selection starts at down + longPress.
    static let hold = (row: Token.holdRow, down: Token.holdDown)
    static let drag = Token.drag
    static let release = Token.release
    static let tap = (row: Token.tapRow, at: Token.tapAt)
    /// Drag on the circles into the bottom edge and hold.
    static let edge = (row: Token.edgeRow, down: Token.edgeDown, reach: Token.edgeReach, up: Token.edgeUp)
    static let doneAt = Token.doneAt
    static let scrollHome = Token.scrollHome
    /// Select Train and Tetto, delete them, put them back.
    static let remove = (from: Token.removeFrom, to: Token.removeTo, down: Token.removeDown, up: Token.removeUp, trash: Token.removeTrash, restore: Token.removeRestore)
    static let tidy = Token.tidy
}
