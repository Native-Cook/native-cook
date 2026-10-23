import SwiftUI
import UIKit

/// A bento of seven field notes on warm paper: photos, a reading stat, a quote and a little map.
/// Tapping a tile grows it in place into its page while the other tiles are pushed away; the photo
/// becomes the page's header and slides under the text as it scrolls. Dragging the page down
/// shrinks it under the finger; letting go puts it back in its tile.
struct BentoExpandGrid: View {
    /// The seven notes on the grid, each with its place on a 4 × 4 grid.
    var notes: [BentoExpandGridNote] = BentoExpandGridNote.samples
    /// Opens a tile, scrolls its page a little, closes it, opens another, and starts over.
    var autoPlay = false
    /// The paper behind the grid and the pages.
    var background = Color(hex: 0xf1ebe0)
    var ink = Color(hex: 0x1f1c18)
    /// The one accent: kickers, the map's pin, the stat's dot and the pull quote's rule.
    var accent = Color(hex: 0xc4582f)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = BentoExpandGridEngine()

    var body: some View {
        // The block draws under the system bars and keeps its text clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            // Nothing to lay out until the screen is measured: a zero size gives tiles negative frames
            // and the sheet a 0 width to divide by.
            if size.width > 0 && size.height > 0 {
                TimelineView(.animation) { timeline in
                    let _ = engine.configure(notes: notes, autoPlay: autoPlay, reducedMotion: reduceMotion)
                    let _ = engine.layout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
                    let _ = engine.step(to: timeline.date)
                    BentoExpandGridScene(engine: engine, time: engine.elapsed, background: background, ink: ink, accent: accent)
                }
                .frame(width: size.width, height: size.height)
                .background(background)
                .clipped()
                .offset(x: -insets.leading, y: -insets.top)
            }
        }
        .task(id: notes.map(\.id)) { await loadPhotos() }
    }

    // Header photos are shared by the tile and the page; the pages' pairs are fetched ahead, so a
    // page never opens onto empty frames.
    private func loadPhotos() async {
        typealias M = BentoExpandGridMetrics
        var addresses: [String] = []
        for note in notes {
            if case let .photo(id) = note.face { addresses.append(BentoExpandGridNote.photoURL(id, width: M.headerPx)) }
        }
        for note in notes {
            addresses += note.pair.map { BentoExpandGridNote.photoURL($0, width: M.pairPx) }
        }
        await withTaskGroup(of: (String, UIImage?).self) { group in
            for address in addresses {
                group.addTask { (address, await BentoExpandGridPhotos.load(address)) }
            }
            for await (address, image) in group {
                if let image { engine.photoArrived(image, for: address) }
            }
        }
    }
}

// MARK: - Style

enum BentoExpandGridStyle {
    static let muted = Color(hex: 0x8b8173)
    static let statPaper = Color(hex: 0x1f1c18)
    static let quotePaper = Color(hex: 0xe5dbc9)
    static let mapPaper = Color(hex: 0xe9e1d1)
    static let photoPaper = Color(hex: 0xddd3c2)
    static let cream = Color(hex: 0xf1ebe0)

    static func serif(_ size: CGFloat, italic: Bool = false) -> Font {
        .custom(italic ? "Georgia-Italic" : "Georgia", fixedSize: size)
    }

    static func paper(_ face: BentoExpandGridFace) -> Color {
        switch face {
        case .photo: photoPaper
        case .stat: statPaper
        case .quote: quotePaper
        case .map: mapPaper
        }
    }
}

/// Lines set `lineHeight` apart with the extra space split above and below, like React Native.
private struct BentoExpandGridLines: ViewModifier {
    let font: UIFont
    let lineHeight: CGFloat

    func body(content: Content) -> some View {
        let extra = max(0, lineHeight - font.lineHeight)
        content
            .lineSpacing(extra)
            .padding(.vertical, extra / 2)
    }
}

private extension View {
    func bentoLines(_ font: UIFont, _ lineHeight: CGFloat) -> some View {
        modifier(BentoExpandGridLines(font: font, lineHeight: lineHeight))
    }
}

private func bentoExpandGridGeorgia(_ size: CGFloat, italic: Bool = false) -> UIFont {
    UIFont(name: italic ? "Georgia-Italic" : "Georgia", size: size) ?? .systemFont(ofSize: size)
}

/// A photo drawn to cover its box, fading in over `fade` seconds once it has arrived.
private struct BentoExpandGridPhoto: View {
    let loaded: (image: UIImage, at: Double)?
    let now: Double
    let fade: Double

