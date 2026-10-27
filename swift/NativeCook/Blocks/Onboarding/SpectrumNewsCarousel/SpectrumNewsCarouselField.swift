import SwiftUI

// MARK: - Particles

/// The 240 seeded particles (`SpectrumParticles` of `SpectrumArtwork.tsx`): positions come from the
/// same seed function and constants, so every particle lands where it does in React Native.
enum SpectrumNewsCarouselParticleField {
    private typealias M = SpectrumNewsCarouselMotion

    struct Seed {
        let x: Double
        let y: Double
        let phase: Double
        let radius: Double
    }

    private static let tau = Double.pi * 2

    /// `hash`: the fractional part of a sine, the usual shader noise.
    private static func noise(_ index: Int) -> Double {
        let value = sin(Double(index) * 127.1 + 31.7) * 43758.5453
        return value - floor(value)
    }

    /// `PARTICLES`.
    static let seeds: [Seed] = (0..<240).map { index in
        let size = Self.noise(index + 661)
        let large = pow(max(0, (size - 0.72) / 0.28), 1.5) * 5
        return Seed(
            x: Self.noise(index + 1),
            y: Self.noise(index + 221),
            phase: Self.noise(index + 441) * Self.tau,
            radius: 1.1 + size * 2 + large
        )
    }

    /// `DENSITY` and `EXTENT`: how many particles show, and how far down they reach, on each page.
    private static let density: [Double] = [0.4, 1, 0.52, 0.95, 0.2]
    private static let extent: [Double] = [0.48, 0.4, 0.33, 0.4, 0.22]

    /// The three layers' colours at the first page (`warm`) and at the later pages (`gray`).
    private static let warm: [[Double]] = [[212, 46, 74], [239, 108, 92], [234, 116, 172]]
    private static let gray: [[Double]] = [[51, 50, 57], [118, 116, 122], [197, 193, 198]]

    /// Each layer's own opacity (`opacity={[.82, .66, .5][group]}`).
    private static let layerOpacity: [Double] = [0.82, 0.66, 0.5]

    /// The layer's colour for page position `page`: warm on the welcome, grey from the first swipe.
    static func color(group: Int, page: Double) -> Color {
        let mix = M.clamp(page)
        var channels: [Double] = []
        for channel in 0..<3 {
            let from = warm[group][channel]
            let to = gray[group][channel]
            channels.append(M.jsRound(from + (to - from) * mix))
        }
        return Color(.sRGB, red: channels[0] / 255, green: channels[1] / 255, blue: channels[2] / 255, opacity: 1)
    }

    /// `useDerivedValue` path: the circles of one layer (every third particle) at loop time `time`.
    static func path(group: Int, time: Double, page: Double, width: Double, height: Double) -> Path {
        let p = M.clamp(page, 0, 4)
        let first = Int(floor(p))
        let second = min(4, first + 1)
        let blend = p - Double(first)
        let layerDensity = density[first] + (density[second] - density[first]) * blend
        let layerExtent = extent[first] + (extent[second] - extent[first]) * blend
        let phase = time / M.loop * tau
        let spread = M.clamp(p - 3)
        let count = Double(seeds.count)
        var circles = Path()
        var index = group
        while index < seeds.count {
            let seed = seeds[index]
            let weight = M.clamp((layerDensity * count - Double(index)) / 10 + 1)
            if weight > 0 {
                let harmonic = Double(index % 3)
                let sway = sin(phase * (1 + harmonic) + seed.phase) * (8 + p * 2)
                let x = (seed.x * width + sway - width / 2) * (1 + spread * 0.32) + width / 2
                let bob = cos(phase * (2 + harmonic) + seed.phase) * 13
                let y = 8 + pow(seed.y, 1.9) * height * layerExtent + bob - spread * seed.y * 60
                let radius = seed.radius * weight
                circles.addEllipse(in: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
            }
            index += 3
        }
        return circles
    }

    /// Draws the three layers; the largest, palest one sits out of focus (`<Blur blur={1.8} />`).
    static func draw(_ context: inout GraphicsContext, time: Double, page: Double, width: Double, height: Double) {
        for group in 0..<3 {
            let layerPath = path(group: group, time: time, page: page, width: width, height: height)
            let fill = GraphicsContext.Shading.color(color(group: group, page: page).opacity(layerOpacity[group]))
            if group == 2 {
                context.drawLayer { layer in
                    layer.addFilter(.blur(radius: 1.8))
                    layer.fill(layerPath, with: fill)
                }
            } else {
                context.fill(layerPath, with: fill)
            }
        }
    }
}

/// `SpectrumParticles`: one bounded canvas, `width` by 330 points, drawn under the page content.
struct SpectrumNewsCarouselParticles: View {
    let time: Double
    let page: Double
    let width: CGFloat

    /// The canvas's height in `<SpectrumParticles … height={330} />`.
    static let height: CGFloat = 330

