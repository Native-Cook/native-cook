import SwiftUI
import UIKit

/*
 Continuous Zoom Grid — a photo library that zooms continuously from 3 large photos per row to
 a contact sheet of 20 per row and back, the way a pinch does it.

 The zoom z (photos per row) is continuous; the grid is drawn from the two layouts either side of
 it in [3, 5, 9, 14, 20]. Each layout keeps its photos square (the screen's width / n) and is
 scaled by n / z about the anchor photo (the one under the fingers), whose centre stays at the
 same height; the closer layout fades in over the middle of each step (smoothstep 0.2 – 0.8 of
 the step in log space) while the other fades out. Small photos are their sky over their ground;
 from 22 pt their picture fades in (fully drawn at 44 pt), so the zoom reads like mipmaps
 cross-fading. Month chips sit on each month's first photo below 64 pt (gone by 64, full at 34);
 video lengths show on photos above 60 pt.

 The 3,000 photos are drawn in code from their index (ContinuousZoomGridModel.swift): one of eight
 palettes, one of eight kinds of picture (sun over the sea, hills in haze, city lights out of focus,
 dunes, pines in mist, petals close up, a building in low sun, a beach) and a few shape
 parameters, all from an integer hash; every picture gets a soft vignette. Photos come in runs that
 share a kind and a palette, and months are 30 to 109 photos long, newest first.

 Chrome: a white screen; a header (round back button, "Library" over the month in the middle of
 the screen, a Select pill) over a white fade; a floating glass control at the bottom (Years ·
 Months · All Photos) whose highlight follows the zoom.

 autoPlay timeline (loop = 8 s):
   0.0 – 1.0   rest: 3 per row, photo 901 centred at half height
   1.0 – 2.8   zoom out to 20 per row about it (z = 3 · (20/3)^e, e easeInOut)
   3.4 – 4.4   the grid is dragged down 262 pt, until photo 906 sits where it rests at 3 per row
   4.6 – 6.4   zoom back in to 3 per row about photo 906 (cubic in, out with a 5 % overshoot)
   6.4 – 8.0   rest (the same frame as 0)

 Touch: pinch to zoom about the photo between the fingers (rubber band past 3 and 20 per row; on
 release it settles on the nearest layout in 0.35 s, ease out, with a light haptic); drag to
 scroll (thrown on release, 0.5 s ease out). 2 s after the last touch the grid zooms back to 3 per
 row (0.6 s), then scrolls back to its resting place (0.6 s) and the loop starts over.
 Reduced motion: no loop; a pinch still zooms but lands on the nearest layout at once, and drags
 scroll without the throw.
 */

struct ContinuousZoomGrid: View {
    var autoPlay = false

    static let loop = ContinuousZoomGridModel.loop
    typealias M = ContinuousZoomGridModel
    typealias V = ContinuousZoomGridView

    struct Tween {
        var from: Double, to: Double, at: Double, dur: Double
        init(_ v: Double) { from = v; to = v; at = 0; dur = 1 }
        init(from: Double, to: Double, at: Double, dur: Double) { self.from = from; self.to = to; self.at = at; self.dur = dur }
        /// Ease out (cubic).
        func value(_ now: Double) -> Double {
            let u = M.clamp01((now - at) / dur)
            return from + (to - from) * (1 - pow(1 - u, 3))
        }
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale

    @State private var autoStart: Double? = nil
    @State private var returning: (at: Double, z: Double, anchor: Int, fy: Double)? = nil
    /// The manual zoom, kept as log z so a settle is even across layouts.
    @State private var zoom = Tween(log(3))
    @State private var anchor = M.restAnchor
    @State private var fy = Tween(0)
    @State private var pinchStart: Double? = nil
    @State private var dragStart: Double? = nil
    @State private var lastLevel = 0
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @State private var tick = UISelectionFeedbackGenerator()

