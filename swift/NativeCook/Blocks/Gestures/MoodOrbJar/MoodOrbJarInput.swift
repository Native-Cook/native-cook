import Foundation

/// What a finger, a button or the autoplay script tells the simulation (jar-sim.ts `Input`).
enum MoodOrbJarInput {
    case down(x: Double, y: Double, script: Bool)
    case move(x: Double, y: Double)
    case up(vx: Double, vy: Double)
    case add(mood: Int)
    case undo
}

/// What the simulation tells the screen (jar-sim.ts `EVENT` and `HAPTIC` codes).
enum MoodOrbJarEvent: Equatable {
    case haptic(Haptic)
    case added(mood: Int)
    case removed

    enum Haptic {
        case grab
        case throwOrb
        case land
        case undo
        case landSoft
        case landHard
    }
}
