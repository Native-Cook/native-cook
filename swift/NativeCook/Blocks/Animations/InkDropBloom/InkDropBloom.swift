import SwiftUI
import UIKit

/*
 Ink Drop Bloom — "Studio Tint", a palette generator whose loader is a glass of water.

 Look: a warm studio wall and table, a tall glass tumbler of clear water in the middle (all drawn
 by one full-screen Metal shader, InkDropBloomShader.metal). Drops of ink fall in one by one —
 indigo, orchid, lagoon — and bloom into billowing plumes that sink, break into smaller rings and
 mix where they meet; the palette card below fills each swatch as its colour blooms (the two mixes
 once the colours meet). Then a mesh press pushes the ink down and the water is clear again.

 autoPlay: loop = 9 s, see InkDropBloomMotion.swift for the script.
 Touch: tap in the water to drop ink at that point (indigo, orchid, lagoon in turn; light haptic);
 drag to stir (the plumes twist around the finger). 2 s after the last touch the ink fades out
 (0.9 s) and the loop restarts from t = 0. Reduced motion: the full palette, still, no loop.
*/
struct InkDropBloom: View {
    /// Plays the 9 s drop-bloom-filter loop until touched.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = InkDropBloomSim()
    @State private var touched = false
    @State private var dragStarted = false

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
        let g = InkDropBloomGeo(w: max(1, size.width), h: max(1, size.height),
                                insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let paused = reduced || (!auto && !touched)
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = sim.step(to: timeline.date, geo: g, auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                InkDropBloomPalette.room
                scene(f, g, size: size)
                    .gesture(touch(g), including: reduced ? .none : .all)
                    .accessibilityElement()
                    .accessibilityLabel("A glass of water. Tap the water to drop ink, drag to stir it.")
                InkDropBloomHeader(label: f.label)
                    .frame(width: size.width - 48, height: InkDropBloomMotion.headerH, alignment: .topLeading)
                    .offset(x: 24, y: insets.top + 12)
                    .allowsHitTesting(false)
                InkDropBloomPaletteCard(swatches: f.swatches, bar: f.bar)
                    .frame(width: size.width - 40, height: InkDropBloomMotion.paletteH, alignment: .topLeading)
                    .offset(x: 20, y: g.paletteTop)
                    .allowsHitTesting(false)
                InkDropBloomActions(bar: f.bar)
                    .frame(width: size.width - 40, height: InkDropBloomMotion.buttonH)
                    .offset(x: 20, y: g.buttonTop)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
    }

    private func scene(_ f: InkDropBloomFrame, _ g: InkDropBloomGeo, size: CGSize) -> some View {
        let d = f.drops.map { Float($0) }
        let ink = { (i: Int) -> SIMD3<Float> in InkDropBloomMotion.inks[f.inks[i]] }
        let c0 = ink(0), c1 = ink(1), c2 = ink(2), c3 = ink(3)
        let fc = InkDropBloomMotion.inks[f.fallInk]
        let tint = f.tint
        let shader = ShaderLibrary.default.InkDropBloomScene(
            .float2(Float(g.w), Float(g.h)),
            .float4(Float(g.cx), Float(g.top), Float(g.bottom), Float(g.hwTop)),
            .float4(Float(g.surface), Float(g.floor), Float(g.hwBot), Float(g.wall)),
            .float4(d[0], d[1], d[2], d[3]),
            .float4(d[4], d[5], d[6], d[7]),
            .float4(d[8], d[9], d[10], d[11]),
            .float4(d[12], d[13], d[14], d[15]),
            .float3(c0.x, c0.y, c0.z),
            .float3(c1.x, c1.y, c1.z),
            .float3(c2.x, c2.y, c2.z),
            .float3(c3.x, c3.y, c3.z),
            .float4(Float(f.falling[0]), Float(f.falling[1]), Float(f.falling[2]), Float(f.falling[3])),
            .float3(fc.x, fc.y, fc.z),
            .float4(Float(f.press[0]), Float(f.press[1]), Float(f.press[2]), Float(f.press[3])),
            .float4(Float(f.stir[0]), Float(f.stir[1]), Float(f.stir[2]), Float(f.stir[3])),
            .float(Float(sim.flow)),
            .float3(tint.x, tint.y, tint.z)
        )
        return Rectangle()
            .colorEffect(shader)
            .frame(width: size.width, height: size.height)
            .contentShape(Rectangle())
    }

    private func touch(_ g: InkDropBloomGeo) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !dragStarted {
                    dragStarted = true
                    touched = true
                    sim.touchDown(v.startLocation.x, v.startLocation.y)
                }
                sim.stir(v.location.x, v.location.y)
            }
            .onEnded { v in
                // A short touch that barely moved is a tap: ink goes in where it landed.
                if hypot(v.translation.width, v.translation.height) < 10 && g.inWater(v.location.x, v.location.y) {
                    sim.drop(v.location.x, v.location.y)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
                sim.touchUp()
                dragStarted = false
            }
    }
}