    var body: some View {
        Color.clear
            .overlay {
                if let loaded {
                    Image(uiImage: loaded.image)
                        .resizable()
                        .scaledToFill()
                        .opacity(fade == 0 ? 1 : bentoExpandGridClamp((now - loaded.at) / fade))
                }
            }
            .clipped()
    }
}

// MARK: - Scene

/// Everything one frame shows: the title block, the tiles, and the open page.
private struct BentoExpandGridScene: View {
    let engine: BentoExpandGridEngine
    /// The engine's clock: a new value every frame, so SwiftUI draws the frame again.
    let time: Double
    let background: Color
    let ink: Color
    let accent: Color

    private typealias M = BentoExpandGridMetrics
    private typealias S = BentoExpandGridStyle

    var body: some View {
        ZStack(alignment: .topLeading) {
            Color.clear.frame(width: engine.width, height: engine.height)

            HStack(alignment: .bottom) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("FIELD NOTES · NO. 07")
                        .font(.system(size: 10, weight: .bold))
                        .kerning(1.6)
                        .foregroundStyle(accent)
                    Text("Slow crafts")
                        .font(S.serif(36))
                        .kerning(-0.8)
                        .foregroundStyle(ink)
                        .padding(.top, 6)
                }
                Spacer(minLength: 0)
                Text("Autumn\n2026")
                    .font(.system(size: 11, weight: .semibold))
                    .bentoLines(.systemFont(ofSize: 11, weight: .semibold), 14)
                    .multilineTextAlignment(.trailing)
                    .foregroundStyle(S.muted)
                    .padding(.bottom, 6)
            }
            .padding(.horizontal, M.margin + 4)
            .frame(width: engine.width)
            .offset(y: engine.top + 14)
            .opacity(engine.titleAlpha)
            .accessibilityHidden(engine.active != nil)

            Group {
                ForEach(Array(engine.notes.enumerated()), id: \.element.id) { index, note in
                    if index < engine.rects.count {
                        tile(index: index, note: note)
                    }
                }
            }
            .accessibilityHidden(engine.active != nil)

            if let index = engine.active {
                BentoExpandGridPage(engine: engine, time: time, index: index, background: background, ink: ink, accent: accent)
            }
        }
        .frame(width: engine.width, height: engine.height, alignment: .topLeading)
    }

    private func tile(index: Int, note: BentoExpandGridNote) -> some View {
        let rect = engine.rects[index]
        let state = engine.tileState(index)
        let label: String = if case .photo = note.face { "\(note.kicker): \(note.title)" } else { note.title }
        return Button {
            engine.tapTile(index)
        } label: {
            BentoExpandGridFaceView(engine: engine, time: time, note: note, index: index, ink: ink, accent: accent)
                .frame(width: rect.width, height: rect.height)
                .background(S.paper(note.face))
                .clipShape(RoundedRectangle(cornerRadius: M.tileRadius, style: .continuous))
                .scaleEffect(engine.pressScale(index))
        }
        .buttonStyle(BentoExpandGridPressStyle { engine.press(index, down: $0) })
        .accessibilityLabel(label)
        .offset(x: state.x, y: state.y)
        .scaleEffect(state.scale)
        .opacity(state.alpha)
        .offset(x: rect.minX, y: rect.minY)
    }
}

/// Reports a press as it starts and ends, so the tile can shrink on the same timing curve as RN.
private struct BentoExpandGridPressStyle: ButtonStyle {
    let onPress: (Bool) -> Void

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { _, pressed in onPress(pressed) }
    }
}

/// What a tile shows at rest: the same drawing the page's header grows from.
private struct BentoExpandGridFaceView: View {
    let engine: BentoExpandGridEngine
    /// The engine's clock, so a photo arriving or fading in is drawn.
    let time: Double
    let note: BentoExpandGridNote
    let index: Int
    let ink: Color
    let accent: Color

    private typealias M = BentoExpandGridMetrics