    private static func now() -> Double { Date.timeIntervalSinceReferenceDate }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            TimelineView(.animation(paused: reduceMotion && pinchStart == nil && dragStart == nil)) { timeline in
                let now = timeline.date.timeIntervalSinceReferenceDate
                let v = view(now, size: size)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, _ in
                        ContinuousZoomGridDraw.grid(&ctx, size: size, top: insets.top, v: v)
                    }
                    .frame(width: size.width, height: size.height)
                    .contentShape(Rectangle())
                    .gesture(SimultaneousGesture(pinch(size: size), drag(size: size)))
                    .accessibilityElement()
                    .accessibilityLabel("Photo library, \(Int(v.z.rounded())) photos per row, \(ContinuousZoomGridDraw.centreMonth(size: size, v: v))")
                    chrome(size: size, insets: insets, v: v)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
            }
            .ignoresSafeArea()
            .onAppear {
                fy = Tween(size.height * 0.5)
                haptic.prepare()
                if autoPlay && !reduceMotion { autoStart = Self.now() }
                ContinuousZoomGridAtlas.shared.prewarm(size: size, scale: displayScale, loop: autoPlay && !reduceMotion)
            }
        }
        .background(Color(red: 0.98, green: 0.98, blue: 0.98).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { resume?.cancel() }
    }

    // MARK: State

    private func view(_ now: Double, size: CGSize) -> V {
        if let autoStart, now >= autoStart, !reduceMotion { return M.view(at: now - autoStart, w: size.width, h: size.height) }
        if let r = returning {
            let t = now - r.at
            if t < 0.6 {
                return V(z: r.z * pow(3 / r.z, M.easeInOut(t / 0.6)), anchor: r.anchor, fy: r.fy)
            }
            // At 3 per row, switch to the resting photo (seamless) and scroll it home.
            let from = r.fy + Double(M.place(M.restAnchor, 3).row - M.place(r.anchor, 3).row) * size.width / 3
            let u = M.easeInOut((t - 0.6) / 0.6)
            return V(z: 3, anchor: M.restAnchor, fy: from + (size.height * 0.5 - from) * u)
        }
        return V(z: exp(zoom.value(now)), anchor: anchor, fy: fy.value(now))
    }

    private func takeOver(_ now: Double, size: CGSize) {
        resume?.cancel()
        let v = view(now, size: size)
        zoom = Tween(log(v.z))
        anchor = v.anchor
        fy = Tween(v.fy)
        autoStart = nil
        returning = nil
    }

    private func scheduleResume(size: CGSize) {
        resume?.cancel()
        guard autoPlay, !reduceMotion else { return }
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Self.now()
            let v = view(now, size: size)
            returning = (now, v.z, v.anchor, v.fy)
            autoStart = now + 1.2
        }
    }

    /// The nearest layout to z, in log space.
    private func nearestLevel(_ z: Double) -> Int {
        var best = 0
        for (i, l) in M.levels.enumerated() where abs(log(l / z)) < abs(log(M.levels[best] / z)) { best = i }
        return best
    }

    /// Re-anchors on the photo under a point (only ever at rest on a layout, so nothing moves).
    private func anchor(at p: CGPoint, now: Double, size: CGSize) {
        let v = view(now, size: size)
        let n = M.levels[nearestLevel(v.z)]
        if abs(v.z - n) > 0.01 { return }
        let cell = size.width / n
        let ra = M.place(v.anchor, Int(n)).row
        let row = ra + Int(((p.y - v.fy) / cell + 0.5).rounded(.down))
        let col = min(Int(n) - 1, max(0, Int(p.x / cell)))
        let i = min(M.count - 1, max(0, row * Int(n) + col))
        anchor = i
        fy = Tween(v.fy + Double(row - ra) * cell)
    }

    private func pinch(size: CGSize) -> some Gesture {
        MagnifyGesture()
            .onChanged { value in
                let now = Self.now()
                if pinchStart == nil {
                    takeOver(now, size: size)
                    anchor(at: value.startLocation, now: now, size: size)
                    pinchStart = zoom.to
                    lastLevel = nearestLevel(exp(zoom.to))
                }
                guard let start = pinchStart else { return }
                var lz = start - log(value.magnification)
                // Rubber band past the first and last layout.
                let lo = log(M.minZ), hi = log(M.maxZ)
                if lz < lo { lz = lo - 0.25 * (1 - exp((lz - lo) / 0.25)) }
                if lz > hi { lz = hi + 0.25 * (1 - exp(-(lz - hi) / 0.25)) }
                zoom = Tween(lz)
                let level = nearestLevel(exp(lz))
                if level != lastLevel {
                    lastLevel = level
                    tick.selectionChanged()
                }
            }
            .onEnded { _ in
                let now = Self.now()
                pinchStart = nil
                let target = log(M.levels[nearestLevel(exp(zoom.to))])
                zoom = Tween(from: zoom.to, to: target, at: now, dur: reduceMotion ? 0.001 : 0.35)
                haptic.impactOccurred(intensity: 0.6)
                scheduleResume(size: size)
            }
    }

    private func drag(size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 8)
            .onChanged { value in
                guard pinchStart == nil else { return }
                let now = Self.now()
                if dragStart == nil {
                    takeOver(now, size: size)
                    dragStart = fy.to
                }
                if let start = dragStart { fy = Tween(start + value.translation.height) }
            }
            .onEnded { value in
                let now = Self.now()
                guard let start = dragStart else { return }
                dragStart = nil
                let end = start + value.translation.height
                let thrown = start + value.predictedEndTranslation.height
                fy = reduceMotion ? Tween(end) : Tween(from: end, to: thrown, at: now, dur: 0.5)
                scheduleResume(size: size)
            }
    }

    // MARK: Chrome

    private func chrome(size: CGSize, insets: EdgeInsets, v: V) -> some View {
        let ink = Color(red: 0.07, green: 0.07, blue: 0.08)
        let barY = size.height - max(insets.bottom, 16) - 10 - 46
        // Highlight: All Photos at 3 per row, Months at about 8, Years at 20 (log scale).
        let t = M.clamp01(log(v.z / M.minZ) / log(M.maxZ / M.minZ))
        let seg = 2 - 2 * t
        let labels = ["Years", "Months", "All Photos"]
        return ZStack(alignment: .topLeading) {
            HStack {
                Circle().fill(.white)
                    .frame(width: 40, height: 40)
                    .shadow(color: .black.opacity(0.10), radius: 8, y: 2)
                    .overlay(Image(systemName: "chevron.left").font(.system(size: 16, weight: .semibold)).foregroundStyle(ink))
                    .accessibilityLabel("Back")
                Spacer()
                Text("Select")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(ink)
                    .padding(.horizontal, 16)
                    .frame(height: 36)
                    .background(Capsule().fill(.white).shadow(color: .black.opacity(0.10), radius: 8, y: 2))
            }
            .padding(.horizontal, 16)
            .frame(width: size.width)
            .offset(y: insets.top + 4)
            VStack(spacing: 1) {
                Text("Library").font(.system(size: 17, weight: .semibold)).foregroundStyle(ink)
                Text(ContinuousZoomGridDraw.centreMonth(size: size, v: v))
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(ink.opacity(0.5))
                    .monospacedDigit()
            }
            .frame(width: size.width)
            .offset(y: insets.top + 6)
            ZStack(alignment: .leading) {
                Capsule().fill(.white.opacity(0.88))
                    .overlay(Capsule().strokeBorder(.black.opacity(0.06), lineWidth: 1))
                    .shadow(color: .black.opacity(0.12), radius: 14, y: 4)
                Capsule().fill(ink.opacity(0.08))
                    .frame(width: 88, height: 36)
                    .offset(x: 5 + seg * 86.7)
                HStack(spacing: 0) {
                    ForEach(0..<3, id: \.self) { i in
                        let near = max(0, 1 - abs(Double(i) - seg))
                        Text(labels[i])
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(ink.opacity(0.42 + 0.58 * near))
                            .lineLimit(1)
                            .fixedSize()
                            .frame(width: 86.7)
                    }
                }
                .padding(.horizontal, 5)
            }
            .frame(width: 270, height: 46)
            .offset(x: size.width / 2 - 135, y: barY)
        }
        .allowsHitTesting(false)
    }
}

