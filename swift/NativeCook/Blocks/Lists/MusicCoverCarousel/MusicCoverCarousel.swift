import SwiftUI
import UIKit

struct MusicCoverCarousel: View {
    static let loop = 7.0
    var autoPlay = false
    var freezeAt: Double?

    init(autoPlay: Bool = false, freezeAt: Double? = nil) {
        self.autoPlay = autoPlay
        self.freezeAt = freezeAt
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @Environment(\.scenePhase) private var scenePhase
    @State private var autoStart: Date?
    @State private var manualFlow = MusicCoverCarouselTokens.start
    @State private var transition: MusicCoverCarouselTransition?
    @State private var dragging = false
    @State private var contactActive = false
    @State private var dragStartFlow = MusicCoverCarouselTokens.start
    @State private var current = Int(MusicCoverCarouselTokens.start)
    @State private var pressedCard: Int?
    @State private var playing = true
    @State private var previousPulse: Date?
    @State private var nextPulse: Date?
    @State private var manualChangedAt = Date.now.addingTimeInterval(-1.4)
    @State private var resumeWork: DispatchWorkItem?
    @State private var finishWork: DispatchWorkItem?
    @State private var feedback = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            let running = freezeAt == nil && !reduceMotion && scenePhase == .active && (autoStart != nil || transition != nil || dragging)
            TimelineView(.animation(paused: !running)) { timeline in
                stage(size: size, date: timeline.date)
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(Color.musicCoverCarousel(hex: 0x2A1607).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .statusBarHidden(false)
        .onAppear {
            feedback.prepare()
            if let freezeAt {
                current = min(
                    MusicCoverCarouselTokens.tracks.count - 1,
                    max(0, Int(musicCoverCarouselFlow(at: freezeAt / 1_000).rounded()))
                )
            } else if autoPlay, !reduceMotion {
                autoStart = .now
            }
        }
        .onChange(of: reduceMotion) { _, reduced in
            resumeWork?.cancel()
            finishWork?.cancel()
            let selected = min(
                Double(MusicCoverCarouselTokens.tracks.count - 1),
                max(0, displayedFlow(at: .now).rounded())
            )
            transition = nil
            autoStart = nil
            manualFlow = reduced ? selected : MusicCoverCarouselTokens.start
            current = Int(manualFlow)
            if !reduced, autoPlay, freezeAt == nil { autoStart = .now }
        }
        .onChange(of: autoPlay) { _, enabled in
            resumeWork?.cancel()
            finishWork?.cancel()
            guard freezeAt == nil, !reduceMotion, enabled else {
                autoStart = nil
                return
            }
            transition = nil
            manualFlow = MusicCoverCarouselTokens.start
            current = Int(MusicCoverCarouselTokens.start)
            autoStart = .now
        }
        .onDisappear {
            resumeWork?.cancel()
            finishWork?.cancel()
        }
    }

    private func stage(size: CGSize, date: Date) -> some View {
        let flow = displayedFlow(at: date)
        let index = min(MusicCoverCarouselTokens.tracks.count - 1, max(0, Int(flow.rounded())))
        let track = MusicCoverCarouselTokens.tracks[freezeAt == nil ? current : index]
        let px: (Double) -> Double = { ($0 - MusicCoverCarouselTokens.pinCenterX) * MusicCoverCarouselTokens.scale + size.width / 2 }
        let py: (Double) -> Double = { ($0 - MusicCoverCarouselTokens.pinCenterY) * MusicCoverCarouselTokens.scale + size.height / 2 }
        let cardY = py(MusicCoverCarouselTokens.cardCenterY)
        let iconY = py(MusicCoverCarouselTokens.iconY)

        return ZStack(alignment: .topLeading) {
            backdrop(size: size, flow: flow)
            roomShadows(size: size, px: px, py: py)
            cards(size: size, flow: flow, cardY: cardY, centerX: px(MusicCoverCarouselTokens.cardCenterX))
            playerBar(px: px, py: py)
            iconButton(.rewind, "Previous", CGPoint(x: px(MusicCoverCarouselTokens.previousX), y: iconY), max(pressAmount(previousPulse, at: date), scriptedPress(at: date, kind: .previous))) {
                let now = Date.now
                takeOver(at: now)
                previousPulse = now
                go(to: flow.rounded() - 1, at: now)
                scheduleResume()
            }
            playPauseButton(center: CGPoint(x: px(MusicCoverCarouselTokens.pauseX), y: iconY)) {
                takeOver(at: .now)
                playing.toggle()
                scheduleResume()
            }
            iconButton(.forward, "Next", CGPoint(x: px(MusicCoverCarouselTokens.nextX), y: iconY), max(pressAmount(nextPulse, at: date), scriptedPress(at: date, kind: .next))) {
                let now = Date.now
                takeOver(at: now)
                nextPulse = now
                go(to: flow.rounded() + 1, at: now)
                scheduleResume()
            }
            nowPlaying(track: track, date: date, px: px, py: py)
            iconButton(.lyrics, "Lyrics", CGPoint(x: px(MusicCoverCarouselTokens.lyricsX), y: iconY)) { takeOver(at: .now); scheduleResume() }
            iconButton(.list, "Queue", CGPoint(x: px(MusicCoverCarouselTokens.listX), y: iconY)) { takeOver(at: .now); scheduleResume() }
            iconButton(.volume, "Volume", CGPoint(x: px(MusicCoverCarouselTokens.volumeX), y: iconY)) { takeOver(at: .now); scheduleResume() }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
        .allowsHitTesting(freezeAt == nil)
        .onChange(of: index) { _, newValue in
            guard freezeAt == nil else { return }
            manualChangedAt = .now
            withAnimation(.easeIn(duration: 0.18)) { current = newValue }
        }
    }

    private func backdrop(size: CGSize, flow: Double) -> some View {
        ZStack {
            ForEach(Array(MusicCoverCarouselTokens.tracks.enumerated()), id: \.element.id) { index, track in
                ZStack {
                    LinearGradient(colors: track.wash, startPoint: .leading, endPoint: .trailing)
                    MusicCoverCarouselImage(name: track.image)
                        .scaledToFill()
                        .frame(width: size.width, height: size.height)
                        .scaleEffect(1.5)
                        .blur(radius: 60)
                        .opacity(0.12)
                }
                .frame(width: size.width, height: size.height)
                .opacity(1 - min(1, abs(Double(index) - flow)))
            }
        }
        .frame(width: size.width, height: size.height)
    }

    private func roomShadows(size: CGSize, px: @escaping (Double) -> Double, py: @escaping (Double) -> Double) -> some View {
        Canvas { context, _ in
            drawRadial(&context, center: CGPoint(x: px(301), y: py(160)), radii: CGSize(width: 185 * 0.615, height: 140 * 0.615), color: .black, stops: [(0, 1), (0.5, 0.75), (1, 0)])
            drawRadial(&context, center: CGPoint(x: px(375), y: py(385)), radii: CGSize(width: 180 * 0.615, height: 45 * 0.615), color: .black, stops: [(0, 0.95), (0.5, 0.665), (1, 0)])
            drawRadial(&context, center: CGPoint(x: px(250), y: py(410)), radii: CGSize(width: 190 * 0.615, height: 95 * 0.615), color: .black, stops: [(0, 0.92), (0.55, 0.736), (1, 0)])
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }

    private func drawRadial(
        _ context: inout GraphicsContext,
        center: CGPoint,
        radii: CGSize,
        color: Color,
        stops: [(Double, Double)]
    ) {
        context.drawLayer { layer in
            layer.scaleBy(x: 1, y: radii.height / radii.width)
            let transformedCenter = CGPoint(x: center.x, y: center.y * radii.width / radii.height)
            let rect = CGRect(x: center.x - radii.width, y: transformedCenter.y - radii.width, width: radii.width * 2, height: radii.width * 2)
            layer.fill(Path(ellipseIn: rect), with: .radialGradient(
                Gradient(stops: stops.map { .init(color: color.opacity($0.1), location: $0.0) }),
                center: transformedCenter,
                startRadius: 0,
                endRadius: radii.width
            ))
        }
    }

    private func cards(size: CGSize, flow: Double, cardY: Double, centerX: Double) -> some View {
        let cardWidth = MusicCoverCarouselTokens.cardWidth * 0.615
        let cardHeight = MusicCoverCarouselTokens.cardHeight * 0.615
        return ZStack(alignment: .topLeading) {
            ForEach(Array(MusicCoverCarouselTokens.tracks.enumerated()), id: \.element.id) { index, track in
                let p = cardPlacement(index: index, flow: flow)
                MusicCoverCarouselCard(
                    track: track,
                    index: index,
                    flow: flow,
                    reducedMotion: reduceMotion,
                    textOpacity: 1 - 0.25 * ramp(abs(Double(index) - flow), 0, 1)
                )
                    .frame(width: cardWidth, height: cardHeight, alignment: .topLeading)
                    .scaleEffect(p.scale * (pressedCard == index ? 0.97 : 1))
                    .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.12), value: pressedCard)
                    .rotation3DEffect(.degrees(-p.turn), axis: (x: 0, y: 1, z: 0), perspective: cardWidth / 700)
                    .opacity(p.opacity)
                    .position(x: centerX + p.x, y: cardY)
                    .zIndex(p.z)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("\(track.title) by \(track.artist)")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityAction {
                        let now = Date.now
                        takeOver(at: now)
                        go(to: Double(index), at: now)
                        scheduleResume()
                    }
            }
            MusicCoverCarouselTouchSurface(
                onBegin: { x, y in
                    beginContact(at: .now)
                    dragStartFlow = displayedFlow(at: .now)
                    dragging = false
                    pressedCard = hitCard(x: x, y: y, flow: dragStartFlow, centerX: centerX)
                    return x
                },
                onChange: { startX, startY, x, y in
                    let dx = x - startX
                    if abs(y - startY) > 16 { pressedCard = nil }
                    guard abs(dx) >= 8 || dragging else { return }
                    dragging = true
                    pressedCard = nil
                    let raw = dragStartFlow - dx / (108 * 0.615)
                    let last = Double(MusicCoverCarouselTokens.tracks.count - 1)
                    let over = raw < 0 ? raw : (raw > last ? raw - last : 0)
                    manualFlow = raw - over + over * 0.3
                },
                onEnd: { _, _, _, velocity in
                    let wasDragging = dragging
                    let tappedCard = pressedCard
                    dragging = false
                    pressedCard = nil
                    if wasDragging {
                        let v = -velocity / (108 * 0.615)
                        let flick = abs(velocity) / 1000 > 0.11 ? (v < 0 ? -1.0 : 1.0) : 0
                        let target = flick != 0 ? dragStartFlow.rounded() + flick : (manualFlow + v * 0.15).rounded()
                        go(to: target, velocity: v, at: .now)
                    } else if let tappedCard {
                        go(to: Double(tappedCard), at: .now)
                    }
                    endContact()
                }
            )
            .frame(width: size.width, height: 280)
            .position(x: size.width / 2, y: cardY)
            .zIndex(200)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func hitCard(x: Double, y: Double, flow: Double, centerX: Double) -> Int? {
        let cardWidth = MusicCoverCarouselTokens.cardWidth * MusicCoverCarouselTokens.scale
        let cardHeight = MusicCoverCarouselTokens.cardHeight * MusicCoverCarouselTokens.scale
        return MusicCoverCarouselTokens.tracks.indices
            .sorted { abs(Double($0) - flow) < abs(Double($1) - flow) }
            .first { index in
                let placement = cardPlacement(index: index, flow: flow)
                let halfWidth = cardWidth * placement.scale * abs(cos(placement.turn * .pi / 180)) / 2
                let halfHeight = cardHeight * placement.scale / 2
                return abs(x - (centerX + placement.x)) <= halfWidth && abs(y - 140) <= halfHeight
            }
    }

    private func cardPlacement(index: Int, flow: Double) -> MusicCoverCarouselCardPlacement {
        let distance = Double(index) - flow
        let amount = min(3, abs(distance))
        let side = distance < 0 ? -1.0 : 1.0
        let slots = side > 0 ? MusicCoverCarouselTokens.slotsRight : MusicCoverCarouselTokens.slots
        let x = side * interpolate(amount, [0, 1, 2, 3], [0, slots[1], slots[2], slots[2] + 40]) * 0.615
        let scale = reduceMotion ? 1 : interpolate(amount, [0, 1, 2, 3], [1, 0.86, 0.78, 0.7])
        let turn = reduceMotion ? 0 : side * interpolate(amount, [0, 1, 2, 3], [0, 12, 20, 34])
        return MusicCoverCarouselCardPlacement(
            x: reduceMotion ? distance.rounded() * 108 * 0.615 : x,
            scale: scale,
            turn: turn,
            opacity: reduceMotion ? 1 - ramp(amount, 0, 3) * 0.6 : 1 - ramp(amount, 2.2, 3),
            z: (100 - amount * 10).rounded()
        )
    }

    private func playerBar(px: @escaping (Double) -> Double, py: @escaping (Double) -> Double) -> some View {
        let token = MusicCoverCarouselTokens.player
        return ZStack {
            MusicCoverCarouselBlur(style: .systemUltraThinMaterialLight, intensity: 0.4)
            Color(red: 150 / 255, green: 92 / 255, blue: 30 / 255).opacity(0.2)
        }
        .frame(width: token.width * 0.615, height: token.height * 0.615)
        .clipShape(Capsule())
        .overlay(Capsule().stroke(.white.opacity(0.38), lineWidth: 2 / displayScale))
        .position(x: px(token.x) + token.width * 0.615 / 2, y: py(token.y) + token.height * 0.615 / 2)
    }

    private func nowPlaying(track: MusicCoverCarouselTrack, date: Date, px: @escaping (Double) -> Double, py: @escaping (Double) -> Double) -> some View {
        let token = MusicCoverCarouselTokens.capsule
        let width = token.width * 0.615
        let height = token.height * 0.615
        return ZStack(alignment: .topLeading) {
            Color(red: 16 / 255, green: 14 / 255, blue: 13 / 255).opacity(0.78)
            ZStack(alignment: .topLeading) {
                MusicCoverCarouselImage(name: track.image)
                    .scaledToFill()
                    .frame(width: 38 * 0.615, height: 38 * 0.615)
                    .clipShape(RoundedRectangle(cornerRadius: 7 * 0.615, style: .continuous))
                    .position(x: (254 - token.x + 19) * 0.615, y: (424 - token.y + 19) * 0.615)
                Text(track.title)
                    .font(.system(size: 12.5 * 0.615, weight: .semibold))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .frame(width: 80, height: 11, alignment: .leading)
                    .position(x: (300 - token.x) * 0.615 + 40, y: (436 - token.y) * 0.615 - 0.5)
                Text(track.artist)
                    .font(.system(size: 10.5 * 0.615))
                    .foregroundStyle(.white.opacity(0.72))
                    .lineLimit(1)
                    .frame(width: 80, height: 10, alignment: .leading)
                    .position(x: (300 - token.x) * 0.615 + 40, y: (452 - token.y) * 0.615)
            }
            .frame(width: width, height: height, alignment: .topLeading)
            .id(track.id)
            .transition(.asymmetric(
                insertion: .opacity.animation(.easeIn(duration: 0.18)),
                removal: .opacity.animation(.easeOut(duration: 0.12))
            ))
            Capsule().fill(.white.opacity(0.84))
                .frame(width: 106 * 0.615, height: 1.5)
                .overlay(alignment: .leading) { Capsule().fill(.white).frame(width: 106 * 0.615 * progressAmount(at: date)) }
                .position(x: (254 - token.x + 53) * 0.615, y: (464.2 - token.y) * 0.615 + 0.75)
            MusicCoverCarouselGlyph(kind: .wave, color: .white).frame(width: 10, height: 5)
                .position(x: (442.8 - token.x) * 0.615, y: height / 2)
            MusicCoverCarouselGlyph(kind: .more, color: .white).frame(width: 12, height: 12)
                .position(x: (470 - token.x) * 0.615, y: height / 2)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 12 * 0.615, style: .continuous))
        .position(x: px(token.x) + width / 2, y: py(token.y) + height / 2)
    }

    private func playPauseButton(center: CGPoint, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            ZStack {
                MusicCoverCarouselGlyph(kind: .pause, color: .white)
                    .frame(width: 8.7, height: 11.7)
                    .opacity(playing ? 1 : 0)
                    .scaleEffect(playing ? 1 : 0.7)
                MusicCoverCarouselGlyph(kind: .play, color: .white)
                    .frame(width: 8.7, height: 11.7)
                    .opacity(playing ? 0 : 1)
                    .scaleEffect(playing ? 0.7 : 1)
            }
            .frame(width: 44, height: 44)
            .contentShape(Rectangle())
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.2), value: playing)
        }
        .buttonStyle(MusicCoverCarouselPressButtonStyle())
        .simultaneousGesture(controlTouchGesture)
        .position(center)
        .accessibilityLabel(playing ? "Pause" : "Play")
    }

