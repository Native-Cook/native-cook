import SwiftUI
import UIKit

/*
 Sundial Time Picker — a calendar's time picker ("Meridian — Pick a time") that is a stone
 sundial seen from above. Drag the sun along the sky arc, or drag the shadow round the dial: the
 gnomon's shadow swings to that hour, shortens towards noon and stretches in the morning and late
 afternoon, the light (and the stone, and the backdrop) warms when the sun is low. While dragging,
 every quarter hour ticks (selection haptic) and the readout rolls; on release the time springs to
 the nearest quarter, carrying the drag's speed. Two seconds after the last touch it eases back to
 10:00 (0.9 s) and the loop starts again.

 One Metal pass (SundialTimePicker.metal) under the carved numerals, which are drawn once in a
 Canvas outside the timeline. The readout and the event card are views.
 Reduced motion: 10:00, still; dragging still works.
 */
struct SundialTimePicker: View {
    var autoPlay = false

    /// The loop's period (s); the manifest's recording loop.
    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = SundialTimePickerEngine()
    @State private var handActive = false
    @State private var handGeneration = 0
    @State private var touched = false

    private typealias M = SundialTimePickerMotion
    private typealias S = SundialTimePickerStyle
    private static let numerals = ["VII", "VIII", "IX", "X", "XI", "XII", "I", "II", "III", "IV", "V"]
    private static let quarters = ["00", "15", "30", "45"]

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
        let g = SundialTimePickerGeo(w: max(1, size.width), h: max(1, size.height), top: insets.top)
        let auto = autoPlay && !reduceMotion
        return ZStack(alignment: .topLeading) {
            S.background

            TimelineView(.animation(paused: !auto && !handActive)) { timeline in
                let s = engine.step(to: timeline.date, auto: auto, autoPlay: autoPlay)
                let sun = M.sunDir(s.hours)
                let (sx, sy) = M.sunOnArc(g, s.hours)
                ZStack(alignment: .topLeading) {
                    Rectangle()
                        .colorEffect(ShaderLibrary.sundialTimePicker(
                            .float2(g.w, g.h),
                            .float4(g.cx, g.cy, g.R, g.unit),
                            .float4(sun.0, sun.1, sun.2, s.hours),
                            .float4(sx, sy, g.gnomon, 0),
                            .float4(sx, sy, s.finger, 0)
                        ))
                        .frame(width: size.width, height: size.height)

                    readout(hours: s.hours, g: g)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)

            carved(g)
                .allowsHitTesting(false)

            header(insets: insets, width: size.width)
                .allowsHitTesting(false)
            card(insets: insets, width: size.width, height: size.height)
                .allowsHitTesting(false)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            if !touched {
                                touched = true
                                engine.grab(g: g, x: v.location.x, y: v.location.y)
                                handGeneration += 1
                                handActive = true
                            } else {
                                engine.drag(g: g, x: v.location.x, y: v.location.y)
                            }
                        }
                        .onEnded { _ in
                            touched = false
                            engine.release()
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
                .accessibilityLabel("Sundial time picker. Drag the sun or the shadow to choose a time.")
                .accessibilityAddTraits(.allowsDirectInteraction)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }

    /// The carved numerals and the motto: a dark cut with a light lower lip. Static.
    private func carved(_ g: SundialTimePickerGeo) -> SundialTimePickerCarved {
        SundialTimePickerCarved(g: g, numerals: Self.numerals)
    }

    private func header(insets: EdgeInsets, width: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("MERIDIAN · NEW EVENT")
                .font(.system(size: 11, weight: .bold))
                .kerning(2)
                .foregroundStyle(S.accent)
            Text("Pick a time")
                .font(.system(size: 34, weight: .heavy))
                .kerning(-0.6)
                .foregroundStyle(S.ink)
                .padding(.top, 8)
            Text("Tea with Ines · Thursday, 24 October")
                .font(.system(size: 15))
                .foregroundStyle(S.mute)
                .padding(.top, 4)
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 16)
        .frame(width: width, alignment: .leading)
    }

