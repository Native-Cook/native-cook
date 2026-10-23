import SwiftUI
import UIKit

struct MoodCardShufflePlace {
    var name: String
    /// Small caps line above the name, e.g. "Yên Bái · Vietnam".
    var region: String
    /// One sentence about the place; it is set on at most two lines.
    var blurb: String
    /// An Unsplash photo id (`photo-…`), landscape.
    var photo: String
}

struct MoodCardShuffleMood {
    var label: String
    /// Four places: the deck always holds four cards (fewer are repeated).
    var places: [MoodCardShufflePlace]
}

/// A mood-based place finder: a deck of photo cards over a blurred copy of the front one. Swiping
/// turns the deck like a drum: the front card swings out to the side and tucks in behind while the
/// next one swings in and straightens up. The mood chips in the sheet below riffle the deck into a
/// pile and deal out another set of places. The deck and the backdrop are drawn from one engine
/// stepped once a frame; the cards are seen through a true perspective projection.
struct MoodCardShuffle: View {
    var moods: [MoodCardShuffleMood] = MoodCardShuffleMood.samples
    var title = "Where do you feel\nlike going?"
    /// Turns cards and changes the mood twice, on a loop of 14 seconds.
    var autoPlay = false
    /// Called with a mood's label when the deck is reshuffled to it.
    var onMoodChange: ((String) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    // The deck's motion lives in a plain object stepped once a frame, like the RN frame callback:
    // nothing in it is observed, the timeline redraws every frame anyway.
    @State private var engine = MoodCardShuffleEngine()
    @State private var photos = MoodCardShufflePhotos()
    @State private var chosen = 0

    private static let space = "moodCardShuffleStage"
    private static let titleShift = min(0, (36 - MoodCardShuffleType.uiFont("AvenirNext-Medium", 30, .medium).lineHeight) / 2)

    /// Every mood padded (or trimmed) to exactly four places.
    private var normalised: [MoodCardShuffleMood] {
        let list = moods.filter { !$0.places.isEmpty }
        let safe = list.isEmpty ? MoodCardShuffleMood.samples : list
        return safe.map { m in
            MoodCardShuffleMood(label: m.label, places: (0..<MoodCardShuffleMetrics.count).map { m.places[$0 % m.places.count] })
        }
    }

    var body: some View {
        let moods = normalised
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets, moods: moods)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: moods.flatMap { $0.places.map(\.photo) }) {
            let engine = engine
            await photos.load(moods.flatMap { $0.places.map(\.photo) }, clock: { engine.elapsed })
        }
        .onAppear {
            engine.onAnnounce = { next, _ in
                chosen = next
                if next >= 0 && next < moods.count { onMoodChange?(moods[next].label) }
            }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets, moods: [MoodCardShuffleMood]) -> some View {
        let card = MoodCardShuffleMetrics.cardSize(Double(size.width))
        prepareFaces(moods: moods, cardWidth: Double(card.width))
        engine.reduced = reduceMotion
        engine.autoPlay = autoPlay
        engine.stageSize = size
        return ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let frame = engine.frame(at: timeline.date, photos: photos.loaded)
                let shadow = photos.shadow(for: frame.cardSize, scale: displayScale)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, canvasSize in
                        MoodCardShufflePaint.backdrop(ctx, size: canvasSize, frame: frame)
                    }
                    .frame(width: size.width, height: size.height)
                    ForEach(0..<7, id: \.self) { slot in
                        cardView(frame: frame, slot: slot, shadow: shadow)
                    }
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            controls(size: size, insets: insets, moods: moods)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(.named(Self.space))
        .background(Color(hex: 0x2a2820))
        .clipped()
    }

    /// Lays the blurbs out again only when the card's width (or the moods) change.
    private func prepareFaces(moods: [MoodCardShuffleMood], cardWidth: Double) {
        let key = "\(cardWidth)|" + moods.flatMap { m in m.places.map { "\($0.name)/\($0.region)/\($0.blurb)/\($0.photo)" } }.joined(separator: "|")
        guard key != engine.facesKey else { return }
        engine.facesKey = key
        engine.faces = moods.map { m in m.places.map { MoodCardShuffleType.face($0, cardWidth: cardWidth) } }
    }

    @ViewBuilder
    private func cardView(frame: MoodCardShuffleFrame, slot: Int, shadow: UIImage?) -> some View {
        if slot < frame.items.count, let item = frame.items[slot], frame.cardSize.width > 0 {
            let w = Double(frame.cardSize.width)
            let h = Double(frame.cardSize.height)
            let m = MoodCardShufflePaint.margin
            let now = frame.now
            Canvas { ctx, _ in
                var c = ctx
                c.translateBy(x: w / 2 + m, y: h / 2 + m)
                MoodCardShufflePaint.card(c, item: item, w: w, h: h, now: now, shadow: shadow)
            }
            .frame(width: w + m * 2, height: h + m * 2)
            .projectionEffect(MoodCardShuffleMath.projection(item.pose, cx: frame.cx, cy: frame.cy, ox: w / 2 + m, oy: h / 2 + m))
            .opacity(item.alpha)
        }
    }

    // MARK: Controls

    private func controls(size: CGSize, insets: EdgeInsets, moods: [MoodCardShuffleMood]) -> some View {
        VStack(spacing: 0) {
            VStack(spacing: 0) {
                // Two set lines 36 pt apart, tighter than the face's own line height.
                VStack(spacing: 0) {
                    ForEach(Array(title.split(separator: "\n", omittingEmptySubsequences: false).enumerated()), id: \.offset) { _, line in
                        Text(String(line))
                            .font(MoodCardShuffleType.medium(30))
                            .kerning(-0.4)
                            .foregroundStyle(.white)
                            .multilineTextAlignment(.center)
                            .frame(height: 36)
                            // RN caps the 41 pt line at 36 pt from the top, so the glyphs sit higher
                            // than a centred line would put them.
                            .offset(y: Self.titleShift)
                    }
                }
                .frame(maxWidth: .infinity)
                .shadow(color: .black.opacity(0.18), radius: 6, x: 0, y: 1)
                .accessibilityElement(children: .combine)
                .accessibilityAddTraits(.isHeader)

                deck(moods: moods)
                    .frame(maxWidth: .infinity, maxHeight: .infinity)
                    .frame(minHeight: 280)

                HStack(spacing: 8) {
                    // SymbolView size 15: the symbol fitted into a 15 pt square.
                    MoodCardShuffleSymbol(name: "magnifyingglass", size: 15)
                    Text("Search places…")
                        .font(MoodCardShuffleType.medium(15))
                }
                .foregroundStyle(Color.white.opacity(0.92))
                .padding(.horizontal, 20)
                .frame(height: 44)
                .background(Capsule().fill(Color.white.opacity(0.14)))
                .overlay(Capsule().strokeBorder(Color.white.opacity(0.45), lineWidth: 1 / max(1, displayScale)))
                .padding(.bottom, 24)
            }
            .padding(.top, insets.top + 30)

            MoodCardShuffleSheet(
                labels: moods.map(\.label),
                chosen: chosen,
                reduced: reduceMotion,
                bottomPadding: max(insets.bottom, 16) + 14,
                onChip: { index in
                    engine.touched()
                    engine.choose(index, user: true)
                },
                onSurprise: {
                    engine.touched()
                    engine.surprise(chosen: chosen)
                }
            )
        }
        .frame(width: size.width, height: size.height)
    }

    private func deck(moods: [MoodCardShuffleMood]) -> some View {
        let label = chosen < moods.count ? moods[chosen].label : ""
        return Color.clear
            .contentShape(Rectangle())
            .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { engine.deckFrame = $0 }
            .gesture(
                DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
                    .onChanged { engine.touchChanged(translation: $0.translation, time: $0.time) }
                    .onEnded {
                        engine.touchEnded(location: $0.location, translation: $0.translation, velocity: $0.velocity, time: $0.time)
                    }
            )
            .accessibilityElement()
            .accessibilityLabel("\(label) places. Swipe to turn the deck.")
            .accessibilityAdjustableAction { direction in
                switch direction {
                case .increment: engine.advance(1, byUser: true)
                case .decrement: engine.advance(-1, byUser: true)
                @unknown default: break
                }
            }
    }
}