    private func iconButton(
        _ kind: MusicCoverCarouselGlyph.Kind,
        _ label: String,
        _ center: CGPoint,
        _ pulse: Double = 0,
        action: @escaping () -> Void
    ) -> some View {
        Button(action: action) {
            MusicCoverCarouselGlyph(kind: kind, color: .white)
                .frame(width: kind.width, height: kind.height)
                .opacity(1 - 0.4 * pulse)
                .scaleEffect(1 - 0.03 * pulse)
                .frame(width: 44, height: 44)
                .contentShape(Rectangle())
        }
        .buttonStyle(MusicCoverCarouselPressButtonStyle())
        .simultaneousGesture(controlTouchGesture)
        .position(center)
        .accessibilityLabel(label)
    }

    private func displayedFlow(at date: Date) -> Double {
        if let freezeAt {
            return musicCoverCarouselFlow(at: freezeAt / 1_000)
        }
        if dragging { return manualFlow }
        if let transition {
            let elapsed = date.timeIntervalSince(transition.started)
            if transition.reduced {
                let unit = min(1, max(0, elapsed / transition.duration))
                return transition.from + (transition.to - transition.from) * easeOut(unit)
            }
            return MusicCoverCarouselSpring.position(
                from: transition.from,
                to: transition.to,
                velocity: transition.velocity,
                elapsed: elapsed,
                dampingRatio: 0.8,
                stiffness: transition.stiffness
            )
        }
        if let autoStart, date >= autoStart {
            return musicCoverCarouselFlow(at: date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop))
        }
        return manualFlow
    }

    private func takeOver(at date: Date) {
        resumeWork?.cancel()
        finishWork?.cancel()
        let value = displayedFlow(at: date)
        if let autoStart, date >= autoStart {
            let clock = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            let playhead = musicCoverCarouselPlayhead(at: clock)
            let elapsed = (playhead - MusicCoverCarouselTokens.playheadFrom) /
                MusicCoverCarouselTokens.playheadPerLoop * Self.loop
            manualChangedAt = date.addingTimeInterval(-elapsed)
        }
        autoStart = nil
        transition = nil
        manualFlow = value
    }

    private func beginContact(at date: Date) {
        contactActive = true
        takeOver(at: date)
    }

    private func endContact() {
        contactActive = false
        scheduleResume()
    }

    private var controlTouchGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !contactActive else { return }
                beginContact(at: .now)
            }
            .onEnded { _ in endContact() }
    }

    private func go(to proposed: Double, velocity: Double = 0, at date: Date) {
        let target = min(Double(MusicCoverCarouselTokens.tracks.count - 1), max(0, proposed))
        let from = displayedFlow(at: date)
        manualFlow = from
        if reduceMotion {
            transition = nil
            manualFlow = target
            current = Int(target.rounded())
            if autoStart == nil {
                feedback.selectionChanged()
                feedback.prepare()
            }
            return
        }
        transition = MusicCoverCarouselTransition(
            from: from,
            to: target,
            velocity: velocity,
            started: date,
            duration: reduceMotion ? 0.2 : 0.4,
            reduced: reduceMotion,
            stiffness: MusicCoverCarouselSpring.stiffness(
                displacement: from - target,
                velocity: MusicCoverCarouselSpring.towardVelocity(from: from, to: target, velocity: velocity),
                duration: 0.4,
                dampingRatio: 0.8
            )
        )
        if autoStart == nil {
            feedback.selectionChanged()
            feedback.prepare()
        }
        finishWork?.cancel()
        let work = DispatchWorkItem {
            manualFlow = target
            transition = nil
        }
        finishWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + (reduceMotion ? 0.2 : 0.7), execute: work)
    }

    private func scheduleResume() {
        guard autoPlay, !reduceMotion, freezeAt == nil, !contactActive else { return }
        resumeWork?.cancel()
        let work = DispatchWorkItem {
            guard autoPlay, !reduceMotion, freezeAt == nil, !contactActive else { return }
            let now = Date.now
            transition = MusicCoverCarouselTransition(
                from: displayedFlow(at: now),
                to: MusicCoverCarouselTokens.start,
                velocity: 0,
                started: now,
                duration: 0.3,
                reduced: true,
                stiffness: 0
            )
            playing = true
            let restart = DispatchWorkItem {
                guard autoPlay, !reduceMotion, freezeAt == nil, !contactActive else { return }
                manualFlow = MusicCoverCarouselTokens.start
                transition = nil
                autoStart = .now
            }
            finishWork = restart
            DispatchQueue.main.asyncAfter(deadline: .now() + 0.32, execute: restart)
        }
        resumeWork = work
        DispatchQueue.main.asyncAfter(deadline: .now() + 3.5, execute: work)
    }

    private func progressAmount(at date: Date) -> Double {
        if let freezeAt {
            return musicCoverCarouselPlayhead(at: freezeAt / 1_000)
        }
        if let autoStart, date >= autoStart {
            let clock = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
            return musicCoverCarouselPlayhead(at: clock)
        }
        let elapsed = max(0, date.timeIntervalSince(manualChangedAt))
        return min(1, MusicCoverCarouselTokens.playheadFrom + MusicCoverCarouselTokens.playheadPerLoop * elapsed / Self.loop)
    }

    private func pressAmount(_ start: Date?, at date: Date) -> Double {
        guard let start else { return 0 }
        let elapsed = date.timeIntervalSince(start)
        if elapsed < 0 || elapsed >= 0.24 { return 0 }
        return elapsed < 0.12 ? easeOut(elapsed / 0.12) : 1 - easeOut((elapsed - 0.12) / 0.12)
    }

    private func scriptedPress(at date: Date, kind: MusicCoverCarouselPressKind) -> Double {
        if let freezeAt {
            return musicCoverCarouselPress(at: freezeAt / 1_000, kind: kind)
        }
        guard let autoStart, date >= autoStart else { return 0 }
        let clock = date.timeIntervalSince(autoStart).truncatingRemainder(dividingBy: Self.loop)
        return musicCoverCarouselPress(at: clock, kind: kind)
    }

    private func ramp(_ value: Double, _ start: Double, _ end: Double) -> Double {
        min(1, max(0, (value - start) / (end - start)))
    }

    private func interpolate(_ value: Double, _ input: [Double], _ output: [Double]) -> Double {
        for index in 0..<(input.count - 1) where value <= input[index + 1] {
            let amount = (value - input[index]) / (input[index + 1] - input[index])
            return output[index] + (output[index + 1] - output[index]) * amount
        }
        return output.last ?? 0
    }

    private func easeOut(_ value: Double) -> Double {
        MusicCoverCarouselBezier.solve(value, x1: 0.23, y1: 1, x2: 0.32, y2: 1)
    }

}

