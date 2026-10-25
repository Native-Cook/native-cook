import SwiftUI

/// Stained Glass Splash — the launch of "Chapel", a meditation app. A rose window waits dim in a
/// stone wall; the sun crosses it and the panes light one by one (outer rings first, the gold rose of
/// the app's mark last), throwing their colours down a dusty shaft onto the flagstones. Then the light
/// floods out of the window into cream and the home screen rises through it.
///
/// Drawing: one Metal colour effect (StainedGlassSplash.metal) over a full-screen rectangle for wall,
/// window, beams, dust, floor and the flood; its uniforms come from one scene computed per frame
/// (StainedGlassSplashMotion.swift). The wordmark and the home screen are views whose opacity and
/// offset read the same scene.
///
/// Touch: during the splash, drag across the window to move the sun: every pane it passes lights and
/// stays lit. Light all of it and the splash carries on into the flood and the home screen;
/// otherwise, 2 s after the finger lifts, the panes go dark again (0.9 s) and the loop restarts.
/// Reduced motion: no loop; the home screen on cream.
struct StainedGlassSplash: View {
    /// Plays the 9 s light-floor-flood-home loop.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = StainedGlassSplashEngine()

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
        let L = StainedGlassSplashLayout(width: size.width, height: size.height, insetTop: insets.top)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            StainedGlassSplashPalette.background

            TimelineView(.animation(paused: reduced)) { timeline in
                let f = engine.step(to: timeline.date, layout: L, auto: auto, reduced: reduced)
                let s = f.scene
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.stainedGlassSplash(
                            .float2(size.width, size.height),
                            .float3(L.cx, L.cy, L.R),
                            .float3(s.lo, s.hi, s.beam),
                            .float(s.keyMix),
                            .float(s.flood),
                            .float(f.phase)
                        ))
                        .frame(width: size.width, height: size.height)

                    StainedGlassSplashMark()
                        .frame(width: size.width)
                        .offset(y: 6 * (1 - s.mark))
                        .opacity(s.mark * (1 - min(1, s.flood * 2.5)))
                        .offset(y: L.cy + L.R * 1.04 + 18)

                    StainedGlassSplashHome(top: insets.top, bottom: insets.bottom)
                        .frame(width: size.width, height: size.height)
                        .offset(y: 12 * (1 - s.home) - 6 * s.homeOut)
                        .opacity(s.home * (1 - s.homeOut))
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
                            .onChanged { value in
                                if !engine.fingerDown {
                                    engine.fingerDown = true
                                    engine.touchBegan(value.startLocation)
                                }
                                engine.touchMoved(value.location)
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
        .accessibilityLabel("Chapel. A rose window. Drag across it to move the sun and light the panes.")
    }
}

enum StainedGlassSplashPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: a)
    }

    static let background = hex(0x15110E)
    static let ink = hex(0x2B2119)
    static let inkSoft = hex(0x2B2119, 0.62)
    static let ruby = hex(0x9B2D2B)
    static let creamCard = hex(0xFFFBF3, 0.78)
    static let shadow = Color(red: 60 / 255, green: 30 / 255, blue: 20 / 255)
}

/// The wordmark under the window.
private struct StainedGlassSplashMark: View, Equatable {
    var body: some View {
        VStack(spacing: 6) {
            Text("Chapel")
                .font(.custom("Georgia-Italic", size: 44))
                .kerning(0.5)
                .foregroundStyle(StainedGlassSplashPalette.hex(0xF4E4C8))
            Text("QUIET MINUTES, EVERY DAY")
                .font(.system(size: 11, weight: .bold))
                .kerning(2.4)
                .foregroundStyle(StainedGlassSplashPalette.hex(0xF4E4C8, 0.62))
        }
    }
}

/// The home screen the splash opens onto (static: it only fades and rises).
private struct StainedGlassSplashHome: View, Equatable {
    let top: Double
    let bottom: Double

    private typealias P = StainedGlassSplashPalette
    private static let week: [(String, Double)] = [("M", 0.55), ("T", 0.8), ("W", 0.35), ("T", 0.95), ("F", 0.7), ("S", 0), ("S", 0)]

