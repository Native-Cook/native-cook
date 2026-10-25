import SwiftUI
import UIKit

/*
 Slide Projector Gallery — a photo-story screen ("Carousel Night — Summer '87"): a carousel slide
 projector at the bottom throws the current slide onto a dark living-room wall, through a visible
 beam with drifting dust. Changing slides: the picture goes dark, the lamp stutters, the round
 tray turns one slot, the next slide drops in and the focus pulls from blurry to sharp. The wall,
 the slide and the beam are one Metal layer shader (SlideProjectorGallery.metal); the timeline is
 SlideProjectorGalleryMotion.swift (loop = 9 s).

 Per frame: the full-screen shader (uniforms only; its layer is the gate's three focus tiles,
 which change only with the slide), one Canvas with the dust (four paths), the tray's mounts
 (two paths), the hub and the lens glow, and the caption rows' offsets. The projector body is a
 static view; the blurred focus levels are baked once off the main thread.

 Touch: swipe left / right (or the chevrons) for the next / previous slide, with the same change
 as the loop; past the first or last slide nothing changes. Pinch to turn the focus ring: the
 spread from where the fingers landed defocuses either way, and a tick marks sharp focus. 2 s
 after the last touch the tray goes back to the first slide, focus sharp, and the loop restarts.
 Reduced motion: the first slide, sharp and still; a change becomes a plain dim-and-back fade.
 */
struct SlideProjectorGallery: View {
    /// Shows three slide changes and goes back to the first slide, on a loop.
    var autoPlay = false

    static let loop = SlideProjectorGalleryMotion.loop
    typealias M = SlideProjectorGalleryMotion
    typealias C = SlideProjectorGalleryPalette
    typealias Art = SlideProjectorGalleryArt
    typealias Lay = SlideProjectorGalleryLayout

    static let motes = 64
    static let bokeh = 7
    static let row: CGFloat = 46

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drive = SlideProjectorGalleryDrive()
    @State private var tiles: [[UIImage]] = []
    @State private var active = false
    @State private var settle: Task<Void, Never>? = nil
    @State private var swiping = false

    private var looping: Bool { autoPlay && !reduceMotion && !tiles.isEmpty }

    var body: some View {
        GeometryReader { proxy in
            let ins = proxy.safeAreaInsets
            let L = Lay(W: proxy.size.width + ins.leading + ins.trailing, H: proxy.size.height + ins.top + ins.bottom,
                        top: ins.top, bottom: ins.bottom)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: !(looping || active))) { timeline in
                    let f = drive.frame(at: timeline.date, auto: looping, reduced: reduceMotion)
                    moving(L, f)
                }
                .contentShape(Rectangle())
                .gesture(swipe)
                .simultaneousGesture(pinch)
                .accessibilityElement()
                .accessibilityLabel("A slide projector showing summer photos on a wall. Swipe for the next slide, pinch to focus.")