private enum MusicCoverCarouselTokens {
    static let scale = 0.615
    // Kept for value-for-value parity with the approved motion exports, even though the RN view does not consume them.
    static let easeInOut = (x1: 0.77, y1: 0.0, x2: 0.175, y2: 1.0)
    static let settleDuration = 0.4
    static let settleDampingRatio = 1.0
    static let pressDuration = 0.12
    static let pressScale = 0.97
    static let pressDim = 0.6
    static let sheenAlpha = 0.4
    static let sheenWidth = 0.46
    static let sheenSlant = 18.0
    static let sheenSpan = 40.0
    static let playheadFrom = 0.1
    static let playheadPerLoop = 0.6
    static let pinCenterX = 368.0
    static let pinCenterY = 282.0
    static let start = 2.0
    static let cardWidth = 215.0
    static let cardHeight = 269.0
    static let cardCenterX = 360.5
    static let cardCenterY = 222.0
    static let slots = [0.0, 108.0, 176.0]
    static let slotsRight = [0.0, 113.0, 184.0]
    static let player = MusicCoverCarouselRect(x: 46, y: 409, width: 644, height: 68)
    static let previousX = 92.4
    static let pauseX = 151.2
    static let nextX = 211.1
    static let iconY = 443.0
    static let iconSize = 22.0
    static let capsule = MusicCoverCarouselRect(x: 248, y: 418, width: 240, height: 50)
    static let lyricsX = 531.9
    static let listX = 587.2
    static let volumeX = 643.1
    static let tracks = [
        MusicCoverCarouselTrack(id: "braids", title: "Dusk Signal", artist: "Gianni Rowe", image: "MusicCoverCarousel-braids.jpg", glow: .musicCoverCarousel(hex: 0x1E3A3C), wash: [.musicCoverCarousel(hex: 0x2E5A5D), .musicCoverCarousel(hex: 0x1E3A3C), .musicCoverCarousel(hex: 0x142829)]),
        MusicCoverCarouselTrack(id: "velvet", title: "Velvet Hours", artist: "Ritva Lane", image: "MusicCoverCarousel-velvet.jpg", glow: .musicCoverCarousel(hex: 0x6E6A66), wash: [.musicCoverCarousel(hex: 0x8E8984), .musicCoverCarousel(hex: 0x6E6A66), .musicCoverCarousel(hex: 0x55514E)]),
        MusicCoverCarouselTrack(id: "ember", title: "Ember Lights", artist: "Thea Vance", image: "MusicCoverCarousel-ember.jpg", glow: .musicCoverCarousel(hex: 0xF2A63C), wash: [.musicCoverCarousel(hex: 0xE39631), .musicCoverCarousel(hex: 0xC47516), .musicCoverCarousel(hex: 0xAE5F08)]),
        MusicCoverCarouselTrack(id: "satin", title: "Opera 1 Theme", artist: "Quinn Tyler", image: "MusicCoverCarousel-satin.jpg", glow: .musicCoverCarousel(hex: 0x8A8580), wash: [.musicCoverCarousel(hex: 0xA39E98), .musicCoverCarousel(hex: 0x8A8580), .musicCoverCarousel(hex: 0x6C6864)]),
        MusicCoverCarouselTrack(id: "drive", title: "Night Drive (Chennai)", artist: "Arun, Isha & Dev", image: "MusicCoverCarousel-drive.jpg", glow: .musicCoverCarousel(hex: 0x4A4A50), wash: [.musicCoverCarousel(hex: 0x626269), .musicCoverCarousel(hex: 0x4A4A50), .musicCoverCarousel(hex: 0x36363B)]),
    ]
    static let steps = [
        MusicCoverCarouselStep(at: 0.9, to: 3, response: 0.55, damping: 0.8, velocity: 2, press: .next),
        MusicCoverCarouselStep(at: 2.3, to: 4, response: 0.62, damping: 0.7, velocity: 6),
        MusicCoverCarouselStep(at: 3.75, to: 3, response: 0.62, damping: 0.72, velocity: 6),
        MusicCoverCarouselStep(at: 5.491, to: 2, response: 0.55, damping: 0.8, velocity: 3, press: .previous),
    ]
    static let changes: [Double] = {
        var result: [Double] = []
        var previous = Int(musicCoverCarouselFlow(at: 0).rounded())
        for millisecond in 1..<Int(MusicCoverCarousel.loop * 1_000) {
            let current = Int(musicCoverCarouselFlow(at: Double(millisecond) / 1_000).rounded())
            if current != previous { result.append(Double(millisecond) / 1_000) }
            previous = current
        }
        return result
    }()
}

