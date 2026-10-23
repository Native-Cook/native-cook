import SwiftUI

// The wave: the edge of the next page, which waits behind the right side of the screen as a bump
// and is pulled across it. Everything here is a function of the time, so that a frame can be
// drawn from what was last decided (a grab, a release) without anything being stored per frame.

enum LiquidSwipePagesMotion {
    /// autoPlay: one page every `period` seconds; with three pages the screen is back at its start after `loop`.
    static let period = 3.2
    static let loop = 9.6
    // Within a period: the bump rests, a finger that is not there drags it out, lets go, and the
    // next page settles for `lead` seconds before its own period starts.
    static let dragAt = 0.7
    static let dragFor = 0.9
    static let release = 0.8
    static let lead = period - dragAt - dragFor - release
    /// After a touch autoPlay waits this long before it goes back to the first page and starts over.
    static let userHold = 3.0
    /// What moves by itself (the bump's breath, the artwork) repeats on this period, which divides `period`.
    static let ambient = 1.6

    // The strip of the next page along the right edge, and the bump it swells into: how far the tip
    // reaches into the page, half its height, how much deeper it breathes, and where it sits.
    static let strip = 8.0
    static let bump = 46.0
    static let bumpSpread = 95.0
    static let breath = 6.0
    static let bumpY = 0.604
    // The breath is gone once the tip has been pulled this far.
    static let breathOut = 40.0
    /// The arrow's centre, measured from the tip.
    static let arrowInset = 20.0
    // While it is dragged the tip stays under the finger, the straight part follows at a third of
    // the finger's speed, and the bump grows taller by this much for each point of travel.
    static let edgeFollow = 0.3
    static let spreadGain = 2.4
    // The shoulders of the bump: how long the curve's handles are at the edge and at the tip, as a
    // share of half the bump's height.
    static let kEdge = 0.45
    static let kTip = 0.45
    // Let go: the straight part reaches the left side in `sweep` seconds, and the bulge springs flat.
    static let sweep = 0.5
    static let settle = (stiffness: 100.0, damping: 12.0)
    static let flatFrom = 0.6
    // Meanwhile the bump shrinks to this share of the screen's height, so that the dent is a bulge too.
    static let dentSpread = 0.42
    static let arrowOut = 0.15
    // The next page's content comes in at the end of the wave.
    static let revealFrom = release - 0.35
    static let revealTo = release - 0.05
    // A new bump grows out of the edge with a spring, the strip and the arrow with it.
    static let grow = (stiffness: 300.0, damping: 20.0)
    static let stripIn = 0.2
    static let arrowFrom = 0.15
    static let arrowTo = 0.45
    // The bump slides to the height of the finger that took it.
    static let grab = 0.18
    // Let go too early: the wave springs back.
    static let back = (stiffness: 260.0, damping: 22.0)
    static let backFor = 0.6
    static let backSlide = 0.3
    /// A release completes when the finger has travelled this share of the width, or flicks left.
    static let completeAt = 0.4
    static let flick = 600.0
    // A wave that plays by itself pulls the tip to this share of the width before it lets go.
    static let autoTip = 0.4
    /// A tap on the arrow drags for this long; Skip is shorter and its release twice as fast.
    static let tapDrag = 0.45
    static let skipDrag = 0.2
    static let skipSpeed = 2.0
    // Reduced motion: the next page fades in over the old one.
    static let fade = 0.3

    static func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

    static func smooth(_ t: Double, _ from: Double, _ to: Double) -> Double {
        let u = clamp01((t - from) / (to - from))
        return u * u * (3 - 2 * u)
    }

    static func easeOut(_ t: Double) -> Double { 1 - (1 - t) * (1 - t) * (1 - t) }

    static func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }

    /// How far a spring of mass 1 has come from 0 to 1 after `t` seconds; it overshoots.
    static func spring(_ t: Double, _ spring: (stiffness: Double, damping: Double)) -> Double {
        guard t > 0 else { return 0 }
        let w = spring.stiffness.squareRoot()
        let z = spring.damping / (2 * w)
        let wd = w * (1 - z * z).squareRoot()
        return 1 - exp(-z * w * t) * (cos(wd * t) + (z * w / wd) * sin(wd * t))
    }
}

/// What was last decided. `travel` and `cy` are the finger's, or where a release or a return started.
struct LiquidSwipePagesWave: Equatable {
    enum Mode { case rest, hand, script, letGo, backing }

    var mode = Mode.rest
    var page = 0
    var target = 0
    /// When the mode began, in seconds on the screen's clock.
    var t0 = 0.0
    var dragFor = 0.0
    var speed = 1.0
    var travel = 0.0
    var cy = 0.0
    var cyFrom = 0.0

    private typealias M = LiquidSwipePagesMotion

    static func resting(_ page: Int, since: Double) -> LiquidSwipePagesWave {
        LiquidSwipePagesWave(mode: .rest, page: page, target: page, t0: since)
    }

    /// A wave that plays by itself from `at`: dragged out for `dragFor` seconds, then let go.
    static func script(page: Int, target: Int, at: Double, dragFor: Double, speed: Double) -> LiquidSwipePagesWave {
        LiquidSwipePagesWave(mode: .script, page: page, target: target, t0: at, dragFor: dragFor, speed: speed)
    }

    /// When what was started has run out, if it does by itself.
    var end: Double? {
        switch mode {
        case .script: t0 + dragFor + M.release / speed
        case .letGo: t0 + M.release / speed
        case .backing: t0 + M.backFor
        default: nil
        }
    }

