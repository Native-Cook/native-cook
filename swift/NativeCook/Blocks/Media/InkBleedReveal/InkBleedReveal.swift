import SwiftUI
import UIKit

struct InkBleedRevealPlate {
    /// Unsplash photo id, printed through the ink.
    var photo: String
    var title: String
    var line: String
    /// Small caps over the print, e.g. a place.
    var place: String
}

/// Ink over warm paper. A drop falls, and the next photo bleeds into the print through an organic,
/// fibrous ink front that spreads from where it landed, dark and wet at the edge before the image
/// comes through. Tap anywhere to drop ink; hold first for a heavier drop.
struct InkBleedReveal: View {
    /// Up to four plates, printed in turn.
    var plates: [InkBleedRevealPlate] = InkBleedRevealPlate.samples
    /// Drops ink at a few spots in turn, then lets the prints dry back to paper and starts over.
    var autoPlay = false
    /// Called with a plate's index when its ink has fully spread.
    var onReveal: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The ink lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = InkBleedRevealEngine()
    // The four photos, in plate order; the ink shader prints them in turn.
    @State private var photos: [UIImage]?

    // An empty list falls back to the built-in plates, so there is always something to print.
    private var list: [InkBleedRevealPlate] { plates.isEmpty ? InkBleedRevealPlate.samples : plates }
    private var count: Int { min(4, list.count) }

    var body: some View {
        // The block draws under the system bars and keeps its type clear of them itself.
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
        .task(id: plates.map(\.photo)) { await loadPhotos() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                stage: InkBleedRevealStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                settings: InkBleedRevealSettings(count: count, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, onReveal: onReveal)
            )
            InkBleedRevealScene(frame: frame, plates: Array(list.prefix(count)), photos: photos)
                .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height)
        .background(InkBleedRevealScene.paper)
        .clipped()
        .contentShape(Rectangle())
        // A press that starts anywhere: the bead hangs under it, follows it and falls when let go.
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in engine.touchChanged(start: value.startLocation, location: value.location) }
                .onEnded { value in engine.touchEnded(velocity: value.velocity) }
        )
        .accessibilityElement()
        .accessibilityLabel("Ink and water: drop ink to reveal the next print")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction(named: "Drop ink") { engine.dropInMiddle() }
    }

    /// Photos load one by one; the ink starts as soon as the first is in, and a photo that fails
    /// prints as a plain blue-grey wash instead.
    private func loadPhotos() async {
        let list = self.list
        var loaded: [UIImage?] = Array(repeating: nil, count: 4)
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for i in 0..<4 {
                group.addTask { (i, await InkBleedRevealPhotos.load(inkBleedRevealPhoto(list[i % list.count].photo))) }
            }
            for await (i, image) in group {
                loaded[i] = image ?? InkBleedRevealPhotos.placeholder
                guard let first = loaded.compactMap({ $0 }).first else { continue }
                photos = loaded.map { $0 ?? first }
                engine.photosArrived()
            }
        }
    }
}

/// Everything one frame draws: the inked paper, the crop marks, the droplets, the bead and the type.
private struct InkBleedRevealScene: View {
    let frame: InkBleedRevealFrame
    let plates: [InkBleedRevealPlate]
    let photos: [UIImage]?

    static let paper = Color(hex: 0xf3efe7)
    static let ink = Color(hex: 0x161a23)
    static let inkSoft = Color(hex: 0x59606e)

    private typealias M = InkBleedRevealMetrics

    var body: some View {
        let stage = frame.stage
        let print = stage.print
        ZStack(alignment: .topLeading) {
            paperAndInk
            Canvas { context, _ in drawMarks(context, print) }
            Canvas { context, _ in drawDroplets(context) }
            Canvas { context, _ in drawBead(context) }
            header
            captions(top: print.maxY + 30)
            Text("Tap to drop ink · hold for a heavier drop")
                .font(InkBleedRevealType.hint)
                .foregroundStyle(Self.inkSoft)
                .opacity(0.85)
                .frame(width: stage.width, height: stage.height - stage.bottom - 22, alignment: .bottom)
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    // MARK: Paper and ink

    /// The ink shader as a layer effect over the printed photo, cropped to fill the print (and,
    /// like a cover-fitted image shader, running on past it along its longer side); the photo the
    /// ink brings in goes to the shader as a texture with the rectangle it fills.
    private var paperAndInk: some View {
        let stage = frame.stage
        let print = stage.print
        let base = frame.base
        let top = frame.top
        let basePhoto = photos.flatMap { base >= 0 ? $0[base % $0.count] : nil }
        let topPhoto = photos.flatMap { top >= 0 ? $0[top % $0.count] : nil }
        let incoming = topPhoto.map { Self.cover($0, print) } ?? CGRect(x: 0, y: 0, width: 1, height: 1)
        let shader = ShaderLibrary.inkBleedRevealInk(
            .float2(CGSize(width: stage.width, height: stage.height)),
            .float4(print.minX, print.minY, print.width, print.height),
            .image(topPhoto.map { Image(uiImage: $0) } ?? InkBleedRevealScene.blank),
            .float4(incoming.minX, incoming.minY, incoming.width, incoming.height),
            .float(basePhoto != nil ? 1 : 0),
            .float(topPhoto != nil ? 1 : 0),
            .float2(frame.drop),
            .float(frame.front),
            .float(frame.seed),
            .float(frame.dry),
            .float(frame.blend)
        )
        return Canvas { context, size in
            // Opaque black where there is no photo, which the shader reads as a clear decal does;
            // an empty layer would also skip the effect altogether.
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black))
            if let basePhoto {
                context.draw(Image(uiImage: basePhoto), in: Self.cover(basePhoto, print))
            }
        }
        .frame(width: stage.width, height: stage.height)
        .layerEffect(shader, maxSampleOffset: .zero)
    }

