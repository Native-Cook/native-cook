import SwiftUI
import UIKit

/// A faithful port of the React Native jelly: 48 quadratic points, a glossy radial fill,
/// delayed googly pupils and three flavours. All automatic motion repeats every eight seconds.
/// Pokes at 1.6, 4.3 and 7.0 s squash for 1.6 s, blink for 0.18 s and change colour over 0.3 s.
/// A drag takes over the target; a tap pokes. After 2 s, control blends back over 0.6 s.
struct JellyGooglyMascot: View {
    static let loop: Double = 8
    var title = "Poke the\njelly"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion = JellyGooglyMascotMotion()
    @State private var manualFrames = false
    @State private var touching = false
    @State private var touchBegan = Date()
    @State private var tapCandidate = false
    @State private var touchGeneration = 0

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            TimelineView(.animation(paused: scenePhase != .active || reducedMotion || (!autoPlay && !manualFrames))) { timeline in
                let frame = motion.frame(at: timeline.date, autoPlay: autoPlay, reduced: reducedMotion)
                let palette = JellyGooglyMascotPalette(flavour: frame.poke.flavour)
                ZStack(alignment: .topLeading) {
                    palette.room
                    header(safe: safe, width: size.width, ink: palette.ink)
                    Canvas { context, _ in
                        JellyGooglyMascotDrawing.draw(context, size: size, frame: frame, palette: palette)
                        if autoPlay && !frame.manual {
                            let rect = CGRect(x: size.width / 2 + frame.target.x - 16,
                                              y: size.height / 2 + frame.target.y - 16,
                                              width: 32, height: 32)
                            let circle = Path(ellipseIn: rect)
                            context.fill(circle, with: .color(.white.opacity(0.55)))
                            context.stroke(circle, with: .color(.white.opacity(0.9)), lineWidth: 2)
                        }
                    }
                    .accessibilityLabel("A jelly blob with googly eyes. Drag to be watched, tap to poke.")
                    footer(safe: safe, size: size, flavour: frame.poke.flavour, ink: palette.ink)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(size: size))
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { motion.reset(at: .now) }
        .onChange(of: scenePhase) { _, phase in motion.setActive(phase == .active, at: .now) }
        .onChange(of: reducedMotion) { _, reduced in
            if reduced {
                touching = false
                manualFrames = false
                motion.reset(at: .now)
            }
        }
        .task(id: touchGeneration) {
            guard touchGeneration > 0 else { return }
            try? await Task.sleep(for: .seconds(2.65))
            guard !Task.isCancelled, !touching else { return }
            _ = motion.frame(at: .now, autoPlay: autoPlay, reduced: reducedMotion)
            manualFrames = false
        }
    }

    private func header(safe: EdgeInsets, width: CGFloat, ink: Color) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("blobby")
                    .font(.system(size: 20, weight: .black))
                    .tracking(-0.5)
                Spacer()
                Text("Playground")
                    .font(.system(size: 14, weight: .semibold))
                    .opacity(0.6)
            }
            .frame(height: 34)
            VStack(alignment: .leading, spacing: 0) {
                ForEach(Array(title.components(separatedBy: "\n").enumerated()), id: \.offset) { _, line in
                    Text(line)
                        .font(.system(size: 46, weight: .black))
                        .tracking(-1.8)
                        .fixedSize()
                        .frame(height: 48)
                        // Match RN's explicit 48 pt line height rather than SwiftUI's leading.
                        .offset(y: -3)
                }
            }
            .padding(.top, 14)
        }
        .foregroundStyle(ink)
        .frame(width: width - 48, alignment: .topLeading)
        .offset(x: 24, y: safe.top + 8)
        .allowsHitTesting(false)
    }

    private func footer(safe: EdgeInsets, size: CGSize, flavour: Double, ink: Color) -> some View {
        VStack(spacing: 6) {
            ZStack(alignment: .top) {
                ForEach(0..<3) { index in
                    let distance = abs(flavour - Double(index))
                    let wrapped = min(distance, 3 - distance)
                    Text(JellyGooglyMascotPalette.names[index])
                        .font(.system(size: 30, weight: .black))
                        .tracking(-0.8)
                        .opacity(1 - JellyGooglyMascotMotion.clamp(wrapped / 0.5))
                }
            }
            .frame(maxWidth: .infinity)
            .frame(height: 40, alignment: .top)
            Text("Drag to be watched · tap to poke")
                .font(.system(size: 15, weight: .semibold))
                .opacity(0.6)
                .frame(height: 18)
        }
        .foregroundStyle(ink)
        .frame(width: size.width - 48)
        .position(x: size.width / 2, y: size.height - safe.bottom - 24 - 32)
        .allowsHitTesting(false)
    }

    private func drag(size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reducedMotion else { return }
                let now = Date()
                if !touching {
                    motion.takeOver(at: now, autoPlay: autoPlay)
                    touching = true
                    manualFrames = true
                    touchBegan = now
                    tapCandidate = true
                }
                if hypot(value.translation.width, value.translation.height) > 10 { tapCandidate = false }
                motion.move(to: CGPoint(x: value.location.x - size.width / 2,
                                       y: value.location.y - size.height / 2), at: now)
                touchGeneration += 1
            }
            .onEnded { value in
                guard !reducedMotion else { return }
                let now = Date()
                motion.move(to: CGPoint(x: value.location.x - size.width / 2,
                                       y: value.location.y - size.height / 2), at: now)
                motion.release(at: now)
                touching = false
                // The simultaneous RN tap recognizer accepts a short, stationary pan.
                if tapCandidate, now.timeIntervalSince(touchBegan) <= 0.5,
                   hypot(value.translation.width, value.translation.height) <= 10 {
                    motion.tap(at: now)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
                touchGeneration += 1
            }
    }
}

