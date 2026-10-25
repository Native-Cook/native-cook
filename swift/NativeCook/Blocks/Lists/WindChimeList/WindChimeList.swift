import SwiftUI
import UIKit

/*
 Wind Chime List — a sound-bath app's sessions as a hanging wind chime: every row is a brass or
 bamboo tube on two strings, hanging from the one above, the first from an oak beam under the
 title. Scrolling swings them: each tube is a damped pendulum driven by the list's acceleration,
 40 ms later per row and at its own pitch (shorter tubes swing faster), neighbours in opposite
 directions; when two swing far enough apart they touch and a ring of light spreads from the
 point of contact. Tapping a tube strikes it: it swings hard and its waveform plays.

 The list scrolls under its own drag and deceleration (like UIScrollView: 0.998 per ms, rubber
 band at the ends), so the loop can script the scroll and a touch takes over from the frame on
 screen. One Canvas draws every visible tube from cached pieces (a shared shadow image, each
 row's text image, gradients and one path of bars); the title, beam, bars and fades are views.
*/
struct WindChimeList: View {
    /// Flings the list down, strikes a tube, scrolls back, every `loop` seconds.
    var autoPlay = false

    static let loop = 9.0

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            WindChimeListStage(autoPlay: autoPlay, size: size, insetTop: insets.top, insetBottom: insets.bottom)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(WindChimeListArt.hex(0x0F1D20).ignoresSafeArea())
    }
}

private struct WindChimeListStage: View {
    let autoPlay: Bool
    let size: CGSize
    let insetTop: Double
    let insetBottom: Double