// MARK: - The sheet

/// The white sheet: the mood chips with a black pill sliding to the chosen one, and "Go Unexpected".
private struct MoodCardShuffleSheet: View {
    let labels: [String]
    let chosen: Int
    let reduced: Bool
    let bottomPadding: CGFloat
    let onChip: (Int) -> Void
    let onSurprise: () -> Void

    @State private var frames: [Int: CGRect] = [:]
    @State private var pill = CGRect.zero

    private static let ink = Color(hex: 0x11110d)
    private static let pillStiffness = MoodCardShuffleSpring.stiffness(
        duration: MoodCardShuffleMetrics.pillDuration,
        dampingRatio: MoodCardShuffleMetrics.pillDampingRatio
    )

    private var pillSpring: Animation {
        let k = Self.pillStiffness
        return .interpolatingSpring(mass: 1, stiffness: k, damping: 2 * MoodCardShuffleMetrics.pillDampingRatio * sqrt(k))
    }

    var body: some View {
        VStack(spacing: 20) {
            ScrollViewReader { proxy in
                ScrollView(.horizontal, showsIndicators: false) {
                    HStack(spacing: 8) {
                        ForEach(labels.indices, id: \.self) { index in
                            chip(index)
                                .id(index)
                                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("moodCardShuffleChips")) } action: { frame in
                                    frames[index] = frame
                                    if index == chosen && (pill.width == 0 || reduced) { pill = frame }
                                }
                        }
                    }
                    .background(alignment: .topLeading) {
                        Capsule()
                            .fill(Self.ink)
                            .frame(width: pill.width, height: pill.height)
                            .offset(x: pill.minX, y: pill.minY)
                            .opacity(pill.width > 0 ? 1 : 0)
                    }
                    .coordinateSpace(.named("moodCardShuffleChips"))
                    .padding(.horizontal, 20)
                }
                .onChange(of: chosen) { _, next in
                    guard let frame = frames[next] else { return }
                    if reduced || pill.width == 0 {
                        pill = frame
                    } else {
                        withAnimation(pillSpring) { pill = frame }
                    }
                    // The row scrolls to keep the chosen chip in view.
                    withAnimation(.easeInOut(duration: 0.3)) { proxy.scrollTo(next, anchor: .center) }
                }
            }

