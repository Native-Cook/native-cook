import SwiftUI

/// "Ember", a focus timer whose progress is a pillar candle burning down.
///
/// A dark plaster wall and a walnut table lit warm by the flame, an ivory pillar candle on a brass
/// dish with a molten pool, a wick, wax drips, a flickering teardrop flame with a glow, and smoke
/// when it goes out, all in one Metal shader (CandleBurnTimer.metal). The wax height is the time
/// left. Under it the time rolls like an odometer (25:00), a status line, and Pause / Reset.
///
/// autoPlay (loop 9 s, compressed time, CandleBurnTimerMotion.swift): 0–1.4 rest (25:00, full
/// candle, lit), 1.4–6.0 burn 25:00 → 00:00 (the wax shrinks to a 24 % stub; drips run down at 2.4
/// and 4.2), 6.0–6.35 the flame goes out and smoke curls up, 6.6–7.4 "Session done", 7.4–8.4 a
/// fresh candle rises as the time rolls back to 25:00, 8.4 it catches (settled by 9.0).
///
/// Touch: drag sideways for wind: the flame leans with the drag's speed on a wobbly spring; a flick
/// faster than 1500 pt/s blows it out (light haptic); tap the candle to relight. Pause / Resume and
/// Reset. The timer runs in real time. 2 s after the last touch the candle resets and relights and
/// the loop starts again. Reduced motion: no loop, a still full candle at 25:00.
struct CandleBurnTimer: View {
    /// Plays the 9 s compressed burn loop until touched.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = CandleBurnTimerEngine()
    /// Keeps the timeline running after a touch under reduced motion.
    @State private var awake = false

    private typealias M = CandleBurnTimerMetrics
    private typealias T = CandleBurnTimerTuning

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
        let W = size.width
        let H = size.height
        let cx = W / 2
        let baseY = (H * 0.62).rounded()
        let full = max(80, min(250, baseY - insets.top - 236))
        // Only the still first frame of reduced motion may pause the clock.
        let paused = autoPlay && !auto && !awake

