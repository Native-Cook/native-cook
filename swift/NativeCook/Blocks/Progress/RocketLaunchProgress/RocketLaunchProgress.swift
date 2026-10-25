import SwiftUI
import UIKit

/*
 Rocket Launch Progress — "Shipwright", a deploy screen where the progress is fuel. A small rocket
 waits on its pad at dusk; the tank beside it fills as Build, Test and Upload tick; at 100 % the
 sky counts 3-2-1, the engine lights (flame, smoke thrown sideways along the pad, the camera
 shaking), the rocket lifts off and arcs away on a contrail, "v4.2.0 is live" — and the next
 rocket rolls onto the pad. Hold the pad to fuel it yourself; full tanks launch.

 autoPlay (loop = 10 s, RocketLaunchProgressMotion): rest 0–1.4 · fuelling 1.4–4.4 · countdown
 4.4–5.6 · ignition 5.6–6.2 · lift-off 6.2–7.6 · deployed 7.6–8.4 · new rocket 8.4–9.4 · rest.
 A touch takes over: holding fuels (2.4 s to full; letting go holds the gauge on a whole number);
 a full tank hands the rest of the launch back to the loop. 2 s after letting go of a part-full
 tank it drains (0.9 s) and the loop restarts. Reduced motion: no loop, no shake or flight; the
 gauge fills under the finger and the screen shows the deployed state when full.

 Drawing: the baked scene; a Canvas with the contrail, the fuel (a clipped level with a sloshing
 top) and the rocket (static paths under a moving transform); one Metal colorEffect for flame,
 smoke and glow. Text and the pipeline are views reading the same frame.
*/
struct RocketLaunchProgress: View {
    /// Fuels, counts down, launches, deploys, rolls out the next rocket.
    var autoPlay = false

    static let loop = 10.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = RocketLaunchProgressEngine()
    @State private var back: Image?
    @State private var backKey = ""
    @State private var awake = false
    @GestureState private var pressing = false

