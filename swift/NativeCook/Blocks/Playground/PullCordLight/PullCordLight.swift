import CoreImage
import CoreImage.CIFilterBuiltins
import SwiftUI
import UIKit

/// A dark bedroom at dusk with a pendant lamp hanging from the ceiling and its pull cord, a chain
/// of links simulated as a rope. Pulling the cord down stretches it; letting go past the click
/// switches the lamp: the bulb glows, a warm cone of light fades in under the shade and the room
/// comes up in lamplight with a soft flicker, while the cord bounces and swings. Pull again to switch
/// it off. The lamp itself can be grabbed and swung, and the cone sweeps the room with it.
struct PullCordLight: View {
    /// The room, drawn dark and lit from the same photo.
    var photo = "https://images.unsplash.com/photo-1529047033375-f402d3da24ca?auto=format&fit=crop&w=900&h=1950&q=80"
    /// Starts with the light on.
    var initiallyOn = false
    /// Pulls the cord to switch the light on, swings the lamp, and pulls it off again, on a loop.
    var autoPlay = false
    /// Called when a pull switches the light.
    var onToggle: ((Bool) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The lamp's physics live in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = PullCordLightEngine()
    @State private var room: PullCordLightRoom?
    /// True while the drag is alive; it resets without `onEnded` when the system cancels the touch.
    @GestureState private var dragging = false

    var body: some View {
        // The block draws under the system bars and keeps its caption clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: photo) {
            room = await PullCordLightRoom.load(photo)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = PullCordLightSettings(
            width: size.width,
            height: size.height,
            initiallyOn: initiallyOn,
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            onToggle: onToggle
        )
        let room = room
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, settings: settings)
            ZStack(alignment: .bottomLeading) {
                Canvas { context, canvasSize in
                    PullCordLightPainter.draw(in: context, size: canvasSize, frame: frame, room: room)
                }
                caption(frame: frame)
                    .padding(.horizontal, 28)
                    .padding(.bottom, insets.bottom + 26)
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: 0x05060a))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($dragging) { _, state, _ in state = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { _ in engine.touchEnded() }
        )
        .onChange(of: dragging) { _, isDragging in
            if !isDragging { engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("Pendant lamp. Pull the cord to switch the light.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { engine.toggle() }
    }

    private func caption(frame: PullCordLightFrame) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("LINEN ROOM · PENDANT")
                .font(.custom("AvenirNext-DemiBold", fixedSize: 11))
                .kerning(2)
                .foregroundStyle(Color.rgba(255, 240, 220, 0.6))
            ZStack(alignment: .topLeading) {
                title("Lights off").opacity(1 - frame.level)
                title("Lights on").opacity(frame.glow)
            }
            .frame(height: 44, alignment: .topLeading)
            Text("Pull the cord · Swing the lamp")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(Color(hex: 0xf3e6d4))
                .opacity(0.45 + 0.25 * frame.level)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func title(_ text: String) -> some View {
        Text(text)
            .font(.custom("AvenirNext-DemiBold", fixedSize: 34))
            .kerning(-0.5)
            .foregroundStyle(Color(hex: 0xfff3e2))
            .fixedSize()
    }
}

// MARK: - The room

/// The room photo graded twice, for night and for lamplight, once when it arrives.
struct PullCordLightRoom {
    let night: Image
    let lit: Image
    let size: CGSize

    static func load(_ address: String) async -> PullCordLightRoom? {
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url)
        else { return nil }
        return await Task.detached(priority: .userInitiated) { () -> PullCordLightRoom? in
            guard let image = UIImage(data: data), let source = CIImage(image: image) else { return nil }
            // No colour management: the matrices act on the photo's own encoded values, as Skia's do.
            let context = CIContext(options: [.workingColorSpace: NSNull(), .outputColorSpace: NSNull()])
            let space = CGColorSpace(name: CGColorSpace.sRGB)!
            func graded(_ m: [Double]) -> CGImage? {
                let filter = CIFilter.colorMatrix()
                filter.inputImage = source
                filter.rVector = CIVector(x: m[0], y: m[1], z: m[2], w: m[3])
                filter.gVector = CIVector(x: m[5], y: m[6], z: m[7], w: m[8])
                filter.bVector = CIVector(x: m[10], y: m[11], z: m[12], w: m[13])
                filter.aVector = CIVector(x: m[15], y: m[16], z: m[17], w: m[18])
                filter.biasVector = CIVector(x: m[4], y: m[9], z: m[14], w: m[19])
                guard let output = filter.outputImage else { return nil }
                return context.createCGImage(output, from: source.extent, format: .RGBA8, colorSpace: space)
            }
            guard let night = graded(PullCordLightMetrics.night), let lit = graded(PullCordLightMetrics.lamp) else { return nil }
            return PullCordLightRoom(
                night: Image(decorative: night, scale: 1),
                lit: Image(decorative: lit, scale: 1),
                size: CGSize(width: night.width, height: night.height)
            )
        }.value
    }
}

// MARK: - Drawing

enum PullCordLightColor {
    /// Reanimated's `interpolateColor`: each channel mixed in gamma 2.2 and rounded.
    static func mix(_ from: UInt32, _ to: UInt32, _ t: Double) -> Color {
        let t = min(1, max(0, t))
        func channel(_ shift: UInt32) -> Double {
            let a = pow(Double((from >> shift) & 0xff) / 255, 2.2)
            let b = pow(Double((to >> shift) & 0xff) / 255, 2.2)
            return (pow(a + (b - a) * t, 1 / 2.2) * 255).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }

    /// The same over three stops at 0, `middle` and 1.
    static func mix(_ a: UInt32, _ b: UInt32, _ c: UInt32, middle: Double, _ t: Double) -> Color {
        t <= middle ? mix(a, b, t / middle) : mix(b, c, (t - middle) / (1 - middle))
    }

    /// An alpha rounded to thousandths, as the RN version prints it into a colour string.
    static func alpha(_ value: Double) -> Double { (min(1, max(0, value)) * 1000).rounded() / 1000 }
}

enum PullCordLightPainter {
    private typealias M = PullCordLightMetrics
    private typealias C = PullCordLightColor

