import SwiftUI
import UIKit

/// A trip drawn one leg at a time; drag the line artwork to scrub its eight-second story.
@MainActor
struct TripLineLoader: View {
    static let loop: Double = 8.0
    private let autoPlay: Bool
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date.now
    @State private var mode = TripLineLoaderPlayback.playing
    @State private var dragging = false
    @State private var dragStart = 0.0
    @State private var previousScrub = 0.0
    @State private var lean = 0.0
    @State private var generation = 0
    @State private var lastHaptic = Date.distantPast
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @GestureState private var touching = false

    init(autoPlay: Bool = false) { self.autoPlay = autoPlay }

    private var paused: Bool {
        if reduceMotion || scenePhase != .active { return true }
        switch mode {
        case .playing: return !autoPlay
        case .held: return true
        case .returning: return false
        }
    }

    var body: some View {
        GeometryReader { geometry in
            let width = Double(geometry.size.width)
            let height = Double(geometry.size.height)
            ZStack(alignment: .topLeading) {
                TripLineLoaderPalette.bg.ignoresSafeArea()
                chrome(width: width, height: height)
                TimelineView(.animation(paused: paused)) { timeline in
                    let t = time(at: timeline.date)
                    let tick = autoplayTick(at: timeline.date)
                    ZStack(alignment: .topLeading) {
                        hero(t: t, width: width)
                            .offset(y: 150)
                        route(t: t, width: width)
                            .offset(y: 416)
                        ForEach(0..<3, id: \.self) { index in
                            row(index, t: t, width: width - 48)
                                .offset(x: 24, y: 476 + Double(index) * 78)
                        }
                    }
                    .frame(width: width, height: height, alignment: .topLeading)
                    // This integer changes only at the three ticks, never at the loop seam.
                    .onChange(of: tick) { before, after in
                        if let before, let after, after > before { impact() }
                    }
                }
            }
            .frame(width: width, height: height, alignment: .topLeading)
        }
        .preferredColorScheme(.light)
        .onAppear { restart(); haptic.prepare() }
        .onDisappear { generation += 1 }
        .onChange(of: reduceMotion) { restart() }
        .onChange(of: autoPlay) { restart() }
        .onChange(of: scenePhase) { restart() }
        .onChange(of: touching) { _, down in if !down { finishScrub() } }
        .task(id: generation) { await returnAfterIdle(generation) }
    }

    // MARK: Clock and touch (state changes only at interaction / mode boundaries)

    private func time(at date: Date) -> Double {
        guard !reduceMotion else { return 0 }
        switch mode {
        case .playing:
            return autoPlay ? TripLineLoaderMotion.wrap(max(0, date.timeIntervalSince(origin))) : 0
        case .held(let t): return TripLineLoaderMotion.wrap(t)
        case .returning(let from, let to, let start, let duration):
            let e = TripLineLoaderMotion.inOut(TripLineLoaderMotion.seg(date.timeIntervalSince(start), 0, duration))
            return TripLineLoaderMotion.wrap(from + (to - from) * e)
        }
    }

    private func autoplayTick(at date: Date) -> Int? {
        guard autoPlay, !reduceMotion, scenePhase == .active, case .playing = mode else { return nil }
        return TripLineLoaderMotion.tickBucket(max(0, date.timeIntervalSince(origin)))
    }

