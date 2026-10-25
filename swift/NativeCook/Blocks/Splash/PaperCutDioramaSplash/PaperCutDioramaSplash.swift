import SwiftUI
import UIKit

/*
 Paper Cut Diorama Splash — the launch screen of a storytelling app ("Fable"). Six layers of cut
 paper (dusk sky with a paper moon, far hills, pines, a meadow with a river, a grass edge, and a
 cream front frame with an arched window) form a diorama lit from behind the logo and wordmark cut
 out of the frame; the scene sways in parallax, the camera flies through the logo's little door
 into the home screen, and the home screen recedes while the diorama rises back from below as one
 piece. Every layer is baked once off the main thread (PaperCutDioramaSplashArt.swift); per frame
 one Canvas only draws the six images under their transforms, the lamp and the bloom. Timeline
 in PaperCutDioramaSplashMotion.swift (loop = 9 s).

 Touch: drag anywhere to tilt the diorama: each layer slides by its depth (the front frame and
 the sky in opposite directions, ±18 pt at full tilt, rubber-banded) and springs back level on
 release (0.6 s, damping ratio 0.55, carrying the finger's speed). A touch during any other phase
 brings the scene to the lit diorama first (0.7 s, ease-in-out: from home it flies back out
 through the door, during the rebuild it finishes rising). 2 s after the last touch the tilt
 levels (0.4 s) and the loop restarts from its rest pose.
 Reduced motion: the lit diorama, still; tilting still works.
 */
struct PaperCutDioramaSplash: View {
    /// Plays the launch: the scene sways, the camera flies into home, the layers rise, the logo lights.
    var autoPlay = false

    static let loop = PaperCutDioramaSplashMotion.loop
    typealias M = PaperCutDioramaSplashMotion
    typealias C = PaperCutDioramaSplashPalette
    typealias Lay = PaperCutDioramaSplashLayout
    typealias Art = PaperCutDioramaSplashArt
    static let tilt: Double = 18

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var drive = PaperCutDioramaSplashDrive()
    @State private var layers: [PaperCutDioramaSplashLayerArt] = []
    @State private var bloom: PaperCutDioramaSplashLayerArt? = nil
    @State private var bakedFor: Lay? = nil
    @State private var active = false
    @State private var settle: Task<Void, Never>? = nil

