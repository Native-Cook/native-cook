import SwiftUI
import UIKit

/// The picture an item shows on the menu ring while it is not selected.
enum SegmentedRingSelectorGlyph {
    case paint, wheel, steering, lock, battery, gauge, car, music, reset
}

struct SegmentedRingSelectorOption: Equatable {
    var name: String
    /// The small line under the name.
    var tier: String
    /// The option's block on the outer ring, as 0xRRGGBB.
    var swatch: UInt32
    /// The colour the wedge and the small line take while the option is chosen.
    var accent: UInt32
}

struct SegmentedRingSelectorItem: Equatable {
    var label: String
    var glyph: SegmentedRingSelectorGlyph
    var options: [SegmentedRingSelectorOption]

    static let samples = [
        SegmentedRingSelectorItem(label: "Paint", glyph: .paint, options: [
            SegmentedRingSelectorOption(name: "Solar Orange", tier: "SIGNATURE", swatch: 0xffa400, accent: 0xf5470a),
            SegmentedRingSelectorOption(name: "Glacier Blue", tier: "SIGNATURE", swatch: 0x5bc0f5, accent: 0x1e7fd6),
            SegmentedRingSelectorOption(name: "Moss Green", tier: "HERITAGE", swatch: 0x8cc152, accent: 0x4e8f2e),
            SegmentedRingSelectorOption(name: "Graphite", tier: "CLASSIC", swatch: 0x7a828c, accent: 0x4a525c),
        ]),
        SegmentedRingSelectorItem(label: "Wheels", glyph: .wheel, options: [
            SegmentedRingSelectorOption(name: "Ten Spoke", tier: "FORGED", swatch: 0xd9a441, accent: 0xb8791f),
            SegmentedRingSelectorOption(name: "Turbine", tier: "FORGED", swatch: 0xc9ced3, accent: 0x6c7a89),
            SegmentedRingSelectorOption(name: "Mesh", tier: "CAST", swatch: 0x5a6068, accent: 0x2f3338),
        ]),
        SegmentedRingSelectorItem(label: "Steering", glyph: .steering, options: [
            SegmentedRingSelectorOption(name: "Light", tier: "COMFORT", swatch: 0x7ed6c8, accent: 0x1fa391),
            SegmentedRingSelectorOption(name: "Balanced", tier: "COMFORT", swatch: 0x5bc0f5, accent: 0x1e7fd6),
            SegmentedRingSelectorOption(name: "Heavy", tier: "SPORT", swatch: 0xf58a6b, accent: 0xe0362c),
        ]),
        SegmentedRingSelectorItem(label: "Locks", glyph: .lock, options: [
            SegmentedRingSelectorOption(name: "Auto Lock", tier: "SECURE", swatch: 0x8cc152, accent: 0x4e8f2e),
            SegmentedRingSelectorOption(name: "Walk Away", tier: "SECURE", swatch: 0xffc44d, accent: 0xd68a00),
            SegmentedRingSelectorOption(name: "Valet", tier: "LIMITED", swatch: 0xf58a6b, accent: 0xe0362c),
        ]),
        SegmentedRingSelectorItem(label: "Charge", glyph: .battery, options: [
            SegmentedRingSelectorOption(name: "Daily 80%", tier: "BATTERY CARE", swatch: 0x8cc152, accent: 0x4e8f2e),
            SegmentedRingSelectorOption(name: "Long Trip", tier: "FULL CHARGE", swatch: 0x5bc0f5, accent: 0x1e7fd6),
            SegmentedRingSelectorOption(name: "Off Peak", tier: "SCHEDULED", swatch: 0xb79cf2, accent: 0x7a4fd6),
        ]),
        SegmentedRingSelectorItem(label: "Drive", glyph: .gauge, options: [
            SegmentedRingSelectorOption(name: "Comfort", tier: "ROAD", swatch: 0x5bc0f5, accent: 0x1e7fd6),
            SegmentedRingSelectorOption(name: "Sport", tier: "ROAD", swatch: 0xffa400, accent: 0xf5470a),
            SegmentedRingSelectorOption(name: "Track", tier: "CIRCUIT", swatch: 0xf5606b, accent: 0xd61e3c),
        ]),
        SegmentedRingSelectorItem(label: "Body", glyph: .car, options: [
            SegmentedRingSelectorOption(name: "Coupe", tier: "FIXED ROOF", swatch: 0xc9ced3, accent: 0x6c7a89),
            SegmentedRingSelectorOption(name: "Spider", tier: "OPEN ROOF", swatch: 0xffc44d, accent: 0xd68a00),
        ]),
        SegmentedRingSelectorItem(label: "Sound", glyph: .music, options: [
            SegmentedRingSelectorOption(name: "Studio", tier: "FLAT", swatch: 0xb79cf2, accent: 0x7a4fd6),
            SegmentedRingSelectorOption(name: "Concert", tier: "WIDE", swatch: 0xf59ac4, accent: 0xd63a8a),
            SegmentedRingSelectorOption(name: "Lounge", tier: "WARM", swatch: 0xffc44d, accent: 0xd68a00),
        ]),
        SegmentedRingSelectorItem(label: "Reset", glyph: .reset, options: [
            SegmentedRingSelectorOption(name: "Keep Setup", tier: "NO CHANGE", swatch: 0xc9ced3, accent: 0x6c7a89),
            SegmentedRingSelectorOption(name: "Factory", tier: "ERASE ALL", swatch: 0xf5606b, accent: 0xd61e3c),
        ]),
    ]
}

