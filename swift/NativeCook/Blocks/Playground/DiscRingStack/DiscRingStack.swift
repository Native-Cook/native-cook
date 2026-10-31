import SwiftUI
import UIKit

// Faithful port of the React Native Token Hall wall. The 9 s loop slides six 36 pt slots
// at 24 pt/s. Each 1.5 s pop rises over 0.4 s (back easing, k = 1.4), holds until 0.9 s
// with a damped 2 degree yaw wobble, sinks by 1.3 s (cubic), then rests until 1.5 s.
// Dragging takes over the row; tapping pops the nearest token. After 2 s without touch,
// resume the reference's sliding phase immediately after a pop, at age 1.31 s.
struct DiscRingStack: View {
    var title = "Your token wall"
    var autoPlay = false

    static let loop: Double = 9

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var clockStart = Date()
    @State private var pausedAt: Date?
    @State private var origin = 0.0
    @State private var manual = false
    @State private var touching = false
    @State private var lastTouch = -10.0
    @State private var grabbed = 0.0
    @State private var userShift = 0.0
    @State private var userPop = -10.0
    @State private var userSlot = 0
    @State private var idleWall = DiscRingStackWall.rest
    @State private var touchVersion = 0
    @State private var gestureStart: Date?
    @State private var panning = false
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + safe.leading + safe.trailing,
                height: geo.size.height + safe.top + safe.bottom
            )
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && !manual))) { timeline in
                let wall = wall(at: elapsed(at: timeline.date))
                ZStack(alignment: .topLeading) {
                    DiscRingStackDrawing.background
                    Canvas { context, canvasSize in
                        DiscRingStackDrawing.wall(context: context, wall: wall, size: canvasSize)
                    }
                    .frame(width: size.width, height: size.height)
                    .accessibilityLabel("A row of arcade tokens. Drag to slide, tap to pop one.")

                    header(safe: safe, width: size.width)
                        .allowsHitTesting(false)
                    footer(wall: wall, safe: safe, size: size)
                        .allowsHitTesting(false)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .clipped()
                .contentShape(Rectangle())
                .gesture(gesture)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            clockStart = .now
            origin = 0
            pausedAt = reduceMotion || scenePhase != .active ? .now : nil
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, _ in updateClock() }
        .onChange(of: reduceMotion) { _, _ in updateClock() }
        .task(id: touchVersion) { await resumeAfterTouch() }
    }

    private func header(safe: EdgeInsets, width: CGFloat) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            HStack {
                Text("Token Hall")
                    .font(.system(size: 18, weight: .heavy))
                    .foregroundStyle(.white)
                Spacer(minLength: 0)
                Text("55 tokens")
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(.white.opacity(0.55))
            }
            .frame(height: 34)
            Text(title)
                .font(.system(size: 36, weight: .black))
                .tracking(-1)
                .foregroundStyle(.white)
                .frame(height: 40)
                // Match the glyph baseline inside React Native's explicit 40 pt line box.
                .offset(y: -5.0 / 3)
                .padding(.top, 16)
            Text("Every game you win drops one in. Something to watch forever.")
                .font(.system(size: 16))
                .foregroundStyle(.white.opacity(0.6))
                .lineSpacing(22 - UIFont.systemFont(ofSize: 16).lineHeight)
                .fixedSize(horizontal: false, vertical: true)
                .padding(.top, 8)
                .offset(y: 5.0 / 3)
        }
        .frame(width: width - 48, alignment: .leading)
        .offset(x: 24, y: safe.top + 8)
    }

    private func footer(wall: DiscRingStackWall, safe: EdgeInsets, size: CGSize) -> some View {
        VStack(spacing: 12) {
            ZStack {
                ForEach(DiscRingStackDrawing.tokens.indices, id: \.self) { k in
                    let token = DiscRingStackDrawing.tokens[k]
                    let on = DiscRingStackMotion.index(wall.slot) == k
                        ? DiscRingStackMotion.clamp(wall.up * 1.4) : 0
                    HStack(spacing: 12) {
                        Circle().fill(DiscRingStackDrawing.color(token.face))
                            .frame(width: 22, height: 22)
                        Text(token.name)
                            .font(.system(size: 17, weight: .bold))
                            .foregroundStyle(.white)
                        Spacer(minLength: 0)
                        Text("× \(token.count)")
                            .font(.system(size: 17, weight: .heavy))
                            .foregroundStyle(.white.opacity(0.7))
                    }
                    .padding(.horizontal, 16)
                    .frame(height: 56)
                    .background(RoundedRectangle(cornerRadius: 18).fill(.white.opacity(0.08)))
                    .opacity(on)
                    .offset(y: 10 * (1 - on))
                }
            }
            .frame(height: 56)
            Text("Trade tokens")
                .font(.system(size: 17, weight: .bold))
                .foregroundStyle(DiscRingStackDrawing.background)
                .frame(maxWidth: .infinity)
                .frame(height: 58)
                .background(Capsule().fill(.white))
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: size.width - 40)
        .offset(x: 20, y: size.height - safe.bottom - 14 - 126)
    }

    private func elapsed(at date: Date) -> Double {
        max(0, (pausedAt ?? date).timeIntervalSince(clockStart))
    }

    private func updateClock() {
        if scenePhase == .active && !reduceMotion {
            if let pausedAt {
                clockStart = clockStart.addingTimeInterval(Date().timeIntervalSince(pausedAt))
                self.pausedAt = nil
            }
        } else if pausedAt == nil {
            pausedAt = .now
        }
    }

    private func wall(at now: Double) -> DiscRingStackWall {
        guard !reduceMotion else { return .rest }
        if manual {
            let pop = DiscRingStackMotion.pop(at: now - userPop)
            return DiscRingStackWall(shift: userShift, slot: userSlot, up: pop.up, wobble: pop.wobble)
        }
        guard autoPlay else { return idleWall }
        return DiscRingStackMotion.wall(at: now - origin)
    }

    private func takeOver(at now: Double) {
        if !manual {
            let current = wall(at: now)
            manual = true
            userShift = current.shift
            userSlot = current.slot
            userPop = current.up > 0.001 ? now - 0.9 : -10
        }
        lastTouch = now
    }

    // The exclusive RN pan/tap recognizers share a 6 pt threshold. A stationary touch only
    // takes over on a successful tap; a pan preserves its translation from the touch's start.
    private var gesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                guard !reduceMotion else { return }
                let date = Date()
                if gestureStart == nil { gestureStart = date }
                let now = elapsed(at: date)
                if !panning, hypot(value.translation.width, value.translation.height) >= 6 {
                    takeOver(at: now)
                    panning = true
                    touching = true
                    grabbed = userShift
                    touchVersion += 1
                }
                if panning {
                    userShift = grabbed - value.translation.width
                    lastTouch = now
                }
            }
            .onEnded { _ in
                guard !reduceMotion else { return }
                let date = Date()
                let now = elapsed(at: date)
                if panning {
                    touching = false
                    lastTouch = now
                    touchVersion += 1
                } else if let gestureStart, date.timeIntervalSince(gestureStart) <= 0.5 {
                    takeOver(at: now)
                    userSlot = Int(floor(userShift / DiscRingStackMotion.gap + 0.5))
                    userPop = now
                    haptic.impactOccurred()
                    touchVersion += 1
                }
                panning = false
                gestureStart = nil
            }
    }

    private func resumeAfterTouch() async {
        guard touchVersion > 0 else { return }
        while !Task.isCancelled, manual, !touching {
            let now = elapsed(at: .now)
            let age = now - userPop
            if pausedAt == nil, now - lastTouch > 2, age > 1.3 || age < 0 {
                // Without autoplay the reference retains its last manually drawn resting wall.
                idleWall = DiscRingStackWall(shift: userShift, slot: userSlot, up: 0, wobble: 0)
                let shift = DiscRingStackMotion.modulo(userShift, 216)
                let phase = floor((shift / 24) / 1.5) * 1.5 + 1.31
                origin = now - phase
                userShift = 0
                manual = false
                return
            }
            let remaining = max(0.01, max(lastTouch + 2 - now, userPop + 1.3 - now))
            try? await Task.sleep(for: .seconds(pausedAt == nil ? remaining : 0.25))
        }
    }
}