// MARK: Drawing

enum ContinuousZoomGridDraw {
    typealias M = ContinuousZoomGridModel
    typealias V = ContinuousZoomGridView

    static func color(_ c: ContinuousZoomGridRGB, _ a: Double = 1) -> Color {
        Color(red: min(1, max(0, c.r)), green: min(1, max(0, c.g)), blue: min(1, max(0, c.b)), opacity: a)
    }

    /// The two layouts either side of z, the closer one's opacity, and how far between them z is.
    static func layers(_ z: Double) -> [(n: Double, alpha: Double)] {
        let l = M.levels
        if z <= l[0] { return [(l[0], 1)] }
        if z >= l[l.count - 1] { return [(l[l.count - 1], 1)] }
        var k = 0
        while !(z >= l[k] && z < l[k + 1]) { k += 1 }
        let f = log(z / l[k]) / log(l[k + 1] / l[k])
        let b = M.smoothstep(0.2, 0.8, f)
        return [(l[k], min(1, 2 * (1 - b))), (l[k + 1], b)]
    }

    /// The month of the photo in the middle of the screen.
    static func centreMonth(size: CGSize, v: V) -> String {
        let ls = layers(v.z)
        let n = (ls.count > 1 && ls[1].alpha >= 0.5) ? ls[1].n : ls[0].n
        let cell = size.width / n * (n / v.z)
        let ra = M.place(v.anchor, Int(n)).row
        let row = ra + Int(((size.height / 2 - v.fy) / cell + 0.5).rounded(.down))
        return M.month(of: min(M.count - 1, max(0, row * Int(n) + Int(n) / 2)))
    }