private func musicCoverCarouselSpringRemain(
    time: Double,
    response: Double,
    damping: Double,
    velocity: Double
) -> Double {
    if time <= 0 { return 1 }
    if time > response * 5 { return 0 }
    let frequency = 2 * Double.pi / response
    if damping >= 1 {
        return exp(-frequency * time) * (1 + (frequency - velocity) * time)
    }
    let damped = frequency * sqrt(1 - damping * damping)
    return exp(-damping * frequency * time) * (
        cos(damped * time) + ((damping * frequency - velocity) / damped) * sin(damped * time)
    )
}

private func musicCoverCarouselFlow(at time: Double) -> Double {
    var flow = MusicCoverCarouselTokens.start
    var from = MusicCoverCarouselTokens.start
    for step in MusicCoverCarouselTokens.steps {
        if time > step.at {
            let normalizedVelocity = step.velocity / abs(step.to - from)
            flow += (step.to - from) * (
                1 - musicCoverCarouselSpringRemain(
                    time: time - step.at,
                    response: step.response,
                    damping: step.damping,
                    velocity: normalizedVelocity
                )
            )
        }
        from = step.to
    }
    return flow
}

private func musicCoverCarouselPress(at time: Double, kind: MusicCoverCarouselPressKind) -> Double {
    for step in MusicCoverCarouselTokens.steps where step.press == kind {
        let unit = (time - step.at + MusicCoverCarouselTokens.pressDuration) /
            MusicCoverCarouselTokens.pressDuration
        if unit > 0, unit < 2 { return unit < 1 ? unit : 2 - unit }
    }
    return 0
}

