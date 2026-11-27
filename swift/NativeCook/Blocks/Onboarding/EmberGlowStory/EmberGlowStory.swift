import SwiftUI

enum EmberGlowStoryGlyph {
    case clock, bolt, figure, calendar

    var symbol: String {
        switch self {
        case .clock: "clock"
        case .bolt: "bolt.fill"
        case .figure: "figure.stand"
        case .calendar: "calendar"
        }
    }
}

struct EmberGlowStoryWord: Equatable {
    /// A word, or a glyph drawn at the height of a capital letter.
    var text: String? = nil
    var glyph: EmberGlowStoryGlyph? = nil
    /// A key word lights up; the number is its turn among the key words of its page.
    var key: Int? = nil
    /// It was lit on the page before, which this page continues.
    var was = false
    var italic = false
    /// No space before it.
    var glue = false

    static func word(_ text: String, _ key: Int? = nil) -> Self { Self(text: text, key: key) }
    static func glyph(_ glyph: EmberGlowStoryGlyph, _ key: Int? = nil) -> Self { Self(glyph: glyph, key: key) }
}

struct EmberGlowStoryPage: Equatable {
    /// The sentence, line by line: the lines are fixed, nothing wraps.
    var lines: [[EmberGlowStoryWord]]
    /// Which of `glows` lights the page.
    var glow: Int
    /// A streak of amber in the hot spot.
    var amber = false
    /// How many words at the start are the page before, kept on screen.
    var keeps = 0

    static let samples: [EmberGlowStoryPage] = [
        EmberGlowStoryPage(
            lines: [[.word("Industrial")], [.word("revolution"), .word("made")], [.word("us"), .word("plan"), .word("around")], [.word("the"), .glyph(.clock, 0), .word("clock", 0)]],
            glow: 0
        ),
        EmberGlowStoryPage(
            lines: [
                [.word("But"), .word("every"), .word("person")],
                [.word("has"), .word("their"), .word("own")],
                [.word("unique"), .glyph(.bolt, 0), .word("energy", 0)],
                [.word("rhythm", 1)],
            ],
            glow: 1
        ),
        EmberGlowStoryPage(
            lines: [
                [.word("But"), .word("every"), .word("person")],
                [.word("has"), .word("their"), .word("own")],
                [.word("unique"), EmberGlowStoryWord(glyph: .bolt, was: true), EmberGlowStoryWord(text: "energy", was: true)],
                [EmberGlowStoryWord(text: "rhythm", was: true), EmberGlowStoryWord(text: ",", key: 0, glue: true), .word("and", 0), .word("it’s", 0)],
                [EmberGlowStoryWord(text: "not", key: 1, italic: true), EmberGlowStoryWord(text: "random", key: 1, italic: true), .word("it’s", 2), .word("a", 2)],
                [.word("predictable")],
                [.word("biology", 3)],
            ],
            glow: 1, amber: true, keeps: 10
        ),
        EmberGlowStoryPage(
            lines: [
                [.word("Unlock"), .word("your"), .word("full")],
                [.word("potential"), .word("by"), .word("pairing")],
                [.word("your"), .glyph(.figure, 0), .word("biology", 0), .word("and")],
                [.glyph(.calendar, 1), .word("schedule", 1)],
            ],
            glow: 2
        ),
    ]
}

struct EmberGlowStoryGlow: Equatable {
    /// The tint from where it starts down to the bottom edge, and where each colour sits (0 top, 1 bottom).
    var tint: [Color]
    var stops: [Double]
    /// The hot spot at the bottom edge: its middle, its rim, and its radii.
    var hot: Color
    var rim: Color
    var size: CGSize
    /// Plain words under this light.
    var mute: Color

    static let samples = [
        EmberGlowStoryGlow(
            tint: [Color(hex: 0x010200), Color(hex: 0x0c0706), Color(hex: 0x160b0a), Color(hex: 0x24130f)], stops: [0.68, 0.8, 0.9, 1],
            hot: Color(hex: 0xe4ccc4), rim: Color(hex: 0x8a5a4c), size: CGSize(width: 150, height: 46), mute: Color(hex: 0x8e8a84)
        ),
        EmberGlowStoryGlow(
            tint: [Color(hex: 0x010200), Color(hex: 0x101a06), Color(hex: 0x1a3a13), Color(hex: 0x264c01), Color(hex: 0x3a6a08)], stops: [0.57, 0.65, 0.75, 0.82, 1],
            hot: Color(hex: 0x9bd12f), rim: Color(hex: 0x508131), size: CGSize(width: 210, height: 120), mute: Color(hex: 0x8c936e)
        ),
        EmberGlowStoryGlow(
            tint: [Color(hex: 0x010200), Color(hex: 0x2b0d12), Color(hex: 0x3c000d), Color(hex: 0x510013)], stops: [0.62, 0.7, 0.81, 1],
            hot: Color(hex: 0xe8621a), rim: Color(hex: 0xa3180f), size: CGSize(width: 210, height: 105), mute: Color(hex: 0x9c7f7c)
        ),
    ]
}

