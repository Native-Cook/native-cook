import SwiftUI
import UIKit

struct WaterRipplePhotoItem {
    /// Unsplash photo id.
    var photo: String
    var title: String
    var place: String
}

/// A photo that behaves like water. Touches send rings out across it that bend the image and catch
/// the light; dragging leaves a trail of them. Swiping up sends a big swell from the finger that
/// carries in the next photo.
struct WaterRipplePhoto: View {
    /// Six photos, shown in turn.
    var photos: [WaterRipplePhotoItem] = WaterRipplePhotoItem.samples
    /// Drops rain at scattered spots and swipes through every photo, then starts over.
    var autoPlay = false
    /// Called with a photo's index once it has filled the screen.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The water lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = WaterRipplePhotoEngine()
    // Each photo arrives on its own; the screen comes in as soon as the first one has.
    @State private var images: [UIImage?] = Array(repeating: nil, count: WaterRipplePhotoMetrics.slots)
    // Resets when the system cancels the touch, which never reaches onEnded.
    @GestureState private var fingerDown = false

    private var count: Int { min(WaterRipplePhotoMetrics.slots, photos.count) }

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
        .task(id: photos.map(\.photo)) { await loadPhotos() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                stage: WaterRipplePhotoStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                settings: WaterRipplePhotoSettings(count: count, autoPlay: autoPlay && !reduceMotion, reducedMotion: reduceMotion, onChange: onChange)
            )
            WaterRipplePhotoScene(frame: frame, photos: Array(photos.prefix(count)), images: images, still: reduceMotion)
                .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height)
        .background(Color(hex: 0x0d0c11))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($fingerDown) { _, down, _ in down = true }
                .onChanged { value in engine.touchChanged(start: value.startLocation, location: value.location) }
                .onEnded { value in engine.touchEnded(location: value.location, translation: value.translation, velocity: value.velocity) }
        )
        .onChange(of: fingerDown) { _, down in
            // A touch the system took away lets go of the water (after onEnded, which wins if it fired).
            guard !down else { return }
            let engine = engine
            Task { @MainActor in engine.touchCancelled() }
        }
        .accessibilityElement()
        .accessibilityLabel("Photo on water")
        .accessibilityHint("Touch to make ripples, swipe up for the next photo")
        .accessibilityAdjustableAction { direction in
            if direction == .increment { engine.nextPhoto() }
        }
    }

    private func loadPhotos() async {
        let list = photos
        let slots = WaterRipplePhotoMetrics.slots
        images = Array(repeating: nil, count: slots)
        engine.photosReset()
        guard !list.isEmpty else { return }
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for i in 0..<slots {
                group.addTask { (i, await WaterRipplePhotoLoader.load(waterRipplePhotoAddress(list[i % list.count].photo))) }
            }
            for await (i, image) in group {
                guard let image, !Task.isCancelled else { continue }
                images[i] = image
                engine.photoArrived(i)
            }
        }
    }
}

/// Everything one frame draws: the photo through the water, the scrims and the type.
private struct WaterRipplePhotoScene: View {
    let frame: WaterRipplePhotoFrame
    let photos: [WaterRipplePhotoItem]
    let images: [UIImage?]
    let still: Bool

    private static let scrim = Color(red: 8 / 255, green: 6 / 255, blue: 14 / 255)

