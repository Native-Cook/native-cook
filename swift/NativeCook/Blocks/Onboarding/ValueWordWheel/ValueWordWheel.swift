import SwiftUI

enum ValueWordWheelIcon {
    case card, bars, shield, bolt, globe
}

struct ValueWordWheelItem: Equatable {
    var word: String
    var icon: ValueWordWheelIcon

    static let samples = [
        ValueWordWheelItem(word: "Spend", icon: .card),
        ValueWordWheelItem(word: "Earn", icon: .bars),
        ValueWordWheelItem(word: "Save", icon: .shield),
        ValueWordWheelItem(word: "Pay", icon: .bolt),
        ValueWordWheelItem(word: "Invest", icon: .globe),
    ]
}

/// A sign-up entry screen. In the white upper half a wheel of single words turns one row at a
/// time: the word on the focus line is sharp and dark with its icon, the rows around it are faint
/// and soft, and the list never ends. Below, a blue light breathes behind the headline and the two
/// buttons. The wheel can be dragged, and a faint word can be tapped into focus.
struct ValueWordWheel: View {
    /// The words of the wheel, in the order they come into focus; the first one starts there.
    var items = ValueWordWheelItem.samples
    var headline = "Your money,\nupgraded"
    var caption = "Save, earn and invest\nwith one account for everything."
    var primaryLabel = "Get started"
    var secondaryLabel = "I already have an account"
    /// Behind the wheel.
    var background = Color.white
    /// The words.
    var ink = Color(hex: 0x1C1C1E)
    /// The light at the bottom, from its pale edge down to the deepest blue.
    var glow = [Color(hex: 0xCDEEFF), Color(hex: 0x09BFFF), Color(hex: 0x0093FB), Color(hex: 0x166AFF)]
    /// Turns the wheel by one word every `ValueWordWheelMotion.step` seconds; a touch holds it for a while.
    var autoPlay = false
    /// Called with the place in `items` of the word that comes into focus.
    var onChange: ((Int) -> Void)? = nil
    var onPrimary: (() -> Void)? = nil
    var onSecondary: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the screen appeared: the light's clock.
    @State private var appeared = Date()
    /// The turn in progress, or the last one. The wheel's place is in rows: row n is on the focus
    /// line when it is n. It only ever grows or shrinks; the words repeat every `items.count` rows.
    @State private var roll = Roll(from: 0, to: 0, speed: 0, start: .distantPast)
    /// Where the wheel would be with the finger where the drag began; nil when no finger is on it.
    @State private var grab: Double? = nil
    /// The wheel's place under the finger.
    @State private var held = 0.0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tickHaptic = UISelectionFeedbackGenerator()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private struct Roll {
        var from: Double
        var to: Double
        /// Rows a second at the start.
        var speed: Double
        var start: Date
    }

