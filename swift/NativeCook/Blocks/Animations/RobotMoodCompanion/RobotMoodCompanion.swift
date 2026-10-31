import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Pip's six moods, faithfully ported from the React Native block.
/// 8.4 s loop: switches at 1.2, 2.6, 4.0, 5.4, 6.8, 8.2 s; eyes close for
/// 0.12 s and open for 0.14 s. Room, pose, title and dots share the same smoothstep.
/// Happy bobs; Surprised hops; Sleepy sways; Proud floats; Sad cries; Grumpy shakes.
/// A tap picks the next blink and pauses its clock for 2 s, exactly as in RN.
struct RobotMoodCompanion: View {
    var brand = "pip"
    var action = "Talk to Pip"
    var autoPlay = false

    static let loop: Double = 8.4
    private typealias M = RobotMoodCompanionMotion

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var touchedAt: Date?
    @State private var tappedClock = 0.6
    @State private var suspendedAt: Date?
    @State private var reducedClock = 0.6
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            TimelineView(.animation(paused: reduceMotion || scenePhase != .active || !autoPlay && touchedAt == nil)) { timeline in
                let time = clock(at: timeline.date)
                let mood = M.mood(at: time)
                Canvas { context, _ in
                    RobotMoodCompanionDrawing.draw(&context, size: size, safe: safe,
                                                   time: time, brand: brand, action: action)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .contentShape(Rectangle())
                .onTapGesture { tap() }
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Pip. \(M.moods[mood.cur].name). \(M.moods[mood.cur].line)")
                .accessibilityHint("Tap to change its mood.")
                .accessibilityAddTraits(.isButton)
                .accessibilityAction { tap() }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .onAppear {
            RobotMoodCompanionType.prepare(brand: brand, action: action)
            _ = RobotMoodCompanionDrawing.head
            origin = .now
            if reduceMotion || scenePhase != .active { suspendedAt = .now }
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            setPaused(reduceMotion || phase != .active)
        }
        .onChange(of: reduceMotion) { _, reduced in
            if reduced { reducedClock = clock(at: .now, respectReduced: false) }
            setPaused(reduced || scenePhase != .active)
        }
        .task(id: "\(touchedAt?.timeIntervalSinceReferenceDate ?? -1)|\(scenePhase == .active)|\(reduceMotion)") {
            guard let touchedAt, !autoPlay, !reduceMotion, scenePhase == .active else { return }
            let remaining = max(0, M.hold - Date().timeIntervalSince(touchedAt))
            try? await Task.sleep(for: .seconds(remaining))
            guard !Task.isCancelled, self.touchedAt == touchedAt else { return }
            self.touchedAt = nil
        }
    }

    /// Derive frames from dates, without writing SwiftUI state on every display tick.
    private func setPaused(_ paused: Bool) {
        let now = Date()
        if paused {
            if suspendedAt == nil { suspendedAt = now }
        } else if let start = suspendedAt {
            let duration = now.timeIntervalSince(start)
            origin = origin.addingTimeInterval(duration)
            if let touchedAt { self.touchedAt = touchedAt.addingTimeInterval(duration) }
            suspendedAt = nil
        }
    }

    private func clock(at date: Date, respectReduced: Bool = true) -> Double {
        if respectReduced && reduceMotion { return reducedClock }
        let now = suspendedAt ?? date
        if let touchedAt {
            let age = max(0, now.timeIntervalSince(touchedAt))
            // RN's frozen guard is s < 1.26. The next-blink tap target is s = 1.28,
            // so it holds before the blink, then lets the loop continue after 2 s.
            if age <= M.hold { return tappedClock }
            if !autoPlay { return 0.6 }
            return M.normalized(tappedClock + age - M.hold)
        }
        return autoPlay ? M.normalized(now.timeIntervalSince(origin)) : 0.6
    }

    private func tap() {
        guard !reduceMotion else { return }
        let now = Date()
        let time = clock(at: now)
        let mood = M.mood(at: time)
        let next = M.first + Double(mood.cur) * M.stop - M.close
        tappedClock = mood.cur == 0 && time > M.first + 5 * M.stop
            ? M.first - M.close : M.normalized(next)
        origin = now.addingTimeInterval(-tappedClock)
        touchedAt = now
        haptic.impactOccurred()
    }
}

private enum RobotMoodCompanionMotion {
    static let stop = 1.4
    static let first = 1.2
    static let close = 0.12
    static let open = 0.14
    static let hold = 2.0
    static let head = 300.0