    static func grid(_ ctx: inout GraphicsContext, size: CGSize, top: Double, v: V) {
        let w = size.width, h = size.height
        ctx.fill(Path(CGRect(origin: .zero, size: size)), with: .color(Color(red: 0.98, green: 0.98, blue: 0.98)))
        for layer in layers(v.z) where layer.alpha > 0.001 {
            var lc = ctx
            lc.opacity = layer.alpha
            draw(&lc, w: w, h: h, n: layer.n, v: v)
        }
        // White fades under the header and the control.
        let white = Color(red: 0.98, green: 0.98, blue: 0.98)
        ctx.fill(Path(CGRect(x: 0, y: 0, width: w, height: top + 84)),
                 with: .linearGradient(Gradient(stops: [.init(color: white.opacity(0.96), location: 0), .init(color: white.opacity(0.85), location: 0.55),
                                                        .init(color: white.opacity(0), location: 1)]),
                                       startPoint: .zero, endPoint: CGPoint(x: 0, y: top + 84)))
        ctx.fill(Path(CGRect(x: 0, y: h - 140, width: w, height: 140)),
                 with: .linearGradient(Gradient(colors: [white.opacity(0), white.opacity(0.92)]),
                                       startPoint: CGPoint(x: 0, y: h - 140), endPoint: CGPoint(x: 0, y: h)))
    }

