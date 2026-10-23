import SwiftUI
import UIKit

/*
 Light Table Loupe — an original Native Cook design: a glowing light table of 35 mm slides and a loupe
 that magnifies the slide under it.

 Look: a black header (status bar, "CONTACT 04 · 12 FRAMES" in 12 pt mono caps, a red "1 MARKED" at the
 right when a slide is marked). The rest is the lit table: #EEF1F4 at the middle to #CFD6DD at the
 corners, a fine deterministic grain, and a soft sheen that crosses it. Twelve slide mounts in 3 × 4
 (cardboard white #F7F6F2, 3 pt corners, 82 % as tall as wide, a window 74 % × 64 % of the mount with
 the frame number and "OCT 84" printed under it in 6.5 pt mono; 3 columns 14 pt apart, rows 20 pt
 apart) each hold a backlit transparency drawn
 as gradient art (LightTableLoupeModel.swift), its colour spilling onto the table round it. Below the
 grid, side by side and centred in the space left: a slide viewer (a dark body with a 1.4 : 1 screen that
 projects the slide under the loupe, with a faint flicker, or a dim lamp when the gate is empty; its
 caption under it, "FRAME 08 · OCT 84" and the slide's title, or "Drag the loupe over a slide"), and the
 loupe's home, a dashed ring centred on the viewer's middle.

 The loupe: a black housing (128 pt across: a 9 pt ring, a 2 pt chrome lip) over a lens of 52 pt radius
 that shows the table 2.2 × larger about its centre, with grain, a vignette, a red and a blue fringe at the rim and a
 glass highlight; its shadow grows when it is lifted. Landing on a new slide pulls focus: 6 → 0 pt of blur
 in 0.25 s. A mark is a red (#E5342C) grease-pencil circle drawn round the slide in 0.4 s, two rough turns
 3 pt wide; the marked slide shifts 3 pt and tilts 1.5°.

 autoPlay: loop = 9 s, timeline in LightTableLoupeModel.swift.
 Touch: drag the loupe (grabbed where it was touched; a drag that starts elsewhere brings it there, 64 pt
 above the finger, in 0.25 s); a light haptic at each new slide under it. Tap a slide, or the loupe, to mark or
 unmark the slide (medium haptic). 2 s after the last touch the loupe glides home, the marks are wiped and
 the loop starts again.
 Reduced motion: no loop, no sheen or breathing light, no focus pull; marks appear at once.
 */

struct LightTableLoupe: View {
    var autoPlay = false

    static let loop = LightTableLoupeModel.loop
    typealias M = LightTableLoupeModel

    @Environment(\.accessibilityReduceMotion) private var reduceMotion

    @State private var autoStart: Date? = nil
    /// The manual state.
    @State private var loupe: CGPoint? = nil
    @State private var glide: (date: Date, from: CGPoint, to: CGPoint, duration: Double)? = nil
    @State private var grab: CGSize? = nil
    @State private var liftAt: (date: Date, up: Bool) = (.distantPast, false)
    @State private var focus: (index: Int, date: Date) = (-1, .distantPast)
    @State private var marks: [Int: (date: Date, on: Bool)] = [:]
    @State private var moved = false
    /// The drag started away from the loupe.
    @State private var away = false
    @State private var active = false
    /// Resets by itself when the system cancels a drag (onEnded is not called then).
    @GestureState private var pressing = false
    @State private var lastLayout: Layout? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var light = UIImpactFeedbackGenerator(style: .light)
    @State private var medium = UIImpactFeedbackGenerator(style: .medium)

    struct Layout {
        var size: CGSize
        var insets: EdgeInsets
        var header: Double
        var mount: CGSize
        var gridX: Double, gridY: Double
        var gapX: Double, gapY: Double
        var park: CGPoint
        /// The slide viewer at the lower left: its body, its screen, and where the caption under it starts.
        var viewer: CGRect
        var screen: CGRect
        var captionY: Double

        init(size: CGSize, insets: EdgeInsets) {
            self.size = size
            self.insets = insets
            header = insets.top + 40
            let w = floor((size.width - 40 - 28) / 3)
            mount = CGSize(width: w, height: (w * 0.82).rounded())
            gapX = 14
            gapY = 20
            gridX = (size.width - 3 * w - 2 * gapX) / 2
            gridY = header + 22
            let gridBottom = gridY + 4 * mount.height + 3 * gapY
            let bottom = size.height - max(insets.bottom, 20)
            // The lower area: the viewer and its caption on the left, the loupe's home on the right,
            // centred together between the grid and the bottom.
            let parkX = size.width - 20 - 68
            let vw = parkX - 68 - 16 - 20
            let sh = ((vw - 20) / 1.4).rounded()
            let block = sh + 20 + 10 + 40
            let top = gridBottom + 24 + max(0, (bottom - gridBottom - 24 - block) / 2)
            viewer = CGRect(x: 20, y: top, width: vw, height: sh + 20)
            screen = viewer.insetBy(dx: 10, dy: 10)
            park = CGPoint(x: parkX, y: viewer.midY)
            captionY = viewer.maxY + 10
        }

