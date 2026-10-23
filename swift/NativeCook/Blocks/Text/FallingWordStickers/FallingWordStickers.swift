import CoreText
import SwiftUI
import UIKit

/*
 Falling Word Stickers: a studio's landing screen whose call to action, "your next big project starts
 here", is six pastel word stickers that drop from above the screen, bounce and settle into a pile
 at the bottom, tilted and overlapping.

 Layout (pt, W × H the whole screen): the words are Inter Tight Medium at S = 0.13 W (51 pt on a
 393 pt screen), "big" at 1.4 S; a sticker is the word's advance plus 0.24 em on each side, 1.12 em
 tall, corners 0.1 em, the baseline 0.262 em below its centre (half-way between cap height and the
 descender of "p", read from the font). The pile rests on a floor 20 pt above the bottom safe area:
 "your", "next" and "big" on the floor (centres at 0.165, 0.48 and 0.82 W, kept 8 pt inside the
 screen), "project" on the first two (0.36 W, 1.06 S up), "starts" on "big" (0.80 W, 1.52 S up) and
 "here" on "starts" (0.70 W, 2.58 S up), tilted −5°, 3°, −3°, −6°, 5° and −12°.

 Timeline of the self-playing loop (`FallingWordStickers.loop` = 6 s), gravity g = 2600 pt/s²:
   0.00–1.20  The pile at rest (the first frame).
   1.20–1.95  The floor gives way: from the top sticker down, 0.05 s apart, each pops up at 250 pt/s
              and falls out of the bottom of the screen under g, spinning at its own rate (30–70°/s)
              and drifting sideways.
   2.00–2.70  The words drop in reading order, 0.14 s apart, from just above the screen: free fall
              under g to their place, sliding in from their own side offset (20–50 pt, ease-out
              cubic) and turning from their own spin (20–55°) to their resting tilt.
   on impact  Two bounces with restitution 0.3 (about 60 pt and 5 pt high) and a wobble of ±7°
              (sin 18 t · e^(−5 t), faded out over 1.6 s). The last word has settled by 5.1 s.
   5.10–6.00  At rest again; 6 s is the same frame as 0.

 Touch: a sticker can be picked up (light haptic, it lifts and grows 5%) and dragged; it leans with
 the finger's speed and is thrown on release with the finger's velocity. Thrown stickers fall under
 the same gravity, bounce off the screen's sides and come to rest on their own floor height with
 restitution 0.3, a spring pulling them back to their tilt; a hard landing gives a rigid haptic. A
 tap without a drag makes the sticker hop (900 pt/s up, with a spin). 2 s after the last touch the
 stickers glide back to the pile (0.6 s, ease-in-out) and the loop starts over. Reduced motion: the
 pile stays at rest and does not move.
*/
struct FallingWordStickers: View {
    var badge = "Open for new briefs"
    var headline = ["Product sites that", "say it straight —", "clear, direct,", "no fluff."]
    var subtitle = "A two-person studio for sites and apps."
    var action = "Start a project →"
    /// The six words of the pile, in reading order.
    var words = ["your", "next", "big", "project", "starts", "here"]
    /// Drops the words again every `loop` seconds.
    var autoPlay = false
    var onAction: (() -> Void)? = nil

