import SwiftUI

/// A screen with a floating pill of three outlined icons. Selecting one thickens and darkens its
/// stroke and brings a dot into it, each icon in its own way: the house's drops in and bounces,
/// the picture rocks while its small circle fills, the gear turns while its ring fills. The page
/// above cross-fades to the tab's own.
struct DotIconTabBar: View {
    /// The titles of the three tabs, in the order of the icons: house, picture, gear.
    var labels = ["Home", "Photos", "Settings"]
    /// The tab that is selected at first, and the one autoPlay comes back to.
    var initialTab = 0
    var background = Color(hex: 0xD6D6D6)
    var barColor = Color(hex: 0xEFEFEF)
    /// The placeholder cards of the sample pages.
    var cardColor = Color(hex: 0xE4E4E4)
    /// The selected icon's stroke, and the titles.
    var ink = Color(hex: 0x0B0B0F)
    /// The stroke of an icon that is not selected.
    var mute = Color(hex: 0x55555A)
    /// The dot inside the selected icon.
    var accent = Color(hex: 0x5546F2)
    /// Goes through the tabs by itself every `DotIconTabBarMotion.loop` seconds.
    var autoPlay = false
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The selected tab; nil until one is chosen, which means `initialTab`.
    @State private var selected: Int? = nil
    /// What moves each tab; nil until the first change, when every tab is at rest.
    @State private var motions: [TabMotion]? = nil
    /// True while something is still moving, which is when the timeline runs.
    @State private var moving = false
    /// Counts the changes, so the timeline is paused after the last one has settled.
    @State private var moves = 0
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    private var current: Int { selected ?? initialTab }