    static func draw(in context: GraphicsContext, size: CGSize, frame: PullCordLightFrame, room: PullCordLightRoom?) {
        let width = Double(size.width)
        let height = Double(size.height)
        let pivotX = width / 2
        let wire = height * M.wireShare
        let glow = frame.glow
        let full = CGRect(origin: .zero, size: size)

        if let room {
            // The photo cropped to fill the screen.
            let scale = max(width / room.size.width, height / room.size.height)
            let fill = CGRect(
                x: (width - room.size.width * scale) / 2,
                y: (height - room.size.height * scale) / 2,
                width: room.size.width * scale,
                height: room.size.height * scale
            )
            context.draw(room.night, in: fill)
            // The lit room: brightest where the cone lands, so it sweeps the room as the lamp swings.
            if glow > 0.001 {
                let d = wire + M.shadeDepth + height * 0.42
                let pool = CGPoint(x: pivotX - d * sin(frame.angle), y: M.pivotY + d * cos(frame.angle))
                context.drawLayer { layer in
                    layer.opacity = glow
                    layer.draw(room.lit, in: fill)
                    layer.blendMode = .destinationIn
                    layer.fill(Path(full), with: .radialGradient(
                        Gradient(stops: [
                            .init(color: .white, location: 0),
                            .init(color: Color(hex: 0xffffff, opacity: 0.5), location: 0.45),
                            .init(color: Color(hex: 0xffffff, opacity: 0.12), location: 1),
                        ]),
                        center: pool,
                        startRadius: 0,
                        endRadius: height * 0.8
                    ))
                }
            }
        } else {
            context.fill(Path(full), with: .color(Color(hex: 0x07090d)))
        }
        // A vignette that keeps the corners of the room in shadow.
        context.fill(Path(full), with: .radialGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x000000, opacity: 0), location: 0),
                .init(color: Color(hex: 0x000000, opacity: 0.25), location: 0.55),
                .init(color: Color(hex: 0x000000, opacity: 0.72), location: 1),
            ]),
            center: CGPoint(x: width / 2, y: height * 0.42),
            startRadius: 0,
            endRadius: height * 0.72
        ))

        var lamp = context
        lamp.translateBy(x: pivotX, y: M.pivotY)
        lamp.rotate(by: .radians(frame.angle))
        var shade = lamp
        shade.translateBy(x: 0, y: wire)

        let filament = frame.filament
        let halo = Gradient(colors: [
            .rgba(255, 214, 150, C.alpha(0.75 * filament)),
            .rgba(255, 190, 120, 0),
        ])

        // The cone of light, which turns with the lamp.
        if glow > 0.001 {
            let coneTop = M.shadeDepth + 4
            let coneEnd = height * M.coneLength
            let coneHalf = width * M.coneSpread
            var cone = Path()
            cone.move(to: CGPoint(x: -M.shadeRim * 0.86, y: coneTop))
            cone.addLine(to: CGPoint(x: M.shadeRim * 0.86, y: coneTop))
            cone.addLine(to: CGPoint(x: coneHalf, y: coneEnd))
            cone.addLine(to: CGPoint(x: -coneHalf, y: coneEnd))
            cone.closeSubpath()
            var core = Path()
            core.move(to: CGPoint(x: -M.shadeRim * 0.5, y: coneTop))
            core.addLine(to: CGPoint(x: M.shadeRim * 0.5, y: coneTop))
            core.addLine(to: CGPoint(x: coneHalf * 0.42, y: coneEnd * 0.8))
            core.addLine(to: CGPoint(x: -coneHalf * 0.42, y: coneEnd * 0.8))
            core.closeSubpath()
            var light = shade
            light.blendMode = .screen
            light.drawLayer { layer in
                layer.addFilter(.blur(radius: 16))
                layer.fill(cone, with: .linearGradient(
                    Gradient(stops: [
                        .init(color: .rgba(255, 220, 160, C.alpha(0.5 * glow)), location: 0),
                        .init(color: .rgba(255, 196, 120, C.alpha(0.2 * glow)), location: 0.35),
                        .init(color: .rgba(255, 180, 110, 0), location: 1),
                    ]),
                    startPoint: CGPoint(x: 0, y: coneTop),
                    endPoint: CGPoint(x: 0, y: coneEnd)
                ))
            }
            light.fill(
                Path(ellipseIn: CGRect(x: -150, y: M.shadeDepth + 10 - 150, width: 300, height: 300)),
                with: .radialGradient(halo, center: CGPoint(x: 0, y: M.shadeDepth + 10), startRadius: 0, endRadius: 150)
            )
            light.drawLayer { layer in
                layer.addFilter(.blur(radius: 16))
                layer.fill(core, with: .linearGradient(
                    Gradient(colors: [.rgba(255, 238, 205, C.alpha(0.4 * glow)), .rgba(255, 220, 170, 0)]),
                    startPoint: CGPoint(x: 0, y: coneTop),
                    endPoint: CGPoint(x: 0, y: coneEnd * 0.8)
                ))
            }
        } else if filament > 0.001 {
            // The halo outlives the cone while the filament cools.
            var light = shade
            light.blendMode = .screen
            light.fill(
                Path(ellipseIn: CGRect(x: -150, y: M.shadeDepth + 10 - 150, width: 300, height: 300)),
                with: .radialGradient(halo, center: CGPoint(x: 0, y: M.shadeDepth + 10), startRadius: 0, endRadius: 150)
            )
        }

        let cord = cordPath(frame.rope)
        // The cord's shadow on the wall.
        if glow > 0.003 {
            var shadow = context
            shadow.translateBy(x: 5 * glow, y: 4 * glow)
            shadow.drawLayer { layer in
                layer.addFilter(.blur(radius: 2.5))
                layer.stroke(cord, with: .color(.rgba(0, 0, 0, C.alpha(0.38 * glow))), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
            }
        }

        // The wire and the shade.
        let roomLight = 0.16 + 0.84 * glow
        var wirePath = Path()
        wirePath.move(to: .zero)
        wirePath.addLine(to: CGPoint(x: 0, y: wire + 4))
        lamp.stroke(wirePath, with: .color(Color(hex: 0x2a2e38)), lineWidth: 1.6)
        shade.fill(Path(CGRect(x: -9, y: -2, width: 18, height: 14)), with: .color(Color(hex: 0x1d1a16)))
        var dome = Path()
        dome.move(to: CGPoint(x: -11, y: 10))
        dome.addCurve(to: CGPoint(x: -M.shadeRim, y: M.shadeDepth), control1: CGPoint(x: -38, y: 12), control2: CGPoint(x: -M.shadeRim + 2, y: 40))
        dome.addLine(to: CGPoint(x: M.shadeRim, y: M.shadeDepth))
        dome.addCurve(to: CGPoint(x: 11, y: 10), control1: CGPoint(x: M.shadeRim - 2, y: 40), control2: CGPoint(x: 38, y: 12))
        dome.closeSubpath()
        shade.fill(dome, with: .linearGradient(
            Gradient(stops: [
                .init(color: C.mix(0x0a0c11, 0x2a2622, roomLight), location: 0),
                .init(color: C.mix(0x2c3243, 0x5d564d, roomLight), location: 0.32),
                .init(color: C.mix(0x3c4660, 0x3b352f, roomLight), location: 0.7),
                .init(color: C.mix(0x1a1f2c, 0x1d1a17, roomLight), location: 1),
            ]),
            startPoint: CGPoint(x: -M.shadeRim, y: 0),
            endPoint: CGPoint(x: M.shadeRim, y: 0)
        ))
        shade.fill(
            Path(ellipseIn: CGRect(x: -M.shadeRim, y: M.shadeDepth - 6, width: M.shadeRim * 2, height: 12)),
            with: .color(C.mix(0x05060a, 0xffd9a0, glow))
        )

        // The cord and its bead.
        context.stroke(cord, with: .color(C.mix(0x80869a, 0xefe2cc, roomLight)), style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
        let p = frame.rope
        if p.count >= (M.segments + 1) * 4 {
            let o = M.segments * 4
            let turn = atan2(p[o + 1] - p[o - 3], p[o] - p[o - 4]) - .pi / 2
            var bead = context
            bead.translateBy(x: p[o], y: p[o + 1])
            bead.rotate(by: .radians(turn))
            var shape = Path()
            shape.move(to: CGPoint(x: 0, y: -2))
            shape.addCurve(to: CGPoint(x: 7, y: 17), control1: CGPoint(x: 5, y: -2), control2: CGPoint(x: 7.5, y: 10))
            shape.addCurve(to: CGPoint(x: 0, y: 28), control1: CGPoint(x: 6.5, y: 25), control2: CGPoint(x: 3.5, y: 28))
            shape.addCurve(to: CGPoint(x: -7, y: 17), control1: CGPoint(x: -3.5, y: 28), control2: CGPoint(x: -6.5, y: 25))
            shape.addCurve(to: CGPoint(x: 0, y: -2), control1: CGPoint(x: -7.5, y: 10), control2: CGPoint(x: -5, y: -2))
            shape.closeSubpath()
            bead.fill(shape, with: .linearGradient(
                Gradient(colors: [C.mix(0x6c5847, 0xd49a62, roomLight), C.mix(0x2a2019, 0x7a4b28, roomLight)]),
                startPoint: CGPoint(x: -7, y: 0),
                endPoint: CGPoint(x: 7, y: 0)
            ))
        }

        // The bulb and its glow.
        if filament > 0.001 {
            var bulbHalo = shade
            bulbHalo.blendMode = .screen
            bulbHalo.fill(
                Path(ellipseIn: CGRect(x: -34, y: M.shadeDepth + 2 - 34, width: 68, height: 68)),
                with: .radialGradient(halo, center: CGPoint(x: 0, y: M.shadeDepth + 2), startRadius: 0, endRadius: 34)
            )
        }
        shade.fill(
            Path(ellipseIn: CGRect(x: -M.bulbR, y: M.shadeDepth + 1 - M.bulbR, width: M.bulbR * 2, height: M.bulbR * 2)),
            with: .color(C.mix(0x4d525e, 0xffb36b, 0xfff6e2, middle: 0.35, filament))
        )
        shade.fill(
            Path(ellipseIn: CGRect(x: -4.5 - 3.2, y: M.shadeDepth - 3 - 3.2, width: 6.4, height: 6.4)),
            with: .color(.rgba(255, 255, 255, 0.35))
        )
    }

    /// The cord through the midpoints between links, so it bends smoothly.
    private static func cordPath(_ p: [Double]) -> Path {
        var path = Path()
        guard p.count >= (M.segments + 1) * 4 else { return path }
        path.move(to: CGPoint(x: p[0], y: p[1]))
        for i in 1..<M.segments {
            let o = i * 4
            path.addQuadCurve(
                to: CGPoint(x: (p[o] + p[o + 4]) / 2, y: (p[o + 1] + p[o + 5]) / 2),
                control: CGPoint(x: p[o], y: p[o + 1])
            )
        }
        let last = M.segments * 4
        path.addLine(to: CGPoint(x: p[last], y: p[last + 1]))
        return path
    }
}
