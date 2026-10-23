import SwiftUI
import UIKit

struct GlassRodOnboardingPage {
    /// Unsplash photo id.
    var photo: String
    var label: String
    var title: (String, String)
    var body: (String, String)
    /// Drawn while the photo loads, or if it fails.
    var tone: Color
}

/// An onboarding whose pages change under a glass rod. The rod lies across the screen: drag it and
/// it rolls over the page, showing the next one behind it and the seam between the two flipped and
/// squeezed in its glass, with colour fringes, highlights and a soft shadow. It follows the finger,
/// leans as it moves and tilts as the finger rises or falls; let go and it springs on to the next
/// page or back with the finger's speed. The page dots stretch with the paging, and the button
/// reads "Get started" on the last page.
struct GlassRodOnboarding: View {
    /// Pages through the onboarding with the glass rod, presses the button and starts over.
    var autoPlay = false
    /// Called when "Get started" is pressed on the last page.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The paging lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = GlassRodOnboardingEngine(count: GlassRodOnboardingPage.pages.count)
    @State private var images: [UIImage?] = Array(repeating: nil, count: GlassRodOnboardingPage.pages.count)
    // False again once the finger is gone, also when the system takes the touch away.
    @GestureState private var touching = false

    var body: some View {
        // The block draws under the system bars and keeps its controls clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            TimelineView(.animation) { timeline in
                let frame = engine.step(to: timeline.date, size: size, autoPlay: autoPlay, reduced: reduceMotion)
                GlassRodOnboardingScene(
                    frame: frame,
                    layout: GlassRodOnboardingLayout(size: size, top: insets.top, bottom: insets.bottom),
                    images: images,
                    reduced: reduceMotion,
                    engine: engine,
                    touching: $touching,
                    onDone: onDone
                )
            }
            .onChange(of: touching) { _, now in
                if !now { engine.touchCancelled() }
            }
            .frame(width: size.width, height: size.height)
            .offset(x: -insets.leading, y: -insets.top)
        }
        .background(Color(hex: 0x0d0907))
        .task { await loadPhotos() }
    }

    private func loadPhotos() async {
        let pages = GlassRodOnboardingPage.pages
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for i in pages.indices {
                group.addTask { (i, await GlassRodOnboardingLoader.load(glassRodOnboardingAddress(pages[i].photo))) }
            }
            for await (i, image) in group where images.indices.contains(i) {
                images[i] = image
            }
        }
    }
}

/// Where everything sits, in pt, from the bottom up: the button, the dots, the copy.
struct GlassRodOnboardingLayout {
    let width: Double
    let height: Double
    let top: Double
    let margin: Double = 28
    let buttonTop: Double
    let dotsY: Double
    let label: Double
    let title: (Double, Double)
    let body: (Double, Double)
    let titleSize: Double

    init(size: CGSize, top: CGFloat, bottom: CGFloat) {
        width = Double(size.width)
        height = Double(size.height)
        self.top = Double(top)
        buttonTop = height - max(Double(bottom), 16) - 16 - 56
        dotsY = buttonTop - 30
        let body1 = dotsY - 36
        let body0 = body1 - 23
        titleSize = min(46, (width - 56) / 7.2)
        let title1 = body0 - 38
        let title0 = title1 - titleSize * 1.04
        label = title0 - titleSize - 12
        title = (title0, title1)
        body = (body0, body1)
    }
}

/// One frame: the pages seen through the rod, then the controls on top.
private struct GlassRodOnboardingScene: View {
    let frame: GlassRodOnboardingFrame
    let layout: GlassRodOnboardingLayout
    let images: [UIImage?]
    let reduced: Bool
    let engine: GlassRodOnboardingEngine
    let touching: GestureState<Bool>
    let onDone: (() -> Void)?

    private typealias M = GlassRodOnboardingMetrics
    private typealias X = GlassRodOnboardingMath
    private static let cream = Color(hex: 0xf6efe6)
    private static let ink = Color(hex: 0x0d0907)
    private var pages: [GlassRodOnboardingPage] { GlassRodOnboardingPage.pages }

