import SwiftUI
import UIKit

struct CoverFlowVinylAlbum {
    var title: String
    var artist: String
    var cover: String
    /// Track length and where playback starts, in seconds.
    var length: Double
    var startAt: Double
}

/// Albums in a cover flow on a dark floor: the one in front faces you, the rest stand turned
/// towards it on each side, overlapping, with their reflections fading into the floor. Swipe to
/// flip through. Tap the front cover and its record slides out to the right and spins, the cover
/// lifts and a small player fades in under it; tap again to slide the record back.
struct CoverFlowVinyl: View {
    var albums: [CoverFlowVinylAlbum] = CoverFlowVinylAlbum.samples
    /// Flips through the covers, pulls a record out, plays it, puts it back and flips back.
    var autoPlay = false
    /// Called with an album when its record is pulled out.
    var onPlay: ((CoverFlowVinylAlbum) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The flow's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = CoverFlowVinylEngine()

    var body: some View {
        // The block draws under the system bars and keeps its labels clear of them itself.
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
        .task(id: albums.map(\.cover)) { await loadCovers() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                albums: albums,
                width: size.width,
                height: size.height,
                autoPlay: autoPlay,
                reducedMotion: reduceMotion
            )
            CoverFlowVinylScene(frame: frame, albums: albums, engine: engine, size: size, insets: insets, reducedMotion: reduceMotion)
        }
        .frame(width: size.width, height: size.height)
        .background(CoverFlowVinylPalette.background)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onAppear { engine.onPlay = onPlay }
        .accessibilityElement()
        .accessibilityLabel("Cover flow of \(albums.count) records")
        .accessibilityHint("Swipe to flip through albums, tap the cover to play its record")
    }

    private func loadCovers() async {
        await withTaskGroup(of: (String, UIImage?).self) { group in
            for address in Set(albums.map(\.cover)) {
                group.addTask { (address, await CoverFlowVinylPhotos.load(address)) }
            }
            for await (address, image) in group {
                if let image { engine.photoArrived(image, for: address) }
            }
        }
    }
}

enum CoverFlowVinylPalette {
    static let background = Color(hex: 0x0d0c0b)
    static let ink = Color(hex: 0xf3eee6)
    static let muted = Color(hex: 0x8f877c)
    static let track = Color(hex: 0x2c2925)
    static let accent = Color(hex: 0xe8a24a)
}

// MARK: - The scene

/// Everything one frame draws: the wall, the header, the covers stacked by their distance from the
/// front with the record just under the front one, the caption, the player and the hint.
private struct CoverFlowVinylScene: View {
    let frame: CoverFlowVinylFrame
    let albums: [CoverFlowVinylAlbum]
    let engine: CoverFlowVinylEngine
    let size: CGSize
    let insets: EdgeInsets
    let reducedMotion: Bool

    private typealias M = CoverFlowVinylMetrics
    private typealias P = CoverFlowVinylPalette

    var body: some View {
        let width = size.width
        let cover = min(width * M.sizeShare, M.maxSize)
        let centreY = size.height * M.centreAt
        let count = albums.count
        let album = albums[frame.front]
        let open = frame.open

        ZStack(alignment: .topLeading) {
            // A warm wall down to the floor the covers stand on; the floor is flat, so reflections melt into it.
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x1d1a16), location: 0),
                    .init(color: Color(hex: 0x13110f), location: 0.6),
                    .init(color: P.background, location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: width, height: centreY + cover / 2)

            ForEach(0..<count, id: \.self) { index in
                coverView(index: index, size: cover, centreY: centreY)
            }

            CoverFlowVinylRecord(
                image: engine.photo(album.cover),
                size: cover,
                spin: frame.spin
            )
            .opacity(reducedMotion ? min(max(open, 0), 1) : min(max(open * 6, 0), 1))
            .offset(
                x: width / 2 - cover * M.recordSize / 2 + (reducedMotion ? 1 : open) * (M.recordOut - M.shift) * cover,
                y: centreY - cover * M.recordSize / 2 - (reducedMotion ? 0 : min(max(open, 0), 1.2) * M.lift)
            )
            // Between the front cover (100) and its neighbours (90 and below).
            .zIndex(96.5)

