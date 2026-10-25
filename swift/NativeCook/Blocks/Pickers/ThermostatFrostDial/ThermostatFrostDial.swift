import SwiftUI
import UIKit

/*
 Thermostat Frost Dial — "Hearthline", the living room's thermostat in a smart-home app.

 Look: a slatted oak wall with a round thermostat on it: a matte ceramic ring with fine grip
 grooves and a set-point notch, around a dark face with 41 ticks (10° – 30°, every 0.5°) and the
 set point in big light digits. Turning colder grows frost over the ring from the rim inward and
 cools the wall to steel blue; turning warmer melts the frost into beads that dry off, the wall
 glows amber and the air above the dial shimmers. One full-screen Metal shader
 (ThermostatFrostDialShader.metal), views for the text and controls.

 autoPlay: loop = 9 s (21° → 16° → 25° → 21°, script in ThermostatFrostDialMotion.swift).
 Touch: drag around the dial to turn it (13.5° of turn per degree, rubber-band past 10° and 30°);
 on release a spring carries the finger's speed and settles on the nearest 0.5°; a selection
 haptic at every 0.5° step. 2 s after the last touch the dial eases back to 21° (0.9 s) and the
 loop restarts. Reduced motion: 21°, still; the dial still turns under a finger.
*/
struct ThermostatFrostDial: View {
    /// Plays the 9 s cold-warm-rest loop until touched.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var sim = ThermostatFrostDialSim()
    @State private var touches = 0
    @State private var held = false
    @State private var awake = false

    private typealias M = ThermostatFrostDialMotion
    private typealias P = ThermostatFrostDialPalette

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
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
        let g = ThermostatFrostDialGeo(w: max(1, size.width), h: max(1, size.height),
                                       insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        // Without autoPlay the clock runs only while a finger has the dial and for a while after,
        // until the release spring has settled and the melt water has dried.
        let paused = !auto && !awake
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = sim.step(to: timeline.date, auto: auto, reduced: reduced)
            let _ = feelDetent()
            ZStack(alignment: .topLeading) {
                P.base
                scene(f, g, size: size)
                ThermostatFrostDialHeader()
                    .frame(width: size.width - 48, height: M.headerH, alignment: .topLeading)
                    .offset(x: 24, y: insets.top + 10)
                readout(f, g)
                ThermostatFrostDialBottom()
                    .frame(width: size.width - 40, height: M.bottomH)
                    .offset(x: 20, y: size.height - max(insets.bottom, 12) - M.bottomH)
                Color.clear
                    .frame(width: g.ro * 2 + 32, height: g.ro * 2 + 32)
                    .contentShape(Circle())
                    .gesture(turn(g))
                    .offset(x: g.cx - g.ro - 16, y: g.cy - g.ro - 16)
                    .accessibilityElement()
                    .accessibilityLabel("Set temperature. Turn the dial.")
                    .accessibilityValue(String(format: "%.1f degrees", f.T))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .coordinateSpace(name: ThermostatFrostDialPalette.space)
        }
        .clipped()
    }

    private func feelDetent() -> Bool {
        if sim.takeDetent() { UISelectionFeedbackGenerator().selectionChanged() }
        return true
    }

    private func scene(_ f: ThermostatFrostDialFrame, _ g: ThermostatFrostDialGeo, size: CGSize) -> some View {
        let T = min(M.maxT + 1.5, max(M.minT - 1.5, f.T))
        let heating: Float = T >= M.room ? 1 : 0
        let litTo = M.angleOf(min(M.maxT, max(M.minT, (T * 2).rounded() / 2)))
        let shader = ShaderLibrary.default.ThermostatFrostDialScene(
            .float2(Float(g.w), Float(g.h)),
            .float4(Float(g.cx), Float(g.cy), Float(g.ro), Float(g.ri)),
            .float4(Float(M.angleOf(T) - M.angleOf(M.rest)), Float(M.angleOf(T)), Float(f.frost), Float(f.wet)),
            .float4(Float(M.easeInOut(M.coldOf(T))), Float(M.easeInOut(M.warmOf(T))),
                    Float(f.phase - floor(f.phase)), 0),
            .float4(Float(M.angleOf(M.room)), Float(litTo), heating, 0)
        )
        return Rectangle()
            .colorEffect(shader)
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
    }