    /// A 1 pt image for the shader's texture while no photo is coming in.
    private static let blank = Image(uiImage: UIGraphicsImageRenderer(size: CGSize(width: 1, height: 1)).image { _ in })

    /// Where `image` lies when it is scaled to cover `rect`, centred on it.
    private static func cover(_ image: UIImage, _ rect: CGRect) -> CGRect {
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let w = image.size.width * scale
        let h = image.size.height * scale
        return CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h)
    }

    private func drawMarks(_ context: GraphicsContext, _ print: CGRect) {
        var path = Path()
        let corners: [(CGFloat, CGFloat, CGFloat, CGFloat)] = [
            (print.minX, print.minY, -1, -1),
            (print.maxX, print.minY, 1, -1),
            (print.minX, print.maxY, -1, 1),
            (print.maxX, print.maxY, 1, 1),
        ]
        for (cx, cy, sx, sy) in corners {
            path.move(to: CGPoint(x: cx + sx * M.cropGap, y: cy))
            path.addLine(to: CGPoint(x: cx + sx * (M.cropGap + M.cropMark), y: cy))
            path.move(to: CGPoint(x: cx, y: cy + sy * M.cropGap))
            path.addLine(to: CGPoint(x: cx, y: cy + sy * (M.cropGap + M.cropMark)))
        }
        context.stroke(path, with: .color(Self.ink.opacity(0.32)), lineWidth: 0.8)
    }

    private func drawDroplets(_ context: GraphicsContext) {
        guard frame.dropletOpacity > 0 else { return }
        var context = context
        context.opacity = frame.dropletOpacity
        for d in frame.droplets {
            context.fill(Path(ellipseIn: CGRect(x: d.x - d.r, y: d.y - d.r, width: d.r * 2, height: d.r * 2)), with: .color(Self.ink))
        }
    }

    /// The bead: bigger and lifted while it hangs, its shadow sliding in under it as it falls.
    private func drawBead(_ context: GraphicsContext) {
        guard frame.beadOpacity > 0 else { return }
        let z = frame.beadZ
        let r = frame.beadRadius
        let circle = Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2))

        // The blur is set in screen space, so it is scaled with the shadow as Skia's mask blur is.
        var shadow = context
        shadow.opacity = frame.beadOpacity * (0.32 - 0.14 * z)
        shadow.addFilter(.blur(radius: (1.5 + z * 7) * (1 + 0.6 * z)))
        shadow.translateBy(x: frame.beadX + 9 * z, y: frame.beadY + 16 * z)
        shadow.scaleBy(x: 1 + 0.6 * z, y: 1 + 0.6 * z)
        shadow.fill(circle, with: .color(Self.ink))

        var bead = context
        bead.opacity = frame.beadOpacity
        bead.translateBy(x: frame.beadX, y: frame.beadY - z * 10)
        bead.scaleBy(x: frame.beadScale, y: frame.beadScale * frame.beadStretch)
        bead.fill(circle, with: .radialGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0x4a5266), location: 0),
                .init(color: Self.ink, location: 0.55),
                .init(color: Color(hex: 0x07080c), location: 1),
            ]),
            center: CGPoint(x: -3, y: -4),
            startRadius: 0,
            endRadius: M.beadRadius * 2.4
        ))
        // The glint.
        let g = r * 0.28
        bead.fill(
            Path(ellipseIn: CGRect(x: -r * 0.36 - g, y: -r * 0.38 - g, width: g * 2, height: g * 2)),
            with: .color(.white.opacity(0.75))
        )
    }

    // MARK: Type

    private var header: some View {
        let stage = frame.stage
        let count = plates.count
        return ZStack(alignment: .topLeading) {
            label("Field Journal")
            ForEach(0...count, id: \.self) { i in
                let words = ["One", "Two", "Three", "Four"]
                let roman = ["I", "II", "III", "IV"]
                let text = i == 0 ? "\(words[count - 1]) prints" : "\(roman[i - 1]) / \(roman[count - 1])"
                label(text)
                    .frame(width: stage.width - (M.printSide + 2) * 2, alignment: .trailing)
                    .opacity(frame.captions[i].opacity)
                    .offset(y: frame.captions[i].offset)
            }
        }
        .frame(width: stage.width - (M.printSide + 2) * 2, height: 20, alignment: .topLeading)
        .offset(x: M.printSide + 2, y: stage.top + 18)
    }

    private func label(_ text: String) -> some View {
        Text(text.uppercased())
            .font(InkBleedRevealType.smallCaps)
            .kerning(1.8)
            .foregroundStyle(Self.inkSoft)
            .fixedSize()
    }

    private func captions(top: CGFloat) -> some View {
        let width = frame.stage.width - (M.printSide + 4) * 2
        let entries = [(InkBleedRevealPlate.intro)] + plates
        return ZStack(alignment: .topLeading) {
            ForEach(entries.indices, id: \.self) { i in
                let plate = entries[i]
                VStack(alignment: .leading, spacing: 0) {
                    Text(plate.place.uppercased())
                        .font(InkBleedRevealType.smallCaps)
                        .kerning(1.8)
                        .foregroundStyle(Self.inkSoft)
                        .padding(.bottom, 6)
                    Text(plate.title)
                        .font(InkBleedRevealType.title)
                        .kerning(-0.3)
                        .foregroundStyle(Self.ink)
                        .lineSpacing(InkBleedRevealType.titleSpacing)
                        .padding(.vertical, InkBleedRevealType.titlePad)
                    Text(plate.line)
                        .font(InkBleedRevealType.line)
                        .foregroundStyle(Self.inkSoft)
                        .lineSpacing(InkBleedRevealType.lineSpacing)
                        .padding(.vertical, InkBleedRevealType.linePad)
                        .frame(maxWidth: 330, alignment: .leading)
                        .padding(.top, 8)
                }
                .frame(width: width, alignment: .leading)
                .opacity(frame.captions[i].opacity)
                .offset(y: frame.captions[i].offset)
            }
        }
        .frame(width: width, alignment: .topLeading)
        .offset(x: M.printSide + 4, y: top)
    }
}

