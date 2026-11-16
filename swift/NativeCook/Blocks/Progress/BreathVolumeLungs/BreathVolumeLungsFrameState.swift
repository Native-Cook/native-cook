import Foundation

struct BreathVolumeLungsFrameState {
    var fontAlpha = 1.0
    var progress = 0.0
    var flow = 0.0
    var time = 0.0
    var elapsed = 0.0
    var pressed = 0.0
    var rebound = 0.0
    var result = 0.0
    var packAge = -1.0
    var lastAir = 0.0
    static func pinned(_ time: Double, reduced: Bool) -> Self {
        typealias M = BreathVolumeLungsMotion
        let t = M.wrap(M.clamp(time, 0, M.loop))
        var f = Self()
        f.time = t; f.progress = M.progress(t); f.flow = reduced ? 0 : M.flow(t)
        f.elapsed = M.elapsed(t); f.result = M.result(t); f.lastAir = reduced ? 0 : M.lastAir(t)
        for crossing in M.crossings where t >= crossing && t - crossing < 0.24 { f.packAge = t - crossing }
        return f
    }
}
