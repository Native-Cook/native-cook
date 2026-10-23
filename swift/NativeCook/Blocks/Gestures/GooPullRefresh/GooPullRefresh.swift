import SwiftUI
import UIKit

/// One photo in the feed. `shape` is how it is cropped.
struct GooPullRefreshSighting: Hashable {
    enum Shape: Hashable { case portrait, square, landscape }
    let id: String
    let shape: Shape
    let title: String
    let by: String
    let likes: Int

    /// Height over width of the photo for its shape.
    var aspect: Double {
        switch shape {
        case .portrait: 1.3
        case .square: 1
        case .landscape: 0.76
        }
    }
}

/// One tile of the masonry: where it sits in its column and which rows it waits behind.
struct GooPullRefreshTile: Hashable {
    let item: GooPullRefreshSighting
    let fresh: Bool
    let x: Double
    let y: Double
    let row: Int
    let photo: Double
}

/// Both columns laid out: the incoming rows on top (the first to arrive nearest the feed), then
/// the feed, each photo going to the shorter column.
struct GooPullRefreshLayout {
    let column: Double
    let tiles: [GooPullRefreshTile]
    let prefix: [Double]
    let total: Double

    init(feed: [GooPullRefreshSighting], incoming: [[GooPullRefreshSighting]], width: Double) {
        typealias M = GooPullRefreshMetrics
        let col = max(1, (width - M.side * 2 - M.colGap) / 2)
        var tiles: [GooPullRefreshTile] = []
        var heights = [0.0, 0.0]
        var rows = [0, 0]
        var prefix = [0.0]
        for pair in incoming.reversed() where pair.count == 2 {
            let photo = (col * pair[0].aspect).rounded()
            for c in 0..<2 {
                tiles.append(.init(item: pair[c], fresh: true, x: M.side + Double(c) * (col + M.colGap), y: heights[c], row: rows[c], photo: photo))
                heights[c] += photo + M.textH + M.rowGap
                rows[c] += 1
            }
            prefix.append(heights[0])
        }
        for item in feed {
            let c = heights[1] < heights[0] - 0.5 ? 1 : 0
            let photo = (col * item.aspect).rounded()
            tiles.append(.init(item: item, fresh: false, x: M.side + Double(c) * (col + M.colGap), y: heights[c], row: rows[c], photo: photo))
            heights[c] += photo + M.textH + M.rowGap
            rows[c] += 1
        }
        column = col
        self.tiles = tiles
        self.prefix = prefix
        total = max(heights[0], heights[1]) - M.rowGap
    }
}

/// A two-column feed of cat and bird photos under a white header. Pulling the feed down draws a
/// drop of orange liquid out from behind the header's edge on a neck that thins as you pull; past
/// the threshold the neck snaps with a haptic and the drop hangs free, jiggling. Let go and it
/// becomes a gooey loader, three droplets breathing out of a core and back while the trio turns;
/// when the new photos are in, they merge into one drop that the header draws back in, the feed
/// closes and the new row slides in on top.
struct GooPullRefresh: View {
    /// The photos already in the feed, newest first.
    var sightings: [GooPullRefreshSighting] = GooPullRefreshSighting.feed
    /// The photos refreshes bring in, a row of two at a time in the order they arrive. The two of
    /// a row share a shape, so both columns move by the same amount.
    var incoming: [[GooPullRefreshSighting]] = GooPullRefreshSighting.incoming
    /// Pulls to refresh, waits for the new photos, scrolls down the feed and back up.
    var autoPlay = false
    /// Called when a pull commits to a refresh.
    var onRefresh: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The feed's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = GooPullRefreshEngine()

    private typealias M = GooPullRefreshMetrics

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
        .background(Color(hex: M.page).ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let barBottom = insets.top + M.headerH
        let listTop = barBottom + M.listTop
        let layout = GooPullRefreshLayout(feed: sightings, incoming: incoming, width: size.width)
        let hidden = layout.prefix.count - 1
        let stage = GooPullRefreshStage(
            height: size.height, barBottom: barBottom, bottomInset: insets.bottom, total: layout.total, prefix: layout.prefix,
            hidden: hidden, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, onRefresh: onRefresh
        )
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, stage: stage)
            let reach = M.hiddenReach(layout.prefix, frame.head)
            ZStack(alignment: .topLeading) {
                // Only the tiles on screen are built; each keeps its photo in a shared cache.
                ForEach(layout.tiles.filter { tile in
                    let y = listTop + frame.scroll + tile.y - reach
                    return y < size.height && y + tile.photo + M.textH > barBottom - 40 && (!tile.fresh || Double(tile.row) - frame.head + 1 > 0)
                }, id: \.self) { tile in
                    // 0 while hidden, 1 once in: each new row takes its turn as the hidden count falls.
                    let shown = tile.fresh ? M.clamp01(Double(tile.row) - frame.head + 1) : 1
                    GooPullRefreshTileView(tile: tile, width: layout.column)
                        .equatable()
                        .scaleEffect(reduceMotion ? 1 : 0.92 + 0.08 * shown)
                        .opacity(shown)
                        .offset(x: tile.x, y: listTop + frame.scroll + tile.y - reach)
                }

                Canvas { context, _ in
                    GooPullRefreshLiquid.draw(&context, frame: frame, width: size.width, barBottom: barBottom, reducedMotion: reduceMotion)
                }
                .frame(width: size.width, height: size.height)

                header(width: size.width, top: insets.top)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Sightings feed. Pull down to refresh.")
    }

