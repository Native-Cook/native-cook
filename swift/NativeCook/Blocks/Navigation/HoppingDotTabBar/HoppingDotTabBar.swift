import SwiftUI

/// An icon in a 24 × 24 box, from SVG path data made of M, L, C and Z: its outline, and the lines inside it.
struct HoppingDotTabBarIcon {
    let outline: Path
    let detail: Path

    init(outline: String, detail: String) {
        self.outline = Self.path(outline)
        self.detail = Self.path(detail)
    }

    static let home = HoppingDotTabBarIcon(
        outline: "M4.5 11L12 4.5L19.5 11L19.5 19.5L4.5 19.5Z",
        detail: "M10 19.5L10 16C10 14.9 10.9 14 12 14C13.1 14 14 14.9 14 16L14 19.5"
    )
    static let messages = HoppingDotTabBarIcon(
        outline: "M5 19L5 11.5C5 7.36 8.36 4 12.5 4C16.64 4 20 7.36 20 11.5C20 15.64 16.64 19 12.5 19Z",
        detail: "M9.5 11.5L9.51 11.5M12.5 11.5L12.51 11.5M15.5 11.5L15.51 11.5"
    )
    static let saved = HoppingDotTabBarIcon(
        outline: "M12 19.5C12 19.5 4 15 4 9.5C4 7 6 5 8.3 5C9.9 5 11.3 5.9 12 7.2C12.7 5.9 14.1 5 15.7 5C18 5 20 7 20 9.5C20 15 12 19.5 12 19.5Z",
        detail: "M7 9.6C7 8.6 7.7 7.8 8.6 7.8"
    )
    static let profile = HoppingDotTabBarIcon(
        outline: "M12 4C13.93 4 15.5 5.57 15.5 7.5C15.5 9.43 13.93 11 12 11C10.07 11 8.5 9.43 8.5 7.5C8.5 5.57 10.07 4 12 4Z"
            + "M8.5 14.5L15.5 14.5C16.88 14.5 18 15.62 18 17C18 18.38 16.88 19.5 15.5 19.5L8.5 19.5C7.12 19.5 6 18.38 6 17C6 15.62 7.12 14.5 8.5 14.5Z",
        detail: "M10.6 8.1C11 9 13 9 13.4 8.1"
    )

    /// Reads the four commands the icons are written in.
    private static func path(_ data: String) -> Path {
        var path = Path()
        var command: Character = "M"
        var numbers: [Double] = []
        var digits = ""

        func flush() {
            if let value = Double(digits) { numbers.append(value) }
            digits = ""
            let needed = command == "C" ? 6 : 2
            guard numbers.count == needed else { return }
            switch command {
            case "M": path.move(to: CGPoint(x: numbers[0], y: numbers[1]))
            case "L": path.addLine(to: CGPoint(x: numbers[0], y: numbers[1]))
            default:
                path.addCurve(
                    to: CGPoint(x: numbers[4], y: numbers[5]),
                    control1: CGPoint(x: numbers[0], y: numbers[1]),
                    control2: CGPoint(x: numbers[2], y: numbers[3])
                )
            }
            numbers = []
        }

        for character in data {
            if character.isLetter {
                flush()
                if character == "Z" { path.closeSubpath() } else { command = character }
            } else if character == " " {
                flush()
            } else {
                digits.append(character)
            }
        }
        flush()
        return path
    }
}

struct HoppingDotTabBarTab {
    var title: String
    var icon: HoppingDotTabBarIcon
    /// The heights of the placeholder cards on the tab's page.
    var cards: [Double]
    /// A round picture with two lines above the cards.
    var avatar = false

    static let samples = [
        HoppingDotTabBarTab(title: "Home", icon: .home, cards: [150, 96, 96]),
        HoppingDotTabBarTab(title: "Messages", icon: .messages, cards: [72, 72, 72, 72]),
        HoppingDotTabBarTab(title: "Saved", icon: .saved, cards: [96, 150, 96]),
        HoppingDotTabBarTab(title: "Profile", icon: .profile, cards: [96, 96], avatar: true),
    ]
}