        func origin(_ i: Int) -> CGPoint {
            CGPoint(x: gridX + Double(i % 3) * (mount.width + gapX), y: gridY + Double(i / 3) * (mount.height + gapY))
        }
        func center(_ i: Int) -> CGPoint {
            let o = origin(i)
            return CGPoint(x: o.x + mount.width / 2, y: o.y + mount.height / 2)
        }
        func window(_ i: Int) -> CGRect {
            let o = origin(i)
            let ww = mount.width * 0.74, wh = mount.height * 0.64
            return CGRect(x: o.x + (mount.width - ww) / 2, y: o.y + mount.height * 0.12, width: ww, height: wh)
        }
        func slide(at p: CGPoint) -> Int? {
            for i in 0..<12 {
                let o = origin(i)
                if CGRect(origin: o, size: mount).insetBy(dx: -gapX / 2, dy: -gapY / 2).contains(p) { return i }
            }
            return nil
        }
    }

    static let lens = 52.0
    static let zoom = 2.2

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing,
                              height: proxy.size.height + insets.top + insets.bottom)
            let layout = Layout(size: size, insets: insets)
            TimelineView(.animation(paused: !running)) { timeline in
                let f = frame(at: timeline.date, layout: layout)
                ZStack(alignment: .topLeading) {
                    Canvas { ctx, sz in
                        LightTableLoupePainter.table(&ctx, layout: layout, frame: f, size: sz)
                        LightTableLoupePainter.loupe(&ctx, layout: layout, frame: f, size: sz)
                    }
                    .frame(width: size.width, height: size.height)
                    .accessibilityElement()
                    .accessibilityLabel("Light table with twelve slides and a loupe")
                    header(layout, f)
                    caption(layout, f)
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
                guard grab != nil, let layout = lastLayout else { return }
                grab = nil
                if liftAt.up { liftAt = (.now, false) }
                if let gl = glide { loupe = gl.to; glide = nil }
                scheduleResume(layout)
            }
        }
        .onAppear {
            light.prepare()
            medium.prepare()
            if autoPlay && !reduceMotion { autoStart = .now }
        }
        .onDisappear { resume?.cancel() }
    }

    private var running: Bool { (autoStart != nil && !reduceMotion) || active }

    // MARK: Time

    private func frame(at date: Date, layout: Layout) -> LightTableLoupeFrame {
        if let start = autoStart, date >= start {
            let t = date.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
            var f = M.frame(t, park: layout.park, center: { layout.center($0) })
            f.marks = f.markAlpha > 0 ? [M.marked: (f.markDraw, f.markAlpha, f.markTilt)] : [:]
            f.markCount = f.markAlpha > 0 ? 1 : 0
            if reduceMotion { f.blur = 0; f.sheen = -1; f.pulse = 0 }
            return f
        }
        var f = LightTableLoupeFrame()
        var p = loupe ?? layout.park
        if let g = glide {
            let u = M.clamp(date.timeIntervalSince(g.date) / g.duration)
            p = M.arc(g.from, g.to, u, bow: g.duration > 0.5 ? -0.18 : 0)
        }
        f.loupe = p
        let lu = M.clamp(date.timeIntervalSince(liftAt.date) / 0.15)
        f.lift = liftAt.up ? lu : 1 - lu
        if focus.index >= 0 {
            f.focus = focus.index
            let since = date.timeIntervalSince(focus.date)
            f.blur = reduceMotion ? 0 : 6 * (1 - M.smoothstep(0, 0.25, since))
            f.caption = M.smoothstep(0, 0.25, since)
        }
        f.hint = focus.index >= 0 ? 0 : 1
        var count = 0.0
        for (i, m) in marks {
            let since = date.timeIntervalSince(m.date)
            if m.on {
                let draw = reduceMotion ? 1 : M.clamp(since / 0.4)
                f.marks[i] = (draw, reduceMotion ? M.clamp(since / 0.2) : 1, reduceMotion ? 0 : M.outBack(since / 0.3))
                count += 1
            } else {
                let a = 1 - M.clamp(since / 0.25)
                if a > 0 { f.marks[i] = (1, a, reduceMotion ? 0 : 1 - M.inOut(since / 0.6)) }
            }
        }
        f.counter = min(1, count)
        f.markCount = Int(count)
        f.flicker = sin(2 * .pi * 2 * date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: 1000))
        return f
    }

    // MARK: Touch

    private func takeOver(_ now: Date, layout: Layout) {
        resume?.cancel()
        active = true
        guard let start = autoStart else { return }
        let t = now.timeIntervalSince(start).truncatingRemainder(dividingBy: Self.loop)
        let f = M.frame(t, park: layout.park, center: { layout.center($0) })
        autoStart = nil
        loupe = f.loupe
        glide = nil
        marks = f.markAlpha > 0.5 ? [M.marked: (now.addingTimeInterval(-1), true)] : [:]
        focus = f.caption > 0.5 ? (f.focus, now.addingTimeInterval(-1)) : (-1, .distantPast)
        liftAt = (now.addingTimeInterval(-1), f.lift > 0.5)
    }

    private func drag(_ layout: Layout) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($pressing) { _, state, _ in state = true }
            .onChanged { value in
                lastLayout = layout
                let now = Date.now
                if grab == nil {
                    takeOver(now, layout: layout)
                    let current = frame(at: now, layout: layout).loupe
                    moved = false
                    let d = hypot(value.startLocation.x - current.x, value.startLocation.y - current.y)
                    away = d > 70
                    if away {
                        // A drag that starts away from the loupe brings it over, 64 pt above the finger.
                        grab = CGSize(width: 0, height: -64)
                    } else {
                        grab = CGSize(width: current.x - value.startLocation.x, height: current.y - value.startLocation.y)
                        liftAt = (now, true)
                    }
                }
                guard let g = grab else { return }
                let target = CGPoint(x: value.location.x + g.width, y: value.location.y + g.height)
                if !moved {
                    guard hypot(value.translation.width, value.translation.height) > 6 else { return }
                    moved = true
                    if away {
                        glide = (now, frame(at: now, layout: layout).loupe, target, 0.25)
                        liftAt = (now, true)
                    }
                }
                if let gl = glide, now.timeIntervalSince(gl.date) < gl.duration {
                    glide = (gl.date, gl.from, target, gl.duration)
                } else {
                    glide = nil
                    loupe = target
                }
                lookUnder(target, now: now, layout: layout)
            }
            .onEnded { value in
                let now = Date.now
                defer { grab = nil }
                if liftAt.up { liftAt = (now, false) }
                if !moved {
                    // A tap marks the slide under the finger, or under the loupe when the loupe was tapped.
                    let current = frame(at: now, layout: layout).loupe
                    if let i = layout.slide(at: away ? value.location : current) {
                        let on = !(marks[i]?.on ?? false)
                        marks[i] = (now, on)
                        medium.impactOccurred()
                    }
                }
                if let gl = glide {
                    // Let a trip still under way finish where the finger left the loupe.
                    loupe = gl.to
                    if now.timeIntervalSince(gl.date) >= gl.duration { glide = nil }
                }
                scheduleResume(layout)
            }
    }

    private func lookUnder(_ p: CGPoint, now: Date, layout: Layout) {
        let i = layout.slide(at: p) ?? -1
        if i != focus.index {
            focus = (i, now)
            if i >= 0 { light.impactOccurred(intensity: 0.6) }
        }
    }

    private func scheduleResume(_ layout: Layout) {
        resume?.cancel()
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            let now = Date.now
            let from = frame(at: now, layout: layout).loupe
            glide = (now, from, layout.park, 0.8)
            loupe = layout.park
            focus = (-1, now)
            for (i, m) in marks where m.on { marks[i] = (now, false) }
            try? await Task.sleep(for: .seconds(0.8))
            guard !Task.isCancelled else { return }
            glide = nil
            marks = [:]
            if autoPlay && !reduceMotion {
                // Join the loop in its final rest.
                autoStart = Date.now.addingTimeInterval(-8.2)
            } else {
                active = false
            }
        }
    }

    // MARK: Overlays

    private func header(_ layout: Layout, _ f: LightTableLoupeFrame) -> some View {
        ZStack(alignment: .bottom) {
            Rectangle().fill(Color(red: 0.06, green: 0.06, blue: 0.07))
            HStack {
                Text("CONTACT 04 · 12 FRAMES")
                    .font(.system(size: 12, weight: .semibold, design: .monospaced))
                    .tracking(0.6)
                    .foregroundStyle(.white.opacity(0.92))
                Spacer()
                HStack(spacing: 6) {
                    Circle().fill(Color(red: 0.898, green: 0.204, blue: 0.173)).frame(width: 7, height: 7)
                    Text("\(max(1, f.markCount)) MARKED")
                        .font(.system(size: 12, weight: .semibold, design: .monospaced))
                        .foregroundStyle(.white.opacity(0.92))
                }
                .opacity(f.counter)
            }
            .padding(.horizontal, 20)
            .frame(height: 40)
        }
        .frame(width: layout.size.width, height: layout.header)
        .allowsHitTesting(false)
    }

    private func caption(_ layout: Layout, _ f: LightTableLoupeFrame) -> some View {
        let ink = Color(red: 0.10, green: 0.11, blue: 0.13)
        let i = max(0, f.focus)
        return ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 3) {
                Text("VIEWER · GATE EMPTY")
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundStyle(ink.opacity(0.45))
                Text("Drag the loupe over a slide")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(ink.opacity(0.8))
                    .lineLimit(1)
                    .minimumScaleFactor(0.8)
            }
            .opacity(M.clamp(f.hint))
            VStack(alignment: .leading, spacing: 3) {
                Text(String(format: "FRAME %02d · OCT 84", i + 1))
                    .font(.system(size: 11, weight: .semibold, design: .monospaced))
                    .foregroundStyle(ink.opacity(0.45))
                Text(M.slides[i].title)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(ink)
                    .lineLimit(1)
            }
            .opacity(f.focus >= 0 ? f.caption : 0)
        }
        .frame(width: layout.viewer.width, alignment: .topLeading)
        .offset(x: layout.viewer.minX, y: layout.captionY)
        .allowsHitTesting(false)
    }
}
