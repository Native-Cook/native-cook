import SwiftUI
import UIKit

struct CurtainSplitWelcomeScene {
    /// Two short lines: the headline is cut into the strips with the photo.
    var title: (String, String)
    var place: String
    var photo: String
}

/// A welcome screen of architecture photos cut into six vertical strips. Turning to the next scene
/// slides the strips away like blinds, alternately up and down and one after another, and the
/// headline, cut by the same strips, goes with them. Dragging up or down scrubs the turn.
struct CurtainSplitWelcome: View {
    /// Turns to the next scene every few seconds, round and round.
    var autoPlay = false
    /// Called with the scene that comes into view after a swipe or a tap on the arrow.
    var onChange: ((Int) -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The scenes' motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = CurtainSplitWelcomeEngine(count: CurtainSplitWelcomeScenes.all.count)
    // False again once the finger is gone, also when the system takes the touch away.
    @GestureState private var touching = false

    private typealias M = CurtainSplitWelcomeMetrics

    var body: some View {
        // The block draws under the system bars and keeps its chrome clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(layout: CurtainSplitWelcomeLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom))
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .task { await loadPhotos() }
    }

    private func stage(layout: CurtainSplitWelcomeLayout) -> some View {
        TimelineView(.animation) { timeline in
            let frame = engine.step(to: timeline.date, layout: layout, autoPlay: autoPlay, reduced: reduceMotion)
            ZStack(alignment: .topLeading) {
                Canvas(rendersAsynchronously: false) { context, _ in
                    CurtainSplitWelcomePicture(
                        position: frame.position,
                        layout: layout,
                        photos: engine.photos,
                        reduced: reduceMotion,
                        wrap: engine.wrap
                    ).draw(context)
                }
                .frame(width: layout.width, height: layout.height)
                chrome(layout: layout, press: frame.press)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .background(Color(hex: 0x1a1916))
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { engine.touchMoved($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .onChange(of: touching) { _, now in
            if !now { engine.touchLost() }
        }
        .onAppear { engine.onChange = onChange }
        .accessibilityElement()
        .accessibilityAddTraits(.allowsDirectInteraction)
        .accessibilityLabel(CurtainSplitWelcomeScenes.all.map { "\($0.title.0) \($0.title.1)" }.joined(separator: ", "))
    }

    /// The chrome stays put while the scenes turn behind it.
    private func chrome(layout l: CurtainSplitWelcomeLayout, press: Double) -> some View {
        let white = Color(hex: CurtainSplitWelcomeScenes.white)
        let hintHeight = UIFont(name: "AvenirNext-DemiBold", size: 11)?.lineHeight ?? 15
        return ZStack(alignment: .topLeading) {
            HStack(alignment: .top) {
                Text("/ 0\(CurtainSplitWelcomeScenes.all.count)")
                    .font(.custom("AvenirNext-Medium", fixedSize: 15))
                    .monospacedDigit()
                    .foregroundStyle(white.opacity(0.7))
                    .padding(.top, 4)
                Spacer(minLength: 0)
                Text("PLINTH")
                    .font(.custom("AvenirNext-Bold", fixedSize: 12))
                    .kerning(4.5)
                    .foregroundStyle(white)
                    .padding(.top, 6)
            }
            .frame(width: max(0, l.width - M.side * 2 - 74))
            .offset(x: M.side + 74, y: l.top + 30)

            Text("Swipe up")
                .font(.custom("AvenirNext-DemiBold", fixedSize: 11))
                .kerning(0.4)
                .foregroundStyle(white.opacity(0.7))
                .fixedSize()
                .frame(width: M.button)
                .offset(x: l.width - M.side - M.button, y: l.height - l.bottom - 30 - M.button - 14 - hintHeight)

            Circle()
                .fill(white)
                .overlay {
                    CurtainSplitWelcomeArrow()
                        .stroke(Color(hex: 0x1a1916), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
                        .frame(width: 22, height: 22)
                }
                .frame(width: M.button, height: M.button)
                .scaleEffect(1 - 0.07 * press)
                .offset(x: l.width - M.side - M.button, y: l.height - l.bottom - 30 - M.button)
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func loadPhotos() async {
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (index, scene) in CurtainSplitWelcomeScenes.all.enumerated() {
                group.addTask { (index, await CurtainSplitWelcomePhotos.load(scene.photo)) }
            }
            for await (index, image) in group {
                if let image { engine.photoArrived(image, at: index) }
            }
        }
    }
}

/// An arrow pointing up, drawn in a 22 pt box.
private struct CurtainSplitWelcomeArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 22
        var path = Path()
        path.move(to: CGPoint(x: 11 * s, y: 19 * s))
        path.addLine(to: CGPoint(x: 11 * s, y: 3 * s))
        path.move(to: CGPoint(x: 4.5 * s, y: 9.5 * s))
        path.addLine(to: CGPoint(x: 11 * s, y: 3 * s))
        path.addLine(to: CGPoint(x: 17.5 * s, y: 9.5 * s))
        return path.offsetBy(dx: rect.minX, dy: rect.minY)
    }
}

/// One frame of the scenes: the one underneath settling in, and the strips of the one on top.
private struct CurtainSplitWelcomePicture {
    let position: Double
    let layout: CurtainSplitWelcomeLayout
    let photos: [UIImage?]
    let reduced: Bool
    let wrap: (Int) -> Int

    private typealias M = CurtainSplitWelcomeMetrics

    func draw(_ context: GraphicsContext) {
        let l = layout
        let whole = floor(position)
        let phase = position - whole
        let top = wrap(Int(whole))
        let full = CGRect(x: 0, y: 0, width: l.width, height: l.height)

        // At rest the strips are closed up: the scene on top is all there is.
        if phase < 1e-6 {
            scene(context, top)
            return
        }

        var under = context
        let grow = reduced ? 1 : 1 + M.underScale * (1 - M.stripEase.value(at: phase))
        under.translateBy(x: l.width / 2, y: l.height / 2)
        under.scaleBy(x: grow, y: grow)
        under.translateBy(x: -l.width / 2, y: -l.height / 2)
        scene(under, wrap(Int(whole) + 1))
        context.fill(Path(full), with: .color(.black.opacity(M.underDim * (1 - phase))))

        // Under reduced motion the strips stay in place and the scene on top fades out instead.
        var upper = context
        if reduced { upper.opacity = 1 - phase }
        let column = l.width / CGFloat(M.strips)
        for k in 0..<M.strips {
            let travel = reduced ? 0 : M.stripAt(phase, k)
            let direction: CGFloat = k % 2 == 0 ? -1 : 1
            let gap = M.gap * sin(.pi * travel)
            var strip = upper
            // The clip travels with the strip, since it is set in the strip's moved coordinates.
            strip.translateBy(x: 0, y: direction * (l.height + 40) * travel)
            strip.clip(to: Path(CGRect(x: CGFloat(k) * column + gap / 2, y: 0, width: column - gap, height: l.height)))
            scene(strip, top)
            let shade = M.shade * sin(.pi / 2 * travel)
            if shade > 0 { strip.fill(Path(full), with: .color(.black.opacity(shade))) }
        }
    }

    /// What one scene draws: its photo, the shade for the text, the number, the place and the headline.
    private func scene(_ context: GraphicsContext, _ index: Int) {
        let l = layout
        let data = CurtainSplitWelcomeScenes.all[index]
        let full = CGRect(x: 0, y: 0, width: l.width, height: l.height)
        let white = Color(hex: CurtainSplitWelcomeScenes.white)
        context.fill(Path(full), with: .color(Color(hex: 0x1a1916)))
        if let photo = photos[index] {
            var art = context
            art.clip(to: Path(full))
            let fit = max(l.width / photo.size.width, l.height / photo.size.height)
            let w = photo.size.width * fit
            let h = photo.size.height * fit
            art.draw(Image(uiImage: photo), in: CGRect(x: (l.width - w) / 2, y: (l.height - h) / 2, width: w, height: h))
        }
        // Shade at the top for the number and at the bottom for the headline.
        context.fill(
            Path(full),
            with: .linearGradient(
                Gradient(stops: [
                    .init(color: .black.opacity(Double(0x73) / 255), location: 0),
                    .init(color: .black.opacity(0), location: 0.26),
                    .init(color: .black.opacity(0), location: 0.48),
                    .init(color: .black.opacity(Double(0xc4) / 255), location: 1),
                ]),
                startPoint: .zero,
                endPoint: CGPoint(x: 0, y: l.height)
            )
        )
        text(context, Text("0\(index + 1)").font(.custom("AvenirNext-Medium", fixedSize: M.indexSize)).foregroundColor(white), x: M.side - 3, baseline: l.indexY)
        text(
            context,
            Text(data.place.uppercased()).font(.custom("AvenirNext-Bold", fixedSize: M.placeSize)).tracking(M.placeTracking).foregroundColor(white.opacity(0.78)),
            x: M.side,
            baseline: l.placeY
        )
        let title = Font.custom("AvenirNext-DemiBold", fixedSize: M.titleSize)
        text(context, Text(data.title.0).font(title).foregroundColor(white), x: M.side - 2, baseline: l.line1Y)
        text(context, Text(data.title.1).font(title).foregroundColor(white), x: M.side - 2, baseline: l.line2Y)
    }

    /// Text whose baseline starts at (x, baseline).
    private func text(_ context: GraphicsContext, _ text: Text, x: CGFloat, baseline: CGFloat) {
        let resolved = context.resolve(text)
        let ascent = resolved.firstBaseline(in: CGSize(width: 2000, height: 2000))
        context.draw(resolved, at: CGPoint(x: x, y: baseline - ascent), anchor: .topLeading)
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum CurtainSplitWelcomePhotos {
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

// MARK: - Scenes

enum CurtainSplitWelcomeScenes {
    static func photo(_ id: String) -> String {
        "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1200&q=80"
    }

    static let white: UInt32 = 0xf6f4ef

    static let all: [CurtainSplitWelcomeScene] = [
        .init(title: ("Stay inside", "the drawing."), place: "Casa Ondara · Porto", photo: photo("photo-1496865534669-25ec2a3a0fd3")),
        .init(title: ("Concrete,", "softened."), place: "Halle Nord · Basel", photo: photo("photo-1737442981890-0a87ef3ce9f5")),
        .init(title: ("Light keeps", "the hours."), place: "Torre Luma · Valencia", photo: photo("photo-1522404419647-18cb51cc5c7a")),
        .init(title: ("Rooms with", "a spine."), place: "Maison Gris · Lyon", photo: photo("photo-1536924491042-b0466800ce46")),
        .init(title: ("Find your", "angle."), place: "Studio Okra · Kyoto", photo: photo("photo-1543067361-9bf996edf6ff")),
    ]
}