/// A tab bar whose indicator is a white dot resting in a notch of the bar's top edge. Choosing a
/// tab makes the dot hop there on a parabola, higher the further it goes, while the notch it left
/// closes and a new one opens under it as it lands. The page above changes with it.
struct HoppingDotTabBar: View {
    var tabs = HoppingDotTabBarTab.samples
    /// The tab that is open at first, from 0.
    var initialTab = 0
    /// The screen, the lighter page on it, the bar, and the icons when idle and when chosen.
    var field = Color(hex: 0x244ff6)
    var panel = Color(hex: 0x2e59fe)
    var bar = Color.white
    var idle = Color(hex: 0xb4beef)
    var active = Color(hex: 0x3b55e6)
    /// Goes round the tabs by itself every `M.loop` seconds.
    var autoPlay = false
    /// Called with the tab a tap chose.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The open tab; nil until one is chosen.
    @State private var chosen: Int? = nil
    /// The hop in progress or the last one, and when it started; nil while the first tab rests.
    @State private var hop: Hop? = nil
    @State private var start = Date.distantPast
    /// The timeline runs while a hop does.
    @State private var busy = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    private var first: Int { min(max(initialTab, 0), tabs.count - 1) }
    private var index: Int { chosen ?? first }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let barWidth = width - Metrics.side * 2
            let barTop = height - max(insets.bottom, 20) - Metrics.barHeight
            let tabWidth = barWidth / Double(tabs.count)

            TimelineView(.animation(paused: !busy)) { timeline in
                let now = Self.sample(hop ?? .resting(first, tabs.count), timeline.date.timeIntervalSince(start), reduced: reduceMotion)

                ZStack(alignment: .topLeading) {
                    field

                    panel
                        .frame(width: barWidth, height: barTop + Metrics.panelUnder)
                        .offset(x: Metrics.side)

                    ForEach(Array(tabs.enumerated()), id: \.offset) { k, tab in
                        page(tab, width: barWidth - Metrics.inset * 2)
                            .opacity(now.page == k ? now.presence : 0)
                            .offset(x: Metrics.side + Metrics.inset + (now.page == k ? now.shift : 0), y: insets.top + Metrics.titleTop)
                            .accessibilityHidden(k != index)
                    }

                    Canvas { context, _ in
                        context.fill(Self.barPath(width: barWidth, depth: now.depth), with: .color(bar))
                        if now.cover > 0 {
                            context.fill(Self.notchPath(at: tabWidth * (Double(now.page) + 0.5)), with: .color(bar.opacity(now.cover)))
                        }
                        for (k, tab) in tabs.enumerated() {
                            var layer = context
                            layer.translateBy(
                                x: tabWidth * (Double(k) + 0.5) - Metrics.icon / 2,
                                y: Metrics.iconY - Metrics.icon / 2 - Metrics.iconLift * now.tint[k]
                            )
                            Self.draw(tab.icon, in: layer, color: idle)
                            // The chosen colour fades in over the idle one.
                            layer.opacity = now.tint[k]
                            Self.draw(tab.icon, in: layer, color: active)
                        }
                    }
                    .frame(width: barWidth, height: Metrics.barHeight)
                    .offset(x: Metrics.side, y: barTop)
                    .allowsHitTesting(false)

                    HStack(spacing: 0) {
                        ForEach(Array(tabs.enumerated()), id: \.offset) { k, tab in
                            Button {
                                touches += 1
                                select(k, byHand: true)
                            } label: {
                                Color.clear.contentShape(Rectangle())
                            }
                            .buttonStyle(.plain)
                            .accessibilityLabel(tab.title)
                            .accessibilityAddTraits(k == index ? .isSelected : [])
                        }
                    }
                    .frame(width: barWidth, height: Metrics.barHeight)
                    .offset(x: Metrics.side, y: barTop)

                    // The dot flies over the page, so it is the top layer.
                    Circle()
                        .fill(bar)
                        .frame(width: Metrics.dot, height: Metrics.dot)
                        .opacity(now.dotAlpha)
                        .offset(
                            x: Metrics.side + tabWidth * (now.dotX + 0.5) - Metrics.dot / 2,
                            y: barTop + Metrics.dotSink - Metrics.dot / 2 - now.dotUp
                        )
                        .allowsHitTesting(false)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
            }
            // The reader starts inside the safe area; the screen is laid out from its real corner.
            .offset(x: -insets.leading, y: -insets.top)
        }
        .preferredColorScheme(.dark)
        .onAppear { haptic.prepare() }
        .task(id: start) {
            try? await Task.sleep(for: .seconds(M.rest))
            if !Task.isCancelled { busy = false }
        }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The page

