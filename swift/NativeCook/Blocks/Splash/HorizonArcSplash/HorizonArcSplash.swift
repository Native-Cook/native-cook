import SwiftUI
import UIKit

/// The React Native reference's 8.5-second Farside launch, including its fixed-radius rims.
/// 0–1.4: top rim sweeps down; 0.9–2: staggered words; 1.5–2.5: lower rim rises.
/// 2.5–3.9: glow breathes; 3.9–4.7: planet swells; 4.4–7.4: meteor envelope.
/// 4.6–5.2: welcome appears; 7.2–7.9: dim; 7.9–8.5: opening rim returns.
/// A tap adds a 1.8-second sine-envelope shower without interrupting the reference's clock.
struct HorizonArcSplash: View {
    var autoPlay = false
    static let loop: Double = 8.5

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var accumulated = 0.0
    @State private var showerAt = -10.0
    @State private var showerActive = false
    @State private var taps = 0
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private typealias M = HorizonArcSplashMotion
    private typealias Art = HorizonArcSplashArt

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            let copy = HorizonArcSplashCopy.layout(size: size, safe: safe)
            TimelineView(.animation(paused: scenePhase != .active || reduceMotion || (!autoPlay && !showerActive))) { timeline in
                let now = elapsed(at: timeline.date)
                let t = autoPlay && !reduceMotion ? M.time(now) : 6
                let age = now - showerAt
                let shower = !reduceMotion && age > 0 && age < 1.8 ? sin(.pi * age / 1.8) : 0
                Canvas { context, canvasSize in
                    context.fill(Path(CGRect(origin: .zero, size: canvasSize)), with: .color(Art.background))
                    Art.draw(context, t: t, shower: shower, size: canvasSize)
                    copy.draw(context, t: t)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .onTapGesture { sendShower() }
                .accessibilityLabel("Farside. Plan the trips beyond the map. Routes, stays and the long way round, in one quiet place. Start exploring. I already have an account.")
                .accessibilityHint("Tap for a meteor shower.")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { sendShower() }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(Art.background.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            accumulated = 0
            haptic.prepare()
        }
        .onChange(of: scenePhase) { oldPhase, newPhase in
            if oldPhase == .active && !reduceMotion { accumulated += max(0, Date().timeIntervalSince(origin)) }
            if newPhase == .active { origin = .now }
        }
        .onChange(of: reduceMotion) { _, reduced in
            guard scenePhase == .active else { return }
            if reduced { accumulated += max(0, Date().timeIntervalSince(origin)) }
            origin = .now
        }
        .task(id: taps) {
            guard taps > 0 else { return }
            try? await Task.sleep(for: .seconds(1.8))
            guard !Task.isCancelled else { return }
            showerActive = false
        }
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (scenePhase == .active && !reduceMotion ? max(0, date.timeIntervalSince(origin)) : 0)
    }

    private func sendShower() {
        guard !reduceMotion else { return }
        showerAt = elapsed(at: .now)
        showerActive = true
        taps += 1
        haptic.impactOccurred()
        haptic.prepare()
    }
}

private enum HorizonArcSplashMotion {
    static func time(_ elapsed: Double) -> Double {
        max(0, elapsed).truncatingRemainder(dividingBy: HorizonArcSplash.loop)
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func easeIn(_ x: Double) -> Double { x * x * x }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
}

private enum HorizonArcSplashArt {
    private typealias M = HorizonArcSplashMotion
    static let background = Color(hex: 0x000005)
    static let blue = Color(hex: 0x2E7BFF)
    static let ice = Color(hex: 0x8EC5FF)
    static let rimRadius = 250.0

    static func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
    }

    static func rim(_ context: GraphicsContext, x: Double, y: Double, alpha: Double, glow: Double) {
        guard alpha > 0.001 else { return }
        let layers: [(width: Double, blur: Double, color: Color, alpha: Double)] = [
            (26, 22, blue, 0.55 * glow), (9, 6, blue, 0.9), (2.5, 0.8, ice, 1)
        ]
        for layer in layers {
            var stroke = context
            stroke.opacity = M.clamp(alpha * layer.alpha)
            stroke.addFilter(.blur(radius: layer.blur))
            stroke.stroke(circle(x, y, rimRadius), with: .color(layer.color), lineWidth: layer.width)
        }
    }

