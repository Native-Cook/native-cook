import CoreGraphics
import Observation

// What a touch on this screen means. One finger, one of three things: a sideways drag turns the
// carousel, a vertical drag tilts the camera, and a touch that goes nowhere is a tap on whatever
// it landed on. The two drags race, each behind its own gate, exactly as the reference composes
// them: Gesture.Race(panGesture, tiltGesture) with activeOffset ±10 along the gesture's axis
// and failOffset ±28 across it (useChooseBallCarousel.ts, useBallTilt.ts).

//
// The router keeps no callbacks: the screen reads `isButtonPressed` and is told of a tap by what
// `ended` returns. A closure stored here would capture the view, which owns the router.

@MainActor
@Observable
final class ChooseYourBallTouchRouter {
    enum Target: Equatable {
        case button
        case thumbnail(slot: Int)
    }

    /// One drag's recogniser: it fails when the finger strays too far across its axis, and
    /// activates when it has gone far enough along it. Failure is tested first.
    private enum Gate {
        case possible, active, failed

        static let activation: CGFloat = 10
        static let failure: CGFloat = 28

        mutating func test(along: CGFloat, across: CGFloat) {
            guard self == .possible else { return }
            if abs(across) > Self.failure {
                self = .failed
            } else if abs(along) > Self.activation {
                self = .active
            }
        }
    }

    /// The button's pressed look: a finger is on it and has not become a drag.
    private(set) var isButtonPressed = false

    /// Whether the vertical drag exists at all. Reduce Motion turns it off.
    @ObservationIgnored var tiltEnabled = true

    @ObservationIgnored private let carousel: ChooseYourBallCarousel
    @ObservationIgnored private let tilt: ChooseYourBallTilt

    @ObservationIgnored private var isDown = false
    @ObservationIgnored private var pan = Gate.possible
    @ObservationIgnored private var look = Gate.possible
    /// Where the finger was when its drag was recognised: travel is counted from there.
    @ObservationIgnored private var origin = CGPoint.zero
    /// What the touch landed on, while it can still become a tap on it.
    @ObservationIgnored private var target: Target?

    init(carousel: ChooseYourBallCarousel, tilt: ChooseYourBallTilt) {
        self.carousel = carousel
        self.tilt = tilt
    }

    /// Every sample of the touch, the first included. `hitTest` says what lies under a point.
    func moved(to location: CGPoint, start: CGPoint, hitTest: (CGPoint) -> Target?) {
        if !isDown {
            isDown = true
            pan = .possible
            look = tiltEnabled ? .possible : .failed
            target = hitTest(start)
            carousel.touchBegan()
            if target == .button { isButtonPressed = true }
        }

        let dx = location.x - start.x
        let dy = location.y - start.y

        if pan != .active, look != .active {
            pan.test(along: dx, across: dy)
            if pan == .active {
                look = .failed
            } else {
                look.test(along: dy, across: dx)
                if look == .active { pan = .failed }
            }
            if pan == .active || look == .active {
                // A recognised drag takes the touch away from whatever was under it.
                origin = location
                release(target: nil)
            } else if let target, hitTest(location) != target {
                // The finger slid off the control it pressed.
                release(target: nil)
            }
        }

        if pan == .active {
            carousel.panChanged(translation: location.x - origin.x)
        } else if look == .active {
            tilt.changed(translation: location.y - origin.y)
        }
    }

    /// The finger lifted. `velocity` is its speed at that moment, in points per second.
    /// Returns true when the touch was a tap on the button.
    func ended(velocity: CGSize) -> Bool {
        guard isDown else { return false }
        var tappedButton = false
        if pan == .active {
            carousel.panEnded(velocity: velocity.width)
        } else if look != .active {
            switch target {
            case let .thumbnail(slot): carousel.select(slot: slot)
            case .button: tappedButton = true
            case nil: break
            }
        }
        isDown = false
        release(target: nil)
        tilt.released()
        return tappedButton
    }

    /// The touch was taken away (the system cancelled it, or a second finger joined it).
    func cancelled() {
        guard isDown else { return }
        isDown = false
        // Only a drag that had been recognised lands the loop; a touch that never became one
        // leaves it where the finger stopped it, as the reference does.
        if pan == .active { carousel.panCancelled() }
        release(target: nil)
        tilt.released()
    }

    private func release(target: Target?) {
        self.target = target
        if isButtonPressed { isButtonPressed = false }
    }
}
