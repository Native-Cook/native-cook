import SwiftUI
import UIKit
import CoreText

// Port of the redesigned React Native reference. All distances are points.
// Loop (5 s): 0–0.6 rest; 0.6–0.9 anticipation; 0.9–1.45 pop;
// 0.9–2.1 burst (0.015 s stagger); 2.1–3.4 drift; 3.4–4.4 gather;
// 4.3–4.85 gulp. The ghost bobs and tilts twice per loop, and the headline's
// letters bounce with a 0.045 s stagger. Tap starts at 0.6, plays once,
// rests for 2 s, then restarts autoplay. Reduced motion pauses the clock.
struct ConfettiGatherBadge: View {
    static let loop: Double = 5
    var headline = "All done!"
    var bodyText = "Your week is planned.\nNow go and enjoy it."
    var action = "Let’s go"
    var secondary = "Edit my plan"
    var autoPlay = false

    private static let ghostSize = 212.0
    private static let pieceCount = 22
    private static let top = Color(hex: 0x7A5BFF)
    private static let bottom = Color(hex: 0x4A2ED3)
    private static let glow = Color(hex: 0xB9A6FF)
    private static let shadow = Color(hex: 0x24107A)
    private static let soft = Color.white.opacity(0.78)

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    // The accumulated clock freezes while the app is inactive or motion is reduced.
    @State private var accumulated = 0.0
    @State private var anchor = Date()
    @State private var running = false
    @State private var loopOrigin = 0.0
    @State private var tapOrigin: Double?
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private static let registered: Void = {
        for name in ["Nunito-Black", "Nunito-ExtraBold"] {
            if let url = Bundle.main.url(forResource: "ConfettiGatherBadge-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()

    private static func font(_ name: String, size: CGFloat) -> Font {
        _ = registered
        return .custom(name, fixedSize: size)
    }

    private static func image(_ name: String) -> Image {
        guard let url = Bundle.main.url(forResource: "ConfettiGatherBadge-\(name)", withExtension: "png"),
              let image = UIImage(contentsOfFile: url.path) else { return Image(uiImage: UIImage()) }
        return Image(uiImage: image.preparingForDisplay() ?? image)
    }

    // Decode the same seven PNGs as RN once, rather than on animation frames.
    private static let ghost = image("ghost")
    private static let sprites = ["confetti-ribbon", "confetti-star", "confetti-ball", "confetti-ring", "confetti-squiggle", "confetti-cube"].map { image($0) }

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let center = CGPoint(x: size.width / 2, y: size.height * 0.385)
            ZStack(alignment: .topLeading) {
                TimelineView(.animation(paused: !running || (!autoPlay && tapOrigin == nil))) { timeline in
                    let t = time(at: timeline.date)
                    ZStack(alignment: .topLeading) {
                        Canvas { context, _ in
                            drawBackground(context, size: size, center: center, time: t)
                            drawPieces(context, time: t, center: center, front: false)
                            drawGhost(context, time: t, center: center)
                        }

                        topBar(safe: safe, size: size)

                        VStack(spacing: 8) {
                            HStack(spacing: 0) {
                                ForEach(Array(headline.enumerated()), id: \.offset) { k, ch in
                                    let b = max(0, Self.bounce(Self.seg(t, 0.95 + Double(k) * 0.045, 1.75 + Double(k) * 0.045)))
                                    Text(String(ch))
                                        .font(Self.font("Nunito-Black", size: 62))
                                        .kerning(-1)
                                        .foregroundStyle(.white)
                                        .offset(y: (70 - Self.lineHeight("Nunito-Black", size: 62)) / 2)
                                        .frame(height: 70)
                                        .scaleEffect(1 + 0.2 * b)
                                        .offset(y: -12 * b)
                                }
                            }
                            .accessibilityElement(children: .ignore)
                            .accessibilityLabel(headline)
                            Text(bodyText)
                                .font(Self.font("Nunito-ExtraBold", size: 17))
                                .foregroundStyle(Self.soft)
                                .multilineTextAlignment(.center)
                                .lineSpacing(23 - Self.lineHeight("Nunito-ExtraBold", size: 17))
                                .fixedSize(horizontal: false, vertical: true)
                                .frame(height: 46)
                        }
                        .frame(width: size.width - 40)
                        .offset(x: 20, y: center.y + 136)
                        .allowsHitTesting(false)

                        Canvas { context, _ in
                            drawPieces(context, time: t, center: center, front: true)
                        }
                        .allowsHitTesting(false)
                    }
                    .frame(width: size.width, height: size.height, alignment: .topLeading)
                }
                .contentShape(Rectangle())
                .onTapGesture {
                    guard !reduced else { return }
                    tapOrigin = elapsed(at: .now) - 0.6
                    haptic.impactOccurred()
                }

                footer(safe: safe, size: size)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            updateClock()
            haptic.prepare()
        }
        .onDisappear { updateClock(active: false) }
        .onChange(of: scenePhase) { _, _ in updateClock() }
        .onChange(of: reduced) { _, _ in updateClock() }
        .task(id: completionDeadline) {
            guard let deadline = completionDeadline else { return }
            try? await Task.sleep(for: .seconds(max(0, deadline - elapsed(at: .now))))
            guard !Task.isCancelled, running else { return }
            loopOrigin = elapsed(at: .now)
            tapOrigin = nil
        }
    }

    private static func lineHeight(_ name: String, size: CGFloat) -> CGFloat {
        _ = registered
        return (UIFont(name: name, size: size) ?? .systemFont(ofSize: size)).lineHeight
    }

    private func topBar(safe: EdgeInsets, size: CGSize) -> some View {
        HStack(spacing: 0) {
            Text("tidy")
                .font(Self.font("Nunito-Black", size: 28))
                .kerning(-0.5)
                .foregroundStyle(.white)
                .offset(y: (34 - Self.lineHeight("Nunito-Black", size: 28)) / 2)
                .frame(height: 34)
            Spacer(minLength: 0)
            HStack(spacing: 5) {
                ForEach(0..<4) { _ in
                    Capsule().fill(.white).frame(width: 22, height: 5)
                }
            }
        }
        .frame(width: size.width - 48, height: 34)
        .offset(x: 24, y: safe.top + 10)
        .allowsHitTesting(false)
    }

    private func footer(safe: EdgeInsets, size: CGSize) -> some View {
        VStack(spacing: 0) {
            Text(action)
                .font(Self.font("Nunito-Black", size: 19))
                .foregroundStyle(Self.bottom)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(.white, in: Capsule())
                .accessibilityAddTraits(.isButton)
            Text(secondary)
                .font(Self.font("Nunito-ExtraBold", size: 15))
                .foregroundStyle(Self.soft)
                .frame(height: 20)
                .padding(.top, 14)
                .padding(.bottom, 2)
        }
        .frame(width: size.width - 48)
        .padding(.bottom, safe.bottom + 12)
        // RN's footer sits outside the tap detector and has no action of its own.
        .contentShape(Rectangle())
        .onTapGesture { }
        .offset(x: 24, y: size.height - safe.bottom - 106)
    }

    private var completionDeadline: Double? {
        running ? tapOrigin.map { $0 + Self.loop + 2 } : nil
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(anchor)) : 0)
    }

