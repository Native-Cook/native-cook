import SwiftUI

/// Lava Lamp Loader — "Glowhaus", a music-sync screen whose loader is a lava lamp on a walnut shelf.
/// Wax heats on the pool at the bottom, swells, necks off and rises; it merges with the blob under
/// the cap, splits, and the cooled half sinks back. The tracks counter clicks up as it syncs.
///
/// Drawing: one Metal colour effect (LavaLampLoader.metal) over a full-screen rectangle draws the
/// wall, shelf, chrome and the metaball wax; its blob uniforms come from one pose computed per frame
/// (LavaLampLoaderMotion.swift). The header and the card are plain views over it.
///
/// Touch: a finger on the glass warms the wax there: a blob grows under it (about 1 s) and follows
/// it; the other blobs are pushed away from the finger and dragged along its motion. Let go and the
/// warmed blob rises to the cap. 2 s after the last touch the wax eases back to its resting pose
/// (0.9 s) and the loop starts again. Reduced motion: the resting lamp, no loop, no touch.
struct LavaLampLoader: View {
    /// Plays the 9 s rise-merge-sink loop of the wax.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = LavaLampLoaderEngine()

    private typealias P = LavaLampLoaderPalette

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
        let L = LavaLampLoaderLayout(width: size.width, height: size.height, insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            P.background

            TimelineView(.animation(paused: reduced)) { timeline in
                let f = engine.step(to: timeline.date, layout: L, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    lamp(f, L: L, size: size)
                    header(f, size: size, insets: insets)
                    card(spin: f.spin, size: size, insets: insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Text("Touch the glass to warm the wax")
                .font(.system(size: 12, weight: .semibold))
                .kerning(0.3)
                .foregroundStyle(P.inkFaint)
                .multilineTextAlignment(.center)
                .frame(width: size.width - 48)
                .offset(x: 24, y: L.shelf + 26)
                .allowsHitTesting(false)

            if !reduced {
                Color.clear
                    .contentShape(Rectangle())
                    .frame(width: size.width, height: size.height)
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                if !engine.fingerDown {
                                    engine.fingerDown = true
                                    engine.touchBegan(value.startLocation)
                                }
                                engine.touchMoved(value.location, velocity: value.velocity)
                            }
                            .onEnded { _ in
                                engine.fingerDown = false
                                engine.touchEnded()
                            }
                    )
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("A lava lamp. Touch the glass to warm the wax; drag to push the blobs. Syncing your library.")
    }

    private func lamp(_ f: LavaLampLoaderFrame, L: LavaLampLoaderLayout, size: CGSize) -> some View {
        let p = f.pose
        func b(_ i: Int) -> Shader.Argument { .float4(p[i * 4], p[i * 4 + 1], p[i * 4 + 2], p[i * 4 + 3]) }
        return Rectangle()
            .colorEffect(ShaderLibrary.lavaLampLoader(
                .float2(size.width, size.height),
                .float4(L.cx, L.top, L.H, L.k),
                b(0), b(1), b(2), b(3), b(4), b(5),
                .float(f.glow),
                .float(f.phase)
            ))
            .frame(width: size.width, height: size.height)
    }

    private func header(_ f: LavaLampLoaderFrame, size: CGSize, insets: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("GLOWHAUS")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2.2)
                .foregroundStyle(P.amber)
            Text("Syncing your library")
                .font(.system(size: 30, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(P.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            LavaLampLoaderCount(tracks: f.tracks)
                .padding(.top, 2)
            LavaLampLoaderBar(fill: min(1, f.tracks / LavaLampLoaderMotion.tracksAll))
                .padding(.top, 12)
        }
        .frame(width: size.width - 48, alignment: .leading)
        .offset(x: 24, y: insets.top + 18)
    }

    private func card(spin: Double, size: CGSize, insets: EdgeInsets) -> some View {
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return HStack(spacing: 12) {
            ZStack {
                LinearGradient(
                    stops: [
                        .init(color: LavaLampLoaderPalette.hex(0xFFB36B), location: 0),
                        .init(color: LavaLampLoaderPalette.hex(0xE0482F), location: 0.55),
                        .init(color: LavaLampLoaderPalette.hex(0x6E1838), location: 1),
                    ],
                    startPoint: .topLeading,
                    endPoint: .bottomTrailing
                )
                Circle()
                    .fill(Color(red: 1, green: 236 / 255, blue: 200 / 255).opacity(0.55))
                    .frame(width: 22, height: 22)
                    .offset(y: 5)
            }
            .frame(width: 48, height: 48)
            .clipShape(RoundedRectangle(cornerRadius: 12, style: .continuous))

            VStack(alignment: .leading, spacing: 3) {
                Text("Low Tide Hymns")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                Text("Marlow Fenn · 14 of 22 tracks")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(P.inkSoft)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)

            ZStack {
                Circle().fill(P.amber.opacity(0.12))
                Image(systemName: "arrow.triangle.2.circlepath")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(P.amber)
                    .rotationEffect(.radians(spin))
            }
            .frame(width: 36, height: 36)
        }
        .padding(.horizontal, 14)
        .frame(width: size.width - 32, height: 76)
        .background {
            shape
                .fill(Color(red: 38 / 255, green: 20 / 255, blue: 22 / 255).opacity(0.92))
                .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.42), radius: 18, y: 16)
        }
        .overlay {
            shape.strokeBorder(Color(red: 1, green: 200 / 255, blue: 170 / 255).opacity(0.08), lineWidth: 1)
        }
        .overlay {
            // The inset highlight along the card's top edge.
            shape
                .inset(by: 1)
                .stroke(
                    LinearGradient(
                        colors: [Color(red: 1, green: 230 / 255, blue: 210 / 255).opacity(0.08), .clear],
                        startPoint: .top,
                        endPoint: .init(x: 0.5, y: 0.25)
                    ),
                    lineWidth: 1
                )
        }
        .offset(x: 16, y: size.height - insets.bottom - 16 - 76)
    }
}

enum LavaLampLoaderPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: a)
    }

    static let background = hex(0x1A0D10)
    static let ink = hex(0xF6E7DA)
    static let inkSoft = hex(0xF6E7DA, 0.62)
    static let inkFaint = hex(0xF6E7DA, 0.4)
    static let amber = hex(0xFF9A4A)
}

