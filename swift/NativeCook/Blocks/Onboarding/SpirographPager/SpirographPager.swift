import SwiftUI

/// An intro pager whose only artwork is one set of hairline ellipses. Every page arranges them
/// into another figure (a cone, two turning spheres, a lens, an hourglass…), and a change of page
/// sends each ellipse from its place in one figure to its place in the next while the texts slide.
/// Swipe up or tap the round arrow for the next page, swipe down for the one before.
struct SpirographPager: View {
    /// The pages: a title, the figure the page arrives with and the one it settles into.
    var pages = SpirographPagerPage.samples
    /// The two lines under every title.
    var bodyText = "Discover how we can\nelevate your business"
    /// The lines of the figures.
    var ink = Color(hex: 0x16305C)
    /// The titles and the round button.
    var accent = Color(hex: 0x1B3F95)
    /// Goes through the first four pages and back to the first by itself, every `SpirographPagerMotion.loop` seconds.
    var autoPlay = false
    /// Called with the page's index when a page becomes the one shown.
    var onPageChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var calm
    @State private var run = Run(from: [], first: 0, second: 1, leaving: 0, arriving: 0, r: M.move, start: nil, end: M.move)
    @State private var runTask: Task<Void, Never>? = nil
    /// The page shown: it changes when a change of page is certain.
    @State private var page = 0
    /// When the block appeared, for the breathing and the spheres' turn.
    @State private var appeared = Date.now
    /// The drag in progress: the page it started on, and its way (1 to the next page, -1 to the
    /// one before, 2 before it has one, 0 when it is ignored).
    @State private var home = 0
    @State private var way: Int? = nil
    @State private var touching = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    /// The rings on their way from where they were to one figure and on to a second.
    private struct Run {
        /// Where the rings were when the run began: rings × 6 numbers.
        var from: [Double]
        /// The figures the run goes to, as places in the list of figures (two per page).
        var first: Int
        var second: Int
        /// The page the texts leave and the one they arrive at.
        var leaving: Int
        var arriving: Int
        /// Seconds into the run, at `start` if its clock is going. A drag sets it by hand.
        var r: Double
        var start: Date?
        /// Where the clock stops.
        var end: Double

        func time(at date: Date) -> Double {
            guard let start else { return r }
            let passed = max(0, date.timeIntervalSince(start))
            return end >= r ? min(end, r + passed) : max(end, r - passed)
        }
    }

    /// Two figures per page: the one it arrives with and the one it settles into.
    private var figures: [[[Double]]] { pages.flatMap { [$0.figure, $0.settled ?? $0.figure] } }
    /// The rings are on their way or under a finger.
    private var active: Bool { run.start != nil || way != nil }

