import CoreText
import SwiftUI
import UIKit

/// A full-screen field-survey page: white, edge to edge, holding a column of round photo bubbles,
/// one per site, each with its name beside it. The selected site is a big rounded square; drag along
/// the column to move the selection and the bubbles morph between circle and square as it passes. Tap
/// the selected site to grow it into the whole card, where a heading types itself, a target ring locks
/// on and a big pixel reading counts up; the slider zooms into the photo, and dragging down puts it back.
struct MorphBubbleColumn: View {
    /// The sites down the column, top to bottom (five fit any phone).
    var sites: [MorphBubbleColumnSite] = MorphBubbleColumnSite.samples
    /// Small photo in the pill at the top right.
    var avatar = "photo-1641901960200-1e878f0cbf63"
    /// The name in the pill.
    var greeting = "HI MARA"
    /// Lines at the top left of the card.
    var date = ["THURSDAY", "15", "OCT"]
    /// The clock in pixels: digits, ":", "P" and "M".
    var clock = "9:PM"
    /// Walks the selection down the column, opens a site, reads it, closes it and walks back.
    var autoPlay = false
    /// Called with a site's index when it opens.
    var onOpen: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The column's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = MorphBubbleColumnEngine()
    @State private var statics = MorphBubbleColumnStatics()
    // Resets when the system cancels the touch, which never reaches onEnded.
    @GestureState private var fingerDown = false

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: sites.map(\.id) + [avatar]) { await loadPhotos() }
        .onAppear { wire() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let room = MorphBubbleColumnStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
        let paths = statics.paths(width: size.width, height: size.height, top: insets.top, clock: clock)
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, stage: room, count: sites.count, autoPlay: autoPlay, reducedMotion: reduceMotion)
            Canvas { context, _ in
                MorphBubbleColumnPainter(sites: sites, greeting: greeting, date: date, statics: paths)
                    .draw(frame, in: context)
            }
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: MorphBubbleColumnMetrics.card))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($fingerDown) { _, down, _ in down = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: fingerDown) { _, down in
            // A touch the system took away lets go (after onEnded, which wins if it fired).
            guard !down else { return }
            let engine = engine
            Task { @MainActor in engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("Survey sites in a column of photo bubbles. Drag to move the selection; tap the selected site to open it; drag down to close it.")
    }

    private func wire() {
        let sites = sites
        let engine = engine
        let onOpen = onOpen
        engine.onOpened = { index, user in
            // A sharp copy for the full card, fetched once per site; the thumbnail stands in until it
            // arrives.
            Self.loadSharp(sites, index, engine)
            guard user else { return }
            UIImpactFeedbackGenerator(style: .light).impactOccurred()
            onOpen?(index)
        }
        engine.onCloseByHand = { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        engine.onTick = { UISelectionFeedbackGenerator().selectionChanged() }
    }

    private static func loadSharp(_ sites: [MorphBubbleColumnSite], _ index: Int, _ engine: MorphBubbleColumnEngine) {
        guard sites.indices.contains(index), !engine.hasSharp(index) else { return }
        let address = morphBubbleColumnPhoto(sites[index].id, size: MorphBubbleColumnMetrics.sharpSize)
        Task { @MainActor in
            if let image = await MorphBubbleColumnPhotos.load(address) { engine.sharpArrived(image, index: index) }
        }
    }

    // Thumbnails for the column, and the avatar; each fades in as it arrives. With autoPlay the loop's
    // site gets its sharp copy up front, so the first open is sharp.
    private func loadPhotos() async {
        engine.resetPhotos(count: sites.count)
        if autoPlay && !reduceMotion { Self.loadSharp(sites, MorphBubbleColumnMetrics.autoSite, engine) }
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, site) in sites.enumerated() {
                group.addTask { (index, await MorphBubbleColumnPhotos.load(morphBubbleColumnPhoto(site.id, size: MorphBubbleColumnMetrics.thumbSize))) }
            }
            group.addTask { (-1, await MorphBubbleColumnPhotos.load(morphBubbleColumnPhoto(avatar, size: "w=120&h=120"))) }
            for await (index, image) in group {
                guard let image else { continue }
                if index < 0 { engine.faceArrived(image) } else { engine.thumbArrived(image, at: index) }
            }
        }
    }
}

