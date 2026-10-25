import Foundation
import UIKit

// Stained Glass Splash — the loop's script and the hand-driven sun (a port of stainedGlassMotion.ts).
//
// loop = 9 s:
//   0.0 – 0.8  rest: the window at a quarter glow in its stone, the wordmark at 55 %.
//   0.8 – 4.4  the sun crosses: panes light by their key (left to right, outer rings first, the gold
//              rose of the hub last), the beam moves with it, the floor fills with colour.
//   4.4 – 5.6  the projection peaks: the beam settles in the middle, the floor at its brightest.
//   5.6 – 6.4  flood: warm light pours out of the window into cream (ease-in-out); the home screen
//              rises in over 5.8 – 6.25 (ease-out, 12 pt). Under the cream the panes go dark again
//              and the beam returns to the left.
//   6.4 – 7.6  home.
//   7.8 – 9.0  home fades (8.0 – 8.5) as the cream drains back into the window (7.8 – 8.8) and the
//              wordmark returns (8.4 – 9.0): the frame at 9 s is the frame at 0 s.

enum StainedGlassSplashMotion {
    /// The lit range of keys at rest (nothing lit).
    static let dark = -0.12

    static func clamp01(_ x: Double) -> Double { min(1, max(0, x)) }

    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }

    static func easeInOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return c < 0.5 ? 4 * c * c * c : 1 - pow(-2 * c + 2, 3) / 2
    }

    static func easeOut(_ x: Double) -> Double {
        let c = clamp01(x)
        return 1 - pow(1 - c, 3)
    }

    static func loopScene(_ t: Double) -> StainedGlassSplashScene {
        var s = StainedGlassSplashScene()
        // Ease-out: the first panes catch the sun within half a second of 0.8 s.
        let cross = 1 - pow(1 - seg(t, 0.8, 4.4), 1.6)
        s.lo = dark
        s.hi = t < 6.4 ? dark + (1.25 - dark) * cross : dark
        // The beam crosses with the light, settles in the middle, and goes back to the left edge under
        // the cream (6.4 – 7.8), so the window it drains back to is the one the loop starts with.
        if t < 4.4 {
            s.beam = -0.15 + 1.1 * cross
        } else if t < 6.4 {
            s.beam = 0.95 - 0.45 * easeInOut(seg(t, 4.4, 5.4))
        } else {
            s.beam = 0.5 - 0.65 * easeInOut(seg(t, 6.4, 7.8))
        }
        s.keyMix = 1
        s.flood = easeInOut(seg(t, 5.6, 6.4)) * (1 - easeInOut(seg(t, 7.8, 8.8)))
        // The home's content arrives with the cream, not after it.
        s.home = easeOut(seg(t, 5.8, 6.25))
        s.homeOut = easeInOut(seg(t, 8.0, 8.5))
        let lit = clamp01((s.hi - s.lo) / 1.3)
        s.mark = t < 6.4 ? 0.55 + 0.45 * lit : 0.55 * easeOut(seg(t, 8.4, 9.0))
        return s
    }
}

struct StainedGlassSplashScene {
    var lo = StainedGlassSplashMotion.dark
    var hi = StainedGlassSplashMotion.dark
    var beam = -0.2
    var keyMix = 1.0
    var flood = 0.0
    /// The home screen: 0 hidden … 1 shown, and its exit (0 … 1).
    var home = 0.0
    var homeOut = 0.0
    /// The wordmark's opacity.
    var mark = 0.55

    static func blend(_ a: Self, _ b: Self, _ e: Double) -> Self {
        func m(_ x: Double, _ y: Double) -> Double { x + (y - x) * e }
        return Self(
            lo: m(a.lo, b.lo), hi: m(a.hi, b.hi), beam: m(a.beam, b.beam), keyMix: m(a.keyMix, b.keyMix),
            flood: m(a.flood, b.flood), home: m(a.home, b.home), homeOut: m(a.homeOut, b.homeOut), mark: m(a.mark, b.mark)
        )
    }
}

struct StainedGlassSplashLayout {
    let cx: Double
    let cy: Double
    let R: Double

    init(width: Double, height: Double, insetTop: Double) {
        R = min(width * 0.42, height * 0.2)
        cx = width / 2
        cy = insetTop + 70 + R
    }
}

/// What one frame draws: the scene and the loop's phase (0 … 1) for the dust.
struct StainedGlassSplashFrame {
    var scene: StainedGlassSplashScene
    var phase: Double
}