/// Two concentric wheels around a white disc, with one fixed index at 12 o'clock. The inner, grey ring
/// carries the items as glyphs; the one under the index sits in a coloured wedge with its name in
/// white. The outer ring is a band of small blocks, the options of that item: the block under the
/// index stands taller in its own colour, and the disc names it. Dragging either ring turns it, and it
/// snaps on a spring; when the item changes, the outer blocks fan shut towards 12 and open again.
struct SegmentedRingSelector: View {
    var items = SegmentedRingSelectorItem.samples
    /// Index of the item under the index at first, and of its chosen option.
    var initialItem = 0
    var initialOption = 0
    /// Steps the two rings by itself, in a loop.
    var autoPlay = false
    /// Called with the item's index and its option's index when either ring is sent to a new place.
    var onSelect: ((Int, Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var motion = Motion()
    /// Counts touches: each one stops the loop and restarts the wait before it resumes.
    @State private var touches = 0

    private enum Zone {
        case menu, value
    }

    /// What moves, sampled against the `TimelineView` clock.
    private final class Motion {
        /// The menu ring's position as an item index, and the outer ring's as an option index. Both
        /// keep counting past the last one: the rings go round.
        var menu = Track(0)
        var value = Track(0)
        /// The item under the index, and each item's chosen place on its outer ring.
        var item = 0
        var chosen: [Int] = []
        var zone: Zone?
        var dragged = false
        /// The finger's angle at the last event, and the ring's position when it went down.
        var angle = 0.0
        var turned = 0.0
        var dragFrom = 0.0
        /// Where each ring was at the last frame, for the haptic ticks.
        var menuTick = 0
        var valueTick = 0
        /// The turns come from a finger, so they tick; the script's do not.
        var felt = false
        let haptic = UISelectionFeedbackGenerator()
    }

    private var auto: Bool { autoPlay && !reduceMotion && items.count > 1 }
    private var home: Int { min(max(initialItem, 0), items.count - 1) }

    var body: some View {
        TimelineView(.animation) { timeline in
            let now = timeline.date
            let _ = step(now)
            let menu = motion.menu.value(at: now)
            let value = motion.value.value(at: now)

            ZStack {
                if motion.chosen.count == items.count {
                    Canvas { context, _ in
                        draw(context, menu: menu, value: value)
                    }

                    ForEach(items.indices, id: \.self) { i in
                        let d = wrapped(Double(i) - menu, Double(items.count))
                        let at = point(d * Metrics.itemStep, Metrics.itemRadius)
                        // Under the index the glyph gives way to the item's name in white.
                        let named = clamp01(1 - abs(d) * 2.2)
                        glyph(items[i].glyph)
                            .opacity(1 - named)
                            .position(at)
                        Text(items[i].label)
                            .font(.system(size: 14, weight: .bold))
                            .foregroundStyle(.white)
                            .fixedSize()
                            .opacity(named)
                            .position(at)
                    }

                    names(menu: menu, value: value)
                }
            }
            .lineLimit(1)
            .frame(width: Metrics.size, height: Metrics.size)
            .contentShape(Circle())
            .gesture(drag)
        }
        .frame(width: Metrics.size, height: Metrics.size)
        .onAppear { reset() }
        .onChange(of: items) { reset() }
        .task(id: LoopKey(auto: auto, touches: touches)) { await runLoop() }
    }

    private struct LoopKey: Equatable {
        let auto: Bool
        let touches: Int
    }

    // MARK: Parts

    /// The option's name and the small line under it, on the disc. Between two options one leaves
    /// upwards as the next arrives from below; half-way between two items the disc is empty.
    private func names(menu: Double, value: Double) -> some View {
        let options = items[motion.item].options
        let k = value.rounded(.down)
        let part = value - k
        let seen = clamp01(1 - 2.5 * abs(menu - menu.rounded()))
        let tone = accent(menu: menu, value: value)
        return ForEach(0..<2, id: \.self) { side in
            let option = options[wrap(Int(k) + side, options.count)]
            let shown = side == 0 ? clamp01(1 - 2 * part) : clamp01(2 * part - 1)
            let slide = side == 0 ? -Metrics.nameSlide * min(1, 2 * part) : Metrics.nameSlide * min(1, 2 - 2 * part)
            VStack(spacing: 5) {
                Text(option.name)
                    .font(.system(size: 22, weight: .light))
                    .tracking(0.4)
                    .foregroundStyle(Color(hex: Palette.slate))
                Text(option.tier)
                    .font(.system(size: 12, weight: .bold))
                    .tracking(0.8)
                    .foregroundStyle(tone)
            }
            .fixedSize()
            .opacity(shown * seen)
            .position(x: Metrics.size / 2, y: Metrics.size / 2 + 2 + slide)
        }
    }

    @ViewBuilder
    private func glyph(_ glyph: SegmentedRingSelectorGlyph) -> some View {
        switch glyph {
        case .wheel, .steering:
            Canvas { context, size in
                drawGlyph(context, glyph, at: CGPoint(x: size.width / 2, y: size.height / 2))
            }
            .frame(width: 30, height: 30)
        default:
            Image(systemName: symbol(glyph))
                .font(.system(size: 21, weight: .semibold))
                .foregroundStyle(Color(hex: Palette.slate))
                .frame(width: 30, height: 30)
        }
    }

    private func symbol(_ glyph: SegmentedRingSelectorGlyph) -> String {
        switch glyph {
        case .paint: "paintpalette.fill"
        case .lock: "lock.fill"
        case .battery: "battery.100.bolt"
        case .gauge: "speedometer"
        case .car: "car.side.fill"
        case .music: "music.note"
        default: "arrow.counterclockwise"
        }
    }

    /// The two glyphs no icon set shares: a spoked wheel and a steering wheel.
    private func drawGlyph(_ context: GraphicsContext, _ glyph: SegmentedRingSelectorGlyph, at centre: CGPoint) {
        let ink = GraphicsContext.Shading.color(Color(hex: Palette.slate))
        let rim = Path(ellipseIn: CGRect(x: centre.x - 11, y: centre.y - 11, width: 22, height: 22))
        var spokes = Path()
        if glyph == .wheel {
            for n in 0..<8 {
                let turn = Double(n) / 8 * tau
                spokes.move(to: centre)
                spokes.addLine(to: CGPoint(x: centre.x + 11 * sin(turn), y: centre.y - 11 * cos(turn)))
            }
            context.stroke(rim, with: ink, lineWidth: 2.6)
            context.stroke(spokes, with: ink, lineWidth: 1.3)
            context.fill(Path(ellipseIn: CGRect(x: centre.x - 2.8, y: centre.y - 2.8, width: 5.6, height: 5.6)), with: ink)
        } else {
            spokes.move(to: CGPoint(x: centre.x - 11, y: centre.y - 1))
            spokes.addLine(to: CGPoint(x: centre.x + 11, y: centre.y - 1))
            spokes.move(to: centre)
            spokes.addLine(to: CGPoint(x: centre.x, y: centre.y + 11))
            context.stroke(rim, with: ink, lineWidth: 2.8)
            context.stroke(spokes, with: ink, lineWidth: 2.6)
            context.fill(Path(ellipseIn: CGRect(x: centre.x - 4, y: centre.y - 5, width: 8, height: 8)), with: ink)
        }
    }

    /// The outer blocks, the grey ring, the wedge with its tab, and the disc.
    private func draw(_ context: GraphicsContext, menu: Double, value: Double) {
        let options = items[motion.item].options
        let off = menu - menu.rounded()
        // The blocks fan shut towards 12 as the menu leaves an item, and open again as it reaches the next.
        let fan = smooth(clamp01(1 - 2.5 * abs(off)))
        let other = items[wrap(motion.item + (off < 0 ? -1 : 1), items.count)]
        let otherOption = other.options[wrap(motion.chosen[wrap(motion.item + (off < 0 ? -1 : 1), items.count)], other.options.count)]

        let first = Int(value.rounded(.down)) - Metrics.blocksAside
        for pass in 0..<2 {
            for j in first...first + Metrics.blocksAside * 2 + 1 {
                let d = Double(j) - value
                // The block under the index stands taller, a little wider, in its own colour.
                let lit = clamp01(1.5 - 2 * abs(d))
                guard (lit > 0) == (pass == 1) else { continue }
                // Blocks nearer the index open first.
                let open = clamp01((fan - Metrics.fanStagger * abs(d)) / (1 - Metrics.fanStagger * Double(Metrics.blocksAside)))
                let angle = d * Metrics.blockStep * (lit >= 1 ? 1 : open)
                let fade = clamp01((Metrics.fadeEnd - abs(d) * Metrics.blockStep) / (Metrics.fadeEnd - Metrics.fadeStart)) * max(open, lit)
                guard fade > 0 else { continue }
                let half = mix(Metrics.blockHalf, Metrics.litHalf, lit)
                let outer = mix(Metrics.bandOuter, Metrics.litOuter, lit)
                let grey = Color(hex: Palette.band, opacity: (abs(j) % 2 == 0 ? 0.7 : 0.45) * fade * (1 - lit))
                let path = sector(angle - half, angle + half, Metrics.bandInner, outer)
                context.fill(path, with: .color(grey))
                if lit > 0 {
                    let swatch = options[wrap(j, options.count)].swatch
                    let top = blend(swatch, otherOption.swatch, abs(off)).opacity(lit)
                    let bottom = blend(shade(swatch), shade(otherOption.swatch), abs(off)).opacity(lit)
                    context.fill(path, with: .linearGradient(Gradient(colors: [top, bottom]), startPoint: point(angle, outer), endPoint: point(angle, Metrics.bandInner)))
                }
            }
        }

        let centre = CGPoint(x: Metrics.size / 2, y: Metrics.size / 2)
        let ring = (Metrics.discRadius + Metrics.ringOuter) / 2
        context.stroke(
            Path(ellipseIn: CGRect(x: centre.x - ring, y: centre.y - ring, width: ring * 2, height: ring * 2)),
            with: .color(Color(hex: Palette.ring)), lineWidth: Metrics.ringOuter - Metrics.discRadius
        )

        let tone = accent(menu: menu, value: value)
        var wedge = sector(-Metrics.wedgeHalf, Metrics.wedgeHalf, Metrics.discRadius - 1, Metrics.ringOuter)
        wedge.move(to: point(-Metrics.tabHalf, Metrics.ringOuter - 0.5))
        wedge.addLine(to: point(0, Metrics.ringOuter + Metrics.tabRise))
        wedge.addLine(to: point(Metrics.tabHalf, Metrics.ringOuter - 0.5))
        wedge.closeSubpath()
        context.fill(wedge, with: .color(tone))
        // A little darker towards the disc.
        context.fill(wedge, with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.08)]), startPoint: point(0, Metrics.ringOuter), endPoint: point(0, Metrics.discRadius)))

        context.fillEllipse(
            center: CGPoint(x: centre.x, y: centre.y + 6),
            radii: CGSize(width: Metrics.discRadius + 24, height: Metrics.discRadius + 24),
            gradient: Gradient(stops: [
                .init(color: .black.opacity(0.2), location: 0),
                .init(color: .black.opacity(0.2), location: 0.72),
                .init(color: .black.opacity(0), location: 1),
            ])
        )
        context.fill(Path(ellipseIn: CGRect(x: centre.x - Metrics.discRadius, y: centre.y - Metrics.discRadius, width: Metrics.discRadius * 2, height: Metrics.discRadius * 2)), with: .color(.white))
    }

    /// The wedge's colour: the chosen option's, cross-fading with the next option's as the outer ring
    /// turns and with the next item's as the menu ring does.
    private func accent(menu: Double, value: Double) -> Color {
        let options = items[motion.item].options
        let k = Int(value.rounded(.down))
        let part = smooth(value - Double(k))
        let off = menu - menu.rounded()
        let next = wrap(motion.item + (off < 0 ? -1 : 1), items.count)
        let other = items[next].options[wrap(motion.chosen[next], items[next].options.count)].accent
        let a = options[wrap(k, options.count)].accent
        let b = options[wrap(k + 1, options.count)].accent
        func channel(_ shift: UInt32) -> Double {
            let own = mix(Double((a >> shift) & 0xff), Double((b >> shift) & 0xff), part)
            return mix(own, Double((other >> shift) & 0xff), abs(off)) / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }

    // MARK: The rings' moves

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let angle = bearing(value.location)
                if motion.zone == nil {
                    let reach = hypot(value.startLocation.x - Metrics.size / 2, value.startLocation.y - Metrics.size / 2)
                    guard reach > Metrics.discRadius else { return }
                    // The finger catches the ring it lands on, wherever that ring is.
                    touches += 1
                    motion.zone = reach > Metrics.ringOuter + 3 ? .value : .menu
                    motion.dragged = false
                    motion.felt = true
                    motion.angle = bearing(value.startLocation)
                    motion.turned = 0
                    let track = motion.zone == .menu ? \Motion.menu : \Motion.value
                    motion.dragFrom = motion[keyPath: track].value(at: .now)
                    motion[keyPath: track].set(motion.dragFrom)
                }
                motion.turned += wrapped(angle - motion.angle, 360)
                motion.angle = angle
                guard motion.dragged || abs(motion.turned) > Metrics.tapSlop else { return }
                motion.dragged = true
                if motion.zone == .menu {
                    motion.menu.set(motion.dragFrom - motion.turned / Metrics.itemStep)
                } else {
                    motion.value.set(motion.dragFrom - motion.turned / Metrics.blockStep)
                }
            }
            .onEnded { value in
                guard let zone = motion.zone else { return }
                touches += 1
                motion.zone = nil
                let step = zone == .menu ? Metrics.itemStep : Metrics.blockStep
                let position = (zone == .menu ? motion.menu : motion.value).value(at: .now)
                var target = position.rounded()
                var speed = 0.0
                if motion.dragged {
                    // Degrees a second around the centre, from the finger's speed across its radius.
                    let arm = CGPoint(x: value.location.x - Metrics.size / 2, y: value.location.y - Metrics.size / 2)
                    let reach = max(40, hypot(arm.x, arm.y))
                    let spin = (arm.x * value.velocity.height - arm.y * value.velocity.width) / (reach * reach) * 180 / .pi
                    speed = -spin / step
                    target = (position + speed * Metrics.coast).rounded()
                } else {
                    // A tap brings the item or the block under it to the index.
                    target = (position + bearing(value.location) / step).rounded()
                }
                if zone == .menu { selectItem(Int(target), velocity: speed) } else { selectOption(Int(target), velocity: speed) }
            }
    }

    /// A point's angle around the centre in degrees: 0 at 12 o'clock, growing clockwise, −180…180.
    private func bearing(_ location: CGPoint) -> Double {
        atan2(location.x - Metrics.size / 2, Metrics.size / 2 - location.y) * 180 / .pi
    }

    private func reset() {
        motion.chosen = items.map { _ in 0 }
        guard !items.isEmpty else { return }
        motion.item = home
        motion.chosen[home] = initialOption
        motion.menu = Track(Double(home))
        motion.value = Track(Double(initialOption))
        motion.menuTick = home
        motion.valueTick = initialOption
    }

    /// Runs once a frame: ticks as items and blocks pass the index, and hands the outer ring over to
    /// the item that has come under it.
    private func step(_ now: Date) {
        guard motion.chosen.count == items.count, !items.isEmpty else { return }
        let menu = Int(motion.menu.value(at: now).rounded())
        if menu != motion.menuTick {
            motion.menuTick = menu
            motion.item = wrap(menu, items.count)
            motion.value = Track(Double(motion.chosen[motion.item]))
            motion.valueTick = motion.chosen[motion.item]
            if motion.felt { motion.haptic.selectionChanged() }
        }
        let value = Int(motion.value.value(at: now).rounded())
        if value != motion.valueTick {
            motion.valueTick = value
            if motion.felt { motion.haptic.selectionChanged() }
        }
    }

    private var snap: Track.Kind {
        reduceMotion ? .jump : .spring(Spring(response: Metrics.response, dampingRatio: Metrics.dampingRatio))
    }

    /// Turns the menu ring until place `position` is under the index.
    private func selectItem(_ position: Int, velocity: Double = 0) {
        motion.menu.retarget(Double(position), snap, velocity: velocity)
        let item = wrap(position, items.count)
        onSelect?(item, wrap(motion.chosen[item], items[item].options.count))
    }

    /// Turns the outer ring until place `position` is under the index, and keeps it as the item's choice.
    private func selectOption(_ position: Int, velocity: Double = 0) {
        motion.chosen[motion.item] = position
        motion.value.retarget(Double(position), snap, velocity: velocity)
        onSelect?(motion.item, wrap(position, items[motion.item].options.count))
    }

    /// The nearest place on a ring of `count` that shows `index`, starting from `position`.
    private func nearest(_ index: Int, from position: Int, count: Int) -> Int {
        position + Int(wrapped(Double(index - position), Double(count)).rounded())
    }

    /// The script a finger would follow: two options on, the next item, one option on, and back.
    private func runLoop() async {
        guard auto else { return }
        do {
            if touches > 0 {
                // A touch took over: wait, turn both rings back to the resting choice, and start over from there.
                try await Task.sleep(for: .seconds(Metrics.resumeAfter))
                guard motion.zone == nil else { return }
                motion.felt = false
                selectItem(nearest(home, from: motion.menuTick, count: items.count))
                try await Task.sleep(for: .seconds(Metrics.homeTime))
                selectOption(nearest(initialOption, from: motion.valueTick, count: items[home].options.count))
                try await Task.sleep(for: .seconds(Metrics.homeTime))
            }
            let clock = ContinuousClock()
            var start = clock.now
            while true {
                // The items that are out of sight go back to their first option.
                for i in items.indices where i != motion.item { motion.chosen[i] = 0 }
                for step in Metrics.script {
                    try await clock.sleep(until: start + .seconds(step.at))
                    if step.menu { selectItem(motion.menuTick + step.by) } else { selectOption(motion.chosen[motion.item] + step.by) }
                }
                start += .seconds(Metrics.loop)
            }
        } catch {}
    }
}