    var body: some View {
        GeometryReader { proxy in
            let ins = proxy.safeAreaInsets
            let L = Lay(W: proxy.size.width + ins.leading + ins.trailing, H: proxy.size.height + ins.top + ins.bottom,
                        top: ins.top, bottom: ins.bottom)
            let looping = autoPlay && !reduceMotion && !layers.isEmpty
            ZStack(alignment: .topLeading) {
                PaperCutDioramaSplashBacking(L: L).equatable()
                TimelineView(.animation(paused: !(looping || active))) { timeline in
                    let f = drive.frame(at: timeline.date, auto: looping, reduced: reduceMotion, riseDist: Double(L.H) + 60)
                    ZStack(alignment: .topLeading) {
                        scene(L, f)
                        PaperCutDioramaSplashHome(L: L)
                            .equatable()
                            .scaleEffect(f.pose.homeScale)
                            .opacity(f.pose.home)
                    }
                    .frame(width: L.W, height: L.H, alignment: .topLeading)
                }
                .allowsHitTesting(false)
                Color.clear
                    .frame(width: L.W, height: L.H)
                    .contentShape(Rectangle())
                    .gesture(pan)
                    .accessibilityElement()
                    .accessibilityLabel("Fable. A paper-cut diorama of a river valley at dusk. Drag to tilt it.")
            }
            .frame(width: L.W, height: L.H, alignment: .topLeading)
            .ignoresSafeArea()
            .task(id: L) { await bake(L) }
        }
        .background(C.bg.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onDisappear { settle?.cancel() }
    }

    /// Bakes the layers off the main thread; the loop starts at t = 0 on the first frame that has them.
    private func bake(_ L: Lay) async {
        guard L.W > 0, bakedFor != L else { return }
        let baked = await Task.detached(priority: .userInitiated) { Art.bake(L, scale: 2) }.value
        layers = baked.layers
        bloom = baked.bloom
        bakedFor = L
        drive.begin(at: .now)
    }

    private func scene(_ L: Lay, _ f: PaperCutDioramaSplashDrive.Frame) -> some View {
        Canvas { ctx, _ in
            guard layers.count == M.layers, let bloom, f.pose.visible > 0 else { return }
            let ex = L.ex, ey = L.ey
            func place(_ i: Int) -> GraphicsContext {
                let l = f.pose.layers[i]
                let k = (M.depth[i] - 0.66) * 2.2
                var c = ctx
                c.translateBy(x: ex + l.swayX + f.tiltX * Self.tilt * k, y: ey + l.rise + l.swayY + f.tiltY * Self.tilt * 0.5 * k)
                c.scaleBy(x: l.scale, y: l.scale)
                c.translateBy(x: -ex, y: -ey)
                return c
            }
            for i in 0..<(M.layers - 1) {
                place(i).draw(Image(uiImage: layers[i].image), in: layers[i].rect)
            }
            let light = min(1, f.pose.light)
            let frame = place(M.layers - 1)
            // The lamp behind the front frame: seen only through its cut-outs.
            if light > 0.001 {
                var lamp = frame
                lamp.opacity = light
                lamp.clip(to: Path(CGRect(x: -40, y: L.winBottom + 2, width: L.W + 80, height: L.H)))
                let gy = (ey + L.wordY) / 2
                let r = 130 + 60 * min(1, max(0, f.pose.light - 1))
                lamp.fill(Path(ellipseIn: CGRect(x: ex - r, y: gy - r, width: r * 2, height: r * 2)), with: .radialGradient(
                    Gradient(stops: [.init(color: C.rgb(0xFFF1D2), location: 0), .init(color: C.rgb(0xFFC274), location: 0.22),
                                     .init(color: C.rgb(0xF08A3E), location: 0.6), .init(color: C.rgba(232, 120, 60, 0), location: 1)]),
                    center: CGPoint(x: ex, y: gy), startRadius: 0, endRadius: r))
            }
            frame.draw(Image(uiImage: layers[M.layers - 1].image), in: layers[M.layers - 1].rect)
            if light > 0.001 {
                var b = frame
                b.opacity = light
                b.blendMode = .screen
                b.draw(Image(uiImage: bloom.image), in: bloom.rect)
            }
        }
        .frame(width: L.W, height: L.H)
        .opacity(f.pose.visible)
    }

    // MARK: Touch

    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                let now = Date.now
                if !drive.touching {
                    settle?.cancel()
                    active = true
                    drive.touch(at: now, auto: autoPlay && !reduceMotion && !layers.isEmpty)
                }
                drive.move(dx: value.translation.width, dy: value.translation.height, at: now)
            }
            .onEnded { value in
                drive.release(at: .now, velocity: value.velocity)
                settle?.cancel()
                guard !autoPlay else { return }
                settle = Task { @MainActor in
                    try? await Task.sleep(for: .seconds(2 + 0.4 + 0.8))
                    if !Task.isCancelled && !drive.touching { active = false }
                }
            }
    }
}

/// The home screen the camera flies into (static; the timeline only fades and scales it).
struct PaperCutDioramaSplashHome: View, Equatable {
    var L: PaperCutDioramaSplashLayout
    typealias C = PaperCutDioramaSplashPalette

    static let shelf: [(title: String, meta: String, color: UInt32)] = [
        ("The Moth Who Kept Time", "8 min · Chapter 2 of 5", 0x5B4F86),
        ("Seven Paper Boats", "10 min · New tonight", 0x3F6470),
        ("Grandpa's Kite", "6 min · Finished twice", 0xF0AE88),
        ("Under the Willow Bridge", "14 min · Saved for Sunday", 0x7E9C68),
    ]
    /// The tabs' icons: SF Symbols closest to the React Native SVG glyphs (moon, books, person).
    static let tabs: [(label: String, symbol: String)] = [("Tonight", "moon.fill"), ("Library", "books.vertical.fill"), ("Profile", "person.fill")]

