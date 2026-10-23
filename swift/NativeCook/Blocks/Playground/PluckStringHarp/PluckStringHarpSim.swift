import Foundation

/// The harp's physics, ported step for step from the React Native `PluckStringHarpSim.ts`. One
/// plain object is stepped once a frame and the view draws what it finds.
///
/// Each string is three standing waves (harmonics 1, 2, 3), each a damped oscillator. A finger
/// that crosses a string catches it and drags it sideways into a triangle; past `slip` the string
/// slips free and the triangle is handed to the harmonics, which ring and die away. Neighbours are
/// driven by each other's fundamental, so a plucked string sets the strings beside it shimmering.
enum PluckStringHarpPhysics {
    static let strings = 8
    static let modes = 3
    static let noteNames = ["C", "D", "E", "F", "G", "A", "B", "C"]
    // Semitones above the lowest string: a C major scale.
    static let semitones: [Double] = [0, 2, 4, 5, 7, 9, 11, 12]

    // Visible fundamental of the lowest string (Hz). Real strings move too fast to see; this keeps
    // the wave readable at 60 fps while the scale still doubles across the octave.
    static let baseHz = 5.2
    // Decay rate of the fundamental (1/s); higher harmonics die faster by `harmonicDamp` per step.
    static let decay = 1.3
    static let harmonicDamp = 0.9
    // How hard a string's fundamental drives its neighbours (one and two strings away).
    static let sympathy = 0.022
    static let sympathyFar = 0.4

    // Catching: a finger crossing a string drags it up to `slip` pt sideways before it slips free.
    static let slip = 18.0
    // A touch that lands this close to a string catches it straight away.
    static let touchCatch = 11.0
    // Letting go of a string held less than this far still plucks it this hard.
    static let tapPull = 9.0
    // The catch point stays this far from either end of the string (share of its length).
    static let endGuard = 0.07

    // Glow: brightens with amplitude, then fades over `heatTau` seconds after the string quiets.
    static let heatTau = 0.55
    static let heatGain = 1.25

    // Note names that float up from each pluck.
    static let notePool = 12
    static let noteLife = 1.7
    // The finger's light trail: positions kept for `trailLife` seconds.
    static let trail = 18
    static let trailLife = 0.26

    // Clock: one capped step drives both the physics and the autoPlay script.
    static let dtCap = 1.0 / 30
    static let substeps = 8

    // autoPlay: an arpeggio up and back, a strum down, a strum up, one low note, then rest.
    static let loop = 9.5
    // After a touch the loop waits this long, then starts over.
    static let userHold = 3.0

    // Scripted strokes: [start (s), duration (s), from index, to index, from along, to along].
    static let script: [[Double]] = [
        // Arpeggio: C, E, G, C, G, E, each a short pluck to the right.
        [0.5, 0.22, -0.4, 0.75, 0.52, 0.55],
        [0.95, 0.22, 1.6, 2.75, 0.5, 0.53],
        [1.4, 0.22, 3.6, 4.75, 0.5, 0.53],
        [1.85, 0.24, 6.6, 7.8, 0.48, 0.52],
        [2.4, 0.22, 3.6, 4.75, 0.55, 0.58],
        [2.85, 0.22, 1.6, 2.75, 0.56, 0.59],
        // A strum down across all eight, then back up.
        [3.7, 0.42, -0.6, 7.7, 0.42, 0.56],
        [4.75, 0.46, 7.7, -0.6, 0.6, 0.46],
        // One low C to ring out the loop.
        [5.9, 0.26, -0.45, 0.8, 0.62, 0.64],
    ]

    /// Angular frequency of a string's fundamental.
    static func omega(_ string: Int) -> Double {
        2 * Double.pi * baseHz * pow(2, semitones[string] / 12)
    }

    static func clamp(_ v: Double, _ lo: Double, _ hi: Double) -> Double {
        v < lo ? lo : v > hi ? hi : v
    }

