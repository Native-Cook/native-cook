import SwiftUI
import UIKit

/// The React Native reference's twelve glass petals, rendering progress and angular gestures.
/// One six-second revolution carries three face-to-edge waves around the ring.
/// Progress eases over 0.2–5.4 s and resets over 5.6–6 s; step labels change every two seconds.
/// Gestures and the initial reduced-motion frame intentionally follow the RN implementation.
struct PetalLoaderSpin: View {
    var autoPlay = false
    static let loop: Double = 6

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var elapsedBeforePause = 0.0
    @State private var resumedAt: Date?
    @State private var spinAnchor = 0.0
    @State private var spinVelocity = 0.0
    @State private var spinAt = 0.0
    @State private var flipAt = -10.0
    @State private var lastAngle = 0.0
    @State private var touching = false
    @State private var panning = false
    @State private var touchBeganAt = Date.distantPast

    private typealias M = PetalLoaderSpinMotion
    private typealias A = PetalLoaderSpinArt

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            let centre = CGPoint(x: size.width / 2, y: size.height * 0.44)

            TimelineView(.animation(paused: resumedAt == nil)) { timeline in
                let elapsed = elapsed(at: timeline.date)
                let t = autoPlay ? elapsed.truncatingRemainder(dividingBy: Self.loop) : 0
                let percent = M.percent(at: t)
                let flipAge = elapsed - flipAt
                let flip = flipAge < 0.6 ? M.cubic(M.segment(flipAge, 0, 0.6)) : 0

                ZStack(alignment: .topLeading) {
                    A.background
                        .colorEffect(ShaderLibrary.petalLoaderSpinGlass(
                            .float2(centre.x, centre.y),
                            .float(t), .float(spin(at: elapsed)), .float(flip),
                            .float(1 / UIScreen.main.scale),
                            .image(A.glintImage)
                        ))
                        .contentShape(Rectangle())
                        .gesture(drag(centre: centre))
                        .accessibilityLabel("A ring of glass petals, rendering. Drag round to spin, tap to flip.")
                        .accessibilityValue("\(Int((percent * 100).rounded())) percent")

                    Canvas { context, _ in
                        context.withCGContext { cg in
                            A.percentage(cg, value: percent, centre: centre)
                        }
                    }
                    .allowsHitTesting(false)

                    topRow(safe: safe)
                        .allowsHitTesting(false)

                    footer(time: t, percent: percent, safe: safe, size: size)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { updateClock() }
        .onChange(of: scenePhase) { updateClock() }
        .onChange(of: reduceMotion) { updateClock() }
    }

    private func topRow(safe: EdgeInsets) -> some View {
        HStack {
            Text("Lumen")
                .font(.system(size: 20, weight: .heavy))
                .tracking(-0.4)
                .foregroundStyle(.white)
            Spacer()
            Text("Scene 4 of 6")
                .font(.system(size: 14, weight: .semibold))
                .foregroundStyle(.white.opacity(0.5))
        }
        .frame(height: 34)
        .padding(.horizontal, 24)
        .padding(.top, safe.top + 8)
    }

    private func footer(time: Double, percent: Double, safe: EdgeInsets, size: CGSize) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Spacer(minLength: 0)
            Text("Rendering “Glass Garden”")
                .font(.system(size: 24, weight: .heavy))
                .tracking(-0.5)
                .foregroundStyle(.white)
                .lineLimit(1)
                .fixedSize(horizontal: true, vertical: true)
            ZStack(alignment: .topLeading) {
                ForEach(Array(M.steps.enumerated()), id: \.offset) { index, step in
                    let opacity = M.stepOpacity(index, time: time)
                    Text(step)
                        .font(.system(size: 16))
                        .foregroundStyle(A.lavender)
                        .opacity(opacity)
                        .offset(y: 8 * (1 - opacity))
                }
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: 26, alignment: .topLeading)
            .padding(.top, 6)

            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.1))
                Capsule().fill(A.bar).frame(width: (size.width - 48) * percent)
            }
            .frame(height: 4)
            .clipped()
            .padding(.top, 14)

            Text("Run in background")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(.white)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .overlay(Capsule().strokeBorder(.white.opacity(0.25), lineWidth: 1.5))
                .padding(.top, 22)
        }
        .padding(.horizontal, 24)
        .padding(.bottom, safe.bottom + 14)
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func elapsed(at date: Date) -> Double {
        elapsedBeforePause + (resumedAt.map { max(0, date.timeIntervalSince($0)) } ?? 0)
    }

    private func updateClock() {
        let now = Date()
        elapsedBeforePause = elapsed(at: now)
        resumedAt = scenePhase == .active && !reduceMotion ? now : nil
        if resumedAt == nil {
            touching = false
            panning = false
        }
    }

    /// Closed-form integration of the reference's exponentially decaying angular velocity.
    private func spin(at elapsed: Double) -> Double {
        spinAnchor + spinVelocity * (1 - exp(-2.5 * max(0, elapsed - spinAt))) / 2.5
    }

    private func drag(centre: CGPoint) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                if !touching {
                    touching = true
                    panning = false
                    touchBeganAt = .now
                    lastAngle = atan2(value.startLocation.y - centre.y, value.startLocation.x - centre.x)
                }
                if hypot(value.translation.width, value.translation.height) >= 4 { panning = true }
                guard panning else { return }
                let angle = atan2(value.location.y - centre.y, value.location.x - centre.x)
                var delta = angle - lastAngle
                if delta > .pi { delta -= .pi * 2 }
                if delta < -.pi { delta += .pi * 2 }
                let now = elapsed(at: .now)
                spinAnchor = spin(at: now) + delta
                spinAt = now
                spinVelocity = delta * 60
                lastAngle = angle
            }
            .onEnded { value in
                guard !reduceMotion else { return }
                // The pan wins after four points. The exclusive tap alone flips and buzzes.
                if !panning, Date.now.timeIntervalSince(touchBeganAt) < 0.5 {
                    flipAt = elapsed(at: .now)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
                touching = false
                panning = false
            }
    }
}

