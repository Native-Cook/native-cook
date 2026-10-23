import SwiftUI
import UIKit

struct LiquidSwipeOnboardingPage {
    /// Unsplash photo id, shown in the arch.
    var photo: String
    /// The page's colour, and the colour of its type.
    var color: Color
    var ink: Color
    /// The headline, one string per line.
    var title: [String]
    var body: String
}

/// Onboarding pages in bold colours that pour over each other like liquid. A tab of the next page
/// sticks out of the right edge; dragging it pulls a smooth wave of that page after the finger,
/// and past a third of the width the wave sweeps across and fills the screen with a springy
/// overshoot. The previous page waits as a tab on the left.
struct LiquidSwipeOnboarding: View {
    var pages: [LiquidSwipeOnboardingPage] = LiquidSwipeOnboardingPage.samples
    /// The label of the button on the last page.
    var action = "Pick a volcano"
    /// Swipes through every page and back again, then rests and starts over.
    var autoPlay = false
    /// Called with a page's index once it fills the screen.
    var onChange: ((Int) -> Void)? = nil
    /// Called when the button on the last page is tapped.
    var onDone: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    // The pages' motion lives in a plain object stepped once a frame, like the RN frame callback.
    @State private var engine = LiquidSwipeOnboardingEngine()
    // Each photo arrives on its own; until then its arch shows a faint wash.
    @State private var images: [Int: UIImage] = [:]
    // False again once the finger is gone, also when the system takes the touch away.
    @GestureState private var touching = false

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
        .task(id: pages.map(\.photo)) { await loadPhotos() }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let layout = LiquidSwipeOnboardingLayout(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom, pages: pages, action: action)
        return TimelineView(.animation) { timeline in
            let frame = engine.step(
                to: timeline.date,
                stage: LiquidSwipeOnboardingStage(width: size.width, height: size.height, top: insets.top, bottom: insets.bottom),
                settings: LiquidSwipeOnboardingSettings(
                    count: pages.count,
                    autoPlay: autoPlay && !reduceMotion,
                    reducedMotion: reduceMotion,
                    button: layout.button,
                    onChange: onChange,
                    onDone: onDone
                )
            )
            Canvas { context, _ in
                LiquidSwipeOnboardingPainter(layout: layout, pages: pages, images: images, action: action).draw(frame, in: context)
            }
            .accessibilityHidden(true)
        }
        .frame(width: size.width, height: size.height)
        .background(pages.first?.color ?? .black)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { value in engine.touchChanged(value) }
                .onEnded { value in engine.touchEnded(value) }
        )
        .onChange(of: touching) { _, now in
            if !now { engine.touchLost() }
        }
        .accessibilityElement()
        .accessibilityLabel(pages.map { $0.title.joined(separator: " ") }.joined(separator: ". "))
        .accessibilityAdjustableAction { direction in
            engine.page(direction == .increment ? 1 : -1)
        }
    }

    private func loadPhotos() async {
        await withTaskGroup(of: (Int, UIImage?).self) { group in
            for (i, page) in pages.enumerated() {
                group.addTask { (i, await LiquidSwipeOnboardingPhotos.load(liquidSwipeOnboardingPhoto(page.photo))) }
            }
            for await (i, image) in group {
                if let image { images[i] = image }
            }
        }
    }
}

/// Where everything on a page sits, measured once for a screen.
struct LiquidSwipeOnboardingLayout {
    var width: Double
    var height: Double
    var top: Double
    var bottom: Double
    var arch: CGRect
    var button: CGRect
    /// Each page's copy, broken into lines that fit.
    var bodies: [[String]]

    private typealias M = LiquidSwipeOnboardingMetrics

    init(width: Double, height: Double, top: Double, bottom: Double, pages: [LiquidSwipeOnboardingPage], action: String) {
        self.width = width
        self.height = height
        self.top = top
        self.bottom = bottom
        arch = CGRect(x: M.side, y: top + M.photoTop, width: width - M.side * 2, height: height * M.photoHeight)
        let w = Self.measure(action, LiquidSwipeOnboardingType.buttonUI) + 66
        let cy = height - bottom - M.tabAbove
        button = CGRect(x: width - M.side - w, y: cy - M.buttonHeight / 2, width: w, height: M.buttonHeight)
        let max = width - M.side * 2 - 30
        bodies = pages.map { Self.wrap($0.body, LiquidSwipeOnboardingType.bodyUI, max) }
    }

