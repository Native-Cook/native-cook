import SwiftUI
import UIKit

/// A wellness library as an airy collage: photos of different sizes float on a white page, flying
/// slowly towards the viewer at different depths, the small far ones barely moving and the near
/// ones sliding out past the edges. Tapping one flies it out of the collage into a full-screen
/// hero with a serif title, a Start button and a strip of thumbnails. Swiping the hero sideways
/// pushes in the next one; dragging it down closes it back into its slot.
struct ScatterCollageHero: View {
    /// The practices in the collage, in the order they are born. Twenty to thirty read best.
    var sessions: [ScatterCollageHeroSession] = ScatterCollageHeroSession.samples
    /// Plays the loop by itself: the collage drifts, one photo opens, the hero moves to the next, closes.
    var autoPlay = false
    /// The page behind the collage.
    var background = Color(hex: 0xfafaf7)
    /// The one accent: the active chip and the Start button.
    var accent = Color(hex: 0xcdeec3)
    /// Called when Start is pressed on a hero.
    var onStart: ((ScatterCollageHeroSession) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = ScatterCollageHeroEngine()

    var body: some View {
        // The block draws under the system bars and keeps its chrome clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            // Nothing to lay out until the screen is measured (a zero first pass gives negative frames).
            if size.width > 0 && size.height > 0 {
                TimelineView(.animation) { timeline in
                    let _ = engine.configure(sessions: sessions, autoPlay: autoPlay, reducedMotion: reduceMotion)
                    let _ = engine.onStart = onStart
                    let _ = engine.step(
                        to: timeline.date,
                        stage: ScatterCollageHeroStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
                    )
                    ScatterCollageHeroScene(engine: engine, time: engine.elapsed, background: background, accent: accent)
                }
                .frame(width: size.width, height: size.height)
                .background(background)
                .clipped()
                .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .task(id: sessions.map(\.photo)) { await loadPhotos() }
    }

    // The collage draws its photos as they arrive, each fading in.
    private func loadPhotos() async {
        engine.configure(sessions: sessions, autoPlay: autoPlay, reducedMotion: reduceMotion)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, session) in sessions.enumerated() {
                let address = ScatterCollageHeroSession.photoURL(session.photo, width: ScatterCollageHeroMetrics.tilePx)
                group.addTask { (index, await ScatterCollageHeroPhotos.load(address)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

// MARK: - Scene

private enum ScatterCollageHeroStyle {
    static let ink = Color(hex: 0x1d1d1b)
    static let muted = Color(hex: 0x8d8b85)
    static let paper = Color(hex: 0xecebe6)
    // A sturdy book serif (Georgia) for the hero's title and the wordmark, the system sans for the rest.
    static func serif(_ size: CGFloat, bold: Bool = false, italic: Bool = false) -> Font {
        .custom(italic ? "Georgia-Italic" : bold ? "Georgia-Bold" : "Georgia", fixedSize: size)
    }
}

/// Everything one frame shows: the collage, its chrome, the hero's photos and its copy.
private struct ScatterCollageHeroScene: View {
    let engine: ScatterCollageHeroEngine
    /// The engine's clock: a new value every frame, so SwiftUI draws the frame again.
    let time: Double
    let background: Color
    let accent: Color

    private typealias M = ScatterCollageHeroMetrics
    private typealias S = ScatterCollageHeroStyle

    // True while a pan is on the hero; reset by SwiftUI when the system cancels the drag.
    @GestureState private var panning = false

    var body: some View {
        let stage = engine.stage
        let o = scatterCollageHeroClamp(engine.open.value)
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in drawCollage(&context) }
                .contentShape(Rectangle())
                .gesture(SpatialTapGesture().onEnded { engine.tap(at: $0.location) })
                .accessibilityElement()
                .accessibilityLabel("Practice library: a collage of photos. Tap a photo to open it.")
                .accessibilityHidden(engine.hero != nil)

            chrome(stage: stage)
                .opacity(1 - o)
                .allowsHitTesting(engine.hero == nil)
                .accessibilityHidden(engine.hero != nil)

            Canvas { context, _ in drawHero(&context) }
                .allowsHitTesting(false)
                .accessibilityHidden(true)

            if let hero = engine.hero {
                ZStack(alignment: .topLeading) {
                    Color.clear
                    ForEach(Array(hero.enumerated()), id: \.element) { k, index in
                        ScatterCollageHeroCopy(engine: engine, time: time, index: k, session: engine.sessions[index])
                    }
                    ScatterCollageHeroControls(engine: engine, time: time, list: hero, accent: accent)
                }
                .frame(width: stage.width, height: stage.height)
                .contentShape(Rectangle())
                .gesture(
                    DragGesture(minimumDistance: 6)
                        .updating($panning) { _, state, _ in state = true }
                        .onChanged { engine.panChanged(translation: $0.translation) }
                        .onEnded { engine.panEnded(translation: $0.translation, velocity: $0.velocity) }
                )
                .onChange(of: panning) { _, down in
                    // A drag the system cancels never reaches onEnded.
                    if !down { DispatchQueue.main.async { engine.panCancelled() } }
                }
            }
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
    }

    // MARK: Chrome

    private func chrome(stage: ScatterCollageHeroStage) -> some View {
        ZStack(alignment: .topLeading) {
            Color.clear
            // A soft white fade under the wordmark and over the chips.
            LinearGradient(colors: [background, background.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: stage.width, height: stage.top + 86)
                .allowsHitTesting(false)
            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 2) {
                    Text("Stillhour")
                        .font(S.serif(28, italic: true))
                        .kerning(-0.3)
                        .foregroundStyle(S.ink)
                    Text("\(engine.sessions.count) practices for slow days")
                        .font(.system(size: 12, weight: .medium))
                        .kerning(0.2)
                        .foregroundStyle(S.muted)
                }
                Spacer(minLength: 0)
                ScatterCollageHeroAvatar(initials: "AK", tone: accent)
            }
            .padding(.horizontal, M.side)
            .frame(width: stage.width)
            .offset(y: stage.top + 8)
            .allowsHitTesting(false)

            LinearGradient(colors: [background.opacity(0), background], startPoint: .top, endPoint: .bottom)
                .frame(width: stage.width, height: stage.bottom + 120)
                .offset(y: stage.height - stage.bottom - 120)
                .allowsHitTesting(false)

            chips
                .padding(.horizontal, 16)
                .frame(width: stage.width)
                .offset(y: stage.height - stage.bottom - 14 - 36)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
    }

    private var chips: some View {
        HStack(spacing: 0) {
            ForEach(Array(ScatterCollageHeroSession.categories.enumerated()), id: \.offset) { index, label in
                if index > 0 { Spacer(minLength: 0) }
                Button {
                    engine.chooseChip(index)
                } label: {
                    Text(label)
                        .font(.system(size: 13, weight: .semibold))
                        .foregroundStyle(engine.chip == index ? S.ink : S.muted)
                        .padding(.horizontal, 14)
                        .frame(height: 36)
                        .background(Capsule().fill(engine.chip == index ? Color.clear : S.ink.opacity(0.05)))
                }
                .buttonStyle(.plain)
                .accessibilityAddTraits(engine.chip == index ? .isSelected : [])
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("ScatterCollageHeroChips")) } action: {
                    engine.chipLaidOut(index, frame: $0)
                }
            }
        }
        .background(alignment: .topLeading) {
            Capsule()
                .fill(accent)
                .frame(width: max(0, engine.pillW.value), height: 36)
                .offset(x: engine.pillX.value)
        }
        .coordinateSpace(.named("ScatterCollageHeroChips"))
    }

    // MARK: Drawing

    private func drawCollage(_ context: inout GraphicsContext) {
        let stage = engine.stage
        if stage.width == 0 { return }
        let o = scatterCollageHeroClamp(engine.open.value)
        // The hero's own tile is drawn by the hero; the rest fade out under it, and back in a little
        // while it is dragged down.
        let hidden = engine.heroOn && !engine.ids.isEmpty ? engine.ids[engine.front] : -1
        let peek = scatterCollageHeroClamp(engine.dragY.value / (stage.height * M.peekSpan))
        let others = 1 - o * (1 - M.peek * peek)
        // Far tiles first, so the near ones pass over them.
        let field = engine.tiles.indices.map { ($0, engine.geo($0)) }.sorted { $0.1.u < $1.1.u }
        for (i, g) in field where i != hidden {
            let alpha = g.alpha * engine.weight(i) * others
            if alpha < 0.004 { continue }
            if g.x > stage.width || g.y > stage.height || g.x + g.w < 0 || g.y + g.h < 0 { continue }
            drawPhoto(&context, index: i, sharp: false, rect: CGRect(x: g.x, y: g.y, width: g.w, height: g.h), radius: g.r, shift: 0, alpha: alpha, shade: 0)
        }
    }

    // In flight (opening, closing or dragged down) the hero is one photo between its slot and the
    // screen; open, it is a row of screens paged sideways.
    private func drawHero(_ context: inout GraphicsContext) {
        let stage = engine.stage
        if stage.width == 0 || !engine.heroOn || engine.ids.isEmpty { return }
        let list = engine.ids
        // Int(floor(p)) below traps on NaN.
        let p = engine.page.value.isFinite ? engine.page.value : 0
        let o = engine.open.value
        let dx = engine.dragX.value
        let dy = engine.dragY.value
        let screen = CGRect(x: 0, y: 0, width: stage.width, height: stage.height)
        let shade = scatterCollageHeroReveal(o, 0) * (1 - scatterCollageHeroClamp(dy / 160))
        let flying = scatterCollageHeroClamp(o) < 0.999 || engine.closing || abs(dx) > 0.5 || abs(dy) > 0.5
        if flying {
            let index = list[engine.front]
            if engine.reduced {
                drawPhoto(&context, index: index, sharp: true, rect: screen, radius: 0, shift: 0, alpha: scatterCollageHeroClamp(o), shade: shade)
                return
            }
            let frame = engine.heroRect(slot: engine.geo(index))
            drawPhoto(&context, index: index, sharp: true, rect: frame.rect, radius: frame.radius, shift: 0, alpha: 1, shade: shade)
            return
        }
        let k0 = Int(floor(p))
        for k in k0...(k0 + 1) where k >= 0 && k < list.count {
            let d = Double(k) - p
            if abs(d) >= 1 { continue }
            if engine.reduced {
                drawPhoto(&context, index: list[k], sharp: true, rect: screen, radius: 0, shift: 0, alpha: 1 - abs(d), shade: shade)
            } else {
                drawPhoto(&context, index: list[k], sharp: true, rect: screen.offsetBy(dx: d * stage.width, dy: 0), radius: 0, shift: -d * stage.width * M.parallax, alpha: 1, shade: shade)
            }
        }
    }

    /// A photo in a rounded box, cropped to cover it: paper until the small copy arrives, then the
    /// small copy, then the full-size one fading over it. `shift` slides the photo inside its box
    /// (the paging parallax); `shade` darkens the top and the bottom for the hero's copy.
    private func drawPhoto(_ context: inout GraphicsContext, index: Int, sharp: Bool, rect: CGRect, radius: Double, shift: Double, alpha: Double, shade: Double) {
        let now = engine.elapsed
        let low = engine.low[index]
        let high = sharp ? engine.high[index] : nil
        let lowIn = low.map { scatterCollageHeroClamp((now - $0.at) / M.photoFade) } ?? 0
        let highIn = high.map { scatterCollageHeroClamp((now - $0.at) / M.sharpFade) } ?? 0
        var c = context
        c.clip(to: Path(roundedRect: rect, cornerRadius: radius, style: .circular))
        if lowIn < 1 && highIn < 1 {
            c.opacity = alpha
            c.fill(Path(rect), with: .color(S.paper))
        }
        if let low, lowIn > 0, highIn < 1 {
            c.opacity = alpha * lowIn
            c.draw(Image(uiImage: low.image), in: cover(low.image.size, rect, shift))
        }
        if let high, highIn > 0 {
            c.opacity = alpha * highIn
            c.draw(Image(uiImage: high.image), in: cover(high.image.size, rect, shift))
        }
        if shade > 0.004 {
            c.opacity = alpha * shade
            let veil = Color(red: 12 / 255, green: 12 / 255, blue: 10 / 255)
            c.fill(
                Path(rect),
                with: .linearGradient(
                    Gradient(stops: [
                        .init(color: veil.opacity(0.42), location: 0),
                        .init(color: veil.opacity(0), location: 0.2),
                        .init(color: veil.opacity(0.12), location: 0.42),
                        .init(color: veil.opacity(0.86), location: 1),
                    ]),
                    startPoint: CGPoint(x: 0, y: rect.minY),
                    endPoint: CGPoint(x: 0, y: rect.maxY)
                )
            )
        }
    }

    /// The rect a photo of `size` is drawn in to cover `box`, centred, slid by `shift`.
    private func cover(_ size: CGSize, _ box: CGRect, _ shift: Double) -> CGRect {
        let scale = max(box.width / size.width, box.height / size.height)
        let w = size.width * scale
        let h = size.height * scale
        return CGRect(x: box.midX - w / 2 + shift, y: box.midY - h / 2, width: w, height: h)
    }
}

// MARK: - Hero copy

/// A hero's words: guide, title, line and the time and date beside Start, sliding with its page.
private struct ScatterCollageHeroCopy: View {
    let engine: ScatterCollageHeroEngine
    let time: Double
    let index: Int
    let session: ScatterCollageHeroSession

    private typealias M = ScatterCollageHeroMetrics
    private typealias S = ScatterCollageHeroStyle

    var body: some View {
        let stage = engine.stage
        ZStack(alignment: .topLeading) {
            Color.clear
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 8) {
                    Text(session.guide.split(separator: " ").compactMap(\.first).map(String.init).joined())
                        .font(.system(size: 10, weight: .bold))
                        .foregroundStyle(.white)
                        .frame(width: 26, height: 26)
                        .background(Circle().fill(.white.opacity(0.22)))
                        .overlay(Circle().strokeBorder(.white.opacity(0.5), lineWidth: 1 / 3))
                    Text(session.guide)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white)
                }
                .padding(.bottom, 12)
                .modifier(line(0))
                Text(session.title.0)
                    .font(S.serif(56, bold: true))
                    .kerning(-0.5)
                    .foregroundStyle(.white)
                    .frame(height: 58)
                    .modifier(line(1))
                Text(session.title.1)
                    .font(S.serif(56, italic: true))
                    .kerning(-0.5)
                    .foregroundStyle(.white)
                    .frame(height: 58)
                    .modifier(line(2))
                Text(ScatterCollageHeroSession.blurbs[session.category] ?? "")
                    .font(.system(size: 14))
                    .lineSpacing(20 - UIFont.systemFont(ofSize: 14).lineHeight)
                    .foregroundStyle(.white.opacity(0.74))
                    .lineLimit(2)
                    .frame(maxWidth: 320, alignment: .leading)
                    .padding(.top, 12)
                    .modifier(line(3))
            }
            .lineLimit(1)
            .frame(width: stage.width - M.side * 2, height: max(0, stage.height - M.copyBottom(stage.bottom)), alignment: .bottomLeading)
            .offset(x: M.side)

            HStack {
                HStack(spacing: 8) {
                    ScatterCollageHeroIcon.clock
                    VStack(alignment: .leading, spacing: 0) {
                        Text("Time").font(.system(size: 10, weight: .medium)).kerning(0.3).foregroundStyle(.white.opacity(0.6))
                        Text("\(session.minutes) min").font(.system(size: 14, weight: .semibold)).monospacedDigit().foregroundStyle(.white)
                    }
                }
                Spacer(minLength: 0)
                HStack(spacing: 8) {
                    VStack(alignment: .trailing, spacing: 0) {
                        Text("Added").font(.system(size: 10, weight: .medium)).kerning(0.3).foregroundStyle(.white.opacity(0.6))
                        Text(session.date).font(.system(size: 14, weight: .semibold)).monospacedDigit().foregroundStyle(.white)
                    }
                    ScatterCollageHeroIcon.calendar
                }
            }
            .frame(width: stage.width - M.side * 2, height: M.rowHeight)
            .modifier(line(4))
            .offset(x: M.side, y: stage.height - M.rowBottom(stage.bottom) - M.rowHeight)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(abs(Double(index) - engine.page.value) > 0.5)
    }

    private func line(_ order: Int) -> ScatterCollageHeroLine {
        let d = Double(index) - engine.page.value
        let shown = scatterCollageHeroReveal(engine.open.value, order)
        let nearby = scatterCollageHeroClamp(1 - abs(d) * 1.6)
        let drag = 1 - scatterCollageHeroClamp(engine.dragY.value / 140)
        return ScatterCollageHeroLine(
            alpha: shown * nearby * drag,
            x: engine.reduced ? 0 : d * engine.stage.width * M.copyParallax,
            y: engine.reduced ? 0 : (1 - shown) * M.revealRise
        )
    }
}

private struct ScatterCollageHeroLine: ViewModifier {
    let alpha: Double
    let x: Double
    let y: Double

