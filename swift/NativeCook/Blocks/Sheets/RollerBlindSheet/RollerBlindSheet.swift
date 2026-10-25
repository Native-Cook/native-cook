import SwiftUI
import UIKit

/*
 Roller Blind Sheet — a smart-home room ("Nook — Bedroom") whose bottom sheet is a linen roller
 blind. Pull the bead on its cord down: the blind unrolls from the roller (the roll gets thinner),
 the morning light through the window dims the room and glows through the weave, and the scene
 presets printed on the fabric come down with it. Let go and the hem springs to the nearest detent
 (rolled up, the window's sill, or covering the room's controls) with a bounce, carrying the
 release velocity; a short tug from a detent lets the spring roller wind it back up. The cord
 swings as a pendulum driven by the hem's acceleration.

 The room is one Metal pass (rollerBlindSheetRoom) under the controls; the blind over them is the
 fabric pass (rollerBlindSheetFabric) in a view from the roller to the hem, with the roll, hem bar,
 brackets, blurred shadows, the cord and the bead drawn in Canvases around it. The printed scenes
 are views in a box clipped at the roller and at the hem. Two seconds after the last touch the
 blind eases back to the sill (0.9 s, ease-in-out) and the loop restarts from t = 0.
 Reduced motion: the blind sits at the sill, still; dragging still works, with no bounce.
 */
struct RollerBlindSheet: View {
    var autoPlay = false

    /// The loop's period (s); the manifest's recording loop.
    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = RollerBlindSheetEngine()
    @State private var handActive = false
    @State private var handGeneration = 0
    @State private var touched = false

    private typealias M = RollerBlindSheetMotion
    private typealias S = RollerBlindSheetStyle

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = RollerBlindSheetGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top, bottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let u = g.unit
        return ZStack(alignment: .topLeading) {
            S.background

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let s = engine.step(to: timeline.date, g: g, auto: auto, reduced: reduced, autoPlay: autoPlay)
                let cover = M.coverOf(g, s.hem)
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.rollerBlindSheetRoom(
                            .float2(g.w, g.h),
                            .float4(g.winX, g.winY, g.winW, g.winH),
                            .float(cover * 0.5),
                            .float(u)
                        ))
                        .frame(width: size.width, height: size.height)

                    header(hem: s.hem, g: g, insets: insets)
                    controls(g: g, insets: insets)
                    S.dim.opacity(cover * 0.22)
                        .frame(width: size.width, height: max(0, size.height - (g.controlsY - 10 * u)))
                        .offset(y: g.controlsY - 10 * u)