    var body: some View {
        VStack(alignment: .leading, spacing: 16) {
            VStack(alignment: .leading, spacing: 4) {
                Text("CHAPEL")
                    .font(.system(size: 11, weight: .heavy))
                    .kerning(2.2)
                    .foregroundStyle(P.ruby)
                Text("Good evening, Mira")
                    .font(.custom("Georgia", size: 32))
                    .kerning(-0.3)
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
                Text("Day 14 · 86 minutes this week")
                    .font(.system(size: 14, weight: .medium).monospacedDigit())
                    .foregroundStyle(P.inkSoft)
            }
            today
            Text("Keep going")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(P.ink)
                .padding(.top, 4)
            HStack(spacing: 10) {
                tile("Wind down", "8 min", [0xE7B45A, 0xC9733A, 0x8E3A2E])
                tile("Morning light", "10 min", [0x9FCB9A, 0x3E8A6A, 0x22524E])
                tile("Steady focus", "5 min", [0xB9A3E0, 0x6C55B0, 0x352A6E])
            }
            weekCard
            Spacer(minLength: 0)
            tabs
        }
        .padding(.horizontal, 20)
        .padding(.top, top + 18)
        .padding(.bottom, bottom + 10)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func gradient(_ c: [UInt32]) -> LinearGradient {
        LinearGradient(
            stops: [.init(color: P.hex(c[0]), location: 0), .init(color: P.hex(c[1]), location: 0.55), .init(color: P.hex(c[2]), location: 1)],
            startPoint: .topLeading,
            endPoint: .bottomTrailing
        )
    }

    private var today: some View {
        let cream = Color(red: 1, green: 240 / 255, blue: 220 / 255)
        return VStack(alignment: .leading, spacing: 4) {
            Text("TODAY'S SIT")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2)
                .foregroundStyle(cream.opacity(0.72))
            Text("Stillwater")
                .font(.custom("Georgia", size: 30))
                .foregroundStyle(P.hex(0xFFF6E8))
            Text("12 min · Breath and stillness")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(cream.opacity(0.8))
            HStack(spacing: 12) {
                ZStack {
                    Circle().fill(P.hex(0xFFF6E8))
                    Image(systemName: "play.fill")
                        .font(.system(size: 16))
                        .foregroundStyle(P.hex(0x2B3F8F))
                        .offset(x: 1.5)
                }
                .frame(width: 44, height: 44)
                Text("Guided by Ines Varga")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(cream.opacity(0.8))
            }
            .padding(.top, 14)
        }
        .padding(20)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(gradient([0x2B3F8F, 0x4B2A78, 0x7A2440]), in: RoundedRectangle(cornerRadius: 26, style: .continuous))
        .shadow(color: P.shadow.opacity(0.18), radius: 1, y: 1)
        .shadow(color: P.shadow.opacity(0.22), radius: 17, y: 16)
    }

    private func tile(_ title: String, _ detail: String, _ c: [UInt32]) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            RoundedRectangle(cornerRadius: 12, style: .continuous)
                .fill(gradient(c))
                .frame(height: 64)
                .padding(.bottom, 4)
            Text(title)
                .font(.system(size: 14, weight: .bold))
                .foregroundStyle(P.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.85)
            Text(detail)
                .font(.system(size: 12, weight: .medium).monospacedDigit())
                .foregroundStyle(P.inkSoft)
        }
        .padding(10)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(P.creamCard, in: RoundedRectangle(cornerRadius: 18, style: .continuous))
        .shadow(color: P.shadow.opacity(0.1), radius: 1, y: 1)
        .shadow(color: P.shadow.opacity(0.08), radius: 10, y: 8)
    }

    private var weekCard: some View {
        VStack(spacing: 10) {
            HStack(alignment: .firstTextBaseline) {
                Text("Your week")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(P.ink)
                Spacer()
                Text("86 min")
                    .font(.system(size: 13, weight: .semibold).monospacedDigit())
                    .foregroundStyle(P.inkSoft)
            }
            HStack(spacing: 10) {
                ForEach(0..<Self.week.count, id: \.self) { i in
                    let d = Self.week[i]
                    VStack(spacing: 6) {
                        GeometryReader { g in
                            ZStack(alignment: .bottom) {
                                Capsule().fill(P.ruby.opacity(0.08))
                                Capsule()
                                    .fill(i == 4 ? P.ruby : P.ruby.opacity(0.32))
                                    .frame(height: g.size.height * d.1)
                            }
                        }
                        .frame(width: 12)
                        Text(d.0)
                            .font(.system(size: 11, weight: .bold))
                            .foregroundStyle(i == 4 ? P.ruby : P.inkSoft)
                    }
                    .frame(maxWidth: .infinity)
                }
            }
            .frame(height: 56)
        }
        .padding(14)
        .background(P.creamCard, in: RoundedRectangle(cornerRadius: 20, style: .continuous))
        .shadow(color: P.shadow.opacity(0.1), radius: 1, y: 1)
        .shadow(color: P.shadow.opacity(0.08), radius: 10, y: 8)
    }

    private var tabs: some View {
        HStack {
            tab("sun.horizon.fill", "Today", on: true)
            tab("square.grid.2x2", "Library")
            tab("book.closed", "Journal")
            tab("person.crop.circle", "You")
        }
        .frame(maxWidth: .infinity)
        .frame(height: 60)
        .background(P.creamCard, in: Capsule())
        .shadow(color: P.shadow.opacity(0.1), radius: 1, y: 1)
        .shadow(color: P.shadow.opacity(0.1), radius: 12, y: 10)
    }

    private func tab(_ icon: String, _ label: String, on: Bool = false) -> some View {
        let tint = on ? P.ruby : P.ink.opacity(0.45)
        return VStack(spacing: 2) {
            Image(systemName: icon)
                .font(.system(size: 18))
            Text(label)
                .font(.system(size: 11, weight: .bold))
        }
        .foregroundStyle(tint)
        .frame(minWidth: 60)
        .frame(maxWidth: .infinity)
    }

    static func == (a: Self, b: Self) -> Bool { a.top == b.top && a.bottom == b.bottom }
}