    var body: some View {
        ZStack(alignment: .bottom) {
            VStack(alignment: .leading, spacing: 0) {
                Text("FABLE")
                    .font(.system(size: 12, weight: .bold)).tracking(2.4)
                    .foregroundStyle(C.rust)
                Text("Good evening, Mira")
                    .font(.system(size: 30, weight: .bold)).tracking(-0.6)
                    .foregroundStyle(C.ink)
                    .padding(.top, 4)
                Text("Tonight's story is ready · 12 min")
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(C.mute)
                    .padding(.top, 2)
                card
                    .padding(.top, 20)
                Text("Your shelf")
                    .font(.system(size: 20, weight: .bold))
                    .foregroundStyle(C.ink)
                    .padding(.top, 28)
                    .padding(.bottom, 8)
                ForEach(0..<Self.shelf.count, id: \.self) { k in
                    let s = Self.shelf[k]
                    HStack(spacing: 14) {
                        RoundedRectangle(cornerRadius: 14, style: .continuous)
                            .fill(C.rgb(s.color))
                            .overlay(RoundedRectangle(cornerRadius: 14, style: .continuous)
                                .strokeBorder(LinearGradient(colors: [.white.opacity(0.25), .clear], startPoint: .top, endPoint: .init(x: 0.5, y: 0.1)), lineWidth: 1))
                            .frame(width: 52, height: 52)
                        VStack(alignment: .leading, spacing: 2) {
                            Text(s.title)
                                .font(.system(size: 17, weight: .semibold))
                                .foregroundStyle(C.ink)
                            Text(s.meta)
                                .font(.system(size: 14, weight: .medium).monospacedDigit())
                                .foregroundStyle(C.mute)
                        }
                        Spacer(minLength: 0)
                    }
                    .padding(.vertical, 10)
                }
                Spacer(minLength: 0)
            }
            .padding(.horizontal, 24)
            .padding(.top, L.top + 16)
            .frame(width: L.W, height: L.H, alignment: .topLeading)

            HStack(spacing: 0) {
                ForEach(0..<Self.tabs.count, id: \.self) { k in
                    let color = k == 0 ? C.rust : C.rgba(42, 34, 48, k == 0 ? 1 : 0.45)
                    VStack(spacing: 3) {
                        Image(systemName: Self.tabs[k].symbol)
                            .font(.system(size: 17, weight: .regular))
                            .foregroundStyle(color)
                            .frame(width: 22, height: 22)
                        Text(Self.tabs[k].label)
                            .font(.system(size: 11, weight: .semibold))
                            .foregroundStyle(k == 0 ? C.rust : C.rgba(42, 34, 48, 0.5))
                    }
                    .frame(minWidth: 64)
                    .frame(maxWidth: .infinity)
                }
            }
            .padding(.top, 10)
            .padding(.bottom, L.bottom + 6)
            .frame(width: L.W)
            .background(C.rgb(0xF6EBD9))
            .overlay(alignment: .top) { Rectangle().fill(C.rgba(42, 34, 48, 0.14)).frame(height: 1 / UIScreen.main.scale) }
        }
        .frame(width: L.W, height: L.H)
        .background(C.rgb(0xFBF2E2))
        .accessibilityHidden(true)
    }

