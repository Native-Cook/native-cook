import Foundation

/// The block's clock: inserting and ejecting a tape as functions of time.
enum TapeShelfPlayerMotion {
    static let loop = 10.0
    /// The tape the loop plays (the middle of the grid).
    static let loopTape = 4
    /// The loop inserts at 0.8 s and ejects at 6.6 s.
    static let insertAt = 0.8
    static let ejectAt = 6.6
    /// How long the two sequences take.
    static let insertLength = 2.2
    static let ejectLength = 2.2
    /// When, inside the insert sequence, the screen starts to light up.
    static let powerOnAt = 1.7

    static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }
    static func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
        let t = clamp01((x - a) / (b - a))
        return t * t * (3 - 2 * t)
    }
    static func easeInOut(_ x: Double) -> Double {
        let t = clamp01(x)
        return t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - clamp01(x), 3) }
    static func easeIn(_ x: Double) -> Double { pow(clamp01(x), 3) }
    static func ramp(_ a: Double, _ b: Double, _ x: Double) -> Double { clamp01((x - a) / (b - a)) }

    struct Pose {
        var tape = loopTape
        /// The sleeve rising off the shelf to let its cassette out (or in): 0 to 1.
        var lift = 0.0
        /// The cassette sliding out of the sleeve to the right: 0 in, 1 out.
        var slide = 0.0
        /// The flight from beside the sleeve to above the slot: 0 to 1.
        var flight = 0.0
        /// Going into the slot (squashed flat as it disappears): 0 to 1.
        var squash = 0.0
        var cassetteAlpha = 0.0
        /// The screen: 0 off, 1 on; the power line effect runs between.
        var screen = 0.0
        /// 1 while powering on, −1 while powering off (which picture the transition shows).
        var powering = 1.0
        var led = 0.0
        /// The player's jolt when the tape seats, in points.
        var bounce = 0.0
        var transport = 0.0
        var pill = 1.0
        var ejectPress = 0.0
        /// Seconds the tape has been playing (drives the picture on the screen).
        var video = 0.0
    }

    /// e seconds into inserting `tape`; after `insertLength` the tape plays on.
    static func insert(_ e: Double, tape: Int) -> Pose {
        var p = Pose()
        p.tape = tape
        p.lift = easeOut(e / 0.25) * (1 - smoothstep(0.6, 0.9, e))
        p.slide = easeInOut((e - 0.15) / 0.45)
        p.flight = easeInOut((e - 0.6) / 0.75)
        p.squash = easeIn((e - 1.35) / 0.3)
        p.cassetteAlpha = (e > 0.1 ? 1 : 0) * (1 - ramp(1.55, 1.65, e))
        let tau = e - 1.65
        p.bounce = tau > 0 ? 3 * exp(-8 * tau) * sin(25 * tau) : 0
        p.led = e >= 1.65 ? 1 : 0
        p.screen = ramp(powerOnAt, powerOnAt + 0.35, e)
        p.powering = 1
        p.transport = easeOut((e - 1.8) / 0.4)
        p.pill = 1 - easeInOut((e - 1.6) / 0.3)
        p.video = max(0, e - powerOnAt)
        return p
    }

    /// e seconds into ejecting `tape` (played for `played` seconds); after `ejectLength` all is at rest.
    static func eject(_ e: Double, tape: Int, played: Double) -> Pose {
        var p = Pose()
        p.tape = tape
        p.ejectPress = smoothstep(0, 0.06, e) * (1 - smoothstep(0.12, 0.25, e))
        p.screen = 1 - ramp(0, 0.3, e)
        p.powering = -1
        p.transport = 1 - easeInOut((e - 0.1) / 0.4)
        p.pill = easeOut((e - 0.4) / 0.4)
        p.led = e < 0.3 ? 1 : 0
        p.squash = 1 - easeOut((e - 0.3) / 0.3)
        p.cassetteAlpha = e >= 1.8 ? 0 : ramp(0.3, 0.4, e)
        p.flight = 1 - easeInOut((e - 0.6) / 0.75)
        p.lift = easeOut((e - 1.2) / 0.25) * (1 - smoothstep(1.8, 2.2, e))
        p.slide = 1 - easeInOut((e - 1.35) / 0.45)
        p.video = played + e
        return p
    }

    /// The loop at time t.
    static func loopPose(_ t: Double) -> Pose {
        if t >= insertAt && t < ejectAt { return insert(t - insertAt, tape: loopTape) }
        if t >= ejectAt && t < ejectAt + ejectLength {
            return eject(t - ejectAt, tape: loopTape, played: ejectAt - insertAt - powerOnAt)
        }
        var p = Pose()
        p.tape = loopTape
        return p
    }
}