private enum PetalLoaderSpinMotion {
    static let steps = ["Mixing the light", "Adding depth", "Polishing the glass"]

    static func segment(_ t: Double, _ start: Double, _ end: Double) -> Double {
        min(1, max(0, (t - start) / (end - start)))
    }

    static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func percent(at t: Double) -> Double {
        cubic(segment(t, 0.2, 5.4)) * (1 - segment(t, 5.6, 6))
    }

    static func stepOpacity(_ index: Int, time t: Double) -> Double {
        let start = Double(index) * 2
        let opacity = segment(t, start, start + 0.3) * (1 - segment(t, start + 1.7, start + 2))
        return max(opacity, index == 0 ? segment(t, 5.7, 6) : 0)
    }
}

private enum PetalLoaderSpinArt {
    static let background = Color(red: 4 / 255, green: 3 / 255, blue: 10 / 255)
    static let lavender = Color(red: 185 / 255, green: 168 / 255, blue: 1)
    static let bar = Color(red: 143 / 255, green: 123 / 255, blue: 1)
    static let colorSpace = CGColorSpace(name: CGColorSpace.sRGB)!
    static let digitFont = UIFont.monospacedDigitSystemFont(ofSize: 40, weight: .heavy)
    static let percentFont = UIFont.systemFont(ofSize: 22, weight: .bold)

    /// A white disk of radius 11.9, convolved with a Gaussian of sigma 6; decoded once.
    static let glint: CGImage = {
        let scale = 3
        let side = 72 * scale
        var pixels = [UInt8](repeating: 0, count: side * side * 4)
        // Integrate the isotropic Gaussian over the disk in polar coordinates.
        var radial = [Double](repeating: 0, count: 256)
        for sample in radial.indices {
            let distance = Double(sample) / 5
            var alpha = 0.0
            for radius in 0..<24 {
                let r = (Double(radius) + 0.5) * 11.9 / 24
                for angle in 0..<32 {
                    let theta = (Double(angle) + 0.5) * .pi * 2 / 32
                    let d2 = distance * distance + r * r - 2 * distance * r * cos(theta)
                    alpha += exp(-d2 / 72) * r * (11.9 / 24) / (36 * 32)
                }
            }
            radial[sample] = min(1, alpha)
        }
        for y in 0..<side {
            for x in 0..<side {
                let r = hypot((Double(x) + 0.5) / 3 - 36, (Double(y) + 0.5) / 3 - 36)
                let sample = min(254, Int(r * 5))
                let fraction = min(1, max(0, r * 5 - Double(sample)))
                let alpha = UInt8(((radial[sample] * (1 - fraction) + radial[sample + 1] * fraction) * 255).rounded())
                let offset = (y * side + x) * 4
                pixels[offset] = alpha
                pixels[offset + 1] = alpha
                pixels[offset + 2] = alpha
                pixels[offset + 3] = alpha
            }
        }
        let provider = CGDataProvider(data: Data(pixels) as CFData)!
        return CGImage(width: side, height: side, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: side * 4, space: colorSpace, bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue), provider: provider, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }()
    static let glintImage = Image(decorative: glint, scale: 3)

    static func percentage(_ cg: CGContext, value: Double, centre: CGPoint) {
        UIGraphicsPushContext(cg)
        defer { UIGraphicsPopContext() }
        let attributes: [NSAttributedString.Key: Any] = [.font: digitFont, .foregroundColor: UIColor.white]
        let percentAttributes: [NSAttributedString.Key: Any] = [.font: percentFont, .foregroundColor: UIColor.white.withAlphaComponent(0.6)]
        let symbol = NSAttributedString(string: "%", attributes: percentAttributes)
        let left = centre.x - (84 + 2 + symbol.size().width) / 2
        let characters = Array(String(format: "%3d", Int((value * 100).rounded())))
        for index in 0..<3 {
            guard characters[index] != " " else { continue }
            let digit = NSAttributedString(string: String(characters[index]), attributes: attributes)
            let width = digit.size().width
            digit.draw(at: CGPoint(x: left + Double(index) * 28 + (28 - width) / 2, y: centre.y - digitFont.lineHeight / 2))
        }
        symbol.draw(at: CGPoint(x: left + 86, y: centre.y - percentFont.lineHeight / 2))
    }
}

#Preview {
    PetalLoaderSpin(autoPlay: true)
}