// MARK: - Static paths

/// The card's outline, its dot grid and the pixel clock, built once per size; and every dot-matrix
/// figure of the reading, one pixel to a unit.
final class MorphBubbleColumnStatics {
    struct Paths {
        var card = Path()
        var grid = Path()
        var clock = Path()
    }

    private typealias M = MorphBubbleColumnMetrics
    private var key: (Double, Double, Double, String)?
    private var cached = Paths()

    /// Each dot-matrix figure as one path of square pixels, and how many pixels wide it is.
    static let figures: [Character: (path: Path, cols: Int)] = {
        var out: [Character: (path: Path, cols: Int)] = [:]
        for ch in "0123456789+" {
            guard let rows = MorphBubbleColumnGlyphs.glyphs[ch] else { continue }
            out[ch] = (MorphBubbleColumnGlyphs.path([rows], x: 0, y: 0, px: 1, gap: M.pixelGap), rows.first?.count ?? 5)
        }
        return out
    }()

    func paths(width: Double, height: Double, top: Double, clock: String) -> Paths {
        if let key, key == (width, height, top, clock) { return cached }
        key = (width, height, top, clock)
        var grid = Path()
        var gy = M.gridStep / 2
        while gy < height {
            var gx = M.gridStep / 2
            while gx < width {
                grid.addRect(CGRect(x: gx - 0.6, y: gy - 0.6, width: 1.2, height: 1.2))
                gx += M.gridStep
            }
            gy += M.gridStep
        }
        let card = CGRect(x: M.cardInset, y: M.cardInset, width: max(0, width - 2 * M.cardInset), height: max(0, height - 2 * M.cardInset))
        cached = Paths(
            card: Self.cardPath(card),
            grid: grid,
            clock: MorphBubbleColumnGlyphs.path(MorphBubbleColumnGlyphs.rows(clock), x: M.cardInset + 22, y: max(top, 50) + 50, px: 3.2, gap: 0.7)
        )
        return cached
    }

    /// The card's outline: a rectangle (rounded by cardRadius) with a soft-cornered notch in its right
    /// edge.
    static func cardPath(_ card: CGRect) -> Path {
        let l = card.minX
        let t = card.minY
        let r = card.maxX
        let b = card.maxY
        let n0 = t + card.height * M.notchAt - M.notchHeight / 2
        let n1 = n0 + M.notchHeight
        let inner = r - M.notchDepth
        var p = Path()
        func corner(_ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double, _ radius: Double) {
            if radius > 0 {
                p.addArc(tangent1End: CGPoint(x: x1, y: y1), tangent2End: CGPoint(x: x2, y: y2), radius: radius)
            } else {
                p.addLine(to: CGPoint(x: x1, y: y1))
            }
        }
        p.move(to: CGPoint(x: l + M.cardRadius, y: t))
        corner(r, t, r, b, M.cardRadius)
        corner(r, n0, inner, n0, M.notchRadius)
        corner(inner, n0, inner, n1, M.notchRadius)
        corner(inner, n1, r, n1, M.notchRadius)
        corner(r, n1, r, b, M.notchRadius)
        corner(r, b, l, b, M.cardRadius)
        corner(l, b, l, t, M.cardRadius)
        corner(l, t, r, t, M.cardRadius)
        p.closeSubpath()
        return p
    }
}

// MARK: - Drawing

/// Draws one frame: the page, the card and its grid, the header and the site labels, the column, the
/// open photo and its details, then the rail, the pill and the bar on top.
struct MorphBubbleColumnPainter {
    let sites: [MorphBubbleColumnSite]
    let greeting: String
    let date: [String]
    let statics: MorphBubbleColumnStatics.Paths

    private typealias M = MorphBubbleColumnMetrics
    private typealias X = MorphBubbleColumnMath