private struct DiscRingStackWall {
    var shift: Double
    var slot: Int
    var up: Double
    var wobble: Double

    static let rest = Self(shift: 0, slot: 0, up: 0, wobble: 0)
}

private enum DiscRingStackMotion {
    static let gap = 36.0

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func modulo(_ x: Double, _ period: Double) -> Double {
        (x.truncatingRemainder(dividingBy: period) + period).truncatingRemainder(dividingBy: period)
    }
    static func index(_ slot: Int) -> Int { ((slot % 6) + 6) % 6 }
    static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func pop(at age: Double) -> (up: Double, wobble: Double) {
        if age < 0 || age > 1.3 { return (0, 0) }
        if age < 0.4 {
            let x = age / 0.4 - 1
            return (1 + 2.4 * pow(x, 3) + 1.4 * pow(x, 2), 0)
        }
        if age < 0.9 {
            let x = age - 0.4
            return (1, 2 * sin(x * 14) * exp(-x * 2))
        }
        return (1 - cubic(clamp((age - 0.9) / 0.4)), 0)
    }
    static func wall(at elapsed: Double) -> DiscRingStackWall {
        let t = modulo(elapsed, DiscRingStack.loop)
        let slot = Int(floor(t / 1.5))
        let pop = pop(at: t - Double(slot) * 1.5)
        return DiscRingStackWall(shift: 24 * t, slot: slot, up: pop.up, wobble: pop.wobble)
    }
}

