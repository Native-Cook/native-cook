import SwiftUI
import UIKit

struct FilmBoxUnboxingDetail: Hashable {
    let label: String
    let value: String
}

struct FilmBoxUnboxingFilm {
    var name: String
    var description: String
    /// Seconds left before the filter expires, counted down from the moment it is shown.
    var expiresIn: Int
    var details: [FilmBoxUnboxingDetail]
    /// The colour laid over a photo taken with it.
    var tint: Color
    var tintOpacity: Double

    static let alien = FilmBoxUnboxingFilm(
        name: "Alien",
        description: "Otherworldly filter with vibrant, alien-like colors.",
        expiresIn: 17 * 3600 + 21 * 60 + 58,
        details: [
            FilmBoxUnboxingDetail(label: "ISO", value: "Medium"),
            FilmBoxUnboxingDetail(label: "Contrast", value: "High"),
            FilmBoxUnboxingDetail(label: "Saturation", value: "Vibrant"),
        ],
        tint: Color(hex: 0x0F5560),
        tintOpacity: 0.4
    )
}

/// A daily filter, unboxed: a slider opens a film box drawn in perspective, the view dives into
/// it, and the filter's canister comes out turning, with its details. Load film flies the canister
/// up into a camera; the shutter takes a photo with the filter's colour on it.
struct FilmBoxUnboxing: View {
    var title = "Unbox your filter for today"
    var date = "5/7/26"
    var slideLabel = "Unbox filter"
    var expiresLabel = "Expires in"
    var loadLabel = "Load film"
    var retakeLabel = "Retake"
    var film = FilmBoxUnboxingFilm.alien
    /// What the camera sees.
    var photo = URL(string: "https://images.unsplash.com/photo-1441974231531-c6227db76b6e?auto=format&fit=crop&w=600&h=600&q=80")
    /// The ring of the slider, the knob and the shutter.
    var accent = Color(hex: 0xFFD60A)
    /// Plays the whole flow by itself every `loop` seconds, from the closed box back to it.
    var autoPlay = false
    var onUnbox: (() -> Void)?
    var onLoad: ((FilmBoxUnboxingFilm) -> Void)?
    var onCapture: (() -> Void)?
    var onRetake: (() -> Void)?
    var onSave: (() -> Void)?
    var onShare: (() -> Void)?

    private typealias T = FilmBoxUnboxingTimeline
    private typealias Art = FilmBoxUnboxingArt

    private enum Palette {
        static let black = Color.black
        static let title = Color(hex: 0xE6E6E6)
        static let mute = Color(hex: 0x8A8A8A)
        static let description = Color(hex: 0xB0B0B0)
        static let rowLabel = Color(hex: 0x6E6E6E)
        static let rowValue = Color(hex: 0xE8D44D)
        static let rule = Color(hex: 0x222222)
        static let button = Color(hex: 0x1C1C1C)
        static let round = Color(hex: 0x141414)
        static let knobTop = Color(hex: 0xFFE259)
        static let knobBottom = Color(hex: 0xFFC800)
    }

    // Layout, in points. The box and the filter's name sit on the middle of the screen; the slider,
    // the spinner, Load film and the camera's controls share one row above the bottom edge.
    private enum Metrics {
        static let headerTop = 54.0
        static let canisterAbove = 150.0
        /// The small canister at the top of the camera: its centre below the safe area.
        static let smallTop = 40.0
        static let textX = 40.0
        static let descriptionTop = 22.0
        static let rowsTop = 70.0
        static let rowHeight = 26.0
        static let action = 88.0
        static let pillWidth = 216.0
        static let pillHeight = 52.0
        static let pillStroke = 3.0
        static let knob = 44.0
        static let knobInset = 4.0
        static let travel = pillWidth - knobInset * 2 - knob
        /// Released past this share of the track, the knob opens the box; before it, it springs back.
        static let unboxThreshold = 0.85
        static let loadWidth = 104.0
        static let loadHeight = 38.0
        static let roundSize = 44.0
        static let side = 112.0
        static let shutter = 66.0
        static let shutterRing = 3.0
        static let shutterGap = 3.0
        static let retakeWidth = 88.0
        static let photoMax = 300.0
        static let photoMargin = 56.0
        static let photoRadius = 6.0
        static let bracket = 18.0
        static let bracketInset = 10.0
        static let icon = 22.0
        static let pressed = 0.92
        static let snap = 0.12
    }