    static let label = UIFont(name: "Menlo-Bold", size: 9) ?? .monospacedSystemFont(ofSize: 9, weight: .bold)
    static let tiny = UIFont(name: "Menlo-Bold", size: 8) ?? .monospacedSystemFont(ofSize: 8, weight: .bold)
    static let heading = UIFont(name: "Menlo-Bold", size: 25) ?? .monospacedSystemFont(ofSize: 25, weight: .bold)
    static let caption = UIFont(name: "Menlo-Bold", size: 10) ?? .monospacedSystemFont(ofSize: 10, weight: .bold)
    static let headingAdvance = MorphBubbleColumnPainter.advance("M", heading)

    func draw(_ frame: MorphBubbleColumnFrame, in context: GraphicsContext) {
        let room = frame.stage
        guard room.width > 0, room.height > 0 else { return }
        let card = M.card(room)
        let now = frame.now
        let o = frame.open
        let which = frame.openSite
        let column = frame.boxes
        let still = frame.reducedMotion

        context.fill(Path(CGRect(x: 0, y: 0, width: room.width, height: room.height)), with: .color(Color(hex: M.page)))
        context.fill(statics.card, with: .color(Color(hex: M.card)))
        var inside = context
        inside.clip(to: statics.card)
        inside.fill(statics.grid, with: .color(Color(hex: M.gridDot)))

        // The header and the site labels fade as a photo opens over them.
        let chrome = X.clamp01(1 - o * 1.6)
        if chrome > 0 {
            var c = inside
            c.opacity = chrome
            for (i, line) in date.enumerated() {
                text(c, line, Self.tiny, x: card.minX + 30, baseline: max(22, room.top - 30) + Double(i) * 11, color: M.ink)
            }
            c.fill(statics.clock, with: .color(Color(hex: M.ink)))
            for s in column.indices where sites.indices.contains(s) {
                let enter = still ? 1 : X.clamp01((now - M.enterDelay - Double(s) * M.enterStagger - 0.15) / 0.35)
                siteLabel(inside, sites[s], index: s, count: sites.count, box: column[s], card: card, alpha: chrome * enter)
            }
        }

        for s in column.indices where s != which && sites.indices.contains(s) {
            let box = column[s]
            // The rest sink back a little and fade while one opens.
            let sink = still ? 1 : 1 - M.sink * X.clamp01(o)
            var sunk = box
            sunk.x = box.x + box.w * (1 - sink) / 2
            sunk.y = box.y + box.h * (1 - sink) / 2
            sunk.w = box.w * sink
            sunk.h = box.h * sink
            let appear = still ? X.clamp01(now / 0.3) : X.clamp01((box.scale - M.dot) / 0.3)
            bubble(inside, sunk, site: sites[s], loaded: Self.loaded(frame.thumbs, s), now: now, alpha: appear * X.clamp01(1 - o))
        }

        if which >= 0, column.indices.contains(which), sites.indices.contains(which) {
            let from = column[which]
            let site = sites[which]
            let full = still ? 1 : X.clamp01(o)
            // The bubble grows into the card: its box, corners and squircle shoulder all morph to the
            // card's own rectangle, and the notch bites into it as it arrives.
            let w = X.mix(from.w, card.width, full)
            let h = X.mix(from.h, card.height, full)
            let half = max(1, min(w, h) / 2)
            let radius = X.mix(from.r * min(from.w, from.h) / 2, M.cardRadius, full)
            let box = MorphBubbleColumnBox(
                x: X.mix(from.x, card.minX, full),
                y: X.mix(from.y, card.minY, full),
                w: w,
                h: h,
                r: min(1, radius / half),
                n: X.mix(from.n, 2, full),
                scale: 1,
                big: 1
            )
            let zoomed = 1 + M.zoomPhoto * frame.slider * full
            var photo = inside
            photo.clip(to: Self.shapePath(box.x, box.y, box.w, box.h, box.r, box.n))
            let body = { (layer: inout GraphicsContext) in
                let rect = CGRect(x: box.x, y: box.y, width: box.w, height: box.h)
                layer.fill(Path(rect), with: .color(Color(hex: site.tint)))
                if let thumb = Self.loaded(frame.thumbs, which) {
                    cover(layer, thumb.image, rect, zoom: zoomed, alpha: X.clamp01((now - thumb.at) / M.imageFade))
                }
                if let crisp = Self.loaded(frame.sharps, which) {
                    cover(layer, crisp.image, rect, zoom: zoomed, alpha: X.clamp01((now - crisp.at) / M.imageFade))
                }
                details(layer, room: room, card: card, site: site, alpha: X.clamp01((o - 0.72) / 0.28), shown: frame.shownFor, zoom: frame.slider, now: now, still: still)
            }
            if still {
                // Without spatial motion the photo only fades in and out.
                photo.opacity = X.clamp01(o)
                photo.drawLayer { layer in body(&layer) }
            } else {
                body(&photo)
            }
        }

        rail(context, card: card)
        pill(context, room: room, avatar: frame.face, now: now)
        bar(context, room: room, zoom: frame.slider)
    }

