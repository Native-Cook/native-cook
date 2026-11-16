import Foundation

struct BreathVolumeLungsSpring {
    var from = 0.0
    var target = 0.0
    var velocity = 0.0
    var start = 0.0
    var stiffness = 322.3
    var damping = 30.5
    private var elapsed = 0.0
    private var lastTime: Double?

    init(from: Double = 0, target: Double = 0, velocity: Double = 0, start: Double = 0,
         stiffness: Double = 322.3, damping: Double = 30.5) {
        self.from = from; self.target = target; self.velocity = velocity; self.start = start
        self.stiffness = stiffness; self.damping = damping
    }
    mutating func sample(_ time: Double) -> (value: Double, velocity: Double, done: Bool) {
        // Reanimated caps each spring animation step at 64 ms, independently of the driver's 50 ms flow delta.
        elapsed += min(0.064, max(0, time - (lastTime ?? start)))
        lastTime = time
        let s = BreathVolumeLungsMotion.spring(elapsed, from: from, to: target, velocity: velocity,
                                             stiffness: stiffness, damping: damping)
        let initialEnergy = velocity * velocity + stiffness * pow(from - target, 2)
        let energy = s.velocity * s.velocity + stiffness * pow(s.value - target, 2)
        let done = initialEnergy == 0 || energy / initialEnergy <= 6e-9
        if done { from = target; velocity = 0; elapsed = 0; start = time }
        return (done ? target : s.value, done ? 0 : s.velocity, done)
    }
    mutating func retarget(_ target: Double, at time: Double) {
        let s = sample(time)
        from = s.value; velocity = s.velocity
        if (target > from && velocity < 0) || (target < from && velocity > 0) { velocity = 0 }
        self.target = target; start = time; elapsed = 0; lastTime = time
    }
}