            Button(action: onSurprise) {
                HStack(spacing: 12) {
                    VStack(alignment: .leading, spacing: 2) {
                        Text("Go Unexpected")
                            .font(MoodCardShuffleType.demiBold(21))
                            .kerning(-0.3)
                            .foregroundStyle(Self.ink)
                        Text("Let the deck pick a mood for you")
                            .font(MoodCardShuffleType.medium(13))
                            .foregroundStyle(Color.rgba(17, 17, 13, 0.55))
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    MoodCardShuffleSymbol(name: "shuffle", size: 16)
                        .foregroundStyle(.white)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(Self.ink))
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(MoodCardShufflePress())
            .padding(.horizontal, 20)
        }
        .padding(.top, 20)
        .padding(.bottom, bottomPadding)
        .frame(maxWidth: .infinity)
        .background(
            UnevenRoundedRectangle(topLeadingRadius: 30, topTrailingRadius: 30, style: .continuous)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.12), radius: 15, x: 0, y: -8)
                .ignoresSafeArea(edges: .bottom)
        )
    }

    private func chip(_ index: Int) -> some View {
        let on = index == chosen
        return Button { onChip(index) } label: {
            Text(labels[index])
                .font(MoodCardShuffleType.medium(15))
                .foregroundStyle(on ? Color.white : Self.ink)
                // 20 pt of padding plus the 1 pt border on each side, as RN lays it out.
                .padding(.horizontal, 21)
                .frame(height: 44)
                .overlay(Capsule().strokeBorder(on ? Color.clear : Color.rgba(17, 17, 13, 0.12), lineWidth: 1))
                .contentShape(Capsule())
        }
        .buttonStyle(MoodCardShufflePress())
        .accessibilityAddTraits(on ? .isSelected : [])
    }
}

/// An SF Symbol drawn like expo-symbols' SymbolView: the symbol image at the system font's point
/// size, semibold, fitted into a `size` pt square (so the glyph keeps the image's own padding).
private struct MoodCardShuffleSymbol: View {
    let name: String
    let size: CGFloat

    /// The symbol drawn into a plain image, so its padding is kept when it is scaled to fit (SwiftUI
    /// scales a symbol image by its glyph alone).
    private static func flat(_ name: String) -> UIImage? {
        let config = UIImage.SymbolConfiguration(pointSize: UIFont.systemFontSize, weight: .semibold)
        guard let symbol = UIImage(systemName: name, withConfiguration: config),
              symbol.size.width > 0, symbol.size.height > 0 else { return nil }
        let image = UIGraphicsImageRenderer(size: symbol.size).image { _ in
            symbol.draw(in: CGRect(origin: .zero, size: symbol.size))
        }
        return image.withRenderingMode(.alwaysTemplate)
    }