private struct JellyGooglyMascotPoke {
    var squash = 0.0
    var blink = 0.0
    var flavour = 0.0
}

private struct JellyGooglyMascotFrame {
    var time = 0.0
    var target = CGPoint(x: 0, y: -40)
    var pupils = CGPoint(x: 0, y: -40)
    var poke = JellyGooglyMascotPoke()
    var manual = false
}

/// Automatic frames are pure functions of loop time. Only the finger's low-pass pupil filter
/// carries state between frames, matching the reference's 1-exp(-14*dt) response.
private final class JellyGooglyMascotMotion {
    private var epoch = Date()
    private var pausedAt: Date?
    private var origin = 0.0
    private var lastFrame = 0.0
    private var lastTouch = -10.0
    private var userPoke = -10.0
    private var userFlavour = 0.0
    private var finger = CGPoint.zero
    private var touching = false
    private var manual = false
    private var current = JellyGooglyMascotFrame()
    private static let pokes = [1.6, 4.3, 7.0]

    static func clamp(_ value: Double) -> Double { min(1, max(0, value)) }
    static func smooth(_ value: Double) -> Double {
        let u = clamp(value)
        return u * u * (3 - 2 * u)
    }
    static func wrapped(_ value: Double) -> Double {
        (value.truncatingRemainder(dividingBy: JellyGooglyMascot.loop) + JellyGooglyMascot.loop)
            .truncatingRemainder(dividingBy: JellyGooglyMascot.loop)
    }
    static func ghost(_ time: Double) -> CGPoint {
        let a = time / JellyGooglyMascot.loop * .pi * 2
        return CGPoint(x: 150 * sin(a) + 30 * sin(3 * a), y: -40 + 120 * sin(2 * a) + 40 * cos(a))
    }
    static func poke(_ time: Double) -> JellyGooglyMascotPoke {
        var result = JellyGooglyMascotPoke()
        for start in pokes {
            let x = time - start
            if x > 0 && x < 1.6 { result.squash += 0.16 * exp(-4 * x) * sin(13 * x) }
            if x > 0 && x < 0.18 { result.blink = max(result.blink, sin(.pi * x / 0.18)) }
            result.flavour += smooth(x / 0.3)
        }
        let wrap = time + JellyGooglyMascot.loop - pokes[2]
        if wrap > 0 && wrap < 1.6 { result.squash += 0.16 * exp(-4 * wrap) * sin(13 * wrap) }
        result.flavour = result.flavour.truncatingRemainder(dividingBy: 3)
        return result
    }
    func reset(at date: Date) {
        epoch = date
        pausedAt = nil
        origin = 0
        lastFrame = 0
        lastTouch = -10
        userPoke = -10
        userFlavour = 0
        finger = .zero
        touching = false
        manual = false
        current = JellyGooglyMascotFrame()
    }
    func setActive(_ active: Bool, at date: Date) {
        if active, let pause = pausedAt {
            epoch = epoch.addingTimeInterval(date.timeIntervalSince(pause))
            pausedAt = nil
        } else if !active && pausedAt == nil { pausedAt = date }
    }
    private func elapsed(_ date: Date) -> Double { max(0, (pausedAt ?? date).timeIntervalSince(epoch)) }