                    blind(s, g: g)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if !touched {
                                touched = true
                                if engine.grab(g: g, y: v.startLocation.y) {
                                    handGeneration += 1
                                    handActive = true
                                }
                            }
                            engine.pull(g: g, ty: v.translation.height)
                        }
                        .onEnded { v in
                            touched = false
                            engine.letGo(g: g, ty: v.translation.height, vy: v.velocity.height)
                            let gen = handGeneration
                            Task { @MainActor in
                                while gen == handGeneration {
                                    try? await Task.sleep(for: .milliseconds(500))
                                    if gen == handGeneration && engine.resting(autoPlay: autoPlay) {
                                        handActive = false
                                        break
                                    }
                                }
                            }
                        }
                )
                .accessibilityElement()
                .accessibilityLabel("Roller blind. Pull the bead down to lower it; a short tug rolls it up.")
                .accessibilityAddTraits(.allowsDirectInteraction)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private func header(hem: Double, g: RollerBlindSheetGeo, insets: EdgeInsets) -> some View {
        let closed = max(0, min(100, (hem - g.up) / (g.full - g.up) * 100))
        return VStack(alignment: .leading, spacing: 0) {
            Text("NOOK · BEDROOM")
                .font(.system(size: 11, weight: .bold))
                .kerning(2)
                .foregroundStyle(S.accent)
            Text("Bedroom")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(S.ink)
                .padding(.top, 6)
            HStack(alignment: .center, spacing: 0) {
                Text("21.5 °C · 3 lights on · Blind ")
                    .font(S.meta.font)
                    .foregroundStyle(S.meta.color)
                    .fixedSize()
                RollerBlindSheetRollNumber(value: closed, digits: 3, line: 20, style: S.meta, suffix: "% closed")
            }
            .frame(height: 20)
            .padding(.top, 4)
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 14)
        .frame(width: g.w, alignment: .leading)
    }

    private func controls(g: RollerBlindSheetGeo, insets: EdgeInsets) -> some View {
        let height = max(0, g.h - insets.bottom - 16 - g.controlsY)
        return VStack(spacing: 12) {
            HStack(spacing: 12) {
                card("Ceiling light", "60 %", .bulb)
                card("Bedside lamp", "Warm · 30 %", .lamp)
            }
            .frame(maxHeight: 112)
            HStack(spacing: 12) {
                card("Heating", "21.5 °C", .thermo)
                card("Air purifier", "Auto · quiet", .fan)
            }
            .frame(maxHeight: 112)
        }
        .frame(width: g.w - 40, height: height, alignment: .top)
        .offset(x: 20, y: g.controlsY)
    }

    private func card(_ label: String, _ value: String, _ icon: RollerBlindSheetIcon) -> some View {
        let hair = 1 / displayScale
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return ZStack(alignment: .topLeading) {
            icon.view(color: S.ink)
            VStack(alignment: .leading, spacing: 2) {
                Spacer(minLength: 0)
                Text(label)
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(S.mute)
                    .lineLimit(1)
                Text(value)
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(S.ink)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
        }
        .padding(14)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .background {
            ZStack {
                shape.fill(S.cardFill)
                    .shadow(color: Color.rgba(60, 44, 30, 0.10), radius: 12, y: 10)
                    .shadow(color: Color.rgba(60, 44, 30, 0.12), radius: 1, y: 1)
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)), lineWidth: 1)
                shape.strokeBorder(S.ink.opacity(0.1), lineWidth: hair)
            }
        }
    }

    @ViewBuilder
    private func blind(_ s: RollerBlindSheetState, g: RollerBlindSheetGeo) -> some View {
        let u = g.unit
        let hem = s.hem
        let fabricH = max(0, hem - g.rollerY)
        let rollR = M.rollRadius(g, hem)
        let a = s.cordDeg * .pi / 180
        let cordLen = RollerBlindSheetEngine.cordLen * u
        let top = CGPoint(x: g.w / 2, y: hem + 8 * u)
        let bead = CGPoint(x: g.w / 2 + sin(a) * cordLen, y: hem + 8 * u + cos(a) * cordLen)
        let fingerAlpha = s.finger * 0.9

        // Shadow of the blind and a plain linen backing under the shaded fabric.
        Canvas { ctx, _ in
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 6 * u))
                c.fill(Path(CGRect(x: g.x0 - 3 * u, y: g.rollerY, width: g.x1 - g.x0 + 6 * u, height: fabricH + 10 * u)),
                       with: .color(Color.rgba(40, 28, 18, 0.22)))
            }
            ctx.fill(Path(CGRect(x: g.x0, y: g.rollerY, width: g.x1 - g.x0, height: fabricH)), with: .color(S.backing))
        }
        .frame(width: g.w, height: g.h)

        Rectangle()
            .colorEffect(ShaderLibrary.rollerBlindSheetFabric(
                .float2(g.x0, g.rollerY),
                .float4(g.winX, g.winY, g.winW, g.winH),
                .float4(g.x0, g.x1, g.rollerY, rollR),
                .float4(hem, 0, 1 - M.coverOf(g, hem) * 0.3, u)
            ))
            .frame(width: g.x1 - g.x0, height: fabricH)
            .offset(x: g.x0, y: g.rollerY)

        Canvas { ctx, _ in
            ctx.drawLayer { c in
                c.addFilter(.blur(radius: 5 * u))
                c.fill(Path(CGRect(x: g.x0, y: hem + 6 * u, width: g.x1 - g.x0, height: 10 * u)),
                       with: .color(Color.rgba(40, 28, 18, 0.25)))
            }
            // the hem bar
            let barY = hem - 2 * u
            ctx.fill(Path(roundedRect: CGRect(x: g.x0 - 4 * u, y: barY, width: g.x1 - g.x0 + 8 * u, height: 11 * u), cornerRadius: 3 * u),
                     with: .linearGradient(Gradient(colors: S.hemColors), startPoint: CGPoint(x: 0, y: barY), endPoint: CGPoint(x: 0, y: barY + 11 * u)))
            // the roll
            let rollY = g.rollerY - rollR
            ctx.fill(Path(roundedRect: CGRect(x: g.x0 - 4 * u, y: rollY, width: g.x1 - g.x0 + 8 * u, height: rollR * 2), cornerRadius: rollR),
                     with: .linearGradient(S.rollGradient, startPoint: CGPoint(x: 0, y: rollY), endPoint: CGPoint(x: 0, y: rollY + rollR * 2)))
            // the brackets
            for bx in [g.x0 - 15 * u, g.x1 + 2 * u] {
                ctx.fill(Path(roundedRect: CGRect(x: bx, y: g.rollerY - 16 * u, width: 13 * u, height: 32 * u), cornerRadius: 3 * u),
                         with: .linearGradient(Gradient(colors: S.bracketColors), startPoint: CGPoint(x: 0, y: g.rollerY - 16 * u),
                                               endPoint: CGPoint(x: 0, y: g.rollerY + 16 * u)))
            }
            // the cord and its bead
            var cord = Path()
            cord.move(to: top)
            cord.addLine(to: bead)
            ctx.stroke(cord, with: .color(S.cord), style: StrokeStyle(lineWidth: 1.6 * u, lineCap: .round))
            let bc = CGPoint(x: bead.x, y: bead.y + 9)
            ctx.fill(Path(ellipseIn: CGRect(x: bc.x - 9 * u, y: bc.y - 9 * u, width: 18 * u, height: 18 * u)),
                     with: .radialGradient(Gradient(colors: S.beadColors), center: CGPoint(x: bead.x - 3, y: bead.y + 6), startRadius: 0, endRadius: 12 * u))
            if fingerAlpha > 0.001 {
                let ring = Path(ellipseIn: CGRect(x: bc.x - 20 * u, y: bc.y - 20 * u, width: 40 * u, height: 40 * u))
                ctx.opacity = fingerAlpha
                ctx.stroke(ring, with: .color(Color.rgba(255, 250, 240, 0.85)), lineWidth: 2)
                ctx.fill(ring, with: .color(Color.rgba(255, 250, 240, 0.18)))
            }
        }
        .frame(width: g.w, height: g.h)

        // The scenes printed on the fabric, in a box from under the roll down to the hem.
        printed(u: u)
            .frame(width: g.x1 - g.x0, height: max(0, hem - g.rollerY - 14 * u), alignment: .bottom)
            .clipped()
            .offset(x: g.x0, y: g.rollerY + 14 * u)
    }

    private func printed(u: Double) -> some View {
        let hair = 1 / displayScale
        return VStack(alignment: .leading, spacing: 0) {
            Spacer(minLength: 0)
            Text("SCENES")
                .font(.system(size: 11, weight: .bold))
                .kerning(2.4)
                .foregroundStyle(S.printMute)
            Text("Set the mood")
                .font(.system(size: 24, weight: .heavy))
                .kerning(-0.3)
                .foregroundStyle(S.print)
                .padding(.top, 4)
                .padding(.bottom, 10)
            ForEach(S.scenes, id: \.title) { scene in
                HStack(spacing: 14) {
                    scene.icon.view(color: S.print, size: 20)
                        .frame(width: 36, height: 36)
                        .overlay(Circle().strokeBorder(S.printRing, lineWidth: 1.2))
                    VStack(alignment: .leading, spacing: 1) {
                        Text(scene.title)
                            .font(.system(size: 16, weight: .bold))
                            .foregroundStyle(S.print)
                        Text(scene.detail)
                            .font(.system(size: 13))
                            .foregroundStyle(S.printMute)
                    }
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
                .frame(height: 54)
                .overlay(alignment: .top) { Rectangle().fill(S.printLine).frame(height: hair) }
            }
        }
        .padding(.horizontal, 28)
        .frame(height: 292, alignment: .bottom)
        .padding(.bottom, 12 * u)
    }
}

