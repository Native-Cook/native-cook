import SwiftUI
import UIKit

/// One onboarding page: a short headline and the line under it.
struct VelocityMarqueePage {
    var title: String
    var body: String
}

extension VelocityMarqueePage {
    static let samples: [VelocityMarqueePage] = [
        .init(title: "Photos that keep moving", body: "Afterimage strings your camera roll into reels that drift by on their own."),
        .init(title: "Flick a reel to travel back", body: "Throw it to race through a year of days, then let it ease to a stop."),
        .init(title: "Share the whole reel", body: "Send a moving set of frames to the people who were there with you."),
    ]
}

/// An onboarding screen whose hero is three tilted rows of photo tiles running as endless
/// marquees, the middle one the other way. Swiping through the pages throws the rows with the
/// finger's speed: they race, the tiles lean into the motion and stretch, then ease back to the
/// idle drift while the headline under them changes. A "Get started" button and a sign-in line
/// sit at the foot.
struct VelocityMarquee: View {
    /// The rows, top to bottom; the middle one runs the other way. Ten to fourteen photos a row.
    var rows: [[VelocityMarqueePhoto]] = VelocityMarqueePhoto.rows
    /// The pages, swiped through left and right; the dots under the rows follow them.
    var pages: [VelocityMarqueePage] = VelocityMarqueePage.samples
    /// The app's name, next to its mark at the top.
    var name = "Afterimage"
    /// Rests on each page, then swipes to the next one, throwing the rows, and starts over.
    var autoPlay = false
    var onGetStarted: (() -> Void)? = nil
    var onSignIn: (() -> Void)? = nil
    /// Called with a page's index when a swipe lands on it.
    var onPage: ((Int) -> Void)? = nil
    /// The paper.
    var background = Color(hex: 0xf2efe8)
    var ink = Color(hex: 0x161512)