/// A point `radius` from the centre at `angle` degrees, 0 at 12 o'clock and growing clockwise.
private func point(_ angle: Double, _ radius: Double) -> CGPoint {
    let turn = angle * .pi / 180
    return CGPoint(x: Metrics.size / 2 + radius * sin(turn), y: Metrics.size / 2 - radius * cos(turn))
}

/// The piece of a ring between two angles and two radii.
private func sector(_ from: Double, _ to: Double, _ inner: Double, _ outer: Double) -> Path {
    let steps = max(2, Int(((to - from) / 2).rounded(.up)))
    var path = Path()
    for n in 0...steps {
        let at = point(mix(from, to, Double(n) / Double(steps)), outer)
        if n == 0 { path.move(to: at) } else { path.addLine(to: at) }
    }
    for n in 0...steps {
        path.addLine(to: point(mix(to, from, Double(n) / Double(steps)), inner))
    }
    path.closeSubpath()
    return path
}

private enum Palette {
    static let slate: UInt32 = 0x3a4046
    static let ring: UInt32 = 0xd6d6d6
    static let band: UInt32 = 0x9aaeb0
}

private enum Metrics {
    static let size = 340.0

    // Radii, from the disc outwards.
    static let discRadius = 92.0
    static let ringOuter = 134.0
    static let itemRadius = 113.0
    static let bandInner = 142.0
    static let bandOuter = 155.0
    static let litOuter = 168.0

