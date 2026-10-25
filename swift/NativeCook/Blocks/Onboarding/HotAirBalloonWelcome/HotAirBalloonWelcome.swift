import SwiftUI

/// The onboarding of "Drift", a travel planner. A striped hot-air balloon stands on a meadow at
/// dawn over layered hills; each onboarding step is an altitude. Hold the burner button: the flame
/// roars (with a bloom that lights the envelope from inside), the envelope swells a little and the
/// balloon climbs, the hills slide away below with parallax, clouds come down past it (two of them
/// in front), and the step text rolls. The sky, the four hill layers and the cloud sprites are
/// Canvases drawn once and only moved; the balloon is drawn every frame (HotAirBalloonWelcomeArt).
///
/// autoPlay (loop = 10 s, HotAirBalloonWelcomeMotion.swift):
///   0.0 – 1.6   rest on the ground, step 1.
///   1.6 – 4.0   burn and rise to step 2; the text rolls at 2.4.
///   4.6 – 7.0   burn and rise to step 3; the text rolls at 5.2.
///   7.0 – 8.8   a smooth descent to the ground (a short burn at 8.0); the text rolls back. Rest.
///   The envelope turns one colour pattern (120°) per loop; aloft it sways ±1.3° and bobs 2 pt.
/// Touch: hold the burner to climb; let go and the balloon settles on the nearest step. Drag the
/// sky to change altitude; a flick carries its velocity. 2 s after the last touch it descends.
/// Reduced motion: the ground frame, no loop; the button and drag change the step.
struct HotAirBalloonWelcome: View {
    var autoPlay = false

    static let loop = HotAirBalloonWelcomeData.loop
    static let background = hotAirBalloonWelcomeHex(0x1A1430)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = HotAirBalloonWelcomeModel()
    @State private var touchLive = false
    @State private var touchSerial = 0
    @State private var holding = false
    @State private var dragging = false

