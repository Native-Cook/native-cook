// Murmuration Flock — a breathing session led by twenty thousand starlings.
//
// Dusk's wind-down screen: a Metal compute pass (MurmurationFlock.metal) places a flock against the
// evening sky, a dark ribbon that folds and streams. On the inhale it gathers into a soft circle, on
// the exhale it lets go and streams out again.
//
// autoPlay: loop = 12 s. Every bird's place is a pure function of its seeds and the loop time; a ghost
// finger parts the flock during the exhale, and what it pushed is blended out in the rest and cleared
// at the cut, so frame 12 s equals frame 0. A real touch takes over at once: birds flee and swirl
// around it while the film slows to a quarter. 2 s after the last touch the film runs to the nearer
// end of the loop and restarts at t = 0. Reduced motion: the circle, still, no loop.
//
// Thread model: the Metal view's frame advances the clock and steps the birds; the labels, the timer
// and the guide ring read the clock's film time inside TimelineViews.

import SwiftUI

struct MurmurationFlockCopy {
    var app = "Dusk"
    var session = "Evening wind-down"
    var settle = "Settle"
    var inhale = "Inhale"
    var exhale = "Exhale"
    var pace = "4 in, 1 hold, 4 out"
    var hint = "Touch the flock"
}

struct MurmurationFlock: View {
    static let loop: Double = 12

    private typealias M = MurmurationFlockMotion
    private let autoPlay: Bool
    private let copy: MurmurationFlockCopy
    @Environment(\.accessibilityReduceMotion) private var reduced
    @State private var clock = MurmurationFlockClock()
    @State private var touching = false
    @GestureState private var held = false

    init(autoPlay: Bool = true, copy: MurmurationFlockCopy = MurmurationFlockCopy()) {
        self.autoPlay = autoPlay
        self.copy = copy
    }

    private static let ink = Color(hex: 0xFFF4E6)
    private static let mute = Color.rgba(255, 244, 230, 0.72)
    private static let digit = 24.0
    private static let ghost = 46.0

    var body: some View {
        GeometryReader { proxy in
            let top = proxy.safeAreaInsets.top, bottom = proxy.safeAreaInsets.bottom
            let w = proxy.size.width, h = proxy.size.height + top + bottom
            screen(w, h, top, bottom)
                .frame(width: w, height: h, alignment: .topLeading)
                .clipped()
                .contentShape(Rectangle())
                .gesture(touch)
                .offset(y: -top)
        }
        .background(Color(hex: 0x2C3163).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onChange(of: held) { _, now in
            // a touch the system took away never reaches onEnded
            if !now, touching {
                touching = false
                clock.touchEnded()
            }
        }
    }

    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($held) { _, state, _ in state = true }
            .onChanged { value in
                if !touching {
                    touching = true
                    clock.touchBegan(x: value.startLocation.x, y: value.startLocation.y)
                }
                clock.touchMoved(x: value.location.x, y: value.location.y)
            }
            .onEnded { _ in
                touching = false
                clock.touchEnded()
            }
    }

