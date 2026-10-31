import CoreText
import SwiftUI
import UIKit

// Faithful port of the React Native exhibition guide, with photos clipped inside each print.
// Four 2.2 s stops make an 8.8 s loop: hold 1.4 s, then change over 0.8 s.
// Digit i rolls forward over 0.5 s after a 0.08*i s delay, using ease-in-out cubic.
// The outgoing print flies left 420 pt and turns -24 degrees over 0.45 s (cubic in).
// After 0.25 s the incoming print drops 160 pt from scale 1.15, landing over 0.55 s
// with ease-out back (1.4). Colour, captions and the timeline follow the same stop index.
// Swipe 240 pt per stop; release snaps over 0.4 s, holds 2 s, then carries on from that era.

struct YearPhotoCounter: View {
    var autoPlay = false
    static let loop: Double = 8.8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var pausedAt: Date?
    @State private var manual = false
    @State private var touching = false
    @State private var grabbed = 0.0
    @State private var dragged = 0.0
    @State private var panActive = false
    @State private var lastStep = 0
    @State private var releasedAt: Date?
    @State private var snapFrom = 0.0
    @State private var snapTo = 0.0
    @State private var haptic = UISelectionFeedbackGenerator()

    private typealias M = YearPhotoCounterMotion
    private typealias A = YearPhotoCounterArtwork

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let artwork = A.layout(width: size.width)
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && !manual))) { context in
                let index = index(at: pausedAt ?? context.date)
                Canvas { ctx, _ in
                    draw(in: &ctx, size: size, safe: safe, index: index, artwork: artwork)
                }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Carry On. Room 3. \(M.eras[M.wrap(Int(floor(index + 0.5)))].year). \(M.eras[M.wrap(Int(floor(index + 0.5)))].title). Swipe to change year.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(pan)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            let now = Date()
            if phase != .active {
                if pausedAt == nil { pausedAt = now }
            } else if let pause = pausedAt {
                let gap = now.timeIntervalSince(pause)
                origin = origin.addingTimeInterval(gap)
                if let release = releasedAt { releasedAt = release.addingTimeInterval(gap) }
                pausedAt = nil
            }
        }
    }

    private func index(at date: Date) -> Double {
        guard !reduceMotion else { return 0 }
        if manual {
            if touching { return dragged }
            guard let releasedAt else { return dragged }
            let since = max(0, date.timeIntervalSince(releasedAt))
            if since < M.snap {
                return snapFrom + (snapTo - snapFrom) * M.ease(since / M.snap)
            }
            if since <= M.snap + M.hold || !autoPlay { return snapTo }
            // The RN origin is rebased to the snapped era after its snap and hold.
            return M.index(at: M.mod(Double(M.wrap(Int(snapTo))) * M.stop + since - M.snap - M.hold,
                                    Self.loop))
        }
        guard autoPlay else { return 0 }
        return M.index(at: M.mod(max(0, date.timeIntervalSince(origin)), Self.loop))
    }

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                if !touching {
                    let current = index(at: .now)
                    grabbed = current
                    dragged = current
                    lastStep = Int(floor(current + 0.5))
                    touching = true
                    manual = true
                    panActive = false
                }
                if !panActive && hypot(value.translation.width, value.translation.height) >= 4 {
                    panActive = true
                }
                guard panActive else { return }
                dragged = grabbed - value.translation.width / M.per
                let step = Int(floor(dragged + 0.5))
                if step != lastStep {
                    lastStep = step
                    haptic.selectionChanged()
                    haptic.prepare()
                }
            }
            .onEnded { _ in
                guard !reduceMotion else { return }
                touching = false
                snapFrom = dragged
                snapTo = floor(dragged + 0.5)
                releasedAt = .now
            }
    }

    private func draw(in ctx: inout GraphicsContext, size: CGSize, safe: EdgeInsets,
                      index: Double, artwork: A.Layout) {
        let base = Int(floor(index))
        let fraction = index - floor(index)
        let a = M.wrap(base), b = M.wrap(base + 1)
        let eased = M.ease(fraction)
        ctx.fill(Path(CGRect(origin: .zero, size: size)),
                 with: .color(M.mix(M.eras[a].background, M.eras[b].background, eased)))

        A.draw(artwork.brand, in: &ctx, x: 24, y: safe.top + 8 + (34 - artwork.brand.size.height) / 2)
        A.draw(artwork.meta, in: &ctx, x: size.width - 24 - artwork.meta.size.width,
               y: safe.top + 8 + (34 - artwork.meta.size.height) / 2)

        for i in 0..<4 {
            let old = M.digit(M.eras[a].year, i)
            let new = M.digit(M.eras[b].year, i)
            let steps = new >= old ? new - old : new + 10 - old
            let local = M.clamp((fraction * 0.8 - 0.08 * Double(i)) / 0.5)
            let pos = Double(old) + Double(steps) * M.ease(local)
            let column = CGRect(x: (size.width - 320) / 2 + Double(i) * 80,
                                y: size.height * 0.13, width: 80, height: 110)
            var digits = ctx
            digits.clip(to: Path(column))
            let row = Int(floor(pos))
            for r in row...min(row + 1, 19) {
                A.draw(A.digits[r % 10], in: &digits, x: column.minX,
                       y: column.minY + (Double(r) - pos) * 110)
            }
        }

        // Draw in the same z-order as the RN prints: outgoing below incoming.
        let outgoing = pow(M.clamp(fraction * 0.8 / 0.45), 3)
        drawPrint(a, in: &ctx, size: size,
                  opacity: 1 - M.clamp((outgoing - 0.7) / 0.3),
                  x: -420 * outgoing, y: 0, rotation: M.eras[a].tilt - 24 * outgoing, scale: 1)
        let incoming = M.clamp((fraction * 0.8 - 0.25) / 0.55)
        let landed = M.back(incoming)
        drawPrint(b, in: &ctx, size: size, opacity: M.clamp(incoming * 4),
                  x: 0, y: -160 * (1 - landed), rotation: M.eras[b].tilt + 14 * (1 - landed),
                  scale: 1.15 - 0.15 * landed)

        for k in M.eras.indices {
            var distance = M.mod(abs(index - Double(k)), 4)
            distance = min(distance, 4 - distance)
            let opacity = 1 - M.clamp(distance / 0.5)
            guard opacity > 0 else { continue }
            var caption = ctx
            caption.opacity = opacity
            A.draw(artwork.titles[k], in: &caption,
                   x: (size.width - artwork.titles[k].size.width) / 2,
                   y: size.height * 0.5 + 168 + 8 * (1 - opacity))
            A.draw(artwork.descriptions[k], in: &caption, x: 32,
                   y: size.height * 0.5 + 168 + artwork.titles[k].size.height + 6 + 8 * (1 - opacity))
        }

        let labelHeight = artwork.labels[0].size.height
        let trackTop = size.height - safe.bottom - 18 - labelHeight - 8 - 18
        let trackWidth = size.width - 64
        ctx.fill(Path(CGRect(x: 32, y: trackTop + 8, width: trackWidth, height: 2)),
                 with: .color(M.ink.opacity(0.25)))
        for k in M.eras.indices {
            let x = 32 + Double(k) / 3 * trackWidth
            ctx.fill(Path(CGRect(x: x - 1, y: trackTop + 4, width: 2, height: 10)),
                     with: .color(M.ink.opacity(0.45)))
            // RN's labels use space-between in a row with -14 pt horizontal margins.
            let totalLabelWidth = artwork.labels.reduce(0) { $0 + $1.size.width }
            let gap = (trackWidth + 28 - totalLabelWidth) / 3
            let labelX = 18 + Double(k) * (artwork.labels[k].size.width + gap)
            A.draw(artwork.labels[k], in: &ctx, x: labelX, y: trackTop + 26)
        }
        let marker = Double(a) + Double(b - a) * eased
        ctx.fill(Path(ellipseIn: CGRect(x: 32 + marker / 3 * trackWidth - 7,
                                       y: trackTop + 2, width: 14, height: 14)),
                 with: .color(M.ink))
    }

    private func drawPrint(_ k: Int, in ctx: inout GraphicsContext, size: CGSize,
                           opacity: Double, x: Double, y: Double, rotation: Double, scale: Double) {
        guard opacity > 0 else { return }
        var card = ctx
        card.opacity = opacity
        card.translateBy(x: size.width / 2 + x, y: size.height * 0.5 - 9 + y)
        card.rotate(by: .degrees(rotation))
        card.scaleBy(x: scale, y: scale)
        let paper = Path(CGRect(x: -126, y: -156, width: 252, height: 312))
        card.drawLayer { shadow in
            shadow.addFilter(.shadow(color: .black.opacity(0.22), radius: 14, x: 0, y: 8))
            shadow.fill(paper, with: .color(M.color(0xFBF8F1)))
        }
        card.clip(to: paper)
        // Aspect-fill the inner box, leaving 10 pt borders and a 30 pt bottom margin.
        if let image = A.photos[k] {
            var photo = card
            let target = CGRect(x: -116, y: -146, width: 232, height: 272)
            photo.clip(to: Path(target))
            let ratio = max(target.width / image.size.width, target.height / image.size.height)
            let rect = CGRect(x: target.midX - image.size.width * ratio / 2,
                              y: target.midY - image.size.height * ratio / 2,
                              width: image.size.width * ratio, height: image.size.height * ratio)
            photo.draw(Image(uiImage: image), in: rect)
        }
        let caption = A.printLabels[k]
        A.draw(caption, in: &card, x: -114, y: 156 - 9 - caption.size.height)
    }
}