            Group {
                header
                    .frame(width: width - 44)
                    .offset(x: 22, y: insets.top + 14)

                captions(width: width, count: count)
                    .offset(x: 32, y: centreY + cover / 2 + M.captionGap - open * M.lift * 0.6)

                player(album: album, width: width)
                    .offset(x: 40, y: centreY + cover / 2 + M.captionGap + 66)

                Text("Swipe to browse · Tap a cover to play")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.muted)
                    .frame(width: width)
                    .opacity(1 - min(max(open * 2, 0), 1))
                    .offset(y: size.height - insets.bottom - 22 - 16)
            }
            .zIndex(200)
        }
        .frame(width: width, height: size.height, alignment: .topLeading)
    }

    // One sleeve standing on the floor, with its reflection under it, in a flat layer of its own
    // stacked by its distance from the front.
    private func coverView(index: Int, size: Double, centreY: Double) -> some View {
        let count = albums.count
        let d = M.centred(Double(index) - frame.scroll, count)
        let p = M.placement(d)
        let o = min(max(frame.open, 0), 1.2)
        let front = max(0, 1 - abs(d))
        let sign: Double = d > 0 ? 1 : d < 0 ? -1 : 0
        // Opening moves the front cover up and left, and spreads the rest outwards.
        let x = p.x * size + sign * o * M.spread * size * (1 - front) - front * o * M.shift * size
        let lift = reducedMotion ? 0 : front * frame.open * M.lift
        let alpha = p.alpha * (1 - (1 - front) * min(max(o, 0), 1) * 0.85)
        let album = albums[index]
        return CoverFlowVinylSleeve(
            image: engine.photo(album.cover),
            elapsed: frame.elapsed,
            size: size,
            dim: p.dim,
            reflection: M.reflectionStrength * (1 - p.dim) * (1 - min(max(frame.open, 0), 1) * 0.6)
        )
        .projectionEffect(M.projection(size: size, scale: p.scale, turn: p.turn, x: reducedMotion ? p.x * size : x, lift: lift))
        .opacity(alpha)
        .offset(x: self.size.width / 2 - size / 2, y: centreY - size / 2)
        // Kept in Double: Int() of a non-finite d would trap.
        .zIndex(100 - M.round(abs(d) * 10))
    }

    private var header: some View {
        HStack(alignment: .bottom) {
            Text("Crate")
                .font(.system(size: 30, weight: .bold))
                .kerning(-0.8)
                .foregroundStyle(P.ink)
                .fixedSize()
            Spacer()
            Text("\(albums.count) records")
                .font(.system(size: 13, weight: .medium))
                .kerning(0.3)
                .foregroundStyle(P.muted)
        }
    }

    // Each album's title and artist, crossfading with the flow as its cover comes to the front.
    private func captions(width: Double, count: Int) -> some View {
        ZStack(alignment: .top) {
            ForEach(0..<count, id: \.self) { index in
                let d = M.centred(Double(index) - frame.scroll, count)
                let alpha = min(max(1 - abs(d) * 2.2, 0), 1)
                if alpha > 0 {
                    VStack(spacing: 4) {
                        Text(albums[index].title)
                            .font(.system(size: 22, weight: .bold))
                            .kerning(-0.4)
                            .foregroundStyle(P.ink)
                            .lineLimit(1)
                        Text(albums[index].artist)
                            .font(.system(size: 15, weight: .medium))
                            .foregroundStyle(P.muted)
                            .lineLimit(1)
                    }
                    .frame(width: width - 64)
                    .opacity(alpha)
                    .offset(x: d * -24)
                }
            }
        }
        .frame(width: width - 64, height: 54, alignment: .top)
    }

    private func player(album: CoverFlowVinylAlbum, width: Double) -> some View {
        let open = frame.open
        let o = min(max((open - 0.45) / 0.55, 0), 1)
        let seconds = floor(frame.track)
        return VStack(spacing: 8) {
            GeometryReader { geo in
                ZStack(alignment: .leading) {
                    P.track
                    P.accent.frame(width: geo.size.width * frame.track / album.length)
                }
            }
            .frame(height: 3)
            .clipShape(RoundedRectangle(cornerRadius: 1.5))
            HStack {
                Text(Self.time(seconds))
                Spacer()
                Text("-" + Self.time(album.length - seconds))
            }
            .font(.system(size: 12, weight: .medium).monospacedDigit())
            .foregroundStyle(P.muted)
            HStack(spacing: 44) {
                CoverFlowVinylSymbol(name: "backward.fill", size: 22, color: P.ink)
                ZStack {
                    Circle().fill(P.ink)
                    CoverFlowVinylSymbol(name: frame.playing ? "pause.fill" : "play.fill", size: 24, color: P.background)
                }
                .frame(width: 58, height: 58)
                CoverFlowVinylSymbol(name: "forward.fill", size: 22, color: P.ink)
            }
            .padding(.top, 8)
        }
        .frame(width: width - 80)
        .opacity(o)
        .offset(y: (1 - o) * 14 - open * M.lift * 0.6)
        .allowsHitTesting(false)
    }

    static func time(_ s: Double) -> String {
        let whole = Int(max(0, s))
        return String(format: "%d:%02d", whole / 60, whole % 60)
    }
}

