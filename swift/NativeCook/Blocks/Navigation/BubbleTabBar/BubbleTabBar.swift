import SwiftUI

enum BubbleTabBarIcon {
    case profile, saved, add, search, more

    // Icons in a 24 × 24 box: stroked paths (M, L, H, V, C and Z only) and stroked circles.
    fileprivate var d: String {
        switch self {
        case .profile: "M4.5 20.5C4.5 16.6 7.9 14 12 14C16.1 14 19.5 16.6 19.5 20.5"
        case .saved: "M6.5 4.5C6.5 3.95 6.95 3.5 7.5 3.5H16.5C17.05 3.5 17.5 3.95 17.5 4.5V20.5L12 16.5L6.5 20.5Z"
        case .add: "M12 4.5V19.5M4.5 12H19.5"
        case .search: "M15.4 15.4L20 20"
        case .more: "M4.5 7H19.5M4.5 12H19.5M4.5 17H19.5"
        }
    }

    fileprivate var circles: [(Double, Double, Double)] {
        switch self {
        case .profile: [(12, 8, 4)]
        case .search: [(10.8, 10.8, 6.3)]
        default: []
        }
    }

    /// The icon's outline: its path and its circles.
    fileprivate var path: Path {
        var tokens: [String] = []
        var token = ""
        for c in d {
            if c.isLetter || c == " " {
                if !token.isEmpty { tokens.append(token) }
                token = ""
                if c.isLetter { tokens.append(String(c)) }
            } else {
                token.append(c)
            }
        }
        if !token.isEmpty { tokens.append(token) }
        var path = Path()
        var point = CGPoint.zero
        var i = 0
        func next() -> Double {
            defer { i += 1 }
            return Double(tokens[i]) ?? 0
        }
        while i < tokens.count {
            let command = tokens[i]
            i += 1
            switch command {
            case "M": point = CGPoint(x: next(), y: next()); path.move(to: point)
            case "L": point = CGPoint(x: next(), y: next()); path.addLine(to: point)
            case "H": point.x = next(); path.addLine(to: point)
            case "V": point.y = next(); path.addLine(to: point)
            case "C":
                let c1 = CGPoint(x: next(), y: next())
                let c2 = CGPoint(x: next(), y: next())
                point = CGPoint(x: next(), y: next())
                path.addCurve(to: point, control1: c1, control2: c2)
            default: path.closeSubpath()
            }
        }
        for (x, y, r) in circles { path.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2)) }
        return path
    }
}

struct BubbleTabBarTab {
    var label: String
    var icon: BubbleTabBarIcon
    /// The page's heading and the line under it.
    var title: String
    var caption: String

    static let samples = [
        BubbleTabBarTab(label: "Profile", icon: .profile, title: "Profile", caption: "Your account and activity"),
        BubbleTabBarTab(label: "Saved", icon: .saved, title: "Saved", caption: "Things you kept for later"),
        BubbleTabBarTab(label: "Add", icon: .add, title: "Create", caption: "Start something new"),
        BubbleTabBarTab(label: "Search", icon: .search, title: "Search", caption: "Find people and posts"),
        BubbleTabBarTab(label: "More", icon: .more, title: "More", caption: "Settings, help and about"),
    ]
}

/// A damped spring (mass 1) sampled against the timeline's date, starting from a value and a speed.
private struct BubbleTabBarSpring {
    var stiffness: Double
    var damping: Double
    var from: Double
    var velocity: Double = 0
    var to: Double
    var start = Date.distantPast

    private var omega: Double { stiffness.squareRoot() }
    private var zeta: Double { damping / (2 * omega) }
    /// Within a ten-thousandth of the distance after this long.
    var settle: Double { 9.2 / (zeta * omega) }

    func value(at date: Date) -> Double {
        let t = max(0, date.timeIntervalSince(start))
        guard t < settle else { return to }
        let wd = omega * (1 - zeta * zeta).squareRoot()
        let a = from - to
        let b = (velocity + zeta * omega * a) / wd
        return to + exp(-zeta * omega * t) * (a * cos(wd * t) + b * sin(wd * t))
    }

    func speed(at date: Date) -> Double {
        (value(at: date + 0.004) - value(at: date - 0.004)) / 0.008
    }

    /// Springs to `target` from where it is at `date` (or `start`, when later), keeping its speed.
    mutating func retarget(_ target: Double, at date: Date, start: Date, stiffness: Double, damping: Double, still: Bool) {
        let v = still ? 0 : speed(at: date)
        from = still ? target : value(at: date)
        velocity = v
        to = target
        self.start = start
        self.stiffness = stiffness
        self.damping = damping
    }
}

