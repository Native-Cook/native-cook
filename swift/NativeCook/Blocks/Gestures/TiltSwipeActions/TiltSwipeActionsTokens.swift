import CoreGraphics
import Foundation

enum TiltSwipeActionsTokens {
    static let loop: TimeInterval = 10
    static let resume: TimeInterval = 3.5
    static let tidy: TimeInterval = 0.8

    enum Split {
        static let detentStart: CGFloat = 72
        static let detentStep: CGFloat = 60
        static let hysteresis: CGFloat = 8
        static let follow: CGFloat = 0.6
        static let contentFade: ClosedRange<CGFloat> = 20...56
        static let piecesIn: ClosedRange<CGFloat> = 56...84
        static let dimIn: ClosedRange<CGFloat> = 24...52
        static let dimTo: CGFloat = 0.28
        static let overRubber: CGFloat = 0.35
        static let gap: CGFloat = 6
        static let gapMax: CGFloat = 8
        static let trailMax: CGFloat = 3
    }

    enum Swipe {
        static let activeOffset: CGFloat = 12
        static let failY: CGFloat = 10
        static let openAt: CGFloat = 78
        static let openFlick: CGFloat = -500
        static let fullAt: CGFloat = 0.55
        static let fullRubber: CGFloat = 0.35
        static let rightRubber: CGFloat = 0.25
        static let deleteFlick: CGFloat = -1400
        static let deleteFlickAt: CGFloat = 0.4
    }

    enum Timing {
        static let toastIn: TimeInterval = 0.28
        static let toastOut: TimeInterval = 0.14
        static let toastVisible: TimeInterval = 4
        static let sheet: TimeInterval = 0.28
        static let reduced: TimeInterval = 0.18
        static let pressIn: TimeInterval = 0.10
        static let pressOut: TimeInterval = 0.16
        static let menuIn: TimeInterval = 0.20
        static let menuOut: TimeInterval = 0.15
        static let splitClose: TimeInterval = 0.48
        static let splitSettle: TimeInterval = 0.22
        static let swipeOpen: TimeInterval = 0.42
        static let swipeClose: TimeInterval = 0.36
        static let swipeArm: TimeInterval = 0.22
        static let slideOut: TimeInterval = 0.26
    }
}