    static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 2 * t * t : 1 - pow(-2 * t + 2, 2) / 2
    }
}

/// Where the strings are on screen. Strings are vertical: `x`, and their two ends.
struct PluckStringHarpGeometry {
    var x: [Double]
    var top: [Double]
    var bottom: [Double]
}

/// The harp's state: the same fields as the RN `HarpSim`, mutated in place once a frame.
final class PluckStringHarpSim {
    private typealias P = PluckStringHarpPhysics

    var time = 0.0
    var clock = 0.0
    var hold = 0.0
    var looping: Bool
    var still: Bool
    // The finger (real or scripted) and where it was last frame.
    var down = false
    var scripted = false
    var justDown = false
    var fx = 0.0
    var fy = 0.0
    var px = 0.0
    var py = 0.0
    // Harmonic displacement (pt) and speed per string: index string * modes + mode.
    var q = [Double](repeating: 0, count: P.strings * P.modes)
    var qv = [Double](repeating: 0, count: P.strings * P.modes)
    var heat = [Double](repeating: 0, count: P.strings)
    // A caught string: side it is pulled to (+1 right, -1 left, 0 free), where, and how far.
    var held = [Double](repeating: 0, count: P.strings)
    var heldAt = [Double](repeating: 0.5, count: P.strings)
    var pull = [Double](repeating: 0, count: P.strings)
    // Floating note names.
    var noteString = [Int](repeating: 0, count: P.notePool)
    var noteX = [Double](repeating: 0, count: P.notePool)
    var noteY = [Double](repeating: 0, count: P.notePool)
    var noteAge = [Double](repeating: -1, count: P.notePool)
    var nextNote = 0
    // Finger trail, a ring of positions and when they were seen.
    var trailX = [Double](repeating: 0, count: P.trail)
    var trailY = [Double](repeating: 0, count: P.trail)
    var trailT = [Double](repeating: -10, count: P.trail)
    var trailHead = 0
    // Counts plucks made by a real finger; the view sends a haptic when it grows.
    var userPlucks = 0

    init(looping: Bool, still: Bool) {
        self.looping = looping
        self.still = still
    }

    /// Displacement of string `i` at `s` (0 at the top, 1 at the bottom), in pt. A caught string is
    /// a sharp triangle through the finger; a free one is the sum of its harmonics.
    func displacement(_ i: Int, _ s: Double) -> Double {
        if held[i] != 0 {
            let at = heldAt[i]
            return s <= at ? (pull[i] * s) / at : (pull[i] * (1 - s)) / (1 - at)
        }
        var d = 0.0
        for k in 0..<P.modes { d += q[i * P.modes + k] * sin(Double(k + 1) * .pi * s) }
        return still ? 0 : d
    }

    /// How far string `i` swings at most at `s`: the outline of its vibration, in pt.
    func envelope(_ i: Int, _ s: Double) -> Double {
        if held[i] != 0 || still { return 0 }
        let w = P.omega(i)
        var e = 0.0
        for k in 0..<P.modes {
            let j = i * P.modes + k
            let wk = w * Double(k + 1)
            let a = sqrt(q[j] * q[j] + (qv[j] / wk) * (qv[j] / wk))
            e += a * abs(sin(Double(k + 1) * .pi * s))
        }
        return e
    }