    private func readout(_ f: ThermostatFrostDialFrame, _ g: ThermostatFrostDialGeo) -> some View {
        let wheels = M.digitWheels(f.T)
        let heat = sim.heating
        return ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                // "Heating to" / "Cooling to" rolls when the set point crosses the room's temperature.
                ZStack {
                    Text("HEATING TO")
                        .foregroundStyle(P.hex(0xF5B46A))
                        .opacity(heat)
                        .offset(y: (1 - heat) * -14)
                    Text("COOLING TO")
                        .foregroundStyle(P.hex(0x9CCBF2))
                        .opacity(1 - heat)
                        .offset(y: heat * 14)
                }
                .font(.system(size: 12, weight: .bold))
                .kerning(1.6)
                .frame(width: 160, height: 18)
                .clipped()
                HStack(alignment: .top, spacing: 0) {
                    ThermostatFrostDialWheel(position: wheels.0, strip: P.digits)
                    ThermostatFrostDialWheel(position: wheels.1, strip: P.digits)
                    ThermostatFrostDialBig(text: ".")
                    ThermostatFrostDialWheel(position: wheels.2, strip: P.halves)
                    Text("°")
                        .font(.system(size: 44, weight: .thin))
                        .kerning(-1)
                        .foregroundStyle(P.ink)
                        .frame(height: 64)
                        .padding(.leading, 2)
                }
                .frame(height: P.digitH, alignment: .top)
                .padding(.top, 4)
                Text("Indoor 20.5°")
                    .font(.system(size: 14, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(P.soft)
                    .padding(.top, 4)
            }
            .frame(width: 240, height: 140, alignment: .top)
            .offset(x: g.cx - 120, y: g.cy - 70)
            rangeLabel("10°", x: g.cx - g.ri * 0.5 - 14, y: g.cy + g.ri * 0.5 - 7)
            rangeLabel("30°", x: g.cx + g.ri * 0.5 - 14, y: g.cy + g.ri * 0.5 - 7)
        }
        .allowsHitTesting(false)
    }

    private func rangeLabel(_ s: String, x: Double, y: Double) -> some View {
        Text(s)
            .font(.system(size: 11, weight: .semibold))
            .foregroundStyle(P.hex(0xF2EDE6, 0.45))
            .fixedSize()
            .frame(width: 28)
            .offset(x: x, y: y)
    }

    private func angle(_ p: CGPoint, _ g: ThermostatFrostDialGeo) -> Double {
        atan2(p.y - g.cy, p.x - g.cx)
    }

    private func turn(_ g: ThermostatFrostDialGeo) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named(ThermostatFrostDialPalette.space))
            .onChanged { v in
                if !held {
                    held = true
                    awake = true
                    touches += 1
                    sim.grab(angle(v.startLocation, g))
                }
                sim.turn(angle(v.location, g))
            }
            .onEnded { _ in
                held = false
                sim.release()
                let mark = touches
                // Let the spring settle and the beads dry, then let the clock rest.
                Task { @MainActor in
                    try? await Task.sleep(for: .seconds(9))
                    if touches == mark && !held { awake = false }
                }
            }
    }
}

enum ThermostatFrostDialPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255, opacity: a)
    }

    static let space = "ThermostatFrostDialStage"
    static let base = hex(0x3A3530)
    static let ink = hex(0xF2EDE6)
    static let soft = hex(0xF2EDE6, 0.62)
    static let digitH = 76.0
    static let digits = ["0", "1", "2", "3", "4", "5", "6", "7", "8", "9", "0"]
    static let halves = ["0", "5", "0"]
}

private struct ThermostatFrostDialBig: View {
    let text: String

    var body: some View {
        Text(text)
            .font(.system(size: 66, weight: .thin))
            .monospacedDigit()
            .kerning(-1)
            .foregroundStyle(ThermostatFrostDialPalette.ink)
            .frame(height: ThermostatFrostDialPalette.digitH)
    }
}

/// One column of the big number: a strip of digits behind a one-digit window.
private struct ThermostatFrostDialWheel: View {
    let position: Double
    let strip: [String]

    var body: some View {
        VStack(spacing: 0) {
            ForEach(Array(strip.enumerated()), id: \.offset) { _, d in
                ThermostatFrostDialBig(text: d)
            }
        }
        .offset(y: -position * ThermostatFrostDialPalette.digitH)
        .frame(height: ThermostatFrostDialPalette.digitH, alignment: .top)
        .clipped()
    }
}

