import SwiftUI
import UIKit

struct OrigamiFoldListSpot {
    var name: String
    var place: String
    /// Maximum depth in metres.
    var depth: Int
    /// Water temperature in °C.
    var temperature: Int
    var visibility: Int
    var current: String
    var level: String
    /// Months of the season, 0 = January.
    var season: [Int]
    /// A line under the photo strip in the last panel.
    var entry: String
    /// Unsplash photo ids: a square thumbnail and a wide strip.
    var thumb: String
    var strip: String
}

private enum OrigamiFoldListColor {
    static let ink = Color(hex: 0x071519)
    static let sea = Color(hex: 0x0f232a)
    static let paper = Color(hex: 0xf1eadc)
    static let paperInk = Color(hex: 0x0c2a31)
    static let muted = Color(hex: 0x7c9ea6)
    static let coral = Color(hex: 0xff7d5c)
}

/// A logbook of dive sites. Tapping a row unfolds its details under it like folded paper, one
/// panel after another, pushing the rows below down; tapping it again folds it back. Dragging a
/// row down opens the fold under the finger, and letting go finishes it with a spring.
struct OrigamiFoldList: View {
    var spots: [OrigamiFoldListSpot] = OrigamiFoldListSpot.samples
    var eyebrow = "Logbook · 2026"
    var title = "Where the reef\nopens up"
    /// Opens and closes a few rows on a loop of 11 seconds.
    var autoPlay = false
    /// Called with the index of the row that opens, or -1 when all are folded.
    var onOpen: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The folds live in a plain object stepped once a frame, like the RN frame callback: nothing in
    // it is observed, the timeline redraws every frame anyway.
    @State private var engine = OrigamiFoldListEngine()
    @State private var photos = OrigamiFoldListPhotos()
    @State private var headerHeight: CGFloat = 0
    // True while the touch is down; the system resets it when it takes the touch away without onEnded.
    @GestureState private var touchDown = false

    private typealias M = OrigamiFoldListMetrics
    private typealias C = OrigamiFoldListColor

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
        .task(id: spots.map(\.name)) { await loadPhotos() }
        .onAppear { engine.onOpen = { onOpen?($0) } }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let listTop = headerHeight > 0 ? Double(headerHeight) + 6 : 0
            let _ = engine.step(
                to: timeline.date,
                settings: OrigamiFoldListSettings(
                    count: spots.count,
                    autoPlay: autoPlay,
                    reduced: reduceMotion,
                    top: listTop,
                    bottom: Double(size.height - insets.bottom) - 20
                )
            )
            let _ = photos.now = engine.elapsed
            let layout = engine.layout()
            let shown = engine.shown(reduced: reduceMotion)
            ZStack(alignment: .topLeading) {
                C.ink
                header(top: insets.top)
                    .offset(y: -layout.shift)
                    .opacity((1 - M.clamp01(layout.shift / M.headerFade)) * shown)
                if listTop > 0 {
                    // The rows fade up together once, when they know where they sit.
                    ZStack(alignment: .topLeading) {
                        ForEach(spots.indices, id: \.self) { index in
                            row(index, width: max(0, size.width - M.side * 2))
                                .offset(x: M.side, y: (layout.tops.indices.contains(index) ? layout.tops[index] : listTop) - layout.shift)
                        }
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                    .opacity(shown)
                    .offset(y: reduceMotion ? 0 : (1 - shown) * M.listRise)
                }
                LinearGradient(colors: [C.ink.opacity(0), C.ink], startPoint: .top, endPoint: .bottom)
                    .frame(width: size.width, height: insets.bottom + 56)
                    .offset(y: size.height - insets.bottom - 56)
                    .allowsHitTesting(false)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touchDown) { _, state, _ in state = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touchDown) { _, now in
            // After onEnded has had its turn: a touch that did end is already over by then.
            if !now { DispatchQueue.main.async { engine.touchCancelled() } }
        }
    }