    func frame(at date: Date, autoPlay: Bool, reduced: Bool) -> JellyGooglyMascotFrame {
        if reduced { return JellyGooglyMascotFrame() }
        let now = elapsed(date)
        let dt = max(0, now - lastFrame)
        lastFrame = now
        let t = autoPlay ? Self.wrapped(now - origin) : 0
        let ghost = autoPlay ? Self.ghost(t) : CGPoint(x: 0, y: -40)
        let pupils = autoPlay ? Self.ghost(Self.wrapped(t - 0.12)) : ghost
        let autoPoke = Self.poke(t)
        current.time = t
        if manual {
            let back = touching ? 0 : Self.smooth((now - lastTouch - 2) / 0.6)
            let k = 1 - exp(-14 * dt)
            let px = current.pupils.x + (finger.x - current.pupils.x) * k
            let py = current.pupils.y + (finger.y - current.pupils.y) * k
            current.target = CGPoint(x: finger.x + (ghost.x - finger.x) * back,
                                     y: finger.y + (ghost.y - finger.y) * back)
            current.pupils = CGPoint(x: px + (pupils.x - px) * back, y: py + (pupils.y - py) * back)
            let x = now - userPoke
            let squash = x > 0 && x < 1.6 ? 0.16 * exp(-4 * x) * sin(13 * x) : 0
            let blink = x > 0 && x < 0.18 ? sin(.pi * x / 0.18) : 0
            let flavour = (userFlavour + Self.smooth(x / 0.3)).truncatingRemainder(dividingBy: 3)
            current.poke = JellyGooglyMascotPoke(squash: squash, blink: blink, flavour: back >= 1 ? autoPoke.flavour : flavour)
            if back >= 1 {
                manual = false
                let shown = Int(flavour.rounded()) % 3
                origin = now - [0.0, 2.0, 4.7][shown]
            }
        } else {
            current.target = ghost
            current.pupils = pupils
            current.poke = autoPlay ? autoPoke : JellyGooglyMascotPoke()
        }
        current.manual = manual
        return current
    }
    func takeOver(at date: Date, autoPlay: Bool) {
        _ = frame(at: date, autoPlay: autoPlay, reduced: false)
        if !manual {
            manual = true
            userFlavour = Double(Int(current.poke.flavour.rounded()) % 3)
            userPoke = -10
        }
        touching = true
        lastTouch = elapsed(date)
    }
    func move(to point: CGPoint, at date: Date) {
        finger = point
        lastTouch = elapsed(date)
    }
    func release(at date: Date) {
        touching = false
        lastTouch = elapsed(date)
    }
    func tap(at date: Date) {
        let now = elapsed(date)
        let x = now - userPoke
        userFlavour = (userFlavour + (x > 0 && x < 10 ? 1 : 0)).truncatingRemainder(dividingBy: 3)
        userPoke = now
        lastTouch = now
    }
}

private struct JellyGooglyMascotPalette {
    static let names = ["Lemon", "Blueberry", "Grape"]
    private static let lights = [0xFFE45C, 0x8FB0FF, 0xF2B8FF]
    private static let deeps = [0xF2A100, 0x3553E6, 0xA855E8]
    private static let rooms = [0xFFF3D6, 0xC3CDFF, 0xA98BF5]
    private static let inks = [0x4A3500, 0x14205E, 0x2B0F4F]
    var light: Color
    var deep: Color
    var room: Color
    var ink: Color

    init(flavour: Double) {
        let base = Int(floor(flavour)) % 3
        let next = (base + 1) % 3
        let u = flavour - floor(flavour)
        light = Self.mix(Self.lights[base], Self.lights[next], u)
        deep = Self.mix(Self.deeps[base], Self.deeps[next], u)
        room = Self.mix(Self.rooms[base], Self.rooms[next], u)
        ink = Self.mix(Self.inks[base], Self.inks[next], u)
    }
    static func color(_ hex: Int, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: opacity)
    }
    private static func mix(_ a: Int, _ b: Int, _ u: Double) -> Color {
        func channel(_ shift: Int) -> Double {
            (Double((a >> shift) & 255) * (1 - u) + Double((b >> shift) & 255) * u).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0), opacity: 1)
    }
}

private enum JellyGooglyMascotDrawing {
    static let radius = 128.0