    var body: some View {
        let rect = engine.rects[index]
        switch note.face {
        case let .photo(id):
            ZStack(alignment: .bottomLeading) {
                BentoExpandGridPhoto(loaded: engine.image(id, width: M.headerPx), now: engine.elapsed, fade: 0.3)
                BentoExpandGridPhotoLabel(note: note, narrow: rect.width < M.narrow)
            }
        case let .map(place, stops):
            ZStack(alignment: .bottomLeading) {
                Canvas { context, size in bentoExpandGridDrawMap(&context, w: size.width, h: size.height, ink: ink, accent: accent) }
                BentoExpandGridMapCaption(place: place, stops: stops, ink: ink)
            }
        case .stat, .quote:
            let grow = engine.grow(index)
            BentoExpandGridTypeFace(face: note.face, grow: grow, ink: ink, accent: accent)
                .frame(width: rect.width * grow, height: rect.height * grow)
                .scaleEffect(1 / grow, anchor: .topLeading)
                .frame(width: rect.width, height: rect.height, alignment: .topLeading)
        }
    }
}

/// A photo tile's caption, on a soft shade at its foot; a narrow tile sets it smaller, on two lines.
private struct BentoExpandGridPhotoLabel: View {
    let note: BentoExpandGridNote
    let narrow: Bool

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(note.kicker.uppercased())
                .font(.system(size: 9, weight: .bold))
                .kerning(1.4)
                .foregroundStyle(.white.opacity(0.75))
            Text(note.title)
                .font(.system(size: narrow ? 13 : 15, weight: .semibold))
                .bentoLines(.systemFont(ofSize: narrow ? 13 : 15, weight: .semibold), narrow ? 17 : UIFont.systemFont(ofSize: 15, weight: .semibold).lineHeight)
                .lineLimit(2)
                .foregroundStyle(.white)
                .padding(.top, 5)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
        .padding(.horizontal, narrow ? 11 : 14)
        .padding(.top, 26)
        .padding(.bottom, narrow ? 10 : 12)
        .background(LinearGradient(colors: [Color(red: 20 / 255, green: 17 / 255, blue: 13 / 255).opacity(0), Color(red: 20 / 255, green: 17 / 255, blue: 13 / 255).opacity(0.55)], startPoint: .top, endPoint: .bottom))
        .allowsHitTesting(false)
    }
}

private struct BentoExpandGridMapCaption: View {
    let place: String
    let stops: Int
    let ink: Color

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            Text(place).font(.system(size: 13, weight: .bold)).foregroundStyle(ink)
            Text("\(stops) stops").font(.system(size: 10, weight: .medium)).foregroundStyle(BentoExpandGridStyle.muted)
        }
        .padding(.leading, 10)
        .padding(.bottom, 9)
        .allowsHitTesting(false)
    }
}

/// The stat and the quote, set at `grow` times their tile's size.
private struct BentoExpandGridTypeFace: View {
    let face: BentoExpandGridFace
    let grow: Double
    let ink: Color
    let accent: Color

    var body: some View {
        let g = grow
        VStack(alignment: .leading, spacing: 0) {
            switch face {
            case let .stat(value, unit, label):
                HStack {
                    Text(label.uppercased())
                        .font(.system(size: 10 * g, weight: .semibold))
                        .kerning(1.4 * g)
                        .foregroundStyle(BentoExpandGridStyle.cream.opacity(0.6))
                    Spacer(minLength: 0)
                    Circle().fill(accent).frame(width: 7 * g, height: 7 * g)
                }
                Spacer(minLength: 0)
                HStack(alignment: .firstTextBaseline, spacing: 6 * g) {
                    Text(value)
                        .font(BentoExpandGridStyle.serif(40 * g))
                        .kerning(-1 * g)
                        .monospacedDigit()
                        .foregroundStyle(BentoExpandGridStyle.cream)
                    Text(unit)
                        .font(.system(size: 13 * g, weight: .medium))
                        .foregroundStyle(BentoExpandGridStyle.cream.opacity(0.7))
                }
            case let .quote(text, by):
                Text(quote(text))
                    .font(BentoExpandGridStyle.serif(18 * g, italic: true))
                    .bentoLines(bentoExpandGridGeorgia(18 * g, italic: true), 23 * g)
                    .foregroundStyle(ink)
                    .fixedSize(horizontal: false, vertical: true)
                Spacer(minLength: 0)
                Text(by.uppercased())
                    .font(.system(size: 10 * g, weight: .semibold))
                    .kerning(1.2 * g)
                    .foregroundStyle(BentoExpandGridStyle.muted)
            default:
                EmptyView()
            }
        }
        .padding(14 * g)
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
    }

    private func quote(_ text: String) -> AttributedString {
        var mark = AttributedString("\u{201C}")
        mark.foregroundColor = accent
        return mark + AttributedString(text)
    }
}