    private typealias A = WindChimeListArt
    private typealias M = WindChimeListMotion

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.displayScale) private var displayScale
    @State private var model = WindChimeListModel()
    @State private var art: WindChimeListArt.Pieces?
    @State private var awake = false
    /// True while the drag is alive; it resets without `onEnded` when the system cancels the touch.
    @GestureState private var touching = false

    var body: some View {
        let w = size.width
        let h = size.height
        let headerH = insetTop + 168
        let maxScroll = max(0, headerH + M.rowH * Double(M.sessions.count) + insetBottom + 72 - h)
        let auto = autoPlay && !reduced
        model.configure(w: w, headerH: headerH, maxScroll: maxScroll, auto: auto, reduced: reduced, onSleep: { awake = false })
        return ZStack(alignment: .topLeading) {
            WindChimeListGrain(w: w, h: h)
                .equatable()
                .frame(width: w, height: h)
                .allowsHitTesting(false)

            TimelineView(.animation(paused: !(auto || awake))) { timeline in
                let f = model.frame(timeline.date)
                ZStack(alignment: .topLeading) {
                    header(w: w, scroll: f.scroll)
                        .frame(width: w, height: headerH, alignment: .topLeading)
                        .offset(y: -f.scroll)

                    tubes(w: w, h: h, headerH: headerH, frame: f)
                        .frame(width: w, height: h)

                    // Both edges fade; a compact bar fades in once the title has scrolled away.
                    LinearGradient(colors: [A.rgba(10, 19, 21, 0), A.rgba(10, 19, 21, 0.92)], startPoint: .top, endPoint: .bottom)
                        .frame(width: w, height: 90 + insetBottom)
                        .offset(y: h - 90 - insetBottom)
                    topBar(w: w)
                        .opacity(M.clamp01((f.scroll - 60) / 50))
                    LinearGradient(colors: [A.rgba(15, 29, 32, 0.9), A.rgba(15, 29, 32, 0)], startPoint: .top, endPoint: .bottom)
                        .frame(width: w, height: insetTop + 12)
                }
                .frame(width: w, height: h, alignment: .topLeading)
            }
            .allowsHitTesting(false)
        }
        .frame(width: w, height: h, alignment: .topLeading)
        .clipped()
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .updating($touching) { _, state, _ in state = true }
                .onChanged { v in
                    if !awake { awake = true }
                    model.touchMoved(v)
                }
                .onEnded { v in model.touchEnded(v) }
        )
        .onChange(of: touching) { _, isTouching in
            if !isTouching { model.touchCancelled() }
        }
        .onAppear { art = A.Pieces(w: w, scale: displayScale) }
        .onChange(of: w) { _, nw in art = A.Pieces(w: nw, scale: displayScale) }
        .accessibilityElement()
        .accessibilityLabel("Sound baths, \(M.sessions.count) sessions hanging as a wind chime. Scroll to swing them, tap a tube to hear it.")
    }

    // MARK: Header and bars

    private func header(w: Double, scroll: Double) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 4) {
                Text("STILLWATER")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(3)
                    .foregroundStyle(A.hex(0xD9B56A))
                Text("Sound baths")
                    .font(.system(size: 34, weight: .bold))
                    .kerning(-0.5)
                    .foregroundStyle(A.hex(0xEEF3EE))
                Text("14 sessions · tuned to 432 Hz · tap a tube to hear it")
                    .font(.system(size: 15))
                    .foregroundStyle(A.rgba(238, 243, 238, 0.6))
                    .fixedSize(horizontal: false, vertical: true)
            }
            .frame(width: max(0, w - 48), alignment: .leading)
            .opacity(1 - M.clamp01(scroll / 90))
            .offset(x: 24, y: insetTop + 20 + Self.parallax(scroll))

            beam(w: w)
                .offset(x: 16, y: insetTop + 168 - 18)
        }
    }

    /// The title's parallax: −100…0…120 pt of scroll moves it −30…0…40 pt.
    private static func parallax(_ s: Double) -> Double {
        if s <= -100 { return -30 }
        if s <= 0 { return -30 * (s / -100) }
        if s >= 120 { return 40 }
        return 40 * s / 120
    }

    private func beam(w: Double) -> some View {
        let bw = max(0, w - 32)
        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: A.hex(0x3C2412), location: 0), .init(color: A.hex(0x8A5A31), location: 0.2),
                    .init(color: A.hex(0xB98352), location: 0.45), .init(color: A.hex(0x7A4B26), location: 0.75),
                    .init(color: A.hex(0x2E1A0B), location: 1),
                ],
                startPoint: .top, endPoint: .bottom)
            ForEach([0.12, 0.31, 0.55, 0.78], id: \.self) { x in
                Rectangle()
                    .fill(A.rgba(40, 20, 6, 0.35))
                    .frame(width: 40, height: 1)
                    .offset(x: bw * x, y: 3)
            }
        }
        .frame(width: bw, height: 18, alignment: .topLeading)
        .clipShape(RoundedRectangle(cornerRadius: 6, style: .continuous))
        .overlay(
            RoundedRectangle(cornerRadius: 6, style: .continuous)
                .strokeBorder(LinearGradient(colors: [A.rgba(255, 220, 170, 0.35), .clear], startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.2)), lineWidth: 1)
        )
        .shadow(color: .black.opacity(0.45), radius: 1.5, y: 2)
        .shadow(color: .black.opacity(0.35), radius: 12, y: 12)
    }

    private func topBar(w: Double) -> some View {
        ZStack(alignment: .top) {
            Rectangle().fill(.ultraThinMaterial).environment(\.colorScheme, .dark)
            Rectangle().fill(A.rgba(15, 29, 32, 0.55))
            Text("Sound baths")
                .font(.system(size: 17, weight: .semibold))
                .foregroundStyle(A.hex(0xEEF3EE))
                .frame(height: 48)
                .padding(.top, insetTop)
        }
        .frame(width: w, height: insetTop + 48)
        .overlay(alignment: .bottom) {
            Rectangle().fill(A.rgba(238, 243, 238, 0.15)).frame(height: 1 / displayScale)
        }
    }

    // MARK: Tubes

    private func tubes(w: Double, h: Double, headerH: Double, frame f: WindChimeListFrame) -> some View {
        let art = art
        return Canvas { c, _ in
            guard let art else { return }
            let n = M.sessions.count
            for i in 0..<n {
                let top = headerH + Double(i) * M.rowH - f.scroll
                if top > h + 40 || top < -M.rowH - 60 { continue }
                let s = M.sessions[i]
                let tubeW = w - 32 - s.shrink * 2
                let left = (w - tubeW) / 2
                let a = f.swing[i]
                let brass = s.brass
                let ink = brass ? A.hex(0x2A1A07) : A.hex(0x1D2408)
                do {
                    var r = c
                    r.translateBy(x: M.sway * a + w / 2, y: top)
                    r.rotate(by: .radians(M.visualTurn * a))
                    r.translateBy(x: -w / 2, y: 0)
                    // Strings.
                    var strings = Path()
                    strings.addRect(CGRect(x: left + tubeW * 0.24, y: -2, width: 1.2, height: M.stringH + 4))
                    strings.addRect(CGRect(x: left + tubeW * 0.76, y: -2, width: 1.2, height: M.stringH + 4))
                    r.fill(strings, with: .color(A.rgba(226, 206, 160, 0.55)))
                    // Shadow, then the tube.
                    let m = A.shadowMargin
                    r.draw(art.shadow, in: CGRect(x: left - m, y: M.stringH - m, width: tubeW + 2 * m, height: M.tubeH + 2 * m))
                    let rect = CGRect(x: left, y: M.stringH, width: tubeW, height: M.tubeH)
                    let capsule = Path(roundedRect: rect, cornerRadius: M.tubeH / 2)
                    r.fill(capsule, with: .linearGradient(brass ? A.brass : A.bamboo, startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.maxY)))
                    r.fill(capsule, with: .linearGradient(A.shading, startPoint: CGPoint(x: rect.minX, y: 0), endPoint: CGPoint(x: rect.maxX, y: 0)))
                    if !brass {
                        var k = r
                        k.clip(to: capsule)
                        for x in [0.36, 0.7] {
                            k.fill(Path(CGRect(x: left + tubeW * x, y: rect.minY, width: 3, height: M.tubeH)), with: .color(A.rgba(40, 48, 12, 0.35)))
                            k.fill(Path(CGRect(x: left + tubeW * x + 2, y: rect.minY, width: 1, height: M.tubeH)), with: .color(A.rgba(255, 255, 220, 0.2)))
                        }
                    }
                    r.stroke(Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: M.tubeH / 2 - 0.5), with: .linearGradient(
                        Gradient(colors: [A.rgba(255, 250, 225, 0.5), A.rgba(255, 250, 225, 0)]),
                        startPoint: CGPoint(x: 0, y: rect.minY), endPoint: CGPoint(x: 0, y: rect.minY + 6)), lineWidth: 1)
                    // The playing tube's glow.
                    let playTau = f.playRow == i ? f.playTau : -1
                    let env = playTau >= 0 ? M.playEnvelope(playTau) : 0
                    if env > 0.001 {
                        var g = r
                        g.opacity = 0.55 * env
                        g.fill(capsule, with: .color(A.rgba(255, 240, 190, 0.55)))
                    }
                    // Title and meta.
                    if i < art.texts.count {
                        let t = art.texts[i]
                        r.draw(t.image, in: CGRect(x: left + 24, y: rect.midY - t.size.height / 2, width: t.size.width, height: t.size.height))
                    }
                    // The waveform: static, or dancing on the playing tube.
                    var bars = Path()
                    let x0 = left + tubeW - 24 - A.waveW
                    for k in 0..<A.bars {
                        let bh = 4 + 22 * M.barHeight(s.seed, k)
                        var sy = 1.0
                        if env > 0 {
                            let kd = Double(k)
                            let dance = 0.5 + 0.5 * abs(sin(playTau * (7 + kd * 0.9) + kd) * 0.7 + sin(playTau * (13 + Double(k % 4) * 2) + kd * 2.1) * 0.3)
                            sy = 1 + env * (dance * 1.6 - 0.2)
                        }
                        let hh = bh * sy
                        bars.addRoundedRect(in: CGRect(x: x0 + Double(k) * 5, y: rect.midY - hh / 2, width: 2.5, height: hh), cornerSize: CGSize(width: 1.25, height: min(1.25, hh / 2)))
                    }
                    var b = r
                    b.opacity = 0.7
                    b.fill(bars, with: .color(ink))
                }
                // The contact ring between this tube and the next.
                let age = f.ringAge[i]
                if age >= 0 && age <= M.ringLen {
                    let u = age / M.ringLen
                    let o = 0.85 * (1 - u) * (1 - u)
                    let cx = w / 2 + f.ringSide[i] * (tubeW / 2 - 26)
                    let cy = top + M.rowH + M.stringH / 2
                    let rad = 22 * (0.5 + 1.8 * u)
                    let ring = Path(ellipseIn: CGRect(x: cx - rad, y: cy - rad, width: rad * 2, height: rad * 2))
                    c.drawLayer { l in
                        l.opacity = o
                        l.drawLayer { gl in
                            gl.addFilter(.blur(radius: 7 * (0.5 + 1.8 * u)))
                            gl.stroke(ring, with: .color(A.rgba(255, 210, 120, 0.8)), lineWidth: 4)
                        }
                        l.stroke(ring, with: .color(A.rgba(255, 226, 150, 0.95)), lineWidth: 1.5 * (0.5 + 1.8 * u))
                    }
                }
            }
        }
    }
}

