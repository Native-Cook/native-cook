import Foundation

/// Every motion value of the block. Times in seconds unless named in milliseconds.
enum CassetteDeckMotion {
    /// The simulation runs at a fixed step so the autoplay is identical on every device and every loop.
    static let step = 1.0 / 120

    /// Keys: a spring-loaded cap that bottoms out on the chassis (restitution) and clicks there.
    enum Key {
        static let pressStiffness = 1500.0 // finger force: ~70 ms to the bottom
        static let pressDamping = 34.0
        static let returnStiffness = 520.0 // the cap's spring pushing it back up
        static let returnDamping = 17.0 // under-damped: a small rebound at the top
        static let bottomRestitution = 0.18
        static let topRestitution = 0.32
        static let latchDepth = 0.86 // a latched play key rests just above the bottom, caught by the latch
        static let rearm = 0.45 // depth below which a key can click again
    }

    /// Transport: the capstan motor spins the tape up with inertia; a brake and friction stop it in finite time.
    enum Transport {
        static let spinUpTau = 0.32
        static let brake = 0.55 // constant deceleration (speed units per second)
        static let friction = 1.0 // viscous term (per second)
        static let wind = 6.0 // fast-forward / rewind speed in play-speed units
        static let engageTau = 0.15 // a loaded cassette's hubs take up the capstan speed
        static let baseOmega = 2.6 // rad/s of a reel whose tape pack is refRadius at play speed
        static let refRadius = 25.0
        static let progressRate = 1.0 / 900 // tape fraction per second at play speed
    }

    /// Cassettes sit one screen width apart and are swapped like pages with momentum.
    enum Pager {
        static let stiffness = 180.0
        static let damping = 24.0
        static let momentum = 0.18 // seconds of release velocity projected forward
        static let loadedWithin = 0.08 // pages from centre at which the deck engages a cassette
        static let rotateMax = 7.0 // degrees of yaw at full swipe speed
        static let rotatePerSpeed = 3.2 // degrees per page/s
        /// RN's `perspective: 900 * s`: distance of the viewer in points at 402 pt wide.
        static let perspective = 900.0
    }

    /// The deck answers each key with a dip of the chassis and a rattle of the cassette in its tray.
    enum Chassis {
        static let dipStiffness = 900.0
        static let dipDamping = 30.0
        static let dipImpulse = 0.012 // pt of dip per pt/s of the key's impact speed
        static let dipTravel = 1.0 // pt per unit
        static let rattleStiffness = 420.0
        static let rattleDamping = 9.0
        static let rattleImpulse = 0.02
        static let rattleTravel = 1.0
        /// The cassette's arrival kick, in pt/s at one page/s, matching the RN seat clack.
        static let seatKick = 40.0
    }

    /// Grab a loaded reel and turn it; the tape follows the finger and coasts on release.
    enum Scrub {
        static let grabRadius = 34.0 // pt around a reel centre
        static let maxSpeed = 14.0 // play-speed units
        /// Keep selection haptics below 25 Hz while a reel is scrubbed.
        static let tickMinGap = 0.04
    }

    /// Reels blur when winding; the shell catches a sheen as it slides.
    enum Material {
        static let blurFrom = 4.0 // rad/s at which the ghost hubs start to show
        static let blurSpan = 8.0
        static let shutter = 1.0 / 60 // s of turn each ghost trails by (spread over the ghosts)
        static let ghosts = 1.0
        static let sheenTravel = 0.6 // of a cassette width per cassette width moved
        static let sheenGain = 3.0
        static let edge = 4.5 // pt: the shell's thickness seen when it yaws
        static let ledFlicker = 0.16 // a level flicker while playing
        static let keyGlow = 0.55 // opacity of the red spill under a latched play key
    }

    enum Light {
        static let ledTau = 0.06
        static let glyphOnTau = 0.04
        static let glyphOffTau = 0.22
    }

    /// Autoplay: a scripted finger on one clock. The loop starts and ends stopped on the first cassette.
    static let loopMS = 9000.0
    /// Source constant retained for RN parity; the Swift main simulation resumes after the user contract's 2 s.
    static let resumeMS = 3500.0
    static let interactionResumeMS = 2000.0

    /// Numeric parity for the RN haptic event table. The live Swift simulation emits `CassetteDeckHaptic` cases.
    enum Haptic {
        static let click = 1
        static let latch = 2
        static let release = 3
        static let commit = 4
        static let tick = 5
    }

    enum Script {
        static let play = (start: 0.7, end: 0.86)
        static let next = (start: 3.3, end: 3.62)
        /// A flick to the right on the cassette brings the first one back: p goes 1 → 1 − dist over `time`, accelerating.
        static let drag = (start: 5.4, time: 0.32, dist: 0.3, power: 1.6)
        static let ff = (start: 4.3, end: 4.9)
        static let pause = (start: 7.0, end: 7.16)
    }

    /// Reduce Motion: no travel; changes cross-fade.
    enum Reduced {
        static let fade = 0.18
    }

    // QA-only holds of the React Native block, at their shipped values (off).
    enum QAHold { case rest, a, b }
    static let qaHold: QAHold? = nil
    static let qaBPage = 0.599
    /// QA: freeze the autoplay at this script time (seconds) for proof frames.
    static let qaT: Double? = nil
}
