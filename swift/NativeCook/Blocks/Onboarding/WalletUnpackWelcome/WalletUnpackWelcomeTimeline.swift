import Foundation

/// The hero at one moment.
struct WalletUnpackWelcomeHero {
    var card = 1.0
    /// The card's printing, which goes before the card is edge-on.
    var face = 1.0
    var cardY = 0.0
    /// Degrees about the card's horizontal axis (top edge away) and in the screen's plane.
    var tip = 0.0
    var turn = 0.0
    var cardScale = 1.0
    var wallet = 0.0
    var walletScale = 1.0
    var note1Y = WalletUnpackWelcomeTimeline.noteRest
    var note2Y = WalletUnpackWelcomeTimeline.noteRest
    var note1Turn = 0.0
    var note2Turn = 0.0
    var note1Wave = 0.0
    var note2Wave = 0.0
    /// The ripple's phase, radians.
    var phase = 0.0
    var note1Blur = 0.0
    var note2Blur = 0.0
}

/// The hero as a function of one clock: `hero(at:)` turns the time into the sequence (the card
/// falls back and the wallet comes up, two notes rise out of it and slide back, the card rises
/// out and lands) into positions, angles and opacities. The autoPlay loop and a tap read the same one.
enum WalletUnpackWelcomeTimeline {
    /// Seconds of one autoPlay loop.
    static let loop = 7.5
    /// Seconds after the last touch, once the sequence has ended, before the loop starts again.
    static let userHold = 3.0

    // The sequence, in seconds from the start of a loop; a tap starts it at `outAt`.
    /// The card lies still until then.
    static let outAt = 0.8
    /// The card tips back past edge-on and drops behind the wallet, to show below it.
    private static let outFor = 0.7
    private static let tipFor = 0.6
    private static let tipAngle = 104.0
    private static let drop = 100.0
    private static let walletInAt = 0.25
    private static let walletIn = 0.3
    private static let walletFrom = 0.94
    private static let note1UpAt = 1.9
    private static let note2UpAt = 2.2
    private static let noteUp = 0.6
    private static let note2DownAt = 3.5
    private static let note1DownAt = 3.7
    private static let noteDown = 0.5
    /// The card rises out of the pocket, tilted, then lands flat on a spring as the wallet goes.
    private static let backAt = 4.7
    private static let rise = 0.55
    private static let riseFromY = 10.0
    private static let riseY = -88.0
    private static let riseTurn = -12.0
    private static let riseTip = 28.0
    private static let riseFromScale = 0.9
    private static let riseScale = 0.96
    private static let landResponse = 0.55
    private static let landDamping = 0.8
    private static let dissolveAt = 0.35
    private static let dissolve = 0.4
    /// The sequence is over and the card at rest by then.
    static let sequenceEnd = backAt + rise + 1

    // The two notes, in points from the hero's centre: resting with a pale edge showing above the
    // pocket, and how high each one rises.
    static let noteRest = -24.0
    private static let note1Up = -118.0
    private static let note2Up = -62.0
    private static let note1Turn = -3.0
    private static let note2Turn = 2.0
    /// The notes ripple while out: points of wave and its period.
    private static let wave = 3.5
    private static let wavePeriod = 1.1
    private static let blurRest = 0.8
    private static let blurMoving = 1.8

    private static func clamp01(_ x: Double) -> Double { min(max(x, 0), 1) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let x = clamp01((t - from) / (to - from))
        return x * x * (3 - 2 * x)
    }

    private static func easeOut(_ x: Double) -> Double {
        let k = 1 - clamp01(x)
        return 1 - k * k * k
    }

    private static func easeInOut(_ x: Double) -> Double {
        let k = clamp01(x)
        return k < 0.5 ? 4 * k * k * k : 1 - pow(-2 * k + 2, 3) / 2
    }

    /// A spring from 0 to 1 after `t` seconds, as `Animation.spring(response:dampingFraction:)` moves.
    private static func spring(_ t: Double, response: Double, damping: Double) -> Double {
        guard t > 0 else { return 0 }
        let w0 = tau / response
        let wd = w0 * (1 - damping * damping).squareRoot()
        return 1 - exp(-damping * w0 * t) * (cos(wd * t) + (damping * w0 / wd) * sin(wd * t))
    }

    /// How far a note is out of the wallet, 0 to 1, and how fast it is moving (0 still, 1 fastest).
    private static func lift(_ q: Double, upAt: Double, downAt: Double, still: Bool) -> (out: Double, moving: Double) {
        if still { return (q >= upAt && q < downAt + noteDown / 2 ? 1 : 0, 0) }
        if q < upAt || q >= downAt + noteDown { return (0, 0) }
        if q < upAt + noteUp {
            let p = (q - upAt) / noteUp
            return (easeOut(p), sin(.pi * p))
        }
        if q < downAt { return (1, 0) }
        let p = (q - downAt) / noteDown
        return (1 - easeInOut(p), sin(.pi * p))
    }

    /// The hero `q` seconds into the sequence; reduced motion swaps the movement for cross-fades.
    static func hero(at q: Double, still: Bool) -> WalletUnpackWelcomeHero {
        var h = WalletUnpackWelcomeHero()
        let c = q - outAt
        let b = q - backAt
        if c >= 0 && b < 0 {
            if still {
                h.card = 1 - smooth(c, 0, 0.3)
                h.wallet = smooth(c, 0, 0.3)
            } else {
                h.tip = tipAngle * easeInOut(c / tipFor)
                h.cardY = drop * easeInOut(c / outFor)
                h.face = 1 - smooth(h.tip, 60, 88)
                h.card = 1 - smooth(c, outFor - 0.2, outFor)
                h.wallet = smooth(c, walletInAt, walletInAt + walletIn)
                h.walletScale = walletFrom + (1 - walletFrom) * easeOut((c - walletInAt) / walletIn)
            }
        } else if b >= 0 {
            if still {
                h.card = smooth(b, 0, 0.3)
                h.wallet = 1 - smooth(b, 0, 0.3)
            } else {
                let gone = smooth(b, dissolveAt, dissolveAt + dissolve)
                h.wallet = 1 - gone
                h.walletScale = 1 - (1 - walletFrom) * gone
                if b < rise {
                    let r = easeOut(b / rise)
                    h.cardY = riseFromY + (riseY - riseFromY) * r
                    h.turn = riseTurn * r
                    h.tip = riseTip * r
                    h.cardScale = riseFromScale + (riseScale - riseFromScale) * r
                } else {
                    let left = 1 - spring(b - rise, response: landResponse, damping: landDamping)
                    h.cardY = riseY * left
                    h.turn = riseTurn * left
                    h.tip = riseTip * left
                    h.cardScale = riseScale + (1 - riseScale) * (1 - left)
                }
            }
        }

        let n1 = lift(q, upAt: note1UpAt, downAt: note1DownAt, still: still)
        let n2 = lift(q, upAt: note2UpAt, downAt: note2DownAt, still: still)
        h.note1Y = noteRest + (note1Up - noteRest) * n1.out
        h.note2Y = noteRest + (note2Up - noteRest) * n2.out
        h.note1Turn = still ? 0 : note1Turn * n1.out
        h.note2Turn = still ? 0 : note2Turn * n2.out
        h.note1Wave = still ? 0 : wave * n1.out
        h.note2Wave = still ? 0 : wave * n2.out
        h.phase = (q - note1UpAt) / wavePeriod * tau
        h.note1Blur = blurRest + blurMoving * n1.moving
        h.note2Blur = blurRest + blurMoving * n2.moving
        return h
    }
}