/// The glow, grain and vignette behind the list, drawn once.
private struct WindChimeListGrain: View, Equatable {
    let w: Double
    let h: Double

    var body: some View {
        Canvas { c, _ in
            WindChimeListArt.drawGrain(&c, w: w, h: h)
        }
    }
}

enum WindChimeListArt {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255, blue: Double(v & 0xFF) / 255).opacity(a)
    }

    static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double) -> Color {
        Color(red: r / 255, green: g / 255, blue: b / 255).opacity(a)
    }

    private static let stops = [0, 0.16, 0.34, 0.52, 0.82, 1.0]
    static let brass = Gradient(stops: zip([0x4F3512, 0xA87A35, 0xF1D690, 0xD8AD5C, 0x8A6025, 0x3A2709] as [UInt32], stops).map { .init(color: hex($0), location: $1) })
    static let bamboo = Gradient(stops: zip([0x3F4A1E, 0x7F8F45, 0xCFD68F, 0xA7B064, 0x5E6A2C, 0x2D3513] as [UInt32], stops).map { .init(color: hex($0), location: $1) })
    static let shading = Gradient(stops: [
        .init(color: .black.opacity(0.38), location: 0), .init(color: .black.opacity(0), location: 0.07),
        .init(color: .white.opacity(0.06), location: 0.5), .init(color: .black.opacity(0), location: 0.93),
        .init(color: .black.opacity(0.38), location: 1),
    ])

    static let bars = 16
    /// Width of a waveform: 16 bars of 2.5 pt, 2.5 pt apart.
    static let waveW = Double(bars) * 2.5 + Double(bars - 1) * 2.5
    static let shadowMargin = 44.0

    static func drawGrain(_ c: inout GraphicsContext, w W: Double, h H: Double) {
        let full = Path(CGRect(x: 0, y: 0, width: W, height: H))
        c.fill(full, with: .linearGradient(
            Gradient(stops: [.init(color: hex(0x13262A), location: 0), .init(color: hex(0x0F1D20), location: 0.5), .init(color: hex(0x0A1315), location: 1)]),
            startPoint: .zero, endPoint: CGPoint(x: 0, y: H)))
        c.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: rgba(120, 190, 180, 0.14), location: 0),
                .init(color: rgba(120, 190, 180, 0.03), location: 0.5),
                .init(color: .clear, location: 1),
            ]),
            center: CGPoint(x: W * 0.5, y: H * 0.12), startRadius: 0, endRadius: W * 0.9))
        var dots = Path()
        let hash = WindChimeListMotion.hash
        for i in 0..<1500 {
            let d = Double(i)
            dots.addEllipse(in: CGRect(x: hash(d * 1.3) * W - 0.6, y: hash(d * 2.9 + 4) * H - 0.6, width: 1.2, height: 1.2))
        }
        c.fill(dots, with: .color(rgba(220, 240, 235, 0.03)))
        c.fill(full, with: .radialGradient(
            Gradient(stops: [
                .init(color: .black.opacity(0), location: 0.45),
                .init(color: .black.opacity(0.2), location: 0.78),
                .init(color: .black.opacity(0.55), location: 1),
            ]),
            center: CGPoint(x: W / 2, y: H * 0.45), startRadius: 0, endRadius: H * 0.75))
    }

    /// Pieces rendered once per width: the tubes' shared shadow and each row's text.
    struct Pieces {
        var shadow: Image
        var texts: [(image: Image, size: CGSize)]

        @MainActor
        init(w: Double, scale: Double) {
            typealias M = WindChimeListMotion
            let sw = max(1, w - 32)
            let m = shadowMargin
            let shadowView = Canvas { c, _ in
                let rect = CGRect(x: m, y: m, width: sw, height: M.tubeH)
                let capsule = Path(roundedRect: rect, cornerRadius: M.tubeH / 2)
                c.drawLayer { l in
                    l.addFilter(.blur(radius: 13))
                    l.fill(capsule.offsetBy(dx: 0, dy: 14), with: .color(.black.opacity(0.38)))
                }
                c.drawLayer { l in
                    l.addFilter(.blur(radius: 1.5))
                    l.fill(capsule.offsetBy(dx: 0, dy: 2), with: .color(.black.opacity(0.45)))
                }
            }
            .frame(width: sw + 2 * m, height: M.tubeH + 2 * m)
            // A blurred shadow loses nothing at a low resolution.
            let r = ImageRenderer(content: shadowView)
            r.scale = 1
            r.isOpaque = false
            shadow = Image(uiImage: r.uiImage ?? UIImage())

            texts = M.sessions.map { s in
                let tubeW = w - 32 - s.shrink * 2
                let tw = max(1, tubeW - 48 - 12 - waveW)
                let ink = s.brass ? hex(0x2A1A07) : hex(0x1D2408)
                let view = VStack(alignment: .leading, spacing: 2) {
                    Text(s.title)
                        .font(.system(size: 16, weight: .semibold))
                        .lineLimit(1)
                    Text("\(s.minutes) min · \(s.meta)")
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .lineLimit(1)
                        .opacity(0.72)
                }
                .foregroundStyle(ink)
                .frame(width: tw, alignment: .leading)
                .fixedSize(horizontal: false, vertical: true)
                let tr = ImageRenderer(content: view)
                tr.scale = scale
                tr.isOpaque = false
                let img = tr.uiImage ?? UIImage()
                return (Image(uiImage: img), img.size)
            }
        }
    }
}