                header(L)
                controls(L)
                Text("Swipe to change slides · pinch to focus")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(C.rgba(243, 232, 218, 0.5))
                    .frame(width: L.W - 48)
                    .offset(x: 24, y: L.H - L.bottom - 12 - 16)
                    .allowsHitTesting(false)
            }
            .frame(width: L.W, height: L.H, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(C.bg.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task {
            guard tiles.isEmpty else { return }
            let baked = await Task.detached(priority: .userInitiated) { Art.bakeTiles() }.value
            tiles = baked
            drive.begin(at: .now)
        }
        .onDisappear { settle?.cancel() }
    }

    // MARK: Drawing

    private func moving(_ L: Lay, _ f: SlideProjectorGalleryDrive.Frame) -> some View {
        let p = f.pose
        let lit = p.bright * p.flicker
        return ZStack(alignment: .topLeading) {
            SlideProjectorGalleryGate(L: L, slide: p.slide, ready: !tiles.isEmpty, tiles: tiles.isEmpty ? [] : tiles[p.slide])
                .equatable()
                .layerEffect(
                    ShaderLibrary.default.SlideProjectorGalleryWall(
                        .float2(L.W, L.H),
                        .float4(L.projCX, L.projTop, L.projW, L.projH),
                        .float2(Lay.keyTop, Lay.keyBottom),
                        .float2(L.lensX, L.lensY),
                        .float4(Double(p.slide), tiles.isEmpty ? 0 : p.bright, p.drop, p.focus),
                        .float4(p.flicker, p.scale, 0, 0),
                        .float2(L.tile.width, L.tile.height)
                    ),
                    maxSampleOffset: .zero
                )

            // Dust in the beam: one path per brightness band, plus a few soft out-of-focus motes.
            Canvas { ctx, _ in
                guard !tiles.isEmpty else { return }
                var bands = [Path(), Path(), Path(), Path()]
                let half = Double(L.projW * 0.5 * Lay.keyTop)
                for i in 0..<(Self.motes + Self.bokeh) {
                    let m = M.mote(i, reduceMotion ? 0 : f.dust, lensX: Double(L.lensX), lensY: Double(L.lensY),
                                   topY: Double(L.projTop + L.projH * 0.6), halfTop: half)
                    let b = m.3 * lit
                    if b < 0.08 { continue }
                    let r = i >= Self.motes ? 2.6 + m.2 * 1.4 : m.2
                    let circle = CGRect(x: m.0 - r, y: m.1 - r, width: r * 2, height: r * 2)
                    if i >= Self.motes { bands[3].addEllipse(in: circle) } else { bands[b > 0.6 ? 2 : (b > 0.3 ? 1 : 0)].addEllipse(in: circle) }
                }
                ctx.fill(bands[0], with: .color(C.rgba(255, 236, 200, 0.35)))
                ctx.fill(bands[1], with: .color(C.rgba(255, 238, 206, 0.6)))
                ctx.fill(bands[2], with: .color(C.rgba(255, 244, 222, 0.9)))
                ctx.drawLayer { l in
                    l.addFilter(.blur(radius: 2.4))
                    l.fill(bands[3], with: .color(C.rgba(255, 236, 200, 0.14)))
                }
            }
            .frame(width: L.W, height: L.H)

            SlideProjectorGalleryBody(L: L).equatable()

            Canvas { ctx, _ in
                let m = Art.mounts(L, pos: p.tray, slots: M.traySlots)
                ctx.fill(m.far, with: .color(C.rgb(0x7E7465)))
                ctx.fill(Art.hub(L), with: .radialGradient(
                    Gradient(stops: [.init(color: C.rgb(0x5B5450), location: 0), .init(color: C.rgb(0x2E2A28), location: 0.6), .init(color: C.rgb(0x1C1918), location: 1)]),
                    center: CGPoint(x: L.trayCX - L.rx * 0.12, y: L.trayCY - L.ry * 0.2), startRadius: 0, endRadius: L.rx * 0.5))
                ctx.fill(m.near, with: .color(C.rgb(0xE8DCC5)))
                if !tiles.isEmpty && lit > 0.001 {
                    ctx.opacity = lit
                    ctx.fill(Art.oval(L.lensX, L.lensY, 28, 28), with: .radialGradient(
                        Gradient(stops: [.init(color: C.rgba(255, 240, 205, 0.85), location: 0), .init(color: C.rgba(255, 214, 150, 0.25), location: 0.35), .init(color: C.rgba(255, 200, 130, 0), location: 1)]),
                        center: CGPoint(x: L.lensX, y: L.lensY), startRadius: 0, endRadius: 28))
                }
            }
            .frame(width: L.W, height: L.H)
            .allowsHitTesting(false)

            captions(L, tray: p.tray)
        }
        .frame(width: L.W, height: L.H, alignment: .topLeading)
    }

    /// One caption per row of a clipped box: rolls up and fades as the next rolls in from below.
    private func captions(_ L: Lay, tray: Double) -> some View {
        let base = floor(tray)
        let roll = base + M.smoothstep(0.3, 0.7, tray - base)
        return ZStack(alignment: .top) {
            ForEach(0..<Art.slides.count, id: \.self) { k in
                let d = Double(k) - roll
                if abs(d) < 0.6 {
                    VStack(spacing: 0) {
                        Text(Art.slides[k].title)
                            .font(.system(size: 17, weight: .semibold)).tracking(-0.2)
                            .foregroundStyle(C.ink)
                            .lineLimit(1)
                            .frame(height: 22)
                        Text("\(k + 1) of 5 · \(Art.slides[k].meta)")
                            .font(.system(size: 12, weight: .medium).monospacedDigit())
                            .foregroundStyle(C.mute)
                            .lineLimit(1)
                            .frame(height: 16)
                    }
                    .frame(width: L.W - 48, height: Self.row)
                    .opacity(max(0, 1 - abs(d) * 1.8))
                    .offset(y: CGFloat(d) * Self.row)
                }
            }
        }
        .frame(width: L.W - 48, height: Self.row, alignment: .top)
        .clipped()
        .offset(x: 24, y: L.captionY)
        .allowsHitTesting(false)
    }

    private func header(_ L: Lay) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Text("CAROUSEL NIGHT")
                .font(.system(size: 12, weight: .bold)).tracking(2.4)
                .foregroundStyle(C.accent)
            Text("Summer '87")
                .font(.system(size: 30, weight: .bold)).tracking(-0.6)
                .foregroundStyle(C.ink)
                .padding(.top, 4)
            Text("From Aunt Ivy's tray · 5 slides · 35 mm")
                .font(.system(size: 14, weight: .medium))
                .foregroundStyle(C.mute)
                .padding(.top, 2)
        }
        .frame(width: L.W - 48, alignment: .leading)
        .offset(x: 24, y: L.top + 12)
        .allowsHitTesting(false)
        .accessibilityElement(children: .combine)
    }

    private func controls(_ L: Lay) -> some View {
        HStack {
            roundButton(dir: -1)
            Spacer()
            roundButton(dir: 1)
        }
        .frame(width: L.W - 40)
        .offset(x: 20, y: L.lensY - 34)
    }

    private func roundButton(dir: Int) -> some View {
        Button {
            press(dir)
        } label: {
            Canvas { ctx, _ in
                var p = Path()
                if dir < 0 {
                    p.move(to: CGPoint(x: 11.5, y: 3.5)); p.addLine(to: CGPoint(x: 6, y: 9)); p.addLine(to: CGPoint(x: 11.5, y: 14.5))
                } else {
                    p.move(to: CGPoint(x: 6.5, y: 3.5)); p.addLine(to: CGPoint(x: 12, y: 9)); p.addLine(to: CGPoint(x: 6.5, y: 14.5))
                }
                ctx.stroke(p, with: .color(C.ink), style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
            }
            .frame(width: 18, height: 18)
            .frame(width: 44, height: 44)
            .background(Circle().fill(C.rgba(243, 232, 218, 0.08)))
            .overlay(Circle().strokeBorder(C.rgba(243, 232, 218, 0.22), lineWidth: 1 / UIScreen.main.scale))
            .contentShape(Circle().inset(by: -8))
        }
        .buttonStyle(SlideProjectorGalleryPressStyle())
        .accessibilityLabel(dir < 0 ? "Previous slide" : "Next slide")
    }

    // MARK: Touch

    private func touched() {
        settle?.cancel()
        active = true
        guard !autoPlay else { return }
        settle = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2 + M.change + 0.5 + 0.8))
            if !Task.isCancelled && !drive.pinching { active = false }
        }
    }

    private func press(_ dir: Int) {
        drive.step(dir, at: .now, reduced: reduceMotion)
        touched()
    }

    private var swipe: some Gesture {
        DragGesture(minimumDistance: 14)
            .onEnded { e in
                let tx = e.translation.width, ty = e.translation.height
                guard abs(ty) < 24 || abs(tx) > abs(ty) * 1.5 else { return }
                if abs(tx) > 44 || abs(e.velocity.width) > 500 {
                    press(tx < 0 ? 1 : -1)
                }
            }
    }

    private var pinch: some Gesture {
        MagnifyGesture()
            .onChanged { e in
                drive.pinch(scale: e.magnification, at: .now, reduced: reduceMotion)
                touched()
            }
            .onEnded { _ in
                drive.endPinch(at: .now)
                touched()
            }
    }
}