    private func header(width: CGFloat, top: CGFloat) -> some View {
        let ink = Color(hex: M.ink)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Sightings")
                    .font(.system(size: 30, weight: .bold))
                    .tracking(-0.6)
                    .foregroundStyle(ink)
                Spacer()
                Image(systemName: "bell")
                    .font(.system(size: 16, weight: .regular))
                    .foregroundStyle(ink)
                    .frame(width: 38, height: 38)
                    .background(Color(hex: M.chip), in: Circle())
            }
            .padding(.horizontal, 18)
            .frame(height: 52)
            HStack(spacing: 8) {
                ForEach(Array(GooPullRefreshSighting.chips.enumerated()), id: \.offset) { index, chip in
                    Text(chip)
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(index == 0 ? Color.white : ink)
                        .fixedSize()
                        .padding(.horizontal, 15)
                        .frame(height: 34)
                        .background(index == 0 ? ink : Color(hex: M.chip), in: Capsule())
                }
            }
            .padding(.horizontal, 18)
            .padding(.top, 6)
            .frame(width: width, height: 50, alignment: .topLeading)
            .clipped()
        }
        .padding(.top, top)
        .frame(width: width, height: top + M.headerH, alignment: .top)
        .background(Color(hex: M.header))
        .overlay(alignment: .bottom) {
            Rectangle()
                .fill(Color(hex: M.ink, opacity: 0.12))
                .frame(height: 1 / UIScreen.main.scale)
        }
    }
}

/// One photo tile: the photo, a title line and the byline with its likes.
struct GooPullRefreshTileView: View, Equatable {
    let tile: GooPullRefreshTile
    let width: Double

    private typealias M = GooPullRefreshMetrics

    var body: some View {
        let item = tile.item
        let muted = Color(hex: M.muted)
        VStack(alignment: .leading, spacing: 0) {
            GooPullRefreshPhoto(address: gooPullRefreshPhoto(item.id, Int((width * 2.5).rounded()), Int((tile.photo * 2.5).rounded())))
                .frame(width: width, height: tile.photo)
                .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            HStack(spacing: 6) {
                if tile.fresh {
                    Circle().fill(Color(hex: M.accent)).frame(width: 7, height: 7)
                }
                Text(item.title)
                    .font(.system(size: 14, weight: .semibold))
                    .tracking(-0.1)
                    .foregroundStyle(Color(hex: M.ink))
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(height: 18)
            .padding(.top, 8)
            HStack(spacing: 5) {
                Text(String(item.by.prefix(1)))
                    .font(.system(size: 9, weight: .bold))
                    .foregroundStyle(Color(hex: M.ink))
                    .frame(width: 17, height: 17)
                    .background(Color(hex: M.avatar), in: Circle())
                Text(item.by)
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(muted)
                    .lineLimit(1)
                    .frame(maxWidth: .infinity, alignment: .leading)
                Image(systemName: "heart")
                    .font(.system(size: 10.5, weight: .regular))
                    .foregroundStyle(muted)
                    .frame(width: 12, height: 12)
                Text(gooPullRefreshLikes(item.likes))
                    .font(.system(size: 12, weight: .medium).monospacedDigit())
                    .foregroundStyle(muted)
            }
            .frame(height: 17)
            .padding(.top, 3)
        }
        .frame(width: width, height: tile.photo + M.textH, alignment: .topLeading)
    }
}

/// A photo from the shared cache, loaded on first sight and faded in over 0.25 s; a blank tile of
/// the page's tint stands in while it loads or if it fails.
private struct GooPullRefreshPhoto: View {
    let address: String
    @State private var image: UIImage?

