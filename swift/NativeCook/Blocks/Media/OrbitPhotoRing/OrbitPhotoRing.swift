import SwiftUI
import UIKit

/// One camera body: its name, a short spec line, its proportions and colours, and the prints it
/// deals into the ring. Every number is in points of a 402 pt wide screen.
struct OrbitPhotoRingCamera {
    var name: String
    var spec: String
    /// Body width, height and depth.
    var body: [Double]
    /// The prism hump (or hood) on top: width, height, depth. Zero height for none.
    var hump: [Double]
    /// Lens radius and how far it stands out of the body.
    var lens: [Double]
    /// Where the lens sits on the front, from the middle of the body.
    var lensAt: [Double]
    /// 1 for a rangefinder with two finder windows on the front plate.
    var windows: Double
    /// Share of the body's height covered by the metal top plate.
    var plate: Double
    var leather: UInt32
    var metal: UInt32
    var barrel: UInt32
    var accent: UInt32
    var glass: UInt32
    /// Tumble style: the glossy shell on top and the satin body under it.
    var toyShell: UInt32? = nil
    var toyBody: UInt32? = nil
    /// Unsplash photo ids for its prints.
    var photos: [String]
}

/// How the prints orbit: one tilted ring, a close ring around a tumbling camera, or two crossed rings.
enum OrbitPhotoRingStyle: String, CaseIterable {
    case ring
    case tumble
    case gyro

    var label: String {
        switch self {
        case .ring: "Ring"
        case .tumble: "Tumble"
        case .gyro: "Gyro"
        }
    }
}

/// A retro camera hangs in the dark, drawn in true perspective, with ten polaroids orbiting it on
/// a tilted ring: the prints behind it shrink, darken and blur. Dragging sideways spins the ring, a
/// throw coasts, and the body turns with the spin; dragging up or down tilts the scene on a rubber
/// band. A bar of thumbnails swaps the body: it spins once while it morphs into the next model, the
/// prints fly off, and new ones are shot out of the lens. Tapping the body fires the shutter. A
/// switcher at the top morphs the ring between three styles.
struct OrbitPhotoRing: View {
    var cameras: [OrbitPhotoRingCamera] = OrbitPhotoRingCamera.samples
    /// The style the block opens with; the switcher at the top changes it, morphing smoothly.
    var initialRingStyle: OrbitPhotoRingStyle = .ring
    /// Shows the Ring · Tumble · Gyro switcher at the top.
    var showStylePicker = true
    /// Called with the style chosen in the switcher.
    var onStyleChange: ((OrbitPhotoRingStyle) -> Void)? = nil
    /// Plays a 16 s loop: idle spin, a flick, camera changes, back to the first.
    var autoPlay = false
    var background = Color(orbitHex: 0x050506)
    var textColor = Color(orbitHex: 0xf3f1ea)
    /// Called with the index of a camera when it is chosen.
    var onSelect: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The ring's motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = OrbitPhotoRingEngine()
    // The style chosen in the switcher; nil until the first pick.
    @State private var pickedStyle: Int?

    private var styleIndex: Int {
        pickedStyle ?? OrbitPhotoRingStyle.allCases.firstIndex(of: initialRingStyle) ?? 0
    }

