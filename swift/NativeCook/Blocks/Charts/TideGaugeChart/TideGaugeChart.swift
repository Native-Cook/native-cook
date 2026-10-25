import SwiftUI
import UIKit

/*
 Tide Gauge Chart — "Harbor Point · Tides": a 24-hour tide chart whose area is real water (one
 Metal shader over the card). The loop and its phases are in TideGaugeChartMotion.swift
 (loop = 9 s).

 Touch: the scrubber follows the finger along the day (a fast critically damped follow, ω 28),
 the buoy rides the surface, and the speed of the scrub drives the slosh oscillator (ω 9 rad/s,
 damping ratio 0.3): the surface leans against the move and rings when the finger stops. Past
 either end of the day the scrubber rubber-bands and springs back on release. A selection tick
 when the scrubber passes a high or low tide. 2 s after the last touch the scrubber eases back to
 now in 0.8 s and the loop restarts. Reduced motion: no loop, no ripples, no slosh; the finger
 still scrubs.
*/

struct TideGaugeChart: View {
    /// Plays the 9 s scrub demo: back to the morning, across the high tide, and home to now.
    var autoPlay = false

    static let loop = TideGaugeChartMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = TideGaugeChartEngine()
    /// True while a touch or its return still moves things under reduced motion.
    @State private var live = false

    typealias M = TideGaugeChartMotion
    typealias A = TideGaugeChartArt
    private static let pad = 20.0
    private static let cardRadius = 24.0

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let l = TideGaugeChartLayout(size: size, insets: insets)
        let scale = min(3, displayScale)
        let backdrop = A.backdrop(width: l.cardW, height: l.cardH, plot: l.plot, scale: scale)
        let buoy = A.buoy(scale: scale)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        engine.plot = l.plot
        engine.onRest = { live = false }