    var body: some View {
        ZStack {
            Color(hex: GooPullRefreshMetrics.photoBlank)
            if let image = image ?? GooPullRefreshPhotos.cached(address) {
                Image(uiImage: image)
                    .resizable()
                    .scaledToFill()
                    .transition(.opacity)
            }
        }
        .task(id: address) {
            guard GooPullRefreshPhotos.cached(address) == nil else { return }
            let loaded = await GooPullRefreshPhotos.load(address)
            withAnimation(.linear(duration: 0.25)) { image = loaded }
        }
    }
}

/// The liquid, drawn as the RN version records it into one Skia picture over the tiles: shapes in
/// one layer that is blurred and cut at an alpha threshold, so shapes that come close melt
/// together, and a glint on the drop.
enum GooPullRefreshLiquid {
    private typealias M = GooPullRefreshMetrics

    static func draw(_ context: inout GraphicsContext, frame: GooPullRefreshFrame, width: Double, barBottom bar: Double, reducedMotion: Bool) {
        let p = frame.phase
        let gap = max(0, frame.scroll)
        let m = frame.men
        guard width > 0, !(p == .idle && gap < 0.5 && m < M.menRest + 3) else { return }
        let cx = width / 2

        // The blur runs first, then the threshold; a hairline blur after it smooths the cut edge.
        var goo = context
        goo.addFilter(.blur(radius: 0.5))
        goo.addFilter(.alphaThreshold(min: 0.4, color: Color(hex: M.accent)))
        goo.addFilter(.blur(radius: M.gooBlur))

        var glint: (x: Double, y: Double, r: Double)?
        goo.drawLayer { layer in
            let ink = GraphicsContext.Shading.color(.black)
            layer.fill(Path(ellipseIn: CGRect(x: cx - M.resRX, y: bar + m - M.resRY * 2, width: M.resRX * 2, height: M.resRY * 2)), with: ink)
            if p == .idle {
                let q = M.clamp01(gap / M.snapPull)
                let r = M.mix(M.beadR, M.dropR, q) * frame.grow
                if r > 0.5 {
                    let cy = bar + gap * M.dropTravel
                    let w = min(r * 0.85, M.mix(M.neckWide, M.neckThin, pow(q, 1.2))) * frame.grow
                    neck(&layer, cx: cx, y0: bar + m, y1: cy - r * 0.6, width: w)
                    drop(&layer, cx: cx, cy: cy, r: r, s: 0)
                    glint = (cx, cy, r)
                }
            } else {
                let y = frame.dropY
                let r = frame.dropR
                let age = frame.jiggleAge
                let jiggle = M.jiggle * exp(-M.jiggleDecay * age) * sin(2 * .pi * M.jiggleHz * age)
                let s = reducedMotion ? 0 : max(-0.25, min(0.3, abs(frame.dropV) * M.stretchPerSpeed + jiggle))
                if p == .free {
                    neck(&layer, cx: cx, y0: bar + m, y1: y - r * 0.6, width: M.neckThin * frame.neck)
                } else if p == .returning {
                    // A neck forms again as the drop nears the meniscus, and pulls it in.
                    let near = M.clamp01(1 - (y - r - (bar + m)) / 36)
                    neck(&layer, cx: cx, y0: bar + m, y1: y - r * 0.6, width: 7 * near)
                }
                let loader = p == .loading || p == .merging
                drop(&layer, cx: cx, cy: y, r: r, s: loader ? 0 : s)
                if loader {
                    // Three droplets breathe out of the core and back while the trio turns.
                    let t = frame.phaseTime
                    let spread = p == .loading ? M.easeOut(t / M.spreadIn) : 1 - M.easeInOut(t / M.mergeTime)
                    let breath = reducedMotion ? 0 : 0.5 - 0.5 * cos(2 * .pi * (p == .loading ? t : M.refreshTime + t) / M.pulse)
                    let orbit = M.orbit * spread * breath
                    for k in 0..<3 {
                        let a = (frame.spin + Double(k) * 120) * .pi / 180
                        let sr = M.satR * spread * (1 - 0.18 * breath)
                        if sr > 0.3 {
                            let x = cx + cos(a) * orbit
                            let yy = y + sin(a) * orbit
                            layer.fill(Path(ellipseIn: CGRect(x: x - sr, y: yy - sr, width: sr * 2, height: sr * 2)), with: ink)
                        }
                    }
                }
                glint = (cx, y, r)
            }
        }
        // A soft highlight on the upper left of the drop, so it reads as liquid rather than a flat dot.
        if let g = glint, g.r >= 6 {
            context.fill(
                Path(ellipseIn: CGRect(x: g.x - g.r * 0.62, y: g.y - g.r * 0.62, width: g.r * 0.6, height: g.r * 0.38)),
                with: .color(.white.opacity(0.55))
            )
        }
    }

    /// Beads between the meniscus at `y0` and a drop at `y1`, wide at both ends and thinnest midway.
    private static func neck(_ layer: inout GraphicsContext, cx: Double, y0: Double, y1: Double, width: Double) {
        guard width >= 0.3, y1 > y0 else { return }
        for k in 1...M.neckBeads {
            let t = Double(k) / Double(M.neckBeads + 1)
            let r = width * (0.62 + 0.38 * pow(abs(2 * t - 1), 1.4))
            let y = M.mix(y0, y1, t)
            layer.fill(Path(ellipseIn: CGRect(x: cx - r, y: y - r, width: r * 2, height: r * 2)), with: .color(.black))
        }
    }

