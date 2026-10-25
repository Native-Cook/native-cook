import SwiftUI
import UIKit

/*
 Tape Measure Picker — "Fitline · Your height": a height picker that is a real tape measure. Pull
 the yellow tape out of its case and the value under the red hairline is your height; let go and
 it springs to the nearest centimetre; flick it left and it reels in to the minimum with a whip.
 The readout above is a mechanical counter whose units wheel turns with the tape.

 One Canvas (TapeMeasurePickerArt: background and case rendered once, the tape drawn per frame
 from the value, only its visible range). Value and wobble: TapeMeasurePickerMotion, analytic in
 autoPlay (loop = 8 s), a spring under the finger.

 Modes: 0 loop · 1 dragging · 2 released, springing to a centimetre · 3 easing back to 178 cm
 (0.9 s) two seconds after the last touch, then the loop restarts.
 */
struct TapeMeasurePicker: View {
    /// Plays the 8 s demo: pull out to 192 cm, reel in to 165 with a whip, back to 178.
    var autoPlay = false

    static let loop = TapeMeasurePickerMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = TapeMeasurePickerEngine()
    /// Keeps the timeline running while a touch's tape is still moving (outside autoPlay).
    @State private var active = false

    fileprivate static let background = Color(hex: 0xEFEBE4)
    fileprivate static let ink = Color(hex: 0x1D1B17)
    fileprivate static let inkSoft = Color(hex: 0x6E685E)
    private static let digitSize = 84.0
    private static let digitLine = 92.0
    private static let digitW = 52.0
    private static let weightKg = 72.0

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
        .background(Self.background.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = TapeMeasurePickerGeo(w: size.width, h: size.height)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let art = engine.art(for: g, scale: displayScale)
        let top = insets.top
        return TimelineView(.animation(paused: !auto && !active)) { timeline in
            let f = engine.step(to: timeline.date, auto: auto, reduced: reduced)
            let cm = Int(min(TapeMeasurePickerMotion.maxValue, max(TapeMeasurePickerMotion.minValue, f.value)).rounded())
            ZStack(alignment: .topLeading) {
                Canvas { c, _ in
                    c.draw(art.background, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                    TapeMeasurePickerArt.drawTape(c, g, f.value, f.amp, f.phase)
                    c.draw(art.body, in: CGRect(x: 0, y: 0, width: g.w, height: g.h))
                    TapeMeasurePickerArt.drawHairline(c, g)
                } symbols: {
                    ForEach(TapeMeasurePickerArt.numbers, id: \.self) { n in
                        Text("\(n)")
                            .font(.system(size: n % 10 == 0 ? 19 : 17, weight: n % 10 == 0 ? .heavy : .bold))
                            .foregroundColor(n % 10 == 0 ? Color(hex: 0xC8261C) : Color(hex: 0x1D1A14))
                            .tag(n)
                    }
                }
                .frame(width: size.width, height: size.height)
                .allowsHitTesting(false)

                nav
                    .frame(width: size.width - 36, height: 44)
                    .offset(x: 16, y: top + 6)

                VStack(alignment: .leading, spacing: 6) {
                    Text("STEP 3 OF 6 · BODY")
                        .font(.system(size: 11, weight: .bold))
                        .kerning(1.6)
                        .foregroundStyle(Color(hex: 0xB07D06))
                    Text("How tall are you?")
                        .font(.system(size: 30, weight: .bold))
                        .kerning(-0.5)
                        .foregroundStyle(Self.ink)
                    Text("Pull the tape out to your height. It tunes your stride and calorie burn.")
                        .font(.system(size: 15))
                        .lineSpacing(3)
                        .foregroundStyle(Self.inkSoft)
                        .fixedSize(horizontal: false, vertical: true)
                }
                .frame(width: size.width - 48, alignment: .leading)
                .offset(x: 24, y: top + 62)
                .allowsHitTesting(false)

                readout(value: f.value, cm: cm)
                    .frame(width: size.width - 48, alignment: .leading)
                    .offset(x: 24, y: max(top + 174, g.caseY - Self.digitLine - 34))
                    .allowsHitTesting(false)

                Color.clear
                    .frame(width: size.width - g.caseX, height: 160)
                    .contentShape(Rectangle())
                    .offset(x: g.caseX, y: g.tapeY - 80)
                    .gesture(drag(reduced: reduced))
                    .accessibilityElement()
                    .accessibilityAddTraits(.allowsDirectInteraction)
                    .accessibilityLabel("Tape measure. Drag sideways to set your height.")
                    .accessibilityValue("\(cm) centimetres")

                Text("Drag the tape · flick left to reel it in")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Self.inkSoft)
                    .multilineTextAlignment(.center)
                    .frame(width: size.width - 48)
                    .offset(x: 24, y: g.tapeY + g.tapeH / 2 + 30)
                    .allowsHitTesting(false)

                stats(cm: cm)
                    .frame(width: size.width - 40)
                    .offset(x: 20, y: g.tapeY + g.tapeH / 2 + 62)
                    .allowsHitTesting(false)

                Button {} label: {
                    Text("Continue")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color(hex: 0xF7F3EA))
                        .frame(maxWidth: .infinity)
                        .frame(height: 56)
                        .background(
                            Capsule()
                                .fill(Color(hex: 0x22201B))
                                .overlay(
                                    Capsule().strokeBorder(
                                        LinearGradient(colors: [.white.opacity(0.12), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.1)),
                                        lineWidth: 1
                                    )
                                )
                                .shadow(color: Color(.sRGB, red: 20 / 255, green: 14 / 255, blue: 6 / 255, opacity: 0.3), radius: 1, y: 1)
                                .shadow(color: Color(.sRGB, red: 20 / 255, green: 14 / 255, blue: 6 / 255, opacity: 0.18), radius: 12, y: 12)
                        )
                        .contentShape(Capsule())
                }
                .buttonStyle(TapeMeasurePickerPressStyle())
                .frame(width: size.width - 40)
                .offset(x: 20, y: size.height - max(insets.bottom, 16) - 8 - 56)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .onChange(of: f.idle) { _, idle in
                if idle { active = false }
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    private var nav: some View {
        HStack(spacing: 16) {
            Button {} label: {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Self.ink)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle()
                            .fill(Color(hex: 0xFFFDF8, opacity: 0.8))
                            .shadow(color: Color(.sRGB, red: 60 / 255, green: 44 / 255, blue: 20 / 255, opacity: 0.12), radius: 1, y: 1)
                            .shadow(color: Color(.sRGB, red: 60 / 255, green: 44 / 255, blue: 20 / 255, opacity: 0.08), radius: 8, y: 6)
                    )
                    .contentShape(Circle().inset(by: -8))
            }
            .buttonStyle(TapeMeasurePickerPressStyle())
            .accessibilityLabel("Back")
            HStack(spacing: 4) {
                ForEach(0..<6, id: \.self) { i in
                    Capsule()
                        .fill(i < 3 ? Self.ink : Self.ink.opacity(0.1))
                        .frame(height: 4)
                        .frame(maxWidth: .infinity)
                }
            }
            Text("Skip")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(Self.inkSoft)
        }
    }

    private func readout(value: Double, cm: Int) -> some View {
        HStack(alignment: .bottom, spacing: 10) {
            HStack(spacing: 0) {
                wheel(value, place: 2)
                wheel(value, place: 1)
                wheel(value, place: 0)
            }
            .frame(height: Self.digitLine)
            .overlay(alignment: .top) {
                LinearGradient(colors: [Self.background, Self.background.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(height: 16)
            }
            .overlay(alignment: .bottom) {
                LinearGradient(colors: [Self.background.opacity(0), Self.background], startPoint: .top, endPoint: .bottom)
                    .frame(height: 16)
            }
            VStack(alignment: .leading, spacing: 2) {
                Text("cm")
                    .font(.system(size: 22, weight: .semibold))
                    .foregroundStyle(Self.ink)
                Text(TapeMeasurePickerMotion.feetInches(cm))
                    .font(.system(size: 14, weight: .medium).monospacedDigit())
                    .foregroundStyle(Self.inkSoft)
                    .fixedSize()
            }
            .padding(.bottom, 16)
        }
        .accessibilityElement()
        .accessibilityLabel("Height")
        .accessibilityValue("\(cm) centimetres")
    }

    /// One wheel of the counter: 0–9 and a second 0, rolled by the value (odometer carry).
    private func wheel(_ value: Double, place: Int) -> some View {
        let unit = pow(10, Double(place))
        let q = max(0, value) / unit
        let base = floor(q).truncatingRemainder(dividingBy: 10)
        let frac = q - floor(q)
        let roll = place == 0 ? frac : min(max(frac * unit - (unit - 1), 0), 1)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(.system(size: Self.digitSize, weight: .bold).monospacedDigit())
                    .kerning(-2)
                    .foregroundStyle(Self.ink)
                    .frame(width: Self.digitW, height: Self.digitLine)
            }
        }
        .offset(y: -(base + roll) * Self.digitLine)
        .frame(width: Self.digitW, height: Self.digitLine, alignment: .top)
        .clipped()
    }

    private func stats(cm: Int) -> some View {
        let m = Double(cm) / 100
        let bmi = Self.weightKg / (m * m)
        let items = [
            ("Weight", "\(Int(Self.weightKg)) kg"),
            ("Stride", "\(Int((Double(cm) * 0.415).rounded())) cm"),
            ("BMI", String(format: "%.1f", bmi)),
        ]
        let shape = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return HStack(spacing: 0) {
            ForEach(items.indices, id: \.self) { i in
                VStack(spacing: 4) {
                    Text(items[i].0)
                        .font(.system(size: 12, weight: .medium))
                        .foregroundStyle(Self.inkSoft)
                    Text(items[i].1)
                        .font(.system(size: 17, weight: .semibold).monospacedDigit())
                        .foregroundStyle(Self.ink)
                }
                .frame(maxWidth: .infinity)
                .overlay(alignment: .leading) {
                    if i > 0 {
                        Rectangle().fill(Self.ink.opacity(0.14)).frame(width: 0.5)
                    }
                }
            }
        }
        .padding(.vertical, 14)
        .background(
            shape.fill(Color(hex: 0xFFFDF8, opacity: 0.72))
                .overlay(
                    shape.strokeBorder(
                        LinearGradient(colors: [.white.opacity(0.8), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.08)),
                        lineWidth: 1
                    )
                )
                .shadow(color: Color(.sRGB, red: 60 / 255, green: 44 / 255, blue: 20 / 255, opacity: 0.1), radius: 1, y: 1)
                .shadow(color: Color(.sRGB, red: 60 / 255, green: 44 / 255, blue: 20 / 255, opacity: 0.08), radius: 14, y: 10)
        )
    }

    private func drag(reduced: Bool) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                active = true
                engine.touch(x: value.location.x)
            }
            .onEnded { _ in
                engine.release(reduced: reduced)
            }
    }
}