    static func measure(_ text: String, _ font: UIFont) -> Double {
        (text as NSString).size(withAttributes: [.font: font]).width
    }

    /// Breaks `text` into lines no wider than `max`.
    static func wrap(_ text: String, _ font: UIFont, _ max: Double) -> [String] {
        var lines: [String] = []
        var line = ""
        for word in text.split(separator: " ") {
            let next = line.isEmpty ? String(word) : "\(line) \(word)"
            if !line.isEmpty && measure(next, font) > max {
                lines.append(line)
                line = String(word)
            } else {
                line = next
            }
        }
        if !line.isEmpty { lines.append(line) }
        return lines
    }
}

/// The type the pages are set in: a heavy condensed headline over a clean sans.
enum LiquidSwipeOnboardingType {
    static let title = Font.custom("AvenirNextCondensed-Heavy", fixedSize: LiquidSwipeOnboardingMetrics.titleSize)
    static let body = Font.custom("AvenirNext-Medium", fixedSize: LiquidSwipeOnboardingMetrics.bodySize)
    static let label = Font.custom("AvenirNext-Bold", fixedSize: 12)
    static let button = Font.custom("AvenirNext-DemiBold", fixedSize: 16)
    static let bodyUI = UIFont(name: "AvenirNext-Medium", size: LiquidSwipeOnboardingMetrics.bodySize) ?? .systemFont(ofSize: 16, weight: .medium)
    static let buttonUI = UIFont(name: "AvenirNext-DemiBold", size: 16) ?? .systemFont(ofSize: 16, weight: .semibold)
}

/// Draws one frame: the current page pushed aside, the liquid pages over it, and the chevrons.
private struct LiquidSwipeOnboardingPainter {
    let layout: LiquidSwipeOnboardingLayout
    let pages: [LiquidSwipeOnboardingPage]
    let images: [Int: UIImage]
    let action: String

    private typealias M = LiquidSwipeOnboardingMetrics