/// A sleeve laid out flat (the cover over its reflection, two covers tall) before its perspective.
private struct CoverFlowVinylSleeve: View {
    let image: (image: UIImage, at: Double)?
    let elapsed: Double
    let size: Double
    let dim: Double
    let reflection: Double

    private typealias M = CoverFlowVinylMetrics

    var body: some View {
        let fade = image.map { min(max((elapsed - $0.at) / M.imageFade, 0), 1) } ?? 0
        VStack(spacing: 3) {
            ZStack {
                Color(hex: 0x1c1a17)
                if let image {
                    Image(uiImage: image.image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: size, height: size)
                        .clipped()
                        .opacity(fade)
                }
                RoundedRectangle(cornerRadius: 4)
                    .strokeBorder(Color.white.opacity(0.18), lineWidth: 1 / UIScreen.main.scale)
                Color.black.opacity(dim > 0.002 ? dim : 0)
            }
            .frame(width: size, height: size)
            .clipShape(RoundedRectangle(cornerRadius: 4))

            // The reflection is dimmed through its own opacity, so under it is always exactly the
            // floor's colour and no box shows round it.
            ZStack {
                CoverFlowVinylPalette.background
                if let image {
                    Image(uiImage: image.image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: size, height: size)
                        .clipped()
                        .scaleEffect(x: 1, y: -1)
                        .opacity(reflection * fade)
                }
                LinearGradient(
                    stops: [
                        .init(color: CoverFlowVinylPalette.background.opacity(0), location: 0),
                        .init(color: CoverFlowVinylPalette.background, location: M.reflection),
                        .init(color: CoverFlowVinylPalette.background, location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
            }
            .frame(width: size, height: size - 3, alignment: .top)
            .clipShape(RoundedRectangle(cornerRadius: 4))
        }
        .frame(width: size, height: size * 2, alignment: .top)
        .compositingGroup()
    }
}

// The grooves: rings of slightly different greys, with three gaps between tracks.
private let coverFlowVinylGrooves = 46
private let coverFlowVinylGaps: Set<Int> = [11, 23, 34]

/// A black record behind the front sleeve; it slides out to the right and spins. Only its label
/// turns: the grooves are rings, and the light on them stays put.
private struct CoverFlowVinylRecord: View {
    let image: (image: UIImage, at: Double)?
    let size: Double
    let spin: Double

    private typealias M = CoverFlowVinylMetrics

    var body: some View {
        let disc = size * M.recordSize
        let r = disc / 2
        let label = disc * M.label
        ZStack {
            Canvas { context, _ in
                let centre = CGPoint(x: r, y: r)
                func circle(_ radius: Double) -> Path {
                    Path(ellipseIn: CGRect(x: centre.x - radius, y: centre.y - radius, width: radius * 2, height: radius * 2))
                }
                context.fill(circle(r), with: .color(Color(hex: 0x090909)))
                for k in 0..<coverFlowVinylGrooves {
                    let radius = label / 2 + 6 + (r - label / 2 - 10) * Double(k) / Double(coverFlowVinylGrooves - 1)
                    let gap = coverFlowVinylGaps.contains(k)
                    let color: Color = gap ? .black.opacity(0.95) : .white.opacity(k % 2 == 0 ? 0.075 : 0.04)
                    context.stroke(circle(radius), with: .color(color), lineWidth: gap ? 1.6 : 0.7)
                }
                context.stroke(circle(r - 1), with: .color(.white.opacity(0.12)), lineWidth: 1.4)
                // The smooth run-out round the label.
                context.fill(circle(label / 2 + 5), with: .color(Color(hex: 0x050505)))
                // The light on the grooves stays put while the record turns under it.
                context.fill(
                    circle(r - 2),
                    with: .conicGradient(
                        Gradient(stops: [
                            .init(color: .white.opacity(0), location: 0.04),
                            .init(color: .white.opacity(0.2), location: 0.12),
                            .init(color: .white.opacity(0), location: 0.22),
                            .init(color: .white.opacity(0), location: 0.54),
                            .init(color: .white.opacity(0.15), location: 0.62),
                            .init(color: .white.opacity(0), location: 0.72),
                            .init(color: .white.opacity(0), location: 1),
                        ]),
                        center: centre,
                        angle: .zero
                    )
                )
            }
            .frame(width: disc, height: disc)

            ZStack {
                Color(hex: 0x141414)
                if let image {
                    Image(uiImage: image.image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .frame(width: label - 4, height: label - 4)
                        .clipShape(Circle())
                }
                Circle().fill(CoverFlowVinylPalette.background).frame(width: 8, height: 8)
            }
            .frame(width: label, height: label)
            .clipShape(Circle())
            .rotationEffect(.degrees(spin))
        }
        .frame(width: disc, height: disc)
    }
}

/// An SF Symbol fitted into a square of `size` points, like `SymbolView` in the RN version.
private struct CoverFlowVinylSymbol: View {
    let name: String
    let size: CGFloat
    let color: Color

    var body: some View {
        Image(systemName: name)
            .resizable()
            .aspectRatio(contentMode: .fit)
            .foregroundStyle(color)
            .frame(width: size, height: size)
    }
}

// MARK: - Samples

private func coverFlowVinylCover(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=600&h=600&q=80"
}

extension CoverFlowVinylAlbum {
    static let samples: [CoverFlowVinylAlbum] = [
        .init(title: "Low Tide Radio", artist: "Mara Quell", cover: coverFlowVinylCover("photo-1644855640845-ab57a047320e"), length: 228, startAt: 64),
        .init(title: "Yellow Room", artist: "June Calloway", cover: coverFlowVinylCover("photo-1645919268978-35253dfb1d75"), length: 201, startAt: 41),
        .init(title: "Glass Choir", artist: "Ilse Varga", cover: coverFlowVinylCover("photo-1651443039959-582bbea6be6a"), length: 254, startAt: 72),
        .init(title: "Half Moon Club", artist: "Corvo & Lune", cover: coverFlowVinylCover("photo-1614283868067-b4e06385bf93"), length: 236, startAt: 38),
        .init(title: "Paper Suns", artist: "The Halvorsens", cover: coverFlowVinylCover("photo-1717598604697-bb2e9e841c1c"), length: 192, startAt: 55),
        .init(title: "Number Nine", artist: "The Sundries", cover: coverFlowVinylCover("photo-1645677042909-907678e3a40a"), length: 213, startAt: 47),
        .init(title: "Field Notes", artist: "Tomas Rieu", cover: coverFlowVinylCover("photo-1598909688344-fbe5e6d4c869"), length: 244, startAt: 80),
        .init(title: "Velvet Interval", artist: "Odile Fenn", cover: coverFlowVinylCover("photo-1611506420749-9cbb8e5a1057"), length: 262, startAt: 59),
        .init(title: "Hand-Me-Down Heat", artist: "Desert Bloom Society", cover: coverFlowVinylCover("photo-1694355450043-04721056e2f1"), length: 207, startAt: 33),
        .init(title: "Long Distance", artist: "Kite Assembly", cover: coverFlowVinylCover("photo-1588066077857-70494c21533c"), length: 219, startAt: 50),
    ]
}