private struct TapeMeasurePickerPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .opacity(configuration.isPressed ? 0.9 : 1)
    }
}

/// What one frame shows.
struct TapeMeasurePickerFrame {
    var value: Double
    var amp: Double
    var phase: Double
    /// The tape is still and no finger is on it (lets the timeline pause outside autoPlay).
    var idle: Bool
}

/// The tape's value, its wobble, the autoPlay clock and the touch, stepped once a frame.
@MainActor
final class TapeMeasurePickerEngine {
    private typealias M = TapeMeasurePickerMotion

    private static let flick = -90.0
    private static let returnAfter = 2.0
    private static let returnFor = 0.9

    private var images: TapeMeasurePickerArt.Images?
    private var imagesKey: (TapeMeasurePickerGeo, Double)?

    private var last: Double?
    private var loopStart: Double?
    private var mode: Int?
    private var value = M.rest
    private var speed = 0.0
    private var amp = 0.0
    /// Spring after release: target, ω, ζ, settled.
    private var spring = (M.rest, 9.0, 0.72, true)
    /// Drag: start x, start raw value, last x, last time, velocity.
    private var drag: (Double, Double, Double, Double, Double)?
    private var touching = false
    private var lastTouch = 0.0
    private var back = (0.0, M.rest)
    /// Last whole cm and the time of the last tick, for the detent haptics.
    private var detent = (Int(M.rest), 0.0)
    private var lastFrame: TapeMeasurePickerFrame?