    private var card: some View {
        ZStack(alignment: .topLeading) {
            LinearGradient(stops: [.init(color: C.rgb(0x26315A), location: 0), .init(color: C.rgb(0x7A6496), location: 0.55), .init(color: C.rgb(0xF0AE88), location: 1)],
                           startPoint: .top, endPoint: .bottom)
            PaperCutDioramaSplashHills(index: 0).fill(C.rgb(0x7F74A8).opacity(0.9))
            PaperCutDioramaSplashHills(index: 1).fill(C.rgb(0x34545F))
            PaperCutDioramaSplashHills(index: 2).fill(C.rgb(0x2F5137))
            GeometryReader { g in
                Circle()
                    .fill(C.rgb(0xFCE9C6))
                    .frame(width: 34, height: 34)
                    .shadow(color: C.rgba(255, 230, 190, 0.7), radius: 9)
                    .offset(x: g.size.width - 56 - 34, y: 40)
            }
            VStack(alignment: .leading, spacing: 4) {
                Text("The Lantern Fox")
                    .font(.system(size: 24, weight: .bold)).tracking(-0.4)
                    .foregroundStyle(C.rgb(0xFFF6E8))
                Text("Chapter 3 · read by Juno Vale")
                    .font(.system(size: 14, weight: .medium))
                    .foregroundStyle(C.rgb(0xFFF6E8, 0.75))
            }
            .padding(.leading, 20)
            .padding(.top, 22)
            .padding(.trailing, 120)
            VStack {
                Spacer()
                Text("Continue")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(C.ink)
                    .padding(.horizontal, 16)
                    .frame(height: 36)
                    .background(Capsule().fill(C.rgb(0xFBF2E2)))
            }
            .padding(.leading, 20)
            .padding(.bottom, 18)
        }
        .frame(width: L.W - 48, height: 220)
        .clipShape(RoundedRectangle(cornerRadius: 24, style: .continuous))
        .background(
            RoundedRectangle(cornerRadius: 24, style: .continuous)
                .fill(C.rgb(0xFBF2E2))
                .shadow(color: C.rgba(40, 30, 50, 0.18), radius: 16, y: 16)
                .shadow(color: C.rgba(40, 30, 50, 0.18), radius: 2, y: 2)
        )
    }
}

/// The card's three paper hills (the React Native SVG paths in a 345 × 220 box, stretched).
struct PaperCutDioramaSplashHills: Shape {
    var index: Int

    func path(in rect: CGRect) -> Path {
        let sx = rect.width / 345, sy = rect.height / 220
        func p(_ x: CGFloat, _ y: CGFloat) -> CGPoint { CGPoint(x: rect.minX + x * sx, y: rect.minY + y * sy) }
        var path = Path()
        switch index {
        case 0:
            path.move(to: p(0, 150))
            path.addCurve(to: p(170, 128), control1: p(60, 120), control2: p(110, 140))
            path.addCurve(to: p(345, 120), control1: p(230, 116), control2: p(290, 136))
        case 1:
            path.move(to: p(0, 175))
            path.addCurve(to: p(190, 166), control1: p(70, 160), control2: p(120, 178))
            path.addCurve(to: p(345, 160), control1: p(250, 156), control2: p(300, 172))
        default:
            path.move(to: p(0, 200))
            path.addCurve(to: p(220, 194), control1: p(80, 188), control2: p(150, 204))
            path.addCurve(to: p(345, 192), control1: p(280, 186), control2: p(320, 198))
        }
        path.addLine(to: p(345, 220))
        path.addLine(to: p(0, 220))
        path.closeSubpath()
        return path
    }
}

/// The scene's state outside the autoPlay timeline: the finger's tilt and where the story is.
final class PaperCutDioramaSplashDrive {
    typealias M = PaperCutDioramaSplashMotion
    struct Frame { var pose: M.Pose; var tiltX: Double; var tiltY: Double }
    enum Mode { case auto, manual, returning(until: Date) }

    private(set) var touching = false
    private var mode: Mode = .auto
    private var autoStart = Date.now
    private var lastFrame: Date? = nil
    private var lastTouch = Date.distantPast
    /// The scene time a finger plays to the rest pose.
    private var sceneFrom = 0.0, sceneTo = 0.0
    private var sceneStart = Date.distantPast
    private let tiltX = PaperCutDioramaSplashTilt()
    private let tiltY = PaperCutDioramaSplashTilt()

    func begin(at date: Date) {
        autoStart = date
        lastFrame = nil
        mode = .auto
    }