    struct Mood {
        var name: String
        var line: String
        var background: UInt32
        var ink: UInt32
    }
    static let moods: [Mood] = [
        Mood(name: "Happy", line: "Pip had a good day. So did you?", background: 0xF8E6A0, ink: 0x3A2E0A),
        Mood(name: "Surprised", line: "Three new messages, all at once.", background: 0xF0957F, ink: 0x3E150B),
        Mood(name: "Sleepy", line: "Past eleven. Pip is winding down.", background: 0xB49E9E, ink: 0x2E2222),
        Mood(name: "Proud", line: "You finished every task today.", background: 0x55CF9C, ink: 0x0D3B27),
        Mood(name: "Sad", line: "You skipped your walk again.", background: 0x1F4652, ink: 0xE6F6FA),
        Mood(name: "Grumpy", line: "Four hours of meetings? Really?", background: 0xE8206E, ink: 0xFFF0F5),
    ]
    struct Phase {
        var cur: Int
        var prev: Int
        var fade: Double
        var open: Double
        var s: Double
        func weight(_ k: Int) -> Double { cur == k ? fade : prev == k ? 1 - fade : 0 }
    }
    struct Pose { var x = 0.0; var y = 0.0; var rotation = 0.0 }
    static func normalized(_ t: Double) -> Double {
        let remainder = t.truncatingRemainder(dividingBy: RobotMoodCompanion.loop)
        return remainder < 0 ? remainder + RobotMoodCompanion.loop : remainder
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func mood(at t: Double) -> Phase {
        let k = Int(floor((t - first) / stop)) + 1
        let since = t - (first + Double(k - 1) * stop)
        let s = k == 0 ? t + (RobotMoodCompanion.loop - (first + 5 * stop)) : since
        let toNext = first + Double(k) * stop - t
        var eyeOpen = 1.0
        if s < open { eyeOpen = smooth(s / open) }
        if toNext < close { eyeOpen = smooth(toNext / close) }
        let current = ((k % 6) + 6) % 6
        return Phase(cur: current, prev: (current + 5) % 6,
                     fade: smooth((s + close) / (close + open)), open: eyeOpen, s: s)
    }
    static func pose(_ m: Int, _ s: Double) -> Pose {
        switch m {
        case 0: return Pose(y: -6 * abs(sin(s * .pi * 2.2)))
        case 1: return Pose(y: -16 * sin(.pi * clamp(s / 0.45)))
        case 2: return Pose(y: 6, rotation: 7 * sin(s * 1.6 + 0.4))
        case 3: return Pose(y: -10 - 4 * sin(s * .pi * 1.4), rotation: -3)
        case 4: return Pose(y: 10, rotation: -5)
        default: return Pose(x: 3.5 * sin(s * 42) * exp(-2 * s))
        }
    }
    static func color(_ a: UInt32, _ b: UInt32, _ u: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            let first = Double((a >> shift) & 255), second = Double((b >> shift) & 255)
            return (first * (1 - u) + second * u).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }
}

private enum RobotMoodCompanionDrawing {
    private typealias M = RobotMoodCompanionMotion
    static let cyan = Color(.sRGB, red: 63 / 255, green: 224 / 255, blue: 240 / 255)
    static let head: Image? = {
        guard let url = Bundle.main.url(forResource: "RobotMoodCompanion-robot-head", withExtension: "png"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let image = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
        else { return nil }
        return Image(decorative: image, scale: 1)
    }()

    static func draw(_ c: inout GraphicsContext, size: CGSize, safe: EdgeInsets, time: Double,
                     brand: String, action: String) {
        let mood = M.mood(at: time)
        let background = M.color(M.moods[mood.prev].background, M.moods[mood.cur].background, mood.fade)
        let ink = M.color(M.moods[mood.prev].ink, M.moods[mood.cur].ink, mood.fade)
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .color(background))
        scene(&c, phase: mood, center: CGPoint(x: size.width / 2, y: size.height * 0.52))

        let top = safe.top + 8
        let brandText = RobotMoodCompanionType.text(brand, size: 24, headline: true)
        brandText.draw(&c, at: CGPoint(x: 24, y: top + (34 - brandText.size.height) / 2), color: ink)
        let meta = RobotMoodCompanionType.text("Battery 82 %", size: 14, semibold: true)
        meta.draw(&c, at: CGPoint(x: size.width - 24 - meta.size.width, y: top + (34 - meta.size.height) / 2), color: ink.opacity(0.7))
        for k in M.moods.indices {
            let weight = mood.weight(k)
            if weight <= 0 { continue }
            var text = c
            text.opacity = weight
            let y = safe.top + 64 + 12 * (1 - weight)
            RobotMoodCompanionType.text(M.moods[k].name, size: 64, headline: true, tracking: -2, lineHeight: 70)
                .draw(&text, at: CGPoint(x: 24, y: y), color: ink)
            RobotMoodCompanionType.text(M.moods[k].line, size: 18, lineHeight: 24)
                .draw(&text, at: CGPoint(x: 24, y: y + 74), color: ink.opacity(0.8))
        }
        let buttonY = size.height - safe.bottom - 14 - 58
        let button = CGRect(x: 24, y: buttonY, width: size.width - 48, height: 58)
        c.fill(Path(roundedRect: button, cornerRadius: 29), with: .color(ink))
        let label = RobotMoodCompanionType.text(action, size: 18, headline: true)
        label.draw(&c, at: CGPoint(x: (size.width - label.size.width) / 2, y: button.midY - label.size.height / 2), color: background)
        let widths = M.moods.indices.map { 8 + 18 * mood.weight($0) }
        var x = (size.width - widths.reduce(0, +) - 5 * 8) / 2
        for k in M.moods.indices {
            let rect = CGRect(x: x, y: buttonY - 28, width: widths[k], height: 8)
            c.fill(Path(roundedRect: rect, cornerRadius: 4), with: .color(ink.opacity(0.3 + 0.7 * mood.weight(k))))
            x += widths[k] + 8
        }
    }