    /// The knob springs back on SwiftUI's spring(response: 0.35, dampingFraction: 0.75).
    private static let springBack = Animation.spring(response: 0.35, dampingFraction: 0.75)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// The clock every motion reads counts from here.
    @State private var start = Date.now
    @State private var events = FilmBoxUnboxingEvents.none
    /// When the autoPlay loop started, in seconds of the clock; nil while touches have the screen.
    @State private var origin: Double?
    /// Where the knob is along its track by hand, 0 to 1.
    @State private var drag = 0.0
    @State private var dragFrom: Double?
    @GestureState private var touching = false
    @State private var lastTouch = 0.0
    /// Nothing on screen moves: the timeline can rest.
    @State private var quiet = false
    @State private var timerOn = false
    @State private var flashOn = false
    @State private var lightHaptic = UIImpactFeedbackGenerator(style: .light)
    @State private var mediumHaptic = UIImpactFeedbackGenerator(style: .medium)

    private func clock(_ date: Date = .now) -> Double { date.timeIntervalSince(start) }

    /// The actions and the knob at `now`: the loop's while it plays, else those touches made.
    private func current(_ now: Double) -> (events: FilmBoxUnboxingEvents, drag: Double) {
        if let origin, now >= origin {
            let base = origin + floor((now - origin) / T.loop) * T.loop
            return (T.loopEvents(now - base, base: base), T.loopDrag(now - base, still: reduceMotion))
        }
        return (events, drag)
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: quiet)) { timeline in
                let now = clock(timeline.date)
                let state = current(now)
                let scene = T.scene(at: now, events: state.events, still: reduceMotion)
                content(scene, knob: state.drag, screen: screen, insets: insets)
                    .frame(width: screen.width, height: screen.height)
                    .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .background(Palette.black.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .contentShape(Rectangle())
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, touching, _ in touching = true }
                .onChanged { _ in if origin != nil { takeOver(springBack: true) } }
        )
        .onChange(of: touching) { _, down in
            if !down { lastTouch = clock() }
        }
        .onAppear {
            start = .now
            origin = autoPlay ? 0 : nil
            lightHaptic.prepare()
            mediumHaptic.prepare()
        }
        .onChange(of: autoPlay) { _, on in origin = on ? clock() : nil }
        .task(id: IdleKey(events: events, lastTouch: lastTouch, touching: touching, playing: origin != nil, autoPlay: autoPlay)) {
            await idle()
        }
    }

    // MARK: The screen

    @ViewBuilder
    private func content(_ s: FilmBoxUnboxingScene, knob: Double, screen: CGSize, insets: EdgeInsets) -> some View {
        let w = screen.width
        let midY = screen.height / 2
        let actionY = screen.height - max(insets.bottom, 16) - Metrics.action
        let smallY = insets.top + Metrics.smallTop
        let photoSize = min(w - Metrics.photoMargin * 2, Metrics.photoMax, actionY - Metrics.shutter / 2 - smallY - 72)
        let photoY = (smallY + 24 + actionY - Metrics.shutter / 2) / 2
        let rows = [FilmBoxUnboxingDetail(label: expiresLabel, value: T.countdown(film.expiresIn - Int(s.shown)))] + film.details

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                if s.boxAlpha > 0.001 {
                    var layer = context
                    layer.opacity = s.boxAlpha
                    layer.drawLayer { box in
                        Art.drawBox(
                            box,
                            center: CGPoint(x: w / 2, y: midY + s.lift),
                            focus: CGPoint(x: w / 2, y: midY),
                            pose: Art.Pose(yaw: s.yaw, pitch: s.tilt, roll: s.roll, flap: s.flap, glow: s.glow, sweep: s.sweep, dive: s.dive)
                        )
                    }
                }
                if s.spinner > 0.001 {
                    Art.drawSpinner(context, at: CGPoint(x: w / 2, y: actionY), time: s.spinnerTime, alpha: s.spinner)
                }
                if s.canister > 0.001 {
                    let from = midY - Metrics.canisterAbove
                    var layer = context
                    layer.opacity = min(1, s.canister)
                    layer.drawLayer { can in
                        Art.drawCanister(
                            can,
                            center: CGPoint(x: w / 2, y: from + (smallY - from) * s.fly),
                            scale: s.canisterScale * (1 + (T.small - 1) * s.fly),
                            spin: s.spin
                        )
                    }
                }
            }
            .frame(width: w, height: screen.height)
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            VStack(spacing: 4) {
                label(title, size: 12, height: 16, color: Palette.title)
                label(date, size: 11, height: 14, color: Palette.mute)
            }
            .frame(width: w - 48)
            .opacity(s.header)
            .position(x: w / 2, y: insets.top + Metrics.headerTop + 17)
            .allowsHitTesting(false)

            pill(knob: knob)
                .opacity(s.pill)
                .position(x: w / 2, y: actionY)
                .allowsHitTesting(s.stage == .box)
                .accessibilityHidden(s.stage != .box)

            label(film.name, size: 17, height: 22, color: .white)
                .frame(width: w - 48)
                .opacity(s.name * (1 - s.cardOut))
                .position(x: w / 2, y: midY)
                .allowsHitTesting(false)

            Text(film.description)
                .font(.system(size: 11, design: .monospaced))
                .lineSpacing(16 - UIFont.monospacedSystemFont(ofSize: 11, weight: .regular).lineHeight)
                .foregroundStyle(Palette.description)
                .frame(width: w - Metrics.textX * 2, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                .modifier(Reveal(scene: s, index: 0))
                .offset(x: Metrics.textX, y: midY + Metrics.descriptionTop)

            ForEach(Array(rows.enumerated()), id: \.element.label) { i, row in
                HStack {
                    label(row.label, size: 10, height: 14, color: Palette.rowLabel)
                    Spacer(minLength: 8)
                    label(row.value, size: 10, height: 14, color: Palette.rowValue)
                }
                .frame(width: w - Metrics.textX * 2, height: Metrics.rowHeight)
                .overlay(alignment: .top) {
                    if i > 0 { Palette.rule.frame(height: 1) }
                }
                .modifier(Reveal(scene: s, index: min(i + 1, 4)))
                .position(x: w / 2, y: midY + Metrics.rowsTop + Double(i) * Metrics.rowHeight + Metrics.rowHeight / 2)
            }

            Button(action: load) {
                label(loadLabel, size: 12, height: 16, color: .white)
                    .opacity(1 - 0.62 * s.grey)
                    .frame(width: Metrics.loadWidth, height: Metrics.loadHeight)
                    .background(Palette.button, in: Capsule())
            }
            .buttonStyle(FilmBoxUnboxingFade())
            .scaleEffect(s.loadScale)
            .offset(y: T.rowLift * (1 - s.rows[5]))
            .opacity(s.rows[5] * (1 - s.cardOut))
            .position(x: w / 2, y: actionY)
            .allowsHitTesting(s.stage == .film)
            .accessibilityHidden(s.stage != .film)

            photoFrame(s, size: photoSize)
                .position(x: w / 2, y: photoY)
                .allowsHitTesting(false)

            cameraControls(s, width: w)
                .opacity(s.camera * s.cameraRow)
                .offset(y: T.cameraLift * (1 - s.camera) + 12 * (1 - s.cameraRow))
                .position(x: w / 2, y: actionY)
                .allowsHitTesting(s.stage == .camera)
                .accessibilityHidden(s.stage != .camera)

            photoControls(width: w)
                .opacity(s.photoRow)
                .offset(y: 12 * (1 - s.photoRow))
                .position(x: w / 2, y: actionY)
                .allowsHitTesting(s.stage == .photo)
                .accessibilityHidden(s.stage != .photo)

            Color.white.opacity(s.flash).allowsHitTesting(false)
            Palette.black.opacity(s.overlay).allowsHitTesting(false)
        }
    }

    private func label(_ text: String, size: Double, height: Double, color: Color) -> some View {
        Text(text)
            .font(.system(size: size, design: .monospaced))
            .foregroundStyle(color)
            .lineLimit(1)
            .frame(height: height)
    }

    private func pill(knob: Double) -> some View {
        ZStack(alignment: .leading) {
            Capsule().strokeBorder(accent, lineWidth: Metrics.pillStroke)
            label(slideLabel, size: 12, height: 16, color: .white)
                .opacity(1 - T.smooth(knob, 0.05, 0.45))
                .frame(maxWidth: .infinity)
                .padding(.leading, Metrics.knobInset + Metrics.knob)
                .padding(.trailing, Metrics.knobInset)
            Circle()
                .fill(LinearGradient(colors: [Palette.knobTop, Palette.knobBottom], startPoint: .top, endPoint: .bottom))
                .overlay { FilmBoxUnboxingIcon(drawing: .arrow, size: Metrics.icon, color: .black, stroke: 2) }
                .frame(width: Metrics.knob, height: Metrics.knob)
                .offset(x: Metrics.knobInset + knob * Metrics.travel)
        }
        .frame(width: Metrics.pillWidth, height: Metrics.pillHeight)
        .contentShape(Capsule())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if dragFrom == nil {
                        takeOver(springBack: false)
                        dragFrom = current(clock()).drag
                    }
                    drag = T.clamp01((dragFrom ?? 0) + value.translation.width / Metrics.travel)
                }
                .onEnded { _ in
                    dragFrom = nil
                    lastTouch = clock()
                    if drag >= Metrics.unboxThreshold && events.unbox < 0 {
                        unbox()
                    } else {
                        withAnimation(Self.springBack) { drag = 0 }
                    }
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel(slideLabel)
        .accessibilityAddTraits(.isButton)
        .accessibilityAction {
            takeOver(springBack: false)
            if events.unbox < 0 { unbox() }
        }
    }

    private func photoFrame(_ s: FilmBoxUnboxingScene, size: Double) -> some View {
        ZStack {
            ZStack {
                photoImage
                ForEach(0..<4, id: \.self) { corner in
                    FilmBoxUnboxingBracket(corner: corner)
                        .stroke(Color.white.opacity(0.9), lineWidth: 2)
                        .frame(width: Metrics.bracket, height: Metrics.bracket)
                        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: [.topLeading, .topTrailing, .bottomLeading, .bottomTrailing][corner])
                        .padding(Metrics.bracketInset)
                }
            }
            .opacity(s.camera)
            ZStack {
                photoImage
                film.tint.opacity(film.tintOpacity)
                    .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius))
            }
            .scaleEffect(s.photoScale)
            .opacity(s.photo)
        }
        .frame(width: size, height: size)
        .accessibilityHidden(true)
    }

    private var photoImage: some View {
        Palette.round
            .overlay {
                AsyncImage(url: photo, transaction: Transaction(animation: .easeOut(duration: 0.2))) { phase in
                    if let image = phase.image { image.resizable().scaledToFill().transition(.opacity) }
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: Metrics.photoRadius))
    }

    private func cameraControls(_ s: FilmBoxUnboxingScene, width: Double) -> some View {
        ZStack {
            roundButton("Timer", drawing: .timer, color: timerOn ? accent : .white) {
                lightHaptic.impactOccurred()
                timerOn.toggle()
            }
            .offset(x: -Metrics.side)
            Button(action: shoot) {
                Circle()
                    .fill(LinearGradient(colors: [Palette.knobTop, Palette.knobBottom], startPoint: .top, endPoint: .bottom))
                    .padding(Metrics.shutterRing + Metrics.shutterGap)
                    .background(Palette.black, in: Circle())
                    .overlay { Circle().strokeBorder(accent, lineWidth: Metrics.shutterRing) }
                    .frame(width: Metrics.shutter, height: Metrics.shutter)
            }
            .buttonStyle(FilmBoxUnboxingFade())
            .scaleEffect(s.shutter)
            .accessibilityLabel("Take photo")
            roundButton(flashOn ? "Flash on" : "Flash off", drawing: flashOn ? .flashOn : .flashOff, color: flashOn ? accent : .white, fill: flashOn ? accent : nil) {
                lightHaptic.impactOccurred()
                flashOn.toggle()
            }
            .offset(x: Metrics.side)
        }
        .frame(width: width, height: Metrics.shutter)
    }

    private func photoControls(width: Double) -> some View {
        ZStack {
            roundButton("Save", drawing: .download, color: .white) {
                lightHaptic.impactOccurred()
                onSave?()
            }
            .offset(x: -Metrics.side)
            Button(action: retake) {
                label(retakeLabel, size: 12, height: 16, color: .white)
                    .frame(width: Metrics.retakeWidth, height: Metrics.roundSize)
                    .background(Palette.button, in: Capsule())
            }
            .buttonStyle(FilmBoxUnboxingPress())
            roundButton("Share", drawing: .share, color: .white) {
                lightHaptic.impactOccurred()
                onShare?()
            }
            .offset(x: Metrics.side)
        }
        .frame(width: width, height: Metrics.shutter)
    }

    private func roundButton(_ name: String, drawing: FilmBoxUnboxingDrawing, color: Color, fill: Color? = nil, action: @escaping () -> Void) -> some View {
        Button(action: action) {
            FilmBoxUnboxingIcon(drawing: drawing, size: Metrics.icon, color: color, stroke: 1.6, fill: fill)
                .frame(width: Metrics.roundSize, height: Metrics.roundSize)
                .background(Palette.round, in: Circle())
        }
        .buttonStyle(FilmBoxUnboxingPress())
        .accessibilityLabel(name)
    }

    // MARK: Actions

    /// A touch takes the screen from the autoPlay loop; a knob it left midway springs back.
    private func takeOver(springBack: Bool) {
        let now = clock()
        lastTouch = now
        guard origin != nil else { return }
        let state = current(now)
        events = state.events
        drag = state.drag
        origin = nil
        if springBack && state.events.unbox < 0 {
            withAnimation(Self.springBack) { drag = 0 }
        }
    }

    private func unbox() {
        withAnimation(.linear(duration: Metrics.snap)) { drag = 1 }
        events = FilmBoxUnboxingEvents(unbox: clock())
        mediumHaptic.impactOccurred()
        onUnbox?()
    }

    private func load() {
        let now = clock()
        guard T.scene(at: now, events: events, still: reduceMotion).stage == .film else { return }
        lightHaptic.impactOccurred()
        events = FilmBoxUnboxingEvents(unbox: events.unbox, load: now)
        onLoad?(film)
    }

    private func shoot() {
        let now = clock()
        guard T.scene(at: now, events: events, still: reduceMotion).stage == .camera else { return }
        mediumHaptic.impactOccurred()
        events = FilmBoxUnboxingEvents(unbox: events.unbox, load: events.load, shot: now, retake: events.retake)
        onCapture?()
    }

    private func retake() {
        let now = clock()
        guard T.scene(at: now, events: events, still: reduceMotion).stage == .photo else { return }
        lightHaptic.impactOccurred()
        events = FilmBoxUnboxingEvents(unbox: events.unbox, load: events.load, shot: events.shot, retake: now)
        onRetake?()
    }

    /// Once the last action has played out the timeline rests if nothing moves; a while after
    /// the last touch the screen goes back to rest and the loop starts again.
    private func idle() async {
        guard origin == nil, !touching else {
            quiet = false
            return
        }
        quiet = false
        let settled = max(lastTouch, T.settledAt(events))
        try? await Task.sleep(for: .seconds(max(0, settled - clock())))
        guard !Task.isCancelled else { return }
        let stage = T.scene(at: clock(), events: events, still: reduceMotion).stage
        if stage == .camera || stage == .photo || (stage == .box && reduceMotion) { quiet = true }
        guard autoPlay else { return }
        try? await Task.sleep(for: .seconds(max(0, settled + T.userHold - clock())))
        guard !Task.isCancelled else { return }
        quiet = false
        let now = clock()
        let atRest = (events.unbox < 0 || (events.reset >= 0 && now >= events.reset + T.resetHalf * 2)) && drag == 0
        if atRest {
            origin = now
        } else {
            // Fade through black; the closed box and the knob are back by its darkest moment.
            origin = now + T.resetHalf * 2
            events.reset = now
            drag = 0
        }
    }

    private struct IdleKey: Equatable {
        let events: FilmBoxUnboxingEvents
        let lastTouch: Double
        let touching: Bool
        let playing: Bool
        let autoPlay: Bool
    }
}