/// What one frame draws.
struct WindChimeListFrame {
    var scroll = 0.0
    var swing: [Double]
    var ringAge: [Double]
    var ringSide: [Double]
    var playRow = -1
    var playTau = 0.0
}

/// The list's scroll, the autoPlay clock and the tubes' oscillators, stepped once a frame like
/// the RN frame callback.
@MainActor
final class WindChimeListModel {
    private typealias M = WindChimeListMotion
    private static let count = M.sessions.count
    private static let idle = 2.0
    private static let returnLen = 0.8
    /// How strongly the list's acceleration (pt/s²) kicks a tube (rad/s² per pt/s²).
    private static let gain = 0.0018
    private static let maxSwing = 0.6

    private var w = 393.0
    private var headerH = 227.0
    private var maxScroll = 0.0
    private var auto = false
    private var reduced = false
    private var onSleep: () -> Void = {}

    private var last: Date?
    private var now = 0.0
    private var clock = 0.0
    /// 0 = autoPlay (or rest), 1 = the user drives, 2 = easing back to rest.
    private var mode = 0
    private var lastTouch = 0.0
    private var returnAt = 0.0
    private var returnFrom = 0.0

    // The scroll.
    private var scroll = 0.0
    private var scrollV = 0.0
    private var dragging = false
    private var touchDown = false
    private var dragStartScroll = 0.0
    private var dragFrom = 0.0
    private var fling = false

