import SwiftUI
import UIKit

/*
 Velvet Curtain Sheet — "Odeon Lane", tonight's screening of "The Glass Orchard". The seat picker
 is a sheet whose cover is a theatre curtain: two red velvet halves that part from the middle,
 bunching their folds toward the sides, with a gold fringe at the hem (one Metal shader). The loop
 is in VelvetCurtainSheetMotion.swift (loop = 9 s).

 Touch: drag the curtain sideways to open or close it (the finger's side of the centre sets the
 direction; rubber-banded past both ends; released, it springs to the nearer end with the finger's
 velocity, ω 10, ζ 0.85); the hem trails the rail on its own spring (ω 9, ζ 0.35), so the cloth
 swings. Drag the sheet up or down between its half and full detents (ω 14, ζ 0.86, the target
 chosen from the projected position). Tap the closed curtain to open it, tap a seat to pick it
 (a 0.35 s pop). Light haptic at a detent or when the curtain lands; a selection tick per seat.
 2 s after the last touch the curtain closes, the sheet returns to its half detent (0.8 s,
 ease-in-out), the picks clear behind the curtain and the loop restarts. Reduced motion: no loop,
 no sway, critically damped springs.
*/

struct VelvetCurtainSheet: View {
    /// Plays the 9 s demo: open the curtain, pick C7 and C8, close it.
    var autoPlay = false

    static let loop = VelvetCurtainSheetMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = VelvetCurtainSheetEngine()
    /// True while a touch or its springs still move things outside autoPlay.
    @State private var live = false

    typealias M = VelvetCurtainSheetMotion
    typealias A = VelvetCurtainSheetArt
    private static let radius = 28.0

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
        let l = VelvetCurtainSheetLayout(size: size, insets: insets)
        let scale = min(3, displayScale)
        let poster = A.poster(width: size.width, height: l.halfTop + 60, scale: scale)
        let seats = A.seats(width: size.width, pitch: l.pitch, scale: scale)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        engine.layout = l
        engine.onRest = { live = false }