    // Degrees.
    static let itemStep = 40.0
    static let wedgeHalf = 22.0
    static let blockStep = 12.0
    static let blockHalf = 5.4
    static let litHalf = 6.6
    /// The band is whole down to `fadeStart` from 12 o'clock and gone by `fadeEnd`.
    static let fadeStart = 60.0
    static let fadeEnd = 150.0
    static let blocksAside = 13
    /// How much later each block further from the index opens, as a share of the fan.
    static let fanStagger = 0.03
    static let tabHalf = 3.0
    static let tabRise = 8.0
    static let nameSlide = 6.0

    // Snapping.
    static let response = 0.4
    static let dampingRatio = 0.75
    /// Seconds of the finger's speed a ring coasts for.
    static let coast = 0.2
    /// Degrees a finger may turn before a tap becomes a drag.
    static let tapSlop = 2.0

    // autoPlay (seconds): when each move starts, which ring it turns and by how many places.
    static let loop = 8.0
    static let script: [(at: Double, menu: Bool, by: Int)] = [
        (1.0, false, 1),
        (1.45, false, 1),
        (2.7, true, 1),
        (4.1, false, 1),
        (5.2, true, -1),
        (5.75, false, -2),
    ]
    /// After a touch the loop waits this long, then takes `homeTime` for each ring to return.
    static let resumeAfter = 2.0
    static let homeTime = 0.7
}

