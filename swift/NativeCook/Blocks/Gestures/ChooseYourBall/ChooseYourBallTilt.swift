import Foundation

// Free look: a vertical drag flies the camera over or under the ball, and letting go springs it
// home. The camera moves, not the ball, so the lights stay where they are and the highlight
// sweeps across the shell. Mirrors useBallTilt.ts.

@MainActor
final class ChooseYourBallTilt {
    /// -1...1 (a little past, with resistance): the player's intent, not an angle. Positive is a
    /// downward drag, which lifts the camera to look down on the crown. Read by the stage on
    /// every frame; nothing in SwiftUI depends on it.
    private(set) var value = 0.0

    /// With Reduce Motion on the gesture is off (see ChooseYourBallTouchRouter) and the way
    /// home, should the setting change mid-drag, is not played.
    var reduceMotion = false

    /// Finger travel for the full tilt, and how hard it resists past that.
    private static let reach = 190.0
    private static let resist = 2.6
    /// Slower and looser than the carousel's snap: nothing is decided by letting go.
    private static let homeSpring = ChooseYourBallSpring(mass: 0.9, stiffness: 150, damping: 17)

    private let runner = ChooseYourBallSpringRunner()

    init() {
        runner.onChange = { [weak self] value in self?.value = value }
    }

    /// `translation` is the finger's vertical travel since the drag was recognised.
    func changed(translation: Double) {
        runner.stop()
        let reach = translation / Self.reach
        let over = abs(reach) - 1
        if over <= 0 {
            value = reach
        } else {
            let resisted = 1 + over / (1 + over * Self.resist)
            value = reach < 0 ? -resisted : resisted
        }
    }

    /// Home on every way out of the gesture, a cancelled one included.
    func released() {
        if reduceMotion {
            runner.stop()
            value = 0
        } else {
            runner.run(from: value, to: 0, spring: Self.homeSpring)
        }
    }
}
