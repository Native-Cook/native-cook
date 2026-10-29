import SwiftUI
import UIKit

/// "petalfield", a focus-sound app: its scene switcher. Macro petal scenes drift slowly behind a
/// tall glass card named after the scene. The card is a thick slab: it magnifies the petals, warps
/// them with a slow liquid ripple and bends them hard along its bevelled rim with a thin colour
/// split. Each scene change grows the next scene out of the glass: first inside the card, a beat
/// at its rim, then out across the screen behind a lens-like front.
///
/// Loop (9.6 s): Clarity rests 1.8 s → Depth grows out of the glass (1.4 s) → rests → Echo → back to
/// Clarity at 9.6 s. Drag anywhere to move the card and watch the refraction shift (it springs back);
/// swipe left or right to change the scene. The loop resumes 2 s after the last touch at the shown
/// scene's rest. Reduced motion: Clarity, still; swipes cross-fade.
struct PetalGlassScenes: View {
    static let loop = PetalGlassScenesMotion.loop

    var autoPlay = false
    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var touch = PetalGlassScenesTouch()
    @State private var awake = false
    @State private var dragging = false

    private typealias M = PetalGlassScenesMotion

    struct Scene {
        let title: String
        let line: String
        let sound: String
        let accent: Color
    }

    static let scenes: [Scene] = [
        Scene(title: "Clarity", line: "Bright air for clear,\nearly focus.", sound: "Morning air · 25 min",
              accent: Color(red: 0.949, green: 0.541, blue: 0.118)),
        Scene(title: "Depth", line: "Low tones that hold\nlong, deep work.", sound: "Deep hum · 50 min",
              accent: Color(red: 0.294, green: 0.310, blue: 0.847)),
        Scene(title: "Echo", line: "Soft layers that fade as\nyou rest.", sound: "Warm echo · 15 min",
              accent: Color(red: 0.949, green: 0.647, blue: 0.518)),
    ]

    /// The three petal renders, decoded once.
    static let images: [UIImage] = ["clarity", "depth", "echo"].map { name in
        guard let path = Bundle.main.path(forResource: "PetalGlassScenes-petal-\(name)", ofType: "webp"),
              let image = UIImage(contentsOfFile: path) else { return UIImage() }
        return image.preparingForDisplay() ?? image
    }

    static let cardSize = CGSize(width: 240, height: 344)
    static let cardRadius = 28.0

    var body: some View {
        GeometryReader { outer in
            let safeTop = outer.safeAreaInsets.top
            let safeBottom = outer.safeAreaInsets.bottom
            GeometryReader { geometry in
                let k = geometry.size.width / 393
                let height = geometry.size.height / k
                TimelineView(.animation(paused: scenePhase != .active || !(running || awake))) { timeline in
                    let clock = max(0, timeline.date.timeIntervalSince(epoch))
                    screen(clock: clock, k: k, height: height, top: safeTop / k, bottom: safeBottom / k)
                }
            }
            .ignoresSafeArea()
        }
        .background(Color(red: 0.42, green: 0.66, blue: 0.86).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .task(id: awake) {
            guard awake, !running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                let clock = Date().timeIntervalSince(epoch)
                if !touch.touching, clock > touch.idleUntil {
                    awake = false
                    return
                }
            }
        }
    }

    private var running: Bool { autoPlay && !reducedMotion }

    // MARK: Screen

