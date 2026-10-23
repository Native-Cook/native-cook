import SwiftUI
import UIKit

struct PerspectiveRolodexItem {
    var title: String
    /// One word, shown between "Because" and "Matters" while the card is in front.
    var category: String
    /// A line under the title once the card is open.
    var caption: String
    /// Address of a photo, about 1 : 0.93.
    var photo: String
    /// Flat colour shown until the photo arrives.
    var tint: Color
}

/// How the cards already seen leave: lying back face up towards the viewer, or tipping forward.
enum PerspectiveRolodexFall {
    case back, forward
}

/// An editorial rolodex of photo cards on a black screen. The card in front stands a little above
/// the middle; the cards to come wait behind it in a tall stack that climbs away to the top of the
/// screen, a sliver of each showing; and the cards already seen lie back towards the viewer, large
/// and leaning, until they leave the bottom edge. Dragging turns the stack and a release snaps a
/// card to the front; the stack never ends. Tapping the card in front stands it up, lifts it out to
/// nearly the full width and shows its title; a drag down puts it back.
struct PerspectiveRolodex: View {
    /// The cards, in the order they come forward. Sixteen or more keep the far stack reaching the top.
    var items: [PerspectiveRolodexItem] = PerspectiveRolodexItem.samples
    /// Brings the next card forward every 1.4 s, like a rolodex turned by hand.
    var autoPlay = false
    /// How the cards already seen leave the front.
    var fall: PerspectiveRolodexFall = .back
    var background = Color(hex: 0x000000)
    var textColor = Color(hex: 0xf2f1ec)
    /// Called with the index of a card when it opens.
    var onOpen: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The stack's motion lives in a plain object stepped once a frame, like the RN frame callback:
    // nothing in it is observed, the timeline redraws every frame anyway.
    @State private var engine = PerspectiveRolodexEngine()

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
        .task(id: items.map(\.photo)) { await loadPhotos() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                stage: PerspectiveRolodexStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                settings: settings
            )
            PerspectiveRolodexScene(frame: frame, items: items, background: background, textColor: textColor)
        }
        .frame(width: size.width, height: size.height)
        .background(background)
        .clipped()
        .contentShape(Rectangle())
        .gesture(touch)
        .accessibilityElement()
        .accessibilityLabel("Rolodex of \(items.count) photo cards. Swipe down to bring the next card forward; tap the front card to open it.")
    }

    private var settings: PerspectiveRolodexSettings {
        PerspectiveRolodexSettings(
            count: items.count,
            fall: fall,
            autoPlay: autoPlay && !reduceMotion,
            reducedMotion: reduceMotion,
            onOpen: onOpen
        )
    }

    // One recogniser stands in for the race of a pan (after 6 pt of travel) and a tap (a touch
    // lifted within 400 ms that never travelled that far).
    private var touch: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in engine.touchMoved(value, settings: settings) }
            .onEnded { value in engine.touchEnded(value, settings: settings) }
    }

    // Each photo arrives on its own and fades in; until then its card shows its tint.
    private func loadPhotos() async {
        engine.resetPhotos(count: items.count)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, item) in items.enumerated() {
                group.addTask { (index, await PerspectiveRolodexPhotos.load(item.photo)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

/// Everything one frame draws: the cards far to near, the open card's caption and the labels.
private struct PerspectiveRolodexScene: View {
    let frame: PerspectiveRolodexFrame
    let items: [PerspectiveRolodexItem]
    let background: Color
    let textColor: Color

    private typealias M = PerspectiveRolodexMetrics

    var body: some View {
        let stage = frame.stage
        let width = stage.width
        let height = stage.height
        let chrome = 1 - 0.6 * min(max(frame.open, 0), 1)
        let topLine = stage.top + 14
        let bottomLine = height - stage.bottom - 10
        let side = M.cardLayoutWidth(width)

        ZStack(alignment: .topLeading) {
            Color.clear.frame(width: width, height: height)

            // The middle label at the top sits behind the stack, which climbs past it.
            label("Content", x: width / 2, baseline: topLine, align: 0.5, alpha: chrome)

            ForEach(0..<items.count, id: \.self) { index in
                card(index: index, side: side)
            }

            // The near cards sink into the background towards the bottom edge.
            LinearGradient(
                colors: [background.opacity(0), background.opacity(0xf2 / 255)],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: width, height: M.bottomFade)
            .offset(y: height - M.bottomFade)
            .zIndex(1000)

            Group {
                label("Selected", x: M.openMargin, baseline: topLine, align: 0, alpha: chrome)
                label("For You", x: width - M.openMargin, baseline: topLine, align: 1, alpha: chrome)
                label("Because", x: M.openMargin, baseline: bottomLine, align: 0, alpha: chrome)
                label("Matters", x: width - M.openMargin, baseline: bottomLine, align: 1, alpha: chrome)
                category(width: width, bottomLine: bottomLine, chrome: chrome)
                caption(width: width, height: height)
            }
            .zIndex(1001)
        }
        .allowsHitTesting(false)
    }

    @ViewBuilder
    private func card(index: Int, side: CGFloat) -> some View {
        let item = items[index]
        if let shown = frame.cards[index] {
            let photo = frame.photos[index]
            PerspectiveRolodexFace(
                tint: item.tint,
                image: photo?.image,
                imageAlpha: photo.map { min(max((frame.elapsed - $0.at) / M.imageFade, 0), 1) } ?? 0,
                shade: shown.shade,
                side: side
            )
            .opacity(shown.alpha)
            .projectionEffect(shown.projection(side: side))
            .zIndex(shown.order)
        } else {
            // Kept in the tree so a card leaving and coming back keeps its view.
            PerspectiveRolodexFace(tint: item.tint, image: nil, imageAlpha: 0, shade: 0, side: side)
                .opacity(0)
                .zIndex(-1)
        }
    }

    /// Text whose baseline sits at `baseline`, set to the left (align 0), middle (0.5) or right (1) of `x`.
    private func label(_ text: String, x: CGFloat, baseline: CGFloat, align: CGFloat, alpha: Double) -> some View {
        // Hung from a point with no size, so a label never widens the layout it sits in.
        Color.clear
            .frame(width: 0, height: 0)
            .overlay(alignment: Alignment(horizontal: align == 0 ? .leading : align == 1 ? .trailing : .center, vertical: .firstTextBaseline)) {
                Text(text)
                    .font(M.labelFont)
                    .foregroundStyle(textColor)
                    .fixedSize()
                    .opacity(alpha)
            }
            .offset(x: x, y: baseline)
    }

    // The middle word rolls up a line per card: the front card's category.
    private func category(width: CGFloat, bottomLine: CGFloat, chrome: Double) -> some View {
        let at = frame.scroll
        let front = Int((at).rounded(.toNearestOrAwayFromZero))
        let count = items.count
        return ZStack(alignment: .topLeading) {
            Color.clear.frame(width: width, height: M.line + 5)
            ForEach((front - 1)...(front + 1), id: \.self) { k in
                let d = Double(k) - at
                let fade = min(max(1 - abs(d) / 0.7, 0), 1)
                if fade > 0 {
                    label(
                        items[PerspectiveRolodexPath.wrapIndex(k, count)].category,
                        x: width / 2,
                        baseline: M.line + d * M.line,
                        align: 0.5,
                        alpha: fade * chrome
                    )
                }
            }
        }
        .frame(width: width, height: M.line + 5, alignment: .topLeading)
        .clipped()
        .offset(y: bottomLine - M.line)
    }

    private func caption(width: CGFloat, height: CGFloat) -> some View {
        let o = frame.open
        let item = items[min(max(frame.shown, 0), items.count - 1)]
        let openHeight = (width - M.openMargin * 2) * M.aspect
        let number = String(format: "%02d", frame.shown + 1)
        let bodyPad = max(0, (21 - UIFont.systemFont(ofSize: 15).lineHeight) / 2)
        return VStack(alignment: .leading, spacing: 6) {
            Text("\(number) — \(item.category)".uppercased())
                .font(.system(size: 12))
                .kerning(0.4)
                .opacity(0.6)
            Text(item.title)
                .font(.system(size: 34, weight: .light))
                .kerning(-0.6)
            Text(item.caption)
                .font(.system(size: 15))
                .padding(.vertical, bodyPad)
                .opacity(0.7)
        }
        .foregroundStyle(textColor)
        .frame(width: width - 48, alignment: .leading)
        .opacity(min(max((o - 0.55) / 0.45, 0), 1))
        .offset(x: 24, y: height * M.openAt + openHeight / 2 + 22 + (1 - min(o, 1)) * M.captionRise)
    }
}

/// The card's face, laid out flat at a fixed size: its tint, then its photo cropped to fill once it
/// arrives, then its shade. The perspective is applied to it as a whole afterwards.
private struct PerspectiveRolodexFace: View {
    let tint: Color
    let image: UIImage?
    let imageAlpha: Double
    let shade: Double
    let side: CGFloat

    var body: some View {
        let height = side * PerspectiveRolodexMetrics.aspect
        ZStack {
            tint
            if let image {
                Image(uiImage: image)
                    .resizable()
                    .interpolation(.high)
                    .antialiased(true)
                    .aspectRatio(contentMode: .fill)
                    .frame(width: side, height: height)
                    .clipped()
                    .opacity(imageAlpha)
            }
            Color.black.opacity(shade > 0.004 ? shade : 0)
        }
        .frame(width: side, height: height)
        .clipShape(RoundedRectangle(cornerRadius: PerspectiveRolodexMetrics.radius * side, style: .circular))
        // The card fades as one piece, so its tint never shows through a half-faded photo.
        .compositingGroup()
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded for every card that shows it.
enum PerspectiveRolodexPhotos {
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

// MARK: - Samples

private func perspectiveRolodexPhoto(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=760&h=706&q=82"
}

extension PerspectiveRolodexItem {
    static let samples: [PerspectiveRolodexItem] = [
        .init(title: "Swell", category: "Ocean", caption: "A wave held at the top of its curl.", photo: perspectiveRolodexPhoto("photo-1616141893496-fbc65370493e"), tint: Color(hex: 0x245d6e)),
        .init(title: "Seat No. 4", category: "Design", caption: "A black shell chair on white.", photo: perspectiveRolodexPhoto("photo-1581539250439-c96689b516dd"), tint: Color(hex: 0xdcdcdc)),
        .init(title: "Ember", category: "Colour", caption: "Red light, one face, no shadow.", photo: perspectiveRolodexPhoto("photo-1727341556787-7292f7515aec"), tint: Color(hex: 0xc42a1c)),
        .init(title: "Red Room", category: "Light", caption: "A neon cube left in the trees.", photo: perspectiveRolodexPhoto("photo-1629440951880-8c81df77e2d0"), tint: Color(hex: 0x5a1810)),
        .init(title: "Open Field", category: "Space", caption: "One cloud over a green hill.", photo: perspectiveRolodexPhoto("photo-1648219124133-8e53e8a9577c"), tint: Color(hex: 0x7da35a)),
        .init(title: "Plain Oak", category: "Craft", caption: "A bentwood chair against yellow.", photo: perspectiveRolodexPhoto("photo-1640938776314-4d303f8a1380"), tint: Color(hex: 0xd8b832)),
        .init(title: "Bloom", category: "Nature", caption: "Orange gerberas in a white jar.", photo: perspectiveRolodexPhoto("photo-1604435719097-7f73bfc6304a"), tint: Color(hex: 0x8aa3a6)),
        .init(title: "Level −2", category: "Motion", caption: "A white car under strip lights.", photo: perspectiveRolodexPhoto("photo-1611760399750-bf3b95ac8f0a"), tint: Color(hex: 0x3f3f3f)),
        .init(title: "Signal", category: "Culture", caption: "A sneaker drawn in pink neon.", photo: perspectiveRolodexPhoto("photo-1628558422453-576457df4b0d"), tint: Color(hex: 0x5b2a5c)),
        .init(title: "Flare", category: "Fashion", caption: "Red silk against a burning sky.", photo: perspectiveRolodexPhoto("photo-1767049606807-2fcc71c865f2"), tint: Color(hex: 0x7c6a6c)),
        .init(title: "Tulip", category: "Form", caption: "A white pedestal, a red seat.", photo: perspectiveRolodexPhoto("photo-1659246973603-748505504837"), tint: Color(hex: 0xe4e1dc)),
        .init(title: "Soft", category: "Beauty", caption: "Skin, close, in morning light.", photo: perspectiveRolodexPhoto("photo-1643630661247-2474f10e4f70"), tint: Color(hex: 0xcaa38c)),
        .init(title: "Noon", category: "Style", caption: "A black shirt against a yellow wall.", photo: perspectiveRolodexPhoto("photo-1680967494203-789f70ddcfdf"), tint: Color(hex: 0xf0c02a)),
        .init(title: "Midnight", category: "Detail", caption: "Dutch flowers out of the dark.", photo: perspectiveRolodexPhoto("photo-1783680599080-abe4d5634c3a"), tint: Color(hex: 0x2a2118)),
        .init(title: "High Sky", category: "Freedom", caption: "Red wool under a blue noon.", photo: perspectiveRolodexPhoto("photo-1662532577856-e8ee8b138a8b"), tint: Color(hex: 0x2d55c8)),
        .init(title: "Loop", category: "Material", caption: "A ceramic ring in sand tones.", photo: perspectiveRolodexPhoto("photo-1643569556871-91ec60671ed7"), tint: Color(hex: 0xd9d0c3)),
        .init(title: "Navy", category: "Comfort", caption: "One moulded chair, grey room.", photo: perspectiveRolodexPhoto("photo-1748887522222-ae67e761fb64"), tint: Color(hex: 0xc9ccd0)),
        .init(title: "Scarlet", category: "Feeling", caption: "A hand to the cheek, red behind.", photo: perspectiveRolodexPhoto("photo-1727341557531-343cd4364501"), tint: Color(hex: 0xb5201a)),
        .init(title: "Arcs", category: "Balance", caption: "Half circles leaning on a wall.", photo: perspectiveRolodexPhoto("photo-1620987278429-ab178d6eb547"), tint: Color(hex: 0xc9a27a)),
        .init(title: "Cut Out", category: "Shape", caption: "Black wool on a white page.", photo: perspectiveRolodexPhoto("photo-1603189343302-e603f7add05a"), tint: Color(hex: 0xefefef)),
        .init(title: "Peach", category: "Warmth", caption: "A tufted armchair, soft light.", photo: perspectiveRolodexPhoto("photo-1722357533644-9b20e356ba5f"), tint: Color(hex: 0xe8cdb2)),
        .init(title: "Eclipse", category: "Mood", caption: "A profile against blue light.", photo: perspectiveRolodexPhoto("photo-1691679769924-4430510fbc4f"), tint: Color(hex: 0x0c1733)),
    ]
}