private func musicCoverCarouselPlayhead(at time: Double) -> Double {
    guard let finalChange = MusicCoverCarouselTokens.changes.last else {
        return MusicCoverCarouselTokens.playheadFrom
    }
    var last = finalChange - MusicCoverCarousel.loop
    for change in MusicCoverCarouselTokens.changes where change <= time { last = change }
    return min(
        1,
        MusicCoverCarouselTokens.playheadFrom +
            MusicCoverCarouselTokens.playheadPerLoop * ((time - last) / MusicCoverCarousel.loop)
    )
}

private func musicCoverCarouselInterpolate(
    _ value: Double,
    input: [Double],
    output: [Double]
) -> Double {
    for index in 0..<(input.count - 1) where value <= input[index + 1] {
        let amount = (value - input[index]) / (input[index + 1] - input[index])
        return output[index] + (output[index + 1] - output[index]) * amount
    }
    return output.last ?? 0
}

private struct MusicCoverCarouselTrack: Identifiable {
    let id: String
    let title: String
    let artist: String
    let image: String
    let glow: Color
    let wash: [Color]
}

private struct MusicCoverCarouselRect {
    let x: Double
    let y: Double
    let width: Double
    let height: Double
}

private enum MusicCoverCarouselPressKind: Equatable {
    case previous
    case next
}