    var body: some View {
        GeometryReader { proxy in
            // The reader is laid out inside the safe area; the screen itself is that much larger.
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let focusY = max(size.height - Metrics.glowTop - Metrics.focusAbove, insets.top + 100)
            let bottom = max(insets.bottom + 6, 24)

            ZStack(alignment: .topLeading) {
                background
                TimelineView(.animation(paused: reduceMotion)) { timeline in
                    ZStack(alignment: .topLeading) {
                        ValueWordWheelGlow(
                            clock: reduceMotion ? 0 : timeline.date.timeIntervalSince(appeared),
                            background: background, colors: glow
                        )
                        wheel(position: position(at: timeline.date), width: size.width)
                            .offset(y: focusY - Metrics.wheel / 2)
                    }
                }
                foot
                    .padding(.bottom, bottom)
                    .frame(width: size.width, height: size.height, alignment: .bottomLeading)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The wheel

    private func wheel(position: Double, width: Double) -> some View {
        // Enough rows to fill the wheel however few words there are, and a whole number of rounds.
        let rows = items.count * Int((8 / Double(items.count)).rounded(.up))
        let focus = Self.index(roll.to, items.count)
        return ZStack(alignment: .topLeading) {
            // A row keeps its word; it is drawn at the nearest of its places on the endless wheel.
            ForEach(0..<rows, id: \.self) { index in
                let d = Self.offset(index, position, rows)
                if abs(d) < 3 {
                    let item = items[index % items.count]
                    HStack(spacing: Metrics.iconGap) {
                        ValueWordWheelGlyph(icon: item.icon)
                            .frame(width: Metrics.icon, height: Metrics.icon)
                            .scaleEffect(Self.pop(d))
                        Text(item.word)
                            .font(.system(size: Metrics.word, weight: .medium))
                            .tracking(-0.5)
                            .foregroundStyle(ink)
                            .lineLimit(1)
                            .fixedSize()
                    }
                    .frame(height: Metrics.row)
                    .blur(radius: min(2, abs(d)) * Metrics.soften)
                    .opacity(Self.dim(d))
                    .offset(x: Metrics.left, y: Metrics.wheel / 2 + (d - 0.5) * Metrics.row)
                }
            }
        }
        .frame(width: width, height: Metrics.wheel, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .gesture(drag)
        .accessibilityElement(children: .ignore)
        .accessibilityValue(items[focus].word)
        .accessibilityAdjustableAction { direction in
            pick(direction == .increment ? 1 : -1)
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let pull = value.translation.height / Metrics.row
                if grab == nil {
                    // A touch that does not move is a tap.
                    guard abs(value.translation.height) > Metrics.slop else { return }
                    let now = position(at: .now)
                    held = now
                    grab = now + pull
                    touches += 1
                    tickHaptic.prepare()
                }
                guard let grab else { return }
                let to = grab - pull
                // One tick for each row that crosses the focus line.
                if to.rounded() != held.rounded() { tickHaptic.selectionChanged() }
                held = to
            }
            .onEnded { value in
                if grab == nil {
                    pick(Int(((value.location.y - Metrics.wheel / 2) / Metrics.row).rounded()))
                } else {
                    let speed = -value.velocity.height / Metrics.row
                    roll(to: (held + speed * M.coast).rounded(), speed: speed)
                    touches += 1
                }
            }
    }

    // MARK: The foot

    private var foot: some View {
        VStack(alignment: .leading, spacing: 0) {
            ValueWordWheelMark()
                .frame(width: Metrics.tile, height: Metrics.tile)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(.white))
                .padding(.leading, Metrics.left)
                .accessibilityHidden(true)
            Text(headline)
                .font(.system(size: 26, weight: .medium))
                .tracking(-0.4)
                .lineSpacing(3)
                .foregroundStyle(.white)
                .padding(.horizontal, Metrics.left)
                .padding(.top, 18)
            Text(caption)
                .font(.system(size: 12.5))
                .lineSpacing(2)
                .foregroundStyle(.white.opacity(0.6))
                .padding(.horizontal, Metrics.left)
                .padding(.top, 14)
            VStack(spacing: 12) {
                Button {
                    tapHaptic.impactOccurred()
                    onPrimary?()
                } label: {
                    Text(primaryLabel)
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(Palette.buttonInk)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(height: Metrics.button)
                        .background(Capsule().fill(.white))
                }
                Button {
                    tapHaptic.impactOccurred()
                    onSecondary?()
                } label: {
                    Text(secondaryLabel)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                        .lineLimit(1)
                        .frame(maxWidth: .infinity)
                        .frame(height: Metrics.button)
                        .background(Capsule().fill(.white.opacity(0.12)))
                        .overlay(Capsule().strokeBorder(.white.opacity(0.2), lineWidth: 1))
                }
            }
            .buttonStyle(PressStyle())
            .padding(.horizontal, Metrics.buttonSide)
            .padding(.top, 40)
        }
    }

    // MARK: Behaviour

    /// The wheel's place at `date`: under the finger, or on the spring of its last turn.
    private func position(at date: Date) -> Double {
        if grab != nil { return held }
        let t = date.timeIntervalSince(roll.start)
        guard t < M.settled else { return roll.to }
        return roll.from + M.spring.value(target: roll.to - roll.from, initialVelocity: roll.speed, time: max(0, t))
    }

    private func speed(at date: Date) -> Double {
        let t = date.timeIntervalSince(roll.start)
        guard grab == nil, t < M.settled else { return 0 }
        return M.spring.velocity(target: roll.to - roll.from, initialVelocity: roll.speed, time: max(0, t))
    }

    /// Turns the wheel to row `to`, carrying on at `speed` rows a second, or at the speed it has.
    private func roll(to: Double, speed: Double? = nil) {
        let now = Date()
        // Reduced motion: the word changes in place.
        let from = reduceMotion ? to : position(at: now)
        let carried = reduceMotion ? 0 : speed ?? self.speed(at: now)
        grab = nil
        roll = Roll(from: from, to: to, speed: carried, start: now)
        onChange?(Self.index(to, items.count))
    }

    /// A faint word that was tapped comes into focus.
    private func pick(_ rowsAway: Int) {
        if rowsAway != 0 { tickHaptic.selectionChanged() }
        roll(to: roll.to + Double(rowsAway))
        touches += 1
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            // A touch takes over; a while later the wheel turns back to the first word and starts over.
            try? await Task.sleep(for: .seconds(M.userHold))
            while grab != nil, !Task.isCancelled { try? await Task.sleep(for: .seconds(0.1)) }
            guard !Task.isCancelled else { return }
            let round = Double(items.count)
            roll(to: (roll.to / round).rounded() * round)
        }
        // Step n happens n × step after the origin, however late the one before it ran.
        let clock = ContinuousClock()
        let origin = clock.now
        var n = 1.0
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(M.step * n))
            guard !Task.isCancelled else { return }
            roll(to: roll.to + 1)
            n += 1
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    // MARK: The rows

    private static func index(_ row: Double, _ count: Int) -> Int {
        ((Int(row) % count) + count) % count
    }

    /// How far row `index` is from the focus line, in rows; the wheel repeats every `count` rows.
    private static func offset(_ index: Int, _ position: Double, _ count: Int) -> Double {
        let d = Double(index) - position
        return d - Double(count) * (d / Double(count)).rounded()
    }

    private static func dim(_ d: Double) -> Double {
        let a = abs(d)
        if a <= 1 { return 1 - (1 - Metrics.dimNear) * a }
        if a <= 2 { return Metrics.dimNear - (Metrics.dimNear - Metrics.dimFar) * (a - 1) }
        return max(0, Metrics.dimFar * (3 - a))
    }

    /// The icon's size: it pops as its word arrives from below, and shrinks as the word leaves upwards.
    private static func pop(_ d: Double) -> Double {
        let u = min(1, abs(d))
        if d < 0 { return 1 - (1 - Metrics.popFrom) * u }
        let back = 1 + (Metrics.popBack + 1) * pow(-u, 3) + Metrics.popBack * u * u
        return Metrics.popFrom + (1 - Metrics.popFrom) * back
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .contentShape(Capsule())
                .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
                .animation(.easeOut(duration: Metrics.pressFor), value: configuration.isPressed)
        }
    }
}

private typealias M = ValueWordWheelMotion

enum ValueWordWheelMotion {
    /// autoPlay: one word every `step` seconds, so the five words come round once a `loop`.
    static let step = 1.6
    static let loop = 8.0
    /// After a touch the wheel waits this long, then turns back to the first word and starts over.
    static let userHold = 3.0
    /// The roll: it overshoots the row by about two points and has settled in 0.45 s.
    static let spring = Spring(mass: 1, stiffness: 180, damping: 20)
    /// The spring is taken to be at rest after this long.
    static let settled = 1.5
    /// A flick carries the wheel on by this many seconds of its speed.
    static let coast = 0.18
}

private enum Palette {
    static let buttonInk = Color(hex: 0x050505)
    static let mark = Color(hex: 0x1C1C1E)
}

private enum Metrics {
    /// The design is 393 points wide; the light's widths scale with the screen.
    static let designWidth = 393.0
    static let left = 56.0
    static let row = 72.0
    static let icon = 28.0
    static let iconGap = 12.0
    static let word = 34.0
    /// Two and a half rows show on each side of the focus line.
    static let wheel = row * 5
    /// A word one row from the focus line, two rows from it, and how soft each row away makes it.
    static let dimNear = 0.12
    static let dimFar = 0.05
    static let soften = 1.6
    /// The icon of the word that comes into focus grows from this size, past its own, and settles.
    static let popFrom = 0.6
    static let popBack = 3.0
    /// A finger that moves less than this has tapped.
    static let slop = 6.0