/// A short argument told one sentence a page on black, under a glow that rises from the bottom
/// edge and changes colour with each page. Sentences are written word by word and their key words
/// light up. A tap goes on, a swipe down goes back, and the glow comes under a finger on the button.
struct EmberGlowStory: View {
    var pages = EmberGlowStoryPage.samples
    var glows = EmberGlowStoryGlow.samples
    var nextLabel = "Next"
    var doneLabel = "Unlock"
    /// Goes through the pages by itself every `EmberGlowStoryMotion.loop` seconds.
    var autoPlay = false
    var onPageChange: ((Int) -> Void)? = nil
    /// Called when the last page's button is pressed.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The clock's zero: every time below is in seconds since then.
    @State private var origin = Date.now
    /// The story opens as it is just after the last page's flood: the first page, whole.
    @State private var scene = M.Scene(page: 0, from: 0, at: -M.returnTime, how: .flooded)
    @State private var press = M.Press()
    /// A touch is on the screen, and it has moved too far to be a tap.
    @State private var tracking = false
    @State private var moved = false
    /// Without motion nothing breathes, so the timeline may stop: when, and whether it has.
    @State private var settled = Double.infinity
    @State private var resting = false
    @State private var restTask: Task<Void, Never>? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var screenWidth = 393.0
    @State private var selectHaptic = UISelectionFeedbackGenerator()

    private enum By {
        case tap, auto, rest
    }