    private static func ellipse(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(ellipseIn: CGRect(x: x, y: y, width: w, height: h))
    }
    /// Skia's positive sweep follows screen coordinates, clockwise from the right.
    private static func arc(_ rect: CGRect, start: Double, sweep: Double) -> Path {
        let base = Path { path in
            path.addArc(center: .zero, radius: 1, startAngle: .degrees(start),
                        endAngle: .degrees(start + sweep), clockwise: false)
        }
        return base.applying(CGAffineTransform(a: rect.width / 2, b: 0, c: 0, d: rect.height / 2,
                                               tx: rect.midX, ty: rect.midY))
    }
    private static func stroke(_ c: inout GraphicsContext, _ path: Path, width: Double, alpha: Double = 1) {
        c.stroke(path, with: .color(cyan.opacity(alpha)), style: StrokeStyle(lineWidth: width, lineCap: .round))
    }
    private static func scene(_ c: inout GraphicsContext, phase mo: M.Phase, center: CGPoint) {
        let a = M.pose(mo.prev, mo.s + M.stop), b = M.pose(mo.cur, mo.s)
        let w = mo.fade
        let x = a.x * (1 - w) + b.x * w, y = a.y * (1 - w) + b.y * w
        let rotation = a.rotation * (1 - w) + b.rotation * w
        let shadowWidth = 200 * (1 - M.clamp(-y / 20) * 0.2)
        c.drawLayer { shadow in
            shadow.addFilter(.blur(radius: 10))
            shadow.fill(ellipse(center.x - shadowWidth / 2, center.y + 128, shadowWidth, 22), with: .color(.black.opacity(0.16)))
        }
        var headContext = c
        headContext.translateBy(x: center.x + x, y: center.y + y)
        headContext.rotate(by: .degrees(rotation))
        let halo = mo.cur == 3 ? M.smooth(mo.s / 0.3) : mo.prev == 3 ? 1 - w : 0
        if halo > 0 {
            let width = 150 * (0.4 + 0.6 * halo)
            headContext.stroke(ellipse(-width / 2, -128 - 12 * halo, width, 26),
                               with: .color(Color(hex: 0xFFF1C2).opacity(halo)), lineWidth: 9)
        }
        if let head { headContext.draw(head, in: CGRect(x: -150, y: -150, width: 300, height: 300)) }
        var visor = headContext
        let vx = 0.002 * M.head, vy = 0.002 * M.head
        visor.clip(to: Path(roundedRect: CGRect(x: vx - 96, y: vy - 61.5, width: 192, height: 123), cornerRadius: 50))
        face(&visor, mood: mo.cur, open: mo.open, time: mo.s, vx: vx, vy: vy, glow: true)
        face(&visor, mood: mo.cur, open: mo.open, time: mo.s, vx: vx, vy: vy, glow: false)
        if mo.cur == 2 {
            for i in 0..<3 {
                let u = (mo.s + Double(i) * 0.45).truncatingRemainder(dividingBy: 1.35) / 1.35
                let size = 8 + 8 * u
                let zx = center.x + 110 + 20 * u, zy = center.y - 110 - 70 * u
                let z = Path { path in
                    path.move(to: CGPoint(x: zx - size / 2, y: zy - size / 2))
                    path.addLine(to: CGPoint(x: zx + size / 2, y: zy - size / 2))
                    path.addLine(to: CGPoint(x: zx - size / 2, y: zy + size / 2))
                    path.addLine(to: CGPoint(x: zx + size / 2, y: zy + size / 2))
                }
                c.stroke(z, with: .color(.white.opacity(sin(.pi * u) * mo.open)), style: StrokeStyle(lineWidth: 3, lineCap: .round))
            }
        }
    }