struct SlideProjectorGalleryPressStyle: ButtonStyle {
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? 0.94 : 1)
    }
}

/// The projector's state outside the autoPlay timeline: slide changes started by the finger and
/// the focus ring. Read from the timeline's date; nothing is written per frame.
final class SlideProjectorGalleryDrive {
    typealias M = SlideProjectorGalleryMotion
    struct Frame { var pose: M.Pose; var dust: Double }
    enum Mode { case auto, manual, returning }

    private(set) var pinching = false
    private var mode: Mode = .auto
    private var autoStart = Date.now
    private var lastFrame: Date? = nil
    private var lastTouch = Date.distantPast
    private var chFrom = 0
    private var chTo = 0
    private var chStart = Date.distantPast
    private var returnEnd = Date.distantPast
    /// The focus ring turned by a pinch: −1 … 1, 0 sharp; eased back to 0 over 0.5 s.
    private var focus = 0.0
    private var focusFrom = 0.0
    private var focusStart: Date? = nil
    private var pinchBase = 0.0
    private var sharp = false
    private let tick = UISelectionFeedbackGenerator()
    private let light = UIImpactFeedbackGenerator(style: .light)
    private let warn = UINotificationFeedbackGenerator()

    func begin(at date: Date) {
        autoStart = date
        lastFrame = nil
        mode = .auto
        tick.prepare(); light.prepare()
    }

