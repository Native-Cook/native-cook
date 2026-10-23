import CoreGraphics

// Sizes and the loop arithmetic the picker shares. Mirrors ballPickerGeometry.ts and the layout
// constants at the top of index.tsx.

enum ChooseYourBallGeometry {
    /// Finger travel that moves the selection by one ball. Long: the gesture is a page turn.
    static let itemStride: Double = 128
    /// One gesture moves the loop by at most this many balls; the drag resists past it.
    static let panReach: Double = 1
    /// Coefficient of the resistance past `panReach`: the excess never exceeds 1/6 of a ball.
    static let panResist: Double = 6
    /// Seconds of release velocity that carry into the landing slot.
    static let flingProjection: Double = 0.18
    /// The snap: settles in about 0.44 s with one small overshoot.
    static let snapSpring = ChooseYourBallSpring(mass: 1, stiffness: 130, damping: 18)

    /// The ball's stage is a square wider than the screen; the sphere fills ~74% of it.
    static let stageWidthFraction: CGFloat = 1.06
    static let stageMax: CGFloat = 460
    /// Where the ball's centre sits, as a fraction of the screen height.
    static let ballCentreFraction: CGFloat = 0.47
    /// Screens shorter than this get a smaller ball and wordmark.
    static let compactHeight: CGFloat = 740

    static let wordmarkFraction: CGFloat = 0.2
    static let wordmarkMax: CGFloat = 82

    static func stageSize(width: CGFloat, compact: Bool) -> CGFloat {
        min(stageMax, width * stageWidthFraction) * (compact ? 0.86 : 1)
    }

    static func wordmarkSize(width: CGFloat, compact: Bool) -> CGFloat {
        min(wordmarkMax, width * wordmarkFraction) * (compact ? 0.88 : 1)
    }

    /// Rounds to the nearest slot with ties going up, as JavaScript's Math.round does in the
    /// reference: -0.5 is slot 0, not -1.
    static func nearestSlot(_ offset: Double) -> Double {
        (offset + 0.5).rounded(.down)
    }

    /// `slot` folded onto the loop: the catalog has no first ball and no last one.
    static func wrapIndex(_ slot: Int, total: Int) -> Int {
        guard total > 0 else { return 0 }
        return ((slot % total) + total) % total
    }

    /// Signed distance from `offset` to `index` in slots, the short way round: [-total/2, total/2).
    static func cyclicDelta(index: Double, offset: Double, total: Int) -> Double {
        guard total > 0 else { return 0 }
        let count = Double(total)
        var delta = (index - offset).truncatingRemainder(dividingBy: count)
        if delta < 0 { delta += count }
        if delta >= count / 2 { delta -= count }
        return delta
    }
}
