import SwiftUI

// The pill is one drop of ink. Opening it, each edge travels on its own share of the morph, so the
// edge in front leaves first and the one behind catches up: the top rises before the bottom, the
// right edge runs out before the left, and the pill stretches like a drop pulled across glass.
// Each pair is (start, length) as shares of the morph (0…1). Played backwards for closing, the same
// shares make the bottom and the left lead the way down.
enum LiquidSearchMorphInk {
    static let top = (0.0, 0.8)
    static let bottom = (0.14, 0.86)
    static let left = (0.1, 0.8)
    static let right = (0.0, 0.75)
    static let rest = (0.07, 0.83)
    /// A drop keeps its volume: for every point it is drawn out taller than it would be, it gets
    /// this much narrower on each side.
    static let narrowing = 0.4
    /// Two smaller drops ride inside it, the magnifier's drop and a little one behind it, and lag
    /// further: they part from the pill on a thread of ink and merge into it again.
    static let drop = (0.3, 0.7)
    static let tail = (0.44, 0.56)
    static let dropRadius = 17.0
    static let tailRadius = 8.0
    /// Where the magnifier sits, from the pill's left edge.
    static let iconInset = 25.0
    /// The goo: everything is blurred by `gooBlur`, then its alpha is pushed through a steep ramp
    /// (alpha × gooGain − gooCut), so shapes that come close melt into one with a crisp edge.
    static let gooBlur = 9.0
    static let gooGain: Float = 22
    static let gooCut: Float = 10
    /// A press pinches the pill in by this much all round.
    static let pressInset = 3.0
}

/// The pill's edges and its two drops at one point of the morph.
struct LiquidSearchMorphShape {
    var left: Double
    var top: Double
    var right: Double
    var bottom: Double
    var dropX: Double
    var dropY: Double
    var tailX: Double
    var tailY: Double

    private typealias I = LiquidSearchMorphInk

    private static func easeInOut(_ t: Double) -> Double {
        t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2
    }

    /// How far an edge with this share has moved; under reduced motion it jumps at the middle.
    private static func edge(_ t: Double, _ share: (Double, Double), _ still: Bool) -> Double {
        if still { return t < 0.5 ? 0 : 1 }
        return easeInOut(min(max((t - share.0) / share.1, 0), 1))
    }

    private static func mix(_ a: Double, _ b: Double, _ k: Double) -> Double { a + (b - a) * k }

    /// The shape at `t` of the morph from `closed` (0) to `open` (1).
    init(t: Double, closed: CGRect, open: CGRect, still: Bool) {
        typealias S = LiquidSearchMorphShape
        top = S.mix(closed.minY, open.minY, S.edge(t, I.top, still))
        bottom = S.mix(closed.maxY, open.maxY, S.edge(t, I.bottom, still))
        let rest = S.mix(closed.height, open.height, S.edge(t, I.rest, still))
        let stretch = max(0, bottom - top - rest) * I.narrowing
        left = S.mix(closed.minX, open.minX, S.edge(t, I.left, still)) + stretch
        right = S.mix(closed.maxX, open.maxX, S.edge(t, I.right, still)) - stretch
        let drop = S.edge(t, I.drop, still)
        let tail = S.edge(t, I.tail, still)
        dropX = S.mix(closed.minX + I.iconInset, open.minX + I.iconInset, drop)
        dropY = S.mix(closed.midY, open.midY, drop)
        tailX = S.mix(closed.minX + I.iconInset + 14, open.minX + I.iconInset + 14, tail)
        tailY = S.mix(closed.midY, open.midY, tail)
    }
}

/// The ink pill, drawn through the goo filter.
struct LiquidSearchMorphPill: View {
    let t: Double
    let press: Double
    let closed: CGRect
    let open: CGRect
    let still: Bool
    let color: Color

    private typealias I = LiquidSearchMorphInk