    private func page(_ tab: HoppingDotTabBarTab, width: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(tab.title)
                .font(.system(size: 34, weight: .bold))
                .foregroundStyle(.white)
                .lineLimit(1)
                .frame(height: 41)
                .padding(.bottom, 12)
                .accessibilityAddTraits(.isHeader)
            if tab.avatar {
                HStack(spacing: 16) {
                    Circle()
                        .fill(Palette.card)
                        .frame(width: Metrics.avatar, height: Metrics.avatar)
                    lines
                }
                .padding(.top, 12)
            }
            ForEach(Array(tab.cards.enumerated()), id: \.offset) { _, cardHeight in
                RoundedRectangle(cornerRadius: 20, style: .continuous)
                    .fill(Palette.card)
                    .frame(height: cardHeight)
                    .overlay(alignment: .topLeading) { lines.padding(20) }
                    .padding(.top, 12)
            }
        }
        .frame(width: max(0, width), alignment: .leading)
        .allowsHitTesting(false)
    }

    private var lines: some View {
        VStack(alignment: .leading, spacing: 12) {
            RoundedRectangle(cornerRadius: 5).fill(Palette.line).frame(width: 140, height: 10)
            RoundedRectangle(cornerRadius: 5).fill(Palette.line).frame(width: 90, height: 10)
        }
    }

    // MARK: Behaviour

    /// Opens a tab: the same path for a tap and for the loop, which is silent.
    private func select(_ target: Int, byHand: Bool) {
        guard target != index else { return }
        if byHand { haptic.selectionChanged() }
        let moment = Date.now
        let now = Self.sample(hop ?? .resting(first, tabs.count), moment.timeIntervalSince(start), reduced: reduceMotion)
        // Everything that was moving is picked up where it was.
        hop = Hop(target: target, from: now.page, x: now.dotX, up: now.dotUp, depth: now.depth, tint: now.tint, presence: now.presence, shift: now.shift)
        start = moment
        chosen = target
        busy = true
        if byHand { onSelect?(target) }
    }

    private func loop() async {
        guard autoPlay else { return }
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // The loop starts over from the tab it was given.
            select(first, byHand: false)
            try? await Task.sleep(for: .seconds(M.settle))
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            for k in 0..<tabs.count {
                try? await clock.sleep(until: origin + .seconds(M.hopAt + Double(k) * M.hopEvery))
                guard !Task.isCancelled else { return }
                select((index + 1) % tabs.count, byHand: false)
            }
            origin += .seconds(M.loop)
            try? await clock.sleep(until: origin)
        }
    }

    // MARK: One frame

    /// Everything that moves, `t` seconds into a hop.
    private static func sample(_ hop: Hop, _ t: Double, reduced: Bool) -> Sample {
        let count = hop.depth.count
        if reduced {
            let arrived = t >= M.fade
            let shown = arrived ? hop.target : hop.from
            let visible = arrived ? M.clamp((t - M.fade) / M.fade) : hop.presence * (1 - M.clamp(t / M.fade))
            return Sample(
                depth: (0..<count).map { $0 == shown ? 1 : 0 }, tint: (0..<count).map { $0 == shown ? visible : 0 },
                dotX: Double(shown), dotUp: 0, dotAlpha: visible, page: shown, presence: visible, shift: 0, cover: 1 - visible
            )
        }

        let target = Double(hop.target)
        let distance = abs(target - hop.x)
        let air = min(M.air + M.airPerTab * distance, M.airMax)
        let arc = min(max(M.arc + M.arcPerTab * (distance - 1), M.arcMin), M.arcMax)
        let flown = M.clamp(t / air)
        let landing = t - (air - M.openBefore)
        var depth: [Double] = []
        var tint: [Double] = []
        for i in 0..<count {
            let closing = hop.depth[i] * (1 - M.clamp(t / M.close) * M.clamp(t / M.close))
            let fading = hop.tint[i] * (1 - M.clamp(t / M.tint))
            depth.append(i == hop.target ? max(closing, landing > 0 ? M.pressed(landing / M.open) : 0) : closing)
            tint.append(i == hop.target ? max(fading, M.clamp(landing / M.tint)) : fading)
        }
        let bounce = M.clamp((t - air) / M.bounceFor)
        let side: Double = target > hop.x ? 1 : target < hop.x ? -1 : 0
        let gone = M.clamp(t / M.pageOut)
        let come = M.smooth(landing / M.pageIn)
        return Sample(
            depth: depth, tint: tint,
            dotX: hop.x + (target - hop.x) * M.clamp((t - M.lift) / (air - M.lift)),
            // Gravity: a parabola from where the dot was to its rest, then one small bounce.
            dotUp: t < air ? hop.up * (1 - flown) + 4 * arc * flown * (1 - flown) : 4 * M.bounce * bounce * (1 - bounce),
            dotAlpha: 1,
            page: landing < 0 ? hop.from : hop.target,
            presence: landing < 0 ? hop.presence * (1 - gone) : come,
            shift: landing < 0 ? hop.shift + (-side * M.pageSlide - hop.shift) * gone : side * M.pageSlide * (1 - come),
            cover: 0
        )
    }

    // MARK: Drawing

    /// The bar's outline in its own box: a notch in the top edge over each tab that has one.
    private static func barPath(width: Double, depth: [Double]) -> Path {
        // A quarter circle as one curve.
        let k = 0.5523
        let top = Metrics.topRadius
        let bottom = Metrics.bottomRadius
        let h = Metrics.barHeight
        let tab = width / Double(depth.count)
        var path = Path()
        path.move(to: CGPoint(x: top, y: 0))
        for (i, share) in depth.enumerated() {
            let d = share * Metrics.notchDepth
            if d < 0.05 { continue }
            addNotch(to: &path, at: tab * (Double(i) + 0.5), depth: d)
        }
        path.addLine(to: CGPoint(x: width - top, y: 0))
        path.addCurve(to: CGPoint(x: width, y: top), control1: CGPoint(x: width - top + top * k, y: 0), control2: CGPoint(x: width, y: top - top * k))
        path.addLine(to: CGPoint(x: width, y: h - bottom))
        path.addCurve(
            to: CGPoint(x: width - bottom, y: h), control1: CGPoint(x: width, y: h - bottom + bottom * k), control2: CGPoint(x: width - bottom + bottom * k, y: h)
        )
        path.addLine(to: CGPoint(x: bottom, y: h))
        path.addCurve(to: CGPoint(x: 0, y: h - bottom), control1: CGPoint(x: bottom - bottom * k, y: h), control2: CGPoint(x: 0, y: h - bottom + bottom * k))
        path.addLine(to: CGPoint(x: 0, y: top))
        path.addCurve(to: CGPoint(x: top, y: 0), control1: CGPoint(x: 0, y: top - top * k), control2: CGPoint(x: top - top * k, y: 0))
        path.closeSubpath()
        return path
    }

    /// One notch at rest, closed along the top edge: what fills it in.
    private static func notchPath(at x: Double) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: x - Metrics.notch / 2, y: 0))
        addNotch(to: &path, at: x, depth: Metrics.notchDepth)
        path.closeSubpath()
        return path
    }

    private static func addNotch(to path: inout Path, at x: Double, depth d: Double) {
        let half = Metrics.notch / 2
        path.addLine(to: CGPoint(x: x - half, y: 0))
        path.addCurve(
            to: CGPoint(x: x, y: d),
            control1: CGPoint(x: x - half + Metrics.notch * Metrics.shoulder, y: 0), control2: CGPoint(x: x - Metrics.notch * Metrics.tip, y: d)
        )
        path.addCurve(
            to: CGPoint(x: x + half, y: 0),
            control1: CGPoint(x: x + Metrics.notch * Metrics.tip, y: d), control2: CGPoint(x: x + half - Metrics.notch * Metrics.shoulder, y: 0)
        )
    }

    private static func draw(_ icon: HoppingDotTabBarIcon, in context: GraphicsContext, color: Color) {
        let style = StrokeStyle(lineWidth: Metrics.iconStroke, lineCap: .round, lineJoin: .round)
        context.fill(icon.outline, with: .color(color.opacity(0.18)))
        context.stroke(icon.outline, with: .color(color), style: style)
        context.stroke(icon.detail, with: .color(color.opacity(0.7)), style: style)
    }

    // MARK: Types

    /// Where a hop started: everything that was moving is picked up where it was.
    private struct Hop {
        var target: Int
        /// The tab whose page is leaving.
        var from: Int
        /// The dot's place along the bar, in tabs, and its height over its rest.
        var x: Double
        var up: Double
        /// Each notch's depth and each icon's colour, 0 to 1.
        var depth: [Double]
        var tint: [Double]
        /// How much of the leaving page shows, and how far it has slid.
        var presence: Double
        var shift: Double

        static func resting(_ tab: Int, _ count: Int) -> Hop {
            let zeros = [Double](repeating: 0, count: count)
            return Hop(target: tab, from: tab, x: Double(tab), up: 0, depth: zeros, tint: zeros, presence: 0, shift: 0)
        }
    }

    private struct Sample {
        var depth: [Double]
        var tint: [Double]
        var dotX: Double
        var dotUp: Double
        var dotAlpha: Double
        /// The page that shows, how much of it, and how far aside.
        var page: Int
        var presence: Double
        var shift: Double
        /// With reduced motion: how far the shown tab's notch is filled in.
        var cover: Double
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private enum Palette {
        static let card = Color.white.opacity(0.14)
        static let line = Color.white.opacity(0.4)
    }

    private enum Metrics {
        static let side = 16.0
        static let barHeight = 78.0
        static let topRadius = 8.0
        static let bottomRadius = 32.0
        // The page runs on behind the bar this far, so that it shows through a notch.
        static let panelUnder = 24.0
        // The notch: its width along the bar's top edge and its depth at rest. The shoulders and
        // the tip are rounded by control points this share of the width from each end of a side.
        static let notch = 44.0
        static let notchDepth = 16.0
        static let shoulder = 0.22
        static let tip = 0.16
        // The dot rests in the notch, its centre this far under the bar's top edge.
        static let dot = 10.0
        static let dotSink = 5.0
        static let icon = 24.0
        static let iconY = 40.0
        static let iconLift = 4.0
        static let iconStroke = 1.6
        // The page: its content's inset in the panel, and the title's place under the safe area.
        static let inset = 24.0
        static let titleTop = 37.0
        static let avatar = 72.0
    }

    /// Seconds. The loop goes once round the tabs: a hop every `hopEvery` from `hopAt`.
    private enum M {
        static let loop = 9.0
        static let hopAt = 2.0
        static let hopEvery = 1.8
        // One hop. The dot leaves straight up and starts across after `lift`; it is in the air for
        // `air` plus `airPerTab` for each tab it crosses, on a parabola `arc` high for a neighbour
        // and `arcPerTab` higher for each tab more.
        static let lift = 0.1
        static let air = 0.4
        static let airPerTab = 0.05
        static let airMax = 0.6
        static let arc = 14.0
        static let arcPerTab = 23.0
        static let arcMin = 8.0
        static let arcMax = 90.0
        // The notch it left closes; the one it lands in starts to open just before it arrives,
        // goes a fifth too deep under its weight and comes back, while the dot bounces once.
        static let close = 0.2
        static let openBefore = 0.05
        static let open = 0.32
        static let press = 2.6
        static let bounce = 2.0
        static let bounceFor = 0.16
        // An icon takes or loses its colour and its lift in this time.
        static let tint = 0.2
        // The page that leaves slides away from where the dot goes; the new one comes from that side.
        static let pageOut = 0.25
        static let pageIn = 0.28
        static let pageSlide = 16.0
        // The clock of a hop runs this long, well past its last movement.
        static let rest = 1.2
        // With reduced motion the notch and the dot fade out, then in at the new tab, each in this time.
        static let fade = 0.15
        // After a touch the loop waits, then goes back to its first tab and waits for that to end.
        static let userHold = 3.0
        static let settle = 1.2

        static func clamp(_ x: Double) -> Double { min(max(x, 0), 1) }

        static func smooth(_ t: Double) -> Double {
            let u = clamp(t)
            return u * u * (3 - 2 * u)
        }

        /// Ease-out that goes past 1 by a fifth and comes back.
        static func pressed(_ t: Double) -> Double {
            let u = clamp(t) - 1
            return 1 + (press + 1) * u * u * u + press * u * u
        }
    }
}
