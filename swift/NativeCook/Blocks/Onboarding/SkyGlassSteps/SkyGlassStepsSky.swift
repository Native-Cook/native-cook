import SwiftUI

/// A blue sky with clouds that drift, and the three panes of glass in front of it. Inside a pane
/// the same sky is drawn again as a blur would leave it, then tinted, so the clouds pass behind
/// the cards as slow shifts of light.
enum SkyGlassStepsSky {
    static let cardWidth = 298.0
    static let cardHeight = 452.0
    private static let cardRadius = 34.0

    /// The clouds are placed for a screen this tall and keep their distance from its bottom edge.
    private static let skyHeight = 852.0
    private static let skyStops = [0, 0.55, 1]
    private static let cloudLight = Color.rgba(241, 244, 249, 1)
    private static let cloudShade = Color.rgba(167, 181, 204, 1)
    private static let veil = Color.black.opacity(0.08)
    private static let edge = Color.white.opacity(0.14)

    // How a puff fades from its middle to its rim: full for a third of the way, then a soft edge.
    private static let puffStops = [0, 0.3, 0.55, 0.78, 1]
    private static let puffEdge = [1, 0.96, 0.7, 0.28, 0]
    // Seen through glass a puff is `glassSpread` larger all round, fades from its very middle, and
    // is thinner the smaller it was: what a blur of that reach makes of it, without blurring anything.
    private static let glassStops = [0, 0.25, 0.5, 0.75, 1]
    private static let glassEdge = [1, 0.8, 0.42, 0.13, 0]
    private static let glassSpread = 34.0
    private static let glassGain = 2.4

    private struct Layer {
        let period: Double
        let puffs: [Double]
    }

    /// The sky in three depths. A layer is one strip of clouds, `period` points long, that repeats
    /// across the screen and moves left by its own length in a loop: the far strip is the shortest,
    /// so it is the slowest. A puff is x, y, rx, ry, whether it is shaded (the belly of a cloud), alpha.
    private static let layers = [
        Layer(period: 131, puffs: [
            40, 34, 92, 30, 0, 0.22,
            66, 690, 50, 18, 1, 0.64,
            37, 666, 47, 39, 0, 0.8,
            62, 660, 61, 51, 0, 0.8,
            96, 666, 48, 40, 0, 0.8,
            79, 663, 38, 33, 0, 0.8,
            30, 851, 38, 9, 1, 0.8,
            8, 840, 21, 18, 0, 1,
            23, 839, 23, 19, 0, 1,
            49, 839, 24, 20, 0, 1,
            30, 837, 21, 18, 0, 1,
        ]),
        Layer(period: 196.5, puffs: [
            110, 78, 130, 26, 0, 0.18,
            30, 196, 120, 46, 0, 0.12,
            70, 416, 55, 26, 1, 0.74,
            42, 391, 47, 39, 0, 0.92,
            65, 380, 73, 61, 0, 0.92,
            106, 383, 66, 55, 0, 0.92,
            59, 377, 51, 45, 0, 0.92,
            150, 844, 50, 12, 1, 0.8,
            124, 830, 27, 22, 0, 1,
            145, 829, 28, 24, 0, 1,
            179, 832, 21, 18, 0, 1,
            145, 825, 27, 23, 0, 1,
            36, 356, 28, 23, 0, 0.92,
            70, 330, 36, 30, 0, 0.92,
            108, 346, 30, 25, 0, 0.92,
            84, 364, 26, 20, 1, 0.45,
        ]),
        Layer(period: 262, puffs: [
            150, 490, 75, 57, 1, 0.8,
            105, 426, 122, 102, 0, 1,
            139, 423, 129, 108, 0, 1,
            159, 412, 152, 127, 0, 1,
            193, 423, 128, 107, 0, 1,
            119, 402, 97, 85, 0, 1,
            157, 391, 124, 108, 0, 1,
            30, 776, 75, 27, 1, 0.72,
            -16, 752, 44, 37, 0, 0.9,
            20, 740, 70, 59, 0, 0.9,
            51, 731, 90, 75, 0, 0.9,
            76, 751, 46, 38, 0, 0.9,
            12, 734, 50, 44, 0, 0.9,
            42, 729, 59, 51, 0, 0.9,
            62, 356, 38, 30, 0, 1,
            104, 322, 44, 36, 0, 1,
            150, 298, 50, 42, 0, 1,
            198, 316, 44, 36, 0, 1,
            238, 348, 36, 30, 0, 1,
            128, 340, 34, 28, 1, 0.5,
            182, 352, 30, 24, 1, 0.45,
        ]),
    ]