    private func screen(_ w: Double, _ h: Double, _ top: Double, _ bottom: Double) -> some View {
        let _ = clock.auto = autoPlay && !reduced
        let _ = clock.reduced = reduced
        let compact = h < 740
        let unit = min(w * M.scaleW, h * M.scaleH)
        let guide = unit * M.ringRadius * 2 + 34
        let ridgeH = (h * 0.3).rounded()
        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x2C3163), location: 0),
                    .init(color: Color(hex: 0x5B4A82), location: 0.3),
                    .init(color: Color(hex: 0xA9668A), location: 0.56),
                    .init(color: Color(hex: 0xE58F73), location: 0.8),
                    .init(color: Color(hex: 0xF6BE7E), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: w, height: h)

            TimelineView(.animation(paused: reduced)) { _ in
                let k = M.guideAt(clock.film)
                Circle()
                    .strokeBorder(Color.rgba(255, 240, 222, 0.9), lineWidth: 1)
                    .frame(width: guide, height: guide)
                    .scaleEffect(0.92 + 0.08 * k)
                    .opacity(0.3 * k)
                    .offset(x: w * M.centreX - guide / 2, y: h * M.centreY - guide / 2)
            }
            .frame(width: w, height: h, alignment: .topLeading)

            MurmurationFlockSky(clock: clock, width: w, height: h, still: reduced)
                .frame(width: w, height: h)
                .accessibilityLabel("A flock of starlings against the dusk")

            MurmurationFlockRidges()
                .frame(width: w, height: ridgeH)
                .offset(y: h - ridgeH)

            TimelineView(.animation(paused: reduced)) { _ in
                ZStack(alignment: .topLeading) {
                    VStack(spacing: 0) {
                        head
                        Spacer(minLength: 0)
                        foot
                    }
                    .padding(.horizontal, 24)
                    .padding(.top, top + (compact ? 8 : 14))
                    .padding(.bottom, max(bottom, 12) + (compact ? 8 : 16))
                    .frame(width: w, height: h)

                    finger
                        .opacity(clock.ghostAlpha * 0.9)
                        .offset(x: clock.ghostX - Self.ghost / 2, y: clock.ghostY - Self.ghost / 2)
                        .accessibilityHidden(true)
                }
                .frame(width: w, height: h, alignment: .topLeading)
            }
        }
        .allowsHitTesting(false)
    }

    private var head: some View {
        HStack(alignment: .top, spacing: 0) {
            VStack(alignment: .leading, spacing: 2) {
                Text(copy.app)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Self.ink)
                Text(copy.session)
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(Self.mute)
            }
            Spacer(minLength: 0)
            let s = M.sessionAt(clock.film)
            HStack(spacing: 0) {
                Text("4:")
                    .font(Self.digitFont)
                    .foregroundStyle(Self.ink)
                    .accessibilityHidden(true)
                strip(M.tensOf(s))
                strip(M.onesOf(s))
            }
            .frame(height: Self.digit)
            // an odometer: VoiceOver would read its columns as loose digits
            .accessibilityHidden(true)
        }
    }

    private static let digitFont = Font.system(size: 19, weight: .semibold).monospacedDigit()

    /// a digit on a strip 0…9, 0 that rolls by `place`
    private func strip(_ place: Double) -> some View {
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(Self.digitFont)
                    .foregroundStyle(Self.ink)
                    .frame(height: Self.digit)
            }
        }
        .offset(y: -Self.digit * place)
        .frame(height: Self.digit, alignment: .top)
        .clipped()
        .accessibilityHidden(true)
    }

    private var foot: some View {
        let words = M.wordsAt(clock.film)
        return VStack(spacing: 0) {
            ZStack {
                word(copy.settle, words[0])
                word(copy.inhale, words[1])
                word(copy.exhale, words[2])
            }
            .frame(maxWidth: .infinity)
            .frame(height: 58)
            Text(copy.pace)
                .font(.system(size: 15, weight: .medium).monospacedDigit())
                .foregroundStyle(Self.ink)
                .padding(.top, 2)
            Text(copy.hint)
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(Self.ink)
                .opacity(clock.hint * 0.8)
                .padding(.top, 8)
        }
    }

    private func word(_ label: String, _ k: Double) -> some View {
        Text(label)
            .font(.system(size: 46, weight: .bold))
            .tracking(-0.8)
            .foregroundStyle(Self.ink)
            .fixedSize()
            .scaleEffect(0.97 + 0.03 * k)
            .offset(y: 8 * (1 - k))
            .opacity(k)
            .accessibilityHidden(k < 0.5)
    }

    /// the ghost finger: a pale ring with a soft core
    private var finger: some View {
        ZStack {
            Circle().fill(Color.rgba(255, 244, 230, 0.14))
            Circle().strokeBorder(Color.rgba(255, 244, 230, 0.6), lineWidth: 1.5)
            Circle().fill(Color.rgba(255, 244, 230, 0.55)).frame(width: 14, height: 14)
        }
        .frame(width: Self.ghost, height: Self.ghost)
    }
}

/// the two hills at the foot of the sky, drawn in a 400 × 160 box stretched to the frame
struct MurmurationFlockRidges: View {
    var body: some View {
        Canvas { context, size in
            let t = CGAffineTransform(scaleX: size.width / 400, y: size.height / 160)
            var far = Path()
            far.move(to: CGPoint(x: 0, y: 34))
            far.addCurve(to: CGPoint(x: 124, y: 36), control1: CGPoint(x: 44, y: 18), control2: CGPoint(x: 82, y: 24))
            far.addCurve(to: CGPoint(x: 256, y: 16), control1: CGPoint(x: 170, y: 50), control2: CGPoint(x: 208, y: 20))
            far.addCurve(to: CGPoint(x: 400, y: 24), control1: CGPoint(x: 306, y: 12), control2: CGPoint(x: 352, y: 36))
            far.addLine(to: CGPoint(x: 400, y: 160))
            far.addLine(to: CGPoint(x: 0, y: 160))
            far.closeSubpath()
            var near = Path()
            near.move(to: CGPoint(x: 0, y: 66))
            near.addCurve(to: CGPoint(x: 158, y: 66), control1: CGPoint(x: 60, y: 48), control2: CGPoint(x: 104, y: 60))
            near.addCurve(to: CGPoint(x: 316, y: 54), control1: CGPoint(x: 214, y: 72), control2: CGPoint(x: 262, y: 50))
            near.addCurve(to: CGPoint(x: 400, y: 60), control1: CGPoint(x: 350, y: 56), control2: CGPoint(x: 376, y: 64))
            near.addLine(to: CGPoint(x: 400, y: 160))
            near.addLine(to: CGPoint(x: 0, y: 160))
            near.closeSubpath()
            context.fill(far.applying(t), with: .color(Color(hex: 0x6E4468).opacity(0.72)))
            context.fill(near.applying(t), with: .color(Color(hex: 0x2E2340)))
        }
        .accessibilityHidden(true)
    }
}