    // The oscillators.
    private var th = [Double](repeating: 0, count: count)
    private var om = [Double](repeating: 0, count: count)
    private var touchAt = [Double](repeating: -9, count: count)
    private var was = [Bool](repeating: false, count: count)
    private var side = [Double](repeating: 1, count: count)
    private var hist: [(Double, Double)] = []
    private var lastY = 0.0
    private var vs = 0.0
    private var strikeAt = -9.0
    private var strikeRow = -1
    private var current = WindChimeListFrame(swing: [Double](repeating: 0, count: count), ringAge: [Double](repeating: -1, count: count), ringSide: [Double](repeating: 1, count: count))

    private let tick = UIImpactFeedbackGenerator(style: .light)

    func configure(w: Double, headerH: Double, maxScroll: Double, auto: Bool, reduced: Bool, onSleep: @escaping () -> Void) {
        if auto != self.auto { clock = 0 }
        self.w = w
        self.headerH = headerH
        self.maxScroll = maxScroll
        self.auto = auto
        self.reduced = reduced
        self.onSleep = onSleep
    }

    func frame(_ date: Date) -> WindChimeListFrame {
        let raw = last.map { date.timeIntervalSince($0) } ?? 1.0 / 60
        last = date
        let dt = min(max(raw, 0), 0.05)
        if dt > 0 { step(dt) }
        return current
    }

