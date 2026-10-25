import SwiftUI

/// The onboarding of "Orrery", a stargazing app. A deep night sky (one Metal shader: navy gradient,
/// a Milky Way of fbm dust with a dark rift, two twinkling layers of hashed stars) turns round the
/// pole, which sits above the screen. Each step brings a constellation to the centre and draws it:
/// its stars bloom as glowing lines reach them one after another, the brightest is named, and a
/// faint line drawing of its figure fades in. Constellations, lines and labels are Canvas drawing
/// (ConstellationStepsWelcomeArt.swift); the text is views.
///
/// autoPlay (loop = 10 s, ConstellationStepsWelcomeMotion.swift):
///   0.0 – 1.6   rest: step 1, Cygnus's stars waiting.
///   1.6 – 3.6   Cygnus is drawn, Deneb named, the swan fades in.
///   3.6 – 4.4   the sky turns 48° to Cassiopeia; the text rolls; 4.4 – 6.2 it is drawn.
///   6.2 – 7.0   on to Orion; 7.0 – 8.4 drawn.
///   8.4 – 9.4   the sky turns back home while every line and figure fades. Rest to 10.0.
/// Touch: swipe sideways and the sky turns with the finger; let go and it settles on the nearest
/// step, whose constellation then draws itself. "Continue" steps on. 2 s after the last touch the
/// sky turns home and fades and the loop restarts. Reduced motion: step 1 drawn, no loop.
struct ConstellationStepsWelcome: View {
    var autoPlay = false

    static let loop = ConstellationStepsWelcomeData.loop
    static let background = Color(red: 0x07 / 255, green: 0x0A / 255, blue: 0x1A / 255)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = ConstellationStepsWelcomeModel()
    @State private var touchLive = false
    @State private var touchSerial = 0
    @State private var dragging = false

