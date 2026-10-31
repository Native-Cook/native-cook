import CoreImage
import CoreText
import SwiftUI
import UIKit

// Kiln 26's five conference posters, ported from the React Native reference.
// LOOP = 8 s: five 1.6 s stops, resting for 0.95 s before a 0.65 s change.
// Six slats slide up over 0.4 s each, staggered by 0.05 s, with ease-out cubic.
// A sideways drag scrubs 260 pt per poster. Release snaps in 0.4 s with
// ease-in-out cubic, holds for 2 s, then resumes at the selected poster.
// Artwork is rasterized once; animation frames only composite the poster images.

private enum PosterMorphCarouselMotion {
    static let stop = 1.6
    static let change = 0.95
    static let slats = 6
    static let delay = 0.05
    static let run = 0.4
    static let count = 5
    static let per = 260.0
    static let hold = 2.0
    static let snap = 0.4

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func easeInOut(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func modulo(_ x: Double, _ divisor: Double = 5) -> Double {
        let remainder = x.truncatingRemainder(dividingBy: divisor)
        return (remainder + divisor).truncatingRemainder(dividingBy: divisor)
    }
    // JavaScript Math.round rounds negative half values toward positive infinity.
    static func step(_ x: Double) -> Double { floor(x + 0.5) }
    static func index(at t: Double) -> Double {
        let k = floor(t / stop)
        return k + clamp((t - k * stop - change) / (stop - change))
    }
    static func slat(_ i: Int, fraction: Double) -> Double {
        let span = run + delay * Double(slats - 1)
        return easeOut(clamp((fraction * span - delay * Double(i)) / run))
    }
}

struct PosterMorphCarousel: View {
    var brand = "Kiln 26"
    var action = "Get your ticket"
    var autoPlay = false
    static let loop: Double = 8

    private typealias M = PosterMorphCarouselMotion
    private typealias Art = PosterMorphCarouselArtwork

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var touching = false
    @State private var panActive = false
    @State private var manual = false
    @State private var grabbed = 0.0
    @State private var dragIndex = 0.0
    @State private var from = 0.0
    @State private var snapTo = 0.0
    @State private var released: Date?
    @State private var lastStep = 0.0
    @State private var touchID = 0
    @State private var pausedAt: Date?
    @State private var frozenIndex = 0.0
    @State private var selection = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let width = size.width - 40
            let height = (width * 1.325).rounded()
            let y = size.height * 0.48 - height / 2
            let posters = Art.posters(width: width, height: height)