    private typealias D = HotAirBalloonWelcomeData
    private typealias G = HotAirBalloonWelcomeGeo
    static let ink = hotAirBalloonWelcomeHex(0xFBF1EA)
    static let muted = hotAirBalloonWelcomeHex(0xFBF1EA).opacity(0.64)
    static let coral = hotAirBalloonWelcomeHex(0xE5664F)
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
        let g = G(w: size.width, h: size.height, ground: panelTop - 8, ceiling: insets.top + 70)
        return ZStack(alignment: .topLeading) {
            scene(g: g)
                .allowsHitTesting(false)

            // The sky: drag up or down to change altitude.
            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 8)
                        .onChanged { v in
                            if !dragging {
                                beginTouch()
                                dragging = true
                                model.skyBegan()
                            }
                            model.skyMoved(v.translation.height, rise: g.rise)
                        }
                        .onEnded { v in
                            dragging = false
                            model.skyEnded(velocityY: v.velocity.height, rise: g.rise)
                            endTouch()
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("A hot-air balloon at dawn. Drag up or down to change altitude.")

            header
                .padding(.horizontal, 24)
                .frame(width: size.width, height: 36)
                .offset(y: insets.top + 12)

            panel(insets: insets)
                .frame(width: size.width, height: size.height - panelTop, alignment: .top)
                .offset(y: panelTop)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Self.background)
        .clipped()
    }

    // MARK: Scene

    private func scene(g: G) -> some View {
        TimelineView(.animation(paused: paused)) { tl in
            let p = model.pose(at: tl.date)
            let w = 2 * Double.pi / D.loop
            ZStack(alignment: .topLeading) {
                HotAirBalloonWelcomeSky(geo: g).equatable()
                ForEach(0..<2, id: \.self) { i in hill(g, i, p) }
                ForEach(0..<4, id: \.self) { i in cloud(g, i, p, w) }
                ForEach(2..<4, id: \.self) { i in hill(g, i, p) }
                Canvas { ctx, _ in HotAirBalloonWelcomeArt.drawBalloon(ctx, g, p) }
                    .frame(width: g.w, height: g.h)
                ForEach(4..<6, id: \.self) { i in cloud(g, i, p, w) }
                Canvas { ctx, _ in HotAirBalloonWelcomeArt.drawBloom(ctx, g, p) }
                    .frame(width: g.w, height: g.h)
            }
            .frame(width: g.w, height: g.h, alignment: .topLeading)
        }
    }

    private func hill(_ g: G, _ i: Int, _ p: HotAirBalloonWelcomePose) -> some View {
        HotAirBalloonWelcomeHill(geo: g, i: i).equatable()
            .offset(y: g.hillY(i) + p.alt * g.scroll * G.hillPar[i])
    }

    private func cloud(_ g: G, _ i: Int, _ p: HotAirBalloonWelcomePose, _ w: Double) -> some View {
        let c = HotAirBalloonWelcomeCloud.all[i]
        let cw = 160 * c.scale
        let ch = 70 * c.scale
        let x = c.x * g.w - cw / 2 + c.amp * sin(w * p.time + c.phase)
        let y = c.y + p.alt * g.scroll * c.par
        let visible = !(y > g.h || y + ch < 0)
        return HotAirBalloonWelcomeCloudSprite(sprite: c.sprite, scale: c.scale).equatable()
            .opacity(visible ? c.alpha : 0)
            .offset(x: x, y: y)
    }

    // MARK: Header

    private var header: some View {
        HStack {
            Text("drift")
                .font(.system(size: 26, weight: .heavy))
                .kerning(-0.8)
                .foregroundStyle(Self.ink)
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
        let shape = UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30, style: .continuous)
        return VStack(alignment: .leading, spacing: 8) {
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                VStack(alignment: .leading, spacing: 8) {
                    ZStack(alignment: .leading) {
                        HStack(spacing: 8) {
                            ForEach(0..<3, id: \.self) { _ in
                                Circle().fill(Self.ink.opacity(0.22 / 1)).frame(width: 8, height: 8)
                            }
                        }
                        Circle().fill(Self.coral).frame(width: 8, height: 8).offset(x: p.step * 16)
                    }
                    .frame(height: 8)
                    .padding(.bottom, 2)
                    HotAirBalloonWelcomeRollStack(items: D.titles, index: p.step, height: 36) {
                        Text($0)
                            .font(.system(size: 28, weight: .bold))
                            .kerning(-0.5)
                            .foregroundStyle(Self.ink)
                            .lineLimit(1)
                    }
                    HotAirBalloonWelcomeRollStack(items: D.bodies, index: p.step, height: 44) {
                        Text($0)
                            .font(.system(size: 15))
                            .lineSpacing(21 - 18)
                            .foregroundStyle(Self.muted)
                            .lineLimit(2)
                    }
                }
            }
            burner
                .padding(.top, 8)
            Text("Already planning with Drift? Sign in")
                .font(.system(size: 13))
                .foregroundStyle(Self.ink.opacity(0.45 / 1))
                .frame(maxWidth: .infinity)
                .padding(.top, 4)
        }
        .padding(.top, 20)
        .padding(.horizontal, 24)
        .padding(.bottom, insets.bottom + 16)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
        .background(
            shape
                .fill(LinearGradient(
                    stops: [
                        .init(color: hotAirBalloonWelcomeRGBA(40, 30, 66, 0.96), location: 0),
                        .init(color: hotAirBalloonWelcomeRGBA(30, 22, 52, 0.98), location: 0.4),
                        .init(color: hotAirBalloonWelcomeHex(0x1A1430), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                ))
                .shadow(color: .black.opacity(0.2), radius: 1, y: -1)
                .shadow(color: hotAirBalloonWelcomeRGBA(10, 6, 30, 0.35), radius: 20, y: -16)
        )
        .overlay(alignment: .top) {
            Rectangle()
                .fill(hotAirBalloonWelcomeRGBA(255, 220, 200, 0.3))
                .frame(height: 1 / 3)
                .padding(.horizontal, 30)
        }
        .contentShape(shape)
    }

    private var burner: some View {
        ZStack {
            Capsule().fill(hotAirBalloonWelcomeHex(0xC9533F))
            TimelineView(.animation(paused: paused)) { tl in
                Capsule().fill(hotAirBalloonWelcomeHex(0xFF9A55)).opacity(model.pose(at: tl.date).heat)
            }
            HStack(spacing: 10) {
                HotAirBalloonWelcomeFlameIcon()
                    .frame(width: 18, height: 22)
                Text("Hold to rise")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(hotAirBalloonWelcomeHex(0xFFF6EE))
            }
        }
        .overlay(alignment: .top) {
            // inset 0 1px 0: a lit top edge inside the pill.
            Capsule()
                .strokeBorder(hotAirBalloonWelcomeRGBA(255, 230, 210, 0.35), lineWidth: 1)
                .mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.12)))
        }
        .frame(height: 56)
        .clipShape(Capsule())
        .background(
            Capsule()
                .fill(hotAirBalloonWelcomeHex(0xC9533F))
                .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                .shadow(color: hotAirBalloonWelcomeRGBA(229, 102, 79, 0.35), radius: 14, y: 12)
        )
        .contentShape(Capsule())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    if !holding {
                        holding = true
                        beginTouch()
                        model.burnerDown()
                    }
                }
                .onEnded { _ in
                    holding = false
                    model.burnerUp()
                    endTouch()
                }
        )
        .accessibilityElement()
        .accessibilityAddTraits(.isButton)
        .accessibilityLabel("Hold to rise")
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