    static func draw(_ context: GraphicsContext, t: Double, shower: Double, size: CGSize) {
        let width = Double(size.width)
        let height = Double(size.height)
        let cx = width / 2
        let dim = 1 - M.seg(t, 7.2, 7.9)
        let fadeIn = M.seg(t, 7.9, 8.5)
        if t < 1.4 {
            let y = 40 + (height + 120) * M.easeIn(M.seg(t, 0, 1.4))
            rim(context, x: cx, y: y - rimRadius, alpha: 1, glow: 1)
        } else if fadeIn > 0 {
            rim(context, x: cx, y: 40 - rimRadius, alpha: fadeIn, glow: 1)
        }

        let rise = M.easeOut(M.seg(t, 1.5, 2.5))
        let swell = M.easeInOut(M.seg(t, 3.9, 4.7))
        if rise > 0 && dim > 0 {
            let top = height + 30 - (height + 30 - height * 0.62) * rise + height * 0.08 * swell
            let breathe = 1 + 0.2 * sin(max(0, t - 2.5) * .pi * 1.4) * (1 - swell)
            let radius = rimRadius * (1 + 1.2 * swell)
            let cy = top + radius
            if swell > 0 {
                var planet = context
                planet.opacity = swell * dim
                planet.fill(circle(cx, cy, radius), with: .radialGradient(
                    Gradient(stops: [
                        .init(color: Color(hex: 0x3D8BFF), location: 0),
                        .init(color: Color(hex: 0x1A3FD6), location: 0.45),
                        .init(color: Color(hex: 0x08155C), location: 1)
                    ]), center: CGPoint(x: cx, y: top), startRadius: 0, endRadius: radius * 0.55
                ))

                var beam = context
                beam.opacity = swell * dim * 0.85
                beam.translateBy(x: width, y: 0)
                beam.rotate(by: .degrees(-32))
                beam.translateBy(x: -width, y: 0)
                beam.addFilter(.blur(radius: 40))
                beam.fill(Path(CGRect(x: width * 0.35, y: -100, width: width * 0.5, height: height * 1.4)), with: .linearGradient(
                    Gradient(colors: [blue.opacity(0), blue.opacity(0.55)]),
                    startPoint: .zero, endPoint: CGPoint(x: 0, y: height)
                ))
            }
            // The reference keeps the stroke radius at 250 even as the filled planet grows.
            rim(context, x: cx, y: cy, alpha: dim, glow: breathe)
        }

        let on = M.seg(t, 4.4, 4.8) * (1 - M.seg(t, 7, 7.4))
        for i in 0..<12 {
            let index = Double(i)
            let phase = (t + index * 0.9 / 12 * 3.1).truncatingRemainder(dividingBy: 0.9) / 0.9
            let a = max(on, shower)
            guard a > 0.01 else { continue }
            let sx = width * (0.55 + 0.45 * (index * 0.37).truncatingRemainder(dividingBy: 1)) + 60
            let sy = height * (0.02 + 0.3 * (index * 0.61).truncatingRemainder(dividingBy: 1)) - 40
            let distance = 260 * phase
            let x = sx - distance * 0.5
            let y = sy + distance * 0.87
            let tail = 46 + 30 * (index * 0.23).truncatingRemainder(dividingBy: 1)
            let start = CGPoint(x: x + tail * 0.5, y: y - tail * 0.87)
            let end = CGPoint(x: x, y: y)
            var meteor = context
            meteor.opacity = a * sin(.pi * phase) * dim
            var path = Path()
            path.move(to: start)
            path.addLine(to: end)
            meteor.stroke(path, with: .linearGradient(
                Gradient(colors: [.white.opacity(0), .white]), startPoint: start, endPoint: end
            ), style: StrokeStyle(lineWidth: 2, lineCap: .round))
            meteor.fill(circle(x, y, 3.2), with: .color(.white))
        }
    }
}

/// Rasterize the immutable copy once with the same TextKit settings as React Native iOS.
/// This preserves line heights, glyph advances, trailing spaces, and the per-word fade.
private struct HorizonArcSplashCopy {
    private typealias M = HorizonArcSplashMotion
    private struct Label {
        let image: Image
        let size: CGSize
    }
    private struct Word {
        let label: Label
        let point: CGPoint
        let index: Int
    }
    private let size: CGSize
    private let safe: EdgeInsets
    private let words: [Word]
    private let brand: Label
    private let heading: Label
    private let sub: Label
    private let action: Label
    private let account: Label

    private static var cache: [String: HorizonArcSplashCopy] = [:]

    static func layout(size: CGSize, safe: EdgeInsets) -> HorizonArcSplashCopy {
        let key = "\(size.width)|\(size.height)|\(safe.top)|\(safe.bottom)"
        if let cached = cache[key] { return cached }
        let copy = HorizonArcSplashCopy(size: size, safe: safe)
        cache[key] = copy
        return copy
    }