    /// The wave once what was started has run out: a page that arrived, or a bump that is back.
    func settled(at now: Double) -> LiquidSwipePagesWave {
        guard let end, now >= end else { return self }
        return mode == .backing ? .resting(page, since: end - M.lead) : .resting(target, since: end)
    }

    /// Where the finger is, real or not: how far it has pulled the tip, and at which height.
    func held(at now: Double, size: CGSize, still: Bool) -> (travel: Double, cy: Double) {
        let restY = size.height * M.bumpY
        switch mode {
        case .hand:
            return (travel, cyFrom + (cy - cyFrom) * M.smooth(now - t0, 0, M.grab))
        case .backing:
            return (travel * (1 - M.spring(now - t0, M.back)), cy + (restY - cy) * M.smooth(now - t0, 0, M.backSlide))
        case .script:
            let full = still ? 0 : size.width * (1 - M.autoTip) - M.strip - M.bump
            return (full * M.easeInOut(M.clamp01((now - t0) / dragFor)), restY)
        case .letGo:
            return (travel, cy)
        case .rest:
            return (0, restY)
        }
    }

    func resolve(at now: Double, size: CGSize, count: Int, still: Bool) -> LiquidSwipePagesShape {
        let w = settled(at: now)
        let breath = still ? 0 : M.breath * (0.5 - 0.5 * cos(2 * .pi * now / M.ambient))
        if w.mode == .rest {
            let tau = now - w.t0
            return LiquidSwipePagesShape(
                page: w.page, incoming: -1, tint: w.page, mix: 0,
                reach: M.strip * (still ? 1 : M.clamp01(tau / M.stripIn)),
                depth: (M.bump + breath) * (still ? 1 : M.spring(tau, M.grow)),
                cy: size.height * M.bumpY, spread: M.bumpSpread,
                arrow: M.smooth(tau, M.arrowFrom, M.arrowTo), reveal: 0, fade: 0
            )
        }
        let (travel, cy) = w.held(at: now, size: size, still: still)
        let reach = M.strip + M.edgeFollow * travel
        let depth = M.bump + breath * (1 - M.clamp01(travel / M.breathOut)) + (1 - M.edgeFollow) * travel
        let spread = M.bumpSpread + M.spreadGain * travel
        // Seconds since the wave was let go; negative while it is held.
        let u = w.mode == .letGo ? (now - w.t0) * w.speed : w.mode == .script ? (now - w.t0 - w.dragFor) * w.speed : -1
        if u < 0 {
            let mix = w.mode == .script ? M.clamp01((now - w.t0) / w.dragFor) : 0
            return LiquidSwipePagesShape(
                page: w.page, incoming: -1, tint: w.target, mix: mix, reach: reach, depth: depth, cy: cy, spread: spread, arrow: 1, reveal: 0, fade: 0
            )
        }
        if still {
            let fade = M.smooth(u, 0, M.fade)
            return LiquidSwipePagesShape(
                page: w.page, incoming: w.target, tint: w.target, mix: 1, reach: reach, depth: depth, cy: cy, spread: spread,
                arrow: 1 - fade, reveal: fade, fade: fade
            )
        }
        let sweep = M.easeOut(M.clamp01(u / M.sweep))
        return LiquidSwipePagesShape(
            page: w.page, incoming: w.target, tint: w.target, mix: 1,
            reach: reach + (size.width - reach) * sweep,
            // The bulge overshoots into a dent, a last sliver of the old page at the left side.
            depth: depth * (1 - M.spring(u, M.settle)) * (1 - M.smooth(u, M.flatFrom, M.release)),
            cy: cy,
            spread: spread + (min(spread, size.height * M.dentSpread) - spread) * sweep,
            arrow: 1 - M.clamp01(u / M.arrowOut),
            reveal: M.smooth(u, M.revealFrom, M.revealTo), fade: 0
        )
    }
}

/// One frame: which pages show, and the wave between them.
struct LiquidSwipePagesShape {
    var page: Int
    /// The page coming in over the wave, or -1.
    var incoming: Int
    /// The wave takes the colour of the page after `page`, blended by `mix` into that of `tint`.
    var tint: Int
    var mix: Double
    var reach: Double
    var depth: Double
    var cy: Double
    var spread: Double
    var arrow: Double
    var reveal: Double
    var fade: Double

    /// The next page's side of the wave as one closed path: recomputed for every frame.
    func outline(in size: CGSize) -> Path {
        let m = LiquidSwipePagesMotion.self
        let ex = size.width - reach
        let tx = ex - depth
        let top = cy - spread
        let bottom = cy + spread
        let above = min(top, -1)
        let below = max(bottom, size.height + 1)
        var path = Path()
        path.move(to: CGPoint(x: size.width + 1, y: above))
        path.addLine(to: CGPoint(x: ex, y: above))
        path.addLine(to: CGPoint(x: ex, y: top))
        path.addCurve(
            to: CGPoint(x: tx, y: cy),
            control1: CGPoint(x: ex, y: top + spread * m.kEdge), control2: CGPoint(x: tx, y: cy - spread * m.kTip)
        )
        path.addCurve(
            to: CGPoint(x: ex, y: bottom),
            control1: CGPoint(x: tx, y: cy + spread * m.kTip), control2: CGPoint(x: ex, y: bottom - spread * m.kEdge)
        )
        path.addLine(to: CGPoint(x: ex, y: below))
        path.addLine(to: CGPoint(x: size.width + 1, y: below))
        path.closeSubpath()
        return path
    }
}
