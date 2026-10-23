import SwiftUI

enum SwipeDeckToolbarBandMetrics {
    static let band = Color(hex: 0x070909)
    /// The band above the bottom inset, and the least the inset counts for.
    static let height = 62.0
    static let inset = 34.0
    /// The icons' centres: under the band's top edge, and the first and last from the screen's sides.
    static let iconY = 40.0
    static let iconInset = 52.0
    static let topRadius = 40.0
    /// The valley starts this far inside the end icons, takes `slope` to reach its floor and is `depth` deep.
    static let shoulder = 40.0
    static let slope = 52.0
    static let depth = 74.0
    static let ring = 58.0
    /// A ring in flight is this much wider, and its outline this much thinner.
    static let ringStretch = 0.5
    static let ringThin = 0.4
    static let capsuleWidth = 136.0
    static let capsuleHeight = 42.0
    /// The cross and the heart from the capsule's ends.
    static let markInset = 29.0
    /// The side of the capsule a verdict lights.
    static let pillWidth = 54.0
    static let pillHeight = 38.0
    static let markerWidth = 30.0
    static let markerHeight = 20.0
    static let markerSlide = 6.0
    static let glow = 16.0
    static let stroke = 1.6
    static let crossStroke = 2.0
    static let clock = 8.5
    static let cross = 5.0
    /// The heart of the capsule next to the heart of the tab, and how much a verdict's mark grows.
    static let heartSmall = 0.83
    static let grow = 0.15
    static let flash = 0.06

    static func iconX(_ index: Double, width: Double) -> Double {
        iconInset + index * (width - iconInset * 2) / 3
    }

    static func smooth(_ x: Double, _ from: Double, _ to: Double) -> Double {
        let t = min(1, max(0, (x - from) / (to - from)))
        return t * t * (3 - 2 * t)
    }
}

private typealias B = SwipeDeckToolbarBandMetrics

/// The bottom band of the swipe deck: four line icons and a ring on the current one. On the deck's
/// tab its top edge sinks into a valley, and the two middle icons meet in a glass capsule as the
/// cross and the heart that judge a card.
struct SwipeDeckToolbarBand: View {
    /// 0 flat, 1 with the valley open; a spring may carry it a little past either.
    var valley: Double
    /// Where the ring is, in tabs, and the tabs it left and is going to.
    var ring: Double
    var ringFrom: Double
    var ringTo: Double
    /// How far the top card is from its place, and whether it is flying off: 0 to 1, toward `side`.
    var cardX: Double
    var fly: Double
    var side: Double
    /// The pull that lights a side fully.
    var full: Double
    var accent: Color
    var likeColor: Color
    var rejectColor: Color
    /// Reduced motion: the two states of the band cross-fade, and verdicts only change colour.
    var still: Bool

