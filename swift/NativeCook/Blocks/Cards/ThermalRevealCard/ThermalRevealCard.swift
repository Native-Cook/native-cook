import SwiftUI

/// A travel club's membership pass printed in thermochromic ink.
///
/// A dark navy screen ("Hidden Coves of Aruna"), and a portrait pass of dark ink with a fine
/// guilloché print, a gloss band and a lit top edge. Warmth turns the ink deep violet → magenta →
/// orange → yellow and then clear, showing a map drawn in code underneath
/// (ThermalRevealCardArt.swift): islands, a dashed route, three coves marked X, a compass and place
/// names. One Metal layer effect (ThermalRevealCard.metal) draws the card over the map image.
///
/// autoPlay (loop 9 s, ThermalRevealCardMotion.swift): 0–1.5 rest (dark card, gloss drifting, a
/// ±1.6° sway), 1.5–5.5 a thumb sweeps an S-curve down the card through the three coves, 4.6–5.5
/// the whole card warms, 5.5–6.4 peak, 6.4–8.2 it cools from the edges in, 8.2–9 rest.
///
/// Touch: drag on the card to warm it (a resting thumb keeps warming the same spot); a selection
/// tick when a cove is uncovered. Drag beside the card to tilt it (up to 8°, soft limit), the gloss
/// follows; it springs back on release. 2 s after the last touch the warmth fades (0.8 s) and the
/// loop restarts. Reduced motion: the loop stops on the revealed map.
struct ThermalRevealCard: View {
    /// Plays the 9 s warm-and-cool loop until touched.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = ThermalRevealCardEngine()
    /// Keeps the timeline running after a touch while autoPlay is off.
    @State private var awake = false