private enum YearPhotoCounterMotion {
    static let stop = 2.2
    static let per = 240.0
    static let snap = 0.4
    static let hold = 2.0
    static let ink = color(0x1E1A16)

    struct Era {
        let year: Int
        let title: String
        let text: String
        let background: UInt32
        let tilt: Double
    }

    static let eras = [
        Era(year: 1905, title: "The box camera", text: "Folded wood and leather. One roll, eight pictures, a week to develop.", background: 0xECE5D8, tilt: -5),
        Era(year: 1957, title: "The city scooter", text: "Mint paint, chrome trim and a spare wheel strapped to the back.", background: 0xE1ECE3, tilt: 4),
        Era(year: 1984, title: "The pocket tape player", text: "Ninety minutes of music, and a pencil to wind the tape back.", background: 0xEFE0E4, tilt: -3),
        Era(year: 2021, title: "The wireless earbuds", text: "Two grams each. Lost behind the sofa within a month.", background: 0xDFE4EE, tilt: 6)
    ]

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func back(_ x: Double) -> Double { 1 + 2.4 * pow(x - 1, 3) + 1.4 * pow(x - 1, 2) }
    static func wrap(_ x: Int) -> Int { ((x % 4) + 4) % 4 }
    static func mod(_ x: Double, _ n: Double) -> Double { (x.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n) }
    static func index(at t: Double) -> Double {
        let k = floor(t / stop)
        return k + clamp((t - k * stop - 1.4) / 0.8)
    }
    static func digit(_ year: Int, _ i: Int) -> Int { year / [1000, 100, 10, 1][i] % 10 }
    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: 1)
    }
    static func mix(_ a: UInt32, _ b: UInt32, _ u: Double) -> Color {
        func channel(_ shift: UInt32) -> UInt32 {
            UInt32((Double((a >> shift) & 255) * (1 - u) + Double((b >> shift) & 255) * u).rounded())
        }
        return color(channel(16) << 16 | channel(8) << 8 | channel(0))
    }
}