            ZStack(alignment: .topLeading) {
                Color(red: 20 / 255, green: 20 / 255, blue: 22 / 255)

                TimelineView(.animation(paused: pausedAt != nil || (!autoPlay && !manual))) { context in
                    let index = index(at: context.date)
                    ZStack(alignment: .topLeading) {
                        Canvas { ctx, _ in
                            let base = floor(index)
                            let current = Int(M.modulo(base))
                            let next = (current + 1) % M.count
                            let fraction = index - base
                            let rect = CGRect(x: 20, y: y, width: width, height: height)

                            ctx.drawLayer { shadow in
                                shadow.addFilter(.blur(radius: 22))
                                shadow.fill(Path(roundedRect: CGRect(x: 30, y: y + 24, width: width - 20, height: height), cornerRadius: 18), with: .color(.black.opacity(0.5)))
                            }
                            ctx.draw(Image(uiImage: posters[current]), in: rect)
                            if fraction > 0 {
                                let sw = width / CGFloat(M.slats)
                                for slat in 0..<M.slats {
                                    let p = M.slat(slat, fraction: fraction)
                                    if p <= 0 { continue }
                                    var strip = ctx
                                    strip.clip(to: Path(CGRect(x: 20 + CGFloat(slat) * sw - 0.5, y: y, width: sw + 1, height: height)))
                                    // Clip before translating: each baked poster keeps its own corners.
                                    strip.draw(Image(uiImage: posters[next]), in: rect.offsetBy(dx: 0, dy: (1 - p) * height))
                                }
                            }
                        }
                        .frame(width: size.width, height: size.height)
                        .accessibilityLabel("Conference posters. Swipe to change poster.")

                        HStack(spacing: 0) {
                            Text("Lisbon · 12–16 May 2026")
                                .font(.system(size: 13, weight: .semibold))
                                .foregroundStyle(.white.opacity(0.6))
                            Spacer(minLength: 0)
                            counter(index: index)
                        }
                        .frame(width: width, height: 20)
                        .offset(x: 20, y: y + height + 14)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }

                HStack {
                    Text(brand).font(Art.font(18)).foregroundStyle(.white)
                    Spacer()
                    Text("Poster wall")
                        .font(.system(size: 14, weight: .semibold))
                        .foregroundStyle(.white.opacity(0.55))
                }
                .frame(width: width, height: 34)
                .offset(x: 20, y: safe.top + 8)

                Text("Five posters for five days of making")
                    .font(.system(size: 13, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.6))
                    .frame(width: width, alignment: .top)
                    .offset(x: 20, y: y - 30)

                Text(action)
                    .font(Art.font(15))
                    .foregroundStyle(Color(red: 20 / 255, green: 20 / 255, blue: 22 / 255))
                    .frame(width: width, height: 58)
                    .background(.white, in: RoundedRectangle(cornerRadius: 29, style: .circular))
                    .offset(x: 20, y: size.height - safe.bottom - 14 - 58)
                    .accessibilityAddTraits(.isButton)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Rectangle())
            .gesture(drag(buttonTop: size.height - safe.bottom - 72, width: size.width))
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            origin = .now
            selection.prepare()
            if reduceMotion || scenePhase != .active {
                frozenIndex = 0
                pausedAt = .now
            }
        }
        .onChange(of: reduceMotion) { _, _ in updatePause() }
        .onChange(of: scenePhase) { _, _ in updatePause() }
        .task(id: touchID) { await finishManual() }
    }

    private func counter(index: Double) -> some View {
        HStack(spacing: 0) {
            VStack(spacing: 0) {
                ForEach(0..<6) { row in
                    Text(String(format: "%02d", row % 5 + 1))
                        .font(Art.font(14))
                        .foregroundStyle(.white)
                        .frame(height: 20)
                }
            }
            .offset(y: -20 * M.modulo(index))
            .frame(height: 20, alignment: .top)
            .clipped()

            Text(" / 05")
                .font(Art.font(14))
                .foregroundStyle(.white.opacity(0.45))
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Poster \(Int(M.modulo(M.step(index))) + 1) of 5")
    }

    private func index(at date: Date) -> Double {
        if pausedAt != nil { return frozenIndex }
        if touching { return dragIndex }
        if manual, let released {
            let since = max(0, date.timeIntervalSince(released))
            if since < M.snap {
                return from + (snapTo - from) * M.easeInOut(since / M.snap)
            }
            if since <= M.snap + M.hold || !autoPlay { return snapTo }
            let elapsed = since - M.snap - M.hold + M.modulo(snapTo) * M.stop
            return M.index(at: M.modulo(elapsed, Self.loop))
        }
        guard autoPlay else { return snapTo }
        return M.index(at: M.modulo(max(0, date.timeIntervalSince(origin)), Self.loop))
    }

    private func drag(buttonTop: CGFloat, width: CGFloat) -> some Gesture {
        // RN's onBegin pauses at touch-down; onUpdate starts after 4 pt.
        // Its footer sits outside the gesture detector.
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                guard value.startLocation.y < buttonTop || value.startLocation.x < 20 || value.startLocation.x > width - 20 else { return }
                if !touching {
                    let current = index(at: .now)
                    touching = true
                    panActive = false
                    manual = true
                    grabbed = current
                    dragIndex = current
                    lastStep = M.step(current)
                    touchID += 1
                }
                if hypot(value.translation.width, value.translation.height) >= 4 { panActive = true }
                guard panActive else { return }
                let nextIndex = grabbed - value.translation.width / M.per
                dragIndex = nextIndex
                if M.step(nextIndex) != lastStep {
                    lastStep = M.step(nextIndex)
                    selection.selectionChanged()
                    selection.prepare()
                }
            }
            .onEnded { _ in
                guard touching else { return }
                finalizeTouch(at: .now)
            }
    }

