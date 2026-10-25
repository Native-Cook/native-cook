import SwiftUI
import UIKit

/*
 Soap Bubble Pop — "Bubble Bath", the reward screen of a kids' word-learning app.

 Look: a pastel tiled bathroom wall (aqua to lilac), three big soap bubbles hovering over it with
 swirling thin-film colour, a window reflection and a faint rim; a coral bubble wand holding a
 film of soap above the "Up next" card. All of it is one full-screen Metal shader
 (SoapBubblePopShader.metal). Tap a bubble and it pops: the film tears open from the finger,
 droplets fly off its outline and fall, a "+10" star floats up and the round's score rolls. New
 bubbles are blown from the wand.

 autoPlay: loop = 8 s (script in SoapBubblePopMotion.swift). Touch interrupts it; popped bubbles
 are blown again from the wand one at a time; 2 s after the last touch the bubbles glide home
 (0.9 s) and the loop restarts from t = 0. Reduced motion: the bubbles rest, still; nothing plays
 or pops.
*/
struct SoapBubblePop: View {
    /// Plays the 8 s hover-pop-blow loop until touched.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = SoapBubblePopSim()
    @State private var touched = false
    @State private var downAt: Date?

    private typealias P = SoapBubblePopPalette

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
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
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = SoapBubblePopGeo(w: max(1, size.width), h: max(1, size.height),
                                 insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let paused = reduced || (!auto && !touched)
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = sim.step(to: timeline.date, geo: g, auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                P.base
                scene(f, g, size: size)
                    .gesture(touch, including: reduced ? .none : .all)
                    .accessibilityElement()
                    .accessibilityLabel("Soap bubbles. Tap a bubble to pop it.")
                ForEach(0..<3, id: \.self) { i in
                    SoapBubblePopFloatingStar(age: f.stars[i], x: f.starX[i], y: f.starY[i])
                }
                SoapBubblePopHeader(score: f.score)
                    .frame(width: size.width - 40, alignment: .topLeading)
                    .offset(x: 20, y: insets.top + 8)
                SoapBubblePopCard()
                    .frame(width: size.width - 32, height: SoapBubblePopMotion.cardH, alignment: .topLeading)
                    .offset(x: 16, y: g.cardTop)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
    }

    private func scene(_ f: SoapBubblePopFrame, _ g: SoapBubblePopGeo, size: CGSize) -> some View {
        let p = f.poses.map { $0.map { Float($0) } }
        let shader = ShaderLibrary.default.SoapBubblePopScene(
            .float2(Float(g.w), Float(g.h)),
            .float4(p[0][0], p[0][1], p[0][2], p[0][3]),
            .float4(p[1][0], p[1][1], p[1][2], p[1][3]),
            .float4(p[2][0], p[2][1], p[2][2], p[2][3]),
            .float4(p[0][4], p[0][5], p[0][6], p[0][7]),
            .float4(p[1][4], p[1][5], p[1][6], p[1][7]),
            .float4(p[2][4], p[2][5], p[2][6], p[2][7]),
            .float4(Float(g.wx), Float(g.wy), Float(g.wr), Float(f.wandFilm)),
            .float4(Float(f.tap[0]), Float(f.tap[1]), Float(f.tap[2]), 0),
            .float(Float(f.film - f.film.rounded(.down)))
        )
        return Rectangle()
            .colorEffect(shader)
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
    }

    /// A tap (under 0.4 s, barely moving) pops the bubble under it; any touch stops the script.
    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                if downAt == nil {
                    downAt = .now
                    touched = true
                    sim.touch()
                }
            }
            .onEnded { v in
                let quick = Date.now.timeIntervalSince(downAt ?? .now) < 0.4
                downAt = nil
                guard quick, hypot(v.translation.width, v.translation.height) < 10 else {
                    sim.touch()
                    return
                }
                if sim.tap(v.location.x, v.location.y) >= 0 {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
    }
}

enum SoapBubblePopPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255, opacity: a)
    }

    static let base = hex(0x86A9DC)
    static let navy = hex(0x1B2F55)
    static let muted = hex(0x3E5276)
    static let coral = hex(0xF0644A)
}

/// The star of the score pill and the "+10": an SVG star in a 24 × 24 box.
private struct SoapBubblePopStar: View {
    let size: Double