    private static func loaded(_ list: [MorphBubbleColumnLoaded?], _ index: Int) -> MorphBubbleColumnLoaded? {
        list.indices.contains(index) ? list[index] : nil
    }

    // MARK: Bubbles

    /// A rounded rectangle whose corners are quarter superellipses: radius `rf` × half the shorter
    /// side, exponent `n`. With rf = 1 and n = 2 a square becomes a circle, with n ≈ 4 a squircle.
    static func shapePath(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ rf: Double, _ n: Double) -> Path {
        let rc = max(0, min(rf, 1)) * min(w, h) * 0.5
        let e = 2 / max(2, n)
        let corners: [(Double, Double, Double)] = [
            (x + w - rc, y + rc, -Double.pi / 2),
            (x + w - rc, y + h - rc, 0),
            (x + rc, y + h - rc, Double.pi / 2),
            (x + rc, y + rc, Double.pi),
        ]
        let steps = 14
        var path = Path()
        for (k, corner) in corners.enumerated() {
            for i in 0...steps {
                let a = corner.2 + Double(i) / Double(steps) * (Double.pi / 2)
                let c = cos(a)
                let s = sin(a)
                let px = corner.0 + rc * sign(c) * pow(abs(c), e)
                let py = corner.1 + rc * sign(s) * pow(abs(s), e)
                if k == 0 && i == 0 { path.move(to: CGPoint(x: px, y: py)) } else { path.addLine(to: CGPoint(x: px, y: py)) }
            }
        }
        path.closeSubpath()
        return path
    }

    private static func sign(_ v: Double) -> Double { v > 0 ? 1 : v < 0 ? -1 : 0 }

    /// `image` scaled to cover the rect (and `zoom` more), centred, at `alpha`; the caller clips.
    private func cover(_ context: GraphicsContext, _ image: UIImage, _ rect: CGRect, zoom: Double, alpha: Double) {
        let iw = image.size.width
        let ih = image.size.height
        guard alpha > 0, iw > 0, ih > 0, rect.width > 0, rect.height > 0 else { return }
        let fit = max(rect.width / iw, rect.height / ih) * zoom
        let dw = iw * fit
        let dh = ih * fit
        var c = context
        c.opacity *= alpha
        c.draw(Image(uiImage: image).interpolation(.medium), in: CGRect(x: rect.midX - dw / 2, y: rect.midY - dh / 2, width: dw, height: dh))
    }

    /// One bubble: the site's tint, then its photo fading in, clipped to the bubble's shape.
    private func bubble(_ context: GraphicsContext, _ box: MorphBubbleColumnBox, site: MorphBubbleColumnSite, loaded: MorphBubbleColumnLoaded?, now: Double, alpha: Double) {
        guard alpha > 0.002, box.w >= 0.5 else { return }
        let body = { (layer: inout GraphicsContext) in
            layer.clip(to: Self.shapePath(box.x, box.y, box.w, box.h, box.r, box.n))
            let rect = CGRect(x: box.x, y: box.y, width: box.w, height: box.h)
            layer.fill(Path(rect), with: .color(Color(hex: site.tint)))
            if let loaded {
                cover(layer, loaded.image, rect, zoom: 1, alpha: X.clamp01((now - loaded.at) / M.imageFade))
            }
        }
        var c = context
        if alpha < 0.995 {
            c.opacity = alpha
            c.drawLayer { layer in body(&layer) }
        } else {
            body(&c)
        }
    }

