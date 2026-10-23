import SwiftUI

struct DiscTrayPickerDisc: Equatable {
    /// Shown under the tray while the disc is in it.
    var name: String
    /// The print: the first two colours blend corner to corner, the third and the fourth are soft spots on it.
    var colors: [Color]
    /// How strong the rainbow sheen is on this print, 0…1.
    var sheen: Double

    static let samples = [
        DiscTrayPickerDisc(name: "Olive Hours", colors: [Color(hex: 0x3a3716), Color(hex: 0x16150a), Color(hex: 0xd9b24a), Color(hex: 0x6b5a1e)], sheen: 0.7),
        DiscTrayPickerDisc(name: "Crimson Static", colors: [Color(hex: 0x8f1020), Color(hex: 0x14040a), Color(hex: 0xf0364c), Color(hex: 0x3a0a12)], sheen: 1),
        DiscTrayPickerDisc(name: "Rose Signal", colors: [Color(hex: 0xf7a8e0), Color(hex: 0xd63fb8), Color(hex: 0xffe1f6), Color(hex: 0xb3279a)], sheen: 0.55),
        DiscTrayPickerDisc(name: "Ember Coast", colors: [Color(hex: 0xff5a1f), Color(hex: 0xc81e0c), Color(hex: 0xffa040), Color(hex: 0x9c1408)], sheen: 0.6),
        DiscTrayPickerDisc(name: "Silver Tide", colors: [Color(hex: 0x2a2d34), Color(hex: 0x0b0c0f), Color(hex: 0xc9d0da), Color(hex: 0x5a6170)], sheen: 0.95),
        DiscTrayPickerDisc(name: "Aqua Motel", colors: [Color(hex: 0x1a8f93), Color(hex: 0x0a4b52), Color(hex: 0xb5fff4), Color(hex: 0x52d6cd)], sheen: 0.65),
    ]
}

/// A disc player seen from above, over a grid of compact discs. Tapping a disc lifts it out of the
/// grid and flies it into the tray, where it grows to the well's size and turns under a rainbow sheen
/// that stays with the light; tapping another one swaps them in mid-air, and tapping the disc in the
/// tray sends it back to its place. The name of the loaded disc shows under the tray.
struct DiscTrayPicker: View {
    var title = "Welcome to Sidetrack"
    /// Shown under the tray while it is empty.
    var prompt = "Choose a disc"
    var action = "Continue"
    /// Up to six discs, laid out three to a row.
    var discs = DiscTrayPickerDisc.samples
    /// Index of the disc in the tray at first; nil leaves the tray empty.
    var initial: Int? = nil
    /// Loads three discs one after the other and empties the tray by itself, in a loop.
    var autoPlay = false
    /// Called with the index of the disc that goes into the tray, or nil when the tray is emptied.
    var onSelect: ((Int?) -> Void)? = nil
    /// Called with the index of the loaded disc when the button is tapped.
    var onContinue: ((Int?) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts taps: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// Each disc's way from its place in the grid, 0, to the tray, 1.
        var flight: [Track] = []
        /// Each disc's dip as it is tapped, and its swell as it lands in the tray.
        var press: [Track] = []
        var seat: [Track] = []
        /// The print's angle in degrees while the disc is not turning in the tray.
        var angle: [Track] = []
        /// When a disc started to turn in the tray, and from which angle.
        var spinSince: [Date?] = []
        var spinFrom: [Double] = []
        /// The lines under the tray, 0…1: the prompt first, then each disc's name.
        var shown: [Track] = []
        var button = Track(0)

        var loaded: Int?
        /// The order the discs are drawn in; the one that moved last is on top.
        var order: [Int] = []
        var began = Date.now
        /// A finger chose the disc that is flying, so its landing is felt.
        var byHand = false

        func printAngle(_ i: Int, at now: Date) -> Double {
            guard let since = spinSince[i] else { return angle[i].value(at: now) }
            return spinFrom[i] + Metrics.spinRate * max(0, now.timeIntervalSince(since))
        }
    }

    private var count: Int { min(discs.count, 6) }
    private var home: Int? { initial.flatMap { (0..<count).contains($0) ? $0 : nil } }
    private var auto: Bool { autoPlay && !reduceMotion && count > 1 }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let ready = motion.flight.count == count
            let seated = ready && motion.loaded.map { motion.flight[$0].value(at: now) >= 1 } ?? false