    private func finalizeTouch(at date: Date) {
        touching = false
        panActive = false
        from = dragIndex
        snapTo = M.step(dragIndex)
        released = date
        touchID += 1
    }

    private func finishManual() async {
        guard manual, !touching, let release = released else { return }
        let remaining = max(0, M.snap + M.hold - Date().timeIntervalSince(release))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, !touching, pausedAt == nil else { return }
        origin = release.addingTimeInterval(M.snap + M.hold - M.modulo(snapTo) * M.stop)
        manual = false
    }

    private func updatePause() {
        let now = Date()
        if (reduceMotion || scenePhase != .active), touching { finalizeTouch(at: now) }
        if reduceMotion || scenePhase != .active {
            guard pausedAt == nil else { return }
            frozenIndex = index(at: now)
            pausedAt = now
        } else if let pause = pausedAt {
            let duration = now.timeIntervalSince(pause)
            origin = origin.addingTimeInterval(duration)
            if let release = released { released = release.addingTimeInterval(duration) }
            pausedAt = nil
            if manual, !touching { touchID += 1 }
        }
    }
}

private enum PosterMorphCarouselArtwork {
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "PosterMorphCarousel-ArchivoExpanded-Black", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    static let face: UIFont = {
        _ = registered
        return UIFont(name: "ArchivoExpanded-Black", size: 100) ?? .systemFont(ofSize: 100, weight: .black)
    }()
    static let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
    private static var cache: [String: [UIImage]] = [:]

    static func font(_ size: CGFloat) -> Font {
        _ = registered
        return .custom("ArchivoExpanded-Black", fixedSize: size)
    }
    static func color(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
    static func gradient(_ colors: [UIColor]) -> CGGradient {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map(\.cgColor) as CFArray, locations: nil)!
    }
    static let extend: CGGradientDrawingOptions = [.drawsBeforeStartLocation, .drawsAfterEndLocation]

    static func posters(width w: CGFloat, height h: CGFloat) -> [UIImage] {
        let key = "\(w)|\(h)"
        if let hit = cache[key] { return hit }
        let format = UIGraphicsImageRendererFormat()
        format.scale = UIScreen.main.scale
        format.preferredRange = .standard
        let images = (0..<5).map { i in
            UIGraphicsImageRenderer(size: CGSize(width: w, height: h), format: format).image { renderer in
                let c = renderer.cgContext
                c.addPath(UIBezierPath(roundedRect: CGRect(x: 0, y: 0, width: w, height: h), cornerRadius: 18).cgPath)
                c.clip()
                switch i {
                case 0: now(c, w, h)
                case 1: next(c, w, h)
                case 2: way(c, w, h)
                case 3: isNow(c, w, h)
                default: make(c, w, h)
                }
            }
        }
        cache[key] = images
        return images
    }