    private func userFocus(at date: Date) -> Double {
        guard let s = focusStart else { return focus }
        return focusFrom * (1 - M.easeInOutQuad(M.clamp01(date.timeIntervalSince(s) / 0.5)))
    }

    private func loopTime(_ date: Date, auto: Bool) -> Double {
        guard auto else { return 0 }
        // A stalled frame pauses the loop clock instead of skipping ahead.
        if let last = lastFrame, date.timeIntervalSince(last) > 0.25 {
            autoStart = autoStart.addingTimeInterval(date.timeIntervalSince(last) - 1 / 60)
        }
        lastFrame = date
        return max(0, date.timeIntervalSince(autoStart)).truncatingRemainder(dividingBy: M.loop)
    }

    func frame(at date: Date, auto: Bool, reduced: Bool) -> Frame {
        switch mode {
        case .auto:
            let t = loopTime(date, auto: auto)
            return Frame(pose: reduced ? M.rest(0) : M.autoPose(t), dust: t)
        case .manual:
            let ready = chStart.addingTimeInterval(M.change)
            let idle = lastTouch.addingTimeInterval(2)
            if !pinching && date > idle && date > ready {
                let start = max(idle, ready)
                focusFrom = focus
                focusStart = start
                focus = 0
                if chTo != 0 {
                    chFrom = chTo
                    chTo = 0
                    chStart = start
                }
                returnEnd = max(chStart.addingTimeInterval(M.change), start.addingTimeInterval(0.5))
                mode = .returning
                return frame(at: date, auto: auto, reduced: reduced)
            }
        case .returning:
            if date >= returnEnd {
                mode = .auto
                autoStart = returnEnd
                lastFrame = nil
                focusStart = nil
                focus = 0
                return frame(at: date, auto: auto, reduced: reduced)
            }
        }
        let u = date.timeIntervalSince(chStart) / M.change
        var p = u >= 0 && u < 1 ? M.change(u, chFrom, chTo) : M.rest(chTo)
        if reduced && u >= 0 && u < 1 {
            var r = M.rest(u < 0.5 ? chFrom : chTo)
            r.bright = abs(1 - 2 * u)
            p = r
        }
        let uf = abs(userFocus(at: date))
        if uf > p.focus {
            p.focus = uf
            p.scale = 1 + 0.03 * min(1, uf)
        }
        return Frame(pose: p, dust: date.timeIntervalSinceReferenceDate.truncatingRemainder(dividingBy: M.loop))
    }

    /// Takes over from the loop where it is (on the nearest whole slide).
    private func takeOver(at date: Date, reduced: Bool) {
        if case .manual = mode { return }
        let cur: Int
        if case .auto = mode {
            cur = Int(frame(at: date, auto: true, reduced: reduced).pose.tray.rounded())
        } else {
            cur = chTo
        }
        focus = userFocus(at: date)
        focusStart = nil
        chFrom = cur
        chTo = cur
        chStart = .distantPast
        mode = .manual
    }

    func step(_ dir: Int, at date: Date, reduced: Bool) {
        takeOver(at: date, reduced: reduced)
        lastTouch = date
        if date.timeIntervalSince(chStart) < M.change * 0.6 { return }
        let cur = chTo
        let to = max(0, min(SlideProjectorGalleryArt.slides.count - 1, cur + dir))
        if to == cur {
            warn.notificationOccurred(.warning)
            return
        }
        chFrom = cur
        chTo = to
        chStart = date
        light.impactOccurred()
    }

    func pinch(scale: CGFloat, at date: Date, reduced: Bool) {
        if !pinching {
            takeOver(at: date, reduced: reduced)
            pinching = true
            pinchBase = focus
            sharp = abs(focus) < 0.06
        }
        lastTouch = date
        focus = max(-1, min(1, pinchBase + (Double(scale) - 1) * 1.6))
        // A tick when the pinch brings the picture into focus.
        let now = abs(focus) < 0.06
        if now && !sharp { tick.selectionChanged() }
        sharp = now
    }

    func endPinch(at date: Date) {
        pinching = false
        lastTouch = date
    }
}