enum RollerBlindSheetStyle {
    static let background = Color(hex: 0xE6DED2)
    static let ink = Color(hex: 0x2B2622)
    static let mute = Color(hex: 0x2B2622, opacity: 0.6)
    static let print = Color(hex: 0x4A3626)
    static let printMute = Color(hex: 0x6E5844)
    static let printLine = Color.rgba(78, 60, 44, 0.28)
    static let printRing = Color.rgba(78, 60, 44, 0.4)
    static let accent = Color(hex: 0xC9793A)
    static let dim = Color(hex: 0x2A221C)
    static let backing = Color(hex: 0xD9CDB8)
    static let cardFill = Color.rgba(252, 249, 244, 0.86)
    static let cord = Color(hex: 0x8C7B66)
    static let hemColors = [Color(hex: 0xC49A6C), Color(hex: 0x93694A), Color(hex: 0x5E4029)]
    static let rollGradient = Gradient(stops: [
        .init(color: Color(hex: 0x9E9482), location: 0), .init(color: Color(hex: 0xEDE4D3), location: 0.32),
        .init(color: Color(hex: 0xD8CDB9), location: 0.7), .init(color: Color(hex: 0x8A806F), location: 1),
    ])
    static let bracketColors = [Color(hex: 0xF1EFEC), Color(hex: 0xB9B5AF), Color(hex: 0x8E8A84)]
    static let beadColors = [Color(hex: 0xD8A877), Color(hex: 0x9C6A41), Color(hex: 0x5E3C22)]
    static let meta = RollerBlindSheetRollFont(size: 14, weight: .medium, color: mute)

