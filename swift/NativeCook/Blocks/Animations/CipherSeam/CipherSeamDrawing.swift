import SwiftUI

/// The drawing of Cipher Seam's three Skia canvases: the lane of dust, the seam of light, and the
/// dark bowl behind the hero. All of it is a function of loop time `t`.
enum CipherSeamDrawing {
    // MARK: - Dust

    /// `DustField`: 660 fine points in a lane across the card's band, in three brightness layers
    /// drawn dimmest first. The lane sags a little toward the screen edges, following the bowl.
    /// `time` is already 0 under Reduce Motion.
    static func dust(_ context: GraphicsContext, time t: Double, width: Double, center: Double, lane: Double) {
        let layers: [(index: Int, color: Color, alpha: Double)] = [
            (2, CipherSeamPalette.rgb(0xFFFFFF), 0.38),
            (1, CipherSeamPalette.rgb(0xEDE6FF), 0.62),
            (0, CipherSeamPalette.rgb(0xFFFFFF), 0.9),
        ]
        for layer in layers {
            let drift = t * 0.004 * Double(1 + layer.index)
            var path = Path()
            for point in CipherSeamField.dust[layer.index] {
                let u = (point.x + drift).truncatingRemainder(dividingBy: 1)
                let fromMiddle = abs(u - 0.5)
                let spread = point.y * lane * (1 + 0.25 * fromMiddle)
                let y = center + spread + fromMiddle * 10 + sin(t * 0.6 + point.phase) * 1.5
                let radius = point.radius * (0.85 + 0.3 * sin(t * 2.2 + point.phase))
                path.addEllipse(in: CGRect(x: u * width - radius, y: y - radius, width: radius * 2, height: radius * 2))
            }
            context.fill(path, with: .color(layer.color.opacity(layer.alpha)))
        }
    }

    // MARK: - Seam

    /// `Seam`: a hairline of white inside two violet glows, tapering at both ends. While the card
    /// crosses (5.0 to 8.1 s) it flares into a white band about 12 pt wide whose right edge breaks
    /// into particles, and sparks peel off the boundary into the token side.
    static func seam(
        _ context: GraphicsContext, time t: Double, reduced: Bool,
        width: Double, cardTop: Double, cardHeight: Double
    ) {
        let flare = reduced ? 0.35 : CipherSeamMath.interpolate(t, [5, 5.4, 7.6, 8.1], [0, 1, 1, 0])
        let pulse = 0.94 + 0.06 * sin(t * 23)
        let envelope = CipherSeamMath.interpolate(t, [0, 5, 5.4, 7.6, 8.1, 10.8], [0.85, 0.85, 1, 1, 0.85, 0.85])
        let glow = reduced ? 0.8 : envelope * pulse
        let middle = width / 2
        let tall = cardHeight * 1.2
        let top = cardTop + cardHeight / 2 - tall / 2
        let fadeStart = CGPoint(x: 0, y: top)
        let fadeEnd = CGPoint(x: 0, y: top + tall)

        // The idle seam: a wide violet glow, a lilac bloom and the white hairline.
        var glowGroup = context
        glowGroup.opacity = glow
        glowGroup.drawLayer { layer in
            let violet = Gradient(colors: [
                CipherSeamPalette.rgb(0x7A4DFF, alpha: 0),
                CipherSeamPalette.rgb(0x7A4DFF),
                CipherSeamPalette.rgb(0x7A4DFF, alpha: 0),
            ])
            softRect(
                layer, CGRect(x: middle - 22, y: top, width: 44, height: tall),
                shading: .linearGradient(violet, startPoint: fadeStart, endPoint: fadeEnd),
                blur: 18, alpha: 0.55
            )
            let lilac = Gradient(colors: [
                CipherSeamPalette.rgb(0x9A6BFF, alpha: 0),
                CipherSeamPalette.rgb(0xA884FF),
                CipherSeamPalette.rgb(0x9A6BFF, alpha: 0),
            ])
            softRect(
                layer, CGRect(x: middle - 5 - flare * 4, y: top + tall * 0.08, width: 10 + flare * 4, height: tall * 0.84),
                shading: .linearGradient(lilac, startPoint: fadeStart, endPoint: fadeEnd),
                blur: 5, alpha: 1
            )
            let white = Gradient(colors: [
                CipherSeamPalette.rgb(0xFFFFFF, alpha: 0),
                CipherSeamPalette.rgb(0xFFFFFF),
                CipherSeamPalette.rgb(0xFFFFFF, alpha: 0),
            ])
            softRect(
                layer, CGRect(x: middle - 0.8, y: top + tall * 0.14, width: 1.6, height: tall * 0.72),
                shading: .linearGradient(white, startPoint: fadeStart, endPoint: fadeEnd),
                blur: 0.7, alpha: 1
            )
        }

        guard flare > 0.01 else { return }

        // The band: a violet glow behind a white core that grows with the flare.
        let coreWidth = 1.6 + flare * 10.4
        let coreX = middle - 1 - flare * 1
        let coreTop = cardTop - cardHeight * 0.02 * flare
        let coreHeight = cardHeight * (1 + 0.04 * flare)
        var band = context
        band.opacity = flare
        band.drawLayer { layer in
            softRect(
                layer, CGRect(x: middle - 9, y: coreTop, width: 12, height: coreHeight),
                shading: .color(CipherSeamPalette.rgb(0x7B4BFF)), blur: 5, alpha: 1
            )
            softRect(
                layer, CGRect(x: coreX, y: coreTop, width: coreWidth, height: coreHeight),
                shading: .color(CipherSeamPalette.rgb(0xFFFFFF)), blur: 1.4, alpha: 1
            )
            layer.fill(edgePath(time: t, flare: flare, middle: middle, cardTop: cardTop, cardHeight: cardHeight), with: .color(.white))
        }

        context.fill(sparkPath(time: t, flare: flare, middle: middle, cardTop: cardTop, cardHeight: cardHeight), with: .color(.white))
    }