    private init(size: CGSize, safe: EdgeInsets) {
        self.size = size
        self.safe = safe
        let width = max(1, size.width - 48)
        brand = Self.label("FARSIDE", size: 15, weight: .heavy, color: UIColor(red: 142 / 255, green: 197 / 255, blue: 1, alpha: 1), tracking: 2, width: width)
        heading = Self.label("Plan the trips\nbeyond the map", size: 38, weight: .black, tracking: -1.2, lineHeight: 42, width: width)
        sub = Self.label("Routes, stays and the long way round, in one quiet place.", size: 16, color: .white.withAlphaComponent(0.7), lineHeight: 22, width: min(280, width))
        action = Self.label("Start exploring", size: 17, weight: .bold, color: UIColor(red: 0, green: 0, blue: 5 / 255, alpha: 1), width: width)
        account = Self.label("I already have an account", size: 15, weight: .semibold, color: .white.withAlphaComponent(0.75), width: width)
        let labels = ["Every ", "trip ", "starts ", "with ", "a ", "horizon "].map {
            Self.label($0, size: 24, weight: .medium, tracking: -0.2, width: width)
        }
        var rows: [[Int]] = [[]]
        var rowWidth: CGFloat = 0
        for i in labels.indices {
            if rowWidth + labels[i].size.width > width && !rows[rows.count - 1].isEmpty {
                rows.append([])
                rowWidth = 0
            }
            rows[rows.count - 1].append(i)
            rowWidth += labels[i].size.width
        }
        var placed: [Word] = []
        let scale = UIScreen.main.scale
        var y = (size.height * 0.42 * scale).rounded() / scale
        for row in rows {
            let total = row.reduce(CGFloat.zero) { $0 + labels[$1].size.width }
            var x = (size.width - total) / 2
            for index in row {
                placed.append(Word(label: labels[index], point: CGPoint(x: floor(x * scale) / scale, y: y), index: index))
                x += labels[index].size.width
            }
            y += row.map { labels[$0].size.height }.max() ?? 0
        }
        words = placed
    }

    private static func label(_ text: String, size: CGFloat, weight: UIFont.Weight = .regular, color: UIColor = .white, tracking: CGFloat? = nil, lineHeight: CGFloat? = nil, width: CGFloat) -> Label {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        if let tracking { attributes[.kern] = tracking }
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        let storage = NSTextStorage(string: text, attributes: attributes)
        let manager = NSLayoutManager()
        manager.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.lineBreakMode = .byClipping
        manager.addTextContainer(container)
        storage.addLayoutManager(manager)
        manager.ensureLayout(for: container)
        let used = manager.usedRect(for: container).size
        let scale = UIScreen.main.scale
        let measured = CGSize(width: ceil(used.width * scale) / scale, height: ceil(used.height * scale) / scale)
        let imageSize = CGSize(width: max(1, measured.width), height: max(1, measured.height))
        let renderer = UIGraphicsImageRenderer(size: imageSize)
        let bitmap = renderer.image { _ in
            let range = manager.glyphRange(for: container)
            manager.drawBackground(forGlyphRange: range, at: .zero)
            manager.drawGlyphs(forGlyphRange: range, at: .zero)
        }
        return Label(image: Image(uiImage: bitmap), size: measured)
    }

    func draw(_ context: GraphicsContext, t: Double) {
        for word in words {
            let index = Double(word.index)
            let u = M.seg(t, 0.9 + index * 0.12, 1.4 + index * 0.12) * (1 - M.seg(t, 3.9, 4.3))
            var layer = context
            layer.opacity = u
            layer.draw(word.label.image, at: CGPoint(x: word.point.x, y: word.point.y + 8 * (1 - u)), anchor: .topLeading)
        }
        let u = M.seg(t, 4.6, 5.2) * (1 - M.seg(t, 7.2, 7.7))
        guard u > 0 else { return }
        var page = context
        page.opacity = u
        page.translateBy(x: 0, y: 14 * (1 - u))
        let pageTop = safe.top + 60
        page.draw(brand.image, at: CGPoint(x: 24, y: pageTop), anchor: .topLeading)
        let headingTop = pageTop + brand.size.height + 12
        page.draw(heading.image, at: CGPoint(x: 24, y: headingTop), anchor: .topLeading)
        page.draw(sub.image, at: CGPoint(x: 24, y: headingTop + heading.size.height + 10), anchor: .topLeading)
        let buttonTop = size.height - safe.bottom - 14 - account.size.height - 14 - 58
        let button = CGRect(x: 24, y: buttonTop, width: size.width - 48, height: 58)
        page.fill(Path(roundedRect: button, cornerRadius: 29), with: .color(.white))
        // Yoga floors text origins to the pixel grid before applying animated transforms.
        let scale = UIScreen.main.scale
        let actionPoint = CGPoint(x: floor((size.width - action.size.width) * 0.5 * scale) / scale,
                                  y: floor((button.midY - action.size.height * 0.5) * scale) / scale)
        let accountPoint = CGPoint(x: floor((size.width - account.size.width) * 0.5 * scale) / scale,
                                   y: button.maxY + 14)
        page.draw(action.image, at: actionPoint, anchor: .topLeading)
        page.draw(account.image, at: accountPoint, anchor: .topLeading)
    }
}

#Preview {
    HorizonArcSplash(autoPlay: true)
}