    static let loop = 6.0
    static let ink = Color(hex: 0x141414)

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = FallingWordStickersModel()
    /// Keeps the clock running after a touch when the loop is off.
    @State private var touched = 0
    @State private var active = false

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let screen = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            ZStack(alignment: .topLeading) {
                copy(width: screen.width)
                    .padding(.top, insets.top + 16)
                    .padding(.horizontal, 24)

                TimelineView(.animation(paused: reduceMotion || (!autoPlay && !active))) { timeline in
                    Canvas { context, size in
                        model.prepare(words: words, size: size, bottom: insets.bottom)
                        model.draw(&context, date: timeline.date, autoPlay: autoPlay && !reduceMotion)
                    }
                }
                .ignoresSafeArea()
                .allowsHitTesting(false)
                .accessibilityLabel(words.joined(separator: " "))
            }
            .frame(width: screen.width, height: screen.height, alignment: .topLeading)
            .ignoresSafeArea()
            .contentShape(Rectangle())
            .simultaneousGesture(drag)
        }
        .background(Color(hex: 0xf3f2ee).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { model.start(at: .now) }
        .task(id: touched) {
            guard touched > 0 else { return }
            // Two seconds of rest, then the 0.6 s glide back.
            try? await Task.sleep(for: .seconds(FallingWordStickersModel.idle + FallingWordStickersModel.glide + 0.1))
            guard !Task.isCancelled, !model.isDragging else { return }
            active = false
        }
    }

    private func copy(width: Double) -> some View {
        let size = FallingWordStickersModel.fit(headline, width: width - 48, cap: 36)
        return VStack(alignment: .leading, spacing: 0) {
            HStack(spacing: 8) {
                Circle().fill(Color(hex: 0x3ddc84)).frame(width: 6, height: 6)
                Text(badge.uppercased())
                    .font(.system(size: 11, weight: .medium, design: .monospaced))
                    .tracking(1)
                    .foregroundStyle(.white)
                    .lineLimit(1)
            }
            .padding(.horizontal, 12)
            .frame(height: 26)
            .background(Capsule().fill(Self.ink))

            VStack(alignment: .leading, spacing: 0) {
                ForEach(headline.indices, id: \.self) { i in
                    Text(headline[i])
                        .font(.custom(FallingWordStickersModel.fontName, fixedSize: size))
                        .tracking(-0.02 * size)
                        .foregroundStyle(Self.ink)
                        .lineLimit(1)
                        .frame(height: 1.08 * size, alignment: .leading)
                }
            }
            .padding(.top, 20)

            Text(subtitle)
                .font(.custom(FallingWordStickersModel.fontName, fixedSize: 15))
                .foregroundStyle(Self.ink.opacity(0.55))
                .lineLimit(2)
                .padding(.top, 14)

            Button {
                onAction?()
            } label: {
                Text(action)
                    .font(.custom(FallingWordStickersModel.fontName, fixedSize: 17))
                    .foregroundStyle(.white)
                    .lineLimit(1)
                    .padding(.horizontal, 24)
                    .frame(height: 52)
                    .background(Capsule().fill(Self.ink))
                    .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .padding(.top, 24)
        }
    }

    private var drag: some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { value in
                guard !reduceMotion else { return }
                if model.isDragging {
                    model.move(to: value.location, at: .now)
                } else if !model.began, model.begin(at: value.location, date: .now, autoPlay: autoPlay) {
                    active = true
                    touched += 1
                }
                model.began = true
            }
            .onEnded { _ in
                model.began = false
                guard model.isDragging else { return }
                model.end(at: .now)
                touched += 1
            }
    }
}

/// The stickers: their layout, the self-playing fall, and the physics a touch hands them to.
final class FallingWordStickersModel {
    struct Pose {
        var x: Double
        var y: Double
        /// Radians.
        var a: Double
    }

    struct Sticker {
        let word: String
        let color: Color
        let size: Double
        let width: Double
        let height: Double
        /// Where it rests in the pile (centre) and how it tilts there.
        let rest: Pose
        /// The drop: side offset (pt), spin (rad) and wobble on landing (rad); the exit: spin rate (rad/s).
        let drift: Double
        let spin: Double
        let wobble: Double
        let exitSpin: Double
    }

    static let fontName = "InterTight-Medium"
    static let gravity = 2600.0
    static let bounce = 0.3
    static let idle = 2.0
    static let glide = 0.6

    static let colors: [UInt32] = [0xf2655b, 0xff9a6b, 0x6fd0f0, 0xd9a0f4, 0xb9f0e6, 0x9be58b]
    /// Per word: centre x (fraction of W), height of its bottom above the floor (in S), tilt (degrees).
    static let pile: [(Double, Double, Double)] = [(0.165, 0, -5), (0.48, 0, 3), (0.82, 0, -3), (0.36, 1.06, -6), (0.80, 1.52, 5), (0.70, 2.58, -12)]
    static let drifts = [-40.0, 30, -20, 50, -30, 40]
    static let spins = [-35.0, 28, -20, 40, -30, 55]
    static let exitSpins = [-50.0, 40, -30, 60, -45, 70]

    private(set) var stickers: [Sticker] = []
    private var key: (words: [String], size: CGSize, bottom: Double) = ([], .zero, 0)
    private var screen = CGSize.zero