/// The loop clock, the finger's sun and the hand-off between them, stepped once a frame.
@MainActor
final class StainedGlassSplashEngine {
    private typealias M = StainedGlassSplashMotion
    private enum Mode { case auto, user, back }

    private static let idle = 2.0
    private static let returnFor = 0.9
    /// Where the loop picks up after a finger has lit every pane: the floor's peak.
    private static let resumeLit = 4.4

    private var mode = Mode.auto
    private var clock = 0.0
    private var now = 0.0
    private var lastTouch = 0.0
    private var holding = false
    private var returnAt = 0.0
    private var scene = StainedGlassSplashScene()
    private var from = StainedGlassSplashScene()
    // The finger's sun: lo, hi, beam, keyMix; and whether the full window was lit once.
    private var hand = (StainedGlassSplashMotion.dark, StainedGlassSplashMotion.dark, -0.2, 1.0)
    private var full = false
    private var last: Date?
    private var layout: StainedGlassSplashLayout?
    /// Whether a finger is on the screen (the drag gesture has begun and not ended).
    var fingerDown = false
    private let select = UISelectionFeedbackGenerator()

    func step(to date: Date, layout L: StainedGlassSplashLayout, auto: Bool, reduced: Bool) -> StainedGlassSplashFrame {
        layout = L
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        now += dt
        let at = now
        var s: StainedGlassSplashScene
        if reduced {
            s = M.loopScene(7.0)
        } else {
            switch mode {
            case .auto:
                let t = auto ? (clock + dt).truncatingRemainder(dividingBy: StainedGlassSplash.loop) : 0
                clock = t
                s = M.loopScene(t)
            case .user:
                s = StainedGlassSplashScene()
                // The key eases from the loop's order to plain left to right (0.3 s).
                let km = max(0, hand.3 - dt / 0.3)
                hand.3 = km
                s.lo = hand.0
                s.hi = hand.1
                s.beam = hand.2
                s.keyMix = km
                s.mark = 0.55 + 0.45 * min(1, max(0, (hand.1 - hand.0) / 1.0))
                if !holding && full {
                    // The whole window is lit: hand back to the loop at the floor's peak.
                    from = s
                    returnAt = at
                    clock = Self.resumeLit
                    mode = .back
                } else if !holding && at - lastTouch > Self.idle {
                    from = s
                    returnAt = at
                    clock = 0
                    mode = .back
                }
            case .back:
                // Ease into the loop's frame at the clock it will resume from.
                let e = M.easeInOut((at - returnAt) / Self.returnFor)
                s = StainedGlassSplashScene.blend(from, M.loopScene(clock), e)
                if e >= 1 { mode = .auto }
            }
        }
        scene = s
        return StainedGlassSplashFrame(scene: s, phase: clock / StainedGlassSplash.loop)
    }

    /// The finger's x as a place across the window (0 left edge … 1 right edge).
    private func across(_ x: Double, _ L: StainedGlassSplashLayout) -> Double {
        (x - (L.cx - L.R)) / (2 * L.R)
    }

    private func spread(_ x: Double, _ L: StainedGlassSplashLayout) {
        let b = across(x, L)
        let lo = min(hand.0, b - 0.06)
        let hi = max(hand.1, b + 0.06)
        hand = (lo, hi, b, hand.3)
        if !full && lo < 0.04 && hi > 0.96 {
            full = true
            select.selectionChanged()
        }
    }

    func touchBegan(_ p: CGPoint) {
        // The home screen and the flood do not take the finger.
        guard let L = layout, scene.flood <= 0.05 else { return }
        holding = true
        lastTouch = now
        if mode != .user {
            let s = scene
            let b = across(p.x, L)
            // A lit range carries over in plain left-to-right keys; a dark window starts at the finger.
            let lit = s.hi - s.lo > 0.05
            hand = lit ? (max(-0.05, s.lo), min(1.05, s.hi), b, s.keyMix) : (b - 0.06, b + 0.06, b, s.keyMix)
            full = false
            mode = .user
        }
        spread(p.x, L)
    }

    func touchMoved(_ p: CGPoint) {
        guard let L = layout, mode == .user, holding else { return }
        spread(p.x, L)
        lastTouch = now
    }

    func touchEnded() {
        holding = false
        lastTouch = now
    }
}
