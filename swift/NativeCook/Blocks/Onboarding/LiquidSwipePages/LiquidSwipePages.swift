import SwiftUI

struct LiquidSwipePagesPage {
    var title: String
    var body: String
    /// The page's colour, and the colour of everything written on it.
    var color: Color
    var ink: Color
    var art: LiquidSwipePagesArt

    static let samples = [
        LiquidSwipePagesPage(
            title: "Best Digital Solution", body: "Everything your team needs, in one tidy place.",
            color: Color(hex: 0x392eb8), ink: Color(hex: 0x35d0f5), art: .tiles
        ),
        LiquidSwipePagesPage(
            title: "Achieve Your Goal", body: "Set a target and watch the steps add up.",
            color: Color(hex: 0x32c96f), ink: Color(hex: 0xf6f1a7), art: .flag
        ),
        LiquidSwipePagesPage(
            title: "Increase Your Value", body: "Small wins compound. We keep the score.",
            color: Color(hex: 0xe76239), ink: Color(hex: 0xffd9c2), art: .coins
        ),
    ]
}

/// Onboarding pages that change with a liquid wave. The next page waits behind the right edge as a
/// bump with an arrow in it; dragging the bump pulls a wave of the next page's colour across the
/// screen, and letting go past the middle sends it to the left side, where it settles with one
/// soft overshoot. The pages' content stays where it is: the wave covers one and uncovers the next.
struct LiquidSwipePages: View {
    /// The pages, in order; the last one's wave leads back to the first.
    var pages = LiquidSwipePagesPage.samples
    /// The button of every page but the last, which goes to the last page.
    var skipLabel = "SKIP"
    /// The last page's button.
    var doneLabel = "GET STARTED"
    /// Pulls the wave by itself, one page every `LiquidSwipePagesMotion.period` seconds, back at the first page after `loop`.
    var autoPlay = false
    /// Called when a wave has brought a page in.
    var onPageChange: ((Int) -> Void)? = nil
    var onSkip: (() -> Void)? = nil
    /// Called by the last page's button.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var still
    /// The screen's clock counts seconds from here.
    @State private var epoch: Date? = nil
    @State private var wave = Wave.resting(0, since: -M.lead)
    /// Writes the page down when the wave in progress has run out.
    @State private var arrival: Task<Void, Never>? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    /// The travel a drag started from; nil while no finger holds the wave.
    @State private var grabbed: Double? = nil
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var pageHaptic = UISelectionFeedbackGenerator()

    private typealias M = LiquidSwipePagesMotion
    private typealias Wave = LiquidSwipePagesWave

    private var now: Double { epoch.map { Date.now.timeIntervalSince($0) } ?? 0 }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let bottom = max(insets.bottom - 5, 24)