    /// One layout of n photos per row, scaled by n / z about its anchor photo.
    static func draw(_ ctx: inout GraphicsContext, w: Double, h: Double, n: Double, v: V) {
        let scale = ctx.environment.displayScale
        let ni = Int(n)
        let cell = w / n
        let k = n / v.z
        let s = cell * k
        let (ra, ca) = M.place(v.anchor, ni)
        let ax = (Double(ca) + 0.5) * cell
        let gap = min(3, max(0.8, s * 0.022))
        let first = max(0, ra + Int(((0 - v.fy) / s).rounded(.down)) - 1)
        let last = min((M.count - 1) / ni, ra + Int(((h - v.fy) / s).rounded(.up)) + 1)
        guard first <= last else { return }
        let detail = M.smoothstep(22, 44, s)
        let chipA = 1 - M.smoothstep(34, 64, s)
        let lengthA = M.smoothstep(60, 90, s)
        var chips: [(CGPoint, String)] = []
        for r in first...last {
            let y = v.fy + Double(r - ra) * s
            for c in 0..<ni {
                let i = r * ni + c
                if i >= M.count { break }
                let x = ax + ((Double(c) + 0.5) * cell - ax) * k
                if x + s / 2 < 0 || x - s / 2 > w { continue }
                let rect = CGRect(x: x - s / 2 + gap / 2, y: y - s / 2 + gap / 2, width: s - gap, height: s - gap)
                if detail < 0.999 {
                    let t = M.tiny(i)
                    ctx.fill(Path(rect), with: .color(color(t.sky)))
                    if t.split < 1 {
                        ctx.fill(Path(CGRect(x: rect.minX, y: rect.minY + rect.height * t.split, width: rect.width, height: rect.height * (1 - t.split))),
                                 with: .color(color(t.ground)))
                    }
                }
                if detail > 0.001 {
                    let duration = M.duration(i)
                    var tc = ctx
                    tc.clip(to: Path(rect))
                    tc.opacity = ctx.opacity * detail
                    // The picture comes from an atlas of bitmaps (drawing hundreds of vector pictures a
                    // frame stalls the renderer while two dense layouts cross-fade); until its page is
                    // ready, a photo is drawn as vectors.
                    if let sprite = ContinuousZoomGridAtlas.shared.sprite(i, px: rect.width * scale) {
                        let k = rect.width / Double(sprite.side)
                        tc.fill(Path(rect), with: .tiledImage(sprite.image, origin: CGPoint(x: rect.minX - sprite.x * k, y: rect.minY - sprite.y * k),
                                                              sourceRect: CGRect(x: 0, y: 0, width: 1, height: 1), scale: k))
                    } else {
                        picture(&tc, M.photo(i), rect)
                    }
                    if duration > 0 && lengthA > 0.01 {
                        let label = String(format: "0:%02d", duration)
                        tc.opacity = ctx.opacity * detail * lengthA
                        tc.draw(Text(label).font(.system(size: 12, weight: .semibold)).monospacedDigit().foregroundColor(.white),
                                at: CGPoint(x: rect.maxX - 7, y: rect.maxY - 6), anchor: .bottomTrailing)
                    }
                }
                if chipA > 0.01, M.monthStarts.contains(i) {
                    chips.append((CGPoint(x: rect.minX + 4, y: rect.minY + 4), M.month(of: i)))
                }
            }
        }
        // Month chips over the first photo of each month.
        for (p, label) in chips {
            let text = ctx.resolve(Text(label).font(.system(size: 11, weight: .semibold)).foregroundColor(Color(red: 0.07, green: 0.07, blue: 0.08)))
            let ts = text.measure(in: CGSize(width: 200, height: 40))
            let box = CGRect(x: p.x, y: p.y, width: ts.width + 14, height: 20)
            var cc = ctx
            cc.opacity *= chipA
            cc.fill(Path(roundedRect: box, cornerRadius: 10), with: .color(.white.opacity(0.94)))
            cc.draw(text, at: CGPoint(x: box.midX, y: box.midY), anchor: .center)
        }
    }

    static func picture(_ ctx: inout GraphicsContext, _ photo: M.Photo, _ r: CGRect) {
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: r.minX + x * r.width, y: r.minY + y * r.height) }
        func box(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
            CGRect(x: r.minX + x * r.width, y: r.minY + y * r.height, width: w * r.width, height: h * r.height)
        }
        for op in photo.ops {
            switch op {
            case let .rect(x, y, rw, rh, c, a):
                ctx.fill(Path(box(x, y, rw, rh)), with: .color(color(c, a)))
            case let .grad(x, y, rw, rh, a, b):
                ctx.fill(Path(box(x, y, rw, rh)), with: .linearGradient(Gradient(colors: [color(a), color(b)]), startPoint: pt(0, y), endPoint: pt(0, y + rh)))
            case let .circle(x, y, rad, c, a):
                let rr = rad * r.width
                ctx.fill(Path(ellipseIn: CGRect(x: pt(x, y).x - rr, y: pt(x, y).y - rr, width: 2 * rr, height: 2 * rr)), with: .color(color(c, a)))
            case let .glow(x, y, rad, c, a):
                let rr = rad * r.width
                ctx.fill(Path(ellipseIn: CGRect(x: pt(x, y).x - rr, y: pt(x, y).y - rr, width: 2 * rr, height: 2 * rr)),
                         with: .radialGradient(Gradient(colors: [color(c, a), color(c, a * 0.45), color(c, 0)]), center: pt(x, y), startRadius: 0, endRadius: rr))
            case let .poly(pts, c, a):
                var p = Path()
                for j in stride(from: 0, to: pts.count, by: 2) {
                    let q = pt(pts[j], pts[j + 1])
                    if j == 0 { p.move(to: q) } else { p.addLine(to: q) }
                }
                p.closeSubpath()
                ctx.fill(p, with: .color(color(c, a)))
            }
        }
        // A lens's falloff towards the corners.
        ctx.fill(Path(r), with: .radialGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.22)]),
                                                center: pt(0.5, 0.5), startRadius: r.width * 0.35, endRadius: r.width * 0.75))
        if photo.duration > 0 {
            ctx.fill(Path(box(0, 0.7, 1, 0.3)), with: .linearGradient(Gradient(colors: [.black.opacity(0), .black.opacity(0.35)]),
                                                                       startPoint: pt(0, 0.7), endPoint: pt(0, 1)))
        }
    }
}