    private var last: Int { pages.count - 1 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let fit = min(1.1, screen.width / Metrics.designWidth)
            let buttonBottom = max(insets.bottom + 3, 20)
            let buttonTop = screen.height - buttonBottom - Metrics.buttonHeight

            TimelineView(.animation(paused: resting)) { timeline in
                let t = resting ? settled : min(timeline.date.timeIntervalSince(origin), settled)

                ZStack {
                    Palette.background
                    glow(t, screen: screen)

                    ForEach(pages.indices, id: \.self) { index in
                        pageText(index, t, fit: fit)
                            .padding(.leading, Metrics.margin * fit)
                            .padding(.bottom, buttonBottom + Metrics.buttonHeight + Metrics.textGap)
                            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                    }

                    button(t)
                        .padding(.horizontal, Metrics.buttonMargin)
                        .padding(.bottom, buttonBottom)
                        .frame(maxHeight: .infinity, alignment: .bottom)
                }
                .frame(width: screen.width, height: screen.height)
                .clipped()
            }
            .contentShape(Rectangle())
            .gesture(touch(buttonTop: buttonTop, width: screen.width))
            .ignoresSafeArea()
            .onChange(of: screen.width, initial: true) { screenWidth = screen.width }
        }
        .preferredColorScheme(.dark)
        .onAppear {
            // The clock starts with the loop, so the first pass is timed like every other.
            origin = .now
            scene = M.Scene(page: 0, from: last, at: -M.returnTime, how: .flooded)
            settle()
        }
        .onDisappear { restTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The glow

    private func glow(_ t: Double, screen: CGSize) -> some View {
        let still = reduceMotion
        let hot = M.hotAt(home: screen.width * Metrics.hotAcross, press, t, still: still)
        let amber = M.amberAt(has: pages[scene.page].amber, had: pages[scene.from].amber, scene, t)
        return Canvas { context, _ in
            let height = screen.height
            var lift = 0.0
            for (color, glow) in glows.enumerated() {
                let pose = M.glowPose(color: color, glow: pages[scene.page].glow, before: pages[scene.from].glow, scene, t, still: still)
                if color == pages[scene.page].glow { lift = pose.lift }
                if pose.opacity <= 0 { continue }
                // Stretched from the bottom edge, and lifted.
                var layer = context
                layer.opacity = pose.opacity
                layer.translateBy(x: 0, y: height)
                layer.scaleBy(x: 1, y: pose.stretch)
                layer.translateBy(x: 0, y: -height - pose.lift)
                let from = glow.stops[0] * height
                let stops = zip(glow.tint, glow.stops).map { Gradient.Stop(color: $0, location: ($1 - glow.stops[0]) / (1 - glow.stops[0])) }
                // The tint runs on below the screen, so the surge leaves no gap under it.
                layer.fill(
                    Path(CGRect(x: 0, y: from, width: screen.width, height: height - from + M.surge + 40)),
                    with: .linearGradient(Gradient(stops: stops), startPoint: CGPoint(x: 0, y: from), endPoint: CGPoint(x: 0, y: height))
                )
                var spot = layer
                spot.opacity = pose.opacity * hot.light
                spot.fillEllipse(
                    center: CGPoint(x: hot.x, y: height + glow.size.height * Metrics.hotBelow), radii: glow.size,
                    gradient: Metrics.falloff(glow.hot, glow.rim)
                )
            }
            if amber > 0 {
                var streak = context
                streak.opacity = amber * hot.light
                streak.fillEllipse(
                    center: CGPoint(x: hot.x + Metrics.amberAside, y: height + Metrics.amberSize.height * Metrics.hotBelow - lift),
                    radii: Metrics.amberSize, gradient: Metrics.falloff(Palette.amber, Palette.amber)
                )
            }
        }
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    // MARK: The words

    private func pageText(_ index: Int, _ t: Double, fit: Double) -> some View {
        let page = pages[index]
        let count = page.lines.reduce(0) { $0 + $1.count } - page.keeps
        // A page that continues the one before starts lower by the lines it adds.
        let rise = page.keeps > 0 && index > 0 ? Double(page.lines.count - pages[index - 1].lines.count) * Metrics.line * fit : 0
        let handsOver = index < last && pages[index + 1].keeps > 0 && scene.page == index + 1
        let block = M.blockPose(index, rise: scene.from == index - 1 ? rise : 0, handsOver: handsOver, scene, t, still: reduceMotion)
        // Where each line starts among the page's words.
        let starts = page.lines.reduce(into: [0]) { $0.append($0[$0.count - 1] + $1.count) }
        return VStack(alignment: .leading, spacing: 0) {
            ForEach(page.lines.indices, id: \.self) { row in
                HStack(spacing: 0) {
                    ForEach(page.lines[row].indices, id: \.self) { column in
                        let item = page.lines[row][column]
                        let order = starts[row] + column - page.keeps
                        let pose = scene.page == index
                            ? M.wordPose(order: order, count: count, key: item.key ?? -1, was: item.was, scene, t, still: reduceMotion)
                            : M.wordAtRest(key: item.key ?? -1)
                        EmberGlowStoryWordView(item: item, pose: pose, glow: glows[page.glow], fit: fit)
                            .padding(.leading, column == 0 || item.glue ? 0 : Metrics.space * fit)
                    }
                }
                .frame(height: Metrics.line * fit)
            }
        }
        .opacity(block.opacity)
        .offset(y: block.y)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(page.lines.map { $0.compactMap(\.text).joined(separator: " ") }.joined(separator: " "))
        .accessibilityHidden(index != scene.page)
    }

    private func button(_ t: Double) -> some View {
        let e = t - scene.at
        // The last page's label takes the place of the others'.
        let done = scene.page == last ? M.smooth(e, 0.2, 0.5) : scene.from == last ? 1 - M.smooth(e, 0, 0.25) : 0
        return ZStack {
            Capsule().fill(.white.opacity(0.06 + 0.085 * M.pressAt(press, t)))
            Text(nextLabel)
                .opacity(1 - done)
            HStack(spacing: 6) {
                Image(systemName: "lock.open.fill")
                    .font(.system(size: 14, weight: .semibold))
                    .scaleEffect(reduceMotion ? 1 : 0.6 + 0.4 * done)
                Text(doneLabel)
            }
            .opacity(done)
        }
        .font(.system(size: 16, weight: .semibold))
        .foregroundStyle(.white)
        .lineLimit(1)
        .frame(height: Metrics.buttonHeight)
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(scene.page == last ? doneLabel : nextLabel)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            touches += 1
            advance(.tap)
        }
    }

    // MARK: Behaviour

    /// Seconds on the clock right now.
    private var now: Double { Date.now.timeIntervalSince(origin) }

    /// One touch is a tap to go on, a swipe down to go back, or a finger resting on the button.
    private func touch(buttonTop: Double, width: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if !tracking {
                    tracking = true
                    touches += 1
                    let point = value.startLocation
                    let onButton = point.y >= buttonTop && point.y <= buttonTop + Metrics.buttonHeight && point.x >= Metrics.buttonMargin && point.x <= width - Metrics.buttonMargin
                    if onButton { hold(true, x: point.x) }
                }
                if !moved, hypot(value.translation.width, value.translation.height) > Metrics.slop {
                    moved = true
                    hold(false)
                }
            }
            .onEnded { value in
                hold(false)
                if !moved {
                    advance(.tap)
                } else if value.translation.height > Metrics.backSwipe, value.translation.height > abs(value.translation.width) {
                    if scene.page > 0 { goTo(scene.page - 1, .whole, .tap) }
                }
                tracking = false
                moved = false
            }
    }