    var body: some View {
        ZStack(alignment: .topLeading) {
            glass
            header
            dots
            button
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
        .clipped()
    }

    // MARK: Pages

    private var glass: some View {
        let shader = ShaderLibrary.glassRodOnboardingRod(
            .float2(CGSize(width: layout.width, height: layout.height)),
            .float2(CGPoint(x: frame.rodX, y: frame.pivotY)),
            .float(frame.angle),
            .float(M.rodRadius),
            .float(frame.chroma),
            .float(1)
        )
        return Canvas { context, _ in
            context.fill(Path(CGRect(x: 0, y: 0, width: layout.width, height: layout.height)), with: .color(Self.ink))
            for i in pages.indices { drawPage(i, in: &context) }
        }
        .frame(width: layout.width, height: layout.height)
        .layerEffect(shader, maxSampleOffset: CGSize(width: 110, height: 40))
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating(touching) { _, state, _ in state = true }
                .onChanged { engine.touchChanged($0) }
                .onEnded { engine.touchEnded($0) }
        )
        .accessibilityElement()
        .accessibilityLabel("Onboarding pages")
        .accessibilityHint("Swipe left or right to change page")
        .accessibilityAdjustableAction { direction in
            engine.step(direction == .increment ? 1 : -1)
        }
    }

    /// The two half-planes either side of the rod's centre line.
    private func cut(left: Bool) -> Path {
        let a = frame.angle
        let nx = cos(a)
        let ny = sin(a)
        let far = (layout.height + layout.width) * 3
        let cx = frame.rodX
        let cy = frame.pivotY
        let side = left ? -1.0 : 1.0
        var path = Path()
        path.move(to: CGPoint(x: cx + ny * far, y: cy - nx * far))
        path.addLine(to: CGPoint(x: cx - ny * far, y: cy + nx * far))
        path.addLine(to: CGPoint(x: cx - ny * far + side * nx * far, y: cy + nx * far + side * ny * far))
        path.addLine(to: CGPoint(x: cx + ny * far + side * nx * far, y: cy - nx * far + side * ny * far))
        path.closeSubpath()
        return path
    }