    /// A drop of radius `r` squashed by `s` (taller when positive), keeping about the same area.
    private static func drop(_ layer: inout GraphicsContext, cx: Double, cy: Double, r: Double, s: Double) {
        let rx = r * (1 - s * 0.5)
        let ry = r * (1 + s)
        layer.fill(Path(ellipseIn: CGRect(x: cx - rx, y: cy - ry, width: rx * 2, height: ry * 2)), with: .color(.black))
    }
}

// MARK: - Samples

func gooPullRefreshPhoto(_ id: String, _ w: Int, _ h: Int) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(h)&q=80"
}

func gooPullRefreshLikes(_ n: Int) -> String {
    guard n >= 1000 else { return "\(n)" }
    let text = String(format: "%.1f", Double(n) / 1000)
    return (text.hasSuffix(".0") ? String(text.dropLast(2)) : text) + "k"
}

extension GooPullRefreshSighting {
    static let chips = ["For you", "Cats", "Birds", "Nearby", "Following"]

    static let feed: [GooPullRefreshSighting] = [
        .init(id: "photo-1536589961747-e239b2abbec2", shape: .portrait, title: "Supervising the sofa delivery", by: "Ines Mora", likes: 2184),
        .init(id: "photo-1616081352935-7daa6487b6ae", shape: .portrait, title: "Low pass over the reeds", by: "Tomas Hale", likes: 932),
        .init(id: "photo-1498100152307-ce63fd6c5424", shape: .landscape, title: "Window seat, as always", by: "Lea Brandt", likes: 3016),
        .init(id: "photo-1591055749071-927e6ddffc82", shape: .portrait, title: "Hovered a full minute, then gone", by: "Kit Okafor", likes: 1207),
        .init(id: "photo-1596854372745-0906a0593bca", shape: .square, title: "Caught mid-judgement", by: "Noor Haddad", likes: 2875),
        .init(id: "photo-1593893722928-17454e6d5fb8", shape: .landscape, title: "Gulls riding the cliff updraft", by: "Tomas Hale", likes: 688),
        .init(id: "photo-1559624989-7b9303bd9792", shape: .portrait, title: "Found the one sunbeam in the flat", by: "Mika Sato", likes: 4410),
        .init(id: "photo-1470167494176-c2e966a4eb3d", shape: .landscape, title: "The whole flock turned at once", by: "Ada Lind", likes: 1540),
        .init(id: "photo-1647806422508-0322f33e270b", shape: .portrait, title: "Claimed the laundry basket", by: "Mika Sato", likes: 6230),
        .init(id: "photo-1626444344029-5c680f7513c1", shape: .portrait, title: "Tiny, loud, very sure of itself", by: "Kit Okafor", likes: 864),
        .init(id: "photo-1583795128727-6ec3642408f8", shape: .landscape, title: "Nap number four", by: "Joel Vance", likes: 5122),
        .init(id: "photo-1549589465-442e6195e54b", shape: .portrait, title: "Talons out, eyes on the grass", by: "Ada Lind", likes: 1993),
        .init(id: "photo-1582725461742-8ecd962c260d", shape: .portrait, title: "Back from the vet, still sulking", by: "Lea Brandt", likes: 3384),
        .init(id: "photo-1554409011-d874157657b8", shape: .portrait, title: "Spotted the heron first", by: "Joel Vance", likes: 1102),
    ]

    static let incoming: [[GooPullRefreshSighting]] = [
        [
            .init(id: "photo-1611843275167-a9bba9aa65dd", shape: .portrait, title: "Top of the bookshelf, finally", by: "Noor Haddad", likes: 214),
            .init(id: "photo-1567187549304-1fe8331879b5", shape: .portrait, title: "Same fence post every morning", by: "Tomas Hale", likes: 230),
        ],
        [
            .init(id: "photo-1597838816882-4435b1977fbe", shape: .square, title: "Morning stretch, full length", by: "Ines Mora", likes: 388),
            .init(id: "photo-1448227922836-6d05b3f8b663", shape: .square, title: "Wings folded, straight down", by: "Ada Lind", likes: 96),
        ],
        [
            .init(id: "photo-1552933529-e359b2477252", shape: .landscape, title: "Versus the curtain, round three", by: "Joel Vance", likes: 402),
            .init(id: "photo-1731650211720-54d314975a7b", shape: .landscape, title: "Two finches, one seed", by: "Kit Okafor", likes: 142),
        ],
    ]
}