    /// The light is one large ellipse whose centre lies below the screen, so its upper edge is a
    /// dome that stands higher in the middle than at the sides.
    static let domeRX = 553.0
    static let domeRY = 576.0
    static let domeBelow = 119.0
    /// Where the colours sit from the centre out to the edge: deepest, deepest, blue, cyan, pale, background.
    static let domeStops = [0, 0.4, 0.644, 0.852, 0.94, 1]
    /// The dome rises and falls, and a brighter patch in it drifts from side to side.
    static let domeRise = 10.0
    static let domePeriod = 4.0
    static let spotRX = 260.0
    static let spotRY = 130.0
    static let spotAbove = 332.0
    static let spotDrift = 20.0
    static let spotPeriod = 8.0
    static let spotAlpha = 0.55
    /// cos², so the patch has no rim.
    static let spotAt = [0, 0.2, 0.4, 0.6, 0.8, 1]
    static let spotFalloff = [1, 0.905, 0.655, 0.345, 0.095, 0]
    /// The dome's top, and the focus line above it.
    static let glowTop = domeRY - domeBelow
    static let focusAbove = 138.0

    static let tile = 40.0
    static let button = 50.0
    static let buttonSide = 40.0
    static let pressScale = 0.97
    static let pressFor = 0.13
}

/// The light at the bottom of the screen: a dome that breathes, and a brighter patch drifting in it.
struct ValueWordWheelGlow: View {
    /// Seconds since the screen appeared.
    let clock: Double
    let background: Color
    /// Pale, cyan, blue, deepest.
    let colors: [Color]