    var body: some View {
        // The pieces are drawn plain, then the whole layer is blurred and pushed through the alpha
        // ramp at full resolution (LiquidSearchMorphGoo.metal).
        Canvas { context, _ in
            let p = LiquidSearchMorphShape(t: t, closed: closed, open: open, still: still)
            let inset = I.pressInset * press
            let rect = CGRect(x: p.left + inset, y: p.top + inset, width: p.right - p.left - inset * 2, height: p.bottom - p.top - inset * 2)
            guard rect.width > 0, rect.height > 0 else { return }
            // Always fully round, so a drawn-out pill reads as a drop, never as a box.
            let radius = min(rect.width, rect.height) / 2
            context.fill(Path(roundedRect: rect, cornerRadius: radius, style: .circular), with: .color(.white))
            context.fill(Path(ellipseIn: CGRect(x: p.dropX - I.dropRadius, y: p.dropY - I.dropRadius, width: I.dropRadius * 2, height: I.dropRadius * 2)), with: .color(.white))
            context.fill(Path(ellipseIn: CGRect(x: p.tailX - I.tailRadius, y: p.tailY - I.tailRadius, width: I.tailRadius * 2, height: I.tailRadius * 2)), with: .color(.white))
        }
        .layerEffect(
            ShaderLibrary.liquidSearchMorphBlurAcross(.float(I.gooBlur)),
            maxSampleOffset: CGSize(width: ceil(I.gooBlur * 3), height: 0)
        )
        .layerEffect(
            ShaderLibrary.liquidSearchMorphGooDown(.float(I.gooBlur), .color(color), .float(I.gooGain), .float(I.gooCut)),
            maxSampleOffset: CGSize(width: 0, height: ceil(I.gooBlur * 3))
        )
        // Under reduced motion the pill fades out at the jump and back in after it.
        .opacity(still ? abs(1 - 2 * t) : 1)
        .allowsHitTesting(false)
    }
}

// MARK: - Motion

/// A number moving to a target on a curve, sampled against the timeline's clock; it can queue one
/// more leg (`then`) that starts when the first lands, like Reanimated's `withSequence`.
struct LiquidSearchMorphTween {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var duration = 0.0
    private var curve = UnitCurve.linear
    private var then: (to: Double, duration: Double, curve: UnitCurve)?

    init(_ value: Double) {
        from = value
        to = value
    }

    /// Reanimated's default timing curve, `Easing.inOut(Easing.quad)`.
    static let inOutQuad = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    var end: Date { start.addingTimeInterval(duration + (then?.duration ?? 0)) }

    func value(at date: Date) -> Double {
        let elapsed = date.timeIntervalSince(start)
        if let then, elapsed >= duration {
            guard then.duration > 0 else { return then.to }
            let k = min(max((elapsed - duration) / then.duration, 0), 1)
            return to + (then.to - to) * then.curve.value(at: k)
        }
        guard duration > 0 else { return to }
        let k = min(max(elapsed / duration, 0), 1)
        return from + (to - from) * curve.value(at: k)
    }

    mutating func retarget(_ target: Double, at date: Date = .now, duration: Double, curve: UnitCurve = inOutQuad, then: (Double, Double)? = nil) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
        self.curve = curve
        self.then = then.map { (to: $0.0, duration: $0.1, curve: Self.inOutQuad) }
    }
}

/// Reanimated's `withSpring({ duration, dampingRatio })`: the stiffness is solved (mass 1) so the
/// spring's energy falls to Reanimated's threshold after 1.5 × the duration, as it does in RN.
enum LiquidSearchMorphSpring {
    static func animation(duration: Double, dampingRatio: Double) -> Animation {
        let k = stiffness(duration: duration, dampingRatio: dampingRatio)
        return .interpolatingSpring(mass: 1, stiffness: k, damping: 2 * dampingRatio * sqrt(k))
    }

    static func stiffness(duration: Double, dampingRatio zeta: Double) -> Double {
        let threshold = 6e-9
        let settling = duration * 1.5
        func difference(_ k: Double) -> Double {
            let w = sqrt(k) * zeta
            let xt = (1 + w * settling) * exp(-w * settling)
            let vt = xt * -w + w * exp(-w * settling)
            return (0.5 * k * xt * xt + 0.5 * vt * vt) / (0.5 * k) - threshold
        }
        var low = Double.ulpOfOne
        var high = 8e3
        let rising = difference(high) >= difference(low)
        var k = (low + high) / 2
        var left = 100
        while abs(difference(k)) > threshold * 1e-3 && left > 0 {
            left -= 1
            if (difference(k) < 0) == rising { low = k } else { high = k }
            k = (low + high) / 2
        }
        return k.isFinite ? k : 170
    }
}
