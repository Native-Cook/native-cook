// Nebula Dust Swirl — a sleep sound playing under forty thousand grains of light.
//
// Drift's now-playing screen: a Metal compute pass (NebulaDustSwirl.metal) turns a slow spiral
// nebula, a warm core and two cool arms in a tilted disc, every grain added as light, nearer grains
// larger. A timer ring frames the core; the sleep timer and the track sit under it.
//
// The reusable piece is this view: give it the track, the timer's state and your labels.
//
// autoPlay: loop = 10 s. The nebula turns half a turn per loop (its grains come in pairs on opposite
// arms, so the frame at 10 s equals the frame at 0). A ghost finger stirs the arms along an arc round
// the core from 1.2 to 6 s; the stirred dust springs back, what is left is blended out from 8.6 to
// 9.4 s and cleared at the cut. A real touch takes over at once: the dust swirls round the finger and
// is dragged along with it, while the film slows to 0.4×. 2 s after the last touch the film eases to
// the nearer end of the loop and restarts at t = 0. Reduced motion: the rest pose, still.

import SwiftUI

struct NebulaTrack: Hashable {
    var app: String
    var section: String
    var title: String
    var detail: String
    var hint: String
}

struct NebulaDustSwirl: View {
    static let loop: Double = NebulaDustSwirlMotion.loop
    private typealias M = NebulaDustSwirlMotion

    private let autoPlay: Bool
    private let track: NebulaTrack
    /// seconds left on the sleep timer when the screen opens (keep ten or more in its minute: the loop counts ten down)
    private let remaining: Double
    private let total: Double

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @State private var clock = NebulaDustSwirlClock()
    @State private var touching = false
    @GestureState private var held = false

    init(
        autoPlay: Bool = true,
        track: NebulaTrack = NebulaTrack(app: "Drift", section: "Sleep sounds", title: "Low Tide Hum", detail: "Brown noise · 8 layers", hint: "Drag to stir the dust"),
        remaining: Double = NebulaDustSwirlMotion.session,
        total: Double = NebulaDustSwirlMotion.sessionTotal
    ) {
        self.autoPlay = autoPlay
        self.track = track
        self.remaining = remaining
        self.total = total
    }

    private static let ink = Color(hex: 0xF6EEE6)
    private static let mute = Color.rgba(246, 238, 230, 0.66)
    private static let digit = 54.0
    private static let ghost = 46.0

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width, h = proxy.size.height + top + bottom
            let unit = min(w * M.scaleW, h * M.scaleH)
            let cx = w * M.centreX, cy = h * M.centreY
            let _ = clock.centreX = cx
            let _ = clock.centreY = cy
            let _ = clock.unit = unit
            let _ = clock.auto = autoPlay && !reduced
            let _ = reduced ? clock.showStill() : ()
            ZStack(alignment: .topLeading) {
                backdrop(w, h, cx, cy, unit)
                NebulaDustSwirlDust(clock: clock, still: reduced, width: w, height: h)
                    .frame(width: w, height: h)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
                ring(cx, cy, unit)
                page(w, h, top, bottom)
                    .allowsHitTesting(false)
                live {
                    ghostCircle
                        .opacity(clock.ghostA * 0.9)
                        .offset(x: clock.ghostX - Self.ghost / 2, y: clock.ghostY - Self.ghost / 2)
                }
                .allowsHitTesting(false)
            }
            .frame(width: w, height: h, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(reduced ? nil : stir)
            .clipped()
            .offset(y: -top)
        }
        .background(Color(hex: 0x0A0E1D).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onChange(of: held) { _, now in
            // a touch the system took away never reaches onEnded
            if !now, touching {
                touching = false
                clock.ended()
            }
        }
    }

    private func live<Content: View>(@ViewBuilder _ content: @escaping () -> Content) -> some View {
        TimelineView(.animation(paused: reduced)) { _ in content() }
    }