    /// A heart 16.8 points wide around its own centre.
    private static let heart: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 0, y: 8.2))
        path.addCurve(to: CGPoint(x: -8.4, y: -2.6), control1: CGPoint(x: 0, y: 8.2), control2: CGPoint(x: -8.4, y: 3.4))
        path.addCurve(to: CGPoint(x: -3.7, y: -7.4), control1: CGPoint(x: -8.4, y: -5.4), control2: CGPoint(x: -6.2, y: -7.4))
        path.addCurve(to: CGPoint(x: 0, y: -5.2), control1: CGPoint(x: -2.1, y: -7.4), control2: CGPoint(x: -0.7, y: -6.5))
        path.addCurve(to: CGPoint(x: 3.7, y: -7.4), control1: CGPoint(x: 0.7, y: -6.5), control2: CGPoint(x: 2.1, y: -7.4))
        path.addCurve(to: CGPoint(x: 8.4, y: -2.6), control1: CGPoint(x: 6.2, y: -7.4), control2: CGPoint(x: 8.4, y: -5.4))
        path.addCurve(to: CGPoint(x: 0, y: 8.2), control1: CGPoint(x: 8.4, y: 3.4), control2: CGPoint(x: 0, y: 8.2))
        path.closeSubpath()
        return path
    }()

    /// The band: a sheet with round top corners whose top edge sinks `depth` between its shoulders.
    private static func band(width: Double, height: Double, depth: Double) -> Path {
        let from = B.iconInset + B.shoulder
        let to = width - from
        let half = B.slope / 2
        var path = Path()
        path.move(to: CGPoint(x: 0, y: height))
        path.addLine(to: CGPoint(x: 0, y: B.topRadius))
        path.addQuadCurve(to: CGPoint(x: B.topRadius, y: 0), control: .zero)
        path.addLine(to: CGPoint(x: from, y: 0))
        path.addCurve(to: CGPoint(x: from + B.slope, y: depth), control1: CGPoint(x: from + half, y: 0), control2: CGPoint(x: from + half, y: depth))
        path.addLine(to: CGPoint(x: to - B.slope, y: depth))
        path.addCurve(to: CGPoint(x: to, y: 0), control1: CGPoint(x: to - half, y: depth), control2: CGPoint(x: to - half, y: 0))
        path.addLine(to: CGPoint(x: width - B.topRadius, y: 0))
        path.addQuadCurve(to: CGPoint(x: width, y: B.topRadius), control: CGPoint(x: width, y: 0))
        path.addLine(to: CGPoint(x: width, y: height))
        path.closeSubpath()
        return path
    }

    var body: some View {
        Canvas { context, size in
            let width = size.width
            let open = min(1, max(0, valley))
            let capsule = CGRect(x: width / 2 - B.capsuleWidth / 2, y: B.iconY - B.capsuleHeight / 2, width: B.capsuleWidth, height: B.capsuleHeight)

            context.fill(Self.band(width: width, height: size.height, depth: still ? B.depth : B.depth * valley), with: .color(B.band))
            if still {
                context.fill(Self.band(width: width, height: size.height, depth: 0), with: .color(B.band.opacity(1 - open)))
            }

            // In flight the ring is longer along its way and its outline thinner.
            let span = ringTo - ringFrom
            let flight = still || span == 0 ? 0 : sin(.pi * min(1, max(0, (ring - ringFrom) / span)))
            let ringX = B.iconX(ring, width: width)
            let ringWidth = B.ring * (1 + B.ringStretch * flight)
            let ringPath = Path(roundedRect: CGRect(x: ringX - ringWidth / 2, y: B.iconY - B.ring / 2, width: ringWidth, height: B.ring), cornerRadius: B.ring / 2)
            context.fill(ringPath, with: .color(.white.opacity(0.04)))
            context.stroke(
                ringPath,
                with: .linearGradient(
                    Gradient(colors: [.white.opacity(0.35), accent.opacity(0.6)]),
                    startPoint: CGPoint(x: ringX - B.ring / 2, y: B.iconY - B.ring / 2),
                    endPoint: CGPoint(x: ringX + B.ring / 2, y: B.iconY + B.ring / 2)
                ),
                lineWidth: 1 - B.ringThin * flight
            )

            // A person: a head over a rounded body.
            var person = context
            person.translateBy(x: B.iconX(0, width: width), y: B.iconY)
            person.stroke(Path(ellipseIn: CGRect(x: -3.6, y: -7.8, width: 7.2, height: 7.2)), with: .color(.white), lineWidth: B.stroke)
            person.stroke(Path(roundedRect: CGRect(x: -6.5, y: 2.3, width: 13, height: 6.4), cornerRadius: 3.2), with: .color(.white), lineWidth: B.stroke)

            // Two tilted cards, the upper one in the accent colour.
            let card = Path(roundedRect: CGRect(x: -4.5, y: -6, width: 9, height: 12), cornerRadius: 2.5)
            var back = context
            back.translateBy(x: B.iconX(3, width: width) - 1.6, y: B.iconY + 1.2)
            back.rotate(by: .radians(-0.17))
            back.stroke(card, with: .color(.white), lineWidth: B.stroke)
            var front = context
            front.translateBy(x: B.iconX(3, width: width) + 2.4, y: B.iconY - 1.2)
            front.rotate(by: .radians(0.25))
            front.fill(card, with: .color(B.band))
            front.stroke(card, with: .color(accent), lineWidth: B.stroke)

            // A verdict grows with the card's pull; a card that flies off lights its side fully, then fades.
            func strength(_ to: Double) -> Double {
                let pulled = min(1, max(0, to * cardX / full))
                let flown = fly > 0 && side == to ? B.smooth(fly, 0, 0.25) : 0
                return max(pulled, flown) * (1 - B.smooth(fly, 0.5, 1)) * open
            }
            let reject = strength(-1)
            let like = strength(1)

            let glass = still ? open : B.smooth(open, 0.3, 1)
            var glassy = context
            glassy.opacity = glass
            let capsulePath = Path(roundedRect: capsule, cornerRadius: B.capsuleHeight / 2)
            glassy.fill(capsulePath, with: .color(Color(hex: 0x0E2233, opacity: 0.7)))
            glassy.stroke(capsulePath, with: .color(.white.opacity(0.12)), lineWidth: 1)
            pill(in: context, x: capsule.minX + 2, color: rejectColor, strength: glass * reject, inward: 1)
            pill(in: context, x: capsule.maxX - 2 - B.pillWidth, color: likeColor, strength: glass * like, inward: -1)
            var marker = context
            marker.opacity = glass * (still ? 1 : B.smooth(open, 0.7, 1)) * (1 - max(reject, like))
            let markerX = width / 2 - B.markerWidth / 2 + (still ? 0 : B.markerSlide * (like - reject))
            marker.stroke(
                Path(roundedRect: CGRect(x: markerX, y: B.iconY - B.markerHeight / 2, width: B.markerWidth, height: B.markerHeight), cornerRadius: B.markerHeight / 2),
                with: .color(.white.opacity(0.25)), lineWidth: 1
            )

            if still {
                middle(in: context, morph: 0, opacity: 1 - open, width: width, reject: reject, like: like)
                middle(in: context, morph: 1, opacity: open, width: width, reject: reject, like: like)
            } else {
                middle(in: context, morph: open, opacity: 1, width: width, reject: reject, like: like)
            }
        }
    }

    /// One side of the capsule lit by a verdict: its colour from the end toward the middle, and a glow.
    private func pill(in context: GraphicsContext, x: Double, color: Color, strength: Double, inward: Double) {
        guard strength > 0 else { return }
        let box = CGRect(x: x, y: B.iconY - B.pillHeight / 2, width: B.pillWidth, height: B.pillHeight)
        let path = Path(roundedRect: box, cornerRadius: B.pillHeight / 2)
        var lit = still ? context : context.scaled(1 + B.flash * sin(.pi * fly), about: CGPoint(x: box.midX, y: box.midY))
        lit.opacity = strength
        if !still {
            var glow = lit
            glow.addFilter(.blur(radius: B.glow))
            glow.fill(path, with: .color(color.opacity(0.6)))
        }
        let outer = inward > 0 ? box.minX : box.maxX
        lit.fill(
            path,
            with: .linearGradient(
                Gradient(colors: [color, color.opacity(0.45)]),
                startPoint: CGPoint(x: outer, y: 0), endPoint: CGPoint(x: outer + inward * B.pillWidth, y: 0)
            )
        )
        lit.stroke(path, with: .color(.white.opacity(0.2)), lineWidth: 1)
    }

    /// The two middle tabs on their way to the capsule: at 0 a clock and an outlined heart, at 1 the
    /// cross its hands have opened into and the same heart, smaller and solid.
    private func middle(in context: GraphicsContext, morph m: Double, opacity: Double, width: Double, reject: Double, like: Double) {
        guard opacity > 0 else { return }
        let crossX = width / 2 - B.capsuleWidth / 2 + B.markInset
        let heartX = width / 2 + B.capsuleWidth / 2 - B.markInset
        let clockX = B.iconX(1, width: width)
        let tabHeartX = B.iconX(2, width: width)

        var clock = context
        clock.opacity = opacity
        clock.translateBy(x: clockX + (crossX - clockX) * m, y: B.iconY)
        let grown = still ? 1 : 1 + B.grow * reject
        clock.scaleBy(x: grown, y: grown)
        var face = clock
        face.opacity = opacity * (1 - B.smooth(m, 0, 0.6))
        let radius = B.clock * (1 - 0.6 * m)
        face.stroke(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .color(.white), lineWidth: B.stroke)
        // The hands leave the centre and become the two strokes of the cross.
        var hands = Path()
        hands.move(to: CGPoint(x: -B.cross * m, y: B.cross * m))
        hands.addLine(to: CGPoint(x: B.cross * m, y: -4.5 - (B.cross - 4.5) * m))
        hands.move(to: CGPoint(x: -B.cross * m, y: -B.cross * m))
        hands.addLine(to: CGPoint(x: 3 + (B.cross - 3) * m, y: 1.8 + (B.cross - 1.8) * m))
        clock.stroke(hands, with: .color(.white), style: StrokeStyle(lineWidth: B.stroke + (B.crossStroke - B.stroke) * m, lineCap: .round))

        var heart = context
        heart.opacity = opacity
        heart.translateBy(x: tabHeartX + (heartX - tabHeartX) * m, y: B.iconY)
        let scale = (1 - (1 - B.heartSmall) * m) * (still ? 1 : 1 + B.grow * like)
        heart.scaleBy(x: scale, y: scale)
        if !still, like > 0 {
            var halo = heart
            halo.opacity = opacity * 0.4 * like
            halo.addFilter(.blur(radius: 8))
            halo.fill(Path(ellipseIn: CGRect(x: -13, y: -13, width: 26, height: 26)), with: .color(.white))
        }
        var solid = heart
        solid.opacity = opacity * m
        solid.fill(Self.heart, with: .color(.white))
        heart.stroke(Self.heart, with: .color(.white), style: StrokeStyle(lineWidth: B.stroke, lineJoin: .round))
    }
}
