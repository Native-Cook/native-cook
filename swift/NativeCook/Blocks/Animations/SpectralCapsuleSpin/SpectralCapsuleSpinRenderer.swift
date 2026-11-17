import SwiftUI
import UIKit

enum SpectralCapsuleSpinPalette {
    static func rgb(_ hex: UInt32, _ alpha: Double = 1) -> Color {
        Color(.sRGB,
              red: Double((hex >> 16) & 0xFF) / 255,
              green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255,
              opacity: alpha)
    }

    static let background: UInt32 = 0x040508
    static let vignette: UInt32 = 0x0B0E18
    static let body: UInt32 = 0x05070D
    static let frost: UInt32 = 0xC8D7EB
    static let fibre: UInt32 = 0xB9C9E6
    static let innerGlow: UInt32 = 0x2D4DFF
    static let rimCore: UInt32 = 0xF4F7FF
    static let warmFringe: UInt32 = 0xFFB458
    static let coolFringe: UInt32 = 0x8A6BFF
    static let capBase: UInt32 = 0xECE8FF
    static let causticCore: UInt32 = 0xFFE24A
    static let causticMid: UInt32 = 0xFFB040
    static let causticEdge: UInt32 = 0xFF6A3D
    static let crescent: UInt32 = 0xFF4D5A
    static let seam: UInt32 = 0x48E0D6
    static let halo: UInt32 = 0x1A2C7A
    static let rings: [UInt32] = [0xFFF1D6, 0xE6F27A, 0x5C8DFF]
    /// The dispersion band, red round to red.
    static let spectrum: [UInt32] = [0xFF3B5C, 0xFF9F1C, 0xFFE14D, 0x4BE38A, 0x29C6FF, 0x5A5BFF, 0xC64BFF, 0xFF3B5C]
}

/// Faint frosted fibres inside the glass: seeded strokes drawn once into a bitmap.
enum SpectralCapsuleSpinFibres {
    static let size = 480.0
    static let count = 420

    static func hash(_ i: Double, _ k: Double) -> Double {
        let x = sin(i * 12.9898 + k * 78.233) * 43758.5453
        return x - floor(x)
    }

    static let image: UIImage = {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = false
        let renderer = UIGraphicsImageRenderer(size: CGSize(width: size, height: size), format: format)
        return renderer.image { context in
            let cg = context.cgContext
            cg.setLineCap(.round)
            let base = SpectralCapsuleSpinPalette.fibre
            let red = CGFloat((base >> 16) & 0xFF) / 255
            let green = CGFloat((base >> 8) & 0xFF) / 255
            let blue = CGFloat(base & 0xFF) / 255
            for n in 0..<count {
                let i = Double(n)
                let x = hash(i, 1) * size
                let y = hash(i, 2) * size
                var length = 6 + 20 * hash(i, 3)
                let angle = 2 * Double.pi * hash(i, 4)
                let bend = (hash(i, 5) - 0.5) * length * 0.6
                var alpha = 0.12 + 0.20 * hash(i, 6)
                if n % 13 == 0 {
                    length *= 2.2
                    alpha = 0.38
                }
                let dx = cos(angle) * length / 2
                let dy = sin(angle) * length / 2
                cg.setStrokeColor(red: red, green: green, blue: blue, alpha: alpha)
                cg.setLineWidth(0.6)
                cg.move(to: CGPoint(x: x - dx, y: y - dy))
                cg.addQuadCurve(to: CGPoint(x: x + dx, y: y + dy),
                                control: CGPoint(x: x - sin(angle) * bend, y: y + cos(angle) * bend))
                cg.strokePath()
            }
        }
    }()
}

/// Draws the capsule for one pose into a canvas whose centre is the capsule's centre.
struct SpectralCapsuleSpinRenderer {
    let phi: Double
    /// Extra turn of the dispersion hues, in radians (the reduced-motion shimmer).
    let hueShift: Double
    /// Multiplies the warm caustic (the reduced-motion shimmer).
    let warmGain: Double
    let radius: Double
    let halfLength: Double

    private typealias P = SpectralCapsuleSpinPalette

