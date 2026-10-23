import SwiftUI

private typealias W = StemWaveCyclesWave

private let defaultBackground = Color(hex: 0xe7e6f0)
private let defaultInk = Color(hex: 0x1b1b1f)
private let curveInk = Color(hex: 0x6e6e78)
private let lineInk = Color(hex: 0x8a8a92)

private let pitch = 17.0
private let stripHeight = 14.0
private let rowHeight = 8.0
private let overlap = 2.0
private let dotRadius = 2.5
private let defaultAmplitude = 120.0
// The strip's centre, as a share of the screen's height.
private let axisAt = 0.46
private let captionGap = 40.0
// The pinch's reach: the zoom may go this far past one period, or below the stems' period.
private let zoomMin = -0.6
private let zoomMax = 1.6
private let dragSlop = 8.0

private let userHold = 3.0
private let settleFor = 0.6

/// A number moving from one value to another over a time span: linear for the loop's clock, cubic
/// in-out for the hand's turn and stretch easing away.
private struct StemWaveCyclesRamp {
    var from: Double
    var to: Double
    var start = Date.distantPast
    var duration = 0.0
    var cubic = false

    init(_ value: Double) {
        from = value
        to = value
    }

    init(from: Double, to: Double, start: Date, duration: Double, cubic: Bool) {
        self.from = from
        self.to = to
        self.start = start
        self.duration = duration
        self.cubic = cubic
    }

    func value(at date: Date) -> Double {
        guard duration > 0 else { return to }
        let u = min(1, max(0, date.timeIntervalSince(start) / duration))
        let eased = !cubic ? u : (u < 0.5 ? 4 * u * u * u : 1 - pow(-2 * u + 2, 3) / 2)
        return from + (to - from) * eased
    }
}

/// An abstract chart of a cycle: a lollipop chart of stems on a strip of colour blocks, the stems
/// rolling as a wave. It dissolves into a single sine curve through the dots, zooms to one period
/// with its crest and trough marked, and returns to stems. Drag to turn the wave, pinch to change
/// its period, tap to switch between the stems and the curve.
struct StemWaveCycles: View {
    var caption = "Economic cycles"
    /// Peak height of the wave in points.
    var amplitude = defaultAmplitude
    /// Stems per period while the stems stand.
    var periodStems = 12.0
    var background = defaultBackground
    var ink = defaultInk
    /// Plays stems → curve → one period → stems by itself, every LOOP seconds.
    var autoPlay = false
    /// Called when a tap switches between the stems and the curve.
    var onModeChange: ((StemWaveCyclesMode) -> Void)? = nil

    static let loop = W.loop

    @Environment(\.accessibilityReduceMotion) private var still