    var body: some View {
        let s = size / 24
        Canvas { ctx, _ in
            let pts: [(Double, Double)] = [
                (12, 2.6), (14.8, 8.6), (21.3, 9.3), (16.4, 13.7), (17.8, 20.2),
                (12, 16.9), (6.2, 20.2), (7.6, 13.7), (2.7, 9.3), (9.2, 8.6),
            ]
            var star = Path()
            star.addLines(pts.map { CGPoint(x: $0.0 * s, y: $0.1 * s) })
            star.closeSubpath()
            ctx.fill(star, with: .linearGradient(
                Gradient(stops: [
                    .init(color: SoapBubblePopPalette.hex(0xFFE28A), location: 0),
                    .init(color: SoapBubblePopPalette.hex(0xFFC23D), location: 0.55),
                    .init(color: SoapBubblePopPalette.hex(0xF29A1F), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: 2.6 * s), endPoint: CGPoint(x: 0, y: 20.2 * s)))
            ctx.stroke(star, with: .color(SoapBubblePopPalette.hex(0xD9821A)),
                       style: StrokeStyle(lineWidth: s, lineJoin: .round))
            let r = 1.4 * s
            ctx.fill(Path(ellipseIn: CGRect(x: 9.6 * s - r, y: 9.8 * s - r, width: 2 * r, height: 2 * r)),
                     with: .color(.white.opacity(0.7)))
        }
        .frame(width: size, height: size)
    }
}

/// "+10" with a star, floating up from where a bubble popped.
private struct SoapBubblePopFloatingStar: View {
    let age: Double
    let x: Double
    let y: Double

    var body: some View {
        let shown = age >= 0
        let u = min(1, max(0, age) / SoapBubblePopMotion.starFor)
        let rise = 1 - pow(1 - u, 3)
        let pop = min(1, max(0, age) / 0.22)
        // A playful overshoot on the way in: 0.9 → 1.06 → 1.
        let scale = !shown ? 0.9 : age < 0.22 ? 0.9 + 0.16 * sin(pop * .pi * 0.5) : 1.06 - 0.06 * min(1, (age - 0.22) / 0.2)
        let opacity = shown ? min(1, age / 0.12) * (1 - max(0, (age - 0.7) / 0.4)) : 0
        HStack(spacing: 4) {
            SoapBubblePopStar(size: 26)
            Text("+10")
                .font(.system(size: 22, weight: .black))
                .foregroundStyle(Color.white)
                .shadow(color: SoapBubblePopPalette.hex(0x1B2F55, 0.45), radius: 1.5, y: 1.5)
        }
        .frame(width: 88, height: 40)
        .scaleEffect(scale)
        .opacity(max(0, opacity))
        .offset(x: shown ? x - 44 : -200, y: shown ? y - 20 - 76 * rise : -200)
        .allowsHitTesting(false)
    }
}

private struct SoapBubblePopHeader: View {
    let score: Double
    private typealias P = SoapBubblePopPalette
    private let digitH = 22.0
    private static let tens = ["", "1", "2", "3", "4", "5", "6", "7", "8", "9"]

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Button {} label: {
                    Path { p in
                        p.move(to: CGPoint(x: 3.5, y: 3.5))
                        p.addLine(to: CGPoint(x: 12.5, y: 12.5))
                        p.move(to: CGPoint(x: 12.5, y: 3.5))
                        p.addLine(to: CGPoint(x: 3.5, y: 12.5))
                    }
                    .stroke(P.navy, style: StrokeStyle(lineWidth: 2, lineCap: .round))
                    .frame(width: 16, height: 16)
                    .frame(width: 40, height: 40)
                    .background {
                        Circle().fill(Color.white.opacity(0.55))
                            .shadow(color: P.hex(0x1B2F55, 0.18), radius: 1, y: 1)
                    }
                    .overlay { SoapBubblePopInnerLight(shape: Circle(), opacity: 0.8) }
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Close")
                Spacer(minLength: 0)
                Text("Lesson 4 of 12")
                    .font(.system(size: 14, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(P.muted)
                Spacer(minLength: 0)
                HStack(spacing: 6) {
                    SoapBubblePopStar(size: 18)
                    HStack(spacing: 0) {
                        // The tens of the round score: a column of digits that rolls and rests on a whole digit.
                        VStack(spacing: 0) {
                            ForEach(0..<10, id: \.self) { i in
                                digit(Self.tens[i])
                            }
                        }
                        .offset(y: -score * digitH)
                        .frame(height: digitH, alignment: .top)
                        .clipped()
                        digit("0")
                    }
                    .frame(width: 26, alignment: .trailing)
                }
                .padding(.horizontal, 14)
                .frame(height: 40)
                .background {
                    Capsule().fill(Color.white.opacity(0.72))
                        .shadow(color: P.hex(0x1B2F55, 0.12), radius: 9, y: 8)
                        .shadow(color: P.hex(0x1B2F55, 0.18), radius: 1, y: 1)
                }
                .overlay { SoapBubblePopInnerLight(shape: Capsule(), opacity: 0.9) }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Round stars")
            }
            .frame(height: 44)
            Text("Lesson 4 done!")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(P.navy)
                .padding(.top, 16)
            Text("Ocean Words · pop the bubbles for stars")
                .font(.system(size: 16, weight: .medium))
                .foregroundStyle(P.muted)
                .padding(.top, 4)
                .allowsHitTesting(false)
        }
    }

    private func digit(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 18, weight: .heavy))
            .monospacedDigit()
            .foregroundStyle(P.navy)
            .frame(height: digitH)
    }
}