/// A number that jumps or springs towards a target, sampled against the `TimelineView` clock.
private struct Track {
    enum Kind {
        case jump
        case spring(Spring)
    }

    private var from: Double
    private var to: Double
    private var speed = 0.0
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
        case .spring(let spring): return from + spring.value(target: to - from, initialVelocity: speed, time: t)
        }
    }

    private func velocity(at date: Date) -> Double {
        let t = date.timeIntervalSince(start)
        guard t > 0, case .spring(let spring) = kind else { return 0 }
        return spring.velocity(target: to - from, initialVelocity: speed, time: t)
    }

    mutating func set(_ value: Double) {
        from = value
        to = value
        kind = .jump
    }

    /// Heads for `target` from where it is now, adding `velocity` to the speed it already has.
    mutating func retarget(_ target: Double, _ kind: Kind, velocity: Double = 0) {
        let now = Date.now
        let value = value(at: now)
        speed = self.velocity(at: now) + velocity
        from = value
        to = target
        start = now
        self.kind = kind
    }
}

private func clamp01(_ t: Double) -> Double { min(1, max(0, t)) }

private func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }

private func smooth(_ t: Double) -> Double { t * t * (3 - 2 * t) }

/// `index` brought into 0..<count, for a ring that goes round.
private func wrap(_ index: Int, _ count: Int) -> Int {
    ((index % count) + count) % count
}

/// `value` brought into −span/2…span/2: the short way round.
private func wrapped(_ value: Double, _ span: Double) -> Double {
    value - span * ((value + span / 2) / span).rounded(.down)
}

private func blend(_ a: UInt32, _ b: UInt32, _ t: Double) -> Color {
    func channel(_ shift: UInt32) -> Double {
        mix(Double((a >> shift) & 0xff), Double((b >> shift) & 0xff), t) / 255
    }
    return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
}

/// The colour a tenth darker, for the foot of a block.
private func shade(_ colour: UInt32) -> UInt32 {
    func channel(_ shift: UInt32) -> UInt32 {
        UInt32(Double((colour >> shift) & 0xff) * 0.9) << shift
    }
    return channel(16) | channel(8) | channel(0)
}

#Preview {
    ZStack {
        Color(hex: 0x22343a).ignoresSafeArea()
        SegmentedRingSelector(autoPlay: true)
    }
}