private struct MusicCoverCarouselStep {
    let at: Double
    let to: Double
    let response: Double
    let damping: Double
    let velocity: Double
    let press: MusicCoverCarouselPressKind?

    init(
        at: Double,
        to: Double,
        response: Double,
        damping: Double,
        velocity: Double,
        press: MusicCoverCarouselPressKind? = nil
    ) {
        self.at = at
        self.to = to
        self.response = response
        self.damping = damping
        self.velocity = velocity
        self.press = press
    }
}

private struct MusicCoverCarouselTransition {
    let from: Double
    let to: Double
    let velocity: Double
    let started: Date
    let duration: Double
    let reduced: Bool
    let stiffness: Double
}

private struct MusicCoverCarouselCardPlacement {
    let x: Double
    let scale: Double
    let turn: Double
    let opacity: Double
    let z: Double
}

private struct MusicCoverCarouselCard: View {
    @Environment(\.displayScale) private var displayScale

    let track: MusicCoverCarouselTrack
    let index: Int
    let flow: Double
    let reducedMotion: Bool
    let textOpacity: Double

    var body: some View {
        let scale = MusicCoverCarouselTokens.scale
        let inset = 5 * scale
        let width = 215 * scale
        let height = 269 * scale
        let distance = Double(index) - flow
        let amount = min(3, abs(distance))
        let side = distance < 0 ? -1.0 : 1.0
        let turn = side * musicCoverCarouselInterpolate(
            amount,
            input: [0, 1, 2, 3],
            output: [0, 12, 20, 34]
        )
        let between = abs(distance - distance.rounded())
        let bandWidth = width * MusicCoverCarouselTokens.sheenWidth
        let sheenX = (0.5 - turn / MusicCoverCarouselTokens.sheenSpan) * width - bandWidth / 2
        ZStack(alignment: .topLeading) {
            MusicCoverCarouselBlur(style: .systemUltraThinMaterialDark, intensity: 0.28)
            Color(red: 120 / 255, green: 110 / 255, blue: 104 / 255).opacity(0.42)
            MusicCoverCarouselImage(name: track.image)
                .scaledToFill()
                .frame(width: 215 * scale - inset * 2, height: 205 * scale)
                .clipShape(RoundedRectangle(cornerRadius: 12 * scale, style: .continuous))
                .position(x: 215 * scale / 2, y: inset + 205 * scale / 2)
            Text(track.title)
                .font(.system(size: 17.5 * scale, weight: .medium))
                .foregroundStyle(.white)
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: 215 * scale - 8, height: 16)
                .position(x: 215 * scale / 2, y: 228 * scale)
                .opacity(textOpacity)
            Text(track.artist)
                .font(.system(size: 16.5 * scale))
                .foregroundStyle(.white.opacity(0.72))
                .lineLimit(1)
                .minimumScaleFactor(0.7)
                .frame(width: 215 * scale - 8, height: 14)
                .position(x: 215 * scale / 2, y: 252 * scale)
                .opacity(textOpacity)
            LinearGradient(
                colors: [
                    .white.opacity(0),
                    .white.opacity(MusicCoverCarouselTokens.sheenAlpha),
                    .white.opacity(0),
                ],
                startPoint: .leading,
                endPoint: .trailing
            )
            .frame(width: bandWidth, height: height * 1.4)
            .rotationEffect(.degrees(MusicCoverCarouselTokens.sheenSlant))
            .offset(x: sheenX, y: -height * 0.2)
            .opacity(reducedMotion ? 0 : sin(.pi * between))
            .allowsHitTesting(false)
        }
        .clipShape(RoundedRectangle(cornerRadius: 17 * scale, style: .continuous))
        .overlay(RoundedRectangle(cornerRadius: 17 * scale, style: .continuous).stroke(.white.opacity(0.32), lineWidth: 2 / displayScale))
    }
}

private struct MusicCoverCarouselImage: View {
    let name: String
    private let image: UIImage?

    init(name: String) {
        self.name = name
        let image = UIImage(named: name)
#if DEBUG
        assert(image != nil, "Missing bundled image: \(name)")
#endif
        self.image = image
    }

    var body: some View {
        if let image {
            Image(uiImage: image).resizable()
        } else {
            Color(red: 42 / 255, green: 22 / 255, blue: 7 / 255)
#if DEBUG
                .overlay(Text("Missing \(name)").font(.caption2).foregroundStyle(.white))
#endif
        }
    }
}

private struct MusicCoverCarouselBlur: UIViewRepresentable {
    let style: UIBlurEffect.Style
    let intensity: CGFloat
    func makeCoordinator() -> MusicCoverCarouselBlurCoordinator { MusicCoverCarouselBlurCoordinator() }
    func makeUIView(context: Context) -> UIVisualEffectView {
        let view = UIVisualEffectView(effect: nil)
        let animator = UIViewPropertyAnimator(duration: 1, curve: .linear) { view.effect = UIBlurEffect(style: style) }
        animator.fractionComplete = intensity
        animator.pausesOnCompletion = true
        context.coordinator.animator = animator
        return view
    }
    func updateUIView(_ view: UIVisualEffectView, context: Context) {
        context.coordinator.animator?.fractionComplete = intensity
    }
}

private final class MusicCoverCarouselBlurCoordinator {
    var animator: UIViewPropertyAnimator?
}

private struct MusicCoverCarouselPressButtonStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.6 : 1)
            .scaleEffect(configuration.isPressed ? 0.97 : 1)
            .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.12), value: configuration.isPressed)
    }
}

private struct MusicCoverCarouselGlyph: View {
    enum Kind {
        case rewind, pause, play, forward, lyrics, list, volume, wave, more

        var width: Double {
            switch self {
            case .rewind: 15.3
            case .pause, .play: 8.7
            case .forward: 15.6
            case .lyrics, .list, .volume: 13
            case .wave: 10
            case .more: 12
            }
        }