    // The loop's clock: running from `origin` while automatic, else `clock`. A hand freezes it and
    // turns (`phase`) or stretches (`zoom`) the wave on top.
    @State private var automatic = false
    @State private var started = false
    @State private var origin = Date.now
    @State private var clock = StemWaveCyclesRamp(0)
    @State private var phase = StemWaveCyclesRamp(0)
    @State private var zoom = StemWaveCyclesRamp(0)
    /// The end of the tap's play to the curve or back, and whether it returns to the stems.
    @State private var toggleEnd: (date: Date, toStems: Bool)? = nil
    /// When the last hand let go; nil while a finger is down or a tap's play runs.
    @State private var releasedAt: Date? = nil
    @State private var generation = 0
    @State private var dragFrom: Double? = nil
    @State private var pinchFrom: Double? = nil
    @State private var quarter = 0
    // The frame clock runs only while something moves; otherwise the picture is drawn at `sampleAt`.
    @State private var autoMoving = false
    @State private var handMoving = false
    @State private var sampleAt = Date.now
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { outer in
            let bottom = outer.safeAreaInsets.bottom
            ZStack(alignment: .bottom) {
                GeometryReader { proxy in
                    let look = makeLook(proxy.size)
                    TimelineView(.animation(paused: !(automatic ? autoMoving : handMoving))) { timeline in
                        let date = (automatic ? autoMoving : handMoving) ? timeline.date : sampleAt
                        Canvas { c, _ in
                            let t = time(at: date)
                            let scene = W.timeline(t, still: still)
                            let z = min(zoomMax, max(zoomMin, scene.zoom + zoom.value(at: date)))
                            W.draw(c, t: t, phase: scene.phase + phase.value(at: date), zoom: z, still: still, look: look)
                        }
                    }
                    .contentShape(Rectangle())
                    .gesture(drag(look).simultaneously(with: pinch(look)))
                    .onTapGesture { toggle() }
                }
                .ignoresSafeArea()
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel(caption)
                .accessibilityHint("Switches between the stems and the curve")
                .accessibilityAction { toggle() }

                Text(caption)
                    .font(.system(size: 17))
                    .tracking(-0.2)
                    .foregroundStyle(ink)
                    .frame(maxWidth: .infinity, minHeight: 22)
                    .multilineTextAlignment(.center)
                    .padding(.bottom, max(bottom, 16) + captionGap - bottom)
                    .allowsHitTesting(false)
                    .accessibilityHidden(true)
            }
        }
        .background(background.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            haptic.prepare()
        }
        .task(id: generation) { await drive() }
    }

    private func makeLook(_ size: CGSize) -> W.Look {
        W.Look(
            width: size.width, height: size.height, axis: size.height * axisAt,
            amplitude: min(amplitude, size.height * 0.16), pitch: pitch, periodStems: periodStems,
            strip: stripHeight, row: rowHeight, overlap: overlap, dot: dotRadius,
            ink: ink, curveInk: curveInk, lineInk: lineInk
        )
    }

    private func time(at date: Date) -> Double {
        automatic ? max(0, date.timeIntervalSince(origin)).truncatingRemainder(dividingBy: W.loop) : clock.value(at: date)
    }

    /// The zoom on screen, where a drag measures one period.
    private func zoomNow(_ date: Date) -> Double {
        min(zoomMax, max(zoomMin, W.timeline(time(at: date), still: still).zoom + zoom.value(at: date)))
    }

    /// A hand stops the loop where it is and the hand's own easing away.
    private func touch(_ now: Date) {
        if automatic {
            clock = StemWaveCyclesRamp(time(at: now))
            automatic = false
            autoMoving = false
        }
        phase = StemWaveCyclesRamp(phase.value(at: now))
        zoom = StemWaveCyclesRamp(zoom.value(at: now))
        handMoving = toggleEnd != nil
        sampleAt = now
        releasedAt = nil
        generation += 1
    }

    private func release() {
        guard dragFrom == nil, pinchFrom == nil else { return }
        releasedAt = toggleEnd == nil ? .now : nil
        generation += 1
    }

    /// Plays on to the zoomed curve, or from it back to the stems.
    private func toggle() {
        let now = Date.now
        touch(now)
        let at = clock.value(at: now)
        let curve = at >= W.retractStart && at < W.stemsBack
        let from = curve ? max(at, W.returnStart) : W.retractStart
        let to = curve ? W.stemsBack : W.curveHeld
        clock = StemWaveCyclesRamp(from: from, to: to, start: now, duration: to - from, cubic: false)
        toggleEnd = (now.addingTimeInterval(to - from), curve)
        handMoving = true
        haptic.selectionChanged()
        haptic.prepare()
        onModeChange?(curve ? .stems : .curve)
    }

    private func drag(_ look: W.Look) -> some Gesture {
        DragGesture(minimumDistance: dragSlop)
            .onChanged { value in
                let now = Date.now
                if dragFrom == nil {
                    touch(now)
                    dragFrom = phase.value(at: now)
                    quarter = Int(floor(phase.value(at: now) / (.pi / 2)))
                }
                // The wave follows the finger: a drag of one period turns it one whole cycle.
                let base = pitch * periodStems
                let period = base * exp(log(look.width / base) * zoomNow(now))
                let turned = (dragFrom ?? 0) - value.translation.width / period * .pi * 2
                phase = StemWaveCyclesRamp(turned)
                // A selection tick each quarter turn.
                let q = Int(floor(turned / (.pi / 2)))
                if q != quarter {
                    quarter = q
                    haptic.selectionChanged()
                    haptic.prepare()
                }
            }
            .onEnded { _ in
                dragFrom = nil
                release()
            }
    }

    private func pinch(_ look: W.Look) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                let now = Date.now
                if pinchFrom == nil {
                    touch(now)
                    pinchFrom = zoom.value(at: now)
                }
                let reach = log(look.width / (pitch * periodStems))
                zoom = StemWaveCyclesRamp((pinchFrom ?? 0) + log(max(0.05, value.magnification)) / reach)
            }
            .onEnded { _ in
                pinchFrom = nil
                release()
            }
    }

    private func wait(_ date: Date) async throws {
        while date > .now { try await Task.sleep(for: .seconds(date.timeIntervalSinceNow), tolerance: .zero) }
        try Task.checkCancellation()
    }

    /// Finishes a tap's play, waits out the hand, eases its turn and stretch away and runs the loop.
    private func drive() async {
        do {
            if autoPlay && !started {
                // The first run starts the loop from rest at once.
                started = true
                origin = .now
                sampleAt = origin
                automatic = true
            }
            if !automatic, let end = toggleEnd {
                try await wait(end.date)
                if end.toStems { clock = StemWaveCyclesRamp(0) }
                toggleEnd = nil
                handMoving = false
                sampleAt = end.date
                if dragFrom == nil && pinchFrom == nil { releasedAt = end.date }
            }
            guard autoPlay else { return }
            if !automatic {
                guard let released = releasedAt else { return }
                // The hand's turn and stretch ease away (the turn to the nearest whole cycle), then
                // the loop plays on from where it was stopped and comes round to rest.
                let settleAt = released.addingTimeInterval(userHold)
                try await wait(settleAt)
                let turns = (phase.value(at: settleAt) / (.pi * 2)).rounded() * .pi * 2
                phase = StemWaveCyclesRamp(from: phase.value(at: settleAt), to: turns, start: settleAt, duration: settleFor, cubic: true)
                zoom = StemWaveCyclesRamp(from: zoom.value(at: settleAt), to: 0, start: settleAt, duration: settleFor, cubic: true)
                handMoving = true
                let resume = settleAt.addingTimeInterval(settleFor)
                try await wait(resume)
                phase = StemWaveCyclesRamp(0)
                zoom = StemWaveCyclesRamp(0)
                handMoving = false
                origin = resume.addingTimeInterval(-clock.value(at: resume))
                automatic = true
            }
            // The picture is still from the loop's end to the start of the roll.
            while true {
                let start = origin.addingTimeInterval(floor(Date.now.timeIntervalSince(origin) / W.loop) * W.loop)
                let rollAt = start.addingTimeInterval(W.rollStart)
                if Date.now < rollAt {
                    autoMoving = false
                    sampleAt = start.addingTimeInterval(W.rollStart / 2)
                    try await wait(rollAt)
                }
                autoMoving = true
                try await wait(start.addingTimeInterval(W.loop))
            }
        } catch {}
    }
}

enum StemWaveCyclesMode {
    case stems, curve
}
