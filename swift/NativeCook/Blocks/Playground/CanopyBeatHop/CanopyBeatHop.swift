import SwiftUI
import UIKit

/// An offline rhythm-hop course. All motion is sampled from a single clock.
struct CanopyBeatHop: View {
    static let loop: Double = 10
    var autoPlay = false
    /// Fixed phase for previews and deterministic frame comparisons.
    var previewTime: Double? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var epoch = Date()
    @State private var manualStart: Date?
    @State private var lastTouch = Date.distantPast
    @State private var releaseTime: Date?
    @State private var startTravel = 0.0
    @State private var steering = 0.0
    @State private var releaseVelocity = 0.0
    @State private var dragging = false
    @State private var resuming = false
    @State private var resumeTask: Task<Void, Never>?

    var body: some View {
        GeometryReader { geometry in
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || (!autoPlay && manualStart == nil && !resuming) || previewTime != nil)) { tick in
                let elapsed = max(0, tick.date.timeIntervalSince(epoch))
                let time = reduceMotion ? 0 : (previewTime ?? (autoPlay ? elapsed.truncatingRemainder(dividingBy: Self.loop) : 0))
                let manualAge = manualStart.map { max(0, tick.date.timeIntervalSince($0)) }
                let releaseAge = releaseTime.map { max(0, tick.date.timeIntervalSince($0)) } ?? 0
                let lane = dragging ? steering : steering + releaseVelocity * releaseAge * exp(-10 * releaseAge)
                let frame = CanopyBeatHopFrame(
                    time: time,
                    travel: manualAge.map { startTravel + (reduceMotion ? 0 : $0 * (24 / 8.38)) },
                    steering: manualStart == nil ? nil : lane,
                    still: reduceMotion
                )
                Canvas { context, size in
                    context.scaleBy(x: size.width / 393, y: size.height / 852)
                    frame.draw(&context)
                }
                .overlay {
                    // Native text supplies a discoverable touch target for the drawn game.
                    Color.clear
                        .contentShape(Rectangle())
                        .accessibilityLabel("Canopy Beat Hop game")
                        .accessibilityValue(reduceMotion ? "Ready. Reduced motion" : "Forest rhythm course")
                        .accessibilityHint("Drag left or right to steer. Tap to hop.")
                        .accessibilityAddTraits(.allowsDirectInteraction)
                        .accessibilityAction(named: "Move Left") { accessibleMove(-0.75) }
                        .accessibilityAction(named: "Move Right") { accessibleMove(0.75) }
                        .accessibilityAction(named: "Hop") { accessibleMove(0) }
                }
                .gesture(DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        let now = Date()
                        resumeTask?.cancel()
                        if !dragging {
                            if manualStart == nil {
                                startTravel = frame.progress
                                manualStart = now
                            }
                            UIImpactFeedbackGenerator(style: .light).impactOccurred()
                        }
                        dragging = true
                        lastTouch = now
                        releaseTime = nil
                        steering = max(-1.35, min(1.35, (value.location.x / geometry.size.width * 393 - 196.5) / 100))
                    }
                    .onEnded { value in
                        releaseVelocity = max(-3, min(3, (value.predictedEndTranslation.width - value.translation.width) / geometry.size.width * 2))
                        dragging = false
                        releaseTime = Date()
                        scheduleResume()
                    })
                .overlay {
                    let age = max(0, tick.date.timeIntervalSince(lastTouch))
                    let veil = manualStart == nil
                        ? (resuming ? 1 - CanopyBeatHopFrame.smooth(elapsed / 0.25) : 0)
                        : (dragging ? 0 : CanopyBeatHopFrame.smooth((age - 2) / 0.25))
                    Color(red: 0.03, green: 0.37, blue: 0.31).opacity(veil).allowsHitTesting(false)
                }
            }
        }
        .ignoresSafeArea()
        .preferredColorScheme(.dark)
        .onAppear { epoch = Date() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .active { epoch = Date(); manualStart = nil }
        }
        .onDisappear { resumeTask?.cancel() }
    }

    private func accessibleMove(_ lane: Double) {
        resumeTask?.cancel()
        if manualStart == nil { manualStart = Date(); startTravel = 0 }
        steering = lane
        lastTouch = Date()
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        scheduleResume()
    }

    private func scheduleResume() {
        lastTouch = Date()
        resumeTask = Task { @MainActor in
            try? await Task.sleep(for: .milliseconds(2250))
            guard !Task.isCancelled else { return }
            manualStart = nil
            resuming = !reduceMotion
            epoch = Date()
            try? await Task.sleep(for: .milliseconds(250))
            guard !Task.isCancelled else { return }
            resuming = false
        }
    }
}

