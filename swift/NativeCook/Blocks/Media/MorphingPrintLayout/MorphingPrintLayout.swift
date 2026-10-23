import SwiftUI
import UIKit

/*
 Morphing Print Layout — a film picker where one white print frame morphs between formats.

 Look: a white top strip (nav bar: back arrow, "Select Film" with a small black "?" badge, a sliders and
 a share glyph; under it a one-line pager of format names in 24 pt medium, the selected one black, the
 others #C4C4C4, and five 6 pt dots). Below it an orange stage, #F4B043 at the top to #EE4D0F at the
 bottom, with the print frame centred in it (white, corner 3 pt, a warm shadow), its square wells filled
 with six summer photos (palm beach, shoreline, sunrise, cliff village, pool, boardwalk). Under the frame a
 caption for the format; at the bottom an export button and a white "Choose photos" pill.

 Layouts, morph and autoPlay timeline (loop = 7 s): MorphingPrintLayoutModel.swift. The names slide with
 the page (each centred by its measured width, 20 pt apart), their ink blending from black to grey over
 one page; the caption crossfades over half a page each way, one caption at a time.

 Touch: drag sideways anywhere on the stage or the pager to scrub the page (170 pt a page, rubber-banded
 at 35 % past the ends); a light haptic at each format. On release it springs to the nearest format, thrown
 a quarter of the way by the finger's speed. Tapping a name goes to it. 2 s after the last touch the frame
 sweeps back to Single and the loop starts again.
 Reduced motion: no loop and no scattering; a drag still scrubs, and release snaps at once.
 */

struct MorphingPrintLayout: View {
    var autoPlay = false