    private var scrub: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($touching) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduceMotion else { return }
                if !dragging {
                    let frozen = time(at: value.time)
                    generation += 1
                    dragging = true
                    dragStart = frozen
                    previousScrub = frozen
                    mode = .held(frozen)
                }
                let next = dragStart + Double(value.translation.width) * Self.loop / 320
                if TripLineLoaderMotion.tickBucket(next) != TripLineLoaderMotion.tickBucket(previousScrub) {
                    impact()
                }
                previousScrub = next
                mode = .held(next)
                lean = min(1, max(-1, Double(value.velocity.width) / 900)) * 6
            }
            .onEnded { _ in finishScrub() }
    }

    private func finishScrub() {
        // GestureState also releases a cancelled drag, so its idle timer cannot get stranded.
        guard dragging else { return }
        dragging = false
        withAnimation(.spring(response: 0.4, dampingFraction: 0.7)) { lean = 0 }
        generation += 1
    }

    private func returnAfterIdle(_ token: Int) async {
        guard !dragging, !reduceMotion, case .held = mode else { return }
        do { try await Task.sleep(for: .seconds(2)) } catch { return }
        guard generation == token, !dragging, !reduceMotion, case .held(let held) = mode else { return }
        let from = TripLineLoaderMotion.wrap(held)
        let to = from < 4 ? 0.0 : Self.loop
        let duration = min(0.9, 0.35 + 0.08 * abs(to - from))
        mode = .returning(from: from, to: to, start: .now, duration: duration)
        do { try await Task.sleep(for: .seconds(duration)) } catch { return }
        guard generation == token, !dragging, !reduceMotion else { return }
        origin = .now
        mode = .playing
    }

    private func restart() {
        generation += 1
        dragging = false
        lean = 0
        origin = .now
        mode = .playing
    }

    private func impact() {
        let now = Date.now
        guard !reduceMotion, now.timeIntervalSince(lastHaptic) >= 0.1 else { return }
        lastHaptic = now
        haptic.impactOccurred()
        haptic.prepare()
    }

    // MARK: Product screen

    private func chrome(width: Double, height: Double) -> some View {
        ZStack(alignment: .topLeading) {
            HStack {
                Text("Farewind").font(.system(size: 17, weight: .semibold)).tracking(0.2)
                    .foregroundStyle(TripLineLoaderPalette.coral)
                Spacer()
                Text("Cancel")
                    .font(.system(size: 15)).foregroundStyle(TripLineLoaderPalette.muted)
            }
            .frame(width: width - 48, height: 44).offset(x: 24, y: 8)
            Text("Building your trip")
                .font(.system(size: 30, weight: .bold)).tracking(-0.4)
                .foregroundStyle(TripLineLoaderPalette.ink)
                .frame(width: width - 48, height: 36).offset(x: 24, y: 68)
            Text("Lisbon to Kyoto · 12–18 Mar · 2 guests")
                .font(.system(size: 15)).foregroundStyle(TripLineLoaderPalette.muted)
                .frame(width: width - 48, height: 20).offset(x: 24, y: 112)
            Text("Matching fares, rides and rooms")
                .font(.system(size: 13)).foregroundStyle(TripLineLoaderPalette.muted)
                .frame(width: width)
                // SF's baseline sits one descender above the bottom of its line box.
                .offset(y: height - 24 - Double(UIFont.systemFont(ofSize: 13).ascender))
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private func hero(t: Double, width: Double) -> some View {
        ZStack {
            Canvas { context, _ in TripLineLoaderDrawing.shadow(context, t: t, width: width, reduced: reduceMotion) }
            Canvas { context, _ in TripLineLoaderDrawing.icons(context, t: t, width: width, reduced: reduceMotion) }
                .rotationEffect(.degrees(lean), anchor: UnitPoint(x: 0.5, y: 112.0 / 250))
        }
        .frame(width: width, height: 250)
        .contentShape(Rectangle())
        .gesture(scrub, including: reduceMotion ? .none : .all)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Trip loading animation. Drag sideways to scrub.")
    }

    private func route(t: Double, width: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                let path = TripLineLoaderArtwork.line(68, 8, width - 68, 8)
                context.stroke(path, with: .color(TripLineLoaderPalette.track), style: StrokeStyle(lineWidth: 2, dash: [4, 6]))
                let fill = TripLineLoaderMotion.route(t, reduced: reduceMotion)
                if fill.to > fill.from {
                    context.stroke(path.trimmedPath(from: fill.from, to: fill.to), with: .color(TripLineLoaderPalette.coral),
                                   style: StrokeStyle(lineWidth: 2.5, lineCap: .round))
                }
                let done = TripLineLoaderMotion.row(2, t, reduced: reduceMotion).done
                let pulse = reduceMotion ? 0 : sin(.pi * TripLineLoaderMotion.seg(t, 6.22, 6.52)) * 0.4
                for x in [68.0, width - 68] {
                    let r = 3.5 * (x == 68 ? 1 : 1 + pulse)
                    let dot = Path(ellipseIn: CGRect(x: x - r, y: 8 - r, width: r * 2, height: r * 2))
                    context.fill(dot, with: .color(TripLineLoaderPalette.track))
                    context.fill(dot, with: .color(TripLineLoaderPalette.coral.opacity(x == 68 ? 1 : done)))
                }
            }
            HStack(alignment: .top) {
                city("LIS", name: "Lisbon", alignment: .leading)
                Spacer()
                city("KIX", name: "Kyoto", alignment: .trailing)
            }.padding(.horizontal, 24)
        }
        .frame(width: width, height: 40, alignment: .topLeading)
        .accessibilityElement(children: .combine)
    }

    private func city(_ code: String, name: String, alignment: HorizontalAlignment) -> some View {
        VStack(alignment: alignment, spacing: 2) {
            Text(code).font(.system(size: 13, weight: .semibold)).tracking(0.6)
                .foregroundStyle(TripLineLoaderPalette.ink).frame(height: 16)
            Text(name).font(.system(size: 12)).foregroundStyle(TripLineLoaderPalette.muted)
        }
    }

    private func row(_ index: Int, t: Double, width: Double) -> some View {
        let state = TripLineLoaderMotion.row(index, t, reduced: reduceMotion)
        let copy = TripLineLoaderArtwork.rows[index]
        return ZStack(alignment: .topLeading) {
            RoundedRectangle(cornerRadius: 16).fill(TripLineLoaderPalette.card)
            RoundedRectangle(cornerRadius: 16).fill(TripLineLoaderPalette.coralSoft.opacity(state.active))
            RoundedRectangle(cornerRadius: 16).strokeBorder(TripLineLoaderPalette.cardBorder, lineWidth: 1)
            RoundedRectangle(cornerRadius: 16).strokeBorder(TripLineLoaderPalette.coral.opacity(0.35 * state.active), lineWidth: 1)
            Canvas { context, _ in
                TripLineLoaderDrawing.status(context, state: state, t: t, reduced: reduceMotion)
            }.frame(width: 24, height: 24).offset(x: 16, y: 22)
            VStack(alignment: .leading, spacing: 4) {
                Text(copy.title).font(.system(size: 16, weight: .semibold)).foregroundStyle(TripLineLoaderPalette.ink)
                Text(copy.detail).font(.system(size: 13)).foregroundStyle(TripLineLoaderPalette.muted)
                    .lineLimit(1).truncationMode(.tail)
            }
            .frame(width: width - 56 - 16 - 82 - 8, alignment: .leading).offset(x: 56, y: 14)
            ZStack(alignment: .trailing) {
                Text("Waiting").font(.system(size: 13)).foregroundStyle(TripLineLoaderPalette.muted).opacity(state.waiting)
                Text("Searching…").font(.system(size: 13, weight: .medium))
                    .foregroundStyle(TripLineLoaderPalette.coral).opacity(state.searching)
                Text(copy.price).font(.system(size: 15, weight: .semibold)).monospacedDigit()
                    .foregroundStyle(TripLineLoaderPalette.ink).opacity(state.done)
            }
            .frame(width: 82, height: 68, alignment: .trailing).offset(x: width - 98)
        }
        .frame(width: width, height: 68, alignment: .topLeading)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(copy.title), \(copy.detail)")
        .accessibilityValue(state.done > 0.5 ? copy.price : (state.active > 0.5 ? "Searching" : "Waiting"))
    }
}