    /// One colour that fades outwards over a circle of radius 1; a puff is this circle stretched.
    private static func puffGradient(_ color: Color, _ edge: [Double], _ stops: [Double]) -> Gradient {
        Gradient(stops: zip(edge, stops).map { Gradient.Stop(color: color.opacity($0), location: $1) })
    }

    private static let sharpLight = puffGradient(cloudLight, puffEdge, puffStops)
    private static let sharpShade = puffGradient(cloudShade, puffEdge, puffStops)
    private static let glassLight = puffGradient(cloudLight, glassEdge, glassStops)
    private static let glassShade = puffGradient(cloudShade, glassEdge, glassStops)

    /// Draws the clouds that reach into `box`. `turn` is how far through the loop the sky is, 0 to
    /// 1; `top` is where the clouds' own zero is.
    private static func drawClouds(in context: GraphicsContext, glass: Bool, turn: Double, top: Double, box: CGRect) {
        let spread = glass ? glassSpread : 0
        for layer in layers {
            let puffs = layer.puffs
            for i in stride(from: 0, to: puffs.count, by: 6) {
                let rx = puffs[i + 2] + spread
                let ry = puffs[i + 3] + spread
                let y = puffs[i + 1] + top
                if y + ry < box.minY || y - ry > box.maxY { continue }
                let thin = glass ? min(1, glassGain * puffs[i + 2] * puffs[i + 3] / (rx * ry)) : 1
                let shaded = puffs[i + 4] == 1
                var puff = context
                puff.opacity = puffs[i + 5] * thin
                // The first copy that reaches the box, then one every period until they are past it.
                var x = puffs[i] - turn * layer.period
                x += ((box.minX - rx - x) / layer.period).rounded(.up) * layer.period
                while x - rx < box.maxX {
                    puff.fillEllipse(
                        center: CGPoint(x: x, y: y),
                        radii: CGSize(width: rx, height: ry),
                        gradient: glass ? (shaded ? glassShade : glassLight) : (shaded ? sharpShade : sharpLight)
                    )
                    x += layer.period
                }
            }
        }
    }

    /// The whole picture in the block's own points. `turn` is how far through the loop the sky is,
    /// `position` where the carousel is in cards, `cardTop` the top edge of the cards.
    static func draw(in context: GraphicsContext, size: CGSize, turn: Double, position: Double, cardTop: Double, colors: [Color], glass: Color) {
        let screen = CGRect(origin: .zero, size: size)
        let top = size.height - skyHeight
        let gradient = colors.count == skyStops.count
            ? Gradient(stops: zip(colors, skyStops).map { Gradient.Stop(color: $0, location: $1) })
            : Gradient(colors: colors)
        let sky = GraphicsContext.Shading.linearGradient(gradient, startPoint: .zero, endPoint: CGPoint(x: 0, y: size.height))

        context.fill(Path(screen), with: sky)
        drawClouds(in: context, glass: false, turn: turn, top: top, box: screen)
        // A thin veil keeps white text readable over the clouds.
        context.fill(Path(screen), with: .color(veil))

        for step in 0 ..< SkyGlassStepsMotion.steps {
            let x = (size.width - cardWidth) / 2 + SkyGlassStepsMotion.offsetOf(step, position) * SkyGlassStepsMotion.pitch
            if x + cardWidth <= 0 || x >= size.width { continue }
            let card = CGRect(x: x, y: cardTop, width: cardWidth, height: cardHeight)
            var pane = context
            pane.clip(to: Path(roundedRect: card, cornerRadius: cardRadius, style: .circular))
            pane.fill(Path(screen), with: sky)
            drawClouds(in: pane, glass: true, turn: turn, top: top, box: card)
            pane.fill(Path(screen), with: .color(veil))
            pane.fill(Path(screen), with: .color(glass))
            // A hairline of light just inside the pane's edge.
            context.stroke(
                Path(roundedRect: card.insetBy(dx: 0.5, dy: 0.5), cornerRadius: cardRadius - 0.5, style: .circular),
                with: .color(edge),
                lineWidth: 1
            )
        }
    }
}