enum InkDropBloomPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255, opacity: a)
    }

    static let room = hex(0xE6DED2)
    static let ink = hex(0x2A2330)
    static let muted = hex(0x776C61)
    static let rust = hex(0x8A5A3C)

    struct Swatch {
        let name: String
        let hex: String
        let color: Color
        let deep: Color
    }

    static let swatches: [Swatch] = [
        Swatch(name: "Indigo", hex: "2D3489", color: hex(0x2D3489), deep: hex(0x1D2266)),
        Swatch(name: "Orchid", hex: "B0306E", color: hex(0xB0306E), deep: hex(0x7E1B4E)),
        Swatch(name: "Lagoon", hex: "0F7C80", color: hex(0x0F7C80), deep: hex(0x08585C)),
        Swatch(name: "Plum", hex: "4E2763", color: hex(0x4E2763), deep: hex(0x331745)),
        Swatch(name: "Deep Sea", hex: "1C4A6B", color: hex(0x1C4A6B), deep: hex(0x11324B)),
    ]
}

private struct InkDropBloomHeader: View {
    let label: Double
    private typealias P = InkDropBloomPalette
    private let line = 22.0

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 10) {
                Text("STUDIO TINT")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2.2)
                    .foregroundStyle(P.rust)
                Text("Batch 0427")
                    .font(.system(size: 11, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(P.rust)
                    .padding(.horizontal, 8)
                    .frame(height: 20)
                    .background(Capsule().fill(P.hex(0x8A5A3C, 0.1)))
            }
            Text("Generating your palette")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.6)
                .foregroundStyle(P.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.5)
                .padding(.top, 10)
            // The phase line: labels roll up through a one-line window as the phase changes.
            ZStack(alignment: .topLeading) {
                ForEach(Array(InkDropBloomMotion.phases.enumerated()), id: \.offset) { i, text in
                    let d = Double(i) - label
                    Text(text)
                        .font(.system(size: 16, weight: .medium))
                        .foregroundStyle(P.muted)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .frame(height: line)
                        .opacity(max(0, 1 - abs(d) * 1.4))
                        .offset(y: d * line)
                }
            }
            .frame(height: line, alignment: .topLeading)
            .clipped()
            .padding(.top, 8)
        }
    }
}

private struct InkDropBloomPaletteCard: View {
    let swatches: [Double]
    let bar: Double
    private typealias P = InkDropBloomPalette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        VStack(spacing: 0) {
            HStack(spacing: 12) {
                Text("Palette")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(P.ink)
                ZStack(alignment: .leading) {
                    Capsule().fill(P.hex(0x2A2330, 0.08))
                    Capsule().fill(P.hex(0x4A3F86))
                        .scaleEffect(x: max(0.001, bar), y: 1, anchor: .leading)
                }
                .frame(height: 4)
                .frame(maxWidth: .infinity)
                .clipShape(Capsule())
                Text("5 colours")
                    .font(.system(size: 13, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(P.muted)
            }
            HStack(alignment: .top, spacing: 0) {
                ForEach(0..<5, id: \.self) { i in
                    if i > 0 { Spacer(minLength: 0) }
                    InkDropBloomSwatchView(swatch: P.swatches[i], k: swatches[i])
                }
            }
            .padding(.top, 14)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 16)
        .padding(.top, 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background {
            shape.fill(P.hex(0xFCF9F4, 0.86))
                .shadow(color: P.hex(0x463424, 0.12), radius: 18, y: 18)
                .shadow(color: P.hex(0x463424, 0.14), radius: 1.5, y: 1)
        }
        .overlay {
            shape.strokeBorder(P.hex(0x786450, 0.18), lineWidth: 0.5)
        }
        .overlay {
            shape.strokeBorder(
                LinearGradient(colors: [Color.white.opacity(0.9), Color.white.opacity(0)],
                               startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)),
                lineWidth: 1
            )
        }
    }
}

/// A swatch: a dashed outline that fills from the bottom like a little vial of its colour.
private struct InkDropBloomSwatchView: View {
    let swatch: InkDropBloomPalette.Swatch
    let k: Double
    private typealias P = InkDropBloomPalette

