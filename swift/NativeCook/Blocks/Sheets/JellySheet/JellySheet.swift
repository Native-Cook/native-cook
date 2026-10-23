import SwiftUI
import UIKit

/// A hotel's page: a tall photo with a booking sheet over it. The sheet's top edge is jelly — it
/// bulges towards the finger while dragged and wobbles when let go — and it rests at three heights:
/// a peek with the hotel's name, half with the stay, full with the rooms. Fully open, the photo
/// behind blurs and sinks back.
struct JellySheet: View {
    /// Drags the sheet up to half, to full, and back down to the peek, over and over.
    var autoPlay = false
    /// Called with the detent the sheet settles on: 0 peek, 1 half, 2 full.
    var onDetentChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The sheet's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = JellySheetEngine()

    private typealias M = JellySheetMetrics

    var body: some View {
        // The block draws under the system bars and keeps its controls clear of them itself.
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
        .task { await loadPhotos() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let stage = JellySheetStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom)
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, stage: stage, autoPlay: autoPlay, reducedMotion: reduceMotion)
            JellySheetScene(frame: frame, stage: stage, engine: engine, reducedMotion: reduceMotion)
        }
        .frame(width: size.width, height: size.height)
        .background(Color.black)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onAppear { engine.onDetentChange = onDetentChange }
        .accessibilityElement(children: .contain)
        .accessibilityLabel("Booking details for Casa Albera")
        .accessibilityHint("Drag up for the rooms, down to close")
    }

    private func loadPhotos() async {
        let addresses = [JellySheetContent.hero] + JellySheetContent.rooms.map(\.photo)
        await withTaskGroup(of: (String, UIImage?).self) { group in
            for address in addresses {
                group.addTask { (address, await JellySheetPhotos.load(address)) }
            }
            for await (address, image) in group {
                if let image { engine.photoArrived(image, for: address) }
            }
        }
    }
}

// MARK: - Colours and content

/// A warm paper sheet, one terracotta accent.
enum JellySheetPalette {
    static let sheet = Color(hex: 0xf7f3ee)
    static let ink = Color(hex: 0x1d1b18)
    static let muted = Color(hex: 0x736d65)
    static let line = Color(hex: 0xe5ded4)
    static let tile = Color(hex: 0xeee7dd)
    static let accent = Color(hex: 0xb4532a)
    static let grabber = Color(hex: 0xcfc5b8)
}

private func jellySheetUnsplash(_ id: String, _ w: Int, _ h: Int) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(w)&h=\(h)&q=80"
}

enum JellySheetContent {
    static let hero = jellySheetUnsplash("photo-1549294413-26f195200c16", 1100, 2300)
    static let rooms: [(name: String, price: String, photo: String)] = [
        ("Pool Villa", "€312", jellySheetUnsplash("photo-1562778612-e1e0cda9915c", 330, 380)),
        ("Garden Suite", "€248", jellySheetUnsplash("photo-1587870306141-4f19861e6c73", 330, 380)),
        ("Sea Loft", "€286", jellySheetUnsplash("photo-1586611292717-f828b167408c", 330, 380)),
    ]
    static let amenities: [(label: String, symbol: String)] = [
        ("Pool", "figure.pool.swim"),
        ("Breakfast", "cup.and.saucer"),
        ("Wi-Fi", "wifi"),
        ("Bar", "wineglass"),
    ]
}

// MARK: - The scene

/// Everything one frame draws, back to front: the photo, its buttons, the sheet's jelly outline,
/// what the sheet holds, and the footer.
private struct JellySheetScene: View {
    let frame: JellySheetFrame
    let stage: JellySheetStage
    let engine: JellySheetEngine
    let reducedMotion: Bool

    private typealias M = JellySheetMetrics
    private typealias P = JellySheetPalette