    @ViewBuilder
    private func screen(clock: Double, k: CGFloat, height: CGFloat, top: CGFloat, bottom: CGFloat) -> some View {
        let _ = haptics()
        let frame = touch.frame(clock, autoPlay: running, reduced: reducedMotion)
        let still = reducedMotion
        let center = CGPoint(x: 196.5, y: (height * 0.465).rounded())
        let scale = frame.fade >= 0 ? 1 : M.cardScale(frame.p)
        let card = CGRect(x: center.x + frame.offset.x - Self.cardSize.width / 2,
                          y: center.y + frame.offset.y - Self.cardSize.height / 2,
                          width: Self.cardSize.width, height: Self.cardSize.height)
        let shown = frame.p >= 0.5 ? frame.to : frame.from

        ZStack(alignment: .topLeading) {
            glass(frame: frame, k: k, height: height, center: center, scale: scale, still: still)
                .allowsHitTesting(false)

            ZStack(alignment: .topLeading) {
                Color.clear
                    .frame(width: 393, height: height)
                    .contentShape(Rectangle())
                    .gesture(drag())
                    .accessibilityElement()
                    .accessibilityLabel("Scene \(Self.scenes[shown].title): \(Self.scenes[shown].line.replacingOccurrences(of: "\n", with: " "))")
                    .accessibilityAdjustableAction { direction in
                        step(direction == .increment ? -1 : 1)
                    }

                cardText(frame: frame, card: card, scale: scale)
                chrome(frame: frame, clock: clock, height: height, top: top, bottom: bottom, card: center)
            }
            .frame(width: 393, height: height, alignment: .topLeading)
            .scaleEffect(k, anchor: .topLeading)
        }
    }

    /// The petals and the glass card, in one shader.
    private func glass(frame: M.Frame, k: CGFloat, height: CGFloat, center: CGPoint, scale: Double, still: Bool) -> some View {
        let size = CGSize(width: 393, height: height)
        let rectA = Self.drift(scene: frame.from, theta: frame.theta, offset: frame.offset, size: size, still: still)
        let rectB = Self.drift(scene: frame.to, theta: frame.theta, offset: frame.offset, size: size, still: still)
        let half = CGSize(width: Self.cardSize.width / 2 * scale, height: Self.cardSize.height / 2 * scale)
        let c = CGPoint(x: center.x + frame.offset.x, y: center.y + frame.offset.y)
        let reach = Self.reach(center: c, half: half, size: size)
        let light = (x: -0.55 + frame.offset.x / 160, y: -0.83 + frame.offset.y / 220)
        let shader = ShaderLibrary.petalGlassScenesRender(
            .float(Float(k)),
            .float2(size),
            .image(Image(uiImage: Self.images[frame.from])),
            .float4(rectA.minX, rectA.minY, rectA.width, rectA.height),
            .image(Image(uiImage: Self.images[frame.to])),
            .float4(rectB.minX, rectB.minY, rectB.width, rectB.height),
            .float4(c.x, c.y, half.width, half.height),
            .float4(Float(M.front(frame.p, reach: reach)), Float(M.bulge(frame.p)),
                    Float(still ? 9 : M.warp(frame.p)), Float(frame.fade)),
            .float4(Float(still ? 0 : frame.theta), Float(frame.offset.x * 0.012 + frame.offset.y * 0.008),
                    Float(light.x), Float(light.y)),
            .float4(Float(Self.cardRadius * scale), Float(-40 + frame.offset.x * 0.6 - frame.offset.y * 0.3), 1, 0)
        )
        return Rectangle()
            .fill(Color.black)
            .frame(width: 393 * k, height: height * k)
            .colorEffect(shader)
    }

    /// Where scene `scene`'s image lies: covering the screen ×1.10, breathing ±2.5 %, panning 14 and
    /// 10 units on a loop, and shifting against the card's drag.
    static func drift(scene: Int, theta: Double, offset: (x: Double, y: Double), size: CGSize, still: Bool) -> CGRect {
        let image = images[scene]
        let iw = max(image.size.width, 1)
        let ih = max(image.size.height, 1)
        let phase = Double(scene) * 2.1
        let breathe = still ? 0 : 0.025 * sin(theta + phase)
        let cover = max(size.width / iw, size.height / ih) * (1.10 + breathe)
        let w = iw * cover
        let h = ih * cover
        let px = still ? 0 : 14 * sin(theta + phase) - offset.x * 0.12
        let py = still ? 0 : 10 * sin(theta + phase + 1.9) - offset.y * 0.12
        return CGRect(x: size.width / 2 - w / 2 + px, y: size.height / 2 - h / 2 + py, width: w, height: h)
    }

    /// How far the front must travel from the card's rim to clear the farthest corner.
    static func reach(center: CGPoint, half: CGSize, size: CGSize) -> Double {
        let r = cardRadius
        var far = 0.0
        for corner in [CGPoint.zero, CGPoint(x: size.width, y: 0), CGPoint(x: 0, y: size.height),
                       CGPoint(x: size.width, y: size.height)] {
            let qx = abs(corner.x - center.x) - (half.width - r)
            let qy = abs(corner.y - center.y) - (half.height - r)
            let d = hypot(max(qx, 0), max(qy, 0)) + min(max(qx, qy), 0) - r
            far = max(far, d)
        }
        return far + 70
    }