    private func loopTime(_ date: Date, auto: Bool) -> Double {
        guard auto else { return 0 }
        // A stalled frame pauses the loop clock instead of skipping ahead.
        if let lf = lastFrame, date.timeIntervalSince(lf) > 0.25 {
            autoStart = autoStart.addingTimeInterval(date.timeIntervalSince(lf) - 1 / 60)
        }
        lastFrame = date
        return max(0, date.timeIntervalSince(autoStart)).truncatingRemainder(dividingBy: M.loop)
    }

    private func sceneTime(_ date: Date) -> Double {
        let u = M.clamp01(date.timeIntervalSince(sceneStart) / 0.7)
        return sceneFrom + (sceneTo - sceneFrom) * M.backEase(u)
    }

    func frame(at date: Date, auto: Bool, reduced: Bool, riseDist: Double) -> Frame {
        switch mode {
        case .auto:
            let t = reduced ? M.restScene : loopTime(date, auto: auto)
            return Frame(pose: M.pose(t, riseDist: riseDist), tiltX: 0, tiltY: 0)
        case .manual where !touching && date.timeIntervalSince(lastTouch) > 2:
            // The scene is already at rest; level the tilt and hand back to the loop at t = 0.
            let start = lastTouch.addingTimeInterval(2)
            tiltX.tween(to: 0, duration: 0.4, at: start)
            tiltY.tween(to: 0, duration: 0.4, at: start)
            mode = .returning(until: start.addingTimeInterval(0.4))
            return frame(at: date, auto: auto, reduced: reduced, riseDist: riseDist)
        case let .returning(until) where date >= until:
            mode = .auto
            autoStart = until
            lastFrame = nil
            return frame(at: date, auto: auto, reduced: reduced, riseDist: riseDist)
        default:
            return Frame(pose: M.pose(sceneTime(date), riseDist: riseDist), tiltX: tiltX.value(at: date), tiltY: tiltY.value(at: date))
        }
    }

    func touch(at date: Date, auto: Bool) {
        touching = true
        lastTouch = date
        switch mode {
        case .manual: return
        case .auto:
            sceneFrom = loopTime(date, auto: auto)
        case .returning:
            sceneFrom = sceneTime(date)
        }
        // Play the scene to the lit diorama: back out through the door from home, or on through
        // the rebuild, whichever way the rest pose is nearer in the story.
        sceneTo = sceneFrom >= M.rebuildAt ? M.loop : M.restScene
        sceneStart = date
        mode = .manual
    }

    func move(dx: CGFloat, dy: CGFloat, at date: Date) {
        lastTouch = date
        tiltX.set(M.rubber(Double(dx) / 130))
        tiltY.set(M.rubber(Double(dy) / 130))
    }

    func release(at date: Date, velocity: CGSize) {
        touching = false
        lastTouch = date
        tiltX.spring(velocity: Double(velocity.width) / 130, at: date)
        tiltY.spring(velocity: Double(velocity.height) / 130, at: date)
    }
}

/// One tilt axis: held by the finger, a spring back to level (0.6 s, damping ratio 0.55,
/// carrying the finger's speed), or a 0.4 s ease back to level.
final class PaperCutDioramaSplashTilt {
    private enum Kind { case hold, spring, tween }
    private var kind: Kind = .hold
    private var from = 0.0
    private var v0 = 0.0
    private var start = Date.distantPast

    func value(at date: Date) -> Double {
        let t = max(0, date.timeIntervalSince(start))
        switch kind {
        case .hold:
            return from
        case .spring:
            let w = 2 * Double.pi / 0.6, z = 0.55
            let wd = w * (1 - z * z).squareRoot()
            let b = (v0 + z * w * from) / wd
            return exp(-z * w * t) * (from * cos(wd * t) + b * sin(wd * t))
        case .tween:
            return from * (1 - PaperCutDioramaSplashMotion.easeInOutQuad(PaperCutDioramaSplashMotion.clamp01(t / 0.4)))
        }
    }

    func set(_ v: Double) { kind = .hold; from = v }

    func spring(velocity: Double, at date: Date) {
        from = value(at: date)
        v0 = velocity
        start = date
        kind = .spring
    }

    func tween(to _: Double, duration _: Double, at date: Date) {
        from = value(at: date)
        start = date
        kind = .tween
    }
}