        return ZStack(alignment: .topLeading) {
            Rectangle()
                .colorEffect(ShaderLibrary.tideGaugeChartBackdrop(.float2(max(1, size.width), max(1, size.height))))
                .frame(width: size.width, height: size.height)

            card(l, backdrop)
                .offset(x: l.cardX, y: l.cardY)

            TimelineView(.animation(paused: reduced && !live)) { timeline in
                let s = engine.step(to: timeline.date, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    water(s, l, buoy, reduced)
                        .offset(x: l.cardX, y: l.cardY)
                    reading(s, l, reduced)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }

            header(l)
            tiles(l)

            Color.clear
                .contentShape(Rectangle())
                .frame(width: l.cardW, height: l.cardH)
                .offset(x: l.cardX, y: l.cardY)
                .gesture(
                    DragGesture(minimumDistance: 0)
                        .onChanged { v in
                            live = true
                            engine.touch(x: v.location.x)
                        }
                        .onEnded { _ in engine.release() }
                )
                .accessibilityElement()
                .accessibilityAddTraits(.allowsDirectInteraction)
                .accessibilityLabel("Tide chart for today. Drag to scrub through the day.")

            Text("Predicted levels above chart datum · Not for navigation")
                .font(.system(size: 11, weight: .medium))
                .foregroundStyle(Color.rgba(200, 222, 230, 0.4))
                .multilineTextAlignment(.center)
                .frame(width: size.width - Self.pad * 2)
                .frame(width: size.width, height: size.height - insets.bottom - 14, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(hex: 0x081822))
        .clipped()
    }

    // MARK: Static

    /// Ambient and contact shadows, then the card with a lit top edge and its printed chart.
    private func card(_ l: TideGaugeChartLayout, _ backdrop: Image) -> some View {
        let r = Self.cardRadius
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: r)
                .fill(Color.rgba(1, 8, 14, 0.55))
                .frame(width: l.cardW - 8, height: l.cardH - 10)
                .blur(radius: 22)
                .offset(x: 4, y: 18)
            RoundedRectangle(cornerRadius: r)
                .fill(Color.rgba(1, 8, 14, 0.5))
                .frame(width: l.cardW - 2, height: l.cardH)
                .blur(radius: 3)
                .offset(x: 1, y: 2)
            RoundedRectangle(cornerRadius: r)
                .fill(LinearGradient(
                    stops: [
                        .init(color: Color(hex: 0x123447), location: 0),
                        .init(color: Color(hex: 0x0C2636), location: 0.55),
                        .init(color: Color(hex: 0x091C2A), location: 1),
                    ],
                    startPoint: .zero, endPoint: UnitPoint(x: 0.3, y: 1)
                ))
                .frame(width: l.cardW, height: l.cardH)
            RoundedRectangle(cornerRadius: r - 0.5)
                .stroke(LinearGradient(
                    stops: [
                        .init(color: Color.rgba(190, 230, 245, 0.16), location: 0),
                        .init(color: Color.rgba(190, 230, 245, 0.04), location: 0.3),
                        .init(color: Color.rgba(0, 0, 0, 0.25), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                ), lineWidth: 1)
                .frame(width: l.cardW - 1, height: l.cardH - 1)
                .offset(x: 0.5, y: 0.5)
            backdrop
                .resizable()
                .frame(width: l.cardW, height: l.cardH)
        }
        .frame(width: l.cardW, height: l.cardH, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func header(_ l: TideGaugeChartLayout) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text("TIDES · STATION 4127")
                .font(.system(size: 11, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(Color(hex: 0x7FC3D6))
            Text("Harbor Point")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.4)
                .foregroundStyle(A.ink)
            Text("Sat 25 Oct · Spring tide · Swell 1.2 m")
                .font(.system(size: 15, weight: .medium))
                .foregroundStyle(A.inkSoft)
        }
        .frame(width: l.size.width - Self.pad * 2, alignment: .leading)
        .offset(x: Self.pad, y: l.top)
        .allowsHitTesting(false)
    }

    private func tiles(_ l: TideGaugeChartLayout) -> some View {
        VStack(spacing: 12) {
            HStack(spacing: 12) {
                tile("HIGH TIDE", "2.4 m at 14:12", "Next 02:26 · 2.3 m")
                tile("LOW TIDE", "0.3 m at 08:05", "Next 20:19 · 0.4 m")
            }
            HStack(spacing: 10) {
                Circle().fill(Color(hex: 0x7FE0B4)).frame(width: 8, height: 8)
                Text("Surf window 12:30 – 15:40")
                    .font(.system(size: 15, weight: .medium).monospacedDigit())
                    .foregroundStyle(A.ink)
                    .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
                Text("Good")
                    .font(.system(size: 13, weight: .bold))
                    .foregroundStyle(Color(hex: 0x7FE0B4))
            }
            .padding(.horizontal, 16)
            .frame(height: 48)
            .background(
                Capsule()
                    .fill(Color.rgba(18, 48, 66, 0.5))
                    .overlay(Capsule().strokeBorder(Color.rgba(190, 230, 245, 0.12), lineWidth: 1 / max(1, displayScale)))
            )
        }
        .frame(width: l.size.width - (Self.pad - 4) * 2)
        .offset(x: Self.pad - 4, y: l.tilesY)
        .allowsHitTesting(false)
    }

    private func tile(_ label: String, _ value: String, _ sub: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(.system(size: 11, weight: .bold))
                .kerning(1.2)
                .foregroundStyle(Color(hex: 0x7FC3D6))
            Text(value)
                .font(.system(size: 17, weight: .semibold).monospacedDigit())
                .foregroundStyle(A.ink)
                .lineLimit(1)
                .minimumScaleFactor(0.8)
            Text(sub)
                .font(.system(size: 12, weight: .medium).monospacedDigit())
                .foregroundStyle(A.inkSoft)
                .lineLimit(1)
        }
        .padding(14)
        .frame(minWidth: 0, maxWidth: .infinity, alignment: .topLeading)
        .frame(height: 84, alignment: .topLeading)
        .background(
            RoundedRectangle(cornerRadius: 18, style: .continuous)
                .fill(Color.rgba(18, 48, 66, 0.72))
                .overlay(RoundedRectangle(cornerRadius: 18, style: .continuous).strokeBorder(Color.rgba(190, 230, 245, 0.14), lineWidth: 1 / max(1, displayScale)))
                .shadow(color: Color.rgba(0, 8, 14, 0.25), radius: 12, y: 10)
                .shadow(color: Color.rgba(0, 8, 14, 0.35), radius: 1, y: 1)
        )
    }

    // MARK: Per frame

    /// The water, the scrubber and the buoy, in card coordinates.
    private func water(_ s: TideGaugeChartEngine.State, _ l: TideGaugeChartLayout, _ buoy: (buoy: A.Piece, lamp: A.Piece), _ reduced: Bool) -> some View {
        let plot = l.plot
        let x = M.hourToX(s.hour, plot)
        let surf = M.surfaceY(x, plot, phase: s.phase, scrubX: x, tilt: s.tilt, chop: s.chop)
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: Self.cardRadius)
                .colorEffect(ShaderLibrary.tideGaugeChartWater(
                    .float4(plot.x, plot.y, plot.w, plot.h),
                    .float4(x, s.tilt, s.chop, s.glow),
                    .float(s.phase)
                ))
                .frame(width: l.cardW, height: l.cardH)
            Canvas { ctx, _ in
                var line = Path()
                line.move(to: CGPoint(x: x, y: plot.y - 4))
                line.addLine(to: CGPoint(x: x, y: surf - 30))
                ctx.stroke(line, with: .color(Color.rgba(230, 240, 243, 0.55)), style: StrokeStyle(lineWidth: 1, dash: [3, 3]))
                ctx.fill(Path(ellipseIn: CGRect(x: x - 3.5, y: plot.y - 4 - 3.5, width: 7, height: 7)), with: .color(A.ink))

                let left = M.surfaceY(x - 9, plot, phase: s.phase, scrubX: x, tilt: s.tilt, chop: s.chop)
                let right = M.surfaceY(x + 9, plot, phase: s.phase, scrubX: x, tilt: s.tilt, chop: s.chop)
                let bob = reduced ? 0 : 1.4 * sin(2 * Double.pi * 3 * s.phase)
                let lean = atan2(right - left, 18) * 0.8 + s.tilt * 0.012
                var c = ctx
                c.translateBy(x: x, y: surf + 2 + bob)
                c.rotate(by: .radians(lean))
                let lamp = 0.35 + 0.25 * pow(sin(2 * Double.pi * 2 * s.phase), 2) + s.glow * 0.4
                var g = c
                g.opacity = min(1, lamp)
                g.draw(buoy.lamp.image, in: buoy.lamp.rect)
                c.draw(buoy.buoy.image, in: buoy.buoy.rect)
            }
            .frame(width: l.cardW, height: l.cardH)
        }
        .frame(width: l.cardW, height: l.cardH, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func reading(_ s: TideGaugeChartEngine.State, _ l: TideGaugeChartLayout, _ reduced: Bool) -> some View {
        let h = M.clamp(s.hour, 0, M.day)
        let level = M.tide(h)
        let count = Int((max(0, level) * 100).rounded())
        let status = "\(M.tideTrend(h) > 0 ? "Rising" : "Falling") · \(M.clockText(h))"
        return HStack(alignment: .bottom) {
            HStack(alignment: .bottom, spacing: 0) {
                TideGaugeChartDigit(count: count / 100, reduced: reduced)
                Text(".")
                    .font(TideGaugeChartDigit.font)
                    .kerning(-1)
                    .foregroundStyle(A.ink)
                    .frame(height: TideGaugeChartDigit.height)
                TideGaugeChartDigit(count: count / 10, reduced: reduced)
                TideGaugeChartDigit(count: count, reduced: reduced)
                Text("m")
                    .font(.system(size: 20, weight: .semibold))
                    .foregroundStyle(A.inkSoft)
                    .padding(.leading, 4)
                    .padding(.bottom, 10)
            }
            .background(
                RoundedRectangle(cornerRadius: 30)
                    .fill(Color.rgba(143, 214, 232, 0.10))
                    .shadow(color: Color.rgba(143, 214, 232, 0.55), radius: 14)
                    .padding(.horizontal, -6)
                    .padding(.top, 10)
                    .padding(.bottom, 6)
                    .opacity(s.glow)
            )
            Spacer(minLength: 8)
            VStack(alignment: .trailing, spacing: 2) {
                Text(status)
                    .font(.system(size: 15, weight: .semibold).monospacedDigit())
                    .foregroundStyle(A.ink)
                Text("Water 17 °C · Wind 9 kn SW")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(A.inkSoft)
            }
            .padding(.bottom, 8)
        }
        .frame(width: l.size.width - Self.pad * 2)
        .offset(x: Self.pad, y: l.top + 96)
        .allowsHitTesting(false)
    }
}

/// One odometer column: 0–9 and a second 0 so the wrap from 9 rolls on. The wheel springs to the
/// column's whole count (critically damped, 0.26 s), so it only shows a partial digit while the
/// value changes and always comes to rest square on a digit.
private struct TideGaugeChartDigit: View {
    let count: Int
    let reduced: Bool