    private func motion(_ index: Int) -> TabMotion {
        motions?[index] ?? TabMotion(selected: index == initialTab)
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let bottom = max(Metrics.barBottom, insets.bottom + Metrics.barClearance)

            TimelineView(.animation(paused: !moving)) { timeline in
                // At rest every value is its target, whatever date the paused timeline holds.
                let now = moving ? timeline.date : Date.distantFuture

                ZStack(alignment: .bottom) {
                    background

                    ForEach(0..<3, id: \.self) { index in
                        let shown = motion(index).shown.value(at: now)
                        page(index)
                            .padding(.top, insets.top + Metrics.titleTop)
                            .padding(.horizontal, Metrics.margin)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .top)
                            .scaleEffect(reduceMotion ? 1 : Metrics.contentScale + (1 - Metrics.contentScale) * shown)
                            .opacity(shown)
                            .allowsHitTesting(index == current)
                            .accessibilityHidden(index != current)
                    }

                    bar(now: now)
                        .padding(.bottom, bottom)
                }
                .ignoresSafeArea()
            }
        }
        .preferredColorScheme(.light)
        .onAppear { haptic.prepare() }
        .task(id: moves) {
            guard moves > 0 else { return }
            try? await Task.sleep(for: .seconds(M.motionFor + 0.1))
            if !Task.isCancelled { moving = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The bar

    private func bar(now: Date) -> some View {
        Canvas { context, _ in
            let strokeFrom = UIColor(mute)
            let strokeTo = UIColor(ink)
            for index in 0..<3 {
                var icon = context
                icon.translateBy(x: Metrics.iconX[index] - Metrics.icon / 2, y: (Metrics.barHeight - Metrics.icon) / 2)
                let tab = motion(index)
                let on = tab.on.value(at: now)
                let look = IconLook(
                    fill: tab.fill.value(at: now),
                    t: tab.t(at: now),
                    active: tab.active,
                    still: reduceMotion,
                    width: Metrics.stroke + (Metrics.strokeActive - Metrics.stroke) * on,
                    stroke: Self.mix(strokeFrom, strokeTo, on),
                    accent: accent
                )
                switch index {
                case 0: Self.drawHome(icon, look)
                case 1: Self.drawPhotos(icon, look)
                default: Self.drawSettings(icon, look)
                }
            }
        }
        .frame(width: Metrics.barWidth, height: Metrics.barHeight)
        .background(Capsule().fill(barColor).shadow(color: .black.opacity(0.06), radius: 7, y: 4))
        .overlay {
            HStack(spacing: 0) {
                ForEach(0..<3, id: \.self) { index in
                    Button {
                        touches += 1
                        select(index, byHand: true)
                    } label: {
                        Color.clear
                            .frame(width: Metrics.tapWidth, height: Metrics.barHeight)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(.plain)
                    .accessibilityLabel(labels[index])
                    .accessibilityAddTraits(index == current ? [.isSelected] : [])
                }
            }
        }
    }

    /// What one icon is drawn with at this moment.
    private struct IconLook {
        /// The dot, from absent (0) to full (1).
        let fill: Double
        /// Seconds since the tab was last selected.
        let t: Double
        let active: Bool
        let still: Bool
        let width: Double
        let stroke: Color
        let accent: Color

        var style: StrokeStyle { StrokeStyle(lineWidth: width, lineCap: .round, lineJoin: .round) }
    }

    private static func drawHome(_ context: GraphicsContext, _ look: IconLook) {
        context.stroke(Shapes.house, with: .color(look.stroke), style: look.style)
        // Selected, the dot drops from under the roof on the spring; deselected, it rises back as it fades.
        let fall = look.active ? M.drop(look.t) : look.fill
        let y = look.still ? Metrics.homeDotY : Metrics.homeDotFrom + (Metrics.homeDotY - Metrics.homeDotFrom) * fall
        let radius = Metrics.dot / 2 * (look.still ? 1 : Metrics.dotSmall + (1 - Metrics.dotSmall) * look.fill)
        var dot = context
        dot.opacity = look.fill
        dot.fill(Shapes.circle(x: Metrics.icon / 2, y: y, radius: radius), with: .color(look.accent))
    }

    private static func drawPhotos(_ context: GraphicsContext, _ look: IconLook) {
        let icon = turned(context, by: look.still ? 0 : M.rock(look.t))
        icon.stroke(Shapes.frame, with: .color(look.stroke), style: look.style)
        icon.stroke(Shapes.hills, with: .color(look.stroke), style: look.style)
        drawMorph(icon, look, x: Metrics.photoDot.x, y: Metrics.photoDot.y, small: Metrics.photoDotRadius)
    }

    private static func drawSettings(_ context: GraphicsContext, _ look: IconLook) {
        let gear = turned(context, by: look.still ? 0 : M.spinDegrees * M.easeOut(look.t / M.spinFor))
        gear.stroke(Shapes.gear, with: .color(look.stroke), style: look.style)
        drawMorph(context, look, x: Metrics.icon / 2, y: Metrics.icon / 2, small: Metrics.gearRing)
    }

    /// An outlined circle of radius `small` that fills with the accent and grows into the dot.
    private static func drawMorph(_ context: GraphicsContext, _ look: IconLook, x: Double, y: Double, small: Double) {
        let grown = small + (Metrics.dot / 2 - small) * look.fill
        // Without motion the two only cross-fade, each at its own size.
        var ring = context
        ring.opacity = 1 - look.fill
        ring.stroke(Shapes.circle(x: x, y: y, radius: (look.still ? small : grown) - look.width / 2), with: .color(look.stroke), lineWidth: look.width)
        var dot = context
        dot.opacity = look.fill
        dot.fill(Shapes.circle(x: x, y: y, radius: look.still ? Metrics.dot / 2 : grown), with: .color(look.accent))
    }

    /// A copy of the context turned about the centre of the icon's box.
    private static func turned(_ context: GraphicsContext, by degrees: Double) -> GraphicsContext {
        var copy = context
        copy.translateBy(x: Metrics.icon / 2, y: Metrics.icon / 2)
        copy.rotate(by: .degrees(degrees))
        copy.translateBy(x: -Metrics.icon / 2, y: -Metrics.icon / 2)
        return copy
    }

    private static func mix(_ from: UIColor, _ to: UIColor, _ amount: Double) -> Color {
        var a: (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        var b: (CGFloat, CGFloat, CGFloat, CGFloat) = (0, 0, 0, 0)
        from.getRed(&a.0, green: &a.1, blue: &a.2, alpha: &a.3)
        to.getRed(&b.0, green: &b.1, blue: &b.2, alpha: &b.3)
        return Color(
            .sRGB,
            red: a.0 + (b.0 - a.0) * amount,
            green: a.1 + (b.1 - a.1) * amount,
            blue: a.2 + (b.2 - a.2) * amount,
            opacity: a.3 + (b.3 - a.3) * amount
        )
    }

    // MARK: The pages

    /// Sample pages, one per tab: grey placeholders in three different layouts.
    private func page(_ index: Int) -> some View {
        VStack(alignment: .leading, spacing: Metrics.titleGap) {
            Text(labels[index])
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(ink)
                .lineLimit(1)
                .frame(height: 41)
                .accessibilityAddTraits(.isHeader)

            switch index {
            case 0:
                VStack(spacing: 12) {
                    RoundedRectangle(cornerRadius: 24, style: .continuous).fill(cardColor).frame(height: 150)
                    RoundedRectangle(cornerRadius: 24, style: .continuous).fill(cardColor).frame(height: 96)
                }
            case 1:
                VStack(spacing: 13) {
                    ForEach(0..<3, id: \.self) { _ in
                        HStack(spacing: 13) {
                            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(cardColor).aspectRatio(1, contentMode: .fit)
                            RoundedRectangle(cornerRadius: 18, style: .continuous).fill(cardColor).aspectRatio(1, contentMode: .fit)
                        }
                    }
                }
            default:
                VStack(spacing: 8) {
                    ForEach(0..<5, id: \.self) { _ in
                        HStack(spacing: 12) {
                            Circle().fill(Palette.detail).frame(width: 24, height: 24)
                            Capsule().fill(Palette.detail).frame(width: 90, height: 10)
                            Spacer(minLength: 0)
                        }
                        .padding(.horizontal, 16)
                        .frame(height: 56)
                        .background(RoundedRectangle(cornerRadius: 16, style: .continuous).fill(cardColor))
                    }
                }
            }
        }
    }

    // MARK: Behaviour

    private func select(_ index: Int, byHand: Bool) {
        guard index != current else { return }
        // The loop that plays by itself is silent.
        if byHand { haptic.selectionChanged() }
        let now = Date.now
        var next = (0..<3).map(motion)
        next[current].move(selected: false, at: now)
        next[index].move(selected: true, at: now)
        motions = next
        selected = index
        moving = true
        moves += 1
        onSelect?(index)
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(until: clock.now + .seconds(M.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            select(initialTab, byHand: false)
        }
        // Every step is timed from the loop's origin, so a late wake does not push the next one back.
        var origin = clock.now
        while !Task.isCancelled {
            for step in M.steps {
                try? await clock.sleep(until: origin + .seconds(step.at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                select(step.tab, byHand: false)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

/// The timeline of the block, shared by its three stacks.
enum DotIconTabBarMotion {
    /// The period of the autoPlay loop, in seconds, and the tab it selects at each moment of it.
    static let loop = 9.0
    static let steps: [(at: Double, tab: Int)] = [(1.2, 1), (3.2, 2), (5.0, 1), (6.6, 0)]
    /// After a touch the loop waits this long, returns to the first tab and starts over.
    static let userHold = 3.0

    // Seconds. The stroke and the dot each ease towards their new state; a tab that is deselected
    // while it still moves turns round from where it is.
    static let strokeOn = 0.15
    static let strokeOff = 0.25
    static let fillOn = 0.3
    static let fillOff = 0.25
    static let content = 0.3
    /// How long the clock of a selected icon runs: the longest of the three effects, settled.
    static let motionFor = 1.0
    /// The house's dot drops on a spring that overshoots once: mass 1, stiffness 400, damping 14.
    static let dropStiffness = 400.0
    static let dropDamping = 14.0
    /// The picture rocks: a swing of this period that is over after `rockFor`, about -10°, +6°, -2°.
    static let rockDegrees = 12.0
    static let rockPeriod = 0.36
    static let rockFor = 0.6
    static let rockFalloff = 1.17
    /// The gear turns one lobe on, which maps it onto itself.
    static let spinDegrees = 60.0
    static let spinFor = 0.4

    static func easeOut(_ x: Double) -> Double {
        let rest = 1 - min(1, max(0, x))
        return 1 - rest * rest * rest
    }

    /// Where a spring released at 0 towards 1 is after `t` seconds.
    static func drop(_ t: Double) -> Double {
        if t >= motionFor { return 1 }
        let omega = dropStiffness.squareRoot()
        let zeta = dropDamping / (2 * omega)
        let damped = omega * (1 - zeta * zeta).squareRoot()
        return 1 - exp(-zeta * omega * t) * (cos(damped * t) + zeta * omega / damped * sin(damped * t))
    }

    /// The picture's angle `t` seconds after it was selected, in degrees.
    static func rock(_ t: Double) -> Double {
        if t >= rockFor { return 0 }
        return -rockDegrees * sin(2 * .pi * t / rockPeriod) * pow(1 - t / rockFor, rockFalloff)
    }
}

private typealias M = DotIconTabBarMotion

/// A number easing out towards a target, sampled against the timeline's date.
private struct Ramp {
    private var from: Double
    private var to: Double
    private var start = Date.distantPast
    private var duration = 0.0

    init(_ value: Double) {
        from = value
        to = value
    }

    func value(at date: Date) -> Double {
        guard duration > 0 else { return to }
        return from + (to - from) * M.easeOut(date.timeIntervalSince(start) / duration)
    }

    mutating func retarget(_ target: Double, at date: Date, duration: Double) {
        from = value(at: date)
        to = target
        start = date
        self.duration = duration
    }
}

/// What moves one tab.
private struct TabMotion {
    var active: Bool
    /// The stroke, from thin and grey (0) to thick and dark (1).
    var on: Ramp
    /// The dot, from absent (0) to full (1).
    var fill: Ramp
    /// The tab's page, from hidden (0) to shown (1).
    var shown: Ramp
    private var selectedAt = Date.distantPast

    init(selected: Bool) {
        active = selected
        on = Ramp(selected ? 1 : 0)
        fill = Ramp(selected ? 1 : 0)
        shown = Ramp(selected ? 1 : 0)
    }

    mutating func move(selected: Bool, at now: Date) {
        active = selected
        on.retarget(selected ? 1 : 0, at: now, duration: selected ? M.strokeOn : M.strokeOff)
        fill.retarget(selected ? 1 : 0, at: now, duration: selected ? M.fillOn : M.fillOff)
        shown.retarget(selected ? 1 : 0, at: now, duration: M.content)
        if selected { selectedAt = now }
    }

    /// Seconds since the tab was last selected, up to `motionFor`.
    func t(at now: Date) -> Double {
        min(M.motionFor, max(0, now.timeIntervalSince(selectedAt)))
    }
}

private enum Palette {
    /// The circle and the line on a settings row.
    static let detail = Color(hex: 0xCFCFCF)
}

private enum Metrics {
    static let margin = 24.0
    /// The title's top edge below the status bar, and the gap under it.
    static let titleTop = 22.0
    static let titleGap = 20.0
    static let contentScale = 0.97
    static let barWidth = 262.0
    static let barHeight = 88.0
    /// The bar's bottom edge above the bottom of the screen, and at least this far above the home indicator.
    static let barBottom = 40.0
    static let barClearance = 6.0
    /// Each icon is drawn in a 40 point box; these are the centres from the bar's left edge.
    static let icon = 40.0
    static let iconX = [52.0, 131.0, 210.0]
    static let tapWidth = 79.0

    static let stroke = 2.0
    static let strokeActive = 3.0
    static let dot = 14.0
    /// The house's dot starts at this share of its size, under the roof, and lands in the lower middle.
    static let dotSmall = 0.4
    static let homeDotFrom = 14.0
    static let homeDotY = 24.0
    /// The picture's small circle (8 points) and the gear's ring (10 points) grow into the dot.
    static let photoDot = CGPoint(x: 25, y: 16.5)
    static let photoDotRadius = 4.0
    static let gearRing = 5.0
}

/// The icons' outlines, in the 40 point box.
private enum Shapes {
    /// The house: a roof peak, two shoulders and a flat bottom.
    static let house = rounded(
        [CGPoint(x: 20, y: 4), CGPoint(x: 35.5, y: 16.5), CGPoint(x: 35.5, y: 36), CGPoint(x: 4.5, y: 36), CGPoint(x: 4.5, y: 16.5)],
        cuts: [3.5, 5, 7, 7, 5]
    )

    /// The picture: a rounded square, and two hills that start on its left edge and on the first hill.
    static let frame = Path(roundedRect: CGRect(x: 4.5, y: 4.5, width: 31, height: 31), cornerRadius: 11, style: .circular)
    static let hills: Path = {
        var path = Path()
        path.move(to: CGPoint(x: 4.5, y: 21))
        path.addQuadCurve(to: CGPoint(x: 23.5, y: 35.5), control: CGPoint(x: 19, y: 21))
        path.move(to: CGPoint(x: 19.9, y: 28.1))
        path.addQuadCurve(to: CGPoint(x: 35.5, y: 25.5), control: CGPoint(x: 27, y: 24.5))
        return path
    }()

    /// The gear: a circle whose radius swells six times on the way round, with a lobe at the top.
    static let gear: Path = {
        let radius = 15.0, wave = 1.6, lobes = 6.0, steps = 120
        var path = Path()
        path.addLines((0..<steps).map { step in
            let angle = Double(step) / Double(steps) * 2 * .pi
            let r = radius + wave * cos(lobes * angle)
            return CGPoint(x: Metrics.icon / 2 + r * sin(angle), y: Metrics.icon / 2 - r * cos(angle))
        })
        path.closeSubpath()
        return path
    }()

    static func circle(x: Double, y: Double, radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }

    /// A closed outline through `points` whose corners are rounded off `cuts` points before and after each.
    static func rounded(_ points: [CGPoint], cuts: [Double]) -> Path {
        func toward(_ from: CGPoint, _ to: CGPoint, _ distance: Double) -> CGPoint {
            let length = hypot(to.x - from.x, to.y - from.y)
            return CGPoint(x: from.x + (to.x - from.x) * distance / length, y: from.y + (to.y - from.y) * distance / length)
        }
        var path = Path()
        for (index, point) in points.enumerated() {
            let before = toward(point, points[(index + points.count - 1) % points.count], cuts[index])
            let after = toward(point, points[(index + 1) % points.count], cuts[index])
            if index == 0 { path.move(to: before) } else { path.addLine(to: before) }
            path.addQuadCurve(to: after, control: point)
        }
        path.closeSubpath()
        return path
    }
}

#Preview {
    DotIconTabBar(autoPlay: true)
}