private struct CanopyBeatHopFrame {
    static let lanes = [0.0, 0.45, -0.45, -0.8, 0.0, 0.8, 0.85, -0.85, 0.85, -0.85, 0.85, -0.85, 0.85, 0.0, -0.65, 0.65, -1.6, 0.0, 1.6, 0.0, -1.6, 0.0, 0.45, 0.0]
    static let faces = [0x8CFFCB, 0x91CBFF, 0xF58CF4, 0xFFE395, 0x8CFFCB]
    static let neons = [0x00FFAB, 0x2AAEFF, 0xF82AEB, 0xFFC22D, 0x00FFAB]
    static let grounds = [0x0FB36B, 0x165FA5, 0xA00E9B, 0xAC7417, 0x0FB36B]
    static let artwork: [Image] = ["day", "night", "violet", "amber"].map { name in
        Image(uiImage: UIImage(named: "CanopyBeatHop-\(name).png") ?? UIImage())
    }
    static let courier = Image(uiImage: UIImage(named: "CanopyBeatHop-courier.png") ?? UIImage())

    let time: Double
    var travel: Double?
    var steering: Double?
    var still = false

    static func smooth(_ value: Double) -> Double {
        let f = min(1, max(0, value)); return f * f * (3 - 2 * f)
    }
    static func index(_ n: Int) -> Int { (n % 24 + 24) % 24 }
    static func lane(_ n: Int) -> Double { lanes[index(n)] }
    static func rgb(_ hex: Int) -> Color {
        Color(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255)
    }
    static func blend(_ a: Int, _ b: Int, _ f: Double) -> Color {
        func channel(_ shift: Int) -> Double {
            let x = Double((a >> shift) & 255), y = Double((b >> shift) & 255)
            return (x + (y - x) * f) / 255
        }
        return Color(red: channel(16), green: channel(8), blue: channel(0))
    }
    var progress: Double {
        if let travel { return travel }
        let q = min(1, max(0, (time - 0.75) / 8.5)), r = 0.12 / 8.5
        let integrated = q < r ? q * q / (2 * r) : (q > 1 - r ? 1 - r - (1 - q) * (1 - q) / (2 * r) : q - r / 2)
        return integrated / (1 - r) * 24
    }
    var palette: (Int, Double) {
        if time < 2.15 { return (0, 0) }
        if time < 4.15 { return (0, Self.smooth((time - 2.15) / 0.35)) }
        if time < 6.15 { return (1, Self.smooth((time - 4.15) / 0.35)) }
        if time < 8.15 { return (2, Self.smooth((time - 6.15) / 0.35)) }
        return (3, Self.smooth((time - 8.15) / 0.35))
    }