    func draw(_ frame: LiquidSwipeOnboardingFrame, in context: GraphicsContext) {
        let width = layout.width
        let height = layout.height
        guard width > 0 else { return }
        let current = frame.current

        var base = context
        base.translateBy(x: frame.baseShift, y: 0)
        drawPage(current, in: base)
        if frame.buttonShade > 0 {
            base.fill(capsule(layout.button), with: .color(.black.opacity(frame.buttonShade)))
        }

        if let g = frame.back, current - 1 >= 0 {
            var layer = context
            layer.opacity = frame.backOpacity
            layer.clip(to: Self.liquid(-1, width, height, g))
            layer.translateBy(x: frame.backShift, y: 0)
            drawPage(current - 1, in: layer)
        }
        if let g = frame.forward, current + 1 < pages.count {
            var layer = context
            layer.opacity = frame.forwardOpacity
            layer.clip(to: Self.liquid(1, width, height, g))
            layer.translateBy(x: frame.forwardShift, y: 0)
            drawPage(current + 1, in: layer)
        }

        // A chevron on each tab, in the colour of the page it brings, fading as the tab is pulled.
        let stroke = StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round)
        if let g = frame.back, current - 1 >= 0, frame.backChevron > 0 {
            var c = context
            c.opacity = frame.backChevron
            c.stroke(Self.chevron(-1, g, width), with: .color(pages[current - 1].ink), style: stroke)
        }
        if let g = frame.forward, current + 1 < pages.count, frame.forwardChevron > 0 {
            var c = context
            c.opacity = frame.forwardChevron
            c.stroke(Self.chevron(1, g, width), with: .color(pages[current + 1].ink), style: stroke)
        }
    }

    private func capsule(_ rect: CGRect) -> Path {
        Path(roundedRect: rect, cornerRadius: rect.height / 2, style: .circular)
    }

    // MARK: Page

    /// One page: its colour, the photo in an arch, the headline and copy, and the dots.
    private func drawPage(_ index: Int, in context: GraphicsContext) {
        let page = pages[index]
        let width = layout.width
        let height = layout.height
        context.fill(Path(CGRect(x: -width, y: -40, width: width * 3, height: height + 80)), with: .color(page.color))

        // Header: the brand and the page number.
        let headerY = layout.top + 28
        text(context, "CALDERA", LiquidSwipeOnboardingType.label, page.ink, x: M.side, baseline: headerY, tracking: 3)
        let number = String(format: "%02d / %02d", index + 1, pages.count)
        let numberText = context.resolve(Text(number).font(LiquidSwipeOnboardingType.label).tracking(1.5).foregroundColor(page.ink))
        let numberWidth = numberText.measure(in: CGSize(width: 1000, height: 100)).width - 1.5
        text(context, number, LiquidSwipeOnboardingType.label, page.ink, x: width - M.side - numberWidth, baseline: headerY, tracking: 1.5)

        // The photo in an arch: a semicircle on top of a rectangle.
        let arch = layout.arch
        let r = arch.width / 2
        var shape = Path()
        shape.move(to: CGPoint(x: arch.minX, y: arch.maxY))
        shape.addLine(to: CGPoint(x: arch.minX, y: arch.minY + r))
        shape.addArc(center: CGPoint(x: arch.midX, y: arch.minY + r), radius: r, startAngle: .degrees(180), endAngle: .degrees(360), clockwise: false)
        shape.addLine(to: CGPoint(x: arch.maxX, y: arch.maxY))
        shape.closeSubpath()
        if let image = images[index] {
            var photo = context
            photo.clip(to: shape)
            let scale = max(arch.width / image.size.width, arch.height / image.size.height)
            let w = image.size.width * scale
            let h = image.size.height * scale
            photo.draw(Image(uiImage: image), in: CGRect(x: arch.midX - w / 2, y: arch.midY - h / 2, width: w, height: h))
        } else {
            context.fill(shape, with: .color(page.ink.opacity(0.12)))
        }

        // Headline and copy.
        var y = arch.maxY + M.titleSize + 14
        for line in page.title {
            text(context, line, LiquidSwipeOnboardingType.title, page.ink, x: M.side - 2, baseline: y)
            y += M.titleLeading
        }
        y += M.bodyLeading - M.titleLeading + 14
        for line in layout.bodies[index] {
            text(context, line, LiquidSwipeOnboardingType.body, page.ink.opacity(0.78), x: M.side, baseline: y)
            y += M.bodyLeading
        }

        // Dots: the current page is a longer bar.
        let dotsY = height - layout.bottom - M.tabAbove
        var x = M.dotsLeft
        for i in pages.indices {
            let w: CGFloat = i == index ? 22 : 7
            context.fill(
                Path(roundedRect: CGRect(x: x, y: dotsY - 3.5, width: w, height: 7), cornerRadius: 3.5),
                with: .color(page.ink.opacity(i == index ? 1 : 0.35))
            )
            x += w + 7
        }

        // The last page has a button where the tab would be.
        if index == pages.count - 1 {
            let b = layout.button
            context.fill(capsule(b), with: .color(page.ink))
            text(context, action, LiquidSwipeOnboardingType.button, page.color, x: b.minX + 24, baseline: b.midY + 5.5)
            let ax = b.maxX - 30
            let ay = b.midY
            var arrow = Path()
            arrow.move(to: CGPoint(x: ax - 7, y: ay))
            arrow.addLine(to: CGPoint(x: ax + 7, y: ay))
            arrow.move(to: CGPoint(x: ax + 1, y: ay - 6))
            arrow.addLine(to: CGPoint(x: ax + 7, y: ay))
            arrow.addLine(to: CGPoint(x: ax + 1, y: ay + 6))
            context.stroke(arrow, with: .color(page.color), style: StrokeStyle(lineWidth: 2, lineCap: .round, lineJoin: .round))
        }
    }

    /// Text whose baseline sits at `baseline`, starting at `x`.
    private func text(_ context: GraphicsContext, _ string: String, _ font: Font, _ color: Color, x: Double, baseline: Double, tracking: Double = 0) {
        let resolved = context.resolve(Text(string).font(font).tracking(tracking).foregroundColor(color))
        let box = CGSize(width: 2000, height: 400)
        let ascent = resolved.firstBaseline(in: box)
        context.draw(resolved, at: CGPoint(x: x, y: baseline - ascent), anchor: .topLeading)
    }

    // MARK: Liquid

    /// The bump that shapes the liquid's edge: 1 at its centre, 0 at ±1, smooth throughout.
    private static func bump(_ u: Double) -> Double {
        if u <= -1 || u >= 1 { return 0 }
        return 0.5 + 0.5 * cos(Double.pi * u)
    }

    /// `inset` pt in from the right edge (side 1) or the left edge (side -1).
    private static func fromSide(_ side: Int, _ width: Double, _ inset: Double) -> Double {
        side > 0 ? width - inset : inset
    }

    /// The liquid page coming in from one side: its edge is straight at `edge` pt from that side,
    /// with a bump `reach` deep and `radius` tall either side of `cy`.
    static func liquid(_ side: Int, _ width: Double, _ height: Double, _ g: LiquidSwipeOnboardingShape) -> Path {
        var path = Path()
        path.move(to: CGPoint(x: fromSide(side, width, -2), y: -2))
        path.addLine(to: CGPoint(x: fromSide(side, width, g.edge), y: -2))
        let r = max(1, g.radius)
        let top = g.cy - r
        if top > 0 { path.addLine(to: CGPoint(x: fromSide(side, width, g.edge), y: top)) }
        for k in 0...M.samples {
            let y = top + 2 * r * Double(k) / Double(M.samples)
            path.addLine(to: CGPoint(x: fromSide(side, width, g.edge + g.reach * bump((y - g.cy) / r)), y: y))
        }
        let end = max(height + 2, g.cy + r)
        path.addLine(to: CGPoint(x: fromSide(side, width, g.edge), y: end))
        path.addLine(to: CGPoint(x: fromSide(side, width, -2), y: end))
        path.closeSubpath()
        return path
    }

    /// The chevron near the tip of a tab, pointing toward the middle of the screen.
    static func chevron(_ side: Int, _ g: LiquidSwipeOnboardingShape, _ width: Double) -> Path {
        let tip = g.edge + g.reach
        let x = side > 0 ? width - tip + 11 : tip - 11
        let y = g.cy
        let size = 5.5
        let s = Double(side)
        var path = Path()
        path.move(to: CGPoint(x: x + s * size * 0.45, y: y - size))
        path.addLine(to: CGPoint(x: x - s * size * 0.45, y: y))
        path.addLine(to: CGPoint(x: x + s * size * 0.45, y: y + size))
        return path
    }
}