            Canvas { context, _ in
                guard ready else { return }
                context.draw(
                    Text(title).font(.system(size: 17, weight: .bold)).foregroundStyle(Color(hex: 0x1a1a1a)),
                    at: CGPoint(x: Metrics.width / 2, y: Metrics.titleY)
                )
                drawTray(context)
                drawCaptions(context, now: now)
                for i in 0..<count {
                    context.fill(circle(slot(i), Metrics.disc / 2), with: .color(Color(hex: 0xe6e6e6)))
                }
                for i in motion.order {
                    drawDisc(context, i, now: now)
                }
                drawButton(context, press: motion.button.value(at: now))
            }
            .frame(width: Metrics.width, height: Metrics.height)
            .contentShape(Rectangle())
            .gesture(SpatialTapGesture().onEnded { tap(at: $0.location) })
            .sensoryFeedback(trigger: seated) { _, landed in landed && motion.byHand ? .impact(weight: .light) : nil }
        }
        .sensoryFeedback(.selection, trigger: touches)
        .accessibilityRepresentation {
            VStack {
                Text(title)
                ForEach(0..<count, id: \.self) { i in
                    Button(discs[i].name) { pick(i) }
                        .accessibilityAddTraits(motion.loaded == i ? .isSelected : [])
                }
                Button(action) { proceed() }
            }
        }
        .onAppear { reset() }
        .onChange(of: discs.count) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Drawing

    private func circle(_ center: CGPoint, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: center.x - radius, y: center.y - radius, width: radius * 2, height: radius * 2))
    }

    /// The centre of disc `i`'s place in the grid.
    private func slot(_ i: Int) -> CGPoint {
        let pitch = Metrics.disc + Metrics.gap
        return CGPoint(
            x: Metrics.width / 2 + Double(i % 3 - 1) * pitch,
            y: Metrics.gridY + Metrics.disc / 2 + Double(i / 3) * pitch
        )
    }

    private func drawTray(_ context: GraphicsContext) {
        let box = CGRect(x: (Metrics.width - Metrics.tray) / 2, y: Metrics.trayY, width: Metrics.tray, height: Metrics.tray)
        var raised = context
        raised.addFilter(.shadow(color: .black.opacity(0.1), radius: 15, x: 0, y: 10))
        raised.fill(
            RoundedRectangle(cornerRadius: Metrics.trayRadius, style: .continuous).path(in: box),
            with: .linearGradient(Gradient(colors: [Color(hex: 0xe1e1e1), Color(hex: 0xd5d5d5)]), startPoint: CGPoint(x: 0, y: box.minY), endPoint: CGPoint(x: 0, y: box.maxY))
        )
        let center = Metrics.well
        let radius = Metrics.wellSize / 2
        context.fill(
            circle(center, radius),
            with: .radialGradient(Gradient(colors: [Color(hex: 0xbababa), Color(hex: 0xb0b0b0)]), center: center, startRadius: 0, endRadius: radius)
        )
        context.stroke(circle(center, radius - 1.5), with: .color(Color(hex: 0xc6c6c6)), lineWidth: 3)
        context.stroke(circle(center, radius - 3.5), with: .color(.black.opacity(0.05)), lineWidth: 1)
        context.fill(circle(center, 5), with: .color(Color(hex: 0x9c9c9c)))
    }

    /// The prompt and the discs' names share one line; the one that applies is the one that shows.
    private func drawCaptions(_ context: GraphicsContext, now: Date) {
        for k in motion.shown.indices {
            let shown = motion.shown[k].value(at: now)
            guard shown > 0.01 else { continue }
            var line = context
            line.opacity = shown
            line.draw(
                Text(k == 0 ? prompt : discs[k - 1].name).font(.system(size: 13, weight: .medium)).foregroundStyle(Color(hex: 0x8a8a8a)),
                at: CGPoint(x: Metrics.width / 2, y: Metrics.captionY + Metrics.captionShift * (1 - shown))
            )
        }
    }

    private func drawDisc(_ context: GraphicsContext, _ i: Int, now: Date) {
        let disc = discs[i]
        let way = motion.flight[i].value(at: now)
        // Off the ground in the middle of the flight.
        let lift = sin(.pi * min(1, max(0, way)))
        let from = slot(i)
        // The path bows away from the middle column, so two discs swapping pass side by side.
        let side = from.x < Metrics.width / 2 ? -1.0 : 1.0
        let center = CGPoint(
            x: from.x + (Metrics.well.x - from.x) * way + side * Metrics.bow * lift,
            y: from.y + (Metrics.well.y - from.y) * way
        )
        let size = (Metrics.disc + (Metrics.loaded - Metrics.disc) * way)
            * (1 - Metrics.dip * motion.press[i].value(at: now))
            * (1 + Metrics.swell * motion.seat[i].value(at: now))
        let radius = size / 2
        let colors = disc.colors + Array(repeating: Color.gray, count: max(0, 4 - disc.colors.count))
        // The sheen belongs to the light: it shifts as the disc changes place, and shimmers a little at rest.
        let shimmer = reduceMotion ? 0 : Metrics.shimmer * sin(tau * (now.timeIntervalSince(motion.began) / Metrics.shimmerTime + Double(i) / 3))
        let sheenAngle = Metrics.sheenTurn * way + shimmer * (1 - way)

        var shadow = context
        shadow.addFilter(.shadow(color: .black.opacity(0.2 + 0.08 * lift), radius: 4 + 8 * lift + 3 * way, x: 0, y: 3 + 10 * lift + 2 * way))
        shadow.fill(circle(center, radius - 0.5), with: .color(colors[1]))

        var unit = context
        unit.translateBy(x: center.x, y: center.y)
        unit.scaleBy(x: radius, y: radius)
        var face = unit
        face.clip(to: unitCircle)

        var print = face
        print.rotate(by: .degrees(motion.printAngle(i, at: now)))
        print.fill(unitCircle, with: .linearGradient(Gradient(colors: [colors[0], colors[1]]), startPoint: CGPoint(x: -0.75, y: -0.75), endPoint: CGPoint(x: 0.75, y: 0.75)))
        print.fill(unitCircle, with: .radialGradient(Gradient(colors: [colors[2], colors[2].opacity(0)]), center: CGPoint(x: 0.42, y: -0.36), startRadius: 0, endRadius: 0.8))
        print.fill(unitCircle, with: .radialGradient(Gradient(colors: [colors[3], colors[3].opacity(0)]), center: CGPoint(x: -0.5, y: 0.45), startRadius: 0, endRadius: 0.75))
        print.stroke(Path(ellipseIn: CGRect(x: -0.95, y: -0.2, width: 1.3, height: 1.3)), with: .color(.white.opacity(0.16)), lineWidth: 0.035)
        print.stroke(Path(ellipseIn: CGRect(x: -0.1, y: -1.05, width: 1.0, height: 1.0)), with: .color(.black.opacity(0.14)), lineWidth: 0.05)

        var sheen = face
        sheen.rotate(by: .degrees(sheenAngle))
        sheen.blendMode = .screen
        sheen.opacity = disc.sheen
        sheen.fill(unitCircle, with: .conicGradient(discTrayPickerSheen, center: .zero))

        face.fill(unitCircle, with: .radialGradient(Gradient(stops: [.init(color: .black.opacity(0), location: 0.7), .init(color: .black.opacity(0.22), location: 1)]), center: .zero, startRadius: 0, endRadius: 1))
        unit.stroke(Path(ellipseIn: CGRect(x: -0.985, y: -0.985, width: 1.97, height: 1.97)), with: .color(.white.opacity(0.45)), lineWidth: 0.02)

        // The hub: a white ring with a dark edge, a grey ring inside it and the hole.
        unit.fill(discTrayPickerRing(0.32), with: .color(Color(hex: 0xf6f6f6)))
        unit.stroke(discTrayPickerRing(0.32), with: .color(Color(hex: 0x1e1e1e)), lineWidth: 0.022)
        unit.fill(discTrayPickerRing(0.21), with: .color(Color(hex: 0xcfcfcf)))
        unit.stroke(discTrayPickerRing(0.21), with: .color(Color(hex: 0x8f8f8f)), lineWidth: 0.016)
        unit.fill(discTrayPickerRing(0.105), with: .color(Color(hex: 0xa6a6a6)))
        unit.stroke(discTrayPickerRing(0.105), with: .color(Color(hex: 0x2a2a2a)), lineWidth: 0.02)
    }

    private func drawButton(_ context: GraphicsContext, press: Double) {
        var context = context
        let box = Metrics.button
        context.translateBy(x: box.midX, y: box.midY)
        context.scaleBy(x: 1 - 0.04 * press, y: 1 - 0.04 * press)
        context.fill(Capsule().path(in: CGRect(x: -box.width / 2, y: -box.height / 2, width: box.width, height: box.height)), with: .color(Color(hex: 0x111111)))
        context.draw(Text(action).font(.system(size: 15, weight: .bold)).foregroundStyle(.white), at: .zero)
    }

    // MARK: Choosing

    private func reset() {
        let now = Date.now
        motion.flight = (0..<count).map { Track($0 == home ? 1 : 0) }
        motion.press = (0..<count).map { _ in Track(0) }
        motion.seat = (0..<count).map { _ in Track(0) }
        motion.angle = (0..<count).map { _ in Track(0) }
        motion.spinFrom = (0..<count).map { _ in 0 }
        motion.spinSince = (0..<count).map { $0 == home && !reduceMotion ? now : nil }
        motion.shown = (0...count).map { Track($0 == (home.map { $0 + 1 } ?? 0) ? 1 : 0) }
        motion.order = (0..<count).filter { $0 != home } + (home.map { [$0] } ?? [])
        motion.loaded = home
        motion.began = now
    }

    private func tap(at location: CGPoint) {
        guard motion.flight.count == count else { return }
        if Metrics.button.insetBy(dx: -6, dy: -6).contains(location) {
            touch()
            proceed()
        } else if let loaded = motion.loaded, hypot(location.x - Metrics.well.x, location.y - Metrics.well.y) < Metrics.loaded / 2 {
            pick(loaded)
        } else if let hit = (0..<count).first(where: { $0 != motion.loaded && hypot(location.x - slot($0).x, location.y - slot($0).y) < Metrics.disc / 2 + Metrics.gap / 2 }) {
            pick(hit)
        }
    }

    /// A tap on disc `i`: into the tray, or back out of it when it is the one loaded.
    private func pick(_ i: Int) {
        touch()
        select(motion.loaded == i ? nil : i)
    }

    private func proceed() {
        motion.button.retarget(1, .pulse(0.08, 0.16))
        onContinue?(motion.loaded)
    }

    private func touch() {
        motion.byHand = true
        touches += 1
    }

    /// Puts disc `index` in the tray (nil empties it). The disc that was there flies back to its
    /// place while the new one flies up.
    private func select(_ index: Int?) {
        guard index != motion.loaded, motion.flight.count == count else { return }
        let now = Date.now
        if let old = motion.loaded {
            // It stops turning and comes to rest the way it was printed, by the shorter way round.
            let angle = motion.printAngle(old, at: now).truncatingRemainder(dividingBy: 360)
            motion.spinSince[old] = nil
            motion.angle[old] = Track(angle > 180 ? angle - 360 : angle)
            if index == nil { motion.press[old].retarget(1, .pulse(0.08, 0.12)) }
            move(&motion.angle[old], to: 0, .tween(Metrics.downTime, Ease.out))
            move(&motion.flight[old], to: 0, .tween(Metrics.downTime, flyCurve))
            motion.seat[old] = Track(0)
        }
        if let new = index {
            // A dip under the finger, then the lift.
            let lift = reduceMotion ? 0 : Metrics.dipTime
            motion.press[new].retarget(1, .pulse(0.08, 0.12))
            move(&motion.flight[new], to: 1, .tween(Metrics.upTime, flyCurve), delay: lift)
            if !reduceMotion {
                motion.seat[new].retarget(1, .pulse(0.06, 0.22), delay: lift + Metrics.upTime - 0.06)
                motion.spinFrom[new] = motion.printAngle(new, at: now)
                motion.spinSince[new] = now.addingTimeInterval(lift + Metrics.upTime)
            }
            motion.order.removeAll { $0 == new }
            motion.order.append(new)
        }
        motion.loaded = index
        let line = index.map { $0 + 1 } ?? 0
        for k in motion.shown.indices {
            move(&motion.shown[k], to: k == line ? 1 : 0, .tween(Metrics.captionTime, Ease.out))
        }
        onSelect?(index)
    }

    /// Under reduced motion nothing travels: every change is a jump.
    private func move(_ track: inout Track, to target: Double, _ kind: Track.Kind, delay: Double = 0) {
        track.retarget(target, reduceMotion ? .jump : kind, delay: reduceMotion ? 0 : delay)
    }

    // MARK: autoPlay

    /// The script a finger would follow: the second disc, the last, the one before it, then the tray is emptied.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, put the tray back the way it began and start over.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                motion.byHand = false
                select(home)
                try await Task.sleep(for: .seconds(0.7))
            }
            var picks: [Int] = []
            for pick in [1, count - 1, count - 2] where pick >= 0 && pick != (picks.last ?? home) {
                picks.append(pick)
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                for (step, pick) in picks.enumerated() {
                    try await clock.sleep(until: start + .seconds(Metrics.rest + Double(step) * Metrics.stepGap))
                    select(pick)
                }
                try await clock.sleep(until: start + .seconds(Metrics.rest + Double(picks.count) * Metrics.stepGap))
                select(home)
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

private enum Metrics {
    static let width = 330.0
    static let height = 612.0

    static let titleY = 12.0
    /// The player: a rounded square with a round well in it.
    static let tray = 240.0
    static let trayY = 44.0
    static let trayRadius = 42.0
    static let wellSize = 202.0
    static let well = CGPoint(x: width / 2, y: trayY + tray / 2)
    static let captionY = 308.0
    /// A line under the tray rises this far as it fades in, and sinks as it fades out.
    static let captionShift = 4.0
    static let captionTime = 0.2

    /// A disc in the grid, the gap between two, and a disc in the tray.
    static let disc = 94.0
    static let gap = 12.0
    static let gridY = 336.0
    static let loaded = 186.0
    static let button = CGRect(x: width / 2 - 63, y: 560, width: 126, height: 52)

    // The flight (seconds): a dip under the finger, up to the tray, back down to the grid.
    static let dipTime = 0.08
    static let upTime = 0.45
    static let downTime = 0.4
    /// How far a tapped disc shrinks, and how far it swells as it lands.
    static let dip = 0.05
    static let swell = 0.03
    /// How far the path bows sideways at its middle.
    static let bow = 18.0
    /// Degrees a second the print turns in the tray: once round in six seconds.
    static let spinRate = 60.0
    /// The sheen turns this many degrees on the way to the tray, and sways this many in the grid.
    static let sheenTurn = -40.0
    static let shimmer = 5.0
    static let shimmerTime = 4.0

    // autoPlay (seconds): rest, a disc every `stepGap`, the tray emptied one gap after the last.
    static let rest = 0.9
    static let stepGap = 1.8
    static let loop = 8.0
    /// After a touch the loop waits this long before it starts over.
    static let resumeAfter = 2.0
}

private let flyCurve = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.45, y: 0), endControlPoint: UnitPoint(x: 0.2, y: 1))