    var body: some View {
        if let image = Self.flat(name) {
            Image(uiImage: image)
                .renderingMode(.template)
                .resizable()
                .scaledToFit()
                .frame(width: size, height: size)
        } else {
            Color.clear.frame(width: size, height: size)
        }
    }
}

/// Pressed controls shrink a little, like the RN Pressable.
private struct MoodCardShufflePress: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
    }
}

// MARK: - Samples

extension MoodCardShuffleMood {
    static let samples: [MoodCardShuffleMood] = [
        .init(label: "Quiet", places: [
            .init(name: "Mù Cang Chải", region: "Yên Bái · Vietnam", blurb: "Layered rice terraces, misty hills and slow mornings in the highlands.", photo: "photo-1609412058473-c199497c3c5d"),
            .init(name: "Black Forest", region: "Baden · Germany", blurb: "Fog-soft spruce trails, wood-fired saunas and nobody in a hurry.", photo: "photo-1732130318657-c8740c0f5215"),
            .init(name: "Blue Ridge", region: "Virginia · USA", blurb: "Ridge after ridge fading into blue, best seen from a porch at dusk.", photo: "photo-1552152370-fb05b25ff17d"),
            .init(name: "Trà Quế", region: "Quảng Nam · Vietnam", blurb: "Bicycle lanes through the herb gardens, lanterns once the sun goes down.", photo: "photo-1608335715837-1994a535d5c3"),
        ]),
        .init(label: "Nature", places: [
            .init(name: "Hoàng Su Phì", region: "Hà Giang · Vietnam", blurb: "Terraces stacked like contour lines, bright green before the harvest.", photo: "photo-1480996408299-fc0e830b5db1"),
            .init(name: "Hoh Valley", region: "Washington · USA", blurb: "Moss-hung rainforest and rivers the colour of glacier milk.", photo: "photo-1542273917363-3b1817f69a2d"),
            .init(name: "Central Otago", region: "South Island · New Zealand", blurb: "Rolling hills, open roads and quiet valleys stretching into the distance.", photo: "photo-1612441804231-77a36b284856"),
            .init(name: "Bieszczady", region: "Podkarpacie · Poland", blurb: "Cloud pouring through old beech forest along the eastern border.", photo: "photo-1488330890490-c291ecf62571"),
        ]),
        .init(label: "Artisanal Food", places: [
            .init(name: "Sa Pa", region: "Lào Cai · Vietnam", blurb: "Hand-milled sticky rice, smoked pork and corn wine at the Sunday market.", photo: "photo-1711060266983-92bd378c850c"),
            .init(name: "Banaue", region: "Ifugao · Philippines", blurb: "Heirloom red rice grown on terraces two thousand years old.", photo: "photo-1548710332-3e03e8220a79"),
            .init(name: "Jatiluwih", region: "Bali · Indonesia", blurb: "Farm kitchens cooking straight from the fields outside the window.", photo: "photo-1715755455989-76413081ad10"),
            .init(name: "Campo Imperatore", region: "Abruzzo · Italy", blurb: "Shepherds' pecorino, saffron and lentils from the high plains.", photo: "photo-1601281426795-9fedf41df8d1"),
        ]),
        .init(label: "Wild", places: [
            .init(name: "Dolomites", region: "South Tyrol · Italy", blurb: "Limestone spires, iron-rung climbs and huts above the clouds.", photo: "photo-1611080402167-ed75bae6df32"),
            .init(name: "Julian Alps", region: "Triglav · Slovenia", blurb: "Glacial valleys, emerald rivers and trails with no signal at all.", photo: "photo-1610552050890-fe99536c2615"),
            .init(name: "Tongass", region: "Alaska · USA", blurb: "The wettest, wildest rainforest in the north, and bears on the shore.", photo: "photo-1476362555312-ab9e108a0b7e"),
            .init(name: "Huayhuash", region: "Áncash · Peru", blurb: "Ten days of passes over 4,500 m, dawn turning the peaks pink.", photo: "photo-1500964757637-c85e8a162699"),
        ]),
    ]
}
