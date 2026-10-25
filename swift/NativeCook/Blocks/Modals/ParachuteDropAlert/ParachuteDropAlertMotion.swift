import Foundation

/*
 Parachute Drop Alert — the motion. A frame is the alert card (centre, angle, squash) plus one or
 two canopies (position, size, tilt, how collapsed) and their suspension lines. The loop computes
 them from the timeline; under a finger the component simulates the card and the canopy follows.

 autoPlay (loop = 9 s):
   0.0 – 1.2   rest: the app, no alert.
   1.2 – 4.0   descent from above the screen (decelerating, 1 - (1 - u)^2.2) with a pendulum sway
               (0.17 rad at 0.85 Hz, dying out before touchdown) and a drift; the canopy breathes
               (±3 % at 2.1 Hz) and leans against the swing; the scrim fades in from 3.0.
   4.0         touchdown: the card squashes (6 %, 4 Hz, decaying in 0.1 s).
   4.0 – 4.7   the canopy deflates and drapes down over the card's top, lines going slack.
   4.6 – 5.1   the wind pulls it away to the right, lines trailing.
   5.0 – 6.6   hold: "Track" / "Later".
   6.6 – 7.0   a fresh canopy blooms out of the card's top (ease-out back).
   7.0 – 8.0   the card is lifted up and out (accelerating), the scrim fades.
   8.0 – 9.0   rest. 9.0 is the same frame as 0.0.
 */
enum ParachuteDropAlertMotion {
    static let loop = 9.0
    static let landAt = 4.0
    static let liftAt = 6.6
    static let gores = 7

    struct Geo: Equatable {
        var w: Double
        var h: Double
        /// The card's size and landed centre.
        var cardW: Double
        var cardH: Double
        var landX: Double
        var landY: Double
        /// Canopy size and line length (skirt to card top).
        var canW: Double
        var canH: Double
        var lines: Double
        var top: Double
        var skyH: Double
        var sheetY: Double
        var card1Y: Double
        var card1H: Double
        var sectionY: Double
        var rowsY: Double
        var rows: Int
        var tabY: Double
        /// Button rects in card space (from the card's top left).
        var later: CGRect
        var track: CGRect
    }

    /// A canopy: skirt centre, width, height, tilt (rad), collapse 0..1, attached lines, slack 0..1, alpha.
    struct Canopy {
        var x, y, w, h, tilt, collapse: Double
        var attached: Bool
        var slack, alpha: Double
    }

    struct Pose {
        /// Card centre, angle (rad), squash x / y; visible when shown.
        var cardX: Double
        var cardY: Double
        var cardRot = 0.0
        var sx = 1.0
        var sy = 1.0
        var shown = false
        var scrim = 0.0
        /// Reduced motion fades the whole alert instead of moving it.
        var fade = 1.0
        var canopies: [Canopy] = []
    }

    static func clamp01(_ x: Double) -> Double { x < 0 ? 0 : x > 1 ? 1 : x }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp01((t - a) / (b - a)) }
    static func easeInOutCubic(_ x: Double) -> Double {
        let k = clamp01(x)
        if k < 0.5 { return 4 * k * k * k }
        let m = -2 * k + 2
        return 1 - m * m * m / 2
    }
    static func easeOutBack(_ x: Double, _ c: Double = 1.7) -> Double {
        let k = clamp01(x) - 1
        return 1 + (c + 1) * k * k * k + c * k * k
    }

    /// Card touchdown squash, e seconds after landing.
    static func landSquash(_ e: Double) -> (Double, Double) {
        if e < 0 || e > 0.6 { return (1, 1) }
        let k = exp(-e / 0.1) * cos(2 * .pi * 4 * e)
        return (1 + 0.035 * k, 1 - 0.06 * k)
    }

    /// The canopy a card of centre (x, y) and angle rot hangs from, fully open.
    static func hangingCanopy(_ g: Geo, _ x: Double, _ y: Double, _ rot: Double, _ breath: Double, _ scale: Double) -> Canopy {
        let top = g.cardH / 2 + g.lines * scale
        return Canopy(x: x + sin(rot) * top, y: y - cos(rot) * top,
                      w: g.canW * scale * (1 + 0.03 * breath), h: g.canH * scale * (1 - 0.04 * breath),
                      tilt: rot * 0.45, collapse: 0, attached: true, slack: 0, alpha: 1)
    }

    /// The collapse after touchdown, e seconds after it, for a canopy that was at c0.
    static func collapsing(_ g: Geo, _ c0: Canopy, _ cardX: Double, _ cardY: Double, _ e: Double) -> Canopy {
        let k = easeInOutCubic(seg(e, 0, 0.7))
        let away = easeInOutCubic(seg(e, 0.6, 1.1))
        // The hem settles a little below the card's top edge, so the cloth lies over it.
        let drapeY = cardY - g.cardH / 2 + 24
        return Canopy(x: c0.x + (cardX + g.cardW * 0.08 - c0.x) * k + g.w * 1.05 * away,
                      y: c0.y + (drapeY - c0.y) * k - 40 * away,
                      w: c0.w * (1 + 0.12 * k), h: c0.h * (1 - 0.82 * k),
                      tilt: c0.tilt * (1 - k) + 0.32 * away, collapse: k,
                      attached: away < 0.02, slack: k, alpha: 1)
    }

    static func loopPose(_ t: Double, _ g: Geo) -> Pose {
        var pose = Pose(cardX: g.landX, cardY: g.landY)
        let breath = sin(2 * .pi * 2.1 * t)
        pose.scrim = 0.36 * easeInOutCubic(seg(t, 3.0, 4.0)) * (1 - easeInOutCubic(seg(t, 7.0, 7.9)))
        if t < 1.2 || t >= 8.0 { return pose }
        pose.shown = true
        let startY = -(g.cardH / 2 + g.lines + g.canH + 30)
        if t < landAt {
            let u = (t - 1.2) / (landAt - 1.2)
            let fall = 1 - pow(1 - u, 2.2)
            let env = pow(1 - u, 0.9) * seg(u, 0, 0.12)
            let rot = 0.17 * sin(2 * .pi * 0.85 * (t - 1.2)) * env
            pose.cardX = g.landX + 30 * sin(2 * .pi * 0.42 * (t - 1.2)) * pow(1 - u, 1.4)
            pose.cardY = startY + (g.landY - startY) * fall
            pose.cardRot = rot * 0.6
            pose.canopies.append(hangingCanopy(g, pose.cardX, pose.cardY, rot, breath, 1))
            return pose
        }
        let e = t - landAt
        let sq = landSquash(e)
        pose.sx = sq.0
        pose.sy = sq.1
        if e < 1.2 { pose.canopies.append(collapsing(g, hangingCanopy(g, g.landX, g.landY, 0, breath, 1), g.landX, g.landY, e)) }
        if t >= liftAt {
            let bloom = easeOutBack(seg(t, liftAt, liftAt + 0.4), 1.4)
            let up = pow(seg(t, liftAt + 0.4, 8.0), 2.2)
            let total = g.landY + g.cardH / 2 + g.lines + g.canH + 40
            pose.cardY = g.landY - total * up
            let rot = 0.05 * sin(2 * .pi * 1.1 * (t - liftAt)) * seg(t, liftAt + 0.2, 7.2)
            pose.cardRot = rot * 0.6
            var c = hangingCanopy(g, pose.cardX, pose.cardY, rot, breath * bloom, 0.25 + 0.75 * bloom)
            c.alpha = seg(t, liftAt, liftAt + 0.08)
            pose.canopies.append(c)
        }
        return pose
    }
}
