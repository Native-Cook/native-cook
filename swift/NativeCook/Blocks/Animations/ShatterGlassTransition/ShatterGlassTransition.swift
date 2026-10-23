import SwiftUI
import UIKit

struct ShatterGlassTransitionPhoto {
    /// An Unsplash photo id (`photo-…`), portrait.
    var id: String
    var title: String
    var line: String
}

/// A full-screen photo behind glass. Tapping (or a hard swipe) cracks it from the touch point, and
/// the pane breaks into shards that fall and spin away with the light catching their edges,
/// showing the next photo underneath. Swiping back fits the previous photo's shards together.
struct ShatterGlassTransition: View {
    var photos: [ShatterGlassTransitionPhoto] = ShatterGlassTransitionPhoto.samples
    /// Breaks to the next photo every couple of seconds, and once mends back, on a 13.2-second loop.
    var autoPlay = false
    /// Called with the index of the photo that ends up in front.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The pane lives in a plain object stepped once a frame, like the RN frame callback: nothing in
    // it is observed, the timeline redraws every frame anyway.
    @State private var engine = ShatterGlassTransitionEngine()
    @State private var images = ShatterGlassTransitionImages()
    @State private var shown = 0
    // Resets when the system cancels the touch, which never reaches onEnded.
    @GestureState private var fingerDown = false

    private typealias M = ShatterGlassTransitionMetrics

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
        .task(id: photos.map(\.id)) { await images.load(photos.map(\.id)) }
        .onAppear {
            engine.onAnnounce = { index in
                withAnimation { shown = index }
                onChange?(index)
            }
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        ZStack(alignment: .topLeading) {
            TimelineView(.animation) { timeline in
                let _ = engine.step(
                    to: timeline.date,
                    settings: ShatterGlassTransitionSettings(count: photos.count, autoPlay: autoPlay, reduced: reduceMotion, size: size)
                )
                Canvas { context, canvasSize in
                    ShatterGlassTransitionPainter.draw(
                        in: &context,
                        size: canvasSize,
                        engine: engine,
                        images: photos.indices.map { images.image(photos[$0].id) },
                        reduced: reduceMotion
                    )
                }
            }
            .frame(width: size.width, height: size.height)

            LinearGradient(colors: [Color.shatterRGBA(3, 4, 8, 0.45), Color.shatterRGBA(3, 4, 8, 0)], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: insets.top + 90)
            LinearGradient(colors: [Color.shatterRGBA(3, 4, 8, 0), Color.shatterRGBA(3, 4, 8, 0.7)], startPoint: .top, endPoint: .bottom)
                .frame(width: size.width, height: insets.bottom + 220)
                .offset(y: size.height - insets.bottom - 220)

            HStack {
                Text("BEHIND GLASS")
                    .font(ShatterGlassTransitionFont.bold(11))
                    .kerning(2.4)
                    .foregroundStyle(Color.white.opacity(0.92))
                Spacer(minLength: 8)
                Text("Tap to break · swipe back to mend")
                    .font(ShatterGlassTransitionFont.medium(12))
                    .foregroundStyle(Color.white.opacity(0.62))
            }
            .frame(width: max(0, size.width - 48))
            .offset(x: 24, y: insets.top + 14)

            caption
                .frame(width: max(0, size.width - 48), height: 120, alignment: .bottomLeading)
                .offset(x: 24, y: size.height - insets.bottom - 34 - 120)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .background(Color(shatterHex: 0x05060a))
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
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(photos[min(shown, max(0, photos.count - 1))].title). Tap to break the glass, swipe right to mend it.")
        .accessibilityAddTraits(.isButton)
    }

    // The caption fades out quickly and the next one in, a little later and slower.
    private var caption: some View {
        let photo = photos[min(shown, photos.count - 1)]
        return ZStack(alignment: .bottomLeading) {
            VStack(alignment: .leading, spacing: 0) {
                (Text(String(format: "%02d", shown + 1)).foregroundStyle(.white)
                    + Text(" / \(String(format: "%02d", photos.count))").foregroundStyle(Color.white.opacity(0.5)))
                    .font(ShatterGlassTransitionFont.bold(13))
                    .kerning(1.2)
                    .monospacedDigit()
                Text(photo.title)
                    .font(ShatterGlassTransitionFont.bold(34))
                    .kerning(-0.8)
                    .foregroundStyle(.white)
                    .padding(.top, 8)
                Text(photo.line)
                    .font(ShatterGlassTransitionFont.medium(14))
                    .foregroundStyle(Color.white.opacity(0.72))
                    .padding(.top, 4)
            }
            .id(shown)
            .transition(.asymmetric(
                insertion: .opacity.animation(.easeInOut(duration: 0.42).delay(0.2)),
                removal: .opacity.animation(.easeInOut(duration: 0.14))
            ))
        }
        .allowsHitTesting(false)
    }
}

// MARK: - Drawing

/// One frame of the pane: the next photo waiting behind, the front photo whole, cracked or in
/// shards, and the cracks.
enum ShatterGlassTransitionPainter {
    private typealias M = ShatterGlassTransitionMetrics