    private func step(_ dt: Double) {
        let n = now + dt
        now = n
        let c = Self.count
        var sw = [Double](repeating: 0, count: c)
        var age = [Double](repeating: -1, count: c)
        var sd = [Double](repeating: 1, count: c)
        if mode == 0 {
            guard auto else {
                current = WindChimeListFrame(scroll: scroll, swing: sw, ringAge: age, ringSide: sd)
                return
            }
            let t = (clock + dt).truncatingRemainder(dividingBy: WindChimeList.loop)
            clock = t
            scroll = M.scrollAt(t)
            for i in 0..<c { sw[i] = M.swingAt(t, i) }
            for i in 0..<(c - 1) {
                let r = M.ringAt(t, i)
                age[i] = r.0
                sd[i] = r.1
            }
            let playing = t >= M.strikeAt && t < M.strikeAt + M.playLen
            current = WindChimeListFrame(scroll: scroll, swing: sw, ringAge: age, ringSide: sd, playRow: playing ? M.strikeRow : -1, playTau: playing ? t - M.strikeAt : 0)
            return
        }

        stepScroll(dt)

        // The list's speed and acceleration, smoothed, with a short history for the per-row delay.
        let y = scroll
        let v = (y - lastY) / max(dt, 1e-3)
        lastY = y
        let nvs = vs + (v - vs) * 0.35
        let a = max(-9000, min(9000, (nvs - vs) / max(dt, 1e-3)))
        vs = nvs
        hist.append((n, a))
        while let first = hist.first, first.0 < n - Double(c) * M.rowDelay - 0.1 { hist.removeFirst() }
        let zeta = mode == 2 ? 0.7 : 0.22
        var energy = 0.0
        for i in 0..<c {
            let w0 = M.rowOmega(i)
            let z = i == strikeRow && n - strikeAt < M.playLen && mode == 1 ? 0.12 : zeta
            let kick = Self.gain * M.rowDir(i) * accelAgo(n, Double(i) * M.rowDelay) * (mode == 2 || reduced ? 0 : 1)
            let acc = -w0 * w0 * th[i] - 2 * z * w0 * om[i] + kick
            om[i] += acc * dt
            th[i] += om[i] * dt
            sw[i] = Self.maxSwing * tanh(th[i] / Self.maxSwing)
            energy = max(energy, abs(th[i]) + abs(om[i]) * 0.1)
        }
        for i in 0..<(c - 1) {
            let d = sw[i] - sw[i + 1]
            let touchingNow = abs(d) > M.touch
            if touchingNow && !was[i] {
                touchAt[i] = n
                side[i] = d > 0 ? 1 : -1
            }
            was[i] = touchingNow
            let ag = n - touchAt[i]
            age[i] = ag <= M.ringLen ? ag : -1
            sd[i] = side[i]
        }
        let sr = strikeRow
        let playing = sr >= 0 && n - strikeAt < M.playLen
        current = WindChimeListFrame(scroll: scroll, swing: sw, ringAge: age, ringSide: sd, playRow: playing ? sr : -1, playTau: playing ? n - strikeAt : 0)

        if mode == 1 && !touchDown && !fling && n - lastTouch > Self.idle {
            mode = 2
            returnAt = n
            returnFrom = scroll
            strikeRow = -1
        }
        if mode == 2 {
            let u = min(1, (n - returnAt) / Self.returnLen)
            scroll = returnFrom * (1 - M.easeInOutCubic(u))
            current.scroll = scroll
            if u >= 1 && energy < 0.01 {
                clock = 0
                mode = 0
                scroll = 0
                if !auto {
                    let sleep = onSleep
                    DispatchQueue.main.async { sleep() }
                }
            }
        }
    }