    private static func path(_ points: [CGPoint], closed: Bool = true) -> Path {
        var path = Path()
        guard let first = points.first else { return path }
        path.move(to: first)
        for point in points.dropFirst() { path.addLine(to: point) }
        if closed { path.closeSubpath() }
        return path
    }

    private static func disc(_ c: CGPoint, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: 2 * r, height: 2 * r))
    }

    func draw(in context: inout GraphicsContext, size: CGSize) {
        let center = CGPoint(x: size.width / 2, y: size.height / 2)
        let g = SpectralCapsuleSpinGeometry(center: center, r: radius, h: halfLength, phi: phi)
        let light = SpectralCapsuleSpinTimeline.Light(facing: g.facing)
        let warm = light.warm * warmGain
        let silhouette = Self.path(g.silhouette())
        let cap = Self.path(g.nearCap())
        let near = g.near
        let u = SpectralCapsuleSpinGeometry.u
        let v = g.across
        let spectrum = Gradient(colors: (P.spectrum + P.spectrum.dropFirst()).map { P.rgb($0) })
        let spectralAngle = Angle.radians(-Double.pi / 4 + phi + hueShift)

        // The glass body, everything clipped to the silhouette.
        context.drawLayer { body in
            body.clip(to: silhouette)
            body.fill(silhouette, with: .color(P.rgb(P.body, 0.92)))

            let frost = light.frost
            if frost > 0.005 {
                let span = radius * 1.1
                body.fill(silhouette, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: P.rgb(P.frost, 0.55 * frost), location: 0),
                        .init(color: P.rgb(P.frost, 0.16 * frost), location: 0.5),
                        .init(color: P.rgb(P.frost, 0.55 * frost), location: 1),
                    ]),
                    startPoint: CGPoint(x: center.x - v.x * span, y: center.y - v.y * span),
                    endPoint: CGPoint(x: center.x + v.x * span, y: center.y + v.y * span)))
            }

            let fibres = body.resolve(Image(uiImage: SpectralCapsuleSpinFibres.image))
            let s = SpectralCapsuleSpinFibres.size
            body.drawLayer { tex in
                tex.opacity = min(1, 0.8 + 0.6 * frost)
                tex.translateBy(x: center.x, y: center.y)
                tex.rotate(by: .radians(-Double.pi / 4))
                tex.scaleBy(x: max(abs(g.cosP), 0.32), y: 1)
                tex.draw(fibres, in: CGRect(x: -s / 2 + g.sinP * 30, y: -s / 2, width: s, height: s))
            }

            body.drawLayer { glow in
                glow.addFilter(.blur(radius: 12))
                glow.stroke(silhouette, with: .color(P.rgb(P.innerGlow, 0.9 * light.rim)), lineWidth: 34)
            }

            // The near cap catching the light: a pale lens, a red crescent at the pole, a warm caustic.
            if warm > 0.005 || light.fibres > 0.005 {
                body.drawLayer { lens in
                    lens.clip(to: cap)
                    lens.fill(cap, with: .color(P.rgb(P.capBase, 0.8 * SpectralCapsuleSpinTimeline.smoothstep(0, 0.55, warm))))
                    let redCentre = g.towardsPole(0.55 + 0.3 * abs(g.cosP))
                    let redRadius = 0.75 * near.r
                    lens.fill(Self.disc(redCentre, redRadius), with: .radialGradient(
                        Gradient(colors: [P.rgb(P.crescent, 0.85 * warm), P.rgb(P.crescent, 0)]),
                        center: redCentre, startRadius: 0, endRadius: redRadius))
                    let hot = g.towardsPole(0.12 + 0.35 * abs(g.cosP))
                    lens.fill(Self.disc(hot, near.r), with: .radialGradient(
                        Gradient(stops: [
                            .init(color: P.rgb(P.causticCore, warm), location: 0),
                            .init(color: P.rgb(P.causticMid, 0.9 * warm), location: 0.4),
                            .init(color: P.rgb(P.causticEdge, 0.5 * warm), location: 0.7),
                            .init(color: P.rgb(P.causticEdge, 0), location: 1),
                        ]),
                        center: hot, startRadius: 0, endRadius: near.r))
                    if light.fibres > 0.005 {
                        lens.drawLayer { lit in
                            lit.opacity = light.fibres
                            lit.translateBy(x: near.c.x, y: near.c.y)
                            lit.rotate(by: .radians(Double.pi / 4))
                            lit.draw(fibres, in: CGRect(x: -s / 4, y: -s / 4, width: s / 2, height: s / 2))
                        }
                    }
                }
            }

            // The dispersion band: a crescent of spectrum along the cap's rim, and along the seam.
            let rainbow = light.rainbow
            let seamBand = max(0.7 * rainbow, 0.9 * warm)
            if rainbow > 0.005 || seamBand > 0.005 {
                body.drawLayer { band in
                    band.clip(to: cap)
                    band.addFilter(.blur(radius: 4))
                    let shading = GraphicsContext.Shading.conicGradient(spectrum, center: near.c, angle: spectralAngle)
                    band.drawLayer { arc in
                        arc.opacity = rainbow
                        arc.stroke(Self.path(g.nearRimArc(inset: near.r * 0.22), closed: false), with: shading,
                                   style: StrokeStyle(lineWidth: near.r * 0.36, lineCap: .round))
                    }
                    band.drawLayer { seam in
                        seam.opacity = seamBand
                        seam.stroke(Self.path(g.nearSeamFront(), closed: false), with: shading,
                                    style: StrokeStyle(lineWidth: near.r * 0.16, lineCap: .round))
                    }
                }
                body.stroke(Self.path(g.nearSeamFront(), closed: false),
                            with: .color(P.rgb(P.seam, 0.8 * warm)), lineWidth: 2)
            }

            // The far seam and the stacked rings that read through the glass when it points at us.
            let farSeam = 0.3 * g.facing * (1 - light.rings)
            if farSeam > 0.01 {
                body.stroke(Self.path(g.ring(0)), with: .color(P.rgb(0x9FB6FF, farSeam)), lineWidth: 1.5)
            }
            if light.rings > 0.005 {
                body.drawLayer { rings in
                    rings.addFilter(.blur(radius: 1.2))
                    for (index, lambda) in [0.0, 0.35, 0.7].enumerated() {
                        rings.stroke(Self.path(g.ringBack(lambda), closed: false),
                                     with: .color(P.rgb(P.rings[index], light.rings * (0.95 - 0.15 * Double(index)))),
                                     lineWidth: 4.5 - Double(index))
                    }
                    rings.stroke(Self.path(g.ring(1)), with: .color(P.rgb(P.rimCore, 0.8 * light.rings)), lineWidth: 2.5)
                }
            }
        }

        // The rim: a warm outer fringe (strongest bottom-left), a soft white bloom, a bright core.
        let span = halfLength + radius
        let along = (start: CGPoint(x: center.x - u.x * span, y: center.y - u.y * span),
                     end: CGPoint(x: center.x + u.x * span, y: center.y + u.y * span))
        context.drawLayer { fringe in
            fringe.addFilter(.blur(radius: 6))
            fringe.stroke(silhouette, with: .linearGradient(
                Gradient(colors: [P.rgb(P.warmFringe, 0.95), P.rgb(P.warmFringe, 0.55), P.rgb(P.coolFringe, 0.5)]),
                startPoint: along.start, endPoint: along.end), lineWidth: 16)
        }
        context.drawLayer { bloom in
            bloom.addFilter(.blur(radius: 4))
            bloom.stroke(silhouette, with: .color(P.rgb(0xFFFFFF, 0.65 * light.rim)), lineWidth: 10)
        }
        // A thin spectral fringe on the near cap's rim, always faintly there.
        context.drawLayer { edge in
            edge.addFilter(.blur(radius: 2))
            edge.opacity = 0.25 + 0.55 * light.rainbow
            edge.clip(to: silhouette)
            edge.stroke(Self.path(g.nearRimArc(inset: 3), closed: false),
                        with: .conicGradient(spectrum, center: near.c, angle: spectralAngle), lineWidth: 5)
        }
        context.stroke(silhouette, with: .color(P.rgb(P.rimCore, 0.95 * light.rim)), lineWidth: 3.4)
    }
}