    static let loop = MorphingPrintLayoutModel.loop
    typealias M = MorphingPrintLayoutModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var autoStart: Date? = nil
    /// The manual page: either dragged, or gliding from one page to another.
    @State private var dragFrom: Double? = nil
    @State private var dragOffset = 0.0
    @State private var glide: (date: Date, from: Double, to: Double, sweep: Bool)? = nil
    @State private var resting = 0.0
    @State private var lastTick = 0
    @State private var active = false
    /// Resets by itself when the system cancels a drag (onEnded is not called then).
    @GestureState private var pressing = false
    @State private var lastLayout: Layout? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    static let pageWidth = 170.0
    static let nameFont = UIFont.systemFont(ofSize: 24, weight: .medium)
    static let nameWidths: [Double] = M.formats.map { Double(($0.name as NSString).size(withAttributes: [.font: nameFont]).width) }

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var k: Double
        var stripBottom: Double
        var center: CGPoint
        var captionY: Double
        var bottomY: Double

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            k = min(size.width, 430) / 393
            stripBottom = insets.top + 112
            bottomY = size.height - max(insets.bottom, 20) - 34
            center = CGPoint(x: size.width / 2, y: (stripBottom + bottomY - 66) / 2)
            captionY = center.y + 186 * k
        }
    }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: !running)) { timeline in
                let p = page(at: timeline.date)
                ZStack(alignment: .topLeading) {
                    stage(layout)
                    MorphingPrintLayoutFrame(page: p, k: layout.k, center: layout.center, reduce: reduceMotion)
                        .frame(width: size.width, height: size.height)
                        .allowsHitTesting(false)
                    caption(layout, p)
                    bottomBar(layout)
                    strip(layout, p)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(layout))
            }
            .ignoresSafeArea()
        }
        .onChange(of: pressing) { _, down in
            guard !down else { return }
            // A drag the system cancelled never ends: tidy up after it.
            Task { @MainActor in
                try? await Task.sleep(for: .seconds(0.1))
                guard let from = dragFrom, let layout = lastLayout else { return }
                let p = rubber(from - dragOffset / Self.pageWidth)
                dragFrom = nil
                let target = min(Double(M.formats.count - 1), max(0, p.rounded()))
                glide = (.now, p, target, false)
                resting = target
                _ = layout
                scheduleResume()
            }
        }
        .onAppear {
            haptic.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    private var running: Bool { (autoStart != nil && !reduceMotion) || active }

    // MARK: Time

    private func page(at date: Date) -> Double {
        if let start = autoStart, date >= start {
            return M.page(date.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop))
        }
        if let from = dragFrom { return rubber(from - dragOffset / Self.pageWidth) }
        if let g = glide {
            let u = date.timeIntervalSince(g.date)
            let e = reduceMotion ? 1 : (g.sweep ? M.critical(u) : M.spring(u))
            return g.from + (g.to - g.from) * e
        }
        return resting
    }

    private func rubber(_ p: Double) -> Double {
        let last = Double(M.formats.count - 1)
        if p < 0 { return p * 0.35 }
        if p > last { return last + (p - last) * 0.35 }
        return p
    }

    // MARK: Touch

    private func drag(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($pressing) { _, state, _ in state = true }
            .onChanged { value in
                lastLayout = layout
                let now = Date.now
                if dragFrom == nil {
                    resume?.cancel()
                    active = true
                    let p = page(at: now)
                    autoStart = nil
                    glide = nil
                    dragFrom = p
                    dragOffset = 0
                    lastTick = Int(p.rounded())
                }
                dragOffset = value.translation.width
                let p = page(at: now)
                let k = Int(p.rounded())
                if k != lastTick && p >= -0.2 && p <= Double(M.formats.count - 1) + 0.2 {
                    lastTick = k
                    haptic.impactOccurred(intensity: 0.6)
                }
            }
            .onEnded { value in
                let now = Date.now
                guard let from = dragFrom else { return }
                let p = rubber(from - value.translation.width / Self.pageWidth)
                dragFrom = nil
                var target: Double
                if abs(value.translation.width) < 6 && abs(value.translation.height) < 6 {
                    // A tap: on a name, go to it.
                    target = (p).rounded()
                    if value.location.y < layout.stripBottom, value.location.y > layout.insets.top + 44 {
                        target = tappedName(value.location.x, page: p, layout: layout) ?? target
                    }
                } else {
                    let thrown = p - (value.predictedEndTranslation.width - value.translation.width) / Self.pageWidth * 0.25
                    target = thrown.rounded()
                }
                target = min(Double(M.formats.count - 1), max(0, target))
                if target != p.rounded() || abs(value.translation.width) < 6 { haptic.impactOccurred(intensity: 0.6) }
                glide = (now, p, target, false)
                resting = target
                scheduleResume()
            }
    }

    private func tappedName(_ x: Double, page p: Double, layout: Layout) -> Double? {
        let xs = Self.nameCenters()
        let cp = Self.centerAt(p, xs)
        for i in 0..<xs.count {
            let cx = layout.size.width / 2 + xs[i] - cp
            if abs(x - cx) < Self.nameWidths[i] / 2 + 10 { return Double(i) }
        }
        return nil
    }

    private func scheduleResume() {
        resume?.cancel()
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            guard autoPlay, !reduceMotion else {
                active = false
                return
            }
            let now = Date.now
            let p = page(at: now)
            if p > 0.01 {
                glide = (now, p, 0, true)
                resting = 0
                try? await Task.sleep(for: .seconds(1.0))
                guard !Task.isCancelled else { return }
            }
            glide = nil
            // Join the loop in its final rest.
            autoStart = Date.now.addingTimeInterval(-6.9)
        }
    }

    // MARK: Names

    static func nameCenters() -> [Double] {
        var xs: [Double] = []
        var x = 0.0
        for w in nameWidths {
            xs.append(x + w / 2)
            x += w + 20
        }
        return xs
    }

    static func centerAt(_ p: Double, _ xs: [Double]) -> Double {
        let n = xs.count
        let a = min(n - 2, max(0, Int(floor(p))))
        return xs[a] + (xs[a + 1] - xs[a]) * (p - Double(a))
    }

    // MARK: Drawing

    private func stage(_ layout: Layout) -> some View {
        LinearGradient(colors: [Color(red: 0.957, green: 0.690, blue: 0.263), Color(red: 0.933, green: 0.302, blue: 0.059)],
                       startPoint: .top, endPoint: .bottom)
            .frame(width: layout.size.width, height: layout.size.height - layout.stripBottom)
            .offset(y: layout.stripBottom)
            .allowsHitTesting(false)
    }

    private func strip(_ layout: Layout, _ p: Double) -> some View {
        let ink = Color(red: 0.07, green: 0.07, blue: 0.08)
        let xs = Self.nameCenters()
        let cp = Self.centerAt(p, xs)
        return ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .frame(width: layout.size.width, height: layout.stripBottom)
            // Nav bar.
            HStack(spacing: 18) {
                MorphingPrintLayoutGlyph(kind: .back).stroke(ink, style: .init(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 22, height: 22)
                Spacer()
                MorphingPrintLayoutGlyph(kind: .sliders).stroke(ink, style: .init(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 22, height: 22)
                MorphingPrintLayoutGlyph(kind: .share).stroke(ink, style: .init(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                    .frame(width: 22, height: 22)
            }
            .padding(.horizontal, 20)
            .frame(width: layout.size.width, height: 44)
            .offset(y: layout.insets.top)
            HStack(spacing: 6) {
                Text("Select Film").font(.system(size: 15, weight: .semibold)).foregroundStyle(ink)
                Text("?").font(.system(size: 10, weight: .bold)).foregroundStyle(.white)
                    .frame(width: 15, height: 15).background(ink, in: Circle())
            }
            .frame(width: layout.size.width, height: 44)
            .offset(y: layout.insets.top)
            // The pager of names.
            ForEach(0..<M.formats.count, id: \.self) { i in
                let d = min(1, abs(Double(i) - p))
                let grey = 0.07 + (0.77 - 0.07) * d
                Text(M.formats[i].name)
                    .font(Font(Self.nameFont))
                    .foregroundStyle(Color(white: grey))
                    .fixedSize()
                    .position(x: layout.size.width / 2 + xs[i] - cp, y: layout.insets.top + 44 + 26)
            }
            // Dots.
            HStack(spacing: 8) {
                ForEach(0..<M.formats.count, id: \.self) { i in
                    let a = max(0, 1 - abs(Double(i) - p))
                    Circle().fill(Color(white: 0.84 - 0.77 * a)).frame(width: 6, height: 6)
                }
            }
            .frame(width: layout.size.width)
            .offset(y: layout.insets.top + 44 + 50)
        }
        .frame(width: layout.size.width, height: layout.stripBottom, alignment: .topLeading)
        .clipped()
    }

    private func caption(_ layout: Layout, _ p: Double) -> some View {
        ZStack {
            ForEach(0..<M.formats.count, id: \.self) { i in
                // One caption at a time: each is gone half way to the next.
                let a = max(0, 1 - 2 * abs(Double(i) - p))
                if a > 0.001 {
                    Text(M.formats[i].caption)
                        .font(.system(size: 15, weight: .medium))
                        .foregroundStyle(.white.opacity(0.92))
                        .opacity(a)
                }
            }
        }
        .frame(width: layout.size.width, height: 22)
        .offset(y: layout.captionY - 11)
        .allowsHitTesting(false)
    }

    private func bottomBar(_ layout: Layout) -> some View {
        HStack(spacing: 12) {
            ZStack {
                Circle().fill(.white.opacity(0.22))
                MorphingPrintLayoutGlyph(kind: .export).stroke(.white, style: .init(lineWidth: 1.8, lineCap: .round, lineJoin: .round))
                    .frame(width: 22, height: 22)
            }
            .frame(width: 48, height: 48)
            Text("Choose photos")
                .font(.system(size: 16, weight: .semibold))
                .foregroundStyle(Color(red: 0.07, green: 0.07, blue: 0.08))
                .frame(maxWidth: .infinity)
                .frame(height: 48)
                .background(.white, in: Capsule())
        }
        .padding(.horizontal, 24)
        .frame(width: layout.size.width, height: 48)
        .offset(y: layout.bottomY - 24)
        .allowsHitTesting(false)
    }
}

/// The print frame and its wells for a fractional page, drawn in one Canvas.
struct MorphingPrintLayoutFrame: View {
    var page: Double
    var k: Double
    var center: CGPoint
    var reduce: Bool

    var body: some View {
        Canvas { ctx, _ in
            let scale = ctx.environment.displayScale
            MorphingPrintLayoutAssets.prepare(k: k, scale: scale)
            let s = MorphingPrintLayoutModel.shape(page, reduce: reduce)
            let w = s.w * k, h = s.h * k
            let origin = CGPoint(x: center.x - w / 2, y: center.y - h / 2)
            let frame = CGRect(origin: origin, size: CGSize(width: w, height: h))
            let shadow = Color(red: 0.45, green: 0.12, blue: 0.0)
            // The big frame, fading while the prints are loose.
            let frameAlpha = 1 - min(1, s.loose * 1.6)
            if frameAlpha > 0.001 {
                ctx.drawLayer { l in
                    l.opacity = frameAlpha
                    l.addFilter(.shadow(color: shadow.opacity(0.28), radius: 22, x: 0, y: 12))
                    l.fill(Path(roundedRect: frame, cornerRadius: 3), with: .color(.white))
                }
            }
            // Wells, from the last to the first, so a well that splits off comes out from under its neighbour.
            for i in stride(from: s.shown - 1, through: 0, by: -1) {
                let wl = s.wells[i]
                let rect = CGRect(x: origin.x + wl.x * k, y: origin.y + wl.y * k, width: wl.s * k, height: wl.s * k)
                ctx.drawLayer { l in
                    if s.loose > 0, i < 4 {
                        let sc = MorphingPrintLayoutModel.scatter[i]
                        l.translateBy(x: rect.midX + sc.dx * k * s.loose, y: rect.midY + sc.dy * k * s.loose)
                        l.rotate(by: .degrees(sc.r * s.loose))
                        l.translateBy(x: -rect.midX, y: -rect.midY)
                        // Its own little print border.
                        let b = 8 * k * s.loose
                        let border = CGRect(x: rect.minX - b, y: rect.minY - b, width: rect.width + 2 * b, height: rect.height + b + 22 * k * s.loose)
                        l.drawLayer { bl in
                            bl.addFilter(.shadow(color: shadow.opacity(0.3 * s.loose), radius: 14, x: 0, y: 8))
                            bl.fill(Path(roundedRect: border, cornerRadius: 2), with: .color(.white))
                        }
                    }
                    l.clip(to: Path(roundedRect: rect, cornerRadius: 2))
                    l.draw(MorphingPrintLayoutAssets.photo(i, px: rect.width * scale), in: rect)
                }
            }
        }
        .accessibilityElement()
        .accessibilityLabel("Print layout preview")
    }
}

/// Line glyphs on a 24 × 24 grid.
struct MorphingPrintLayoutGlyph: Shape {
    enum Kind { case back, sliders, share, export }
    var kind: Kind

    func path(in rect: CGRect) -> Path {
        var p = Path()
        switch kind {
        case .back:
            p.move(to: CGPoint(x: 20, y: 12)); p.addLine(to: CGPoint(x: 4, y: 12))
            p.move(to: CGPoint(x: 10, y: 6)); p.addLine(to: CGPoint(x: 4, y: 12)); p.addLine(to: CGPoint(x: 10, y: 18))
        case .sliders:
            for (y, x) in [(6.0, 15.0), (12.0, 8.0), (18.0, 13.0)] {
                p.move(to: CGPoint(x: 3, y: y)); p.addLine(to: CGPoint(x: 21, y: y))
                p.addEllipse(in: CGRect(x: x - 2, y: y - 2, width: 4, height: 4))
            }
        case .share:
            p.move(to: CGPoint(x: 12, y: 3.5)); p.addLine(to: CGPoint(x: 12, y: 15))
            p.move(to: CGPoint(x: 7.5, y: 8)); p.addLine(to: CGPoint(x: 12, y: 3.5)); p.addLine(to: CGPoint(x: 16.5, y: 8))
            p.move(to: CGPoint(x: 8, y: 11)); p.addLine(to: CGPoint(x: 5, y: 11)); p.addLine(to: CGPoint(x: 5, y: 21))
            p.addLine(to: CGPoint(x: 19, y: 21)); p.addLine(to: CGPoint(x: 19, y: 11)); p.addLine(to: CGPoint(x: 16, y: 11))
        case .export:
            p.move(to: CGPoint(x: 12, y: 4)); p.addLine(to: CGPoint(x: 12, y: 15))
            p.move(to: CGPoint(x: 7.5, y: 10.5)); p.addLine(to: CGPoint(x: 12, y: 15)); p.addLine(to: CGPoint(x: 16.5, y: 10.5))
            p.move(to: CGPoint(x: 4, y: 15)); p.addLine(to: CGPoint(x: 4, y: 20)); p.addLine(to: CGPoint(x: 20, y: 20)); p.addLine(to: CGPoint(x: 20, y: 15))
        }
        let s = min(rect.width, rect.height) / 24
        return p.applying(CGAffineTransform(scaleX: s, y: s).concatenating(CGAffineTransform(translationX: rect.minX, y: rect.minY)))
    }
}

/// The six photos. Drawn straight from their JPEGs, every frame of a morph decoded them again,
/// converted their pixels and uploaded them, and shrank them to the size of the moment (the cause of
/// the stalls while the wells resize). So each photo is decoded once, in the background, into bitmaps
/// at the size of its well in each layout at rest (and sizes between those at most √2 apart, never
/// larger than the photo); a well draws the largest one not larger than itself, which the renderer
/// keeps as a texture and only enlarges a little while it moves. Until they are ready the JPEGs are
/// drawn.
enum MorphingPrintLayoutAssets {
    static let names = ["palm", "shore", "sunrise", "village", "pool", "boardwalk"]
    static let photos: [UIImage?] = names.map { name in
        guard let url = Bundle.main.url(forResource: "MorphingPrintLayout-\(name)", withExtension: "jpg") else { return nil }
        return UIImage(contentsOfFile: url.path)
    }

    private struct Prepared {
        var sides: [Int]
        var images: [[CGImage]]
    }
    private static let lock = NSLock()
    private static var prepared: Prepared? = nil
    private static var started = false

    /// Makes the bitmaps for a frame scale `k` (points per design point) and screen scale, once.
    static func prepare(k: Double, scale: Double) {
        lock.lock()
        defer { lock.unlock() }
        if started { return }
        started = true
        DispatchQueue.global(qos: .userInitiated).async {
            let sources = photos.map { $0?.cgImage }
            let native = sources.compactMap { $0?.width }.min() ?? 720
            // The wells at rest: Triple and Six 94, Four 125, Double 141, Single 242 design points.
            let rest = Set([94.0, 125, 141, 242].map { min(Int(($0 * k * scale).rounded(.down)), native) }).sorted()
            var sides: [Int] = []
            for (a, b) in zip(rest, rest.dropFirst()) {
                let steps = max(1, Int(ceil(log(Double(b) / Double(a)) / log(1.415))))
                for j in 0..<steps { sides.append(Int((Double(a) * pow(Double(b) / Double(a), Double(j) / Double(steps))).rounded(.down))) }
            }
            sides.append(rest.last ?? native)
            var images: [[CGImage]] = []
            for source in sources {
                guard let source else { return }
                images.append(sides.map { bitmap(source, side: $0) ?? source })
            }
            lock.lock()
            prepared = Prepared(sides: sides, images: images)
            lock.unlock()
        }
    }

    private static func bitmap(_ source: CGImage, side: Int) -> CGImage? {
        guard let g = CGContext(data: nil, width: side, height: side, bitsPerComponent: 8, bytesPerRow: 0,
                                space: CGColorSpace(name: CGColorSpace.sRGB)!,
                                bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else { return nil }
        g.interpolationQuality = .high
        g.draw(source, in: CGRect(x: 0, y: 0, width: side, height: side))
        return g.makeImage()
    }

    /// Photo i for a well `px` pixels wide.
    static func photo(_ i: Int, px: Double) -> Image {
        lock.lock()
        let p = prepared
        lock.unlock()
        if let p {
            var best = 0
            for (j, side) in p.sides.enumerated() where Double(side) <= px + 0.5 { best = j }
            return Image(decorative: p.images[i][best], scale: 1)
        }
        if let image = photos[i] { return Image(uiImage: image) }
        return Image(systemName: "photo")
    }
}