    @MainActor
    static func draw(
        in context: inout GraphicsContext,
        size: CGSize,
        engine: ShatterGlassTransitionEngine,
        images: [UIImage?],
        reduced: Bool
    ) {
        let w = Double(size.width)
        let h = Double(size.height)
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(shatterHex: 0x05060a)))
        guard w > 0, !images.isEmpty else { return }
        let n = images.count
        let m = engine.mode
        let front = engine.current
        // A mend plays a break backwards from `reverseFrom` seconds, on the previous photo.
        let t = m == 2 ? M.reverseFrom * (1 - engine.mend) : engine.time
        let top = images[m == 2 ? (front - 1 + n) % n : front]
        let under = m == 0 ? nil : images[m == 2 ? front : (front + 1) % n]
        let reveal = M.smooth((t - M.fallAt) / (M.end - M.fallAt - 0.2))
        if let under {
            drawCover(&context, under, w, h, zoom: reduced ? 1 : 1 + M.revealZoom * (1 - reveal), alpha: 1)
            context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(.black.opacity(M.revealDim * (1 - reveal))))
        }
        guard let top else { return }
        guard m != 0, let s = engine.shatter else {
            drawCover(&context, top, w, h, zoom: 1, alpha: 1)
            return
        }
        // The pane shakes once on impact.
        let shake = reduced ? 0 : M.shake * exp(-t * 16) * sin(t * 70)
        var pane = context
        pane.translateBy(x: shake, y: shake * 0.4)
        let fade = 1 - M.smooth((t - (M.end - M.fadeTail)) / M.fadeTail)
        // While mending, the shards fade in as they come.
        let enter = m == 2 ? M.clamp01(engine.mend / 0.18) : 1
        if reduced || t < M.fallAt {
            // Reduced motion: nothing falls; the cracked photo fades to the next one.
            let alpha = reduced ? 1 - M.smooth((t - M.fallAt) / 0.6) : 1
            drawCover(&pane, top, w, h, zoom: 1, alpha: alpha * enter)
        } else {
            drawShards(&pane, s, top, w, h, t: t, alpha: fade * enter)
        }
        if t < M.fallAt + (reduced ? 0.6 : 0) {
            let reach = hypot(w, h) * M.smooth(t / M.crackTime)
            drawCracks(&pane, s, reach: reach, alpha: enter * (reduced ? 1 - M.smooth((t - M.fallAt) / 0.6) : 1))
        }
    }

    /// The rectangle an image fills to cover w × h, centred, grown by `zoom`.
    private static func cover(_ image: UIImage, _ w: Double, _ h: Double, zoom: Double) -> CGRect {
        let iw = Double(image.size.width)
        let ih = Double(image.size.height)
        let s = max(w / iw, h / ih) * zoom
        return CGRect(x: (w - iw * s) / 2, y: (h - ih * s) / 2, width: iw * s, height: ih * s)
    }

    private static func drawCover(_ context: inout GraphicsContext, _ image: UIImage, _ w: Double, _ h: Double, zoom: Double, alpha: Double) {
        guard alpha > 0 else { return }
        var c = context
        c.opacity = alpha
        c.draw(Image(uiImage: image), in: cover(image, w, h, zoom: zoom))
    }

    /// The cracks, drawn out to `reach` pt from the impact.
    private static func drawCracks(_ context: inout GraphicsContext, _ s: ShatterGlassTransitionShatter, reach: Double, alpha: Double) {
        guard alpha > 0, reach > 0 else { return }
        var path = Path()
        for i in s.rays.indices {
            let ray = s.rays[i]
            let dist = s.reach[i]
            path.move(to: ray[0])
            for j in 1..<dist.count {
                if dist[j - 1] >= reach { break }
                let f = min(1, (reach - dist[j - 1]) / max(1, dist[j] - dist[j - 1]))
                let a = ray[j - 1]
                path.addLine(to: CGPoint(x: a.x + (ray[j].x - a.x) * f, y: a.y + (ray[j].y - a.y) * f))
            }
        }
        // The rings join neighbouring cracks once both have run past them.
        let count = s.rays.count
        for i in 0..<count {
            let a = s.rays[i]
            let b = s.rays[(i + 1) % count]
            for j in 1..<(s.reach[i].count - 2) {
                if s.reach[i][j] * 1.25 > reach || s.reach[(i + 1) % count][j] * 1.25 > reach { break }
                path.move(to: a[j])
                path.addLine(to: b[j])
            }
        }
        context.stroke(path, with: .color(.white.opacity(0.16 * alpha)), style: StrokeStyle(lineWidth: 3.6, lineCap: .round, lineJoin: .round))
        context.stroke(path, with: .color(.white.opacity(0.9 * alpha)), style: StrokeStyle(lineWidth: 1.1, lineCap: .round, lineJoin: .round))
        // A frosted bruise where the glass was struck.
        let r = 4 + 10 * M.clamp01(reach / 60)
        context.fill(Path(ellipseIn: CGRect(x: s.x - r, y: s.y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.4 * alpha)))
    }

    /// The shards of `image` at `t` seconds into the fall.
    private static func drawShards(
        _ context: inout GraphicsContext,
        _ s: ShatterGlassTransitionShatter,
        _ image: UIImage,
        _ w: Double,
        _ h: Double,
        t: Double,
        alpha: Double
    ) {
        guard alpha > 0 else { return }
        let resolved = context.resolve(Image(uiImage: image))
        let rect = cover(image, w, h, zoom: 1)
        let edge = StrokeStyle(lineWidth: 1.2, lineJoin: .round)
        for k in s.polys.indices {
            let tt = max(0, t - M.fallAt - s.delay[k])
            let x = s.cx[k] + s.vx[k] * tt
            let y = s.cy[k] + s.vy[k] * tt + 0.5 * M.gravity * tt * tt
            if y > h + 260 { continue }
            let spin = s.spin[k] * tt
            let flip = s.flip[k] * tt
            let facing = cos(flip)
            let grow = 1 + s.grow[k] * min(1, tt * 1.5)
            let poly = s.polys[k]
            var c = context
            c.translateBy(x: x, y: y)
            c.rotate(by: .radians(spin))
            // Turning about its own axis, a shard narrows to an edge and comes back mirrored.
            c.scaleBy(x: grow * (abs(facing) < 0.04 ? 0.04 * (facing < 0 ? -1 : 1) : facing), y: grow)
            c.translateBy(x: -s.cx[k], y: -s.cy[k])
            var piece = c
            piece.clip(to: poly)
            piece.opacity = alpha
            piece.draw(resolved, in: rect)
            // Darker the more it is turned away, as glass seen at a slant.
            let shade = alpha * (0.55 * (1 - abs(facing)) + (facing < 0 ? 0.18 : 0) + 0.08 * min(1, tt * 4))
            c.fill(poly, with: .color(.black.opacity(shade)))
            // Light glints along the edges when the shard turns through the light.
            let glint = pow(abs(sin(flip + spin * 0.5)), 6)
            c.stroke(poly, with: .color(.white.opacity(alpha * (tt > 0 ? 0.28 + 0.7 * glint : 0.75))), style: edge)
        }
    }
}