// MARK: - Map

/// The little map: streets on a grid, a river, a dashed walk joining four stops, a pin. It is
/// drawn on a 400 × 300 board, scaled to cover the box; lines keep their width.
func bentoExpandGridDrawMap(_ context: inout GraphicsContext, w: Double, h: Double, ink: Color, accent: Color) {
    let k = max(w / 400, h / 300)
    let ox = (w - 400 * k) / 2
    let oy = (h - 300 * k) / 2
    func p(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: ox + x * k, y: oy + y * k) }
    func line(_ a: CGPoint, _ b: CGPoint) -> Path {
        var path = Path()
        path.move(to: a)
        path.addLine(to: b)
        return path
    }
    var streets = Path()
    for x in stride(from: -40.0, through: 440, by: 34) {
        streets.addPath(line(p(x, -20), p(x + 46, 320)))
    }
    for y in stride(from: -20.0, through: 320, by: 30) {
        streets.addPath(line(p(-20, y), p(420, y - 18)))
    }
    context.stroke(streets, with: .color(ink.opacity(0.12)), lineWidth: 1)
    // Two main roads.
    var roads = line(p(-20, 96), p(420, 70))
    roads.addPath(line(p(250, -20), p(290, 320)))
    context.stroke(roads, with: .color(ink.opacity(0.28)), lineWidth: 2.2)
    // The river.
    var river = Path()
    river.move(to: p(-20, 250))
    river.addCurve(to: p(210, 150), control1: p(90, 210), control2: p(120, 120))
    river.addCurve(to: p(420, 20), control1: p(300, 180), control2: p(330, 40))
    context.stroke(river, with: .color(Color(hex: 0x8fb2bf).opacity(0.7)), style: StrokeStyle(lineWidth: 9, lineCap: .round))
    // The walk, dashed, with its stops.
    let stops = [p(70, 70), p(150, 120), p(235, 205), p(330, 130)]
    var walk = Path()
    walk.addLines(stops)
    context.stroke(walk, with: .color(ink.opacity(0.8)), style: StrokeStyle(lineWidth: 1.6, dash: [4, 4]))
    func dot(_ c: CGPoint, _ r: Double, _ color: Color) {
        context.fill(Path(ellipseIn: CGRect(x: c.x - r, y: c.y - r, width: r * 2, height: r * 2)), with: .color(color))
    }
    for stop in stops.dropLast() {
        dot(stop, 5, BentoExpandGridStyle.mapPaper)
        dot(stop, 3, ink)
    }
    // The pin on the last stop, with a soft halo.
    let pin = stops[stops.count - 1]
    dot(pin, 14, accent.opacity(0.18))
    dot(pin, 6, accent)
    dot(pin, 2.2, .white)
}

// MARK: - Page

/// The open page. Its frame is one clipped box between the tile and the screen; inside it the
/// header (photo, map or type) fills the top and the sheet of text scrolls over it.
private struct BentoExpandGridPage: View {
    let engine: BentoExpandGridEngine
    let time: Double
    let index: Int
    let background: Color
    let ink: Color
    let accent: Color

    private typealias M = BentoExpandGridMetrics
    private typealias S = BentoExpandGridStyle

    // True while a drag is on the page; reset by SwiftUI when the system cancels the drag.
    @GestureState private var dragging = false