    /// Every camera needs a print; a list without cameras falls back to the four built in.
    private var shown: [OrbitPhotoRingCamera] {
        !cameras.isEmpty && cameras.allSatisfy({ !$0.photos.isEmpty }) ? cameras : OrbitPhotoRingCamera.samples
    }

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
        .task(id: shown.flatMap(\.photos)) { await loadPhotos() }
    }

    private var settings: OrbitPhotoRingSettings {
        let cameras = shown
        return OrbitPhotoRingSettings(
            cameras: cameras,
            vectors: cameras.map(OrbitPhotoRingVector.make),
            autoPlay: autoPlay,
            initialStyle: OrbitPhotoRingStyle.allCases.firstIndex(of: initialRingStyle) ?? 0,
            reducedMotion: reduceMotion,
            onSelect: onSelect
        )
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let settings = settings
        let cameras = settings.cameras
        return ZStack(alignment: .top) {
            canvas(size: size, insets: insets, settings: settings, cameras: cameras)
            // Over the canvas, outside its gesture: taps on the switcher never reach the ring.
            if showStylePicker {
                OrbitPhotoRingStylePicker(selected: styleIndex, ink: textColor, reduced: reduceMotion) { pickStyle($0, settings: settings) }
                    .padding(.top, insets.top + 10)
            }
        }
        .frame(width: size.width, height: size.height, alignment: .top)
    }

    private func pickStyle(_ index: Int, settings: OrbitPhotoRingSettings) {
        if index == styleIndex { return }
        pickedStyle = index
        engine.chooseStyle(index, settings: settings)
        engine.selectionTick()
        let styles = OrbitPhotoRingStyle.allCases
        if styles.indices.contains(index) { onStyleChange?(styles[index]) }
    }

    private func canvas(size: CGSize, insets: EdgeInsets, settings: OrbitPhotoRingSettings, cameras: [OrbitPhotoRingCamera]) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                stage: OrbitPhotoRingStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                settings: settings
            )
            OrbitPhotoRingScene(frame: frame, cameras: cameras, vectors: settings.vectors, background: background, textColor: textColor, reduced: reduceMotion)
        }
        .frame(width: size.width, height: size.height)
        .background(background)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { engine.touchMoved($0, settings: settings) }
                .onEnded { engine.touchEnded($0, settings: settings) }
        )
        .accessibilityElement()
        .accessibilityLabel("A \(cameras.map(\.name).joined(separator: ", ")) camera with ten photo prints orbiting it. Drag sideways to spin the prints; swipe the bar of cameras below to browse them and tap one to change cameras.")
    }

    // Every print of every camera is fetched up front; each fades in once it arrives.
    private func loadPhotos() async {
        let cameras = shown
        engine.resetPhotos(cameras)
        await withTaskGroup(of: (Int, Int, UIImage?).self) { group in
            for (ci, camera) in cameras.enumerated() {
                for (pi, id) in camera.photos.enumerated() {
                    group.addTask { (ci, pi, await OrbitPhotoRingPhotos.load(id)) }
                }
            }
            for await (ci, pi, image) in group {
                if let image { engine.photoArrived(image, camera: ci, index: pi) }
            }
        }
    }
}

/// Everything one frame draws: the warm pool behind, the prints far to near with the body among
/// them, and over it all the flash, the name, the spec line and the bar.
private struct OrbitPhotoRingScene: View {
    let frame: OrbitPhotoRingFrame
    let cameras: [OrbitPhotoRingCamera]
    let vectors: [[Double]]
    let background: Color
    let textColor: Color
    let reduced: Bool

    private typealias M = OrbitPhotoRingMetrics
    private typealias P = OrbitPhotoRingMath

