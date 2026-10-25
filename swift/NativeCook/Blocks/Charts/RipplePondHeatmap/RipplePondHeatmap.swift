import SwiftUI
import UIKit

/*
 Ripple Pond Heatmap — "Stillpond", a meditation habit tracker's month as lily pads on a pond.

 Look: a pond seen from above (sand, pebbles, drifting caustics), October as a 7 × 5 grid of lily
 pads whose green deepens with the minutes practised (pale sage to deep emerald; days over an
 hour carry a water lily; days after today are faint rings). Tap a day and a ripple spreads from
 it: the floor refracts, light focuses into rings, the pads around bob as the wave passes, a ring
 lights the pad and the card below rolls to that day. Drag across the water and a wake follows.
 One full-screen Metal layer effect (RipplePondHeatmapShader.metal) over pads baked once
 (RipplePondHeatmapArt.swift).

 autoPlay: loop = 9 s (script in RipplePondHeatmapMotion.swift). 2 s after the last touch the
 water calms (0.9 s), the card rolls back to the month and the loop restarts. Reduced motion:
 still water, the month card, no loop.
*/
struct RipplePondHeatmap: View {
    /// Plays the 9 s tap-tap-wake loop until touched.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var sim = RipplePondHeatmapSim()
    @State private var pads: UIImage?
    @State private var touched = false
    @State private var pressed = false

    private typealias M = RipplePondHeatmapMotion
    private typealias P = RipplePondHeatmapPalette

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
        let g = RipplePondHeatmapGeo(w: max(1, size.width), h: max(1, size.height),
                                     insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let ready = pads != nil
        let paused = !ready || (!auto && !touched)
        let key = "\(Int(size.width))x\(Int(size.height))@\(Int(g.grid[1]))"
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = sim.step(to: timeline.date, geo: g, auto: auto, reduced: reduced, ready: ready)
            ZStack(alignment: .topLeading) {
                P.base
                scene(f, g, size: size)
                    .gesture(touch(g), including: reduced ? .none : .all)
                    .accessibilityElement()
                    .accessibilityLabel("October as lily pads on a pond. Tap a day to see it; drag to make a wake.")
                RipplePondHeatmapHeader()
                    .frame(width: size.width - 40, height: M.headerH, alignment: .topLeading)
                    .offset(x: 20, y: insets.top + 10)
                    .allowsHitTesting(false)
                overlays(g, size: size)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
        .task(id: key) {
            let w = size.width, h = size.height, grid = g.grid, scale = displayScale
            guard w > 1 && h > 1 else { return }
            let baked = await Task.detached(priority: .userInitiated) {
                RipplePondHeatmapArt.bake(w: w, h: h, grid: grid, scale: scale)
            }.value
            pads = baked
        }
    }

    private func scene(_ f: RipplePondHeatmapFrame, _ g: RipplePondHeatmapGeo, size: CGSize) -> some View {
        let s = f.src.map { Float($0) }
        let shader = ShaderLibrary.default.RipplePondHeatmapScene(
            .float2(Float(g.w), Float(g.h)),
            .float4(Float(g.grid[0]), Float(g.grid[1]), Float(g.grid[2]), Float(g.grid[3])),
            .float4(s[0], s[1], s[2], s[3]),
            .float4(s[4], s[5], s[6], s[7]),
            .float4(s[8], s[9], s[10], s[11]),
            .float4(s[12], s[13], s[14], s[15]),
            .float4(s[16], s[17], s[18], s[19]),
            .float4(s[20], s[21], s[22], s[23]),
            .float4(s[24], s[25], s[26], s[27]),
            .float4(s[28], s[29], s[30], s[31]),
            .float4(Float(f.sel[0]), Float(f.sel[1]), Float(f.sel[2]), 0),
            .float(Float(f.phase - floor(f.phase)))
        )
        // The layer is the baked pads; the shader draws the pond and samples them.
        return ZStack {
            Color.clear
            if let pads {
                Image(uiImage: pads)
                    .resizable()
                    .interpolation(.high)
            }
        }
        .frame(width: size.width, height: size.height)
        .layerEffect(shader, maxSampleOffset: CGSize(width: 48, height: 48))
        .contentShape(Rectangle())
    }

    private func overlays(_ g: RipplePondHeatmapGeo, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                ForEach(Array(["M", "T", "W", "T", "F", "S", "S"].enumerated()), id: \.offset) { _, d in
                    Text(d)
                        .font(.system(size: 12, weight: .bold))
                        .foregroundStyle(P.hex(0xEAF3EC, 0.5))
                        .frame(width: g.grid[2])
                }
            }
            .offset(x: g.grid[0], y: g.grid[1] - 26)
            .allowsHitTesting(false)

            HStack(spacing: 8) {
                legendText("Less")
                Canvas { ctx, _ in
                    for (i, c) in P.levels.enumerated() {
                        let r = 4 + Double(i) * 0.7
                        let cx = 7 + Double(i) * 19.5
                        ctx.fill(Path(ellipseIn: CGRect(x: cx - r, y: 7 - r, width: 2 * r, height: 2 * r)),
                                 with: .color(c.opacity(i == 0 ? 0.7 : 1)))
                    }
                }
                .frame(width: 92, height: 14)
                legendText("More")
                Spacer(minLength: 0)
                legendText("Goal 20 min a day")
            }
            .frame(width: size.width - 40, height: 20)
            .offset(x: 20, y: g.legendTop)
            .allowsHitTesting(false)

            RipplePondHeatmapCard(shown: sim.shown, previous: sim.previous, k: sim.roll)
                .frame(width: size.width - 32, height: M.cardH)
                .offset(x: 16, y: g.cardTop)

            HStack(spacing: 8) {
                chip("Best streak \(RipplePondHeatmapData.bestStreak) days")
                chip("Reminder 7:00")
                chip("Export")
            }
            .frame(width: size.width - 32, height: M.chipsH, alignment: .leading)
            .offset(x: 16, y: g.chipsTop)
        }
    }

    private func legendText(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 12, weight: .semibold))
            .foregroundStyle(P.hex(0xEAF3EC, 0.55))
    }

    private func chip(_ label: String) -> some View {
        Text(label)
            .font(.system(size: 13, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(P.ink)
            .lineLimit(1)
            .padding(.horizontal, 14)
            .frame(height: M.chipsH)
            .background(Capsule().fill(P.hex(0xC8F0D7, 0.1)))
            .overlay(Capsule().strokeBorder(P.hex(0xC8F0D7, 0.2), lineWidth: 0.5))
    }

    private func touch(_ g: RipplePondHeatmapGeo) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !pressed {
                    pressed = true
                    touched = true
                    sim.down(v.startLocation.x, v.startLocation.y)
                }
                if hypot(v.translation.width, v.translation.height) < 10 { return }
                sim.drag(v.location.x, v.location.y, speed: hypot(v.velocity.width, v.velocity.height))
            }
            .onEnded { v in
                pressed = false
                if hypot(v.translation.width, v.translation.height) < 10 {
                    if sim.tap(g, v.location.x, v.location.y) > 0 {
                        UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    }
                } else {
                    sim.dragEnd(g, v.location.y)
                }
                sim.lift()
            }
    }
}