    /// A site's label, left of its bubble and centred on it: its number, its name and, while selected,
    /// its position and a hairline that runs to the bubble.
    private func siteLabel(_ context: GraphicsContext, _ site: MorphBubbleColumnSite, index: Int, count: Int, box: MorphBubbleColumnBox, card: CGRect, alpha: Double) {
        guard alpha > 0.002 else { return }
        let x = card.minX + 22
        let cy = box.y + box.h / 2
        let on = box.big
        let number = String(format: "%02d/%02d", index + 1, count)
        var c = context
        c.opacity = alpha * X.mix(0.7, 1, on)
        text(c, number, Self.tiny, x: x, baseline: cy - 9, color: M.muted)
        c = context
        c.opacity = alpha * X.mix(0.42, 1, on)
        text(c, site.name, Self.label, x: x, baseline: cy + 3, color: M.ink)
        guard on > 0.01 else { return }
        c = context
        c.opacity = alpha * on
        text(c, site.coords, Self.tiny, x: x, baseline: cy + 15, color: M.muted)
        // The leader: from just past the name to just short of the bubble, drawn out as it is selected.
        let from = x + Self.advance(site.name, Self.label) + 8
        let to = box.x - 8
        guard to - from > 6 else { return }
        let end = X.mix(from, to, M.easeOut.value(at: on))
        let ink = GraphicsContext.Shading.color(Color(hex: M.ink))
        c.stroke(Self.segment(from, cy - 0.5, end, cy - 0.5), with: ink, style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        c.fill(Path(ellipseIn: CGRect(x: end - 1.8, y: cy - 0.5 - 1.8, width: 3.6, height: 3.6)), with: ink)
    }

    // MARK: Chrome

    /// The pill at the top right: the avatar, a greeting and a round bolt button.
    private func pill(_ context: GraphicsContext, room: MorphBubbleColumnStage, avatar: MorphBubbleColumnLoaded?, now: Double) {
        let w = 48.0
        let x = room.width - M.cardInset - 14 - w
        let y = max(14, room.top - 40)
        let ink = GraphicsContext.Shading.color(Color(hex: M.ink))
        context.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: 122), cornerRadius: w / 2), with: ink)
        var face = context
        let faceRect = CGRect(x: x + 7, y: y + 7, width: 34, height: 34)
        face.clip(to: Path(ellipseIn: faceRect))
        face.fill(Path(faceRect), with: .color(Color(hex: 0x555555)))
        if let avatar {
            cover(face, avatar.image, faceRect, zoom: 1, alpha: X.clamp01((now - avatar.at) / M.imageFade))
        }
        let width = Self.advance(greeting, Self.tiny)
        text(context, greeting, Self.tiny, x: x + w / 2 - width / 2, baseline: y + 58, color: M.card)
        let cx = x + w / 2
        let cy = y + 93
        context.fill(Path(ellipseIn: CGRect(x: cx - 16, y: cy - 16, width: 32, height: 32)), with: .color(Color(hex: M.card)))
        // A bolt, drawn: two offset triangles.
        var bolt = Path()
        bolt.move(to: CGPoint(x: cx + 1.6, y: cy - 8))
        bolt.addLine(to: CGPoint(x: cx - 5, y: cy + 1.4))
        bolt.addLine(to: CGPoint(x: cx - 0.4, y: cy + 1.4))
        bolt.addLine(to: CGPoint(x: cx - 1.6, y: cy + 8))
        bolt.addLine(to: CGPoint(x: cx + 5, y: cy - 1.4))
        bolt.addLine(to: CGPoint(x: cx + 0.4, y: cy - 1.4))
        bolt.closeSubpath()
        context.fill(bolt, with: ink)
    }

    /// The tool rail in the card's notch: a viewfinder, a card and an exit icon, drawn in light lines.
    private func rail(_ context: GraphicsContext, card: CGRect) {
        let cx = card.maxX - M.notchDepth / 2
        let top = card.minY + card.height * M.notchAt - M.notchHeight / 2
        let line = StrokeStyle(lineWidth: 1.4, lineCap: .round, lineJoin: .round)
        let light = GraphicsContext.Shading.color(Color(hex: M.onPage))
        // Viewfinder: four corners.
        var v = Path()
        let a = 6.5
        let l = 3.2
        let vy = top + 30
        func poly(_ points: [(Double, Double)]) {
            v.move(to: CGPoint(x: points[0].0, y: points[0].1))
            for p in points.dropFirst() { v.addLine(to: CGPoint(x: p.0, y: p.1)) }
        }
        poly([(cx - a, vy - a + l), (cx - a, vy - a), (cx - a + l, vy - a)])
        poly([(cx + a - l, vy - a), (cx + a, vy - a), (cx + a, vy - a + l)])
        poly([(cx + a, vy + a - l), (cx + a, vy + a), (cx + a - l, vy + a)])
        poly([(cx - a + l, vy + a), (cx - a, vy + a), (cx - a, vy + a - l)])
        context.stroke(v, with: light, style: line)
        // A card.
        let my = top + M.notchHeight / 2
        context.stroke(Path(roundedRect: CGRect(x: cx - 6, y: my - 5, width: 12, height: 10), cornerRadius: 2), with: light, style: line)
        context.stroke(Self.segment(cx - 6, my - 1.5, cx + 6, my - 1.5), with: light, style: line)
        // Exit: a door with an arrow.
        let ey = top + M.notchHeight - 30
        var door = Path()
        door.move(to: CGPoint(x: cx + 1, y: ey - 6))
        door.addLine(to: CGPoint(x: cx + 6, y: ey - 6))
        door.addLine(to: CGPoint(x: cx + 6, y: ey + 6))
        door.addLine(to: CGPoint(x: cx + 1, y: ey + 6))
        door.move(to: CGPoint(x: cx - 6, y: ey))
        door.addLine(to: CGPoint(x: cx + 2, y: ey))
        door.move(to: CGPoint(x: cx - 1, y: ey - 3))
        door.addLine(to: CGPoint(x: cx + 2, y: ey))
        door.addLine(to: CGPoint(x: cx - 1, y: ey + 3))
        context.stroke(door, with: light, style: line)
    }

    /// The white bar at the bottom of the card: menu, EXPLORE, the zoom slider and its reading.
    private func bar(_ context: GraphicsContext, room: MorphBubbleColumnStage, zoom: Double) {
        let top = M.barTop(room)
        let left = M.cardInset + 10
        let rect = Path(roundedRect: CGRect(x: left, y: top, width: room.width - 2 * left, height: 56), cornerRadius: 18)
        context.fill(rect, with: .color(Color(hex: 0xfbfbf8)))
        context.stroke(rect, with: .color(Color(hex: 0xdcdcd5)), style: StrokeStyle(lineWidth: 0.8, lineCap: .round))
        let ink = GraphicsContext.Shading.color(Color(hex: M.ink))
        let line = StrokeStyle(lineWidth: 1.4, lineCap: .round)
        context.stroke(Self.segment(left + 18, top + 24, left + 33, top + 24), with: ink, style: line)
        context.stroke(Self.segment(left + 18, top + 30, left + 33, top + 30), with: ink, style: line)
        text(context, "EXPLORE", Self.label, x: left + 44, baseline: top + 30.5, color: M.ink)
        let t = M.track(room)
        context.stroke(Self.segment(t.x0, t.y, t.x1, t.y), with: ink, style: StrokeStyle(lineWidth: 0.9, lineCap: .round))
        let at = X.mix(t.x0, t.x1, zoom)
        context.stroke(Self.segment(t.x0, t.y, at, t.y), with: ink, style: StrokeStyle(lineWidth: 2, lineCap: .round))
        // End stops, each with a pixel above and below, like a survey instrument's scale.
        for ex in [t.x0, t.x1] {
            context.fill(Path(CGRect(x: ex - 1, y: t.y - 5, width: 2, height: 10)), with: ink)
            context.fill(Path(CGRect(x: ex - 1.5, y: t.y - 11, width: 3, height: 3)), with: ink)
            context.fill(Path(CGRect(x: ex - 1.5, y: t.y + 8, width: 3, height: 3)), with: ink)
        }
        context.fill(Path(roundedRect: CGRect(x: at - 1.5, y: t.y - 10, width: 3, height: 20), cornerRadius: 1.5), with: ink)
        let reading = "\(Int(X.jsRound(10 + X.clamp01(zoom) * 80))) x"
        text(context, reading, Self.label, x: t.x1 - Self.advance(reading, Self.label), baseline: top + 46, color: M.ink)
        text(context, "ZOOM", Self.tiny, x: t.x0, baseline: top + 46, color: M.muted)
    }

    // MARK: Open photo

    /// `text` in dot-matrix figures, `pixel` pt to a pixel, its top left at (x, y).
    private func figures(_ context: GraphicsContext, _ text: String, x: Double, y: Double, color: UInt32) {
        var left = x
        for ch in text {
            guard let figure = MorphBubbleColumnStatics.figures[ch] else { continue }
            let placed = figure.path.applying(CGAffineTransform(a: M.pixel, b: 0, c: 0, d: M.pixel, tx: left, ty: y))
            context.fill(placed, with: .color(Color(hex: color)))
            left += Double(figure.cols + 1) * M.pixel
        }
    }

    /// What shows over an open photo: the typed heading, the target ring, the counted reading.
    private func details(_ context: GraphicsContext, room: MorphBubbleColumnStage, card: CGRect, site: MorphBubbleColumnSite, alpha: Double, shown: Double, zoom: Double, now: Double, still: Bool) {
        guard alpha > 0.002 else { return }
        var c = context
        c.opacity *= alpha
        // A light shade at the top and the bottom keeps white type legible on a bright sky or ice.
        c.fill(
            Path(CGRect(x: card.minX, y: card.minY, width: card.width, height: 240)),
            with: .linearGradient(Gradient(colors: [.black.opacity(0.3), .black.opacity(0)]), startPoint: CGPoint(x: 0, y: card.minY), endPoint: CGPoint(x: 0, y: card.minY + 240))
        )
        let low = M.barTop(room) - 230
        c.fill(
            Path(CGRect(x: card.minX, y: low, width: card.width, height: card.maxY - low)),
            with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.34)]), startPoint: CGPoint(x: 0, y: low), endPoint: CGPoint(x: 0, y: card.maxY))
        )
        let ink = site.ink

        // The heading types itself a character at a time behind a block cursor.
        let total = site.heading.reduce(0) { $0 + $1.count }
        let typed = still ? total : max(0, min(total, Int(((shown - M.typeDelay) / M.typeStep).rounded(.down))))
        let x = card.minX + 22
        let lead = 29.0
        let first = max(room.top, 50) + 76
        var left = typed
        var cursorLine = 0
        var cursorCol = 0
        for (i, line) in site.heading.enumerated() {
            let n = min(line.count, left)
            if n > 0 { text(c, String(line.prefix(n)), Self.heading, x: x, baseline: first + Double(i) * lead, color: ink) }
            cursorLine = i
            cursorCol = n
            if n < line.count { break }
            left -= n
        }
        let done = typed >= total
        let blink = done ? Int((now * M.cursorHz * 2).rounded(.down)) % 2 == 0 : true
        let advance = Self.headingAdvance
        if shown > M.typeDelay - 0.2 && blink {
            c.fill(Path(CGRect(x: x + Double(cursorCol) * advance + 2, y: first + Double(cursorLine) * lead - 19, width: advance * 0.72, height: 22)), with: .color(Color(hex: ink)))
        }

        // A target ring and its readout, pinned to the photo: it moves with the slider's zoom.
        let ring = X.clamp01((shown - 1.0) / 0.6)
        if ring > 0 {
            let ccx = card.midX
            let ccy = card.midY
            let scale = 1 + M.zoomPhoto * zoom
            let rx = ccx + (card.minX + card.width * site.ring.0 - ccx) * scale
            let ry = ccy + (card.minY + card.height * site.ring.1 - ccy) * scale
            let grow = still ? 1 : M.easeOut.value(at: ring)
            var r = c
            r.opacity *= ring
            let stroke = StrokeStyle(lineWidth: 1.2, lineCap: .round, lineJoin: .round)
            let shading = GraphicsContext.Shading.color(Color(hex: ink))
            let radius = 12 + 8 * grow
            r.stroke(Path(ellipseIn: CGRect(x: rx - radius, y: ry - radius, width: radius * 2, height: radius * 2)), with: shading, style: stroke)
            var ticks = Path()
            let turn = still ? 0 : now * 14 * Double.pi / 180
            for k in 0..<4 {
                let a = Double(k) * Double.pi / 2 + turn
                ticks.move(to: CGPoint(x: rx + cos(a) * 25 * grow, y: ry + sin(a) * 25 * grow))
                ticks.addLine(to: CGPoint(x: rx + cos(a) * 31 * grow, y: ry + sin(a) * 31 * grow))
            }
            r.stroke(ticks, with: shading, style: stroke)
            r.fill(Path(ellipseIn: CGRect(x: rx - 1.8, y: ry - 1.8, width: 3.6, height: 3.6)), with: shading)
            let tagWidth = Self.advance(site.tag, Self.caption)
            text(r, site.tag, Self.caption, x: rx - tagWidth / 2, baseline: ry + 52, color: ink)
        }

        // The reading counts up in big dot-matrix figures, with its caption below.
        if shown > M.countDelay - 0.3 {
            let count = still ? 1 : M.easeOut.value(at: X.clamp01((shown - M.countDelay) / M.countTime))
            let value = Int(X.jsRound(Double(site.value) * count))
            var n = c
            n.opacity *= X.clamp01((shown - M.countDelay + 0.3) / 0.3)
            let top = M.barTop(room) - 124
            figures(n, "+\(value)", x: x, y: top, color: ink)
            let dots = String(repeating: ".", count: Int((now * 2.5).rounded(.down)) % 4)
            for (i, line) in site.caption.enumerated() {
                let shownLine = i == site.caption.count - 1 ? line + dots : line
                text(n, shownLine, Self.caption, x: x, baseline: top + 7 * M.pixel + 22 + Double(i) * 13, color: ink)
            }
        }
        let hint = "DRAG DOWN TO CLOSE"
        var h = c
        h.opacity *= 0.85
        text(h, hint, Self.label, x: card.maxX - 22 - Self.advance(hint, Self.label), baseline: M.barTop(room) - 16, color: ink)
    }

    // MARK: Text

    static func segment(_ x0: Double, _ y0: Double, _ x1: Double, _ y1: Double) -> Path {
        var p = Path()
        p.move(to: CGPoint(x: x0, y: y0))
        p.addLine(to: CGPoint(x: x1, y: y1))
        return p
    }

    /// The width a line of text advances by.
    static func advance(_ string: String, _ font: UIFont) -> Double {
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: font]))
        return CTLineGetTypographicBounds(line, nil, nil, nil)
    }

    /// Draws `string` with its baseline at `baseline`, starting at `x`, like Skia's drawText.
    private func text(_ context: GraphicsContext, _ string: String, _ font: UIFont, x: Double, baseline: Double, color: UInt32) {
        let resolved = context.resolve(Text(string).font(Font(font as CTFont)).foregroundStyle(Color(hex: color)))
        let size = resolved.measure(in: CGSize(width: CGFloat.greatestFiniteMagnitude, height: CGFloat.greatestFiniteMagnitude))
        let rise = resolved.firstBaseline(in: size)
        context.draw(resolved, in: CGRect(x: x, y: baseline - rise, width: size.width, height: size.height))
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum MorphBubbleColumnPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

func morphBubbleColumnPhoto(_ id: String, size: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&\(size)&q=80"
}