    var body: some View {
        let room = frame.stage
        ZStack(alignment: .topLeading) {
            Canvas { ctx, _ in ground(ctx) }
                .frame(width: room.width, height: room.height)

            ForEach(frame.prints, id: \.key) { print in
                printView(print)
            }

            Canvas { ctx, _ in
                Self.drawAnyCamera(ctx, view: frame.view, pose: frame.pose, v: frame.body, toy: frame.toy, detail: true, label: frame.label, flash: frame.flash)
            }
            .frame(width: room.width, height: room.height)
            .zIndex(frame.bodyOrder)

            Canvas { ctx, _ in overlay(ctx) }
                .frame(width: room.width, height: room.height)
                .zIndex(10_000)
        }
        .frame(width: room.width, height: room.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func ground(_ ctx: GraphicsContext) {
        let room = frame.stage
        let k = room.width / M.designWidth
        let all = Path(CGRect(x: 0, y: 0, width: room.width, height: room.height))
        ctx.fill(all, with: .color(background))
        // A dim warm pool of light behind the body.
        let pool = Gradient(stops: [
            .init(color: Color(orbitHex: 0x221d1a), location: 0),
            .init(color: Color(orbitHex: 0x0d0c0c), location: 0.5),
            .init(color: background, location: 1),
        ])
        ctx.fill(all, with: .radialGradient(pool, center: CGPoint(x: room.width / 2, y: frame.view.cy + 20 * k), startRadius: 0, endRadius: room.width * 0.75))
    }

    @ViewBuilder
    private func printView(_ p: OrbitPhotoRingPrint) -> some View {
        let row = frame.photos.indices.contains(p.set) ? frame.photos[p.set] : []
        let photo = row.indices.contains(p.index) ? row[p.index] : nil
        let count = cameras[p.set].photos.count
        OrbitPhotoRingPolaroid(
            image: photo?.image,
            imageAlpha: photo.map { P.clamp01((frame.elapsed - $0.at) / M.imageFade) } ?? 0,
            facing: p.facing,
            shade: p.shade,
            caption: "\(cameras[p.set].name) · \(String(format: "%02d", p.index + 1))/\(String(format: "%02d", count))"
        )
        .projectionEffect(OrbitPhotoRingPolaroid.projection(p.matrix))
        .blur(radius: p.blur > 0.25 ? p.blur : 0)
        .opacity(P.clamp01(p.alpha))
        .zIndex(p.order)
    }

    private func ink(_ a: Double) -> Color { textColor.opacity(a) }

    private func overlay(_ ctx: GraphicsContext) {
        let room = frame.stage
        let k = room.width / M.designWidth
        let fromIndex = frame.from
        let toIndex = frame.to
        let t = frame.since
        let names = cameras.map(\.name)

        // A faint white flash over the whole scene when the shutter fires.
        if frame.flash > 0.01 {
            ctx.fill(Path(CGRect(x: 0, y: 0, width: room.width, height: room.height)), with: .color(.white.opacity(frame.flash * frame.flash * 0.12)))
        }

        // The name rolls letter by letter: the old one up and out, the new one up from below.
        let barY = room.height - room.bottom - M.barAbove
        let nameY = barY - 92
        var clipped = ctx
        clipped.clip(to: Path(CGRect(x: 0, y: nameY - 34, width: room.width, height: 42)))
        let changing = fromIndex != toIndex && t < 1.2
        rollName(clipped, names[fromIndex], cx: room.width / 2, y: nameY, t: changing ? t : -1, incoming: false)
        rollName(clipped, names[toIndex], cx: room.width / 2, y: nameY, t: changing ? t : -1, incoming: true)
        let specOut = fromIndex != toIndex ? 1 - P.clamp01((t - 0.05) / 0.25) : 0
        let specIn = fromIndex != toIndex ? P.clamp01((t - 0.35) / 0.3) : 1
        if specOut > 0.01 {
            ctx.fill(OrbitPhotoRingType.tracked(cameras[fromIndex].spec, OrbitPhotoRingType.spec, x: room.width / 2, y: nameY + 22, tracking: 1.4, align: 0.5), with: .color(ink(0.5 * specOut)))
        }
        if specIn > 0.01 {
            ctx.fill(OrbitPhotoRingType.tracked(cameras[toIndex].spec, OrbitPhotoRingType.spec, x: room.width / 2, y: nameY + 22, tracking: 1.4, align: 0.5), with: .color(ink(0.5 * specIn)))
        }

        // The bar: the chosen camera in the middle, in colour; its neighbours smaller and grey.
        for i in 0..<cameras.count {
            let x = room.width / 2 + (Double(i) - frame.bar) * M.barSpacing * k
            if x < -M.barSpacing || x > room.width + M.barSpacing { continue }
            let e = P.clamp01(1 - abs(Double(i) - frame.pick))
            var layer = ctx
            layer.opacity = 0.42 + 0.58 * e
            layer.addFilter(.colorMatrix(Self.saturation(0.15 + 0.85 * e)))
            let v = vectors[i]
            let thumb = OrbitPhotoRingPose(
                x: 0, y: 0, z: M.depth,
                s: M.thumbWidth / v[OrbitPhotoRingVector.w] * (0.82 + 0.3 * e),
                r: P.rotation(24 * .pi / 180, 16 * .pi / 180, 0)
            )
            let view = OrbitPhotoRingView3(f: M.focal * k, cx: x, cy: barY - 6 * k)
            layer.drawLayer { l in
                Self.drawAnyCamera(l, view: view, pose: thumb, v: v, toy: frame.toy, detail: false, label: names[i], flash: 0)
            }
            ctx.fill(OrbitPhotoRingType.tracked(names[i], OrbitPhotoRingType.label, x: x, y: barY + 30 * k, tracking: 1.2, align: 0.5), with: .color(ink(0.3 + 0.7 * e)))
        }
    }

    /// The camera's name, letter by letter. While a change is under way (`t` its age in seconds, −1
    /// for none) the old name's letters leave upwards and the new one's rise in from below, each a
    /// little after the one before.
    private func rollName(_ ctx: GraphicsContext, _ text: String, cx: Double, y: Double, t: Double, incoming: Bool) {
        let font = OrbitPhotoRingType.name
        let tracking = 3.0
        let chars = Array(text)
        var total = 0.0
        for (i, c) in chars.enumerated() { total += OrbitPhotoRingType.letter(c, font).measured + (i < chars.count - 1 ? tracking : 0) }
        var at = cx - total / 2
        for (i, c) in chars.enumerated() {
            let glyph = OrbitPhotoRingType.letter(c, font)
            var dy = 0.0
            var a = incoming ? 1.0 : 0
            if t >= 0 {
                if incoming {
                    let q = P.clamp01((t - 0.22 - Double(i) * 0.045) / 0.38)
                    dy = 30 * (1 - P.easeOut(q))
                    a = q
                } else {
                    let q = P.clamp01((t - Double(i) * 0.045) / 0.28)
                    dy = -30 * P.easeIn(q)
                    a = 1 - q
                }
            }
            if reduced { dy = 0 }
            if a > 0.01 {
                ctx.fill(glyph.path.applying(CGAffineTransform(translationX: at, y: y + dy)), with: .color(ink(a)))
            }
            at += glyph.measured + tracking
        }
    }

    /// The camera as the style wants it: the box camera, the toy, or both cross-fading while the
    /// style morphs (`toy` 0…1).
    static func drawAnyCamera(_ ctx: GraphicsContext, view: OrbitPhotoRingView3, pose: OrbitPhotoRingPose, v: [Double], toy: Double, detail: Bool, label: String, flash: Double) {
        if toy < 0.99 {
            if toy > 0.01 {
                var box = ctx
                box.opacity = 1 - toy
                box.drawLayer { l in
                    OrbitPhotoRingPainter.drawCamera(l, view: view, pose: pose, v: v, detail: detail, label: label, flash: flash)
                }
            } else {
                OrbitPhotoRingPainter.drawCamera(ctx, view: view, pose: pose, v: v, detail: detail, label: label, flash: flash)
            }
        }
        if toy > 0.01 {
            OrbitPhotoRingToy.draw(ctx, view: view, pose: pose, shape: OrbitPhotoRingToyShape(v), alpha: toy, flash: flash)
        }
    }

    /// A colour matrix that keeps `s` of the colour and greys the rest.
    private static func saturation(_ s: Double) -> ColorMatrix {
        let r = Float((1 - s) * 0.2126)
        let g = Float((1 - s) * 0.7152)
        let b = Float((1 - s) * 0.0722)
        let k = Float(s)
        var m = ColorMatrix()
        m.r1 = r + k; m.r2 = g; m.r3 = b; m.r4 = 0; m.r5 = 0
        m.g1 = r; m.g2 = g + k; m.g3 = b; m.g4 = 0; m.g5 = 0
        m.b1 = r; m.b2 = g; m.b3 = b + k; m.b4 = 0; m.b5 = 0
        m.a1 = 0; m.a2 = 0; m.a3 = 0; m.a4 = 1; m.a5 = 0
        return m
    }
}

/// A polaroid laid out flat at twice its size (100 × 126 units, 2 pt each): a white card with a
/// slightly tall photo, and on the wide bottom margin a caption in pencil grey and a small ring
/// mark. Its perspective is applied to it as a whole.
private struct OrbitPhotoRingPolaroid: View {
    let image: UIImage?
    let imageAlpha: Double
    let facing: Bool
    let shade: Double
    let caption: String

    static let scale = 2.0

    /// The print's matrix (units about its middle) as SwiftUI wants it for the face laid out from
    /// its top left: ProjectionTransform multiplies row vectors, so the product is transposed.
    static func projection(_ m: [Double]) -> ProjectionTransform {
        let c = OrbitPhotoRingMath.mul3(m, [1 / scale, 0, -50, 0, 1 / scale, -63, 0, 0, 1])
        var t = ProjectionTransform()
        t.m11 = c[0]; t.m21 = c[1]; t.m31 = c[2]
        t.m12 = c[3]; t.m22 = c[4]; t.m32 = c[5]
        t.m13 = c[6]; t.m23 = c[7]; t.m33 = c[8]
        return t
    }

    var body: some View {
        Canvas { ctx, _ in
            ctx.scaleBy(x: Self.scale, y: Self.scale)
            ctx.translateBy(x: 50, y: 63)
            let card = Path(roundedRect: CGRect(x: -50, y: -63, width: 100, height: 126), cornerSize: CGSize(width: 2, height: 2), style: .circular)
            ctx.fill(card, with: .color(Color(orbitHex: facing ? 0xf4f2ec : 0xd9d5cc)))
            if facing {
                let window = CGRect(x: -44, y: -57, width: 88, height: 98)
                ctx.fill(Path(window), with: .color(Color(orbitHex: 0x26262a)))
                if let image, imageAlpha > 0 {
                    var art = ctx
                    art.opacity = imageAlpha
                    art.clip(to: Path(window))
                    // The middle of the photo, filling the window.
                    let k = max(window.width / max(1, image.size.width), window.height / max(1, image.size.height))
                    let drawn = CGRect(
                        x: window.midX - image.size.width * k / 2,
                        y: window.midY - image.size.height * k / 2,
                        width: image.size.width * k,
                        height: image.size.height * k
                    )
                    art.draw(Image(uiImage: image).interpolation(.high), in: drawn)
                }
                // A soft sheen across the gloss, and the caption in pencil grey.
                let sheen = Gradient(stops: [
                    .init(color: .white.opacity(0.16), location: 0),
                    .init(color: .white.opacity(0), location: 0.5),
                    .init(color: .white.opacity(0.06), location: 1),
                ])
                ctx.fill(Path(window), with: .linearGradient(sheen, startPoint: CGPoint(x: -44, y: -57), endPoint: CGPoint(x: 44, y: 41)))
                let pencil = Color(.sRGB, red: 0.22, green: 0.21, blue: 0.2, opacity: 0.7)
                let text = OrbitPhotoRingType.plain(caption, OrbitPhotoRingType.caption, x: -43, y: 54.5)
                ctx.fill(text, with: .color(pencil))
                ctx.stroke(Path(ellipseIn: CGRect(x: 40 - 2.6, y: 52 - 2.6, width: 5.2, height: 5.2)), with: .color(pencil), lineWidth: 0.8)
            }
            if shade > 0.004 {
                ctx.fill(card, with: .color(.black.opacity(shade)))
            }
        }
        .frame(width: 100 * Self.scale, height: 126 * Self.scale)
    }
}

// MARK: - Style switcher

/// Ring · Tumble · Gyro, at the top of the screen: a dark capsule of three segments, a light pill
/// sliding under the chosen one.
private struct OrbitPhotoRingStylePicker: View {
    let selected: Int
    let ink: Color
    let reduced: Bool
    let onPick: (Int) -> Void

    static let pad = 3.0
    // Reanimated's spring of 380 ms (perceptual) at a damping ratio of 0.86 works out to this
    // stiffness for a unit mass.
    static let spring = Animation.interpolatingSpring(mass: 1, stiffness: 629.4, damping: 2 * 0.86 * 629.4.squareRoot())

    @State private var frames: [Int: CGRect] = [:]
    @Environment(\.displayScale) private var displayScale

    var body: some View {
        let styles = OrbitPhotoRingStyle.allCases
        HStack(spacing: 0) {
            ForEach(Array(styles.enumerated()), id: \.offset) { index, style in
                Button { onPick(index) } label: {
                    Text(style.label)
                        .font(.custom("AvenirNext-DemiBold", size: 13))
                        .kerning(0.2)
                        .foregroundStyle(index == selected ? Color(orbitHex: 0x0b0b0c) : ink)
                        .opacity(index == selected ? 1 : 0.62)
                        .lineLimit(1)
                        .fixedSize()
                        .padding(.horizontal, 16)
                        .frame(height: 30)
                        // Reaches 8 pt above and below the segment, like the React Native hit slop.
                        .padding(.vertical, 8)
                        .contentShape(Rectangle())
                        .padding(.vertical, -8)
                }
                .buttonStyle(OrbitPhotoRingSegmentStyle())
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named("orbitPhotoRingPicker")) } action: { frames[index] = $0 }
                .accessibilityLabel("\(style.label) style")
                .accessibilityAddTraits(index == selected ? [.isSelected, .isButton] : .isButton)
            }
        }
        .coordinateSpace(.named("orbitPhotoRingPicker"))
        .background(alignment: .topLeading) {
            if let frame = frames[selected], frame.width > 0, frame.width.isFinite, frame.minX.isFinite {
                Capsule()
                    .fill(ink)
                    .frame(width: frame.width, height: 30)
                    .offset(x: frame.minX)
                    .animation(reduced ? nil : Self.spring, value: selected)
            }
        }
        .padding(Self.pad)
        .background(Capsule().fill(Color.white.opacity(0.07)))
        .overlay(Capsule().strokeBorder(Color.white.opacity(0.16), lineWidth: 1 / max(1, displayScale)))
        .accessibilityElement(children: .contain)
    }
}