    /// The list's acceleration `delay` seconds ago, from the sampled history.
    private func accelAgo(_ n: Double, _ delay: Double) -> Double {
        let want = n - delay
        for e in hist.reversed() where e.0 <= want { return e.1 }
        return 0
    }

    /// Momentum after a drag (UIScrollView's normal deceleration) and the rubber band at the ends.
    private func stepScroll(_ dt: Double) {
        guard mode == 1, !dragging else { return }
        if scroll < 0 || scroll > maxScroll {
            // Spring back to the edge (critically damped, about 0.4 s).
            let edge = scroll < 0 ? 0 : maxScroll
            let k = 180.0
            let cdamp = 2 * sqrt(k)
            scrollV += (-k * (scroll - edge) - cdamp * scrollV) * dt
            scroll += scrollV * dt
            if abs(scroll - edge) < 0.3 && abs(scrollV) < 5 {
                scroll = edge
                scrollV = 0
            }
        } else if fling {
            scrollV *= exp(-2.0 * dt)
            scroll += scrollV * dt
        }
        let moving = abs(scrollV) > 8 || scroll < 0 || scroll > maxScroll
        if fling && !moving {
            fling = false
            scrollV = 0
        }
        if fling { lastTouch = now }
    }

    // MARK: Touch

    /// Leaves autoPlay: the oscillators start from the loop's current angles and speeds.
    private func takeOver() {
        lastTouch = now
        if mode == 1 { return }
        let t = clock
        for i in 0..<Self.count {
            if mode == 0 && auto {
                th[i] = M.swingAt(t, i)
                om[i] = (M.swingAt(t, i) - M.swingAt(max(0, t - 1.0 / 120), i)) * 120
            } else {
                th[i] = current.swing[i]
                om[i] = 0
            }
            touchAt[i] = -9
            was[i] = false
        }
        hist = []
        vs = 0
        lastY = scroll
        if mode == 0 && auto && strikeRow < 0 && t > M.strikeAt && t < M.strikeAt + M.playLen {
            strikeRow = M.strikeRow
            strikeAt = now - (t - M.strikeAt)
        }
        mode = 1
    }

    private func strike(_ i: Int) {
        takeOver()
        if !reduced { om[i] += M.rowDir(i) * 0.32 * M.rowOmega(i) }
        strikeRow = i
        strikeAt = now
        tick.impactOccurred()
    }

    private func rubber(_ s: Double) -> Double {
        if s < 0 { return -(-s) / (1 + -s / 300) }
        if s > maxScroll {
            let over = s - maxScroll
            return maxScroll + over / (1 + over / 300)
        }
        return s
    }

    func touchMoved(_ v: DragGesture.Value) {
        if !touchDown {
            touchDown = true
            dragStartScroll = scroll
            dragFrom = v.startLocation.y
            if mode == 1 {
                // A touch stops the momentum where it is.
                fling = false
                scrollV = 0
            }
        }
        if !dragging && abs(v.translation.height) > 6 {
            dragging = true
            takeOver()
            fling = false
            scrollV = 0
            dragStartScroll = scroll
            dragFrom = v.location.y
        }
        guard dragging else { return }
        scroll = rubber(dragStartScroll + (dragFrom - v.location.y))
        lastTouch = now
    }

    func touchEnded(_ v: DragGesture.Value) {
        if dragging {
            scrollV = -v.velocity.height
            if scroll < 0 || scroll > maxScroll { scrollV *= 0.3 }
            fling = true
        } else if touchDown {
            // A tap: strike the tube under the finger.
            let y = v.location.y + scroll - headerH
            let i = Int(floor(y / M.rowH))
            if i >= 0 && i < Self.count {
                let local = y - Double(i) * M.rowH
                let tubeW = w - 32 - M.sessions[i].shrink * 2
                let x = v.location.x - M.sway * current.swing[i]
                if local >= M.stringH && x >= (w - tubeW) / 2 && x <= (w + tubeW) / 2 { strike(i) }
            }
        }
        touchDown = false
        dragging = false
        lastTouch = now
    }

    func touchCancelled() {
        if dragging { fling = true }
        touchDown = false
        dragging = false
        lastTouch = now
    }
}