private enum DiscRingStackDrawing {
    struct Token {
        var name: String
        var face: UInt32
        var deep: UInt32
        var icon: String
        var count: Int
    }

    static let background = color(0x07070A)
    static let tokens = [
        Token(name: "Star token", face: 0xFFB23F, deep: 0xE8650F, icon: "star", count: 12),
        Token(name: "Bolt token", face: 0x5AA8FF, deep: 0x1F4FD8, icon: "bolt", count: 7),
        Token(name: "Leaf token", face: 0x3FD58A, deep: 0x0F8A4A, icon: "leaf", count: 21),
        Token(name: "Heart token", face: 0xFF6A6A, deep: 0xC8202E, icon: "heart", count: 4),
        Token(name: "Moon token", face: 0xA98BFF, deep: 0x5B2BD8, icon: "moon", count: 9),
        Token(name: "Crown token", face: 0xE8C27A, deep: 0xA8762A, icon: "crown", count: 2),
    ]
    static let icons = tokens.map { icon($0.icon) }
    // Skia interpolates both RGB and alpha toward transparent black. SwiftUI normally
    // premultiplies the endpoints; intermediate stops retain the reference's dim spotlight.
    static let spotlight = Gradient(stops: (0...32).map { i in
        let t = Double(i) / 32
        let v = 1 - t
        return Gradient.Stop(color: Color(.sRGB, red: 120 / 255 * v, green: 130 / 255 * v,
                                          blue: 170 / 255 * v, opacity: 0.28 * v), location: t)
    })

