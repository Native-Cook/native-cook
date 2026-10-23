import SwiftUI

/// Typed adapters for every value in `motion.ts`; the single source of truth is `TiltSwipeActionsTokens`.
enum TiltSwipeActionsMotion {
    enum Swipe {
        private typealias Token = TiltSwipeActionsTokens.Swipe
        static let activeOffsetX = Token.activeOffsetX; static let failOffsetY = Token.failOffsetY
        static let fullAt = Token.fullAt; static let fullRubber = Token.fullRubber; static let rightRubber = Token.rightRubber
        static let openAt = Token.openAt; static let openFlick = Token.openFlick
        static let deleteFlick = Token.deleteFlick; static let deleteFlickAt = Token.deleteFlickAt
    }

    enum Tilt {
        private typealias Token = TiltSwipeActionsTokens.Tilt
        static let perVelocity = Token.perVelocity; static let max = Token.max
        static let perspective = Token.perspective; static let lift = Token.lift
    }

    enum Spring {
        private typealias Token = TiltSwipeActionsTokens.Spring
        static let follow = value(Token.follow); static let snap = value(Token.snap); static let lift = value(Token.lift)
        static let tileStretch = value(Token.tileStretch); static let collapse = value(Token.collapse); static let restore = value(Token.restore)
        static let pill = value(Token.pill); static let lens = value(Token.lens); static let menu = value(Token.menu)
        private static func value(_ token: (damping: Double, stiffness: Double, mass: Double)) -> TiltSwipeActionsSpring {
            TiltSwipeActionsSpring(damping: token.damping, stiffness: token.stiffness, mass: token.mass)
        }
    }

    enum Timing {
        private typealias Token = TiltSwipeActionsTokens.Timing
        static let flyOut = Double(Token.flyOut) / 1_000; static let tileFade = Double(Token.tileFade) / 1_000
        static let colour = Double(Token.colour) / 1_000; static let undoVisible = Double(Token.undoVisible) / 1_000
        static let reduced = Double(Token.reduced) / 1_000; static let reducedGap = Double(Token.reducedGap) / 1_000
        static let pressScale = Token.pressScale; static let pressIn = Double(Token.pressIn) / 1_000; static let pressOut = Double(Token.pressOut) / 1_000
        static let menuStagger = Token.menuStagger; static let tab = Double(Token.tab) / 1_000; static let roll = Double(Token.roll) / 1_000
        static let breath = Token.breath; static let sheen = Token.sheen; static let tabSlide = Token.tabSlide
        static let restoreDelay = Double(Token.restoreDelay) / 1_000
    }

    static let loop = Double(TiltSwipeActionsTokens.LOOP_MS) / 1_000
    static let resume = Double(TiltSwipeActionsTokens.RESUME_MS) / 1_000

    enum Demo {
        private typealias Token = TiltSwipeActionsTokens.Demo
        static let fingerFade = Token.fingerFade; static let open = Token.open; static let remove = Token.remove
        static let undo = Token.undo; static let lean = Token.lean; static let tidy = Token.tidy
    }
}
