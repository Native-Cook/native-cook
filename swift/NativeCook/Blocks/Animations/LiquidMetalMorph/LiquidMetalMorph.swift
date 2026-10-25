import SwiftUI

/// "Forge", a mode switch whose icon is a blob of liquid chrome.
///
/// A graphite backdrop and a mercury blob drawn by one Metal shader (LiquidMetalMorph.metal): a
/// signed distance field shaded as chrome reflecting a studio gradient, with a Fresnel rim, a key
/// highlight and a faint chromatic split. It morphs drop → four-point star → ring → drop; during
/// each morph four satellite droplets pinch off and merge back. Under it the state rolls
/// ("Melting" / "Casting" / "Polishing") with three progress dots; the bottom holds a segmented
/// control (Drop, Star, Ring) whose pill slides with the morph.
///
/// autoPlay (loop 9 s, LiquidMetalMorphMotion.swift): 0–1.6 rest (drop, slow wobble), 1.6–3.2
/// drop → star, 4.6–6.2 star → ring, 7.0–8.4 ring → drop, a 0.6 s jiggle after each morph,
/// 8.4–9 rest.
///
/// Touch: drag from inside the blob to pull a droplet out on a smooth-min tether (light haptic at
/// 120 pt); let go and it flies home on a spring with the finger's velocity. Tap a segment to
/// morph on a playful spring. 2 s after the last touch the blob eases back to the drop (0.85 s)
/// and the loop starts again. Reduced motion: no loop, no wobble; the droplet and the segments
/// still answer the finger.
struct LiquidMetalMorph: View {
    /// Plays the 9 s morph loop until touched.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = LiquidMetalMorphEngine()
    /// Keeps the timeline running after a touch while autoPlay is off and motion is reduced.
    @State private var awake = false

    private typealias M = LiquidMetalMorphMetrics

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let width = size.width
        let height = size.height
        let cx = width / 2
        let cy = height * 0.41
        let R = min(width * 0.23, 96, height * 0.11)
        // The readings row sits above the footer, where there is room for it.
        let statusTop = cy + R * 2.05
        let statusBottom = statusTop + 80
        let footerTop = height - insets.bottom - 24 - 84
        let showReadings = footerTop - statusBottom > 76
        let paused = !auto && reduced && !awake

        return TimelineView(.animation(paused: paused)) { timeline in
            let f = engine.step(to: timeline.date, R: R, auto: auto, reduced: reduced)
            let _ = sleepWhenIdle(auto: auto, reduced: reduced)
            ZStack(alignment: .topLeading) {
                Rectangle()
                    .colorEffect(ShaderLibrary.liquidMetalMorph(
                        .float2(max(1, width), max(1, height)),
                        .float2(cx, cy),
                        .float(max(R, 1)),
                        .float3(f.w.0, f.w.1, f.w.2),
                        .float4(f.wob.0, f.wob.1, f.wob.2, f.wob.3),
                        .floatArray(f.sats),
                        .float4(f.finger.0, f.finger.1, f.finger.2, f.finger.3)
                    ))
                    .frame(width: width, height: height)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                awake = true
                                engine.dragChanged(value.location, center: CGPoint(x: cx, y: cy), R: R)
                            }
                            .onEnded { value in
                                engine.dragEnded(velocity: value.velocity)
                            }
                    )
                    .accessibilityElement()
                    .accessibilityLabel("A blob of liquid metal. Drag from it to pull out a droplet.")

                header
                    .padding(.horizontal, 24)
                    .frame(width: width, alignment: .leading)
                    .offset(y: insets.top + 20)
                    .allowsHitTesting(false)

                status(f)
                    .padding(.horizontal, 24)
                    .frame(width: width)
                    .offset(y: statusTop)
                    .allowsHitTesting(false)

                if showReadings {
                    readings
                        .padding(.horizontal, 24)
                        .frame(width: width)
                        .offset(y: footerTop - 24 - 56)
                        .allowsHitTesting(false)
                }