/// A segment dims while it is held, like a React Native Pressable.
private struct OrbitPhotoRingSegmentStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.opacity(configuration.isPressed ? 0.7 : 1)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum OrbitPhotoRingPhotos {
    private static let cache = NSCache<NSString, UIImage>()

    static func load(_ id: String) async -> UIImage? {
        let address = "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=300&h=340&q=80"
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

extension OrbitPhotoRingCamera {
    static let samples: [OrbitPhotoRingCamera] = [
        .init(
            name: "KDX", spec: "SLR · 35 MM · F/1.8 · 1/1000",
            body: [224, 124, 70], hump: [92, 34, 52], lens: [40, 38], lensAt: [0, 10], windows: 0, plate: 0.3,
            leather: 0x7a2e1c, metal: 0xcbc9c3, barrel: 0x19191b, accent: 0xdc3428, glass: 0x33386b, toyShell: 0x8a2e14, toyBody: 0x252238,
            photos: [
                "photo-1590486803833-1c5dc8ddd4c8", "photo-1569346276519-709519eeaa51", "photo-1568190002605-b51fa38eac46",
                "photo-1518676590629-3dcbd9c5a5c9", "photo-1597157153515-028fa3d4bd69", "photo-1561871733-40a3338b8cb4",
                "photo-1643138874737-33babf17f0a8", "photo-1598461336473-b0cc035d4e35", "photo-1603118258032-aea76c546616",
                "photo-1452587925148-ce544e77e70d",
            ]
        ),
        .init(
            name: "ORT4", spec: "RANGEFINDER · F/2 · 1/500",
            body: [238, 110, 62], hump: [0, 0, 40], lens: [34, 30], lensAt: [-16, 12], windows: 1, plate: 0.34,
            leather: 0x9c5a2c, metal: 0x2b2b2e, barrel: 0x1d1d20, accent: 0xec8a2e, glass: 0x28505a, toyShell: 0x1f1f22, toyBody: 0x7b4426,
            photos: [
                "photo-1623475329493-889804e377f8", "photo-1613749742342-5abdaa0d0af3", "photo-1649366735070-8e78a4f86f31",
                "photo-1617618482053-36e9280bbf5a", "photo-1622297118224-2a816821a174", "photo-1570385404967-fe4e1b48454b",
                "photo-1542388481-52d5a10ae242", "photo-1714201623945-0079e2150240", "photo-1690023430523-a298e78ab6fd",
                "photo-1602230168052-741c2b79ea91",
            ]
        ),
        .init(
            name: "ADR3", spec: "HALF FRAME · F/2.8 · 1/250",
            body: [206, 128, 74], hump: [80, 30, 50], lens: [42, 50], lensAt: [0, 12], windows: 0, plate: 0.28,
            leather: 0x424b31, metal: 0xc8c3b6, barrel: 0x202020, accent: 0xf0bd40, glass: 0x4f2d60, toyShell: 0xbdb9ae, toyBody: 0x3c4530,
            photos: [
                "photo-1573455494060-c5595004fb6c", "photo-1549693578-d683be217e58", "photo-1480796927426-f609979314bd",
                "photo-1564284369929-026ba231f89b", "photo-1542931287-023b922fa89b", "photo-1561503972-839d0c56de17",
                "photo-1525230071276-4a87f42f469e", "photo-1601042879364-f3947d3f9c16", "photo-1498036882173-b41c28a8ba34",
                "photo-1573455494057-12684d151bf4",
            ]
        ),
        .init(
            name: "MRV6", spec: "6×6 · F/3.5 · 1/400",
            body: [196, 146, 84], hump: [96, 18, 70], lens: [46, 40], lensAt: [0, 16], windows: 0, plate: 0.22,
            leather: 0x1c1c1f, metal: 0xbcbdc2, barrel: 0x141416, accent: 0xd8343c, glass: 0x2c3c66, toyShell: 0xa82429, toyBody: 0x1c1c20,
            photos: [
                "photo-1605437241278-c1806d14a4d9", "photo-1620584899131-a5ff5f8fbb03", "photo-1633014041037-f5446fb4ce99",
                "photo-1620584898989-d39f7f9ed1b7", "photo-1694678505383-676d78ea3b96", "photo-1515569067071-ec3b51335dd0",
                "photo-1708805282695-ef186db20192", "photo-1732357624591-f2137085659b", "photo-1708805283017-c662be2c7a44",
                "photo-1520340356584-f9917d1eea6f",
            ]
        ),
    ]
}

extension Color {
    /// A colour from a 0xRRGGBB number.
    init(orbitHex hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: opacity
        )
    }
}