    private typealias D = ConstellationStepsWelcomeData
    private typealias G = ConstellationStepsWelcomeGeo
    static let ink = Color(red: 0xF3 / 255, green: 0xEE / 255, blue: 0xE4 / 255)
    static let muted = ink.opacity(0.62)
    static let gold = Color(red: 0xF2 / 255, green: 0xD4 / 255, blue: 0x8A / 255)
    private static let panelH = 256.0

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            screen(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private var auto: Bool { autoPlay && !reduceMotion }
    private var paused: Bool { !(auto || touchLive) }

    private func screen(size: CGSize, insets: EdgeInsets) -> some View {
        model.configure(auto: auto, reduced: reduceMotion)
        let panelTop = size.height - insets.bottom - Self.panelH
        let g = G(w: size.width, h: size.height, top: insets.top + 84, bottom: panelTop)
        let radius = g.cy - g.py
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.constellationStepsWelcomeSky(
                            .float2(size.width, size.height),
                            .float2(g.px, g.py),
                            .float(p.theta),
                            .float(2 * Double.pi * p.time / D.loop)
                        ))
                    Canvas { ctx, _ in ConstellationStepsWelcomeArt.draw(ctx, g, p) }
                }
                .frame(width: size.width, height: size.height)
            }
            .allowsHitTesting(false)

            // The sky: swipe sideways to turn it.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 10)
                        .onChanged { v in
                            if !dragging {
                                dragging = true
                                beginTouch()
                                model.swipeBegan()
                            }
                            model.swipeMoved(v.translation.width, radius: radius)
                        }
                        .onEnded { v in
                            dragging = false
                            model.swipeEnded(v.translation.width, velocityX: v.velocity.width, radius: radius)
                            endTouch()
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("The night sky. Swipe sideways to turn it to the next constellation.")

            header
                .padding(.horizontal, 24)
                .frame(width: size.width, height: 36)
                .offset(y: insets.top + 12)

            live { p in
                ConstellationStepsWelcomeRollStack(items: D.captions, index: p.step, height: 16, alignment: .center) {
                    Text($0)
                        .font(.system(size: 11, weight: .bold))
                        .kerning(2)
                        .foregroundStyle(Self.gold.opacity(0.8))
                        .lineLimit(1)
                }
            }
            .padding(.horizontal, 24)
            .frame(width: size.width)
            .offset(y: insets.top + 58)
            .allowsHitTesting(false)

            panel(insets: insets)
                .frame(width: size.width, height: size.height - panelTop, alignment: .top)
                .offset(y: panelTop)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Self.background)
        .clipped()
    }

    private func live<V: View>(@ViewBuilder _ content: @escaping (ConstellationStepsWelcomePose) -> V) -> some View {
        TimelineView(.animation(paused: paused)) { tl in
            content(model.pose(at: tl.date))
        }
    }

    // MARK: Header

    private var header: some View {
        HStack {
            HStack(spacing: 10) {
                ZStack {
                    Capsule()
                        .strokeBorder(Self.gold, lineWidth: 1.2)
                        .frame(width: 26, height: 14)
                        .rotationEffect(.degrees(-20))
                    Circle().fill(Self.gold).frame(width: 8, height: 8)
                }
                .frame(width: 26, height: 26)
                Text("orrery")
                    .font(.system(size: 24, weight: .bold))
                    .kerning(-0.6)
                    .foregroundStyle(Self.ink)
            }
            Spacer()
            Text("Skip")
                .font(.system(size: 15, weight: .semibold))
                .foregroundStyle(Self.muted)
                .padding(.vertical, 8)
                .padding(.leading, 12)
                .accessibilityAddTraits(.isButton)
        }
    }

    // MARK: Panel

    private func panel(insets: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            live { p in
                VStack(alignment: .leading, spacing: 8) {
                    ZStack(alignment: .leading) {
                        HStack(spacing: 8) {
                            ForEach(0..<3, id: \.self) { _ in
                                Circle().fill(Self.ink.opacity(0.22)).frame(width: 8, height: 8)
                            }
                        }
                        Circle().fill(Self.gold).frame(width: 8, height: 8).offset(x: p.step * 16)
                    }
                    .frame(height: 8)
                    .padding(.bottom, 2)
                    ConstellationStepsWelcomeRollStack(items: D.titles, index: p.step, height: 36, alignment: .leading) {
                        Text($0)
                            .font(.system(size: 28, weight: .bold))
                            .kerning(-0.5)
                            .foregroundStyle(Self.ink)
                            .lineLimit(1)
                    }
                    ConstellationStepsWelcomeRollStack(items: D.bodies, index: p.step, height: 44, alignment: .leading) {
                        Text($0)
                            .font(.system(size: 15))
                            .lineSpacing(3)
                            .foregroundStyle(Self.muted)
                            .lineLimit(2)
                    }
                }
            }
            Button {
                if !auto { beginTouch() }
                model.next()
                if !auto { endTouch() }
            } label: {
                Text("Continue")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(Color(red: 0x1C / 255, green: 0x1A / 255, blue: 0x2E / 255))
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        Capsule()
                            .fill(Self.gold)
                            .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                            .shadow(color: Self.gold.opacity(0.25), radius: 15, y: 12)
                    )
                    .overlay(
                        // inset 0 1px 0: a lit top edge inside the pill.
                        Capsule()
                            .strokeBorder(Color(red: 1, green: 250 / 255, blue: 230 / 255).opacity(0.7), lineWidth: 1)
                            .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.12)))
                    )
            }
            .buttonStyle(ConstellationStepsWelcomePress())
            .padding(.top, 8)
            Text("Uses your location only to face the right sky")
                .font(.system(size: 13))
                .foregroundStyle(Self.ink.opacity(0.42))
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
        }
        .padding(.top, 20)
        .padding(.horizontal, 24)
        .padding(.bottom, insets.bottom + 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(alignment: .top) {
            GeometryReader { geo in
                LinearGradient(
                    stops: [
                        .init(color: Color(red: 7 / 255, green: 10 / 255, blue: 26 / 255).opacity(0), location: 0),
                        .init(color: Color(red: 7 / 255, green: 10 / 255, blue: 26 / 255).opacity(0.82), location: 0.3),
                        .init(color: Color(red: 6 / 255, green: 8 / 255, blue: 22 / 255).opacity(0.96), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                .frame(width: geo.size.width, height: geo.size.height + 40)
                .offset(y: -40)
            }
            .allowsHitTesting(false)
        }
    }

    // MARK: Touch bookkeeping

    private func beginTouch() {
        touchSerial += 1
        touchLive = true
    }

    private func endTouch() {
        touchSerial += 1
        let serial = touchSerial
        Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(400))
            while model.moving && serial == touchSerial {
                try? await Task.sleep(for: .milliseconds(300))
            }
            if serial == touchSerial && !auto { touchLive = false }
        }
    }
}

/// Pressed: the pill shrinks to 97 %.
private struct ConstellationStepsWelcomePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

/// One line of a list chosen by a continuous index: the old line rolls up and fades, the new one
/// comes in from below, inside a clipped box.
struct ConstellationStepsWelcomeRollStack<Row: View>: View {
    let items: [String]
    let index: Double
    let height: Double
    let alignment: Alignment
    @ViewBuilder let row: (String) -> Row

    var body: some View {
        ZStack(alignment: alignment) {
            ForEach(items.indices, id: \.self) { i in
                let d = Double(i) - index
                if abs(d) < 1 {
                    row(items[i])
                        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: alignment)
                        .offset(y: d * height)
                        .opacity(max(0, 1 - abs(d) * 1.15))
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        .clipped()
    }
}