private struct ThermostatFrostDialHeader: View {
    private typealias P = ThermostatFrostDialPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Path { p in
                    p.addLines([CGPoint(x: 2.5, y: 7.4), CGPoint(x: 8, y: 2.8), CGPoint(x: 13.5, y: 7.4),
                                CGPoint(x: 13.5, y: 13.2), CGPoint(x: 2.5, y: 13.2)])
                    p.closeSubpath()
                    p.addLines([CGPoint(x: 6.4, y: 13.2), CGPoint(x: 6.4, y: 9.6), CGPoint(x: 9.6, y: 9.6),
                                CGPoint(x: 9.6, y: 13.2)])
                }
                .stroke(P.ink, style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
                .frame(width: 16, height: 16)
                Text("HEARTHLINE")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2)
                    .foregroundStyle(P.ink)
                Spacer(minLength: 0)
                Text("MR")
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(P.ink)
                    .frame(width: 32, height: 32)
                    .background(Circle().fill(P.hex(0xF2EDE6, 0.14)))
                    .overlay(Circle().strokeBorder(P.hex(0xF2EDE6, 0.3), lineWidth: 0.5))
            }
            .frame(height: 32)
            Text("Living room")
                .font(.system(size: 34, weight: .bold))
                .kerning(-0.5)
                .foregroundStyle(P.ink)
                .padding(.top, 10)
            Text("Indoor 20.5° · Humidity 41% · Eco on")
                .font(.system(size: 15, weight: .medium))
                .monospacedDigit()
                .foregroundStyle(P.soft)
                .padding(.top, 4)
        }
        .allowsHitTesting(false)
    }
}

private struct ThermostatFrostDialBottom: View {
    private typealias P = ThermostatFrostDialPalette
    private static let modes = ["Heat", "Cool", "Auto", "Eco"]

    var body: some View {
        VStack(spacing: 14) {
            HStack(spacing: 0) {
                ForEach(Self.modes, id: \.self) { m in
                    let on = m == "Auto"
                    Text(m)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(on ? P.hex(0x2A2622) : P.soft)
                        .frame(maxWidth: .infinity, maxHeight: .infinity)
                        .background {
                            if on {
                                Capsule().fill(P.hex(0xF2EDE6, 0.92))
                                    .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                                    .overlay {
                                        Capsule().strokeBorder(
                                            LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)],
                                                           startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.15)),
                                            lineWidth: 1)
                                    }
                            }
                        }
                }
            }
            .padding(4)
            .frame(height: 44)
            .background(Capsule().fill(P.hex(0x141210, 0.42)))
            .overlay(Capsule().strokeBorder(P.hex(0xF2EDE6, 0.14), lineWidth: 0.5))

            let card = RoundedRectangle(cornerRadius: 24, style: .continuous)
            VStack(alignment: .leading, spacing: 10) {
                Text("TODAY")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(P.hex(0xF2EDE6, 0.5))
                row("18:00", "Evening", "21°", active: true)
                row("22:30", "Night", "18°", active: false)
                HStack(spacing: 8) {
                    Path { p in
                        p.addLines([CGPoint(x: 9, y: 1.8), CGPoint(x: 3.6, y: 9), CGPoint(x: 7.6, y: 9),
                                    CGPoint(x: 6.8, y: 14.2), CGPoint(x: 12.4, y: 6.8), CGPoint(x: 8.4, y: 6.8)])
                        p.closeSubpath()
                    }
                    .fill(P.hex(0xF2B45C))
                    .frame(width: 16, height: 16)
                    Text("3.2 kWh today · 12% under your usual")
                        .font(.system(size: 13, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(P.soft)
                        .lineLimit(1)
                        .minimumScaleFactor(0.8)
                }
                .padding(.top, 10)
                .overlay(alignment: .top) {
                    Rectangle().fill(P.hex(0xF2EDE6, 0.14)).frame(height: 0.5)
                }
            }
            .padding(.horizontal, 18)
            .padding(.vertical, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
            .background {
                card.fill(P.hex(0x181614, 0.55))
                    .shadow(color: .black.opacity(0.3), radius: 20, y: 18)
                    .shadow(color: .black.opacity(0.25), radius: 2, y: 2)
            }
            .overlay(card.strokeBorder(P.hex(0xF2EDE6, 0.14), lineWidth: 0.5))
            .overlay {
                card.strokeBorder(
                    LinearGradient(colors: [.white.opacity(0.08), .white.opacity(0)],
                                   startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)),
                    lineWidth: 1)
            }
        }
    }

    private func row(_ time: String, _ name: String, _ temp: String, active: Bool) -> some View {
        HStack(spacing: 10) {
            Circle()
                .fill(active ? P.hex(0xF2B45C) : P.hex(0xF2EDE6, 0.25))
                .frame(width: 8, height: 8)
                .frame(width: 10, height: 10)
            Text(time)
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(P.ink)
                .frame(width: 48, alignment: .leading)
            Text(name)
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(P.soft)
                .frame(maxWidth: .infinity, alignment: .leading)
            Text(temp)
                .font(.system(size: 15, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(P.ink)
        }
    }
}