    private enum Mode { case auto, physics, gliding }
    private var mode = Mode.auto
    private var loopStart = Date.now
    private var poses: [Pose] = []
    private var velocity: [Pose] = []
    private var floored: [Bool] = []
    private var order: [Int] = []
    private var last = Date.now
    private var dragged: Int?
    private var grab = CGPoint.zero
    private var finger = CGPoint.zero
    private var fingerVelocity = CGPoint.zero
    private var fingerAt = Date.now
    private var downAt = Date.now
    private var travel = 0.0
    private var released = Date.distantPast
    private var glideFrom: [Pose] = []
    private var glideStart = Date.now
    private let lift = UIImpactFeedbackGenerator(style: .light)
    private let land = UIImpactFeedbackGenerator(style: .rigid)
    var began = false
    var isDragging: Bool { dragged != nil }

    private static let registered: Void = {
        guard let url = Bundle.main.url(forResource: "FallingWordStickers-InterTight-Medium", withExtension: "ttf") else { return }
        CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
    }()

    static func font(_ size: Double) -> UIFont {
        _ = registered
        return UIFont(name: fontName, size: size) ?? .systemFont(ofSize: size, weight: .medium)
    }

    static func width(_ text: String, size: Double) -> Double {
        (text as NSString).size(withAttributes: [.font: font(size)]).width
    }

    /// The largest size up to `cap` at which every line fits `width`.
    static func fit(_ lines: [String], width: Double, cap: Double) -> Double {
        let widest = lines.map { Self.width($0, size: 100) }.max() ?? 0
        return widest > 0 ? min(cap, 100 * width / widest) : cap
    }

    /// Cap height and the depth of "p", per unit of size, from the font's outlines.
    static let metrics: (cap: Double, descent: Double) = {
        let ct = FallingWordStickersModel.font(100) as CTFont
        var chars: [UniChar] = Array("Hp".utf16)
        var glyphs = [CGGlyph](repeating: 0, count: 2)
        CTFontGetGlyphsForCharacters(ct, &chars, &glyphs, 2)
        var rects = [CGRect](repeating: .zero, count: 2)
        CTFontGetBoundingRectsForGlyphs(ct, .horizontal, &glyphs, &rects, 2)
        return (rects[0].maxY / 100, -rects[1].minY / 100)
    }()

    func start(at date: Date) {
        loopStart = date
        last = date
    }

    func prepare(words: [String], size: CGSize, bottom: Double) {
        guard key.words != words || key.size != size || key.bottom != bottom else { return }
        key = (words, size, bottom)
        screen = size
        let s = 0.13 * size.width
        let floor = size.height - bottom - 20
        stickers = words.prefix(6).enumerated().map { i, word in
            let pile = Self.pile[i]
            let fontSize = i == 2 ? 1.4 * s : s
            let width = Self.width(word, size: fontSize) + 0.48 * fontSize
            let height = 1.12 * fontSize
            let x = min(max(pile.0 * size.width, width / 2 + 8), size.width - width / 2 - 8)
            return Sticker(
                word: word, color: Color(hex: Self.colors[i]), size: fontSize, width: width, height: height,
                rest: Pose(x: x, y: floor - pile.1 * s - height / 2, a: pile.2 * .pi / 180),
                drift: Self.drifts[i], spin: Self.spins[i] * .pi / 180, wobble: (Self.spins[i] > 0 ? 7 : -7) * .pi / 180,
                exitSpin: Self.exitSpins[i] * .pi / 180
            )
        }
        order = Array(stickers.indices)
        poses = stickers.map(\.rest)
        velocity = stickers.map { _ in Pose(x: 0, y: 0, a: 0) }
        floored = stickers.map { _ in true }
    }

    // MARK: The self-playing loop

    /// Where sticker `i` is `t` seconds into the loop.
    func pose(_ i: Int, at t: Double) -> Pose {
        let c = stickers[i]
        let g = Self.gravity
        let exit = 1.2 + 0.05 * Double(stickers.count - 1 - i)
        let drop = 2.0 + 0.14 * Double(i)
        if t < exit { return c.rest }
        if t < drop {
            let tau = t - exit
            let y = c.rest.y - 250 * tau + 0.5 * g * tau * tau
            return Pose(x: c.rest.x + 1.5 * c.drift * tau, y: min(y, screen.height + 400), a: c.rest.a + c.exitSpin * tau)
        }
        let tau = t - drop
        let y0 = -max(c.width, c.height) / 2 - 20
        let fall = sqrt(2 * (c.rest.y - y0) / g)
        if tau < fall {
            let p = tau / fall
            return Pose(x: c.rest.x + c.drift * (1 - easeOutCubic(p)), y: y0 + 0.5 * g * tau * tau, a: c.rest.a + c.spin * (1 - p))
        }
        let after = tau - fall
        let v1 = Self.bounce * g * fall
        let t1 = 2 * v1 / g
        let v2 = Self.bounce * v1
        let t2 = 2 * v2 / g
        var height = 0.0
        if after < t1 {
            height = v1 * after - 0.5 * g * after * after
        } else if after < t1 + t2 {
            let u = after - t1
            height = v2 * u - 0.5 * g * u * u
        }
        let wobble = c.wobble * sin(18 * after) * exp(-5 * after) * (1 - smoothstep(0, 1.6, after))
        return Pose(x: c.rest.x, y: c.rest.y - height, a: c.rest.a + wobble)
    }