    private static func eye(_ c: inout GraphicsContext, mood: Int, side: Double,
                            ex: Double, ey: Double, open: Double, glow: Bool) {
        let rx = 16.5, ry = max(1.5, 15 * open)
        let alpha = glow ? 0.65 : 1.0
        if mood == 2 || mood == 3 {
            c.drawLayer { layer in
                if glow { layer.addFilter(.blur(radius: 6)) }
                stroke(&layer, arc(CGRect(x: ex - rx, y: ey - 9, width: rx * 2, height: 18),
                                   start: mood == 2 ? 20 : 200, sweep: 140), width: 4, alpha: alpha)
            }
            return
        }
        let erx = mood == 1 ? 14 : rx, ery = mood == 1 ? max(1.5, 20 * open) : ry
        var clipped = c
        let clip: Path
        if mood == 0 {
            clip = Path(CGRect(x: ex - 30, y: ey - 40, width: 60, height: 43))
        } else if mood == 4 || mood == 5 {
            let inner = mood == 5 ? ey - 2 : ey - ery - 4
            let outer = mood == 5 ? ey - ery - 6 : ey - 3
            let yl = side < 0 ? outer : inner, yr = side < 0 ? inner : outer
            clip = Path { path in
                path.move(to: CGPoint(x: ex - 30, y: yl - (yr - yl) * (14.0 / 60)))
                path.addLine(to: CGPoint(x: ex + 30, y: yr + (yr - yl) * (14.0 / 60)))
                path.addLine(to: CGPoint(x: ex + 30, y: ey + 40))
                path.addLine(to: CGPoint(x: ex - 30, y: ey + 40))
                path.closeSubpath()
            }
        } else {
            clip = Path(CGRect(x: ex - 30, y: ey - 40, width: 60, height: 80))
        }
        clipped.clip(to: clip)
        clipped.drawLayer { layer in
            if glow { layer.addFilter(.blur(radius: 6)) }
            layer.fill(ellipse(ex - erx, ey - ery, erx * 2, ery * 2), with: .color(cyan.opacity(alpha)))
        }
    }