    struct Scene {
        let title: String
        let detail: String
        let icon: RollerBlindSheetIcon
    }

    static let scenes = [
        Scene(title: "Wind down", detail: "Lamps at 20 %, 2700 K", icon: .moon),
        Scene(title: "Reading", detail: "Bedside lamp, warm white", icon: .book),
        Scene(title: "Sleep", detail: "Everything off, blind closed", icon: .sleep),
        Scene(title: "Wake gently", detail: "Sunrise light from 06:45", icon: .sunrise),
    ]
}

/// What one frame shows: the hem's y, the cord's angle (degrees) and the finger on the bead.
struct RollerBlindSheetState {
    var hem: Double
    var cordDeg: Double
    var finger: Double
}

/// The clock, the autoPlay script and the hand-driven spring, stepped once per timeline frame.
@MainActor
final class RollerBlindSheetEngine {
    private typealias M = RollerBlindSheetMotion

    static let idle = 2.0
    static let returnTime = 0.9
    /// The hem's spring after a release (ω = 2π / 0.5 s, ζ 0.55) and the cord's pendulum (1.4 Hz).
    private static let kHem = 158.0
    private static let cHem = 13.8
    private static let wCord = Double.pi * 2 * 1.4
    static let cordLen = 64.0

    private enum Mode { case auto, hand, back }

    private var mode = Mode.auto
    private var last: Date?
    private var now = 0.0
    private var autoStart = 0.0
    private var lastTouch = 0.0
    private var touching = false
    /// Hem [y, velocity, target], cord [angle (rad), angular velocity], drag [start hem, from a detent].
    private var hem: [Double] = [0, 0, 0]
    private var cord: [Double] = [0, 0]
    private var dragStart = 0.0
    private var dragFromDetent = false
    private var landed = true
    private var returnFrom: [Double] = [0, 0]
    private var returnStart = 0.0
    private var current = RollerBlindSheetState(hem: 0, cordDeg: 0, finger: 0)
    private var lastSide = -1
    private let impact = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    /// Whether nothing moves any more after a touch (the timeline may pause).
    func resting(autoPlay: Bool) -> Bool {
        if mode == .auto { return true }
        return mode == .hand && !autoPlay && !touching && abs(hem[1]) < 1 && abs(hem[0] - hem[2]) < 0.5 && abs(cord[0]) < 0.002 && abs(cord[1]) < 0.02
    }