    /// Hands a triangle of height `pull` peaking at `at` to the harmonics. The plucked-string series
    /// b_k = 2·pull·sin(kπ·at) / (k²π²·at·(1−at)) is cut at three terms, then scaled so the curve
    /// still passes through the finger, so nothing jumps on release.
    private func release(_ g: PluckStringHarpGeometry, _ i: Int, _ pull: Double, sounded: Bool, user: Bool) {
        let at = heldAt[i]
        held[i] = 0
        self.pull[i] = 0
        var sum = 0.0
        var b = [0.0, 0.0, 0.0]
        for k in 0..<P.modes {
            let n = Double(k + 1)
            b[k] = (2 * sin(n * .pi * at)) / (n * n * .pi * .pi * at * (1 - at))
            sum += b[k] * sin(n * .pi * at)
        }
        let scale = abs(sum) > 1e-4 ? pull / sum : 0
        for k in 0..<P.modes {
            q[i * P.modes + k] = b[k] * scale
            qv[i * P.modes + k] = 0
        }
        if !sounded { return }
        heat[i] = max(heat[i], min(1, (abs(pull) / P.slip) * P.heatGain))
        // A note name rises from where the string was plucked.
        let n = nextNote
        nextNote = (n + 1) % P.notePool
        noteString[n] = i
        noteX[n] = g.x[i]
        noteY[n] = g.top[i] + (g.bottom[i] - g.top[i]) * at
        noteAge[n] = 0
        if user { userPlucks += 1 }
    }

    /// Where the finger is along string `i`, as a share of its length, kept off the ends. A string
    /// with no length yet (before layout, or after a relayout mid-catch) is caught in the middle.
    private func catchAt(_ g: PluckStringHarpGeometry, _ i: Int) -> Double {
        let length = g.bottom[i] - g.top[i]
        guard length > 1 else { return 0.5 }
        let at = (fy - g.top[i]) / length
        return at.isFinite ? P.clamp(at, P.endGuard, 1 - P.endGuard) : 0.5
    }

    /// Catches string `i` at the finger, pulled to `side`.
    private func grab(_ g: PluckStringHarpGeometry, _ i: Int, _ side: Double) {
        held[i] = side
        heldAt[i] = catchAt(g, i)
        pull[i] = fx - g.x[i]
        for k in 0..<P.modes {
            q[i * P.modes + k] = 0
            qv[i * P.modes + k] = 0
        }
    }

    /// Catches, drags and releases strings as the finger moves across them.
    private func touchStrings(_ g: PluckStringHarpGeometry) {
        let user = !scripted
        for i in 0..<P.strings {
            let x = g.x[i]
            if held[i] != 0 {
                let side = held[i]
                if !down {
                    // Let go: a string barely touched still sounds.
                    let p = abs(pull[i]) < P.tapPull ? side * P.tapPull : pull[i]
                    release(g, i, p, sounded: true, user: user)
                    continue
                }
                let p = fx - x
                if p * side < -2 {
                    // The finger went back across the string: it is let go without a sound.
                    release(g, i, 0, sounded: false, user: user)
                } else if abs(p) >= P.slip {
                    release(g, i, side * P.slip, sounded: true, user: user)
                } else {
                    pull[i] = p
                    heldAt[i] = catchAt(g, i)
                }
                continue
            }
            if !down { continue }
            // Is the finger on this string's length (a little past either end counts)?
            let within = fy > g.top[i] + 6 && fy < g.bottom[i] - 6
            if !within { continue }
            if justDown {
                if abs(fx - x) < P.touchCatch { grab(g, i, fx >= x ? 1 : -1) }
                continue
            }
            let before = px - x
            let now = fx - x
            if (before < 0 && now >= 0) || (before > 0 && now <= 0) {
                grab(g, i, now >= before ? 1 : -1)
                // A fast finger has already passed the slip point.
                if abs(pull[i]) >= P.slip { release(g, i, held[i] * P.slip, sounded: true, user: user) }
            }
        }
        justDown = false
    }