    /// Shows a page. Taps, the loop that plays by itself and the return to rest all come through here.
    private func goTo(_ page: Int, _ how: M.How, _ by: By) {
        scene = M.Scene(page: page, from: scene.page, at: now, how: how)
        settle()
        if by == .tap { selectHaptic.selectionChanged() }
        onPageChange?(page)
        if how == .flooded { onDone?() }
    }

    private func advance(_ by: By) {
        if scene.page < last { goTo(scene.page + 1, .forward, by) } else { goTo(0, .flooded, by) }
    }

    /// Puts a finger on the button at x, or takes it off.
    private func hold(_ down: Bool, x: Double = 0) {
        guard press.down != down else { return }
        press = M.Press(down: down, at: now, x: down ? x : press.x)
        settle()
    }

    /// Without motion nothing breathes: the timeline runs for a while after a change and stops.
    private func settle() {
        restTask?.cancel()
        resting = false
        settled = reduceMotion ? now + 3 : .infinity
        guard reduceMotion else { return }
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(3))
            if !Task.isCancelled { resting = true }
        }
    }

    private func loop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        var start = clock.now
        if touches > 0 {
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            // The way back to the first page takes `returnTime`, and the loop starts from there.
            if scene.page != 0 { goTo(0, .whole, .rest) }
            start = clock.now + .seconds(M.returnTime)
        }
        while !Task.isCancelled {
            for at in M.autoTaps {
                try? await clock.sleep(until: start + .seconds(at - M.pressFor))
                guard !Task.isCancelled else { return }
                hold(true, x: screenWidth / 2)
                try? await clock.sleep(until: start + .seconds(at))
                guard !Task.isCancelled else { return }
                hold(false)
                advance(.auto)
            }
            start += .seconds(M.loop)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }
}

/// One word or glyph: grey under the glow, white once it is lit, with a halo in the glow's colour as it lights up.
struct EmberGlowStoryWordView: View {
    let item: EmberGlowStoryWord
    let pose: EmberGlowStoryMotion.WordPose
    let glow: EmberGlowStoryGlow
    let fit: Double

    var body: some View {
        mark(glow.mute)
            .overlay {
                if item.key != nil || item.was {
                    mark(Palette.lit)
                        .shadow(color: glow.hot.opacity(pose.flash), radius: 12)
                        .opacity(pose.lit)
                }
            }
            .scaleEffect(item.glyph == nil ? 1 : pose.pop)
            .opacity(pose.opacity)
            .offset(y: pose.y)
    }

    @ViewBuilder
    private func mark(_ color: Color) -> some View {
        if let glyph = item.glyph {
            // SF Symbols are drawn larger than icon-font glyphs, hence the smaller size.
            Image(systemName: glyph.symbol)
                .font(.system(size: Metrics.symbol * fit, weight: .bold))
                .foregroundStyle(color)
                .frame(width: Metrics.glyph * fit, height: Metrics.glyph * fit)
        } else {
            Text(item.text ?? "")
                .font(.system(size: Metrics.font * fit, weight: .bold))
                .italic(item.italic)
                .tracking(-0.3)
                .foregroundStyle(color)
                .lineLimit(1)
                .fixedSize()
        }
    }
}

private typealias M = EmberGlowStoryMotion

private enum Palette {
    static let background = Color(hex: 0x010200)
    static let amber = Color(hex: 0xe0a21a)
    static let lit = Color.white.opacity(0.95)
}

private enum Metrics {
    /// The type is set for a screen 393 points wide and scaled down on a narrower one.
    static let designWidth = 393.0
    static let margin = 40.0
    static let font = 30.0
    static let line = 43.0
    static let space = 7.5
    static let glyph = 26.0
    static let symbol = 21.0
    static let buttonMargin = 28.0
    static let buttonHeight = 54.0
    static let textGap = 38.0
    // The hot spot's middle: this far across the screen, and this share of its height below the edge.
    static let hotAcross = 0.6
    static let hotBelow = 0.15
    static let amberSize = CGSize(width: 90, height: 46)
    static let amberAside = 40.0
    // A finger that moves further than this is not tapping; this far downwards, it is going back.
    static let slop = 12.0
    static let backSwipe = 40.0

    /// How a hot spot's light falls off from the middle to the rim.
    static func falloff(_ middle: Color, _ rim: Color) -> Gradient {
        Gradient(stops: [
            .init(color: middle, location: 0),
            .init(color: middle.opacity(0.7), location: 0.3),
            .init(color: rim.opacity(0.35), location: 0.6),
            .init(color: rim.opacity(0.08), location: 0.85),
            .init(color: rim.opacity(0), location: 1),
        ])
    }
}

#Preview {
    EmberGlowStory(autoPlay: true)
}