enum ShatterGlassTransitionFont {
    static func medium(_ size: CGFloat) -> Font { .custom("AvenirNext-Medium", fixedSize: size) }
    static func bold(_ size: CGFloat) -> Font { .custom("AvenirNext-Bold", fixedSize: size) }
}

// MARK: - Photos

/// Every photo decoded up front, so a break never waits for the next one.
@MainActor
final class ShatterGlassTransitionImages {
    private var images: [String: UIImage] = [:]

    func image(_ id: String) -> UIImage? { images[id] }

    func load(_ ids: [String]) async {
        await withTaskGroup(of: (String, UIImage?).self) { group in
            for id in ids where images[id] == nil {
                group.addTask {
                    let address = "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=\(ShatterGlassTransitionMetrics.photoPx)&q=80"
                    guard let url = URL(string: address),
                          let (data, _) = try? await URLSession.shared.data(from: url),
                          let image = UIImage(data: data)
                    else { return (id, nil) }
                    return (id, await image.byPreparingForDisplay() ?? image)
                }
            }
            for await (id, image) in group {
                if let image { images[id] = image }
            }
        }
    }
}

// MARK: - Samples

extension ShatterGlassTransitionPhoto {
    static let samples: [ShatterGlassTransitionPhoto] = [
        .init(id: "photo-1711560705654-325ba859c38b", title: "Ember Nursery", line: "A cloud of newborn stars, 7,500 light-years out"),
        .init(id: "photo-1543007168-5fa9b3c5f5fb", title: "Sea Nettle", line: "Drifting twelve metres down, open Pacific"),
        .init(id: "photo-1759179075678-d65678f1050e", title: "Mercury Fold", line: "Liquid chrome, study no. 4"),
        .init(id: "photo-1628944682084-831f35256163", title: "Mauve Stinger", line: "Night swarm off the Atlantic shelf"),
    ]
}

extension Color {
    /// A colour from a 0xRRGGBB number.
    init(shatterHex hex: UInt32, opacity: Double = 1) {
        self.init(
            .sRGB,
            red: Double((hex >> 16) & 0xff) / 255,
            green: Double((hex >> 8) & 0xff) / 255,
            blue: Double(hex & 0xff) / 255,
            opacity: opacity
        )
    }

    /// A colour from 0…255 channels and an opacity.
    static func shatterRGBA(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(.sRGB, red: r / 255, green: g / 255, blue: b / 255, opacity: a)
    }
}