    private typealias P = RocketLaunchProgressPalette
    private typealias M = RocketLaunchProgressMotion

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            if size.width > 0 && size.height > 0 {
                stage(size: size, insets: insets)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(P.night.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let L = RocketLaunchProgressLayout(W: size.width, H: size.height, top: insets.top, bottom: insets.bottom)
        let key = "\(Int(size.width))x\(Int(size.height))-\(Int(insets.top))-\(Int(insets.bottom))"
        let auto = autoPlay && !reduceMotion
        let skyMid = insets.top + 108 + (L.baseY - L.rocketH - insets.top - 108) * 0.42
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation(paused: back == nil || !(auto || awake))) { timeline in
                let f = engine.step(to: timeline.date, auto: auto, layout: L)
                ZStack(alignment: .topLeading) {
                    if let back {
                        back.resizable().frame(width: L.W, height: L.H)
                    }
                    // The engine shakes what stands on the pad; the sky and the ground stay put.
                    ZStack(alignment: .topLeading) {
                        Canvas { ctx, _ in drawPad(ctx, L, f) }
                            .frame(width: L.W, height: L.H)
                        Rectangle()
                            .frame(width: L.W, height: L.H)
                            .colorEffect(ShaderLibrary.RocketLaunchProgressExhaust(
                                .float2(f.rx, f.ry),
                                .float2(sin(f.rot), -cos(f.rot)),
                                .float(f.thr),
                                .float(f.lift),
                                .float(f.age),
                                .float(f.fade),
                                .float2(L.padX, L.groundY - 8),
                                .float(L.k)
                            ))
                    }
                    .offset(x: f.sx, y: f.sy)
                    .opacity(back == nil ? 0 : 1)

                    header(f.t)
                        .frame(width: L.W - 44, alignment: .leading)
                        .offset(x: 22, y: insets.top + 14)
                    odometer(f)
                        .offset(x: 22, y: insets.top + 118)
                    countdown(f.t)
                        .frame(width: L.W, height: 120)
                        .offset(y: skyMid - 60)
                    badge(f.t)
                        .frame(width: L.W)
                        .offset(y: skyMid - 22)
                    steps(L, f)
                        .frame(width: L.W - 28, height: L.cardH)
                        .offset(x: 14, y: L.cardTop)
                }
                .frame(width: L.W, height: L.H, alignment: .topLeading)
                .allowsHitTesting(false)
            }
            // The whole screen is the pad's hold area.
            Color.clear
                .frame(width: size.width, height: size.height)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .updating($pressing) { _, s, _ in s = true }
                        .onChanged { _ in
                            if !engine.holding && !pressingStarted {
                                pressingStarted = true
                                engine.press()
                                wake()
                            }
                        }
                        .onEnded { _ in
                            pressingStarted = false
                            engine.release()
                            wake()
                        }
                )
                .onChange(of: pressing) { _, down in
                    if !down { DispatchQueue.main.async { pressingStarted = false; engine.release() } }
                }
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Launch pad. Hold to fuel the rocket.")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .task(id: key) {
            engine.reduced = reduceMotion
            engine.onFeel = { kind in
                if kind == 0 { UISelectionFeedbackGenerator().selectionChanged() } else { UIImpactFeedbackGenerator(style: .medium).impactOccurred() }
            }
            if backKey == key, back != nil { return }
            let px = min(3, Double(displayScale))
            // The scene is baked off the main thread; the loop starts at 0 once it is in.
            let bitmap = await Task.detached(priority: .userInitiated) { RocketLaunchProgressArt.bakeBack(L, px: px) }.value
            back = Image(decorative: bitmap.image, scale: bitmap.px)
            backKey = key
        }
        .onChange(of: reduceMotion) { _, r in engine.reduced = r }
    }

    @State private var pressingStarted = false

    private func wake() {
        awake = true
        scheduleSleep()
    }

    /// Outside autoPlay the timeline stops once nothing moves.
    private func scheduleSleep() {
        DispatchQueue.main.asyncAfter(deadline: .now() + 1.5) {
            if engine.active { scheduleSleep() } else { awake = false }
        }
    }

    // MARK: - Pad

    private func drawPad(_ ctx: GraphicsContext, _ L: RocketLaunchProgressLayout, _ f: RocketLaunchProgressFrame) {
        // The contrail: a wide soft ribbon and a brighter core.
        if f.trailA > 0.001 {
            let trail = RocketLaunchProgressEngine.trail(f.t, L)
            var c = ctx
            c.opacity = f.trailA
            c.drawLayer { l in
                var a = l
                a.addFilter(.blur(radius: 9))
                a.fill(trail, with: .color(Color(.sRGB, red: 232 / 255, green: 224 / 255, blue: 240 / 255, opacity: 0.35)))
                var b = l
                b.addFilter(.blur(radius: 2.5))
                b.fill(trail, with: .color(Color(.sRGB, red: 1, green: 250 / 255, blue: 246 / 255, opacity: 0.55)))
            }
        }
        // The fuel, clipped to the tank.
        let tank = L.tank
        let tankShape = Path(roundedRect: tank, cornerRadius: tank.width / 2)
        var fuel = ctx
        fuel.clip(to: tankShape)
        fuel.fill(
            RocketLaunchProgressEngine.slosh(min(1, f.fuel / 100), f.t, f.moving, tank),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: P.hex(0xC2561C), location: 0), .init(color: P.hex(0xFFB04F), location: 0.35),
                    .init(color: P.hex(0xFFD58A), location: 0.55), .init(color: P.hex(0xD86A22), location: 1),
                ]),
                startPoint: CGPoint(x: tank.minX, y: 0), endPoint: CGPoint(x: tank.maxX, y: 0)
            )
        )
        ctx.stroke(tankShape, with: .color(.white.opacity(0.18)), lineWidth: 1)
        ctx.fill(Path(roundedRect: CGRect(x: tank.minX + 5, y: tank.minY + 8, width: 4, height: tank.height - 16), cornerRadius: 2), with: .color(.white.opacity(0.16)))

        // The rocket.
        guard f.on > 0.001 else { return }
        let parts = RocketLaunchProgressArt.parts
        var r = ctx
        r.opacity = f.on
        r.translateBy(x: f.rx, y: f.ry)
        r.rotate(by: .radians(f.rot))
        r.scaleBy(x: L.k, y: L.k)
        func across(_ x0: Double, _ x1: Double, _ stops: [(UInt32, Double)]) -> GraphicsContext.Shading {
            .linearGradient(
                Gradient(stops: stops.map { .init(color: P.hex($0.0), location: $0.1) }),
                startPoint: CGPoint(x: x0, y: 0), endPoint: CGPoint(x: x1, y: 0)
            )
        }
        r.fill(parts.nozzle, with: across(-8, 8, [(0x2A2630, 0), (0x6E6878, 0.4), (0x1E1A24, 1)]))
        r.fill(parts.finL, with: .color(P.hex(0x9E2A22)))
        r.fill(parts.finR, with: .color(P.hex(0x7E1F1A)))
        r.fill(parts.body, with: across(-15, 15, [(0x7C7686, 0), (0xE9E4EE, 0.3), (0xFFFFFF, 0.45), (0xC7C0CF, 0.75), (0x5E5868, 1)]))
        r.fill(parts.band, with: across(-15, 15, [(0x6E1A16, 0), (0xE3523A, 0.4), (0xA52A20, 1)]))
        r.fill(parts.nose, with: across(-15, 15, [(0x6E1A16, 0), (0xF06A4A, 0.35), (0xE3523A, 0.55), (0x7A1E18, 1)]))
        r.fill(parts.finC, with: across(-2.5, 2.5, [(0xB8352A, 0), (0xE05A44, 0.5), (0x8E2420, 1)]))
        r.fill(Path(roundedRect: CGRect(x: -5.5, y: -84, width: 11, height: 11), cornerRadius: 5.5), with: .color(P.hex(0x2E3B5E)))
        r.fill(Path(roundedRect: CGRect(x: -4, y: -82.5, width: 5, height: 4), cornerRadius: 2), with: .color(Color(.sRGB, red: 190 / 255, green: 220 / 255, blue: 1, opacity: 0.75)))
    }

    // MARK: - Text

    private func header(_ t: Double) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("SHIPWRIGHT")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2)
                .foregroundStyle(P.amber)
            Text("Deploying v4.2.0")
                .font(.system(size: 32, weight: .heavy))
                .kerning(-0.5)
                .foregroundStyle(P.ink)
            // One line in, the previous one out upward: never two strings faded over each other in place.
            ZStack(alignment: .topLeading) {
                ForEach(M.status.indices, id: \.self) { i in
                    let s = M.statusLine(i, t)
                    Text(M.status[i])
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(P.inkSoft)
                        .lineLimit(1)
                        .opacity(s.o)
                        .offset(y: s.y)
                }
            }
            .frame(maxWidth: .infinity, alignment: .topLeading)
            .frame(height: 22, alignment: .topLeading)
            .clipped()
        }
    }

    private static let digitH = 56.0

    /// Column `place` (1, 10, 100): the ones roll with the value, higher columns turn over as the
    /// one below passes 9.
    private func column(_ place: Double, _ fuel: Double) -> some View {
        let v = max(0, fuel)
        let m = v / place
        let base = floor(m)
        let pos = place == 1 ? m : base + max(0, (m - base) * 10 - 9)
        let shown = place == 1 ? 1 : (place == 10 ? min(1, max(0, v - 9)) : min(1, max(0, v - 99)))
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 52, weight: .bold).monospacedDigit())
                    .foregroundStyle(P.ink)
                    .frame(width: 32, height: Self.digitH)
            }
        }
        .offset(y: -pos.truncatingRemainder(dividingBy: 10) * Self.digitH)
        .opacity(shown)
        .frame(width: 32, height: Self.digitH, alignment: .top)
        .clipped()
    }

    private func odometer(_ f: RocketLaunchProgressFrame) -> some View {
        // Right-aligned columns; the readout steps aside while the countdown and the badge have the sky.
        let away = min(1, max(0, (f.t - 4.3) / 0.2)) * (1 - min(1, max(0, (f.t - 9.3) / 0.3)))
        return VStack(alignment: .leading, spacing: 2) {
            Text("DEPLOY PROGRESS")
                .font(.system(size: 10, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(P.inkFaint)
            HStack(alignment: .bottom, spacing: 0) {
                column(100, f.fuel)
                column(10, f.fuel)
                column(1, f.fuel)
                Text("%")
                    .font(.system(size: 24, weight: .bold))
                    .foregroundStyle(P.amber)
                    .padding(.bottom, 9)
                    .padding(.leading, 2)
            }
        }
        .opacity(1 - away)
        .offset(y: -10 * away)
    }

    private func countdown(_ t: Double) -> some View {
        ZStack {
            ForEach(0..<3, id: \.self) { k in
                let d = M.countDigit(k, t)
                Text("\(3 - k)")
                    .font(.system(size: 96, weight: .heavy).monospacedDigit())
                    .foregroundStyle(P.ink)
                    .shadow(color: Color(.sRGB, red: 1, green: 150 / 255, blue: 80 / 255, opacity: 0.45), radius: 12)
                    .scaleEffect(d.s)
                    .offset(y: d.y)
                    .opacity(d.o)
            }
        }
    }

    private func badge(_ t: Double) -> some View {
        let b = M.deployedBadge(t)
        return HStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 20))
                .foregroundStyle(P.amber)
            Text("v4.2.0 is live")
                .font(.system(size: 16, weight: .bold))
                .foregroundStyle(P.ink)
        }
        .padding(.horizontal, 16)
        .frame(height: 44)
        .background(
            Capsule()
                .fill(Color(.sRGB, red: 30 / 255, green: 24 / 255, blue: 52 / 255, opacity: 0.86))
                .overlay(Capsule().strokeBorder(P.amber.opacity(0.4), lineWidth: 1))
                .shadow(color: .black.opacity(0.35), radius: 15, y: 10)
        )
        .scaleEffect(b.s)
        .opacity(b.o)
    }

    private static let stepNames = [("Build", "2,184 modules · 41 s"), ("Test", "612 checks passed"), ("Upload", "38.4 MB to 3 regions"), ("Launch", "Traffic 0 → 100 %")]

    private func steps(_ L: RocketLaunchProgressLayout, _ f: RocketLaunchProgressFrame) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(alignment: .firstTextBaseline) {
                Text("Pipeline")
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(P.ink)
                Spacer(minLength: 0)
                Text("Hold the pad to fuel")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(P.inkFaint)
            }
            .padding(.bottom, 6)
            ForEach(0..<4, id: \.self) { i in
                stepRow(i, state: M.stepState(i, f.t, f.fuel), spin: f.spin)
                    .frame(height: L.rowH)
            }
            Spacer(minLength: 0)
        }
        .padding(.horizontal, 18)
        .padding(.top, 16)
        .background(
            RoundedRectangle(cornerRadius: 26, style: .continuous)
                .fill(P.card)
                .overlay(RoundedRectangle(cornerRadius: 26, style: .continuous).strokeBorder(.white.opacity(0.06), lineWidth: 1))
                .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.4), radius: 20, y: 18)
        )
    }

    private func stepRow(_ i: Int, state: Int, spin: Double) -> some View {
        HStack(spacing: 14) {
            ZStack {
                Circle()
                    .strokeBorder(P.ink.opacity(0.35), lineWidth: 1.5)
                    .frame(width: 10, height: 10)
                    .opacity(state == 0 ? 1 : 0)
                ZStack {
                    Circle().strokeBorder(P.amber.opacity(0.18), lineWidth: 2.5)
                    // The ring's lit top edge, as a border-top on a round view draws it.
                    Circle()
                        .inset(by: 1.25)
                        .trim(from: 0.625, to: 0.875)
                        .stroke(P.amber, lineWidth: 2.5)
                }
                .frame(width: 22, height: 22)
                .rotationEffect(.radians(spin))
                .opacity(state == 1 ? 1 : 0)
                Circle()
                    .fill(P.amber)
                    .frame(width: 22, height: 22)
                    .overlay(
                        Image(systemName: "checkmark")
                            .font(.system(size: 12, weight: .bold))
                            .foregroundStyle(P.hex(0x2A1606))
                    )
                    .scaleEffect(state == 2 ? 1 : 0.85)
                    .opacity(state == 2 ? 1 : 0)
            }
            .frame(width: 24, height: 24)
            HStack(alignment: .firstTextBaseline, spacing: 10) {
                Text(Self.stepNames[i].0)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(P.ink)
                    .fixedSize()
                Spacer(minLength: 0)
                Text(Self.stepNames[i].1)
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(P.inkSoft)
                    .lineLimit(1)
            }
            .frame(minWidth: 0, maxWidth: .infinity)
            .opacity(state == 0 ? 0.5 : 1)
        }
    }
}