    func body(content: Content) -> some View {
        content.opacity(alpha).offset(x: x, y: y)
    }
}

// MARK: - Hero controls

/// What stays put while the heroes page: close, the avatar, Start and the thumbnail strip.
private struct ScatterCollageHeroControls: View {
    let engine: ScatterCollageHeroEngine
    let time: Double
    let list: [Int]
    let accent: Color

    private typealias M = ScatterCollageHeroMetrics
    private typealias S = ScatterCollageHeroStyle

    var body: some View {
        let stage = engine.stage
        let inner = stage.width - M.side * 2
        let slot = (inner - M.thumbGap * 3) / 4
        // The active thumbnail stays in the second slot until the strip runs out.
        let shift = min(max(0, Double(list.count - 4)), max(0, engine.page.value - 1))
        ZStack(alignment: .topLeading) {
            Color.clear

            HStack {
                Button(action: engine.close) {
                    ScatterCollageHeroIcon.chevron
                        .frame(width: 40, height: 40)
                        .background(Circle().fill(.white.opacity(0.18)))
                        .overlay(Circle().strokeBorder(.white.opacity(0.35), lineWidth: 1 / 3))
                        .contentShape(Circle().inset(by: -8))
                }
                .buttonStyle(ScatterCollageHeroPress())
                .accessibilityLabel("Close")
                Spacer(minLength: 0)
                ScatterCollageHeroAvatar(initials: "AK", tone: accent)
            }
            .frame(width: stage.width - (M.side - 4) - M.side)
            .modifier(fade(0))
            .offset(x: M.side - 4, y: stage.top + 6)

            Button(action: engine.start) {
                HStack(spacing: 10) {
                    ScatterCollageHeroIcon.play(accent)
                        .frame(width: 36, height: 36)
                        .background(Circle().fill(S.ink))
                    Text("Start")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(S.ink)
                }
                .padding(.leading, 6)
                .padding(.trailing, 22)
                .frame(height: M.rowHeight)
                .background(Capsule().fill(accent))
            }
            .buttonStyle(ScatterCollageHeroPress())
            .accessibilityLabel("Start")
            .frame(width: stage.width, height: M.rowHeight)
            .modifier(fade(4))
            .offset(y: stage.height - M.rowBottom(stage.bottom) - M.rowHeight)

            HStack(spacing: M.thumbGap) {
                ForEach(Array(list.enumerated()), id: \.element) { k, index in
                    thumb(k: k, index: index, size: slot)
                }
            }
            .offset(x: -shift * (slot + M.thumbGap))
            .padding(.top, 6)
            .frame(width: inner, height: M.thumbHeight + 8, alignment: .topLeading)
            .clipped()
            .modifier(fade(5))
            .offset(x: M.side, y: stage.height - M.stackBottom(stage.bottom) - M.thumbHeight - 8)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
    }

    /// One thumbnail in the strip: the hero in front is lifted, ringed and fully lit.
    private func thumb(k: Int, index: Int, size: Double) -> some View {
        let near = scatterCollageHeroClamp(1 - abs(Double(k) - engine.page.value))
        let shape = RoundedRectangle(cornerRadius: 14, style: .continuous)
        let session = engine.sessions[index]
        return Button { engine.pick(k) } label: {
            Color.white.opacity(0.12)
                .overlay {
                    if let image = engine.low[index]?.image {
                        Image(uiImage: image).resizable().scaledToFill()
                    }
                }
                .frame(width: size, height: M.thumbHeight)
                .clipShape(shape)
                .overlay(shape.strokeBorder(.white, lineWidth: 2).opacity(near))
                .opacity(0.55 + 0.45 * near)
                .offset(y: -5 * near)
        }
        .buttonStyle(.plain)
        .accessibilityLabel("\(session.title.0) \(session.title.1)")
    }

    private func fade(_ order: Int) -> ScatterCollageHeroLine {
        let shown = scatterCollageHeroReveal(engine.open.value, order)
        return ScatterCollageHeroLine(
            alpha: shown * (1 - scatterCollageHeroClamp(engine.dragY.value / 140)),
            x: 0,
            y: (1 - shown) * M.revealRise
        )
    }
}

private struct ScatterCollageHeroPress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.96 : 1)
    }
}

