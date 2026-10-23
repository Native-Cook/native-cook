import Foundation

/// One orb of the pool (jar-sim.ts keeps these as parallel arrays).
struct MoodOrbJarOrb {
    enum State {
        case off
        case pile
        case settling
        case flying
        case held
        case leaving
        case returning

        /// In the jar: part of the pile or landing on it.
        var isInJar: Bool {
            self == .pile || self == .settling
        }
    }

    var x = 0.0
    var y = 0.0
    var vx = 0.0
    var vy = 0.0
    /// Where the orb rests in the pile (its slot).
    var homeX = 0.0
    var homeY = 0.0
    var mood = 0
    var state = State.off
    var restTime = 0.0
    /// Seconds since the start of a return or departure; -1 while a landing has not made its first impact.
    var time = 0.0
    var spin = 0.0
    var fade = 0.0
    var fromX = 0.0
    var fromY = 0.0
    /// Soft body: squash and its spring velocity.
    var squash = 0.0
    var squashVelocity = 0.0
    /// The vertical speed before this step, to read a sudden stop.
    var previousVY = 0.0
}