private enum TripLineLoaderPlayback {
    case playing, held(Double)
    case returning(from: Double, to: Double, start: Date, duration: Double)
}

private enum TripLineLoaderPalette {
    static let bg = color(0xFFFBF8), card = color(0xFFFFFF), cardBorder = color(0xF1E6E1)
    static let coral = color(0xE64C3B), coralSoft = color(0xFCE9E6), ink = color(0x2A1F1D)
    static let muted = color(0x8D7F7A), track = color(0xE9DDD8)
    private static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: 1)
    }
}

private enum TripLineLoaderMotion {
    static let ticks = [1.62, 3.92, 6.22]
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func out(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func into(_ x: Double) -> Double { x * x * x }
    static func inOut(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func back(_ x: Double) -> Double { 1 + 2.70158 * pow(x - 1, 3) + 1.70158 * pow(x - 1, 2) }
    static func softBack(_ x: Double) -> Double {
        let e = back(x)
        return e <= 1 ? e : 1 + (e - 1) * 0.4
    }
    static func mix(_ a: Double, _ b: Double, _ x: Double) -> Double { a + (b - a) * x }
    static func wrap(_ t: Double) -> Double { let r = t.truncatingRemainder(dividingBy: 8); return r < 0 ? r + 8 : r }
    static func tickBucket(_ t: Double) -> Int { ticks.reduce(0) { $0 + Int(floor((t - $1) / 8)) } }

    struct Row {
        var active: Double, done: Double, waiting: Double, searching: Double, pop: Double, check: Double
    }

    static func row(_ i: Int, _ t: Double, reduced: Bool) -> Row {
        if reduced { return Row(active: 0, done: 1, waiting: 0, searching: 0, pop: 1, check: 1) }
        let tick = ticks[i]
        let reset = 6.90 + Double(2 - i) * 0.06
        let fade = inOut(seg(t, reset, reset + 0.2))
        let done = out(seg(t, tick, tick + 0.15)) * (1 - fade)
        let arrival = i == 0 ? 1.0 : out(seg(t, ticks[i - 1], ticks[i - 1] + 0.2))
        let departure = out(seg(t, tick, tick + 0.15))
        let active = arrival * (1 - departure) + (i == 0 ? inOut(seg(t, 7.10, 7.30)) : 0)
        let pop = t < tick ? 0.6 : 0.6 + 0.4 * back(seg(t, tick, tick + 0.3))
        return Row(active: active, done: done, waiting: max(0, 1 - active - done), searching: active,
                   pop: pop, check: out(seg(t, tick + 0.08, tick + 0.30)))
    }

    static func route(_ t: Double, reduced: Bool) -> (from: Double, to: Double) {
        if reduced { return (0, 1) }
        if t >= 7.35 { return (0, 0) }
        let to = 0.6 * inOut(seg(t, 1.62, 2.12)) + 0.2 * inOut(seg(t, 3.92, 4.42)) + 0.2 * inOut(seg(t, 6.22, 6.72))
        return (inOut(seg(t, 6.85, 7.35)), to)
    }
}

private enum TripLineLoaderDrawing {
    private typealias M = TripLineLoaderMotion
    private typealias A = TripLineLoaderArtwork
    private typealias P = TripLineLoaderPalette

    static func status(_ context: GraphicsContext, state: TripLineLoaderMotion.Row, t: Double, reduced: Bool) {
        let ring = Path(ellipseIn: CGRect(x: 1, y: 1, width: 22, height: 22))
        context.stroke(ring, with: .color(P.track), lineWidth: 2)
        if state.active > 0 {
            var arc = Path()
            let angle = 360 * t - 90
            arc.addArc(center: CGPoint(x: 12, y: 12), radius: 11,
                       startAngle: .degrees(angle), endAngle: .degrees(angle + 90), clockwise: false)
            context.stroke(arc, with: .color(P.coral.opacity(state.active)), style: StrokeStyle(lineWidth: 2, lineCap: .round))
        }
        var done = context
        done.opacity = state.done
        done.translateBy(x: 12, y: 12)
        done.scaleBy(x: state.pop, y: state.pop)
        done.translateBy(x: -12, y: -12)
        done.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 24, height: 24)), with: .color(P.coral))
        if state.check > 0 {
            done.stroke(A.check.trimmedPath(from: 0, to: state.check), with: .color(.white),
                        style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        }
    }

