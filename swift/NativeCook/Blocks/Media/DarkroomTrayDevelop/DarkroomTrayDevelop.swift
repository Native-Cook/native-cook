import SwiftUI
import UIKit

/// Darkroom Tray Develop — "Safelight", a film app's print step under a red safelight. A blank fibre
/// print lies in the developer; rocking the tray sends a wave across it and the image comes up,
/// shadows first, unevenly where the liquid front reaches it last. Then the stop bath, a FIXED stamp,
/// the room light flickers on to show the print in white light, and back to red: the print slides out
/// and a fresh sheet slides in.
///
/// Drawing: one Metal layer effect (DarkroomTrayDevelop.metal) over the bundled photo stretched to
/// the whole screen draws bench, tray, liquid, tongs and the two sheets; the photo is sampled from the
/// layer. The header, timer, stamp and stage pills are plain views over it.
///
/// Touch: drag sideways to rock the tray: the tilt follows the finger (rubber-banded past a full tip),
/// springs back on release, and the faster it rocks the faster the print develops. A fully developed
/// print hands over to the loop's stop-fix-lights sequence; otherwise, 2 s after the finger lifts, the
/// half-developed print slides out and a fresh sheet comes in. Reduced motion: no loop and no wave;
/// the finished print in white light, and dragging does nothing.
struct DarkroomTrayDevelop: View {
    /// Plays the 9 s rock-develop-fix-lights-next-sheet loop.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = DarkroomTrayDevelopEngine()

    private typealias P = DarkroomTrayDevelopPalette

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
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
        let L = DarkroomTrayDevelopLayout(width: size.width, height: size.height, insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            P.background

            TimelineView(.animation(paused: reduced)) { timeline in
                let shown = engine.step(to: timeline.date, layout: L, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    scene(shown.frame, L: L, size: size)
                    header(shown, size: size, insets: insets)
                    stamp(shown.frame, L: L)
                    panel(shown, size: size, insets: insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            if !reduced {
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: size.width, height: size.height)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in engine.drag(value.translation) }
                            .onEnded { _ in engine.release() }
                    )
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("A developing tray with a print. Drag sideways to rock the tray and develop the print.")
    }

    private func scene(_ f: DarkroomTrayDevelopFrame, L: DarkroomTrayDevelopLayout, size: CGSize) -> some View {
        Image(uiImage: DarkroomTrayDevelopPhoto.image)
            .resizable()
            .interpolation(.high)
            .frame(width: size.width, height: size.height)
            .layerEffect(
                ShaderLibrary.darkroomTrayDevelop(
                    .float2(size.width, size.height),
                    .float4(L.tray.0, L.tray.1, L.tray.2, L.tray.3),
                    .float4(L.sheet.0, L.sheet.1, L.sheet.2, L.sheet.3),
                    .float(f.dev),
                    .float(f.devB),
                    .float2(f.slideA, f.slideB),
                    .float2(f.rock, f.wave),
                    .float(f.wphase),
                    .float(f.light),
                    .float(f.stopBath)
                ),
                maxSampleOffset: .zero
            )
    }

    private func header(_ s: DarkroomTrayDevelopShown, size: CGSize, insets: EdgeInsets) -> some View {
        let light = s.frame.light
        let ink = P.ink(light)
        let soft = P.soft(light)
        return HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 4) {
                Text("SAFELIGHT")
                    .font(.system(size: 11, weight: .heavy))
                    .kerning(2.2)
                    .foregroundStyle(ink)
                Text("Print 3 of 12")
                    .font(.system(size: 30, weight: .heavy))
                    .kerning(-0.6)
                    .foregroundStyle(ink)
                Text("Fibre paper · 8 × 10 in · 20 °C")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(soft)
            }
            .layoutPriority(-1)
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text("DEV")
                    .font(.system(size: 11, weight: .heavy))
                    .kerning(2)
                    .foregroundStyle(soft)
                DarkroomTrayDevelopTimer(secs: s.secs, color: ink)
            }
            .padding(.top, 2)
        }
        .frame(width: size.width - 48, alignment: .topLeading)
        .offset(x: 24, y: insets.top + 18)
    }

    private func stamp(_ f: DarkroomTrayDevelopFrame, L: DarkroomTrayDevelopLayout) -> some View {
        let show = f.stamp > 0 ? min(1, f.stamp * 3) * (1 - f.stampOut) : 0
        let line = Color(red: 250 / 255, green: 236 / 255, blue: 222 / 255)
        return VStack(spacing: 0) {
            Text("FIXED")
                .font(.system(size: 22, weight: .black))
                .kerning(5)
                .foregroundStyle(line.opacity(0.92))
            Text("24 OCT · 2:00")
                .font(.system(size: 9, weight: .bold).monospacedDigit())
                .kerning(1.6)
                .foregroundStyle(line.opacity(0.8))
        }
        // Padding plus border, as the React Native box model lays them out.
        .padding(.horizontal, 13)
        .padding(.vertical, 7)
        .overlay(RoundedRectangle(cornerRadius: 7).strokeBorder(line.opacity(0.7), lineWidth: 1))
        .padding(5)
        .overlay(RoundedRectangle(cornerRadius: 10).strokeBorder(line.opacity(0.85), lineWidth: 2))
        .scaleEffect(DarkroomTrayDevelopMotion.stampScale(f.stamp))
        .rotationEffect(.degrees(-9))
        .offset(y: -8 * f.stampOut)
        .opacity(show)
        .offset(x: L.sheet.0 - L.sheet.2 / 2 + 14, y: L.sheet.1 - L.sheet.3 / 2 + 22)
    }

    private func panel(_ s: DarkroomTrayDevelopShown, size: CGSize, insets: EdgeInsets) -> some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        let light = s.frame.light
        return VStack(spacing: 14) {
            HStack(spacing: 8) {
                ForEach(0..<3, id: \.self) { i in
                    DarkroomTrayDevelopPill(i: i, pos: s.stagePos, light: light)
                }
            }
            Text("Drag sideways to rock the tray")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(P.soft(light))
                .frame(maxWidth: .infinity)
        }
        .padding(14)
        .frame(width: size.width - 32, height: 128)
        .background {
            shape
                .fill(Color(red: 24 / 255, green: 14 / 255, blue: 14 / 255).opacity(0.88))
                .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.45), radius: 20, y: 18)
        }
        .overlay {
            shape.strokeBorder(Color(red: 1, green: 220 / 255, blue: 210 / 255).opacity(0.07), lineWidth: 1)
        }
        .overlay {
            shape
                .inset(by: 1)
                .stroke(
                    LinearGradient(colors: [Color(red: 1, green: 230 / 255, blue: 220 / 255).opacity(0.06), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.15)),
                    lineWidth: 1
                )
        }
        .offset(x: 16, y: size.height - insets.bottom - 16 - 128)
    }
}