    /// Integrates every free string's harmonics over `dt`, with fixed substeps.
    private func ring(_ dt: Double) {
        let h = dt / Double(P.substeps)
        for _ in 0..<P.substeps {
            for i in 0..<P.strings {
                if held[i] != 0 { continue }
                let w = P.omega(i)
                // Sympathy: the neighbours' fundamentals pull on this one's.
                var drive = 0.0
                for d in -2...2 {
                    let j = i + d
                    if d == 0 || j < 0 || j >= P.strings { continue }
                    drive += q[j * P.modes] * (abs(d) == 1 ? 1 : P.sympathyFar)
                }
                for k in 0..<P.modes {
                    let n = i * P.modes + k
                    let wk = w * Double(k + 1)
                    let gamma = P.decay * (1 + P.harmonicDamp * Double(k))
                    let target = k == 0 ? P.sympathy * drive : 0
                    let accel = -wk * wk * (q[n] - target) - 2 * gamma * qv[n]
                    // Semi-implicit Euler: stable for these frequencies at this step.
                    qv[n] += accel * h
                    q[n] += qv[n] * h
                }
            }
        }
    }

    /// A point on the scripted finger's path: x in string spacings from the first string, y as a
    /// share of the string's length.
    private func scriptPoint(_ g: PluckStringHarpGeometry, _ index: Double, _ along: Double) -> (x: Double, y: Double) {
        let spacing = (g.x[P.strings - 1] - g.x[0]) / Double(P.strings - 1)
        let k = Int(P.clamp(floor(index), 0, Double(P.strings - 2)))
        let f = P.clamp(index - Double(k), 0, 1)
        let top = g.top[k] + (g.top[k + 1] - g.top[k]) * f
        let bottom = g.bottom[k] + (g.bottom[k + 1] - g.bottom[k]) * f
        return (g.x[0] + index * spacing, top + (bottom - top) * along)
    }

    /// Puts the scripted finger where the script says at `clock`, or lifts it.
    private func playScript(_ g: PluckStringHarpGeometry) {
        for stroke in P.script {
            let t = (clock - stroke[0]) / stroke[1]
            if t < 0 || t > 1 { continue }
            let e = P.easeInOut(t)
            let p = scriptPoint(g, stroke[2] + (stroke[3] - stroke[2]) * e, stroke[4] + (stroke[5] - stroke[4]) * e)
            if !down {
                down = true
                justDown = true
                scripted = true
                px = p.x
                py = p.y
            }
            fx = p.x
            fy = p.y
            return
        }
        if down && scripted { down = false }
    }

    /// A real finger touched down, moved or lifted.
    func fingerDown(_ x: Double, _ y: Double) {
        down = true
        justDown = true
        scripted = false
        fx = x
        fy = y
        px = x
        py = y
        hold = P.userHold
    }

    func fingerMove(_ x: Double, _ y: Double) {
        if !down || scripted { return }
        fx = x
        fy = y
        hold = P.userHold
    }

    func fingerUp() {
        if scripted { return }
        down = false
        hold = P.userHold
    }

    /// One frame: the script, the strings under the finger, the waves, the glows, the notes.
    func step(_ g: PluckStringHarpGeometry, frameDt: Double) {
        let dt = min(max(frameDt, 0), P.dtCap)
        if dt <= 0 { return }
        time += dt

        if hold > 0 {
            // A real touch pauses the loop; it starts over once the hand has been away a while.
            if !down { hold -= dt }
            if hold <= 0 { clock = 0 }
        } else if looping {
            clock += dt
            if clock >= P.loop { clock -= P.loop }
            playScript(g)
        }

        touchStrings(g)
        ring(dt)

        for i in 0..<P.strings {
            let level = held[i] != 0 ? abs(pull[i]) / P.slip : envelope(i, 0.5) / P.slip
            let fade = heat[i] * exp(-dt / P.heatTau)
            heat[i] = max(fade, min(1, level * P.heatGain))
        }

        for n in 0..<P.notePool {
            if noteAge[n] < 0 { continue }
            noteAge[n] += dt
            if noteAge[n] > P.noteLife { noteAge[n] = -1 }
        }

        if down {
            let h = (trailHead + 1) % P.trail
            trailHead = h
            trailX[h] = fx
            trailY[h] = fy
            trailT[h] = time
        }
        px = fx
        py = fy
    }
}