private enum YearPhotoCounterArtwork {
    private typealias M = YearPhotoCounterMotion
    private static let registered: Void = {
        for name in ["ArchivoExpanded-Black", "JetBrainsMono-Medium"] {
            if let url = Bundle.main.url(forResource: "YearPhotoCounter-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    static func font(_ size: CGFloat, mono: Bool = false) -> UIFont {
        _ = registered
        return UIFont(name: mono ? "JetBrainsMono-Medium" : "ArchivoExpanded-Black", size: size)
            ?? .systemFont(ofSize: size, weight: mono ? .medium : .black)
    }

    // Decode once; each animated frame only changes a canvas transform.
    static let photos: [UIImage?] = M.eras.map { era in
        guard let url = Bundle.main.url(forResource: "YearPhotoCounter-year-\(era.year)", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return image.preparingForDisplay() ?? image
    }

    static let digits: [UIImage] = (0...9).map { digit in
        let font = font(92)
        return raster(size: CGSize(width: 80, height: 110)) { ctx in
            let line = CTLineCreateWithAttributedString(NSAttributedString(string: String(digit), attributes: [
                .font: font, .foregroundColor: UIColor(red: 30 / 255, green: 26 / 255, blue: 22 / 255, alpha: 1)
            ]))
            let width = CTLineGetTypographicBounds(line, nil, nil, nil)
            let baseline = (110 - font.lineHeight) / 2 + font.ascender
            ctx.translateBy(x: 0, y: 110)
            ctx.scaleBy(x: 1, y: -1)
            ctx.textPosition = CGPoint(x: (80 - width) / 2, y: 110 - baseline)
            CTLineDraw(line, ctx)
        }
    }

    static let printLabels: [UIImage] = M.eras.enumerated().map { k, era in
        text(String(format: "No. %02d · %d", k + 1, era.year), size: 10, mono: true, opacity: 0.6)
    }

    struct Layout {
        let brand: UIImage
        let meta: UIImage
        let titles: [UIImage]
        let descriptions: [UIImage]
        let labels: [UIImage]
    }
    private static var layouts: [CGFloat: Layout] = [:]

    static func layout(width: CGFloat) -> Layout {
        if let cached = layouts[width] { return cached }
        let result = Layout(
            brand: text("Carry On", size: 16),
            meta: text("Room 3 · 1905 – 2021", size: 12, mono: true, opacity: 0.6),
            titles: M.eras.map { text($0.title, size: 18) },
            descriptions: M.eras.map { text($0.text, size: 13, mono: true, opacity: 0.72, width: width - 64, lineHeight: 19) },
            labels: M.eras.map { text(String($0.year), size: 12, mono: true, opacity: 0.7) }
        )
        layouts[width] = result
        return result
    }

    static func text(_ string: String, size: CGFloat, mono: Bool = false, opacity: CGFloat = 1,
                     width: CGFloat? = nil, lineHeight: CGFloat? = nil) -> UIImage {
        let face = font(size, mono: mono)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = width == nil ? .left : .center
        if let lineHeight {
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
        }
        var attributes: [NSAttributedString.Key: Any] = [
            .font: face, .paragraphStyle: paragraph,
            .foregroundColor: UIColor(red: 30 / 255, green: 26 / 255, blue: 22 / 255, alpha: opacity)
        ]
        if let lineHeight { attributes[.baselineOffset] = (lineHeight - face.lineHeight) / 2 }
        let attributed = NSAttributedString(string: string, attributes: attributes)
        let bounds = attributed.boundingRect(with: CGSize(width: width ?? 2000, height: 1000),
                                             options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        let imageSize = CGSize(width: width ?? ceil(bounds.width), height: ceil(bounds.height))
        return raster(size: imageSize) { _ in
            attributed.draw(with: CGRect(origin: .zero, size: imageSize),
                            options: [.usesLineFragmentOrigin, .usesFontLeading], context: nil)
        }
    }

    static func raster(size: CGSize, draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        return UIGraphicsImageRenderer(size: size, format: format).image { renderer in draw(renderer.cgContext) }
    }

    static func draw(_ image: UIImage, in ctx: inout GraphicsContext, x: CGFloat, y: CGFloat) {
        ctx.draw(Image(uiImage: image), in: CGRect(origin: CGPoint(x: x, y: y), size: image.size))
    }
}

#Preview {
    YearPhotoCounter(autoPlay: true)
}