/// A white tab bar on a dark screen whose active tab rises out of the bar as a round bubble sitting
/// in a bite cut from the bar's edge. On a change the bite closes up, the bubble glides to the new
/// tab with a little overshoot and the bite opens under it again, 40 ms behind, like a liquid.
struct BubbleTabBar: View {
    var tabs = BubbleTabBarTab.samples
    var initialIndex = 0
    /// Hops through every tab by itself every `loop` seconds, then glides back to the first.
    var autoPlay = false
    var onChange: ((Int, BubbleTabBarTab) -> Void)? = nil

    private static let screen = Color(hex: 0x202020)
    private static let bar = Color.white
    private static let ink = Color(hex: 0x111111)
    private static let mute = Color(hex: 0x8a8a8a)
    private static let row = Color(hex: 0x2a2a2a)
    private static let rowMark = Color(hex: 0x353535)

    // The bar, in points. Its top edge is y 0; the bubble rises above it.
    private static let barHeight = 60.0
    private static let side = 22.0
    private static let corner = 16.0
    private static let icon = 24.0
    private static let iconY = 22.0
    private static let labelY = 42.0
    private static let bubbleR = 28.0
    // The bite the bubble sits in: 4 points wider than the bubble, with rounded shoulders.
    private static let notchR = 32.0
    private static let fillet = 8.0
    /// Where the bubble's and the bite's centres rest, below the bar's top edge.
    private static let restY = 6.0
    // While the bubble travels the bite closes up (its centre rises this much) and the bubble lifts.
    private static let flatLift = 32.0
    private static let hopRise = 4.0
    /// How far from a tab the bubble must be before its icon starts to leave.
    private static let reach = 0.6
    /// Room above the bar for the bubble.
    private static let room = 40.0
    // Polyline steps for each arc of the outline.
    private static let cornerSteps = 8
    private static let filletSteps = 8
    private static let notchSteps = 28

    // Motion. A hop is a spring of response 0.45 s at damping ratio 0.7; a hop of more than one tab
    // uses response 0.7 s. The bite follows the bubble 40 ms late, and closes over `hopClose`.
    private static let hop = (stiffness: 194.96, damping: 19.55)
    private static let longHop = (stiffness: 80.57, damping: 12.57)
    private static let notchDelay = 0.04
    private static let hopClose = 0.2
    private static let pageFade = 0.25

    // The autoPlay loop: a hop every 1.6 s through the tabs, then one long glide back to the first.
    static let loop = 9.0
    private static let script: [(Double, Int)] = [(0.9, 1), (2.5, 2), (4.1, 3), (5.7, 4), (7.3, 0)]
    private static let userHold = 3.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var index: Int? = nil
    @State private var touches = 0
    @State private var bubble = BubbleTabBarSpring(stiffness: 1, damping: 1, from: 0, to: 0)
    @State private var notch = BubbleTabBarSpring(stiffness: 1, damping: 1, from: 0, to: 0)
    @State private var hopStart = Date.distantPast
    @State private var width = 0.0
    /// The timeline runs until then; afterwards the screen is still and it pauses.
    @State private var motionUntil = Date.distantPast
    @State private var idle = false
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private var current: Int { index ?? initialIndex }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let barTotal = Self.barHeight + insets.bottom

            ZStack(alignment: .bottom) {
                Self.screen
                page
                    .padding(.top, insets.top + 24)
                    .padding(.horizontal, 24)
                    .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)

                TimelineView(.animation(paused: Date() > motionUntil)) { timeline in
                    barCanvas(width: width, height: barTotal, now: timeline.date)
                }
                .frame(width: width, height: Self.room + barTotal)
                .allowsHitTesting(false)

