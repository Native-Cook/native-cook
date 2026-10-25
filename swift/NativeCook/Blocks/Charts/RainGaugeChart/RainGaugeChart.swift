import SwiftUI

/// "Plot 7", a garden app's week of rainfall as seven glass test-tube gauges in a wooden rack, one
/// per day (Mon 3.2 … Sun 6.6 mm, 38.6 mm in all; a tube holds 15 mm). Everything that moves is
/// Canvas drawing (RainGaugeChartArt.swift): glass with a bright stripe and ticks, water with a
/// meniscus that tilts as it sloshes, streaked rain with splashes and ripples. Text is views.
///
/// autoPlay (loop = 9 s, RainGaugeChartMotion.swift):
///   0.0 – 1.4   rest: every tube holds its day's rain.
///   1.4 – 2.4   the tubes drain (ease-in-out, 40 ms apart); the total rolls down.
///   2.4 – 6.0   rain sweeps across the rack from Monday to Sunday and each tube fills in turn.
///   6.0 – 7.4   Thursday lifts out of the rack with a slosh, its card rises, and it settles back.
///   7.4 – 9.0   rest.
/// Touch: tap a tube to lift it and see its day; tap again to put it back. Drag in the sky above
/// the rack to rain on the tubes under the finger. 2 s after the last touch everything eases back.
struct RainGaugeChart: View {
    var autoPlay = false

    static let loop = RainGaugeChartData.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = RainGaugeChartModel()
    @State private var heroFrame: CGRect = .zero
    @State private var callout = RainGaugeChartData.loopLift
    @State private var touchLive = false
    @State private var touchSerial = 0
    @State private var dragging = false
    @State private var dragStart: CGPoint = .zero
    @State private var touchMode = 0 // 1 = a tube under the finger, 2 = rain under the finger