    static let height = 60.0
    static let font = Font.system(size: 52, weight: .semibold).monospacedDigit()

    var body: some View {
        VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in
                Text("\(i % 10)")
                    .font(Self.font)
                    .kerning(-1)
                    .foregroundStyle(TideGaugeChartArt.ink)
                    .frame(height: Self.height)
            }
        }
        .modifier(TideGaugeChartRoll(wheel: Double(count)))
        .animation(reduced ? nil : .spring(duration: 0.26, bounce: 0), value: count)
        .frame(height: Self.height, alignment: .top)
        .clipped()
    }
}

private struct TideGaugeChartRoll: ViewModifier, Animatable {
    var wheel: Double

    var animatableData: Double {
        get { wheel }
        set { wheel = newValue }
    }

    func body(content: Content) -> some View {
        let pos = (wheel.truncatingRemainder(dividingBy: 10) + 10).truncatingRemainder(dividingBy: 10)
        return content.offset(y: -pos * TideGaugeChartDigit.height)
    }
}

struct TideGaugeChartLayout {
    let size: CGSize
    let top: Double
    let cardX: Double
    let cardY: Double
    let cardW: Double
    let cardH: Double
    let plot: TideGaugeChartMotion.Plot
    let tilesY: Double

    init(size: CGSize, insets: EdgeInsets) {
        self.size = size
        top = insets.top + 16
        cardX = 20 - 4
        cardY = top + 164
        cardW = size.width - cardX * 2
        cardH = TideGaugeChartMotion.clamp(size.height - cardY - 214 - insets.bottom, 220, 340)
        plot = TideGaugeChartMotion.Plot(x: 14, y: 40, w: cardW - 28, h: cardH - 40 - 48)
        tilesY = cardY + cardH + 16
    }
}