    private func updateClock(active: Bool = true) {
        let now = Date()
        accumulated = elapsed(at: now)
        anchor = now
        running = active && scenePhase == .active && !reduced
    }

    private func time(at date: Date) -> Double {
        let clock = elapsed(at: date)
        if let tapOrigin {
            let local = clock - tapOrigin
            return local < Self.loop ? local : 0
        }
        return autoPlay ? max(0, clock - loopOrigin).truncatingRemainder(dividingBy: Self.loop) : 0
    }

    private static func seg(_ t: Double, _ a: Double, _ b: Double) -> Double { min(1, max(0, (t - a) / (b - a))) }
    private static func out(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    private static func smooth(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    private static func bounce(_ x: Double) -> Double { x <= 0 || x >= 1 ? 0 : sin(x * .pi * 2.5) * exp(-4 * x) }

    private struct Ghost {
        let sx, sy, bob, tilt, glow: Double
    }

    private static func ghostAt(_ t: Double) -> Ghost {
        let squash = pow(seg(t, 0.6, 0.9), 3) * (1 - seg(t, 0.9, 0.96))
        let pop = bounce(seg(t, 0.9, 1.45)) * 0.42
        let gulp = bounce(seg(t, 4.3, 4.85)) * 0.3
        let phase = t / loop * .pi * 4
        return Ghost(sx: 1 + 0.12 * squash - pop * 0.35 + gulp,
                     sy: 1 - 0.14 * squash + pop + gulp,
                     bob: sin(phase) * 6, tilt: sin(phase + 0.8) * 3,
                     glow: 0.5 + 0.35 * sin(.pi * seg(t, 0.9, 2.1)))
    }

    private struct Piece {
        let sprite: Int
        let angle, radius, size, spin, turn: Double
        let front: Bool
    }

    private static let pieces: [Piece] = (0..<pieceCount).map { i in
        func h(_ n: Double) -> Double {
            let x = sin(Double(i + 1) * 12.9898 + n * 78.233) * 43758.5453
            return x - floor(x)
        }
        let angle = Double(i) / Double(pieceCount) * .pi * 2 + (h(1) - 0.5) * 0.35
        let front = i % 2 == 0 && sin(angle) < -0.15
        return Piece(sprite: (i * 5) % 6, angle: angle, radius: 120 + 130 * h(2),
                     size: (46 + 34 * h(3)) * (front ? 1.12 : 0.9),
                     spin: (h(4) > 0.5 ? 1 : -1) * (40 + 80 * h(5)), turn: h(6) * 360, front: front)
    }

    private func drawBackground(_ context: GraphicsContext, size: CGSize, center: CGPoint, time t: Double) {
        context.fill(Path(CGRect(origin: .zero, size: size)),
                     with: .linearGradient(Gradient(colors: [Self.top, Self.bottom]), startPoint: .zero,
                                           endPoint: CGPoint(x: 0, y: size.height)))
        var glow = context
        glow.opacity = Self.ghostAt(t).glow
        glow.fill(Path(ellipseIn: CGRect(x: center.x - 230, y: center.y - 230, width: 460, height: 460)),
                  with: .radialGradient(Gradient(colors: [Self.glow, Self.glow.opacity(0)]),
                                        center: center, startRadius: 0, endRadius: 230))
    }

    private func drawPieces(_ context: GraphicsContext, time t: Double, center: CGPoint, front: Bool) {
        for (i, piece) in Self.pieces.enumerated() where piece.front == front {
            let out = Self.out(Self.seg(t, 0.9 + Double(i) * 0.015, 2.1 + Double(i) * 0.015))
            let back = pow(Self.seg(t, 3.4 + Double(Self.pieceCount - 1 - i) * 0.01, 4.4 - Double(i) * 0.003), 3)
            let spread = out * (1 - back)
            if spread <= 0.001 { continue }
            let swirl = -1.1 * (1 - out) * (piece.spin > 0 ? 1.0 : -1.0)
                + 0.35 * Self.smooth(Self.seg(t, 2.1, 3.4)) + 1.5 * back
            let a = piece.angle + swirl
            let r = piece.radius * spread
            let size = piece.size * (0.15 + 0.85 * min(1, spread * 1.6))
            var c = context
            c.opacity = min(1, spread * 5)
            c.translateBy(x: center.x + cos(a) * r, y: center.y + sin(a) * r * 1.25)
            c.rotate(by: .degrees(piece.turn + piece.spin * (t - 0.9) * spread))
            c.draw(Self.sprites[piece.sprite], in: CGRect(x: -size / 2, y: -size / 2, width: size, height: size))
        }
    }

    private func drawGhost(_ context: GraphicsContext, time t: Double, center: CGPoint) {
        let g = Self.ghostAt(t)
        let sw = 118 * g.sx * (1 - g.bob / 60)
        var shadow = context
        shadow.opacity = 0.42
        shadow.addFilter(.blur(radius: 9))
        shadow.fill(Path(ellipseIn: CGRect(x: center.x - sw / 2, y: center.y + Self.ghostSize * 0.56, width: sw, height: 20)),
                    with: .color(Self.shadow))
        var c = context
        c.translateBy(x: center.x, y: center.y + g.bob + Self.ghostSize * 0.42)
        c.rotate(by: .degrees(g.tilt))
        c.scaleBy(x: g.sx, y: g.sy)
        c.translateBy(x: 0, y: -Self.ghostSize * 0.42)
        c.draw(Self.ghost, in: CGRect(x: -Self.ghostSize / 2, y: -Self.ghostSize / 2, width: Self.ghostSize, height: Self.ghostSize))
    }
}