    var body: some View {
        let width = stage.width
        let height = stage.height
        let d = frame.detents
        // 0 at half or below, 1 fully open.
        let open = min(max((d[1] - frame.y) / (d[1] - d[2]), 0), 1)
        let footer = M.footer + stage.bottom

        ZStack(alignment: .topLeading) {
            photo(open: open)

            topBar
                .frame(width: width - 32)
                .offset(x: 16, y: stage.top + 6)
                .opacity(max(0, 1 - open * 1.6))

            counter
                .frame(width: width - 16, alignment: .trailing)
                .offset(y: frame.y - 46)
                .opacity(min(max((frame.y - d[1]) / (d[0] - d[1]), 0), 1))

            outline(width: width, height: height)

            JellySheetBody(engine: engine, elapsed: frame.elapsed)
                .frame(width: width, alignment: .topLeading)
                .offset(y: frame.y - min(0, M.liftAt(frame.lift, width / 2, width)) * M.contentGive)

            footerBar
                .frame(width: width, height: footer, alignment: .top)
                .offset(y: height - footer)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    // The photo, which scales back, rounds its corners, drops a little and blurs as the sheet opens.
    private func photo(open o: Double) -> some View {
        let hero = engine.photo(JellySheetContent.hero)
        let fade = hero.map { min(max((frame.elapsed - $0.at) / M.imageFade, 0), 1) } ?? 0
        return ZStack(alignment: .top) {
            Color(hex: 0x1d2a2c)
            if let hero {
                Image(uiImage: hero.image)
                    .resizable()
                    .aspectRatio(contentMode: .fill)
                    .frame(width: stage.width, height: stage.height)
                    .clipped()
                    .opacity(fade)
            }
            LinearGradient(colors: [.black.opacity(0.38), .black.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(height: stage.top + 110)
            // The dark blur crossfades in as the sheet opens fully.
            if o > 0.001 {
                ZStack {
                    if let hero {
                        Image(uiImage: hero.image)
                            .resizable()
                            .aspectRatio(contentMode: .fill)
                            .frame(width: stage.width, height: stage.height)
                            .blur(radius: 14, opaque: true)
                            .clipped()
                            .opacity(fade)
                    }
                    Color.black.opacity(0.42)
                }
                .opacity(o)
            }
        }
        .frame(width: stage.width, height: stage.height)
        .clipShape(RoundedRectangle(cornerRadius: o * M.photoRadius, style: .continuous))
        .scaleEffect(reducedMotion ? 1 : 1 - (1 - M.photoScale) * o)
        .offset(y: reducedMotion ? 0 : o * stage.top * 0.4)
    }

    private var topBar: some View {
        HStack {
            JellySheetGlass(symbol: "chevron.left")
            Spacer()
            HStack(spacing: 10) {
                JellySheetGlass(symbol: "square.and.arrow.up")
                JellySheetGlass(symbol: "heart")
            }
        }
    }

    private var counter: some View {
        HStack(spacing: 6) {
            JellySheetSymbol(name: "photo.on.rectangle", size: 13, color: .white)
            Text("1 / 24")
                .font(.system(size: 12.5, weight: .semibold).monospacedDigit())
                .foregroundStyle(.white)
        }
        .padding(.horizontal, 11)
        .frame(height: 28)
        .background(Capsule().fill(Color.black.opacity(0.38)))
    }

    // The sheet's top: the corners pinned and round, the edge between them riding the membrane,
    // with a soft shadow; the grabber rides the edge in the middle, tilting and stretching with it.
    private func outline(width: Double, height: Double) -> some View {
        let top = frame.y
        let lift = frame.lift
        return Canvas { context, _ in
            guard width > 0, top.isFinite else { return }
            func edge(_ x: Double) -> Double { top - M.liftAt(lift, x, width) }
            var path = Path()
            path.move(to: CGPoint(x: 0, y: top + M.corner))
            path.addQuadCurve(to: CGPoint(x: M.corner, y: edge(M.corner)), control: CGPoint(x: 0, y: top))
            for k in 1...M.samples {
                let x = M.corner + (width - 2 * M.corner) * Double(k) / Double(M.samples)
                path.addLine(to: CGPoint(x: x, y: edge(x)))
            }
            path.addQuadCurve(to: CGPoint(x: width, y: top + M.corner), control: CGPoint(x: width, y: top))
            path.addLine(to: CGPoint(x: width, y: height + 80))
            path.addLine(to: CGPoint(x: 0, y: height + 80))
            path.closeSubpath()

            var shadowed = context
            shadowed.addFilter(.shadow(color: .black.opacity(0.28), radius: 22, x: 0, y: -4))
            shadowed.fill(path, with: .color(P.sheet))

            let mid = width / 2
            let rise = M.liftAt(lift, mid, width)
            let slope = (M.liftAt(lift, mid + 12, width) - M.liftAt(lift, mid - 12, width)) / 24
            let w = M.grabW + min(M.grabStretch, abs(slope) * 60 + abs(rise) * 0.12)
            var grab = context
            grab.translateBy(x: mid, y: top - rise + 11.5)
            grab.rotate(by: .radians(-atan(slope)))
            grab.fill(
                Path(roundedRect: CGRect(x: -w / 2, y: -2.5, width: w, height: 5), cornerRadius: 2.5),
                with: .color(P.grabber)
            )
        }
        .frame(width: width, height: height)
        .allowsHitTesting(false)
    }

    private var footerBar: some View {
        HStack {
            VStack(alignment: .leading, spacing: 2) {
                (Text("€248")
                    .font(.system(size: 22, weight: .bold).monospacedDigit())
                    .kerning(-0.4)
                    .foregroundStyle(P.ink)
                 + Text(" / night")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(P.muted))
                Text("12 – 16 May · 2 guests")
                    .font(.system(size: 12.5))
                    .foregroundStyle(P.muted)
            }
            Spacer()
            Text("Reserve")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(.white)
                .padding(.horizontal, 34)
                .frame(height: 52)
                .background(Capsule().fill(P.accent))
        }
        .padding(.horizontal, 20)
        .frame(maxHeight: .infinity)
        .padding(.bottom, stage.bottom)
        .background(P.sheet)
        .overlay(alignment: .top) {
            Rectangle().fill(P.line).frame(height: 1 / UIScreen.main.scale)
        }
    }
}

/// A round frosted button on the photo.
private struct JellySheetGlass: View {
    let symbol: String

    var body: some View {
        ZStack {
            // A light blur at about a third of its strength, as expo-blur's intensity 30.
            Circle().fill(.ultraThinMaterial).environment(\.colorScheme, .light).opacity(0.45)
            Circle().fill(Color.white.opacity(0.12))
            JellySheetSymbol(name: symbol, size: 17, color: .white, weight: .semibold)
        }
        .frame(width: 42, height: 42)
    }
}

/// An SF Symbol fitted into a square of `size` points, like `SymbolView` in the RN version.
private struct JellySheetSymbol: View {
    let name: String
    let size: CGFloat
    let color: Color
    var weight: Font.Weight = .regular

    var body: some View {
        Image(systemName: name)
            .resizable()
            .fontWeight(weight)
            .aspectRatio(contentMode: .fit)
            .foregroundStyle(color)
            .frame(width: size, height: size)
    }
}

// MARK: - The sheet's content

/// What the sheet holds, top to bottom: the hotel, the stay, the rooms, what's included.
private struct JellySheetBody: View {
    let engine: JellySheetEngine
    let elapsed: Double

    private typealias P = JellySheetPalette

    var body: some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 12) {
                Text("Casa Albera")
                    .font(.system(size: 29, weight: .bold))
                    .kerning(-0.7)
                    .foregroundStyle(P.ink)
                    .lineLimit(1)
                    .frame(height: 34)
                Spacer(minLength: 0)
                HStack(spacing: 4) {
                    JellySheetSymbol(name: "star.fill", size: 11, color: P.ink)
                    Text("4.92")
                        .font(.system(size: 13.5, weight: .semibold).monospacedDigit())
                        .foregroundStyle(P.ink)
                }
                .padding(.horizontal, 10)
                .frame(height: 28)
                .background(Capsule().fill(P.tile))
            }
            HStack(spacing: 5) {
                JellySheetSymbol(name: "mappin.and.ellipse", size: 13, color: P.muted)
                Text("Praia do Norte, Comporta · 214 reviews")
                    .font(.system(size: 14.5))
                    .foregroundStyle(P.muted)
            }
            .padding(.top, 6)

            label("Your stay")
            HStack(spacing: 0) {
                stayCell("Check-in", "Tue 12 May")
                divider
                stayCell("Check-out", "Sat 16 May")
                divider
                stayCell("Guests", "2 adults")
            }
            .frame(height: 64)
            .overlay(RoundedRectangle(cornerRadius: 16, style: .continuous).strokeBorder(P.line, lineWidth: 1))

            label("Rooms")
            HStack(alignment: .top, spacing: 10) {
                ForEach(JellySheetContent.rooms, id: \.name) { room in
                    VStack(alignment: .leading, spacing: 3) {
                        roomPhoto(room.photo)
                            .padding(.bottom, 5)
                        Text(room.name)
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(P.ink)
                            .lineLimit(1)
                        Text("from \(room.price)")
                            .font(.system(size: 12.5).monospacedDigit())
                            .foregroundStyle(P.muted)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                }
            }

            label("Included")
            HStack {
                ForEach(Array(JellySheetContent.amenities.enumerated()), id: \.offset) { k, item in
                    if k > 0 { Spacer(minLength: 0) }
                    VStack(spacing: 6) {
                        JellySheetSymbol(name: item.symbol, size: 19, color: P.ink)
                            .frame(width: 46, height: 46)
                            .background(Circle().fill(P.tile))
                        Text(item.label)
                            .font(.system(size: 12, weight: .medium))
                            .foregroundStyle(P.ink)
                    }
                    .frame(width: 72)
                }
            }

            Text("Twelve whitewashed rooms between the rice fields and the dunes, a ten-minute walk from the sea.")
                .font(.system(size: 14.5))
                .lineSpacing(21 - UIFont.systemFont(ofSize: 14.5).lineHeight)
                .foregroundStyle(P.muted)
                .padding(.top, 20)
                .fixedSize(horizontal: false, vertical: true)
        }
        .padding(.top, 30)
        .padding(.horizontal, 20)
    }

    private func label(_ text: String) -> some View {
        Text(text.uppercased())
            .font(.system(size: 12, weight: .semibold))
            .kerning(1.1)
            .foregroundStyle(P.muted)
            .padding(.top, 24)
            .padding(.bottom, 10)
    }

    private var divider: some View {
        Rectangle().fill(P.line).frame(width: 1, height: 34)
    }

    private func stayCell(_ label: String, _ value: String) -> some View {
        VStack(alignment: .leading, spacing: 3) {
            Text(label)
                .font(.system(size: 12))
                .foregroundStyle(P.muted)
            Text(value)
                .font(.system(size: 15, weight: .semibold))
                .kerning(-0.2)
                .foregroundStyle(P.ink)
                .lineLimit(1)
        }
        .padding(.horizontal, 14)
        .frame(maxWidth: .infinity, alignment: .leading)
    }

    private func roomPhoto(_ address: String) -> some View {
        let shown = engine.photo(address)
        return Color.clear
            .frame(height: 116)
            .frame(maxWidth: .infinity)
            .background(P.tile)
            .overlay {
                if let shown {
                    Image(uiImage: shown.image)
                        .resizable()
                        .aspectRatio(contentMode: .fill)
                        .opacity(min(max((elapsed - shown.at) / 0.3, 0), 1))
                }
            }
            .clipShape(RoundedRectangle(cornerRadius: 14, style: .continuous))
    }
}