    private typealias D = RainGaugeChartData
    static let ink = Color(red: 0xEA / 255, green: 0xF2 / 255, blue: 0xF0 / 255)
    static let muted = ink.opacity(0.58)
    static let accent = Color(red: 0x8F / 255, green: 0xD3 / 255, blue: 0xE8 / 255)
    static let background = Color(red: 0x1D / 255, green: 0x2D / 255, blue: 0x32 / 255)

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
        model.onCallout = { i in Task { @MainActor in callout = i } }
        let g = heroFrame.width > 0 ? RainGaugeChartGeo(heroFrame) : nil
        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(red: 0x26 / 255, green: 0x39 / 255, blue: 0x3F / 255), location: 0),
                    .init(color: Color(red: 0x1D / 255, green: 0x2D / 255, blue: 0x32 / 255), location: 0.5),
                    .init(color: Color(red: 0x15 / 255, green: 0x21 / 255, blue: 0x24 / 255), location: 1),
                ],
                startPoint: .top, endPoint: .bottom
            )
            if let g {
                scene(g: g, size: size)
            }

            VStack(spacing: 0) {
                header
                    .padding(.horizontal, 24)
                hero(g: g)
                    .frame(maxWidth: .infinity, minHeight: 360, maxHeight: .infinity)
                    .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("RainGaugeChart")) } action: { heroFrame = $0 }
                card
                    .padding(.top, 12)
                    .padding(.horizontal, 24)
            }
            .padding(.top, insets.top + 10)
            .padding(.bottom, insets.bottom + 12)
            .frame(width: size.width, height: size.height, alignment: .top)

            if let g {
                labels(g: g, size: size)
                    .allowsHitTesting(false)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(name: "RainGaugeChart")
        .background(Self.background)
        .clipped()
    }

    // MARK: Scene

    /// Backdrop and rack front are static Canvases (drawn once per layout); the tubes and the rain
    /// are redrawn every frame from the model's pose.
    private func scene(g: RainGaugeChartGeo, size: CGSize) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in RainGaugeChartArt.drawBackdrop(ctx, g, size: size) }
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                Canvas { ctx, _ in RainGaugeChartArt.drawRackTubes(ctx, g, p) }
            }
            Canvas { ctx, _ in RainGaugeChartArt.drawRackFront(ctx, g) }
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                Canvas { ctx, _ in RainGaugeChartArt.drawFront(ctx, g, p) }
            }
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    // MARK: Header

    private var header: some View {
        VStack(alignment: .leading, spacing: 6) {
            HStack {
                Text("PLOT 7 · ALDER LANE")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(2.2)
                    .foregroundStyle(Self.accent)
                    .lineLimit(1)
                Spacer(minLength: 8)
                Text("Week 43 · Oct 20 – 26")
                    .font(.system(size: 12, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(Self.muted)
                    .lineLimit(1)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Capsule().fill(Color(red: 226 / 255, green: 244 / 255, blue: 248 / 255).opacity(0.06)))
                    .overlay(Capsule().strokeBorder(Color(red: 226 / 255, green: 244 / 255, blue: 248 / 255).opacity(0.18), lineWidth: 1 / 3))
            }
            Text("Rainfall this week")
                .font(.system(size: 32, weight: .bold))
                .kerning(-0.6)
                .foregroundStyle(Self.ink)
                .padding(.top, 4)
            HStack(spacing: 10) {
                TimelineView(.animation(paused: paused)) { tl in
                    let p = model.pose(at: tl.date)
                    RainGaugeChartOdometer(value: (p.total * 10 * 1000).rounded() / 1000, digits: 3, suffix: " mm", height: 26)
                }
                Text("+12% on last week")
                    .font(.system(size: 14))
                    .foregroundStyle(Self.muted)
            }
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Hero (touch only; the drawing is in the scene)

    private func hero(g: RainGaugeChartGeo?) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { v in
                        guard let g else { return }
                        let x = v.location.x + g.ox
                        if !dragging {
                            beginTouch()
                            model.takeOver()
                            dragStart = v.location
                            if v.location.y + g.oy > g.board - 24 || reduceMotion {
                                touchMode = 1
                            } else {
                                touchMode = 2
                                model.startRain(cursor: cursorAt(x, g))
                            }
                        } else if touchMode == 2 {
                            model.moveRain(cursor: cursorAt(x, g))
                        }
                    }
                    .onEnded { v in
                        defer { endTouch() }
                        guard let g else { return }
                        if touchMode == 1 && hypot(v.location.x - dragStart.x, v.location.y - dragStart.y) < 14 {
                            let x = v.location.x + g.ox
                            if let i = (0..<D.tubes).first(where: { abs(x - g.cx[$0]) < g.pitch / 2 }) {
                                model.tapTube(i)
                            }
                        }
                        if touchMode == 2 { model.stopRain() }
                        touchMode = 0
                        model.released()
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("Seven rain gauges, Monday to Sunday. Tap a gauge for its day, drag above the rack to make it rain.")
    }

    private func cursorAt(_ x: Double, _ g: RainGaugeChartGeo) -> Double {
        max(-0.5, min(Double(D.tubes) - 0.5, (x - g.cx[0]) / g.pitch))
    }

    // MARK: Labels over the scene

    private func labels(g: RainGaugeChartGeo, size: CGSize) -> some View {
        let cardW = 176.0
        let cardTop = g.y0 - RainGaugeChartGeo.liftPt - (g.y1 - g.y0) * RainGaugeChartGeo.liftScale - 84
        let cardLeft = max(16, min(size.width - cardW - 16, g.cx[callout] - cardW / 2))
        return ZStack(alignment: .topLeading) {
            Text("mm")
                .font(.system(size: 10, weight: .semibold))
                .foregroundStyle(Self.muted)
                .offset(x: g.ox + 10, y: g.levelY(D.capacity) - 30)
            ForEach([15, 10, 5, 0], id: \.self) { mm in
                Text(String(mm))
                    .font(.system(size: 10, weight: .semibold))
                    .monospacedDigit()
                    .foregroundStyle(Self.muted)
                    .frame(width: 22, alignment: .trailing)
                    .offset(x: g.ox + 6, y: g.levelY(Double(mm)) - 8)
            }
            TimelineView(.animation(paused: paused)) { tl in
                let p = model.pose(at: tl.date)
                ZStack(alignment: .topLeading) {
                    ForEach(0..<D.tubes, id: \.self) { i in
                        dayLabel(i, lit: p.liftIdx == i ? p.lift : 0)
                            .frame(width: g.pitch)
                            .offset(x: g.cx[i] - g.pitch / 2, y: g.ground + 6)
                    }
                    calloutCard
                        .frame(width: cardW)
                        .scaleEffect(0.96 + 0.04 * p.lift)
                        .offset(x: cardLeft, y: cardTop + 10 * (1 - p.lift))
                        .opacity(p.lift)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func dayLabel(_ i: Int, lit: Double) -> some View {
        func content(_ name: Color, _ value: Color) -> some View {
            VStack(spacing: 1) {
                Text(D.days[i])
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(name)
                Text(String(format: "%.1f", D.mm[i]))
                    .font(.system(size: 11, weight: .medium))
                    .monospacedDigit()
                    .foregroundStyle(value)
            }
            .frame(maxWidth: .infinity)
        }
        return content(Self.muted, Self.ink.opacity(0.42 / 1))
            .overlay(content(Self.accent, Self.ink).opacity(lit))
    }

    private var calloutCard: some View {
        let shape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return VStack(alignment: .leading, spacing: 2) {
            Text(D.fullDays[callout])
                .font(.system(size: 12, weight: .bold))
                .kerning(0.4)
                .foregroundStyle(Self.accent)
            Text(String(format: "%.1f mm", D.mm[callout]))
                .font(.system(size: 22, weight: .bold))
                .monospacedDigit()
                .foregroundStyle(Self.ink)
            Text(D.notes[callout])
                .font(.system(size: 12))
                .foregroundStyle(Self.muted)
                .lineLimit(1)
        }
        .padding(.vertical, 10)
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(
            ZStack {
                shape.fill(Color.black)
                    .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.3), radius: 15, y: 14)
                    .mask(Rectangle().padding(-60).overlay(shape.blendMode(.destinationOut)).compositingGroup())
                shape.fill(Color(red: 30 / 255, green: 50 / 255, blue: 56 / 255).opacity(0.92))
                shape.strokeBorder(Color(red: 170 / 255, green: 225 / 255, blue: 240 / 255).opacity(0.35), lineWidth: 1 / 3)
            }
        )
    }

    // MARK: Card

    private var card: some View {
        let line = Color(red: 226 / 255, green: 244 / 255, blue: 248 / 255).opacity(0.14)
        let shape = RoundedRectangle(cornerRadius: 20, style: .continuous)
        return HStack(alignment: .top, spacing: 0) {
            stat("Wettest", "Thu · 11.6 mm")
            Rectangle().fill(line).frame(width: 1 / 3).padding(.horizontal, 12)
            stat("Dry spells", "1 day, Tue")
            Rectangle().fill(line).frame(width: 1 / 3).padding(.horizontal, 12)
            stat("Watering", "Skip till Sat")
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.vertical, 14)
        .padding(.horizontal, 16)
        .background(
            ZStack {
                shape.fill(Color.black)
                    .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                    .shadow(color: .black.opacity(0.25), radius: 16, y: 12)
                    .mask(Rectangle().padding(-60).overlay(shape.blendMode(.destinationOut)).compositingGroup())
                shape.fill(Color(red: 226 / 255, green: 244 / 255, blue: 248 / 255).opacity(0.05))
                shape.strokeBorder(line, lineWidth: 1 / 3)
            }
        )
    }

    private func stat(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(label)
                .font(.system(size: 11, weight: .semibold))
                .kerning(0.4)
                .foregroundStyle(Self.muted)
            Text(value)
                .font(.system(size: 14, weight: .semibold))
                .monospacedDigit()
                .foregroundStyle(Self.ink)
                .fixedSize(horizontal: false, vertical: true)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    // MARK: Touch bookkeeping

    private func beginTouch() {
        dragging = true
        touchSerial += 1
        touchLive = true
    }

    /// Keeps the timeline running until the springs, the rain and the return to rest have finished.
    private func endTouch() {
        dragging = false
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

/// An odometer for a value with one decimal (e.g. 386 shows 38.6): `digits` columns in all, the
/// last after the point.
struct RainGaugeChartOdometer: View {
    let value: Double
    let digits: Int
    let suffix: String
    let height: Double

    private func glyph(_ s: String) -> some View {
        Text(s)
            .font(.system(size: 22, weight: .semibold))
            .monospacedDigit()
            .foregroundStyle(RainGaugeChart.ink)
            .frame(height: height)
    }

    var body: some View {
        HStack(spacing: 0) {
            ForEach((0..<digits).reversed(), id: \.self) { k in
                column(place: k)
                if k == 1 { glyph(".") }
            }
            glyph(suffix)
        }
    }

    private func column(place: Int) -> some View {
        let v = max(0, value)
        let scaled = v / pow(10, Double(place))
        let whole = floor(scaled).truncatingRemainder(dividingBy: 10)
        let frac = scaled - floor(scaled)
        let lower = place == 0 ? frac : max(0, frac * 10 - 9)
        return VStack(spacing: 0) {
            ForEach(0..<11, id: \.self) { i in glyph(String(i % 10)) }
        }
        .offset(y: -(whole + lower) * height)
        .frame(height: height, alignment: .top)
        .clipped()
    }
}