            ZStack {
                // What moves by itself never stops, so the clock only rests under reduced motion.
                TimelineView(.animation(paused: still && wave.mode == .rest)) { timeline in
                    let now = epoch.map { timeline.date.timeIntervalSince($0) } ?? 0
                    let shape = wave.resolve(at: now, size: size, count: pages.count, still: still)
                    let last = pages.count - 1

                    ZStack {
                        pages[shape.page].color
                        LiquidSwipePagesContent(
                            page: pages[shape.page], index: shape.page, count: pages.count,
                            label: shape.page == last ? doneLabel : skipLabel, wide: shape.page == last,
                            size: size, bottom: bottom, now: now, still: still, artScale: 1, action: { press(size: size) }
                        )
                        .allowsHitTesting(shape.incoming < 0)

                        // The colour is blended by drawing the two over each other.
                        let outline = shape.outline(in: size)
                        outline.fill(pages[(shape.page + 1) % pages.count].color)
                        outline.fill(pages[shape.tint].color).opacity(shape.mix)
                        // Reduced motion: the next page's colour fades in over the screen.
                        pages[shape.tint].color.opacity(shape.fade)
                        Arrow()
                            .opacity(shape.arrow)
                            .position(x: size.width - shape.reach - shape.depth + M.arrowInset, y: shape.cy)

                        if shape.incoming >= 0 {
                            LiquidSwipePagesContent(
                                page: pages[shape.incoming], index: shape.incoming, count: pages.count,
                                label: shape.incoming == last ? doneLabel : skipLabel, wide: shape.incoming == last,
                                size: size, bottom: bottom, now: now, still: still,
                                artScale: still ? 1 : 0.9 + 0.1 * shape.reveal, action: {}
                            )
                            .opacity(shape.reveal)
                            .allowsHitTesting(false)
                            .accessibilityHidden(true)
                        }
                    }
                    .frame(width: size.width, height: size.height)
                    .clipped()
                }

                grip(size: size)
            }
            .frame(width: size.width, height: size.height)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear { tapHaptic.prepare() }
        .onDisappear { arrival?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: Touch

    /// The right side of the screen takes the drag; a tap near the arrow plays the wave.
    private func grip(size: CGSize) -> some View {
        Color.clear
            .contentShape(Rectangle())
            .frame(width: Metrics.grip, height: size.height)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        guard abs(value.translation.width) > Metrics.slop || grabbed != nil else { return }
                        if grabbed == nil { grab(value, size: size) }
                        guard let grabbed, wave.mode == .hand else { return }
                        wave.travel = max(0, grabbed - value.translation.width)
                        wave.cy = value.location.y
                    }
                    .onEnded { value in
                        if grabbed != nil {
                            grabbed = nil
                            letGo(velocity: value.velocity.width, size: size)
                        } else if abs(value.startLocation.x - (Metrics.grip - M.strip - M.bump + M.arrowInset)) < Metrics.arrowReach,
                                  abs(value.startLocation.y - size.height * M.bumpY) < Metrics.arrowReach {
                            if play(to: (wave.settled(at: now).page + 1) % pages.count, dragFor: M.tapDrag, speed: 1, size: size) { pageHaptic.selectionChanged() }
                        }
                    }
            )
            .frame(maxWidth: .infinity, alignment: .trailing)
            .accessibilityElement()
            .accessibilityLabel("Next page")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { _ = play(to: (wave.settled(at: now).page + 1) % pages.count, dragFor: M.tapDrag, speed: 1, size: size) }
    }

    private func grab(_ value: DragGesture.Value, size: CGSize) {
        let now = self.now
        touched(at: now, size: size)
        let w = wave.settled(at: now)
        // Zero, so that a wave already on its way is left alone.
        grabbed = 0
        guard w.mode == .rest || w.mode == .backing else { return }
        let from = w.held(at: now, size: size, still: still)
        grabbed = from.travel + value.translation.width
        run(Wave(mode: .hand, page: w.page, target: (w.page + 1) % pages.count, t0: now, travel: from.travel, cy: value.location.y, cyFrom: from.cy))
    }

    private func letGo(velocity: Double, size: CGSize) {
        guard wave.mode == .hand else { return }
        let now = self.now
        let from = wave.held(at: now, size: size, still: still)
        let complete = wave.travel > size.width * M.completeAt || velocity < -M.flick
        var next = wave
        next.mode = complete ? .letGo : .backing
        next.t0 = now
        next.travel = from.travel
        next.cy = from.cy
        run(next)
        if complete { pageHaptic.selectionChanged() }
    }

    private func press(size: CGSize) {
        tapHaptic.impactOccurred()
        if wave.settled(at: now).page == pages.count - 1 {
            touched(at: now, size: size)
            onDone?()
        } else if play(to: pages.count - 1, dragFor: M.skipDrag, speed: M.skipSpeed, size: size) {
            onSkip?()
        }
    }

    /// Plays a whole wave to `target`, as the autoPlay loop does.
    private func play(to target: Int, dragFor: Double, speed: Double, size: CGSize) -> Bool {
        let now = self.now
        touched(at: now, size: size)
        let w = wave.settled(at: now)
        guard w.mode == .rest, target != w.page else { return false }
        run(.script(page: w.page, target: target, at: now, dragFor: dragFor, speed: speed))
        return true
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched(at now: Double, size: CGSize) {
        touches += 1
        // A wave the loop is still dragging out is dropped; one it has let go runs to its end.
        guard autoPlay, wave.mode == .script, now < wave.t0 + wave.dragFor else { return }
        let from = wave.held(at: now, size: size, still: still)
        var back = wave
        back.mode = .backing
        back.t0 = now
        back.travel = from.travel
        back.cy = from.cy
        run(back)
    }

    // MARK: The wave in progress

    /// Starts a wave, and writes its outcome down when it has run out.
    private func run(_ next: Wave) {
        wave = next
        arrival?.cancel()
        guard let end = next.end else { return }
        arrival = Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, end - now)))
            guard !Task.isCancelled else { return }
            commit()
        }
    }

    private func commit() {
        let settled = wave.settled(at: now)
        guard settled != wave else { return }
        let arrived = settled.page != wave.page
        wave = settled
        if arrived { onPageChange?(settled.page) }
    }

    private func loop() async {
        let fresh = epoch == nil
        if fresh { epoch = .now }
        guard autoPlay else { return }
        let clock = ContinuousClock()
        let instant = clock.now
        let began = now
        func sleep(until time: Double) async { try? await clock.sleep(until: instant + .seconds(time - began)) }

        // Periods start on the ambient beat, so that what moves by itself looks the same in each.
        var start = fresh ? 0 : (began / M.ambient).rounded(.up) * M.ambient
        if touches > 0 {
            await sleep(until: began + M.userHold)
            while wave.settled(at: now).mode != .rest, !Task.isCancelled { try? await Task.sleep(for: .seconds(0.05)) }
            guard !Task.isCancelled else { return }
            commit()
            // Back to the first page if a touch left another one showing, then on with the loop.
            var free = max(now, wave.t0 + M.lead)
            if wave.page != 0 {
                free = now + M.skipDrag + M.release / M.skipSpeed + M.lead
                run(.script(page: wave.page, target: 0, at: now, dragFor: M.skipDrag, speed: M.skipSpeed))
            }
            start = (free / M.ambient).rounded(.up) * M.ambient
        }
        while !Task.isCancelled {
            await sleep(until: start + M.dragAt)
            guard !Task.isCancelled else { return }
            commit()
            // Timed from when it was due, not from now: every loop is the same length.
            run(.script(page: wave.page, target: (wave.page + 1) % pages.count, at: start + M.dragAt, dragFor: M.dragFor, speed: 1))
            start += M.period
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    private enum Metrics {
        static let grip = 80.0
        static let arrowReach = 28.0
        /// A finger that has moved less than this has tapped.
        static let slop = 8.0
    }

    /// The ring with a chevron that sits in the bump.
    private struct Arrow: View {
        var body: some View {
            ZStack {
                Circle().strokeBorder(lineWidth: 1.5)
                Path { path in
                    path.move(to: CGPoint(x: 15, y: 12))
                    path.addLine(to: CGPoint(x: 21, y: 18))
                    path.addLine(to: CGPoint(x: 15, y: 24))
                }
                .stroke(style: StrokeStyle(lineWidth: 1.5, lineCap: .round, lineJoin: .round))
            }
            .foregroundStyle(.white.opacity(0.8))
            .frame(width: 36, height: 36)
            .accessibilityHidden(true)
        }
    }
}

/// What is written and drawn on one page. It never moves with the wave: the wave uncovers it.
struct LiquidSwipePagesContent: View {
    let page: LiquidSwipePagesPage
    let index: Int
    let count: Int
    let label: String
    let wide: Bool
    let size: CGSize
    let bottom: Double
    let now: Double
    let still: Bool
    let artScale: Double
    let action: () -> Void

    private enum Metrics {
        static let margin = 28.0
        /// The texts stop short of the bump.
        static let textRight = 96.0
        /// The picture's centre, as a share of the screen's height.
        static let artY = 0.3
        static let titleGap = 18.0
        static let bodyHeight = 48.0
        static let pagerGap = 42.0
        static let dash = (width: 14.0, wide: 22.0, height: 3.0, gap: 6.0)
        static let buttonGap = 82.0
        static let button = (width: 152.0, wide: 180.0, height: 58.0, radius: 8.0, stroke: 1.5)
    }

    var body: some View {
        ZStack {
            LiquidSwipePagesArtwork(kind: page.art, color: page.color, now: now, still: still)
                .scaleEffect(artScale)
                .position(x: size.width / 2, y: size.height * Metrics.artY)

            VStack(alignment: .leading, spacing: 0) {
                Text(page.title)
                    .font(.system(size: 24, weight: .bold, design: .rounded))
                    .lineLimit(1)
                    .frame(height: 30)
                // Lines 24 points apart, the first one centred in its own 24 points.
                Text(page.body)
                    .font(.system(size: 15))
                    .lineSpacing(6)
                    .lineLimit(2)
                    .opacity(0.85)
                    .padding(.top, 3)
                    .frame(height: Metrics.bodyHeight, alignment: .top)
                    .padding(.top, Metrics.titleGap)
                HStack(spacing: Metrics.dash.gap) {
                    ForEach(0..<count, id: \.self) { dash in
                        Capsule()
                            .frame(width: dash == index ? Metrics.dash.wide : Metrics.dash.width, height: Metrics.dash.height)
                            .opacity(dash == index ? 1 : 0.4)
                    }
                }
                .padding(.top, Metrics.pagerGap)
                .accessibilityElement()
                .accessibilityLabel("Page \(index + 1) of \(count)")
                Button(action: action) {
                    Text(label)
                        .font(.system(size: 14, weight: .bold, design: .rounded))
                        .tracking(0.4)
                        .lineLimit(1)
                        .frame(width: wide ? Metrics.button.wide : Metrics.button.width, height: Metrics.button.height)
                        .overlay(RoundedRectangle(cornerRadius: Metrics.button.radius).strokeBorder(lineWidth: Metrics.button.stroke))
                        .contentShape(Rectangle())
                }
                .buttonStyle(DimStyle())
                .padding(.top, Metrics.buttonGap)
            }
            .foregroundStyle(page.ink)
            .padding(.leading, Metrics.margin)
            .padding(.trailing, Metrics.textRight)
            .padding(.bottom, bottom)
            .frame(width: size.width, height: size.height, alignment: .bottomLeading)
        }
    }

    private struct DimStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .opacity(configuration.isPressed ? 0.6 : 1)
                .animation(.easeOut(duration: 0.12), value: configuration.isPressed)
        }
    }
}

#Preview {
    LiquidSwipePages(autoPlay: true)
}