/// The scrubber, the slosh and the loop, stepped once a frame like the React Native frame callback.
@MainActor
final class TideGaugeChartEngine {
    typealias M = TideGaugeChartMotion

    struct State {
        var hour = TideGaugeChartMotion.now
        var tilt = 0.0
        var chop = 0.0
        var glow = 0.0
        var phase = 0.0
    }

    var plot = TideGaugeChartMotion.Plot()
    var onRest: () -> Void = {}

    /// 0 loop, 1 finger down, 2 released (waiting), 3 easing back to rest.
    private var mode = 0
    private var s = State()
    private var fingerHour = TideGaugeChartMotion.now
    private var hourV = 0.0
    private var tiltV = 0.0
    private var idle = 0.0
    private var fromHour = TideGaugeChartMotion.now
    private var ret = 0.0
    private var loopStart: Date?
    private var last: Date?
    private var wasAuto: Bool?
    private var auto = false
    private let tick = UISelectionFeedbackGenerator()

    func step(to date: Date, auto: Bool, reduced: Bool) -> State {
        self.auto = auto
        if wasAuto != auto {
            wasAuto = auto
            loopStart = date
            if mode != 1 {
                mode = 0
                s.hour = M.now
            }
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)

        if mode == 0 {
            if !auto {
                s.hour = M.now
                s.tilt = 0
                s.chop = 0
                s.glow = 0
                if !reduced { s.phase = (s.phase + dt / M.loop).truncatingRemainder(dividingBy: 1) }
                return s
            }
            let t = max(0, date.timeIntervalSince(loopStart ?? date)).truncatingRemainder(dividingBy: M.loop)
            let sl = M.scriptSlosh(t, plotW: plot.w)
            s.hour = M.scriptHour(t)
            s.tilt = sl.tilt
            s.chop = sl.chop
            s.glow = M.scriptGlow(t)
            s.phase = t / M.loop
            return s
        }
        // Ripples keep travelling while the finger is in charge (not under reduced motion).
        if !reduced { s.phase = (s.phase + dt / M.loop).truncatingRemainder(dividingBy: 1) }
        let before = s.hour
        if mode == 3 {
            let r = min(1, ret + dt / M.returnFor)
            ret = r
            s.hour = fromHour + (M.now - fromHour) * M.easeInOut(r)
            if r >= 1 {
                mode = 0
                loopStart = date
                s.phase = 0
                if reduced { DispatchQueue.main.async { self.onRest() } }
            }
        } else {
            // The scrubber follows the finger (or, released, springs back inside the day).
            let target = mode == 1 ? fingerHour : M.clamp(s.hour, 0, M.day)
            let w = 28.0
            let a = -w * w * (s.hour - target) - 2 * w * hourV
            hourV += a * dt
            s.hour += hourV * dt
            if mode == 2 {
                idle += dt
                if idle > M.idleBeforeReturn {
                    mode = 3
                    ret = 0
                    fromHour = s.hour
                }
            }
        }
        let h = s.hour
        let speed = (h - before) / max(dt, 1e-3) * (plot.w / M.day)
        // Slosh: the oscillator, driven by the scrub speed (critically damped under reduced motion).
        let zeta = reduced ? 1 : M.sloshZeta
        let drive = reduced ? 0 : M.clamp(-speed * M.sloshGain, -M.sloshMax, M.sloshMax)
        let acc = -M.sloshOmega * M.sloshOmega * (s.tilt - drive) - 2 * zeta * M.sloshOmega * tiltV
        tiltV += acc * dt
        s.tilt += tiltV * dt
        // While easing back to rest the slosh is let go, so the loop takes over from a calm surface.
        if mode == 3 { s.tilt *= 1 - min(1, ret) * 0.25 }
        s.chop = reduced ? 0 : s.chop + (M.clamp(abs(speed) / 700, 0, 1) - s.chop) * min(1, dt * 6)
        s.glow *= exp(-dt * 4)
        // A tick when the scrubber passes a high or low tide (touch only; the loop is silent).
        if mode != 3 && mode != 0 {
            for e in M.extremes where (before - e) * (h - e) < 0 {
                tick.selectionChanged()
            }
        }
        return s
    }

    private func hourAt(_ x: Double) -> Double {
        let raw = (x - plot.x) / plot.w * M.day
        // Rubber band past either end of the day.
        if raw < 0 { return raw / (1 - raw / 3) }
        if raw > M.day { return M.day + (raw - M.day) / (1 + (raw - M.day) / 3) }
        return raw
    }

    func touch(x: Double) {
        if mode != 1 {
            if mode == 0 {
                hourV = 0
                tiltV = 0
            }
            mode = 1
            tick.prepare()
        }
        fingerHour = hourAt(x)
    }

    func release() {
        mode = 2
        idle = 0
    }
}