// MARK: - Photos

/// Downloads a photo once and keeps it decoded.
enum LiquidSwipeOnboardingPhotos {
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

// The arch holds a photo about 360 pt wide on a phone; 1000 px covers that at 3x.
private func liquidSwipeOnboardingPhoto(_ id: String) -> String {
    "https://images.unsplash.com/\(id)?auto=format&fit=crop&w=1000&q=80"
}

// MARK: - Samples

extension LiquidSwipeOnboardingPage {
    static let samples: [LiquidSwipeOnboardingPage] = [
        .init(
            photo: "photo-1582721478779-0ae163c05a60",
            color: Color(hex: 0xff5b1f),
            ink: Color(hex: 0x1d0b04),
            title: ["Find the", "fire."],
            body: "Guided treks to forty active craters, from your first ridge to the rim."
        ),
        .init(
            photo: "photo-1628632577468-645afac21c82",
            color: Color(hex: 0x17120f),
            ink: Color(hex: 0xffd9c1),
            title: ["Read the", "ground."],
            body: "Live heat maps and vent alerts on every trail you walk."
        ),
        .init(
            photo: "photo-1663640397122-d50df280b1db",
            color: Color(hex: 0xf4c9a4),
            ink: Color(hex: 0x2b1109),
            title: ["Climb at", "first light."],
            body: "Small groups, local guides and hot coffee waiting at the crater."
        ),
        .init(
            photo: "photo-1623093386041-a0915e5a1ca4",
            color: Color(hex: 0x7c1c0f),
            ink: Color(hex: 0xffe4d4),
            title: ["Stand at", "the edge."],
            body: "Your first trek is on us. Pick a volcano and we will do the rest."
        ),
    ]
}
