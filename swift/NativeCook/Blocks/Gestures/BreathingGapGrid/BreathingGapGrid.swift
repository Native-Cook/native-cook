import SwiftUI
import UIKit

/// A wall of square tiles in four drifting columns, photos mixed with set type. Pinching opens the
/// gap between the tiles: the wall zooms out to eight whole columns, and the rows part around the
/// middle for a line of text that arrives a letter at a time; a release settles on closed or open.
/// The wall can be dragged, and tapping a tile lifts it out over the blurred wall with its title;
/// the open tile follows the finger, and a pull down puts it back in its cell.
struct BreathingGapGrid: View {
    /// What the columns are filled with, repeated as far as they run.
    var tiles: [BreathingGapGridTile] = BreathingGapGridTile.samples
    /// The line that opens up between the tiles once the gap is wide.
    var caption = "Room to breathe"
    /// Opens the gap, closes it, opens a tile, closes it and starts over, while the columns drift.
    var autoPlay = false
    var background: UInt32 = 0x000000
    var textColor: UInt32 = 0xf2f1ec
    /// Called with a tile when it is opened.
    var onOpen: ((BreathingGapGridTile) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The wall's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = BreathingGapGridEngine()

    private typealias M = BreathingGapGridMetrics

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
        .task(id: tiles.count) { await loadPhotos() }
    }