    var body: some View {
        let figures = figures
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let center = CGPoint(x: size.width / 2, y: (size.height * Metrics.artAt).rounded())
            let buttonY = size.height - max(insets.bottom, 16) - Metrics.buttonUp

            TimelineView(.animation(paused: calm && !active)) { timeline in
                let r = run.time(at: timeline.date)
                let clock = calm ? 0 : timeline.date.timeIntervalSince(appeared)

                ZStack(alignment: .topLeading) {
                    Color.white
                    Canvas { context, _ in
                        draw(&context, figures: figures, r: r, clock: clock, center: center)
                    }
                    .accessibilityHidden(true)

                    ForEach(pages.indices, id: \.self) { index in
                        if index == run.leaving || index == run.arriving {
                            let shift = M.textShift(r, role: run.leaving == run.arriving ? 0 : index == run.arriving ? 1 : -1, calm: calm)
                            words(pages[index].title)
                                .opacity(shift.opacity)
                                .offset(y: shift.y)
                                .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .bottomLeading)
                                .padding(.leading, Metrics.margin)
                                .padding(.bottom, size.height - buttonY - 16)
                                .accessibilityHidden(index != page)
                        }
                    }

                    Button {
                        go(to: (page + 1) % pages.count, hand: true)
                    } label: {
                        SpirographPagerArrow()
                            .stroke(accent, style: StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round))
                            .frame(width: 14, height: 14)
                            .frame(width: Metrics.button, height: Metrics.button)
                            .overlay(Circle().strokeBorder(accent, lineWidth: 1))
                            .padding(10)
                            .contentShape(Circle())
                    }
                    .buttonStyle(DimStyle())
                    .accessibilityLabel("Next: \(pages[(page + 1) % pages.count].title.replacingOccurrences(of: "\n", with: " "))")
                    .position(x: size.width - Metrics.buttonRight, y: buttonY)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if !touching {
                        touching = true
                        touches += 1
                    }
                    dragged(by: value.translation.height, figures: figures)
                }
                .onEnded { value in
                    touching = false
                    released(velocity: value.velocity.height, figures: figures)
                }
        )
        .onAppear { arrive(at: .now, figures: figures) }
        .onDisappear { runTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    private func words(_ title: String) -> some View {
        let font = UIFont.systemFont(ofSize: 13)
        return VStack(alignment: .leading, spacing: Metrics.titleGap) {
            // One view a line: the lines are as far apart as the letters are tall.
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(title.split(separator: "\n").enumerated()), id: \.offset) { line in
                    Text(String(line.element))
                        .font(.system(size: 30, weight: .semibold))
                        .tracking(-0.8)
                        .foregroundStyle(accent)
                        .lineLimit(1)
                        .frame(height: 30)
                }
            }
            Text(bodyText)
                .font(Font(font))
                .lineSpacing(max(0, 16 - font.lineHeight))
                .foregroundStyle(Palette.mute)
                .frame(height: 32)
        }
    }

    // MARK: The figure

    private func draw(_ context: inout GraphicsContext, figures: [[[Double]]], r: Double, clock: Double, center: CGPoint) {
        let from = run.from
        guard from.count == SpirographPagerFigures.rings * 6 else { return }
        let first = figures[run.first]
        let second = figures[run.second]
        let size = calm ? 1 : M.breathing(clock)

        func oval(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ alpha: Double) {
            guard alpha > 0.002 else { return }
            let box = CGRect(x: center.x + (x - w / 2) * size, y: center.y + (y - h / 2) * size, width: w * size, height: h * size)
            context.stroke(Path(ellipseIn: box), with: .color(ink.opacity(alpha)), lineWidth: Metrics.stroke)
        }

        for k in 0..<SpirographPagerFigures.rings {
            if calm {
                // Nothing travels: the figure the rings come from fades into the one they go to.
                let settling = r >= M.move
                let u = settling ? M.ease((r - M.move - M.settleAfter) / M.settle) : M.ease(r / M.move)
                let to = settling ? second[k] : first[k]
                if settling {
                    oval(first[k][0], first[k][1], first[k][2], first[k][3], first[k][4] * (1 - u))
                } else {
                    oval(from[k * 6], from[k * 6 + 1], from[k * 6 + 2], from[k * 6 + 3], from[k * 6 + 4] * (1 - u))
                }
                oval(to[0], to[1], to[2], to[3], to[4] * u)
            } else {
                let spin = M.ringValue(from, first, second, r, k, 5)
                oval(
                    M.ringValue(from, first, second, r, k, 0),
                    M.ringValue(from, first, second, r, k, 1),
                    M.ringValue(from, first, second, r, k, 2) * M.turned(k, spin: spin, clock: clock),
                    M.ringValue(from, first, second, r, k, 3),
                    M.ringValue(from, first, second, r, k, 4)
                )
            }
        }
    }

    /// Where every ring is now, as the flat list a run starts from.
    private func snapshot(at date: Date, figures: [[[Double]]]) -> [Double] {
        let r = run.time(at: date)
        let first = figures[run.first]
        let second = figures[run.second]
        return (0..<SpirographPagerFigures.rings * 6).map { M.ringValue(run.from, first, second, r, $0 / 6, $0 % 6) }
    }

    // MARK: Behaviour

    /// A change of page is on its way and can no longer be stopped.
    private func changing(at date: Date) -> Bool {
        run.leaving != run.arriving && run.time(at: date) < M.move && page == run.arriving
    }

    /// Lets the run's clock go from where it is to `end`, and stops it there.
    private func play(to end: Double, at date: Date, then: (() -> Void)? = nil) {
        runTask?.cancel()
        run.start = date
        run.end = end
        let length = abs(end - run.r) - Date.now.timeIntervalSince(date)
        let clock = ContinuousClock()
        let due = clock.now + .seconds(max(0, length))
        runTask = Task { @MainActor in
            try? await clock.sleep(until: due, tolerance: .zero)
            guard !Task.isCancelled else { return }
            run.r = end
            run.start = nil
            then?()
        }
    }

    /// The block opens on the first page as if it had just arrived there.
    private func arrive(at date: Date, figures: [[[Double]]]) {
        run = Run(from: figures[0].flatMap { $0 }, first: 0, second: 1, leaving: 0, arriving: 0, r: M.move, start: nil, end: M.move)
        play(to: M.runLength(settles: true), at: date)
    }

    /// Starts the change to page `to`: the rings go to its figure, wait, and settle. The loop
    /// passes the moment the change was due, so a late wake-up does not shift it.
    private func go(to: Int, hand: Bool, at date: Date = .now) {
        let figures = figures
        guard !changing(at: date) else { return }
        run = Run(from: snapshot(at: date, figures: figures), first: to * 2, second: to * 2 + 1, leaving: page, arriving: to, r: 0, start: nil, end: 0)
        turn(to: to, hand: hand)
        play(to: M.runLength(settles: true), at: date)
    }

    private func turn(to: Int, hand: Bool) {
        guard to != page else { return }
        page = to
        if hand { haptic.selectionChanged() }
        onPageChange?(to)
    }

    private func dragged(by distance: Double, figures: [[[Double]]]) {
        if way == nil {
            guard abs(distance) >= 8 else { return }
            // A change of page that is on its way is left to finish.
            if changing(at: .now) {
                way = 0
                return
            }
            runTask?.cancel()
            run = Run(from: snapshot(at: .now, figures: figures), first: page * 2 + 1, second: page * 2 + 1, leaving: page, arriving: page, r: 0, start: nil, end: 0)
            home = page
            way = 2
        }
        guard let now = way, now != 0 else { return }
        let to = distance < 0 ? 1 : -1
        if to != now {
            // Up goes to the next page's figure (after the last comes the first), down to the settled
            // figure of the page before; the first page has none before it.
            way = to
            let target = to > 0 ? (home + 1) % pages.count : home - 1
            run.arriving = target < 0 ? home : target
            run.first = target < 0 ? home * 2 + 1 : to > 0 ? target * 2 : target * 2 + 1
            run.second = target < 0 ? home * 2 + 1 : target * 2 + 1
        }
        let far = run.arriving == home ? 0 : min(0.999, abs(distance) / M.drag)
        run.r = far * M.move
    }

    private func released(velocity: Double, figures: [[[Double]]]) {
        guard let now = way else { return }
        way = nil
        guard now != 0 else { return }
        let flick = now > 0 ? -velocity : velocity
        let far = run.r / M.move
        if run.arriving != home, far > M.commit || (far > 0.05 && flick > 600) {
            // Up arrives at the figure and lets it settle; down arrives at the settled figure.
            turn(to: run.arriving, hand: true)
            play(to: M.runLength(settles: run.first != run.second), at: .now)
        } else {
            play(to: 0, at: .now) { resettle() }
        }
    }

    /// Brings the rings from wherever they are to the settled figure of the page shown.
    private func resettle() {
        let figures = figures
        run = Run(from: snapshot(at: .now, figures: figures), first: page * 2 + 1, second: page * 2 + 1, leaving: page, arriving: page, r: 0, start: nil, end: 0)
        play(to: M.move, at: .now)
    }

    private func loop() async {
        guard autoPlay else { return }
        // Every step is due at a fixed time after the loop began, and the timers may not run late.
        let clock = ContinuousClock()
        var origin = clock.now
        var began = Date.now
        if touches == 0 {
            arrive(at: began, figures: figures)
        } else {
            // Three seconds of rest, then back to the first page, which the loop starts from the
            // moment its figure has arrived.
            var quiet = 0.0
            while quiet < M.userHold {
                try? await Task.sleep(for: .seconds(0.1))
                guard !Task.isCancelled else { return }
                quiet = active ? 0 : quiet + 0.1
            }
            go(to: 0, hand: false)
            origin = clock.now + .seconds(M.move)
            began = Date.now + M.move
        }
        while !Task.isCancelled {
            for at in M.nextAt {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                go(to: (page + 1) % pages.count, hand: false, at: began + at)
            }
            try? await clock.sleep(until: origin + .seconds(M.homeAt), tolerance: .zero)
            guard !Task.isCancelled else { return }
            go(to: 0, hand: false, at: began + M.homeAt)
            origin += .seconds(M.loop)
            began += M.loop
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.5 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

private typealias M = SpirographPagerMotion

private enum Palette {
    static let mute = Color(hex: 0x8A8F9A)
}

private enum Metrics {
    /// The artwork's centre, as a fraction of the screen's height.
    static let artAt = 0.387
    static let stroke = 0.75
    static let margin = 24.0
    static let button = 36.0
    /// The button's centre from the right edge and from the bottom safe area.
    static let buttonRight = 41.0
    static let buttonUp = 58.0
    static let titleGap = 16.0
}

/// A down arrow in a 14 × 14 box.
struct SpirographPagerArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 14
        var path = Path()
        path.move(to: CGPoint(x: rect.minX + 7 * s, y: rect.minY + 1.5 * s))
        path.addLine(to: CGPoint(x: rect.minX + 7 * s, y: rect.minY + 12.5 * s))
        path.move(to: CGPoint(x: rect.minX + 2.5 * s, y: rect.minY + 8 * s))
        path.addLine(to: CGPoint(x: rect.minX + 7 * s, y: rect.minY + 12.5 * s))
        path.addLine(to: CGPoint(x: rect.minX + 11.5 * s, y: rect.minY + 8 * s))
        return path
    }
}

#Preview {
    SpirographPager(autoPlay: true)
}