    /// A rectangle blurred by `blur` (Skia's sigma), filled with `shading`, at `alpha`.
    private static func softRect(_ context: GraphicsContext, _ rect: CGRect, shading: GraphicsContext.Shading, blur: Double, alpha: Double) {
        context.drawLayer { layer in
            layer.opacity = alpha
            layer.addFilter(.blur(radius: blur))
            layer.fill(Path(rect), with: shading)
        }
    }

    /// `edge`: the band's right side breaking up, 60 rows of ragged bars and loose specks that
    /// change 24 times a second.
    private static func edgePath(time t: Double, flare f: Double, middle: Double, cardTop: Double, cardHeight: Double) -> Path {
        var path = Path()
        guard f > 0.01 else { return path }
        let frame = Int(floor(t * 24))
        let rowHeight = cardHeight / 60
        for row in 0..<60 {
            let jitter = CipherSeamMath.hash(row * 31 + frame * 7)
            let reach = CipherSeamMath.hash(row * 17 + frame * 13 + 5)
            let y = cardTop + Double(row) * rowHeight
            path.addRect(CGRect(x: middle + 11 * f - 1, y: y, width: (1 + jitter * 7) * f, height: rowHeight + 0.4))
            if reach > 0.4 {
                path.addRect(CGRect(x: middle + 11 * f + 4 + reach * reach * 16, y: y, width: 1.3, height: 1.3))
            }
        }
        return path
    }

    /// `sparks`: 320 specks drifting off the boundary into the token side.
    private static func sparkPath(time t: Double, flare f: Double, middle: Double, cardTop: Double, cardHeight: Double) -> Path {
        var path = Path()
        guard f > 0.01 else { return path }
        for spark in CipherSeamField.sparks {
            let age = (t * spark.speed + spark.offset).truncatingRemainder(dividingBy: 1)
            let x = middle + 10 * f + age * age * (10 + spark.reach * 40)
            let y = cardTop + 2 + spark.y * (cardHeight - 4) + sin(age * 7 + Double(spark.index)) * 1.2
            let radius = spark.radius * (1 - age * 0.7) * f
            path.addEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
        }
        return path
    }

    // MARK: - Bowl

    /// The panel's dark bowl: an ellipse as wide as the hero and 172 pt tall, solid to 70% of its
    /// radius and fading to nothing at its rim (`cipher-bowl`), so the violet climbs the edges.
    static func bowl(_ context: GraphicsContext, heroWidth: Double, centerY: Double) {
        let radiusX = heroWidth * 0.5
        let radiusY = 172.0
        let ink = CipherSeamPalette.rgb(0x06040F)
        let gradient = Gradient(stops: [
            Gradient.Stop(color: ink, location: 0),
            Gradient.Stop(color: ink, location: 0.7),
            Gradient.Stop(color: ink.opacity(0), location: 1),
        ])
        var squashed = context
        squashed.translateBy(x: heroWidth / 2, y: centerY)
        squashed.scaleBy(x: 1, y: radiusY / radiusX)
        let circle = Path(ellipseIn: CGRect(x: -radiusX, y: -radiusX, width: radiusX * 2, height: radiusX * 2))
        squashed.fill(circle, with: .radialGradient(gradient, center: .zero, startRadius: 0, endRadius: radiusX))
    }
}