    static func color(_ hex: UInt32, opacity: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255,
              green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: opacity)
    }

    static func circle(_ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: -radius, y: -radius, width: 2 * radius, height: 2 * radius))
    }

    static func token(context: GraphicsContext, index: Int, x: Double, y: Double, yawDegrees: Double, scale: Double) {
        let token = tokens[index]
        let yaw = yawDegrees * .pi / 180
        let squeeze = max(0.06, cos(yaw))
        let radius = 64 * scale
        let edge = 14 * scale * sin(yaw)
        var side = context
        side.translateBy(x: x + edge, y: y)
        side.scaleBy(x: squeeze, y: 1)
        // Match the reference's side shader coordinates, including their local offset.
        side.fill(circle(radius), with: .linearGradient(
            Gradient(colors: [color(0x8D8F99), color(0x3C3E46), color(0xA7A9B3)]),
            startPoint: CGPoint(x: x, y: y - radius), endPoint: CGPoint(x: x, y: y + radius)
        ))
        var face = context
        face.translateBy(x: x, y: y)
        face.scaleBy(x: squeeze, y: 1)
        face.fill(circle(radius), with: .linearGradient(
            Gradient(stops: [
                .init(color: color(0xF4F5F8), location: 0),
                .init(color: color(0x9EA1AC), location: 0.4),
                .init(color: color(0xE6E7EC), location: 0.6),
                .init(color: color(0x6E717C), location: 1),
            ]),
            startPoint: CGPoint(x: -radius, y: -radius), endPoint: CGPoint(x: radius, y: radius)
        ))
        face.fill(circle(radius * 0.82), with: .radialGradient(
            Gradient(colors: [color(token.face), color(token.deep)]),
            center: CGPoint(x: -radius * 0.3, y: -radius * 0.35), startRadius: 0, endRadius: radius * 1.3
        ))
        face.stroke(circle(radius * 0.7), with: .color(.white.opacity(0.35)), lineWidth: 2)
        var symbol = face
        symbol.scaleBy(x: radius * 0.38, y: radius * 0.38)
        symbol.fill(icons[index], with: .color(.white))
        face.fill(circle(radius * 0.82), with: .linearGradient(
            Gradient(colors: [.white.opacity(0.35), .white.opacity(0)]),
            startPoint: CGPoint(x: 0, y: -radius), endPoint: .zero
        ))
    }

    static func wall(context: GraphicsContext, wall: DiscRingStackWall, size: CGSize) {
        let width = size.width
        let height = size.height
        let cx = width / 2
        let top = height * 0.64
        let baseY = top - 30
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .radialGradient(
            spotlight,
            center: CGPoint(x: cx, y: top - 60), startRadius: 0, endRadius: width * 0.8
        ))
        let first = Int(floor((wall.shift - cx - 80) / 36))
        let last = Int(ceil((wall.shift + cx + 80) / 36))
        var popped: Int?
        for slot in stride(from: last, through: first, by: -1) {
            if slot == wall.slot && wall.up > 0.001 {
                popped = slot
                continue
            }
            token(context: context, index: DiscRingStackMotion.index(slot),
                  x: cx + Double(slot) * 36 - wall.shift, y: baseY, yawDegrees: 66, scale: 1)
        }
        context.fill(Path(CGRect(x: 0, y: top, width: width, height: 150)), with: .linearGradient(
            Gradient(colors: [color(0xB9BBC3), color(0x8A8D96)]),
            startPoint: CGPoint(x: 0, y: top), endPoint: CGPoint(x: 0, y: top + 150)
        ))
        for slot in first...last where ((slot % 2) + 2) % 2 == 0 {
            let x = cx + Double(slot) * 36 - wall.shift - 18
            var seam = Path()
            seam.move(to: CGPoint(x: x, y: top))
            seam.addLine(to: CGPoint(x: x, y: top + 150))
            context.stroke(seam, with: .color(color(0x282A32, opacity: 0.35)), lineWidth: 1.5)
        }
        context.fill(Path(CGRect(x: 0, y: top, width: width, height: 3)), with: .color(color(0xD9DBE2)))
        if let popped {
            let lift = max(0, wall.up)
            token(context: context, index: DiscRingStackMotion.index(popped),
                  x: cx + Double(popped) * 36 - wall.shift, y: baseY - 150 * wall.up,
                  yawDegrees: 66 * (1 - min(1, lift)) + wall.wobble, scale: 1 + 0.3 * min(1.1, lift))
        }
    }

    static func icon(_ kind: String) -> Path {
        var path = Path()
        switch kind {
        case "star":
            for i in 0..<10 {
                let angle = -.pi / 2 + Double(i) * .pi / 5
                let radius = i % 2 == 0 ? 1.0 : 0.45
                let point = CGPoint(x: cos(angle) * radius, y: sin(angle) * radius)
                if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
            }
        case "bolt":
            path.move(to: CGPoint(x: 0.2, y: -1))
            for p in [CGPoint(x: -0.55, y: 0.12), CGPoint(x: -0.02, y: 0.12),
                      CGPoint(x: -0.25, y: 1), CGPoint(x: 0.55, y: -0.15), CGPoint(x: 0.02, y: -0.15)] {
                path.addLine(to: p)
            }
        case "leaf":
            path.move(to: CGPoint(x: -0.7, y: 0.75))
            path.addCurve(to: CGPoint(x: 0.85, y: -0.85), control1: CGPoint(x: -0.9, y: -0.3), control2: CGPoint(x: 0, y: -0.95))
            path.addCurve(to: CGPoint(x: -0.7, y: 0.75), control1: CGPoint(x: 0.95, y: 0), control2: CGPoint(x: 0.3, y: 0.9))
        case "heart":
            path.move(to: CGPoint(x: 0, y: 0.9))
            path.addCurve(to: CGPoint(x: 0, y: -0.45), control1: CGPoint(x: -1.1, y: 0.1), control2: CGPoint(x: -0.9, y: -0.95))
            path.addCurve(to: CGPoint(x: 0, y: 0.9), control1: CGPoint(x: 0.9, y: -0.95), control2: CGPoint(x: 1.1, y: 0.1))
        case "moon":
            path.move(to: CGPoint(x: 0.2, y: -0.95))
            path.addCurve(to: CGPoint(x: -0.35, y: 0.8), control1: CGPoint(x: -0.75, y: -0.85), control2: CGPoint(x: -1, y: 0.35))
            path.addCurve(to: CGPoint(x: 0.95, y: 0.35), control1: CGPoint(x: 0.15, y: 1.1), control2: CGPoint(x: 0.75, y: 0.85))
            path.addCurve(to: CGPoint(x: 0.2, y: -0.95), control1: CGPoint(x: 0.3, y: 0.55), control2: CGPoint(x: -0.35, y: 0.05))
        default:
            path.move(to: CGPoint(x: -0.9, y: 0.6))
            for p in [CGPoint(x: -0.95, y: -0.55), CGPoint(x: -0.45, y: -0.05), CGPoint(x: 0, y: -0.8),
                      CGPoint(x: 0.45, y: -0.05), CGPoint(x: 0.95, y: -0.55), CGPoint(x: 0.9, y: 0.6)] {
                path.addLine(to: p)
            }
        }
        path.closeSubpath()
        return path
    }
}

#Preview {
    DiscRingStack(autoPlay: true)
}