    var body: some View {
        let stage = frame.stage
        ZStack(alignment: .topLeading) {
            if let first = images.first ?? nil {
                // The first photo fades in while settling to full size.
                let v = frame.photoSettle
                water(first)
                    .scaleEffect(still ? 1 : WaterRipplePhotoMetrics.photoScale + (1 - WaterRipplePhotoMetrics.photoScale) * v)
                    .opacity(frame.photoIn)
            }
            LinearGradient(colors: [Self.scrim.opacity(0.42), Self.scrim.opacity(0)], startPoint: .top, endPoint: .bottom)
                .frame(width: stage.width, height: stage.top + 110)
            LinearGradient(colors: [Self.scrim.opacity(0), Self.scrim.opacity(0.55)], startPoint: .top, endPoint: .bottom)
                .frame(width: stage.width, height: stage.bottom + 260)
                .offset(y: stage.height - stage.bottom - 260)
            header
                .modifier(WaterRipplePhotoEntrance(progress: frame.textIn[0], still: still))
            captions
                .modifier(WaterRipplePhotoEntrance(progress: frame.textIn[1], still: still))
            Text("Touch the water · swipe up for the next")
                .font(.custom("AvenirNext-Medium", fixedSize: 13))
                .foregroundStyle(.white.opacity(0.6))
                .frame(width: stage.width, height: max(0, stage.height - stage.bottom - 24), alignment: .bottom)
                .modifier(WaterRipplePhotoEntrance(progress: frame.textIn[2], still: still))
        }
        .frame(width: stage.width, height: stage.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    /// The shader as a layer effect over the current photo, cropped to fill the screen; the next
    /// photo goes to the shader as a texture with the rectangle it fills.
    private func water(_ first: UIImage) -> some View {
        let stage = frame.stage
        let screen = CGRect(x: 0, y: 0, width: stage.width, height: stage.height)
        // A photo still on its way stands in as the first one; the swell waits for it.
        let current = images.isEmpty ? first : (images[frame.current % images.count] ?? first)
        let next = images.isEmpty ? first : (images[frame.next % images.count] ?? first)
        let nextRect = Self.cover(next, screen)
        let shader = ShaderLibrary.waterRipplePhotoWater(
            .float2(screen.size),
            .float(frame.time),
            .floatArray(frame.ripples),
            .image(Image(uiImage: next)),
            .float4(nextRect.minX, nextRect.minY, nextRect.width, nextRect.height),
            .float2(frame.origin),
            .float(frame.swell),
            .float(frame.mixing),
            .float(still ? 1 : 0)
        )
        return Canvas { context, _ in
            context.draw(Image(uiImage: current), in: Self.cover(current, screen))
        }
        .frame(width: stage.width, height: stage.height)
        .layerEffect(shader, maxSampleOffset: .zero)
    }

    /// Where `image` lies when it is scaled to cover `rect`, centred on it.
    private static func cover(_ image: UIImage, _ rect: CGRect) -> CGRect {
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let w = image.size.width * scale
        let h = image.size.height * scale
        return CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h)
    }

    private func brand(_ text: String) -> some View {
        Text(text)
            .font(.custom("AvenirNext-DemiBold", fixedSize: 12))
            .monospacedDigit()
            .kerning(2.4)
            .foregroundStyle(.white.opacity(0.86))
            .fixedSize()
    }

    private var header: some View {
        let stage = frame.stage
        let width = max(0, stage.width - 48)
        return ZStack(alignment: .topLeading) {
            brand("STILL WATER")
            ForEach(photos.indices, id: \.self) { i in
                brand(String(format: "%02d / %02d", i + 1, photos.count))
                    .frame(width: width, alignment: .trailing)
                    .opacity(frame.captions[i].opacity)
                    .offset(y: frame.captions[i].offset)
            }
        }
        .frame(width: width, height: 18, alignment: .topLeading)
        .offset(x: 24, y: stage.top + 16)
    }

    private var captions: some View {
        let stage = frame.stage
        let width = max(0, stage.width - 48)
        let titleFont = UIFont(name: "AvenirNext-Medium", size: 38)?.lineHeight ?? 46
        let titlePad = (42 - titleFont) / 2
        return ZStack(alignment: .bottomLeading) {
            ForEach(photos.indices, id: \.self) { i in
                VStack(alignment: .leading, spacing: 0) {
                    Text(photos[i].place.uppercased())
                        .font(.custom("AvenirNext-DemiBold", fixedSize: 12))
                        .kerning(2)
                        .foregroundStyle(.white.opacity(0.78))
                        .padding(.bottom, 6)
                    Text(photos[i].title)
                        .font(.custom("AvenirNext-Medium", fixedSize: 38))
                        .kerning(-0.8)
                        .foregroundStyle(.white)
                        .padding(.vertical, titlePad)
                }
                .frame(width: width, alignment: .leading)
                .opacity(frame.captions[i].opacity)
                .offset(y: frame.captions[i].offset)
            }
        }
        .frame(width: width, height: 92, alignment: .bottomLeading)
        .offset(x: 24, y: stage.height - stage.bottom - 74 - 92)
    }
}

/// Type over the photo, hidden until the photo arrives, then fading and rising in.
private struct WaterRipplePhotoEntrance: ViewModifier {
    let progress: Double
    let still: Bool

    func body(content: Content) -> some View {
        content
            .opacity(progress)
            .offset(y: still ? 0 : (1 - progress) * WaterRipplePhotoMetrics.textRise)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum WaterRipplePhotoLoader {
    private static let cache = NSCache<NSString, UIImage>()

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

// The photo fills a phone screen, about 400 pt wide; 1200 px covers that at 3x.
private func waterRipplePhotoAddress(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1200&q=80"
}

// MARK: - Samples

extension WaterRipplePhotoItem {
    static let samples: [WaterRipplePhotoItem] = [
        .init(photo: "photo-1625934036482-2dccff707564", title: "Lavender, after rain", place: "Sorell Plateau · July"),
        .init(photo: "photo-1493589976221-c2357c31ad77", title: "Blossom hour", place: "Hanaoka Canal · April"),
        .init(photo: "photo-1593715857983-5531aa640471", title: "Rows of hum", place: "Valmont Ridge · June"),
        .init(photo: "photo-1522748906645-95d8adfd52c7", title: "Petal weather", place: "Old Mill Lane · April"),
        .init(photo: "photo-1625215393266-d09797f4a441", title: "The purple hour", place: "Linden Farm · August"),
        .init(photo: "photo-1538943186303-104afadcbb16", title: "Pink quiet", place: "Kiyose Garden · March"),
    ]
}