        var height: Double {
            switch self {
            case .rewind, .forward: 8.7
            case .pause, .play: 11.7
            case .lyrics, .more: 12
            case .list: 9.3
            case .volume: 9
            case .wave: 5
            }
        }
    }

    let kind: Kind
    let color: Color

    var body: some View {
        Canvas { context, _ in
            switch kind {
            case .rewind:
                triangle(&context, [CGPoint(x: 7.4, y: 0.5), CGPoint(x: 7.4, y: 8.2), CGPoint(x: 0.5, y: 4.35)])
                triangle(&context, [CGPoint(x: 14.8, y: 0.5), CGPoint(x: 14.8, y: 8.2), CGPoint(x: 7.9, y: 4.35)])
            case .forward:
                triangle(&context, [CGPoint(x: 0.5, y: 0.5), CGPoint(x: 0.5, y: 8.2), CGPoint(x: 7.5, y: 4.35)])
                triangle(&context, [CGPoint(x: 8.1, y: 0.5), CGPoint(x: 8.1, y: 8.2), CGPoint(x: 15.1, y: 4.35)])
            case .pause:
                context.fill(Path(roundedRect: CGRect(x: 0, y: 0, width: 3.2, height: 11.7), cornerRadius: 0.9), with: .color(color))
                context.fill(Path(roundedRect: CGRect(x: 5.5, y: 0, width: 3.2, height: 11.7), cornerRadius: 0.9), with: .color(color))
            case .play:
                triangle(&context, [CGPoint(x: 1, y: 1.6), CGPoint(x: 7.7, y: 5.85), CGPoint(x: 1, y: 11)])
            case .more:
                for x in [2.5, 6.0, 9.5] {
                    context.fill(Path(ellipseIn: CGRect(x: x - 0.95, y: 5.05, width: 1.9, height: 1.9)), with: .color(color))
                }
            case .wave:
                context.fill(Path(roundedRect: CGRect(x: 0, y: 0.8, width: 2.2, height: 3.4), cornerRadius: 1.1), with: .color(color))
                context.fill(Path(roundedRect: CGRect(x: 2.9, y: 0, width: 4.2, height: 5), cornerRadius: 1.6), with: .color(color))
                context.fill(Path(roundedRect: CGRect(x: 7.8, y: 0.8, width: 2.2, height: 3.4), cornerRadius: 1.1), with: .color(color))
            case .list:
                for y in [0.7, 4.65, 8.6] {
                    context.fill(Path(ellipseIn: CGRect(x: 0, y: y - 0.75, width: 1.5, height: 1.5)), with: .color(color))
                    var line = Path()
                    line.move(to: CGPoint(x: 3.3, y: y))
                    line.addLine(to: CGPoint(x: 12.3, y: y))
                    context.stroke(line, with: .color(color), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
                }
            case .volume:
                var speaker = Path()
                speaker.move(to: CGPoint(x: 0, y: 2.9))
                speaker.addLine(to: CGPoint(x: 2.4, y: 2.9))
                speaker.addLine(to: CGPoint(x: 5.6, y: 0.2))
                speaker.addLine(to: CGPoint(x: 5.6, y: 8.8))
                speaker.addLine(to: CGPoint(x: 2.4, y: 6.1))
                speaker.addLine(to: CGPoint(x: 0, y: 6.1))
                speaker.closeSubpath()
                context.fill(speaker, with: .color(color))
                var waves = Path()
                waves.move(to: CGPoint(x: 7.6, y: 2.6))
                waves.addCurve(to: CGPoint(x: 7.6, y: 6.4), control1: CGPoint(x: 10.2, y: 3.4), control2: CGPoint(x: 10.2, y: 5.6))
                waves.move(to: CGPoint(x: 9.9, y: 0.9))
                waves.addCurve(to: CGPoint(x: 9.9, y: 8.1), control1: CGPoint(x: 14, y: 2.1), control2: CGPoint(x: 14, y: 6.9))
                context.stroke(waves, with: .color(color), style: StrokeStyle(lineWidth: 1.4, lineCap: .round))
            case .lyrics:
                var bubble = Path()
                bubble.move(to: CGPoint(x: 2.8, y: 0.75))
                bubble.addLine(to: CGPoint(x: 10.2, y: 0.75))
                bubble.addQuadCurve(to: CGPoint(x: 12.25, y: 2.8), control: CGPoint(x: 12.25, y: 0.75))
                bubble.addLine(to: CGPoint(x: 12.25, y: 7.4))
                bubble.addQuadCurve(to: CGPoint(x: 10.2, y: 9.45), control: CGPoint(x: 12.25, y: 9.45))
                bubble.addLine(to: CGPoint(x: 6.1, y: 9.45))
                bubble.addLine(to: CGPoint(x: 3.3, y: 11.3))
                bubble.addLine(to: CGPoint(x: 3.3, y: 9.45))
                bubble.addLine(to: CGPoint(x: 2.8, y: 9.45))
                bubble.addQuadCurve(to: CGPoint(x: 0.75, y: 7.4), control: CGPoint(x: 0.75, y: 9.45))
                bubble.addLine(to: CGPoint(x: 0.75, y: 2.8))
                bubble.addQuadCurve(to: CGPoint(x: 2.8, y: 0.75), control: CGPoint(x: 0.75, y: 0.75))
                context.stroke(bubble, with: .color(color), style: StrokeStyle(lineWidth: 1.5, lineJoin: .round))
                for x in [4.6, 8.2] {
                    context.fill(Path(ellipseIn: CGRect(x: x - 1.3, y: 3.1, width: 2.6, height: 2.6)), with: .color(color))
                    var tail = Path()
                    tail.move(to: CGPoint(x: x + 1, y: 4.4))
                    tail.addQuadCurve(to: CGPoint(x: x - 0.8, y: 7.4), control: CGPoint(x: x + 1, y: 6.4))
                    context.stroke(tail, with: .color(color), style: StrokeStyle(lineWidth: 1.1, lineCap: .round))
                }
            }
        }
    }

    private func triangle(_ context: inout GraphicsContext, _ points: [CGPoint]) {
        var path = Path()
        path.move(to: points[0])
        path.addLine(to: points[1])
        path.addLine(to: points[2])
        path.closeSubpath()
        context.fill(path, with: .color(color))
        context.stroke(path, with: .color(color), style: StrokeStyle(lineWidth: 1, lineJoin: .round))
    }
}

/// A zero-delay UIKit hold keeps the cover flow alive beside the gallery's back-swipe recognizer.
private struct MusicCoverCarouselTouchSurface: UIViewRepresentable {
    let onBegin: (Double, Double) -> Double
    let onChange: (Double, Double, Double, Double) -> Void
    let onEnd: (Double, Double, Double, Double) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        let recognizer = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(MusicCoverCarouselTouchCoordinator.receive(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        view.backgroundColor = .clear
        return view
    }

    func updateUIView(_ view: UIView, context: Context) {
        context.coordinator.parent = self
    }

    func makeCoordinator() -> MusicCoverCarouselTouchCoordinator {
        MusicCoverCarouselTouchCoordinator(parent: self)
    }
}

private final class MusicCoverCarouselTouchCoordinator: NSObject, UIGestureRecognizerDelegate {
    var parent: MusicCoverCarouselTouchSurface
    private var startX = 0.0
    private var startY = 0.0
    private var previousX = 0.0
    private var previousTime = Date.now
    private var velocity = 0.0