                footer(f)
                    .padding(.horizontal, 24)
                    .frame(width: width, height: height - insets.bottom - 24, alignment: .bottom)
            }
            .frame(width: width, height: height, alignment: .topLeading)
        }
        .background(M.background)
        .clipped()
    }

    /// Lets the timeline pause once the blob has settled with nothing to play (reduced motion).
    private func sleepWhenIdle(auto: Bool, reduced: Bool) -> Bool {
        if awake && !auto && reduced && engine.isIdle {
            Task { @MainActor in if engine.isIdle { awake = false } }
        }
        return true
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("FORGE")
                .font(.system(size: 12, weight: .bold))
                .kerning(3)
                .foregroundStyle(M.faint)
            Text("Casting your workspace")
                .font(.system(size: 28, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(M.ink)
            Text("Liquid alloy preset · 3 modes")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(M.mute)
        }
    }

    private func status(_ f: LiquidMetalMorphFrame) -> some View {
        let shown = [f.labels.0, f.labels.1, f.labels.2]
        let weights = [f.w.0, f.w.1, f.w.2]
        return VStack(spacing: 16) {
            ZStack(alignment: .top) {
                ForEach(0..<3, id: \.self) { i in
                    let v = shown[i]
                    VStack(spacing: 4) {
                        Text(M.states[i].title)
                            .font(.system(size: 24, weight: .semibold))
                            .kerning(-0.2)
                            .foregroundStyle(M.ink)
                        Text(M.states[i].line)
                            .font(.system(size: 13, weight: .medium))
                            .monospacedDigit()
                            .foregroundStyle(M.mute)
                    }
                    .frame(maxWidth: .infinity)
                    // 0 = waiting below, 1 = shown, 2 = gone above.
                    .opacity(v <= 1 ? v : 2 - v)
                    .offset(y: (1 - v) * 14)
                }
            }
            .frame(height: 56, alignment: .top)
            HStack(spacing: 12) {
                ForEach(0..<3, id: \.self) { i in
                    let k = M.fade(weights[i])
                    Circle()
                        .fill(M.ink)
                        .frame(width: 6, height: 6)
                        .scaleEffect(x: 1 + 1.6 * k, y: 1)
                        .opacity(0.3 + 0.7 * k)
                }
            }
        }
    }

    private var readings: some View {
        HStack(spacing: 0) {
            ForEach(0..<M.readings.count, id: \.self) { i in
                let (value, label) = M.readings[i]
                VStack(spacing: 2) {
                    Text(value)
                        .font(.system(size: 15, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.ink)
                    Text(label)
                        .font(.system(size: 11, weight: .semibold))
                        .kerning(0.4)
                        .foregroundStyle(M.faint)
                }
                .frame(maxWidth: .infinity)
            }
        }
        .frame(height: 56)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.white.opacity(0.04))
                .overlay(
                    RoundedRectangle(cornerRadius: 18, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.09), lineWidth: 0.5)
                )
        )
    }

    private func footer(_ f: LiquidMetalMorphFrame) -> some View {
        let weights = [f.w.0, f.w.1, f.w.2]
        return VStack(spacing: 16) {
            ZStack(alignment: .topLeading) {
                Capsule()
                    .fill(M.pill)
                    .overlay(
                        Capsule()
                            .strokeBorder(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.7), Color.white.opacity(0)],
                                    startPoint: .top,
                                    endPoint: UnitPoint(x: 0.5, y: 0.12)
                                ),
                                lineWidth: 1
                            )
                    )
                    .shadow(color: .black.opacity(0.35), radius: 1.5, y: 1)
                    .frame(width: M.segmentW, height: 40)
                    .offset(x: 4 + (f.w.1 + 2 * f.w.2) * M.segmentW, y: 4)
                    .allowsHitTesting(false)
                HStack(spacing: 0) {
                    ForEach(0..<3, id: \.self) { i in
                        Button {
                            awake = true
                            engine.choose(i)
                        } label: {
                            Text(M.segments[i])
                                .font(.system(size: 15, weight: .semibold))
                                .foregroundStyle(M.segmentInk(M.fade(weights[i])))
                                .frame(width: M.segmentW, height: 40)
                                .contentShape(Rectangle())
                        }
                        .buttonStyle(.plain)
                        .accessibilityLabel("\(M.segments[i]) shape")
                    }
                }
                .padding(4)
            }
            .background(
                RoundedRectangle(cornerRadius: 24, style: .continuous)
                    .fill(M.segmentedFill)
                    .overlay(
                        RoundedRectangle(cornerRadius: 24, style: .continuous)
                            .strokeBorder(Color.white.opacity(0.1), lineWidth: 0.5)
                    )
                    .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.28), radius: 16, y: 12)
            )
            Text("Pull a droplet out of the metal")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(M.faint)
                .allowsHitTesting(false)
        }
    }
}

enum LiquidMetalMorphMetrics {
    static let background = Color(hex: 0x16171b)
    static let ink = Color(hex: 0xe9ebf0)
    static let mute = Color(hex: 0xe9ebf0, opacity: 0.56)
    static let faint = Color(hex: 0xe9ebf0, opacity: 0.32)
    static let pill = Color(hex: 0xd9dde5)
    static let segmentedFill = Color.rgba(40, 42, 49, 0.92)
    static let segmentW = 96.0

    static let states: [(title: String, line: String)] = [
        ("Melting", "Drop · alloy at 356 °C"),
        ("Casting", "Star · cooling to 210 °C"),
        ("Polishing", "Ring · buffing to a mirror"),
    ]
    static let segments = ["Drop", "Star", "Ring"]
    static let readings: [(String, String)] = [
        ("356 °C", "alloy"),
        ("1.5 cP", "viscosity"),
        ("0.8 s", "per cast"),
    ]

    /// A shape's weight past 35 %, eased in over the next 40 %.
    static func fade(_ w: Double) -> Double {
        min(max((w - 0.35) / 0.4, 0), 1)
    }

    /// Segment text from idle #9095a1 to on #17181d.
    static func segmentInk(_ k: Double) -> Color {
        let a = (0x90, 0x95, 0xa1)
        let b = (0x17, 0x18, 0x1d)
        func mix(_ x: Int, _ y: Int) -> Double { (Double(x) + (Double(y) - Double(x)) * k) / 255 }
        return Color(red: mix(a.0, b.0), green: mix(a.1, b.1), blue: mix(a.2, b.2))
    }
}
