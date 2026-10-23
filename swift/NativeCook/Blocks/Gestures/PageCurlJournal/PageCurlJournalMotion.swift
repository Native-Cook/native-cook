import Foundation

/// Page Curl Journal: every motion value (motion.ts).
enum PageCurlJournalMotion {
    // QA holds freeze one state for the fidelity screenshots; the shipped block has none.
    enum Hold { case rest, a, b, p1, p2 }
    static let qaHold: Hold? = nil

    // Slow-motion review: the autoplay clock, every spring and every timing run at qaSpeed (1 in the shipped block),
    // and qaStart starts the loop at that time.
    static let qaSpeed = 1.0
    static let qaStart = 0.0

    /// Spring settings: a mass-spring, or (Reanimated's duration form) a perceptual duration and a damping ratio.
    struct Spring {
        // Reanimated 4's installed GentleSpringConfig defaults used when a duration spring omits physical values.
        var mass = 4.0
        var stiffness = 900.0
        var damping = 120.0
        var durationMs: Double?
        var dampingRatio: Double?

        init(mass: Double, stiffness: Double, damping: Double) {
            self.mass = mass
            self.stiffness = stiffness
            self.damping = damping
        }

        init(durationMs: Double, dampingRatio: Double) {
            self.durationMs = durationMs
            self.dampingRatio = dampingRatio
        }

        /// motion.ts `sp()`: stiffness × k², damping × k; a duration spring lasts duration / k.
        var scaled: Spring {
            let k = PageCurlJournalMotion.qaSpeed
            if let durationMs, let dampingRatio {
                return Spring(durationMs: durationMs / k, dampingRatio: dampingRatio)
            }
            return Spring(mass: mass, stiffness: stiffness * k * k, damping: damping * k)
        }
    }

    static func ms(_ x: Double) -> Double { x / qaSpeed }

    /// The source's mid-turn frame (015): the top-right corner of the title page lies at this point.
    static let holdA = (grab: (x: 402.0, y: -4.0), tip: (x: -187.68, y: 250.27))

    enum Turn {
        static let pickSlop = 4.0 // pt of travel before a drag picks forward or back
        static let commitVelocity = 450.0 // pt/s: a flick commits whatever the distance
        static let commitProgress = 0.5 // past half a turn the page goes over on release
        static let rampIn = 120.0 // pt of pull over which the roll grows to its full radius
        static let rampOut = 140.0 // and shrinks as it lands flat
        // Mass-spring for the released page: a little heavier going over than falling back.
        static let over = Spring(mass: 1, stiffness: 190, damping: 27)
        static let back = Spring(mass: 1, stiffness: 260, damping: 32)
        // The sheet flexes with speed: the roll opens up to +60 % at 2500 pt/s and springs back when it lands.
        static let flexPerSpeed = 1.0 / 4200.0
        static let flexMax = 0.6
        static let flex = Spring(mass: 1, stiffness: 240, damping: 14)
        static let releaseKick = 1.0 / 9000.0 // a release kicks the roll's flex by its speed
        static let buttonGrabY = 0.62 // "Turn the page" lifts the right edge at 62 % of the height
        static let preLift = 10.0 // pt: touch-down in the right 40 % lifts the corner within a frame
        static let preLiftZone = 0.6
        static let backFactor = 2.0 // a backward drag moves the fold 1:1, so the hidden corner moves twice as far
    }

    /// The cast shadow answers the lift: narrower and lighter while the sheet is low.
    enum Lift {
        static let widthBase = 0.6
        static let widthPerTurn = 0.5
        static let darkBase = 0.8
        static let darkPerTurn = 0.25
    }

    /// Riffle: a flick right this fast turns every page back to the title, sheets in flight together.
    enum Riffle {
        static let velocity = 1500.0 // pt/s of the fold
        static let staggerMs = 180.0
        static let spring = Spring(mass: 1, stiffness: 260, damping: 30)
        static let launch = 1800.0 // pt/s each later sheet starts with
        static let slots = 3
    }

    /// Haptic grain while dragging: a tick per step of fold travel, never closer than minGapMs.
    enum Grain {
        static let stepPt = 34.0
        static let minGapMs = 55.0
    }

    /// Idle nudge: the bottom-right corner lifts and falls back with a wobble.
    enum Nudge {
        static let afterMs = 4500.0
        static let everyMs = 7000.0
        static let lift = (x: -30.0, y: -18.0)
        static let up = Spring(durationMs: 420, dampingRatio: 0.85)
        static let down = Spring(mass: 1, stiffness: 300, damping: 16)
        static let holdMs = 160.0
        static let kick = 1.2 // flex impulse when it drops
        static let max = 2 // two hints per idle stretch, then still until the next touch
    }

    /// The mark's sparkles twinkle when the title page settles.
    enum Twinkle {
        static let durationMs = 900.0
        static let scale = 1.35
        static let turnDeg = 45.0
        static let staggerMs = 120.0
    }

    /// Foil light on the cover: tilt moves the band; on its own it sweeps every 6 s and once on landing.
    enum Foil {
        static let sweepMs = 1400.0
        static let everyMs = 6000.0
        static let width = 26.0 // pt
        static let amount = 0.62
        static let angleDeg = 62.0 // band direction from the x axis
        static let tiltGain = 260.0 // pt of band travel per radian of roll
        static let rest = -400.0 // band position at rest: off the title
        static let reach = 520.0 // band position at the end of a sweep
    }

    /// Page-stack edge at the right: one hairline per sheet still to turn.
    enum Stack {
        static let gap = 1.6
        static let line = 1.0
    }

    static let fadeMs = 200.0 // Reduce Motion: pages cross-fade
    static let idleResumeMs = 3500.0

    /// Autoplay: a scripted finger through the same input as a real one. Times in ms on a 14 s loop.
    /// Each stroke: down at `from`, moves through `via` to `to`, lifts at t1 with the path's speed.
    static let loopMs = 14000.0
    struct Stroke {
        let t0: Double
        let t1: Double
        let from: SIMD2<Double>
        let via: SIMD2<Double>
        let to: SIMD2<Double>
    }

    static let script: [Stroke] = [
        // A hesitant peek at the corner, let go: the page falls back.
        Stroke(t0: 900, t1: 2500, from: [384, 64], via: [150, 330], to: [310, 170]),
        // Title page over to the preface: a short drag and a flick.
        Stroke(t0: 3200, t1: 3700, from: [386, 540], via: [300, 520], to: [150, 480]),
        Stroke(t0: 5300, t1: 5800, from: [388, 300], via: [300, 310], to: [160, 340]),
        Stroke(t0: 7400, t1: 7900, from: [388, 690], via: [300, 670], to: [160, 610]),
        // A fast flick right from the cover: the riffle turns every page back to the title.
        Stroke(t0: 9900, t1: 10180, from: [60, 450], via: [120, 446], to: [260, 438]),
    ]

    /// Autoplay: the idle nudge on the title page after the riffle.
    static let nudgeAtMs = 12200.0
}