/// The type the React Native block uses: a sturdy book serif (Georgia) for the captions, its bold
/// italic for the titles, and tracked capitals in the system sans for the small labels.
private enum InkBleedRevealType {
    static let smallCaps = Font.system(size: 12, weight: .semibold)
    static let title = Font.custom("Georgia-BoldItalic", fixedSize: 36)
    static let line = Font.custom("Georgia", fixedSize: 17)
    static let hint = Font.system(size: 13, weight: .medium)

    // React Native's line heights: 42 pt for the title, 24 pt for the line, split evenly above and
    // below each line of type.
    private static func leading(_ name: String, _ size: CGFloat) -> CGFloat {
        UIFont(name: name, size: size)?.lineHeight ?? size * 1.2
    }
    static let titleSpacing = max(0, 42 - leading("Georgia-BoldItalic", 36))
    static let titlePad = titleSpacing / 2
    static let lineSpacing = max(0, 24 - leading("Georgia", 17))
    static let linePad = lineSpacing / 2
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum InkBleedRevealPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    /// A plain blue-grey wash that stands in for a photo that could not be loaded.
    static let placeholder: UIImage = UIGraphicsImageRenderer(size: CGSize(width: 64, height: 96)).image { ctx in
        let colours = [UIColor(red: 0.365, green: 0.42, blue: 0.486, alpha: 1).cgColor, UIColor(red: 0.149, green: 0.188, blue: 0.239, alpha: 1).cgColor]
        if let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colours as CFArray, locations: [0, 1]) {
            ctx.cgContext.drawLinearGradient(gradient, start: .zero, end: CGPoint(x: 0, y: 96), options: [])
        }
    }

    static func load(_ address: String) async -> UIImage? {
        if let hit = cache.object(forKey: address as NSString) { return hit }
        guard let url = URL(string: address),
              let (data, _) = try? await URLSession.shared.data(from: url),
              let image = UIImage(data: data)
        else { return nil }
        let ready = await image.byPreparingForDisplay() ?? image
        cache.setObject(ready, forKey: address as NSString)
        return ready
    }
}

// Photos are drawn about 370 pt wide on a phone; 1100 px covers that at 3x.
private func inkBleedRevealPhoto(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1100&q=80"
}

// MARK: - Samples

extension InkBleedRevealPlate {
    static let intro = InkBleedRevealPlate(
        photo: "",
        title: "Ink & Water",
        line: "Four quiet places, printed by hand. Drop a little ink to begin.",
        place: "Volume I"
    )

    static let samples: [InkBleedRevealPlate] = [
        .init(photo: "photo-1506536329413-d2f0d31ceb9f", title: "Still water", line: "Mornings on Lake Orsel, where every peak is printed twice.", place: "Lake Orsel"),
        .init(photo: "photo-1433086966358-54859d0ed716", title: "Falling light", line: "Hollin Falls after three days of rain, loud enough to hush you.", place: "Hollin Falls"),
        .init(photo: "photo-1596003903067-bf5762ad5c19", title: "The long mirror", line: "A tarn above the treeline that holds the whole sky still.", place: "Aster Tarn"),
        .init(photo: "photo-1525824236856-8c0a31dfe3be", title: "White thread", line: "The upper cascade of the Vell, thin as a stitch in the moss.", place: "River Vell"),
    ]
}