    // MARK: Text

    @ViewBuilder
    private func cardText(frame: M.Frame, card: CGRect, scale: Double) -> some View {
        ZStack(alignment: .topLeading) {
            ForEach(cardTexts(frame), id: \.scene) { item in
                let s = Self.scenes[item.scene]
                Text(s.title)
                    .font(.system(size: 50, weight: .light))
                    .tracking(-0.5)
                    .foregroundStyle(.white)
                    .fixedSize()
                    .frame(width: 280, height: 60, alignment: .leading)
                    .rotationEffect(.degrees(90))
                    .offset(x: 18 - 110, y: 24 + 110 + item.title.shift)
                    .opacity(item.title.alpha)
                    .blur(radius: item.title.blur)
                Text(s.line)
                    .font(.system(size: 13, weight: .regular))
                    .lineSpacing(3)
                    .foregroundStyle(.white.opacity(0.92))
                    .frame(width: 150, alignment: .bottomLeading)
                    .frame(width: 196, height: 80, alignment: .bottomLeading)
                    .offset(x: 22, y: Self.cardSize.height - 22 - 80 + item.line.shift * 0.5)
                    .opacity(item.line.alpha)
                    .blur(radius: item.line.blur)
            }
        }
        .frame(width: Self.cardSize.width, height: Self.cardSize.height, alignment: .topLeading)
        .shadow(color: .black.opacity(0.12), radius: 6, y: 1)
        .scaleEffect(scale)
        .offset(x: card.minX, y: card.minY)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private struct TextState {
        var scene: Int
        var title: (alpha: Double, shift: Double, blur: Double)
        var line: (alpha: Double, shift: Double, blur: Double)
    }

    private func cardTexts(_ f: M.Frame) -> [TextState] {
        if f.from == f.to || f.p <= 0 { return [TextState(scene: f.from, title: (1, 0, 0), line: (1, 0, 0))] }
        if f.fade >= 0 {
            return [TextState(scene: f.from, title: (1 - f.fade, 0, 0), line: (1 - f.fade, 0, 0)),
                    TextState(scene: f.to, title: (f.fade, 0, 0), line: (f.fade, 0, 0))]
        }
        return [TextState(scene: f.from, title: M.textOut(f.p), line: M.textOut(f.p, delay: 0.04)),
                TextState(scene: f.to, title: M.textIn(f.p), line: M.textIn(f.p, delay: 0.04))]
    }

    // MARK: Chrome

    @ViewBuilder
    private func chrome(frame f: M.Frame, clock: Double, height: CGFloat, top: CGFloat, bottom: CGFloat, card: CGPoint) -> some View {
        let texts = cardTexts(f)
        let dots = card.y + Self.cardSize.height / 2 + 38
        let barBottom = height - bottom - 20
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 2) {
                Text("petalfield")
                    .font(.system(size: 17, weight: .semibold))
                    .tracking(-0.2)
                Text("Focus scenes")
                    .font(.system(size: 12, weight: .regular))
                    .opacity(0.78)
            }
            .offset(x: 24, y: top + 10)

            ZStack(alignment: .trailing) {
                ForEach(texts, id: \.scene) { item in
                    Text(String(format: "%02d / %02d", item.scene + 1, M.scenes))
                        .font(.system(size: 13, weight: .medium).monospacedDigit())
                        .opacity(item.title.alpha)
                        .offset(y: item.title.shift * 0.4)
                }
            }
            .frame(width: 120, alignment: .trailing)
            .offset(x: 393 - 24 - 120, y: top + 13)

            pageDots(f)
                .frame(width: 393, height: 6)
                .offset(y: dots - 3)

            Text("Drag the glass · swipe for a scene")
                .font(.system(size: 12, weight: .regular))
                .opacity(0.72)
                .frame(width: 393)
                .offset(y: dots + 16)

            HStack(alignment: .center) {
                VStack(alignment: .leading, spacing: 5) {
                    HStack(spacing: 7) {
                        equalizer(f.theta)
                        Text("NOW PLAYING")
                            .font(.system(size: 11, weight: .semibold))
                            .tracking(1.2)
                            .opacity(0.8)
                    }
                    ZStack(alignment: .leading) {
                        ForEach(texts, id: \.scene) { item in
                            Text(Self.scenes[item.scene].sound)
                                .font(.system(size: 17, weight: .medium))
                                .opacity(item.line.alpha)
                                .offset(y: item.line.shift * 0.4)
                                .blur(radius: item.line.blur * 0.5)
                        }
                    }
                }
                Spacer()
                ZStack {
                    Circle().fill(Color.white.opacity(0.92))
                    HStack(spacing: 5) {
                        RoundedRectangle(cornerRadius: 1.5).frame(width: 4.5, height: 17)
                        RoundedRectangle(cornerRadius: 1.5).frame(width: 4.5, height: 17)
                    }
                    .foregroundStyle(Color(red: 0.106, green: 0.133, blue: 0.2))
                }
                .frame(width: 56, height: 56)
                .shadow(color: .black.opacity(0.16), radius: 12, y: 6)
            }
            .frame(width: 393 - 48, height: 56)
            .offset(x: 24, y: barBottom - 56)
        }
        .foregroundStyle(.white)
        .shadow(color: .black.opacity(0.14), radius: 8, y: 1)
        .frame(width: 393, height: height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func pageDots(_ f: M.Frame) -> some View {
        let p = f.fade >= 0 ? f.fade : M.easeInOutCubic(M.seg(f.p, 0.3, 0.9))
        let at = Double(f.from) + Double(f.to - f.from) * (f.from == f.to ? 0 : p)
        return HStack(spacing: 8) {
            ForEach(0..<M.scenes, id: \.self) { i in
                let near = max(0, 1 - abs(Double(i) - at))
                Capsule()
                    .fill(Color.white.opacity(0.5 + 0.45 * near))
                    .frame(width: 6 + 12 * near, height: 6)
            }
        }
    }

    /// Three bars that bounce as if the sound were playing, whole cycles per loop.
    private func equalizer(_ theta: Double) -> some View {
        HStack(alignment: .bottom, spacing: 2) {
            ForEach(0..<3, id: \.self) { i in
                let k = [7.0, 11.0, 13.0][i]
                let v = 0.5 + 0.3 * sin(k * theta + Double(i) * 1.7) + 0.2 * sin((k + 4) * theta + Double(i))
                RoundedRectangle(cornerRadius: 1)
                    .frame(width: 3, height: 3 + 9 * (reducedMotion ? 0.6 : v))
            }
        }
        .frame(height: 12, alignment: .bottom)
    }

    // MARK: Touch

    private func haptics() {
        guard touch.haptic > touch.hapticTaken else { return }
        DispatchQueue.main.async {
            if touch.takeHaptic() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        }
    }

    /// One scene with the accessibility actions: dx < 0 → next.
    private func step(_ dx: Double) {
        let clock = Date().timeIntervalSince(epoch)
        touch.begin(clock, autoPlay: running, reduced: reducedMotion)
        touch.end(clock, dx: dx * 120, dy: 0, vx: dx * 1000, vy: 0, reduced: reducedMotion)
        if !awake { awake = true }
    }

    private func drag() -> some Gesture {
        DragGesture(minimumDistance: 4)
            .onChanged { value in
                let clock = Date().timeIntervalSince(epoch)
                if !dragging {
                    dragging = true
                    touch.begin(clock, autoPlay: running, reduced: reducedMotion)
                    if !awake { awake = true }
                }
                // Under reduced motion the card stays put; only the swipe counts.
                if !reducedMotion {
                    touch.move(clock, dx: Double(value.translation.width), dy: Double(value.translation.height))
                }
            }
            .onEnded { value in
                let clock = Date().timeIntervalSince(epoch)
                guard dragging else { return }
                dragging = false
                touch.end(clock, dx: Double(value.translation.width), dy: Double(value.translation.height),
                          vx: Double(value.velocity.width), vy: Double(value.velocity.height), reduced: reducedMotion)
            }
    }
}
