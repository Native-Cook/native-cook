import SwiftUI

struct EnergyWaveStoryStep: Equatable {
    /// Its lines are broken by hand with "\n", so an emoji never moves a word to the next line.
    var headline: String
    var body: String? = nil
    /// One emoji a tag: over the first crest, over the second, under the trough. The first step has none.
    var tags: [String] = []

    static let samples = [
        EnergyWaveStoryStep(
            headline: "Energy in your body\nrises and falls.",
            body: "Your body runs on a daily rhythm, known as the circadian rhythm."
        ),
        EnergyWaveStoryStep(
            headline: "This cycle controls when\nyou feel ⚡ energetic or\n🥱 tired.",
            tags: ["⚡", "⚡", "🥱"]
        ),
        EnergyWaveStoryStep(
            headline: "Sync with your rhythm to\nsleep better, feel more\nenergised, and be more\nproductive.",
            body: "Crest can help you find the ideal time for a 👟 run, 😴 nap or when you should 📵 wind down for sleep.",
            tags: ["👟", "📵", "😴"]
        ),
    ]
}

/// A three-step explainer under a wave. The wave is grey, then colour runs along it; a tap brings
/// three emoji tags out of its crests and trough; another changes them. On the first step a finger
/// dragged along the wave moves the colour, and a tapped tag hops.
struct EnergyWaveStory: View {
    /// The three steps: the wave fills with colour, the tags arrive, the tags change.
    var steps = EnergyWaveStoryStep.samples
    var footerLabel = "Tap to continue"
    /// The wave's colours from its crests down to its troughs.
    var waveColors = [Color(hex: 0xf5c12e), Color(hex: 0x71aaab), Color(hex: 0x309bd0)]
    var emptyColor = Color(hex: 0xc9c9ce)
    var panelColor = Color(hex: 0xf0eff6)
    var backgroundColor = Color(hex: 0xfffcff)
    var inkColor = Color(hex: 0x111113)
    var muteColor = Color(hex: 0x9a9aa0)
    var accentColor = Color(hex: 0x3b2f8f)
    /// Goes through the steps by itself every `EnergyWaveStoryMotion.loop` seconds.
    var autoPlay = false
    var onStepChange: ((Int) -> Void)? = nil
    /// Called when the last step is left.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The clock's zero: every time below is in seconds since then.
    @State private var origin = Date.now
    /// The story opens as it is a moment after the last step was left: grey, with the colour about to set off.
    @State private var scene = EnergyWaveStoryScene(step: 0, from: 2, at: -M.returnTime, fill: 1)
    /// Where a finger left the colour on the first step; negative while the colour runs by itself.
    @State private var scrub = -1.0
    @State private var dragging = false
    /// A touch that has moved too far to be a tap.
    @State private var moved = false
    @State private var pressed = -10.0
    @State private var pressedUntil = -10.0
    /// When each tag was last tapped.
    @State private var jumps = [-10.0, -10, -10]
    /// When nothing moves any more, and whether that moment has passed: the timeline stops then.
    @State private var settled = M.fillAfter + M.fill
    @State private var resting = false
    @State private var restTask: Task<Void, Never>? = nil
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var selectHaptic = UISelectionFeedbackGenerator()
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    private enum How {
        case tap, auto, rest
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let scale = screen.width / Metrics.designWidth
            let bottom = max(insets.bottom - 2, 14)

            TimelineView(.animation(paused: resting)) { timeline in
                let t = resting ? settled : min(timeline.date.timeIntervalSince(origin), settled)

                ZStack(alignment: .topLeading) {
                    backgroundColor

                    header(t, scale: scale)
                        .frame(width: screen.width, height: Metrics.sceneHeight * scale, alignment: .topLeading)

                    VStack(spacing: 0) {
                        Color.clear.frame(height: Metrics.sceneHeight * scale)
                        texts(t)
                        footer(t)
                            .padding(.top, Metrics.footerGap)
                            .padding(.bottom, bottom)
                    }
                }
                .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            }
            .contentShape(Rectangle())
            .gesture(touch(scale: scale))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            // The clock starts with the loop, so the first pass is timed like every other.
            origin = .now
            settle()
        }
        .onDisappear { restTask?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await loop() }
    }

    // MARK: The header

    private func header(_ t: Double, scale: Double) -> some View {
        let fill = scene.step == 0 && scrub >= 0 ? scrub : M.fillAt(scene, t)
        let flex = M.flexAt(scene, t, still: reduceMotion)
        let still = reduceMotion
        return Canvas { context, _ in
            var context = context
            context.scaleBy(x: scale, y: scale)
            context.fill(Metrics.panel, with: .color(panelColor))

            // The wave swells from the line it rests on.
            var wave = context
            wave.translateBy(x: 0, y: Metrics.base)
            wave.scaleBy(x: 1, y: 1 + flex / Metrics.crest)
            wave.translateBy(x: 0, y: -Metrics.base)
            let stroke = StrokeStyle(lineWidth: Metrics.stroke, lineCap: .round, lineJoin: .round)
            wave.stroke(Metrics.wave, with: .color(emptyColor), style: stroke)
            if fill > 0 {
                // Without motion the colour fades in over the whole wave.
                var colour = wave
                colour.opacity = still ? fill : 1
                colour.stroke(
                    Metrics.wave.trimmedPath(from: 0, to: still ? 1 : fill),
                    with: .linearGradient(
                        gradient,
                        startPoint: CGPoint(x: 0, y: Metrics.base - Metrics.crest - Metrics.stroke / 2),
                        endPoint: CGPoint(x: 0, y: Metrics.base + Metrics.stroke / 2)
                    ),
                    style: stroke
                )
                // The head is there while the colour is on its way, and gone once it has arrived.
                let light = still ? 0 : Metrics.headLight * M.smooth(fill, 0, 0.05) * (1 - M.smooth(fill, 0.93, 1))
                if light > 0 {
                    for layer in 0 ..< Metrics.headLayers {
                        let back = Metrics.head * Double(Metrics.headLayers - layer) / Double(Metrics.headLayers)
                        let head = Metrics.wave.trimmedPath(from: max(0, fill - back / Metrics.length), to: fill)
                        wave.stroke(head, with: .color(.white.opacity(light)), style: stroke)
                    }
                }
            }

            for x in Metrics.discs {
                var shadow = context
                shadow.addFilter(.blur(radius: 5))
                shadow.fill(disc(x, Metrics.base + 2), with: .color(Palette.shadow))
                context.fill(disc(x, Metrics.base), with: .color(.white))
            }
            drawAlarm(in: context, x: Metrics.discs[0], y: Metrics.base)
            drawBed(in: context, x: Metrics.discs[1], y: Metrics.base)
        }
        .accessibilityHidden(true)
        .overlay(alignment: .topLeading) {
            ZStack {
                ForEach(Metrics.tags.indices, id: \.self) { index in
                    tag(index, t)
                }
            }
            .frame(width: Metrics.designWidth, height: Metrics.sceneHeight)
            .scaleEffect(scale, anchor: .topLeading)
            .accessibilityHidden(true)
        }
    }

    private var gradient: Gradient {
        guard waveColors.count == Metrics.waveStops.count else { return Gradient(colors: waveColors) }
        return Gradient(stops: zip(waveColors, Metrics.waveStops).map { Gradient.Stop(color: $0, location: $1) })
    }

    private func disc(_ x: Double, _ y: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - Metrics.disc / 2, y: y - Metrics.disc / 2, width: Metrics.disc, height: Metrics.disc))
    }

    /// An alarm clock about the point (x, y): two bells, a face with its hands, two feet.
    private func drawAlarm(in context: GraphicsContext, x: Double, y: Double) {
        func circle(_ dx: Double, _ dy: Double, _ r: Double) -> Path {
            Path(ellipseIn: CGRect(x: x + dx - r, y: y + dy - r, width: r * 2, height: r * 2))
        }
        func line(_ ax: Double, _ ay: Double, _ bx: Double, _ by: Double) -> Path {
            var path = Path()
            path.move(to: CGPoint(x: x + ax, y: y + ay))
            path.addLine(to: CGPoint(x: x + bx, y: y + by))
            return path
        }
        context.fill(circle(-4.6, -5.2, 2.3), with: .color(Palette.sun))
        context.fill(circle(4.6, -5.2, 2.3), with: .color(Palette.sun))
        let feet = StrokeStyle(lineWidth: 1.6, lineCap: .round)
        context.stroke(line(-3.6, 5.6, -4.9, 7.2), with: .color(Palette.sun), style: feet)
        context.stroke(line(3.6, 5.6, 4.9, 7.2), with: .color(Palette.sun), style: feet)
        context.fill(circle(0, 0.6, 7), with: .color(.white))
        context.fill(circle(0, 0.6, 5.9), with: .color(Palette.sun))
        let hands = StrokeStyle(lineWidth: 1.4, lineCap: .round)
        context.stroke(line(0, 0.6, 0, -2.8), with: .color(.white), style: hands)
        context.stroke(line(0, 0.6, 2.4, 0.6), with: .color(.white), style: hands)
    }

    /// A bed about the point (x, y): two pillows on a mattress with two legs.
    private func drawBed(in context: GraphicsContext, x: Double, y: Double) {
        func block(_ dx: Double, _ dy: Double, _ width: Double, _ height: Double, _ radius: Double) -> Path {
            Path(roundedRect: CGRect(x: x + dx, y: y + dy, width: width, height: height), cornerRadius: radius, style: .circular)
        }
        var bed = Path()
        bed.addPath(block(-6.6, -5.6, 5.8, 3.6, 1.3))
        bed.addPath(block(0.8, -5.6, 5.8, 3.6, 1.3))
        bed.addPath(block(-8, -1.2, 16, 4.8, 1.8))
        bed.addPath(block(-8, 2, 2.2, 3.6, 0.6))
        bed.addPath(block(5.8, 2, 2.2, 3.6, 0.6))
        context.fill(bed, with: .color(Palette.night))
    }

    private func tag(_ index: Int, _ t: Double) -> some View {
        let spot = Metrics.tags[index]
        let pose = M.tagPose(index, scene, t, jumped: jumps[index], still: reduceMotion)
        let emojis = (pose.late ? steps[2] : steps[1]).tags
        // The tail is on the side of the wave; the tag's box holds both.
        let lift = (spot.down ? -1 : 1) * Metrics.tail / 2
        return ZStack {
            RoundedRectangle(cornerRadius: Metrics.tagRadius, style: .continuous)
                .fill(.white)
                .shadow(color: Palette.shadow, radius: 5, y: 3)
                .frame(width: Metrics.tagWidth, height: Metrics.tagHeight)
                .offset(y: lift)
            // A square turned by 45° whose corner sticks out of the tag.
            RoundedRectangle(cornerRadius: 2)
                .fill(.white)
                .frame(width: Metrics.tailSide, height: Metrics.tailSide)
                .rotationEffect(.degrees(45))
                .offset(y: (spot.down ? 1 : -1) * (Metrics.tagHeight - Metrics.tail) / 2)
            Text(index < emojis.count ? emojis[index] : "")
                .font(.system(size: 16))
                .rotationEffect(.degrees(pose.spin))
                .offset(y: lift)
        }
        .frame(width: Metrics.tagWidth, height: Metrics.tagHeight + Metrics.tail)
        // Everything a tag does starts from the tip of its tail.
        .scaleEffect(x: pose.scaleX, y: pose.scaleY, anchor: spot.down ? .bottom : .top)
        .offset(x: pose.x, y: pose.y)
        .opacity(pose.opacity)
        .position(x: spot.x, y: spot.y - lift)
    }

    // MARK: Text and footer

    private func texts(_ t: Double) -> some View {
        ZStack(alignment: .leading) {
            ForEach(Array(steps.prefix(3).enumerated()), id: \.offset) { index, step in
                let pose = M.textPose(index, scene, t, still: reduceMotion)
                VStack(alignment: .leading, spacing: Metrics.bodyGap) {
                    // The lines are the headline's own, so it shrinks before it would wrap.
                    Text(step.headline)
                        .font(.system(size: Metrics.headline, weight: .bold))
                        .tracking(-0.2)
                        .lineSpacing(Metrics.headlineLine - UIFont.systemFont(ofSize: Metrics.headline, weight: .bold).lineHeight)
                        .foregroundStyle(inkColor)
                        .lineLimit(step.headline.split(separator: "\n").count)
                        .minimumScaleFactor(0.7)
                    if let body = step.body {
                        Text(body)
                            .font(.system(size: Metrics.body))
                            .lineSpacing(Metrics.bodyLine - UIFont.systemFont(ofSize: Metrics.body).lineHeight)
                            .foregroundStyle(muteColor)
                            .fixedSize(horizontal: false, vertical: true)
                    }
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(pose.opacity)
                .offset(y: pose.y)
                .accessibilityHidden(index != scene.step)
            }
        }
        .padding(.horizontal, Metrics.margin)
        .frame(maxWidth: .infinity, maxHeight: .infinity)
    }

    private func footer(_ t: Double) -> some View {
        Text(footerLabel)
            .font(.system(size: 14, weight: .semibold))
            .foregroundStyle(accentColor)
            .lineLimit(1)
            .frame(width: Metrics.footerWidth, height: Metrics.footerHeight)
            .background(Capsule().fill(Palette.flash).opacity(M.flashAt(pressed: pressed, until: pressedUntil, t)))
            .accessibilityAddTraits(.isButton)
            .accessibilityAction {
                touches += 1
                advance(.tap)
            }
    }

    // MARK: Behaviour

    /// Seconds on the clock right now.
    private var now: Double { Date.now.timeIntervalSince(origin) }

    /// One touch is a tap anywhere, a tap on a tag, or a finger moving the colour along the wave.
    private func touch(scale: Double) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let far = hypot(value.translation.width, value.translation.height) > Metrics.slop
                if far, !moved {
                    moved = true
                    let y = value.startLocation.y / scale
                    if scene.step == 0, y >= Metrics.scrubBand.lowerBound, y <= Metrics.scrubBand.upperBound {
                        dragging = true
                        touches += 1
                    }
                }
                if dragging { scrub = Metrics.lengthAt(value.location.x / scale) }
            }
            .onEnded { value in
                if !moved { tapped(x: value.location.x / scale, y: value.location.y / scale) }
                moved = false
                dragging = false
            }
    }

    private func tapped(x: Double, y: Double) {
        touches += 1
        let t = now
        if scene.step != 0 {
            for (index, spot) in Metrics.tags.enumerated() {
                let pose = M.tagPose(index, scene, t, jumped: jumps[index], still: reduceMotion)
                if abs(x - spot.x - pose.x) <= Metrics.tagWidth / 2 + 6, abs(y - spot.y - pose.y) <= Metrics.tagHeight / 2 + 8 {
                    jumps[index] = t
                    tapHaptic.impactOccurred()
                    settle()
                    return
                }
            }
        }
        flash(M.flashIn)
        advance(.tap)
    }

    private func advance(_ how: How) {
        goTo((scene.step + 1) % 3, how)
    }

    /// Shows a step. Taps, the loop that plays by itself and the return to rest all come through here.
    private func goTo(_ step: Int, _ how: How) {
        let t = now
        let fill = scene.step == 0 && scrub >= 0 ? scrub : M.fillAt(scene, t)
        scene = EnergyWaveStoryScene(step: step, from: scene.step, at: t, fill: fill)
        scrub = -1
        settle()
        if how == .tap { selectHaptic.selectionChanged() }
        onStepChange?(step)
        if step == 0, how != .rest { onDone?() }
    }

    /// Lights the pill behind the footer for a while.
    private func flash(_ seconds: Double) {
        pressed = now
        pressedUntil = pressed + seconds
        settle()
    }

    /// Runs the timeline until nothing moves any more.
    private func settle() {
        restTask?.cancel()
        resting = false
        settled = max(M.settledAt(scene, still: reduceMotion), pressedUntil + M.flashOut, (jumps.max() ?? 0) + M.spin)
        guard settled.isFinite else { return }
        let wait = settled - now
        restTask = Task { @MainActor in
            try? await Task.sleep(for: .seconds(max(0, wait)))
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
            // The way back to rest takes `returnTime`, and the loop starts from rest.
            goTo(0, .rest)
            start = clock.now + .seconds(M.returnTime)
        }
        while !Task.isCancelled {
            for at in M.autoTaps {
                try? await clock.sleep(until: start + .seconds(at - M.pressFor))
                guard !Task.isCancelled else { return }
                flash(M.pressFor)
                try? await clock.sleep(until: start + .seconds(at))
                guard !Task.isCancelled else { return }
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

private typealias M = EnergyWaveStoryMotion

private enum Palette {
    static let flash = Color(hex: 0xecebf3)
    static let sun = Color(hex: 0xf5b301)
    static let night = Color(hex: 0x4b3fb5)
    static let shadow = Color.rgba(53, 45, 110, 0.14)
}

private enum Metrics {
    /// The header is drawn for a screen 393 points wide and scaled to the screen's width.
    static let designWidth = 393.0
    /// The panel's lower edge at the sides, and how much deeper it hangs in the middle.
    static let panelEdge = 353.0
    static let panelSag = 10.0
    static let sceneHeight = panelEdge + panelSag * 2
    /// The wave rests on this line, and its turning points are joined by half cosines.
    static let base = 200.0
    static let knots: [(x: Double, y: Double)] = [(67, 200), (125, 145), (188, 187), (251, 158), (324, 200)]
    static let waveFrom = knots[0].x
    static let waveTo = knots[knots.count - 1].x
    /// The height of the taller crest: a rise of `flex` points is this share of it.
    static let crest = base - knots[1].y
    static let stroke = 14.0
    static let samples = 96
    static let waveStops = [0.08, 0.5, 0.88]
    static let disc = 34.0
    static let discs = [34.0, 357.0]
    static let tagWidth = 64.0
    static let tagHeight = 38.0
    static let tagRadius = 9.0
    static let tail = 8.0
    static let tailSide = tail * 2.0.squareRoot()
    /// The middle of each tag, and whether its tail points down at the wave.
    static let tags: [(x: Double, y: Double, down: Bool)] = [(126, 95, true), (269, 113, true), (197, 235, false)]
    /// The leading stretch of the colour is a little lighter, in this many layers of white.
    static let head = 30.0
    static let headLayers = 5
    static let headLight = 0.05
    /// A finger within this band, in the header's own points, takes hold of the colour.
    static let scrubBand = 110.0 ... 240.0
    /// A touch that moves less than this is a tap.
    static let slop = 6.0

    static let margin = 30.0
    static let headline = 27.0
    static let headlineLine = 37.0
    static let body = 14.0
    static let bodyLine = 19.0
    static let bodyGap = 7.0
    static let footerWidth = 232.0
    static let footerHeight = 36.0
    static let footerGap = 12.0

    static func waveY(_ x: Double) -> Double {
        var i = 0
        while i < knots.count - 2, x > knots[i + 1].x { i += 1 }
        let (a, b) = (knots[i], knots[i + 1])
        return a.y + (b.y - a.y) * (1 - cos(.pi * (x - a.x) / (b.x - a.x))) / 2
    }

    static let points: [CGPoint] = (0 ... samples).map { i in
        let x = waveFrom + (waveTo - waveFrom) * Double(i) / Double(samples)
        return CGPoint(x: x, y: waveY(x))
    }

    /// The length of the wave up to each sample: the colour is trimmed by length, a finger moves by x.
    static let lengths: [Double] = points.indices.reduce(into: []) { sums, i in
        sums.append(i == 0 ? 0 : sums[i - 1] + hypot(points[i].x - points[i - 1].x, points[i].y - points[i - 1].y))
    }

    static let length = lengths[samples]

    static let wave: Path = {
        var path = Path()
        path.addLines(points)
        return path
    }()

    static let panel: Path = {
        var path = Path()
        path.move(to: .zero)
        path.addLine(to: CGPoint(x: designWidth, y: 0))
        path.addLine(to: CGPoint(x: designWidth, y: panelEdge))
        path.addQuadCurve(to: CGPoint(x: 0, y: panelEdge), control: CGPoint(x: designWidth / 2, y: panelEdge + panelSag * 2))
        path.closeSubpath()
        return path
    }()

    /// The share of the wave's length that lies left of x.
    static func lengthAt(_ x: Double) -> Double {
        let place = min(1, max(0, (x - waveFrom) / (waveTo - waveFrom))) * Double(samples)
        let i = min(samples - 1, Int(place))
        return (lengths[i] + (lengths[i + 1] - lengths[i]) * (place - Double(i))) / length
    }
}

#Preview {
    EnergyWaveStory(autoPlay: true)
}