enum DarkroomTrayDevelopPalette {
    static let background = Color(red: 14 / 255, green: 6 / 255, blue: 6 / 255)

    typealias RGBA = (Double, Double, Double, Double)
    static let inkRed: RGBA = (242, 185, 173, 1)
    static let inkWhite: RGBA = (243, 240, 234, 1)
    static let softRed: RGBA = (242, 185, 173, 0.62)
    static let softWhite: RGBA = (243, 240, 234, 0.66)

    static func mix(_ a: RGBA, _ b: RGBA, _ e: Double) -> Color {
        let m = { (x: Double, y: Double) in x + (y - x) * e }
        return Color(.sRGB, red: m(a.0, b.0) / 255, green: m(a.1, b.1) / 255, blue: m(a.2, b.2) / 255, opacity: m(a.3, b.3))
    }

    static func ink(_ light: Double) -> Color { mix(inkRed, inkWhite, light) }
    static func soft(_ light: Double) -> Color { mix(softRed, softWhite, light) }
}

/// The bundled photo, decoded once.
enum DarkroomTrayDevelopPhoto {
    static let image: UIImage = {
        guard let url = Bundle.main.url(forResource: "DarkroomTrayDevelop-print", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }()
}

/// The development timer: three wheels of digits and a colon.
private struct DarkroomTrayDevelopTimer: View {
    let secs: Double
    let color: Color

    static let digitH = 40.0

    var body: some View {
        HStack(spacing: 0) {
            wheel(count: 10, which: 0)
            Text(":")
                .font(.system(size: 32, weight: .light))
                .foregroundStyle(color)
                .padding(.horizontal, 1)
            wheel(count: 6, which: 1)
            wheel(count: 10, which: 2)
        }
        .frame(height: Self.digitH)
    }

    /// A wheel for the minutes (0), the tens of seconds (1) or the seconds (2).
    private func wheel(count: Int, which: Int) -> some View {
        let s = secs
        var pos: Double
        if which == 2 {
            pos = s.truncatingRemainder(dividingBy: 10)
        } else if which == 1 {
            pos = floor(s.truncatingRemainder(dividingBy: 60) / 10) + max(0, s.truncatingRemainder(dividingBy: 10) - 9)
        } else {
            pos = floor(s / 60) + max(0, s.truncatingRemainder(dividingBy: 60) - 59)
        }
        let shift = pos.truncatingRemainder(dividingBy: Double(count))
        return VStack(spacing: 0) {
            ForEach(0...count, id: \.self) { i in
                Text("\(i % count)")
                    .font(.system(size: 34, weight: .light).monospacedDigit())
                    .foregroundStyle(color)
                    .frame(width: 21, height: Self.digitH)
            }
        }
        .offset(y: -shift * Self.digitH)
        .frame(width: 21, height: Self.digitH, alignment: .top)
        .clipped()
    }
}

private struct DarkroomTrayDevelopPill: View {
    let i: Int
    let pos: Double
    let light: Double

    private static let names = ["Develop", "Stop", "Fix"]
    private static let times = ["1:30", "0:30", "2:00"]

    var body: some View {
        typealias P = DarkroomTrayDevelopPalette
        let on = max(0, 1 - abs(pos - Double(i)))
        let base: P.RGBA = (242 + (243 - 242) * light, 120 + (240 - 120) * light, 100 + (234 - 100) * light, 0.08)
        let lit: P.RGBA = (242 + (243 - 242) * light, 120 + (240 - 120) * light, 100 + (234 - 100) * light, 0.3 + (0.22 - 0.3) * light)
        let ink = P.ink(light)
        return VStack(spacing: 2) {
            Text(Self.names[i])
                .font(.system(size: 15, weight: .bold))
            Text(Self.times[i])
                .font(.system(size: 13, weight: .medium).monospacedDigit())
        }
        .foregroundStyle(ink)
        .opacity(0.55 + 0.45 * on)
        .frame(maxWidth: .infinity)
        .frame(height: 58)
        .background(P.mix(base, lit, on), in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .scaleEffect(1 + 0.03 * on)
    }
}
