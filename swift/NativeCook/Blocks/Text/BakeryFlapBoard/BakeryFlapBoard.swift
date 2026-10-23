import SwiftUI
import UIKit

/// A split-flap menu board in a small bakery: a walnut case with brass edges under a drifting strip
/// of bakery photos. Two lines tell the time in words, four list what is out of the oven; when a line
/// changes, every flap falls through the alphabet to its new letter with the real two-half flip, a
/// column at a time, and lands with a little bounce. Tapping a line flips it to its next item and
/// grows that line into a cream card with the item's photo and price.
struct BakeryFlapBoard: View {
    /// One list of items per menu line; tapping a line shows the next item of its list.
    var menu: [[BakeryFlapBoardItem]] = BakeryFlapBoardItem.menu
    /// Photos (Unsplash ids) in the strip across the top.
    var strip: [String] = BakeryFlapBoardItem.strip
    /// The two lines that stand in for the time now and then.
    var notice = ["SECOND BAKE", "AT HALF NINE"]
    /// The bakery's name over the strip.
    var name = "ROSIER BAKEHOUSE"
    /// Changes lines on a schedule, opens one card and puts everything back, on a loop.
    var autoPlay = false
    /// Called with a menu item when its card opens.
    var onOpen: ((BakeryFlapBoardItem) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = BakeryFlapBoardEngine()
    @State private var photos = BakeryFlapBoardPhotos()
    // Resets when the system cancels the touch, which never reaches onEnded.
    @GestureState private var fingerDown = false

    private typealias M = BakeryFlapBoardMetrics
    private typealias X = BakeryFlapBoardMath

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing, height: geo.size.height + insets.top + insets.bottom)
            let room = BakeryFlapBoardStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
            screen(room)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task(id: menu.flatMap { $0.map(\.photo) } + strip) { await loadPhotos() }
        .onAppear { wire() }
    }

    private func screen(_ room: BakeryFlapBoardStage) -> some View {
        let layout = BakeryFlapBoardLayout(room, rowCount: M.timeRows + menu.count)
        let labels = ["THE TIME", "TODAY'S BAKE"]
        return ZStack(alignment: .topLeading) {
            Rectangle()
                .colorEffect(ShaderLibrary.bakeryFlapBoardWood(.float2(Float(room.width), Float(room.height))))
            // Everything under the letters that never moves, drawn once.
            Canvas { context, _ in BakeryFlapBoardPainter.under(context, layout, labels: labels) }
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, stage: room, autoPlay: autoPlay, reducedMotion: reduceMotion)
                ZStack(alignment: .topLeading) {
                    Canvas { context, _ in BakeryFlapBoardPainter.flaps(context, frame, layout) }
                    Canvas { context, _ in BakeryFlapBoardPainter.over(context, layout) }
                    header(room)
                    photoStrip(room, now: frame.now, reducedMotion: frame.reducedMotion)
                    hint(room)
                    Color.black.opacity(0.62 * X.clamp01(frame.cardOpen))
                    card(room, layout: layout, frame: frame)
                }
            }
        }
        .frame(width: room.width, height: room.height)
        .background(Color(hex: M.background))
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
        .accessibilityLabel("Split-flap menu board. Tap a line to flip it to the next item and open its card.")
    }

    private func wire() {
        engine.menu = menu
        engine.noticeLines = notice.map(BakeryFlapBoardText.centred)
        engine.onTick = { UISelectionFeedbackGenerator().selectionChanged() }
        engine.onSoft = { UIImpactFeedbackGenerator(style: .soft).impactOccurred() }
        let onOpen = onOpen
        engine.onOpen = { onOpen?($0) }
    }

    // The strip photos and every card's photo are fetched up front, so a card never opens empty.
    private func loadPhotos() async {
        let photos = photos
        let engine = engine
        await withTaskGroup(of: Void.self) { group in
            for id in strip {
                group.addTask { await photos.load(bakeryFlapBoardPhoto(id, width: 340), now: { await engine.elapsed }) }
            }
            for item in menu.flatMap({ $0 }) {
                group.addTask { await photos.load(bakeryFlapBoardCardPhoto(item), now: { await engine.elapsed }) }
            }
        }
    }

    // MARK: Chrome

    private static let condensedBold = "AvenirNextCondensed-Bold"
    private static let condensedDemi = "AvenirNextCondensed-DemiBold"

    private func header(_ room: BakeryFlapBoardStage) -> some View {
        HStack(alignment: .firstTextBaseline) {
            Text(name)
                .font(.custom(Self.condensedBold, fixedSize: 14))
                .kerning(3.4)
                .foregroundStyle(Color(hex: M.brass))
            Spacer(minLength: 0)
            Text("OPEN 7 — 4")
                .font(.custom(Self.condensedDemi, fixedSize: 11))
                .kerning(2)
                .foregroundStyle(Color(hex: M.brass))
                .opacity(0.7)
        }
        .padding(.horizontal, M.caseInset + 4)
        .frame(width: room.width, alignment: .leading)
        .offset(y: room.top + 6)
        .allowsHitTesting(false)
    }

    private func photoStrip(_ room: BakeryFlapBoardStage, now: Double, reducedMotion: Bool) -> some View {
        // The strip drifts one set of photos to the left per loop.
        let setWidth = Double(strip.count) * (M.tileW + M.tileGap)
        let drift = reducedMotion ? 0 : -(now.truncatingRemainder(dividingBy: M.loop) / M.loop) * setWidth
        return HStack(spacing: M.tileGap) {
            ForEach(0..<(strip.count * 3), id: \.self) { k in
                tile(strip[k % strip.count], now: now)
            }
        }
        .padding(.leading, M.caseInset)
        .offset(x: drift)
        .frame(width: room.width, height: M.tileH, alignment: .leading)
        .clipped()
        .offset(y: room.top + 32)
        .allowsHitTesting(false)
    }

    private func tile(_ id: String, now: Double) -> some View {
        let shape = RoundedRectangle(cornerRadius: 10, style: .continuous)
        let loaded = photos.photo(bakeryFlapBoardPhoto(id, width: 340))
        return ZStack {
            Color(hex: 0x2c1c12)
            if let loaded {
                Image(uiImage: loaded.image)
                    .resizable()
                    .scaledToFill()
                    .frame(width: M.tileW, height: M.tileH)
                    .opacity(X.clamp01((now - loaded.at) / 0.3))
            }
        }
        .frame(width: M.tileW, height: M.tileH)
        .clipShape(shape)
        .overlay(shape.strokeBorder(Color.rgba(230, 190, 120, 0.35), lineWidth: 1 / 3))
    }

    private func hint(_ room: BakeryFlapBoardStage) -> some View {
        Text("Tap a line for its card")
            .font(.custom(Self.condensedDemi, fixedSize: 12))
            .kerning(1.6)
            .foregroundStyle(Color(hex: M.brass))
            .opacity(0.75)
            .frame(width: room.width, height: max(0, room.height - room.bottom - 18), alignment: .bottom)
            .allowsHitTesting(false)
    }

    // MARK: Card

    private func card(_ room: BakeryFlapBoardStage, layout: BakeryFlapBoardLayout, frame: BakeryFlapBoardFrame) -> some View {
        let to = BakeryFlapBoardLayout.card(room)
        let row = frame.cardRow
        let o = max(0, frame.cardOpen)
        let t = frame.reducedMotion ? 1 : o
        let fromW = layout.w - M.pad * 2 + 8
        let fromH = layout.cellH + 8
        let fromX = layout.left - 4
        let fromY = row >= 0 ? layout.rows[row] - 4 : to.minY
        // The card lifts out of its line as a cream strip and grows; it fades in over the first
        // cardFade of the way so it never sits over the line as a flat block.
        let opacity = row < 0 ? 0 : frame.reducedMotion ? X.clamp01(o) : X.clamp01(o / M.cardFade)
        let w = row < 0 ? to.width : X.mix(fromW, to.width, t)
        let h = row < 0 ? to.height : X.mix(fromH, to.height, t)
        let x = row < 0 ? to.minX : X.mix(fromX, to.minX, t)
        let y = row < 0 ? to.minY : X.mix(fromY, to.minY, t)
        let radius = row < 0 ? 0 : X.mix(5, 24, X.clamp01(t))
        let shown = X.clamp01((o - 0.45) / 0.4)
        let item = frame.cardItem ?? menu[0][0]
        // The content stays centred in the growing card, so the card reveals it rather than squeezing it.
        return ZStack {
            cardContent(item, width: to.width, height: to.height, now: frame.now)
                .opacity(shown)
                .offset(y: frame.reducedMotion ? 0 : (1 - shown) * 14)
        }
        .frame(width: max(0, w), height: max(0, h))
        .background(Color(hex: M.cream))
        .clipShape(RoundedRectangle(cornerRadius: radius, style: .continuous))
        .opacity(opacity)
        .offset(x: x, y: y)
        .allowsHitTesting(false)
    }

    private func cardContent(_ item: BakeryFlapBoardItem, width: Double, height: Double, now: Double) -> some View {
        let photoH = width * M.cardPhoto
        let loaded = photos.photo(bakeryFlapBoardCardPhoto(item))
        let framed = bakeryFlapBoardFrame(item, width: width, height: photoH)
        let cocoa = Color(hex: M.cocoa)
        return VStack(alignment: .leading, spacing: 0) {
            ZStack {
                if let loaded {
                    // Framed on its subject: sized and placed by hand rather than centred by a fill.
                    Image(uiImage: loaded.image)
                        .resizable()
                        .frame(width: framed.width, height: framed.height)
                        .offset(x: framed.minX, y: framed.minY)
                        .opacity(X.clamp01((now - loaded.at) / 0.25))
                }
            }
            .frame(width: width, height: photoH, alignment: .topLeading)
            .clipped()
            VStack(alignment: .leading, spacing: 8) {
                HStack(alignment: .firstTextBaseline) {
                    Text(item.name)
                        .font(.custom("Georgia", fixedSize: 30))
                        .foregroundStyle(cocoa)
                        .lineLimit(1)
                    Spacer(minLength: 8)
                    Text(item.price)
                        .font(.custom(Self.condensedBold, fixedSize: 28).monospacedDigit())
                        .foregroundStyle(Color(hex: 0xa8722b))
                }
                Text(item.note)
                    .font(.custom("Georgia-Italic", fixedSize: 15))
                    .lineSpacing(4)
                    .foregroundStyle(cocoa.opacity(0.78))
                    .lineLimit(2)
                    .fixedSize(horizontal: false, vertical: true)
                    // A 21 pt line, as the React Native version sets it.
                    .padding(.vertical, 2)
                Rectangle()
                    .fill(cocoa.opacity(0.25))
                    .frame(height: 1 / 3)
                    .padding(.top, 6)
                HStack {
                    meta(item.baked == "ALL DAY" ? "POURED ALL DAY" : "OUT OF THE OVEN \(item.baked)")
                    Spacer(minLength: 8)
                    meta("DRAG DOWN TO CLOSE")
                }
            }
            .padding(.horizontal, 20)
            .padding(.top, 18)
        }
        .frame(width: width, height: height, alignment: .top)
    }

    private func meta(_ text: String) -> some View {
        Text(text)
            .font(.custom(Self.condensedDemi, fixedSize: 11))
            .kerning(1.4)
            .foregroundStyle(Color(hex: M.cocoa))
            .opacity(0.6)
    }
}

// MARK: - Photos

/// Downloads each photo once and remembers when it arrived (on the board's clock), so it can fade in.
@MainActor
final class BakeryFlapBoardPhotos {
    struct Loaded {
        let image: UIImage
        let at: Double
    }

    private var loaded: [String: Loaded] = [:]

    func photo(_ address: String) -> Loaded? { loaded[address] }

    nonisolated func load(_ address: String, now: @escaping () async -> Double) async {
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return }
        // Decoded off the main thread, so the first frame that draws it does not stall.
        let ready = await image.byPreparingForDisplay() ?? image
        let at = await now()
        await MainActor.run { self.loaded[address] = Loaded(image: ready, at: at) }
    }
}
