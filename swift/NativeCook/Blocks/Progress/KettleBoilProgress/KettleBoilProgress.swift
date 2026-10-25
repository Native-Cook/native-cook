import SwiftUI

/// Kettle Boil Progress — "Steep", a tea app heating water for a sencha. A glass kettle on a charcoal
/// base: the LED ring round the base fills with the temperature, micro bubbles gather on the floor,
/// streams rise, the surface starts to roll, the kettle clicks off at 80 °C and steam pours from the
/// spout. The temperature rolls up digit by digit.
///
/// Drawing: one Metal colour effect (KettleBoilProgress.metal) over a full-screen rectangle: wall,
/// counter, base and ring, glass, water, bubbles, steam. Its uniforms come from one state computed per
/// frame (KettleBoilProgressMotion.swift); the readout, the status line and the button are views.
///
/// Touch: a tap anywhere starts heating, a tap while it heats cancels (the bubbles settle and it
/// cools); at 80 °C it clicks off by itself. 2 s after the last tap, once the element is off, it eases
/// back to cold still water (0.9 s) and the loop starts again. Reduced motion: still water at 21 °C,
/// no loop; a tap still heats and cancels, the readout changing without rolling.
struct KettleBoilProgress: View {
    /// Plays the 9 s heat-click-steam-cool loop.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = KettleBoilProgressEngine()
    /// Set by the first tap, so the clock runs from then on even without autoPlay.
    @State private var awake = false

    private typealias P = KettleBoilProgressPalette

    private static let status = ["Ready · cold water", "Heating to 80 °C", "Ready to steep", "Cooling down"]
    private static let action = ["Heat to 80 °C", "Cancel", "Start 2 min timer", "Heat again"]

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
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
        let L = KettleBoilProgressLayout(width: size.width, height: size.height, insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        return ZStack(alignment: .topLeading) {
            P.background

            TimelineView(.animation(paused: !(auto || awake))) { timeline in
                let f = engine.step(to: timeline.date, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    scene(f, L: L, size: size)
                    header(size: size, insets: insets)
                    readout(f, size: size, insets: insets)
                    panel(f, size: size, insets: insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .onTapGesture {
                    awake = true
                    engine.tap()
                }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Glass kettle. Tap to heat to 80 degrees, tap again to cancel.")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            awake = true
            engine.tap()
        }
    }

    private func scene(_ f: KettleBoilProgressFrame, L: KettleBoilProgressLayout, size: CGSize) -> some View {
        let s = f.state
        return Rectangle()
            .colorEffect(ShaderLibrary.kettleBoilProgress(
                .float2(size.width, size.height),
                .float4(L.ox, L.oy, L.k, 0),
                .float(s.heat),
                .float2(s.ringFill, s.ringOn),
                .float(s.steam),
                .float(f.phase),
                .float(s.lever)
            ))
            .frame(width: size.width, height: size.height)
    }

    private func header(size: CGSize, insets: EdgeInsets) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("STEEP")
                .font(.system(size: 11, weight: .heavy))
                .kerning(2.2)
                .foregroundStyle(P.mint)
            Text("Sencha, 80 °C")
                .font(.system(size: 30, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(P.ink)
            Text("Japanese green · 250 ml · 2 min")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(P.inkSoft)
        }
        .frame(width: size.width - 48, alignment: .leading)
        .offset(x: 24, y: insets.top + 18)
    }

    private func readout(_ f: KettleBoilProgressFrame, size: CGSize, insets: EdgeInsets) -> some View {
        VStack(alignment: .trailing, spacing: 2) {
            HStack(alignment: .top, spacing: 0) {
                KettleBoilProgressColumn(place: 10, temp: f.temp)
                KettleBoilProgressColumn(place: 1, temp: f.temp)
                Text("°C")
                    .font(.system(size: 20, weight: .medium))
                    .foregroundStyle(P.inkSoft)
                    .padding(.top, 12)
                    .padding(.leading, 4)
            }
            KettleBoilProgressRoller(lines: Self.status, pos: f.statusPos, height: 20, alignment: .trailing) {
                Text($0)
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(P.inkSoft)
            }
        }
        .frame(width: 200, alignment: .trailing)
        .offset(x: size.width - 24 - 200, y: insets.top + 118)
    }

    private func panel(_ f: KettleBoilProgressFrame, size: CGSize, insets: EdgeInsets) -> some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        let width = size.width - 32
        return VStack(spacing: 12) {
            HStack(spacing: 8) {
                chip("drop.fill", "250 ml")
                chip("thermometer.medium", "80 °C")
                chip("timer", "2:00")
            }
            KettleBoilProgressRoller(lines: Self.action, pos: f.statusPos, height: 22, alignment: .center) {
                Text($0)
                    .font(.system(size: 17, weight: .bold))
                    .foregroundStyle(P.mintInk)
            }
            .padding(.horizontal, 20)
            .frame(maxWidth: .infinity)
            .frame(height: 52)
            .background {
                Capsule()
                    .fill(P.mint)
                    .shadow(color: .black.opacity(0.25), radius: 0.5, y: 1)
                    .shadow(color: Color(red: 80 / 255, green: 180 / 255, blue: 120 / 255).opacity(0.22), radius: 10, y: 8)
            }
            .overlay {
                Capsule()
                    .inset(by: 0.5)
                    .stroke(LinearGradient(colors: [.white.opacity(0.45), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.3)), lineWidth: 1)
            }
            Text("Tap the kettle to heat or cancel")
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(P.inkFaint)
                .frame(maxWidth: .infinity)
        }
        .padding(14)
        .frame(width: width)
        .background {
            shape
                .fill(Color(red: 22 / 255, green: 30 / 255, blue: 30 / 255).opacity(0.9))
                .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.4), radius: 20, y: 18)
        }
        .overlay {
            shape.strokeBorder(Color(red: 220 / 255, green: 240 / 255, blue: 230 / 255).opacity(0.07), lineWidth: 1)
        }
        .overlay {
            shape
                .inset(by: 1)
                .stroke(LinearGradient(colors: [Color(red: 230 / 255, green: 245 / 255, blue: 238 / 255).opacity(0.07), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.15)), lineWidth: 1)
        }
        .frame(width: size.width, height: size.height - insets.bottom - 16, alignment: .bottom)
    }