    static func draw(_ context: GraphicsContext, size: CGSize, frame: JellyGooglyMascotFrame, palette: JellyGooglyMascotPalette) {
        let r = radius
        let cx = size.width / 2
        let cy = size.height / 2
        let tx = frame.target.x
        let ty = frame.target.y
        let squash = frame.poke.squash
        let blink = frame.poke.blink
        let distance = hypot(tx, ty)
        let lean = min(18, distance * 0.09)
        let ux = distance > 0.001 ? tx / distance : 0
        let uy = distance > 0.001 ? ty / distance : 0
        let ox = cx + ux * lean
        let oy = cy + uy * lean
        let stretch = min(0.08, distance / 2400)
        let w = .pi * 2 / JellyGooglyMascot.loop

        var shadow = context
        shadow.addFilter(.blur(radius: 16))
        shadow.fill(ellipse(cx - r * (0.85 + squash), cy + r * 0.92, r * 2 * (0.85 + squash), 34),
                    with: .color(JellyGooglyMascotPalette.color(0x28143C, opacity: 0.22)))

        let points: [CGPoint] = (0..<48).map { index in
            let theta = Double(index) / 48 * .pi * 2
            let wave = 1 + 0.045 * sin(3 * theta + 2 * w * frame.time)
                + 0.03 * sin(5 * theta - 3 * w * frame.time)
                + 0.02 * sin(2 * theta + w * frame.time)
            var x = cos(theta) * r * wave
            var y = sin(theta) * r * wave
            let along = x * ux + y * uy
            x += ux * along * stretch
            y += uy * along * stretch
            x *= 1 + squash
            y = y * (1 - squash) + r * squash
            return CGPoint(x: ox + x, y: oy + y)
        }
        func midpoint(_ index: Int) -> CGPoint {
            let a = points[index % 48]
            let b = points[(index + 1) % 48]
            return CGPoint(x: (a.x + b.x) / 2, y: (a.y + b.y) / 2)
        }
        var body = Path()
        body.move(to: midpoint(0))
        for index in 1...48 { body.addQuadCurve(to: midpoint(index), control: points[index % 48]) }
        body.closeSubpath()
        context.fill(body, with: .radialGradient(
            Gradient(stops: [.init(color: palette.light, location: 0.1), .init(color: palette.deep, location: 1)]),
            center: CGPoint(x: ox - r * 0.35, y: oy - r * 0.4), startRadius: 0, endRadius: r * 1.7
        ))

        var inside = context
        inside.clip(to: body)
        inside.drawLayer { layer in
            layer.addFilter(.blur(radius: 14))
            layer.stroke(body, with: .color(palette.deep.opacity(0.35)), lineWidth: 26)
        }
        inside.drawLayer { layer in
            layer.addFilter(.blur(radius: 18))
            layer.fill(ellipse(ox - r * 0.5, oy + r * 0.62, r, r * 0.3), with: .color(.white.opacity(0.35)))
        }
        var highlight = context
        highlight.addFilter(.blur(radius: 9))
        let pivot = CGPoint(x: ox - r * 0.42, y: oy - r * 0.5)
        highlight.translateBy(x: pivot.x, y: pivot.y)
        highlight.rotate(by: .degrees(-28))
        highlight.translateBy(x: -pivot.x, y: -pivot.y)
        highlight.fill(ellipse(ox - r * 0.62, oy - r * 0.64, r * 0.42, r * 0.22), with: .color(.white.opacity(0.8)))

        let ex = ox + ux * lean * 0.6
        let ey = oy - r * 0.12 + uy * lean * 0.6 + r * squash * 0.8
        let black = JellyGooglyMascotPalette.color(0x0B0B0F)
        for side in [-1.0, 1.0] {
            let eyeX = ex + side * r * 0.4
            let rx = r * 0.27 * (1 + squash)
            let ry = r * 0.31 * (1 - squash) * (1 - 0.92 * blink)
            let eye = ellipse(eyeX - rx, ey - ry, rx * 2, ry * 2)
            context.fill(eye, with: .color(.white))
            let dx = frame.pupils.x - (eyeX - cx)
            let dy = frame.pupils.y - (ey - cy)
            let d = max(1, hypot(dx, dy))
            let reach = min(1, d / 120)
            let qx = eyeX + dx / d * rx * 0.42 * reach
            let qy = ey + dy / d * ry * 0.42 * reach
            let pr = r * 0.155
            var pupils = context
            pupils.clip(to: eye)
            pupils.fill(ellipse(qx - pr, qy - pr * 1.1, pr * 2, pr * 2.2), with: .color(black))
            pupils.fill(ellipse(qx - pr * 0.35 - pr * 0.22, qy - pr * 0.45 - pr * 0.22, pr * 0.44, pr * 0.44), with: .color(.white))
        }
        context.fill(ellipse(ex - 6, ey + r * 0.3 - 4 * (1 - squash), 12, 9 * (1 - squash) + 2), with: .color(black))
    }

    private static func ellipse(_ x: Double, _ y: Double, _ width: Double, _ height: Double) -> Path {
        Path(ellipseIn: CGRect(x: x, y: y, width: width, height: height))
    }
}

#Preview {
    JellyGooglyMascot(autoPlay: true)
}
