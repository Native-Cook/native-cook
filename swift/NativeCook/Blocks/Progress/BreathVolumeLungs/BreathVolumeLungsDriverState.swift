import Foundation

struct BreathVolumeLungsDriverState {
    var clock = 0.0
    var epoch: Double?
    var fontStart: Double?
    var phase = 0
    var p = 0.0
    var flow = 0.0
    var elapsed = 0.0
    var initial = 0.0
    var anchor = 0.0
    var dragOffset = 0.0
    var releaseVelocity = 0.0
    var releaseOver = 0.0
    var touched = false
    var fingerDown = false
    var scrubRegion = false
    var lastPack = -10.0
    var airPeak = -10.0
    var lastCue = -10.0
    var resultAt = 0.0
    var resultFrom = 0.0
    var resultTo = 0.0
    var press = BreathVolumeLungsSpring()
    var refill = BreathVolumeLungsSpring()
    var rebound = BreathVolumeLungsSpring(stiffness: 180, damping: 10)
    var reboundActive = false
    var adjusting = false
}