    private static func face(_ c: inout GraphicsContext, mood: Int, open: Double, time: Double,
                             vx: Double, vy: Double, glow: Bool) {
        eye(&c, mood: mood, side: -1, ex: vx - 42, ey: vy - 6, open: open, glow: glow)
        eye(&c, mood: mood, side: 1, ex: vx + 42, ey: vy - 6, open: open, glow: glow)
        let alpha = glow ? 0.65 : 1.0
        c.drawLayer { layer in
            if glow { layer.addFilter(.blur(radius: 6)) }
            switch mood {
            case 1:
                layer.fill(ellipse(vx - 5, vy + 24, 10, 13), with: .color(cyan.opacity(alpha)))
            case 3:
                stroke(&layer, arc(CGRect(x: vx - 9, y: vy + 16, width: 18, height: 12), start: 20, sweep: 140), width: 3.5, alpha: alpha)
            case 4:
                let run = time.truncatingRemainder(dividingBy: 0.9) / 0.9
                let length = 10 + 34 * run
                for side in [-1.0, 1.0] {
                    let x0 = vx + side * 46
                    let path = Path { path in
                        path.move(to: CGPoint(x: x0, y: vy + 6))
                        for i in 1...8 {
                            path.addLine(to: CGPoint(x: x0 + sin(Double(i) * 1.4 + time * 6) * 2.5,
                                                    y: vy + 6 + length * Double(i) / 8))
                        }
                    }
                    stroke(&layer, path, width: 7, alpha: alpha * (1 - run * 0.3))
                }
            case 5:
                let path = Path { path in
                    path.move(to: CGPoint(x: vx - 10, y: vy + 30))
                    path.addQuadCurve(to: CGPoint(x: vx + 10, y: vy + 30), control: CGPoint(x: vx, y: vy + 24))
                }
                stroke(&layer, path, width: 3.5, alpha: alpha)
            default: break
            }
        }
    }
}

/// Cache TextKit glyph outlines using RN's line boxes and baseline rules.
private enum RobotMoodCompanionType {
    struct Label {
        var path: Path
        var size: CGSize
        func draw(_ context: inout GraphicsContext, at point: CGPoint, color: Color) {
            context.fill(path.offsetBy(dx: point.x, dy: point.y), with: .color(color))
        }
    }
    static let registered: Void = {
        if let url = Bundle.main.url(forResource: "RobotMoodCompanion-BricolageHeadline", withExtension: "ttf") {
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
        }
    }()
    private static var cache: [String: Label] = [:]
    static func prepare(brand: String, action: String) {
        _ = text(brand, size: 24, headline: true)
        _ = text("Battery 82 %", size: 14, semibold: true)
        _ = text(action, size: 18, headline: true)
        for mood in RobotMoodCompanionMotion.moods {
            _ = text(mood.name, size: 64, headline: true, tracking: -2, lineHeight: 70)
            _ = text(mood.line, size: 18, lineHeight: 24)
        }
    }
    static func text(_ string: String, size: CGFloat, headline: Bool = false, semibold: Bool = false,
                     tracking: CGFloat = 0, lineHeight: CGFloat? = nil) -> Label {
        let key = "\(string)|\(size)|\(headline)|\(semibold)|\(tracking)|\(lineHeight ?? 0)"
        if let hit = cache[key] { return hit }
        _ = registered
        let font = headline ? UIFont(name: "BricolageHeadline-Regular", size: size) ?? .systemFont(ofSize: size)
            : .systemFont(ofSize: size, weight: semibold ? .semibold : .regular)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .kern: tracking]
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 2000, height: 200))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = 1
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container)
        let bounds = layout.usedRect(for: container)
        var outline = Path()
        for i in range.location..<NSMaxRange(range) {
            let position = layout.location(forGlyphAt: i)
            let fragment = layout.lineFragmentRect(forGlyphAt: i, effectiveRange: nil)
            var transform = CGAffineTransform(a: 1, b: 0, c: 0, d: -1,
                                               tx: position.x + fragment.minX, ty: position.y + fragment.minY)
            if let glyph = CTFontCreatePathForGlyph(font as CTFont, layout.glyph(at: i), &transform) {
                outline.addPath(Path(glyph))
            }
        }
        let label = Label(path: outline, size: bounds.size)
        cache[key] = label
        return label
    }
}

#Preview {
    RobotMoodCompanion(autoPlay: true)
}