    var body: some View {
        let groupOpacity = 0.78 + SpectrumNewsCarouselMotion.clamp(page) * 0.22
        Canvas { context, _ in
            var canvas = context
            SpectrumNewsCarouselParticleField.draw(
                &canvas,
                time: time,
                page: page,
                width: Double(width),
                height: Double(Self.height)
            )
        }
        .frame(width: width, height: Self.height)
        // `<Group opacity={...}>`: the three layers are lifted together, as one.
        .opacity(groupOpacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - Warm backdrop

/// `WarmBackdrop`: a light field behind the welcome page, hot magenta in the top-right corner, amber
/// on the left, a blush of green underfoot, and each edge glowing faintly inward like a lit screen
/// bezel. It is the screen's size, scaled into the welcome window as the window grows, and it fades
/// out as the first swipe begins.
struct SpectrumNewsCarouselWarmBackdrop: View {
    let window: SpectrumNewsCarouselMotion.WelcomeFrame
    let glow: Double
    let position: Double
    let size: CGSize

    /// One glow: centre and radii as fractions of the window, its colour and its peak alpha.
    private struct Blob {
        let x: CGFloat
        let y: CGFloat
        let rx: CGFloat
        let ry: CGFloat
        let color: UInt32
        let alpha: Double
    }

    /// `blobs` of the React Native component, in painting order.
    private static let blobs: [Blob] = [
        Blob(x: 0.98, y: 0.0, rx: 0.78, ry: 0.34, color: 0xF06BD8, alpha: 0.78),
        Blob(x: 0.02, y: 0.18, rx: 0.62, ry: 0.32, color: 0xFFAA3D, alpha: 0.72),
        Blob(x: 0.38, y: 0.06, rx: 0.62, ry: 0.2, color: 0xFFE16A, alpha: 0.7),
        Blob(x: 0.64, y: 0.14, rx: 0.5, ry: 0.2, color: 0xFF92B6, alpha: 0.5),
        Blob(x: -0.04, y: 0.48, rx: 0.18, ry: 0.36, color: 0xFFBE4A, alpha: 0.55),
        Blob(x: 1.04, y: 0.46, rx: 0.17, ry: 0.42, color: 0xF27FCD, alpha: 0.52),
        Blob(x: 0.22, y: 1.04, rx: 0.62, ry: 0.13, color: 0x94D67F, alpha: 0.62),
        Blob(x: 0.88, y: 1.03, rx: 0.44, ry: 0.11, color: 0xF4A2CB, alpha: 0.42),
    ]

    var body: some View {
        // `opacity: clamp(1 - page) * glow`.
        let opacity = SpectrumNewsCarouselMotion.clamp(1 - position) * glow
        Canvas { context, _ in
            guard opacity > 0 else { return }
            let width = size.width
            let height = size.height
            let centreX = CGFloat(window.x + window.width / 2) * width
            let centreY = CGFloat(window.y + window.height / 2) * height
            var canvas = context
            // `translateX`, `translateY`, `scaleX`, `scaleY` about the view's centre: the screen-sized
            // field becomes the welcome window.
            canvas.translateBy(x: centreX, y: centreY)
            canvas.scaleBy(x: CGFloat(window.width), y: CGFloat(window.height))
            canvas.translateBy(x: -width / 2, y: -height / 2)
            let box = CGRect(x: 0, y: 0, width: width, height: height)
            // `overflow: 'hidden'` and `backgroundColor`.
            canvas.clip(to: Path(box))
            canvas.fill(Path(box), with: .color(SpectrumNewsCarouselPalette.ground))
            for blob in Self.blobs {
                let base = SpectrumNewsCarouselPalette.rgb(blob.color)
                // The SVG gradient: alpha, 0.38 of it at 55% of the radius, none at the edge.
                let gradient = Gradient(stops: [
                    .init(color: base.opacity(blob.alpha), location: 0),
                    .init(color: base.opacity(blob.alpha * 0.38), location: 0.55),
                    .init(color: base.opacity(0), location: 1),
                ])
                var ellipse = canvas
                ellipse.translateBy(x: blob.x * width, y: blob.y * height)
                ellipse.scaleBy(x: blob.rx * width, y: blob.ry * height)
                let unitCircle = Path(ellipseIn: CGRect(x: -1, y: -1, width: 2, height: 2))
                ellipse.fill(
                    unitCircle,
                    with: .radialGradient(gradient, center: .zero, startRadius: 0, endRadius: 1)
                )
            }
        }
        .frame(width: size.width, height: size.height)
        // The view's own opacity lifts the whole field as one.
        .opacity(opacity)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

// MARK: - Window mask

/// The expansion mask (`expansionMask`): the screen shows only through a rounded window whose
/// corners open out to the screen's own as it fills it.
struct SpectrumNewsCarouselWindowShape: Shape {
    let window: SpectrumNewsCarouselMotion.WelcomeFrame

    func path(in rect: CGRect) -> Path {
        let frame = CGRect(
            x: CGFloat(window.x) * rect.width,
            y: CGFloat(window.y) * rect.height,
            width: CGFloat(window.width) * rect.width,
            height: CGFloat(window.height) * rect.height
        )
        // `borderRadius: interpolate(rect.width, [.9, 1], [28, 0], 'clamp')`.
        let radius = SpectrumNewsCarouselMotion.interpolate(window.width, [0.9, 1], [28, 0])
        return Path(roundedRect: frame, cornerRadius: CGFloat(radius), style: .circular)
    }
}