    private func header(top: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 6) {
            Text(eyebrow.uppercased())
                .font(OrigamiFoldListFont.bold(11))
                .kerning(2.2)
                .foregroundStyle(C.coral)
            // Two set lines 39 pt apart, tighter than the face's own line height.
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(title.split(separator: "\n").enumerated()), id: \.offset) { _, line in
                    Text(String(line))
                        .font(OrigamiFoldListFont.bold(34))
                        .kerning(-0.8)
                        .foregroundStyle(C.paper)
                        .frame(height: 39)
                }
            }
            .accessibilityElement(children: .combine)
            .accessibilityAddTraits(.isHeader)
            Text("Tap a site to unfold it · drag down to peek")
                .font(OrigamiFoldListFont.medium(13))
                .foregroundStyle(C.muted)
                .padding(.top, 4)
        }
        .padding(.top, top + 22)
        .padding(.horizontal, M.side + 4)
        .padding(.bottom, 18)
        .fixedSize(horizontal: false, vertical: true)
        .onGeometryChange(for: CGFloat.self) { $0.size.height } action: { headerHeight = $0 }
    }

    // MARK: A row

    private func row(_ index: Int, width: CGFloat) -> some View {
        let spot = spots[index]
        let open = engine.opens.indices.contains(index) ? engine.opens[index] : 0
        let foldWidth = max(0, width - 16)
        return ZStack(alignment: .topLeading) {
            // The folded paper hangs under the row, behind it; it is built a moment after the screen
            // opens (see `foldsAfter`).
            if index < engine.built {
                ZStack(alignment: .topLeading) {
                    ForEach(0..<M.panels, id: \.self) { k in
                        let panel = M.panel(k, open: open, reduced: reduceMotion)
                        OrigamiFoldListPanel(k: k, open: panel.open, width: foldWidth) {
                            panelContent(k, spot: spot)
                        }
                        .opacity(reduceMotion ? panel.open : panel.open > 0.002 ? 1 : 0)
                        .projectionEffect(M.hinge(width: foldWidth, top: panel.top, angle: panel.angle))
                        .accessibilityHidden(panel.open < 0.5)
                    }
                }
                .frame(width: foldWidth, height: 1, alignment: .topLeading)
                .offset(x: 8, y: M.rowHeight)
            }

            rowFace(spot, open: open, width: width)
        }
        .frame(width: width, height: M.rowHeight, alignment: .topLeading)
    }

    private func rowFace(_ spot: OrigamiFoldListSpot, open: Double, width: CGFloat) -> some View {
        HStack(spacing: 14) {
            OrigamiFoldListPhoto(image: photos.image(spot.thumb, OrigamiFoldListPhotos.thumbPx), now: photos.now)
                .frame(width: M.thumb, height: M.thumb)
                .clipShape(RoundedRectangle(cornerRadius: 15, style: .continuous))
            VStack(alignment: .leading, spacing: 2) {
                Text(spot.name)
                    .font(OrigamiFoldListFont.demiBold(17))
                    .kerning(-0.2)
                    .foregroundStyle(C.paper)
                    .lineLimit(1)
                Text(spot.place)
                    .font(OrigamiFoldListFont.medium(12.5))
                    .foregroundStyle(C.muted)
                    .lineLimit(1)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            VStack(alignment: .trailing, spacing: 0) {
                (Text("\(spot.depth)").font(OrigamiFoldListFont.bold(22)).kerning(-0.5).foregroundStyle(C.paper)
                    + Text(" m").font(OrigamiFoldListFont.demiBold(13)).foregroundStyle(C.muted))
                    .monospacedDigit()
                Text("\(spot.temperature)°C")
                    .font(OrigamiFoldListFont.demiBold(12))
                    .monospacedDigit()
                    .foregroundStyle(C.muted)
            }
            chevron(open: open)
                .padding(.trailing, 2)
        }
        .padding(.horizontal, 10)
        .frame(width: width, height: M.rowHeight)
        .background(RoundedRectangle(cornerRadius: 22, style: .continuous).fill(C.sea))
        .overlay(
            RoundedRectangle(cornerRadius: 22, style: .continuous)
                .strokeBorder(Color.rgba(160, 210, 220, 0.14), lineWidth: OrigamiFoldListFont.hairline)
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(spot.name), \(spot.place), \(spot.depth) metres, \(spot.temperature) degrees")
        .accessibilityAddTraits(.isButton)
    }

    // Two short strokes meeting in a point, turned with the fold.
    private func chevron(open: Double) -> some View {
        ZStack(alignment: .topLeading) {
            Capsule().fill(C.muted).frame(width: 8, height: 1.8).rotationEffect(.degrees(40)).offset(x: 4.6, y: 11)
            Capsule().fill(C.muted).frame(width: 8, height: 1.8).rotationEffect(.degrees(-40)).offset(x: 22 - 4.6 - 8, y: 11)
        }
        .frame(width: 22, height: 22, alignment: .topLeading)
        .rotationEffect(.degrees(M.clamp01(open) * 180))
    }

    @ViewBuilder
    private func panelContent(_ k: Int, spot: OrigamiFoldListSpot) -> some View {
        switch k {
        case 0:
            HStack(spacing: 0) {
                stat("Visibility", "\(spot.visibility) m")
                stat("Current", spot.current)
                stat("Level", spot.level)
            }
            .padding(.horizontal, 16)
            .frame(maxHeight: .infinity)
        case 1:
            VStack(alignment: .leading, spacing: 9) {
                label("Best months")
                HStack(spacing: 0) {
                    ForEach(0..<12, id: \.self) { i in
                        let on = spot.season.contains(i)
                        VStack(spacing: 5) {
                            Capsule()
                                .fill(on ? C.coral : Color.rgba(12, 42, 49, 0.14))
                                .frame(width: 14, height: 5)
                            Text(OrigamiFoldListSpot.months[i])
                                .font(OrigamiFoldListFont.demiBold(10.5))
                                .foregroundStyle(on ? C.paperInk : Color.rgba(12, 42, 49, 0.4))
                        }
                        .frame(width: 18)
                        if i < 11 { Spacer(minLength: 0) }
                    }
                }
            }
            .padding(.horizontal, 16)
            .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .leading)
        default:
            ZStack(alignment: .bottomLeading) {
                OrigamiFoldListPhoto(image: photos.image(spot.strip, OrigamiFoldListPhotos.stripPx), now: photos.now)
                LinearGradient(
                    stops: [
                        .init(color: Color.rgba(4, 14, 18, 0), location: 0.25),
                        .init(color: Color.rgba(4, 14, 18, 0.72), location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )
                Text(spot.entry)
                    .font(OrigamiFoldListFont.demiBold(13))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(.horizontal, 16)
                    .padding(.bottom, 12)
            }
        }
    }

    private func label(_ text: String) -> some View {
        Text(text.uppercased())
            .font(OrigamiFoldListFont.bold(9.5))
            .kerning(1.4)
            .foregroundStyle(Color.rgba(12, 42, 49, 0.55))
    }

    private func stat(_ name: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            label(name)
            Text(value)
                .font(OrigamiFoldListFont.demiBold(15))
                .foregroundStyle(C.paperInk)
                .lineLimit(1)
        }
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func loadPhotos() async {
        await withTaskGroup(of: Void.self) { group in
            for spot in spots {
                group.addTask { await photos.load(spot.thumb, OrigamiFoldListPhotos.thumbPx) }
                group.addTask { await photos.load(spot.strip, OrigamiFoldListPhotos.stripPx) }
            }
        }
    }
}

/// One panel of paper: its content, the fold's shadow over it and the crease along its top.
private struct OrigamiFoldListPanel<Content: View>: View {
    let k: Int
    let open: Double
    let width: CGFloat
    @ViewBuilder let content: Content

    var body: some View {
        let last = k == OrigamiFoldListMetrics.panels - 1
        let height = CGFloat(OrigamiFoldListMetrics.panelHeight)
        ZStack(alignment: .top) {
            OrigamiFoldListColor.paper
            content
            // The fold's shadow: deepest along the crease the panel turns about, gone once it is flat.
            LinearGradient(
                colors: k % 2 == 0
                    ? [Color.rgba(4, 14, 18, 0.25), Color.rgba(4, 14, 18, 1)]
                    : [Color.rgba(4, 14, 18, 1), Color.rgba(4, 14, 18, 0.35)],
                startPoint: .top,
                endPoint: .bottom
            )
            .opacity(OrigamiFoldListMetrics.shade * (1 - open))
            if k > 0 {
                Color.rgba(12, 42, 49, 0.28).frame(height: OrigamiFoldListFont.hairline)
            }
        }
        .frame(width: width, height: height)
        .clipShape(UnevenRoundedRectangle(bottomLeadingRadius: last ? 18 : 0, bottomTrailingRadius: last ? 18 : 0, style: .continuous))
    }
}

/// A photo cropped to fill its frame, fading in over 200 ms when it arrives.
private struct OrigamiFoldListPhoto: View {
    let image: OrigamiFoldListPhotos.Arrived?
    let now: Double

    var body: some View {
        GeometryReader { geo in
            if let image {
                Image(uiImage: image.image)
                    .resizable()
                    .interpolation(.high)
                    .aspectRatio(contentMode: .fill)
                    .frame(width: geo.size.width, height: geo.size.height)
                    .clipped()
                    .opacity(OrigamiFoldListMetrics.clamp01((now - image.at) / 0.2))
            }
        }
    }
}

enum OrigamiFoldListFont {
    static func medium(_ size: CGFloat) -> Font { .custom("AvenirNext-Medium", fixedSize: size) }
    static func demiBold(_ size: CGFloat) -> Font { .custom("AvenirNext-DemiBold", fixedSize: size) }
    static func bold(_ size: CGFloat) -> Font { .custom("AvenirNext-Bold", fixedSize: size) }
    static var hairline: CGFloat { 1 / max(1, UITraitCollection.current.displayScale) }
}

// MARK: - Photos

/// The thumbnails and strips, downloaded once and kept decoded.
@MainActor
final class OrigamiFoldListPhotos {
    static let thumbPx = 200
    static let stripPx = 900

    struct Arrived {
        var image: UIImage
        /// The engine's clock when it arrived, for its fade.
        var at: Double
    }

    private var images: [String: Arrived] = [:]
    private var loading: Set<String> = []
    /// The engine's clock, kept up to date by the view.
    var now = 0.0

    func image(_ id: String, _ width: Int) -> Arrived? { images["\(id)@\(width)"] }

    func load(_ id: String, _ width: Int) async {
        let key = "\(id)@\(width)"
        guard !loading.contains(key),
              let url = URL(string: "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(width)&q=80")
        else { return }
        loading.insert(key)
        guard let (data, _) = try? await URLSession.shared.data(from: url), let image = UIImage(data: data) else { return }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        images[key] = Arrived(image: ready, at: now)
    }
}

// MARK: - Samples

extension OrigamiFoldListSpot {
    static let months = ["J", "F", "M", "A", "M", "J", "J", "A", "S", "O", "N", "D"]

    static let samples: [OrigamiFoldListSpot] = [
        .init(name: "Blue Corner", place: "Koror · Palau", depth: 30, temperature: 28, visibility: 30, current: "Strong drift", level: "Advanced", season: [11, 0, 1, 2, 3], entry: "Reef hook on the wall · 45 min by boat", thumb: "photo-1583212292454-1fe6229603b7", strip: "photo-1494879540385-bc170b0878a7"),
        .init(name: "Barracuda Point", place: "Sipadan · Malaysia", depth: 25, temperature: 29, visibility: 20, current: "Moderate", level: "Intermediate", season: [3, 4, 5, 6, 7, 8, 9, 10], entry: "Negative entry off the drop-off · 10 min out", thumb: "photo-1637308107080-82cf40953742", strip: "photo-1507295386538-ddd5e86cd597"),
        .init(name: "Tubbataha North", place: "Sulu Sea · Philippines", depth: 40, temperature: 27, visibility: 35, current: "Mild", level: "Advanced", season: [2, 3, 4, 5], entry: "Liveaboard only · twelve hours offshore", thumb: "photo-1514907283155-ea5f4094c70c", strip: "photo-1508624217470-5ef0f947d8be"),
        .init(name: "Crystal Bay", place: "Nusa Penida · Indonesia", depth: 18, temperature: 22, visibility: 25, current: "Surge", level: "Intermediate", season: [6, 7, 8, 9], entry: "Sheltered cove · cold upwellings bring the sunfish", thumb: "photo-1701673505069-45a4078044ba", strip: "photo-1517219039361-66f283bce5db"),
        .init(name: "Ras Mohammed", place: "Red Sea · Egypt", depth: 35, temperature: 25, visibility: 30, current: "Mild drift", level: "Advanced", season: [3, 4, 5, 6, 7, 8, 9, 10], entry: "Shark and Yolanda reefs · day boat from the marina", thumb: "photo-1637308109237-4ea1a7dd22f5", strip: "photo-1470217407524-b1e77afc6ec5"),
        .init(name: "Cod Hole", place: "Coral Sea · Australia", depth: 24, temperature: 26, visibility: 30, current: "Gentle", level: "Beginner", season: [5, 6, 7, 8, 9, 10], entry: "Ribbon reefs · potato cod come to say hello", thumb: "photo-1694661641480-d4e76d6bead3", strip: "photo-1466094899371-97b327dff551"),
    ]
}