// MARK: Bitmap atlas

/// Every photo's picture rendered once into bitmaps, packed into atlas pages, so a frame of the dense
/// layouts draws a handful of large textures instead of thousands of gradients and shapes (or
/// hundreds of small images, which overflow the renderer's texture cache and are uploaded again every
/// frame). A picture is taken from the largest bitmap size not larger than the size it is drawn at,
/// so the renderer never has to shrink a page; the sizes include each layout's resting size exactly,
/// so a layout at rest is as sharp as when it was drawn as vectors. The bitmaps are drawn with Core
/// Graphics in the same way as `ContinuousZoomGridDraw.picture` (sRGB, the same gradients and shapes).
final class ContinuousZoomGridAtlas: @unchecked Sendable {
    typealias M = ContinuousZoomGridModel
    typealias RGB = ContinuousZoomGridRGB

    static let shared = ContinuousZoomGridAtlas()

    struct Sprite {
        var image: Image
        /// The picture's top-left corner in the page and its side, in pixels.
        var x: Double, y: Double, side: Int
    }

    private struct Level {
        var side: Int, cols: Int
        var pitch: Int { side + 2 }
        var cap: Int { cols * cols }
    }

    private let lock = NSLock()
    private var levels: [Level] = []
    private var pages: [Int: CGImage] = [:]
    /// When each page was last drawn from; past `budget` bytes the longest unused pages go (the
    /// autoPlay loop needs about 180 MB on a 3× screen, so it never loses a page).
    private var lastUse: [Int: Double] = [:]
    private var bytes = 0
    private let budget = 224 << 20
    private var pending = Set<Int>()
    private var warmed = false
    /// Serial and below the main thread's priority: on a slow machine (Xcode Cloud's simulators)
    /// rendering the loop's 180 MB of pages on every core starved the main thread, and the
    /// accessibility query the recording test makes timed out. A page arrives a little later.
    private let queue = DispatchQueue(label: "continuous-zoom-grid.atlas", qos: .utility)

    init() {
        NotificationCenter.default.addObserver(forName: UIApplication.didReceiveMemoryWarningNotification, object: nil, queue: nil) { [weak self] _ in
            guard let self else { return }
            lock.lock()
            pages.removeAll()
            lastUse.removeAll()
            bytes = 0
            warmed = false
            lock.unlock()
        }
    }

    /// The bitmap sides for a screen: the resting picture size of each layout with pictures (3, 5, 9
    /// and 14 per row), the smallest size a picture shows at (22 pt) and sizes between them at most
    /// √2 apart.
    private static func makeLevels(w: Double, scale: Double) -> [Level] {
        var rest = M.levels.dropLast().map { n -> Double in
            let s = w / n
            return (s - min(3, max(0.8, s * 0.022))) * scale
        }
        rest.append((22 - 0.8) * scale)
        rest.sort()
        var sides: [Double] = []
        for (a, b) in zip(rest, rest.dropFirst()) {
            let steps = max(1, Int(ceil(log(b / a) / log(1.415))))
            for k in 0..<steps { sides.append(a * pow(b / a, Double(k) / Double(steps))) }
        }
        sides.append(rest.last!)
        return sides.map { d in
            let side = Int(d.rounded(.down))
            return Level(side: side, cols: min(24, max(1, 1024 / (side + 2))))
        }
    }

    private func setUp(w: Double, scale: Double) {
        lock.lock()
        if levels.isEmpty { levels = Self.makeLevels(w: w, scale: scale) }
        lock.unlock()
    }

    /// The level for a picture drawn `px` pixels wide: the largest bitmap not larger than that.
    private func level(_ px: Double) -> Int {
        var best = 0
        for (k, l) in levels.enumerated() where Double(l.side) <= px + 0.5 { best = k }
        return best
    }

    private func key(_ i: Int, _ lv: Int) -> Int { lv * 100_000 + i / levels[lv].cap }