    static func background(_ c: CGContext, _ w: CGFloat, _ h: CGFloat, _ a: UInt32, _ b: UInt32) {
        c.drawLinearGradient(gradient([color(a), color(b)]), start: .zero, end: CGPoint(x: w * 0.4, y: h), options: extend)
    }
    static func blob(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat, _ light: UInt32, _ deep: UInt32) {
        c.saveGState()
        c.addEllipse(in: CGRect(x: x - r, y: y - r, width: r * 2, height: r * 2))
        c.clip()
        let center = CGPoint(x: x - r * 0.35, y: y - r * 0.4)
        c.drawRadialGradient(gradient([color(light), color(deep)]), startCenter: center, startRadius: 0,
                             endCenter: center, endRadius: r * 1.5, options: extend)
        c.restoreGState()
    }
    static func soft(_ c: CGContext, _ x: CGFloat, _ y: CGFloat, _ r: CGFloat) {
        let padding: CGFloat = 72
        let side = 2 * (r + padding)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.preferredRange = .standard
        let source = UIGraphicsImageRenderer(size: CGSize(width: side, height: side), format: format).image { renderer in
            color(0xFFAA28, alpha: 0.75).setFill()
            renderer.cgContext.fillEllipse(in: CGRect(x: padding, y: padding, width: r * 2, height: r * 2))
        }
        if let input = CIImage(image: source),
           let result = ci.createCGImage(input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 36]), from: input.extent) {
            let image = UIImage(cgImage: result, scale: 2, orientation: .up)
            UIGraphicsPushContext(c)
            image.draw(in: CGRect(x: x - r - padding, y: y - r - padding, width: side, height: side))
            UIGraphicsPopContext()
        }
    }

    // Skia's unshaped drawText uses advances at size 100. Its default non-subpixel
    // font rounds advances. The CoreText backend rounds glyph bounds outward and
    // adds one pixel of antialias padding on every side, also used by fit().
    static func glyphs(_ string: String) -> (glyphs: [CGGlyph], positions: [CGPoint], bounds: CGRect) {
        let font = face as CTFont
        let chars = Array(string.utf16)
        var glyphs = [CGGlyph](repeating: 0, count: chars.count)
        CTFontGetGlyphsForCharacters(font, chars, &glyphs, chars.count)
        var advances = [CGSize](repeating: .zero, count: glyphs.count)
        CTFontGetAdvancesForGlyphs(font, .horizontal, glyphs, &advances, glyphs.count)
        var positions: [CGPoint] = []
        var bounds = CGRect.null
        var x: CGFloat = 0
        for i in glyphs.indices {
            positions.append(CGPoint(x: x, y: 0))
            var glyph = glyphs[i]
            let box = CTFontGetBoundingRectsForGlyphs(font, .horizontal, &glyph, nil, 1)
            if box.width > 0 {
                let rounded = CGRect(x: floor(box.minX), y: floor(box.minY), width: ceil(box.maxX) - floor(box.minX), height: ceil(box.maxY) - floor(box.minY))
                bounds = bounds.union(rounded.insetBy(dx: -1, dy: -1).offsetBy(dx: x, dy: 0))
            }
            x += advances[i].width.rounded()
        }
        return (glyphs, positions, bounds)
    }
    static func width(_ string: String, size: CGFloat = 100) -> CGFloat { glyphs(string).bounds.width * size / 100 }
    static func fit(_ string: String, _ target: CGFloat) -> CGFloat { target / width(string) * 100 }

    static func word(_ c: CGContext, _ text: String, _ x: CGFloat, _ y: CGFloat, _ size: CGFloat,
                     _ ink: UInt32 = 0xFFFFFF, alpha: CGFloat = 1, gradientInk: Bool = false, width w: CGFloat = 0) {
        let run = glyphs(text)
        c.saveGState()
        c.translateBy(x: x, y: y)
        c.scaleBy(x: size / 100, y: -size / 100)
        if gradientInk {
            let path = CGMutablePath()
            for i in run.glyphs.indices {
                var transform = CGAffineTransform(translationX: run.positions[i].x, y: 0)
                if let glyph = CTFontCreatePathForGlyph(face as CTFont, run.glyphs[i], &transform) { path.addPath(glyph) }
            }
            c.addPath(path)
            c.clip()
            // Skia applies this shader in the size-100 text coordinate space.
            c.drawLinearGradient(gradient([color(0x5BE7FF), color(0xB48CFF), .white]), start: .zero, end: CGPoint(x: w, y: 0), options: extend)
        } else {
            c.setFillColor(color(ink, alpha: alpha).cgColor)
            CTFontDrawGlyphs(face as CTFont, run.glyphs, run.positions, run.glyphs.count, c)
        }
        c.restoreGState()
    }

    static func now(_ c: CGContext, _ w: CGFloat, _ h: CGFloat) {
        background(c, w, h, 0x2F5BFF, 0x62B6FF)
        blob(c, w * 0.3, h * 0.22, w * 0.32, 0xFFE07A, 0xFF7A00)
        blob(c, w * 0.72, h * 0.12, w * 0.2, 0xFFD36B, 0xF29100)
        soft(c, w * 0.85, h * 0.45, w * 0.16)
        word(c, "2026", 20, h * 0.64, 34)
        word(c, "NOW", 18, h * 0.88, fit("NOW", w - 36))
        word(c, "KILN 26", 20, h - 18, 11, alpha: 0.85)
    }
    static func next(_ c: CGContext, _ w: CGFloat, _ h: CGFloat) {
        background(c, w, h, 0xFFFFFF, 0xF4F1EC)
        blob(c, w * 0.25, h * 0.15, w * 0.2, 0xFFB15C, 0xFF6A2B)
        blob(c, w * 0.5, h * 0.2, w * 0.24, 0xB48CFF, 0x5B2BE8)
        blob(c, w * 0.78, h * 0.12, w * 0.18, 0xFF8AC4, 0xE8207A)
        blob(c, w * 0.35, h * 0.33, w * 0.12, 0xFF9A9A, 0xD8264A)
        c.setFillColor(color(0x3D7BFF).cgColor)
        c.fill(CGRect(x: w * 0.16, y: h * 0.62, width: w * 0.1, height: h * 0.38))
        blob(c, w * 0.7, h * 0.95, w * 0.28, 0xFFE36B, 0xFF9E1A)
        blob(c, w * 0.42, h * 0.98, w * 0.16, 0xFF6B8B, 0xD11A4A)
        word(c, "Kiln", 24, h * 0.43, 18, 0x5F6368)
        word(c, "Next", 22, h * 0.56, fit("Next", w * 0.6), 0x111111)
    }
    static func way(_ c: CGContext, _ w: CGFloat, _ h: CGFloat) {
        background(c, w, h, 0xFF5FA2, 0xFF9E2B)
        blob(c, w * 0.42, h * 0.42, w * 0.3, 0x7CFFB8, 0x00A86B)
        blob(c, w * 0.7, h * 0.6, w * 0.26, 0x64E1FF, 0x0072FF)
        blob(c, w * 0.3, h * 0.7, w * 0.24, 0x9BFF6B, 0x1DB954)
        blob(c, w * 0.65, h * 0.3, w * 0.18, 0x5BE7C4, 0x008F7A)
        let size = fit("The new", w - 40)
        word(c, "The new", 20, h * 0.16, size)
        word(c, "way to", w - 20 - width("way to", size: size), h * 0.56, size)
        word(c, "build", 20, h - 24, size)
    }
    static func isNow(_ c: CGContext, _ w: CGFloat, _ h: CGFloat) {
        background(c, w, h, 0x1C1C1F, 0x121214)
        c.setStrokeColor(color(0x7CFF4D).cgColor)
        c.setLineWidth(16)
        c.setLineCap(.round)
        c.move(to: CGPoint(x: w * 0.8, y: h * 0.08))
        c.addLine(to: CGPoint(x: w * 0.8, y: h * 0.22))
        c.strokePath()
        c.move(to: CGPoint(x: w * 0.73, y: h * 0.15))
        c.addLine(to: CGPoint(x: w * 0.87, y: h * 0.15))
        c.strokePath()
        c.saveGState()
        let center = CGPoint(x: w * 0.52, y: h * 0.16)
        let ring = CGMutablePath()
        ring.addEllipse(in: CGRect(x: center.x - w * 0.09, y: center.y - w * 0.09, width: w * 0.18, height: w * 0.18))
        c.addPath(ring.copy(strokingWithWidth: 13, lineCap: .butt, lineJoin: .miter, miterLimit: 10))
        c.clip()
        // The sweep shader is baked into narrow wedges once with the artwork.
        let colors: [(CGFloat, CGFloat, CGFloat)] = [(1, 95 / 255, 162 / 255), (1, 213 / 255, 77 / 255), (107 / 255, 184 / 255, 1), (1, 95 / 255, 162 / 255)]
        for i in 0..<720 {
            let fraction = CGFloat(i) / 720 * 3
            let segment = min(2, Int(fraction))
            let t = fraction - CGFloat(segment)
            let a = colors[segment], b = colors[segment + 1]
            c.setFillColor(UIColor(red: a.0 + (b.0 - a.0) * t, green: a.1 + (b.1 - a.1) * t, blue: a.2 + (b.2 - a.2) * t, alpha: 1).cgColor)
            c.move(to: center)
            c.addArc(center: center, radius: w * 0.15, startAngle: CGFloat(i) / 720 * 2 * .pi, endAngle: CGFloat(i + 1) / 720 * 2 * .pi + 0.002, clockwise: false)
            c.closePath()
            c.fillPath()
        }
        c.restoreGState()
        for i in 0..<4 {
            let angle = CGFloat(i) * .pi / 2 + .pi / 4
            blob(c, w * 0.66 + cos(angle) * 16, h * 0.27 + sin(angle) * 16, 15, 0xFFB84D, 0xFF7A00)
        }
        c.setFillColor(color(0x3D6BFF).cgColor)
        for i in 0..<5 {
            for j in 0..<5 where (i + j * 2) % 3 != 0 {
                c.fill(CGRect(x: w * 0.1 + CGFloat(i) * 12, y: h * 0.1 + CGFloat(j) * 12, width: 10, height: 10))
            }
        }
        let size = fit("now", w - 40)
        word(c, "is", (w - width("is", size: size)) / 2, h * 0.68, size)
        word(c, "now", 20, h - 24, size, gradientInk: true, width: w)
    }
    static func band(_ c: CGContext, _ points: [CGFloat], _ ink: UInt32) {
        c.setFillColor(color(ink).cgColor)
        c.move(to: CGPoint(x: points[0], y: points[1]))
        for i in stride(from: 2, to: points.count, by: 2) { c.addLine(to: CGPoint(x: points[i], y: points[i + 1])) }
        c.closePath()
        c.fillPath()
    }
    static func tube(_ c: CGContext, _ points: [CGFloat], _ width: CGFloat, _ light: UInt32, _ deep: UInt32) {
        let path = CGMutablePath()
        path.move(to: CGPoint(x: points[0], y: points[1]))
        path.addCurve(to: CGPoint(x: points[6], y: points[7]), control1: CGPoint(x: points[2], y: points[3]), control2: CGPoint(x: points[4], y: points[5]))
        c.saveGState()
        c.addPath(path.copy(strokingWithWidth: width, lineCap: .round, lineJoin: .miter, miterLimit: 10))
        c.clip()
        // Mirror the shader beyond each end of its vertical gradient interval.
        let start = points[1] - width
        let end = points[1] + width
        let colors = [color(light), color(deep)]
        for i in -4...4 {
            let reverse = abs(i) % 2 == 1
            c.drawLinearGradient(gradient(reverse ? colors.reversed().map { $0 } : colors),
                                 start: CGPoint(x: 0, y: start + CGFloat(i) * (end - start)),
                                 end: CGPoint(x: 0, y: end + CGFloat(i) * (end - start)), options: [])
        }
        c.restoreGState()
    }
    static func make(_ c: CGContext, _ w: CGFloat, _ h: CGFloat) {
        background(c, w, h, 0x3B2BFF, 0x5E8BFF)
        band(c, [0, h * 0.15, w * 0.45, 0, w, h * 0.55, w, h * 0.85], 0xFFC21A)
        band(c, [0, h * 0.6, w * 0.5, h * 0.45, w * 0.4, h, 0, h], 0xFF8AC4)
        tube(c, [w * 0.15, h * 0.38, w * 0.4, h * 0.1, w * 0.75, h * 0.2, w * 0.7, h * 0.45], 54, 0xFF8A94, 0xE81A3A)
        tube(c, [w * 0.7, h * 0.45, w * 0.66, h * 0.65, w * 0.4, h * 0.6, w * 0.35, h * 0.72], 44, 0xFFC8E2, 0xF06AA8)
        blob(c, w * 0.2, h * 0.4, 40, 0x8FB8FF, 0x2F5BFF)
        word(c, "Make it", 20, h - 28, fit("Make it", w - 40))
    }
}

#Preview {
    PosterMorphCarousel(autoPlay: true)
}