private struct ScatterCollageHeroAvatar: View {
    let initials: String
    let tone: Color

    var body: some View {
        Text(initials)
            .font(.system(size: 13, weight: .bold))
            .kerning(0.4)
            .foregroundStyle(ScatterCollageHeroStyle.ink)
            .frame(width: 40, height: 40)
            .background(Circle().fill(tone))
    }
}

/// The few line icons, drawn on a 24-unit grid like the SVGs of the React Native version.
private enum ScatterCollageHeroIcon {
    private static func icon(size: CGFloat, width: CGFloat = 1.7, color: Color = .white, _ build: @escaping (inout Path) -> Void) -> some View {
        Canvas { context, canvas in
            var path = Path()
            build(&path)
            let k = canvas.width / 24
            context.stroke(
                path.applying(CGAffineTransform(scaleX: k, y: k)),
                with: .color(color),
                style: StrokeStyle(lineWidth: width * k, lineCap: .round, lineJoin: .round)
            )
        }
        .frame(width: size, height: size)
    }

    static var clock: some View {
        icon(size: 20) { p in
            p.addEllipse(in: CGRect(x: 3, y: 3, width: 18, height: 18))
            p.move(to: CGPoint(x: 12, y: 7.5))
            p.addLine(to: CGPoint(x: 12, y: 12))
            p.addLine(to: CGPoint(x: 15, y: 14))
        }
    }