    init(parent: MusicCoverCarouselTouchSurface) {
        self.parent = parent
    }

    func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer) -> Bool {
        true
    }

    @objc func receive(_ recognizer: UILongPressGestureRecognizer) {
        let point = recognizer.location(in: recognizer.view)
        let x = point.x
        let now = Date.now
        switch recognizer.state {
        case .began:
            startX = parent.onBegin(x, point.y)
            startY = point.y
            previousX = x
            previousTime = now
            velocity = 0
        case .changed:
            let elapsed = max(0.001, now.timeIntervalSince(previousTime))
            velocity = (x - previousX) / elapsed
            previousX = x
            previousTime = now
            parent.onChange(startX, startY, x, point.y)
        case .ended:
            parent.onEnd(startX, x, point.y, velocity)
        case .cancelled, .failed:
            parent.onEnd(startX, previousX, point.y, velocity)
        default:
            break
        }
    }
}

private enum MusicCoverCarouselBezier {
    static func solve(_ x: Double, x1: Double, y1: Double, x2: Double, y2: Double) -> Double {
        if x <= 0 { return 0 }
        if x >= 1 { return 1 }
        var low = 0.0
        var high = 1.0
        for _ in 0..<12 {
            let t = (low + high) / 2
            if cubic(t, 0, x1, x2, 1) < x { low = t } else { high = t }
        }
        return cubic((low + high) / 2, 0, y1, y2, 1)
    }

    private static func cubic(_ t: Double, _ a: Double, _ b: Double, _ c: Double, _ d: Double) -> Double {
        let u = 1 - t
        return u * u * u * a + 3 * u * u * t * b + 3 * u * t * t * c + t * t * t * d
    }
}

/// Reanimated 4.5 duration-spring math, including its mass, energy threshold and stiffness solve.
private enum MusicCoverCarouselSpring {
    private static let mass = 4.0
    private static let energyThreshold = 6e-9

    static func towardVelocity(from: Double, to: Double, velocity: Double) -> Double {
        if (to > from && velocity < 0) || (to < from && velocity > 0) { return 0 }
        return velocity
    }

    static func stiffness(
        displacement: Double,
        velocity: Double,
        duration: Double,
        dampingRatio: Double
    ) -> Double {
        guard displacement != 0 || velocity != 0 else { return 0 }
        let settlingDuration = duration * 1.5
        func difference(_ stiffness: Double) -> Double {
            let decay = sqrt(stiffness / mass) * dampingRatio
            let envelope = exp(-decay * settlingDuration)
            let term = velocity + displacement * decay
            let position = (displacement + term * settlingDuration) * envelope
            let speed = position * -decay + term * envelope
            let initial = energy(displacement: displacement, velocity: velocity, stiffness: stiffness)
            let settled = energy(displacement: position, velocity: speed, stiffness: stiffness)
            return settled / initial - energyThreshold
        }

        var low = Double.ulpOfOne
        var high = 8_000.0
        let direction = difference(high) >= difference(low) ? 1.0 : -1.0
        var current = (low + high) / 2
        for _ in 0..<100 {
            if abs(difference(current)) <= energyThreshold * 1e-3 { break }
            if difference(current) * direction < 0 { low = current } else { high = current }
            current = (low + high) / 2
        }
        return current
    }

    static func position(
        from: Double,
        to: Double,
        velocity: Double,
        elapsed: Double,
        dampingRatio: Double,
        stiffness: Double
    ) -> Double {
        guard stiffness > 0, elapsed > 0 else { return elapsed > 0 ? to : from }
        let clippedVelocity = towardVelocity(from: from, to: to, velocity: velocity)
        let displacement = from - to
        let omega0 = sqrt(stiffness / mass)
        let omega1 = omega0 * sqrt(max(0, 1 - dampingRatio * dampingRatio))
        let envelope = exp(-dampingRatio * omega0 * elapsed)
        let sine = sin(omega1 * elapsed)
        let cosine = cos(omega1 * elapsed)
        let fragment = envelope * (sine * ((clippedVelocity + dampingRatio * omega0 * displacement) / omega1) + displacement * cosine)
        let value = to + fragment
        let speed = -dampingRatio * omega0 * fragment + envelope * (
            cosine * (clippedVelocity + dampingRatio * omega0 * displacement) - omega1 * displacement * sine
        )
        let initial = energy(displacement: displacement, velocity: clippedVelocity, stiffness: stiffness)
        let current = energy(displacement: to - value, velocity: speed, stiffness: stiffness)
        return initial == 0 || current / initial <= energyThreshold ? to : value
    }

    private static func energy(displacement: Double, velocity: Double, stiffness: Double) -> Double {
        0.5 * stiffness * displacement * displacement + 0.5 * mass * velocity * velocity
    }
}

private extension Color {
    static func musicCoverCarousel(hex: UInt32) -> Color {
        Color(
            red: Double((hex >> 16) & 0xFF) / 255,
            green: Double((hex >> 8) & 0xFF) / 255,
            blue: Double(hex & 0xFF) / 255
        )
    }
}