    var body: some View {
        let vial = RoundedRectangle(cornerRadius: 14, style: .continuous)
        VStack(spacing: 0) {
            ZStack(alignment: .topLeading) {
                vial.fill(P.hex(0x2A2330, 0.03))
                ZStack(alignment: .topLeading) {
                    swatch.color
                    VStack(spacing: 0) {
                        Spacer(minLength: 0)
                        swatch.deep.opacity(0.55).frame(height: 52 * 0.45)
                    }
                    RoundedRectangle(cornerRadius: 3)
                        .fill(Color.white.opacity(0.35))
                        .frame(width: 14, height: 5)
                        .offset(x: 6, y: 6)
                }
                .frame(width: 52, height: 52, alignment: .topLeading)
                .offset(x: -2, y: -2 + (1 - k) * 52)
            }
            .frame(width: 48, height: 48, alignment: .topLeading)
            .clipShape(vial)
            .overlay {
                vial.strokeBorder(P.hex(0x2A2330, 0.22), style: StrokeStyle(lineWidth: 1.5, dash: [4.5, 3]))
            }
            VStack(spacing: 1) {
                Text(swatch.name)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                Text(swatch.hex)
                    .font(.system(size: 10, design: .monospaced))
                    .foregroundStyle(P.muted)
                    .lineLimit(1)
            }
            .fixedSize()
            .frame(width: 56)
            .padding(.top, 6)
            .opacity(0.35 + 0.65 * min(1, k * 1.5))
        }
        .frame(width: 56)
    }
}

private struct InkDropBloomActions: View {
    let bar: Double
    private typealias P = InkDropBloomPalette
    private let h = InkDropBloomMotion.buttonH

    var body: some View {
        HStack(spacing: 12) {
            Button {} label: {
                Text("Save palette")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(P.hex(0xF6F1EA))
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .background {
                        Capsule().fill(P.hex(0x2B2433))
                            .shadow(color: P.hex(0x1E1428, 0.22), radius: 12, y: 10)
                            .shadow(color: P.hex(0x1E1428, 0.3), radius: 1, y: 1)
                    }
                    .overlay {
                        Capsule().strokeBorder(
                            LinearGradient(colors: [Color.white.opacity(0.14), Color.white.opacity(0)],
                                           startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.15)),
                            lineWidth: 1
                        )
                    }
            }
            .buttonStyle(.plain)
            .opacity(0.5 + 0.5 * bar)

            Button {} label: {
                InkDropBloomRegenerateIcon()
                    .stroke(P.ink, style: StrokeStyle(lineWidth: 1.9, lineCap: .round, lineJoin: .round))
                    .frame(width: 22, height: 22)
                    .frame(width: h, height: h)
                    .background {
                        Circle().fill(P.hex(0xFCF9F4, 0.9))
                            .shadow(color: P.hex(0x463424, 0.1), radius: 9, y: 8)
                            .shadow(color: P.hex(0x463424, 0.14), radius: 1, y: 1)
                    }
                    .overlay { Circle().strokeBorder(P.hex(0x786450, 0.22), lineWidth: 0.5) }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Regenerate")
        }
    }
}

/// The SVG "M17.6 8.2 A7 7 0 1 0 18 13.2 M17.8 3.6 V8.4 H13" in a 22 × 22 box: the large arc runs
/// counter-clockwise around (11.29, 11.22) from -25.6° to 16.4°.
private struct InkDropBloomRegenerateIcon: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 22
        let c = CGPoint(x: 11.286, y: 11.221)
        let a0 = atan2(8.2 - c.y, 17.6 - c.x)
        let a1 = atan2(13.2 - c.y, 18 - c.x) - 2 * .pi
        var p = Path()
        let n = 48
        for i in 0...n {
            let a = a0 + (a1 - a0) * Double(i) / Double(n)
            let pt = CGPoint(x: (c.x + 7 * cos(a)) * s, y: (c.y + 7 * sin(a)) * s)
            if i == 0 { p.move(to: pt) } else { p.addLine(to: pt) }
        }
        p.move(to: CGPoint(x: 17.8 * s, y: 3.6 * s))
        p.addLine(to: CGPoint(x: 17.8 * s, y: 8.4 * s))
        p.addLine(to: CGPoint(x: 13 * s, y: 8.4 * s))
        return p
    }
}