    private func readout(hours tau: Double, g: SundialTimePickerGeo) -> some View {
        let shown = M.quarterOf(tau)
        let hours = floor(shown + 1e-6)
        let quarter = Int(((shown - hours) * 4).rounded()) % 4
        let altitude = asin(max(0, M.sunDir(tau).2)) * 180 / .pi
        let endHours = floor(shown + 0.75 + 1e-6)
        let endQuarter = Int(((shown + 0.75 - endHours) * 4).rounded()) % 4
        return VStack(spacing: 2) {
            HStack(alignment: .center, spacing: 0) {
                SundialTimePickerRollNumber(value: hours, digits: 2, line: 60, style: S.time, pad: true)
                Text(":")
                    .font(S.time.font)
                    .kerning(S.time.kerning)
                    .foregroundStyle(S.time.color)
                    .frame(height: 60)
                    .padding(.horizontal, 2)
                    .offset(y: -4)
                SundialTimePickerRollLabel(labels: Self.quarters, index: quarter, line: 60, style: S.time)
            }
            HStack(alignment: .center, spacing: 0) {
                Text("SUN ").font(S.meta.font).kerning(S.meta.kerning).foregroundStyle(S.meta.color)
                SundialTimePickerRollNumber(value: altitude, digits: 2, line: 16, style: S.meta, suffix: "° HIGH  ·  UNTIL ")
                SundialTimePickerRollNumber(value: endHours, digits: 2, line: 16, style: S.meta, pad: true)
                Text(":").font(S.meta.font).kerning(S.meta.kerning).foregroundStyle(S.meta.color)
                SundialTimePickerRollLabel(labels: Self.quarters, index: endQuarter, line: 16, style: S.meta)
            }
            .fixedSize()
        }
        .frame(width: g.w)
        .offset(y: g.cy + g.R + 22 * g.unit)
    }

    private func card(insets: EdgeInsets, width: Double, height: Double) -> some View {
        let hair = 1 / displayScale
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return HStack(spacing: 12) {
            RoundedRectangle(cornerRadius: 3).fill(S.accent).frame(width: 6, height: 36)
            VStack(alignment: .leading, spacing: 2) {
                Text("Tea with Ines")
                    .font(.system(size: 16, weight: .bold))
                    .foregroundStyle(S.ink)
                Text("Garden room · 45 min · Reminder 10 min before")
                    .font(.system(size: 12))
                    .foregroundStyle(S.mute)
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Text("Confirm")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(S.buttonText)
                .padding(.horizontal, 18)
                .frame(height: 44)
                .background {
                    ZStack {
                        Capsule().fill(S.ink).shadow(color: .black.opacity(0.2), radius: 1, y: 1)
                        Capsule().strokeBorder(LinearGradient(colors: [.white.opacity(0.12), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.1)), lineWidth: 1)
                    }
                }
        }
        .padding(.leading, 16)
        .padding(.trailing, 12)
        .frame(width: width - 32, height: 72)
        .background {
            ZStack {
                shape.fill(S.card)
                    .shadow(color: Color.rgba(70, 50, 30, 0.12), radius: 15, y: 14)
                    .shadow(color: Color.rgba(70, 50, 30, 0.12), radius: 1.5, y: 1)
                shape.strokeBorder(LinearGradient(colors: [.white.opacity(0.9), .white.opacity(0)], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)), lineWidth: 1)
                shape.strokeBorder(S.ink.opacity(0.1), lineWidth: hair)
            }
        }
        .offset(x: 16, y: height - insets.bottom - 16 - 72)
    }
}

/// The numerals and the motto cut into the stone, drawn once (they never move).
struct SundialTimePickerCarved: View, Equatable {
    let g: SundialTimePickerGeo
    let numerals: [String]

    static func == (a: Self, b: Self) -> Bool { a.g.w == b.g.w && a.g.h == b.g.h && a.g.cy == b.g.cy }

    var body: some View {
        let g = self.g
        let big = Font.custom("Georgia-Bold", fixedSize: (15 * g.unit).rounded())
        let small = Font.custom("Georgia-Bold", fixedSize: (10 * g.unit).rounded())
        return Canvas { ctx, _ in
            func put(_ s: String, _ font: Font, _ x: Double, _ y: Double) {
                let dark = ctx.resolve(Text(s).font(font).foregroundStyle(Color.rgba(58, 48, 38, 0.78)))
                let lip = ctx.resolve(Text(s).font(font).foregroundStyle(Color.rgba(255, 252, 244, 0.55)))
                let box = dark.measure(in: CGSize(width: 1000, height: 1000))
                let base = dark.firstBaseline(in: box)
                // (x, y) is the centre of the baseline, as Skia's drawText puts it
                let anchor = UnitPoint(x: 0.5, y: base / max(box.height, 1))
                ctx.draw(lip, at: CGPoint(x: x + 0.6, y: y + 0.8), anchor: anchor)
                ctx.draw(dark, at: CGPoint(x: x, y: y), anchor: anchor)
            }
            let r = g.R * 0.87
            for (i, n) in numerals.enumerated() {
                let h = Double(i + 7 - 12) * 15 * .pi / 180
                let a = atan(SundialTimePickerMotion.sinLat * tan(h))
                put(n, big, g.cx + sin(a) * r, g.cy - cos(a) * r + 5 * g.unit)
            }
            put("MERIDIAN", small, g.cx, g.cy + g.R * 0.5)
            put("LAT 40 N  -  EST 1894", small, g.cx, g.cy + g.R * 0.62)
        }
        .frame(width: g.w, height: g.h)
    }
}