    private typealias M = VelocityMarqueeMetrics

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = VelocityMarqueeEngine()
    @State private var buttonPressed = false
    @State private var signInPressed = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        // The block draws under the system bars and keeps its type clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, top: max(insets.top, M.minTop), bottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .background(background)
                .clipped()
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: rows.flatMap { $0.map(\.id) }) { await loadPhotos() }
    }

    private func stage(size: CGSize, top: Double, bottom: Double) -> some View {
        let layout = VelocityMarqueeLayout(size: size, top: top, bottom: bottom, pages: pages)
        return ZStack(alignment: .topLeading) {
            // The paper, drawn once per size by a canvas that never redraws per frame.
            VelocityMarqueePaper(size: size, paper: background)
                .equatable()
                .allowsHitTesting(false)

            TimelineView(.animation) { timeline in
                let _ = engine.configure(rows: rows, pages: pages.count, autoPlay: autoPlay, reducedMotion: reduceMotion)
                let _ = engine.onPage = onPage
                let _ = size.width > 0 ? engine.enterOnce() : ()
                let frame = engine.step(to: timeline.date)
                scene(frame, layout: layout)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.dragChanged(translation: $0.translation, velocity: $0.velocity) }
                .onEnded { engine.dragEnded(translation: $0.translation, velocity: $0.velocity, width: size.width) }
        )
        .accessibilityElement(children: .contain)
        .accessibilityLabel("\(name) introduction, \(pages.count) pages. Swipe left or right to turn the page.")
        .accessibilityAdjustableAction { direction in
            engine.turn(direction == .increment ? 1 : -1)
        }
    }

    private func scene(_ frame: VelocityMarqueeFrame, layout: VelocityMarqueeLayout) -> some View {
        ZStack(alignment: .topLeading) {
            // The rows rise and fade in once everything has a place.
            VelocityMarqueeRows(engine: engine, frame: frame, layout: layout)
                .opacity(frame.hero)
                .offset(y: reduceMotion ? 0 : (1 - frame.hero) * M.heroRise)
                .allowsHitTesting(false)

            brand
                .frame(width: layout.size.width - M.margin * 2, height: M.headerHeight)
                .modifier(VelocityMarqueeEnter(progress: frame.enter[0], still: reduceMotion))
                .offset(x: M.margin, y: layout.top + M.headerTop)
                .allowsHitTesting(false)

            foot(frame, layout: layout)
                .offset(x: M.margin, y: layout.footTop)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    /// The app's mark and name, centred over the rows.
    private var brand: some View {
        HStack(spacing: 8) {
            VelocityMarqueeMark(ink: ink, paper: background)
            Text(name)
                .font(.custom(M.semibold, fixedSize: 19))
                .kerning(-0.2)
                .foregroundStyle(ink)
                .lineLimit(1)
                .fixedSize()
                .frame(height: 24)
        }
    }

    /// The dots, the page's text, the button and the sign-in line.
    private func foot(_ frame: VelocityMarqueeFrame, layout: VelocityMarqueeLayout) -> some View {
        let width = layout.textWidth
        return VStack(alignment: .leading, spacing: 0) {
            VelocityMarqueeDots(count: pages.count, page: frame.page, ink: ink)
                .frame(width: width, height: M.dot)
                .modifier(VelocityMarqueeEnter(progress: frame.enter[1], still: reduceMotion))
                .padding(.bottom, M.dotsBelow)
                .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                ForEach(pages.indices, id: \.self) { i in
                    // A page's text is fully faded out past `textGone`, so it is left out there:
                    // only the page shown is read out.
                    if abs(M.pageDistance(frame.page, i, pages.count)) < M.textGone {
                        pageText(i, page: frame.page, layout: layout)
                    }
                }
            }
            .frame(width: width, height: layout.textHeight, alignment: .topLeading)
            .modifier(VelocityMarqueeEnter(progress: frame.enter[2], still: reduceMotion))
            .padding(.bottom, M.textBelow)
            .allowsHitTesting(false)

            button
                .frame(width: width)
                .modifier(VelocityMarqueeEnter(progress: frame.enter[3], still: reduceMotion))

            signIn
                .frame(width: width)
                .modifier(VelocityMarqueeEnter(progress: frame.enter[4], still: reduceMotion))
                .padding(.top, M.signInAbove)
        }
    }

    /// One page's headline and line, sliding with the page and gone before the next one arrives.
    private func pageText(_ index: Int, page: Double, layout: VelocityMarqueeLayout) -> some View {
        let d = M.pageDistance(page, index, pages.count)
        let lines = layout.lines[index]
        return VStack(alignment: .leading, spacing: M.textGap) {
            VStack(alignment: .leading, spacing: 0) {
                ForEach(lines.title.indices, id: \.self) { k in
                    Text(lines.title[k])
                        .font(.custom(M.semibold, fixedSize: M.titleSize))
                        .kerning(M.titleKerning)
                        .fixedSize()
                        .frame(height: M.titleLine)
                }
            }
            VStack(alignment: .leading, spacing: 0) {
                ForEach(lines.body.indices, id: \.self) { k in
                    Text(lines.body[k])
                        .font(.custom(M.regular, fixedSize: M.bodySize))
                        .fixedSize()
                        .frame(height: M.bodyLine)
                }
            }
            .opacity(0.6)
        }
        .foregroundStyle(ink)
        .frame(width: layout.textWidth, alignment: .topLeading)
        .opacity(min(max(1 - abs(d) / M.textGone, 0), 1))
        .offset(x: reduceMotion ? 0 : -d * M.textShift)
        // Read as one passage.
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(pages[index].title). \(pages[index].body)")
    }

    /// "Get started": squeezes on touch down with a light tap and springs back on release.
    private var button: some View {
        Text("Get started")
            .font(.custom(M.semibold, fixedSize: 17))
            .kerning(0.1)
            .foregroundStyle(background)
            .frame(maxWidth: .infinity)
            .frame(height: M.buttonHeight)
            .background(Capsule().fill(ink))
            .contentShape(Capsule())
            .scaleEffect(buttonPressed ? M.pressScale : 1)
            .animation(.interpolatingSpring(mass: 1, stiffness: M.pressStiffness, damping: M.pressDamping), value: buttonPressed)
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !buttonPressed {
                            buttonPressed = true
                            haptic.impactOccurred()
                        }
                    }
                    .onEnded { value in
                        buttonPressed = false
                        if hypot(value.translation.width, value.translation.height) <= 40 { onGetStarted?() }
                    }
            )
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Get started")
            .accessibilityAddTraits(.isButton)
            .accessibilityAction { onGetStarted?() }
    }

    /// "Already have an account? Sign in", dimming while pressed.
    private var signIn: some View {
        HStack(spacing: 5) {
            Text("Already have an account?")
                .font(.custom(M.regular, fixedSize: 15))
                .opacity(0.55)
            Text("Sign in")
                .font(.custom(M.semibold, fixedSize: 15))
        }
        .foregroundStyle(ink)
        .lineLimit(1)
        .fixedSize()
        .padding(.horizontal, 12)
        .frame(height: M.signInHeight)
        .contentShape(Rectangle())
        .opacity(signInPressed ? 0.45 : 1)
        // Reanimated's default timing curve, an ease-in-out quad: 100 ms down, 200 ms back.
        .animation(.timingCurve(0.455, 0.03, 0.515, 0.955, duration: signInPressed ? 0.1 : 0.2), value: signInPressed)
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in if !signInPressed { signInPressed = true } }
                .onEnded { value in
                    signInPressed = false
                    if hypot(value.translation.width, value.translation.height) <= 30 { onSignIn?() }
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Sign in")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { onSignIn?() }
    }

    // The photos are drawn on the canvas, each fading in as it arrives; a photo that fails stays paper.
    private func loadPhotos() async {
        engine.configure(rows: rows, pages: pages.count, autoPlay: autoPlay, reducedMotion: reduceMotion)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, photo) in rows.joined().enumerated() {
                let address = photo.url(width: photo.portrait ? M.portraitPx : M.landscapePx)
                group.addTask { (index, await VelocityMarqueePhotos.load(address)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

/// Where things sit on a screen of this size: the text broken into lines, the text box as tall as
/// the tallest page, the foot above the safe bottom and the hero between the brand line and it.
@MainActor
struct VelocityMarqueeLayout {
    typealias Lines = (title: [String], body: [String])

    let size: CGSize
    let top: Double
    let textWidth: Double
    let lines: [Lines]
    let textHeight: Double
    let footTop: Double
    let heroTop: Double
    let heroBottom: Double

    init(size: CGSize, top: Double, bottom: Double, pages: [VelocityMarqueePage]) {
        typealias M = VelocityMarqueeMetrics
        self.size = size
        self.top = top
        let width = max(0, size.width - M.margin * 2)
        textWidth = width
        lines = pages.map { page in
            (
                VelocityMarqueeLines.split(page.title, font: M.semibold, size: M.titleSize, kerning: M.titleKerning, width: width, limit: M.titleLines),
                VelocityMarqueeLines.split(page.body, font: M.regular, size: M.bodySize, kerning: 0, width: width, limit: M.bodyLines)
            )
        }
        textHeight = lines.map { Double($0.title.count) * M.titleLine + M.textGap + Double($0.body.count) * M.bodyLine }.max() ?? 0
        let foot = M.dot + M.dotsBelow + textHeight + M.textBelow + M.buttonHeight + M.signInAbove + M.signInHeight
        footTop = size.height - bottom - M.footGap - foot
        heroTop = top + M.headerTop + M.headerHeight + M.heroGap
        heroBottom = max(heroTop + 1, footTop - M.heroGap)
    }
}

/// Fades and rises in as its entrance runs.
private struct VelocityMarqueeEnter: ViewModifier {
    let progress: Double
    let still: Bool

    func body(content: Content) -> some View {
        content
            .opacity(progress)
            .offset(y: still ? 0 : (1 - progress) * VelocityMarqueeMetrics.enterRise)
    }
}

/// The app's mark: a solid disc linked with a ring, the ring cut where it crosses the disc.
private struct VelocityMarqueeMark: View {
    let ink: Color
    let paper: Color

    var body: some View {
        Canvas { context, _ in
            let disc = Path(ellipseIn: CGRect(x: 1, y: 1, width: 18, height: 18))
            let ring = Path(ellipseIn: CGRect(x: 19 - 7.5, y: 10 - 7.5, width: 15, height: 15))
            context.fill(disc, with: .color(ink))
            context.stroke(ring, with: .color(ink), lineWidth: 2)
            var inside = context
            inside.clip(to: disc)
            inside.stroke(ring, with: .color(paper), lineWidth: 2)
        }
        .frame(width: 28, height: 20)
    }
}

/// The page dots: wide and dark for the page shown, a small faint dot for the others.
private struct VelocityMarqueeDots: View {
    let count: Int
    let page: Double
    let ink: Color

    private typealias M = VelocityMarqueeMetrics

    var body: some View {
        Canvas { context, _ in
            var left = 0.0
            for k in 0..<max(0, count) {
                let near = min(max(1 - abs(M.pageDistance(page, k, count)), 0), 1)
                let width = M.dot + (M.activeWidth - M.dot) * near
                let pill = Path(roundedRect: CGRect(x: left, y: 0, width: width, height: M.dot), cornerRadius: M.dot / 2)
                context.fill(pill, with: .color(ink.opacity(0.2 + 0.8 * near)))
                left += width + M.dotGap
            }
        }
    }
}

/// The paper: a flat colour and a fine grain. Its inputs only change with the screen, so it is
/// drawn once.
private struct VelocityMarqueePaper: View, Equatable {
    let size: CGSize
    let paper: Color

    var body: some View {
        Canvas { context, _ in
            let all = CGRect(origin: .zero, size: size)
            context.fill(Path(all), with: .color(paper))
            if let grain = VelocityMarqueeGrain.image {
                let tile = Image(decorative: grain, scale: 1).interpolation(.none)
                context.fill(Path(all), with: .tiledImage(tile, origin: .zero, sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: VelocityMarqueeMetrics.grainTexel))
            }
        }
        .frame(width: size.width, height: size.height)
    }
}

/// The rows, each turned about its own centre line, filling the hero.
private struct VelocityMarqueeRows: View {
    let engine: VelocityMarqueeEngine
    let frame: VelocityMarqueeFrame
    let layout: VelocityMarqueeLayout

    private typealias M = VelocityMarqueeMetrics

    var body: some View {
        Canvas { context, _ in draw(&context) }
            .frame(width: layout.size.width, height: layout.size.height)
    }

    private func draw(_ context: inout GraphicsContext) {
        let rows = engine.rows
        let width = layout.size.width
        if width == 0 || rows.isEmpty { return }
        let x = frame.travel
        let lean = frame.lean
        let reference = engine.reference
        let angle = min(max(lean * M.leanPer, -M.leanMax), M.leanMax)
        let skew = tan(angle * .pi / 180)

        // The rows fill the hero, growing or shrinking together with the screen; the tilt lifts
        // the right end of each row and drops the left, so that is counted in the height they need.
        let rise = width * tan(abs(M.tilt) * .pi / 180)
        let need = Double(rows.count - 1) * M.rowPitch + M.rowHeight + rise
        let scale = min(max((layout.heroBottom - layout.heroTop) / need, M.minHeroScale), M.maxHeroScale)
        let midY = (layout.heroTop + layout.heroBottom) / 2
        let span = width / 2 / scale + M.landscapeWidth
        let stretch = 1 + min(M.stretchMax, abs(lean) * M.stretchPer)
        let thin = 1 - (stretch - 1) / 3
        var hero = context
        hero.translateBy(x: width / 2, y: midY)
        hero.scaleBy(x: scale, y: scale)
        for (r, row) in rows.enumerated() {
            let moved = row.dir * x * (row.length / reference)
            var c = hero
            c.translateBy(x: 0, y: (Double(r) - Double(rows.count - 1) / 2) * M.rowPitch)
            c.rotate(by: .degrees(M.tilt))
            for tile in row.tiles {
                let center = M.wrap(tile.offset + tile.width / 2 + moved + row.length / 2, row.length) - row.length / 2
                if abs(center) - tile.width / 2 > span { continue }
                var t = c
                t.translateBy(x: center, y: 0)
                // A row moving right leans its tops to the right, like type set in italic.
                t.concatenate(CGAffineTransform(a: 1, b: 0, c: -row.dir * skew, d: 1, tx: 0, ty: 0))
                t.scaleBy(x: stretch, y: thin)
                drawPhoto(&t, index: tile.index, w: tile.width, h: M.rowHeight)
            }
        }
    }

    /// One photo centred on the origin, clipped to a rounded box: paper until it arrives, then fading in.
    private func drawPhoto(_ context: inout GraphicsContext, index: Int, w: Double, h: Double) {
        let box = CGRect(x: -w / 2, y: -h / 2, width: w, height: h)
        context.clip(to: Path(roundedRect: box, cornerRadius: M.tileRadius, style: .circular))
        let loaded = engine.loaded.indices.contains(index) ? engine.loaded[index] : nil
        let shown = loaded.map { min(max((frame.elapsed - $0.at) / M.photoFade, 0), 1) } ?? 0
        if shown < 1 {
            context.fill(Path(box), with: .color(M.tilePaper))
        }
        if let loaded, shown > 0 {
            let size = loaded.image.size
            guard size.width > 0, size.height > 0 else { return }
            let scale = max(w / size.width, h / size.height)
            let dw = size.width * scale
            let dh = size.height * scale
            context.opacity = shown
            context.draw(Image(uiImage: loaded.image), in: CGRect(x: -dw / 2, y: -dh / 2, width: dw, height: dh))
        }
    }
}