    static func shadow(_ context: GraphicsContext, t: Double, width: Double, reduced: Bool) {
        var factor = 1.0
        var opacity = 1.0
        if !reduced {
            // Each silhouette leaves a narrower contact shadow as it gathers into the pill.
            let phases: [(Double, Double, Double, Double, Double)] = [
                (1.40, 1.80, 1.90, 2.26, 0.9), (3.70, 4.10, 4.20, 4.56, 0.7), (6.00, 6.40, 6.50, 6.86, 1.0)
            ]
            for (start, end, draw, live, next) in phases {
                if t < start { break }
                let gather = M.into(M.seg(t, start, end))
                let appear = M.out(M.seg(t, draw, live))
                factor = M.mix(M.mix(factor, 0.45, gather), next, appear)
                opacity = M.mix(1 - 0.5 * gather, 1, appear)
            }
            let planePresence = t < 1.8 ? 1 - M.into(M.seg(t, 1.4, 1.8)) : M.out(M.seg(t, 6.5, 6.86))
            factor *= 1 - 0.04 * sin(.pi * t) * planePresence
        }
        var shadow = context
        shadow.addFilter(.blur(radius: 4))
        shadow.fill(Path(ellipseIn: CGRect(x: width / 2 - 65 * factor, y: 209.5, width: 130 * factor, height: 9)),
                    with: .color(P.coral.opacity(0.12 * opacity)))
    }

