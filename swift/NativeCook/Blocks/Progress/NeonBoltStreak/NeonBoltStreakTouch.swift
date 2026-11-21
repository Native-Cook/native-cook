import Foundation

struct NeonBoltStreakTouch {
    enum Mode { case pressing, igniting, flickering, celebrating }

    var mode: Mode
    var day: Int
    var started: Date
    var countGesture = false
    var charge: CGFloat = 0
    var chargeHapticPlayed = false
    var cancelled = false
    var lastDay: Int
    var beganCharging = false
    var flickers: [(day: Int, started: Date)] = []
}