        return TimelineView(.animation(paused: paused)) { timeline in
            let f = engine.step(to: timeline.date, height: H, autoPlay: autoPlay, auto: auto)
            let height = full * (T.stub + (1 - T.stub) * f.remain)
            let smoke = f.smoke
            let drips: [Float] = f.drips.flatMap { [Float($0.0), 0, Float($0.1), Float($0.2)] }
            ZStack(alignment: .topLeading) {
                Rectangle()
                    .colorEffect(ShaderLibrary.candleBurnTimer(
                        .float2(max(W, 1), max(H, 1)),
                        .float2(cx, baseY),
                        .float2(T.candleW, height),
                        .float4(f.lit, f.bend, f.flick, max(f.lit, 0.25 * smoke.1)),
                        .float4(cos(f.phase), sin(f.phase), cos(f.phase * 1.7), sin(f.phase * 1.7)),
                        .float2(smoke.0, smoke.1),
                        .floatArray(drips)
                    ))
                    .frame(width: W, height: H)
                    .contentShape(Rectangle())
                    .gesture(
                        DragGesture(minimumDistance: 0)
                            .onChanged { value in
                                awake = true
                                engine.dragChanged(location: value.location, velocity: value.velocity, cx: cx)
                            }
                            .onEnded { value in
                                engine.dragEnded(location: value.location, translation: value.translation, cx: cx, baseY: baseY, full: full)
                            }
                    )
                    .accessibilityElement()
                    .accessibilityLabel("A burning candle that shows the time left. Drag sideways to blow on the flame; tap the candle to relight it.")

                header
                    .padding(.horizontal, 24)
                    .frame(width: W, alignment: .leading)
                    .offset(y: insets.top + 16)
                    .allowsHitTesting(false)

                timer(f)
                    .padding(.horizontal, 24)
                    .frame(width: W)
                    .offset(y: baseY + 40)
                    .allowsHitTesting(false)

                controls(f)
                    .padding(.horizontal, 24)
                    .frame(width: W, height: H - insets.bottom - 24, alignment: .bottom)
            }
            .frame(width: W, height: H, alignment: .topLeading)
        }
        .background(M.background)
        .clipped()
    }

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("EMBER")
                .font(.system(size: 12, weight: .bold))
                .kerning(3)
                .foregroundStyle(M.ember)
            Text("Deep work")
                .font(.system(size: 28, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(M.ink)
            Text("Draft the launch notes · session 3 of 4")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(M.mute)
        }
    }

    private func timer(_ f: CandleBurnTimerFrame) -> some View {
        let seconds = f.remain * T.session
        return VStack(spacing: 6) {
            HStack(spacing: 0) {
                CandleBurnTimerDigitColumn(seconds: seconds, place: 600, base: 10)
                CandleBurnTimerDigitColumn(seconds: seconds, place: 60, base: 10)
                Text(":")
                    .font(.system(size: 56, weight: .thin))
                    .foregroundStyle(M.ink)
                    .offset(y: -4)
                    .padding(.horizontal, 2)
                CandleBurnTimerDigitColumn(seconds: seconds, place: 10, base: 6)
                CandleBurnTimerDigitColumn(seconds: seconds, place: 1, base: 10)
            }
            .accessibilityElement()
            .accessibilityLabel("Time left")
            .accessibilityValue(String(format: "%02d:%02d", Int(seconds) / 60, Int(seconds) % 60))
            ZStack {
                ForEach(0..<M.statuses.count, id: \.self) { i in
                    let w = f.statusW[i]
                    Text(M.statuses[i])
                        .font(.system(size: 14, weight: .medium))
                        .foregroundStyle(M.faint)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .opacity(w)
                        .offset(y: (1 - w) * 4)
                }
            }
            .frame(height: 20, alignment: .top)
        }
    }

    private func controls(_ f: CandleBurnTimerFrame) -> some View {
        HStack(spacing: 12) {
            Button {
                awake = true
                engine.togglePause()
            } label: {
                ZStack {
                    HStack(spacing: 8) {
                        Canvas { c, _ in
                            c.fill(Path(roundedRect: CGRect(x: 1, y: 1, width: 3.4, height: 12), cornerRadius: 1.2), with: .color(M.ink))
                            c.fill(Path(roundedRect: CGRect(x: 7.6, y: 1, width: 3.4, height: 12), cornerRadius: 1.2), with: .color(M.ink))
                        }
                        .frame(width: 12, height: 14)
                        Text("Pause")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(M.ink)
                    }
                    .opacity(f.running ? 1 : 0)
                    HStack(spacing: 8) {
                        Canvas { c, _ in
                            var p = Path()
                            p.move(to: CGPoint(x: 2, y: 1.5))
                            p.addLine(to: CGPoint(x: 11, y: 7))
                            p.addLine(to: CGPoint(x: 2, y: 12.5))
                            p.closeSubpath()
                            c.fill(p, with: .color(M.ink))
                        }
                        .frame(width: 12, height: 14)
                        Text("Resume")
                            .font(.system(size: 16, weight: .semibold))
                            .foregroundStyle(M.ink)
                    }
                    .opacity(f.running ? 0 : 1)
                }
                .frame(width: 120, height: 24)
                .frame(maxWidth: .infinity)
                .frame(height: 52)
                .background(
                    Capsule()
                        .fill(M.ink.opacity(0.08))
                        .overlay(Capsule().strokeBorder(M.ink.opacity(0.16), lineWidth: 0.5))
                )
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(f.running ? "Pause" : "Resume")

            Button {
                awake = true
                engine.reset()
            } label: {
                Text("Reset")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(Color(hex: 0x2a1a10))
                    .frame(maxWidth: .infinity)
                    .frame(height: 52)
                    .background(
                        Capsule()
                            .fill(M.primary)
                            .overlay(
                                Capsule().strokeBorder(
                                    LinearGradient(
                                        colors: [Color.white.opacity(0.6), Color.white.opacity(0)],
                                        startPoint: .top,
                                        endPoint: UnitPoint(x: 0.5, y: 0.1)
                                    ),
                                    lineWidth: 1
                                )
                            )
                            .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                            .shadow(color: Color.rgba(240, 162, 90, 0.18), radius: 12, y: 10)
                    )
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Reset the session")
        }
    }
}

/// One rolling digit column of mm:ss: it rolls during the last second before its value changes
/// (the units roll every second).
struct CandleBurnTimerDigitColumn: View {
    let seconds: Double
    let place: Double
    let base: Int

    var body: some View {
        let s = max(seconds, 0)
        let whole = floor(s / place)
        let digit = Int(whole) % base
        let r = s - whole * place
        let roll = place == 1 ? r : max(0, r - (place - 1))
        let h = CandleBurnTimerMetrics.digitH
        VStack(spacing: 0) {
            ForEach(0...base, id: \.self) { i in
                Text("\(i % base)")
                    .font(.system(size: 64, weight: .thin))
                    .monospacedDigit()
                    .foregroundStyle(CandleBurnTimerMetrics.ink)
                    .frame(width: 42, height: h)
            }
        }
        .offset(y: -(Double(digit) + CandleBurnTimerEngine.easeInOut(roll)) * h)
        .frame(width: 42, height: h, alignment: .top)
        .clipped()
    }
}

enum CandleBurnTimerMetrics {
    static let background = Color(hex: 0x140e0b)
    static let ink = Color(hex: 0xf4e9dc)
    static let mute = Color(hex: 0xf4e9dc, opacity: 0.6)
    static let faint = Color(hex: 0xf4e9dc, opacity: 0.36)
    static let ember = Color(hex: 0xf0a25a)
    static let primary = Color(hex: 0xf1dcc0)
    static let digitH = 76.0
    static let statuses = [
        "Focus · ends at 10:25",
        "Paused",
        "Blown out · tap the wick to relight",
        "Session done · take five",
    ]
}