    private var stir: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($held) { _, state, _ in state = true }
            .onChanged { value in
                if !touching {
                    touching = true
                    clock.began(x: value.location.x, y: value.location.y)
                } else {
                    clock.moved(x: value.location.x, y: value.location.y, vx: value.velocity.width, vy: value.velocity.height)
                }
            }
            .onEnded { _ in
                touching = false
                clock.ended()
            }
    }

    // MARK: - the sky

    private func backdrop(_ w: Double, _ h: Double, _ cx: Double, _ cy: Double, _ unit: Double) -> some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [
                .init(color: Color(hex: 0x0A0E1D), location: 0),
                .init(color: Color(hex: 0x121834), location: 0.45),
                .init(color: Color(hex: 0x1B1630), location: 0.8),
                .init(color: Color(hex: 0x140F22), location: 1),
            ], startPoint: .top, endPoint: .bottom)
            .frame(width: w, height: h)
            // the cool haze of the disc
            Circle()
                .fill(RadialGradient(stops: [
                    .init(color: .rgba(96, 128, 196, 0.2), location: 0),
                    .init(color: .rgba(70, 90, 160, 0.08), location: 0.55),
                    .init(color: .rgba(40, 50, 100, 0), location: 1),
                ], center: .center, startRadius: 0, endRadius: unit * 1.25))
                .frame(width: unit * 2.5, height: unit * 2.5)
                .scaleEffect(x: 1, y: 0.62)
                .offset(x: cx - unit * 1.25, y: cy - unit * 1.25)
            // the warm breath of the core
            live {
                let g = M.glowAt(clock.film)
                let r = unit * (0.62 + 0.05 * g)
                Circle()
                    .fill(RadialGradient(stops: [
                        .init(color: .rgba(255, 196, 140, 0.34), location: 0),
                        .init(color: .rgba(214, 132, 128, 0.12), location: 0.4),
                        .init(color: .rgba(120, 70, 110, 0), location: 1),
                    ], center: .center, startRadius: 0, endRadius: unit * 0.68))
                    .frame(width: r * 2, height: r * 2)
                    .blur(radius: 6)
                    .opacity(0.82 + 0.18 * g)
                    .offset(x: cx - r, y: cy - r)
            }
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: - the timer ring

    private func ring(_ cx: Double, _ cy: Double, _ unit: Double) -> some View {
        let r = unit * 0.5
        let done = min(max(1 - remaining / total, 0), 1)
        // the timer's head on the ring, from the top, clockwise
        let a = -Double.pi / 2 + 2 * Double.pi * done
        return ZStack(alignment: .topLeading) {
            Circle()
                .stroke(Color.rgba(246, 238, 230, 0.16), lineWidth: 1)
                .frame(width: r * 2, height: r * 2)
                .offset(x: cx - r, y: cy - r)
            Circle()
                .trim(from: 0, to: done)
                .stroke(Color.rgba(255, 226, 196, 0.72), style: StrokeStyle(lineWidth: 1.6, lineCap: .round))
                .rotationEffect(.degrees(-90))
                .frame(width: r * 2, height: r * 2)
                .offset(x: cx - r, y: cy - r)
            live {
                Circle()
                    .fill(Color(hex: 0xFFE6CC))
                    .frame(width: 10, height: 10)
                    .shadow(color: .rgba(255, 214, 170, 0.9), radius: 5)
                    .opacity(0.55 + 0.45 * M.glowAt(clock.film))
                    .offset(x: cx + r * cos(a) - 5, y: cy + r * sin(a) - 5)
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private var ghostCircle: some View {
        Circle()
            .fill(Color.rgba(246, 238, 230, 0.12))
            .overlay(Circle().strokeBorder(Color.rgba(246, 238, 230, 0.55), lineWidth: 1.5))
            .overlay(Circle().fill(Color.rgba(246, 238, 230, 0.5)).frame(width: 14, height: 14))
            .frame(width: Self.ghost, height: Self.ghost)
    }

    // MARK: - the page

    private func page(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> some View {
        let compact = h < 740
        let minutes = Int(floor(remaining / 60))
        return VStack(spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text(track.app)
                    .font(.system(size: 20, weight: .bold))
                    .tracking(-0.2)
                    .foregroundStyle(Self.ink)
                    .accessibilityAddTraits(.isHeader)
                Spacer(minLength: 0)
                Text(track.section)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.mute)
            }
            Spacer(minLength: 0)
            VStack(spacing: 0) {
                live { timer(minutes) }
                    .frame(height: Self.digit)
                Text("left of \(Int((total / 60).rounded())) min")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(Self.mute)
                    .padding(.top, 2)
                card
                    .padding(.top, compact ? 14 : 22)
                live {
                    Text(track.hint)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(Self.ink)
                        .opacity(clock.hint * 0.8)
                }
                .padding(.top, 12)
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, top + (compact ? 8 : 14))
        .padding(.bottom, max(bottom, 12) + (compact ? 6 : 12))
        .frame(width: w, height: h)
    }

    private func timer(_ minutes: Int) -> some View {
        let seconds = max(M.sessionAt(clock.film, remaining) - Double(minutes * 60), 0)
        return HStack(spacing: 0) {
            Text("\(minutes):")
                .font(.system(size: 48, weight: .semibold).monospacedDigit())
                .tracking(-1)
                .foregroundStyle(Self.ink)
                .fixedSize()
                // React Native's 54 pt line box sits the glyphs 2 pt higher than a centred line
                .offset(y: -2)
            digitStrip(M.stripOf(seconds, tens: true))
            digitStrip(M.stripOf(seconds, tens: false))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(minutes) minutes \(Int(seconds.rounded(.up))) seconds left")
    }

    private func digitStrip(_ strip: Double) -> some View {
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 48, weight: .semibold).monospacedDigit())
                    .tracking(-1)
                    .foregroundStyle(Self.ink)
                    .fixedSize()
                    .offset(y: -2)
                    .frame(height: Self.digit)
                    .clipped()
            }
        }
        .offset(y: -Self.digit * strip)
        .frame(height: Self.digit, alignment: .top)
        .clipped()
        .accessibilityHidden(true)
    }

    private var card: some View {
        HStack(spacing: 0) {
            ZStack {
                RoundedRectangle(cornerRadius: 14, style: .continuous)
                    .fill(LinearGradient(stops: [
                        .init(color: Color(hex: 0xF2C49A), location: 0),
                        .init(color: Color(hex: 0xB56F86), location: 0.35),
                        .init(color: Color(hex: 0x3A3566), location: 0.72),
                        .init(color: Color(hex: 0x1E1B38), location: 1),
                    ], startPoint: UnitPoint(x: 0.2, y: 0.1), endPoint: UnitPoint(x: 0.9, y: 1)))
                Circle().fill(Color.rgba(255, 240, 224, 0.85)).frame(width: 8, height: 8)
            }
            .frame(width: 52, height: 52)
            VStack(alignment: .leading, spacing: 2) {
                Text(track.title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .lineLimit(1)
                Text(track.detail)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Self.mute)
                    .lineLimit(1)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            .padding(.horizontal, 14)
            HStack(spacing: 5) {
                RoundedRectangle(cornerRadius: 2).fill(Color(hex: 0x1E1B38)).frame(width: 4, height: 16)
                RoundedRectangle(cornerRadius: 2).fill(Color(hex: 0x1E1B38)).frame(width: 4, height: 16)
            }
            .frame(width: 48, height: 48)
            .background(Circle().fill(Self.ink))
            .shadow(color: .rgba(0, 0, 0, 0.3), radius: 1, y: 1)
            .shadow(color: .rgba(0, 0, 0, 0.25), radius: 9, y: 8)
            .accessibilityLabel("Pause")
        }
        .padding(12)
        .background(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .fill(Color.rgba(30, 30, 58, 0.55))
                .shadow(color: .rgba(0, 0, 0, 0.3), radius: 1, y: 1)
                .shadow(color: .rgba(0, 0, 0, 0.32), radius: 18, y: 16)
        )
        .overlay(RoundedRectangle(cornerRadius: 22, style: .continuous).strokeBorder(Color.rgba(246, 238, 230, 0.18), lineWidth: 1 / displayScale))
    }
}