    func sprite(_ i: Int, px: Double) -> Sprite? {
        lock.lock()
        guard !levels.isEmpty else { lock.unlock(); return nil }
        let lv = level(px)
        let l = levels[lv]
        let k = key(i, lv)
        let page = pages[k]
        if page != nil { lastUse[k] = CACurrentMediaTime() }
        lock.unlock()
        guard let page else {
            request([k])
            return nil
        }
        let j = i % l.cap
        return Sprite(image: Image(decorative: page, scale: 1), x: Double((j % l.cols) * l.pitch + 1), y: Double((j / l.cols) * l.pitch + 1), side: l.side)
    }

    private func request(_ keys: [Int]) {
        lock.lock()
        let todo = keys.filter { pages[$0] == nil && !pending.contains($0) }
        pending.formUnion(todo)
        let lv = levels
        lock.unlock()
        guard !todo.isEmpty else { return }
        queue.async { [self] in
            for key in todo {
                let image = Self.renderPage(lv[key / 100_000], first: (key % 100_000) * lv[key / 100_000].cap)
                lock.lock()
                if let image {
                    pages[key] = image
                    lastUse[key] = CACurrentMediaTime()
                    bytes += image.bytesPerRow * image.height
                    evict()
                }
                pending.remove(key)
                lock.unlock()
            }
        }
    }

    /// Drops the pages unused the longest while over budget (never one drawn in the last second).
    private func evict() {
        guard bytes > budget else { return }
        let now = CACurrentMediaTime()
        for (key, used) in lastUse.sorted(by: { $0.value < $1.value }) {
            guard bytes > budget, now - used > 1 else { break }
            if let image = pages.removeValue(forKey: key) { bytes -= image.bytesPerRow * image.height }
            lastUse.removeValue(forKey: key)
        }
    }

    /// Makes, in the background, the pages the autoPlay loop draws from (or, without the loop, those
    /// of the resting screen).
    func prewarm(size: CGSize, scale: Double, loop: Bool) {
        setUp(w: size.width, scale: scale)
        lock.lock()
        if warmed { lock.unlock(); return }
        warmed = true
        lock.unlock()
        queue.async { [self] in
            var keys = Set<Int>()
            let times = loop ? Array(stride(from: 0.0, to: M.loop, by: 1.0 / 60)) : [0.0]
            for t in times {
                let v = M.view(at: t, w: size.width, h: size.height)
                for layer in ContinuousZoomGridDraw.layers(v.z) where layer.alpha > 0.001 {
                    Self.visit(w: size.width, h: size.height, n: layer.n, v: v) { i, s in
                        let lv = level((s - min(3, max(0.8, s * 0.022))) * scale)
                        keys.insert(key(i, lv))
                    }
                }
            }
            request(keys.sorted())
        }
    }

    /// The photos of one layout that `ContinuousZoomGridDraw.draw` shows with their picture, and their size.
    static func visit(w: Double, h: Double, n: Double, v: ContinuousZoomGridView, _ body: (Int, Double) -> Void) {
        let ni = Int(n)
        let cell = w / n, k = n / v.z, s = cell * k
        guard M.smoothstep(22, 44, s) > 0.001 else { return }
        let (ra, ca) = M.place(v.anchor, ni)
        let ax = (Double(ca) + 0.5) * cell
        let first = max(0, ra + Int(((0 - v.fy) / s).rounded(.down)) - 1)
        let last = min((M.count - 1) / ni, ra + Int(((h - v.fy) / s).rounded(.up)) + 1)
        guard first <= last else { return }
        for r in first...last {
            for c in 0..<ni {
                let i = r * ni + c
                if i >= M.count { break }
                let x = ax + ((Double(c) + 0.5) * cell - ax) * k
                if x + s / 2 < 0 || x - s / 2 > w { continue }
                body(i, s)
            }
        }
    }

    // MARK: Core Graphics

    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!

    private static func cg(_ c: RGB, _ a: Double = 1) -> CGColor {
        CGColor(colorSpace: space, components: [min(1, max(0, c.r)), min(1, max(0, c.g)), min(1, max(0, c.b)), a])!
    }

    private static func gradient(_ colors: [CGColor]) -> CGGradient {
        CGGradient(colorsSpace: space, colors: colors as CFArray, locations: nil)!
    }