    static func icons(_ context: GraphicsContext, t: Double, width: Double, reduced: Bool) {
        if reduced {
            icon(context, kind: .plane, t: 0, elapsed: 10, gather: 0, width: width, reduced: true)
            return
        }
        // Cruise / build, gather, and draw-on share the same paths for autoplay and scrubbing.
        if t < 1.8 || t >= 6.5 {
            let clock = t < 1.8 ? t + 8 : t
            icon(context, kind: .plane, t: t, elapsed: clock - 6.5,
                 gather: M.into(M.seg(clock, 9.4, 9.8)), width: width, reduced: false)
        }
        if t >= 1.9 && t < 4.1 {
            icon(context, kind: .taxi, t: t, elapsed: t - 1.9,
                 gather: M.into(M.seg(t, 3.7, 4.1)), width: width, reduced: false)
        }
        if t >= 4.2 && t < 6.4 {
            icon(context, kind: .hotel, t: t, elapsed: t - 4.2,
                 gather: M.into(M.seg(t, 6.0, 6.4)), width: width, reduced: false)
        }
        // The pill bridges the disappearing strokes and the next drawing; trim both ends.
        for (start, length) in [(1.56, 64.0), (3.86, 56.0), (6.16, 64.0)] {
            guard t >= start, t < start + 0.72 else { continue }
            let grown = length * M.out(M.seg(t, start, start + 0.24))
            let stretched = grown + 40 * M.out(M.seg(t, start + 0.24, start + 0.42))
            let trim = 0.5 * M.into(M.seg(t, start + 0.42, start + 0.72))
            let path = A.line(width / 2 - stretched * 0.6, 112, width / 2 + stretched * 0.6, 112)
            if stretched > 0 && trim < 0.5 {
                context.stroke(path.trimmedPath(from: trim, to: 1 - trim), with: .color(P.coral),
                               style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        }
    }

    private static func icon(_ context: GraphicsContext, kind: A.Icon, t: Double, elapsed: Double,
                             gather: Double, width: Double, reduced: Bool) {
        let plane = kind == .plane
        let hotel = kind == .hotel
        let build = plane && t < 1.8 && !reduced ? M.inOut(M.seg(t, 0.8, 1.3)) : 0
        let anticipation = plane && t < 1.8 && !reduced ? M.out(M.seg(t, 1.3, 1.4)) : 0
        let bob = reduced ? 0 : (plane ? 2 * sin(.pi * t) : (kind == .taxi ? 0.8 * abs(sin(4 * .pi * t)) : 0))
        let pitch = plane && !reduced ? 1.2 * sin(.pi * t + .pi / 2) : 0
        let appear = M.seg(elapsed, 0, 0.36)
        var c = context
        c.opacity = 1 - M.seg(gather, 0.7, 1)
        c.translateBy(x: width / 2, y: 112 + (plane ? bob : 0))
        c.rotate(by: .degrees(pitch))
        c.scaleBy(x: 1.4, y: 1.4)
        c.translateBy(x: -88 + 5 * build * (1 - gather), y: -60)
        c.translateBy(x: 88, y: 60)
        c.scaleBy(x: M.mix(1 - 0.03 * anticipation, 0.55, gather), y: M.mix(1 + 0.05 * anticipation, 0.06, gather))
        c.translateBy(x: -88, y: -60)
        let anchorY = hotel ? 120.0 : 60.0
        c.translateBy(x: 88, y: anchorY)
        c.scaleBy(x: hotel ? 1 : M.mix(1.15, 1, M.out(appear)),
                  y: M.mix(0.3, 1, hotel ? M.out(appear) : M.softBack(appear)))
        c.translateBy(x: -88, y: -anchorY)
        let strokes = kind == .plane ? A.plane : (kind == .taxi ? A.taxi : A.hotel)
        for stroke in strokes {
            var layer = c
            var path = stroke.path
            if kind == .taxi {
                // The car bounces above a steady road; its cloud only drifts sideways.
                if case .taxiCloud = stroke.part {} else { layer.translateBy(x: 0, y: bob / 1.4) }
            }
            switch stroke.part {
            case .planeCloud:
                let u = M.wrap(t + 1.5)
                layer.translateBy(x: reduced ? 0 : -8 * min(u, 3.3) / 3.3, y: 0)
            case .taxiCloud: layer.translateBy(x: -10 * M.seg(t, 2.55, 3.70), y: 0)
            case .hotelCloud: layer.translateBy(x: -8 * M.seg(t, 4.90, 6.00), y: 0)
            case .wheel(let x):
                // Opaque wheel centres hide the body edge before the circle and spoke draw on.
                let amount = M.out(M.seg(elapsed, stroke.delay, stroke.delay + 0.32))
                layer.fill(Path(ellipseIn: CGRect(x: x - 11, y: 75, width: 22, height: 22)), with: .color(P.bg.opacity(amount)))
            case .spoke(let x):
                layer.translateBy(x: x, y: 86)
                layer.rotate(by: .radians(4 * .pi * t))
                layer.translateBy(x: -x, y: -86)
            case .tree:
                layer.translateBy(x: 25, y: 116)
                layer.rotate(by: .degrees(3 * sin(2 * .pi * t)))
                layer.translateBy(x: -25, y: -116)
            case .window(let i):
                let light = M.out(M.seg(t, 4.95 + Double(i) * 0.1, 5.10 + Double(i) * 0.1))
                layer.fill(path, with: .color(P.coral.opacity(0.85 * light * (1 - gather))))
            case .plain: break
            }
            let duration = hotel ? 0.30 : 0.32
            let drawn = M.out(M.seg(elapsed, stroke.delay, stroke.delay + duration))
            let from = 0.5 * 0.85 * gather
            let to = min(drawn, 1 - from)
            if to > from {
                path = path.trimmedPath(from: from, to: to)
                layer.stroke(path, with: .color(P.coral), style: StrokeStyle(lineWidth: stroke.width, lineCap: .round, lineJoin: .round))
            }
        }
        // Dashes are individual paths, sharing their lane's draw slot and gather amount.
        if plane {
            let phase = reduced ? 0 : t.truncatingRemainder(dividingBy: 0.5) / 0.5
            for (y, end) in [(20.0, 44.0), (76.0, 30.0), (98.0, 48.0)] {
                dashes(c, y: y, start: 0, end: end, long: 18, gap: 8, phase: phase,
                       lengthScale: 1 + 0.5 * build, alpha: 0.55 + 0.45 * build,
                       drawn: M.out(M.seg(elapsed, 6 * 0.035, 6 * 0.035 + 0.32)), gather: gather)
            }
        } else if kind == .taxi {
            dashes(c, y: 102, start: 4, end: 172, long: 16, gap: 9,
                   phase: t.truncatingRemainder(dividingBy: 0.5) / 0.5, lengthScale: 1, alpha: 1,
                   drawn: M.out(M.seg(elapsed, 8 * 0.035, 8 * 0.035 + 0.32)), gather: gather)
        }
    }

    private static func dashes(_ context: GraphicsContext, y: Double, start: Double, end: Double,
                               long: Double, gap: Double, phase: Double, lengthScale: Double,
                               alpha: Double, drawn: Double, gather: Double) {
        // A full long/short pair repeats every 0.5 s; both lengths and both gaps stay intact.
        let period = (long + 5) * lengthScale + 2 * gap
        let offset = phase * period
        let from = 0.5 * 0.85 * gather
        let to = min(drawn, 1 - from)
        guard to > from else { return }
        var layer = context
        layer.clip(to: Path(CGRect(x: start, y: y - 3, width: end - start, height: 6)))
        for i in -1...6 {
            let x = start + Double(i) * period - offset
            for (a, length) in [(x, long * lengthScale), (x + long * lengthScale + gap, 5 * lengthScale)] {
                layer.stroke(A.line(a, y, a + length, y).trimmedPath(from: from, to: to), with: .color(P.coral.opacity(alpha)),
                             style: StrokeStyle(lineWidth: 2.2, lineCap: .round))
            }
        }
    }
}

private enum TripLineLoaderArtwork {
    enum Icon { case plane, taxi, hotel }
    enum Part { case plain, planeCloud, taxiCloud, hotelCloud, wheel(Double), spoke(Double), tree, window(Int) }
    struct Stroke {
        var path: Path
        var slot: Int
        var extra = 0.0
        var width = 2.2
        var part = Part.plain
        var delay: Double { Double(slot) * 0.035 + extra }
    }
    static let rows = [
        (title: "Flight", detail: "LIS → KIX · 1 stop · 16h 05m", price: "€612"),
        (title: "Transfer", detail: "Airport taxi to Gion · 48 min", price: "€38"),
        (title: "Stay", detail: "Kamo River Inn · 6 nights", price: "€486")
    ]
    static let check = polyline([(7, 12.5), (10.5, 16), (17, 9)])

    static let plane: [Stroke] = {
        var outline = rounded([(17.5, 33.2), (26.6, 32.6), (44.9, 44.8), (151.3, 44.8)], radius: 3)
        outline.addQuadCurve(to: CGPoint(x: 165.9, y: 56.9), control: CGPoint(x: 166, y: 45))
        let rest = [(165.9, 56.9), (157.4, 63.6), (114.8, 63.6), (73.8, 94.9), (55.5, 95.8), (78.9, 63.6), (35.2, 63.6), (17.5, 33.2)]
        appendRounded(rest, radius: 3, to: &outline)
        outline.closeSubpath()
        return [
            Stroke(path: outline, slot: 0),
            Stroke(path: rounded([(76.8, 44.8), (54, 20.4), (73.8, 18.9), (107.2, 44.8)], radius: 3), slot: 1),
            Stroke(path: line(29.7, 52.4, 44, 52.4), slot: 2), Stroke(path: line(50.5, 52.4, 50.6, 52.4), slot: 2),
            Stroke(path: line(128.5, 52.4, 146, 52.4), slot: 3), Stroke(path: line(152.3, 52.4, 156.9, 52.4), slot: 3),
            Stroke(path: cloud(152, 16, 22, 12), slot: 4, part: .planeCloud),
            Stroke(path: cloud(128, 104, 30, 15), slot: 5, part: .planeCloud)
        ]
    }()

    static let taxi: [Stroke] = {
        var strokes = [
            Stroke(path: rounded([(17.2, 87.4), (21.5, 65.7), (38.7, 55.8), (137.6, 55.8), (155.6, 70), (161.2, 87.4)], radius: 3, closed: true), slot: 0),
            Stroke(path: rounded([(36.5, 55.8), (51.6, 33.4), (96.8, 31.3), (114, 55.8)], radius: 3), slot: 1),
            Stroke(path: line(68.8, 32, 68.8, 79.9), slot: 2), Stroke(path: line(69.5, 83, 69.6, 83), slot: 2),
            Stroke(path: line(80.8, 67.8, 88.1, 67.8), slot: 3),
            Stroke(path: quad(19, 72, 24, 58, 38.7, 56), slot: 4),
            Stroke(path: quad(137.6, 56, 156, 58, 160, 80), slot: 5)
        ]
        for (i, x) in [43.8, 128.0].enumerated() {
            var wheel = Path()
            wheel.addArc(center: CGPoint(x: x, y: 86), radius: 11, startAngle: .degrees(-90), endAngle: .degrees(270), clockwise: false)
            strokes.append(Stroke(path: wheel, slot: 6 + i, part: .wheel(x)))
            strokes.append(Stroke(path: line(x, 79, x, 83), slot: 6 + i, part: .spoke(x)))
        }
        strokes.append(Stroke(path: cloud(128, 14, 32, 16), slot: 9, part: .taxiCloud))
        return strokes
    }()

    static let hotel: [Stroke] = {
        var strokes = [
            Stroke(path: line(14, 116, 162, 116), slot: 0),
            Stroke(path: polyline([(43, 116), (43, 14), (132.5, 14), (132.5, 116)]), slot: 1),
            Stroke(path: line(62, 9, 113, 9), slot: 2, width: 3.1)
        ]
        for x in [60.0, 87.75, 115.5] { strokes.append(Stroke(path: line(x, 24, x + 0.1, 24), slot: 3)) }
        strokes.append(Stroke(path: polyline([(77, 116), (77, 92), (98, 92), (98, 116)]), slot: 4))
        for (row, y) in [72.0, 56, 40].enumerated() {
            for (column, x) in [62.0, 87.75, 113.5].enumerated() {
                let i = row * 3 + column
                let window = Path(roundedRect: CGRect(x: x - 4.5, y: y - 3.5, width: 9, height: 7), cornerRadius: 1.5)
                strokes.append(Stroke(path: window, slot: 5, extra: Double(i) * 0.03, part: .window(i)))
            }
        }
        strokes += [
            Stroke(path: Path(ellipseIn: CGRect(x: 17, y: 82, width: 16, height: 22)), slot: 6, part: .tree),
            Stroke(path: line(25, 96, 25, 116), slot: 6, part: .tree),
            Stroke(path: line(25, 104, 29, 100), slot: 6, part: .tree),
            Stroke(path: cloud(148, 112, 22, 9), slot: 7),
            Stroke(path: cloud(154, 4, 28, 13), slot: 8, part: .hotelCloud)
        ]
        return strokes
    }()

    static func line(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Path { polyline([(x1, y1), (x2, y2)]) }
    private static func polyline(_ points: [(Double, Double)]) -> Path {
        var path = Path()
        for (i, point) in points.enumerated() {
            let p = CGPoint(x: point.0, y: point.1)
            if i == 0 { path.move(to: p) } else { path.addLine(to: p) }
        }
        return path
    }
    private static func quad(_ x: Double, _ y: Double, _ cx: Double, _ cy: Double, _ ex: Double, _ ey: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x, y: y))
        path.addQuadCurve(to: CGPoint(x: ex, y: ey), control: CGPoint(x: cx, y: cy))
        return path
    }
    private static func cloud(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x - w * 0.32, y: y + h / 2))
        path.addLine(to: CGPoint(x: x + w * 0.32, y: y + h / 2))
        path.addCurve(to: CGPoint(x: x + w * 0.29, y: y - h * 0.12),
                      control1: CGPoint(x: x + w * 0.58, y: y + h / 2), control2: CGPoint(x: x + w * 0.56, y: y - h * 0.10))
        path.addCurve(to: CGPoint(x: x - w * 0.24, y: y - h * 0.14),
                      control1: CGPoint(x: x + w * 0.16, y: y - h * 0.70), control2: CGPoint(x: x - w * 0.17, y: y - h * 0.70))
        path.addCurve(to: CGPoint(x: x - w * 0.32, y: y + h / 2),
                      control1: CGPoint(x: x - w * 0.54, y: y - h * 0.28), control2: CGPoint(x: x - w * 0.61, y: y + h / 2))
        path.closeSubpath()
        return path
    }

    /// Round each specified corner with short quadratic tangents, retaining the design vertices.
    private static func rounded(_ points: [(Double, Double)], radius: Double, closed: Bool = false) -> Path {
        var path = Path()
        guard let first = points.first else { return path }
        if closed, let last = points.last, points.count > 2 {
            let p = CGPoint(x: first.0, y: first.1)
            let before = inset(from: p, toward: CGPoint(x: last.0, y: last.1), radius: radius)
            let after = inset(from: p, toward: CGPoint(x: points[1].0, y: points[1].1), radius: radius)
            path.move(to: before)
            path.addQuadCurve(to: after, control: p)
            appendRounded(points + [first, points[1]], radius: radius, to: &path, closeAt: before)
            path.closeSubpath()
        } else {
            path.move(to: CGPoint(x: first.0, y: first.1))
            appendRounded(points, radius: radius, to: &path)
        }
        return path
    }

    private static func appendRounded(_ points: [(Double, Double)], radius: Double, to path: inout Path, closeAt: CGPoint? = nil) {
        guard points.count > 1 else { return }
        let limit = closeAt == nil ? points.count - 1 : points.count - 2
        for i in 1..<limit {
            let p = CGPoint(x: points[i].0, y: points[i].1)
            let before = inset(from: p, toward: CGPoint(x: points[i - 1].0, y: points[i - 1].1), radius: radius)
            let after = inset(from: p, toward: CGPoint(x: points[i + 1].0, y: points[i + 1].1), radius: radius)
            path.addLine(to: before)
            path.addQuadCurve(to: after, control: p)
        }
        if let closeAt { path.addLine(to: closeAt) }
        else if let last = points.last { path.addLine(to: CGPoint(x: last.0, y: last.1)) }
    }

    private static func inset(from p: CGPoint, toward q: CGPoint, radius: Double) -> CGPoint {
        let dx = q.x - p.x, dy = q.y - p.y
        let distance = hypot(dx, dy)
        let fraction = min(radius, distance / 2) / max(distance, 0.001)
        return CGPoint(x: p.x + dx * fraction, y: p.y + dy * fraction)
    }
}