    private static let tick = UISelectionFeedbackGenerator()
    private static let snap = UIImpactFeedbackGenerator(style: .light)

    private static func clock() -> Double {
        Date().timeIntervalSinceReferenceDate
    }

    func art(for g: TapeMeasurePickerGeo, scale: Double) -> TapeMeasurePickerArt.Images {
        if let images, let key = imagesKey, key.0 == g, key.1 == scale { return images }
        let made = TapeMeasurePickerArt.render(g, scale: scale)
        images = made
        imagesKey = (g, scale)
        return made
    }

    func step(to date: Date, auto: Bool, reduced: Bool) -> TapeMeasurePickerFrame {
        let t0 = date.timeIntervalSinceReferenceDate
        if let last, t0 <= last, let lastFrame { return lastFrame }
        let dt = min(last.map { t0 - $0 } ?? 1 / 60, 1 / 30)
        last = t0
        if loopStart == nil { loopStart = t0 }
        if mode == nil { mode = auto ? 0 : 2 }
        let start = loopStart ?? t0
        let tau = Double.pi * 2
        var phase = ((t0 - start) * M.waveHz * tau).truncatingRemainder(dividingBy: tau * M.waveHz * M.loop)

        if mode == 0 {
            let t = (t0 - start).truncatingRemainder(dividingBy: M.loop)
            let lv = M.loopValue(t)
            value = lv.0
            speed = lv.1
            amp = M.loopWobble(t)
            phase = (t * M.waveHz).truncatingRemainder(dividingBy: 1) * tau
            let frame = TapeMeasurePickerFrame(value: value, amp: amp, phase: phase, idle: false)
            lastFrame = frame
            return frame
        }
        if mode == 1 {
            // A finger that holds still stops the wobble.
            speed *= exp(-dt / 0.05)
        } else if mode == 2 {
            if !spring.3 {
                let sub = 4
                let h = dt / Double(sub)
                for _ in 0..<sub {
                    speed += (-spring.1 * spring.1 * (value - spring.0) - 2 * spring.2 * spring.1 * speed) * h
                    value += speed * h
                }
                if abs(value - spring.0) < 0.02 && abs(speed) < 0.3 {
                    value = spring.0
                    speed = 0
                    spring.3 = true
                    Self.snap.impactOccurred()
                }
            } else if auto && t0 - lastTouch > Self.returnAfter {
                mode = 3
                back = (t0, value)
            }
        } else if mode == 3 {
            let k = M.easeInOut(M.seg(t0 - back.0, 0, Self.returnFor))
            value = back.1 + (M.rest - back.1) * k
            speed = 0
            if k >= 1 {
                mode = 0
                loopStart = t0
            }
        }
        amp = reduced ? 0 : max(amp * exp(-dt / 0.3), M.wobble(speed: abs(speed)))
        // Detents: a selection tick per whole centimetre under the finger, at most ~12 a second.
        if mode == 1 || mode == 2 {
            let cm = Int(value.rounded())
            if cm != detent.0 && t0 - detent.1 > 0.08 {
                detent = (cm, t0)
                Self.tick.selectionChanged()
            }
        }
        let idle = !auto && mode == 2 && spring.3 && !touching && amp < 0.01
        let frame = TapeMeasurePickerFrame(value: value, amp: amp, phase: phase, idle: idle)
        lastFrame = frame
        return frame
    }

    func touch(x: Double) {
        let t = Self.clock()
        if !touching {
            touching = true
            mode = 1
            drag = (x, M.unband(value), x, t, 0)
            detent = (Int(value.rounded()), t)
            return
        }
        guard let d = drag else { return }
        let raw = d.1 + (x - d.0) / M.ppcm
        let dtt = max(1.0 / 240, t - d.3)
        let vel = 0.6 * d.4 + 0.4 * ((x - d.2) / M.ppcm) / dtt
        drag = (d.0, d.1, x, t, vel)
        let v = M.band(raw)
        speed = (v - value) / dtt
        value = v
    }

    func release(reduced: Bool) {
        touching = false
        guard let d = drag else { return }
        drag = nil
        let vel = d.4
        lastTouch = Self.clock()
        // A fast flick to the left lets the spring reel the tape all the way in.
        let whip = vel < Self.flick && !reduced
        let target = whip ? M.minValue : min(M.maxValue, max(M.minValue, (value + (reduced ? 0 : vel * 0.12)).rounded()))
        speed = reduced ? 0 : vel
        spring = whip ? (target, 11, 0.6, false) : (target, reduced ? 14 : 9, reduced ? 1 : 0.72, false)
        mode = 2
    }
}