                tabButtons(width: width)
                    .frame(width: width, height: Self.room + barTotal, alignment: .topLeading)
            }
            .ignoresSafeArea()
            .onChange(of: width, initial: true) { place(width: width) }
        }
        .preferredColorScheme(.dark)
        .onAppear { selectHaptic.prepare() }
        .task(id: motionUntil) {
            // Wakes the view once the last motion has ended, so the timeline pauses.
            let wait = motionUntil.timeIntervalSinceNow
            guard wait > 0 else { return }
            try? await Task.sleep(for: .seconds(wait + 0.05))
            if !Task.isCancelled { idle.toggle() }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await runLoop() }
    }

    // MARK: The page

    private var page: some View {
        VStack(alignment: .leading, spacing: 0) {
            ZStack(alignment: .topLeading) {
                ForEach(tabs.indices, id: \.self) { i in
                    VStack(alignment: .leading, spacing: 6) {
                        Text(tabs[i].title)
                            .font(.system(size: 34, weight: .bold))
                            .tracking(-0.6)
                            .foregroundStyle(.white)
                            .frame(height: 41)
                        Text(tabs[i].caption)
                            .font(.system(size: 15))
                            .foregroundStyle(Self.mute)
                            .frame(height: 20)
                    }
                    .lineLimit(1)
                    .opacity(i == current ? 1 : 0)
                    .animation(.timingCurve(0.25, 0.1, 0.25, 1, duration: Self.pageFade), value: i == current)
                }
            }
            .frame(height: 76, alignment: .topLeading)
            .padding(.bottom, 20)
            ForEach([0.72, 0.56, 0.64], id: \.self) { share in
                HStack(spacing: 14) {
                    Circle().fill(Self.rowMark).frame(width: 40, height: 40)
                    GeometryReader { box in
                        VStack(alignment: .leading, spacing: 8) {
                            Capsule().fill(Self.rowMark).frame(width: box.size.width * share, height: 10)
                            Capsule().fill(Self.rowMark).frame(width: box.size.width * share * 0.6, height: 8)
                        }
                        .frame(maxHeight: .infinity)
                    }
                }
                .padding(.horizontal, 16)
                .frame(height: 72)
                .background(RoundedRectangle(cornerRadius: 20, style: .continuous).fill(Self.row))
                .padding(.bottom, 12)
            }
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(tabs[current].title)
    }

    // MARK: The bar

    private func tabX(_ i: Int, width: Double) -> Double {
        Self.side + (width - Self.side * 2) * (Double(i) + 0.5) / Double(tabs.count)
    }

    private func barCanvas(width: Double, height: Double, now: Date) -> some View {
        let pitch = (width - Self.side * 2) / Double(tabs.count)
        let bx = bubble.value(at: now)
        let target = bubble.to
        let hop = reduceMotion ? 1 : min(1, max(0, now.timeIntervalSince(hopStart) / Self.hopClose))
        let hopEased = 1 - (1 - hop) * (1 - hop)
        // How much the bite is closed: as soon as a hop starts, and opening again as the bubble arrives.
        let flat = min(hopEased, min(1, max(0, abs(bx - target) / (pitch * Self.reach))))
        let lift = Self.restY - Self.hopRise * flat
        // The bite stays clear of the bar's rounded corners even when the bubble overshoots.
        let cx = min(tabX(tabs.count - 1, width: width) + 1.5, max(tabX(0, width: width) - 1.5, notch.value(at: now)))
        let outline = Self.outline(width: width, height: height, top: Self.room, cx: cx, cy: Self.restY - Self.flatLift * flat)

        return Canvas { context, _ in
            context.fill(outline, with: .color(Self.bar))
            for (i, tab) in tabs.enumerated() {
                let near = min(1, max(0, 1 - abs(bx - tabX(i, width: width)) / (pitch * Self.reach)))
                mark(tab.icon, in: context, x: tabX(i, width: width), y: Self.room + Self.iconY, scale: 0.8 + 0.2 * (1 - near), opacity: 1 - near, line: 1.8)
            }
            context.fill(Path(ellipseIn: CGRect(x: bx - Self.bubbleR, y: Self.room + lift - Self.bubbleR, width: Self.bubbleR * 2, height: Self.bubbleR * 2)), with: .color(Self.bar))
            for (i, tab) in tabs.enumerated() {
                let near = min(1, max(0, 1 - abs(bx - tabX(i, width: width)) / (pitch * Self.reach)))
                mark(tab.icon, in: context, x: bx, y: Self.room + lift, scale: 1.1 - 0.1 * near, opacity: near, line: 2)
            }
        }
    }

    private func mark(_ icon: BubbleTabBarIcon, in context: GraphicsContext, x: Double, y: Double, scale: Double, opacity: Double, line: Double) {
        guard opacity > 0.001 else { return }
        var layer = context
        layer.opacity = opacity
        layer.translateBy(x: x, y: y)
        layer.scaleBy(x: scale, y: scale)
        layer.translateBy(x: -Self.icon / 2, y: -Self.icon / 2)
        layer.stroke(icon.path, with: .color(Self.ink), style: StrokeStyle(lineWidth: line, lineCap: .round, lineJoin: .round))
    }

    /// The bar with a round bite centred at (cx, top + cy), its shoulders rounded by `fillet`.
    private static func outline(width: Double, height: Double, top: Double, cx: Double, cy: Double) -> Path {
        var path = Path()
        func arc(_ x: Double, _ y: Double, _ r: Double, _ from: Double, _ to: Double, _ steps: Int) {
            for k in 0...steps {
                let a = from + (to - from) * Double(k) / Double(steps)
                path.addLine(to: CGPoint(x: x + r * cos(a), y: y + r * sin(a)))
            }
        }
        let d = (pow(notchR + fillet, 2) - pow(fillet - cy, 2)).squareRoot()
        path.move(to: CGPoint(x: 0, y: top + height))
        path.addLine(to: CGPoint(x: 0, y: top + corner))
        arc(corner, top + corner, corner, .pi, 1.5 * .pi, cornerSteps)
        arc(cx - d, top + fillet, fillet, -0.5 * .pi, atan2(cy - fillet, d), filletSteps)
        arc(cx, top + cy, notchR, atan2(fillet - cy, -d), atan2(fillet - cy, d), notchSteps)
        arc(cx + d, top + fillet, fillet, atan2(cy - fillet, -d), -0.5 * .pi, filletSteps)
        arc(width - corner, top + corner, corner, 1.5 * .pi, 2 * .pi, cornerSteps)
        path.addLine(to: CGPoint(x: width, y: top + height))
        path.closeSubpath()
        return path
    }

    private func tabButtons(width: Double) -> some View {
        let pitch = (width - Self.side * 2) / Double(tabs.count)
        return ZStack(alignment: .topLeading) {
            ForEach(tabs.indices, id: \.self) { i in
                Button {
                    touches += 1
                    select(i, auto: false)
                } label: {
                    Text(tabs[i].label)
                        .font(.system(size: 11, weight: .medium))
                        .foregroundStyle(Self.ink)
                        .lineLimit(1)
                        .frame(height: 13)
                        .padding(.top, 24 + Self.labelY)
                        .frame(width: pitch, height: Self.barHeight + 24, alignment: .top)
                        .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(i == current ? [.isSelected, .isButton] : .isButton)
                .offset(x: tabX(i, width: width) - pitch / 2, y: Self.room - 24)
            }
        }
    }

    // MARK: Behaviour

    /// Puts the bubble on its tab at once when the width is first known or changes.
    private func place(width: Double) {
        self.width = width
        guard width > 0, !tabs.isEmpty else { return }
        let x = tabX(current, width: width)
        bubble = BubbleTabBarSpring(stiffness: Self.hop.stiffness, damping: Self.hop.damping, from: x, to: x)
        notch = bubble
    }

    private func select(_ next: Int, auto: Bool) {
        guard width > 0 else { return }
        let x = tabX(next, width: width)
        if next == current, abs(bubble.value(at: .now) - x) < 0.5 { return }
        let now = Date.now
        let spring = abs(next - current) > 1 ? Self.longHop : Self.hop
        bubble.retarget(x, at: now, start: now, stiffness: spring.stiffness, damping: spring.damping, still: reduceMotion)
        notch.retarget(x, at: now, start: now + (reduceMotion ? 0 : Self.notchDelay), stiffness: spring.stiffness, damping: spring.damping, still: reduceMotion)
        hopStart = now
        index = next
        let until = now + Self.notchDelay + bubble.settle
        if until > motionUntil { motionUntil = until }
        if !auto { selectHaptic.selectionChanged() }
        onChange?(next, tabs[next])
    }

    private func runLoop() async {
        guard autoPlay, !tabs.isEmpty else { return }
        let clock = ContinuousClock()
        var origin = clock.now
        if touches > 0 {
            try? await clock.sleep(until: origin + .seconds(Self.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            origin = clock.now
            if current != 0 {
                // Back to the first tab the way the loop ends, then the loop starts over from there.
                select(0, auto: true)
                origin += .seconds(Self.loop - Self.script[Self.script.count - 1].0)
            }
        }
        while !Task.isCancelled {
            for (time, tab) in Self.script {
                try? await clock.sleep(until: origin + .seconds(time), tolerance: .zero)
                guard !Task.isCancelled else { return }
                select(tab % tabs.count, auto: true)
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}