    func draw(_ context: inout GraphicsContext) {
        let (theme, mix) = palette
        let rect = CGRect(x: 0, y: 0, width: 393, height: 852)
        context.draw(Self.artwork[theme % 4], in: rect)
        var next = context; next.opacity = mix
        next.draw(Self.artwork[(theme + 1) % 4], in: rect)
        let neon = Self.blend(Self.neons[theme], Self.neons[theme + 1], mix)
        let face = Self.blend(Self.faces[theme], Self.faces[theme + 1], mix)
        let ground = Self.blend(Self.grounds[theme], Self.grounds[theme + 1], mix)
        let p = progress, step = Int(floor(p)), f = p - floor(p)
        let resting = travel == nil && (time <= 0.75 || time >= 9.25)
        let jump = still || resting ? 0 : 4 * f * (1 - f)
        let currentLane = Self.lane(step), nextLane = Self.lane(step + 1)
        let lane = steering ?? (currentLane + (nextLane - currentLane) * Self.smooth(f))
        let playerX = 196.5 + lane * 100

        // Only five or six approaching platforms: the large spent rim owns the foreground.
        for i in stride(from: step + 6, through: step - 2, by: -1) {
            let d = Double(i) - p
            if d < -1.7 || d > 5.5 { continue }
            let s = d < 0 ? 1 - 0.8 * d : 1 / (1 + 0.8 * d)
            let y = d < 0 ? 615 - 270 * d : 225 + 390 * s - 15 * d
            let n = Self.index(i), selected = Self.lane(i)
            var lanes = [selected]
            if (5...10).contains(n) { lanes.append(selected + (selected > 0 ? -1.9 : 1.9)) }
            if (16...21).contains(n) { lanes = [-1.6, 0, 1.6] }
            for position in lanes {
                let x = 196.5 + position * 100 * s
                let active = abs(position - selected) < 0.01
                let hit = d <= 0.02
                var tileContext = context
                tileContext.opacity = 1 - Self.smooth((d - 4.8) / 0.7)
                drawTile(&tileContext, x, y, s, active && !hit, neon, face, ground, d)
                if active && !hit {
                    if n % 5 == 3 && d > 0.15 { crystal(&tileContext, x, y - 22 * s, 7 * s, 17 * s) }
                    if n % 4 == 1 { tileContext.fill(diamond(x, y, 22 * s, 9 * s), with: .color(.white.opacity(0.5))) }
                }
            }
        }
        let squash = still || resting ? 0 : exp(-f * 22) * cos(f * 22) * 0.06
        let lean = still || resting ? 0 : (steering == nil ? (nextLane - currentLane) * sin(f * .pi) * 0.05 : lane * 0.035)
        drawCourier(&context, playerX, jump, squash, lean, 1)
        if !still && !resting && step % 5 == 3 && f < 0.75 {
            let life = min(1, f / 0.75), radius = 46 * (1 - pow(1 - life, 3))
            for j in 0..<12 {
                let angle = Double(j) * .pi / 6
                context.fill(diamond(playerX + cos(angle) * radius, 533 - jump * 100 + sin(angle) * radius * 0.65 - life * 20, 2.5 * (1 - life), 4 * (1 - life)), with: .color(Self.rgb(j % 2 == 0 ? 0xD5FFFF : 0x63B9FF).opacity(1 - life)))
            }
        }
        let n = Self.index(step)
        let perfect = !resting && [2, 3, 4, 9, 10, 18].contains(n)
        let combo = (2...4).contains(n) ? n - 1 : ((9...10).contains(n) ? n - 8 : 1)
        if perfect && !still {
            let flash = 0.85 * exp(-f * 11)
            context.fill(Path(ellipseIn: CGRect(x: 114, y: 4, width: 165, height: 165)), with: .radialGradient(Gradient(colors: [.white.opacity(flash), .white.opacity(flash * 0.5), .clear]), center: CGPoint(x: 196.5, y: 86), startRadius: 2, endRadius: 83))
        }
        let alpha = resting ? 1 : 0.72 + 0.28 * exp(-f * 8)
        let size = (perfect ? 39.0 : 34.0) * (resting ? 1 : 1 + 0.08 * exp(-f * 12))
        outlinedLabel(&context, perfect ? "PERFECT" : "GREAT", 196.5, 86, size, Self.rgb(perfect ? 0xF020D9 : 0x159FD0), alpha)
        if perfect { outlinedLabel(&context, "X\(combo)", 196.5, 133, 19, Self.rgb(0xF43CE4), alpha) }
    }