    func step(to date: Date, g: RollerBlindSheetGeo, auto: Bool, reduced: Bool, autoPlay: Bool) -> RollerBlindSheetState {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let adv = raw > 0.25 || raw < 0 ? 0 : raw
        now += adv
        let dt = min(0.033, adv)

        if mode == .hand {
            var h = hem
            var c = cord
            if !touching {
                // the hem springs to its detent carrying the release velocity
                let before = h[0] - h[2]
                let acc = -Self.kHem * (h[0] - h[2]) - (reduced ? 2 * sqrt(Self.kHem) : Self.cHem) * h[1]
                h[1] += acc * dt
                h[0] += h[1] * dt
                if h[0] < g.up - 18 * g.unit {
                    h[0] = g.up - 18 * g.unit
                    h[1] = 0
                }
                if !landed && before * (h[0] - h[2]) <= 0 {
                    landed = true
                    impact.impactOccurred()
                }
                // the cord swings on (a lightly damped pendulum), kicked when the hem stops hard
                c[1] += (-Self.wCord * Self.wCord * c[0] - 1.2 * c[1]) * dt
                c[1] += max(-3000, min(3000, acc)) / (Self.cordLen * g.unit) * dt * 0.02
                c[0] += c[1] * dt
                if now - lastTouch > Self.idle && abs(h[1]) < 5 && autoPlay {
                    mode = .back
                    returnStart = now
                    returnFrom = [h[0], c[0]]
                }
            } else {
                c[0] *= exp(-dt * 12)
                c[1] = 0
            }
            hem = h
            cord = c
        } else if mode == .back && now - returnStart >= Self.returnTime {
            mode = .auto
            autoStart = now
        }

        let s: RollerBlindSheetState
        switch mode {
        case .hand:
            s = RollerBlindSheetState(hem: hem[0], cordDeg: cord[0] * 180 / .pi, finger: touching ? 1 : 0)
        case .back:
            let k = M.easeInOut(min(1, (now - returnStart) / Self.returnTime))
            s = RollerBlindSheetState(hem: returnFrom[0] + (g.half - returnFrom[0]) * k, cordDeg: returnFrom[1] * 180 / .pi * (1 - k), finger: 0)
        case .auto:
            if !auto {
                s = RollerBlindSheetState(hem: g.half, cordDeg: 0, finger: 0)
            } else {
                let t = (now - autoStart).truncatingRemainder(dividingBy: RollerBlindSheet.loop)
                s = RollerBlindSheetState(hem: M.hemAt(g, t), cordDeg: M.cordAt(t), finger: M.fingerAt(t))
            }
        }
        current = s

        // a tick as the hem passes the sill while dragged
        let side = mode == .hand && touching ? (s.hem > g.half ? 1 : 0) : -1
        if side >= 0 && lastSide >= 0 && side != lastSide { tick.selectionChanged() }
        lastSide = side
        return s
    }

    /// Takes the hem if the finger lands near it or the bead; returns whether it did.
    func grab(g: RollerBlindSheetGeo, y: Double) -> Bool {
        let s = current
        let near = abs(y - s.hem) < 60 * g.unit || abs(y - (s.hem + Self.cordLen * g.unit)) < 50 * g.unit
        if !near { return false }
        let atDetent = abs(s.hem - g.half) < 6 || abs(s.hem - g.full) < 6
        mode = .hand
        touching = true
        lastTouch = now
        hem = [s.hem, 0, s.hem]
        cord = [s.cordDeg * .pi / 180, 0]
        dragStart = s.hem
        dragFromDetent = atDetent
        tick.selectionChanged()
        return true
    }

    func pull(g: RollerBlindSheetGeo, ty: Double) {
        guard touching else { return }
        hem[0] = M.rubberHem(g, dragStart + ty)
        lastTouch = now
    }

    func letGo(g: RollerBlindSheetGeo, ty: Double, vy: Double) {
        guard touching else { return }
        // a short tug from a detent lets the spring roller wind the blind up
        let tug = dragFromDetent && ty > 4 && ty < 44 * g.unit && vy < 900
        let target = tug ? g.up : M.detentFor(g, hem[0], vy)
        hem = [hem[0], vy, target]
        landed = false
        touching = false
        lastTouch = now
        // the bead swings as the hand lets go
        cord = [0, -max(-6, min(6, vy / 400))]
    }
}