    /// One page: `cap` pictures from photo `first` on, each in a cell with a clear pixel round it.
    private static func renderPage(_ l: Level, first: Int) -> CGImage? {
        let px = l.cols * l.pitch
        guard let g = CGContext(data: nil, width: px, height: px, bitsPerComponent: 8, bytesPerRow: 0, space: space,
                                bitmapInfo: CGImageAlphaInfo.premultipliedFirst.rawValue | CGBitmapInfo.byteOrder32Little.rawValue) else { return nil }
        // Top-left origin, like the Canvas.
        g.translateBy(x: 0, y: CGFloat(px))
        g.scaleBy(x: 1, y: -1)
        for j in 0..<l.cap where first + j < M.count {
            let r = CGRect(x: (j % l.cols) * l.pitch + 1, y: (j / l.cols) * l.pitch + 1, width: l.side, height: l.side)
            g.saveGState()
            g.clip(to: r)
            picture(g, M.photo(first + j), r)
            g.restoreGState()
        }
        return g.makeImage()
    }

    private static func picture(_ g: CGContext, _ photo: M.Photo, _ r: CGRect) {
        func pt(_ x: Double, _ y: Double) -> CGPoint { CGPoint(x: r.minX + x * r.width, y: r.minY + y * r.height) }
        func box(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> CGRect {
            CGRect(x: r.minX + x * r.width, y: r.minY + y * r.height, width: w * r.width, height: h * r.height)
        }
        let extend: CGGradientDrawingOptions = [.drawsBeforeStartLocation, .drawsAfterEndLocation]
        func linear(_ area: CGPath, _ colors: [CGColor], _ a: CGPoint, _ b: CGPoint) {
            g.saveGState()
            g.addPath(area)
            g.clip()
            g.drawLinearGradient(gradient(colors), start: a, end: b, options: extend)
            g.restoreGState()
        }
        func radial(_ area: CGPath, _ colors: [CGColor], _ c: CGPoint, _ r0: Double, _ r1: Double) {
            g.saveGState()
            g.addPath(area)
            g.clip()
            g.drawRadialGradient(gradient(colors), startCenter: c, startRadius: r0, endCenter: c, endRadius: r1, options: extend)
            g.restoreGState()
        }
        for op in photo.ops {
            switch op {
            case let .rect(x, y, rw, rh, c, a):
                g.setFillColor(cg(c, a))
                g.fill(box(x, y, rw, rh))
            case let .grad(x, y, rw, rh, a, b):
                linear(CGPath(rect: box(x, y, rw, rh), transform: nil), [cg(a), cg(b)], pt(0, y), pt(0, y + rh))
            case let .circle(x, y, rad, c, a):
                let rr = rad * r.width
                g.setFillColor(cg(c, a))
                g.fillEllipse(in: CGRect(x: pt(x, y).x - rr, y: pt(x, y).y - rr, width: 2 * rr, height: 2 * rr))
            case let .glow(x, y, rad, c, a):
                let rr = rad * r.width
                radial(CGPath(ellipseIn: CGRect(x: pt(x, y).x - rr, y: pt(x, y).y - rr, width: 2 * rr, height: 2 * rr), transform: nil),
                       [cg(c, a), cg(c, a * 0.45), cg(c, 0)], pt(x, y), 0, rr)
            case let .poly(pts, c, a):
                let p = CGMutablePath()
                for j in stride(from: 0, to: pts.count, by: 2) {
                    let q = pt(pts[j], pts[j + 1])
                    if j == 0 { p.move(to: q) } else { p.addLine(to: q) }
                }
                p.closeSubpath()
                g.addPath(p)
                g.setFillColor(cg(c, a))
                g.fillPath()
            }
        }
        // A lens's falloff towards the corners.
        let black = RGB(0, 0, 0)
        radial(CGPath(rect: r, transform: nil), [cg(black, 0), cg(black, 0.22)], pt(0.5, 0.5), r.width * 0.35, r.width * 0.75)
        if photo.duration > 0 {
            linear(CGPath(rect: box(0, 0.7, 1, 0.3), transform: nil), [cg(black, 0), cg(black, 0.35)], pt(0, 0.7), pt(0, 1))
        }
    }
}