    private func drawTile(_ context: inout GraphicsContext, _ x: Double, _ y: Double, _ s: Double, _ active: Bool, _ neon: Color, _ face: Color, _ ground: Color, _ distance: Double) {
        let top = tile(x, y, s)
        let side = Path { path in
            path.move(to: CGPoint(x: x - 103 * s, y: y + 57 * s)); path.addLine(to: CGPoint(x: x + 103 * s, y: y + 57 * s))
            path.addLine(to: CGPoint(x: x + 101 * s, y: y + 102 * s)); path.addLine(to: CGPoint(x: x - 101 * s, y: y + 102 * s)); path.closeSubpath()
        }
        context.fill(side, with: .linearGradient(Gradient(colors: [active ? face.opacity(0.8) : neon.opacity(0.3), neon.opacity(0.28), ground.opacity(0)]), startPoint: CGPoint(x: x, y: y + 50 * s), endPoint: CGPoint(x: x, y: y + 105 * s)))
        var glow = context
        glow.addFilter(.shadow(color: neon.opacity(active ? 0.9 : 0.5), radius: 8 * s))
        glow.fill(top, with: .color(active ? face : ground))
        glow.stroke(top, with: .color(neon.opacity(0.8)), lineWidth: 6 * s)
        context.stroke(top, with: .color(Color(red: 0.92, green: 1, blue: 0.99)), lineWidth: 2.8 * s)
        if distance < -0.04 {
            for j in 1...2 {
                var echo = context
                echo.opacity = max(0, (0.43 + distance * 0.3) / Double(j))
                echo.addFilter(.blur(radius: 1.2 * s))
                echo.stroke(tile(x, y, s * (1 + Double(j) * 0.26)), with: .color(neon), lineWidth: 2 * s)
            }
        }
    }
    private func drawCourier(_ context: inout GraphicsContext, _ x: Double, _ jump: Double, _ squash: Double, _ lean: Double, _ opacity: Double) {
        var sprite = context; sprite.opacity = opacity
        let sw = 55 * (1 - jump * 0.3)
        var shadow = sprite; shadow.addFilter(.blur(radius: 4))
        shadow.fill(Path(ellipseIn: CGRect(x: x - sw / 2, y: 615, width: sw, height: 13)), with: .color(.black.opacity(0.27 - jump * 0.17)))
        sprite.translateBy(x: x, y: 615 - jump * 170)
        sprite.rotate(by: .radians(lean))
        let scale = 0.89 + jump * 0.11
        sprite.scaleBy(x: scale * (1 + squash * 0.5), y: scale * (1 - squash))
        sprite.draw(Self.courier, in: CGRect(x: -50, y: -143, width: 100, height: 143))
    }
    private func tile(_ x: Double, _ y: Double, _ s: Double) -> Path {
        let pts = [CGPoint(x: x - 85 * s, y: y - 60 * s), CGPoint(x: x + 85 * s, y: y - 60 * s), CGPoint(x: x + 104 * s, y: y + 60 * s), CGPoint(x: x - 104 * s, y: y + 60 * s)]
        var path = Path()
        for i in 0..<4 {
            let a = pts[(i + 3) % 4], b = pts[i], c = pts[(i + 1) % 4]
            let entry = CGPoint(x: b.x + (a.x - b.x) * 0.07, y: b.y + (a.y - b.y) * 0.07)
            let exit = CGPoint(x: b.x + (c.x - b.x) * 0.07, y: b.y + (c.y - b.y) * 0.07)
            if i == 0 { path.move(to: entry) } else { path.addLine(to: entry) }
            path.addQuadCurve(to: exit, control: b)
        }
        path.closeSubpath(); return path
    }
    private func diamond(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path { path in
            path.move(to: CGPoint(x: x, y: y - h)); path.addLine(to: CGPoint(x: x + w, y: y))
            path.addLine(to: CGPoint(x: x, y: y + h)); path.addLine(to: CGPoint(x: x - w, y: y)); path.closeSubpath()
        }
    }
    private func crystal(_ context: inout GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double) {
        let shape = diamond(x, y, w, h)
        context.fill(shape, with: .linearGradient(Gradient(colors: [Self.rgb(0xBCFFFF), Self.rgb(0x147AFF)]), startPoint: CGPoint(x: x - w, y: y - h), endPoint: CGPoint(x: x + w, y: y + h)))
        var facet = Path(); facet.move(to: CGPoint(x: x, y: y - h)); facet.addLine(to: CGPoint(x: x, y: y + h)); facet.addLine(to: CGPoint(x: x - w, y: y)); facet.closeSubpath()
        context.fill(facet, with: .color(Self.rgb(0x9BEEFF).opacity(0.7)))
    }
    private func outlinedLabel(_ context: inout GraphicsContext, _ value: String, _ x: Double, _ y: Double, _ size: Double, _ ink: Color, _ opacity: Double) {
        func label(_ dx: Double, _ dy: Double, _ color: Color) {
            context.draw(Text(value).font(.system(size: size, weight: .heavy, design: .rounded)).foregroundColor(color.opacity(opacity)), at: CGPoint(x: x + dx, y: y + dy))
        }
        label(1.5, 3, .black.opacity(0.25))
        for j in 0..<8 {
            let angle = Double(j) * .pi / 4
            label(cos(angle) * 1.5, sin(angle) * 1.5, .white)
        }
        label(0, 0, ink)
    }
}