enum SundialTimePickerStyle {
    static let background = Color(hex: 0xEEE8DF)
    static let ink = Color(hex: 0x2C2722)
    static let mute = Color.rgba(44, 39, 34, 0.58)
    static let accent = Color(hex: 0xC77A35)
    static let card = Color(hex: 0xFBF8F3)
    static let buttonText = Color(hex: 0xFBF5EC)
    static let time = SundialTimePickerRollFont(size: 52, weight: .bold, color: ink, kerning: -1)
    static let meta = SundialTimePickerRollFont(size: 12, weight: .bold, color: mute, kerning: 1.2)
}

/// What one frame shows: the chosen time (hours) and the finger on the sun.
struct SundialTimePickerState {
    var hours: Double
    var finger: Double
}

/// The clock, the autoPlay script and the dragged time with its snap spring.
@MainActor
final class SundialTimePickerEngine {
    private typealias M = SundialTimePickerMotion

    static let idle = 2.0
    static let returnTime = 0.9
    /// The snap spring (response 0.45 s, damping ratio 0.7) in hours.
    private static let kSnap = 195.0
    private static let cSnap = 19.5

    private enum Mode { case auto, hand, back }

    private var mode = Mode.auto
    private var last: Date?
    private var now = 0.0
    private var autoStart = 0.0
    private var lastTouch = 0.0
    private var touching = false
    /// Time [hours, velocity h/s, asked-for hours].
    private var time: [Double] = [M.restTime, 0, M.restTime]
    private var onDial = false
    private var returnFrom = M.restTime
    private var returnStart = 0.0
    private var current = SundialTimePickerState(hours: M.restTime, finger: 0)
    private var lastShown = -1.0
    private let tick = UISelectionFeedbackGenerator()

    /// Whether nothing moves any more after a touch (the timeline may pause).
    func resting(autoPlay: Bool) -> Bool {
        if mode == .auto { return true }
        return mode == .hand && !autoPlay && !touching && abs(time[1]) < 0.001 && abs(time[0] - time[2]) < 0.0005
    }

    func step(to date: Date, auto: Bool, autoPlay: Bool) -> SundialTimePickerState {
        let raw = last.map { date.timeIntervalSince($0) } ?? 0
        last = date
        let adv = raw > 0.25 || raw < 0 ? 0 : raw
        now += adv
        let dt = min(0.033, adv)

        if mode == .hand {
            var tm = time
            if touching {
                let next = tm[0] + (tm[2] - tm[0]) * min(1, dt * 30)
                tm[1] = dt > 0 ? tm[1] * 0.6 + 0.4 * (next - tm[0]) / dt : tm[1]
                tm[0] = next
            } else {
                tm[1] += (-Self.kSnap * (tm[0] - tm[2]) - Self.cSnap * tm[1]) * dt
                tm[0] += tm[1] * dt
                if now - lastTouch > Self.idle && abs(tm[1]) < 0.01 && autoPlay {
                    mode = .back
                    returnStart = now
                    returnFrom = tm[0]
                }
            }
            time = tm
        } else if mode == .back && now - returnStart >= Self.returnTime {
            mode = .auto
            autoStart = now
        }

        let s: SundialTimePickerState
        switch mode {
        case .hand:
            s = SundialTimePickerState(hours: time[0], finger: touching ? 1 : 0)
        case .back:
            let k = M.easeInOut(min(1, (now - returnStart) / Self.returnTime))
            s = SundialTimePickerState(hours: returnFrom + (M.restTime - returnFrom) * k, finger: 0)
        case .auto:
            if auto {
                let t = (now - autoStart).truncatingRemainder(dividingBy: SundialTimePicker.loop)
                s = SundialTimePickerState(hours: M.timeAt(t), finger: M.fingerAt(t))
            } else {
                s = SundialTimePickerState(hours: M.restTime, finger: 0)
            }
        }
        current = s

        let shown = mode == .hand && touching ? M.quarterOf(s.hours) : -1
        if shown >= 0 && lastShown >= 0 && shown != lastShown { tick.selectionChanged() }
        lastShown = shown
        return s
    }

    func grab(g: SundialTimePickerGeo, x: Double, y: Double) {
        onDial = hypot(x - g.cx, y - g.cy) < g.R
        mode = .hand
        touching = true
        lastTouch = now
        time = [current.hours, 0, M.rubberTime(M.timeFromFinger(g, x, y, onDial: onDial))]
    }

    func drag(g: SundialTimePickerGeo, x: Double, y: Double) {
        guard touching else { return }
        time[2] = M.rubberTime(M.timeFromFinger(g, x, y, onDial: onDial))
        lastTouch = now
    }

    func release() {
        if touching {
            // the release aims at the quarter the drag's momentum carries it to
            time[2] = M.quarterOf(time[0] + time[1] * 0.12)
        }
        touching = false
        lastTouch = now
    }
}