    /// One page: its photo, filling the screen, and its words. It shows on the rod's left when it is
    /// the page being left, on its right when it is the page coming in, and nowhere otherwise.
    private func drawPage(_ index: Int, in context: inout GraphicsContext) {
        let seg = X.segment(frame.progress, wrap: frame.wrap, count: pages.count)
        let side: Int
        if index == seg.a && index == seg.b { side = 2 } else if index == seg.a { side = -1 } else if index == seg.b { side = 1 } else { return }
        let page = pages[index]
        let w = layout.width
        let h = layout.height
        // The page being left drifts left behind the rod, the one coming in drifts in from the right
        // and settles from a little closer.
        let travel = (reduced || side == 2) ? 0 : (side < 0 ? -seg.f : 1 - seg.f)
        context.drawLayer { ctx in
            if !reduced && side != 2 { ctx.clip(to: cut(left: side < 0)) }
            // Under reduced motion the pages crossfade instead: the incoming one fades in over the
            // other, or, when it is drawn underneath (the wrap round to the first page), the
            // outgoing one fades out.
            if reduced && side == 1 && seg.b > seg.a { ctx.opacity = seg.f }
            if reduced && side == -1 && seg.b < seg.a { ctx.opacity = 1 - seg.f }

            var photo = ctx
            let zoom = travel > 0 ? 1 + (M.incomingScale - 1) * travel : 1
            photo.translateBy(x: w / 2 + travel * M.photoShift * w, y: h / 2)
            photo.scaleBy(x: zoom, y: zoom)
            photo.translateBy(x: -w / 2, y: -h / 2)
            let screen = CGRect(x: 0, y: 0, width: w, height: h)
            photo.fill(Path(screen), with: .color(page.tone))
            if let image = images[index] {
                photo.draw(Image(uiImage: image), in: Self.cover(image, screen))
            }

            // The scrim belongs to the page, so the words keep their contrast through the glass.
            let low = CGRect(x: 0, y: h * 0.42, width: w, height: h * 0.58)
            ctx.fill(Path(low), with: .linearGradient(
                Gradient(stops: [
                    .init(color: Self.ink.opacity(0), location: 0),
                    .init(color: Self.ink.opacity(0.72), location: 0.5),
                    .init(color: Self.ink.opacity(0.95), location: 1),
                ]),
                startPoint: CGPoint(x: 0, y: low.minY), endPoint: CGPoint(x: 0, y: low.maxY)
            ))
            ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: 150)), with: .linearGradient(
                Gradient(colors: [Self.ink.opacity(0.4), Self.ink.opacity(0)]),
                startPoint: .zero, endPoint: CGPoint(x: 0, y: 150)
            ))

            var words = ctx
            words.translateBy(x: travel * M.textShift * w, y: 0)
            let m = layout.margin
            Self.text(Text(page.label).font(.custom("AvenirNext-Bold", fixedSize: 12)).kerning(2.4).foregroundColor(Color(hex: 0xe9b98f)), x: m, baseline: layout.label, in: &words)
            let titleFont = Font.custom("AvenirNext-Bold", fixedSize: layout.titleSize)
            Self.text(Text(page.title.0).font(titleFont).foregroundColor(Self.cream), x: m - 2, baseline: layout.title.0, in: &words)
            Self.text(Text(page.title.1).font(titleFont).foregroundColor(Self.cream), x: m - 2, baseline: layout.title.1, in: &words)
            let bodyFont = Font.custom("AvenirNext-Medium", fixedSize: 16)
            Self.text(Text(page.body.0).font(bodyFont).foregroundColor(Self.cream.opacity(0.77)), x: m, baseline: layout.body.0, in: &words)
            Self.text(Text(page.body.1).font(bodyFont).foregroundColor(Self.cream.opacity(0.77)), x: m, baseline: layout.body.1, in: &words)
        }
    }

    /// Draws one line of text with its baseline at `baseline`, like Skia's text.
    private static func text(_ text: Text, x: Double, baseline: Double, in context: inout GraphicsContext) {
        let resolved = context.resolve(text)
        let room = CGSize(width: 2000, height: 400)
        let top = baseline - resolved.firstBaseline(in: room)
        context.draw(resolved, at: CGPoint(x: x, y: top), anchor: .topLeading)
    }

    /// Where `image` lies when it is scaled to cover `rect`, centred on it.
    private static func cover(_ image: UIImage, _ rect: CGRect) -> CGRect {
        guard image.size.width > 0, image.size.height > 0 else { return rect }
        let scale = max(rect.width / image.size.width, rect.height / image.size.height)
        let w = image.size.width * scale
        let h = image.size.height * scale
        return CGRect(x: rect.midX - w / 2, y: rect.midY - h / 2, width: w, height: h)
    }

    // MARK: Controls

    private var lastness: Double { X.smooth(1 - X.ringDistance(frame.progress, pages.count - 1, count: pages.count)) }

    private var header: some View {
        HStack {
            HStack(spacing: 8) {
                GlassRodOnboardingMark()
                    .frame(width: 22, height: 22)
                Text("Sandline")
                    .font(.custom("AvenirNext-Bold", fixedSize: 17))
                    .kerning(-0.2)
                    .foregroundStyle(Self.cream)
            }
            .allowsHitTesting(false)
            Spacer()
            Button {
                engine.skip()
            } label: {
                Text("Skip")
                    .font(.custom("AvenirNext-DemiBold", fixedSize: 15))
                    .foregroundStyle(Self.cream.opacity(0.85))
                    .padding(.horizontal, 10)
                    .padding(.vertical, 6)
                    .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .opacity(1 - lastness)
            .allowsHitTesting(lastness < 0.5)
        }
        .frame(width: layout.width - layout.margin * 2 + 10)
        .offset(x: layout.margin, y: layout.top + 10)
    }

    private var dots: some View {
        let count = pages.count
        let weights = (0..<count).map { X.smooth(1 - X.ringDistance(frame.progress, $0, count: count)) }
        return ZStack(alignment: .topLeading) {
            ForEach(0..<count, id: \.self) { i in
                // Every dot before this one may be stretched too, so its x is the sum of their widths.
                let x = (0..<i).reduce(0.0) { $0 + M.dot + (M.dotActive - M.dot) * weights[$1] + M.dotGap }
                Capsule()
                    .fill(Self.cream)
                    .frame(width: M.dot + (M.dotActive - M.dot) * weights[i], height: M.dot)
                    .opacity(0.38 + 0.62 * weights[i])
                    .offset(x: x)
            }
        }
        .frame(width: M.dotActive + (M.dot + M.dotGap) * Double(count - 1), height: M.dot, alignment: .topLeading)
        .offset(x: layout.margin, y: layout.dotsY - M.dot / 2)
        .allowsHitTesting(false)
    }

    private var button: some View {
        let last = lastness
        return Button {
            if engine.isOnLastPage { onDone?() }
            engine.buttonTapped()
        } label: {
            ZStack {
                HStack(spacing: 8) {
                    Text("Continue")
                    GlassRodOnboardingArrow()
                        .stroke(Self.ink, style: StrokeStyle(lineWidth: 2.1, lineCap: .round, lineJoin: .round))
                        .frame(width: 18, height: 18)
                }
                // The labels hand over without overlapping: "Continue" leaves in the first half,
                // "Get started" arrives in the second.
                .opacity(1 - X.smooth(last * 2))
                .offset(y: -8 * last)
                // Only the label on show is read out, as the button's own.
                .accessibilityHidden(last > 0.5)
                Text("Get started")
                    .opacity(X.smooth(last * 2 - 1))
                    .offset(y: 8 * (1 - last))
                    .accessibilityHidden(last <= 0.5)
            }
            .font(.custom("AvenirNext-Bold", fixedSize: 17))
            .foregroundStyle(Self.ink)
            .frame(width: layout.width - layout.margin * 2, height: 56)
            .background(Capsule().fill(Self.cream))
            .contentShape(Capsule())
        }
        .buttonStyle(GlassRodOnboardingPressStyle(extra: frame.press))
        .accessibilityLabel(last > 0.5 ? "Get started" : "Continue")
        .offset(x: layout.margin, y: layout.buttonTop)
    }
}