    var body: some View {
        let note = engine.notes[index]
        let tile = engine.rects[index]
        let width = engine.width
        let height = engine.height
        let headerH = engine.headerHeight
        let o = engine.open.value
        let f = engine.frame(for: index)
        let reduced = engine.reduced
        // The face fills the whole frame for the first part of the flight, then gives way to the sheet.
        let give = bentoExpandGridSmooth(M.headerGiveFrom, M.headerGiveTo, o)
        let header = bentoExpandGridMix(f.rect.height, headerH * f.scale, give)
        let parallax = reduced ? 0 : -max(0, engine.scrollY) * M.parallax * bentoExpandGridClamp(o)
        // The sheet keeps its page width and is scaled with the frame, so its lines never re-wrap.
        let sheetScale = f.rect.width / width
        let sheetAlpha = reduced ? 1 : bentoExpandGridSmooth(M.sheetFrom, M.sheetTo, o)
        let lifted = reduced ? 0 : bentoExpandGridClamp(sin(.pi * bentoExpandGridClamp(o)) * 0.8 + engine.pull() * 4)
        let chrome = bentoExpandGridSmooth(0.6, 1, o) * (1 - bentoExpandGridClamp(engine.pull() * 3))
        let shape = RoundedRectangle(cornerRadius: f.radius, style: .continuous)

        ZStack(alignment: .topLeading) {
            // The page lifts off the paper on a soft shadow while it flies or is dragged.
            shape
                .fill(S.paper(note.face))
                .shadow(color: Color(red: 31 / 255, green: 28 / 255, blue: 24 / 255).opacity(0.22), radius: 22, y: 18)
                .frame(width: f.rect.width, height: f.rect.height)
                .opacity(lifted)
                .offset(x: f.rect.minX, y: f.rect.minY)
                .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                S.paper(note.face)

                headerView(note: note, tile: tile, frame: f, header: header, headerH: headerH, open: o, give: give)
                    .frame(width: f.rect.width, height: max(0, header), alignment: .topLeading)
                    .clipped()
                    .offset(y: parallax)

                sheet(note: note)
                    .frame(width: width, alignment: .topLeading)
                    .padding(.top, headerH - M.overlap)
                    .offset(y: -engine.scrollY)
                    .frame(width: width, height: height, alignment: .topLeading)
                    .clipped()
                    .scaleEffect(sheetScale, anchor: .topLeading)
                    .offset(y: header - headerH * sheetScale)
                    .opacity(sheetAlpha)

                Button(action: engine.close) {
                    Path { p in
                        p.move(to: CGPoint(x: 5, y: 5))
                        p.addLine(to: CGPoint(x: 19, y: 19))
                        p.move(to: CGPoint(x: 19, y: 5))
                        p.addLine(to: CGPoint(x: 5, y: 19))
                    }
                    .applying(CGAffineTransform(scaleX: 14 / 24, y: 14 / 24))
                    .stroke(ink, style: StrokeStyle(lineWidth: 2.6 * 14 / 24, lineCap: .round))
                    .frame(width: 14, height: 14)
                    .frame(width: 38, height: 38)
                    .background(Circle().fill(Color(red: 250 / 255, green: 247 / 255, blue: 241 / 255).opacity(0.88)))
                    .contentShape(Circle().inset(by: -8))
                }
                .buttonStyle(BentoExpandGridCloseStyle())
                .accessibilityLabel("Close")
                .opacity(chrome)
                .offset(x: f.rect.width - 16 - 38, y: engine.top + 8)
            }
            .frame(width: f.rect.width, height: f.rect.height, alignment: .topLeading)
            .clipShape(shape)
            .contentShape(shape)
            .opacity(reduced ? bentoExpandGridClamp(o) : 1)
            .gesture(
                DragGesture(minimumDistance: 5)
                    .updating($dragging) { _, state, _ in state = true }
                    .onChanged { engine.pageDragChanged(translation: $0.translation) }
                    .onEnded { engine.pageDragEnded(translation: $0.translation, velocity: $0.velocity) }
            )
            .onChange(of: dragging) { _, down in
                // A drag the system cancels never reaches onEnded.
                if !down { DispatchQueue.main.async { engine.pageDragCancelled() } }
            }
            .offset(x: f.rect.minX, y: f.rect.minY)
        }
    }

    @ViewBuilder
    private func headerView(note: BentoExpandGridNote, tile: CGRect, frame f: BentoExpandGridFrame, header: Double, headerH: Double, open o: Double, give: Double) -> some View {
        switch note.face {
        case let .photo(id):
            ZStack(alignment: .bottomLeading) {
                BentoExpandGridPhoto(loaded: engine.image(id, width: M.headerPx), now: engine.elapsed, fade: 0)
                BentoExpandGridPhotoLabel(note: note, narrow: tile.width < M.narrow)
                    .opacity(1 - bentoExpandGridSmooth(0, 0.3, o))
            }
        case let .map(place, stops):
            ZStack(alignment: .topLeading) {
                Canvas { context, _ in
                    bentoExpandGridDrawMap(&context, w: f.rect.width, h: header, ink: ink, accent: accent)
                }
                .frame(width: f.rect.width, height: max(0, header))
                BentoExpandGridMapCaption(place: place, stops: stops, ink: ink)
                    .frame(width: tile.width, height: tile.height, alignment: .bottomLeading)
                    .opacity(1 - bentoExpandGridSmooth(0, 0.3, o))
                    .offset(y: header - tile.height)
            }
        case .stat, .quote:
            // The stat and quote grow from their tile's size, anchored at the header's foot, lifted
            // clear of the sheet's rounded top once open.
            let grow = engine.grow(index)
            let p = min(1, o)
            let scale = bentoExpandGridMix(1 / grow, f.scale, p)
            let lift = M.overlap * 0.8 * give
            BentoExpandGridTypeFace(face: note.face, grow: grow, ink: ink, accent: accent)
                .frame(width: tile.width * grow, height: tile.height * grow)
                .scaleEffect(scale, anchor: .topLeading)
                .offset(y: header - tile.height * grow * scale - lift)
        }
    }

    private func sheet(note: BentoExpandGridNote) -> some View {
        let number = String(format: "%02d / %02d", index + 1, engine.notes.count)
        return VStack(alignment: .leading, spacing: 22) {
            Capsule()
                .fill(Color(red: 31 / 255, green: 28 / 255, blue: 24 / 255).opacity(0.14))
                .frame(width: 36, height: 5)
                .frame(maxWidth: .infinity)
                .padding(.bottom, -6)
            section(0) {
                VStack(alignment: .leading, spacing: 0) {
                    Text("\(note.kicker.uppercased()) · \(number)")
                        .font(.system(size: 10, weight: .bold))
                        .kerning(1.6)
                        .foregroundStyle(accent)
                    Text(note.title)
                        .font(S.serif(32))
                        .kerning(-0.6)
                        .bentoLines(bentoExpandGridGeorgia(32), 37)
                        .foregroundStyle(ink)
                        .padding(.top, 10)
                    Text(note.byline)
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(S.muted)
                        .padding(.top, 8)
                }
            }
            section(1) {
                Text(note.lead)
                    .font(S.serif(19))
                    .bentoLines(bentoExpandGridGeorgia(19), 28)
                    .foregroundStyle(ink)
            }
            section(2) {
                VStack(alignment: .leading, spacing: 0) {
                    ForEach(note.body, id: \.self) { text in
                        paragraph(text)
                    }
                }
            }
            section(3) {
                Text(note.pull)
                    .font(S.serif(22, italic: true))
                    .bentoLines(bentoExpandGridGeorgia(22, italic: true), 29)
                    .foregroundStyle(ink)
                    .padding(.leading, 16)
                    .padding(.vertical, 4)
                    .overlay(alignment: .leading) { Rectangle().fill(accent).frame(width: 3) }
            }
            section(4) {
                VStack(alignment: .leading, spacing: 0) {
                    HStack(spacing: 10) {
                        ForEach(note.pair, id: \.self) { id in
                            BentoExpandGridPhoto(loaded: engine.image(id, width: M.pairPx), now: engine.elapsed, fade: 0.25)
                                .frame(maxWidth: .infinity)
                                .frame(height: 180)
                                .background(S.photoPaper)
                                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
                        }
                    }
                    .padding(.bottom, 18)
                    paragraph(note.outro)
                    Text("END OF NOTE")
                        .font(.system(size: 11, weight: .semibold))
                        .kerning(1.4)
                        .foregroundStyle(S.muted)
                        .frame(maxWidth: .infinity)
                        .padding(.top, 6)
                }
            }
        }
        .fixedSize(horizontal: false, vertical: true)
        .padding(.horizontal, 24)
        .padding(.top, 12)
        .padding(.bottom, engine.bottom + 40)
        .background(UnevenRoundedRectangle(topLeadingRadius: M.overlap, topTrailingRadius: M.overlap, style: .continuous).fill(background))
        .onGeometryChange(for: Double.self) { $0.size.height } action: { engine.sheetMeasured($0) }
    }

    private func paragraph(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 16))
            .bentoLines(.systemFont(ofSize: 16), 25)
            .foregroundStyle(ink)
            .fixedSize(horizontal: false, vertical: true)
            .padding(.bottom, 12)
    }

    /// A block of the page that rises in after the frame has mostly grown, in its turn.
    private func section(_ order: Int, @ViewBuilder content: () -> some View) -> some View {
        let from = M.revealFrom + Double(order) * M.revealStep
        let shown = bentoExpandGridSmooth(from, from + M.revealSpan, engine.open.value)
        return content()
            .opacity(shown)
            .offset(y: engine.reduced ? 0 : (1 - shown) * M.revealRise)
    }
}

private struct BentoExpandGridCloseStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .opacity(configuration.isPressed ? 0.7 : 1)
            .scaleEffect(configuration.isPressed ? 0.95 : 1)
    }
}