/// One of the card's lines, coming in after the ones above it.
private struct Reveal: ViewModifier {
    let scene: FilmBoxUnboxingScene
    let index: Int

    func body(content: Content) -> some View {
        let shown = scene.rows[index]
        content
            .opacity(shown * (1 - scene.cardOut))
            .offset(y: FilmBoxUnboxingTimeline.rowLift * (1 - shown))
            .allowsHitTesting(false)
    }
}

/// A corner of the viewfinder: two strokes meeting at corner 0 top left, 1 top right, 2 bottom
/// left or 3 bottom right.
struct FilmBoxUnboxingBracket: Shape {
    let corner: Int

    func path(in rect: CGRect) -> Path {
        let right = corner == 1 || corner == 3
        let bottom = corner >= 2
        let x = right ? rect.maxX : rect.minX
        let y = bottom ? rect.maxY : rect.minY
        var path = Path()
        path.move(to: CGPoint(x: right ? rect.minX : rect.maxX, y: y))
        path.addLine(to: CGPoint(x: x, y: y))
        path.addLine(to: CGPoint(x: x, y: bottom ? rect.minY : rect.maxY))
        return path
    }
}

/// A button that shrinks a little while it is held.
private struct FilmBoxUnboxingPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(configuration.isPressed ? 0.92 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}

/// A button that dims a little while it is held.
private struct FilmBoxUnboxingFade: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .animation(.easeOut(duration: 0.1), value: configuration.isPressed)
    }
}