/// The odometer: the ones turn with the value, higher places as the one below passes 9.
private struct LavaLampLoaderCount: View {
    let tracks: Double

    static let digitH = 22.0

    var body: some View {
        HStack(spacing: 0) {
            column(1000)
            Text(",")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(LavaLampLoaderPalette.ink)
            column(100)
            column(10)
            column(1)
            Text(" / 2,310 tracks")
                .font(.system(size: 16, weight: .medium).monospacedDigit())
                .foregroundStyle(LavaLampLoaderPalette.inkSoft)
        }
        .frame(height: Self.digitH)
    }

    private func column(_ place: Double) -> some View {
        let v = max(0, tracks)
        let m = v / place
        let base = floor(m)
        let pos = place == 1 ? m : base + max(0, (m - base) * 10 - 9)
        let shift = pos.truncatingRemainder(dividingBy: 10)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 16, weight: .semibold).monospacedDigit())
                    .foregroundStyle(LavaLampLoaderPalette.ink)
                    .frame(width: 10, height: Self.digitH)
            }
        }
        .offset(y: -shift * Self.digitH)
        .frame(width: 10, height: Self.digitH, alignment: .top)
        .clipped()
    }
}

private struct LavaLampLoaderBar: View {
    let fill: Double

    var body: some View {
        ZStack(alignment: .leading) {
            Capsule().fill(LavaLampLoaderPalette.ink.opacity(0.12))
            Capsule()
                .fill(LavaLampLoaderPalette.amber)
                .scaleEffect(x: fill, y: 1, anchor: .leading)
        }
        .frame(height: 4)
        .clipShape(Capsule())
    }
}