/// The burner button's flame (the RN version's 18 × 22 SVG).
private struct HotAirBalloonWelcomeFlameIcon: View {
    var body: some View {
        Canvas { ctx, size in
            var c = ctx
            c.scaleBy(x: size.width / 18, y: size.height / 22)
            var p = Path()
            p.move(to: CGPoint(x: 9, y: 1))
            p.addCurve(to: CGPoint(x: 15.5, y: 13.5), control1: CGPoint(x: 10.5, y: 5), control2: CGPoint(x: 15.5, y: 8))
            p.addCurve(to: CGPoint(x: 9, y: 21), control1: CGPoint(x: 15.5, y: 17.6), control2: CGPoint(x: 12.6, y: 21))
            p.addCurve(to: CGPoint(x: 2.5, y: 13.5), control1: CGPoint(x: 5.4, y: 21), control2: CGPoint(x: 2.5, y: 17.6))
            p.addCurve(to: CGPoint(x: 5.6, y: 7.4), control1: CGPoint(x: 2.5, y: 10.6), control2: CGPoint(x: 4.2, y: 8.6))
            p.addCurve(to: CGPoint(x: 8, y: 11.6), control1: CGPoint(x: 5.8, y: 9.6), control2: CGPoint(x: 6.8, y: 11))
            p.addCurve(to: CGPoint(x: 9, y: 1), control1: CGPoint(x: 7.6, y: 7.6), control2: CGPoint(x: 8.2, y: 4.2))
            p.closeSubpath()
            c.fill(p, with: .linearGradient(
                Gradient(stops: [
                    .init(color: hotAirBalloonWelcomeHex(0xFFE7B0), location: 0),
                    .init(color: hotAirBalloonWelcomeHex(0xFFB255), location: 0.5),
                    .init(color: hotAirBalloonWelcomeHex(0xFF7A3D), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: 22), endPoint: .zero
            ))
        }
    }
}

/// One line of a list chosen by a continuous index: the old line rolls up and fades, the new one
/// comes in from below, inside a clipped box.
struct HotAirBalloonWelcomeRollStack<Row: View>: View {
    let items: [String]
    let index: Double
    let height: Double
    @ViewBuilder let row: (String) -> Row

    var body: some View {
        ZStack(alignment: .leading) {
            ForEach(items.indices, id: \.self) { i in
                let d = Double(i) - index
                if abs(d) < 1 {
                    row(items[i])
                        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height, alignment: .leading)
                        .offset(y: d * height)
                        .opacity(max(0, 1 - abs(d) * 1.15))
                }
            }
        }
        .frame(maxWidth: .infinity, minHeight: height, maxHeight: height)
        .clipped()
    }
}