        return ZStack(alignment: .topLeading) {
            Rectangle()
                .colorEffect(ShaderLibrary.velvetCurtainSheetPage(.float2(max(1, size.width), max(1, size.height))))
                .frame(width: size.width, height: size.height)
            poster
                .resizable()
                .frame(width: size.width, height: l.halfTop + 60)

            TimelineView(.animation(paused: !(auto || live))) { timeline in
                let s = engine.step(to: timeline.date, auto: auto, reduced: reduced)
                ZStack(alignment: .topLeading) {
                    sheet(s, l, seats, insets)
                        .offset(y: s.top)
                    pageText(l)
                        .opacity(M.clamp((s.top - l.fullTop) / max(1, l.halfTop - l.fullTop), 0, 1))
                    bar(s, l, insets)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }

            Color.clear
                .contentShape(Rectangle())
                .frame(width: size.width, height: size.height - l.fullTop)
                .offset(y: l.fullTop)
                .gesture(
                    DragGesture(minimumDistance: 0, coordinateSpace: .named("velvetCurtainSheet"))
                        .onChanged { v in
                            if engine.dragChanged(v, reduced: reduced, auto: auto) { live = true }
                        }
                        .onEnded { v in engine.dragEnded(v, auto: auto) }
                )
                .accessibilityElement()
                .accessibilityLabel("Seat picker behind a curtain. Tap or drag the curtain sideways to open it, tap a seat to pick it.")
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(name: "velvetCurtainSheet")
        .background(A.page)
        .clipped()
    }

    // MARK: Per frame

    private func sheet(_ s: VelvetCurtainSheetEngine.State, _ l: VelvetCurtainSheetLayout, _ seats: (image: Image, height: Double), _ insets: EdgeInsets) -> some View {
        let w = l.size.width
        let hemY = l.size.height - s.top - insets.bottom * 0.5 - 4
        return ZStack(alignment: .topLeading) {
            // The sheet's shadow on the page above it.
            Rectangle()
                .fill(Color.rgba(0, 0, 0, 0.55))
                .frame(width: w - 16, height: 40)
                .blur(radius: 14)
                .offset(x: 8, y: -6)

            ZStack(alignment: .topLeading) {
                LinearGradient(
                    stops: [
                        .init(color: Color(hex: 0x22121A), location: 0),
                        .init(color: Color(hex: 0x170C12), location: 0.4),
                        .init(color: Color(hex: 0x10080C), location: 1),
                    ],
                    startPoint: .top, endPoint: .bottom
                )
                seats.image
                    .resizable()
                    .frame(width: w, height: seats.height)
                litSeats(s, l)
                Rectangle()
                    .colorEffect(ShaderLibrary.velvetCurtainSheetCurtain(
                        .float2(max(1, w), max(1, l.drawH)),
                        .float4(M.pelmet, hemY, s.o, s.hem)
                    ))
                    .frame(width: w, height: l.drawH)
                Capsule()
                    .fill(Color.rgba(255, 226, 170, 0.45))
                    .frame(width: 36, height: 4)
                    .offset(x: w / 2 - 18, y: 7)
            }
            .frame(width: w, height: l.drawH, alignment: .topLeading)
            .clipShape(RoundedRectangle(cornerRadius: Self.radius, style: .continuous))

            pill
                .scaleEffect(s.press)
                .opacity(1 - M.clamp(s.o * 3, 0, 1))
                .frame(width: w)
                .offset(y: l.pillY)
        }
        .frame(width: w, height: l.drawH, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func litSeats(_ s: VelvetCurtainSheetEngine.State, _ l: VelvetCurtainSheetLayout) -> some View {
        Canvas { ctx, _ in
            guard !s.lit.isEmpty else { return }
            var path = Path()
            for seat in s.lit {
                let (x, y) = M.seatAt(seat.seat, width: l.size.width, pitch: l.pitch)
                let k = (0.86 + 0.14 * M.easeOut(seat.light)) * seat.pop
                let w = M.seatW * k
                let h = M.seatH * k
                path.addRoundedRect(
                    in: CGRect(x: x + M.seatW / 2 - w / 2, y: y + M.seatH / 2 - h / 2, width: w, height: h),
                    cornerSize: CGSize(width: 5 * k, height: 5 * k)
                )
            }
            ctx.drawLayer { c in
                c.opacity = 0.7
                c.addFilter(.blur(radius: 7))
                c.fill(path, with: .color(A.gold))
            }
            ctx.fill(path, with: .color(A.gold))
        }
        .frame(width: l.size.width, height: l.drawH)
    }

    private var pill: some View {
        Text("Choose seats")
            .font(.system(size: 16, weight: .bold))
            .kerning(0.2)
            .foregroundStyle(Color(hex: 0x3A1A0C))
            .padding(.horizontal, 28)
            .frame(height: 48)
            .background(
                Capsule()
                    .fill(LinearGradient(
                        stops: [
                            .init(color: Color(hex: 0xFFE3A0), location: 0),
                            .init(color: Color(hex: 0xF2C060), location: 0.5),
                            .init(color: Color(hex: 0xC98E2E), location: 1),
                        ],
                        startPoint: .top, endPoint: .bottom
                    ))
                    .overlay(
                        Capsule().strokeBorder(
                            LinearGradient(colors: [Color.rgba(255, 250, 230, 0.6), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.12)),
                            lineWidth: 1
                        )
                    )
                    .shadow(color: Color.rgba(20, 4, 8, 0.55), radius: 14, y: 12)
                    .shadow(color: Color.rgba(40, 10, 10, 0.5), radius: 1, y: 1)
            )
    }

    private func bar(_ s: VelvetCurtainSheetEngine.State, _ l: VelvetCurtainSheetLayout, _ insets: EdgeInsets) -> some View {
        let names = s.lit.map { M.seatName($0.seat) }.sorted()
        let summary = names.isEmpty ? "Tap a seat to pick it" : "\(names.count) seat\(names.count > 1 ? "s" : "") · \(names.joined(separator: ", "))"
        let price = String(format: "$%.2f", Double(s.lit.count) * M.price)
        return HStack(spacing: 12) {
            VStack(alignment: .leading, spacing: 2) {
                Text(summary)
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(A.ink)
                    .lineLimit(1)
                Text("Tonight 19:40 · Screen 3")
                    .font(.system(size: 12, weight: .medium).monospacedDigit())
                    .foregroundStyle(A.inkSoft)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            Text(price)
                .font(.system(size: 17, weight: .bold).monospacedDigit())
                .foregroundStyle(A.ink)
                .frame(minWidth: 64, alignment: .trailing)
            Text("Continue")
                .font(.system(size: 15, weight: .bold))
                .foregroundStyle(Color(hex: 0x3A1A0C))
                .padding(.horizontal, 18)
                .frame(height: 44)
                .background(Capsule().fill(A.gold))
        }
        .padding(.top, 14)
        .padding(.horizontal, 20)
        .padding(.bottom, insets.bottom + 12)
        .frame(width: l.size.width)
        .background(Color.rgba(24, 12, 18, 0.96))
        .overlay(alignment: .top) {
            Rectangle().fill(Color.rgba(242, 192, 96, 0.25)).frame(height: 1 / max(1, displayScale))
        }
        .frame(width: l.size.width, height: l.size.height, alignment: .bottom)
        .offset(y: (1 - s.bar) * 150)
        .opacity(M.clamp(s.bar * 2, 0, 1))
        .allowsHitTesting(false)
    }

    // MARK: Static

    private func pageText(_ l: VelvetCurtainSheetLayout) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text("ODEON LANE · SCREEN 3")
                .font(.system(size: 12, weight: .bold))
                .kerning(1.6)
                .foregroundStyle(A.gold)
            Text("The Glass Orchard")
                .font(.system(size: 34, weight: .bold))
                .kerning(-0.6)
                .foregroundStyle(A.ink)
            Text("Tonight 19:40 · 2 h 06 min · 12A")
                .font(.system(size: 15, weight: .medium).monospacedDigit())
                .foregroundStyle(A.inkSoft)
            HStack(spacing: 8) {
                ForEach(["Drama", "Mystery", "Rated 4.6"], id: \.self) { chip in
                    Text(chip)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(A.ink)
                        .padding(.horizontal, 12)
                        .frame(height: 28)
                        .background(
                            Capsule()
                                .fill(Color.rgba(243, 233, 228, 0.08))
                                .overlay(Capsule().strokeBorder(Color.rgba(243, 233, 228, 0.16), lineWidth: 1 / max(1, displayScale)))
                        )
                }
            }
            .padding(.top, 6)
        }
        .frame(width: l.size.width - 40, height: l.halfTop - 28, alignment: .bottomLeading)
        .offset(x: 20)
        .allowsHitTesting(false)
    }
}

struct VelvetCurtainSheetLayout {
    var size: CGSize = .zero
    var insets = EdgeInsets()
    var halfTop = 0.0
    var fullTop = 0.0
    var drawH = 0.0
    var pitch = 24.0
    var pillY = 0.0

    init() {}

    init(size: CGSize, insets: EdgeInsets) {
        typealias M = VelvetCurtainSheetMotion
        self.size = size
        self.insets = insets
        halfTop = (size.height - M.clamp(size.height * 0.56, 380, 480)).rounded()
        fullTop = insets.top + 12
        drawH = size.height - fullTop + 60
        pitch = M.rowPitch(size.height - halfTop)
        pillY = (M.pelmet + (size.height - halfTop)) / 2 - 24
    }
}

/// The curtain, the sheet and the picks, stepped once a frame like the React Native frame callback.
@MainActor
final class VelvetCurtainSheetEngine {
    typealias M = VelvetCurtainSheetMotion

    struct State {
        var o = 0.0
        var hem = 0.0
        var top = 0.0
        var bar = 0.0
        var press = 1.0
        /// Seats lit this frame: the seat, its light 0..1 and its pop scale.
        var lit: [(seat: Int, light: Double, pop: Double)] = []
    }

    var layout = VelvetCurtainSheetLayout()
    var onRest: () -> Void = {}

    /// 0 loop, 1 finger down, 2 released, 3 returning.
    private var mode = 0
    /// 1 sheet (vertical), 2 curtain (horizontal).
    private var axis = 0
    private var o = 0.0, oV = 0.0, oGoal = 0.0
    private var hem = 0.0, hemV = 0.0
    private var top: Double?
    private var topV = 0.0
    private var topGoal = 0.0
    private var bar = 0.0
    private var press = 1.0
    /// Picked seats and when each was lit (on the `now` clock).
    private var picked: [(seat: Int, at: Double)] = []
    private var idle = 0.0, ret = 0.0, fromO = 0.0, fromTop = 0.0, start = 0.0, side = 1.0
    private var now = 0.0
    private var loopStart: Date?
    private var loopT = 0.0
    private var last: Date?
    private var wasAuto: Bool?
    private var reduced = false
    // The finger, between its landing and lifting.
    private var touching = false
    private var ignored = false
    private var started = false
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let tick = UISelectionFeedbackGenerator()

    func step(to date: Date, auto: Bool, reduced: Bool) -> State {
        self.reduced = reduced
        let half = layout.halfTop
        if wasAuto != auto {
            wasAuto = auto
            loopStart = date
            mode = 0
            top = half
        }
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 1.0 / 20)
        now += dt

        if mode == 0 {
            top = half
            if !auto {
                o = 0
                hem = 0
                bar = 0
                press = 1
                return state()
            }
            loopT = max(0, date.timeIntervalSince(loopStart ?? date)).truncatingRemainder(dividingBy: M.loop)
            o = M.railAt(loopT)
            hem = M.hemAt(loopT)
            bar = M.barAt(loopT)
            press = M.pressAt(loopT)
            return state()
        }
        var y = top ?? half
        let zeta = reduced ? 1 : 0.85
        if !(mode == 1 && axis == 2) && mode != 3 {
            let a = -100 * (o - oGoal) - 2 * zeta * 10 * oV
            oV += a * dt
            o += oV * dt
        }
        if !(mode == 1 && axis == 1) && mode != 3 {
            let a = -196 * (y - topGoal) - 2 * (reduced ? 1 : 0.86) * 14 * topV
            topV += a * dt
            y += topV * dt
        }
        if mode == 3 {
            let r = min(1, ret + dt / M.returnFor)
            ret = r
            let e = M.easeInOut(r)
            o = fromO * (1 - e)
            y = fromTop + (half - fromTop) * e
            if r >= 1 {
                picked = []
                mode = 0
                loopStart = date
                loopT = 0
                o = 0
                hem = 0
                top = half
                if !auto { DispatchQueue.main.async { self.onRest() } }
                return state()
            }
        }
        top = y
        // The hem trails the rail.
        if reduced {
            hem = o
        } else {
            let a = -M.swayOmega * M.swayOmega * (hem - o) - 2 * M.swayZeta * M.swayOmega * hemV
            hemV += a * dt
            hem += hemV * dt
            if mode == 3 { hem += (o - hem) * M.easeInOut(ret) }
        }
        bar += ((o > 0.6 && mode != 3 ? 1 : 0) - bar) * min(1, dt * 9)
        press = 1
        if mode == 2 {
            idle += dt
            if idle > M.idleBeforeReturn {
                mode = 3
                ret = 0
                fromO = o
                fromTop = y
            }
        }
        return state()
    }

    private func state() -> State {
        var s = State(o: o, hem: hem, top: top ?? layout.halfTop, bar: bar, press: press)
        if mode == 0 {
            if wasAuto == true {
                for k in 0..<M.loopSeats.count {
                    let a = M.loopSeat(loopT, k)
                    if a > 0.01 { s.lit.append((M.loopSeats[k], a, reduced ? 1 : M.popScale(loopT - M.loopSeatAt[k]))) }
                }
            }
            return s
        }
        for p in picked {
            let e = now - p.at
            s.lit.append((p.seat, M.clamp(e / 0.15, 0, 1), reduced ? 1 : M.popScale(e)))
        }
        return s
    }

    /// Hands the screen from the loop to the finger, keeping what the loop shows.
    private func takeOver(auto: Bool) {
        if mode == 0 {
            oV = 0
            hemV = 0
            topV = 0
            topGoal = layout.halfTop
            oGoal = o > 0.5 ? 1 : 0
            picked = []
            if auto {
                for k in 0..<M.loopSeats.count where M.loopSeat(loopT, k) > 0.5 {
                    picked.append((M.loopSeats[k], now - 1))
                }
            }
        }
        idle = 0
    }

    private func rubber01(_ v: Double) -> Double {
        if v < 0 { return v / (1 - v * 3) * 0.3 }
        if v > 1 { return 1 + (v - 1) / (1 + (v - 1) * 3) * 0.3 }
        return v
    }

    /// Returns true when the touch is the sheet's (the timeline must run).
    func dragChanged(_ v: DragGesture.Value, reduced: Bool, auto: Bool) -> Bool {
        let l = layout
        if !touching {
            touching = true
            started = false
            axis = 0
            // Only touches that land on the sheet, and not on the booking bar while it shows.
            let sheetTop = top ?? l.halfTop
            let barTop = l.size.height - (14 + 44 + l.insets.bottom + 12)
            ignored = v.startLocation.y < sheetTop || (bar > 0.5 && v.startLocation.y > barTop)
        }
        if ignored { return false }
        let dx = v.translation.width
        let dy = v.translation.height
        if !started {
            // The sheet takes a vertical drag (unless it moved sideways first), the curtain a
            // horizontal one.
            if abs(dy) > 10 && abs(dx) <= 14 {
                started = true
                takeOver(auto: auto)
                mode = 1
                axis = 1
                start = top ?? l.halfTop
            } else if abs(dx) > 10 && abs(dy) <= 14 {
                started = true
                takeOver(auto: auto)
                mode = 1
                axis = 2
                start = o
                side = v.startLocation.x < l.size.width / 2 ? -1 : 1
            } else {
                return false
            }
            light.prepare()
        }
        if axis == 1 {
            var y = start + dy
            if y < l.fullTop { y = l.fullTop - (l.fullTop - y) / (1 + (l.fullTop - y) / 50) }
            if y > l.halfTop { y = l.halfTop + (y - l.halfTop) / (1 + (y - l.halfTop) / 70) }
            top = y
            topV = v.velocity.height
        } else if axis == 2 {
            let span = l.size.width * 0.42
            o = rubber01(start + side * dx / span)
            oV = side * v.velocity.width / span
        }
        return true
    }

    func dragEnded(_ v: DragGesture.Value, auto: Bool) {
        defer {
            touching = false
            started = false
        }
        if ignored { return }
        let l = layout
        if started {
            if axis == 1 {
                let y = top ?? l.halfTop
                let projected = y + v.velocity.height * 0.2
                let goal = abs(projected - l.fullTop) < abs(projected - l.halfTop) ? l.fullTop : l.halfTop
                if goal != topGoal { light.impactOccurred() }
                topGoal = goal
            } else if axis == 2 {
                oGoal = o + oV * 0.2 > 0.5 ? 1 : 0
                light.impactOccurred()
            }
            mode = 2
            axis = 0
            idle = 0
            return
        }
        // A tap.
        guard hypot(v.translation.width, v.translation.height) <= 10 else { return }
        takeOver(auto: auto)
        mode = 2
        let sheetTop = top ?? l.halfTop
        let x = v.location.x
        let y = v.location.y - sheetTop
        if o < 0.5 {
            if y > M.pelmet {
                oGoal = 1
                light.impactOccurred()
            }
            return
        }
        let i = M.seatIndexAt(x, y, width: l.size.width, pitch: l.pitch)
        if i < 0 || M.isTaken(i) { return }
        if let found = picked.firstIndex(where: { $0.seat == i }) {
            picked.remove(at: found)
        } else {
            picked.append((i, now))
        }
        tick.selectionChanged()
    }
}