/// Presses to 96.5%, on top of the autoPlay's own press.
private struct GlassRodOnboardingPressStyle: ButtonStyle {
    let extra: Double

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(1 - 0.035 * max(extra, configuration.isPressed ? 1 : 0))
            .animation(.easeOut(duration: configuration.isPressed ? 0.11 : 0.22), value: configuration.isPressed)
    }
}

/// The app's mark: two dune ridges.
private struct GlassRodOnboardingMark: View {
    var body: some View {
        Canvas { context, size in
            let k = size.width / 22
            var back = Path()
            back.move(to: CGPoint(x: 2 * k, y: 15.5 * k))
            back.addCurve(to: CGPoint(x: 12 * k, y: 7.5 * k), control1: CGPoint(x: 5.2 * k, y: 10.1 * k), control2: CGPoint(x: 8.6 * k, y: 7.5 * k))
            back.addCurve(to: CGPoint(x: 20 * k, y: 13.5 * k), control1: CGPoint(x: 15.1 * k, y: 7.5 * k), control2: CGPoint(x: 17.7 * k, y: 9.5 * k))
            var front = Path()
            front.move(to: CGPoint(x: 5.5 * k, y: 18.5 * k))
            front.addCurve(to: CGPoint(x: 12.7 * k, y: 14.7 * k), control1: CGPoint(x: 7.9 * k, y: 15.9 * k), control2: CGPoint(x: 10.3 * k, y: 14.7 * k))
            front.addCurve(to: CGPoint(x: 17.8 * k, y: 16.7 * k), control1: CGPoint(x: 14.6 * k, y: 14.7 * k), control2: CGPoint(x: 16.3 * k, y: 15.4 * k))
            let style = StrokeStyle(lineWidth: 2.2 * k, lineCap: .round)
            context.stroke(back, with: .color(Color(hex: 0xf6efe6)), style: style)
            context.stroke(front, with: .color(Color(hex: 0xe9b98f)), style: style)
        }
    }
}

/// An arrow pointing right, in an 18 pt box.
private struct GlassRodOnboardingArrow: Shape {
    func path(in rect: CGRect) -> Path {
        let k = rect.width / 18
        var path = Path()
        path.move(to: CGPoint(x: 3 * k, y: 9 * k))
        path.addLine(to: CGPoint(x: 14.2 * k, y: 9 * k))
        path.move(to: CGPoint(x: 9.6 * k, y: 4.2 * k))
        path.addLine(to: CGPoint(x: 14.4 * k, y: 9 * k))
        path.addLine(to: CGPoint(x: 9.6 * k, y: 13.8 * k))
        return path
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum GlassRodOnboardingLoader {
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

// The photos fill the screen, about 400 pt wide; 1200 px covers that at 3x.
private func glassRodOnboardingAddress(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1200&q=80"
}

extension GlassRodOnboardingPage {
    static let pages: [GlassRodOnboardingPage] = [
        .init(
            photo: "photo-1511860810434-a92f84c6f01e",
            label: "DUNES · 01",
            title: ("Read the", "wind."),
            body: ("Live dune maps that shift with the", "sand, redrawn every dawn."),
            tone: Color(hex: 0xb8643a)
        ),
        .init(
            photo: "photo-1505521377774-103a8cc2f735",
            label: "CANYONS · 02",
            title: ("Walk into", "the light."),
            body: ("Slot canyons rated by the hour the", "sun reaches the floor."),
            tone: Color(hex: 0x8c3f22)
        ),
        .init(
            photo: "photo-1629659343117-237c364e69ed",
            label: "NIGHT · 03",
            title: ("Sleep under", "the stars."),
            body: ("Camps far from any road, with the", "moon and wind for tonight."),
            tone: Color(hex: 0x1d2a44)
        ),
    ]
}