    private func loopTime(_ date: Date, autoPlay: Bool) -> Double {
        autoPlay ? date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: FallingWordStickers.loop) : 0
    }

    // MARK: Frame

    func draw(_ context: inout GraphicsContext, date: Date, autoPlay: Bool) {
        guard !stickers.isEmpty else { return }
        update(date, autoPlay: autoPlay)
        for i in order {
            let c = stickers[i]
            let p = poses[i]
            guard p.y - max(c.width, c.height) < screen.height else { continue }
            let up = dragged == i ? 1.0 : 0.0
            var shadow = context
            shadow.translateBy(x: p.x, y: p.y + 4 + 8 * up)
            shadow.rotate(by: .radians(p.a))
            shadow.scaleBy(x: 1 + 0.05 * up, y: 1 + 0.05 * up)
            shadow.addFilter(.blur(radius: 6 + 4 * up))
            let rect = CGRect(x: -c.width / 2, y: -c.height / 2, width: c.width, height: c.height)
            let shape = Path(roundedRect: rect, cornerRadius: 0.1 * c.size)
            shadow.fill(shape, with: .color(.black.opacity(0.14 + 0.06 * up)))

            var sticker = context
            sticker.translateBy(x: p.x, y: p.y)
            sticker.rotate(by: .radians(p.a))
            sticker.scaleBy(x: 1 + 0.05 * up, y: 1 + 0.05 * up)
            sticker.fill(shape, with: .color(c.color))
            let text = sticker.resolve(Text(c.word).font(.custom(Self.fontName, fixedSize: c.size)).foregroundColor(FallingWordStickers.ink))
            let measured = text.measure(in: CGSize(width: 10_000, height: 10_000))
            let baseline = (Self.metrics.cap - Self.metrics.descent) / 2 * c.size
            sticker.draw(text, at: CGPoint(x: -c.width / 2 + 0.24 * c.size, y: baseline - text.firstBaseline(in: measured)), anchor: .topLeading)
        }
    }

    private func update(_ date: Date, autoPlay: Bool) {
        defer { last = date }
        switch mode {
        case .auto:
            let t = loopTime(date, autoPlay: autoPlay)
            for i in stickers.indices { poses[i] = pose(i, at: t) }
        case .physics:
            var dt = min(date.timeIntervalSince(last), 0.05)
            while dt > 0 {
                let step = min(dt, 1.0 / 120)
                simulate(step)
                dt -= step
            }
            if dragged == nil, date.timeIntervalSince(released) >= Self.idle {
                mode = .gliding
                glideFrom = poses
                glideStart = date
                order = Array(stickers.indices)
            }
        case .gliding:
            let k = easeInOut(min(date.timeIntervalSince(glideStart) / Self.glide, 1))
            for i in stickers.indices {
                let a = glideFrom[i], b = stickers[i].rest
                poses[i] = Pose(x: a.x + (b.x - a.x) * k, y: a.y + (b.y - a.y) * k, a: a.a + (b.a - a.a) * k)
            }
            if k >= 1 {
                mode = .auto
                loopStart = date
            }
        }
    }

    private func simulate(_ dt: Double) {
        let g = Self.gravity
        for i in stickers.indices {
            let c = stickers[i]
            if i == dragged {
                let target = c.rest.a + max(-0.45, min(0.45, fingerVelocity.x * 0.0005))
                poses[i].a += (target - poses[i].a) * min(1, dt * 12)
                continue
            }
            var p = poses[i], v = velocity[i]
            v.y += g * dt
            p.x += v.x * dt
            p.y += v.y * dt
            // A spring pulls the tilt back to the pile's.
            v.a += (-(p.a - c.rest.a) * 30 - v.a * 6) * dt
            p.a += v.a * dt
            let half = c.width / 2
            if p.x < half { p.x = half; v.x = abs(v.x) * 0.5 }
            if p.x > screen.width - half { p.x = screen.width - half; v.x = -abs(v.x) * 0.5 }
            if floored[i], p.y > c.rest.y {
                p.y = c.rest.y
                if v.y > 700 { land.impactOccurred(intensity: min(1, v.y / 2400)) }
                v.y = v.y > 60 ? -v.y * Self.bounce : 0
                v.x *= 0.8
            }
            if floored[i], p.y >= c.rest.y - 0.5, v.y == 0 { v.x *= exp(-8 * dt) }
            poses[i] = p
            velocity[i] = v
        }
    }

    // MARK: Touch

    /// Picks up the sticker under the finger; false when there is none.
    func begin(at point: CGPoint, date: Date, autoPlay: Bool) -> Bool {
        guard !stickers.isEmpty else { return false }
        update(date, autoPlay: autoPlay)
        guard let hit = order.reversed().first(where: { contains(point, $0) }) else { return false }
        if mode == .auto {
            let t = loopTime(date, autoPlay: autoPlay)
            velocity = stickers.indices.map { i in
                let a = pose(i, at: t - 1.0 / 60), b = pose(i, at: t)
                return Pose(x: (b.x - a.x) * 60, y: (b.y - a.y) * 60, a: (b.a - a.a) * 60)
            }
        } else if mode == .gliding {
            velocity = stickers.map { _ in Pose(x: 0, y: 0, a: 0) }
        }
        floored = stickers.indices.map { poses[$0].y <= stickers[$0].rest.y + 1 }
        mode = .physics
        dragged = hit
        floored[hit] = true
        order.removeAll { $0 == hit }
        order.append(hit)
        grab = CGPoint(x: poses[hit].x - point.x, y: poses[hit].y - point.y)
        finger = point
        fingerVelocity = .zero
        fingerAt = date
        downAt = date
        travel = 0
        last = date
        lift.impactOccurred()
        return true
    }

    func move(to point: CGPoint, at date: Date) {
        guard let i = dragged else { return }
        let dt = max(date.timeIntervalSince(fingerAt), 1.0 / 240)
        let v = CGPoint(x: (point.x - finger.x) / dt, y: (point.y - finger.y) / dt)
        fingerVelocity = CGPoint(x: fingerVelocity.x * 0.6 + v.x * 0.4, y: fingerVelocity.y * 0.6 + v.y * 0.4)
        travel += hypot(point.x - finger.x, point.y - finger.y)
        finger = point
        fingerAt = date
        poses[i].x = point.x + grab.x
        poses[i].y = min(point.y + grab.y, stickers[i].rest.y)
    }

    func end(at date: Date) {
        guard let i = dragged else { return }
        dragged = nil
        released = date
        if travel < 8, date.timeIntervalSince(downAt) < 0.3 {
            // A tap: the sticker hops.
            velocity[i] = Pose(x: 0, y: -900, a: stickers[i].spin > 0 ? 4 : -4)
        } else {
            let still = date.timeIntervalSince(fingerAt) > 0.1
            let vx = still ? 0 : max(-3000, min(3000, fingerVelocity.x))
            let vy = still ? 0 : max(-3000, min(3000, fingerVelocity.y))
            velocity[i] = Pose(x: vx, y: vy, a: vx * 0.004)
        }
    }

    private func contains(_ point: CGPoint, _ i: Int) -> Bool {
        let c = stickers[i], p = poses[i]
        let dx = point.x - p.x, dy = point.y - p.y
        let x = dx * cos(-p.a) - dy * sin(-p.a)
        let y = dx * sin(-p.a) + dy * cos(-p.a)
        return abs(x) <= c.width / 2 + 6 && abs(y) <= c.height / 2 + 6
    }
}

private func easeOutCubic(_ t: Double) -> Double { 1 - pow(1 - t, 3) }
private func easeInOut(_ t: Double) -> Double { t < 0.5 ? 4 * t * t * t : 1 - pow(-2 * t + 2, 3) / 2 }
private func smoothstep(_ a: Double, _ b: Double, _ x: Double) -> Double {
    let t = min(max((x - a) / (b - a), 0), 1)
    return t * t * (3 - 2 * t)
}