/// The 1 pt inner highlight along a control's top edge (CSS `inset 0 1px 0`).
private struct SoapBubblePopInnerLight<S: InsettableShape>: View {
    let shape: S
    let opacity: Double

    var body: some View {
        shape.strokeBorder(
            LinearGradient(colors: [Color.white.opacity(opacity), Color.white.opacity(0)],
                           startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
            lineWidth: 1
        )
        .allowsHitTesting(false)
    }
}

private struct SoapBubblePopCard: View {
    private typealias P = SoapBubblePopPalette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 28, style: .continuous)
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                SoapBubblePopShell()
                    .frame(width: 28, height: 28)
                    .frame(width: 44, height: 44)
                    .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(P.hex(0xFFEDE5)))
                VStack(alignment: .leading, spacing: 2) {
                    Text("UP NEXT · 6 MIN")
                        .font(.system(size: 11, weight: .bold))
                        .kerning(1.2)
                        .foregroundStyle(P.hex(0x8A7FA6))
                    Text("Lesson 5 · Shells & Shores")
                        .font(.system(size: 17, weight: .bold))
                        .foregroundStyle(P.navy)
                        .lineLimit(1)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            Button {} label: {
                Text("Continue")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(P.hex(0xFFF8F4))
                    .frame(maxWidth: .infinity)
                    .frame(height: 48)
                    .background {
                        Capsule().fill(P.coral)
                            .shadow(color: P.hex(0xF0644A, 0.35), radius: 9, y: 8)
                            .shadow(color: P.hex(0xA0321E, 0.35), radius: 1, y: 1)
                    }
                    .overlay { SoapBubblePopInnerLight(shape: Capsule(), opacity: 0.3) }
            }
            .buttonStyle(.plain)
        }
        .padding(16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background {
            shape.fill(P.hex(0xFBF8FF))
                .shadow(color: P.hex(0x1B1E46, 0.2), radius: 22, y: 22)
                .shadow(color: P.hex(0x1B1E46, 0.18), radius: 2, y: 2)
        }
        .overlay { SoapBubblePopInnerLight(shape: shape, opacity: 1) }
    }
}

/// The shell icon of the "Up next" card (SVG, 28 × 28).
private struct SoapBubblePopShell: View {
    var body: some View {
        Canvas { ctx, _ in
            var shell = Path()
            shell.move(to: CGPoint(x: 14, y: 4))
            shell.addCurve(to: CGPoint(x: 4, y: 16), control1: CGPoint(x: 7, y: 4), control2: CGPoint(x: 3.5, y: 10))
            shell.addLine(to: CGPoint(x: 14, y: 24))
            shell.addLine(to: CGPoint(x: 24, y: 16))
            shell.addCurve(to: CGPoint(x: 14, y: 4), control1: CGPoint(x: 24.5, y: 10), control2: CGPoint(x: 21, y: 4))
            shell.closeSubpath()
            ctx.fill(shell, with: .color(SoapBubblePopPalette.hex(0xFFD9C9)))
            ctx.stroke(shell, with: .color(SoapBubblePopPalette.coral),
                       style: StrokeStyle(lineWidth: 1.6, lineJoin: .round))
            var ribs = Path()
            let lines: [(CGPoint, CGPoint)] = [
                (CGPoint(x: 14, y: 6), CGPoint(x: 14, y: 22)),
                (CGPoint(x: 9, y: 7.5), CGPoint(x: 11.5, y: 21)),
                (CGPoint(x: 19, y: 7.5), CGPoint(x: 16.5, y: 21)),
                (CGPoint(x: 5.5, y: 12), CGPoint(x: 10, y: 20)),
                (CGPoint(x: 22.5, y: 12), CGPoint(x: 18, y: 20)),
            ]
            for (a, b) in lines {
                ribs.move(to: a)
                ribs.addLine(to: b)
            }
            ctx.stroke(ribs, with: .color(SoapBubblePopPalette.coral),
                       style: StrokeStyle(lineWidth: 1.2, lineCap: .round))
        }
    }
}