/// Two opposite rainbow wedges and two faint white ones between them, clockwise from 3 o'clock.
private let discTrayPickerSheen: Gradient = {
    let clear = Color.white.opacity(0)
    let rainbow = [Color(hex: 0xff2d55), Color(hex: 0xffd60a), Color(hex: 0x30d158), Color(hex: 0x40c8ff), Color(hex: 0x7d5cff)]
    var stops = [Gradient.Stop(color: clear, location: 0)]
    for start in [0.07, 0.57] {
        stops.append(.init(color: clear, location: start - 0.07))
        stops.append(.init(color: .white.opacity(0.3), location: start - 0.02))
        stops.append(.init(color: clear, location: start + 0.03))
        stops.append(.init(color: clear, location: start + 0.1))
        for (k, color) in rainbow.enumerated() {
            stops.append(.init(color: color.opacity(0.85), location: start + 0.13 + 0.03 * Double(k)))
        }
        stops.append(.init(color: clear, location: start + 0.29))
    }
    stops.append(.init(color: clear, location: 1))
    return Gradient(stops: stops)
}()

private func discTrayPickerRing(_ radius: Double) -> Path {
    Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2))
}

/// A number heading for a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case tween(Double, UnitCurve)
        /// Up to the target in the first time and back to 0 in the second.
        case pulse(Double, Double)
    }

    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var kind = Kind.jump

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0 else { return from }
        switch kind {
        case .jump: return to
        case .tween(let duration, let curve): return t >= duration ? to : from + (to - from) * curve.value(at: t / duration)
        case .pulse(let up, let down): return to * (t < up ? t / up : max(0, 1 - (t - up) / down))
        }
    }

    /// Heads for `target` from wherever it will be `delay` seconds from now, when the move begins.
    mutating func retarget(_ target: Double, _ kind: Kind, delay: Double = 0) {
        let begin = Date.now.addingTimeInterval(delay)
        from = value(at: begin)
        if case .pulse = kind { from = 0 }
        to = target
        start = begin
        self.kind = kind
    }
}

#Preview {
    ZStack {
        Color(hex: 0xefefef).ignoresSafeArea()
        DiscTrayPicker(autoPlay: true)
    }
}