enum RipplePondHeatmapPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255, opacity: a)
    }

    static let base = hex(0x0B2A2E)
    static let ink = hex(0xEAF3EC)
    static let soft = hex(0xEAF3EC, 0.66)
    static let levels = [hex(0x9DB3A2), hex(0xA9CF8E), hex(0x6FB06A), hex(0x3E8C4E), hex(0x1F6A3C)]
}

private struct RipplePondHeatmapHeader: View {
    private typealias P = RipplePondHeatmapPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 6) {
                Canvas { ctx, _ in
                    var leaf = Path()
                    leaf.move(to: CGPoint(x: 8, y: 14.5))
                    leaf.addCurve(to: CGPoint(x: 3.5, y: 2.5), control1: CGPoint(x: 3, y: 12), control2: CGPoint(x: 2, y: 7))
                    leaf.addCurve(to: CGPoint(x: 8, y: 14.5), control1: CGPoint(x: 8, y: 3), control2: CGPoint(x: 12.5, y: 6))
                    leaf.closeSubpath()
                    ctx.fill(leaf, with: .color(P.hex(0x8FD19A)))
                    var vein = Path()
                    vein.move(to: CGPoint(x: 8, y: 14))
                    vein.addCurve(to: CGPoint(x: 3.8, y: 3), control1: CGPoint(x: 6.5, y: 10), control2: CGPoint(x: 5.2, y: 7))
                    ctx.stroke(vein, with: .color(P.hex(0x2F6B3D)), style: StrokeStyle(lineWidth: 1, lineCap: .round))
                }
                .frame(width: 16, height: 16)
                Text("STILLPOND")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2)
                    .foregroundStyle(P.hex(0x9FD8A9))
            }
            .frame(height: 24)
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text("October")
                    .font(.system(size: 36, weight: .bold))
                    .kerning(-0.6)
                    .foregroundStyle(P.ink)
                Text("2026")
                    .font(.system(size: 20, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(P.soft)
            }
            .padding(.top, 8)
            Text(RipplePondHeatmapData.summary)
                .font(.system(size: 15, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(P.soft)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .padding(.top, 4)
        }
    }
}

/// The card's two lines, rolled: the new detail rolls up from below while the old one rolls up and
/// out (0.38 s, cubic-bezier(0.77, 0, 0.175, 1)), so old and new lines never sit on each other.
private struct RipplePondHeatmapCard: View {
    let shown: Int
    let previous: Int
    let k: Double
    private typealias P = RipplePondHeatmapPalette
    private let roll = 52.0

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 24, style: .continuous)
        ZStack(alignment: .topLeading) {
            lines(previous, y: -k)
            lines(shown, y: 1 - k)
        }
        .frame(maxWidth: .infinity, alignment: .topLeading)
        .frame(height: 52, alignment: .topLeading)
        .clipped()
        .padding(.horizontal, 20)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
        .background {
            shape.fill(P.hex(0x081E20, 0.66))
                .shadow(color: P.hex(0x000A0C, 0.35), radius: 20, y: 20)
                .shadow(color: P.hex(0x000A0C, 0.35), radius: 2, y: 2)
        }
        .overlay(shape.strokeBorder(P.hex(0xC8F0D7, 0.2), lineWidth: 0.5))
        .overlay {
            shape.strokeBorder(
                LinearGradient(colors: [.white.opacity(0.08), .white.opacity(0)],
                               startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.1)),
                lineWidth: 1)
        }
    }

    private func lines(_ id: Int, y: Double) -> some View {
        let d = RipplePondHeatmapData.detailFor(id)
        return VStack(alignment: .leading, spacing: 4) {
            Text(d.title)
                .font(.system(size: 20, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(P.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
            Text(d.sub)
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(P.soft)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .opacity(1 - min(1, abs(y) * 1.4))
        .offset(y: y * roll)
    }
}