    var body: some View {
        Canvas { context, size in
            let k = size.width / Metrics.designWidth
            let ring = [colors[3], colors[3], colors[2], colors[1], colors[0], background]
            let dome = Gradient(stops: zip(ring, Metrics.domeStops).map { Gradient.Stop(color: $0, location: $1) })
            context.fillEllipse(
                center: CGPoint(x: size.width / 2, y: size.height + Metrics.domeBelow + Metrics.domeRise * sin(clock / Metrics.domePeriod * tau)),
                radii: CGSize(width: Metrics.domeRX * k, height: Metrics.domeRY),
                gradient: dome
            )
            let spot = Gradient(stops: zip(Metrics.spotFalloff, Metrics.spotAt).map {
                Gradient.Stop(color: colors[1].opacity(Metrics.spotAlpha * $0), location: $1)
            })
            context.fillEllipse(
                center: CGPoint(x: size.width / 2 + Metrics.spotDrift * sin(clock / Metrics.spotPeriod * tau), y: size.height - Metrics.spotAbove),
                radii: CGSize(width: Metrics.spotRX * k, height: Metrics.spotRY),
                gradient: spot
            )
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

/// One of the five icons, drawn in a 28 × 28 box and scaled to the frame.
struct ValueWordWheelGlyph: View {
    let icon: ValueWordWheelIcon

    private struct Part {
        let path: Path
        let color: Color
        var stroke: Double? = nil
    }

    private static func box(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r, style: .circular)
    }

    private static func parts(_ icon: ValueWordWheelIcon) -> [Part] {
        switch icon {
        case .card:
            return [
                Part(path: box(3, 6, 22, 16, 4), color: Color(hex: 0x7B5CFF)),
                Part(path: Path(CGRect(x: 3, y: 10.5, width: 22, height: 3.5)), color: Color(hex: 0xCFC4FF)),
                Part(path: box(6.5, 16.5, 6, 2.5, 1.25), color: Color(hex: 0xCFC4FF)),
            ]
        case .bars:
            return [
                Part(path: box(3, 14, 6, 11, 2), color: Color(hex: 0xFFB286)),
                Part(path: box(11, 9, 6, 16, 2), color: Color(hex: 0xFF9E62)),
                Part(path: box(19, 4, 6, 21, 2), color: Color(hex: 0xFF8A3D)),
            ]
        case .shield:
            var whole = Path()
            whole.move(to: CGPoint(x: 14, y: 2.5))
            whole.addLine(to: CGPoint(x: 24, y: 6))
            whole.addLine(to: CGPoint(x: 24, y: 13.2))
            whole.addCurve(to: CGPoint(x: 14, y: 25.8), control1: CGPoint(x: 24, y: 19.4), control2: CGPoint(x: 19.9, y: 23.7))
            whole.addCurve(to: CGPoint(x: 4, y: 13.2), control1: CGPoint(x: 8.1, y: 23.7), control2: CGPoint(x: 4, y: 19.4))
            whole.addLine(to: CGPoint(x: 4, y: 6))
            whole.closeSubpath()
            var half = Path()
            half.move(to: CGPoint(x: 14, y: 2.5))
            half.addLine(to: CGPoint(x: 4, y: 6))
            half.addLine(to: CGPoint(x: 4, y: 13.2))
            half.addCurve(to: CGPoint(x: 14, y: 25.8), control1: CGPoint(x: 4, y: 19.4), control2: CGPoint(x: 8.1, y: 23.7))
            half.closeSubpath()
            return [Part(path: whole, color: Color(hex: 0x35C8F5)), Part(path: half, color: Color(hex: 0x7FDDFA))]
        case .bolt:
            var bolt = Path()
            bolt.addLines([
                CGPoint(x: 16.5, y: 2), CGPoint(x: 5.5, y: 15.5), CGPoint(x: 12.5, y: 15.5), CGPoint(x: 11, y: 26),
                CGPoint(x: 22.5, y: 12), CGPoint(x: 15.3, y: 12),
            ])
            bolt.closeSubpath()
            return [Part(path: bolt, color: Color(hex: 0xFF4D4D))]
        case .globe:
            var equator = Path()
            equator.move(to: CGPoint(x: 2.5, y: 14))
            equator.addLine(to: CGPoint(x: 25.5, y: 14))
            return [
                Part(path: Path(ellipseIn: CGRect(x: 2.5, y: 2.5, width: 23, height: 23)), color: Color(hex: 0x6C7BFF)),
                Part(path: Path(ellipseIn: CGRect(x: 9.2, y: 2.5, width: 9.6, height: 23)), color: Color(hex: 0xDDE1FF), stroke: 1.6),
                Part(path: equator, color: Color(hex: 0xDDE1FF), stroke: 1.6),
            ]
        }
    }

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / 28, y: size.height / 28)
            for part in Self.parts(icon) {
                if let width = part.stroke {
                    context.stroke(part.path, with: .color(part.color), style: StrokeStyle(lineWidth: width, lineCap: .round))
                } else {
                    context.fill(part.path, with: .color(part.color))
                }
            }
        }
    }
}

/// The app's mark on its tile: two joined dots and two loose ones, in a 40 × 40 box.
struct ValueWordWheelMark: View {
    private static let dots: [(Double, Double, Double)] = [(15, 15, 3), (15, 25, 3), (25, 15, 3), (25, 25, 2)]

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / 40, y: size.height / 40)
            var link = Path()
            link.move(to: CGPoint(x: 15, y: 15))
            link.addLine(to: CGPoint(x: 15, y: 25))
            context.stroke(link, with: .color(Palette.mark), style: StrokeStyle(lineWidth: 2.4, lineCap: .round))
            for (x, y, r) in Self.dots {
                context.fill(Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)), with: .color(Palette.mark))
            }
        }
    }
}

#Preview {
    ValueWordWheel(autoPlay: true)
}