    static var calendar: some View {
        icon(size: 20) { p in
            p.addRoundedRect(in: CGRect(x: 3.5, y: 6.5, width: 17, height: 14), cornerSize: CGSize(width: 1.5, height: 1.5))
            p.move(to: CGPoint(x: 3.5, y: 10.5))
            p.addLine(to: CGPoint(x: 20.5, y: 10.5))
            p.move(to: CGPoint(x: 8, y: 4))
            p.addLine(to: CGPoint(x: 8, y: 8))
            p.move(to: CGPoint(x: 16, y: 4))
            p.addLine(to: CGPoint(x: 16, y: 8))
        }
    }

    static var chevron: some View {
        icon(size: 18, width: 2.4) { p in
            p.move(to: CGPoint(x: 5, y: 9))
            p.addLine(to: CGPoint(x: 12, y: 16))
            p.addLine(to: CGPoint(x: 19, y: 9))
        }
    }

    /// The play triangle, rounded at its corners, nudged 2 pt right of the circle's middle.
    static func play(_ color: Color) -> some View {
        Canvas { context, canvas in
            let k = canvas.width / 24
            var path = Path()
            path.move(to: CGPoint(x: 8, y: 5.2))
            path.addLine(to: CGPoint(x: 8, y: 18.8))
            path.addLine(to: CGPoint(x: 19.6, y: 12))
            path.closeSubpath()
            let scaled = path.applying(CGAffineTransform(scaleX: k, y: k))
            context.fill(scaled, with: .color(color))
            context.stroke(scaled, with: .color(color), style: StrokeStyle(lineWidth: 2 * k, lineJoin: .round))
        }
        .frame(width: 12, height: 12)
        .offset(x: 1)
    }
}