    private func chip(_ icon: String, _ label: String) -> some View {
        HStack(spacing: 6) {
            Image(systemName: icon)
                .font(.system(size: 12, weight: .semibold))
                .foregroundStyle(P.mint)
            Text(label)
                .font(.system(size: 13, weight: .semibold).monospacedDigit())
                .foregroundStyle(P.ink)
        }
        .frame(maxWidth: .infinity)
        .frame(height: 32)
        .background(P.mint.opacity(0.08), in: Capsule())
    }
}

enum KettleBoilProgressPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(.sRGB, red: Double((v >> 16) & 255) / 255, green: Double((v >> 8) & 255) / 255, blue: Double(v & 255) / 255, opacity: a)
    }

    static let background = hex(0x141C1D)
    static let ink = hex(0xEEF3EF)
    static let inkSoft = hex(0xEEF3EF, 0.64)
    static let inkFaint = hex(0xEEF3EF, 0.42)
    static let mint = hex(0xA9E3BE)
    static let mintInk = hex(0x123222)
}

/// One digit wheel: the ones turn with the value, the tens as the ones pass 9.
private struct KettleBoilProgressColumn: View {
    let place: Double
    let temp: Double

    static let digitH = 72.0

    var body: some View {
        let v = max(0, temp)
        let m = v / place
        let base = floor(m)
        let pos = place == 1 ? m : base + max(0, (m - base) * 10 - 9)
        let shift = pos.truncatingRemainder(dividingBy: 10)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: 66, weight: .light).monospacedDigit())
                    .kerning(-2)
                    .foregroundStyle(KettleBoilProgressPalette.ink)
                    .frame(width: 40, height: Self.digitH)
            }
        }
        .offset(y: -shift * Self.digitH)
        .frame(width: 40, height: Self.digitH, alignment: .top)
        .clipped()
    }
}

/// Labels in a clipped box: the old one rolls up and out, the new one in from below.
private struct KettleBoilProgressRoller<Label: View>: View {
    let lines: [String]
    let pos: Double
    let height: Double
    let alignment: Alignment
    @ViewBuilder let label: (String) -> Label

    var body: some View {
        ZStack(alignment: alignment) {
            ForEach(-1...4, id: \.self) { i in
                let d = Double(i) - pos
                label(lines[(i + 4) % 4])
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: alignment)
                    .frame(height: height)
                    .offset(y: d * height)
                    .opacity(max(0, 1 - abs(d) * 1.2))
            }
        }
        .frame(maxWidth: .infinity)
        .frame(height: height)
        .clipped()
    }
}