    private var settings: BreathingGapGridSettings {
        BreathingGapGridSettings(tiles: tiles, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, onOpen: onOpen)
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        return TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, width: size.width, height: size.height, settings: settings)
            BreathingGapGridScene(
                frame: frame,
                tiles: tiles,
                caption: caption,
                background: background,
                textColor: textColor,
                insets: insets,
                reducedMotion: reduceMotion
            )
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: background))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            // One recogniser stands in for the race of a one-finger pan (after 6 pt of travel) and a
            // tap (a touch lifted within 400 ms that never travelled that far).
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0, settings: settings) }
                .onEnded { engine.touchEnded($0, settings: settings) }
        )
        .simultaneousGesture(
            MagnifyGesture()
                .onChanged { engine.pinchChanged(scale: $0.magnification, settings: settings) }
                .onEnded { engine.pinchEnded(scale: $0.magnification, velocity: $0.velocity, settings: settings) }
        )
        .accessibilityElement()
        .accessibilityLabel(caption)
    }

    // Each photo arrives on its own; until then its tile shows its tint.
    private func loadPhotos() async {
        engine.resetPhotos(count: tiles.count)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, tile) in tiles.enumerated() {
                guard case let .photo(uri, _, _) = tile.kind else { continue }
                group.addTask { (index, await BreathingGapGridPhotos.load(uri)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

/// Everything one frame draws: the wall, the open tile over it, the edges and the labels.
private struct BreathingGapGridScene: View {
    let frame: BreathingGapGridFrame
    let tiles: [BreathingGapGridTile]
    let caption: String
    let background: UInt32
    let textColor: UInt32
    let insets: EdgeInsets
    let reducedMotion: Bool

    private typealias M = BreathingGapGridMetrics
    private typealias L = BreathingGapGridLayout

    var body: some View {
        let l = frame.layout
        let width = l.width
        let height = l.height
        let o = frame.open
        let lifted = min(max(o, 0), 1)
        // How far the open tile has been pulled away, 0 … 1.
        let away = min(max((max(0, frame.dragY) + abs(frame.dragX) * 0.4) / (height * M.awayShare), 0), 1)
        let wall = lifted * (1 - M.awayRelief * away)
        let ground = Color(hex: background)
        let ink = Color(hex: textColor)

        ZStack(alignment: .topLeading) {
            // The wall, blurred and dimmed behind an open tile.
            Canvas(rendersAsynchronously: false) { context, _ in
                BreathingGapGridPainter(frame: frame, tiles: tiles, caption: caption, background: background, textColor: textColor, reducedMotion: reducedMotion)
                    .drawWall(in: &context)
            }
            .frame(width: width, height: height)
            .drawingGroup()
            .blur(radius: reducedMotion || wall <= 0.002 ? 0 : M.blurRadius(M.wallBlur * wall), opaque: true)

            ground
                .opacity(wall > 0.002 ? M.wallDim * wall : 0)
                .frame(width: width, height: height)

            if o > 0.001 && frame.openC != M.none {
                openTile(lifted: lifted, away: away)
            }

            LinearGradient(colors: [ground.opacity(M.edgeStrength), ground.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: insets.top + M.edgeFade)
            LinearGradient(colors: [ground.opacity(0), ground.opacity(M.edgeStrength)], startPoint: .top, endPoint: .bottom)
                .frame(width: width, height: insets.bottom + M.edgeFade)
                .offset(y: height - insets.bottom - M.edgeFade)

            // The columns on screen and the live width of the gap, in points, right-aligned with
            // the other labels' edge.
            let readout = Self.readout(width: width, height: height, spread: frame.spread)
            let measured = BreathingGapGridSkiaText.width(readout, BreathingGapGridSkiaText.font("HelveticaNeue-Medium", 11))
            BreathingGapGridLabel(text: readout, font: .custom("HelveticaNeue-Medium", fixedSize: 11), color: ink, align: 0)
                .offset(x: width - 20 - measured, y: insets.top + 26)
                .opacity(frame.chrome)

            labels(width: width, height: height)
                .opacity(frame.chrome)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// "4 COL · GAP 14 PT": the columns on screen and the live width of the gap.
    static func readout(width: Double, height: Double, spread: Double) -> String {
        let l = L(width: width, height: height, spread: spread, time: 0, scroll: 0)
        return "\(l.columnsShown) COL · GAP \(Int((l.pitch - l.side).rounded(.toNearestOrAwayFromZero))) PT"
    }

    private func labels(width: Double, height: Double) -> some View {
        let ink = Color(hex: textColor)
        func label(_ text: String, _ alignment: Alignment) -> some View {
            Text(text.uppercased())
                .font(.system(size: 11, weight: .medium))
                .kerning(1)
                .foregroundStyle(ink)
                .lineLimit(1)
                .frame(maxWidth: .infinity, alignment: alignment)
        }
        return ZStack(alignment: .topLeading) {
            HStack(spacing: 0) {
                label("Studio Notes", .leading)
                label("Frames", .center)
                Color.clear.frame(maxWidth: .infinity, maxHeight: 1)
            }
            .padding(.horizontal, 20)
            .frame(width: width)
            .offset(y: insets.top + 14)

            HStack(spacing: 0) {
                label("Pinch", .leading)
                label("Drag", .center)
                label("Tap", .trailing)
            }
            .padding(.horizontal, 20)
            .frame(width: width, height: height - insets.bottom - 18, alignment: .bottom)
        }
    }

    // The open tile, between its cell and the middle of the screen, following the finger, with a
    // glow in its own colour below it and its note and title riding just below it.
    private func openTile(lifted: Double, away: Double) -> some View {
        let l = frame.layout
        let o = frame.open
        let openSide = l.width - M.openMargin * 2
        let from = l.cellCentre(frame.openC, frame.openR)
        let shrink = 1 - M.dragShrink * min(max(frame.dragY / (l.height * M.shrinkShare), 0), 1)
        let s = l.side + (openSide * shrink - l.side) * o
        let cx = from.x + (l.width / 2 + frame.dragX - from.x) * o
        let cy = from.y + (l.height * M.openAt + frame.dragY - from.y) * o
        let index = L.tileAt(frame.openC, frame.openR, tiles.count)
        let tile = tiles[index]
        let shown = min(max((o - 0.55) / 0.45, 0), 1) * (1 - min(max(away * 3, 0), 1))
        let e = 1 - pow(1 - shown, 3)
        let top = cy + s / 2 + M.detailGap + (1 - e) * M.captionRise
        let ink = Color(hex: textColor)

        return ZStack(alignment: .topLeading) {
            Rectangle()
                .fill(Color(hex: tile.ground))
                .frame(width: max(0, s * 0.9), height: max(0, s * 0.9))
                .blur(radius: M.blurRadius(M.glowBlur * max(0.2, s / openSide)))
                .opacity(M.glow * lifted * (1 - away))
                .offset(x: cx - s * 0.45, y: cy - s * 0.45 + M.glowDrop * lifted)

            Canvas { context, _ in
                let sharp = frame.sharp.flatMap { $0.index == index ? $0.photo : nil }
                BreathingGapGridPainter(frame: frame, tiles: tiles, caption: caption, background: background, textColor: textColor, reducedMotion: reducedMotion)
                    .drawTile(in: &context, index: index, rect: CGRect(x: 0, y: 0, width: s, height: s), alpha: 1, sharper: sharp, vector: true)
            }
            .frame(width: max(1, s), height: max(1, s))
            .offset(x: cx - s / 2, y: cy - s / 2)

            if shown > 0.01 {
                BreathingGapGridTrackedText(text: tile.note.uppercased(), fontName: "HelveticaNeue-Medium", size: 12, tracking: 1, color: ink)
                    .opacity(0.6 * e)
                    .offset(x: cx - s / 2, y: top + 12)
                BreathingGapGridLabel(text: tile.title, font: .custom("HelveticaNeue-Light", fixedSize: 34), color: ink, align: 0)
                    .opacity(e)
                    .offset(x: cx - s / 2, y: top + 12 + 8 + 30)
            }
        }
    }
}

/// Text hung by its baseline from a point with no size, set to the left (align 0), middle (0.5) or
/// right (1) of it, so it never widens the layout it sits in.
private struct BreathingGapGridLabel: View {
    let text: String
    let font: Font
    let color: Color
    let align: CGFloat
    var kerning: CGFloat = 0

    var body: some View {
        Color.clear
            .frame(width: 0, height: 0)
            .overlay(alignment: Alignment(horizontal: align == 0 ? .leading : align == 1 ? .trailing : .center, vertical: .firstTextBaseline)) {
                Text(text)
                    .font(font)
                    .kerning(kerning)
                    .foregroundStyle(color)
                    .fixedSize()
            }
    }
}