    private typealias M = ThermalRevealCardMetrics

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
        let W = size.width
        let H = size.height
        let cardW = min(W - 48, 345)
        let cardTop = insets.top + 168
        let cardH = max(0, (min(cardW * 1.3, H - cardTop - insets.bottom - 196)).rounded())
        let cardX = (W - cardW) / 2

        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x18202e), location: 0),
                    .init(color: Color(hex: 0x0f141d), location: 0.5),
                    .init(color: Color(hex: 0x0a0d13), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: W, height: H)

            nav
                .padding(.horizontal, 16)
                .frame(width: W, height: 44)
                .offset(y: insets.top + 6)

            header
                .padding(.horizontal, 24)
                .frame(width: W, alignment: .leading)
                .offset(y: insets.top + 62)
                .allowsHitTesting(false)

            if cardH > 0 {
                cardStage(W: W, cardX: cardX, cardW: cardW, cardH: cardH)
                    .offset(y: cardTop - M.stagePad)

                stats
                    .padding(.horizontal, 24)
                    .frame(width: W)
                    .offset(y: cardTop + cardH + 22)
                    .allowsHitTesting(false)
            }

            cta
                .padding(.horizontal, 24)
                .frame(width: W, height: H - insets.bottom - 20, alignment: .bottom)
        }
        .frame(width: W, height: H, alignment: .topLeading)
        .background(Color(hex: 0x0e1219))
        .clipped()
    }

    // MARK: Card

    private func cardStage(W: Double, cardX: Double, cardW: Double, cardH: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let map = ThermalRevealCardArt.map(width: cardW, height: cardH, scale: min(displayScale, 3))
        let paused = !auto && !awake
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = engine.step(to: timeline.date, w: cardW, h: cardH, auto: auto, reduced: reduced)
            let _ = sleepWhenIdle(auto: auto)
            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: W, height: cardH + M.stagePad * 2)
                card(f, map: map, cardW: cardW, cardH: cardH)
                    .rotation3DEffect(.degrees(f.ry), axis: (x: 0, y: 1, z: 0), perspective: max(cardW, cardH) / 900)
                    .rotation3DEffect(.degrees(f.rx), axis: (x: 1, y: 0, z: 0), perspective: max(cardW, cardH) / 900)
                    .offset(x: cardX, y: M.stagePad)
                    .allowsHitTesting(false)
            }
            .frame(width: W, height: cardH + M.stagePad * 2, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        awake = true
                        let p = CGPoint(x: value.location.x - cardX, y: value.location.y - M.stagePad)
                        engine.dragChanged(p, translation: value.translation, w: cardW, h: cardH)
                    }
                    .onEnded { value in
                        engine.dragEnded(velocity: value.velocity)
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Membership card in thermochromic ink. Drag on it to warm the ink and reveal the map; drag beside it to tilt it.")
        }
    }

    /// Lets the timeline pause once the card has cooled and settled with autoPlay off.
    private func sleepWhenIdle(auto: Bool) -> Bool {
        if awake && !auto && engine.isIdle {
            Task { @MainActor in if engine.isIdle { awake = false } }
        }
        return true
    }

    private func card(_ f: ThermalRevealCardFrame, map: UIImage?, cardW: Double, cardH: Double) -> some View {
        ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(M.cardInk)
                .shadow(color: .black.opacity(0.35), radius: 1.5, y: 2)
                .shadow(color: .black.opacity(0.42), radius: 22, y: 20)
            if let map {
                Image(uiImage: map)
                    .resizable()
                    .frame(width: cardW, height: cardH)
                    .layerEffect(
                        ShaderLibrary.thermalRevealCard(
                            .float2(max(cardW, 1), max(cardH, 1)),
                            .floatArray(f.heat),
                            .float(f.glob),
                            .float2(f.gloss, 1),
                            .float(22)
                        ),
                        maxSampleOffset: .zero
                    )
            }
            cardTop
                .padding(.horizontal, 20)
                .frame(width: cardW, alignment: .leading)
                .offset(y: 18)
            cardBottom
                .padding(.horizontal, 20)
                .frame(width: cardW, height: cardH - 18, alignment: .bottomLeading)
        }
        .frame(width: cardW, height: cardH, alignment: .topLeading)
    }

    private var cardTop: some View {
        HStack(alignment: .top) {
            VStack(alignment: .leading, spacing: 2) {
                Text("ARUNA")
                    .font(.system(size: 17, weight: .heavy))
                    .kerning(5)
                    .foregroundStyle(M.ink)
                    .shadow(color: .black.opacity(0.45), radius: 2)
                Text("COVE SOCIETY")
                    .font(.system(size: 9, weight: .bold))
                    .kerning(2.6)
                    .foregroundStyle(M.mute)
                    .shadow(color: .black.opacity(0.45), radius: 1.5)
            }
            Spacer(minLength: 8)
            Text("No. 0418")
                .font(.custom("Menlo", fixedSize: 12))
                .monospacedDigit()
                .foregroundStyle(M.ink)
                .shadow(color: .black.opacity(0.45), radius: 1.5)
        }
    }

    private var cardBottom: some View {
        HStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 2) {
                Text("MARA ELLIS")
                    .font(.system(size: 13, weight: .bold))
                    .kerning(1.6)
                    .foregroundStyle(M.ink)
                    .shadow(color: .black.opacity(0.5), radius: 1.5)
                Text("Explorer · since 2023")
                    .font(.system(size: 11, weight: .medium))
                    .foregroundStyle(M.mute)
                    .shadow(color: .black.opacity(0.5), radius: 1.5)
            }
            Spacer(minLength: 8)
            HStack(spacing: 6) {
                ThermalRevealCardThermometer()
                    .frame(width: 8, height: 16)
                Text("Reveals at 31 °C")
                    .font(.system(size: 10, weight: .semibold))
                    .foregroundStyle(M.mute)
                    .shadow(color: .black.opacity(0.5), radius: 1.5)
            }
        }
    }

    // MARK: Screen

    private var nav: some View {
        HStack {
            Button {} label: {
                Canvas { c, _ in
                    var p = Path()
                    p.move(to: CGPoint(x: 9, y: 2))
                    p.addLine(to: CGPoint(x: 4, y: 7))
                    p.addLine(to: CGPoint(x: 9, y: 12))
                    c.stroke(p, with: .color(M.ink), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                }
                .frame(width: 14, height: 14)
                .modifier(ThermalRevealCardNavButton())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            Spacer()
            Text("Members")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(M.mute)
            Spacer()
            Button {} label: {
                HStack(spacing: 2.8) {
                    ForEach(0..<3, id: \.self) { _ in
                        Circle().fill(M.ink).frame(width: 3.2, height: 3.2)
                    }
                }
                .frame(width: 16, height: 4)
                .modifier(ThermalRevealCardNavButton())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("More")
        }
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("COVE SOCIETY · SEASON 4")
                .font(.system(size: 11, weight: .bold))
                .kerning(2)
                .foregroundStyle(M.amber)
            Text("Hidden Coves of Aruna")
                .font(.system(size: 28, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(M.ink)
            Text("Warm the ink with your thumb to read the map")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(M.mute)
        }
    }

    private var stats: some View {
        HStack(spacing: 0) {
            ForEach(0..<M.stats.count, id: \.self) { i in
                VStack(spacing: 2) {
                    Text(M.stats[i].0)
                        .font(.system(size: 17, weight: .semibold))
                        .monospacedDigit()
                        .foregroundStyle(M.ink)
                    Text(M.stats[i].1)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(M.faint)
                }
                .frame(maxWidth: .infinity)
            }
        }
    }

    private var cta: some View {
        Button {} label: {
            Text("Start the route")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(Color(hex: 0x2a1a0c))
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(
                    Capsule()
                        .fill(
                            LinearGradient(
                                stops: [
                                    .init(color: Color(hex: 0xf7cf7a), location: 0),
                                    .init(color: Color(hex: 0xf2a94e), location: 0.55),
                                    .init(color: Color(hex: 0xe9853a), location: 1),
                                ],
                                startPoint: .topLeading,
                                endPoint: .bottomTrailing
                            )
                        )
                        .overlay(
                            Capsule().strokeBorder(
                                LinearGradient(
                                    colors: [Color.white.opacity(0.45), Color.white.opacity(0)],
                                    startPoint: .top,
                                    endPoint: UnitPoint(x: 0.5, y: 0.1)
                                ),
                                lineWidth: 1
                            )
                        )
                        .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                        .shadow(color: Color.rgba(233, 133, 58, 0.28), radius: 12, y: 10)
                )
        }
        .buttonStyle(.plain)
    }
}

/// The round glass button of the nav bar.
private struct ThermalRevealCardNavButton: ViewModifier {
    func body(content: Content) -> some View {
        content
            .frame(width: 40, height: 40)
            .background(
                Circle()
                    .fill(Color.white.opacity(0.07))
                    .overlay(Circle().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5))
            )
            .contentShape(Circle())
    }
}

/// The small thermometer by "Reveals at 31 °C" (an 8 × 16 pt drawing).
struct ThermalRevealCardThermometer: View {
    var body: some View {
        Canvas { c, _ in
            var p = Path()
            p.move(to: CGPoint(x: 2.2, y: 3))
            p.addArc(center: CGPoint(x: 4, y: 3), radius: 1.8, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
            p.addLine(to: CGPoint(x: 5.8, y: 9.6))
            p.addArc(
                center: CGPoint(x: 4, y: 12),
                radius: 3,
                startAngle: .radians(atan2(-2.4, 1.8)),
                endAngle: .radians(atan2(-2.4, -1.8) + 2 * .pi),
                clockwise: false
            )
            p.closeSubpath()
            c.stroke(p, with: .color(ThermalRevealCardMetrics.ink), lineWidth: 1)
            c.fill(Path(ellipseIn: CGRect(x: 2.4, y: 10.6, width: 3.2, height: 3.2)), with: .color(ThermalRevealCardMetrics.amber))
        }
    }
}

enum ThermalRevealCardMetrics {
    static let ink = Color(hex: 0xf2ece2)
    static let mute = Color(hex: 0xf2ece2, opacity: 0.6)
    static let faint = Color(hex: 0xf2ece2, opacity: 0.38)
    static let amber = Color(hex: 0xf2b35a)
    static let cardInk = Color(hex: 0x1b1f2b)
    /// The touch area reaches this far above and below the card, for tilting beside it.
    static let stagePad = 28.0
    static let stats: [(String, String)] = [("6", "coves"), ("14.8 km", "route"), ("06:12", "low tide")]
}
